package com.github.esrrhs.fakescript.lua;

/**
 * 调用帧 —— 对应 {@code lstate.h} 的 {@code CallInfo}。
 * <p>
 * C 版用指针（{@code func.p} / {@code top.p}）在栈上定位，这里换成<b>栈下标</b>。
 * 等价性依赖于 {@link LuaState} 的不变式：栈扩容时只复制数组、不替换槽对象，
 * 所以下标与「槽对象引用」两种定位方式在语义上一致。
 * <p>
 * C 版把 {@code u.l.savedpc} 与 {@code u.c.*} 放在一个 union 里区分 Lua / C 帧，
 * 这里摊平成字段，用 {@link #isLua} 区分含义。
 */
public final class CallInfo {

    /** 被调用函数在栈上的下标；参数紧跟其后 */
    int func;

    /** 本帧可用的栈顶（下标），对应 {@code ci->top.p} */
    int top;

    /** 调用方期望的结果数；{@link LuaState#MULTRET} 表示「全部」 */
    int nresults;

    /* ---- Lua 帧 ---- */

    /** 下一条待执行指令的 pc（对应 {@code u.l.savedpc}） */
    int savedpc;

    /** 可变参数里「额外参数」的个数（{@code u.l.nextraargs}） */
    int nextraargs;

    /* ---- 通用 ---- */

    /**
     * {@code u2.nres}：{@code OP_RETURN} 关闭包时暂存的结果数，
     * 供 {@code luaV_finishOp} 在 yield 后恢复。本实现不做 yield，
     * 但字段留着，保持与上游一一对应。
     */
    int nres;

    /** 本帧是 Lua 函数（false 表示 C 函数） */
    boolean isLua = true;

    /**
     * {@code CIST_FRESH}：这一帧是 {@code luaV_execute} 的入口帧。
     * 返回时若是 fresh 帧就退出解释器，否则回到调用者的帧继续跑——
     * 这就是 {@code OP_CALL} 不递归而直接 {@code goto startfunc} 的代价：
     * 得自己记住「该在哪一层停下来」。
     */
    boolean fresh;

    /** {@code CIST_TAIL}：是一次尾调用 */
    boolean tail;

    /** {@code CIST_LEQ}：正在用 {@code __lt} 顶替缺失的 {@code __le} */
    boolean leq;

    CallInfo previous;

    /** 本帧的名字，仅供错误信息与调试 */
    String what = "?";
}
