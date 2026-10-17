package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * {@code lobject.c: luaO_chunkid}。
 * <p>
 * 单独开一类是因为它有一个<b>只在长名字上才出现</b>的坑，而差分用例全跑在
 * {@code target/rt/case.lua} 这个短路径上，永远碰不到。它是接上游测试套件时
 * 第一个炸出来的东西：用例目录的绝对路径一长，{@code error} 拼消息前缀
 * 就越界一字节。
 * <p>
 * 期望值全部<b>实测</b>于参考实现（{@code load("error('boom')", name)} 后看
 * {@code pcall} 拿到的消息前缀）：
 * <pre>
 *   = + 30 个 n   (srclen 31) → 30 字节
 *   = + 80 个 n   (srclen 81) → 59 字节（= LUA_IDSIZE - 1）
 *   @ + 70 个 a   (srclen 71) → 59 字节，"..." + 56
 *   @ + 86 字节路径(srclen 87) → 59 字节
 *   80 个 s                    → [string "sss..."] 共 59 字节
 *   "has\n..."                 → [string "has..."] 共 17 字节（只取第一行）
 * </pre>
 * <p>
 * 关键点是 {@code srclen} <b>含</b> 那个前导字符（{@code '='} / {@code '@'}）。
 * C 版 {@code memcpy} 的末位读到的是字符串结尾的 {@code '\0'}，所以可见内容比
 * 参数里的 {@code bufflen} 少一；照抄那个长度在 Java 里就是越界。
 */
final class ChunkIdTest {

    private static String id(String src) {
        return new String(LuaState.chunkId(src.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    /** {@code '='}：短名原样 */
    @Test
    void literalShort() {
        assertEquals("nnnnnnnnnnnnnnnnnnnnnnnnnnnnnn", id("=" + "n".repeat(30)));
        assertEquals("short", id("=short"));
    }

    /** {@code '='}：过长从右边截到 {@code LUA_IDSIZE - 1} */
    @Test
    void literalLong() {
        String s = id("=" + "n".repeat(80));
        assertEquals(59, s.length());
        assertEquals("n".repeat(59), s);
    }

    /**
     * {@code '@'}：过长在<b>前面</b>加 {@code ...}，保留末尾那一段。
     * <p>
     * 这一条就是那个越界 bug：C 的 {@code bufflen} 是 57，实际搬 56 字节。
     */
    @Test
    void fileNameLong() {
        String s = id("@" + "a".repeat(70));
        assertEquals(59, s.length());
        assertEquals("..." + "a".repeat(56), s);

        String p = id("@" + "/dir".repeat(20) + "/f.lua");
        assertEquals(59, p.length());
        assertEquals("...ir/dir/dir/dir/dir/dir/dir/dir/dir/dir/dir/dir/dir/f.lua", p);
    }

    /** {@code '@'}：短名去掉 {@code '@'} 原样 */
    @Test
    void fileNameShort() {
        assertEquals("x.lua", id("@x.lua"));
        assertEquals(59, id("@" + "a".repeat(59)).length()); // srclen 60 仍在界内
    }

    /** 其余：包成 {@code [string "..."]}，过长在<b>后面</b>加 {@code ...} */
    @Test
    void stringSource() {
        assertEquals("[string \"sssssssssssssssssssssssssssssssssssssssssssss...\"]",
                id("s".repeat(80)));
    }

    /** 只取第一行 */
    @Test
    void stringSourceStopsAtNewline() {
        assertEquals("[string \"has...\"]", id("has\nnewline and " + "z".repeat(80)));
        assertEquals("[string \"abc\"]", id("abc"));
    }
}
