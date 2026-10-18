package com.github.esrrhs.fakescript.lua;

/**
 * 一个 {@code lua_CFunction} —— 对应 C 里那个函数指针。
 * <p>
 * 从 C 进来时它只是一个地址；套上这个类才能当 Lua 值用（{@link Lua#VLCF}），
 * 也才能被引擎调用。{@link #call} 里那三个返回码是 {@code NativeBridge#invokeC}
 * 给的，各自对应 C 侧的一种出口：
 * <ul>
 *   <li>{@code >= 0}：正常返回，是返回值个数；</li>
 *   <li>{@code -1}：{@code lua_error} 的 longjmp，错误对象在栈顶，
 *       要按 {@code lua_error} 的语义抛出去；</li>
 *   <li>{@code -2}：{@code lua_yieldk}，要抛 {@link LuaYield}。</li>
 * </ul>
 */
public final class LuaNativeFunction implements LuaFunction {

    /** C 函数指针 */
    public final long fn;

    public LuaNativeFunction(long fn) {
        this.fn = fn;
    }

    @Override
    public int call(LuaState L) {
        long h = NativeBridge.handle(L);
        int n = NativeBridge.invokeC(fn, h);
        if (n >= 0) {
            return n;
        }
        Throwable t = NativeBridge.takePending();
        if (n == -2) {
            /*
             * yield。两种来源：①C 函数自己调了 {@code lua_yieldk}（C 侧置
             * {@code g_yielding} 后 longjmp，Java 这头没抛过，{@code t} 为 null）；
             * ②被调的 Lua 代码 yield 了，Java 异常一路冒过 C 帧——那时
             * {@code luashim.c} 靠 {@code IsInstanceOf(LuaYield)} 认出它是 yield
             * 而不是 error，{@code t} 就是那个 {@link LuaYield}。
             * 两种都往上抛，绝不包成 {@link LuaError}：包了就穿不出
             * {@code pcall}，而 yield 是能穿过 pcall 的。
             */
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
        // -1 但 Java 侧没抛：C 代码自己 lua_error 的，错误对象就是栈顶那个
        throw new LuaError(new LuaValue(L.stack[L.top - 1]), "");
    }

    @Override
    public String toString() {
        return "function: native 0x" + Long.toHexString(fn);
    }
}
