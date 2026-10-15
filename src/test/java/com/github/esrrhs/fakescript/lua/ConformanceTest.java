package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 一致性测试：拿真实的 {@code lua5.4} 二进制当裁判。
 * <p>
 * 这是本项目的主闸门（见 README「怎么证明它真的是 Lua」）。目前解释器还没落地，
 * 所以这里先做两件已经能做的事：
 * <ol>
 *   <li>确认参考二进制可用 —— 否则后面所有差分结论都无从谈起；</li>
 *   <li>把已经移植好的那部分语义（表键规范化、{@code #t} 边界等）<b>用真实
 *       Lua 跑一遍并记录结果</b>，作为可执行的语义契约。</li>
 * </ol>
 * 第 2 条不是空转：它把「我们以为 Lua 是这样」变成「实测 Lua 是这样」，
 * 一旦参考版本变了、或者当初的理解是错的，这里会先红。等解释器在阶段 3 落地，
 * 这些脚本改成两侧各跑一遍、比对 stdout 即可，无需另建框架。
 */
public class ConformanceTest {

    /**
     * 没有参考二进制时整类跳过。
     * <p>
     * 为什么是 skip 而不是 fail：普通的 {@code mvn test}（本地与 build job）
     * 不保证装了 lua5.4，硬失败会让「没装参考实现」变成编译失败，掩盖真正的
     * 回归。真正的一致性校验放在 CI 的 conformance job 里跑——那个 job 会先
     * 从源码构建 lua5.4，构建不出来它自己就红了，不需要这里再守一道。
     */
    @BeforeAll
    public static void requireReference() {
        Assumptions.assumeTrue(ReferenceLua.available(),
                "找不到 lua5.4 参考二进制，跳过一致性测试。设置 LUA54_BIN，"
                        + "或在仓库内构建到 target/lua54/src/lua。查找结果：" + ReferenceLua.describe());
    }

    /** 参考二进制确实是 5.4（5.5 或其他版本会得出错误的「不一致」结论） */
    @Test
    public void referenceBinaryIsLua54() {
        ReferenceLua.Result r = ReferenceLua.run("print(_VERSION)");
        assertEquals(0, r.exitCode);
        assertTrue(r.output.contains("Lua 5.4"), "版本应当是 5.4，实际：" + r.output);
    }

    /**
     * 表键语义：整值浮点规范化、整数键与字符串键分离、{@code #t} 的边界。
     * <p>
     * 这些断言与 {@link LuaTableTest} 里的一一对应，区别在于这里的期望值是
     * <b>真实 Lua 5.4 跑出来的</b>，不是我们替它写的。
     */
    @Test
    public void tableKeySemantics() {
        String script = ""
                + "local t = {}\n"
                + "t[1.0] = 'a'\n"
                + "local k, v = next(t)\n"
                + "print(math.type(k), k, v, #t)\n"
                + "t['1'] = 'b'\n"
                + "print(t[1], t['1'])\n"
                + "local n = 0 for _ in pairs(t) do n = n + 1 end print(n)\n"
                + "local z = {}\n"
                + "z[0.0] = 'zero'\n"
                + "print(#z, z[0], z[1])\n"
                + "local f = {}\n"
                + "f[1.5] = 'x'\n"
                + "local fk = next(f) print(math.type(fk), fk)\n"
                + "local ok, err = pcall(function() local q = {} q[0/0] = 1 end)\n"
                + "print(ok, err ~= nil and tostring(err):find('table index is NaN') ~= nil)\n";
        ReferenceLua.Result r = ReferenceLua.run(script);
        assertEquals(0, r.exitCode, r.toString());
        assertEquals(""
                + "integer\t1\ta\t1\n"
                + "a\tb\n"
                + "2\n"
                + "0\tzero\tnil\n"
                + "float\t1.5\n"
                + "false\ttrue\n", r.output.replaceAll("\r\n", "\n"));
    }

    /** {@code #t} 在数组部分与哈希部分交界处的行为 */
    @Test
    public void lengthBorderSemantics() {
        String script = ""
                + "local z = {}\n"
                + "for i = 1, 8 do z[i] = i end\n"
                + "print(#z)\n"
                + "z[9] = 9\n"
                + "print(#z)\n"
                + "z[4] = nil\n"
                + "print(#z >= 3 and #z <= 9, z[4])\n"
                + "local w = {}\n"
                + "w[2^53] = 1\n"
                + "print(#w, w[2^53])\n";
        ReferenceLua.Result r = ReferenceLua.run(script);
        assertEquals(0, r.exitCode, r.toString());
        String out = r.output.replaceAll("\r\n", "\n");
        assertEquals("8\n9\ntrue\tnil\n0\t1\n", out);
    }
}
