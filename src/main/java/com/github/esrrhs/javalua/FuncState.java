package com.github.esrrhs.javalua;

/**
 * 单个函数的编译状态 —— 对应 {@code lparser.h} 的 {@code FuncState}。
 * <p>
 * 每进入一个函数体就压一个新的 FuncState，{@link #prev} 串起外层，upvalue 的
 * 解析就是顺着这条链往上找局部变量。
 * <p>
 * 与上游的一处结构性差异：<b>没有 {@code nk} / {@code np} / {@code nabslineinfo}</b>。
 * C 里这三个是「已用长度」，与数组的「已分配长度」分开；这里数组由 {@link Proto}
 * 自己增长，两者恒等——留着两套计数只会给「改了一个忘了另一个」留位置。
 * 于是 {@code nk} 就是 {@code f.sizek}，{@code np} 就是 {@code f.sizep}。
 * <p>
 * 常量缓存<b>不在这里</b>：上游的 {@code ls->h} 属于词法状态（整份源码共享，
 * 跨函数），见 {@link Lexer#lookupConstant}。
 */
public final class FuncState {

    /** 当前函数的原型 */
    public final Proto f;
    /** 外层函数 */
    public final FuncState prev;
    /** 词法状态（报错要用它带上位置信息） */
    public final Lexer ls;
    /** 跨函数共享的动态结构 */
    public final DynData dyd;

    /** 下一条指令的写入位置（等价于 sizecode） */
    public int pc;
    /** 最后一个跳转目标的位置（防止跨基本块做优化） */
    public int lasttarget;
    /** 上一次存进 lineinfo 的行号 */
    public int previousline;
    /** 本函数第一个局部变量在 dyd.actvar 里的下标 */
    public int firstlocal;
    /** 本函数第一个标签在 dyd.label 里的下标 */
    public int firstlabel;
    /** f.locvars 里已用的条目数 */
    public int ndebugvars;
    /** 活跃局部变量数（= 寄存器栈上被变量占住的部分） */
    public int nactvar;
    /** upvalue 数 */
    public int nups;
    /** 第一个空闲寄存器 */
    public int freereg;
    /** 距上次写绝对行号以来发出的指令数 */
    public int iwthabs;
    /** 返回时需要关闭 upvalue */
    public boolean needclose;
    /**
     * 当前块（{@code fs->bl}）。
     * <p>
     * C 里 {@code BlockCnt} 是栈上对象，靠这个指针串成链；这里只能堆分配，
     * 但链的用法（进出块时压弹）完全一样。
     */
    public Parser.BlockCnt bl;

    public FuncState(Proto f, FuncState prev, Lexer ls, DynData dyd) {
        this.f = f;
        this.prev = prev;
        this.ls = ls;
        this.dyd = dyd;
    }

    /**
     * {@code luaY_nvarstack}：寄存器栈上被局部变量占住的寄存器数。
     * <p>
     * 不能简单地返回 {@link #nactvar}：编译期常量（{@code <const>}）不占寄存器，
     * 所以要看 {@code nactvar} 之下「最高的那个真变量」用了哪个寄存器。
     * {@code local a <const> = 1; local b = 2} 里 {@code b} 在 r0 而不是 r1。
     */
    public int nvarstack() {
        return reglevel(this, nactvar);
    }

    /**
     * {@code lparser.c: reglevel}：把「编译器视角的变量序号」换算成寄存器号。
     * <p>
     * 从上往下找第一个<b>不在常量区</b>的变量，返回它的寄存器 +1。
     * 跳过 RDKCTC 是必须的：常量的 {@code ridx} 从来没被赋过值。
     */
    public static int reglevel(FuncState fs, int nvar) {
        while (nvar-- > 0) {
            DynData.Vardesc vd = fs.dyd.actvar.get(fs.firstlocal + nvar);
            if (vd.kind != DynData.RDKCTC) {
                return vd.ridx + 1;
            }
        }
        return 0;
    }
}
