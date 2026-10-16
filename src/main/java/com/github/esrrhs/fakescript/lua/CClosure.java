package com.github.esrrhs.fakescript.lua;

/**
 * C 闭包 —— 对应 {@code lobject.h} 的 {@code CClosure}。
 * <p>
 * 与 Lua 闭包（{@link LClosure}）的区别只有一个：绑定的是<b>值</b>而不是
 * upvalue 槽。C 版 {@code CClosure} 里直接放着 {@code TValue upvalue[]}，
 * 因为 C 函数没有栈上的局部变量可供开放 upvalue 指向，绑成值就够了。
 * <p>
 * 现存代码里第一个真正需要它的是 {@code coroutine.wrap}：
 * {@code lcorolib.c} 用 {@code lua_pushcclosure(L, luaB_auxwrap, 1)} 把协程
 * 对象绑进闭包，之后每次调用 wrap 出来的函数都从 upvalue 里取回那个协程。
 * 用全局变量或表来存都不行——那样两个 wrap 会互相串。
 * <p>
 * 放入 {@link LuaValue} 时用标签 {@link Lua#VCCL}。
 */
public final class CClosure {

    public final LuaFunction f;

    /** 绑定上来的值，长度在构造时固定 */
    public final LuaValue[] upvalue;

    public CClosure(LuaFunction f, int nupvals) {
        this.f = f;
        this.upvalue = new LuaValue[nupvals];
        for (int i = 0; i < nupvals; i++) {
            this.upvalue[i] = LuaValue.ofNil();
        }
    }
}
