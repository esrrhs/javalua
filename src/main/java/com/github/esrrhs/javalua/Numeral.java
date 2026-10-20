package com.github.esrrhs.javalua;

import java.util.regex.Pattern;

/**
 * 数字字面量的解析 —— 对应 {@code lobject.c} 里的 {@code luaO_str2num}、
 * {@code l_str2int}、{@code l_str2d}、{@code lua_strx2number} 与 {@code luaO_utf8esc}。
 * <p>
 * 这一段是整个词法层最容易「差不多就行」、然后悄悄错的地方，两条规则必须精确：
 * <ol>
 *   <li><b>先按整数解析，失败才按浮点</b>（{@code luaO_str2num}）。这决定了
 *       {@code 1} 是 integer 而 {@code 1.0} 是 float，进而决定
 *       {@code math.type} 与 {@code //} 的行为；</li>
 *   <li><b>十六进制整数字面量是 integer，带 p 指数的十六进制是 float</b>。
 *       {@code 0x10} 走 {@code l_str2int} 得到整数 16，而 {@code 0x1p4}
 *       因为末尾的 'p' 让 {@code l_str2int} 失败，落到浮点路径得到 16.0。</li>
 * </ol>
 * 两个细节与 Java 直觉相反，所以不能直接用 {@code Double.parseDouble} 顶上：
 * <ul>
 *   <li>Java 的 {@code parseDouble} 接受 {@code "1d"} / {@code "1f"} 这类
 *       类型后缀，C 的 {@code strtod} 不收——而 Lua 正是靠「数字后面紧跟字母
 *       要报错」来拒绝 {@code 1d} 这种写法的；</li>
 *   <li>Java 的 {@code parseDouble} 接受 {@code "inf"} / {@code "nan"}，
 *       Lua 明确拒绝（{@code l_str2d} 里 <code>mode == 'n'</code> 直接返回失败）。</li>
 * </ul>
 */
final class Numeral {

    private Numeral() {
    }

    /** {@code luaO_hexavalue} */
    static int hexaValue(int c) {
        if (LCType.isDigit(c)) {
            return c - '0';
        }
        return (LCType.toLower(c) - 'a') + 10;
    }

    /** 最多读取的有效数字位数（避免溢出） */
    private static final int MAXSIGDIG = 30;

    /** {@code UTF8BUFFSZ} */
    public static final int UTF8BUFFSZ = 8;

    /**
     * {@code luaO_str2num}：把数字字面量转成 Lua 值。
     *
     * @return 整数或浮点值；格式错误返回 {@code null}
     */
    static LuaValue str2num(String s) {
        Long i = str2int(s);
        if (i != null) {
            return LuaValue.ofInteger(i);
        }
        Double d = str2d(s);
        if (d != null) {
            return LuaValue.ofFloat(d);
        }
        return null;
    }

    /* ---------- 整数路径 ---------- */

    private static final long MAXBY10 = Long.MAX_VALUE / 10;
    private static final int MAXLASTD = (int) (Long.MAX_VALUE % 10);

    /** {@code l_str2int}：解析十进制或十六进制整数；溢出或格式不符返回 null */
    static Long str2int(String s) {
        int p = 0;
        boolean neg = false;
        while (p < s.length() && LCType.isSpace(s.charAt(p))) {
            p++;
        }
        if (p < s.length() && s.charAt(p) == '-') {
            p++;
            neg = true;
        } else if (p < s.length() && s.charAt(p) == '+') {
            p++;
        }
        long a = 0;
        boolean empty = true;
        if (p + 1 < s.length() && s.charAt(p) == '0' && (s.charAt(p + 1) == 'x' || s.charAt(p + 1) == 'X')) {
            p += 2;
            while (p < s.length() && LCType.isXdigit(s.charAt(p))) {
                a = a * 16 + hexaValue(s.charAt(p));
                p++;
                empty = false;
            }
        } else {
            while (p < s.length() && LCType.isDigit(s.charAt(p))) {
                int d = s.charAt(p) - '0';
                /*
                 * 累加器在 C 里是 <b>{@code lua_Unsigned}</b>，比较必须按无符号做。
                 *
                 * 有符号的 {@code long} 在这里会漏判：{@code a} 一旦越过 2^63
                 * 就变成负数，{@code a >= MAXBY10} 从此恒为假，溢出检查整个失效。
                 * 命中条件是「前 19 位已经 ≥ 2^63 且还有下一位」——
                 * {@code "-92233720368547758080"}（= -10 * 2^63）正是这一类：
                 * 它按无符号一路回绕，最后恰好等于 0，于是
                 * {@code 1 + "-92233720368547758080"} 给出 <b>1</b>，
                 * 而参考实现先拒绝它、落到浮点路径，给出 -9.2233720368548e+19。
                 *
                 * 负数的下界比正数多一个（Long.MIN_VALUE），故阈值随符号放宽 1。
                 */
                if (Long.compareUnsigned(a, MAXBY10) >= 0
                        && (Long.compareUnsigned(a, MAXBY10) > 0
                                || d > MAXLASTD + (neg ? 1 : 0))) {
                    return null;
                }
                a = a * 10 + d;
                p++;
                empty = false;
            }
        }
        while (p < s.length() && LCType.isSpace(s.charAt(p))) {
            p++;
        }
        if (empty || p != s.length()) {
            return null;
        }
        return neg ? -a : a;
    }

    /* ---------- 浮点路径 ---------- */

    /**
     * {@code l_str2d}。
     * <p>
     * C 版在首次解析失败后还有一段「按当前 locale 的小数点再试一次」的回退。
     * Java 的 {@code Double.parseDouble} 只认 '.'，不存在 locale 小数点，
     * 因此那段回退在这里没有对应物——不需要也不该造一个。
     */
    static Double str2d(String s) {
        int mode = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.' || c == 'x' || c == 'X' || c == 'n' || c == 'N') {
                mode = LCType.toLower(c);
                break;
            }
        }
        if (mode == 'n') {
            return null; // 拒绝 inf / nan
        }
        return str2dLoc(s, mode);
    }

    /** 严格匹配 C strtod 接受的十进制浮点形状（不含 d/f 后缀、不含 inf/nan） */
    private static final Pattern DECIMAL = Pattern
            .compile("[+-]?((\\d+(\\.\\d*)?)|(\\.\\d+))([eE][+-]?\\d+)?");

    private static Double str2dLoc(String s, int mode) {
        if (mode == 'x') {
            double[] out = new double[1];
            int[] end = new int[1];
            strx2number(s, out, end);
            if (end[0] == 0) {
                return null; // endptr == s，一个字符都没认出来
            }
            int e = end[0];
            while (e < s.length() && LCType.isSpace(s.charAt(e))) {
                e++;
            }
            return (e == s.length()) ? out[0] : null;
        }
        /*
         * 不能用 {@code String.trim()}：Java 的 {@code trim} 去掉的是
         * 「小于等于 U+0020 的字符」，<b>连 {@code \0} 一起去掉</b>，而 C 版
         * {@code l_str2d} 只认 {@code SPACECHARS}（" \f\n\r\t\v"）。
         * 于是 {@code tonumber('1\0')} 会被这里算成 1.0——参考实现给的是
         * {@code nil}（上游 {@code math.lua} 有一整条断言对着它）。
         * 这里手工按 {@code SPACECHARS} 掐头去尾，剩下的子串里若还有别的
         * 字符（包括 {@code \0}），正则就匹配不上。
         */
        int sp = 0;
        while (sp < s.length() && LCType.isSpace(s.charAt(sp))) {
            sp++;
        }
        int se = s.length();
        while (se > sp && LCType.isSpace(s.charAt(se - 1))) {
            se--;
        }
        String t = s.substring(sp, se);
        if (!DECIMAL.matcher(t).matches()) {
            return null;
        }
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * {@code lua_strx2number}：按 C99 的 {@code strtod} 规范解析十六进制浮点。
     * <p>
     * 与标准 C99 的一处差别：<b>指数部分可以省略</b>（{@code 0x1.8} 合法）。
     * 这是 Lua 自己的实现，不是标准行为，必须照搬。
     * <p>
     * {@code end} 回填的是「已成功解析到的位置」，用来判断字面量尾部是否还有
     * 垃圾字符。指数部分格式错误时会提前返回，但 {@code end} 保持在数字部分
     * 的末尾——于是 {@code str2dLoc} 会因为「尾部还有字符」而判失败，
     * 与 C 版一致。
     */
    private static void strx2number(String s, double[] out, int[] end) {
        int p = 0;
        double r = 0.0;
        int sigdig = 0;
        int nosigdig = 0;
        int e = 0;
        boolean neg = false;
        boolean hasdot = false;
        end[0] = 0; // endptr = s
        while (p < s.length() && LCType.isSpace(s.charAt(p))) {
            p++;
        }
        if (p < s.length() && s.charAt(p) == '-') {
            p++;
            neg = true;
        } else if (p < s.length() && s.charAt(p) == '+') {
            p++;
        }
        if (!(p + 1 < s.length() && s.charAt(p) == '0' && (s.charAt(p + 1) == 'x' || s.charAt(p + 1) == 'X'))) {
            out[0] = 0.0;
            return; // 没有 0x 前缀
        }
        p += 2;
        for (; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == '.') {
                if (hasdot) {
                    break; // 第二个小数点，停
                }
                hasdot = true;
            } else if (LCType.isXdigit(c)) {
                if (sigdig == 0 && c == '0') {
                    nosigdig++; // 前导零不算有效位
                } else if (++sigdig <= MAXSIGDIG) {
                    r = (r * 16.0) + hexaValue(c);
                } else {
                    e++; // 位数过多，忽略但仍计入指数
                }
                if (hasdot) {
                    e--;
                }
            } else {
                break;
            }
        }
        if (nosigdig + sigdig == 0) {
            out[0] = 0.0;
            return; // 一个数字都没有
        }
        end[0] = p; // 到此为止是有效的
        e *= 4;
        if (p < s.length() && (s.charAt(p) == 'p' || s.charAt(p) == 'P')) {
            int exp1 = 0;
            boolean neg1 = false;
            p++; // 跳过 'p'
            if (p < s.length() && s.charAt(p) == '-') {
                p++;
                neg1 = true;
            } else if (p < s.length() && s.charAt(p) == '+') {
                p++;
            }
            if (!(p < s.length() && LCType.isDigit(s.charAt(p)))) {
                // 指数必须至少有一位数字。注意：此处不更新 end，
                // 于是调用方会因「尾部还有字符」而判失败
                out[0] = 0.0;
                return;
            }
            while (p < s.length() && LCType.isDigit(s.charAt(p))) {
                exp1 = exp1 * 10 + (s.charAt(p) - '0');
                p++;
            }
            if (neg1) {
                exp1 = -exp1;
            }
            e += exp1;
            end[0] = p;
        }
        if (neg) {
            r = -r;
        }
        out[0] = Math.scalb(r, e);
    }

    /* ---------- UTF-8 转义 ---------- */

    /**
     * {@code luaO_utf8esc}：把码点编码成 UTF-8，从缓冲区<b>尾部往前</b>填。
     *
     * @return 写入的字节数 {@code n}，字节位于 {@code buff[len-n .. len-1]}
     */
    static int utf8esc(long x, byte[] buff) {
        int len = buff.length;
        int n = 1;
        if (x < 0x80) {
            buff[len - 1] = (byte) x;
        } else {
            int mfb = 0x3f; // 首字节还能放下的最大值
            do {
                buff[len - (n++)] = (byte) (0x80 | (x & 0x3f));
                x >>= 6;
                mfb >>= 1;
            } while (x > mfb);
            buff[len - n] = (byte) ((~mfb << 1) | x);
        }
        return n;
    }
}
