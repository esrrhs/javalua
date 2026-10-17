package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
     * 传给本实现的 chunk 名<b>带 {@code @}</b>，与参考实现一致。
     * <p>
     * 参考实现的 {@code lua.c} 拿到文件名后会自己加上 {@code @} 再交给
     * {@code luaL_loadfilex}，打印时 {@code luaO_chunkid} 又把它去掉，所以
     * 两边看到的都是裸路径。这里同样加 {@code @} 再让 {@code chunkId} 剥掉——
     * 加载期与运行期错误走的是同一个 {@code luaO_chunkid}（见
     * {@code Lexer.lexError} 与 {@code LuaState.chunkId}），不能只有一边剥。
     */
    static final String CHUNK = "@" + CASE_PATH;

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
        requireReference();
        String raw = refRaw(src);
        assertEquals(true, !raw.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。script:\n" + src);
        /*
         * 挡掉「回溯把后半段吃掉」。
         * <p>
         * 归一化会从第一个 {@code stack traceback:} 处把后面<b>全部砍掉</b>。
         * 对「脚本先打印若干行再崩」这类用例，砍的正是错误信息本身，是有意的；
         * 但对<b>回溯属于预期输出</b>的用例（{@code debug.traceback} 的返回值、
         * 未捕获错误被 {@code msghandler} 装裱后的消息），砍掉的是<b>脚本后半段
         * 的全部输出</b> —— 于是后面几十行分歧一条也发现不了，而用例照样是绿的。
         * <p>
         * 实测：{@code debug} 库矩阵第一版走的就是 {@code assertMatches}，
         * 脚本第 5 段打印了 {@code debug.traceback()}，后面 180 行（含咬出
         * {@code xpcall} 那处真分歧的行）全被砍掉，回退实现居然全绿。
         * 改成 {@code assertFullMatches} 才红。这种「静默少测」比假红危险得多，
         * 所以这里<b>直接判失败</b>而不是默默砍。
         */
        assertEquals(true, !hasContentAfterTraceback(raw),
                "输出里回溯<b>后面还有脚本输出</b>：assertMatches 会从回溯处把后面全砍掉，"
                        + "那一段分歧一条也发现不了。回溯属于预期输出时请改用 "
                        + "assertFullMatches。script:\n" + src);
        assertEquals(normalize(raw, true), mine(src), "script:\n" + src);
    }

    /**
     * 回溯<b>之后</b>是否还有不属于回溯的输出。
     * <p>
     * 判据是「回溯块后面出现了不以制表符开头的非空行」。回溯自己的每一行都以
     * 制表符开头（{@code luaL_traceback} 的格式），所以：
     * <ul>
     *   <li><b>脚本自己崩了</b>（未捕获错误经 {@code msghandler}）：回溯在最后，
     *       后面什么都没有 —— 这正是 {@code assertMatches} 存在的理由，
     *       砍掉它是对的，放过；</li>
     *   <li><b>回溯是脚本打印出来的</b>（{@code debug.traceback()} 的返回值）：
     *       回溯在<b>中间</b>，后面还有脚本自己的输出 —— 那一段会被静默丢掉，
     *       必须挡住。</li>
     * </ul>
     */
    private static boolean hasContentAfterTraceback(String s) {
        int tb = s.indexOf("stack traceback:");
        if (tb < 0) {
            return false;
        }
        boolean started = false;
        for (String line : s.substring(tb).split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (!started) {
                started = true; // "stack traceback:" 那一行本身
                continue;
            }
            if (line.charAt(0) == '\t') {
                continue; // 回溯自己的一行
            }
            return true;
        }
        return false;
    }

    /**
     * 与 {@link #assertMatches} 的差别：<b>不剥回溯</b>。
     * <p>
     * 剥回溯是给「脚本先打印若干行再崩」这类用例准备的兜底——本实现一度没有完整的
     * {@code ldebug.c}，层数对不齐。但 {@code debug.traceback} 的<b>返回值本身就是
     * 回溯</b>，砍掉它就什么都不剩了，用例会退化成「两边都输出空串」的假绿。
     * 所以凡是回溯属于预期输出的用例都走这条。
     */
    static void assertFullMatches(String src) {
        requireReference();
        String r = normalize(refRaw(src), false);
        assertEquals(true, !r.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。script:\n" + src);
        assertEquals(r, normalize(mineRaw(src), false), "script:\n" + src);
    }

    /**
     * 断言一个<b>文件</b>在本实现与参考实现上的输出一致。
     * <p>
     * 与 {@link #assertFullMatches} 的差别是输入来自磁盘而不是字符串：上游自带的
     * 测试套件是一堆 {@code .lua} 文件，而且它们互相 {@code require}
     * （{@code bitwise.lua} 要 {@code require "bwcoercion"}，
     * {@code cstack.lua} 要 {@code require "tracegc"}），只能按文件跑。
     *
     * @param dir  用例所在目录（同时是 {@code require} 的搜索起点与工作目录）
     * @param name 文件名
     */
    static void assertFileMatches(File dir, String name) {
        requireReference();
        String r = normalize(refFileRaw(dir, name), false);
        assertEquals(true, !r.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。file: " + name);
        assertEquals(r, normalize(mineFileRaw(dir, name), false),
                "file: " + name + "\n见 target/rt/" + name + ".ref 与 .mine");
    }

    /** 只比对第一行（回溯与后续内容不可比时用） */
    static void assertFirstLineMatches(String src) {
        requireReference();
        String r = ref(src);
        assertEquals(true, !r.isEmpty(),
                "用例在参考实现上没有产生任何输出，等于没测。script:\n" + src);
        assertEquals(firstLine(r), firstLine(mine(src)), "script:\n" + src);
    }

    /**
     * 没有参考实现二进制就跳过，而不是报错。
     * <p>
     * 守卫放在<b>这一处</b>而不是每个测试类各写一个 {@code @BeforeAll}：
     * 十个类里只有几个写了，漏掉的那几个（全是 {@code @ParameterizedTest}）
     * 在没有 {@code lua5.4} 的机器上会把 {@code ./mvnw clean test} 变成红的——
     * 而 README 里写的构建命令就是它。JUnit 的 {@code assumeTrue} 在这里抛的是
     * {@code TestAbortedException}，整条用例记成 skipped，正是想要的结果。
     */
    private static void requireReference() {
        assumeTrue(ReferenceLua.find() != null,
                "参考实现二进制不存在，跳过一致性测试；设 LUA54_BIN 或从源码构建到 "
                        + "target/lua54/src/lua。已找过：" + ReferenceLua.describe());
    }

    /** 在参考实现上跑一段脚本，返回归一化后的输出（剥回溯） */
    static String ref(String src) {
        return normalize(refRaw(src), true);
    }

    /** 在参考实现上跑一段脚本，返回原始输出 */
    private static String refRaw(String src) {
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
        return out;
    }

    /**
     * 在本实现上跑一段脚本，返回归一化后的输出（剥回溯）。
     * <p>
     * 归一化留在这一层而不是抛给调用方：{@code FuzzTest} 之类的用例直接拿
     * {@code ref} 与 {@code mine} 比，让它们自己记着归一化迟早会漏一次——
     * 漏的那次表现为「只有地址不同」的假红。
     */
    static String mine(String src) {
        return normalize(mineRaw(src), true);
    }

    /** 在参考实现上跑一个文件：工作目录设为用例目录，参考实现才有 {@code ./?.lua} */
    private static String refFileRaw(File dir, String name) {
        String out = ReferenceLua.runFile(new File(dir, name).getAbsolutePath(), dir).output;
        String bin = ReferenceLua.find();
        if (bin != null) {
            out = out.replace(new File(bin).getAbsolutePath() + ": ", "");
        }
        return out;
    }

    /**
     * 只在本实现上跑一个文件，返回<b>原始</b>输出——<b>不碰参考实现</b>。
     * <p>
     * 给「输出不该逐字节比、但要保证能跑完」的那批用例用。它们要么打印随实现
     * 而变的数字（{@code cstack.lua} 的 {@code final count}），要么依赖本实现
     * 没有的设施（{@code gc.lua} 的增量收集器参数）。放进严格比对表会永远红，
     * 完全不跑又会漏掉「栈上限被改坏导致死循环／OOM」这类回归——
     * 而这类回归<b>真的发生过</b>：{@code cstack.lua} 一度把
     * surefire 的 forked JVM 撑到 {@code Java heap space}，
     * {@code db.lua} 一度死循环等一个永远不跑的 {@code __gc}。
     */
    static String mineFileOnly(File dir, String name) {
        return mineFileRaw(dir, name);
    }

    /** 在本实现上跑一个文件，返回<b>原始</b>输出 */
    private static String mineFileRaw(File dir, String name) {
        File f = new File(dir, name);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (java.io.IOException e) {
            throw new AssertionError("读不到用例文件：" + f, e);
        }
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.out = ps;
        L.err = ps;
        LuaValue err;
        try {
            err = runFileLikeLuaC(L, dir, f.getAbsolutePath(), bytes);
        } catch (LuaError e) {
            return messageOf(e) + "\n";
        }
        L.close();
        String stdout = latin1(bos.toByteArray());
        return (err == null) ? stdout : stdout + messageOf(err) + "\n";
    }

    /**
     * 照 {@code lua.c} 的形状驱动一个文件。
     * <p>
     * 比 {@link #runLikeLuaC} 多一步：把用例目录前置到 {@code package.path}。
     * 参考实现靠的是「进程工作目录就是用例目录」—— {@code LUA_PATH_DEFAULT} 里的
     * {@code ./?.lua} 因此能找到 {@code bwcoercion}、{@code tracegc} 这些被
     * {@code require} 的同伴文件。JVM 的工作目录改不了（它是整个 Maven 进程共享
     * 的），所以只能在 {@code package.path} 上补一条绝对路径——输出不受影响，
     * 这批用例里没有一个打印 {@code package.path}。
     */
    private static LuaValue runFileLikeLuaC(LuaState L, File dir, String absPath, byte[] bytes) {
        LuaValue pkg = L.getGlobals().get(LuaString.latin1("package"));
        if (pkg != null && pkg.tt == Lua.TTABLE) {
            LuaTable t = pkg.asTable();
            LuaValue p = t.get(LuaString.latin1("path"));
            if (p != null && p.isString()) {
                t.set("path", LuaValue.ofString(LuaString.utf8(
                        dir.getAbsolutePath() + "/?.lua;" + p.asString().toJavaString())));
            }
        }
        int base = L.top;
        L.push(LuaValue.ofFunction(Diff::pmainFile));
        L.push(LuaValue.ofString(LuaString.of(bytes)));
        L.push(LuaValue.ofString(LuaString.utf8("@" + absPath)));
        return L.pcall(base, 0);
    }

    /**
     * {@code lua.c: pmain} 跑文件的形态：chunk 名是 {@code @} 加路径。
     * <p>
     * 消息处理函数是<b>压在栈上</b>再交给 {@code lua_pcall} 的，形状照
     * {@code lua.c: docall}：
     * <pre>
     * int base = lua_gettop(L) - narg;      // 函数所在的下标
     * lua_pushcfunction(L, msghandler);     // 压在 chunk 之上
     * lua_insert(L, base);                  // 再插到 chunk <b>下面</b>
     * lua_pcall(L, narg, nres, base);
     * </pre>
     * 这一步不只是「多占一格」：栈上因此多了一个槽，递归深度就少一层。上游
     * {@code errors.lua} 打印的「expected stack overflow after N calls」是
     * 照着参考实现那个数字断言的，少这一格就会多报一层。
     */
    private static int pmainFile(LuaState L) {
        byte[] bytes = L.arg1(1).asString().bytes;
        String chunk = L.arg1(2).asString().toJavaString();
        int base = L.top;
        L.pushClosure(L.load(bytes, chunk));
        int func = pushMsgHandler(L, base);
        LuaValue err = L.pcall(func, 0, L.stack[base]);
        if (err != null) {
            throw new LuaError(err, "");
        }
        return 0;
    }

    /**
     * {@code lua.c: docall} 那两行：把 {@code msghandler} 压到栈顶，再插到
     * {@code base} 处（chunk <b>下面</b>）。
     *
     * @return 插完之后 chunk 所在的下标
     */
    private static int pushMsgHandler(LuaState L, int base) {
        L.push(LuaValue.ofFunction(Diff::msghandler));
        LuaValue tmp = new LuaValue(L.stack[base]);
        L.stack[base].set(L.stack[base + 1]);
        L.stack[base + 1].set(tmp);
        return base + 1;
    }

    /**
     * 照 {@code lua.c} 的形状驱动：{@code main} 里 {@code lua_pcall(pmain)}，
     * 脚本是 {@code pmain} 这个函数<b>里面</b>加载并调用的。
     * <p>
     * 这一层不是形式主义——{@code pmain} 本身是一个 <b>C 函数帧</b>，它会实实在在
     * 出现在栈上。实测 {@code debug.traceback()} 在参考实现里最后一行是
     * {@code [C]: in ?}，{@code debug.getinfo(3).what == "C"}，都是这一帧。
     * 少了它，回溯与层号就整体差一层，而 {@code debug} 库的用例会假红在
     * 一件跟 {@code debug} 库无关的事情上。
     * <p>
     * 至于引擎本身：宿主直接 {@code luaL_dostring} 时<b>没有</b>这一帧，
     * {@code debug.traceback()} 也就不会多出那一行。所以这属于<b>驱动方式</b>
     * 的差异，不该靠改引擎去凑。
     */
    private static LuaValue runLikeLuaC(LuaState L, String src) {
        int base = L.top;
        L.push(LuaValue.ofFunction(Diff::pmain));
        L.push(LuaValue.ofString(LuaString.of(src.getBytes(StandardCharsets.UTF_8))));
        /*
         * 第二个实参不是凑数的：{@code lua.c: main} 给 {@code pmain} 传的是
         * {@code (argc, argv)} <b>两个</b>实参，栈的初始 {@code top} 因此是 3。
         * 只传一个就比参考实现少占一格，而<b>少一格会一路带到底</b> ——
         * 脚本栈溢出时的递归深度差一层，最深那一帧也就换了个函数，
         * 报错行号随之不同（fuzz seed 7984 撞的就是这个：参考报第 67 行，
         * 我们报第 65 行）。按帧大小铺开量过：补上这一格之后
         * 0/1/2/3/4/6/8/16/32 个局部变量的九种递归深度<b>全部</b>与参考一致。
         */
        L.push(LuaValue.ofInteger(2));
        return L.pcall(base, 0);
    }

    /** {@code lua.c: pmain}：加载并执行脚本 */
    private static int pmain(LuaState L) {
        byte[] bytes = L.arg1(1).asString().bytes;
        // lua.c: docall —— 未捕获的错误会经过 msghandler，回溯就是它加的
        int base = L.top;
        L.pushClosure(L.load(bytes, CHUNK));
        int func = pushMsgHandler(L, base);
        LuaValue err = L.pcall(func, 0, L.stack[base]);
        if (err != null) {
            throw new LuaError(err, "");
        }
        return 0;
    }

    /**
     * {@code lua.c: msghandler}：{@code docall} 给脚本装上的默认消息处理函数。
     * <p>
     * 不装它，脚本里未捕获的错误就只有一行消息，而参考实现还带着一整段
     * {@code luaL_traceback}——两个用例（一个是「先打印几行再崩」）会在
     * 「回溯之后的内容」上差一截，恰好又是被归一化抹掉的那截，于是没人发现。
     */
    private static int msghandler(LuaState L) {
        LuaValue v = L.arg1(1);
        String msg;
        if (v.isString()) {
            msg = v.asString().toJavaString();
        } else if (callMeta(L, v, "__tostring")
                && L.stack[L.top - 1].isString()) {
            /*
             * 看的是<b>栈顶</b>（{@code __tostring} 的结果），不是第 1 个参数——
             * 第 1 个参数是错误对象本人，它当然不是字符串。看错位置的症状是
             * {@code error(setmetatable({}, {__tostring = f}))} 的未捕获错误
             * 打成了 {@code (error object is a table value)} 而不是 {@code f} 的
             * 结果。C 版这里是 {@code lua_type(L, -1) == LUA_TSTRING}，看的就是 -1。
             */
            return 1;
        } else {
            msg = "(error object is a " + Lua.typeName(v.baseType()) + " value)";
        }
        L.push(LuaValue.ofString(LuaString.utf8(DebugLib.traceback(L, L, msg, 1))));
        return 1;
    }

    /**
     * {@code lauxlib.c: luaL_callmeta}。
     * <p>
     * 只查<b>对象自己的元表</b>这一层（不走 {@code __index} 链），且调用是
     * <b>不保护</b>的——{@code __tostring} 自己出错时错误会继续冒，正好被
     * {@code applyErrMsg} 那层兜成 {@code error in error handling}。
     */
    private static boolean callMeta(LuaState L, LuaValue v, String event) {
        LuaTable mt = LuaTm.metatableOf(L, v);
        if (mt == null) {
            return false;
        }
        LuaValue tm = mt.get(LuaString.latin1(event));
        if (tm == null || tm.isNil()) {
            return false;
        }
        int base = L.top;
        L.push(tm);
        L.push(new LuaValue(v));
        L.call(base, 1);
        return true;
    }

    /**
     * 在本实现上跑一段脚本，返回<b>原始</b>输出。
     * <p>
     * 出错时不带程序名前缀：参考实现的 {@code lua.c} 直接写消息本身
     * （{@code lua_writestringerror("%s\n", msg)}），程序名是 shell 加的。
     */
    private static String mineRaw(String src) {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.out = ps;
        // stderr 也指到同一条缓冲上：参考实现是合并捕获的
        L.err = ps;
        LuaValue err;
        try {
            err = runLikeLuaC(L, src);
        } catch (LuaError e) {
            /*
             * 加载期错误（词法/语法）在参考实现里也不可被 pcall 捕获：
             * lua.c 加载失败就直接打印并退出。所以这里同样让它冒出来，
             * 只是转成与参考实现同形的消息参与比对——否则一个生成器 bug
             * 会变成看不懂的堆栈。
             */
            return messageOf(e) + "\n";
        }
        /*
         * 参考实现的 lua.c 在退出前会 lua_close，而 lua_close 会把还没跑的 __gc
         * 全跑一遍。不补这一步，「不调 collectgarbage 的 __gc 用例」两边就差一截
         * 输出——而且差的是脚本<b>结束之后</b>才打印的那部分，很容易误判成
         * 「__gc 根本没实现」。
         */
        L.close();
        String stdout = latin1(bos.toByteArray());
        /*
         * 出错时<b>不能把 stdout 丢掉</b>。参考实现的 stdout 与 stderr 被合并
         * 捕获，所以「先打印十几行、然后崩」的脚本，参考输出是那十几行加错误
         * 消息；只返回消息就会把这类用例整片判红——而且红得毫无道理，
         * 因为 VM 本身是对的。这个坑是 fuzzer 抓出来的。
         */
        if (err == null) {
            return stdout;
        }
        return stdout + messageOf(err) + "\n";
    }

    private static String messageOf(LuaValue err) {
        if (err.isString()) {
            // 与捕获输出同一套解码，否则消息里的非 UTF-8 字节会被换成 U+FFFD
            return latin1(err.asString().bytes);
        }
        return "(error object is a " + err.typeName() + " value)";
    }

    /**
     * 字节摊成字符：Latin-1 是字节↔字符的一一映射，比对因此是<b>逐字节</b>的。
     * <p>
     * 按 UTF-8 解码的话非法字节会变成 U+FFFD，而参考实现那边的同一批字节也变成
     * U+FFFD —— 于是「一边 0x80、另一边 0xFF」被判成一致。差分闸门对整片非
     * UTF-8 输出是瞎的，这本身就是一类假绿。
     */
    private static String latin1(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static String messageOf(LuaError e) {
        return (e.value == null) ? "" : messageOf(e.value);
    }

    private static String normalize(String s, boolean stripTraceback) {
        if (stripTraceback) {
            int tb = s.indexOf("stack traceback:");
            if (tb >= 0) {
                s = s.substring(0, tb);
            }
        }
        return s.replaceAll("0x[0-9a-fA-F]+", "0xADDR");
    }

    static String firstLine(String s) {
        int i = s.indexOf('\n');
        return (i < 0) ? s : s.substring(0, i);
    }
}
