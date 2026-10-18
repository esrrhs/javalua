package com.github.esrrhs.fakescript.lua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
/**
 * 元方法的差分矩阵 —— 阶段 12。
 * <p>
 * 为什么单开一类：前面几轮把三个层次都扫过了——「函数在不在这个库里」（符号
 * 对撞）、「传错参数报什么错」（错误参数网格）、「传对参数返回什么」（返回值
 * 网格）——但三层都绕开了元方法。而元方法是 Lua 里<b>唯一</b>能把运算符语义整
 * 个换掉的地方：{@code lvm.c} 的 {@code OP_ADD..OP_LE} 有一半分支挂在
 * {@code luaT_trybinTM} 上，另一半挂在 {@code luaV_finishget} /
 * {@code luaV_finishset} / {@code luaV_objlen} / {@code OP_CONCAT} 上。
 * <p>
 * 四条线各自成片：
 * <ul>
 *   <li><b>片一：二元运算符 × 操作数类型 × 元表所在侧</b>。
 *       八种类型两两配对、元表摆在左 / 右 / 两侧 / 不摆，再乘上元方法本身的
 *       六种形态。两侧都摆那一档是重点——算术取左边的、相等两边都试、小于与
 *       小于等于先试自己的再退回对方的，<b>三条规则各不相同</b>。</li>
 *   <li><b>片二：__index / __newindex / __call / __len / 一元 / 拼接链</b>。
 *       这些不是运算符，走的是另一条路。{@code __index} 是表时会<b>循环</b>再取
 *       （不是递归），成环时报 {@code '__index' chain too long}；
 *       {@code OP_CONCAT} 是从右往左两两折的，三个值里只有中间那个有
 *       {@code __concat} 时，调用顺序与「从左到右」的直觉相反。</li>
 *   <li><b>片三：元方法里 yield</b>。第 7 条：这类 bug 只在协程里现形。
 *       外套分三种（裸写 / {@code pcall} / C 函数内部），三种给出三种答案——
 *       少一种都看不出续体写没写对。</li>
 *   <li><b>片四：__pairs / __close / raw* / 元表保护</b>。协议类元方法与后门
 *       函数的参数校验。</li>
 * </ul>
 * <p>
 * 一个前提（实测，别凭记忆推）：脚本里<b>只有表和完整 userdata 能挂实例元表</b>。
 * {@code setmetatable} 的第 1 个参数被 {@code luaL_checktype} 限成表；给别的
 * 值挂只能走 {@code debug.setmetatable}，改的是<b>全局类型元表</b>。所以片一里对
 * 非表类型一律用 {@code debug.setmetatable}，而且<b>用完立刻还原原值</b>
 * （字符串那份是 string 库自己装上去的，还成 nil 会把后面全炸掉）。
 *
 * @see LuaTm
 * @see Vm#finishGet
 * @see Vm#finishSet
 * @see Vm#objLen
 */
class MetamethodTest {

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.find() != null,
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    /** 第一片：二元运算符 × 操作数类型 × 元表所在侧 */
    @Test
    void binaryOperatorGridMatchesReference() {
        Diff.assertMatches("""
-- 元方法矩阵 · 第一片：二元运算符 × 操作数类型 × 元表所在侧
--
-- 为什么单独开一片：前面几轮扫的是「函数在不在这个库里」「传错参数报什么错」
-- 「传对参数返回什么」，三层都绕开了元方法——而元方法是 Lua 里唯一能把运算符
-- 语义整个换掉的地方，lvm.c 的 OP_ADD..OP_LE 有一半分支挂在它上面。
-- 更要紧的是：元方法<b>缺失</b>时抛的那条消息
-- （attempt to perform arithmetic on a X value ...）由 varinfo 反推名字拼出来，
-- 跟前面那片 varinfo 网格是同一条链路，换个入口再压一遍。
--
-- 一个前提（实测，别凭记忆推）：脚本里<b>只有表和完整 userdata 能挂实例元表</b>。
-- setmetatable 的第 1 个参数被 luaL_checktype 限成表；给别的值挂，走的是
-- debug.setmetatable，改的是<b>全局类型元表</b>（mt[LUA_TNIL] 那一档）。
-- 所以下面对非表类型一律用 debug.setmetatable，并且<b>用完立刻还原</b>——
-- 不然会把后面的用例全污染掉（字符串那份尤其：string 库自己就占着它）。

local TN = { "nil", "false", "true", "int", "flt", "str", "tbl", "fn" }

local function mkv(t)
    if t == "nil" then return nil end
    if t == "false" then return false end
    if t == "true" then return true end
    if t == "int" then return 7 end
    if t == "flt" then return 2.5 end
    if t == "str" then return "ab" end
    if t == "tbl" then return {} end
    return print
end

--[[
  运算符 → 要试着挂的元方法名（是个<b>列表</b>）。
  两条不显然的：
  <ul>
    <li>{@code ~=} 归 {@code __eq}、{@code >} 归 {@code __lt}、{@code >=} 归
        {@code __le} —— 它们没有自己的元方法，列进来只为压「取反 / 换位」。</li>
    <li>{@code <=} 与 {@code >=} 挂<b>两个</b>：{@code __le} 缺失时
        {@code luaT_callorderTM} 会退回 {@code !(p2 < p1)}（LUA_COMPAT_LT_LE）。
        只挂 {@code __le} 的话这条兜底一行也走不到 —— 第一版就是这样漏的。</li>
  </ul>
]]
local BIN = {
    { "+", { "__add" } }, { "-", { "__sub" } }, { "*", { "__mul" } },
    { "/", { "__div" } }, { "//", { "__idiv" } }, { "%", { "__mod" } },
    { "^", { "__pow" } },
    { "&", { "__band" } }, { "|", { "__bor" } }, { "~", { "__bxor" } },
    { "<<", { "__shl" } }, { ">>", { "__shr" } },
    { "..", { "__concat" } },
    { "==", { "__eq" } }, { "~=", { "__eq" } },
    { "<", { "__lt" } }, { "<=", { "__le", "__lt" } },
    { ">", { "__lt" } }, { ">=", { "__le", "__lt" } },
}

--[[
  元表摆在哪一侧。
  「两侧都有」这一档最容易写错：算术取<b>左</b>边的；相等两边都试；
  小于 / 小于等于先试自己的、再退回对方的——三条规则各不相同，
  光跑「左边有」根本看不出来。
]]
local MT = {
    { "none", false, false },
    { "L", true, false },
    { "R", false, true },
    { "B", true, true },
}

-- 元方法本身的形态：正常返回 / 不返回 / 返回多个 / 不是函数
local KIND = { "fn", "void", "multi", "num", "tbl", "str" }

-- 结果归一：整数和浮点必须分开看，地址一律去掉（第 42 条）。
-- 放在 mkmeth 前面，因为下面要用它当上值。
local function show(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

local function mkmeth(kind, tag)
    if kind == "fn" then
        return function(x, y) return tag .. ":" .. show(x) .. "," .. show(y) end
    elseif kind == "void" then
        return function(x, y) end
    elseif kind == "multi" then
        return function(x, y) return tag, "extra" end
    elseif kind == "num" then
        return 123
    elseif kind == "tbl" then
        return { 1 }
    else
        return "notafunction"
    end
end

--[[
  每次求值都在 pcall 里。
  未捕获的抛错会让脚本当场死掉——后面几百行一条也不会比，而用例照样绿。
  这是第 38 条，每一片网格都要照做。
]]
local function eval(f, a, b)
    local r = table.pack(pcall(f, a, b))
    local out = { tostring(r[1]) }
    for i = 2, r.n do out[#out + 1] = show(r[i]) end
    return table.concat(out, " ")
end

--[[
  给任意类型的值挂元表，返回「撤销函数」。
  表走 setmetatable；其余类型走 debug.setmetatable（改的是全局类型元表）。
  表<b>不能</b>用 debug.setmetatable 之外的路子，而别的类型<b>只能</b>用它，
  所以两条分岔都得走一遍——矩阵里 8 种类型正好把两条都压到。
]]
local function withmt(v, mt)
    if type(v) == "table" then
        setmetatable(v, mt)
        return function() setmetatable(v, nil) end
    end
    local old = debug.getmetatable(v)
    debug.setmetatable(v, mt)
    return function() debug.setmetatable(v, old) end
end

print("BEGIN", #TN, #BIN, #MT, #KIND)

for oi = 1, #BIN do
    local op, names = BIN[oi][1], BIN[oi][2]
    local f = load("local a, b = ... return a " .. op .. " b", "=X")
    for ni = 1, #names do
        local mname = names[ni]
        for ai = 1, #TN do
            for bi = 1, #TN do
                for mi = 1, #MT do
                    local cfg = MT[mi]
                    -- 无元表这一档只跑一遍：摆法一样，多跑纯属浪费行数
                    local kinds = (mi == 1) and { "fn" } or KIND
                    -- 只在第一个元方法名上跑「无元表」，后面的只跑有元表的
                    if ni == 1 or mi > 1 then
                        for ki = 1, #kinds do
                            local a, b = mkv(TN[ai]), mkv(TN[bi])
                            local undo = {}
                            if cfg[2] then
                                undo[#undo + 1] = withmt(a, { [mname] = mkmeth(kinds[ki], "L") })
                            end
                            if cfg[3] then
                                undo[#undo + 1] = withmt(b, { [mname] = mkmeth(kinds[ki], "R") })
                            end
                            local r = eval(f, a, b)
                            for i = #undo, 1, -1 do undo[i]() end
                            print(op, TN[ai], TN[bi], cfg[1], mname, kinds[ki], r)
                        end
                    end
                end
            end
        end
    end
end

print("END")
                """);
    }

    /** 第二片：__index / __newindex / __call / __len / 一元 / 拼接链 */
    @Test
    void indexCallLenUnaryConcatGridMatchesReference() {
        Diff.assertMatches("""
-- 元方法矩阵 · 第二片：索引 / 新索引 / 调用 / 长度 / 一元 / 拼接链
--
-- 第一片把所有<b>运算符</b>过了一遍，但元方法里有一半不是运算符：
-- __index / __newindex / __call / __len / __concat 这些走的是
-- lvm.c 里完全另一条路（OP_GETTABUP 的 TM 分支、luaV_objlen、
-- OP_CONCAT 的 catrecursively）。这一片专门扫它们。
--
-- 不变的规矩：每个求值都包在 pcall 里（第 38 条），
-- 结果里的地址一律归一化（第 42 条），
-- 整数和浮点分开看（第 11 条）。

local function show(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

-- 每次求值都在 pcall 里：未捕获的抛错让脚本当场死掉，
-- 后面几百行一条也不会比，而用例照样绿。
local function go(tag, f, ...)
    local r = table.pack(pcall(f, ...))
    local out = { tag, tostring(r[1]) }
    for i = 2, r.n do out[#out + 1] = show(r[i]) end
    print(table.concat(out, " "))
end

-- 每次都建新表：元表状态会在用例之间互相污染
local function tbl() return {} end

--[[
  __index / __newindex 的六种形态。
  「是表」这一档要单独盯：luaV_finishget 在 __index 是表时会<b>递归</b>再取一次，
  而且是循环不是递归（while 循环里换 t = tm）。__index 链成环时该报
  '__index' chain too long; possibly looping —— 那也是这一档的事。
]]
local FORMS = { "absent", "nil", "func", "tbl", "num", "str", "tblchain" }

local function mkform(name, kind, tag)
    if kind == "absent" then return nil end
    if kind == "nil" then return nil end
    if kind == "func" then
        return function(t, k) return tag .. ":" .. tostring(k) end
    end
    if kind == "tbl" then return { x = tag .. "X", y = tag .. "Y", [1] = tag .. "1" } end
    if kind == "num" then return 42 end
    if kind == "str" then return "plainstring" end
    if kind == "tblchain" then
        -- __index 是表，那张表自己又有 __index
        local inner = setmetatable({}, { __index = { deep = tag .. "DEEP", x = tag .. "INX" } })
        return inner
    end
end

local KEYS = { "x", "y", "z", "deep" }

print("BEGIN")

-- ============ 1) __index：取值 ============
for fi = 1, #FORMS do
    local kind = FORMS[fi]
    for ki = 1, #KEYS do
        local k = KEYS[ki]
        local t = tbl()
        local m = mkform("__index", kind, "I")
        if kind ~= "absent" then
            setmetatable(t, { __index = m })
        end
        go("IDX", function(tt, kk) return tt[kk] end, t, k)
    end
    -- 整数键与浮点键
    for _, k in ipairs({ 1, 2.0, 2.5, true }) do
        local t = tbl()
        local m = mkform("__index", kind, "I")
        if kind ~= "absent" then
            setmetatable(t, { __index = m })
        end
        go("IDXK", function(tt, kk) return tt[kk] end, t, k)
    end
    -- nil 与 NaN 键：应当报错
    local t = tbl()
    local m = mkform("__index", kind, "I")
    if kind ~= "absent" then
        setmetatable(t, { __index = m })
    end
    go("IDXNIL", function(tt) return tt[nil] end, t)
    go("IDXNAN", function(tt) return tt[0 / 0] end, t)
end

-- ============ 2) __newindex：赋值 ============
for fi = 1, #FORMS do
    local kind = FORMS[fi]
    for ki = 1, #KEYS do
        local k = KEYS[ki]
        local t = tbl()
        local m
        if kind == "func" then
            m = function(tt, kk, v) rawset(tt, kk, "S" .. tostring(v)) end
        else
            m = mkform("__newindex", kind, "N")
        end
        if kind ~= "absent" then
            setmetatable(t, { __newindex = m })
        end
        go("NIDX", function(tt, kk)
            tt[kk] = "V"
            return rawget(tt, kk), tt[kk]
        end, t, k)
    end
    -- 键已存在（rawget 命中）：__newindex 不该被调用
    local t = { x = "old" }
    local hit = false
    if kind == "func" then
        setmetatable(t, { __newindex = function(tt, kk, v) hit = true end })
    elseif kind ~= "absent" then
        setmetatable(t, { __newindex = mkform("__newindex", kind, "N") })
    end
    go("NIDXHIT", function(tt)
        tt.x = "new"
        return rawget(tt, "x"), hit
    end, t)
    -- nil 键
    local t2 = tbl()
    if kind ~= "absent" then
        setmetatable(t2, { __newindex = (kind == "func")
            and function(tt, kk, v) end or mkform("__newindex", kind, "N") })
    end
    go("NIDXNIL", function(tt) tt[nil] = 1 return rawget(tt, "x") end, t2)
end

--[[
  ============ 3) __index / __newindex 成环 ============
  luaV_finishget 里的循环有 LUAI_MAXCCALLS 上限，超了报
  "'__index' chain too long; possibly looping"。
  这一档是「循环 vs 递归」的分水岭——写成递归会 Java StackOverflow。
]]
do
    local a, b = tbl(), tbl()
    setmetatable(a, { __index = b })
    setmetatable(b, { __index = a })
    go("IDXLOOP", function(tt) return tt.nope end, a)

    local c, d = tbl(), tbl()
    setmetatable(c, { __newindex = d })
    setmetatable(d, { __newindex = c })
    go("NIDXLOOP", function(tt) tt.nope = 1 return "ok" end, c)
end

-- ============ 4) __call ============
do
    local CALLS = { "absent", "func", "num", "str", "tbl" }
    for i = 1, #CALLS do
        local kind = CALLS[i]
        local m
        if kind == "func" then
            m = function(self, ...) return "C", select("#", ...), ... end
        elseif kind == "num" then m = 7
        elseif kind == "str" then m = "s"
        elseif kind == "tbl" then m = {}
        end
        local t = tbl()
        if kind ~= "absent" then setmetatable(t, { __call = m }) end
        for na = 0, 3 do
            go("CALL", function(tt, n)
                local args = {}
                for j = 1, n do args[j] = j end
                return tt(table.unpack(args, 1, n))
            end, t, na)
        end
    end
    -- userdata 上的 __call
    local ud = io.stdout
    local old = debug.getmetatable(ud)
    debug.setmetatable(ud, { __call = function(self, x) return "UD", x end })
    go("CALLUD", function(u) return u(9) end, ud)
    debug.setmetatable(ud, old)
end

-- ============ 5) __len：返回值类型决定一切 ============
do
    local LENS = {
        { "int", function() return 5 end },
        { "fltint", function() return 6.0 end },
        { "fltfrac", function() return 6.5 end },
        { "str", function() return "7" end },
        { "strbad", function() return "x" end },
        { "tbl", function() return {} end },
        { "bool", function() return true end },
        { "nil", function() return nil end },
        { "void", function() end },
        { "neg", function() return -3 end },
        { "huge", function() return math.huge end },
        { "nan", function() return 0 / 0 end },
        { "maxi", function() return math.maxinteger end },
        { "mini", function() return math.mininteger end },
    }
    for i = 1, #LENS do
        local nm, f = LENS[i][1], LENS[i][2]
        local t = setmetatable({ 1, 2, 3 }, { __len = f })
        go("LEN", function(tt) return #tt end, t)
        go("RAWLEN", function(tt) return rawlen(tt) end, t)
        print("LENNAME", nm)
    end
    -- 没有 __len 的表与字符串
    local plain = { 1, 2, 3 }
    go("LENPLAIN", function(tt) return #tt end, plain)
    go("LENSTR", function(s) return #s end, "hello")
end

-- ============ 6) 一元：- 与 ~ ============
do
    local TN = { "nil", "false", "true", "int", "flt", "str", "tbl", "fn" }
    local function mkv(t)
        if t == "nil" then return nil end
        if t == "false" then return false end
        if t == "true" then return true end
        if t == "int" then return 7 end
        if t == "flt" then return 2.5 end
        if t == "str" then return "12" end
        if t == "tbl" then return {} end
        return print
    end
    local UN = { "-", "~", "#", "not" }
    for ui = 1, #UN do
        local op = UN[ui]
        for ai = 1, #TN do
            local fneg = load("local a = ... return " .. op .. " a", "=U")
            local afn = load("local a = ... return " .. op .. " a", "=U")
            -- 无元表
            go("UN" .. op, fneg, mkv(TN[ai]))
            -- 挂 __unm / __bnot / __len
            for _, nm in ipairs({ "__unm", "__bnot", "__len" }) do
                local v = mkv(TN[ai])
                -- 改全局类型元表要存下原值再还回去，别还成 nil（字符串那份是
                -- string 库装上去的），否则后面所有 ("x"):len() 都会炸
                local old = (type(v) == "table") and nil or debug.getmetatable(v)
                if type(v) == "table" then
                    setmetatable(v, { [nm] = function(x) return nm .. show(x) end })
                else
                    debug.setmetatable(v, { [nm] = function(x) return nm .. show(x) end })
                end
                go("UN" .. op .. nm, afn, v)
                if type(v) == "table" then
                    setmetatable(v, nil)
                else
                    debug.setmetatable(v, old)
                end
            end
        end
    end
end

--[[
  ============ 7) 拼接链 ============
  OP_CONCAT 是「一批一批拼」：luaV_concat 一次收栈顶若干个，
  有 __concat 就走 catrecursively —— 它是<b>从后往前</b>两两拼的，
  而且一次只调一个元方法。所以三个值里只有中间那个有 __concat 时，
  调用顺序与「从左到右」的直觉是反的。
]]
do
    local function mk(tag)
        -- 用 show 而不是 tostring：表地址每次都不同，不归一化整片假红（第 42 条）
        return setmetatable({}, { __concat = function(x, y) return "(" .. show(x) .. tag .. show(y) .. ")" end })
    end
    for mask = 0, 7 do
        local a, b, c = "a", "b", "c"
        if mask & 1 ~= 0 then a = mk("A") end
        if mask & 2 ~= 0 then b = mk("B") end
        if mask & 4 ~= 0 then c = mk("C") end
        go("CAT", function(x, y, z) return x .. y .. z end, a, b, c)
        print("CATMASK", mask)
    end
    -- 四五个值：一批拼不完的情况
    for mask = 0, 15 do
        local v = {}
        for i = 1, 4 do
            v[i] = (mask & (1 << (i - 1)) ~= 0) and mk("" .. i) or ("" .. i)
        end
        go("CAT4", function(a, b, c, d) return a .. b .. c .. d end,
            v[1], v[2], v[3], v[4])
        print("CAT4MASK", mask)
    end
end

print("END")
                """);
    }

    /** 第三片：元方法里 yield */
    @Test
    void yieldInsideMetamethodMatchesReference() {
        Diff.assertMatches("""
-- 元方法矩阵 · 第三片：元方法里 yield
--
-- 为什么单开一片：协程侧只有 pcall / xpcall 注册了续体（第 5 条），
-- 而元方法是由 <b>VM 指令</b>调的——OP_ADD 走 callTmMightYield，
-- OP_CONCAT 走 catrecursively，__call 走普通调用。三条路的续体各不相同，
-- 而且「能不能 yield」取决于外面套的是 C 函数还是 Lua 函数
-- （跨 C 边界 yield 要报 attempt to yield across a C-call boundary）。
-- 前面几片全在主线程跑，这一片是把同一批元方法塞进 coroutine.create 再跑一遍
-- ——第 7 条：这类 bug 只在协程里现形。

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

--[[
  每个事件跑三种「外套」：
    bare   —— 协程里裸写这个表达式
    pcall  —— 包 pcall（续体已注册，应当能 yield 并被 pcall 收住）
    cfn    —— 包在 C 函数里（table.insert / table.sort），应当报跨 C 边界
  同一条表达式，三种外套给出三种答案，少一种都看不出续体写没写对。
]]
local function runcase(tag, build)
    for _, outer in ipairs({ "bare", "pcall", "cfn" }) do
        local co = coroutine.create(function()
            if outer == "bare" then
                return build()
            elseif outer == "pcall" then
                return pcall(build)
            else
                return pcall(function()
                    local t = { 1, 2, 3 }
                    table.insert(t, 4) -- 触发 __len 的 C 边界
                    return "ins"
                end)
            end
        end)
        local ok, a = coroutine.resume(co)
        print(tag, outer, "r1", tostring(ok), show(a))
        local ok2, b = coroutine.resume(co, "R1")
        print(tag, outer, "r2", tostring(ok2), show(b))
        local ok3, c = coroutine.resume(co, "R2")
        print(tag, outer, "r3", tostring(ok3), show(c))
    end
end

print("BEGIN")

--[[
  ============ 1) 算术 / 位运算的 __add 里 yield ============
  注意 OP_ADD 这类指令是「先算再写」：元方法的结果先是落在栈顶，
  再由 Protect 之后的写回指令搬进目标寄存器。yield 恰好卡在中间，
  恢复时那个临时结果不能再算一遍。
]]
do
    local mtadd = { __add = function(x, y) return coroutine.yield("in-add") end }
    runcase("ADD", function()
        local a, b = setmetatable({}, mtadd), setmetatable({}, mtadd)
        local v = a + b
        return "sum", v
    end)

    local mtconcat = { __concat = function(x, y) return coroutine.yield("in-concat") end }
    runcase("CONCAT", function()
        local a, b = setmetatable({}, mtconcat), setmetatable({}, mtconcat)
        local v = a .. b
        return "cat", v
    end)

    local mtunm = { __unm = function(x) return coroutine.yield("in-unm") end }
    runcase("UNM", function()
        local a = setmetatable({}, mtunm)
        local v = -a
        return "neg", v
    end)

    local mtlen = { __len = function(x) return coroutine.yield("in-len") end }
    runcase("LEN", function()
        local a = setmetatable({ 1, 2 }, mtlen)
        local v = #a
        return "len", v
    end)

    local mteq = { __eq = function(x, y) return coroutine.yield("in-eq") end }
    runcase("EQ", function()
        local a, b = setmetatable({}, mteq), setmetatable({}, mteq)
        local v = (a == b)
        return "eq", v
    end)

    local mtlt = { __lt = function(x, y) return coroutine.yield("in-lt") end }
    runcase("LT", function()
        local a, b = setmetatable({}, mtlt), setmetatable({}, mtlt)
        local v = (a < b)
        return "lt", v
    end)

    -- __le 缺失 → 退回 __lt，续体里还要取反（CallInfo.leq）
    local mtle = { __lt = function(x, y) return coroutine.yield("in-lt-for-le") end }
    runcase("LE", function()
        local a, b = setmetatable({}, mtle), setmetatable({}, mtle)
        local v = (a <= b)
        return "le", v
    end)

    local mtindex = { __index = function(t, k) return coroutine.yield("in-index") end }
    runcase("INDEX", function()
        local a = setmetatable({}, mtindex)
        local v = a.foo
        return "idx", v
    end)

    local mtnewindex = { __newindex = function(t, k, v) return coroutine.yield("in-newindex") end }
    runcase("NEWINDEX", function()
        local a = setmetatable({}, mtnewindex)
        a.foo = 1
        return "nidx", "ok"
    end)

    local mtcall = { __call = function(self, ...) return coroutine.yield("in-call") end }
    runcase("CALL", function()
        local a = setmetatable({}, mtcall)
        local v = a(1, 2)
        return "call", v
    end)
end

--[[
  ============ 2) 在 C 函数里触发 __len / __lt ============
  这些才是「跨 C 边界」的真正入口：table.insert 取长度、table.sort 比较。
  参考实现报 attempt to yield across a C-call boundary（或 cannot resume
  dead coroutine 之类），语义跟 Lua 函数里的 yield 完全不同。
]]
--[[
  每个 case 在<b>协程里</b>跑，然后连 resume 三轮：
    r1 —— 第一次 resume，多半是 yield 出来的值
    r2 —— 带着 "R1" 恢复，元方法应当返回它
    r3 —— 协程已死，应当报 cannot resume dead coroutine
  重点在 table.insert / table.sort 那几条：它们<b>没有</b>注册续体，
  所以元方法里 yield 必须报 attempt to yield across a C-call boundary。
]]
local function resume3(tag, fn)
    -- 直接把 pcall 的结果摊开返回：resume 于是给 (true, ok, err...)，
    -- 错误消息<b>看得见</b>。包成 table 的话只能看到 "table: 0x..."。
    local co = coroutine.create(function() return pcall(fn) end)
    for i = 1, 3 do
        local r = table.pack(coroutine.resume(co, "R" .. i))
        local out = { tag, "r" .. i }
        for j = 1, r.n do out[#out + 1] = show(r[j]) end
        print(table.concat(out, " "))
    end
end

do
    local ylen = setmetatable({ 1, 2, 3 }, {
        __len = function() return coroutine.yield("in-len-c") end,
    })
    local ylt = setmetatable({ 3, 1, 2 }, {
        __lt = function(a, b) return coroutine.yield("in-lt-c") end,
    })
    local yindex = setmetatable({}, {
        __index = function(t, k) return coroutine.yield("in-index-c") end,
    })
    local ynewindex = setmetatable({}, {
        __newindex = function(t, k, v) return coroutine.yield("in-newindex-c") end,
    })
    local ycall = setmetatable({}, {
        __call = function(s, ...) return coroutine.yield("in-call-c") end,
    })
    local yconcat = setmetatable({}, {
        __concat = function(a, b) return coroutine.yield("in-concat-c") end,
    })

    resume3("CFN_INSERT", function() table.insert(ylen, 9) return "ok" end)
    resume3("CFN_REMOVE", function() return table.remove(ylen) end)
    resume3("CFN_CONCAT", function() return yconcat .. "x" end)
    resume3("CFN_SORT", function() table.sort(ylt) return "sorted" end)
    -- 这里正是要验「tostring 不认 __index」，结果必然是 "table: 0x..."；
    -- 地址每次都不同，不抹掉这一片假红（第 42 条）
    resume3("CFN_TOSTRING", function()
        return (tostring(yindex):gsub("0x%x+", "PTR"))
    end)
    resume3("CFN_RAWGET", function() return rawget(yindex, "k") end)
    resume3("CFN_RAWEQUAL", function() return rawequal(ylen, ylt) end)
    resume3("CFN_GSUB", function() return string.gsub("abc", "b", yindex) end)
    resume3("CFN_CALL", function() return ycall(1) end)
    resume3("CFN_NEWIDX", function() ynewindex.x = 1 return "ok" end)
    resume3("CFN_LEN", function() return #ylen end)
end

--[[
  ============ 3) 跨 C 边界：在 C 函数内部触发 yield ============
  第 5 条：只有 pcall / xpcall 注册了续体，所以 table.sort 的比较函数里
  yield 必须报错。这条要与「Lua 函数里 yield 成功」成对出现才说明续体写对了。
]]
do
    local co = coroutine.create(function()
        return pcall(function()
            return table.sort({ 3, 1, 2 }, function(a, b)
                coroutine.yield("from-comp")
                return a < b
            end)
        end)
    end)
    print("SORTYIELD r1", tostring(coroutine.resume(co)))

    -- pcall 里的 yield 应当成功
    local co2 = coroutine.create(function()
        return pcall(function() return coroutine.yield("in-pcall") end)
    end)
    print("PCALLYIELD r1", tostring(coroutine.resume(co2)))
    print("PCALLYIELD r2", tostring(coroutine.resume(co2, "back")))

    -- xpcall 里的 yield
    local co3 = coroutine.create(function()
        return xpcall(function() return coroutine.yield("in-xpcall") end,
            function(e) return "H:" .. tostring(e) end)
    end)
    print("XPCALLYIELD r1", tostring(coroutine.resume(co3)))
    print("XPCALLYIELD r2", tostring(coroutine.resume(co3, "back2")))
end

--[[
  ============ 4) 元方法里抛错：错误对象与位置前缀 ============
  元方法抛的错要经过 C 边界传回去；非字符串的错误对象在 pcall 之外
  会被 msghandler 处理成 (error object is a X value)。
]]
--[[
  ============ 3) 元方法里抛错：错误对象与位置前缀 ============
  第 4 条：只有 LuaError 会被 pcall 接住，实现自身的 Java 异常必须继续上冒。
  这里压的是「错误对象不是字符串」那一档——脚本里 error(42) 传到宿主手上时
  要变成 (error object is a number value)。
]]
do
    local ERR = {
        { "string", function() error("boom") end },
        { "string0", function() error("boom", 0) end },
        { "num", function() error(42) end },
        { "nil", function() error() end },
        { "tbl", function() error({}) end },
        { "bool", function() error(true) end },
        { "strmt", function() error(setmetatable({}, { __tostring = function() return "TS" end })) end },
    }
    local EVENTS = {
        "__add", "__sub", "__mul", "__div", "__idiv", "__mod", "__pow",
        "__band", "__bor", "__bxor", "__shl", "__shr", "__unm", "__bnot",
        "__concat", "__len", "__eq", "__lt", "__le", "__index", "__newindex",
        "__call",
    }
    for ei = 1, #EVENTS do
        local ev = EVENTS[ei]
        for ri = 1, #ERR do
            local nm, f = ERR[ri][1], ERR[ri][2]
            local t = setmetatable({}, { [ev] = f })
            if ev == "__unm" or ev == "__bnot" or ev == "__len" then
                local op = (ev == "__unm") and "-" or ((ev == "__bnot") and "~" or "#")
                go("ERR" .. ev .. nm, load("local a = ... return " .. op .. " a", "=E"), t)
            elseif ev == "__index" then
                go("ERR" .. ev .. nm, function(tt) return tt.zz end, t)
            elseif ev == "__newindex" then
                go("ERR" .. ev .. nm, function(tt) tt.zz = 1 end, t)
            elseif ev == "__call" then
                go("ERR" .. ev .. nm, function(tt) return tt() end, t)
            else
                local op = ({
                    __add = "+", __sub = "-", __mul = "*", __div = "/",
                    __idiv = "//", __mod = "%", __pow = "^",
                    __band = "&", __bor = "|", __bxor = "~",
                    __shl = "<<", __shr = ">>", __concat = "..",
                    __eq = "==", __lt = "<", __le = "<=",
                })[ev]
                -- __eq 只在同类型的表之间才试，所以给 b 也挂上同一份元表
                local b = setmetatable({}, getmetatable(t))
                go("ERR" .. ev .. nm, load("local a, b = ... return a " .. op .. " b", "=E"), t, b)
            end
        end
    end
end

print("END")
                """);
    }

    /** 第四片：__pairs / __close / raw* / get-setmetatable 边界 */
    @Test
    void pairCloseRawMetatableEdgesMatchReference() {
        Diff.assertMatches("""
-- 元方法矩阵 · 第四片：__pairs / __close / raw* / get-setmetatable 边界
--
-- 前两片扫的是运算符与索引，这一片补剩下的「协议类」元方法：
-- __pairs（迭代协议）、__close（作用域退出协议）、__metatable（元表保护）、
-- __name（错误消息里的类型名），以及四个 raw* 的越界行为。
--
-- 规矩照旧：一个求值一层 pcall（第 38 条）；地址归一（第 42 条）；
-- 整数浮点分开（第 11 条）；绝不打印字符串键的 pairs 顺序（第 43 条）。

local function show(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

-- 每次求值都在 pcall 里：未捕获的抛错让脚本当场死掉，
-- 后面几百行一条也不会比，而用例照样绿。
local function go(tag, f, ...)
    local r = table.pack(pcall(f, ...))
    local out = { tag, tostring(r[1]) }
    for i = 2, r.n do out[#out + 1] = show(r[i]) end
    print(table.concat(out, " "))
end

print("BEGIN")

--[[
  ============ 1) __pairs ============
  luaB_pairs 的逻辑是：先查 __pairs，有就<b>整个替代</b>迭代协议，
  返回三个值；没有才退化成 (next, t, nil)。
  关键在于「有 __pairs 但返回的不是函数」时，报错发生在
  <b>generic for 的第一次调用</b>，而不是 pairs 里。
]]
do
    local FORMS = {
        { "absent", nil },
        { "func3", function(t) return function(s, c)
            c = (c or 0) + 1
            if c > 3 then return nil end
            return c, c * c
        end, "STATE", nil end },
        { "func2", function(t) return next, { a = 1, b = 2 } end },
        { "func1", function(t) return next end },
        { "func0", function(t) return end },
        { "num", 42 },
        { "str", "s" },
        { "tbl", {} },
        { "bool", true },
    }
    for i = 1, #FORMS do
        local nm, v = FORMS[i][1], FORMS[i][2]
        local t = setmetatable({}, (nm == "absent") and nil or { __pairs = v })
        -- pairs() 返回什么
        go("PAIRS" .. nm, function(tt)
            local a, b, c = pairs(tt)
            return type(a), type(b), tostring(c)
        end, t)
        -- 真跑一轮 generic for
        go("PAIRSFOR" .. nm, function(tt)
            local acc = {}
            for k, val in pairs(tt) do acc[#acc + 1] = tostring(k) .. "=" .. tostring(val) end
            table.sort(acc)
            return table.concat(acc, ",")
        end, t)
    end
    -- 普通表上的 pairs，确认退化路径
    go("PAIRSPLAIN", function()
        local acc = {}
        for k, val in pairs({ a = 1, b = 2, c = 3 }) do acc[#acc + 1] = tostring(k) end
        table.sort(acc)
        return table.concat(acc, ",")
    end)
end

--[[
  ============ 2) raw* 的越界行为 ============
  这四个是「绕过元方法」的后门，所以它们的<b>参数校验</b>才是重点：
  给错类型要报错，而且报错的位置前缀来自 libError（第 8 条）。
]]
do
    local TN = { "nil", "false", "true", "int", "flt", "str", "tbl", "fn" }
    local function mkv(t)
        if t == "nil" then return nil end
        if t == "false" then return false end
        if t == "true" then return true end
        if t == "int" then return 7 end
        if t == "flt" then return 2.5 end
        if t == "str" then return "ab" end
        if t == "tbl" then return {} end
        return print
    end
    for _, t in ipairs(TN) do
        local v = mkv(t)
        go("RAWLEN" .. t, function(x) return rawlen(x) end, v)
        go("RAWGET" .. t, function(x) return rawget(x, "k") end, v)
    end
    -- rawequal：8 × 8 全比
    for ai = 1, #TN do
        for bi = 1, #TN do
            go("RAWEQ", rawequal, mkv(TN[ai]), mkv(TN[bi]))
        end
    end
    -- rawset 的非法键
    -- 这里<b>不能</b>用 ipairs：第一个元素就是 nil，ipairs 会当场截断，
    -- NaN 那条一次也跑不到（第 47 条）。按下标手动取。
    local RAWK = { [1] = "nil", [2] = "nan", [3] = "tbl", [4] = "bool" }
    local RAWV = { [1] = nil, [2] = 0 / 0, [3] = {}, [4] = true }
    for i = 1, 4 do
        go("RAWSET", function(t, kk) rawset(t, kk, 1) return rawlen(t) end, {}, RAWV[i])
        print("RAWSETK", RAWK[i])
    end
    go("RAWSETN", function() rawset({}, 1, 1) return "ok" end)
    go("RAWSETSTR", function() rawset({}, "a", 1) return "ok" end)
    go("RAWSETFLT", function() rawset({}, 2.0, 1) return "ok" end)
end

--[[
  ============ 3) getmetatable / setmetatable 边界 ============
  __metatable 保护：getmetatable 返回它，setmetatable 直接报错。
  注意 setmetatable 的第 1 个参数被 luaL_checktype 限成<b>表</b>，
  给别的值设元表只能走 debug.setmetatable（改的是全局类型元表）。
]]
do
    local TN = { "nil", "false", "true", "int", "flt", "str", "tbl", "fn" }
    local function mkv(t)
        if t == "nil" then return nil end
        if t == "false" then return false end
        if t == "true" then return true end
        if t == "int" then return 7 end
        if t == "flt" then return 2.5 end
        if t == "str" then return "ab" end
        if t == "tbl" then return {} end
        return print
    end
    for _, t in ipairs(TN) do
        go("GETMT" .. t, function(x) return getmetatable(x) end, mkv(t))
    end

    -- __metatable 保护
    local prot = setmetatable({}, { __metatable = "LOCKED" })
    go("PROT_GET", function(t) return getmetatable(t) end, prot)
    go("PROT_SET", function(t) return setmetatable(t, {}) end, prot)
    go("PROT_SETNIL", function(t) return setmetatable(t, nil) end, prot)

    -- setmetatable 的参数校验
    go("SETMT_NUM", function() return setmetatable(1, {}) end)
    go("SETMT_NIL", function() return setmetatable(nil, {}) end)
    go("SETMT_NOTBL", function() return setmetatable({}, 42) end)
    go("SETMT_STR", function() return setmetatable({}, "s") end)
    go("SETMT_NILMT", function()
        local t = setmetatable({}, { __index = { x = 1 } })
        setmetatable(t, nil)
        return t.x
    end)
    go("SETMT_REPLACE", function()
        local t = setmetatable({}, { __index = { x = 1 } })
        setmetatable(t, { __index = { x = 2 } })
        return t.x
    end)

    -- 元表自己指向自己
    go("SETMT_SELF", function()
        local t = {}
        setmetatable(t, t)
        return t.nope
    end)
end

--[[
  ============ 4) __close ============
  三条要分开盯：
    <1> 正常退出作用域，err 参数是 nil
    <2> 出错解绕，err 是那个错误对象
    <3> __close 自己抛错——这个新错要<b>顶掉</b>原来的错
  以及非可调用的 __close（报 attempt to call a X value），
  还有 __close 里 yield。
]]
do
    local log = {}
    local function rec(s) log[#log + 1] = s end

    -- <1> 正常退出
    do
        local closed = false
        do
            local x <close> = setmetatable({}, { __close = function(self, err)
                closed = true; rec("c1:" .. tostring(err))
            end })
            rec("body")
        end
        rec("closed=" .. tostring(closed))
    end

    -- <2> 出错解绕
    do
        local ok
        local function f()
            local x <close> = setmetatable({}, { __close = function(self, err)
                rec("c2:" .. tostring(err))
            end })
            error("boom2")
        end
        go("CLOSE_ERR", f)
    end

    -- <3> __close 自己抛错
    do
        local function f()
            local x <close> = setmetatable({}, { __close = function(self, err) error("in-close") end })
            error("orig")
        end
        go("CLOSE_THROW", f)
    end

    -- 非可调用的 __close
    do
        local function f()
            local x <close> = setmetatable({}, { __close = 42 })
        end
        go("CLOSE_NUM", f)
    end
    do
        local function f()
            local x <close> = setmetatable({}, { __close = "str" })
        end
        go("CLOSE_STR", f)
    end
    do
        local function f()
            local x <close> = setmetatable({}, { __close = {} })
        end
        go("CLOSE_TBL", f)
    end

    -- 多个 tbc：按<b>逆序</b>关
    do
        local function f()
            local a <close> = setmetatable({}, { __close = function() rec("a") end })
            local b <close> = setmetatable({}, { __close = function() rec("b") end })
            error("multi")
        end
        go("CLOSE_MULTI", f)
    end

    -- __close 里 yield
    do
        local co = coroutine.create(function()
            local x <close> = setmetatable({}, { __close = function() return coroutine.yield("y-close") end })
            return "body-done"
        end)
        for i = 1, 3 do
            local r = table.pack(coroutine.resume(co, "R" .. i))
            local out = { "CLOSE_YIELD", "r" .. i }
            for j = 1, r.n do out[#out + 1] = show(r[j]) end
            print(table.concat(out, " "))
        end
    end

    --[[
      <const> 的重复赋值是<b>编译期</b>错误，不是运行时错误，
      所以不能直接写在脚本里——那样整份脚本都加载不了。
      必须用 load 现造，让错误发生在被保护的调用里。
    ]]
    do
        local f, e = load("local c <const> = 1 c = 2 return c", "=C")
        print("CONST_LOAD", type(f), (type(e) == "string") and (e:gsub("0x%x+", "PTR")) or tostring(e))
        if f then go("CONST", f) end
    end
    -- <close> 的元方法在<b>声明时</b>就要检查，同样是编译期的事
    do
        local f, e = load("local x <close> = 5", "=C2")
        print("CLOSE_LOAD", type(f), (type(e) == "string") and (e:gsub("0x%x+", "PTR")) or tostring(e))
        if f then go("CLOSE_DECL", f) end
    end

    -- 打印收集到的顺序
    print("LOG", table.concat(log, "|"))
end

--[[
  ============ 5) __name ============
  第 65 条：只有表与完整 userdata 认 __name，其余类型连类型元表都不看。
  错误消息里的名字来自 __name，所以这里直接压「报错」这条路。
]]
do
    local named = setmetatable({}, { __name = "MyType" })
    local namedstr = setmetatable({}, { __name = "StrType" })
    local namednum = setmetatable({}, { __name = 42 })

    go("NAME_ADD", function(t) return t + 1 end, named)
    go("NAME_INDEX", function(t) return t.zz end, named)
    go("NAME_CALL", function(t) return t() end, named)
    go("NAME_LEN", function(t) return #t end, named)
    go("NAME_CONCAT", function(t) return t .. "x" end, named)
    go("NAME_CMP", function(t) return t < 1 end, named)
    go("NAME_STR", function(t) return t .. "x" end, namedstr)
    go("NAME_NUM", function(t) return t + 1 end, namednum)

    -- userdata 上的 __name
    local ud = io.stdout
    local old = debug.getmetatable(ud)
    debug.setmetatable(ud, { __name = "UDType" })
    go("NAME_UD", function(u) return u + 1 end, ud)
    debug.setmetatable(ud, old)

    --[[
      其余类型：__name 应当被忽略。
      第 65 条：只有表与完整 userdata 认它——其余类型连类型元表都不看，
      所以错误消息里出现的还是<b>真实类型名</b>（string / number / ...），
      不是 __name 给的那个。
    ]]
    --[[
      改全局类型元表必须<b>存下原值再还回去</b>，不能还成 nil——
      字符串那份是 string 库自己装上去的（{@code mt[LUA_TSTRING]}），
      抹掉之后后面任何 {@code ("x"):len()} 都会炸。
    ]]
    local function withmt(v, mt)
        if type(v) == "table" then
            setmetatable(v, mt)
            return function() setmetatable(v, nil) end
        end
        local old = debug.getmetatable(v)
        debug.setmetatable(v, mt)
        return function() debug.setmetatable(v, old) end
    end

    local IGN = {
        { "number", 1 },
        { "boolean", true },
        { "string", "s" },
        { "function", print },
    }
    for _, e in ipairs(IGN) do
        local nm, v = e[1], e[2]
        local undo = withmt(v, { __name = "IGNORED" })
        go("NAME_IGN_" .. nm, function(x) return x + 1 end, v)
        undo()
    end
end

print("END")
                """);
    }

    private MetamethodTest() {
    }
}
