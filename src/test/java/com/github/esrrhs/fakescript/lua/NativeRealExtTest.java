package com.github.esrrhs.fakescript.lua;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * <b>真·第三方 C 扩展</b>的端到端闸门：把 lpeg 编成 .so，用 {@code require} 装进
 * 来，两边跑同一份脚本逐字节比。
 * <p>
 * 为什么另开一个类：{@link NativeApiTest} / {@link NativeExtTest} 的探针是<b>我们
 * 自己写的</b>，只调我们想要它调的那些 {@code lua_*}。真实扩展不顺着我们的清单
 * 走——lpeg 第一跑就撞在 {@code lua_getallocf} 上：它拿
 * {@code lua_getallocf} 的返回值是<b>为了直接当函数指针调用</b>（{@code lpcode.c}
 * 的 {@code newcode} / {@code freecode} / {@code realloccode} 三处），
 * 而这个符号在 {@code Api} 里一直是 {@code unimplemented}。自己写的探针一条
 * 都发现不了，因为压根没人想过要去调它。
 * <p>
 * 源码不入库（第三方代码，且体积不小），用环境变量 {@code LPEG_SRC} 指到解压后的
 * 目录（含 {@code lpcap.c} 那几个文件）；没设就整类跳过。
 */
final class NativeRealExtTest {

    /** 环境变量：lpeg 源码目录 */
    private static final String ENV = "LPEG_SRC";

    private static final String[] SRC = {
        "lpcap.c", "lpcode.c", "lpcset.c", "lpprint.c", "lptree.c", "lpvm.c",
    };

    private static String so;
    private static String skipReason = "";

    /**
     * 脚本网格。
     * <p>
     * 刻意不打印任何 pattern（那是 userdata，地址没法比），只比<b>语义结果</b>。
     * 报错那几条很要紧：{@code luaL_argerror} 的措辞、位置前缀、以及
     * {@code luaL_checkversion_} 那条路，都是扩展真会走到的。
     */
    private static final String[][] CASES = {
        {"装进来",
            "print(type(n), type(n.P), type(n.match))",
            "print(n.match(n.P('a'), 'a'))"},
        {"匹配",
            "print(n.match(n.P('a'), 'a'))",
            "print(n.match(n.P('a'), 'b'))",
            "print(n.match(n.P('abc'), 'abc'))",
            "print(n.match(n.R('az')^1, 'hello'))",
            "print(n.match(n.S('xy')^0, 'xxyyx'))",
            "print(n.match(n.P(1)^0, '\\xe4\\xb8\\xad\\xe6\\x96\\x87'))"},
        {"捕获",
            "print(n.match(n.C(n.R('az')^1), 'hello there'))",
            "local t = n.match(n.Ct(n.C(n.R('az')^1) * n.P(' ') * n.C(n.R('09')^1)), 'abc 123')",
            "print(type(t), t[1], t[2])",
            "print(n.match(n.Cc('x') * n.P('y'), 'y'))",
            "print(n.match(n.Ct((n.C(n.P(1)) * n.P(',')^-1)^0), 'a,b,c')[3])",
            "print(n.match(n.C(n.P('a')^-1) * n.C(n.P(1)^0), 'bb'))"},
        {"折叠 / 替换 / 语法捕获",
            "local add = function(a, b) return a + b end",
            "print(n.match(n.Cf(n.Cc(0) * (n.C(n.R('09')) / tonumber * n.P(',')^-1)^0, add), '1,2,3,4'))",
            "print(n.match(n.Cs((n.P('a') / 'X' + n.P(1))^0), 'banana'))",
            "print(n.match(n.P{'S', S = n.V('V') * n.P('!'), V = n.C(n.R('az')^1)}, 'hi!'))"},
        {"错误路径",
            "print(pcall(n.P, {}))",
            "print(pcall(n.match, n.P('a')))",
            "print(pcall(n.P, 1.5))",
            "print(pcall(function() return n.P('a') + 1 end))",
            "print(pcall(function() return n.P('a'):match() end))"},
        {"扩展自己的 userdata 与元表",
            "local p = n.P('a')",
            "print(type(p), getmetatable(p) ~= nil)",
            "print(pcall(function() return p + 1 end))",
            "print(pcall(function() return #p end))",
            "print(tostring(p):sub(1, 7))"},
    };

    @TestFactory
    List<DynamicTest> realExtensionMatchesReference() {
        List<DynamicTest> tests = new ArrayList<>();
        String path = fixture();
        Assumptions.assumeTrue(path != null, "跳过真实扩展用例：" + skipReason);
        String pre = "package.cpath = [==[" + path + "]==] .. \";\" .. package.cpath\n"
                + "local n = require('lpeg')\n";
        for (String[] c : CASES) {
            StringBuilder src = new StringBuilder(pre);
            for (int i = 1; i < c.length; i++) {
                src.append(c[i]).append('\n');
            }
            String script = src.toString();
            tests.add(DynamicTest.dynamicTest("lpeg: " + c[0],
                    () -> Diff.assertMatches(script)));
        }
        return tests;
    }

    private static synchronized String fixture() {
        if (so != null) {
            return so;
        }
        if (!NativeBridge.AVAILABLE) {
            skipReason = "native 层没装起来: " + NativeBridge.LOAD_ERROR;
            return null;
        }
        String dir = System.getenv(ENV);
        if (dir == null || dir.isEmpty()) {
            skipReason = "没设 " + ENV + "（lpeg 源码目录）";
            return null;
        }
        String cc = firstOf("cc", "gcc", "clang");
        if (cc == null) {
            skipReason = "PATH 里没有 C 编译器";
            return null;
        }
        try {
            File root = new File(dir);
            List<String> cmd = new ArrayList<>(Arrays.asList(
                    cc, "-shared", "-fPIC", "-undefined", "dynamic_lookup",
                    "-I", "native/lua", "-o", "target/rt/lpeg.so"));
            for (String s : SRC) {
                File f = new File(root, s);
                if (!f.isFile()) {
                    skipReason = dir + " 里缺 " + s;
                    return null;
                }
                cmd.add(f.getPath());
            }
            File out = new File("target/rt/lpeg.so");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (p.waitFor() != 0 || !out.isFile()) {
                Files.write(new File("target/rt/lpeg_build.log").toPath(),
                        log.getBytes(StandardCharsets.UTF_8));
                skipReason = "编译 lpeg 失败，见 target/rt/lpeg_build.log";
                return null;
            }
            so = out.getAbsolutePath();
        } catch (Exception e) {
            skipReason = "编译 lpeg 失败: " + e;
            so = null;
        }
        return so;
    }

    private static String firstOf(String... names) {
        for (String n : names) {
            for (String d : System.getenv("PATH").split(File.pathSeparator)) {
                if (new File(d, n).canExecute()) {
                    return n;
                }
            }
        }
        return null;
    }
}
