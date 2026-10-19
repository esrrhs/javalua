package com.github.esrrhs.fakescript.lua;

import java.lang.ref.WeakReference;

/**
 * 短字符串驻留池 —— 对应 {@code lstring.c} 的 {@code strt}（string table）。
 * <p>
 * 驻留是 Lua 的性能基石：短字符串全局唯一，相等比较可以退化成引用比较，
 * 表查找也能省掉逐字节比对。语义上不可观测，但少了它 {@code t.x = t.x} 这类
 * 代码会慢一个量级。
 * <p>
 * <b>为什么要弱引用。</b>C 版里驻留表是由 {@code lgc.c} 显式管理的：GC 周期中
 * 会清掉没人引用的短字符串（{@code iscleared} 那套逻辑）。这里没有自己的 GC，
 * 如果驻留池用强引用，脚本里出现过的每一个短字符串都会永久驻留——跑数据处理
 * 脚本时这会实打实地泄漏。所以键是弱引用：脚本不再引用某个字符串时它自然被
 * JVM 回收。
 * <p>
 * <b>为什么不用 {@code WeakHashMap}。</b>第一版就是 {@code WeakHashMap}，实测
 * 整片跑一致性闸门时它会从 20 秒变成 7～38 分钟。JFR 剖面给得很直白：4391 个
 * 执行采样里 <b>4201 个（96%）</b>落在
 * {@code WeakHashMap.getTable → expungeStaleEntries → ReferenceQueue.reallyPoll}，
 * 真正干活（建串、算哈希、拼接）的那几帧只占 4%。
 * <p>
 * 原因是 {@code WeakHashMap} 的每一次 {@code get}/{@code put} 都要
 * {@code getTable()}，而 {@code getTable()} 会把引用队列<b>整个排空</b>，
 * 每摘一条还要 {@code synchronized (queue)} 抢一次锁——而 JVM 的
 * {@code ReferenceHandler} 线程入队时抢的是<b>同一把锁</b>。字符串一 churn
 * （循环里 {@code s = a .. i} 这种，每轮产生一个短的、随即死掉的新串），
 * 队列就是一边被 GC 灌、一边被排空，锁在两条线程之间来回弹。
 * <p>
 * 所以这里照 {@code lstring.c} 自己写一张<b>链地址法 + 弱键</b>的表：查表时
 * 撞见 {@code get() == null} 的死节点<b>直接跳过</b>，不碰任何队列；死节点攒到
 * 装填因子上限时随 {@code rehash} 一次性摘掉。没有队列、没有那把锁，
 * 也就没有那个悬崖。
 */
public final class StringPool {

    /** 初始桶数，2 的幂 */
    private static final int MIN_CAPACITY = 32;

    /** 装填因子：活节点数达到 {@code 容量 * LOAD} 就 rehash */
    private static final float LOAD = 0.75f;

    /**
     * 一个槽位：对字符串的弱引用 + 缓存的哈希 + 冲突链。
     * <p>
     * 存哈希是为了 rehash 时不必把已经死掉的键再 {@code get()} 一次
     * （{@code get()} 返回 null 的节点没法问它的哈希）。
     */
    private static final class Node extends WeakReference<LuaString> {
        final int hash;
        Node next;

        Node(LuaString s, int hash, Node next) {
            super(s);
            this.hash = hash;
            this.next = next;
        }
    }

    private Node[] tab;

    /** 节点总数，<b>含已经死掉但还没被 rehash 摘掉的</b> */
    private int count;

    private int threshold;

    public StringPool() {
        allocTab(MIN_CAPACITY);
    }

    /**
     * 返回与 {@code s} 内容相同的规范实例。
     * <p>
     * 长字符串不驻留（与上游一致：{@code LUAI_MAXSHORTLEN} 以上的字符串每次都新建）。
     */
    public LuaString intern(LuaString s) {
        if (!s.isShort()) {
            return s;
        }
        int h = s.hash();
        synchronized (this) {
            int i = h & (tab.length - 1);
            for (Node n = tab[i]; n != null; n = n.next) {
                LuaString v = n.get();
                if (v == null) {
                    continue; // 死节点：跳过，不摘（摘是 rehash 的事）
                }
                if (v.hash() == h && v.equals(s)) {
                    return v;
                }
            }
            if (count >= threshold) {
                rehash();
                i = h & (tab.length - 1);
            }
            tab[i] = new Node(s, h, tab[i]);
            count++;
            return s;
        }
    }

    /**
     * 重挂一遍：顺手摘掉死节点，并按活着的节点数重新定容量。
     * <p>
     * 容量是<b>可以变小</b>的 —— 全死光时 {@code count} 归零，下一轮 rehash 会把
     * 桶数缩回 {@link #MIN_CAPACITY}。{@code WeakHashMap} 做不到这一点：它的
     * {@code size} 只在排空队列时递减，而队列什么时候排取决于 GC。
     */
    private void rehash() {
        Node[] old = tab;
        int live = 0;
        for (Node n : old) {
            for (Node p = n; p != null; p = p.next) {
                if (p.get() != null) {
                    live++;
                }
            }
        }
        int cap = old.length;
        while (live + 1 > (int) (cap * LOAD)) {
            cap <<= 1;
        }
        while (cap > MIN_CAPACITY && live + 1 <= (int) ((cap >> 1) * LOAD)) {
            cap >>= 1;
        }
        Node[] nt = new Node[cap];
        for (Node n : old) {
            for (Node p = n; p != null; ) {
                Node next = p.next;
                LuaString v = p.get();
                if (v != null) {
                    int i = v.hash() & (cap - 1);
                    p.next = nt[i];
                    nt[i] = p;
                }
                p = next;
            }
        }
        tab = nt;
        count = live;
        threshold = (int) (cap * LOAD);
    }

    private void allocTab(int cap) {
        tab = new Node[cap];
        threshold = (int) (cap * LOAD);
    }

    /** 池中条目数（含尚未被 rehash 摘掉的失效条目），仅用于测试与诊断 */
    public synchronized int size() {
        return count;
    }
}
