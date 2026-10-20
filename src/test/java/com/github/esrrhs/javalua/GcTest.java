package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * {@code __gc} 的差分用例 —— 阶段 6 的第三块。
 * <p>
 * 与弱表那批一样，这批依赖 JVM 真的把对象收掉，而且多依赖一样：
 * {@code finalize} 必须真的被调到。所以这里只写<b>能稳定复现</b>的形态，
 * 不写「正好卡在某一轮收集上」的用例——那是写给自己找麻烦。
 * <p>
 * 三个被实测钉住的事实，是这批用例的全部依据：
 * <ul>
 *   <li>{@code __gc} 只跑一次；</li>
 *   <li>{@code __gc} 里的错误<b>静默吞掉</b>，既不打印也不传播；</li>
 *   <li>设元表<b>之后</b>才往元表里塞 {@code __gc} 不生效。</li>
 * </ul>
 */
class GcTest {

    @TestFactory
    List<DynamicTest> gcScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = SCRIPTS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("gc[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    /**
     * 不依赖收集时机的那一半。
     * <p>
     * 这一半无论 JVM 收不收都必须成立——它们红了是逻辑写错了，不是环境的问题。
     */
    @TestFactory
    List<DynamicTest> gcRulesWithoutCollection() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < NO_GC.length; i++) {
            String src = NO_GC[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("gcNoGc[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    private static final String[] NO_GC = {

            """
            -- 设元表当下没有 __gc → 之后补上也不生效（实测参考实现如此）
            local mt = {}
            local t = setmetatable({}, mt)
            mt.__gc = function() print("SHOULD NOT RUN") end
            collectgarbage()
            print("done")
            """,

            """
            -- __gc 不是函数：静默忽略
            local t = setmetatable({}, {__gc = 5})
            collectgarbage()
            print("ok", type(t))
            """,

            """
            -- 还活着的对象不会被终结
            local t = setmetatable({}, {__gc = function() print("SHOULD NOT RUN") end})
            collectgarbage()
            print("alive", type(t))
            """,

            """
            -- __gc 是 nil：什么也不做
            local t = setmetatable({}, {__gc = nil})
            collectgarbage()
            print("ok")
            """,

            """
            -- 反复 collectgarbage，__gc 只跑一次
            local n = 0
            do
              local t = setmetatable({}, {__gc = function() n = n + 1 end})
            end
            collectgarbage()
            collectgarbage()
            collectgarbage()
            print("times", n)
            """,
    };

    private static final String[] SCRIPTS = {

            """
            -- 最基本的：对象不可达后 __gc 跑一次
            local n = 0
            do
              local t = setmetatable({}, {__gc = function(o) n = n + 1 end})
            end
            collectgarbage()
            print("called", n)
            """,

            """
            -- 参数就是那个对象
            do
              local t = setmetatable({}, {__gc = function(o) print("arg", type(o)) end})
            end
            collectgarbage()
            """,

            """
            -- __gc 里能看见全局
            do
              local t = setmetatable({}, {__gc = function(o) print("sees", type(_G)) end})
            end
            collectgarbage()
            """,

            """
            -- __gc 里报错：静默吞掉，后面的代码照常跑
            do
              local t = setmetatable({}, {__gc = function(o) error("boom in gc") end})
            end
            collectgarbage()
            print("after")
            """,

            """
            -- 一批对象的 __gc 都会跑到。
            -- 只数个数、不比对顺序：Lua 没规定顺序，参考实现给出的是 3,2,1，
            -- 而本实现的顺序取决于 JVM 终结线程把哨兵入队的先后，本身就不该被测。
            local n = 0
            for i = 1, 3 do
              local t = setmetatable({}, {__gc = function(o) n = n + 1 end})
            end
            collectgarbage()
            print("count", n)
            """,

            """
            -- 没有 collectgarbage：退出时照样跑
            do
              local t = setmetatable({}, {__gc = function(o) print("at exit") end})
            end
            print("before exit")
            """,

            """
            -- 收尾时又造一个带 __gc 的对象。
            -- 这一条盯的不是「__gc 跑几次」（时机是<b>有意偏离</b>，约定 #57），
            -- 而是<b>别崩</b>：{@code lua_close} 要用下标遍历 pending 列表，
            -- 因为 __gc 里再 setmetatable 会往同一个列表里追加，
            -- ArrayList 的迭代器当场抛 ConcurrentModificationException——
            -- 那是 Java 自己的异常（约定 #4），会被误读成脚本报错。
            -- C 那边是 while (g->tobefnz) GCTM(L)，新入队的接着跑，不炸。
            local n = 0
            local function mk()
              n = n + 1
              setmetatable({}, {__gc = function(o)
                if n < 5 then mk() end
              end})
            end
            mk()
            print("ok", n)
            """,

            """
            -- 弱表与 __gc 同时用：__gc 照常会跑。
            -- 这里<b>不</b>断言弱表里那一项还在不在：参考实现里带终结器的对象在
            -- 终结器跑掉之前会被 mark 成活的，于是那一轮弱条目还在（实测 false）；
            -- 本实现靠 JVM 的 WeakReference 判死活，做不到「死过一次又活过来」，
            -- 弱条目会在同一轮就被摘掉。这是已知的一处不一致，README 里记着。
            local n = 0
            local wt = setmetatable({}, {__mode = "k"})
            do
              local t = setmetatable({}, {__gc = function(o) n = n + 1 end})
              wt[t] = "v"
            end
            collectgarbage()
            print("gc ran", n)
            """,
    };

    /**
     * 「{@code collectgarbage()} 返回的时候，{@code __gc} 必须已经全部跑完」这条
     * 契约的<b>压力</b>版本。
     * <p>
     * 上面那条差分用例（{@code gcScriptsMatchReference} 里的「一批对象的
     * {@code __gc} 都会跑到」）本来就是冲它来的，但它一次只跑<b>一遍</b>，
     * 撞不撞得上下方那场竞态全看机器当时忙不忙——实测整套 1100 条跑到后半程时
     * 六次里出过一次：期望 {@code count 3}，拿到 {@code count 0}，而同一轮的
     * 另外五次都是 3。这种「绿了五百次、红了一次」的东西不能只靠重跑交差。
     * <p>
     * <b>竞态在哪。</b>{@code __gc} 靠 {@link GcSentinel} 的 {@code finalize}
     * 报信，而报信的是 <b>JVM 的另一条线程</b>；{@code LuaState
     * #collectAndRunFinalizers} 只能在「排空队列」和「问清单上还有已死未跑的
     * 吗」之间反复确认。麻烦在于：带 {@code finalize} 的对象在被发现的那一刻会
     * 被 JVM <b>临时复活</b>（不然它跑不了终结器），于是那半程里
     * {@code WeakReference.get()} 还不是 null——「没有已死的」这个判断<b>说得
     * 太早</b>。机器被别的线程占满时，这条报信线程可能一整轮都没排上队。
     * <p>
     * 所以这里把两个条件<b>主动造出来</b>，而不是等 CI 偶尔繁忙：
     * <ul>
     *   <li>开 {@code 2×核数} 条忙等线程，让报信线程去抢时间片；</li>
     *   <li>压一批活数据进堆，让 {@code System.gc()} 这一轮本身就慢；</li>
     *   <li>反复几十遍，每遍都丢掉八个挂了 {@code __gc} 的表。</li>
     * </ul>
     * 判定用脚本里的 {@code assert(n == 8)}：少跑一个就是 Lua 错误，
     * 直接红，<b>不重试、不放宽、也不看机器脸色</b>。
     */
    @Test
    void collectFinishesEveryFinalizerUnderStarvation() {
        int cores = Runtime.getRuntime().availableProcessors();
        final AtomicBoolean go = new AtomicBoolean(true);
        List<Thread> burners = new ArrayList<>();
        for (int i = 0; i < cores * 2; i++) {
            Thread t = new Thread(() -> {
                long x = 1;
                while (go.get()) {
                    x = x * 6364136223846793005L + 1442695040888963407L;
                }
                if (x == 42L) {
                    System.out.println("impossible");
                }
            });
            t.setDaemon(true);
            t.setName("gcStarve-" + i);
            burners.add(t);
        }
        for (Thread t : burners) {
            t.start();
        }
        /*
         * 每遍之前压 8 MB 活数据进堆：不为别的，只为 {@code System.gc()} 别在
         * 一微秒内返回——它太快的时候，报信线程反而总能赶上，那条竞态就藏在
         * 「收集比报信快」的那个窗口里。
         */
        List<byte[]> hog = new ArrayList<>();
        try {
            for (int round = 0; round < 25; round++) {
                hog.add(new byte[8 * 1024 * 1024]);
                if (hog.size() > 24) {
                    hog.subList(0, 8).clear();
                }
                LuaState L = new LuaState();
                LuaLibs.openAll(L);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
                L.out = ps;
                L.err = ps;
                String out;
                try {
                    L.run(STRESS_SCRIPT);
                } catch (LuaError e) {
                    // 断言没跑齐就是这条竞态现形；输出（应当是空的）一并报出来
                    out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                    fail("round " + round + "：collectgarbage() 返回时 __gc 没跑齐 —— "
                            + errorMessage(e)
                            + (out.isEmpty() ? "" : "，输出：" + out));
                    return;
                }
                out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                if (!out.isEmpty()) {
                    fail("round " + round + "：不该有输出，拿到 " + out);
                }
                L.close();
            }
        } finally {
            go.set(false);
        }
    }

    /**
     * 八个表，全部丢掉，然后一次 {@code collectgarbage()} —— 少跑任何一个就是
     * 那条竞态。断言写在脚本里，错误消息带着实际个数。
     * <p>
     * 脚本本身只用 ASCII：{@link LuaState#run(String)} 走的是 Latin-1（约定 #9），
     * 这里塞中文会在<b>加载阶段</b>就抛 {@code IllegalArgumentException}，
     * 症状是「一条 Java 异常从 collectgarbage 附近冒出来」，跟被測的东西毫无关系。
     */
    private static final String STRESS_SCRIPT = """
            local n = 0
            for i = 1, 8 do
              local t = setmetatable({}, {__gc = function(o) n = n + 1 end})
            end
            collectgarbage()
            assert(n == 8, "finalizers ran: " .. n)
            """;

    /** 错误值摊成一行文本；带位置信息的那种本来就长这样 */
    private static String errorMessage(LuaError e) {
        LuaValue v = e.value;
        if (v == null) {
            return String.valueOf(e.getMessage());
        }
        String body = v.isString()
                ? new String(v.asString().bytes, StandardCharsets.UTF_8)
                : "(error object is a " + v.typeName() + " value)";
        return (e.where == null ? "" : e.where) + body;
    }
}
