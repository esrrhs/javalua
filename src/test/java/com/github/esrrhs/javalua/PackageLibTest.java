package com.github.esrrhs.javalua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * package 库的差分用例 —— 阶段 7。
 * <p>
 * 只比「纯 Lua 那条路」：{@code preload} 搜索器与 {@code Lua} 搜索器的行为
 * 可以与参考实现逐字节一致，C 搜索器不能——参考二进制是用 {@code dlopen} 编
 * 出来的，本实现没有动态库。所以：
 * <ul>
 *   <li><b>不比对 {@code package.loadlib}</b> 的返回值（消息是
 *       {@code dlerror()} 的文本，平台相关）；</li>
 *   <li><b>不比对「能找到 {@code .so}」的 {@code require}</b>——测试里根本
 *       不建 {@code .so} 文件，C 搜索器一律停在「找不到文件」，两边一致；</li>
 *   <li>{@code package.path} / {@code cpath} 的<b>数值</b>不比：那是编译期
 *       常量，本实现照抄了默认值，但 {@code LUA_PATH} 环境变量会改它。用例里
 *       都先显式设成临时目录再 require。</li>
 * </ul>
 * <p>
 * 真正盯住的：
 * <ul>
 *   <li><b>{@code require} 返回两个值</b>：模块本身与 loader 数据（Lua 模块
 *       是文件名，preload 是 {@code ":preload:"}）；</li>
 *   <li><b>模块返回 nil 时 {@code _LOADED[name]} 是 {@code true}</b>，
 *       {@code require} 也返回 {@code true}；</li>
 *   <li><b>{@code require} 的缓存</b>：第二次调用不再跑模块体；</li>
 *   <li><b>找不到时的错误消息</b>是各搜索器消息用 {@code \n\t} 拼起来的；</li>
 *   <li><b>{@code package.searchpath}</b> 的 {@code sep} / {@code dirsep}
 *       替换与 {@code no file '...'} 消息的拼法。</li>
 * </ul>
 */
class PackageLibTest {

    @TestFactory
    List<DynamicTest> packageScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "cfg", CONFIG);
        add(tests, "search", SEARCHPATH);
        add(tests, "preload", PRELOAD);
        add(tests, "lua", LUAMOD);
        add(tests, "err", ERRORS);
        add(tests, "deep", DEEP);
        add(tests, "cfallback", CFALLBACK);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    /* ==================== 表结构 ==================== */

    private static final String[] CONFIG = {
            """
            print(package.config)
            print(type(package.loaded), type(package.preload), type(package.searchers))
            print(#package.searchers)
            print(type(package.searchers[1]), type(package.searchers[2]))
            print(type(package.searchers[3]), type(package.searchers[4]))
            print(type(package.path), type(package.cpath))
            print(type(package.loadlib), type(package.searchpath))
            print(package.loaded.package == package)
            print(package.loaded.string == string)
            print(package.loaded["no_such_module_zzz"])
            print(require)
            """,

            """
            -- config 的五个字段：目录分隔符、路径分隔符、模板占位符、
            -- 可执行文件目录占位符、忽略标记
            local a,b,c,d,e = package.config:match("(%S+)\\n(%S+)\\n(%S+)\\n(%S+)\\n(%S+)\\n")
            print(a, b, c, d, e)
            print(package.path:find("./", 1, true) ~= nil)
            print(package.cpath:sub(-6))
            """,

            """
            -- searchers 是裸表：改动它会影响 require
            print(#package.searchers)
            local n = 0
            for k, v in ipairs(package.searchers) do n = n + 1 end
            print(n)
            print(package.searchers[5])
            print(package.searchers[0])
            """, };

    /* ==================== searchpath ==================== */

    private static final String[] SEARCHPATH = {
            """
            -- 找到就返回文件名
            local p = "/tmp/fkpk_sp1.lua"
            local f = io.open(p, "w")
            f:write("return {}\\n")
            f:close()
            print(package.searchpath("fkpk_sp1", "/tmp/?.lua"))
            print(package.searchpath("fkpk_sp1", "/tmp/?.lua;/tmp/?/init.lua"))
            os.remove(p)
            """,

            """
            -- 找不到：nil + "no file '...'" 逐条拼起来
            print(package.searchpath("zzz_nope", "./?.lua;./?/init.lua"))
            print(package.searchpath("zzz_nope", "./?.lua"))
            print(package.searchpath("zzz_nope", "a;;b"))
            """,

            """
            -- sep / dirsep：名字里的 sep 换成 dirsep 再去套模板
            print(package.searchpath("a.b", "./?.lua", ".", "/"))
            print(package.searchpath("a.b", "./?.lua"))
            print(package.searchpath("a.b", "./?.lua", "|", "-"))
            print(package.searchpath("a.b", "./?.lua", ".", "::"))
            print(package.searchpath("a", "./?.lua", "", ""))
            """,

            """
            -- 参数检查
            print(pcall(package.searchpath))
            print(pcall(package.searchpath, "x"))
            print(pcall(package.searchpath, "x", 42))
            print(package.searchpath("x", ""))
            """, };

    /* ==================== preload 搜索器 ==================== */

    private static final String[] PRELOAD = {
            """
            package.preload["fkpk_p1"] = function(name) return {v = 1, n = name} end
            local m, ld = require("fkpk_p1")
            print(m.v, m.n, ld)
            print(package.loaded["fkpk_p1"] == m)
            -- 第二次走缓存：模块体不再跑
            package.preload["fkpk_p1"] = function() return {v = 2} end
            print(require("fkpk_p1").v)
            """,

            """
            -- 模块返回 nil：_LOADED[name] 变成 true
            package.preload["fkpk_p2"] = function() return nil end
            print(require("fkpk_p2"))
            print(package.loaded["fkpk_p2"])
            -- 模块什么都不返回也是 true
            package.preload["fkpk_p3"] = function() end
            print(require("fkpk_p3"))
            print(package.loaded["fkpk_p3"])
            """,

            """
            -- 模块返回 false / 0 也算「设了值」（只有 nil 不算）
            package.preload["fkpk_p4"] = function() return false end
            local m = require("fkpk_p4")
            print(m)
            print(package.loaded["fkpk_p4"])
            print(require("fkpk_p4"))
            """,

            """
            -- 模块名里带点也走 preload（Croot 搜索器只在 cpath 命中时才插话）
            package.preload["fkpk.p5"] = function(n) return {n = n} end
            package.path = "/tmp/?.lua"
            package.cpath = "/tmp/?.so"
            local m, ld = require("fkpk.p5")
            print(m.n, ld)
            print(package.loaded["fkpk.p5"] == m)
            """, };

    /* ==================== Lua 搜索器 ==================== */

    private static final String[] LUAMOD = {
            """
            -- 从文件加载；require 的第二个返回值是文件名
            local p = "/tmp/fkpk_l1.lua"
            local f = io.open(p, "w")
            f:write("local M = {v = 42}\\nreturn M\\n")
            f:close()
            package.path = "/tmp/?.lua"
            local m, ld = require("fkpk_l1")
            print(m.v, ld)
            print(package.loaded["fkpk_l1"] == m)
            os.remove(p)
            """,

            """
            -- 子模块：a.b 会去找 a/b.lua
            local p = "/tmp/fkpk_l2.lua"
            local f = io.open(p, "w")
            f:write("return {deep = true}\\n")
            f:close()
            package.path = "/tmp/?.lua"
            local m = require("fkpk_l2")
            print(m.deep)
            os.remove(p)
            """,

            """
            -- 模块里的错误带的是文件的 chunk 名（@ + 路径），不是调用点
            local p = "/tmp/fkpk_l3.lua"
            local f = io.open(p, "w")
            f:write("error('boom')\\n")
            f:close()
            package.path = "/tmp/?.lua"
            print(pcall(require, "fkpk_l3"))
            os.remove(p)
            """,

            """
            -- 模块有语法错误：searcher_Lua 报 error loading module
            local p = "/tmp/fkpk_l4.lua"
            local f = io.open(p, "w")
            f:write("this is not lua\\n")
            f:close()
            package.path = "/tmp/?.lua"
            print(pcall(require, "fkpk_l4"))
            os.remove(p)
            """,

            """
            -- package.path 不是字符串
            package.path = 42
            print(pcall(require, "fkpk_l5"))
            package.path = true
            print(pcall(require, "fkpk_l5"))
            """,

            """
            -- 标准库都在 _LOADED 里，require 它们直接命中缓存
            local a = require("table")
            local b = require("table")
            print(a == table, b == table)
            print(require("string") == string)
            print(require("math") == math)
            print(require("io") == io)
            print(require("os") == os)
            print(require("utf8") == utf8)
            print(require("coroutine") == coroutine)
            """, };

    /* ==================== 报错 ==================== */

    private static final String[] ERRORS = {
            """
            -- 找不到：四个搜索器的消息依次拼起来
            package.path = "/tmp/?.lua"
            package.cpath = "/tmp/?.so"
            print(pcall(require, "no_such_module_zzz"))
            """,

            """
            -- 带点的名字会多出 Croot 那一条
            package.path = "/tmp/?.lua"
            package.cpath = "/tmp/?.so"
            print(pcall(require, "no.such.module"))
            """,

            """
            -- searchers 被弄坏
            print(pcall(require))
            print(pcall(require, 42))
            package.searchers = 42
            print(pcall(require, "fkpk_e3"))
            package.searchers = {}
            print(pcall(require, "fkpk_e3"))
            """,

            """
            -- searchers 里放一个返回非函数非字符串的东西：那个搜索器被跳过，
            -- 消息里也不会多出一行
            package.path = "/tmp/?.lua"
            package.cpath = "/tmp/?.so"
            package.searchers = {
              function(n) return 42 end,
              function(n) return nil, "extra" end,
            }
            print(pcall(require, "fkpk_e4"))
            """,

            """
            -- 搜索器自己报错会直接冒出去（require 不用 pcall 包它）
            package.searchers = { function(n) error("searcher died") end }
            print(pcall(require, "fkpk_e5"))
            """, };

    /**
     * {@code require} / {@code package.searchpath} 的<b>深矩阵</b>（57 行）：
     * 真在磁盘上造一批模块，然后走完整条搜索器链 —— 而不只是在内存里摆
     * {@code package.preload}。之前这一块只覆盖了「纯 Lua 那条路」的一半。
     * <p>
     * 覆盖到的形状：模块返回 nil / false / true / 数字 / 无返回 / 多值（只取第一）、
     * 模块抛错、模块语法错、模块里自己设 {@code package.loaded}、
     * 点号模块名换目录分隔符、重复 {@code require} 的缓存、循环 {@code require}
     * （参考给 {@code C stack overflow}，不是哨兵）、
     * {@code package.loaded} 预置成值 / false / nil 的三种后果、
     * {@code package.preload} 给函数 / 返回 nil / 抛错 / 非函数、
     * {@code searchpath} 的 {@code sep} / {@code rep} 与缺参、
     * {@code package.searchers} 的个数与逐个调用、整条链被换掉。
     * <p>
     * 顺带钉住一件容易悄悄坏掉的事：<b>{@code package.path} 的默认值</b>必须与参考
     * 二进制编译时那套 {@code LUA_PATH_DEFAULT} 一致 —— 矩阵里每条
     * {@code module 'X' not found} 都把 {@code /usr/local/share/lua/5.4/...} 那
     * 一串<b>完整打印</b>出来，改一个字符就红。
     * <p>
     * 敏感性量过：把 {@code searchpath} 的默认 {@code sep} 从 {@code "."} 改成
     * {@code "-"}，立刻红在 {@code sp_dots}。
     * <p>
     * 库目录用 {@code /tmp/fkreq} 并在开头 {@code rm -rf} 重建：两边跑在同一台
     * 机器上，绝对路径对双方都可见，也不依赖 cwd。
     */
    private static final String[] DEEP = {"""
        -- require / package.searchpath 深矩阵：真在磁盘上造模块，走完整条搜索器链
        local out = {}
        local function d(v)
          local t = type(v)
          if t == "nil" then return "nil" end
          if t == "boolean" then return tostring(v) end
          if t == "number" then return math.type(v) .. ":" .. tostring(v) end
          if t == "string" then return string.format("q(%d)[%s]", #v, (v:gsub("\\n", "\\\\n"))) end
          return t
        end
        local function probe(name, f, ...)
          local r = table.pack(pcall(f, ...))
          local s = name .. " [" .. r.n .. "]"
          for i = 2, r.n do s = s .. " " .. d(r[i]) end
          if not r[1] then s = s .. " ERR " .. d(r[2]) end
          out[#out + 1] = s
        end
        local function say(s) out[#out + 1] = s end

        local D = "/tmp/fkreq"
        local R = D .. "/repo"
        os.execute("rm -rf " .. D .. " && mkdir -p " .. R .. "/sub")
        local function put(path, body)
          local f = assert(io.open(path, "w"))
          f:write(body)
          f:close()
        end

        put(R .. "/basic.lua", "return 'BASIC'\\n")
        put(R .. "/nonil.lua", "-- 什么都不返回\\n")
        put(R .. "/retnil.lua", "return nil\\n")
        put(R .. "/retfalse.lua", "return false\\n")
        put(R .. "/rettrue.lua", "return true\\n")
        put(R .. "/retnum.lua", "return 42\\n")
        put(R .. "/retmulti.lua", "return 1, 2, 3\\n")
        put(R .. "/err.lua", "error('boom')\\n")
        put(R .. "/syntax.lua", "this is not lua\\n")
        put(R .. "/prints.lua", "print('SIDE'); return 'P'\\n")
        put(R .. "/setsloaded.lua", "package.loaded['setsloaded'] = 'PRESET'; return 'FROMCHUNK'\\n")
        put(R .. "/sub/inner.lua", "return 'INNER'\\n")
        put(R .. "/counter.lua", "local n = (package.loaded['counter_n'] or 0) + 1\\n"
                              .. "package.loaded['counter_n'] = n\\nreturn n\\n")
        put(R .. "/circ.lua", "return require('circ')\\n")

        -- 把仓库路径前置到 package.path，避免依赖 cwd
        local oldpath = package.path
        package.path = R .. "/?.lua;" .. R .. "/?/init.lua;" .. oldpath

        say("path_head " .. tostring(package.path:sub(1, #R + 6)))

        probe("r_basic", require, "basic")
        probe("r_basic_twice", require, "basic")       -- 缓存：第二次不再跑
        probe("r_prints", require, "prints")           -- 副作用只发生一次
        probe("r_prints2", require, "prints")
        probe("r_nonil", require, "nonil")             -- 无返回 → true
        probe("r_retnil", require, "retnil")           -- 返回 nil → true
        probe("r_retfalse", require, "retfalse")       -- 返回 false → false
        probe("r_rettrue", require, "rettrue")
        probe("r_retnum", require, "retnum")
        probe("r_retmulti", require, "retmulti")       -- 只取第一个
        probe("r_err", require, "err")
        probe("r_syntax", require, "syntax")
        probe("r_setsloaded", require, "setsloaded")   -- chunk 里先设了 loaded
        probe("r_setsloaded2", require, "setsloaded")
        probe("r_sub", require, "sub.inner")           -- 点号换成目录分隔符
        probe("r_counter1", require, "counter")
        probe("r_counter2", require, "counter")
        probe("r_missing", require, "no_such_mod_xyz")
        probe("r_missing_dots", require, "a.b.c.nope")
        probe("r_circ", require, "circ")               -- 循环 require 给的是哨兵
        probe("r_noname", require)
        probe("r_nilname", require, nil)
        probe("r_numname", require, 42)
        probe("r_emptyname", require, "")
        probe("r_boolname", require, true)

        -- package.loaded 预置
        package.loaded["preset"] = "PRE"
        probe("l_preset", require, "preset")
        package.loaded["prefalse"] = false
        probe("l_prefalse", require, "prefalse")
        package.loaded["prenil"] = nil
        probe("l_prenil", require, "prenil")

        -- package.preload
        package.preload["frompreload"] = function() return "FROM_PRELOAD" end
        probe("p_preload", require, "frompreload")
        package.preload["preloadnil"] = function() return nil end
        probe("p_preloadnil", require, "preloadnil")
        package.preload["preloaderr"] = function() error("pe") end
        probe("p_preloaderr", require, "preloaderr")
        probe("p_preload_extra", function()
          package.preload["multi"] = function(n) return "N=" .. tostring(n) end
          return require("multi")
        end)
        probe("p_preload_badtype", function()
          package.preload["notfn"] = 42
          return require("notfn")
        end)

        -- searchpath
        probe("sp_basic", package.searchpath, "basic", R .. "/?.lua")
        probe("sp_missing", package.searchpath, "nope_xyz", R .. "/?.lua")
        probe("sp_multi", package.searchpath, "nope_xyz", R .. "/?.lua;" .. R .. "/?/init.lua")
        probe("sp_dots", package.searchpath, "sub.inner", R .. "/?.lua")
        probe("sp_sep", package.searchpath, "sub.inner", R .. "/?.lua", ".", "/")
        probe("sp_rep", package.searchpath, "subXinner", R .. "/?.lua", "X", "/")
        probe("sp_rep2", package.searchpath, "sub--inner", R .. "/?.lua", "--", "/")
        probe("sp_emptysep", package.searchpath, "basic", R .. "/?.lua", "", "")
        probe("sp_nopath", package.searchpath, "basic", "")
        probe("sp_nosep", package.searchpath, "basic", R .. "/?.lua")
        probe("sp_badname", package.searchpath, 42, R .. "/?.lua")
        probe("sp_badpath", package.searchpath, "basic", 42)
        probe("sp_noargs", package.searchpath)
        probe("sp_abs", package.searchpath, "basic", R .. "/?.lua;" .. R .. "/?.lua")

        -- searchers 链本身
        probe("s_count", function() return #package.searchers end)
        probe("s_types", function()
          local r = {}
          for i, v in ipairs(package.searchers) do r[#r + 1] = type(v) end
          return table.concat(r, ",")
        end)
        probe("s_call1", function() return package.searchers[1]("basic") end)
        probe("s_call4", function() return package.searchers[4]("basic") end)
        probe("s_call_missing1", function() return package.searchers[1]("nope_xyz_1") end)
        probe("s_call_bad", function() return package.searchers[1](42) end)
        probe("s_replace", function()
          local old = package.searchers
          package.searchers = { function(n) return function() return "REPLACED" end end }
          local v = require("anything_xyz")
          package.searchers = old
          return v
        end)
        probe("s_empty", function()
          local old = package.searchers
          package.searchers = {}
          local ok, e = pcall(require, "anything_xyz")
          package.searchers = old
          return ok, e
        end)

        package.path = oldpath
        os.execute("rm -rf " .. D)
        print(table.concat(out, "\\n"))
            """ };


    /**
     * C 搜索器那条路（23 行）—— 也是照着未覆盖行点名打的。
     * <p>
     * 本实现没有 {@code dlopen}，{@code loadfunc} 一律失败，但<b>「失败成什么样」
     * 是有形状的</b>：{@code searcher_C} 找到文件 → {@code loadfunc} 失败 →
     * {@code checkLoad} 装裱出 {@code error loading module 'X' from file 'Y'}；
     * {@code searcher_Croot} 还会先把模块名切成根名（{@code a.b} → {@code a}）；
     * {@code package.loadlib} 的三元组第三个值是 {@code "open"}（不是
     * {@code "absent"} —— 那是 {@code ERRLIB} 才给的）。
     * 这些分支<b>从来没人走过</b>，因为默认 {@code cpath} 下永远找不到真文件；
     * 把 {@code package.cpath} 指到一个真存在的文件上就走到了。
     * <p>
     * 一处不比：<b>{@code dlopen} 的失败文本是平台自己的</b>（macOS 是
     * {@code dlopen(...): tried: '...' (slice is not valid mach-o file)}，
     * 本实现给 {@code dynamic libraries not enabled; check your Lua installation}）。
     * 两边是同一件事、只是措辞不同，归一成 {@code <NODL>} 再比，其余照比。
     * <p>
     * 顺带这一片还咬出了「目录当文件用」那处 bug（{@code m_dir}）：
     * {@code require} 一个名字撞上目录的模块时，参考报
     * {@code error loading module ... cannot read ...: Is a directory}，
     * 我们报 {@code module not found}。修法见
     * {@code RuntimeTest#directoryAsFileMatchesReference}。
     */
    private static final String[] CFALLBACK = {"""
        -- 照着 PackageLib 未覆盖行点名打：C 搜索器那条路（本实现没有 dlopen，
        -- 一律失败，但「失败成什么样」是有形状的）、loadlib、searchpath 的模板边界
        local out = {}
        local function d(v)
          local t = type(v)
          if t == "nil" then return "nil" end
          if t == "boolean" then return tostring(v) end
          if t == "number" then return math.type(v) .. ":" .. tostring(v) end
          if t == "string" then return string.format("q(%d)[%s]", #v, (v:gsub("\\n", "\\\\n"))) end
          return t
        end
        -- C 搜索器 / loadlib 的失败文本是平台自己的：macOS 是
        -- "dlopen(...): tried: '...' (slice is not valid mach-o file)"，glibc 是
        -- "...: cannot open shared object file" / "...: file too short" /
        -- "undefined symbol: ..."，本实现没有 dlopen，给
        -- "dynamic libraries not enabled..."，而 require 那层会把它们再包成
        -- "error loading module 'x' from file 'y': ..."。两边都是「没有动态库」
        -- 这一件事，只是措辞不同 —— 归一成 <NODL> 再比，其余部分照比。
        local NODL_PATS = { "dlopen", "dynamic libraries not enabled",
                            "cannot open shared object", "file too short",
                            "undefined symbol", "error loading module" }
        local function nodl(v)
          if type(v) ~= "string" then return v end
          for i = 1, #NODL_PATS do
            if v:find(NODL_PATS[i], 1, true) then return "<NODL>" end
          end
          return v
        end
        local function d0(v)
          return d(nodl(v))
        end
        local function probe(name, f, ...)
          local r = table.pack(pcall(f, ...))
          local s = name .. " [" .. r.n .. "]"
          for i = 2, r.n do s = s .. " " .. d0(r[i]) end
          if not r[1] then s = s .. " ERR " .. d0(r[2]) end
          out[#out + 1] = s
        end

        local D = "/tmp/fkreq2"
        os.execute("rm -rf " .. D .. " && mkdir -p " .. D)
        local function put(p, body)
          local f = assert(io.open(p, "w")); f:write(body or "x"); f:close()
        end
        -- 这些文件真的存在：C 搜索器于是能「找到」它们，才有后面的 loadfunc
        put(D .. "/basic.so")
        put(D .. "/a.so")
        put(D .. "/bad.lua", "syntax error here\\n")

        -- 1. cpath 指到真实存在的文件上：searcher_C 找得到 → loadfunc → 无 dlopen → 失败
        probe("c_hit", function()
          local old = package.cpath
          package.cpath = D .. "/?.so"
          local ok, e = pcall(require, "basic")
          package.cpath = old
          return ok, e
        end)
        probe("c_hit_dotted", function()
          local old = package.cpath
          package.cpath = D .. "/?.so"
          local ok, e = pcall(require, "a.b")     -- searcher_Croot：先切出根名 "a"
          package.cpath = old
          return ok, e
        end)
        probe("c_searcher_direct", function()
          local old = package.cpath
          package.cpath = D .. "/?.so"
          local r = table.pack(pcall(package.searchers[3], "basic"))
          package.cpath = old
          local s = ""
          for i = 2, r.n do s = s .. " " .. d0(r[i]) end
          return "n=" .. r.n .. s
        end)
        probe("c_searcher4_direct", function()
          local old = package.cpath
          package.cpath = D .. "/?.so"
          local r = table.pack(pcall(package.searchers[4], "a.b"))
          package.cpath = old
          local s = ""
          for i = 2, r.n do s = s .. " " .. d0(r[i]) end
          return "n=" .. r.n .. s
        end)
        probe("c_searcher4_root", function()
          local old = package.cpath
          package.cpath = D .. "/?.so"
          local r = table.pack(pcall(package.searchers[4], "a"))  -- 没有点号 → 直接返回 0
          package.cpath = old
          return "n=" .. r.n
        end)

        -- 2. package.loadlib：永远失败，但三元组有形状（第三个是 "absent"）
        probe("ll_basic", package.loadlib, "/tmp/nope.so", "luaopen_x")
        probe("ll_exists", package.loadlib, D .. "/basic.so", "luaopen_basic")
        probe("ll_noargs", package.loadlib)
        probe("ll_onearg", package.loadlib, "/tmp/nope.so")
        probe("ll_nil", package.loadlib, nil, "x")
        probe("ll_num", package.loadlib, 42, "x")
        -- ll_empty 是唯一要单独走的：三元组第三个值（「在哪一步失败」）落在
        -- ERRLIB 上时给 LIB_FAIL（POSIX 有 dlopen 是 "open"，没有的是
        -- "absent"），落在 ERRFUNC 上时给 "init"。而<b>空路径</b>是分水岭：
        -- glibc 的 dlopen("") 是「打开主程序」—— 它<b>成功</b>，于是往下走到
        -- dlsym("") 失败，给 "init"；macOS 的 dlopen("") 直接失败，给 "open"；
        -- 本实现没有 dlopen，也给 "open"。三个答案分属三个 loader，不是 Lua
        -- 的语义差 —— 只比「这个三元组在不在、第三个是不是字符串」。
        -- 其余 ll_* 两边都停在 ERRLIB，"open" 照比（它同时钉住 LIB_FAIL 不是
        -- "absent"）。
        local function probe_llnowhere(name, ...)
          local r = table.pack(pcall(package.loadlib, ...))
          local s = name .. " [" .. r.n .. "]"
          for i = 2, r.n do
            local v = r[i]
            if i == 4 and (v == "open" or v == "init" or v == "absent") then
              v = "<WHERE>"
            end
            s = s .. " " .. d0(v)
          end
          if not r[1] then s = s .. " ERR " .. d0(r[2]) end
          out[#out + 1] = s
        end
        probe_llnowhere("ll_empty", "", "")

        -- 3. searchpath 的模板边界：以分隔符结尾、只有分隔符、连续分隔符
        local P = D .. "/hit.lua"
        put(P)
        probe("sp_trailsep", package.searchpath, "hit", D .. "/?.lua;")
        probe("sp_onlysep", package.searchpath, "hit", ";")
        probe("sp_2sep", package.searchpath, "hit", ";;")
        probe("sp_leadsep", package.searchpath, "hit", ";" .. D .. "/?.lua")
        probe("sp_midsep", package.searchpath, "hit", ";" .. D .. "/?.lua;")
        probe("sp_noq", package.searchpath, "hit", D .. "/hit.lua")
        probe("sp_twoq", package.searchpath, "hit", D .. "/?.lua;" .. D .. "/?.lua")

        -- 4. 模块文件本身加载失败：语法错 / 是目录
        probe("m_syntax", function()
          local old = package.path
          package.path = D .. "/?.lua"
          local ok, e = pcall(require, "bad")
          package.path = old
          return ok, e
        end)
        probe("m_dir", function()
          local old = package.path
          package.path = D .. "/?.lua"
          os.execute("mkdir -p " .. D .. "/adir.lua")
          local ok, e = pcall(require, "adir")
          package.path = old
          return ok, e
        end)
        probe("m_loadfile", function()
          local ok, e = pcall(loadfile, D .. "/bad.lua")
          return ok, e
        end)
        probe("m_loadfile_missing", function()
          return loadfile(D .. "/nope_xyz.lua")
        end)

        os.execute("rm -rf " .. D)
        print(table.concat(out, "\\n"))
            """ };

    /**
     * <b>{@code require} 语义矩阵</b>（135 行）。
     * <p>
     * 判据全在 {@code loadlib.c: ll_require}，四句就说完：
     * <pre>
     * _LOADED[name] 为真 → 直接用（<b>只返回 1 个值</b>，没有 loader data）；
     * 否则跑 loader，返回值非 nil 就写进 _LOADED；
     * 跑完 _LOADED[name] 还是 nil 就用 {@code true} 顶上；
     * 正常路径返回<b>两个</b>值：模块 + loader data。
     * </pre>
     * 九组：模块返回值的十种形状（含「什么都不返回」与「返回多个值」）
     * 及其<b>第二次</b> {@code require}（走缓存，只给一个值）；模块自己往
     * {@code package.loaded} 里写（写 {@code false} 会让它<b>下次重载</b>）；
     * 外部预置 {@code package.loaded} 的三种值；模块报错（报错之后<b>没</b>写进
     * 缓存，再 require 会再跑一遍）；{@code package.preload[name]} 不是函数；
     * 名字带点的子模块；{@code require} 的参数错；{@code package.searchers}
     * 全删 / 换成自定义 / 返回非函数 / 返回 {@code nil + 消息}；真去
     * {@code target/rt/req/} 下找文件的 Lua 搜索器；{@code package.searchpath}
     * 与 {@code package.config} / {@code loadlib}。
     * <p>
     * 两处<b>不比内容</b>：{@code package.loadlib} 的失败消息（约定 #54/#57：
     * 没有 dlopen，只比「是不是字符串」），以及未捕获错误的
     * {@code <程序名>: } 前缀（那是 {@code lua.c: report} 的行为，不是库的
     * 语义 —— 所以 {@code W2} 必须包在 {@code pcall} 里）。
     * <p>
     * 一条分歧都没咬出来。留作回归网：{@code ll_require} 那四句的<b>顺序</b>一乱，
     * 这里立刻红。
     */
    @Test
    void requireSemanticsMatrixMatchesReference() {
        Diff.assertMatches("""
-- require / package 语义矩阵。
-- 判据全在 lbaselib... loadlib.c: ll_require：
--   _LOADED[name] 为真就直接用；否则跑 loader，返回值非 nil 就写进 _LOADED；
--   跑完还是 nil 就用 true 顶上。返回值是<b>两个</b>：模块 + loader data。
-- 注意 5.4 没有「哨兵」，所以循环 require 是<b>真递归</b>（不是报循环依赖）。
local function sh(v)
  local t = type(v)
  if t == "string" then return "s[" .. v .. "]" end
  return tostring(v)
end
local function pr(tag, ...)
  local n = select("#", ...)
  local out = { tag }
  for i = 1, n do out[#out + 1] = sh((select(i, ...))) end
  print(table.concat(out, " | "))
end
local D = "target/rt/req/"   -- 临时目录，两边同一个 cwd

-- 1) 模块返回值的各种形状
local shapes = {
  { "nil",   "return nil" },
  { "none",  "" },
  { "false", "return false" },
  { "true",  "return true" },
  { "zero",  "return 0" },
  { "empty", "return ''" },
  { "tbl",   "return {v=1}" },
  { "multi", "return 1, 2, 3" },
  { "fn",    "return function() end" },
  { "self",  "return package.loaded['rq_1']" },
}
for i = 1, #shapes do
  local tag, body = shapes[i][1], shapes[i][2]
  package.preload["rq_" .. tag] = load(body, "=rq_" .. tag)
  local m, ld = require("rq_" .. tag)
  pr("S" .. i, tag, type(m), sh(m), sh(ld), type(package.loaded["rq_" .. tag]))
  -- 第二次：走缓存
  local m2, ld2 = require("rq_" .. tag)
  pr("S" .. i .. "b", type(m2), sh(m2), sh(ld2))
end

-- 2) 模块自己往 package.loaded 里写
do
  package.preload["rq_self"] = function(name)
    package.loaded["rq_self"] = { written = name }
    return nil
  end
  local m, ld = require("rq_self")
  pr("W1", type(m), sh(m), sh(ld), type(package.loaded["rq_self"]))
  -- 必须包 pcall：未捕获的错误由 lua.c: report 打 "<程序名>: " 前缀，那是
  -- 解释器（不是库）的行为，比对的是库语义。
  pr("W2", pcall(function() return require("rq_self").written end))
end
do
  -- 写了 false：require 下次见到假值，会<b>重新加载</b>
  package.preload["rq_false"] = function()
    package.loaded["rq_false"] = false
    return { n = 1 }
  end
  local a = require("rq_false").n
  local b = require("rq_false").n
  pr("W3", a, b, package.loaded["rq_false"])
end
do
  -- 外部预置 package.loaded
  package.loaded["rq_pre"] = { pre = true }
  pr("W4", require("rq_pre").pre)
  package.loaded["rq_pre2"] = false
  package.preload["rq_pre2"] = function() return { reloaded = 1 } end
  pr("W5", type(require("rq_pre2")))
  package.loaded["rq_pre3"] = nil
  package.preload["rq_pre3"] = function() return { fresh = 1 } end
  pr("W6", type(require("rq_pre3")))
end

-- 3) 模块里报错 / 非函数 loader / loader 返回 nil
do
  package.preload["rq_err"] = function() error("mod-boom") end
  pr("E1", pcall(require, "rq_err"))
  pr("E2", package.loaded["rq_err"])
  pr("E3", pcall(require, "rq_err"))  -- 报错之后没写进缓存，会再跑一次
end
do
  package.preload["rq_notfn"] = 5
  pr("E4", pcall(require, "rq_notfn"))
end
do
  -- 名字带点的子模块
  package.preload["rq.sub"] = function(n) return { n = n } end
  pr("E5", require("rq.sub").n)
end

-- 4) require 的参数错
pr("P1", pcall(require))
pr("P2", pcall(require, nil))
pr("P3", pcall(require, 5))
pr("P4", pcall(require, true))
pr("P5", pcall(require, {}))
pr("P6", pcall(require, "x", "y"))
pr("P7", pcall(require, "a\\0b"))
pr("P8", pcall(require, "no_such_module_zzz_qqq"))

-- 5) searchers：全删 / 插到最前 / 返回非函数
do
  local saved = package.searchers
  package.searchers = {}
  pr("H1", pcall(require, "no_such_zzz"))
  package.searchers = { function(n) return load("return {from=1}") end }
  pr("H2", require("any_name_zz").from)
  package.searchers = { function(n) return 5 end }
  pr("H3", pcall(require, "any_name_zz2"))
  package.searchers = { function(n) return nil, "no way" end }
  pr("H4", pcall(require, "any_name_zz3"))
  package.searchers = saved
end

-- 6) 文件搜索器（Lua 搜索器）：真去 target/rt/req/ 下找
do
  local dir = D
  os.execute("mkdir -p " .. dir)
  local function put(name, body)
    local f = assert(io.open(dir .. name .. ".lua", "w"))
    f:write(body)
    f:close()
  end
  put("fm1", "return {v=11}")
  put("fm2", "local t = {...} return {n=#t, a=t[1]}")
  put("fm3", "error('file-boom')")
  put("fm4", "return nil")
  local oldpath = package.path
  package.path = dir .. "?.lua;" .. oldpath
  pr("F1", require("fm1").v)
  local m2, ld2 = require("fm1")
  pr("F2", m2.v, type(ld2))
  pr("F3", require("fm2").n, require("fm2").a)
  pr("F4", pcall(require, "fm3"))
  pr("F5", type(require("fm4")))
  pr("F6", pcall(require, "no_such_file_zzz"))
  package.path = oldpath
end

-- 7) package.searchpath
do
  local dir = D
  pr("Q1", package.searchpath("fm1", dir .. "?.lua"))
  pr("Q2", pcall(package.searchpath, "zzz", dir .. "?.lua"))
  pr("Q3", pcall(package.searchpath, "fm1", dir .. "?.lua", ".", "-"))
  pr("Q4", pcall(package.searchpath))
  pr("Q5", pcall(package.searchpath, "a"))
  pr("Q6", pcall(package.searchpath, "a", 5))
  pr("Q7", pcall(package.searchpath, 5, "a"))
  pr("Q8", pcall(package.searchpath, "a", "b", 5))
  pr("Q9", pcall(package.searchpath, "a", "b", ".", 5))
  pr("Q10", package.searchpath("fm1", dir .. "?.lua;" .. dir .. "?/init.lua"))
end

-- 8) package.config / cpath / loadlib
pr("C1", (package.config:gsub("\\n", "<N>")))
pr("C2", type(package.cpath))
pr("C3", select("#", package.loadlib("no_such_lib_zzz", "luaopen_x")))
pr("C4", type(select(1, package.loadlib("no_such_lib_zzz", "luaopen_x"))))
-- 约定 #54/#57：loadlib 的失败消息是「在哪一步失败」，dlopen 的文本随平台变，
-- 本实现没有 dlopen —— 只比「是不是字符串」。
pr("C5", type(select(2, package.loadlib("no_such_lib_zzz", "luaopen_x"))))
pr("C6", pcall(package.loadlib))
pr("C7", pcall(package.loadlib, "a"))
do
  -- loadlib 的第二个参数会被 luaL_checkstring 转成字符串（5 → "5"）
  local ok, a, b, c = pcall(package.loadlib, "a", 5)
  pr("C8", ok, type(a), type(b), type(c))
end
pr("C9", type(package.preload), type(package.loaded), type(package.searchers))
pr("C10", #package.searchers)

-- 9) 循环 require：5.4 没有哨兵，是真递归（栈溢出由 pcall 兜住）
do
  local n = 0
  package.preload["cyc_a"] = function()
    n = n + 1
    if n > 200 then error("deep-stop") end
    return require("cyc_b")
  end
  package.preload["cyc_b"] = function() return require("cyc_a") end
  local ok, e = pcall(require, "cyc_a")
  pr("X1", ok, type(e))
  pr("X2", n > 1)
end

print("END")

                """);
    }
}
