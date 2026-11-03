package com.github.esrrhs.javalua;

/**
 * UTF-8 库 —— 对应 {@code lutf8lib.c}。
 * <p>
 * Lua 字符串是字节串，UTF-8 只是<b>在其之上</b>的一层解释：{@code utf8.len}
 * 数的是字符不是字节，{@code #s} 仍然数字节。这个区分是刻意的，写成
 * 「字符串内部就是 UTF-8」会把含 {@code \xFF} 的串变成不可用。
 * <p>
 * 所有解码都自己按字节做，<b>不</b>交给 Java 的 {@code String}：
 * 一方面要能识别「不合法序列」并返回 {@code nil + 位置}（Java 的解码器只会
 * 替换成 U+FFFD）。Lua 5.3.4 无 {@code lax} 模式，码点上限为 U+10FFFF。
 */
public final class Utf8Lib {

    private Utf8Lib() {
    }

    private static final long MAXUNICODE = 0x10FFFFL;
    private static final String MSG_INVALID = "invalid UTF-8 code";

    /** {@code iscont} */
    private static boolean isCont(int c) {
        return (c & 0xC0) == 0x80;
    }

    public static void open(LuaState L) {
        LuaTable u = new LuaTable();
        LuaAux.setFn(u, "offset", Utf8Lib::byteOffset);
        LuaAux.setFn(u, "codepoint", Utf8Lib::codePoint);
        LuaAux.setFn(u, "char", Utf8Lib::utfChar);
        LuaAux.setFn(u, "len", Utf8Lib::utfLen);
        LuaAux.setFn(u, "codes", Utf8Lib::iterCodes);
        u.set("charpattern", LuaValue.ofString(charPattern()));
        LuaAux.registerLib(L, "utf8", u);
    }

    /**
     * {@code UTF8PATT}（Lua 5.3.4）：{@code [\0-\x7F\xC2-\xF4][\x80-\xBF]*}。
     * 上限 {@code \xF4} 对应 U+10FFFF；5.4 才抬到 {@code \xFD}。
     */
    public static LuaString charPattern() {
        return LuaString.of(new byte[] { '[', 0x00, '-', 0x7F, (byte) 0xC2, '-', (byte) 0xF4, ']',
                '[', (byte) 0x80, '-', (byte) 0xBF, ']', '*' });
    }

    /* ==================== 解码 ==================== */

    /**
     * {@code utf8_decode}：从 {@code i} 解出一个码点。
     *
     * @return 下一个字符的起始下标，{@code -1} 表示序列不合法
     */
    /**
     * {@code utf8_decode}（Lua 5.3.4）：无 lax；最多 4 字节；{@code res > MAXUNICODE}
     * 即非法。limits 与上游 {@code lutf8lib.c} 一致。
     */
    private static int decode(byte[] s, int i, long[] val) {
        long[] limits = { 0xFFL, 0x7FL, 0x7FFL, 0xFFFFL };
        int c = s[i] & 0xFF;
        long res = 0;
        if (c < 0x80) {
            res = c;
        } else {
            int count = 0;
            int cc = c;
            for (; (cc & 0x40) != 0; cc <<= 1) {
                if (i + count + 1 >= s.length) {
                    return -1;
                }
                int next = s[i + count + 1] & 0xFF;
                if (!isCont(next)) {
                    return -1;
                }
                res = (res << 6) | (next & 0x3F);
                count++;
            }
            res |= ((long) (cc & 0x7F)) << (count * 5);
            if (count > 3 || res > MAXUNICODE || res <= limits[count]) {
                return -1;
            }
            i += count;
        }
        if (val != null) {
            val[0] = res;
        }
        return i + 1;
    }

    /** {@code luaO_utf8esc}：把一个码点编成 UTF-8 字节 */
    private static byte[] utf8Esc(long x) {
        byte[] out = new byte[8];
        int n = 1;
        if (x < 0x80) {
            out[8 - 1] = (byte) x;
        } else {
            long mfb = 0x3F;
            do {
                out[8 - (n++)] = (byte) (0x80 | (x & 0x3F));
                x >>= 6;
                mfb >>= 1;
            } while (x > mfb);
            out[8 - n] = (byte) (((~mfb) << 1) | x);
        }
        byte[] r = new byte[n];
        System.arraycopy(out, 8 - n, r, 0, n);
        return r;
    }

    /* ==================== 各函数 ==================== */

    /** {@code u_posrelat} */
    private static long uPosRelat(long pos, int len) {
        if (pos >= 0) {
            return pos;
        }
        if (pos < -(long) len) {
            return 0;
        }
        return len + pos + 1;
    }

    /** {@code utflen} */
    private static int utfLen(LuaState L) {
        LuaString str = LuaAux.checkString(L, 1);
        byte[] s = str.bytes;
        int len = s.length;
        long posi = uPosRelat(LuaAux.optInteger(L, 2, 1), len);
        long posj = uPosRelat(LuaAux.optInteger(L, 3, -1), len);
        if (!(1 <= posi && --posi <= len)) {
            throw L.argError(2, "initial position out of string");
        }
        if (!(--posj < len)) {
            throw L.argError(3, "final position out of string");
        }
        long n = 0;
        int p = (int) posi;
        int pj = (int) posj;
        while (p <= pj) {
            int next = decode(s, p, null);
            if (next < 0) {
                L.push(LuaValue.ofNil());
                L.push(LuaValue.ofInteger(p + 1));
                return 2;
            }
            p = next;
            n++;
        }
        L.push(LuaValue.ofInteger(n));
        return 1;
    }

    /** {@code codepoint} */
    private static int codePoint(LuaState L) {
        LuaString str = LuaAux.checkString(L, 1);
        byte[] s = str.bytes;
        int len = s.length;
        long posi = uPosRelat(LuaAux.optInteger(L, 2, 1), len);
        long pose = uPosRelat(LuaAux.optInteger(L, 3, posi), len);
        if (posi < 1) {
            throw L.argError(2, "out of range");
        }
        if (pose > len) {
            throw L.argError(3, "out of range");
        }
        if (posi > pose) {
            return 0;
        }
        if (pose - posi >= Integer.MAX_VALUE) {
            throw L.libError("string slice too long");
        }
        int n = (int) (pose - posi) + 1;
        LuaAux.checkStack(L, n, "string slice too long");
        int count = 0;
        int p = (int) (posi - 1);
        int end = (int) pose;
        while (p < end) {
            long[] code = new long[1];
            int next = decode(s, p, code);
            if (next < 0) {
                throw L.libError(MSG_INVALID);
            }
            L.push(LuaValue.ofInteger(code[0]));
            count++;
            p = next;
        }
        return count;
    }

    /** {@code utfchar} */
    private static int utfChar(LuaState L) {
        int n = L.getTop();
        // 范围检查必须按<b>无符号</b>比：C 里 code 是 (lua_Unsigned) 转出来的，
        // utf8.char(-1) 于是变成 0xFFFF... 而报错。按有符号比 -1 会「通过」，
        // 结果吐出一个 \xBF 开头的垃圾字节。
        if (n == 1) {
            long code = LuaAux.checkInteger(L, 1);
            if (code < 0 || code > MAXUNICODE) {
                throw L.argError(1, "value out of range");
            }
            L.push(LuaValue.ofString(LuaString.of(utf8Esc(code))));
            return 1;
        }
        LuaAux.Buf b = new LuaAux.Buf();
        for (int i = 1; i <= n; i++) {
            long code = LuaAux.checkInteger(L, i);
            if (code < 0 || code > MAXUNICODE) {
                throw L.argError(i, "value out of range");
            }
            byte[] e = utf8Esc(code);
            b.addBytes(e, 0, e.length);
        }
        L.push(LuaValue.ofString(b.result()));
        return 1;
    }

    /**
     * 按 C 的 {@code iscontp(s + i)} 读一个字节：越界（正好在串尾）时读到的是
     * 结尾那个 {@code '\0'}，所以返回 0 而不是抛 {@code ArrayIndexOutOfBounds}。
     */
    private static int byteAt(byte[] s, int i) {
        return (i < s.length) ? (s[i] & 0xFF) : 0;
    }

    /** {@code byteoffset} */
    private static int byteOffset(LuaState L) {
        LuaString str = LuaAux.checkString(L, 1);
        byte[] s = str.bytes;
        int len = s.length;
        long n = LuaAux.checkInteger(L, 2);
        long def = (n >= 0) ? 1 : len + 1;
        long posi = uPosRelat(LuaAux.optInteger(L, 3, def), len);
        if (!(1 <= posi && --posi <= len)) {
            throw L.argError(3, "position out of range");
        }
        int p = (int) posi;
        // 下标允许等于 len：C 里那个位置是字符串结尾的 '\0'（不是续字节），
        // 越界检查必须靠「当作 0 来读」而不是真的去索引数组
        if (n == 0) {
            while (p > 0 && isCont(byteAt(s, p))) {
                p--;
            }
        } else {
            if (isCont(byteAt(s, p))) {
                throw L.libError("initial position is a continuation byte");
            }
            if (n < 0) {
                while (n < 0 && p > 0) {
                    do {
                        p--;
                    } while (p > 0 && isCont(byteAt(s, p)));
                    n++;
                }
            } else {
                n--;
                while (n > 0 && p < len) {
                    do {
                        p++;
                    } while (isCont(byteAt(s, p))); // 结尾的 '\0' 会让它停下
                    n--;
                }
            }
        }
        if (n == 0) {
            L.push(LuaValue.ofInteger(p + 1));
        } else {
            L.push(LuaValue.ofNil());
        }
        return 1;
    }

    /** {@code iter_aux}（Lua 5.3.4：无 lax） */
    private static int iterAux(LuaState L) {
        LuaString str = LuaAux.checkString(L, 1);
        byte[] s = str.bytes;
        long n = L.arg1(2).asInteger() - 1;
        if (n < 0) {
            n = 0;
        } else if (n < s.length) {
            n++;
            while (n < s.length && isCont(s[(int) n] & 0xFF)) {
                n++;
            }
        }
        if (n >= s.length) {
            return 0;
        }
        int i = (int) n;
        long[] code = new long[1];
        int next = decode(s, i, code);
        if (next < 0 || (next < s.length && isCont(s[next] & 0xFF))) {
            throw L.libError(MSG_INVALID);
        }
        L.push(LuaValue.ofInteger(i + 1));
        L.push(LuaValue.ofInteger(code[0]));
        return 2;
    }

    /** {@code iter_codes} */
    private static int iterCodes(LuaState L) {
        LuaAux.checkString(L, 1);
        L.pushFunction(Utf8Lib::iterAux);
        L.push(L.arg1(1));
        L.push(LuaValue.ofInteger(0));
        return 3;
    }
}
