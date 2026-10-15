package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lua 5.4 语法对齐(Stage A)的锁定测试。
 * <p>
 * 这一组只关心「写法」:关键字拼写、块注释、取长度运算符、while/for 的 do 收尾。
 * 每条都同时钉住语义,避免出现「文法接受了但算出别的数」这种比报错更糟的情况。
 */
public class LuaSyntaxTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    private Object run(String script) {
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        return fk.run(f, "f");
    }

    private void bad(String script) {
        f.clearerr();
        assertFalse(fk.parsestr(f, script), "应当解析失败: " + script);
        f.clearerr();
    }

    /** local / function 是 Lua 的声明与定义关键字 */
    @Test
    public void testLocalAndFunctionKeywords() {
        assertEquals(3L, run("function f()\n    local x = 3\n    return x\nend\n"));
        assertEquals(1L, run("function f()\n    return 1\nend\n"));
    }

    /**
     * 旧写法必须彻底失效:Lua 化之后 var / func / != / && / || / ! 都不再是记号,
     * 它们退化成普通标识符或非法字符,任何一处还被接受都说明清理没做干净。
     */
    @Test
    public void testOldSpellingsAreGone() {
        bad("function f()\n    var x = 3\n    return x\nend\n");
        bad("func f()\n    return 1\nend\n");
        bad("function f()\n    if 1 != 2 then\n        return 7\n    end\n    return 0\nend\n");
        bad("function f()\n    if 1 < 2 && 2 < 3 then\n        return 1\n    end\n    return 0\nend\n");
        bad("function f()\n    if 1 > 2 || 2 < 3 then\n        return 1\n    end\n    return 0\nend\n");
        bad("function f()\n    if !false then\n        return 1\n    end\n    return 0\nend\n");
    }

    /** ~= 是 Lua 的不等于;二元 ~ 仍是异或,两者不能互相顶掉 */
    @Test
    public void testNotEqualIsTildeEqual() {
        assertEquals(7L, run("function f()\n    if 1 ~= 2 then\n        return 7\n    end\n    return 0\nend\n"));
        assertEquals(0L, run("function f()\n    if 1 ~= 1 then\n        return 7\n    end\n    return 0\nend\n"));
        assertEquals(7L, run("function f()\n    if \"a\" ~= \"b\" then\n        return 7\n    end\n    return 0\nend\n"));
        // 异或没有被动过:12 ~ 10 == 6
        assertEquals(6L, run("function f()\n    return 12 ~ 10\nend\n"));
    }

    /**
     * # 是 Lua 的取长度,文法层脱糖为 size(...)。
     * 关键是 #t + 1 必须解析成 (#t) + 1,而不是把 + 吸进来变成 #(t + 1)。
     * 这条同时是 %right SHARP 的护栏:去掉那条声明后,冲突数会从 58 涨到 70,
     * 虽然当前默认方向碰巧正确,但已经失去显式保证。
     */
    @Test
    public void testSharpLengthOperator() {
        assertEquals(3L, run("function f()\n    return #\"abc\"\nend\n"));
        assertEquals(3L, run("function f()\n    local m = map()\n    m[\"a\"] = 1\n    m[\"b\"] = 2\n    m[\"c\"] = 3\n    return #m\nend\n"));
        // 优先级:必须是 (#m) + 1 == 4,而不是把 + 吸进 # 里
        assertEquals(4L, run("function f()\n    local m = map()\n    m[\"a\"] = 1\n    m[\"b\"] = 2\n    m[\"c\"] = 3\n    return #m + 1\nend\n"));
        assertEquals(4L, run("function f()\n    local m = map()\n    m[\"a\"] = 1\n    m[\"b\"] = 2\n    m[\"c\"] = 3\n    return (#m) + 1\nend\n"));
        assertEquals(0L, run("function f()\n    local m = map()\n    return #m\nend\n"));
    }

    /** --[[ ]] 是 Lua 长注释,可跨行;注释内容不得参与求值 */
    @Test
    public void testLongComment() {
        assertEquals(5L, run("function f()\n    --[[ 块注释\n    第二行 ]]\n    return 5\nend\n"));
        assertEquals(5L, run("function f()\n    --[[]]\n    return 5\nend\n"));
        // 注释里的 ]] 之前的内容全部作废,包括看起来像代码的整行
        assertEquals(2L, run("function f()\n    --[[ return 999 ]]\n    return 2\nend\n"));
    }

    /** while / for 用 do 收尾(Lua);if/elseif 仍用 then(Lua 也一样) */
    @Test
    public void testDoTerminatesLoops() {
        assertEquals(3L, run("function f()\n    local i = 0\n    while i < 3 do\n        i = i + 1\n    end\n    return i\nend\n"));
        assertEquals(6L, run("function f()\n    local s = 0\n    for local i = 0, i < 4, i++ do\n        s = s + i\n    end\n    return s\nend\n"));
        assertEquals(6L, run("function f()\n    local s = 0\n    for local i = 1 -> 4, 1 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 空循环体
        assertTrue(fk.parsestr(f, "function f()\n    for local i = 0, i < 3, i++ do\n    end\nend\n"));
        assertTrue(fk.parsestr(f, "function f()\n    local i = 0\n    while i < 3 do\n    end\nend\n"));
        // if 仍然是 then
        assertEquals(1L, run("function f()\n    if 1 < 2 then\n        return 1\n    end\n    return 0\nend\n"));
    }

    /**
     * Lua 数值 for:{@code for i = begin, end[, step] do ... end},步长省略时为 1。
     * <p>
     * 最要紧的是**闭区间**:Lua 的 {@code for i = 1, 4 do} 跑 1/2/3/4,而既有的
     * {@code for local i = 1 -> 4, 1 do} 是半开区间只跑 1/2/3。两者共用同一个
     * for_loop_stmt 节点,靠 m_closed 选 *_CLOSED 变体 —— 若这条退化成半开,
     * 从 Lua 抄来的循环会**静默少跑一次**,比解析失败更糟。
     */
    @Test
    public void testLuaNumericFor() {
        // 步长省略
        assertEquals(10L, run("function f()\n    local s = 0\n    for i = 1, 4 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 显式步长
        assertEquals(25L, run("function f()\n    local s = 0\n    for i = 1, 10, 2 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 负步长倒序,同样闭区间
        assertEquals(55L, run("function f()\n    local s = 0\n    for i = 10, 1, -1 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 边界可以是任意表达式
        assertEquals(25L, run("function f()\n    local n = 5\n    local s = 0\n    for i = 1, n * 2, n - 3 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 起点已越过终点:一次都不跑
        assertEquals(7L, run("function f()\n    local s = 7\n    for i = 5, 1 do\n        s = s + i\n    end\n    return s\nend\n"));
        // 嵌套:闭区间下应为 (1+2+3)*(1+2+3)
        assertEquals(36L, run("function f()\n    local s = 0\n    for i = 1, 3 do\n        for j = 1, 3 do\n            s = s + i * j\n        end\n    end\n    return s\nend\n"));
        // break 仍然有效
        assertEquals(6L, run("function f()\n    local s = 0\n    for i = 1, 10 do\n        if i > 3 then\n            break\n        end\n        s = s + i\n    end\n    return s\nend\n"));
        // 空循环体(带/不带步长两条规则都要能解析)
        assertEquals(9L, run("function f()\n    for i = 1, 3 do\n    end\n    return 9\nend\n"));
        assertEquals(9L, run("function f()\n    for i = 1, 3, 1 do\n    end\n    return 9\nend\n"));
    }

    /** 既有的 -> 写法是半开区间 [begin, end),不能被 Lua 化顺手改成闭区间 */
    @Test
    public void testArrowForStaysHalfOpen() {
        assertEquals(6L, run("function f()\n    local s = 0\n    for local i = 1 -> 4, 1 do\n        s = s + i\n    end\n    return s\nend\n"));
        // C 式 for 也照旧
        assertEquals(6L, run("function f()\n    local s = 0\n    for local i = 0, i < 4, i++ do\n        s = s + i\n    end\n    return s\nend\n"));
    }

    /** 逻辑运算符统一成 Lua 的 and / or / not */
    @Test
    public void testLogicalKeywords() {
        assertEquals(1L, run("function f()\n    if 1 < 2 and 2 < 3 then\n        return 1\n    end\n    return 0\nend\n"));
        assertEquals(0L, run("function f()\n    if 1 < 2 and 2 > 3 then\n        return 1\n    end\n    return 0\nend\n"));
        assertEquals(1L, run("function f()\n    if 1 > 2 or 2 < 3 then\n        return 1\n    end\n    return 0\nend\n"));
        assertEquals(1L, run("function f()\n    if not false then\n        return 1\n    end\n    return 0\nend\n"));
    }

    /**
     * 配对错误必须干净失败,不能顺手解析成别的东西。
     * 注意 while/for 用 then 收尾现在是错误(已换成 do),这正是要钉住的行为。
     */
    @Test
    public void testMismatchedDelimitersFail() {
        bad("function f()\n    local i = 0\n    while i < 3 then\n    end\nend\n");
        bad("function f()\n    for local i = 0, i < 3, i++ then\n    end\nend\n");
        bad("function f()\n    if 1 < 2 do\n        return 1\n    end\nend\n");
        bad("function f()\n    local i = 0\n    while i < 3 do\n        i = i + 1\nend\n");
    }
}
