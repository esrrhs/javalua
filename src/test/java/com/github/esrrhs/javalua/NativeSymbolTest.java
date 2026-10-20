package com.github.esrrhs.javalua;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * <b>符号集对撞</b>——C API 那一层的「函数存在吗」。
 * <p>
 * {@link NativeApiTest} / {@link NativeExtTest} 的探针是我们自己写的，只调我们
 * 想要它调的那些 {@code lua_*}；一个符号<b>整条缺席</b>它们一条也发现不了
 * （约定 #50）。这一层不看语义，只比两个库各自导出了什么：
 * {@code nm liblua.a} 与 {@code nm} 我们编出来的 shim。
 * <p>
 * 十个 {@code luaopen_*} 就是这么撞出来的：{@code lualib.h} 里它们用的是
 * {@code LUAMOD_API} 而不是 {@code LUALIB_API}，而 {@code gen_capi.py} 原本
 * 只认后两个 —— 于是整整十个符号在我们的 dylib 里一个都没有，而那时
 * {@code NativeApiTest} 全绿。缺席的后果不是「报错」，是扩展<b>连 dlopen 都过
 * 不去</b>（undefined symbol），比语义错更难查。
 * <p>
 * 只比<b>公开</b>的那部分（{@code lua_*} / {@code luaL_*} / {@code luaopen_*}）：
 * 参考实现还导出一堆内部符号（{@code luaC_*} {@code luaD_*} {@code luaV_*} …），
 * 那些是 {@code lgc.c} 一类的实现细节，本实现没有义务有，也不该被扩展调。
 * <p>
 * 参考库用 {@code LUA54_LIB} 指（默认 {@code /tmp/lua-5.4.7/src/liblua.a}），
 * 找不到 {@code nm} 或缺参考库就整类跳过。差别只在「有没有装」，不在「对不对」。
 */
final class NativeSymbolTest {

    private static final String DEFAULT_LIB =
            "/tmp/lua-5.4.7/src/liblua.a";

    @TestFactory
    List<DynamicTest> symbolsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        String reason = whySkip();
        if (reason != null) {
            tests.add(DynamicTest.dynamicTest("符号集对撞",
                    () -> Assumptions.assumeTrue(false, "跳过：" + reason)));
            return tests;
        }
        TreeSet<String> ref = exported(refLib(), new TreeSet<>());
        TreeSet<String> ours = exported(ourLib(), new TreeSet<>());

        tests.add(DynamicTest.dynamicTest("公开 C API 一个都不少", () -> {
            TreeSet<String> missing = new TreeSet<>(ref);
            missing.removeAll(ours);
            org.junit.jupiter.api.Assertions.assertTrue(missing.isEmpty(),
                    "参考实现导出、我们没有的公开符号：" + missing
                            + "\n（多半是 gen_capi.py 的 decl_re 没认全某个 API 宏）");
        }));

        tests.add(DynamicTest.dynamicTest("没有多出参考实现不认识的符号", () -> {
            TreeSet<String> extra = new TreeSet<>(ours);
            extra.removeAll(ref);
            org.junit.jupiter.api.Assertions.assertTrue(extra.isEmpty(),
                    "我们有、参考实现没有的公开符号：" + extra);
        }));

        tests.add(DynamicTest.dynamicTest("数量对得上（防 nm 抽取本身失效）", () -> {
            org.junit.jupiter.api.Assertions.assertTrue(ref.size() > 100,
                    "从参考库里只抽出 " + ref.size() + " 个公开符号，"
                            + "nm 的抽取多半坏了（阈值 100）");
            org.junit.jupiter.api.Assertions.assertEquals(ref.size(), ours.size(),
                    "公开符号个数不同：参考 " + ref.size() + "，我们 " + ours.size());
        }));
        return tests;
    }

    private static String whySkip() {
        File lib = refLib();
        if (lib == null || !lib.isFile()) {
            return "找不到参考实现的 liblua.a；设 LUA54_LIB 指到它";
        }
        if (ourLib() == null) {
            return "找不到编好的 luashim（先跑 native/build.sh）";
        }
        if (firstOf("nm") == null) {
            return "PATH 里没有 nm";
        }
        return null;
    }

    private static File refLib() {
        String env = System.getenv("LUA54_LIB");
        String p = (env != null && !env.isEmpty()) ? env : DEFAULT_LIB;
        return new File(p);
    }

    /**
     * 随包那份平台库。
     * <p>
     * 不去问 {@link NativeBridge} 要路径：它那两个方法一个是包级可见的、一个是
     * private，为一条测试把可见性放开不值当。这里自己按
     * {@code os.name} / {@code os.arch} 拼一遍目录名，拼不中就退回任意一份——
     * 反正这个目录下只会有一份能在本机加载的。
     */
    private static File ourLib() {
        File dir = new File("src/main/resources/native");
        File[] subs = dir.listFiles();
        if (subs == null) {
            return null;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String want = (os.contains("mac") || os.contains("darwin")) ? "darwin"
                : os.contains("win") ? "windows" : "linux";
        want += "-" + (arch.contains("aarch64") || arch.contains("arm")
                ? "arm64" : "x86_64");
        List<File> any = new ArrayList<>();
        for (File d : subs) {
            if (!d.isDirectory()) {
                continue;
            }
            File[] fs = d.listFiles((x, n) -> n.startsWith("libluashim"));
            if (fs == null) {
                continue;
            }
            for (File f : fs) {
                if (d.getName().equals(want)) {
                    return f;
                }
                any.add(f);
            }
        }
        return any.isEmpty() ? null : any.get(0);
    }

    /**
     * 抽<b>公开</b>符号：{@code lua_*} / {@code luaL_*} / {@code luaopen_*}。
     * <p>
     * macOS 的符号带前导下划线，要剥掉。只要 {@code nm} 认作「已定义」的那些
     * （{@code T D B R}），且不去管 {@code nm} 是 GNU 还是 BSD 口味——
     * 两种输出都是「地址 类型 名字」，取 {@code $3}。
     */
    private static TreeSet<String> exported(File lib, TreeSet<String> out) {
        for (String[] argv : new String[][]{
                {"nm", "-g", "--defined-only", lib.getPath()},
                {"nm", "-gU", lib.getPath()}}) {
            try {
                Process p = new ProcessBuilder(Arrays.asList(argv))
                        .redirectErrorStream(true).start();
                String s = new String(p.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                p.waitFor();
                TreeSet<String> got = new TreeSet<>();
                for (String ln : s.split("\n")) {
                    String[] f = ln.trim().split("\\s+");
                    if (f.length < 3) {
                        continue;
                    }
                    if (!"TDBR".contains(f[f.length - 2])) {
                        continue;
                    }
                    String n = f[f.length - 1];
                    if (n.startsWith("_")) {
                        n = n.substring(1);
                    }
                    if (n.startsWith("lua_") || n.startsWith("luaL_")
                            || n.startsWith("luaopen_")) {
                        got.add(n);
                    }
                }
                if (!got.isEmpty()) {
                    out.addAll(got);
                    return out;
                }
            } catch (Exception ignore) {
                // 换下一种 nm 用法
            }
        }
        return out;
    }

    private static String firstOf(String name) {
        for (String d : System.getenv("PATH").split(File.pathSeparator)) {
            if (new File(d, name).canExecute()) {
                return name;
            }
        }
        return null;
    }
}
