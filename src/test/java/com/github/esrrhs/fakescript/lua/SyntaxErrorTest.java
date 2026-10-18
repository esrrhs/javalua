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

    /**
     * 顺着 {@code lparser.c} / {@code llex.c} 的<b>每一个报错点</b>点名打。
     * <p>
     * 与上面那条网格的分工：那条是「把合法程序砍短 / 删字符 / 换字符」——
     * 覆盖面靠数量；这条是<b>照着 C 源码里的 {@code throw} 一行一行点</b>，
     * 保证每个 {@code syntaxerror} / {@code semerror} 的调用点都有人走过。
     * JaCoCo 显示 {@code Parser} 有 24 行没走到，清一色是这些点。
     * <p>
     * 点到的点：{@code "%s expected"}、{@code "%s expected (to close %s at
     * line %d)"}、{@code errorlimit} 的三种 {@code what}（局部变量 200 /
     * upvalue 255 / 构造式项数）、{@code attempt to assign to const variable}、
     * {@code unknown attribute}、{@code multiple to-be-closed variables}、
     * {@code cannot use '...' outside a vararg function}、{@code <name> or
     * '...' expected}、{@code function arguments expected}、
     * {@code '=' or 'in' expected}、{@code <goto> ... jumps into the scope of
     * local}、{@code no visible label}、{@code label already defined}、
     * lexer 的 {@code unfinished string} / {@code malformed number} /
     * {@code hexadecimal digit expected}，以及 {@code C stack overflow}。
     * <p>
     * 两处写法上的坑：
     * <ul>
     *   <li><b>chunk 名统一用 {@code "=c"}</b>：{@code load(字符串)} 默认把
     *       源码塞进 {@code [string "..."]}，几十条用例的消息就成了几十倍噪声；</li>
     *   <li><b>必须包 {@code pcall}</b>：C 栈溢出是<b>抛出</b>不是返回（约定
     *       #27）。不包的话那一条带着回溯把脚本打断，而回溯里的文件名两边
     *       不一样（约定 #37）。</li>
     * </ul>
     * {@code control structure too long} 与「构造式项数」两个上限要到
     * {@code MAX_INT} 才触发，打不出来，如实记为不可达（约定 #30）。
     */
    @Test
    void parserErrorSitesMatchReference() {
        Diff.assertMatches("""
-- 语法错误全矩阵：顺着 lparser.c / llex.c / lcode.c 的每一个报错点写
-- 起因：JaCoCo 里 Parser 有 24 行没走到，几乎全是 throw syntaxError ——
-- 也就是「错误消息与原话」这一片从来没被系统扫过。SyntaxErrorTest 只有 2 条。
--
-- chunk 名统一用 "=c"（load 的第二个参数）：默认的 "[string \\"...\\"]" 会把
-- 源码塞进消息里，几十条用例的消息就成了几十倍的噪声。
-- 必须包 pcall：C 栈溢出是<b>抛出</b>而不是返回（约定 #27）。不包的话那
-- 一条会带着回溯把脚本打断，而回溯里的文件名两边不一样（约定 #37）。
local function p(tag, s)
  local ok, a, b = pcall(load, s, "=c")
  if not ok then
    print(tag, "THROW", tostring(a))
  elseif a then
    print(tag, "OK")
  else
    print(tag, tostring(b))
  end
end

-- ① "%s expected"：同一个 token 在不同位置上的报错（error_expected）
print("== 缺 token ==")
local miss = {
  "if x then", "if x then y=1", "if x then y=1 else", "if x then y=1 elseif",
  "while x do", "while x do y=1",
  "for i=1,2 do", "for i=1,2", "for i", "for i in", "for i in x do", "for",
  "repeat", "repeat x=1",
  "do", "do x=1",
  "function", "function f", "function f(", "function f(a", "function f(a,",
  "function f(a,b", "function f()", "function f() x=1",
  "local", "local a", "local a =", "local a,", "local function",
  "local function f(", "return 1 2",
  "x =", "x = 1 +", "x = (", "x = (1", "x = {", "x = {1", "x = {1,", "x = t[",
  "x = t[1", "x = f(", "x = f(1", "x = f(1,", "x = a.", "x = a:", "x = a:b",
  "x = -", "x = #", "x = not", "x = 1 and", "x = 1 or", "x = 1 ..",
  "goto", "::", "::a", "::a::", "break",
  ";", "x", "x = 1;", "", " ", "\\n",
}
for i, s in ipairs(miss) do p("M" .. i, s) end

-- ② "%s expected (to close %s at line %d)"：跨行的 check_match
print("== 跨行闭合 ==")
local spread = {
  "if x then\\n\\nend",
  "if x then\\n\\n\\nelse\\nend",
  "while x do\\n\\n\\nend",
  "for i=1,2 do\\n\\n\\nend",
  "do\\n\\n\\nend",
  "function f()\\n\\n\\nend",
  "repeat\\n\\n\\nuntil x",
  "x = function()\\n\\n\\nend",
  "x = (\\n\\n\\n1)",
  "x = {\\n\\n\\n1}",
  "x = t[\\n\\n\\n1]",
  "x = f(\\n\\n\\n1)",
  "if x then\\ny=1\\nelseif z then\\n\\nelse\\nend",
  "if x\\nthen\\nend",
  "while x\\ndo\\nend",
}
for i, s in ipairs(spread) do p("S" .. i, s) end

-- ③ errorlimit："too many %s (limit is %d) in %s"
print("== 上限 ==")
do
  -- 局部变量 200：主 chunk 与函数里的 where 不一样
  local a = {}
  for i = 1, 201 do a[i] = "v" .. i end
  p("L1", "local " .. table.concat(a, ","))
  p("L2", "local " .. table.concat(a, ",", 1, 200))
  p("L3", "function f()\\nlocal " .. table.concat(a, ",") .. "\\nend")
  p("L4", "do\\nlocal " .. table.concat(a, ",") .. "\\nend")
end
do
  -- upvalue 255
  local a = {}
  for i = 1, 256 do a[i] = "u" .. i end
  local outer = "local " .. table.concat(a, ",", 1, 256) .. "\\n"
  local use = {}
  for i = 1, 256 do use[i] = "u" .. i end
  p("U1", outer .. "function f() return " .. table.concat(use, "+") .. " end")
end
do
  -- 函数实参上限？以及 return 的个数
  local a = {}
  for i = 1, 300 do a[i] = "1" end
  p("A1", "f(" .. table.concat(a, ",") .. ")")
  p("A2", "return " .. table.concat(a, ","))
end

-- ④ const / close 属性
print("== 属性 ==")
p("C1", "local x <const> = 1\\nx = 2")
p("C2", "local x <const> = 1\\nx = 2\\nlocal y = x")
p("C3", "local x <foo> = 1")
p("C4", "local x <const> = 1")
p("C5", "local x <close> = nil")
p("C6", "local x <close>, y <close> = nil, nil")
p("C7", "local x <close> = nil, y <close> = nil")
p("C8", "function f() local x <const> = 1 x = 2 end")
p("C9", "local x <const> = 1 local x = 2")
p("C10", "for i <const> = 1, 2 do end")
p("C11", "for i <close> = 1, 2 do end")
p("C12", "local x <const>")
p("C13", "local x <>")
p("C14", "local x <const")

-- ⑤ vararg
print("== vararg ==")
p("V1", "function f() return ... end")
p("V2", "function f() local x = ... end")
p("V3", "local function f() return ... end")
p("V4", "return ...")
p("V5", "local x = ...")
p("V6", "function f(...) return ... end")
p("V7", "do return ... end")
p("V8", "function f() x = ... end")
p("V9", "x = function() return ... end")
p("V10", "function f(a, ...) return ..., a end")

-- ⑥ goto / label
print("== goto ==")
p("G1", "goto x")
p("G2", "goto x\\n::x::")
p("G3", "::x:: goto x")
p("G4", "do local y = 1 goto x ::x:: end")
p("G5", "for i=1,2 do goto x end ::x::")
p("G6", "goto x\\n::y::")
p("G7", "::x::\\n::x::")
p("G8", "goto continue\\n::continue::")
p("G9", "while true do goto x ::y:: end")
p("G10", "goto")

-- ⑦ lexer 的报错
print("== lexer ==")
-- 全部写成常规引号：嵌套的 [[ ]] 会让<b>本探针自己</b>先编不过
local lex = {
  "'abc", '"abc', 'x = "abc', 'x = [[abc',
  'x = [==[abc]', 'x = [==[abc]]', 'x = [[abc]]=',
  'x = [ [ 1 ] ]',
  "0x", "0xg", "x = 0x", "x = 0x.", "x = 0x.1p", "x = 0x1p",
  "1e", "x = 1e", "x = 1e+", "x = .", "x = 1..2", "x = 0xa",
  'x = "\\\\xZZ"', 'x = "\\\\x1"', 'x = "\\\\xff"', 'x = "\\\\z"',
  'x = "\\\\u{}"', 'x = "\\\\u{110000}"', 'x = "\\\\u{41}"', 'x = "\\\\u{1100000}"',
  "$", "x = $", "x = 1 $ 2", "x = @", "x = `", "x = ?",
  "--[==[", "--", "--[", "x = 1 --[[\\n",
  "x = 'a\\\\", 'x = "a\\nb"', 'x = "a\\\\\\nb"',
  "x = 0777", "x = 1_000", "x = 1e999",
  "\\\\", "x = \\\\", "1abc", "x = 0b101", "x = 1e309",
}
for i, s in ipairs(lex) do p("X" .. i, s) end

-- ⑧ 方法调用后跟的不是参数列表："function arguments expected"
print("== 调用形状 ==")
p("F1", "x = a:b 5")
p("F2", "x = a:b\\n5")
p("F3", "x = a:b =")
p("F4", "x = a:b;")
p("F5", "x = a:b end")
p("F6", "a:b()")
p("F7", "a:b{}")
p("F8", [[a:b"c"]])
p("F9", "a:b[[c]]")
p("F10", "a:b(1)(2)")

-- ⑨ "'=' or 'in' expected" 与 "<name> or '...' expected"
print("== for / 形参 ==")
p("N1", "for i do end")
p("N2", "for i, do end")
p("N3", "for 1=1,2 do end")
p("N4", "for i.j=1,2 do end")
p("N5", "function f(1) end")
p("N6", "function f(a, 1) end")
p("N7", "function f(a.b) end")
p("N8", "function f(..., a) end")
p("N9", "function f(a, ..., b) end")
p("N10", "for i=1,2,3,4 do end")

-- ⑩ 语句层面接不上的东西
print("== 语句 ==")
local stmt = {
  "y=1 z", "y=1 (", "y=1 )", "y=1 }", "y=1 ]", "y=1 ,",
  "y=1 .", "y=1 :", "y=1 =", "y=1 <", "y=1 >", "y=1 *",
  "y = 1 then", "y = 1 do", "y = 1 end", "y = 1 else",
  "y = 1 elseif", "y = 1 until", "y = 1 in", "y = 1 local",
  "return", "return return", "return 1 return 2",
  "::a:: ::a::", "break break", ";", ";;", "; ;",
}
for i, s in ipairs(stmt) do p("T" .. i, s) end

-- ⑪ "<goto x> at line N jumps into the scope of local 'y'"（jumpscopeerror）
print("== goto 跨局部变量作用域 ==")
p("J1", "do goto x local y = 1 ::x:: end")
p("J2", "do goto x local y ::x:: end")
p("J3", "while true do goto x local y = 1 ::x:: end")
p("J4", "for i=1,2 do goto x local y = 1 ::x:: end")
p("J5", "do local y = 1 goto x end ::x::")
p("J6", "function f() goto x local y = 1 ::x:: end")
p("J7", "do goto x local y <const> = 1 ::x:: end")
p("J8", "do goto x local y <close> = nil ::x:: end")
p("J9", "do do goto x local y = 1 ::x:: end end")
p("J10", "repeat goto x local y = 1 ::x:: until true")

-- ⑫ upvalue 上限 255：一层套一层，内层的 upvalue 能超过 255
print("== upvalue 上限 ==")
do
  local a, b, use = {}, {}, {}
  for i = 1, 200 do
    a[i] = "a" .. i
    b[i] = "b" .. i
    use[#use + 1] = "a" .. i
    use[#use + 1] = "b" .. i
  end
  local src = "local " .. table.concat(a, ",") .. "\\n"
      .. "local function f()\\n"
      .. "local " .. table.concat(b, ",") .. "\\n"
      .. "local function g() return " .. table.concat(use, "+") .. " end\\n"
      .. "return g\\nend"
  p("P1", src)
end

-- ⑬ C stack overflow：语法分析器的层数上限（约定 #26，参考恒 197）
print("== 层数 ==")
for _, n in ipairs({ 100, 190, 200, 210, 300 }) do
  p("D" .. n, string.rep("do ", n) .. string.rep(" end", n))
  p("B" .. n, string.rep("(", n) .. "1" .. string.rep(")", n))
  p("T" .. n, string.rep("{", n) .. string.rep("}", n))
end

print("DONE")
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

    /**
     * 语法错<b>补漏</b>（41 条）：既有
     * {@link #parserErrorSitesMatchReference} 没点到的那几条。
     * <p>
     * 那边已经铺了 {@code \xZZ} / {@code \z} / {@code \\u{}} / {@code 0x1p} /
     * {@code [==[abc} / {@code <close>} 重复 / const 赋值 / jumps into scope，
     * 这边只补剩下的，逐条对过不重复。五组：
     * <ul>
     *   <li><b>属性</b>：{@code unknown attribute 'foo'}（大小写敏感，
     *       {@code <CONST>} 一样不认）、{@code <close> <const>} 叠着写、
     *       {@code for x <close> in ...}。</li>
     *   <li><b>泛化的 {@code syntax error}</b>：{@code (x) = 1} 与
     *       {@code f() = 1}（赋值目标不是变量）给的是不带 token 名的那句；
     *       {@code a:b:1()} 才撞得到 {@code funcargs} 的
     *       {@code default: "function arguments expected"} —— 从
     *       {@code primaryexp} 那条路看过去它像是死代码，只有
     *       <b>冒号之后再来一个冒号</b>才进得去。</li>
     *   <li><b>长字符串 / 长注释</b>：三种「没写完」与
     *       {@code invalid long string delimiter}。</li>
     *   <li><b>字面量边角</b>：{@code malformed number near '1..2'}、
     *       {@code invalid escape sequence}、{@code unfinished string}。</li>
     *   <li><b>赋值目标不是变量</b>：{@code 1 = 2}、{@code local 1 = 2}、
     *       {@code ::1::}。</li>
     * </ul>
     */
    @Test
    void syntaxErrorSitesSecondPassMatchesReference() {
        Diff.assertMatches("""
-- 语法错「补漏」：既有 parserErrorSitesMatchReference 没点到的那几条。
-- 逐条对过：那边有 \\xZZ / \\z / \\u{} / 0x1p / [==[abc / <close> 重复 /
-- const 赋值 / jumps into scope，这边只补剩下的。
local function p(tag, s)
  local ok, a, b = pcall(load, s, "=c")
  if not ok then
    print(tag, "THROW", tostring(a))
  elseif a then
    print(tag, "OK")
  else
    print(tag, tostring(b))
  end
end

-- ① unknown attribute（属性名不是 const / close）
p("A1", "local x <foo> = 1")
p("A2", "local x <CONST> = 1")
p("A3", "local x <> = 1")
p("A4", "local x <close> <const> = nil")
p("A5", "local x <const> <close> = 1")
p("A6", "for x <close> in pairs({}) do end")
p("A7", "for x <const> = 1, 2 do end")
p("A8", "local x <close> = 1, 2")

-- ② 泛化的 "syntax error"（lparser.c 里那句不带 token 名的话）
p("S1", "f::")
p("S2", "a.b::")
p("S3", "f:")
p("S4", "f:1()")
p("S5", "a:b:1()")
p("S6", "::")

-- ③ 长字符串 / 长注释：三种「没写完」与「分隔符不合法」
p("L1", "x = [[abc")
p("L2", "x = [==[abc]=]")
p("L3", "x = [=abc=]")
p("L4", "x = [abc]")
p("L5", "x = --[[abc")
p("L6", "x = [[]]")
p("L7", "x = [[]=]")
p("L8", "x = --[==[")

-- ④ 字面量的边角
p("N1", "x = .")
p("N2", "x = 1..2")
p("N3", "x = 1e+")
p("N4", "x = 1E-")
p("N5", "x = 0x1p+")
p("N6", 'x = "abc\\\\qdef"')
p("N7", 'x = "\\\\8"')
p("N8", 'x = "\\\\"')
p("N9", "x = 'abc\\\\'")

-- ⑤ 赋值目标不是变量 / 名字位置上是别的
p("V1", "1 = 2")
p("V2", "local 1 = 2")
p("V3", "1, 2 = 3")
p("V4", "(x) = 1")
p("V5", "x.y = 1")
p("V6", "x[1] = 1")
p("V7", "f() = 1")
p("V8", "local x.y = 1")
p("V9", "::1::")

print("END")

                """);
    }
}
