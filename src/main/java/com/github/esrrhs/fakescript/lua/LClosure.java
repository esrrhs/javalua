package com.github.esrrhs.fakescript.lua;

/**
 * Lua 闭包 —— 对应 {@code lobject.h} 的 {@code LClosure}。
 * <p>
 * 闭包 = 原型 + upvalue 数组。同一个 {@link Proto} 可以被任意多个闭包共享
 * （循环里 {@code local f = function() end} 就会共享），可变的部分只有
 * {@code upvals}，这与 C 版一致。
 * <p>
 * 放入 {@link LuaValue} 时用标签 {@link Lua#VLIF}。
 */
public final class LClosure {

    public final Proto p;

    /** upvalue 数组，长度等于 {@code p.sizeupvalues}（主 chunk 例外，见下） */
    public final UpVal[] upvals;

    public LClosure(Proto p) {
        this(p, p.sizeupvalues);
    }

    /**
     * 用显式的 upvalue 个数构造。
     * <p>
     * 主 chunk 必须走这条路：{@code luaY_parser} 固定按 1 个 upvalue 建闭包
     * （{@code _ENV}），之后由 {@code lua_load} 把全局表塞进去，与
     * {@code p.sizeupvalues} 是否相等无关。
     */
    public LClosure(Proto p, int nupvals) {
        this.p = p;
        this.upvals = new UpVal[Math.max(nupvals, p.sizeupvalues)];
    }

    /** 把所有 upvalue 初始化为「关闭且值为 nil」（{@code lfunc.c: luaF_initupvals}） */
    public void initUpvals() {
        for (int i = 0; i < upvals.length; i++) {
            if (upvals[i] == null) {
                upvals[i] = UpVal.closed(LuaValue.ofNil());
            }
        }
    }
}
