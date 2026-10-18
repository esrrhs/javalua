package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
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
}
