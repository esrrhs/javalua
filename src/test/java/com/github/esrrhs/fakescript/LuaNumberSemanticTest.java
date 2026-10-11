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
