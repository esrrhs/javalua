package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 字符串驻留池。
 * <p>
 * 这里只能测「能观测到的」部分：内容相同的短字符串必须拿到同一个实例，
 * 长字符串不驻留。弱引用那一条（没人引用后条目会被回收）没法写成稳定的
 * 断言——JVM 何时回收不受控——所以只在注释里说明设计意图，不写会偶发红的用例。
 */
public class StringPoolTest {

    @Test
    public void shortStringsAreInterned() {
        StringPool pool = new StringPool();
        LuaString a = pool.intern(LuaString.latin1("hello"));
        LuaString b = pool.intern(LuaString.of(new byte[] { 'h', 'e', 'l', 'l', 'o' }));
        assertSame(a, b, "内容相同的短字符串应当是同一个实例");
    }

    @Test
    public void differentStringsStayDistinct() {
        StringPool pool = new StringPool();
        LuaString a = pool.intern(LuaString.latin1("hello"));
        LuaString b = pool.intern(LuaString.latin1("world"));
        assertNotSame(a, b);
        assertFalse(a.equals(b));
    }

    /** 长字符串不驻留：每次都返回新实例 */
    @Test
    public void longStringsAreNotInterned() {
        StringPool pool = new StringPool();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Lua.MAXSHORTLEN + 10; i++) {
            sb.append('y');
        }
        LuaString a = pool.intern(LuaString.latin1(sb.toString()));
        LuaString b = pool.intern(LuaString.latin1(sb.toString()));
        assertNotSame(a, b, "长字符串不该驻留");
        assertTrue(a.equals(b), "内容仍然相等");
    }

    /** 池的大小随不同字符串增长，重复驻留不增加条目 */
    @Test
    public void poolGrowsWithDistinctStrings() {
        StringPool pool = new StringPool();
        for (int i = 0; i < 50; i++) {
            pool.intern(LuaString.latin1("s" + i));
        }
        int afterInsert = pool.size();
        assertTrue(afterInsert >= 50, "size=" + afterInsert);
        for (int i = 0; i < 50; i++) {
            pool.intern(LuaString.latin1("s" + i));
        }
        assertEquals(afterInsert, pool.size(), "重复驻留不应新增条目");
    }

    private static void assertEquals(int expected, int actual, String msg) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, msg);
    }
}
