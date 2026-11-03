package com.github.esrrhs.javalua;

/**
 * 元方法编号 —— 对应 Lua 5.3.4 {@code ltm.h} 的 {@code TMS} 与
 * {@code ltm.c} 的 {@code luaT_eventname}。
 * <p>
 * 顺序不能动：ORDER TM 与 ORDER OP（算术）对齐。无 {@code TM_CLOSE}（5.4）。
 */
public final class Tm {

    private Tm() {
    }

    public static final int TM_INDEX = 0;
    public static final int TM_NEWINDEX = 1;
    public static final int TM_GC = 2;
    public static final int TM_MODE = 3;
    public static final int TM_LEN = 4;
    /** 最后一个「快速访问」的元方法 */
    public static final int TM_EQ = 5;
    public static final int TM_ADD = 6;
    public static final int TM_SUB = 7;
    public static final int TM_MUL = 8;
    public static final int TM_MOD = 9;
    public static final int TM_POW = 10;
    public static final int TM_DIV = 11;
    public static final int TM_IDIV = 12;
    public static final int TM_BAND = 13;
    public static final int TM_BOR = 14;
    public static final int TM_BXOR = 15;
    public static final int TM_SHL = 16;
    public static final int TM_SHR = 17;
    public static final int TM_UNM = 18;
    public static final int TM_BNOT = 19;
    public static final int TM_LT = 20;
    public static final int TM_LE = 21;
    public static final int TM_CONCAT = 22;
    public static final int TM_CALL = 23;
    public static final int TM_N = 24;

    /** ORDER TM —— 与上面的编号严格同序 */
    public static final String[] EVENT_NAMES = {
        "__index", "__newindex",
        "__gc", "__mode", "__len", "__eq",
        "__add", "__sub", "__mul", "__mod", "__pow",
        "__div", "__idiv",
        "__band", "__bor", "__bxor", "__shl", "__shr",
        "__unm", "__bnot", "__lt", "__le",
        "__concat", "__call"
    };
}
