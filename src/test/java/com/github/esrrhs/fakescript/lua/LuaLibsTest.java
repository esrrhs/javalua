package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link LuaLibs#openAll} —— {@code linit.c: luaL_openlibs} 的对应物。
 * <p>
 * 不是「测一个循环」：这里钉住两件会悄悄退化的事
 * <ul>
 *   <li><b>一个都不能少</b>。少开 {@code PackageLib}，{@code package.loaded} 里
 *       就没有 {@code _G}，于是 {@code lauxlib.c: pushglobalfuncname} 查不到
 *       C 函数的全局名，{@code pcall(string.len)} 的报错从
 *       {@code bad argument #1 to 'string.len'} 退化成 {@code ... to 'len'}——
 *       而那套报错是别的用例在看的，退化会表现成「那边绿、这边莫名少个前缀」。</li>
 *   <li><b>库表是同一张</b>。{@code string} 库的 {@code __index} 挂在字符串元表上，
 *       如果 {@code openAll} 里新建了库表而 {@code StringLib.open} 又建了一张，
 *       {@code ("x"):upper()} 就会用上错的那张。</li>
 * </ul>
 */
final class LuaLibsTest {

    @BeforeAll
    static void requireRef() {
        // 只有第三条用例需要参考实现，前两条这里跳过也无所谓，但一次跳过更省事
        assumeTrue(ReferenceLua.find() != null, "参考实现二进制不存在，跳过一致性测试");
    }

    @Test
    void opensEveryStandardLibrary() {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        String[] names = { "string", "table", "math", "io", "os", "utf8",
                "coroutine", "package", "debug" };
        for (String n : names) {
            LuaValue v = L.globals.get(n);
            assertTrue(v != null && v.isTable(), n + " 没开：" + v);
        }
        // base 库是散在全局上的函数，挑几个代表
        for (String fn : new String[] { "print", "pcall", "xpcall", "select",
                "setmetatable", "load", "require", "tostring" }) {
            LuaValue v = L.globals.get(fn);
            assertTrue(v != null && v.isFunction(), fn + " 没开：" + v);
        }
    }

    @Test
    void libraryTableIsTheOneTheMetatablePointsAt() {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        LuaValue err = L.runProtected("return ('x'):upper()".getBytes(), "=t");
        assertEquals(null, err, "字符串元表的 __index 不是开出来的那张 string 库表");
    }

    /**
     * 走 {@code Diff} 而不是自造断言：{@code print(pcall(string.len))} 的期望值
     * 是 {@code false bad argument #1 to 'string.len' (...)}，那个
     * {@code 'string.len'} 完全来自 {@code _LOADED} 反查，自造断言等于把结论
     * 抄一遍——抄错了还绿。交给参考实现才是真的钉住。
     * <p>
     * 用 {@code pcall(string.len)} 而不是 {@code string.len(nil)}：后者有调用点，
     * 名字走 {@code getfuncname} 那条路，查出来是 {@code 'len'}（参考实现也是）。
     * 要逼出 {@code pushglobalfuncname}，调用者必须是 C 帧。
     */
    @Test
    void globalFuncNamesSurvive() {
        Diff.assertMatches("print(pcall(string.len))");
        Diff.assertMatches("print(pcall(string.len, nil))");
        Diff.assertMatches("print(pcall(table.concat))");
    }
}
