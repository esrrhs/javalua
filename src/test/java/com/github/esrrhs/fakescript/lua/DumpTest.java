package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;

/**
 * {@code string.dump} 与二进制 chunk 的差分用例 —— 阶段 8。
 * <p>
 * 这里比 {@link LoadTest} 更进一步，把 {@code ldump.c} / {@code lundump.c}
 * 这一对也拉进闸门，而且用<b>两种不同粒度</b>的比法：
 * <ul>
 *   <li><b>运行期</b>：脚本的 stdout 与参考实现逐字节比（{@link Diff}）；</li>
 *   <li><b>产物</b>：{@code string.dump} 出来的<b>字节本身</b>与参考实现逐字节比。</li>
 * </ul>
 * 第二种是这一阶段的硬闸门——它同时校验了编译产物（常量顺序、行号表、
 * 局部变量与 upvalue 的调试信息、{@code maxstacksize}）与序列化格式。
 * 只有第一种的话，「编出来的东西不对、但恰好能跑」是漏得过去的。
 * <p>
 * 盯住的几处：
 * <ul>
 *   <li><b>{@code string.dump} 只吃 Lua 函数</b>：不给参数报
 *       「function expected」，给 C 函数报「unable to dump given function」——
 *       两条消息来自两个不同的检查，不能合并；</li>
 *   <li><b>{@code strip}</b> 剥掉行号与源名，于是错误不再带位置前缀；</li>
 *   <li><b>加载出来的函数，第 0 号 upvalue 会被 {@code load} 换成 {@code _ENV}</b>。
 *       这是 C 版的既有行为，不是 bug：dump 一个带非 {@code _ENV} upvalue 的
 *       函数再 load 回来，那个 upvalue 就变成了全局表。</li>
 * </ul>
 */
class DumpTest {

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    @TestFactory
    List<DynamicTest> dumpScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "dump", DUMP);
        add(tests, "err", ERRORS);
        add(tests, "bad", BAD);
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
     * 手工构造二进制 chunk 的公共前奏。
     * <p>
     * 头部直接取自 {@code string.dump(function() end)} 的前 31 个字节
     * （4 签名 + 1 版本 + 1 格式 + 6 校验 + 3 尺寸 + 8 整数 + 8 浮点）——
     * 比在测试里硬写一串转义更不容易抄错，也顺带把头部长度钉住。
     */
    private static final String HAND = """
            local HDR = string.dump(function() end):sub(1, 31)
            -- 变长无符号数：7 位一组、大端在前，最后一组最高位置 1
            local function sz(n)
              if n == 0 then return "\\128" end
              local t = {}
              while true do
                t[#t + 1] = n % 128
                n = n // 128
                if n == 0 then break end
              end
              t[1] = t[1] + 128
              local r = {}
              for i = #t, 1, -1 do r[#r + 1] = string.char(t[i]) end
              return table.concat(r)
            end
            -- 拼一个「只有常量表、其它全空」的函数
            local function chunk(consts)
              local s = "\\0" .. sz(0) .. sz(0) .. sz(0)  -- nupvals, source, 行号范围
                      .. "\\0\\0\\0"                       -- numparams, is_vararg, maxstacksize
                      .. sz(0)                           -- code
                      .. sz(#consts)
              for i = 1, #consts do s = s .. consts[i] end
              s = s .. sz(0)                             -- upvalues
                      .. sz(0)                           -- protos
                      .. sz(0) .. sz(0) .. sz(0) .. sz(0) -- 调试信息四段
              return HDR .. s
            end
            """;


    /* ==================== 运行期 ==================== */

    private static final String[] DUMP = {
            """
            local f = function(a) return a * 2 end
            local s = string.dump(f)
            print(type(s), #s > 0)
            print(load(s)(21))
            print(pcall(load, s, nil, "t"))
            print(load(s, nil, "b") ~= nil)
            """,

            """
            -- strip 剥掉行号与源名：错误不再带位置前缀
            local f = function() error("boom") end
            print(pcall(f))
            local g = load(string.dump(f))
            print(pcall(g))
            local h = load(string.dump(f, true))
            print(pcall(h))
            """,

            """
            -- 内嵌函数、各类常量、长字符串都要能往返
            local f = function(a)
              local function g(b) return b * 3 end
              return g(a) .. "x" .. 1.5 .. tostring(true) .. tostring(nil) .. #string.rep("z", 100)
            end
            print(f(2))
            print(load(string.dump(f))(2))
            """,

            """
            -- 第 0 号 upvalue 会被 load 换成 _ENV：原 upvalue 变成全局表
            local up = 10
            local f = function(a) return a + up end
            print(f(1))
            local g = load(string.dump(f))
            print(pcall(g, 1))
            """,

            """
            -- 主 chunk 也能 dump（它的 sizeupvalues 就是 1）
            local m = load("return 7")
            print(m())
            print(load(string.dump(m))())
            """,

            """
            -- 二进制 chunk 落盘再由 loadfile / dofile 读回来
            local p = "/tmp/fkdump_f1.luac"
            local f = io.open(p, "wb")
            f:write(string.dump(function() return 99 end))
            f:close()
            print(loadfile(p)())
            print(dofile(p))
            print(pcall(loadfile, p, "t"))
            os.remove(p)
            """,

            """
            -- 同一个 proto 被多个闭包共享时 dump 出来的仍然只有一份
            local t = {}
            for i = 1, 3 do t[i] = function() return i end end
            local s = string.dump(t[1])
            print(#s)
            print(load(s)())
            """, };

    private static final String[] ERRORS = {
            """
            print(pcall(string.dump))
            print(pcall(string.dump, print))
            print(pcall(string.dump, 42))
            print(pcall(string.dump, "s"))
            print(pcall(string.dump, function() return 1 end, "x"))
            print(#string.dump(function() return 1 end, false) > 0)
            """,

            """
            -- 剥完之后源名也没了，于是 chunk 名是什么都不影响报错
            local f = function() error("bare") end
            print(pcall(load(string.dump(f, true), "=myname")))
            print(pcall(load(string.dump(f), "=myname")))
            """, };

    private static final String[] BAD = {
            """
            -- 截断到各个位置，报的都是 truncated chunk
            local s = string.dump(function() return 1 end)
            print(load(s:sub(1, 10)))
            print(load(s:sub(1, 20)))
            print(load(s:sub(1, #s - 1)))
            """,

            """
            -- 头部各字段逐个错：版本、格式、校验串
            print(load("\\27Lua\\1\\0"))
            print(load("\\27Lua\\84\\1"))
            print(load("\\27Lua\\84\\0\\25\\147\\r\\n\\26\\n\\4\\8\\8"))
            print(load("\\27Lua\\84\\0\\25\\147\\r\\n\\26\\n\\4\\8\\8\\120\\86\\0\\0\\0\\0\\0\\0\\0"))
            """,

            """
            -- 首字节是签名、但后面压根不是 Lua：走 undump 而不是词法
            print(load("\\27Lua"))
            print(load("\\27Lua", "=myname"))
            print(load("\\27", nil, "b"))
            """,

            // 手工拼出来的 chunk：用来打那些「正常 dump 永远产不出来」的分支
            HAND +
            """
            -- 变长长度编码溢出：10 个 \\127 还不够，第 11 个才顶到 limit
            print(load(HDR .. string.rep("\\127", 10)))
            print(load(HDR .. string.rep("\\127", 11)))
            """,

            HAND +
            """
            -- 常量类型字节写成一个不存在的类型：C 的 lua_assert 在默认构建下
            -- 是空操作，于是那个常量就停在 nil 上，chunk 照样加载成功
            print(type(load(chunk({ "\\99" }))))
            -- 而类型合法、长度却是 0 的字符串常量要报错
            print(load(chunk({ "\\4" .. sz(0) })))
            """,

            HAND +
            """
            -- 长字符串常量（> LUAI_MAXSHORTLEN，不驻留）走的是另一条分支
            local body = string.rep("z", 100)
            print(type(load(chunk({ "\\4" .. sz(#body + 1) .. body }))))
            """, };

    /* ==================== 产物逐字节比 ==================== */

    private static final String REF_BIN = "target/rt/dump_ref.bin";
    private static final String MINE_BIN = "target/rt/dump_mine.bin";

    @Test
    void dumpBytesMatchReference() throws Exception {
        assertDumpBytes("simple", "function(a) return a * 2 end", false);
        assertDumpBytes("simple-strip", "function(a) return a * 2 end", true);
        assertDumpBytes("nested", "function(a) local function g(b) return b + a end return g(a) end",
                false);
        assertDumpBytes("consts",
                "function() return 1, 1.5, \"short\", string.rep(\"z\", 100), true, nil end", false);
        assertDumpBytes("vararg", "function(...) local a, b = ... return a, b, select('#', ...) end",
                false);
        assertDumpBytes("loop", "function(n) local s = 0 for i = 1, n do s = s + i end return s end",
                false);
        assertDumpBytes("upval", "function() local x = 1 return function() return x end end", false);
        assertDumpBytes("goto", "function(n) for i = 1, n do if i == 3 then goto cont end ::cont:: end end",
                false);
        // 常量字符串超过 LUAI_MAXSHORTLEN：dump 与 undump 都走长字符串那条路
        assertDumpBytes("longstr", "function() return \"" + "z".repeat(100) + "\" end", false);
    }

    /**
     * 两边各自把 {@code string.dump(f)} 写到文件，再比这两个文件。
     * <p>
     * <b>两个脚本必须从同一个路径跑。</b>dump 里存着源名，源名就是 chunk 名，
     * 两边路径不同则产物必然不同，比出来的「差异」全是路径造成的噪声。
     * 所以先跑参考实现写 {@link #REF_BIN}，再跑本实现写 {@link #MINE_BIN}，
     * 中间复用同一个 {@link Diff#CASE_PATH}。
     */
    private static void assertDumpBytes(String name, String funcExpr, boolean strip) throws Exception {
        String script = "local f = io.open(\"%s\", \"wb\")\n"
                + "f:write(string.dump(%s, %s))\n"
                + "f:close()\n";
        ReferenceLua.Result r = ReferenceLua.runBytes(
                String.format(script, REF_BIN, funcExpr, strip).getBytes(StandardCharsets.UTF_8),
                Diff.CASE_PATH).result;
        assertEquals("", r.output, "参考实现跑 dump 用例 " + name + " 时就出错了");
        String mine = Diff.mine(String.format(script, MINE_BIN, funcExpr, strip));
        assertEquals("", mine, "本实现跑 dump 用例 " + name + " 时就出错了");
        assertArrayEquals(Files.readAllBytes(Paths.get(REF_BIN)),
                Files.readAllBytes(Paths.get(MINE_BIN)),
                "string.dump 的产物与参考实现不一致：" + name);
    }
}
