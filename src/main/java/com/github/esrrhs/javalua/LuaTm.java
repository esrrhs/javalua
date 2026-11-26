package com.github.esrrhs.javalua;

/**
 * 元方法 —— 对应 {@code ltm.c}。
 * <p>
 * 元表查找有两条路径，区别必须保留：
 * <ul>
 *   <li><b>表 / userdata</b>：元表挂在对象自己身上；</li>
 *   <li><b>其余类型</b>（nil、number、string…）：整个类型共用一个元表，
 *       存在 {@link LuaState} 里，只能通过 {@code debug.setmetatable} 设置。
 *       字符串是个特例——所有字符串共享 {@code string} 库的元表。</li>
 * </ul>
 * <p>
 * {@code LUA_COMPAT_LT_LE} 的行为照搬：缺 {@code __le} 时退化成
 * {@code !(p2 < p1)}，用 {@code CIST_LEQ} 标记这次替换（本实现不做 yield，
 * 标记退化为 {@link CallInfo#leq}，但保留以对应上游语义）。
 */
final class LuaTm {

    private LuaTm() {
    }

    /* ==================== 查找 ==================== */

    /**
     * {@code luaT_gettmbyobj}：按对象取元方法。
     *
     * @return 元方法值；不存在返回 {@code null}
     */
    static LuaValue getTmByObj(LuaState L, LuaValue o, int event) {
        LuaTable mt = metatableOf(L, o);
        if (mt == null) {
            return null;
        }
        LuaValue v = mt.get(LuaValue.ofString(L.tmName[event]));
        return (v == null || v.isNil()) ? null : v;
    }

    /**
     * {@code luaT_gettmbyobj} 的前半段：取一个值的元表。
     * <p>
     * 表与 userdata 的元表<b>在对象自己身上</b>，其余类型共享一张「类型元表」。
     * 这段判断之前在 {@code BaseLib} / {@code LuaState} / 这里各写了一份，
     * userdata 那一支只有这份有——于是 {@code io.stdout} 的
     * {@code __name} / {@code __tostring} 走别的路径时全部落空。
     */
    static LuaTable metatableOf(LuaState L, LuaValue v) {
        switch (v.baseType()) {
        case Lua.TTABLE:
            return v.asTable().getMetatable();
        case Lua.TUSERDATA:
            return ((LuaUserdata) v.o).metatable;
        default:
            return L.typeMetatable(v.baseType());
        }
    }

    /**
     * {@link #metatableOf} 的反向操作 —— {@code lapi.c: lua_setmetatable} 里
     * 「按类型分派到三处」的那半段。
     * <p>
     * 之前只有 {@link BaseLib} 里的 {@code setmetatable} 自己写了这段分派，
     * 于是 {@code debug.setmetatable(1, mt)} 这种「给整个类型设元表」的路径
     * 没有对应物。抽到这里，与 {@link #metatableOf} 成对，改一处不会漏另一处。
     */
    static void setMetatable(LuaState L, LuaValue v, LuaTable mt) {
        switch (v.baseType()) {
        case Lua.TTABLE:
            v.asTable().setMetatable(mt);
            v.asTable().markFinalizable(L);
            break;
        case Lua.TUSERDATA:
            ((LuaUserdata) v.o).metatable = mt;
            ((LuaUserdata) v.o).markFinalizable(L);
            break;
        default:
            L.setTypeMetatable(v.baseType(), mt);
            break;
        }
    }

    /**
     * {@code luaT_objtypename}：{@code __name} 元字段优先于标准类型名。
     * <p>
     * 这是 {@code print(#io.stdout)} 报
     * {@code attempt to get length of a FILE* value} 的那条路径——
     * 光有 {@code typeName} 会报成 {@code userdata}。
     * <p>
     * <b>只有表与 userdata 认 {@code __name}</b>：C 版的条件是
     * {@code ttistable(o) || ttisfulluserdata(o)}，其余类型<b>连类型元表都不看</b>。
     * 实测 {@code debug.setmetatable(nil, {__name = "NilT"})} 之后 {@code nil + 1}
     * 参考实现报的仍是 {@code ... on a nil value} —— 走
     * {@link #metatableOf} 会把类型元表也算进来，于是我们多报了 {@code NilT}。
     * <p>
     * <b>{@code __name} 还走 C 字符串语义</b>：{@code ltm.c: luaT_objtypename} 拿
     * 到的是 {@code getstr()} 那个 {@code const char *}，而 {@code luaG_typeerror}
     * 用 {@code "%s"} 拼进消息 —— 于是内容在第一个 {@code \0} 处被砍断。实测
     * {@code setmetatable({}, {__name = "A\0B"}) + 1}：参考实现报
     * <b>{@code attempt to perform arithmetic on a A value}</b>，只有 {@code A}。
     * （{@code luaL_tolstring} 那条路上同样的问题另见 {@link LuaString#toCString}。）
     */
    static String objTypeName(LuaState L, LuaValue v) {
        LuaTable mt = null;
        switch (v.baseType()) {
        case Lua.TTABLE:
            mt = v.asTable().getMetatable();
            break;
        case Lua.TUSERDATA:
            mt = ((LuaUserdata) v.o).metatable;
            break;
        default:
            break; // 其余类型不看 __name
        }
        if (mt != null) {
            LuaValue nm = mt.get(LuaString.latin1("__name"));
            if (nm != null && nm.isString()) {
                // Latin-1 往返：这个名字会走 luaG_runerror 的 %s 拼回 Lua 字符串
                return nm.asString().toCString().toLatin1String();
            }
        }
        return v.typeName();
    }

    /**
     * {@code fasttm}：表的元方法快速路径。
     * <p>
     * 与 {@link #getTmByObj} 的差别只是省掉了类型分派——调用方已经确认了是表。
     */
    static LuaValue fastTm(LuaTable mt, int event, LuaState L) {
        if (mt == null) {
            return null;
        }
        LuaValue v = mt.get(LuaValue.ofString(L.tmName[event]));
        return (v == null || v.isNil()) ? null : v;
    }

    /* ==================== 调用 ==================== */

    /**
     * {@code luaT_callTM}：调 {@code f(p1, p2, p3)}，不要结果。
     * <p>
     * 压栈位置是当前 {@code L.top}——C 版靠 {@code EXTRA_STACK} 保证金，
     * 这里显式 {@code ensureSlots}。
     */
    /**
     * 与 {@link #callTm} 同形，但 {@code forceYy} 为真时<b>一定</b>用可 yield 的
     * {@code luaD_call}。
     *
     * @see #callTm(LuaState, LuaValue, LuaValue, LuaValue)
     */
    static void callTm(LuaState L, LuaValue f, LuaValue p1, LuaValue p2, LuaValue p3) {
        int func = L.top;
        L.ensureSlots(func + 4);
        L.stack[func].set(f);
        L.stack[func + 1].set(p1);
        L.stack[func + 2].set(p2);
        L.stack[func + 3].set(p3);
        L.top = func + 4;
        L.callTmMightYield(func, 0);
    }

    /**
     * {@code luaT_callTMres}：调 {@code f(p1, p2)}，把结果写到 {@code resIdx}。
     * <p>
     * 结果先落在 {@code func} 位置（因为 {@code poscall} 会把第一个结果搬到
     * {@code ci.func}），再拷到 {@code resIdx}。这两步不能合并：{@code resIdx}
     * 是调用方寄存器，可能就在被覆盖的范围内。
     * <p>
     * <b>能不能 yield 要看当前帧</b>：C 版这两句
     * <pre>
     * if (isLuacode(L-&gt;ci)) luaD_call(L, func, 1);
     * else luaD_callnoyield(L, func, 1);
     * </pre>
     * 意思是「元方法只有在被 Lua 代码触发时才可以 yield」。少了这一刀，
     * 从 C 函数（比如 {@code string.gsub} 查表做替换）触发的元方法里 yield
     * 会一路穿过那个 C 帧；而那个帧<b>没注册续体</b>，恢复时
     * {@code finishCcall} 拿到 {@code k == null} —— Java 里是 NPE，
     * C 里是段错误。参考实现报的是干净的
     * {@code attempt to yield across a C-call boundary}，且能被 pcall 接住。
     */
    static void callTmRes(LuaState L, LuaValue f, LuaValue p1, LuaValue p2, int resIdx) {
        int func = L.top;
        L.ensureSlots(Math.max(func + 4, resIdx + 1));
        L.stack[func].set(f);
        L.stack[func + 1].set(p1);
        L.stack[func + 2].set(p2);
        L.top = func + 3;
        L.callTmMightYield(func, 1);
        L.stack[resIdx].set(L.stack[--L.top]);
    }

    /* ==================== 算术 / 位运算 ==================== */

    /** {@code callbinTM}：依次试两个操作数的元方法 */
    private static boolean callBinTm(LuaState L, LuaValue p1, LuaValue p2, int resIdx, int event) {
        LuaValue tm = getTmByObj(L, p1, event);
        if (tm == null) {
            tm = getTmByObj(L, p2, event);
        }
        if (tm == null) {
            return false;
        }
        callTmRes(L, tm, p1, p2, resIdx);
        return true;
    }

    /** {@code luaT_trybinTM}：找不到元方法就报「对 X 做算术/位运算」 */
    static void tryBinTm(LuaState L, LuaValue p1, LuaValue p2, int resIdx, int event) {
        if (!callBinTm(L, p1, p2, resIdx, event)) {
            switch (event) {
            case Tm.TM_BAND:
            case Tm.TM_BOR:
            case Tm.TM_BXOR:
            case Tm.TM_SHL:
            case Tm.TM_SHR:
            case Tm.TM_BNOT:
                if (Vm.toNumber(p1) != null && Vm.toNumber(p2) != null) {
                    /*
                     * {@code ldebug.c: luaG_tointerror}：
                     * <pre>
                     * if (!luaV_tointegerns(p1, &amp;temp, LUA_FLOORN2I))
                     *   p2 = p1;
                     * luaG_runerror(L, "number%s has no integer representation",
                     *               varinfo(L, p2));
                     * </pre>
                     * 三处都不能想当然：
                     * <ul>
                     *   <li><b>报哪一个</b>：先试 {@code p1}，它转不成整数才报
                     *       {@code p1}，否则报 {@code p2}。
                     *       {@code math.huge << 1} 报的是左边那个 inf。</li>
                     *   <li><b>模式是 {@code F2I_EQ}，不是 {@code F2I_FLOOR}</b>。
                     *       {@code lvm.h} 里默认 {@code LUA_FLOORN2I == F2Ieq}
                     *       —— 名字带 floor，语义其实是「只接受整数值，不取整」。
                     *       按 FLOOR 判的话 {@code 1.5 << 3} 里 {@code 1.5}
                     *       会被当成「能转」，于是去报右边的 {@code 3}；参考实现
                     *       报的是左边那个 {@code 1.5}。这个坑是「运算符 × 类型」
                     *       全矩阵扫描撞出来的，手写的用例只有 inf/nan 那两种
                     *       极端值，两种模式在它们上面结论一样。</li>
                     *   <li><b>带 {@code varinfo} 后缀</b>：值若在寄存器里，
                     *       参考实现还会反推它是怎么来的——上面那句实测是
                     *       {@code number (field 'huge') has no integer
                     *       representation}（{@code math.huge} 是
                     *       {@code OP_GETFIELD} 取来的）。少了后缀，上游
                     *       {@code math.lua} 的 {@code checkcompt("field
                     *       'huge'", "return math.huge << 1")} 就对不上。</li>
                     * </ul>
                     */
                    LuaValue bad = (Vm.toInteger(p1, Arith.F2I_EQ) == null) ? p1 : p2; /* 5.3 tointeger */
                    throw L.runError("number" + L.varInfo(bad)
                            + " has no integer representation");
                }
                throw L.typeError(Vm.toNumber(p1) != null ? p2 : p1, "perform bitwise operation on");
            default:
                /* 5.3.4 luaG_opinterror: tonumber() includes numeric strings */
                throw L.typeError(Vm.toNumber(p1) != null ? p2 : p1, "perform arithmetic on");
            }
        }
    }

    /** {@code luaT_tryconcatTM}：栈顶两个值拼不起来 */
    static void tryConcatTm(LuaState L) {
        int t = L.top;
        if (!callBinTm(L, L.stack[t - 2], L.stack[t - 1], t - 2, Tm.TM_CONCAT)) {
            throw L.concatError(L.stack[t - 2], L.stack[t - 1]);
        }
    }

    /* ==================== 比较 ==================== */

    /** {@code luaT_callorderTM} */
    static boolean callOrderTm(LuaState L, LuaValue p1, LuaValue p2, int event) {
        int t = L.top;
        L.ensureSlots(t + 1);
        if (callBinTm(L, p1, p2, t, event)) {
            return L.stack[t].toBoolean();
        }
        // LUA_COMPAT_LT_LE：没有 __le 时用 !(p2 < p1) 顶替
        if (event == Tm.TM_LE) {
            L.ci.leq = true;
            if (callBinTm(L, p2, p1, t, Tm.TM_LT)) {
                L.ci.leq = false;
                return !L.stack[t].toBoolean();
            }
        }
        throw L.orderError(p1, p2);
    }

    /* ==================== 可变参数 ==================== */

    /**
     * {@code luaT_adjustvarargs}：把固定参数搬到栈顶，可变参数留在原处。
     * <p>
     * 搬完之后 {@code ci.func} 前移 {@code actual+1}，于是「多余参数」变成
     * {@code ci.func - nextra .. ci.func - 1}，正好在帧的下方——这就是
     * {@code OP_VARARG} 取值的地方。
     */
    /**
     * Lua 5.3.4 {@code adjust_varargs}: copy fixed params to top; leave extras
     * below the new base. Returns the new base index.
     */
    static int adjustVarargs(LuaState L, Proto p, int actual) {
        int nfixargs = p.numparams;
        int fixed = L.top - actual; // first fixed argument
        int base = L.top; // final position of first argument
        int i = 0;
        for (; i < nfixargs && i < actual; i++) {
            L.stack[L.top++].set(L.stack[fixed + i]);
            L.stack[fixed + i].setNil();
        }
        for (; i < nfixargs; i++) {
            L.stack[L.top++].setNil();
        }
        return base;
    }

    /** Lua 5.3.4 {@code OP_VARARG}. */
    static void getVarargs(LuaState L, CallInfo c, int where, int wanted) {
        LClosure cl = (LClosure) L.stack[c.func].o;
        int numparams = cl.p.numparams;
        int n = (c.base - c.func) - numparams - 1;
        if (n < 0) {
            n = 0;
        }
        if (wanted < 0) {
            wanted = n;
            L.ensureSlots(where + n);
            L.top = where + n;
        }
        L.ensureSlots(where + wanted);
        int j = 0;
        for (; j < wanted && j < n; j++) {
            L.stack[where + j].set(L.stack[c.base - n + j]);
        }
        for (; j < wanted; j++) {
            L.stack[where + j].setNil();
        }
    }

}
