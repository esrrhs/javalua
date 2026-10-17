package com.github.esrrhs.fakescript.lua;

/**
 * 协程库 —— {@code lcorolib.c} 直译。
 * <p>
 * 没有用线程实现协程。Lua 协程是<b>协作式</b>的：同一时刻只有一个在跑，挂起
 * 点由 {@code coroutine.yield} 显式给出。C 版靠「每个 {@code lua_State} 一条
 * 独立栈 + {@code longjmp} 跳出」实现，这里同样靠「每个 {@link LuaState} 一条
 * 独立栈 + 异常跳出」实现，两者结构一一对应。
 * <p>
 * 不用虚拟线程还有一层理由：虚拟线程会让 {@code table.sort} 的比较函数里的
 * yield <b>变成合法的</b>，而参考实现在那里报
 * {@code attempt to yield across a C-call boundary}。目标是「一行不改地跑现有
 * Lua 项目」，所以宁可和 C 一样保守。
 */
public final class CoroutineLib {

    /* lcorolib.c 里的 auxstatus 返回值 */
    private static final int COS_RUN = 0;
    private static final int COS_DEAD = 1;
    private static final int COS_YIELD = 2;
    private static final int COS_NORM = 3;

    private static final String[] STAT_NAME = {"running", "dead", "suspended", "normal"};

    private CoroutineLib() {
    }

    public static void open(LuaState L) {
        LuaTable t = new LuaTable();
        setFn(t, "create", CoroutineLib::cocreate);
        setFn(t, "resume", CoroutineLib::coresume);
        setFn(t, "running", CoroutineLib::corunning);
        setFn(t, "status", CoroutineLib::costatus);
        setFn(t, "wrap", CoroutineLib::cowrap);
        setFn(t, "yield", CoroutineLib::coyield);
        setFn(t, "isyieldable", CoroutineLib::yieldable);
        setFn(t, "close", CoroutineLib::coclose);
        /*
         * 进 _LOADED 而不只是 _G：参考实现里 require("coroutine") 拿到的
         * 就是这张表。少登记这一条，脚本里写 require("coroutine") 会直接
         * 报 module not found。
         */
        LuaAux.registerLib(L, "coroutine", t);
    }

    private static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(LuaString.latin1(name), LuaValue.ofFunction(f));
    }

    /**
     * {@code lcorolib.c: getco}。
     * <p>
     * C 用的是 {@code luaL_argexpected(L, co, 1, "thread")}，类型名取自
     * {@code luaL_typename(L, 1)} —— <b>缺参是 {@code no value}</b>，不是
     * {@code nil}。曾经直接拿 {@code L.arg(0)} 的 {@code typeName()}，
     * 于是 {@code coroutine.status()} 报 {@code got nil}，参考实现报
     * {@code got no value}。
     */
    private static LuaState getco(LuaState L, String fname) {
        LuaValue v = L.arg(0);
        if (L.isNoneArg(1) || v.tt != Lua.VTHREAD) {
            throw L.libError("bad argument #1 to 'coroutine." + fname + "' (thread expected, got "
                    + L.argTypeName(1) + ")");
        }
        return v.asThread();
    }

    /**
     * {@code lcorolib.c: auxresume}。
     *
     * @return 结果个数；{@code -1} 表示出错（错误对象已压在 {@code L} 上）
     */
    private static int auxresume(LuaState L, LuaState co, int narg) {
        /*
         * 两处 {@code lua_checkstack} 都不许抛：失败要报的是
         * {@code too many arguments to resume} / {@code too many results to
         * resume}，不是 {@code stack overflow}。差在这两句上，上游
         * {@code coroutine.lua} 末尾 {@code table.unpack({}, 1, lim)} 那一组
         * 用例就过不去——参考实现给的是「结果太多搬不回来」，
         * 而不是「协程自己栈溢出了」。
         */
        if (!co.checkStackOk(narg)) {
            L.push(LuaValue.ofString(LuaString.latin1("too many arguments to resume")));
            return -1;
        }
        LuaState.xmove(L, co, narg);
        int[] nres = new int[1];
        int status = co.resume(L, narg, nres);
        if (status == LuaState.LUA_OK || status == LuaState.LUA_YIELD) {
            if (!L.checkStackOk(nres[0] + 1)) {
                co.pop(nres[0]); // lua_pop(co, nres)：结果照样从协程上摘掉
                L.push(LuaValue.ofString(LuaString.latin1("too many results to resume")));
                return -1;
            }
            LuaState.xmove(co, L, nres[0]);
            return nres[0];
        }
        LuaState.xmove(co, L, 1); // 搬错误对象
        return -1;
    }

    /** {@code luaB_coresume} */
    private static int coresume(LuaState L) {
        LuaState co = getco(L, "resume");
        int r = auxresume(L, co, L.argCount() - 1);
        if (r < 0) {
            L.push(LuaValue.ofBoolean(false));
            insert(L, -2);
            return 2;
        }
        L.push(LuaValue.ofBoolean(true));
        insert(L, -(r + 1));
        return r + 1;
    }

    /**
     * 把栈顶元素搬到 {@code idx} 处，上面的元素整体上移一格。
     * <p>
     * 对应 {@code lua_insert}。{@code resume} 要把 {@code true/false} 插到
     * 所有结果<b>前面</b>，就是这个操作。
     */
    private static void insert(LuaState L, int idx) {
        int p = (idx < 0) ? L.top + idx : idx;
        LuaValue v = new LuaValue(L.stack[L.top - 1]);
        for (int i = L.top - 1; i > p; i--) {
            L.stack[i].set(L.stack[i - 1]);
        }
        L.stack[p].set(v);
    }

    /** {@code luaB_auxwrap} */
    private static int auxwrap(LuaState L) {
        LuaState co = L.upvalue(0).asThread();
        int r = auxresume(L, co, L.argCount());
        if (r < 0) {
            int stat = co.getStatus();
            if (stat != LuaState.LUA_OK && stat != LuaState.LUA_YIELD) {
                /*
                 * {@code lua_closethread(co, L)} + {@code lua_xmove(co, L, 1)}。
                 * <p>
                 * 注意 {@code xmove} 是往 {@code L} <b>压</b>一个值，不是覆盖：
                 * {@code auxresume} 搬过来的那个还在下面，真正被当作错误对象的
                 * 是这次新压上来的栈顶。
                 * <p>
                 * 而 {@code luaE_resetthread} 要交出的是
                 * {@code luaD_seterrorobj(L, status, L->stack + 1)} 的结果，
                 * 源头是「搬走一格之后的栈顶」——所以 {@code luaB_error} 必须照
                 * C 版写（先 {@code settop(1)} 再 {@code where}+{@code concat}），
                 * 那一格才正好是带位置的错误对象。
                 */
                LuaValue prev = new LuaValue(L.stack[L.top - 1]); // auxresume 刚搬上来的错误对象
                if (co.closeThreadValue(L, prev) != null) {
                    LuaState.xmove(co, L, 1);
                }
            }
            LuaValue err = L.stack[L.top - 1];
            if (err.isString()) {
                // luaL_where(L, 1) + concat：wrap 出来的函数崩了要带位置
                String prefix = L.whereAt(1);
                err = LuaValue.ofString(
                        LuaString.latin1(prefix + err.asString().toJavaString()));
                L.stack[L.top - 1].set(err);
                /*
                 * {@code where} 非 null = 「位置已经加过了」。这里哪怕
                 * {@code prefix} 是空串也要传非 null——{@code pcall} 见到
                 * {@code null} 会再补一次位置，报出
                 * {@code file:3: file:2: boom} 这种叠前缀。
                 */
                throw new LuaError(err, prefix);
            }
            throw new LuaError(err, "");
        }
        return r;
    }

    /** {@code luaB_cocreate} */
    private static int cocreate(LuaState L) {
        createCo(L, "coroutine.create");
        return 1;
    }

    /**
     * {@code create} 与 {@code wrap} 的公共部分。
     * <p>
     * 报错里的函数名要由调用方给：{@code wrap} 内部调的就是
     * {@code luaB_cocreate}，但参考实现报的是
     * {@code bad argument #1 to 'coroutine.wrap'}——因为 C 版的名字是从
     * 「这一帧是被谁调用的」倒推的，而 cocreate 是被 cowrap 直接调的，
     * 倒推出来的名字是最外层那个 Lua 看得见的名字。
     */
    private static void createCo(LuaState L, String fname) {
        LuaValue f = L.arg(0);
        if (L.isNoneArg(1) || !f.isFunction()) {
            /*
             * 同样是 {@code luaL_argexpected}：缺参报 {@code got no value}。
             */
            throw L.libError("bad argument #1 to '" + fname + "' (function expected, got "
                    + L.argTypeName(1) + ")");
        }
        LuaState nl = L.newThread();
        L.push(LuaValue.ofThread(nl)); // 这个才是 create 的返回值
        /*
         * xmove 搬的是<b>栈顶</b>，所以必须先把函数复制一份压上来，否则搬走的
         * 是刚压的协程对象——那样 create 会返回上一个栈槽里的东西（多半就是
         * 那个函数），表现是 type(co) 变成 "function"。
         */
        L.push(new LuaValue(L.arg(0)));
        LuaState.xmove(L, nl, 1); // 把函数搬到新线程的栈上
    }

    /** {@code luaB_cowrap} */
    private static int cowrap(LuaState L) {
        createCo(L, "coroutine.wrap"); // 栈顶现在是新协程
        LuaValue co = new LuaValue(L.stack[L.top - 1]);
        CClosure cl = new CClosure(CoroutineLib::auxwrap, 1);
        cl.upvalue[0] = co;
        L.stack[L.top - 1].set(LuaValue.ofCClosure(cl));
        return 1;
    }

    /** {@code luaB_yield} */
    private static int coyield(LuaState L) {
        L.yieldk(L.argCount());
        return 0; // 不可达：yieldk 一定抛
    }

    /** {@code lcorolib.c: auxstatus} */
    private static int auxStatus(LuaState L, LuaState co) {
        if (L == co) {
            return COS_RUN;
        }
        switch (co.getStatus()) {
        case LuaState.LUA_YIELD:
            return COS_YIELD;
        case LuaState.LUA_OK:
            if (co.hasFrames()) {
                return COS_NORM; // 正在跑（被别人 resume 起来的）
            }
            return (co.getTop() == 0) ? COS_DEAD : COS_YIELD; // 初始状态
        default:
            return COS_DEAD;
        }
    }

    /** {@code luaB_costatus} */
    private static int costatus(LuaState L) {
        LuaState co = getco(L, "status");
        L.push(LuaValue.ofString(LuaString.latin1(STAT_NAME[auxStatus(L, co)])));
        return 1;
    }

    /** {@code luaB_yieldable} */
    private static int yieldable(LuaState L) {
        LuaState co = (L.argCount() == 0 || L.arg(0).isNone()) ? L : getco(L, "isyieldable");
        L.push(LuaValue.ofBoolean(co.isYieldable()));
        return 1;
    }

    /** {@code luaB_corunning} */
    private static int corunning(LuaState L) {
        int ismain = L.pushThread();
        L.push(LuaValue.ofBoolean(ismain != 0));
        return 2;
    }

    /** {@code luaB_close} */
    private static int coclose(LuaState L) {
        LuaState co = getco(L, "close");
        int status = auxStatus(L, co);
        switch (status) {
        case COS_DEAD:
        case COS_YIELD: {
            LuaValue err = co.closeThreadValue(L);
            if (err == null) {
                L.push(LuaValue.ofBoolean(true));
                return 1;
            }
            L.push(LuaValue.ofBoolean(false));
            LuaState.xmove(co, L, 1);
            return 2;
        }
        default:
            throw L.libError("cannot close a " + STAT_NAME[status] + " coroutine");
        }
    }
}
