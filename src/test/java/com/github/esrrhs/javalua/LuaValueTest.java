package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 值层：类型标签、真假判定、原始相等（对应 lobject.c / lvm.c 的基础约定） */
public class LuaValueTest {

    @Test
    public void typeTags() {
        assertEquals(Lua.TNIL, LuaValue.ofNil().baseType());
        assertEquals(Lua.TBOOLEAN, LuaValue.ofBoolean(true).baseType());
        assertEquals(Lua.TNUMBER, LuaValue.ofInteger(1).baseType());
        assertEquals(Lua.TNUMBER, LuaValue.ofFloat(1.0).baseType());
        assertEquals(Lua.TSTRING, LuaValue.ofString(LuaString.latin1("x")).baseType());
        assertEquals(Lua.TTABLE, LuaValue.ofTable(new LuaTable()).baseType());

        assertEquals("nil", LuaValue.ofNil().typeName());
        assertEquals("boolean", LuaValue.ofBoolean(false).typeName());
        assertEquals("number", LuaValue.ofInteger(0).typeName());
        assertEquals("string", LuaValue.ofString(LuaString.latin1("")).typeName());
        assertEquals("table", LuaValue.ofTable(new LuaTable()).typeName());
    }

    /** 整数与浮点必须是不同的变体标签 —— math.type(1) 与 math.type(1.0) 靠它区分 */
    @Test
    public void numberSubtypesAreDistinct() {
        assertTrue(LuaValue.ofInteger(1).isInteger());
        assertFalse(LuaValue.ofInteger(1).isFloat());
        assertTrue(LuaValue.ofFloat(1.0).isFloat());
        assertFalse(LuaValue.ofFloat(1.0).isInteger());
        // 但二者都是「数字」
        assertTrue(LuaValue.ofInteger(1).isNumber());
        assertTrue(LuaValue.ofFloat(1.0).isNumber());
    }

    /** Lua 的真假：只有 nil 与 false 为假，0 与空字符串都是真 */
    @Test
    public void truthiness() {
        assertFalse(LuaValue.ofNil().toBoolean());
        assertFalse(LuaValue.ofBoolean(false).toBoolean());
        assertTrue(LuaValue.ofBoolean(true).toBoolean());
        assertTrue(LuaValue.ofInteger(0).toBoolean());
        assertTrue(LuaValue.ofFloat(0.0).toBoolean());
        assertTrue(LuaValue.ofString(LuaString.EMPTY).toBoolean());
        assertTrue(LuaValue.ofTable(new LuaTable()).toBoolean());
    }

    /** rawEqual 决定表键的行为：1 与 1.0 同键，1 与 "1" 不同键 */
    @Test
    public void rawEqual() {
        assertTrue(LuaValue.rawEqual(LuaValue.ofInteger(1), LuaValue.ofInteger(1)));
        assertTrue(LuaValue.rawEqual(LuaValue.ofInteger(1), LuaValue.ofFloat(1.0)));
        assertTrue(LuaValue.rawEqual(LuaValue.ofFloat(1.0), LuaValue.ofInteger(1)));
        assertFalse(LuaValue.rawEqual(LuaValue.ofInteger(1), LuaValue.ofFloat(1.5)));
        assertFalse(LuaValue.rawEqual(LuaValue.ofInteger(1), LuaValue.ofString(LuaString.latin1("1"))));
        assertFalse(LuaValue.rawEqual(LuaValue.ofString(LuaString.latin1("a")), LuaValue.ofNil()));
        assertTrue(LuaValue.rawEqual(LuaValue.ofNil(), LuaValue.ofNil()));

        // -0.0 与 0.0 相等（Lua 的数值相等如此）
        assertTrue(LuaValue.rawEqual(LuaValue.ofFloat(-0.0), LuaValue.ofFloat(0.0)));
        // NaN 与自身不等
        assertFalse(LuaValue.rawEqual(LuaValue.ofFloat(Double.NaN), LuaValue.ofFloat(Double.NaN)));

        // 表按引用
        LuaTable t = new LuaTable();
        assertTrue(LuaValue.rawEqual(LuaValue.ofTable(t), LuaValue.ofTable(t)));
        assertFalse(LuaValue.rawEqual(LuaValue.ofTable(t), LuaValue.ofTable(new LuaTable())));
    }

    /** 字符串相等是逐字节的 */
    @Test
    public void stringEqualityIsByBytes() {
        LuaString a = LuaString.of(new byte[] { 'a', 0, 'b' });
        LuaString b = LuaString.of(new byte[] { 'a', 0, 'b' });
        LuaString c = LuaString.of(new byte[] { 'a', 0, 'c' });
        assertTrue(LuaValue.rawEqual(LuaValue.ofString(a), LuaValue.ofString(b)));
        assertFalse(LuaValue.rawEqual(LuaValue.ofString(a), LuaValue.ofString(c)));
    }
}
