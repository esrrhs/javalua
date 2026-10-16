package com.github.esrrhs.fakescript.lua;

/**
 * {@code __gc} 的落地手段 —— 一个只被目标表引用的哨兵。
 * <p>
 * <b>为什么要有这个中间对象。</b>要跑 {@code __gc(t)}，就得在跑的那一刻还握着
 * {@code t}。Java 里所有「对象已死」的通知机制（{@code WeakReference} 被清、
 * {@code Cleaner} / {@code PhantomReference} 入队）都发生在对象<b>已经取不到</b>
 * 之后——引用一清就再也拿不回来。而 C 版没有这个问题：{@code lgc.c: GCTM} 是在
 * mark 阶段之后、对象还在内存里时直接拿着指针调元方法。
 * <p>
 * 唯一能在对象尚可访问时被调用的是 {@link Object#finalize()}——它被调的那一刻
 * {@code this} 还能用，而且<b>允许复活</b>（把 {@code this} 重新交给别人保存）。
 * 于是这里绕一圈：
 * <ol>
 *   <li>表持有一个哨兵（且只有表持有它），哨兵反过来强引用表；</li>
 *   <li>脚本一丢开表，这一对一起变成不可达；</li>
 *   <li>JVM 调哨兵的 {@code finalize}，哨兵趁机把表塞进待终结队列——
 *       表就此复活；</li>
 *   <li>下一次 {@code collectgarbage()} 在 <b>Lua 线程</b>上排空队列，
 *       真正调用 {@code __gc}。</li>
 * </ol>
 * <b>第 3、4 步必须分开。</b>{@code finalize} 跑在 JVM 的终结线程上，在那里调
 * Lua 函数等于在另一个线程上动 {@code LuaState}，数据竞争无可避免；而且终结线程
 * 上抛异常会被 JVM 直接吞掉（连日志都没有）。所以那里只做「入队」这一件事。
 * <p>
 * 放在哨兵上而不是直接给 {@link LuaTable} 写 {@code finalize}，是为了<b>不给所有
 * 表付这份代价</b>：带终结器的对象在 JVM 里至少要两轮 GC 才收得掉，还要进终结
 * 队列。只有真挂了 {@code __gc} 的表才需要它。
 * <p>
 * {@code finalize} 已被标记为 deprecated，这是唯一的出路，别无选择。若将来
 * {@code --finalization=disabled} 成为默认，这里会静默失效——那也是它该有的反应：
 * 那时 {@code __gc} 确实没法跑了。
 */
final class GcSentinel {

    private final LuaTable owner;

    /** 队列挂在主线程上：哨兵是在 {@code newThread} 之前还是之后创建的无从保证 */
    private final LuaState main;

    GcSentinel(LuaTable owner, LuaState main) {
        this.owner = owner;
        this.main = main;
    }

    /**
     * 表不可达 → 哨兵也不可达 → JVM 调这里。
     * <p>
     * 这里唯一的活就是把 {@code owner} 拽回可达集合。它只被 {@link #owner} 强
     * 引用着，而 {@code owner} 这条边不算「脚本的引用」，所以一旦脚本丢开表，
     * 两者一起不可达——这正是我们要的判据。
     */
    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() {
        main.enqueueFinalizer(owner);
    }
}
