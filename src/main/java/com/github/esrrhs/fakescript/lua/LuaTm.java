package com.github.esrrhs.fakescript.lua;

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
        LuaTable mt;
        switch (o.baseType()) {
        case Lua.TTABLE:
            mt = o.asTable().getMetatable();
            break;
        default:
            mt = L.typeMetatable(o.baseType());
            break;
        }
        if (mt == null) {
            return null;
        }
        LuaValue v = mt.get(LuaValue.ofString(L.tmName[event]));
        return (v == null || v.isNil()) ? null : v;
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
    /** 两参版本：{@code __close(obj, err)} 只有两个参数 */
    static void callTm(LuaState L, LuaValue f, LuaValue p1, LuaValue p2) {
        int func = L.top;
        L.ensureSlots(func + 3);
        L.stack[func].set(f);
        L.stack[func + 1].set(p1);
        L.stack[func + 2].set(p2);
        L.top = func + 3;
        L.call(func, 0);
    }

    static void callTm(LuaState L, LuaValue f, LuaValue p1, LuaValue p2, LuaValue p3) {
        int func = L.top;
        L.ensureSlots(func + 4);
        L.stack[func].set(f);
        L.stack[func + 1].set(p1);
        L.stack[func + 2].set(p2);
        L.stack[func + 3].set(p3);
        L.top = func + 4;
        L.call(func, 0);
    }

    /**
     * {@code luaT_callTMres}：调 {@code f(p1, p2)}，把结果写到 {@code resIdx}。
     * <p>
     * 结果先落在 {@code func} 位置（因为 {@code poscall} 会把第一个结果搬到
     * {@code ci.func}），再拷到 {@code resIdx}。这两步不能合并：{@code resIdx}
     * 是调用方寄存器，可能就在被覆盖的范围内。
     */
    static void callTmRes(LuaState L, LuaValue f, LuaValue p1, LuaValue p2, int resIdx) {
        int func = L.top;
        L.ensureSlots(Math.max(func + 4, resIdx + 1));
        L.stack[func].set(f);
        L.stack[func + 1].set(p1);
        L.stack[func + 2].set(p2);
        L.top = func + 3;
        L.call(func, 1);
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
                if (p1.isNumber() && p2.isNumber()) {
                    throw L.runError("number has no integer representation");
                }
                throw L.typeError(p1.isNumber() ? p2 : p1, "perform bitwise operation on");
            default:
                throw L.typeError(p1.isNumber() ? p2 : p1, "perform arithmetic on");
            }
        }
    }

    /** {@code luaT_trybinassocTM}：{@code flip} 表示两个操作数在字节码里是反着放的 */
    static void tryBinAssocTm(LuaState L, LuaValue p1, LuaValue p2, boolean flip, int resIdx,
            int event) {
        if (flip) {
            tryBinTm(L, p2, p1, resIdx, event);
        } else {
            tryBinTm(L, p1, p2, resIdx, event);
        }
    }

    /** {@code luaT_trybiniTM}：第二个操作数是立即整数 */
    static void tryBinITm(LuaState L, LuaValue p1, long i2, boolean flip, int resIdx, int event) {
        tryBinAssocTm(L, p1, LuaValue.ofInteger(i2), flip, resIdx, event);
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

    /** {@code luaT_callorderiTM}：第二个操作数是立即数 */
    static boolean callOrderITm(LuaState L, LuaValue p1, long v2, boolean flip, boolean isFloat,
            int event) {
        LuaValue aux = isFloat ? LuaValue.ofFloat(v2) : LuaValue.ofInteger(v2);
        if (flip) {
            return callOrderTm(L, aux, p1, event);
        }
        return callOrderTm(L, p1, aux, event);
    }

    /* ==================== to-be-closed ==================== */

    /**
     * {@code callclosemethod} 的入口：先 {@code checkclosemth}，再
     * {@code __close(obj, errObj)}。
     * <p>
     * 没有 {@code __close} 就是一个运行时错误——这正是
     * {@code local x <close> = 1} 报「non-closable value」的来源。
     * 报错时要带上<b>变量名</b>（{@code luaG_findlocal}），所以这里必须拿到
     * 寄存器序号，光有值是不够的。
     *
     * @param varIdx {@code luaG_findlocal} 用的序号：{@code 槽位 - ci.func}
     */
    static void callCloseMethod(LuaState L, LuaValue obj, LuaValue errObj, int varIdx) {
        LuaValue tm = getTmByObj(L, obj, Tm.TM_CLOSE);
        if (tm == null || tm.isNil()) {
            LuaString vn = L.localNameAt(varIdx);
            throw L.runError("variable '" + ((vn == null) ? "?" : vn.toJavaString())
                    + "' got a non-closable value");
        }
        // __close 只收 (self, err) 两个参数；多传一个会把 err 顶成 self
        callTm(L, tm, obj, errObj);
    }

    /* ==================== 可变参数 ==================== */

    /**
     * {@code luaT_adjustvarargs}：把固定参数搬到栈顶，可变参数留在原处。
     * <p>
     * 搬完之后 {@code ci.func} 前移 {@code actual+1}，于是「多余参数」变成
     * {@code ci.func - nextra .. ci.func - 1}，正好在帧的下方——这就是
     * {@code OP_VARARG} 取值的地方。
     */
    static void adjustVarargs(LuaState L, int nfixparams, CallInfo c, Proto p) {
        int actual = L.top - c.func - 1;
        int nextra = actual - nfixparams;
        c.nextraargs = nextra;
        L.ensureSlots(c.top + p.maxstacksize + 1);
        L.stack[L.top++].set(L.stack[c.func]); // 把函数拷到栈顶
        for (int i = 1; i <= nfixparams; i++) {
            L.stack[L.top++].set(L.stack[c.func + i]);
            L.stack[c.func + i].setNil(); // 抹掉原位置
        }
        c.func += actual + 1;
        c.top += actual + 1;
    }

    /** {@code luaT_getvarargs}：取 {@code wanted} 个可变参数写到 {@code where} */
    static void getVarargs(LuaState L, CallInfo c, int where, int wanted) {
        int nextra = c.nextraargs;
        if (wanted < 0) {
            wanted = nextra;
            L.ensureSlots(where + nextra);
            L.top = where + nextra; // 下一条指令要用到 top
        }
        L.ensureSlots(where + wanted);
        int i = 0;
        for (; i < wanted && i < nextra; i++) {
            L.stack[where + i].set(L.stack[c.func - nextra + i]);
        }
        for (; i < wanted; i++) {
            L.stack[where + i].setNil();
        }
    }
}
