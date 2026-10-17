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
        add(tests, "char", CHAR);
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

    /**
     * {@code utf8.char} 与 {@code utf8.codepoint} 的边角。
     * <p>
     * 三条容易记反的，全都是实测（参考二进制）钉住的：
     * <ul>
     *   <li><b>{@code MAXUTF} 是 {@code 0x7FFFFFFF}</b>，所以 {@code 0x110000}
     *       与 {@code 0xD800}（代理区）在 5.4 里都是<b>合法</b>码点 ——
     *       按 Unicode 的直觉写「越界」用例会得到一个正常的字符串，而不是报错。</li>
     *   <li><b>越界是按<b>无符号</b>比的</b>：{@code utf8.char(-1)} 转成
     *       {@code 0xFFFF...} 于是报错；按有符号比 -1 会「通过」并吐出一个
     *       {@code ¿} 开头的垃圾字节。</li>
     *   <li><b>单参与多参走的是两条分支</b>，各自都有「value out of range」。
     *       只测单参的那条覆盖不到多参的那一处 —— 覆盖率报告上表现为整段 nc。</li>
     * </ul>
     * 另外 {@code charpattern} 这一组是<b>真被拿来切字符</b>的：它是
     * {@code [\0-\x7F\xC2-\xFD][\x80-\xBF]*}，首字节排除了 {@code \xC0} /
     * {@code \xC1}（过长编码）与 {@code \xFE} / {@code \xFF}（非法），所以
     * {@code "\xff\xfe"} 上一个都匹配不到。只用
     * {@code print(utf8.charpattern)} 比对原样输出的话，这个性质一点都没验到。
     */
    private static final String[] CHAR = {
            """
-- utf8.char / utf8.codepoint 的边角
local function p(tag, f, ...)
    local ok, a, b = pcall(f, ...)
    if not ok then
        print(string.format("%s ERR %s", tag, tostring(a)))
    else
        local t = { tostring(a), tostring(b) }
        print(string.format("%s %s | %s", tag, t[1], t[2]))
    end
end
local function q(tag, s)
    -- 结果按字节摊开，含 \\0 也看得见
    local t = {}
    for i = 1, #s do
        t[i] = string.format("%02X", string.byte(s, i))
    end
    print(string.format("%s #%d [%s]", tag, #s, table.concat(t, " ")))
end

-- A. utf8.char：单参
q("A01", utf8.char(65))
q("A02", utf8.char(0))
q("A03", utf8.char(0x7F))
q("A04", utf8.char(0x80))
q("A05", utf8.char(0x7FF))
q("A06", utf8.char(0x800))
q("A07", utf8.char(0xFFFF))
q("A08", utf8.char(0x10000))
q("A09", utf8.char(0x10FFFF))
q("A10", utf8.char(0xD800))                  -- 代理区：5.4 允许
p("A11", utf8.char, 0x110000)
p("A12", utf8.char, 0x7FFFFFFF)
p("A13", utf8.char, -1)
p("A14", utf8.char, -0x80000000)
p("A15", utf8.char, math.maxinteger)
p("A16", utf8.char, math.mininteger)

-- B. utf8.char：多参（第二个之后越界走的是另一条分支）
q("B01", utf8.char())
q("B02", utf8.char(65, 66, 67))
q("B03", utf8.char(65, 0x4E2D, 66))
p("B04", utf8.char, 65, 0x7FFFFFFF)
p("B05", utf8.char, 65, -1)
p("B06", utf8.char, 65, 0x110000)
p("B07", utf8.char, 0x110000, 65)
p("B08", utf8.char, 65, 66, -1, 67)
p("B09", utf8.char, 65, true)
p("B10", utf8.char, 65, 66.0)
p("B11", utf8.char, 65, 66.5)
p("B12", utf8.char, "65", 66)
p("B13", utf8.char, 65, nil)                 -- nil 收尾：算几个参数
p("B14", utf8.char, 65, 66, 67, 68, 69, 70, 71, 72)

-- C. utf8.codepoint 的越界形状
local s = "a中b"
p("C01", utf8.codepoint, s)
p("C02", utf8.codepoint, s, 1)
p("C03", utf8.codepoint, s, 1, 2)
p("C04", utf8.codepoint, s, 2, 1)            -- i > j：一个都不给
p("C05", utf8.codepoint, s, 0)
p("C06", utf8.codepoint, s, 1, 6)
p("C07", utf8.codepoint, s, -6)
p("C08", utf8.codepoint, s, 1, -6)
p("C09", utf8.codepoint, "", 1)
p("C10", utf8.codepoint, "", 1, 0)
p("C11", utf8.codepoint, s, 1, 5, true)
p("C12", utf8.codepoint, "\\xff", 1, 1, true)
p("C13", utf8.codepoint, "\\xff", 1, 1, false)
p("C14", utf8.codepoint, s, "2")
p("C15", utf8.codepoint, s, 1, "3")
p("C16", utf8.codepoint, s, 1.0, 2.0)
p("C17", utf8.codepoint, s, 1.5)
p("C18", utf8.codepoint, s, true)

-- D. charpattern 真被用过：能不重不漏地切字符
local pat = utf8.charpattern
local n = 0
for _ in ("a中b\\u{1F600}"):gmatch(pat) do
    n = n + 1
end
print("D01 " .. n)
local n2 = 0
for _ in ("\\xff\\xfe"):gmatch(pat) do
    n2 = n2 + 1
end
print("D02 " .. n2)
print("D03 " .. ("a中b"):gsub(pat, "#"))
print("D04 " .. tostring(pat:byte(1, -1)))
            """,
    };
}
