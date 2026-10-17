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

    /**
     * {@code io} 库矩阵：32 种打开模式（含全部非法模式）、{@code read} 的全部格式
     * 与 0–24 字节计数、数字读法、写各类值、{@code seek} 的三 whence、
     * {@code lines}、{@code close} 后再用、{@code io.input/output/read/write/close}、
     * 追加模式的 {@code seek} 语义、{@code r+} 读写交错、5000 字节大块、
     * 同一文件开两次，167 行。<b>干净</b>。
     * <p>
     * 值得钉住的理由不是它找出了什么，而是它覆盖了<b>errno 文案</b>这一层：
     * {@code io.open} 失败要返回 {@code "路径: No such file or directory"} 加
     * 错误号 {@code 2}，那是 C 的 {@code strerror} 给的，我们得自己对齐。
     * 这部分一旦对不上，没有任何手写用例会说话。
     * <p>
     * 脚本里有一处<b>自己坑自己</b>的地方，留着当提醒：{@code io.output(5)}
     * 会在当前目录建一个名叫 {@code 5} 的文件，而差分两边跑在<b>同一个 cwd</b>，
     * 于是第二趟的 {@code io.open(5)} 就从「打不开」变成「打开了」——第一版
     * 跑出两行假分歧，查了半天。凡是脚本有文件系统副作用的，开头结尾都要清。
     */
    @Test
    void ioLibraryMatrixMatchesReference() {
        Diff.assertMatches("""
                -- io 库矩阵：打开模式 / 读格式 / 写 / seek / lines / close / 错误路径
                -- 注意：绝对路径与 errno 文案都可能出现，这里一律不打印路径本身。
                local function ok(tag, ...)
                  print(tag, ...)
                end

                local function pc(tag, f, ...)
                  -- pcall，但把 userdata 和函数都换成稳定的字符串，只留类型名
                  local r = { pcall(f, ...) }
                  for i = 2, #r do
                    local v = r[i]
                    local t = type(v)
                    if t == "userdata" then
                      r[i] = "<file>"
                    elseif t == "function" then
                      r[i] = "<fn>"
                    elseif t == "string" then
                      r[i] = string.format("%q", v)
                    end
                  end
                  print(tag, table.unpack(r, 1, #r))
                end

                local name = os.tmpname()
                local path = name

                -- 1) open：全部模式组合（含无效模式）
                local MODES = {
                  "r", "w", "a", "r+", "w+", "a+", "rb", "wb", "ab", "r+b", "w+b", "a+b",
                  "rb+", "wb+", "ab+", "br", "bw", "rt", "wt", "rw", "wr", "ar", "ra",
                  "", "x", "rx", "r++", "+", "r+b+", "r b", "R", "W", "A",
                }
                for i = 1, #MODES do
                  local m = MODES[i]
                  local pok, f, e = pcall(io.open, path, m)
                  if not pok then
                    print("O", string.format("%q", m), "err", string.format("%q", tostring(f)))
                  elseif f == nil then
                    print("O", string.format("%q", m), "nil", string.format("%q", tostring(e)))
                  else
                    print("O", string.format("%q", m), "opened", io.type(f))
                    f:close()
                    print("O2", string.format("%q", m), io.type(f))
                  end
                end

                -- 2) 参数错误
                -- G5 的 io.output(5) 会在当前目录建一个名叫 5 的文件；两边跑同一个 cwd，
                -- 第二趟就会看见它。所以每处用到名字 "5" 的地方前后都要清掉。
                os.remove("5")
                pc("E1", io.open)
                pc("E2", io.open, nil)
                pc("E3", io.open, 5)
                pc("E4", io.open, true)
                pc("E5", io.open, {})
                pc("E6", io.open, path, 5)
                pc("E7", io.open, path, nil)
                pc("E8", io.open, path, "r", 5)
                pc("E9", io.open, "/nonexistent-dir-xyz/nonexistent-file")
                pc("E10", io.open, "/nonexistent-dir-xyz/nonexistent-file", "w")
                pc("E11", io.open, "/nonexistent-dir-xyz/", "r")

                -- 3) 写 + 读：各种 read 格式
                local function withcontent(body)
                  local f = assert(io.open(path, "w"))
                  f:write("line1\\nline2\\nline3")
                  f:close()
                  local g = assert(io.open(path, "r"))
                  local r = { pcall(body, g) }
                  g:close()
                  return table.unpack(r, 1, #r)
                end

                local FMTS = { "l", "L", "a", "n", "line", "*l", "*L", "*a", "*n", "*line" }
                for i = 1, #FMTS do
                  pc(string.format("R %q", FMTS[i]), function(g)
                    local h = assert(io.open(path, "r"))
                    local a, b, c = h:read(FMTS[i], FMTS[i], FMTS[i])
                    h:close()
                    return a, b, c
                  end)
                end
                for n = 0, 24 do
                  pc(string.format("RN %d", n), function()
                    local h = assert(io.open(path, "r"))
                    local v = h:read(n)
                    h:close()
                    return v
                  end)
                end
                -- 数字格式：各种前缀
                for _, s in ipairs({ "42 rest", "0x10", " 3.5", "-7", "1e3", "abc", "", "\\n5",
                                     "0xZZ", "3", "  12  ", "inf", "nan", ".5", "5.", "0X1p4" }) do
                  pc(string.format("RD %q", s), function()
                    local f = assert(io.open(path, "w"))
                    f:write(s)
                    f:close()
                    local h = assert(io.open(path, "r"))
                    local a, b = h:read("n", "n")
                    h:close()
                    return a, b
                  end)
                end
                -- 混合格式串
                pc("RM1", function()
                  local h = assert(io.open(path, "r"))
                  local a, b, c, d = h:read("l", 3, "a", "l")
                  h:close()
                  return a, b, c, d
                end)
                pc("RM2", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read("a", "a", "l")
                  h:close()
                  return a
                end)
                pc("RM3", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read(0)
                  h:close()
                  return a
                end)
                pc("RM4", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read("x")
                  h:close()
                  return a
                end)
                pc("RM5", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read(5, "x")
                  h:close()
                  return a
                end)
                pc("RM6", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read()
                  h:close()
                  return a
                end)
                pc("RM7", function()
                  local h = assert(io.open(path, "r"))
                  local a = h:read("a")
                  local b = h:read("a")
                  h:close()
                  return a, b
                end)

                -- 4) write：类型与返回值
                pc("W1", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write("a", 1, 2.5, "b")
                  h:close()
                  return r
                end)
                pc("W2", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write()
                  h:close()
                  return r
                end)
                pc("W3", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write(nil)
                  h:close()
                  return r
                end)
                pc("W4", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write(true)
                  h:close()
                  return r
                end)
                pc("W5", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write({})
                  h:close()
                  return r
                end)
                pc("W6", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write(7)
                  h:close()
                  local g = assert(io.open(path, "r"))
                  local s = g:read("a")
                  g:close()
                  return r, s
                end)
                pc("W7", function()
                  local h = assert(io.open(path, "w"))
                  local r = h:write(1.0)
                  h:close()
                  local g = assert(io.open(path, "r"))
                  local s = g:read("a")
                  g:close()
                  return r, s
                end)
                pc("W8", function()
                  local h = assert(io.open(path, "r"))
                  local r = h:write("x")
                  h:close()
                  return r
                end)

                -- 5) seek
                pc("S1", function()
                  local h = assert(io.open(path, "w"))
                  h:write("0123456789")
                  h:close()
                  local g = assert(io.open(path, "r"))
                  local a = g:seek()
                  local b = g:seek("set")
                  local c = g:seek("cur")
                  local d = g:seek("end")
                  local e2 = g:seek("set", 3)
                  local f2 = g:read(2)
                  local g2 = g:seek("cur", -1)
                  local h2 = g:read(2)
                  local i2 = g:seek("set", 100)
                  local j2 = g:read("l")
                  local k2 = g:seek("set", -1)
                  g:close()
                  return a, b, c, d, e2, f2, g2, h2, i2, j2, k2
                end)
                pc("S2", function()
                  local g = assert(io.open(path, "r"))
                  local a = g:seek("bad")
                  g:close()
                  return a
                end)
                pc("S3", function()
                  local g = assert(io.open(path, "r"))
                  local a = g:seek("set", "x")
                  g:close()
                  return a
                end)
                pc("S4", function()
                  local g = assert(io.open(path, "r"))
                  local a = g:seek(nil, 1)
                  g:close()
                  return a
                end)
                pc("S5", function()
                  local g = assert(io.open(path, "r"))
                  local a = g:seek(1)
                  g:close()
                  return a
                end)

                -- 6) lines
                pc("L1", function()
                  local n = 0
                  for l in io.lines(path) do n = n + 1 end
                  return n
                end)
                pc("L2", function()
                  local h = assert(io.open(path, "w"))
                  h:write("a\\nb\\nc\\n")
                  h:close()
                  local t = {}
                  for l in io.lines(path, "l") do t[#t + 1] = l end
                  return #t, t[1], t[2], t[3]
                end)
                pc("L3", function()
                  local f = assert(io.open(path, "r"))
                  local t = {}
                  for l in f:lines() do t[#t + 1] = l end
                  f:close()
                  return #t
                end)
                pc("L4", function()
                  local f = assert(io.open(path, "r"))
                  local n = 0
                  for l in f:lines("l", "l") do n = n + 1 end
                  f:close()
                  return n
                end)
                pc("L5", io.lines, "/nonexistent-dir-xyz/nope")

                -- 7) close / type / 关闭后再用
                pc("C1", function()
                  local f = assert(io.open(path, "r"))
                  local a = io.type(f)
                  local b = f:close()
                  local c = io.type(f)
                  local d = f:close()
                  return a, b, c, d
                end)
                pc("C2", function()
                  local f = assert(io.open(path, "r"))
                  f:close()
                  return f:read("l")
                end)
                pc("C3", function()
                  local f = assert(io.open(path, "r"))
                  f:close()
                  return f:write("x")
                end)
                pc("C4", function()
                  local f = assert(io.open(path, "r"))
                  f:close()
                  return f:seek()
                end)
                pc("C5", io.type, 5)
                pc("C6", io.type, "x")
                pc("C7", io.type, nil)
                pc("C8", io.type, {})

                -- 8) io.read / io.write / io.input / io.output
                pc("G1", function()
                  local old = io.output()
                  local w = io.output(path)
                  io.write("abc")
                  io.output(old)
                  io.output(w)
                  io.close(w)
                  local h = assert(io.open(path, "r"))
                  local s = h:read("a")
                  h:close()
                  return s
                end)
                pc("G2", function()
                  local old = io.input()
                  local f = assert(io.open(path, "w"))
                  f:write("z\\n")
                  f:close()
                  local r = io.input(path)
                  local s = io.read()
                  io.input(old)
                  io.close(r)
                  return s
                end)
                pc("G3", io.input, 5)
                pc("G4", io.input, "/nonexistent-dir-xyz/nope")
                pc("G5", io.output, 5)
                os.remove("5")
                io.output(io.stdout)
                pc("G6", io.close, 5)
                pc("G7", io.close, {})

                -- 9) 标准句柄
                print("H1", io.type(io.stdout), io.type(io.stdin), io.type(io.stderr))

                -- 10) 二进制与 \\0 / \\r\\n
                pc("B1", function()
                  local f = assert(io.open(path, "wb"))
                  f:write("a\\0b\\r\\nc\\n")
                  f:close()
                  local g = assert(io.open(path, "rb"))
                  local s = g:read("a")
                  g:close()
                  return #s, (string.gsub(s, ".", function(c)
                    return string.format("[%d]", string.byte(c))
                  end))
                end)
                pc("B2", function()
                  local f = assert(io.open(path, "wb"))
                  f:write("a\\0b\\r\\nc\\n")
                  f:close()
                  local g = assert(io.open(path, "r"))
                  local a, b, c = g:read("l", "l", "l")
                  g:close()
                  return a, b, c
                end)

                -- 11) flush / setvbuf / 长行
                pc("V1", function()
                  local f = assert(io.open(path, "w"))
                  local a = f:flush()
                  local b = f:setvbuf("no")
                  local c = f:setvbuf("full", 1024)
                  local d = f:setvbuf("line")
                  local e2 = f:setvbuf("bad")
                  f:close()
                  return a, b, c, d, e2
                end)

                -- 12) 追加模式的 seek 语义 / r+ 读写交错 / 大块 / 越界
                pc("A1", function()
                  local f = assert(io.open(path, "w"))
                  f:write("12345")
                  f:close()
                  local g = assert(io.open(path, "a"))
                  g:seek("set", 0)
                  g:write("X")
                  g:close()
                  local h = assert(io.open(path, "r"))
                  local s = h:read("a")
                  h:close()
                  return s
                end)
                pc("A2", function()
                  local f = assert(io.open(path, "w"))
                  f:write("abcdef")
                  f:close()
                  local g = assert(io.open(path, "r+"))
                  local a = g:read(2)
                  g:seek("set", 0)
                  g:write("Z")
                  g:seek("set", 0)
                  local b = g:read("a")
                  g:close()
                  return a, b
                end)
                pc("A3", function()
                  local big = string.rep("x", 5000)
                  local f = assert(io.open(path, "w"))
                  f:write(big)
                  f:close()
                  local g = assert(io.open(path, "r"))
                  local a = g:read(4096)
                  local b = g:read(4096)
                  local c = g:read(1)
                  g:close()
                  return #a, #b, c
                end)
                pc("A4", function()
                  local f = assert(io.open(path, "w"))
                  local a = f:read("l")
                  f:close()
                  return a
                end)
                pc("A5", function()
                  local f = assert(io.open(path, "w"))
                  f:write("1 2 3\\n4\\n")
                  f:close()
                  local t = {}
                  for a, b in io.lines(path, "n", "n") do t[#t + 1] = tostring(a) .. "/" .. tostring(b) end
                  return #t, table.concat(t, ",")
                end)
                pc("A6", function()
                  local f = assert(io.open(path, "w"))
                  f:write("a\\nb\\n")
                  f:close()
                  local t = {}
                  for l in io.lines(path, 1) do t[#t + 1] = l end
                  return #t, table.concat(t, "")
                end)
                pc("A7", function()
                  local f = assert(io.open(path, "w"))
                  f:write("a\\n\\nb\\n")
                  f:close()
                  local t = {}
                  for l in io.lines(path, "L") do t[#t + 1] = string.format("%q", l) end
                  return #t, table.concat(t, ",")
                end)
                pc("A8", function()
                  -- 同一文件开两次
                  local f = assert(io.open(path, "w"))
                  f:write("abc")
                  f:flush()
                  local g = assert(io.open(path, "r"))
                  local s = g:read("a")
                  g:close()
                  f:write("def")
                  f:close()
                  return s
                end)
                pc("A9", function()
                  local f = assert(io.open(path, "w"))
                  f:write("x")
                  f:close()
                  local g = assert(io.open(path, "r"))
                  g:close()
                  g:close()
                  return "ok"
                end)

                os.remove(path)
                os.remove("5")
                print("DONE")
                """);
    }

    /**
     * {@code debug} 库 + {@code load} + {@code require} + 错误与栈回溯矩阵，
     * 289 行。它咬出过一处真分歧：
     * <p>
     * <b>{@code xpcall} 没校验第二个参数是不是函数。</b> C 第一件事就是
     * {@code luaL_checktype(L, 2, LUA_TFUNCTION)}，而且发生在调用被调函数
     * <b>之前</b>。我们原先只判「参数够不够两个」，于是
     * {@code xpcall(f, 5)} 不报错，照常调 {@code f}；{@code f} 抛错后拿 5 当
     * 消息处理函数，结果是 {@code true, false, "error in error handling"}，
     * 而参考实现是
     * {@code false  bad argument #2 to 'xpcall' (function expected, got number)}。
     * 参数不足时同理：该报 {@code function expected, got no value}，
     * 不是含糊的 {@code value expected}。
     * <p>
     * 覆盖的面：{@code getinfo} 的 14 个字段 × 0–6 层、{@code getlocal/setlocal}
     * （含 vararg 与负下标）、upvalue 全家（含 {@code upvalueid}/
     * {@code upvaluejoin}）、{@code traceback}（含协程内）、{@code sethook}
     * 的 10 种掩码与计数钩子、{@code load} 的源码/读取函数/模式/env/二进制块、
     * {@code package.path}/{@code cpath}/{@code config}/{@code searchers} 与
     * {@code require} 的循环/抛错/返回 {@code false} 与 {@code nil}、
     * {@code error} 的 0–4 层与各种错误对象、{@code xpcall}/{@code assert}，
     * 以及错误在协程里的传播。
     * <p>
     * 两个坑写在脚本注释里，别再踩：
     * <ul>
     *   <li>{@code load(function() return 5 end)} <b>会挂死</b>：数字也算
     *       「字符串」，读取函数永不返回 {@code nil}，chunk 无限增长。
     *       参考实现实测挂了 4 分钟才被杀掉。要限次。</li>
     *   <li>函数/表的<b>地址</b>不能进输出，一律换成类型名。</li>
     * </ul>
     */
    @Test
    void debugLoadAndRequireMatrixMatchesReference() {
        Diff.assertFullMatches("""
                -- debug 库 / load / require / 错误与栈回溯 矩阵
                -- 函数、表、userdata 一律换成类型名，地址不参与比对。
                local function norm(v)
                  local t = type(v)
                  if t == "function" then return "<fn>" end
                  if t == "table" then return "<table>" end
                  if t == "userdata" then return "<ud>" end
                  if t == "thread" then return "<thread>" end
                  if t == "string" then return string.format("%q", v) end
                  return tostring(v)
                end
                local function pc(tag, f, ...)
                  local r = { pcall(f, ...) }
                  local out = { (r[1] and "true" or "false") }
                  for i = 2, #r do out[#out + 1] = norm(r[i]) end
                  print(tag, table.unpack(out, 1, #out))
                end

                -- 1) debug.getinfo：层级 × what
                local FIELDS = { "source", "short_src", "linedefined", "lastlinedefined",
                                 "what", "currentline", "nups", "nparams", "isvararg",
                                 "name", "namewhat", "istailcall", "activelines", "func" }
                for _, k in ipairs(FIELDS) do
                  for lvl = 0, 6 do
                    local ok, info = pcall(debug.getinfo, lvl, k)
                    if ok and info then
                      print("GI", k, lvl, norm(info[k]))
                    else
                      print("GI", k, lvl, ok and "nil" or tostring(info))
                    end
                  end
                end
                -- 全部字段一起取
                for lvl = 0, 4 do
                  local ok, info = pcall(debug.getinfo, lvl)
                  if ok and info then
                    local keys = {}
                    for k in pairs(info) do keys[#keys + 1] = k end
                    table.sort(keys)
                    local parts = {}
                    for _, k in ipairs(keys) do parts[#parts + 1] = k .. "=" .. norm(info[k]) end
                    print("GA", lvl, table.concat(parts, ","))
                  else
                    print("GA", lvl, ok and "nil" or tostring(info))
                  end
                end
                -- 传函数
                local function target() end
                local ok, info = pcall(debug.getinfo, target)
                print("GF", ok, ok and (info.what .. "/" .. info.nparams .. "/" .. tostring(info.isvararg)
                      .. "/" .. tostring(info.source) .. "/" .. tostring(info.linedefined)) or tostring(info))
                pc("GF2", debug.getinfo, print)
                print("GF3", pcall(debug.getinfo, 5))
                print("GF4", pcall(debug.getinfo, "x"))
                print("GF5", pcall(debug.getinfo, {}, "S"))
                print("GF6", pcall(debug.getinfo, 1, 5))
                print("GF7", pcall(debug.getinfo))

                -- 2) debug.getlocal / setlocal
                local function loc()
                  local a, b, c = 1, 2, 3
                  local t = {}
                  for i = 1, 5 do
                    local n, v = debug.getlocal(1, i)
                    t[#t + 1] = tostring(n) .. "=" .. norm(v)
                  end
                  print("GL", table.concat(t, ","))
                  debug.setlocal(1, 2, 99)
                  print("GL2", a, b, c)
                  print("GL3", debug.getlocal(1, 2))
                  print("GL4", pcall(debug.setlocal, 1, 99, 1))
                  print("GL5", pcall(debug.getlocal, 99, 1))
                  print("GL6", pcall(debug.getlocal, 1, -1))
                  print("GL7", pcall(debug.getlocal))
                  print("GL8", pcall(debug.getlocal, 1))
                end
                loc()
                local function vararg(...)
                  print("GV", debug.getlocal(1, 1), debug.getlocal(1, -1))
                end
                vararg(7, 8, 9)

                -- 3) upvalue
                local up1, up2 = "u1", "u2"
                local function useup()
                  return up1 .. up2
                end
                print("U1", debug.getupvalue(useup, 1))
                print("U2", debug.getupvalue(useup, 2))
                print("U3", debug.getupvalue(useup, 3))
                print("U4", debug.getupvalue(useup, 0))
                print("U5", debug.getupvalue(useup, -1))
                print("U6", pcall(debug.getupvalue, 5, 1))
                print("U7", pcall(debug.getupvalue, print, 1))
                print("U8", debug.setupvalue(useup, 1, "X"))
                print("U9", useup())
                print("U10", pcall(debug.setupvalue, useup, 99, "Y"))
                print("U11", pcall(debug.upvalueid, useup, 1) ~= nil)
                print("U12", pcall(debug.upvalueid, 5, 1))
                print("U13", pcall(debug.upvaluejoin, useup, 1, useup, 2))
                print("U14", useup())

                -- 4) metatable / registry / uservalue
                local mt = { __index = {} }
                local t = setmetatable({}, mt)
                print("M1", debug.getmetatable(t) == mt)
                print("M2", debug.setmetatable(t, nil) == t)
                print("M3", debug.getmetatable(t))
                print("M4", pcall(debug.getmetatable, 5))
                print("M5", pcall(debug.setmetatable, 5, {}))
                print("M6", type(debug.getregistry()) == "table")
                print("M7", pcall(debug.getuservalue, 5))
                print("M8", pcall(debug.setuservalue, 5, {}))
                print("M9", pcall(debug.setuservalue, {}, {}))

                -- 5) traceback
                local function scrub(s) return (s:gsub(":%d+:", ":N:")) end
                print("T1", scrub(debug.traceback()))
                print("T2", scrub(debug.traceback("msg")))
                print("T3", scrub(debug.traceback("msg", 2)))
                print("T4", scrub(debug.traceback("msg", 0)))
                print("T5", pcall(debug.traceback, 5))
                print("T6", scrub(debug.traceback(nil, 1)))
                local co = coroutine.create(function() print("TC", scrub(debug.traceback())) end)
                coroutine.resume(co)

                -- 6) sethook / gethook
                local events = {}
                local function hook(what, line)
                  events[#events + 1] = tostring(what) .. (line and ("@" .. line) or "")
                end
                local function work()
                  local x = 0
                  for i = 1, 6 do x = x + i end
                  return x
                end
                for _, mask in ipairs({ "l", "c", "r", "cr", "cl", "rl", "crl", "", "x", "lcr" }) do
                  events = {}
                  debug.sethook(hook, mask)
                  local ok2 = pcall(work)
                  debug.sethook()
                  print("HK", string.format("%q", mask), ok2, #events > 0 and events[1] or "-",
                        #events > 1 and events[2] or "-")
                end
                events = {}
                debug.sethook(hook, "", 3)
                pcall(work)
                debug.sethook()
                print("HKC", #events > 0 and events[1] or "-")
                print("HK2", debug.gethook())
                debug.sethook(hook, "l")
                print("HK3", debug.gethook() ~= nil, select("#", debug.gethook()))
                debug.sethook()
                print("HK4", debug.gethook())
                print("HK5", pcall(debug.sethook, 5, "l"))
                print("HK6", pcall(debug.sethook, hook, 5))
                print("HK7", pcall(debug.sethook, hook, "l", "x"))
                print("HK8", pcall(debug.sethook, hook, "l", -1))
                print("HK9", pcall(debug.sethook))

                -- 7) load：源码 / 读取函数 / 模式 / env / 二进制
                local SRC = { "return 1", "return", "x =", "return 1 +", "", "  ", "--c",
                              "return ...", "local x", "return 'a'" }
                for i = 1, #SRC do
                  pc(string.format("LD %q", SRC[i]), load, SRC[i])
                end
                pc("LD2", load, "return 1", "=name")
                pc("LD3", load, "return 1", "@file")
                pc("LD4", load, "return 1", "name")
                pc("LD5", load, "return 1", nil, "b")
                pc("LD6", load, "return 1", nil, "t")
                pc("LD7", load, "return 1", nil, "bt")
                pc("LD8", load, "return 1", nil, "z")
                pc("LD9", load, "return 1", nil, "x")
                pc("LD10", load, "return 1", nil, 5)
                local env = { print = print }
                local f = load("return X", "=e", "t", env)
                print("LD11", type(f))
                env.X = 42
                print("LD12", f and f())
                pc("LD13", load, "return X", "=e", "t", 5)
                local bin = string.dump(load("return 7"))
                pc("LD14", load, bin)
                pc("LD15", load, bin, "=b", "b")
                pc("LD16", load, bin, "=b", "t")
                pc("LD17", load, "\\27Lua junk")
                pc("LD18", load, "\\27")
                local pieces = { "return ", "1 +", " 2" }
                local idx = 0
                local f2 = load(function()
                  idx = idx + 1
                  return pieces[idx]
                end)
                print("LD19", type(f2), f2 and f2())
                local n3 = 0
                pc("LD20", load, function() n3 = n3 + 1; error("boom") end)
                pc("LD21", load, 5)
                -- 读取函数返回数字也算「字符串」，会一直喂下去；必须自己限次，否则 chunk
                -- 无限增长，参考实现直接卡死（实测挂了 4 分钟）。
                local n22 = 0
                print("LD22", pcall(load, function()
                  n22 = n22 + 1
                  if n22 > 2 then return nil end
                  return 5
                end))
                pc("LD23", load, function() return true end)
                pc("LD24", load, function() return {} end)

                -- 8) require / package
                print("P1", package.path)
                print("P2", package.cpath)
                print("P3", type(package.loaded), package.loaded.string ~= nil,
                      package.loaded.math ~= nil, package.loaded["nonexistent-mod"])
                print("P4", type(package.preload), type(package.searchers), #package.searchers)
                print("P5", package.config)
                print("P6", pcall(require, "nonexistent-mod-xyz"))
                print("P7", pcall(require))
                print("P8", pcall(require, 5))
                print("P9", pcall(require, true))
                package.preload["mymod"] = function(name) return { loaded = name } end
                local m = require("mymod")
                print("P10", type(m), m and m.loaded)
                print("P11", package.loaded["mymod"] == m, require("mymod") == m)
                package.loaded["mymod"] = nil
                print("P12", package.loaded["mymod"])
                package.preload["cyc"] = function() return require("cyc") end
                print("P13", pcall(require, "cyc"))
                package.preload["bad"] = function() error("mod boom") end
                print("P14", pcall(require, "bad"))
                print("P15", package.loaded["bad"])
                package.preload["f"] = function() return false end
                package.preload["n"] = function() return nil end
                print("P16", require("f"), package.loaded["f"])
                print("P17", require("n"), package.loaded["n"])

                -- 9) error / pcall / xpcall / assert
                for lvl = 0, 4 do
                  print("ER", lvl, select(2, pcall(function() error("e", lvl) end)))
                end
                pc("ER2", function() error({}) end)
                print("ER3", pcall(function() error(nil) end))
                print("ER4", pcall(function() error() end))
                print("ER5", pcall(function() error(setmetatable({}, { __tostring = function() return "TS" end })) end))
                print("ER6", pcall(function() error("x", "y") end))
                print("ER7", pcall(function() error("x", 1.5) end))
                print("XP1", xpcall(function() return 1 end, print, "a"))
                print("XP2", xpcall(function() error("E") end, function(mm) return "H:" .. mm end))
                print("XP3", pcall(xpcall, function() error("E") end, 5))
                print("XP4", xpcall(error, function(mm) return "H:" .. tostring(mm) end, "E"))
                print("XP5", pcall(xpcall))
                print("AS1", pcall(assert, false))
                print("AS2", pcall(assert, nil, "custom"))
                print("AS3", pcall(assert, false, 5))
                print("AS4", pcall(assert))
                print("AS5", assert(1, 2, 3))

                -- 10) 错误在协程里的传播
                local co2 = coroutine.create(function() error("inside") end)
                print("CE1", coroutine.resume(co2))
                print("CE2", coroutine.status(co2))
                local co3 = coroutine.wrap(function() error("wrapped") end)
                print("CE3", pcall(co3))
                local co4 = coroutine.create(function() coroutine.yield(1) error("after yield") end)
                print("CE4", coroutine.resume(co4))
                print("CE5", coroutine.resume(co4))
                print("CE6", coroutine.status(co4))

                print("DONE")
                """);
    }

    /**
     * 栈溢出的<b>深度</b>与<b>报错行号</b>。它咬出过一处真分歧，而且根因不在引擎：
     * <p>
     * <b>{@code lua.c: main} 给 {@code pmain} 传的是 {@code (argc, argv)} 两个实参，
     * 栈的初始 {@code top} 是 3。</b> {@code Diff} 只传了一个实参，于是整条栈比
     * 参考实现<b>少占一格</b>。少一格会一路带到底：脚本栈溢出时的递归深度差一层，
     * 最深那一帧也就换了个函数，报错行号随之不同。fuzz seed 7984 撞的正是这个
     * —— 参考报第 67 行，我们报第 65 行。
     * <p>
     * 之所以难查，是因为它<b>不是每次都差</b>：每帧开销是 {@code k+1}（{@code k}
     * 为局部变量个数），深度是 {@code floor((limit - base) / (k+1))}，
     * 基址差 1 只在刚好跨过整除边界时才改变结果。实测 0/1/2/6 个局部变量时差一层，
     * 3/4/8/16/32 个时不差 —— 挑一个例子来试的话会以为没问题。
     * 所以这片要按帧大小<b>铺开</b>量，不能只测一种。
     * <p>
     * 另一组（互相递归）测的是<b>报错行号</b>：{@code r7}/{@code r8} 交替递归，
     * 最深那一帧是谁，就决定报哪一行，深度差一层行号就换一行。
     * <p>
     * 顺带一个探针陷阱：{@code local function f() return f() end} 是<b>尾调用</b>，
     * 不占栈，会变成死循环而不是溢出（参考实现实测挂满 5 分钟）。
     * 这里所有递归都写成 {@code return 1 + f()} 这种非尾形式。
     */
    @Test
    void stackOverflowDepthAndLineMatchReference() {
        Diff.assertMatches("""

                local function mk(k)
                  local ls = {}
                  for i = 1, k do ls[#ls + 1] = "local v" .. i .. " = " .. i end
                  local src = "local n = 0\\nlocal function f()\\n" .. table.concat(ls, "\\n")
                    .. "\\nn = n + 1\\nreturn 1 + f()\\nend\\nlocal ok = pcall(f)\\nreturn n"
                  return load(src)
                end
                for _, k in ipairs({0,1,2,3,4,6,8,16,32}) do
                  print("k=" .. k, mk(k)())
                end
                """);
    }

    /** 同上，另一半：互相递归时报错落在哪一行。 */
    @Test
    void stackOverflowLineInMutualRecursionMatchesReference() {
        Diff.assertMatches("""
                -- 栈溢出的报错行号：错误发生在 OP_CALL 的 precall 里，
                -- C 在 precall 之前刚 savepc，所以行号应当是<b>调用点</b>那一行。
                -- 注意：这里所有递归都刻意写成<b>非尾调用</b>（return 1 + r() 之类），
                -- 尾调用不占栈，会变成死循环而不是溢出。
                local function show(tag, f)
                  local ok, e = pcall(f)
                  print(tag, ok, tostring(e):gsub("^.-:(%d+):", "L%1:"))
                end

                local function r1() return 1 + r1() end
                show("A", r1)

                local function r2()
                  for i = 1, 3 do
                    local x = r2()
                  end
                end
                show("B", r2)

                local function r3()
                  for _, v in ipairs({ 1, 2 }) do
                    local x = r3()
                  end
                end
                show("C", r3)

                local function r4()
                  local a = 1
                  local b = 2
                  if a < b then
                    local x = r4()
                  end
                end
                show("D", r4)

                local r7
                local function r8() return 1 + r7() end
                r7 = function() return 1 + r8() end
                show("G", r7)

                local function r9() return 1 + r9() end
                print("H", pcall(function() return r9() end))

                local rf
                rf = function() return 1 + rf() end
                show("I", rf)

                local function r10(n) return 1 + r10(n + 1) end
                show("J", function() return r10(1) end)

                -- 尾调用形态（不占栈，用别的方式逼出溢出）
                local function rt() return rt() end
                local guard = 0
                local function rt2()
                  guard = guard + 1
                  if guard > 100000 then error("give up") end
                  return rt2()
                end
                print("K", pcall(rt2), guard)
                """);
    }

    /**
     * {@code math} 全函数 × 30 个边界值（含 ±inf、nan、±0.0、maxinteger、
     * mininteger、2^53、2^63、1/3）+ 二元函数取样 + {@code modf} 的双返回值 +
     * {@code log} 的底 + 每个函数的六类参数错误 + 播种后的随机序列；外加
     * {@code os} 里输出确定的那部分（{@code getenv}、{@code remove}/
     * {@code rename} 的失败路径、{@code setlocale}、{@code difftime}、
     * {@code time} 的越界字段、{@code date}）。1423 行，<b>干净</b>。
     * <p>
     * 留着的三条理由：
     * <ul>
     *   <li><b>浮点最后一比特</b>：{@code math.sin} 等走的是 {@code libm}，
     *       我们的 {@code Math.sin} 与之未必逐比特一致。默认 {@code tostring}
     *       只打 14 位有效数字，正好看不见 —— 这片能证明确实看不见，
     *       而不是「恰好没测到」。</li>
     *   <li><b>{@code math.random} 的序列</b>：xoshiro256** 定死的序列是很硬的
     *       差分点，播种后必须逐个数对上。</li>
     *   <li><b>{@code errno}/{@code strerror} 那一层</b>：{@code os.remove} /
     *       {@code rename} 失败要带上一句 C 的错误文案。</li>
     * </ul>
     * 三处<b>不能测</b>的，别加回来：{@code os.clock}（耗时）、
     * 无参 {@code math.randomseed}（拿当前时间播种，两边不可能一致）、
     * 无参 {@code os.time}（当前时间）。
     */
    @Test
    void mathAndOsMatrixMatchesReference() {
        Diff.assertMatches("""
                -- math 全函数 × 边界值 矩阵；外加 os 里那些输出确定的部分
                -- os.clock（耗时）、os.execute 的退出码文案、os.time() 无参（当前时间）都不可比，不测。
                local VALS = {
                  -1 / 0, -1e308, -1e300, -2.5, -1.5, -1.0, -0.5, -0.0, 0, 0.0, 0.5, 1.0,
                  1.5, 2.0, 2.5, 3.0, 100.0, 1e300, 1e308, 1 / 0, 0 / 0,
                  math.maxinteger, math.mininteger, -math.maxinteger,
                  2 ^ 53, 2 ^ 63, -(2 ^ 63), 0.1, 1 / 3, math.pi, math.huge,
                }
                local NAMES = {}
                for i = 1, #VALS do
                  NAMES[i] = string.format("%.17g", VALS[i])
                end

                -- 1) 一元函数
                local UNARY = { "abs", "ceil", "floor", "sqrt", "sin", "cos", "tan", "asin",
                                "acos", "atan", "exp", "tointeger", "type" }
                for _, fn in ipairs(UNARY) do
                  for i = 1, #VALS do
                    local ok, r = pcall(math[fn], VALS[i])
                    if ok then
                      print("U", fn, NAMES[i], tostring(r), math.type(r))
                    else
                      print("U", fn, NAMES[i], "err", tostring(r))
                    end
                  end
                end

                -- 2) 二元函数
                local BINARY = { "fmod", "max", "min", "atan", "ult", "log" }
                for _, fn in ipairs(BINARY) do
                  for i = 1, #VALS do
                    for j = 1, #VALS do
                      if (i + j) % 7 == 0 then -- 全矩阵太大，取样
                        local ok, r = pcall(math[fn], VALS[i], VALS[j])
                        if ok then
                          print("B", fn, NAMES[i], NAMES[j], tostring(r), math.type(r))
                        else
                          print("B", fn, NAMES[i], NAMES[j], "err", tostring(r))
                        end
                      end
                    end
                  end
                end

                -- 3) modf 两个返回值
                for i = 1, #VALS do
                  local ok, a, b = pcall(math.modf, VALS[i])
                  if ok then
                    print("M", NAMES[i], tostring(a), math.type(a), tostring(b), math.type(b))
                  else
                    print("M", NAMES[i], "err", tostring(a))
                  end
                end

                -- 4) log 的第二个参数
                for _, b in ipairs({ 2, 10, 0.5, 1, 0, -1, 1 / 0 }) do
                  for _, x in ipairs({ 8, 100, 1, 0, -1, 1 / 0 }) do
                    print("L", b, x, tostring(select(2, pcall(math.log, x, b))))
                  end
                end

                -- 5) 参数类型错误
                -- math.atan 与 math.atan2 是同一个函数：报错里报哪个名字，
                -- 取决于 lauxlib.c: findfield 遍历 package.loaded 时先撞见谁，
                -- 而那个顺序由字符串哈希决定、哈希又被 luai_makeseed（按时间）
                -- 播种。参考实现自己连跑八次，六次报 atan2、两次报 atan ——
                -- 它是不确定的。所以这里先把名字统一成 atan 再比，
                -- 否则这条差分用例会随机翻红。
                local function fixname(s)
                  return (tostring(s):gsub("'math%.atan2'", "'math.atan'"))
                end
                for _, fn in ipairs({ "abs", "ceil", "floor", "sqrt", "sin", "exp", "tointeger",
                                      "fmod", "max", "min", "ult", "modf", "log", "atan" }) do
                  local function pe(...)
                    local ok, ev = pcall(math[fn], ...)
                    print("E", fn, ok, ok and tostring(ev) or fixname(ev))
                  end
                  pe() pe(nil) pe("x") pe("1")
                  local eok, ev = pcall(math[fn], {})
                  print("E", fn, eok, type(ev))
                  pe(true)
                end

                -- 6) 常量与 type
                print("C", math.pi, math.huge, math.maxinteger, math.mininteger)
                print("C2", math.type(1), math.type(1.0), math.type("1"), math.type(nil),
                      math.type(math.maxinteger), math.type(1 / 0))
                print("C3", pcall(math.type))
                print("C4", pcall(math.type, 1, 2))

                -- 7) random：播种后取整串
                math.randomseed(42)
                local rs = {}
                for i = 1, 10 do rs[#rs + 1] = tostring(math.random()) end
                print("R1", table.concat(rs, ","))
                local rs2 = {}
                for i = 1, 10 do rs2[#rs2 + 1] = tostring(math.random(100)) end
                print("R2", table.concat(rs2, ","))
                local rs3 = {}
                for i = 1, 10 do rs3[#rs3 + 1] = tostring(math.random(-5, 5)) end
                print("R3", table.concat(rs3, ","))
                math.randomseed(42)
                print("R4", math.random(), math.random(100))
                print("R5", pcall(math.random, 5, 1))
                print("R6", pcall(math.random, 1, 2, 3))
                print("R7", pcall(math.random, "x"))
                print("R8", pcall(math.random, 0))
                print("R9", pcall(math.random, math.mininteger, math.maxinteger))
                -- 无参 randomseed 拿当前时间播种，两边不可能一致，不测
                print("R11", pcall(math.randomseed, "x"))
                print("R12", pcall(math.randomseed, 1.5))

                -- 8) os：输出确定的那部分
                print("O1", type(os.getenv("PATH")), os.getenv("NO_SUCH_ENV_VAR_XYZ"))
                print("O2", pcall(os.getenv))
                print("O3", pcall(os.getenv, 5))
                print("O4", pcall(os.getenv, true))
                print("O5", os.remove("/nonexistent-dir-xyz/nope"))
                print("O6", os.rename("/nonexistent-dir-xyz/a", "/nonexistent-dir-xyz/b"))
                print("O7", pcall(os.remove))
                print("O8", pcall(os.rename, 5, 6))
                print("O9", tostring(os.setlocale()), tostring(os.setlocale("C")),
                      tostring(os.setlocale("no-such-locale-xyz")))
                print("O10", pcall(os.setlocale, 5))
                print("O11", os.difftime(100, 50), os.difftime(50, 100), os.difftime(0, 0))
                print("O12", pcall(os.difftime, "x", 1))
                print("O13", pcall(os.difftime, 1))
                print("O14", os.time({ year = 1970, month = 1, day = 1, hour = 0, min = 0, sec = 0 }))
                print("O15", os.time({ year = 2038, month = 1, day = 19, hour = 3, min = 14, sec = 8 }))
                print("O16", os.time({ year = 2020, month = 2, day = 29, hour = 12 }))
                print("O17", os.time({ year = 2021, month = 2, day = 29, hour = 12 }))
                print("O18", pcall(os.time, {}))
                print("O19", pcall(os.time, "x"))
                print("O20", pcall(os.time, 5))
                print("O21", os.time({ year = 2020, month = 13, day = 1 }))
                print("O22", os.time({ year = 2020, month = 1, day = 32 }))
                print("O23", os.time({ year = 2020, month = 1, day = 1, hour = 25 }))
                print("O24", os.time({ year = 2020, month = 1, day = 1, isdst = true }))
                print("O25", os.time({ year = 2020, month = 1, day = 1, sec = 60 }))
                print("O26", pcall(os.date, "%Y", os.time({ year = 2000, month = 6, day = 15, hour = 12 })))
                print("O27", os.date("!*t", 0).year, os.date("!*t", 0).yday, os.date("!*t", 0).wday)
                print("O28", pcall(os.date, "!%Y-%m-%d", 2147483647))
                print("O29", pcall(os.date, "!%Y-%m-%d", -1))
                print("O30", pcall(os.date, 5, 0))
                print("O31", pcall(os.date, "%Y", "x"))

                print("DONE")
                """);
    }
    /**
     * {@code string} 库<b>非模式匹配</b>那一半的系统矩阵——模式匹配的 pattern
     * 语义另有 {@code patternMatchingMatrixMatchesReference} 那 24819 行单独覆盖，
     * 这里只碰 {@code sub / byte / char / rep / upper / lower / len / reverse /
     * format / tonumber(base) / tostring}。
     * <p>
     * 咬出来两条：
     * <ul>
     *   <li><b>{@code __name} 走 C 字符串语义</b>：{@code lauxlib.c: luaL_tolstring}
     *       取到 {@code __name} 后用 {@code lua_tostring} 转成 {@code const char*}，
     *       再用 {@code "%s"} 拼进「名字: 0x地址」——于是名字在第一个 {@code \0}
     *       处被砍断。{@code __name = "A\0B"} 只打印出 {@code A}，我们把整个
     *       {@code "A B"} 都打了出去。</li>
     *   <li><b>{@code "%p"} 不补前导零</b>：地址写成了 {@code 0x%016x}，
     *       参考实现是 {@code 0x7ab30002c0} 这种不补零的样子。</li>
     * </ul>
     * 另有<b>一处不能比</b>的：{@code "+"}/空格标志作用在 NaN 上时，macOS 的 libc
     * 不打符号（{@code nan}）、glibc 打（{@code +nan}）。CI 的 conformance job
     * 在 ubuntu 上跑、本机在 macOS 上跑，两边的<b>参考实现自己</b>就不一样，
     * 所以脚本里先把 NaN 的符号抹掉再比。
     */
    @Test
    void stringLibraryMatrixMatchesReference() {
        Diff.assertMatches("""
                -- string 库非模式匹配部分矩阵：sub/byte/char/rep/upper/lower/len/reverse/
                -- format/tonumber(base)/tostring。模式匹配（find/match/gmatch/gsub 的 pattern
                -- 语义）另有 24819 行矩阵单独覆盖，这里只用 plain 参数。
                local S = {
                  "", "a", "abc", "abcdef", "Hello World", "  pad  ",
                  "\\0abc", "a\\0b", "\\0", "\\255\\254",
                  "0", "-1", "1", "  1  ", "0x10", "1e3", " 0x1p4 ", "10", "z", "Z", "0777",
                  "inf", "nan", "-inf", "true",
                  string.rep("x", 70),
                }
                -- tostring 出来的表/函数/线程带 0x 地址，两边不可能一致，统一抹掉
                local function norm(s)
                  return (tostring(s):gsub("0x%x+", "0xADDR"))
                end
                -- libc 分歧："+"/空格标志作用在 NaN 上时，macOS 的 libc 不打符号（"nan"），
                -- glibc 打（"+nan"）。CI 的 conformance job 在 ubuntu 上跑，本机在 macOS 上跑，
                -- 两边的参考实现自己就不一样，故统一成不带符号再比。
                local function fnorm(s)
                  return (s:gsub("^[+ ]nan$", "nan"):gsub("^[+ ]NAN$", "NAN"))
                end
                local function show(s)
                  -- 带 \\0 和非法 UTF-8 的字符串直接 print 会难读，转成十六进制再打
                  return (string.gsub(s, ".", function(c)
                    return string.format("\\\\%d", string.byte(c))
                  end))
                end
                for i = 1, #S do
                  print("S", i, show(S[i]))
                end
                
                -- 1) len / upper / lower / reverse
                for i = 1, #S do
                  print("L", i, #S[i], string.len(S[i]))
                  print("U", i, show(string.upper(S[i])), show(string.lower(S[i])))
                  print("V", i, show(string.reverse(S[i])))
                end
                -- len 的非字符串实参
                for _, v in ipairs({ nil, 5, 5.0, true, {}, print }) do
                  print("LE", type(v), pcall(string.len, v))
                end
                
                -- 2) sub 的 i/j 全边界交叉
                local IJ = { -100, -8, -3, -1, 0, 1, 2, 3, 8, 100 }
                for i = 1, #S do
                  for _, a in ipairs(IJ) do
                    for _, b in ipairs(IJ) do
                      print("B", i, a, b, show(string.sub(S[i], a, b)))
                    end
                  end
                end
                print("B2", pcall(string.sub, "abcdef")) -- 第二个参数是必填的：luaL_checkinteger
                print("B3", pcall(string.sub))
                print("B4", pcall(string.sub, nil, 1))
                print("B5", pcall(string.sub, "abc", "x"))
                print("B6", pcall(string.sub, "abc", 1, "x"))
                print("B7", pcall(string.sub, "abc", 1.5))
                print("B8", show(string.sub("abc", 1.0)), pcall(string.sub, "abc", 1.5))
                print("B9", show(string.sub("abc", 2.0, 3.0)))
                print("B10", pcall(string.sub, "abc", math.mininteger))
                print("B11", pcall(string.sub, "abc", 1, math.maxinteger))
                
                -- 3) byte / char
                for i = 1, #S do
                  for _, a in ipairs(IJ) do
                    print("Y", i, a, string.byte(S[i], a))
                    print("Y2", i, a, a + 2, string.byte(S[i], a, a + 2))
                  end
                end
                print("C", pcall(string.char))
                print("C2", show(string.char(65, 66, 0, 255)))
                print("C3", pcall(string.char, -1))
                print("C4", pcall(string.char, 256))
                print("C5", pcall(string.char, 1.5))
                print("C6", pcall(string.char, 1e300))
                print("C7", pcall(string.char, "x"))
                print("C8", #string.char(0, 0, 0))
                
                -- 4) rep
                for _, n in ipairs({ -2, -1, 0, 1, 3 }) do
                  for _, sep in ipairs({ nil, "", "-", "ab" }) do
                    print("R", n, tostring(sep), pcall(string.rep, "ab", n, sep))
                  end
                end
                print("R2", pcall(string.rep, "ab", 1e300))
                print("R3", pcall(string.rep, "ab", "3"))
                print("R4", pcall(string.rep))
                print("R5", pcall(string.rep, "ab", 2, 5))
                print("R6", #string.rep("ab", 0, "xyz"))
                
                -- 5) format：动词 × 值 × 修饰
                local FMT = {
                  "%d", "%5d", "%-5d|", "%05d", "%+d", "% d", "%.0d", "%i",
                  "%x", "%X", "%#x", "%o", "%#o", "%c",
                  "%f", "%.3f", "%10.2f", "%-10.2f|", "%+f", "%e", "%E", "%.3e",
                  "%g", "%G", "%.0g", "%#g", "%a", "%A",
                  "%s", "%10s", "%-10s|", "%.2s", "%q", "%%", "%5.2q",
                }
                local FV = {
                  0, 1, -1, 255, math.maxinteger, math.mininteger,
                  0.0, -0.0, 1 / 3, 1e300, 1 / 0, -1 / 0, 0 / 0,
                  "str", "", "a\\0b", true, false, nil,
                }
                for _, f in ipairs(FMT) do
                  for i = 1, #FV do
                    local ok, r = pcall(string.format, f, FV[i])
                    print("F", f, i, ok, ok and show(fnorm(r)) or tostring(r))
                  end
                end
                -- format 的多参数与缺参
                print("F2", pcall(string.format, "%d %s", 1, "a"))
                print("F3", pcall(string.format, "%d %d", 1))
                print("F4", pcall(string.format, "%d", "x"))
                print("F5", pcall(string.format, "%d", 1.5))
                print("F6", select(1, pcall(string.format, "%s", {})),
                      norm(select(2, pcall(string.format, "%s", {}))))
                print("F7", pcall(string.format))
                print("F8", pcall(string.format, "%z", 1))
                print("F9", pcall(string.format, "%1000000d", 1))
                print("F10", string.format("%s", setmetatable({}, { __tostring = function() return "TT" end })))
                print("F11", pcall(string.format, "%s", setmetatable({}, { __tostring = function() return 5 end })))
                print("F12", pcall(string.format, "%s", setmetatable({}, { __tostring = function() error("boom") end })))
                print("F13", string.format("%q", "a\\nb\\\\\\"c\\0d"))
                print("F14", string.format("%q", "\\r\\t\\1\\255"))
                print("F15", string.format("%q", 1), string.format("%q", 1.0), string.format("%q", 1 / 3))
                print("F16", pcall(string.format, "%q", nil), pcall(string.format, "%q", true))
                print("F17", string.format("%d%%%s", 1, "x"))
                
                -- 6) tonumber 带基数
                local TN = { "0", "1", "10", "z", "Z", "-1", "-z", "ff", "FF", "  ff  ",
                             "", " ", "0x10", "1e3", "1.5", "- 1", "+1", "1 2", "\\0", "0777" }
                for _, s in ipairs(TN) do
                  for _, b in ipairs({ nil, 1, 2, 8, 10, 16, 36, 37 }) do
                    print("T", show(s), tostring(b), pcall(tonumber, s, b))
                  end
                end
                print("T2", tonumber("10", 2), tonumber("z", 36), tonumber("Z", 36))
                print("T3", pcall(tonumber, 5, 10))
                print("T4", tonumber(5), tonumber("5"), math.type(tonumber("5")))
                print("T5", tonumber("0x10"), tonumber("  0x10  "))
                print("T6", tonumber("1e2"), math.type(tonumber("1e2")))
                print("T7", tonumber(1 / 0), tonumber(0 / 0))
                print("T8", pcall(tonumber, "10", 1.5))
                print("T9", pcall(tonumber, "10", "16"))
                print("T10", pcall(tonumber, "10", 0))
                print("T11", pcall(tonumber, "10", -1))
                print("T12", pcall(tonumber, "10", 38))
                print("T13", tonumber("9223372036854775807"), math.type(tonumber("9223372036854775807")))
                print("T14", tonumber("9223372036854775808"), math.type(tonumber("9223372036854775808")))
                print("T15", tonumber("-9223372036854775808"), math.type(tonumber("-9223372036854775808")))
                print("T16", tonumber("-9223372036854775809"), math.type(tonumber("-9223372036854775809")))
                
                -- 7) tostring / __name / __metatable
                print("TS", tostring(nil), tostring(true), tostring(1), tostring(1.0), tostring("s"))
                local mt = { __name = "MyType" }
                print("TS2", norm(tostring(setmetatable({}, mt))))
                print("TS3", select(1, pcall(tostring, setmetatable({}, { __name = 5 }))),
                      norm(select(2, pcall(tostring, setmetatable({}, { __name = 5 })))))
                print("TS4", norm(tostring(setmetatable({}, { __name = "A\\0B" }))))
                local mtr = { __tostring = function() return "RR" end }
                print("TS5", tostring(setmetatable({}, mtr)))
                print("TS6", pcall(tostring, setmetatable({}, { __tostring = function() return nil end })))
                print("TS7", pcall(tostring, setmetatable({}, { __tostring = function() return {} end })))
                print("TS8", pcall(tostring, setmetatable({}, { __tostring = 5 })))
                print("TS9", norm(tostring(print)), type(tostring(print)))
                print("TS10", norm(tostring(coroutine.create(function() end))))
                print("TS11", norm(tostring({})), norm(tostring(io.stdout)))
                
                print("DONE")
                """);
    }
    /**
     * 元方法语义矩阵 + 报错里的<b>变量来源推断</b>（{@code ldebug.c: varinfo} 给
     * 的 {@code local 'x'} / {@code global 'g'} / {@code field 'f'} /
     * {@code upvalue 'u'} / {@code method 'm'} 后缀），外加一片<b>参数缺失</b>
     * 矩阵：六十来个库函数各用「不传 / nil / 数字 / 字符串 / 表 / 布尔」调一遍。
     * 共 {@code N} 行。
     * <p>
     * 参数缺失那一片咬出来的东西最多，根子是同一个：
     * <ul>
     *   <li><b>{@code LuaState.arg} 越界读的是残值。</b>C 里 {@code lua_type}
     *       对越界下标返回 {@code LUA_TNONE}、取值拿到 nil；我们的栈是复用数组，
     *       越界读出来的是上一帧留下的东西 —— 于是 {@code pcall(setmetatable)}
     *       有时报 {@code got function}、有时 {@code got nil}，
     *       {@code assert()} 甚至会因为残值为真而返回 0 个值。<b>随机翻红</b>的
     *       用例最难查，这种就是。</li>
     *   <li><b>缺参要报 {@code no value}，不是 {@code nil}</b>：{@code rawlen}、
     *       {@code rawequal}、{@code rawget}、{@code rawset}、
     *       {@code setmetatable}、五个 {@code coroutine.*} 全都错成
     *       {@code nil} 或干脆不报错。</li>
     *   <li><b>{@code rawequal} 漏了两遍 {@code luaL_checkany}</b>：
     *       {@code rawequal(1)} 本该报
     *       {@code bad argument #2 ... (value expected)}，我们返回
     *       {@code true true}。</li>
     *   <li><b>{@code pairs} / {@code ipairs} 不要求是表</b>：C 只做
     *       {@code luaL_checkany}，{@code pairs(5)} 照样返回三个值，真正炸是在
     *       迭代时 {@code next(5)}。我们多加了「必须是表」，把它变成报错。</li>
     *   <li><b>{@code select} 的 {@code #} 只比第一个字节</b>：C 是
     *       {@code *lua_tostring(L, 1) == '#'}，所以 {@code select("#abc", 1)}
     *       也是「数个数」；判断整串等于 {@code "#"} 就漏了。另外缺参报的是
     *       {@code number expected, got no value}，不是 {@code value expected}。</li>
     *   <li><b>{@code rawget} 没有「键不能是 nil」那条</b>：{@code rawget({}, nil)}
     *       是 {@code true nil}。报「table index is nil」的是 {@code rawset}，
     *       那条来自 {@code ltable.c: luaH_newkey}。</li>
     *   <li><b>{@code setmetatable} 的第二个参数</b>走 {@code luaL_argexpected}，
     *       必须带 {@code got <实际类型>}；且缺参是 {@code TNONE}，不属于
     *       {nil, table}，所以 {@code setmetatable({})} 要报错。</li>
     * </ul>
     */
    @Test
    void metamethodsAndArgPresenceMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 元方法语义矩阵 + 报错里的变量来源推断（luaG_typeerror / varinfo）
                -- 报错文案来自 ldblib 之外的地方：lvm.c: luaT_trybinTM 抛的
                -- "attempt to perform arithmetic on a X value"，以及 luaG_runerror 那句
                -- "attempt to ... "。名字后缀（local 'x' / global 'g' / field 'f' /
                -- upvalue 'u' / method 'm' / constant 'k'）由 ldebug.c: varinfo 推断。
                local function p(...)
                  print(...)
                end
                -- tostring 出来的表/函数/线程带 0x 地址，两边不可能一致，统一抹掉
                local function norm(s)
                  return (tostring(s):gsub("0x%x+", "0xADDR"))
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local ok = r[1]
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return ok, table.concat(t, "\\t")
                end
                
                -- 1) 算术元方法：全部二元操作符 × 全部类型组合
                local VALS = { nil, true, false, 1, 2.5, "s", "10", {}, print }
                local OPS = {
                  { "+", function(a, b) return a + b end },
                  { "-", function(a, b) return a - b end },
                  { "*", function(a, b) return a * b end },
                  { "/", function(a, b) return a / b end },
                  { "//", function(a, b) return a // b end },
                  { "%", function(a, b) return a % b end },
                  { "^", function(a, b) return a ^ b end },
                  { "&", function(a, b) return a & b end },
                  { "|", function(a, b) return a | b end },
                  { "~", function(a, b) return a ~ b end },
                  { "<<", function(a, b) return a << b end },
                  { ">>", function(a, b) return a >> b end },
                  { "..", function(a, b) return a .. b end },
                  { "==", function(a, b) return a == b end },
                  { "<", function(a, b) return a < b end },
                  { "<=", function(a, b) return a <= b end },
                  { ">", function(a, b) return a > b end },
                  { ">=", function(a, b) return a >= b end },
                  { "~=", function(a, b) return a ~= b end },
                }
                for _, op in ipairs(OPS) do
                  for i = 1, #VALS do
                    for j = 1, #VALS do
                      local ok, r = pc(op[2], VALS[i], VALS[j])
                      p("O", op[1], i, j, ok, r)
                    end
                  end
                end
                
                -- 2) 一元
                local UN = {
                  { "-", function(a) return -a end },
                  { "#", function(a) return #a end },
                  { "~", function(a) return ~a end },
                  { "not", function(a) return not a end },
                }
                for _, op in ipairs(UN) do
                  for i = 1, #VALS do
                    local ok, r = pc(op[2], VALS[i])
                    p("N", op[1], i, ok, r)
                  end
                end
                
                -- 3) 变量来源推断：同一个错误在不同上下文里名字后缀不同
                G = nil
                local function probe(label, f)
                  local ok, r = pc(f)
                  p("V", label, ok, r)
                end
                -- global
                probe("global-arith", function() return nilG + 1 end)
                probe("global-index", function() return nilG.x end)
                probe("global-newindex", function() nilG.x = 1 end)
                probe("global-call", function() return nilG() end)
                probe("global-concat", function() return nilG .. "x" end)
                probe("global-len", function() return #nilG end)
                probe("global-compare", function() return nilG < 1 end)
                -- local
                probe("local-arith", function() local loc = nil return loc + 1 end)
                probe("local-index", function() local loc = nil return loc.x end)
                probe("local-call", function() local loc = nil return loc() end)
                probe("local-concat", function() local loc = nil return loc .. "x" end)
                probe("local-len", function() local loc = nil return #loc end)
                probe("local-name-num", function() local n1, n2 = nil, nil return n2 + 1 end)
                -- upvalue
                do
                  local up = nil
                  probe("upvalue-arith", function() return up + 1 end)
                  probe("upvalue-index", function() return up.x end)
                  probe("upvalue-call", function() return up() end)
                end
                -- field（表里的键）
                do
                  local t = { fld = nil }
                  probe("field-arith", function() return t.fld + 1 end)
                  probe("field-index", function() return t.fld.x end)
                  probe("field-call", function() return t.fld() end)
                  probe("field-missing-arith", function() return t.nope + 1 end)
                  probe("field-missing-call", function() return t.nope() end)
                  probe("table-arith", function() return t + 1 end)
                  probe("table-call", function() return t() end)
                  probe("table-len", function() return #t end)
                  probe("table-index-num", function() return t[1].x end)
                end
                -- method
                do
                  local t = {}
                  probe("method-call", function() return t:m() end)
                  probe("method-on-nil", function() local z = nil return z:m() end)
                  probe("method-on-str", function() return ("s"):m() end)
                  probe("method-on-num", function() local nn = 1 return nn:m() end)
                  probe("method-on-bool", function() local bb = true return bb:m() end)
                end
                -- constant / 临时值
                probe("const-arith", function() return (nil) + 1 end)
                probe("str-arith", function() return "abc" + 1 end)
                probe("bool-arith", function() return true + 1 end)
                probe("num-index", function() return (1).x end)
                probe("num-call", function() return (1)() end)
                probe("bool-index", function() return (true).x end)
                probe("bool-call", function() return (true)() end)
                -- main chunk 里的 self / vararg
                probe("vararg-arith", function(...) return ... + 1 end)
                probe("vararg-call", function(...) return (...)() end)
                
                -- 4) 元方法被调用于各种场合
                local function mkmt(fields)
                  return setmetatable({}, fields)
                end
                local mtadd = mkmt({ __add = function(a, b) return "ADD" end })
                p("M1", mtadd + 1, 1 + mtadd, mtadd + mtadd)
                local mtconcat = mkmt({ __concat = function(a, b) return "CC" end })
                p("M2", mtconcat .. "x", "x" .. mtconcat, mtconcat .. mtconcat)
                local mtlen = mkmt({ __len = function() return 42 end })
                p("M3", #mtlen, rawlen(mtlen))
                p("M4", pcall(function() return #mkmt({ __len = function() return "x" end }) end))
                p("M5", pcall(function() return #mkmt({ __len = 5 }) end))
                p("M6", pcall(function() return #mkmt({ __len = function() end }) end))
                local mteq = mkmt({ __eq = function() return true end })
                local mteq2 = mkmt({ __eq = function() return true end })
                p("M7", mteq == mteq2, mteq ~= mteq2, mteq == mteq, rawequal(mteq, mteq2))
                p("M8", mteq == {}, {} == mteq, mteq == 1)
                p("M9", pcall(function() return mkmt({ __eq = function() return nil end })
                  == mkmt({ __eq = function() return nil end }) end))
                local mtlt = mkmt({ __lt = function() return true end })
                local mtlt2 = mkmt({ __lt = function() return false end })
                p("M10", mtlt < mtlt2, mtlt2 < mtlt, mtlt > mtlt2, mtlt <= mtlt2)
                p("M11", pcall(function() return mkmt({ __le = function() return 1 end })
                  <= mkmt({ __le = function() return 1 end }) end))
                p("M12", pcall(function() return mkmt({ __lt = function() return 5 end })
                  < mkmt({ __lt = function() return 5 end }) end))
                local mtcall = mkmt({ __call = function(self, ...) return "CALL", ... end })
                p("M13", mtcall(), mtcall(1, 2), select(2, mtcall(1)))
                p("M14", pcall(setmetatable({}, { __call = 5 })))
                local mtindex = mkmt({ __index = function(t, k) return "I" .. tostring(k) end })
                p("M15", mtindex.x, mtindex[1], rawget(mtindex, "x"))
                local prototab = { proto = 1 }
                local mtproto = setmetatable({}, { __index = prototab })
                p("M16", mtproto.proto, mtproto.nope, rawget(mtproto, "proto"))
                p("M17", pcall(function() return setmetatable({}, { __index = 5 }).x end))
                p("M18", pcall(function() return setmetatable({}, { __index = "s" }).x end))
                local mtnewindex = mkmt({ __newindex = function(t, k, v) rawset(t, "seen", k) end })
                mtnewindex.x = 1
                p("M19", mtnewindex.seen, rawget(mtnewindex, "x"))
                p("M20", pcall(function() setmetatable({}, { __newindex = 5 }).x = 1 end))
                local shallow = setmetatable({}, { __newindex = prototab })
                shallow.y = 9
                p("M21", prototab.y, shallow.y)
                local mtunm = mkmt({ __unm = function() return "UNM" end })
                p("M22", -mtunm)
                local mtidiv = mkmt({ __idiv = function() return "ID" end })
                p("M23", mtidiv // 1)
                local mtmod = mkmt({ __mod = function() return "MOD" end })
                p("M24", mtmod % 1)
                local mtpow = mkmt({ __pow = function() return "POW" end })
                p("M25", mtpow ^ 1)
                local mtband = mkmt({ __band = function() return "BAND" end })
                p("M26", mtband & 1)
                local mtbnot = mkmt({ __bnot = function() return "BNOT" end })
                p("M27", ~mtbnot)
                local mtshl = mkmt({ __shl = function() return "SHL" end })
                p("M28", mtshl << 1)
                -- 元方法链：__index 指向的表自己也有元表
                local lvl3 = { deep = 3 }
                local lvl2 = setmetatable({}, { __index = lvl3 })
                local lvl1 = setmetatable({}, { __index = lvl2 })
                p("M29", lvl1.deep, lvl1.nope)
                -- __index 循环
                local a, b = {}, {}
                setmetatable(a, { __index = b })
                setmetatable(b, { __index = a })
                p("M30", pcall(function() return a.x end))
                -- 元方法自身报错
                local mtboom = mkmt({ __add = function() error("add-boom") end })
                p("M31", pcall(function() return mtboom + 1 end))
                local mtboom2 = mkmt({ __index = function() error("idx-boom") end })
                p("M32", pcall(function() return mtboom2.x end))
                -- 元方法不是函数但可调（带 __call 的表）
                local callable = setmetatable({}, { __call = function() return "CF" end })
                p("M33", setmetatable({}, { __add = callable }) + 1)
                p("M34", setmetatable({}, { __index = callable }).x)
                
                -- 5) 元表本身的读写与保护
                p("T1", type(getmetatable("")), getmetatable(1), getmetatable(nil),
                  getmetatable(true), getmetatable(print))
                p("T2", type(getmetatable("")), getmetatable("").__index == string)
                local guard = setmetatable({}, { __metatable = "LOCKED" })
                p("T3", getmetatable(guard), pcall(setmetatable, guard, {}))
                p("T4", pcall(setmetatable, guard, nil))
                p("T5", tostring(guard):sub(1, 7))
                p("T6", pcall(setmetatable, 5, {}))
                p("T7", pcall(setmetatable, {}, 5))
                p("T8", pcall(setmetatable))
                p("T9", getmetatable(setmetatable({}, {})) == nil)
                do
                  local t = {}
                  local m = {}
                  p("T10", setmetatable(t, m) == t, getmetatable(t) == m)
                  setmetatable(t, nil)
                  p("T11", getmetatable(t))
                end
                p("T12", pcall(getmetatable))
                p("T13", pcall(getmetatable, 5))
                p("T14", pcall(rawget, 5, 1), pcall(rawget, {}, 1), pcall(rawget))
                p("T15", pcall(rawequal, 1, 1), pcall(rawequal, 1), pcall(rawequal))
                p("T16", pcall(rawlen, 5), pcall(rawlen, "abc"), pcall(rawlen))
                p("T17", pcall(rawset, 5, 1, 1), pcall(rawset, {}, nil, 1))
                do
                  local t = {}
                  p("T18", pcall(rawset, t, 0 / 0, 1))
                end
                -- 数字/字符串共享元表？
                p("T19", getmetatable(1) == getmetatable(2), getmetatable("a") == getmetatable("b"))
                -- 修改 string 元表会影响所有字符串
                do
                  local smt = getmetatable("")
                  local old = smt.__index
                  p("T20", type(old))
                end
                
                -- 6) 参数缺失矩阵：每个函数用 0/1 个（故意错类型的）实参各调一次
                -- 「一个参数也没传」在 C 里是 LUA_TNONE，报错文案是 "no value"；
                -- 传了 nil 才是 "nil"。两者混掉就看不出缺参校验到底有没有做。
                local FNS = {
                  { "type", type }, { "tostring", tostring }, { "tonumber", tonumber },
                  { "rawget", rawget }, { "rawset", rawset }, { "rawlen", rawlen },
                  { "rawequal", rawequal }, { "next", next }, { "pairs", pairs },
                  { "ipairs", ipairs }, { "select", select }, { "assert", assert },
                  { "pcall", pcall }, { "error", error }, { "unpack", table.unpack },
                  { "setmetatable", setmetatable }, { "getmetatable", getmetatable },
                  { "collectgarbage", collectgarbage },
                  { "coroutine.create", coroutine.create }, { "coroutine.resume", coroutine.resume },
                  { "coroutine.status", coroutine.status }, { "coroutine.wrap", coroutine.wrap },
                  { "coroutine.isyieldable", coroutine.isyieldable },
                  { "coroutine.close", coroutine.close }, { "coroutine.running", coroutine.running },
                  { "string.len", string.len }, { "string.sub", string.sub },
                  { "string.rep", string.rep }, { "string.format", string.format },
                  { "string.byte", string.byte }, { "string.char", string.char },
                  { "table.insert", table.insert }, { "table.remove", table.remove },
                  { "table.concat", table.concat }, { "table.sort", table.sort },
                  { "table.move", table.move }, { "table.unpack", table.unpack },
                  { "math.abs", math.abs }, { "math.fmod", math.fmod }, { "math.max", math.max },
                  { "math.floor", math.floor }, { "math.tointeger", math.tointeger },
                  -- 这四条的第 1、2 组实参（缺参 / nil）会落到「拿当前时间」，两边不可能一致：
                  -- math.random 的序列取决于此前有没有播种，math.randomseed() 与 os.time()、
                  -- os.date() 无参都是读时钟。只比 3~6 组（错类型的实参）。
                  -- math.random 整个不测：Lua 5.4 建 state 时就拿时间+地址播种了，播种前的
                  -- 序列两边不可能一致。定死的那串序列在 math/os 矩阵里用 randomseed(42) 比。
                  { "math.randomseed", math.randomseed, { 1, 2 } },
                  { "os.time", os.time, { 1, 2 } }, { "os.date", os.date, { 1, 2 } },
                  { "os.difftime", os.difftime },
                  { "os.getenv", os.getenv }, { "os.remove", os.remove }, { "os.rename", os.rename },
                  { "io.open", io.open }, { "io.lines", io.lines },
                  { "debug.getinfo", debug.getinfo }, { "debug.getlocal", debug.getlocal },
                  { "debug.sethook", debug.sethook },
                  -- debug.traceback 不能进这张表：它的返回值<b>就是</b>回溯，打印出来会落在这段
                  -- 输出的中间，而 assertMatches 会从回溯处把后面全砍掉（那道门禁正是为此设的）。
                  -- 回溯相关的语义另有 debug/load/require 那片矩阵用 assertFullMatches 覆盖。
                  { "utf8.char", utf8.char }, { "utf8.len", utf8.len }, { "utf8.codepoint", utf8.codepoint },
                }
                local ARGS = { {}, { nil }, { 5 }, { "x" }, { {} }, { true } }
                for _, e in ipairs(FNS) do
                  local name, fn = e[1], e[2]
                  local skip = e[3] or {}
                  for i = 1, #ARGS do
                    if not skip[i] then
                      local r = { pcall(fn, table.unpack(ARGS[i], 1, #ARGS[i])) }
                      local ok = r[1]
                      local t = {}
                      for j = 2, #r do t[#t + 1] = tostring(r[j]) end
                      p("A", name, i, ok, norm(table.concat(t, "\\t")))
                    end
                  end
                end
                
                print("DONE")
                """);
    }
    /**
     * <b>可选参数</b>矩阵：上一片只试了「0 个 / 1 个实参」，这一片把第 2、第 3 个
     * 参数「给不给、给什么」也做全排列 —— 七十来个函数，按
     * {@code {nil, 1, "x"}} 的所有组合从 0 个实参一直喂到各函数的形参上限。
     * 共 {@code N} 行，专抓 {@code luaL_opt*} 的默认值与<b>多道校验的先后</b>。
     * <p>
     * 咬出两条，都是「校验顺序写反」：
     * <ul>
     *   <li><b>{@code string.gsub}</b>：C 里 {@code luaL_optinteger(L, 4, ...)}
     *       在校验第 3 个参数<b>之前</b>，所以 {@code gsub(s, p, nil, "x")} 报的是
     *       {@code bad argument #4 (number expected, got string)}，而不是
     *       「第三个参数不是 string/function/table」。我们反过来了。</li>
     *   <li><b>{@code tonumber}</b>：三道校验的顺序是
     *       {@code luaL_checkinteger(2)} → {@code luaL_checktype(1, STRING)} →
     *       {@code luaL_argcheck(2 <= base <= 36)}，<b>范围检查在最后</b>。
     *       于是 {@code tonumber(1, 1)} 报
     *       {@code bad argument #1 (string expected, got number)}，
     *       我们报成了 {@code bad argument #2 (base out of range)}。</li>
     * </ul>
     * 三个函数不在这张表里：{@code os.time} / {@code os.date} 第一个参数为
     * {@code nil} 时读的是时钟，{@code math.randomseed(nil)} 拿时钟播种，
     * {@code io.lines} 无参会去读 stdin。
     */
    @Test
    void optionalArgumentMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 可选参数矩阵：上一片只试了「0 个 / 1 个实参」，这一片把第 2、第 3 个参数
                -- 「给不给、给什么」也铺开 —— 每个函数按 {nil, 1, "x"} 的全排列调用到 maxn 个
                -- 实参。专抓「可选参数的边界」：luaL_opt* 的默认值、缺参报错、类型文案。
                local POOL = { nil, 1, "x" }
                local NAMES = { "nil", "1", "x" }
                -- 表/函数/线程的 tostring 带地址，两边不可能一致，只打类型
                local function s(v)
                  local t = type(v)
                  if t == "table" or t == "function" or t == "thread" or t == "userdata" then
                    return t
                  end
                  return tostring(v)
                end
                -- 不进这张表的：os.time / os.date / math.randomseed —— 它们第一个参数是 nil
                -- 时读的是时钟（os.time(nil)、os.date(nil) 取当前时间；randomseed(nil) 拿
                -- 时钟播种）；io.lines 无参会去读 stdin，跑在探针里会卡住。
                local FNS = {
                  { "rawget", rawget, 2 }, { "rawset", rawset, 3 }, { "rawlen", rawlen, 1 },
                  { "rawequal", rawequal, 2 }, { "next", next, 2 },
                  { "setmetatable", setmetatable, 2 }, { "getmetatable", getmetatable, 1 },
                  { "type", type, 1 }, { "tostring", tostring, 1 }, { "tonumber", tonumber, 2 },
                  { "select", select, 3 }, { "assert", assert, 2 }, { "pcall", pcall, 2 },
                  { "error", error, 2 }, { "ipairs", ipairs, 1 }, { "pairs", pairs, 1 },
                  { "table.insert", table.insert, 3 }, { "table.remove", table.remove, 2 },
                  { "table.concat", table.concat, 4 }, { "table.move", table.move, 5 },
                  { "table.unpack", table.unpack, 3 }, { "table.pack", table.pack, 3 },
                  { "table.sort", table.sort, 2 },
                  { "string.len", string.len, 1 }, { "string.sub", string.sub, 3 },
                  { "string.rep", string.rep, 3 }, { "string.byte", string.byte, 3 },
                  { "string.char", string.char, 3 }, { "string.upper", string.upper, 1 },
                  { "string.reverse", string.reverse, 1 }, { "string.format", string.format, 3 },
                  { "string.find", string.find, 4 }, { "string.match", string.match, 3 },
                  { "string.gmatch", string.gmatch, 2 }, { "string.gsub", string.gsub, 4 },
                  { "string.dump", string.dump, 2 },
                  { "math.max", math.max, 3 }, { "math.min", math.min, 3 },
                  { "math.fmod", math.fmod, 2 }, { "math.log", math.log, 2 },
                  { "math.floor", math.floor, 1 }, { "math.ceil", math.ceil, 1 },
                  { "math.tointeger", math.tointeger, 1 }, { "math.type", math.type, 1 },
                  { "math.abs", math.abs, 1 }, { "math.sqrt", math.sqrt, 1 },
                  { "math.ult", math.ult, 2 }, { "math.modf", math.modf, 1 },
                  { "os.difftime", os.difftime, 2 }, { "os.setlocale", os.setlocale, 2 },
                  { "os.getenv", os.getenv, 1 }, { "os.remove", os.remove, 1 },
                  { "os.rename", os.rename, 2 },
                  { "io.open", io.open, 2 },
                  { "coroutine.resume", coroutine.resume, 3 }, { "coroutine.wrap", coroutine.wrap, 1 },
                  { "coroutine.close", coroutine.close, 1 }, { "coroutine.status", coroutine.status, 1 },
                  { "coroutine.create", coroutine.create, 1 },
                  { "debug.getlocal", debug.getlocal, 3 }, { "debug.setmetatable", debug.setmetatable, 2 },
                  { "debug.getmetatable", debug.getmetatable, 1 }, { "debug.getuservalue", debug.getuservalue, 2 },
                  { "debug.setuservalue", debug.setuservalue, 2 },
                  { "utf8.char", utf8.char, 3 }, { "utf8.codepoint", utf8.codepoint, 3 },
                  { "utf8.len", utf8.len, 3 }, { "utf8.offset", utf8.offset, 3 },
                }
                for _, e in ipairs(FNS) do
                  local name, fn, maxn = e[1], e[2], e[3]
                  for len = 0, maxn do
                    local idx = {}
                    for i = 1, len do idx[i] = 1 end
                    while true do
                      local args, label = {}, {}
                      for i = 1, len do
                        args[i] = POOL[idx[i]]
                        label[i] = NAMES[idx[i]]
                      end
                      local r = { pcall(fn, table.unpack(args, 1, len)) }
                      local out = {}
                      for j = 2, #r do out[#out + 1] = s(r[j]) end
                      print("O", name, table.concat(label, ","), tostring(r[1]), table.concat(out, "\\t"))
                      -- 里程表进位
                      local p = len
                      while p >= 1 do
                        idx[p] = idx[p] + 1
                        if idx[p] <= #POOL then break end
                        idx[p] = 1
                        p = p - 1
                      end
                      if p < 1 then break end
                    end
                  end
                end
                print("DONE")
                """);
    }
    /**
     * 协程语义矩阵：{@code yield} 到底能跨哪些边界、{@code close} 的语义、
     * 错误怎么传播、四个状态怎么转、嵌套 {@code resume}。
     * <p>
     * 这片<b>是干净的</b>，照样固化进来 —— 「yield 能不能跨某某边界」这类规则
     * 极容易在改动调用路径时被悄悄改掉，而现成的 63 个 {@code CoroutineTest}
     * 用例并不覆盖「{@code table.sort} 的比较函数里 yield」这种组合。
     * 实测分界（参考实现与这里一致）：
     * <table>
     *   <tr><td>能 yield</td><td>{@code pcall} / {@code xpcall}、数值与泛型 for
     *       （含自定义迭代器）、{@code gmatch}、
     *       {@code __index} / {@code __newindex} / {@code __add} /
     *       {@code __lt} / {@code __concat} / {@code __len} / {@code __call} /
     *       {@code __close}</td></tr>
     *   <tr><td>不能</td><td>{@code table.sort} 的比较函数、
     *       {@code string.gsub} 的替换函数、{@code print} 里的
     *       {@code __tostring} —— 报
     *       {@code attempt to yield across a C-call boundary}</td></tr>
     * </table>
     * 另外几处一并盯着：{@code __eq} 里 yield 之后协程是 <b>dead</b>（不是
     * suspended）；自己 {@code close} 自己报
     * {@code cannot close a running coroutine}；{@code <close>} 变量按
     * <b>声明的逆序</b>关闭，且 {@code __close} 里抛错会替换掉原错误。
     */
    @Test
    void coroutineSemanticsMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 协程语义矩阵：yield 能跨哪些边界、close 的语义、错误怎么传播、
                -- 状态机的四个状态、嵌套协程。
                local function pr(...)
                  print(...)
                end
                -- wrap 抛出的错误对象可能带回溯，这里只打第一行
                local function first(s)
                  return (tostring(s):gsub("\\n.*", ""))
                end
                
                -- 1) 基本状态机
                do
                  local co = coroutine.create(function(a, b)
                    pr("C1", a, b, coroutine.status(coroutine.running()))
                    local x, y = coroutine.yield(a + b)
                    pr("C2", x, y)
                    return "done", 42
                  end)
                  pr("S1", coroutine.status(co))
                  pr("R1", coroutine.resume(co, 1, 2))
                  pr("S2", coroutine.status(co))
                  pr("R2", coroutine.resume(co, "x", "y"))
                  pr("S3", coroutine.status(co))
                  pr("R3", coroutine.resume(co))
                  pr("S4", coroutine.status(co))
                end
                
                -- 2) 主线程不能 yield；running() 的返回值
                pr("M1", coroutine.isyieldable())
                do
                  local c, main = coroutine.running()
                  pr("M2", type(c), main) -- 主线程对象的 tostring 带地址，只打类型
                end
                pr("M3", pcall(coroutine.yield))
                pr("M4", pcall(coroutine.yield, 1, 2))
                
                -- 3) 死协程 / 运行中的协程
                do
                  local co = coroutine.create(function() return 1 end)
                  coroutine.resume(co)
                  pr("D1", coroutine.status(co), coroutine.resume(co))
                  local co2 = coroutine.create(function() return 1 end)
                  pr("D2", coroutine.resume(co2), coroutine.resume(co2))
                  local co3
                  co3 = coroutine.create(function()
                    pr("D3", coroutine.status(co3))
                    pr("D4", pcall(coroutine.resume, co3))
                    pr("D5", coroutine.close(co3))
                  end)
                  coroutine.resume(co3)
                end
                
                -- 4) wrap：错误传播与死后调用
                do
                  local w = coroutine.wrap(function()
                    coroutine.yield(1)
                    error("boom")
                  end)
                  pr("W1", w())
                  pr("W2", pcall(w))
                  pr("W3", pcall(w))
                  local w2 = coroutine.wrap(function() end)
                  pr("W4", pcall(w2))
                  pr("W5", pcall(w2))
                  local w3 = coroutine.wrap(function() return 1, 2, 3 end)
                  pr("W6", w3())
                  local w7 = coroutine.wrap(function() error({ code = 1 }) end)
                  -- 错误对象是表，tostring 带地址、两边不可能一致，只打类型
                  pr("W7", select(1, pcall(w7)), type(select(2, pcall(w7))))
                  pr("W8", first(select(2, pcall(coroutine.wrap(function() error("plain") end)))))
                  pr("W9", first(select(2, pcall(coroutine.wrap(function() error(nil) end)))))
                  pr("W10", first(select(2, pcall(coroutine.wrap(function() error(42) end)))))
                  pr("W11", first(select(2, pcall(coroutine.wrap(function()
                    local x = nil
                    return x.field
                  end)))))
                  pr("W12", pcall(coroutine.wrap(function() coroutine.yield(1) end)))
                end
                
                -- 5) yield 能跨哪些边界
                local function canYield(label, f)
                  local co = coroutine.create(f)
                  local ok, err = coroutine.resume(co)
                  local st = coroutine.status(co)
                  pr("Y", label, ok, st, ok and "ok" or first(err))
                end
                canYield("plain", function() coroutine.yield(1) end)
                canYield("pcall", function() return pcall(function() return coroutine.yield(1) end) end)
                canYield("xpcall", function()
                  return xpcall(function() return coroutine.yield(1) end, function(e) return "H" end)
                end)
                canYield("for-numeric", function()
                  for i = 1, 3 do coroutine.yield(i) end
                end)
                canYield("for-generic-pairs", function()
                  for k, v in pairs({ 1, 2 }) do coroutine.yield(k) end
                end)
                canYield("for-generic-ipairs", function()
                  for _, v in ipairs({ 1, 2 }) do coroutine.yield(v) end
                end)
                canYield("for-generic-custom", function()
                  local function iter(s, c)
                    if c < 2 then return c + 1, c * 10 end
                  end
                  for a, b in iter, nil, 0 do coroutine.yield(a, b) end
                end)
                canYield("sort-cmp", function()
                  table.sort({ 3, 1, 2 }, function(a, b) coroutine.yield(0) return a < b end)
                end)
                canYield("gsub-repl", function()
                  return string.gsub("abc", "%a", function(c) coroutine.yield(0) return c end)
                end)
                canYield("gmatch", function()
                  for w in string.gmatch("a b", "%a") do coroutine.yield(w) end
                end)
                canYield("tostring-mm", function()
                  local t = setmetatable({}, { __tostring = function() coroutine.yield(0) return "T" end })
                  print(t)
                  return 1
                end)
                canYield("index-mm", function()
                  local t = setmetatable({}, { __index = function(_, k) coroutine.yield(0) return k end })
                  return t.x
                end)
                canYield("newindex-mm", function()
                  local t = setmetatable({}, { __newindex = function(_, k, v) coroutine.yield(0) end })
                  t.x = 1
                  return 1
                end)
                canYield("add-mm", function()
                  local t = setmetatable({}, { __add = function() coroutine.yield(0) return 1 end })
                  return t + 1
                end)
                canYield("eq-mm", function()
                  local t = setmetatable({}, { __eq = function() coroutine.yield(0) return true end })
                  return t == t
                end)
                canYield("lt-mm", function()
                  local t = setmetatable({}, { __lt = function() coroutine.yield(0) return true end })
                  return t < t
                end)
                canYield("concat-mm", function()
                  local t = setmetatable({}, { __concat = function() coroutine.yield(0) return "C" end })
                  return t .. "x"
                end)
                canYield("len-mm", function()
                  local t = setmetatable({}, { __len = function() coroutine.yield(0) return 3 end })
                  return #t
                end)
                canYield("call-mm", function()
                  local t = setmetatable({}, { __call = function() coroutine.yield(0) return "K" end })
                  return t()
                end)
                canYield("close-mm", function()
                  local t = setmetatable({}, { __close = function() coroutine.yield(0) end })
                  local x <close> = t
                  coroutine.yield(1)
                end)
                canYield("resume-inside", function()
                  local inner = coroutine.create(function() coroutine.yield("in") end)
                  pr("  inner1", coroutine.resume(inner))
                  pr("  inner2", coroutine.resume(inner))
                  return "out"
                end)
                canYield("wrap-inside", function()
                  local inner = coroutine.wrap(function() coroutine.yield("iw") return "iw2" end)
                  pr("  innerw", inner(), inner())
                  return "outw"
                end)
                
                -- 6) to-be-closed 与 close()
                do
                  local log = {}
                  local co = coroutine.create(function()
                    local a <close> = setmetatable({}, {
                      __close = function(_, err)
                        log[#log + 1] = "close-a:" .. tostring(err)
                      end,
                    })
                    local b <close> = setmetatable({}, {
                      __close = function(_, err)
                        log[#log + 1] = "close-b:" .. tostring(err)
                      end,
                    })
                    coroutine.yield()
                    return "fin"
                  end)
                  pr("T1", coroutine.resume(co))
                  pr("T2", table.concat(log, "|"))
                  pr("T3", coroutine.resume(co))
                  pr("T4", table.concat(log, "|"))
                end
                do
                  local log = {}
                  local co = coroutine.create(function()
                    local a <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "c:" .. tostring(err) end,
                    })
                    coroutine.yield()
                    error("inside")
                  end)
                  pr("T5", coroutine.resume(co))
                  pr("T6", coroutine.resume(co))
                  pr("T7", table.concat(log, "|"), coroutine.status(co))
                end
                do
                  local log = {}
                  local co = coroutine.create(function()
                    local a <close> = setmetatable({}, {
                      __close = function(_, err)
                        log[#log + 1] = "c1"
                        error("in-close")
                      end,
                    })
                    local b <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "c2:" .. tostring(err) end,
                    })
                    coroutine.yield()
                  end)
                  pr("T8", coroutine.resume(co))
                  pr("T9", coroutine.resume(co))
                  pr("T10", table.concat(log, "|"))
                end
                pr("T11", coroutine.close(coroutine.create(function() return 1 end)))
                do
                  local co = coroutine.create(function() return 1 end)
                  pr("T12", coroutine.resume(co), coroutine.close(co))
                end
                do
                  local co = coroutine.create(function() coroutine.yield() end)
                  pr("T13", coroutine.resume(co), coroutine.close(co), coroutine.status(co))
                  pr("T14", coroutine.close(co))
                end
                do
                  local co
                  co = coroutine.create(function() pr("T15", coroutine.close(co)) end)
                  pr("T16", coroutine.resume(co))
                end
                pr("T17", pcall(coroutine.close, 5))
                pr("T18", pcall(coroutine.close))
                pr("T19", pcall(coroutine.close, {}))
                
                -- 7) 错误对象与层级
                do
                  local co = coroutine.create(function() error("e1") end)
                  pr("E1", coroutine.resume(co))
                  local co2 = coroutine.create(function() error("e2", 0) end)
                  pr("E2", coroutine.resume(co2))
                  local co3 = coroutine.create(function() error("e3", 2) end)
                  pr("E3", coroutine.resume(co3))
                  local co4 = coroutine.create(function() error({}) end)
                  local e4ok, e4v = coroutine.resume(co4)
                  pr("E4", e4ok, type(e4v)) -- 表当错误对象，地址不可比
                  local co5 = coroutine.create(function() coroutine.yield() error("e5") end)
                  pr("E5", coroutine.resume(co5), coroutine.resume(co5))
                  local co6 = coroutine.create(function()
                    local ok, e = pcall(function() error("inner") end)
                    pr("E6", ok, e)
                    return 1
                  end)
                  pr("E7", coroutine.resume(co6))
                  local co7 = coroutine.create(function()
                    error(setmetatable({}, { __tostring = function() return "OBJ" end }))
                  end)
                  pr("E8", coroutine.resume(co7))
                  pr("E9", first(select(2, coroutine.resume(co7))))
                end
                
                -- 8) 嵌套：协程里 resume 另一个协程
                do
                  local depth = 0
                  local function mk(n)
                    return coroutine.create(function()
                      depth = depth + 1
                      if n > 0 then
                        local inner = mk(n - 1)
                        pr("N", n, coroutine.resume(inner))
                      end
                      coroutine.yield("d" .. n)
                      return n
                    end)
                  end
                  local top = mk(3)
                  pr("N1", coroutine.resume(top))
                  pr("N2", coroutine.resume(top))
                  pr("N3", depth)
                end
                
                -- 9) resume 传参与多返回值
                do
                  local co = coroutine.create(function(...)
                    pr("V1", select("#", ...), ...)
                    local a, b, c = coroutine.yield(...)
                    pr("V2", a, b, c)
                    return ...
                  end)
                  pr("V3", coroutine.resume(co, 1, 2, 3))
                  pr("V4", coroutine.resume(co, "p", "q"))
                end
                
                -- 10) 协程里写全局；协程当错误对象 / 表键 / 算术
                do
                  local co = coroutine.create(function()
                    GVAR = "set-by-co"
                    pr("G1", GVAR)
                    return 1
                  end)
                  pr("G2", coroutine.resume(co), GVAR)
                end
                do
                  local co = coroutine.create(function() end)
                  pr("K1", type(co), tostring(co):sub(1, 7))
                  local t = {}
                  t[co] = 1
                  pr("K2", t[co], rawequal(t[co], 1))
                  pr("K3", pcall(function() return co + 1 end))
                  pr("K4", pcall(function() return #co end))
                  pr("K5", pcall(function() return co() end))
                end
                
                print("DONE")
                """);
    }
    /**
     * 数值 {@code for} 的四个分支（{@code lvm.c: forprep} / {@code forloop}：
     * 整数升、整数降、浮点升、浮点降，各有各的溢出处理）+ 整数/浮点强制转换 +
     * {@code //} / {@code %} / 位运算的角落。共 {@code N} 行，干净。
     * <p>
     * 留着的三条理由：
     * <ul>
     *   <li><b>{@code maxinteger} 边界</b>：{@code for i = maxinteger - 2, maxinteger}
     *       这类循环，终止条件在整数路径上是要<b>防溢出</b>地比较的，写漏一处
     *       就是 2^64 次迭代。</li>
     *   <li><b>循环变量是整数还是浮点</b>：{@code for i = 0, 1, 0.5} 与
     *       {@code for i = 0, 1.0, 0.5} 打出来的 {@code math.type(i)} 不一样，
     *       前者首值是整数 {@code 0}、之后变浮点。</li>
     *   <li><b>位运算不做字符串转换</b>：{@code "10" // 2} 给 5，但
     *       {@code "10" & 1} 报
     *       {@code attempt to perform bitwise operation on a string value}。</li>
     * </ul>
     * 三条<b>不能测</b>的：{@code for i = 1, inf}、{@code for i = inf, inf}
     * （{@code i + 1} 还是 {@code inf}，条件恒真）、
     * {@code for i = maxinteger, mininteger, -1}（2^64 次）。另外
     * {@code for i = 2^53, 2^53 + 2} 与 {@code for i = 1e300, 1e300} 在浮点精度下
     * 也是<b>不推进</b>的死循环，探针里加了 20000 次的兜底，两边都在同一处跳出来。
     */
    @Test
    void numericForAndCoercionMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 数值 for 的边界 + 整数/浮点强制转换 + // % 位运算的角落
                -- 数值 for 在 lvm.c: forprep/forloop，四个分支（整数升、整数降、浮点升、
                -- 浮点降）各有各的溢出处理，这里是按分支铺的。
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                
                -- 1) 数值 for：跑起来数一遍，并把每个 i 的类型也打出来
                local function runFor(label, a, b, s)
                  local n = 0
                  local first, last, types = nil, nil, {}
                  local ok, err = pcall(function()
                    -- 兜底：万一哪个组合是永不停的循环，跑满 100000 次就跳出来，
                    -- 免得参考实现挂在那儿（这类探针挂一次要手动杀进程）
                    local guard = 0
                    local function step(i)
                      n = n + 1
                      if n == 1 then first = i end
                      last = i
                      types[math.type(i)] = (types[math.type(i)] or 0) + 1
                      guard = guard + 1
                      if guard >= 20000 then error("__TOOBIG__") end
                    end
                    if s == nil then
                      for i = a, b do step(i) end
                    else
                      for i = a, b, s do step(i) end
                    end
                  end)
                  local ts = {}
                  for k, v in pairs(types) do ts[#ts + 1] = k .. "=" .. v end
                  table.sort(ts)
                  pr("F", label, ok, n, tostring(first), tostring(last), table.concat(ts, ","),
                     ok and "" or tostring(err))
                end
                runFor("1..3", 1, 3)
                runFor("1..3 nil step", 1, 3, nil)
                runFor("3..1", 3, 1)
                runFor("1..1", 1, 1)
                runFor("1..0", 1, 0)
                runFor("0..0", 0, 0)
                runFor("1..3 s1", 1, 3, 1)
                runFor("1..3 s-1", 1, 3, -1)
                runFor("3..1 s-1", 3, 1, -1)
                runFor("1..10 s3", 1, 10, 3)
                runFor("10..1 s-3", 10, 1, -3)
                runFor("1.0..3.0", 1.0, 3.0)
                runFor("1.0..3.0 s0.5", 1.0, 3.0, 0.5)
                runFor("0..1 s0.5", 0, 1, 0.5)
                runFor("0..1.0 s0.5", 0, 1.0, 0.5)
                runFor("1..2 s0.1", 1, 2, 0.1)
                runFor("1..3 s-0.5", 1, 3, -0.5)
                runFor("3..1 s-0.5", 3, 1, -0.5)
                runFor("-1..1 s0", -1, 1, 0)
                runFor("1..2 snil", 1, 2, nil)
                runFor("str..str", "1", "3")
                runFor("str..str s", "1", "3", "1")
                runFor("1..3 sstr", 1, 3, "1")
                runFor("1..3 sstr0.5", 1, 3, "0.5")
                runFor("max-2..max", math.maxinteger - 2, math.maxinteger)
                runFor("max-2..max s2", math.maxinteger - 2, math.maxinteger, 2)
                runFor("min..min+2", math.mininteger, math.mininteger + 2)
                runFor("min..min+2 s2", math.mininteger, math.mininteger + 2, 2)
                runFor("min..max smax", math.mininteger, math.maxinteger, math.maxinteger)
                runFor("max-1..max s2", math.maxinteger - 1, math.maxinteger, 2)
                runFor("1..max smax", 1, math.maxinteger, math.maxinteger)
                runFor("2^53..2^53+2", 2 ^ 53, 2 ^ 53 + 2)
                runFor("2^53..2^53+2 f", (2 ^ 53) + 0.0, (2 ^ 53) + 2.0)
                runFor("1e300..1e300", 1e300, 1e300)
                runFor("-0.0..0.0", -0.0, 0.0)
                runFor("nan..nan", 0 / 0, 0 / 0)
                runFor("1..nan", 1, 0 / 0)
                -- 下面两条<b>不能</b>测：for i = 1, inf 与 for i = inf, inf 都是永不停的
                -- 循环（i 加 1 还是 inf，条件恒真），参考实现跑起来就不回来了。
                -- 同理 for i = maxinteger, mininteger, -1 是 2^64 次迭代。
                runFor("-inf..inf s-inf", -1 / 0, 1 / 0, 1 / 0)
                pr("F2", pc(function() for i = 1, 3, "x" do end end))
                pr("F3", pc(function() for i = {}, 3 do end end))
                pr("F4", pc(function() for i = 1, {} do end end))
                pr("F5", pc(function() for i = 1, 3, {} do end end))
                pr("F6", pc(function() for i = nil, 3 do end end))
                -- 数值 for 只接受 2 或 3 个表达式，写 1 个或 4 个都是语法错误（解析期就挂，
                -- 没法在 pcall 里兜），所以这里不测。
                do
                  local co = {}
                  for i = 1, 3 do
                    co[#co + 1] = i
                    i = i + 100
                  end
                  pr("F8", table.concat(co, ","))
                end
                do
                  local s = 0
                  for i = 1, 10 do
                    if i > 3 then break end
                    s = s + i
                  end
                  pr("F9", s)
                end
                
                -- 2) 整数除与取模的角落
                local N = {
                  math.maxinteger, math.mininteger, -1, 0, 1, 2, 3, -3, 7, -7,
                  2 ^ 53, -(2 ^ 53),
                }
                for _, a in ipairs(N) do
                  for _, b in ipairs(N) do
                    pr("I", a, b, pc(function() return a // b end),
                       pc(function() return a % b end), pc(function() return a / b end))
                  end
                end
                local FV = { 0.0, -0.0, 1.0, -1.0, 2.5, -2.5, 0.5, 1 / 3, 1 / 0, -1 / 0, 0 / 0,
                             1e300, -1e300, 2 ^ 53 }
                for _, a in ipairs(FV) do
                  for _, b in ipairs(FV) do
                    pr("D", tostring(a), tostring(b), tostring(a // b), tostring(a % b))
                  end
                end
                
                -- 3) 位运算：只接受有整数表示的数
                local BV = { 0, 1, -1, 255, 2 ^ 31, 2 ^ 32, 2 ^ 63, -(2 ^ 63), math.maxinteger,
                             math.mininteger, 1.0, 2.0, 0.5, 1 / 3, 1 / 0, 0 / 0, "1", "0x10" }
                for _, a in ipairs(BV) do
                  pr("B1", tostring(a), pc(function() return ~a end))
                  pr("B2", tostring(a), pc(function() return a & 255 end))
                  pr("B3", tostring(a), pc(function() return a | 0 end))
                  pr("B4", tostring(a), pc(function() return a ~ 0 end))
                  pr("B5", tostring(a), pc(function() return a << 1 end))
                  pr("B6", tostring(a), pc(function() return a >> 1 end))
                  pr("B7", tostring(a), pc(function() return -a end))
                end
                pr("B8", 1 << 63, 1 << 64, 1 << 100, -1 << 63, 1 >> 100, -1 >> 100)
                pr("B9", 255 >> -1, 255 << -1, math.maxinteger << 1, math.mininteger >> 1)
                pr("B10", pc(function() return 1 << (1 / 0) end))
                pr("B11", pc(function() return 1 << (0 / 0) end))
                
                -- 4) 字符串与数字的强制转换
                local SV = { "10", " 10 ", "0x10", "1e2", "10.0", ".5", "1.", "inf", "nan",
                             "-10", "+10", "10x", "", " ", "0X1P4", "  0x10  ", "1e", "0b101" }
                for _, s in ipairs(SV) do
                  pr("S", string.format("%q", s), pc(function() return s + 0 end),
                     pc(function() return s * 2 end), pc(function() return -s end),
                     math.type(tonumber(s)), tostring(tonumber(s)))
                end
                pr("S2", "10" == 10, "10" ~= 10, 10 == 10.0, 0 == -0.0, 1 / 0 == 1 / 0,
                   (0 / 0) == (0 / 0))
                pr("S3", pc(function() return "10" < 11 end))
                -- 位运算<b>不</b>做字符串转换（"10" & 1 报错），而 // 与 % 做
                pr("S4", "10" // 2, "10" % 3, pc(function() return "10" & 1 end))
                pr("S5", 10 .. "", 10.0 .. "", 1 / 3 .. "", (0 / 0) .. "")
                pr("S6", tostring(10), tostring(10.0), tostring(-0.0), tostring(1 / 0),
                   tostring(-1 / 0), tostring(0 / 0))
                pr("S7", tostring(1e15), tostring(1e16), tostring(2 ^ 53), tostring(2 ^ 63),
                   tostring(math.maxinteger), tostring(math.mininteger))
                pr("S8", 1 == 1.0, math.type(1), math.type(1.0), math.type(2 ^ 63),
                   math.type(2 ^ 31))
                pr("S9", pc(function() return "abc" + 1 end))
                pr("S10", pc(function() return nil + 1 end))
                pr("S11", pc(function() return true + 1 end))
                pr("S12", pc(function() return 1 < "a" end))
                pr("S13", pc(function() return 1 < nil end))
                pr("S14", pc(function() return {} < {} end))
                pr("S15", 1 < 2, "a" < "b", "10" < "9", 1 < 1.5, math.maxinteger < 1 / 0,
                   math.mininteger > -1 / 0)
                pr("S16", math.maxinteger + 1, math.mininteger - 1, math.maxinteger * 2,
                   -math.mininteger)
                pr("S17", math.maxinteger + 1 == math.mininteger, math.tointeger(2 ^ 63),
                   math.tointeger(2 ^ 63 - 1))
                pr("S18", 2 ^ 63, math.type(2 ^ 63), (2 ^ 63) + 0.0 == 2 ^ 63)
                
                print("DONE")
                """);
    }
    /**
     * 语法层与作用域矩阵，两半：
     * <ul>
     *   <li><b>编译期错误</b>：用 {@code load()} 把六十来个片段编一遍，报什么错
     *       就打什么错。{@code goto} 跳进作用域、重复标签、给 {@code <const>}
     *       赋值、{@code break} 在循环外、畸形数字与转义、函数名的各种形态 ——
     *       这些<b>只有手写才碰得到</b>，fuzzer 生成的程序撞不上。</li>
     *   <li><b>运行期语义</b>：多重赋值「右边先全部求值」、表构造器的求值顺序与
     *       键覆盖、变长参数（含 {@code nil} 混在里面）、方法调用与 {@code :} 的
     *       各种畸形用法、局部变量遮蔽与 upvalue 共享、{@code repeat} 的条件能
     *       看见循环体里的 {@code local}、运算符优先级与结合性、字面量。</li>
     * </ul>
     * 干净（{@code N} 行）。留着是因为这片全是「改动词法/语法/作用域时会悄悄
     * 坏掉」的地方，而现成用例里只有零散几处。
     */
    @Test
    void syntaxAndScopeMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 语法层与作用域矩阵。两半：
                -- A) 编译期错误：用 load() 把片段编一遍，报什么错就打什么错 —— goto 跳进
                --    作用域、重复标签、给 <const> 赋值、break 在循环外这些只有手写才碰得到，
                --    fuzzer 生成的程序基本不会撞上。
                -- B) 运行期语义：多重赋值的求值顺序、表构造器的求值顺序、变长参数、方法调用、
                --    局部变量遮蔽与 upvalue 共享、运算符优先级与结合性、字面量。
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                
                -- ============ A) 编译期错误 ============
                local CHUNKS = {
                  { "goto-ok-fwd", [[ do goto x ::x:: end ]] },
                  { "goto-ok-back", [[ local i = 0 ::x:: i = i + 1 if i < 3 then goto x end ]] },
                  { "goto-into-scope", [[ do goto x end local y = 1 ::x:: ]] },
                  { "goto-into-scope2", [[ for i = 1, 3 do goto x end ::x:: ]] },
                  { "goto-undefined", [[ goto nope ]] },
                  { "goto-label-undefined-2", [[ ::a:: goto b ::b:: ]] },
                  { "dup-label", [[ ::a:: ::a:: ]] },
                  { "dup-label-block", [[ do ::a:: end do ::a:: end ]] },
                  { "label-at-end", [[ local function f() ::done:: return 1 end ]] },
                  { "goto-out-of-function", [[ local function f() goto out end ::out:: ]] },
                  { "break-outside", [[ break ]] },
                  { "break-in-func", [[ local function f() break end ]] },
                  { "break-across-func", [[ for i = 1, 3 do local function f() break end end ]] },
                  { "const-assign", [[ local x <const> = 1 x = 2 ]] },
                  { "const-ok", [[ local x <const> = 1 local y = x + 1 ]] },
                  { "const-nil", [[ local x <const> ]] },
                  { "close-nil", [[ local x <close> = nil ]] },
                  { "close-ok", [[ local x <close> = setmetatable({}, {__close=function() end}) ]] },
                  { "attrib-unknown", [[ local x <nope> = 1 ]] },
                  { "attrib-twice", [[ local x <const, close> = 1 ]] },
                  { "syntax-do", [[ do ]] },
                  { "syntax-end", [[ end ]] },
                  { "syntax-if", [[ if 1 then ]] },
                  { "syntax-1", [[ 1 + ]] },
                  { "num-1e", [[ return 1e ]] },
                  { "num-dot1", [[ return ..1 ]] },
                  { "num-0b", [[ return 0b101 ]] },
                  { "num-0x-p", [[ return 0x1p4 ]] },
                  { "num-0x-", [[ return 0x.8 ]] },
                  { "num-3dot", [[ return 3. ]] },
                  { "str-badesc", [[ return "\\q" ]] },
                  { "str-badhex", [[ return "\\xZZ" ]] },
                  { "str-baduni", [[ return "\\u{110000}" ]] },
                  { "str-uni-ok", [[ return "\\u{41}" ]] },
                  { "str-z", [[ return "a\\z
                     b" ]] },
                  -- 这三条外层必须用 [==[ ：片段里自己带 ]]，用 [[ ]] 会被提前闭合
                  { "str-unfinished", [==[ return "abc ]==] },
                  { "longstr-unfinished", [==[ return [[abc ]==] },
                  -- 这条片段自己带 ]==]，外层得再高两级
                  { "longstr-level", [====[ return [==[a]]b]==] ]====] },
                  { "self-outside", [[ function a:b() end ]] },
                  { "func-name-expr", [[ function (a).b() end ]] },
                  { "vararg-in-middle", [[ local function f(a, ..., b) end ]] },
                  { "vararg-outside", [[ return ... ]] },
                  { "assign-to-call", [[ f() = 1 ]] },
                  { "assign-noname", [[ 1 = 2 ]] },
                  { "multiple-local", [[ local a, = 1 ]] },
                  { "return-mid", [[ return 1 + ]] },
                  { "concat-num", [[ return 1 .. 2 ]] },
                  { "eq-assign", [[ local a == 1 ]] },
                  { "malformed-number", [[ return 1__2 ]] },
                  { "newline-in-str", [[ return "a
                b" ]] },
                }
                for _, e in ipairs(CHUNKS) do
                  local f, err = load(e[2], "=CHUNK")
                  if f then
                    local ok, r = pcall(f)
                    pr("A", e[1], "ok", ok, tostring(r))
                  else
                    pr("A", e[1], "err", tostring(err))
                  end
                end
                
                -- ============ B) 运行期语义 ============
                -- 多重赋值：右边先全部求值
                do
                  local log = {}
                  local function f(name, v)
                    log[#log + 1] = name
                    return v
                  end
                  local a, b = f("1", 10), f("2", 20)
                  pr("B1", a, b, table.concat(log, ","))
                  log = {}
                  local x, y = 1, 2
                  x, y = y, x
                  pr("B2", x, y)
                  log = {}
                  local t = {}
                  t[1], t[2] = f("a", 1), f("b", 2)
                  pr("B3", t[1], t[2], table.concat(log, ","))
                end
                -- 个数不匹配
                do
                  local a, b, c = 1
                  pr("B4", a, b, c)
                  local d, e = 1, 2, 3
                  pr("B5", d, e)
                  local function g()
                    return 1, 2, 3
                  end
                  local p, q, r, s = g()
                  pr("B6", p, q, r, s)
                  pr("B7", (g()))
                  pr("B8", select("#", g()), select(2, g()))
                  local arr = { g() }
                  pr("B9", #arr)
                  local arr2 = { (g()) }
                  pr("B10", #arr2)
                end
                -- 表构造器的求值顺序与键覆盖
                do
                  local log = {}
                  local function k(n)
                    log[#log + 1] = n
                    return n
                  end
                  local t = { k(1), k(2), k(3) }
                  pr("C1", table.concat(log, ","), #t, t[1], t[2], t[3])
                  log = {}
                  local t2 = { [k("a")] = 1, b = k(2), k(3) }
                  pr("C2", table.concat(log, ","), #t2, t2.a, t2.b, t2[1])
                  log = {}
                  local t3 = { [1] = k("x"), [1] = k("y") }
                  pr("C3", table.concat(log, ","), t3[1], #t3)
                  local t4 = { 1, 2, 3, n = 4, [5] = 5 }
                  pr("C4", #t4, t4.n, t4[5])
                  local t5 = { "a", "b", "c" }
                  pr("C5", #t5, table.concat(t5, ""))
                  local t6 = { nil, nil, 3 }
                  pr("C6", #t6, t6[3])
                end
                -- 变长参数
                do
                  local function f(...)
                    return select("#", ...), ...
                  end
                  pr("D1", f())
                  pr("D2", f(1, 2, 3))
                  pr("D3", f(nil, nil))
                  pr("D4", f(1, nil, 3))
                  local function g(a, ...)
                    return a, select("#", ...)
                  end
                  pr("D5", g(1), g(1, 2), g(1, 2, 3))
                  local function h(...)
                    local p = table.pack(...)
                    return p.n, select("#", ...)
                  end
                  pr("D6", h(), h(1, nil, 3))
                  local function i(...)
                    local t = { ... }
                    return #t
                  end
                  pr("D7", i(1, 2, 3), i(1, nil, 3), i(nil))
                  local function j(...)
                    return ...
                  end
                  pr("D8", j(1, 2, 3))
                  pr("D9", select("#"))
                end
                -- 方法与冒号
                do
                  local obj = { v = 7 }
                  function obj:get()
                    return self.v
                  end
                  function obj:set(x)
                    self.v = x
                    return self
                  end
                  pr("E1", obj:get(), obj:set(9).v)
                  pr("E2", obj["get"](obj))
                  pr("E3", pc(function() return obj.get() end))
                  local s = "abc"
                  pr("E4", s:upper(), s:sub(2), s:len(), ("x"):rep(3))
                  pr("E5", ("%d-%s"):format(1, "a"))
                  pr("E6", pc(function() return s:nope() end))
                  pr("E7", pc(function() return (1):nope() end))
                  pr("E8", pc(function() return (nil):nope() end))
                  pr("E9", pc(function() return (true):nope() end))
                end
                -- 作用域与遮蔽
                do
                  local x = "outer"
                  do
                    local x = "inner"
                    pr("F1", x)
                  end
                  pr("F2", x)
                  local y = "o"
                  local y = y .. "i"
                  pr("F3", y)
                  do
                    local z = 1
                    for i = 1, 2 do
                      local z = i * 10
                      pr("F4", i, z)
                    end
                    pr("F5", z)
                  end
                end
                -- upvalue 共享
                do
                  local n = 0
                  local function inc()
                    n = n + 1
                    return n
                  end
                  local function get()
                    return n
                  end
                  inc()
                  inc()
                  pr("G1", get(), n)
                  local fns = {}
                  for i = 1, 3 do
                    fns[i] = function() return i end
                  end
                  pr("G2", fns[1](), fns[2](), fns[3]())
                  local function mk()
                    local v = 0
                    return function() v = v + 1 return v end, function() return v end
                  end
                  local a1, a2 = mk()
                  local b1, b2 = mk()
                  a1()
                  a1()
                  b1()
                  pr("G3", a2(), b2())
                end
                -- repeat 的条件能看见循环体里的 local
                do
                  local n = 0
                  repeat
                    local done = n >= 2
                    n = n + 1
                  until done
                  pr("H1", n)
                  local m = 0
                  repeat
                    m = m + 1
                    local stop = m > 5
                  until stop
                  pr("H2", m)
                end
                -- 运算符优先级与结合性
                pr("I1", 2 ^ 3 ^ 2, -2 ^ 2, 2 ^ -2, (-2) ^ 2)
                pr("I2", 1 .. 2 .. 3, "a" .. 1 .. "b")
                pr("I3", 1 + 2 * 3 - 4 / 2, (1 + 2) * 3)
                pr("I4", 7 // 2 * 2, 7 % 3 + 1)
                pr("I5", 1 < 2 and 3 or 4, 1 > 2 and 3 or 4)
                pr("I6", not nil, not not 1, not (1 == 2))
                pr("I7", -"10", #"abc" + 1, -#  "ab")
                pr("I8", 1 | 2 & 3 ~ 4, (1 | 2) & 3)
                pr("I9", 1 << 2 >> 1, -1 >> 1, 1 << 1 + 1)
                pr("I10", 2 ^ 2 ^ 3 == 2 ^ (2 ^ 3))
                pr("I11", "a" .. "b" == "ab")
                pr("I12", pc(function() return 1 < 2 < 3 end))
                pr("I13", 10 // 3 // 2, 10 // (3 // 2))
                pr("I14", -2 ^ -2, (-2) ^ (-2))
                -- 字面量
                pr("J1", 0x10, 0X10, 0x1p4, 0X1P4, 0x.8p1, 0xA, 0xff)
                pr("J2", 1e2, 1E2, 1e+2, 1e-2, .5, 5., 5.0, 0.5e1)
                pr("J3", math.type(0x10), math.type(1e2), math.type(1), math.type(1.0),
                   math.type(3), math.type(3.0))
                pr("J4", 1e15, 1e16, 0x10 == 16, 1e2 == 100)
                pr("J5", "\\65", "\\x41", "A", "\\u{41}", "\\u{1F600}" == "\\240\\159\\152\\128")
                pr("J6", #"\\u{41}", #"\\u{1F600}", "\\z" .. "a")
                pr("J7", [[abc]], [==[a]]b]==], [[\\n]] == "\\\\n")
                pr("J8", [[
                line2]], #[[
                ]])
                pr("J9", 0x7fffffff, 0x80000000, 0xffffffff, 0xfffffffffffff)
                -- 空语句（5.2 起 `;` 单独成句合法）
                do
                  local k1 = 1;
                  ;
                  pr("K1", k1)
                end
                -- 比较链里的 __lt
                do
                  local mt = { __lt = function(a, b) return true end }
                  local a, b = setmetatable({}, mt), setmetatable({}, mt)
                  pr("K2", a < b, a > b, a <= b, a >= b)
                end
                print("DONE")
                """);
    }
    /**
     * {@code debug} 库与 {@code utf8} 库矩阵。干净（{@code N} 行）。
     * <ul>
     *   <li><b>{@code getinfo}</b>：{@code what} 的每个字母单独与组合
     *       （{@code n S l u f L t}），外加越界 level、传函数、传非字符串。</li>
     *   <li><b>{@code getlocal} / {@code setlocal} / {@code getupvalue} /
     *       {@code setupvalue}</b>：遍历全部槽与越界。</li>
     *   <li><b>{@code sethook}</b>：{@code c} / {@code r} / {@code l} / count
     *       四个掩码各跑一遍并打事件序列。</li>
     *   <li><b>{@code traceback}</b>：<b>不</b>整段比对（里头有 chunk 名与地址），
     *       只比行数与「有没有 {@code stack traceback:} / {@code in main chunk}」。</li>
     *   <li><b>{@code utf8}</b>：{@code char / codepoint / len / offset / codes /
     *       charpattern} 的越界与非法序列。</li>
     * </ul>
     * 两处坑，写这片时踩过：{@code debug.setlocal(1, 1, ...)} 在主 chunk 里改掉的
     * 是第 1 个局部变量，也就是脚本自己的 {@code print} 包装，改完后面全炸；
     * 以及 {@code getinfo} / {@code getmetatable} 返回的表打印出来带地址，
     * 必须只比类型。
     */
    @Test
    void debugAndUtf8LibraryMatrixMatchesReference() {
        Diff.assertMatches("""
                -- debug 库与 utf8 库矩阵。
                -- debug 那半：getinfo 的每个 what 字母、getlocal/setlocal、getupvalue/
                -- setupvalue、sethook 的四个掩码、getregistry、uservalue、traceback 的形状。
                -- 回溯<b>不</b>整段比对（里头有 chunk 名与地址），只比行数与关键片段。
                -- 只留到第一个换行
                local function first(v)
                  return (tostring(v):gsub("\\n.*", ""))
                end
                local function pr(...)
                  print(...)
                end
                -- 函数/表的 tostring 带地址，只打类型
                local function s(v)
                  local tt = type(v)
                  if tt == "table" or tt == "function" or tt == "thread" or tt == "userdata" then
                    return tt
                  end
                  return tostring(v)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = s(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                
                -- 1) getinfo：level 与 what
                local function outer()
                  return debug.getinfo(1)
                end
                local function outer2()
                  return debug.getinfo(2)
                end
                pr("I0", s(debug.getinfo(0)), s(debug.getinfo(1)))
                do
                  local i = outer()
                  pr("I1", i.source, i.short_src, i.currentline, i.linedefined, i.lastlinedefined,
                     i.what, i.namewhat, tostring(i.name), i.nups, i.nparams, i.ntransfers,
                     i.isvararg, s(i.func), type(i.activelines))
                end
                do
                  local i = outer2()
                  pr("I2", i.currentline > 0, i.what)
                end
                -- what 的每个字母，单独与组合
                local WHATS = { "", "n", "S", "l", "u", "f", "L", "t", "nSlfLut", "Sn", "fl", "Sln" }
                for _, w in ipairs(WHATS) do
                  local ok, i = pcall(debug.getinfo, 1, w)
                  if not ok then
                    pr("W", string.format("%q", w), "err", tostring(i))
                  else
                    local keys = {}
                    for k in pairs(i) do keys[#keys + 1] = k end
                    table.sort(keys)
                    local vals = {}
                    for _, k in ipairs(keys) do vals[#vals + 1] = k .. "=" .. s(i[k]) end
                    pr("W", string.format("%q", w), table.concat(vals, ","))
                  end
                end
                pr("W2", pc(debug.getinfo, 100))
                pr("W3", pc(debug.getinfo, -1))
                pr("W4", pc(debug.getinfo))
                pr("W5", pc(debug.getinfo, print, "S"))
                pr("W6", pc(debug.getinfo, print))
                pr("W7", pc(debug.getinfo, 1, 5))
                pr("W8", pc(debug.getinfo, "x"))
                
                -- 2) getlocal / setlocal
                do
                  local a1, a2, a3 = 1, 2, 3
                  local n = 1
                  while true do
                    local nm, v = debug.getlocal(1, n)
                    if not nm then break end
                    pr("L", n, nm, s(v))
                    n = n + 1
                  end
                  -- setlocal 要在<b>自己的</b>那一层做：在主 chunk 里 setlocal(1, 1, ...)
                  -- 改掉的是第 1 个局部变量，也就是 pr 自己 —— 后面就再也调不动 print 了
                  local function inner()
                    local p, q = 1, 2
                    local nm = debug.setlocal(1, 1, 99)
                    return nm, p, q
                  end
                  pr("L2", inner())
                  pr("L2b", pc(debug.setlocal, 1, 0, 1))
                  pr("L3", pc(debug.getlocal, 1, 0))
                  pr("L4", pc(debug.getlocal, 100, 1))
                  pr("L5", pc(debug.getlocal))
                  pr("L6", pc(debug.getlocal, 1))
                end
                
                -- 3) upvalue
                do
                  local up1, up2 = "u1", "u2"
                  local function f()
                    return up1, up2
                  end
                  local n = 1
                  while true do
                    local nm, v = debug.getupvalue(f, n)
                    if not nm then break end
                    pr("U", n, nm, s(v))
                    n = n + 1
                  end
                  pr("U2", debug.getupvalue(f, 1))
                  pr("U3", debug.setupvalue(f, 1, "NEW"), f())
                  pr("U4", debug.setupvalue(f, 99, "x"))
                  pr("U5", pc(debug.getupvalue, print, 1))
                  pr("U6", pc(debug.getupvalue, 5, 1))
                  pr("U7", pc(debug.getupvalue))
                  pr("U8", pc(debug.setupvalue, f, 1))
                end
                
                -- 4) sethook：只打印事件序列，不打印地址
                do
                  local log = {}
                  local function hook(ev, ln)
                    log[#log + 1] = ev .. ":" .. tostring(ln)
                  end
                  local function target(a)
                    local x = a + 1
                    return x
                  end
                  debug.sethook(hook, "c")
                  target(1)
                  debug.sethook()
                  pr("H1", table.concat(log, "|"))
                  log = {}
                  debug.sethook(hook, "r")
                  target(1)
                  debug.sethook()
                  pr("H2", table.concat(log, "|"))
                  log = {}
                  debug.sethook(hook, "l")
                  target(1)
                  debug.sethook()
                  pr("H3", table.concat(log, "|"))
                  log = {}
                  debug.sethook(hook, "", 3)
                  for i = 1, 10 do local _ = i end
                  debug.sethook()
                  pr("H4", table.concat(log, "|"))
                  log = {}
                  debug.sethook(hook, "crl")
                  target(2)
                  debug.sethook()
                  pr("H5", (table.concat(log, "|"):gsub(":%d+", ":N")))
                  pr("H6", debug.gethook() == nil, pc(debug.gethook))
                  pr("H7", pc(debug.sethook, 5))
                  pr("H8", pc(debug.sethook, hook, 5))
                  pr("H9", pc(debug.sethook, hook, "z"))
                  pr("H10", debug.sethook(hook, "cr", 2))
                  debug.sethook()
                  pr("H11", debug.gethook() == nil)
                end
                
                -- 5) traceback 的形状（不整段比对）
                do
                  local tb = debug.traceback("MSG")
                  local n = select(2, tb:gsub("\\n", ""))
                  pr("T1", n, tb:sub(1, 4), (tb:find("stack traceback:", 1, true) ~= nil),
                     (tb:find("in main chunk", 1, true) ~= nil))
                  local tb2 = debug.traceback("M2", 2)
                  local n2 = select(2, tb2:gsub("\\n", ""))
                  pr("T2", n2, (tb2:find("in main chunk", 1, true) ~= nil))
                  pr("T3", (debug.traceback():find("stack traceback:", 1, true) ~= nil))
                  -- traceback(5) 的返回值<b>就是</b>回溯，整段打印会让 assertMatches 那道
                  -- 「回溯后还有输出」的门禁炸掉，这里只留第一行
                  local t4ok, t4v = pcall(debug.traceback, 5)
                  pr("T4", t4ok, first(t4v))
                  pr("T5", s(debug.traceback(nil, 0)) == "string")
                  local co = coroutine.create(function()
                    pr("T6", (debug.traceback("INCO"):find("INCO", 1, true) ~= nil))
                  end)
                  coroutine.resume(co)
                  -- traceback(协程) 的返回值开头就是 "stack traceback:"，只打类型
                  pr("T7", type(debug.traceback(co)))
                end
                
                -- 6) registry / metatable / uservalue
                pr("R1", s(debug.getregistry()), type(debug.getregistry()))
                pr("R2", debug.getregistry()[1] == _G or debug.getregistry()[2] == _G)
                pr("R3", pc(debug.getregistry, 1))
                pr("R4", pc(debug.setmetatable, 5, {}))
                pr("R5", pc(debug.setmetatable, {}, 5))
                do
                  local t = {}
                  pr("R6", debug.setmetatable(t, { __index = { x = 1 } }) == t, t.x)
                  pr("R7", s(debug.getmetatable(t)), debug.setmetatable(t, nil) == t,
                     s(debug.getmetatable(t)))
                end
                pr("R8", pc(debug.getmetatable))
                pr("R9", pc(debug.getmetatable, 5))
                pr("R10", pc(debug.getuservalue, 5))
                pr("R11", pc(debug.getuservalue, nil))
                pr("R12", pc(debug.setuservalue, 5, {}))
                pr("R13", pc(debug.setuservalue, nil, {}))
                
                -- 7) utf8
                pr("F1", utf8.charpattern)
                pr("F2", utf8.char(65, 66, 0x4E2D, 0x1F600) == "AB\\228\\184\\173\\240\\159\\152\\128")
                pr("F3", #utf8.char(0x1F600), #utf8.char(0x4E2D), #utf8.char(65))
                pr("F4", pc(utf8.char, -1))
                pr("F5", pc(utf8.char, 0x7FFFFFFF))
                pr("F6", pc(utf8.char, 0x110000))
                pr("F7", pc(utf8.char, 1.5))
                pr("F8", utf8.char() == "")
                pr("F9", utf8.codepoint("AB", 1), utf8.codepoint("AB", 2))
                pr("F10", pc(utf8.codepoint, "AB", 3))
                pr("F11", pc(utf8.codepoint, "AB", 0))
                pr("F12", pc(utf8.codepoint, "AB", -1))
                pr("F13", utf8.codepoint("\\228\\184\\173"), utf8.codepoint("\\240\\159\\152\\128"))
                pr("F14", utf8.codepoint("AB", 1, 2))
                pr("F15", pc(utf8.codepoint, "AB", 1, 3))
                pr("F16", pc(utf8.codepoint, "AB", 2, 1))
                pr("F17", utf8.len("AB"), utf8.len("\\228\\184\\173"), utf8.len("\\240\\159\\152\\128"))
                pr("F18", pc(utf8.len, "\\255"))
                pr("F19", utf8.len("AB", 2), utf8.len("AB", 3))
                pr("F20", pc(utf8.len, "AB", 4))
                pr("F21", utf8.len("", 1) == 0, pc(utf8.len, "", 2))
                pr("F22", pc(utf8.len, 5))
                pr("F23", utf8.offset("ABC", 2), utf8.offset("ABC", 4))
                pr("F24", utf8.offset("\\228\\184\\173", 2))
                pr("F25", pc(utf8.offset, "ABC", 0))
                pr("F26", pc(utf8.offset, "ABC", 5))
                pr("F27", utf8.offset("hi\\228\\184\\173", -1))
                pr("F28", pc(utf8.offset, "ABC", -5))
                pr("F29", pc(utf8.offset, "ABC", 1.5))
                -- 遍历 UTF-8 串
                do
                  local out = {}
                  for p, c in utf8.codes("A\\228\\184\\173B") do
                    out[#out + 1] = p .. ":" .. c
                  end
                  pr("F30", table.concat(out, ","))
                  pr("F31", pc(function()
                    for p, c in utf8.codes("\\255") do end
                  end))
                  local out2 = {}
                  for p, c in utf8.codes("AB", 2) do
                    out2[#out2 + 1] = p .. ":" .. c
                  end
                  pr("F32", table.concat(out2, ","))
                end
                -- 用 charpattern 走一遍
                do
                  local cnt = 0
                  for c in ("A\\228\\184\\173\\240\\159\\152\\128"):gmatch(utf8.charpattern) do
                    cnt = cnt + 1
                  end
                  pr("F33", cnt)
                end
                
                print("DONE")
                """);
    }

    /**
     * {@code package} / {@code require} / {@code load} 矩阵 —— 参考实现与
     * 本实现逐字节比对。
     * <p>
     * 铺它的起因是三处<b>只有对着参考实现才看得见</b>的分歧：
     * <ul>
     *   <li>{@code package.loadlib} 的第三个返回值。判据是失败发生在哪一步：
     *       {@code ERRLIB}（库打不开）给 {@code "open"}，{@code ERRFUNC}
     *       （库里没这个符号）给 {@code "absent"}。之前照着「没有 dlopen
     *       的兜底分支」写死成 {@code "absent"}，而那个分支下
     *       {@code lookforfunc} 恒返回 {@code ERRLIB}，照样该给
     *       {@code "open"}；</li>
     *   <li>{@code require} 循环依赖的报错<b>不是</b>裸的
     *       {@code C stack overflow}，而是被 {@code checkload} 包成
     *       {@code error loading module '_pk_b' from file './_pk_b.lua':\n\t
     *       C stack overflow}。原因见 {@code Parser#enterLevel}：解析器的
     *       {@code enterlevel} 与 {@code luaD_call} <b>共用</b> {@code nCcalls}，
     *       于是第 200 层是在某次 {@code luaL_loadfile} 的解析中途炸的，
     *       错误被 {@code luaD_protectedparser} 兜住，才轮到
     *       {@code checkload} 去包；</li>
     *   <li>{@code package.loadlib} 的失败消息由平台 libc 决定（macOS 是
     *       {@code dlopen(...)}，glibc 是 {@code cannot open shared object
     *       file...}），本实现根本不加载动态库——三边文本都不一样，只能比
     *       「是不是字符串」。</li>
     * </ul>
     */
    @Test
    void packageRequireAndLoadMatrixMatchesReference() {
        Diff.assertMatches("""
                -- package / require / load 矩阵。
                -- 注意文件系统副作用：两边跑在<b>同一个 cwd</b>，所以用到的临时文件一律
                -- 「开头先删、用完再删」，保证两次运行都从干净状态开始。
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                local function s(v)
                  local tt = type(v)
                  if tt == "table" or tt == "function" or tt == "thread" or tt == "userdata" then
                    return tt
                  end
                  return tostring(v)
                end
                -- 动态库失败消息由平台 libc 决定（macOS 是 dlopen(...)，glibc 是
                -- "cannot open shared object file..."），本实现根本不加载动态库，
                -- 三边的文本都不一样。这里只比「是不是字符串」，内容归一成一个占位。
                local function dl(v)
                  if type(v) ~= "string" then return v end
                  if v:find("^dlopen%(") or v:find("cannot open shared object")
                      or v:find("dynamic libraries not enabled")
                      or v:find("no such file") or v:find("No such file or directory") then
                    return "<DLERR>"
                  end
                  return v
                end
                -- 临时模块一律写到 target/rt 下：两边的工作目录都是仓库根，直接写根目录
                -- 会在用例失败时留下一堆 _pk_*.lua。
                local D = "target/rt/"
                local function rm(p)
                  return os.remove(D .. p)
                end
                local function mkfile(path, content)
                  local f, err = io.open(D .. path, "w")
                  if not f then return false, err end
                  f:write(content)
                  f:close()
                  return true
                end

                -- 1) package 的静态结构
                pr("A1", string.format("%q", package.config))
                pr("A2", type(package.path), type(package.cpath))
                pr("A3", (package.path:find("?", 1, true) ~= nil), (package.cpath:find("?", 1, true) ~= nil))
                pr("A4", (package.path:find(";", 1, true) ~= nil))
                do
                  local names = {}
                  for k in pairs(package.loaded) do
                    names[#names + 1] = tostring(k)
                  end
                  table.sort(names)
                  pr("A5", table.concat(names, ","))
                end
                pr("A6", type(package.preload), type(package.searchers), #package.searchers)
                for i = 1, #package.searchers do
                  pr("A7", i, type(package.searchers[i]))
                end
                pr("A8", type(package.loadlib), pcall(package.loadlib))
                pr("A9", select(3, package.loadlib("no_such_lib_xyz", "luaopen_x")))
                pr("A10", s(dl(select(2, package.loadlib("no_such_lib_xyz", "luaopen_x")))))
                pr("A11", (function()
                  local r = { pcall(package.loadlib, 5, "x") }
                  local t = { tostring(r[1]) }
                  for i = 2, #r do t[#t + 1] = s(dl(r[i])) end
                  return table.concat(t, "\\t")
                end)())
                pr("A12", select(1, pcall(package.loadlib, "x")))

                -- 2) searchpath
                pr("B1", package.searchpath("nope_xyz", "./?.lua"))
                pr("B2", package.searchpath("nope_xyz", "./?.lua;./?/init.lua"))
                pr("B3", package.searchpath("nope_xyz", ""))
                pr("B4", pc(package.searchpath, "a", "./?.lua", 5))
                pr("B5", pc(package.searchpath, 5, "./?.lua"))
                pr("B6", pc(package.searchpath))
                pr("B7", package.searchpath("", "./?.lua"))
                do
                  local ok = mkfile("_pk_sp.lua", "return 1\\n")
                  pr("B8", ok, package.searchpath("_pk_sp", "./target/rt/?.lua"))
                  pr("B9", package.searchpath("_pk_sp", "./target/rt/?.lua;./target/rt/?.xyz"))
                  rm("_pk_sp.lua")
                  pr("B10", package.searchpath("_pk_sp", "./target/rt/?.lua"))
                end
                do
                  -- sep / rep：把名字里的点换成目录分隔符
                  local ok = mkfile("_pk_sp2.lua", "return 2\\n")
                  pr("B11", ok, package.searchpath("a.b", "./target/rt/?.lua", ".", "_"))
                  pr("B12", package.searchpath("_pk.sp2", "./target/rt/?.lua", ".", "_"))
                  rm("_pk_sp2.lua")
                end

                -- 3) require：从磁盘加载 + 返回值的各种形态
                local OLDPATH = package.path
                package.path = "./target/rt/?.lua"
                local function withModule(name, body, fn)
                  rm(name .. ".lua")
                  mkfile(name .. ".lua", body)
                  package.loaded[name] = nil
                  local r = { pcall(fn) }
                  rm(name .. ".lua")
                  package.loaded[name] = nil
                  return table.unpack(r)
                end
                pr("C1", withModule("_pk_m1", "return {v = 42}\\n", function()
                  local m = require("_pk_m1")
                  return type(m), m.v, package.loaded["_pk_m1"] == m
                end))
                pr("C2", withModule("_pk_m2", "return nil\\n", function()
                  local m = require("_pk_m2")
                  return type(m), tostring(m), tostring(package.loaded["_pk_m2"])
                end))
                pr("C3", withModule("_pk_m3", "return false\\n", function()
                  local m = require("_pk_m3")
                  return type(m), tostring(m), tostring(package.loaded["_pk_m3"])
                end))
                pr("C4", withModule("_pk_m4", "return true\\n", function()
                  local m = require("_pk_m4")
                  return type(m), tostring(m), tostring(package.loaded["_pk_m4"])
                end))
                pr("C5", withModule("_pk_m5", "local x = 1\\n", function()
                  local m = require("_pk_m5")
                  return type(m), tostring(m), tostring(package.loaded["_pk_m5"])
                end))
                pr("C6", withModule("_pk_m6", "return 7\\n", function()
                  local m = require("_pk_m6")
                  return type(m), tostring(m), tostring(package.loaded["_pk_m6"])
                end))
                -- 同一模块取两次：第二次走缓存，文件改了也不重新加载
                rm("_pk_m7.lua")
                mkfile("_pk_m7.lua", "return {v = 1}\\n")
                package.loaded["_pk_m7"] = nil
                do
                  local a = require("_pk_m7")
                  mkfile("_pk_m7.lua", "return {v = 2}\\n")
                  local b = require("_pk_m7")
                  pr("C7", a.v, b.v, a == b)
                  package.loaded["_pk_m7"] = nil
                  local c = require("_pk_m7")
                  pr("C8", c.v, c == a)
                end
                rm("_pk_m7.lua")
                package.loaded["_pk_m7"] = nil
                -- 模块里抛错：loaded 不被设置
                pr("C9", withModule("_pk_m8", "error('mod-boom')\\n", function()
                  return pc(require, "_pk_m8"), tostring(package.loaded["_pk_m8"])
                end))
                -- 循环依赖
                rm("_pk_a.lua")
                rm("_pk_b.lua")
                mkfile("_pk_a.lua", "return require('_pk_b')\\n")
                mkfile("_pk_b.lua", "return require('_pk_a')\\n")
                package.loaded["_pk_a"] = nil
                package.loaded["_pk_b"] = nil
                pr("C10", pc(require, "_pk_a"))
                rm("_pk_a.lua")
                rm("_pk_b.lua")
                package.loaded["_pk_a"] = nil
                package.loaded["_pk_b"] = nil
                -- 找不到的模块
                pr("C11", pc(require, "no_such_module_xyz"))
                pr("C12", pc(require))
                pr("C13", pc(require, 5))
                pr("C14", pc(require, true))
                pr("C15", pc(require, ""))
                pr("C16", pc(require, {}))
                -- preload 优先于磁盘
                rm("_pk_p.lua")
                mkfile("_pk_p.lua", "return 'from-disk'\\n")
                package.loaded["_pk_p"] = nil
                package.preload["_pk_p"] = function(n)
                  return "from-preload"
                end
                pr("C17", require("_pk_p"), package.loaded["_pk_p"])
                package.preload["_pk_p"] = nil
                package.loaded["_pk_p"] = nil
                pr("C18", require("_pk_p"))
                rm("_pk_p.lua")
                package.loaded["_pk_p"] = nil
                -- preload 返回 nil / 不返回
                package.preload["_pk_p2"] = function(n) end
                pr("C19", require("_pk_p2"), tostring(package.loaded["_pk_p2"]))
                package.preload["_pk_p2"] = nil
                package.loaded["_pk_p2"] = nil
                -- 模块名带斜杠
                rm("_pk_d.lua")
                mkfile("_pk_d.lua", "return 'd'\\n")
                package.loaded["_pk_d"] = nil
                pr("C20", pc(require, "_pk_d/x"))
                pr("C21", require("_pk_d"))
                rm("_pk_d.lua")
                package.loaded["_pk_d"] = nil
                package.path = OLDPATH

                -- 4) load：mode / env / chunkname / reader
                pr("D1", s(load("return 1")))
                pr("D2", select(1, load("return 1")) ~= nil)
                pr("D3", pc(function() return load("return 1")() end))
                pr("D4", pc(load, "return 1 +"))
                pr("D5", select(2, load("return 1 +")))
                pr("D6", select(2, load("@@@")))
                -- mode
                pr("E1", s(load("return 1", "=c", "t")))
                pr("E2", s(load("return 1", "=c", "b")))
                pr("E3", s(load("return 1", "=c", "bt")))
                pr("E4", pc(load, "return 1", "=c", "z"))
                pr("E5", pc(load, "return 1", "=c", ""))
                pr("E6", pc(load, "return 1", "=c", 5))
                do
                  local ok, f = pcall(load, "return 1", "=c", "t", 5)
                  pr("E7", ok, s(f))
                end
                -- 二进制块：mode "b" 接受 dump 出来的，"t" 不接受
                do
                  local f = load("return 1")
                  local bin = string.dump(f)
                  pr("E8", s(load(bin, "=b", "b")), s(load(bin, "=b", "t")))
                  pr("E9", s(load(bin, "=b", "bt")))
                  pr("E10", pc(load, bin, "=b", "z"))
                end
                -- 文本块带 # 注释头（bin 模式允许开头有注释行）
                pr("E11", s(load("#comment\\nreturn 1", "=c", "b")))
                -- env
                do
                  local env = { X = 5, print = print }
                  local f = load("return X", "=c", "t", env)
                  pr("F1", f ~= nil, f ~= nil and f())
                  local g = load("X = 9 return X", "=c", "t", env)
                  pr("F2", g(), env.X)
                  local h = load("return _ENV", "=c", "t", env)
                  pr("F3", h() == env)
                  local i2 = load("return print", "=c", "t", env)
                  pr("F4", i2() == print)
                  pr("F5", s(load("return 1", "=c", "t", nil)))
                  pr("F6", s(load("return 1", "=c", "t", 1.0)))
                end
                -- chunkname 影响报错位置
                pr("G1", pc(function() return load("error('x')", "@myfile.lua")() end))
                pr("G2", pc(function() return load("error('x')", "=myname")() end))
                pr("G3", pc(function() return load("error('x')")() end))
                pr("G4", pc(function() return load("error('x')", "plain")() end))
                pr("G5", pc(function() return load("error('x')", 5)() end))
                pr("G6", pc(function() return load("return 1", 5)() end))
                -- reader 函数
                do
                  local parts = { "return ", "1 + ", "2" }
                  local i = 0
                  local f = load(function()
                    i = i + 1
                    return parts[i]
                  end)
                  pr("H1", s(f), f ~= nil and f())
                end
                do
                  -- 一次给整段，然后给 nil 结束
                  local n = 0
                  local f = load(function()
                    n = n + 1
                    if n == 1 then return "return 3" end
                    return nil
                  end)
                  pr("H2", s(f), f ~= nil and f(), n)
                end
                do
                  -- 返回空串也表示结束
                  local n = 0
                  local f = load(function()
                    n = n + 1
                    if n == 1 then return "return 4" end
                    return ""
                  end)
                  pr("H3", s(f), f ~= nil and f())
                end
                do
                  -- 注意：<b>返回数字会挂死</b>（数字也算「字符串」，读取函数永不结束），
                  -- 所以这里只让它返回两次就停
                  local n = 0
                  local f = load(function()
                    n = n + 1
                    if n <= 2 then return 5 end
                    return nil
                  end)
                  pr("H4", s(f), tostring(f))
                end
                pr("H5", pc(load, 5))
                pr("H6", pc(load, nil))
                pr("H7", pc(load, true))
                -- dofile / loadstring
                pr("I1", pc(dofile, "no_such_file_xyz.lua"))
                pr("I2", pc(dofile))
                pr("I3", pc(dofile, 5))
                rm("_pk_df.lua")
                mkfile("_pk_df.lua", "return 11\\n")
                pr("I4", pc(dofile, "./target/rt/_pk_df.lua"))
                rm("_pk_df.lua")
                pr("I5", loadstring == nil)
                pr("I6", loadstring == nil and "gone" or "present")
                -- load 出来的函数的环境与 upvalue
                do
                  local f = load("local a = 1 return function() return a end")
                  local g = f()
                  pr("J1", g())
                  pr("J2", type(load("return ...")))
                  local h = load("return ...")
                  pr("J3", h(1, 2, 3))
                end
                print("DONE")
                """);
    }

    /**
     * {@code io} 库矩阵 —— 参考实现与本实现逐字节比对。
     * <p>
     * {@code io} 原本是十个标准库里铺得最薄的一片（167 行），这一片把它补到
     * 124 行输出：{@code open} 的十六种合法模式与九种非法模式、{@code read}
     * 的全部格式（{@code l} / {@code L} / {@code a} / {@code n} / 数字 / 多格式
     * / 缺省）、{@code write} 的整数与浮点写出形态、{@code seek} 的三个起点与
     * 越界、{@code lines}、{@code setvbuf}、{@code close} 关两次、
     * {@code io.output} / {@code io.input} 的切换，以及二进制安全
     * （含 {@code \0} 与非法 UTF-8）。
     * <p>
     * 咬出一处：<b>{@code seek} 的负目标位置是 {@code EINVAL}(22)</b>，不是随便
     * 一个 IO 错。C 的 {@code fseek} 在 {@code SET} / {@code CUR} / {@code END}
     * 三种起点上都是这个判据（实测三种都给 {@code nil, "Invalid argument", 22}），
     * 而 Java 的 {@code RandomAccessFile.seek} 抛的是 "Negative seek offset"，
     * 掉进 {@code errnoOf} 的兜底分支就成了 {@code EIO}(5)。往 EOF <b>之后</b>
     * 定位是<b>允许</b>的，别一起拦。
     * <p>
     * 另有两处不属于分歧、只能绕开：{@code fopen} 一个<b>目录</b>在 macOS 与
     * glibc 上都成功但读的行为不同，而 JVM 压根不允许把目录当流打开；
     * {@code print} 走的是 C 层 {@code stdout}，不受 {@code io.output} 切换影响
     * —— 这两条写进比对只会两边都假红。
     */
    @Test
    void ioLibraryDeepMatrixMatchesReference() {
        Diff.assertMatches("""
                -- io 库矩阵。
                -- 临时文件一律放 target/rt/（两边 cwd 都是仓库根，写根目录会留垃圾）。
                local D = "target/rt/"
                local function P(n)
                  return D .. n
                end
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                -- 文件句柄带地址，只打类型
                local function s(v)
                  local tt = type(v)
                  if tt == "table" or tt == "function" or tt == "thread" or tt == "userdata" then
                    return tt
                  end
                  return tostring(v)
                end
                local function rm(p)
                  return os.remove(P(p))
                end
                local function mk(name, content)
                  local f = io.open(P(name), "wb")
                  if not f then return false end
                  f:write(content)
                  f:close()
                  return true
                end
                local function slurp(name)
                  local f = io.open(P(name), "rb")
                  if not f then return nil end
                  local c = f:read("a")
                  f:close()
                  return c
                end

                -- 1) 静态结构
                pr("A1", s(io.stdout), s(io.stderr), s(io.stdin))
                pr("A2", io.type(io.stdout), io.type(io.stderr), io.type(io.stdin))
                pr("A3", io.type(1), io.type("x"), io.type({}), io.type(nil))
                pr("A4", s(io.open), s(io.lines), s(io.read), s(io.write), s(io.close))
                pr("A5", pc(io.type))

                -- 2) open：模式的各种形态
                pr("B1", pc(io.open, P("_io_nope"), "r"))
                pr("B2", s(select(1, io.open(P("_io_w"), "w"))))
                rm("_io_w")
                local modes = { "r", "w", "a", "r+", "w+", "a+", "rb", "wb", "ab", "r+b", "w+b",
                                "a+b", "br", "bw", "rt", "wt" }
                -- 先把文件建出来：不然第 1 个 "r" 的成败取决于上一次运行有没有留下残骸，
                -- 两边跑的先后一变结论就反了。
                mk("_io_mode", "x")
                for i, m in ipairs(modes) do
                  -- 坏模式是<b>抛错</b>不是返 nil+消息（lauxlib 的 l_checkmode 判据是
                  -- '[rwa]%+?b?'），所以必须走 pcall，否则脚本在这里就断了。
                  local ok, f, e = pcall(io.open, P("_io_mode"), m)
                  pr("B3", i, m, ok, s(f), e)
                  if ok and f then f:close() end
                end
                rm("_io_mode")
                -- 坏模式
                local bad = { "rw", "x", "rr", "r++", "+", "b", "", "r+x", "w+b+" }
                for i, m in ipairs(bad) do
                  pr("B4", i, string.format("%q", m), select(2, pc(io.open, P("_io_bad"), m)))
                end
                rm("_io_bad")
                -- 缺省模式是 "r"
                do
                  local f, e = io.open(P("_io_def"))
                  pr("B5", select(2, pc(io.open, P("_io_def"))))
                  if f then f:close() end
                end
                rm("_io_def")
                -- 参数类型（数字会被就地转成字符串：真的去打开叫 "5" 的文件。
                -- 先删一次：H8/H9 的 io.output(5) 会把它建出来，留在盘上会让下一轮 B6 的
                -- 成败反过来 —— 这种「上一次运行的残骸决定这一次结果」的坑已经踩过一回。）
                os.remove("5")
                pr("B6", pc(io.open, 5))
                pr("B7", pc(io.open, nil))
                pr("B8", pc(io.open, "x", 5))
                pr("B9", pc(io.open))
                -- 「打开目录」不测：fopen 一个目录在 macOS 与 glibc 上都成功，但读的行为不同，
                -- 而 JVM 根本不允许把目录当流打开。属于平台能力差异，放进比对只会两边都假红。

                -- 3) 读：格式串
                mk("_io_r", "line1\\nline2\\n\\nline4\\n")
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C1", f:read("l"), f:read("l"), f:read("l"), f:read("l"), f:read("l"))
                  f:close()
                end
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C2", string.format("%q", f:read("L")), string.format("%q", f:read("L")))
                  f:close()
                end
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C3", string.format("%q", f:read("a")))
                  f:close()
                end
                do
                  -- 数字：各种形态
                  local f = io.open(P("_io_num"), "w")
                  f:write("12 3.5 -7 0x10 1e3 .5 5. .inf -.inf nan 12abc\\n")
                  f:close()
                  local g = io.open(P("_io_num"), "r")
                  local out = {}
                  for i = 1, 12 do out[i] = tostring(g:read("n")) end
                  pr("C4", table.concat(out, ","))
                  pr("C5", g:read("n"), g:read("n"))
                  g:close()
                  rm("_io_num")
                end
                do
                  -- 一次读多个格式
                  local f = io.open(P("_io_r"), "r")
                  pr("C6", f:read("l", "l", "l"))
                  f:close()
                end
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C7", string.format("%q", f:read(3)), string.format("%q", f:read(0)),
                     string.format("%q", f:read(100)))
                  f:close()
                end
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C8", pc(f.read, f, "x"))
                  pr("C9", pc(f.read, f, 5, "x"))
                  f:close()
                end
                -- 已关闭 / 只写打开
                do
                  local f = io.open(P("_io_r"), "r")
                  f:close()
                  pr("C10", pc(f.read, f, "l"))
                  pr("C11", pc(f.close, f))
                end
                do
                  local f = io.open(P("_io_r2"), "w")
                  pr("C12", pc(f.read, f, "l"))
                  f:close()
                  rm("_io_r2")
                end
                -- 缺省格式是 "l"
                do
                  local f = io.open(P("_io_r"), "r")
                  pr("C13", string.format("%q", f:read()))
                  f:close()
                end
                -- 空文件
                do
                  mk("_io_empty", "")
                  local f = io.open(P("_io_empty"), "r")
                  pr("C14", f:read("l"), f:read("a"), f:read("L"), f:read("n"), f:read(10))
                  f:close()
                  rm("_io_empty")
                end
                -- 没有最后的换行
                do
                  mk("_io_nonl", "abc")
                  local f = io.open(P("_io_nonl"), "r")
                  pr("C15", string.format("%q", f:read("l")), string.format("%q", f:read("L")))
                  f:close()
                  rm("_io_nonl")
                end

                -- 4) 写
                do
                  local f = io.open(P("_io_w2"), "w")
                  pr("D1", s(f:write("a")), s(f:write("b", "c")), s(f:write(1)), s(f:write(2.5)))
                  pr("D2", s(f:write()))
                  pr("D3", pc(f.write, f, {}))
                  pr("D4", pc(f.write, f, nil))
                  pr("D5", pc(f.write, f, true))
                  f:close()
                  pr("D6", string.format("%q", slurp("_io_w2")))
                  rm("_io_w2")
                end
                do
                  -- 追加
                  local f = io.open(P("_io_ap"), "w")
                  f:write("x")
                  f:close()
                  local g = io.open(P("_io_ap"), "a")
                  g:write("y")
                  g:close()
                  pr("D7", string.format("%q", slurp("_io_ap")))
                  rm("_io_ap")
                end
                do
                  mk("_io_rd", "x")
                  local f = io.open(P("_io_rd"), "r")
                  pr("D8", pc(f.write, f, "z"))
                  f:close()
                  rm("_io_rd")
                end
                -- 整数的写出形态（区分整数与浮点）
                do
                  local f = io.open(P("_io_num2"), "w")
                  f:write(1, 1.0, -0.0, 1e15, 2^53, math.maxinteger, 0.1)
                  f:close()
                  pr("D9", string.format("%q", slurp("_io_num2")))
                  rm("_io_num2")
                end

                -- 5) seek
                do
                  local f = io.open(P("_io_sk"), "w")
                  f:write("0123456789")
                  f:close()
                  local g = io.open(P("_io_sk"), "r")
                  pr("E1", g:seek("set", 3), string.format("%q", g:read(2)))
                  pr("E2", g:seek("cur"), g:seek("cur", -2), string.format("%q", g:read(2)))
                  pr("E3", g:seek("end"), g:seek("end", -3), string.format("%q", g:read(3)))
                  pr("E4", g:seek("set"), string.format("%q", g:read(1)))
                  pr("E5", g:seek())
                  pr("E6", pc(g.seek, g, "nope"))
                  pr("E7", pc(g.seek, g, "set", "x"))
                  pr("E8", pc(g.seek, g, 5))
                  pr("E9", g:seek("set", 1000), g:read(1), g:read("a"))
                  pr("E10", pc(g.seek, g, "set", -1))
                  g:close()
                  pr("E11", pc(g.seek, g, "set", 0))
                  rm("_io_sk")
                end

                -- 6) lines
                do
                  mk("_io_ln", "a\\nb\\nc")
                  local out = {}
                  for l in io.lines(P("_io_ln")) do out[#out + 1] = l end
                  pr("F1", table.concat(out, ","))
                  local out2 = {}
                  for l in io.lines(P("_io_ln"), "L") do out2[#out2 + 1] = string.format("%q", l) end
                  pr("F2", table.concat(out2, ","))
                  local out3 = {}
                  for a, b in io.lines(P("_io_ln"), "l", "l") do out3[#out3 + 1] = tostring(a) .. tostring(b) end
                  pr("F3", table.concat(out3, ","))
                  pr("F4", pc(function()
                    for l in io.lines(P("_io_nope2")) do end
                  end))
                  -- 文件句柄的 lines
                  local f = io.open(P("_io_ln"), "r")
                  local out4 = {}
                  for l in f:lines() do out4[#out4 + 1] = l end
                  pr("F5", table.concat(out4, ","))
                  f:close()
                  pr("F6", pc(function()
                    for l in f:lines() do end
                  end))
                  rm("_io_ln")
                end

                -- 7) flush / setvbuf / close
                do
                  local f = io.open(P("_io_fl"), "w")
                  pr("G1", f:flush())
                  pr("G2", pc(f.setvbuf, f, "no"))
                  pr("G3", pc(f.setvbuf, f, "full", 1024))
                  pr("G4", pc(f.setvbuf, f, "line"))
                  pr("G5", pc(f.setvbuf, f, "nope"))
                  pr("G6", pc(f.setvbuf, f, "full", "x"))
                  pr("G7", pc(f.setvbuf, f))
                  f:write("z")
                  f:close()
                  pr("G8", string.format("%q", slurp("_io_fl")))
                  pr("G9", pc(f.close, f)) -- 关第二次是<b>抛错</b>，不是返回 nil+消息
                  pr("G10", io.type(f))
                  rm("_io_fl")
                end
                do
                  local f = io.open(P("_io_cl"), "w")
                  pr("G11", f:close(), io.type(f))
                  pr("G12", pc(f.flush, f))
                  pr("G13", pc(f.write, f, "x"))
                  rm("_io_cl")
                end
                pr("G14", pc(io.close, 5))
                pr("G15", pc(io.close))

                -- 8) io.output / io.input
                do
                  local old = io.output()
                  pr("H1", io.type(old))
                  local f = io.open(P("_io_out"), "w")
                  pr("H2", io.type(io.output(f)))
                  pr("H3", s(io.write("123")), s(io.write(4)))
                  io.output(old)
                  pr("H4", string.format("%q", slurp("_io_out")))
                  rm("_io_out")
                end
                do
                  local f = io.open(P("_io_in"), "w")
                  f:write("q")
                  f:close()
                  local old = io.input()
                  local g = io.open(P("_io_in"), "r")
                  pr("H5", io.type(io.input(g)))
                  pr("H6", string.format("%q", io.read("a")))
                  io.input(old)
                  pr("H7", io.type(io.input()))
                  rm("_io_in")
                end
                -- io.output(5) / io.input(5) 返回的是新句柄（带地址），只比「成了没有」与类型
                pr("H8", select(1, pc(io.output, 5)), io.type(io.output()))
                pr("H9", select(1, pc(io.input, 5)), io.type(io.input()))
                -- 上面两句把默认输入／输出切到叫 "5" 的文件上了，用完还原并删掉它
                io.output(io.stdout)
                io.input(io.stdin)
                os.remove("5")

                -- 9) 句柄的元表 / tostring / 相等
                do
                  local f = io.open(P("_io_mt"), "w")
                  local g = io.open(P("_io_mt2"), "w")
                  pr("I1", f == g, f == f)
                  pr("I2", tostring(f) == tostring(f))
                  pr("I3", (tostring(f):sub(1, 6)))
                  pr("I4", pc(function() return f .. "" end))
                  pr("I5", pc(function() return #f end))
                  pr("I6", pc(function() return f.x end))
                  pr("I7", pc(function() f.x = 1 end))
                  f:close()
                  g:close()
                  rm("_io_mt")
                  rm("_io_mt2")
                end

                -- 10) 二进制安全：含 \\0 与非法 UTF-8
                do
                  local f = io.open(P("_io_bin"), "wb")
                  f:write("a\\0b\\255\\254c")
                  f:close()
                  local g = io.open(P("_io_bin"), "rb")
                  local c = g:read("a")
                  pr("J1", #c, string.format("%q", c))
                  g:close()
                  rm("_io_bin")
                end

                rm("_io_r")
                rm("_io_w")
                pr("DONE")
                """);
    }

    /**
     * {@code os} 库矩阵的下半边 —— 参考实现与本实现逐字节比对。
     * <p>
     * 既有那一片（854 行）铺的是 {@code os.date} 的每个转换说明符与
     * {@code tonumber} 的全进制；这一片铺它没盖住的：{@code os.time} 的
     * 各种表形态（非闰年进位、月/日/时越界、缺省值、多余字段）、
     * {@code os.difftime}、{@code os.getenv}、{@code os.remove} /
     * {@code os.rename}、{@code os.tmpname}、{@code os.execute} 的退出码、
     * {@code os.setlocale}。
     * <p>
     * 咬出一处：<b>{@code os.time} 的字段是 {@code lua_tointegerx}，带字符串
     * 转换</b>。{@code os.time{year = "2000", month = 1, day = 1}} 在参考实现里
     * 就是 2000 年，而我们报 {@code field 'year' is not an integer}——
     * {@code cvt2num} 让可解析的字符串也走 {@code luaV_tointeger}，库函数参数
     * 走的是会做类型转换的那一套，与 VM 算术指令刻意不转换的做法相反。
     * 顺带钉住 {@code month} / {@code day} 是<b>必填</b>（缺了报
     * {@code field 'x' missing in date table}），以及大到越出 int 的字段报
     * {@code field 'x' is out-of-bound}。
     * <p>
     * 两处刻意不测：{@code year = -100} 这种极端年份（{@code mktime} 的可用
     * 范围随 libc 而定，macOS 报 {@code time result cannot be represented}），
     * 以及删<b>目录</b>（{@code ENOTEMPTY} 的数值 macOS 是 66、glibc 是 39）。
     */
    @Test
    void osTimeAndProceduralMatrixMatchesReference() {
        Diff.assertMatches("""
                -- os 库矩阵（补既有那片没盖住的半边）。
                -- 既有那片铺的是 os.date 的每个转换说明符（UTC、固定时间戳）；这一片铺
                -- os.time 的各种表形态与越界、difftime、getenv、remove/rename、tmpname、
                -- execute、setlocale，以及 os.date 的参数校验。
                -- 时区：两边同一台机器、同一份环境，所以本地时间的绝对数值是可比的。
                local D = "target/rt/"
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                local function s(v)
                  local tt = type(v)
                  if tt == "table" or tt == "function" or tt == "thread" or tt == "userdata" then
                    return tt
                  end
                  return tostring(v)
                end
                local function rm(p)
                  return os.remove(D .. p)
                end
                local function mk(name, content)
                  local f = io.open(D .. name, "wb")
                  if not f then return false end
                  f:write(content or "x")
                  f:close()
                  return true
                end

                -- 1) 静态结构
                pr("A1", s(os.time), s(os.date), s(os.difftime), s(os.getenv))
                pr("A2", s(os.remove), s(os.rename), s(os.tmpname), s(os.execute), s(os.exit))
                pr("A3", s(os.clock), s(os.setlocale))
                -- os.time() 无参与 os.clock 的数值随时刻而变，只比形状
                pr("A4", math.type(os.time()), math.type(os.clock()))

                -- 2) os.time：给表
                local T0 = os.time({ year = 1970, month = 1, day = 1, hour = 0 })
                pr("B1", T0)
                pr("B2", os.time({ year = 1970, month = 1, day = 2, hour = 0 }) - T0)
                pr("B3", os.time({ year = 2000, month = 6, day = 15, hour = 12, min = 30, sec = 45 }) - T0)
                pr("B4", os.time({ year = 2024, month = 2, day = 29, hour = 0 }) - T0)
                pr("B5", os.time({ year = 2023, month = 2, day = 29, hour = 0 }) - T0) -- 非闰年，进位
                pr("B6", os.time({ year = 2000, month = 13, day = 1, hour = 0 }) - T0)  -- 月越界
                pr("B7", os.time({ year = 2000, month = 0, day = 1, hour = 0 }) - T0)
                pr("B8", os.time({ year = 2000, month = 1, day = 0, hour = 0 }) - T0)
                pr("B9", os.time({ year = 2000, month = 1, day = 32, hour = 0 }) - T0)
                pr("B10", os.time({ year = 2000, month = 1, day = 1, hour = 24 }) - T0)
                pr("B11", os.time({ year = 2000, month = 1, day = 1, hour = 0, min = 60 }) - T0)
                pr("B12", os.time({ year = 2000, month = 1, day = 1, hour = 0, sec = 60 }) - T0)
                pr("B13", os.time({ year = 2000, month = 1, day = 1, hour = -1 }) - T0)
                -- month / day 是<b>必填</b>（getfield 的 d < 0），year 可选、hour 缺省 12。
                -- 这两句是抛错，必须走 pcall，否则脚本在这里就断了。
                pr("B14", pc(os.time, { year = 2000 }))
                pr("B15", pc(os.time, { year = 2000, month = 1 }))
                pr("B15b", os.time({ year = 2000, month = 1, day = 1 }) - T0) -- hour 缺省 12
                -- 多余字段被忽略
                pr("B16", os.time({ year = 2000, month = 3, day = 4, hour = 5,
                                    yday = 999, wday = 9, isdst = true, extra = 1 })
                           - os.time({ year = 2000, month = 3, day = 4, hour = 5 }))
                pr("B17", os.time({ year = 2024, month = 7, day = 1, hour = 12, isdst = true })
                           - os.time({ year = 2024, month = 7, day = 1, hour = 12, isdst = false }))
                -- 参数形态
                pr("B18", pc(os.time, 5))
                pr("B19", pc(os.time, "x"))
                pr("B20", pc(os.time, { year = "2000", month = 1, day = 1 }))
                pr("B21", pc(os.time, { year = 2000.5, month = 1, day = 1 }))
                pr("B22", pc(os.time, { year = 2000, month = 1, day = 1, hour = true }))
                pr("B23", pc(os.time, { year = 10000, month = 1, day = 1, hour = 0 }))
                -- year = -100 不测：mktime 的可用范围随 libc 而定（macOS 报
                -- "time result cannot be represented"，glibc 未必），放进比对只在本地对。
                pr("B25", pc(os.time, {}))
                pr("B26", pc(os.time, { year = 2 ^ 40, month = 1, day = 1 }))
                pr("B27", pc(os.time, { year = -2 ^ 40, month = 1, day = 1 }))
                pr("B28", pc(os.time, { year = 2000, month = 2 ^ 40, day = 1 }))
                pr("B29", pc(os.time, { year = 2000, month = 1, day = "3" }))
                pr("B30", pc(os.time, { year = "2000", month = "1", day = "1", hour = "12" }))

                -- 3) os.date 的参数校验与非 UTC 的 *t
                do
                  local t = os.time({ year = 2000, month = 6, day = 15, hour = 12, min = 30, sec = 45 })
                  local d = os.date("*t", t)
                  local keys = { "year", "month", "day", "hour", "min", "sec", "wday", "yday" }
                  local out = {}
                  for _, k in ipairs(keys) do out[#out + 1] = k .. "=" .. tostring(d[k]) end
                  pr("C1", table.concat(out, ","))
                  pr("C2", type(d.isdst))
                  local keys2 = {}
                  for k in pairs(d) do keys2[#keys2 + 1] = k end
                  table.sort(keys2)
                  pr("C3", table.concat(keys2, ","))
                  -- 缺省格式是 %c；缺省时间是「现在」，只比长度
                  pr("C4", (os.date(nil, t)):len() == (os.date("%c", t)):len())
                  pr("C5", (os.date("%Y-%m-%d")):len())
                  pr("C6", pc(os.date, "%Y", "x"))
                  pr("C7", pc(os.date, 5, t))
                  pr("C8", pc(os.date, "%Y", 5))
                  pr("C9", pc(os.date, "%"))
                  pr("C10", pc(os.date, "%Q", t))
                  pr("C11", pc(os.date, "*t", 2 ^ 62))
                  pr("C12", pc(os.date, "%Y", -1))
                  local function fields(d)
                    local out = {}
                    for _, k in ipairs({ "year", "month", "day", "hour", "min", "sec", "wday", "yday" }) do
                      out[#out + 1] = tostring(d[k])
                    end
                    return table.concat(out, ",")
                  end
                  pr("C13", fields(os.date("*t", 0)))
                  pr("C14", fields(os.date("!*t", 0)))
                  -- 带前导的其他 "*" 变体
                  pr("C15", pc(os.date, "*x", t))
                end

                -- 4) difftime
                pr("D1", os.difftime(100, 50), math.type(os.difftime(100, 50)))
                pr("D2", os.difftime(50, 100))
                pr("D3", pc(os.difftime, 1))
                pr("D4", pc(os.difftime))
                pr("D5", pc(os.difftime, "x", 1))
                pr("D6", pc(os.difftime, 1, "x"))

                -- 5) getenv
                pr("E1", type(os.getenv("PATH")))
                pr("E2", os.getenv("_NO_SUCH_ENV_VAR_XYZ_"))
                pr("E3", pc(os.getenv))
                pr("E4", pc(os.getenv, 5))
                pr("E5", pc(os.getenv, true))

                -- 6) remove / rename
                do
                  rm("_os_a")
                  pr("F1", mk("_os_a"), os.remove(D .. "_os_a") == true)
                  pr("F2", os.remove(D .. "_os_a")) -- 已删：nil + 消息 + errno
                end
                do
                  rm("_os_b")
                  rm("_os_c")
                  pr("F3", mk("_os_b"), os.rename(D .. "_os_b", D .. "_os_c") == true)
                  pr("F4", io.open(D .. "_os_c", "r") ~= nil)
                  rm("_os_c")
                  pr("F5", os.rename(D .. "_os_b", D .. "_os_c")) -- 源不存在
                end
                pr("F6", pc(os.remove))
                pr("F7", pc(os.remove, 5))
                pr("F8", pc(os.rename, "x"))
                pr("F9", pc(os.rename, "x", 5))
                -- 删目录不测：ENOTEMPTY 的数值 macOS 是 66、glibc 是 39，不可比。
                pr("F10", pc(os.remove, D .. "_os_a", "x")) -- 多余参数
                rm("_os_b")
                rm("_os_c")

                -- 7) tmpname
                do
                  local n = os.tmpname()
                  pr("G1", type(n), n:len() > 0)
                  local f = io.open(n, "w")
                  pr("G2", f ~= nil)
                  if f then
                    f:write("x")
                    f:close()
                    pr("G3", os.remove(n) == true)
                  end
                end

                -- 8) execute
                pr("H1", os.execute("exit 0"))
                pr("H2", os.execute("exit 3"))
                pr("H3", os.execute("exit 1"))
                pr("H4", pc(os.execute))
                pr("H5", pc(os.execute, 5))
                pr("H6", pc(os.execute, "x", "y"))

                -- 9) setlocale：只试 "C" 与查询，且放在最后（改了会影响 %a/%B 的名字）
                pr("I1", pc(os.setlocale, "C"))
                pr("I2", pc(os.setlocale, "C", "numeric"))
                pr("I3", pc(os.setlocale, nil, "nope"))
                pr("I4", pc(os.setlocale, 5))

                pr("DONE")

                print("DONE")
                """);
    }

    /**
     * 弱表 / {@code __gc} / {@code <close>} 矩阵 —— 参考实现与本实现逐字节比对。
     * <p>
     * 这是整个项目<b>语义风险最大</b>的一块：我们<b>没有收集器</b>（{@code lgc.c}
     * 不移植），弱表靠 {@code WeakReference}、{@code __gc} 靠 {@code Cleaner}
     * 重建。所以这块必须钉住，不能只靠 {@code GcTest} 那 12 个手写用例。
     * <p>
     * 咬出一处：<b>{@code collectgarbage("generational"/"incremental")} 的判据
     * 是 {@code isdecGCmodegen(g)}，不是 {@code gckind} 本身</b>：
     * <pre>
     * #define isdecGCmodegen(g)  (g->gckind == KGC_GEN || g->lastatomic != 0)
     * </pre>
     * 而 {@code lastatomic} 在<b>头一次真正收集之后</b>就非 0 —— 加载脚本本身
     * 就要分配，所以用户代码刚跑起来时它已经不是 0。实测参考实现连续切换的
     * 五步是 {@code generational, incremental, incremental, generational,
     * incremental}；只记 {@code gckind} 的话<b>第一行就假</b>。
     * <p>
     * 不比的两处：{@code collectgarbage("count")} 的数值（没有收集器，无从统计）
     * 与 {@code collectgarbage("step")} 的返回值（参考实现的一「步」永远走不完
     * 一轮，恒 {@code false}；我们没有增量收集器，一步就是一整轮，恒
     * {@code true}。取 {@code false} 会让 {@code repeat until
     * collectgarbage("step")} 这类写法死循环，所以这里<b>刻意</b>取 {@code true}）。
     */
    @Test
    void weakTableAndFinalizerMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 弱表 / __gc / to-be-closed 矩阵（不含任何随实现而变的量：
                -- collectgarbage("count") 的数值与增量收集的进度都不可比）。
                local function pr(...)
                  print(...)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  for i = 2, #r do t[#t + 1] = tostring(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                local function count(t)
                  local n = 0
                  for _ in pairs(t) do n = n + 1 end
                  return n
                end

                -- 1) 弱键 / 弱值 / 双弱
                do
                  local kt = setmetatable({}, { __mode = "k" })
                  local vt = setmetatable({}, { __mode = "v" })
                  local kvt = setmetatable({}, { __mode = "kv" })
                  do
                    local k = {}
                    kt[k] = 1
                  end
                  do
                    local v = {}
                    vt[1] = v
                  end
                  do
                    local k, v = {}, {}
                    kvt[k] = v
                  end
                  collectgarbage("collect")
                  pr("A1", count(kt), count(vt), count(kvt))
                end
                -- 强引用还在时不该被收
                do
                  local keep = {}
                  local kt = setmetatable({}, { __mode = "k" })
                  kt[keep] = true
                  collectgarbage("collect")
                  pr("A2", count(kt), kt[keep])
                end
                do
                  local keep = {}
                  local vt = setmetatable({}, { __mode = "v" })
                  vt[1] = keep
                  collectgarbage("collect")
                  pr("A3", count(vt), vt[1] == keep)
                end
                -- 弱值指向「只被弱值引用」的表：两个弱表互相引用也会被收
                do
                  local vt = setmetatable({}, { __mode = "v" })
                  do
                    local a, b = {}, {}
                    a[1] = b
                    b[1] = a
                    vt[1] = a
                  end
                  collectgarbage("collect")
                  pr("A4", count(vt))
                end
                -- 字符串是<b>值</b>，不是可收集对象，弱值里的字符串不会被收
                do
                  local vt = setmetatable({}, { __mode = "v" })
                  vt[1] = "abc"
                  vt[2] = 42
                  vt[3] = true
                  collectgarbage("collect")
                  pr("A5", vt[1], vt[2], vt[3], count(vt))
                end
                -- __mode 的取值：'k' / 'v' / 'kv'，其它字符不影响
                do
                  local modes = { "k", "v", "kv", "vk", "K", "V", "", "x", "kk", "kvx" }
                  for i, m in ipairs(modes) do
                    local t = setmetatable({}, { __mode = m })
                    do
                      local k = {}
                      t[k] = 1
                    end
                    collectgarbage("collect")
                    pr("A6", i, string.format("%q", m), count(t))
                  end
                end
                -- __mode 不是字符串：不生效（不报错）
                do
                  local t = setmetatable({}, { __mode = 5 })
                  do
                    local k = {}
                    t[k] = 1
                  end
                  collectgarbage("collect")
                  pr("A7", count(t))
                end
                -- 弱表本身被收掉不影响其它
                do
                  local n = 0
                  for i = 1, 50 do
                    local t = setmetatable({}, { __mode = "k" })
                    t[{}] = i
                    n = n + count(t)
                  end
                  collectgarbage("collect")
                  pr("A8", n >= 50)
                end
                -- 弱表迭代时不会中途变化（收完之后再迭代）
                do
                  local t = setmetatable({}, { __mode = "k" })
                  for i = 1, 20 do t[{}] = i end
                  collectgarbage("collect")
                  pr("A9", count(t))
                end

                -- 2) __gc
                do
                  local n = 0
                  do
                    local t = setmetatable({}, { __gc = function() n = n + 1 end })
                  end
                  collectgarbage("collect")
                  pr("B1", n)
                end
                do
                  -- 多个对象：每个都该跑到
                  local n = 0
                  for i = 1, 20 do
                    local t = setmetatable({}, { __gc = function() n = n + 1 end })
                  end
                  collectgarbage("collect")
                  pr("B2", n == 20, n)
                end
                do
                  -- __gc 收到的参数是被收集的那个表
                  local got
                  do
                    local t = setmetatable({}, { __gc = function(x) got = x end })
                  end
                  collectgarbage("collect")
                  pr("B3", type(got))
                end
                do
                  -- 还被强引用着的不跑
                  local n = 0
                  local keep = setmetatable({}, { __gc = function() n = n + 1 end })
                  collectgarbage("collect")
                  pr("B4", n, type(keep))
                end
                do
                  -- __gc 不是函数：设元表时不报错，收的时候也不报错
                  local ok = pcall(function()
                    local t = setmetatable({}, { __gc = 5 })
                    return t
                  end)
                  collectgarbage("collect")
                  pr("B5", ok)
                end
                do
                  -- __gc 里报错：不扩散（收集期的错误会被吞掉）
                  local n = 0
                  do
                    local t = setmetatable({}, {
                      __gc = function() n = n + 1 error("gc-boom") end,
                    })
                  end
                  local ok = pcall(collectgarbage, "collect")
                  pr("B6", ok, n)
                end
                do
                  -- 先设元表再加 __gc 是无效的（C 只在 setmetatable 那一刻检查）
                  local n = 0
                  do
                    local t = setmetatable({}, {})
                    getmetatable(t).__gc = function() n = n + 1 end
                  end
                  collectgarbage("collect")
                  pr("B7", n)
                end

                -- 3) to-be-closed
                do
                  local log = {}
                  do
                    local f <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "f:" .. tostring(err) end,
                    })
                    local g <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "g:" .. tostring(err) end,
                    })
                  end
                  pr("C1", table.concat(log, ","))
                end
                do
                  -- 出错时的 err 参数，以及收尾顺序（后声明的先关）
                  local log = {}
                  local ok, e = pcall(function()
                    local f <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "f:" .. tostring(err) end,
                    })
                    local g <close> = setmetatable({}, {
                      __close = function(_, err) log[#log + 1] = "g:" .. tostring(err) end,
                    })
                    error("boom")
                  end)
                  pr("C2", ok, e, table.concat(log, ","))
                end
                do
                  -- __close 里再抛错：替换原错误
                  local ok, e = pcall(function()
                    local f <close> = setmetatable({}, {
                      __close = function() error("close-boom") end,
                    })
                    error("orig")
                  end)
                  pr("C3", ok, e)
                end
                do
                  -- return 时也要关
                  local log = {}
                  local function f()
                    local x <close> = setmetatable({}, {
                      __close = function() log[#log + 1] = "x" end,
                    })
                    return 1
                  end
                  pr("C4", f(), table.concat(log, ","))
                end
                do
                  -- 没有 __close 的常量 <close>：报错
                  pr("C5", pc(load("local x <close> = 5 return x")))
                  pr("C6", pc(load("local x <const> <close> = nil return 1")))
                  pr("C7", pc(load("return 1")) ~= nil)
                end
                do
                  -- <close> 变量不能被重新赋值 / 不能是 nil（nil 与 false 不关）
                  local log = {}
                  do
                    local x <close> = nil
                  end
                  pr("C8", table.concat(log, ","))
                end

                -- 4) collectgarbage 的参数形态
                pr("D1", pc(collectgarbage, "nope"))
                pr("D2", pc(collectgarbage, 5))
                pr("D3", pc(collectgarbage))
                pr("D4", type(collectgarbage("isrunning")))
                pr("D5", pc(collectgarbage, "collect", "x"))
                pr("D6", collectgarbage("collect"))
                pr("D7", pc(collectgarbage, "setpause", 200))
                pr("D8", pc(collectgarbage, "setstepmul", 100))
                pr("D9", pc(collectgarbage, "incremental"))
                pr("D10", pc(collectgarbage, "generational"))
                -- 停了之后 count 与 collect 仍然可用
                do
                  collectgarbage("stop")
                  pr("D11", collectgarbage("isrunning"), type(collectgarbage("count")))
                  pr("D12", collectgarbage("collect"))
                  collectgarbage("restart")
                  pr("D13", collectgarbage("isrunning"))
                end
                -- 注意：不比 "count" 的数值，也不比 "step" 的返回值 —— 那两个随
                -- 收集器实现而变（我们没有增量收集器，一步就是一整轮）。

                pr("DONE")

                print("DONE")
                """);
    }

    /**
     * {@code debug} × 协程 交叉矩阵 —— 参考实现与本实现逐字节比对。
     * <p>
     * 两边各自都铺过（114 / 99 行），但<b>交叉</b>的那一层没有：给协程做
     * {@code getinfo} / {@code getlocal} / {@code traceback} / {@code sethook}、
     * 钩子的<b>计数</b>（而不是逐条打印——计数本身就是对行号表与调用序列的强
     * 校验，{@code tail call} 事件也钉在里面）、registry / uservalue /
     * setmetatable、以及协程状态机的补充。
     * <p>
     * 咬出一处：<b>{@code _LOADED} 必须挂在注册表上</b>。
     * <pre>
     * #define LUA_LOADED_TABLE  "_LOADED"     // lauxlib.h
     * </pre>
     * C 的 {@code package.loaded} 是<b>注册表里的一个字段</b>，不是别处另存
     * 一份：{@code debug.getregistry()["_LOADED"]} 得拿得到它。之前我们只在
     * {@code LuaState} 里存了一份，于是 {@code type(...)} 给 {@code nil} 而
     * 参考实现给 {@code table} —— 靠注册表做手脚的脚本会直接失效。改成从
     * 注册表里读：脚本把 {@code _LOADED} 换掉时我们也跟着换。
     */
    @Test
    void debugAndCoroutineMatrixMatchesReference() {
        /*
         * 用 assertFullMatches：脚本中途打印了 debug.traceback(co)，
         * assertMatches 会把回溯之后的内容全部静默砍掉。回溯里没有地址，
         * 两边同一份脚本、同一个 chunk 名，可以整段比。
         */
        Diff.assertFullMatches("""
                -- debug × 协程 交叉矩阵。
                -- 原则：凡是带地址、带实现细节的（函数地址、注册表内容）只比类型/形状；
                -- 钩子用<b>计数</b>而不是逐条打印——计数本身就是对行号表与调用序列的强校验。
                local function pr(...)
                  print(...)
                end
                local function s(v)
                  local tt = type(v)
                  if tt == "table" or tt == "function" or tt == "thread" or tt == "userdata" then
                    return tt
                  end
                  return tostring(v)
                end
                local function pc(f, ...)
                  local r = { pcall(f, ...) }
                  local t = {}
                  -- 过一遍 s()：getlocal 之类会返回函数/表，直接 tostring 会带地址
                  for i = 2, #r do t[#t + 1] = s(r[i]) end
                  return r[1], table.concat(t, "\\t")
                end
                -- getinfo 结果里只挑确定的字段
                local function gi(...)
                  local ok, info = pcall(debug.getinfo, ...)
                  if not ok then return "err:" .. tostring(info) end
                  if type(info) ~= "table" then return tostring(info) end
                  local out = {}
                  for _, k in ipairs({ "what", "currentline", "nups", "nparams", "isvararg",
                                       "name", "namewhat", "func", "linedefined",
                                       "lastlinedefined" }) do
                    local v = info[k]
                    if k == "func" then v = s(v) end
                    out[#out + 1] = k .. "=" .. tostring(v)
                  end
                  out[#out + 1] = "src=" .. (tostring(info.short_src):gsub("^.*[/\\\\]", ""))
                  return table.concat(out, ",")
                end

                -- 1) 主线程上的 getinfo
                local function outer()
                  local a = 1
                  local function inner()
                    return gi(1, "nSlufL"), gi(2, "nSlufL")
                  end
                  return inner()
                end
                pr("A1", outer())
                pr("A2", gi(0, "nSlufL"))        -- 0 层是 getinfo 自己
                pr("A3", pc(debug.getinfo, 100)) -- 层号越界
                pr("A4", pc(debug.getinfo, -1))
                pr("A5", gi(print, "n"))         -- C 函数
                pr("A6", gi(print, "S"))
                pr("A7", gi(print, "nSlufL"))
                pr("A8", pc(debug.getinfo, 5))
                pr("A9", pc(debug.getinfo, "x"))
                pr("A10", pc(debug.getinfo))
                pr("A11", gi(outer, "f"))
                pr("A12", type(debug.getinfo(1, "L").activelines))

                -- 2) 局部变量与 upvalue
                do
                  local up = 7
                  local function f(p, q, ...)
                    local x, y = 1, 2
                    return up, x, y
                  end
                  pr("B1", debug.getlocal(f, 1)) -- 函数（未运行）没有帧
                  pr("B2", pc(debug.getlocal, 2, 1))
                  local function g(p, q, ...)
                    local x, y = 1, 2
                    local names = {}
                    local i = 1
                    while true do
                      local n, v = debug.getlocal(1, i)
                      if not n then break end
                      names[#names + 1] = n .. ":" .. s(v)
                      i = i + 1
                    end
                    return table.concat(names, ",")
                  end
                  pr("B3", g(10, 20, 30, 40))
                  local function h()
                    local z = 1
                    debug.setlocal(1, 1, 99)
                    return z
                  end
                  pr("B4", h())
                  pr("B5", pc(debug.setlocal, 1, 100, 5))
                  local function k()
                    return up
                  end
                  pr("B6", debug.getupvalue(k, 1))
                  pr("B7", debug.getupvalue(k, 2))
                  pr("B8", debug.getupvalue(print, 1)) -- C 函数没有 upvalue
                  pr("B9", debug.setupvalue(k, 1, 42), up)
                  pr("B10", debug.setupvalue(k, 1, 7), up)
                end

                -- 3) 协程上的 debug
                do
                  local co = coroutine.create(function(a, b)
                    local x = a + b
                    coroutine.yield(x)
                    local y = x * 2
                    return y
                  end)
                  pr("C1", coroutine.resume(co, 1, 2))
                  pr("C2", gi(co, 0, "nSlufL"))
                  pr("C3", gi(co, 1, "nSlufL"))
                  pr("C4", pc(debug.getinfo, co, 5))
                  pr("C5", debug.getlocal(co, 1, 1))
                  pr("C6", debug.getlocal(co, 1, 2))
                  pr("C7", debug.getlocal(co, 1, 3))
                  pr("C8", pc(debug.setlocal, co, 1, 1, 100))
                  pr("C9", coroutine.resume(co))
                  pr("C10", coroutine.status(co))
                  pr("C11", gi(co, 1, "nSlufL"))
                  pr("C12", pc(debug.getlocal, co, 1, 1)) -- 死了之后再问是抛错
                  pr("C13", pc(debug.sethook, co, print, "l"))
                end
                -- traceback 带协程：只比形状（含 chunk 名与行号）
                do
                  local co = coroutine.create(function()
                    coroutine.yield()
                    error("in-co")
                  end)
                  coroutine.resume(co)
                  pr("C14", coroutine.resume(co))
                  pr("C15", (debug.traceback(co)):gsub("%s+", " "))
                end
                do
                  local co = coroutine.create(function() error("boom-co") end)
                  local ok, e = coroutine.resume(co)
                  pr("C16", ok, e)
                  pr("C17", (debug.traceback(co)):gsub("%s+", " "))
                  pr("C18", (debug.traceback(co, "MSG")):sub(1, 3))
                end
                pr("C19", pc(debug.traceback, 5))
                pr("C20", pc(debug.traceback, "msg", 5))
                pr("C21", type(debug.traceback()))

                -- 4) 钩子：用计数而不是逐条打印
                do
                  local function runhook(mask)
                    local n = 0
                    debug.sethook(function() n = n + 1 end, mask)
                    local x = 0
                    for i = 1, 10 do x = x + i end
                    local function g() return 1 end
                    g()
                    debug.sethook()
                    return n
                  end
                  pr("D1", runhook("l"))
                  pr("D2", runhook("c"))
                  pr("D3", runhook("r"))
                  pr("D4", runhook("cr"))
                  pr("D5", runhook("lcr"))
                end
                do
                  local seq = {}
                  debug.sethook(function(what) seq[#seq + 1] = what end, "cr")
                  local function a() return 1 end
                  local function b() return a() end
                  b()
                  debug.sethook()
                  pr("D6", table.concat(seq, ","))
                end
                do
                  local n = 0
                  debug.sethook(function() n = n + 1 end, "", 10)
                  local x = 0
                  for i = 1, 1000 do x = x + i end
                  debug.sethook()
                  pr("D7", n > 0, n < 2000)
                end
                do
                  local n = 0
                  local co = coroutine.create(function()
                    local x = 0
                    for i = 1, 10 do x = x + i end
                    coroutine.yield(x)
                  end)
                  debug.sethook(co, function() n = n + 1 end, "l")
                  coroutine.resume(co)
                  debug.sethook(co)
                  pr("D8", n > 0)
                  local m = 0
                  debug.sethook(function() m = m + 1 end, "l")
                  local co2 = coroutine.create(function() local y = 0 for i = 1, 10 do y = y + i end end)
                  coroutine.resume(co2)
                  debug.sethook()
                  pr("D9", m)
                end
                pr("D10", pc(debug.sethook, print, "x"))
                pr("D11", pc(debug.sethook, 5, "l"))
                pr("D12", pc(debug.sethook))
                pr("D13", debug.gethook() == nil)
                do
                  debug.sethook(print, "l")
                  pr("D14", s(debug.gethook()))
                  debug.sethook()
                end

                -- 5) registry / uservalue / metatable
                pr("E1", s(debug.getregistry()))
                pr("E2", type(debug.getregistry()["_LOADED"]))
                pr("E3", pc(debug.setmetatable, 5, {}))
                do
                  local t = {}
                  pr("E4", debug.setmetatable(t, { __index = { a = 1 } }) == t, t.a)
                  pr("E5", s(debug.getmetatable(t)))
                  pr("E6", s(debug.getmetatable(5)))
                  pr("E7", pc(debug.setmetatable, t, 5))
                end
                do
                  local f = io.open("target/rt/_dbg_uv", "w")
                  f:write("x")
                  pr("E8", s(f), tostring(debug.getuservalue(f)))
                  pr("E9", debug.setuservalue(f, 42), tostring(debug.getuservalue(f)))
                  f:close()
                  os.remove("target/rt/_dbg_uv")
                  pr("E10", pc(debug.getuservalue, 5))
                  pr("E11", pc(debug.setuservalue, 5, 1))
                end

                -- 6) 协程语义的补充
                pr("F1", s(select(1, coroutine.running())), select(2, coroutine.running()))
                do
                  local main, ismain = coroutine.running()
                  pr("F2", s(main), tostring(ismain))
                end
                do
                  local co = coroutine.create(function(...) return ... end)
                  pr("F3", coroutine.resume(co, 1, 2, 3))
                  pr("F4", coroutine.resume(co))
                end
                do
                  local co = coroutine.create(function() coroutine.yield(1, 2, 3) end)
                  pr("F5", coroutine.resume(co))
                  pr("F6", coroutine.status(co))
                  pr("F7", coroutine.resume(co))
                  pr("F8", coroutine.status(co))
                end
                do
                  local w = coroutine.wrap(function() error("w-boom") end)
                  pr("F9", pc(w))
                end
                do
                  local co = coroutine.create(print)
                  pr("F10", coroutine.resume(co, "x"))
                end
                do
                  local co = coroutine.create(function()
                    local a, b = coroutine.resume(coroutine.create(function() return 7 end))
                    coroutine.yield(a, b)
                  end)
                  pr("F11", coroutine.resume(co))
                end
                do
                  local co = coroutine.create(function() coroutine.yield(1) end)
                  coroutine.resume(co)
                  pr("F12", coroutine.close(co))
                  pr("F13", coroutine.status(co))
                  pr("F14", coroutine.close(co))
                end
                do
                  local co = coroutine.create(function() coroutine.yield(1) error("x") end)
                  coroutine.resume(co)
                  pr("F15", pc(coroutine.close, co))
                  pr("F16", coroutine.status(co))
                end
                do
                  local co = coroutine.create(function() return 1 end)
                  pr("F17", coroutine.resume(co))
                  pr("F18", coroutine.resume(co))
                  pr("F19", pc(coroutine.resume, 5))
                  pr("F20", pc(coroutine.create, 5))
                end
                do
                  local co = coroutine.create(function() return coroutine.isyieldable() end)
                  pr("F21", coroutine.resume(co))
                end

                pr("DONE")

                print("DONE")
                """);
    }

    /**
     * 错误与错误对象矩阵：{@code error} 的 {@code level}、错误对象的类型与
     * 字符串化、位置前缀（{@code luaL_where}）在各种语法位置上的取值、
     * {@code xpcall} 处理器的形状、协程与 {@code <close>} 里的传播，
     * 外加剥掉调试信息的 chunk 与 userdata / 类型元表上的 {@code __name}。
     * 208 行逐字节一致。
     * <p>
     * 咬出两处，都在 {@code __name} 上：
     * <ul>
     *   <li><b>{@code __name} 走 C 字符串语义</b>（{@code ltm.c: luaT_objtypename}
     *       返回的是 {@code const char*}，{@code luaG_typeerror} 用 {@code %s} 拼）：
     *       {@code __name = "A\0B"} 报的只有 {@code A}；</li>
     *   <li><b>只有表与 userdata 认 {@code __name}</b>：C 的条件是
     *       {@code ttistable(o) || ttisfulluserdata(o)}，其余类型<b>连类型元表
     *       都不看</b>。实测 {@code debug.setmetatable(nil, {__name = "NilT"})}
     *       之后 {@code nil + 1} 参考实现报的还是 {@code nil value} —— 走
     *       {@link LuaTm#metatableOf} 会把类型元表也算进来，于是我们多报了
     *       {@code NilT}。</li>
     * </ul>
     * <p>
     * 片子里还钉住几条容易写反的：
     * <ul>
     *   <li><b>{@code debug.traceback} 把非字符串、非 nil 的消息原样返回</b>
     *       （{@code ldblib.c: db_traceback}），所以
     *       {@code xpcall(f, debug.traceback)} 碰上表错误对象返回的是那张表，
     *       不是回溯；而 {@code lua_tostring} <b>不</b>认 {@code __tostring}
     *       —— 这正是 {@code lua.c} 的 {@code msghandler} 要显式
     *       {@code luaL_callmeta(1, "__tostring")} 的原因；</li>
     *   <li><b>行钩子在这一行的第一条指令之前触发</b>，所以
     *       {@code print(pcall(f))} 与钩子写在同一行是<b>拦不住</b>的，
     *       调用钩子连 {@code print} / {@code pcall} 自己都算；</li>
     *   <li><b>{@code xpcall} 处理器的第二个返回值会被丢掉</b>：
     *       {@code ldo.c: dothecall} 用 {@code luaD_callnoyield(L, top - 2, 1)}，
     *       结果数写死是 1；</li>
     *   <li>{@code coroutine.wrap} 抛出的错<b>带两层位置前缀</b>；
     *       {@code error} 作为 C 函数直接被 {@code pcall} 时<b>没有</b>前缀，
     *       而库函数报错<b>有</b>（{@code luaL_error} 的 {@code luaL_where(L, 1)}
     *       取的是调用者那一层）；</li>
     *   <li>顶层未捕获的非字符串错误对象报 {@code (error object is a X value)}，
     *       这里的 {@code luaL_typename} <b>不</b>看 {@code __name}。</li>
     * </ul>
     * <p>
     * 回溯只打印头 6 个字节：整段打印的话归一化会从 {@code stack traceback:}
     * 处把后面<b>全部</b>砍掉，后面几十行一条也比不到。
     */
    @Test
    void errorsAndErrorObjectsMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 错误与错误对象矩阵：error 的 level、错误对象的类型与字符串化、
                -- 位置前缀（luaL_where）在各种语法位置上的取值、xpcall 的形状、
                -- 协程与 <close> 里的传播。
                local function show(v)
                  return (tostring(v):gsub("0x%x+", "0xADDR"))
                end
                local function w(tag, f, ...)
                  local r = table.pack(pcall(f, ...))
                  local parts = { tag, tostring(r[1]), "#" .. r.n }
                  for i = 2, r.n do parts[#parts + 1] = show(r[i]) end
                  print(table.concat(parts, " "))
                end

                -- 1) error(msg, level)
                local function mkl(lv)
                  return function() error("E", lv) end
                end
                for lv = -1, 6 do
                  w("L" .. lv, mkl(lv))
                end
                for _, lv in ipairs({ 0, 1, 2, 3, 4 }) do
                  w("C" .. lv, error, "E", lv)
                end
                print("LV1", pcall(error, "E", 1.0))
                print("LV2", pcall(error, "E", 1.5))
                print("LV3", pcall(error, "E", "2"))
                print("LV4", pcall(error, "E", "x"))
                print("LV5", pcall(error, "E", {}))
                print("LV6", pcall(function() local function g() error("A", 2) end g() end))
                print("LV7", pcall(function() local function g() error("A", 3) end g() end))
                print("LV8", pcall(function() local function g() error("A", 4) end g() end))
                print("LV9", pcall(function() local function g() return error("A", 2) end return g() end))

                -- 2) 错误对象的类型
                local OBJS = {
                  { "str", "msg" }, { "int", 7 }, { "flt", 4.5 }, { "true", true }, { "false", false },
                  { "tbl", {} }, { "fn", print }, { "thr", coroutine.create(function() end) },
                }
                for _, o in ipairs(OBJS) do
                  local r = table.pack(pcall(function() error(o[2]) end))
                  print("O", o[1], r[1], "#" .. r.n, type(r[2]), show(r[2]))
                end
                do
                  local r = table.pack(pcall(function() error(nil) end))
                  print("O", "nil", r[1], "#" .. r.n, type(r[2]), show(r[2]))
                end
                do
                  local r = table.pack(pcall(error))
                  print("O", "err0", r[1], "#" .. r.n, type(r[2]), show(r[2]))
                end

                -- 3) 错误对象的字符串化：print 走 luaL_tolstring
                local e1 = setmetatable({}, { __tostring = function() return "TSTR" end })
                local e2 = setmetatable({}, { __name = "MyType" })
                local e3 = setmetatable({}, { __tostring = function() return 5 end })
                local e4 = setmetatable({}, { __tostring = function() error("nested") end })
                local e5 = setmetatable({}, { __tostring = function() return nil end })
                local ES = { e1, e2, e3, {}, 5, "s", true, print }
                for i = 1, #ES do
                  print("S", i, show(ES[i]))
                end
                print("Snil", show(nil))
                print("S2", string.format("%s", e1))
                print("S3", pcall(string.format, "%s", e3))
                print("S4", pcall(tostring, e4))
                print("S5", pcall(print, e4))
                -- __tostring 返回非字符串：luaL_tolstring 报的是「必须返回字符串」
                print("S6", pcall(tostring, e5))
                print("S7", pcall(function() return show(e5) end))
                print("S8", pcall(function() return ("%s"):format(e5) end))

                -- 4) luaL_where：位置前缀在各种语法位置上
                w("W1", function() error("A") end)
                w("W2", function()
                  error("B")
                end)
                w("W3", function() local t = { 1, error("C"), 3 } end)
                w("W4", function() local t = { [error("D")] = 1 } end)
                w("W5", function() local function g() error("E") end for i = 1, 2 do g() end end)
                w("W6", function() for i = 1, 2 do error("F") end end)
                local mt = {}
                function mt.__add(a, b) error("G") end
                function mt.__index(t, k) error("H") end
                function mt.__newindex(t, k, v) error("I") end
                function mt.__tostring(t) error("J") end
                function mt.__len(t) error("K") end
                function mt.__call(t, ...) error("L") end
                function mt.__concat(a, b) error("M") end
                function mt.__eq(a, b) error("N") end
                function mt.__lt(a, b) error("O") end
                function mt.__le(a, b) error("P") end
                function mt.__unm(t) error("Q") end
                local obj = setmetatable({}, mt)
                local obj2 = setmetatable({}, mt)
                w("M1", function() return obj + 1 end)
                w("M2", function() return obj.k end)
                w("M3", function() obj.k = 1 end)
                w("M4", function() return tostring(obj) end)
                w("M5", function() return #obj end)
                w("M6", function() return obj() end)
                w("M7", function() return obj .. "x" end)
                w("M8", function() return obj == obj2 end)
                w("M9", function() return obj < obj2 end)
                w("MA", function() return obj <= obj2 end)
                w("MB", function() return -obj end)
                w("G1", function() return ("x"):gsub("x", function() error("R") end) end)
                w("G2", function() table.sort({ 3, 1, 2 }, function(a, b) error("S") end) end)
                w("G3", function() local co = coroutine.create(function() error("T") end) return select(2, coroutine.resume(co)) end)
                w("G4", function() local co = coroutine.wrap(function() error("U") end) return co() end)
                w("G5", function() local function g(...) error("V") end return g(1, 2, 3) end)
                w("G6", function() local t = {} t.a = function() error("X") end t:a() end)
                w("G7", function() local t = setmetatable({}, { __index = { f = function() error("Y") end } }) return t:f() end)
                w("G8", function() return (function() error("Z") end)() end)
                w("G9", function() local f = function() error("AA") end return f() end)
                w("GA", function() local function g() error("AB") end return g() end)
                w("L1", function() return load("error('AC')", "=chunk")() end)
                w("L2", function() return load("error('AD')", "@file.lua")() end)
                w("L3", function() return load("error('AE')")() end)
                w("L4", function() return load("x=", "=bad") end)
                w("L5", function() return load("x=") end)
                w("CF", error, "AF")
                w("C1", function() return string.rep("a") end)
                w("C2", function() return #5 end)
                w("C3", function() return nil + 1 end)
                print("SORT", pcall(table.sort, { 3, 1, 2 }, function(a, b) return true end))
                print("EH", pcall(function() return xpcall(function() error("E1") end, function(e) error("H1") end) end))

                -- 5) xpcall 的形状
                print("X1", xpcall(function() return 1, 2, 3 end, print))
                print("X2", xpcall(function() error("E") end, function(e) return "H:" .. tostring(e) end))
                print("X3", xpcall(function() error("E") end, function(e) return "a", "b" end))
                print("X4", xpcall(function(a, b) return a + b end, function(e) return "H" end, 3, 4))
                print("X5", pcall(xpcall, function() end, 5))
                print("X6", pcall(xpcall))
                print("X7", pcall(xpcall, function() end))
                -- 只打印头 6 个字节：整段打印的话 Diff 的归一化会从 "stack traceback:" 处
                -- 把后面全部砍掉，后面的行一条也比不到（guard 会挡，但那就得改 assertFullMatches）
                local ok8, r8 = xpcall(function() error("E8") end, debug.traceback)
                print("X8", ok8, type(r8), r8:sub(1, 6))
                -- debug.traceback 当处理器：非字符串、非 nil 的错误对象原样返回（ldblib.c: db_traceback）
                local ok9, r9 = xpcall(function() error({}) end, debug.traceback)
                print("X9", ok9, type(r9), show(r9))
                local okA, rA = xpcall(function() error(setmetatable({}, { __tostring = function() return "TS" end })) end, debug.traceback)
                print("XA", okA, type(rA), tostring(rA):match("^[^\\n]*"))
                local okB, rB = xpcall(function() error(nil) end, debug.traceback)
                print("XB", okB, type(rB), rB:sub(1, 6))
                print("XC", type(debug.traceback({})), type(debug.traceback(nil)), type(debug.traceback("m")))
                print("XD", (debug.traceback("m")):sub(1, 6), (debug.traceback("m", 2)):sub(1, 6))

                -- 6) 协程里的错误传播
                local co = coroutine.create(function() error("CE") end)
                print("CR1", coroutine.resume(co))
                print("CR2", coroutine.status(co))
                local co2 = coroutine.create(function() error({}) end)
                local okc, ec = coroutine.resume(co2)
                print("CR3", okc, type(ec), show(ec))
                local co3 = coroutine.create(function() coroutine.yield(1) error("CE3") end)
                print("CR4", coroutine.resume(co3))
                print("CR5", coroutine.resume(co3))
                local co4 = coroutine.wrap(function() error("CE4") end)
                print("CR6", pcall(co4))
                print("CR7", pcall(co4))

                -- 7) <close> 与关闭时抛错
                print("CL1", pcall(function()
                  local x <close> = setmetatable({}, { __close = function(_, err) print("closed", type(err), tostring(err)) end })
                  error("IN")
                end))
                print("CL2", pcall(function()
                  local x <close> = setmetatable({}, { __close = function() error("CLOSE") end })
                  return 1
                end))
                print("CL3", pcall(function()
                  local x <close> = setmetatable({}, { __close = function(_, err) print("e", type(err)) end })
                  error({})
                end))

                -- 8) assert 的形状
                print("A1", pcall(assert, false))
                print("A2", pcall(assert, nil))
                print("A3", pcall(assert, false, "msg"))
                print("A4", pcall(assert, nil, 5))
                print("A5", assert(1, 2, 3))
                print("A6", pcall(assert))
                print("A7", pcall(assert, false, {}))

                -- 9) __name 进的是「类型错误」的消息，不是 luaL_typename
                local nm = setmetatable({}, { __name = "MyType" })
                local nm2 = setmetatable({}, { __name = "MyType" })
                print("N1", pcall(function() return nm + 1 end))
                print("N2", pcall(function() return nm < 1 end))
                print("N3", pcall(function() return #nm end))
                print("N4", pcall(function() return nm .. "x" end))
                print("N5", pcall(function() return nm() end))
                print("N6", pcall(function() return nm.k + 1 end))
                print("N7", pcall(function() return 1 + nm end))
                print("N8", pcall(function() return -nm end))
                print("N9", pcall(function() return nm | 1 end))
                print("NA", pcall(function() return nm == nm2 end))
                print("NB", pcall(string.rep, nm))
                print("NC", pcall(string.len, nm))
                print("ND", pcall(math.abs, nm))
                print("NE", pcall(table.concat, nm))
                print("NF", pcall(table.insert, nm, 1))
                print("NG", pcall(setmetatable, 5, nm))
                do
                  local n2 = setmetatable({}, { __name = 5 })
                  print("NH", pcall(function() return n2 + 1 end))
                end
                do
                  local n3 = setmetatable({}, { __name = "A\\0B" })
                  print("NI", pcall(function() return n3 + 1 end))
                  print("NJ", show(n3))
                end
                print("NK", show(nm))

                -- 10) 边界：pcall / xpcall 的实参数型、for-in 的迭代器、goto、协程里的 level、
                --    消息里的裸字节
                print("P1", pcall(5))
                print("P2", pcall(nil))
                print("P3", pcall(pcall))
                print("P4", xpcall(5, print))
                print("P5", xpcall(nil, print))
                print("P6", pcall(function() for k, v in 5 do end end))
                print("P7", pcall(function() for k, v in pairs(nil) do end end))
                print("P8", pcall(function() local t = setmetatable({}, { __pairs = function() error("PAIRS") end }) for k, v in pairs(t) do end end))
                print("P9", pcall(function() local x = 0 ::top:: x = x + 1 if x < 2 then goto top end error("GOTO") end))
                print("PA", pcall(function() local co = coroutine.create(function() error("CL", 2) end) return select(2, coroutine.resume(co)) end))
                print("PB", pcall(function() local co = coroutine.wrap(function() error("CW", 0) end) return co() end))
                print("PC", pcall(function() local co = coroutine.wrap(function() error("CW2", 1) end) return co() end))
                print("PD", pcall(select, "x"))
                print("PE", pcall(select, 0))
                print("PF", pcall(select, 1))
                print("PG", pcall(function() return select(-1, 1, 2) end))
                print("PH", pcall(os.date, "%Q", 0))
                print("PI", pcall(os.date, 5))
                print("PJ", pcall(string.format, "%d", nil))
                print("PK", pcall(function() error("E", 100) end))
                print("PL", pcall(function() error("E", math.maxinteger) end))
                print("PM", pcall(function() error("E", -1) end))
                local rec = {}
                setmetatable(rec, { __tostring = function() return rec end })
                print("PN", pcall(tostring, rec))
                do
                  local ok, e = pcall(function() error("a\\0b") end)
                  print("Q1", ok, #e, e:byte(1, -1))
                end
                do
                  local ok, e = pcall(function() error("\\255\\254") end)
                  print("Q2", ok, #e, e:byte(1, -1))
                end
                do
                  local ok, e = pcall(function() error(255) end)
                  print("Q3", ok, math.type(e))
                end

                -- 11) userdata 与类型元表上的 __name、错误对象的同一性、traceback 的其他形状
                print("U1", pcall(function() return #io.stdout end))
                print("U2", pcall(function() return io.stdout + 1 end))
                print("U3", show(io.stdout))
                print("U4", pcall(string.rep, io.stdout))
                print("U5", pcall(function() return io.stdout .. "x" end))
                debug.setmetatable(nil, { __name = "NilT" })
                print("U6", pcall(function() return nil + 1 end))
                print("U7", pcall(function() return #nil end))
                debug.setmetatable(nil, nil)
                print("U8", pcall(function() return nil + 1 end))
                print("U9", pcall(xpcall, function() error("Z") end, setmetatable({}, { __call = function() return "CALLED" end })))
                local idt = {}
                local okid, eid = pcall(function() error(idt) end)
                print("ID1", okid, eid == idt)
                local s0 = "msg"
                local oks, es = pcall(function() error(s0) end)
                print("ID2", oks, es == s0, es)
                local oks0, es0 = pcall(function() error(s0, 0) end)
                print("ID3", oks0, es0 == s0, es0)
                local cq = coroutine.create(function() local x <close> = setmetatable({}, { __close = function() end }) coroutine.yield() end)
                coroutine.resume(cq)
                print("CC1", coroutine.close(cq))
                local cq2 = coroutine.create(function() error("CE9") end)
                coroutine.resume(cq2)
                print("CC2", coroutine.close(cq2))
                print("CC3", pcall(coroutine.close, coroutine.running()))
                local tbco = coroutine.create(function() coroutine.yield() end)
                coroutine.resume(tbco)
                print("TB1", type(debug.traceback(tbco)))
                print("TB2", debug.traceback(tbco, "M"):sub(1, 6))
                print("TB3", debug.traceback(tbco, "M", 0):sub(1, 6))
                print("TB4", type(debug.traceback(tbco, nil)))

                -- 12) 剥掉调试信息的 chunk：位置前缀没了
                local f0 = load("local x = 1 error('E0') return x")
                print("D1", pcall(f0))
                print("D2", pcall(load(string.dump(f0, true))))
                print("D3", pcall(load(string.dump(f0, false))))
                print("D4", pcall(load(string.dump(f0, true), "=n")))
                print("D5", pcall(load(string.dump(f0), "=n")))
                print("D6", pcall(load(string.dump(f0), "@p.lua")))
                local gs = load(string.dump(f0, true))
                local inf = debug.getinfo(gs, "Sl")
                print("D7", inf.currentline, inf.linedefined, inf.lastlinedefined, inf.short_src, inf.source)
                local gf = load(string.dump(f0, false))
                local inf2 = debug.getinfo(gf, "Sl")
                print("D8", inf2.currentline, inf2.linedefined, inf2.lastlinedefined, inf2.short_src == inf2.source)
                print("D9", pcall(function() return load(string.dump(f0, true), "=n", "t") end))
                print("DA", pcall(function() return load(string.dump(f0), "=n", "t") end))
                print("DB", pcall(function() return load(string.dump(f0), "=n", "b") end))

                -- 13) 钩子里抛错
                -- 行钩子在「这一行的第一条指令之前」触发，所以 pcall(...) 写在同一行是拦不住的：
                -- 钩子先跑、pcall 还没进去。要拦住，得让触发点落在 pcall 里面。
                local n = 0
                local ok, e = pcall(function()
                  debug.sethook(function() n = n + 1 if n == 1 then error("HOOK") end end, "l")
                  local x = 1
                  local y = x + 1
                  return y
                end)
                print("HK", ok, e)
                -- 钩子还在，所以清钩子这一步也得放在 pcall 里
                print("HK2", pcall(function() debug.sethook() end), n > 0)
                print("HK3", pcall(function() local z = 1 return z end))
                -- 调用钩子会在「每一次调用」时触发，连 print / pcall 自己也算 —— 所以装钩子的
                -- 那一步必须在 pcall 里面，否则第一个被钩住的就是外面那层的 print。
                local m = 0
                local ok4, e4 = pcall(function()
                  debug.sethook(function() m = m + 1 if m == 1 then error("H2") end end, "c")
                  return (function() return 1 end)()
                end)
                print("HK4", ok4, e4)
                print("HK5", pcall(function() debug.sethook() end), m > 0)
                print("HK6", pcall(function() local q = 1 return q end))

                -- 14) 顶层未捕获：错误对象不是字符串（lua.c: msghandler 的兜底）
                error(setmetatable({}, { __name = "MyType" }))
                """);
    }

    /**
     * 内嵌 {@code \0} 的字符串矩阵：C 版凡是当 {@code const char *} 用的地方，
     * 都会在第一个 {@code \0} 处被砍断（{@code strlen} / {@code %s} / {@code strstr} /
     * {@code strchr}），而 Lua 字符串本身是字节串、<b>不以 {@code \0} 结尾</b>。
     * 88 行逐字节一致。
     * <p>
     * 被砍断的一共有这些地方 —— 逐个钉住，少一个就是一处分歧：
     * <ul>
     *   <li><b>chunk 名</b>：{@code luaS_new} 用 {@code strlen}，所以
     *       {@code load("error('E')", "a\0b")} 报的是 {@code [string "a"]:1: E}；</li>
     *   <li><b>模块名与路径</b>：{@code luaL_gsub} 的替换串、
     *       {@code package.loaded} 的键、{@code searchpath} 的四个参数；</li>
     *   <li><b>文件名与 {@code fopen} 的 mode</b>：{@code io.open} / {@code io.lines} /
     *       {@code os.remove} / {@code os.rename} / {@code os.getenv}；</li>
     *   <li><b>{@code debug.getinfo} 的 what</b>：C 扫到 {@code \0} 就收工，
     *       后面那几个字符<b>连看都不看</b>，不会变成 {@code invalid option}；</li>
     *   <li><b>{@code debug.sethook} 的 mask</b>（{@code strchr}）：{@code "l\0c"}
     *       只装行钩子，{@code "c\0l"} 只装调用钩子 —— 两者计数完全相反；</li>
     *   <li><b>{@code luaL_traceback} 的消息</b>、<b>{@code luaL_checkoption}</b>
     *       （{@code collectgarbage}）、<b>{@code string.pack} 的格式串</b>。</li>
     * </ul>
     * <p>
     * 反过来，<b>不被砍断</b>的也得钉住：{@code string.format("%s")} 走的是
     * {@code luaL_tolstring}（带长度），{@code error} 的消息走 {@code lua_concat}，
     * {@code os.date} 用 {@code s + slen} 扫完整个格式串（实测
     * {@code os.date("%Y\0%m", 0)} 给 {@code "1970"} + {@code \0} + {@code "01"}），
     * 表键与 {@code gsub} / {@code upper} 也都是按字节的。
     * <p>
     * 还有一处<b>拒绝</b>而不是截断：带修饰符的 {@code %s} 与
     * {@code string.pack} 的 {@code z} 会报
     * {@code string contains zeros}（{@code luaL_argcheck(l == strlen(s))}）。
     * <p>
     * 最凶的一处是 {@code os.remove}：不截断的话 Java 的 {@code Paths.get} 抛
     * {@code InvalidPathException}，而它<b>不是 {@code LuaError}</b>，
     * {@code pcall} 拦不住，整颗逃逸成 Java 异常、进程当场崩掉。
     * <p>
     * 不比一处：{@code collectgarbage("count")} 的内存数值（没有收集器，
     * 无从统计，见 {@code weakTableAndFinalizerMatrixMatchesReference}）。
     */
    @Test
    void embeddedNulMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 内嵌 \\0 的字符串矩阵：C 版凡是当 const char* 用的地方，都会在第一个 \\0 处
                -- 被砍断（strlen / %s / strstr），而 Lua 字符串本身是字节串、不以 \\0 结尾。
                -- 这一片就是把这些地方逐个钉出来。
                local function show(v)
                  return (tostring(v):gsub("0x%x+", "0xADDR"))
                end
                local function bytes(s)
                  local t = {}
                  for i = 1, #s do t[#t + 1] = s:byte(i) end
                  return table.concat(t, ",")
                end

                -- 1) chunk 名字：luaL_loadbufferx 的 chunkname 是 const char*
                print("C1", pcall(function() return load("return 1", "a\\0b")() end))
                local f1 = load("return 1", "a\\0b")
                local i1 = debug.getinfo(f1, "S")
                print("C2", bytes(i1.source), bytes(i1.short_src))
                print("C3", pcall(load("error('E')", "a\\0b")))
                print("C4", pcall(load("error('E')", "=a\\0b")))
                print("C5", pcall(load("error('E')", "@a\\0b")))
                print("C6", select(2, load("x=", "a\\0b")))
                print("C7", select(2, load("x=", "=a\\0b")))
                print("C8", select(2, load("x=", "@a\\0b")))
                print("C9", select(2, load("x=", "@a\\0b\\0c")))

                -- 2) 模块名 / searchpath：luaL_gsub 的替换串是 const char*
                print("M1", pcall(require, "a\\0b"))
                print("M2", package.searchpath("a\\0b", "./?.lua"))
                print("M3", package.searchpath("a\\0b", "?"))
                print("M4", pcall(package.searchpath, "a\\0b", "./?.lua;./?.x"))
                print("M5", pcall(package.searchpath, "m", "?\\0z"))
                print("M6", pcall(package.searchpath, "m", "./?.lua", ".", "\\0"))
                print("M7", pcall(package.searchpath, "m", "./?.lua", "\\0", "-"))
                print("M8", package.searchpath("a\\0b", "./?.lua", ".", "-"))

                -- 3) 文件名：io.open / os.remove / os.rename 的错误消息用 %s 拼文件名
                print("F1", pcall(io.open, "/nonexistent-dir-xyz/no\\0pe"))
                print("F2", pcall(io.open, "\\0/nonexistent-dir-xyz/nope"))
                print("F3", pcall(os.remove, "/nonexistent-dir-xyz/no\\0pe"))
                print("F4", pcall(os.rename, "/nonexistent-dir-xyz/no\\0pe", "/nonexistent-dir-xyz/x"))
                -- fopen 的 mode 也是 const char*
                print("F5", pcall(io.open, "/nonexistent-dir-xyz/x", "r\\0w"))
                print("F6", pcall(io.open, "/nonexistent-dir-xyz/x", "\\0r"))
                print("F7", pcall(io.open, "/nonexistent-dir-xyz/x", "r"))
                -- getenv 的名字同样是 const char*
                print("F8", type(os.getenv("HOME\\0X")), type(os.getenv("HOME")), type(os.getenv("NOPE\\0X")))

                -- 4) string.format：%s 是整串（走 luaL_tolstring 带长度），%q 也按字节
                print("S1", bytes(("%s"):format("a\\0b")))
                print("S2", pcall(string.format, "%.3s", "a\\0bc"))
                print("S2b", #("%.3s"):format("ab"), pcall(function() return #("%.3s"):format("a\\0bc") end))
                print("S3", bytes(("%q"):format("a\\0b")))
                print("S4", #("%s"):format("a\\0b"))
                print("S5", bytes(("%s%s"):format("a\\0", "b")))
                print("S6", bytes(string.rep("a\\0b", 2)))
                print("S7", bytes(("x"):rep(2, "\\0")))

                -- 5) debug.getinfo 的 what：多出来的部分被 \\0 砍掉
                local function gi()
                  return 1
                end
                local a1 = debug.getinfo(gi, "S\\0ln")
                print("G1", a1.source ~= nil, a1.short_src ~= nil, a1.currentline, a1.linedefined,
                      a1.name, a1.namewhat, a1.what)
                local a2 = debug.getinfo(gi, "Sln")
                print("G2", a2.source ~= nil, a2.short_src ~= nil, a2.currentline, a2.linedefined,
                      a2.name, a2.namewhat, a2.what)
                print("G3", pcall(debug.getinfo, gi, "\\0"))
                print("G4", pcall(debug.getinfo, gi, 5))
                print("G5", pcall(debug.getinfo, gi, nil))

                -- 6) traceback 的消息：luaL_traceback 用 strlen 取长度
                print("T1", bytes(debug.traceback("a\\0b"):sub(1, 5)))
                print("T2", bytes(debug.traceback("ab"):sub(1, 5)))
                local okt, rt = xpcall(function() error("a\\0b") end, debug.traceback)
                print("T3", okt, bytes(rt:sub(1, 5)))

                -- 7) __name 走 C 字符串（luaT_objtypename）
                local n3 = setmetatable({}, { __name = "A\\0B" })
                print("N1", pcall(function() return n3 + 1 end))
                print("N2", show(n3))
                print("N3", pcall(string.rep, n3))
                print("N4", pcall(function() return #n3 end))

                -- 8) error 的消息本身按字节拼（lua_concat），不砍
                local oke, ee = pcall(function() error("a\\0b") end)
                print("E1", oke, #ee, bytes(ee:sub(-4)))
                local oke2, ee2 = pcall(function() error("a\\0b", 0) end)
                print("E2", oke2, #ee2, bytes(ee2))

                -- 9) os.setlocale 的 locale 名
                print("L1", pcall(os.setlocale, "a\\0b"))
                print("L2", os.setlocale("C"))
                print("L3", pcall(os.setlocale, "C\\0X"))
                print("L4", os.setlocale())

                -- 10) 表键 / 字符串库都按字节，不砍
                local kt = {}
                kt["a\\0b"] = 1
                print("K1", #("a\\0b"), kt["a\\0b"], next(kt) == "a\\0b")
                print("K2", bytes(table.concat({ "a\\0b", "c" }, "-")))
                print("K3", bytes(tostring(("a\\0b"):upper())))
                print("K4", bytes(("a\\0b"):gsub("\\0", "Z")))

                -- 11) string.pack 的 z（零结尾串）与 format 带修饰符的 %s 都拒绝含 \\0 的串
                print("P1", pcall(string.pack, "z", "a\\0b"))
                print("P2", pcall(string.pack, "z", "ab"))
                print("P3", bytes(string.pack("z", "ab")))
                print("P4", pcall(string.packsize, "z", "a\\0b"))
                print("P5", pcall(string.unpack, "z", "ab\\0c"))
                print("P6", pcall(string.format, "%.3s", "a\\0bc"))
                print("P7", pcall(string.format, "%10s", "a\\0bc"))
                print("P8", pcall(string.format, "%-5s|", "a\\0bc"))
                print("P9", pcall(string.format, "%s", "a\\0bc"))

                -- 12) 其余拿字符串当 const char* 的入口
                print("X1", pcall(collectgarbage, "collect\\0junk"))
                print("X3", pcall(collectgarbage, "stop\\0"))
                print("X4", pcall(collectgarbage, "\\0collect"))
                print("X5", pcall(io.lines, "/nonexistent-dir-xyz/no\\0pe"))
                print("X6", pcall(string.pack, "i\\0j", 1))
                print("X7", bytes(string.pack("i\\0j", 1)))
                print("X8", pcall(string.packsize, "i\\0j"))
                print("X9", pcall(os.date, "%Y\\0%m", 0))
                print("XA", os.date("%Y", 0))
                -- sethook 的 mask 也走 strchr，\\0 之后的部分根本看不见
                local cnt = 0
                local okh = pcall(function()
                  debug.sethook(function() cnt = cnt + 1 end, "l\\0c")
                  local a = 1
                  local b = a + 1
                  return b
                end)
                print("XB", okh, cnt > 0)
                local cnt2 = 0
                local okh2 = pcall(function()
                  debug.sethook(function() cnt2 = cnt2 + 1 end, "c\\0l")
                  local a = 1
                  local b = a + 1
                  return b
                end)
                print("XC", okh2, cnt2 > 0)
                print("XD", pcall(function() debug.sethook() end))
                """);
    }

    /**
     * 内嵌 {@code \0} 第二轮：{@code load} / {@code loadfile} /
     * {@code io.output} / {@code io.input} 这几个门口。40 行逐字节一致。
     * <p>
     * <b>{@code load} 的 mode</b>：{@code checkmode} 比的是 {@code const char*}，
     * 所以 {@code load(src, name, "t\0b")} 的模式是 {@code "t"} ——
     * {@code "b\0t"} 会去 undump 一个文本 chunk 然后报
     * {@code attempt to load a text chunk (mode is 'b')}。
     * <p>
     * <b>{@code loadfile} 的文件名</b>同样在 {@code \0} 处砍断，
     * 砍出来的名字才进 {@code cannot open ...} 那条消息。
     * <p>
     * 还咬出 <b>{@code loadfile("")}</b>：C 的 {@code fopen("")} 给
     * {@code ENOENT}，报 {@code cannot open : No such file or directory}；
     * Java 把空路径解析成当前目录、读的时候给 {@code Is a directory}，
     * 于是我们报成了 {@code Input/output error}。空路径是 C 与 Java 唯一
     * 判不一致的一处，单独按 C 的判据取 {@code ENOENT} ——
     * <b>目录本身不用改</b>：{@code loadfile("/tmp")} 在 C 里 {@code fopen}
     * 是<b>成功</b>的、失败在读，走的是
     * {@code cannot read /tmp: Is a directory} 那条分支。
     */
    @Test
    void embeddedNulLoadMatrixMatchesReference() {
        Diff.assertMatches("""
                -- 内嵌 \\0 第二轮：load / loadfile / io.output / io.input 这几个门口
                local function bytes(s)
                  local t = {}
                  for i = 1, #s do t[#t + 1] = s:byte(i) end
                  return table.concat(t, ",")
                end

                -- 1) load 的 mode：checkmode 比的是 const char*
                print("A1", pcall(load, "return 1", "=n", "t\\0b"))
                print("A2", pcall(load, "return 1", "=n", "b\\0t"))
                print("A3", pcall(load, "return 1", "=n", "\\0"))
                print("A4", pcall(load, "return 1", "=n", "x\\0t"))
                print("A5", pcall(load, "return 1", "=n", "t"))
                print("A6", pcall(load, "return 1", "=n", "bt"))
                print("A7", pcall(load, "return 1", "=n", "bt\\0z"))
                -- 二进制 chunk 走 undump：模式也是先被 \\0 砍过
                print("A8", pcall(load, string.dump(function() return 1 end), "=n", "b\\0t"))
                print("A9", pcall(load, string.dump(function() return 1 end), "=n", "t\\0b"))
                print("AA", pcall(load, string.dump(function() return 1 end), "=n", "bt"))

                -- 2) loadfile 的文件名与 mode
                print("B1", loadfile("/nonexistent-dir-xyz/x\\0y"))
                print("B2", select(2, loadfile("/nonexistent-dir-xyz/x\\0y")))
                print("B3", select(2, loadfile("/nonexistent-dir-xyz/x")))
                print("B4", select(2, loadfile("\\0")))
                print("B5", select(2, loadfile("/nonexistent-dir-xyz/x", "t\\0b")))
                print("B6", select(2, loadfile("/nonexistent-dir-xyz/x", "x\\0t")))

                -- 3) io.output / io.input 给的是文件名
                print("C1", pcall(io.output, "/nonexistent-dir-xyz/o\\0ut"))
                print("C2", pcall(io.input, "/nonexistent-dir-xyz/in\\0put"))
                io.output(io.stdout)
                io.input(io.stdin)
                print("C3", pcall(io.output, "/nonexistent-dir-xyz/out"))
                print("C4", pcall(io.input, "/nonexistent-dir-xyz/input"))

                -- 4) package.loaded 里预先放一个带 \\0 的键：require 查的是被砍过的名字
                package.loaded["a"] = "PRE"
                print("D1", pcall(require, "a"))
                print("D2", pcall(require, "a\\0b"))
                package.loaded["a"] = nil
                print("D3", pcall(require, "a\\0b"))

                -- 5) require 一个能被找到的模块：文件名里带 \\0 也照样砍
                print("E1", pcall(package.searchpath, "m", "./?.lua\\0x"))
                print("E2", pcall(package.searchpath, "m\\0n", "./?.lua"))

                -- 6) os.date / os.time 的格式与月份名不含 NUL，但 os.date 的 s 是按长度扫的
                print("F1", bytes(os.date("%Y\\0%m\\0%d", 0)))
                print("F2", bytes(os.date("!\\0%Y", 0)))
                print("F3", bytes(os.date("\\0", 0)))
                print("F4", bytes(os.date("%%\\0%%", 0)))
                """);
    }

    /**
     * {@code load} 的<b>读取器</b>矩阵（{@code lua_load} 的 reader 契约）。
     * 54 行逐字节一致。
     * <p>
     * 钉住这几条：
     * <ul>
     *   <li><b>空串不等于结束</b>：{@code Zio} 拿到空串会继续问下一个，
     *       只有 {@code nil} 才是收尾。{@code {"", "", src}} 编出来的是
     *       {@code src}，而 {@code {"", ""}} 编出来的是<b>空 chunk</b>；</li>
     *   <li><b>读取器给的不是字符串也不是数字就当结束</b>（{@code true} / 表 /
     *       函数），给数字会被当字符串接上（所以「永远返回同一个数字」的
     *       读取器会<b>挂死</b>，本片子里不测）；</li>
     *   <li><b>读取器自己抛错</b>要原样传出去，包括已经喂进去一半的情况；</li>
     *   <li><b>读取器里不能 yield</b>：{@code lua_load} 走
     *       {@code luaD_callnoyield}，报 {@code attempt to yield across a
     *       C-call boundary}；</li>
     *   <li><b>{@code load} 不像 {@code loadfile} 那样吃 BOM 与 {@code #!} 行</b>
     *       （那属于加载层，不属于词法层）；</li>
     *   <li>读取器返回的串<b>按长度拼</b>，中间的 {@code \0} 不会被砍。</li>
     * </ul>
     * <p>
     * 写这条片子时踩了一次自己的坑：读取器「永远返回同一个非空串」是死循环
     * —— {@code load(function() return "return 1" end)} 编出来的是
     * {@code "return 1return 1return 1..."}，报
     * {@code malformed number}。每个读取器都得自己收尾。
     */
    @Test
    void loadReaderMatrixMatchesReference() {
        Diff.assertMatches("""
                -- load 的读取器（lua_load 的 reader 契约）矩阵
                -- 注意：读取器「永远返回同一个非空串」是死循环（Zio 只在拿到 nil 或空串时才
                -- 继续问，拿到内容就喂给词法器），所以每个读取器都必须自己收尾。
                local function once(s)
                  local done = false
                  return function()
                    if done then return nil end
                    done = true
                    return s
                  end
                end
                local function chunks(list)
                  local i = 1
                  return function()
                    local v = list[i]
                    i = i + 1
                    return v
                  end
                end
                local function perbyte(s)
                  local i = 1
                  return function()
                    if i > #s then return nil end
                    local c = s:sub(i, i)
                    i = i + 1
                    return c
                  end
                end
                local SRC = "return 1 + 2"

                -- 1) 一次给完 / 一个字节一个字节给
                print("A1", load(once(SRC)))
                print("A2", (load(once(SRC)))())
                print("A3", (load(perbyte(SRC)))())
                print("A4", (load(perbyte(SRC), "=one"))())
                print("A5", (load(chunks({ "return ", "1 +", " 2" })))())
                print("A6", (load(chunks({ "return ", "", "1 +", "", " 2", "" })))())

                -- 2) 空串：Zio 碰到空串不结束，继续问下一个
                print("B1", (load(chunks({ "", SRC })))())
                print("B2", (load(chunks({ "", "", "", SRC })))())
                print("B3", (load(chunks({ "", "" })))())
                print("B4", select("#", (load(chunks({ "", "" })))()))
                print("B5", pcall(load(chunks({ "", "" }))))

                -- 3) 第一次就 nil：空 chunk
                print("C1", select("#", (load(function() return nil end))()))
                print("C2", pcall(load(function() return nil end)))

                -- 4) 读取器给的不是字符串也不是数字
                print("D1", pcall(load, function() return true end))
                print("D2", pcall(load, function() return {} end))
                print("D3", pcall(load, function() return print end))
                print("D4", pcall(load, chunks({ SRC, true })))
                print("D5", pcall(load, chunks({ true, SRC })))

                -- 5) 读取器自己抛错
                print("E1", pcall(load, function() error("READER") end))
                print("E2", pcall(load, chunks({ "return ", "x" })))
                local k = 0
                print("E3", pcall(load, function()
                  k = k + 1
                  if k == 1 then return "return " end
                  error("MID")
                end))
                k = 0
                print("E4", pcall(load, function()
                  k = k + 1
                  if k == 1 then return SRC end
                  error("AFTER")
                end))

                -- 6) 名字与模式配读取器
                print("F1", pcall(load(once(SRC), "a\\0b")))
                print("F2", select(2, load(once("x="), "=n")))
                print("F3", pcall(load(once(SRC), "=n", "b")))
                print("F4", pcall(load(once(SRC), "=n", "t")))
                print("F5", pcall(load(once(SRC), "=n", "bt")))

                -- 7) 读取器喂二进制 chunk
                local bin = string.dump(function() return 7 end)
                print("G1", (load(perbyte(bin)))())
                print("G2", (load(once(bin)))())
                print("G3", pcall(load(once(bin), "=n", "t")))
                print("G4", (load(perbyte(bin), "=n", "b"))())
                print("G5", pcall(load(chunks({ bin:sub(1, 1), bin:sub(2) }))))

                -- 8) env 参数
                local envs = { print = print }
                print("H1", (load(once("return print"), "=n", "t", envs))() == print)
                print("H2", (load(once("return _ENV.print"), "=n", "t", envs))() == print)
                print("H3", pcall(load(once(SRC), "=n", "t", 5)))
                print("H4", pcall(load(once(SRC), "=n", "t", nil)))
                local h5 = load(once("return 3"), "=n", "t", false)
                print("H5", h5 ~= nil and h5() or "nilchunk")

                -- 9) load 的第一个参数不是函数也不是字符串
                print("I1", pcall(load, true))
                print("I2", pcall(load, {}))
                print("I3", pcall(load))
                print("I4", pcall(load, 42))
                print("I5", pcall(load, 42, "=n", "t"))
                print("I6", (load("return 42") or function() return "x" end)())

                -- 10) 读取器返回带 \\0 的串：Zio 按长度拼，不砍
                print("J1", (load(chunks({ "return '", "a\\0b", "'" })))():byte(1, -1))
                print("J2", #(load(once("return 'a\\0b'")))())
                print("J3", pcall(load(once("return 1\\0+ 2"))))

                -- 11) 读取器在 load 里不能 yield（lua_load 走 luaD_callnoyield）
                print("K1", coroutine.resume(coroutine.create(function()
                  return pcall(load, function() coroutine.yield(1) return nil end)
                end)))
                print("K2", coroutine.resume(coroutine.create(function()
                  local ok, e = pcall(load, function() coroutine.yield(1) return SRC end)
                  return ok, e
                end)))
                -- 12) 先给 nil 再给内容：nil 就是结束，后面的不会被问
                print("K3", select("#", (load(chunks({ nil, SRC })))()))
                print("K4", pcall(load(chunks({ nil, SRC }))))
                -- 13) 读取器返回的串里带换行与 BOM（load 不像 loadfile 那样吃 BOM）
                print("K5", pcall(load(once("\\239\\187\\191return 1"))))
                print("K6", pcall(load(once("#!/x\\nreturn 1"))))
                print("K7", (load(once("\\n\\n  return 1  ")))())
                """);
    }
}
