package com.github.esrrhs.javalua;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 二进制安全的差分矩阵 —— 阶段 12。
 * <p>
 * 为什么单开一类：约定 #9/#10 说「Lua 字符串是 {@code byte[]}，禁止用 Java
 * String 方法处理内容」，但前面十一轮铺的网格里，含 {@code \0} 与高位字节的
 * 样本只零星出现（全仓库 27 个测试类里 {@code char(0)} 只用了几十次）。
 * 而这一片正是实现最容易错的地方——因为它要求<b>每一层都按字节办事</b>，
 * 只要有一处退化成 Java String（UTF-8 解码 → 非法字节变 {@code U+FFFD}
 * → 再编码回去），内容就静默变了。
 * <p>
 * 这一轮实际咬出三个真 bug，全在这条链上：
 * <ul>
 *   <li><b>{@code l_strcmp} 用了有符号 byte</b>。C99 规定 {@code strcoll}
 *       的返回值符号由第一对不等字节「都按 {@code unsigned char} 解释」之差
 *       决定；Java 的 {@code byte} 有符号，{@code 0xFF} 被当成 {@code -1}，
 *       于是 {@code "\xff" < "abc"} 从 <b>false</b> 翻成 <b>true</b>，
 *       {@code table.sort} 的整体次序跟着反转。本机实测
 *       {@code strcoll("\xff", "abc") == 158 > 0}。</li>
 *   <li><b>位置前缀与错误消息的拼接走了 Java String</b>。
 *       {@code errorAt} / {@code runError} / {@code assert} 都是
 *       {@code latin1(prefix + msg.toJavaString())}：{@code toJavaString}
 *       按 UTF-8 解码，{@code 0xFF} 变成 {@code U+FFFD}（{@code 0xFFFD > 0xFF}），
 *       {@code latin1} 直接抛 {@code IllegalArgumentException}。
 *       {@code assert(false, "\xff")} 一度把整个进程炸掉——而且是
 *       <b>Java 异常不是 LuaError</b>，pcall 接不住（约定 #4）。</li>
 *   <li><b>{@code short_src} 与 {@code debug.traceback} 同样走 Java String</b>。
 *       {@code load(f, "=\xff")} 之后 {@code debug.getinfo(1,'S').short_src}
 *       给的是 {@code "\xef\xbf\xbd"}；回溯里 {@code function <...>} 那段
 *       更隐蔽——{@code 0xFFFD} 被 {@code (byte)} 截成 {@code 0xFD}，
 *       {@code U+4E2D} 截成 {@code '-'}，长度倒是对的，只有内容错。</li>
 * </ul>
 * <p>
 * 四片各自成阵：
 * <ul>
 *   <li><b>片一：string 核心函数 × 字节样本</b>。27 个样本（含 NUL 在头/中/尾、
 *       {@code 0xFF}、四类非法 UTF-8、超长编码、代理项、合法 UTF-8）乘上
 *       {@code byte/char/sub/find/match/gmatch/gsub/format/rep/reverse/
 *       upper/lower/tonumber} 的参数网格。</li>
 *   <li><b>片二：比较与排序、表键、错误消息、utf8、pack、load、io</b>。
 *       256 个单字节串全排列的 <b>{@code <} 真值表压成位图</b>，一行钉住
 *       整个次序；再把带 NUL 的值当表键、当错误消息、喂给 utf8 与
 *       {@code string.pack}。</li>
 *   <li><b>片三：chunk 名本身是任意字节</b>。{@code =} / {@code @} / 裸三种
 *       写法 × 含 NUL 与高位字节 × 跨 {@code LUA_IDSIZE} 的长度，看
 *       {@code luaL_where} / {@code short_src} / {@code traceback} 三条出口。</li>
 *   <li><b>片四：错误消息里嵌的<b>值本身</b></b>——{@code varinfo} 反推的字段名、
 *       {@code __name}、{@code require} 的模块名、{@code io}/{@code os} 的文件名。
 *       这里额外咬出<b>「名字是 C 字符串」</b>这一层：{@code kname} 拿到的是
 *       {@code getstr()} 那个 {@code const char*}，{@code formatvarinfo} 用
 *       {@code "%s"} 拼，于是名字在第一个 {@code \0} 处被砍断——实测
 *       {@code load("local t={} return t['a\0b'].x")()} 参考实现报
 *       {@code (field 'a')}，而高字节<b>不</b>砍（{@code t['\255\254']} 原样报
 *       {@code (field '<FF><FE>')}）。</li>
 * </ul>
 * <p>
 * 片二与片四都带一遍<b>协程第二遍</b>（约定：同一批语料塞进
 * {@code coroutine.create} 再跑一遍）。
 *
 * @see Vm#lStrcmp
 * @see LuaState#errorAt
 * @see LuaState#kName
 * @see DebugLib#traceback
 */
class BinarySafetyTest {

    @BeforeAll
    static void prepare() {
        // 片二/片三会往这里写临时文件（约定 #41：开头先删一次）
        new File(Diff.SCRATCH).mkdirs();
    }

    /** 片一：string 核心函数 x 字节样本（12000+ 行）。 */
    @Test
    void stringCoreGridMatchesReference() {
        Diff.assertMatches("""

-- 二进制安全矩阵 I：string 核心函数 x 含 NUL / 高位字节 / 非法 UTF-8 的输入
-- 约定 #9/#10：Lua 字符串是 byte[]，内容可以含 NUL、可以是非法 UTF-8。
-- 所有特殊字节一律用 string.char 造，文件里不出现反斜杠（Java 文本块会炸）。

local NUL = string.char(0)
local BS  = string.char(92)
local LF  = string.char(10)
local CR  = string.char(13)
local TAB = string.char(9)

-- 可打印原样，其余转成 <XX>；方括号也转义，免得跟包围符混
local function esc(s)
  return (s:gsub(".", function(c)
    local b = c:byte()
    if b == 91 or b == 93 then return string.format("<%.2X>", b) end
    if b < 32 or b > 126 then return string.format("<%.2X>", b) end
    return c
  end))
end

local function show(v)
  local t = type(v)
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]" end
  if t == "number" then
    local mt = math.type(v)
    if mt == "integer" then return "i" .. tostring(v) end
    if mt == "float" then return "f" .. tostring(v) end
    return "?" .. tostring(v)
  end
  if t == "nil" or t == "boolean" then return tostring(v) end
  return t .. "(" .. (tostring(v):gsub("0x%x+", "PTR")) .. ")"
end

-- 每个可能抛错的调用都必须在 pcall 里（约定 #38）
local function P(tag, fn, ...)
  local r = table.pack(pcall(fn, ...))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

local SAMPLES = {
  {"empty",    ""},
  {"ascii",    "abc"},
  {"nulonly",  NUL},
  {"nulmid",   "a" .. NUL .. "b"},
  {"nulend",   "ab" .. NUL},
  {"nulbeg",   NUL .. "ab"},
  {"nulx3",    NUL .. NUL .. NUL},
  {"nulnum",   "1" .. NUL .. "2"},
  {"ff",       string.char(0xFF)},
  {"ffmid",    "a" .. string.char(0xFF) .. "b"},
  {"c3_28",    string.char(0xC3) .. string.char(0x28)},
  {"c0_80",    string.char(0xC0) .. string.char(0x80)},
  {"e2_82",    string.char(0xE2) .. string.char(0x82)},
  {"eda080",   string.char(0xED) .. string.char(0xA0) .. string.char(0x80)},
  {"f49080",   string.char(0xF4) .. string.char(0x90) .. string.char(0x80) .. string.char(0x80)},
  {"utf8ok",   string.char(0xE4) .. string.char(0xB8) .. string.char(0xAD)},
  {"lf",       "a" .. LF .. "b"},
  {"cr",       "a" .. CR .. "b"},
  {"tab",      "a" .. TAB .. "b"},
  {"pct",      "a%b"},
  {"bs",       "a" .. BS .. "b"},
  {"digits",   "12a"},
  {"spaces",   "  12  "},
  {"dash",     "-12"},
  {"hexnum",   "0x1p4"},
  {"dot",      "a.b"},
  {"brack",    "a[b"},
}
local NS = #SAMPLES

print("=== A 长度与表示 ===")
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  P("A len " .. n, function() return #s, s:len(), string.len(s), tostring(s) == s end)
  P("A cmp " .. n, function()
    return s == "abc", s < "abc", s > "abc", s <= s, s .. NUL == s .. NUL
  end)
end

print("=== B string.byte 网格 ===")
local IDX = {-4, -3, -1, 0, 1, 2, 3, 4, 5, 100}
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  P("B byte " .. n, function() return string.byte(s) end)
  for _, a in ipairs(IDX) do
    P("B byte1 " .. n .. " " .. a, function() return string.byte(s, a) end)
    for _, b in ipairs(IDX) do
      P("B byte2 " .. n .. " " .. a .. " " .. b, function() return string.byte(s, a, b) end)
    end
  end
end

print("=== C string.char 网格 ===")
local CH = {0, 1, 65, 127, 128, 255, 256, -1, -256, 1.0, 1.5, 2.9, -0.0}
for _, a in ipairs(CH) do
  P("C char1 " .. tostring(a), function() return #string.char(a) end)
  for _, b in ipairs(CH) do
    P("C char2 " .. tostring(a) .. " " .. tostring(b), function() return #string.char(a, b) end)
  end
end
P("C char0", function() return #string.char() end)
P("C char str", function() return #string.char("65") end)
P("C char str2", function() return #string.char("0x41") end)
P("C char bool", function() return #string.char(true) end)
P("C char nil", function() return #string.char(nil) end)
P("C char tbl", function() return #string.char({}) end)
P("C char huge", function() return #string.char(1e300) end)
P("C char nan", function() return #string.char(0 / 0) end)
P("C char many", function() return #string.char(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) end)

print("=== D string.sub 网格 ===")
local SI = {-5, -3, -1, 0, 1, 2, 3, 4, 100, 1.0, 1.5}
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  P("D sub " .. n, function() return string.sub(s, 2) end)
  for _, a in ipairs(SI) do
    P("D sub1 " .. n .. " " .. tostring(a), function() return string.sub(s, a) end)
    for _, b in ipairs(SI) do
      P("D sub2 " .. n .. " " .. tostring(a) .. " " .. tostring(b), function() return string.sub(s, a, b) end)
    end
  end
end
P("D sub bool", function() return string.sub("abc", true) end)
P("D sub str", function() return string.sub("abc", "2") end)
P("D sub nil2", function() return string.sub("abc", 1, nil) end)
P("D sub noarg", function() return string.sub() end)

print("=== E find / match / gmatch / gsub ===")
local PATS = {".", "%a", "%d", "%s", "%w", "%p", "%c", "a", NUL, "[%z]", "[^%z]", "%z", "b*", "^a", "b$", "()", "%bxy", "[a-c]", ".*", ".+"}
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  for _, p in ipairs(PATS) do
    P("E find " .. n .. " " .. esc(p), function() return string.find(s, p) end)
    P("E findP " .. n .. " " .. esc(p), function() return string.find(s, p, 1, true) end)
    P("E match " .. n .. " " .. esc(p), function() return string.match(s, p) end)
    P("E gsub0 " .. n .. " " .. esc(p), function() return string.gsub(s, p, "") end)
    P("E gsubX " .. n .. " " .. esc(p), function() return string.gsub(s, p, "X") end)
    P("E gsubF " .. n .. " " .. esc(p), function()
      return string.gsub(s, p, function(c) return "<" .. (type(c) == "string" and #c or tostring(c)) .. ">" end)
    end)
    local hits = 0
    local ok, e = pcall(function()
      for a, b in string.gmatch(s, p) do
        hits = hits + 1
        if hits == 1 then io.write("") end
        if hits > 30 then break end
        print("E gmatch " .. n .. " " .. esc(p) .. " | " .. show(a) .. " | " .. show(b))
      end
    end)
    print("E gmatch " .. n .. " " .. esc(p) .. " | ok=" .. tostring(ok) .. " | hits=" .. hits
        .. " | err=" .. (ok and "nil" or show(e)))
  end
end

print("=== F string.format ===")
local FMTS = {"%s", "%q", "%d", "%5s", "%-5s", "%.2s", "%10.3s", "%c", "%x", "%a"}
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  for _, f in ipairs(FMTS) do
    P("F fmt " .. n .. " " .. f, function() return string.format(f, s) end)
  end
  P("F fmt2 " .. n, function() return string.format("%s|%s|%q", s, s, s) end)
end
P("F fmt c num", function() return string.format("%c", 65) end)
P("F fmt c nul", function() return #string.format("%c", 0) end)
P("F fmt c 255", function() return #string.format("%c", 255) end)
P("F fmt c 256", function() return #string.format("%c", 256) end)
P("F fmt s nul", function() return #string.format("%s", NUL) end)

print("=== G rep / reverse / upper / lower ===")
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  P("G rep0 " .. n, function() return string.rep(s, 0) end)
  P("G rep1 " .. n, function() return string.rep(s, 1) end)
  P("G rep3 " .. n, function() return string.rep(s, 3) end)
  P("G repS " .. n, function() return string.rep(s, 2, NUL) end)
  P("G repS2 " .. n, function() return string.rep(s, 3, "-") end)
  P("G rev " .. n, function() return string.reverse(s) end)
  P("G lower " .. n, function() return string.lower(s) end)
  P("G upper " .. n, function() return string.upper(s) end)
end
P("G rep neg", function() return string.rep("a", -1) end)
P("G rep huge", function() return #string.rep("a", 1e7) end)

print("=== H tonumber / 强制转换 ===")
for i = 1, NS do
  local n, s = SAMPLES[i][1], SAMPLES[i][2]
  P("H tonum " .. n, function()
    local v = tonumber(s)
    if v == nil then return nil, math.type(nil) end
    return v, math.type(v)
  end)
  for _, base in ipairs({2, 8, 10, 16, 36, 1, 37}) do
    P("H tonumB " .. n .. " " .. base, function()
      local v = tonumber(s, base)
      if v == nil then return nil end
      return v, math.type(v)
    end)
  end
  P("H arith " .. n, function() return s + 0 end)
  P("H arith2 " .. n, function() return 0 + s end)
  P("H concat " .. n, function() return s .. "" end)
  P("H lt " .. n, function() return s < 0 end)
end
P("H tonum nonstr", function() return tonumber({}, 10) end)
P("H tonum bool", function() return tonumber(true, 10) end)
        """);
    }

    /** 片二：比较与排序 / 表键 / 错误消息 / utf8 / pack / load / io。 */
    @Test
    void compareKeyMessagePackLoadGridMatchesReference() {
        Diff.assertMatches("""

-- 二进制安全矩阵 II：比较与排序（专钉 strcoll 的无符号语义）、表键、错误消息、
-- utf8、string.pack、load、io、以及协程第二遍。

local NUL = string.char(0)
local BS  = string.char(92)
local LF  = string.char(10)

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
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]" end
  if t == "number" then
    local mt = math.type(v)
    if mt == "integer" then return "i" .. tostring(v) end
    if mt == "float" then return "f" .. tostring(v) end
  end
  if t == "nil" or t == "boolean" then return tostring(v) end
  return t .. "(" .. (tostring(v):gsub("0x%x+", "PTR")) .. ")"
end
local function P(tag, fn, ...)
  local r = table.pack(pcall(fn, ...))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

print("=== I 全字节对比较 ===")
-- 256 个单字节串，按参考实现排序后的顺序（一整行钉住全部次序）
local all = {}
for b = 0, 255 do all[b + 1] = string.char(b) end
local ok, err = pcall(table.sort, all)
print("I sort ok=" .. tostring(ok) .. " err=" .. (ok and "nil" or show(err)))
if ok then
  local s = table.concat(all, "")
  print("I order " .. esc(s))
end
-- 逐对：< 的真值表压成 256 行位图
for b1 = 0, 255 do
  local row = {}
  for b2 = 0, 255 do
    row[b2 + 1] = (string.char(b1) < string.char(b2)) and "1" or "0"
  end
  print("I lt " .. hb(b1) .. " " .. table.concat(row))
end

print("=== I2 多字节串的排序与 max/min ===")
local WORDS = {
  string.char(0xFF), "abc", "Abc", NUL, "a" .. NUL, "a" .. NUL .. "a",
  string.char(0xE4) .. string.char(0xB8) .. string.char(0xAD),
  string.char(0xC3) .. string.char(0xA9), "", "z", "Z", "0",
  string.char(0x7F), string.char(0x80), string.char(0x01),
}
local sorted = {}
for i = 1, #WORDS do sorted[i] = WORDS[i] end
local ok2, e2 = pcall(table.sort, sorted)
print("I2 sort ok=" .. tostring(ok2) .. " err=" .. (ok2 and "nil" or show(e2)))
print("I2 sorted " .. esc(table.concat(sorted, "|")))
P("I2 max", function() return math.max(WORDS[1], WORDS[2], WORDS[7], WORDS[10]) end)
P("I2 min", function() return math.min(WORDS[1], WORDS[2], WORDS[7], WORDS[10]) end)
for i = 1, #WORDS do
  for j = 1, #WORDS do
    P("I2 cmp " .. esc(WORDS[i]) .. " " .. esc(WORDS[j]), function()
      return WORDS[i] < WORDS[j], WORDS[i] <= WORDS[j], WORDS[i] > WORDS[j], WORDS[i] >= WORDS[j], WORDS[i] == WORDS[j]
    end)
  end
end

print("=== J 表键 ===")
local KEYS = {"a", NUL, "a" .. NUL, NUL .. "a", string.char(0xFF), "", "b" .. NUL .. "c", string.char(0xC3)}
local t = {}
for i = 1, #KEYS do t[KEYS[i]] = i end
-- pairs 顺序不可比（约定 #57）：收集后排序再打印
local got = {}
for k, v in pairs(t) do got[#got + 1] = k end
table.sort(got)
print("J n=" .. #got)
for i = 1, #got do print("J key " .. esc(got[i]) .. " = " .. tostring(t[got[i]])) end
for i = 1, #KEYS do
  P("J get " .. esc(KEYS[i]), function() return t[KEYS[i]], rawget(t, KEYS[i]) end)
  P("J getmiss " .. esc(KEYS[i]), function() return t[KEYS[i] .. "z"] end)
end
P("J concat", function() return table.concat(KEYS, NUL) end)
P("J concat2", function() return table.concat(KEYS, "-", 2, 4) end)
P("J keynil", function() local u = {} u[NUL] = 1 return #u, u[NUL] end)

print("=== K 错误消息里的 NUL ===")
for i = 1, #KEYS do
  local s = KEYS[i]
  P("K error " .. esc(s), function() return pcall(error, s) end)
  P("K error0 " .. esc(s), function() return pcall(error, s, 0) end)
  P("K error2 " .. esc(s), function() return pcall(error, s, 2) end)
  P("K assert " .. esc(s), function() return pcall(assert, false, s) end)
  P("K xpcall " .. esc(s), function()
    return xpcall(function() error(s) end, function(m) return "H:" .. tostring(m) end)
  end)
  P("K raise " .. esc(s), function()
    local f = load("error(" .. string.format("%q", s) .. ")")
    return pcall(f)
  end)
  P("K badarg " .. esc(s), function() return pcall(string.rep, "a", s) end)
  P("K badarg2 " .. esc(s), function() return pcall(string.sub, "abc", s) end)
  P("K badarg3 " .. esc(s), function() return pcall(math.abs, s) end)
  P("K badarg4 " .. esc(s), function() return pcall(table.insert, {}, 1, s) end)
end

print("=== L utf8 网格 ===")
for i = 1, #KEYS do
  local s = KEYS[i]
  P("L len " .. esc(s), function() return utf8.len(s) end)
  P("L len1 " .. esc(s), function() return utf8.len(s, 1) end)
  P("L len2 " .. esc(s), function() return utf8.len(s, 1, -1) end)
  P("L len9 " .. esc(s), function() return utf8.len(s, 9) end)
  for _, ix in ipairs({-2, -1, 0, 1, 2, 3, 9}) do
    P("L offset " .. esc(s) .. " " .. ix, function() return utf8.offset(s, ix) end)
    P("L offset2 " .. esc(s) .. " " .. ix, function() return utf8.offset(s, ix, 2) end)
  end
  P("L cp1 " .. esc(s), function() return utf8.codepoint(s, 1) end)
  P("L cpall " .. esc(s), function() return utf8.codepoint(s, 1, -1) end)
  P("L cp0 " .. esc(s), function() return utf8.codepoint(s, 0) end)
  local n, cs = 0, {}
  local okc, ec = pcall(function()
    for p, c in utf8.codes(s) do
      n = n + 1
      if n <= 20 then cs[#cs + 1] = p .. ":" .. c end
    end
  end)
  print("L codes " .. esc(s) .. " | ok=" .. tostring(okc) .. " | n=" .. n
      .. " | " .. table.concat(cs, ",") .. " | err=" .. (okc and "nil" or show(ec)))
end
local UCH = {0, 1, 0x7F, 0x80, 0x7FF, 0x800, 0xFFFF, 0x10000, 0x10FFFF, 0x110000, -1, 1.5, 0xD800}
for _, c in ipairs(UCH) do
  P("L char " .. tostring(c), function() return string.byte(utf8.char(c), 1, -1) end)
end
P("L char none", function() return #utf8.char() end)
P("L char str", function() return #utf8.char("65") end)
P("L char bool", function() return #utf8.char(true) end)
print("L pat " .. esc(utf8.charpattern))

print("=== M string.pack / unpack ===")
local FMT = {"z", "s", "s1", "s2", "s4", "c1", "c3", "c8", "i1", "i2", "i4", "b", "B", "j", "T", "x"}
for i = 1, #KEYS do
  local s = KEYS[i]
  for _, f in ipairs(FMT) do
    P("M pack " .. f .. " " .. esc(s), function()
      local p = string.pack(f, s)
      return #p, string.byte(p, 1, -1)
    end)
    P("M packS " .. f .. " " .. esc(s), function()
      local p = string.pack("<" .. f .. " >", s)
      return #p
    end)
  end
  P("M fmt " .. esc(s), function() return string.pack("s", s) end)
end
local PACKS = {"z", "s1", "c3", "i4", "b", "B", "j", "T", "x", "i", "I", "f", "d", "n"}
for _, f in ipairs(PACKS) do
  local okk, blob = pcall(string.pack, f, (f == "z" or f == "s1" or f == "c3") and "ab" or 1)
  if okk then
    P("M unpack " .. f, function()
      local v, i = string.unpack(f, blob)
      return v, i, #blob
    end)
    P("M unpackPad " .. f, function()
      local v, i = string.unpack(f, blob .. NUL .. NUL)
      return v, i
    end)
  else
    print("M packfail " .. f .. " | " .. show(blob))
  end
end
P("M packfmt nul", function() return #string.pack("c4", NUL .. NUL .. NUL .. NUL) end)
P("M unpack z", function()
  local p = string.pack("z", "ab")
  return string.unpack("z", p)
end)

print("=== N load / chunkname ===")
local SRCS = {
  "return 1",
  "return 1" .. NUL,
  NUL .. "return 1",
  "return" .. NUL .. " 1",
  "local x" .. NUL .. "=1 return x",
  "return '" .. NUL .. "'",
  "return [[" .. NUL .. "]]",
  "return '" .. string.char(0xFF) .. "'",
  "return 1 --" .. NUL,
}
for i = 1, #SRCS do
  local s = SRCS[i]
  P("N load " .. esc(s), function()
    local f, e = load(s)
    if f == nil then return nil, e end
    return f(), type(f)
  end)
  P("N loadname " .. esc(s), function()
    local f, e = load(s, NUL .. "n")
    if f == nil then return nil, e end
    return f()
  end)
  P("N loadname2 " .. esc(s), function()
    local f, e = load(s, "a" .. NUL .. "b")
    if f == nil then return nil, e end
    return f()
  end)
  P("N loadbin " .. esc(s), function()
    local f, e = load(s, "=x", "b")
    if f == nil then return nil, e end
    return f()
  end)
  P("N loadtxt " .. esc(s), function()
    local f, e = load(s, "=x", "t")
    if f == nil then return nil, e end
    return f()
  end)
end
P("N dumpname", function()
  local f = load("return 1")
  local d = string.dump(f)
  local g, e = load(d, NUL .. "z", "b")
  if g == nil then return nil, e end
  return g()
end)

print("=== O io 字节往返 ===")
local TMP = "$RT/b.bin"
os.remove(TMP)
P("O write", function()
  local f = assert(io.open(TMP, "wb"))
  f:write(NUL, "a", string.char(0xFF), NUL .. NUL, LF)
  f:close()
  return true
end)
P("O readall", function()
  local f = assert(io.open(TMP, "rb"))
  local s = f:read("a")
  f:close()
  return s
end)
P("O readn", function()
  local f = assert(io.open(TMP, "rb"))
  local a, b, c = f:read(1, 2, 3)
  f:close()
  return a, b, c
end)
P("O readline", function()
  local f = assert(io.open(TMP, "rb"))
  local a, b = f:read("l", "l")
  f:close()
  return a, b
  end)
P("O lines", function()
  local out = {}
  for l in io.lines(TMP) do out[#out + 1] = l end
  return #out, table.concat(out, "|")
end)
P("O size", function()
  local f = assert(io.open(TMP, "rb"))
  local sz = f:seek("end")
  f:close()
  return sz
end)
os.remove(TMP)

print("=== P 协程第二遍 ===")
local function inner()
  local out = {}
  local function add(...) out[#out + 1] = table.concat({...}, ",") end
  local s = "a" .. NUL .. string.char(0xFF)
  add(#s, s:byte(1, -1))
  add(tostring(s < "abc"), tostring(s > "abc"))
  add(string.format("%q", s))
  add(tostring(string.find(s, NUL)))
  add(select(2, pcall(error, s)) == s)
  add(tostring(string.sub(s, 2, 3) == NUL .. string.char(0xFF)))
  local t2 = {}
  t2[s] = 7
  add(tostring(t2[s]))
  return table.concat(out, ";")
end
P("P main", function() return inner() end)
P("P wrap", function()
  local w = coroutine.wrap(function() coroutine.yield(inner()) end)
  return w()
end)
P("P resume", function()
  local c = coroutine.create(function() coroutine.yield(inner()) end)
  return select(2, coroutine.resume(c))
end)
P("P err", function()
  local c = coroutine.create(function() error("a" .. NUL .. "b") end)
  return coroutine.resume(c)
end)
P("P status", function()
  local c = coroutine.create(function() coroutine.yield() end)
  coroutine.resume(c)
  local a = coroutine.status(c)
  coroutine.resume(c)
  return a, coroutine.status(c)
end)
        """);
    }

    /**
     * 片三：chunk 名是任意字节时的 {@code luaL_where} / {@code short_src} /
     * {@code traceback}。
     * <p>
     * 走 {@code assertFullMatches} 而不是 {@code assertMatches}：片三把
     * {@code debug.traceback} 的<b>返回值</b>打印出来了，回溯属于预期输出，
     * 用 {@code assertMatches} 会从回溯处把后面全砍掉（约定 #33）。
     */
    @Test
    void chunkNameByteGridMatchesReference() {
        Diff.assertFullMatches("""

-- 二进制安全矩阵 III：chunk 名是任意字节时的 luaL_where / short_src / traceback
local NUL = string.char(0)
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
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]" end
  if t == "number" then
    local mt = math.type(v)
    if mt == "integer" then return "i" .. tostring(v) end
    if mt == "float" then return "f" .. tostring(v) end
  end
  if t == "nil" or t == "boolean" then return tostring(v) end
  return t .. "(" .. (tostring(v):gsub("0x%x+", "PTR")) .. ")"
end
local function P(tag, fn, ...)
  local r = table.pack(pcall(fn, ...))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

-- chunk 名样本：= / @ / 裸 三种写法，内容含 NUL 与高位字节，长度跨 LUA_IDSIZE
local NAMES = {
  "=x", "=" .. string.char(0xFF), "=" .. NUL .. "ab", "=a" .. NUL .. "b",
  "=abc", "@x.lua", "@" .. string.char(0xFF) .. ".lua", "@" .. NUL .. "f.lua",
  "plain", string.char(0xFF), NUL, "a" .. NUL .. "b",
  "=" .. string.rep("a", 55), "=" .. string.rep("a", 60), "=" .. string.rep("a", 70),
  "@" .. string.rep("b", 55) .. ".lua", "@" .. string.rep("b", 70) .. ".lua",
  string.rep("c", 55), string.rep("c", 70),
  "=" .. string.char(0xE4) .. string.char(0xB8) .. string.char(0xAD),
  "", "=", "@",
}

print("=== Q chunk 名 x error 的位置前缀 ===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  P("Q err " .. esc(nm), function()
    local f = assert(load("error('boom')", nm))
    return f()
  end)
  P("Q err0 " .. esc(nm), function()
    local f = assert(load("error('boom', 0)", nm))
    return f()
  end)
  P("Q err2 " .. esc(nm), function()
    local f = assert(load("local function g() error('boom', 2) end g()", nm))
    return f()
  end)
  P("Q runerr " .. esc(nm), function()
    local f = assert(load("local x = nil return x.y", nm))
    return f()
  end)
  P("Q shortsrc " .. esc(nm), function()
    local f = assert(load("return debug.getinfo(1, 'S').short_src", nm))
    return f()
  end)
  P("Q src " .. esc(nm), function()
    local f = assert(load("return debug.getinfo(1, 'S').source", nm))
    return f()
  end)
end

print("=== R traceback ===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  P("R tb " .. esc(nm), function()
    local f = assert(load("return debug.traceback('m')", nm))
    return f()
  end)
  P("R tbco " .. esc(nm), function()
    local f = assert(load("local co = coroutine.create(function() return debug.traceback() end) return select(2, coroutine.resume(co))", nm))
    return f()
  end)
  P("R uncaught " .. esc(nm), function()
    local f = assert(load("error('boom')", nm))
    return f()
  end)
end
P("R tbmsg", function() return debug.traceback(string.char(0xFF) .. NUL .. "m") end)
P("R tbmsg2", function()
  local co = coroutine.create(function() error("a" .. string.char(0xFF)) end)
  local ok, e = coroutine.resume(co)
  return ok, e, debug.traceback(co)
end)

print("=== S 位置前缀 x 消息里含任意字节 ===")
local MSGS = {"boom", NUL, string.char(0xFF), "a" .. NUL .. "b", string.char(0xED) .. string.char(0xA0)}
for i = 1, #MSGS do
  local m = MSGS[i]
  P("S error " .. esc(m), function() return pcall(error, m) end)
  P("S assert " .. esc(m), function() return pcall(assert, false, m) end)
  P("S runerr " .. esc(m), function()
    local t = setmetatable({}, {__index = function() error(m, 1) end})
    return pcall(function() return t.x + 1 end)
  end)
  P("S concaterr " .. esc(m), function() return pcall(function() return {} .. 1 end) end)
  P("S callerr " .. esc(m), function() return pcall(function() local x = nil x() end) end)
  P("S idxerr " .. esc(m), function() return pcall(function() local x = nil return x[m] end) end)
  P("S cmp " .. esc(m), function() return pcall(function() return {} < {} end) end)
  P("S forerr " .. esc(m), function() return pcall(function() for i = 1, m do end end) end)
end
-- 非字符串错误对象照原样穿出（不能被 latin1 拦下）
for _, v in ipairs({1, 0, true, false, {}}) do
  P("S objerr " .. type(v), function() return pcall(error, v) end)
  P("S objerr0 " .. type(v), function() return pcall(error, v, 0) end)
end
P("S objerr mt", function()
  local t = setmetatable({}, {__tostring = function() return string.char(0xFF) end})
  return pcall(error, t)
end)

print("=== T require / loadfile 的 chunk 名 ===")
os.remove("$RT/m1.lua")
local w = io.open("$RT/m1.lua", "wb")
w:write("return {}" .. string.char(10))
w:close()
P("T require", function()
  package.path = "$RT/?.lua"
  local m = require("m1")
  return type(m)
end)
P("T require2", function()
  package.path = "$RT/?.lua"
  package.loaded["m1"] = nil
  local m, extra = require("m1")
  return type(m), extra
end)
os.remove("$RT/m1.lua")

print("=== U 协程第二遍：整片重跑 ===")
local function body()
  local out = {}
  local B = string.char(0xFF) .. NUL .. "z"
  out[#out + 1] = tostring(#B)
  out[#out + 1] = tostring(B < "a") .. tostring(B > "a")
  out[#out + 1] = esc(string.format("%q", B))
  out[#out + 1] = select(2, pcall(error, B))
  out[#out + 1] = esc(select(2, pcall(assert, false, B)))
  local f = load("error('b')", "=" .. B)
  out[#out + 1] = esc(select(2, pcall(f)))
  out[#out + 1] = esc(load("return debug.getinfo(1,'S').short_src", "=" .. B)())
  return table.concat(out, ";")
end
P("U main", function() return body() end)
P("U co", function()
  local c = coroutine.create(function() coroutine.yield(body()) end)
  return select(2, coroutine.resume(c))
end)
P("U wrap", function()
  local w2 = coroutine.wrap(function() coroutine.yield(body()) end)
  return w2()
end)
P("U xpcall", function()
  return xpcall(function() error(string.char(0xFF)) end,
                function(m) return "H" .. #m end)
end)
        """);
    }


    /**
     * 片四：错误消息里「回流的 Lua 字符串」。
     * <p>
     * 与前三片的差别：前三片修的是位置前缀与回溯，这一片盯的是<b>嵌在消息里的
     * 值本身</b>——{@code varinfo} 反推出的字段名（{@code t["\xff"]} 的键）、
     * 元表的 {@code __name}、{@code require} 的模块名、{@code argError} 反查
     * 出来的函数名。这些值从 Lua 字节串出发、绕一圈 Java String、最后又
     * {@code LuaString.latin1} 编码回 Lua 字符串；中间那一圈用 UTF-8 解码就会
     * 把 {@code 0xFF} 变成 {@code U+FFFD}。
     * <p>
     * 修法与前几片不同：这里没有把每一处的类型都改成 {@code LuaString}
     * （{@code varinfo} 那一串 {@code String[]} 出参牵动十几个函数），而是引入
     * {@code LuaString.toLatin1String()} —— 与 {@code latin1} 互为逆运算，
     * 保证 {@code byte<->char} 在 0..255 上无损往返。代价是这条链上的中间
     * 表示只能是 Latin-1，凡是拿它去拼 Lua 字符串的地方都必须用
     * {@code latin1} 而不是 {@code utf8}（{@code debug.getinfo} 的
     * {@code name} / {@code namewhat}、回溯里的 {@code function 'x'} 就是）。
     * <p>
     * 光有 Latin-1 还不够，<b>名字还要再走一次 {@code toCString()}</b>：
     * {@code W2} 段是专门为此加的——{@code W} 段用变量键（{@code t[k]}），
     * 编译器走寄存器，压根碰不到 {@code kname}；只有写成<b>字面键</b>
     * （{@code load("local t = {} return t['a\0b'].x")}）才会编译成
     * {@code OP_GETFIELD}、由 {@code kname} 报 {@code field}。灵敏度脚本一开始
     * 说「{@code kName} 的改动盯不住」，就是缺了这一段。补上之后 470 行里
     * 咬出 14 处分歧（{@code (field '')} vs {@code (field '<00>')}）。
     * <p>
     * {@code io} / {@code os} 那一段<b>故意不比</b>三件事（参考实测值记在
     * 约定 #57）：文件名含非 UTF-8 字节时，C 把原始字节递给 {@code fopen}，
     * macOS 回 EILSEQ(92)，而 Java 的文件层走 UTF-8、直接建出别的文件；
     * 同理 {@code os.execute} 的命令行；以及 {@code os.setlocale} 设成非 C 之后
     * {@code strcoll} 变成 locale 相关排序（参考在 {@code zh_CN.UTF-8} 下
     * {@code "a" < "B"} 为 <b>true</b>、排序 {@code aAbBcC}，本实现恒字节序）。
     */
    @Test
    void errorMessageEmbeddedValueGridMatchesReference() {
        Diff.assertMatches("""

-- 二进制安全矩阵 IV：错误消息里「回流的 Lua 字符串」
-- 上一片修的是位置前缀与 traceback；这一片盯的是另一类出口：
-- 错误消息里嵌的**值本身**（varinfo 的字段名、__name、模块名、文件名）。
local NUL = string.char(0)
local HEX = "0123456789ABCDEF"
local function hb(b) return HEX:sub((b // 16) + 1, (b // 16) + 1) .. HEX:sub((b % 16) + 1, (b % 16) + 1) end
local function esc(s)
  -- 地址不可比（约定 #43）
  s = s:gsub("0x%x+", function(a) return string.rep("P", #a) end) -- 等长替换：# 也要可比
  return (s:gsub(".", function(c)
    local b = c:byte()
    if b == 91 or b == 93 then return "<" .. hb(b) .. ">" end
    if b < 32 or b > 126 then return "<" .. hb(b) .. ">" end
    return c
  end))
end
local function show(v)
  local t = type(v)
  if t == "string" then return "#" .. #v .. "[" .. esc(v) .. "]" end
  if t == "number" then
    local mt = math.type(v)
    if mt == "integer" then return "i" .. tostring(v) end
    if mt == "float" then return "f" .. tostring(v) end
  end
  if t == "nil" or t == "boolean" then return tostring(v) end
  return t .. "(" .. (tostring(v):gsub("0x%x+", "PTR")) .. ")"
end
local function P(tag, fn, ...)
  local r = table.pack(pcall(fn, ...))
  local out = tag
  for i = 1, r.n do out = out .. " | " .. show(r[i]) end
  print(out .. " | n=" .. r.n)
end

local NAMES = {
  "ok", NUL, "a" .. NUL .. "b", string.char(0xFF), string.char(0xC3) .. string.char(0x28),
  string.char(0xE4) .. string.char(0xB8) .. string.char(0xAD), "", "veryveryverylongname",
}

print("=== V __name（tostring 的类型名）===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  -- 地址长度本身不可比（C 与 JVM 的指针位数不同）：先抹再取长度
  P("V name " .. esc(nm), function()
    return (tostring(setmetatable({}, {__name = nm})):gsub("0x%x+", "PTR"))
  end)
  P("V nameS " .. esc(nm), function()
    return tostring(setmetatable({}, {__name = nm})):gsub("0x%x+", "PTR")
  end)
  P("V arith " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return t + 1
  end)
  P("V call " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return t()
  end)
  P("V idx " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return t.x
  end)
  P("V len " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return #t
  end)
  P("V concat " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return t .. "x"
  end)
  P("V cmp " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    return t < 1
  end)
  P("V newidx " .. esc(nm), function()
    local t = setmetatable({}, {__name = nm})
    t.x = 1
    return "ok"
  end)
end

print("=== W varinfo：错误消息里嵌的字段名 ===")
for i = 1, #NAMES do
  local k = NAMES[i]
  P("W getidx " .. esc(k), function()
    local t = {}
    return t[k].x
  end)
  P("W call " .. esc(k), function()
    local t = {}
    return t[k]()
  end)
  P("W arith " .. esc(k), function()
    local t = {}
    return t[k] + 1
  end)
  P("W concat " .. esc(k), function()
    local t = {}
    return t[k] .. "x"
  end)
  P("W cmp " .. esc(k), function()
    local t = {}
    return t[k] < 1
  end)
  P("W len " .. esc(k), function()
    local t = {}
    return #t[k]
  end)
  P("W newidx " .. esc(k), function()
    local t = {}
    t[k].x = 1
    return "ok"
  end)
  P("W forlimit " .. esc(k), function()
    local t = {}
    for j = 1, t[k] do end
    return "ok"
  end)
  P("W forinit " .. esc(k), function()
    local t = {}
    for j = t[k], 2 do end
    return "ok"
  end)
  P("W upval " .. esc(k), function()
    local t = {}
    local function f() return t[k] + 1 end
    return f()
  end)
  P("W method " .. esc(k), function()
    local t = {}
    return t[k](t, 1)
  end)
  P("W intidx", function()
    local t = {}
    return t[1].x
  end)
  P("W selfidx " .. esc(k), function()
    local t = setmetatable({}, {__index = function(_, kk) error("boom", 1) end})
    return t[k]
  end)
end

print("=== W2 字面键：编译器把它当常量，varinfo 报 constant/field ===")
for i = 1, #NAMES do
  local k = NAMES[i]
  -- 用 load 造源码：字面写进字符串的是真字节，编译器才会走 OP_GETFIELD -> kname
  P("W2 const " .. esc(k), function()
    return load("local t = {} return t['" .. k .. "'].x")()
  end)
  P("W2 arith " .. esc(k), function()
    return load("local t = {} return t['" .. k .. "'] + 1")()
  end)
  P("W2 call " .. esc(k), function()
    return load("local t = {} return t['" .. k .. "']()")()
  end)
  P("W2 set " .. esc(k), function()
    return load("local t = {} t['" .. k .. "'].y = 1 return 'ok'")()
  end)
  P("W2 concat " .. esc(k), function()
    return load("local t = {} return t['" .. k .. "'] .. 'x'")()
  end)
  P("W2 len " .. esc(k), function()
    return load("local t = {} return #t['" .. k .. "']")()
  end)
  P("W2 for " .. esc(k), function()
    return load("local t = {} for j = 1, t['" .. k .. "'] do end return 'ok'")()
  end)
  P("W2 cmp " .. esc(k), function()
    return load("local t = {} return t['" .. k .. "'] < 1")()
  end)
  -- 字段名也要出现在 argError 反查出来的「函数名」里
  P("W2 badself " .. esc(k), function()
    local f = load("local t = {} local m = t['" .. k .. "'] return m.x(1)")
    return f()
  end)
  -- 全局名：_ENV 的字面键
  P("W2 global " .. esc(k), function()
    return load("return _ENV['" .. k .. "'] + 1")()
  end)
end

-- 约定第四条：同一批语料塞进协程再跑一遍。registry / 位置前缀是 global_State
-- 级的，主线程上怎么跑都正常的 bug 只有在这里现形。
print("=== W3 协程第二遍：字面键 varinfo ===")
local function PC(tag, src)
  P(tag, function()
    local co = coroutine.create(assert(load(src)))
    local ok, e = coroutine.resume(co)
    return ok, e
  end)
end
for i = 1, #NAMES do
  local k = NAMES[i]
  PC("W3 const " .. esc(k), "local t = {} return t['" .. k .. "'].x")
  PC("W3 arith " .. esc(k), "local t = {} return t['" .. k .. "'] + 1")
  PC("W3 call " .. esc(k), "local t = {} return t['" .. k .. "']()")
  PC("W3 concat " .. esc(k), "local t = {} return t['" .. k .. "'] .. 'x'")
  PC("W3 global " .. esc(k), "return _ENV['" .. k .. "'] + 1")
  PC("W3 method " .. esc(k), "local t = {} return t['" .. k .. "']:x()")
end
print("=== V2 协程第二遍：__name ===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  P("V2 arith " .. esc(nm), function()
    local co = coroutine.create(function()
      local t = setmetatable({}, {__name = nm})
      return t + 1
    end)
    local ok, e = coroutine.resume(co)
    return ok, e
  end)
  P("V2 cmp " .. esc(nm), function()
    local co = coroutine.create(function()
      local t = setmetatable({}, {__name = nm})
      return t < 1
    end)
    local ok, e = coroutine.resume(co)
    return ok, e
  end)
end

print("=== X require 的模块名 ===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  P("X req " .. esc(nm), function()
    package.path = "$RT/?.lua"
    return require(nm)
  end)
  P("X searchpath " .. esc(nm), function()
    return package.searchpath(nm, "$RT/?.lua")
  end)
  P("X searchpath2 " .. esc(nm), function()
    return package.searchpath("m", nm)
  end)
  P("X searchpath3 " .. esc(nm), function()
    return package.searchpath("m", "$RT/?.lua", nm)
  end)
  P("X searchpath4 " .. esc(nm), function()
    return package.searchpath("m", "$RT/?.lua", ".", nm)
  end)
  P("X searchers " .. esc(nm), function()
    return package.searchers[1](nm)
  end)
  P("X searchers2 " .. esc(nm), function()
    return package.searchers[2](nm)
  end)
  P("X searchers3 " .. esc(nm), function()
    return package.searchers[3](nm)
  end)
  P("X searchers4 " .. esc(nm), function()
    return package.searchers[4](nm)
  end)
  P("X preload " .. esc(nm), function()
    package.preload[nm] = function() return 1 end
    return require(nm)
  end)
end

print("=== Y io / os 的文件名 ===")
for i = 1, #NAMES do
  local fn = NAMES[i]
  P("Y open " .. esc(fn), function() return io.open(fn) end)
  P("Y lines " .. esc(fn), function() return io.lines(fn) end)
  P("Y remove " .. esc(fn), function() return os.remove(fn) end)
  P("Y rename " .. esc(fn), function() return os.rename(fn, fn .. "2") end)
  P("Y getenv " .. esc(fn), function() return os.getenv(fn) end)
end
print("=== Z 消息里嵌值的其他出口 ===")
for i = 1, #NAMES do
  local v = NAMES[i]
  -- 约定 #80：debug.setmetatable 改的是全局类型元表，用完必须还原原值
  P("Z setmeta " .. esc(v), function()
    local old = debug.getmetatable(v)
    local ok, e = pcall(debug.setmetatable, v, {})
    debug.setmetatable(v, old)
    return ok, e
  end)
  P("Z getmeta " .. esc(v), function() return getmetatable(v) end)
  P("Z setmeta2 " .. esc(v), function()
    return debug.setmetatable(nil, v)
  end)
  P("Z badarg " .. esc(v), function() return pcall(string.rep, "a", v) end)
  P("Z badarg2 " .. esc(v), function() return pcall(string.format, v) end)
  P("Z badarg3 " .. esc(v), function() return pcall(io.open, {}, v) end)
  P("Z badarg4 " .. esc(v), function() return pcall(os.setlocale, v, "x") end)
  P("Z loadmode " .. esc(v), function() return load("return 1", "=x", v) end)
  P("Z gsub " .. esc(v), function() return ("abc"):gsub("b", v) end)
  P("Z gsubtbl " .. esc(v), function()
    local t = setmetatable({}, {__index = function(_, k) return k .. "!" end})
    return ("abc"):gsub("b", t)
  end)
  P("Z sortcmp " .. esc(v), function()
    return pcall(table.sort, {1, 2, 3}, v)
  end)
end

-- 下面三段是灵敏度脚本点出来的「用例盯不住」的补测：
-- findfield 的名字、<close> 变量名、loadfile 的 mode。
-- 前两条都要绕开「pairs 顺序不可比」（约定 #43）与「标识符只能 ASCII」。
print("=== AA findfield：package.loaded 里反查出来的函数名 ===")
for i = 1, #NAMES do
  local nm = NAMES[i]
  -- 名字必须**唯一可达**：只挂在 package.loaded 下、不进 _G，
  -- 否则 findfield 撞上 _G 的先后由 lua_next 决定，顺序不可比。
  P("AA tb " .. esc(nm), function()
    local inner = function() return debug.traceback("MSG") end
    local m = {}
    m[nm] = inner
    package.loaded["M" .. nm] = m
    return inner()
  end)
  P("AA getinfo " .. esc(nm), function()
    local inner = function() return 1 end
    local m = {}
    m[nm] = inner
    package.loaded["M" .. nm] = m
    -- 走同一条 pushglobalfuncname，但出口是 getinfo 的 name
    local co = coroutine.create(function() return inner() end)
    return select(2, coroutine.resume(co))
  end)
end

print("=== AB <close> 变量名：只有二进制 chunk 能带非 ASCII 局部名 ===")
-- 语法里的标识符只能 ASCII，想让局部名含高位字节必须改 dump；
-- 等长替换（zzzz -> 4 字节）保证 dump 仍然合法。
for _, v in ipairs({ "nil", "1", "'x'", "{}", "io.stdout" }) do
  local src = "local zzzz <close> = " .. v .. " return 'ok'"
  P("AB src " .. v, function() return load(src)() end)
  local d1 = string.dump(load(src))
  P("AB dump " .. v, function() return load(d1)() end)
  local d2 = d1:gsub("zzzz", "\255\254\253\252")
  P("AB patched " .. v, function() return load(d2)() end)
end
-- 局部名含 NUL：C 这边是 %s，照样在第一个 \0 处断
P("AB nulname", function()
  local d = string.dump(load("local zzzz <close> = 1 return 'ok'")):gsub("zzzz", "a\0b\0")
  return load(d)()
end)

print("=== AC loadfile 的 mode：原样拼进错误消息 ===")
-- 约定 #41：自己建临时文件，别读 $RT/case.lua（那个文件的内容随测试而异）
os.remove("$RT/ac.lua")
local w = assert(io.open("$RT/ac.lua", "wb"))
w:write("return 1\n")
w:close()

for i = 1, #NAMES do
  local md = NAMES[i]
  P("AC loadfile " .. esc(md), function() return loadfile("$RT/ac.lua", md) end)
  P("AC loadfile2 " .. esc(md), function() return loadfile("$RT/nope.lua", md) end)
  P("AC load " .. esc(md), function() return load("return 1", "=x", md) end)
end
        """);
    }
}
