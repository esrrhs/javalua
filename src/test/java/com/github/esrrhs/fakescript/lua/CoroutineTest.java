package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 协程的差分用例 —— 每个脚本都在参考实现与本实现上各跑一遍，逐字节比输出。
 * <p>
 * 协程是最容易「看起来对、其实差一点」的一块：yield 能穿过哪些边界、
 * resume 的参数怎么变成 yield 的返回值、出错时状态机怎么走，全都是靠这一批
 * 用例钉住的。写用例时有两条硬规则：
 * <ul>
 *   <li><b>每个脚本都要有输出</b>。{@link Diff#assertMatches} 会挡掉空输出，
 *       否则一个静默结束的脚本会假绿；</li>
 *   <li><b>协程必须会结束</b>。while true 的脚本会让两边一起挂住，
 *       测试永远跑不完。</li>
 * </ul>
 */
class CoroutineTest {

    @TestFactory
    List<DynamicTest> coroutineScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = SCRIPTS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("coroutine[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    /**
     * 第二批：<b>在元方法里 yield</b>。
     * <p>
     * 这批用例的价值不在「协程能不能用」，而在把 {@code luaV_finishOp} 的每个
     * 分支都跑到。一条指令执行到一半去调 Lua 代码、结果那里面 yield 了，
     * 协程恢复时得先把这条指令剩下的半步补完——比较类要决定跳不跳 JMP，
     * 算术类要写回目标寄存器，{@code OP_RETURN} / {@code OP_CLOSE} 要把 pc
     * 回退一格重跑。这些分支用普通脚本一条都碰不到，写漏了也不会红，
     * 只能靠这里钉住。
     */
    @TestFactory
    List<DynamicTest> yieldInsideMetamethods() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < DEEP.length; i++) {
            String src = DEEP[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("coroutineDeep[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    /**
     * 这批脚本不做逐行比对：错误位置前缀里的行号依赖 {@code luaL_where}，
     * 而本实现还没有完整的行号表回溯。只比第一行或整体结构。
     */
    @TestFactory
    List<DynamicTest> coroutineFirstLineMatches() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < FIRST_LINE.length; i++) {
            String src = FIRST_LINE[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("coroutineFirstLine[" + n + "]",
                    () -> Diff.assertFirstLineMatches(src)));
        }
        return tests;
    }

    /** 确认「空用例会被挡掉」这条护栏本身还在 */
    @TestFactory
    List<DynamicTest> vacuousCaseIsRejected() {
        String src = "local co = coroutine.create(function() end)\n";
        return List.of(DynamicTest.dynamicTest("vacuous", () -> {
            try {
                Diff.assertMatches(src);
                throw new AssertionError("空输出的用例本应被挡掉");
            } catch (AssertionError e) {
                assertEquals(true, e.getMessage().contains("没有产生任何输出"));
            }
        }));
    }

    private static final String[] SCRIPTS = {

            /* ---------- 基本生命周期 ---------- */

            """
            local co = coroutine.create(function(a, b)
              print("start", a, b)
              local x = coroutine.yield(a + b)
              print("got", x)
              return "end", x
            end)
            print(coroutine.status(co))
            print(coroutine.resume(co, 1, 2))
            print(coroutine.status(co))
            print(coroutine.resume(co, 10))
            print(coroutine.status(co))
            print(coroutine.resume(co))
            print(coroutine.status(co))
            """,

            """
            -- yield 出去多个值、resume 传进多个值
            local co = coroutine.create(function()
              print(coroutine.yield(1, 2, 3))
              return 9
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "a", "b"))
            """,

            """
            -- 不 yield、直接返回
            local co = coroutine.create(function() return 1, 2, 3 end)
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            """,

            """
            -- 主协程里 running 返回 nil
            print(coroutine.running())
            local co = coroutine.create(function()
              print(coroutine.running())
            end)
            coroutine.resume(co)
            """,

            """
            -- 初始状态是 suspended，不是 dead
            local co = coroutine.create(function() end)
            print(coroutine.status(co))
            coroutine.resume(co)
            print(coroutine.status(co))
            """,

            """
            -- 协程里看主协程的状态
            local main = nil
            local co = coroutine.create(function(m)
              print(coroutine.status(m))
              print(coroutine.status(coroutine.running()))
            end)
            coroutine.resume(co, coroutine.running())
            """,

            /* ---------- wrap ---------- */

            """
            local w = coroutine.wrap(function(a)
              local b = coroutine.yield(a * 2)
              return b .. "!"
            end)
            print(w(3))
            print(w("hi"))
            """,

            """
            local w = coroutine.wrap(function() return 1, 2 end)
            print(w())
            """,

            """
            -- wrap 出来的函数崩了会带位置，且会传播
            local w = coroutine.wrap(function() error("boom") end)
            print(pcall(w))
            """,

            """
            local w = coroutine.wrap(function() error({code = 42}) end)
            local ok, e = pcall(w)
            print(ok, type(e), e.code)
            """,

            /* ---------- isyieldable ---------- */

            """
            print(coroutine.isyieldable())
            local co = coroutine.create(function()
              print(coroutine.isyieldable())
              coroutine.yield()
            end)
            coroutine.resume(co)
            print(coroutine.isyieldable(co))
            """,

            /* ---------- yield 能穿过的边界（实测参考实现允许的） ---------- */

            """
            -- pcall 可以 yield
            local co = coroutine.create(function()
              local ok, v = pcall(function() return coroutine.yield(1) end)
              print("inner", ok, v)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 42))
            """,

            """
            -- xpcall 也可以 yield
            local co = coroutine.create(function()
              local ok, v = xpcall(function() return coroutine.yield(1) end, print)
              print("inner", ok, v)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 42))
            """,

            """
            -- 元方法可以 yield
            local co = coroutine.create(function()
              local t = setmetatable({}, {__index = function(_, k)
                return coroutine.yield(k)
              end})
              print("v", t.foo)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "V"))
            """,

            """
            -- for 的迭代器可以 yield
            local co = coroutine.create(function()
              local function iter(s, c)
                if c < 3 then return coroutine.yield(c + 1) end
              end
              for x in iter, nil, 0 do print("x", x) end
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 1))
            print(coroutine.resume(co, 2))
            print(coroutine.resume(co, 3))
            """,

            """
            -- 嵌套协程：在协程里 resume 另一个协程
            local inner = coroutine.create(function()
              coroutine.yield("i1")
              return "i2"
            end)
            local outer = coroutine.create(function()
              print(coroutine.resume(inner))
              print(coroutine.resume(inner))
              return "o"
            end)
            print(coroutine.resume(outer))
            """,

            """
            -- 在协程里 yield 出去，外层再 resume
            local co = coroutine.create(function()
              for i = 1, 3 do coroutine.yield(i) end
              return "fin"
            end)
            for i = 1, 4 do print(coroutine.resume(co)) end
            """,

            /* ---------- 不能 yield 的地方 ---------- */

            """
            print(pcall(function() return coroutine.yield(1) end))
            """,

            """
            local co = coroutine.create(function()
              local ok, err = pcall(function() coroutine.yield(1) end)
              print("no yield here", ok, err)
              return "done"
            end)
            print(coroutine.resume(co))
            """,

            /* ---------- 错误传播 ---------- */

            """
            local co = coroutine.create(function() error("boom") end)
            print(coroutine.resume(co))
            print(coroutine.status(co))
            print(coroutine.resume(co))
            """,

            """
            local co = coroutine.create(function()
              local x = nil
              return x.field
            end)
            print(coroutine.resume(co))
            """,

            """
            -- 错误对象非字符串时，resume 原样搬回来
            local co = coroutine.create(function() error({code = 7}) end)
            local ok, e = coroutine.resume(co)
            print(ok, type(e), e.code)
            """,

            """
            -- 协程里嵌套 pcall：内层 pcall 先吃掉错误
            local co = coroutine.create(function()
              print(pcall(function() error("inner") end))
              error("outer")
            end)
            print(coroutine.resume(co))
            """,

            """
            -- yield 之后才出错：走 precover 的恢复路径
            local co = coroutine.create(function()
              local a = coroutine.yield(1)
              error("after yield " .. tostring(a))
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "Z"))
            print(coroutine.status(co))
            """,

            """
            -- pcall 里 yield，恢复后出错
            local co = coroutine.create(function()
              local ok, err = pcall(function()
                coroutine.yield(1)
                error("in pcall")
              end)
              print("caught", ok, err)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            """,

            """
            -- xpcall 的处理函数在 yield 之后仍然生效
            local co = coroutine.create(function()
              local ok, err = xpcall(function()
                coroutine.yield(1)
                error("raw")
              end, function(m) return "H:" .. m end)
              print("caught", ok, err)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            """,

            /* ---------- close ---------- */

            """
            local co = coroutine.create(function() coroutine.yield(1) end)
            coroutine.resume(co)
            print(coroutine.close(co))
            print(coroutine.status(co))
            """,

            """
            local co = coroutine.create(function() return 1 end)
            coroutine.resume(co)
            print(pcall(coroutine.close, co))
            """,

            """
            local co = coroutine.create(function() coroutine.yield() end)
            coroutine.resume(co)
            -- 已挂起的协程可以关；正在跑的不行
            print(coroutine.close(co))
            """,

            """
            -- <close> 变量在协程被关掉时要跑 __close
            local log = {}
            local co = coroutine.create(function()
              local obj <close> = setmetatable({}, {__close = function()
                log[#log + 1] = "closed"
              end})
              coroutine.yield(1)
            end)
            coroutine.resume(co)
            print(coroutine.close(co))
            print(#log, log[1])
            """,

            """
            -- <close> 变量在协程出错返回时也要跑
            local log = {}
            local co = coroutine.create(function()
              local obj <close> = setmetatable({}, {__close = function(_, err)
                log[#log + 1] = "closed:" .. tostring(err)
              end})
              error("boom")
            end)
            print(coroutine.resume(co))
            print(#log, log[1])
            """,

            /* ---------- 参数错误 ---------- */

            """
            print(pcall(coroutine.resume, 1))
            print(pcall(coroutine.status, "x"))
            print(pcall(coroutine.create, 42))
            print(pcall(coroutine.wrap, nil))
            """,

            """
            print(pcall(coroutine.close, 1))
            print(pcall(coroutine.isyieldable, 1))
            """,

            /* ---------- 生成器惯用法 ---------- */

            """
            local function range(n)
              return coroutine.wrap(function()
                for i = 1, n do coroutine.yield(i) end
              end)
            end
            local sum = 0
            for x in range(5) do sum = sum + x end
            print(sum)
            """,

            """
            -- 递归协程（用协程实现树的遍历）
            local function walk(t)
              return coroutine.wrap(function()
                for _, v in ipairs(t) do
                  if type(v) == "table" then
                    for x in walk(v) do coroutine.yield(x) end
                  else
                    coroutine.yield(v)
                  end
                end
              end)
            end
            -- 用不了 table.concat / io.write：这两个属于标准库阶段，还没实现
            local out = ""
            for x in walk({1, {2, 3}, {4, {5}}}) do out = out .. tostring(x) .. " " end
            print(out)
            """,
    };

    /** yield 发生在元方法里、以及 {@code <close>} 里的一批用例 */
    private static final String[] DEEP = {

            """
            -- __add 里 yield：finishOp 的 OP_MMBIN 分支（结果要写回上一条指令的 A）
            local mt = {__add = function(a, b) return coroutine.yield("add") end}
            local co = coroutine.create(function()
              local x = setmetatable({}, mt) + 1
              print("res", x)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 100))
            """,

            """
            -- __concat 里 yield：finishOp 的 OP_CONCAT 分支
            local mt = {__concat = function(a, b) return coroutine.yield("cat") end}
            local co = coroutine.create(function()
              print(setmetatable({}, mt) .. "x")
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "C"))
            """,

            """
            -- __lt 里 yield：finishOp 要根据结果决定跳不跳紧跟的 JMP
            local mt = {__lt = function(a, b) return coroutine.yield("lt") end}
            local co = coroutine.create(function()
              local a, b = setmetatable({}, mt), setmetatable({}, mt)
              if a < b then print("lt") else print("not lt") end
              if a < b then print("lt2") else print("not lt2") end
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, true))
            print(coroutine.resume(co, false))
            """,

            """
            -- __le 用 __lt 顶替时结果要取反
            local mt = {__lt = function(a, b) return coroutine.yield("v") end}
            local co = coroutine.create(function()
              local a, b = setmetatable({}, mt), setmetatable({}, mt)
              print(a <= b)
              print(a <= b)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, true))
            print(coroutine.resume(co, false))
            """,

            """
            -- __eq 里 yield
            local mt = {__eq = function(a, b) return coroutine.yield("eq") end}
            local co = coroutine.create(function()
              local a, b = setmetatable({}, mt), setmetatable({}, mt)
              print(a == b)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, true))
            """,

            """
            -- __index 里 yield：finishOp 的 OP_GETFIELD / OP_GETTABLE 分支
            local mt = {__index = function(t, k) return coroutine.yield(k) end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              print(t.foo)
              print(t["bar"])
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "V1"))
            print(coroutine.resume(co, "V2"))
            """,

            """
            -- __newindex 里 yield
            local log = {}
            local mt = {__newindex = function(t, k, v) log[#log + 1] = coroutine.yield(k) end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              t.a = 1
              t.b = 2
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "X"))
            print(coroutine.resume(co, "Y"))
            print(#log, log[1], log[2])
            """,

            """
            -- __call 里 yield
            local mt = {__call = function(self, x) return coroutine.yield(x) end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              print(t(5))
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "called"))
            """,

            """
            -- __len / __unm 里 yield
            local mt = {__len = function() return coroutine.yield("len") end,
                        __unm = function() return coroutine.yield("unm") end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              print(#t)
              print(-t)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 7))
            print(coroutine.resume(co, -7))
            """,

            """
            -- <close> 的 __close 里 yield：finishOp 的 OP_RETURN 分支（pc 要回退）
            local log = {}
            local co = coroutine.create(function()
              local obj <close> = setmetatable({}, {__close = function()
                log[#log + 1] = coroutine.yield("closing")
              end})
              return "body"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "A"))
            print(#log, log[1])
            """,

            """
            -- <close> 的 __close 里连续 yield 多次
            local n = 0
            local co = coroutine.create(function()
              local a <close> = setmetatable({}, {__close = function() n = n + 1 end})
              local b <close> = setmetatable({}, {__close = function() coroutine.yield("b") end})
              return "body"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            print("n", n)
            """,

            """
            -- 作用域结束时的 OP_CLOSE 里 yield
            local co = coroutine.create(function()
              do
                local obj <close> = setmetatable({}, {__close = function()
                  coroutine.yield("scope")
                end})
              end
              print("after scope")
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            """,

            """
            -- 元方法里 yield，外面还套着 pcall
            local mt = {__index = function(_, k) return coroutine.yield(k) end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              print(pcall(function() return t.x end))
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "V"))
            """,

            """
            -- 元方法里 yield，恢复后元方法自己出错
            local mt = {__index = function(_, k)
              local v = coroutine.yield(k)
              error("mt failed")
            end}
            local co = coroutine.create(function()
              local t = setmetatable({}, mt)
              print(pcall(function() return t.x end))
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "V"))
            """,

            """
            -- resume 传的值比 yield 出去的多 / 少
            local co = coroutine.create(function()
              local a, b, c = coroutine.yield(1)
              print(a, b, c)
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, "x"))
            """,

            """
            local co = coroutine.create(function()
              print(coroutine.yield())
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.resume(co, 1, 2, 3))
            """,

            """
            -- 协程里再起协程并 yield 进去
            local co = coroutine.create(function()
              local inner = coroutine.create(function() return coroutine.yield("i") end)
              print(coroutine.resume(inner))
              print(coroutine.status(inner))
              print(coroutine.resume(inner, "R"))
              print(coroutine.status(inner))
              return "done"
            end)
            print(coroutine.resume(co))
            """,

            """
            -- __tostring 是被 print 用 C 调进去的，属于「不可 yield」的边界
            local co = coroutine.create(function()
              print(setmetatable({}, {__tostring = function()
                return coroutine.yield("t")
              end}))
              return "done"
            end)
            print(coroutine.resume(co))
            print(coroutine.status(co))
            """,

            """
            -- 协程里 resume 自己
            local co
            co = coroutine.create(function()
              print(coroutine.status(co))
              print(coroutine.resume(co))
            end)
            print(coroutine.resume(co))
            print(coroutine.status(co))
            """,

            """
            -- 协程里 close 自己
            local co
            co = coroutine.create(function()
              print(pcall(coroutine.close, co))
              return "done"
            end)
            print(coroutine.resume(co))
            """,

            """
            -- wrap 出来的函数在协程死后再次调用
            local w = coroutine.wrap(function() return 1 end)
            print(w())
            print(pcall(w))
            """,

            """
            -- 一个 wrap 内的递归 resume（用协程实现的一对一传递）
            local function passthrough(f)
              return coroutine.wrap(function(...)
                local args = table.pack(...)
                while true do
                  args = table.pack(coroutine.yield(f(table.unpack(args, 1, args.n))))
                end
              end)
            end
            local ok = pcall(passthrough, function() end)
            print(ok)
            """,
    };

    /** 只比第一行的用例（位置前缀细节尚未对齐） */
    private static final String[] FIRST_LINE = {

            """
            local w = coroutine.wrap(function() error("boom") end)
            w()
            """,

            """
            local co = coroutine.create(function() error("boom") end)
            local ok, e = coroutine.resume(co)
            print(ok)
            """,
    };
}
