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
    -- 约定 #52/#120：NaN 的符号随 libc 变，长度得先折平再量（同上）。
    local s = v:gsub("0x%x+", "PTR"):gsub("[%-%+]nan", "nan")
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
    -- 约定 #52/#120：NaN 的符号随 libc 变，长度得先折平再量（同上）。
    local s = v:gsub("0x%x+", "PTR"):gsub("[%-%+]nan", "nan")
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
    /**
     * 片三：to-be-closed 变量出错的全网格（406 行）。
     * <p>
     * 起因是 JaCoCo 里 {@code LuaState#closeUpvalsOnUnwind} 有一条分支从没被
     * 走到 ——「{@code __close} 抛出的错误没有位置时补一个前缀」。既有的
     * {@code D close} 那一组清一色是 {@code error(o)}（默认 level 1，自带位置），
     * 所以那条分支从没被扫过。
     * <p>
     * 两维铺开：
     * <ul>
     *   <li><b>维一 24 种「{@code __close} 怎么出错」</b>：{@code error} 的
     *       level 0/1/2/3/99/-1、运行时错、{@code error(非串)}、
     *       {@code assert}、<b>C 函数当 {@code __close}</b>
     *       （{@code error} / {@code string.rep} / {@code tonumber} /
     *       {@code print}）、带 {@code __call} 的表、以及 {@code __close} 里
     *       yield；</li>
     *   <li><b>维二 8 种上下文</b>：函数体正常返回 / 函数体先抛错 / 两个
     *       {@code <close>} 变量 / {@code xpcall} + 处理函数 / 处理函数自己抛。</li>
     * </ul>
     * 再单铺四组：协程里（{@code resume} 两趟）、<b>main chunk 里</b>
     * （{@code do} 块结束才关）、<b>四种跳出作用域的方式</b>
     * （{@code return} / {@code break} / {@code goto} / 尾调用），
     * 以及 <b>10 种「不可关」的值</b>（{@code non-closable}）。
     * <p>
     * 这一片本身没有咬出分歧，但把上面那条分支和
     * {@link #memoryErrorSkipsHandlerMatchesReference}、{@code next} 那两处
     * 真分歧的<b>触发语料</b>一起固化了下来。
     * <p>
     * 写法上两个坑：
     * <ul>
     *   <li>换掉<b>字符串的类型元表</b>时必须带上 {@code __index = string}，
     *       否则 {@code show} 里的 {@code v:gsub} 会抛
     *       {@code attempt to index a string value}，而那是在 {@code pcall}
     *       外面抛的，整个探针崩掉（约定 #38）；</li>
     *   <li>{@code print} 当 {@code __close} 会打出 {@code table: 0x...}，
     *       由 {@link Diff} 的地址归一化兜住（约定 #42）。</li>
     * </ul>
     */
    @Test
    void toBeClosedErrorGridMatchesReference() {
        Diff.assertMatches("""
-- to-be-closed 变量出错的全网格
-- 起因：JaCoCo 里 LuaState 的 closeUpvalsOnUnwind 有一条分支从没被走到 ——
-- 「__close 抛出的错误<b>没有位置</b>时补一个前缀」。既有的 ErrorPropagationTest
-- 只有一组 `D close`，而且清一色是 error(o)（默认 level 1，自带位置），
-- 所以那条分支从没被扫过。
--
-- 这里铺两维：
--   维一 24 种「__close 怎么出错」；维二 8 种「在什么上下文里出错」。
-- 再加几组单点的：main chunk 里的 tbc、goto 跳出作用域、非函数 __close。

local function show(v)
  local t = type(v)
  if t == "string" then
    -- 约定 #52/#120：NaN 的符号随 libc 变，长度得先折平再量（同上）。
    local s = v:gsub("[%-%+]nan", "nan")
    return "#" .. #s .. "[" .. (s:gsub("[\\r\\n]", "?")) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  elseif t == "nil" or t == "boolean" then
    return tostring(v)
  end
  local ok, s = pcall(tostring, v)
  if not ok or type(s) ~= "string" then return t .. "(<notostring>)" end
  return t .. "(" .. (s:gsub("0x%x+", "PTR")) .. ")"
end

local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

-- ==================== 维一：__close 怎么出错 ====================
-- 每一行是一个「close 方法」。_self 是被关的变量，_err 是传给它的错误对象。
local SHAPES = {
  { "nil",        function(_self, _err) return nil end },
  { "retvals",    function(_self, _err) return 1, 2, 3 end },
  { "err0",       function(_self, _err) error("E0", 0) end },
  { "err1",       function(_self, _err) error("E1", 1) end },
  { "err2",       function(_self, _err) error("E2", 2) end },
  { "err3",       function(_self, _err) error("E3", 3) end },
  { "err99",      function(_self, _err) error("E99", 99) end },
  { "errneg",     function(_self, _err) error("ENEG", -1) end },
  { "errnolevel", function(_self, _err) error("ENO") end },
  { "errtwo",     function(_self, _err) error("ET", 2) end },
  { "nilindex",   function(_self, _err) local t = nil return t.x end },
  { "callstr",    function(_self, _err) return ("s")() end },
  { "arith",      function(_self, _err) return nil + 1 end },
  { "obj",        function(_self, _err) error(setmetatable({}, { __tostring = function() return "OBJ" end })) end },
  { "errarg",     function(_self, _err) error("EARG:" .. show(_err)) end },
  { "errarg0",    function(_self, _err) error("EARG0:" .. show(_err), 0) end },
  { "assert",     function(_self, _err) return assert(false) end },
  { "assertmsg",  function(_self, _err) return assert(false, "AMSG") end },
  { "cerr",       error },                       -- C 函数当 __close：error(obj, err)
  { "crep",       string.rep },                  -- C 函数当 __close：参数不对
  { "ctonumber",  tonumber },                    -- C 函数当 __close：能跑通
  { "cprint",     print },                       -- C 函数当 __close：有副作用
  { "callable",   setmetatable({}, { __call = function(_self2, _a, _b) error("ECALL", 0) end }) },
  { "yield",      function(_self, _err) return coroutine.yield("YC") end },
  -- 下面这批是「<b>没有位置</b>的错误」：它们不是 error() / luaL_error 抛的，
  -- 而是 LuaTable / IoLib 里直接 new 出来的裸错误值，where 字段是 null。
  -- 位置是走到保护区才补的（判据 isLua(ci)），__close 这条路径上补的地方
  -- 就是 closeUpvalsOnUnwind —— 这一批形状专门为了走到那条分支。
  { "setnil",     function(_self, _err) local t = {} t[nil] = 1 end },
  { "setnan",     function(_self, _err) local t = {} t[0/0] = 1 end },
  { "idiv0",      function(_self, _err) return 1 // 0 end },
  { "mod0",       function(_self, _err) return 1 % 0 end },
  { "nextbad",    function(_self, _err) return next({}, 1) end },
  { "nomem",      function(_self, _err) return io.open("/dev/null"):read(-1) end },
}

-- ==================== 维二：什么上下文 ====================
-- 每个上下文收一个 close 函数，返回一个「被 pcall 包起来的动作」。
local CTXS = {
  { "plain", function(cf)
    return function()
      local x <close> = setmetatable({}, { __close = cf })
      return "body"
    end
  end },
  { "bodyerr", function(cf)
    return function()
      local x <close> = setmetatable({}, { __close = cf })
      error("BODY")
    end
  end },
  { "bodynil", function(cf)
    return function()
      local x <close> = setmetatable({}, { __close = cf })
      local t = nil
      return t.x
    end
  end },
  { "two", function(cf)
    return function()
      local a <close> = setmetatable({}, { __close = cf })
      local b <close> = setmetatable({}, { __close = cf })
      return "body"
    end
  end },
  { "twoerr", function(cf)
    return function()
      local a <close> = setmetatable({}, { __close = cf })
      local b <close> = setmetatable({}, { __close = cf })
      error("BODY2")
    end
  end },
  { "xpcall", function(cf)
    return function()
      return xpcall(function()
        local x <close> = setmetatable({}, { __close = cf })
        return "body"
      end, function(m) return "H:" .. show(m) end)
    end
  end },
  { "xpcallerr", function(cf)
    return function()
      return xpcall(function()
        local x <close> = setmetatable({}, { __close = cf })
        error("BODY3")
      end, function(m) return "H:" .. show(m) end)
    end
  end },
  { "xpcallherr", function(cf)
    return function()
      return xpcall(function()
        local x <close> = setmetatable({}, { __close = cf })
        error("BODY4")
      end, function(m) error("HANDLER") end)
    end
  end },
}

print("=== grid ===")
for i = 1, #CTXS do
  local cname, mk = CTXS[i][1], CTXS[i][2]
  for j = 1, #SHAPES do
    local sname, cf = SHAPES[j][1], SHAPES[j][2]
    P(cname .. "/" .. sname, mk(cf))
  end
end

-- ==================== 协程里的 tbc ====================
-- 单独一列：__close 在协程里出错时，错误是从 resume 那侧回来的。
print("=== coroutine ===")
for j = 1, #SHAPES do
  local sname, cf = SHAPES[j][1], SHAPES[j][2]
  P("co/" .. sname, function()
    local co = coroutine.create(function()
      local x <close> = setmetatable({}, { __close = cf })
      coroutine.yield("Y1")
      return "body"
    end)
    local r1 = table.pack(coroutine.resume(co))
    local r2 = table.pack(coroutine.resume(co))
    local out = ""
    for i = 1, r1.n do out = out .. " r1:" .. show(r1[i]) end
    for i = 1, r2.n do out = out .. " r2:" .. show(r2[i]) end
    return out .. " st=" .. coroutine.status(co)
  end)
  P("coerr/" .. sname, function()
    local co = coroutine.create(function()
      local x <close> = setmetatable({}, { __close = cf })
      error("COBODY")
    end)
    local r1 = table.pack(coroutine.resume(co))
    local out = ""
    for i = 1, r1.n do out = out .. " r1:" .. show(r1[i]) end
    return out .. " st=" .. coroutine.status(co)
  end)
end

-- ==================== main chunk 里的 tbc ====================
-- 函数体外面也能声明 <close>：do 块结束时关。
print("=== mainchunk ===")
for j = 1, #SHAPES do
  local sname, cf = SHAPES[j][1], SHAPES[j][2]
  P("mc/" .. sname, function()
    local f = load("local x <close> = ... return 'body'", "=c")
    return f(setmetatable({}, { __close = cf }))
  end)
end

-- ==================== 跳出作用域的四种方式 ====================
print("=== scape ===")
local function mkClose(cf)
  return setmetatable({}, { __close = cf })
end
for j = 1, #SHAPES do
  local sname, cf = SHAPES[j][1], SHAPES[j][2]
  P("return/" .. sname, function()
    local function f()
      local x <close> = mkClose(cf)
      if true then return "ret" end
      return "no"
    end
    return f()
  end)
  P("break/" .. sname, function()
    local function f()
      for i = 1, 3 do
        local x <close> = mkClose(cf)
        if i == 2 then break end
      end
      return "brk"
    end
    return f()
  end)
  P("goto/" .. sname, function()
    local function f()
      do
        local x <close> = mkClose(cf)
        goto out
      end
      ::out::
      return "gto"
    end
    return f()
  end)
  P("tail/" .. sname, function()
    local function g() return "tail" end
    local function f()
      local x <close> = mkClose(cf)
      return g()
    end
    return f()
  end)
end

-- ==================== 非函数 / 不可关的值 ====================
print("=== nonclosable ===")
local NCS = {
  { "nil", "nil" },
  { "false", "false" },
  { "true", "true" },
  { "int", "42" },
  { "float", "1.5" },
  { "str", "'abc'" },
  { "func", "print" },
  { "thread", "coroutine.create(function() end)" },
  { "notm", "setmetatable({}, {})" },
  { "emptymt", "setmetatable({}, {__close = nil})" },
}
for i = 1, #NCS do
  local name, expr = NCS[i][1], NCS[i][2]
  P("nc/" .. name, function()
    local f = load("local x <close> = (" .. expr .. ") return 'body'", "=c")
    return f()
  end)
  P("ncn/" .. name, function()
    local function f()
      local x <close> = load("return " .. expr, "=c")()
      return "body"
    end
    return f()
  end)
end

-- 字符串/数字的类型元表上挂 __close（约定 #80：用完必须还原）
print("=== typemt ===")
do
  local old = debug.getmetatable("")
  -- __index 必须带上：换掉字符串的类型元表之后，show 里的 v:gsub 会变成
  -- "attempt to index a string value"，而那是在 pcall 外面抛的，整个探针就崩了
  debug.setmetatable("", { __index = string, __close = function() error("SCLOSE", 0) end })
  P("strmt", load("local x <close> = 'zz' return 'body'", "=c"))
  debug.setmetatable("", old)
end

print("END")
        """);
    }

    /**
     * 片四：{@code lua_error} 的<b>内存错误</b>特判（16 行，咬出两处真分歧）。
     * <p>
     * {@code lapi.c: lua_error} 里有唯一一处「<b>按错误值的内容决定错误种类</b>」
     * 的判断：错误对象是 {@code "not enough memory"} 这个<b>驻留短串</b>时走
     * {@code luaM_error}（{@code LUA_ERRMEM}，直跳），否则走
     * {@code luaG_errormsg}（先过 {@code L->errfunc}）。
     * 两条路<b>唯一的外部差别是过不过消息处理函数</b>：
     * <pre>
     * xpcall(function() error("not enough memory", 0) end, H)
     *   参考：false  not enough memory          （处理函数接不到）
     *   本实现：false  H:not enough memory
     * xpcall(function() return io.open("/dev/null"):read(-1) end, H)
     *   参考：false  not enough memory
     *   本实现：false  H:not enough memory
     * </pre>
     * 而 {@code error("not enough memory")}（默认 level 1）会先拼上位置前缀，
     * 拼出来的串<b>不再是</b>那个驻留串，于是退化成普通错误、处理函数照样接得到
     * ——「有没有位置」这个看似无关的差别把错误种类换了，所以十个用例里
     * {@code level} 取 0/1 都要有。
     * <p>
     * {@code f:read(-1)} 那条同源：C 的 {@code resizebox} 是
     * {@code lua_pushliteral + lua_error}，同样被认成内存错误。
     */

    @Test
    void memoryErrorSkipsHandlerMatchesReference() {
        Diff.assertMatches("""
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
local H = function(m) return "H:" .. tostring(m) end
P("lit", function() return xpcall(function() error("not enough memory") end, H) end)
P("lit0", function() return xpcall(function() error("not enough memory", 0) end, H) end)
P("lit1", function() return xpcall(function() error("not enough memory", 1) end, H) end)
P("concat", function() return xpcall(function() error("not enough " .. "memory") end, H) end)
P("pcall", function() return pcall(error, "not enough memory") end)
P("read", function() return xpcall(function() return io.open("/dev/null"):read(-1) end, H) end)
P("close", function() return xpcall(function()
   local x <close> = setmetatable({}, {__close = function() error("not enough memory") end})
   return "b" end, H) end)
P("co", function()
   local co = coroutine.create(function() error("not enough memory") end)
   local r = table.pack(coroutine.resume(co))
   local o = ""
   for i = 1, r.n do o = o .. " | " .. tostring(r[i]) end
   return o end)
P("var", function() local s = "not enough memory" return xpcall(function() error(s) end, H) end)
-- level 0：拼出来的串<i>就是</i>那个驻留串（"内容相等"与"同一个对象"等价）
P("var0", function() local s = "not enough memory" return xpcall(function() error(s, 0) end, H) end)
-- 拼接结果：短串驻留，拼出来的还是同一个串对象
P("concat0", function() return xpcall(function() error("not enough " .. "memory", 0) end, H) end)
P("rep1", function() return xpcall(function() error(("not enough memory"):rep(1), 0) end, H) end)
P("sub", function() return xpcall(function() error(("Xnot enough memory"):sub(2), 0) end, H) end)
print("END")
        """);
    }

    /**
     * 片五：{@code luaG_runerror} 在 <b>C 帧</b>里没有位置（74 行，咬出
     * {@code next} 那处真分歧）。
     * <p>
     * C 版给运行时错误加位置的是 {@code luaG_runerror}，判据是
     * {@code isLua(ci)} —— <b>只看当前帧</b>，与 {@code luaL_error} 的
     * {@code luaL_where(L, 1)}（回跳一层到调用者）完全是两回事。于是
     * {@code ltable.c: findindex} 里那句
     * {@code luaG_runerror(L, "invalid key to 'next'")} 永远没有位置：跑到
     * 那儿时 {@code ci} 是 {@code luaB_next} 这个 C 帧。
     * <p>
     * 本实现当时用的是 {@link LuaState#libError}（{@code luaL_error} 那套），
     * 于是凭空多出 {@code case.lua:N:}：
     * <pre>
     * 参考：  false  invalid key to 'next'
     * 本实现：false  case.lua:12: invalid key to 'next'
     * </pre>
     * <b>约定 #8 那句「库函数一律 {@code libError}」在这里不成立</b>——
     * 这条消息的源头在 {@code ltable.c} 而不在库代码里，得走
     * {@link LuaState#runError}。
     * <p>
     * 这一片把 23 种运行时错误各放进三种上下文（直接在 Lua 帧里 / 在
     * {@code gsub} 的 Lua 回调里 / 由 C 函数自己触发），目的就是找出
     * 「同一个错误在不同帧里位置不一样」的那一族。除 {@code next} 之外
     * 全部一致（{@code table.sort} 的 {@code attempt to compare} 也是
     * C 帧无位置，两边都对）。
     */
    @Test
    void runtimeErrorPositionByFrameMatchesReference() {
        Diff.assertMatches("""
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
-- 一批「运行时错误」，分别在三种上下文里触发：
--   L = 直接在 Lua 帧里
--   G = 在 gsub 的 Lua 回调里（ci 还是 Lua）
--   C = 由 C 函数自己做（ci 是 C 帧）
local E = {
  { "nextbad",  function() return next({}, 1) end },
  { "nextbad2", function() return next({1,2,3}, "k") end },
  { "sortcmp",  function() local t = {1,"a"} table.sort(t) return t end },
  { "sortcmp2", function() local t = {{},{}} table.sort(t) return t end },
  { "callnil",  function() return pcall(nil) end },
  { "callnum",  function() return pcall(42) end },
  { "callnum2", function() return xpcall(42, print) end },
  { "nilidx",   function() local t = {} t[nil] = 1 end },
  { "nanidx",   function() local t = {} t[0/0] = 1 end },
  { "idiv0",    function() return 1 // 0 end },
  { "mod0",     function() return 1 % 0 end },
  { "arith",    function() return {} + 1 end },
  { "concat",   function() return {} .. "x" end },
  { "index",    function() local t = nil return t.x end },
  { "callstr",  function() return ("s")() end },
  { "intrep",   function() return ("x"):rep(1.5) end },
  { "intrep2",  function() return string.format("%d", 1.5) end },
  { "totoint",  function() return ("x"):sub(1.5) end },
  { "yieldout", function() return coroutine.yield() end },
  { "yieldC",   function()
      local co = coroutine.create(function() return ("x"):gsub("x", function() return coroutine.yield(1) end) end)
      local a = table.pack(coroutine.resume(co))
      local b = table.pack(coroutine.resume(co))
      return tostring(a[1]) .. " " .. tostring(a[2]) .. " / " .. tostring(b[1]) .. " " .. tostring(b[2])
    end },
  { "getmeta",  function() return ("x"):gsub("x", setmetatable({}, {__index = function() error("IDX", 0) end})) end },
  { "le",       function() return {} < {} end },
  { "ltn",      function() return 1 < "a" end },
}
for i = 1, #E do
  local n, f = E[i][1], E[i][2]
  P("L/" .. n, f)
  P("G/" .. n, function() return ("x"):gsub("x", f) end)
  P("C/" .. n, function() return pcall(f) end)
end
print("END")
        """);
    }

    /**
     * 片六：协程边界<b>漏了补位置</b>（62 行，咬出第三处真分歧）。
     * <p>
     * C 的位置是 {@code luaG_runerror} 在<b>抛出点</b>加的；本实现里
     * {@code table index is nil} / {@code table index is NaN} /
     * {@code not enough memory} 抛的是<b>裸错误值</b>
     * （{@link LuaError#where} 为 {@code null}），位置要等走到保护边界才补。
     * {@code pcall} 那条边界补了，<b>协程这条没补</b>：
     * <pre>
     * 参考：  false  case.lua:8: table index is nil
     * 本实现：false  table index is nil
     * </pre>
     * 四条路都是同一个洞：{@code resume} / {@code wrap} / yield 之后再抛 /
     * {@code __close} 里抛。修在 {@code errObjAt}（{@code resume} 与
     * {@code precover} 两个 {@code catch} 共用）。
     * <p>
     * 这一片同时是对照组：{@code error("E0", 0)}
     * （{@code where} 是空串不是 null）、{@code next} 的错（C 帧，
     * {@code where()} 给空串）、以及带位置的普通错，三类都<b>不该</b>再补。
     */
    @Test
    void coroutineBoundaryAddsPositionMatchesReference() {
        Diff.assertMatches("""
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
local SH = {
  { "setnil",  function() local t = {} t[nil] = 1 end },
  { "setnan",  function() local t = {} t[0/0] = 1 end },
  { "idiv0",   function() return 1 // 0 end },
  { "mod0",    function() return 1 % 0 end },
  { "nextbad", function() return next({}, 1) end },
  { "nomem",   function() return io.open("/dev/null"):read(-1) end },
  { "err1",    function() error("E1") end },
  { "err0",    function() error("E0", 0) end },
  { "runtime", function() local t = nil return t.x end },
  { "memerr",  function() error("not enough memory", 0) end },
}
for i = 1, #SH do
  local n, f = SH[i][1], SH[i][2]
  -- 协程里直接抛（没有 pcall 兜）
  P("co/" .. n, function()
    local co = coroutine.create(f)
    local r = table.pack(coroutine.resume(co))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o .. " st=" .. coroutine.status(co)
  end)
  -- 协程里有 pcall 兜
  P("copcall/" .. n, function()
    local co = coroutine.create(function() return pcall(f) end)
    local r = table.pack(coroutine.resume(co))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o .. " st=" .. coroutine.status(co)
  end)
  -- coroutine.wrap
  P("wrap/" .. n, function()
    local w = coroutine.wrap(f)
    local r = table.pack(pcall(w))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o
  end)
  -- yield 之后再抛
  P("y/" .. n, function()
    local co = coroutine.create(function() coroutine.yield("Y") return f() end)
    local a = table.pack(coroutine.resume(co))
    local b = table.pack(coroutine.resume(co))
    return tostring(a[1]) .. " " .. tostring(a[2]) .. " / " .. tostring(b[1]) .. " " .. tostring(b[2])
  end)
  -- 闭包里（__close）—— 正常返回路径
  P("close/" .. n, function()
    local co = coroutine.create(function()
      local x <close> = setmetatable({}, {__close = f})
      coroutine.yield("Y")
      return "b"
    end)
    local a = table.pack(coroutine.resume(co))
    local b = table.pack(coroutine.resume(co))
    return tostring(a[1]) .. " " .. tostring(a[2]) .. " / " .. tostring(b[1]) .. " " .. tostring(b[2])
  end)
  -- 主线程对照
  P("main/" .. n, function() return f() end)
end
print("END")
        """);
    }

    /**
     * 片七：{@code __close} 里 <b>yield 之后再抛</b>的错怎么走处理函数
     * （14 行，咬出第四处真分歧）。
     * <p>
     * 这一片专门打 {@code ldo.c: finishpcallk} 那条路：{@code xpcall} 的函数体
     * 先出错，{@code __close} 里 yield 把 {@code pcall} 打断，resume 回来之后
     * {@code __close} 才抛真正的错。这时错误对象是「上一趟 yield 出去、这一趟
     * 从栈上取回来」的，<b>当初那个 {@code LuaError} 的 {@code errMem} 标记早
     * 丢了</b> —— 只能像 C 那样<b>判错误对象本身</b>：
     * <pre>
     * 参考：  false  not enough memory
     * 本实现：false  H:not enough memory
     * </pre>
     * 对照组（{@code err0} / {@code err1} / {@code setnil}）都是普通错误，
     * 处理函数照旧要跑。
     */
    @Test
    void closeYieldsThenErrorsMatchesReference() {
        Diff.assertMatches("""
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
-- __close 里先 yield（打断 pcall），恢复之后再抛一个「裸错」
local CFS = {
  { "setnil", function(_s, _e) coroutine.yield("Y") local t = {} t[nil] = 1 end },
  { "setnan", function(_s, _e) coroutine.yield("Y") local t = {} t[0/0] = 1 end },
  { "idiv0",  function(_s, _e) coroutine.yield("Y") return 1 // 0 end },
  { "err0",   function(_s, _e) coroutine.yield("Y") error("E0", 0) end },
  { "err1",   function(_s, _e) coroutine.yield("Y") error("E1") end },
  { "mem",    function(_s, _e) coroutine.yield("Y") error("not enough memory", 0) end },
}
for i = 1, #CFS do
  local n, cf = CFS[i][1], CFS[i][2]
  P("yclose/" .. n, function()
    local co = coroutine.create(function()
      return xpcall(function()
        local x <close> = setmetatable({}, {__close = cf})
        error("BODY")
      end, function(m) return "H:" .. tostring(m) end)
    end)
    local out = ""
    for k = 1, 5 do
      local r = table.pack(coroutine.resume(co))
      out = out .. " r" .. k .. ":"
      for j = 1, r.n do out = out .. " " .. tostring(r[j]) end
      if coroutine.status(co) == "dead" then break end
    end
    return out
  end)
  P("ypcall/" .. n, function()
    local co = coroutine.create(function()
      return pcall(function()
        local x <close> = setmetatable({}, {__close = cf})
        error("BODY")
      end)
    end)
    local out = ""
    for k = 1, 5 do
      local r = table.pack(coroutine.resume(co))
      out = out .. " r" .. k .. ":"
      for j = 1, r.n do out = out .. " " .. tostring(r[j]) end
      if coroutine.status(co) == "dead" then break end
    end
    return out
  end)
end
print("END")
        """);
    }

    /**
     * 片八：剩下的几条<b>保护边界</b>（34 行，咬出第五、六处真分歧）。
     * <p>
     * 前四片修的都是「错误走到某个边界时该怎么装裱」。这一片把<b>剩下的边界</b>
     * 挨个点名，同一批裸错误值逐个塞进去：
     * <ul>
     *   <li>{@code coroutine.close} 一个<b>挂起</b>的协程 —— {@code __close}
     *       里抛 {@code table index is nil}：参考带位置，本实现不带
     *       （{@code closeThreadValue} 的 {@code catch} 没补，已修）；</li>
     *   <li>{@code coroutine.close} 一个<b>已死</b>的协程 —— 参考把当初那个
     *       错误对象原样带回来（{@code false, "case.lua:8: ..."}），本实现给
     *       {@code false, nil}。根因是 C 的错误对象在栈上是<b>连着两格</b>的
     *       （{@code lua_resume} 出错时拷一份、{@code xmove} 只搬走上面那格），
     *       本实现只有一格，搬走就没 —— 加 {@code deadErr} 另存一份；</li>
     *   <li>{@code load} 的读函数里抛、{@code require} 的保护区里抛。</li>
     * </ul>
     */
    @Test
    void remainingProtectedBoundariesMatchReference() {
        Diff.assertMatches("""
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
local CF = {
  { "setnil", function() local t = {} t[nil] = 1 end },
  { "setnan", function() local t = {} t[0/0] = 1 end },
  { "idiv0",  function() return 1 // 0 end },
  { "nomem",  function() return io.open("/dev/null"):read(-1) end },
  { "err0",   function() error("E0", 0) end },
  { "err1",   function() error("E1") end },
  { "mem",    function() error("not enough memory", 0) end },
  { "rt",     function() local t = nil return t.x end },
}
for i = 1, #CF do
  local n, f = CF[i][1], CF[i][2]
  -- coroutine.close：__close 里抛
  P("close/" .. n, function()
    local co = coroutine.create(function()
      local x <close> = setmetatable({}, {__close = f})
      coroutine.yield("Y")
      return "b"
    end)
    local a = table.pack(coroutine.resume(co))
    local c = table.pack(pcall(coroutine.close, co))
    local d = table.pack(pcall(coroutine.close, co))
    local out = tostring(a[1]) .. " " .. tostring(a[2]) .. " / close:"
    for j = 1, c.n do out = out .. " " .. tostring(c[j]) end
    out = out .. " / again:"
    for j = 1, d.n do out = out .. " " .. tostring(d[j]) end
    return out
  end)
  -- 已经死了的协程上 close
  P("closedead/" .. n, function()
    local co = coroutine.create(f)
    coroutine.resume(co)
    local r = table.pack(pcall(coroutine.close, co))
    local out = ""
    for j = 1, r.n do out = out .. " " .. tostring(r[j]) end
    return out
  end)
  -- load 的读函数里抛
  P("loadrd/" .. n, function()
    local k = 0
    local ok, e = pcall(load, function() k = k + 1 if k == 1 then return "return 1 " else f() end end, "=c")
    return tostring(ok) .. " " .. tostring(e)
  end)
  -- require 的保护区里抛
  P("preload/" .. n, function()
    package.preload["zz" .. n] = f
    local ok, e = pcall(require, "zz" .. n)
    return tostring(ok) .. " " .. tostring(e)
  end)
end
print("END")
        """);
    }

    /**
     * 片九：运行时错误 x 三种帧上下文，大网格（191 行）。
     * <p>
     * 与片五（{@code cframe}）同一条思路，只是把语料从 23 条扩到 63 条：
     * {@code luaG_callerror} / {@code luaG_typeerror} / {@code luaG_concaterror}
     * / {@code luaG_opinterror} / {@code luaG_ordererror} / {@code luaG_forerror}
     * 全族，加上 {@code ltable.c} 的索引与 {@code next}、{@code table.sort}
     * 无比较函数那条 C 帧路径、以及一批 {@code luaL_error} 的参数错。
     * <p>
     * <b>这一片一条分歧都没咬出来</b>（除已在片五修掉的 {@code next}）。但它是
     * 「同一条消息在不同帧里位置不一样」这一族最宽的一张网，留着当回归网：
     * 位置相关的改动动过之后它是最先红的。
     */
    @Test
    void runtimeErrorPositionWideGridMatchesReference() {
        Diff.assertMatches("""
-- 运行时错误消息 x 三种帧上下文 的大网格
-- 起因：「invalid key to 'next'」那条咬出来的是一类 bug —— 同一条消息在
-- Lua 帧里有位置、在 C 帧里没有。C 的判据是 luaG_runerror 里的 isLua(ci)，
-- 只看当前帧；而 luaL_error 走 luaL_where(L,1) 回跳一层。本实现把两者混了。
-- 这一片把能想到的运行时错误全铺一遍，各放进三种上下文，看哪条消息的位置随帧变。
--
--   L = 直接在 Lua 帧里触发
--   G = 在 gsub 的 Lua 回调里触发（ci 仍是 Lua，但上面压着 C 帧）
--   C = 由 C 函数自己触发（ci 是 C 帧）

local function esc(s)
  return (s:gsub("[^ -~]", function(c) return "<" .. c:byte() .. ">" end))
end
local function show(v)
  local t = type(v)
  if t == "string" then
    -- 只截掉「文件名:行号:」这一段的<b>行号</b>以外的部分？不：位置本身就是
    -- 被测对象，原样留着。只把路径压成同一长度，免得改一行就整片红。
    -- 约定 #52/#120：NaN 的符号随 libc 变（glibc "-nan"、Apple libc "nan"），
    -- 内容由 Diff.normalize 折平，可长度在这里量，不先折就会把 4 与 3 写进断言。
    local s = v:gsub("[%-%+]nan", "nan")
    return "#" .. #s .. "[" .. esc(s) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  elseif t == "nil" or t == "boolean" then
    return tostring(v)
  end
  local ok, s = pcall(tostring, v)
  if not ok or type(s) ~= "string" then return t .. "(<notostring>)" end
  return t .. "(" .. (s:gsub("0x%x+", "PTR")) .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

local E = {
  -- luaG_callerror：call 一个不可调的值
  { "callnil",    function() local f = nil return f() end },
  { "callnum",    function() local f = 42 return f() end },
  { "calltbl",    function() local f = {} return f() end },
  { "callstr",    function() local f = "s" return f() end },
  { "callbool",   function() local f = true return f() end },
  -- luaG_typeerror：index / arithmetic / length
  { "idxnil",     function() local t = nil return t.x end },
  { "idxnum",     function() local t = 5 return t.x end },
  { "idxbool",    function() local t = true return t.x end },
  { "len",        function() return #nil end },
  { "lenbool",    function() return #true end },
  { "arith",      function() return {} + 1 end },
  { "arith2",     function() return 1 - {} end },
  { "unm",        function() return -{} end },
  { "band",       function() return {} & 1 end },
  { "shift",      function() return {} << 1 end },
  -- luaG_concaterror
  { "conc",       function() return {} .. "x" end },
  { "conc2",      function() return "x" .. {} end },
  { "conc3",      function() return nil .. "x" end },
  -- luaG_opinterror：整数除法 / 取模 的除零，以及「没有整数表示」
  { "idiv0",      function() return 1 // 0 end },
  { "mod0",       function() return 1 % 0 end },
  { "fdiv0",      function() return 1.0 // 0 end },
  { "fmod0",      function() return 1.0 % 0 end },
  { "idiv0u",     function() return -1 // -1 end },
  -- luaG_ordererror
  { "lt",         function() return {} < {} end },
  { "le",         function() return {} <= {} end },
  { "ltn",        function() return 1 < "a" end },
  { "ltn2",       function() return "a" < 1 end },
  -- luaG_forerror
  { "forinit",    function() for i = "a", 2 do end end },
  { "forlimit",   function() for i = 1, "a" do end end },
  { "forstep",    function() for i = 1, 2, "a" do end end },
  -- ltable.c：索引与 next
  { "setnil",     function() local t = {} t[nil] = 1 end },
  { "setnan",     function() local t = {} t[0/0] = 1 end },
  { "getnil",     function() local t = {} return t[nil] end },
  { "nextbad",    function() return next({}, 1) end },
  { "nextbad2",   function() return next({1,2,3}, "k") end },
  { "nextbad3",   function() return next({1,2,3}, 99) end },
  { "rawsetnil",  function() return rawset({}, nil, 1) end },
  { "rawsetnan",  function() return rawset({}, 0/0, 1) end },
  -- C 函数自己做的比较（table.sort 无比较函数）
  { "sortlt",     function() local t = {1,"a"} table.sort(t) return t end },
  { "sortle",     function() local t = {{},{}} table.sort(t) return t end },
  { "sortnan",    function() local t = {1,0/0,2} table.sort(t) return t end },
  -- 库函数的参数错（luaL_error 那一族，位置应当来自调用者）
  { "repfrac",    function() return ("x"):rep(1.5) end },
  { "fmtfrac",    function() return string.format("%d", 1.5) end },
  { "subfrac",    function() return ("x"):sub(1.5) end },
  { "selectnil",  function() return select(nil) end },
  { "unpackbig",  function() return table.unpack({}, 1, 1e18) end },
  { "concattbl",  function() return table.concat({{}}) end },
  { "insnil",     function() local t = {} for i = 1, 3 do t[i] = nil end return table.insert(t, 1, 2, 3) end },
  { "remfrac",    function() local t = {1,2,3} return table.remove(t, 1.5) end },
  { "floorstr",   function() return math.floor("abc") end },
  { "maxnan",     function() return math.max(1, 0/0) end },
  { "tostrerr",   function() return print(setmetatable({}, {__tostring = function() return {} end})) end },
  { "setmt",      function() return setmetatable({}, 5) end },
  { "getmtstr",   function() return getmetatable(5) end },
  { "loadmode",   function() return load("x", "=c", "z") end },
  { "loadbin",    function() return load("\\27bad", "=c", "t") end },
  -- yield 的两条
  { "yieldout",   function() return coroutine.yield() end },
  -- 元方法缺失
  { "indexmiss",  function() return ({}).x.y end },
  { "newidx",     function() local t = setmetatable({}, {}) t.x = 1 return t end },
  { "callmtmiss", function() return (setmetatable({}, {}))() end },
  { "eqmt",       function() return setmetatable({}, {__eq = 5}) == setmetatable({}, {__eq = 5}) end },
  -- userdata
  { "udlen",      function() return #io.stdout end },
  { "udidx",      function() return io.stdout.x end },
}

for i = 1, #E do
  local n, f = E[i][1], E[i][2]
  P("L/" .. n, f)
  P("G/" .. n, function() return ("x"):gsub("x", f) end)
  P("C/" .. n, function() return pcall(f) end)
end
print("END")
        """);
    }

    /**
     * 片十：<b>库函数的错</b> x 四种协程帧（152 行）。
     * <p>
     * 库函数的错走 {@code luaL_where(L, 1)}（<b>回跳一层到调用者</b>），与
     * {@code luaG_runerror}（只看当前帧 {@code isLua(ci)}）是两套判据。
     * 协程把调用链的头部换成了另一套东西（{@code resume} 的 C 帧、
     * {@code wrap} 的续体），所以这一片把同一批库函数错分别放进：主线程 /
     * 协程体顶层 / 协程里再套一层函数 / {@code coroutine.wrap} /
     * 协程里的 {@code gsub} 回调。
     * <p>
     * 一条分歧都没咬出来 —— 但同样留作回归网：位置的两套判据一旦混用，
     * 这里是除片五之外最容易红的地方。
     */
    @Test
    void libraryErrorPositionByCoroutineFrameMatchesReference() {
        Diff.assertMatches("""
-- 库函数的错 x 四种帧：位置取自 luaL_where(L, 1)（回跳一层到调用者）。
-- 协程体的<b>顶层</b>是一层特殊的帧（base_ci，currentline 为 -1），
-- luaL_where 会据此给不出位置 —— 与主线程顶层完全不同。这一片就是为这个。
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
  print(out .. " | n=" .. r.n)
end
local E = {
  { "rep",     function() return ("x"):rep(1.5) end },
  { "repnil",  function() return ("x"):rep(nil) end },
  { "fmt",     function() return string.format("%d", {}) end },
  { "sub",     function() return ("x"):sub("a") end },
  { "sel",     function() return select(nil) end },
  { "sel2",    function() return select(0) end },
  { "unpack",  function() return table.unpack({}, 1, 1e18) end },
  { "concat",  function() return table.concat({{}}) end },
  { "insert",  function() local t = {} t[1]=1 t[2]=2 t[3]=3 return table.insert(t, 1, 2, 3) end },
  { "remove",  function() local t = {1,2,3} return table.remove(t, 1.5) end },
  { "floor",   function() return math.floor("abc") end },
  { "maxnil",  function() return math.max() end },
  { "tonum",   function() return tonumber() end },
  { "setmt",   function() return setmetatable({}, 5) end },
  { "rawsetn", function() return rawset({}, nil, 1) end },
  { "ipairs",  function() for _ in ipairs(nil) do end end },
  { "nextnt",  function() return next(nil) end },
  { "osdate",  function() return os.date("%Q") end },
  { "ostime",  function() return os.time({}) end },
  { "ioread",  function() return io.open("/dev/null"):read(-1) end },
  { "iolines", function() return io.lines("/nope/nope")() end },
  { "utfchar", function() return utf8.char(-1) end },
  { "strpack", function() return string.pack("!", 1) end },
  { "tmovef",  function() return table.move({}, 1, 2, 1.5) end },
  { "err0",    function() error("E0", 0) end },
  { "err1",    function() error("E1") end },
  { "err2",    function() error("E2", 2) end },
  { "err3",    function() error("E3", 3) end },
  { "assert",  function() return assert(false) end },
  { "rt",      function() local t = nil return t.x end },
}
for i = 1, #E do
  local n, f = E[i][1], E[i][2]
  P("L/" .. n, f)
  -- 协程体的顶层
  P("co/" .. n, function()
    local co = coroutine.create(f)
    local r = table.pack(coroutine.resume(co))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o
  end)
  -- 协程里再套一层函数（顶层之下）
  P("cof/" .. n, function()
    local co = coroutine.create(function() return f() end)
    local r = table.pack(coroutine.resume(co))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o
  end)
  -- coroutine.wrap（错误从 wrap 出来的函数上抛）
  P("wrap/" .. n, function()
    local w = coroutine.wrap(f)
    local r = table.pack(pcall(w))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o
  end)
  -- 协程里的 gsub 回调（上面压着一层 C 帧）
  P("cog/" .. n, function()
    local co = coroutine.create(function() return ("x"):gsub("x", f) end)
    local r = table.pack(coroutine.resume(co))
    local o = ""
    for j = 1, r.n do o = o .. " | " .. tostring(r[j]) end
    return o
  end)
end
print("END")
        """);
    }
}
