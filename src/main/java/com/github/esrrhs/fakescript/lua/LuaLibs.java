package com.github.esrrhs.fakescript.lua;

/**
 * 标准库的入口 —— 对应 {@code linit.c} 的 {@code luaL_openlibs}。
 * <p>
 * 之前这份「依次开十个库」的代码是<b>写在测试驱动里</b>的（{@code Diff}），
 * 于是引擎侧一直没有 {@code luaL_openlibs} 的对应物：想跑一段脚本的宿主得自己
 * 记住哪十个库、按什么顺序开。顺序不是小事——{@code PackageLib} 要往
 * {@code package.loaded} 里登记，而 {@code lauxlib.c: pushglobalfuncname} 又靠
 * {@code package.loaded} 反查 C 函数的名字，开漏一个，{@code pcall(string.len)}
 * 的报错就从 {@code 'string.len'} 退化成 {@code 'len'}。
 * <p>
 * 开库顺序照 {@code linit.c} 的 {@code loadedlibs}：base、package 先于其余，
 * 因为后面那些库要靠 {@code require} 的登记机制。
 */
public final class LuaLibs {

    private LuaLibs() {
    }

    /** {@code luaL_openlibs} */
    public static void openAll(LuaState L) {
        BaseLib.open(L);
        PackageLib.open(L);
        CoroutineLib.open(L);
        StringLib.open(L);
        Utf8Lib.open(L);
        TableLib.open(L);
        MathLib.open(L);
        OsLib.open(L);
        IoLib.open(L);
        DebugLib.open(L);
    }
}
