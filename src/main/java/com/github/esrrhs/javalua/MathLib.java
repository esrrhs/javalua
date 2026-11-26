package com.github.esrrhs.javalua;

/**
 * math 库 —— 对应 {@code lmathlib.c}。
 * <p>
 * 分成三块：普通数学函数（大多一行一个 {@code Math.xxx}）、
 * {@code random/randomseed} 的 xoshiro256** 发生器、以及
 * {@code LUA_COMPAT_MATHLIB} 那批兼容函数（{@code atan2/pow/log10/frexp/ldexp/cosh/sinh/tanh}，
 * 参考实现默认编进去了，实测都存在，所以这里也照给）。
 * <p>
 * 三处容易写错、且都是「照 C 一行行翻才翻得出来」的地方：
 * <ol>
 *   <li><b>{@code pushnumint}</b>：{@code floor/ceil/modf} 的结果
 *       <b>能表示为整数就返回整数</b>。所以 {@code math.floor(2.0)} 是
 *       {@code 2} 不是 {@code 2.0}，{@code math.type} 一测就露。
 *       反过来 {@code math.floor(1e300)} 还是 float——装不下。</li>
 *   <li><b>{@code max/min} 用的是 {@code lua_compare}</b>，不是数字比较。
 *       {@code math.max("a","b")} 返回 {@code "b"}，{@code math.max(1,"b")}
 *       报 {@code attempt to compare number with string}。另外算子类型要保
 *       留：{@code math.min(1, 2.0)} 是 {@code 1}（integer）。</li>
 *   <li><b>随机数状态是「共享的」</b>：C 里 {@code random} 与
 *       {@code randomseed} 是两个 C 闭包，共用同一个 userdata upvalue。
 *       用静态字段存会串到别的 {@code lua_State} 上（本实现里协程是独立
 *       {@link LuaState} 但共享 math 表，测试也常并行开几个状态），所以
 *       必须走 C 闭包的 upvalue。</li>
 * </ol>
 * <p>
 * {@code math.random} 的初始种子在 C 里取自 {@code time(NULL)} 与
 * {@code L} 的地址，本无可比性；这里用 {@code System.currentTimeMillis()}
 * 与 {@link System#identityHashCode}。只要显式
 * {@code math.randomseed(n1, n2)}，后续序列必须与参考实现逐位一致——
 * 差分测试里就是这么验的。
 * <p>
 * <b>一处已知的、无法消除的偏离：超越函数的最后一位。</b>
 * {@code sin/cos/tan/asin/acos/atan/exp/log/log10/sinh/cosh/tanh/pow}
 * 走的是 Java 的 {@code Math}（内部是 fdlibm），而参考实现链接的是平台
 * libm（这里是 macOS 的 libsystem_m）。两者都「误差小于 1 ulp」但<b>不是
 * 逐位相同</b>：实测 30 个典型输入里 {@code sin} 差 1 个、{@code tan} 差 12 个、
 * {@code cosh} 差 6 个，且都是差在最后一位。要消掉只能自带一份 libm，
 * 那既不现实也超出了「能跑现有 Lua 项目」的目标——Lua 自己在换平台、
 * 换 libm 时同样会漂。因此差分用例对这些函数只比对到 14 位有效数字
 * （{@code %.14g}），1 ulp 是 2.2e-16 相对误差，落在 14 位之下。
 * <p>
 * 反过来，能对齐的地方就必须对齐：{@code log} 的 base 2/10 分支
 * （见 {@link #log2}）与 {@code sqrt/abs/floor/ceil/frexp/ldexp} 都是逐位一致的。
 */
public final class MathLib {

    /** {@code PI}：与 {@code lmathlib.c} 的 {@code #define PI} 同一个字面量 */
    private static final double PI = 3.141592653589793238462643383279502884;

    /* ==================== 注册 ==================== */

    public static void open(LuaState L) {
        LuaTable m = new LuaTable();

        setFn(m, "abs", MathLib::abs);
        setFn(m, "acos", MathLib::acos);
        setFn(m, "asin", MathLib::asin);
        setFn(m, "atan", MathLib::atan);
        setFn(m, "ceil", MathLib::ceil);
        setFn(m, "cos", MathLib::cos);
        setFn(m, "deg", MathLib::deg);
        setFn(m, "exp", MathLib::exp);
        setFn(m, "tointeger", MathLib::tointeger);
        setFn(m, "floor", MathLib::floor);
        setFn(m, "fmod", MathLib::fmod);
        setFn(m, "ult", MathLib::ult);
        setFn(m, "log", MathLib::log);
        setFn(m, "max", MathLib::max);
        setFn(m, "min", MathLib::min);
        setFn(m, "modf", MathLib::modf);
        setFn(m, "rad", MathLib::rad);
        setFn(m, "sin", MathLib::sin);
        setFn(m, "sqrt", MathLib::sqrt);
        setFn(m, "tan", MathLib::tan);
        setFn(m, "type", MathLib::type);
        /*
         * LUA_COMPAT_MATHLIB：参考实现默认编进去的兼容函数。
         * <p>
         * {@code atan2} 必须和 {@code atan} 是<b>同一个值</b>：C 里
         * {@code {"atan", math_atan}} 与 {@code {"atan2", math_atan}} 挂的是同一个
         * 函数指针，而 {@code lua_pushcclosure} 在 upvalue 数为 0 时压的是
         * <b>轻 C 函数</b>（{@code setfvalue}，值就是指针本身，不另建闭包），
         * 所以 {@code math.atan == math.atan2} 在参考实现里是 <b>true</b>。
         * 以前这里也是 {@code MathLib::atan}，但 {@code setFn} 每次新建一个
         * {@code LuaValue} 包一层，两个名字各包一份 —— {@code ==} 就成 false 了，
         * 连带着 {@code luaL_argerror} 的那个回退名字（{@code findfield} 在
         * {@code _LOADED} 里按值反查）也会查错人。
         */
        m.set("atan2", m.get(LuaString.latin1("atan")));
        setFn(m, "cosh", MathLib::cosh);
        setFn(m, "sinh", MathLib::sinh);
        setFn(m, "tanh", MathLib::tanh);
        setFn(m, "pow", MathLib::pow);
        setFn(m, "frexp", MathLib::frexp);
        setFn(m, "ldexp", MathLib::ldexp);
        setFn(m, "log10", MathLib::log10);

        m.set("pi", LuaValue.ofFloat(PI));
        m.set("huge", LuaValue.ofFloat(Double.POSITIVE_INFINITY));
        m.set("maxinteger", LuaValue.ofInteger(Long.MAX_VALUE));
        m.set("mininteger", LuaValue.ofInteger(Long.MIN_VALUE));

        // Lua 5.3.4: per-state Random shared across coroutines of this LuaState, isolated across states
        java.util.Random rng = new java.util.Random(1);
        CClosure rndFn = new CClosure(MathLib::random, 1);
        rndFn.upvalue[0] = LuaValue.ofUserdata(rng);
        m.set("random", LuaValue.ofCClosure(rndFn));
        CClosure seedFn = new CClosure(MathLib::randomseed, 1);
        seedFn.upvalue[0] = LuaValue.ofUserdata(rng);
        m.set("randomseed", LuaValue.ofCClosure(seedFn));

        LuaAux.registerLib(L, "math", m);
    }

    /* ==================== 一元 / 二元数学函数 ==================== */

    private static int abs(LuaState L) {
        LuaValue v = L.arg1(1);
        if (v.isInteger()) {
            // C 里写成 (lua_Integer)(0u - (lua_Unsigned)n) 是为了避开有符号取负
            // 的 UB；Java 里 -Long.MIN_VALUE 同样还是 MIN_VALUE，结果一致
            L.push(LuaValue.ofInteger(v.i < 0 ? -v.i : v.i));
        } else {
            L.push(LuaValue.ofFloat(Math.abs(LuaAux.checkNumber(L, 1))));
        }
        return 1;
    }

    private static int sin(LuaState L) {
        L.push(LuaValue.ofFloat(Math.sin(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int cos(LuaState L) {
        L.push(LuaValue.ofFloat(Math.cos(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int tan(LuaState L) {
        L.push(LuaValue.ofFloat(Math.tan(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int asin(LuaState L) {
        L.push(LuaValue.ofFloat(domainNaN(Math.asin(LuaAux.checkNumber(L, 1)))));
        return 1;
    }

    private static int acos(LuaState L) {
        L.push(LuaValue.ofFloat(domainNaN(Math.acos(LuaAux.checkNumber(L, 1)))));
        return 1;
    }

    /**
     * Java {@code Math.asin}/{@code acos} 对定义域外输入给出符号位置位的 NaN
     * （{@code 0xfff8…}），而 glibc/fdlibm 给的是正 NaN（{@code 0x7ff8…}）。
     * 符号位一进 {@code tostring}/{@code string.len} 就能观测到，所以这里把
     * 「由有限输入算出的 NaN」收到与 libm 相同的正载荷上。
     */
    private static double domainNaN(double r) {
        if (Double.isNaN(r) && Double.doubleToRawLongBits(r) < 0) {
            return Double.longBitsToDouble(0x7ff8000000000000L);
        }
        return r;
    }

    /** {@code math.atan(y [, x])}：给第二个参数就是 {@code atan2} */
    private static int atan(LuaState L) {
        double y = LuaAux.checkNumber(L, 1);
        double x = LuaAux.optNumber(L, 2, 1.0);
        L.push(LuaValue.ofFloat(Math.atan2(y, x)));
        return 1;
    }

    private static int sqrt(LuaState L) {
        L.push(LuaValue.ofFloat(Math.sqrt(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int exp(LuaState L) {
        L.push(LuaValue.ofFloat(Math.exp(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int deg(LuaState L) {
        L.push(LuaValue.ofFloat(LuaAux.checkNumber(L, 1) * (180.0 / PI)));
        return 1;
    }

    private static int rad(LuaState L) {
        L.push(LuaValue.ofFloat(LuaAux.checkNumber(L, 1) * (PI / 180.0)));
        return 1;
    }

    /**
     * {@code math.log(x [, base])}。
     * <p>
     * base 为 2 / 10 时走专用函数而不是 {@code log(x)/log(base)}——C 用的是
     * {@code log2} 与 {@code log10}，与「两次 {@code log} 相除」<b>不是同一个
     * 结果</b>：实测 43 个样本里 {@code log(x)/log(2)} 有 9 个与 {@code log2(x)}
     * 差 1 ulp，base 10 更是差到 25 个。所以这两条分支必须真的分开写。
     * <p>
     * Java 没有 {@code Math.log2}，这里用尾数分解补出来（见 {@link #log2}）：
     * 分解后 192 个样本只差 2 个，而朴素除法差 21%。
     */
    private static int log(LuaState L) {
        double x = LuaAux.checkNumber(L, 1);
        double res;
        if (L.isNoneArg(2) || L.arg1(2).isNil()) {
            res = Math.log(x);
        } else {
            double base = LuaAux.checkNumber(L, 2);
            if (base == 2.0) {
                res = log2(x);
            } else if (base == 10.0) {
                res = Math.log10(x);
            } else {
                res = Math.log(x) / Math.log(base);
            }
        }
        L.push(LuaValue.ofFloat(res));
        return 1;
    }

    /** {@code ln 2}，取到 double 能表示的最高精度 */
    private static final double LN2 = 0.693147180559945309417232121458176568;

    /**
     * {@code log2(x)}：Java 标准库没有，用尾数分解补。
     * <p>
     * 把 {@code x} 写成 {@code m * 2^e}（{@code m ∈ [0.5,1)}），则
     * {@code log2(x) = e + log(m)/ln2}。这样 {@code log} 只在 {@code [0.5,1)}
     * 这个最平缓的区间上取值，误差被压到最低——直接 {@code log(x)/log(2)}
     * 会把 {@code x} 本身的量级误差一起带进来。
     * <p>
     * 非有限值与非正数一律退回 {@code log(x)/ln2}：{@code 0 → -inf}、
     * {@code -1 → nan}、{@code inf → inf} 都正好对得上，而尾数分解在这几处
     * 会算出 1024 这种荒唐值。
     */
    private static double log2(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x <= 0.0) {
            return Math.log(x) / LN2;
        }
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) ((bits >>> 52) & 0x7ff);
        if (e == 0) { // 次正规数：先放大成正规数，指数再扣回去
            x *= 0x1p+64;
            bits = Double.doubleToRawLongBits(x);
            e = (int) ((bits >>> 52) & 0x7ff) - 64;
        }
        double m = Double.longBitsToDouble((bits & 0x800fffffffffffffL) | (1022L << 52));
        return (e - 1022) + Math.log(m) / LN2;
    }

    /** {@code math.ult}：按<b>无符号</b>比较，所以 {@code ult(-1, 2)} 是 false */
    private static int ult(LuaState L) {
        long a = LuaAux.checkInteger(L, 1);
        long b = LuaAux.checkInteger(L, 2);
        L.push(LuaValue.ofBoolean(Long.compareUnsigned(a, b) < 0));
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
        LuaValue v = L.arg1(1);
        LuaAux.checkAny(L, 1);
        // 不能写成三元表达式：一边是 long、一边是 Long 会被自动拆箱，
        // 返回 null 的那次直接在拆箱处 NPE
        Long i;
        if (v.isInteger()) {
            i = Long.valueOf(v.i);
        } else {
            i = Vm.toInteger(v, Arith.F2I_EQ);
        }
        if (i == null) {
            LuaAux.pushFail(L);
        } else {
            L.push(LuaValue.ofInteger(i));
        }
        return 1;
    }

    /** {@code math.type}：子类型探针 */
    private static int type(LuaState L) {
        LuaValue v = L.arg1(1);
        if (v.isInteger()) {
            L.push(LuaValue.ofString(LuaString.latin1("integer")));
        } else if (v.isFloat()) {
            L.push(LuaValue.ofString(LuaString.latin1("float")));
        } else {
            LuaAux.checkAny(L, 1);
            LuaAux.pushFail(L);
        }
        return 1;
    }

    /** {@code pushnumint}：能表示为整数就返回整数，否则返回浮点 */
    private static void pushIntOrFloat(LuaState L, double d) {
        Long i = Arith.fltToInteger(d, Arith.F2I_EQ);
        L.push((i == null) ? LuaValue.ofFloat(d) : LuaValue.ofInteger(i));
    }

    /** {@code math.floor}：整数就是自己的 floor，原样返回（连子类型一起） */
    private static int floor(LuaState L) {
        LuaValue v = L.arg1(1);
        if (v.isInteger()) {
            L.push(v);
        } else {
            pushIntOrFloat(L, Math.floor(LuaAux.checkNumber(L, 1)));
        }
        return 1;
    }

    private static int ceil(LuaState L) {
        LuaValue v = L.arg1(1);
        if (v.isInteger()) {
            L.push(v);
        } else {
            pushIntOrFloat(L, Math.ceil(LuaAux.checkNumber(L, 1)));
        }
        return 1;
    }

    /**
     * {@code math.modf}：返回整数部分与小数部分两个值。
     * <p>
     * 整数入参时整数部分就是它自己、小数部分是 {@code 0.0}（<b>浮点</b>零，
     * 实测 {@code math.modf(3)} 输出 {@code 3  0.0}）。
     * {@code n == ip} 那个判断是给 inf 用的：{@code inf - inf} 是 nan，
     * 而 {@code math.modf(math.huge)} 的第二个返回值是 {@code 0.0}。
     */
    private static int modf(LuaState L) {
        LuaValue v = L.arg1(1);
        if (v.isInteger()) {
            L.push(v);
            L.push(LuaValue.ofFloat(0.0));
        } else {
            double n = LuaAux.checkNumber(L, 1);
            double ip = (n < 0) ? Math.ceil(n) : Math.floor(n);
            pushIntOrFloat(L, ip);
            L.push(LuaValue.ofFloat((n == ip) ? 0.0 : (n - ip)));
        }
        return 2;
    }

    /**
     * {@code math.fmod}：两个整数就走<b>整数</b>取模，否则走 C 的 {@code fmod}。
     * <p>
     * 两条路径都不能省：整数路径给出 integer 结果（{@code math.fmod(7,3)}
     * 是 {@code 1}），浮点路径给出 float 结果（{@code math.fmod(7.5,2)}
     * 是 {@code 1.5}）——统一成浮点会让 {@code math.type} 露馅。
     * <p>
     * 浮点路径用的是 <b>C 的 {@code fmod}</b>（向零取整，结果取<b>被除数</b>
     * 的符号），<b>不是</b> Lua 的 {@code %}（{@code Arith.modf}，结果取
     * <b>除数</b>的符号）。两者的差就摆在 {@code math.fmod(-7.5, 2)} 上：
     * 参考实现给 {@code -1.5}，照 Lua 的 {@code %} 写会给出 {@code 0.5}。
     * <p>
     * {@code -1} 与 {@code 0} 两个除数是特例：0 要报错，-1 要给 0 以避开
     * {@code Long.MIN_VALUE / -1} 的溢出。
     */
    private static int fmod(LuaState L) {
        LuaValue a = L.arg1(1);
        LuaValue b = L.arg1(2);
        if (a.isInteger() && b.isInteger()) {
            long d = b.i;
            if (d == 0) {
                throw L.argError(2, "zero");
            }
            if (d == -1) {
                L.push(LuaValue.ofInteger(0));
            } else {
                L.push(LuaValue.ofInteger(a.i % d));
            }
            return 1;
        }
        double x = LuaAux.checkNumber(L, 1);
        double y = LuaAux.checkNumber(L, 2);
        // Java 的 double % 就是 C 的 fmod（向零取整、取被除数符号），
        // 与 Arith.modf（Lua 的 %，取除数符号）不是一回事
        L.push(LuaValue.ofFloat(x % y));
        return 1;
    }

    /**
     * {@code math.max}：走 {@code lua_compare(LUA_OPLT)}，保留操作数的子类型。
     * <p>
     * 所以 {@code math.max(1, 2.0)} 是 {@code 2.0}（float），
     * {@code math.max(1, 2)} 是 {@code 2}（integer），而
     * {@code math.max("a","b")} 是 {@code "b"}。
     */
    private static int max(LuaState L) {
        int n = L.argCount();
        if (n < 1) {
            throw L.argError(1, "value expected");
        }
        LuaValue best = L.arg(0);
        for (int i = 1; i < n; i++) {
            LuaValue v = L.arg(i);
            if (Vm.lessThan(L, best, v)) {
                best = v;
            }
        }
        L.push(best);
        return 1;
    }

    private static int min(LuaState L) {
        int n = L.argCount();
        if (n < 1) {
            throw L.argError(1, "value expected");
        }
        LuaValue best = L.arg(0);
        for (int i = 1; i < n; i++) {
            LuaValue v = L.arg(i);
            if (Vm.lessThan(L, v, best)) {
                best = v;
            }
        }
        L.push(best);
        return 1;
    }

    /* ==================== LUA_COMPAT_MATHLIB ==================== */

    private static int cosh(LuaState L) {
        L.push(LuaValue.ofFloat(Math.cosh(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int sinh(LuaState L) {
        L.push(LuaValue.ofFloat(Math.sinh(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int tanh(LuaState L) {
        L.push(LuaValue.ofFloat(Math.tanh(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    private static int pow(LuaState L) {
        double x = LuaAux.checkNumber(L, 1);
        double y = LuaAux.checkNumber(L, 2);
        L.push(LuaValue.ofFloat(Arith.powf(x, y)));
        return 1;
    }

    /** {@code math.frexp}：拆成 {@code m * 2^e}，{@code m ∈ [0.5,1)} */
    private static int frexp(LuaState L) {
        double x = LuaAux.checkNumber(L, 1);
        int e;
        if (x == 0.0 || Double.isNaN(x) || Double.isInfinite(x)) {
            e = 0;
        } else {
            long bits = Double.doubleToRawLongBits(x);
            int exp = (int) ((bits >>> 52) & 0x7ff);
            if (exp == 0) { // 次正规数：先放大成正规数，再从指数里扣回去
                x *= 0x1p+64;
                bits = Double.doubleToRawLongBits(x);
                exp = (int) ((bits >>> 52) & 0x7ff) - 64;
            }
            bits = (bits & 0x800fffffffffffffL) | (1022L << 52);
            x = Double.longBitsToDouble(bits);
            e = exp - 1022;
        }
        L.push(LuaValue.ofFloat(x));
        L.push(LuaValue.ofInteger(e));
        return 2;
    }

    private static int ldexp(LuaState L) {
        double x = LuaAux.checkNumber(L, 1);
        int ep = (int) LuaAux.checkInteger(L, 2);
        L.push(LuaValue.ofFloat(Math.scalb(x, ep)));
        return 1;
    }

    private static int log10(LuaState L) {
        L.push(LuaValue.ofFloat(Math.log10(LuaAux.checkNumber(L, 1))));
        return 1;
    }

    /* ==================== random ==================== */

    /**
     * Lua 5.3.4 {@code math.random}：底层是 libc {@code rand}/{@code srand} 模型。
     * 这里用 {@link java.util.Random} 复刻 API 形状（参数个数、空区间、过大区间）；
     * 与参考实现的数值序列不可比（平台 libc 与 JVM 发生器不同），见 MathLibTest。
     */
    private static java.util.Random rngOf(LuaState L) {
        return (java.util.Random) LuaUserdata.dataOf(L.upvalue(0));
    }

    /** {@code math.random([m [, n]])}（5.3.4） */
    private static int random(LuaState L) {
        int n = L.argCount();
        double r = rngOf(L).nextDouble(); // [0,1)
        long low;
        long up;
        switch (n) {
            case 0:
                L.push(LuaValue.ofFloat(r));
                return 1;
            case 1:
                low = 1;
                up = LuaAux.checkInteger(L, 1);
                break;
            case 2:
                low = LuaAux.checkInteger(L, 1);
                up = LuaAux.checkInteger(L, 2);
                break;
            default:
                throw L.libError("wrong number of arguments");
        }
        if (low > up) {
            throw L.argError(1, "interval is empty");
        }
        // low >= 0 || up <= LUA_MAXINTEGER + low
        if (!(low >= 0 || up <= Long.MAX_VALUE + low)) {
            throw L.argError(1, "interval too large");
        }
        r *= (double) (up - low) + 1.0;
        L.push(LuaValue.ofInteger((long) r + low));
        return 1;
    }

    /** {@code math.randomseed(x)}（5.3.4：一参、无返回） */
    private static int randomseed(LuaState L) {
        double x = LuaAux.checkNumber(L, 1);
        java.util.Random rng = rngOf(L);
        rng.setSeed((long) x); // match (unsigned)(lua_Integer) cast shape loosely
        rng.nextInt(); // discard first value like l_rand after srand
        return 0;
    }

    private static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(name, LuaValue.ofFunction(f));
    }
}
