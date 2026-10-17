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
}
