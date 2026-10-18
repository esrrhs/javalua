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
     * 是不是 <b>{@code luaD_throw} 直跳</b>出来的（语法错误、{@code load} 的
     * mode 错误、二进制 chunk 的畸形）。
     * <p>
     * C 版有两条抛错的路，只有一条会过消息处理函数：
     * <ul>
     * <li>{@code luaG_errormsg}：先调 {@code L->errfunc} 再跳。{@code error()}、
     *     {@code luaL_error}、{@code luaG_runerror}（含「C stack overflow」）走这条。</li>
     * <li>{@code luaD_throw}：<b>直接跳</b>。{@code lexerror}（{@code LUA_ERRSYNTAX}）、
     *     {@code checkmode}、{@code lundump} 的 {@code error()} 走这条。</li>
     * </ul>
     * 可观测的场合是 {@code xpcall} 里 {@code load(读函数)}：
     * {@code luaD_protectedparser} 把 {@code L->errfunc} 原样传进内层保护区，
     * 于是读函数里 {@code error("boom")} 的<b>抛出点</b>就被 {@code xpcall}
     * 的处理器拦下了 —— 实测 {@code load} 返回
     * {@code nil, "H:case.lua:13: boom"}，而语法错误返回的是裸消息。
     */
    public boolean directThrow;

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
