package com.github.esrrhs.javalua;

/**
 * 代码生成 —— 对应 Lua 5.3.4 {@code lcode.h} / {@code lcode.c}。
 * 指令以打包 int 存储；字段读写经 {@link OpCode}。
 */
public final class Codegen {

    private Codegen() {}

    public static final int NO_JUMP = -1;
    public static final int MAXREGS = 255;
    public static final int MULTRET = -1;

    public static final int OPR_ADD = 0;
    public static final int OPR_SUB = 1;
    public static final int OPR_MUL = 2;
    public static final int OPR_MOD = 3;
    public static final int OPR_POW = 4;
    public static final int OPR_DIV = 5;
    public static final int OPR_IDIV = 6;
    public static final int OPR_BAND = 7;
    public static final int OPR_BOR = 8;
    public static final int OPR_BXOR = 9;
    public static final int OPR_SHL = 10;
    public static final int OPR_SHR = 11;
    public static final int OPR_CONCAT = 12;
    public static final int OPR_EQ = 13;
    public static final int OPR_LT = 14;
    public static final int OPR_LE = 15;
    public static final int OPR_NE = 16;
    public static final int OPR_GT = 17;
    public static final int OPR_GE = 18;
    public static final int OPR_AND = 19;
    public static final int OPR_OR = 20;
    public static final int OPR_NOBINOPR = 21;

    public static final int UNOPR_MINUS = 0;
    public static final int UNOPR_BNOT = 1;
    public static final int UNOPR_NOT = 2;
    public static final int UNOPR_LEN = 3;
    public static final int UNOPR_NOUNOPR = 4;

    private static boolean hasJumps(ExpDesc e) {
        return e.t != e.f;
    }

    private static int getInstr(FuncState fs, ExpDesc e) {
        return fs.f.code[e.info];
    }

    private static void setInstr(FuncState fs, ExpDesc e, int instr) {
        fs.f.code[e.info] = instr;
    }

    private static boolean toNumeral(ExpDesc e, LuaValue v) {
        if (hasJumps(e)) {
            return false;
        }
        switch (e.k) {
        case ExpDesc.VKINT:
            if (v != null) {
                v.setInteger(e.ival);
            }
            return true;
        case ExpDesc.VKFLT:
            if (v != null) {
                v.setFloat(e.nval);
            }
            return true;
        default:
            return false;
        }
    }

    public static void codeNil(FuncState fs, int from, int n) {
        int l = from + n - 1;
        if (fs.pc > fs.lasttarget) {
            int prevPc = fs.pc - 1;
            int previous = fs.f.code[prevPc];
            if (OpCode.getOpCode(previous) == OpCode.OP_LOADNIL) {
                int pfrom = OpCode.getArgA(previous);
                int pl = pfrom + OpCode.getArgB(previous);
                if ((pfrom <= from && from <= pl + 1) || (from <= pfrom && pfrom <= l + 1)) {
                    if (pfrom < from) {
                        from = pfrom;
                    }
                    if (pl > l) {
                        l = pl;
                    }
                    previous = OpCode.setArgA(previous, from);
                    previous = OpCode.setArgB(previous, l - from);
                    fs.f.code[prevPc] = previous;
                    return;
                }
            }
        }
        codeABC(fs, OpCode.OP_LOADNIL, from, n - 1, 0);
    }

    private static int getJump(FuncState fs, int pc) {
        int offset = OpCode.getArgSBx(fs.f.code[pc]);
        if (offset == NO_JUMP) {
            return NO_JUMP;
        }
        return (pc + 1) + offset;
    }

    private static void fixJump(FuncState fs, int pc, int dest) {
        int offset = dest - (pc + 1);
        if (absInt(offset) > OpCode.MAXARG_SBX) {
            throw fs.ls.syntaxError("control structure too long");
        }
        fs.f.code[pc] = OpCode.setArgSBx(fs.f.code[pc], offset);
    }

    private static int absInt(int x) {
        return x < 0 ? -x : x;
    }

    public static void concat(FuncState fs, int[] l1, int l2) {
        if (l2 == NO_JUMP) {
            return;
        } else if (l1[0] == NO_JUMP) {
            l1[0] = l2;
        } else {
            int list = l1[0];
            for (;;) {
                int next = getJump(fs, list);
                if (next == NO_JUMP) {
                    break;
                }
                list = next;
            }
            fixJump(fs, list, l2);
        }
    }

    public static void concatT(FuncState fs, ExpDesc e, int list) {
        int[] t = new int[] { e.t };
        concat(fs, t, list);
        e.t = t[0];
    }

    public static void concatF(FuncState fs, ExpDesc e, int list) {
        int[] f = new int[] { e.f };
        concat(fs, f, list);
        e.f = f[0];
    }

    public static int jump(FuncState fs) {
        int jpc = fs.jpc;
        fs.jpc = NO_JUMP;
        int j = codeAsBxPublic(fs, OpCode.OP_JMP, 0, NO_JUMP);
        int[] jh = new int[] { j };
        concat(fs, jh, jpc);
        return jh[0];
    }

    public static void ret(FuncState fs, int first, int nret) {
        codeABC(fs, OpCode.OP_RETURN, first, nret + 1, 0);
    }

    private static int condJump(FuncState fs, int op, int A, int B, int C) {
        codeABC(fs, op, A, B, C);
        return jump(fs);
    }

    public static int getLabel(FuncState fs) {
        fs.lasttarget = fs.pc;
        return fs.pc;
    }

    /** 返回控制该跳转的指令 pc（TESTSET 在 JMP 前一条） */
    private static int jumpControlPc(FuncState fs, int pc) {
        if (pc >= 1 && OpCode.testTMode(OpCode.getOpCode(fs.f.code[pc - 1]))) {
            return pc - 1;
        }
        return pc;
    }

    private static boolean patchTestReg(FuncState fs, int node, int reg) {
        int ipc = jumpControlPc(fs, node);
        int i = fs.f.code[ipc];
        if (OpCode.getOpCode(i) != OpCode.OP_TESTSET) {
            return false;
        }
        if (reg != OpCode.NO_REG && reg != OpCode.getArgB(i)) {
            fs.f.code[ipc] = OpCode.setArgA(i, reg);
        } else {
            fs.f.code[ipc] = OpCode.createABC(OpCode.OP_TEST, OpCode.getArgB(i), 0, OpCode.getArgC(i));
        }
        return true;
    }

    private static void removeValues(FuncState fs, int list) {
        while (list != NO_JUMP) {
            patchTestReg(fs, list, OpCode.NO_REG);
            list = getJump(fs, list);
        }
    }

    private static void patchListAux(FuncState fs, int list, int vtarget, int reg, int dtarget) {
        while (list != NO_JUMP) {
            int next = getJump(fs, list);
            if (patchTestReg(fs, list, reg)) {
                fixJump(fs, list, vtarget);
            } else {
                fixJump(fs, list, dtarget);
            }
            list = next;
        }
    }

    private static void dischargeJpc(FuncState fs) {
        patchListAux(fs, fs.jpc, fs.pc, OpCode.NO_REG, fs.pc);
        fs.jpc = NO_JUMP;
    }

    public static void patchToHere(FuncState fs, int list) {
        getLabel(fs);
        int[] jpc = new int[] { fs.jpc };
        concat(fs, jpc, list);
        fs.jpc = jpc[0];
    }

    public static void patchList(FuncState fs, int list, int target) {
        if (target == fs.pc) {
            patchToHere(fs, list);
        } else {
            patchListAux(fs, list, target, OpCode.NO_REG, target);
        }
    }

    public static void patchClose(FuncState fs, int list, int level) {
        level++;
        while (list != NO_JUMP) {
            int next = getJump(fs, list);
            fs.f.code[list] = OpCode.setArgA(fs.f.code[list], level);
            list = next;
        }
    }

    public static void jumpTo(FuncState fs, int t) {
        patchList(fs, jump(fs), t);
    }

    public static int code(FuncState fs, int i) {
        dischargeJpc(fs);
        Proto f = fs.f;
        f.setCode(fs.pc, i);
        f.setLineInfo(fs.pc, fs.ls.lastline);
        fs.pc++;
        return fs.pc - 1;
    }

    public static int codeABC(FuncState fs, int o, int a, int b, int c) {
        return code(fs, OpCode.createABC(o, a, b, c));
    }

    public static int codeABx(FuncState fs, int o, int a, int bc) {
        return code(fs, OpCode.createABx(o, a, bc));
    }

    public static int codeAsBxPublic(FuncState fs, int o, int a, int bc) {
        return codeABx(fs, o, a, bc + OpCode.OFFSET_SBX);
    }

    private static int codeExtraArg(FuncState fs, int a) {
        return code(fs, OpCode.createAx(OpCode.OP_EXTRAARG, a));
    }

    public static int codeK(FuncState fs, int reg, int k) {
        if (k <= OpCode.MAXARG_BX) {
            return codeABx(fs, OpCode.OP_LOADK, reg, k);
        }
        int p = codeABx(fs, OpCode.OP_LOADKX, reg, 0);
        codeExtraArg(fs, k);
        return p;
    }

    public static void checkStack(FuncState fs, int n) {
        int newstack = fs.freereg + n;
        if (newstack > fs.f.maxstacksize) {
            if (newstack >= MAXREGS) {
                throw fs.ls.syntaxError("function or expression needs too many registers");
            }
            fs.f.maxstacksize = newstack;
        }
    }

    public static void reserveRegs(FuncState fs, int n) {
        checkStack(fs, n);
        fs.freereg += n;
    }

    private static void freeReg(FuncState fs, int reg) {
        if (!OpCode.isK(reg) && reg >= fs.nactvar) {
            fs.freereg--;
        }
    }

    private static void freeExp(FuncState fs, ExpDesc e) {
        if (e.k == ExpDesc.VNONRELOC) {
            freeReg(fs, e.info);
        }
    }

    private static void freeExps(FuncState fs, ExpDesc e1, ExpDesc e2) {
        int r1 = e1.k == ExpDesc.VNONRELOC ? e1.info : -1;
        int r2 = e2.k == ExpDesc.VNONRELOC ? e2.info : -1;
        if (r1 > r2) {
            freeReg(fs, r1);
            freeReg(fs, r2);
        } else {
            freeReg(fs, r2);
            freeReg(fs, r1);
        }
    }

    public static int stringK(FuncState fs, LuaString s) {
        return addK(fs, LuaValue.ofString(s));
    }

    public static int intK(FuncState fs, long n) {
        return addK(fs, LuaValue.ofInteger(n));
    }

    static int numberK(FuncState fs, double r) {
        return addK(fs, LuaValue.ofFloat(r));
    }

    private static int boolK(FuncState fs, boolean b) {
        return addK(fs, LuaValue.ofBoolean(b));
    }

    private static int nilK(FuncState fs) {
        return addK(fs, LuaValue.ofNil());
    }

    private static int addK(FuncState fs, LuaValue v) {
        Integer idx = fs.ls.lookupConstant(fs, v);
        if (idx != null) {
            return idx;
        }
        if (fs.f.sizek >= OpCode.MAXARG_AX) {
            throw fs.ls.syntaxError("too many constants");
        }
        return fs.ls.addConstant(fs, v);
    }

    public static void setReturns(FuncState fs, ExpDesc e, int nresults) {
        if (e.k == ExpDesc.VCALL) {
            int i = getInstr(fs, e);
            setInstr(fs, e, OpCode.setArgC(i, nresults + 1));
        } else if (e.k == ExpDesc.VVARARG) {
            int i = getInstr(fs, e);
            i = OpCode.setArgB(i, nresults + 1);
            i = OpCode.setArgA(i, fs.freereg);
            setInstr(fs, e, i);
            reserveRegs(fs, 1);
        }
    }

    public static void setMultiRet(FuncState fs, ExpDesc e) {
        setReturns(fs, e, MULTRET);
    }

    public static void setOneRet(FuncState fs, ExpDesc e) {
        if (e.k == ExpDesc.VCALL) {
            e.k = ExpDesc.VNONRELOC;
            e.info = OpCode.getArgA(getInstr(fs, e));
        } else if (e.k == ExpDesc.VVARARG) {
            setInstr(fs, e, OpCode.setArgB(getInstr(fs, e), 2));
            e.k = ExpDesc.VRELOC;
        }
    }

    public static void dischargeVars(FuncState fs, ExpDesc e) {
        switch (e.k) {
        case ExpDesc.VLOCAL:
            e.k = ExpDesc.VNONRELOC;
            break;
        case ExpDesc.VUPVAL:
            e.info = codeABC(fs, OpCode.OP_GETUPVAL, 0, e.info, 0);
            e.k = ExpDesc.VRELOC;
            break;
        case ExpDesc.VINDEXED: {
            int op;
            freeReg(fs, e.indIdx);
            if (e.indVt == ExpDesc.VLOCAL) {
                freeReg(fs, e.indT);
                op = OpCode.OP_GETTABLE;
            } else {
                op = OpCode.OP_GETTABUP;
            }
            e.info = codeABC(fs, op, 0, e.indT, e.indIdx);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VVARARG:
        case ExpDesc.VCALL:
            setOneRet(fs, e);
            break;
        default:
            break;
        }
    }

    private static void discharge2Reg(FuncState fs, ExpDesc e, int reg) {
        dischargeVars(fs, e);
        switch (e.k) {
        case ExpDesc.VNIL:
            codeNil(fs, reg, 1);
            break;
        case ExpDesc.VFALSE:
        case ExpDesc.VTRUE:
            codeABC(fs, OpCode.OP_LOADBOOL, reg, e.k == ExpDesc.VTRUE ? 1 : 0, 0);
            break;
        case ExpDesc.VK:
            codeK(fs, reg, e.info);
            break;
        case ExpDesc.VKSTR:
            codeK(fs, reg, stringK(fs, e.strval));
            break;
        case ExpDesc.VKFLT:
            codeK(fs, reg, numberK(fs, e.nval));
            break;
        case ExpDesc.VKINT:
            codeK(fs, reg, intK(fs, e.ival));
            break;
        case ExpDesc.VRELOC: {
            int i = getInstr(fs, e);
            setInstr(fs, e, OpCode.setArgA(i, reg));
            break;
        }
        case ExpDesc.VNONRELOC:
            if (reg != e.info) {
                codeABC(fs, OpCode.OP_MOVE, reg, e.info, 0);
            }
            break;
        default:
            return;
        }
        e.info = reg;
        e.k = ExpDesc.VNONRELOC;
    }

    private static void discharge2AnyReg(FuncState fs, ExpDesc e) {
        if (e.k != ExpDesc.VNONRELOC) {
            reserveRegs(fs, 1);
            discharge2Reg(fs, e, fs.freereg - 1);
        }
    }

    private static int codeLoadBool(FuncState fs, int A, int b, int jump) {
        getLabel(fs);
        return codeABC(fs, OpCode.OP_LOADBOOL, A, b, jump);
    }

    private static boolean needValue(FuncState fs, int list) {
        for (; list != NO_JUMP; list = getJump(fs, list)) {
            int i = fs.f.code[jumpControlPc(fs, list)];
            if (OpCode.getOpCode(i) != OpCode.OP_TESTSET) {
                return true;
            }
        }
        return false;
    }

    private static void exp2Reg(FuncState fs, ExpDesc e, int reg) {
        discharge2Reg(fs, e, reg);
        if (e.k == ExpDesc.VJMP) {
            int[] t = new int[] { e.t };
            concat(fs, t, e.info);
            e.t = t[0];
        }
        if (hasJumps(e)) {
            int finalPc;
            int p_f = NO_JUMP;
            int p_t = NO_JUMP;
            if (needValue(fs, e.t) || needValue(fs, e.f)) {
                int fj = e.k == ExpDesc.VJMP ? NO_JUMP : jump(fs);
                p_f = codeLoadBool(fs, reg, 0, 1);
                p_t = codeLoadBool(fs, reg, 1, 0);
                patchToHere(fs, fj);
            }
            finalPc = getLabel(fs);
            patchListAux(fs, e.f, finalPc, reg, p_f);
            patchListAux(fs, e.t, finalPc, reg, p_t);
        }
        e.f = NO_JUMP;
        e.t = NO_JUMP;
        e.info = reg;
        e.k = ExpDesc.VNONRELOC;
    }

    public static void exp2NextReg(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        freeExp(fs, e);
        reserveRegs(fs, 1);
        exp2Reg(fs, e, fs.freereg - 1);
    }

    public static int exp2AnyReg(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        if (e.k == ExpDesc.VNONRELOC) {
            if (!hasJumps(e)) {
                return e.info;
            }
            if (e.info >= fs.nactvar) {
                exp2Reg(fs, e, e.info);
                return e.info;
            }
        }
        exp2NextReg(fs, e);
        return e.info;
    }

    public static void exp2AnyRegUp(FuncState fs, ExpDesc e) {
        if (e.k != ExpDesc.VUPVAL || hasJumps(e)) {
            exp2AnyReg(fs, e);
        }
    }

    public static void exp2Val(FuncState fs, ExpDesc e) {
        if (hasJumps(e)) {
            exp2AnyReg(fs, e);
        } else {
            dischargeVars(fs, e);
        }
    }

    public static int exp2RK(FuncState fs, ExpDesc e) {
        exp2Val(fs, e);
        switch (e.k) {
        case ExpDesc.VTRUE:
            e.info = boolK(fs, true);
            break;
        case ExpDesc.VFALSE:
            e.info = boolK(fs, false);
            break;
        case ExpDesc.VNIL:
            e.info = nilK(fs);
            break;
        case ExpDesc.VKINT:
            e.info = intK(fs, e.ival);
            break;
        case ExpDesc.VKFLT:
            e.info = numberK(fs, e.nval);
            break;
        case ExpDesc.VK:
            break;
        case ExpDesc.VKSTR:
            e.info = stringK(fs, e.strval);
            break;
        default:
            return exp2AnyReg(fs, e);
        }
        e.k = ExpDesc.VK;
        if (e.info <= OpCode.MAXINDEXRK) {
            return OpCode.rkAsk(e.info);
        }
        return exp2AnyReg(fs, e);
    }

    public static void storeVar(FuncState fs, ExpDesc var, ExpDesc ex) {
        switch (var.k) {
        case ExpDesc.VLOCAL:
            freeExp(fs, ex);
            exp2Reg(fs, ex, var.info);
            return;
        case ExpDesc.VUPVAL: {
            int e = exp2AnyReg(fs, ex);
            codeABC(fs, OpCode.OP_SETUPVAL, e, var.info, 0);
            break;
        }
        case ExpDesc.VINDEXED: {
            int op = var.indVt == ExpDesc.VLOCAL ? OpCode.OP_SETTABLE : OpCode.OP_SETTABUP;
            int e = exp2RK(fs, ex);
            codeABC(fs, op, var.indT, var.indIdx, e);
            break;
        }
        default:
            break;
        }
        freeExp(fs, ex);
    }

    public static void self(FuncState fs, ExpDesc e, ExpDesc key) {
        exp2AnyReg(fs, e);
        int ereg = e.info;
        freeExp(fs, e);
        e.info = fs.freereg;
        e.k = ExpDesc.VNONRELOC;
        reserveRegs(fs, 2);
        codeABC(fs, OpCode.OP_SELF, e.info, ereg, exp2RK(fs, key));
        freeExp(fs, key);
    }

    private static void negateCondition(FuncState fs, ExpDesc e) {
        int pc = jumpControlPc(fs, e.info);
        int i = fs.f.code[pc];
        int a = OpCode.getArgA(i) != 0 ? 0 : 1;
        fs.f.code[pc] = OpCode.setArgA(i, a);
    }

    private static int jumpOnCond(FuncState fs, ExpDesc e, int cond) {
        if (e.k == ExpDesc.VRELOC) {
            int ie = getInstr(fs, e);
            if (OpCode.getOpCode(ie) == OpCode.OP_NOT) {
                fs.pc--;
                return condJump(fs, OpCode.OP_TEST, OpCode.getArgB(ie), 0, cond == 0 ? 1 : 0);
            }
        }
        discharge2AnyReg(fs, e);
        freeExp(fs, e);
        return condJump(fs, OpCode.OP_TESTSET, OpCode.NO_REG, e.info, cond);
    }

    public static void goIfTrue(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        int pc;
        switch (e.k) {
        case ExpDesc.VJMP:
            negateCondition(fs, e);
            pc = e.info;
            break;
        case ExpDesc.VK:
        case ExpDesc.VKFLT:
        case ExpDesc.VKINT:
        case ExpDesc.VTRUE:
            pc = NO_JUMP;
            break;
        default:
            pc = jumpOnCond(fs, e, 0);
            break;
        }
        int[] f = new int[] { e.f };
        concat(fs, f, pc);
        e.f = f[0];
        patchToHere(fs, e.t);
        e.t = NO_JUMP;
    }

    public static void goIfFalse(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        int pc;
        switch (e.k) {
        case ExpDesc.VJMP:
            pc = e.info;
            break;
        case ExpDesc.VNIL:
        case ExpDesc.VFALSE:
            pc = NO_JUMP;
            break;
        default:
            pc = jumpOnCond(fs, e, 1);
            break;
        }
        int[] t = new int[] { e.t };
        concat(fs, t, pc);
        e.t = t[0];
        patchToHere(fs, e.f);
        e.f = NO_JUMP;
    }

    private static void codeNot(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        switch (e.k) {
        case ExpDesc.VNIL:
        case ExpDesc.VFALSE:
            e.k = ExpDesc.VTRUE;
            break;
        case ExpDesc.VK:
        case ExpDesc.VKFLT:
        case ExpDesc.VKINT:
        case ExpDesc.VTRUE:
            e.k = ExpDesc.VFALSE;
            break;
        case ExpDesc.VJMP:
            negateCondition(fs, e);
            break;
        case ExpDesc.VRELOC:
        case ExpDesc.VNONRELOC:
            discharge2AnyReg(fs, e);
            freeExp(fs, e);
            e.info = codeABC(fs, OpCode.OP_NOT, 0, e.info, 0);
            e.k = ExpDesc.VRELOC;
            break;
        default:
            break;
        }
        int tmp = e.t;
        e.t = e.f;
        e.f = tmp;
        removeValues(fs, e.f);
        removeValues(fs, e.t);
    }

    public static void indexed(FuncState fs, ExpDesc t, ExpDesc k) {
        t.indT = t.info;
        t.indIdx = exp2RK(fs, k);
        t.indVt = t.k == ExpDesc.VUPVAL ? ExpDesc.VUPVAL : ExpDesc.VLOCAL;
        t.k = ExpDesc.VINDEXED;
    }

    private static boolean validOp(int op, LuaValue v1, LuaValue v2) {
        switch (op) {
        case Arith.OP_BAND:
        case Arith.OP_BOR:
        case Arith.OP_BXOR:
        case Arith.OP_SHL:
        case Arith.OP_SHR:
        case Arith.OP_BNOT:
            return Arith.toIntegerNs(v1) != null && Arith.toIntegerNs(v2) != null;
        case Arith.OP_DIV:
        case Arith.OP_IDIV:
        case Arith.OP_MOD:
            return (v2.isInteger() ? (double) v2.i : v2.n) != 0;
        default:
            return true;
        }
    }

    private static boolean constFolding(FuncState fs, int op, ExpDesc e1, ExpDesc e2) {
        LuaValue v1 = new LuaValue();
        LuaValue v2 = new LuaValue();
        if (!toNumeral(e1, v1) || !toNumeral(e2, v2) || !validOp(op, v1, v2)) {
            return false;
        }
        LuaValue r = Arith.rawArith(null, op, v1, v2);
        if (r == null) {
            return false;
        }
        if (r.isInteger()) {
            e1.k = ExpDesc.VKINT;
            e1.ival = r.i;
        } else {
            double n = r.n;
            if (Double.isNaN(n) || n == 0) {
                return false;
            }
            e1.k = ExpDesc.VKFLT;
            e1.nval = n;
        }
        return true;
    }

    private static void codeUnexpVal(FuncState fs, int op, ExpDesc e, int line) {
        int r = exp2AnyReg(fs, e);
        freeExp(fs, e);
        e.info = codeABC(fs, op, 0, r, 0);
        e.k = ExpDesc.VRELOC;
        fixLine(fs, line);
    }

    private static void codeBinexpVal(FuncState fs, int op, ExpDesc e1, ExpDesc e2, int line) {
        int rk2 = exp2RK(fs, e2);
        int rk1 = exp2RK(fs, e1);
        freeExps(fs, e1, e2);
        e1.info = codeABC(fs, op, 0, rk1, rk2);
        e1.k = ExpDesc.VRELOC;
        fixLine(fs, line);
    }

    private static void codeComp(FuncState fs, int opr, ExpDesc e1, ExpDesc e2) {
        int rk1 = e1.k == ExpDesc.VK ? OpCode.rkAsk(e1.info) : e1.info;
        int rk2 = exp2RK(fs, e2);
        freeExps(fs, e1, e2);
        switch (opr) {
        case OPR_NE:
            e1.info = condJump(fs, OpCode.OP_EQ, 0, rk1, rk2);
            break;
        case OPR_GT:
        case OPR_GE: {
            int op = (opr - OPR_NE) + OpCode.OP_EQ;
            e1.info = condJump(fs, op, 1, rk2, rk1);
            break;
        }
        default: {
            int op = (opr - OPR_EQ) + OpCode.OP_EQ;
            e1.info = condJump(fs, op, 1, rk1, rk2);
            break;
        }
        }
        e1.k = ExpDesc.VJMP;
    }

    public static void prefix(FuncState fs, int op, ExpDesc e, int line) {
        ExpDesc ef = new ExpDesc();
        ef.k = ExpDesc.VKINT;
        ef.ival = 0;
        ef.t = NO_JUMP;
        ef.f = NO_JUMP;
        switch (op) {
        case UNOPR_MINUS:
        case UNOPR_BNOT:
            if (constFolding(fs, op + Arith.OP_UNM, e, ef)) {
                break;
            }
            // fallthrough
        case UNOPR_LEN:
            codeUnexpVal(fs, op + OpCode.OP_UNM, e, line);
            break;
        case UNOPR_NOT:
            codeNot(fs, e);
            break;
        default:
            break;
        }
    }

    public static void infix(FuncState fs, int op, ExpDesc v) {
        switch (op) {
        case OPR_AND:
            goIfTrue(fs, v);
            break;
        case OPR_OR:
            goIfFalse(fs, v);
            break;
        case OPR_CONCAT:
            exp2NextReg(fs, v);
            break;
        case OPR_ADD:
        case OPR_SUB:
        case OPR_MUL:
        case OPR_DIV:
        case OPR_IDIV:
        case OPR_MOD:
        case OPR_POW:
        case OPR_BAND:
        case OPR_BOR:
        case OPR_BXOR:
        case OPR_SHL:
        case OPR_SHR:
            if (!toNumeral(v, null)) {
                exp2RK(fs, v);
            }
            break;
        default:
            exp2RK(fs, v);
            break;
        }
    }

    public static void posfix(FuncState fs, int op, ExpDesc e1, ExpDesc e2, int line) {
        switch (op) {
        case OPR_AND:
            dischargeVars(fs, e2);
            {
                int[] f = new int[] { e2.f };
                concat(fs, f, e1.f);
                e2.f = f[0];
            }
            e1.copyFrom(e2);
            break;
        case OPR_OR:
            dischargeVars(fs, e2);
            {
                int[] t = new int[] { e2.t };
                concat(fs, t, e1.t);
                e2.t = t[0];
            }
            e1.copyFrom(e2);
            break;
        case OPR_CONCAT:
            exp2Val(fs, e2);
            if (e2.k == ExpDesc.VRELOC && OpCode.getOpCode(getInstr(fs, e2)) == OpCode.OP_CONCAT) {
                freeExp(fs, e1);
                int i = getInstr(fs, e2);
                setInstr(fs, e2, OpCode.setArgB(i, e1.info));
                e1.k = ExpDesc.VRELOC;
                e1.info = e2.info;
            } else {
                exp2NextReg(fs, e2);
                codeBinexpVal(fs, OpCode.OP_CONCAT, e1, e2, line);
            }
            break;
        case OPR_ADD:
        case OPR_SUB:
        case OPR_MUL:
        case OPR_DIV:
        case OPR_IDIV:
        case OPR_MOD:
        case OPR_POW:
        case OPR_BAND:
        case OPR_BOR:
        case OPR_BXOR:
        case OPR_SHL:
        case OPR_SHR:
            if (!constFolding(fs, op + Arith.OP_ADD, e1, e2)) {
                codeBinexpVal(fs, op + OpCode.OP_ADD, e1, e2, line);
            }
            break;
        case OPR_EQ:
        case OPR_LT:
        case OPR_LE:
        case OPR_NE:
        case OPR_GT:
        case OPR_GE:
            codeComp(fs, op, e1, e2);
            break;
        default:
            break;
        }
    }

    public static void fixLine(FuncState fs, int line) {
        fs.f.lineinfo[fs.pc - 1] = line;
    }

    public static void setList(FuncState fs, int base, int nelems, int tostore) {
        int c = (nelems - 1) / OpCode.LFIELDS_PER_FLUSH + 1;
        int b = tostore;
        if (tostore == MULTRET) {
            b = 0;
        }
        if (c <= OpCode.MAXARG_C) {
            codeABC(fs, OpCode.OP_SETLIST, base, b, c);
        } else if (c <= OpCode.MAXARG_AX) {
            codeABC(fs, OpCode.OP_SETLIST, base, b, 0);
            codeExtraArg(fs, c);
        } else {
            throw fs.ls.syntaxError("constructor too long");
        }
        fs.freereg = base + 1;
    }

    /** 兼容旧调用名 */
    public static boolean foldBinOp(int op) {
        return op <= OPR_SHR;
    }

    public static boolean exp2Const(FuncState fs, ExpDesc e, LuaValue out) {
        return false; // no <const> in 5.3.4
    }

    public static void semError(FuncState fs, String msg) {
        /* 5.3.4 semerror: clear token so message has no "near ..." suffix */
        fs.ls.t.token = 0;
        throw fs.ls.syntaxError(msg);
    }

    public static void codeInt(FuncState fs, int reg, long i) {
        codeK(fs, reg, intK(fs, i));
    }

    public static int codesJ(FuncState fs, int o, int sj, int k) {
        // 5.3: JMP is AsBx; ignore k
        return codeAsBxPublic(fs, OpCode.OP_JMP, 0, sj);
    }

    public static int codeABCk(FuncState fs, int o, int a, int b, int c, int k) {
        return codeABC(fs, o, a, b, c);
    }

    /** 5.3 NEWTABLE uses fb2int in B/C at emit time — patch sizes here. */
    public static void setTableSize(FuncState fs, int pc, int ra, int na, int nh) {
        int instr = fs.f.code[pc];
        instr = OpCode.setArgB(instr, int2fb(na));
        instr = OpCode.setArgC(instr, int2fb(nh));
        fs.f.code[pc] = instr;
    }

    private static int int2fb(int x) {
        // luaO_int2fb
        int e = 0;
        if (x < 8) return x;
        while (x >= (8 << 4)) {
            x = (x + 0xf) >> 4;
            e += 4;
        }
        while (x >= (8 << 1)) {
            x = (x + 1) >> 1;
            e++;
        }
        return ((e + 1) << 3) | (x - 8);
    }

    public static void finish(FuncState fs) {
        // 5.3: no special finish beyond return
    }


    /** 5.3: emit JMP with A=level (close upvals >= R(A-1)) and sBx=0. */
    public static int closeUpvals(FuncState fs, int level) {
        return codeAsBxPublic(fs, OpCode.OP_JMP, level, 0);
    }

}
