package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 阶段 4 的闸门：闭包与 upvalue、{@code goto}/标签、变长参数、多重赋值与多重返回。
 * <p>
 * 与 {@link RuntimeTest} 走同一套差分驱动（{@link Diff}），差别只在选例：这里
 * 专挑「同一个变量被多处引用」的场景——闭包捕获、upvalue 开关闭、跳出作用域、
 * 参数个数与返回值个数的各种失配。这类 bug 的共同特征是<b>不报错、只是值不对</b>：
 * 一个 upvalue 关错了时机，闭包读到的就是过期值，脚本照样跑完。
 * <p>
 * 所以这里的用例一律「先改、后读」：写进去一个值，换一条路径（离开作用域、
 * 跳到标签、走一趟函数调用）再读回来，让读不到正确值的实现当场露馅。
 */
class ClosureTest {

    @BeforeAll
    static void requireReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN. found: " + ReferenceLua.describe());
    }

    static List<String> scripts() {
        List<String> s = new ArrayList<>();

        // ---- 闭包与 upvalue ----

        // 一个 upvalue 被两个闭包共享，改一个另一个要看得见
        s.add("local x = 0 local function inc() x = x + 1 end"
                + " local function get() return x end"
                + " inc() inc() inc() print(get(), x)");
        // 反向：闭包里改，外面读
        s.add("local x = 1 local function set(v) x = v end"
                + " set(42) print(x)");
        // 闭包在变量作用域结束之后仍然活着 —— 这是「关闭 upvalue」的核心场景
        s.add("local f do local x = 'captured' f = function() return x end end print(f())");
        // 同一个外层局部变量被三层嵌套捕获
        s.add("local x = 'a' local f1 = function() local f2 = function()"
                + " local f3 = function() return x end return f3 end return f2 end print(f1()()())");
        // upvalue 指向表：共享的是引用，不是副本
        s.add("local t = {} local function put(k, v) t[k] = v end"
                + " local function get(k) return t[k] end put('a', 1) print(get('a'), #t)");
        // 循环里建闭包：每个闭包捕获自己的那份 i（Lua 5.4 的语义）
        s.add("local fs = {} for i = 1, 3 do fs[i] = function() return i end end"
                + " print(fs[1](), fs[2](), fs[3]())");
        s.add("local fs = {} local i = 1 while i <= 3 do"
                + " local j = i fs[i] = function() return j end i = i + 1 end"
                + " print(fs[1](), fs[2](), fs[3]())");
        // 泛型 for 的控制变量同样每轮一份
        s.add("local fs = {} for _, v in ipairs({'x','y','z'}) do"
                + " fs[#fs+1] = function() return v end end"
                + " print(fs[1](), fs[2](), fs[3]())");
        // 闭包返回闭包，中间那层的局部变量也要留住
        s.add("local function counter() local n = 0"
                + " return function() n = n + 1 return n end end"
                + " local a, b = counter(), counter() print(a(), a(), b(), a())");
        // 递归闭包：函数体里引用自己
        s.add("local function fact(n) if n <= 1 then return 1 end return n * fact(n - 1) end"
                + " print(fact(10))");
        // 互相递归（靠前向声明）
        s.add("local isEven, isOdd"
                + " isEven = function(n) return n == 0 or isOdd(n - 1) end"
                + " isOdd = function(n) return n ~= 0 and isEven(n - 1) end"
                + " print(isEven(10), isOdd(10))");
        // upvalue 被关闭后再赋值，两边应当断开
        s.add("local f local x = 1 do f = function() return x end end x = 2 print(f(), x)");
        s.add("local x = 1 local f = function() return x end x = 2 print(f(), x)");
        // 表里的 upvalue 与栈上的同名变量
        s.add("local t = {v = 1} local function bump() t.v = t.v + 1 end"
                + " bump() bump() print(t.v)");
        // 把一个闭包当值传来传去
        s.add("local function make() local n = 0 return function() n = n + 1 return n end end"
                + " local t = {f = make()} print(t.f(), t.f())");
        // 方法定义里的 self 与 upvalue 混用
        s.add("local n = 0 local o = {} function o:inc() n = n + self.step end"
                + " o.step = 5 o:inc() o:inc() print(n)");

        // ---- goto 与标签 ----

        s.add("local i = 1 local s = 0 ::top:: if i <= 5 then s = s + i i = i + 1 goto top end"
                + " print(s)");
        s.add("for i = 1, 5 do if i == 3 then goto cont end print(i) ::cont:: end print('done')");
        s.add("do goto done ::skipped:: print('never') end ::done:: print('ok')");
        // goto 跳出多层块
        s.add("for i = 1, 3 do for j = 1, 3 do if j == 2 then goto next_i end print(i, j) end"
                + " ::next_i:: end print('end')");
        // goto 跳出循环后局部变量已失效
        s.add("local r do for i = 1, 3 do if i == 2 then goto out end end ::out:: r = 'jumped' end"
                + " print(r)");
        // 标签在块末尾（需要一个空语句收尾）
        s.add("local function f() do goto e end ::e:: return 'ret' end print(f())");
        // goto 与 break 混用
        s.add("local n = 0 while true do n = n + 1 if n >= 3 then break end end print(n)");
        // goto 跳回循环开头（continue 的写法）
        s.add("local s = 0 for i = 1, 6 do if i % 2 == 0 then goto cont end s = s + i ::cont:: end"
                + " print(s)");
        // 反向跳转跨过局部变量声明
        s.add("local n = 0 ::again:: n = n + 1 if n < 3 then goto again end print(n)");

        // ---- 变长参数 ----

        s.add("local function f(...) return select('#', ...) end"
                + " print(f(), f(nil), f(nil, nil), f(1, 2, 3))");
        s.add("local function f(...) local a, b, c = ... return a, b, c end print(f(1, 2, 3, 4))");
        s.add("local function f(...) local a, b = ... return b, a end print(f(1))");
        s.add("local function f(a, b, ...) return a, b, select('#', ...) end"
                + " print(f(1, 2, 3, 4, 5))");
        s.add("local function f(a, ...) return a, ... end print(f(1, 2, 3))");
        s.add("local function f(...) return ... end print(f(f(1, 2), 3))");
        s.add("local function f(...) return ... , 9 end print(f(1, 2))");
        s.add("local function f(...) local t = {...} return #t end print(f(1, 2, 3, nil))");
        s.add("local function sum(...) local s = 0 for _, v in ipairs({...}) do s = s + v end"
                + " return s end print(sum(1, 2, 3, 4, 5))");
        // 主 chunk 自己就是 vararg
        s.add("print(select('#', ...))");
        s.add("local a, b = ... print(a, b)");
        // 变长参数穿过元方法
        s.add("local mt = {__call = function(self, ...) return select('#', ...) end}"
                + " print(setmetatable({}, mt)(1, 2, 3))");
        // 变长参数与尾调用
        s.add("local function g(...) return select('#', ...) end"
                + " local function f(...) return g(...) end print(f(1, 2, 3))");

        // ---- 多重赋值 ----

        s.add("local a, b = 1, 2 a, b = b, a print(a, b)");
        s.add("local a, b, c = 1 print(a, b, c)");
        s.add("local a, b, c = 1, 2, 3, 4 print(a, b, c)");
        s.add("local function two() return 1, 2 end local a, b = two() print(a, b)");
        s.add("local function two() return 1, 2 end local a, b, c = two() print(a, b, c)");
        s.add("local function two() return 1, 2 end local a = two() print(a)");
        s.add("local a, b = (function() return 1, 2 end)() print(a, b)");
        // 右边先整体求值：t[1] 要用交换前的 t
        s.add("local t = {1, 2} t[1], t[2] = t[2], t[1] print(t[1], t[2])");
        s.add("local t = {a = 1, b = 2} t.a, t.b = t.b, t.a print(t.a, t.b)");
        s.add("local o = {} local i = 1 o[i], i = 'first', 2 print(o[1], i)");
        // 赋值链上带函数调用
        s.add("local function id(...) return ... end"
                + " local a, b, c = id(1), id(2, 3), id(4) print(a, b, c)");
        s.add("local function id(...) return ... end local a, b = id(id(1, 2)) print(a, b)");
        // 全局赋值
        s.add("g1, g2 = 1, 2 print(g1, g2)");
        s.add("local t = {} t.x, t.y = 1, 2 print(t.x, t.y)");
        // 多重赋值里的 upvalue
        s.add("local x, y = 1, 2 local function swap() x, y = y, x end"
                + " swap() print(x, y) swap() print(x, y)");

        // ---- 多重返回 ----

        s.add("local function three() return 1, 2, 3 end print(three())");
        s.add("local function three() return 1, 2, 3 end print((three()))");
        s.add("local function three() return 1, 2, 3 end"
                + " print(three(), three(), three())");
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {three(), three(), three()} print(#t)");
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {[1] = three()} print(#t, t[1])");
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {three(), (three())} print(#t)");
        // return 后面接表达式：只有最后一项展开
        s.add("local function f() local function three() return 1, 2, 3 end"
                + " return three(), three() end print(f())");
        s.add("local function f() local function three() return 1, 2, 3 end"
                + " return (three()) end print(f())");
        s.add("local function f(...) return ... end print(f(1, 2, 3))");
        s.add("local function f(...) return (...) end print(f(1, 2, 3))");
        // 尾调用
        s.add("local function loop(n, acc) if n == 0 then return acc end"
                + " return loop(n - 1, acc + n) end print(loop(10000, 0))");
        s.add("local function a(x) return b(x) end local function b(x) return x * 2 end"
                + " print(a(21))");
        // return 从深层块里跳出来
        s.add("local function f() for i = 1, 10 do if i == 3 then return i end end"
                + " return 'none' end print(f())");
        s.add("local function f() do do do return 'deep' end end end end print(f())");
        // 返回值个数在调用点被截断 / 补齐
        s.add("local function one() return 1 end local a, b, c = one() print(a, b, c)");
        s.add("local function none() end local a, b = none() print(a, b)");
        s.add("print((function() end)())");

        // ---- 批 2：专挑「不报错、只是值不对」的场景 ----

        // 闭包活得比栈帧久：返回之后栈会被后续调用反复覆盖，
        // upvalue 没在返回时关闭就会读到别人写进去的值
        s.add("local function make(v) local x = v return function() return x end end"
                + " local a = make('A') local b = make('B') local c = make('C')"
                + " print(a(), b(), c())");
        s.add("local function make(v) local x = v return function() return x end end"
                + " local fs = {} for i = 1, 8 do fs[i] = make(i * 10) end"
                + " local s = 0 for i = 1, 8 do s = s + fs[i]() end print(s)");
        // 闭包之后再跑一堆递归，把栈压深再回来读
        s.add("local function make(v) local x = v return function() return x end end"
                + " local f = make('keep')"
                + " local function rec(n) if n == 0 then return 0 end return 1 + rec(n - 1) end"
                + " rec(200) print(f())");
        // break 跳出循环时也要关 upvalue
        s.add("local f for i = 1, 5 do local x = i if i == 3 then f = function() return x end"
                + " break end end print(f())");
        // goto 跳出时同样
        s.add("local f do for i = 1, 5 do local x = i if i == 2 then"
                + " f = function() return x end goto out end end end ::out:: print(f())");
        // 两个闭包捕获同一个变量必须共用一份 upvalue
        s.add("local x = 0 local a = function() return x end local b = function() x = x + 1 end"
                + " b() b() print(a(), x)");
        // 三层嵌套各改一次，值要一路传下去
        s.add("local x = 0"
                + " local f1 = function() x = x + 1 local y = x"
                + "   local f2 = function() x = x + 10 return y end return f2 end"
                + " local g = f1() print(g(), x) print(g(), x)");
        // 关闭之后外层再改，闭包不应跟着变
        s.add("local f = nil do local x = 1 f = function() return x end end"
                + " local t = {f()} print(t[1])");
        // 闭包在表里，稍后才调
        s.add("local t = {} do local x = 'held' t.get = function() return x end"
                + " t.set = function(v) x = v end end t.set('new') print(t.get())");
        // 闭包由 C 函数（pcall）调用
        s.add("local x = 7 print(pcall(function() return x end))");
        // 出错时 upvalue 也要关住
        s.add("local f do local x = 'err' f = function() return x end"
                + " pcall(function() error('x') end) end print(f())");

        // <const> / <close> 与闭包
        s.add("local x <const> = 5 local f = function() return x end print(f())");
        s.add("local log = {} do local c <close> = setmetatable({},"
                + " {__close = function() log[#log+1] = 'closed' end})"
                + " local f = function() return 'in' end print(f()) end"
                + " print(log[1])");
        s.add("local x <close> = nil print(x)");

        // 变长参数的边界
        s.add("local function f(...) return select('#', ...) end"
                + " print(f(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))");
        s.add("local function f(...) return select(-1, ...) end print(f(1, 2, 3))");
        s.add("local function f(...) return select(-3, ...) end print(f(1, 2, 3))");
        s.add("local function f(...) return select(-4, ...) end print(f(1, 2, 3))");
        s.add("local function f(...) return ... end print(f(nil, nil))");
        s.add("local function f(...) local t = {...} return t[1], t[2], #t end print(f(nil, 1))");
        s.add("local function f(n, ...) return n, select('#', ...) end print(f(1))");
        s.add("local args = {} for i = 1, 60 do args[i] = i end"
                + " local function f(...) return select('#', ...) end print(f(table.unpack(args)))");

        // 多重赋值的求值顺序：下标表达式在赋值之前全部求值
        s.add("local t = {} local i = 1 local function next_i() i = i + 1 return i end"
                + " t[next_i()], t[next_i()] = 'a', 'b' print(t[2], t[3], i)");
        s.add("local t = {} local n = 0 local function bump() n = n + 1 return n end"
                + " t[bump()] = bump() print(n, t[1])");
        // 带 __newindex 的多次赋值：每一次都过元方法
        s.add("local log = {} local t = setmetatable({},"
                + " {__newindex = function(_, k, v) log[#log+1] = k .. '=' .. v end})"
                + " t.a, t.b = 1, 2 print(#log, log[1], log[2])");
        // 交换里带函数调用
        s.add("local function id(...) return ... end local a, b = 1, 2"
                + " a, b = id(b), id(a) print(a, b)");
        // 多重赋值右边比左边短 / 长
        s.add("local a, b, c = (function() return 1 end)() print(a, b, c)");
        s.add("local a, b = 1, 2, 3 print(a, b)");
        // 多重赋值链：右值里的函数调用先全部跑完
        s.add("local order = {} local function p(x) order[#order+1] = x return x end"
                + " local a, b = p(1), p(2) print(order[1], order[2], a, b)");

        // 多重返回的落点
        s.add("local function three() return 1, 2, 3 end"
                + " local t = {[1] = 'x', three()} print(#t, t[1], t[2])");
        s.add("local function three() return 1, 2, 3 end"
                + " local ok, a, b = pcall(three) print(ok, a, b)");
        s.add("local function three() return 1, 2, 3 end"
                + " print(#{three(), three(), three(), three()})");
        // 尾调用链
        s.add("local function c(x) return x end local function b(x) return c(x) end"
                + " local function a(x) return b(x) end print(a('through'))");
        s.add("local function even(n) if n == 0 then return true end return odd(n - 1) end"
                + " local function odd(n) if n == 0 then return false end return even(n - 1) end"
                + " print(even(500), odd(500))");
        // return 在元方法里
        s.add("local mt = {__call = function(_, x) return x, x * 2 end}"
                + " print(setmetatable({}, mt)(3))");
        // 闭包返回值又当参数
        s.add("local function mk() return function() return 1, 2 end end"
                + " print(select('#', mk()()))");

        // ---- 批 3：<close> 的关闭顺序与 upvalue 的关闭时机 ----
        //
        // 这一组专测「什么时候关、按什么顺序关」。顺序错了不报错，只是
        // 打印出来的一串名字次序不对，所以每条都要把次序完整打出来。

        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function() log[#log+1] = n end}) end"
                + " do local a <close> = mk('a') local b <close> = mk('b')"
                + " local c <close> = mk('c') end print(log[1], log[2], log[3])");
        // return 之前也要关
        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function() log[#log+1] = n end}) end"
                + " local function f() local a <close> = mk('a') return 'ret' end"
                + " print(f(), log[1])");
        // break 之前
        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function() log[#log+1] = n end}) end"
                + " for i = 1, 3 do local a <close> = mk('a' .. i) if i == 2 then break end end"
                + " print(log[1], log[2], log[3])");
        // goto 之前
        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function() log[#log+1] = n end}) end"
                + " do for i = 1, 3 do local a <close> = mk('a' .. i)"
                + " if i == 2 then goto out end end end ::out:: print(log[1], log[2], log[3])");
        // 循环体每轮关一次
        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function() log[#log+1] = n end}) end"
                + " for i = 1, 3 do local a <close> = mk(i) end print(#log, log[1], log[3])");
        // 出错时按栈从顶往下关，且错误对象要传进去
        s.add("local log = {} local function mk(n) return setmetatable({},"
                + " {__close = function(_, err) log[#log+1] = n .. ':' .. tostring(err) end}) end"
                + " print(pcall(function() local a <close> = mk('a')"
                + " local b <close> = mk('b') error('boom') end))"
                + " print(log[1], log[2])");
        // __close 自己出错：沿用原错误
        s.add("print(pcall(function() local a <close> = setmetatable({},"
                + " {__close = function() error('in close') end}) error('orig') end))");
        // 普通（非 <close>）upvalue 出错时也不能泄漏
        s.add("local f do local x = 'keep' f = function() return x end"
                + " pcall(function() error('x') end) end print(f())");
        // 同一个函数被调用两次，每次是各自的 upvalue
        s.add("local function mk() local v = 0 return function() v = v + 1 return v end,"
                + " function() return v end end"
                + " local a1, a2 = mk() local b1, b2 = mk()"
                + " a1() a1() b1() print(a2(), b2())");
        // 闭包捕获的 upvalue 又被内层闭包捕获
        s.add("local x = 1 local outer = function()"
                + " local inner = function() return x end x = x + 1 return inner end"
                + " local f = outer() print(f(), x)");
        // 函数在表里定义，捕获外层局部
        s.add("local n = 5 local t = {get = function() return n end,"
                + " set = function(v) n = v end} t.set(9) print(t.get(), n)");
        // _ENV 作为 upvalue 被换掉
        s.add("local print = print do local _ENV = {print = print}"
                + " hidden = 1 print(hidden) end print(hidden)");
        // 局部函数与全局函数
        s.add("function gfun() return 'global' end print(gfun())");
        s.add("local t = {} function t.m() return 'method' end print(t.m())");
        s.add("local t = {} function t:m() return self.tag end t.tag = 'T' print(t:m())");

        return s;
    }

    @ParameterizedTest(name = "script #{index}")
    @MethodSource("scripts")
    void scriptMatchesReference(String src) {
        Diff.assertMatches(src);
    }
}
