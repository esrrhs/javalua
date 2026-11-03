package com.github.esrrhs.javalua;

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
        boolean ret = false;
        boolean trap = false;

        outer:
        for (;;) {
            if (start || ret) {
                if (start) {
                    trap = L.hookmask != 0;
                }
                start = false;
                ret = false;
                cl = (LClosure) L.stack[ci.func].o;
                k = cl.p.k;
                code = cl.p.code;
                pc = ci.savedpc;
                base = ci.base;
                if (trap) {
                    trap = DebugLib.traceCall(L, ci);
                }
            }
            for (;;) {
                if (ci.trap) {
                    trap = true;
                }
                if (trap) {
                    trap = DebugLib.traceExec(L, ci, pc);
                    base = ci.base;
                }
                int i = code[pc++];
                ci.savedpc = pc;
                int op = OpCode.getOpCode(i);
                int a = OpCode.getArgA(i);
                int ra = base + a;

                switch (op) {
                case OpCode.OP_MOVE:
                    L.stack[ra].set(L.stack[base + OpCode.getArgB(i)]);
                    break;
                case OpCode.OP_LOADK:
                    L.stack[ra].set(k[OpCode.getArgBx(i)]);
                    break;
                case OpCode.OP_LOADKX:
                    L.stack[ra].set(k[OpCode.getArgAx(code[pc++])]);
                    break;
                case OpCode.OP_LOADBOOL:
                    L.stack[ra].setBoolean(OpCode.getArgB(i) != 0);
                    if (OpCode.getArgC(i) != 0) {
                        pc++;
                    }
                    break;
                case OpCode.OP_LOADNIL: {
                    int b = OpCode.getArgB(i);
                    for (int n = 0; n <= b; n++) {
                        L.stack[ra + n].setNil();
                    }
                    break;
                }
                case OpCode.OP_GETUPVAL:
                    L.stack[ra].set(cl.upvals[OpCode.getArgB(i)].value());
                    break;
                case OpCode.OP_GETTABUP: {
                    LuaValue tab = cl.upvals[OpCode.getArgB(i)].value();
                    LuaValue key = rk(L, k, base, OpCode.getArgC(i));
                    LuaValue slot = fastGet(tab, key);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, tab, key, ra);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_GETTABLE: {
                    LuaValue tab = L.stack[base + OpCode.getArgB(i)];
                    LuaValue key = rk(L, k, base, OpCode.getArgC(i));
                    LuaValue slot = fastGet(tab, key);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, tab, key, ra);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_SETTABUP: {
                    LuaValue tab = cl.upvals[a].value();
                    LuaValue key = rk(L, k, base, OpCode.getArgB(i));
                    LuaValue val = rk(L, k, base, OpCode.getArgC(i));
                    LuaValue slot = fastSet(tab, key);
                    if (slot != null) {
                        slot.set(val);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, tab, key, val);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_SETUPVAL:
                    cl.upvals[OpCode.getArgB(i)].value().set(L.stack[ra]);
                    break;
                case OpCode.OP_SETTABLE: {
                    LuaValue tab = L.stack[ra];
                    LuaValue key = rk(L, k, base, OpCode.getArgB(i));
                    LuaValue val = rk(L, k, base, OpCode.getArgC(i));
                    LuaValue slot = fastSet(tab, key);
                    if (slot != null) {
                        slot.set(val);
                    } else {
                        saveState(L, ci, pc);
                        finishSet(L, tab, key, val);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_NEWTABLE: {
                    int b = OpCode.getArgB(i);
                    int c = OpCode.getArgC(i);
                    LuaTable t = new LuaTable(fb2int(b), fb2int(c));
                    L.stack[ra].setTable(t);
                    break;
                }
                case OpCode.OP_SELF: {
                    LuaValue tab = L.stack[base + OpCode.getArgB(i)];
                    L.stack[ra + 1].set(tab);
                    LuaValue key = rk(L, k, base, OpCode.getArgC(i));
                    LuaValue slot = fastGet(tab, key);
                    if (slot != null) {
                        L.stack[ra].set(slot);
                    } else {
                        saveState(L, ci, pc);
                        finishGet(L, tab, key, ra);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_ADD:
                case OpCode.OP_SUB:
                case OpCode.OP_MUL:
                case OpCode.OP_MOD:
                case OpCode.OP_POW:
                case OpCode.OP_DIV:
                case OpCode.OP_IDIV:
                case OpCode.OP_BAND:
                case OpCode.OP_BOR:
                case OpCode.OP_BXOR:
                case OpCode.OP_SHL:
                case OpCode.OP_SHR: {
                    LuaValue rb = rk(L, k, base, OpCode.getArgB(i));
                    LuaValue rc = rk(L, k, base, OpCode.getArgC(i));
                    int aop = op - OpCode.OP_ADD; // maps to Arith.OP_*
                    LuaValue r = Arith.rawArith(L, aop, rb, rc);
                    if (r != null) {
                        L.stack[ra].set(r);
                    } else {
                        saveState(L, ci, pc);
                        LuaTm.tryBinTm(L, rb, rc, ra, Tm.TM_ADD + aop);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_UNM: {
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    if (rb.isInteger()) {
                        L.stack[ra].setInteger(-rb.i);
                    } else {
                        Double n = toNumber(rb);
                        if (n != null) {
                            L.stack[ra].setFloat(-n);
                        } else {
                            saveState(L, ci, pc);
                            LuaTm.tryBinTm(L, rb, rb, ra, Tm.TM_UNM);
                            base = ci.base;
                        }
                    }
                    break;
                }
                case OpCode.OP_BNOT: {
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    Long ib = Arith.toIntegerNs(rb);
                    if (ib != null) {
                        L.stack[ra].setInteger(~ib);
                    } else {
                        saveState(L, ci, pc);
                        LuaTm.tryBinTm(L, rb, rb, ra, Tm.TM_BNOT);
                        base = ci.base;
                    }
                    break;
                }
                case OpCode.OP_NOT: {
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    L.stack[ra].setBoolean(!rb.toBoolean());
                    break;
                }
                case OpCode.OP_LEN:
                    saveState(L, ci, pc);
                    objLen(L, ra, L.stack[base + OpCode.getArgB(i)]);
                    base = ci.base;
                    break;
                case OpCode.OP_CONCAT: {
                    int b = OpCode.getArgB(i);
                    int c = OpCode.getArgC(i);
                    L.top = base + c + 1;
                    ci.savedpc = pc;
                    concat(L, c - b + 1);
                    base = ci.base;
                    ra = base + a;
                    L.stack[ra].set(L.stack[base + b]);
                    L.top = ci.top;
                    break;
                }
                case OpCode.OP_JMP: {
                    if (a != 0) {
                        L.closeUpvals(base + a - 1, null);
                    }
                    pc += OpCode.getArgSBx(i);
                    break;
                }
                case OpCode.OP_EQ: {
                    LuaValue rb = rk(L, k, base, OpCode.getArgB(i));
                    LuaValue rc = rk(L, k, base, OpCode.getArgC(i));
                    saveState(L, ci, pc);
                    boolean eq = equalObj(L, rb, rc);
                    base = ci.base;
                    doJump(L, code, ci, base, /*cond*/ (eq == (a != 0)), /*pc*/ pc);
                    pc = ci.savedpc;
                    break;
                }
                case OpCode.OP_LT:
                case OpCode.OP_LE: {
                    LuaValue rb = rk(L, k, base, OpCode.getArgB(i));
                    LuaValue rc = rk(L, k, base, OpCode.getArgC(i));
                    saveState(L, ci, pc);
                    boolean res = (op == OpCode.OP_LT)
                            ? lessThan(L, rb, rc)
                            : lessEqual(L, rb, rc);
                    base = ci.base;
                    doJump(L, code, ci, base, res == (a != 0), pc);
                    pc = ci.savedpc;
                    break;
                }
                case OpCode.OP_TEST: {
                    if (!L.stack[ra].toBoolean() != (OpCode.getArgC(i) != 0)) {
                        // fall through to jump
                    } else {
                        pc++;
                        break;
                    }
                    // take jump at pc
                    int ji = code[pc++];
                    if (OpCode.getArgA(ji) != 0) {
                        L.closeUpvals(base + OpCode.getArgA(ji) - 1, null);
                    }
                    pc += OpCode.getArgSBx(ji);
                    break;
                }
                case OpCode.OP_TESTSET: {
                    LuaValue rb = L.stack[base + OpCode.getArgB(i)];
                    if (!rb.toBoolean() != (OpCode.getArgC(i) != 0)) {
                        L.stack[ra].set(rb);
                        int ji = code[pc++];
                        if (OpCode.getArgA(ji) != 0) {
                            L.closeUpvals(base + OpCode.getArgA(ji) - 1, null);
                        }
                        pc += OpCode.getArgSBx(ji);
                    } else {
                        pc++;
                    }
                    break;
                }
                case OpCode.OP_CALL: {
                    int b = OpCode.getArgB(i);
                    int nresults = OpCode.getArgC(i) - 1;
                    if (b != 0) {
                        L.top = ra + b;
                    }
                    ci.savedpc = pc;
                    if (L.precall(ra, nresults) != null) {
                        // Lua function — new ci
                        ci = L.ci;
                        start = true;
                        continue outer;
                    }
                    // C function returned
                    if (nresults >= 0) {
                        L.top = ci.top;
                    }
                    base = ci.base;
                    break;
                }
                case OpCode.OP_TAILCALL: {
                    int b = OpCode.getArgB(i);
                    if (b != 0) {
                        L.top = ra + b;
                    }
                    ci.savedpc = pc;
                    // simplify: treat as call (full tailcall later)
                    if (L.precall(ra, -1) != null) {
                        ci = L.ci;
                        start = true;
                        continue outer;
                    }
                    base = ci.base;
                    break;
                }
                case OpCode.OP_RETURN: {
                    int b = OpCode.getArgB(i);
                    if (b != 0) {
                        L.top = ra + b - 1;
                    }
                    // Lua 5.3.4: close if this proto has nested functions (sizep)
                    if (cl.p.sizep > 0) {
                        L.closeUpvals(base, null);
                    }
                    ci.savedpc = pc;
                    L.poscall(ci, (b != 0) ? (b - 1) : (L.top - ra));
                    if (ci == ci0) {
                        return; // fresh invoke done
                    }
                    ci = L.ci;
                    ret = true;
                    continue outer;
                }
                case OpCode.OP_FORLOOP: {
                    // 5.3 integer/float forloop
                    if (doForLoop(L, ra)) {
                        pc += OpCode.getArgSBx(i);
                    }
                    break;
                }
                case OpCode.OP_FORPREP: {
                    saveState(L, ci, pc);
                    forPrep(L, ra);
                    base = ci.base;
                    pc += OpCode.getArgSBx(i);
                    break;
                }
                case OpCode.OP_TFORCALL: {
                    // R(A+3).. = R(A)(R(A+1), R(A+2))
                    int c = OpCode.getArgC(i);
                    L.stack[ra + 3].set(L.stack[ra]);
                    L.stack[ra + 4].set(L.stack[ra + 1]);
                    L.stack[ra + 5].set(L.stack[ra + 2]);
                    L.top = ra + 6;
                    ci.savedpc = pc;
                    L.call(ra + 3, c);
                    base = ci.base;
                    L.top = ci.top;
                    // fallthrough to TFORLOOP expected as next — handled separately
                    break;
                }
                case OpCode.OP_TFORLOOP: {
                    if (!L.stack[ra + 1].isNil()) {
                        L.stack[ra].set(L.stack[ra + 1]);
                        pc += OpCode.getArgSBx(i);
                    }
                    break;
                }
                case OpCode.OP_SETLIST: {
                    int b = OpCode.getArgB(i);
                    int c = OpCode.getArgC(i);
                    if (b == 0) {
                        b = L.top - ra - 1;
                    }
                    if (c == 0) {
                        c = OpCode.getArgAx(code[pc++]);
                    }
                    LuaTable t = L.stack[ra].asTable();
                    int last = ((c - 1) * OpCode.LFIELDS_PER_FLUSH) + b;
                    for (; b > 0; b--) {
                        t.set(LuaValue.ofInteger(last--), L.stack[ra + b]);
                    }
                    L.top = ci.top;
                    break;
                }
                case OpCode.OP_CLOSURE: {
                    Proto p = cl.p.p[OpCode.getArgBx(i)];
                    pushClosure(L, p, cl.upvals, base, ra);
                    break;
                }
                case OpCode.OP_VARARG: {
                    int b = OpCode.getArgB(i) - 1;
                    ci.savedpc = pc;
                    LuaTm.getVarargs(L, ci, ra, b);
                    base = ci.base;
                    break;
                }
                case OpCode.OP_EXTRAARG:
                    break;
                default:
                    throw new IllegalStateException("unknown opcode " + op);
                }
            }
        }
    }

    /** RK operand: BITRK means constant */
    private static LuaValue rk(LuaState L, LuaValue[] k, int base, int x) {
        if (OpCode.isK(x)) {
            return k[OpCode.indexK(x)];
        }
        return L.stack[base + x];
    }

    private static int fb2int(int x) {
        return (x < 8) ? x : ((x & 7) + 8) << ((x >> 3) - 1);
    }

    private static void doJump(LuaState L, int[] code, CallInfo ci, int base, boolean take, int pc) {
        if (take) {
            int ji = code[pc++];
            if (OpCode.getArgA(ji) != 0) {
                L.closeUpvals(base + OpCode.getArgA(ji) - 1, null);
            }
            pc += OpCode.getArgSBx(ji);
        } else {
            pc++;
        }
        ci.savedpc = pc;
    }

    private static LuaValue rkc(LuaState L, LuaValue[] k, int base, int i) {
        return rk(L, k, base, OpCode.getArgC(i));
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
        int base = ci.base;
        int inst = ((LClosure) L.stack[ci.func].o).p.code[ci.savedpc - 1];
        int op = OpCode.getOpCode(inst);
        switch (op) {
        case OpCode.OP_UNM:
        case OpCode.OP_BNOT:
        case OpCode.OP_LEN:
        case OpCode.OP_GETTABUP:
        case OpCode.OP_GETTABLE:
        case OpCode.OP_SELF: {
            L.stack[base + OpCode.getArgA(inst)].set(L.stack[--L.top]);
            break;
        }
        case OpCode.OP_LT:
        case OpCode.OP_LE:
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
                    && res != (OpCode.getArgA(inst) != 0)) {
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
        LuaValue v2 = LuaValue.ofInteger(OpCode.getArgSC(i));
        if (!v1.isInteger()) {
            Double n1 = toNumber(v1);
            if (n1 == null) {
                return false;
            }
            v1 = LuaValue.ofFloat(n1);
            v2 = LuaValue.ofFloat(v2.i);
        }
        LuaValue res = Arith.rawArith(L, op, v1, v2);
        if (res == null) {
            return false;
        }
        L.stack[base + OpCode.getArgA(i)].set(res);
        return true;
    }

    /** {@code op_arithK} / {@code op_arithfK} / {@code op_bitwiseK}：第二个操作数在常量表 */
    private static boolean arithK(LuaState L, int i, int base, LuaValue[] k, int op) {
        LuaValue v1 = L.stack[base + OpCode.getArgB(i)];
        LuaValue v2 = k[OpCode.getArgC(i)];
        if (!(v1.isInteger() && v2.isInteger())) {
            Double n1 = toNumber(v1);
            Double n2 = toNumber(v2);
            if (n1 == null || n2 == null) {
                return false;
            }
            v1 = LuaValue.ofFloat(n1);
            v2 = LuaValue.ofFloat(n2);
        }
        LuaValue res = Arith.rawArith(L, op, v1, v2);
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
        // Lua 5.3: non-integer operands go through luaV_tonumber_ (strings → float).
        if (!(v1.isInteger() && v2.isInteger())) {
            Double n1 = toNumber(v1);
            Double n2 = toNumber(v2);
            if (n1 != null && n2 != null) {
                LuaValue res = Arith.rawArith(L, op, LuaValue.ofFloat(n1), LuaValue.ofFloat(n2));
                if (res != null) {
                    L.stack[base + OpCode.getArgA(i)].set(res);
                    return true;
                }
            }
            return false;
        }
        LuaValue res = Arith.rawArith(L, op, v1, v2);
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

    /** Lua 5.3.4 {@code luaV_lessequal}: try __le, else !(r < l) via __lt. */
    static boolean lessEqual(LuaState L, LuaValue a, LuaValue b) {
        if (a.isInteger() && b.isInteger()) {
            return a.i <= b.i;
        }
        if (a.isNumber() && b.isNumber()) {
            return leNum(a, b);
        }
        if (a.isString() && b.isString()) {
            return lStrcmp(a.asString(), b.asString()) <= 0;
        }
        return LuaTm.callOrderTm(L, a, b, Tm.TM_LE);
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
     * <p>
     * <b>每个字节都要当 {@code unsigned char} 比。</b>C99 对 {@code strcoll}
     * 的规定是「返回值符号由第一对不相等字节之差决定，两个字节都按
     * {@code unsigned char} 解释」。Java 的 {@code byte} 是有符号的，直接相减会
     * 把 {@code 0xFF} 当成 {@code -1}：于是 {@code "\xff" < "abc"} 从
     * <b>false</b> 翻成 <b>true</b>，{@code table.sort} 的结果也跟着整体反转。
     * 本机实测 {@code strcoll("\xff","abc") == 158 > 0}。
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
                // unsigned char：见上面 javadoc 里 strcoll 的规定
                int d = (s1[p1 + j] & 0xFF) - (s2[p2 + j] & 0xFF);
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
    /**
     * Lua 5.3.4 {@code forlimit}: convert limit to integer when possible.
     * @return false if limit is not a number; true and fill {@code p}/{@code stopnow} otherwise
     */
    private static boolean forLimit53(LuaValue obj, long[] p, long step, boolean[] stopnow) {
        stopnow[0] = false;
        Long conv = toInteger(obj, step < 0 ? Arith.F2I_CEIL : Arith.F2I_FLOOR);
        if (conv == null) {
            Double n = toNumber(obj);
            if (n == null) {
                return false;
            }
            if (0 < n) {
                p[0] = Long.MAX_VALUE;
                if (step < 0) {
                    stopnow[0] = true;
                }
            } else {
                p[0] = Long.MIN_VALUE;
                if (step >= 0) {
                    stopnow[0] = true;
                }
            }
        } else {
            p[0] = conv;
        }
        return true;
    }

    /**
     * Lua 5.3.4 {@code OP_FORPREP}: convert values; subtract step from init;
     * always fall through via sBx (caller adds sBx).
     */
    private static void forPrep(LuaState L, int ra) {
        LuaValue init = L.stack[ra];
        LuaValue plimit = L.stack[ra + 1];
        LuaValue pstep = L.stack[ra + 2];
        long[] ilimit = new long[1];
        boolean[] stopnow = new boolean[1];
        if (init.isInteger() && pstep.isInteger()
                && forLimit53(plimit, ilimit, pstep.i, stopnow)) {
            long initv = stopnow[0] ? 0L : init.i;
            L.stack[ra + 1].setInteger(ilimit[0]);
            L.stack[ra].setInteger(initv - pstep.i);
        } else {
            Double nlimit = toNumber(plimit);
            if (nlimit == null) {
                throw L.forError(plimit, "limit");
            }
            Double nstep = toNumber(pstep);
            if (nstep == null) {
                throw L.forError(pstep, "step");
            }
            Double ninit = toNumber(init);
            if (ninit == null) {
                throw L.forError(init, "initial value");
            }
            L.stack[ra + 1].setFloat(nlimit);
            L.stack[ra + 2].setFloat(nstep);
            L.stack[ra].setFloat(ninit - nstep);
        }
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
            /*
             * glibc 按 IEEE 符号位打 "-nan" / "nan"；C99 把符号留作
             * unspecified，但本仓库的差分参考是 ubuntu/glibc 的 lua，
             * 且 string.len(0/0) / #tostring(0/0) 会把符号变成可观测长度。
             */
            return Double.doubleToRawLongBits(d) < 0 ? "-nan" : "nan";
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
        /*
         * C 那边这两步都是 sprintf/locale 的事：{@code buff[len++] =
         * lua_getlocaledecpoint()} 补的那个点，与 {@code %.14g} 自己打出来的
         * 那个点是同一个字符。这里数字全部先按 '.' 算，最后统一换成
         * locale 的那一个（{@code os.setlocale("pt_BR")} 之后
         * {@code tostring(3.0)} 是 "3,0"）。
         */
        return Numeral.toLocaleRadix(s);
    }

    /**
     * C 的 {@code %.<p>g} —— 与 {@code string.format} 的 {@code %g} 共用
     * {@link StringLib#generalDigits}，理由写在那边。
     * <p>
     * <b>不要用 Java 的 {@code %.14g}。</b> 两个坑，第二个是实测撞出来的：
     * <ol>
     *   <li>C 的 {@code %g} 要去掉小数部分末尾的零、没有小数部分时连小数点一起
     *       去掉；Java 的 {@code %g} 两者都不做
     *       （{@code String.format("%.14g", 1.5)} 给 {@code 1.5000000000000}，
     *       C 给 {@code 1.5}）。</li>
     *   <li>Java 的 {@code %f} / {@code %e} 是拿 {@code Double.toString} 的
     *       <b>最短表示</b>再做一次四舍五入，等于连舍两次。
     *       {@code math.sqrt(math.maxinteger)} 的真值是
     *       {@code 3037000499.976049900054931640625}，最短表示是
     *       {@code 3037000499.97605}；按真值取 14 位有效数字是
     *       {@code 3037000499.976}，按最短表示取（末位 5 进一）就成了
     *       {@code 3037000499.9761}。参考实现给的是前者。</li>
     * </ol>
     * 所以走 {@link java.math.BigDecimal}：{@code new BigDecimal(double)} 是
     * 精确的二进制展开，一次 {@code setScale} 就舍到位。
     */
    static String formatG(double d, int p) {
        if (Double.isNaN(d)) {
            return Double.doubleToRawLongBits(d) < 0 ? "-nan" : "nan";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        String s = StringLib.generalDigits(Math.abs(d), p, false);
        // 符号单独加：BigDecimal 没有 -0.0，tostring(-0.0) 得是 "-0.0"
        return (Double.doubleToRawLongBits(d) < 0) ? "-" + s : s;
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
    /** 5.3 OP_FORLOOP: update index; return true if loop continues. */
    private static boolean doForLoop(LuaState L, int ra) {
        LuaValue idx = L.stack[ra];
        LuaValue limit = L.stack[ra + 1];
        LuaValue step = L.stack[ra + 2];
        if (idx.isInteger() && limit.isInteger() && step.isInteger()) {
            long i = idx.i + step.i;
            boolean cont = (step.i > 0) ? (i <= limit.i) : (i >= limit.i);
            if (cont) {
                L.stack[ra].setInteger(i);
                L.stack[ra + 3].setInteger(i);
            }
            return cont;
        }
        double i = (idx.isInteger() ? idx.i : idx.n) + (step.isInteger() ? step.i : step.n);
        double lim = limit.isInteger() ? limit.i : limit.n;
        double st = step.isInteger() ? step.i : step.n;
        boolean cont = (st > 0) ? (i <= lim) : (i >= lim);
        if (cont) {
            L.stack[ra].setFloat(i);
            L.stack[ra + 3].setFloat(i);
        }
        return cont;
    }


}
