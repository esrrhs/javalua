package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * table 库的差分用例 —— 阶段 7。
 * <p>
 * {@code ltablib.c} 里最容易写错的是这几点：
 * <ul>
 *   <li><b>{@code __index/__newindex/__len} 会把非表「变成」表</b>：
 *       {@code TAB_R/W/L} 三个宏都要求元方法<b>存在</b>才放行，所以
 *       {@code table.insert({}, 1, x)} 在对方只有 {@code __index} 时报的是
 *       「bad argument #1 to 'insert' (table expected, got userdata)」而不是
 *       「没有 __newindex」。</li>
 *   <li><b>{@code table.sort} 是不稳定的</b>：参考实现是原地快排，测试只能
 *       断言「有序」不能断言「相等元素的相对次序」。另外排序比较函数返回
 *       不一致时会有一句专门的 {@code invalid order function for sorting}。</li>
 *   <li><b>{@code remove} 的返回值</b>：空表返回 nil 而不是报错；
 *       {@code remove(t, n)} 的 {@code n} 允许 {@code #t+1}。</li>
 *   <li><b>{@code concat} 会真的把数字转成字符串</b>，且只对
 *       {@code [1, #t]} 区间生效。</li>
 * </ul>
 */
class TableLibTest {

    @TestFactory
    List<DynamicTest> tableScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "basic", BASIC);
        add(tests, "sort", SORT);
        add(tests, "meta", META);
        add(tests, "err", ERRORS);
        add(tests, "overflow", OVERFLOW);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    /**
     * {@code choosePivot} 的不变量 {@code lo + r4 <= p <= up - r4} —— C 版里
     * 这是一条 {@code lua_assert}，Java 版得自己守住。
     * <p>
     * 为什么值得单独钉一条：{@code IdxT} 是 {@code unsigned int}，
     * {@code rnd} 也是 {@code unsigned int}，所以 C 的取模恒为非负。Java 这边
     * {@code int} 有符号，而随机种子本来就是 C 那 32 个比特——一半概率是负数，
     * 有符号取模跟着变负，{@code p} 就掉出 {@code [lo, up]}，极端情形
     * {@code p <= 0}，{@code a[p]} 是 nil，报出来的却是
     * 「attempt to compare nil with number」。
     * <p>
     * 这条缺陷在 {@code sort.lua} 上的表现是<b>偶发</b>：它用未播种的
     * {@code math.random()} 造 5 万个元素，只有分区严重失衡时才会去换随机种子。
     * 反射直打私有方法，把一个概率性失败变成确定性断言。
     */
    @Test
    void choosePivotStaysInsideTheInterval() throws Exception {
        Method m = TableLib.class.getDeclaredMethod("choosePivot",
                int.class, int.class, int.class);
        m.setAccessible(true);
        int[] seeds = {
                0, 1, 7, 12345, Integer.MAX_VALUE,
                -1, -12500, -24998, Integer.MIN_VALUE,
                (int) 0xFFFFFFFFL, (int) 0xDEADBEEFL, (int) 0x80000000L,
        };
        int[][] ranges = {
                {1, 50000}, {1, 5000}, {1, 1000}, {2, 101}, {100, 500}, {3, 40000},
        };
        for (int[] r : ranges) {
            int lo = r[0];
            int up = r[1];
            int r4 = (up - lo) / 4;
            for (int rnd : seeds) {
                int p = (Integer) m.invoke(null, lo, up, rnd);
                assertTrue(p >= lo + r4 && p <= up - r4,
                        "choosePivot(" + lo + ", " + up + ", " + rnd + ") = " + p
                                + "，越出了 [" + (lo + r4) + ", " + (up - r4) + "]");
            }
        }
    }

    private static final String[] BASIC = {

            """
            local t = {10, 20, 30}
            print(#t, table.concat(t, ","), table.concat(t, ",", 2), table.concat(t, ",", 2, 3))
            print(table.concat(t), table.concat({1, 2.5, "x"}, "-"))
            print(table.concat({}, ","), table.concat({1, nil, 3}, ","))
            """,

            """
            local t = {1, 2, 3}
            table.insert(t, 4)
            print(table.concat(t, ","))
            table.insert(t, 2, 99)
            print(table.concat(t, ","))
            print(table.remove(t), table.concat(t, ","))
            print(table.remove(t, 1), table.concat(t, ","))
            local e = {}
            print(table.remove(e), table.remove(e, 1), table.remove(e, 0))
            """,

            """
            local a = {1, 2, 3, 4, 5}
            print(table.unpack(a))
            print(table.unpack(a, 2))
            print(table.unpack(a, 2, 4))
            print(table.unpack(a, 5, 4))
            print(table.unpack(a, 1, 5))
            print(select("#", table.unpack(a)))
            """,

            """
            local p = table.pack(1, nil, 3)
            print(p.n, p[1], p[2], p[3])
            print(table.pack().n)
            print(select("#", table.unpack({}, 1, 0)))
            """,

            """
            local a = {1, 2, 3, 4, 5}
            local b = {}
            table.move(a, 1, 5, 1, b)
            print(table.concat(b, ","))
            table.move(a, 2, 4, 1, a)
            print(table.concat(a, ","))
            table.move(a, 1, 3, 3)
            print(table.concat(a, ","))
            local c = {}
            table.move(c, 1, 0, 1, c)
            print(#c)
            """,

            """
            local t = {3, 1, 2}
            table.sort(t)
            print(table.concat(t, ","))
            table.sort(t, function(a, b) return a > b end)
            print(table.concat(t, ","))
            local s = {"bb", "a", "ccc"}
            table.sort(s)
            print(table.concat(s, ","))
            """,

            """
            local t = {}
            for i = 1, 50 do t[i] = (i * 37) % 50 end
            table.sort(t)
            local ok = true
            for i = 2, 50 do if t[i-1] > t[i] then ok = false end end
            print(ok, #t, t[1], t[50])
            local n = 0
            for k in pairs(t) do n = n + 1 end
            print(n)
            """,
    };

    private static final String[] SORT = {

            """
            -- 相等元素排完仍是「全序正确」，但相对次序不确定，只能断言有序性
            local t = {}
            for i = 1, 30 do t[i] = i % 3 end
            table.sort(t)
            local ok = true
            for i = 2, 30 do if t[i-1] > t[i] then ok = false end end
            print(ok, table.concat(t, ""))
            """,

            """
            -- 越过 RANLIMIT(100) 才会走「随机枢轴」那条路；枢轴越界时 a[p] 是 nil，
            -- sort_comp 会在调用比较函数之前就抛错，所以这里用 pcall 兜住。
            math.randomseed(42)
            local t = {}
            for i = 1, 5000 do t[i] = math.random() end
            print(pcall(table.sort, t, function(a, b) return a < b end))
            local ok = true
            for i = 2, 5000 do if t[i-1] > t[i] then ok = false end end
            print(ok, #t)
            """,

            """
            print(pcall(table.sort, {3,1,2}, function(a,b) return true end))
            print(pcall(table.sort, {1,2,3}, function(a,b) return false end))
            print(pcall(table.sort, {1,2,3}, function(a,b) return nil end))
            print(pcall(table.sort, {1,2,3}, 42))
            print(pcall(table.sort, 42))
            """,

            """
            local t = {5, -1, 0, 3.5, -2.5, 2}
            table.sort(t, function(a,b) return math.abs(a) < math.abs(b) end)
            print(table.concat(t, ","))
            local u = {"10", "9", "1"}
            table.sort(u)
            print(table.concat(u, ","))
            """,

            """
            -- 混合 integer/float：sort 不该改变子类型
            local t = {1.0, 2, 3.0}
            table.sort(t)
            print(math.type(t[1]), math.type(t[2]), math.type(t[3]))
            """,
    };

    private static final String[] META = {

            """
            -- __index 生效：把 userdata 当表读
            local ud = setmetatable({}, {__index = function(t, k) return k * 2 end})
            print(table.concat({1,2}, ","))
            local mt = {__index = {1, 2, 3}}
            local t = setmetatable({}, mt)
            print(#t, table.concat(t, ","))
            print(table.unpack(t))
            """,

            """
            -- __newindex 生效：insert/remove 能写
            local store = {}
            local t = setmetatable({}, {
                __index = function(_, k) return store[k] end,
                __newindex = function(_, k, v) store[k] = v end,
                __len = function() return #store end,
            })
            table.insert(t, "a")
            table.insert(t, "b")
            print(store[1], store[2])
            print(table.remove(t))
            print(store[1], store[2])
            """,

            """
            -- __len 生效：# 走元方法
            local t = setmetatable({}, {__len = function() return 7 end})
            print(#t)
            print(pcall(table.insert, t, 1))
            local u = setmetatable({}, {__len = function() return "x" end})
            print(pcall(#u))
            """,
    };

    /**
     * {@code ltablib.c} 里两处<b>整数溢出</b>的边界。
     * <p>
     * 两处 C 版都靠「无符号/有符号回绕」来判越界，而 Java 的 {@code long}
     * 回绕的方向不一样，直译会漏判——漏了之后不是报错，是
     * {@code OutOfMemoryError}（{@code unpack} 一路 push）或者报<b>错的那一条
     * 参数</b>（{@code move}）。上游 {@code sort.lua} 一上来就撞这两处。
     */
    private static final String[] OVERFLOW = {

            /* tunpack：n 是无符号的，e - i 溢出时按无符号比 */
            """
            local maxI, minI = math.maxinteger, math.mininteger
            local maxi, mini = (1 << 31) - 1, -(1 << 31)
            print(pcall(table.unpack, {}, 0, maxi))
            print(pcall(table.unpack, {}, 1, maxi))
            print(pcall(table.unpack, {}, 0, maxI))
            print(pcall(table.unpack, {}, 1, maxI))
            print(pcall(table.unpack, {}, mini, maxi))
            print(pcall(table.unpack, {}, -maxi, maxi))
            print(pcall(table.unpack, {}, minI, maxI))
            print(pcall(table.unpack, {}, 1, 2^31))
            print(pcall(table.unpack, {}, 1, 999999))
            print(pcall(table.unpack, {}, 1, 1000001))
            """,

            /* tmove：判据是 e < MAXINTEGER + f，化简成 e - f < MAXINTEGER 会漏 */
            """
            local maxI, minI = math.maxinteger, math.mininteger
            print(pcall(table.move, {}, 0, maxI, 1))
            print(pcall(table.move, {}, -1, maxI - 1, 1))
            print(pcall(table.move, {}, minI, -1, 1))
            print(pcall(table.move, {}, minI, maxI, 1))
            print(pcall(table.move, {}, 1, maxI, 2))
            print(pcall(table.move, {}, 1, 2, maxI))
            print(pcall(table.move, {}, minI, -2, 2))
            print(pcall(table.move, {}, 1, 2, 3, 4))
            """,
    };

    private static final String[] ERRORS = {

            """
            print(pcall(table.insert, 42, 1))
            print(pcall(table.insert, "s", 1))
            print(pcall(table.insert, {}, 1, 2, 3))
            print(pcall(table.insert, {}, "x", 1))
            print(pcall(table.insert, {}, 0, 1))
            print(pcall(table.insert, {1}, 3, 1))
            """,

            """
            print(pcall(table.remove, 42))
            print(pcall(table.remove, {}, "x"))
            print(pcall(table.concat, 42))
            print(pcall(table.concat, {1, {}}, ","))
            print(pcall(table.concat, {{}}, ","))
            print(pcall(table.concat, {1, 2}, {}))
            """,

            """
            print(pcall(table.unpack, 42))
            print(pcall(table.unpack, {1,2,3}, "x"))
            print(pcall(table.unpack, {1,2,3}, 1, "x"))
            print(pcall(table.move, 42, 1, 2, 1))
            print(pcall(table.move, {1,2}, "x", 2, 1))
            """,

            """
            local f = function() return table.insert(nil, 1) end
            print(pcall(f))
            print(pcall(table.insert, nil, 1))
            print(select(2, pcall(table.concat)))
            """,
    };
}
