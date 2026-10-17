package com.github.esrrhs.fakescript.lua;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 虚拟机 —— 对应 {@code lvm.c}。
 * <p>
 * 主循环用「一个 for + 一个 switch」实现 C 版的 computed goto。两处跳转靠标签模拟：
 * <ul>
 *   <li>{@code goto startfunc}（调进一个 Lua 函数）→ {@code continue outer}，
 *       由 {@code start} 标志重新装载 {@code cl / k / base / pc}；</li>
 *   <li>{@code goto ret}（从 Lua 函数返回）→ 回到 {@code ci.previous} 的帧继续跑。</li>
 * </ul>
 * 这与 C 版「Lua 调用 Lua 不消耗 C 栈帧」的做法一致，代价是返回时要靠
 * {@code CIST_FRESH} 判断该在哪一层退出。
 * <p>
 * <b>算术不做字符串转换。</b>这一点容易被直觉带偏：{@code "10" + 1 == 11}
 * 并不是 VM 做的，而是 {@code lstrlib.c} 给字符串元表挂的 {@code __add}
 * 先 {@code lua_stringtonumber} 再 {@code lua_arith}。VM 里所有算术用的都是
 * {@code tonumberns} / {@code tointegerns}（<b>不</b>带字符串转换）——
 * 照手册或照别的语言习惯写成「先转数字再算」，会在字符串有 {@code __add}
 * 但转换失败时给出与参考实现不同的报错信息。
 * <p>
 * <b>算术指令后面必定跟一条 {@code OP_MMBIN*}</b>：算成就 {@code pc++} 跳过它，
 * 算不成（类型不对）就自然落到它上面去派发元方法。这个「成功才跳过」的约定
 * 是整套算术指令的隐式契约，写漏一个 {@code pc++} 都会在正确路径上多跑一次元方法。
 */
public final class Vm {

    private Vm() {
    }

    /** {@code MAXTAGLOOP}：{@code __index} / {@code __newindex} 链的循环上限 */
    private static final int MAX_TAG_LOOP = 2000;

    /* ================================================================
     * 主循环
     * ================================================================ */

    static void execute(LuaState L, CallInfo ci0) {
        CallInfo ci = ci0;
        LClosure cl = null;
        LuaValue[] k = null;
        int[] code = null;
        int base = 0;
        int pc = 0;
        boolean start = true;

        /*
         * {@code goto ret} 与 {@code goto startfunc} 在 C 版是两个标签
         * （{@code returning:} 与 {@code startfunc:}），差别只有一句：前者
         * <b>不</b>重新装载 {@code trap}，后者从 {@code L->hookmask} 重载。
         * 这里合进同一个 {@code outer} 循环，就得用两个标志把它们分开——
         * 一律按 {@code startfunc} 处理的话，从内层函数返回时会把上层帧的
         * {@code trap} 冲掉，返回钩子之后的行钩子就没了。
         */
        boolean ret = false;

        /**
         * {@code lvm.c} 的 {@code trap}：要不要在每条指令前调
         * {@code luaG_traceexec}。钩子没开时它恒为 0，这是「不开钩子就没有
         * 调试开销」的兑现方式。
         */
        boolean trap = false;

        outer:
        for (;;) {
            if (start || ret) {
                if (start) {
                    trap = L.hookmask != 0; // 只有 startfunc 才重新装载
                }
                start = false;
                ret = false;
                cl = (LClosure) L.stack[ci.func].o;
                k = cl.p.k;
                code = cl.p.code;
                pc = ci.savedpc;
                base = ci.func + 1;
                if (trap) {
                    trap = DebugLib.traceCall(L, ci);
                }
            }
            for (;;) {
                /*
                 * {@code vmfetch()}：钩子（与栈重分配）就在这一个点上被检查。
                 * {@code traceExec} 可能改 {@code ci.top}，所以 {@code base}
                 * 要在它之后重算。
                 */
                if (trap) {
                    trap = DebugLib.traceExec(L, ci, pc);
                    base = ci.func + 1;
                }
                int i = code[pc++];
                switch (OpCode.getOpCode(i)) {

                /* ---------------- 装载 ---------------- */

                case OpCode.OP_MOVE: {
                    L.stack[base + OpCode.getArgA(i)].set(L.stack[base + OpCode.getArgB(i)]);
                    break;
                }
                case OpCode.OP_LOADI: {
                    L.stack[base + OpCode.getArgA(i)].setInteger(OpCode.getArgSBx(i));
                    break;
                }
                case OpCode.OP_LOADF: {
                    L.stack[base + OpCode.getArgA(i)].setFloat(OpCode.getArgSBx(i));
                    break;
                }
                case OpCode.OP_LOADK: {
                    L.stack[base + OpCode.getArgA(i)].set(k[OpCode.getArgBx(i)]);
                    break;
                }
                case OpCode.OP_LOADKX: {
                    L.stack[base + OpCode.getArgA(i)].set(k[OpCode.getArgAx(code[pc])]);
                    pc++;
                    break;
                }
                case OpCode.OP_LOADFALSE: {
                    L.stack[base + OpCode.getArgA(i)].setBoolean(false);
                    break;
                }
                case OpCode.OP_LFALSESKIP: {
                    L.stack[base + OpCode.getArgA(i)].setBoolean(false);
                    pc++;
                    break;
                }
                case OpCode.OP_LOADTRUE: {
                    L.stack[base + OpCode.getArgA(i)].setBoolean(true);
                    break;
                }
                case OpCode.OP_LOADNIL: {
                    int ra = base + OpCode.getArgA(i);
                    int b = OpCode.getArgB(i);
                    do {
                        L.stack[ra++].setNil();
                    } while (b-- > 0);
                    break;
                }

                /* ---------------- upvalue ---------------- */

                case OpCode.OP_GETUPVAL: {
                    L.stack[base + OpCode.getArgA(i)].set(cl.upvals[OpCode.getArgB(i)].value());
                    break;
                }
                case OpCode.OP_SETUPVAL: {
                    cl.upvals[OpCode.getArgB(i)].value().set(L.stack[base + OpCode.getArgA(i)]);
                    break;
                }

                /* ---------------- 取 ---------------- */

                case OpCode.OP_GETTABUP: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue upval = cl.upvals[OpCode.getArgB(i)].value();
                    LuaValue rc = k[OpCode.getArgC(i)];
                    LuaValue slot = fastGet(upval, rc);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, upval, rc, ra);
                    }
                    break;
                }
                case OpCode.OP_GETTABLE: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    LuaValue rc = L.stack[base + OpCode.getArgC(i)];
                    LuaValue slot = fastGet(rb, rc);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, rb, rc, ra);
                    }
                    break;
                }
                case OpCode.OP_GETI: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    LuaValue key = LuaValue.ofInteger(OpCode.getArgC(i));
                    LuaValue slot = fastGet(rb, key);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, rb, key, ra);
                    }
                    break;
                }
                case OpCode.OP_GETFIELD: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    LuaValue rc = k[OpCode.getArgC(i)];
                    LuaValue slot = fastGet(rb, rc);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, rb, rc, ra);
                    }
                    break;
                }

                /* ---------------- 存 ---------------- */

                case OpCode.OP_SETTABUP: {
                    LuaValue upval = cl.upvals[OpCode.getArgA(i)].value();
                    LuaValue rb = k[OpCode.getArgB(i)];
                    LuaValue rc = rkc(L, k, base, i);
                    LuaValue slot = fastSet(upval, rb);
                    if (slot != null) {
                        slot.set(rc);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, upval, rb, rc);
                    }
                    break;
                }
                case OpCode.OP_SETTABLE: {
                    LuaValue ra = L.stack[base + OpCode.getArgA(i)];
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    LuaValue rc = rkc(L, k, base, i);
                    LuaValue slot = fastSet(ra, rb);
                    if (slot != null) {
                        slot.set(rc);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, ra, rb, rc);
                    }
                    break;
                }
                case OpCode.OP_SETI: {
                    LuaValue ra = L.stack[base + OpCode.getArgA(i)];
                    LuaValue key = LuaValue.ofInteger(OpCode.getArgB(i));
                    LuaValue rc = rkc(L, k, base, i);
                    LuaValue slot = fastSet(ra, key);
                    if (slot != null) {
                        slot.set(rc);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, ra, key, rc);
                    }
                    break;
                }
                case OpCode.OP_SETFIELD: {
                    LuaValue ra = L.stack[base + OpCode.getArgA(i)];
                    LuaValue rb = k[OpCode.getArgB(i)];
                    LuaValue rc = rkc(L, k, base, i);
                    LuaValue slot = fastSet(ra, rb);
                    if (slot != null) {
                        slot.set(rc);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, ra, rb, rc);
                    }
                    break;
                }

                /* ---------------- 表构造 ---------------- */

                case OpCode.OP_NEWTABLE: {
                    int ra = base + OpCode.getArgA(i);
                    int b = OpCode.getArgB(i);
                    int c = OpCode.getArgC(i);
                    if (b > 0) {
                        b = 1 << (b - 1);
                    }
                    /*
                     * EXTRAARG 对 NEWTABLE 是<b>无条件</b>存在的：k 位为 0 时它
                     * 的 Ax 就是 0（上游有断言 (k==0) == (Ax==0)）。所以这里的
                     * pc++ 必须放在 k 判断外面——只对 SETLIST 才是条件的，
                     * 那条指令只在 c 溢出 8 位时才有 EXTRAARG。
                     */
                    if (OpCode.getArgK(i) != 0) {
                        c += OpCode.getArgAx(code[pc]) * (OpCode.MAXARG_C + 1);
                    }
                    pc++;
                    /*
                     * {@code L->top = ra + 1} —— C 版那句「correct top in case
                     * of emergency GC」。<b>不能省</b>：{@code OP_CALL} 收尾时
                     * 会把 {@code top} 拉回 {@code ra}（{@code moveresults} 的
                     * {@code case 0}），此后 {@code top} 就落在还活着的寄存器
                     * 底下；而 {@code top} 之上的槽在 Lua 里<b>对 GC 不可见</b>
                     * （{@code traversethread} 只 mark {@code [stack, top)}）。
                     * 少了这一句，下面那个收集点就会把活着的局部量当成死区
                     * （实测：{@code local m} 被清成 nil）。
                     */
                    L.top = ra + 1;
                    L.stack[ra].setTable(new LuaTable(c, b));
                    /*
                     * {@code checkGC(L, ra+1)}：C 版主循环里仅有的两个收集点
                     * 之一（另一个在 {@code OP_CLOSURE}）。
                     */
                    if (L.gcDue()) {
                        L.maybeCollect(L);
                    }
                    break;
                }
                case OpCode.OP_SETLIST: {
                    int ra = base + OpCode.getArgA(i);
                    int n = OpCode.getArgB(i);
                    long last = OpCode.getArgC(i);
                    LuaTable h = L.stack[ra].asTable();
                    if (n == 0) {
                        n = (int) (L.top - ra) - 1;
                    } else {
                        L.top = ci.top;
                    }
                    last += n;
                    if (OpCode.getArgK(i) != 0) {
                        last += (long) OpCode.getArgAx(code[pc]) * (OpCode.MAXARG_C + 1);
                        pc++;
                    }
                    for (; n > 0; n--) {
                        h.setArrayRaw(last, L.stack[ra + n]);
                        last--;
                    }
                    break;
                }

                /* ---------------- 算术 ---------------- */

                case OpCode.OP_ADDI: {
                    if (arithImm(L, i, base, Arith.OP_ADD)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_ADDK: {
                    if (arithK(L, i, base, k, Arith.OP_ADD)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_SUBK: {
                    if (arithK(L, i, base, k, Arith.OP_SUB)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_MULK: {
                    if (arithK(L, i, base, k, Arith.OP_MUL)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_MODK: {
                    saveState(L, ci, pc);
                    if (arithK(L, i, base, k, Arith.OP_MOD)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_POWK: {
                    if (arithK(L, i, base, k, Arith.OP_POW)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_DIVK: {
                    if (arithK(L, i, base, k, Arith.OP_DIV)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_IDIVK: {
                    saveState(L, ci, pc);
                    if (arithK(L, i, base, k, Arith.OP_IDIV)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BANDK: {
                    if (arithK(L, i, base, k, Arith.OP_BAND)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BORK: {
                    if (arithK(L, i, base, k, Arith.OP_BOR)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BXORK: {
                    if (arithK(L, i, base, k, Arith.OP_BXOR)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_SHRI: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    int ic = OpCode.getArgSC(i);
                    Long ib = toIntegerNs(rb, Arith.F2I_EQ);
                    if (ib != null) {
                        pc++;
                        L.stack[ra].setInteger(Arith.shiftl(ib, -ic));
                    }
                    break;
                }
                case OpCode.OP_SHLI: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    int ic = OpCode.getArgSC(i);
                    Long ib = toIntegerNs(rb, Arith.F2I_EQ);
                    if (ib != null) {
                        pc++;
                        L.stack[ra].setInteger(Arith.shiftl(ic, ib));
                    }
                    break;
                }
                case OpCode.OP_ADD: {
                    if (arith(L, i, base, Arith.OP_ADD)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_SUB: {
                    if (arith(L, i, base, Arith.OP_SUB)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_MUL: {
                    if (arith(L, i, base, Arith.OP_MUL)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_MOD: {
                    saveState(L, ci, pc);
                    if (arith(L, i, base, Arith.OP_MOD)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_POW: {
                    if (arith(L, i, base, Arith.OP_POW)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_DIV: {
                    if (arith(L, i, base, Arith.OP_DIV)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_IDIV: {
                    saveState(L, ci, pc);
                    if (arith(L, i, base, Arith.OP_IDIV)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BAND: {
                    if (arith(L, i, base, Arith.OP_BAND)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BOR: {
                    if (arith(L, i, base, Arith.OP_BOR)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_BXOR: {
                    if (arith(L, i, base, Arith.OP_BXOR)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_SHR: {
                    if (arith(L, i, base, Arith.OP_SHR)) {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_SHL: {
                    if (arith(L, i, base, Arith.OP_SHL)) {
                        pc++;
                    }
                    break;
                }

                /* ---------------- 元方法 ---------------- */

                case OpCode.OP_MMBIN: {
                    int pi = code[pc - 2];
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    int result = base + OpCode.getArgA(pi);
                    saveState(L, ci, pc);
                    LuaTm.tryBinTm(L, L.stack[base + OpCode.getArgA(i)], rb, result,
                            OpCode.getArgC(i));
                    break;
                }
                case OpCode.OP_MMBINI: {
                    int pi = code[pc - 2];
                    int result = base + OpCode.getArgA(pi);
                    saveState(L, ci, pc);
                    LuaTm.tryBinITm(L, L.stack[base + OpCode.getArgA(i)], OpCode.getArgSB(i),
                            OpCode.getArgK(i) != 0, result, OpCode.getArgC(i));
                    break;
                }
                case OpCode.OP_MMBINK: {
                    int pi = code[pc - 2];
                    int result = base + OpCode.getArgA(pi);
                    saveState(L, ci, pc);
                    LuaTm.tryBinAssocTm(L, L.stack[base + OpCode.getArgA(i)],
                            k[OpCode.getArgB(i)], OpCode.getArgK(i) != 0, result,
                            OpCode.getArgC(i));
                    break;
                }

                /* ---------------- 一元 ---------------- */

                case OpCode.OP_UNM: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    if (rb.isInteger()) {
                        L.stack[ra].setInteger(-rb.i);
                    } else {
                        Double nb = toNumberNs(rb);
                        if (nb != null) {
                            L.stack[ra].setFloat(-nb);
                        } else {
                            saveState(L, ci, pc);
                            LuaTm.tryBinTm(L, rb, rb, ra, Tm.TM_UNM);
                        }
                    }
                    break;
                }
                case OpCode.OP_BNOT: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    Long ib = toIntegerNs(rb, Arith.F2I_EQ);
                    if (ib != null) {
                        L.stack[ra].setInteger(~ib);
                    } else {
                        saveState(L, ci, pc);
                        LuaTm.tryBinTm(L, rb, rb, ra, Tm.TM_BNOT);
                    }
                    break;
                }
                case OpCode.OP_NOT: {
                    L.stack[base + OpCode.getArgA(i)]
                            .setBoolean(!L.stack[base + OpCode.getArgB(i)].toBoolean());
                    break;
                }
                case OpCode.OP_LEN: {
                    saveState(L, ci, pc);
                    objLen(L, base + OpCode.getArgA(i), L.stack[base + OpCode.getArgB(i)]);
                    break;
                }
                case OpCode.OP_CONCAT: {
                    int ra = base + OpCode.getArgA(i);
                    int n = OpCode.getArgB(i);
                    L.top = ra + n;
                    ci.savedpc = pc;
                    concat(L, n);
                    break;
                }

                /* ---------------- 控制流 ---------------- */

                case OpCode.OP_CLOSE: {
                    saveState(L, ci, pc);
                    L.closeUpvals(base + OpCode.getArgA(i), null);
                    break;
                }
                case OpCode.OP_TBC: {
                    /*
                     * {@code luaF_newtbcupval} 在<b>赋值的当下</b>就检查
                     * {@code __close}，不是在作用域结束时。所以
                     * {@code local x <close> = 5} 会在这一条指令上直接抛
                     * “variable 'x' got a non-closable value”，
                     * 后面的 {@code print(x)} 根本不会执行——实测确认。
                     * 挪到关闭时检查会让它先打印、最后才报错。
                     */
                    ci.savedpc = pc;
                    L.newTbcUpval(base + OpCode.getArgA(i));
                    break;
                }
                case OpCode.OP_JMP: {
                    pc += OpCode.getArgSJ(i);
                    break;
                }

                /* ---------------- 测试与比较 ---------------- */

                case OpCode.OP_EQ: {
                    saveState(L, ci, pc);
                    int cond = equalObj(L, L.stack[base + OpCode.getArgA(i)],
                            L.stack[base + OpCode.getArgB(i)]) ? 1 : 0;
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_LT: {
                    int cond = order(L, ci, pc, i, base, Tm.TM_LT) ? 1 : 0;
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_LE: {
                    int cond = order(L, ci, pc, i, base, Tm.TM_LE) ? 1 : 0;
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_EQK: {
                    int cond = LuaValue.rawEqual(L.stack[base + OpCode.getArgA(i)],
                            k[OpCode.getArgB(i)]) ? 1 : 0;
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_EQI: {
                    LuaValue ra = L.stack[base + OpCode.getArgA(i)];
                    int im = OpCode.getArgSB(i);
                    int cond;
                    if (ra.isInteger()) {
                        cond = (ra.i == im) ? 1 : 0;
                    } else if (ra.isFloat()) {
                        cond = (ra.n == (double) im) ? 1 : 0;
                    } else {
                        cond = 0; // 其余类型不可能等于数字
                    }
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_LTI:
                case OpCode.OP_LEI:
                case OpCode.OP_GTI:
                case OpCode.OP_GEI: {
                    int op = OpCode.getOpCode(i);
                    /*
                     * 四条指令的比较运算符各不同，不能归约成「< 还是 <=」。
                     * LTI/LEI/GTI/GEI 的立即数<b>永远在右边</b>（{@code ra OP im}），
                     * inv 只影响元方法的调用顺序（{@code ra >= im} 退化成
                     * {@code im <= ra} 时要把两个操作数换过来）。
                     */
                    boolean inv = (op == OpCode.OP_GTI || op == OpCode.OP_GEI);
                    LuaValue ra = L.stack[base + OpCode.getArgA(i)];
                    int im = OpCode.getArgSB(i);
                    int cond;
                    if (ra.isInteger()) {
                        long ia = ra.i;
                        switch (op) {
                        case OpCode.OP_LTI:
                            cond = (ia < im) ? 1 : 0;
                            break;
                        case OpCode.OP_LEI:
                            cond = (ia <= im) ? 1 : 0;
                            break;
                        case OpCode.OP_GTI:
                            cond = (ia > im) ? 1 : 0;
                            break;
                        default:
                            cond = (ia >= im) ? 1 : 0;
                            break;
                        }
                    } else if (ra.isFloat()) {
                        double fa = ra.n;
                        switch (op) {
                        case OpCode.OP_LTI:
                            cond = (fa < im) ? 1 : 0;
                            break;
                        case OpCode.OP_LEI:
                            cond = (fa <= im) ? 1 : 0;
                            break;
                        case OpCode.OP_GTI:
                            cond = (fa > im) ? 1 : 0;
                            break;
                        default:
                            cond = (fa >= im) ? 1 : 0;
                            break;
                        }
                    } else {
                        saveState(L, ci, pc);
                        int ev = (op == OpCode.OP_LTI || op == OpCode.OP_GTI)
                                ? Tm.TM_LT : Tm.TM_LE;
                        cond = LuaTm.callOrderITm(L, ra, im, inv, OpCode.getArgC(i) != 0,
                                ev) ? 1 : 0;
                    }
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_TEST: {
                    int cond = L.stack[base + OpCode.getArgA(i)].toBoolean() ? 1 : 0;
                    if (cond != OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_TESTSET: {
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    /*
                     * 判的是 {@code l_isfalse(rb) == k}，不是「真值 == k」。
                     * <p>
                     * 这两种写法在 {@code k=0} 与 {@code k=1} 上<b>恰好相反</b>，
                     * 而且是那种「只在一半路径上错」的相反：{@code and} 的
                     * {@code TESTSET} 只在「结果寄存器 ≠ B」时保留下来（否则
                     * {@code patchTestReg} 会把它改写成 {@code OP_TEST}），
                     * 于是 {@code if x then} 一直是对的，{@code x and y} 一直是错的。
                     * <p>
                     * 症状是 {@code local t = {} print(t and "yes")} 打印出那张表：
                     * 字节码与参考实现逐条相同，说明错不在代码生成而在这一句。
                     */
                    int isFalse = rb.toBoolean() ? 0 : 1; // l_isfalse(rb)
                    if (isFalse == OpCode.getArgK(i)) {
                        pc++;
                    } else {
                        L.stack[base + OpCode.getArgA(i)].set(rb);
                        pc += OpCode.getArgSJ(code[pc]) + 1;
                    }
                    break;
                }
                case OpCode.OP_SELF: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    LuaValue rc = rkc(L, k, base, i);
                    L.stack[ra + 1].set(rb);
                    LuaValue slot = fastGet(rb, rc);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, rb, rc, ra);
                    }
                    break;
                }

                /* ---------------- 调用与返回 ---------------- */

                case OpCode.OP_CALL: {
                    int ra = base + OpCode.getArgA(i);
                    int b = OpCode.getArgB(i);
                    if (b != 0) {
                        L.top = ra + b;
                    }
                    ci.savedpc = pc;
                    CallInfo newci = L.precall(ra, OpCode.getArgC(i) - 1);
                    if (newci == null) {
                        /*
                         * {@code updatetrap(ci)}：C 调用可能开/关过钩子。
                         * <b>这一处必须有</b>——{@code debug.sethook} 自己就是个
                         * C 函数，它的 {@code settraps} 只改帧上的 {@code trap}，
                         * 主循环这个局部变量要靠这里才读得到。
                         */
                        trap = ci.trap;
                        break; // C 函数：已经跑完，结果在栈上
                    }
                    ci = newci;
                    start = true;
                    continue outer;
                }
                case OpCode.OP_TAILCALL: {
                    int ra = base + OpCode.getArgA(i);
                    int b = OpCode.getArgB(i);
                    int nparams1 = OpCode.getArgC(i);
                    int delta = (nparams1 != 0) ? ci.nextraargs + nparams1 : 0;
                    if (b != 0) {
                        L.top = ra + b;
                    } else {
                        b = L.top - ra;
                    }
                    ci.savedpc = pc;
                    if (OpCode.getArgK(i) != 0) {
                        L.closeUpvals(base, null);
                    }
                    int n = L.pretailcall(ci, ra, b, delta);
                    if (n < 0) {
                        start = true; // Lua 函数：复用同一帧继续跑
                        continue outer;
                    }
                    ci.func -= delta;
                    L.poscall(ci, n); // 里面会报返回钩子
                    trap = ci.trap; // luaD_poscall 可能改了钩子
                    if (ci.fresh) {
                        return;
                    }
                    ci = ci.previous;
                    ret = true;
                    continue outer;
                }
                case OpCode.OP_RETURN: {
                    int ra = base + OpCode.getArgA(i);
                    int n = OpCode.getArgB(i) - 1;
                    int nparams1 = OpCode.getArgC(i);
                    if (n < 0) {
                        n = L.top - ra;
                    }
                    ci.savedpc = pc;
                    if (OpCode.getArgK(i) != 0) {
                        ci.nres = n;
                        if (L.top < ci.top) {
                            L.top = ci.top;
                        }
                        L.closeUpvals(base, null);
                    }
                    if (nparams1 != 0) {
                        ci.func -= ci.nextraargs + nparams1;
                    }
                    L.top = ra + n;
                    L.poscall(ci, n); // 里面会报返回钩子
                    trap = ci.trap; // luaD_poscall 可能改了钩子
                    if (ci.fresh) {
                        return;
                    }
                    ci = ci.previous;
                    ret = true;
                    continue outer;
                }
                case OpCode.OP_RETURN0: {
                    if (L.hookmask != 0) {
                        // 有钩子就走正式的 poscall，让它把返回钩子报出去
                        int ra = base + OpCode.getArgA(i);
                        L.top = ra;
                        ci.savedpc = pc;
                        L.poscall(ci, 0);
                        /*
                         * C 版在这里写 {@code trap = 1}。置位是为了让紧接着的
                         * {@code luaG_tracecall}（{@code goto ret} 之后）跑一下，
                         * 把上层帧的 {@code trap} 重新置 1——否则上层帧的
                         * {@code trap} 早被 {@code luaG_traceexec} 清成 0 了，
                         * 返回之后就再也接不上行钩子。
                         */
                        trap = true;
                    } else {
                        int nres = ci.nresults;
                        L.ci = ci.previous;
                        L.top = base - 1;
                        L.ensureSlots(L.top + Math.max(nres, 0));
                        while (nres > 0) {
                            L.stack[L.top++].setNil();
                            nres--;
                        }
                    }
                    if (ci.fresh) {
                        return;
                    }
                    ci = ci.previous;
                    ret = true;
                    continue outer;
                }
                case OpCode.OP_RETURN1: {
                    if (L.hookmask != 0) {
                        int ra = base + OpCode.getArgA(i);
                        L.top = ra + 1;
                        ci.savedpc = pc;
                        L.poscall(ci, 1);
                        trap = true; // 同 OP_RETURN0
                    } else {
                        int nres = ci.nresults;
                        L.ci = ci.previous;
                        if (nres == 0) {
                            L.top = base - 1;
                        } else {
                            int ra = base + OpCode.getArgA(i);
                            L.stack[base - 1].set(L.stack[ra]);
                            L.top = base;
                            L.ensureSlots(L.top + nres - 1);
                            while (nres > 1) {
                                L.stack[L.top++].setNil();
                                nres--;
                            }
                        }
                    }
                    if (ci.fresh) {
                        return;
                    }
                    ci = ci.previous;
                    ret = true;
                    continue outer;
                }

                /* ---------------- 循环 ---------------- */

                case OpCode.OP_FORLOOP: {
                    int ra = base + OpCode.getArgA(i);
                    LuaValue counter = L.stack[ra + 1];
                    if (counter.isInteger()) {
                        long count = counter.i;
                        if (count != 0) { // C 里是无符号比较，等价于「计数不为 0」
                            long step = L.stack[ra + 2].i;
                            long idx = L.stack[ra].i + step;
                            L.stack[ra + 1].setInteger(count - 1);
                            L.stack[ra].setInteger(idx);
                            L.stack[ra + 3].setInteger(idx);
                            pc -= OpCode.getArgBx(i);
                        }
                    } else if (floatForLoop(L, ra)) {
                        pc -= OpCode.getArgBx(i);
                    }
                    break;
                }
                case OpCode.OP_FORPREP: {
                    saveState(L, ci, pc);
                    if (forPrep(L, base + OpCode.getArgA(i))) {
                        pc += OpCode.getArgBx(i) + 1;
                    }
                    break;
                }
                case OpCode.OP_TFORPREP: {
                    int ra = base + OpCode.getArgA(i);
                    ci.savedpc = pc;
                    L.newTbcUpval(ra + 3);
                    pc += OpCode.getArgBx(i);
                    // 落到紧随其后的 TFORCALL，再落到 TFORLOOP
                    i = code[pc++];
                    doTforCall(L, ci, ra, i, pc);
                    i = code[pc++];
                    if (doTforLoop(L, ra, i)) {
                        pc -= OpCode.getArgBx(i);
                    }
                    break;
                }
                case OpCode.OP_TFORCALL: {
                    int ra = base + OpCode.getArgA(i);
                    doTforCall(L, ci, ra, i, pc);
                    i = code[pc++];
                    if (doTforLoop(L, ra, i)) {
                        pc -= OpCode.getArgBx(i);
                    }
                    break;
                }
                case OpCode.OP_TFORLOOP: {
                    int ra = base + OpCode.getArgA(i);
                    if (doTforLoop(L, ra, i)) {
                        pc -= OpCode.getArgBx(i);
                    }
                    break;
                }

                /* ---------------- 闭包与可变参数 ---------------- */

                case OpCode.OP_CLOSURE: {
                    int ra = base + OpCode.getArgA(i);
                    pushClosure(L, cl.p.p[OpCode.getArgBx(i)], cl.upvals, base, ra);
                    /*
                     * {@code checkGC(L, ra+1)}。这里 C 版没有那句
                     * {@code L->top = ra+1}（{@code OP_NEWTABLE} 有），但收集
                     * 要在栈上跑 {@code __gc}，{@code top} 得先落在活寄存器
                     * 之上——照 {@code OP_NEWTABLE} 的做法抬一下。
                     */
                    if (L.gcDue()) {
                        int savedTop = L.top;
                        L.top = Math.max(savedTop, ra + 1);
                        L.maybeCollect(L);
                        L.top = savedTop;
                    }
                    break;
                }
                case OpCode.OP_VARARG: {
                    saveState(L, ci, pc);
                    LuaTm.getVarargs(L, ci, base + OpCode.getArgA(i), OpCode.getArgC(i) - 1);
                    break;
                }
                case OpCode.OP_VARARGPREP: {
                    ci.savedpc = pc;
                    LuaTm.adjustVarargs(L, OpCode.getArgA(i), ci, cl.p);
                    /*
                     * {@code ProtectNT} 后半句的 {@code updatetrap(ci)}。
                     * 变参函数的 {@code luaG_tracecall} 返回的是 0（钩子推迟到这里
                     * 才报），但它已经把帧上的 {@code trap} 置成 1 了，所以这里
                     * 读回来正好是 1——上游就是靠这个「先关再开」把调用钩子挪到
                     * 参数归位之后的。
                     */
                    trap = ci.trap;
                    if (trap) {
                        DebugLib.dHookCall(L, ci);
                        L.oldpc = 1; // 下一条指令按「新行」处理
                    }
                    base = ci.func + 1;
                    break;
                }
                case OpCode.OP_EXTRAARG: {
                    throw L.runError("malformed opcode EXTRAARG");
                }
                default:
                    throw L.runError("unknown opcode " + OpCode.getOpCode(i));
                }
            }
        }
    }

    /* ================================================================
     * 取指令操作数
     * ================================================================ */

    /** {@code RKC(i)}：k 位为 1 时取常量表，否则取寄存器 */
    private static LuaValue rkc(LuaState L, LuaValue[] k, int base, int i) {
        if (OpCode.getArgK(i) != 0) {
            return k[OpCode.getArgC(i)];
        }
        return L.stack[base + OpCode.getArgC(i)];
    }

    /**
     * {@code luaV_fastget} 的等价物。
     *
     * @return 命中的槽（非 nil）；未命中或 {@code t} 不是表时返回 {@code null}
     */
    private static LuaValue fastGet(LuaValue t, LuaValue key) {
        if (t.tt != Lua.TTABLE) {
            return null;
        }
        LuaValue slot = t.asTable().get(key);
        return (slot != null && !slot.isNil()) ? slot : null;
    }

    /**
     * 写路径用的「快速槽」，与 {@link #fastGet} 分开。
     * <p>
     * 分开的理由只有一条，但它是致命的：弱表上 {@code LuaTable.get} 返回的是
     * <b>副本</b>——槽里存的是 {@code WeakRef}，不能直接交到 VM 手里，所以读出来
     * 要解一层。往这个副本上 {@code set} 等于什么都没写。实测出来的症状很怪：
     * {@code t.f = g} 之后 {@code t.f} 还是 {@code f}，因为第二次赋值被写进了
     * 一个马上就被丢掉的临时对象。
     * <p>
     * 所以弱表一律返回 {@code null}，把写入交给 {@link #finishSet} 走
     * {@code LuaTable.set}——包装、插入、失效清扫都在那一条路上。
     */
    private static LuaValue fastSet(LuaValue t, LuaValue key) {
        if (t.tt != Lua.TTABLE) {
            return null;
        }
        LuaTable tb = t.asTable();
        if (tb.isWeak()) {
            return null;
        }
        LuaValue slot = tb.get(key);
        return (slot != null && !slot.isNil()) ? slot : null;
    }

    /** {@code Protect}：出错前把 pc 与 top 修正好 */
    private static void saveState(LuaState L, CallInfo ci, int pc) {
        ci.savedpc = pc;
        L.top = ci.top;
    }

    /* ================================================================
     * luaV_finishOp：补完被 yield 打断的那条指令
     * ================================================================ */

    /**
     * {@code lvm.c: luaV_finishOp}。
     * <p>
     * 一条指令执行到一半去调了 Lua 代码（元方法、for 迭代器、普通调用），
     * 结果那里面 yield 了——协程挂起时这条指令<b>还没跑完</b>。等 resume 回来，
     * {@code unroll} 会先调这里把剩下的半步补上，再重新进入解释器。
     * <p>
     * 补的方式全靠「结果就压在栈顶」这一点：被调用者返回时
     * {@code luaD_poscall} 已经把结果搬好，这里要么把它写进目标寄存器
     * （{@code OP_GETTABLE} 之类），要么拿它决定跳不跳转（比较类指令），
     * 要么把 pc 回退一格重跑（{@code OP_CLOSE} / {@code OP_RETURN} 这种要
     * 「关一批变量、yield、再关下一批」的）。
     * <p>
     * 调用方保证 {@code L.ci} 是 Lua 帧，且 {@code savedpc} 指向被中断指令的
     * <b>下一条</b>（主循环里 {@code pc++} 在取指时就做了），所以
     * {@code code[savedpc - 1]} 才是被中断的那条。
     */
    static void finishOp(LuaState L) {
        CallInfo ci = L.ci;
        int base = ci.func + 1;
        int inst = ((LClosure) L.stack[ci.func].o).p.code[ci.savedpc - 1];
        int op = OpCode.getOpCode(inst);
        switch (op) {
        case OpCode.OP_MMBIN:
        case OpCode.OP_MMBINI:
        case OpCode.OP_MMBINK: {
            // 结果写回触发元方法的那条算术指令的目标寄存器
            int prev = ((LClosure) L.stack[ci.func].o).p.code[ci.savedpc - 2];
            L.stack[base + OpCode.getArgA(prev)].set(L.stack[--L.top]);
            break;
        }
        case OpCode.OP_UNM:
        case OpCode.OP_BNOT:
        case OpCode.OP_LEN:
        case OpCode.OP_GETTABUP:
        case OpCode.OP_GETTABLE:
        case OpCode.OP_GETI:
        case OpCode.OP_GETFIELD:
        case OpCode.OP_SELF: {
            L.stack[base + OpCode.getArgA(inst)].set(L.stack[--L.top]);
            break;
        }
        case OpCode.OP_LT:
        case OpCode.OP_LE:
        case OpCode.OP_LTI:
        case OpCode.OP_LEI:
        case OpCode.OP_GTI:
        case OpCode.OP_GEI:
        case OpCode.OP_EQ: {
            // 注意 OP_EQI / OP_EQK 不会 yield，所以不在这一组里
            boolean res = L.stack[L.top - 1].toBoolean();
            L.top--;
            if (ci.leq) { // 拿 __lt 顶替缺失的 __le，结果要取反
                ci.leq = false;
                res = !res;
            }
            int next = ((LClosure) L.stack[ci.func].o).p.code[ci.savedpc];
            if (OpCode.getOpCode(next) == OpCode.OP_JMP
                    && res != (OpCode.getArgK(inst) != 0)) {
                ci.savedpc++; // 条件不成立，跳过紧跟的 JMP
            }
            break;
        }
        case OpCode.OP_CONCAT: {
            /*
             * 拼接是「一批一批来」的：一次把栈顶若干个拼成一个，再接着拼。
             * yield 发生在调 __concat 的那一刻，所以恢复时要先把元方法的结果
             * 放回它该在的位置（倒数第二格），把 top 收一格，然后把剩下那
             * 几个继续拼完——可能又 yield，那就再来一遍。
             */
            int t = L.top - 1; // 调 luaT_tryconcatTM 时的栈顶
            int total = t - 1 - (base + OpCode.getArgA(inst)); // 还没拼的元素数
            L.stack[t - 2].set(L.stack[t]);
            L.top = t - 1;
            concat(L, total);
            break;
        }
        case OpCode.OP_CLOSE: {
            ci.savedpc--; // 重跑，接着关剩下的变量
            break;
        }
        case OpCode.OP_RETURN: {
            // 先按 nres 把 top 摆正，再重跑，让 return 这一整套走完
            L.top = base + OpCode.getArgA(inst) + ci.nres;
            ci.savedpc--;
            break;
        }
        default:
            // 剩下的 OP_TFORCALL / OP_CALL / OP_TAILCALL / OP_SET* 没有半步要补：
            // 结果已经由 poscall 直接写进目标寄存器了
            break;
        }
    }

    /* ================================================================
     * 算术（成功返回 true，调用方据此跳过后面的 MMBIN）
     * ================================================================ */

    /** {@code op_arithI}：第二个操作数是立即整数 */
    private static boolean arithImm(LuaState L, int i, int base, int op) {
        LuaValue v1 = L.stack[base + OpCode.getArgB(i)];
        LuaValue res = Arith.rawArith(op, v1, LuaValue.ofInteger(OpCode.getArgSC(i)));
        if (res == null) {
            return false;
        }
        L.stack[base + OpCode.getArgA(i)].set(res);
        return true;
    }

    /** {@code op_arithK} / {@code op_arithfK} / {@code op_bitwiseK}：第二个操作数在常量表 */
    private static boolean arithK(LuaState L, int i, int base, LuaValue[] k, int op) {
        LuaValue v1 = L.stack[base + OpCode.getArgB(i)];
        LuaValue res = Arith.rawArith(op, v1, k[OpCode.getArgC(i)]);
        if (res == null) {
            return false;
        }
        L.stack[base + OpCode.getArgA(i)].set(res);
        return true;
    }

    /** {@code op_arith} / {@code op_arithf} / {@code op_bitwise}：两个操作数都在寄存器 */
    private static boolean arith(LuaState L, int i, int base, int op) {
        LuaValue v1 = L.stack[base + OpCode.getArgB(i)];
        LuaValue v2 = L.stack[base + OpCode.getArgC(i)];
        LuaValue res = Arith.rawArith(op, v1, v2);
        if (res == null) {
            return false;
        }
        L.stack[base + OpCode.getArgA(i)].set(res);
        return true;
    }

    /* ================================================================
     * 比较
     * ================================================================ */

    /** {@code op_order}：{@code event} 决定是 {@code <}（TM_LT）还是 {@code <=}（TM_LE） */
    private static boolean order(LuaState L, CallInfo ci, int pc, int i, int base, int event) {
        LuaValue ra = L.stack[base + OpCode.getArgA(i)];
        LuaValue rb = L.stack[base + OpCode.getArgB(i)];
        boolean lt = (event == Tm.TM_LT);
        if (ra.isInteger() && rb.isInteger()) {
            return lt ? (ra.i < rb.i) : (ra.i <= rb.i);
        }
        if (ra.isNumber() && rb.isNumber()) {
            return lt ? ltNum(ra, rb) : leNum(ra, rb);
        }
        if (ra.isString() && rb.isString()) {
            int c = lStrcmp(ra.asString(), rb.asString());
            return lt ? (c < 0) : (c <= 0);
        }
        saveState(L, ci, pc);
        return LuaTm.callOrderTm(L, ra, rb, event);
    }

    /**
     * {@code luaV_lessthan}：给库函数用的「与 VM 完全一致」的小于。
     * <p>
     * {@code math.max/min} 走的是 {@code lua_compare(LUA_OPLT)}，而不是自己
     * 比数字——所以 {@code math.max("a","b")} 是 {@code "b"}（走字符串比较），
     * 而 {@code math.max(1,"b")} 报的是
     * {@code attempt to compare number with string}（走 {@code __lt} 失败分支）。
     * 只比数字会把这两条都做错。
     */
    static boolean lessThan(LuaState L, LuaValue a, LuaValue b) {
        if (a.isInteger() && b.isInteger()) {
            return a.i < b.i;
        }
        if (a.isNumber() && b.isNumber()) {
            return ltNum(a, b);
        }
        if (a.isString() && b.isString()) {
            return lStrcmp(a.asString(), b.asString()) < 0;
        }
        return LuaTm.callOrderTm(L, a, b, Tm.TM_LT);
    }

    /** {@code LTnum} */
    static boolean ltNum(LuaValue l, LuaValue r) {
        if (l.isInteger()) {
            long li = l.i;
            if (r.isInteger()) {
                return li < r.i;
            }
            return ltIntFloat(li, r.n);
        }
        double lf = l.n;
        if (r.isFloat()) {
            return lf < r.n;
        }
        return ltFloatInt(lf, r.i);
    }

    /** {@code LEnum} */
    static boolean leNum(LuaValue l, LuaValue r) {
        if (l.isInteger()) {
            long li = l.i;
            if (r.isInteger()) {
                return li <= r.i;
            }
            return leIntFloat(li, r.n);
        }
        double lf = l.n;
        if (r.isFloat()) {
            return lf <= r.n;
        }
        return leFloatInt(lf, r.i);
    }

    /** {@code l_intfitsf}：{@code i} 能否无损转成 double —— 判据是 {@code |i| <= 2^53} */
    private static boolean intFitsF(long i) {
        return i >= -(1L << 53) && i <= (1L << 53);
    }

    /** {@code LTintfloat}：{@code i < f} */
    private static boolean ltIntFloat(long i, double f) {
        if (intFitsF(i)) {
            return (double) i < f;
        }
        Long fi = Arith.fltToInteger(f, Arith.F2I_CEIL);
        return (fi != null) ? (i < fi) : (f > 0);
    }

    /** {@code LEintfloat}：{@code i <= f} */
    private static boolean leIntFloat(long i, double f) {
        if (intFitsF(i)) {
            return (double) i <= f;
        }
        Long fi = Arith.fltToInteger(f, Arith.F2I_FLOOR);
        return (fi != null) ? (i <= fi) : (f > 0);
    }

    /** {@code LTfloatint}：{@code f < i} */
    private static boolean ltFloatInt(double f, long i) {
        if (intFitsF(i)) {
            return f < (double) i;
        }
        Long fi = Arith.fltToInteger(f, Arith.F2I_FLOOR);
        return (fi != null) ? (fi < i) : (f < 0);
    }

    /** {@code LEfloatint}：{@code f <= i} */
    private static boolean leFloatInt(double f, long i) {
        if (intFitsF(i)) {
            return f <= (double) i;
        }
        Long fi = Arith.fltToInteger(f, Arith.F2I_CEIL);
        return (fi != null) ? (fi <= i) : (f < 0);
    }

    /**
     * {@code l_strcmp}：按字节比较，与 C locale 下的 {@code strcoll} 一致。
     * <p>
     * Lua 字符串可以含 {@code \0}，所以比较要分段：每段比到 {@code \0} 或串尾，
     * 相等则跳过 {@code \0} 继续下一段。段长不同时短的更小。
     */
    static int lStrcmp(LuaString a, LuaString b) {
        byte[] s1 = a.bytes;
        byte[] s2 = b.bytes;
        int p1 = 0;
        int p2 = 0;
        int rl1 = s1.length;
        int rl2 = s2.length;
        for (;;) {
            int z1 = firstZero(s1, p1);
            int z2 = firstZero(s2, p2);
            int seg1 = (z1 < 0) ? rl1 : (z1 - p1);
            int seg2 = (z2 < 0) ? rl2 : (z2 - p2);
            int n = Math.min(seg1, seg2);
            for (int j = 0; j < n; j++) {
                int d = s1[p1 + j] - s2[p2 + j]; // 有符号 byte，与 C 的 char 一致
                if (d != 0) {
                    return d;
                }
            }
            if (seg1 != seg2) {
                return seg1 < seg2 ? -1 : 1;
            }
            if (z2 < 0) {
                return (z1 < 0) ? 0 : 1;
            }
            if (z1 < 0) {
                return -1;
            }
            p1 = z1 + 1;
            p2 = z2 + 1;
            rl1 -= seg1 + 1;
            rl2 -= seg2 + 1;
        }
    }

    private static int firstZero(byte[] s, int from) {
        for (int i = from; i < s.length; i++) {
            if (s[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    /* ================================================================
     * 相等
     * ================================================================ */

    /** {@code luaV_equalobj} */
    static boolean equalObj(LuaState L, LuaValue t1, LuaValue t2) {
        if (t1.tt != t2.tt) {
            if (t1.baseType() != t2.baseType() || t1.baseType() != Lua.TNUMBER) {
                return false;
            }
            // 两个变体不同的数字：只有都能取到整数值且相等时才相等
            Long i1 = Arith.toIntegerNs(t1);
            Long i2 = Arith.toIntegerNs(t2);
            return i1 != null && i2 != null && i1.longValue() == i2.longValue();
        }
        switch (t1.tt) {
        case Lua.TNIL:
            return true;
        case Lua.TBOOLEAN:
            return t1.i == t2.i;
        case Lua.VNUMINT:
            return t1.i == t2.i;
        case Lua.VNUMFLT:
            return t1.n == t2.n;
        case Lua.VSHRSTR:
        case Lua.VLNGSTR:
            return t1.asString().equals(t2.asString());
        case Lua.TTABLE: {
            if (t1.o == t2.o) {
                return true;
            }
            LuaValue tm = LuaTm.fastTm(t1.asTable().getMetatable(), Tm.TM_EQ, L);
            if (tm == null) {
                tm = LuaTm.fastTm(t2.asTable().getMetatable(), Tm.TM_EQ, L);
            }
            if (tm == null) {
                return false;
            }
            int t = L.top;
            L.ensureSlots(t + 1);
            LuaTm.callTmRes(L, tm, t1, t2, t);
            return L.stack[t].toBoolean();
        }
        default:
            return t1.o == t2.o;
        }
    }

    /* ================================================================
     * 表访问的慢路径
     * ================================================================ */

    /**
     * {@code luaV_gettable}：库函数用的表访问（快路径 + {@code __index} 慢路径）。
     * <p>
     * 与 {@code OP_GETTABLE} 走同一套代码，{@code ipairs} 之类的库函数才会
     * 和脚本里的 {@code t[i]} 表现得一模一样。
     */
    static void getTable(LuaState L, LuaValue t, LuaValue key, int valIdx) {
        LuaValue slot = fastGet(t, key);
        if (slot != null) {
            L.stack[valIdx].set(slot);
            return;
        }
        finishGet(L, t, key, valIdx);
    }

    /**
     * {@code luaV_settable}：库函数用的表写入（快路径 + {@code __newindex} 慢路径）。
     * <p>
     * 与 {@link #getTable} 成对，{@code table.insert/remove/move/sort} 都靠它
     * 才能作用于「带 {@code __newindex} 的对象」——C 的 {@code lua_seti} 走的
     * 是 {@code luaV_fastset + luaV_finishset}，<b>不是</b> {@code luaH_setint}，
     * 所以元方法是会被调用的。写成裸写会让 {@code table.insert(ud, v)} 静默地
     * 什么也不做。
     */
    static void setTable(LuaState L, LuaValue t, LuaValue key, LuaValue val) {
        LuaValue slot = fastSet(t, key);
        if (slot != null) {
            slot.set(val);
            return;
        }
        finishSet(L, t, key, val);
    }

    /** {@code luaV_finishget} */
    static void finishGet(LuaState L, LuaValue t, LuaValue key, int valIdx) {
        for (int loop = 0; loop < MAX_TAG_LOOP; loop++) {
            LuaValue tm;
            if (t.tt == Lua.TTABLE) {
                tm = LuaTm.fastTm(t.asTable().getMetatable(), Tm.TM_INDEX, L);
                if (tm == null) {
                    L.stack[valIdx].setNil();
                    return;
                }
            } else {
                tm = LuaTm.getTmByObj(L, t, Tm.TM_INDEX);
                if (tm == null) {
                    throw L.typeError(t, "index");
                }
            }
            if (tm.isFunction()) {
                LuaTm.callTmRes(L, tm, t, key, valIdx);
                return;
            }
            t = tm; // __index 是表：继续在它上面查
            LuaValue slot = fastGet(t, key);
            if (slot != null) {
                L.stack[valIdx].set(slot);
                return;
            }
        }
        throw L.runError("'__index' chain too long; possible loop");
    }

    /** {@code luaV_finishset} */
    private static void finishSet(LuaState L, LuaValue t, LuaValue key, LuaValue val) {
        for (int loop = 0; loop < MAX_TAG_LOOP; loop++) {
            LuaValue tm;
            if (t.tt == Lua.TTABLE) {
                tm = LuaTm.fastTm(t.asTable().getMetatable(), Tm.TM_NEWINDEX, L);
                if (tm == null) {
                    t.asTable().set(key, val);
                    return;
                }
            } else {
                tm = LuaTm.getTmByObj(L, t, Tm.TM_NEWINDEX);
                if (tm == null) {
                    throw L.typeError(t, "index");
                }
            }
            if (tm.isFunction()) {
                LuaTm.callTm(L, tm, t, key, val);
                return;
            }
            t = tm; // __newindex 是表：继续在它上面写
            LuaValue slot = fastGet(t, key);
            if (slot != null) {
                slot.set(val);
                return;
            }
        }
        throw L.runError("'__newindex' chain too long; possible loop");
    }

    /* ================================================================
     * 长度
     * ================================================================ */

    /** {@code luaV_objlen} */
    static void objLen(LuaState L, int raIdx, LuaValue rb) {
        LuaValue tm = null;
        switch (rb.tt) {
        case Lua.TTABLE: {
            LuaTable h = rb.asTable();
            tm = LuaTm.fastTm(h.getMetatable(), Tm.TM_LEN, L);
            if (tm == null) {
                L.stack[raIdx].setInteger(h.length());
                return;
            }
            break;
        }
        case Lua.VSHRSTR:
        case Lua.VLNGSTR:
            L.stack[raIdx].setInteger(rb.asString().length());
            return;
        default:
            tm = LuaTm.getTmByObj(L, rb, Tm.TM_LEN);
            if (tm == null) {
                throw L.typeError(rb, "get length of");
            }
            break;
        }
        LuaTm.callTmRes(L, tm, rb, rb, raIdx);
    }

    /* ================================================================
     * 拼接
     * ================================================================ */

    private static boolean cvt2str(LuaValue o) {
        return o.isNumber();
    }

    /** {@code tostring(L,o)}：不是字符串但可转换的（数字）就地转成字符串 */
    private static boolean toStr(LuaValue o) {
        if (o.isString()) {
            return true;
        }
        if (cvt2str(o)) {
            o.setString(LuaString.latin1(numberToString(o)));
            return true;
        }
        return false;
    }

    private static boolean isEmptyStr(LuaValue o) {
        return o.isString() && o.asString().length() == 0;
    }

    /**
     * {@code luaV_concat}：把栈顶 {@code total} 个值从右往左折叠成一个。
     * <p>
     * 每趟取末尾 {@code n} 个合成 1 个，结果落在「收缩后的栈顶」前一格；
     * 反复直到只剩一个。之所以从右往左，是因为左端还可能有未拼接的操作数。
     */
    static void concat(LuaState L, int total) {
        if (total == 1) {
            return;
        }
        do {
            int t = L.top;
            int n = 2;
            LuaValue v1 = L.stack[t - 2];
            LuaValue v2 = L.stack[t - 1];
            if (!(v1.isString() || cvt2str(v1)) || !toStr(v2)) {
                LuaTm.tryConcatTm(L);
            } else if (isEmptyStr(v2)) {
                toStr(v1); // 结果就是第一个操作数
            } else if (isEmptyStr(v1)) {
                L.stack[t - 2].set(v2);
            } else {
                long tl = v2.asString().length();
                for (n = 1; n < total && toStr(L.stack[t - n - 1]); n++) {
                    tl += L.stack[t - n - 1].asString().length();
                }
                byte[] buff = new byte[(int) tl];
                copyToBuff(L, t, n, buff);
                L.stack[t - n].setString(L.newString(buff));
            }
            total -= n - 1;
            L.top -= n - 1;
        } while (total > 1);
    }

    /** {@code copy2buff}：把栈顶往下 {@code n} 个字符串拷进 {@code buff} */
    private static void copyToBuff(LuaState L, int top, int n, byte[] buff) {
        int tl = 0;
        int idx = top - n;
        do {
            LuaString st = L.stack[idx].asString();
            System.arraycopy(st.bytes, 0, buff, tl, st.bytes.length);
            tl += st.bytes.length;
            idx++;
        } while (--n > 0);
    }

    /* ================================================================
     * 数值 for
     * ================================================================ */

    /**
     * {@code forlimit}：把循环上界收敛成一个整数。
     *
     * @return 循环一次都不该跑时返回 true
     */
    private static boolean forLimit(LuaState L, long init, LuaValue lim, long[] p, long step) {
        Long conv = toInteger(lim, step < 0 ? Arith.F2I_CEIL : Arith.F2I_FLOOR);
        if (conv == null) {
            Double flim = toNumber(lim);
            if (flim == null) {
                throw L.forError(lim, "limit");
            }
            if (0 < flim) { // 太大
                if (step < 0) {
                    return true; // 初值必然小于它
                }
                p[0] = Long.MAX_VALUE;
            } else { // 太小
                if (step > 0) {
                    return true;
                }
                p[0] = Long.MIN_VALUE;
            }
        } else {
            p[0] = conv;
        }
        return step > 0 ? init > p[0] : init < p[0];
    }

    /**
     * {@code forprep}：准备数值 for 循环。
     * <p>
     * 整数循环把「上界」换成「剩余次数」——因为 {@code for i = mininteger, maxinteger}
     * 这种循环的次数本身也塞得进 int64，而边界比较不行。这个替换是 Lua 能正确跑
     * 到 int64 边界的原因。
     *
     * @return 循环一次都不跑时返回 true
     */
    private static boolean forPrep(LuaState L, int ra) {
        LuaValue pinit = L.stack[ra];
        LuaValue plimit = L.stack[ra + 1];
        LuaValue pstep = L.stack[ra + 2];
        if (pinit.isInteger() && pstep.isInteger()) {
            long init = pinit.i;
            long step = pstep.i;
            if (step == 0) {
                throw L.runError("'for' step is zero");
            }
            L.stack[ra + 3].setInteger(init);
            long[] limit = new long[1];
            if (forLimit(L, init, plimit, limit, step)) {
                return true;
            }
            long count;
            if (step > 0) {
                count = limit[0] - init; // 无符号减法，位模式等同有符号
                if (step != 1) {
                    count = Long.divideUnsigned(count, step);
                }
            } else {
                count = init - limit[0];
                // -(step+1) 是为了避开对 mininteger 取负
                count = Long.divideUnsigned(count, (-(step + 1)) + 1);
            }
            L.stack[ra + 1].setInteger(count);
        } else {
            Double initD = toNumber(pinit);
            Double limitD = toNumber(plimit);
            Double stepD = toNumber(pstep);
            if (limitD == null) {
                throw L.forError(plimit, "limit");
            }
            if (stepD == null) {
                throw L.forError(pstep, "step");
            }
            if (initD == null) {
                throw L.forError(pinit, "initial value");
            }
            double init = initD;
            double limit = limitD;
            double step = stepD;
            if (step == 0) {
                throw L.runError("'for' step is zero");
            }
            if (step > 0 ? (limit < init) : (init < limit)) {
                return true;
            }
            L.stack[ra + 1].setFloat(limit);
            L.stack[ra + 2].setFloat(step);
            L.stack[ra].setFloat(init);
            L.stack[ra + 3].setFloat(init);
        }
        return false;
    }

    /** {@code floatforloop} */
    private static boolean floatForLoop(LuaState L, int ra) {
        if (!L.stack[ra + 2].isFloat()) {
            return false;
        }
        double step = L.stack[ra + 2].n;
        double limit = L.stack[ra + 1].n;
        double idx = L.stack[ra].n + step;
        if (step > 0 ? (idx <= limit) : (limit <= idx)) {
            L.stack[ra].setFloat(idx);
            L.stack[ra + 3].setFloat(idx);
            return true;
        }
        return false;
    }

    /* ================================================================
     * 泛型 for
     * ================================================================ */

    /**
     * {@code OP_TFORCALL} 的实体：把「迭代器函数 / 状态 / 控制变量」复制到
     * {@code ra+4} 起，然后调用。
     */
    private static void doTforCall(LuaState L, CallInfo ci, int ra, int i, int pc) {
        for (int j = 0; j < 3; j++) {
            L.stack[ra + 4 + j].set(L.stack[ra + j]);
        }
        L.top = ra + 7;
        ci.savedpc = pc;
        L.call(ra + 4, OpCode.getArgC(i));
    }

    /**
     * {@code OP_TFORLOOP} 的实体。
     *
     * @return 是否继续循环（调用方据此 {@code pc -= Bx}）
     */
    private static boolean doTforLoop(LuaState L, int ra, int i) {
        if (!L.stack[ra + 4].isNil()) {
            L.stack[ra + 2].set(L.stack[ra + 4]);
            return true;
        }
        return false;
    }

    /* ================================================================
     * 闭包
     * ================================================================ */

    /** {@code pushclosure} */
    private static void pushClosure(LuaState L, Proto p, UpVal[] encup, int base, int ra) {
        LClosure ncl = new LClosure(p);
        L.stack[ra].setObject(Lua.VLIF, ncl);
        for (int i = 0; i < p.sizeupvalues; i++) {
            Proto.Upvaldesc uv = p.upvalues[i];
            if (uv.instack) {
                ncl.upvals[i] = L.findUpval(base + uv.idx);
            } else {
                ncl.upvals[i] = encup[uv.idx];
            }
        }
    }

    /* ================================================================
     * 转换
     * ================================================================ */

    /** {@code l_strton}：字符串能不能整个解释成一个数字 */
    static LuaValue strToNum(LuaValue o) {
        if (!o.isString()) {
            return null;
        }
        String s = new String(o.asString().bytes, StandardCharsets.ISO_8859_1);
        return Numeral.str2num(s);
    }

    /** {@code luaV_tointegerns}，带 mode */
    static Long toIntegerNs(LuaValue v, int mode) {
        if (v.isFloat()) {
            return Arith.fltToInteger(v.n, mode);
        }
        if (v.isInteger()) {
            return v.i;
        }
        return null;
    }

    /** {@code luaV_tointeger}：含字符串转换 */
    static Long toInteger(LuaValue v, int mode) {
        LuaValue n = strToNum(v);
        if (n != null) {
            v = n;
        }
        return toIntegerNs(v, mode);
    }

    /** {@code tonumberns}：不含字符串转换 */
    static Double toNumberNs(LuaValue v) {
        if (v.isInteger()) {
            return (double) v.i;
        }
        if (v.isFloat()) {
            return v.n;
        }
        return null;
    }

    /** {@code luaV_tonumber_}：含字符串转换 */
    static Double toNumber(LuaValue v) {
        if (v.isInteger()) {
            return (double) v.i;
        }
        if (v.isFloat()) {
            return v.n;
        }
        LuaValue n = strToNum(v);
        if (n == null) {
            return null;
        }
        return n.isInteger() ? (double) n.i : n.n;
    }

    /**
     * {@code luaO_tostring} 的数字格式化：{@code %.14g}，看起来像整数的补 {@code .0}。
     * <p>
     * 整值浮点必须输出成 {@code 1.0} 而不是 {@code 1}——Lua 靠这个区分
     * {@code print(1)} 与 {@code print(1.0)}，也靠它保住 {@code math.type}
     * 的可观测差异。
     */
    static String numberToString(LuaValue v) {
        if (v.isInteger()) {
            return Long.toString(v.i);
        }
        double d = v.n;
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        String s = formatG(d, 14);
        if (looksLikeInt(s)) {
            s = s + ".0";
        }
        return s;
    }

    /**
     * C 的 {@code %.<p>g}。
     * <p>
     * <b>不能直接用 Java 的 {@code %.14g}</b>：C 规定 {@code %g} 要去掉小数部分
     * 末尾的零、并在没有小数部分时连小数点一起去掉；Java 的 {@code %g} 两者都不做
     * （{@code String.format("%.14g", 1.5)} 得到 {@code 1.5000000000000}，
     * 而 C 得到 {@code 1.5}）。所以这里自己走一遍 C 的选形规则再手工去尾零。
     * <p>
     * C 的规则：设 {@code X} 为按 {@code %E} 转换后的指数。若
     * {@code P > X >= -4} 用 {@code %f}、精度 {@code P-1-X}，否则用
     * {@code %e}、精度 {@code P-1}。
     */
    static String formatG(double d, int p) {
        String e = String.format(Locale.ROOT, "%." + (p - 1) + "e", d);
        int x = exponentOfE(e);
        String s;
        if (p > x && x >= -4) {
            s = String.format(Locale.ROOT, "%." + (p - 1 - x) + "f", d);
        } else {
            s = e;
        }
        return stripTrailingZeros(s);
    }

    /** 从 {@code %e} 的结果里取出指数 */
    private static int exponentOfE(String e) {
        int i = e.indexOf('e');
        if (i < 0) {
            return 0;
        }
        return Integer.parseInt(e.substring(i + 1));
    }

    /** 去掉小数部分末尾的零，以及随之变空的那个小数点（指数部分保持不动） */
    private static String stripTrailingZeros(String s) {
        int e = s.indexOf('e');
        if (e < 0) {
            e = s.length();
        }
        String mant = s.substring(0, e);
        String exp = s.substring(e);
        if (mant.indexOf('.') >= 0) {
            int end = mant.length();
            while (end > 0 && mant.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && mant.charAt(end - 1) == '.') {
                end--;
            }
            mant = mant.substring(0, end);
        }
        return mant + exp;
    }

    /** {@code buff[strspn(buff, "-0123456789")] == '\0'} */
    private static boolean looksLikeInt(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                i++;
            } else {
                break;
            }
        }
        return i == s.length();
    }
}
