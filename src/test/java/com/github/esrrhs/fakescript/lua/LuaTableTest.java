package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 表层：两段结构、键的相等语义、#t 的边界、next 遍历、rehash */
public class LuaTableTest {

    /** 取不到时 get 返回 null，语义上等价于 nil */
    private static boolean isNil(LuaValue v) {
        return v == null || v.isNil();
    }

    @Test
    public void getSetBasics() {
        LuaTable t = new LuaTable();
        t.set(1, LuaValue.ofInteger(10));
        t.set("x", LuaValue.ofInteger(20));
        assertEquals(10, t.get(1).asInteger());
        assertEquals(20, t.get("x").asInteger());
        assertTrue(isNil(t.get(2)));
        assertTrue(isNil(t.get("y")));
    }

    /**
     * 整数键与字符串键是不同的项：t[1] 与 t["1"] 互不干扰。
     * 这是 Lua 表最容易在实现里被「顺手统一」掉的一条。
     */
    @Test
    public void integerAndStringKeysAreDistinct() {
        LuaTable t = new LuaTable();
        t.set(1, LuaValue.ofString(LuaString.latin1("int")));
        t.set("1", LuaValue.ofString(LuaString.latin1("str")));
        assertEquals("int", t.get(1).asString().toJavaString());
        assertEquals("str", t.get("1").asString().toJavaString());
        assertEquals(2, t.count());
    }

    /** 1 与 1.0 是同一个键（Lua 里 1 == 1.0） */
    @Test
    public void integerAndFloatKeysAreTheSame() {
        LuaTable t = new LuaTable();
        t.set(1, LuaValue.ofString(LuaString.latin1("first")));
        t.set(LuaValue.ofFloat(1.0), LuaValue.ofString(LuaString.latin1("second")));
        assertEquals("second", t.get(1).asString().toJavaString());
        assertEquals(1, t.count());
    }

    /**
     * 整值浮点键在<b>写入</b>时被规范化成整数键。
     * <p>
     * 这条不是猜的，是拿 Lua 5.4.7 实测出来的：
     * <pre>
     *   t[1.0] = "a"  →  next(t) 键的 math.type 为 "integer"，#t 为 1
     *   t[0.0] = "a"  →  键为整数 0，但 0 不是数组下标，故 #t 仍为 0
     *   t[1.5] = "a"  →  键保持浮点
     *   t[2^63] = "a" →  超出 integer 范围，键保持浮点
     * </pre>
     */
    @Test
    public void integralFloatKeysAreNormalizedToInteger() {
        LuaTable t = new LuaTable();
        t.set(LuaValue.ofFloat(1.0), LuaValue.ofInteger(1));
        LuaValue[] kv = t.next(LuaValue.ofNil());
        assertTrue(kv[0].isInteger(), "键应当是整数而不是浮点");
        assertEquals(1, kv[0].asInteger());
        // 规范化后它进了数组部分，所以 #t 是 1
        assertEquals(1, t.length());

        // 0.0 → 整数 0，但 0 不是数组下标
        LuaTable z = new LuaTable();
        z.set(LuaValue.ofFloat(0.0), LuaValue.ofInteger(7));
        assertEquals(7, z.get(0).asInteger());
        assertEquals(0, z.length());

        // 非整值浮点保持浮点
        LuaTable f = new LuaTable();
        f.set(LuaValue.ofFloat(1.5), LuaValue.ofInteger(2));
        assertTrue(f.next(LuaValue.ofNil())[0].isFloat());

        // 超出 integer 范围的整值浮点保持浮点
        LuaTable big = new LuaTable();
        big.set(LuaValue.ofFloat(9.223372036854775808E18), LuaValue.ofInteger(3));
        assertTrue(big.next(LuaValue.ofNil())[0].isFloat());
    }

    /** 写 NaN 键报错，读 NaN 键返回 nil —— 同样是 Lua 5.4.7 实测的行为 */
    @Test
    public void nanKey() {
        LuaTable t = new LuaTable();
        // 读：不报错，返回 nil
        assertTrue(isNil(t.get(LuaValue.ofFloat(Double.NaN))));
        // 写：报错
        LuaError e = org.junit.jupiter.api.Assertions.assertThrows(LuaError.class,
                () -> t.set(LuaValue.ofFloat(Double.NaN), LuaValue.ofInteger(1)));
        assertEquals("table index is NaN", e.value.asString().toJavaString());
    }

    /** 赋 nil 等于删除（键留在表里但值为 nil，与 C 版一致） */
    @Test
    public void assigningNilRemovesValue() {
        LuaTable t = new LuaTable();
        t.set(1, LuaValue.ofInteger(1));
        t.set("a", LuaValue.ofInteger(2));
        assertEquals(2, t.count());
        t.set(1, LuaValue.ofNil());
        t.set("a", LuaValue.ofNil());
        assertEquals(0, t.count());
        assertTrue(isNil(t.get(1)));
    }

    /** 数组部分按 2 的幂增长，密集整数键会被安排进数组部分 */
    @Test
    public void arrayPartGrowsForDenseIntegerKeys() {
        LuaTable t = new LuaTable();
        assertEquals(0, t.arrayPartSize());
        t.set(1, LuaValue.ofInteger(1));
        assertEquals(1, t.arrayPartSize());
        t.set(2, LuaValue.ofInteger(2));
        assertEquals(2, t.arrayPartSize());
        for (int i = 3; i <= 10; i++) {
            t.set(i, LuaValue.ofInteger(i));
        }
        // 10 个密集键 → 数组部分至少 8（2 的幂），具体档位由 computesizes 决定
        assertTrue(t.arrayPartSize() >= 8, "array part = " + t.arrayPartSize());
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, t.get(i).asInteger());
        }
    }

    /** 稀疏的大整数键不该撑爆数组部分 */
    @Test
    public void sparseKeyDoesNotBlowUpArrayPart() {
        LuaTable t = new LuaTable();
        t.set(1_000_000, LuaValue.ofInteger(1));
        assertTrue(t.arrayPartSize() <= 4, "array part = " + t.arrayPartSize());
        assertEquals(1, t.get(1_000_000).asInteger());
        assertEquals(1, t.count());
    }

    /** #t 返回一个合法边界：t[n] 非 nil 且 t[n+1] 为 nil */
    @Test
    public void lengthReturnsABorder() {
        LuaTable t = new LuaTable();
        for (int i = 1; i <= 5; i++) {
            t.set(i, LuaValue.ofInteger(i));
        }
        long n = t.length();
        assertFalse(isNil(t.get(n)), "t[" + n + "] 应当存在");
        assertTrue(isNil(t.get(n + 1)), "t[" + (n + 1) + "] 应当为 nil");
    }

    /** 边界藏在哈希部分时（数组满 + 哈希里有更大的整数键）也能找到 */
    @Test
    public void lengthWithBorderInHashPart() {
        LuaTable t = new LuaTable();
        for (int i = 1; i <= 3; i++) {
            t.set(i, LuaValue.ofInteger(i));
        }
        t.set("str", LuaValue.ofInteger(9)); // 逼出哈希部分
        t.set(4, LuaValue.ofInteger(4));
        t.set(5, LuaValue.ofInteger(5));
        long n = t.length();
        assertFalse(isNil(t.get(n)));
        assertTrue(isNil(t.get(n + 1)));
    }

    /** next 必须不重不漏地遍历所有项 */
    @Test
    public void nextVisitsEveryEntryOnce() {
        LuaTable t = new LuaTable();
        for (int i = 1; i <= 20; i++) {
            t.set(i, LuaValue.ofInteger(i));
        }
        t.set("a", LuaValue.ofInteger(100));
        t.set("b", LuaValue.ofInteger(200));
        t.set(LuaValue.ofFloat(2.5), LuaValue.ofInteger(300));

        List<String> seen = new ArrayList<>();
        LuaValue k = LuaValue.ofNil();
        while (true) {
            LuaValue[] kv = t.next(k);
            if (kv == null) {
                break;
            }
            seen.add(kv[0].toString() + "=" + kv[1].asInteger());
            k = kv[0];
        }
        assertEquals(23, seen.size());
        assertEquals(23, new java.util.HashSet<>(seen).size(), "next 不应重复: " + seen);
    }

    /** 大量随机键反复插入/置 nil，最后存活的项必须与预期一致 */
    @Test
    public void survivesMixedInsertDelete() {
        LuaTable t = new LuaTable();
        java.util.Map<Long, Long> intModel = new java.util.HashMap<>();
        java.util.Map<String, Long> strModel = new java.util.HashMap<>();
        java.util.Random r = new java.util.Random(0xC0FFEE);

        for (int step = 0; step < 3000; step++) {
            if (r.nextInt(3) == 0) {
                long k = r.nextInt(50) + 1;
                t.set(k, LuaValue.ofNil());
                intModel.remove(k);
            } else if (r.nextBoolean()) {
                long k = r.nextInt(50) + 1;
                long v = r.nextInt(1000);
                t.set(k, LuaValue.ofInteger(v));
                intModel.put(k, v);
            } else {
                String k = "k" + r.nextInt(30);
                long v = r.nextInt(1000);
                t.set(k, LuaValue.ofInteger(v));
                strModel.put(k, v);
            }
        }

        assertEquals(intModel.size() + strModel.size(), t.count());
        for (java.util.Map.Entry<Long, Long> e : intModel.entrySet()) {
            LuaValue v = t.get(e.getKey());
            assertEquals(e.getValue().longValue(), v.asInteger(), "key " + e.getKey());
        }
        for (java.util.Map.Entry<String, Long> e : strModel.entrySet()) {
            assertEquals(e.getValue().longValue(), t.get(e.getKey()).asInteger(), "key " + e.getKey());
        }
    }

    /** 表作为键按引用相等 */
    @Test
    public void tableAsKey() {
        LuaTable t = new LuaTable();
        LuaTable k1 = new LuaTable();
        LuaTable k2 = new LuaTable();
        t.set(LuaValue.ofTable(k1), LuaValue.ofInteger(1));
        t.set(LuaValue.ofTable(k2), LuaValue.ofInteger(2));
        assertEquals(1, t.get(LuaValue.ofTable(k1)).asInteger());
        assertEquals(2, t.get(LuaValue.ofTable(k2)).asInteger());
        assertEquals(2, t.count());
    }

    /** 布尔也能作键（true 与 false 是不同键） */
    @Test
    public void booleanKeys() {
        LuaTable t = new LuaTable();
        t.set(LuaValue.ofBoolean(true), LuaValue.ofInteger(1));
        t.set(LuaValue.ofBoolean(false), LuaValue.ofInteger(2));
        assertEquals(1, t.get(LuaValue.ofBoolean(true)).asInteger());
        assertEquals(2, t.get(LuaValue.ofBoolean(false)).asInteger());
        assertEquals(2, t.count());
    }

    /** 元表存取 */
    @Test
    public void metatable() {
        LuaTable t = new LuaTable();
        LuaTable mt = new LuaTable();
        assertNull(t.getMetatable());
        t.setMetatable(mt);
        assertSame(mt, t.getMetatable());
    }

    /** 不存在的键传给 next 应当报错（对应 "invalid key to 'next'"） */
    @Test
    public void nextRejectsUnknownKey() {
        LuaTable t = new LuaTable();
        t.set("a", LuaValue.ofInteger(1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> t.next(LuaValue.ofString(LuaString.latin1("absent"))));
    }
}
