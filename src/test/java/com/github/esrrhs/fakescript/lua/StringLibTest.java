package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 字符串库的差分用例 —— 阶段 7 的第一道闸门。
 * <p>
 * 选例的着眼点是「照着 C 源码写会写错的那些地方」，不是把每个函数点一遍：
 * <ul>
 *   <li><b>逐字节</b>而非逐字符：{@code #s}、{@code byte}、{@code sub}、{@code rep}
 *       都要能处理含 {@code \0} 与非法 UTF-8 的串；</li>
 *   <li><b>相对下标</b>的夹取：{@code sub(0)}、{@code sub(-3)}、{@code byte(100)}；</li>
 *   <li><b>模式匹配</b>的边界：锚点、{@code %} 结尾、{@code %b}、{%code %f}、
 *        位置捕获、{@code gsub} 的「没改动就返回原串」；</li>
 *   <li><b>{@code format}</b>：{@code %g} 去尾零、{@code %a} 的平局舍入、
 *        {@code %q} 的转义、{@code %c} 写单字节。</li>
 * </ul>
 */
class StringLibTest {

    @TestFactory
    List<DynamicTest> stringScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "str", SCRIPTS);
        add(tests, "fmt", FORMAT);
        add(tests, "pat", PATTERN);
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

    private static final String[] SCRIPTS = {

            """
            print(("Hello"):len(), ("Hello"):upper(), ("Hello"):lower(), ("abc"):reverse())
            print(string.len(42), string.len(""), #"abc")
            """,

            """
            print(("abcdef"):sub(2,3), ("abcdef"):sub(-3), ("abcdef"):sub(0), ("abcdef"):sub(5,3))
            print(("abcdef"):sub(2,-2), ("abcdef"):sub(-100), ("abcdef"):sub(100))
            print(("abcdef"):sub(2,3,4))
            """,

            """
            print(("x"):rep(3, "-"))
            print(string.rep("ab", 0), string.rep("ab", -1))
            print(string.rep("a", 3, "sep"))
            print(#string.rep("ab", 10, "--"))
            """,

            """
            print(("abc"):byte(1,-1))
            print(("abc"):byte(0), ("abc"):byte(100), ("abc"):byte(1,1))
            print(("abc"):byte(2))
            print(string.char(72,101,108,108,111))
            print(#string.char(0, 255, 10))
            print(("a\\0b"):len(), ("a\\0b"):byte(1,3))
            print(("\\xff\\xfe"):byte(1,2))
            """,

            """
            -- 数字参数会被转换（luaL_check* 那一套的转换规则）
            print(string.sub("abcdef", "2", "4"))
            print(string.rep("ab", "2"))
            print(("x"):rep(3, 2))
            print(string.byte(65.0))
            print(pcall(string.byte, 65))
            """,

            """
            -- __index = string：方法式调用
            print(("abc"):upper(), ("ABC"):lower(), ("abc"):len())
            print(("a,b,c"):gsub(",", ";"))
            local f = ("abc").upper
            print(f("xyz"))
            print(("abc"):sub(2))
            """,

            """
            -- 字符串照旧参与算术（元表里的 __add 等还在）
            print("10" + 1, "3" * "4", -"7")
            print(("10"):len() + 1)
            """,
    };

    private static final String[] PATTERN = {

            """
            print(string.find("hello world", "o w"))
            print(string.find("hello", "l+"))
            print(string.find("hello", "l+", 4))
            print(string.find("abc", "b", 1, true))
            print(string.find("a.b", ".", 1, true))
            print(string.find("abc", ".b"))
            print(string.find("abc", ""))
            print(string.find("", ""))
            print(string.find("", "x"))
            print(("hello"):find("^h"))
            print(("hello"):find("l+", 5))
            print(string.find("abc", "b", 1, false))
            """,

            """
            print(string.match("hello world", "(%w+) (%w+)"))
            print(string.match("hello=world", "(%w+)=(%w+)"))
            print(string.match("2024-01-02", "(%d+)-(%d+)-(%d+)"))
            print(string.match("abc", "(b)(c)"))
            print(string.match("hello", "()l()"))
            print(string.match("abc", "x"))
            print(("hello"):match("^()l"))
            """,

            """
            print(string.gsub("hello world", "o", "0"))
            print(string.gsub("hello world", "%w+", "<%0>"))
            print(string.gsub("hello", "()l()", "[%1,%2]"))
            print(string.gsub("hello", "l", function(c) return c:upper() end))
            print(string.gsub("hello", "%w", {h="H", e="E"}))
            print(string.gsub("hello", "l", false))
            print(string.gsub("hello", "l", nil))
            print(string.gsub("hello", "z", "y"))
            print(("aaa"):gsub("a", "b", 2))
            print(("aaa"):gsub("a", "b", 0))
            print(("abc"):gsub("", "-"))
            print(string.gsub("hello", "l", 5))
            """,

            """
            local acc = ""
            for w in ("one two three"):gmatch("%a+") do acc = acc .. w .. "," end
            print(acc)
            local acc2 = ""
            for k, v in ("a=1, b=2"):gmatch("(%w+)=(%w+)") do acc2 = acc2 .. k .. "->" .. v .. " " end
            print(acc2)
            local acc3 = ""
            for w in ("abc"):gmatch("") do acc3 = acc3 .. "[" .. w .. "]" end
            print(acc3)
            local n = 0
            for _ in ("aaa"):gmatch("a") do n = n + 1 end
            print(n)
            print(("hello"):gmatch("l+")())
            """,

            """
            -- %b 与字符类
            print(string.match("(a(b)c)", "%b()"))
            print(string.match("xx<a>yy", "%b<>"))
            print(string.gsub("(a(b)c)", "%b()", "X"))
            print(string.match("hello world", "%a+"))
            print(string.match("42", "%d+"))
            print(string.match("  x", "%s+x"))
            print(string.match("HELLO", "%u+"))
            print(string.match("a1b2", "%w+"))
            print(string.match("z", "%A"))
            print(string.match("123", "%D"))
            print(string.match("abc", "[a-c]+"))
            print(string.match("abc", "[^a-b]+"))
            print(string.match("a-c", "%a[-]%a"))
            """,

            """
            --  frontier %f
            print(string.gsub("THE (quick) fox", "%f[%a]%a+", "X"))
            print(string.gsub("THE (quick) fox", "%f[%w]%w+", "X"))
            """,

            """
            -- 位置捕获
            print(string.find("hello", "()ll()"))
            print(("hello world"):find("()o()", 6))
            print(("abc"):gsub("()", "|"))
            """,

            """
            -- 贪婪与懒惰
            print(string.match("<a><b>", "<(.-)>"))
            print(string.match("<a><b>", "<(.*)>"))
            print(string.match("aaa", "a-"))
            print(string.match("aaa", "a*"))
            print(string.match("aaa", "a+"))
            print(("aaa"):gsub("a-", "[%0]"))
            """,
    };

    private static final String[] FORMAT = {

            """
            print(string.format("[%s] [%d] [%5.2f] [%x] [%X] [%o] [%c] [%e] [%g] [%%]",
                  "hi", 42, 3.14159, 255, 255, 8, 65, 12345.678, 12345.678))
            print(string.format("%5d|%-5d|%05d|%+d|% d", 42, 42, 42, 42, 42))
            print(string.format("%.3s|%10s|%-10s|", "abcdef", "x", "y"))
            """,

            """
            print(("|%.3f|%f|%5.2f|%-8.2f|%+f|% f|"):format(1/0, 1/0, 3.14159, 3.14159, 1.5, 1.5))
            print(("|%.3f|%.3e|%.3g|%g|%G|"):format(-1/0, -1/0, -1/0, 0/0, 0/0))
            print(("|%E|%E|%A|%A|"):format(1/0, 0/0, 1/0, 0/0))
            print(("|%.0f|%.0e|%#e|"):format(1.0, 1.0, 1.0))
            """,

            """
            print(("|%a|%A|%.2a|%010a|%-20a|%+#a|"):format(1.5, 1.5, 1.5, 1.5, 1.5, 1.5))
            print(("|%a|%a|%a|%a|"):format(0.0, -0.0, 1.0, 255.5))
            print(("|%#a|%#a|%.0a|%.1a|"):format(1.0, 1.5, 1.5, 1.5))
            print(("|%.0a|%.0a|%.0a|%.1a|"):format(1.75, 3.0, 1.5, 1.99))
            print(("|%.0a|%.0a|%.1a|%.1a|%.2a|"):format(6.0, 12.0, 1.53125, 1.59375, 1.51953125))
            print(("|%.0a|%.1a|"):format(-1.5, -1.53125))
            """,

            """
            print(("|%g|%g|%g|%g|%g|"):format(0.0001, 0.00001, 100000, 1000000, 123456789))
            print(("|%#.3g|%.3g|%5.1g|%-8g|"):format(1.0, 1.0, 0.0, 1.5))
            print(("|%#g|%#g|%#.0f|"):format(1.0, 1.5, 1.0))
            print(("|%.0f|"):format(0.5), ("|%.0f|"):format(1.5), ("|%.0f|"):format(2.5))
            """,

            """
            print(("|%d|%5d|%-5d|%05d|%+d|% d|%.0d|%.5d|"):format(0, 42, 42, 42, 42, 42, 0, 42))
            print(("|%x|%X|%#x|%#X|%#o|%o|%5.3x|%-6x|"):format(255, 255, 255, 255, 8, 8, 255, 255))
            print(("|%u|%u|%u|"):format(-1, 0, 255))
            print(("|%#x|%#X|%#.0o|%#o|"):format(0, 0, 0, 0))
            print(("|%.3d|%.3u|"):format(-5, 7))
            print(("|%x|"):format(-1))
            print(("|%d|"):format(math.mininteger), ("|%d|"):format(math.maxinteger))
            """,

            """
            print(("|%c|%c|%3c|%-3c|"):format(65, 200, 65, 65))
            print(("|%5c|"):format(9))
            print(#("%c"):format(200))
            print(("|%.3s|%10s|%-10s|%.0s|"):format("abcdef", "x", "y", "z"))
            print(("[%s]"):format(12), ("[%s]"):format(true), ("[%s]"):format(nil))
            print(("[%.5s]"):format(string.rep("x", 150)))
            print(#("[%s]"):format(string.rep("x", 150)))
            """,

            """
            print(("|%q|%q|%q|%q|%q|"):format(0, -0.0, 1/0, 0/0, "a\\0b\\\\c\\nd\\1e"))
            print(("|%q|%q|%q|"):format(2^53, -2^63, 1.5))
            print(("|%q|"):format(0.1))
            print(("|%q|%q|%q|"):format(nil, true, false))
            print(("|%q|"):format(math.mininteger))
            """,

            """
            print(("|%010.2f|%-010.2f|"):format(3.14159, 3.14159))
            print(("|%+.2e|% .2e|"):format(3.14159, 3.14159))
            print(("%s %s %s"):format(1, 2, 3))
            print(("%s"):format(setmetatable({}, {__tostring = function() return "TS" end})))
            print(pcall(string.format, "%s", setmetatable({}, {__tostring = function() return 42 end})))
            print(("%d"):format("7"), ("%d"):format(7.0))
            print(("%.99f|"):format(-1.7976931348623157e308):len())
            """,
    };

    private static final String[] ERRORS = {

            """
            print(pcall(string.len, nil))
            print(pcall(string.len, true))
            print(pcall(string.sub, "abc"))
            print(pcall(string.sub, "abc", "x"))
            print(pcall(string.rep, "a"))
            print(pcall(string.rep, "a", "x"))
            print(pcall(string.byte))
            print(pcall(string.rep, "a", 2^62))
            print(pcall(string.char, 256))
            print(pcall(string.char, -1))
            """,

            """
            print(pcall(string.format))
            print(pcall(string.format, "%d"))
            print(pcall(string.format, "%s", {}))
            print(pcall(string.format, "%z", 1))
            print(pcall(string.format, "%1000000d", 1))
            print(pcall(string.format, "%.200d", 1))
            print(pcall(string.format, "%"))
            print(pcall(string.format, "%5"))
            print(pcall(string.format, "%q", print))
            print(pcall(string.format, "%5.2s", "abc\\0def"))
            print(pcall(string.format, "%d", 2^63))
            """,

            """
            print(pcall(string.gsub, "abc", "b"))
            print(pcall(string.gsub, "abc", "b", true))
            print(pcall(string.gsub, "abc", "b", {}))
            print(pcall(string.find, "abc", "%"))
            print(pcall(string.find, "abc", "%b"))
            print(pcall(string.find, "abc", "[abc"))
            print(pcall(string.match, "abc", "%f"))
            print(pcall(string.gsub, "abc", "b", "x%"))
            print(pcall(string.gsub, "abc", "(b)", "%2"))
            print(pcall(string.match, "abc", "(b"))
            """,

            """
            print(pcall(string.len))
            local sl = string.len
            print(pcall(sl))
            local t = {len = string.len}
            print(pcall(t.len))
            print(pcall(function() return string.len(nil) end))
            print(pcall(function() return string.format("%d", "x") end))
            print(pcall(function() local f = string.len return f(5) end))
            print(pcall(function() return ("x"):rep(nil) end))
            print(pcall(function() return ("x"):sub(nil) end))
            """,

            """
            -- 递归深度上限：MAXCCALLS 挡住，不至于把 C 栈（这里是 JVM 栈）打爆
            print(pcall(string.find, string.rep("a", 12), string.rep("a*", 10) .. "b"))
            """,

            """
            -- 参数过多 / 过少时 format 的行为
            print(string.format("%s %s", "a"))
            """,
    };
}
