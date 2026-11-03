package com.github.esrrhs.javalua;

/**
 * 指令集与指令编解码 —— 逐行翻译 Lua 5.3.4 {@code lopcodes.h} / {@code lopcodes.c}。
 * <p>
 * 指令是 32 位字，用 Java {@code int} 承载。C 版的 {@code Instruction} 是无符号
 * 32 位，Java 的 {@code int} 是有符号的：所有取字段一律用 {@code >>>}（无符号右移）
 * 再掩码。带符号字段用「偏移 K」表示：写入值 = 真值 + K。
 * <p>
 * 5.3.4 指令格式（SIZE_OP=6）：
 * <pre>
 * iABC          B(9)     |      C(9)     |     A(8)      |   Op(6)
 * iABx                Bx(18)               |     A(8)      |   Op(6)
 * iAsBx              sBx (signed)(18)      |     A(8)      |   Op(6)
 * iAx                           Ax(26)                     |   Op(6)
 * </pre>
 * RK 操作数：B/C 的最高位（BITRK）为 1 表示常量表下标，否则为寄存器。
 */
public final class OpCode {

    private OpCode() {
    }

    /* ---------- 指令格式 ---------- */

    public static final int IABC = 0;
    public static final int IABX = 1;
    public static final int IASBX = 2;
    public static final int IAX = 3;

    public static final int SIZE_C = 9;
    public static final int SIZE_B = 9;
    public static final int SIZE_BX = SIZE_C + SIZE_B; // 18
    public static final int SIZE_A = 8;
    public static final int SIZE_AX = SIZE_C + SIZE_B + SIZE_A; // 26
    public static final int SIZE_OP = 6;

    public static final int POS_OP = 0;
    public static final int POS_A = POS_OP + SIZE_OP; // 6
    public static final int POS_C = POS_A + SIZE_A; // 14
    public static final int POS_B = POS_C + SIZE_C; // 23
    public static final int POS_BX = POS_C; // 14
    public static final int POS_AX = POS_A; // 6

    public static final int MAXARG_BX = (1 << SIZE_BX) - 1; // 262143
    public static final int MAXARG_SBX = MAXARG_BX >> 1; // 131071
    public static final int OFFSET_SBX = MAXARG_SBX;
    public static final int MAXARG_AX = (1 << SIZE_AX) - 1;
    public static final int MAXARG_A = (1 << SIZE_A) - 1;
    public static final int MAXARG_B = (1 << SIZE_B) - 1;
    public static final int MAXARG_C = (1 << SIZE_C) - 1;

    /** BITRK：B/C 最高位为 1 表示常量 */
    public static final int BITRK = 1 << (SIZE_B - 1); // 256
    public static final int MAXINDEXRK = BITRK - 1; // 255

    /** {@code NO_REG}：一个能塞进 8 位的非法寄存器号 */
    public static final int NO_REG = MAXARG_A;

    /** {@code LFIELDS_PER_FLUSH} */
    public static final int LFIELDS_PER_FLUSH = 50;

    /** {@code MAXUPVAL}（lfunc.h） */
    public static final int MAXUPVAL = 255;

    /* ---------- OpArgMask ---------- */

    public static final int OpArgN = 0;
    public static final int OpArgU = 1;
    public static final int OpArgR = 2;
    public static final int OpArgK = 3;

    /* ---------- 操作码（ORDER OP，与 5.3.4 enum 严格一致） ---------- */

    public static final int OP_MOVE = 0;
    public static final int OP_LOADK = 1;
    public static final int OP_LOADKX = 2;
    public static final int OP_LOADBOOL = 3;
    public static final int OP_LOADNIL = 4;
    public static final int OP_GETUPVAL = 5;
    public static final int OP_GETTABUP = 6;
    public static final int OP_GETTABLE = 7;
    public static final int OP_SETTABUP = 8;
    public static final int OP_SETUPVAL = 9;
    public static final int OP_SETTABLE = 10;
    public static final int OP_NEWTABLE = 11;
    public static final int OP_SELF = 12;
    public static final int OP_ADD = 13;
    public static final int OP_SUB = 14;
    public static final int OP_MUL = 15;
    public static final int OP_MOD = 16;
    public static final int OP_POW = 17;
    public static final int OP_DIV = 18;
    public static final int OP_IDIV = 19;
    public static final int OP_BAND = 20;
    public static final int OP_BOR = 21;
    public static final int OP_BXOR = 22;
    public static final int OP_SHL = 23;
    public static final int OP_SHR = 24;
    public static final int OP_UNM = 25;
    public static final int OP_BNOT = 26;
    public static final int OP_NOT = 27;
    public static final int OP_LEN = 28;
    public static final int OP_CONCAT = 29;
    public static final int OP_JMP = 30;
    public static final int OP_EQ = 31;
    public static final int OP_LT = 32;
    public static final int OP_LE = 33;
    public static final int OP_TEST = 34;
    public static final int OP_TESTSET = 35;
    public static final int OP_CALL = 36;
    public static final int OP_TAILCALL = 37;
    public static final int OP_RETURN = 38;
    public static final int OP_FORLOOP = 39;
    public static final int OP_FORPREP = 40;
    public static final int OP_TFORCALL = 41;
    public static final int OP_TFORLOOP = 42;
    public static final int OP_SETLIST = 43;
    public static final int OP_CLOSURE = 44;
    public static final int OP_VARARG = 45;
    public static final int OP_EXTRAARG = 46;

    public static final int NUM_OPCODES = OP_EXTRAARG + 1; // 47



    /* ---------- RK ---------- */

    /** {@code ISK} */
    public static boolean isK(int x) {
        return (x & BITRK) != 0;
    }

    /** {@code INDEXK} */
    public static int indexK(int r) {
        return r & ~BITRK;
    }

    /** {@code RKASK} */
    public static int rkAsk(int x) {
        return x | BITRK;
    }

    /* ---------- opmodes（lopcodes.c） ---------- */

    private static int opmode(int t, int a, int b, int c, int m) {
        return ((t) << 7) | ((a) << 6) | ((b) << 4) | ((c) << 2) | (m);
    }

    private static final int[] OPMODES = {
        opmode(0, 1, OpArgR, OpArgN, IABC),  /* OP_MOVE */
        opmode(0, 1, OpArgK, OpArgN, IABX),  /* OP_LOADK */
        opmode(0, 1, OpArgN, OpArgN, IABX),  /* OP_LOADKX */
        opmode(0, 1, OpArgU, OpArgU, IABC),  /* OP_LOADBOOL */
        opmode(0, 1, OpArgU, OpArgN, IABC),  /* OP_LOADNIL */
        opmode(0, 1, OpArgU, OpArgN, IABC),  /* OP_GETUPVAL */
        opmode(0, 1, OpArgU, OpArgK, IABC),  /* OP_GETTABUP */
        opmode(0, 1, OpArgR, OpArgK, IABC),  /* OP_GETTABLE */
        opmode(0, 0, OpArgK, OpArgK, IABC),  /* OP_SETTABUP */
        opmode(0, 0, OpArgU, OpArgN, IABC),  /* OP_SETUPVAL */
        opmode(0, 0, OpArgK, OpArgK, IABC),  /* OP_SETTABLE */
        opmode(0, 1, OpArgU, OpArgU, IABC),  /* OP_NEWTABLE */
        opmode(0, 1, OpArgR, OpArgK, IABC),  /* OP_SELF */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_ADD */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_SUB */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_MUL */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_MOD */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_POW */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_DIV */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_IDIV */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_BAND */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_BOR */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_BXOR */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_SHL */
        opmode(0, 1, OpArgK, OpArgK, IABC),  /* OP_SHR */
        opmode(0, 1, OpArgR, OpArgN, IABC),  /* OP_UNM */
        opmode(0, 1, OpArgR, OpArgN, IABC),  /* OP_BNOT */
        opmode(0, 1, OpArgR, OpArgN, IABC),  /* OP_NOT */
        opmode(0, 1, OpArgR, OpArgN, IABC),  /* OP_LEN */
        opmode(0, 1, OpArgR, OpArgR, IABC),  /* OP_CONCAT */
        opmode(0, 0, OpArgR, OpArgN, IASBX), /* OP_JMP */
        opmode(1, 0, OpArgK, OpArgK, IABC),  /* OP_EQ */
        opmode(1, 0, OpArgK, OpArgK, IABC),  /* OP_LT */
        opmode(1, 0, OpArgK, OpArgK, IABC),  /* OP_LE */
        opmode(1, 0, OpArgN, OpArgU, IABC),  /* OP_TEST */
        opmode(1, 1, OpArgR, OpArgU, IABC),  /* OP_TESTSET */
        opmode(0, 1, OpArgU, OpArgU, IABC),  /* OP_CALL */
        opmode(0, 1, OpArgU, OpArgU, IABC),  /* OP_TAILCALL */
        opmode(0, 0, OpArgU, OpArgN, IABC),  /* OP_RETURN */
        opmode(0, 1, OpArgR, OpArgN, IASBX), /* OP_FORLOOP */
        opmode(0, 1, OpArgR, OpArgN, IASBX), /* OP_FORPREP */
        opmode(0, 0, OpArgN, OpArgU, IABC),  /* OP_TFORCALL */
        opmode(0, 1, OpArgR, OpArgN, IASBX), /* OP_TFORLOOP */
        opmode(0, 0, OpArgU, OpArgU, IABC),  /* OP_SETLIST */
        opmode(0, 1, OpArgU, OpArgN, IABX),  /* OP_CLOSURE */
        opmode(0, 1, OpArgU, OpArgN, IABC),  /* OP_VARARG */
        opmode(0, 0, OpArgU, OpArgU, IAX),   /* OP_EXTRAARG */
    };

    public static final String[] NAMES = {
        "MOVE", "LOADK", "LOADKX", "LOADBOOL", "LOADNIL", "GETUPVAL",
        "GETTABUP", "GETTABLE", "SETTABUP", "SETUPVAL", "SETTABLE",
        "NEWTABLE", "SELF",
        "ADD", "SUB", "MUL", "MOD", "POW", "DIV", "IDIV",
        "BAND", "BOR", "BXOR", "SHL", "SHR",
        "UNM", "BNOT", "NOT", "LEN", "CONCAT",
        "JMP", "EQ", "LT", "LE", "TEST", "TESTSET",
        "CALL", "TAILCALL", "RETURN",
        "FORLOOP", "FORPREP", "TFORCALL", "TFORLOOP",
        "SETLIST", "CLOSURE", "VARARG", "EXTRAARG",
    };

    public static int getOpMode(int op) {
        return OPMODES[op] & 3;
    }

    public static int getBMode(int op) {
        return (OPMODES[op] >> 4) & 3;
    }

    public static int getCMode(int op) {
        return (OPMODES[op] >> 2) & 3;
    }

    public static boolean testAMode(int op) {
        return (OPMODES[op] & (1 << 6)) != 0;
    }

    public static boolean testTMode(int op) {
        return (OPMODES[op] & (1 << 7)) != 0;
    }

    public static String name(int op) {
        return NAMES[op];
    }

    /* ---------- 编解码 ---------- */

    private static int mask1(int n, int p) {
        return (~((~0) << n)) << p;
    }

    private static int getarg(int i, int pos, int size) {
        return (i >>> pos) & mask1(size, 0);
    }

    private static int setarg(int i, int v, int pos, int size) {
        return (i & ~mask1(size, pos)) | ((v << pos) & mask1(size, pos));
    }

    public static int getOpCode(int i) {
        return getarg(i, POS_OP, SIZE_OP);
    }

    public static int setOpCode(int i, int op) {
        return setarg(i, op, POS_OP, SIZE_OP);
    }

    public static int getArgA(int i) {
        return getarg(i, POS_A, SIZE_A);
    }

    public static int setArgA(int i, int v) {
        return setarg(i, v, POS_A, SIZE_A);
    }

    public static int getArgB(int i) {
        return getarg(i, POS_B, SIZE_B);
    }

    public static int setArgB(int i, int v) {
        return setarg(i, v, POS_B, SIZE_B);
    }

    public static int getArgC(int i) {
        return getarg(i, POS_C, SIZE_C);
    }

    public static int setArgC(int i, int v) {
        return setarg(i, v, POS_C, SIZE_C);
    }

    public static int getArgBx(int i) {
        return getarg(i, POS_BX, SIZE_BX);
    }

    public static int setArgBx(int i, int v) {
        return setarg(i, v, POS_BX, SIZE_BX);
    }

    public static int getArgSBx(int i) {
        return getArgBx(i) - OFFSET_SBX;
    }

    public static int setArgSBx(int i, int v) {
        return setArgBx(i, v + OFFSET_SBX);
    }

    public static int getArgAx(int i) {
        return getarg(i, POS_AX, SIZE_AX);
    }

    public static int setArgAx(int i, int v) {
        return setarg(i, v, POS_AX, SIZE_AX);
    }

    /** {@code CREATE_ABC} */
    public static int createABC(int o, int a, int b, int c) {
        return (o << POS_OP) | (a << POS_A) | (b << POS_B) | (c << POS_C);
    }

    /** {@code CREATE_ABx} */
    public static int createABx(int o, int a, int bx) {
        return (o << POS_OP) | (a << POS_A) | (bx << POS_BX);
    }

    /** {@code CREATE_AsBx} */
    public static int createAsBx(int o, int a, int sbx) {
        return createABx(o, a, sbx + OFFSET_SBX);
    }

    /** {@code CREATE_Ax} */
    public static int createAx(int o, int ax) {
        return (o << POS_OP) | (ax << POS_AX);
    }

    /** @deprecated 5.4 k-bit; 5.3 uses RK in B/C */
    public static int getArgK(int i) { return 0; }
    public static int setArgK(int i, int v) { return i; }
    public static int getArgSJ(int i) { return getArgSBx(i); }
    public static int setArgSJ(int i, int v) { return setArgSBx(i, v); }
    public static int getArgSC(int i) { return getArgC(i); }
    public static int getArgSB(int i) { return getArgB(i); }
    public static int int2sC(int i) { return i; }
    public static int createABCk(int o, int a, int b, int c, int k) {
        return createABC(o, a, b, c);
    }
    public static int createsJ(int o, int j, int k) {
        return createAsBx(o, 0, j);
    }
}

