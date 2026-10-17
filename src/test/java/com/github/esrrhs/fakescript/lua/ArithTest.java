package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 算术测试 —— 与真实 {@code lua5.4} 逐例比对。
 * <p>
 * 为什么值得单独做：这套算术同时服务于<b>编译期常量折叠</b>和将来的 VM。
 * 两边只要有一处与 Lua 不一致，就会出现「写成常量算出一个值、写成变量算出
 * 另一个值」——这类分歧在正常测试里根本看不出来。
 * <p>
 * 比对方式：把每个用例同时写成 Lua 表达式交给参考实现求值，和
 * {@link Arith#rawArith} 的结果逐例对照（含 {@code math.type} 的子类型，
 * 因为 {@code 3 // 1} 是 integer 而 {@code 3 // 1.0} 是 float）。
 */
class ArithTest {

    @BeforeAll
    static void requireReference() {
        Assumptions.assumeTrue(ReferenceLua.available(),
                "找不到 lua5.4 参考二进制，跳过算术差分比对：" + ReferenceLua.describe());
    }

    private static LuaValue i(long v) {
        return LuaValue.ofInteger(v);
    }

    private static LuaValue f(double v) {
        return LuaValue.ofFloat(v);
    }

    /** 一个用例：Lua 表达式 + 我们这边对应的操作与操作数 */
    private static final class Case {
        final String expr;
        final int op;
        final LuaValue a;
        final LuaValue b;

        Case(String expr, int op, LuaValue a, LuaValue b) {
            this.expr = expr;
            this.op = op;
            this.a = a;
            this.b = b;
        }
    }

    private static List<Case> cases() {
        List<Case> cs = new ArrayList<>();
        // 算术
        cs.add(new Case("1 + 2", Arith.OP_ADD, i(1), i(2)));
        cs.add(new Case("5 - 8", Arith.OP_SUB, i(5), i(8)));
        cs.add(new Case("6 * 7", Arith.OP_MUL, i(6), i(7)));
        cs.add(new Case("7 // 2", Arith.OP_IDIV, i(7), i(2)));
        cs.add(new Case("(0-7) // 2", Arith.OP_IDIV, i(-7), i(2)));
        cs.add(new Case("7 // (0-2)", Arith.OP_IDIV, i(7), i(-2)));
        cs.add(new Case("7 % 3", Arith.OP_MOD, i(7), i(3)));
        cs.add(new Case("(0-7) % 3", Arith.OP_MOD, i(-7), i(3)));
        cs.add(new Case("7 % (0-3)", Arith.OP_MOD, i(7), i(-3)));
        cs.add(new Case("(0-7) % (0-3)", Arith.OP_MOD, i(-7), i(-3)));
        cs.add(new Case("2 ^ 10", Arith.OP_POW, i(2), i(10)));
        cs.add(new Case("2 ^ 2", Arith.OP_POW, i(2), i(2)));
        cs.add(new Case("7 / 2", Arith.OP_DIV, i(7), i(2)));
        // 浮点
        cs.add(new Case("1.5 + 2.25", Arith.OP_ADD, f(1.5), f(2.25)));
        cs.add(new Case("7.0 // 2", Arith.OP_IDIV, f(7.0), i(2)));
        cs.add(new Case("(0-7.0) // 2", Arith.OP_IDIV, f(-7.0), i(2)));
        cs.add(new Case("7.5 % 2", Arith.OP_MOD, f(7.5), i(2)));
        cs.add(new Case("(0-7.5) % 2", Arith.OP_MOD, f(-7.5), i(2)));
        cs.add(new Case("2.0 ^ 0.5", Arith.OP_POW, f(2.0), f(0.5)));
        // 整数与浮点混合（子类型是区分点）
        cs.add(new Case("3 + 1.0", Arith.OP_ADD, i(3), f(1.0)));
        cs.add(new Case("3 // 1.0", Arith.OP_IDIV, i(3), f(1.0)));
        cs.add(new Case("3 % 1.0", Arith.OP_MOD, i(3), f(1.0)));
        // 位运算
        cs.add(new Case("12 & 10", Arith.OP_BAND, i(12), i(10)));
        cs.add(new Case("12 | 10", Arith.OP_BOR, i(12), i(10)));
        cs.add(new Case("12 ~ 10", Arith.OP_BXOR, i(12), i(10)));
        cs.add(new Case("1 << 62", Arith.OP_SHL, i(1), i(62)));
        cs.add(new Case("1 << 64", Arith.OP_SHL, i(1), i(64)));
        cs.add(new Case("1 << (0-1)", Arith.OP_SHL, i(1), i(-1)));
        cs.add(new Case("(0-1) >> 1", Arith.OP_SHR, i(-1), i(1)));
        cs.add(new Case("(0-8) >> 2", Arith.OP_SHR, i(-8), i(2)));
        cs.add(new Case("(0-8) >> 64", Arith.OP_SHR, i(-8), i(64)));        cs.add(new Case("1 >> (0-1)", Arith.OP_SHR, i(1), i(-1)));
        cs.add(new Case("(0-1) >> (0-1)", Arith.OP_SHR, i(-1), i(-1)));
        cs.add(new Case("7 & 1.0", Arith.OP_BAND, i(7), f(1.0)));
        // 一元
        cs.add(new Case("(0-5)", Arith.OP_UNM, i(5), i(0)));
        cs.add(new Case("~0", Arith.OP_BNOT, i(0), i(0)));
        cs.add(new Case("~12", Arith.OP_BNOT, i(12), i(0)));
        cs.add(new Case("(0-1.5)", Arith.OP_UNM, f(1.5), f(0)));
        // 边界：mininteger 的除法与取模不能溢出
        cs.add(new Case("math.mininteger // (0-1)", Arith.OP_IDIV, i(Long.MIN_VALUE), i(-1)));
        cs.add(new Case("math.mininteger % (0-1)", Arith.OP_MOD, i(Long.MIN_VALUE), i(-1)));
        cs.add(new Case("math.mininteger + 0", Arith.OP_ADD, i(Long.MIN_VALUE), i(0)));
        return cs;
    }

    @Test
    void rawArithMatchesReferenceLua() {
        List<Case> cs = cases();
        StringBuilder script = new StringBuilder();
        for (Case c : cs) {
            script.append("do local R = ").append(c.expr).append("\n")
                    .append("  if math.type(R) == 'integer' then\n")
                    .append("    print('integer', tostring(R))\n")
                    .append("  else\n")
                    .append("    local s = string.pack('>d', R)\n")
                    .append("    local h = ''\n")
                    .append("    for i = 1, #s do h = h .. string.format('%02x', string.byte(s, i)) end\n")
                    .append("    print('float', h)\n")
                    .append("  end\n")
                    .append("end\n");
        }
        ReferenceLua.Result r = ReferenceLua.run(script.toString());
        assertEquals(0, r.exitCode, r.toString());
        String[] lines = r.output.replaceAll("\r\n", "\n").split("\n");
        assertEquals(cs.size(), lines.length, "参考实现输出行数应与用例数相同：" + r.output);

        for (int n = 0; n < cs.size(); n++) {
            Case c = cs.get(n);
            LuaValue got = Arith.rawArith(null, c.op, c.a, c.b);
            assertNotNull(got, "我们这边的运算失败了：" + c.expr);
            String ours = typeOf(got) + "\t" + format(got);
            assertEquals(lines[n], ours, "用例 " + c.expr);
        }
    }

    private static String typeOf(LuaValue v) {
        return v.isInteger() ? "integer" : "float";
    }

    /**
     * 整数直接打十进制；浮点打<b>位模式的十六进制</b>。
     * <p>
     * 为什么不用 {@code %.17g} 之类的十进制格式化：Java 的 {@code Formatter}
     * 对 {@code %g} 不会去掉末尾的零（{@code 3} 打成
     * {@code 3.0000000000000000}），而 C 会；用十进制比较等于拿格式化的差异
     * 冒充数值的差异。位模式没有这个问题，一个 ULP 都藏不住。
     */
    private static String format(LuaValue v) {
        if (v.isInteger()) {
            return Long.toString(v.i);
        }
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(v.n));
    }

    /* ---------- 不依赖参考实现的补充 ---------- */

    @Test
    void fltToIntegerFollowsLuaRules() {
        assertEquals(Long.valueOf(3), Arith.fltToInteger(3.0, Arith.F2I_EQ));
        assertNull(Arith.fltToInteger(3.5, Arith.F2I_EQ), "非整值在 F2Ieq 下必须失败");
        assertEquals(Long.valueOf(3), Arith.fltToInteger(3.5, Arith.F2I_FLOOR));
        assertEquals(Long.valueOf(4), Arith.fltToInteger(3.5, Arith.F2I_CEIL));
        assertNull(Arith.fltToInteger(1e300, Arith.F2I_EQ), "超出 int64 范围必须失败");
        assertNull(Arith.fltToInteger(Double.NaN, Arith.F2I_EQ));
    }

    @Test
    void shiftOutOfRangeIsZeroNotModulo() {
        // Java 的 << 会把位数按 64 取模，Lua 不会：1 << 64 是 0
        assertEquals(0L, Arith.shiftl(1, 64));
        assertEquals(0L, Arith.shiftl(1, -64));
        assertEquals(0L, Arith.shiftl(1, 65));
        assertEquals(2L, Arith.shiftl(1, 1));
        assertEquals(1L, Arith.shiftl(2, -1));
    }

    @Test
    void shiftRightIsLogicalNotArithmetic() {
        // Lua 手册：两个移位运算符都用 0 填充空位。参考实现实测 -8 >> 2
        // 得到 4611686018427387902（0x3FFFFFFFFFFFFFFE），不是 -2。
        assertEquals(4611686018427387902L, Arith.shiftl(-8, -2));
        assertEquals(9223372036854775807L, Arith.shiftl(-1, -1));
    }

    @Test
    void rawArithRejectsNonNumbers() {
        assertNull(Arith.rawArith(null, Arith.OP_ADD, LuaValue.ofString(LuaString.latin1("x")), i(1)));
        assertNull(Arith.rawArith(null, Arith.OP_BAND, f(1.5), i(1)), "非整值不能参与位运算");
    }

    @Test
    void divisionByZeroRaises() {
        assertThrowsLuaError(() -> Arith.rawArith(null, Arith.OP_IDIV, i(1), i(0)));
        assertThrowsLuaError(() -> Arith.rawArith(null, Arith.OP_MOD, i(1), i(0)));
    }

    private static void assertThrowsLuaError(Runnable r) {
        try {
            r.run();
        } catch (LuaError e) {
            return;
        }
        throw new AssertionError("期望 LuaError");
    }
}
