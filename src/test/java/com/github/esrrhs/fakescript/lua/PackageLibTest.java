package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
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
}
