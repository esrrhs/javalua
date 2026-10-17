package com.github.esrrhs.fakescript.lua;

/**
 * Lua 错误 —— 携带一个 Lua 值，对应 C 版 {@code lua_error} 的 longjmp。
 * <p>
 * C 版用 {@code setjmp/longjmp} 做非局部跳转，{@code pcall} / {@code xpcall} /
 * 协程边界各自 {@code setjmp} 一次。这里换成异常：抛出即 longjmp，捕获即
 * {@code setjmp} 命中。
 * <p>
 * 只有这个类型会被 {@code pcall} 捕获，这一点很关键：{@code error()} 抛出的
 * 是它，而本实现自身的 Java 异常（bug、栈溢出等）不是——后者必须继续往上冒，
 * 否则会被引擎静默吞掉变成「脚本报了个错」，把实现缺陷伪装成脚本问题。
 */
public class LuaError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误值。可以是任意 Lua 值，不一定是字符串 */
    public final LuaValue value;

    /** 是否带有位置信息（对应 {@code luaL_where} 加上的 {@code file:line:}） */
    public String where;

    /** 是否可加回溯（对应 {@code luaL_traceback}）。{@code error(msg, 0)} 时不加 */
    public boolean addTraceback = true;

    /**
     * {@code LUA_ERRERR}：错误处理过程本身又出错了。
     * <p>
     * {@code ldo.c: luaD_seterrorobj} 对这一档不取栈顶那个错误值，而是固定写
     * {@code "error in error handling"}。看起来像句废话，但它是<b>可观测</b>的：
     * 上游 {@code cstack.lua} 第一个用例就是 {@code xpcall(loop, loop)}——
     * 消息处理函数自己也在无限递归，最终断言 {@code msg == "error in error
     * handling"}。带位置前缀就会被 {@code ==} 判否。
     */
    public boolean errErr;

    public LuaError(LuaValue value) {
        super(value == null ? "nil" : value.toString(), null, false, false);
        this.value = value;
    }

    public LuaError(LuaValue value, String where) {
        this(value);
        this.where = where;
    }
}
