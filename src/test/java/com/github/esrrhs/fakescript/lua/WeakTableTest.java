package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 弱表的差分用例 —— 阶段 6 的闸门。
 * <p>
 * 这批用例与之前几批有个本质区别：<b>它们依赖 JVM 真的把对象收掉</b>。
 * 参考实现那边靠 {@code collectgarbage()} 触发一次完整的 Lua GC，这边靠
 * {@code System.gc()}。开工前先量过可靠性：本机 JDK 17 上 500/500 次
 * {@code System.gc()} 都能清掉不可达对象的 {@code WeakReference}，
 * 带上 JaCoCo agent 也是 500/500，所以这批用例是可以进闸门的。
 * <p>
 * 反过来，如果哪天换了 JVM 参数（比如 {@code -XX:+DisableExplicitGC}）
 * 让 {@code System.gc()} 变成空操作，这里会成片变红——那正是它该做的反应：
 * 弱表语义已经不成立了。
 */
class WeakTableTest {

    @TestFactory
    List<DynamicTest> weakTableScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = SCRIPTS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("weak[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    /**
     * 不依赖收集时机的那一半：无论 JVM 收不收，这些规则都必须成立。
     * <p>
     * 单独列出来是有意义的——真正依赖收集的用例在环境异常时会成片变红，
     * 而这一半应当永远绿着。它们红了说明是弱表逻辑写错了，不是环境的问题。
     */
    @TestFactory
    List<DynamicTest> collectibleRulesWithoutCollection() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < NO_GC.length; i++) {
            String src = NO_GC[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("weakNoGc[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    private static final String[] NO_GC = {

            """
            -- 字符串 / 数字 / 布尔不会被弱表清掉（没有「显式构造」）
            local t = setmetatable({}, {__mode = "kv"})
            t["abc"] = "def"
            t[1] = true
            t[2.5] = 7
            collectgarbage()
            local n = 0
            for k in pairs(t) do n = n + 1 end
            print(n, t["abc"], t[1], t[2.5])
            """,

            """
            -- 还活着的键不会被清掉
            local keep = {}
            local t = setmetatable({}, {__mode = "k"})
            t[keep] = "v"
            collectgarbage()
            print(t[keep], (next(t)) ~= nil)
            """,

            """
            -- 还活着的值不会被清掉
            local keep = {}
            local t = setmetatable({}, {__mode = "v"})
            t[1] = keep
            collectgarbage()
            print(t[1] == keep)
            """,

            """
            -- 非法的 __mode 一律静默接受
            print(pcall(setmetatable, {}, {__mode = "x"}))
            print(pcall(setmetatable, {}, {__mode = 5}))
            print(pcall(setmetatable, {}, {__mode = ""}))
            print(pcall(setmetatable, {}, {__mode = "kvjk"}))
            """,

            """
            -- 弱表照常能读写、能遍历
            local t = setmetatable({}, {__mode = "kv"})
            for i = 1, 20 do t[i] = i * i end
            local n, sum = 0, 0
            for k, v in pairs(t) do n = n + 1 sum = sum + v end
            print(n, sum, t[5])
            """,

            """
            -- 函数也是可收集对象
            local f = function() return 1 end
            local t = setmetatable({}, {__mode = "v"})
            t.f = f
            print(t.f())
            collectgarbage()
            print(t.f())
            local g = function() return 2 end
            t.f = g
            print(t.f())
            """,
    };

    private static final String[] SCRIPTS = {

            """
            -- 弱键：键不再被引用 → 条目消失
            local t = setmetatable({}, {__mode = "k"})
            do
              local k = {}
              t[k] = "v"
            end
            print("before", next(t) ~= nil)
            collectgarbage()
            print("after", next(t))
            """,

            """
            -- 弱值：值不再被引用 → 条目消失
            local t = setmetatable({}, {__mode = "v"})
            do
              local v = {}
              t.x = v
            end
            collectgarbage()
            print(next(t))
            """,

            """
            -- 先建普通表、后设 __mode：实测参考实现里照样生效
            local t = {}
            local key = {}
            t[key] = "alive"
            print("before", t[key])
            setmetatable(t, {__mode = "k"})
            key = nil
            collectgarbage()
            print("after", next(t))
            """,

            """
            -- ephemeron：键只被自己的值引用时，仍然会被清掉
            local t = setmetatable({}, {__mode = "k"})
            do
              local k = {}
              t[k] = k
            end
            collectgarbage()
            print("ephemeron", next(t))
            """,

            """
            -- 弱键表里，值引用了键 → 键活下来（JVM 可达性与 Lua 可达性在这里一致）
            local t = setmetatable({}, {__mode = "k"})
            local holder
            do
              local k = {}
              holder = {k}      -- 值这一侧强引用 k
              t[k] = holder
            end
            collectgarbage()
            print("kept", next(t) ~= nil)
            """,

            """
            -- kv 都弱：键或值任一被回收就摘掉
            local t = setmetatable({}, {__mode = "kv"})
            do
              local k, v = {}, {}
              t[k] = v
            end
            collectgarbage()
            print(next(t))
            """,

            """
            -- 弱值表里的整数键（数组部分）也要能被回收
            local t = setmetatable({}, {__mode = "v"})
            for i = 1, 5 do t[i] = {i} end
            print("before", #t)
            collectgarbage()
            local n = 0
            for k in pairs(t) do n = n + 1 end
            print("after", n)
            """,

            """
            -- 部分条目活着、部分被回收
            local keep = {}
            local t = setmetatable({}, {__mode = "k"})
            t[keep] = "kept"
            do
              local dead = {}
              t[dead] = "dead"
            end
            collectgarbage()
            local n = 0
            for k in pairs(t) do n = n + 1 end
            print(n, t[keep])
            """,

            """
            -- 弱表当缓存用：命中与失效
            local cache = setmetatable({}, {__mode = "k"})
            local function get(t)
              local v = cache[t]
              if v == nil then
                v = "computed"
                cache[t] = v
              end
              return v
            end
            local a = {}
            print(get(a), get(a))
            local n = 0
            for k in pairs(cache) do n = n + 1 end
            print(n)
            a = nil
            collectgarbage()
            local m = 0
            for k in pairs(cache) do m = m + 1 end
            print(m)
            """,

            """
            -- 协程对象作为弱值
            local t = setmetatable({}, {__mode = "v"})
            do
              local co = coroutine.create(function() end)
              t.co = co
            end
            collectgarbage()
            print(next(t))
            """,

            """
            -- 连续两轮收集：第二轮不该再有问题
            local t = setmetatable({}, {__mode = "kv"})
            for i = 1, 10 do
              local k = {}
              t[k] = {i}
            end
            collectgarbage()
            collectgarbage()
            local n = 0
            for k in pairs(t) do n = n + 1 end
            print(n)
            """,

            """
            -- 遍历中途条目仍然活着：pairs 不该崩
            local t = setmetatable({}, {__mode = "v"})
            local keep = {}
            for i = 1, 10 do t[i] = keep end
            local n = 0
            for k, v in pairs(t) do n = n + 1 end
            print(n)
            collectgarbage()
            local m = 0
            for k, v in pairs(t) do m = m + 1 end
            print(m)
            """,

            """
            -- 弱表里放 nil 值：等于没有这一项
            local t = setmetatable({}, {__mode = "kv"})
            local k = {}
            t[k] = 1
            t[k] = nil
            print(next(t) == nil)
            """,

            """
            -- collectgarbage 返回数字；弱表照常是表、照常能取地址串
            print(type(collectgarbage()))
            local t = setmetatable({}, {__mode = "k"})
            print(type(t), type(tostring(t)))
            """,
    };
}
