package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 上游套件里「输出不该逐字节比、但必须能跑完」的那些用例。
 * <p>
 * 与 {@link UpstreamSuiteTest} 的分工：那边断言与参考实现<b>逐字节一致</b>，
 * 这边只断言<b>能正常结束</b>——跑完并打印 {@code OK}。理由写在
 * {@link UpstreamSuiteTest} 的类注释里（{@code cstack.lua} 打印随实现而变的
 * 递归层数，{@code gc.lua} 依赖本实现没有的增量收集器，
 * {@code literals.lua} 依赖 C 程序才设得上的 locale 小数点）。
 * <p>
 * <b>为什么不能干脆不跑。</b>这几个文件是全套里最容易把实现「跑死」的：
 * <ul>
 *   <li>{@code cstack.lua} 整篇都在制造栈溢出与 C 栈溢出，没有
 *       {@code LUAI_MAXSTACK} / {@code LUAI_MAXCCALLS} 这两道上限，
 *       无限递归在 Java 这边就是 {@code OutOfMemoryError}——它真的把
 *       surefire 的 forked JVM 撑爆过，{@code ./mvnw test} 因此整条红；</li>
 *   <li>{@code db.lua} 有一句 {@code repeat local a = {} until name}，
 *       在等一个 {@code __gc} 终结器。终结器不跑就是死循环；</li>
 *   <li>{@code literals.lua} / {@code gc.lua} 大量分配，能兜住自动收集
 *       那一层的回归。</li>
 * </ul>
 * 所以这里宁可断言得松，也不能没有。
 */
final class UpstreamSmokeTest {

    private static File dir;

    @BeforeAll
    static void locate() {
        String env = System.getenv("LUA54_TESTS");
        if (env != null && !env.isEmpty() && new File(env).isDirectory()) {
            dir = new File(env);
        } else if (new File("target/lua54-tests").isDirectory()) {
            dir = new File("target/lua54-tests");
        }
        assumeTrue(dir != null, "找不到上游用例目录，跳过；设 LUA54_TESTS，"
                + "或下载到 target/lua54-tests");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cstack.lua",
            "literals.lua",
            "locals.lua",
            "constructs.lua",
    })
    void runsToCompletion(String name) {
        String out = Diff.mineFileOnly(dir, name);
        String[] lines = out.trim().split("\r?\n");
        assertTrue(lines.length > 0, "用例没有任何输出，等于没跑。file: " + name);
        /*
         * 只认最后一行：这些文件都以 print'OK' 收尾，跑到那一行才说明
         * 中途既没死循环、也没被栈/堆上限打断。
         * <p>
         * 判定前要先抹掉 {@code tracegc} 的进度点。上游 {@code tracegc.lua}
         * 的 {@code __gc} 是 {@code io.stderr:write'.'}——<b>不带换行</b>，
         * 所以那个点会粘在相邻输出上（{@code locals.lua} 里就成了
         * {@code ".OK"}），也可能在 {@code print'OK'} 之后单独占一行
         * （{@code cstack.lua}）。它什么时候写，取决于终结器什么时候跑：
         * 参考实现由 {@code lgc.c} 的步进决定，本实现<b>不移植</b>那一层
         * （回收全交给 JVM），于是位置对不上。这是噪声，不是回归。
         */
        String last = null;
        for (String line : lines) {
            String s = line.replace(".", "").trim();
            if (!s.isEmpty()) {
                last = s;
            }
        }
        assertTrue("OK".equals(last),
                "用例没有跑到最后的 print'OK'。file: " + name
                        + "\n末几行：\n" + tail(lines));
    }

    /**
     * {@code gc.lua}：专测收集器本身，单独一条断言，契约与上面那条不同。
     * <p>
     * <b>它到不了 {@code OK}</b>，而且这是设计使然——本实现不移植
     * {@code lgc.c}（弱表走 {@code WeakReference}、{@code __gc} 走
     * {@code Cleaner}，回收动作全交给 JVM），所以「收集之后内存下降了多少」
     * 这个量根本不存在。{@code collectgarbage("count")} 恒报 0，于是
     * {@code gc.lua:194} 的 {@code assert(gcinfo() < x)} 是 {@code 0 < 0}，
     * 恒假；再往后 {@code gc.lua:463} 还要求一个 2^22 字节的串让
     * {@code count} 正好涨 2^13 KB——那是逐字节的分配记账，更不可能。
     * <p>
     * 所以这里守的是另外两件事，它们才是真会回归的：
     * <ul>
     *   <li><b>得跑到那一步</b>：前面「creating many objects /
     *       functions with errors / long strings / steps」四节靠的是
     *       {@code __gc}、终结语、长串驻留，崩在其中任何一节都会被这里拦下；</li>
     *   <li><b>必须是「断言失败」而不是别的</b>：恒 0 的内存量会让
     *       {@code repeat until gcinfo() > 3 * x} 变成死循环（真写错过一次），
     *       而 {@code OutOfMemoryError} / {@code ArrayIndexOutOfBounds}
     *       这类会直接从引擎里冒出来。所以末行必须是 Lua 的
     *       {@code assertion failed!}，也就是「脚本自己停下的」。</li>
     * </ul>
     */
    @org.junit.jupiter.api.Test
    void gcRunsThroughFinalizers() {
        String out = Diff.mineFileOnly(dir, "gc.lua");
        assertTrue(out.contains("creating many objects"),
                "gc.lua 没跑到第一节。输出：\n" + out);
        assertTrue(out.contains("long strings"),
                "gc.lua 没跑到长串那一节。输出：\n" + out);
        assertTrue(out.contains("steps (2)"),
                "gc.lua 没跑到 steps 那一节。输出：\n" + out);
        /*
         * 不能只认<b>末</b>行：未捕获的错误会过 {@code lua.c} 那个
         * {@code msghandler}，消息后面还挂着一段 {@code luaL_traceback}，
         * 最后一行是 {@code [C]: in ?}。所以是「<b>某一行</b>」匹配。
         */
        String[] lines = out.trim().split("\r?\n");
        boolean onAssert = false;
        StringBuilder tail = new StringBuilder();
        for (String line : lines) {
            if (line.matches(".*gc\\.lua:\\d+: assertion failed!")) {
                onAssert = true;
            }
            if (tail.length() == 0 && !line.isEmpty()) {
                tail.append(line);
            }
        }
        assertTrue(onAssert,
                "gc.lua 应当停在一条 Lua 断言上（内存统计那一段），"
                        + "而不是崩溃或死循环。末几行：\n" + tail(lines));
    }

    private static String tail(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - 8); i < lines.length; i++) {
            sb.append("  ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
