package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 运行期差分 —— 阶段 3 的主闸门。
 * <p>
 * 与阶段 1/2 的字节码闸门互补：那边证明「编译出来的东西和参考实现一样」，
 * 这边证明「跑起来的结果和参考实现一样」。同一份脚本分别交给两边，
 * 逐字节比对 stdout；出错时比对第一行错误信息（回溯依赖调试信息的层数，
 * 且程序名前缀是二进制路径，两边必然不同）。
 * <p>
 * 脚本路径固定为 {@code target/rt/case.lua}：参考实现的 {@code luaO_chunkid}
 * 会把过长的源名从左边截成 {@code ...xxx}，用随机临时文件名就没法比对了。
 */
class RuntimeTest {

    @BeforeAll
    static void requireReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN. found: " + ReferenceLua.describe());
    }

    /** 差分用例。每条都会在两边各跑一遍 */
    static List<String> scripts() {
        List<String> s = new ArrayList<>();

        // 输出与类型
        s.add("print(1, 2.0, \"x\", true, nil, false)");
        s.add("print(1.0, 0.0, -0.0, 100.0, 1e15, 1e16, 1e300, 0.1, 1/3)");
        s.add("print(0/0, 1/0, -1/0)");
        s.add("print(type(nil), type(true), type(1), type(1.0), type('a'), type({}), type(print))");
        s.add("print(math.type(1), math.type(1.0), math.type('1'), math.type(nil))");

        // 算术与整数/浮点子类型
        s.add("print(1+2, 3-1, 2*3, 7/2, 7//2, 7%3, 2^10)");
        s.add("print(-7//2, -7%3, 7//-2, 7%-3)");
        s.add("print(1 == 1.0, math.type(1), tostring(1), tostring(1.0))");
        s.add("print(1//0)");

        // 位运算
        s.add("print(5 & 3, 5 | 3, 5 ~ 3, ~0, 1 << 3, -8 >> 2)");

        // 字符串
        s.add("print('a' .. 'b' .. 1 .. 2.0)");
        s.add("print(#('abc'), #(''))");
        s.add("print('abc' < 'abd', 'Z' < 'a', 'a' < 'ab')");
        s.add("local s = '10' print(s + 1, s + 1.0, -s)");

        // 表
        s.add("local t = {} t[1] = 'a' t[2] = 'b' print(#t, t[1], t[2])");
        s.add("local t = {1, 2, 3, x = 'y'} print(#t, t.x, t[3])");
        s.add("local t = {} t[1.0] = 'v' print(t[1], #t)");
        s.add("local t = {} for i = 1, 10 do t[i] = i * i end print(#t, t[10])");

        // 控制流
        s.add("local s = 0 for i = 1, 10 do s = s + i end print(s)");
        s.add("local s = 0 for i = 10, 1, -2 do s = s + i end print(s)");
        s.add("local s = 0 for i = 1.0, 2.0, 0.5 do s = s + 1 end print(s)");
        s.add("for i = 1, 0 do print('never') end print('done')");
        s.add("local i = 0 while i < 5 do i = i + 1 end print(i)");
        s.add("local i = 0 repeat i = i + 1 until i >= 3 print(i)");
        s.add("local t = {'a','b','c'} local r = '' for _, v in ipairs(t) do r = r .. v end print(r)");
        s.add("local t = {a=1, b=2} local n = 0 for k, v in pairs(t) do n = n + v end print(n)");
        s.add("local x = 5 if x > 3 then print('big') elseif x > 1 then print('mid') else print('small') end");
        s.add("for i = 1, 5 do if i == 3 then goto cont end print(i) ::cont:: end");

        // 函数与闭包
        s.add("local function f(a, b) return a + b end print(f(1, 2), f(1))");
        s.add("local function counter() local n = 0 return function() n = n + 1 return n end end"
                + " local c = counter() print(c(), c(), c())");
        s.add("local function outer() local x = 10 return function() return x end,"
                + " function(v) x = v end end local g, s = outer() print(g()) s(99) print(g())");
        s.add("local function fmt(...) local a, b = ... return a, b end print(fmt(1, 2, 3))");
        s.add("local function va(...) return select('#', ...) end print(va(), va(1), va(1,2,3))");
        s.add("print((function() return 1, 2, 3 end)())");
        s.add("local function rec(n) if n <= 0 then return 0 end return n + rec(n - 1) end print(rec(100))");
        s.add("local function tail(n, acc) if n == 0 then return acc end return tail(n - 1, acc + n) end"
                + " print(tail(1000, 0))");

        // 元表
        s.add("local mt = {__index = function(t, k) return 'def:' .. k end}"
                + " local t = setmetatable({}, mt) print(t.x, t.y)");
        s.add("local mt = {__add = function(a, b) return 'added' end}"
                + " local t = setmetatable({}, mt) print(t + 1, 1 + t)");
        s.add("local log = {} local mt = {__newindex = function(t, k, v) log[#log+1] = k end}"
                + " local t = setmetatable({}, mt) t.a = 1 t.b = 2 print(#log, log[1], log[2])");
        s.add("local mt = {__index = {greet = 'hi'}} local t = setmetatable({}, mt) print(t.greet)");
        s.add("local mt = {__eq = function(a, b) return true end}"
                + " local a, b = setmetatable({}, mt), setmetatable({}, mt) print(a == b, a == {})");
        s.add("local mt = {__lt = function(a, b) return true end}"
                + " local a, b = setmetatable({}, mt), setmetatable({}, mt) print(a < b, a <= b)");
        s.add("local mt = {__le = function(a, b) return false end}"
                + " local a, b = setmetatable({}, mt), setmetatable({}, mt) print(a <= b)");
        s.add("local mt = {__len = function() return 42 end} print(#setmetatable({}, mt))");
        s.add("local mt = {__call = function(self, x) return x * 2 end}"
                + " print(setmetatable({}, mt)(21))");
        s.add("local mt = {__tostring = function() return 'CUSTOM' end} print(setmetatable({}, mt))");
        s.add("local t = setmetatable({}, {__metatable = 'locked'})"
                + " print(getmetatable(t)) print(pcall(setmetatable, t, {}))");
        s.add("local a = {} local b = {} print(rawequal(a, b), rawequal(a, a))");
        s.add("local t = {} rawset(t, 1, 'x') print(rawget(t, 1), rawlen(t))");

        // 错误处理
        s.add("print(pcall(function() error('boom') end))");
        s.add("print(pcall(function() error('boom', 0) end))");
        s.add("print(pcall(function() error({code = 7}) end))");
        s.add("print(pcall(function() return 1 + nil end))");
        s.add("print(pcall(function() local t = nil return t.x end))");
        s.add("print(pcall(function() local t = nil t.x = 1 end))");
        s.add("print(pcall(function() return #5 end))");
        s.add("print(pcall(function() return nil .. 'x' end))");
        s.add("print(xpcall(function() error('e') end, function(m) return 'H:' .. m end))");
        s.add("print(pcall(error))");
        s.add("local ok, err = pcall(function() assert(false, 'nope') end) print(ok, err)");
        s.add("print(select('#', 1, 2, 3), select(2, 'a', 'b', 'c'))");

        // 库函数
        s.add("print(tonumber('10'), tonumber('0x10'), tonumber('1.5'), tonumber('abc'))");
        s.add("print(tonumber('ff', 16), tonumber('101', 2), tonumber('z', 36))");
        s.add("print(tostring(1), tostring(nil), tostring(true), tostring(1.5))");
        s.add("local t = {1, 2, 3} print(table.unpack(t))");
        s.add("local t = {'a'} local k, v = next(t) print(k, v, next(t, k))");

        // math
        s.add("print(math.abs(-3), math.abs(-3.5), math.abs(0))");
        s.add("print(math.ceil(2.3), math.ceil(-2.3), math.ceil(3), math.ceil(3.0))");
        s.add("print(math.floor(2.7), math.floor(-2.7), math.floor(3), math.floor(3.0))");
        s.add("print(math.sqrt(16), math.sqrt(2))");
        s.add("print(math.fmod(7, 3), math.fmod(-7, 3), math.fmod(7, -3), math.fmod(7.5, 2))");
        s.add("print(math.max(1, 5, 3), math.min(1, 5, 3), math.max(1.5, 1), math.min(1.5, 1))");
        s.add("print(math.tointeger(3.0), math.tointeger(3.5), math.tointeger('7'),"
                + " math.tointeger(3))");
        s.add("print(math.pi, math.huge, -math.huge, math.huge > 1e300)");
        s.add("print(math.maxinteger + 1 == math.mininteger,"
                + " math.mininteger - 1 == math.maxinteger)");

        // 一元运算与布尔
        s.add("print(not nil, not false, not 0, not '')");
        s.add("print(-'5', -'5.0', #'abcd')");
        s.add("print(1 and 2, nil and 2, false or 3, nil or false)");
        s.add("print('a' and 'b' or 'c')");

        // 表构造与多返回值的各种落点
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {three()} print(#t, t[1], t[2], t[3])");
        s.add("local function three() return 1, 2, 3 end local t = {three(), three()}"
                + " print(#t, t[1], t[2], t[3], t[4])");
        s.add("local function three() return 1, 2, 3 end print(three(), three())");
        s.add("local function three() return 1, 2, 3 end print((three()))");
        s.add("local t = {[1] = 'a', [2] = 'b', 3, 4} print(#t, t[1], t[2], t[3], t[4])");

        // 方法与 self
        s.add("local o = {} function o:m(x) return x * 2 end print(o:m(21))");
        s.add("local o = {v = 7} function o:get() return self.v end print(o:get())");

        // upvalue 共享
        s.add("local x = 0 local function inc() x = x + 1 end"
                + " local function get() return x end inc() inc() print(get(), x)");
        s.add("local t = {} for i = 1, 3 do t[#t+1] = function() return i end end"
                + " print(t[1](), t[2](), t[3]())");

        // 元表的其它入口
        s.add("local mt = {__concat = function(a, b) return 'J' end}"
                + " print(setmetatable({}, mt) .. 'x', 'x' .. setmetatable({}, mt))");
        s.add("local mt = {__unm = function(a) return 'NEG' end} print(-setmetatable({}, mt))");
        s.add("local mt = {__index = function(_, k) error('no key ' .. k) end}"
                + " print(pcall(function() local t = setmetatable({}, mt) return t.zz end))");
        s.add("local base = {a = 1} local mid = setmetatable({}, {__index = base})"
                + " local t = setmetatable({}, {__index = mid}) print(t.a)");
        s.add("local t = setmetatable({}, {__name = 'MyType'}) print(tostring(t))");
        s.add("print(getmetatable(''), getmetatable(1), getmetatable(nil), getmetatable(true))");
        s.add("local mt = {__eq = function() return 'maybe' end}"
                + " local a = setmetatable({}, mt) local b = setmetatable({}, mt) print(a == b)");
        s.add("print(pcall(function() local t = {} t[nil] = 1 end))");
        s.add("print(pcall(function() local t = {} t[0/0] = 1 end))");
        s.add("print(#{1, 2, 3, nil}, #{n = 1})");

        // 错误处理进阶
        s.add("print(pcall(function() error(setmetatable({}, {__tostring = function()"
                + " return 'OBJ' end})) end))");
        s.add("local function f() error('deep', 2) end local function g() f() end"
                + " print(pcall(g))");
        s.add("print(pcall(pcall, error, 'x'))");
        s.add("print(select('#'), select('#', nil))");
        s.add("print(select(-1, 'a', 'b', 'c'), select(-3, 'a', 'b', 'c'))");
        s.add("print(pcall(function() return select(0, 'a') end))");

        // _ENV
        s.add("local _ENV = {print = print, x = 42} print(x)");
        s.add("local function f() return _G == _ENV end print(f())");

        // 边界
        s.add("print(math.maxinteger, math.mininteger)");
        s.add("local n = 0 for i = 1, 3 do n = n + i end print(n)");
        s.add("do local x = 1 do local x = 2 print(x) end print(x) end");
        s.add("local t = {} for i = 1, 100 do t[i] = i end local s = 0"
                + " for i = 1, #t do s = s + t[i] end print(s)");
        s.add("local s = 0 for i = 1, 1000 do s = s + i end print(s)");
        s.add("print(({'x','y','z'})[2])");
        s.add("local a, b, c = 1 print(a, b, c)");

        // ---- 批 2：把 Vm 里还没被点亮的指令补上 ----

        // 立即数比较走元方法（OP_LTI/LEI/GTI/GEI 的慢路径）
        s.add("local mt = {__lt = function(a, b) return true end,"
                + " __le = function(a, b) return false end}"
                + " local t = setmetatable({}, mt) print(t < 1, t <= 1, t > 1, t >= 1)");
        s.add("local mt = {__lt = function(a, b) return b == 1 end}"
                + " print(setmetatable({}, mt) < 1, 1 < setmetatable({}, mt))");
        s.add("local mt = {} print(pcall(function() return setmetatable({}, mt) < 1 end))");
        s.add("print(2.5 < 3, 2.5 <= 2.5, 3 > 2.5, 3 >= 3.0)");

        // 立即数算术走元方法（OP_ADDI / SHRI / BNOT 等）
        s.add("local mt = {__add = function(a, b) return 'A' .. b end}"
                + " print(setmetatable({}, mt) + 7)");
        s.add("local mt = {__shl = function(a, b) return 'SHL' end}"
                + " print(setmetatable({}, mt) << 3)");
        s.add("local mt = {__bnot = function(a) return 'BNOT' end}"
                + " print(~setmetatable({}, mt))");
        s.add("print(pcall(function() return '10' | 1 end))");
        s.add("print(pcall(function() return ~'a' end))");
        s.add("print(pcall(function() return 2 ^ 'x' end))");

        // 位运算 / 除法的整数与浮点两路
        s.add("print(1 << 62, -1 >> 63, 7 & -1, 0 ~ 0 ~ 5)");
        s.add("print(5.0 | 0, 5.0 & 3.0, ~5.0, 1.0 << 2.0)");
        // 整数除零/取模零报错，浮点不报（1.0//0 就是 inf）——两者别混成一条
        s.add("print(pcall(function() return 1 // 0 end))");
        s.add("print(pcall(function() return 1 % 0 end))");
        s.add("print(1.0 // 0, 1.0 % 0, -1.0 // 0)");
        s.add("print(2 ^ 0.5, (-8) ^ (1/3), 0 ^ 0)");
        s.add("print(-2.5, - -3, -(0/0))");

        // CONCAT：多个操作数、数字、元方法
        s.add("print('a' .. 1 .. 2.0 .. true .. 'b')");
        s.add("local s = '' for i = 1, 60 do s = s .. 'x' end print(#s)");
        s.add("print(pcall(function() return {} .. 'x' end))");

        // 变长参数
        s.add("local function f(...) return ... end print(f(1, 2, 3))");
        s.add("local function f(...) local t = {...} return #t, t[2] end print(f('a', 'b', nil))");
        s.add("local function f(a, ...) return select('#', ...), (...) end print(f(1, 2, 3))");
        s.add("local function id(...) return ... end print(id(id(1, 2), 3))");

        // 大表构造：多段 SETLIST 与哈希部分预分配
        s.add("local t = {} for i = 1, 200 do t[i] = i end print(#t, t[200])");
        s.add("local t = {1,2,3,4,5,6,7,8,9,10, a=1, b=2, c=3, d=4, e=5} print(#t, t.e)");
        s.add("local n = 0 local t = {10,20,30,40,50,60} for k in pairs(t) do n = n + k end"
                + " print(n)");

        // <close> 与 OP_CLOSE / OP_TBC
        s.add("do local f <close> = setmetatable({}, {__close = function()"
                + " print('closing') end}) print('body') end print('after')");
        s.add("local order = {} do local a <close> = setmetatable({}, {__close = function()"
                + " order[#order+1] = 'a' end}) local b <close> = setmetatable({},"
                + " {__close = function() order[#order+1] = 'b' end}) end"
                + " print(order[1], order[2])");
        s.add("print(pcall(function() local f <close> = setmetatable({},"
                + " {__close = function(_, err) print('on', err) end}) error('x') end))");

        // 短路与真假值（OP_TEST / OP_TESTSET / OP_LFALSESKIP）
        s.add("local t = {} print(t.a and t.a.b, t.a or 'fallback')");
        s.add("local x = nil local y = x and 'has' or 'none' print(y)");
        s.add("local x = 1 local y = x and 'has' or 'none' print(y)");
        s.add("local n = 0 local t = {1, 2, 3} while t[n+1] do n = n + 1 end print(n)");

        // 方法调用 OP_SELF
        s.add("local obj = {v = 1, inc = function(self) self.v = self.v + 1 end}"
                + " obj:inc() obj:inc() print(obj.v)");
        s.add("print(pcall(function() local t = {} t:nope() end))");

        // _ENV 与 upvalue
        s.add("local _ENV = {print = print} a = 1 print(a)");
        s.add("print(pcall(function() local _ENV = 5 return x end))");
        s.add("local f do local up = 'u' f = function() return up end end print(f())");

        // 返回值截断
        s.add("local function three() return 1, 2, 3 end local a = three() print(a)");
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {three(), 9} print(#t, t[1], t[2])");
        s.add("local function three() return 1, 2, 3 end return print(#{three()})");

        // 数值格式化
        s.add("print(1e15, 1e16, 1e-5, 123456789012345, 1/3, 2/3)");
        s.add("print(0.1 + 0.2, 1e308 * 10, -1e308 * 10)");
        s.add("print(3 == 3.0, '3' == 3, 0/0 == 0/0, 1/0 == 1/0)");

        return s;
    }

    @ParameterizedTest(name = "script #{index}")
    @MethodSource("scripts")
    void scriptMatchesReference(String src) {
        Diff.assertMatches(src);
    }

    @Test
    void errorMessagesMatchReference() {
        List<String> bad = new ArrayList<>();
        bad.add("print(nil + 1)");
        bad.add("local t = nil print(t.x)");
        bad.add("local t = nil t.x = 1");
        bad.add("print(#5)");
        bad.add("print(nil .. 'x')");
        bad.add("print((nil)())");
        bad.add("error('boom')");
        bad.add("assert(false)");
        bad.add("for i = 1, 'x' do end");
        bad.add("for i = 1, 10, 0 do end");
        // 名字后缀：常量 / 局部变量 / upvalue / 全局 / 字段 / 方法 / 元方法
        bad.add("return '10' | 1");
        bad.add("return ~'a'");
        bad.add("local x = nil return x + 1");
        bad.add("local x = nil return x .. 'a'");
        bad.add("local x = nil return #x");
        bad.add("local up = nil local function f() return up + 1 end f()");
        bad.add("return undefinedglobal + 1");
        bad.add("local t = {} return t.x + 1");
        bad.add("local t = {} return t[1] + 1");
        bad.add("local t = {} t:nope()");
        bad.add("local t = {} return t ^ 1");
        bad.add("local x <close> = 5 print(x)");
        bad.add("return 1 // 0");
        bad.add("return 1 % 0");
        bad.add("return 1 & 'x'");
        bad.add("local t = setmetatable({}, {}) return t < 1");
        bad.add("return 2 < 'a'");
        for (String src : bad) {
            Diff.assertFirstLineMatches(src);
        }
    }
}
