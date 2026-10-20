package com.github.esrrhs.javalua;

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
        L.callTmMightYield(f, 1);
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
            /*
             * 必须是 {@code e < Long.MAX_VALUE + f}，不能化简成
             * {@code e - f < Long.MAX_VALUE}。两者在无溢出时等价，但这里<b>就是
             * 要判溢出</b>：{@code f} 为负得厉害时 {@code e - f} 先回绕成负数，
             * 化简后的式子反而成立，判断就漏过去了。
             * <p>
             * 实测（上游 {@code sort.lua:185}）：
             * {@code table.move({}, math.mininteger, math.maxinteger, 1)}
             * 参考实现报 {@code bad argument #3 ... (too many elements to move)}，
             * 照化简的写法会走到下面那条，报
             * {@code bad argument #4 ... (destination wrap around)}。
             * <p>
             * {@code f > 0} 短路之后 {@code Long.MAX_VALUE + f} 的 {@code f}
             * 必为非正，因此这边的加法本身不会再溢出——与 C 版一致。
             */
            if (!(f > 0 || e < Long.MAX_VALUE + f)) {
                throw L.argError(3, "too many elements to move");
            }
            long n = e - f + 1;
            if (t > Long.MAX_VALUE - n + 1) {
                throw L.argError(4, "destination wrap around");
            }
            LuaValue src = L.arg1(1);
            LuaValue dst = L.arg1(tt);
            /*
             * C 版第三个条件 {@code tt != 1 && !lua_compare(1, tt, OPEQ)} 别省：
             * <b>源和目标是两张不同的表时一律正向拷贝</b>，跟 {@code t}、{@code f}
             * 的相对位置无关。省掉它，本该正向的情形会走成逆向。
             * <p>
             * 内容完全相同，但插入次序反了，表内部的数组/哈希布局就不同，
             * 于是 {@code #t} 跟着变——实测
             * {@code table.move({1}, -2, 1, -1, {1})} 之后目标表只有 {@code [2]=1}，
             * 参考实现 {@code #t == 0}（正向先挖空 {@code [1]}），
             * 逆向先塞 {@code [2]} 会把 alimit 顶到 2，得到 {@code #t == 2}。
             * <p>
             * 比较走 {@code lua_compare} 而不是引用比较，所以两张 {@code __eq}
             * 相等的表仍按同表处理。
             */
            boolean forward = t > e || t <= f
                    || (tt != 1 && !Vm.equalObj(L, src, dst));
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
        /*
         * {@code n} 必须按<b>无符号</b>比。C 版它是 {@code lua_Unsigned}，
         * {@code e - i} 溢出时按无符号回绕成一个<b>巨大</b>的数，于是
         * {@code unpack({}, minI, maxI)} 直接落进下面那句
         * {@code "too many results to unpack"}。
         * <p>
         * Java 的 {@code long} 回绕成<b>负数</b>，照有符号比就漏过去了：
         * {@code count} 于是变成 0，循环从 {@code minI} 一路 push 到
         * {@code maxI}——上游 {@code sort.lua} 第一节就是这么把
         * {@code table.unpack} 撑到 {@code OutOfMemoryError} 的。
         */
        if (Long.compareUnsigned(n, Integer.MAX_VALUE) >= 0) {
            throw L.libError("too many results to unpack");
        }
        int count = (int) n + 1;
        /*
         * 这里走的是 {@code lua_checkstack} + {@code luaL_error}，而不是
         * {@code luaL_checkstack}：后者会拼成
         * {@code "stack overflow (too many results to unpack)"}，而参考实现
         * {@code ltablib.c: tunpack} 给的是不带前缀的那句。
         * {@code count} 撑不下时两条路同归一处，所以分两步判。
         */
        if (!L.checkStackOk(count)) {
            throw L.libError("too many results to unpack");
        }
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
        /*
         * {@code sort_comp} 用的是 {@code lua_call}（= 不可 yield）：
         * 实测 {@code pcall(table.sort, {1,2,3}, coroutine.yield)} 在参考实现里
         * 得到 {@code false, "attempt to yield across a C-call boundary"}，
         * 也就是「根本没 yield 出去」。用可 yield 的 {@code call} 会真的挂起，
         * 恢复时 {@code table.sort} 那一帧没有续体，直接 NPE。
         */
        L.callNoYield(f, 1);
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

    /**
     * {@code choosePivot}。
     * <p>
     * <b>{@code IdxT} 是 {@code unsigned int}</b>，这一点必须照搬：C 的
     * {@code rnd % (r4 * 2)} 是无符号取模，结果恒在 {@code [0, r4*2)}。
     * Java 的 {@code int} 有符号，而 {@link #randomizePivot()} 给出的是
     * C 里 {@code unsigned int} 的那 32 个比特——有一半概率落成负数，
     * 此时 {@code rnd % m} 也是负的，{@code p} 会掉到 {@code lo} 以下，
     * 极端情形 {@code p <= 0}：{@code a[p]} 是 nil，于是
     * {@code sort_comp(nil, number)} 抛出
     * 「attempt to compare nil with number」。上游 {@code sort.lua} 用未播种的
     * {@code math.random()} 造 5 万个元素，这个缺陷就表现成<b>偶发</b>失败。
     */
    private static int choosePivot(int lo, int up, int rnd) {
        int r4 = (up - lo) / 4;
        int p = Integer.remainderUnsigned(rnd, r4 * 2) + (lo + r4);
        return p;
    }

    /**
     * {@code auxsort}：递归用显式栈展开（C 版也把尾递归改成了循环）。
     * <p>
     * {@code rnd} 是<b>按值</b>传的：C 里它是 {@code auxsort} 的形参，
     * 递归调用里的重新赋值不会漏回外层帧。早先这里用 {@code int[]} 共享，
     * 深层递归换掉的随机种子会一路影响外层剩下的分区——虽然只影响枢轴
     * 选择、不改变排序结果，但和参考实现不是同一个算法了。
     */
    private static void auxSort(LuaState L, LuaValue t, int lo, int up, int rnd) {
        while (lo < up) {
            int p;
            int n;
            if (sortComp(L, getI(L, t, up), getI(L, t, lo))) {
                swap(L, t, lo, up);
            }
            if (up - lo == 1) {
                return;
            }
            if (up - lo < RANLIMIT || rnd == 0) {
                p = (lo + up) / 2;
            } else {
                p = choosePivot(lo, up, rnd);
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
                rnd = randomizePivot();
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
            auxSort(L, L.arg1(1), 1, (int) n, 0);
        }
        return 0;
    }
}
