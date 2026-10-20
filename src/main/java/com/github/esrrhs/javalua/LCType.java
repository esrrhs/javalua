package com.github.esrrhs.javalua;

/**
 * 字符分类 —— 对应 {@code lctype.c} 的 {@code luai_ctype_} 表。
 * <p>
 * 为什么照搬这张表而不是用 {@code Character.isDigit} 之类：Lua 的字符分类是
 * <b>与 locale 无关的 ASCII 固定表</b>，且语义与 C 标准库并不完全一致——
 * 最明显的是 {@code lislalpha} 把 {@code '_'} 当作字母（所以 Lua 标识符可以
 * 以下划线开头），而 {@code isalpha} 不会。照搬表可以逐字节对齐上游行为。
 * <p>
 * 表按 {@code c + 1} 索引，是为了让 EOZ（-1）也有一个条目。
 */
final class LCType {

    private LCType() {
    }

    private static final int ALPHABIT = 0;
    private static final int DIGITBIT = 1;
    private static final int PRINTBIT = 2;
    private static final int SPACEBIT = 3;
    private static final int XDIGITBIT = 4;

    private static final int MASK_ALPHA = 1 << ALPHABIT;
    private static final int MASK_DIGIT = 1 << DIGITBIT;
    private static final int MASK_PRINT = 1 << PRINTBIT;
    private static final int MASK_SPACE = 1 << SPACEBIT;
    private static final int MASK_XDIGIT = 1 << XDIGITBIT;

    /**
     * 逐字节照搬 {@code lctype.c}。NONA 取 0（未定义 LUA_UCID），
     * 即所有非 ASCII 字节都不是字母——默认配置下 Lua 标识符只能是 ASCII。
     */
    private static final byte[] RAW = {
        (byte) 0x00, /* EOZ */
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, /* 0. */
        (byte) 0x00, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, /* 1. */
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x0c, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, /* 2. */
        (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04,
        (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16, /* 3. */
        (byte) 0x16, (byte) 0x16, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04,
        (byte) 0x04, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x05, /* 4. */
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05,
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, /* 5. */
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x05,
        (byte) 0x04, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x05, /* 6. */
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05,
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x05, /* 7. */
        (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x00,
        /* 8. .. f. ：NONA == 0，全部非 ASCII 字节都不是字母 */
    };

    /** 补齐 128..255（NONA == 0，全为 0） */
    private static byte[] pad(byte[] raw) {
        byte[] full = new byte[257];
        System.arraycopy(raw, 0, full, 0, raw.length);
        return full;
    }

    private static final byte[] CTYPE = pad(RAW);

    private static int prop(int c) {
        // c 可能是 EOZ(-1)；表按 c+1 索引
        int idx = c + 1;
        if (idx < 0 || idx >= CTYPE.length) {
            return 0;
        }
        return CTYPE[idx] & 0xFF;
    }

    /** {@code lislalpha}：字母或 '_'（注意：包含下划线，与 C 的 isalpha 不同） */
    static boolean isAlpha(int c) {
        return (prop(c) & MASK_ALPHA) != 0;
    }

    /** {@code lislalnum} */
    static boolean isAlnum(int c) {
        return (prop(c) & (MASK_ALPHA | MASK_DIGIT)) != 0;
    }

    static boolean isDigit(int c) {
        return (prop(c) & MASK_DIGIT) != 0;
    }

    static boolean isSpace(int c) {
        return (prop(c) & MASK_SPACE) != 0;
    }

    static boolean isPrint(int c) {
        return (prop(c) & MASK_PRINT) != 0;
    }

    static boolean isXdigit(int c) {
        return (prop(c) & MASK_XDIGIT) != 0;
    }

    /** {@code ltolower}：只对大写字母与 '.' 有意义，这正是 Lua 需要的范围 */
    static int toLower(int c) {
        return c | ('A' ^ 'a');
    }
}
