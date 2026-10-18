package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * 运算符 × 操作数类型 的差分矩阵（1794 行）—— 阶段 12。
 * <p>
 * 起因：{@code ArithTest} 是 <b>Java 单测</b>（调 {@code Vm} 里的几个静态方法），
 * 没有一条是「同一份脚本在参考实现上跑一遍比输出」。而算术这块最容易错的恰恰
 * 是<b>强制转换</b>与<b>结果类型</b>，单测看不出来。
 * <p>
 * 四组：
 * <ul>
 *   <li><b>二元运算符 × 9 种操作数</b>（18 × 81 = 1458 档）：整数、浮点、
 *       整数值的浮点、数字字符串、非数字字符串、布尔、nil、表。
 *       每行比的是<b>结果的 {@code math.type}</b> —— 整数运算给整数、
 *       浮点给浮点，字符串参与算术一律先转数。</li>
 *   <li><b>一元运算符</b>（{@code # - ~ not} 与 {@code tostring} /
 *       {@code tonumber}）× 那 9 种操作数。</li>
 *   <li><b>整除 / 取模 / 幂 的边界</b>：除零（整数的 {@code //} 与 {@code %}
 *       报错、浮点的 {@code /} 给 {@code inf}）、
 *       {@code mininteger // -1} <b>回绕</b>成 {@code mininteger}、
 *       {@code maxinteger + 1} 回绕成 {@code mininteger}。</li>
 *   <li><b>位移</b>：位数 {@code >= 64} 给 <b>0</b>（<b>不是</b>取模 64），
 *       负数反向移，{@code -1 >> 1} 是<b>逻辑</b>右移给
 *       {@code maxinteger}。</li>
 * </ul>
 * <p>
 * 实测出来的强制转换规则（两边一致）：{@code "10" + 1} 给整数 11、
 * {@code "1e2" + 0} 给浮点 100.0、{@code "0x10" + 0} 给 16、
 * {@code "10" // 3} 与 {@code "10" % 3} 都按<b>整数</b>算，而
 * {@code "10" & 3} <b>不行</b>——位运算不接受数字字符串；
 * 比较运算更严格，{@code "10" < 11} 直接报
 * {@code attempt to compare string with number}。
 */
class ArithGridTest {

    @BeforeAll
    static void prepare() {
        new File("target/rt").mkdirs();
    }

    /** 运算符 × 操作数类型 的全网格。 */
    @Test
    void operatorByOperandTypeMatrixMatchesReference() {
        Diff.assertMatches("""
-- 运算符 × 操作数类型 矩阵：luaV_arith / luaV_idiv / luaV_mod / luaV_oband...
-- 与 lvm.c 的 luaT_trybinTM、以及「字符串能不能当数字用」那条强制转换规则。
-- 关键点：<b>结果的类型也要比</b>（math.type）—— 整数运算给整数、浮点运算给
-- 浮点、字符串参与算术一律先转浮点（"4" 是整数值但 "4"+1 给的是整数 5，
-- 而 "4.5"+1 给浮点）。
local V = {
  { "i", 3 }, { "f", 3.5 }, { "if", 2.0 }, { "s4", "4" }, { "s45", "4.5" },
  { "sx", "abc" }, { "b", true }, { "nil", nil }, { "tbl", setmetatable({}, {}) },
}
local function sh(v)
  local t = type(v)
  if t == "number" then
    return (math.type(v) == "integer" and "i" or "f") .. tostring(v)
  end
  if t == "string" then return "s[" .. v .. "]" end
  return t == "nil" and "nil" or t
end
local function kinds(v)
  local t = type(v)
  if t ~= "number" and t ~= "string" then return nil end
  return v
end

local BIN = { "+", "-", "*", "/", "//", "%", "^", "&", "|", "~", "<<", ">>",
              "..", "==", "~=", "<", "<=", ">", ">=" }
for oi = 1, #BIN do
  local op = BIN[oi]
  local f = load("local a, b = ... return a " .. op .. " b", "=o")
  for ai = 1, #V do
    for bi = 1, #V do
      local ok, r = pcall(f, V[ai][2], V[bi][2])
      if ok then
        print("B" .. oi .. "_" .. V[ai][1] .. "_" .. V[bi][1], "ok", sh(r))
      else
        print("B" .. oi .. "_" .. V[ai][1] .. "_" .. V[bi][1], "err",
              tostring(r):gsub("^target/rt/case.lua:%d+: ", ""))
      end
    end
  end
end

-- 一元：#、-（算术）、~（按位非）、not
local UN = {
  { "len", function(x) return #x end },
  { "neg", function(x) return -x end },
  { "bnot", function(x) return ~x end },
  { "not", function(x) return not x end },
  { "tostr", function(x) return tostring(x) end },
  { "tonum", function(x) return tonumber(x) end },
}
for ui = 1, #UN do
  for vi = 1, #V do
    local ok, r = pcall(UN[ui][2], V[vi][2])
    if ok then
      print("U" .. UN[ui][1] .. "_" .. V[vi][1], "ok", sh(r))
    else
      print("U" .. UN[ui][1] .. "_" .. V[vi][1], "err",
            tostring(r):gsub("^target/rt/case.lua:%d+: ", ""))
    end
  end
end

-- 关键的整数/浮点边界：除法、整除、取模、幂 的结果类型
local N = {
  { 7, 2 }, { 7.0, 2 }, { 7, 2.0 }, { -7, 2 }, { 7, -2 }, { -7, -2 },
  { 1, 0 }, { 1.0, 0 }, { 0, 0 }, { math.maxinteger, 1 },
  { math.mininteger, -1 }, { math.maxinteger, -1 }, { math.mininteger, 1 },
  { 2, math.maxinteger }, { 0x7fffffffffffffff, 1 }, { 0.5, 0.5 },
  { 1e308, 1 }, { 1, 1e308 }, { 3, 0.0 }, { -3, 0.0 },
  { 2 ^ 63, 1 }, { 1, 3 }, { 2, 3 }, { -1, 3 }, { 1, -3 },
}
local OPS2 = {
  { "div", "/" }, { "idiv", "//" }, { "mod", "%" }, { "pow", "^" },
  { "add", "+" }, { "sub", "-" }, { "mul", "*" },
}
for pi = 1, #N do
  for oi = 1, #OPS2 do
    local f = load("local a, b = ... return a " .. OPS2[oi][2] .. " b", "=p")
    local ok, r = pcall(f, N[pi][1], N[pi][2])
    print("P" .. pi .. "_" .. OPS2[oi][1], ok and "ok" or "err",
          ok and sh(r) or tostring(r):gsub("^target/rt/case.lua:%d+: ", ""))
  end
end

-- 位移的越界：位数 >= 64 给 0（不是取模），负数反向移
do
  local shifts = { 0, 1, 63, 64, 65, -1, -63, -64, -65, 2 ^ 31 }
  for i = 1, #shifts do
    local n = shifts[i]
    print("SH" .. i, tostring(n), sh(1 << n), sh(1 >> n), sh(-1 << n), sh(-1 >> n))
  end
end

-- 字符串强制转换：只有算术运算会转，比较不会
print("C1", pcall(function() return "10" + 1 end))
print("C2", pcall(function() return "10" < 11 end))
print("C3", pcall(function() return "10" == 10 end))
print("C4", pcall(function() return "10" .. 1 end))
print("C5", pcall(function() return "  10  " + 0 end))
print("C6", pcall(function() return "0x10" + 0 end))
print("C7", pcall(function() return "1e2" + 0 end))
print("C8", pcall(function() return "10" // 3 end))
print("C9", pcall(function() return "10" % 3 end))
print("C10", pcall(function() return "10" & 3 end))
print("C11", pcall(function() return "abc" + 1 end))
print("C12", pcall(function() return "10" + "20" end))
print("C13", pcall(function() return "10.0" + 0 end))
print("C14", pcall(function() return "9223372036854775808" + 0 end))

print("END")

                """);
    }

    private ArithGridTest() {
    }
}
