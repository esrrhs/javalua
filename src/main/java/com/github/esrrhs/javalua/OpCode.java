package com.github.esrrhs.javalua;

/**
 * 指令集与指令编解码 —— 逐行翻译 {@code lopcodes.h} / {@code lopcodes.c}。
 * <p>
 * 指令是 32 位字，用 Java {@code int} 承载。两处必须小心的差别：
 * <ol>
 *   <li>C 版的 {@code Instruction} 是<b>无符号</b> 32 位，Java 的 {@code int}
 *       是有符号的。所有取字段一律用 {@code >>>}（无符号右移）再掩码，
 *       否则最高位那个 1 会带着符号扩展进来；</li>
 *   <li>带符号字段用「偏移 K」表示：写入值 = 真值 + K，K 是对应无符号字段
 *       最大值的一半（{@code sBx} 的 K 是 65535，{@code sC} 的 K 是 127）。</li>
 * </ol>
 * <p>
 * 指令格式（与上游一致）：
 * <pre>
 * iABC          C(8)     |      B(8)     |k|     A(8)      |   Op(7)
 * iABx                Bx(17)               |     A(8)      |   Op(7)
 * iAsBx              sBx (signed)(17)      |     A(8)      |   Op(7)
 * iAx                           Ax(25)                     |   Op(7)
 * isJ                           sJ (signed)(25)            |   Op(7)
 * </pre>
 */
public final class OpCode {

    private OpCode() {
    }

    /* ---------- 指令格式 ---------- */

    public static final int IABC = 0;
    public static final int IABX = 1;
    public static final int IASBX = 2;
    public static final int IAX = 3;
    public static final int ISJ = 4;

    public static final int SIZE_C = 8;
    public static final int SIZE_B = 8;
    public static final int SIZE_BX = SIZE_C + SIZE_B + 1; // 17
    public static final int SIZE_A = 8;
    public static final int SIZE_AX = SIZE_BX + SIZE_A; // 25
    public static final int SIZE_SJ = SIZE_BX + SIZE_A; // 25
    public static final int SIZE_OP = 7;

    public static final int POS_OP = 0;
    public static final int POS_A = POS_OP + SIZE_OP; // 7
    public static final int POS_K = POS_A + SIZE_A; // 15
    public static final int POS_B = POS_K + 1; // 16
    public static final int POS_C = POS_B + SIZE_B; // 24
    public static final int POS_BX = POS_K; // 15
    public static final int POS_AX = POS_A; // 7
    public static final int POS_SJ = POS_A; // 7

    public static final int MAXARG_BX = (1 << SIZE_BX) - 1;
    public static final int OFFSET_SBX = MAXARG_BX >> 1;
    public static final int MAXARG_AX = (1 << SIZE_AX) - 1;
    public static final int MAXARG_SJ = (1 << SIZE_SJ) - 1;
    public static final int OFFSET_SJ = MAXARG_SJ >> 1;
    public static final int MAXARG_A = (1 << SIZE_A) - 1;
    public static final int MAXARG_B = (1 << SIZE_B) - 1;
    public static final int MAXARG_C = (1 << SIZE_C) - 1;
    public static final int OFFSET_SC = MAXARG_C >> 1;

    /** {@code int2sC} */
    public static int int2sC(int i) {
        return i + OFFSET_SC;
    }

    /** {@code sC2int} */
    public static int sC2int(int i) {
        return i - OFFSET_SC;
    }

    /** {@code NO_REG}：一个能塞进 8 位的非法寄存器号 */
    public static final int NO_REG = MAXARG_A;

    /** {@code LFIELDS_PER_FLUSH}：构造表时攒多少个字段才发一次 SETLIST */
    public static final int LFIELDS_PER_FLUSH = 50;

    /* ---------- 操作码（ORDER OP，顺序与上游 enum 严格一致） ---------- */

    public static final int OP_MOVE = 0;        /* A B     R[A] := R[B] */
    public static final int OP_LOADI = 1;       /* A sBx   R[A] := sBx */
    public static final int OP_LOADF = 2;       /* A sBx   R[A] := (float)sBx */
    public static final int OP_LOADK = 3;       /* A Bx    R[A] := K[Bx] */
    public static final int OP_LOADKX = 4;      /* A       R[A] := K[extra arg] */
    public static final int OP_LOADFALSE = 5;   /* A       R[A] := false */
    public static final int OP_LFALSESKIP = 6;  /* A       R[A] := false; pc++ */
    public static final int OP_LOADTRUE = 7;    /* A       R[A] := true */
    public static final int OP_LOADNIL = 8;     /* A B     R[A], R[A+1], ..., R[A+B] := nil */
    public static final int OP_GETUPVAL = 9;    /* A B     R[A] := UpValue[B] */
    public static final int OP_SETUPVAL = 10;   /* A B     UpValue[B] := R[A] */
    public static final int OP_GETTABUP = 11;   /* A B C   R[A] := UpValue[B][K[C]:string] */
    public static final int OP_GETTABLE = 12;   /* A B C   R[A] := R[B][R[C]] */
    public static final int OP_GETI = 13;       /* A B C   R[A] := R[B][C] */
    public static final int OP_GETFIELD = 14;   /* A B C   R[A] := R[B][K[C]:string] */
    public static final int OP_SETTABUP = 15;   /* A B C   UpValue[A][K[B]:string] := RK(C) */
    public static final int OP_SETTABLE = 16;   /* A B C   R[A][R[B]] := RK(C) */
    public static final int OP_SETI = 17;       /* A B C   R[A][B] := RK(C) */
    public static final int OP_SETFIELD = 18;   /* A B C   R[A][K[B]:string] := RK(C) */
    public static final int OP_NEWTABLE = 19;   /* A B C k R[A] := {} */
    public static final int OP_SELF = 20;       /* A B C   R[A+1] := R[B]; R[A] := R[B][RK(C):string] */
    public static final int OP_ADDI = 21;       /* A B sC  R[A] := R[B] + sC */
    public static final int OP_ADDK = 22;       /* A B C   R[A] := R[B] + K[C]:number */
    public static final int OP_SUBK = 23;       /* A B C   R[A] := R[B] - K[C]:number */
    public static final int OP_MULK = 24;       /* A B C   R[A] := R[B] * K[C]:number */
    public static final int OP_MODK = 25;       /* A B C   R[A] := R[B] % K[C]:number */
    public static final int OP_POWK = 26;       /* A B C   R[A] := R[B] ^ K[C]:number */
    public static final int OP_DIVK = 27;       /* A B C   R[A] := R[B] / K[C]:number */
    public static final int OP_IDIVK = 28;      /* A B C   R[A] := R[B] // K[C]:number */
    public static final int OP_BANDK = 29;      /* A B C   R[A] := R[B] & K[C]:integer */
    public static final int OP_BORK = 30;       /* A B C   R[A] := R[B] | K[C]:integer */
    public static final int OP_BXORK = 31;      /* A B C   R[A] := R[B] ~ K[C]:integer */
    public static final int OP_SHRI = 32;       /* A B sC  R[A] := R[B] >> sC */
    public static final int OP_SHLI = 33;       /* A B sC  R[A] := sC << R[B] */
    public static final int OP_ADD = 34;        /* A B C   R[A] := R[B] + R[C] */
    public static final int OP_SUB = 35;        /* A B C   R[A] := R[B] - R[C] */
    public static final int OP_MUL = 36;        /* A B C   R[A] := R[B] * R[C] */
    public static final int OP_MOD = 37;        /* A B C   R[A] := R[B] % R[C] */
    public static final int OP_POW = 38;        /* A B C   R[A] := R[B] ^ R[C] */
    public static final int OP_DIV = 39;        /* A B C   R[A] := R[B] / R[C] */
    public static final int OP_IDIV = 40;       /* A B C   R[A] := R[B] // R[C] */
    public static final int OP_BAND = 41;       /* A B C   R[A] := R[B] & R[C] */
    public static final int OP_BOR = 42;        /* A B C   R[A] := R[B] | R[C] */
    public static final int OP_BXOR = 43;       /* A B C   R[A] := R[B] ~ R[C] */
    public static final int OP_SHL = 44;        /* A B C   R[A] := R[B] << R[C] */
    public static final int OP_SHR = 45;        /* A B C   R[A] := R[B] >> R[C] */
    public static final int OP_MMBIN = 46;      /* A B C   call C metamethod over R[A] and R[B] */
    public static final int OP_MMBINI = 47;     /* A sB C k call C metamethod over R[A] and sB */
    public static final int OP_MMBINK = 48;     /* A B C k call C metamethod over R[A] and K[B] */
    public static final int OP_UNM = 49;        /* A B     R[A] := -R[B] */
    public static final int OP_BNOT = 50;       /* A B     R[A] := ~R[B] */
    public static final int OP_NOT = 51;        /* A B     R[A] := not R[B] */
    public static final int OP_LEN = 52;        /* A B     R[A] := #R[B] */
    public static final int OP_CONCAT = 53;     /* A B     R[A] := R[A].. ... ..R[A+B-1] */
    public static final int OP_CLOSE = 54;      /* A       close all upvalues >= R[A] */
    public static final int OP_TBC = 55;        /* A       mark variable A "to be closed" */
    public static final int OP_JMP = 56;        /* sJ      pc += sJ */
    public static final int OP_EQ = 57;         /* A B k   if ((R[A] == R[B]) ~= k) then pc++ */
    public static final int OP_LT = 58;         /* A B k   if ((R[A] <  R[B]) ~= k) then pc++ */
    public static final int OP_LE = 59;         /* A B k   if ((R[A] <= R[B]) ~= k) then pc++ */
    public static final int OP_EQK = 60;        /* A B k   if ((R[A] == K[B]) ~= k) then pc++ */
    public static final int OP_EQI = 61;        /* A sB k  if ((R[A] == sB) ~= k) then pc++ */
    public static final int OP_LTI = 62;        /* A sB k  if ((R[A] <  sB) ~= k) then pc++ */
    public static final int OP_LEI = 63;        /* A sB k  if ((R[A] <= sB) ~= k) then pc++ */
    public static final int OP_GTI = 64;        /* A sB k  if ((R[A] >  sB) ~= k) then pc++ */
    public static final int OP_GEI = 65;        /* A sB k  if ((R[A] >= sB) ~= k) then pc++ */
    public static final int OP_TEST = 66;       /* A k     if (not R[A] == k) then pc++ */
    public static final int OP_TESTSET = 67;    /* A B k   if (not R[B] == k) then pc++ else R[A] := R[B] */
    public static final int OP_CALL = 68;       /* A B C   R[A], ... ,R[A+C-2] := R[A](R[A+1], ... ,R[A+B-1]) */
    public static final int OP_TAILCALL = 69;   /* A B C k return R[A](R[A+1], ... ,R[A+B-1]) */
    public static final int OP_RETURN = 70;     /* A B C k return R[A], ... ,R[A+B-2] */
    public static final int OP_RETURN0 = 71;    /*         return */
    public static final int OP_RETURN1 = 72;    /* A       return R[A] */
    public static final int OP_FORLOOP = 73;    /* A Bx    update counters; if loop continues then pc-=Bx */
    public static final int OP_FORPREP = 74;    /* A Bx    <check values and prepare counters> */
    public static final int OP_TFORPREP = 75;   /* A Bx    create upvalue for R[A+3]; pc+=Bx */
    public static final int OP_TFORCALL = 76;   /* A C     R[A+4], ... ,R[A+3+C] := R[A](R[A+1], R[A+2]) */
    public static final int OP_TFORLOOP = 77;   /* A Bx    if R[A+2] ~= nil then { R[A]=R[A+2]; pc -= Bx } */
    public static final int OP_SETLIST = 78;    /* A B C k R[A][C+i] := R[A+i], 1 <= i <= B */
    public static final int OP_CLOSURE = 79;    /* A Bx    R[A] := closure(KPROTO[Bx]) */
    public static final int OP_VARARG = 80;     /* A C     R[A], R[A+1], ..., R[A+C-2] = vararg */
    public static final int OP_VARARGPREP = 81; /* A       (adjust vararg parameters) */
    public static final int OP_EXTRAARG = 82;   /* Ax      extra arg for previous opcode */

    public static final int NUM_OPCODES = OP_EXTRAARG + 1;

    /* ---------- 操作码属性表（lopcodes.c: luaP_opmodes） ---------- */

    /**
     * {@code opmode(mm,ot,it,t,a,m)}：
     * <pre>
     * bit 0-2  指令格式
     * bit 3    指令写寄存器 A
     * bit 4    是测试指令（下一条必须是跳转）
     * bit 5    使用上一条指令设置的 top（B == 0 时）
     * bit 6    为下一条指令设置 top（C == 0 时）
     * bit 7    是元方法指令
     * </pre>
     */
    private static int opmode(int mm, int ot, int it, int t, int a, int m) {
        return ((mm) << 7) | ((ot) << 6) | ((it) << 5) | ((t) << 4) | ((a) << 3) | (m);
    }

    /** ORDER OP —— 与上面的操作码常量严格同序 */
    private static final int[] OPMODES = {
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_MOVE */
        opmode(0, 0, 0, 0, 1, IASBX), /* OP_LOADI */
        opmode(0, 0, 0, 0, 1, IASBX), /* OP_LOADF */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_LOADK */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_LOADKX */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_LOADFALSE */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_LFALSESKIP */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_LOADTRUE */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_LOADNIL */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_GETUPVAL */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_SETUPVAL */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_GETTABUP */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_GETTABLE */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_GETI */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_GETFIELD */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_SETTABUP */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_SETTABLE */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_SETI */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_SETFIELD */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_NEWTABLE */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SELF */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_ADDI */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_ADDK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SUBK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_MULK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_MODK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_POWK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_DIVK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_IDIVK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BANDK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BORK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BXORK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SHRI */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SHLI */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_ADD */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SUB */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_MUL */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_MOD */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_POW */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_DIV */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_IDIV */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BAND */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BOR */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BXOR */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SHL */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_SHR */
        opmode(1, 0, 0, 0, 0, IABC),  /* OP_MMBIN */
        opmode(1, 0, 0, 0, 0, IABC),  /* OP_MMBINI */
        opmode(1, 0, 0, 0, 0, IABC),  /* OP_MMBINK */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_UNM */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_BNOT */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_NOT */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_LEN */
        opmode(0, 0, 0, 0, 1, IABC),  /* OP_CONCAT */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_CLOSE */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_TBC */
        opmode(0, 0, 0, 0, 0, ISJ),   /* OP_JMP */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_EQ */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_LT */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_LE */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_EQK */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_EQI */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_LTI */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_LEI */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_GTI */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_GEI */
        opmode(0, 0, 0, 1, 0, IABC),  /* OP_TEST */
        opmode(0, 0, 0, 1, 1, IABC),  /* OP_TESTSET */
        opmode(0, 1, 1, 0, 1, IABC),  /* OP_CALL */
        opmode(0, 1, 1, 0, 1, IABC),  /* OP_TAILCALL */
        opmode(0, 0, 1, 0, 0, IABC),  /* OP_RETURN */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_RETURN0 */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_RETURN1 */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_FORLOOP */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_FORPREP */
        opmode(0, 0, 0, 0, 0, IABX),  /* OP_TFORPREP */
        opmode(0, 0, 0, 0, 0, IABC),  /* OP_TFORCALL */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_TFORLOOP */
        opmode(0, 0, 1, 0, 0, IABC),  /* OP_SETLIST */
        opmode(0, 0, 0, 0, 1, IABX),  /* OP_CLOSURE */
        opmode(0, 1, 0, 0, 1, IABC),  /* OP_VARARG */
        opmode(0, 0, 1, 0, 1, IABC),  /* OP_VARARGPREP */
        opmode(0, 0, 0, 0, 0, IAX),   /* OP_EXTRAARG */
    };

    /** ORDER OP —— 与操作码常量严格同序 */
    /**
     * 操作码名字（{@code lopnames.h: opnames}，ORDER OP）。
     * <p>
     * 上游把它放在单独的头文件里、只有 {@code luac} 用得上；这里留一份是因为
     * 差分比对要用它把指令打成可读文本，VM 的 trace 也要用。
     */
    public static final String[] NAMES = {
        "MOVE", "LOADI", "LOADF", "LOADK", "LOADKX", "LOADFALSE", "LFALSESKIP",
        "LOADTRUE", "LOADNIL", "GETUPVAL", "SETUPVAL", "GETTABUP", "GETTABLE",
        "GETI", "GETFIELD", "SETTABUP", "SETTABLE", "SETI", "SETFIELD", "NEWTABLE",
        "SELF", "ADDI", "ADDK", "SUBK", "MULK", "MODK", "POWK", "DIVK", "IDIVK",
        "BANDK", "BORK", "BXORK", "SHRI", "SHLI", "ADD", "SUB", "MUL", "MOD",
        "POW", "DIV", "IDIV", "BAND", "BOR", "BXOR", "SHL", "SHR", "MMBIN",
        "MMBINI", "MMBINK", "UNM", "BNOT", "NOT", "LEN", "CONCAT", "CLOSE",
        "TBC", "JMP", "EQ", "LT", "LE", "EQK", "EQI", "LTI", "LEI", "GTI",
        "GEI", "TEST", "TESTSET", "CALL", "TAILCALL", "RETURN", "RETURN0",
        "RETURN1", "FORLOOP", "FORPREP", "TFORPREP", "TFORCALL", "TFORLOOP",
        "SETLIST", "CLOSURE", "VARARG", "VARARGPREP", "EXTRAARG",
    };

    /** {@code getOpMode} */
    public static int getOpMode(int op) {
        return OPMODES[op] & 7;
    }

    public static boolean testAMode(int op) {
        return (OPMODES[op] & (1 << 3)) != 0;
    }

    public static boolean testTMode(int op) {
        return (OPMODES[op] & (1 << 4)) != 0;
    }

    public static boolean testITMode(int op) {
        return (OPMODES[op] & (1 << 5)) != 0;
    }

    public static boolean testOTMode(int op) {
        return (OPMODES[op] & (1 << 6)) != 0;
    }

    public static boolean testMMMode(int op) {
        return (OPMODES[op] & (1 << 7)) != 0;
    }

    public static String name(int op) {
        return NAMES[op];
    }

    /* ---------- 编解码 ---------- */

    /** 生成 {@code n} 个 1 位于 {@code p} 的掩码（{@code MASK1}） */
    private static int mask1(int n, int p) {
        return (~((~0) << n)) << p;
    }

    /** {@code getarg}。用 {@code >>>}：指令是有符号 int，但字段是无符号的 */
    private static int getarg(int i, int pos, int size) {
        return (i >>> pos) & mask1(size, 0);
    }

    /** {@code setarg} */
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

    /** {@code GETARG_sB} */
    public static int getArgSB(int i) {
        return sC2int(getArgB(i));
    }

    public static int setArgB(int i, int v) {
        return setarg(i, v, POS_B, SIZE_B);
    }

    public static int getArgC(int i) {
        return getarg(i, POS_C, SIZE_C);
    }

    /** {@code GETARG_sC} */
    public static int getArgSC(int i) {
        return sC2int(getArgC(i));
    }

    public static int setArgC(int i, int v) {
        return setarg(i, v, POS_C, SIZE_C);
    }

    /** {@code GETARG_k} */
    public static int getArgK(int i) {
        return getarg(i, POS_K, 1);
    }

    public static int setArgK(int i, int v) {
        return setarg(i, v, POS_K, 1);
    }

    public static int getArgBx(int i) {
        return getarg(i, POS_BX, SIZE_BX);
    }

    public static int setArgBx(int i, int v) {
        return setarg(i, v, POS_BX, SIZE_BX);
    }

    /** {@code GETARG_sBx} */
    public static int getArgSBx(int i) {
        return getArgBx(i) - OFFSET_SBX;
    }

    /** {@code SETARG_sBx} */
    public static int setArgSBx(int i, int v) {
        return setArgBx(i, v + OFFSET_SBX);
    }

    public static int getArgAx(int i) {
        return getarg(i, POS_AX, SIZE_AX);
    }

    public static int setArgAx(int i, int v) {
        return setarg(i, v, POS_AX, SIZE_AX);
    }

    /** {@code GETARG_sJ} */
    public static int getArgSJ(int i) {
        return getarg(i, POS_SJ, SIZE_SJ) - OFFSET_SJ;
    }

    /** {@code SETARG_sJ} */
    public static int setArgSJ(int i, int v) {
        return setarg(i, v + OFFSET_SJ, POS_SJ, SIZE_SJ);
    }

    /** {@code CREATE_ABCk} */
    public static int createABCk(int o, int a, int b, int c, int k) {
        return (o << POS_OP) | (a << POS_A) | (b << POS_B) | (c << POS_C) | (k << POS_K);
    }

    /** {@code CREATE_ABx} */
    public static int createABx(int o, int a, int bx) {
        return (o << POS_OP) | (a << POS_A) | (bx << POS_BX);
    }

    /** {@code CREATE_Ax} */
    public static int createAx(int o, int ax) {
        return (o << POS_OP) | (ax << POS_AX);
    }

    /** {@code CREATE_sJ} */
    public static int createsJ(int o, int j, int k) {
        return (o << POS_OP) | (j << POS_SJ) | (k << POS_K);
    }

    /* ---------- top 相关（ldo.c / ldebug.c 用） ---------- */

    /** {@code isOT}：本指令会为下一条设置 top */
    public static boolean isOT(int i) {
        int op = getOpCode(i);
        return (testOTMode(op) && getArgC(i) == 0) || op == OP_TAILCALL;
    }

    /** {@code isIT}：本指令使用上一条设置的 top */
    public static boolean isIT(int i) {
        return testITMode(getOpCode(i)) && getArgB(i) == 0;
    }
}
