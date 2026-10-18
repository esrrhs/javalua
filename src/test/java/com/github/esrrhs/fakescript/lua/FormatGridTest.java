package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeAll;

import java.io.File;

/**
 * {@code string.format} 的全网格差分 —— 阶段 12。
 * <p>
 * 为什么单开一类：{@code format} 是本实现里唯一一处「照 C99 抄一遍 printf」的
 * 代码，而 C 的每一条特殊规则都是踩出来的（约定 #15–#21）——浮点只走
 * {@code BigDecimal}、同一个转换只有一份实现、校验顺序因动词而异、整数动词
 * 一给精度 {@code 0} 标志就失效。规则之间是<b>相乘</b>的，手挑用例盖不住
 * 「动词 × 标志 × 宽度 × 精度 × 值」这个五维。
 * <p>
 * 十三段（6791 行）：
 * <ul>
 *   <li>{@code A}/{@code B}：18 个动词 × 39 个数值 / 10 个非数值；</li>
 *   <li>{@code C}/{@code D}/{@code E}/{@code F}：标志（20 种组合，含乱序
 *       {@code "0#-+"}）× 宽度 × 精度 × 动词 × 值——{@code 0} 标志那一条是坑
 *       （约定 #19），{@code F} 段专铺「宽度+精度+标志」三件套；</li>
 *   <li>{@code G}/{@code M}：{@code %q} 的 5.4 新语义与<b>往返</b>
 *       （{@code load} 回来还是不是原值）；</li>
 *   <li>{@code H}：{@code %s}/{@code %c} 的字节安全（含 {@code \0}、
 *       {@code 0xFF}、空串）；</li>
 *   <li>{@code I}/{@code J}：畸形格式串与参数个数/类型；</li>
 *   <li>{@code K}：<b>协程第二遍</b>（约定 #7——{@code registry} 级的东西只在
 *       协程里现形）；</li>
 *   <li>{@code L}：{@code %a} × {@code #} × 2 的整数次幂 / 次正规 / 非有限。</li>
 * </ul>
 * <p>
 * 这一片咬出三个 bug，都在 {@code %a} 上：
 * <ol>
 *   <li><b>{@code #} 标志下小数点必须留着</b>：{@code ("%#a"):format(0.0)} 是
 *       {@code 0x0.p+0}。原实现只在非零分支照这条写，零值分支漏了；</li>
 *   <li><b>{@code %q} 与 {@code %a} 各写了一份</b>：同一个转换两份实现，次正规
 *       数上给出两种答案（约定 #16）；</li>
 *   <li><b>{@code inf}/{@code nan} 不参与零填充</b>：
 *       {@code ("%020.5a"):format(math.huge)} 是 17 个空格加 {@code inf}，不是
 *       17 个 {@code 0}。glibc 的 {@code printf_fphex.c} 在 {@code special}
 *       分支里直接 {@code pad(buf, ' ', width)}，根本不看 {@code pad == '0'}。</li>
 * </ol>
 * <p>
 * <b>两批用例刻意不打</b>（两边判据一致，跳过的就是同一批）：
 * <ul>
 *   <li><b>次正规数的 {@code %a}/{@code %A}/{@code %q}</b>：C99 把次正规的写法
 *       留作 <i>unspecified</i>，glibc 保留机器编码（{@code 0x0.…p-1022}）、
 *       Apple libc 先规格化（{@code 0x1p-1074}）——约定 #52；</li>
 *   <li><b>带精度的 {@code %a} 里「舍弃的第一位十六进制数字恰为 8」</b>：glibc
 *       是就近舍入 + 平局取偶，Apple libc 在这一段根本不是就近舍入
 *       （{@code ("%.4a"):format(0x1.500082p0)} 给 {@code 0x1.5000p+0}，
 *       {@code 0x82} 明明大于半 {@code 0x80} 却舍了下去）。</li>
 * </ul>
 * 这两批由 {@link StringLibTest#hexFloatPrecisionRoundingFollowsGlibc} 与
 * {@link StringLibTest#subnormalQuoteFollowsGlibc} 用普通断言钉住 glibc 的答案
 * ——CI 的一致性闸门跑在 ubuntu 上，glibc 才是裁判。
 * <p>
 * 写同类探针要记住的两个坑：
 * <ul>
 *   <li><b>{@code string.find(s, "%.", 1, true)} 找的是两个字 {@code "%."}</b>
 *       ——{@code plain=true} 时不做模式匹配。第一版这么写，于是
 *       {@code precof} 恒返回 {@code nil}，该跳过的平局用例一条没跳过；</li>
 *   <li><b>判据函数不能抛错</b>：{@code ("%a"):format('abcdef')} 会报
 *       {@code bad argument}，而 {@code skip} 在 {@code pcall} 外面跑，
 *       一抛就整个脚本断在半路——两端输出还「一致」，是假的一致。</li>
 * </ul>
 */
class FormatGridTest {

    @BeforeAll
    static void prepare() {
        new File("target/rt").mkdirs();
    }

    /** 动词 × 标志 × 宽度 × 精度 × 值。 */
    @Test
    void stringFormatGridMatchesReference() {
        Diff.assertMatches("""
-- string.format 全网格：动词 × 标志 × 宽度 × 精度 × 值
-- 起因：format 是本实现里少数「照 C99 抄一遍 printf」的地方，而 C 的每一条
-- 特殊规则都是踩出来的（约定 #15-#21）：格式化只走 BigDecimal、同一个转换
-- 只有一份实现、校验顺序因动词而异、整数动词一指定精度 0 标志就失效……
-- 手挑用例覆盖不到「标志 × 动词 × 精度」这个三维。
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
    -- NaN 的符号是 libc 自己的（约定 #96）：x86 默认 NaN 符号位是 1，glibc
    -- 打 "-nan"（%+f 也打 "-nan"），Apple libc 打 "nan"。抹掉符号只是第一步
    -- —— glibc 填的是 4 个字符、Apple 填的是 3 个，同样 width=20，前者补
    -- 16 个空格、后者补 17 个；符号抹平之后<b>总长度就差一格</b>
    -- （"%20.5a":format(0/0) 参考给 19 列、我们给 20 列）。
    -- 所以带填充的 nan 连「填了几个空格」一起让出去，只比「填的是空格、
    -- 内容是 nan、填在哪一侧」。inf 不受影响：两边都是 3 个字符。
    -- 第一版写成 ^(%s*)(nan)(%s*)$ 的<b>整串</b>匹配，漏掉两种形状：
    -- 结果后面还跟着别的字（"%-10.2f|" 的哨兵 "|"、"(0/0) -> nan" 这种
    -- 前后带东西的）。改成「nan 前后那两段空格」分别压 —— 只要是贴着 nan
    -- 的连续空格就压成一个 <PAD>，在哪一侧照样看得出来。
    s = s:gsub("(%s+)([%-%+]?[nN][aA][nN])", "<PAD>%2")
    s = s:gsub("([%-%+]?[nN][aA][nN])(%s+)", "%1<PAD>")
    s = s:gsub("[%-%+]nan", "nan"):gsub("[%-%+]NAN", "NAN")
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

-- ===== 平台相关的两批：差分表里直接不打（两边判据一致，跳过的就是同一批）=====
--
-- ① 次正规数的 %a / %A / %q。C99 只规定「小数点左边恰好一位十六进制数字」，
--    并且「这个数字只在数次正规时为 0，其它情况取值由实现定义」——次正规怎么写
--    属于 unspecified。glibc（CI 的 ubuntu）忠实于机器编码，5e-324 给
--    0x0.0000000000001p-1022；Apple libc / musl 先规格化，给 0x1p-1074。
--    CI 跑 ubuntu，取 glibc，所以这批不拿 macOS 的参考实现来比。
--
-- ② 带精度的 %a 里「舍弃的第一位十六进制数字恰为 8」的那批。glibc 的判据
--    （stdio-common/printf_fphex.c）是 round_away(negative, last_digit & 1,
--    next_digit >= 8, more_bits, mode)，而 round_away 在 FE_TONEAREST 下就是
--    half_bit && (last_digit_odd || more_bits)——就近舍入、平局取偶。
--    Apple libc 在这一段根本不是就近舍入，错得可以量化：
--      ("%.4a"):format(0x1.500082p+0) -> 0x1.5000p+0   （0x82 > 0x80 却舍了）
--      ("%.4a"):format(0x1.500081p+0) -> 0x1.5001p+0   （反倒进了）
--    它的判据成了「第二位舍弃数字的奇偶」。两条在 glibc 上都该进位。
local MINNORMAL = 2 ^ -1022
local function num(e) return load("return " .. e)() end
local function is_subnormal(x)
  return math.type(x) == "float" and x ~= 0 and math.abs(x) < MINNORMAL
end
-- 不给精度时的十六进制展开（精确，不含 p 之后的部分）
local function hexfrac(x)
  local s = ("%a"):format(x)
  local d = s:find(".", 1, true)
  if not d then return "" end
  return (s:sub(d + 1):gsub("p.*$", ""))
end
local function skip(verb, x, prec)
  if verb ~= "a" and verb ~= "A" and verb ~= "q" then return false end
  -- 非数值的 %a 会报错，那是另一条用例（E 段有 'abcdef'），不该在这里炸掉
  if type(x) ~= "number" then return false end
  if is_subnormal(x) then return true end
  -- %q 不给精度（snprintf("%a")），不舍入；prec 为 nil 同理
  if verb == "q" or prec == nil then return false end
  local f = hexfrac(x)
  return #f > prec and f:sub(prec + 1, prec + 1) == "8"
end
-- 格式串里的精度；没有小数点就是不给精度，有小数点但没数字就是 0
local function precof(spec)
  local i = spec:find(".", 1, true)
  if not i then return nil end
  local d = spec:match("^%d+", i + 1)
  return d and tonumber(d) or 0
end
-- 打一条；平台相关的直接跳过
local function PQ(tag, verb, x, prec, fn)
  if skip(verb, x, prec) then return end
  P(tag, fn)
end

local NUMS = {
  "0", "1", "-1", "42", "-42", "255", "-255", "1000000",
  "math.maxinteger", "math.mininteger", "math.maxinteger + 0.0",
  "0.0", "-0.0", "1.5", "-1.5", "0.5", "2.5", "3.5",
  "1e300", "-1e300", "1e-300", "math.pi", "1 / 3", "2 ^ 53", "2 ^ 53 + 1.0",
  "0.1", "1e15", "1e16", "1e-5", "123456.789",
  "math.huge", "-math.huge", "0 / 0", "5e-324", "2 ^ -1074",
  "1.7976931348623157e308", "0x7fffffff", "3.0", "-3.0",
}
local NONNUMS = { "nil", "true", "false", "'str'", "''", "'a\\\\0b'", "'\\\\255\\\\254'",
                  "{}", "print", "coroutine.create(function() end)" }

local VERBS = { "d", "i", "u", "o", "x", "X", "e", "E", "f", "F", "g", "G",
                "a", "A", "c", "s", "q", "%" }

print("=== A 动词 × 数值（无标志无宽无精度）===")
for i = 1, #VERBS do
  local v = VERBS[i]
  for j = 1, #NUMS do
    local e = NUMS[j]
    local x = num(e)
    PQ("A " .. v .. " " .. j .. " " .. e, v, x, nil, function()
      return string.format("%" .. v, x)
    end)
  end
end

print("=== B 动词 × 非数值 ===")
for i = 1, #VERBS do
  local v = VERBS[i]
  for j = 1, #NONNUMS do
    local e = NONNUMS[j]
    P("B " .. v .. " " .. j .. " " .. e, function()
      return string.format("%" .. v, load("return " .. e)())
    end)
  end
end

print("=== C 标志 × 动词 × 值 ===")
-- C99 五个标志：- + 空格 0 #。取十六种组合（含空）
local FLAGS = { "", "-", "+", " ", "0", "#", "-+", "- ", "-0", "-#", "+ ",
                "+0", "+#", " 0", " #", "0#", "-+0", "-+#", "-+ 0", "0#-+ " }
local CVALS = { "42", "-42", "1.5", "0.0", "math.maxinteger", "1 / 3", "255" }
for i = 1, #FLAGS do
  local f = FLAGS[i]
  for j = 1, #VERBS do
    local v = VERBS[j]
    for k = 1, #CVALS do
      local x = num(CVALS[k])
      PQ("C '" .. f .. "' " .. v .. " " .. k, v, x, precof("%" .. f .. v), function()
        return string.format("%" .. f .. v, x)
      end)
    end
  end
end

print("=== D 宽度 × 动词 × 值 ===")
local WIDTHS = { "", "0", "1", "3", "8", "20", "40" }
local DVALS = { "42", "-42", "1.5", "'ab'" }
for i = 1, #WIDTHS do
  local w = WIDTHS[i]
  for j = 1, #VERBS do
    local v = VERBS[j]
    for k = 1, #DVALS do
      P("D '" .. w .. "' " .. v .. " " .. k, function()
        return string.format("%" .. w .. v, load("return " .. DVALS[k])())
      end)
    end
  end
end

print("=== E 精度 × 动词 × 值 ===")
-- 约定 #19：整数动词一旦指定精度，0 标志就失效
local PRECS = { "", ".", ".0", ".1", ".3", ".5", ".14", ".17", ".20", ".40" }
local EVALS = { "42", "-42", "1.5", "0.0", "1 / 3", "255", "'abcdef'" }
for i = 1, #PRECS do
  local p = PRECS[i]
  for j = 1, #VERBS do
    local v = VERBS[j]
    for k = 1, #EVALS do
      local x = num(EVALS[k])
      PQ("E '" .. p .. "' " .. v .. " " .. k, v, x, precof("%" .. p .. v), function()
        return string.format("%" .. p .. v, x)
      end)
    end
  end
end

print("=== F 宽度 × 精度 × 标志 × 动词（0 标志那一条是坑）===")
local FWP = { "5.3", "05.3", "-05.3", "5.0", "05.0", "0.3", "8.2", "08.2", "-8.2",
              "20.17", "020.17", "1.1", "0.0", "10.10", "3.40" }
local FVALS = { "42", "-42", "1.5", "1 / 3" }
for i = 1, #FWP do
  local wp = FWP[i]
  for j = 1, #VERBS do
    local v = VERBS[j]
    for k = 1, #FVALS do
      local x = num(FVALS[k])
      PQ("F '" .. wp .. "' " .. v .. " " .. k, v, x, precof("%" .. wp .. v), function()
        return string.format("%" .. wp .. v, x)
      end)
    end
  end
end

print("=== G %q（5.4 的新语义）===")
local QVALS = { "nil", "true", "false", "0", "-0.0", "1.5", "-1.5", "1e300",
                "1e-300", "math.pi", "math.huge", "-math.huge", "0 / 0",
                "0x10", "1e15", "1e16", "'str'", "''", "'a\\\\0b'", "'\\\\255'",
                "'\\\\n\\\\r\\\\t\\\\\\\\\\\\\\"'", "math.maxinteger", "math.mininteger",
                "1 / 3", "2 ^ 63", "5e-324" }
for i = 1, #QVALS do
  local e = QVALS[i]
  local x = num(e)
  PQ("G " .. i .. " " .. e, "q", x, nil, function()
    return string.format("%q", x)
  end)
  PQ("G r " .. i .. " " .. e, "q", x, nil, function()
    -- 往返：%q 出来的东西应当能 load 回原值
    local s = string.format("%q", x)
    local back = load("return " .. s)
    if back == nil then return "NOLOAD " .. show(s) end
    return tostring(s) .. " -> " .. tostring(back())
  end)
end

print("=== H %s / %c 的字节安全 ===")
local SVALS = { "''", "'a'", "'a\\\\0b'", "'\\\\255\\\\254'", "'\\\\0'", "'\\\\n'",
                "string.rep('x', 30)", "'\\\\1\\\\2\\\\3'" }
for i = 1, #SVALS do
  local e = SVALS[i]
  for _, spec in ipairs({ "%s", "%10s", "%-10s", "%.3s", "%10.3s", "%-10.3s",
                          "%.0s", "%1s", "%100s" }) do
    P("H " .. i .. " " .. spec, function()
      return string.format(spec, load("return " .. e)())
    end)
  end
  P("H tostring " .. i, function()
    return tostring(load("return " .. e)())
  end)
end
for _, e in ipairs({ "65", "0", "255", "-1", "1.5", "65.0", "'A'", "math.maxinteger" }) do
  for _, spec in ipairs({ "%c", "%5c", "%-5c", "%.2c" }) do
    P("H c " .. spec .. " " .. e, function()
      return string.format(spec, load("return " .. e)())
    end)
  end
end

print("=== I 畸形 / 边界格式串 ===")
local BAD = { "%", "%z", "%l", "%y", "%5", "%.", "%-%", "%%", "%%d", "%d%",
              "%1$s", "%*d", "abc%d", "%1000d", "%-0d", "% d", "%+d", "%#o",
              "%#x", "%#X", "%#.0o", "%#g", "%#.0g", "%#f", "%5.2s", "%.2",
              "%999999999999d", "%-999999999999d", "%.999999999999f",
              "%-", "%+", "% ", "%0", "%#", "%..1d", "%1.1.1d", "%--d",
              "%-5", "%05%", "%s%s", "%d%d", "%\\\\0d", "%\\\\255" }
for i = 1, #BAD do
  local f = BAD[i]
  P("I " .. i .. " " .. esc(f), function() return string.format(f) end)
  P("I a " .. i .. " " .. esc(f), function() return string.format(f, 42) end)
  P("I b " .. i .. " " .. esc(f), function() return string.format(f, 42, "x", 1.5) end)
end

print("=== J 参数个数与类型 ===")
P("J extra", function() return string.format("%d", 1, 2, 3) end)
P("J extra2", function() return string.format("%d %d", 1, 2, 3, 4) end)
P("J few", function() return string.format("%d %d", 1) end)
P("J none", function() return string.format("%d") end)
P("J empty", function() return string.format("") end)
P("J plain", function() return string.format("abc") end)
P("J nonstr", function() return string.format(42) end)
P("J nil0", function() return string.format(nil) end)
P("J nilarg", function() return string.format("a") end)
for _, a in ipairs({ "nil", "true", "42", "1.5", "{}", "print" }) do
  P("J f " .. a, function() return string.format(load("return " .. a)()) end)
  P("J f2 " .. a, function() return string.format("%d", load("return " .. a)()) end)
  P("J f3 " .. a, function() return string.format("%s", load("return " .. a)()) end)
end
P("J noself", function() return string.format() end)

print("=== K 协程第二遍：整片重跑 ===")
local function body()
  local out = {}
  for i = 1, #VERBS do
    for j = 1, #NUMS do
      local v = VERBS[i]
      local x = num(NUMS[j])
      if not skip(v, x, nil) then
        out[#out + 1] = show(select(2, pcall(string.format, "%" .. v, x)))
      end
    end
  end
  for i = 1, #FWP do
    for j = 1, #VERBS do
      local v = VERBS[j]
      local spec = "%" .. FWP[i] .. v
      if not skip(v, 42, precof(spec)) then
        out[#out + 1] = show(select(2, pcall(string.format, spec, 42)))
      end
    end
  end
  return table.concat(out, ";")
end
P("K main", function() return body() end)
P("K co", function()
  local c = coroutine.create(function() return body() end)
  return select(2, coroutine.resume(c))
end)
P("K wrap", function()
  local w = coroutine.wrap(function() return body() end)
  return w()
end)

print("=== L %a × # × 2 的整数次幂 / 次正规 / 非有限 ===")
-- 咬出三个 bug 的地方：①# 标志下小数点必须留着（零与 2 的整数次幂上才看得出
-- 来）；②%q 与 %a 必须是同一个转换（之前两份实现，次正规数上给出两种答案）；
-- ③inf/nan 不参与零填充（%020.5a 给 17 个空格加 inf，不是 17 个 0）。
local AVALS = { "0.0", "-0.0", "1.0", "2.0", "4.0", "0.5", "5e-324",
                "2 ^ -1074", "2 ^ -1022", "1 / 3", "math.pi", "1e300",
                "math.huge", "-math.huge", "0 / 0", "255.0", "16.0" }
local ASPECS = { "%a", "%#a", "%.0a", "%#.0a", "%.3a", "%#.3a", "%A", "%#A",
                 "%#.0A", "%20.5a", "%-20.5a", "%#20.5a", "%020.5a", "%q" }
for i = 1, #AVALS do
  local x = num(AVALS[i])
  for j = 1, #ASPECS do
    local spec = ASPECS[j]
    local verb = spec:sub(-1)
    PQ("L " .. i .. " " .. j .. " " .. spec .. " " .. AVALS[i], verb, x,
       precof(spec), function()
      return string.format(spec, x)
    end)
  end
end

print("=== M %q 往返：load 回来还是不是原值 ===")
local QV2 = { "0.0", "-0.0", "1.0", "-1.0", "5e-324", "-5e-324", "2 ^ -1022",
              "1e308", "1e-308", "math.pi", "1 / 3", "math.maxinteger",
              "math.mininteger", "2 ^ 53", "0.1", "1e15", "1e16", "math.huge",
              "-math.huge", "0 / 0", "'a\\\\0b'", "'\\\\255'", "''" }
for i = 1, #QV2 do
  local e = QV2[i]
  local v = num(e)
  PQ("M " .. i .. " " .. e, "q", v, nil, function()
    local s = string.format("%q", v)
    local back = load("return " .. s)
    if back == nil then return "NOLOAD " .. show(s) end
    local bv = back()
    return show(s) .. " -> " .. show(bv) .. " same=" .. tostring(bv == v or (bv ~= bv and v ~= v))
  end)
end
        """);
    }
}
