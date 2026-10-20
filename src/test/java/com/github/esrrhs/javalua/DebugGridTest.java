package com.github.esrrhs.javalua;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeAll;

import java.io.File;

/**
 * {@code debug} 库的全网格差分 —— 阶段 12。
 * <p>
 * 为什么单开一类：{@code DebugTest} 只有 39 个用例，而 {@code debug} 是本项目里
 * 唯一「往回看自己的栈」的模块——<b>栈形状</b>、<b>帧种类</b>、<b>钩子时机</b>
 * 三条线在这里交汇（约定 #28/#31/#32）。手挑用例盖不住
 * 「帧种类 × 层数 × 选项字母」这个三维。
 * <p>
 * 三条用例：
 * <ul>
 *   <li><b>主网格</b>（559 行，16 段）：
 *     <ul>
 *       <li>{@code A}–{@code E}：{@code getinfo} 的 18 种选项（含 {@code nil}
 *           取默认值、{@code >} 与 {@code x} 两种非法选项）× 九个层号 × 六种
 *           帧（主 chunk / 一层 Lua / 三层嵌套 / C 函数回调 {@code gsub}·
 *           {@code sort}·{@code pcall} / 尾调用 / 方法调用 / {@code __index}·
 *           {@code __add} / {@code for} 迭代器）；</li>
 *       <li>{@code F}：协程里看自己、以及把 {@code co} 当第一个参数看别人；</li>
 *       <li>{@code G}–{@code H}：{@code getinfo(函数)} 七种函数，以及错误参数；</li>
 *       <li>{@code I}–{@code J}：{@code getlocal}/{@code setlocal} 与
 *           {@code getupvalue}/{@code setupvalue} 的<b>下标全扫</b>（-3…6 与
 *           -2…5），含 vararg、数值 {@code for}、泛型 {@code for}、
 *           {@code (C temporary)}、写临时寄存器；</li>
 *       <li>{@code K}：{@code traceback} 的九种消息 × 五个 level，
 *           <b>回溯本身逐字节比</b>；</li>
 *       <li>{@code L}：{@code getmetatable}/{@code setmetatable}/
 *           {@code getuservalue}/{@code setuservalue}/{@code getregistry}
 *           在九种值上；</li>
 *       <li>{@code M}：钩子——12 种 mask × count，钩子里读 {@code getinfo(2)/(3)}，
 *           {@code gethook} 三元组，钩子里 yield；</li>
 *       <li>{@code N}：协程第二遍；</li>
 *       <li>{@code O}：{@code chunkid} 边界——12 种 chunkname（含 {@code \0}、
 *           {@code 0xFF}、58/60/100 字符）在 {@code source}/{@code short_src}/
 *           位置前缀/回溯四个出口上（约定 #84）；</li>
 *       <li>{@code P}：{@code <close>}/{@code <const>} 的局部名、
 *           长函数的 {@code activelines}。</li>
 *     </ul>
 *   </li>
 *   <li><b>行钩子里抛错</b>与<b>调用钩子里抛错</b>各一条：主线程上这是
 *       <b>致命</b>的（约定 #31——{@code pcall} 接不住，脚本当场终止），
 *       所以各自单独成条；塞进主网格里会把后面几百行全吃掉。</li>
 * </ul>
 * <p>
 * 三条都走 {@link Diff#assertFullMatches}：<b>回溯属于预期输出</b>
 * （{@code debug.traceback} 的返回值、钩子崩掉后的回溯），
 * {@code assertMatches} 会从第一个 {@code stack traceback:} 处把后面全砍掉。
 * <p>
 * 两个必须在写同类探针时记住的坑：
 * <ul>
 *   <li><b>{@code nil} 不能直接放进 {@code ipairs} 的表里</b>——首元素是
 *       {@code nil} 时 {@code ipairs} 就地停住，整段静默不跑。用 {@code "NIL"}
 *       哨兵 + {@code if w == "NIL" then return nil end}；写成
 *       {@code (w == "NIL") and nil or w} 也不行，Lua 的 {@code and/or} 习语
 *       在中间值为 {@code nil} 时会返回 {@code w}。</li>
 *   <li><b>{@code debug.setmetatable} 用完必须还原成原值</b>（约定 #80）。
 *       这里的第一版还成 {@code nil}，于是字符串的类型元表被摘掉，后面所有
 *       {@code ("x"):gsub(...)} 全部 {@code attempt to index a string value}
 *       ——探针自己把自己崩了。</li>
 * </ul>
 * <p>
 * <b>这一片一条分歧都没咬出来</b>——铺之前担心、铺完确认干净：{@code debug}
 * 这一块的栈形状与 C 版是一致的。
 */
class DebugGridTest {

    @BeforeAll
    static void prepare() {
        new File(Diff.SCRATCH).mkdirs();
    }

    /** 主网格：16 段 559 行。 */
    @Test
    void debugLibraryGridMatchesReference() {
        Diff.assertFullMatches("""
-- debug 库全网格：getinfo / getlocal / upvalue / traceback / 钩子 / 元表
-- 起因：DebugTest 只有 39 个用例，而 debug 是本项目里唯一「往回看自己的栈」
-- 的模块——栈形状、帧种类、钩子时机三条都在这里交汇（约定 #28/#31/#32）。
-- 手挑用例盖不住「帧种类 × 层数 × 选项字母」这个三维。
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
-- 约定 #52/#120：NaN 的符号随 libc 变（glibc 打 "-nan"、Apple libc 打 "nan"），
-- C99 把这份留作 unspecified。内容由 Diff.normalize 折平，可长度是在这里量的
-- —— 不先折一次，同一行在 Linux 上就是 #4[nan]、在 macOS 上是 #3[nan]。
local function norm(s) return (s:gsub("0x%x+", "PTR"):gsub("[%-%+]nan", "nan")) end
local function show(v)
  local t = type(v)
  if t == "string" then
    local s = norm(v)
    return "#" .. #s .. "[" .. esc(s) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  elseif t == "nil" or t == "boolean" then return tostring(v) end
  if t == "table" then
    -- 约定 #43：键序不可比，一律排序后再打
    local nk, sk = {}, {}
    for k in next, v do
      if type(k) == "number" then nk[#nk + 1] = k else sk[#sk + 1] = tostring(k) end
    end
    table.sort(nk)
    table.sort(sk)
    local out = {}
    for _, k in ipairs(nk) do out[#out + 1] = k .. "=" .. show(v[k]) end
    for _, k in ipairs(sk) do out[#out + 1] = k .. "=" .. show(v[k]) end
    return "{" .. table.concat(out, " ") .. "}"
  end
  local ok, s = pcall(tostring, v)
  if not ok or type(s) ~= "string" then return t .. "(<notostring>)" end
  return t .. "(" .. norm(s) .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

-- debug.getinfo 的字段按固定顺序打（约定 #43：不能 pairs）
local FIELDS = { "source", "short_src", "linedefined", "lastlinedefined", "what",
                 "currentline", "name", "namewhat", "istailcall", "nups",
                 "nparams", "isvararg", "ftransfer", "ntransfer",
                 "activelines", "func" }
local function gi(a, b)
  local ok, info = pcall(debug.getinfo, a, b)
  if not ok then return "ERR:" .. show(info) end
  if info == nil then return "nil" end
  local out = {}
  for _, k in ipairs(FIELDS) do
    if info[k] ~= nil then out[#out + 1] = k .. "=" .. show(info[k]) end
  end
  return table.concat(out, " ")
end

local WHATS = { "NIL", "", "S", "l", "u", "n", "t", "r", "L", "f",
                "flnSrtu", "nSl", "uL", "ft", "x", "Sx", ">", "Sl>" }
-- "NIL" 是哨兵：选项参数要传真 nil，而表里放真 nil 会让 ipairs 就地停住
local function opt(w)
  -- 不能写 (w == "NIL") and nil or w：and/or 习语在中间值为 nil 时会返回 w
  if w == "NIL" then return nil end
  return w
end
local LEVELS = { 0, 1, 2, 3, 4, 5, 6, 7, 100 }

-- 把「各层」铺开：从当前位置往上数九层
local function levels(what)
  local out = {}
  for _, lv in ipairs(LEVELS) do out[#out + 1] = lv .. ":" .. gi(lv, what) end
  return table.concat(out, " || ")
end

print("=== A 各层 × 各选项：主 chunk ===")
for _, w in ipairs(WHATS) do
  P("A " .. tostring(w), function() return levels(opt(w)) end)
end

print("=== B 各层 × 各选项：一层 Lua 函数里 ===")
local function inFn1(w) return levels(opt(w)) end
for _, w in ipairs(WHATS) do
  P("B " .. tostring(w), function() return inFn1(w) end)
end

print("=== C 三层嵌套 Lua 函数 ===")
local function inFn3(w) return levels(opt(w)) end
local function inFn2(w) return inFn3(w) end
local function inFn1b(w) return inFn2(w) end
for _, w in ipairs(WHATS) do
  P("C " .. tostring(w), function() return inFn1b(w) end)
end

print("=== D 从 C 函数的回调里（gsub / sort / gmatch）===")
for _, w in ipairs(WHATS) do
  P("D gsub " .. tostring(w), function()
    return ("abc"):gsub("b", function() return levels(opt(w)) end)
  end)
  P("D sort " .. tostring(w), function()
    local r
    table.sort({ 2, 1 }, function(x, y) r = levels(w) return x < y end)
    return r
  end)
  P("D pcall " .. tostring(w), function()
    return pcall(function() return levels(opt(w)) end)
  end)
end

print("=== E 尾调用位置 / 方法调用 / 元方法 / for 迭代器 ===")
local function tailTarget(w) return levels(opt(w)) end
for _, w in ipairs(WHATS) do
  P("E tail " .. tostring(w), function() return (function() return tailTarget(w) end)() end)
  P("E method " .. tostring(w), function()
    local obj = setmetatable({}, { __index = { m = function(self) return levels(opt(w)) end } })
    return obj:m()
  end)
  P("E meta " .. tostring(w), function()
    local t = setmetatable({}, { __index = function() return levels(opt(w)) end })
    return t.x
  end)
  P("E metaadd " .. tostring(w), function()
    local t = setmetatable({}, { __add = function() return levels(opt(w)) end })
    return t + 1
  end)
  P("E iter " .. tostring(w), function()
    local it = function(s, c)
      if c < 1 then return levels(opt(w)) end
      return nil
    end
    for v in it, nil, 0 do return v end
  end)
end

print("=== F 协程里：主线程 / 自身 / 互看 ===")
for _, w in ipairs(WHATS) do
  P("F co " .. tostring(w), function()
    local co = coroutine.create(function() return levels(opt(w)) end)
    return select(2, coroutine.resume(co))
  end)
  P("F wrap " .. tostring(w), function()
    local w2 = coroutine.wrap(function() return levels(opt(w)) end)
    return w2()
  end)
end
-- 协程作为第一个参数：看它自己的栈
local COGRID = {}
for _, w in ipairs({ "NIL", "", "S", "l", "n", "u", "flnSrtu" }) do
  P("F coarg " .. tostring(w), function()
    local co = coroutine.create(function() coroutine.yield(1) return "done" end)
    coroutine.resume(co)
    local out = {}
    for lv = 0, 3 do out[#out + 1] = lv .. ":" .. gi(co, lv, opt(w)) end
    return table.concat(out, " || ")
  end)
end

print("=== G getinfo(函数) ===")
local function plain(a, b) return a + b end
local function vararg(a, ...) return a, select("#", ...) end
local up1 = 7
local function withUps() return up1 end
local FNCASES = {
  { "lua", plain }, { "vararg", vararg }, { "ups", withUps },
  { "print", print }, { "pcall", pcall }, { "methods", string.format },
  { "mainchunk", nil },
}
for i = 1, #FNCASES do
  local name, f = FNCASES[i][1], FNCASES[i][2]
  if name == "mainchunk" then
    for _, w in ipairs({ "NIL", "", "S", "l", "u", "n", "flnSrtu", "L" }) do
      P("G main " .. tostring(w), function() return gi(debug.getinfo(1, "f").func, opt(w)) end)
    end
  else
    for _, w in ipairs({ "NIL", "", "S", "l", "u", "n", "flnSrtu", "L", "f" }) do
      P("G " .. name .. " " .. tostring(w), function() return gi(f, opt(w)) end)
    end
  end
end

print("=== H getinfo 的错误参数 ===")
local BAD = { "nil", "true", "42", "1.5", "'x'", "{}", "print", "1.0", "-1", "0" }
for i = 1, #BAD do
  local a = BAD[i]
  P("H a1 " .. a, function() return debug.getinfo(load("return " .. a)()) end)
  P("H a2 " .. a, function() return debug.getinfo(1, load("return " .. a)()) end)
  P("H f2 " .. a, function() return debug.getinfo(print, load("return " .. a)()) end)
end
P("H n0", function() return debug.getinfo() end)
P("H n3", function() return debug.getinfo(1, "S", 3) end)
P("H neg", function() return debug.getinfo(-1) end)

print("=== I getlocal / setlocal ===")
local function localGrid(tag)
  local a1, a2 = 10, 20
  local function inner(...)
    local b1 = 1
    local out = {}
    for i = -3, 6 do
      local ok, n, v = pcall(debug.getlocal, 1, i)
      out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
    end
    for i = -3, 6 do
      local ok, n, v = pcall(debug.getlocal, 2, i)
      out[#out + 1] = "L2." .. i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
    end
    return table.concat(out, " ")
  end
  return inner(1, 2, 3)
end
P("I locals", function() return localGrid("x") end)
P("I vararg", function()
  local function f(...)
    local out = {}
    for i = -3, 4 do
      local ok, n, v = pcall(debug.getlocal, 1, i)
      out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
    end
    return table.concat(out, " ")
  end
  return f("a", "b")
end)
P("I fornum", function()
  local out = {}
  for i = 1, 2 do
    for k = -3, 6 do
      local ok, n, v = pcall(debug.getlocal, 1, k)
      out[#out + 1] = k .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
    end
    break
  end
  return table.concat(out, " ")
end)
P("I forgen", function()
  local out = {}
  for _, v in ipairs({ 1 }) do
    for k = -3, 6 do
      local ok, n, v2 = pcall(debug.getlocal, 1, k)
      out[#out + 1] = k .. "=" .. (ok and (tostring(n) .. "/" .. show(v2)) or show(n))
    end
  end
  return table.concat(out, " ")
end)
P("I setlocal", function()
  local function f()
    local x = 1
    local ok = debug.setlocal(1, 1, 99)
    return tostring(ok) .. "/" .. tostring(x)
  end
  return f()
end)
P("I setlocal bad", function()
  local function f()
    local x = 1
    local out = {}
    for i = -3, 6 do
      local ok, e = pcall(debug.setlocal, 1, i, 99)
      out[#out + 1] = i .. "=" .. (ok and tostring(e) or show(e))
    end
    return table.concat(out, " ")
  end
  return f()
end)
P("I setlocal tmp", function()
  -- 临时寄存器不可写
  local function f()
    local out = {}
    for i = 1, 4 do
      local ok, e = pcall(debug.setlocal, 2, i, 99)
      out[#out + 1] = i .. "=" .. (ok and tostring(e) or show(e))
    end
    return table.concat(out, " ")
  end
  return f()
end)
P("I co", function()
  local co = coroutine.create(function()
    local x = 5
    coroutine.yield()
  end)
  coroutine.resume(co)
  local out = {}
  for i = 0, 4 do
    local ok, n, v = pcall(debug.getlocal, co, 1, i)
    out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
  end
  return table.concat(out, " ")
end)
P("I args bad", function()
  local out = {}
  for _, a in ipairs({ "nil", "true", "'x'", "{}", "1.5", "0" }) do
    local ok, e = pcall(debug.getlocal, 1, load("return " .. a)())
    out[#out + 1] = a .. "=" .. (ok and tostring(e) or show(e))
  end
  return table.concat(out, " ")
end)

print("=== J getupvalue / setupvalue ===")
local uv1, uv2, uv3 = 1, 2, 3
local function mkUps()
  local a = uv1
  local b = uv2
  local c = uv3
  return function() return a + b + c end
end
local F = mkUps()
for i = -2, 5 do
  P("J get " .. i, function()
    local ok, n, v = pcall(debug.getupvalue, F, i)
    if not ok then return show(n) end
    return tostring(n) .. "/" .. show(v)
  end)
end
P("J setup", function()
  local ok, n = pcall(debug.setupvalue, F, 1, 100)
  return tostring(ok) .. "/" .. tostring(n) .. "/" .. tostring(F())
end)
P("J setup bad", function()
  local out = {}
  for i = -2, 5 do
    local ok, e = pcall(debug.setupvalue, F, i, 7)
    out[#out + 1] = i .. "=" .. (ok and tostring(e) or show(e))
  end
  return table.concat(out, " ")
end)
P("J cups", function()
  local out = {}
  for i = -1, 3 do
    local ok, n, v = pcall(debug.getupvalue, print, i)
    out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
  end
  return table.concat(out, " ")
end)
P("J nups0", function()
  local g = load("return 1")
  local out = {}
  for i = -1, 2 do
    local ok, n, v = pcall(debug.getupvalue, g, i)
    out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
  end
  return table.concat(out, " ")
end)
P("J _ENV", function()
  local out = {}
  for i = 1, 2 do
    local ok, n, v = pcall(debug.getupvalue, mkUps, i)
    out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. show(v)) or show(n))
  end
  return table.concat(out, " ")
end)
for _, a in ipairs({ "nil", "true", "42", "1.5", "'x'", "{}" }) do
  P("J bad " .. a, function()
    return pcall(debug.getupvalue, load("return " .. a)(), 1)
  end)
  P("J bad2 " .. a, function()
    return pcall(debug.getupvalue, print, load("return " .. a)())
  end)
end

print("=== K traceback ===")
local TBMSG = { "nil", "'msg'", "''", "42", "1.5", "true", "{}",
                "setmetatable({}, {__tostring = function() return 'TS' end})",
                "setmetatable({}, {__tostring = function() return {} end})" }
for i = 1, #TBMSG do
  local m = TBMSG[i]
  for _, lv in ipairs({ 0, 1, 2, 3, 99 }) do
    P("K " .. i .. " lvl" .. tostring(lv), function()
      local msg = load("return " .. m)()
      -- 回溯本身逐字节比：这里只取第一行之前的部分，行号一致即可比
      local s = debug.traceback(msg, lv)
      return (s:gsub("0x%x+", "PTR"))
    end)
  end
end
P("K co", function()
  local co = coroutine.create(function() coroutine.yield() end)
  coroutine.resume(co)
  return debug.traceback(co, "m", 1)
end)
P("K co0", function()
  local co = coroutine.create(function() coroutine.yield() end)
  return debug.traceback(co)
end)
P("K bad", function() return pcall(debug.traceback, print) end)

print("=== L 元表 / registry / uservalue ===")
local TYPES = { "nil", "true", "42", "1.5", "'x'", "{}", "print",
                "coroutine.create(function() end)", "io.stdout" }
for i = 1, #TYPES do
  local a = TYPES[i]
  P("L getmt " .. a, function() return debug.getmetatable(load("return " .. a)()) end)
  P("L setmt " .. a, function()
    local v = load("return " .. a)()
    local orig = debug.getmetatable(v) -- 约定 #80：还原成原值，还成 nil 会炸
    local ok, e = pcall(debug.setmetatable, v, { __index = { z = 1 } })
    local back = tostring(ok) .. "/" .. tostring(e)
    pcall(debug.setmetatable, v, orig)
    return back
  end)
  P("L uv " .. a, function()
    local v = load("return " .. a)()
    local ok, e = pcall(debug.getuservalue, v)
    return tostring(ok) .. "/" .. show(e)
  end)
  P("L suv " .. a, function()
    local v = load("return " .. a)()
    local ok, e = pcall(debug.setuservalue, v, 42)
    return tostring(ok) .. "/" .. show(e)
  end)
end
P("L setmt0", function() return pcall(debug.setmetatable, 1) end)
P("L setmtnil", function()
  local ok = debug.setmetatable(42, nil)
  return tostring(ok)
end)
P("L registry", function()
  local r = debug.getregistry()
  return type(r) .. "/" .. tostring(r ~= nil)
end)

print("=== M 钩子 ===")
local MASKS = { "", "c", "r", "l", "cr", "cl", "rl", "crl", "lcr", "x", "L", "C" }
for i = 1, #MASKS do
  local m = MASKS[i]
  P("M " .. m .. " c0", function()
    debug.sethook(function(...) end, m)
    local r = pcall(function() return 1 + 1 end)
    debug.sethook()
    return r
  end)
  P("M " .. m .. " c1", function()
    local n = 0
    debug.sethook(function(...) n = n + 1 end, m, 1)
    local r = pcall(function() return ("x"):upper() end)
    debug.sethook()
    return r, n
  end)
end
-- 钩子里读 getinfo：约定 #31，取第 3 层
P("M gi2", function()
  local out = {}
  debug.sethook(function(what)
    out[#out + 1] = what .. "/" .. tostring(debug.getinfo(2, "Sln").currentline)
  end, "l")
  local r = pcall(function() local a = 1 local b = 2 return a + b end)
  debug.sethook()
  return r, table.concat(out, ",")
end)
P("M gi3", function()
  local out = {}
  debug.sethook(function(what)
    local i = debug.getinfo(3, "n")
    out[#out + 1] = what .. "/" .. tostring(i and i.name)
  end, "l")
  local r = pcall(function() local a = 1 return a end)
  debug.sethook()
  return r, table.concat(out, ",")
end)
P("M gethook", function()
  local f = function() end
  debug.sethook(f, "l", 7)
  local r1 = table.pack(debug.gethook())
  debug.sethook()
  -- 没有钩子时 debug.gethook() 只返回一个 nil，不是三个值
  local r2 = table.pack(debug.gethook())
  local function fmt(r)
    local out = {}
    for i = 1, r.n do out[#out + 1] = tostring(r[i]) end
    return "n=" .. r.n .. "[" .. table.concat(out, ",") .. "]"
  end
  return tostring(r1[1] == f) .. " " .. fmt(r1) .. " " .. fmt(r2)
end)
-- 主线程上「钩子里抛错」是致命的（约定 #31：脚本直接崩，pcall 接不住），
-- 所以放进协程里跑——resume 能把它收成 false, msg，脚本继续往下走。
-- 裸的主线程那份单独成一条用例，见 DebugGridTest 的第二个方法。
P("M errco", function()
  local co = coroutine.create(function()
    debug.sethook(function() error("hook") end, "l")
    local r = pcall(function() local a = 1 return a end)
    debug.sethook()
    return r
  end)
  local out = {}
  for i = 1, 3 do
    local r = table.pack(coroutine.resume(co))
    out[#out + 1] = tostring(r[1]) .. "/" .. show(r[2])
  end
  return table.concat(out, " ") .. " " .. coroutine.status(co)
end)
P("M errcco", function()
  local co = coroutine.create(function()
    debug.sethook(function() error("hook") end, "c")
    local r = pcall(function() return ("x"):upper() end)
    debug.sethook()
    return r
  end)
  local out = {}
  for i = 1, 3 do
    local r = table.pack(coroutine.resume(co))
    out[#out + 1] = tostring(r[1]) .. "/" .. show(r[2])
  end
  return table.concat(out, " ") .. " " .. coroutine.status(co)
end)
P("M errco2", function()
  local co = coroutine.create(function()
    debug.sethook(function() error(setmetatable({}, {__tostring = function() return "TS" end})) end, "l")
    local r = pcall(function() local a = 1 return a end)
    debug.sethook()
    return r
  end)
  local r = table.pack(coroutine.resume(co))
  return tostring(r[1]) .. "/" .. show(r[2])
end)
P("M yield", function()
  local co = coroutine.create(function()
    debug.sethook(function() coroutine.yield("Y") end, "l")
    local a = 1
    debug.sethook()
    return "done"
  end)
  local out = {}
  for i = 1, 4 do
    local r = table.pack(coroutine.resume(co))
    out[#out + 1] = table.concat({ tostring(r[1]), tostring(r[2]) }, "/")
  end
  return table.concat(out, " ")
end)
-- 事件名里的 "tail call" / "tail return" 只有尾调用才出得来
P("M tail", function()
  local out = {}
  debug.sethook(function(what) out[#out + 1] = what end, "c")
  local function g() return 1 end
  local function f() return g() end
  local r = f()
  debug.sethook()
  return tostring(r) .. " | " .. table.concat(out, ",")
end)
P("M allret", function()
  local out = {}
  debug.sethook(function(what, line) out[#out + 1] = what .. ":" .. tostring(line) end, "crl")
  local r = (function() return ("x"):upper() end)()
  debug.sethook()
  return tostring(r) .. " | " .. table.concat(out, ",")
end)
P("M args bad", function()
  local out = {}
  for _, a in ipairs({ "nil", "42", "'x'", "{}" }) do
    local ok, e = pcall(debug.sethook, load("return " .. a)())
    out[#out + 1] = a .. "=" .. (ok and "ok" or show(e))
    debug.sethook()
  end
  return table.concat(out, " ")
end)

print("=== N 协程第二遍：整片重跑 ===")
local function body()
  local out = {}
  for _, w in ipairs({ "NIL", "", "S", "l", "n", "u", "flnSrtu", "L" }) do
    out[#out + 1] = gi(1, opt(w))
    out[#out + 1] = gi(2, opt(w))
  end
  out[#out + 1] = norm(debug.traceback("m", 1))
  local ok1, n1, v1 = debug.getlocal(1, 1)
  out[#out + 1] = tostring(ok1) .. "/" .. tostring(n1) .. "/" .. show(v1)
  local f = function() return 1 end
  local ok2, n2, v2 = debug.getupvalue(f, 1)
  out[#out + 1] = tostring(ok2) .. "/" .. tostring(n2) .. "/" .. show(v2)
  out[#out + 1] = show(debug.getmetatable(1))
  out[#out + 1] = tostring(debug.getregistry() ~= nil)
  return table.concat(out, ";")
end
P("N main", function() return body() end)
P("N co", function()
  local c = coroutine.create(function() return body() end)
  return select(2, coroutine.resume(c))
end)
P("N wrap", function()
  local w = coroutine.wrap(function() return body() end)
  return w()
end)

print("=== O chunkid 边界：source / short_src / 位置前缀 ===")
-- 约定 #84：出口是 %s，在第一个 \\\\0 处砍断；#85：高位字节原样留着。
-- chunkid 自己还有一层「超长截成 ...」的语义，一起钉住。
local NAMES = { "", "@f.lua", "=lit", "[C]", "a\\0b", "\\255\\254",
                string.rep("x", 58), string.rep("x", 60), string.rep("x", 100),
                "@" .. string.rep("y", 70), "=a\\0b", "\\0abc" }
for i = 1, #NAMES do
  local nm = NAMES[i]
  P("O info " .. i, function()
    return gi(load("return 1", nm), "S")
  end)
  P("O err " .. i, function()
    return load("error('x')", nm)()
  end)
  P("O lvl " .. i, function()
    return load("return debug.getinfo(1, 'Sl')", nm)()
  end)
  P("O tb " .. i, function()
    return load("return debug.traceback('m')", nm)()
  end)
end

print("=== P <close> / <const> / 长函数的 activelines ===")
P("P close", function()
  return load([[
    local mt = {__close = function() end}
    local a <close> = setmetatable({}, mt)
    local b <const> = 2
    local c = 3
    local out = {}
    for i = -2, 6 do
      local ok, n, v = pcall(debug.getlocal, 1, i)
      out[#out + 1] = i .. "=" .. (ok and (tostring(n) .. "/" .. type(v)) or tostring(n))
    end
    return table.concat(out, " ")
  ]])()
end)
P("P actl", function()
  local src = "local x = 1\\n"
  for i = 1, 12 do src = src .. "local y" .. i .. " = " .. i .. "\\n" end
  src = src .. "return x\\n"
  local f = load(src, "=actl")
  return gi(f, "SL")
end)
P("P actl2", function()
  return gi(debug.getinfo(1, "f").func, "SL")
end)
        """);
    }

    /** 行钩子里抛错：主线程上致命，pcall 接不住（约定 #31）。 */
    @Test
    void lineHookErrorIsFatalOnMainThread() {
        Diff.assertFullMatches("""
-- 主线程上「行钩子里抛错」：脚本直接崩，pcall 接不住（约定 #31）。
-- 这类用例会终止脚本，所以必须单独成条——塞进大网格里会把后面几百行全吃掉。
print("before")
local r = pcall(function() local a = 1 return a + 1 end)
print("after", r)
debug.sethook(function(what) error("hook:" .. tostring(what)) end, "l")
local r2 = pcall(function() local a = 1 return a + 1 end)
print("unreachable", r2)
debug.sethook()
print("still unreachable")
        """);
    }

    /** 调用钩子里抛错：同样致命，触发点与行钩子不同。 */
    @Test
    void callHookErrorIsFatalOnMainThread() {
        Diff.assertFullMatches("""
-- 主线程上「调用钩子里抛错」：同样致命，但触发点与行钩子不同（约定 #31）。
print("before")
local r = pcall(function() return ("x"):upper() end)
print("after", r)
debug.sethook(function(what) error("hook:" .. tostring(what)) end, "c")
local r2 = pcall(function() return ("x"):upper() end)
print("unreachable", r2)
debug.sethook()
print("still unreachable")
        """);
    }
}
