package com.github.esrrhs.fakescript.lua;

/**
 * 最小 math 库 —— 对应 {@code lmathlib.c} 的一小部分。
 * <p>
 * 阶段 3 只装差分比对真正用得上的那几个：整数边界常量、{@code type}、
 * {@code tointeger} 与几个取整函数。{@code math.type} 尤其关键——整数与浮点
 * 的子类型分裂在 {@code print} 的输出里是看不出来的（{@code print(1)} 与
 * {@code print(1.0)} 只差一个 {@code .0}），只有 {@code math.type} 能直接
 * 把它暴露出来，而这条分裂是本实现刻意保留的 C 版语义。
 * <p>
 * 其余（三角、随机、{@code math.max} 的多态细节等）随阶段 7 的完整 math 库一起补。
 */
public final class MathLib {

    private MathLib() {
    }

    public static void open(LuaState L) {
        LuaTable m = new LuaTable();
        m.set("maxinteger", LuaValue.ofInteger(Long.MAX_VALUE));
        m.set("mininteger", LuaValue.ofInteger(Long.MIN_VALUE));
        m.set("pi", LuaValue.ofFloat(Math.PI));
        m.set("huge", LuaValue.ofFloat(Double.POSITIVE_INFINITY));

        setFn(m, "type", MathLib::type);
        setFn(m, "tointeger", MathLib::tointeger);
        setFn(m, "abs", MathLib::abs);
        setFn(m, "ceil", MathLib::ceil);
        setFn(m, "floor", MathLib::floor);
        setFn(m, "sqrt", MathLib::sqrt);
        setFn(m, "fmod", MathLib::fmod);
        setFn(m, "max", MathLib::max);
        setFn(m, "min", MathLib::min);

        L.globals.set("math", LuaValue.ofTable(m));
    }

    /** {@code math.type}：子类型探针 */
    private static int type(LuaState L) {
        LuaValue v = L.arg(0);
        if (v.isInteger()) {
            L.push(LuaValue.ofString(LuaString.latin1("integer")));
        } else if (v.isFloat()) {
            L.push(LuaValue.ofString(LuaString.latin1("float")));
        } else {
            L.pushNil();
        }
        return 1;
    }

    /**
     * {@code math.tointeger}：能精确取到整数才给，否则 nil。
     * <p>
     * 注意这里是 {@code lua_tointegerx} 而不是 {@code luaV_tointegerns}——
     * <b>带字符串转换</b>：{@code math.tointeger('7') == 7}。库函数参数走的是
     * {@code luaL_check*} 那一套（会做类型转换），与 VM 算术指令刻意不转换
     * 的做法相反，别把两者写成同一个。
     */
    private static int tointeger(LuaState L) {
        LuaValue v = L.arg(0);
        // 不能写成三元表达式：一边是 long、一边是 Long 会被自动拆箱，
        // 返回 null 的那次直接在拆箱处 NPE
        Long i;
        if (v.isInteger()) {
            i = Long.valueOf(v.i);
        } else {
            i = Vm.toInteger(v, Arith.F2I_EQ);
        }
        if (i == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofInteger(i));
        }
        return 1;
    }

    private static int abs(LuaState L) {
        LuaValue v = L.arg(0);
        if (v.isInteger()) {
            L.push(LuaValue.ofInteger(Math.abs(v.i)));
        } else {
            L.push(LuaValue.ofFloat(Math.abs(num(L, 0))));
        }
        return 1;
    }

    /** {@code pushnumint}：能表示为整数就返回整数，否则返回浮点 */
    private static void pushIntOrFloat(LuaState L, double d) {
        Long i = Arith.fltToInteger(d, Arith.F2I_EQ);
        L.push((i == null) ? LuaValue.ofFloat(d) : LuaValue.ofInteger(i));
    }

    private static int ceil(LuaState L) {
        LuaValue v = L.arg(0);
        if (v.isInteger()) {
            L.push(v);
        } else {
            pushIntOrFloat(L, Math.ceil(num(L, 0)));
        }
        return 1;
    }

    private static int floor(LuaState L) {
        LuaValue v = L.arg(0);
        if (v.isInteger()) {
            L.push(v);
        } else {
            pushIntOrFloat(L, Math.floor(num(L, 0)));
        }
        return 1;
    }

    private static int sqrt(LuaState L) {
        L.push(LuaValue.ofFloat(Math.sqrt(num(L, 0))));
        return 1;
    }

    /**
     * {@code math.fmod}：两个整数就走<b>整数</b>取模，否则走 C 的 {@code fmod}。
     * <p>
     * 两条路径的结果符号规则不同，不能只留浮点那一条：整数路径是 C 的
     * {@code %}（向零取整，{@code fmod(-7,3) == -1}），浮点路径是
     * {@code fmod}（同样是向零，但结果一定是 float）。写成统一浮点会把
     * {@code math.fmod(7, 3)} 从 {@code 1} 变成 {@code 1.0}——
     * {@code math.type} 一测就露。
     */
    private static int fmod(LuaState L) {
        LuaValue a = L.arg(0);
        LuaValue b = L.arg(1);
        if (a.isInteger() && b.isInteger()) {
            long d = b.i;
            if (d == 0) {
                throw L.libError("bad argument #2 to 'fmod' (zero)");
            }
            if (d == -1) {
                L.push(LuaValue.ofInteger(0)); // 避开 Long.MIN_VALUE / -1 溢出
            } else {
                L.push(LuaValue.ofInteger(a.i % d));
            }
            return 1;
        }
        L.push(LuaValue.ofFloat(Arith.modf(num(L, 0), num(L, 1))));
        return 1;
    }

    /** {@code math.max}：保持操作数的子类型（有浮点就走浮点） */
    private static int max(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.libError("bad argument #1 to 'max' (number expected, got no value)");
        }
        LuaValue best = L.arg(0);
        for (int i = 1; i < n; i++) {
            LuaValue v = L.arg(i);
            if (Vm.ltNum(best, v)) {
                best = v;
            }
        }
        L.push(best);
        return 1;
    }

    private static int min(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.libError("bad argument #1 to 'min' (number expected, got no value)");
        }
        LuaValue best = L.arg(0);
        for (int i = 1; i < n; i++) {
            LuaValue v = L.arg(i);
            if (Vm.ltNum(v, best)) {
                best = v;
            }
        }
        L.push(best);
        return 1;
    }

    private static double num(LuaState L, int i) {
        LuaValue v = L.arg(i);
        if (v.isInteger()) {
            return v.i;
        }
        if (v.isFloat()) {
            return v.n;
        }
        LuaValue n = Vm.strToNum(v);
        if (n == null) {
            throw L.libError("bad argument #" + (i + 1) + " to math function (number expected, got "
                    + v.typeName() + ")");
        }
        return n.isInteger() ? n.i : n.n;
    }

    private static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(name, LuaValue.ofFunction(f));
    }
}
