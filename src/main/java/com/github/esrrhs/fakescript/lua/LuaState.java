package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

/**
 * Lua 状态 —— 对应 {@code lstate.h} 的 {@code lua_State} 与 {@code ldo.c} 的
 * 调用机制。
 * <p>
 * <b>核心不变式：栈槽对象一旦创建就不再替换。</b>C 版开放 upvalue 保存的是栈槽的
 * 地址；这里保存的是槽对象的引用。两者等价的前提是栈扩容只复制数组、不动槽对象。
 * 因此全类内部一律 {@code stack[i].set(v)}，从不写 {@code stack[i] = v}，
 * 新槽只在 {@link #ensureSlots(int)} 里 {@code new} 出来。
 * <p>
 * 几处与 C 版的差异（都是刻意的）：
 * <ul>
 *   <li>指针换成<b>栈下标</b>。{@code StkId} 在 C 里能加减，Java 里只能这样；</li>
 *   <li>没有 {@code EXTRA_STACK} 保证金：{@code luaD_call} 前需要栈空间的地方
 *       显式 {@code ensureSlots}；</li>
 *   <li>{@code luaD_throw} 换成抛 {@link LuaError}，{@code pcall} 用
 *       {@code try/catch} 实现；</li>
 *   <li>没有 hook、没有 yield，因此 {@code luaV_finishOp}、{@code trap} 那套
 *       逻辑整体不存在。</li>
 * </ul>
 */
public final class LuaState {

    /** {@code LUA_MULTRET}：调用方要全部结果 */
    public static final int MULTRET = -1;

    /** {@code LUA_MINSTACK}：C 函数帧保证的可用槽数 */
    public static final int MINSTACK = 20;

    /** {@code LUAI_MAXCCALLS}：C 调用深度上限，超过报「C stack overflow」 */
    public static final int MAXCCALLS = 200;

    /** 初始栈容量 */
    private static final int BASIC_STACK_SIZE = 2 * MINSTACK;

    /* ==================== 栈 ==================== */

    LuaValue[] stack = new LuaValue[BASIC_STACK_SIZE];
    int top;

    /* ==================== 调用 ==================== */

    CallInfo ci;
    private final CallInfo baseCi = new CallInfo();
    int nCcalls;

    /* ==================== 全局 ==================== */

    public final StringPool strings = new StringPool();
    public LuaTable globals = new LuaTable();
    public final LuaTable registry = new LuaTable();

    /**
     * 每个基础类型的默认元表，对应 {@code global_State.mt[]}。
     * <p>
     * 只有 {@code debug.setmetatable(nil, mt)} 这类「给整个类型设元表」的调用
     * 才会用到；表与 userdata 的元表存在对象自身。
     */
    private final LuaTable[] typeMt = new LuaTable[Lua.TTHREAD + 1];

    /** 元方法名常量表，对应 {@code global_State.tmname[]} */
    final LuaString[] tmName = new LuaString[Tm.TM_N];

    /** 开放 upvalue 列表，按 {@link UpVal#stackIdx} 升序 */
    private final List<UpVal> openUpvals = new ArrayList<>();

    /** 当前 chunk 的名字，仅用于错误信息 */
    private String chunkName = "?";

    /**
     * 标准输出。{@code print} 写这里，测试把它换成内存缓冲区即可做差分比对。
     */
    public java.io.PrintStream out = System.out;

    public LuaState() {
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new LuaValue();
        }
        baseCi.func = 0;
        baseCi.top = MINSTACK;
        baseCi.nresults = 0;
        baseCi.isLua = false;
        ci = baseCi;
        for (int i = 0; i < Tm.TM_N; i++) {
            tmName[i] = LuaString.latin1(Tm.EVENT_NAMES[i]);
        }
        registry.set(2, LuaValue.ofTable(globals)); // LUA_RIDX_GLOBALS
        globals.set("_G", LuaValue.ofTable(globals));
    }

    public LuaTable getGlobals() {
        return globals;
    }

    /* ==================== 栈操作 ==================== */

    /**
     * 保证下标 {@code 0 .. n-1} 的槽都存在。
     * <p>
     * 只增长、只追加新槽，绝不替换已有槽对象（见类注释的不变式）。
     */
    void ensureSlots(int n) {
        if (n <= stack.length) {
            return;
        }
        int cap = stack.length;
        while (cap < n) {
            cap *= 2;
        }
        LuaValue[] bigger = new LuaValue[cap];
        System.arraycopy(stack, 0, bigger, 0, stack.length);
        for (int i = stack.length; i < cap; i++) {
            bigger[i] = new LuaValue();
        }
        stack = bigger;
    }

    /** {@code luaD_checkstack}：保证从 {@code top} 起还有 {@code n} 个槽 */
    void checkStack(int n) {
        ensureSlots(top + n);
    }

    /** 取栈槽。等价于 C 的 {@code s2v(L->top.p + i)} */
    public LuaValue at(int idx) {
        return stack[idx];
    }

    /** 压入一个值（拷贝，不是共享槽） */
    public void push(LuaValue v) {
        ensureSlots(top + 1);
        stack[top++].set(v);
    }

    public void pushNil() {
        ensureSlots(top + 1);
        stack[top++].setNil();
    }

    void pushFunction(LuaFunction f) {
        ensureSlots(top + 1);
        stack[top++].setObject(Lua.VLCF, f);
    }

    void pushClosure(LClosure c) {
        ensureSlots(top + 1);
        stack[top++].setObject(Lua.VLIF, c);
    }

    /** 当前帧的参数个数 */
    public int argCount() {
        return top - ci.func - 1;
    }

    /** 第 i 个参数（0 起） */
    public LuaValue arg(int i) {
        return stack[ci.func + 1 + i];
    }

    /* ==================== upvalue ==================== */

    /**
     * {@code luaF_findupval}：找到指向 {@code stackIdx} 的开放 upvalue，没有就建一个。
     * <p>
     * 列表按 {@code stackIdx} 升序，所以从后往前扫能尽早命中「最近创建」的那批。
     */
    UpVal findUpval(int stackIdx) {
        for (int i = openUpvals.size() - 1; i >= 0; i--) {
            UpVal uv = openUpvals.get(i);
            if (uv.stackIdx == stackIdx) {
                return uv;
            }
            if (uv.stackIdx < stackIdx) {
                break;
            }
        }
        UpVal uv = UpVal.open(stack[stackIdx], stackIdx);
        int pos = 0;
        while (pos < openUpvals.size() && openUpvals.get(pos).stackIdx < stackIdx) {
            pos++;
        }
        openUpvals.add(pos, uv);
        return uv;
    }

    /**
     * {@code luaF_newtbcupval}：为 {@code <close>} 变量新建一个 to-be-closed 的
     * 开放 upvalue，并<b>当场</b>检查它真的有 {@code __close}。
     * <p>
     * 两个容易漏掉的细节，都是实测出来的：
     * <ul>
     *   <li>{@code nil} 与 {@code false} 直接跳过，不需要 {@code __close}
     *       （C 版是 {@code if (l_isfalse(s2v(level))) return;}）。所以
     *       {@code local x <close> = nil} 完全合法。</li>
     *   <li>检查在<b>赋值的当下</b>做，不在作用域结束时。所以
     *       {@code local x <close> = 5 print(x)} 在 {@code OP_TBC} 上就抛错，
     *       {@code print} 根本不会执行——把它挪到关闭时检查，输出会先多出一行。</li>
     * </ul>
     * <p>
     * 与 {@link #findUpval} 不同，这里<b>不去重</b>：C 版也是每次新建一个，
     * 因为 tbc 变量是「槽」而不是「变量绑定」，同一个槽可以被标记多次。
     */
    UpVal newTbcUpval(int stackIdx) {
        LuaValue v = stack[stackIdx];
        if (v.isNil() || (v.tt == Lua.TBOOLEAN && !v.toBoolean())) {
            // false / nil 不需要关闭，也不建 upvalue（C 版直接 return）
            return null;
        }
        LuaValue tm = LuaTm.getTmByObj(this, v, Tm.TM_CLOSE);
        if (tm == null || tm.isNil()) {
            LuaString vn = localNameAt(stackIdx - ci.func);
            throw runError("variable '" + ((vn == null) ? "?" : vn.toJavaString())
                    + "' got a non-closable value");
        }
        UpVal uv = UpVal.open(stack[stackIdx], stackIdx);
        uv.toBeClosed = true;
        int pos = 0;
        while (pos < openUpvals.size() && openUpvals.get(pos).stackIdx < stackIdx) {
            pos++;
        }
        openUpvals.add(pos, uv);
        return uv;
    }

    /**
     * {@code luaF_close}：关闭 {@code stackIdx >= level} 的全部开放 upvalue。
     * <p>
     * 从栈顶往下关（与 C 版一致），因为 {@code __close} 元方法可能改动栈、
     * 也可能再建 upvalue。tbc 变量在关闭前先跑 {@code __close}。
     *
     * @param errObj 传给 {@code __close} 的错误对象；{@code null} 表示正常关闭
     */
    void closeUpvals(int level, LuaValue errObj) {
        while (!openUpvals.isEmpty()) {
            UpVal uv = openUpvals.get(openUpvals.size() - 1);
            if (uv.stackIdx < level) {
                break;
            }
            openUpvals.remove(openUpvals.size() - 1);
            LuaValue obj = uv.value();
            boolean isTbc = uv.toBeClosed;
            int varIdx = uv.stackIdx - ci.func; // luaG_findlocal 的序号，先取再关
            uv.close();
            if (isTbc && !(obj.isNil())) {
                LuaValue errSlot = (errObj == null) ? LuaValue.ofNil() : errObj;
                LuaTm.callCloseMethod(this, obj, errSlot, varIdx);
            }
        }
    }

    /* ==================== 类型元表 ==================== */

    LuaTable typeMetatable(int baseType) {
        return typeMt[baseType];
    }

    /** {@code lapi.c: lua_setmetatable} 里「给整个类型设元表」的分支 */
    public void setTypeMetatable(int baseType, LuaTable mt) {
        typeMt[baseType] = mt;
    }

    /* ==================== 调用 ==================== */

    private CallInfo prepCallInfo(int funcIdx, int nresults, boolean isLua, int topIdx) {
        CallInfo c = new CallInfo();
        c.func = funcIdx;
        c.nresults = nresults;
        c.isLua = isLua;
        c.top = topIdx;
        c.previous = ci;
        ci = c;
        return c;
    }

    /** {@code ldo.c: precallC} —— C 函数在这里就被真正调用掉 */
    private int precallC(int funcIdx, int nresults, LuaFunction f) {
        checkStack(MINSTACK);
        CallInfo c = prepCallInfo(funcIdx, nresults, false, top + MINSTACK);
        int n = f.call(this);
        poscall(c, n);
        return n;
    }

    /**
     * {@code luaD_precall}：准备一次调用。
     *
     * @return Lua 函数返回新帧（交由 {@link Vm} 执行）；C 函数返回 {@code null}
     *         （函数已经跑完，结果在 {@code funcIdx} 处）
     */
    CallInfo precall(int funcIdx, int nresults) {
        while (true) {
            LuaValue f = stack[funcIdx];
            if (f.tt == Lua.VLIF) {
                LClosure cl = (LClosure) f.o;
                Proto p = cl.p;
                int narg = top - funcIdx - 1;
                int nfixparams = p.numparams;
                int fsize = p.maxstacksize;
                checkStack(fsize);
                CallInfo c = prepCallInfo(funcIdx, nresults, true, funcIdx + 1 + fsize);
                c.savedpc = 0;
                for (; narg < nfixparams; narg++) {
                    stack[top++].setNil();
                }
                return c;
            } else if (f.tt == Lua.VLCF) {
                precallC(funcIdx, nresults, (LuaFunction) f.o);
                return null;
            } else {
                funcIdx = tryFuncTM(funcIdx);
            }
        }
    }

    /**
     * {@code luaD_pretailcall}：为尾调用准备帧。
     * <p>
     * Lua 函数<b>复用调用方的 CallInfo</b>（这正是尾调用不涨栈的原因），
     * C 函数则照常新建一帧跑完后销毁。
     *
     * @return Lua 函数返回 {@code -1}（帧已就位，交给 {@link Vm} 跑）；
     *         C 函数返回结果个数
     */
    int pretailcall(CallInfo c, int funcIdx, int narg1, int delta) {
        while (true) {
            LuaValue f = stack[funcIdx];
            if (f.tt == Lua.VLIF) {
                LClosure cl = (LClosure) f.o;
                Proto p = cl.p;
                int fsize = p.maxstacksize;
                int nfixparams = p.numparams;
                checkStack(Math.max(fsize - delta, 0));
                c.func -= delta;
                for (int i = 0; i < narg1; i++) {
                    stack[c.func + i].set(stack[funcIdx + i]);
                }
                funcIdx = c.func; // 函数与参数已下移
                for (; narg1 <= nfixparams; narg1++) {
                    stack[funcIdx + narg1].setNil();
                }
                c.top = funcIdx + 1 + fsize;
                c.savedpc = 0;
                c.tail = true;
                top = funcIdx + narg1;
                return -1;
            } else if (f.tt == Lua.VLCF) {
                return precallC(funcIdx, MULTRET, (LuaFunction) f.o);
            } else {
                funcIdx = tryFuncTM(funcIdx);
                narg1++; // 元方法本身也是参数
            }
        }
    }

    /** {@code tryfuncTM}：不可调用的值，找 {@code __call} 元方法顶上 */
    private int tryFuncTM(int funcIdx) {
        LuaValue tm = LuaTm.getTmByObj(this, stack[funcIdx], Tm.TM_CALL);
        if (tm == null || tm.isNil()) {
            throw callError(stack[funcIdx]);
        }
        // 把元方法插到 func 位置，其余整体右移一格
        checkStack(1);
        for (int p = top; p > funcIdx; p--) {
            stack[p].set(stack[p - 1]);
        }
        top++;
        stack[funcIdx].set(tm);
        return funcIdx;
    }

    /** {@code ldo.c: moveresults} */
    private void moveResults(int res, int nres, int wanted) {
        if (wanted == 0) {
            top = res;
            return;
        }
        if (wanted == 1) {
            if (nres == 0) {
                stack[res].setNil();
            } else {
                stack[res].set(stack[top - nres]);
            }
            top = res + 1;
            return;
        }
        if (wanted == MULTRET) {
            wanted = nres;
        }
        int firstresult = top - nres;
        if (nres > wanted) {
            nres = wanted;
        }
        for (int i = 0; i < nres; i++) {
            stack[res + i].set(stack[firstresult + i]);
        }
        for (int i = nres; i < wanted; i++) {
            stack[res + i].setNil();
        }
        top = res + wanted;
    }

    /** {@code luaD_poscall} */
    void poscall(CallInfo c, int nres) {
        int wanted = c.nresults;
        moveResults(c.func, nres, wanted);
        ci = c.previous;
    }

    /** {@code luaD_call}：从宿主调进 Lua */
    public void call(int funcIdx, int nresults) {
        nCcalls++;
        if (nCcalls >= MAXCCALLS) {
            nCcalls--;
            throw runError("C stack overflow");
        }
        try {
            CallInfo c = precall(funcIdx, nresults);
            if (c != null) {
                c.fresh = true;
                Vm.execute(this, c);
            }
        } finally {
            nCcalls--;
        }
    }

    /**
     * {@code lua_pcall}：带保护地调用。
     * <p>
     * 只捕获 {@link LuaError}。实现自身的 Java 异常必须继续上冒，否则缺陷会被
     * 引擎吞成「脚本报错」。
     *
     * @return 出错时返回错误对象，否则 {@code null}
     */
    public LuaValue pcall(int funcIdx, int nresults) {
        return pcall(funcIdx, nresults, null);
    }

    /**
     * {@code lua_pcall} 带消息处理函数的形态（{@code xpcall} 用它）。
     * <p>
     * 处理函数在<b>调用链已经恢复之后</b>才跑：C 版也是先 {@code luaD_seterrorobj}
     * 把错误对象放到恢复后的栈顶，再 {@code luaD_callnoyield} 调 {@code msgh}，
     * 返回值替换掉原来的错误对象。顺序反过来会让处理函数看到脏帧。
     *
     * @param handler 消息处理函数；{@code null} 表示没有（与 {@code msgh = 0} 等价）
     * @return 出错时返回（可能被处理函数改写过的）错误对象，否则 {@code null}
     */
    public LuaValue pcall(int funcIdx, int nresults, LuaValue handler) {
        int oldTop = top;
        CallInfo saved = ci;
        try {
            call(funcIdx, nresults);
            return null;
        } catch (LuaError e) {
            /*
             * 位置信息在这里补，而不是在抛出的地方补：像「除以零」这类错误
             * 来自 Arith 这种没有 LuaState 的纯算术层，它抛的是裸错误值，
             * 只有走到保护边界才知道当前行号。这正是 C 版 msghandler 里
             * luaL_traceback 干的活。
             */
            LuaValue v = e.value;
            if (e.where == null && v != null && v.isString()) {
                String prefix = where(); // 必须用出错时的帧，先于恢复调用链
                v = LuaValue.ofString(
                        LuaString.latin1(prefix + v.asString().toJavaString()));
            }
            // 必须先于恢复调用链：__close 要拿到错误对象；返回值可能已被 __close 换掉
            v = closeUpvalsOnUnwind(saved, v);
            ci = saved;
            top = oldTop;
            if (handler == null) {
                return v;
            }
            int hf = top;
            checkStack(2);
            stack[hf].set(handler);
            stack[hf + 1].set(v);
            top = hf + 2;
            LuaValue bad = pcall(hf, 1); // 处理函数自身出错不再兜底
            if (bad != null) {
                top = oldTop;
                return bad;
            }
            LuaValue res = new LuaValue(stack[top - 1]);
            top = oldTop;
            return res;
        }
    }

    /**
     * 出错展开时关掉被丢弃帧上的 upvalue，带错误对象跑 {@code __close}。
     * <p>
     * 对应 {@code ldo.c: luaD_closeprotected}。正常返回时 {@code OP_CLOSE} 与
     * {@code OP_RETURN} 自己会关，<b>出错路径没有人关</b>——不在这里补，
     * {@code local f <close> = ... error('x')} 里的 {@code __close} 就永远不跑。
     * <p>
     * <b>{@code __close} 自己又出错时，新错误顶掉原错误</b>（实测：
     * {@code error('orig')} 之后 {@code __close} 里 {@code error('in close')}，
     * {@code pcall} 拿到的是 {@code in close}）。所以这里返回的是<b>可能已被换掉</b>
     * 的错误对象，调用方必须拿返回值覆盖原来的。
     * <p>
     * 另一个细节是循环：C 版用 {@code luaD_rawrunprotected} 一次只关一批，
     * 出错就记下新错误再接着关剩下的，直到关完。这里同样循环到没有可关的为止，
     * 但加了「一轮下来一个都没关掉就停」的兜底，避免某个 {@code __close}
     * 一进入就抛错时转不出来。
     *
     * @return 最终的错误对象（可能与传入的 {@code errObj} 不同）
     */
    private LuaValue closeUpvalsOnUnwind(CallInfo target, LuaValue errObj) {
        if (target == null || openUpvals.isEmpty()) {
            return errObj;
        }
        int level = target.func + 1;
        CallInfo saved = ci;
        while (true) {
            int before = openUpvals.size();
            try {
                closeUpvals(level, errObj);
                return errObj;
            } catch (LuaError e2) {
                LuaValue v = e2.value;
                if (e2.where == null && v != null && v.isString()) {
                    String prefix = where(); // 出错那刻的帧，先于恢复
                    v = LuaValue.ofString(
                            LuaString.latin1(prefix + v.asString().toJavaString()));
                }
                ci = saved;
                errObj = v;
                if (openUpvals.size() >= before) {
                    return errObj; // 一个也没关掉，再试也不会有进展
                }
            }
        }
    }

    /** 恢复调用链到初始状态。宿主在捕获错误后必须调用，否则下一次调用从脏帧开始 */
    public void reset() {
        ci = baseCi;
        top = 0;
        openUpvals.clear();
    }

    /* ==================== 加载与执行 ==================== */

    /**
     * 编译一段源码，返回主 chunk 的闭包。
     * <p>
     * 对应 {@code lapi.c: lua_load}：闭包的第 0 号 upvalue 是关闭的，里面放全局表。
     */
    public LClosure load(byte[] source, String chunkName) {
        this.chunkName = chunkName;
        Proto p = Parser.parse(source, LuaString.latin1(chunkName).bytes, strings);
        LClosure cl = new LClosure(p, 1);
        cl.initUpvals();
        cl.upvals[0] = UpVal.closed(LuaValue.ofTable(globals));
        return cl;
    }

    /**
     * {@code luaL_loadfilex}：编译一个脚本文件。
     * <p>
     * 与 {@link #load} 的差别是会跳过 BOM 与 {@code #!} 行（那属于加载层，
     * 不属于词法层）。行号必须靠补一个 {@code \n} 保住。
     */
    public LClosure loadFile(byte[] source, String chunkName) {
        this.chunkName = chunkName;
        Proto p = Parser.parseFile(source, LuaString.latin1(chunkName).bytes, strings);
        LClosure cl = new LClosure(p, 1);
        cl.initUpvals();
        cl.upvals[0] = UpVal.closed(LuaValue.ofTable(globals));
        return cl;
    }

    /** 编译并执行一段源码 */
    public void run(String source) {
        run(LuaString.latin1(source).bytes, "=chunk");
    }

    /** 编译并执行，等价于 {@code luaL_dostring} */
    public void run(byte[] source, String chunkName) {
        int base = top;
        pushClosure(load(source, chunkName));
        call(base, 0);
    }

    /** 带保护地执行；返回错误对象或 {@code null} */
    public LuaValue runProtected(byte[] source, String chunkName) {
        int base = top;
        pushClosure(load(source, chunkName));
        return pcall(base, 0);
    }

    /* ==================== 错误 ==================== */

    /**
     * {@code luaG_runerror}。
     * <p>
     * 位置前缀直接<b>拼进错误值</b>，不是另存一份元数据——C 版就是这样：
     * {@code luaG_addinfo} 改写的是栈顶那个消息串。{@link LuaError#where}
     * 只当「已经加过位置」的标记用，避免 {@link #pcall} 再加一次。
     */
    public LuaError runError(String msg) {
        String prefix = where();
        return new LuaError(LuaValue.ofString(LuaString.latin1(prefix + msg)), prefix);
    }

    /** {@code luaG_typeerror}（含 {@code varinfo} 的变量名后缀） */
    public LuaError typeError(LuaValue v, String op) {
        return runError("attempt to " + op + " a " + v.typeName() + " value" + varInfo(v));
    }

    /**
     * {@code luaG_callerror}：名字先从「怎么调的」找，找不到才退回 {@link #varInfo}。
     * <p>
     * 两条路都试是必要的：{@code t:nope()} 走 {@code funcnamefromcall}
     * （{@code OP_CALL} → {@code OP_SELF} → "method 'nope'"），而
     * {@code local f = nil; f()} 这类只能靠 {@code varinfo} 认出局部变量。
     * 只用其中一条就会在另一类场景丢掉 {@code (method 'xxx')} 后缀。
     */
    public LuaError callError(LuaValue v) {
        String[] name = new String[1];
        String kind = funcNameFromCall(name);
        String extra = (kind != null) ? formatVarInfo(kind, name[0]) : varInfo(v);
        return runError("attempt to call a " + v.typeName() + " value" + extra);
    }

    /** {@code ldebug.c: funcnamefromcall} */
    private String funcNameFromCall(String[] name) {
        CallInfo c = ci;
        if (c == null || !c.isLua || c.func >= stack.length) {
            return null;
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return funcNameFromCode(((LClosure) f.o).p,
                (c.savedpc > 0) ? c.savedpc - 1 : 0, name);
    }

    /**
     * {@code ldebug.c: funcnamefromcode}：按<b>调用点那条指令</b>反推它在调什么。
     * <p>
     * 元方法那一批共用一个名字来源：{@code tmname[tm]} 去掉开头的 {@code __}
     * （C 版就是 {@code getshrstr(tmname[tm]) + 2}）。
     */
    private static String funcNameFromCode(Proto p, int pc, String[] name) {
        if (pc < 0 || pc >= p.code.length) {
            return null;
        }
        int i = p.code[pc];
        int tm = -1;
        switch (OpCode.getOpCode(i)) {
        case OpCode.OP_CALL:
        case OpCode.OP_TAILCALL:
            return getObjName(p, pc, OpCode.getArgA(i), name);
        case OpCode.OP_TFORCALL:
            name[0] = "for iterator";
            return "for iterator";
        case OpCode.OP_SELF:
        case OpCode.OP_GETTABUP:
        case OpCode.OP_GETTABLE:
        case OpCode.OP_GETI:
        case OpCode.OP_GETFIELD:
            tm = Tm.TM_INDEX;
            break;
        case OpCode.OP_SETTABUP:
        case OpCode.OP_SETTABLE:
        case OpCode.OP_SETI:
        case OpCode.OP_SETFIELD:
            tm = Tm.TM_NEWINDEX;
            break;
        case OpCode.OP_MMBIN:
        case OpCode.OP_MMBINI:
        case OpCode.OP_MMBINK:
            tm = OpCode.getArgC(i);
            break;
        case OpCode.OP_UNM:
            tm = Tm.TM_UNM;
            break;
        case OpCode.OP_BNOT:
            tm = Tm.TM_BNOT;
            break;
        case OpCode.OP_LEN:
            tm = Tm.TM_LEN;
            break;
        case OpCode.OP_CONCAT:
            tm = Tm.TM_CONCAT;
            break;
        case OpCode.OP_EQ:
            tm = Tm.TM_EQ;
            break;
        case OpCode.OP_LT:
        case OpCode.OP_LTI:
        case OpCode.OP_GTI:
            tm = Tm.TM_LT;
            break;
        case OpCode.OP_LE:
        case OpCode.OP_LEI:
        case OpCode.OP_GEI:
            tm = Tm.TM_LE;
            break;
        case OpCode.OP_CLOSE:
        case OpCode.OP_RETURN:
        case OpCode.OP_RETURN0:
        case OpCode.OP_RETURN1:
            tm = Tm.TM_CLOSE;
            break;
        default:
            return null;
        }
        String ev = (tm >= 0 && tm < Tm.EVENT_NAMES.length) ? Tm.EVENT_NAMES[tm] : null;
        name[0] = (ev != null && ev.startsWith("__")) ? ev.substring(2) : "?";
        return "metamethod";
    }

    /**
     * {@code luaG_opinterror}：算术/位运算的类型错误报<b>坏掉的那一个</b>。
     * <p>
     * 判据是「第一个操作数不是数字就报它」——所以 {@code 1 + nil} 与
     * {@code nil + 1} 都得到「attempt to perform arithmetic on a nil value」，
     * 而不是把两个类型都打进去。
     */
    public LuaError opInterror(LuaValue p1, LuaValue p2, String op) {
        return typeError(p1.isNumber() ? p2 : p1, op);
    }

    /** {@code luaG_ordererror} */
    public LuaError orderError(LuaValue p1, LuaValue p2) {
        String t1 = p1.typeName();
        String t2 = p2.typeName();
        if (t1.equals(t2)) {
            return runError("attempt to compare two " + t1 + " values");
        }
        return runError("attempt to compare " + t1 + " with " + t2);
    }

    /**
     * 抛出一个带位置信息的错误，对应 {@code error(msg, level)} 与
     * {@code assert} 的失败路径。
     * <p>
     * 返回值写成异常是为了调用点能直接 {@code throw L.errorAt(...)}。
     * {@link #where} 非空是这个错误「已经带位置」的标记，{@link #pcall} 据此
     * 决定要不要再补一次——补两次会得到 {@code file:1: file:1: msg} 这种叠前缀。
     *
     * @param level 位置取自第几层调用者（{@code 1} = 直接调用者）；
     *              {@code <= 0} 表示不加位置
     */
    public LuaError errorAt(LuaValue msg, int level) {
        if (level > 0 && msg.isString()) {
            String prefix = whereAt(level);
            return new LuaError(
                    LuaValue.ofString(LuaString.latin1(prefix + msg.asString().toJavaString())),
                    prefix);
        }
        return new LuaError(msg, "");
    }

    /**
     * {@code luaL_error} / {@code luaL_argerror}：库函数抛的错误。
     * <p>
     * 位置取自 {@code luaL_where(L, 1)}（回跳一层到调用者），<b>不是</b>
     * {@link #where}：库函数执行时 {@code ci} 是 C 帧，{@link #where} 只会
     * 给出空串，报出来的消息就没有 {@code src:line:} 前缀了。两类错误的取位
     * 方式不同，是 {@code luaG_runerror} 与 {@code luaL_error} 本身就不同——
     * 合成一个会在某一边丢掉行号。
     */
    public LuaError libError(String msg) {
        return errorAt(LuaValue.ofString(LuaString.latin1(msg)), 1);
    }

    /**
     * {@code luaG_concaterror}：拼接失败时报<b>坏掉的那一个</b>操作数。
     * <p>
     * 判据是「第一个操作数本身能当字符串用吗」——能，说明坏的是第二个；
     * 不能，就报第一个。所以 {@code nil .. 'x'} 与 {@code 'x' .. nil}
     * 都得到「attempt to concatenate a nil value」。
     */
    public LuaError concatError(LuaValue p1, LuaValue p2) {
        LuaValue bad = (p1.isString() || p1.isNumber()) ? p2 : p1;
        return typeError(bad, "concatenate");
    }

    /** {@code luaG_forerror}：消息里要带出错值的类型名 */
    public LuaError forError(LuaValue v, String what) {
        return runError("bad 'for' " + what + " (number expected, got " + v.typeName() + ")");
    }

    /**
     * {@code ldebug.c: varinfo}：给出错误值所在变量的名字，形如
     * {@code " (local 'b')"}、{@code " (constant '10')"}、{@code " (method 'nope')"}。
     * <p>
     * 三段来源，顺序与 C 版一致：先认 upvalue，再认寄存器，寄存器还要反推指令流
     * （{@code findsetreg} + {@code getobjname}）才能知道它是常量、字段还是方法。
     * 少了反推那一段，{@code '10' | 1} 就只能报「attempt to perform bitwise
     * operation on a string value」，而参考实现报的是
     * 「… on a string value (constant '10')」——这类后缀在真实项目里天天出现。
     *
     * @return {@code " (kind 'name')"} 或空串
     */
    public String varInfo(LuaValue o) {
        CallInfo c = ci;
        if (c == null || !c.isLua || c.func >= stack.length) {
            return "";
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return "";
        }
        LClosure cl = (LClosure) f.o;
        Proto p = cl.p;
        String[] name = new String[1];
        String kind = getUpvalName(cl, o, name);
        if (kind == null) {
            int reg = inStack(c, o);
            if (reg >= 0) {
                kind = getObjName(p, (c.savedpc > 0) ? c.savedpc - 1 : 0, reg, name);
            }
        }
        return formatVarInfo(kind, name[0]);
    }

    /**
     * {@code luaG_findlocal}：<b>当前</b> Lua 帧里第 {@code n} 号局部量的名字。
     * <p>
     * {@code checkclosemth} 用它给「non-closable value」报错带上变量名。
     *
     * @param n 从 1 起（与 {@code luaF_getlocalname} 一致）
     */
    public LuaString localNameAt(int n) {
        CallInfo c = ci;
        if (c == null || !c.isLua || c.func >= stack.length) {
            return null;
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return ((LClosure) f.o).p.getLocalName(n, (c.savedpc > 0) ? c.savedpc - 1 : 0);
    }

    private static String formatVarInfo(String kind, String name) {
        if (kind == null) {
            return "";
        }
        return " (" + kind + " '" + ((name == null) ? "?" : name) + "')";
    }

    /** {@code ldebug.c: instack}：值是第几个寄存器？不在栈上返回 {@code -1} */
    private int inStack(CallInfo c, LuaValue o) {
        int base = c.func + 1;
        for (int pos = 0; base + pos < c.top && base + pos < stack.length; pos++) {
            if (stack[base + pos] == o) {
                return pos;
            }
        }
        return -1;
    }

    /** {@code ldebug.c: getupvalname} */
    private static String getUpvalName(LClosure cl, LuaValue o, String[] name) {
        for (int i = 0; i < cl.upvals.length; i++) {
            UpVal uv = cl.upvals[i];
            if (uv != null && uv.value() == o) {
                name[0] = upvalName(cl.p, i);
                return "upvalue";
            }
        }
        return null;
    }

    /** {@code ldebug.c: upvalname}：没有名字时给 {@code "?"}，与 C 版一致 */
    private static String upvalName(Proto p, int uv) {
        LuaString s = (uv < p.sizeupvalues && p.upvalues[uv] != null)
                ? p.upvalues[uv].name : null;
        return (s == null) ? "?" : s.toJavaString();
    }

    /** {@code ldebug.c: kname}：只有字符串常量才有名字 */
    private static String kName(Proto p, int index, String[] name) {
        if (index < 0 || index >= p.k.length) {
            name[0] = "?";
            return null;
        }
        LuaValue kv = p.k[index];
        if (kv.isString()) {
            name[0] = kv.asString().toJavaString();
            return "constant";
        }
        name[0] = "?";
        return null;
    }

    /** {@code ldebug.c: filterpc}：在跳转目标之前的赋值不算数（可能没执行到） */
    private static int filterPc(int pc, int jmptarget) {
        return (pc < jmptarget) ? -1 : pc;
    }

    /**
     * {@code ldebug.c: findsetreg}：反推「最后一次改写 {@code reg} 的指令」。
     * <p>
     * 只扫 {@code lastpc} 之前的指令，且一旦被 {@code jmptarget} 之后的条件跳转
     * 越过就作废——这正是它叫「符号执行」的原因：要顺着控制流判断哪次赋值
     * 真的发生过。
     */
    private static int findSetReg(Proto p, int lastpc, int reg) {
        int setreg = -1;
        int jmptarget = 0;
        int[] code = p.code;
        if (lastpc >= 0 && lastpc < code.length && OpCode.testMMMode(OpCode.getOpCode(code[lastpc]))) {
            lastpc--; // 前一条指令其实没执行（那是落空的 MMBIN）
        }
        for (int pc = 0; pc < lastpc && pc < code.length; pc++) {
            int i = code[pc];
            int op = OpCode.getOpCode(i);
            int a = OpCode.getArgA(i);
            boolean change;
            switch (op) {
            case OpCode.OP_LOADNIL: {
                int b = OpCode.getArgB(i);
                change = (a <= reg && reg <= a + b);
                break;
            }
            case OpCode.OP_TFORCALL: {
                change = (reg >= a + 2);
                break;
            }
            case OpCode.OP_CALL:
            case OpCode.OP_TAILCALL: {
                change = (reg >= a);
                break;
            }
            case OpCode.OP_JMP: {
                int dest = pc + 1 + OpCode.getArgSJ(i);
                if (dest <= lastpc && dest > jmptarget) {
                    jmptarget = dest;
                }
                change = false;
                break;
            }
            default:
                change = (OpCode.testAMode(op) && reg == a);
                break;
            }
            if (change) {
                setreg = filterPc(pc, jmptarget);
            }
        }
        return setreg;
    }

    /** {@code ldebug.c: basicgetobjname}：局部名优先，其次反推指令流 */
    private static String basicGetObjName(Proto p, int[] ppc, int reg, String[] name) {
        int pc = ppc[0];
        LuaString local = p.getLocalName(reg + 1, pc);
        if (local != null) {
            name[0] = local.toJavaString();
            return "local";
        }
        pc = findSetReg(p, pc, reg);
        ppc[0] = pc;
        if (pc != -1 && pc < p.code.length) {
            int i = p.code[pc];
            switch (OpCode.getOpCode(i)) {
            case OpCode.OP_MOVE: {
                int b = OpCode.getArgB(i);
                if (b < OpCode.getArgA(i)) {
                    return basicGetObjName(p, ppc, b, name);
                }
                break;
            }
            case OpCode.OP_GETUPVAL: {
                name[0] = upvalName(p, OpCode.getArgB(i));
                return "upvalue";
            }
            case OpCode.OP_LOADK:
                return kName(p, OpCode.getArgBx(i), name);
            case OpCode.OP_LOADKX:
                return kName(p, OpCode.getArgAx(p.code[pc + 1]), name);
            default:
                break;
            }
        }
        return null;
    }

    /** {@code ldebug.c: rname}：只要常量名，别的都写成 {@code "?"} */
    private static void rName(Proto p, int pc, int c, String[] name) {
        int[] ppc = { pc };
        String what = basicGetObjName(p, ppc, c, name);
        if (what == null || what.charAt(0) != 'c') {
            name[0] = "?";
        }
    }

    /** {@code ldebug.c: rkname}：RK 操作数，可能是常量也可能是寄存器 */
    private static void rkName(Proto p, int pc, int i, String[] name) {
        int c = OpCode.getArgC(i);
        if (OpCode.getArgK(i) != 0) {
            kName(p, c, name);
        } else {
            rName(p, pc, c, name);
        }
    }

    /** {@code ldebug.c: isenv}：被索引的表是不是 {@code _ENV} */
    private static String isEnv(Proto p, int pc, int i, boolean isUp) {
        int t = OpCode.getArgB(i);
        String[] name = new String[1];
        if (isUp) {
            name[0] = upvalName(p, t);
        } else {
            int[] ppc = { pc };
            basicGetObjName(p, ppc, t, name);
        }
        return ("_ENV".equals(name[0])) ? "global" : "field";
    }

    /** {@code ldebug.c: getobjname}：在 {@code basicgetobjname} 之上再认表访问 */
    private static String getObjName(Proto p, int lastpc, int reg, String[] name) {
        int[] ppc = { lastpc };
        String kind = basicGetObjName(p, ppc, reg, name);
        if (kind != null) {
            return kind;
        }
        int pc = ppc[0];
        if (pc != -1 && pc < p.code.length) {
            int i = p.code[pc];
            switch (OpCode.getOpCode(i)) {
            case OpCode.OP_GETTABUP:
                kName(p, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, true);
            case OpCode.OP_GETTABLE:
                rName(p, pc, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, false);
            case OpCode.OP_GETI:
                name[0] = "integer index";
                return "field";
            case OpCode.OP_GETFIELD:
                kName(p, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, false);
            case OpCode.OP_SELF:
                rkName(p, pc, i, name);
                return "method";
            default:
                break;
            }
        }
        return null;
    }

    /**
     * {@code luaG_runerror} 的位置前缀：只在<b>当前</b>帧是 Lua 帧时才加。
     * <p>
     * 与 {@link #whereAt} 的差别是刻意的：{@code luaG_runerror} 用的是
     * {@code isLua(ci)} 这一个判断，而 {@code error()} 走的是
     * {@code luaL_where(L, 1)}，会往回跳一层到调用者。混用会让
     * {@code error("x")} 报出行号偏移一格。
     */
    public String where() {
        CallInfo c = ci;
        if (c == null || !c.isLua) {
            return "";
        }
        return lineInfo(c);
    }

    /**
     * {@code luaL_where(L, level)}：回跳 {@code level} 个调用帧后的位置。
     * <p>
     * {@code level} 只数调用层数、不跳过 C 帧——这一点与 {@code lua_getstack}
     * 一致：若那一层恰好是 C 函数，就没有行号可给，返回空串。
     */
    public String whereAt(int level) {
        CallInfo c = ci;
        for (int i = 0; i < level && c != null; i++) {
            c = c.previous;
        }
        if (c == null || !c.isLua) {
            return "";
        }
        return lineInfo(c);
    }

    private String lineInfo(CallInfo c) {
        if (c.func >= stack.length) {
            return "";
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return "";
        }
        LClosure cl = (LClosure) f.o;
        int pc = (c.savedpc > 0) ? c.savedpc - 1 : 0;
        int line = cl.p.getFuncline(pc);
        String raw = (cl.p.source == null) ? chunkName : cl.p.source.toJavaString();
        return chunkId(raw) + ":" + line + ": ";
    }

    /**
     * {@code luaO_chunkid} 的前两种情形：{@code =} 开头是字面源名、
     * {@code @} 开头是文件名，两种都是去掉首字符后原样输出。
     * <p>
     * 完整版还要处理「源名过长时从左边截成 {@code ...xxx}」以及第三种
     * {@code [string "xxx"]} 形式，那两种只在调试与 {@code load} 的错误里出现，
     * 阶段 3 用不到，等阶段 8 做 {@code load} 时一起补。
     */
    static String chunkId(String source) {
        if (source.isEmpty()) {
            return source;
        }
        char c = source.charAt(0);
        if (c == '=' || c == '@') {
            return source.substring(1);
        }
        return source;
    }
}
