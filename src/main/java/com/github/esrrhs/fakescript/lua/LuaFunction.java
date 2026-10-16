package com.github.esrrhs.fakescript.lua;

/**
 * C 函数 —— 对应 {@code lua.h} 的 {@code lua_CFunction}。
 * <p>
 * 约定与 C 版一一对应，只是把「栈」换成了显式的下标：
 * <ul>
 *   <li>调用时 {@code L.ci.func} 是函数自身的下标，参数在
 *       {@code L.ci.func + 1 .. L.top - 1}；</li>
 *   <li>返回值必须<b>从当前 {@code L.top} 开始连续写入</b>，返回它们的个数；</li>
 *   <li>{@link LuaState} 负责按调用方期望的结果数截断或补 nil。</li>
 * </ul>
 * <p>
 * 抛出的 {@link LuaError} 等价于 C 版的 {@code lua_error}（longjmp），
 * 会被引擎的 {@code pcall} 边界捕获；其余 Java 异常按「实现缺陷」处理，继续上冒。
 */
public interface LuaFunction {

    /**
     * @param L 当前状态。{@code L.ci} 是本次调用的帧
     * @return 返回值的个数
     */
    int call(LuaState L);
}
