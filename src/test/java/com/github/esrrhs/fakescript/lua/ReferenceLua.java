package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 参考实现的驱动：调用一个真实的 {@code lua5.4} 二进制跑脚本。
 * <p>
 * 为什么需要它：这是本项目的<b>主闸门</b>。「看起来像 Lua」没有价值，
 * 唯一能证明「真的是 Lua」的办法是同一份脚本在两边跑出<b>逐字节相同</b>的
 * stdout。上游自带的测试套件（{@code lua-5.4.x/tests}）再刁钻，也覆盖不到
 * 我们自己实现里的那些「碰巧对了」的地方。
 * <p>
 * 二进制的查找顺序：
 * <ol>
 *   <li>环境变量 {@code LUA54_BIN}（CI 与本地开发都用它指向确切的路径）</li>
 *   <li>仓库内 {@code target/lua54/src/lua}（CI 构建脚本的默认落点）</li>
 *   <li>PATH 上的 {@code lua5.4} / {@code lua}</li>
 * </ol>
 * 找到之后还会跑一次 {@code -v} 校验版本确实是 5.4：本机上那个 {@code lua}
 * 很可能是 5.5 或其他版本，拿错版本当参考会得出错误的「不一致」结论，
 * 那比没有参考更糟。
 */
final class ReferenceLua {

    private static final String ENV_BIN = "LUA54_BIN";

    private static String cached;
    private static boolean lookedUp;

    private ReferenceLua() {
    }

    /** 找到可用的 lua5.4 二进制；找不到返回 null */
    static synchronized String find() {
        if (lookedUp) {
            return cached;
        }
        lookedUp = true;

        List<String> candidates = new ArrayList<>();
        String env = System.getenv(ENV_BIN);
        if (env != null && !env.isEmpty()) {
            candidates.add(env);
        }
        candidates.add("target/lua54/src/lua");
        candidates.add("/usr/local/bin/lua5.4");
        candidates.add("/opt/homebrew/bin/lua5.4");
        candidates.add("lua5.4");
        candidates.add("lua");

        for (String c : candidates) {
            if (isLua54(c)) {
                cached = c;
                return cached;
            }
        }
        return null;
    }

    /** 校验二进制存在且版本是 5.4 */
    private static boolean isLua54(String bin) {
        try {
            ProcessBuilder pb;
            if (bin.contains(File.separator) || bin.startsWith("target/")) {
                File f = new File(bin);
                if (!f.canExecute()) {
                    return false;
                }
                pb = new ProcessBuilder(f.getAbsolutePath(), "-v");
            } else {
                pb = new ProcessBuilder(bin, "-v");
            }
            Process p = pb.start();
            String out = readAll(p.getInputStream()) + readAll(p.getErrorStream());
            p.waitFor();
            return out.contains("Lua 5.4");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 找到可用的 {@code luac5.4}；找不到返回 {@code null}。
     * <p>
     * 它是「二进制 chunk 双向兼容」那个用例的编译器：本实现要跑<b>参考实现
     * 编出来的</b> chunk，而不是自己编出来的——后者等于用自己的编译器给自己
     * 判卷。{@code make} 默认会把 {@code luac} 和 {@code lua} 一起编出来，
     * 所以 CI 上 {@code target/lua54/src/luac} 一定在。
     */
    static String findLuac() {
        return findSibling("luac", "LUAC54_BIN", "luac5.4", "luac");
    }

    /**
     * 按同一套规则找「参考实现旁边的另一个工具」。
     *
     * @param sibling 与 {@code lua} 同目录时它的文件名
     * @param env     指定它的环境变量
     * @param names   PATH 上可能的名字
     */
    private static String findSibling(String sibling, String env, String... names) {
        String e = System.getenv(env);
        if (e != null && !e.isEmpty() && canRun(e)) {
            return e;
        }
        String lua = find();
        if (lua != null) {
            java.io.File f = new java.io.File(new java.io.File(lua).getParentFile(), sibling);
            if (f.canExecute() && canRun(f.getAbsolutePath())) {
                return f.getAbsolutePath();
            }
        }
        for (String n : names) {
            if (canRun(n)) {
                return n;
            }
        }
        return null;
    }

    private static boolean canRun(String bin) {
        try {
            java.io.File f = new java.io.File(bin);
            ProcessBuilder pb = new ProcessBuilder(
                    f.isAbsolute() ? bin : (f.getPath().contains(java.io.File.separator)
                            ? f.getAbsolutePath() : bin),
                    "-v");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            p.waitFor();
            return out.contains("Lua 5.4");
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 用参考实现自己的编译器把源码编成二进制 chunk。
     *
     * @return 产物路径；编译失败返回 {@code null}
     */
    static java.io.File compile(byte[] source, String outPath) {
        String luac = findLuac();
        if (luac == null) {
            return null;
        }
        try {
            java.nio.file.Path out = Paths.get(outPath);
            java.nio.file.Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            java.nio.file.Path src = Paths.get(outPath + ".lua");
            Files.write(src, source);
            ProcessBuilder pb = new ProcessBuilder(luac, "-o", outPath, src.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            readAll(p.getInputStream());
            int code = p.waitFor();
            Files.deleteIfExists(src);
            return (code == 0) ? out.toFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 跑一段脚本，返回退出码与合并后的输出 */
    static Result run(String script) {
        String bin = find();
        if (bin == null) {
            throw new IllegalStateException("no lua5.4 reference binary; set " + ENV_BIN);
        }
        try {
            File tmp = File.createTempFile("fkconf", ".lua");
            Files.write(tmp.toPath(), script.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb = new ProcessBuilder(new File(bin).getAbsolutePath(),
                    tmp.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            int code = p.waitFor();
            tmp.delete();
            return new Result(code, out);
        } catch (Exception e) {
            throw new IllegalStateException("failed to run reference lua: " + e, e);
        }
    }

    /**
     * 跑一段脚本，stdin 由调用方给。
     * <p>
     * 只有 {@code debug.debug}（{@code ldblib.c: db_debug}）会读 stdin，而它
     * 是<b>一路读到 EOF 或 {@code cont}</b> 的循环——不喂 stdin 就只能验到「打完
     * 提示符就退出」这一种形状，循环体、出错分支、{@code cont} 三条路全验不到。
     *
     * @param stdin 喂给脚本的字节；{@code null} 表示 {@code /dev/null}
     */
    static Result runWithStdin(byte[] script, byte[] stdin) {
        String bin = find();
        if (bin == null) {
            throw new IllegalStateException("no lua5.4 reference binary; set " + ENV_BIN);
        }
        try {
            File tmp = File.createTempFile("fkstdin", ".lua");
            Files.write(tmp.toPath(), script);
            ProcessBuilder pb = new ProcessBuilder(bin, tmp.getAbsolutePath());
            pb.redirectErrorStream(true);
            File in = null;
            if (stdin == null) {
                pb.redirectInput(new File("/dev/null"));
            } else {
                in = File.createTempFile("fkstdin", ".in");
                Files.write(in.toPath(), stdin);
                pb.redirectInput(in);
            }
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            int code = p.waitFor();
            Files.deleteIfExists(tmp.toPath());
            if (in != null) {
                Files.deleteIfExists(in.toPath());
            }
            return new Result(code, out);
        } catch (Exception e) {
            throw new IllegalStateException("failed to run reference lua: " + e, e);
        }
    }

    /** 跑一个已存在的脚本文件 */
    static Result runFile(String path) {
        return runFile(path, null);
    }

    /**
     * 跑一个已存在的脚本文件。
     *
     * @param cwd 工作目录；上游测试套件靠它把 {@code ./?.lua} 落在用例目录上，
     *            从而 {@code require} 到同目录里的同伴文件
     */
    static Result runFile(String path, File cwd) {
        String bin = find();
        if (bin == null) {
            throw new IllegalStateException("no lua5.4 reference binary; set " + ENV_BIN);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(new File(bin).getAbsolutePath(), path);
            if (cwd != null) {
                pb.directory(cwd);
            }
            pb.redirectErrorStream(true);
            /*
             * stdin 定向到 /dev/null：本实现的 LuaState#in 默认是空流，
             * 两边必须一致，否则 io.read() 一边给 nil 另一边却阻塞在
             * 继承来的终端上。
             */
            pb.redirectInput(new File("/dev/null"));
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            int code = p.waitFor();
            return new Result(code, out);
        } catch (Exception e) {
            throw new IllegalStateException("failed to run reference lua: " + e, e);
        }
    }

    /**
     * 把<b>原始字节</b>写成脚本文件并跑它。
     * <p>
     * 不走 {@link #run(String)} 是因为后者按 UTF-8 编码写文件：词法用例里会有
     * 非法 UTF-8 字节（{@code \x80} 之类），而参考实现读的是字节流，
     * 用 Java String 中转会把用例改写成另一份输入。
     * <p>
     * 路径要短：参考实现的 {@code luaO_chunkid} 会把超过 {@code LUA_IDSIZE}
     * 的源名截成 {@code ...xxx}，{@code java.io.tmpdir} 下那串随机临时文件名
     * 必然超长，比对就没法做了。
     */
    static FileResult runBytes(byte[] content, String path) {
        String bin = find();
        if (bin == null) {
            throw new IllegalStateException("no lua5.4 reference binary; set " + ENV_BIN);
        }
        try {
            java.nio.file.Path p = Paths.get(path);
            java.nio.file.Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(p, content);
            Result r = runFile(path);
            return new FileResult(r, path);
        } catch (Exception e) {
            throw new IllegalStateException("failed to run reference lua: " + e, e);
        }
    }

    /**
     * 用参考实现的编译器把脚本编成字节码，再交给 {@code fkdump} 转储。
     * <p>
     * 二进制的来源（优先级从高到低）：环境变量 {@code FKDUMP_BIN}、与
     * {@code lua5.4} 二进制同目录下的 {@code fkdump}、PATH 上的 {@code fkdump}。
     * 找不到就返回 {@code null}，测试跳过——CI 的 conformance job 会把它编出来。
     */
    static String dumpProto(byte[] content, String path) {
        String bin = findFkdump();
        if (bin == null) {
            return null;
        }
        try {
            java.nio.file.Path p = Paths.get(path);
            java.nio.file.Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(p, content);
            ProcessBuilder pb = new ProcessBuilder(bin, path);
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            String out = readAll(pr.getInputStream());
            pr.waitFor();
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("failed to run fkdump: " + e, e);
        }
    }

    private static String fkdumpCached;
    private static boolean fkdumpLookedUp;

    static synchronized String findFkdump() {
        if (fkdumpLookedUp) {
            return fkdumpCached;
        }
        fkdumpLookedUp = true;
        List<String> candidates = new ArrayList<>();
        String env = System.getenv("FKDUMP_BIN");
        if (env != null && !env.isEmpty()) {
            candidates.add(env);
        }
        String lua = find();
        if (lua != null && lua.contains(File.separator)) {
            candidates.add(new File(lua).getParent() + File.separator + "fkdump");
        }
        candidates.add("fkdump");
        for (String c : candidates) {
            File f = new File(c.contains(File.separator) ? c : c);
            if (f.canExecute()) {
                fkdumpCached = f.getAbsolutePath();
                return fkdumpCached;
            }
        }
        return null;
    }

    static final class FileResult {
        final Result result;
        final String path;

        FileResult(Result result, String path) {
            this.result = result;
            this.path = path;
        }
    }

    private static String readAll(InputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            /*
             * 用 Latin-1 而不是 UTF-8 解码：Latin-1 是字节↔字符的<b>一一映射</b>，
             * 比对因此是逐字节的。按 UTF-8 解码的话，任何非法字节都会变成
             * U+FFFD —— 两边同时变成 U+FFFD，于是「一边输出 0x80、另一边输出
             * 0xFF」这种真分歧被判成一致。差分闸门对整片非 UTF-8 输出是瞎的，
             * 这件事本身就是一类假绿。
             */
            return new String(bos.toByteArray(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return "";
        }
    }

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        @Override
        public String toString() {
            return "exit=" + exitCode + "\n" + output;
        }
    }

    /** 让测试能跳过而不是失败（找不到参考二进制时） */
    static boolean available() {
        return find() != null;
    }

    /** 供诊断：把查找路径打出来 */
    static String describe() {
        String bin = find();
        return bin == null ? ("<not found; set " + ENV_BIN + ">") : bin;
    }

    /** 未使用，仅为让 Files/Paths 的 import 保持有效（实际用于 run 的临时文件） */
    static boolean exists(String p) {
        return Files.exists(Paths.get(p));
    }

    /** 未使用，占位以保持与 OutputStream 相关的签名清晰 */
    @SuppressWarnings("unused")
    private static void unused(OutputStream os) {
    }
}
