package com.github.esrrhs.fakescript.lua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
/**
 * 库回调的异常行为矩阵 —— 阶段 12。
 * <p>
 * 为什么单开一类：刚抓到的那个崩溃（元方法里 yield 穿过 C 帧）属于一整族问题
 * ——「C 函数回调进 Lua，Lua 那边不规矩」。那一族里除了元方法，还有一批别的
 * 回调点：<b>table.sort 的比较函数</b>、<b>string.gsub 的替换值</b>、
 * <b>print / tostring 的 __tostring</b>、<b>泛型 for 的迭代器</b>。
 * 它们<b>共用</b>同一条「能不能 yield」的判据，也共用错误传播那一套。
 * <p>
 * 矩阵两维：回调点 × 不规矩形态，再乘两种外套（主线程 / 协程）。
 * <ul>
 *   <li><b>回调点</b>：sort 比较函数、gsub 函数替换、gsub 表替换（走
 *       {@code __index}）、{@code tostring} 的 {@code __tostring}、
 *       {@code print} 的 {@code __tostring}、泛型 for 迭代器。</li>
 *   <li><b>不规矩形态</b>：抛 LuaError（level 0/1/2）、抛非字符串对象
 *       （number / nil / table / boolean）、返回非布尔（sort）、
 *       返回非字符串（{@code __tostring}）、yield。</li>
 * </ul>
 * <p>
 * 两种外套给出两种答案，少一种都看不出续体写没写对：
 * <ul>
 *   <li><b>sort / gsub / print 的 __tostring</b> 走
 *       {@code luaD_callnoyield}（第 5 条），回调里 yield 一律报
 *       {@code attempt to yield across a C-call boundary}。</li>
 *   <li><b>泛型 for 的迭代器</b> 由 {@code OP_TFORCALL} 直接调，
 *       <b>不经过 C 帧</b>，所以这里<b>允许</b> yield —— 与上面正好相反。</li>
 * </ul>
 * <p>
 * <b>踩过的坑</b>：恒返回非 nil 的回调放进 {@code for ... in} 里，
 * 控制变量永远不会变成 nil，裸跑在主线程上就是死循环（亲手挂了一次，
 * 烧了 3 分钟）。所以这一类必须限次。
 *
 * @see LuaState#callNoYield
 * @see LuaTm#callTmMightYield
 */
class LibraryCallbackTest {

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.find() != null,
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    /** 库回调的异常行为矩阵（480 行） */
    @Test
    void misbehavingCallbacksMatchReference() {
        Diff.assertMatches("""
-- 库回调的异常行为矩阵
--
-- 为什么开这一片：刚抓到的那个崩溃（元方法里 yield 穿过 C 帧）属于一整族问题
-- ——「C 函数回调进 Lua，Lua 那边不规矩」。那一族里除了元方法，还有一批别的
-- 回调点：table.sort 的比较函数、string.gsub 的替换值、print/tostring 的
-- __tostring、泛型 for 的迭代器。它们<b>共用</b>同一条「能不能 yield」的判据
-- （luaT_callTMres 的 isLuacode 分支），也共用错误传播的那一套。
--
-- 矩阵两维：
--   回调点 × 不规矩的形态（抛错 / 抛非字符串 / 返回错类型 / yield）
-- 每个组合再跑两种外套：主线程（pcall 兜住）与协程内（连 resume 三轮）。

local function show(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

local function go(tag, f, ...)
    local r = table.pack(pcall(f, ...))
    local out = { tag, tostring(r[1]) }
    for i = 2, r.n do out[#out + 1] = show(r[i]) end
    print(table.concat(out, " "))
end

--[[
  两种外套。
  主线程那一趟确认「错误传播 + 位置前缀」；协程那一趟确认「yield 能不能穿过去」
  ——这正是上一片咬出崩溃的地方。
]]
local function runBoth(tag, build)
    go("M." .. tag, build)
    local co = coroutine.create(function() return pcall(build) end)
    for i = 1, 3 do
        local r = table.pack(coroutine.resume(co, "R" .. i))
        local out = { "C." .. tag, "r" .. i }
        for j = 1, r.n do out[#out + 1] = show(r[j]) end
        print(table.concat(out, " "))
    end
end

print("BEGIN")

--[[
  「不规矩」的回调：抛错、抛非字符串、返回错类型、yield。
  同一个表要在不同组合里反复用，所以每条都写成<b>工厂</b>——
  直接拿一个函数当多个回调会互相污染。
]]
local function badfns()
    return {
        { "err", function(a, b) error("boom") end },
        { "err0", function(a, b) error("boom", 0) end },
        { "err2", function(a, b) error("boom", 2) end },
        { "num", function(a, b) error(42) end },
        { "nil", function(a, b) error() end },
        { "tbl", function(a, b) error({}) end },
        { "bool", function(a, b) error(true) end },
        { "yield", function(a, b) return coroutine.yield("Y") end },
        { "retnil", function(a, b) return nil end },
        { "retnum", function(a, b) return 7 end },
        { "retstr", function(a, b) return "S" end },
        { "retbool", function(a, b) return true end },
        { "retnone", function(a, b) end },
        { "retmulti", function(a, b) return 1, 2, 3 end },
    }
end

for _, e in ipairs(badfns()) do
    local nm, bad = e[1], e[2]

    -- 1) table.sort 的比较函数
    runBoth("sort." .. nm, function()
        local t = { 5, 3, 1, 4, 2 }
        table.sort(t, bad)
        return table.concat(t, ",")
    end)

    -- 2) string.gsub 的替换值是函数
    runBoth("gsubf." .. nm, function()
        return string.gsub("abcabc", "b", bad)
    end)

    -- 3) string.gsub 的替换值是表（走 __index）
    runBoth("gsubt." .. nm, function()
        return string.gsub("abcabc", "b", setmetatable({}, { __index = bad }))
    end)

    -- 4) table.concat 遇上 __tostring
    runBoth("concat." .. nm, function()
        return table.concat({ setmetatable({}, { __tostring = bad }) }, ",")
    end)

    -- 5) tostring 的 __tostring
    runBoth("tostr." .. nm, function()
        return tostring(setmetatable({}, { __tostring = bad }))
    end)

    -- 6) print 的 __tostring
    runBoth("print." .. nm, function()
        print(setmetatable({}, { __tostring = bad }))
        return "after"
    end)

    -- 7) 字符串拼接的 __concat（数字侧）
    runBoth("concat2." .. nm, function()
        return 1 .. setmetatable({}, { __concat = bad })
    end)

    --[[
      8) 泛型 for 的迭代器。
      <b>必须限次</b>：像 retnum / retstr 这种恒返回非 nil 的回调，控制变量
      永远不会变成 nil，裸跑在主线程上就是死循环（刚亲手挂了一个）。
      而且 OP_TFORCALL 是直接调迭代器、不经过 C 帧，所以这里<b>允许</b>
      yield —— 与 table.sort 那种走 callNoYield 的回调正好相反。
    ]]
    runBoth("iter." .. nm, function()
        local acc = {}
        local i = 0
        for x in bad, "S", 0 do
            i = i + 1
            acc[#acc + 1] = tostring(x)
            if i >= 4 then break end
        end
        return table.concat(acc, ","), i
    end)
end

--[[
  单独补两组：比较函数返回<b>非布尔</b>，以及比较函数<b>不一致</b>。
  前者要报 "invalid order function for sorting"，后者算法不同结果就不同
  ——所以这一档也是「排序算法必须与参考实现一致」的闸门。
]]
local NONBOOL = {
    { "nil", function() return nil end },
    { "num", function() return 7 end },
    { "str", function() return "S" end },
    { "tbl", function() return {} end },
    { "zero", function() return 0 end },
}
for _, e in ipairs(NONBOOL) do
    local nm, bad = e[1], e[2]
    runBoth("nonbool." .. nm, function()
        local t = { 5, 3, 1, 4, 2 }
        table.sort(t, bad)
        return table.concat(t, ",")
    end)
end

-- 比较函数在遍历途中改表：算法不同则结果不同
runBoth("mutate", function()
    local t = { 5, 3, 1, 4, 2 }
    local n = 0
    table.sort(t, function(a, b)
        n = n + 1
        if n == 3 then t[#t + 1] = 99 end
        return a < b
    end)
    return table.concat(t, ",")
end)

print("END")
                """);
    }

    private LibraryCallbackTest() {
    }
}
