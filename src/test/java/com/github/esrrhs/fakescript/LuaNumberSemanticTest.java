package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lua 5.4.9 数值语义差分测试:tostring 浮点格式(%.14g / HALF_EVEN)、
 * tonumber 词法(十进制/十六进制整数与浮点/uint64 环绕)、base 参数规则。
 * 期望值全部取自本机真实 lua5.4 实测输出。
 */
public class LuaNumberSemanticTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        fkconfig config = new fkconfig();
        f = fk.newfake(config);
        fk.openbaselib(f);
        assertNotNull(f);
    }

    private Object[] returns(String returnExpr) {
        String script = "func f()\n    return " + returnExpr + "\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] r = fk.runmulti(f, "f");
        assertFalse(fk.error(f), fk.geterror(f));
        return r;
    }

    private void assertStrings(String expr, String... expected) {
        Object[] r = returns(expr);
        assertEquals(expected.length, r.length, fk.geterror(f));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], r[i], "index " + i);
        }
    }

    private void assertRunError(String expr) {
        // 重建上下文,避免上一次运行残留的 error 状态干扰断言
        fkconfig config = new fkconfig();
        f = fk.newfake(config);
        fk.openbaselib(f);
        String script = "func f()\n    return " + expr + "\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f), "expected runtime error for: " + expr);
    }

    // ---------- tostring 浮点格式 ----------

    @Test
    public void testTostringFixedPointMatrix() {
        assertStrings(
                "tostring(1.0), tostring(-1.0), tostring(100.5), tostring(0.1), " +
                "tostring(1.0 / 3.0), tostring(2.0 / 3.0), tostring(100.0 / 3.0), " +
                "tostring(123.456), tostring(1234.5678), tostring(12345678.9), " +
                "tostring(12345678901234.0), tostring(1234567890.123456), tostring(1), tostring(-1)",
                "1.0", "-1.0", "100.5", "0.1",
                "0.33333333333333", "0.66666666666667", "33.333333333333",
                "123.456", "1234.5678", "12345678.9",
                "12345678901234.0", "1234567890.1235", "1", "-1");
    }

    @Test
    public void testTostringHalfEvenBoundary() {
        // 123456789012345 = 1.23456789012345e14,14 位有效数字末位恰为 ties,
        // glibc %.14g 为 ties-to-even -> 舍去;HALF_UP 会错误进位
        assertStrings(
                "tostring(123456789012345.0), tostring(999999999999999.0), " +
                "tostring(1234567890123456.0), tostring(0.0001), tostring(0.00001), tostring(0.000001)",
                "1.2345678901234e+14", "1e+15",
                "1.2345678901235e+15", "0.0001", "1e-05", "1e-06");
    }

    @Test
    public void testTostringScientificFromTonumber() {
        assertStrings(
                "tostring(tonumber(\"1e20\")), tostring(tonumber(\"1e16\")), " +
                "tostring(tonumber(\"1e14\")), tostring(tonumber(\"1e100\")), " +
                "tostring(tonumber(\"1e-10\")), tostring(tonumber(\"1e-100\")), " +
                "tostring(tonumber(\"1e999\")), tostring(tonumber(\"1e-999\")), " +
                "tostring(tonumber(\"4.9406564584125e-324\")), " +
                "tostring(tonumber(\"2.2250738585072e-308\")), " +
                "tostring(tonumber(\"1.7976931348623157e308\"))",
                "1e+20", "1e+16",
                "1e+14", "1e+100",
                "1e-10", "1e-100",
                "inf", "0.0",
                "4.9406564584125e-324",
                "2.2250738585072e-308",
                "1.7976931348623e+308");
    }

    @Test
    public void testFormatSpecialValuesDirect() {
        // nan/inf/-inf/-0.0 无法通过脚本算术构造(REAL 除零被拦截),直接测格式化函数
        assertEquals("nan", types.format_lua_float(Double.NaN));
        assertEquals("inf", types.format_lua_float(Double.POSITIVE_INFINITY));
        assertEquals("-inf", types.format_lua_float(Double.NEGATIVE_INFINITY));
        assertEquals("0.0", types.format_lua_float(0.0));
        assertEquals("-0.0", types.format_lua_float(-0.0));
        // 直接复跑 HALF_EVEN 关键点位
        assertEquals("1.2345678901234e+14", types.format_lua_float(123456789012345.0));
        assertEquals("4.9406564584125e-324",
                types.format_lua_float(Double.longBitsToDouble(1L)));
    }

    // ---------- tonumber 整数 ----------

    @Test
    public void testTonumberIntegers() {
        Object[] r = returns(
                "tonumber(\"  123  \"), tonumber(\"\t12\n\"), tonumber(\"+12\"), " +
                "tonumber(\"0xff\"), tonumber(\"0XFF\"), tonumber(\"-0x10\"), " +
                "tonumber(\"9223372036854775807\"), tonumber(\"-9223372036854775808\")");
        assertEquals(8, r.length, fk.geterror(f));
        long[] expected = {123L, 12L, 12L, 255L, 255L, -16L,
                Long.MAX_VALUE, Long.MIN_VALUE};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
    }

    @Test
    public void testTonumberHexUint64Wrapping() {
        // Lua: hex 整数按 uint64 解析后重解释为 int64
        Object[] r = returns(
                "typeof(tonumber(\"0xffffffffffffffff\")), tonumber(\"0xffffffffffffffff\"), " +
                "tonumber(\"0x8000000000000000\"), tonumber(\"-0x8000000000000000\"), " +
                "tonumber(\"-0x8000000000000001\"), tonumber(\"+0x10\")");
        assertEquals(6, r.length, fk.geterror(f));
        assertEquals("INT", r[0]);
        assertEquals(-1L, ((Long) r[1]).longValue());
        assertEquals(Long.MIN_VALUE, ((Long) r[2]).longValue());
        assertEquals(Long.MIN_VALUE, ((Long) r[3]).longValue());
        assertEquals(Long.MAX_VALUE, ((Long) r[4]).longValue());
        assertEquals(16L, ((Long) r[5]).longValue());
    }

    @Test
    public void testTonumberHexFloats() {
        Object[] r = returns(
                "tonumber(\"0x1.8p1\"), tonumber(\"0x1p4\"), tonumber(\"0x.a\"), " +
                "tonumber(\"0x.8p0\"), tonumber(\"0x1.\"), " +
                "typeof(tonumber(\"0x1p4\"))");
        assertEquals(6, r.length, fk.geterror(f));
        assertEquals(3.0, (Double) r[0], 0.0);
        assertEquals(16.0, (Double) r[1], 0.0);
        assertEquals(0.625, (Double) r[2], 0.0);
        assertEquals(0.5, (Double) r[3], 0.0);
        assertEquals(1.0, (Double) r[4], 0.0);
        assertEquals("REAL", r[5]);
    }

    @Test
    public void testTonumberDecimalOverflowFallsBackToFloat() {
        Object[] r = returns(
                "tostring(tonumber(\"9223372036854775808\")), " +
                "tostring(tonumber(\"999999999999999999999\")), " +
                "typeof(tonumber(\"9223372036854775808\")), " +
                "typeof(tonumber(\"999999999999999999999\")), " +
                "tostring(tonumber(\"1e-999\"))");
        assertEquals(5, r.length, () -> "err=" + fk.geterror(f));
        assertEquals("9.2233720368548e+18", r[0]);
        assertEquals("1e+21", r[1]);
        assertEquals("REAL", r[2]);
        assertEquals("REAL", r[3]);
        assertEquals("0.0", r[4]);
    }

    @Test
    public void testTonumberInfinityViaDirectParse() {
        // 脚本层验证 1e999 溢出为 REAL inf(不依赖除零)
        Object[] r = returns("typeof(tonumber(\"1e999\")), tostring(tonumber(\"1e999\"))");
        assertEquals("REAL", r[0]);
        assertEquals("inf", r[1]);
    }

    @Test
    public void testTonumberHexLexingEdges() {
        Object[] r = returns(
                "tonumber(\" 0xff \"), tonumber(\"0X10\"), tonumber(\"0xAbC\"), " +
                "tonumber(\"\f12\u000b\"), tonumber(\"00012\"), tonumber(\"00011\", 2), " +
                "tonumber(\"0x1P4\"), tostring(tonumber(\"0x1.999999999999ap-4\")), " +
                "tostring(tonumber(\"-0x1.8p1\")), " +
                "tonumber(\"-999999999999999999999\", 10)");
        assertEquals(10, r.length, fk.geterror(f));
        assertEquals(255L, ((Long) r[0]).longValue());
        assertEquals(16L, ((Long) r[1]).longValue());
        assertEquals(2748L, ((Long) r[2]).longValue());
        assertEquals(12L, ((Long) r[3]).longValue());  // \f / \v 属合法前后空白
        assertEquals(12L, ((Long) r[4]).longValue());
        assertEquals(3L, ((Long) r[5]).longValue());
        assertEquals(16.0, (Double) r[6], 0.0);        // 大写 P
        assertEquals("0.1", r[7]);                     // 0.1 的精确 hex double 表示
        assertEquals("-3.0", r[8]);
        assertEquals(-3875820019684212735L, ((Long) r[9]).longValue());
    }

    @Test
    public void testTonumberExtraFailures() {
        Object[] r = returns(
                "tonumber(\"1_000\"), tonumber(\"+ 12\"), tonumber(\"0x.\"), " +
                "tonumber(\"0xp1\"), tonumber(\"\t\", 16)");
        assertEquals(5, r.length, fk.geterror(f));
        for (int i = 0; i < r.length; i++) {
            assertNull(r[i], "index " + i + " should be nil");
        }
    }

    @Test
    public void testTonumberFailuresReturnNil() {
        Object[] r = returns(
                "tonumber(\"abc\"), tonumber(\"\"), tonumber(\"   \"), tonumber(\"12abc\"), " +
                "tonumber(\"0x\"), tonumber(\"0xG\"), tonumber(\"0x1p\"), tonumber(\"0x.p1\"), " +
                "tonumber(\"inf\"), tonumber(\"nan\"), tonumber(\"Infinity\"), tonumber(\"NaN\")");
        assertEquals(12, r.length, fk.geterror(f));
        for (int i = 0; i < r.length; i++) {
            assertNull(r[i], "index " + i + " should be nil");
        }
    }

    // ---------- tonumber 显式 base ----------

    @Test
    public void testTonumberExplicitBaseMatrix() {
        Object[] r = returns(
                "tonumber(\"11\", 2), tonumber(\"ff\", 16), tonumber(\"zz\", 36), " +
                "tonumber(\"z\", 36), tonumber(\"1b\", 12), tonumber(\"-10\", 2), " +
                "tonumber(\"2\", 2), tonumber(\"g\", 16), tonumber(\"0xff\", 16), " +
                "tonumber(\" 11 \", 2), tonumber(\"+10\", 16), tonumber(\"\", 16), tonumber(\"   \", 16)");
        assertEquals(13, r.length, fk.geterror(f));
        assertEquals(3L, ((Long) r[0]).longValue());
        assertEquals(255L, ((Long) r[1]).longValue());
        assertEquals(1295L, ((Long) r[2]).longValue());
        assertEquals(35L, ((Long) r[3]).longValue());
        assertEquals(23L, ((Long) r[4]).longValue());
        assertEquals(-2L, ((Long) r[5]).longValue());
        assertNull(r[6]);
        assertNull(r[7]);
        assertNull(r[8]); // 显式 base 不跳过 0x 前缀
        assertEquals(3L, ((Long) r[9]).longValue());
        assertEquals(16L, ((Long) r[10]).longValue());
        assertNull(r[11]);
        assertNull(r[12]);
    }

    @Test
    public void testTonumberExplicitBaseUint64Wrapping() {
        Object[] r = returns(
                "tonumber(\"ffffffffffffffff\", 16), tonumber(\"-8000000000000001\", 16), " +
                "tonumber(\"7fffffffffffffff\", 16), " +
                "tonumber(\"999999999999999999999\", 10)");
        assertEquals(4, r.length, fk.geterror(f));
        assertEquals(-1L, ((Long) r[0]).longValue());
        assertEquals(Long.MAX_VALUE, ((Long) r[1]).longValue());
        assertEquals(Long.MAX_VALUE, ((Long) r[2]).longValue());
        // 显式 base10 也按 uint64 环绕(与缺省的十进制溢出回落浮点不同)
        assertEquals(3875820019684212735L, ((Long) r[3]).longValue());
    }

    @Test
    public void testTonumberBaseArgumentCoercion() {
        Object[] r = returns(
                "tonumber(\"10\", \"16\"), tonumber(\"10\", \"16.0\"), " +
                "tonumber(\"ff\", \" 16 \"), tonumber(\"10\", null), tonumber(\"10\", 10.0)");
        assertEquals(5, r.length, fk.geterror(f));
        assertEquals(16L, ((Long) r[0]).longValue());
        assertEquals(16L, ((Long) r[1]).longValue());
        assertEquals(255L, ((Long) r[2]).longValue());
        assertEquals(10L, ((Long) r[3]).longValue());
        assertEquals(10L, ((Long) r[4]).longValue());
    }

    @Test
    public void testTonumberBaseArgumentErrors() {
        assertRunError("tonumber(\"10\", 0)");
        assertRunError("tonumber(\"10\", 1)");
        assertRunError("tonumber(\"10\", 37)");
        assertRunError("tonumber(\"10\", 16.5)");
        assertRunError("tonumber(\"10\", \"16.5\")");
        assertRunError("tonumber(\"x\", \"abc\")");
    }

    @Test
    public void testTonumberExplicitBaseRequiresStringFirstArg() {
        assertRunError("tonumber(10, 16)");
        assertRunError("tonumber(2.0, 2)");
        assertRunError("tonumber(null, 16)");
    }

    // ---------- 数值透传 / 拼接 / format 路径 ----------

    @Test
    public void testTonumberNumberPassthrough() {
        Object[] r = returns(
                "tonumber(7), tonumber(7.5), tonumber(\"4.2\"), typeof(tonumber(7)), " +
                "typeof(tonumber(7.5))");
        assertEquals(5, r.length, fk.geterror(f));
        assertEquals(7L, ((Long) r[0]).longValue());
        assertEquals(7.5, (Double) r[1], 0.0);
        assertEquals(4.2, (Double) r[2], 0.0);
        assertEquals("INT", r[3]);
        assertEquals("REAL", r[4]);

        // INT/REAL 数值相等语义:字符串转出来的 123 与整数 123 相等
        String script = "func f()\n" +
                "    if tonumber(\"123\") == 123 then\n" +
                "        return 1\n" +
                "    end\n" +
                "    return 0\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(1L, ((Long) fk.run(f, "f")).longValue());
    }

    @Test
    public void testConcatAndFormatUseLuaFloatFormat() {
        assertStrings(
                "1.0 .. \"\", (1.0 / 3.0) .. \"\", \"x=\" .. 2.5, " +
                "format(\"$\", 1.0 / 3.0), format(\"$\", 123456789012345.0), " +
                "format(\"v=$ end\", -1.0)",
                "1.0", "0.33333333333333", "x=2.5",
                "0.33333333333333", "1.2345678901234e+14", "v=-1.0 end");
    }

    // ---------- concat 类型限制(Lua 5.4:仅 string/number 可拼接) ----------
    // Lua 5.4: nil/table/boolean 参与 .. 报错 "attempt to concatenate a X value";
    // 本语言 true/false 编译为 REAL,故可拼接;null 映射为 POINTER,table 映射为 MAP/ARRAY

    @Test
    public void testConcatTypeRestrictions() {
        // string / number 两两组合均可
        assertStrings(
                "\"a\" .. \"b\", 1 .. 2, 1 .. \"x\", \"x\" .. 2, " +
                "1.5 .. 2.5, \"n=\" .. 0, 0 .. \"\"",
                "ab", "12", "1x", "x2", "1.52.5", "n=0", "0");
        // UUID(整数字面量加 u 后缀)可拼接,视同整数
        assertStrings("123u .. \"!\"", "123!");

        // null(POINTER)/map/array 参与 .. 报错
        assertRunError("null .. 1");
        assertRunError("1 .. null");
        assertRunError("\"\" .. null");
        assertRunError("map() .. 1");
        assertRunError("1 .. map()");
        assertRunError("array() .. 1");
        assertRunError("\"x\" .. array()");
    }

    // ---------- % 取模 / 除零 / floor / ceil / abs(Lua 5.4 算术语义) ----------

    @Test
    public void testIntegerFloorModulo() {
        // 注:当前文法不支持括号包裹裸值(如(-1)),负号由词法器并入 NUMBER 字面量
        Object[] r = returns(
                "-1 % 3, 1 % -3, -1 % -3, 7 % 3, -7 % 3, 7 % -3, 0 % 5, 5 % 1");
        assertEquals(8, r.length, fk.geterror(f));
        long[] expected = {2L, -2L, -1L, 1L, 2L, -2L, 0L, 0L};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
    }

    @Test
    public void testIntegerModuloInt64Edges() {
        // MIN=-2^63:余数校正公式不触发溢出,与 Lua lvm.c 行为一致
        Object[] r = returns(
                "tonumber(\"-9223372036854775808\") % -1, " +
                "tonumber(\"-9223372036854775808\") % 1, " +
                "tonumber(\"-9223372036854775808\") % 3, " +
                "tonumber(\"9223372036854775807\") % 2, " +
                "tonumber(\"999999999999999999999\", 10) % 7");
        assertEquals(5, r.length, fk.geterror(f));
        long[] expected = {0L, 0L, 1L, 1L};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
        // 环绕后的 3875820019684212735 % 7
        assertEquals(3875820019684212735L % 7, ((Long) r[4]).longValue());
    }

    @Test
    public void testFloatFloorModulo() {
        Object[] r = returns(
                "5 % 2.0, 5.5 % 2, -5.5 % 2, 5.5 % -2, 0.5 % 1, -0.5 % 1, " +
                "6.0 % 4, 1e300 % 0.1, typeof(5 % 2.0)");
        assertEquals(9, r.length, fk.geterror(f));
        assertEquals(1.0, (Double) r[0], 0.0);                 // 混合运算结果为 float
        assertEquals(1.5, (Double) r[1], 0.0);
        assertEquals(0.5, (Double) r[2], 0.0);
        assertEquals(-0.5, (Double) r[3], 0.0);
        assertEquals(0.5, (Double) r[4], 0.0);
        assertEquals(0.5, (Double) r[5], 0.0);
        assertEquals(2.0, (Double) r[6], 0.0);
        assertEquals(0.00011215964963493, (Double) r[7], 1e-18);
        assertEquals("REAL", r[8]);
    }

    @Test
    public void testFloatModuloZeroSign() {
        // 整除时零的符号跟随被除数(与真实 lua 5.4.9 实测一致);浮点 % 0 为 nan
        assertStrings(
                "tostring(5.0 % 5), tostring(-5.0 % 5), tostring(5.0 % -5), " +
                "tostring(-5.0 % -5), tostring(-0.0 % 1), tostring(0.0 % 0.0), " +
                "tostring(1.0 % 0.0)",
                "0.0", "-0.0", "0.0", "-0.0", "-0.0", "nan", "nan");
    }

    @Test
    public void testModuloCompoundAssignment() {
        String script = "func f()\n" +
                "    var a = -1\n" +
                "    a %= 3\n" +
                "    var b = 7\n" +
                "    b %= -3\n" +
                "    return a, b\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] r = fk.runmulti(f, "f");
        assertEquals(2, r.length, fk.geterror(f));
        assertEquals(2L, ((Long) r[0]).longValue());
        assertEquals(-2L, ((Long) r[1]).longValue());
    }

    @Test
    public void testDivisionByZeroSemantics() {
        // 整数除零/取模零:抛错
        assertRunError("1 / 0");
        assertRunError("1 % 0");
        // 浮点除零:IEEE 754
        assertStrings(
                "tostring(1.0 / 0.0), tostring(-1.0 / 0.0), tostring(0.0 / 0.0), " +
                "tostring(5 / 0.0), typeof(1.0 / 0.0)",
                "inf", "-inf", "nan", "inf", "REAL");
    }

    @Test
    public void testFloorAndCeilSemantics() {
        Object[] r = returns(
                "floor(2.7), floor(-2.7), ceil(2.1), ceil(-2.1), floor(3), floor(3.0), " +
                "typeof(floor(2.7)), typeof(floor(3)), floor(1e18)");
        assertEquals(9, r.length, fk.geterror(f));
        assertEquals(2L, ((Long) r[0]).longValue());
        assertEquals(-3L, ((Long) r[1]).longValue());
        assertEquals(3L, ((Long) r[2]).longValue());
        assertEquals(-2L, ((Long) r[3]).longValue());
        assertEquals(3L, ((Long) r[4]).longValue());
        assertEquals(3L, ((Long) r[5]).longValue());
        assertEquals("INT", r[6]);
        assertEquals("INT", r[7]);
        assertEquals(1000000000000000000L, ((Long) r[8]).longValue());
    }

    @Test
    public void testFloorAndCeilOutsideInt64StayFloat() {
        // 超出 int64 或 nan/inf:原样保留 REAL(旧实现强转 long 会静默错误)
        assertStrings(
                "tostring(floor(1e20)), tostring(floor(1e300)), tostring(floor(-1e300)), " +
                "tostring(ceil(1e300)), tostring(floor(0.0 / 0.0)), " +
                "tostring(floor(1.0 / 0.0)), tostring(ceil(0.0 / 0.0)), " +
                "typeof(floor(1e300))",
                "1e+20", "1e+300", "-1e+300", "1e+300", "nan", "inf", "nan", "REAL");
    }

    @Test
    public void testAbsTypeSemantics() {
        Object[] r = returns(
                "abs(-3), abs(3), typeof(abs(-3)), abs(-3.5), abs(3.5), " +
                "typeof(abs(-3.5)), abs(tonumber(\"-9223372036854775808\"))");
        assertEquals(7, r.length, fk.geterror(f));
        assertEquals(3L, ((Long) r[0]).longValue());
        assertEquals(3L, ((Long) r[1]).longValue());
        assertEquals("INT", r[2]);
        assertEquals(3.5, (Double) r[3], 0.0);
        assertEquals(3.5, (Double) r[4], 0.0);
        assertEquals("REAL", r[5]);
        // Lua 5.4.9: abs(MIN) 无正数可表示,原样返回 MIN
        assertEquals(Long.MIN_VALUE, ((Long) r[6]).longValue());
    }

    @Test
    public void testVariantModuloDirect() throws Exception {
        variant ret = new variant();
        variant na = new variant();
        na.set_int(-1);
        variant three = new variant();
        three.set_int(3);
        ret.divide_mod(na, three);
        assertEquals(2L, ((Long) ret.get_data()).longValue());
        assertEquals(variant_type.INT, ret.get_type());

        variant x = new variant();
        x.set_real(5.5);
        variant two = new variant();
        two.set_int(2);
        ret.divide_mod(x, two);
        assertEquals(1.5, (Double) ret.get_data(), 0.0);
        assertEquals(variant_type.REAL, ret.get_type());

        // INT % 0 抛错;REAL % 0 得 nan
        variant zeroI = new variant();
        zeroI.set_int(0);
        assertThrows(Exception.class, () -> ret.divide_mod(three, zeroI));
        variant zeroF = new variant();
        zeroF.set_real(0.0);
        ret.divide_mod(zeroF, zeroI);
        assertEquals(Double.NaN, (Double) ret.get_data(), 0.0);
    }

    @Test
    public void testLuaStyleNumericLiterals() {
        // 指数形态(含 + 号)、.5、1. 等 Lua 词法;带小数点/指数恒为 REAL
        assertStrings(
                "tostring(1e20), tostring(.5), tostring(1.), tostring(1E+3), " +
                "tostring(1e-2), tostring(-2.5e2), tostring(1.5e1), " +
                "typeof(1e3), typeof(.5), typeof(12), tostring(0.0)",
                "1e+20", "0.5", "1.0", "1000.0",
                "0.01", "-250.0", "15.0",
                "REAL", "REAL", "INT", "0.0");
    }

    @Test
    public void testHexNumericLiterals() {
        // 十六进制整数:uint64 位模式环绕,0xffffffffffffffff -> -1
        Object[] r = returns(
                "0xff, 0X10, 0x7fffffffffffffff, 0xffffffffffffffff, " +
                "0x10 + 1, -0x10");
        long[] expected = {255L, 16L, 9223372036854775807L, -1L, 17L, -16L};
        assertEquals(expected.length, r.length, fk.geterror(f));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
        // 十六进制浮点(p 指数必须有,或无 p 但含小数点),整型超界自动回落浮点
        assertStrings(
                "tostring(0x1p4), tostring(0x1.8), tostring(0X.8p0), " +
                "tostring(-0x1p4), tostring(0xa.bp3), typeof(0xff), typeof(0x1p4), " +
                "tostring(99999999999999999999), typeof(99999999999999999999)",
                "16.0", "1.5", "0.5", "-16.0", "85.5", "INT", "REAL", "1e+20", "REAL");
    }

    @Test
    public void testParenAndUnaryMinus() {
        // 括号包裹裸值
        Object[] r = returns(
                "(1), (-1), (-1) % 3, ((1 + 2) * 3), (7 % 3), (-7) % 3");
        long[] expected = {1L, -1L, 2L, 9L, 1L, 2L};
        assertEquals(expected.length, r.length, fk.geterror(f));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
        // 一元负号作用于表达式字面量,二元减号不被吞掉
        assertStrings(
                "tostring(10 - 3), tostring(10 - 3 - 2), tostring(2 * -3), " +
                "tostring(-(3 + 4)), tostring(3 - -2), tostring(- -5)",
                "7", "5", "-6", "-7", "5", "5");
        // 一元负号作用于变量(含 INT MIN 溢出为浮点,与 Lua 5.4 一致)
        String script = "func neg(x)\n    return -x\nend\n" +
                "func neg2(y)\n    return -y * 2\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        // 宿主 Integer 映射为脚本 INT(Long 默认映射为 UUID)
        assertEquals(-5L, fk.run(f, "neg", 5));
        assertEquals(-10L, fk.run(f, "neg2", 5));
        // INT MIN 取负溢出,按 Lua 5.4 为浮点 9.2233720368548e+18(脚本内 tostring 格式化)
        assertStrings(
                "tostring(-tonumber(\"-9223372036854775808\"))",
                "9.2233720368548e+18");
        // 函数调用参与减法,验证二元 MINUS 未被一元规则吞掉
        String sub = "func id(a)\n    return a\nend\n";
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
        assertTrue(fk.parsestr(f, sub + "func g()\n    return id(7) - id(3)\nend\n"),
                fk.geterror(f));
        assertEquals(4L, fk.run(f, "g"));
    }

    @Test
    public void testFloorDivide() {
        // 期望值全部取自真实 lua5.4.9
        Object[] r = returns(
                "3 // 2, -3 // 2, 6 // -4, -6 // -4, -1 // 2, " +
                "1 // 2 // 3, tonumber(\"-9223372036854775808\") // -1, " +
                "1 + 2 // 3, 7 // 2 * 2");
        long[] expected = {1L, -2L, -2L, 1L, -1L, 0L, Long.MIN_VALUE, 1L, 6L};
        assertEquals(expected.length, r.length, fk.geterror(f));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
        // 任一操作数为浮点,结果恒为浮点 floor(a/b);浮点除零按 IEEE 754
        assertStrings(
                "tostring(3.0 // 2), tostring(-3.0 // 2), tostring(1 // 0.5), " +
                "tostring(7 // -2.0), tostring(1e300 // 0.01), tostring(1e20 // 1), " +
                "tostring(5.0 // 0), tostring(0.0 // 0.0), tostring(1.5 // 0), " +
                "tostring(1e300 // 1e-300), typeof(3.0 // 2)",
                "1.0", "-2.0", "2.0", "-4.0", "1e+302", "1e+20",
                "inf", "nan", "inf", "inf", "REAL");
        // 整数 floor 除零抛错
        assertRunError("5 // 0");
        // 复合赋值 //=
        String script = "func f()\n" +
                "    var a = 7\n" +
                "    a //= 2\n" +
                "    var b = 6.0\n" +
                "    b //= 4\n" +
                "    return a, typeof(a), tostring(b), typeof(b)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] cr = fk.runmulti(f, "f");
        assertEquals(4, cr.length, fk.geterror(f));
        assertEquals(3L, ((Long) cr[0]).longValue());
        assertEquals("INT", cr[1]);
        assertEquals("1.0", cr[2]);
        assertEquals("REAL", cr[3]);
    }

    @Test
    public void testBitwiseOps() {
        // 期望值全部取自真实 lua5.4.9(64 位整数语义)
        Object[] r = returns(
                "3 & 5, 3 | 4, 3 ~ 5, 1 << 4, 256 >> 2, ~0, ~~5, " +
                "1 << 63, (1 << 63) >> 63, 1 << 64, 1 >> 64, 1 << -1, 256 >> -2, " +
                "-8 >> 1, -1 >> 63, -1 << 1");
        long[] expected = {1L, 7L, 6L, 16L, 64L, -1L, 5L,
                Long.MIN_VALUE, 1L, 0L, 0L, 0L, 1024L,
                9223372036854775804L, 1L, -2L};
        assertEquals(expected.length, r.length, fk.geterror(f));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Long) r[i]).longValue(), "index " + i);
        }
        // 结果恒为 INT
        assertStrings("typeof(3 & 5), typeof(1 << 4), typeof(~0)", "INT", "INT", "INT");
        // 优先级(对齐 Lua): | < ~ < & < 移位
        Object[] p = returns("1 | 2 & 3, 5 & 3 ~ 1, 1 | 1 << 2");
        assertEquals(3L, ((Long) p[0]).longValue());
        assertEquals(0L, ((Long) p[1]).longValue());
        assertEquals(5L, ((Long) p[2]).longValue());
        // 整数值浮点按整数处理
        Object[] fo = returns(
                "0.0 | 0, 3.0 & 5, -2.0 >> 1, 1e3 | 0, 1 << 100.0, ~~5.0");
        long[] fe = {0L, 1L, 9223372036854775807L, 1000L, 0L, 5L};
        assertEquals(fe.length, fo.length, fk.geterror(f));
        for (int i = 0; i < fe.length; i++) {
            assertEquals(fe[i], ((Long) fo[i]).longValue(), "index " + i);
        }
        // 非整数浮点、字符串、null 不能参与位运算(与 Lua 对 nil/string 报错一致)
        assertRunError("~0.5");
        assertRunError("3.5 & 1");
        assertRunError("\"s\" | 1");
        assertRunError("null | 0");
        // 一元 ~ 作用于变量
        String script = "func bnot(x)\n    return ~x\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(-6L, fk.run(f, "bnot", 5));
    }

    // ---------- INT/REAL 跨类型精确比较(Lua 5.4 lvm.c 语义) ----------
    // 期望值全部取自真实 lua 实测:INT 与 REAL 不经 double 中转精确比较,
    // 2^53 以上不丢精度

    private long cond(String c)
    {
        String script = "func f()\n    if " + c + " then\n        return 1\n    end\n    return 0\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object r = fk.run(f, "f");
        assertFalse(fk.error(f), fk.geterror(f));
        return ((Long) r).longValue();
    }

    @Test
    public void testExactCrossTypeEquality()
    {
        // 精确可表示的小整数与边界照旧相等
        assertEquals(1, cond("1 == 1.0"));
        assertEquals(1, cond("3 == 3.0"));
        assertEquals(1, cond("0 == -0.0"));
        assertEquals(1, cond("0.0 == -0.0"));
        assertEquals(1, cond("9007199254740992 == 9007199254740992.0")); // 2^53
        assertEquals(1, cond("9007199254740994 == 9007199254740994.0")); // 2^53+2
        assertEquals(1, cond("1.0/0.0 == 1.0/0.0"));                     // inf == inf
        // MININT 与 -2^63 浮点是同一值
        assertEquals(1, cond("tonumber(\"-9223372036854775808\") == tonumber(\"-9223372036854775808\") - 0.0"));
        // 超过 double 精度:浮点视图已变,不再相等(Lua 5.4 精确比较)
        assertEquals(0, cond("9007199254740993 == 9007199254740993.0"));
        assertEquals(0, cond("tonumber(\"9223372036854775807\") == tonumber(\"9223372036854775807\") + 0.0"));
        // NaN 不等于自身;!= 取反
        assertEquals(0, cond("0.0/0.0 == 0.0/0.0"));
        assertEquals(1, cond("9007199254740993 != 9007199254740993.0"));
        assertEquals(0, cond("1 != 1.0"));
    }

    @Test
    public void testExactCrossTypeOrdering()
    {
        // 普通混合比较不受影响
        assertEquals(1, cond("1.5 < 2"));
        assertEquals(0, cond("2 <= 1.5"));
        assertEquals(1, cond("2 > 1.5"));
        assertEquals(0, cond("1.5 >= 2"));
        assertEquals(1, cond("1.5 > 1"));
        // 2^53+1 的浮点字面量实为 2^53:INT 更大
        assertEquals(0, cond("9007199254740993 < 9007199254740993.0"));
        assertEquals(1, cond("9007199254740993 > 9007199254740993.0"));
        assertEquals(0, cond("9007199254740993 <= 9007199254740992.0"));
        assertEquals(0, cond("9007199254740993 >= 9007199254740994.0"));
        assertEquals(1, cond("9007199254740992.0 < 9007199254740993"));
        // MAXINT+0.0 即 2^63,超出 int64:MAXINT 更小
        assertEquals(1, cond("tonumber(\"9223372036854775807\") < tonumber(\"9223372036854775807\") + 0.0"));
        assertEquals(1, cond("tonumber(\"9223372036854775807\") + 0.0 > tonumber(\"9223372036854775807\")"));
        assertEquals(0, cond("tonumber(\"9223372036854775807\") >= tonumber(\"9223372036854775807\") + 0.0"));
        assertEquals(0, cond("tonumber(\"9223372036854775807\") + 0.0 < tonumber(\"9223372036854775807\")"));
        // MININT-0.0 即 -2^63,可精确表示:等于 MININT
        assertEquals(0, cond("tonumber(\"-9223372036854775808\") < tonumber(\"-9223372036854775808\") - 0.0"));
        assertEquals(1, cond("tonumber(\"-9223372036854775808\") <= tonumber(\"-9223372036854775808\") - 0.0"));
        assertEquals(0, cond("tonumber(\"-9223372036854775808\") - 0.0 > tonumber(\"-9223372036854775808\")"));
    }

    @Test
    public void testCrossTypeMapKeySemantics()
    {
        // Lua 表键规范化:浮点键具备整数表示时与整键同键,否则分键
        String same = "func f()\n" +
                "    var m = map()\n" +
                "    m[1] = 10\n" +
                "    m[1.0] = 20\n" +
                "    return size(m), m[1]\n" +
                "end\n";
        assertTrue(fk.parsestr(f, same), fk.geterror(f));
        Object[] r = fk.runmulti(f, "f");
        assertEquals(2, r.length, fk.geterror(f));
        assertEquals(1L, ((Long) r[0]).longValue());
        assertEquals(20L, ((Long) r[1]).longValue());

        String diff = "func f()\n" +
                "    var m = map()\n" +
                "    m[9007199254740993] = 1\n" +
                "    m[9007199254740993.0] = 2\n" +
                "    return size(m)\n" +
                "end\n";
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
        assertTrue(fk.parsestr(f, diff), fk.geterror(f));
        r = fk.runmulti(f, "f");
        assertEquals(1, r.length, fk.geterror(f));
        assertEquals(2L, ((Long) r[0]).longValue());

        String exact = "func f()\n" +
                "    var m = map()\n" +
                "    m[9007199254740994] = 1\n" +
                "    m[9007199254740994.0] = 2\n" +
                "    return size(m), m[9007199254740994.0]\n" +
                "end\n";
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
        assertTrue(fk.parsestr(f, exact), fk.geterror(f));
        r = fk.runmulti(f, "f");
        assertEquals(2, r.length, fk.geterror(f));
        assertEquals(1L, ((Long) r[0]).longValue());
        assertEquals(2L, ((Long) r[1]).longValue());
    }

    @Test
    public void testVariantCrossTypeEqualsDirect()
    {
        // 9007199254740993.0 这个 Java 字面量本身就是 9007199254740992.0
        variant i = new variant();
        i.set_int(9007199254740993L);
        variant f = new variant();
        f.set_real(9007199254740993.0);
        assertFalse(i.equals(f));
        assertFalse(f.equals(i));

        // 2^53+2 可精确表示,跨类型相等且同哈希(hashCode 契约)
        variant i2 = new variant();
        i2.set_int(9007199254740994L);
        variant f2 = new variant();
        f2.set_real(9007199254740994.0);
        assertTrue(i2.equals(f2));
        assertTrue(f2.equals(i2));
        assertEquals(i2.hashCode(), f2.hashCode());

        // MININT 与 -2^63 浮点相等
        variant min = new variant();
        min.set_int(Long.MIN_VALUE);
        variant minf = new variant();
        minf.set_real(-9223372036854775808.0);
        assertTrue(min.equals(minf));

        // NaN 与任何数值不相等
        variant nan = new variant();
        nan.set_real(Double.NaN);
        variant one = new variant();
        one.set_int(1);
        assertFalse(nan.equals(one));
        assertFalse(one.equals(nan));

        // +2^63 超出 int64:isint 必须为假(此前 (long) 饱和导致误判)
        assertFalse(types.isint(9223372036854775808.0));
        assertTrue(types.isint(-9223372036854775808.0));
        assertTrue(types.isint(9007199254740992.0));
        // 注意:Java 字面量 9007199254740993.0 编译期即舍入为 9007199254740992.0,
        // 2^53+1 不存在对应 double;非整数 double 取 2^52 附近的 .5
        assertFalse(types.isint(4503599627370495.5));
        assertFalse(types.isint(1.5));
        assertFalse(types.isint(Double.NaN));
        assertFalse(types.isint(Double.POSITIVE_INFINITY));
        assertFalse(types.isint(Double.NEGATIVE_INFINITY));
        assertTrue(types.isint(-0.0));
        assertTrue(types.isint(0.0));
    }

    // ---------- 算术字符串强转(Lua 5.4 算术语义) ----------
    // 期望值全部取自真实 lua 实测:数值字符串按 Lua 词法强转后参与算术,
    // 结果类型跟随强转后的数值("10"+1 为 INT,"10.0"+1 为 REAL);
    // 位运算与比较不强转(与 Lua 一致)

    @Test
    public void testStringArithmeticCoercion()
    {
        Object[] r = returns(
                "\"10\" + 1, \"3\" * \"4\", \" 10 \" + 1, \"0x10\" + 0, " +
                "\"10.0\" + 1, \"1e2\" + 0, \"10.5\" + 0, " +
                "\"10\" % 3, \"10\" // 3, \"10\" / 4, \"10\" - 20, " +
                "typeof(\"10\" + 1), typeof(\"10.0\" + 1)");
        assertEquals(13, r.length, fk.geterror(f));
        long[] ints = {11L, 12L, 11L, 16L};
        for (int i = 0; i < ints.length; i++)
        {
            assertEquals(ints[i], ((Long) r[i]).longValue(), "index " + i);
        }
        assertEquals(11.0, (Double) r[4], 0.0);
        assertEquals(100.0, (Double) r[5], 0.0);
        assertEquals(10.5, (Double) r[6], 0.0);
        assertEquals(1L, ((Long) r[7]).longValue());
        assertEquals(3L, ((Long) r[8]).longValue());
        assertEquals(2.5, (Double) r[9], 0.0);
        assertEquals(-10L, ((Long) r[10]).longValue());
        assertEquals("INT", r[11]);
        assertEquals("REAL", r[12]);

        // 一元负号同样强转
        r = returns("-\"10\", - \" 10 \", -\"0x10\"");
        assertEquals(3, r.length, fk.geterror(f));
        assertEquals(-10L, ((Long) r[0]).longValue());
        assertEquals(-10L, ((Long) r[1]).longValue());
        assertEquals(-16L, ((Long) r[2]).longValue());
    }

    @Test
    public void testStringArithmeticCoercionInStatements()
    {
        // 变量与复合赋值路径
        String script = "func f()\n" +
                "    var s = \"21\"\n" +
                "    var a = \"10\"\n" +
                "    a += 5\n" +
                "    return s * 2, a, typeof(a)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] r = fk.runmulti(f, "f");
        assertEquals(3, r.length, fk.geterror(f));
        assertEquals(42L, ((Long) r[0]).longValue());
        assertEquals(15L, ((Long) r[1]).longValue());
        assertEquals("INT", r[2]);

        // 强转结果参与比较与拼接
        script = "func f()\n" +
                "    if \"1\" + 1 == 2 then\n" +
                "        return \"n=\" .. (\"5\" + 5)\n" +
                "    end\n" +
                "    return \"no\"\n" +
                "end\n";
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals("n=10", fk.run(f, "f"));
    }

    @Test
    public void testStringArithmeticCoercionFailures()
    {
        // 非数值字符串、空串:报错(与 Lua 一致,消息保持本库风格)
        assertRunError("\"abc\" + 1");
        assertRunError("\"\" + 1");
        assertRunError("\"10f\" + 1"); // Java 浮点后缀不是 Lua 词法
        assertRunError("\"inf\" + 1");
        assertRunError("\"12abc\" * 2");
        // 位运算不强转字符串(与 Lua 5.4 一致)
        assertRunError("\"10\" & 3");
        assertRunError("\"abc\" & 1");
        // UUID 依旧不参与算术
        assertRunError("123u + \"1\"");
    }

    @Test
    public void testLuaFloatLexicalStrictness()
    {
        // Double.parseDouble 特有的 f/d 后缀必须拒绝(Lua strtod 不接受)
        Object[] r = returns(
                "tonumber(\"10f\"), tonumber(\"1.5d\"), tonumber(\"10F\"), " +
                "tonumber(\"1e3\"), tonumber(\".5\"), tonumber(\"5.\"), " +
                "tonumber(\"+1.5e2\"), tonumber(\"999999999999999999999\")");
        assertEquals(8, r.length, fk.geterror(f));
        assertNull(r[0]);
        assertNull(r[1]);
        assertNull(r[2]);
        assertEquals(1000.0, (Double) r[3], 0.0);
        assertEquals(0.5, (Double) r[4], 0.0);
        assertEquals(5.0, (Double) r[5], 0.0);
        assertEquals(150.0, (Double) r[6], 0.0);
        assertEquals(1.0E21, (Double) r[7], 0.0);
    }

    // ---------- types 静态工具直接测试 ----------

    @Test
    public void testTrimLuaWhitespace() {
        assertEquals("12", types.trim_lua("  12  "));
        assertEquals("12", types.trim_lua("\t\n\r\f\u000b12\u000b"));
        assertEquals("", types.trim_lua("    "));
        assertEquals("a b", types.trim_lua(" a b "));
    }

    @Test
    public void testParseLuaNumberDirect() {
        variant v = types.parse_lua_number("+12", null);
        assertNotNull(v);
        assertEquals(variant_type.INT, v.get_type());
        assertEquals(12L, ((Long) v.get_data()).longValue());

        variant hf = types.parse_lua_number("0x1.8p1", null);
        assertNotNull(hf);
        assertEquals(variant_type.REAL, hf.get_type());
        assertEquals(3.0, (Double) hf.get_data(), 0.0);

        assertNull(types.parse_lua_number("", null));
        assertNull(types.parse_lua_number("xyz", null));
        assertNull(types.parse_lua_number("nan", null));

        variant based = types.parse_lua_number("zz", 36);
        assertNotNull(based);
        assertEquals(1295L, ((Long) based.get_data()).longValue());

        // 非法 base 数字返回 null
        assertNull(types.parse_lua_number("2", 2));
    }
}
