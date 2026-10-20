package com.github.esrrhs.javalua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * {@code load} / {@code loadfile} / {@code dofile} 的差分用例 —— 阶段 8。
 * <p>
 * 这一批盯的是<b>入口</b>那一层，不是编译器：
 * <ul>
 *   <li><b>{@code load} 失败给两个值</b>：{@code nil, 消息}，不抛错；
 *       而 {@code dofile} 失败是<b>抛</b>错；</li>
 *   <li><b>chunk 名</b>：{@code load(s)} 默认拿 {@code s} 自己当名字，
 *       {@code loadfile} 的是 {@code "@" + 路径}，两者打印时都会被
 *       {@code luaO_chunkid} 剥掉前缀；{@code "=x"} 才是「名字原样用」；</li>
 *   <li><b>{@code load(s, name, mode, env)}</b> 的第四参数替换主 chunk 的
 *       {@code _ENV}（也就是它的第一个 upvalue）；</li>
 *   <li><b>读函数</b>返回 nil 表示结束，返回非字符串非数字要报
 *       {@code reader function must return a string}；</li>
 *   <li><b>模式先判</b>：{@code checkmode} 在读内容<b>之前</b>就跑，
 *       所以 {@code load("\27Lua", nil, "t")} 报的是模式错而不是
 *       {@code precompiled part of a file}；消息处理函数的生效位置
 *       （抛出点，不是 {@code pcall} 边界）也跟着变。</li>
 * </ul>
 * <p>
 * 二进制那条路（{@code luaU_dump} / {@code luaU_undump}）不在这里测：产物逐字节
 * 比在 {@code DumpTest}，畸形字节码的健壮性在 {@code BinarySafetyTest}，
 * {@code load}/{@code string.dump} 往返在 {@code BinaryChunkTest}。
 */
class LoadTest {

    @TestFactory
    List<DynamicTest> loadScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "load", LOAD);
        add(tests, "env", ENV);
        add(tests, "reader", READER);
        add(tests, "file", FILE);
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

    /* ==================== load ==================== */

    private static final String[] LOAD = {
            """
            local f = load("return 1+1")
            print(type(f), f())
            print(load("")())
            print(load("return")())
            local g = load("local a, b = 1, 2 return a + b")
            print(g())
            print(load("return ...")(1, 2, 3))
            """,

            """
            -- 默认 chunk 名就是源码本身
            print(load("x++"))
            print(load("return 1", "=myname"))
            local f, e = load("x++", "=myname")
            print(f, e)
            local g = load("error('boom')", "=myname")
            print(pcall(g))
            """,

            """
            -- chunk 名以 '@' 开头时打印要被剥掉
            local f, e = load("x++", "@/tmp/whatever.lua")
            print(f, e)
            local g = load("error('boom')", "@myfile.lua")
            print(pcall(g))
            local h = load("error('boom')", "plain")
            print(pcall(h))
            """,

            """
            -- 模式校验：'b' / 't'
            print(load("return 1", nil, "b"))
            print(load("return 1", nil, "t"))
            print(load("return 1", nil, "bt"))
            print(load("return 1", nil, ""))
            print(load("return 1", nil, "x"))
            print(load("return 1", nil, 42))
            """,

            """
            -- 数字也会当源码编（lua_tolstring）
            local f = load(42)
            print(type(f), f())
            local g = load(42, "name")
            print(g())
            print(load("42")())
            """,

            """
            -- 加载出来的函数就是普通闭包
            local f = load("return function(a) return a * 2 end")
            local inner = f()
            print(inner(21))
            print(type(f), tostring(f):sub(1, 8))
            """, };

    /* ==================== _ENV ==================== */

    private static final String[] ENV = {
            """
            -- 第四参数替换主 chunk 的 _ENV
            local f = load("return x", nil, "t", {x = 99})
            print(f())
            local g = load("x = 7 return x", nil, "t", {})
            print(g())
            local env = {print = print}
            local h = load("print(_ENV == nil)", nil, "t", env)
            h()
            """,

            """
            -- 传 nil 也照样设（_ENV 就变成 nil，取全局会报错）
            local f = load("return x", nil, "t", nil)
            print(pcall(f))
            local g = load("return 1", nil, "t", nil)
            print(g())
            """,

            """
            -- 没传第四参数时 _ENV 是全局表
            X_GLOBAL = 5
            local f = load("return X_GLOBAL")
            print(f())
            local g = load("X_GLOBAL = 6 return X_GLOBAL")
            print(g(), X_GLOBAL)
            """,

            """
            -- 设了 _ENV 之后改动不会漏到全局
            local env = setmetatable({}, {__index = _G, __newindex = _G})
            local f = load("y = 3 return y", nil, "t", env)
            print(f(), y)
            """, };

    /* ==================== 读函数 ==================== */

    private static final String[] READER = {
            """
            -- 一段一段喂
            local pieces = {"return ", "1 +", " 2"}
            local i = 0
            local f = load(function() i = i + 1 return pieces[i] end)
            print(f())
            """,

            """
            -- 第一次就返回 nil：空 chunk
            print(load(function() return nil end)())
            -- 返回数字也算字符串
            local n = 0
            print(pcall(load(function() n = n + 1 if n == 1 then return 42 else return nil end end)))
            """,

            """
            -- 返回非字符串非数字
            print(pcall(load(function() return {} end)))
            print(pcall(load(function() return true end)))
            """,

            """
            -- 读函数报错会冒出去
            print(pcall(load(function() error("reader died") end)))
            """,

            """
            -- 读函数版也有第二参数的默认名 "=(load)"
            local f, e = load(function() return "x++" end)
            print(f, e)
            """, };

    /* ==================== loadfile / dofile ==================== */

    private static final String[] FILE = {
            """
            local p = "$RT/fkld_f1.lua"
            local f = io.open(p, "w")
            f:write("return 7\\n")
            f:close()
            print(loadfile(p)())
            print(dofile(p))
            os.remove(p)
            """,

            """
            -- 每个 chunk 有自己的 _ENV 上值，改全局互相可见
            local p = "$RT/fkld_f2.lua"
            local f = io.open(p, "w")
            f:write("SHARED = SHARED and SHARED + 1 or 1\\nreturn SHARED\\n")
            f:close()
            print(dofile(p))
            print(dofile(p))
            print(SHARED)
            os.remove(p)
            """,

            """
            -- 无参 loadfile 读 stdin（测试里是空流）
            local f = loadfile()
            print(type(f), f())
            print(dofile())
            """,

            """
            -- dofile 返回被调 chunk 的全部返回值
            local p = "$RT/fkld_f4.lua"
            local f = io.open(p, "w")
            f:write("return 1, 2, 3\\n")
            f:close()
            print(dofile(p))
            print(select('#', dofile(p)))
            os.remove(p)
            """,

            """
            -- 文件里的错误带的是文件自己的位置
            local p = "$RT/fkld_f5.lua"
            local f = io.open(p, "w")
            f:write("local a = 1\\nerror('in file')\\n")
            f:close()
            print(pcall(dofile, p))
            local g, e = loadfile(p)
            print(pcall(g))
            os.remove(p)
            """,

            """
            -- '#' 开头的首行会被跳过（Unix 脚本）；行号靠补一个 \\n 保住
            local p = "$RT/fkld_f6.lua"
            local f = io.open(p, "w")
            f:write("#!/usr/bin/lua\\nreturn 5\\n")
            f:close()
            print(dofile(p))
            local p2 = "$RT/fkld_f7.lua"
            f = io.open(p2, "w")
            f:write("#!/usr/bin/lua\\nerror('here')\\n")
            f:close()
            print(pcall(dofile, p2))
            os.remove(p)
            os.remove(p2)
            """,

            """
            -- loadfile 的 mode 参数
            local p = "$RT/fkld_f8.lua"
            local f = io.open(p, "w")
            f:write("return 1\\n")
            f:close()
            print(loadfile(p, "b"))
            print(loadfile(p, "t")())
            print(loadfile(p, "x"))
            os.remove(p)
            """, };

    /* ==================== 报错 ==================== */

    private static final String[] ERRORS = {
            """
            -- load 失败给 nil + 消息；dofile 失败是抛错
            print(load("x++"))
            print(pcall(load, "x++"))
            print(pcall(dofile, "/nonexistent_dir_zzz/x.lua"))
            print(loadfile("/nonexistent_dir_zzz/x.lua"))
            """,

            """
            -- 参数检查
            print(pcall(load))
            print(pcall(load, nil))
            print(pcall(load, true))
            print(pcall(load, {}))
            print(pcall(loadfile, 42))
            """,

            """
            -- 二进制 chunk：模式不含 'b' 时报模式错（这一条已对齐）
            print(load("\\27Lua"))
            print(load("\\27Lua", nil, "b"))
            """,

            """
            -- 消息处理函数是「抛出点」生效，不是「pcall 边界」生效。
            -- luaD_protectedparser 把 L->errfunc 原样传进内层保护区，
            -- 于是读函数里的 error() 当场被外层 xpcall 的处理器接住。
            local function X(tag, fn)
              local r = table.pack(xpcall(fn, function(m) return "H:" .. tostring(m) end))
              local out = tag
              for i = 1, r.n do out = out .. " | " .. tostring(r[i]) end
              print(out .. " | n=" .. r.n)
            end
            -- 走 luaD_throw 直跳：不带 H
            X("syntax", function() return load("x++") end)
            X("syntax2", function() return load(function() return "x++" end) end)
            X("mode", function() return load("return 1", "=x", "q") end)
            X("mode2", function() return loadfile("$RT/case.lua", "q") end)
            X("cannotopen", function() return loadfile("$RT/nope.lua") end)
            -- 走 luaG_errormsg：带 H
            X("readererr", function() return load(function() error("boom") end) end)
            X("readerbad", function() return load(function() return true end) end)
            X("readerlvl0", function() return load(function() error("b", 0) end) end)
            X("readernonstr", function() return load(function() error({ "t" }) end) end)
            -- dofile 的打不开要走 lua_error，所以带 H
            X("dofile", function() return dofile("$RT/nope.lua") end)
            -- 协程不继承 errfunc：新线程那份是 0
            X("co", function()
              local co = coroutine.create(function()
                return load(function() error("co") end)
              end)
              return select(2, coroutine.resume(co))
            end)
            """, };
}
