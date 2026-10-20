package com.github.esrrhs.javalua;

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

import java.io.File;

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

    private static final String GRID_BIN = "target/rt/dumpgrid.bin";

    /**
     * 字节对撞的<b>全语法语料</b>（约 360 条函数 × {@code strip} 真/假）。
     * <p>
     * 现有 {@link #dumpBytesMatchReference} 只比了 9 个手写函数，覆盖不到
     * 编译器里那些「只有规模上去了才走到的分支」。这里机械铺开：
     * <ul>
     *   <li><b>常量</b>：整数（含 {@code 2^63-1}、{@code -2^63}）、浮点
     *       （含 {@code -0.0}、次正规 {@code 0x1p-1074}）、字符串
     *       （长度跨 {@code LUAI_MAXSHORTLEN} 的 39/40/41 与 70000）；</li>
     *   <li><b>运算符</b>：18 个二元 + 4 个一元 + 拼接链（1..60 项）；</li>
     *   <li><b>控制流</b>：{@code if}/{@code while}/{@code repeat}/数字
     *       {@code for}/泛型 {@code for}/{@code break}/{@code goto}；</li>
     *   <li><b>闭包</b>：1..60 个 upvalue、{@code <const>} 与 {@code <close>}；</li>
     *   <li><b>规模边界</b>：{@code if} 体指令数到 140000（{@code OP_JMP}
     *       的 {@code sBx} 是 18 位有符号，超过 {@code 2^17} 就溢出）、
     *       嵌套 200/210 层（C 栈溢出）、upvalue 与参数的 255/256 上限、
     *       常量表 255/256/20000、{@code SETLIST} 的 50/51 批量；</li>
     *   <li><b>行号</b>：跨 {@code MAXIWTHABS} 的 300 行空行与 200 个局部变量。</li>
     * </ul>
     * 规模上限触发的<b>语法错误本身</b>也是差分目标（{@code too many local
     * variables (limit is 200)}、{@code function or expression needs too many
     * registers}），所以脚本的 stdout 也一并比。
     * <p>
     * 产物写成「8 字节长度 + 内容」的记录流，便于失败时直接指出是第几条。
     */
    @Test
    void dumpBytesGridMatchReference() throws Exception {
        new File("target/rt").mkdirs();
        // 两边都从 CASE_PATH 跑：dump 里存着源名，源名就是 chunk 名
        String refOut = Diff.ref(GRID_SCRIPT);
        byte[] ref = Files.readAllBytes(Paths.get(GRID_BIN));
        String mineOut = Diff.mine(GRID_SCRIPT);
        byte[] got = Files.readAllBytes(Paths.get(GRID_BIN));
        assertEquals(refOut, mineOut, "dump 网格脚本自身的输出不一致");
        int at = firstDifferingRecord(ref, got);
        if (at >= 0) {
            org.junit.jupiter.api.Assertions.fail(
                    "string.dump 的产物与参考实现不一致：第 " + at + " 条记录（0 起，"
                            + "每条函数两条：strip=false / true）");
        }
    }

    /** 顺着「8 字节小端长度 + 内容」走记录流，返回第一条不同的下标；全同给 -1 */
    private static int firstDifferingRecord(byte[] a, byte[] b) {
        int i = 0;
        int n = 0;
        while (i + 8 <= a.length && i + 8 <= b.length) {
            long la = 0;
            long lb = 0;
            for (int k = 7; k >= 0; k--) {
                la = (la << 8) | (a[i + k] & 0xFF);
                lb = (lb << 8) | (b[i + k] & 0xFF);
            }
            i += 8;
            if (la != lb || la < 0 || la > Integer.MAX_VALUE) {
                return n;
            }
            int len = (int) la;
            if (i + len > a.length || i + len > b.length) {
                return n;
            }
            for (int k = 0; k < len; k++) {
                if (a[i + k] != b[i + k]) {
                    return n;
                }
            }
            i += len;
            n++;
        }
        return (a.length == b.length) ? -1 : n;
    }

    private static final String GRID_SCRIPT = """

-- string.dump 字节对撞：全语法语料 x（strip 真/假）
-- 每个条目都是一段「返回 Lua 函数」的源码；dump 出来的字节必须与参考实现
-- 逐字节相同 —— 这是比「跑得对」更硬的断言（连带钉住 Parser/Codegen/Proto）。
local FS = {}
local function add(e) FS[#FS + 1] = e end

-- 常量
for _, v in ipairs({ "0", "1", "-1", "127", "128", "255", "32767", "65535",
                     "2147483647", "2147483648", "-2147483648", "9007199254740993",
                     "-9007199254740993", "0x7fffffffffffffff", "-0x8000000000000000",
                     "0x10", "0X1p4" }) do
  add("function() return " .. v .. " end")
end
for _, v in ipairs({ "0.0", "-0.0", "1.5", "-1.5", "1e300", "1e-300", ".5", "5.",
                     "1e15", "1e16", "3.14159265358979", "0x1p-1074", "0x1.fffffffffffffp+1023" }) do
  add("function() return " .. v .. " end")
end
for _, n in ipairs({ 0, 1, 39, 40, 41, 42, 100, 255, 256 }) do
  add("function() return '" .. string.rep("z", n) .. "' end")
end
add("function() return '' end")
add("function() return '\\\\0' end")
add("function() return '\\\\255\\\\254' end")
add("function() return nil end")
add("function() return true, false end")

-- 运算符
for _, op in ipairs({ "+", "-", "*", "/", "//", "%", "^",
                      "==", "~=", "<", "<=", ">", ">=", "&", "|", "~", "<<", ">>" }) do
  add("function(a, b) return a " .. op .. " b end")
end
for _, u in ipairs({ "-", "not ", "#", "~" }) do add("function(a) return " .. u .. "a end") end
for _, n in ipairs({ 1, 2, 3, 10, 60 }) do
  local t = {}
  for i = 1, n do t[i] = "a" end
  add("function(a) return " .. table.concat(t, " .. ") .. " end")
end
add("function(a) return a .. 1 .. 1.5 .. 'x' end")

-- 表构造
add("function() return {} end")
add("function() return {1, 2, 3} end")
add("function() return {x = 1, y = 2} end")
add("function() return {1, 2, x = 3, [10] = 4} end")
add("function() return {[1] = 1, [2] = 2, 3} end")
add("function(f, g) return {f(), g()} end")
add("function(...) return {...} end")
add("function(...) return {1, ...} end")
do
  local t, u = {}, {}
  for i = 1, 60 do t[i] = tostring(i) end
  for i = 1, 30 do u[i] = "[" .. i .. "]=" .. i end
  add("function() return {" .. table.concat(t, ",") .. "} end")
  add("function() return {" .. table.concat(u, ",") .. "} end")
end

-- 调用
for _, e in ipairs({ "function(f) return f() end", "function(f, a) return f(a) end",
                     "function(f, a, b) return f(a, b) end", "function(t) return t:m() end",
                     "function(t, a) return t:m(a) end",
                     "function(f) local r = f() return r end",
                     "function(...) return select('#', ...) end",
                     "function(...) return select(2, ...) end",
                     "function(...) local a, b = ... return a, b end",
                     "function(f, ...) return f(...) end", "function(a) return (a) end",
                     "function(a, b) return a and b end", "function(a, b) return a or b end",
                     "function(a, b, c) return a and b or c end",
                     "function(a, b) return a and b or a or b end" }) do
  add(e)
end

-- 控制流
for _, e in ipairs({ "function(a) if a then return 1 end end",
                     "function(a) if a then return 1 else return 2 end end",
                     "function(a, b) if a then return 1 elseif b then return 2 else return 3 end end",
                     "function(n) local s = 0 while s < n do s = s + 1 end return s end",
                     "function(n) local s = 0 repeat s = s + 1 until s >= n return s end",
                     "function(n) local s = 0 for i = 1, n do s = s + i end return s end",
                     "function(n) local s = 0 for i = 1, n, 2 do s = s + i end return s end",
                     "function(n) local s = 0 for i = n, 1, -1 do s = s + i end return s end",
                     "function(t) local s = 0 for k, v in pairs(t) do s = s + 1 end return s end",
                     "function(t) local s = 0 for i, v in ipairs(t) do s = s + v end return s end",
                     "function(n) for i = 1, n do if i == 3 then break end end return n end",
                     "function(n) for i = 1, n do if i == 3 then goto cont end ::cont:: end end",
                     "function(n) do goto done end ::done:: end",
                     "function(n) while true do if n then goto out end end ::out:: return n end",
                     "function(a, b, c) if a then if b then if c then return 1 end end end end" }) do
  add(e)
end

-- 闭包 / upvalue
add("function() local x = 1 return function() return x end end")
add("function() local x, y = 1, 2 return function() return x + y end end")
add("function() local t = {} for i = 1, 3 do t[i] = function() return i end end return t end")
add("function(a) return function(b) return function(c) return a + b + c end end end")
for _, n in ipairs({ 1, 2, 5, 20, 60 }) do
  local ups, ret = {}, {}
  for i = 1, n do ups[i] = "local u" .. i .. " = " .. i; ret[i] = "u" .. i end
  add("function() " .. table.concat(ups, " ") .. " return function() return "
      .. table.concat(ret, "+") .. " end end")
end
add("function() local x <const> = 1 return function() return x end end")
add("function() local x <close> = nil return function() return x end end")

-- 规模：局部变量 / 常量 / 参数
for _, n in ipairs({ 1, 20, 60, 200, 250, 260 }) do
  local locs, ret = {}, {}
  for i = 0, n - 1 do locs[i + 1] = "local v" .. i .. " = " .. i; ret[i + 1] = "v" .. i end
  add("function() " .. table.concat(locs, " ") .. " return " .. table.concat(ret, "+") .. " end")
end
for _, n in ipairs({ 1, 100, 250, 260, 300 }) do
  local t = {}
  for i = 0, n - 1 do t[i + 1] = tostring(i) end
  add("function() return " .. table.concat(t, ",") .. " end")
end
for _, n in ipairs({ 0, 1, 5, 20, 100, 200 }) do
  local ps, ret = {}, {}
  for i = 0, n - 1 do ps[i + 1] = "p" .. i; ret[i + 1] = "p" .. i end
  add("function(" .. table.concat(ps, ",") .. ") return "
      .. (n > 0 and table.concat(ret, "+") or "nil") .. " end")
end
add("function(...) return ... end")

-- 行号：跨 MAXIWTHABS 的大间距
add("function()\\n" .. string.rep("\\n", 300) .. "return 1\\nend")
do
  local b = { "function()" }
  for i = 0, 199 do b[#b + 1] = "local a" .. i .. " = " .. i end
  b[#b + 1] = "return a199\\nend"
  add(table.concat(b, "\\n"))
end

-- 语法糖与边角
for _, e in ipairs({ "function(a) return a.x.y end",
                     "function(a) a.x = 1 a[1] = 2 a['k'] = 3 return a end",
                     "function() local a; a = 1; return a end",
                     "function() local a, b, c = 1, 2, 3 return a, b, c end",
                     "function(a) local b <const> = a return b end",
                     "function() return function() return function() return 1 end end end",
                     "function() do end do end return 1 end",
                     "function() while false do end return 1 end",
                     "function() for i = 1, 0 do end return 1 end",
                     "function() ::a:: ::b:: ::c:: return 1 end" }) do
  add(e)
end

-- 规模边界：跳转偏移（sBx 是 18 位有符号）、深度嵌套、upvalue/参数上限
for _, n in ipairs({ 100, 1000, 60000, 70000, 140000 }) do
  add("function(c) if c then " .. string.rep("a = 1 ", n) .. "end end")
end
for _, n in ipairs({ 100, 1000, 60000, 70000 }) do
  add("function(c) local i = 0 while i < 1 do " .. string.rep("a = 1 ", n) .. "i = i + 1 end end")
end
for _, n in ipairs({ 1000, 40000 }) do
  add("function() for i = 1, 1 do " .. string.rep("a = 1 ", n) .. "end end")
end
for _, n in ipairs({ 10, 100, 190, 200, 210 }) do
  add("function() " .. string.rep("do ", n) .. string.rep("end ", n) .. "end")
  add("function(a) " .. string.rep("if a then ", n) .. string.rep("end ", n) .. "end")
end
for _, n in ipairs({ 255, 256, 1000, 20000 }) do
  local t = {}
  for i = 1, n do t[i] = tostring(i) end
  add("function() return " .. table.concat(t, ",") .. " end")
end
for _, n in ipairs({ 100, 254, 255, 256 }) do
  local ups, ret = {}, {}
  for i = 1, n do ups[i] = "local u" .. i .. " = " .. i; ret[i] = "u" .. i end
  add("function() " .. table.concat(ups, " ") .. " return function() return "
      .. table.concat(ret, "+") .. " end end")
end
for _, n in ipairs({ 100, 200, 201 }) do
  local ps, ret = {}, {}
  for i = 1, n do ps[i] = "p" .. i; ret[i] = "p" .. i end
  add("function(" .. table.concat(ps, ",") .. ") return " .. table.concat(ret, "+") .. " end")
end
for _, n in ipairs({ 39, 40, 41, 1000, 70000 }) do
  add("function() return '" .. string.rep("z", n) .. "' end")
end
for _, n in ipairs({ 100, 40000 }) do
  add("function() do goto done end " .. string.rep("a = 1 ", n) .. "::done:: end")
end
for _, n in ipairs({ 1, 50, 51, 100, 2000 }) do
  local t = {}
  for i = 1, n do t[i] = tostring(i) end
  add("function() return {" .. table.concat(t, ",") .. "} end")
end

-- 每条 dump 成「长度前缀 + 内容」的记录，便于定位哪一条不一致
local out = assert(io.open("target/rt/dumpgrid.bin", "wb"))
local n = 0
for i = 1, #FS do
  local f, err = load("return " .. FS[i])
  if not f then
    print("LOADFAIL " .. i .. " " .. tostring(err))
  else
    local ok, fn = pcall(f)
    if not ok then
      print("RUNFAIL " .. i .. " " .. tostring(fn))
    else
      for _, strip in ipairs({ false, true }) do
        local d = string.dump(fn, strip)
        out:write(string.pack("<I8", #d))
        out:write(d)
        n = n + 1
      end
    end
  end
end
out:close()
print("records " .. n .. " of " .. (#FS * 2))
            """;

}
