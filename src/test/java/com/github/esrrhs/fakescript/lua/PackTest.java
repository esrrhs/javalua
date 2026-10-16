package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * {@code string.pack} / {@code string.unpack} / {@code string.packsize}
 * 的差分用例。
 * <p>
 * 这一组用例与其它库有两点不同，写在前面：
 * <ul>
 *   <li><b>结果是平台相关的。</b>{@code l} / {@code L} 是 C 的 {@code long}、
 *       {@code T} 是 {@code size_t}、{@code !} 的默认对齐取
 *       {@code LUAI_MAXALIGN} 的偏移、默认字节序取宿主的。这些在
 *       {@code StringPack} 里按参考二进制所在的 LP64 + 小端平台钉死了。</li>
 *   <li><b>{@code X} 会吃掉后一项。</b>{@code string.packsize("Xi4")} 是
 *       {@code 0} 而不是 {@code 4}。看着像 bug，但参考实现就是这样，
 *       而且这是最容易「顺手改对」的一处。</li>
 * </ul>
 * <p>
 * 字节一律用 {@code string.byte} / {@code string.char} 看，不直接 {@code print}
 * 二进制——{@code print} 出来的原始字节在两边都是不可比的东西（终端编码、
 * 缓冲），而差分比的是 stdout 的<b>文本</b>。
 */
class PackTest {

    @BeforeAll
    static void needReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN");
    }

    @TestFactory
    List<DynamicTest> packScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "int", INT);
        add(tests, "float", FLOAT);
        add(tests, "str", STR);
        add(tests, "align", ALIGN);
        add(tests, "wide", WIDE);
        add(tests, "err", ERRORS);
        add(tests, "keys", KEYS);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    /* ==================== 整数 ==================== */

    private static final String[] INT = {
            """
            local function hex(s) local t = {} for i = 1, #s do t[i] = string.format("%02x", string.byte(s, i)) end return table.concat(t, " ") end
            print(hex(string.pack("i4", 7)))
            print(hex(string.pack(">i4", 7)))
            print(hex(string.pack("<i4", 7)))
            print(hex(string.pack("i4", -1)))
            print(hex(string.pack("I4", -1)))
            print(hex(string.pack("i1", -1)), hex(string.pack("i1", 127)))
            print(hex(string.pack("i2", -2)))
            print(hex(string.pack("i8", -2)))
            print(hex(string.pack("i16", -2)))
            print(hex(string.pack("j", -2)), hex(string.pack("J", -2)))
            print(hex(string.pack("bBhHlL", -1, 255, -2, 65534, -3, 3)))
            """,

            """
            -- 往返：unpack 回来应当等于原值，且类型是整数
            for _, v in ipairs{0, 1, -1, 127, -128, 32767, -32768, 2^31 - 1, -2^31, 2^62, -2^62} do
              local s = string.pack("j", v)
              local r, pos = string.unpack("j", s)
              print(v, r, r == v, math.type(r), pos, #s)
            end
            """,

            """
            -- 窄类型的符号扩展与无符号解释
            print(string.unpack("i1", "\\255"))
            print(string.unpack("i2", "\\255\\255"))
            print(math.type(select(1, string.unpack("i1", "\\255"))))
            print(string.unpack("i16", string.rep("\\255", 16)))
            print(string.unpack("I16", string.rep("\\255", 16)))
            print(string.unpack("i16", "\\1" .. string.rep("\\255", 15)))
            """,

            """
            -- 端序切换是<b>粘</b>的：一次设定，之后一直有效
            print(string.unpack("i2I2", "\\1\\0\\2\\0"))
            print(string.unpack(">i2I2", "\\0\\1\\0\\2"))
            print(string.unpack("<i2>i2", "\\1\\0\\0\\2"))
            print(string.unpack("=i2", "\\1\\0"))
            """, };

    /* ==================== 浮点 ==================== */

    private static final String[] FLOAT = {
            """
            local function hex(s) local t = {} for i = 1, #s do t[i] = string.format("%02x", string.byte(s, i)) end return table.concat(t, " ") end
            print(hex(string.pack("f", 1.5)), #string.pack("f", 1.5))
            print(hex(string.pack("d", 1.5)), #string.pack("d", 1.5))
            print(hex(string.pack("n", 1.5)))
            print(hex(string.pack(">d", 1.5)))
            print(hex(string.pack("<f", 1.5)))
            print(hex(string.pack("d", 0)))
            print(hex(string.pack("d", -0.0)))
            print(hex(string.pack("d", 1/0)))
            print(hex(string.pack("d", 0/0)))
            """,

            """
            -- 'f' 是单精度：装不下的精度会丢
            print(string.unpack("f", string.pack("f", 0.1)))
            print(string.unpack("d", string.pack("d", 0.1)) == 0.1)
            print(string.unpack("n", string.pack("n", math.pi)) == math.pi)
            print(string.unpack("f", string.pack(">f", 0.5)))
            print(string.unpack(">f", string.pack("f", 0.5)))
            print(math.type(select(1, string.unpack("d", string.pack("d", 1.5)))))
            """,

            """
            -- 整数也能喂给浮点格式（luaL_checknumber 会转）
            print(string.unpack("d", string.pack("d", 3)))
            print(string.unpack("f", string.pack("f", 3)))
            print(pcall(string.pack, "d", "1.5"))
            print(string.unpack("d", string.pack("d", "1.5")))
            """, };

    /* ==================== 字符串 ==================== */

    private static final String[] STR = {
            """
            local function q(s) return (s:gsub(".", function(c) return string.format("\\\\%03d", string.byte(c)) end)) end
            print(q(string.pack("c0", "")))
            print(q(string.pack("c3", "ab")))
            print(q(string.pack("c3", "abc")))
            print(q(string.pack("z", "ab")))
            print(q(string.pack("s1", "ab")))
            print(q(string.pack("s4", "ab")))
            print(q(string.pack("s8", "ab")))
            print(q(string.pack(">s1", "ab")))
            """,

            """
            -- 往返
            print(string.unpack("c3", string.pack("c3", "ab")))
            print(string.unpack("z", string.pack("z", "ab")))
            print(string.unpack("s1", string.pack("s1", "abc")))
            print(string.unpack("s8", string.pack("s8", "abc")))
            print(select("#", string.unpack("s1", string.pack("s1", "abc"))))
            print(string.unpack("c0z", "\\0"))
            """,

            """
            -- 后面还有数据时，最后那个返回值是下一次的位置
            local s = string.pack("i4i4", 1, 2)
            print(string.unpack("i4", s))
            print(string.unpack("i4", s, 5))
            print(select("#", string.unpack("i4i4", s)))
            print(string.unpack("c2c2", "abcd"))
            print(string.unpack("i4", s, -5))
            print(string.unpack("i4", s, -1))
            """,

            """
            -- 空格式与配置类选项
            print(#string.pack(""))
            print(string.pack(""))
            print(string.unpack("", "abc"))
            print(#string.pack("   ", 1))
            print(#string.pack("x"))
            print(#string.pack("xxx"))
            print(string.packsize("xi4x"))
            """, };

    /* ==================== 对齐 ==================== */

    private static final String[] ALIGN = {
            """
            print(string.packsize("i4"))
            print(string.packsize("d"))
            print(string.packsize("i1d"))
            print(string.packsize("!1i1d"))
            print(string.packsize("!4i4d"))
            print(string.packsize("!8i4d"))
            print(string.packsize("i4Xi4"))
            print(#string.pack("i1d", 1, 2.5))
            print(#string.pack("!1i1d", 1, 2.5))
            """,

            """
            -- 'X' 会吃掉后一项：这一条是本组最反直觉的
            print(string.packsize("Xi4"))
            print(#string.pack("Xi4", 7))
            print(#string.pack("i1Xi4", 7))
            print(pcall(string.packsize, "X"))
            print(pcall(string.packsize, "Xc3"))
            print(pcall(string.packsize, "Xz"))
            """,

            """
            -- 对齐只按「当前偏移」算，与前面有几项无关
            local s = string.pack("i1i1d", 1, 2, 3.5)
            print(#s, string.unpack("i1i1d", s))
            print(string.packsize("!4i1d"))
            print(#string.pack("!4i1d", 1, 2.5))
            print(#string.pack(">i1d", 1, 2.5))
            """, };

    /* ==================== 宽整数 ==================== */

    private static final String[] WIDE = {
            """
            -- size 比 lua_Integer 还宽：pack 要把多出来的高位补成符号位，
            -- unpack 要检查那些没读进来的字节是否也是符号位
            local function hex(s) local t = {} for i = 1, #s do t[i] = string.format("%02x", string.byte(s, i)) end return table.concat(t, " ") end
            print(hex(string.pack("i9", -1)))
            print(hex(string.pack("i9", 1)))
            print(hex(string.pack("i16", -1)))
            print(hex(string.pack("i16", 1)))
            print(hex(string.pack("I16", -1)))
            print(string.unpack("i9", string.pack("i9", -1)))
            print(string.unpack("i12", string.rep("\\255", 12)))
            print(string.unpack("i16", string.rep("\\255", 16)))
            """,

            """
            -- 未读的高位字节不是符号位 → 装不进 Lua 整数
            print(pcall(string.unpack, "i9", "\\1" .. string.rep("\\255", 8)))
            print(pcall(string.unpack, "i9", string.rep("\\255", 8) .. "\\0"))
            print(pcall(string.unpack, "I9", string.rep("\\255", 8) .. "\\1"))
            print(string.unpack("I9", string.rep("\\255", 9)))
            """,

            """
            local function hex(s) local t = {} for i = 1, #s do t[i] = string.format("%02x", string.byte(s, i)) end return table.concat(t, " ") end
            print(hex(string.pack("T", 7)), hex(string.pack("J", 7)))
            print(string.unpack("T", string.pack("T", 7)))
            print(string.unpack("J", string.pack("J", -1)))
            print(hex(string.pack(">I16", 1)))
            print(string.unpack(">I16", string.pack(">I16", 1)))
            print(hex(string.pack("l", -1)), hex(string.pack("L", -1)))
            """, };

    /* ==================== 报错 ==================== */

    private static final String[] ERRORS = {
            """
            print(pcall(string.pack, "i4", 1.5))
            print(pcall(string.pack, "i4", 2^53))
            print(pcall(string.pack, "i1", 200))
            print(pcall(string.pack, "I1", -1))
            print(pcall(string.pack, "i17", 1))
            print(pcall(string.pack, "i0", 1))
            print(pcall(string.pack, "Q", 1))
            print(pcall(string.pack, "!0i4", 1))
            print(pcall(string.pack, "!17i4", 1))
            print(pcall(string.pack, 42, 1))
            """,

            """
            print(pcall(string.pack, "c", "x"))
            print(pcall(string.pack, "c2", "xyz"))
            print(pcall(string.pack, "s1", string.rep("a", 300)))
            print(pcall(string.pack, "z", "a\\0b"))
            print(pcall(string.pack, "s1", 42))
            print(pcall(string.pack, "c2"))
            print(pcall(string.packsize, "s1"))
            print(pcall(string.packsize, "z"))
            print(pcall(string.packsize, 42))
            """,

            """
            print(pcall(string.unpack, "i4", "abc"))
            print(pcall(string.unpack, "i4", "abcd", 5))
            print(pcall(string.unpack, "i4", "abcd", 100))
            print(pcall(string.unpack, "z", "abc"))
            print(pcall(string.unpack, "s1", "\\3ab"))
            print(pcall(string.unpack, "!3i4", "\\1\\0\\0\\0"))
            print(pcall(string.unpack, "i4"))
            print(pcall(string.unpack, "i4", 42))
            print(pcall(string.unpack, "i4", "abcd", "x"))
            """, };

    /* ==================== 命名空间 ==================== */

    /**
     * 枚举整个 {@code string} 表的键。
     * <p>
     * 看着琐碎，但这是「漏了一个函数」唯一的自动检查——之前
     * {@code dump} 只占着名字不干活，就是为了保住这一个比对。现在
     * {@code pack} / {@code packsize} / {@code unpack} 齐了，键集合应当
     * 与参考实现完全一致。
     */
    private static final String[] KEYS = {
            """
            local ks = {}
            for k in pairs(string) do ks[#ks + 1] = k end
            table.sort(ks)
            print(#ks, table.concat(ks, " "))
            """, };
}
