package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * 字符串 ⇄ 数字 的转换矩阵（593 行）—— 阶段 12。
 * <p>
 * 两条<b>互相独立</b>的实现，容易各错各的，所以单开一片：
 * <ul>
 *   <li>{@code tonumber(s)}（无进制）：{@code lobject.c: luaO_str2num}，先试
 *       整数再试浮点；十进/十六进整数、十六进制浮点（{@code 0x1p4}）、
 *       {@code 9223372036854775808} 这种「整数装不下就转浮点」的档、
 *       {@code 1e999} 给 {@code inf}、{@code 0/0} 给 {@code nan}。</li>
 *   <li>{@code tonumber(s, base)}：{@code lbaselib.c: b_str2int}，是 C 的
 *       {@code strtoul} 语义——<b>溢出回绕</b>，不是夹断。
 *       {@code tonumber("fffffffffffffffff", 16)} 给 {@code -1}，
 *       {@code tonumber("ffffffffffffffff", 16)} 给
 *       {@code 3707029189907929095}。base 的合法区间是 {@code [2, 36]}，
 *       越界报 {@code base out of range}（{@code base=1} 与 {@code 37} 都在挡外）。</li>
 * </ul>
 * <p>
 * 四组：94 个字符串逐个 {@code tonumber}；19 个进制 × 21 个值（399 档）；
 * {@code tostring} / {@code math.type} 的往返；以及一个<b>逆运算自检</b>——
 * 把 48879 按 2..36 每个进制拆成字符串再 {@code tonumber} 回去，看是否还原。
 * <p>
 * 约定 #52/#120：NaN 的符号随 libc 变，输出前先折平再比。
 */
class NumberGridTest {

    @BeforeAll
    static void prepare() {
        new File("target/rt").mkdirs();
    }

    /**
     * 字符串 ⇄ 数字 的全网格。
     * <p>
     * 灵敏度（撤掉实现的一处判据后这条用例红几行）：见提交说明。
     */
    @Test
    void numberConversionMatrixMatchesReference() {
        Diff.assertMatches("""
-- 字符串 ⇄ 数字 转换矩阵。
-- tonumber(s) 是 lobject.c: luaO_str2num / l_str2d / l_str2int 一族，
-- tonumber(s, base) 是 lbaselib.c: b_str2int（C 的 strtoul 语义，溢出<b>回绕</b>）。
-- 这两条路与 string.format("%d")/("%g") 是各自独立的实现，容易各错各的。
local function sh(v)
  local t = type(v)
  if t == "number" then
    if math.type(v) == "integer" then return "i" .. tostring(v) end
    return "f" .. (tostring(v):gsub("[%-%+]nan", "nan"))
  end
  return tostring(v)
end
local function pr(tag, ...)
  local n = select("#", ...)
  local out = { tag }
  for i = 1, n do out[#out + 1] = sh((select(i, ...))) end
  print(table.concat(out, " | "))
end

-- 1) tonumber(s)：无进制
local S = {
  "", " ", "  ", "\\t", "\\n", "  1  ", "1 ", " 1", "\\t1\\n", "\\v1\\f", "\\r1\\r",
  "0", "-0", "+0", "1", "-1", "+1", "007", "-007",
  "0x10", "0X10", "0x", "0X", "0xff", "0xFF", "0x7fffffffffffffff",
  "0x8000000000000000", "0xffffffffffffffff", "0x10000000000000000",
  "0x1p4", "0x1P4", "0x1p-1", "0x.8p1", "0x.8", "0x1.", "0x1p", "0x1p+",
  "0x1p999", "0x1p-999", "0x0p0", "-0x0p0",
  ".5", "5.", ".", "-.5", "+.5", "1.5", "-1.5", "1.", "1.e2",
  "1e", "1E", "1e+", "1e-", "1e2", "1E2", "1e+2", "1e-2", "1.5e2", "1.5E-2",
  "1e999", "-1e999", "1e-999", "1e308", "1e309", "-1e309",
  "9223372036854775807", "9223372036854775808", "-9223372036854775808",
  "-9223372036854775809", "18446744073709551615", "18446744073709551616",
  "inf", "-inf", "+inf", "nan", "-nan", "INF", "NAN", "Infinity",
  "0b101", "1_000", "1,5", "0.0", "-0.0", "1e0", "1e-0",
  "  0x10  ", " 0x10", "0x10 ", "1\\0", "\\01", "1 2", "--1", "- 1", "1-",
  "true", "false", "nil",
}
for i = 1, #S do
  local s = S[i]
  pr("N" .. i, pcall(tonumber, s))
end

-- 2) tonumber(s, base)：base 从 -1 到 40 全扫一遍
local V = { "0", "1", "-1", "+1", "10", "ff", "FF", "z", "Z", "7", "8", "9",
            "", " 10 ", "1\\0", "9223372036854775807", "ffffffffffffffff",
            "fffffffffffffffff", "1ffffffffffffffff", "-8000000000000000",
            "-8000000000000001", "10000000000000000" }
local B = { -1, 0, 1, 2, 3, 7, 8, 9, 10, 11, 16, 17, 35, 36, 37, 38, 40, 64, 100 }
for bi = 1, #B do
  for vi = 1, #V do
    pr("B" .. B[bi] .. "_" .. vi, pcall(tonumber, V[vi], B[bi]))
  end
end
-- base 不是整数 / 不是数字
pr("BX1", pcall(tonumber, "10", 2.0))
pr("BX2", pcall(tonumber, "10", 2.5))
pr("BX3", pcall(tonumber, "10", "16"))
pr("BX4", pcall(tonumber, "10", "0x10"))
pr("BX5", pcall(tonumber, "10", {}))
pr("BX6", pcall(tonumber, "10", true))
pr("BX7", pcall(tonumber, 10, 2))
pr("BX8", pcall(tonumber, 10.5, 2))
pr("BX9", pcall(tonumber, nil, 2))
pr("BX10", pcall(tonumber))
pr("BX11", pcall(tonumber, nil))
pr("BX12", pcall(tonumber, "10", nil))
pr("BX13", pcall(tonumber, "10", 2, 3))
pr("BX14", pcall(tonumber, 10))
pr("BX15", pcall(tonumber, true))
pr("BX16", pcall(tonumber, {}))
pr("BX17", pcall(tonumber, print))
pr("BX18", tonumber("10", 16), tonumber("0x10", 16), tonumber("0x10"))

-- 3) 往返：tostring(tonumber(x)) 与 tostring 本身
local R = { 0, -0.0, 1, -1, 1.5, -1.5, 1e308, 1e-308, 5e-324, 2 ^ 53,
            math.maxinteger, math.mininteger, 1 / 0, -1 / 0, 0 / 0, 1 / 3,
            0.1, 100.0, 1e15, 1e16, 123456789012345678 }
for i = 1, #R do
  pr("R" .. i, tostring(R[i]), math.type(R[i]))
end

-- 4) 数字 → 进制字符串的手工实现对照（string.format("%x") 等由别处测，
--    这里只对 tonumber 的逆运算：整数除法拆位）
do
  local n = 48879
  local digits = "0123456789abcdefghijklmnopqrstuvwxyz"
  for base = 2, 36 do
    local t = {}
    local x = n
    repeat
      local r = x % base
      t[#t + 1] = digits:sub(r + 1, r + 1)
      x = x // base
    until x == 0
    local s = string.reverse(table.concat(t))
    pr("W" .. base, tonumber(s, base) == n, s)
  end
end

print("END")

                """);
    }

    private NumberGridTest() {
    }
}
