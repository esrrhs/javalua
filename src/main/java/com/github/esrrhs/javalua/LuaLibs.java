package com.github.esrrhs.javalua;

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

    /**
     * {@code linit.c: loadedlibs} 的<b>原顺序</b>。
     * <p>
     * 顺序照抄而不是「只要都开过就行」：{@code luaL_openlibs} 是逐个
     * {@code luaL_requiref} 走下来的，{@code package.loaded} 的插入次序因此被
     * 定死。原先这里是 base/package/coroutine/string/utf8/table/math/os/io/
     * debug，与上游不一致——{@code io} 开在 {@code os} 之前，而
     * {@code lauxlib.c: findfield} 又是按表序搜的，两边「同一次搜索找到的名字」
     * 就可能不同。
     */
    private static final Object[][] LOADED_LIBS = {
        {"_G", (java.util.function.Consumer<LuaState>) BaseLib::open},
        {"package", (java.util.function.Consumer<LuaState>) PackageLib::open},
        {"coroutine", (java.util.function.Consumer<LuaState>) CoroutineLib::open},
        {"table", (java.util.function.Consumer<LuaState>) TableLib::open},
        {"io", (java.util.function.Consumer<LuaState>) IoLib::open},
        {"os", (java.util.function.Consumer<LuaState>) OsLib::open},
        {"string", (java.util.function.Consumer<LuaState>) StringLib::open},
        {"math", (java.util.function.Consumer<LuaState>) MathLib::open},
        {"utf8", (java.util.function.Consumer<LuaState>) Utf8Lib::open},
        {"debug", (java.util.function.Consumer<LuaState>) DebugLib::open},
    };

    /** {@code luaL_openlibs} */
    public static void openAll(LuaState L) {
        for (Object[] e : LOADED_LIBS) {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<LuaState> open =
                    (java.util.function.Consumer<LuaState>) e[1];
            open.accept(L);
        }
    }

    /**
     * 逐个 {@code luaL_requiref} 的语义：<b>已加载就跳过</b>。
     * <p>
     * 与 {@link #openAll} 的差别就是这一句——{@code luaL_openlibs} 在 C 里连调
     * 两次是合法的（第二次是空操作），而直接 {@code openAll} 会因为
     * 「{@code FILE*} 元表已经建过」抛错。被 .so 里的
     * {@code luaL_openlibs(L)} 二次调用时走的是这一条。
     */
    public static void openAllSkippingLoaded(LuaState L) {
        for (Object[] e : LOADED_LIBS) {
            LuaValue v = L.loadedTable().get((String) e[0]);
            if (v != null && v.toBoolean()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<LuaState> open =
                    (java.util.function.Consumer<LuaState>) e[1];
            open.accept(L);
        }
    }
}
