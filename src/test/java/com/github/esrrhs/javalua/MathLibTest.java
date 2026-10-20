package com.github.esrrhs.javalua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * math 库的差分用例 —— 阶段 7。
 * <p>
 * 挑例的原则与 {@link StringLibTest} 一致：只测「照 C 源码写会写错」的那些点。
 * <ul>
 *   <li><b>子类型</b>：{@code math.type} 与 {@code print} 的组合才看得出
 *       integer/float 分裂——{@code math.floor(2.0)} 是 {@code 2}、
 *       {@code math.modf(3)} 的第二个返回值是 {@code 0.0}、
 *       {@code math.fmod(7,3)} 是 {@code 1} 而 {@code math.fmod(7.5,2)} 是
 *       {@code 1.5}。</li>
 *   <li><b>{@code max/min} 走的是 {@code lua_compare}</b>：会真的去比字符串，
 *       也会真的报 {@code attempt to compare number with string}。</li>
 *   <li><b>{@code log} 的 base 2/10 专用分支</b>：{@code math.log(8,2)}
 *       必须是 {@code 3.0}，走 {@code log(x)/log(base)} 会差一个 ulp。</li>
 *   <li><b>随机序列逐位一致</b>：显式 {@code randomseed} 之后，浮点序列、
 *       整数区间序列、{@code random(0)} 都要与参考实现相同。这是
 *       xoshiro256** 唯一能被外部观测到的地方。</li>
 *   <li><b>错误文本</b>：{@code (value expected)}、{@code (zero)}、
 *       {@code (interval is empty)}、{@code wrong number of arguments}。</li>
 * </ul>
 * <p>
 * 初始种子在参考实现里取自 {@code time(NULL)} 与 {@code L} 的地址，不可比，
 * 所以每个随机用例都<b>先显式播种</b>。
 */
class MathLibTest {

    @TestFactory
    List<DynamicTest> mathScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "num", NUMBERS);
        add(tests, "mm", MINMAX);
        add(tests, "rand", RANDOM);
        add(tests, "err", ERRORS);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    private static final String[] NUMBERS = {

            """
            print(math.pi, math.huge, math.maxinteger, math.mininteger)
            print(math.type(1), math.type(1.0), math.type("x"), math.type(nil), math.type(true))
            print(math.type(math.maxinteger), math.type(math.huge))
            """,

            """
            print(math.abs(-3), math.abs(3), math.abs(-3.5), math.abs(math.mininteger))
            print(math.type(math.abs(-3)), math.type(math.abs(math.mininteger)))
            print(math.abs("-7"), math.abs("-7.5"))
            """,

            """
            print(math.floor(3.7), math.ceil(3.2), math.floor(-3.7), math.ceil(-3.2))
            print(math.type(math.floor(3.7)), math.type(math.floor(3.0)))
            print(math.floor(3), math.ceil(3), math.type(math.floor(3)))
            print(math.floor(-0.0), math.ceil(-0.0), math.type(math.floor(-0.0)))
            print(math.floor(math.huge), math.type(math.floor(math.huge)))
            print(math.floor(1e300), math.type(math.floor(1e300)))
            print(math.floor("7.5"), math.ceil("-2.5"))
            """,

            """
            print(math.modf(3.7), math.modf(-3.7), math.modf(3))
            print(math.type((math.modf(3.7))), math.type(select(2, math.modf(3.7))))
            print(math.modf(math.huge))
            local a, b = math.modf(0/0)
            print(a ~= a, b ~= b)
            print(math.modf(-0.0))
            """,

            """
            print(math.fmod(7,3), math.fmod(-7,3), math.fmod(7,-3))
            print(math.type(math.fmod(7,3)), math.type(math.fmod(7.5,2)))
            print(math.fmod(7.5,2), math.fmod(-7.5,2), math.fmod(7.5,-2))
            print(math.fmod(math.mininteger, -1))
            """,

            """
            print(math.tointeger(3.0), math.tointeger(3.5), math.tointeger("7"), math.tointeger("7.5"))
            print(math.tointeger(math.huge), math.tointeger(0/0), math.tointeger(2^70))
            print(math.tointeger(-3.0), math.tointeger(math.mininteger))
            print(math.type(math.tointeger(3.0)), math.tointeger("  8  "))
            """,

            """
            print(math.ult(1,2), math.ult(2,1), math.ult(-1,2), math.ult(math.mininteger, -1))
            print(math.ult(0,0), math.ult(math.maxinteger, math.mininteger))
            """,

            // sqrt 是 IEEE 精确定义的，可以比到 17 位
            """
            print(string.format("%.17g", math.sqrt(2)))
            print(math.log(8), math.log(8,2), math.log(100,10), math.log(1024,2))
            print(string.format("%.14g", math.log(1000,3)), string.format("%.14g", math.log(2,8)))
            print(string.format("%.14g", math.log10(1000)))
            print(math.log(math.huge), math.log(0), math.log(-1) ~= math.log(-1))
            print(string.format("%.14g", math.log(2, 2)), string.format("%.14g", math.log(1e300, 2)))
            print(string.format("%.14g", math.log(0.5, 2)), string.format("%.14g", math.log(3, 2)))
            """,

            // 超越函数只比到 14 位：Java 的 Math 是 fdlibm，参考实现链的是平台
            // libm，两者在最后一位上不同（详见 MathLib 的类注释）
            """
            print(string.format("%.14g", math.sin(1)), string.format("%.14g", math.cos(1)))
            print(string.format("%.14g", math.tan(1)), string.format("%.14g", math.asin(0.5)))
            print(string.format("%.14g", math.acos(0.5)), string.format("%.14g", math.atan(0.5)))
            print(string.format("%.14g", math.atan(1,2)), string.format("%.14g", math.atan2(1,2)))
            print(string.format("%.14g", math.deg(math.pi)), string.format("%.14g", math.rad(180)))
            print(string.format("%.14g", math.deg(1)), string.format("%.14g", math.rad(1)))
            print(string.format("%.14g", math.sin(123456.789)), string.format("%.14g", math.cos(3.7)))
            print(string.format("%.14g", math.exp(1)), string.format("%.14g", math.exp(-10)))
            """,

            // frexp/ldexp 是纯位操作，可以比到 17 位
            """
            print(math.frexp(8))
            print(math.frexp(0), math.frexp(1), math.frexp(0.5), math.frexp(-3))
            print(math.frexp(math.huge), math.frexp(-math.huge))
            print(math.frexp(5e-324), math.frexp(1e300), math.frexp(2^53))
            print(string.format("%.17g", math.ldexp(3,2)), string.format("%.17g", math.ldexp(1,-1074)))
            print(string.format("%.17g", math.ldexp(0.5, 1024)), string.format("%.17g", math.ldexp(0, 5)))
            print(string.format("%.14g", math.pow(2,0.5)), string.format("%.17g", math.pow(2,10)))
            print(string.format("%.14g", math.cosh(1)), string.format("%.14g", math.sinh(1)))
            print(string.format("%.14g", math.tanh(1)), string.format("%.14g", math.pow(3, 0.3333)))
            """,
    };

    private static final String[] MINMAX = {

            """
            print(math.max(1,2,3), math.min(1,2,3))
            print(math.max(1,2.0), math.min(1,2.0))
            print(math.type(math.max(1,2.0)), math.type(math.min(1,2.0)))
            print(math.max(2.0,1), math.type(math.max(2.0,1)))
            print(math.max(3), math.min(3), math.type(math.min(3)))
            print(math.max(-0.0, 0.0), math.type(math.max(-0.0, 0.0)))
            """,

            """
            print(math.max("a","b"), math.min("b","a"), math.max("abc","abd"))
            print(math.max("", "a"), math.min("a", ""))
            """,

            """
            print(math.max(1, 0/0), math.max(0/0, 1))
            print(math.min(1, 0/0), math.min(0/0, 1))
            print(math.max(math.huge, 1), math.min(-math.huge, 1))
            print(math.max(math.mininteger, math.maxinteger))
            """,
    };

    private static final String[] RANDOM = {

            """
            local s1, s2 = math.randomseed(42, 7)
            print(s1, s2, math.type(s1), math.type(s2))
            local t = {}
            for i = 1, 8 do t[i] = string.format("%.17g", math.random()) end
            print(table.concat(t, " "))
            """,

            """
            math.randomseed(1234, 5678)
            local t = {}
            for i = 1, 10 do t[i] = tostring(math.random(1, 100)) end
            print(table.concat(t, " "))
            """,

            """
            math.randomseed(99)
            local t = {}
            for i = 1, 10 do t[i] = tostring(math.random(-5, 5)) end
            print(table.concat(t, " "))
            """,

            """
            math.randomseed(1, 2)
            print(math.random(0), math.type(math.random(0)))
            math.randomseed(1, 2)
            print(math.random(0))
            """,

            """
            math.randomseed(7)
            print(math.random(3,3), math.random(-3,-3), math.random(0,0))
            print(math.type(math.random(1,1)))
            """,

            """
            -- 区间长度是 2 的幂与非 2 的幂各来一遍：前者走掩码、后者走梅森数重投
            math.randomseed(2024)
            local t = {}
            for i = 1, 8 do t[i] = math.random(0, 255) end
            print(table.concat(t, " "))
            math.randomseed(2024)
            local u = {}
            for i = 1, 8 do u[i] = math.random(0, 100) end
            print(table.concat(u, " "))
            """,

            """
            math.randomseed(5)
            print(math.random(math.mininteger, math.maxinteger) ~= nil)
            print(math.type(math.random(-1, 1)))
            """,

            """
            local a, b = math.randomseed()
            print(math.type(a), math.type(b))
            print(math.random(1, 10) >= 1)
            """,
    };

    private static final String[] ERRORS = {

            """
            print(pcall(math.max))
            print(pcall(math.min))
            print(pcall(math.tointeger))
            print(pcall(math.type))
            print(pcall(math.abs))
            print(pcall(math.abs, "x"))
            print(pcall(math.floor, "x"))
            print(pcall(math.log, 8, "x"))
            """,

            """
            print(pcall(math.fmod, 1))
            print(pcall(math.fmod, 1, 0))
            print(pcall(math.fmod, "a", 1))
            print(pcall(math.fmod, 1, "a"))
            print(pcall(math.ult, 1))
            print(pcall(math.ult, 1.5, 2))
            print(pcall(math.ult, 1, "x"))
            """,

            """
            print(pcall(math.random, 1, 2, 3))
            print(pcall(math.random, 5, 1))
            print(pcall(math.random, "a"))
            print(pcall(math.random, 1.5))
            print(pcall(math.randomseed, 1.5))
            print(pcall(math.randomseed, "x"))
            """,

            """
            print(pcall(math.max, 1, "b"))
            print(pcall(math.min, "a", 1))
            local mt = setmetatable({}, {__lt = function(a,b) return true end})
            print(pcall(math.max, mt, 1))
            """,

            """
            local f = function() return math.fmod(nil, 1) end
            print(pcall(f))
            print(pcall(math.fmod, nil, 1))
            """,
    };
}
