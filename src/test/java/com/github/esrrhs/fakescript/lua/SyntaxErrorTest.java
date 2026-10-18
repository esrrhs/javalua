package com.github.esrrhs.fakescript.lua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
/**
 * 语法 / 编译错误消息的差分矩阵 —— 阶段 12。
 * <p>
 * 为什么单开一类：JaCoCo 一度显示 {@code Parser} 有 28 行、{@code Lexer} 有 14 行
 * <b>一行没走到</b>，而它们清一色是 {@code error()} / {@code syntaxError()} 点——
 * 也就是说，此前从来没有一句坏代码被喂进来过。上游用例里也没有专门压解析错误的
 * （{@code errors.lua} 在 5.4 已改测运行时错误）。
 * <p>
 * 错误消息是最容易跟参考实现分家的地方，三处尤其：
 * <ul>
 *   <li><b>token 的显示名</b>：{@code luaX_token2str} 对
 *       {@code token >= TK_EOS} 返回<b>裸串</b>（第 72 条）。
 *       实测：这一处当前<b>观测不到</b>（破坏它 0 红）。</li>
 *   <li><b>"&lt;eof&gt;" 的写法</b>：块没闭合时记的是 <b>块结束</b> 那一行，
 *       不是坏 token 那一行；"unexpected symbol near" 才是坏 token 那一行。</li>
 *   <li><b>行号</b>：多行片段里取的是哪一行，逐条实测比对。</li>
 * </ul>
 * <p>
 * 矩阵两路：
 * <ul>
 *   <li><b>手写片段</b>（A–J）：空 / 只有注释 / 语句级缺 token / 表达式级 /
 *       畸形数字 / 未闭合字符串 / 长括号等级 / goto 与标签 /
 *       {@code <close>} 与 {@code <const>} / 表构造与调用。</li>
 *   <li><b>系统生成</b>（K–L）：把一批合法程序<b>逐个砍短</b>、<b>逐字符删除</b>、
 *       <b>逐字符替换</b>。不用人去想「哪里会错」，截到每一个位置自然就覆盖了
 *       每一种"话没说完"的形态——这才是铺矩阵该有的样子。</li>
 * </ul>
 * <p>
 * 另外单开一条 <b>源码前导</b>：UTF-8 BOM 与首行 {@code #!}。这条路径只有
 * <b>从文件加载</b> 才走（{@code lauxlib.c} 的 {@code skipBOM} +
 * {@code skipcomment}），{@code load(字符串)} 不走——所以之前所有用例都碰不到，
 * 而它恰恰是 {@code loadfile} / {@code dofile} / {@code require} 的真实入口。
 * 两类情形要分开比：文件里的 BOM 被吃掉；字符串里的 BOM 是非法字符。
 *
 * @see Lexer
 * @see Parser
 */
class SyntaxErrorTest {

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.find() != null,
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    /** 语法 / 编译错误消息矩阵（2586 行） */
    @Test
    void syntaxErrorGridMatchesReference() {
        Diff.assertMatches("""
-- 元方法矩阵之外的新一片：语法 / 编译错误消息矩阵
--
-- 为什么开这一片：JaCoCo 显示 Parser 有 28 行、Lexer 有 14 行完全没走到，
-- 一眼看去全是 error() / syntaxError() 点——也就是说<b>从来没有脚本喂过一句
-- 坏代码</b>。而错误消息是最容易跟参考实现分家的地方：token 的显示名
-- （第 72 条）、"<eof>" 的写法、行号取的是哪一行。
--
-- 两条硬约束：
--  1. load 遇到坏 chunk 是<b>返回</b> nil + 消息，不是抛错。但脚本自身的
--     语法错误是<b>编译期</b>的，整份脚本都加载不了——所以每条坏片段都
--     得用 load 现造，不能直接写在脚本里。
--  2. 探针里不能出现反斜杠（要嵌进 Java 文本块，反斜杠是转义符），
--     需要反斜杠的地方一律用 string.char(92) 拼。

local BS = string.char(92) -- 一个反斜杠
local NL = string.char(10) -- 换行
local Q = string.char(39)  -- 单引号
local DQ = string.char(34) -- 双引号

local function show(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

--[[
  每个片段都包在 pcall 里：万一某条把实现自身的异常激出来（不是 LuaError），
  脚本会当场死掉，后面几百条一条也不比而用例照样绿（第 38 条）。
]]
local function try(s, name)
    local ok, f, e = pcall(load, s, name)
    if not ok then
        return "THROW " .. show(f)
    end
    if f ~= nil then
        return "OK func"
    end
    return "ERR " .. show(e)
end

print("BEGIN")

--[[
  A. 空 / 只有注释 / 只有分隔符
]]
local A = {
    "", " ", NL .. NL, "--c", "--[[c]]", "--[==[c]==]", ";", ";;", " ",
    "--[[" .. NL .. "]]", "---", "--",
}
for i = 1, #A do print("A", i, try(A[i], "=X")) end

--[[
  B. 语句级缺 token。
     行号是关键：报错位置常在前一条语句的<b>结尾</b>而不是坏的地方
     ——"unexpected symbol near" 记的是坏 token 那一行，
     而 "'end' expected" 记的是<b>块结束</b>那一行。
]]
local B = {
    "=", "==", "a =", "a = =", "a = 1 +", "a = 1 + ", "a = * 1",
    "local", "local = 1", "local a =", "local a,", "local a,,b",
    "local a = 1,", "local 1 = 1", "local end = 1", "local a.b = 1",
    "if", "if x", "if x then", "if x then y=1", "if x then else",
    "if x then elseif", "if x then elseif y then", "if x then else y=1",
    "while", "while x", "while x do", "while x do y=1",
    "repeat y=1", "repeat until", "repeat until x", "repeat",
    "for", "for i", "for i =", "for i = 1", "for i = 1,", "for i = 1,2",
    "for i = 1,2 do", "for i = 1,2 do y=1", "for i = 1,2,3 do",
    "for i = 1,2,3 do y=1", "for k in", "for k in t", "for k in t do",
    "do", "do y=1", "end", "else", "elseif", "until", "then",
    "function", "function f", "function f(", "function f()",
    "function f() y=1", "function f.a", "function f.a.", "function f:a.",
    "function f:a", "local function", "local function f", "local function f()",
    "return", "break",
}
for i = 1, #B do print("B", i, try(B[i], "=X")) end

--[[
  C. 表达式级。
]]
local C = {
    "a = (1", "a = 1)", "a = f(,)", "a = f(a,)", "a = f()",
    "a = {}", "a = {,}", "a = {a=}", "a = {[1]}", "a = {[1]=}",
    "a = {1 2}", "a = {1,2,}", "a = 1 +", "a = -", "a = #",
    "a = not", "a = 1 ..", "a = ..1", "a = 1 and", "a = 1 or",
    "a[]", "a[1", "a = a[]", "a = a.b.", "a = a:b.",
    "a = 1 = 2", "a = 1 == ", "a = ((1)",
    "a = 1 2", "a = f(a b)", "a = f(a)(b)",
    "a = 2^-3", "a = 2^-", "a = #a", "a = -#a",
}
for i = 1, #C do print("C", i, try(C[i], "=X")) end

--[[
  D. 数字字面量。
     Lua 5.4 对畸形数字是<b>词法</b>错误（luai_numisnan 那类检查在 lexer 里），
     消息形如 "malformed number near '1e'"。
]]
local D = {
    "a = 1e", "a = 1e+", "a = 1e-", "a = 0x", "a = 0x1p", "a = 0x1p+",
    "a = 0x.1", "a = .", "a = ..", "a = 1..2", "a = 1e999",
    "a = 0x7fffffffffffffff", "a = 0x8000000000000000",
    "a = 0xffffffffffffffff", "a = 1e15", "a = 1e16", "a = -1e15",
    "a = 9007199254740993", "a = 0x1p-1074", "a = 0x1p-1075",
}
for i = 1, #D do print("D", i, try(D[i], "=X")) end

--[[
  E. 字符串字面量：未闭合、长括号等级不匹配。
     转义序列相关的用 BS 拼出来（探针里不能有裸反斜杠）。
]]
local E = {
    "a = 'abc", 'a = "abc', "a = [[abc", "a = [=[abc]=", "a = [==[abc]=",
    "a = [==[abc]==]", "a = [==[abc]===]", "a = --[[x", "a = [[]]",
    "a = ''", 'a = ""',
    -- 转义序列：十六进制转义不带数字、unicode 转义越界、十进制转义超过 255
    "a = '" .. BS .. "x'",
    "a = '" .. BS .. "u{110000}'",
    "a = '" .. BS .. "300'",
    "a = '" .. BS .. "z   " .. NL .. "  x'",
}
for i = 1, #E do print("E", i, try(E[i], "=X")) end

--[[
  F. goto / 标签：缺标签、重复标签、跳进局部变量作用域。
     这些是<b>语义</b>错误（lparser.c 的 undefgoto / jumpScopeError），
     跟纯语法错误走不同的回路。
]]
local F = {
    "goto x",
    "do ::x:: goto x end",
    "::x:: ::x::",
    "do goto x end ::x::",
    "goto x do ::x:: end",
    "for i=1,2 do goto cont ::cont:: end",
    "while true do goto break end ::break::",
    "::a:: goto b ::b:: goto a",
}
for i = 1, #F do print("F", i, try(F[i], "=X")) end

--[[
  G. to-be-closed / const 的声明期检查。
     同样得用 load 现造：重复给 <const> 赋值是<b>编译期</b>错误。
]]
local G = {
    "local x <close> = 5",
    "local x <close> = nil",
    "local x <close>, y <close> = nil, nil",
    "local x <const> = 1 x = 2",
    "local x <const> = 1",
    "local a <close> = setmetatable({}, {__close=print})",
    "do local x <close> = nil end",
}
for i = 1, #G do print("G", i, try(G[i], "=X")) end

--[[
  H. 层级上限：表达式嵌太深、常量太多、upvalue 太多、局部量太多。
     lparser.c 的 errorlimit 给的是 "too many %s (limit is %d) in %s"。
]]
local H = {}
do
    H[#H + 1] = "a = " .. string.rep("(", 210) .. "1" .. string.rep(")", 210)
    H[#H + 1] = "a = " .. string.rep("-", 210) .. "1"
    H[#H + 1] = "local function f() return " .. string.rep("(", 210) .. "1"
            .. string.rep(")", 210) .. " end"
    -- 常量个数
    local t = {}
    for i = 1, 300 do t[#t + 1] = "a = " .. i .. ".0" end
    H[#H + 1] = table.concat(t, NL)
    -- 局部变量个数（一层里 210 个）
    local names = {}
    for i = 1, 210 do names[#names + 1] = "v" .. i end
    H[#H + 1] = "local " .. table.concat(names, ",")
end
for i = 1, #H do print("H", i, try(H[i], "=X")) end

--[[
  I. 方法调用 / 自调用 / 可变参数的位置错误。
]]
local I = {
    "a:b()", "a:b", "('x'):upper()", "a = ('x'):upper()",
    "local function f(...) return ... end",
    "function f(...) return ... end f(1,2,3)",
    "...", "return ...",
    "a = 1 return",
}
for i = 1, #I do print("I", i, try(I[i], "=X")) end

--[[
  J. 数字与名字的粘连、运算符连写。
]]
local J = {
    "a = 1a", "a = 0x1g", "a = 1..", "a = ..", "a = 1 ... 2",
    "a = 1 +* 2", "a = 1 ** 2", "a = ~1", "a = 1 ~ 2",
    "a = #", "a = ##a", "a = 1 //0",
}
for i = 1, #J do print("J", i, try(J[i], "=X")) end

--[[
  K. 前缀截断：把一批合法程序逐个砍短，每个长度都喂一次 load。
     这是<b>系统性</b>造语法错误的办法——不用人去想「哪里会错」，
     截断到每一个位置自然覆盖了每一种"话没说完"的形态。
]]
local PROGS = {
    "local x = 1",
    "local function f(a, b) return a + b end",
    "if x then y = 1 else y = 2 end",
    "for i = 1, 10 do print(i) end",
    "for k, v in pairs(t) do print(k, v) end",
    "while true do break end",
    "repeat x = x + 1 until x > 10",
    "t = {1, 2, 3, a = 'b', [4] = 5}",
    "return function(...) return ... end",
    "local a <const> = 1",
    "do local b <close> = nil end",
    "goto done ::done::",
    "a.b.c:d(1)(2){3}",
    "x = -2^2 // 3 % 4",
    "x = #t .. 'y'",
    "local s = [[long]]",
    "x = 0x1p4 + 1e2 + .5",
    "function t.a.b:c(d) end",
    "local t = {[1]=1, 2, 3}",
    "return 1, 2, 3",
}
for pi = 1, #PROGS do
    local p = PROGS[pi]
    for k = 0, #p do print("K", pi, k, try(string.sub(p, 1, k), "=X")) end
end

--[[
  L. 逐字符删除与逐字符替换。
     替换集取三个括号类字符——它们最容易把"结构"改坏，
     于是消息里出现的 token 名也最杂。
]]
local REP = { '"', "(", "}" }
for pi = 1, #PROGS do
    local p = PROGS[pi]
    for k = 1, #p do
        print("LD", pi, k, try(string.sub(p, 1, k - 1) .. string.sub(p, k + 1), "=X"))
    end
    for k = 1, #p do
        for r = 1, #REP do
            print("LR", pi, k, r, try(string.sub(p, 1, k - 1) .. REP[r] .. string.sub(p, k + 1), "=X"))
        end
    end
end

print("END")
                """);
    }

    /** 源码前导：BOM 与 "#!" 行（只有 loadfile 走） */
    @Test
    void sourcePreambleMatchesReference() {
        Diff.assertMatches("""
-- 源码前导：BOM 与 '#!' 行的差分
--
-- JaCoCo 显示 Parser.skipFilePreamble 有三段从来没走到——UTF-8 BOM 与
-- 首行 '#' 注释。这条路径只有 <b>从文件加载</b>才走（lauxlib.c 的
-- skipBOM + skipcomment），load(字符串) 不走，所以之前所有用例都碰不到。
--
-- 而它恰恰是 loadfile / dofile / require 的真实入口。

local BOM = string.char(0xEF, 0xBB, 0xBF)
local NL = string.char(10)

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

-- 临时文件写 target/rt/：两边同一个 cwd，写仓库根会互相踩（第 41 条）。
-- 开头先删一次，免得上一次跑留下的东西混进来。
local DIR = "target/rt/pre"
os.execute("rm -rf " .. DIR)
os.execute("mkdir -p " .. DIR)

local function put(name, data)
    local f = io.open(DIR .. "/" .. name, "wb")
    f:write(data)
    f:close()
end

local function loadrun(name)
    local f, e = loadfile(DIR .. "/" .. name)
    if f == nil then return "ERR " .. show(e) end
    local r = table.pack(pcall(f))
    local out = { "OK" }
    for i = 1, r.n do out[#out + 1] = show(r[i]) end
    return table.concat(out, " ")
end

print("BEGIN")

-- 1) 纯 BOM + 代码：参考实现应当吃掉 BOM 正常跑
put("bom1.lc", BOM .. "return 1+1")
print("BOM1", loadrun("bom1.lc"))

-- 2) 只有 BOM，没有代码
put("bom2.lc", BOM)
print("BOM2", loadrun("bom2.lc"))

-- 3) '#' 开头的一行 + 代码
put("hash1.lc", "#!/usr/bin/lua" .. NL .. "return 2+2")
print("HASH1", loadrun("hash1.lc"))

-- 4) '#' 开头但没换行（整份源码都是注释行）
put("hash2.lc", "#!/usr/bin/lua")
print("HASH2", loadrun("hash2.lc"))

-- 5) BOM + '#' + 代码：两个都要吃掉
put("both.lc", BOM .. "#!/usr/bin/lua" .. NL .. "return 3+3")
print("BOTH", loadrun("both.lc"))

-- 6) '#' 在第一行中间（不是开头）：不该被跳过
put("mid.lc", " # x" .. NL .. "return 4+4")
print("MID", loadrun("mid.lc"))

-- 7) BOM 出现两次：只吃第一个
put("bom3.lc", BOM .. BOM .. "return 5+5")
print("BOM3", loadrun("bom3.lc"))

-- 8) 只有前两个字节（不完整 BOM）：不该吃
put("bom4.lc", string.char(0xEF, 0xBB) .. "return 6+6")
print("BOM4", loadrun("bom4.lc"))

-- 9) 长度刚好 2 的源码
put("short.lc", "==")
print("SHORT", loadrun("short.lc"))

-- 10) 空文件
put("empty.lc", "")
print("EMPTY", loadrun("empty.lc"))

-- 11) '#' 后面紧跟 EOF（无换行）
put("hash3.lc", "#")
print("HASH3", loadrun("hash3.lc"))

-- 12) loadfile 一个目录
print("DIR", loadrun("."))

-- 13) loadfile 不存在的文件
print("MISSING", loadrun("target/rt/pre/does-not-exist-xyz.lc"))

--[[
  14) load(字符串) 里的 BOM 不该被吃掉——只有文件加载才跳前导。
      这是 lauxlib.c 的 skipcomment 只在 loadfile 路径上。
]]
go("STRBOM", function() return load(BOM .. "return 7") end)
go("STRHASH", function() return load("#!/x") end)
go("STRHASHNL", function() return load("#!/x" .. NL .. "return 8") end)

-- 15) 只有 BOM 的字符串
go("STRBOMONLY", function() return load(BOM) end)

-- 16) BOM 在代码中间
go("STRBOMMID", function() return load("return " .. BOM .. " 9") end)

print("END")
                """);
    }

    private SyntaxErrorTest() {
    }
}
