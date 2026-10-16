package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * utf8 库的差分用例 —— 阶段 7。
 * <p>
 * UTF-8 是<b>盖在字节串之上的一层</b>，不是字符串的另一种表示：
 * {@code #s} 数的是字节，{@code utf8.len} 数的是字符，含 {@code \xFF}
 * 的串照样能用（只是 {@code len} 返回 {@code nil + 出错位置}）。
 * <p>
 * 几条容易写错的：
 * <ul>
 *   <li><b>{@code utf8.len} 用两值返回失败</b>：{@code nil, 出错字节下标}，
 *       不是抛错也不是返回 -1；{@code lax} 模式下会跳过非法序列继续数。</li>
 *   <li><b>{@code offset} 默认往<b>后</b>走</b>（{@code n} 为正时找第 n 个
 *       字符的<b>起始</b>字节），{@code n} 为 0 时返回当前字符的起始；
 *       越界返回 {@code nil}（{@code i} 已越界）或报错（{@code i} 越出串外）。</li>
 *   <li><b>{@code codepoint} 的默认只取一个</b>：{@code utf8.codepoint(s)}
 *       返回单个值，给了 {@code j} 才返回一串。</li>
 *   <li><b>{@code charpattern}</b> 是一个固定的模式串，直接比对原样输出。</li>
 * </ul>
 */
class Utf8LibTest {

    @TestFactory
    List<DynamicTest> utf8ScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "len", LEN);
        add(tests, "off", OFFSET);
        add(tests, "code", CODE);
        add(tests, "err", ERRORS);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    private static final String[] LEN = {

            """
            print(utf8.charpattern)
            print(#"hello", utf8.len("hello"))
            print(utf8.len(""), #"")
            print(#"中文", utf8.len("中文"))
            print(#"\\u{1F600}", utf8.len("\\u{1F600}"))
            print(utf8.len("a\\u{4E2D}b"))
            """,

            """
            -- 非法字节：返回 nil + 出错位置
            print(utf8.len("\\xff"))
            print(utf8.len("a\\xffb"))
            print(utf8.len("\\xc3"))          -- 截断的两字节序列
            print(utf8.len("\\xe4\\xb8"))       -- 截断的三字节序列
            print(utf8.len("\\xc3\\x28"))       -- 第二个字节不是续字节
            print(utf8.len("ok"), utf8.len("ok") == 2)
            """,

            """
            -- lax 模式：跳过非法序列继续数
            print(utf8.len("\\xff", 1, -1, true))
            print(utf8.len("a\\xffb", 1, -1, true))
            print(utf8.len("\\xc3", 1, -1, true))
            print(utf8.len("\\xed\\xa0\\x80", 1, -1, true))  --  surrogate
            print(utf8.len("\\xf5\\x80\\x80\\x80", 1, -1, true)) --  > U+10FFFF
            """,

            """
            -- 带起止区间
            print(utf8.len("中文", 2))
            print(utf8.len("中文", 1, 3))
            print(utf8.len("中文", 4))
            print(utf8.len("abc", -2))
            print(utf8.len("abc", 2, -1))
            """,
    };

    private static final String[] OFFSET = {

            """
            local s = "中文abc"
            print(utf8.offset(s, 1), utf8.offset(s, 2), utf8.offset(s, 3))
            print(utf8.offset(s, 4), utf8.offset(s, 5), utf8.offset(s, 6))
            print(utf8.offset(s, 7))
            print(utf8.offset(s, -1), utf8.offset(s, -2), utf8.offset(s, -3))
            print(utf8.offset(s, -4), utf8.offset(s, -6))
            print(utf8.offset(s, -7))
            """,

            """
            local s = "abc"
            print(utf8.offset(s, 0, 2))
            print(utf8.offset(s, 1, 2), utf8.offset(s, 2, 2))
            print(utf8.offset(s, -1, 2))
            print(utf8.offset(s, 1, 4))
            print(utf8.offset(s, 1, 5))
            """,

            """
            print(utf8.offset("abc", 1, 100))
            print(utf8.offset("abc", 1, 0))
            print(utf8.offset("", 1))
            print(utf8.offset("", 1, 1))
            print(utf8.offset("中", 2))
            """,
    };

    private static final String[] CODE = {

            """
            print(utf8.codepoint("a"))
            print(utf8.codepoint("中"))
            print(utf8.codepoint("\\u{1F600}"))
            print(utf8.codepoint("abc", 2))
            print(utf8.codepoint("abc", 2, 3))
            print(utf8.codepoint("中文", 1, -1))
            print(select("#", utf8.codepoint("abc")))
            print(select("#", utf8.codepoint("abc", 1, 3)))
            """,

            """
            print(utf8.char(65, 66, 67))
            print(#utf8.char(0x4E2D), utf8.codepoint(utf8.char(0x4E2D)))
            print(#utf8.char())
            print(utf8.char(0x1F600) == "\\u{1F600}")
            print(#utf8.char(0x10FFFF), utf8.codepoint(utf8.char(0x10FFFF)))
            print(utf8.codepoint(utf8.char(0, 0x7F, 0x80, 0x7FF, 0x800, 0xFFFF)))
            """,

            """
            -- codes 迭代器
            local t = {}
            for p, c in utf8.codes("a中b") do t[#t+1] = p .. ":" .. c end
            print(table.concat(t, " "))
            local u = {}
            for p, c in utf8.codes("\\u{1F600}") do u[#u+1] = p .. ":" .. c end
            print(table.concat(u, " "))
            local v = {}
            for p, c in utf8.codes("") do v[#v+1] = p end
            print(#v)
            """,
    };

    private static final String[] ERRORS = {

            """
            print(pcall(utf8.char, -1))
            print(pcall(utf8.char, 0x110000))
            print(pcall(utf8.char, "x"))
            print(pcall(utf8.char, 1.5))
            print(pcall(utf8.codepoint))
            print(pcall(utf8.codepoint, 42))
            """,

            """
            print(pcall(utf8.codepoint, "\\xff"))
            print(pcall(utf8.codepoint, "a\\xffb", 1, 3))
            print(pcall(function() for p, c in utf8.codes("\\xff") do end end))
            print(pcall(function() for p, c in utf8.codes("a\\xff") do end end))
            """,

            """
            print(pcall(utf8.len, 42))
            print(pcall(utf8.len, "abc", "x"))
            print(pcall(utf8.offset, 42, 1))
            print(pcall(utf8.offset, "abc", "x"))
            print(pcall(utf8.len, nil))
            """,
    };
}
