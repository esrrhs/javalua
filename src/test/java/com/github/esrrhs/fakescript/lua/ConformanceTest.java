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
 * 一致性测试：拿真实的 {@code lua5.4} 二进制当裁判。
 * <p>
 * 这是本项目的主闸门（见 README「怎么证明它真的是 Lua」）。目前解释器还没落地，
 * 所以这里先做两件已经能做的事：
 * <ol>
 *   <li>确认参考二进制可用 —— 否则后面所有差分结论都无从谈起；</li>
 *   <li>把已经移植好的那部分语义（表键规范化、{@code #t} 边界等）<b>用真实
 *       Lua 跑一遍并记录结果</b>，作为可执行的语义契约。</li>
 * </ol>
 * 第 2 条不是空转：它把「我们以为 Lua 是这样」变成「实测 Lua 是这样」，
 * 一旦参考版本变了、或者当初的理解是错的，这里会先红。等解释器在阶段 3 落地，
 * 这些脚本改成两侧各跑一遍、比对 stdout 即可，无需另建框架。
 */
public class ConformanceTest {

    /**
     * 没有参考二进制时整类跳过。
     * <p>
     * 为什么是 skip 而不是 fail：普通的 {@code mvn test}（本地与 build job）
     * 不保证装了 lua5.4，硬失败会让「没装参考实现」变成编译失败，掩盖真正的
     * 回归。真正的一致性校验放在 CI 的 conformance job 里跑——那个 job 会先
     * 从源码构建 lua5.4，构建不出来它自己就红了，不需要这里再守一道。
     */
    @BeforeAll
    public static void requireReference() {
        Assumptions.assumeTrue(ReferenceLua.available(),
                "找不到 lua5.4 参考二进制，跳过一致性测试。设置 LUA54_BIN，"
                        + "或在仓库内构建到 target/lua54/src/lua。查找结果：" + ReferenceLua.describe());
    }

    /** 参考二进制确实是 5.4（5.5 或其他版本会得出错误的「不一致」结论） */
    @Test
    public void referenceBinaryIsLua54() {
        ReferenceLua.Result r = ReferenceLua.run("print(_VERSION)");
        assertEquals(0, r.exitCode);
        assertTrue(r.output.contains("Lua 5.4"), "版本应当是 5.4，实际：" + r.output);
    }

    /**
     * 表键语义：整值浮点规范化、整数键与字符串键分离、{@code #t} 的边界。
     * <p>
     * 这些断言与 {@link LuaTableTest} 里的一一对应，区别在于这里的期望值是
     * <b>真实 Lua 5.4 跑出来的</b>，不是我们替它写的。
     */
    @Test
    public void tableKeySemantics() {
        String script = ""
                + "local t = {}\n"
                + "t[1.0] = 'a'\n"
                + "local k, v = next(t)\n"
                + "print(math.type(k), k, v, #t)\n"
                + "t['1'] = 'b'\n"
                + "print(t[1], t['1'])\n"
                + "local n = 0 for _ in pairs(t) do n = n + 1 end print(n)\n"
                + "local z = {}\n"
                + "z[0.0] = 'zero'\n"
                + "print(#z, z[0], z[1])\n"
                + "local f = {}\n"
                + "f[1.5] = 'x'\n"
                + "local fk = next(f) print(math.type(fk), fk)\n"
                + "local ok, err = pcall(function() local q = {} q[0/0] = 1 end)\n"
                + "print(ok, err ~= nil and tostring(err):find('table index is NaN') ~= nil)\n";
        ReferenceLua.Result r = ReferenceLua.run(script);
        assertEquals(0, r.exitCode, r.toString());
        assertEquals(""
                + "integer\t1\ta\t1\n"
                + "a\tb\n"
                + "2\n"
                + "0\tzero\tnil\n"
                + "float\t1.5\n"
                + "false\ttrue\n", r.output.replaceAll("\r\n", "\n"));
    }

    /**
     * 词法错误信息：两侧逐字比对。
     * <p>
     * 这是第一个真正意义上的<b>差分</b>用例（前面的用例只是记录参考行为）。
     * 做法是把每一段错误源码写成文件交给参考实现跑，再把同一份字节喂给我们的
     * {@link Lexer}，两边比错误信息的完整文本。
     * <p>
     * 之所以值得单独做：错误信息的措辞、{@code near} 后面那段 token 的切法
     * （保留几个字符、要不要带引号、{@code <eof>} 有没有引号）全是上游的
     * 细节约定，凭记忆写必错；而真实项目里 {@code load} 失败时打印的就是
     * 这个字符串，逐字节一致才有意义。
     */
    @Test
    public void lexerErrorMessagesMatchReference() {
        String[] cases = {
            "x = 1a",                 // malformed number
            "x = 0x",
            "x = 1e+",
            "x = \"abc",              // unfinished string，撞 EOZ
            "x = \"abc\n",            // unfinished string，撞换行
            "x = [[abc",              // unfinished long string
            "--[[ x",                 // unfinished long comment
            "x = [==abc]==]",         // invalid long string delimiter
            "x = \"a\\qb\"",          // invalid escape sequence
            "x = \"\\256\"",          // decimal escape too large
            "x = \"\\xZ1\"",          // hexadecimal digit expected
            "x = \"\\x4\"",
            "x = \"\\u{80000000}\"",  // UTF-8 value too large
            "x = \"\\u{41\"",         // missing '}'
            "x = \"\\uA\"",           // missing '{'
            "x = 1\n[[abc\n",         // 跨行的 unfinished long string
        };
        // 路径必须短：参考实现会把过长的源名截成 '...'（LUA_IDSIZE）
        String path = "target/fklex.lua";
        for (String src : cases) {
            byte[] bytes = src.getBytes(StandardCharsets.ISO_8859_1);
            ReferenceLua.FileResult ref = ReferenceLua.runBytes(bytes, path);
            assertEquals(1, ref.result.exitCode,
                    "参考实现应当在加载阶段就失败，用例：" + escape(src));
            int at = ref.result.output.indexOf(ref.path + ":");
            assertTrue(at >= 0, "参考输出里应当带脚本路径，实际：" + ref.result.output);
            String expected = ref.result.output.substring(at).trim();

            String actual;
            try {
                Lexer ls = new Lexer(bytes, ref.path.getBytes(StandardCharsets.UTF_8),
                        new StringPool());
                ls.next();
                while (ls.t.token != Lexer.TK_EOS) {
                    ls.next();
                }
                fail("期望词法错误但没有报错，用例：" + escape(src));
                return;
            } catch (LuaError e) {
                assertNotNull(e.value);
                actual = e.getMessage().trim();
            }
            assertEquals(expected, actual, "用例：" + escape(src));
        }
    }

    /**
     * 反向验证：参考实现能加载的源码，我们的词法层也不能报错。
     * <p>
     * 只查「不报错」这一件事——token 序列的逐条比对要等 parser 落地才能做
     * （没有语法树就没有可比的中介表示）。但这一条已经能挡住最常见的
     * 「词法层把合法写法当成非法」这类回归，比如误拒 {@code 0x1p4}
     * 或把 {@code \z} 后面的内容吃掉。
     */
    @Test
    public void lexerAcceptsWhatReferenceAccepts() {
        String[] cases = {
            "return 1",
            "local x = 0x1p4 return x",
            "local s = [[hi]] return s",
            "--[[ 长注释 ]] return",
            "-- 短注释\nreturn",
            // 注意：不能放 '::l:: goto l' —— 它在参考实现里是死循环，会挂住测试
            "do goto done end ::done:: return",
            "return 1 // 2, 1 >> 2, 1 << 2",
            "return '\\u{4E2D}'",
            "return '\\z   x'",
            "return 'a\\\nb'",
            "local f = function(...) return ... end return f(1)",
            "return [==[a]]b]==]",
            "return 0x10, 0X1P4, .5, 1e2",
            "local t = {[1]=1, x=2} return t",
            "return #'abc' ~= 3",
            "local a <const> = 1 return a",
            "return ('x'):upper()",
        };
        String path = "target/fklexok.lua";
        for (String src : cases) {
            byte[] bytes = src.getBytes(StandardCharsets.ISO_8859_1);
            ReferenceLua.FileResult ref = ReferenceLua.runBytes(bytes, path);
            assertEquals(0, ref.result.exitCode,
                    "参考实现应当能加载，用例：" + escape(src) + " -> " + ref.result.output);

            Lexer ls = new Lexer(bytes, path.getBytes(StandardCharsets.UTF_8), new StringPool());
            try {
                ls.next();
                while (ls.t.token != Lexer.TK_EOS) {
                    ls.next();
                }
            } catch (LuaError e) {
                fail("我们的词法层报错了，但参考实现能加载。用例：" + escape(src)
                        + "，错误：" + e.getMessage());
            }
        }
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** {@code #t} 在数组部分与哈希部分交界处的行为 */
    @Test
    public void lengthBorderSemantics() {
        String script = ""
                + "local z = {}\n"
                + "for i = 1, 8 do z[i] = i end\n"
                + "print(#z)\n"
                + "z[9] = 9\n"
                + "print(#z)\n"
                + "z[4] = nil\n"
                + "print(#z >= 3 and #z <= 9, z[4])\n"
                + "local w = {}\n"
                + "w[2^53] = 1\n"
                + "print(#w, w[2^53])\n";
        ReferenceLua.Result r = ReferenceLua.run(script);
        assertEquals(0, r.exitCode, r.toString());
        String out = r.output.replaceAll("\r\n", "\n");
        assertEquals("8\n9\ntrue\tnil\n0\t1\n", out);
    }
}
