package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 字符串层。这里的用例全是「用 Java String 就会错」的场景：
 * 内嵌 \0、任意非法 UTF-8 字节、按字节而非按字符的长度。
 */
public class LuaStringTest {

    /** #s 数的是字节，不是字符，也不是 UTF-16 码元 */
    @Test
    public void lengthCountsBytes() {
        assertEquals(0, LuaString.EMPTY.length());
        assertEquals(3, LuaString.latin1("abc").length());
        // "中" 的 UTF-8 是 3 字节；Java String.length() 会是 1
        assertEquals(3, LuaString.utf8("中").length());
        // 4 字节 UTF-8（emoji），Java String.length() 会是 2
        assertEquals(4, LuaString.utf8("\uD83D\uDE00").length());
    }

    /** \0 是合法内容，且计入长度 */
    @Test
    public void embeddedZeroBytes() {
        LuaString s = LuaString.of(new byte[] { 'a', 0, 'b', 0 });
        assertEquals(4, s.length());
        assertEquals('a', s.byteAt(0));
        assertEquals(0, s.byteAt(1));
        assertEquals('b', s.byteAt(2));
        assertEquals(0, s.byteAt(3));
    }

    /** 任意字节（含非法 UTF-8）必须能原样保存 —— 字节串语义的硬指标 */
    @Test
    public void arbitraryBytesRoundTrip() {
        byte[] raw = new byte[256];
        for (int i = 0; i < 256; i++) {
            raw[i] = (byte) i;
        }
        LuaString s = LuaString.of(raw);
        assertEquals(256, s.length());
        for (int i = 0; i < 256; i++) {
            assertEquals(i, s.byteAt(i));
        }
        // 与 Java String 的往返不同：这里不能丢字节
        assertEquals(256, LuaString.of(s.toJavaString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)).length());
    }

    @Test
    public void concatenationAndSubstring() {
        LuaString a = LuaString.latin1("ab");
        LuaString b = LuaString.of(new byte[] { 0, 'c' });
        assertEquals(4, a.concat(b).length());
        assertEquals(0, a.concat(b).byteAt(2));
        assertEquals("xy", LuaString.latin1("wxyz").substring(1, 3).toJavaString());
    }

    /** 相等与哈希基于内容 */
    @Test
    public void contentEquality() {
        LuaString a = LuaString.latin1("hello");
        LuaString b = LuaString.of(new byte[] { 'h', 'e', 'l', 'l', 'o' });
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, LuaString.latin1("hellp"));
    }

    /** 超过 LUAI_MAXSHORTLEN 的不算短字符串（不参与驻留） */
    @Test
    public void shortStringThreshold() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Lua.MAXSHORTLEN; i++) {
            sb.append('x');
        }
        assertTrue(LuaString.latin1(sb.toString()).isShort());
        assertFalse(LuaString.latin1(sb.toString() + "x").isShort());
    }

    /** latin1() 只接受 0..255，超出应当报错而不是静默截断 */
    @Test
    public void latin1RejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> LuaString.latin1("中"));
    }
}
