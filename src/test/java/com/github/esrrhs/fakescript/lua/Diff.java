package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 运行期差分的公共驱动 —— 把「一段脚本跑两遍、比对输出」这件事收在一处。
 * <p>
 * 阶段 3 建 {@code RuntimeTest} 时这套逻辑是写在测试类里的；阶段 4 要再加一批
 * 针对闭包/upvalue/goto/变长参数的用例，再抄一遍就会出现两份略有差异的
 * 归一化规则——而归一化规则不一致本身就是一类假失败（一边抹了地址另一边没抹）。
 * 所以抽出来共用。
 * <p>
 * 归一化抹掉三样东西，它们都不属于「脚本语义」：
 * <ul>
 *   <li><b>回溯</b>：依赖调试信息的层数，本实现还没有完整的 {@code ldebug.c}
 *       回溯；</li>
 *   <li><b>地址</b>：{@code table: 0x...} 每次运行都不同；</li>
 *   <li><b>程序名前缀</b>：参考实现的程序名是二进制路径。</li>
 * </ul>
 */
final class Diff {

    /**
     * 脚本路径固定为 {@code target/rt/case.lua}。
     * <p>
     * 不能换成随机临时文件：参考实现的 {@code luaO_chunkid} 会把过长的源名
     * 从左边截成 {@code ...xxx}，两边一截就没得比了。
     */
    static final String CASE_PATH = "target/rt/case.lua";

    /**
     * 传给本实现的 chunk 名用<b>裸路径</b>，不带 {@code @}。
     * <p>
     * 参考实现的 {@code lua.c} 拿到文件名后会自己加上 {@code @} 再交给
     * {@code luaL_loadfilex}，打印时 {@code luaO_chunkid} 又把它去掉，
     * 所以两边看到的都是裸路径。而我们这边只有运行期错误会过 {@code chunkId}——
     * 词法/语法错误直接用原始 chunk 名拼消息（见 {@code Lexer.lexError}）——
     * 于是带 {@code @} 就会在编译错误上多出一个字符。
     */
    static final String CHUNK = CASE_PATH;

    private Diff() {
    }

    /**
     * 断言一段脚本在本实现与参考实现上的输出一致。
     * <p>
     * 顺带挡掉「空用例」：两边都输出空串的用例会**无条件通过**，什么也没证明。
     * 打错一个函数名、或者脚本在开头就静默结束了，都会变成这种空用例——
     * 而它看起来是全绿的一部分。
     */
    static void assertMatches(String src) {
        String r = ref(src);
        assertEquals(true, !r.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。script:\n" + src);
        assertEquals(r, mine(src), "script:\n" + src);
    }

    /** 只比对第一行（回溯与后续内容不可比时用） */
    static void assertFirstLineMatches(String src) {
        String r = ref(src);
        assertEquals(true, !r.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。script:\n" + src);
        assertEquals(firstLine(r), firstLine(mine(src)), "script:\n" + src);
    }

    /** 在参考实现上跑一段脚本，返回归一化后的输出 */
    static String ref(String src) {
        ReferenceLua.Result r = ReferenceLua.runBytes(src.getBytes(StandardCharsets.UTF_8),
                CASE_PATH).result;
        String out = r.output;
        String bin = ReferenceLua.find();
        if (bin != null) {
            /*
             * 程序名是 {@code lua.c: l_message} 加的（{@code "%s: "}），
             * 每次打印错误都有。所以要<b>全文替换</b>而不是只去开头：
             * stdout 与 stderr 是分别捕获后拼起来的，脚本先打印几行再崩时，
             * 程序名出现在中间，不在开头。
             */
            out = out.replace(new File(bin).getAbsolutePath() + ": ", "");
        }
        return normalize(out);
    }

    /**
     * 在本实现上跑一段脚本，返回归一化后的输出。
     * <p>
     * 出错时不带程序名前缀：参考实现的 {@code lua.c} 直接写消息本身
     * （{@code lua_writestringerror("%s\n", msg)}），程序名是 shell 加的。
     */
    static String mine(String src) {
        LuaState L = new LuaState();
        BaseLib.open(L);
        StringLib.open(L);
        TableLib.open(L);
        MathLib.open(L);
        Utf8Lib.open(L);
        CoroutineLib.open(L);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        L.out = new PrintStream(bos, true, StandardCharsets.UTF_8);
        LuaValue err;
        try {
            err = L.runProtected(src.getBytes(StandardCharsets.UTF_8), CHUNK);
        } catch (LuaError e) {
            /*
             * 加载期错误（词法/语法）在参考实现里也不可被 pcall 捕获：
             * lua.c 加载失败就直接打印并退出。所以这里同样让它冒出来，
             * 只是转成与参考实现同形的消息参与比对——否则一个生成器 bug
             * 会变成看不懂的堆栈。
             */
            return normalize(messageOf(e) + "\n");
        }
        /*
         * 参考实现的 lua.c 在退出前会 lua_close，而 lua_close 会把还没跑的 __gc
         * 全跑一遍。不补这一步，「不调 collectgarbage 的 __gc 用例」两边就差一截
         * 输出——而且差的是脚本<b>结束之后</b>才打印的那部分，很容易误判成
         * 「__gc 根本没实现」。
         */
        L.close();
        String stdout = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        /*
         * 出错时<b>不能把 stdout 丢掉</b>。参考实现的 stdout 与 stderr 被合并
         * 捕获，所以「先打印十几行、然后崩」的脚本，参考输出是那十几行加错误
         * 消息；只返回消息就会把这类用例整片判红——而且红得毫无道理，
         * 因为 VM 本身是对的。这个坑是 fuzzer 抓出来的。
         */
        if (err == null) {
            return normalize(stdout);
        }
        return normalize(stdout + messageOf(err) + "\n");
    }

    private static String messageOf(LuaValue err) {
        if (err.isString()) {
            return err.asString().toJavaString();
        }
        return "(error object is a " + err.typeName() + " value)";
    }

    private static String messageOf(LuaError e) {
        return (e.value == null) ? "" : messageOf(e.value);
    }

    private static String normalize(String s) {
        int tb = s.indexOf("stack traceback:");
        if (tb >= 0) {
            s = s.substring(0, tb);
        }
        return s.replaceAll("0x[0-9a-fA-F]+", "0xADDR");
    }

    static String firstLine(String s) {
        int i = s.indexOf('\n');
        return (i < 0) ? s : s.substring(0, i);
    }
}
