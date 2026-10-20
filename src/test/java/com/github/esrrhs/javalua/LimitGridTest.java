package com.github.esrrhs.javalua;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeAll;

import java.io.File;

/**
 * 上限与边界、以及「报错里的函数名」—— 阶段 12。
 * <p>
 * 为什么单开一类：覆盖率对「调用一个必定抛错的辅助函数」这种形状<b>恒判未覆盖</b>
 * （全代码库 31 条，一条不漏，连天天跑的语法错也算没跑 —— JaCoCo 的探针落在块的
 * 出口，而这条语句永远不出口），所以「哪条报错没被测」根本问不出来。只能反过来：
 * 把各库<b>上限与边界</b>的判据挨个撞一遍，直接跟参考实现比。
 * <p>
 * 两片各自成阵：
 * <ul>
 *   <li><b>片一：上限与边界</b>。{@code string.rep} 的
 *       {@code l + lsep > MAXSIZE / n}（MAXSIZE 是<b>无符号</b> 2^64-1；只铺
 *       判据先拦下、根本没分配的那几档 —— 真去分配的档位 Java 会 OOM、
 *       C 给 {@code not enough memory}，属有意偏离）；
 *       {@code utf8} 的四种越界与 {@code char} 的码点上下界；
 *       {@code table.move/unpack/insert/remove} 的
 *       {@code too many elements to move} 等五条上限，外加 {@code __len}
 *       不是整数时 {@code luaL_len} 那句 {@code object length is not an integer}；
 *       协程状态机的六种错；{@code select} 的 {@code index out of range}、
 *       受保护元表；{@code string.pack} 的畸形格式；{@code io} 的非法模式与格式；
 *       {@code debug} 的越界层号与 upvalue 下标；末尾一组是「内存错误 ×
 *       协程边界」（{@code wrap} 重新抛出 / {@code resume} 返回值 /
 *       {@code close} 返回值 / 带 {@code <close>} 变量时谁顶掉谁）。</li>
 *   <li><b>片二：报错里的函数名 × 全库函数 × 八种调用写法 × 两组装参</b>。
 *       {@code luaL_argerror} 的名字是 {@code ldebug.c: funcnamefromcall}
 *       从<b>调用者那一帧</b>反推的：常量键取成员得键名、局部别名得变量名、
 *       推不出来才退回 {@code pushglobalfuncname} 的 {@code coroutine.close}
 *       这种全名。函数表<b>运行时枚举</b>（{@code _G} + 九个库表，排序后铺），
 *       不手写清单。装参分「给一个空表」与「一个都不给」两组，后者专门盯
 *       {@code luaL_checkany} 的 {@code value expected} 那一路。</li>
 * </ul>
 * <p>
 * 两片一共咬出四处真分歧，都是同一族：
 * <ol>
 *   <li>{@code coroutine} 那六个函数把 {@code 'coroutine.X'} <b>写死</b>在报错里，
 *       而参考实现一律用短名 —— 改走 {@link LuaState#argError}。</li>
 *   <li>{@code tonumber} 无参那句（{@code luaL_checkany}）同样写死了
 *       {@code 'tonumber'}；{@code local f = tonumber; f()} 参考报 {@code 'f'}。</li>
 *   <li>{@code math.atan == math.atan2} 参考是 <b>true</b>：C 把同一个
 *       {@code math_atan} 挂在两个名字下，而 upvalue 数为 0 的 C 函数是
 *       <b>轻 C 函数</b>（{@code setfvalue}，值就是函数指针），两个名字存的是
 *       同一个值。以前 {@code setFn} 每次新建一层包装，两份就不相等了。</li>
 *   <li>文件句柄元表的 {@code __gc} 与 {@code __close} 同理，参考实现里
 *       也是同一个值。</li>
 *   <li>{@code coroutine.wrap} 给<b>内存错误</b>也补了位置前缀。
 *       {@code lcorolib.c: luaB_auxwrap} 的判据是
 *       {@code stat != LUA_ERRMEM && lua_type(L,-1) == LUA_TSTRING}，
 *       而且 {@code stat} 取的是 {@code lua_closethread} 的<b>返回值</b>
 *       （{@code __close} 抛了错就变 {@code LUA_ERRRUN}，那种是要补的）。
 *       这是<b>协程第二遍</b>咬出来的：{@code f:read(-1)} 在主线程上怎么跑
 *       都对，塞进 {@code coroutine.wrap} 就多出 {@code case.lua:40: }。</li>
 * </ol>
 * <p>
 * 除了协程状态机那一组（它自己就在摆弄协程，再包一层语义就变了），
 * 片一每组都跑了<b>协程第二遍</b>（约定 #9.4）；第 5 条就是这么来的。
 * <p>
 * <b>有一档没法比，只能剔除</b>：{@code math.atan}/{@code math.atan2} 在
 * 「推不出名字、要走 {@code findfield} 按值反查」的写法下是<b>掷硬币</b> ——
 * 反查先撞到哪个全看表的遍历顺序，而 Lua 的字符串哈希<b>每次进程换种子</b>
 * （{@code luai_makeseed} 用时间+地址）。同一个脚本连跑五次，参考给出四
 * {@code atan2} 一 {@code atan}。所以这两个名字只剔除<b>回退档</b>，
 * 其余写法是确定的，照铺。
 */
class LimitGridTest {

    @BeforeAll
    static void prepare() {
        new File(Diff.SCRATCH).mkdirs();
    }

    /**
     * 片一：各库的上限判据与边界（约 900 行）。
     * <p>
     * 用 {@code assertFullMatches}：{@code debug.traceback} 那两档的输出里
     * <b>本身就有</b>{@code stack traceback:}，走 {@code assertMatches} 会被
     * 从那一行砍掉（约定 #33），后面几档一条也测不到。
     */
    @Test
    void upperBoundErrorsMatchReference() {
        Diff.assertFullMatches("""
-- 上限与边界类报错的网格
-- 为什么开这一片：刚发现覆盖率对「调用一个必定抛错的辅助函数」这种形状恒判未覆盖
-- （31/31），所以「哪条报错没被测」根本问不出来。改成正面铺：把各库的上限判据
-- （MAXSIZE / MAXINTEGER / 越界 / 非法格式）挨个撞一遍，跟参考实现比。
-- 注意：所有会真去分配巨量内存的档位一律不铺（Java 会 OOM，C 给 not enough memory，
-- 属于有意偏离）。只铺「判据先拦下、根本没分配」的那些。
-- 遍历一律 ipairs：pairs 顺序不可比（第 43 条）。

local function s(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

local function go(tag, f)
    local r = table.pack(pcall(f))
    local out = { tag, tostring(r[1]) }
    for i = 2, r.n do out[#out + 1] = s(r[i]) end
    print(table.concat(out, " "))
end


--[[
  协程第二遍（约定 #9.4）：除了「协程状态机」那一组（它自己就在摆弄协程，
  再包一层语义就变了），其余每组都塞进 coroutine.wrap 再跑一遍。
  盯的是「同一条报错在协程里位置/名字会不会变」。
]]
local NOCO = { "^yield", "^resume", "^close" }
local function isCo(tag)
    for _, p in ipairs(NOCO) do if tag:find(p) == 1 then return false end end
    return true
end
local function go2(tag, f)
    go(tag, f)
    if isCo(tag) then
        go("co." .. tag, function() return coroutine.wrap(f)() end)
    end
end

print("BEGIN")

-- ① string.rep 的上限。判据是 l + lsep > MAXSIZE / n（MAXSIZE 是无符号 2^64-1），
--    只有这一档能在分配之前拦下来；l+lsep 小的是真去分配，不铺。
local REPN = { { "0", 0 }, { "-1", -1 }, { "min", math.mininteger },
               { "max", math.maxinteger }, { "2^62", 2 ^ 62 }, { "2^63", 2 ^ 63 } }
local REPS = { { "", "" }, { "a", "a" }, { "ab", "ab" }, { "abcd", "abcd" }, { "abcde", "abcde" } }
local REPSEP = { { "", "" }, { "b", "b" }, { "bcd", "bcd" } }
for _, e1 in ipairs(REPN) do
    local nk, n = e1[1], e1[2]
    local big = (n == math.maxinteger or n == 2 ^ 62 or n == 2 ^ 63)
    for _, e2 in ipairs(REPS) do
        local sk, ss = e2[1], e2[2]
        for _, e3 in ipairs(REPSEP) do
            local ek, sep = e3[1], e3[2]
            -- 巨 n 只留 l+lsep>=5 的：MAXSIZE/n<=1，判据必拦下，不会去分配
            if (not big) or (#ss + #sep >= 5) then
                go("rep." .. nk .. "." .. sk .. "." .. ek,
                   function() return #string.rep(ss, n, sep) end)
            end
        end
    end
end
-- rep 的错参数
go2("rep.noarg", function() return string.rep() end)
go2("rep.nonn", function() return string.rep("a", "3") end)
go2("rep.niln", function() return string.rep("a", nil) end)
go2("rep.float", function() return string.rep("a", 2.5) end)
go2("rep.floatok", function() return string.rep("ab", 2.0) end)
go2("rep.num", function() return string.rep(12, 2) end)

-- ② utf8 的边界
local US = { { "", "" }, { "a", "a" }, { "acc", "ábc" }, { "bad", "\\xff\\xfe" },
             { "mix", "a\\195\\169b" } }
local IDX = { -2, -1, 0, 1, 2, 4, 9 }
local JDX = { -2, -1, 1, 4, 9 }
local ODX = { -2, -1, 0, 1, 2 }
local OIX = { -1, 0, 1, 2, 3, 9 }
for _, e in ipairs(US) do
    local k, str = e[1], e[2]
    for _, i in ipairs(IDX) do
        for _, j in ipairs(JDX) do
            go("ulen." .. k .. "." .. i .. "." .. j,
               function() return utf8.len(str, i, j) end)
            go("ulenlax." .. k .. "." .. i .. "." .. j,
               function() return utf8.len(str, i, j, true) end)
            go("ucp." .. k .. "." .. i .. "." .. j,
               function() return utf8.codepoint(str, i, j) end)
        end
    end
    for _, off in ipairs(ODX) do
        for _, i in ipairs(OIX) do
            go("uoff." .. k .. "." .. off .. "." .. i,
               function() return utf8.offset(str, off, i) end)
        end
    end
end
local CHARS = { 0, 0x7F, 0x80, 0x7FF, 0x800, 0xFFFF, 0x10000, 0x10FFFF, 0x110000,
                -1, 0x7FFFFFFF, 2 ^ 31, 2 ^ 62 }
for _, v in ipairs(CHARS) do
    go("uchar.i" .. tostring(v), function() return #utf8.char(v) end)
end
go2("uchar.f1.5", function() return #utf8.char(1.5) end)
go2("uchar.f2", function() return #utf8.char(2.0) end)
go2("uchar.s65", function() return #utf8.char("65") end)
go2("uchar.sabc", function() return #utf8.char("abc") end)
go2("uchar.bool", function() return #utf8.char(true) end)
go2("uchar.tbl", function() return #utf8.char({}) end)
go2("uchar.nil", function() return #utf8.char(nil) end)
go2("uchar.many", function() return #utf8.char(65, 66, 67) end)
go2("ucp.none", function() return utf8.codepoint("abc") end)
go2("ucp.none2", function() return utf8.codepoint("abc", 2) end)
go2("ucp.nonstr", function() return utf8.codepoint(123, 1) end)


-- ③ table 的上限与错参
local T = { 1, 2, 3, 4, 5 }
go2("move.neg", function() return table.move(T, math.mininteger, -1, 1) end)
go2("move.wrap", function() return table.move(T, 1, 10, math.maxinteger) end)
go2("move.wrap2", function() return table.move(T, 1, 3, math.maxinteger - 1) end)
go2("move.f1", function() return table.move(T, 1.5, 2, 1) end)
go2("move.f2", function() return table.move(T, 1, 2.5, 1) end)
go2("move.f3", function() return table.move(T, 1, 3, 1.5) end)
go2("move.f4", function() return table.move(T, 1, 3, 2.0) end)
go2("move.a1", function() return table.move(T, 1) end)
go2("move.nonstr", function() return table.move(1, 1, 2, 1) end)
go2("unpack.neg", function() return table.unpack({}, math.mininteger, -1) end)
go2("unpack.huge", function() return table.unpack({}, 1, math.maxinteger) end)
go2("unpack.huge2", function() return table.unpack({}, math.mininteger, math.maxinteger) end)
go2("unpack.f1", function() return table.unpack({}, 1.5, 2) end)
go2("unpack.f2", function() return table.unpack({}, 1, 2.5) end)
go2("unpack.ok", function() return table.unpack({ 1, 2, 3 }, 1.0, 3.0) end)
go2("unpack.nonstr", function() return table.unpack(1, 1, 2) end)
go2("insert.pos0", function() return table.insert({}, 0, 1) end)
go2("insert.pos2", function() return table.insert({}, 2, 1) end)
go2("insert.pos-1", function() return table.insert({}, -1, 1) end)
go2("insert.posmax", function() return table.insert({}, math.maxinteger, 1) end)
go2("insert.4", function() return table.insert({}, 1, 2, 3) end)
go2("insert.f", function() return table.insert({}, 1.5, 1) end)
go2("insert.f2", function() return table.insert({}, 2.0, 1) end)
go2("remove.pos0", function() return table.remove({}, 0) end)
go2("remove.pos2", function() return table.remove({}, 2) end)
go2("remove.posmax", function() return table.remove({}, math.maxinteger) end)
go2("remove.f", function() return table.remove({}, 1.5) end)
go2("remove.ok", function() return table.remove({ 7, 8 }, 2.0) end)
-- __len 不是整数：table.concat / insert / remove 都过 luaL_len
local function lenmt(f) return setmetatable({}, { __len = f }) end
go2("concat.len.frac", function() return table.concat(lenmt(function() return 1.5 end)) end)
go2("concat.len.str", function() return table.concat(lenmt(function() return "abc" end)) end)
go2("concat.len.str3", function() return table.concat(lenmt(function() return "3" end)) end)
go2("concat.len.f2", function() return table.concat(lenmt(function() return 2.0 end)) end)
go2("concat.len.bool", function() return table.concat(lenmt(function() return true end)) end)
go2("concat.len.tbl", function() return table.concat(lenmt(function() return {} end)) end)
go2("insert.len.frac", function() return table.insert(lenmt(function() return 1.5 end), 9) end)
go2("remove.len.frac", function() return table.remove(lenmt(function() return 1.5 end)) end)
go2("sort.nonstr", function() return table.sort(1) end)
go2("sort.badcmp", function() return table.sort({ 3, 1, 2 }, 1) end)

-- ④ 协程的状态机
go2("yield.main", function() return coroutine.yield(1) end)
local dead = coroutine.create(function() return 1 end)
coroutine.resume(dead)
go2("resume.dead", function() return coroutine.resume(dead) end)
local err = coroutine.create(function() error("x") end)
coroutine.resume(err)
go2("resume.dead2", function() return coroutine.resume(err) end)
local self1
self1 = coroutine.create(function() return coroutine.resume(self1) end)
go2("resume.self", function() return coroutine.resume(self1) end)
local self2
self2 = coroutine.create(function()
    table.sort({ 3, 1, 2 }, function(a, b) return select(2, coroutine.resume(self2)) and a < b or a < b end)
    return "after"
end)
go2("resume.normal", function() return coroutine.resume(self2) end)
go2("close.main", function() return coroutine.close(coroutine.running()) end)
go2("close.num", function() return coroutine.close(1) end)
local co4 = coroutine.create(function() coroutine.yield() end)
coroutine.resume(co4)
go2("close.ok", function() return coroutine.close(co4) end)
go2("close.dead", function() return coroutine.close(co4) end)

-- ⑤ 基础库：select / rawlen / 受保护的元表
go2("select.2", function() return select(2) end)
go2("select.0", function() return select(0) end)
go2("select.0b", function() return select(0, 1) end)
go2("select.-1", function() return select(-1) end)
go2("select.-1b", function() return select(-1, 7) end)
go2("select.1", function() return select(1, 10) end)
go2("select.hash", function() return select("#", 1, 2, 3) end)
go2("select.hash0", function() return select("#") end)
go2("select.f", function() return select(1.5, 1) end)
go2("select.f2", function() return select(2.0, 1, 2) end)
go2("rawlen.num", function() return rawlen(1) end)
go2("rawlen.bool", function() return rawlen(true) end)
go2("rawlen.str", function() return rawlen("abc") end)
go2("rawlen.tbl", function() return rawlen({ 1, 2 }) end)
go2("rawlen.ud", function() return rawlen(io.stdout) end)
local prot = setmetatable({}, { __metatable = "protected" })
go2("setmt.prot", function() return setmetatable(prot, {}) end)
go2("setmt.prot2", function() return setmetatable(prot, nil) end)
go2("getmt.prot", function() return getmetatable(prot) end)
go2("setmt.num", function() return setmetatable({}, 1) end)
go2("setmt.num1", function() return setmetatable(1, {}) end)
go2("setmt.str", function() return setmetatable("x", {}) end)
go2("rawequal.mt", function() return rawequal(1, 1) end)
go2("rawget.num", function() return rawget(1, 1) end)
go2("rawset.num", function() return rawset(1, 1, 1) end)
go2("next.nonstr", function() return next(1) end)

-- ⑥ string.pack 的畸形格式与越界值
local PK = {
    { "z", "a\\0b" }, { "z", "ab" }, { "s", "a\\0b" }, { "s1", "ab" }, { "s4", "abc" },
    { "c0", "" }, { "c", "" }, { "c3", "ab" }, { "c2", "abc" },
    { "i", 1.5 }, { "i0", 1 }, { "i17", 1 }, { "i1", 1 }, { "i16", 1 },
    { "b", 300 }, { "B", -1 }, { "h", 1e100 }, { "!", 1 }, { "x", nil },
    { "j", 2 ^ 62 }, { "j", -2 ^ 62 }, { "I", 2 ^ 63 }, { "i", -2 ^ 63 },
    { "f", "x" }, { "d", true }, { "s", 1 }, { "c2", 1 }, { "z", 1 },
    { "n", 1 }, { "T", nil }, { "i", true }, { "b", "5" },
}
for _, e in ipairs(PK) do
    go("pack." .. e[1] .. "." .. tostring(e[2]),
       function() return (string.pack(e[1], e[2])) end)
end
go2("pack.none", function() return (string.pack()) end)
go2("pack.nofmt", function() return (string.pack(nil, 1)) end)
go2("unpack.short", function() return string.unpack("i", "x") end)
go2("unpack.short2", function() return string.unpack("i8", "xy") end)
go2("unpack.bad", function() return string.unpack("!", "x") end)
go2("unpack.z", function() return string.unpack("z", "ab") end)
go2("unpack.c", function() return string.unpack("c3", "ab") end)
go2("unpack.nofmt", function() return string.unpack(nil, "x") end)
go2("packsize.bad", function() return string.packsize("!") end)
go2("packsize.none", function() return string.packsize() end)

-- ⑦ io 的非法格式与模式（只撞开不成功的那几档，不落真实文件）
go2("open.mode", function() return io.open("$RT/nope.txt", "q") end)
go2("open.mode2", function() return io.open("$RT/nope.txt", "rw+") end)
go2("open.mode3", function() return io.open("$RT/nope.txt", "") end)
go2("open.mode4", function() return io.open("$RT/nope.txt", "rb+") end)
go2("open.mode5", function() return io.open("$RT/nope.txt", 1) end)
local tmp = "$RT/lim_in.txt"
os.remove(tmp)
local w = assert(io.open(tmp, "w"))
w:write("1 2\\nthree\\n")
w:close()
local f = assert(io.open(tmp, "r"))
go2("read.fmt", function() return f:read("q") end)
go2("read.fmt2", function() return f:read("a", "l", "q") end)
go2("read.fmt3", function() return f:read(1, "q") end)
go2("read.neg", function() return f:read(-1) end)
go2("read.zero", function() return f:read(0) end)
go2("read.many", function() local r = { pcall(f.read, f, "l", "l", "l", "l") } return r[1] end)
f:close()
os.remove(tmp)

-- ⑧ debug 的越界层号与 upvalue 下标
go2("getlocal.lvl", function() return debug.getlocal(100, 1) end)
go2("getlocal.lvl0", function() return debug.getlocal(0, 1) end)
go2("getlocal.lvlneg", function() return debug.getlocal(-1, 1) end)
go2("setlocal.lvl", function() return debug.setlocal(100, 1, 1) end)
go2("getinfo.lvl", function() return debug.getinfo(100) end)
go2("getinfo.lvl2", function() return debug.getinfo(100, "n") end)
go2("getinfo.0", function() return (debug.getinfo(0, "n")).name end)
go2("setupvalue.idx", function() return debug.setupvalue(print, 99, 1) end)
go2("setupvalue.idx0", function() return debug.setupvalue(print, 0, 1) end)
go2("getupvalue.idx", function() return debug.getupvalue(print, 0) end)
go2("getupvalue.idx2", function() return debug.getupvalue(print, 99) end)
go2("getupvalue.num", function() return debug.getupvalue(1, 1) end)
go2("traceback.lvl", function() return debug.traceback("m", 100) end)
go2("traceback.lvl0", function() return (debug.traceback("m", 0)):sub(1, 1) end)
go2("sethook.bad", function() return debug.sethook(1, "crl") end)
go2("sethook.badm", function() return debug.sethook(function() end, "z") end)
go2("sethook.cnt", function() return debug.sethook(function() end, "", 0) end)



--[[
  ⑧之二：内存错误穿过协程边界。
  内存错误（LUA_ERRMEM）在任何边界都<b>不补位置、不过错误处理函数</b>（#108）。
  协程这边有三处会碰到它：wrap 的重新抛出（lcorolib 里 stat != LUA_ERRMEM 那半句）、
  resume 的返回值、coroutine.close 的返回值。以前 wrap 那条漏了，
  f:read(-1) 在协程里被套上了位置前缀 —— 就是这一组抓到的。
]]
local function memf(lvl) return function() error("not enough memory", lvl) end end
for _, lvl in ipairs { 0, 1, 2 } do
    go2("mem.wrap." .. lvl, function()
        return coroutine.wrap(memf(lvl))()
    end)
    go2("mem.resume." .. lvl, function()
        local co = coroutine.create(memf(lvl))
        return select(2, coroutine.resume(co))
    end)
    go2("mem.after." .. lvl, function()
        local co = coroutine.create(memf(lvl))
        local ok, e = coroutine.resume(co)
        return ok, e, coroutine.status(co)
    end)
    go2("mem.closed." .. lvl, function()
        local co = coroutine.create(memf(lvl))
        coroutine.resume(co)
        return coroutine.close(co)
    end)
    go2("mem.xpcall." .. lvl, function()
        return coroutine.wrap(function()
            return xpcall(memf(lvl), function(m) return "H:" .. tostring(m) end)
        end)()
    end)
end
-- 协程因内存错误死掉、同时有 <close> 变量：__close 抛的错会顶掉内存错误
go2("mem.tbc", function()
    local co = coroutine.create(function()
        local x <close> = setmetatable({}, { __close = function(_, err) return err end })
        error("not enough memory", 0)
    end)
    return select(2, coroutine.resume(co))
end)
go2("mem.tbcerr", function()
    local co = coroutine.create(function()
        local x <close> = setmetatable({}, { __close = function(_, err) error("inclose") end })
        error("not enough memory", 0)
    end)
    return select(2, coroutine.resume(co))
end)
go2("mem.wraptbcerr", function()
    return coroutine.wrap(function()
        local x <close> = setmetatable({}, { __close = function(_, err) error("inclose") end })
        error("not enough memory", 0)
    end)()
end)
-- 真去撞一次内存错误：f:read(-1)（C 里是 resizebox 失败）
local tmp2 = "$RT/lim_in2.txt"
os.remove(tmp2)
local w2 = assert(io.open(tmp2, "w"))
w2:write("abc")
w2:close()
go2("mem.read.wrap", function()
    local f2 = assert(io.open(tmp2, "r"))
    return coroutine.wrap(function() return f2:read(-1) end)()
end)
go2("mem.read.resume", function()
    local f2 = assert(io.open(tmp2, "r"))
    local co = coroutine.create(function() return f2:read(-1) end)
    return select(2, coroutine.resume(co))
end)
go2("mem.read.wrapclose", function()
    local f2 = assert(io.open(tmp2, "r"))
    return coroutine.wrap(function()
        local x <close> = setmetatable({}, { __close = function() return end })
        return f2:read(-1)
    end)()
end)
os.remove(tmp2)

                """);
    }

    /** 片二：报错里的函数名 × 全库函数 × 八种写法 × 两组装参（约 1700 行）。 */
    @Test
    void functionNameInErrorMessageMatchesReference() {
        Diff.assertMatches("""
-- 报错里的函数名 × 全库函数 × 六种调用写法
-- 为什么开这一片：coroutine 那六个函数把 'coroutine.X' 写死在报错里，咬出来之后
-- 顺手发现 tonumber 也写死了一份。手挑两条、改两条没用——这类「名字从调用者那一
-- 帧反推」的机制，全库每个函数都走，得一张网全铺住。
-- 函数名来自 ldebug.c: funcnamefromcall(ci->previous)：
--   字面常量键 → 键名；局部别名 → 局部变量名；推不出来 → pushglobalfuncname 的全名。

local function s(v)
    local t = type(v)
    if t == "string" then return "s[" .. v .. "]" end
    if t == "number" then
        if math.type(v) == "integer" then return "i" .. tostring(v) end
        return "f" .. tostring(v)
    end
    return t
end

local function go(tag, f)
    local r = table.pack(pcall(f))
    local out = { tag, tostring(r[1]) }
    -- 只留第一条：后面的返回值里可能有地址、表等
    if r.n >= 2 then out[#out + 1] = s(r[2]) end
    print(table.concat(out, " "))
end

print("BEGIN")

-- 排除：副作用不可控或返回值不可比的
local SKIP = {
    ["os.clock"] = 1,        -- 返回 CPU 时间，两边不可能一样
    ["os.exit"] = 1,         -- 直接退出进程
    ["debug.setmetatable"] = 1, -- 改的是全局类型元表，会污染后面所有用例
    ["io.output"] = 1, ["io.input"] = 1, -- 换掉标准流
    ["io.close"] = 1,        -- io.close() 会关掉默认输出
    ["os.setlocale"] = 1,    -- 全局 locale
    ["math.randomseed"] = 1, -- 改随机种子
    ["debug.sethook"] = 1,   -- 装钩子
    ["debug.debug"] = 1,     -- 进交互式 REPL，往 stdout 打 lua_debug> 提示符、读 stdin
    --[[
      math.atan2 与 math.atan 在参考实现里是<b>同一个函数</b>（C 把同一个
      math_atan 挂在两个名字下），所以 luaL_argerror 那个回退名字
      （findfield 在 _LOADED 里按值反查）查到的是 atan 还是 atan2，
      取决于表的哈希遍历顺序 —— 而 Lua 的字符串哈希<b>每次进程都换种子</b>
      （luai_makeseed 用时间+地址），所以参考实现自己每次跑的结果都不一定一样。
      同一脚本连跑五次，参考给出四 atan2 一 atan。这一档没法比，只能剔除。
    ]]
}
--[[
  这两个名字的回退档（推不出名字、要走 findfield 的那些写法）是<b>掷硬币</b>：
  math.atan 与 math.atan2 在参考实现里是同一个函数，findfield 按值反查先撞到哪个
  全看哈希顺序，而 Lua 的字符串哈希每次进程换种子（luai_makeseed 用时间+地址）。
  同一个脚本连跑五次，参考给出四 atan2 一 atan。所以只剔除这两个名字的
  <b>回退档</b>（alias/tbl/nested），其余写法（field/pcall/sel/gidx）是确定的，照铺。
]]
local NOFALLBACK = { ["math.atan"] = 1, ["math.atan2"] = 1 }
local FALLBACKFORM = { alias = 1, tbl = 1, nested = 1 }

-- 枚举：_G 的全局 + 各库表的成员，拼成点分全名；排序保证确定性
local names = {}
local function addfn(prefix, t, k, v)
    if type(v) ~= "function" then return end
    local nm = prefix .. k
    if not SKIP[nm] then names[#names + 1] = nm end
end
local LIBS = { "string", "table", "math", "io", "os", "debug", "coroutine", "utf8", "package" }
do
    local gkeys = {}
    for k in pairs(_G) do gkeys[#gkeys + 1] = k end
    table.sort(gkeys)
    for _, k in ipairs(gkeys) do addfn("", _G, k, _G[k]) end
    local lkeys = {}
    for _, ln in ipairs(LIBS) do lkeys[#lkeys + 1] = ln end
    table.sort(lkeys)
    for _, ln in ipairs(lkeys) do
        local t = _G[ln]
        if type(t) == "table" then
            local ks = {}
            for k in pairs(t) do ks[#ks + 1] = k end
            table.sort(ks)
            for _, k in ipairs(ks) do addfn(ln .. ".", t, k, t[k]) end
        end
    end
end
table.sort(names)
print("函数个数 " .. #names)
-- 同一函数挂两个名字：参考实现里这两个是同一个值（无 upvalue 的 C 函数是轻 C 函数，
-- 值就是函数指针），与上面的哈希顺序无关，是确定性的
print("atan.id", math.atan == math.atan2)
print("gcclose.id", debug.getmetatable(io.stdout).__gc == debug.getmetatable(io.stdout).__close)

--[[
  两组装参：
    t = 传一个空表 —— 撞「类型不对」那一档（luaL_checktype / argexpected）；
    n = 一个都不传 —— 撞 luaL_checkany 的 "value expected" 与 "got no value"。
    第二组专门盯 tonumber 那类：只有不传参才走得到 checkany。
]]
local SKIP_N = {
    ["os.time"] = 1, ["os.date"] = 1, -- 返回当前时间，两边不可能一样
    ["math.random"] = 1,              -- 随机数
    ["io.read"] = 1, ["io.lines"] = 1, -- 会去读 stdin
    ["os.execute"] = 1,               -- 真去起进程
    ["collectgarbage"] = 1,           -- 有意偏离（约定 #57）
}
local ARGS = { { "t", "({})", SKIP }, { "n", "()", SKIP_N } }
local FORMS = {
    { "field", "%s" },
    { "alias", "(function() local f = %s return f end)()" },
    { "tbl", "(function() local t = { g = %s } return t.g end)()" },
    { "pcall", "pcall(%s" },
    { "nested", "(function() return %s end)()" },
    { "sel", "select(1, %s" },
    { "idx", nil }, -- 常量键取成员：a["b"] / _G["print"]
}
for _, ag in ipairs(ARGS) do
    for _, nm in ipairs(names) do
        if not ag[3][nm] then
            for _, fm in ipairs(FORMS) do
                if not (NOFALLBACK[nm] and FALLBACKFORM[fm[1]]) then
                local body = nm
                if fm[1] == "idx" then
                    local i = nm:find("%.[^.]+$")
                    body = i and string.format("%s[%q]", nm:sub(1, i - 1), nm:sub(i + 1))
                             or string.format("_G[%q]", nm)
                end
                local src = (fm[2] and string.format(fm[2], body) or body) .. ag[2]
                if fm[1] == "pcall" or fm[1] == "sel" then src = src .. ")" end
                go("nm." .. nm .. "." .. ag[1] .. "." .. fm[1], function()
                    local f = assert(load(src, "=L"))
                    return f()
                end)
                --[[
                  协程第二遍（约定 #9.4）：同一批语料塞进 coroutine.wrap 再跑。
                  取名要看 ci->previous，而协程里「上一帧」跟主线程不一样；
                  位置前缀走 luaL_where(L,1) 也不一样。两边外层用 wrap 包一层
                  就能同时盯住这两种差别。
                ]]
                go("co." .. nm .. "." .. ag[1] .. "." .. fm[1], function()
                    local w = coroutine.wrap(function()
                        return assert(load(src, "=L"))()
                    end)
                    return w()
                end)
                end
            end
        end
    end
end

print("END")

                """);
    }

    private LimitGridTest() {
    }
}
