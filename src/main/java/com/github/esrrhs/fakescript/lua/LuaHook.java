package com.github.esrrhs.fakescript.lua;

/**
 * 调试钩子 —— 对应 {@code lua.h} 的 {@code lua_Hook}。
 * <p>
 * C 版是一个函数指针 {@code void (*)(lua_State *L, lua_Debug *ar)}，由
 * {@code luaD_hook} 在解释器的各个调用点上调用。这里用接口，语义一致。
 * <p>
 * <b>钩子是在「被跟踪的那一帧」上调用的</b>，而不是在自己的帧上：
 * {@code luaD_hook} 把 {@code L->ci}（此刻是被引发事件的函数帧）装进
 * {@code ar.i_ci}，然后把钩子函数作为<b>新的一帧</b>压在上面。所以钩子函数里
 * {@code debug.getinfo(2)} 拿到的正是引发事件的那一帧——这是
 * {@code debug.getinfo(2, "r").ntransfer} 能读到返回值个数的全部原因。
 */
public interface LuaHook {

    /**
     * @param L  引发事件的线程
     * @param ar 事件信息；{@code ar.event} 是 {@link Lua#LUA_HOOKCALL} 等
     */
    void hook(LuaState L, DebugLib.LuaDebug ar);
}
