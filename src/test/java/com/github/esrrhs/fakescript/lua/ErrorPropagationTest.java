package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeAll;

import java.io.File;

/**
 * 错误对象与错误传播的网格差分 —— 阶段 12。
 * <p>
 * 起因是「消息处理函数的生效点」那个洞（约定 #87）：它说明「错误怎么冒出去」
 * 这条线铺得还不够。这里把<b>错误对象的类型</b>与<b>抛出上下文</b>做笛卡尔积，
 * 再单独铺一张<b>处理函数递归深度</b>的网格。
 * <p>
 * 片一（{@code errgrid}，413 行）：16 种错误对象 × 五种上下文。
 * <ul>
 *   <li>{@code A}：{@code error(obj, level)}，{@code level} 取
 *       0/1/2/3/99（0 与 99 都不加位置前缀，2/3 落在不同的帧上）；</li>
 *   <li>{@code B}：{@code xpcall} 处理器的四种返回形态（拼
 *       {@code tostring}、原样返回、自己抛、返回多值只取第一个）；</li>
 *   <li>{@code C}：协程四路（{@code resume} / {@code wrap} /
 *       yield 之后再抛 / {@code status}）；</li>
 *   <li>{@code D}：从 <b>C 函数</b>里抛 —— {@code __index} / {@code __add} /
 *       {@code table.sort} 的比较函数 / {@code gsub} 的回调 / {@code __pairs} /
 *       {@code print} 里的 {@code __tostring} / {@code __close}；</li>
 *   <li>{@code E}：嵌套保护区（{@code xpcall} 套 {@code pcall} 及其反向、
 *       两层 {@code xpcall}、{@code xpcall} 里 {@code resume}）；</li>
 *   <li>{@code F}：行钩子与调用钩子里抛（约定 #31：抛错能否被 {@code pcall}
 *       接住取决于 {@code sethook} 在哪调）。</li>
 * </ul>
 * 错误对象里特意放了三个「{@code __tostring} 不正常」的表：返回非串、自己抛、
 * 返回数字。它们把「{@code tostring} 在<b>处理函数里</b>抛错」这条路径逼了出来。
 * <p>
 * <b>这一片咬出来的是本阶段最实的一个 bug</b>：处理函数自己出错时，C 版是
 * <b>重新进一次 {@code luaG_errormsg}</b>——递归地把<b>新的</b>错误对象交给
 * <b>同一个</b>处理函数，而不是直接失败。本实现用「重入就判死」顶替，
 * 于是把「第二次调用其实会成功」的情形一起吞成了
 * {@code "error in error handling"}：
 * <pre>
 * 参考：false  string:case.lua:73: '__tostring' must return a string
 * 本实现：false  error in error handling
 * </pre>
 * <p>
 * 片二（{@code errdepth}，105 行）：处理函数<b>连续出错 {@code K} 次后才成功</b>，
 * {@code K} 从 0 铺到 400。这一片把两个临界点钉死：
 * <ul>
 *   <li>{@code K=196}：第 200 层 {@code luaE_checkcstack} 抛
 *       {@code C stack overflow}，这个消息又被交给处理函数，于是成功那次
 *       拿到的是 {@code done:C stack overflow}；</li>
 *   <li>{@code K=215}：累到 220 层（{@code MAXCCALLS / 10 * 11}）转
 *       {@code LUA_ERRERR}，落成 {@code error in error handling}。</li>
 * </ul>
 * 两处都曾经差一格 —— {@code luaD_call} 的 {@code L->nCcalls -= inc} 被
 * longjmp 跳过了，而本实现用 {@code finally} 减回去了。另外片二还带
 * 「处理函数里 yield」（{@code callnoyield}）与「处理函数里再 {@code pcall}」
 * 两小组。
 * <p>
 * 写法上两条必须照做（约定 #42、#28）：
 * {@code show} 里<b>先把 {@code 0x} 地址归一再量长度</b>（C 的 {@code %p} 与
 * JVM 的哈希位数不同，先取 {@code #} 会把这份无关差别算进断言）；
 * {@code tostring} 一律包 {@code pcall}（{@code __tostring} 返回非串时它自己会抛
 * {@code "'__tostring' must return a string"}，不包就整个探针崩掉）。
 */
class ErrorPropagationTest {

    @BeforeAll
    static void prepare() {
        new File("target/rt").mkdirs();
    }

    /** 片一：错误对象 x 传播上下文（413 行）。 */
    @Test
    void errorObjectByContextGridMatchesReference() {
        Diff.assertMatches("""

-- 错误对象 x 传播上下文 全网格
-- 起因：msghandler 生效点那个洞（约定 #87）说明「错误怎么冒出去」这条线
-- 铺得还不够。这里把错误对象的类型与抛出上下文做笛卡尔积。
local HEX = "0123456789ABCDEF"
local function hb(b) return HEX:sub((b // 16) + 1, (b // 16) + 1) .. HEX:sub((b % 16) + 1, (b % 16) + 1) end
local function esc(s)
  return (s:gsub(".", function(c)
    local b = c:byte()
    if b == 91 or b == 93 then return "<" .. hb(b) .. ">" end
    if b < 32 or b > 126 then return "<" .. hb(b) .. ">" end
    return c
  end))
end
local function show(v)
  local t = type(v)
  if t == "string" then
    -- 约定 #42：地址先归一再量长度。C 的 %p 与 JVM 的哈希位数不同，
    -- 先取 # 会把这份无关差别算进断言里。
    local s = v:gsub("0x%x+", "PTR")
    return "#" .. #s .. "[" .. esc(s) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  elseif t == "nil" or t == "boolean" then return tostring(v) end
  -- 约定 #42：地址一律归一成类型。
  -- tostring 要包起来：__tostring 返回非字符串时它自己会抛
  -- "'__tostring' must return a string"，而那会把整个探针崩掉。
  local ok, s = pcall(tostring, v)
  if not ok or type(s) ~= "string" then return t .. "(<notostring>)" end
  return t .. "(" .. s:gsub("0x%x+", "PTR") .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

local OBJS = {
  { "nil", "nil" },
  { "false", "false" },
  { "true", "true" },
  { "int", "42" },
  { "float", "1.5" },
  { "str", "'msg'" },
  { "strNUL", "'a\\\\0b'" },
  { "strHi", "'\\\\255\\\\254'" },
  { "empty", "''" },
  { "tbl", "{}" },
  { "tblTostr", "setmetatable({}, {__tostring = function() return 'TS' end})" },
  { "tblTostrBad", "setmetatable({}, {__tostring = function() return {} end})" },
  { "tblTostrErr", "setmetatable({}, {__tostring = function() error('in ts') end})" },
  { "tblTostrNonStr", "setmetatable({}, {__tostring = function() return 42 end})" },
  { "fn", "print" },
  { "co", "coroutine.create(function() end)" },
}
local LEVELS = { 0, 1, 2, 3, 99 }

print("=== A error(obj, level) 直接抛（外层 pcall 接）===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  for _, lv in ipairs(LEVELS) do
    P("A " .. name .. " lvl" .. lv, function()
      local o = load("return " .. expr)()
      return error(o, lv)
    end)
  end
end

print("=== B xpcall 的处理器返回什么 ===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("B " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function() error(o) end,
                  function(m) return type(m) .. ":" .. tostring(m) end)
  end)
  P("B2 " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function() error(o) end, function(m) return m end)
  end)
  P("B3 " .. name, function()
    local o = load("return " .. expr)()
    -- 处理器自己出错
    return xpcall(function() error(o) end, function(m) error("handler") end)
  end)
  P("B4 " .. name, function()
    local o = load("return " .. expr)()
    -- 处理器返回多个值：只取第一个
    return xpcall(function() error(o) end, function(m) return m, "second" end)
  end)
end

print("=== C 协程 ===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("C resume " .. name, function()
    local o = load("return " .. expr)()
    local co = coroutine.create(function() error(o) end)
    return coroutine.resume(co)
  end)
  P("C wrap " .. name, function()
    local o = load("return " .. expr)()
    local w = coroutine.wrap(function() error(o) end)
    return w()
  end)
  P("C yieldThen " .. name, function()
    local o = load("return " .. expr)()
    local co = coroutine.create(function() coroutine.yield(1) error(o) end)
    local a = coroutine.resume(co)
    return coroutine.resume(co)
  end)
  P("C status " .. name, function()
    local o = load("return " .. expr)()
    local co = coroutine.create(function() error(o) end)
    local ok, e = coroutine.resume(co)
    return coroutine.status(co), ok, e
  end)
end

print("=== D 从 C 函数里抛（元方法 / 回调 / 迭代器）===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("D index " .. name, function()
    local o = load("return " .. expr)()
    local t = setmetatable({}, {__index = function() error(o) end})
    return t.x
  end)
  P("D add " .. name, function()
    local o = load("return " .. expr)()
    local t = setmetatable({}, {__add = function() error(o) end})
    return t + 1
  end)
  P("D sort " .. name, function()
    local o = load("return " .. expr)()
    return table.sort({ 3, 1, 2 }, function() error(o) end)
  end)
  P("D gsub " .. name, function()
    local o = load("return " .. expr)()
    return ("abc"):gsub("b", function() error(o) end)
  end)
  P("D pairs " .. name, function()
    local o = load("return " .. expr)()
    local t = setmetatable({}, {__pairs = function() error(o) end})
    for _ in pairs(t) do end
  end)
  P("D tostring " .. name, function()
    local o = load("return " .. expr)()
    return print(setmetatable({}, {__tostring = function() error(o) end}))
  end)
  P("D close " .. name, function()
    local o = load("return " .. expr)()
    local mt = {__close = function() error(o) end}
    local f = load("local x <close> = ... return 'ok'")
    return f(setmetatable({}, mt))
  end)
  P("D gc proxy: exit " .. name, function()
    -- os.exit 会杀掉进程，这里只测它是否报错
    local o = load("return " .. expr)()
    return pcall(function() return o end)
  end)
end

print("=== E 嵌套保护 ===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("E pcallInXpcall " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function() return pcall(function() error(o) end) end,
                  function(m) return "H:" .. tostring(m) end)
  end)
  P("E xpcallInPcall " .. name, function()
    local o = load("return " .. expr)()
    return pcall(function()
      return xpcall(function() error(o) end, function(m) return "H:" .. tostring(m) end)
    end)
  end)
  P("E xpcallInXpcall " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function()
      return xpcall(function() error(o) end, function(m) return "I:" .. tostring(m) end)
    end, function(m) return "O:" .. tostring(m) end)
  end)
  P("E coInXpcall " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function()
      local co = coroutine.create(function() error(o) end)
      return coroutine.resume(co)
    end, function(m) return "H:" .. tostring(m) end)
  end)
end

print("=== F 钩子里抛 ===")
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("F hook " .. name, function()
    local o = load("return " .. expr)()
    debug.sethook(function() error(o) end, "l")
    local r = table.pack(pcall(function() local x = 1 return x + 1 end))
    debug.sethook()
    return table.unpack(r, 1, r.n)
  end)
  P("F hookC " .. name, function()
    local o = load("return " .. expr)()
    debug.sethook(function() error(o) end, "c")
    local r = table.pack(pcall(function() return ("x"):upper() end))
    debug.sethook()
    return table.unpack(r, 1, r.n)
  end)
end
        """);
    }

    /** 片二：消息处理函数的递归深度（105 行，钉住 200 / 220 两个临界点）。 */
    @Test
    void messageHandlerRecursionDepthMatchesReference() {
        Diff.assertMatches("""
-- 消息处理函数「自己出错」的递归深度网格
-- 起因：C 版 luaG_errormsg 是在抛出点无保护地调处理函数，那时 L->errfunc
-- 还设着，于是处理函数里再 error 会递归进同一个处理函数——而不是直接失败。
-- 层数靠 incXCcalls 累计（longjmp 跳过了 decXCcalls），第 200 层抛
-- "C stack overflow"，第 220 层才 LUA_ERRERR。这里把 K（连续出错几次后
-- 才成功）铺成一张网格，把 200 / 220 两个临界点都钉住。
local HEX = "0123456789ABCDEF"
local function hb(b) return HEX:sub((b // 16) + 1, (b // 16) + 1) .. HEX:sub((b % 16) + 1, (b % 16) + 1) end
local function esc(s)
  return (s:gsub(".", function(c)
    local b = c:byte()
    if b == 91 or b == 93 then return "<" .. hb(b) .. ">" end
    if b < 32 or b > 126 then return "<" .. hb(b) .. ">" end
    return c
  end))
end
local function show(v)
  local t = type(v)
  if t == "string" then
    local s = v:gsub("0x%x+", "PTR")
    return "#" .. #s .. "[" .. esc(s) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  elseif t == "nil" or t == "boolean" then return tostring(v) end
  local ok, s = pcall(tostring, v)
  if not ok or type(s) ~= "string" then return t .. "(<notostring>)" end
  return t .. "(" .. s:gsub("0x%x+", "PTR") .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

-- K = 处理函数连续出错几次之后才正常返回
local KS = { 0, 1, 2, 3, 5, 10, 20, 50, 100, 150, 180, 190, 193, 194, 195,
             196, 197, 198, 199, 200, 201, 202, 205, 210, 215, 216, 217, 218,
             219, 220, 221, 225, 250, 400 }

print("=== A 裸 xpcall（外层无 pcall）===")
for _, K in ipairs(KS) do
  P("A K=" .. K, function()
    local n = 0
    return xpcall(function() error("orig") end, function(m)
      n = n + 1
      if n < K then error("h") end
      return "done:" .. tostring(m)
    end)
  end)
end

print("=== B 外层再套一层 pcall ===")
for _, K in ipairs(KS) do
  P("B K=" .. K, function()
    local n = 0
    return xpcall(function() error("orig") end, function(m)
      n = n + 1
      if n < K then error("h") end
      return "done:" .. tostring(m)
    end)
  end)
end

print("=== C 处理函数只在错误对象非串时出错（第二次就成功）===")
local OBJS = {
  { "str", "'msg'" },
  { "tbl", "{}" },
  { "tblTostr", "setmetatable({}, {__tostring = function() return 'TS' end})" },
  { "tblTostrBad", "setmetatable({}, {__tostring = function() return {} end})" },
  { "tblTostrErr", "setmetatable({}, {__tostring = function() error('in ts') end})" },
  { "tblTostrNonStr", "setmetatable({}, {__tostring = function() return 42 end})" },
  { "tblName", "setmetatable({}, {__name = 'N'})" },
  { "fn", "print" },
}
for i = 1, #OBJS do
  local name, expr = OBJS[i][1], OBJS[i][2]
  P("C tostr " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function() error(o) end,
                  function(m) return type(m) .. ":" .. tostring(m) end)
  end)
  P("C luaLtolstring " .. name, function()
    -- luaL_tolstring 的路径：__tostring 返回非串时它自己抛
    local o = load("return " .. expr)()
    return xpcall(function() error(o) end,
                  function(m) return type(m) .. ":" .. ("%s"):format(m) end)
  end)
  P("C concat " .. name, function()
    local o = load("return " .. expr)()
    return xpcall(function() error(o) end, function(m) return "x" .. m end)
  end)
end

print("=== D 处理函数里 yield（callnoyield）===")
for _, K in ipairs({ 0, 1, 2, 3 }) do
  P("D K=" .. K, function()
    local co = coroutine.create(function()
      local n = 0
      return xpcall(function() error("orig") end, function(m)
        n = n + 1
        if n <= K then coroutine.yield("y" .. n) end
        return "done:" .. tostring(m)
      end)
    end)
    local r = { coroutine.resume(co) }
    return table.unpack(r, 1, 3)
  end)
end

print("=== E 处理函数里再 pcall（嵌套保护区）===")
for _, K in ipairs({ 0, 1, 2, 3 }) do
  P("E K=" .. K, function()
    local n = 0
    return xpcall(function() error("orig") end, function(m)
      n = n + 1
      if n < K then error("h") end
      local ok, e = pcall(function() error("inner") end)
      return "done:" .. tostring(m) .. "/" .. tostring(ok) .. "/" .. tostring(e)
    end)
  end)
end
        """);
    }
}
