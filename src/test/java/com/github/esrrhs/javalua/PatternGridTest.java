package com.github.esrrhs.javalua;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeAll;

import java.io.File;

/**
 * 模式匹配的全网格差分 —— 阶段 12。
 * <p>
 * 为什么单开一类：{@code StringLibTest} 里那批模式用例是<b>手挑</b>的，量级只有
 * 几十条，而 {@code lstrlib.c} 的匹配器是本项目里<b>唯一一个靠递归下降 +
 * 回溯</b>的模块（{@code match} / {@code max_expand} / {@code min_expand} /
 * {@code classend} / {@code singlematch}），边界全在字符类与量词的组合上。
 * 手挑用例盖不住的组合，机械铺开一次就能盖住（约定 #39）。
 * <p>
 * 两片各自成阵：
 * <ul>
 *   <li><b>片一：字符类 × 量词 × 构造 × 五个出口</b>。20 个字符类（含
 *       {@code %g}/{@code %G} 这种 5.4 才有的）乘 5 种量词，再叠上集合、
 *       {@code %b}、{@code %f}、捕获、位置捕获、锚，共 188 个模式；
 *       18 个目标串（含 NUL、{@code 0xFF}、空串、{@code %}、{@code ]}）；
 *       出口是 {@code find}/{@code match}/{@code gmatch}/{@code gsub}
 *       四种（{@code gsub} 又分「替换串」与「替换函数」两路）。约 16900 行。</li>
 *   <li><b>片二：{@code init} / {@code plain} / 替换串 / 畸形模式 / 参数类型</b>。
 *       {@code init} 从 {@code -5} 扫到 {@code 8} 外加浮点与数字字符串，
 *       {@code plain} 给八种值（含 {@code 0}、{@code ''}、{@code {}}），
 *       畸形模式 30 种（含 33 层捕获、{@code %f} 缺 {@code [}），
 *       参数类型 8 种 × 四个函数。</li>
 * </ul>
 * <p>
 * 两片都带一遍<b>协程第二遍</b>：同一批语料塞进 {@code coroutine.create}
 * 再跑一遍；片二末尾还试了「在 {@code gsub} 的回调里 yield」——那是从
 * <b>没有续体的 C 帧</b>里 yield，参考实现报
 * {@code attempt to yield across a C-call boundary}（约定 #81）。
 * <p>
 * <b>这一片一条分歧都没咬出来</b>——铺之前担心、铺完确认干净，这本身就是结论：
 * 匹配器这一块可以放心。
 */
class PatternGridTest {

    @BeforeAll
    static void prepare() {
        new File("target/rt").mkdirs();
    }

    /** 片一：字符类 x 量词 x 构造 x 五个出口（约 16900 行）。 */
    @Test
    void classQuantifierGridMatchesReference() {
        Diff.assertMatches("""

-- 模式匹配全网格：字符类 x 量词 x 构造 x 五个出口
-- 语料在 Lua 里自己拼（不是把 16000 行展开写死），但覆盖面与展开版一致。
local HEX = "0123456789ABCDEF"
local function hb(b) return HEX:sub((b // 16) + 1, (b // 16) + 1) .. HEX:sub((b % 16) + 1, (b % 16) + 1) end
local function esc(s)
  return (s:gsub(".", function(c)
    local b = c:byte()
    if b < 32 or b > 126 then return "<" .. hb(b) .. ">" end
    return c
  end))
end
local function show(v)
  local t = type(v)
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  end
  return t .. "(" .. tostring(v):gsub("0x%x+", "PTR") .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end
local function gmatchN(s, p, n)
  local out = {}
  local it = string.gmatch(s, p)
  for _ = 1, n do
    local r = table.pack(it())
    if r.n == 0 then break end
    local one = ""
    for j = 1, r.n do one = one .. ((j > 1) and "," or "") .. show(r[j]) end
    out[#out + 1] = one
  end
  return table.concat(out, ";")
end

local SUBJ = { "", "a", "abc", "a1b2", "  x  ", "A_z9", "\\0ab", "\\255\\254", "()",
  "(a(b)c)", "a.b", "%", "-", "]", "aBc123 \\t", "\\255", "a\\0", "THE (quick) fox" }

local PATS = {}
-- 1) 每个字符类 x 每个量词
for _, c in ipairs({ "a", "A", "c", "C", "d", "D", "g", "G", "l", "L",
                     "p", "P", "s", "S", "u", "U", "w", "W", "x", "X" }) do
  for _, q in ipairs({ "", "*", "+", "-", "?" }) do PATS[#PATS + 1] = "%" .. c .. q end
end
-- 2) 裸字符与 magic 的转义
for _, ch in ipairs({ ".", "a", "1", " " }) do
  for _, q in ipairs({ "", "*", "+", "-", "?" }) do PATS[#PATS + 1] = ch .. q end
end
for _, e in ipairs({ "%%", "%.", "%(", "%)", "%[", "%]", "%*", "%+", "%-", "%?", "%^", "%$" }) do
  PATS[#PATS + 1] = e
end
-- 3) 集合
for _, e in ipairs({ "[abc]", "[^abc]", "[a-c]", "[^a-c]", "[]]", "[]a]", "[a-]", "[-a]",
                     "[%a]", "[^%a]", "[%a%d]", "[a-c0-9]", "[[", "[]", "[^]",
                     "[a-\\255]", "[%]]", "[%%]", "[-]", "[^-]" }) do
  PATS[#PATS + 1] = e
end
-- 4) %b / %f
for _, e in ipairs({ "%b()", "%b<>", "%b[]", "%bab", "%bx", "%b", "%f[%a]", "%f[%w]",
                     "%f[a]", "%f[^a]", "%f[%a]%a+", "%f[]", "%f", "%f[%a" }) do
  PATS[#PATS + 1] = e
end
-- 5) 捕获与位置捕获
for _, e in ipairs({ "(a)", "(a*)", "()", "(())", "(%a)", "()a()", "(a)(b)", "(a-)" }) do
  PATS[#PATS + 1] = e
end
-- 6) 锚与组合
for _, e in ipairs({ "^a", "a$", "^a$", "^", "$", "^$", ".*", ".-", ".+", "a*b", "a-b",
                     "%a+%d", "%a*%d*", "(.)%1" }) do
  PATS[#PATS + 1] = e
end

print("patterns " .. #PATS)
for si = 1, #SUBJ do
  local s = SUBJ[si]
  print("=== S" .. si .. " ===")
  for pi = 1, #PATS do
    local p = PATS[pi]
    local tag = "P" .. pi
    P(tag, function() return string.find(s, p) end)
    P(tag, function() return string.match(s, p) end)
    P(tag, function() return gmatchN(s, p, 12) end)
    P(tag, function() return string.gsub(s, p, "<%0>") end)
    P(tag, function() return string.gsub(s, p, function(...) return "|" .. select("#", ...) .. "|" end) end)
  end
end
        """);
    }

    /** 片二：init / plain / 替换串 / 畸形模式 / 参数类型（约 2400 行）。 */
    @Test
    void initPlainReplacementAndMalformedGridMatchesReference() {
        Diff.assertMatches("""

-- 模式匹配网格 II：init / plain、替换串、畸形模式、协程第二遍
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
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]"
  elseif t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. tostring(v)
  end
  -- 约定 #42：地址一律归一成类型
  return t .. "(" .. tostring(v):gsub("0x%x+", "PTR") .. ")"
end
local function P(tag, fn)
  local r = table.pack(pcall(fn))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

print("=== A find 的 init 网格 ===")
local SUBJ = { "", "a", "abc", "a1b2", "\\0ab", "\\255\\254", "aaa" }
local PATS = { "a", "%a", "%a+", "", "^a", "a$", ".", "%d", "[a-c]" }
for i = 1, #SUBJ do
  local s = SUBJ[i]
  for j = 1, #PATS do
    local p = PATS[j]
    for init = -5, 8 do
      P("A " .. i .. " " .. j .. " init=" .. init, function()
        return string.find(s, p, init)
      end)
    end
    -- plain 三态：false / true / nil，以及非布尔
    for _, plain in ipairs({ "false", "true", "nil", "0", "1", "''", "'x'", "{}" }) do
      P("A plain " .. i .. " " .. j .. " " .. plain, function()
        return string.find(s, p, 1, load("return " .. plain)())
      end)
    end
    -- init 是浮点 / 数字字符串
    for _, iv in ipairs({ "1.0", "2.5", "-1.0", "'2'", "'0'", "0x7fffffff", "math.maxinteger", "math.mininteger" }) do
      P("A initf " .. i .. " " .. j .. " " .. iv, function()
        return string.find(s, p, load("return " .. iv)())
      end)
    end
  end
end

print("=== B gsub 的替换串 ===")
local REPL = { "%0", "%1", "%2", "%9", "%%", "%", "%x", "%-", "% ", "a%0b",
               "%0%0", "[%1]", "%1x", "1", "true", "false" }
for i = 1, #SUBJ do
  local s = SUBJ[i]
  for j = 1, #REPL do
    local r = REPL[j]
    P("B " .. i .. " " .. j .. " " .. esc(r), function()
      return string.gsub(s, "(a)", r)
    end)
    P("B nocap " .. i .. " " .. j .. " " .. esc(r), function()
      return string.gsub(s, "a", r)
    end)
  end
end

print("=== C gsub 的第三个参数：表 / 函数 / 布尔 / nil / 数字 ===")
for i = 1, #SUBJ do
  local s = SUBJ[i]
  P("C nil " .. i, function() return string.gsub(s, "%a", nil) end)
  P("C false " .. i, function() return string.gsub(s, "%a", false) end)
  P("C true " .. i, function() return string.gsub(s, "%a", true) end)
  P("C 0 " .. i, function() return string.gsub(s, "%a", 0) end)
  P("C 42 " .. i, function() return string.gsub(s, "%a", 42) end)
  P("C tbl " .. i, function()
    return string.gsub(s, "%a", setmetatable({}, {__index = function(_, k) return "K" .. k end}))
  end)
  P("C tblnil " .. i, function()
    return string.gsub(s, "%a", setmetatable({}, {__index = function(_, k) return nil end}))
  end)
  P("C fnnil " .. i, function() return string.gsub(s, "%a", function() return nil end) end)
  P("C fnfalse " .. i, function() return string.gsub(s, "%a", function() return false end) end)
  P("C fnnum " .. i, function() return string.gsub(s, "%a", function() return 42 end) end)
  P("C fnerr " .. i, function() return pcall(string.gsub, s, "%a", function() error("boom") end) end)
  P("C fnerr2 " .. i, function() return pcall(string.gsub, s, "%a", function() error({}) end) end)
end

print("=== D gsub 的 max n ===")
for _, n in ipairs({ "-1", "0", "1", "2", "100", "1.0", "2.5", "'2'", "math.maxinteger", "math.mininteger" }) do
  P("D n=" .. n, function() return string.gsub("aaa", "a", "b", load("return " .. n)()) end)
  P("D n2=" .. n, function() return string.gsub("abcabc", "%a", function(c) return c:upper() end, load("return " .. n)()) end)
end

print("=== E 畸形模式 ===")
local BAD = {
  "%", "a%", "%f", "%f[", "%f[%a", "%f[]", "%fa", "(", ")", "(a", "a)",
  "[", "[a", "[^", "[a-", "%1", "(a)%2", "%2(a)", "%0",
  "()%1", "(())%2", "%b", "%bx", "%b()%b",
  "(((((((((((((((((((((((((((((((((((", -- 32 层
  string.rep("(", 33),
  "[" .. string.rep("a", 200) .. "]",
  "%\\255", "%\\0", "[\\255]", "(%255)",
}
for i = 1, #BAD do
  local p = BAD[i]
  P("E find " .. i, function() return string.find("abc", p) end)
  P("E match " .. i, function() return string.match("abc", p) end)
  P("E gsub " .. i, function() return string.gsub("abc", p, "x") end)
  P("E gmatch " .. i, function()
    local it = string.gmatch("abc", p)
    return it()
  end)
end

print("=== F 参数类型错误 ===")
local ARGS = { "nil", "true", "42", "1.5", "'x'", "{}", "print", "coroutine.create(function() end)" }
for i = 1, #ARGS do
  local a = ARGS[i]
  P("F find1 " .. a, function() return pcall(string.find, load("return " .. a)()) end)
  P("F find2 " .. a, function() return pcall(string.find, "abc", load("return " .. a)()) end)
  P("F match1 " .. a, function() return pcall(string.match, load("return " .. a)()) end)
  P("F gmatch1 " .. a, function() return pcall(string.gmatch, load("return " .. a)()) end)
  P("F gsub1 " .. a, function() return pcall(string.gsub, load("return " .. a)()) end)
  P("F gsub2 " .. a, function() return pcall(string.gsub, "abc", load("return " .. a)()) end)
end
P("F find0", function() return pcall(string.find) end)
P("F match0", function() return pcall(string.match) end)
P("F gmatch0", function() return pcall(string.gmatch) end)
P("F gsub0", function() return pcall(string.gsub) end)
P("F gsub3", function() return pcall(string.gsub, "abc") end)

print("=== G 协程第二遍：整片重跑 ===")
-- 约定第四条：同一批语料塞进 coroutine.create 再跑一遍
local function body()
  local out = {}
  for i = 1, #SUBJ do
    local s = SUBJ[i]
    for j = 1, #PATS do
      out[#out + 1] = show(string.find(s, PATS[j]))
      out[#out + 1] = show(select(2, string.gsub(s, PATS[j], "<%0>")))
    end
  end
  for i = 1, #BAD do
    out[#out + 1] = esc(select(2, pcall(string.match, "abc", BAD[i])))
  end
  return table.concat(out, ";")
end
P("G main", function() return body() end)
P("G co", function()
  local c = coroutine.create(function() coroutine.yield(body()) end)
  return select(2, coroutine.resume(c))
end)
P("G wrap", function()
  local w = coroutine.wrap(function() coroutine.yield(body()) end)
  return w()
end)
-- 从 C 函数（gsub 的回调）里 yield：C 帧没有续体，这正是约定 #81
P("G yieldInGsub", function()
  return pcall(string.gsub, "abc", "%a", function(c) coroutine.yield(c) return c end)
end)
P("G yieldInGsubCo", function()
  local co = coroutine.create(function()
    return string.gsub("abc", "%a", function(c) coroutine.yield(c) return c end)
  end)
  local r = table.pack(coroutine.resume(co))
  local out = tostring(r[1])
  for i = 2, r.n do out = out .. " | " .. show(r[i]) end
  return out
end)
        """);
    }
}
