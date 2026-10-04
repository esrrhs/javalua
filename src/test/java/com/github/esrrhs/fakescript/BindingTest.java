package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 绑定类型转换矩阵:覆盖fk.trans/canTrans在各源类型×目标类型组合下的行为
 */
public class BindingTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
        fk.regclass(f, BindingTest.class);
    }

    private Object call(String func, Object... args) throws Exception {
        Object ret = fk.run(f, func, args);
        assertFalse(fk.error(f), fk.geterror(f));
        return ret;
    }

    // 各primitive参数目标
    @fakescript
    public static byte byteP(byte v) { return v; }

    @fakescript
    public static short shortP(short v) { return v; }

    @fakescript
    public static int intP(int v) { return v; }

    @fakescript
    public static long longP(long v) { return v; }

    @fakescript
    public static float floatP(float v) { return v; }

    @fakescript
    public static double doubleP(double v) { return v; }

    @fakescript
    public static boolean boolP(boolean v) { return v; }

    @fakescript
    public static String strP(String v) { return v; }

    @fakescript
    public static Object objP(Object v) { return v; }

    @Test
    public void testNumericSourceToAllPrimitiveTargets() throws Exception {
        // INT源(脚本整数字面量)到各数字参数:窄化/加宽
        String script =
                "func f()\n" +
                "    return BindingTest.byteP(7), BindingTest.shortP(7), BindingTest.intP(7), " +
                "BindingTest.longP(7), BindingTest.floatP(7), BindingTest.doubleP(7)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(6, rets.length, fk.geterror(f));
        // 各目标返回后经pspush:byte/short/int→REAL(Double),long→UUID(Long),float/double→REAL
        // 各目标返回经pspush:byte/short/int→Integer→INT(Long),long→UUID(Long),float/double→REAL(Double)
        assertEquals(7L, ((Long) rets[0]).longValue());
        assertEquals(7L, ((Long) rets[1]).longValue());
        assertEquals(7L, ((Long) rets[2]).longValue());
        assertEquals(7L, ((Long) rets[3]).longValue());
        assertEquals(7.0, ((Double) rets[4]).doubleValue(), 0.0000001);
        assertEquals(7.0, ((Double) rets[5]).doubleValue(), 0.0000001);
    }

    @Test
    public void testRealSourceNarrowing() throws Exception {
        // REAL源到各数字参数:窄化截断;byte/int返回经pspush为INT(Long)
        String script =
                "func f()\n" +
                "    return BindingTest.byteP(3.9), BindingTest.intP(-7.9), BindingTest.longP(2.9)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(3L, ((Long) rets[0]).longValue());
        assertEquals(-7L, ((Long) rets[1]).longValue());
        assertEquals(2L, ((Long) rets[2]).longValue());
    }

    @Test
    public void testBooleanSourceToNumericTargets() throws Exception {
        // 布尔源到数字参数:1/0
        String script =
                "func f()\n" +
                "    return BindingTest.intP(true), BindingTest.intP(false), BindingTest.boolP(true), " +
                "BindingTest.boolP(false)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals(0L, ((Long) rets[1]).longValue());
        assertEquals(1.0, ((Double) rets[2]).doubleValue(), 0.0000001);
        assertEquals(0.0, ((Double) rets[3]).doubleValue(), 0.0000001);
    }

    @Test
    public void testObjectPassThrough() throws Exception {
        // Object参数:数值/字符串/容器原样透传
        String script =
                "func f()\n" +
                "    return BindingTest.objP(3), BindingTest.objP(\"s\"), size(BindingTest.objP(array()))\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(3L, ((Long) rets[0]).longValue());
        assertEquals("s", rets[1]);
        assertEquals(0L, ((Long) rets[2]).longValue());
    }

    @Test
    public void testStringSourceToStringTarget() throws Exception {
        String script =
                "func f()\n" +
                "    return BindingTest.strP(\"hello\"), BindingTest.strP(\"\")\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals("hello", rets[0]);
        assertEquals("", rets[1]);
    }

    @Test
    public void testNilSourceToTargets() throws Exception {
        // nil(POINTER(null))到primitive参数:反射拒绝null,脚本以错误结束返回null;Object/字符串参数收nil
        String script =
                "func f()\n" +
                "    return BindingTest.intP(null), BindingTest.strP(null), BindingTest.objP(null)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        // intP(null)在functor处报可读错误,整个脚本中止,只带回nil
        assertEquals(1, rets.length, fk.geterror(f));
        assertNull(rets[0]);
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("call bind func intP fail"), fk.geterror(f));
    }
}
