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

    /**
     * {@code u.l.trap}：本帧是否要在每条指令前停下来查钩子。
     * <p>
     * 这是「钩子开销为零」的关键：钩子没开时 {@code trap} 是 0，主循环连
     * {@code luaG_traceexec} 都不进。它由三处写：
     * {@code luaG_tracecall}（进函数时置 1）、{@code luaG_traceexec}
     * （没开行/计数钩子时置 0）、以及 {@code lua_sethook} 的
     * {@code settraps}——最后这一处才是「执行到一半打开钩子」也能立刻生效的
     * 原因，因为主循环的 {@code trap} 局部变量只在特定点才从帧里重读。
     */
    boolean trap;

    /**
     * {@code CIST_HOOKED}：本帧正在被钩子跟踪。
     * <p>
     * 作用有二：一是 {@code luaD_hook} 期间置位，让钩子内发起的调用不再触发
     * 新钩子（配合 {@code allowhook}）；二是让 {@code funcnamefromcall} 把
     * 回溯里的这一层打印成 {@code in hook '?'}。
     */
    boolean hooked;

    /**
     * {@code CIST_FIN}：本帧正在跑一个 {@code __gc} 终结器。
     * <p>
     * 注意这个标记打在<b>发起终结的那一帧</b>上，不是终结器自己的帧——
     * {@code lgc.c: GCTM} 是在调用<b>之前</b>给 {@code L->ci} 置位、之后再清掉。
     * 于是终结器里 {@code debug.getinfo(1)} 走
     * {@code funcnamefromcall(ci->previous)} 时能认出来，给出
     * {@code namewhat == "metamethod"}、{@code name == "__gc"}。
     * 上游 {@code db.lua}「testing debug info for finalizers」就断言这两个值，
     * 而且它用 {@code repeat local a = {} until name} 等终结器跑——认不出来
     * 就是死循环。
     */
    boolean fin;

    /**
     * {@code CIST_TRAN}：本帧的 {@code ftransfer / ntransfer} 有效。
     * 只在返回钩子的调用期间为真（{@code debug.getinfo(2, 'r')} 读它）。
     */
    boolean tran;

    /** {@code u2.transferinfo.ftransfer}：第一个返回值相对 {@link #func} 的偏移 */
    int ftransfer;

    /** {@code u2.transferinfo.ntransfer}：返回值的个数 */
    int ntransfer;

    /* ---- 通用 ---- */

    /**
     * {@code u2.nres}：{@code OP_RETURN} 关闭包时暂存的结果数，
     * 供 {@code luaV_finishOp} 在 yield 后恢复。本实现不做 yield，
     * 但字段留着，保持与上游一一对应。
     */
    int nres;

    /** 本帧是 Lua 函数（false 表示 C 函数） */
    boolean isLua = true;

    /* ---- yield 相关（C 版在 {@code u.c.*} 与 {@code u2.*} 里） ---- */

    /**
     * {@code u2.nyield}：本帧 yield 出去的值的个数。
     * {@code lua_resume} 靠它知道该从协程栈上搬几个值回调用方。
     */
    int nyield;

    /**
     * {@code u.c.k}：yield 之后的续体。只有「可能被 yield 打断」的 C 帧才设，
     * 见 {@link LuaKFunction}。
     */
    LuaKFunction k;

    /** {@code u.c.ctx}：续体的上下文 */
    int ctx;

    /**
     * {@code CIST_YPCALL}：本帧是一个被打断的 {@code pcall} / {@code xpcall}，
     * 可以作为错误恢复点。{@code precover} 全靠这个标记找到「该在哪儿继续」。
     */
    boolean yieldPcall;

    /**
     * {@code CIST_RECST}：{@code pcall} 被 yield 打断后、恢复时拿到的错误状态。
     * {@code LUA_OK} 表示不是出错而是被 yield 打断的。
     */
    int recst;

    /** {@code u2.funcidx}：被 {@code pcall} 保护着调用的那个函数的栈下标 */
    int funcIdx;

    /** {@code xpcall} 的消息处理函数；没有就是 {@code null} */
    LuaValue errHandler;

    /** {@code CIST_CLSRET}：C 函数是在关 tbc 变量时被 yield 打断的 */
    boolean clsret;

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
