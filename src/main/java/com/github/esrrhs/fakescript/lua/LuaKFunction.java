package com.github.esrrhs.fakescript.lua;

/**
 * C 函数在 {@code yield} 之后的<b>续体</b> —— 对应 {@code lua.h} 的
 * {@code lua_KFunction}。
 * <p>
 * Lua 5.4 里一个 C 函数想在自己调用 Lua 代码的过程中允许 {@code yield}，
 * 就必须提供续体：{@code lua_callk} / {@code lua_pcallk} 在 yield 发生时把
 * 控制权交回 {@code lua_resume}，等协程被再次 resume 时，<b>不是</b>回到 C
 * 函数里被打断的那一行（C 的栈帧早没了），而是调用这个续体把剩下的活干完。
 * <p>
 * 这里的情况完全一样：我们的 {@code pcall} / {@code xpcall} 是 Java 方法，
 * {@code yield} 用异常抛出后 Java 栈帧同样没了，所以只能把「收尾」写成续体。
 *
 * @param L      发生续体的线程
 * @param status {@code LUA_OK} / {@code LUA_YIELD} / 错误码
 * @param ctx    注册续体时存下的上下文
 * @return 这个函数真正返回的结果个数（会被 {@code luaD_poscall} 搬到位）
 */
public interface LuaKFunction {

    int call(LuaState L, int status, int ctx);
}
