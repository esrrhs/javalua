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
                Parser.parseFile(bytes, path.getBytes(StandardCharsets.UTF_8), new StringPool());
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
                Parser.parseFile(bytes, path.getBytes(StandardCharsets.UTF_8), new StringPool());
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

        Proto p = Parser.parseFile(src, "t".getBytes(StandardCharsets.UTF_8), new StringPool());
        assertEquals(3, p.getFuncline(p.sizecode - 1), "x() 依然在第 3 行");
    }
}
