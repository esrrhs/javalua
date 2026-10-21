package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
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
            -- 「舍弃的第一位十六进制数字恰为 8」的平局用例不在这里：glibc 是
            -- 就近舍入 + 平局取偶，Apple libc 在这一段根本不是就近舍入，两边
            -- 答案不同；它们由 hexFloatPrecisionRoundingFollowsGlibc 钉住。
            print(("|%a|%A|%.2a|%010a|%-20a|%+#a|"):format(1.5, 1.5, 1.5, 1.5, 1.5, 1.5))
            print(("|%a|%a|%a|%a|"):format(0.0, -0.0, 1.0, 255.5))
            print(("|%#a|%#a|%.1a|%.2a|"):format(1.0, 1.5, 1.5, 1.5))
            print(("|%.0a|%.0a|%.0a|%.1a|"):format(1.75, 2.5, 4.5, 1.99))
            print(("|%.1a|%.2a|%.2a|"):format(-1.5, -1.53125, 1.51953125))
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

    /**
     * 次正规浮点的 {@code %q} —— <b>刻意不进差分表</b>。
     * <p>
     * {@code lstrlib.c: quotefloat} 用的是 C 的 {@code %a}：
     * <pre>
     * int nb = lua_number2strx(L, buff, MAX_ITEM, "%a", n);
     * </pre>
     * C99 对 {@code %a} 只规定「小数点左边恰好一位十六进制数字」，并且
     * 「这个数字只在数<b>次正规</b>时为 0，其它情况取值由实现定义」——
     * 次正规怎么写属于 <i>unspecified</i>。实测两个 libc 正好相反：
     * <ul>
     *   <li><b>glibc</b>（CI 的 ubuntu）：忠实于机器编码，
     *       {@code 5e-324} → {@code 0x0.0000000000001p-1022}；</li>
     *   <li><b>Apple libc / musl</b>：先规格化，
     *       {@code 5e-324} → {@code 0x1p-1074}。</li>
     * </ul>
     * 这里选 glibc：<b>CI 跑在 ubuntu 上</b>，那里的一致性闸门才是主闸门。
     * 拿 macOS 上的参考实现来比会红，但那是 libc 的分歧不是本实现的分歧——
     * 所以这条用普通断言钉住，不进差分表，免得有人照着 macOS 的参考实现
     * 「修」成规格化写法，把 Linux 上本来是对的行为改错。
     * <p>
     * {@code Double.toHexString} 对次正规给出的正是 glibc 那种写法，
     * 所以实现上不需要额外做什么。
     */
    @Test
    void subnormalQuoteFollowsGlibc() {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        // 次正规：glibc 保留机器编码，不规格化
        assertEquals("0x0.0000000000001p-1022", quote(L, 5e-324));
        assertEquals("0x0.0000000000003p-1022", quote(L, 3 * 5e-324));
        assertEquals("0x0.8p-1022", quote(L, Math.pow(2, -1023)));
        assertEquals("-0x0.0000000000001p-1022", quote(L, -5e-324));
        // 正规：两个 libc 一致，都是规格化写法
        assertEquals("0x1p-1022", quote(L, Math.pow(2, -1022)));
        assertEquals("0x1p+0", quote(L, 1.0));
        assertEquals("0x1p+1", quote(L, 2.0));
        assertEquals("-0x0p+0", quote(L, -0.0));
        L.close();
    }

    /**
     * 带精度的 {@code %a}：<b>就近舍入、平局取偶</b>，且这一条只能靠读 glibc
     * 源码钉，不能靠本机（Apple libc）的参考实现比。
     * <p>
     * 判据在 {@code stdio-common/printf_fphex.c}：
     * <pre>
     * bool more_bits = ((next_digit_value &amp; 7) != 0
     *                   || precision + 1 &lt; numend - numstr);
     * do_round_away = round_away (negative, last_digit_value &amp; 1,
     *                             next_digit_value &gt;= 8, more_bits, mode);
     * </pre>
     * 而 {@code round_away} 在 {@code FE_TONEAREST} 下是
     * {@code half_bit && (last_digit_odd || more_bits)}。拆开看：
     * <ul>
     *   <li>舍弃的第一位 {@code > 8}：{@code more_bits} 为真，进；</li>
     *   <li>{@code < 8}：{@code half_bit} 为假，舍；</li>
     *   <li>恰为 {@code 8} 且后面还有非零位：超过一半，进；</li>
     *   <li>恰为 {@code 8} 且后面全零（<b>平局</b>）：看保留的最后一位的奇偶，
     *       奇则进。{@code prec == 0} 时「保留的最后一位」是小数点左边那一位。</li>
     * </ul>
     * <p>
     * <b>Apple libc 在这一段根本不是就近舍入</b>，错得可以量化：
     * {@code ("%.4a"):format(0x1.500082p0)} 给 {@code 0x1.5000p+0}——舍弃的
     * {@code 0x82} 明明大于半 {@code 0x80} 却舍了下去，而
     * {@code 0x1.500081p0} 反倒进上来（它的判据成了「第二位舍弃数字的奇偶」）。
     * 所以这些用例<b>不进差分表</b>，用普通断言钉住 glibc 的答案。
     */
    @Test
    void hexFloatPrecisionRoundingFollowsGlibc() {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        // 平局：看保留的最后一位的奇偶
        assertEquals("0x2p+0", fmt(L, "%.0a", 1.5));      // 保留的最后一位是整数位 1（奇）→ 进
        assertEquals("0x2p-1", fmt(L, "%.0a", 0.75));     // 0x1.8p-1，同上
        assertEquals("0x1.0p+0", fmt(L, "%.1a", 0x1.08p0)); // 0（偶）→ 舍
        assertEquals("0x1.2p+0", fmt(L, "%.1a", 0x1.18p0)); // 1（奇）→ 进
        assertEquals("0x1.2p+0", fmt(L, "%.1a", 0x1.28p0)); // 2（偶）→ 舍
        assertEquals("0x1.fep+0", fmt(L, "%.2a", 0x1.fe8p0)); // e（偶）→ 舍
        assertEquals("0x2.00p+0", fmt(L, "%.2a", 0x1.ff8p0)); // f（奇）→ 进，且连进位到整数位
        assertEquals("0X2P+0", fmt(L, "%.0A", 1.5));
        // 不到一半 / 超过一半：两边一致，但顺手钉住
        assertEquals("0x1p+1", fmt(L, "%.0a", 2.5));      // 0x1.4：4 < 8
        assertEquals("0x2p+1", fmt(L, "%.0a", 3.5));      // 0x1.c：c > 8
        // 超过一半但第一位恰为 8 —— Apple libc 在这里是错的，glibc 一律进
        assertEquals("0x1.5001p+0", fmt(L, "%.4a", 0x1.500081p0));
        assertEquals("0x1.5001p+0", fmt(L, "%.4a", 0x1.500082p0));
        assertEquals("0x1.7e43dp+996", fmt(L, "%.5a", 1e300));
        // 次正规：glibc 保留机器编码，小数点左边是 0，不规格化
        assertEquals("0x0.0000000000001p-1022", fmt(L, "%a", 5e-324));
        assertEquals("0x0p-1022", fmt(L, "%.0a", 5e-324));
        assertEquals("0x0p-1022", fmt(L, "%.0a", Math.pow(2, -1023)));
        // inf / nan 不参与零填充：0 标志被忽略，宽度改用空格补
        assertEquals("                 inf", fmtExpr(L, "%020.5a", "math.huge"));
        assertEquals("                -inf", fmtExpr(L, "%020.5a", "-math.huge"));
        assertEquals("                -nan", fmtExpr(L, "%020.5a", "0/0"));
        L.close();
    }

    /** 跑 {@code return string.format(spec, x)}；{@code x} 用十六进制字面量传，保证精确 */
    private static String fmt(LuaState L, String spec, double x) {
        return fmtExpr(L, spec, Double.toHexString(x));
    }

    private static String fmtExpr(LuaState L, String spec, String expr) {
        String src = "return string.format('" + spec + "', " + expr + ")";
        int base = L.top;
        L.pushClosure(L.load(src.getBytes(StandardCharsets.ISO_8859_1), "=f"));
        LuaValue err = L.pcall(base, 1);
        assertNull(err, "format script failed: " + src);
        String r = L.stack[base].asString().toJavaString();
        L.top = base;
        return r;
    }

    /** 跑 {@code return string.format('%q', x)}；{@code x} 用十六进制字面量传，保证精确 */
    private static String quote(LuaState L, double x) {
        // Lua 5.4 认十六进制浮点字面量，用它传值不会在十进制往返里掉精度
        String src = "return string.format('%q', " + Double.toHexString(x) + ")";
        int base = L.top;
        L.pushClosure(L.load(src.getBytes(StandardCharsets.ISO_8859_1), "=q"));
        LuaValue err = L.pcall(base, 1);
        assertNull(err, "quote script failed: " + src);
        String r = L.stack[base].asString().toJavaString();
        L.top = base;
        return r;
    }
}
