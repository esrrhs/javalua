package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 二进制 chunk（预编译字节码）的双向兼容 —— 阶段 11。
 * <p>
 * 为什么单独开一类：Lua 的加载器<b>靠文件头认 chunk</b>，不需要调用方声明。
 * {@code ldo.c: f_parser} 读一个字节，是 {@code LUA_SIGNATURE} 的首字节就走
 * {@code luaU_undump}，否则才进 {@code luaY_parser}。所以
 * {@code load} / {@code loadfile} / {@code dofile} / {@code require}
 * 全都天生支持预编译 chunk——而这正是真实项目的常见分发形态
 * （{@code luac -o mod.luac} 之后直接 {@code require "mod"}）。
 * <p>
 * 本实现原先只在 {@code BaseLib} 的 {@code load} / {@code loadfile} 里做了这个
 * 分派，{@code lua_load} 本身（{@link LuaState#load}）不看签名，于是
 * {@code require} 一个预编译模块会报
 * {@code unexpected symbol near '<\27>'}——把签名当源码读了。修法不是给
 * {@code require} 打补丁，而是把分派补回 {@code lua_load} 那一层。
 * <p>
 * 两个方向都要验：
 * <ul>
 *   <li><b>参考实现编出来的 chunk，本实现要能跑</b>（需要 {@code luac}）。
 *       这是硬闸门：自己编给自己判卷等于没判，必须用参考实现自己的编译器。</li>
 *   <li><b>本实现 dump 出来的 chunk，加载与执行也要一致</b>
 *       （不需要 {@code luac}，用 {@code string.dump} 造 chunk）。</li>
 * </ul>
 */
class BinaryChunkTest {

    /** 固定路径：{@code luaO_chunkid} 会把超过 {@code LUA_IDSIZE} 的源名截断 */
    private static final File DIR = new File("target/rt/chunk");

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.find() != null,
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    @TestFactory
    List<DynamicTest> precompiledChunksMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SOURCES.length; i++) {
            String src = SOURCES[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("luac[" + n + "]", () -> {
                assertTrue(DIR.isDirectory() || DIR.mkdirs(), "建不出 " + DIR);
                String name = "c" + n + ".luac";
                File out = ReferenceLua.compile(src.getBytes(StandardCharsets.UTF_8),
                        new File(DIR, name).getPath());
                assumeTrue(out != null, "没有 luac5.4，跳过；设 LUAC54_BIN 或从源码构建");
                /*
                 * 比的是「同一个 chunk 文件在两边的输出」——不是「本实现编出来的
                 * chunk 和参考实现编出来的 chunk 一不一样」（那是 DumpTest 的事）。
                 * 这里同时压了 undump 与 VM：chunk 里的调试信息（行号、局部变量名）
                 * 也要原样活下来，SOURCES 里那几条会报错的用例就是盯这个的。
                 */
                Diff.assertFileMatches(DIR, name);
            }));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> loadingOurOwnChunks() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < OURS.length; i++) {
            String src = OURS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("ours[" + n + "]", () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    /**
     * 交给 {@code luac} 的语料。挑的都是「编译产物里信息量大的」：
     * upvalue、变长参数、跳转与闭包捕获、混合表构造、行号与局部变量名。
     */
    private static final String[] SOURCES = {

            """
            local up = "upvalue"
            local function f(a)
              local function g(b) return a + b + #up end
              return g
            end
            print(f(1)(2), up)
            """,

            """
            local function va(...)
              local n = select('#', ...)
              return n, ...
            end
            print(va(1, nil, 3))
            print(va())
            print((function(...) return ... end)("x", "y"))
            """,

            """
            for i = 1, 3 do
              if i == 2 then goto continue end
              print("i", i)
              ::continue::
            end
            local j = 0
            while j < 2 do j = j + 1 end
            print(j)
            """,

            """
            local t = {1, 2, 3, x = "X", [10] = "ten", "last"}
            print(#t, t.x, t[10], t[4])
            local mt = setmetatable({}, {__index = function(_, k) return k * 2 end})
            print(mt[21])
            print(("abc"):upper() .. 1 .. 2.5 .. true)
            """,

            """
            local max, min = math.maxinteger, math.mininteger
            print(max, min, max + 1 == min, 1 / 0, -1 / 0, 0 / 0 ~= 0 / 0)
            print(0x7fffffffffffffff, 9007199254740993, 1e300 * 1e300)
            """,

            /*
             * 行号与局部变量名：预编译 chunk 里带的是行号表与调试名，
             * 跑出来的错误消息必须和源码版一模一样，否则就是 undump 丢了信息。
             */
            """
            local function boom(x)
              local inner = x
              error("boom " .. inner)
            end
            print(pcall(boom, "here"))
            print(pcall(function() local a = nil; return a.b end))
            print(pcall(function() error({code = 7}) end))
            """,

            """
            local co = coroutine.create(function(a)
              for i = 1, 3 do
                coroutine.yield(a + i)
              end
              return "done"
            end)
            print(coroutine.resume(co, 10))
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            print(coroutine.resume(co))
            """,

            /*
             * 常量表过 256、局部变量过 200：这两条会顶到 dump 里变长整数的
             * 多字节编码与 OP_NEWTABLE 的尺寸字段。
             */
            """
            local t = {}
            for i = 1, 300 do t[i] = i * i end
            print(#t, t[300])
            local a1, a2 = 1, 2
            local s = 0
            for i = 1, 300 do s = s + (i % 7) end
            print(s, a1 + a2)
            """,
    };

    /**
     * 不需要 {@code luac} 的一组：chunk 由 {@code string.dump} 当场造出来，
     * 写进文件，再走加载器的各条入口。
     */
    private static final String[] OURS = {

            /*
             * 这一条就是修的那个缺陷：require 走的是 LuaState#loadFile，
             * 原先不看签名，报 unexpected symbol near '<\27>'。
             */
            """
            local p = "target/rt/chunk/m1.lua"
            local f = io.open(p, "wb")
            f:write(string.dump(function() return 1, 2, "three" end))
            f:close()
            package.path = "target/rt/chunk/?.lua;" .. package.path
            print(require("m1"))
            print(require("m1"))
            print(package.loaded.m1)
            """,

            """
            local p = "target/rt/chunk/m2.lua"
            local f = io.open(p, "wb")
            f:write(string.dump(function(a, b) return a * b end))
            f:close()
            print(dofile(p))
            print(dofile(p))
            local g, e = loadfile(p)
            print(type(g), e)
            print(g(6, 7))
            """,

            /* mode：二进制 chunk 只能按 "b" 加载，按 "t" 是语法期错误 */
            """
            local d = string.dump(function() return "x" end)
            print(load(d, "=bin", "b"))
            print(load(d, "=bin", "t"))
            print(load(d, "=bin", "bt"))
            print(load("return 1", "=txt", "t"))
            print(load("return 1", "=txt", "b"))
            """,

            /* chunk 名：以签名开头时会被换成 "binary string" */
            """
            local d = string.dump(function() return 1 end)
            print(pcall(load, d))
            print(load(string.dump(function() error("inside") end)))
            print(pcall(load(string.dump(function() error("inside") end))))
            """,

            /* 坏 chunk：截断与篡改都必须是「加载期错误」而不是崩 */
            """
            local d = string.dump(function() return 1 end)
            print(pcall(load, d:sub(1, 20)))
            print(pcall(load, "\27Lua"))
            print(pcall(load, d:sub(1, 1) .. string.rep("\0", 40)))
            print(load(d .. "trailing"))
            """,

            /*
             * 畸形 chunk 矩阵：对一个真实 dump 做<b>逐长度截断 + 逐字节变异 +
             * 变长整数上限注入 + 尾部垃圾</b>，共 865 行，把 {@code lundump.c}
             * 的每一条 {@code error()} 都点名到。
             * <p>
             * 为什么要「逐字节」而不是挑几个坏样本：手挑的样本只覆盖想得到的位置，
             * 而变异不需要预先知道哪个字节是干什么的一一它把
             * {@code truncated chunk} / {@code integer overflow} /
             * {@code bad format for constant string} / {@code version mismatch} /
             * {@code format mismatch} / {@code corrupted chunk} /
             * 三种 {@code size mismatch} / 两种 {@code format mismatch}
             * 全都撞出来了，包括两个手挑时不会想到的地方。
             * <p>
             * chunk 名固定成 {@code "=X"}：{@code string.dump} 的产物里带着源名，
             * 而源名默认就是脚本路径，两边路径不同会让 dump 差 7 个字节、
             * 所有偏移跟着错位，矩阵就白扫了。
             */

            """
-- Undump 畸形 chunk 扫描：截断 / 逐字节变异 / 变长整数注入 / 尾部垃圾 / 剥调试信息
-- chunk 名固定为 "=X"，保证 dump 与脚本所在路径无关，两边字节完全一致
local g = load("return function() return 'hi' end", "=X")()
local base = string.dump(g)
print("len " .. #base)

local function try(s, name)
    local f, e = load(s, name)
    if f then
        return "ok"
    end
    return "err " .. tostring(e)
end

-- 1) 逐长度截断
for i = 0, #base do
    print(string.format("trunc %d %s", i, try(base:sub(1, i), "=C")))
end

-- 2) 逐字节变异
local MUT = { 0x00, 0x80, 0xff, 0x41 }
for i = 1, #base do
    for _, m in ipairs(MUT) do
        local s = base:sub(1, i - 1) .. string.char(m) .. base:sub(i + 1)
        print(string.format("mut %d %d %s", i, m, try(s, "=C")))
    end
end

-- 3) chunk 名对错误信息的影响（签名第二个字节 L 改掉 / 版本字节改掉）
local nosig = base:sub(1, 1) .. "X" .. base:sub(3)
print("name nil    " .. try(nosig))
print("name at     " .. try(nosig, "@file.lua"))
print("name eq     " .. try(nosig, "=nm"))
print("name plain  " .. try(nosig, "plain"))
print("name empty  " .. try(nosig, ""))
print("name sig    " .. try(nosig, "\\27zz"))
local badver = base:sub(1, 4) .. string.char(0x63) .. base:sub(6)
print("ver nil     " .. try(badver))
print("ver at      " .. try(badver, "@file.lua"))

-- 4) 变长整数上限：头 31 字节 + 32 字节 upval 数，之后第一个读的就是 size
for n = 1, 12 do
    local s = base:sub(1, 32) .. string.rep("\\127", n)
    print(string.format("ovf %d %s", n, try(s, "=C")))
end
print("ovf2 " .. try(base:sub(1, 32) .. string.rep("\\127", 4) .. "\\255", "=C"))

-- 5) 尾部垃圾：undump 不检查 EOF
print("tail  " .. try(base .. "GARBAGE", "=C"))
print("tail2 " .. try(base .. string.rep("Z", 100), "=C"))

-- 6) 剥掉调试信息后的同一批扫描（布局不同，走的是另一组偏移）
local st = string.dump(g, true)
print("slen " .. #st)
for i = 0, #st do
    print(string.format("strunc %d %s", i, try(st:sub(1, i), "=C")))
end
for i = 1, #st do
    for _, m in ipairs({ 0x00, 0x80 }) do
        local s = st:sub(1, i - 1) .. string.char(m) .. st:sub(i + 1)
        print(string.format("smut %d %d %s", i, m, try(s, "=C")))
    end
end

-- 7) 带 upvalue 与内嵌函数的 chunk（结构更复杂，字段更多）
local h = load([[
  local x = 1
  local function inner(a) return a + x end
  return function(b) return inner(b) * 2 end
]], "=Y")()
local big = string.dump(h)
print("blen " .. #big)
for i = 0, #big do
    print(string.format("btrunc %d %s", i, try(big:sub(1, i), "=C")))
end
for i = 1, #big do
    for _, m in ipairs({ 0x00, 0x80 }) do
        local s = big:sub(1, i - 1) .. string.char(m) .. big:sub(i + 1)
        print(string.format("bmut %d %d %s", i, m, try(s, "=C")))
    end
end
            """,
    };
}
