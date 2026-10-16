package com.github.esrrhs.fakescript.lua;

import java.util.Locale;

/**
 * 语法分析器 —— 直译 {@code lparser.c}。
 * <p>
 * 上游是<b>手写递归下降</b>，而且边解析边发指令（没有独立的 AST）：表达式解析
 * 出来的不是树，而是一个 {@link ExpDesc}（「这个值现在在哪儿」），等到不得不
 * 落地时才由 {@link Codegen} 决定最省指令的做法。所以 parser 与 codegen
 * 在结构上无法分开落地——这也是这两个阶段必须一起做的原因。
 * <p>
 * 几处 Java 化但语义等价的改动：
 * <ul>
 *   <li>{@code BlockCnt} 在 C 里是栈上分配、靠 {@code fs->bl} 串成链；Java 只能
 *       堆分配，于是每进一个块 {@code new} 一个。链的结构和遍历顺序不变；</li>
 *   <li>C 的 {@code enterlevel/leavelevel} 靠 {@code nCcalls} 防 C 栈溢出。Java
 *       没有可靠的栈余量探测，这里自己数递归深度，超了就报
 *       {@code C stack overflow}；</li>
 *   <li>{@code TString*} 的指针相等（{@code eqstr}）换成 {@code LuaString} 的
 *       同一性 + 内容比较。所有名字都经同一个 {@link StringPool} 驻留，
 *       同一性判断在实际运行中恒成立；写 {@code eqstr} 是为了不依赖这一点；</li>
 *   <li>{@code int *escapelist} 这类出参改成 {@code int[]}。</li>
 * </ul>
 */
public final class Parser {

    /** {@code MAXVARS}：单个函数的局部变量上限（必须 < 250，受字节码格式限制） */
    private static final int MAXVARS = 200;
    /** {@code MAXUPVAL}（{@code lfunc.h}） */
    private static final int MAXUPVAL = 255;
    /** {@code SHRT_MAX}——{@code locvars} 的下标是 short */
    private static final int SHRT_MAX = 32767;
    /** 递归深度上限，对应 {@code LUAI_MAXCCALLS} 的作用 */
    private static final int MAXDEPTH = 200;

    /** {@code LUA_ENV}：主函数的环境 upvalue 名字 */
    private static final String ENV_NAME = "_ENV";

    private final Lexer ls;
    private final DynData dyd;
    private final LuaString source;
    private final LuaString envn;
    private final LuaString breakName;

    /** 当前函数的编译状态 */
    private FuncState fs;
    /** 递归深度（{@code nCcalls}） */
    private int depth;

    private Parser(Lexer ls, DynData dyd, LuaString source) {
        this.ls = ls;
        this.dyd = dyd;
        this.source = source;
        this.envn = ls.newString(LuaString.latin1(ENV_NAME).bytes, 0, ENV_NAME.length());
        this.breakName = ls.newString(LuaString.latin1("break").bytes, 0, 5);
    }

    /**
     * 解析一段源码，返回主函数的原型。
     * <p>
     * 上游这里返回的是带一个 upvalue（{@code _ENV}）的 {@code LClosure}；
     * 闭包是阶段 2 后半的内容，先把 {@link Proto} 交出来，等 {@code LClosure}
     * 落地后再套一层。
     */
    public static Proto parse(byte[] data, byte[] chunkName, StringPool strings) {
        return parse(new Zio(data), chunkName, strings);
    }

    /**
     * 从一条（可能是惰性的）输入流解析。
     * <p>
     * {@code load} 接读函数时走这一条：字节是边解析边要的，读函数的调用次数
     * 也就与 C 一致。
     */
    public static Proto parse(Zio z, byte[] chunkName, StringPool strings) {
        LuaString src = (chunkName == null) ? LuaString.latin1("?") : LuaString.of(chunkName);
        Lexer ls = new Lexer(z, chunkName, strings);
        DynData dyd = new DynData();
        Parser p = new Parser(ls, dyd, src);
        Proto f = new Proto();
        f.source = src;
        FuncState funcstate = new FuncState(f, null, ls, dyd);
        p.fs = funcstate;
        p.mainFunc(funcstate);
        return f;
    }

    /**
     * 按「从文件加载」的语义解析：先吃掉 BOM 与首行的 {@code #!...}。
     * <p>
     * 这一步 {@code lauxlib.c: skipcomment} 才做，<b>不属于词法分析</b>——
     * 用 {@code load} 加载字符串时首行写 {@code #} 照样报
     * {@code unexpected symbol}。所以单独开一个入口，别混进 {@link #parse}。
     * <p>
     * 容易被忽略的一点：跳过首行之后要<b>补一个换行</b>，否则后面所有行的
     * 行号都会少 1，调试信息全错。
     */
    public static Proto parseFile(byte[] data, byte[] chunkName, StringPool strings) {
        return parse(skipFilePreamble(data), chunkName, strings);
    }

    /** {@code lauxlib.c: skipBOM + skipcomment} */
    static byte[] skipFilePreamble(byte[] data) {
        int off = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            off = 3; // UTF-8 BOM 由加载层吃掉，不属于源码
        }
        if (off < data.length && data[off] == '#') {
            int i = off;
            while (i < data.length && data[i] != '\n') {
                i++;
            }
            if (i < data.length) {
                i++; // 吃掉换行本身
            }
            byte[] out = new byte[1 + data.length - i];
            out[0] = '\n'; // 补回被跳过的那一行，保住行号
            System.arraycopy(data, i, out, 1, data.length - i);
            return out;
        }
        if (off == 0) {
            return data;
        }
        byte[] noBom = new byte[data.length - off];
        System.arraycopy(data, off, noBom, 0, noBom.length);
        return noBom;
    }

    public static Proto parse(String utf8Source, String chunkName) {
        return parse(utf8Source.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                chunkName == null ? null : chunkName.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new StringPool());
    }

    /* ============================================================ */
    /* 块结构                                                        */
    /* ============================================================ */

    /**
     * {@code BlockCnt}：活跃块链表的节点。
     * <p>
     * C 里它是栈上的局部结构体，靠 {@code fs->bl} 串起来；这里只能堆分配。
     */
    static final class BlockCnt {
        BlockCnt previous;
        int firstlabel;
        int firstgoto;
        int nactvar;
        boolean upval;
        boolean isloop;
        boolean insidetbc;
    }

    /** 多重赋值左侧的变量链（{@code struct LHS_assign}） */
    private static final class LhsAssign {
        LhsAssign prev;
        final ExpDesc v = new ExpDesc();
    }

    /** 表构造器的控制结构（{@code ConsControl}） */
    private static final class ConsControl {
        final ExpDesc v = new ExpDesc();
        ExpDesc t;
        int nh;
        int na;
        int tostore;
    }

    /* ============================================================ */
    /* 小工具                                                        */
    /* ============================================================ */

    /** {@code eqstr}：驻留之后指针相等即内容相等，这里两者都认 */
    private static boolean eqstr(LuaString a, LuaString b) {
        return a == b || (a != null && a.equals(b));
    }

    private static boolean hasMultRet(int k) {
        return k == ExpDesc.VCALL || k == ExpDesc.VVARARG;
    }

    /** {@code luaX_newstring} 的便捷形式：把一个 ASCII 字面量驻留进词法层的池 */
    private LuaString literal(String s) {
        return ls.newString(LuaString.latin1(s).bytes, 0, s.length());
    }

    private LuaError errorExpected(int token) {
        return ls.syntaxError(Lexer.tokenName(token) + " expected");
    }

    /** {@code errorlimit} */
    private LuaError errorLimit(FuncState fs, int limit, String what) {
        int line = fs.f.linedefined;
        String where = (line == 0) ? "main function"
                : String.format(Locale.ROOT, "function at line %d", line);
        String msg = String.format(Locale.ROOT, "too many %s (limit is %d) in %s", what, limit, where);
        return ls.syntaxError(msg);
    }

    private void checkLimit(FuncState fs, int v, int l, String what) {
        if (v > l) {
            throw errorLimit(fs, l, what);
        }
    }

    /** {@code testnext} */
    private boolean testNext(int c) {
        if (ls.t.token == c) {
            ls.next();
            return true;
        }
        return false;
    }

    /** {@code check} */
    private void check(int c) {
        if (ls.t.token != c) {
            throw errorExpected(c);
        }
    }

    /** {@code checknext} */
    private void checkNext(int c) {
        check(c);
        ls.next();
    }

    /** {@code check_match} */
    private void checkMatch(int what, int who, int where) {
        if (!testNext(what)) {
            if (where == ls.linenumber) {
                throw errorExpected(what);
            }
            throw ls.syntaxError(String.format(Locale.ROOT, "%s expected (to close %s at line %d)",
                    Lexer.tokenName(what), Lexer.tokenName(who), where));
        }
    }

    /** {@code str_checkname} */
    private LuaString strCheckName() {
        check(Lexer.TK_NAME);
        LuaString ts = ls.t.s;
        ls.next();
        return ts;
    }

    /** {@code init_exp} */
    private static void initExp(ExpDesc e, int k, int i) {
        e.f = e.t = Codegen.NO_JUMP;
        e.k = k;
        e.info = i;
    }

    /** {@code codestring} */
    private static void codeString(ExpDesc e, LuaString s) {
        e.f = e.t = Codegen.NO_JUMP;
        e.k = ExpDesc.VKSTR;
        e.strval = s;
    }

    /** {@code codename} */
    private void codeName(ExpDesc e) {
        codeString(e, strCheckName());
    }

    /** {@code enterlevel} / {@code leavelevel} */
    private void enterLevel() {
        if (++depth >= MAXDEPTH) {
            throw new LuaError(LuaValue.ofString(LuaString.latin1("C stack overflow")));
        }
    }

    private void leaveLevel() {
        depth--;
    }

    /* ============================================================ */
    /* 局部变量                                                      */
    /* ============================================================ */

    /** {@code registerlocalvar} */
    private int registerLocalVar(FuncState fs, LuaString varname) {
        Proto.LocVar lv = fs.f.allocLocVar();
        lv.varname = varname;
        lv.startpc = fs.pc;
        return fs.ndebugvars++;
    }

    /** {@code new_localvar} */
    private int newLocalVar(LuaString name) {
        FuncState fs = this.fs;
        checkLimit(fs, dyd.actvarN + 1 - fs.firstlocal, MAXVARS, "local variables");
        checkLimit(fs, dyd.actvarN + 1, SHRT_MAX, "local variables");
        DynData.Vardesc var = new DynData.Vardesc();
        var.kind = DynData.VDKREG;
        var.name = name;
        dyd.setActvar(dyd.actvarN, var);
        return dyd.actvarN++ - fs.firstlocal;
    }

    /** {@code new_localvarliteral} */
    private int newLocalVarLiteral(String name) {
        return newLocalVar(literal(name));
    }

    /** {@code getlocalvardesc} */
    private static DynData.Vardesc getLocalVarDesc(FuncState fs, int vidx) {
        return fs.dyd.actvar.get(fs.firstlocal + vidx);
    }

    /** {@code localdebuginfo} */
    private static Proto.LocVar localDebugInfo(FuncState fs, int vidx) {
        DynData.Vardesc vd = getLocalVarDesc(fs, vidx);
        if (vd.kind == DynData.RDKCTC) {
            return null; // 常量没有调试信息
        }
        return fs.f.locvars[vd.pidx];
    }

    /** {@code init_var} */
    private static void initVar(FuncState fs, ExpDesc e, int vidx) {
        e.f = e.t = Codegen.NO_JUMP;
        e.k = ExpDesc.VLOCAL;
        e.varVidx = vidx;
        e.varRidx = getLocalVarDesc(fs, vidx).ridx;
    }

    /** {@code check_readonly} */
    private void checkReadonly(ExpDesc e) {
        FuncState fs = this.fs;
        LuaString varname = null;
        switch (e.k) {
        case ExpDesc.VCONST:
            varname = dyd.actvar.get(e.info).name;
            break;
        case ExpDesc.VLOCAL: {
            DynData.Vardesc vardesc = getLocalVarDesc(fs, e.varVidx);
            if (vardesc.kind != DynData.VDKREG) {
                varname = vardesc.name;
            }
            break;
        }
        case ExpDesc.VUPVAL: {
            Proto.Upvaldesc up = fs.f.upvalues[e.info];
            if (up.kind != DynData.VDKREG) {
                varname = up.name;
            }
            break;
        }
        default:
            return; // 其余情况不可能是只读的
        }
        if (varname != null) {
            String msg = String.format(Locale.ROOT,
                    "attempt to assign to const variable '%s'", varname.toJavaString());
            throw Codegen.semError(fs, msg);
        }
    }

    /** {@code adjustlocalvars} */
    private void adjustLocalVars(int nvars) {
        FuncState fs = this.fs;
        int reglevel = fs.nvarstack();
        for (int i = 0; i < nvars; i++) {
            int vidx = fs.nactvar++;
            DynData.Vardesc var = getLocalVarDesc(fs, vidx);
            var.ridx = reglevel++;
            var.pidx = registerLocalVar(fs, var.name);
        }
    }

    /** {@code removevars} */
    private void removeVars(FuncState fs, int tolevel) {
        // 只动逻辑长度；槽位本身留着，下面的循环还要读它们去补 endpc
        dyd.actvarN -= (fs.nactvar - tolevel);
        while (fs.nactvar > tolevel) {
            Proto.LocVar var = localDebugInfo(fs, --fs.nactvar);
            if (var != null) {
                var.endpc = fs.pc;
            }
        }
    }

    /* ---------- upvalue ---------- */

    /** {@code searchupvalue} */
    private int searchUpvalue(FuncState fs, LuaString name) {
        for (int i = 0; i < fs.nups; i++) {
            if (eqstr(fs.f.upvalues[i].name, name)) {
                return i;
            }
        }
        return -1;
    }

    /** {@code allocupvalue} */
    private Proto.Upvaldesc allocUpvalue(FuncState fs) {
        checkLimit(fs, fs.nups + 1, MAXUPVAL, "upvalues");
        Proto.Upvaldesc up = fs.f.allocUpvalue();
        fs.nups++;
        return up;
    }

    /** {@code newupvalue} */
    private int newUpvalue(FuncState fs, LuaString name, ExpDesc v) {
        Proto.Upvaldesc up = allocUpvalue(fs);
        FuncState prev = fs.prev;
        if (v.k == ExpDesc.VLOCAL) {
            up.instack = true;
            up.idx = v.varRidx;
            up.kind = getLocalVarDesc(prev, v.varVidx).kind;
        } else {
            up.instack = false;
            up.idx = v.info;
            up.kind = prev.f.upvalues[v.info].kind;
        }
        up.name = name;
        return fs.nups - 1;
    }

    /** {@code searchvar} */
    private int searchVar(FuncState fs, LuaString n, ExpDesc var) {
        for (int i = fs.nactvar - 1; i >= 0; i--) {
            DynData.Vardesc vd = getLocalVarDesc(fs, i);
            if (eqstr(n, vd.name)) {
                if (vd.kind == DynData.RDKCTC) {
                    initExp(var, ExpDesc.VCONST, fs.firstlocal + i);
                } else {
                    initVar(fs, var, i);
                }
                return var.k;
            }
        }
        return -1;
    }

    /** {@code markupval} */
    private void markUpval(FuncState fs, int level) {
        BlockCnt bl = fs.bl;
        while (bl.nactvar > level) {
            bl = bl.previous;
        }
        bl.upval = true;
        fs.needclose = true;
    }

    /** {@code marktobeclosed} */
    private void markToBeClosed(FuncState fs) {
        BlockCnt bl = fs.bl;
        bl.upval = true;
        bl.insidetbc = true;
        fs.needclose = true;
    }

    /** {@code singlevaraux} */
    private void singlevarAux(FuncState fs, LuaString n, ExpDesc var, boolean base) {
        if (fs == null) {
            initExp(var, ExpDesc.VVOID, 0); // 到底了：是全局变量
        } else {
            int v = searchVar(fs, n, var);
            if (v >= 0) {
                if (v == ExpDesc.VLOCAL && !base) {
                    markUpval(fs, var.varVidx); // 会被内层当 upvalue 用
                }
            } else {
                int idx = searchUpvalue(fs, n);
                if (idx < 0) {
                    singlevarAux(fs.prev, n, var, false);
                    if (var.k == ExpDesc.VLOCAL || var.k == ExpDesc.VUPVAL) {
                        idx = newUpvalue(fs, n, var);
                    } else {
                        return; // 全局或常量，本层不用做什么
                    }
                }
                initExp(var, ExpDesc.VUPVAL, idx);
            }
        }
    }

    /** {@code singlevar} */
    private void singlevar(ExpDesc var) {
        LuaString varname = strCheckName();
        FuncState fs = this.fs;
        singlevarAux(fs, varname, var, true);
        if (var.k == ExpDesc.VVOID) { // 是全局名
            ExpDesc key = new ExpDesc();
            singlevarAux(fs, envn, var, true); // 取 _ENV
            Codegen.exp2AnyRegUp(fs, var);
            codeString(key, varname);
            Codegen.indexed(fs, var, key); // _ENV[varname]
        }
    }

    /** {@code adjust_assign} */
    private void adjustAssign(int nvars, int nexps, ExpDesc e) {
        FuncState fs = this.fs;
        int needed = nvars - nexps;
        if (hasMultRet(e.k)) {
            int extra = needed + 1;
            if (extra < 0) {
                extra = 0;
            }
            Codegen.setReturns(fs, e, extra);
        } else {
            if (e.k != ExpDesc.VVOID) {
                Codegen.exp2NextReg(fs, e);
            }
            if (needed > 0) {
                Codegen.codeNil(fs, fs.freereg, needed);
            }
        }
        if (needed > 0) {
            Codegen.reserveRegs(fs, needed);
        } else {
            fs.freereg += needed; // needed 为负，这里是收掉多余的值
        }
    }

    /* ============================================================ */
    /* goto / label                                                  */
    /* ============================================================ */

    /** {@code jumpscopeerror} */
    private void jumpScopeError(DynData.Labeldesc gt) {
        LuaString varname = getLocalVarDesc(fs, gt.nactvar).name;
        String msg = String.format(Locale.ROOT,
                "<goto %s> at line %d jumps into the scope of local '%s'",
                gt.name.toJavaString(), gt.line, varname.toJavaString());
        throw Codegen.semError(fs, msg);
    }

    /** {@code solvegoto} */
    private void solveGoto(int g, DynData.Labeldesc label) {
        DynData.Labeldesc gt = dyd.gt.get(g);
        if (gt.nactvar < label.nactvar) { // 跳进了某个变量的作用域
            jumpScopeError(gt);
        }
        Codegen.patchList(fs, gt.pc, label.pc);
        dyd.gt.remove(g);
    }

    /** {@code findlabel} */
    private DynData.Labeldesc findLabel(LuaString name) {
        for (int i = fs.firstlabel; i < dyd.label.size(); i++) {
            DynData.Labeldesc lb = dyd.label.get(i);
            if (eqstr(lb.name, name)) {
                return lb;
            }
        }
        return null;
    }

    /** {@code newlabelentry} */
    private int newLabelEntry(boolean isGoto, LuaString name, int line, int pc) {
        DynData.Labeldesc l = new DynData.Labeldesc();
        l.name = name;
        l.line = line;
        l.nactvar = fs.nactvar;
        l.close = false;
        l.pc = pc;
        if (isGoto) {
            checkLimit(fs, dyd.gt.size() + 1, SHRT_MAX, "labels/gotos");
            dyd.gt.add(l);
            return dyd.gt.size() - 1;
        }
        checkLimit(fs, dyd.label.size() + 1, SHRT_MAX, "labels/gotos");
        dyd.label.add(l);
        return dyd.label.size() - 1;
    }

    /** {@code solvegotos} */
    private boolean solveGotos(DynData.Labeldesc lb) {
        int i = fs.bl.firstgoto;
        boolean needsclose = false;
        while (i < dyd.gt.size()) {
            if (eqstr(dyd.gt.get(i).name, lb.name)) {
                if (dyd.gt.get(i).close) {
                    needsclose = true;
                }
                solveGoto(i, lb); // 会把 i 位置上的项删掉
            } else {
                i++;
            }
        }
        return needsclose;
    }

    /** {@code createlabel} */
    private boolean createLabel(LuaString name, int line, boolean last) {
        FuncState fs = this.fs;
        int l = newLabelEntry(false, name, line, Codegen.getLabel(fs));
        if (last) { // 标签是块里最后一条空语句
            dyd.label.get(l).nactvar = fs.bl.nactvar; // 认为局部变量已出作用域
        }
        if (solveGotos(dyd.label.get(l))) {
            Codegen.codeABC(fs, OpCode.OP_CLOSE, fs.nvarstack(), 0, 0);
            return true;
        }
        return false;
    }

    /** {@code movegotosout} */
    private void moveGotosOut(FuncState fs, BlockCnt bl) {
        for (int i = bl.firstgoto; i < dyd.gt.size(); i++) {
            DynData.Labeldesc gt = dyd.gt.get(i);
            if (FuncState.reglevel(fs, gt.nactvar) > FuncState.reglevel(fs, bl.nactvar)) {
                if (bl.upval) {
                    gt.close = true; // 跳转可能需要 close
                }
            }
            gt.nactvar = bl.nactvar;
        }
    }

    /** {@code enterblock} */
    private void enterBlock(FuncState fs, BlockCnt bl, boolean isloop) {
        bl.isloop = isloop;
        bl.nactvar = fs.nactvar;
        bl.firstlabel = dyd.label.size();
        bl.firstgoto = dyd.gt.size();
        bl.upval = false;
        bl.insidetbc = (fs.bl != null && fs.bl.insidetbc);
        bl.previous = fs.bl;
        fs.bl = bl;
    }

    /** {@code undefgoto} */
    private void undefGoto(DynData.Labeldesc gt) {
        String msg;
        if (eqstr(gt.name, breakName)) {
            msg = String.format(Locale.ROOT, "break outside loop at line %d", gt.line);
        } else {
            msg = String.format(Locale.ROOT, "no visible label '%s' for <goto> at line %d",
                    gt.name.toJavaString(), gt.line);
        }
        throw Codegen.semError(fs, msg);
    }

    /** {@code leaveblock} */
    private void leaveBlock(FuncState fs) {
        BlockCnt bl = fs.bl;
        boolean hasclose = false;
        int stklevel = FuncState.reglevel(fs, bl.nactvar);
        removeVars(fs, bl.nactvar);
        if (bl.isloop) { // 有未解决的 break？
            hasclose = createLabel(breakName, 0, false);
        }
        if (!hasclose && bl.previous != null && bl.upval) {
            Codegen.codeABC(fs, OpCode.OP_CLOSE, stklevel, 0, 0);
        }
        fs.freereg = stklevel;
        while (dyd.label.size() > bl.firstlabel) {
            dyd.label.remove(dyd.label.size() - 1);
        }
        fs.bl = bl.previous;
        if (bl.previous != null) {
            moveGotosOut(fs, bl);
        } else if (bl.firstgoto < dyd.gt.size()) {
            undefGoto(dyd.gt.get(bl.firstgoto));
        }
    }

    /* ============================================================ */
    /* 函数边界                                                      */
    /* ============================================================ */

    /** {@code addprototype} */
    private static Proto addPrototype(FuncState fs) {
        Proto clp = new Proto();
        fs.f.addNested(clp);
        return clp;
    }

    /** {@code codeclosure} */
    private void codeClosure(ExpDesc v) {
        FuncState fs = this.fs.prev;
        int np = fs.f.sizep - 1;
        initExp(v, ExpDesc.VRELOC, Codegen.codeABx(fs, OpCode.OP_CLOSURE, 0, np));
        Codegen.exp2NextReg(fs, v);
    }

    /** {@code open_func} */
    private void openFunc(FuncState fs, BlockCnt bl) {
        Proto f = fs.f;
        fs.pc = 0;
        fs.previousline = f.linedefined;
        fs.iwthabs = 0;
        fs.lasttarget = 0;
        fs.freereg = 0;
        fs.nups = 0;
        fs.ndebugvars = 0;
        fs.nactvar = 0;
        fs.needclose = false;
        fs.firstlocal = dyd.actvarN;
        fs.firstlabel = dyd.label.size();
        fs.bl = null;
        f.source = source;
        f.maxstacksize = 2; // 寄存器 0/1 永远可用
        enterBlock(fs, bl, false);
    }

    /** {@code close_func} */
    private void closeFunc() {
        FuncState fs = this.fs;
        Proto f = fs.f;
        Codegen.ret(fs, fs.nvarstack(), 0); // 末尾的 return
        leaveBlock(fs);
        Codegen.finish(fs);
        f.truncateCode(fs.pc);
        this.fs = fs.prev;
    }

    /* ============================================================ */
    /* 语法规则                                                      */
    /* ============================================================ */

    /** {@code block_follow} */
    private boolean blockFollow(boolean withuntil) {
        switch (ls.t.token) {
        case Lexer.TK_ELSE:
        case Lexer.TK_ELSEIF:
        case Lexer.TK_END:
        case Lexer.TK_EOS:
            return true;
        case Lexer.TK_UNTIL:
            return withuntil;
        default:
            return false;
        }
    }

    /** {@code statlist} */
    private void statList() {
        while (!blockFollow(true)) {
            if (ls.t.token == Lexer.TK_RETURN) {
                statement();
                return; // return 必须是最后一条语句
            }
            statement();
        }
    }

    /** {@code fieldsel} */
    private void fieldSel(ExpDesc v) {
        FuncState fs = this.fs;
        Codegen.exp2AnyRegUp(fs, v);
        ls.next(); // 跳过 '.' 或 ':'
        ExpDesc key = new ExpDesc();
        codeName(key);
        Codegen.indexed(fs, v, key);
    }

    /** {@code yindex} */
    private void yindex(ExpDesc v) {
        ls.next(); // 跳过 '['
        expr(v);
        Codegen.exp2Val(fs, v);
        checkNext(']');
    }

    /* ---------- 表构造 ---------- */

    /** {@code recfield} */
    private void recField(ConsControl cc) {
        FuncState fs = this.fs;
        int reg = fs.freereg;
        ExpDesc tab = new ExpDesc(cc.t);
        ExpDesc key = new ExpDesc();
        if (ls.t.token == Lexer.TK_NAME) {
            checkLimit(fs, cc.nh, Integer.MAX_VALUE, "items in a constructor");
            codeName(key);
        } else {
            yindex(key);
        }
        cc.nh++;
        checkNext('=');
        Codegen.indexed(fs, tab, key);
        ExpDesc val = new ExpDesc();
        expr(val);
        Codegen.storeVar(fs, tab, val);
        fs.freereg = reg;
    }

    /** {@code closelistfield} */
    private void closeListField(ConsControl cc) {
        if (cc.v.k == ExpDesc.VVOID) {
            return; // 没有待存的元素
        }
        Codegen.exp2NextReg(fs, cc.v);
        cc.v.k = ExpDesc.VVOID;
        if (cc.tostore == OpCode.LFIELDS_PER_FLUSH) {
            Codegen.setList(fs, cc.t.info, cc.na, cc.tostore);
            cc.na += cc.tostore;
            cc.tostore = 0;
        }
    }

    /** {@code lastlistfield} */
    private void lastListField(ConsControl cc) {
        if (cc.tostore == 0) {
            return;
        }
        if (hasMultRet(cc.v.k)) {
            Codegen.setMultiRet(fs, cc.v);
            Codegen.setList(fs, cc.t.info, cc.na, Codegen.MULTRET);
            cc.na--; // 最后一个表达式的元素数未知，不计数
        } else {
            if (cc.v.k != ExpDesc.VVOID) {
                Codegen.exp2NextReg(fs, cc.v);
            }
            Codegen.setList(fs, cc.t.info, cc.na, cc.tostore);
        }
        cc.na += cc.tostore;
    }

    /** {@code constructor} */
    private void constructor(ExpDesc t) {
        FuncState fs = this.fs;
        int line = ls.linenumber;
        int pc = Codegen.codeABC(fs, OpCode.OP_NEWTABLE, 0, 0, 0);
        Codegen.code(fs, 0); // 给 EXTRAARG 留位置
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        initExp(t, ExpDesc.VNONRELOC, fs.freereg);
        Codegen.reserveRegs(fs, 1);
        initExp(cc.v, ExpDesc.VVOID, 0);
        checkNext('{');
        do {
            if (ls.t.token == '}') {
                break;
            }
            closeListField(cc);
            field(cc);
        } while (testNext(',') || testNext(';'));
        checkMatch('}', '{', line);
        lastListField(cc);
        Codegen.setTableSize(fs, pc, t.info, cc.na, cc.nh);
    }

    /** {@code field} */
    private void field(ConsControl cc) {
        switch (ls.t.token) {
        case Lexer.TK_NAME: // 可能是 listfield 也可能是 recfield
            if (ls.lookahead() != '=') {
                listField(cc);
            } else {
                recField(cc);
            }
            break;
        case '[':
            recField(cc);
            break;
        default:
            listField(cc);
            break;
        }
    }

    /** {@code listfield} */
    private void listField(ConsControl cc) {
        expr(cc.v);
        cc.tostore++;
    }

    /* ---------- 函数定义与调用 ---------- */

    /** {@code setvararg} */
    private static void setVararg(FuncState fs, int nparams) {
        fs.f.isVararg = true;
        Codegen.codeABC(fs, OpCode.OP_VARARGPREP, nparams, 0, 0);
    }

    /** {@code parlist} */
    private void parList() {
        FuncState fs = this.fs;
        Proto f = fs.f;
        int nparams = 0;
        boolean isvararg = false;
        if (ls.t.token != ')') {
            do {
                switch (ls.t.token) {
                case Lexer.TK_NAME:
                    newLocalVar(strCheckName());
                    nparams++;
                    break;
                case Lexer.TK_DOTS:
                    ls.next();
                    isvararg = true;
                    break;
                default:
                    throw ls.syntaxError("<name> or '...' expected");
                }
            } while (!isvararg && testNext(','));
        }
        adjustLocalVars(nparams);
        f.numparams = fs.nactvar;
        if (isvararg) {
            setVararg(fs, f.numparams);
        }
        Codegen.reserveRegs(fs, fs.nactvar);
    }

    /** {@code body} */
    private void body(ExpDesc e, boolean ismethod, int line) {
        FuncState newFs = new FuncState(addPrototype(fs), fs, ls, dyd);
        BlockCnt bl = new BlockCnt();
        newFs.f.linedefined = line;
        FuncState saved = this.fs;
        this.fs = newFs;
        openFunc(newFs, bl);
        checkNext('(');
        if (ismethod) {
            newLocalVarLiteral("self");
            adjustLocalVars(1);
        }
        parList();
        checkNext(')');
        statList();
        newFs.f.lastlinedefined = ls.linenumber;
        checkMatch(Lexer.TK_END, Lexer.TK_FUNCTION, line);
        codeClosure(e);
        closeFunc();
        this.fs = saved;
    }

    /** {@code explist} */
    private int expList(ExpDesc v) {
        int n = 1;
        expr(v);
        while (testNext(',')) {
            Codegen.exp2NextReg(fs, v);
            expr(v);
            n++;
        }
        return n;
    }

    /** {@code funcargs} */
    private void funcArgs(ExpDesc f) {
        FuncState fs = this.fs;
        ExpDesc args = new ExpDesc();
        int base;
        int nparams;
        int line = ls.linenumber;
        switch (ls.t.token) {
        case '(':
            ls.next();
            if (ls.t.token == ')') {
                args.k = ExpDesc.VVOID;
            } else {
                expList(args);
                if (hasMultRet(args.k)) {
                    Codegen.setMultiRet(fs, args);
                }
            }
            checkMatch(')', '(', line);
            break;
        case '{':
            constructor(args);
            break;
        case Lexer.TK_STRING:
            codeString(args, ls.t.s);
            ls.next(); // 必须在 next 之前取 seminfo
            break;
        default:
            throw ls.syntaxError("function arguments expected");
        }
        base = f.info; // 调用的基址寄存器
        if (hasMultRet(args.k)) {
            nparams = Codegen.MULTRET;
        } else {
            if (args.k != ExpDesc.VVOID) {
                Codegen.exp2NextReg(fs, args);
            }
            nparams = fs.freereg - (base + 1);
        }
        initExp(f, ExpDesc.VCALL, Codegen.codeABC(fs, OpCode.OP_CALL, base, nparams + 1, 2));
        Codegen.fixLine(fs, line);
        fs.freereg = base + 1; // 调用吃掉函数与实参，留下一个结果
    }

    /* ---------- 表达式 ---------- */

    /** {@code primaryexp} */
    private void primaryExp(ExpDesc v) {
        switch (ls.t.token) {
        case '(': {
            int line = ls.linenumber;
            ls.next();
            expr(v);
            checkMatch(')', '(', line);
            Codegen.dischargeVars(fs, v);
            return;
        }
        case Lexer.TK_NAME:
            singlevar(v);
            return;
        default:
            throw ls.syntaxError("unexpected symbol");
        }
    }

    /** {@code suffixedexp} */
    private void suffixedExp(ExpDesc v) {
        FuncState fs = this.fs;
        primaryExp(v);
        for (;;) {
            switch (ls.t.token) {
            case '.':
                fieldSel(v);
                break;
            case '[': {
                ExpDesc key = new ExpDesc();
                Codegen.exp2AnyRegUp(fs, v);
                yindex(key);
                Codegen.indexed(fs, v, key);
                break;
            }
            case ':': {
                ExpDesc key = new ExpDesc();
                ls.next();
                codeName(key);
                Codegen.self(fs, v, key);
                funcArgs(v);
                break;
            }
            case '(':
            case Lexer.TK_STRING:
            case '{':
                Codegen.exp2NextReg(fs, v);
                funcArgs(v);
                break;
            default:
                return;
            }
        }
    }

    /** {@code simpleexp} */
    private void simpleExp(ExpDesc v) {
        switch (ls.t.token) {
        case Lexer.TK_FLT:
            initExp(v, ExpDesc.VKFLT, 0);
            v.nval = ls.t.r;
            break;
        case Lexer.TK_INT:
            initExp(v, ExpDesc.VKINT, 0);
            v.ival = ls.t.i;
            break;
        case Lexer.TK_STRING:
            codeString(v, ls.t.s);
            break;
        case Lexer.TK_NIL:
            initExp(v, ExpDesc.VNIL, 0);
            break;
        case Lexer.TK_TRUE:
            initExp(v, ExpDesc.VTRUE, 0);
            break;
        case Lexer.TK_FALSE:
            initExp(v, ExpDesc.VFALSE, 0);
            break;
        case Lexer.TK_DOTS: {
            FuncState fs = this.fs;
            if (!fs.f.isVararg) {
                throw ls.syntaxError("cannot use '...' outside a vararg function");
            }
            initExp(v, ExpDesc.VVARARG, Codegen.codeABC(fs, OpCode.OP_VARARG, 0, 0, 1));
            break;
        }
        case '{':
            constructor(v);
            return;
        case Lexer.TK_FUNCTION:
            ls.next();
            body(v, false, ls.linenumber);
            return;
        default:
            suffixedExp(v);
            return;
        }
        ls.next();
    }

    /** {@code getunopr} */
    private static int getUnOpr(int op) {
        switch (op) {
        case Lexer.TK_NOT:
            return Codegen.UNOPR_NOT;
        case '-':
            return Codegen.UNOPR_MINUS;
        case '~':
            return Codegen.UNOPR_BNOT;
        case '#':
            return Codegen.UNOPR_LEN;
        default:
            return Codegen.UNOPR_NOUNOPR;
        }
    }

    /** {@code getbinopr} */
    private static int getBinOpr(int op) {
        switch (op) {
        case '+':
            return Codegen.OPR_ADD;
        case '-':
            return Codegen.OPR_SUB;
        case '*':
            return Codegen.OPR_MUL;
        case '%':
            return Codegen.OPR_MOD;
        case '^':
            return Codegen.OPR_POW;
        case '/':
            return Codegen.OPR_DIV;
        case Lexer.TK_IDIV:
            return Codegen.OPR_IDIV;
        case '&':
            return Codegen.OPR_BAND;
        case '|':
            return Codegen.OPR_BOR;
        case '~':
            return Codegen.OPR_BXOR;
        case Lexer.TK_SHL:
            return Codegen.OPR_SHL;
        case Lexer.TK_SHR:
            return Codegen.OPR_SHR;
        case Lexer.TK_CONCAT:
            return Codegen.OPR_CONCAT;
        case Lexer.TK_NE:
            return Codegen.OPR_NE;
        case Lexer.TK_EQ:
            return Codegen.OPR_EQ;
        case '<':
            return Codegen.OPR_LT;
        case Lexer.TK_LE:
            return Codegen.OPR_LE;
        case '>':
            return Codegen.OPR_GT;
        case Lexer.TK_GE:
            return Codegen.OPR_GE;
        case Lexer.TK_AND:
            return Codegen.OPR_AND;
        case Lexer.TK_OR:
            return Codegen.OPR_OR;
        default:
            return Codegen.OPR_NOBINOPR;
        }
    }

    /** 二元运算符优先级表（{@code priority[]}，ORDER OPR） */
    private static final int[] PRIO_LEFT = {
            10, 10,           /* '+' '-' */
            11, 11,           /* '*' '%' */
            14,               /* '^' 右结合 */
            11, 11,           /* '/' '//' */
            6, 4, 5,          /* '&' '|' '~' */
            7, 7,             /* '<<' '>>' */
            9,                /* '..' 右结合 */
            3, 3, 3,          /* ==, <, <= */
            3, 3, 3,          /* ~=, >, >= */
            2, 1              /* and, or */
    };

    private static final int[] PRIO_RIGHT = {
            10, 10,
            11, 11,
            13,
            11, 11,
            6, 4, 5,
            7, 7,
            8,
            3, 3, 3,
            3, 3, 3,
            2, 1
    };

    private static final int UNARY_PRIORITY = 12;

    /**
     * {@code subexpr}。
     *
     * @return 第一个没被吃掉的运算符（供外层继续展开）
     */
    private int subExpr(ExpDesc v, int limit) {
        int op;
        int uop;
        enterLevel();
        uop = getUnOpr(ls.t.token);
        if (uop != Codegen.UNOPR_NOUNOPR) {
            int line = ls.linenumber;
            ls.next();
            subExpr(v, UNARY_PRIORITY);
            Codegen.prefix(fs, uop, v, line);
        } else {
            simpleExp(v);
        }
        op = getBinOpr(ls.t.token);
        while (op != Codegen.OPR_NOBINOPR && PRIO_LEFT[op] > limit) {
            ExpDesc v2 = new ExpDesc();
            int nextop;
            int line = ls.linenumber;
            ls.next();
            Codegen.infix(fs, op, v);
            nextop = subExpr(v2, PRIO_RIGHT[op]);
            Codegen.posfix(fs, op, v, v2, line);
            op = nextop;
        }
        leaveLevel();
        return op;
    }

    private void expr(ExpDesc v) {
        subExpr(v, 0);
    }

    /* ---------- 语句 ---------- */

    /** {@code block} */
    private void block() {
        FuncState fs = this.fs;
        BlockCnt bl = new BlockCnt();
        enterBlock(fs, bl, false);
        statList();
        leaveBlock(fs);
    }

    /**
     * {@code check_conflict}：{@code a[i], a = 1, 2} 这类赋值里，前面的索引要用
     * 后面被赋值的那个变量的<b>旧值</b>，所以先把旧值拷到一个临时寄存器。
     */
    private void checkConflict(LhsAssign lh, ExpDesc v) {
        FuncState fs = this.fs;
        int extra = fs.freereg;
        boolean conflict = false;
        for (; lh != null; lh = lh.prev) {
            if (lh.v.isIndexed()) {
                if (lh.v.k == ExpDesc.VINDEXUP) { // 表是 upvalue
                    if (v.k == ExpDesc.VUPVAL && lh.v.indT == v.info) {
                        conflict = true;
                        lh.v.k = ExpDesc.VINDEXSTR;
                        lh.v.indT = extra;
                    }
                } else { // 表在寄存器里
                    if (v.k == ExpDesc.VLOCAL && lh.v.indT == v.varRidx) {
                        conflict = true;
                        lh.v.indT = extra;
                    }
                    if (lh.v.k == ExpDesc.VINDEXED && v.k == ExpDesc.VLOCAL
                            && lh.v.indIdx == v.varRidx) {
                        conflict = true;
                        lh.v.indIdx = extra;
                    }
                }
            }
        }
        if (conflict) {
            if (v.k == ExpDesc.VLOCAL) {
                Codegen.codeABC(fs, OpCode.OP_MOVE, extra, v.varRidx, 0);
            } else {
                Codegen.codeABC(fs, OpCode.OP_GETUPVAL, extra, v.info, 0);
            }
            Codegen.reserveRegs(fs, 1);
        }
    }

    /** {@code restassign} */
    private void restAssign(LhsAssign lh, int nvars) {
        ExpDesc e = new ExpDesc();
        if (!lh.v.isVar()) {
            throw ls.syntaxError("syntax error");
        }
        checkReadonly(lh.v);
        if (testNext(',')) {
            LhsAssign nv = new LhsAssign();
            nv.prev = lh;
            suffixedExp(nv.v);
            if (!nv.v.isIndexed()) {
                checkConflict(lh, nv.v);
            }
            enterLevel();
            restAssign(nv, nvars + 1);
            leaveLevel();
        } else {
            int nexps;
            checkNext('=');
            nexps = expList(e);
            if (nexps != nvars) {
                adjustAssign(nvars, nexps, e);
            } else {
                Codegen.setOneRet(fs, e);
                Codegen.storeVar(fs, lh.v, e);
                return;
            }
        }
        initExp(e, ExpDesc.VNONRELOC, fs.freereg - 1); // 缺省赋值
        Codegen.storeVar(fs, lh.v, e);
    }

    /** {@code cond} */
    private int cond() {
        ExpDesc v = new ExpDesc();
        expr(v);
        if (v.k == ExpDesc.VNIL) {
            v.k = ExpDesc.VFALSE; // 这里所有 falsy 等价
        }
        Codegen.goIfTrue(fs, v);
        return v.f;
    }

    /** {@code gotostat} */
    private void gotoStat() {
        FuncState fs = this.fs;
        int line = ls.linenumber;
        LuaString name = strCheckName();
        DynData.Labeldesc lb = findLabel(name);
        if (lb == null) {
            // 向前跳，等标签声明时再解决
            newLabelEntry(true, name, line, Codegen.jump(fs));
        } else {
            int lblevel = FuncState.reglevel(fs, lb.nactvar);
            if (fs.nvarstack() > lblevel) { // 离开了某个变量的作用域
                Codegen.codeABC(fs, OpCode.OP_CLOSE, lblevel, 0, 0);
            }
            Codegen.patchList(fs, Codegen.jump(fs), lb.pc);
        }
    }

    /** {@code breakstat} */
    private void breakStat() {
        int line = ls.linenumber;
        ls.next();
        newLabelEntry(true, breakName, line, Codegen.jump(fs));
    }

    /** {@code checkrepeated} */
    private void checkRepeated(LuaString name) {
        DynData.Labeldesc lb = findLabel(name);
        if (lb != null) {
            String msg = String.format(Locale.ROOT, "label '%s' already defined on line %d",
                    name.toJavaString(), lb.line);
            throw Codegen.semError(fs, msg);
        }
    }

    /** {@code labelstat} */
    private void labelStat(LuaString name, int line) {
        checkNext(Lexer.TK_DBCOLON);
        while (ls.t.token == ';' || ls.t.token == Lexer.TK_DBCOLON) {
            statement(); // 跳过其它空语句
        }
        checkRepeated(name);
        createLabel(name, line, blockFollow(false));
    }

    /** {@code whilestat} */
    private void whileStat(int line) {
        FuncState fs = this.fs;
        int whileinit;
        int condexit;
        BlockCnt bl = new BlockCnt();
        ls.next(); // 跳过 WHILE
        whileinit = Codegen.getLabel(fs);
        condexit = cond();
        enterBlock(fs, bl, true);
        checkNext(Lexer.TK_DO);
        block();
        Codegen.jumpTo(fs, whileinit);
        checkMatch(Lexer.TK_END, Lexer.TK_WHILE, line);
        leaveBlock(fs);
        Codegen.patchToHere(fs, condexit);
    }

    /** {@code repeatstat} */
    private void repeatStat(int line) {
        int condexit;
        FuncState fs = this.fs;
        int repeatInit = Codegen.getLabel(fs);
        BlockCnt bl1 = new BlockCnt();
        BlockCnt bl2 = new BlockCnt();
        enterBlock(fs, bl1, true); // 循环块
        enterBlock(fs, bl2, false); // 作用域块
        ls.next(); // 跳过 REPEAT
        statList();
        checkMatch(Lexer.TK_UNTIL, Lexer.TK_REPEAT, line);
        condexit = cond(); // 条件在作用域块内读
        leaveBlock(fs); // 结束作用域
        if (bl2.upval) {
            int exit = Codegen.jump(fs); // 正常出口要跳过 fix
            Codegen.patchToHere(fs, condexit);
            Codegen.codeABC(fs, OpCode.OP_CLOSE, FuncState.reglevel(fs, bl2.nactvar), 0, 0);
            condexit = Codegen.jump(fs);
            Codegen.patchToHere(fs, exit);
        }
        Codegen.patchList(fs, condexit, repeatInit);
        leaveBlock(fs);
    }

    /** {@code exp1} */
    private void exp1() {
        ExpDesc e = new ExpDesc();
        expr(e);
        Codegen.exp2NextReg(fs, e);
    }

    /** {@code fixforjump} */
    private void fixForJump(int pc, int dest, boolean back) {
        int offset = dest - (pc + 1);
        if (back) {
            offset = -offset;
        }
        if (offset > OpCode.MAXARG_BX) {
            throw ls.syntaxError("control structure too long");
        }
        fs.f.code[pc] = OpCode.setArgBx(fs.f.code[pc], offset);
    }

    /** {@code forbody} */
    private void forBody(int base, int line, int nvars, int isgen) {
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep;
        int endfor;
        checkNext(Lexer.TK_DO);
        prep = Codegen.codeABx(fs, (isgen == 1) ? OpCode.OP_TFORPREP : OpCode.OP_FORPREP, base, 0);
        enterBlock(fs, bl, false);
        adjustLocalVars(nvars);
        Codegen.reserveRegs(fs, nvars);
        block();
        leaveBlock(fs);
        fixForJump(prep, Codegen.getLabel(fs), false);
        if (isgen == 1) {
            Codegen.codeABC(fs, OpCode.OP_TFORCALL, base, 0, nvars);
            Codegen.fixLine(fs, line);
        }
        endfor = Codegen.codeABx(fs, (isgen == 1) ? OpCode.OP_TFORLOOP : OpCode.OP_FORLOOP, base, 0);
        fixForJump(endfor, prep + 1, true);
        Codegen.fixLine(fs, line);
    }

    /** {@code fornum} */
    private void forNum(LuaString varname, int line) {
        FuncState fs = this.fs;
        int base = fs.freereg;
        newLocalVarLiteral("(for state)");
        newLocalVarLiteral("(for state)");
        newLocalVarLiteral("(for state)");
        newLocalVar(varname);
        checkNext('=');
        exp1(); // 初值
        checkNext(',');
        exp1(); // 上限
        if (testNext(',')) {
            exp1(); // 可选步长
        } else {
            Codegen.codeInt(fs, fs.freereg, 1);
            Codegen.reserveRegs(fs, 1);
        }
        adjustLocalVars(3); // 控制变量
        forBody(base, line, 1, 0);
    }

    /** {@code forlist} */
    private void forList(LuaString indexname) {
        FuncState fs = this.fs;
        ExpDesc e = new ExpDesc();
        int nvars = 5; // gen, state, control, toclose, indexname
        int line;
        int base = fs.freereg;
        newLocalVarLiteral("(for state)");
        newLocalVarLiteral("(for state)");
        newLocalVarLiteral("(for state)");
        newLocalVarLiteral("(for state)");
        newLocalVar(indexname);
        while (testNext(',')) {
            newLocalVar(strCheckName());
            nvars++;
        }
        checkNext(Lexer.TK_IN);
        line = ls.linenumber;
        adjustAssign(4, expList(e), e);
        adjustLocalVars(4); // 控制变量
        markToBeClosed(fs); // 最后一个控制变量必须被关闭
        Codegen.checkStack(fs, 3); // 调用迭代器要额外 3 个寄存器
        forBody(base, line, nvars - 4, 1);
    }

    /** {@code forstat} */
    private void forStat(int line) {
        LuaString varname;
        BlockCnt bl = new BlockCnt();
        enterBlock(fs, bl, true);
        ls.next(); // 跳过 'for'
        varname = strCheckName();
        switch (ls.t.token) {
        case '=':
            forNum(varname, line);
            break;
        case ',':
        case Lexer.TK_IN:
            forList(varname);
            break;
        default:
            throw ls.syntaxError("'=' or 'in' expected");
        }
        checkMatch(Lexer.TK_END, Lexer.TK_FOR, line);
        leaveBlock(fs);
    }

    /** {@code test_then_block} */
    private void testThenBlock(int[] escapelist) {
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        ExpDesc v = new ExpDesc();
        int jf;
        ls.next(); // 跳过 IF / ELSEIF
        expr(v);
        checkNext(Lexer.TK_THEN);
        if (ls.t.token == Lexer.TK_BREAK) { // 'if x then break' ?
            int line = ls.linenumber;
            Codegen.goIfFalse(fs, v);
            ls.next();
            enterBlock(fs, bl, false);
            newLabelEntry(true, breakName, line, v.t);
            while (testNext(';')) {
                // 跳过分号
            }
            if (blockFollow(false)) { // 整个块就是这条跳转
                leaveBlock(fs);
                return;
            }
            jf = Codegen.jump(fs); // 条件为假时要跳过 then 部分
        } else {
            Codegen.goIfTrue(fs, v);
            enterBlock(fs, bl, false);
            jf = v.f;
        }
        statList();
        leaveBlock(fs);
        if (ls.t.token == Lexer.TK_ELSE || ls.t.token == Lexer.TK_ELSEIF) {
            Codegen.concat(fs, escapelist, Codegen.jump(fs));
        }
        Codegen.patchToHere(fs, jf);
    }

    /** {@code ifstat} */
    private void ifStat(int line) {
        FuncState fs = this.fs;
        int[] escapelist = new int[] { Codegen.NO_JUMP };
        testThenBlock(escapelist);
        while (ls.t.token == Lexer.TK_ELSEIF) {
            testThenBlock(escapelist);
        }
        if (testNext(Lexer.TK_ELSE)) {
            block();
        }
        checkMatch(Lexer.TK_END, Lexer.TK_IF, line);
        Codegen.patchToHere(fs, escapelist[0]);
    }

    /** {@code localfunc} */
    private void localFunc() {
        ExpDesc b = new ExpDesc();
        FuncState fs = this.fs;
        int fvar = fs.nactvar;
        newLocalVar(strCheckName());
        adjustLocalVars(1);
        body(b, false, ls.linenumber);
        // 调试信息只从这一点开始才看得到这个变量
        Proto.LocVar lv = localDebugInfo(fs, fvar);
        if (lv != null) {
            lv.startpc = fs.pc;
        }
    }

    /** {@code getlocalattribute} */
    private int getLocalAttribute() {
        if (testNext('<')) {
            LuaString attr = strCheckName();
            checkNext('>');
            String a = attr.toJavaString();
            if ("const".equals(a)) {
                return DynData.RDKCONST;
            }
            if ("close".equals(a)) {
                return DynData.RDKTOCLOSE;
            }
            throw Codegen.semError(fs,
                    String.format(Locale.ROOT, "unknown attribute '%s'", a));
        }
        return DynData.VDKREG;
    }

    /** {@code checktoclose} */
    private void checkToClose(int level) {
        if (level != -1) {
            FuncState fs = this.fs;
            markToBeClosed(fs);
            Codegen.codeABC(fs, OpCode.OP_TBC, FuncState.reglevel(fs, level), 0, 0);
        }
    }

    /** {@code localstat} */
    private void localStat() {
        FuncState fs = this.fs;
        int toclose = -1;
        int vidx = 0;
        int nvars = 0;
        int nexps;
        ExpDesc e = new ExpDesc();
        do {
            vidx = newLocalVar(strCheckName());
            int kind = getLocalAttribute();
            getLocalVarDesc(fs, vidx).kind = kind;
            if (kind == DynData.RDKTOCLOSE) {
                if (toclose != -1) {
                    throw Codegen.semError(fs, "multiple to-be-closed variables in local list");
                }
                toclose = fs.nactvar + nvars;
            }
            nvars++;
        } while (testNext(','));
        if (testNext('=')) {
            nexps = expList(e);
        } else {
            e.k = ExpDesc.VVOID;
            nexps = 0;
        }
        DynData.Vardesc var = getLocalVarDesc(fs, vidx);
        LuaValue cv = new LuaValue();
        if (nvars == nexps && var.kind == DynData.RDKCONST && Codegen.exp2Const(fs, e, cv)) {
            var.kind = DynData.RDKCTC; // 编译期常量：不占寄存器
            var.k = new LuaValue(cv);
            adjustLocalVars(nvars - 1);
            fs.nactvar++;
        } else {
            adjustAssign(nvars, nexps, e);
            adjustLocalVars(nvars);
        }
        checkToClose(toclose);
    }

    /** {@code funcname} */
    private boolean funcName(ExpDesc v) {
        boolean ismethod = false;
        singlevar(v);
        while (ls.t.token == '.') {
            fieldSel(v);
        }
        if (ls.t.token == ':') {
            ismethod = true;
            fieldSel(v);
        }
        return ismethod;
    }

    /** {@code funcstat} */
    private void funcStat(int line) {
        boolean ismethod;
        ExpDesc v = new ExpDesc();
        ExpDesc b = new ExpDesc();
        ls.next(); // 跳过 FUNCTION
        ismethod = funcName(v);
        body(b, ismethod, line);
        checkReadonly(v);
        Codegen.storeVar(fs, v, b);
        Codegen.fixLine(fs, line); // 定义「发生」在第一行
    }

    /** {@code exprstat} */
    private void exprStat() {
        FuncState fs = this.fs;
        LhsAssign v = new LhsAssign();
        suffixedExp(v.v);
        if (ls.t.token == '=' || ls.t.token == ',') {
            v.prev = null;
            restAssign(v, 1);
        } else {
            if (v.v.k != ExpDesc.VCALL) {
                throw ls.syntaxError("syntax error");
            }
            // 作为语句的调用不需要结果
            fs.f.code[v.v.info] = OpCode.setArgC(fs.f.code[v.v.info], 1);
        }
    }

    /** {@code retstat} */
    private void retStat() {
        FuncState fs = this.fs;
        ExpDesc e = new ExpDesc();
        int nret;
        int first = fs.nvarstack();
        if (blockFollow(true) || ls.t.token == ';') {
            nret = 0;
        } else {
            nret = expList(e);
            if (hasMultRet(e.k)) {
                Codegen.setMultiRet(fs, e);
                if (e.k == ExpDesc.VCALL && nret == 1 && !fs.bl.insidetbc) {
                    // 尾调用
                    fs.f.code[e.info] = OpCode.setOpCode(fs.f.code[e.info], OpCode.OP_TAILCALL);
                }
                nret = Codegen.MULTRET;
            } else {
                if (nret == 1) {
                    first = Codegen.exp2AnyReg(fs, e);
                } else {
                    Codegen.exp2NextReg(fs, e);
                }
            }
        }
        Codegen.ret(fs, first, nret);
        testNext(';');
    }

    /** {@code statement} */
    private void statement() {
        int line = ls.linenumber;
        enterLevel();
        switch (ls.t.token) {
        case ';':
            ls.next();
            break;
        case Lexer.TK_IF:
            ifStat(line);
            break;
        case Lexer.TK_WHILE:
            whileStat(line);
            break;
        case Lexer.TK_DO:
            ls.next();
            block();
            checkMatch(Lexer.TK_END, Lexer.TK_DO, line);
            break;
        case Lexer.TK_FOR:
            forStat(line);
            break;
        case Lexer.TK_REPEAT:
            repeatStat(line);
            break;
        case Lexer.TK_FUNCTION:
            funcStat(line);
            break;
        case Lexer.TK_LOCAL:
            ls.next();
            if (testNext(Lexer.TK_FUNCTION)) {
                localFunc();
            } else {
                localStat();
            }
            break;
        case Lexer.TK_DBCOLON:
            ls.next();
            labelStat(strCheckName(), line);
            break;
        case Lexer.TK_RETURN:
            ls.next();
            retStat();
            break;
        case Lexer.TK_BREAK:
            breakStat();
            break;
        case Lexer.TK_GOTO:
            ls.next();
            gotoStat();
            break;
        default:
            exprStat();
            break;
        }
        fs.freereg = fs.nvarstack(); // 释放临时寄存器
        leaveLevel();
    }

    /* ---------- 入口 ---------- */

    /** {@code mainfunc} */
    private void mainFunc(FuncState funcstate) {
        BlockCnt bl = new BlockCnt();
        openFunc(funcstate, bl);
        setVararg(funcstate, 0); // 主函数总是 vararg
        Proto.Upvaldesc env = allocUpvalue(funcstate);
        env.instack = true;
        env.idx = 0;
        env.kind = DynData.VDKREG;
        env.name = envn;
        ls.next(); // 读第一个 token
        statList();
        check(Lexer.TK_EOS);
        closeFunc();
    }
}
