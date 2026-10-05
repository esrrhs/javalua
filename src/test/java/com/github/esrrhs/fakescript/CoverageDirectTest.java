package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 直测包私有API:trans/canTrans全矩阵、variant数学REAL路径、dump工具
 */
public class CoverageDirectTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    // ==================== trans 矩阵(字符串/布尔/数字源 × 数字目标) ====================

    @Test
    public void testTransStringToNumerics() {
        assertEquals((byte) 12, fk.trans("12", Byte.TYPE));
        assertEquals((short) 12, fk.trans("12", Short.TYPE));
        assertEquals(12, fk.trans("12", Integer.TYPE));
        assertEquals(12L, fk.trans("12", Long.TYPE));
        assertEquals(12.0f, fk.trans("12", Float.TYPE));
        assertEquals(12.0, fk.trans("12", Double.TYPE));
        // 包装类目标
        assertEquals((byte) 12, fk.trans("12", Byte.class));
        assertEquals(12, fk.trans("12", Integer.class));
        // 布尔目标:字符串按整数解析
        assertEquals(true, fk.trans("5", Boolean.TYPE));
        assertEquals(false, fk.trans("0", Boolean.TYPE));
    }

    @Test
    public void testTransBooleanSource() {
        assertEquals((byte) 1, fk.trans(true, Byte.TYPE));
        assertEquals((short) 1, fk.trans(true, Short.TYPE));
        assertEquals(1, fk.trans(true, Integer.TYPE));
        assertEquals(1L, fk.trans(true, Long.TYPE));
        assertEquals(1.0f, fk.trans(true, Float.TYPE));
        assertEquals(1.0, fk.trans(true, Double.TYPE));
        assertEquals(0.0, fk.trans(false, Double.TYPE));
        assertEquals(true, fk.trans(true, Boolean.class));
    }

    @Test
    public void testTransNumberTargetsFallback() {
        // 非数值源到数字目标:零值
        Object obj = new Object();
        assertEquals((byte) 0, fk.trans(obj, Byte.TYPE));
        assertEquals((short) 0, fk.trans(obj, Short.TYPE));
        assertEquals(0, fk.trans(obj, Integer.TYPE));
        assertEquals(0L, fk.trans(obj, Long.TYPE));
        assertEquals(0.0f, fk.trans(obj, Float.TYPE));
        assertEquals(0.0, fk.trans(obj, Double.TYPE));
        assertEquals(false, fk.trans(obj, Boolean.TYPE));
    }

    @Test
    public void testTransSpecialTypes() {
        // 特定类型目标(fake.class):数字/布尔/字符串源返回null,该类型实例透传
        fake f2 = new fake();
        assertNull(fk.trans(1, fake.class));
        assertNull(fk.trans(true, fake.class));
        assertNull(fk.trans("s", fake.class));
        assertSame(f2, fk.trans(f2, fake.class));
        // String目标
        assertEquals("5", fk.trans(5, String.class));
        assertEquals("true", fk.trans(true, String.class));
        // Object目标
        Object obj = new Object();
        assertSame(obj, fk.trans(obj, Object.class));
        // null源
        assertNull(fk.trans(null, Integer.TYPE));
    }

    // ==================== canTrans 矩阵 ====================

    @Test
    public void testCanTransMatrix() {
        fake f2 = new fake();
        Object num = 5;
        Object str = "s";
        Object boolv = true;
        // 数字源到数字目标
        assertTrue(fk.canTrans(num, Byte.TYPE));
        assertTrue(fk.canTrans(num, Short.TYPE));
        assertTrue(fk.canTrans(num, Integer.TYPE));
        assertTrue(fk.canTrans(num, Long.TYPE));
        assertTrue(fk.canTrans(num, Float.TYPE));
        assertTrue(fk.canTrans(num, Double.TYPE));
        assertTrue(fk.canTrans(num, Boolean.TYPE));
        // 数字源到字符串/对象/特定类型
        assertFalse(fk.canTrans(num, String.class));
        assertTrue(fk.canTrans(num, Object.class));
        assertFalse(fk.canTrans(num, fake.class));
        // 字符串源
        assertTrue(fk.canTrans(str, String.class));
        assertFalse(fk.canTrans(str, Integer.TYPE));
        // 布尔源
        assertFalse(fk.canTrans(boolv, String.class));
        assertTrue(fk.canTrans(boolv, Object.class));
        // 对象源
        assertTrue(fk.canTrans(f2, fake.class));
        assertFalse(fk.canTrans(f2, String.class));
        assertTrue(fk.canTrans(f2, Object.class));
        // null源
        assertTrue(fk.canTrans(null, Integer.TYPE));
    }

    // ==================== variant 数学 REAL/NIL 路径 ====================

    private variant real(double d) {
        variant v = new variant();
        v.set_real(d);
        return v;
    }

    private variant num(long l) {
        variant v = new variant();
        v.set_int(l);
        return v;
    }

    @Test
    public void testVariantMathRealPaths() throws Exception {
        variant ret = new variant();

        // REAL×REAL
        ret.plus(real(1.5), real(2.5));
        assertEquals(4.0, ret.get_real(), 0.0000001);
        ret.minus(real(5), real(2));
        assertEquals(3.0, ret.get_real(), 0.0000001);
        ret.multiply(real(2), real(3));
        assertEquals(6.0, ret.get_real(), 0.0000001);
        ret.divide(real(7), real(2));
        assertEquals(3.5, ret.get_real(), 0.0000001);
        ret.divide_mod(real(7), real(2));
        assertEquals(1.0, ret.get_real(), 0.0000001);

        // INT×REAL与REAL×INT混合
        ret.plus(num(1), real(0.5));
        assertEquals(1.5, ret.get_real(), 0.0000001);
        ret.minus(real(0.5), num(1));
        assertEquals(-0.5, ret.get_real(), 0.0000001);
        ret.multiply(num(2), real(1.5));
        assertEquals(3.0, ret.get_real(), 0.0000001);

        // 比较REAL与混合
        ret.less(real(1), num(2));
        assertEquals(1.0, ret.get_real(), 0.0000001);
        ret.more(real(1), num(2));
        assertEquals(0.0, ret.get_real(), 0.0000001);
        ret.less_equal(real(2), num(2));
        assertEquals(1.0, ret.get_real(), 0.0000001);
        ret.more_equal(num(2), real(2));
        assertEquals(1.0, ret.get_real(), 0.0000001);

        // NIL参与算术(assert_can_cal允许NIL,get_real按0处理)
        ret.plus(new variant(), new variant());
        assertEquals(0.0, ret.get_real(), 0.0000001);

        // not REAL
        ret.not(real(0));
        assertEquals(1.0, ret.get_real(), 0.0000001);
        ret.not(real(3));
        assertEquals(0.0, ret.get_real(), 0.0000001);

        // set_cmp_ret REAL路径:REAL比较结果为REAL
        assertEquals(variant_type.REAL, ret.get_type());
    }

    @Test
    public void testVariantJneRealPaths() throws Exception {
        assertTrue(variant.less_jne(real(1), real(2)));
        assertFalse(variant.less_jne(real(2), real(1)));
        assertTrue(variant.more_jne(real(2), real(1)));
        assertTrue(variant.less_equal_jne(real(2), real(2)));
        assertTrue(variant.more_equal_jne(real(2), real(2)));

        // NIL操作数
        assertTrue(variant.less_jne(new variant(), num(1)));
        assertFalse(variant.less_jne(num(1), new variant()));
        assertTrue(variant.and_jne(real(1), num(1)));
        assertFalse(variant.and_jne(real(0), num(1)));
        assertTrue(variant.or_jne(real(0), num(1)));
        assertFalse(variant.or_jne(real(0), real(0)));
        assertTrue(variant.not_jne(new variant()));
    }

    @Test
    public void testVariantEqualsAllPairs() throws Exception {
        // UUID相等
        variant u1 = new variant();
        u1.set_uuid(9L);
        variant u2 = new variant();
        u2.set_uuid(9L);
        assertTrue(u1.equals(u2));

        // 容器按引用
        variant a1 = new variant();
        a1.set_array(new variant_array());
        variant a2 = new variant();
        a2.set_array(a1.get_array());
        assertTrue(a1.equals(a2));

        // UUID vs INT 同值不同类型
        assertFalse(u1.equals(num(9L)));

        // equal/not_equal写回
        variant ret = new variant();
        ret.equal(u1, u2);
        assertEquals(1.0, ret.get_real(), 0.0000001);
        ret.not_equal(u1, u2);
        assertEquals(0.0, ret.get_real(), 0.0000001);
    }

    // ==================== 工具方法 ====================

    @Test
    public void testDumpAddrAndOpCodeStr() {
        // 已知地址类型
        int stack = command.COMMAND_CODE(command.MAKE_ADDR(command.ADDR_STACK, 3));
        assertTrue(types.dump_addr(stack).contains("STACK"));
        int cst = command.COMMAND_CODE(command.MAKE_ADDR(command.ADDR_CONST, 1));
        assertTrue(types.dump_addr(cst).contains("CONST"));
        int con = command.COMMAND_CODE(command.MAKE_ADDR(command.ADDR_CONTAINER, 2));
        assertTrue(types.dump_addr(con).contains("CONTAINER"));
        // 未知地址类型:default分支
        assertTrue(types.dump_addr(command.MAKEINT32(9, 9)).contains("unknown"));

        // OpCodeStr全表
        for (int i = 0; i < command.OPCODE_MAX; i++) {
            assertNotEquals("", types.OpCodeStr(i));
        }
    }

    @Test
    public void testBuiltinArgCountErrors() {
        // 每个内建函数参数个数错误都应干净报错
        String[] badCalls = {
                "func f()\n    return size()\nend\n",
                "func f()\n    return range()\nend\n",
                "func f()\n    return typeof()\nend\n",
                "func f()\n    return tonumber()\nend\n",
                "func f()\n    return tostring()\nend\n",
                "func f()\n    return tolong()\nend\n",
                "func f()\n    return substr(\"a\")\nend\n",
                "func f()\n    return find(\"a\")\nend\n",
                "func f()\n    return replace(\"a\")\nend\n",
                "func f()\n    return split()\nend\n",
                "func f()\n    return push()\nend\n",
                "func f()\n    return pow(1)\nend\n",
        };

        for (String script : badCalls) {
            assertTrue(fk.parsestr(f, script), fk.geterror(f));
            fk.run(f, "f");
            assertTrue(fk.error(f), "should error: " + printable(script));
            assertTrue(fk.geterror(f).contains("param not match"), fk.geterror(f));
            f.clearerr();
        }
    }

    private String printable(String s) {
        return s.replace("\n", "\\n");
    }

    @Test
    public void testProcessorRoutineInfo() throws Exception {
        assertTrue(fk.parsestr(f, "func f()\n    return 1\nend\n"), fk.geterror(f));

        // 挂起中查看routine信息
        String loop = "func w()\n    var i = 0\n    while i < 2 then\n        i = i + 1\n        yield 1\n    end\nend\n"
                + "func main()\n    fake w()\n    yield 1\n    return 1\nend\n";
        assertTrue(fk.parsestr(f, loop), fk.geterror(f));

        fake f2 = fk.newfake(new fkconfig());
        fk.openbaselib(f2);
        assertTrue(fk.parsestr(f2, loop), fk.geterror(f2));

        assertNull(fk.resume(f2, "main"));
        int rid = fk.getcurroutineid(f2);
        assertTrue(fk.getcurroutine(f2).contains("Alive"));
        assertTrue(fk.getcurroutinebyid(f2, (int) rid).contains("Alive"));
        assertTrue(fk.getcurroutinebyindex(f2, 0).contains("Alive"));

        // get_routine_by_id不存在时不崩溃
        assertFalse(fk.ishaveroutine(f2, 999));

        // 跑完后协程消失
        Object[] rets = null;
        int guard = 0;
        while (rets == null && guard < 100)
        {
            rets = fk.resume(f2, "main");
            guard++;
        }
        assertNotNull(rets);
    }
}
