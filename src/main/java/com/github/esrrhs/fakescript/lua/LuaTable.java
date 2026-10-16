package com.github.esrrhs.fakescript.lua;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Lua 表 —— 对应 {@code ltable.c} 的 {@code Table}。
 * <p>
 * 与 C 版一样分成两段：
 * <ul>
 *   <li><b>数组部分</b> {@code array}：密集的正整数键，下标 {@code k} 落在
 *       {@code array[k-1]}。这是 {@code for i = 1, #t} 能跑快的原因，也是
 *       {@code #t} 能二分查找边界的原因。</li>
 *   <li><b>哈希部分</b> {@code node}：其余所有键，链地址法，容量取 2 的幂。</li>
 * </ul>
 * <p>
 * 键的相等性用 Lua 的 {@code rawequal} 而不是 Java 的 {@code equals}：
 * {@code 1} 与 {@code 1.0} 是同一个键（Lua 里 {@code 1 == 1.0} 为真），
 * 但 {@code 1} 与 {@code "1"} 不是（整数键与字符串键是不同的项）。
 * <p>
 * 一处刻意的简化：C 版 5.4 用「假 alimit」把数组部分的实际边界藏在一个标志位里
 * （{@code isfakealimit}），以省掉部分二分查找。这里直接用精确的
 * {@code alimit == array.length}，语义等价、代码好读，代价是 {@code #t}
 * 偶尔多一次二分。
 */
public final class LuaTable {

    private static final LuaValue[] EMPTY_ARRAY = new LuaValue[0];

    /** 数组部分。下标 k（1 起）对应 array[k-1]，槽永不为空（nil 用 isNil() 表示） */
    LuaValue[] array = EMPTY_ARRAY;

    /** 哈希部分，长度为 2 的幂；为 null 表示为空（对应 C 的 dummy node） */
    Node[] node = null;

    /** 哈希部分长度 */
    int nodeSize = 0;

    /** 下一个待探测的空闲位置，从 {@link #nodeSize} 往下走（对应 {@code lastfree}） */
    int lastFree = 0;

    /** 元表 */
    LuaTable metatable;

    /** 挂了 {@code __gc} 时创建的哨兵；没挂则为 {@code null}。见 {@link GcSentinel} */
    private GcSentinel gcSentinel;

    /** {@code __gc} 是否已经跑过。只跑一次，与参考实现一致（实测确认） */
    private boolean gcDone;

    /** 哈希节点：键 + 值 + 同链下一跳（下标，-1 表示链尾） */
    static final class Node {
        final LuaValue key = new LuaValue();
        final LuaValue val = new LuaValue();
        int next = -1;
    }

    /* ==================== 弱表 ==================== */

    /**
     * 弱引用包装。弱表里的键（或值）把<b>这个</b>放进 {@code LuaValue.o}，
     * 而不是直接放对象本身。
     * <p>
     * 为什么必须换掉载荷：如果槽里直接放对象，那就是一条强引用，JVM 永远
     * 不会回收它，弱表也就永远不会释放任何东西——弱引用必须是<b>存储形式</b>，
     * 不能只是旁边挂一个 {@code WeakReference} 做样子。
     */
    static final class WeakRef extends WeakReference<Object> {

        /**
         * 建引用<b>那一刻</b>的身份哈希。
         * <p>
         * 不能等到用的时候再 {@code System.identityHashCode(get())}：引用失效后
         * {@code get()} 是 {@code null}，而 {@code identityHashCode(null)} 恒为 0，
         * 于是所有失效键的主位置一起塌到 0 号桶。链地址法靠主位置走链，主位置一变
         * 就找不到前驱——表现是 {@code newKey} 里 {@code node[-1]} 越界。
         */
        final int hash;

        WeakRef(Object referent) {
            super(referent);
            hash = System.identityHashCode(referent);
        }
    }

    /** {@code __mode} 里的 {@code 'k'} / {@code 'v'} */
    private boolean weakKey;

    private boolean weakValue;

    /** 上次清扫时的收集轮次；用来保证一轮里只清一次，避免 {@code pairs} 变 O(n²) */
    private int purgeGen = -1;

    /**
     * 全局收集轮次，{@code collectgarbage} 时递增。
     * <p>
     * 弱条目只在「一轮收集结束」时被摘掉，这一点与 C 版一致：Lua 是在 GC 周期的
     * 末尾清弱表，不是键一变成不可达就立刻消失。所以自发发生的 JVM 收集不会
     * 让条目提前消失——这与参考实现的行为更接近。
     */
    static int gcGeneration = 0;

    /** 取槽里真正的对象：弱引用解一层，引用已失效返回 {@code null} */
    static Object payload(LuaValue v) {
        Object o = v.o;
        return (o instanceof WeakRef) ? ((WeakRef) o).get() : o;
    }

    /** 这一项是否已经失效（弱引用被回收） */
    static boolean isDead(LuaValue v) {
        return (v.o instanceof WeakRef) && ((WeakRef) v.o).get() == null;
    }

    /**
     * 会不会被弱表清掉。
     * <p>
     * 只有「有显式构造」的对象才会被清：表、函数、线程、userdata。
     * <b>字符串不算</b>——它虽然可收集，但没有显式构造，所以
     * {@code __mode='kv'} 的表里 {@code t['a']='b'} 永远不会被摘掉（实测确认）。
     * 数字、布尔同理。
     */
    private static boolean isCollectable(LuaValue v) {
        switch (Lua.noVariant(v.tt)) {
        case Lua.TTABLE:
        case Lua.TFUNCTION:
        case Lua.TTHREAD:
        case Lua.TUSERDATA:
            return true;
        default:
            return false;
        }
    }

    /**
     * 写入时把可收集的对象包成弱引用。
     *
     * @param doWrap 这一侧（键或值）是不是弱的
     */
    private static LuaValue weakWrap(LuaValue v, boolean doWrap) {
        /*
         * 已经包过的不能再包。{@code purge} 与 {@code resize} 搬的是<b>槽里现有的
         * LuaValue</b>，它的 {@code o} 已经是 {@code WeakRef}；再包一层就成了
         * WeakRef 套 WeakRef，解引用拿到的是内层引用对象而不是表，所有按身份
         * 比较的查找就此失效。
         */
        if (!doWrap || !isCollectable(v) || (v.o instanceof WeakRef)) {
            return v;
        }
        LuaValue r = new LuaValue();
        r.set(v);
        r.o = new WeakRef(v.o);
        return r;
    }

    /** 本表是不是弱表 */
    boolean isWeak() {
        return weakKey || weakValue;
    }

    /**
     * 需要清扫时清掉失效条目。
     * <p>
     * 放在每个公开方法的入口，于是下面的算法（查找、rehash、遍历）都只需要处理
     * 「活条目」——失效项不会在算法中途冒出来。用轮次挡一层是为了让
     * {@code for k in pairs(t)} 是 O(n) 而不是 O(n²)：一轮收集里只清一次。
     */
    private void purgeIfNeeded() {
        if (!isWeak() || purgeGen == gcGeneration) {
            return;
        }
        purgeGen = gcGeneration;
        purge();
    }

    /** 摘掉所有已经失效的条目。弱表不用数组部分，所以只看哈希部分 */
    private void purge() {
        if (nodeSize == 0) {
            return;
        }
        List<LuaValue[]> live = new ArrayList<>();
        for (int i = 0; i < nodeSize; i++) {
            Node n = node[i];
            if (n.val.isNil()) {
                continue;
            }
            if (isDead(n.key) || isDead(n.val)) {
                continue;
            }
            live.add(new LuaValue[] { n.key, n.val });
        }
        if (live.size() == countLiveNodes()) {
            return; // 一项都没失效，不用重建
        }
        int size = nodeSize;
        node = null;
        nodeSize = 0;
        lastFree = 0;
        setNodeVector(size);
        for (LuaValue[] kv : live) {
            insertNoRehash(kv[0], kv[1]);
        }
    }

    private int countLiveNodes() {
        int n = 0;
        for (int i = 0; i < nodeSize; i++) {
            if (!node[i].val.isNil()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 读出时把弱引用解成普通值。
     * <p>
     * 这一层是必须的：槽里的 {@code WeakRef} 绝不能泄漏到 VM 里，否则
     * {@code (LClosure) v.o} 这类取载荷的地方会直接抛 {@code ClassCastException}。
     */
    private static LuaValue weakUnwrap(LuaValue v) {
        if (!(v.o instanceof WeakRef)) {
            return v;
        }
        Object o = ((WeakRef) v.o).get();
        LuaValue r = new LuaValue();
        if (o == null) {
            // 引用已经失效：还成一个 nil，绝不能带着 tt=TABLE 而载荷是 null
            r.setNil();
            return r;
        }
        r.set(v);
        r.o = o;
        return r;
    }

    /**
     * 弱键比较：两边至少有一边是弱引用时按「解引用后同一对象」判等。
     * <p>
     * 两边都失效时<b>必须判不等</b>：否则任意两个死条目会互相匹配（解出来都是
     * {@code null}），一次查找就会命中一个和它毫无关系的槽。
     */
    private static boolean slotKeyEquals(LuaValue a, LuaValue b) {
        if (a.o instanceof WeakRef || b.o instanceof WeakRef) {
            if (a.tt != b.tt) {
                return false;
            }
            Object pa = payload(a);
            Object pb = payload(b);
            return pa != null && pa == pb;
        }
        return LuaValue.rawEqual(a, b);
    }

    public LuaTable() {
    }

    /** 预分配两段。仅供需要预置大小的场合（如构造 registry） */
    public LuaTable(int arraySize, int hashSize) {
        if (arraySize > 0) {
            setArrayVector(arraySize);
        }
        if (hashSize > 0) {
            setNodeVector(hashSize);
        }
    }

    /* ==================== 哈希与主位置 ==================== */

    /** {@code mainposition}：键的主桶下标 */
    private int mainPosition(LuaValue key) {
        switch (key.tt) {
        case Lua.VNUMINT:
            return (int) (key.i & 0x7FFFFFFF) & (nodeSize - 1);
        case Lua.VNUMFLT:
            return hashFloat(key.n) & (nodeSize - 1);
        case Lua.VSHRSTR:
        case Lua.VLNGSTR:
            return key.asString().hash() & (nodeSize - 1);
        case Lua.TBOOLEAN:
            return (int) key.i & (nodeSize - 1);
        default:
            return objectHash(key.o) & (nodeSize - 1);
        }
    }

    /**
     * 对象键的哈希。
     * <p>
     * 弱键走 {@link WeakRef#hash}——建引用时记下的那份；直接用
     * {@code identityHashCode(payload(key))} 会在引用失效后算出 0（见
     * {@link WeakRef#hash} 的注释）。
     */
    private static int objectHash(Object o) {
        return (o instanceof WeakRef) ? ((WeakRef) o).hash : System.identityHashCode(o);
    }

    /**
     * {@code ltable.c: l_hashfloat}：把浮点拆成尾数×2^指数再映射到整数。
     * <p>
     * {@code -0.0} 与 {@code 0.0} 必须哈希到同一处——Lua 里二者相等
     * （{@code luai_numeq}），因此必须是同一个键。
     */
    private static int hashFloat(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return 0;
        }
        if (n == 0.0) {
            return 0; // 归一化 -0.0
        }
        int e = Math.getExponent(n) + 1;
        double m = Math.scalb(n, -e); // 0.5 <= |m| < 1
        long ni = (long) (m * (Integer.MAX_VALUE - Double.MAX_EXPONENT));
        ni += e;
        if (ni < 0) {
            ni = -ni;
        }
        return (int) (ni & 0x7FFFFFFF);
    }

    /* ==================== 取 ==================== */

    /**
     * {@code luaH_get}：返回值的槽；键不存在时返回 {@code null}（调用方按 nil 处理）。
     * <p>
     * 返回的是<b>活的槽</b>而不是副本：调用方可以直接往里写。这与 C 版返回
     * {@code TValue*} 一致，也是「数组部分优先于哈希部分」这个优化能成立的前提。
     */
    public LuaValue get(LuaValue key) {
        purgeIfNeeded();
        LuaValue slot = rawGet(key);
        if (slot == null) {
            return null;
        }
        /*
         * 弱表必须返回<b>副本</b>而不是活槽：槽里放的是 WeakRef，直接交出去
         * 会让取载荷的代码炸掉。强表照旧返回活槽——那是写入路径依赖的优化，
         * 不能为了弱表把它改掉。
         */
        return isWeak() ? weakUnwrap(slot) : slot;
    }

    /** {@code luaH_get} 的原实现，返回活槽（含弱引用载荷），供内部使用 */
    private LuaValue rawGet(LuaValue key) {
        if (key.tt == Lua.VNUMINT) {
            return getInt(key.i);
        }
        if (key.tt == Lua.VNUMFLT) {
            // 整值浮点与整数是同一个键：Lua 5.4.7 实测 t[1.0] 与 t[1] 同一项
            long[] k = new long[1];
            if (floatToIntExact(key.n, k)) {
                return getInt(k[0]);
            }
        }
        return getFromHash(key);
    }

    /** 整数键查找：先数组部分，再哈希部分 */
    private LuaValue getInt(long k) {
        if (k > 0 && k <= array.length) {
            return array[(int) k - 1];
        }
        return getFromHash(LuaValue.ofInteger(k));
    }

    /**
     * {@code luaV_flttointns} 的 F2Ieq 模式：浮点只有当<b>精确</b>等于某个整数
     * 时才转换。
     * <p>
     * 这条规则直接决定键的规范化，已用 Lua 5.4.7 实测确认：
     * <pre>
     *   t[1.0]  = "a"  →  next(t) 的键 math.type 是 "integer"，#t 是 1
     *   t[0.0]  = "a"  →  键是整数 0（但 0 不是数组下标，故 #t 仍是 0）
     *   t[1.5]  = "a"  →  键是浮点
     *   t[2^63] = "a"  →  键是浮点（超出 integer 可表示范围）
     * </pre>
     */
    private static boolean floatToIntExact(double n, long[] out) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return false;
        }
        if (n >= Long.MIN_VALUE && n < 9223372036854775808.0) {
            long k = (long) n;
            if ((double) k == n) { // 必须精确往返，否则 1.5 会被错误地当成 1
                out[0] = k;
                return true;
            }
        }
        return false;
    }

    /**
     * 写入前的键规范化：整值浮点转成整数键，nil 与 NaN 直接报错。
     * <p>
     * 放在这里而不是各调用点，是因为 {@code ltable.c} 里这两个检查就在
     * {@code luaH_newkey} 的开头——所有写路径（VM 的 {@code OP_SET*}、
     * {@code rawset}、宿主 API）最终都经过它，分散检查必漏一处。
     * <p>
     * NaN 的处理是 Lua 5.4.7 实测的：<b>读</b> {@code t[0/0]} 返回 nil 不报错，
     * <b>写</b> {@code t[0/0] = 1} 报 "table index is NaN"。所以这里只在
     * 写路径上拒绝。
     * <p>
     * 抛的是不带位置的裸错误值：{@code luaG_runerror} 的位置前缀由
     * {@link LuaState#pcall} 在保护边界补上，与 C 版 {@code luaG_addinfo}
     * 的时机一致。
     */
    private static LuaValue normalizeKeyForWrite(LuaValue key) {
        if (key.isNil()) {
            throw new LuaError(LuaValue.ofString(LuaString.latin1("table index is nil")));
        }
        if (key.tt == Lua.VNUMFLT) {
            if (Double.isNaN(key.n)) {
                throw new LuaError(LuaValue.ofString(LuaString.latin1("table index is NaN")));
            }
            long[] out = new long[1];
            if (floatToIntExact(key.n, out)) {
                return LuaValue.ofInteger(out[0]);
            }
        }
        return key;
    }

    private LuaValue getFromHash(LuaValue key) {
        if (nodeSize == 0) {
            return null;
        }
        Node n = node[mainPosition(key)];
        while (true) {
            if (slotKeyEquals(n.key, key)) {
                return n.val;
            }
            if (n.next < 0) {
                return null;
            }
            n = node[n.next];
        }
    }

    public LuaValue get(long k) {
        return get(LuaValue.ofInteger(k));
    }

    public LuaValue get(LuaString k) {
        return get(LuaValue.ofString(k));
    }

    public LuaValue get(String k) {
        return get(LuaString.latin1(k));
    }

    /** 键是否存在且值非 nil */
    public boolean contains(LuaValue key) {
        LuaValue v = get(key);
        return v != null && !v.isNil();
    }

    /* ==================== 存 ==================== */

    public void set(LuaValue key, LuaValue value) {
        purgeIfNeeded();
        ensureSlot(key).set(weakWrap(value, weakValueFor(key, value)));
    }

    /**
     * 值这一侧要不要弱引用。
     * <p>
     * 通常是 {@code weakValue}，但弱键表多一条：<b>值就是键本身</b>时也必须弱存。
     * 否则值会强引用键，键永远死不掉、条目永远清不掉。实测参考实现：
     * {@code t[k] = k} 之后 {@code collectgarbage()}，{@code next(t)} 是 nil。
     * <p>
     * 这个特例是<b>安全</b>的：值与键是同一个对象，键活着值就活着，键死了条目
     * 本来也要摘掉——弱存不会丢任何东西。
     * <p>
     * 反过来，{@code t[k] = {k}} 这种「值里套着键」的形态<b>不</b>在这里弱存：
     * 两者不是同一个对象，一旦弱存，值会在键还活着的时候被回收，
     * {@code t[k]} 凭空变成 nil。那是数据丢失，比漏摘条目严重得多。
     * 这是本实现已知的一处与参考实现不一致，README 里记着。
     */
    private boolean weakValueFor(LuaValue key, LuaValue value) {
        if (weakValue) {
            return true;
        }
        return weakKey && isCollectable(value) && payload(key) == payload(value);
    }

    public void set(long k, LuaValue value) {
        set(LuaValue.ofInteger(k), value);
    }

    public void set(LuaString k, LuaValue value) {
        set(LuaValue.ofString(k), value);
    }

    public void set(String k, LuaValue value) {
        set(LuaString.latin1(k), value);
    }

    /**
     * {@code luaH_set}：拿到键对应的可写槽，键不存在就先建一个值为 nil 的条目。
     * <p>
     * <b>为什么不能一步到位。</b>rehash 有可能把这个键直接安排进数组部分
     * （例如空表上 {@code t[1]=v}：rehash 算出数组部分该是 1，于是槽在
     * {@code array[0]}，而它是 {@code LuaValue} 不是 {@code Node}）。所以
     * rehash 之后必须重新查一次，不能假设结果还在哈希部分——这正是 C 版在
     * rehash 后重新调用 {@code luaH_set} 的原因，只是那里靠指针统一了两种槽。
     */
    private LuaValue ensureSlot(LuaValue rawKey) {
        LuaValue key = normalizeKeyForWrite(rawKey);
        LuaValue slot = rawGet(key);
        if (slot != null) {
            return slot;
        }
        while (true) {
            if (nodeSize == 0) {
                // 还没有哈希部分：交给 rehash 决定两段大小
                rehash(key);
            } else {
                Node n = newKey(key, false);
                if (n != null) {
                    return n.val;
                }
                rehash(key);
            }
            slot = rawGet(key);
            if (slot != null) {
                return slot;
            }
        }
    }

    /**
     * {@code luaH_newkey}：为表中不存在的键分配条目。
     * <p>
     * 返回 {@code null} 表示「需要 rehash」，由调用方 rehash 后重试。之所以把这个
     * 决定交回调用方，是因为 {@link #resize} 搬运旧数据时必须保证不再触发
     * 嵌套 rehash（否则搬一半的表会被再次重建）。
     */
    private Node newKey(LuaValue key, boolean allowRehash) {
        if (nodeSize == 0) {
            return null;
        }
        int mpIdx = mainPosition(key);
        Node mp = node[mpIdx];
        if (!mp.val.isNil()) {
            int f = getFreePos();
            if (f < 0) {
                return null; // 没有空位
            }
            int otherIdx = mainPosition(mp.key);
            if (otherIdx != mpIdx) {
                // mp 不在它自己的主位置：把它挪到空闲位 f，腾出 mp 给新键
                int p = otherIdx;
                while (node[p].next != mpIdx) {
                    p = node[p].next;
                }
                node[p].next = f;
                copyNode(mp, node[f]);
                mp.next = -1;
                mp.val.setNil();
            } else {
                // mp 就在自己的主位置：新节点挂到 f，链在 mp 之后
                node[f].next = mp.next;
                mp.next = f;
                mpIdx = f;
                mp = node[f];
            }
        }
        mp.key.set(weakWrap(key, weakKey));
        mp.val.setNil();
        return mp;
    }

    private static void copyNode(Node from, Node to) {
        to.key.set(from.key);
        to.val.set(from.val);
        to.next = from.next;
    }

    /** {@code getfreepos}：从 lastFree 往下找第一个键为 nil 的位置 */
    private int getFreePos() {
        while (lastFree > 0) {
            lastFree--;
            if (node[lastFree].key.isNil()) {
                return lastFree;
            }
        }
        return -1;
    }

    /* ==================== rehash ==================== */

    /**
     * {@code ltable.c: rehash}：插入新键却找不到空位时，重新分配两段的大小。
     * <p>
     * 核心是 {@code computesizes}：按 2 的幂逐档统计整数键，只要某一档里
     * 「已用数量超过该档容量的一半」，就把数组部分扩到那一档。这就是
     * 「先 {@code t[1]=…} 再 {@code t[2]=…} 会让数组部分从 0 长到 1 再长到 2」
     * 的原因，也是为什么乱序赋值（先 {@code t[1000]=1}）不会撑出一百万长的数组。
     */
    private void rehash(LuaValue extraKey) {
        /*
         * 弱表先清一遍失效条目。
         * <p>
         * 不清会出两种事故：一是失效条目占着槽，{@code getFreePos} 找不到空位，
         * 于是 {@code newKey} 返回 {@code null} → rehash → 还是找不到 → 死循环；
         * 二是 {@code numUseHash} 把它们也算进 {@code totalUse}，哈希部分按一个
         * 虚高的用量扩容，永远缩不回去。
         */
        if (isWeak()) {
            purge();
        }
        int[] nums = new int[Lua.MAXABITS + 1];
        int na = numUseArray(nums);
        int totalUse = na;
        int[] hashUse = { 0 };
        totalUse += numUseHash(nums, hashUse);
        na += hashUse[0];

        // 计入本次要插入的那个键
        if (extraKey.tt == Lua.VNUMINT) {
            na += countInt(extraKey.i, nums);
        }
        totalUse++;

        int[] outNa = { na };
        int newArraySize = computeSizes(nums, outNa);
        /*
         * 弱表一律不用数组部分。数组槽是普通的 LuaValue，放不了 WeakRef，
         * 而给数组另配一套弱存储会把查找路径撕成两份。哈希部分是唯一存储，
         * 于是 {__mode='v'} 的表里 t[1] = {} 也能正常被回收。
         */
        if (isWeak()) {
            newArraySize = 0;
            /*
             * 哈希大小必须重算，不能用 {@code totalUse - outNa[0]}：outNa[0] 是
             * 「会被安排进数组部分」的个数，而弱表的数组部分恒为 0，那部分键
             * 全部得留在哈希里。照抄原来的算式会算出 0 个哈希槽，于是
             * newKey 永远返回 null、ensureSlot 无限重试——表现是整个测试挂死。
             */
            resize(0, Math.max(totalUse, 1));
            return;
        }
        resize(newArraySize, Math.max(totalUse - outNa[0], 0));
    }

    /** {@code numusearray}：统计数组部分里非 nil 的元素，按 2 的幂分档 */
    private int numUseArray(int[] nums) {
        int ause = 0;
        int i = 1;
        int ttlg = 1;
        for (int lg = 0; lg <= Lua.MAXABITS; lg++, ttlg *= 2) {
            int lim = Math.min(ttlg, array.length);
            int lc = 0;
            for (; i <= lim; i++) {
                if (!array[i - 1].isNil()) {
                    lc++;
                }
            }
            nums[lg] = lc;
            ause += lc;
            if (i > array.length) {
                break;
            }
        }
        return ause;
    }

    /** {@code numusehash}：遍历哈希部分，统计总数与「可以放进数组部分」的整数键数 */
    private int numUseHash(int[] nums, int[] pna) {
        int totalUse = 0;
        int ause = 0;
        for (int i = 0; i < nodeSize; i++) {
            Node n = node[i];
            if (!n.val.isNil()) {
                if (n.key.tt == Lua.VNUMINT) {
                    ause += countInt(n.key.i, nums);
                }
                totalUse++;
            }
        }
        pna[0] += ause;
        return totalUse;
    }

    /** {@code countint}：整数键落在数组范围内就计入分档 */
    private static int countInt(long key, int[] nums) {
        int k = arrayIndex(key);
        if (k != 0) {
            nums[Lua.ceilLog2(k)]++;
            return 1;
        }
        return 0;
    }

    private static int arrayIndex(long k) {
        return (k > 0 && k <= Lua.MAXASIZE) ? (int) k : 0;
    }

    /** {@code computesizes}：算出数组部分的「最优」大小 */
    private static int computeSizes(int[] nums, int[] pna) {
        int optimal = 0;
        int a = 0;
        int na = 0;
        for (int i = 0, twotoi = 1; twotoi > 0 && pna[0] > twotoi / 2; i++, twotoi *= 2) {
            if (nums[i] > 0) {
                a += nums[i];
                if (a > twotoi / 2) {
                    optimal = twotoi;
                    na = a;
                }
            }
        }
        pna[0] = na;
        return optimal;
    }

    /**
     * {@code luaH_resize}：重建两段，把旧内容搬过去。
     * <p>
     * 搬的顺序有讲究：先把「数组部分被砍掉的那一段」和「旧哈希部分」的所有键值
     * 收集起来，等新空间就绪后再逐个插入。不能边搬边写目标表——插入过程中若再
     * 触发 rehash，还没搬的数据就丢了。因此这里用 {@code newKey(key, false)}：
     * 空间不足视为实现错误，而不是静默 rehash。
     */
    private void resize(int newArraySize, int newHashSize) {
        LuaValue[] oldArray = array;
        Node[] oldNodes = node;
        int oldNodeSize = nodeSize;

        List<LuaValue[]> moved = new ArrayList<>();

        // 数组部分收缩：被切掉的那一段要搬进哈希部分
        for (int j = newArraySize; j < oldArray.length; j++) {
            if (!oldArray[j].isNil()) {
                moved.add(new LuaValue[] { LuaValue.ofInteger(j + 1), oldArray[j] });
            }
        }

        // 新数组部分，保留前缀
        setArrayVector(newArraySize);
        int keep = Math.min(newArraySize, oldArray.length);
        for (int j = 0; j < keep; j++) {
            array[j].set(oldArray[j]);
        }

        // 旧哈希部分的存活条目
        if (oldNodes != null) {
            for (int i = 0; i < oldNodeSize; i++) {
                Node n = oldNodes[i];
                // 失效条目不搬：搬过去也是垃圾，还白占一个槽
                if (!n.val.isNil() && !isDead(n.key) && !isDead(n.val)) {
                    moved.add(new LuaValue[] { n.key, n.val });
                }
            }
        }

        // 新哈希部分。moved 非空时必须至少有一个槽，否则下面的搬运会失败
        int hashSize = newHashSize;
        if (moved.size() > hashSize) {
            hashSize = moved.size();
        }
        setNodeVector(hashSize);

        for (LuaValue[] kv : moved) {
            insertNoRehash(kv[0], kv[1]);
        }
    }

    /**
     * 搬运旧数据时插入一项，<b>不允许</b>再触发 rehash。
     * <p>
     * 与 {@link #set} 的差别不只是「不许 rehash」：这里必须走 {@link #get}
     * 的路由，让整数键有机会落进新扩出来的数组部分。若图省事直接塞进哈希，
     * 就会破坏「所有 {@code 1..alimit} 的整数键都在数组部分」这条不变式——
     * 那样 {@code get} 会在数组槽上拿到 nil 就返回，哈希里那份数据再也读不到。
     */
    private void insertNoRehash(LuaValue key, LuaValue value) {
        LuaValue slot = rawGet(key);
        if (slot != null) {
            slot.set(value);
            return;
        }
        Node n = newKey(key, false);
        if (n == null) {
            throw new IllegalStateException("hash part too small after resize");
        }
        n.val.set(value);
    }

    private void setArrayVector(int size) {
        LuaValue[] a = new LuaValue[size];
        for (int i = 0; i < size; i++) {
            a[i] = new LuaValue();
        }
        array = a;
    }

    private void setNodeVector(int size) {
        if (size <= 0) {
            node = null;
            nodeSize = 0;
            lastFree = 0;
            return;
        }
        // 向上取整到 2 的幂
        int n = 1;
        while (n < size) {
            n <<= 1;
        }
        Node[] nodes = new Node[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node();
        }
        node = nodes;
        nodeSize = n;
        lastFree = n;
    }

    /**
     * 直接写数组部分的第 {@code k} 项（1 起），必要时只把数组部分扩到 {@code k}。
     * <p>
     * 对应 {@code OP_SETLIST} 的 {@code luaH_resizearray + 直接写}。与
     * {@link #set} 的差别是<b>不走 rehash</b>：表构造的路径已经从字节码里知道
     * 该分配多大，再走一次 rehash 纯属浪费，而且会把刚攒起来的字段搅乱。
     * <p>
     * 不搬哈希部分里落在 {@code [1, k]} 的整数键——C 版也是这样，两边行为一致
     * （数组部分优先于哈希部分，读的时候只会看到数组里的那份）。
     */
    public void setArrayRaw(long k, LuaValue value) {
        if (k <= 0 || isWeak()) {
            // 弱表没有数组部分，表构造也得走哈希
            set(LuaValue.ofInteger(k), value);
            return;
        }
        if (k > array.length) {
            int n = array.length;
            if (n == 0) {
                n = 1;
            }
            while (n < k) {
                n *= 2;
            }
            LuaValue[] bigger = new LuaValue[n];
            System.arraycopy(array, 0, bigger, 0, array.length);
            for (int i = array.length; i < n; i++) {
                bigger[i] = new LuaValue();
            }
            array = bigger;
        }
        array[(int) k - 1].set(value);
    }

    /* ==================== 长度（#t）==================== */

    /**
     * {@code luaH_getn}：返回一个「边界」——{@code t[n]} 非 nil 且 {@code t[n+1]} 为 nil。
     * <p>
     * Lua 只要求返回<b>某个</b>边界，不指定是哪一个，所以这里的二分查找只要落在
     * 合法边界上就算正确。数组部分满且哈希部分非空时，边界可能藏在哈希里，
     * 走 {@code unbound_search}：先指数级试探再二分。
     */
    public long length() {
        int j = array.length;
        if (j > 0 && array[j - 1].isNil()) {
            // 数组部分末尾为空 → 边界在数组内，二分
            int i = 0;
            while (j - i > 1) {
                int m = (i + j) / 2;
                if (array[m - 1].isNil()) {
                    j = m;
                } else {
                    i = m;
                }
            }
            return i;
        }
        if (nodeSize == 0) {
            return j;
        }
        return unboundSearch(j);
    }

    /** {@code unbound_search}：边界在哈希部分，先翻倍试探上界再二分 */
    private long unboundSearch(long j) {
        long i = j;
        j++;
        while (true) {
            LuaValue v = get(LuaValue.ofInteger(j));
            if (v == null || v.isNil()) {
                break;
            }
            i = j;
            if (j > Integer.MAX_VALUE / 2) {
                return i; // 表过大，无法继续翻倍（C 版在此报 "table too big"）
            }
            j *= 2;
        }
        while (j - i > 1) {
            long m = (i + j) / 2;
            LuaValue v = get(LuaValue.ofInteger(m));
            if (v == null || v.isNil()) {
                j = m;
            } else {
                i = m;
            }
        }
        return i;
    }

    /* ==================== 遍历（pairs/next）==================== */

    /**
     * {@code luaH_next}：返回 {@code key} 之后的下一项，形如 {@code [key, value]}；
     * 没有下一项时返回 {@code null}。传入 nil 表示从头开始。
     * <p>
     * 先数组部分后哈希部分，与 C 版一致。顺序本身在 Lua 里是<b>未定义</b>的，
     * 这里不保证任何顺序，只保证每项不重不漏。
     */
    public LuaValue[] next(LuaValue key) {
        purgeIfNeeded();
        int start;
        if (key == null || key.isNil()) {
            start = 0;
        } else if (key.tt == Lua.VNUMINT) {
            long k = key.i;
            if (k > 0 && k <= array.length) {
                start = (int) k; // 下一个数组槽的 0 起下标
            } else {
                int ni = findNodeIndex(key);
                if (ni < 0) {
                    throw new IllegalArgumentException("invalid key to 'next'");
                }
                start = array.length + ni + 1;
            }
        } else {
            int ni = findNodeIndex(key);
            if (ni < 0) {
                throw new IllegalArgumentException("invalid key to 'next'");
            }
            start = array.length + ni + 1;
        }

        for (int i = Math.max(start, 0); i < array.length; i++) {
            if (!array[i].isNil()) {
                return new LuaValue[] { LuaValue.ofInteger(i + 1), array[i] };
            }
        }
        if (nodeSize > 0) {
            for (int j = Math.max(start - array.length, 0); j < nodeSize; j++) {
                if (!node[j].val.isNil() && !isDead(node[j].key) && !isDead(node[j].val)) {
                    return new LuaValue[] { weakUnwrap(node[j].key), weakUnwrap(node[j].val) };
                }
            }
        }
        return null;
    }

    /** 找到键在哈希部分的下标，找不到返回 -1 */
    private int findNodeIndex(LuaValue key) {
        if (nodeSize == 0) {
            return -1;
        }
        int i = mainPosition(key);
        while (true) {
            if (slotKeyEquals(node[i].key, key)) {
                return i;
            }
            if (node[i].next < 0) {
                return -1;
            }
            i = node[i].next;
        }
    }

    /* ==================== 元表 ==================== */

    public LuaTable getMetatable() {
        return metatable;
    }

    public void setMetatable(LuaTable mt) {
        boolean oldK = weakKey;
        boolean oldV = weakValue;
        metatable = mt;
        updateWeakMode();
        if (weakKey != oldK || weakValue != oldV) {
            rewrap();
        }
    }

    /**
     * {@code __mode} 变了之后重排整个表的存储。
     * <p>
     * 弱引用是本实现的<b>存储形式</b>（槽里放的是 {@code WeakRef}），不像 C 版那样
     * 可以在遍历时临时判断「这个键是不是已经死了」。所以强弱一变，已经存在的条目
     * 必须逐个重新包装一遍——变弱时把可收集的对象换成 {@code WeakRef}，变强时换回来。
     * <p>
     * 实测参考实现：先建普通表、塞好条目、再 {@code setmetatable(t, {__mode='k'})}，
     * 那些条目照样会被回收。不做这一步，它们会以强引用的形式永远留在表里。
     */
    private void rewrap() {
        purge();
        List<LuaValue[]> all = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            if (!array[i].isNil()) {
                all.add(new LuaValue[] { LuaValue.ofInteger(i + 1), array[i] });
            }
        }
        for (int i = 0; i < nodeSize; i++) {
            Node n = node[i];
            if (!n.val.isNil() && !isDead(n.key) && !isDead(n.val)) {
                all.add(new LuaValue[] { weakUnwrap(n.key), weakUnwrap(n.val) });
            }
        }
        // 弱表不用数组部分（槽是普通 LuaValue，放不了 WeakRef），整数键一并进哈希
        int arr = isWeak() ? 0 : array.length;
        array = EMPTY_ARRAY;
        node = null;
        nodeSize = 0;
        lastFree = 0;
        setArrayVector(arr);
        setNodeVector(Math.max(all.size(), 1));
        for (LuaValue[] kv : all) {
            set(kv[0], kv[1]);
        }
    }

    /**
     * 按元表里的 {@code __mode} 重算弱标志。
     * <p>
     * <b>是动态读的</b>——实测参考实现：先建一个普通表、塞进条目，之后再
     * {@code setmetatable(t, {__mode='k'})}，那些条目照样会被回收。所以不能只在
     * 建表时看一次。
     * <p>
     * 只认 {@code 'k'} 与 {@code 'v'} 两个字符，其余字符忽略；{@code __mode}
     * 不是字符串时也忽略。实测：{@code {__mode='x'}}、{@code {__mode=5}} 都不报错。
     */
    private void updateWeakMode() {
        boolean k = false;
        boolean v = false;
        if (metatable != null) {
            LuaValue mode = metatable.get(LuaString.latin1("__mode"));
            if (mode != null && mode.isString()) {
                LuaString s = mode.asString();
                for (int i = 0; i < s.length(); i++) {
                    int b = s.byteAt(i);
                    if (b == 'k') {
                        k = true;
                    } else if (b == 'v') {
                        v = true;
                    }
                }
            }
        }
        weakKey = k;
        weakValue = v;
    }

    /* ==================== __gc ==================== */

    /**
     * 有 {@code __gc} 就登记一个哨兵，并记进主线程的可终结清单。
     * <p>
     * <b>只在 {@code setmetatable} 的当下看一次。</b>实测参考实现：先
     * {@code setmetatable(t, mt)}、之后才 {@code mt.__gc = f}，{@code f} 不会被
     * 调用——Lua 是在设元表的那一刻给对象打上「有终结器」标记的。
     * <p>
     * {@code __gc} 不是函数（实测 {@code {__gc = 5}}）时<b>也照常登记</b>：参考
     * 实现里它会在真正调用时炸，而 {@code __gc} 里的错误一律静默吞掉，所以对外
     * 表现就是「什么都没发生」。这里不登记也看不出差别，登记则更贴近 C 版的路径。
     *
     * @param L 当前线程；队列归它的主线程
     * @return 本次是否新登记了一个可终结对象
     */
    boolean markFinalizable(LuaState L) {
        if (gcSentinel != null || gcDone || metatable == null) {
            return false;
        }
        LuaValue tm = metatable.get(LuaString.latin1("__gc"));
        if (tm == null || tm.isNil()) {
            return false;
        }
        gcSentinel = new GcSentinel(this, L.mainThread);
        L.mainThread.registerFinalizable(this);
        return true;
    }

    /**
     * 跑一次 {@code __gc}。
     * <p>
     * <b>{@code __gc} 里的错误一律吞掉</b>——实测参考实现：{@code __gc} 里
     * {@code error} 既不打印也不传播，脚本照常往下跑。
     * <p>
     * 先置 {@code gcDone} 再调，避免元方法里把对象重新挂回全局时再来一遍。
     */
    void runGcMetamethod(LuaState L) {
        if (gcDone) {
            return;
        }
        gcDone = true;
        if (metatable == null) {
            return;
        }
        LuaValue tm = metatable.get(LuaString.latin1("__gc"));
        if (tm == null || tm.isNil()) {
            return;
        }
        try {
            int func = L.top;
            L.ensureSlots(func + 2);
            L.stack[func].set(tm);
            L.stack[func + 1].set(LuaValue.ofTable(this));
            L.top = func + 2;
            /*
             * luaD_callnoyield：__gc 里不许 yield。实测参考实现会在那时报
             * 「attempt to yield across a C-call boundary」，用 L.call 就会让它
             * 「成功」，反而与参考实现不一致。
             */
            L.callNoYield(func, 0);
        } catch (LuaError e) {
            // 静默
        } catch (LuaYield y) {
            // __gc 里 yield：参考实现报跨 C 调用边界，这里同样吞掉
        }
    }

    /* ==================== 诊断 ==================== */

    /** 表中非 nil 项的个数（遍历统计，仅供测试与诊断，不是 O(1)） */
    public int count() {
        purgeIfNeeded();
        int n = 0;
        for (LuaValue v : array) {
            if (!v.isNil()) {
                n++;
            }
        }
        if (nodeSize > 0) {
            for (int i = 0; i < nodeSize; i++) {
                if (!node[i].val.isNil() && !isDead(node[i].key) && !isDead(node[i].val)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 数组部分长度，供测试观察 rehash 行为 */
    public int arrayPartSize() {
        return array.length;
    }

    /** 哈希部分长度，供测试观察 rehash 行为 */
    public int hashPartSize() {
        return nodeSize;
    }
}
