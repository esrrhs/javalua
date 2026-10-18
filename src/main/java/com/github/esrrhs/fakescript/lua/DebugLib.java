package com.github.esrrhs.fakescript.lua;

import java.nio.charset.StandardCharsets;

/**
 * {@code debug} 库 —— 对应 {@code ldblib.c}，外加 {@code ldebug.c} 里那部分
 * 只在调试接口才用到的东西（{@code lua_getstack} / {@code lua_getinfo} /
 * {@code luaG_findlocal}）与 {@code lauxlib.c} 的 {@code luaL_traceback}。
 * <p>
 * 分成两块搬：
 * <ul>
 *   <li><b>第一轮</b>：{@code getinfo} / {@code getlocal} / {@code setlocal} /
 *       upvalue 那四个 / {@code traceback} / {@code getregistry} /
 *       元表那两个 / userdata 那两个 / {@code setcstacklimit} / {@code debug}；</li>
 *   <li><b>第二轮</b>：{@code sethook} / {@code gethook}，以及它们要求的
 *       {@code ldo.c} 的 {@code luaD_hook} 与 {@code ldebug.c} 的
 *       {@code luaG_traceexec} / {@code luaG_tracecall}。调用点分散在
 *       {@link Vm} 的主循环与 {@link LuaState} 的 {@code precallC} /
 *       {@code poscall} 里，见 {@link #dHook}。</li>
 * </ul>
 *
 * <h2>与错误信息的差别</h2>
 * {@code LuaState} 里已经有 {@code findSetReg} / {@code basicGetObjName} /
 * {@code funcNameFromCode}——那是给 {@code luaG_runerror} 拼
 * 「attempt to call a nil value (global 'x')」用的。看起来是同一套符号执行，
 * 但取名的<b>起点不同</b>：错误信息问的是「当前这一帧在调什么」，
 * 而 {@code getinfo('n')} 问的是「{@code ci} 是被<b>上一帧</b>怎么调的」
 * （{@code ci->previous}）。混用会让 {@code debug.getinfo(2).name} 整体错一层。
 */
public final class DebugLib {

    private DebugLib() {
    }

    /** {@code lua_Debug} */
    public static final class LuaDebug {
        /** 钩子事件：{@link Lua#LUA_HOOKCALL} 等。只有钩子路径会用到 */
        int event;

        /** {@code i_ci}：对应的调用帧 */
        CallInfo iCi;

        /**
         * {@link #iCi} 所属的线程。
         * <p>
         * C 版不需要这个字段——{@code CallInfo} 里存的是<b>栈指针</b>，
         * {@code luaL_traceback(co, ...)} 拿着协程的 {@code CallInfo} 也能直接
         * 解引用到协程的栈槽。这里 {@link CallInfo} 存的是下标，必须再记住是
         * 谁的下标，否则 {@code debug.traceback(co)} 会用主线程的栈去解释
         * 协程的帧号。
         */
        LuaState state;
        LuaString source;
        /**
         * {@code luaO_chunkid} 的结果。
         * <p>
         * 这里存的是<b>字节串</b>而不是 Java String：chunk 名可以是任意字节
         * （{@code load(f, "\xff")} 合法），走 Java String 会被 UTF-8 解码
         * 替换成 {@code U+FFFD}，{@code short_src} 与
         * {@code debug.traceback} 就都跟着错。
         */
        LuaString shortSrc;
        int linedefined;
        int lastlinedefined;
        String what;
        int currentline = -1;
        int nups;
        int nparams;
        boolean isvararg;
        boolean istailcall;
        String name;
        String namewhat = "";
        int ftransfer;
        int ntransfer;

        /**
         * 给 {@code funcnamefromcall} 传出名字用的「出参」。
         * <p>
         * C 版是 {@code const char **name} 直接指向 {@code ar->name}；Java 没有
         * 指向字段的指针，用一个 {@code String[]} 当出参。这是 C 里
         * 「名字与名字种类分开返回」的惯例——{@code funcnamefromcode} 返回种类
         * 字符串（{@code NULL} 表示没找到），名字本身另走一条通道。
         */
        final String[] nameHolder = new String[1];
    }

    /* ==================== 开库 ==================== */

    public static void open(LuaState L) {
        LuaTable d = new LuaTable();
        LuaAux.setFn(d, "getregistry", DebugLib::getRegistry);
        LuaAux.setFn(d, "getmetatable", DebugLib::getMetatable);
        LuaAux.setFn(d, "setmetatable", DebugLib::setMetatable);
        LuaAux.setFn(d, "getuservalue", DebugLib::getUserValue);
        LuaAux.setFn(d, "setuservalue", DebugLib::setUserValue);
        LuaAux.setFn(d, "getinfo", DebugLib::getInfoFn);
        LuaAux.setFn(d, "getlocal", DebugLib::getLocalFn);
        LuaAux.setFn(d, "setlocal", DebugLib::setLocalFn);
        LuaAux.setFn(d, "getupvalue", DebugLib::getUpValue);
        LuaAux.setFn(d, "setupvalue", DebugLib::setUpValue);
        LuaAux.setFn(d, "upvalueid", DebugLib::upValueId);
        LuaAux.setFn(d, "upvaluejoin", DebugLib::upValueJoin);
        LuaAux.setFn(d, "traceback", DebugLib::traceback);
        LuaAux.setFn(d, "setcstacklimit", DebugLib::setCStackLimit);
        LuaAux.setFn(d, "debug", DebugLib::debugFn);
        LuaAux.setFn(d, "sethook", DebugLib::setHook);
        LuaAux.setFn(d, "gethook", DebugLib::getHook);
        LuaAux.registerLib(L, "debug", d);
    }

    /* ==================== ldebug.c：栈帧与信息 ==================== */

    /**
     * {@code lua_getstack}：回跳 {@code level} 层。
     * <p>
     * {@code level < 0} 直接失败；{@code level == 0} 是「主 chunk 外的那一层」，
     * 也就是 C 的 {@code base_ci}，同样失败——所以
     * {@code debug.getinfo(2)} 在只有一层时给 {@code nil}。
     */
    static boolean getStack(LuaState L, int level, LuaDebug ar) {
        if (level < 0) {
            return false;
        }
        CallInfo ci = L.ci;
        while (level > 0 && ci != null && ci.previous != null) {
            ci = ci.previous;
            level--;
        }
        if (level == 0 && ci != null && ci.previous != null) {
            ar.iCi = ci;
            ar.state = L;
            return true;
        }
        return false;
    }

    /** {@code currentpc}：{@code savedpc} 是「下一条」，当前这条要减一 */
    private static int currentPc(CallInfo ci) {
        return (ci.savedpc > 0) ? ci.savedpc - 1 : 0;
    }

    /**
     * {@code getcurrentline}：{@code ldebug.c} 的 {@code currentline}，
     * 但按 {@link #auxGetInfo} 的要求先判 {@code isLua}——C 版把
     * {@code lua_assert(isLua(ci))} 放在 {@code currentpc} 里，调用点在外层判。
     */
    private static int currentLine(LuaState L, CallInfo ci) {
        if (!ci.isLua) {
            return -1;
        }
        LuaValue f = L.stack[ci.func];
        if (f.tt != Lua.VLIF) {
            return -1;
        }
        return ((LClosure) f.o).p.getFuncline(currentPc(ci));
    }

    /* ==================== ldebug.c：名字 ==================== */

    /** {@code funcinfo}：源名、行号范围、what */
    private static void funcInfo(LuaDebug ar, Object cl) {
        if (!(cl instanceof LClosure)) {
            ar.source = LuaString.latin1("=[C]");
            ar.linedefined = -1;
            ar.lastlinedefined = -1;
            ar.what = "C";
        } else {
            Proto p = ((LClosure) cl).p;
            ar.source = (p.source == null) ? LuaString.latin1("=?") : p.source;
            ar.linedefined = p.linedefined;
            ar.lastlinedefined = p.lastlinedefined;
            ar.what = (p.linedefined == 0) ? "main" : "Lua";
        }
        ar.shortSrc = LuaString.of(LuaState.chunkId(ar.source.bytes));
    }

    /** {@code getfuncname}：被调函数是怎么被<b>上一帧</b>调的 */
    private static String getFuncName(LuaState L, CallInfo ci, String[] name) {
        if (ci != null && !ci.tail) {
            return funcNameFromCall(L, ci.previous, name);
        }
        return null;
    }

    /**
     * {@code funcnamefromcall}。
     */
    private static String funcNameFromCall(LuaState L, CallInfo ci, String[] name) {
        /*
         * 顺序照抄上游：{@code CIST_HOOKED} 在 {@code isLua} <b>之前</b>判。
         * 这不是风格问题——被钩子跟踪的那一帧常常是 <b>C 帧</b>（比如
         * {@code pcall} 的调用钩子是在 pcall 自己的 C 帧上打的标记），
         * 挪到 {@code isLua} 之后就永远走不到，回溯里那一层会错印成
         * {@code in function <文件:行>} 而不是 {@code in hook '?'}。
         */
        if (ci == null) {
            return null;
        }
        if (ci.hooked) {
            name[0] = "?";
            return "hook";
        }
        if (ci.fin) {
            name[0] = "__gc";
            return "metamethod";
        }
        // base_ci 就是这条分支的常客：它的 isLua 为 false，等价于 C 的
        // "ci->callstatus & CIST_C"，于是「主 chunk 是被谁调的」永远没有名字
        if (!ci.isLua) {
            return null;
        }
        LuaValue f = L.stack[ci.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return LuaState.funcNameFromCode(((LClosure) f.o).p, currentPc(ci), name);
    }

    /* ==================== ldebug.c：getinfo ==================== */

    /** {@code auxgetinfo} */
    private static boolean auxGetInfo(LuaState L, String what, LuaDebug ar, Object cl, CallInfo ci) {
        boolean status = true;
        for (int i = 0; i < what.length(); i++) {
            switch (what.charAt(i)) {
            case 'S':
                funcInfo(ar, cl);
                break;
            case 'l':
                ar.currentline = (ci != null && ci.isLua) ? currentLine(L, ci) : -1;
                break;
            case 'u':
                ar.nups = nupvaluesOf(cl);
                if (!(cl instanceof LClosure)) {
                    ar.isvararg = true;
                    ar.nparams = 0;
                } else {
                    Proto p = ((LClosure) cl).p;
                    ar.isvararg = p.isVararg;
                    ar.nparams = p.numparams;
                }
                break;
            case 't':
                ar.istailcall = (ci != null) && ci.tail;
                break;
            case 'n': {
                String kind = getFuncName(L, ci, ar.nameHolder);
                if (kind == null) {
                    ar.namewhat = "";
                    ar.name = null;
                } else {
                    ar.namewhat = kind;
                    ar.name = ar.nameHolder[0];
                }
                break;
            }
            case 'r':
                /*
                 * 传递信息只在<b>返回钩子期间</b>有效：{@code luaD_hook} 进钩子前
                 * 把 {@code CIST_TRAN} 打在这一帧上、出来就清掉。所以
                 * {@code debug.getinfo(2, 'r')} 在钩子里能读到返回值个数，
                 * 在普通代码里一律是 0/0。
                 */
                if (ci == null || !ci.tran) {
                    ar.ftransfer = 0;
                    ar.ntransfer = 0;
                } else {
                    ar.ftransfer = ci.ftransfer;
                    ar.ntransfer = ci.ntransfer;
                }
                break;
            case 'L':
            case 'f':
                break; // 由 getInfo 处理（它们要往栈上推东西）
            default:
                status = false;
                break;
            }
        }
        return status;
    }

    /** 取闭包的 upvalue 个数：轻量 C 函数没有闭包，给 0 */
    private static int nupvaluesOf(Object cl) {
        if (cl instanceof LClosure) {
            return ((LClosure) cl).p.sizeupvalues;
        }
        if (cl instanceof CClosure) {
            return ((CClosure) cl).upvalue.length;
        }
        return 0;
    }

    /**
     * {@code lua_getinfo}。
     * <p>
     * {@code what} 以 {@code >} 开头表示「信息取自栈顶那个函数」而不是某个栈层。
     */
    static boolean getInfo(LuaState L, String what, LuaDebug ar) {
        CallInfo ci;
        LuaValue func;
        if (!what.isEmpty() && what.charAt(0) == '>') {
            ci = null;
            func = new LuaValue(L.stack[L.top - 1]);
            L.top--; // 弹出函数（'f' 分支会把它推回来）
            what = what.substring(1);
        } else {
            ci = ar.iCi;
            /*
             * 必须<b>拷</b>出来：这里拿到的是栈上那个活的槽对象，而下面
             * L.push(func) 若推的是同一个引用，表里就存了一个会随栈变化的别名。
             * C 版是 setobj2s 的按值复制，等价的是 new LuaValue(...)。
             */
            func = new LuaValue(L.stack[ci.func]);
        }
        Object cl = null;
        if (func.tt == Lua.VLIF) {
            cl = func.o;
        } else if (func.tt == Lua.VCCL) {
            cl = func.o;
        }
        // VLCF（轻量 C 函数）没有闭包对象，cl 保持 null —— 与 C 的
        // "ttisclosure(func) ? clvalue(func) : NULL" 一致
        boolean status = auxGetInfo(L, what, ar, cl, ci);
        if (what.indexOf('f') >= 0) {
            L.push(func);
        }
        if (what.indexOf('L') >= 0) {
            L.push(collectValidLines(cl));
        }
        return status;
    }

    /** {@code collectvalidlines}：所有「有指令落在上面」的行 */
    private static LuaValue collectValidLines(Object cl) {
        if (!(cl instanceof LClosure)) {
            return LuaValue.ofNil();
        }
        Proto p = ((LClosure) cl).p;
        LuaTable t = new LuaTable();
        if (p.sizelineinfo > 0) {
            int currentline = p.linedefined;
            int i;
            if (!p.isVararg) {
                i = 0;
            } else {
                // 变参函数的第一条是 OP_VARARGPREP，不算
                currentline = nextLine(p, currentline, 0);
                i = 1;
            }
            for (; i < p.sizelineinfo; i++) {
                currentline = nextLine(p, currentline, i);
                t.set(currentline, LuaValue.ofBoolean(true));
            }
        }
        return LuaValue.ofTable(t);
    }

    /** {@code nextline}：{@code ABSLINEINFO} 那一条的行号存在 abslineinfo 里 */
    private static int nextLine(Proto p, int currentline, int pc) {
        if (pc < p.lineinfo.length && p.lineinfo[pc] != Proto.ABSLINEINFO) {
            return currentline + p.lineinfo[pc];
        }
        return p.getFuncline(pc);
    }

    /* ==================== ldebug.c：局部变量 ==================== */

    /** {@code findvararg}：负下标访问变长参数 */
    private static String findVarArg(LuaState L, CallInfo ci, int n, int[] posOut) {
        LuaValue f = L.stack[ci.func];
        if (f.tt == Lua.VLIF && ((LClosure) f.o).p.isVararg) {
            int nextra = ci.nextraargs;
            if (n >= -nextra) {
                posOut[0] = ci.func - nextra - (n + 1);
                return "(vararg)";
            }
        }
        return null;
    }

    /** {@code luaG_findlocal} */
    private static String findLocal(LuaState L, CallInfo ci, int n, int[] posOut) {
        int base = ci.func + 1;
        String name = null;
        if (ci.isLua) {
            if (n < 0) {
                return findVarArg(L, ci, n, posOut);
            }
            LuaValue f = L.stack[ci.func];
            if (f.tt == Lua.VLIF) {
                LuaString s = ((LClosure) f.o).p.getLocalName(n, currentPc(ci));
                if (s != null) {
                    name = s.toLatin1String();
                }
            }
        }
        if (name == null) {
            int limit = (ci == L.ci) ? L.top : nextFrameFunc(L, ci);
            if (limit - base >= n && n > 0) {
                name = ci.isLua ? "(temporary)" : "(C temporary)";
            } else {
                return null;
            }
        }
        posOut[0] = base + (n - 1);
        return name;
    }

    /**
     * C 的 {@code ci->next->func.p}：本帧<b>里面那一层</b>（被调用者）的起点。
     * <p>
     * 本实现只存了 {@code previous} 链，{@code next} 要反着找回来。
     */
    private static int nextFrameFunc(LuaState L, CallInfo ci) {
        for (CallInfo c = L.ci; c != null; c = c.previous) {
            if (c.previous == ci) {
                return c.func;
            }
        }
        return L.top;
    }

    /** {@code lua_getlocal} */
    private static String getLocal(LuaState L, LuaDebug ar, int n) {
        if (ar == null) {
            // 查非活动函数：只看入口处还活着的参数
            LuaValue f = L.stack[L.top - 1];
            if (f.tt != Lua.VLIF) {
                return null;
            }
            LuaString s = ((LClosure) f.o).p.getLocalName(n, 0);
            return (s == null) ? null : s.toLatin1String();
        }
        int[] pos = new int[1];
        String name = findLocal(L, ar.iCi, n, pos);
        if (name != null) {
            L.push(new LuaValue(L.stack[pos[0]]));
        }
        return name;
    }

    /** {@code lua_setlocal} */
    private static String setLocal(LuaState L, LuaDebug ar, int n) {
        int[] pos = new int[1];
        String name = findLocal(L, ar.iCi, n, pos);
        if (name != null) {
            L.stack[pos[0]].set(L.stack[L.top - 1]);
            L.top--;
        }
        return name;
    }

    /* ==================== ldebug.c：upvalue ==================== */

    /** {@code auxupvalue}：返回名字，{@code valOut[0]} 是那个值的槽 */
    private static String auxUpValue(LuaValue f, int n, LuaValue[] valOut, Object[] ownerOut) {
        if (f.tt == Lua.VCCL) {
            CClosure c = (CClosure) f.o;
            if (n <= 0 || n > c.upvalue.length) {
                return null;
            }
            valOut[0] = c.upvalue[n - 1];
            if (ownerOut != null) {
                /*
                 * C 的 {@code lua_upvalueid} 给 C 闭包返回的是
                 * {@code &f->upvalue[n-1]}——<b>槽的地址</b>，不是闭包本身。
                 * 这里槽对象是 {@link LuaValue} 且永不被替换（只 {@code set}），
                 * 身份稳定，与 C 的取址等价。注意与 {@code aux_upvalue} 的
                 * {@code *owner = obj2gco(f)} 不同：那是给写屏障用的，我们不做。
                 */
                ownerOut[0] = c.upvalue[n - 1];
            }
            return "";
        }
        if (f.tt == Lua.VLIF) {
            LClosure cl = (LClosure) f.o;
            Proto p = cl.p;
            if (n <= 0 || n > p.sizeupvalues) {
                return null;
            }
            valOut[0] = cl.upvals[n - 1].value();
            if (ownerOut != null) {
                ownerOut[0] = cl.upvals[n - 1];
            }
            /*
             * 这里<b>不能</b>用 {@link LuaState#upvalName}。C 版有两个不同的函数：
             * {@code lapi.c: aux_upvalue} 在名字为空时给 {@code "(no name)"}，
             * 而 {@code ldebug.c: upvalname} 给 {@code "?"}。差别只在调试信息被
             * {@code string.dump(f, true)} 剥掉时才看得见——上游
             * {@code db.lua} 断言 {@code debug.getupvalue(f, 1)} 拿到
             * {@code "(no name)"}，给 {@code "?"} 就过不去。
             */
            LuaString nm = (p.upvalues[n - 1] != null) ? p.upvalues[n - 1].name : null;
            return (nm == null) ? "(no name)" : nm.toLatin1String();
        }
        return null;
    }

    /* ==================== ldblib.c：各函数 ==================== */

    /** {@code getthread}：第一个参数可能是协程 */
    private static LuaState getThread(LuaState L, int[] arg) {
        if (!L.isNoneArg(1) && L.arg1(1).tt == Lua.VTHREAD) {
            arg[0] = 1;
            return (LuaState) L.arg1(1).o;
        }
        arg[0] = 0;
        return L;
    }

    private static int getRegistry(LuaState L) {
        L.push(LuaValue.ofTable(L.registry));
        return 1;
    }

    private static int getMetatable(LuaState L) {
        LuaAux.checkAny(L, 1);
        LuaTable mt = LuaTm.metatableOf(L, L.arg1(1));
        if (mt == null) {
            L.push(LuaValue.ofNil());
        } else {
            L.push(LuaValue.ofTable(mt));
        }
        return 1;
    }

    private static int setMetatable(LuaState L) {
        /*
         * 判的是「第 2 个参数的原始类型」，不能先把缺省折算成 nil：
         * C 版是 {@code int t = lua_type(L, 2)}——参数<b>不存在</b>时 t 是
         * {@code LUA_TNONE}(-1)，一样报错。先折成 nil 再判会让
         * {@code debug.setmetatable()} 静默通过，而参考实现报
         * {@code nil or table expected, got no value}。
         */
        int t = L.isNoneArg(2) ? Lua.TNONE : L.arg1(2).baseType();
        LuaAux.argExpected(L, t == Lua.TNIL || t == Lua.TTABLE, 2, "nil or table");
        L.setTop(2); // 第二个参数不存在时补出一个 nil
        LuaValue v = L.arg1(2);
        LuaValue obj = L.arg1(1);
        if (v.isNil()) {
            LuaTm.setMetatable(L, obj, null);
        } else {
            LuaTm.setMetatable(L, obj, (LuaTable) v.o);
        }
        L.setTop(1);
        return 1;
    }

    private static int getUserValue(LuaState L) {
        int n = (int) LuaAux.optInteger(L, 2, 1);
        if (L.isNoneArg(1) || L.arg1(1).tt != Lua.VUSERDATA) {
            L.push(LuaValue.ofNil());
            return 1;
        }
        // 本实现的 userdata 不带用户值（lua_newuserdatauv 的 nuvalue 总为 0）
        L.push(LuaValue.ofNil());
        return 1;
    }

    private static int setUserValue(LuaState L) {
        /*
         * 序号取在第 3 个参数上（{@code luaL_optinteger(L, 3, 1)}），而且要在
         * 类型检查<b>之前</b>——所以 {@code debug.setuservalue(1, 2, "x")} 报的是
         * 「#3 不是数」，不是「#1 不是 userdata」。
         */
        LuaAux.optInteger(L, 3, 1);
        LuaAux.checkType(L, 1, Lua.TUSERDATA);
        LuaAux.checkAny(L, 2);
        L.setTop(2);
        L.push(LuaValue.ofNil()); // 本实现的 userdata 不带用户值，必失败
        return 1;
    }

    private static int getInfoFn(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        LuaString optStr = LuaAux.optString(L, arg + 2, LuaString.latin1("flnSrtu"));
        /*
         * what 是 const char*：C 的 lua_getinfo 扫到第一个 \0 就收工，后面那几个
         * 字符<b>连看都不看</b>。不截断的话 debug.getinfo(f, "S\0ln") 会把 'l' 'n'
         * 也当成 option 解析 —— 而它们在这里是合法的、在别处却可能不合法，
         * 于是凭空多出一条 "invalid option"。
         */
        String options = optStr.toCString().toJavaString();
        LuaAux.argCheck(L, options.isEmpty() || options.charAt(0) != '>', arg + 2,
                "invalid option '>'");
        LuaDebug ar = new LuaDebug();
        if (!L.isNoneArg(arg + 1) && L.arg1(arg + 1).baseType() == Lua.TFUNCTION) {
            options = ">" + options;
            // 把函数放到 L1 的栈顶
            LuaValue f = new LuaValue(L.arg1(arg + 1));
            if (L1 == L) {
                L.push(f);
            } else {
                L1.push(f);
            }
        } else {
            int level = (int) LuaAux.checkInteger(L, arg + 1);
            if (!getStack(L1, level, ar)) {
                L.push(LuaValue.ofNil()); // 层号越界
                return 1;
            }
        }
        if (!getInfo(L1, options, ar)) {
            throw L.argError(arg + 2, "invalid option");
        }
        LuaTable t = new LuaTable();
        L.push(LuaValue.ofTable(t));
        if (options.indexOf('S') >= 0) {
            t.set("source", LuaValue.ofString(ar.source));
            t.set("short_src", LuaValue.ofString(ar.shortSrc));
            t.set("linedefined", LuaValue.ofInteger(ar.linedefined));
            t.set("lastlinedefined", LuaValue.ofInteger(ar.lastlinedefined));
            t.set("what", LuaValue.ofString(LuaString.utf8(ar.what)));
        }
        if (options.indexOf('l') >= 0) {
            t.set("currentline", LuaValue.ofInteger(ar.currentline));
        }
        if (options.indexOf('u') >= 0) {
            t.set("nups", LuaValue.ofInteger(ar.nups));
            t.set("nparams", LuaValue.ofInteger(ar.nparams));
            t.set("isvararg", LuaValue.ofBoolean(ar.isvararg));
        }
        if (options.indexOf('n') >= 0) {
            t.set("name", (ar.name == null) ? LuaValue.ofNil()
                    : LuaValue.ofString(LuaString.latin1(ar.name)));
            t.set("namewhat", LuaValue.ofString(LuaString.latin1(ar.namewhat)));
        }
        if (options.indexOf('r') >= 0) {
            t.set("ftransfer", LuaValue.ofInteger(ar.ftransfer));
            t.set("ntransfer", LuaValue.ofInteger(ar.ntransfer));
        }
        if (options.indexOf('t') >= 0) {
            t.set("istailcall", LuaValue.ofBoolean(ar.istailcall));
        }
        // 'L' 与 'f' 的结果已经压在 L1 栈顶，取下来装进表
        if (options.indexOf('L') >= 0) {
            popIntoField(L, L1, t, "activelines");
        }
        if (options.indexOf('f') >= 0) {
            popIntoField(L, L1, t, "func");
        }
        return 1;
    }

    /**
     * {@code treatstackoption}。
     * <p>
     * C 版是 {@code lua_rotate(L, -2, 1)} 再 {@code lua_setfield(L, -2, fname)}：
     * 结果表是<b>后</b>压的，{@code lua_getinfo} 的产物在它<b>底下</b>，所以要先
     * 交换再 {@code setfield}。直接 {@code L.top--} 弹掉的是结果表本人，
     * 于是 {@code debug.getinfo(1).func} 里存的成了那张表自己，
     * 而函数返回的是表底下的东西——症状是 {@code debug.getinfo(1)} 返回一个
     * 奇怪的函数而不是表。
     */
    private static void popIntoField(LuaState L, LuaState L1, LuaTable t, String fname) {
        LuaValue v;
        if (L == L1) {
            // 栈形如 [..., obj, table]：把 obj 从 table 底下掏出来，再复位栈顶
            v = new LuaValue(L.stack[L.top - 2]);
            L.top -= 2;
            L.push(LuaValue.ofTable(t));
        } else {
            v = new LuaValue(L1.stack[--L1.top]);
        }
        t.set(fname, v);
    }

    private static int getLocalFn(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        int nvar = (int) LuaAux.checkInteger(L, arg + 2);
        if (!L.isNoneArg(arg + 1) && L.arg1(arg + 1).baseType() == Lua.TFUNCTION) {
            L.push(new LuaValue(L.arg1(arg + 1)));
            String name = getLocal(L, null, nvar);
            L.push((name == null) ? LuaValue.ofNil() : LuaValue.ofString(LuaString.utf8(name)));
            return 1;
        }
        int level = (int) LuaAux.checkInteger(L, arg + 1);
        LuaDebug ar = new LuaDebug();
        if (!getStack(L1, level, ar)) {
            throw L.argError(arg + 1, "level out of range");
        }
        String name = getLocal(L1, ar, nvar);
        if (name != null) {
            LuaValue v;
            if (L1 == L) {
                v = new LuaValue(L.stack[--L.top]);
            } else {
                v = new LuaValue(L1.stack[--L1.top]);
            }
            L.push((name == null) ? LuaValue.ofNil() : LuaValue.ofString(LuaString.utf8(name)));
            L.push(v);
            return 2;
        }
        L.push(LuaValue.ofNil());
        return 1;
    }

    private static int setLocalFn(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        int level = (int) LuaAux.checkInteger(L, arg + 1);
        int nvar = (int) LuaAux.checkInteger(L, arg + 2);
        LuaDebug ar = new LuaDebug();
        if (!getStack(L1, level, ar)) {
            throw L.argError(arg + 1, "level out of range");
        }
        LuaAux.checkAny(L, arg + 3);
        L.setTop(arg + 3);
        LuaValue v = new LuaValue(L.stack[L.top - 1]);
        if (L1 == L) {
            L.push(v);
        } else {
            L1.push(v);
        }
        String name = setLocal(L1, ar, nvar);
        if (name == null) {
            L1.top--; // 没写进去，把值弹掉
        }
        L.push((name == null) ? LuaValue.ofNil() : LuaValue.ofString(LuaString.utf8(name)));
        return 1;
    }

    /** {@code auxupvalue} 的两个入口共用的实现 */
    private static int auxUpValueFn(LuaState L, boolean get) {
        int n = (int) LuaAux.checkInteger(L, 2);
        LuaAux.checkType(L, 1, Lua.TFUNCTION);
        LuaValue f = L.arg1(1);
        LuaValue[] val = new LuaValue[1];
        String name = auxUpValue(f, n, val, null);
        if (name == null) {
            return 0;
        }
        if (get) {
            L.push(new LuaValue(val[0]));
            L.push(LuaValue.ofString(LuaString.latin1(name)));
            // lua_insert(L, -(get+1))：名字挪到值下面，打印出来才是「名字 值」
            LuaValue nm = new LuaValue(L.stack[L.top - 1]);
            L.stack[L.top - 1].set(L.stack[L.top - 2]);
            L.stack[L.top - 2].set(nm);
            return 2;
        }
        LuaValue v = new LuaValue(L.stack[L.top - 1]);
        L.top--;
        val[0].set(v);
        L.push(LuaValue.ofString(LuaString.latin1(name)));
        return 1;
    }

    private static int getUpValue(LuaState L) {
        return auxUpValueFn(L, true);
    }

    private static int setUpValue(LuaState L) {
        LuaAux.checkAny(L, 3);
        return auxUpValueFn(L, false);
    }

    private static int upValueId(LuaState L) {
        int n = (int) LuaAux.checkInteger(L, 2);
        LuaAux.checkType(L, 1, Lua.TFUNCTION);
        LuaValue f = L.arg1(1);
        LuaValue[] val = new LuaValue[1];
        Object[] owner = new Object[1];
        String name = auxUpValue(f, n, val, owner);
        if (name == null || owner[0] == null) {
            L.push(LuaValue.ofNil());
            return 1;
        }
        L.push(LuaValue.ofLightUserdata(owner[0]));
        return 1;
    }

    private static int upValueJoin(LuaState L) {
        int n1 = (int) LuaAux.checkInteger(L, 2);
        LuaAux.checkType(L, 1, Lua.TFUNCTION);
        LuaValue f1 = L.arg1(1);
        Object[] owner1 = new Object[1];
        if (auxUpValue(f1, n1, new LuaValue[1], owner1) == null) {
            throw L.argError(2, "invalid upvalue index");
        }
        int n2 = (int) LuaAux.checkInteger(L, 4);
        LuaAux.checkType(L, 3, Lua.TFUNCTION);
        LuaValue f2 = L.arg1(3);
        Object[] owner2 = new Object[1];
        if (auxUpValue(f2, n2, new LuaValue[1], owner2) == null) {
            throw L.argError(4, "invalid upvalue index");
        }
        LuaAux.argCheck(L, f1.tt == Lua.VLIF, 1, "Lua function expected");
        LuaAux.argCheck(L, f2.tt == Lua.VLIF, 3, "Lua function expected");
        ((LClosure) f1.o).setUpVal(n1 - 1, ((LClosure) f2.o).upvals[n2 - 1]);
        return 0;
    }

    /* ==================== 钩子 ==================== */

    /**
     * 注册表里那张「线程 → 钩子函数」的表。
     * <p>
     * 为什么不直接把函数存在 {@code lua_State} 上：{@code debug.sethook} 允许给
     * <b>别的</b>线程设钩子（{@code debug.sethook(co, f, "l")}），而每个线程的
     * 钩子是各自独立的。用线程做键的表正好表达这一点。
     */
    private static final LuaString HOOKKEY = LuaString.latin1("_HOOKKEY");

    /** 事件名的顺序必须与 {@code Lua.LUA_HOOK*} 的取值一一对应 */
    private static final String[] HOOKNAMES =
            { "call", "return", "line", "count", "tail call" };

    /**
     * {@code ldblib.c: hookf} —— {@code debug.sethook} 装的那个钩子。
     * <p>
     * 它是在「被跟踪的那一帧」上被调用的，{@code ar.iCi} 就指向那一帧；
     * 这里真正做的是「查出用户注册的那个 Lua 函数并调它」。
     * <p>
     * <b>用 {@code callNoYield} 而不是 {@code call}</b>：C 版这里是
     * {@code lua_call}，而 {@code lua_call} 走 {@code luaD_callnoyield}，
     * 会把「不可 yield」的层数加一。后果是<b>钩子函数里 yield 一律报错</b>——
     * 实测参考实现：主线程上是
     * {@code attempt to yield from outside a coroutine}，协程里是
     * {@code attempt to yield across a C-call boundary}，两条都让程序/协程死掉。
     * 换成 {@code call} 就会「成功 yield」，与参考实现相反。
     */
    private static final LuaHook HOOKF = DebugLib::hookf;

    private static void hookf(LuaState L, LuaDebug ar) {
        LuaValue ht = L.registry.get(HOOKKEY);
        if (ht == null || ht.tt != Lua.TTABLE) {
            return;
        }
        LuaValue fn = ht.asTable().get(LuaValue.ofThread(L));
        if (fn == null || fn.baseType() != Lua.TFUNCTION) {
            return;
        }
        int f = L.top;
        L.checkStack(3);
        L.stack[L.top++].set(fn);
        L.stack[L.top++].set(LuaValue.ofString(LuaString.utf8(HOOKNAMES[ar.event])));
        if (ar.currentline >= 0) {
            L.stack[L.top++].set(LuaValue.ofInteger(ar.currentline));
        } else {
            L.stack[L.top++].setNil();
        }
        getInfo(L, "lS", ar); // 补齐 currentline 与 source，钩子常用
        L.callNoYield(f, 0);
    }

    /**
     * {@code makemask}：{@code "crl"} 这样的字符串转成掩码。
     * <p>
     * C 用的是 {@code strchr(smask, 'c')} 而 {@code smask} 是 {@code const char*}，
     * 所以第一个 {@code \0} 之后的字符<b>看不见</b>：{@code "c\0l"} 只装调用钩子，
     * {@code "l\0c"} 只装行钩子。实测两者打出来的计数完全相反。
     */
    private static int makeMask(LuaString smask, int count) {
        String s = smask.toCString().toJavaString();
        int mask = 0;
        if (s.indexOf('c') >= 0) {
            mask |= Lua.LUA_MASKCALL;
        }
        if (s.indexOf('r') >= 0) {
            mask |= Lua.LUA_MASKRET;
        }
        if (s.indexOf('l') >= 0) {
            mask |= Lua.LUA_MASKLINE;
        }
        if (count > 0) {
            mask |= Lua.LUA_MASKCOUNT;
        }
        return mask;
    }

    /**
     * {@code unmakemask}：掩码转回字符串。
     * <p>
     * 只输出 {@code c / r / l}——计数是单独一个返回值，不占字符。
     */
    private static String unmakeMask(int mask) {
        StringBuilder b = new StringBuilder();
        if ((mask & Lua.LUA_MASKCALL) != 0) {
            b.append('c');
        }
        if ((mask & Lua.LUA_MASKRET) != 0) {
            b.append('r');
        }
        if ((mask & Lua.LUA_MASKLINE) != 0) {
            b.append('l');
        }
        return b.toString();
    }

    /** {@code luaL_getsubtable} 的等价物，拿不到就建一张弱键表 */
    private static LuaTable hookTable(LuaState L) {
        LuaValue v = L.registry.get(HOOKKEY);
        if (v != null && v.tt == Lua.TTABLE) {
            return (LuaTable) v.o;
        }
        LuaTable t = new LuaTable();
        // 弱键：协程死后不该被钩子表吊住；以自己为元表是上游的写法
        t.set("__mode", LuaValue.ofString(LuaString.utf8("k")));
        t.setMetatable(t);
        L.registry.set(HOOKKEY, LuaValue.ofTable(t));
        return t;
    }

    private static int setHook(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        LuaHook func;
        int mask;
        int count;
        if (L.isNoneArg(arg + 1) || L.arg1(arg + 1).isNil()) {
            L.setTop(arg + 1);
            func = null;
            mask = 0;
            count = 0;
        } else {
            /*
             * 先取掩码<b>再</b>查第 1 个参数的类型：C 版
             * {@code luaL_checkstring(L, arg+2)} 在前，所以
             * {@code debug.sethook(1, 2)} 报的是「#2 不是字符串」，
             * 而不是「#1 不是函数」。
             */
            LuaString smask = LuaAux.checkString(L, arg + 2);
            LuaAux.checkType(L, arg + 1, Lua.TFUNCTION);
            count = (int) LuaAux.optInteger(L, arg + 3, 0);
            func = HOOKF;
            mask = makeMask(smask, count);
        }
        LuaTable ht = hookTable(L);
        ht.set(LuaValue.ofThread(L1), new LuaValue(L.arg1(arg + 1)));
        L1.setHook(func, mask, count);
        return 0;
    }

    private static int getHook(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        int mask = L1.getHookMask();
        LuaHook hook = L1.getHook();
        if (hook == null) {
            L.push(LuaValue.ofNil()); // luaL_pushfail
            return 1; // 没有钩子时<b>只有一个</b>返回值
        } else if (hook != HOOKF) {
            L.push(LuaValue.ofString(LuaString.utf8("external hook")));
        } else {
            LuaTable ht = hookTable(L);
            LuaValue f = ht.get(LuaValue.ofThread(L1)); // rawget 的等价物
            L.push((f == null) ? LuaValue.ofNil() : new LuaValue(f));
        }
        L.push(LuaValue.ofString(LuaString.utf8(unmakeMask(mask))));
        L.push(LuaValue.ofInteger(L1.getHookCount()));
        return 3;
    }

    /* ---------- ldo.c / ldebug.c：钩子的调用点 ---------- */

    /**
     * {@code ldo.c: luaD_hook}。
     * <p>
     * 两处「先扩大再收窄」是本函数的要害：
     * <ul>
     *   <li>进来时若 {@code L.top < ci.top} 就抬到 {@code ci.top}——Lua 帧的
     *       {@code top} 是「本帧可能用到的最高处」，此时栈上那些值都还活着，
     *       钩子（以及它内部可能的栈扩容）不能把它们盖掉；</li>
     *   <li>再保证 {@code ci.top} 至少是 {@code L.top + LUA_MINSTACK}——钩子自己
     *       要起新帧，得给它留出 C 函数保证的那 20 个槽。</li>
     * </ul>
     * 出去时要把这三个都还原：{@code L.top}、{@code ci.top}、
     * {@code CIST_HOOKED}。
     * <p>
     * <b>钩子抛错时不还原。</b>C 版在这里 {@code luaD_throw} 一跳到底，还原动作
     * 全被跳过；{@code allowhook} 与 {@code CIST_HOOKED} 要到
     * {@code luaD_pcall} 才恢复。这个差别是能观测到的：钩子函数出错时参考实现的
     * 回溯里那一层印成 {@code in hook '?'}，而「出错立刻清标记」会把它印成
     * {@code in function <文件:行>}。
     */
    static void dHook(LuaState L, int event, int line, int ftransfer, int ntransfer) {
        LuaHook hook = L.hook;
        if (hook == null || !L.allowHook) {
            return;
        }
        CallInfo ci = L.ci;
        int savedTop = L.top;
        int savedCiTop = ci.top;
        LuaDebug ar = new LuaDebug();
        ar.event = event;
        ar.currentline = line;
        ar.iCi = ci;
        ar.state = L;
        boolean tran = ntransfer != 0;
        if (tran) {
            ci.ftransfer = ftransfer;
            ci.ntransfer = ntransfer;
        }
        if (ci.isLua && L.top < ci.top) {
            L.top = ci.top;
        }
        L.checkStack(LuaState.MINSTACK);
        if (ci.top < L.top + LuaState.MINSTACK) {
            ci.top = L.top + LuaState.MINSTACK;
        }
        L.allowHook = false;
        ci.hooked = true;
        ci.tran = tran;
        boolean ok = false;
        try {
            hook.hook(L, ar);
            ok = true;
        } finally {
            if (ok) {
                L.allowHook = true;
                ci.top = savedCiTop;
                L.top = savedTop;
                ci.hooked = false;
                ci.tran = false;
            }
        }
    }

    /**
     * {@code ldo.c: luaD_hookcall}：Lua 函数的调用钩子。
     * <p>
     * 那句「{@code savedpc} 先加一再加回来」是因为钩子的约定是
     * 「{@code savedpc} 已经越过当前这条指令」——{@code debug.getinfo(2, 'l')}
     * 要报的是<b>调用发生的那一行</b>，而此时 {@code savedpc} 还停在
     * {@code OP_CALL} 上。
     */
    static void dHookCall(LuaState L, CallInfo ci) {
        L.oldpc = 0;
        if ((L.hookmask & Lua.LUA_MASKCALL) != 0) {
            int event = ci.tail ? Lua.LUA_HOOKTAILCALL : Lua.LUA_HOOKCALL;
            Proto p = ((LClosure) L.stack[ci.func].o).p;
            ci.savedpc++;
            dHook(L, event, -1, 1, p.numparams);
            ci.savedpc--;
        }
    }

    /**
     * {@code ldo.c: rethook}：返回钩子，并顺手更新 {@code oldpc}。
     * <p>
     * 变参函数要把 {@code ci.func} 先退回「虚拟 func」再算偏移：真实
     * {@code func} 与虚拟 {@code func} 之间隔着 {@code nextraargs + numparams + 1}
     * 格，照真实 {@code func} 算出来的 {@code ftransfer} 会是个负数。
     */
    static void rethook(LuaState L, CallInfo ci, int nres) {
        if ((L.hookmask & Lua.LUA_MASKRET) != 0) {
            int firstres = L.top - nres;
            int delta = 0;
            if (ci.isLua) {
                Proto p = ((LClosure) L.stack[ci.func].o).p;
                if (p.isVararg) {
                    delta = ci.nextraargs + p.numparams + 1;
                }
            }
            ci.func += delta;
            dHook(L, Lua.LUA_HOOKRET, -1, firstres - ci.func, nres);
            ci.func -= delta;
        }
        CallInfo prev = ci.previous;
        if (prev != null && prev.isLua) {
            L.oldpc = pcRel(prev.savedpc);
        }
    }

    /** {@code pcRel(pc, p)}：{@code p->code} 在本实现里恒为下标 0 */
    private static int pcRel(int pc) {
        return pc - 1;
    }

    /** {@code ldebug.c: changedline}：{@code oldpc} 到 {@code newpc} 之间换过行吗 */
    private static boolean changedLine(Proto p, int oldpc, int newpc) {
        if (p.sizelineinfo == 0) {
            return false; // 没有调试信息
        }
        if (newpc - oldpc < Proto.MAXIWTHABS / 2) {
            int delta = 0;
            int pc = oldpc;
            while (true) {
                if (pc + 1 >= p.sizelineinfo) {
                    break;
                }
                int lineinfo = p.lineinfo[++pc];
                if (lineinfo == Proto.ABSLINEINFO) {
                    break; // 算不出差值，落到下面显式比
                }
                delta += lineinfo;
                if (pc == newpc) {
                    return delta != 0;
                }
            }
        }
        return p.getFuncline(oldpc) != p.getFuncline(newpc);
    }

    /**
     * {@code lopcodes.h: isIT(i)}：这条指令的操作数个数由 {@code L->top} 决定吗。
     * <p>
     * 是的话 {@code L->top} 此刻是有意义的，不能拿 {@code ci->top} 盖掉；
     * 不是的话 {@code L->top} 只是「上次用到哪儿」，必须抬到 {@code ci->top}
     * 才能给钩子腾出干净的栈。
     */
    private static boolean isIT(int instr) {
        switch (OpCode.getOpCode(instr)) {
        case OpCode.OP_CALL:
        case OpCode.OP_TAILCALL:
        case OpCode.OP_RETURN:
        case OpCode.OP_SETLIST:
        case OpCode.OP_VARARGPREP:
            return OpCode.getArgB(instr) == 0;
        default:
            return false;
        }
    }

    /**
     * {@code ldebug.c: luaG_traceexec}：每条指令前查一次行/计数钩子。
     *
     * @param pc 即将执行的那条指令的下标
     * @return 新的 {@code trap}
     */
    static boolean traceExec(LuaState L, CallInfo ci, int pc) {
        int mask = L.hookmask;
        Proto p = ((LClosure) L.stack[ci.func].o).p;
        if ((mask & (Lua.LUA_MASKLINE | Lua.LUA_MASKCOUNT)) == 0) {
            ci.trap = false; // 不需要再停
            return false;
        }
        pc++; // 参考点永远是「下一条」
        ci.savedpc = pc;
        boolean counthook = (mask & Lua.LUA_MASKCOUNT) != 0 && (--L.hookcount == 0);
        if (counthook) {
            L.resetHookCount();
        } else if ((mask & Lua.LUA_MASKLINE) == 0) {
            return true; // 没有行钩子、计数也没到点，这次没事
        }
        if (!isIT(p.code[ci.savedpc - 1])) {
            L.top = ci.top;
        }
        if (counthook) {
            dHook(L, Lua.LUA_HOOKCOUNT, -1, 0, 0);
        }
        if ((mask & Lua.LUA_MASKLINE) != 0) {
            int oldpc = (L.oldpc < p.sizecode) ? L.oldpc : 0;
            int npci = pcRel(pc);
            if (npci <= oldpc || changedLine(p, oldpc, npci)) {
                dHook(L, Lua.LUA_HOOKLINE, p.getFuncline(npci), 0, 0);
            }
            L.oldpc = npci;
        }
        return true;
    }

    /**
     * {@code ldebug.c: luaG_tracecall}：进一个 Lua 函数时先报调用钩子。
     * <p>
     * 变参函数这里返回 {@code false}（把 {@code trap} 关掉），它的调用钩子推迟到
     * {@code OP_VARARGPREP} 之后——那时参数才归位，报出来的
     * {@code numparams} 才对得上。{@link Vm} 里 {@code OP_VARARGPREP} 之后的
     * {@code updatetrap} 会把 {@code trap} 从帧里读回来重新置 1。
     */
    static boolean traceCall(LuaState L, CallInfo ci) {
        Proto p = ((LClosure) L.stack[ci.func].o).p;
        ci.trap = true;
        if (ci.savedpc == 0) { // 第一条指令（不是从 yield 恢复的）
            if (p.isVararg) {
                return false;
            }
            dHookCall(L, ci);
        }
        return true;
    }

    /* ==================== traceback ==================== */

    /**
     * {@code LEVELS1 / LEVELS2}：回溯太长时只留头尾。
     * <p>
     * 上游是 10 和 11——<b>加起来才是 21</b>，且头尾不等长。凭印象写成 12/10
     * 会让「跳过多少层」那个数字差 1，而且只在深栈上才暴露。
     */
    private static final int LEVELS1 = 10;
    private static final int LEVELS2 = 11;

    private static int lastLevel(LuaState L) {
        LuaDebug ar = new LuaDebug();
        int li = 1;
        int le = 1;
        while (getStack(L, le, ar)) {
            li = le;
            le *= 2;
        }
        while (li < le) {
            int m = (li + le) / 2;
            if (getStack(L, m, ar)) {
                li = m + 1;
            } else {
                le = m;
            }
        }
        return le - 1;
    }

    private static int traceback(LuaState L) {
        int[] argHolder = new int[1];
        LuaState L1 = getThread(L, argHolder);
        int arg = argHolder[0];
        LuaValue m = L.isNoneArg(arg + 1) ? LuaValue.ofNil() : L.arg1(arg + 1);
        if (!m.isString() && !m.isNumber() && !m.isNil()) {
            L.push(new LuaValue(m)); // 非字符串原样返回
            return 1;
        }
        // luaL_traceback 用 strlen(msg) 取长度：  之后的内容不进回溯
        LuaString msg = m.isNil() ? null : LuaAux.asString(m).toCString();
        int level = (int) LuaAux.optInteger(L, arg + 2, (L == L1) ? 1 : 0);
        L.push(LuaValue.ofString(traceback(L, L1, msg, level)));
        return 1;
    }

    /**
     * {@code lauxlib.c: luaL_traceback} 的字符串版本。
     * <p>
     * 抽出来是因为除了 {@code debug.traceback} 还有第二个调用方：{@code lua.c} 的
     * {@code msghandler}（未捕获错误的默认处理函数）也调它。两边共一份，
     * 免得「{@code debug.traceback} 对的、未捕获错误的回溯少一层」这种事发生。
     * <p>
     * 注意 {@code L} 与 {@code L1} 的分工：回溯的<b>文本</b>建在 {@code L} 上，
     * 但<b>帧</b>取自 {@code L1}——{@code debug.traceback(co)} 就是靠这个区别
     * 拿到协程自己的栈。
     */
    static LuaString traceback(LuaState L, LuaState L1, LuaString msg, int level) {
        /*
         * 用字节缓冲而不是 StringBuilder：msg 与 short_src 都是 Lua 字节串，
         * 可以是任意字节。走 Java String 会先按 UTF-8 解码（非法字节变
         * U+FFFD）再编码回去，内容就变了 —— 实测
         * debug.traceback("\xff") 一度返回 "\xef\xbf\xbd" 打头的一串。
         */
        LuaAux.Buf b = new LuaAux.Buf();
        if (msg != null) {
            b.addString(msg);
            b.addChar('\n');
        }
        b.addAscii("stack traceback:");
        int last = lastLevel(L1);
        int limit2show = (last - level > LEVELS1 + LEVELS2) ? LEVELS1 : -1;
        LuaDebug ar = new LuaDebug();
        while (getStack(L1, level++, ar)) {
            if (limit2show-- == 0) {
                int n = last - level - LEVELS2 + 1;
                b.addAscii("\n\t...\t(skipping ");
                b.addAscii(Integer.toString(n));
                b.addAscii(" levels)");
                level += n;
            } else {
                getInfo(L1, "Slnt", ar);
                b.addAscii("\n\t");
                b.addString(ar.shortSrc);
                if (ar.currentline <= 0) {
                    b.addAscii(": in ");
                } else {
                    b.addAscii(":");
                    b.addAscii(Integer.toString(ar.currentline));
                    b.addAscii(": in ");
                }
                b.addString(pushFuncName(L, ar));
                if (ar.istailcall) {
                    b.addAscii("\n\t(...tail calls...)");
                }
            }
        }
        return b.result();
    }

    /**
     * {@code lauxlib.c: pushfuncname} 的字符串版本。
     * <p>
     * <b>全局名优先于代码里的名字</b>：{@code error} 在代码里是
     * {@code global 'error'}（{@code GETTABUP _ENV}），但回溯打印的是
     * {@code function 'error'}——第一支 {@code pushglobalfuncname} 在
     * {@code _LOADED._G} 里按<b>值相等</b>反查到了这个名字。所以这里必须先查全局，
     * 只查 {@code ar.namewhat} 会整体差一层「修饰语」。
     */
    private static LuaString pushFuncName(LuaState L, LuaDebug ar) {
        /*
         * 同样返回字节串：{@code function <shortSrc:linedefined>} 里嵌的是
         * chunk 名，可以是任意字节。走 Java String 再 {@code addAscii} 会把
         * 0xFF 截成 0xFD、把 U+4E2D 截成 '-'（实测 {@code load(f,"=\xe4\xb8\xad")}
         * 的回溯打印成 {@code function <-:1>}）。
         */
        String g = globalFuncName(L, ar);
        if (g != null) {
            return LuaString.latin1("function '" + g + "'");
        }
        if (ar.namewhat != null && !ar.namewhat.isEmpty()) {
            return LuaString.latin1(ar.namewhat + " '" + ar.name + "'");
        }
        if ("main".equals(ar.what)) {
            return LuaString.latin1("main chunk");
        }
        if (!"C".equals(ar.what)) {
            return LuaString.latin1("function <").concat(ar.shortSrc)
                    .concat(LuaString.latin1(":" + ar.linedefined + ">"));
        }
        return LuaString.latin1("?");
    }

    /**
     * {@code lauxlib.c: pushglobalfuncname}：在 {@code _LOADED} 里按值反查两层的名字。
     * <p>
     * 与 {@code LuaState.globalFuncName} 是同一件事，只是<b>取函数的来源不同</b>——
     * 那边取「当前 C 帧的函数」，这里取 {@code ar.iCi} 那一帧的，而后者可能属于
     * 另一个线程（{@code debug.traceback(co)}）。
     */
    private static String globalFuncName(LuaState L, LuaDebug ar) {
        if (ar.iCi == null || ar.state == null) {
            return null;
        }
        LuaValue f = ar.state.stack[ar.iCi.func];
        String s = LuaState.findFieldName(L.loadedTable(), f, 2);
        if (s == null) {
            return null;
        }
        // 上游找到的是 "_G.error"，把前缀去掉
        return s.startsWith("_G.") ? s.substring(3) : s;
    }

    /* ==================== 杂项 ==================== */

    /** {@code lua_setcstacklimit}：本实现没有 C 栈深度限制，一律给上限 */
    private static final int MAXCCALLS = 200;

    private static int setCStackLimit(LuaState L) {
        LuaAux.checkInteger(L, 1);
        L.push(LuaValue.ofInteger(MAXCCALLS));
        return 1;
    }

    /** {@code ldblib.c: db_debug} 里 {@code fgets} 的缓冲区大小 */
    private static final int DB_BUFSIZ = 250;

    /** {@code "lua_debug> "}：注意末尾那个空格，且<b>没有</b>换行 */
    private static final byte[] DB_PROMPT = "lua_debug> ".getBytes(StandardCharsets.UTF_8);

    /**
     * {@code db_debug}：交互调试器。
     * <p>
     * 三处必须与 C 一字不差，否则逐字节比对立刻红：
     * <ul>
     *   <li><b>每次读之前先往 stderr 打 {@code "lua_debug> "}</b>
     *       （{@code lua_writestringerror}，无换行、无缓冲）。原来漏了这一句，
     *       于是参考实现的输出里凭空多一个 {@code lua_debug> }；</li>
     *   <li><b>是循环，不是读一行就返回</b>：C 一直读到 EOF 或某一行正好是
     *       {@code "cont\n"}，中间每行都当 chunk 执行。原来只跑第一行；</li>
     *   <li><b>命令出错时把消息打到 stderr</b> 并<b>继续</b>下一轮，不是抛出去。
     *       C 是 {@code lua_writestringerror("%s\n", luaL_tolstring(L, -1))}。</li>
     * </ul>
     * <p>
     * 命令的 chunk 名是 {@code =(debug command)}，且每次执行完
     * {@code lua_settop(L, 0)}——本实现的 {@link LuaState#setTop} 与 C 一样是
     * 相对当前帧的，所以直接写 0。
     * <p>
     * 差分用例里 stdin 是 {@code /dev/null}（见 {@code ReferenceLua#runBytes}），
     * 所以第一轮就 EOF：表现为「只打一个提示符然后返回」。
     */
    private static int debugFn(LuaState L) {
        byte[] buf = new byte[DB_BUFSIZ];
        for (;;) {
            writeErr(L, DB_PROMPT);
            int n = fgets(L.in, buf);
            if (n < 0 || isCont(buf, n)) {
                return 0;
            }
            byte[] src = java.util.Arrays.copyOf(buf, n);
            LuaValue err;
            try {
                err = L.runProtected(src, "=(debug command)");
            } catch (LuaError e) {
                // luaL_loadbuffer 那一步就失败：消息同样要打出来
                err = e.value;
            }
            if (err != null) {
                writeErr(L, LuaAux.toLString(L, err).bytes);
                writeErr(L, new byte[] { '\n' });
            }
            L.setTop(0);
        }
    }

    /**
     * C 的 {@code fgets}：最多读 {@code buf.length - 1} 个字节，遇到 {@code '\n'}
     * <b>连同换行一起</b>收进来；EOF 时一个字节都没读到才返回 -1。
     *
     * @return 读到的字节数，或 -1 表示 EOF
     */
    private static int fgets(java.io.InputStream in, byte[] buf) {
        int n = 0;
        try {
            while (n + 1 < buf.length) {
                int c = in.read();
                if (c < 0) {
                    break;
                }
                buf[n++] = (byte) c;
                if (c == '\n') {
                    break;
                }
            }
        } catch (java.io.IOException e) {
            // C 的 fgets 出错也返回 NULL；n == 0 即等价
        }
        return (n == 0) ? -1 : n;
    }

    /** {@code strcmp(buffer, "cont\n") == 0} */
    private static boolean isCont(byte[] buf, int n) {
        return n == 5 && buf[0] == 'c' && buf[1] == 'o' && buf[2] == 'n'
                && buf[3] == 't' && buf[4] == '\n';
    }

    /** {@code lua_writestringerror}：写字节 + 立刻 flush（C 的 stderr 无缓冲） */
    private static void writeErr(LuaState L, byte[] b) {
        L.err.write(b, 0, b.length);
        L.err.flush();
    }
}
