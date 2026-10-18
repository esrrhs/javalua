package com.github.esrrhs.fakescript.lua;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Lua 字符串 —— 对应 {@code lobject.h} 的 {@code TString}。
 * <p>
 * <b>底层是 {@code byte[]} 而不是 Java {@code String}</b>。这是本实现最重要的一处
 * 「不能照搬 Java 习惯」的地方：Lua 字符串是字节串，可以包含 {@code \0}，
 * {@code #s} 数的是字节数，{@code s:byte(i)} 返回字节值，{@code \x80} 这类
 * 非法 UTF-8 序列也是合法字符串。Java 的 {@code String} 是 UTF-16 且拒绝孤立
 * 代理项，用它当底层必然在处理二进制数据或 UTF-8 文本的真实脚本上出错。
 * <p>
 * 代价是所有字符串库函数都要按字节实现，不能顺手调 Java 的 String 方法——
 * 这正是 {@code lstrlib.c} 原本的做法，照搬即可。
 */
public final class LuaString {

    public static final LuaString EMPTY = new LuaString(new byte[0]);

    /** 内容，构造后不再变化（与 C 版 TString 一样不可变） */
    public final byte[] bytes;

    /** 缓存的哈希值，对应 {@code lstring.c} 的 {@code luaS_hash} 结果 */
    private final int hash;

    private LuaString(byte[] bytes) {
        this.bytes = bytes;
        this.hash = luaSHash(bytes);
    }

    /**
     * {@code lstring.c: luaS_hash}：从尾部按步长跳跃的混合哈希。
     * <p>
     * 哈希值只影响哈希表内部分布，不影响任何可观测语义，但照搬上游实现可以让
     * 未来的对比调试更好做（同一个字符串在两边落到同一个桶）。
     */
    private static int luaSHash(byte[] b) {
        int h = b.length;
        int step = (b.length >> 5) + 1;
        for (int i = b.length; i >= step; i -= step) {
            h ^= (h << 5) + (h >> 2) + (b[i - 1] & 0xFF);
        }
        return h & 0x7FFFFFFF;
    }

    public static LuaString of(byte[] bytes) {
        return new LuaString(bytes);
    }

    public static LuaString of(byte[] buf, int off, int len) {
        return new LuaString(Arrays.copyOfRange(buf, off, off + len));
    }

    /**
     * 内部使用：把 Java {@code String} 按 Latin-1 逐字节映射。
     * <p>
     * 只在元方法名、错误信息这类由本实现自己产生的 ASCII 文本上使用。
     * 之所以用 Latin-1 而非 UTF-8，是因为它保证 {@code byte<->char} 在 0..255
     * 上无损往返，不会因为编码选择意外改写内容。
     */
    public static LuaString latin1(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0xFF) {
                throw new IllegalArgumentException("not latin-1: " + c);
            }
            b[i] = (byte) c;
        }
        return new LuaString(b);
    }

    /** 按 UTF-8 编码。用于需要真实 UTF-8 字节的场合（如 os.getenv 返回值） */
    public static LuaString utf8(String s) {
        return new LuaString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 字节长度，对应 {@code tsslen} —— 也就是 {@code #s} 的结果 */
    public int length() {
        return bytes.length;
    }

    /** 取第 i 个字节（0 起），返回 0..255 */
    public int byteAt(int i) {
        return bytes[i] & 0xFF;
    }

    /** 是否是短字符串（参与驻留） */
    public boolean isShort() {
        return bytes.length <= Lua.MAXSHORTLEN;
    }

    public int hash() {
        return hash;
    }

    /** 拼接 */
    public LuaString concat(LuaString other) {
        byte[] b = new byte[bytes.length + other.bytes.length];
        System.arraycopy(bytes, 0, b, 0, bytes.length);
        System.arraycopy(other.bytes, 0, b, bytes.length, other.bytes.length);
        return new LuaString(b);
    }

    public LuaString substring(int from, int to) {
        return new LuaString(Arrays.copyOfRange(bytes, from, to));
    }

    /** 按 UTF-8 解码成 Java String —— 仅用于调试输出与宿主交互，非法序列会被替换 */
    public String toJavaString() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 按 Latin-1 逐字节解码，与 {@link #latin1} 互为逆运算。
     * <p>
     * 用在「名字要绕一圈 Java String 再拼回 Lua 字符串」的场合：varinfo 的字段名
     * （{@code t["\xff"]} 里的常量）、元表的 {@code __name}、{@code require} 的
     * 模块名、{@code argError} 反查出来的函数名。这些地方最终都要
     * {@code LuaString.latin1} 编码回去，而用 UTF-8 解码会把 {@code 0xFF} 变成
     * {@code U+FFFD} —— 于是要么内容被改写（编码成 3 字节 {@code EF BF BD}），
     * 要么 {@code latin1} 直接抛 {@code IllegalArgumentException}
     * （{@code U+FFFD > 0xFF}）。Latin-1 保证 {@code byte<->char} 在 0..255 上
     * 无损往返，是这条链上唯一安全的中间表示。
     */
    public String toLatin1String() {
        char[] c = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            c[i] = (char) (bytes[i] & 0xFF);
        }
        return new String(c);
    }

    /**
     * 按 <b>C 字符串</b>语义截断到第一个 {@code \0}。
     * <p>
     * 移植时凡是 C 版把 {@code LuaString} 当作 {@code const char *} 用的地方
     * （格式化进 {@code "%s"}、拿去当类型名、拼进报错文案），内容都会在第一个
     * NUL 处被悄悄砍断 —— 因为 C 字符串以 NUL 结尾，而 Lua 字符串不以 NUL 结尾。
     * 实测 {@code tostring(setmetatable({}, {__name = "A\0B"}))}：参考实现打印
     * {@code A: 0x...}，只有 {@code A}。
     */
    public LuaString toCString() {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return new LuaString(Arrays.copyOf(bytes, i));
            }
        }
        return this;
    }

    /**
     * {@link #toCString} 的 <b>Java String</b> 版：把 Latin-1 中间表示的名字
     * 按 C 字符串语义截断到第一个 {@code \0}。
     * <p>
     * 名字从字节串出发、经 {@link #toLatin1String} 变成 Java String、最后被拼进
     * {@code "%s"} 一路的那些出口（{@code findfield} 反查出来的函数名、
     * {@code luaO_chunkid} 的结果）都卡在这一步：C 那边 {@code %s} 读的是
     * {@code const char *}，第一个 NUL 之后的内容根本看不见。实测
     * {@code package.loaded["M\0"] = {["\0"] = f}} 之后 {@code debug.traceback()}
     * 打印 {@code in function 'M'}，不是 {@code 'M<00>.<00>'}。
     * <p>
     * 高字节<b>不</b>动：只有 NUL 是 C 字符串的终止符。
     */
    public static String cutAtNul(String latin1) {
        if (latin1 == null) {
            return null;
        }
        int i = latin1.indexOf('\0');
        return (i < 0) ? latin1 : latin1.substring(0, i);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LuaString)) {
            return false;
        }
        LuaString s = (LuaString) o;
        return hash == s.hash && Arrays.equals(bytes, s.bytes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return toJavaString();
    }
}
