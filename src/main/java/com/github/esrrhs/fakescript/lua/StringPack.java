package com.github.esrrhs.fakescript.lua;

/**
 * {@code string.pack} / {@code string.unpack} / {@code string.packsize} ——
 * {@code lstrlib.c} 里「pack/unpack stuff」那一段。
 * <p>
 * 单独成一个文件只是因为 StringLib 已经太长了；它属于 {@code lstrlib.c}，
 * 不是新的一层。
 *
 * <h2>这是唯一一处「结果取决于宿主平台」的标准库</h2>
 * 格式里的 {@code l} / {@code L} 是 <b>C 的 {@code long}</b>，{@code T} 是
 * {@code size_t}，{@code !} 的默认最大对齐是 {@code offsetof(struct{char; union
 * {lua_Number; double; void*; lua_Integer; long;};}, u)}，而默认字节序是
 * <b>宿主的</b>字节序。这些在 C 里由编译器和 CPU 决定。
 * <p>
 * 这里按参考二进制所在的平台（64 位 macOS / Linux，LP64 + 小端）钉死：
 * {@code long}=8、{@code size_t}=8、最大对齐=8、小端。只有这一份选择，
 * 差分比对才有意义——换到 {@code long} 是 4 字节的平台（Windows）上，
 * 参考实现自己也会给出不同的字节。
 *
 * <h2>一个必须照抄的怪异之处</h2>
 * {@code getdetails} 处理 {@code X}（按后一项对齐）时<b>会真的把后一项吃掉</b>：
 * 它调 {@code getoption} 是为了拿到后一项的对齐要求，而那次调用同时把格式串
 * 往前推了一格。于是 {@code string.packsize("Xi4")} 是 <b>0</b> 而不是 4，
 * {@code string.pack("Xi4", 7)} 得到空串。「先看一下再决定」的实现会给出 4，
 * 而且看起来更合理——这正是必须先量再写的原因。
 */
final class StringPack {

    private StringPack() {
    }

    /* ---------- 平台尺寸（见类注释） ---------- */

    private static final int SZ_CHAR = 1;
    private static final int SZ_SHORT = 2;
    private static final int SZ_INT = 4; // C 的 int
    private static final int SZ_LONG = 8; // C 的 long（LP64）
    private static final int SZ_SIZE_T = 8;
    private static final int SZ_FLOAT = 4;
    private static final int SZ_DOUBLE = 8;

    /** {@code SZINT = sizeof(lua_Integer)} */
    private static final int SZINT = 8;

    /** {@code MAXINTSIZE} */
    private static final int MAXINTSIZE = 16;

    /** {@code NB = CHAR_BIT} */
    private static final int NB = 8;

    /** {@code MC = (1 << NB) - 1} */
    private static final int MC = 0xFF;

    /** {@code MAXSIZE}：{@code size_t} 不比 {@code int} 小时就是 {@code INT_MAX} */
    private static final int MAXSIZE = Integer.MAX_VALUE;

    /** {@code LUAL_PACKPADBYTE} */
    private static final byte PAD = 0;

    private static final boolean NATIVE_LITTLE = true;
    private static final int NATIVE_MAXALIGN = 8;

    /* ---------- KOption ---------- */

    private static final int KINT = 0;
    private static final int KUINT = 1;
    private static final int KFLOAT = 2;
    private static final int KNUMBER = 3;
    private static final int KDOUBLE = 4;
    private static final int KCHAR = 5;
    private static final int KSTRING = 6;
    private static final int KZSTR = 7;
    private static final int KPADDING = 8;
    private static final int KPADDALIGN = 9;
    private static final int KNOP = 10;

    /** {@code Header} */
    private static final class Header {
        final LuaState L;
        boolean islittle;
        int maxalign;

        Header(LuaState L) {
            this.L = L;
        }
    }

    /* ---------- 格式串 ---------- */

    private static boolean digit(int c) {
        return '0' <= c && c <= '9';
    }

    /** {@code getnum}：读一串十进制数字，没有就用默认值 */
    private static int getnum(LuaState L, byte[] fmt, int[] p, int df) {
        if (p[0] >= fmt.length || !digit(fmt[p[0]] & 0xFF)) {
            return df;
        }
        int a = 0;
        do {
            a = a * 10 + ((fmt[p[0]++] & 0xFF) - '0');
        } while (p[0] < fmt.length && digit(fmt[p[0]] & 0xFF) && a <= (MAXSIZE - 9) / 10);
        return a;
    }

    /** {@code getnumlimit}：读完还要卡在 {@code [1, MAXINTSIZE]} 里 */
    private static int getnumlimit(Header h, byte[] fmt, int[] p, int df) {
        int sz = getnum(h.L, fmt, p, df);
        if (sz > MAXINTSIZE || sz <= 0) {
            throw h.L.libError("integral size (" + sz + ") out of limits [1," + MAXINTSIZE + "]");
        }
        return sz;
    }

    /**
     * {@code getoption}：读并分类下一项，顺便把尺寸写进 {@code sizeOut}。
     * <p>
     * 配置类选项（{@code < > = !} 与空格）不消耗参数，返回 {@link #KNOP}。
     */
    private static int getoption(Header h, byte[] fmt, int[] p, int[] sizeOut) {
        int opt = (p[0] < fmt.length) ? (fmt[p[0]++] & 0xFF) : 0;
        sizeOut[0] = 0;
        switch (opt) {
        case 'b':
            sizeOut[0] = SZ_CHAR;
            return KINT;
        case 'B':
            sizeOut[0] = SZ_CHAR;
            return KUINT;
        case 'h':
            sizeOut[0] = SZ_SHORT;
            return KINT;
        case 'H':
            sizeOut[0] = SZ_SHORT;
            return KUINT;
        case 'l':
            sizeOut[0] = SZ_LONG;
            return KINT;
        case 'L':
            sizeOut[0] = SZ_LONG;
            return KUINT;
        case 'j':
            sizeOut[0] = SZINT;
            return KINT;
        case 'J':
            sizeOut[0] = SZINT;
            return KUINT;
        case 'T':
            sizeOut[0] = SZ_SIZE_T;
            return KUINT;
        case 'f':
            sizeOut[0] = SZ_FLOAT;
            return KFLOAT;
        case 'n':
            sizeOut[0] = SZ_DOUBLE;
            return KNUMBER;
        case 'd':
            sizeOut[0] = SZ_DOUBLE;
            return KDOUBLE;
        case 'i':
            sizeOut[0] = getnumlimit(h, fmt, p, SZ_INT);
            return KINT;
        case 'I':
            sizeOut[0] = getnumlimit(h, fmt, p, SZ_INT);
            return KUINT;
        case 's':
            sizeOut[0] = getnumlimit(h, fmt, p, SZ_SIZE_T);
            return KSTRING;
        case 'c':
            sizeOut[0] = getnum(h.L, fmt, p, -1);
            if (sizeOut[0] == -1) {
                throw h.L.libError("missing size for format option 'c'");
            }
            return KCHAR;
        case 'z':
            return KZSTR;
        case 'x':
            sizeOut[0] = 1;
            return KPADDING;
        case 'X':
            return KPADDALIGN;
        case ' ':
            break;
        case '<':
            h.islittle = true;
            break;
        case '>':
            h.islittle = false;
            break;
        case '=':
            h.islittle = NATIVE_LITTLE;
            break;
        case '!':
            h.maxalign = getnumlimit(h, fmt, p, NATIVE_MAXALIGN);
            break;
        default:
            throw h.L.libError("invalid format option '" + (char) opt + "'");
        }
        return KNOP;
    }

    /**
     * {@code getdetails}。
     * <p>
     * {@code X} 那一段连格式串一起推进（见类注释），不要「优化」成只看不动。
     */
    private static int getdetails(Header h, long totalsize, byte[] fmt, int[] p,
            int[] psize, int[] ntoalign) {
        int opt = getoption(h, fmt, p, psize);
        int[] alignHolder = new int[] { psize[0] };
        if (opt == KPADDALIGN) {
            if (p[0] >= fmt.length || getoption(h, fmt, p, alignHolder) == KCHAR
                    || alignHolder[0] == 0) {
                throw h.L.argError(1, "invalid next option for option 'X'");
            }
        }
        int align = alignHolder[0];
        if (align <= 1 || opt == KCHAR) {
            ntoalign[0] = 0;
        } else {
            if (align > h.maxalign) {
                align = h.maxalign;
            }
            if ((align & (align - 1)) != 0) { // 不是 2 的幂
                throw h.L.argError(1, "format asks for alignment not power of 2");
            }
            ntoalign[0] = (int) (align - (totalsize & (align - 1))) & (align - 1);
        }
        return opt;
    }

    /* ---------- 字节读写 ---------- */

    /** {@code packint}：把 {@code n} 按 {@code size} 字节、指定字节序写进 {@code out} */
    private static void packint(byte[] out, int off, long n, boolean islittle, int size, boolean neg) {
        out[off + (islittle ? 0 : size - 1)] = (byte) (n & MC);
        for (int i = 1; i < size; i++) {
            n >>>= NB;
            out[off + (islittle ? i : size - 1 - i)] = (byte) (n & MC);
        }
        if (neg && size > SZINT) {
            // size 比 lua_Integer 还宽时，多出来的高位要补成符号位，不能留 0
            for (int i = SZINT; i < size; i++) {
                out[off + (islittle ? i : size - 1 - i)] = (byte) MC;
            }
        }
    }

    /** {@code unpackint}：反向操作，窄的要做符号扩展，超宽的要检查未读字节 */
    private static long unpackint(LuaState L, byte[] str, int off, boolean islittle,
            int size, boolean issigned) {
        long res = 0;
        int limit = (size <= SZINT) ? size : SZINT;
        for (int i = limit - 1; i >= 0; i--) {
            res <<= NB;
            res |= (str[off + (islittle ? i : size - 1 - i)] & 0xFF);
        }
        if (size < SZINT) {
            if (issigned) {
                long mask = 1L << (size * NB - 1);
                res = ((res ^ mask) - mask); // 符号扩展
            }
        } else if (size > SZINT) {
            int mask = (!issigned || res >= 0) ? 0 : MC;
            for (int i = limit; i < size; i++) {
                if ((str[off + (islittle ? i : size - 1 - i)] & 0xFF) != mask) {
                    throw L.libError(size + "-byte integer does not fit into Lua Integer");
                }
            }
        }
        return res;
    }

    /* ---------- string.pack ---------- */

    static int pack(LuaState L) {
        LuaString fmtStr = LuaAux.checkString(L, 1);
        byte[] fmt = fmtStr.bytes;
        Header h = new Header(L);
        h.islittle = NATIVE_LITTLE;
        h.maxalign = 1;
        int[] p = new int[1];
        long totalsize = 0;
        int arg = 1;
        /*
         * C 在这里 {@code lua_pushnil} 是「把参数与字符串缓冲隔开」的标记，
         * 但它是<b>可观测的</b>：只传了格式串时，第 2 号参数位置站的就是
         * 这个 nil，于是 {@code string.pack("c2")} 报的是
         * {@code string expected, got nil} 而不是 {@code got no value}。
         * 不压这个 nil，缺参数的报错就会少一个「nil」。
         */
        L.push(LuaValue.ofNil());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int[] size = new int[1];
        int[] ntoalign = new int[1];
        while (p[0] < fmt.length) {
            int opt = getdetails(h, totalsize, fmt, p, size, ntoalign);
            totalsize += ntoalign[0] + size[0];
            for (int i = 0; i < ntoalign[0]; i++) {
                out.write(PAD);
            }
            arg++;
            switch (opt) {
            case KINT:
            case KUINT: {
                long n = LuaAux.checkInteger(L, arg);
                boolean signed = opt == KINT;
                if (size[0] < SZINT) {
                    if (signed) {
                        long lim = 1L << (size[0] * NB - 1);
                        LuaAux.argCheck(L, -lim <= n && n < lim, arg, "integer overflow");
                    } else {
                        LuaAux.argCheck(L, (n >= 0 && n < (1L << (size[0] * NB)))
                                || (n < 0 && size[0] * NB >= 64), arg, "unsigned overflow");
                    }
                }
                byte[] b = new byte[size[0]];
                packint(b, 0, n, h.islittle, size[0], signed && n < 0);
                out.write(b, 0, b.length);
                break;
            }
            case KFLOAT:
            case KNUMBER:
            case KDOUBLE: {
                double d = LuaAux.checkNumber(L, arg);
                byte[] b;
                if (opt == KFLOAT) {
                    int bits = Float.floatToRawIntBits((float) d);
                    b = new byte[SZ_FLOAT];
                    putInt(b, 0, bits, h.islittle, SZ_FLOAT);
                } else {
                    long bits = Double.doubleToRawLongBits(d);
                    b = new byte[SZ_DOUBLE];
                    putLong(b, 0, bits, h.islittle, SZ_DOUBLE);
                }
                out.write(b, 0, b.length);
                break;
            }
            case KCHAR: {
                LuaString s = LuaAux.checkString(L, arg);
                LuaAux.argCheck(L, s.length() <= size[0], arg, "string longer than given size");
                out.write(s.bytes, 0, s.length());
                for (int i = s.length(); i < size[0]; i++) {
                    out.write(PAD);
                }
                break;
            }
            case KSTRING: {
                LuaString s = LuaAux.checkString(L, arg);
                long len = s.length();
                // size 装得下 size_t 就不用查；否则按位宽查
                LuaAux.argCheck(L, size[0] >= SZ_SIZE_T || len < (1L << (size[0] * NB)),
                        arg, "string length does not fit in given size");
                byte[] lb = new byte[size[0]];
                packint(lb, 0, len, h.islittle, size[0], false);
                out.write(lb, 0, lb.length);
                out.write(s.bytes, 0, s.length());
                totalsize += len;
                break;
            }
            case KZSTR: {
                LuaString s = LuaAux.checkString(L, arg);
                if (indexOfZero(s.bytes) >= 0) {
                    LuaAux.argCheck(L, false, arg, "string contains zeros");
                }
                out.write(s.bytes, 0, s.length());
                out.write(0);
                totalsize += s.length() + 1;
                break;
            }
            case KPADDING:
                out.write(PAD);
                arg--;
                break;
            case KPADDALIGN:
            case KNOP:
                arg--;
                break;
            default:
                break;
            }
        }
        L.push(LuaValue.ofString(LuaString.of(out.toByteArray())));
        return 1;
    }

    private static int indexOfZero(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static void putInt(byte[] b, int off, int v, boolean islittle, int size) {
        for (int j = 0; j < size; j++) {
            int shift = 8 * (islittle ? j : size - 1 - j);
            b[off + j] = (byte) ((v >>> shift) & 0xFF);
        }
    }

    private static void putLong(byte[] b, int off, long v, boolean islittle, int size) {
        for (int j = 0; j < size; j++) {
            int shift = 8 * (islittle ? j : size - 1 - j);
            b[off + j] = (byte) ((v >>> shift) & 0xFF);
        }
    }

    /* ---------- string.packsize ---------- */

    static int packsize(LuaState L) {
        byte[] fmt = LuaAux.checkString(L, 1).bytes;
        Header h = new Header(L);
        h.islittle = NATIVE_LITTLE;
        h.maxalign = 1;
        int[] p = new int[1];
        long totalsize = 0;
        int[] size = new int[1];
        int[] ntoalign = new int[1];
        while (p[0] < fmt.length) {
            int opt = getdetails(h, totalsize, fmt, p, size, ntoalign);
            LuaAux.argCheck(L, opt != KSTRING && opt != KZSTR, 1, "variable-length format");
            long used = (long) size[0] + ntoalign[0];
            LuaAux.argCheck(L, totalsize <= MAXSIZE - used, 1, "format result too large");
            totalsize += used;
        }
        L.push(LuaValue.ofInteger(totalsize));
        return 1;
    }

    /* ---------- string.unpack ---------- */

    static int unpack(LuaState L) {
        byte[] fmt = LuaAux.checkString(L, 1).bytes;
        LuaString dataStr = LuaAux.checkString(L, 2);
        byte[] data = dataStr.bytes;
        int ld = data.length;
        long pos = StringLib.posrelatI(LuaAux.optInteger(L, 3, 1), ld) - 1;
        LuaAux.argCheck(L, pos <= ld, 3, "initial position out of string");
        Header h = new Header(L);
        h.islittle = NATIVE_LITTLE;
        h.maxalign = 1;
        int[] p = new int[1];
        int n = 0;
        int[] size = new int[1];
        int[] ntoalign = new int[1];
        while (p[0] < fmt.length) {
            int opt = getdetails(h, pos, fmt, p, size, ntoalign);
            LuaAux.argCheck(L, (long) ntoalign[0] + size[0] <= ld - pos, 2, "data string too short");
            pos += ntoalign[0];
            LuaAux.checkStack(L, 2, "too many results");
            n++;
            int at = (int) pos;
            switch (opt) {
            case KINT:
            case KUINT:
                L.push(LuaValue.ofInteger(
                        unpackint(L, data, at, h.islittle, size[0], opt == KINT)));
                break;
            case KFLOAT:
                L.push(LuaValue.ofFloat(
                        Float.intBitsToFloat((int) getLong(data, at, h.islittle, SZ_FLOAT))));
                break;
            case KNUMBER:
            case KDOUBLE:
                L.push(LuaValue.ofFloat(
                        Double.longBitsToDouble(getLong(data, at, h.islittle, SZ_DOUBLE))));
                break;
            case KCHAR:
                L.push(LuaValue.ofString(LuaString.of(data, at, size[0])));
                break;
            case KSTRING: {
                long len = unpackint(L, data, at, h.islittle, size[0], false);
                LuaAux.argCheck(L, len <= ld - pos - size[0], 2, "data string too short");
                L.push(LuaValue.ofString(LuaString.of(data, at + size[0], (int) len)));
                pos += len;
                break;
            }
            case KZSTR: {
                int len = 0;
                while (at + len < ld && data[at + len] != 0) {
                    len++;
                }
                LuaAux.argCheck(L, pos + len < ld, 2, "unfinished string for format 'z'");
                L.push(LuaValue.ofString(LuaString.of(data, at, len)));
                pos += len + 1;
                break;
            }
            case KPADDING:
            case KPADDALIGN:
            case KNOP:
                n--;
                break;
            default:
                break;
            }
            pos += size[0];
        }
        L.push(LuaValue.ofInteger(pos + 1));
        return n + 1;
    }

    private static long getLong(byte[] b, int off, boolean islittle, int size) {
        long v = 0;
        for (int i = size - 1; i >= 0; i--) {
            v = (v << 8) | (b[off + (islittle ? i : size - 1 - i)] & 0xFF);
        }
        return v;
    }
}
