package com.github.esrrhs.javalua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * {@code collectgarbage} 的 {@code setpause} / {@code setstepmul} ——
 * {@code lbaselib.c} 里最容易照字面写错的两个选项。
 * <p>
 * 为什么单独开一类：{@code RuntimeTest} 里早就有
 * {@code collectgarbage("setpause", 200)}，可它<b>两种写法都绿</b>——
 * 初值恰好就是 200，写不写、量不量化都读回 200。真正的分歧藏在
 * 「不传第二个参数」与「传一个不是 4 的倍数的值」两处，
 * 是本轮铺 {@code lua_gc} 的参数网格才撞出来的：
 * <pre>
 * collectgarbage("setpause")       --&gt; 200  并把 pause 设成 0
 * collectgarbage("setpause")       --&gt; 0    （本实现以前给 200）
 * collectgarbage("setpause", 50)   --&gt; 0
 * collectgarbage("setpause")       --&gt; 48   （50/4==12，12*4==48，不是 50）
 * </pre>
 * <p>
 * 三处都不是「差不多」：{@code setgcparam} 是<b>无条件</b>赋值（{@code 0}
 * 也照写）、存的是 {@code v/4} 而字段是 {@code lu_byte}（{@code -10} 会绕成
 * {@code 1016}、{@code 1024} 会绕成 {@code 0}）。三条全按实测钉住，
 * 详见 {@link LuaState#setGcPause}。
 */
class GcParamTest {

    @TestFactory
    List<DynamicTest> gcParamsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = SCRIPTS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("gcparam[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }

    private static final String[] SCRIPTS = {
            // ① 默认值 0 也是要写进去的：setpause 不带参数会把 pause 清成 0
            """
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause", 100))
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause", 50))
            print(collectgarbage("setpause"))
            """,
            // ② lu_byte 截断：1024 -> 0，-10 -> 1016
            """
            print(collectgarbage("setpause", 1024))
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause", -10))
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause", 1))
            print(collectgarbage("setpause"))
            print(collectgarbage("setpause", -1))
            print(collectgarbage("setpause"))
            """,
            // ③ setstepmul 同一套存取，两个字段互不干扰
            """
            print(collectgarbage("setstepmul"))
            print(collectgarbage("setstepmul", 100))
            print(collectgarbage("setstepmul"))
            print(collectgarbage("setstepmul", 0))
            print(collectgarbage("setstepmul"))
            print(collectgarbage("setstepmul", 7))
            print(collectgarbage("setstepmul"))
            print(collectgarbage("setpause"))
            """,
            // ④ 交叉：先动 stepmul 再动 pause，两边各自记住自己的旧值
            """
            print(collectgarbage("setpause", 300), collectgarbage("setstepmul", 60))
            print(collectgarbage("setpause"), collectgarbage("setstepmul"))
            print(collectgarbage("isrunning"))
            print(collectgarbage("stop"), collectgarbage("isrunning"))
            print(collectgarbage("setpause"))
            print(collectgarbage("restart"), collectgarbage("isrunning"))
            """,
            // ⑤ 参数类型错：这两个选项走 luaL_optinteger，不是 checkinteger
            """
            print(pcall(collectgarbage, "setpause", "x"))
            print(pcall(collectgarbage, "setstepmul", true))
            print(pcall(collectgarbage, "setpause", 1.0))
            print(pcall(collectgarbage, "setpause", 1.5))
            print(pcall(collectgarbage, "setpause", {}))
            """,
    };
}
