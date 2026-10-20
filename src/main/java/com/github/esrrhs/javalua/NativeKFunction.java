package com.github.esrrhs.javalua;

/**
 * C 扩展注册的<b>续体</b>（{@code lua_KFunction}）在 Java 侧的壳。
 * <p>
 * 与 {@link LuaNativeFunction} 是同一套返回码约定，只在入口多两个参数：
 * {@code status}（{@code LUA_YIELD} 或错误码）与 {@code ctx}。
 * <p>
 * <b>续体必然是从 {@code resume} 那条路上调进来的</b>，所以它自己也带着
 * {@code setjmp} 落点——里面照样可以再 {@code lua_yieldk}、照样可以抛错，
 * 两条路都从 {@link NativeBridge#invokeK} 的返回码出来。
 */
final class NativeKFunction implements LuaKFunction {

    /** C 函数指针 */
    private final long fn;

    NativeKFunction(long fn) {
        this.fn = fn;
    }

    @Override
    public int call(LuaState L, int status, int ctx) {
        int n = NativeBridge.invokeK(fn, NativeBridge.handle(L), status, ctx);
        if (n >= 0) {
            return n;
        }
        Throwable t = NativeBridge.takePending();
        if (n == -2) {
            // 续体里又 yield 了：交回 resume，下一趟再接着跑这个续体
            throw (t instanceof LuaYield) ? (LuaYield) t : LuaYield.INSTANCE;
        }
        if (t instanceof LuaError) {
            throw (LuaError) t;
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t != null) {
            throw new LuaError(LuaValue.ofString(
                    LuaString.latin1(String.valueOf(t.getMessage()))), "");
        }
        throw new LuaError(new LuaValue(L.stack[L.top - 1]), "");
    }
}
