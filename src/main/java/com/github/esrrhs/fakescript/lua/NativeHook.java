package com.github.esrrhs.fakescript.lua;

/**
 * 一个 {@code lua_Hook} —— C 的函数指针 {@code void (*)(lua_State*, lua_Debug*)}
 * 与本实现 {@link LuaHook} 之间的适配层。
 * <p>
 * 桥的是那一块 {@code lua_Debug}：它是<b>C 侧的结构体</b>，Java 里没有对应物。
 * 所以每次回调现取一块 native 内存、把 {@link DebugLib.LuaDebug} 的字段按
 * {@code luashim.c} 的 {@code DBG_FIELD} 顺序写进去，再把地址交给 C 函数——
 * 它读的正是 {@code ar->event} / {@code ar->currentline} / {@code ar->short_src}
 * 这些。
 * <p>
 * 每线程复用同一块，不每次回调都 {@code malloc}：行钩子在循环里是每条指令
 * 一次，按次分配会把带钩子的脚本拖慢一个数量级。复用是安全的，因为
 * {@code luaD_hook} 在进钩子前先把 {@code allowhook} 置 0，钩子<b>不会重入</b>。
 * <p>
 * 与 {@link LuaNativeFunction} 的分工：那个是「C 函数当 Lua 值用」，
 * 这个是「C 函数当调试钩子用」——出口不同，前者返回值的个数，后者没有返回值。
 */
final class NativeHook implements LuaHook {

    /** C 函数指针 */
    final long fn;

    NativeHook(long fn) {
        this.fn = fn;
    }

    /** {@code sizeof(lua_Debug)} 大小的复用块；每线程一块 */
    private static final ThreadLocal<Long> BUF = new ThreadLocal<>();

    @Override
    public void hook(LuaState L, DebugLib.LuaDebug ar) {
        Long b = BUF.get();
        if (b == null) {
            b = NativeBridge.alloc(NativeBridge.dbgSize());
            if (b == 0) {
                return; // 分不出来就别回调了，总比写坏内存好
            }
            BUF.set(b);
        }
        Api.fillDebug(b, ar);
        NativeBridge.callPtr(fn, NativeBridge.statePtr(NativeBridge.handle(L)), b, 0, 0);
    }

    @Override
    public String toString() {
        return "hook: native 0x" + Long.toHexString(fn);
    }
}
