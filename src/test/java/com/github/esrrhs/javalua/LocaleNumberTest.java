package com.github.esrrhs.javalua;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * locale 的小数点 —— {@code lua_getlocaledecpoint()} 那一圈的差分用例。
 * <p>
 * C 版把「数字与文本之间的那一个字符」交给了 locale：{@code os.setlocale} 一改，
 * {@code tonumber} / {@code tostring} / {@code string.format} / {@code io.write}
 * / {@code io.read("n")} 五条路全跟着变（{@code luaconf.h} 里那一个宏，
 * 调用点是 {@code lobject.c} 的 166/264/363、{@code lstrlib.c} 的 1044/1164、
 * {@code liolib.c} 的 486）；{@code os.date} 的 {@code %A}/{@code %B}/{@code %c}
 * 等也通过 shim 的 {@code strftime} 跟着变。Java 没有进程级 locale，所以这里
 * 靠 shim 现问 {@code localeconv()}（见 {@link NativeBridge#cLocaleDecpoint()}），
 * 数字仍统一按 '.' 算、进出时换算。
 * <p>
 * 这批用例<b>只有在装有逗号 locale 的机器上才测得到东西</b>：{@code pt_BR}
 * 不存在时两边都走 {@code NO_LOCALE} 那条分支，比对全绿但什么都没比。
 * 所以 CI 在跑之前 {@code locale-gen pt_BR}，而这个类在 locale 缺席时
 * <b>一条用例都不建</b>——空类会被 {@code tools/check_ci_gates.py} 判失败
 * （它列在 NATIVE_REQUIRED 里），「环境没备好所以静默少测」就暴露在明面上了。
 * <p>
 * 纯 Java 兜底那一遍（{@code -Djavalua.nonative}）也是零用例：那条路上
 * {@code os.setlocale} 只认 {@code nil}/{@code "C"}/{@code "POSIX"}，
 * 小数点恒为 '.'，没有可测的东西。
 */
class LocaleNumberTest {

    /** {@code os.setlocale} 的类目下标：0 = "all"（常量编号由 shim 翻） */
    private static final int LC_ALL = 0;

    /**
     * 本机装没装「用逗号当小数点」的 locale。
     * <p>
     * 探测本身就是设置：{@code setlocale(cat, name)} 成功才返回名字。试完立刻
     * 拉回 "C"——locale 是进程级的，留着会污染后面所有用例
     * （{@code Diff.freshState} 那道复位是为同样的事写的）。
     */
    private static boolean commaLocaleAvailable() {
        if (!NativeBridge.AVAILABLE) {
            return false;
        }
        boolean found = false;
        for (String name : CANDIDATES) {
            byte[] set = NativeBridge.cSetlocale(LC_ALL, name.getBytes(StandardCharsets.UTF_8));
            if (set != null) {
                found = true;
                break;
            }
        }
        NativeBridge.cSetlocale(LC_ALL, "C".getBytes(StandardCharsets.UTF_8));
        return found;
    }

    /** 脚本与 Java 侧共用这一串名义，两边探测到的必须是同一个 locale */
    private static final String[] CANDIDATES = {"pt_BR", "ptb", "pt_BR.UTF-8", "pt_BR.iso88591"};

    /** 脚本里那句守卫：任一名义上的巴西葡语 locale 能用就进正题 */
    private static final String GUARD = """
            local loc = os.setlocale("pt_BR") or os.setlocale("ptb")
                     or os.setlocale("pt_BR.UTF-8") or os.setlocale("pt_BR.iso88591")
            if not loc then print("NO_LOCALE") return end
            GLOC = loc
            print("locale", loc)
            """;

    /** 收尾一律复位：脚本漏了这一步的话，同一 fork 里后面的用例全在逗号区域跑 */
    private static final String RESTORE = """

            print("restored", os.setlocale("C"))
            print("after", tostring(tonumber("3,4")), tostring(tonumber("3.4")))
            """;

    private static final String[] SCRIPTS = {

            // 类目<b>下标</b>到 LC_* 常量的翻译。两边的编号不一样（glibc 的
            // LC_ALL 是 6、macOS 的是 0），而 {@code os.setlocale} 的返回值只看
            // 「这一类目装不装得上」——设 "C" 时全都返回 "C"，错成别的类目也
            // 看不出来，所以以前整张表错着跑都没红。这里把 GUARD 设好的
            // "all" 逐类目查回来：错一个下标就有一条不是同一份 locale；
            // 再把 numeric 单独设一次，看它是不是真的只作用到 numeric。
            """
            for _, c in ipairs({"all", "collate", "ctype", "monetary", "numeric", "time"}) do
              print("cat", c, tostring(os.setlocale(nil, c)))
            end
            print("to_c", tostring(os.setlocale("C")))
            print("set_numeric", tostring(os.setlocale(GLOC, "numeric")))
            for _, c in ipairs({"all", "collate", "ctype", "monetary", "numeric", "time"}) do
              print("after", c, tostring(os.setlocale(nil, c)))
            end
            print("radix", tostring(tonumber("3,4")), tostring(tonumber("3.4")))
            """ + RESTORE,

            // 解析：locale 的点与 '.' 两个都收（C 是「先按 locale 试，失败再把
            // 第一个 '.' 换成它重试」，所以 "1.2.3" 与 "3,4.5" 仍然出局）
            """
            local cases = {"3,4", "3.4", "1.2.3", "3,4.5", "1,2e3", "  -.4  ",
                           "+0x.41  ", "0x1,8p0", "0x1.8p0", "1e9999", "3,4x", ","}
            for i = 1, #cases do
              local c = cases[i]
              print(i, c, tostring(tonumber(c)), tostring(tonumber(c, 10)))
            end
            print("int", tostring(tonumber("42")), tostring(tonumber("0x2a")))
            """ + RESTORE,

            // 输出：tostring / 拼接 / print 全过 locale
            """
            print("a", tostring(3.0), 3.0 .. "", type(3.0))
            print("b", tostring(3.5), tostring(-0.0), tostring(0.0))
            print("c", tostring(1/3), tostring(1e100), tostring(1.5e100))
            print("d", tostring(math.maxinteger / 10), tostring(2^10))
            print("e", tostring(math.huge), tostring(-math.huge), tostring(0/0))
            print("f", math.floor(3.7), math.max(1.5, 2.5), math.min(-1.5, -2.5))
            """ + RESTORE,

            // string.format：数字转换全按 locale；%q 例外（quotefloat 把点换回 '.'）
            """
            print("g", string.format("%g %e %f %.3f %G", 3.5, 3.5, 3.5, 3.14159, 3.5))
            print("h", string.format("%a %A", 1.5, 1.5))
            print("i", string.format("%q %q %q", 1.5, 0.5, 3.0))
            print("j", string.format("%s %.2f %+.1f %#.1f", 3.5, 3.14159, 3.5, 3.0))
            print("k", string.format("%d %x %o", 7, 7, 7))
            print("l", string.format("%.20f", 1/3), string.format("%e", 1e100))
            print("m", ("%.1f"):format(2.5), ("%g"):format(0.0001))
            """ + RESTORE,

            // io.write 走 fprintf(LUA_NUMBER_FMT)，也是 locale 的；io.read("n")
            // 那两个候选字符（locale 的点与 '.'）都要收
            """
            io.write(3.5, " ", 3.0, " ", 1/3, "\\n")
            local p = io.tmpfile()
            p:write("1,5 2.5 0x1,8p0 7 3,4e1\\n")
            p:seek("set")
            print("n", tostring(p:read("n")), tostring(p:read("n")))
            print("o", tostring(p:read("n")), tostring(p:read("n")), tostring(p:read("n")))
            p:close()
            local q = io.tmpfile()
            q:write("3.4 3,4\\n")
            q:seek("set")
            print("p", tostring(q:read("n")), tostring(q:read("n")))
            q:close()
            """ + RESTORE,

            // 往返回路：写出去再读回来必须是同一个数；字符串参与算术那一组
            // 元方法（lstrlib.c 的 arith）也过同一条解析路径
            """
            local vals = {0.5, 3.0, -2.25, 1e100, 1/3, 2.5e-8}
            for i = 1, #vals do
              local s = tostring(vals[i])
              print(i, s, tostring(tonumber(s) == vals[i]))
              local h = string.format("%a", vals[i])
              print(i, h, tostring(tonumber(h) == vals[i]))
            end
            print("add", "3,4" + 1, "3.4" + 1)
            print("mul", "2,5" * 2, ("2,5"):sub(1, 3))
            """ + RESTORE,

            // 复位之后：逗号不再是小数点，'.' 又是——这条盯的是
            // 「locale 一改，数字行为立刻跟着变」这个可观测点
            """
            print("before", tostring(tonumber("3,4")))
            os.setlocale("C")
            print("c", tostring(tonumber("3,4")), tostring(tonumber("3.4")))
            print("ts", tostring(3.0), string.format("%g", 3.5))
            """ + RESTORE,

            // os.date 的 locale 名字：有 shim 时走 libc strftime，应对齐参考实现
            """
            local t = os.time{year=2024, month=6, day=15, hour=12}
            print("names", os.date("%A", t), os.date("%B", t), os.date("%a", t), os.date("%b", t))
            print("c", os.date("%c", t))
            print("xX", os.date("%x", t), os.date("%X", t))
            """ + RESTORE,
    };

    @TestFactory
    List<DynamicTest> localeRadixMatchesReference() {
        Assumptions.assumeTrue(ReferenceLua.available(), "没有参考实现");
        Assumptions.assumeTrue(commaLocaleAvailable(),
                "本机没有逗号小数点的 locale（CI 需要 locale-gen pt_BR）");
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < SCRIPTS.length; i++) {
            String src = GUARD + SCRIPTS[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest("localeRadix[" + n + "]",
                    () -> Diff.assertMatches(src)));
        }
        return tests;
    }
}
