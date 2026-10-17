package com.github.esrrhs.fakescript.lua;

/**
 * 代码生成 —— 逐行翻译 {@code lcode.c}（外加 {@code lcode.h} 的 BinOpr / UnOpr）。
 * <p>
 * 这里几乎没有「设计」，只有搬运。真正值得记的是几条<b>为什么不能改写</b>的约束：
 * <ol>
 *   <li><b>{@code and} / {@code or} 不生成布尔值</b>，而是生成跳转链表
 *       （{@link ExpDesc#t} / {@link ExpDesc#f}）。只有最后确定「这里要一个值」
 *       时才用 {@code LFALSESKIP + LOADTRUE} 补上。所以
 *       {@code local x = a and b} 里 {@code b} 的值可能直接来自 {@code a} 的
 *       寄存器，一条 MOVE 都不发；</li>
 *   <li><b>常量折叠在编译期就做</b>，而且折叠用的算术必须与运行期是同一套
 *       （见 {@link Arith}）。否则同一个表达式写成常量与写成变量会得到不同结果；</li>
 *   <li><b>整数与浮点常量不合并</b>：{@code 100000} 与 {@code 100000.0} 数值相等，
 *       但必须是两个常量槽（实测 {@code luac -l} 如此）；</li>
 *   <li><b>跳转的目标只能回填</b>：Lua 的跳转是相对偏移，且只能往后填，
 *       所以所有「跳到哪儿」都要存成链表最后统一 {@code patchlist}。</li>
 * </ol>
 */
public final class Codegen {

    private Codegen() {
    }

    /** {@code NO_JUMP}：跳转链表的结束标记（既是非法地址，也是自环） */
    public static final int NO_JUMP = -1;

    /** {@code MAXREGS}：一个函数最多 255 个寄存器 */
    public static final int MAXREGS = 255;

    /** {@code LIMLINEDIFF}：相对行号差值的上限，超了就得存绝对行号 */
    private static final int LIMLINEDIFF = 0x80;

    /** {@code LUA_MULTRET} */
    public static final int MULTRET = -1;

    /* ---------- BinOpr / UnOpr（ORDER OPR，与上游严格一致） ---------- */

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

    /** {@code foldbinop}：只有算术与位运算可折叠 */
    public static boolean foldBinOp(int op) {
        return op <= OPR_SHR;
    }

    /* ---------- 指令发射 ---------- */

    /** {@code luaK_code} */
    public static int code(FuncState fs, int instr) {
        Proto f = fs.f;
        f.setCode(fs.pc, instr);
        fs.pc++;
        saveLineInfo(fs, f, fs.ls.lastline);
        return fs.pc - 1;
    }

    /** {@code luaK_codeABCk} */
    public static int codeABCk(FuncState fs, int o, int a, int b, int c, int k) {
        return code(fs, OpCode.createABCk(o, a, b, c, k));
    }

    /** {@code luaK_codeABC} */
    public static int codeABC(FuncState fs, int o, int a, int b, int c) {
        return codeABCk(fs, o, a, b, c, 0);
    }

    /** {@code luaK_codeABx} */
    public static int codeABx(FuncState fs, int o, int a, int bx) {
        return code(fs, OpCode.createABx(o, a, bx));
    }

    /** {@code codeAsBx} */
    private static int codeAsBx(FuncState fs, int o, int a, int bx) {
        return codeABx(fs, o, a, bx + OpCode.OFFSET_SBX);
    }

    /** {@code codesJ} */
    private static int codesJ(FuncState fs, int o, int sj, int k) {
        return code(fs, OpCode.createsJ(o, sj + OpCode.OFFSET_SJ, k));
    }

    /** {@code codeextraarg} */
    private static int codeExtraArg(FuncState fs, int a) {
        return code(fs, OpCode.createAx(OpCode.OP_EXTRAARG, a));
    }

    /** {@code luaK_codek}：常量下标太大时用 LOADKX + EXTRAARG */
    private static int codeK(FuncState fs, int reg, int k) {
        if (k <= OpCode.MAXARG_BX) {
            return codeABx(fs, OpCode.OP_LOADK, reg, k);
        }
        int p = codeABx(fs, OpCode.OP_LOADKX, reg, 0);
        codeExtraArg(fs, k);
        return p;
    }

    /* ---------- 行号 ---------- */

    /** {@code savelineinfo} */
    private static void saveLineInfo(FuncState fs, Proto f, int line) {
        int linedif = line - fs.previousline;
        int pc = fs.pc - 1; // 刚写进去的那条指令
        if (Math.abs(linedif) >= LIMLINEDIFF || fs.iwthabs++ >= Proto.MAXIWTHABS) {
            f.addAbsLineInfo(pc, line);
            linedif = Proto.ABSLINEINFO;
            fs.iwthabs = 1;
        }
        f.setLineInfo(pc, (byte) linedif);
        fs.previousline = line;
    }

    /** {@code removelastlineinfo} */
    private static void removeLastLineInfo(FuncState fs) {
        Proto f = fs.f;
        int pc = fs.pc - 1;
        if (f.lineinfo[pc] != Proto.ABSLINEINFO) {
            fs.previousline -= f.lineinfo[pc];
            fs.iwthabs--;
        } else {
            f.sizeabslineinfo--;
            fs.iwthabs = Proto.MAXIWTHABS + 1; // 强制下一条也用绝对行号
        }
    }

    /** {@code removelastinstruction} */
    private static void removeLastInstruction(FuncState fs) {
        removeLastLineInfo(fs);
        fs.pc--;
    }

    /** {@code luaK_fixline}：把刚发的那条指令的行号改写掉 */
    public static void fixLine(FuncState fs, int line) {
        removeLastLineInfo(fs);
        saveLineInfo(fs, fs.f, line);
    }

    /* ---------- 寄存器栈 ---------- */

    /** {@code luaK_checkstack} */
    public static void checkStack(FuncState fs, int n) {
        int newstack = fs.freereg + n;
        if (newstack > fs.f.maxstacksize) {
            if (newstack >= MAXREGS) {
                fs.ls.syntaxError("function or expression needs too many registers");
            }
            fs.f.maxstacksize = newstack;
        }
    }

    /** {@code luaK_reserveregs} */
    public static void reserveRegs(FuncState fs, int n) {
        checkStack(fs, n);
        fs.freereg += n;
    }

    /** {@code freereg}：局部变量占住的寄存器不能释放 */
    private static void freeReg(FuncState fs, int reg) {
        if (reg >= fs.nvarstack()) {
            fs.freereg--;
        }
    }

    /** {@code freeregs}：按从高到低的顺序释放 */
    private static void freeRegs(FuncState fs, int r1, int r2) {
        if (r1 > r2) {
            freeReg(fs, r1);
            freeReg(fs, r2);
        } else {
            freeReg(fs, r2);
            freeReg(fs, r1);
        }
    }

    private static void freeExp(FuncState fs, ExpDesc e) {
        if (e.k == ExpDesc.VNONRELOC) {
            freeReg(fs, e.info);
        }
    }

    private static void freeExps(FuncState fs, ExpDesc e1, ExpDesc e2) {
        int r1 = (e1.k == ExpDesc.VNONRELOC) ? e1.info : -1;
        int r2 = (e2.k == ExpDesc.VNONRELOC) ? e2.info : -1;
        freeRegs(fs, r1, r2);
    }

    /* ---------- 常量 ---------- */

    /** {@code stringK} */
    private static int stringK(FuncState fs, LuaString s) {
        return addK(fs, LuaValue.ofString(s));
    }

    /** {@code luaK_intK} */
    public static int intK(FuncState fs, long n) {
        return addK(fs, LuaValue.ofInteger(n));
    }

    /**
     * {@code luaK_numberK}。
     * <p>
     * 上游是 static，这里给包内可见是为了让「整数常量与浮点常量不合并」这条
     * 规则能被测试直接锁住——它靠肉眼很难发现。
     */
    static int numberK(FuncState fs, double r) {
        return addK(fs, LuaValue.ofFloat(r));
    }

    /** {@code boolK} */
    private static int boolK(FuncState fs, boolean b) {
        return addK(fs, LuaValue.ofBoolean(b));
    }

    /** {@code nilK} */
    private static int nilK(FuncState fs) {
        return addK(fs, LuaValue.ofNil());
    }

    /**
     * {@code addk}：命中缓存就复用下标。
     * <p>
     * 缓存是整份源码共享的（{@code ls->h}），命中后还要按「下标没越界 + 变体标签
     * 一致 + 值相等」三道校验——详见 {@link Lexer#lookupConstant}。
     */
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

    /* ---------- 立即数 ---------- */

    /** {@code fitsC}：能否塞进 sC 操作数（范围 -127..128） */
    public static boolean fitsC(long i) {
        return i >= -OpCode.OFFSET_SC && i <= OpCode.MAXARG_C - OpCode.OFFSET_SC;
    }

    /** {@code fitsBx}：能否塞进 sBx 操作数（范围 -65535..65536） */
    public static boolean fitsBx(long i) {
        return -OpCode.OFFSET_SBX <= i && i <= OpCode.MAXARG_BX - OpCode.OFFSET_SBX;
    }

    /** {@code luaK_int} */
    public static void codeInt(FuncState fs, int reg, long i) {
        if (fitsBx(i)) {
            codeAsBx(fs, OpCode.OP_LOADI, reg, (int) i);
        } else {
            codeK(fs, reg, intK(fs, i));
        }
    }

    /** {@code luaK_float} */
    private static void codeFloat(FuncState fs, int reg, double f) {
        Long fi = Arith.fltToInteger(f, Arith.F2I_EQ);
        if (fi != null && fitsBx(fi)) {
            codeAsBx(fs, OpCode.OP_LOADF, reg, fi.intValue());
        } else {
            codeK(fs, reg, numberK(fs, f));
        }
    }

    /** {@code luaK_nil}：相邻的 LOADNIL 会合并成一条 */
    public static void codeNil(FuncState fs, int from, int n) {
        int l = from + n - 1; // 最后一个要置 nil 的寄存器
        int previous = previousInstruction(fs);
        if (previous != -1 && OpCode.getOpCode(previous) == OpCode.OP_LOADNIL) {
            int pfrom = OpCode.getArgA(previous);
            int pl = pfrom + OpCode.getArgB(previous);
            if ((pfrom <= from && from <= pl + 1) || (from <= pfrom && pfrom <= l + 1)) {
                if (pfrom < from) {
                    from = pfrom;
                }
                if (pl > l) {
                    l = pl;
                }
                int patched = OpCode.setArgA(previous, from);
                patched = OpCode.setArgB(patched, l - from);
                fs.f.setCode(fs.pc - 1, patched);
                return;
            }
        }
        codeABC(fs, OpCode.OP_LOADNIL, from, n - 1, 0);
    }

    /**
     * {@code previousinstruction}：返回上一条指令；若两者之间可能存在跳转目标，
     * 返回 -1（全 1 的非法指令），以避免跨基本块做优化。
     */
    private static int previousInstruction(FuncState fs) {
        if (fs.pc > fs.lasttarget) {
            return fs.f.code[fs.pc - 1];
        }
        return -1;
    }

    /* ---------- 跳转 ---------- */

    /** {@code getjump} */
    private static int getJump(FuncState fs, int pc) {
        int offset = OpCode.getArgSJ(fs.f.code[pc]);
        if (offset == NO_JUMP) {
            return NO_JUMP; // 链表结束
        }
        return (pc + 1) + offset; // 相对偏移转绝对位置
    }

    /** {@code fixjump} */
    private static void fixJump(FuncState fs, int pc, int dest) {
        int offset = dest - (pc + 1);
        if (!(-OpCode.OFFSET_SJ <= offset && offset <= OpCode.MAXARG_SJ - OpCode.OFFSET_SJ)) {
            fs.ls.syntaxError("control structure too long");
        }
        fs.f.setCode(pc, OpCode.setArgSJ(fs.f.code[pc], offset));
    }

    /** {@code luaK_concat}：把链表 l2 接到 l1 尾部 */
    public static void concat(FuncState fs, int[] l1, int l2) {
        if (l2 == NO_JUMP) {
            return;
        }
        if (l1[0] == NO_JUMP) {
            l1[0] = l2;
        } else {
            int list = l1[0];
            int next;
            while ((next = getJump(fs, list)) != NO_JUMP) {
                list = next;
            }
            fixJump(fs, list, l2);
        }
    }

    /** 把 {@code list} 接到 {@code e} 的 t 链表上（C 里是 {@code &e->t}） */
    public static void concatT(FuncState fs, ExpDesc e, int list) {
        int[] box = new int[] { e.t };
        concat(fs, box, list);
        e.t = box[0];
    }

    /** 同上，接到 f 链表 */
    public static void concatF(FuncState fs, ExpDesc e, int list) {
        int[] box = new int[] { e.f };
        concat(fs, box, list);
        e.f = box[0];
    }

    /** {@code luaK_jump} */
    public static int jump(FuncState fs) {
        return codesJ(fs, OpCode.OP_JMP, NO_JUMP, 0);
    }

    /** {@code luaK_ret} */
    public static void ret(FuncState fs, int first, int nret) {
        int op;
        switch (nret) {
        case 0:
            op = OpCode.OP_RETURN0;
            break;
        case 1:
            op = OpCode.OP_RETURN1;
            break;
        default:
            op = OpCode.OP_RETURN;
            break;
        }
        codeABC(fs, op, first, nret + 1, 0);
    }

    /** {@code condjump} */
    private static int condJump(FuncState fs, int op, int a, int b, int c, int k) {
        codeABCk(fs, op, a, b, c, k);
        return jump(fs);
    }

    /** {@code luaK_getlabel} */
    public static int getLabel(FuncState fs) {
        fs.lasttarget = fs.pc;
        return fs.pc;
    }

    /** {@code getjumpcontrol}：找到真正控制这个跳转的那条（测试指令在前一条） */
    private static int getJumpControl(FuncState fs, int pc) {
        if (pc >= 1 && OpCode.testTMode(OpCode.getOpCode(fs.f.code[pc - 1]))) {
            return pc - 1;
        }
        return pc;
    }

    /**
     * {@code patchtestreg}：把 TESTSET 的目标寄存器改掉；
     * 若不需要值就把它降级成 TEST。
     *
     * @return 是否成功（那条指令确实是 TESTSET）
     */
    private static boolean patchTestReg(FuncState fs, int node, int reg) {
        int i = getJumpControl(fs, node);
        int inst = fs.f.code[i];
        if (OpCode.getOpCode(inst) != OpCode.OP_TESTSET) {
            return false;
        }
        if (reg != OpCode.NO_REG && reg != OpCode.getArgB(inst)) {
            fs.f.setCode(i, OpCode.setArgA(inst, reg));
        } else {
            // 不需要值，或寄存器里已经有值了 —— 降级成纯测试
            fs.f.setCode(i, OpCode.createABCk(OpCode.OP_TEST, OpCode.getArgB(inst), 0, 0,
                    OpCode.getArgK(inst)));
        }
        return true;
    }

    /** {@code removevalues} */
    private static void removeValues(FuncState fs, int list) {
        for (; list != NO_JUMP; list = getJump(fs, list)) {
            patchTestReg(fs, list, OpCode.NO_REG);
        }
    }

    /** {@code patchlistaux} */
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

    /** {@code luaK_patchlist} */
    public static void patchList(FuncState fs, int list, int target) {
        patchListAux(fs, list, target, OpCode.NO_REG, target);
    }

    /** {@code luaK_patchtohere} */
    public static void patchToHere(FuncState fs, int list) {
        int hr = getLabel(fs);
        patchList(fs, list, hr);
    }

    /** {@code luaK_jumpto} */
    public static void jumpTo(FuncState fs, int target) {
        patchList(fs, jump(fs), target);
    }

    /* ---------- 表达式落地 ---------- */

    /** {@code luaK_dischargevars}：把「变量」变成「值」（可能仍在寄存器里） */
    public static void dischargeVars(FuncState fs, ExpDesc e) {
        switch (e.k) {
        case ExpDesc.VCONST: {
            LuaValue v = constVal(fs, e);
            constToExp(v, e);
            break;
        }
        case ExpDesc.VLOCAL: {
            int temp = e.varRidx;
            e.info = temp;
            e.k = ExpDesc.VNONRELOC;
            break;
        }
        case ExpDesc.VUPVAL: {
            e.info = codeABC(fs, OpCode.OP_GETUPVAL, 0, e.info, 0);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VINDEXUP: {
            e.info = codeABC(fs, OpCode.OP_GETTABUP, 0, e.indT, e.indIdx);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VINDEXI: {
            freeReg(fs, e.indT);
            e.info = codeABC(fs, OpCode.OP_GETI, 0, e.indT, e.indIdx);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VINDEXSTR: {
            freeReg(fs, e.indT);
            e.info = codeABC(fs, OpCode.OP_GETFIELD, 0, e.indT, e.indIdx);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VINDEXED: {
            freeRegs(fs, e.indT, e.indIdx);
            e.info = codeABC(fs, OpCode.OP_GETTABLE, 0, e.indT, e.indIdx);
            e.k = ExpDesc.VRELOC;
            break;
        }
        case ExpDesc.VVARARG:
        case ExpDesc.VCALL: {
            setOneRet(fs, e);
            break;
        }
        default:
            break; // 已经有值了
        }
    }

    /** {@code const2val} */
    private static LuaValue constVal(FuncState fs, ExpDesc e) {
        return fs.dyd.actvar.get(e.info).k;
    }

    /** {@code const2exp} */
    private static void constToExp(LuaValue v, ExpDesc e) {
        if (v.isInteger()) {
            e.k = ExpDesc.VKINT;
            e.ival = v.i;
        } else if (v.isFloat()) {
            e.k = ExpDesc.VKFLT;
            e.nval = v.n;
        } else if (v.isBoolean()) {
            e.k = v.asBoolean() ? ExpDesc.VTRUE : ExpDesc.VFALSE;
        } else if (v.isNil()) {
            e.k = ExpDesc.VNIL;
        } else if (v.isString()) {
            e.k = ExpDesc.VKSTR;
            e.strval = v.asString();
        } else {
            throw new IllegalStateException("bad constant " + v);
        }
    }

    /** {@code luaK_exp2const} */
    public static boolean exp2Const(FuncState fs, ExpDesc e, LuaValue out) {
        if (e.hasJumps()) {
            return false;
        }
        switch (e.k) {
        case ExpDesc.VFALSE:
            out.setBoolean(false);
            return true;
        case ExpDesc.VTRUE:
            out.setBoolean(true);
            return true;
        case ExpDesc.VNIL:
            out.setNil();
            return true;
        case ExpDesc.VKSTR:
            out.setString(e.strval);
            return true;
        case ExpDesc.VCONST:
            out.set(constVal(fs, e));
            return true;
        default:
            if (e.k == ExpDesc.VKINT) {
                out.setInteger(e.ival);
                return true;
            }
            if (e.k == ExpDesc.VKFLT) {
                out.setFloat(e.nval);
                return true;
            }
            return false;
        }
    }

    /** {@code discharge2reg} */
    private static void discharge2Reg(FuncState fs, ExpDesc e, int reg) {
        dischargeVars(fs, e);
        switch (e.k) {
        case ExpDesc.VNIL:
            codeNil(fs, reg, 1);
            break;
        case ExpDesc.VFALSE:
            codeABC(fs, OpCode.OP_LOADFALSE, reg, 0, 0);
            break;
        case ExpDesc.VTRUE:
            codeABC(fs, OpCode.OP_LOADTRUE, reg, 0, 0);
            break;
        case ExpDesc.VKSTR:
            e.info = stringK(fs, e.strval);
            e.k = ExpDesc.VK;
            // FALLTHROUGH
        case ExpDesc.VK:
            codeK(fs, reg, e.info);
            break;
        case ExpDesc.VKFLT:
            codeFloat(fs, reg, e.nval);
            break;
        case ExpDesc.VKINT:
            codeInt(fs, reg, e.ival);
            break;
        case ExpDesc.VRELOC: {
            int pc = e.info;
            fs.f.setCode(pc, OpCode.setArgA(fs.f.code[pc], reg));
            break;
        }
        case ExpDesc.VNONRELOC:
            if (reg != e.info) {
                codeABC(fs, OpCode.OP_MOVE, reg, e.info, 0);
            }
            break;
        default:
            return; // VJMP：值由跳转链表决定，这里什么都不做
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

    /** {@code code_loadbool} */
    private static int codeLoadBool(FuncState fs, int a, int op) {
        getLabel(fs); // 这两条指令可能是跳转目标
        return codeABC(fs, op, a, 0, 0);
    }

    /** {@code need_value} */
    private static boolean needValue(FuncState fs, int list) {
        for (; list != NO_JUMP; list = getJump(fs, list)) {
            int i = fs.f.code[getJumpControl(fs, list)];
            if (OpCode.getOpCode(i) != OpCode.OP_TESTSET) {
                return true;
            }
        }
        return false;
    }

    /** {@code exp2reg} */
    private static void exp2Reg(FuncState fs, ExpDesc e, int reg) {
        discharge2Reg(fs, e, reg);
        if (e.k == ExpDesc.VJMP) {
            concatT(fs, e, e.info);
        }
        if (e.hasJumps()) {
            int pF = NO_JUMP; // 可能的 LOADFALSE
            int pT = NO_JUMP; // 可能的 LOADTRUE
            if (needValue(fs, e.t) || needValue(fs, e.f)) {
                int fj = (e.k == ExpDesc.VJMP) ? NO_JUMP : jump(fs);
                pF = codeLoadBool(fs, reg, OpCode.OP_LFALSESKIP);
                pT = codeLoadBool(fs, reg, OpCode.OP_LOADTRUE);
                patchToHere(fs, fj);
            }
            int fin = getLabel(fs);
            patchListAux(fs, e.f, fin, reg, pF);
            patchListAux(fs, e.t, fin, reg, pT);
        }
        e.f = e.t = NO_JUMP;
        e.info = reg;
        e.k = ExpDesc.VNONRELOC;
    }

    /** {@code luaK_exp2nextreg} */
    public static void exp2NextReg(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        freeExp(fs, e);
        reserveRegs(fs, 1);
        exp2Reg(fs, e, fs.freereg - 1);
    }

    /** {@code luaK_exp2anyreg} */
    public static int exp2AnyReg(FuncState fs, ExpDesc e) {
        dischargeVars(fs, e);
        if (e.k == ExpDesc.VNONRELOC) {
            if (!e.hasJumps()) {
                return e.info;
            }
            if (e.info >= fs.nvarstack()) {
                exp2Reg(fs, e, e.info);
                return e.info;
            }
            // 否则它是局部变量：有跳转时不能拿它当结果的落点，走默认分支
        }
        exp2NextReg(fs, e);
        return e.info;
    }

    /** {@code luaK_exp2anyregup} */
    public static void exp2AnyRegUp(FuncState fs, ExpDesc e) {
        if (e.k != ExpDesc.VUPVAL || e.hasJumps()) {
            exp2AnyReg(fs, e);
        }
    }

    /** {@code luaK_exp2val} */
    public static void exp2Val(FuncState fs, ExpDesc e) {
        if (e.hasJumps()) {
            exp2AnyReg(fs, e);
        } else {
            dischargeVars(fs, e);
        }
    }

    /** {@code luaK_exp2K} */
    private static boolean exp2K(FuncState fs, ExpDesc e) {
        if (!e.hasJumps()) {
            int info;
            switch (e.k) {
            case ExpDesc.VTRUE:
                info = boolK(fs, true);
                break;
            case ExpDesc.VFALSE:
                info = boolK(fs, false);
                break;
            case ExpDesc.VNIL:
                info = nilK(fs);
                break;
            case ExpDesc.VKINT:
                info = intK(fs, e.ival);
                break;
            case ExpDesc.VKFLT:
                info = numberK(fs, e.nval);
                break;
            case ExpDesc.VKSTR:
                info = stringK(fs, e.strval);
                break;
            case ExpDesc.VK:
                info = e.info;
                break;
            default:
                return false; // 不是常量
            }
            if (info <= OpCode.MAXARG_B) { // 能塞进 RK 下标？
                e.k = ExpDesc.VK;
                e.info = info;
                return true;
            }
        }
        return false;
    }

    /** {@code exp2RK} */
    private static boolean exp2RK(FuncState fs, ExpDesc e) {
        if (exp2K(fs, e)) {
            return true;
        }
        exp2AnyReg(fs, e);
        return false;
    }

    /** {@code codeABRK} */
    private static void codeABRK(FuncState fs, int o, int a, int b, ExpDesc ec) {
        int k = exp2RK(fs, ec) ? 1 : 0;
        codeABCk(fs, o, a, b, ec.info, k);
    }

    /* ---------- 多返回值 ---------- */

    /** {@code luaK_setreturns} */
    public static void setReturns(FuncState fs, ExpDesc e, int nresults) {
        int pc = e.info;
        if (e.k == ExpDesc.VCALL) {
            fs.f.setCode(pc, OpCode.setArgC(fs.f.code[pc], nresults + 1));
        } else {
            int inst = fs.f.code[pc];
            inst = OpCode.setArgC(inst, nresults + 1);
            inst = OpCode.setArgA(inst, fs.freereg);
            fs.f.setCode(pc, inst);
            reserveRegs(fs, 1);
        }
    }

    /** {@code luaK_setmultret} */
    public static void setMultiRet(FuncState fs, ExpDesc e) {
        setReturns(fs, e, MULTRET);
    }

    /** {@code luaK_setoneret} */
    public static void setOneRet(FuncState fs, ExpDesc e) {
        if (e.k == ExpDesc.VCALL) {
            e.k = ExpDesc.VNONRELOC;
            e.info = OpCode.getArgA(fs.f.code[e.info]);
        } else if (e.k == ExpDesc.VVARARG) {
            fs.f.setCode(e.info, OpCode.setArgC(fs.f.code[e.info], 2));
            e.k = ExpDesc.VRELOC;
        }
    }

    /* ---------- 变量读写 ---------- */

    /** {@code luaK_storevar} */
    public static void storeVar(FuncState fs, ExpDesc var, ExpDesc ex) {
        switch (var.k) {
        case ExpDesc.VLOCAL:
            freeExp(fs, ex);
            exp2Reg(fs, ex, var.varRidx);
            return;
        case ExpDesc.VUPVAL: {
            int e = exp2AnyReg(fs, ex);
            codeABC(fs, OpCode.OP_SETUPVAL, e, var.info, 0);
            break;
        }
        case ExpDesc.VINDEXUP:
            codeABRK(fs, OpCode.OP_SETTABUP, var.indT, var.indIdx, ex);
            break;
        case ExpDesc.VINDEXI:
            codeABRK(fs, OpCode.OP_SETI, var.indT, var.indIdx, ex);
            break;
        case ExpDesc.VINDEXSTR:
            codeABRK(fs, OpCode.OP_SETFIELD, var.indT, var.indIdx, ex);
            break;
        case ExpDesc.VINDEXED:
            codeABRK(fs, OpCode.OP_SETTABLE, var.indT, var.indIdx, ex);
            break;
        default:
            throw new IllegalStateException("invalid var kind to store: " + var);
        }
        freeExp(fs, ex);
    }

    /** {@code luaK_self} */
    public static void self(FuncState fs, ExpDesc e, ExpDesc key) {
        exp2AnyReg(fs, e);
        int ereg = e.info;
        freeExp(fs, e);
        e.info = fs.freereg;
        e.k = ExpDesc.VNONRELOC;
        reserveRegs(fs, 2); // 方法本身与 self
        codeABRK(fs, OpCode.OP_SELF, e.info, ereg, key);
        freeExp(fs, key);
    }

    /** {@code luaK_indexed} */
    public static void indexed(FuncState fs, ExpDesc t, ExpDesc k) {
        if (k.k == ExpDesc.VKSTR) {
            k.info = stringK(fs, k.strval);
            k.k = ExpDesc.VK;
        }
        if (t.k == ExpDesc.VUPVAL && !isKstr(fs, k)) {
            exp2AnyReg(fs, t); // upvalue 只能被字面短串索引
        }
        if (t.k == ExpDesc.VUPVAL) {
            int temp = t.info;
            t.indT = temp;
            t.indIdx = k.info;
            t.k = ExpDesc.VINDEXUP;
        } else {
            t.indT = (t.k == ExpDesc.VLOCAL) ? t.varRidx : t.info;
            if (isKstr(fs, k)) {
                t.indIdx = k.info;
                t.k = ExpDesc.VINDEXSTR;
            } else if (isCint(k)) {
                t.indIdx = (int) k.ival;
                t.k = ExpDesc.VINDEXI;
            } else {
                t.indIdx = exp2AnyReg(fs, k);
                t.k = ExpDesc.VINDEXED;
            }
        }
    }

    /** {@code isKstr}：常量短串（GETFIELD/SETFIELD 要求短串） */
    private static boolean isKstr(FuncState fs, ExpDesc e) {
        return e.k == ExpDesc.VK && !e.hasJumps() && e.info <= OpCode.MAXARG_B
                && fs.f.k[e.info].asString().isShort();
    }

    /** {@code isKint} */
    private static boolean isKint(ExpDesc e) {
        return e.k == ExpDesc.VKINT && !e.hasJumps();
    }

    /** {@code isCint}：能塞进 C 字段的整数字面量 */
    private static boolean isCint(ExpDesc e) {
        return isKint(e) && e.ival >= 0 && e.ival <= OpCode.MAXARG_C;
    }

    /** {@code isSCint}：能塞进 sC 字段的整数字面量 */
    private static boolean isSCint(ExpDesc e) {
        return isKint(e) && fitsC(e.ival);
    }

    /** {@code isSCnumber} */
    private static boolean isSCnumber(ExpDesc e, int[] im, boolean[] isfloat) {
        long i;
        if (e.k == ExpDesc.VKINT) {
            i = e.ival;
        } else if (e.k == ExpDesc.VKFLT) {
            Long fi = Arith.fltToInteger(e.nval, Arith.F2I_EQ);
            if (fi == null) {
                return false;
            }
            i = fi;
            isfloat[0] = true;
        } else {
            return false;
        }
        if (!e.hasJumps() && fitsC(i)) {
            im[0] = OpCode.int2sC((int) i);
            return true;
        }
        return false;
    }

    /* ---------- 条件 ---------- */

    /** {@code negatecondition} */
    private static void negateCondition(FuncState fs, ExpDesc e) {
        int pc = getJumpControl(fs, e.info);
        int inst = fs.f.code[pc];
        fs.f.setCode(pc, OpCode.setArgK(inst, OpCode.getArgK(inst) ^ 1));
    }

    /** {@code jumponcond} */
    private static int jumpOnCond(FuncState fs, ExpDesc e, int cond) {
        if (e.k == ExpDesc.VRELOC) {
            int ie = fs.f.code[e.info];
            if (OpCode.getOpCode(ie) == OpCode.OP_NOT) {
                removeLastInstruction(fs); // 消掉这条 NOT
                return condJump(fs, OpCode.OP_TEST, OpCode.getArgB(ie), 0, 0, cond == 0 ? 1 : 0);
            }
        }
        discharge2AnyReg(fs, e);
        freeExp(fs, e);
        return condJump(fs, OpCode.OP_TESTSET, OpCode.NO_REG, e.info, 0, cond);
    }

    /** {@code luaK_goiftrue} */
    public static void goIfTrue(FuncState fs, ExpDesc e) {
        int pc;
        dischargeVars(fs, e);
        switch (e.k) {
        case ExpDesc.VJMP:
            negateCondition(fs, e);
            pc = e.info;
            break;
        case ExpDesc.VK:
        case ExpDesc.VKFLT:
        case ExpDesc.VKINT:
        case ExpDesc.VKSTR:
        case ExpDesc.VTRUE:
            pc = NO_JUMP; // 恒为真
            break;
        default:
            pc = jumpOnCond(fs, e, 0); // 为假则跳
            break;
        }
        concatF(fs, e, pc);
        patchToHere(fs, e.t);
        e.t = NO_JUMP;
    }

    /** {@code luaK_goiffalse} */
    public static void goIfFalse(FuncState fs, ExpDesc e) {
        int pc;
        dischargeVars(fs, e);
        switch (e.k) {
        case ExpDesc.VJMP:
            pc = e.info;
            break;
        case ExpDesc.VNIL:
        case ExpDesc.VFALSE:
            pc = NO_JUMP; // 恒为假
            break;
        default:
            pc = jumpOnCond(fs, e, 1); // 为真则跳
            break;
        }
        concatT(fs, e, pc);
        patchToHere(fs, e.f);
        e.f = NO_JUMP;
    }

    /** {@code codenot} */
    private static void codeNot(FuncState fs, ExpDesc e) {
        switch (e.k) {
        case ExpDesc.VNIL:
        case ExpDesc.VFALSE:
            e.k = ExpDesc.VTRUE;
            break;
        case ExpDesc.VK:
        case ExpDesc.VKFLT:
        case ExpDesc.VKINT:
        case ExpDesc.VKSTR:
        case ExpDesc.VTRUE:
            e.k = ExpDesc.VFALSE;
            break;
        case ExpDesc.VJMP:
            negateCondition(fs, e);
            break;
        case ExpDesc.VRELOC:
        case ExpDesc.VNONRELOC: {
            discharge2AnyReg(fs, e);
            freeExp(fs, e);
            e.info = codeABC(fs, OpCode.OP_NOT, 0, e.info, 0);
            e.k = ExpDesc.VRELOC;
            break;
        }
        default:
            throw new IllegalStateException("cannot happen: " + e);
        }
        int temp = e.f;
        e.f = e.t;
        e.t = temp;
        removeValues(fs, e.f); // 取反之后值就没用了
        removeValues(fs, e.t);
    }

    /* ---------- 运算符 ---------- */

    /** {@code binopr2op}（ORDER OPR - ORDER OP） */
    private static int binOpr2Op(int opr, int baser, int base) {
        return (opr - baser) + base;
    }

    /** {@code unopr2op} */
    private static int unOpr2Op(int opr) {
        return (opr - UNOPR_MINUS) + OpCode.OP_UNM;
    }

    /** {@code binopr2TM}（ORDER OPR - ORDER TM） */
    private static int binOpr2TM(int opr) {
        return (opr - OPR_ADD) + Tm.TM_ADD;
    }

    /** {@code codeunexpval} */
    private static void codeUnExpVal(FuncState fs, int op, ExpDesc e, int line) {
        int r = exp2AnyReg(fs, e);
        freeExp(fs, e);
        e.info = codeABC(fs, op, 0, r, 0);
        e.k = ExpDesc.VRELOC;
        fixLine(fs, line);
    }

    /** {@code finishbinexpval} */
    private static void finishBinExpVal(FuncState fs, ExpDesc e1, ExpDesc e2, int op, int v2,
            int flip, int line, int mmop, int event) {
        int v1 = exp2AnyReg(fs, e1);
        int pc = codeABCk(fs, op, 0, v1, v2, 0);
        freeExps(fs, e1, e2);
        e1.info = pc;
        e1.k = ExpDesc.VRELOC;
        fixLine(fs, line);
        codeABCk(fs, mmop, v1, v2, event, flip); // 元方法兜底
        fixLine(fs, line);
    }

    /** {@code codebinexpval} */
    private static void codeBinExpVal(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int line) {
        int op = binOpr2Op(opr, OPR_ADD, OpCode.OP_ADD);
        int v2 = exp2AnyReg(fs, e2);
        finishBinExpVal(fs, e1, e2, op, v2, 0, line, OpCode.OP_MMBIN, binOpr2TM(opr));
    }

    /** {@code codebini} */
    private static void codeBinI(FuncState fs, int op, ExpDesc e1, ExpDesc e2, int flip, int line,
            int event) {
        int v2 = OpCode.int2sC((int) e2.ival);
        finishBinExpVal(fs, e1, e2, op, v2, flip, line, OpCode.OP_MMBINI, event);
    }

    /** {@code codebinK} */
    private static void codeBinK(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int flip,
            int line) {
        int event = binOpr2TM(opr);
        int v2 = e2.info;
        int op = binOpr2Op(opr, OPR_ADD, OpCode.OP_ADDK);
        finishBinExpVal(fs, e1, e2, op, v2, flip, line, OpCode.OP_MMBINK, event);
    }

    /** {@code finishbinexpneg}：{@code a - 5} 编成 {@code a + (-5)} */
    private static boolean finishBinExpNeg(FuncState fs, ExpDesc e1, ExpDesc e2, int op, int line,
            int event) {
        if (!isKint(e2)) {
            return false;
        }
        long i2 = e2.ival;
        if (!(fitsC(i2) && fitsC(-i2))) {
            return false;
        }
        int v2 = (int) i2;
        finishBinExpVal(fs, e1, e2, op, OpCode.int2sC(-v2), 0, line, OpCode.OP_MMBINI, event);
        // 元方法必须拿到原本的第二个操作数
        int last = fs.pc - 1;
        fs.f.setCode(last, OpCode.setArgB(fs.f.code[last], OpCode.int2sC(v2)));
        return true;
    }

    private static void swapExps(ExpDesc e1, ExpDesc e2) {
        ExpDesc temp = new ExpDesc(e1);
        e1.set(e2);
        e2.set(temp);
    }

    /** {@code codebinNoK} */
    private static void codeBinNoK(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int flip,
            int line) {
        if (flip != 0) {
            swapExps(e1, e2);
        }
        codeBinExpVal(fs, opr, e1, e2, line);
    }

    /** {@code codearith} */
    private static void codeArith(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int flip,
            int line) {
        if (isNumeral(e2) && exp2K(fs, e2)) {
            codeBinK(fs, opr, e1, e2, flip, line);
        } else {
            codeBinNoK(fs, opr, e1, e2, flip, line);
        }
    }

    /** {@code codecommutative} */
    private static void codeCommutative(FuncState fs, int op, ExpDesc e1, ExpDesc e2, int line) {
        int flip = 0;
        if (isNumeral(e1)) {
            swapExps(e1, e2);
            flip = 1;
        }
        if (op == OPR_ADD && isSCint(e2)) {
            codeBinI(fs, OpCode.OP_ADDI, e1, e2, flip, line, Tm.TM_ADD);
        } else {
            codeArith(fs, op, e1, e2, flip, line);
        }
    }

    /** {@code codebitwise} */
    private static void codeBitwise(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int line) {
        int flip = 0;
        if (e1.k == ExpDesc.VKINT) {
            swapExps(e1, e2);
            flip = 1;
        }
        if (e2.k == ExpDesc.VKINT && exp2K(fs, e2)) {
            codeBinK(fs, opr, e1, e2, flip, line);
        } else {
            codeBinNoK(fs, opr, e1, e2, flip, line);
        }
    }

    /** {@code codeorder} */
    private static void codeOrder(FuncState fs, int opr, ExpDesc e1, ExpDesc e2) {
        int r1;
        int r2;
        int[] im = new int[1];
        boolean[] isfloat = new boolean[1];
        int op;
        if (isSCnumber(e2, im, isfloat)) {
            r1 = exp2AnyReg(fs, e1);
            r2 = im[0];
            op = binOpr2Op(opr, OPR_LT, OpCode.OP_LTI);
        } else if (isSCnumber(e1, im, isfloat)) {
            // (A < B) 变成 (B > A)，(A <= B) 变成 (B >= A)
            r1 = exp2AnyReg(fs, e2);
            r2 = im[0];
            op = binOpr2Op(opr, OPR_LT, OpCode.OP_GTI);
        } else {
            r1 = exp2AnyReg(fs, e1);
            r2 = exp2AnyReg(fs, e2);
            op = binOpr2Op(opr, OPR_LT, OpCode.OP_LT);
        }
        freeExps(fs, e1, e2);
        e1.info = condJump(fs, op, r1, r2, isfloat[0] ? 1 : 0, 1);
        e1.k = ExpDesc.VJMP;
    }

    /** {@code codeeq} */
    private static void codeEq(FuncState fs, int opr, ExpDesc e1, ExpDesc e2) {
        int r1;
        int r2;
        int[] im = new int[1];
        boolean[] isfloat = new boolean[1];
        int op;
        if (e1.k != ExpDesc.VNONRELOC) {
            swapExps(e1, e2);
        }
        r1 = exp2AnyReg(fs, e1); // 第一个操作数必须在寄存器里
        if (isSCnumber(e2, im, isfloat)) {
            op = OpCode.OP_EQI;
            r2 = im[0];
        } else if (exp2RK(fs, e2)) {
            op = OpCode.OP_EQK;
            r2 = e2.info;
        } else {
            op = OpCode.OP_EQ;
            r2 = exp2AnyReg(fs, e2);
        }
        freeExps(fs, e1, e2);
        e1.info = condJump(fs, op, r1, r2, isfloat[0] ? 1 : 0, opr == OPR_EQ ? 1 : 0);
        e1.k = ExpDesc.VJMP;
    }

    /** {@code codeconcat} */
    private static void codeConcat(FuncState fs, ExpDesc e1, ExpDesc e2, int line) {
        int ie2 = previousInstruction(fs);
        if (ie2 != -1 && OpCode.getOpCode(ie2) == OpCode.OP_CONCAT) {
            int n = OpCode.getArgB(ie2); // e2 里已拼接的元素数
            freeExp(fs, e2);
            int patched = OpCode.setArgA(ie2, e1.info);
            patched = OpCode.setArgB(patched, n + 1);
            fs.f.setCode(fs.pc - 1, patched);
        } else {
            codeABC(fs, OpCode.OP_CONCAT, e1.info, 2, 0);
            freeExp(fs, e2);
            fixLine(fs, line);
        }
    }

    /* ---------- 常量折叠 ---------- */

    /** {@code tonumeral} */
    private static boolean isNumeral(ExpDesc e) {
        return !e.hasJumps() && (e.k == ExpDesc.VKINT || e.k == ExpDesc.VKFLT);
    }

    /** {@code validop}：折叠会不会引发错误（除零、浮点转整数失败） */
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
        case Arith.OP_MOD: {
            Double n = v2.isInteger() ? (double) v2.i : (v2.isFloat() ? v2.n : null);
            return n != null && n != 0;
        }
        default:
            return true;
        }
    }

    /** {@code constfolding} */
    private static boolean constFolding(FuncState fs, int op, ExpDesc e1, ExpDesc e2) {
        LuaValue v1 = toNumeralValue(e1);
        LuaValue v2 = toNumeralValue(e2);
        if (v1 == null || v2 == null || !validOp(op, v1, v2)) {
            return false;
        }
        /*
             * 常量折叠没有 LuaState，除零那两个分支也到不了
             * （validOp 已经把它们挡在外面），所以这里传 null 是安全的。
             */
            LuaValue res = Arith.rawArith(null, op, v1, v2);
        if (res == null) {
            return false;
        }
        if (res.isInteger()) {
            e1.k = ExpDesc.VKINT;
            e1.ival = res.i;
        } else {
            // NaN 与 0.0 不折叠（避免 -0.0 的问题）
            double n = res.n;
            if (Double.isNaN(n) || n == 0) {
                return false;
            }
            e1.k = ExpDesc.VKFLT;
            e1.nval = n;
        }
        return true;
    }

    private static LuaValue toNumeralValue(ExpDesc e) {
        if (e.hasJumps()) {
            return null;
        }
        if (e.k == ExpDesc.VKINT) {
            return LuaValue.ofInteger(e.ival);
        }
        if (e.k == ExpDesc.VKFLT) {
            return LuaValue.ofFloat(e.nval);
        }
        return null;
    }

    /* ---------- prefix / infix / posfix ---------- */

    /** {@code luaK_prefix} */
    public static void prefix(FuncState fs, int opr, ExpDesc e, int line) {
        dischargeVars(fs, e);
        switch (opr) {
        case UNOPR_MINUS:
        case UNOPR_BNOT: {
            ExpDesc ef = ExpDesc.ofInt(0); // 假的第二个操作数
            if (constFolding(fs, opr + Arith.OP_UNM, e, ef)) {
                break;
            }
            // FALLTHROUGH
            codeUnExpVal(fs, unOpr2Op(opr), e, line);
            break;
        }
        case UNOPR_LEN:
            codeUnExpVal(fs, unOpr2Op(opr), e, line);
            break;
        case UNOPR_NOT:
            codeNot(fs, e);
            break;
        default:
            throw new IllegalStateException("bad unopr " + opr);
        }
    }

    /** {@code luaK_infix} */
    public static void infix(FuncState fs, int op, ExpDesc v) {
        dischargeVars(fs, v);
        switch (op) {
        case OPR_AND:
            goIfTrue(fs, v);
            break;
        case OPR_OR:
            goIfFalse(fs, v);
            break;
        case OPR_CONCAT:
            exp2NextReg(fs, v); // 拼接的操作数必须在栈上相邻
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
            if (!isNumeral(v)) {
                exp2AnyReg(fs, v);
            }
            break;
        case OPR_EQ:
        case OPR_NE:
            if (!isNumeral(v)) {
                exp2RK(fs, v);
            }
            break;
        case OPR_LT:
        case OPR_LE:
        case OPR_GT:
        case OPR_GE: {
            int[] dummy = new int[1];
            boolean[] dummy2 = new boolean[1];
            if (!isSCnumber(v, dummy, dummy2)) {
                exp2AnyReg(fs, v);
            }
            break;
        }
        default:
            throw new IllegalStateException("bad binopr " + op);
        }
    }

    /** {@code luaK_posfix} */
    public static void posfix(FuncState fs, int opr, ExpDesc e1, ExpDesc e2, int line) {
        dischargeVars(fs, e2);
        if (foldBinOp(opr) && constFolding(fs, opr + Arith.OP_ADD, e1, e2)) {
            return; // 折叠掉了
        }
        switch (opr) {
        case OPR_AND:
            concatF(fs, e2, e1.f);
            e1.set(e2);
            break;
        case OPR_OR:
            concatT(fs, e2, e1.t);
            e1.set(e2);
            break;
        case OPR_CONCAT:
            exp2NextReg(fs, e2);
            codeConcat(fs, e1, e2, line);
            break;
        case OPR_ADD:
        case OPR_MUL:
            codeCommutative(fs, opr, e1, e2, line);
            break;
        case OPR_SUB:
            if (finishBinExpNeg(fs, e1, e2, OpCode.OP_ADDI, line, Tm.TM_SUB)) {
                break; // 编成了 (r1 + -I)
            }
            // FALLTHROUGH
        case OPR_DIV:
        case OPR_IDIV:
        case OPR_MOD:
        case OPR_POW:
            codeArith(fs, opr, e1, e2, 0, line);
            break;
        case OPR_BAND:
        case OPR_BOR:
        case OPR_BXOR:
            codeBitwise(fs, opr, e1, e2, line);
            break;
        case OPR_SHL:
            if (isSCint(e1)) {
                swapExps(e1, e2);
                codeBinI(fs, OpCode.OP_SHLI, e1, e2, 1, line, Tm.TM_SHL); // I << r2
            } else if (finishBinExpNeg(fs, e1, e2, OpCode.OP_SHRI, line, Tm.TM_SHL)) {
                // 编成了 (r1 >> -I)
            } else {
                codeBinExpVal(fs, opr, e1, e2, line);
            }
            break;
        case OPR_SHR:
            if (isSCint(e2)) {
                codeBinI(fs, OpCode.OP_SHRI, e1, e2, 0, line, Tm.TM_SHR); // r1 >> I
            } else {
                codeBinExpVal(fs, opr, e1, e2, line);
            }
            break;
        case OPR_EQ:
        case OPR_NE:
            codeEq(fs, opr, e1, e2);
            break;
        case OPR_GT:
        case OPR_GE:
            // (a > b) == (b < a)；(a >= b) == (b <= a)
            swapExps(e1, e2);
            opr = (opr - OPR_GT) + OPR_LT;
            // FALLTHROUGH
        case OPR_LT:
        case OPR_LE:
            codeOrder(fs, opr, e1, e2);
            break;
        default:
            throw new IllegalStateException("bad binopr " + opr);
        }
    }

    /* ---------- 表构造 ---------- */

    /** {@code luaK_settablesize}：回填 NEWTABLE 的数组/哈希大小 */
    public static void setTableSize(FuncState fs, int pc, int ra, int asize, int hsize) {
        int rb = (hsize != 0) ? ceilLog2(hsize) + 1 : 0;
        int extra = asize / (OpCode.MAXARG_C + 1); // 数组大小的高位
        int rc = asize % (OpCode.MAXARG_C + 1); // 低位
        int k = (extra > 0) ? 1 : 0;
        fs.f.setCode(pc, OpCode.createABCk(OpCode.OP_NEWTABLE, ra, rb, rc, k));
        fs.f.setCode(pc + 1, OpCode.createAx(OpCode.OP_EXTRAARG, extra));
    }

    /**
     * {@code luaO_ceillog2}：不小于 {@code log2(x)} 的最小整数。
     * <p>
     * 上游用「先 {@code x--} 再查一张 256 项表」实现，那张表记的其实是
     * 「表示 x 需要几位」，所以整件事等价于 {@code bitlength(x - 1)}。
     * 直接写等价形式而不是照抄移位：照抄的版本我写错过一次
     * （{@code x = 2} 算出 0 而不是 1，于是 {@code {a = 1, b = 2}} 的
     * NEWTABLE 少算一格哈希）。
     */
    static int ceilLog2(int x) {
        if (x <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(x - 1);
    }

    /** {@code luaK_setlist} */
    public static void setList(FuncState fs, int base, int nelems, int tostore) {
        if (tostore == MULTRET) {
            tostore = 0;
        }
        if (nelems <= OpCode.MAXARG_C) {
            codeABC(fs, OpCode.OP_SETLIST, base, tostore, nelems);
        } else {
            int extra = nelems / (OpCode.MAXARG_C + 1);
            nelems %= (OpCode.MAXARG_C + 1);
            codeABCk(fs, OpCode.OP_SETLIST, base, tostore, nelems, 1);
            codeExtraArg(fs, extra);
        }
        fs.freereg = base + 1; // 列表里的值占用的寄存器释放掉
    }

    /* ---------- 收尾 ---------- */

    /** {@code finaltarget}：跳过「跳到跳转」的链 */
    private static int finalTarget(int[] code, int i) {
        for (int count = 0; count < 100; count++) { // 防止死循环
            int pc = code[i];
            if (OpCode.getOpCode(pc) != OpCode.OP_JMP) {
                break;
            }
            i += OpCode.getArgSJ(pc) + 1;
        }
        return i;
    }

    /** {@code luaK_finish}：最后一遍扫描，做小优化与回填 */
    public static void finish(FuncState fs) {
        Proto p = fs.f;
        for (int i = 0; i < fs.pc; i++) {
            int inst = p.code[i];
            int op = OpCode.getOpCode(inst);
            switch (op) {
            case OpCode.OP_RETURN0:
            case OpCode.OP_RETURN1: {
                if (!(fs.needclose || p.isVararg)) {
                    break; // 没什么要补的
                }
                inst = OpCode.setOpCode(inst, OpCode.OP_RETURN);
                op = OpCode.OP_RETURN;
            }
                // FALLTHROUGH
            case OpCode.OP_RETURN:
            case OpCode.OP_TAILCALL: {
                if (fs.needclose) {
                    inst = OpCode.setArgK(inst, 1);
                }
                if (p.isVararg) {
                    inst = OpCode.setArgC(inst, p.numparams + 1);
                }
                p.setCode(i, inst);
                break;
            }
            case OpCode.OP_JMP: {
                int target = finalTarget(p.code, i);
                fixJump(fs, i, target);
                break;
            }
            default:
                break;
            }
        }
    }

    /** {@code luaK_semerror}：语义错误不带 "near <token>" */
    public static LuaError semError(FuncState fs, String msg) {
        fs.ls.t.token = 0;
        return fs.ls.syntaxError(msg);
    }
}
