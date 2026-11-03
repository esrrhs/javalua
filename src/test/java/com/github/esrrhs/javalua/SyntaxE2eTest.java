package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 语法 / 语言特性端到端差分闸门。
 * <p>
 * 与 {@link RuntimeTest} / {@link ClosureTest} 同一套 {@link Diff} 驱动：同一段
 * 脚本在本实现与参考 lua5.4 上各跑一遍，stdout 逐字节比对。本类按语言表面
 * 分块堆用例，专门压语法与运行时语义；标准库深度矩阵、GC / 弱表 / {@code __gc}
 * / {@code collectgarbage} 刻意不放这里（GC 相关见 {@link GcTest} 等）。
 */
class SyntaxE2eTest {

    @BeforeAll
    static void requireReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN. found: " + ReferenceLua.describe());
    }

    static List<String> scripts() {
        List<String> s = new ArrayList<>();

        // ---- 字面量 ----
        s.add("print(nil, true, false)");
        s.add("print(0, -0, 1, -1, 42, 0x10, 0Xff)");
        s.add("print(1.0, .5, 5., 1e3, 1E-2, 0x1.8p4, 0X1P-1)");
        s.add("print('sq', \"dq\", [[long]], [=[nested]=], [==[eq]==])");
        s.add("print('a\\nb', 'c\\td', 'e\\\\f', '\\97', '\\x41', '\\u{1F4A9}')");
        s.add("print('' .. '', #'', #('ab' .. 'cd'))");
        s.add("print(type(1), type(1.0), type('1'), type(true), type(nil), type({}), type(print))");
        s.add("print(math.type(1), math.type(1.0), math.type(1e20), math.type(-0.0))");

        // ---- 局部变量与赋值 ----
        s.add("local a = 1 print(a)");
        s.add("local a, b, c = 1, 2, 3 print(a, b, c)");
        s.add("local a, b, c = 1 print(a, b, c)");
        s.add("local a, b = 1, 2, 3 print(a, b)");
        s.add("local a, b = 1, 2 a, b = b, a print(a, b)");
        s.add("local a = 1 do local a = 2 print(a) end print(a)");
        s.add("local a <const> = 10 print(a)");
        s.add("local x = 1; x = x + 1; print(x)");
        s.add("g = 7 print(g) g = nil print(g)");
        s.add("local t = {} t.x, t.y = 1, 2 print(t.x, t.y)");
        s.add("local t = {1, 2} t[1], t[2] = t[2], t[1] print(t[1], t[2])");
        s.add("local i = 1 local t = {} t[i], i = 'v', 2 print(t[1], i)");
        s.add("local function two() return 10, 20 end local a, b, c = two() print(a, b, c)");
        s.add("local function two() return 10, 20 end local a = two() print(a)");
        s.add("local a, b = (function() return 1, 2 end)() print(a, b)");

        // ---- 算术 / 关系 / 逻辑 / 位运算 / 连接 / 长度 ----
        s.add("print(1+2, 3-1, 2*3, 7/2, 7//2, 7%3, 2^10)");
        s.add("print(-7//2, -7%3, 7//-2, 7%-3, -7%-3)");
        s.add("print(1.5+2.5, 5.0//2, 5.5%2)");
        s.add("print(-(5), -(-3))");
        s.add("print(1 < 2, 2 <= 2, 2 > 1, 1 >= 2, 1 == 1, 1 ~= 2)");
        s.add("print(1 == 1.0, '1' == 1, 0/0 == 0/0, 1/0 == math.huge)");
        s.add("print('a' < 'b', 'ab' < 'ac', 'Z' < 'a')");
        s.add("print(not nil, not false, not 0, not '', not true)");
        s.add("print(1 and 2, nil and 2, false and 3, 0 and 'x')");
        s.add("print(nil or 2, false or 3, 0 or 'x', true or 9)");
        s.add("print('a' and 'b' or 'c', false and 'b' or 'c')");
        s.add("local n = 0; print((function() n = n + 1 return false end)() and (function() n = n + 10 return true end)(), n)");
        s.add("local n = 0; print((function() n = n + 1 return true end)() or (function() n = n + 10 return false end)(), n)");
        s.add("print(5 & 3, 5 | 3, 5 ~ 3, ~0, ~(-1), 1 << 4, 16 >> 2, -8 >> 1)");
        s.add("print('a' .. 'b', 1 .. 2, 1.5 .. 'x')");
        s.add("print(pcall(function() return 'y' .. true end))");
        s.add("print(#(''), #('abc'), #({1,2,3}), #({}))");
        s.add("print(('10') + 1, ('10') + 1.5, -('7'))");

        // ---- 控制流：if / while / repeat / for / break / goto ----
        s.add("local x = 5 if x > 3 then print('big') end");
        s.add("local x = 2 if x > 3 then print('big') else print('small') end");
        s.add("local x = 2 if x > 3 then print('big') elseif x > 1 then print('mid') else print('small') end");
        s.add("local x = 0 if x then print('t') else print('f') end");
        s.add("if false then print('a') elseif nil then print('b') elseif 0 then print('c') else print('d') end");
        s.add("local i = 0 while i < 4 do i = i + 1 end print(i)");
        s.add("local i = 0 while false do i = 1 end print(i)");
        s.add("local i = 0 repeat i = i + 1 until i >= 3 print(i)");
        s.add("local i = 0 repeat i = i + 1 until true print(i)");
        s.add("local s = 0 for i = 1, 10 do s = s + i end print(s)");
        s.add("local s = 0 for i = 10, 1, -1 do s = s + i end print(s)");
        s.add("local s = 0 for i = 1, 10, 2 do s = s + i end print(s)");
        s.add("local s = 0 for i = 1.0, 2.5, 0.5 do s = s + 1 end print(s)");
        s.add("for i = 1, 0 do print('never') end print('done')");
        s.add("for i = 1, 5, 0 do print('never') end print('done')");
        s.add("local t = {'a','b','c'} local r = '' for i, v in ipairs(t) do r = r .. i .. v end print(r)");
        s.add("local t = {x=1, y=2} local n = 0 for k, v in pairs(t) do n = n + v end print(n)");
        s.add("local n = 0 while true do n = n + 1 if n >= 3 then break end end print(n)");
        s.add("local s = 0 for i = 1, 10 do if i == 5 then break end s = s + i end print(s)");
        s.add("local s = 0 for i = 1, 5 do for j = 1, 5 do if j == 3 then break end s = s + 1 end end print(s)");
        s.add("local i = 0 ::again:: i = i + 1 if i < 3 then goto again end print(i)");
        s.add("for i = 1, 5 do if i == 3 then goto cont end print(i) ::cont:: end print('done')");
        s.add("do goto skip print('no') ::skip:: print('yes') end");
        s.add("local r do for i = 1, 3 do if i == 2 then goto out end end ::out:: r = 'ok' end print(r)");
        s.add("for i = 1, 3 do for j = 1, 3 do if j == 2 then goto nexti end print(i, j) end ::nexti:: end");

        // ---- 函数：局部 / 全局 / 闭包 / 变参 / 尾调用 ----
        s.add("local function f(a, b) return a + b end print(f(1, 2), f(1))");
        s.add("function gadd(a, b) return a + b end print(gadd(3, 4))");
        s.add("local f = function(x) return x * 2 end print(f(21))");
        s.add("local function outer() local x = 10 return function() return x end end print(outer()())");
        s.add("local function counter() local n = 0 return function() n = n + 1 return n end end"
                + " local c = counter() print(c(), c(), c())");
        s.add("local function make() local x = 1 return function() return x end, function(v) x = v end end"
                + " local g, s = make() print(g()) s(9) print(g())");
        s.add("local function f(...) return select('#', ...) end print(f(), f(1), f(1,2,3))");
        s.add("local function f(...) local a, b = ... return a, b end print(f(1, 2, 3))");
        s.add("local function f(a, ...) return a, select('#', ...) end print(f(1), f(1, 2, 3))");
        s.add("local function f(...) return ... end print(f(1, 2, 3))");
        s.add("local function f(...) return (...), select('#', ...) end print(f(1, 2, 3))");
        s.add("local function f(...) local t = {...} return #t, t[1], t[3] end print(f(10, 20, 30))");
        s.add("print((function() return 1, 2, 3 end)())");
        s.add("print((function() return 1, 2, 3 end)(), 9)");
        s.add("local function three() return 1, 2, 3 end print(#{three()}, table.pack(three()).n)");
        s.add("local function rec(n) if n <= 0 then return 0 end return n + rec(n - 1) end print(rec(50))");
        s.add("local function tail(n, acc) if n == 0 then return acc end return tail(n - 1, acc + n) end"
                + " print(tail(500, 0))");
        s.add("local function even(n) if n == 0 then return true end return odd(n - 1) end"
                + " function odd(n) if n == 0 then return false end return even(n - 1) end"
                + " print(even(10), odd(10), even(7))");
        s.add("local function f() for i = 1, 10 do if i == 4 then return i * 10 end end end print(f())");
        s.add("local function f() do do return 'deep' end end end print(f())");
        s.add("local function none() end print(none())");
        s.add("local a, b = (function() end)() print(a, b)");

        // ---- 表：构造 / 索引 / 方法 ----
        s.add("local t = {} print(#t, t[1])");
        s.add("local t = {1, 2, 3} print(#t, t[1], t[3], t[4])");
        s.add("local t = {a = 1, b = 2} print(t.a, t['b'], t.c)");
        s.add("local t = {1, 2, x = 9, 3} print(#t, t[3], t.x)");
        s.add("local k = 'z' local t = {[k] = 1, [1+1] = 2} print(t.z, t[2])");
        s.add("local t = {} t[1.0] = 'a' print(t[1], math.type((next(t))))");
        s.add("local t = {1, nil, 3} print(#t, t[1], t[3])");
        s.add("local t = {} for i = 1, 20 do t[i] = i end print(#t, t[20])");
        s.add("local o = {v = 5} function o:get() return self.v end print(o:get())");
        s.add("local o = {} function o:m(x) return self, x end local a, b = o:m(7) print(a == o, b)");
        s.add("local o = {f = function(self, x) return x + 1 end} print(o:f(10))");
        s.add("local o = setmetatable({}, {__index = {m = function(self) return 42 end}}) print(o:m())");
        s.add("local t = {1, 2, 3} print(table.unpack(t))");
        s.add("local t = table.pack(1, nil, 3) print(t.n, t[1], t[2], t[3])");
        s.add("local t = {10, 20, 30} print(table.concat(t, ','))");
        s.add("local t = {3, 1, 2} table.sort(t) print(table.concat(t, '-'))");
        s.add("local t = {1, 2} table.insert(t, 3) table.insert(t, 1, 0) print(table.concat(t, ','))");
        s.add("local t = {1, 2, 3} print(table.remove(t), table.concat(t, ','))");

        // ---- 字符串与模式（语言 / 标准库表面） ----
        s.add("print(string.len('abc'), string.rep('ab', 3), string.reverse('abc'))");
        s.add("print(string.sub('abcdef', 2, 4), string.sub('abcdef', -2), string.sub('abcdef', 2, -2))");
        s.add("print(string.upper('AbC'), string.lower('AbC'))");
        s.add("print(string.byte('ABC', 1, 3))");
        s.add("print(string.char(65, 66, 67))");
        s.add("print(string.find('hello', 'll'), string.find('hello', 'x'))");
        s.add("print(string.match('abc123', '%d+'))");
        s.add("print(string.gsub('a1b2c3', '%d', 'X'))");
        s.add("print(string.format('%d-%s-%.1f', 1, 'x', 2.5))");
        s.add("print(string.format('%q', 'a\\nb'))");
        s.add("local i = 0 for w in string.gmatch('a b c', '%S+') do i = i + 1 print(w) end print(i)");
        s.add("print(('hello'):sub(1, 2), ('hello'):upper(), ('xy'):rep(2))");

        // ---- 元表 / 元方法（非 GC） ----
        s.add("local mt = {__index = function(_, k) return 'd:' .. k end}"
                + " print(setmetatable({}, mt).foo)");
        s.add("local mt = {__index = {x = 1}} print(setmetatable({}, mt).x)");
        s.add("local log = {} local mt = {__newindex = function(_, k, v) log[#log+1] = k .. '=' .. v end}"
                + " local t = setmetatable({}, mt) t.a = 1 print(table.concat(log, ','), rawget(t, 'a'))");
        s.add("local mt = {__add = function(a, b) return 100 end} print(setmetatable({}, mt) + 1)");
        s.add("local mt = {__sub = function() return 'S' end} print(setmetatable({}, mt) - 1)");
        s.add("local mt = {__mul = function() return 'M' end} print(setmetatable({}, mt) * 2)");
        s.add("local mt = {__div = function() return 'D' end} print(setmetatable({}, mt) / 2)");
        s.add("local mt = {__idiv = function() return 'I' end} print(setmetatable({}, mt) // 2)");
        s.add("local mt = {__mod = function() return 'R' end} print(setmetatable({}, mt) % 2)");
        s.add("local mt = {__pow = function() return 'P' end} print(setmetatable({}, mt) ^ 2)");
        s.add("local mt = {__unm = function() return 'N' end} print(-setmetatable({}, mt))");
        s.add("local mt = {__band = function() return 7 end} print(setmetatable({}, mt) & 1)");
        s.add("local mt = {__bor = function() return 7 end} print(setmetatable({}, mt) | 1)");
        s.add("local mt = {__bxor = function() return 7 end} print(setmetatable({}, mt) ~ 1)");
        s.add("local mt = {__bnot = function() return 7 end} print(~setmetatable({}, mt))");
        s.add("local mt = {__shl = function() return 7 end} print(setmetatable({}, mt) << 1)");
        s.add("local mt = {__shr = function() return 7 end} print(setmetatable({}, mt) >> 1)");
        s.add("local mt = {__concat = function() return 'J' end} print(setmetatable({}, mt) .. 'x')");
        s.add("local mt = {__len = function() return 99 end} print(#setmetatable({}, mt))");
        s.add("local mt = {__eq = function() return true end}"
                + " print(setmetatable({}, mt) == setmetatable({}, mt))");
        s.add("local mt = {__lt = function(_, b) return true end}"
                + " print(setmetatable({}, mt) < setmetatable({}, mt))");
        s.add("local mt = {__le = function() return false end}"
                + " print(setmetatable({}, mt) <= setmetatable({}, mt))");
        s.add("local mt = {__call = function(self, x) return x * 3 end}"
                + " print(setmetatable({}, mt)(7))");
        s.add("local mt = {__tostring = function() return 'CUSTOM' end} print(setmetatable({}, mt))");
        s.add("local mt = {__name = 'Foo'} print(tostring(setmetatable({}, mt)))");
        s.add("local t = setmetatable({}, {__metatable = 'locked'})"
                + " print(getmetatable(t), pcall(setmetatable, t, {}))");
        s.add("local t = {} rawset(t, 'a', 1) print(rawget(t, 'a'), rawlen({1,2}), rawequal(t, t))");
        s.add("local base = {a = 1} local mid = setmetatable({}, {__index = base})"
                + " local top = setmetatable({}, {__index = mid}) print(top.a)");
        s.add("local log = {} local function mk(n) return setmetatable({}, {"
                + "__close = function() log[#log+1] = n end}) end"
                + " do local a <close> = mk(1) local b <close> = mk(2) end"
                + " print(table.concat(log, ','))");

        // ---- 协程 ----
        s.add("local co = coroutine.create(function() return 1, 2 end)"
                + " print(coroutine.status(co), coroutine.resume(co))");
        s.add("local co = coroutine.create(function() coroutine.yield(10) return 20 end)"
                + " print(coroutine.resume(co)) print(coroutine.resume(co)) print(coroutine.status(co))");
        s.add("local f = coroutine.wrap(function(x) local y = coroutine.yield(x + 1) return y * 2 end)"
                + " print(f(3)) print(f(5))");
        s.add("print(coroutine.running() == coroutine.running())");
        s.add("local co = coroutine.create(function() error('boom') end)"
                + " local ok, err = coroutine.resume(co) print(ok, tostring(err):match('boom') ~= nil)");
        s.add("local co = coroutine.create(function()"
                + " print(coroutine.isyieldable()) coroutine.yield(1) end)"
                + " print(coroutine.isyieldable()) print(coroutine.resume(co))");
        s.add("local co = coroutine.create(function()"
                + " for i = 1, 3 do coroutine.yield(i) end end)"
                + " print(coroutine.resume(co)) print(coroutine.resume(co))"
                + " print(coroutine.resume(co)) print(coroutine.resume(co))");
        s.add("local function prod() return coroutine.wrap(function()"
                + " for i = 1, 3 do coroutine.yield(i * i) end end) end"
                + " local p = prod() print(p(), p(), p(), p())");

        // ---- 模块 / require / package（不依赖外部文件） ----
        s.add("print(type(package), type(package.loaded), type(require))");
        s.add("package.preload.syn_e2e_mod = function(name) return {n = name, v = 42} end"
                + " local m = require('syn_e2e_mod') print(m.n, m.v, package.loaded.syn_e2e_mod == m)"
                + " package.loaded.syn_e2e_mod = nil package.preload.syn_e2e_mod = nil");
        s.add("package.preload.syn_e2e_once = function() _G._syn_e2e_n = (_G._syn_e2e_n or 0) + 1"
                + " return {k = _G._syn_e2e_n} end"
                + " local a = require('syn_e2e_once') local b = require('syn_e2e_once')"
                + " print(a.k, b.k, a == b)"
                + " package.loaded.syn_e2e_once = nil package.preload.syn_e2e_once = nil _G._syn_e2e_n = nil");
        s.add("print(pcall(require, 'syn_e2e_missing_module_xyz'))");
        s.add("local ok, mod = pcall(function()"
                + " package.preload.syn_e2e_bad = function() error('badmod') end"
                + " return require('syn_e2e_bad') end)"
                + " print(ok, tostring(mod):match('badmod') ~= nil)"
                + " package.preload.syn_e2e_bad = nil package.loaded.syn_e2e_bad = nil");

        // ---- 错误处理：pcall / xpcall / error / assert ----
        s.add("print(pcall(function() return 1, 2 end))");
        s.add("print(pcall(function() error('boom') end))");
        s.add("print(pcall(function() error('boom', 0) end))");
        s.add("local ok, err = pcall(function() error({code = 7}) end) print(ok, err.code)");
        s.add("print(pcall(function() return 1 + nil end))");
        s.add("print(pcall(function() local t = nil return t.x end))");
        s.add("print(pcall(function() local t = nil t.x = 1 end))");
        s.add("print(pcall(function() return #5 end))");
        s.add("print(pcall(function() return nil .. 'x' end))");
        s.add("print(xpcall(function() error('e') end, function(m) return 'H:' .. tostring(m) end))");
        s.add("print(xpcall(function() return 9 end, function() return 'no' end))");
        s.add("print(pcall(error))");
        s.add("local ok, err = pcall(function() assert(false, 'nope') end) print(ok, err)");
        s.add("print(pcall(assert, true))");
        s.add("print(pcall(pcall, error, 'x'))");
        s.add("local function f() error('deep', 2) end local function g() f() end"
                + " local ok, err = pcall(g) print(ok, tostring(err):match('deep') ~= nil)");
        s.add("print(select('#', 1, 2, 3), select(2, 'a', 'b', 'c'), select(-1, 'a', 'b', 'c'))");
        s.add("print(select('#', nil), select('#', nil, nil))");

        // ---- load / loadfile 表面（语法侧） ----
        s.add("local f, err = load('return 1 + 2') print(f())");
        s.add("local f = load('return a + b', nil, 't', {a = 3, b = 4}) print(f())");
        s.add("local f, err = load('return @') print(f, err ~= nil)");
        s.add("local f = assert(load('local x = ... return x * 2')) print(f(21))");
        s.add("print(pcall(load, 'return function() end', nil, 'b'))");

        // ---- _ENV / 作用域边界 ----
        s.add("local _ENV = {print = print, x = 42} print(x)");
        s.add("local function f() return _G == _ENV end print(f())");
        s.add("do local x = 1 do local x = 2 print(x) end print(x) end");
        s.add("local t = {} for i = 1, 3 do local j = i t[i] = function() return j end end"
                + " print(t[1](), t[2](), t[3]())");
        s.add("local fs = {} for i = 1, 3 do fs[i] = function() return i end end"
                + " print(fs[1](), fs[2](), fs[3]())");

        // ---- 综合：多返回值落点 ----
        s.add("local function three() return 1, 2, 3 end local t = {three()} print(#t, t[1], t[3])");
        s.add("local function three() return 1, 2, 3 end local t = {three(), three()} print(#t, t[1], t[2], t[3])");
        s.add("local function three() return 1, 2, 3 end print(three(), three())");
        s.add("local function three() return 1, 2, 3 end print((three()))");
        s.add("local function three() return 1, 2, 3 end local a, b = three(), 9 print(a, b)");
        s.add("local function id(...) return ... end print(id(id(1, 2), 3))");
        s.add("local function id(...) return ... end local a, b = id(id(1, 2)) print(a, b)");

        // ---- 数值与边界边角 ----
        s.add("print(math.maxinteger + 1 == math.mininteger, math.mininteger - 1 == math.maxinteger)");
        s.add("print(1//0)");
        s.add("print(1.0//0, 1/0, -1/0, 0/0)");
        s.add("print(pcall(function() return 1 % 0 end))");
        s.add("print(pcall(function() return 1 // 0 end))");
        s.add("print(#{1, 2, 3, nil}, #{n = 1}, #{[1]=1,[3]=3})");
        s.add("print(pcall(function() local t = {} t[nil] = 1 end))");
        s.add("print(pcall(function() local t = {} t[0/0] = 1 end))");
        s.add("print(('a'):byte(), string.char(97))");
        s.add("print(pcall(function() return (97):char() end))");
        s.add("print(tonumber('10'), tonumber('0x10'), tonumber('ff', 16), tonumber('1.5'), tonumber('x'))");
        s.add("print(tostring(1), tostring(1.0), tostring(nil), tostring(true), tostring(false))");

        // ---- utf8 语言表面（非 GC） ----
        s.add("print(utf8.len('abc'), utf8.len('你好'))");
        s.add("print(utf8.char(0x4f60, 0x597d))");
        s.add("print(utf8.codepoint('A', 1))");
        s.add("local n = 0 for p, c in utf8.codes('ab') do n = n + c end print(n)");

        // ---- 更多控制流 / 语法组合 ----
        s.add("local s = 0 for i = 1, 5 do if i % 2 == 0 then goto cont end s = s + i ::cont:: end print(s)");
        s.add("local function f(x) if x < 0 then return -x elseif x == 0 then return 0 else return x end end"
                + " print(f(-3), f(0), f(4))");
        s.add("local i, s = 10, 0 repeat s = s + i i = i - 3 until i <= 0 print(s, i)");
        s.add("local t = {[1] = 'a', [2] = 'b', 3, 4} print(#t, t[1], t[2], t[3], t[4])");
        s.add("local o = {v = 1} function o.inc(self) self.v = self.v + 1 end o:inc() o:inc() print(o.v)");
        s.add("local function f(...) return select('#', ...), select(1, ...) end print(f(nil, 2))");
        s.add("local a <close> = nil print(a)");
        s.add("local x <const> = 5 local f = function() return x end print(f())");
        s.add("local f, err = load('local a <const> = 1; a = 2') print(f, err ~= nil, err ~= nil and err:find('const') ~= nil)");
        s.add("local log = {} local ok, err = pcall(function()"
                + " local c <close> = setmetatable({},"
                + " {__close = function() log[#log+1] = 'c' end}) error('x') end)"
                + " print(ok, table.concat(log), tostring(err):match('x') ~= nil)");

        // ---- 运算符优先级与结合性 ----
        s.add("print(1 + 2 * 3, (1 + 2) * 3, 2^3^2, (2^3)^2)");
        s.add("print(1 << 2 + 1, (1 << 2) + 1, 8 >> 1 + 1)");
        s.add("print(not not true, not not nil, #'ab' + 1)");
        s.add("print(1 < 2 and 3 or 4, 1 > 2 and 3 or 4)");
        s.add("print(true and false or true and false)");

        // ---- 泛型 for 自定义迭代器 ----
        s.add("local function range(a, b) local i = a - 1"
                + " return function() i = i + 1 if i <= b then return i end end end"
                + " local s = 0 for v in range(1, 5) do s = s + v end print(s)");
        s.add("local function iter(t) local i = 0"
                + " return function() i = i + 1 local v = t[i] if v then return i, v end end end"
                + " local r = '' for i, v in iter({'a','b'}) do r = r .. i .. v end print(r)");

        // ---- 更多字面量 / 长字符串边界 ----
        s.add("print([===[a]=] ]===])");
        s.add("print([==[abc]==])");
        s.add("print([[line1\nline2]])");
        s.add("print('-- not comment', [[-- still string]])");
        s.add("print(0x0, 0xFFFFFFFF, 0xffffffffffffffff)");
        s.add("print(1e0, 1E0, 1.e1, 1.0e1)");

        // ---- 赋值求值顺序 ----
        s.add("local order = {} local function p(x) order[#order+1] = x return x end"
                + " local t = {} t[p(1)], t[p(2)] = p(3), p(4)"
                + " print(table.concat(order, ','), t[1], t[2])");
        s.add("local order = {} local function p(x) order[#order+1] = x return x end"
                + " local a, b = p(1), p(2) print(table.concat(order, ','), a, b)");

        // ---- 尾调用与多返回 ----
        s.add("local function a(x) return x, x + 1 end local function b(x) return a(x) end print(b(5))");
        s.add("local function a(...) return ... end local function b(...) return a(...) end print(b(1, 2, 3))");

        // ---- coroutine + pcall / 元方法交叉（非 GC） ----
        s.add("local co = coroutine.create(function()"
                + " print(pcall(function() coroutine.yield(1) return 2 end)) end)"
                + " print(coroutine.resume(co)) print(coroutine.resume(co, true))");
        s.add("local mt = {__call = function(_, ...) return coroutine.yield(...) end}"
                + " local o = setmetatable({}, mt)"
                + " local co = coroutine.create(function() print(o(9)) return 8 end)"
                + " print(coroutine.resume(co)) print(coroutine.resume(co, 7))");

        return s;
    }

    @ParameterizedTest(name = "syntax #{index}")
    @MethodSource("scripts")
    void scriptMatchesReference(String src) {
        Diff.assertMatches(src);
    }
}
