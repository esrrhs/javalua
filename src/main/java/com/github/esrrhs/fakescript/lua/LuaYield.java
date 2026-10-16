package com.github.esrrhs.fakescript.lua;

/**
 * {@code luaD_throw(L, LUA_YIELD)} 的 Java 形态。
 * <p>
 * C 版用 {@code longjmp} 一跳到底，这里用异常把「挂起」一层层带出 Java 调用栈。
 * 之所以能这么做，是因为<b>Lua 那边的状态全在堆上</b>：栈、调用帧、开放 upvalue
 * 都是 {@link LuaState} 里的对象，异常只弹掉 Java 栈帧，Lua 侧原样留着。
 * 等 {@code resume} 接住它，就能从 {@code L.ci} 继续跑。
 * <p>
 * 两条硬性约束：
 * <ul>
 *   <li><b>不能被 {@code pcall} 捕获。</b>{@code pcall} 只捕 {@link LuaError}，
 *       所以 yield 能穿过 {@code pcall} / {@code xpcall}——实测参考实现就是
 *       这样（{@code pcall(function() return coroutine.yield(1) end)} 正常挂起）；</li>
 *   <li><b>单例、不填栈轨迹。</b>yield 是控制流不是故障，每次 new 一个异常并
 *       抓栈轨迹会让协程比函数调用还慢。</li>
 * </ul>
 */
final class LuaYield extends RuntimeException {

    static final LuaYield INSTANCE = new LuaYield();

    private LuaYield() {
        super(null, null, false, false);
    }
}
