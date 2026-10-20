package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 上游自带的一致性套件 —— {@code lua-5.4.x/tests/*.lua} 原样跑一遍。
 * <p>
 * 这是 README 里四道闸门的第一道，也是最强的一道：上游那套用例比我们手写的任何
 * 一段脚本都刁钻，而且它是「Lua 到底是什么」的<b>定义方</b>写的。
 * <p>
 * 用例目录的查找顺序：
 * <ol>
 *   <li>环境变量 {@code LUA54_TESTS}；</li>
 *   <li>仓库内 {@code target/lua54-tests}（CI 下载到那里）。</li>
 * </ol>
 * 找不到就整类跳过——没有参考二进制时 {@link Diff} 已经会跳过，而这里多一个
 * 「连用例文件都没有」的跳过条件，是为了让本地 {@code ./mvnw clean test} 不需要
 * 先联网下载 136 KB 的测试包也能绿。
 * <p>
 * <b>不是所有用例都进了这张表。</b>挑进来的是「独立可跑 + 输出确定」的那些：
 * <ul>
 *   <li>{@code all.lua} 是总驱动，它要 {@code ltests.c} 编出来的 {@code T} 模块、
 *       还要写 {@code time.txt}、打印内存与耗时，天生不可比；</li>
 *   <li>{@code main.lua} / {@code files.lua} / {@code attrib.lua} / {@code big.lua}
 *       依赖 {@code all.lua} 建的全局环境，单跑就失败；</li>
 *   <li>{@code math.lua} 与 {@code sort.lua} 打印随机种子、随机数范围与耗时，
 *       两次运行都不一样；</li>
 *   <li>{@code bwcoercion.lua} 单跑没有输出（它被 {@code bitwise.lua}
 *       {@code require}，内容在那儿被覆盖）；</li>
 *   <li>{@code heavy.lua} 要跑好几分钟。</li>
 * </ul>
 * 剩下来的那些里，{@code api.lua} 与 {@code code.lua} 在没有 {@code T} 时会自己
 * 跳过 C API 那几段，但剩下部分照跑，所以仍然有价值。
 * <p>
 * <b>还有一类是「能跑但输出不该逐字节比」的</b>，同样不进这张表，理由是
 * 它们打印的本来就是随实现／随机而变的东西：
 * <ul>
 *   <li>{@code cstack.lua}：每个用例都 {@code print("final count: ", count)}。
 *       这个数取决于「一层嵌套吃掉多少 C 栈」，Java 与 C 必然不同，上游自己也
 *       只打印不断言。它仍然值得跑（能兜住栈上限回归），但由
 *       {@link UpstreamSmokeTest} 只断言「跑完并打印 OK」。</li>
 *   <li>{@code gc.lua}：整个文件在测增量收集器的参数（{@code setpause} /
 *       {@code setstepmul} / {@code count} / {@code step}）、弱表、应急 GC。
 *       本实现把内存管理交给 JVM，没有这一层可对标的语义。</li>
 *   <li>{@code literals.lua}：末尾那段 decimal point locale 要
 *       {@code os.setlocale("pt_BR")} 真的生效。{@code pt_BR} 设进 libc 是
 *       C 程序才做得到的事，所以它<b>只在 native 那一路进表</b>——有 shim 时
 *       两边逐字节一致（设得上就都走逗号分支，机器上没装 {@code pt_BR} 就都
 *       打印 skipped），而 {@code -Djavalua.nonative} 那一路设不上 locale，
 *       参考实现照样设得上，输出天然多一行。这一路仍由
 *       {@link UpstreamSmokeTest} 跑它，兜「大量分配会不会把自动收集跑死」。</li>
 *   <li>{@code constructs.lua}：第 319 行是
 *       {@code _ENV.GLOB1 = math.random(0, 1)}，紧跟着就
 *       {@code print('testing short-circuit optimizations (' .. _ENV.GLOB1 .. ')')}。
 *       Lua 5.4 的种子取自时间与地址，<b>两个进程不可能一样</b>，于是这一行
 *       每次都有约一半的概率对不上，后面几百条用例还要按 GLOB1 走不同的分支。
 *       它由 {@link UpstreamSmokeTest} 只断言「跑完并打印 OK」——
 *       这本来就是上游注释里写的「sometimes will be 0, sometimes will not」。</li>
 * </ul>
 */
final class UpstreamSuiteTest {

    private static final String ENV_TESTS = "LUA54_TESTS";

    /** 用例目录；找不到为 {@code null} */
    private static File dir;

    @BeforeAll
    static void locate() {
        String env = System.getenv(ENV_TESTS);
        if (env != null && !env.isEmpty() && new File(env).isDirectory()) {
            dir = new File(env);
        } else if (new File("target/lua54-tests").isDirectory()) {
            dir = new File("target/lua54-tests");
        }
        assumeTrue(dir != null, "找不到上游用例目录，跳过；设 " + ENV_TESTS
                + "，或下载到 target/lua54-tests");
        assumeTrue(ReferenceLua.find() != null, "参考实现二进制不存在，跳过一致性测试");
    }

    /*
     * 显示名里带上文件名。
     * <p>
     * 默认显示名是 {@code suite(String)[4]} —— 曾经整片跑挂在这一条上（超时
     * 180 秒），报出来的只有「[4]」，还得回去数表里的第 4 个是谁。带名字之后
     * 超时消息直接写着 {@code closure.lua}。
     * <p>
     * 用 {@code MethodSource} 而不是 {@code ValueSource}：{@code literals.lua}
     * 要看 native 层在不在，编译期常量表做不到这件事。注意它是<b>整条用例不
     * 存在</b>，不是「跑出来再跳过」——后者会让 {@code tools/check_ci_gates.py}
     * 的跳过数变脏，而零用例是它认识的 {@code --nonative} 语义。
     */
    static List<String> suiteFiles() {
        List<String> files = new ArrayList<>();
        files.add("api.lua");
        files.add("bitwise.lua");
        files.add("calls.lua");
        files.add("closure.lua");
        files.add("code.lua");
        files.add("coroutine.lua");
        files.add("db.lua");
        files.add("errors.lua");
        files.add("events.lua");
        files.add("gengc.lua");
        files.add("goto.lua");
        files.add("nextvar.lua");
        files.add("pm.lua");
        files.add("strings.lua");
        files.add("tpack.lua");
        files.add("utf8.lua");
        files.add("vararg.lua");
        files.add("verybig.lua");
        if (NativeBridge.AVAILABLE) {
            // 见类注释：小数点那段要 os.setlocale 真的能改 libc 的 locale
            files.add("literals.lua");
        }
        return files;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("suiteFiles")
    void suite(String name) {
        Diff.assertFileMatches(dir, name);
    }
}
