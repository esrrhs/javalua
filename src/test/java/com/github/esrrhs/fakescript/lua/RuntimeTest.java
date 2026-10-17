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
        /*
         * 内嵌 \0：C 版靠「解析指针是否走到 s + l」判失败，Java 的
         * String.trim() 却会把 \0 当成空白先去掉，于是这两组曾经全算成数字。
         * 上游 math.lua 有六条断言对着它们。
         */
        s.add("print(tonumber('1\\0'), tonumber('1 \\0'), tonumber('1\\0 '))");
        s.add("print(tonumber('1\\0', 2), tonumber('\\0ff', 16), tonumber('1\\0', 10))");
        s.add("print(tonumber('  '), tonumber(''), tonumber('1  a'), tonumber('1  a', 2))");
        s.add("print(tonumber('inf'), tonumber('Nan'), tonumber(' 3.4.5 '), tonumber('e1'))");
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

    /**
     * {@code lauxlib.c: interror} 的两种报错文本。
     * <p>
     * {@code luaL_checkinteger} 解释不出整数时，判据是
     * {@code lua_isnumber(L, arg)}（是数字，<b>或字符串能解释成数字</b>），
     * 不是 {@code lua_type(L, arg) == LUA_TNUMBER}。于是：
     * <ul>
     *   <li>{@code string.format('%d', '3.5')} —— {@code '3.5'} 解释得成
     *       3.5，只是 3.5 不是整数 → {@code number has no integer
     *       representation}；</li>
     *   <li>{@code string.format('%d', 'abc')} —— 压根不是数字 →
     *       {@code number expected, got string}。</li>
     * </ul>
     * 早年这里按 {@code v.isNumber()} 判，两条都成了后者。
     */
    @Test
    void integerCoercionErrorTexts() {
        Diff.assertMatches(""
                + "print(pcall(string.format, '%d', '3.5'))\n"
                + "print(pcall(string.format, '%d', 'abc'))\n"
                + "print(pcall(string.format, '%d', '10'))\n"
                + "print(pcall(string.format, '%d', '3.0'))\n"
                + "print(pcall(string.format, '%d', ' 7 '))\n"
                + "print(pcall(string.format, '%d', true))\n"
                + "print(pcall(string.format, '%d', nil))\n"
                + "print(pcall(string.format, '%d', {}))\n"
                + "print(pcall(string.format, '%d', 1e100))\n"
                + "print(pcall(string.format, '%5.2f', '3.5'))\n"
                + "print(pcall(string.format, '%5.2f', 'abc'))\n"
                + "print(pcall(string.rep, 'x', '3.5'))\n"
                + "print(pcall(string.rep, 'x', 'abc'))\n"
                + "print(pcall(string.sub, 'abcdef', '2', '4'))\n"
                + "print(pcall(string.sub, 'abcdef', '2.5', '4'))\n"
                + "print(pcall(string.sub, 'abcdef', 'x', '4'))\n"
                + "print(pcall(table.concat, {'a','b'}, '2'))\n"
                + "print(pcall(table.move, {1,2,3}, '1', '2', '1'))\n");
    }

    /**
     * {@code %.14g} 的舍入：一次舍到位，不是「最短表示再舍一次」。
     * <p>
     * Java 的 {@code String.format("%.Nf")} / {@code ("%.Ne")} 不是拿真值舍的，
     * 而是拿 {@code Double.toString} 的<b>最短表示</b>再舍一次 —— 连舍两次。
     * {@code math.sqrt(math.maxinteger)} 的真值是
     * {@code 3037000499.976049900054931640625}，最短表示 {@code 3037000499.97605}
     * 的第 15 位是 5，于是第二次舍入把它顶成 {@code 3037000499.9761}；
     * 参考实现按真值舍，给 {@code 3037000499.976}。
     * <p>
     * 这一类错很隐蔽：4602 个值里只错 12 个（0.26%），手写的用例几乎撞不到，
     * 只有 fuzz 撞得到。所以这里用<b>脚本内的 LCG</b> 铺开几千个量级——
     * 不用 {@code math.random}，免得随机数发生器本身也进入被比较的范围。
     */
    @Test
    void floatFormattingMatchesReferenceAcrossMagnitudes() {
        Diff.assertMatches(""
                // 实测撞出来的那几个
                + "print(math.sqrt(math.maxinteger))\n"
                + "print(math.sqrt(56.5), math.sqrt(96), math.sqrt(385.5), math.sqrt(399.5))\n"
                + "print(2^89)\n"
                + "print(24807139121570.0, 6409511108729.2, 6.8674997827243e-05)\n"
                // 循环小数与整除
                + "for i = 1, 400 do print(i/7) print(i/3) print(i/9) end\n"
                // 铺满量级：次正规到 1e300
                + "for i = 0, 300 do print(2^(i - 150)) end\n"
                + "for i = 1, 300 do print(i * 1e-5) print(i * 1e5) print(i / 3) end\n"
                // 开方：结果往往正好落在 14 位有效数字的边界上
                + "for i = 1, 400 do print(math.sqrt(i)) print(math.sqrt(i + 0.5)) end\n"
                // 脚本内 LCG，铺随机尾数
                + "local s = 12345\n"
                + "local function rnd() s = (s * 1103515245 + 12345) % 2147483648 return s end\n"
                + "for i = 1, 600 do\n"
                + "  local a, b = rnd(), rnd()\n"
                + "  print(a / b)\n"
                + "  print(a * b * 1e-10)\n"
                + "  print(a + b / 10000)\n"
                + "end\n"
                + "print(0.0, -0.0, 1/0, -1/0, 0/0)\n");
    }

    /**
     * {@code #t} 的<b>具体取值</b>，而不只是「某个合法边界」。
     * <p>
     * 手册只要求 {@code #t} 返回一个边界，但真实程序看得到具体是哪一个，所以要
     * 和参考实现一模一样。差在 {@code ltable.c: luaH_getn} 的分支 (1a)：
     * 数组末格为空、但倒数第二格还在时，C 直接答 {@code limit-1}，<b>不去二分</b>
     * ——注释写明了动机，{@code t[#t] = nil} 之后立刻再取长度是最常见的一串操作。
     * <p>
     * 少了 (1a)：数组 {@code [1, nil, 3, nil]} 二分给 {@code 1}，参考实现给
     * {@code 3}。系统性地铺开各种空洞之后，4968 条结果里错 212 条（4.3%）。
     * 这里用循环铺一个子集；每条都把 {@code #t} 取两次 —— C 会顺手把 {@code alimit}
     * 改成一个「提示」，两次结果必须一致。
     */
    @Test
    void tableLengthBoundaryMatchesReference() {
        Diff.assertMatches(""
                // 实测撞出来的那几条
                + "local p = table.pack(1, nil, 3, nil) print(p.n, #p)\n"
                + "local q = {1, 2, 3, 4} q[2] = nil q[4] = nil print(#q)\n"
                // 单个空洞：n 从 1 到 32，空洞扫过每个位置
                + "local function build(n, skip)\n"
                + "  local t = {}\n"
                + "  for i = 1, n do if not skip(i) then t[i] = i end end\n"
                + "  return t\n"
                + "end\n"
                + "for n = 1, 32 do\n"
                + "  for h = 1, n do\n"
                + "    local t = build(n, function(i) return i == h end)\n"
                + "    print(n, h, #t, #t)\n"
                + "  end\n"
                + "end\n"
                // 两个空洞
                + "for n = 2, 14 do\n"
                + "  for a = 1, n do\n"
                + "    for b = a, n do\n"
                + "      local t = build(n, function(i) return i == a or i == b end)\n"
                + "      print(n, a, b, #t, #t)\n"
                + "    end\n"
                + "  end\n"
                + "end\n"
                // 全空 / 只留头 / 只留尾 / 隔一个空一个
                + "for n = 1, 24 do\n"
                + "  local t = build(n, function(i) return true end) print(n, #t, #t)\n"
                + "  t = build(n, function(i) return i > 1 end) print(n, #t, #t)\n"
                + "  t = build(n, function(i) return i < n end) print(n, #t, #t)\n"
                + "  t = build(n, function(i) return i % 2 == 0 end) print(n, #t, #t)\n"
                + "  t = build(n, function(i) return i % 2 == 1 end) print(n, #t, #t)\n"
                + "end\n"
                // table.pack 的 32 种 nil 组合：n 是精确的，#p 与 unpack 用 #p
                + "for m = 0, 31 do\n"
                + "  local args = {}\n"
                + "  for k = 1, 5 do args[k] = ((m >> (k - 1)) & 1) == 1 and k or nil end\n"
                + "  local r = table.pack(table.unpack(args, 1, 5))\n"
                + "  print(r.n, #r, select('#', table.unpack(r)))\n"
                + "end\n");
    }

    /**
     * 库函数报错里的<b>全名</b>在协程里也得反查得到。
     * <p>
     * {@code lauxlib.c: pushglobalfuncname} 从 {@code _LOADED}（也就是
     * {@code package.loaded}）里按值反查两层，才把 {@code '?'} 换成
     * {@code 'math.random'}。而 {@code _LOADED} 在 C 里挂在<b>注册表</b>上，
     * 注册表是 {@code global_State} 级别的 —— 所有 {@code lua_State} 共用一份。
     * 这里早先每个 state 一份，协程拿到的是空表，于是：
     * {@code bad argument #1 to '?'} vs 参考实现的 {@code 'math.random'}。
     * <p>
     * 不打印 {@code coroutine.running()}：那是线程地址，两边必然不同。
     */
    @Test
    void libraryFunctionNamesResolveInsideCoroutines() {
        Diff.assertMatches(""
                + "local co = coroutine.create(function()\n"
                + "  print(pcall(math.random, 5, 1))\n"
                + "  print(pcall(string.rep, 'x', 'y'))\n"
                + "  print(pcall(string.format, '%d', 'zz'))\n"
                + "  print(pcall(table.concat, 5))\n"
                + "  print(pcall(math.random, 'x'))\n"
                + "end)\n"
                + "print(coroutine.resume(co))\n"
                // 主线程上也要对：两条路径共用同一份 _LOADED
                + "print(pcall(math.random, 5, 1))\n"
                + "print(pcall(string.rep, 'x', 'y'))\n"
                + "local function f() return math.random(5, 1) end\n"
                + "print(pcall(f))\n"
                + "local g = math.random\n"
                + "print(pcall(g, 5, 1))\n");
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

    /**
     * {@code and} / {@code or} 的<b>返回值</b>，而不只是真假。
     * <p>
     * 这两个算符的字节码一直与参考实现逐条相同，所以字节码闸门（{@code ConformanceTest}）
     * 全绿；错的是 {@code OP_TESTSET} 的判条件写成了「真值 == k」，
     * 而上游判的是 {@code l_isfalse(rb) == k}——两者在 {@code k} 的两个取值上
     * 恰好相反。更阴的是 {@code TESTSET} 只在「结果寄存器 ≠ B」时才留下来
     * （否则 {@code patchTestReg} 把它改写成 {@code OP_TEST}），于是
     * {@code if x then} 一直是对的、{@code x and y} 一直是错的——
     * 症状 {@code print(t and "yes")} 打印出那张表，
     * 而 {@code cond and a or b} 这个 Lua 里最常用的写法整体取错分支。
     * <p>
     * 所以这里专门钉住「返回的是<b>哪个</b>值」，以及短路有没有真的短路。
     */
    @Test
    void logicalOperatorsReturnValues() {
        Diff.assertMatches(""
                + "print(1 and 2, true and 'x', nil and 2, false and 2)\n"
                + "print(1 or 2, true or 'x', nil or 2, false or 2)\n"
                + "local a, t, z = 1, {}, nil\n"
                + "print(a and 2, t and 'yes', z and 2, z and 'no')\n"
                + "print(a or 9, t or 9, z or 9)\n"
                // 短路：右操作数根本不该被求值
                + "print(z and z.x)\n"
                + "print(t or z.x)\n"
                + "local n = 0\n"
                + "local function bump() n = n + 1 return true end\n"
                + "local r = z and bump()\n"
                + "print(r, n)\n"
                + "r = a and bump()\n"
                + "print(r, n)\n"
                // 最常用的惯用法
                + "print(z and 1 or 2)\n"
                + "print(a and 1 or 2)\n"
                + "print(false and 1 or 2)\n"
                // 比较结果是 VRELOC，走的是另一条（OP_NOT 折叠）
                + "print((1 < 2) and 'lt', (1 > 2) and 'gt', (1 > 2) or 'ge')\n"
                + "print(not (1 < 2) and 'x' or 'y')\n"
                // 嵌套与副作用顺序
                + "print(a and t and 'deep')\n"
                + "print(z or false or 'fall')\n"
                + "print((a and nil) or 'after')\n");
    }

    /**
     * {@code string.format('%q')} 的系统性探针：{@code addquoted} 的每条分支、
     * 以及 0..255 <b>每一个字节</b>原样输出。
     * <p>
     * 扫全部 256 个字节不只是为了 {@code %q}：字节 0x80–0xFF 是非法 UTF-8，
     * 它们被<b>原样</b>写进 stdout。这一段因此同时是「输出逐字节一致」的钉子
     * ——而逐字节这件事本身曾经是假的：闸门两边都用 UTF-8 解码捕获到的输出，
     * 非法字节在两边都变成 {@code U+FFFD}，于是「一边写 0x80、另一边写 0xFF」
     * 会被判成一致。实测：{@code b'"\x80"'} 与 {@code b'"\xef\xbf\xbd"'}
     * 按 UTF-8 解码后<b>相等</b>（都是 {@code U+FFFD}），按 Latin-1 解码才不等。
     * 闸门现在用 Latin-1（字节↔字符一一对应）。
     * <p>
     * <b>不含次正规浮点</b>：{@code %q} 走 C 的 {@code %a}，而 C99 对次正规数
     * 的前导数字写的是 <i>unspecified</i> —— glibc 与 Apple libc 实测相反
     * （详见 {@code StringLibTest.subnormalQuoteFollowsGlibc}）。
     */
    @Test
    void quoteFormatMatchesReferenceForEveryByte() {
        Diff.assertMatches(""
                + "local function emit(s) print(string.format('%q', s)) end\n"
                + "for b = 0, 255 do emit(string.char(b)) end\n"
                + "for b = 0, 255 do emit(string.char(b) .. '5') end\n"
                + "for b = 0, 255 do emit(string.char(b) .. 'z') end\n"
                + "for b = 0, 255 do emit('a' .. string.char(b) .. '9b') end\n"
                // 需要补成 \ddd 的那些：后一位是数字时 \d 会被读成 \dd
                + "emit('\\1\\12\\123')\n"
                + "emit(string.char(1, 50) .. string.char(2, 51) .. string.char(0, 48))\n"
                + "emit('\"')\n"
                + "emit('\\\\')\n"
                + "emit('\\n')\n"
                + "emit('\\r')\n"
                + "emit('\\t')\n"
                + "emit('\\0')\n"
                + "emit('a\\0b')\n"
                + "emit('\\\\\"\\\\')\n"
                // 浮点：quotefloat 的十六进制分支（次正规除外，见上）
                + "local fs = {0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 1/3, 1/7, 0.1, 0.2, 0.3,\n"
                + "  1e300, 1e-300, 1e308, math.pi, math.sqrt(2), 2^53, 2^63, 2^64, -2^63,\n"
                + "  math.maxinteger, math.mininteger, 1e15, 1e16, 1e17, 0.000001, 0.0000001}\n"
                + "for i = 1, #fs do print(string.format('%q', fs[i])) end\n"
                + "print(string.format('%q', 1/0), string.format('%q', -1/0),\n"
                + "      string.format('%q', 0/0))\n"
                // 整数：MIN_INTEGER 走十六进制
                + "local is = {0, 1, -1, 7, 255, 65535, 2147483647, -2147483648,\n"
                + "  math.maxinteger, math.mininteger, 1000000000000, -1000000000000}\n"
                + "for i = 1, #is do print(string.format('%q', is[i])) end\n"
                + "print(string.format('%q', nil), string.format('%q', true),\n"
                + "      string.format('%q', false), string.format('%q', '10'),\n"
                + "      string.format('%q', '  x  '))\n"
                // 往返：%q 出来的要能被 load 原样读回
                + "local rt = {'a', '\\n', '\"', '\\\\', '\\0', '\\1', '\\12', '\\123',\n"
                + "  '\\x7f', '\\xff', 'a\\0b', '\\\\1', '\\0012', 'he said \\\"hi\\\"\\n'}\n"
                + "for i = 1, #rt do\n"
                + "  local q = string.format('%q', rt[i])\n"
                + "  local ok, v = pcall(load('return ' .. q))\n"
                + "  print(q, ok, (ok and v == rt[i]) or '-')\n"
                + "end\n"
                // 报错路径
                + "print(pcall(string.format, '%q', {}))\n"
                + "print(pcall(string.format, '%q', print))\n"
                + "print(pcall(string.format, '%5q', 'x'))\n"
                + "print(pcall(string.format, '%.3q', 'x'))\n"
                + "print(pcall(string.format, '%-q', 'x'))\n"
                + "print(pcall(string.format, '%q'))\n");
    }

    /**
     * 运算符 × 类型的全矩阵：没有元方法时，每个组合的<b>报错内容</b>。
     * <p>
     * 这一批是「运算符 × 类型」的系统性扫描，不是挑几个组合点一下。它撞出过一处
     * 真分歧：{@code 1.5 & 3} 参考实现报左边的 {@code 1.5}
     * （{@code number (local 'a') has no integer representation}），
     * 这里按 {@code F2I_FLOOR} 判，{@code 1.5} 被当成「能转成整数」于是去报了
     * 右边的 {@code 3}。{@code lvm.h} 里 {@code LUA_FLOORN2I == F2Ieq}——
     * 名字带 floor，语义是「只接受整数值，<b>不</b>取整」。手写的用例只有
     * inf/nan 那两个极端值，两种模式在它们上面结论一样，所以一直没露出来。
     * <p>
     * 值里带 {@code local}/{@code field} 后缀是刻意的：{@code varinfo} 会反推
     * 值是怎么来的，报的不是类型而是变量名，这条也一起钉住。
     */
    @Test
    void operatorTypeErrorsMatchReference() {
        Diff.assertMatches("""
                -- 元方法与类型错误矩阵：运算符 × 类型对，无元方法时的报错
                local VALUES = {
                  { "nil",      function() return nil end },
                  { "boolean",  function() return true end },
                  { "integer",  function() return 3 end },
                  { "float",    function() return 1.5 end },
                  { "string",   function() return "x" end },
                  { "numstr",   function() return "3" end },
                  { "numstrf",  function() return "1.5" end },
                  { "table",    function() return {} end },
                  { "function", function() return print end },
                  { "thread",   function() return coroutine.create(function() end) end },
                }

                local OPS = {
                  { "add",  function(a, b) return a + b end },
                  { "sub",  function(a, b) return a - b end },
                  { "mul",  function(a, b) return a * b end },
                  { "div",  function(a, b) return a / b end },
                  { "idiv", function(a, b) return a // b end },
                  { "mod",  function(a, b) return a % b end },
                  { "pow",  function(a, b) return a ^ b end },
                  { "unm",  function(a) return -a end },
                  { "band", function(a, b) return a & b end },
                  { "bor",  function(a, b) return a | b end },
                  { "bxor", function(a, b) return a ~ b end },
                  { "shl",  function(a, b) return a << b end },
                  { "shr",  function(a, b) return a >> b end },
                  { "bnot", function(a) return ~a end },
                  { "concat", function(a, b) return a .. b end },
                  { "len",  function(a) return #a end },
                  { "lt",   function(a, b) return a < b end },
                  { "le",   function(a, b) return a <= b end },
                  { "eq",   function(a, b) return a == b end },
                  { "index", function(a) return a.k end },
                  { "call", function(a) return a() end },
                }

                for oi = 1, #OPS do
                  local op = OPS[oi]
                  local nargs = (op[1] == "unm" or op[1] == "bnot" or op[1] == "len"
                                 or op[1] == "index" or op[1] == "call") and 1 or 2
                  for i = 1, #VALUES do
                    local jto = (nargs == 1) and i or #VALUES
                    for j = (nargs == 1) and i or 1, jto do
                      local a = VALUES[i][2]()
                      local b = (nargs == 2) and VALUES[j][2]() or nil
                      local ok, r
                      if nargs == 1 then
                        ok, r = pcall(op[2], a)
                      else
                        ok, r = pcall(op[2], a, b)
                      end
                      -- 表/函数/线程的地址每次都不同，只留类型
                      if not ok and type(r) ~= "string" then r = type(r) end
                      print(op[1], VALUES[i][1], (nargs == 2) and VALUES[j][1] or "-",
                            ok, tostring(r))
                    end
                  end
                end
                """);
    }

    /**
     * 有元方法时：分派顺序、{@code __le} 的回退、{@code __index}/{@code __newindex}
     * 的表与函数两种形态、{@code __concat} 的右结合、{@code __close} 的调用时机
     * 与错误传递、以及元方法本身出错或不合法时的报错。
     * <p>
     * 二元运算的四种组合（只有左有 / 只有右有 / 两边都有 / 都没有）逐个运算符铺开，
     * 因为「先找谁」是 {@code luaT_trybinTM} 最容易写反的地方。
     */
    @Test
    void metamethodDispatchMatchesReference() {
        Diff.assertMatches("""
                -- 有元方法时：谁的分派先走、结果是什么、链式/回退
                local function mt(t, name)
                  return setmetatable({}, {__index = function(_, k)
                    return function(a, b) return name .. "(" .. tostring(a) .. "," .. tostring(b) .. ")" end
                  end})
                end

                -- 1) 二元运算：左右各自定义/不定义
                local BINOPS = { "__add", "__sub", "__mul", "__div", "__mod", "__pow",
                                 "__idiv", "__band", "__bor", "__bxor", "__shl", "__shr" }
                local SYMBOL = { __add = "+", __sub = "-", __mul = "*", __div = "/",
                                 __mod = "%", __pow = "^", __idiv = "//", __band = "&",
                                 __bor = "|", __bxor = "~", __shl = "<<", __shr = ">>" }
                for i = 1, #BINOPS do
                  local ev = BINOPS[i]
                  for l = 0, 1 do
                    for r = 0, 1 do
                      local a, b = {}, {}
                      if l == 1 then
                        setmetatable(a, {[ev] = function(x, y) return "L" end})
                      end
                      if r == 1 then
                        setmetatable(b, {[ev] = function(x, y) return "R" end})
                      end
                      -- 用 load 拼运算符，避免手写 12 份
                      local f = load("local a, b = ... return a " .. SYMBOL[ev] .. " b")
                      local ok, res = pcall(f, a, b)
                      print(ev, "l=" .. l, "r=" .. r, ok, tostring(res))
                    end
                  end
                end

                -- 2) 一元：只看第一个操作数的元表
                for _, ev in ipairs({ "__unm", "__bnot", "__len" }) do
                  for l = 0, 1 do
                    local a = {}
                    if l == 1 then
                      setmetatable(a, {[ev] = function(x) return "L" end})
                    end
                    local f
                    if ev == "__unm" then f = load("local a = ... return -a")
                    elseif ev == "__bnot" then f = load("local a = ... return ~a")
                    else f = load("local a = ... return #a") end
                    print(ev, "l=" .. l, pcall(f, a))
                  end
                end

                -- 3) 比较：__lt / __le 的回退（没有 __le 时用 not (b < a)）
                local CMP = { "<", "<=", ">", ">=" }
                for i = 1, #CMP do
                  local sym = CMP[i]
                  for l = 0, 1 do
                    for r = 0, 1 do
                      local a, b = {}, {}
                      if l == 1 then setmetatable(a, {__lt = function() return "LT" end,
                                                      __le = function() return "LE" end}) end
                      if r == 1 then setmetatable(b, {__lt = function() return "LT" end,
                                                      __le = function() return "LE" end}) end
                      local f = load("local a, b = ... return a " .. sym .. " b")
                      print("cmp", sym, "l=" .. l, "r=" .. r, pcall(f, a, b))
                    end
                  end
                end

                -- 4) 只有 __lt、没 __le：a <= b 要走 not (b < a)
                local onlylt = setmetatable({}, {__lt = function() return true end})
                print("onlylt <=", pcall(function() return onlylt <= onlylt end))
                print("onlylt >=", pcall(function() return onlylt >= onlylt end))

                -- 5) 比较报错：不同类型
                local cv = { {"nil", nil}, {"bool", true}, {"int", 1}, {"flt", 1.5},
                             {"str", "x"}, {"tbl", {}}, {"fn", print} }
                for i = 1, #cv do
                  for j = 1, #cv do
                    print("lt", cv[i][1], cv[j][1], pcall(function() return cv[i][2] < cv[j][2] end))
                    print("le", cv[i][1], cv[j][1], pcall(function() return cv[i][2] <= cv[j][2] end))
                    print("eq", cv[i][1], cv[j][1], pcall(function() return cv[i][2] == cv[j][2] end))
                  end
                end

                -- 6) __concat：右结合、多操作数、元方法在任一侧
                local ca = setmetatable({}, {__concat = function(a, b) return "C" end})
                print(#(ca .. "x"), ca .. "x" .. 1, 1 .. "x" .. ca, "x" .. ca .. 2)
                print(pcall(function() return {} .. 1 end))
                print(pcall(function() return 1 .. {} end))

                -- 7) __index / __newindex：表形态与函数形态
                local proto = {k = "fromtable"}
                local t1 = setmetatable({}, {__index = proto})
                print(t1.k, t1.missing, rawget(t1, "k"))
                local t2 = setmetatable({}, {__index = function(t, k) return "fn:" .. tostring(k) end})
                print(t2.k, t2[1], rawget(t2, "k"))
                local log = {}
                local t3 = setmetatable({}, {__newindex = function(t, k, v) log[#log+1] = k .. "=" .. tostring(v) end})
                t3.x = 1
                t3[1] = 2
                print(t3.x, rawget(t3, "x"), table.concat(log, ","))
                local store = {}
                local t4 = setmetatable({}, {__index = store, __newindex = store})
                t4.a = 5
                print(t4.a, rawget(t4, "a"), store.a)
                -- 没有 __newindex 但 __index 是表：写入落自己身上
                local t5 = setmetatable({}, {__index = store})
                t5.b = 7
                print(t5.b, rawget(t5, "b"), store.b)

                -- 8) __eq：两侧都有时是否调用（类型相同才走）
                local eqa = setmetatable({}, {__eq = function() return "EQ" end})
                local eqb = setmetatable({}, {__eq = function() return "EQ" end})
                print(eqa == eqb, eqa == setmetatable({}, {}))
                -- 原始相等：数字的不同子类型
                print(1 == 1.0, "1" == 1, rawequal(1, 1.0), rawequal({}, {}))

                -- 9) __call / __tostring / __metatable / __name / __pairs
                local c = setmetatable({}, {__call = function(self, ...) return "called", ... end})
                print(c(1, 2))
                print(tostring(setmetatable({}, {__tostring = function() return "TS" end})))
                print(pcall(function()
                  return setmetatable({}, {__tostring = function() return 42 end})
                end))
                local guarded = setmetatable({}, {__metatable = "locked"})
                print(getmetatable(guarded), pcall(setmetatable, guarded, {}))
                print(pcall(function() return #setmetatable({}, {__len = function() return "x" end}) end))
                local nm = setmetatable({}, {__name = "MyType"})
                print(tostring(nm):sub(1, 6), pcall(function() return nm + 1 end))
                print(pcall(function() return setmetatable({}, {__name = 5}) end))
                local pv = setmetatable({}, {__pairs = function(t) return function() end, t, nil end})
                local n = 0
                for _ in pairs(pv) do n = n + 1 end
                print("pairs", n)

                -- 10) __close 与 to-be-closed 变量
                do
                  local order = {}
                  local function closef(_, err)
                    order[#order + 1] = "closed:" .. tostring(err)
                    return nil
                  end
                  local obj = setmetatable({}, {__close = closef})
                  local ok, e = pcall(function()
                    local x <close> = obj
                    error("boom")
                  end)
                  print("close", ok, e, table.concat(order, "|"))
                  print(pcall(function()
                    local y <close> = setmetatable({}, {__close = function() return 1, 2 end})
                  end))
                  print(pcall(function()
                    local z <close> = 42
                  end))
                  print(pcall(function()
                    local w <close> = nil
                  end))
                end

                -- 11) 元方法自己出错 / 不是函数
                print(pcall(function()
                  return setmetatable({}, {__add = 42}) + 1
                end))
                print(pcall(function()
                  return setmetatable({}, {__add = function() error("in mm") end}) + 1
                end))
                print(pcall(function()
                  return setmetatable({}, {__index = 42}).x
                end))

                -- 12) 元方法链：__index 是表，那张表又有元表
                local deep = setmetatable({}, {__index = setmetatable({}, {__index = {v = "deep"}})})
                print(deep.v)
                """);
    }

    /**
     * {@code string.format} 矩阵：动词 × 修饰符 × 宽度 × 精度 × 值类型，
     * 20777 行。{@code lstrlib.c: str_format} 是 C 里分支最密的函数之一，
     * 这一批是照着它的每个分支铺的，不是挑几个格式串点一下。它咬出两处：
     * <ul>
     *   <li><b>校验顺序</b>：{@code d i u o x X} 与 {@code f e E g G} 在 C 里是
     *       <b>先</b> {@code luaL_checkinteger} / {@code luaL_checknumber}、
     *       <b>后</b> {@code checkformat}（{@code intcase} 那一段的顺序）；
     *       而 {@code c a s} 是先 {@code checkformat}。这里一律把格式串校验
     *       放在前面，于是 {@code ("% .100d"):format("x")} 报的是
     *       {@code invalid conversion specification} 而不是
     *       {@code number expected, got string}——矩阵里错 6620 行。</li>
     *   <li><b>整数动词给了精度时 {@code 0} 标志被忽略</b>：C99 写的是
     *       「for d, i, o, u, x, and X conversions, if a precision is
     *       specified, the 0 flag is ignored」。
     *       {@code ("%05.3d"):format(42)} 是 {@code "  042"}（精度先补成
     *       {@code "042"}，宽度再用<b>空格</b>补），不是 {@code "00042"}。
     *       错 220 行。浮点动词没有这条规则，别一并改。</li>
     * </ul>
     */
    @Test
    void formatSpecifierMatrixMatchesReference() {
        Diff.assertMatches("""
                -- string.format 矩阵：动词 × 修饰符 × 宽度 × 精度 × 值类型
                local VALS = {
                  { "int",      function() return 42 end },
                  { "negint",   function() return -42 end },
                  { "maxint",   function() return math.maxinteger end },
                  { "minint",   function() return math.mininteger end },
                  { "zero",     function() return 0 end },
                  { "flt",      function() return 1.5 end },
                  { "negflt",   function() return -1.5 end },
                  { "bigflt",   function() return 1e300 end },
                  { "smallflt", function() return 1e-300 end },
                  { "nan",      function() return 0 / 0 end },
                  { "inf",      function() return 1 / 0 end },
                  { "neginf",   function() return -1 / 0 end },
                  { "zeroflt",  function() return 0.0 end },
                  { "negzero",  function() return -0.0 end },
                  { "str",      function() return "hi" end },
                  { "longstr",  function() return string.rep("z", 120) end },
                  { "emptystr", function() return "" end },
                  { "nulstr",   function() return "a" .. string.char(0) .. "b" end },
                  { "numstr",   function() return "42" end },
                  { "nilv",     function() return nil end },
                  { "bool",     function() return true end },
                  { "tbl",      function() return {} end },
                }

                -- A) 动词 × 值（无修饰符）
                local VERBS = { "d", "i", "x", "X", "o", "c", "e", "E", "f", "g", "G", "q",
                                "s", "%", "a", "A", "u", "F", "b", "n", "1", "" }
                for vi = 1, #VERBS do
                  local v = VERBS[vi]
                  for i = 1, #VALS do
                    local ok, r = pcall(string.format, "%" .. v, VALS[i][2]())
                    print("A", v, VALS[i][1], ok, tostring(r))
                  end
                end

                -- B) 修饰符 × 宽度 × 精度：挑几个动词与几个值
                local FLAGS = { "", "-", "+", " ", "#", "0", "-0", "+0", "#0", "-+ " }
                local WIDTH = { "", "0", "1", "5", "12" }
                local PREC = { "", ".", ".0", ".1", ".3", ".10", ".20", ".99", ".100" }
                local BVERBS = { "d", "x", "f", "e", "g", "s", "c", "o", "q" }
                local BVALS = { 1, 6, 8, 15, 20 }  -- int / flt / nan / str / nulstr

                for bv = 1, #BVERBS do
                  local verb = BVERBS[bv]
                  for fi = 1, #FLAGS do
                    for wi = 1, #WIDTH do
                      for pi = 1, #PREC do
                        local spec = "%" .. FLAGS[fi] .. WIDTH[wi] .. PREC[pi] .. verb
                        for k = 1, #BVALS do
                          local idx = BVALS[k]
                          local ok, r = pcall(string.format, spec, VALS[idx][2]())
                          print("B", spec, VALS[idx][1], ok, tostring(r))
                        end
                      end
                    end
                  end
                end

                -- C) 多参数 / 参数不够 / 参数过多
                print(pcall(string.format, "%d %d", 1))
                print(pcall(string.format, "%d %d", 1, 2, 3))
                print(pcall(string.format, "%d", 1, 2))
                print(pcall(string.format, "%%"))
                print(pcall(string.format, "%%d", 1))
                print(pcall(string.format, "%5%", 1))
                print(pcall(string.format, "abc"))
                print(pcall(string.format, ""))
                print(pcall(string.format, "%", 1))
                print(pcall(string.format, "a%", 1))
                print(pcall(string.format, "%-", 1))
                print(pcall(string.format, "%.", 1))
                print(pcall(string.format, "%.d", 1))
                print(pcall(string.format))
                print(pcall(string.format, nil))
                print(pcall(string.format, 1))
                print(pcall(string.format, true))
                print(pcall(string.format, {}))

                -- D) %s 的 100 字节阈值（>100 且不截断时不进 buff）
                for n = 95, 105 do
                  local s = string.rep("y", n)
                  print("D", n, pcall(string.format, "%s", s) == false, #string.format("%s", s),
                        pcall(string.format, "%10s", s) == false,
                        pcall(string.format, "%.3s", s) == false)
                end

                -- E) 数字字符串参与数值动词（字符串可转数字）
                for _, v in ipairs({ "42", "42.0", "42.5", "0x2a", "  42  ", "1e3", "", " " }) do
                  print("E", string.format("[%s]", v),
                        pcall(string.format, "%d", v),
                        pcall(string.format, "%5.2f", v),
                        pcall(string.format, "%x", v))
                end

                -- F) 超大精度 / 超大宽度（C 里有上限检查）
                print(pcall(string.format, "%200d", 1))
                print(pcall(string.format, "%.200f", 1.5))
                print(pcall(string.format, "%200s", "x"))
                print(pcall(string.format, "%1000d", 1))
                print(pcall(function() return #(string.format("%200d", 1)) end))
                print(pcall(function() return #(string.format("%200s", "x")) end))
                """);
    }

    /**
     * {@code string.pack} / {@code unpack} / {@code packsize} 矩阵：全部格式动词
     * （含 {@code i/I} 的 1–16 字节宽度、{@code c/s} 的计数、{@code X} 对齐、
     * {@code !n} 对齐、四种字节序）× 各类型的值，外加非法格式串与偏移越界，
     * 706 行。这一批是干净的，收进来是为了把「<b>已经</b>一致」这件事也钉住——
     * 否则下次动 {@code lstrlib.c} 的 pack 分支时，没有一条用例会说话。
     */
    @Test
    void packUnpackMatrixMatchesReference() {
        Diff.assertMatches("""
                -- string.pack / unpack / packsize 矩阵
                local function show(tag, fmt, ...)
                  local pok, p = pcall(string.pack, fmt, ...)
                  if not pok then
                    print(tag, fmt, "pack-err", tostring(p))
                    return
                  end
                  local sok, s = pcall(string.packsize, fmt)
                  local uok, a, b, c = pcall(string.unpack, fmt, p)
                  print(tag, fmt, "ok", #p, sok and s or ("err:" .. tostring(s)),
                        uok and tostring(a) or ("err:" .. tostring(a)),
                        uok and tostring(b) or "-", uok and tostring(c) or "-")
                end

                local FORMATS = {
                  "b", "B", "h", "H", "l", "L", "j", "J", "T", "n", "d", "f", "z", "x", "Xi", "Xo",
                  "i", "i1", "i2", "i3", "i4", "i8", "i16", "i17", "i0",
                  "I", "I1", "I2", "I3", "I4", "I8", "I16",
                  "c0", "c1", "c3", "c8", "s", "s1", "s2", "s4", "s8",
                  "<i4", ">i4", "=i4", "!i4", "<I4", ">I4", "!4i4", "!8i8", "!1i1", "!2i2", "!16i8",
                  "!4b", "!8d", "<d", ">d", "=d", "!d", "<f", ">f",
                  "<j", ">j", "!8j", "<T", "!8T", "<n", ">n",
                  "i4i4", "<i4>i4", "!8i8i8", "c3bBhH", "zs1", "i4z", "xXi4", "Xi4",
                  "!8Xi4", "<Xi4", ">Xi4", "bXb", "i4Xi4",
                  "s1i4", "i4s1", "c0c0", "zz",
                }
                local INTS = { 0, 1, -1, 127, 128, 255, -128, -129, 32767, 32768, -32768, -32769,
                               2147483647, 4294967296, math.maxinteger, math.mininteger }
                local STRS = { "", "a", "abc", "hello world" }

                -- A) 整数动词 × 整数值
                local IFMT = { "b", "B", "h", "H", "l", "L", "j", "J", "T", "i", "i1", "i2",
                               "i3", "i4", "i8", "I", "I1", "I3", "I8", "<i4", ">i4", "!8i8" }
                for i = 1, #IFMT do
                  for k = 1, #INTS do
                    show("A", IFMT[i], INTS[k])
                  end
                end

                -- B) 浮点动词 × 浮点值
                local FFMT = { "f", "d", "n", "<d", ">d", "=f", "!8d" }
                local FLTS = { 0.0, -0.0, 1.0, -1.0, 1.5, -1.5, 1 / 3, 1e300, 1e-300, 1 / 0,
                               -1 / 0, 0 / 0, 3.4028235e38, 1e39 }
                for i = 1, #FFMT do
                  for k = 1, #FLTS do
                    show("B", FFMT[i], FLTS[k])
                  end
                end

                -- C) 字符串动词 × 字符串
                local SFMT = { "z", "s", "s1", "s2", "s4", "s8", "c0", "c1", "c3", "c8" }
                for i = 1, #SFMT do
                  for k = 1, #STRS do
                    show("C", SFMT[i], STRS[k])
                  end
                end

                -- D) 全部格式串 × 一个通用值
                for i = 1, #FORMATS do
                  show("D", FORMATS[i], 7)
                end

                -- E) 参数类型错误
                for _, f in ipairs({ "b", "h", "l", "j", "i4", "I4", "d", "f", "n", "z", "s", "c3", "x" }) do
                  print("E", f, pcall(string.pack, f, "x"))
                  print("E", f, pcall(string.pack, f, nil))
                  print("E", f, pcall(string.pack, f, {}))
                  print("E", f, pcall(string.pack, f, true))
                end

                -- F) 非法格式串
                for _, f in ipairs({ "", "y", "i17", "i0", "i-1", "!0i4", "!7i4", "!3", "!!",
                                     "i4i", "c", "c-1", "X", "Xy", "s0", "s17", "z1", "b1",
                                     "i4 ", " i4", "I17", "I0", string.rep("i4", 100) }) do
                  print("F", string.format("[%s]", f), pcall(string.packsize, f),
                        pcall(string.pack, f, 1))
                end

                -- G) unpack 的输入长度/偏移
                for _, f in ipairs({ "i4", "c3", "z", "s1", "b" }) do
                  print("G", f, pcall(string.unpack, f, ""))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40)))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), 1))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), 2))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), 41))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), 42))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), 0))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), -1))
                  print("G", f, pcall(string.unpack, f, string.rep("\\1", 40), -41))
                end

                -- H) 多值往返
                print("H", pcall(string.unpack, "i4i4", string.pack("i4i4", 1, 2)))
                print("H", pcall(string.unpack, "<i4>i4", string.pack("<i4>i4", 1, 2)))
                print("H", pcall(string.unpack, "zsi4", string.pack("zsi4", "ab", "cd", 9)))
                print("H", pcall(string.unpack, "!8i8d", string.pack("!8i8d", 3, 1.25)))
                print("H", pcall(string.unpack, "c3b", string.pack("c3b", "xyz", 5)))
                print("H", pcall(string.pack, "i4i4", 1))
                print("H", pcall(string.pack, "i4", 1, 2))
                print("H", pcall(string.packsize))
                print("H", pcall(string.unpack))
                print("H", pcall(string.unpack, "i4"))
                print("H", pcall(string.pack, 1, 2))
                print("H", pcall(string.pack, true, 2))
                """);
    }

    /**
     * {@code os.date} 的每个转换说明符、{@code os.time} 的表参数校验、
     * {@code utf8.char/codepoint/offset/len/codes} 的边界、{@code tonumber} 的
     * 2–36 全进制，854 行。它咬出过一处真分歧与一处平台相关的假分歧：
     * <ul>
     *   <li><b>{@code luaL_checkinteger} 有两份实现</b>：{@code BaseLib} 里那份
     *       既没带函数名前缀，也把「无整数表示」报成了「类型不对」。
     *       {@code tonumber} 的进制、{@code select} 的下标、{@code error} 的层号、
     *       {@code ipairs} 迭代器的下标——四个调用点全错。已改成转调
     *       {@link LuaAux#checkInteger}。</li>
     *   <li><b>{@code os.date('!%z', t)} 是平台相关的</b>：C 的
     *       {@code strftime} 读的是<b>进程全局时区</b>，不是 Lua 传进去的那个
     *       {@code tm}；带 {@code !} 时 macOS 给本地偏移 {@code +0800}、
     *       glibc 给 {@code +0000}。所以这里只测<b>不带</b> {@code !} 的
     *       {@code %z} 与 {@code %Z} —— 两边同一台机器，结果确定。
     *       这是继次正规 {@code %a} 之后第二个「不是我们错、是 libc 不一致」的地方。</li>
     * </ul>
     */
    @Test
    void osDateAndUtf8AndBaseConversionMatchesReference() {
        Diff.assertMatches("""
                -- os.date / os.time / utf8 / tonumber 的边界
                -- A) os.date 的每个转换说明符（UTC，固定时间戳，避免本地时区与当前时间）
                local T = 1700000000
                -- 注意：%z / %Z 不带 "!" —— C 的 strftime 读的是<b>进程全局时区</b>，不是
                -- Lua 传进去的那个 tm；带 "!" 时两个 libc 给的答案相反（macOS 给本地偏移
                -- +0800，glibc 给 +0000）。不带 "!" 时两边同一台机器，结果确定。
                local SPECS = { "a", "A", "b", "B", "c", "C", "d", "D", "e", "F", "g", "G",
                                "h", "H", "I", "j", "m", "M", "n", "p", "r", "R", "S", "t",
                                "T", "u", "U", "V", "w", "W", "x", "X", "y", "Y", "%", "Z" }
                for i = 1, #SPECS do
                  print("A", SPECS[i], pcall(os.date, "!" .. SPECS[i], T))
                end
                for i = 1, #SPECS do
                  print("A2", SPECS[i], pcall(os.date, "!%" .. SPECS[i], T))
                end
                print("A3", pcall(os.date, "%z", T))
                print("A4", pcall(os.date, "%Z", T))

                -- B) *t / !*t 整表
                local ok, d = pcall(os.date, "!*t", T)
                if ok then
                  print("B", d.year, d.month, d.day, d.hour, d.min, d.sec, d.wday, d.yday, d.isdst)
                else
                  print("B", "err", d)
                end
                print("B2", pcall(os.date, "!*t"))
                print("B3", pcall(os.date, "*t", T))
                print("B4", pcall(os.date, "!*t", 0))
                print("B5", pcall(os.date, "!*t", -1))
                print("B6", pcall(os.date, "!*t", 2 ^ 40))
                print("B7", pcall(os.date, "!*t", -2 ^ 40))
                print("B8", pcall(os.date, "!*t", 1.5))
                print("B9", pcall(os.date, "!*t", "x"))
                print("B10", pcall(os.date, "!*t", {}))

                -- C) os.date 的非法格式
                for _, f in ipairs({ "%", "%Q", "%E", "%O", "%-", "%_", "%1", "%%%", "abc%",
                                     "%q", "%Ez", "%Oy", "%Ec", "%EC", "%Ex", "%EY", "%Od",
                                     "%Oe", "%OH", "%OI", "%Om", "%OM", "%OS", "%Ou", "%OU",
                                     "%OV", "%Ow", "%OW", "%Oy" }) do
                  print("C", string.format("[%s]", f), pcall(os.date, "!" .. f, T))
                end

                -- D) os.time
                print("D", pcall(os.time, { year = 2023, month = 11, day = 14, hour = 22,
                                            min = 13, sec = 20 }))
                print("D2", pcall(os.time, { year = 1970, month = 1, day = 1, hour = 0,
                                             min = 0, sec = 0 }))
                print("D3", pcall(os.time, { year = 1970, month = 1, day = 2 }))
                print("D4", pcall(os.time, {}))
                print("D5", pcall(os.time, { year = 2023 }))
                print("D6", pcall(os.time, { year = 2023, month = 13, day = 1 }))
                print("D7", pcall(os.time, { year = 2023, month = 0, day = 1 }))
                print("D8", pcall(os.time, { year = 2023, month = 1, day = 0 }))
                print("D9", pcall(os.time, { year = 2023, month = 1, day = 32 }))
                print("D10", pcall(os.time, { year = 2023, month = 1, day = 1, hour = 25 }))
                print("D11", pcall(os.time, { year = 2023, month = 1, day = 1, isdst = true }))
                print("D12", pcall(os.time, { year = 1900, month = 1, day = 1 }))
                print("D13", pcall(os.time, { year = 3000, month = 1, day = 1 }))
                print("D14", pcall(os.time, "x"))
                print("D15", pcall(os.time, 5))
                print("D16", pcall(os.difftime, 100, 50))
                print("D17", pcall(os.difftime, 50, 100))
                print("D18", pcall(os.difftime))
                print("D19", pcall(os.difftime, 1))
                print("D20", pcall(os.difftime, "x", "y"))

                -- E) utf8.char
                for _, n in ipairs({ 0, 1, 0x7f, 0x80, 0x7ff, 0x800, 0xffff, 0x10000, 0x10ffff,
                                     0x110000, 0x7fffffff, 0x80000000, -1, -0x110000 }) do
                  local ok, r = pcall(utf8.char, n)
                  print("E", n, ok, ok and #r or tostring(r))
                end
                print("E2", pcall(utf8.char))
                print("E3", pcall(utf8.char, 1, 2, 3))
                print("E4", pcall(utf8.char, 1, "x"))
                print("E5", pcall(utf8.char, 1.0))
                print("E6", pcall(utf8.char, 1.5))
                print("E7", pcall(utf8.char, 0x110000, 1))

                -- F) utf8.codepoint / offset / len
                local S = "hello" .. utf8.char(0x4e2d) .. utf8.char(0x6587) .. "ab"
                for i = -12, 12 do
                  print("F", i, pcall(utf8.codepoint, S, i))
                end
                for i = -12, 12 do
                  for j = i, i + 2 do
                    print("F2", i, j, pcall(utf8.codepoint, S, i, j))
                  end
                end
                for i = -12, 12 do
                  print("F3", i, pcall(utf8.offset, S, i))
                end
                for i = -12, 12 do
                  print("F4", i, pcall(utf8.offset, S, i, 1))
                end
                print("F5", pcall(utf8.codepoint, ""))
                print("F6", pcall(utf8.codepoint, S, 1, 100))
                print("F7", pcall(utf8.codepoint, S, 0))
                print("F8", pcall(utf8.codepoint, 5))
                print("F9", pcall(utf8.offset, S, 1, 100))
                print("F10", pcall(utf8.len, S))
                print("F11", utf8.charpattern)
                print("F12", pcall(utf8.len, "\\xff"))
                print("F13", pcall(utf8.len, "\\xc3\\xa9"))
                print("F14", pcall(utf8.len, "abc", 2))
                print("F15", pcall(utf8.len, "abc", 2, 3))
                print("F16", pcall(utf8.len, "abc", -1))
                print("F17", pcall(utf8.len, "abc", 0))
                print("F18", pcall(utf8.len, "abc", 5))
                print("F19", pcall(utf8.len, 5))
                local n = 0
                for p, c in utf8.codes(S) do n = n + 1 end
                print("F20", n, pcall(utf8.codes, "\\xff"), pcall(utf8.codes, 5))

                -- G) tonumber 全进制
                for base = 2, 36 do
                  for _, s in ipairs({ "0", "1", "z", "Z", "-1", "-z", "10", "ff", "FF", "",
                                       " 1 ", "1.5", "0x10", "+1", "1e2" }) do
                    print("G", base, string.format("[%s]", s), pcall(tonumber, s, base))
                  end
                end
                print("G2", pcall(tonumber, "10", 1))
                print("G3", pcall(tonumber, "10", 37))
                print("G4", pcall(tonumber, "10", 1.5))
                print("G5", pcall(tonumber, "10", "10"))
                print("G6", pcall(tonumber, 10, 10))
                print("G7", pcall(tonumber, nil))
                print("G8", pcall(tonumber, nil, 10))
                print("G9", tonumber("  12  "), tonumber("0x1p4"), tonumber("0X10"),
                      tonumber(".5"), tonumber("5."), tonumber("1e"), tonumber("inf"),
                      tonumber("nan"), tonumber("-0"), tonumber("0xfffffffffffff"),
                      tonumber("0xffffffffffffffff"))
                print("G10", math.tointeger(1), math.tointeger(1.0), math.tointeger(1.5),
                      math.tointeger("3"), math.tointeger(1 / 0), math.tointeger(0 / 0),
                      math.tointeger(nil), math.tointeger(2 ^ 63))
                """);
    }

    /**
     * {@code table} 库矩阵：{@code insert/remove/move/concat/unpack/pack/sort}
     * 的位置 × 表大小 × 偏移 × 值类型，含同表重叠 {@code move} 与跨表
     * {@code move}，6277 行。它咬出过一处真分歧：
     * <p>
     * <b>{@code tmove} 的正反向分支漏了一个条件。</b> C 是
     * {@code t > e || t <= f || (tt != 1 && !lua_compare(1, tt, OPEQ))}，
     * 我们只写了前两项。第三项的含义是：<b>源和目标是两张不同的表时一律正向
     * 拷贝</b>，与 {@code t}、{@code f} 的相对位置无关。漏掉它，本该正向的
     * 情形会走成逆向。
     * <p>
     * 难缠的地方在于<b>内容完全一致，只有 {@code #t} 不一样</b>：
     * {@code table.move({1}, -2, 1, -1, {1})} 之后目标表同样只剩
     * {@code [2]=1}，参考实现 {@code #t == 0}，我们给 {@code 2}。原因是插入
     * <b>次序</b>反了 —— 正向先把 {@code [1]} 挖空，{@code alimit} 仍是 1；
     * 逆向却先塞 {@code [2]}，把 {@code alimit} 顶到 2，
     * {@code luaH_getn} 的第一条分支（「{@code array[alimit-1]} 为 nil 就只在
     * 数组部分二分」）随之不再成立。所以「写入序列相同、走 {@code rawset} 就对、
     * 走 {@code table.move} 就错」—— 差别只在内部布局。
     * <p>
     * 反过来也说明一件事：<b>光比对表内容是不够的</b>，得连 {@code #t}
     * 一起比，否则这类「布局」分歧永远不会被发现。
     */
    @Test
    void tableLibraryMatrixMatchesReference() {
        Diff.assertMatches("""
                -- table 库矩阵：insert / remove / move / concat / unpack / pack / sort 的边界
                local function dump(tag, t)
                  local parts = {}
                  for i = -2, 8 do
                    parts[#parts + 1] = tostring(i) .. "=" .. tostring(rawget(t, i))
                  end
                  print(tag, "#" .. tostring(#t), table.concat(parts, ","))
                end

                local function fresh(n)
                  local t = {}
                  for i = 1, n do t[i] = i end
                  return t
                end

                -- 1) insert：位置取值 × 表大小
                for n = 0, 4 do
                  for pos = -6, 8 do
                    local t = fresh(n)
                    print("I", n, pos, pcall(table.insert, t, pos, 99))
                    dump("I-dump", t)
                    local t2 = fresh(n)
                    print("I2", n, pos, pcall(table.insert, t2, pos, 99, 98))
                    dump("I2-dump", t2)
                  end
                end

                -- 2) insert：参数与非数组表
                print("IA", pcall(table.insert, {}, 1))
                print("IB", pcall(table.insert, {}, 1, 2, 3, 4))
                print("IC", pcall(table.insert))
                print("ID", pcall(table.insert, 5, 1))
                print("IE", pcall(table.insert, "x", 1))
                print("IF", pcall(table.insert, {}, "x", 1))
                print("IG", pcall(table.insert, {}, 1.5, 1))
                print("IH", pcall(table.insert, {}, 1.0, 1))
                print("II", pcall(table.insert, {}, 2, 1))
                print("IJ", pcall(table.insert, { 1, 2, 3 }, 4, 9))
                local big = {}
                for i = 1, 100 do big[i] = i end
                print("IK", pcall(table.insert, big, 101, 1))
                print("IL", pcall(table.insert, big, 102, 1))
                print("IM", pcall(table.insert, big, 0, 1))
                print("IN", pcall(table.insert, big, -101, 1))
                print("IO", pcall(table.insert, big, math.maxinteger, 1))
                print("IP", pcall(table.insert, big, math.mininteger, 1))

                -- 3) remove：位置取值 × 表大小
                for n = 0, 4 do
                  for pos = -6, 8 do
                    local t = fresh(n)
                    local ok, v = pcall(table.remove, t, pos)
                    print("R", n, pos, ok, tostring(v))
                    dump("R-dump", t)
                  end
                end
                print("RA", pcall(table.remove, {}))
                print("RB", pcall(table.remove, 5))
                print("RC", pcall(table.remove, {}, 1, 2))
                print("RD", pcall(table.remove, {}, "x"))
                print("RE", pcall(table.remove, {}, 1.5))
                print("RF", pcall(table.remove, big))
                print("RG", pcall(table.remove, big, 1))
                print("RH", pcall(table.remove, big, 101))
                print("RI", pcall(table.remove, big, 0))

                -- 4) move：同源重叠 / 跨表 / 各种越界
                -- table.move 返回的是目标表，地址每次都不一样，只留状态与错误文本
                local function mv(a1, f, e, t)
                  local ok, err = pcall(table.move, a1, f, e, t)
                  return ok and "ok" or ("err:" .. tostring(err))
                end
                for n = 0, 3 do
                  for f = -2, 5 do
                    for e = f - 1, 5 do
                      for t = -2, 5 do
                        local a = fresh(n)
                        print("MV", n, f, e, t, mv(a, f, e, t))
                        dump("MV-dump", a)
                      end
                    end
                  end
                end
                for n = 0, 3 do
                  for f = -2, 5 do
                    for e = f - 1, 5 do
                      for t = -2, 5 do
                        local a, b = fresh(n), fresh(n)
                        local mok, merr = pcall(table.move, a, f, e, t, b)
                        print("MV2", n, f, e, t, mok and "ok" or ("err:" .. tostring(merr)))
                        dump("MV2-dump", b)
                      end
                    end
                  end
                end
                local ma, mb = pcall(table.move, {}, 1, 2, 3)
                print("MVA", ma, type(mb))
                print("MVB", pcall(table.move, {}, 1, 2))
                print("MVC", pcall(table.move, {}, 1))
                print("MVD", pcall(table.move, {}))
                print("MVE", pcall(table.move, 5, 1, 2, 3, {}))
                print("MVF", pcall(table.move, {}, "x", 2, 3, {}))
                print("MVG", pcall(table.move, {}, 1, "x", 3, {}))
                print("MVH", pcall(table.move, {}, 1, 2, "x", {}))
                print("MVI", pcall(table.move, {}, 1.5, 2, 3, {}))
                print("MVJ", pcall(table.move, {}, 1, 2, 3, 1, 2))
                local ka, kb = pcall(table.move, {}, 1, 2, 3, {}, 5)
                print("MVK", ka, type(kb))
                print("MVL", pcall(table.move, {}, 1, 1.5, 3, {}))

                -- 5) concat：值类型 × 空洞 × 分隔符 × 区间
                local VALS = { nil, true, 1, 1.5, "s", {}, print }
                local SEPS = { nil, "", ",", "xy" }
                for i = 1, #VALS do
                  local t = { VALS[i] }
                  for s = 1, #SEPS do
                    print("C", i, s, pcall(table.concat, t, SEPS[s]))
                  end
                  print("C2", i, pcall(table.concat, t, ",", 1, 1))
                  print("C3", i, pcall(table.concat, t, ",", 2, 1))
                  print("C4", i, pcall(table.concat, t, ",", 0, 1))
                  print("C5", i, pcall(table.concat, t, ",", -1, 1))
                end
                print("CA", table.concat({ 1, 2, 3 }))
                print("CB", table.concat({ 1, 2, 3 }, "-"))
                print("CC", table.concat({ 1, 2, 3 }, "-", 2))
                print("CD", table.concat({ 1, 2, 3 }, "-", 2, 3))
                print("CE", table.concat({}, "-"))
                print("CF", pcall(table.concat, { 1, nil, 3 }))
                print("CG", pcall(table.concat, 5))
                print("CH", pcall(table.concat, {}, 5))
                print("CI", pcall(table.concat, { 1, 2 }, ",", "x"))
                print("CJ", pcall(table.concat, { 1, 2 }, ",", 1, "y"))
                print("CK", pcall(table.concat))
                print("CL", pcall(table.concat, { 1, 2 }, ",", 1, 3))
                print("CM", pcall(table.concat, { 1, 2 }, ",", 1, 100))

                -- 6) unpack：区间与上限
                for i = -4, 4 do
                  for j = i - 1, 4 do
                    print("U", i, j, pcall(table.unpack, { 1, 2, 3 }, i, j))
                  end
                end
                print("UA", pcall(table.unpack, {}))
                print("UB", pcall(table.unpack, 5))
                print("UC", pcall(table.unpack, { 1, 2 }, "x"))
                print("UD", pcall(table.unpack, { 1, 2 }, 1, "y"))
                print("UE", pcall(table.unpack, { 1, 2 }, 1.5))
                print("UF", pcall(table.unpack, { 1, 2 }, 1, 2, 3))
                print("UG", pcall(table.unpack, {}, 1, 0))
                print("UH", pcall(table.unpack, {}, 2, 1))
                print("UI", pcall(table.unpack, {}, -1, 0))
                local many = {}
                for i = 1, 2000 do many[i] = i end
                print("UJ", pcall(function() return #table.pack(table.unpack(many)) end))
                print("UK", pcall(function() return table.unpack(many, 1, 1000000) end))
                print("UL", pcall(function() return table.unpack(many, -1000000, -1) end))

                -- 7) pack
                local pa, pb = pcall(table.pack)
                print("P", pa, type(pb), pb == nil and "nil" or pb.n)
                local p2a, p2b = pcall(table.pack, nil)
                print("P2", p2a, type(p2b), p2b == nil and "nil" or p2b.n)
                local p3a, p3b = pcall(table.pack, nil, nil)
                print("P3", p3a, type(p3b), p3b == nil and "nil" or p3b.n)
                local pk = table.pack(1, nil, 3, nil)
                print("P4", pk.n, pk[1], pk[2], pk[3], pk[4], #pk)
                print("P5", table.pack("a", "b").n)

                -- 8) sort：比较函数异常 / 非法次序 / 相等元素 / 非数组
                local function cmpOk(a, b) return a < b end
                print("SA", pcall(table.sort, { 3, 1, 2 }, cmpOk))
                local s1 = { 3, 1, 2 }
                table.sort(s1)
                print("SB", s1[1], s1[2], s1[3])
                local s2 = { 3, 1, 2 }
                table.sort(s2, function(a, b) return a > b end)
                print("SC", s2[1], s2[2], s2[3])
                print("SD", pcall(table.sort, { 1, 2 }, function(a, b) return true end))
                print("SE", pcall(table.sort, { 1, 2, 3 }, function(a, b) return true end))
                print("SF", pcall(table.sort, { 1, 2 }, function(a, b) error("boom") end))
                print("SG", pcall(table.sort, { 1, 2 }, function(a, b) return nil end))
                print("SH", pcall(table.sort, { 1, 2 }, 5))
                print("SI", pcall(table.sort, { 1, 2 }, "x"))
                print("SJ", pcall(table.sort, 5))
                print("SK", pcall(table.sort, {}))
                print("SL", pcall(table.sort, { 1 }, function(a, b) return a < b end))
                print("SM", pcall(table.sort, { 1, 2 }, function(a, b) return a < b end, 1))
                local s3 = { 5, 5, 5 }
                table.sort(s3)
                print("SN", s3[1], s3[2], s3[3])
                print("SO", pcall(table.sort, { 1, 2, 3 }, function(a, b) return a <= b end))
                local s4 = { "b", "a" }
                table.sort(s4)
                print("SP", s4[1], s4[2])
                print("SQ", pcall(table.sort, { 1, "a" }, cmpOk))
                print("SR", pcall(table.sort, { 0 / 0, 1 }, cmpOk))
                """);
    }

    /**
     * {@code string} 库矩阵：模式匹配（各类 {@code %} 类、集合、锚点、捕获、
     * {@code %b}、{@code %f}、贪婪/非贪婪）× 主题串（含空串、制表、换行、
     * 多字节 UTF-8）× {@code init} 越界 × 三种替换形态（串/表/函数），24819 行。
     * <p>
     * 这一片是<b>干净</b>的：铺开前心里没底，铺开后一处分歧都没有。照第 25 条
     * 经验（已经一致的也钉住），照样固化成常驻测试 —— 哪天有人动
     * {@code lstrlib.c} 的对应分支，这里会替他说话。
     */
    @Test
    void patternMatchingMatrixMatchesReference() {
        Diff.assertMatches("""
                -- string 库矩阵：模式匹配 × 主题 × init × 替换形态，外加下标类函数
                local TAB = string.char(9)
                local NL = string.char(10)
                local E9 = string.char(0xC3, 0xA9)   -- 单个 UTF-8 字符，占两个字节

                local PATTERNS = {
                  "", "a", "abc", "a*", "a+", "a-", "a?", ".", ".*", ".+", ".-",
                  "%a", "%a+", "%a*", "%d", "%d+", "%s", "%s+", "%w", "%w+", "%p", "%c", "%x+",
                  "%u", "%l", "%A", "%D", "%S", "%W",
                  "[abc]", "[^abc]", "[a-c]", "[a-c]+", "[%a]", "[%a%d]", "[^%d]+",
                  "(%a)", "(%a)(%d)", "(%a+)", "()%a", "()%a+", "((%a)(%d))",
                  "^a", "^a+", "a$", "^ab$", "^", "$", "^$",
                  "%bxy", "%b()", "%f[%a]%a+", "%f[%w]",
                  "a*b", "a-b", "a+b", "a?b", "[a-c]*d", "%a%s*%d", "(%w+)%s*=%s*(%w+)",
                }

                local SUBJECTS = {
                  "", "a", "abc", "aaa", "a1b2c3", "  x  ", "hello world", "x,y,z",
                  "aXbXc", "123", "abc123", E9 .. "ab", "()", "x(y)z", "a=b", "k = v",
                  "AaBb", "   ", TAB .. NL, "xy",
                }

                -- 1) find：模式 × 主题 × init × plain
                for p = 1, #PATTERNS do
                  for s = 1, #SUBJECTS do
                    for _, init in ipairs({ nil, 1, 2, -1, -3, 0, 100, -100 }) do
                      for _, plain in ipairs({ nil, false, true }) do
                        local ok, a, b, c, d = pcall(string.find, SUBJECTS[s], PATTERNS[p],
                                                     init, plain)
                        print("F", p, s, tostring(init), tostring(plain), ok,
                              tostring(a), tostring(b), tostring(c), tostring(d))
                      end
                    end
                  end
                end

                -- 2) match：模式 × 主题 × init
                for p = 1, #PATTERNS do
                  for s = 1, #SUBJECTS do
                    for _, init in ipairs({ nil, 1, 3, -2, 0, 100 }) do
                      local ok, a, b, c = pcall(string.match, SUBJECTS[s], PATTERNS[p], init)
                      print("M", p, s, tostring(init), ok, tostring(a), tostring(b), tostring(c))
                    end
                  end
                end

                -- 3) gmatch：模式 × 主题，收集全部（含位置捕获）
                for p = 1, #PATTERNS do
                  for s = 1, #SUBJECTS do
                    local ok, it = pcall(string.gmatch, SUBJECTS[s], PATTERNS[p])
                    if not ok then
                      print("G", p, s, "err", tostring(it))
                    else
                      local n, acc = 0, {}
                      local gok, a, b, c = pcall(it)
                      while gok and a ~= nil and n < 40 do
                        n = n + 1
                        acc[#acc + 1] = tostring(a) .. "," .. tostring(b) .. "," .. tostring(c)
                        gok, a, b, c = pcall(it)
                      end
                      print("G", p, s, "ok", n, table.concat(acc, "|"), tostring(gok))
                    end
                  end
                end

                -- 4) gsub：模式 × 替换形态 × 主题 × n
                local REPLS_S = { "", "x", "%0", "%1", "%2", "%1%1", "%0%1", "%%", "%9",
                                  "a%1b", "%", "%x", "%z" }
                for p = 1, #PATTERNS do
                  for r = 1, #REPLS_S do
                    for s = 1, #SUBJECTS do
                      local ok, a, b = pcall(string.gsub, SUBJECTS[s], PATTERNS[p], REPLS_S[r])
                      print("S", p, r, s, ok, tostring(a), tostring(b))
                    end
                  end
                end

                -- 5) gsub 的替换是表 / 函数 / 数字 / 布尔
                local function repfn(k) return "[" .. tostring(k) .. "]" end
                for p = 1, #PATTERNS do
                  for s = 1, #SUBJECTS do
                    print("T", p, s, pcall(string.gsub, SUBJECTS[s], PATTERNS[p],
                                           { a = "A", b = "B", ["1"] = "ONE" }))
                    print("U", p, s, pcall(string.gsub, SUBJECTS[s], PATTERNS[p], repfn))
                    print("V", p, s, pcall(string.gsub, SUBJECTS[s], PATTERNS[p], 5))
                    print("W", p, s, pcall(string.gsub, SUBJECTS[s], PATTERNS[p], true))
                    print("X", p, s, pcall(string.gsub, SUBJECTS[s], PATTERNS[p], nil))
                  end
                end

                -- 6) gsub 的 n 上限（含 0 与负）
                for _, n in ipairs({ 0, 1, 2, 3, -1, 1.0, 1.5, "2" }) do
                  print("N", tostring(n), pcall(string.gsub, "aaaa", "a", "b", n))
                end
                print("N2", pcall(string.gsub, "aaaa", "a", "b", "x"))
                print("N3", pcall(string.gsub, "aaaa", "a", "b", {}))

                -- 7) 下标类：sub / byte / len / rep / reverse / upper / lower
                local SS = { "", "a", "abc", "hello", E9 .. "ab" }
                for s = 1, #SS do
                  local str = SS[s]
                  for i = -6, 6 do
                    print("sub", s, i, pcall(string.sub, str, i))
                    for j = -6, 6 do
                      print("sub2", s, i, j, pcall(string.sub, str, i, j))
                    end
                    print("byte", s, i, pcall(string.byte, str, i))
                    print("byte2", s, i, i + 2, pcall(string.byte, str, i, i + 2))
                  end
                  print("len", s, #str, pcall(string.len, str))
                  print("rev", s, pcall(string.reverse, str))
                  print("up", s, pcall(string.upper, str))
                  print("low", s, pcall(string.lower, str))
                  print("rep", s, pcall(string.rep, str, 0))
                  print("rep2", s, pcall(string.rep, str, 3))
                  print("rep3", s, pcall(string.rep, str, 3, "-"))
                  print("rep4", s, pcall(string.rep, str, 3, ""))
                  print("rep5", s, pcall(string.rep, str, -1))
                  print("rep6", s, pcall(string.rep, str, 2, "xy"))
                end

                -- 8) 参数类型错误
                for _, fn in ipairs({ string.find, string.match, string.gmatch, string.gsub,
                                      string.sub, string.byte, string.rep, string.reverse,
                                      string.upper, string.lower, string.len }) do
                  print("E1", pcall(fn))
                  print("E2", pcall(fn, nil))
                  print("E3", pcall(fn, 5))
                  print("E4", pcall(fn, true))
                  print("E5", pcall(fn, {}))
                end
                print("E6", pcall(string.find, "abc", 5))
                print("E7", pcall(string.find, "abc", "a", "x"))
                print("E8", pcall(string.gsub, "abc", "a", "b", "c"))
                print("E9", pcall(string.char, -1))
                print("E10", pcall(string.char, 256))
                print("E11", pcall(string.char, 1.5))
                print("E12", pcall(string.char, "x"))
                print("E13", pcall(string.char))
                print("E14", pcall(string.rep, "a", 1e10))
                print("E15", pcall(string.sub, "abc", 1, 1, 1))
                """);
    }

    /**
     * {@code #t} 单独铺开扫，3736 行。<b>起因就是上面那处 {@code tmove} 分歧</b>：
     * 它表现出来的样子是「表内容一模一样，只有 {@code #t} 不一样」。既然
     * {@code luaH_getn} 的答案取决于内部布局、布局又取决于插入历史，那就把
     * 「同一份内容、不同构造路径」这件事本身当成扫描维度：
     * <ul>
     *   <li>{@code A}：{@code 1..n} 的<b>全部子集</b> × 三种插入次序（正序、
     *       逆序、先奇后偶）—— 内容相同，只有次序不同；</li>
     *   <li>{@code B}：先填满再按子集挖空，走 rehash 的收缩路径；</li>
     *   <li>{@code C}：挖空后往边界外补，看 {@code alimit} 会不会被顶回去；</li>
     *   <li>{@code D}：{@code table.move} 造布局，跨表（一律正向）与同表
     *       （可能逆向）分开跑 —— 就是出事的那条路径；</li>
     *   <li>{@code E}：{@code insert/remove} 交替把长度来回推；</li>
     *   <li>{@code F}：负键、0 键混进来，只撑哈希不撑数组；</li>
     *   <li>{@code G}/{@code H}：大到 40 的二分收敛点、尾部连续挖空。</li>
     * </ul>
     * 这一片<b>干净</b>，但仍固化：{@code luaH_getn} 的每一条分支都有可能被
     * 后来的改动碰歪，而这类歪斜<b>只有把 {@code #t} 和内容一起比才看得见</b>。
     */
    @Test
    void tableLengthAcrossInsertionHistoriesMatchesReference() {
        Diff.assertMatches("""
                -- 长度矩阵：#t 只跟「边界」有关，但边界由内部布局决定，布局由插入历史决定。
                -- 所以同一份内容用不同次序/不同函数构造，#t 可以不同 —— 这片就是扫这个。
                local function dump(tag, t)
                  local p = {}
                  for i = -3, 14 do p[#p + 1] = tostring(i) .. "=" .. tostring(rawget(t, i)) end
                  print(tag, "#" .. tostring(#t), table.concat(p, ","))
                end

                local ORDERS = {
                  { "fwd", function(n) local o = {} for i = 1, n do o[i] = i end return o end },
                  { "rev", function(n) local o = {} for i = 1, n do o[i] = n - i + 1 end return o end },
                  { "oe", function(n)
                      local o = {}
                      for i = 1, n, 2 do o[#o + 1] = i end
                      for i = 2, n, 2 do o[#o + 1] = i end
                      return o
                    end },
                }

                -- A) 子集 × 插入顺序：内容相同、次序不同，看 #t 是否跟着变
                for n = 0, 8 do
                  for mask = 0, 2 ^ n - 1 do
                    for oi = 1, #ORDERS do
                      local name, mk = ORDERS[oi][1], ORDERS[oi][2]
                      local t, order = {}, mk(n)
                      for k = 1, #order do
                        if mask & (1 << (order[k] - 1)) ~= 0 then t[order[k]] = order[k] end
                      end
                      dump(string.format("A n=%d m=%d %s", n, mask, name), t)
                    end
                  end
                end

                -- B) 先填满再挖空：rehash 的收缩路径
                for n = 0, 8 do
                  for mask = 0, 2 ^ n - 1 do
                    local t = {}
                    for i = 1, n do t[i] = i end
                    for i = 1, n do
                      if mask & (1 << (i - 1)) ~= 0 then t[i] = nil end
                    end
                    dump(string.format("B n=%d m=%d", n, mask), t)
                  end
                end

                -- C) 挖空后再往边界外补
                for n = 0, 6 do
                  for hole = 1, n do
                    local t = {}
                    for i = 1, n do t[i] = i end
                    t[hole] = nil
                    t[n + 1] = 99
                    dump(string.format("C n=%d h=%d", n, hole), t)
                    local t2 = {}
                    for i = 1, n do t2[i] = i end
                    t2[hole] = nil
                    t2[n + 1] = 99
                    t2[n + 2] = 98
                    dump(string.format("C2 n=%d h=%d", n, hole), t2)
                  end
                end

                -- D) table.move 造出的布局：跨表（一律正向）与同表（可能逆向）
                for n = 0, 4 do
                  for f = -2, 4 do
                    for t = -2, 4 do
                      local a, b = {}, {}
                      for i = 1, n do a[i] = i; b[i] = i end
                      table.move(a, f, n, t, b)
                      dump(string.format("D n=%d f=%d t=%d", n, f, t), b)
                      local c = {}
                      for i = 1, n do c[i] = i end
                      table.move(c, f, n, t)
                      dump(string.format("D2 n=%d f=%d t=%d", n, f, t), c)
                    end
                  end
                end

                -- E) insert / remove 交替，把长度来回推
                for n = 0, 4 do
                  local t = {}
                  for i = 1, n do t[i] = i end
                  for k = 1, 6 do
                    table.insert(t, 90 + k)
                    dump(string.format("E n=%d k=%d ins", n, k), t)
                    table.remove(t)
                    dump(string.format("E n=%d k=%d rem", n, k), t)
                  end
                end

                -- F) 负键 / 0 键混进来：它们进哈希，不影响数组边界，但会撑大哈希部分
                for n = 0, 4 do
                  for extra = -3, 1 do
                    local t = {}
                    for i = 1, n do t[i] = i end
                    t[extra] = 7
                    dump(string.format("F n=%d e=%d", n, extra), t)
                    local t2 = {}
                    for i = 1, n do t2[i] = i end
                    t2[extra] = nil
                    dump(string.format("F2 n=%d e=%d", n, extra), t2)
                  end
                end

                -- G) 大表边界：二分收敛的地方
                for n = 1, 40 do
                  for hole = 1, n do
                    local u = {}
                    for i = 1, n do u[i] = i end
                    u[hole] = nil
                    print("G", n, hole, #u)
                  end
                end

                -- H) 尾部连续挖空：alimit 与真实边界分离
                for n = 1, 20 do
                  for cut = 0, n do
                    local t = {}
                    for i = 1, n do t[i] = i end
                    for i = cut + 1, n do t[i] = nil end
                    print("H", n, cut, #t)
                  end
                end
                """);
    }
}
