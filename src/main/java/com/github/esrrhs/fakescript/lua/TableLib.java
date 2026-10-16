package com.github.esrrhs.fakescript.lua;

/**
 * 表库 —— 对应 {@code ltablib.c}。
 * <p>
 * 这一块最值得说的不是快排，而是<b>「像表一样的东西」也算表</b>：
 * {@code ltablib.c} 开头那组 {@code TAB_R / TAB_W / TAB_L} 检查允许传一个
 * 带 {@code __index} / {@code __newindex} / {@code __len} 的对象进来。少了它，
 * 凡是拿 userdata 当数组用的代码在 {@code table.insert} 上就直接报错。
 * <p>
 * 排序的不稳定性是<b>刻意保留</b>的：参考实现用的是原地快排，相等元素的相对
 * 顺序没有保证。这里照抄同一套划分逻辑（连「order function 不自洽」的报错
 * 都一致），不去做稳定排序——稳定了反而是另一种可观测行为。
 */
public final class TableLib {

    private TableLib() {
    }

    public static void open(LuaState L) {
        LuaTable t = new LuaTable();
        LuaAux.setFn(t, "concat", TableLib::concat);
        LuaAux.setFn(t, "insert", TableLib::insert);
        LuaAux.setFn(t, "move", TableLib::move);
        LuaAux.setFn(t, "pack", TableLib::pack);
        LuaAux.setFn(t, "remove", TableLib::remove);
        LuaAux.setFn(t, "unpack", TableLib::unpack);
        LuaAux.setFn(t, "sort", TableLib::sort);
        LuaAux.registerLib(L, "table", t);
    }

    /* ==================== 「像表」判定 ==================== */

    private static final int TAB_R = 1;
    private static final int TAB_W = 2;
    private static final int TAB_L = 4;

    /**
     * {@code ltablib.c: checktab}。
     * <p>
     * 不是表的时候，要求它<b>同时</b>具备本操作需要的那几个元方法，缺一个就
     * 报错——只查 {@code __index} 会让 {@code table.insert} 在一个只有
     * {@code __index} 的对象上走到一半才炸。
     */
    private static void checkTab(LuaState L, int arg, int what) {
        LuaValue v = L.arg1(arg);
        if (v.isTable()) {
            return;
        }
        LuaTable mt = (v.baseType() == Lua.TTABLE) ? v.asTable().getMetatable()
                : L.typeMetatable(v.baseType());
        if (mt != null) {
            boolean ok = true;
            if ((what & TAB_R) != 0) {
                ok = hasField(mt, "__index");
            }
            if (ok && (what & TAB_W) != 0) {
                ok = hasField(mt, "__newindex");
            }
            if (ok && (what & TAB_L) != 0) {
                ok = hasField(mt, "__len");
            }
            if (ok) {
                return;
            }
        }
        LuaAux.checkType(L, arg, Lua.TTABLE);
    }

    private static boolean hasField(LuaTable mt, String name) {
        LuaValue r = mt.get(LuaString.latin1(name));
        return r != null && !r.isNil();
    }

    /** {@code luaL_len}：走 {@code __len}，且结果必须是整数 */
    private static long objLen(LuaState L, LuaValue v) {
        LuaValue tm = null;
        if (v.isTable()) {
            tm = LuaTm.fastTm(v.asTable().getMetatable(), Tm.TM_LEN, L);
            if (tm == null) {
                return v.asTable().length();
            }
        } else if (v.isString()) {
            return v.asString().length();
        } else {
            tm = LuaTm.getTmByObj(L, v, Tm.TM_LEN);
            if (tm == null) {
                throw L.typeError(v, "get length of");
            }
        }
        int f = L.top;
        L.ensureSlots(f + 2);
        L.stack[f].set(tm);
        L.stack[f + 1].set(v);
        L.top = f + 2;
        L.call(f, 1);
        LuaValue r = L.stack[L.top - 1];
        Long i = Vm.toInteger(r, Arith.F2I_EQ);
        L.top = f;
        if (i == null) {
            throw L.libError("object length is not an integer");
        }
        return i;
    }

    private static long auxGetN(LuaState L, int arg, int what) {
        checkTab(L, arg, what | TAB_L);
        return objLen(L, L.arg1(arg));
    }

    /* ==================== 各函数 ==================== */

    /**
     * {@code lua_geti} / {@code lua_seti}：<b>会</b>走 {@code __index} /
     * {@code __newindex}。
     * <p>
     * 一开始这里写的是裸读写（{@code luaH_setint} 语义），结果
     * {@code table.insert} 落在带 {@code __newindex} 的对象上<b>静默失败</b>：
     * 元方法没被调用，值也没进表。C 版用的是 {@code lua_seti}，它走
     * {@code luaV_fastset + luaV_finishset}，元方法是会触发的。
     * <p>
     * 取值的实现细节：{@link Vm#getTable} 把结果写进一个栈槽，所以这里借
     * {@code L.top} 当临时槽，用完不推进 {@code top}——回调元方法时
     * {@code L.top} 会被改动，但返回时 {@code callTmRes} 已经把它复位了。
     */
    private static LuaValue getI(LuaState L, LuaValue t, long i) {
        int f = L.top;
        L.ensureSlots(f + 1);
        Vm.getTable(L, t, LuaValue.ofInteger(i), f);
        return new LuaValue(L.stack[f]);
    }

    private static void setI(LuaState L, LuaValue t, long i, LuaValue v) {
        Vm.setTable(L, t, LuaValue.ofInteger(i), v);
    }

    /** {@code tinsert} */
    private static int insert(LuaState L) {
        long e = auxGetN(L, 1, TAB_R | TAB_W);
        e = e + 1; // 第一个空位
        int n = L.getTop();
        long pos;
        if (n == 2) {
            pos = e;
        } else if (n == 3) {
            pos = LuaAux.checkInteger(L, 2);
            // (pos-1) 当作无符号数比较，等价于 1 <= pos <= e
            if (!(pos >= 1 && pos <= e)) {
                throw L.argError(2, "position out of bounds");
            }
            for (long i = e; i > pos; i--) {
                setI(L, L.arg1(1), i, getI(L, L.arg1(1), i - 1));
            }
        } else {
            throw L.libError("wrong number of arguments to 'insert'");
        }
        setI(L, L.arg1(1), pos, new LuaValue(L.arg1(n)));
        return 0;
    }

    /** {@code tremove} */
    private static int remove(LuaState L) {
        long size = auxGetN(L, 1, TAB_R | TAB_W);
        long pos = LuaAux.optInteger(L, 2, size);
        if (pos != size) {
            if (!(pos >= 1 && pos <= size + 1)) {
                throw L.argError(2, "position out of bounds");
            }
        }
        LuaValue res = getI(L, L.arg1(1), pos);
        for (; pos < size; pos++) {
            setI(L, L.arg1(1), pos, getI(L, L.arg1(1), pos + 1));
        }
        setI(L, L.arg1(1), pos, LuaValue.ofNil());
        L.push(res);
        return 1;
    }

    /** {@code tmove} */
    private static int move(LuaState L) {
        long f = LuaAux.checkInteger(L, 2);
        long e = LuaAux.checkInteger(L, 3);
        long t = LuaAux.checkInteger(L, 4);
        int tt = (!L.isNoneArg(5) && !L.arg1(5).isNil()) ? 5 : 1;
        checkTab(L, 1, TAB_R);
        checkTab(L, tt, TAB_W);
        if (e >= f) {
            if (!(f > 0 || e - f < Long.MAX_VALUE)) {
                throw L.argError(3, "too many elements to move");
            }
            long n = e - f + 1;
            if (t > Long.MAX_VALUE - n + 1) {
                throw L.argError(4, "destination wrap around");
            }
            LuaValue src = L.arg1(1);
            LuaValue dst = L.arg1(tt);
            boolean forward = t > e || t <= f;
            if (forward) {
                for (long i = 0; i < n; i++) {
                    setI(L, dst, t + i, getI(L, src, f + i));
                }
            } else {
                for (long i = n - 1; i >= 0; i--) {
                    setI(L, dst, t + i, getI(L, src, f + i));
                }
            }
        }
        L.push(L.arg1(tt));
        return 1;
    }

    /** {@code tconcat} */
    private static int concat(LuaState L) {
        long last = auxGetN(L, 1, TAB_R);
        LuaString sep = LuaAux.optString(L, 2, LuaString.EMPTY);
        long i = LuaAux.optInteger(L, 3, 1);
        last = LuaAux.optInteger(L, 4, last);
        LuaAux.Buf b = new LuaAux.Buf();
        for (; i < last; i++) {
            addField(L, b, i);
            b.addString(sep);
        }
        if (i == last) {
            addField(L, b, i);
        }
        L.push(LuaValue.ofString(b.result()));
        return 1;
    }

    private static void addField(LuaState L, LuaAux.Buf b, long i) {
        LuaValue v = getI(L, L.arg1(1), i);
        if (!(v.isString() || v.isNumber())) {
            throw L.libError("invalid value (" + v.typeName() + ") at index " + i
                    + " in table for 'concat'");
        }
        b.addString(LuaAux.asString(v));
    }

    /** {@code tpack} */
    private static int pack(LuaState L) {
        int n = L.getTop();
        LuaTable t = new LuaTable(n, 1);
        for (int i = 0; i < n; i++) {
            t.set(LuaValue.ofInteger(i + 1), new LuaValue(L.arg1(i + 1)));
        }
        t.set("n", LuaValue.ofInteger(n));
        L.push(LuaValue.ofTable(t));
        return 1;
    }

    /** {@code tunpack} */
    private static int unpack(LuaState L) {
        long i = LuaAux.optInteger(L, 2, 1);
        long e = L.isNoneArg(3) || L.arg1(3).isNil() ? objLen(L, L.arg1(1))
                : LuaAux.checkInteger(L, 3);
        if (i > e) {
            return 0; // 空区间
        }
        long n = e - i; // 元素个数减一
        if (n >= Integer.MAX_VALUE - 1) {
            throw L.libError("too many results to unpack");
        }
        int count = (int) n + 1;
        LuaAux.checkStack(L, count, "too many results to unpack");
        LuaValue t = L.arg1(1);
        for (long k = i; k < e; k++) {
            L.push(getI(L, t, k));
        }
        L.push(getI(L, t, e));
        return count;
    }

    /* ==================== 快排 ==================== */

    private static final int RANLIMIT = 100;

    /** {@code lua_compare(LUA_OPLT)} */
    private static boolean lessThan(LuaState L, LuaValue a, LuaValue b) {
        if (a.isNumber() && b.isNumber()) {
            return Vm.ltNum(a, b);
        }
        if (a.isString() && b.isString()) {
            return Vm.lStrcmp(a.asString(), b.asString()) < 0;
        }
        return LuaTm.callOrderTm(L, a, b, Tm.TM_LT);
    }

    /** {@code sort_comp}：没给比较函数就用 {@code <}（含 {@code __lt}） */
    private static boolean sortComp(LuaState L, LuaValue a, LuaValue b) {
        if (L.isNoneArg(2) || L.arg1(2).isNil()) {
            return lessThan(L, a, b);
        }
        int f = L.top;
        L.push(L.arg1(2));
        L.push(a);
        L.push(b);
        L.call(f, 1);
        boolean r = L.stack[L.top - 1].toBoolean();
        L.top--;
        return r;
    }

    private static void swap(LuaState L, LuaValue t, int i, int j) {
        LuaValue vi = getI(L, t, i);
        LuaValue vj = getI(L, t, j);
        setI(L, t, i, vj);
        setI(L, t, j, vi);
    }

    /**
     * {@code partition}。
     * <p>
     * 两处 {@code luaL_error("invalid order function for sorting")} 是「比较函数
     * 不自洽」的哨兵：向中间扫的两条循环本该在中途相遇，谁越过了谁就说明
     * {@code comp} 不满足传递性。这不是防御性检查，是参考实现<b>会报</b>的错误，
     * 真实代码里靠它发现写错的比较函数。
     */
    private static int partition(LuaState L, LuaValue t, int lo, int up) {
        int i = lo;
        int j = up - 1;
        LuaValue p = getI(L, t, up - 1); // 枢轴
        while (true) {
            while (sortComp(L, getI(L, t, ++i), p)) {
                if (i == up - 1) {
                    throw L.libError("invalid order function for sorting");
                }
            }
            while (sortComp(L, p, getI(L, t, --j))) {
                if (j < i) {
                    throw L.libError("invalid order function for sorting");
                }
            }
            if (j < i) {
                swap(L, t, up - 1, i);
                return i;
            }
            swap(L, t, i, j);
        }
    }

    private static int choosePivot(int lo, int up, int rnd) {
        int r4 = (up - lo) / 4;
        int p = rnd % (r4 * 2) + (lo + r4);
        return p;
    }

    /** {@code auxsort}：递归用显式栈展开（C 版也把尾递归改成了循环） */
    private static void auxSort(LuaState L, LuaValue t, int lo, int up, int[] rnd) {
        while (lo < up) {
            int p;
            int n;
            if (sortComp(L, getI(L, t, up), getI(L, t, lo))) {
                swap(L, t, lo, up);
            }
            if (up - lo == 1) {
                return;
            }
            if (up - lo < RANLIMIT || rnd[0] == 0) {
                p = (lo + up) / 2;
            } else {
                p = choosePivot(lo, up, rnd[0]);
            }
            if (sortComp(L, getI(L, t, p), getI(L, t, lo))) {
                swap(L, t, p, lo);
            } else if (sortComp(L, getI(L, t, up), getI(L, t, p))) {
                swap(L, t, p, up);
            }
            if (up - lo == 2) {
                return;
            }
            swap(L, t, p, up - 1); // 枢轴换到 up-1
            p = partition(L, t, lo, up);
            if (p - lo < up - p) {
                auxSort(L, t, lo, p - 1, rnd);
                n = p - lo;
                lo = p + 1;
            } else {
                auxSort(L, t, p + 1, up, rnd);
                n = up - p;
                up = p - 1;
            }
            if ((up - lo) / 128 > n) {
                rnd[0] = randomizePivot();
            }
        }
    }

    /**
     * {@code l_randomizePivot}。
     * <p>
     * C 版拿 {@code clock()} 与 {@code time()} 的字节当随机源——它只影响枢轴选择，
     * 不影响排序结果。这里用 {@code System.nanoTime()} 的低位，作用相同。
     */
    private static int randomizePivot() {
        return (int) (System.nanoTime() & 0xFFFFFFFFL);
    }

    /** {@code sort} */
    private static int sort(LuaState L) {
        long n = auxGetN(L, 1, TAB_R | TAB_W);
        if (n > 1) {
            if (n >= Integer.MAX_VALUE) {
                throw L.argError(1, "array too big");
            }
            if (!L.isNoneArg(2) && !L.arg1(2).isNil()) {
                LuaAux.checkType(L, 2, Lua.TFUNCTION);
            }
            L.ensureSlots(L.ci.func + 3);
            for (int k = L.top; k < L.ci.func + 3; k++) {
                L.stack[k].setNil(); // lua_settop 补出来的槽是 nil，不是上一帧的残值
            }
            L.top = L.ci.func + 3;
            auxSort(L, L.arg1(1), 1, (int) n, new int[] { 0 });
        }
        return 0;
    }
}
