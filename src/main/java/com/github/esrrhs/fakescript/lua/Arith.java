package com.github.esrrhs.fakescript.lua;

/**
 * 常量折叠需要的那部分算术 —— 对应 {@code lobject.c} 的 {@code luaO_rawarith} /
 * {@code intarith} / {@code numarith}，以及 {@code lvm.c} 的 {@code luaV_idiv} /
 * {@code luaV_mod} / {@code luaV_modf} / {@code luaV_shiftl}。
 * <p>
 * 为什么现在就要它：编译期的常量折叠跑的就是这套运算，折叠结果会被直接写进
 * 常量表。若折叠用的算术与运行期的不是同一套，就会出现
 * 「同一个表达式，常量写法与变量写法结果不同」——那是最难查的一类 bug。
 * <p>
 * 几处刻意的「不像 Java」：
 * <ul>
 *   <li>整数除法 {@code //} 是<b>向下取整</b>，C/Java 的 {@code /} 是向零截断，
 *       所以要在结果需要时减一；</li>
 *   <li>取模的符号跟随<b>除数</b>，而 Java 的 {@code %} 跟随被除数；</li>
 *   <li>右移是<b>逻辑</b>右移（{@code intop} 先转无符号），空位补 0，
 *       不是 Java 的算术右移；</li>
 *   <li>移位量超出字长时结果是 0，而 Java 的移位会把位数按 64 取模；</li>
 *   <li>{@code math.mininteger // -1} 不溢出（返回 {@code -mininteger}）。</li>
 * </ul>
 */
final class Arith {

    private Arith() {
    }

    /* ORDER TM / ORDER OP —— 与 lua.h 的 LUA_OP* 一致 */
    static final int OP_ADD = 0;
    static final int OP_SUB = 1;
    static final int OP_MUL = 2;
    static final int OP_MOD = 3;
    static final int OP_POW = 4;
    static final int OP_DIV = 5;
    static final int OP_IDIV = 6;
    static final int OP_BAND = 7;
    static final int OP_BOR = 8;
    static final int OP_BXOR = 9;
    static final int OP_SHL = 10;
    static final int OP_SHR = 11;
    static final int OP_UNM = 12;
    static final int OP_BNOT = 13;

    /** 整数的位数 */
    private static final int NBITS = 64;

    /**
     * {@code luaO_rawarith}：不经元方法的原始算术。
     *
     * @return 结果值；操作数类型不合适时返回 {@code null}
     */
    static LuaValue rawArith(int op, LuaValue p1, LuaValue p2) {
        switch (op) {
        case OP_BAND:
        case OP_BOR:
        case OP_BXOR:
        case OP_SHL:
        case OP_SHR:
        case OP_BNOT: { // 位运算只在整数上做
            Long i1 = toIntegerNs(p1);
            Long i2 = toIntegerNs(p2);
            if (i1 == null || i2 == null) {
                return null;
            }
            return LuaValue.ofInteger(intArith(op, i1, i2));
        }
        case OP_DIV:
        case OP_POW: { // 只在浮点上做
            Double n1 = toNumberNs(p1);
            Double n2 = toNumberNs(p2);
            if (n1 == null || n2 == null) {
                return null;
            }
            return LuaValue.ofFloat(numArith(op, n1, n2));
        }
        default: {
            if (p1.isInteger() && p2.isInteger()) {
                return LuaValue.ofInteger(intArith(op, p1.i, p2.i));
            }
            Double n1 = toNumberNs(p1);
            Double n2 = toNumberNs(p2);
            if (n1 == null || n2 == null) {
                return null;
            }
            return LuaValue.ofFloat(numArith(op, n1, n2));
        }
        }
    }

    private static Double toNumberNs(LuaValue v) {
        if (v.isInteger()) {
            return (double) v.i;
        }
        if (v.isFloat()) {
            return v.n;
        }
        return null;
    }

    /** {@code luaV_tointegerns}：不做「字符串转数字」，只认真正的数字 */
    static Long toIntegerNs(LuaValue v) {
        if (v.isFloat()) {
            return fltToInteger(v.n, F2I_EQ);
        }
        if (v.isInteger()) {
            return v.i;
        }
        return null;
    }

    /* ---------- 浮点 → 整数 ---------- */

    static final int F2I_EQ = 0;
    static final int F2I_FLOOR = 1;
    static final int F2I_CEIL = 2;

    /** {@code luaV_flttointeger} */
    static Long fltToInteger(double n, int mode) {
        double f = Math.floor(n);
        if (n != f) { // 不是整值？
            if (mode == F2I_EQ) {
                return null; // 要求必须是整值，失败
            } else if (mode == F2I_CEIL) {
                f += 1;
            }
        }
        return numberToInteger(f);
    }

    /** {@code lua_numbertointeger}：超出 int64 可表示范围时失败 */
    private static Long numberToInteger(double n) {
        if (n >= (double) Long.MIN_VALUE && n < -((double) Long.MIN_VALUE)) {
            return (long) n;
        }
        return null;
    }

    /* ---------- 整数运算 ---------- */

    /** {@code intarith} */
    private static long intArith(int op, long v1, long v2) {
        switch (op) {
        case OP_ADD:
            return v1 + v2;
        case OP_SUB:
            return v1 - v2;
        case OP_MUL:
            return v1 * v2;
        case OP_MOD:
            return mod(v1, v2);
        case OP_IDIV:
            return idiv(v1, v2);
        case OP_BAND:
            return v1 & v2;
        case OP_BOR:
            return v1 | v2;
        case OP_BXOR:
            return v1 ^ v2;
        case OP_SHL:
            return shiftl(v1, v2);
        case OP_SHR:
            return shiftl(v1, -v2);
        case OP_UNM:
            return -v1;
        case OP_BNOT:
            return ~v1;
        default:
            throw new IllegalArgumentException("bad int op " + op);
        }
    }

    /** {@code luaV_idiv}：向下取整的整数除法 */
    static long idiv(long m, long n) {
        if (n == 0) {
            throw new LuaError(LuaValue.ofString(LuaString.latin1("attempt to divide by zero")));
        }
        if (n == -1) {
            return -m; // 避开 mininteger // -1 的溢出
        }
        long q = m / n;
        if ((m ^ n) < 0 && m % n != 0) {
            q -= 1;
        }
        return q;
    }

    /** {@code luaV_mod}：结果的符号跟随除数 */
    static long mod(long m, long n) {
        if (n == 0) {
            throw new LuaError(LuaValue.ofString(LuaString.latin1("attempt to perform 'n%%0'")));
        }
        if (n == -1) {
            return 0; // 避开溢出
        }
        long r = m % n;
        if (r != 0 && (r ^ n) < 0) {
            r += n;
        }
        return r;
    }

    /**
     * {@code luaV_shiftl}：移位数超出字长时结果是 0（Java 会按 64 取模，不一样）。
     * <p>
     * 注意右移是<b>逻辑右移</b>：C 里的 {@code intop(>>, x, -y)} 先把两个操作数
     * 转成 {@code lua_Unsigned} 再移，空位补 0。所以 {@code -8 >> 2} 是
     * {@code 0x3FFFFFFFFFFFFFFE}（4611686018427387902）而不是 {@code -2}，
     * 与 Lua 手册「两个移位运算符都用 0 填充空位」一致。
     * 参考实现实测过，别照 Java 直觉写成 {@code >>}。
     */
    static long shiftl(long x, long y) {
        if (y < 0) {
            if (y <= -NBITS) {
                return 0;
            }
            return x >>> (-y);
        }
        if (y >= NBITS) {
            return 0;
        }
        return x << y;
    }

    /* ---------- 浮点运算 ---------- */

    /** {@code numarith} */
    private static double numArith(int op, double v1, double v2) {
        switch (op) {
        case OP_ADD:
            return v1 + v2;
        case OP_SUB:
            return v1 - v2;
        case OP_MUL:
            return v1 * v2;
        case OP_DIV:
            return v1 / v2;
        case OP_POW:
            return (v2 == 2) ? v1 * v1 : Math.pow(v1, v2);
        case OP_IDIV:
            return Math.floor(v1 / v2);
        case OP_UNM:
            return -v1;
        case OP_MOD:
            return modf(v1, v2);
        default:
            throw new IllegalArgumentException("bad num op " + op);
        }
    }

    /** {@code luaV_modf}：{@code fmod} 之后把结果调到与除数同号 */
    static double modf(double m, double n) {
        double r = m % n;
        if ((r > 0) ? (n < 0) : ((r < 0 && n > 0))) {
            r += n;
        }
        return r;
    }
}
