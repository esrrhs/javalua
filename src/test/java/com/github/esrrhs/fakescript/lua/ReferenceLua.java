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

    /** 跑一个已存在的脚本文件 */
    static Result runFile(String path) {
        String bin = find();
        if (bin == null) {
            throw new IllegalStateException("no lua5.4 reference binary; set " + ENV_BIN);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(new File(bin).getAbsolutePath(), path);
            pb.redirectErrorStream(true);
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
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
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
