package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 语法错误信息的差分测试。
 * <p>
 * 阶段 1（词法）已经把词法层的报错逐字节对齐过了；这里接着对齐<b>语法层</b>：
 * 「'end' expected」「label 'x' already defined on line N」
 * 「<goto x> at line N jumps into the scope of local 'y'」……
 * <p>
 * 为什么要这么较真：这些字符串是用户直接看到的。而且它们里的<b>行号与名字</b>
 * 是逐条手拼的（{@code luaO_pushfstring}），照手册推必错——只能跑参考实现。
 */
class ParserTest {

    @BeforeAll
    static void requireReference() {
        Assumptions.assumeTrue(ReferenceLua.available(),
                "找不到 lua5.4 参考二进制，跳过语法错误差分：" + ReferenceLua.describe());
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @Test
    void syntaxErrorMessagesMatchReference() {
        String[] cases = {
            "x =",                                   // '<eof>' expected
            "if x then",                             // 'end' expected (to close 'if' at line 1)
            "local 1 = 2",                           // <name> expected
            "f(1 2)",                                // ')' expected (to close '(' at line 1)
            "::a::\n::a::",                          // label 'a' already defined
            "goto nowhere",                          // no visible label
            "break",                                 // break outside loop
            "goto l\nlocal x = 1\n::l::",            // jumps into the scope of local
            "local a <foo> = 1",                     // unknown attribute
            "local a <close>, b <close> = nil, nil", // multiple to-be-closed
            "local x <const> = 1\nx = 2",            // assign to const
            "function 1() end",                      // <name> expected
            "x[1 = 2",                               // ']' expected
            "a = 1 +",                               // unexpected symbol
            "t = {1 2}",                             // '}' expected
            "while x y end",                         // 'do' expected
            "repeat x = 1",                          // 'until' expected
            "#",                                     // unexpected symbol
            "(1",                                    // ')' expected
            "a.b.1 = 2",                             // <name> expected
            "for i = 1 do end",                      // ',' or 'in'... 实际是 fornum 缺上限
            "for i 1, 10 do end",                    // '=' or 'in' expected
            "local x = function() return 1",         // 'end' expected
            "x = 1 = 2",                             // syntax error
            "x() = 1",                               // syntax error
            "return return",                         // unexpected symbol
            "end",                                   // unexpected symbol near 'end'
            "do do end",                             // 'end' expected (to close 'do' at line 1)
            "for i = 1, 2, 3, 4 do end",             // 'do' expected
            "local function",                        // <name> expected
            "a::b",                                  // '=' expected? 实际是 syntax error
            "x = ...",                               // 合法（主函数是 vararg）
        };
        // 路径必须短：参考实现会把过长的源名截成 '...'（LUA_IDSIZE）
        String path = "target/fkparse.lua";
        int checked = 0;
        for (String src : cases) {
            byte[] bytes = src.getBytes(StandardCharsets.ISO_8859_1);
            ReferenceLua.FileResult ref = ReferenceLua.runBytes(bytes, path);
            String label = "用例：" + escape(src);
            if (ref.result.exitCode == 0) {
                // 参考实现认为合法：我们也不能报错
                Parser.parseFile(bytes, ("@" + path).getBytes(StandardCharsets.UTF_8), new StringPool());
                checked++;
                continue;
            }
            assertEquals(1, ref.result.exitCode, label);
            if (ref.result.output.contains("stack traceback")) {
                // 参考实现是<b>运行时</b>才失败的（比如给 <close> 变量赋了数字），
                // 这类用例比不了语法消息——本测试只关心加载阶段的报错
                checked++;
                continue;
            }
            int at = ref.result.output.indexOf(ref.path + ":");
            assertTrue(at >= 0, "参考输出里应当带脚本路径，实际：" + ref.result.output);
            String expected = ref.result.output.substring(at).trim();

            String actual;
            try {
                Parser.parseFile(bytes, ("@" + path).getBytes(StandardCharsets.UTF_8), new StringPool());
                fail("期望语法错误但没有报错，" + label);
                return;
            } catch (LuaError e) {
                assertNotNull(e.value, label);
                actual = e.getMessage().trim();
            }
            assertEquals(expected, actual, label);
            checked++;
        }
        assertEquals(cases.length, checked);
    }

    /**
     * 编译期常量（{@code <const>}）不占寄存器。
     * <p>
     * 这条规则从源码上看不出来（{@code nvarstack} 要跳过 RDKCTC 的变量去数），
     * 写错了现象是「后面的局部变量莫名其妙挪了一个寄存器」。
     */
    @Test
    void compileTimeConstantDoesNotTakeARegister() {
        Proto p = Parser.parse("local a <const> = 1\nlocal b = 2\nreturn a + b\n"
                .getBytes(StandardCharsets.UTF_8),
                "t".getBytes(StandardCharsets.UTF_8), new StringPool());
        assertEquals(0, p.numparams);
        // a 不占寄存器，b 在 r0；否则 b 会在 r1 而 maxstacksize 也更大
        assertEquals(2, p.maxstacksize, "maxstacksize 应当只用到 r0/r1");
    }

    /** {@code lauxlib.c: skipcomment}：文件首行的 {@code #!...} 要跳过，且行号不变 */
    @Test
    void shebangLineIsSkippedAndKeepsLineNumbers() {
        byte[] src = "#!/usr/bin/env lua\nlocal x = 1\nx()\n".getBytes(StandardCharsets.UTF_8);
        byte[] stripped = Parser.skipFilePreamble(src);
        assertEquals('\n', stripped[0], "被跳过的行要补一个换行");
        assertEquals("\nlocal x = 1\nx()\n", new String(stripped, StandardCharsets.UTF_8));

        Proto p = Parser.parseFile(src, "@t".getBytes(StandardCharsets.UTF_8), new StringPool());
        assertEquals(3, p.getFuncline(p.sizecode - 1), "x() 依然在第 3 行");
    }

    /**
     * 语法 / 词法错误矩阵（172 行）：把 {@code lparser.c} 与 {@code llex.c} 里
     * <b>每一条</b>报错字符串都走到一遍。
     * <p>
     * 枚举办法不是凭印象挑用例，而是<b>扫 C 源码的报错站点</b>：
     * {@code error_expected} / {@code check_match} 的两种形态、
     * {@code errorlimit}（局部变量、upvalue、构造器项）、
     * {@code attempt to assign to const variable}、
     * {@code jumpscopeerror} / {@code undefgoto} / 重复标签、
     * {@code <name> or '...' expected}、{@code function arguments expected}、
     * {@code unexpected symbol}、{@code unknown attribute}、
     * {@code multiple to-be-closed variables}、
     * {@code cannot use '...' outside a vararg function}、
     * {@code '=' or 'in' expected}、{@code control structure too long}，
     * 以及词法层的 unfinished / malformed / hexadecimal digit / missing '{}' /
     * UTF-8 value too large / decimal escape too large / invalid escape /
     * invalid long string delimiter。
     * <ul>
     *   <li><b>{@code <name>} 与 {@code <eof>} 是<b>不带引号</b>的。</b>
     *       {@code luaX_token2str} 对 {@code token >= TK_EOS} 直接返回裸串，
     *       对保留字与单字符才加 {@code '…'}。所以消息里既有
     *       {@code <name> expected} 也有 {@code 'end' expected}，
     *       而 {@code near} 后面那个 token 走的是 {@code txtToken}
     *       （字符串与数字取<b>原文</b>，于是有 {@code near ''\x''} 这种嵌套引号）。</li>
     *   <li><b>C 栈溢出是「返回」不是「抛」。</b>深度嵌套的
     *       {@code luaE_incCstack} 报错会被 {@code luaD_protectedparser} 兜住，
     *       所以 {@code load} 给 {@code nil + "C stack overflow"}；
     *       而 {@code load} 自己的参数校验（{@code bad argument #2}）才是抛。
     *       矩阵里一律走 {@code pcall} 才能把这两种分开。</li>
     *   <li><b>{@code control structure too long}</b> 要循环体超过
     *       {@code MAXARG_Bx}（262143）条指令才触发，所以那条用例是<b>现生成</b>
     *       的 1MB 源码 —— 参考实现 0.02 秒、本实现 0.6 秒，可以进套件。</li>
     * </ul>
     * 敏感性是量过的：把 {@code tokenName} 改成一律加引号红 11 行；把
     * {@code checkMatch} 的同行捷径去掉红 13 行；把 {@code errorLimit} 的
     * {@code "main function"} 改成 {@code "main chunk"} 红 1 行。
     */
    @Test
    void syntaxErrorMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 语法错误矩阵：C 栈溢出是「抛」出来的（load 不返回 nil+msg），所以一律走 pcall
                local function p(tag, src, name)
                    local ok, f, e = pcall(load, src, name or "=c")
                    if not ok then print(tag, "R", f)
                    elseif f ~= nil then print(tag, "OK")
                    else print(tag, e) end
                end
                local function q(tag, src)
                    local ok, f, e = pcall(load, src)
                    if not ok then print(tag, "R", f)
                    elseif f ~= nil then print(tag, "OK")
                    else print(tag, e) end
                end
                -- A/B/D/E/F/O：统一用 =c 做 chunkname
                p("a01", "if x then")
                p("a02", "while x do")
                p("a03", "for i=1,2 do")
                p("a04", "for k,v in t do")
                p("a05", "repeat")
                p("a06", "do")
                p("a07", "function f()")
                p("a08", "local function f()")
                p("a09", "f(1")
                p("a10", "t[1")
                p("a11", "t = {1")
                p("a12", "f{1")
                p("a13", "if x")
                p("a14", "while x")
                p("a15", "function f")
                p("a16", "function f(1)")
                p("a17", "function f(a")
                p("a18", "function f(a,")
                p("a19", "local = 1")
                p("a20", "local ,")
                p("a21", "local x,")
                p("a22", "for i 1,2 do end")
                p("a23", "goto")
                p("a24", "::")
                p("a25", "::1::")
                p("a26", "local function")
                p("a27", "local x =")
                p("a28", "x = 1 +")
                p("a29", "t.x")
                p("a30", "f()")
                p("a31", "local x <close>")
                p("a32", "local x <")
                p("a33", "local x <const")
                p("a34", "x =")
                p("a35", "return 1,")
                p("a36", "t = {1,")
                p("a37", "t = {[1]")
                p("a38", "t = {x")
                p("a39", "f(a,")
                p("a40", "(1")
                p("b01", "if x then\\n")
                p("b02", "do\\n")
                p("b03", "while x do\\n")
                p("b04", "for i=1,2 do\\n")
                p("b05", "repeat\\n")
                p("b06", "function f()\\n")
                p("b07", "local function f()\\n")
                p("b08", "f(\\n")
                p("b09", "t = {\\n")
                p("b10", "t[\\n")
                p("b11", "if x then\\nif y then\\n")
                p("b12", "do\\ndo\\n")
                p("b13", "t = {\\nx=1,\\n")
                p("b14", "if x\\nthen\\n")
                p("b15", "f(\\n1,\\n")
                p("b16", "local f = function()\\n")
                p("b17", "while x do\\nbreak\\n")
                p("b18", "if x then\\n\\n\\n")
                p("b19", "t = {1,\\n")
                p("b20", "if x then\\nelse\\n")
                p("b21", "repeat\\nuntil\\n")
                p("b22", "local function f(\\n")
                p("d01", "local x <const> = 1\\nx = 2")
                p("d02", "local x <const> = 1\\nx, y = 2, 3")
                p("d03", "local x <const> = 1\\nlocal function f() x = 2 end")
                p("d04", "local x <const> = 1\\nx.y = 2")
                p("d05", "local x <const> = 1\\nprint(x)")
                p("d06", "local a, b <const> = 1, 2\\nb = 3")
                p("d07", "local x <foo> = nil")
                p("d08", "local x <CONST> = nil")
                p("d09", "local x <close>, y <close> = nil, nil")
                p("d10", "local x <close> = nil\\nlocal y <close> = nil")
                p("d11", "local x <const> <close> = nil")
                p("d12", "local x <close> = nil")
                p("d13", "local x <const> = 1\\nlocal y = x\\nlocal x = 2")
                p("e01", "goto L")
                p("e02", "break")
                p("e03", "do break end")
                p("e04", "while true do break end\\nbreak")
                p("e05", "goto L\\n::L::")
                p("e06", "::L::\\ngoto L")
                p("e07", "do ::L:: end\\ngoto L")
                p("e08", "goto L\\ndo\\nlocal x = 1\\n::L::\\nend")
                p("e09", "::L::\\n::L::")
                p("e10", "for i=1,2 do goto cont end\\n::cont::")
                p("e11", "while true do goto x end")
                p("e12", "do goto L ::L:: end")
                p("e13", "for i=1,2 do\\ngoto L\\ndo local y = 1 ::L:: end\\nend")
                p("e14", "do\\ngoto L\\nlocal x = 1\\n::L::\\nend")
                p("e15", "for i=1,2 do\\ngoto cont\\nlocal y = 1\\n::cont::\\nend")
                p("e16", "repeat\\ngoto L\\nlocal z = 1\\n::L::\\nuntil true")
                p("e17", "::break::")
                p("f01", "function f(1) end")
                p("f02", "function f(a, 1) end")
                p("f03", "function g(x) return ... end")
                p("f04", "local function f() return ... end")
                p("f05", "1 = 2")
                p("f06", "f() = 1")
                p("f07", "x + 1 = 2")
                p("f08", "return 1, 2 = 3")
                p("f09", "local x = 1\\nx() = 2")
                p("f10", "print(...)")
                p("f11", "return 1 return 2")
                p("f12", "do return 1 end return 2")
                p("f13", "break\\n")
                p("f14", "::L:: goto L")
                p("o01", "local x = 1e")
                p("o02", "local x = 0x")
                p("o03", "local x = 0xg")
                p("o04", "local x = 'abc")
                p("o05", "local x = \\"abc\\n")
                p("o06", "local x = [[abc")
                p("o07", "local x = --[[abc")
                p("o08", "local x = '\\\\x'")
                p("o09", "local x = '\\\\u'")
                p("o10", "local x = '\\\\u{110000}'")
                p("o11", "local x = '\\\\u{41'")
                p("o12", "local x = '\\\\300'")
                p("o13", "local x = '\\\\q'")
                p("o14", "local x = [==[abc]=]")
                p("o15", "local x = @")
                p("o16", "local x = $")
                p("o17", "local x = 1e999e5")
                p("o18", "local x = .")
                p("o19", "local x = 0x1p")
                p("o20", "local x = 0777e")
                p("o21", "local x = `")
                p("o22", "local x = '\\\\u{}'")
                p("o23", "local x = 1 ..")
                p("o24", "local x = '\\\\u{7FFFFFFF}'")
                p("o25", "local x = '\\\\x1'")
                p("o26", "local x = '\\\\xZZ'")
                p("o27", "local x = \\\\1")
                p("o28", "local x = \\\\127")
                p("o29", "local x = '\\\\u{41}'")
                p("o30", "local x = [[]]")
                
                -- C：errorlimit（超限）
                do
                    local s = "local "
                    for i = 1, 201 do s = s .. "v" .. i .. "," end
                    s = s .. "x"
                    p("c01", s)
                end
                do
                    local s = "local function f()\\nlocal "
                    for i = 1, 201 do s = s .. "v" .. i .. "," end
                    s = s .. "x\\nend"
                    p("c02", s)
                end
                do
                    -- 外层 200 个局部（a1..a199 + ax），中层 57 个（b1..b56 + bx）
                    -- 最内层引用全部 = 257 个 upvalue > 255
                    local s = "local function f()\\nlocal "
                    for i = 1, 199 do s = s .. "a" .. i .. "," end
                    s = s .. "ax\\nreturn function()\\nlocal "
                    for i = 1, 56 do s = s .. "b" .. i .. "," end
                    s = s .. "bx\\nreturn function() return "
                    for i = 1, 199 do s = s .. "a" .. i .. "+" end
                    s = s .. "ax+"
                    for i = 1, 56 do s = s .. "b" .. i .. "+" end
                    s = s .. "bx end\\nend\\nend"
                    p("c03", s)
                end
                do
                    local n = 262150
                    p("c04", "for i=1,1 do " .. string.rep("x=1;", n) .. " end")
                end
                
                -- N：嵌套深度（enterlevel / C 栈）
                do
                    local d = 201
                    p("n01", string.rep("(", d) .. "1" .. string.rep(")", d))
                    p("n02", string.rep("{", d) .. string.rep("}", d))
                    p("n03", "return " .. string.rep("-", d) .. "1")
                    p("n04", "return " .. string.rep("not ", d) .. "x")
                    p("n05", "return " .. string.rep("(", 50) .. "1" .. string.rep(")", 50))
                    p("n06", string.rep("(", 3000) .. "1" .. string.rep(")", 3000))
                    p("n07", "return " .. string.rep("{", 201) .. string.rep("}", 201))
                end
                
                -- P：chunkname 的形状
                q("p01", "x=")
                q("p02", "x=\\n")
                q("p03", "x= " .. string.rep("1 ", 40))
                q("p04", "local x = 1\\nx =")
                q("p05", "\\239\\187\\191x=")
                q("p06", "#!/usr/bin/lua\\nx=")
                q("p07", "#!/usr/bin/lua\\n")
                p("p08", "x=", "=c")
                p("p09", "x=", "@f.lua")
                p("p10", "x=", "f.lua")
                p("p11", "x=", "")
                p("p12", "x=", "=a\\nb")
                p("p13", "x=", string.rep("z", 100))
                p("p14", "x=", true)
                p("p15", "x=", 42)
                p("p16", "x=", {})
                do local ok, f = pcall(load, 1) print("p17", ok, type(f)) end
                do local ok, f = pcall(load, nil) print("p18", ok, type(f)) end
                do local ok, f = pcall(load, "return 1", "=c", "b") print("p19", ok, type(f)) end
                do local ok, f = pcall(load, "return 1", "=c", "t", {}) print("p20", ok, type(f)) end
                do local ok, f = pcall(load, "return 1", "=c", "x") print("p21", ok, type(f)) end
                
                -- Q：源码里的 \\0
                q("q01", "x\\0= 1")
                q("q02", "x = 1 --\\0y")
                p("q03", "x\\0= 1", "=c")
                """);
    }
}
