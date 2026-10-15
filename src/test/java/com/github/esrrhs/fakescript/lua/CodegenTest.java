package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 字节码差分测试 —— 编译器的主闸门。
 * <p>
 * 逐个脚本比较「参考实现编出来的字节码」与「我们编出来的字节码」，
 * <b>逐字段</b>比：opcode、A/B/C/k/Bx/sBx/Ax/sB/sC/sJ、行号、常量表、
 * upvalue 表、嵌套函数。
 * <p>
 * 为什么这道闸门值得单独建：parser 与 codegen 的错误绝大多数是「编出来能跑、
 * 但多/少了一条指令、常量表多了一项、跳转差一格」。这类偏差在运行时
 * 往往不报错，直到某天踩到具体分支才炸。逐字段比对能当场抓住。
 */
class CodegenTest {

    @BeforeAll
    static void requireDumper() {
        Assumptions.assumeTrue(ReferenceLua.findFkdump() != null,
                "找不到 fkdump 转储器，跳过字节码差分（设 FKDUMP_BIN 指向它）");
    }

    private static final String[] SCRIPTS = {
        /* 0 */ "local x = 1\n",
        /* 1 */ "local x = 1\nprint(x + 2)\n",
        /* 2 */ "local y <const> = 3\nprint(y)\n",
        /* 3 */ "if a then b = 1 else b = 2 end\n",
        /* 4 */ "if a then b = 1 elseif c then b = 2 elseif d then b = 3 else b = 4 end\n",
        /* 5 */ "while a do b() end\n",
        /* 6 */ "while a do if b then break end c() end\n",
        /* 7 */ "for i = 1, 10 do print(i) end\n",
        /* 8 */ "for i = 10, 1, -1 do print(i) end\n",
        /* 9 */ "for k, v in pairs(t) do print(k, v) end\n",
        /* 10 */ "for a, b, c in f do g(a, b, c) end\n",
        /* 11 */ "local t = {1, 2, 3}\n",
        /* 12 */ "local t = {1, 2, 3, x = 4, [5] = 6, ['y'] = 7}\n",
        /* 13 */ "local t = {f(), g()}\n",
        /* 14 */ "function f(a, b, ...) return a + b, ... end\n",
        /* 15 */ "local function g() end\n",
        /* 16 */ "repeat x = x + 1 until x > 10\n",
        /* 17 */ "do goto done end\n::done::\n",
        /* 18 */ "print(#s, -x, not y, ~z)\n",
        /* 19 */ "local s = 'a' .. 'b' .. 'c'\n",
        /* 20 */ "return\n",
        /* 21 */ "return 1, 2\n",
        /* 22 */ "return f()\n",
        /* 23 */ "local x = a and b or c\n",
        /* 24 */ "a.b.c = 1\n",
        /* 25 */ "a:m(1)\n",
        /* 26 */ "local x = 1 + 2 * 3 // 4 % 5\n",
        /* 27 */ "local x = 2 ^ 3 ^ 4\n",
        /* 28 */ "x = y\n",
        /* 29 */ "local a, b = 1\n",
        /* 30 */ "local a, b = f()\n",
        /* 31 */ "local x = 1\nlocal function f() return x end\nreturn f\n",
        /* 32 */ "local x <close> = nil\n",
        /* 33 */ "local t = {}\nt[1] = 2\nt.x = t.y\n",
        /* 34 */ "a[b], a = 1, 2\n",
        /* 35 */ "print(1 == 2, 3 ~= 4, 5 < 6, 7 <= 8, 9 > 10, 11 >= 12)\n",
        /* 36 */ "local x = 1.5\nlocal y = 0x10\nlocal z = 1e100\n",
        /* 37 */ "local x = -2 ^ 2\n",
        /* 38 */ "if a then return end\nprint(1)\n",
        /* 39 */ "local f = function(...) return ... end\n",
        /* 40 */ "do end\n",
        /* 41 */ "local x = 100000\nlocal y = 100000.0\n",
        /* 42 */ "for i = 1, 10 do\n  for j = 1, 10 do\n    print(i, j)\n  end\nend\n",
        /* 43 */ "local t = {[1] = 1, [2] = 2, [3] = 3}\n",
        /* 44 */ "print(('x'):rep(3))\n",
        /* 45 */ "local n = 0\nwhile n < 10 do n = n + 1 end\nreturn n\n",
        /* 46 */ "local a, b, c = 1, 2, 3\nreturn a, b, c\n",
        /* 47 */ "for i = 1, 10 do if x then break end end\n",
        /* 48 */ "local s = 'a\\0b'\n",
        /* 49 */ "return ...\n",
    };

    @Test
    void bytecodeMatchesReference() {
        int checked = 0;
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = SCRIPTS[i];
            byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
            String ref = ReferenceLua.dumpProto(bytes, "target/fkcodegen/t.lua");
            assertNotNull(ref, "fkdump 没有输出");
            if (ref.startsWith("LOADERR")) {
                throw new AssertionError("脚本 #" + i + " 参考实现就编译不过：" + ref + "\n" + src);
            }
            Proto ours;
            try {
                ours = Parser.parse(bytes, "t".getBytes(StandardCharsets.UTF_8), new StringPool());
            } catch (RuntimeException e) {
                throw new AssertionError("脚本 #" + i + " 我们这边编译失败：" + e + "\n" + src, e);
            }
            String got = ProtoDump.dump(ours);
            assertEquals(normalize(ref), normalize(got), "脚本 #" + i + "\n" + src + "\n" + firstDiff(normalize(ref), normalize(got)));
            checked++;
        }
        assertEquals(SCRIPTS.length, checked);
    }

    /**
     * 用上游自带的测试套件（{@code lua-5.4.x/tests/*.lua}）当语料，逐文件比对字节码。
     * <p>
     * 这批脚本是 Lua 作者自己写的、专门用来压边界的（goto 跳出作用域、
     * 多重赋值的求值顺序、const/close 属性、vararg、超长常量表……），
     * 比手写几十条用例强得多。<b>只编译不运行</b>——运行要等 VM 落地。
     * <p>
     * 目录由环境变量 {@code LUA54_TESTS} 指定，默认 {@code target/lua54-tests}；
     * 不存在则跳过（CI 的 conformance job 会下载并解压到那里）。
     */
    @Test
    void upstreamTestSuiteCompilesIdentically() {
        String dir = System.getenv("LUA54_TESTS");
        if (dir == null || dir.isEmpty()) {
            dir = "target/lua54-tests";
        }
        java.io.File[] files = new java.io.File(dir).listFiles((d, n) -> n.endsWith(".lua"));
        Assumptions.assumeTrue(files != null && files.length > 0,
                "没有上游测试套件语料，跳过：" + dir);
        java.util.Arrays.sort(files);

        int checked = 0;
        for (java.io.File file : files) {
            byte[] bytes;
            try {
                bytes = java.nio.file.Files.readAllBytes(file.toPath());
            } catch (java.io.IOException e) {
                throw new AssertionError("读不了 " + file, e);
            }
            String ref = ReferenceLua.dumpProto(bytes, "target/fkcodegen/t.lua");
            assertNotNull(ref, "fkdump 没有输出");
            if (ref.startsWith("LOADERR")) {
                throw new AssertionError("参考实现就编译不过：" + ref + "（" + file + "）");
            }
            Proto ours;
            try {
                ours = Parser.parseFile(bytes, "t".getBytes(StandardCharsets.UTF_8),
                        new StringPool());
            } catch (RuntimeException e) {
                throw new AssertionError("我们这边编译失败：" + e + "（" + file + "）", e);
            }
            assertEquals(normalize(ref), normalize(ProtoDump.dump(ours)), "文件 " + file.getName() + "\n" + firstDiff(normalize(ref), normalize(ProtoDump.dump(ours))));
            checked++;
        }
        assertEquals(files.length, checked);
    }

    /**
     * 失败时只报第一处不同及其上下文。
     * <p>
     * 整份转储动辄上千行，直接丢进断言信息里等于什么都没说；真正要看的只有
     * 第一个分歧点。
     */
    private static String firstDiff(String expected, String actual) {
        String[] a = expected.split("\n", -1);
        String[] b = actual.split("\n", -1);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            String x = i < a.length ? a[i] : "<缺行>";
            String y = i < b.length ? b[i] : "<缺行>";
            if (!x.equals(y)) {
                StringBuilder sb = new StringBuilder();
                sb.append("第 ").append(i + 1).append(" 行不同（共 ref=").append(a.length)
                        .append(" ours=").append(b.length).append("）\n");
                for (int j = Math.max(0, i - 3); j < Math.min(n, i + 4); j++) {
                    String p = j < a.length ? a[j] : "<缺行>";
                    String q = j < b.length ? b[j] : "<缺行>";
                    sb.append(p.equals(q) ? "  " : "! ").append(j + 1)
                            .append("  ref=").append(p).append("\n");
                    if (!p.equals(q)) {
                        sb.append("     ours=").append(q).append("\n");
                    }
                }
                return sb.toString();
            }
        }
        return "<无差异>";
    }

    private static String normalize(String s) {
        String out = s.replace("\r\n", "\n");
        while (out.endsWith("\n")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
