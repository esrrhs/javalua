package com.github.esrrhs.fakescript.lua;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 字符串库 —— 对应 {@code lstrlib.c}。
 * <p>
 * 这是阶段 7 里最大的一块，也是「不作废任何一行」最容易翻车的一块：
 * 模式匹配、{@code string.format} 都是<b>逐字节</b>算法，而 Java 的
 * {@code String} 是 UTF-16 并且拒绝非法序列。所以这里没有一处用 Java
 * String 处理 Lua 字符串内容——全部走 {@code byte[]}。
 * <p>
 * 三个刻意的缺口，都留到后面的阶段补（见 README 的「已知差异」）：
 * <ul>
 *   <li>{@code string.dump}：要等阶段 8 的二进制 chunk 导出（{@code ldump.c}）；
 *       现在一律报「unable to dump given function」。</li>
 *   <li>{@code string.pack} / {@code unpack} / {@code packsize}：约五百行机械代码，
 *       尚未移植。</li>
 *   <li>{@code string.format} 的 {@code %p}：打印的是 Java 的 identity hash，
 *        不可能是 C 的指针值。</li>
 * </ul>
 */
public final class StringLib {

    private StringLib() {
    }

    /** {@code LUA_MAXCAPTURES} */
    private static final int MAXCAPTURES = 32;
    /** {@code MAXCCALLS}：{@code match} 的递归深度上限 */
    private static final int MAXCCALLS = 200;
    private static final int L_ESC = '%';
    private static final String SPECIALS = "^$*+?.([%-";
    /** {@code MAXSIZE}：C 版是 {@code INT_MAX}（size_t 比 int 宽时的取值） */
    private static final long MAXSIZE = Integer.MAX_VALUE;
    /** 非 {@code %f} 的格式项都放得下（见 {@code lstrlib.c} 的注释） */
    private static final int MAX_ITEM = 120;
    /** {@code MAX_ITEMF = 110 + MAX_10_EXP} */
    private static final int MAX_ITEMF = 110 + 308;
    /** {@code MAX_FORMAT} */
    private static final int MAX_FORMAT = 32;

    private static final int CAP_UNFINISHED = -1;
    private static final int CAP_POSITION = -2;

    /* ==================== 开库 ==================== */

    /**
     * {@code luaopen_string}（外加 {@code createmetatable} 里那句
     * {@code metatable.__index = string}）。
     * <p>
     * 必须在 {@link BaseLib#open} 之后跑：字符串元表是 {@code BaseLib} 建的，
     * 这里只往里补 {@code __index}。少了这一句 {@code ("x"):upper()} 就会报
     * 「attempt to index a string value」，而真实脚本里方法式调用是常态。
     */
    public static void open(LuaState L) {
        LuaTable s = new LuaTable();
        LuaAux.setFn(s, "byte", StringLib::byteFn);
        LuaAux.setFn(s, "char", StringLib::charFn);
        LuaAux.setFn(s, "dump", StringLib::dump);
        LuaAux.setFn(s, "find", StringLib::find);
        LuaAux.setFn(s, "format", StringLib::format);
        LuaAux.setFn(s, "gmatch", StringLib::gmatch);
        LuaAux.setFn(s, "gsub", StringLib::gsub);
        LuaAux.setFn(s, "len", StringLib::len);
        LuaAux.setFn(s, "lower", StringLib::lower);
        LuaAux.setFn(s, "match", StringLib::matchFn);
        LuaAux.setFn(s, "pack", StringPack::pack);
        LuaAux.setFn(s, "packsize", StringPack::packsize);
        LuaAux.setFn(s, "rep", StringLib::rep);
        LuaAux.setFn(s, "reverse", StringLib::reverse);
        LuaAux.setFn(s, "sub", StringLib::sub);
        LuaAux.setFn(s, "unpack", StringPack::unpack);
        LuaAux.setFn(s, "upper", StringLib::upper);
        LuaAux.registerLib(L, "string", s);

        LuaTable mt = L.typeMetatable(Lua.TSTRING);
        if (mt != null) {
            mt.set("__index", LuaValue.ofTable(s));
        }
    }

    /* ==================== 简单函数 ==================== */

    /** {@code str_len} */
    private static int len(LuaState L) {
        L.push(LuaValue.ofInteger(LuaAux.checkString(L, 1).length()));
        return 1;
    }

    /**
     * {@code lstrlib.c: posrelatI}：把相对位置翻成正的，并夹到 {@code [1, ...]}。
     * <p>
     * 返回 {@code long} 而不是 {@code int}：{@code ("abc"):sub(2^40)} 这种
     * 越界下标在 C 里是 {@code size_t}，截断成 int 会把「很远」变成「很近」，
     * 进而切出一个根本不该存在的子串。
     */
    static long posrelatI(long pos, int len) {
        if (pos > 0) {
            return pos;
        }
        if (pos == 0) {
            return 1;
        }
        if (pos < -(long) len) {
            return 1;
        }
        return len + pos + 1;
    }

    /** {@code getendpos}：可选的结束位置，默认 {@code def}，夹到 {@code [0, len]} */
    private static long getEndPos(LuaState L, int arg, long def, int len) {
        long pos = LuaAux.optInteger(L, arg, def);
        if (pos > (long) len) {
            return len;
        }
        if (pos >= 0) {
            return pos;
        }
        if (pos < -(long) len) {
            return 0;
        }
        return len + pos + 1;
    }

    /** {@code str_sub} */
    private static int sub(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        int l = s.length();
        long start = posrelatI(LuaAux.checkInteger(L, 2), l);
        long end = getEndPos(L, 3, -1, l);
        if (start <= end) {
            L.push(LuaValue.ofString(s.substring((int) (start - 1), (int) end)));
        } else {
            L.push(LuaValue.ofString(LuaString.EMPTY));
        }
        return 1;
    }

    /** {@code str_reverse} */
    private static int reverse(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        byte[] b = s.bytes;
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[b.length - i - 1];
        }
        L.push(LuaValue.ofString(L.newString(out)));
        return 1;
    }

    /** {@code str_lower}：按 ASCII 转，不用 Java 的 {@code toLowerCase} */
    private static int lower(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        byte[] out = new byte[s.length()];
        for (int i = 0; i < out.length; i++) {
            int c = s.byteAt(i);
            out[i] = (byte) ((c >= 'A' && c <= 'Z') ? c + 32 : c);
        }
        L.push(LuaValue.ofString(L.newString(out)));
        return 1;
    }

    private static int upper(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        byte[] out = new byte[s.length()];
        for (int i = 0; i < out.length; i++) {
            int c = s.byteAt(i);
            out[i] = (byte) ((c >= 'a' && c <= 'z') ? c - 32 : c);
        }
        L.push(LuaValue.ofString(L.newString(out)));
        return 1;
    }

    /**
     * {@code str_rep}。
     * <p>
     * 溢出检查照抄 C：{@code l + lsep > MAXSIZE / n}。写成
     * {@code n * (l + lsep) > MAXSIZE} 会在 {@code n} 很大时自己先溢出——
     * 正是这里要防的那件事。
     */
    private static int rep(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        long n = LuaAux.checkInteger(L, 2);
        LuaString sep = LuaAux.optString(L, 3, LuaString.EMPTY);
        int l = s.length();
        int lsep = sep.length();
        if (n <= 0) {
            L.push(LuaValue.ofString(LuaString.EMPTY));
            return 1;
        }
        if ((long) l + lsep > MAXSIZE / n) {
            throw L.libError("resulting string too large");
        }
        long total = (long) n * l + (long) (n - 1) * lsep;
        byte[] out = new byte[(int) total];
        int p = 0;
        while (n-- > 1) {
            System.arraycopy(s.bytes, 0, out, p, l);
            p += l;
            if (lsep > 0) {
                System.arraycopy(sep.bytes, 0, out, p, lsep);
                p += lsep;
            }
        }
        System.arraycopy(s.bytes, 0, out, p, l);
        L.push(LuaValue.ofString(L.newString(out)));
        return 1;
    }

    /** {@code str_byte}：一次可以返回很多个字节值 */
    private static int byteFn(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        int l = s.length();
        long pi = LuaAux.optInteger(L, 2, 1);
        long posi = posrelatI(pi, l);
        long pose = getEndPos(L, 3, pi, l);
        if (posi > pose) {
            return 0; // 空区间，一个值也不返回
        }
        if (pose - posi >= Integer.MAX_VALUE) {
            throw L.libError("string slice too long");
        }
        int n = (int) (pose - posi) + 1;
        LuaAux.checkStack(L, n, "string slice too long");
        for (int i = 0; i < n; i++) {
            L.push(LuaValue.ofInteger(s.byteAt((int) (posi + i - 1))));
        }
        return n;
    }

    /**
     * {@code str_char}。
     * <p>
     * 范围检查是 {@code 0..255}，负数一样要拦：C 版先把值转成
     * {@code lua_Unsigned} 再比 {@code UCHAR_MAX}，负数会变成一个巨大的无符号数，
     * 于是 {@code string.char(-1)} 也是「value out of range」。
     */
    private static int charFn(LuaState L) {
        int n = L.argCount();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            long c = LuaAux.checkInteger(L, i + 1);
            if (c < 0 || c > 255) {
                throw L.argError(i + 1, "value out of range");
            }
            out[i] = (byte) c;
        }
        L.push(LuaValue.ofString(L.newString(out)));
        return 1;
    }

    /**
     * {@code str_dump}：把一个 Lua 函数导出成二进制 chunk。
     * <p>
     * 只接受 <b>Lua 函数</b>：C 函数没有 {@code Proto} 可 dump，报的是
     * 「Lua function expected」而不是笼统的「function expected」。
     * <p>
     * {@code strip} 为真时剥掉调试信息（行号、局部变量名、upvalue 名、源名）。
     * 剥完之后仍能执行，只是报错时没有位置前缀。
     */
    private static int dump(LuaState L) {
        /*
         * 两步检查，顺序不能换，消息也不同：
         * {@code luaL_checktype} 先挡「根本不是函数」——报
         * {@code function expected, got X}（连「没给参数」也是这一条）；
         * 然后 {@code lua_dump} 对非 Lua 函数直接返回 1，于是 C 函数落到
         * {@code luaL_error("unable to dump given function")}。
         * 合成一步会把 C 函数也报成「Lua function expected」。
         */
        LuaAux.checkType(L, 1, Lua.TFUNCTION);
        LuaValue v = L.arg1(1);
        boolean strip = !L.isNoneArg(2) && L.arg1(2).toBoolean();
        if (v.tt != Lua.VLIF) {
            throw L.libError("unable to dump given function");
        }
        L.setTop(1);
        LClosure cl = (LClosure) v.o;
        byte[] bytes = Dump.dumpBytes(cl.p, strip);
        L.push(LuaValue.ofString(L.newString(bytes)));
        return 1;
    }

    /* ==================== 模式匹配 ==================== */

    /**
     * {@code MatchState}。
     * <p>
     * C 版用 {@code const char*} 在源串与模式串里游走，这里换成<b>下标</b>：
     * {@code s} / {@code p} 是偏移量，{@code -1} 表示 C 里的 {@code NULL}（匹配失败）。
     * <p>
     * 越界读要按 C 的语义返回 0：Lua 字符串在 C 里总有一个结尾的 {@code \0}，
     * 于是 {@code *s} 在 {@code s == src_end} 时读到的是 0 而不是越界。
     * {@code %b\0\0} 这类以 NUL 为分隔符的模式正是靠这个成立的。
     */
    private static final class MatchState {
        LuaState L;
        byte[] src;
        int srcEnd;
        byte[] pat;
        int pStart;
        int pEnd;
        int matchdepth;
        int level;
        final int[] capInit = new int[MAXCAPTURES];
        final int[] capLen = new int[MAXCAPTURES];

        int sAt(int i) {
            return (i >= 0 && i < srcEnd) ? (src[i] & 0xFF) : 0;
        }

        int pAt(int i) {
            return (i >= 0 && i < pEnd) ? (pat[i] & 0xFF) : 0;
        }
    }

    private static void prepState(MatchState ms, LuaState L, LuaString s, LuaString pat,
            int pStart, int lp) {
        ms.L = L;
        ms.matchdepth = MAXCCALLS;
        ms.src = s.bytes;
        ms.srcEnd = s.length();
        ms.pat = pat.bytes;
        ms.pStart = pStart;
        ms.pEnd = pStart + lp;
    }

    private static void reprepState(MatchState ms) {
        ms.level = 0;
        ms.matchdepth = MAXCCALLS;
    }

    /** {@code classend}：跳过一个「单字符匹配项」（含 {@code %x}、{@code [...]}） */
    private static int classEnd(MatchState ms, int p) {
        int c = ms.pAt(p++);
        switch (c) {
        case L_ESC:
            if (p == ms.pEnd) {
                throw ms.L.libError("malformed pattern (ends with '%')");
            }
            return p + 1;
        case '[':
            if (ms.pAt(p) == '^') {
                p++;
            }
            do {
                if (p == ms.pEnd) {
                    throw ms.L.libError("malformed pattern (missing ']')");
                }
                if (ms.pAt(p++) == L_ESC && p < ms.pEnd) {
                    p++;
                }
            } while (ms.pAt(p) != ']');
            return p + 1;
        default:
            return p;
        }
    }

    /** C locale 下的 {@code isalpha} 等，一律按 ASCII 判 */
    private static boolean isClass(int c, int cl) {
        boolean res;
        switch (Character.toLowerCase(cl)) {
        case 'a':
            res = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            break;
        case 'c':
            res = c < 32 || c == 127;
            break;
        case 'd':
            res = c >= '0' && c <= '9';
            break;
        case 'g':
            res = c > 32 && c < 127;
            break;
        case 'l':
            res = c >= 'a' && c <= 'z';
            break;
        case 'p':
            res = (c > 32 && c < 127) && !((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9'));
            break;
        case 's':
            res = c == ' ' || c == '\t' || c == '\n' || c == 11 || c == 12 || c == '\r';
            break;
        case 'u':
            res = c >= 'A' && c <= 'Z';
            break;
        case 'w':
            res = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            break;
        case 'x':
            res = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            break;
        case 'z':
            res = (c == 0);
            break;
        default:
            return cl == c;
        }
        return (cl >= 'a' && cl <= 'z') ? res : !res;
    }

    /** {@code matchbracketclass}：{@code p} 指向 {@code [}，{@code ec} 指向 {@code ]} */
    private static boolean matchBracketClass(MatchState ms, int c, int p, int ec) {
        int sig = 1;
        if (ms.pAt(p + 1) == '^') {
            sig = 0;
            p++;
        }
        while (++p < ec) {
            if (ms.pAt(p) == L_ESC) {
                p++;
                if (isClass(c, ms.pAt(p))) {
                    return sig != 0;
                }
            } else if (ms.pAt(p + 1) == '-' && p + 2 < ec) {
                p += 2;
                if (ms.pAt(p - 2) <= c && c <= ms.pAt(p)) {
                    return sig != 0;
                }
            } else if (ms.pAt(p) == c) {
                return sig != 0;
            }
        }
        return sig == 0;
    }

    /** {@code singlematch} */
    private static boolean singleMatch(MatchState ms, int s, int p, int ep) {
        if (s >= ms.srcEnd) {
            return false;
        }
        int c = ms.sAt(s);
        int pc = ms.pAt(p);
        if (pc == '.') {
            return true;
        }
        if (pc == L_ESC) {
            return isClass(c, ms.pAt(p + 1));
        }
        if (pc == '[') {
            return matchBracketClass(ms, c, p, ep - 1);
        }
        return pc == c;
    }

    /** {@code matchbalance}（{@code %b}） */
    private static int matchBalance(MatchState ms, int s, int p) {
        if (p >= ms.pEnd - 1) {
            throw ms.L.libError("malformed pattern (missing arguments to '%b')");
        }
        if (ms.sAt(s) != ms.pAt(p)) {
            return -1;
        }
        int b = ms.pAt(p);
        int e = ms.pAt(p + 1);
        int cont = 1;
        while (++s < ms.srcEnd) {
            int sc = ms.sAt(s);
            if (sc == e) {
                if (--cont == 0) {
                    return s + 1;
                }
            } else if (sc == b) {
                cont++;
            }
        }
        return -1;
    }

    private static int maxExpand(MatchState ms, int s, int p, int ep) {
        int i = 0;
        while (singleMatch(ms, s + i, p, ep)) {
            i++;
        }
        while (i >= 0) {
            int res = match(ms, s + i, ep + 1);
            if (res >= 0) {
                return res;
            }
            i--;
        }
        return -1;
    }

    private static int minExpand(MatchState ms, int s, int p, int ep) {
        while (true) {
            int res = match(ms, s, ep + 1);
            if (res >= 0) {
                return res;
            }
            if (singleMatch(ms, s, p, ep)) {
                s++;
            } else {
                return -1;
            }
        }
    }

    private static int startCapture(MatchState ms, int s, int p, int what) {
        int level = ms.level;
        if (level >= MAXCAPTURES) {
            throw ms.L.libError("too many captures");
        }
        ms.capInit[level] = s;
        ms.capLen[level] = what;
        ms.level = level + 1;
        int res = match(ms, s, p);
        if (res < 0) {
            ms.level--;
        }
        return res;
    }

    private static int endCapture(MatchState ms, int s, int p) {
        int l = captureToClose(ms);
        ms.capLen[l] = s - ms.capInit[l];
        int res = match(ms, s, p);
        if (res < 0) {
            ms.capLen[l] = CAP_UNFINISHED;
        }
        return res;
    }

    private static int captureToClose(MatchState ms) {
        for (int level = ms.level - 1; level >= 0; level--) {
            if (ms.capLen[level] == CAP_UNFINISHED) {
                return level;
            }
        }
        throw ms.L.libError("invalid pattern capture");
    }

    /** {@code check_capture}：把 {@code '1'..'9'} 换成下标，顺手校验 */
    private static int checkCapture(MatchState ms, int l) {
        l -= '1';
        if (l < 0 || l >= ms.level || ms.capLen[l] == CAP_UNFINISHED) {
            throw ms.L.libError("invalid capture index %" + (l + 1));
        }
        return l;
    }

    /** {@code match_capture}（{@code %1} 这类回溯引用） */
    private static int matchCapture(MatchState ms, int s, int l) {
        l = checkCapture(ms, l);
        int len = ms.capLen[l];
        if (len < 0) {
            // 位置捕获（CAP_POSITION）：C 里转成 size_t 是个天文数字，必然不匹配
            return -1;
        }
        if (ms.srcEnd - s >= len && regionEquals(ms.src, ms.capInit[l], s, len)) {
            return s + len;
        }
        return -1;
    }

    private static boolean regionEquals(byte[] a, int aOff, int bOff, int len) {
        if (len <= 0) {
            return true;
        }
        for (int i = 0; i < len; i++) {
            if (a[aOff + i] != a[bOff + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code match}：匹配主循环，返回匹配结束的位置，{@code -1} 表示失败。
     * <p>
     * C 版用 {@code goto init} 做尾递归优化；这里换成
     * 「{@code continue} 重新跑一遍循环体」，语义等价——尤其
     * {@code matchdepth} 的减与加仍然各发生一次。
     */
    private static int match(MatchState ms, int s, int p) {
        if (ms.matchdepth-- == 0) {
            throw ms.L.libError("pattern too complex");
        }
        while (true) {
            boolean dflt = false;
            if (p != ms.pEnd) {
                int c = ms.pAt(p);
                switch (c) {
                case '(':
                    if (ms.pAt(p + 1) == ')') {
                        s = startCapture(ms, s, p + 2, CAP_POSITION);
                    } else {
                        s = startCapture(ms, s, p + 1, CAP_UNFINISHED);
                    }
                    break;
                case ')':
                    s = endCapture(ms, s, p + 1);
                    break;
                case '$':
                    if (p + 1 != ms.pEnd) {
                        dflt = true; // 不是最后一个字符，就当普通字符处理
                    } else {
                        s = (s == ms.srcEnd) ? s : -1;
                    }
                    break;
                case L_ESC: {
                    int c1 = ms.pAt(p + 1);
                    if (c1 == 'b') {
                        s = matchBalance(ms, s, p + 2);
                        if (s >= 0) {
                            p += 4;
                            continue;
                        }
                    } else if (c1 == 'f') {
                        p += 2;
                        if (ms.pAt(p) != '[') {
                            throw ms.L.libError("missing '[' after '%f' in pattern");
                        }
                        int ep = classEnd(ms, p);
                        int previous = (s == 0) ? 0 : ms.sAt(s - 1);
                        if (!matchBracketClass(ms, previous, p, ep - 1)
                                && matchBracketClass(ms, ms.sAt(s), p, ep - 1)) {
                            p = ep;
                            continue;
                        }
                        s = -1;
                    } else if (c1 >= '0' && c1 <= '9') {
                        s = matchCapture(ms, s, c1);
                        if (s >= 0) {
                            p += 2;
                            continue;
                        }
                    } else {
                        dflt = true;
                    }
                    break;
                }
                default:
                    dflt = true;
                    break;
                }
                if (dflt) {
                    int ep = classEnd(ms, p);
                    if (!singleMatch(ms, s, p, ep)) {
                        int suf = ms.pAt(ep);
                        if (suf == '*' || suf == '?' || suf == '-') {
                            p = ep + 1;
                            continue; // 接受空匹配
                        }
                        s = -1; // '+' 或无后缀：至少要匹配一次
                    } else {
                        int suf = ms.pAt(ep);
                        if (suf == '?') {
                            int res = match(ms, s + 1, ep + 1);
                            if (res >= 0) {
                                s = res;
                            } else {
                                p = ep + 1;
                                continue;
                            }
                        } else if (suf == '+' || suf == '*') {
                            if (suf == '+') {
                                s++;
                            }
                            s = maxExpand(ms, s, p, ep);
                        } else if (suf == '-') {
                            s = minExpand(ms, s, p, ep);
                        } else {
                            s++;
                            p = ep;
                            continue;
                        }
                    }
                }
            }
            break;
        }
        ms.matchdepth++;
        return s;
    }

    /** {@code lmemfind}：朴素子串查找 */
    private static int lmemfind(byte[] s, int from, int len, byte[] p) {
        if (p.length == 0) {
            return from;
        }
        if (p.length > len) {
            return -1;
        }
        for (int i = from; i + p.length <= from + len; i++) {
            int j = 0;
            while (j < p.length && s[i + j] == p[j]) {
                j++;
            }
            if (j == p.length) {
                return i;
            }
        }
        return -1;
    }

    /** {@code nospecials}：模式里有没有特殊字符 */
    private static boolean noSpecials(LuaString p) {
        for (int i = 0; i < p.length(); i++) {
            if (SPECIALS.indexOf(p.byteAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** {@code get_onecapture}：{@code capOut[0]} 收下标；返回长度，或 {@code CAP_POSITION} */
    private static int getOneCapture(MatchState ms, int i, int s, int e, int[] capOut) {
        if (i >= ms.level) {
            if (i != 0) {
                throw ms.L.libError("invalid capture index %" + (i + 1));
            }
            capOut[0] = s;
            return e - s;
        }
        int capl = ms.capLen[i];
        capOut[0] = ms.capInit[i];
        if (capl == CAP_UNFINISHED) {
            throw ms.L.libError("unfinished capture");
        }
        if (capl == CAP_POSITION) {
            ms.L.push(LuaValue.ofInteger(ms.capInit[i] + 1));
            return CAP_POSITION;
        }
        return capl;
    }

    private static void pushOneCapture(MatchState ms, int i, int s, int e) {
        int[] cap = new int[1];
        int l = getOneCapture(ms, i, s, e, cap);
        if (l != CAP_POSITION) {
            LuaString out = (l <= 0 || cap[0] < 0) ? LuaString.EMPTY
                    : ms.L.newString(ms.src, cap[0], l);
            ms.L.push(LuaValue.ofString(out));
        }
    }

    private static int pushCaptures(MatchState ms, int s, int e) {
        int nlevels = (ms.level == 0 && s >= 0) ? 1 : ms.level;
        LuaAux.checkStack(ms.L, nlevels, "too many captures");
        for (int i = 0; i < nlevels; i++) {
            pushOneCapture(ms, i, s, e);
        }
        return nlevels;
    }

    /** {@code str_find_aux}：{@code find} 与 {@code match} 共用 */
    private static int findAux(LuaState L, boolean find) {
        LuaString s = LuaAux.checkString(L, 1);
        LuaString pat = LuaAux.checkString(L, 2);
        int ls = s.length();
        long init = posrelatI(LuaAux.optInteger(L, 3, 1), ls) - 1;
        if (init > ls) {
            LuaAux.pushFail(L);
            return 1;
        }
        if (find && ((!L.isNoneArg(4) && L.arg1(4).toBoolean()) || noSpecials(pat))) {
            int s2 = lmemfind(s.bytes, (int) init, ls - (int) init, pat.bytes);
            if (s2 >= 0) {
                L.push(LuaValue.ofInteger(s2 + 1));
                L.push(LuaValue.ofInteger(s2 + pat.length()));
                return 2;
            }
        } else {
            MatchState ms = new MatchState();
            int s1 = (int) init;
            int pStart = 0;
            int lp = pat.length();
            boolean anchor = (pat.length() > 0 && pat.byteAt(0) == '^');
            if (anchor) {
                pStart = 1;
                lp--;
            }
            prepState(ms, L, s, pat, pStart, lp);
            do {
                reprepState(ms);
                int res = match(ms, s1, ms.pStart);
                if (res >= 0) {
                    if (find) {
                        L.push(LuaValue.ofInteger(s1 + 1));
                        L.push(LuaValue.ofInteger(res));
                        return pushCaptures(ms, -1, 0) + 2;
                    }
                    return pushCaptures(ms, s1, res);
                }
            } while (s1++ < ms.srcEnd && !anchor);
        }
        LuaAux.pushFail(L);
        return 1;
    }

    private static int find(LuaState L) {
        return findAux(L, true);
    }

    private static int matchFn(LuaState L) {
        return findAux(L, false);
    }

    /* ==================== gmatch ==================== */

    /** {@code GMatchState}：迭代之间要记住「上次匹配到哪儿」 */
    private static final class GMatchState {
        MatchState ms;
        int src;
        int p;
        int lastmatch = -1;
    }

    /**
     * {@code gmatch}。
     * <p>
     * C 版把源串、模式串、状态结构三个都塞进 C 闭包的 upvalue
     * （注释写得很清楚：为了不让那两个字符串被回收）。这里只要一个就够——
     * Java 的引用本身就是强引用，状态结构拿着那两个 {@code LuaString}，
     * 它们就不会消失。
     */
    private static int gmatch(LuaState L) {
        LuaString s = LuaAux.checkString(L, 1);
        LuaString pat = LuaAux.checkString(L, 2);
        long init = posrelatI(LuaAux.optInteger(L, 3, 1), s.length()) - 1;
        L.top = L.ci.func + 3; // lua_settop(L, 2)
        if (init > s.length()) {
            init = s.length() + 1; // 避免 s + init 溢出
        }
        GMatchState gm = new GMatchState();
        gm.ms = new MatchState();
        prepState(gm.ms, L, s, pat, 0, pat.length());
        gm.src = (int) init;
        gm.p = gm.ms.pStart;
        CClosure cl = new CClosure(StringLib::gmatchAux, 1);
        cl.upvalue[0].set(LuaValue.ofUserdata(gm));
        L.push(LuaValue.ofCClosure(cl));
        return 1;
    }

    private static int gmatchAux(LuaState L) {
        GMatchState gm = (GMatchState) LuaUserdata.dataOf(L.upvalue(0));
        gm.ms.L = L;
        for (int src = gm.src; src <= gm.ms.srcEnd; src++) {
            reprepState(gm.ms);
            int e = match(gm.ms, src, gm.p);
            if (e >= 0 && e != gm.lastmatch) {
                gm.src = gm.lastmatch = e;
                return pushCaptures(gm.ms, src, e);
            }
        }
        return 0;
    }

    /* ==================== gsub ==================== */

    private static final int TR_NUMBER = 0;
    private static final int TR_STRING = 1;
    private static final int TR_FUNCTION = 2;
    private static final int TR_TABLE = 3;

    private static int trType(LuaState L) {
        if (L.isNoneArg(3)) {
            return -1;
        }
        LuaValue v = L.arg1(3);
        if (v.isNumber()) {
            return TR_NUMBER;
        }
        if (v.isString()) {
            return TR_STRING;
        }
        if (v.isFunction()) {
            return TR_FUNCTION;
        }
        if (v.isTable()) {
            return TR_TABLE;
        }
        return -1;
    }

    /** {@code add_s}：替换串里的 {@code %0} / {@code %n} / {@code %%} */
    private static void addS(MatchState ms, LuaAux.Buf b, int s, int e) {
        LuaString rep = LuaAux.checkString(ms.L, 3);
        byte[] news = rep.bytes;
        int off = 0;
        while (true) {
            int idx = -1;
            for (int i = off; i < news.length; i++) {
                if ((news[i] & 0xFF) == L_ESC) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                b.addBytes(news, off, news.length - off);
                return;
            }
            b.addBytes(news, off, idx - off);
            int p = idx + 1;
            int pc = (p < news.length) ? (news[p] & 0xFF) : 0;
            if (pc == L_ESC) {
                b.addChar(L_ESC);
            } else if (pc == '0') {
                b.addBytes(ms.src, s, e - s);
            } else if (pc >= '0' && pc <= '9') {
                int[] cap = new int[1];
                int resl = getOneCapture(ms, pc - '1', s, e, cap);
                if (resl == CAP_POSITION) {
                    LuaAux.addTop(ms.L, b); // 位置捕获：值已经压在栈顶了
                } else {
                    b.addBytes(ms.src, cap[0], resl);
                }
            } else {
                throw ms.L.libError("invalid use of '%' in replacement string");
            }
            off = p + 1;
        }
    }

    /** {@code add_value}：返回「原文有没有被改动」 */
    private static boolean addValue(MatchState ms, LuaAux.Buf b, int s, int e, int tr) {
        LuaState L = ms.L;
        switch (tr) {
        case TR_FUNCTION: {
            int fIdx = L.top;
            L.push(L.arg1(3));
            int n = pushCaptures(ms, s, e);
            L.callNoYield(fIdx, 1); /* lstrlib.c: lua_call —— 不可 yield */
            break;
        }
        case TR_TABLE: {
            pushOneCapture(ms, 0, s, e);
            LuaValue key = new LuaValue(L.stack[L.top - 1]);
            L.pushNil();
            int idx = L.top - 1;
            Vm.getTable(L, L.arg1(3), key, idx);
            break;
        }
        default:
            addS(ms, b, s, e);
            return true;
        }
        LuaValue res = L.stack[L.top - 1];
        if (!res.toBoolean()) {
            L.top--; // nil / false：保留原文
            b.addBytes(ms.src, s, e - s);
            return false;
        }
        if (!res.isString() && !res.isNumber()) {
            throw L.libError("invalid replacement value (a " + res.typeName() + ")");
        }
        LuaAux.addTop(L, b);
        return true;
    }

    /** {@code str_gsub} */
    private static int gsub(LuaState L) {
        LuaString src = LuaAux.checkString(L, 1);
        LuaString pat = LuaAux.checkString(L, 2);
        int tr = trType(L);
        /*
         * 顺序照抄 C：<b>先</b> {@code luaL_optinteger(L, 4, ...)}，<b>再</b>
         * 校验第三个参数的类型。所以 {@code gsub(s, p, nil, "x")} 报的是
         * {@code bad argument #4 (number expected, got string)}，而不是
         * 「第三个参数不是 string/function/table」——那个判断在后面。
         * 写反了就会在「repl 不对、同时 max 也不对」时报错的那一条上偏。
         */
        long maxS = LuaAux.optInteger(L, 4, (long) src.length() + 1);
        LuaAux.argExpected(L, tr >= 0, 3, "string/function/table");
        MatchState ms = new MatchState();
        LuaAux.Buf b = new LuaAux.Buf();
        int pStart = 0;
        int lp = pat.length();
        boolean anchor = (pat.length() > 0 && pat.byteAt(0) == '^');
        if (anchor) {
            pStart = 1;
            lp--;
        }
        prepState(ms, L, src, pat, pStart, lp);
        long n = 0;
        boolean changed = false;
        int cur = 0;
        int lastmatch = -1;
        while (n < maxS) {
            reprepState(ms);
            int e = match(ms, cur, ms.pStart);
            /*
             * {@code e != lastmatch} 这一条是 {@code ("abc"):gsub("", "-")}
             * 能给出 {@code -a-b-c-} 而不是 {@code ----abc} 的原因：空匹配
             * 在同一个位置会<b>原地重复命中</b>，只靠 {@code max_s} 挡住的话
             * 得到的是「替换次数够了但一个字符都没推进」——语法上跑完了，
             * 语义上完全错。
             */
            if (e >= 0 && e != lastmatch) {
                n++;
                if (addValue(ms, b, cur, e, tr)) {
                    changed = true;
                }
                cur = lastmatch = e;
            } else if (cur < ms.srcEnd) {
                b.addChar(ms.src[cur++]);
            } else {
                break;
            }
            if (anchor) {
                break;
            }
        }
        if (!changed) {
            L.push(LuaValue.ofString(src));
        } else {
            b.addBytes(src.bytes, cur, ms.srcEnd - cur);
            L.push(LuaValue.ofString(b.result()));
        }
        L.push(LuaValue.ofInteger(n));
        return 2;
    }
    /* ==================== string.format ==================== */

    /**
     * {@code str_format}。
     * <p>
     * C 版把格式项拼成一小段 {@code "%-+#0..."} 再交给系统的 {@code snprintf}；
     * 这里<b>不能</b>直接转手给 Java 的 {@code String.format}，两家的规则在
     * 好几个地方不一样：
     * <ul>
     *   <li>{@code %g} 默认会去掉尾部的零（Java 不会）；</li>
     *   <li>{@code %c} 写的是<b>一个字节</b>，Java 会把 200 编成 UTF-8 的两字节；</li>
     *   <li>{@code %s} 的精度数的是字节不是字符；</li>
     *   <li>inf / NaN 分别写成 {@code inf} / {@code nan}，Java 写 {@code Infinity}。</li>
     * </ul>
     * 所以这里自己解析格式项、自己生成数字串，只在最后一步复用 C printf 的
     * 「符号 / 前缀 / 宽度」组装规则。
     */
    private static int format(LuaState L) {
        LuaString fmt = LuaAux.checkString(L, 1);
        byte[] f = fmt.bytes;
        int sfl = f.length;
        int arg = 1; // 1 号参数是格式串本身，下一个可用的参数从 2 开始
        LuaAux.Buf out = new LuaAux.Buf();
        int i = 0;
        while (i < sfl) {
            if (f[i] != L_ESC) {
                out.addChar(f[i] & 0xFF);
                i++;
                continue;
            }
            i++;
            if (i < sfl && f[i] == L_ESC) {
                out.addChar(L_ESC); // %%
                i++;
                continue;
            }
            arg++;
            if (arg > L.getTop()) {
                // 注意顺序：参数不够比「格式串本身不合法」先报
                throw L.argError(arg, "no value");
            }
            int start = i;
            int len = 0;
            while (start + len < sfl && "-+#0 123456789.".indexOf(f[start + len] & 0xFF) >= 0) {
                len++;
            }
            len++; // 加上后面的转换字符
            if (len >= MAX_FORMAT - 10) {
                throw L.libError("invalid format (too long)");
            }
            int take = Math.min(len, sfl - start);
            String form = "%" + ascii(f, start, take);
            int convIdx = start + len - 1;
            int conv = (convIdx >= 0 && convIdx < sfl) ? (f[convIdx] & 0xFF) : 0;
            i = start + len;
            Spec sp = parseSpec(form);
            switch (conv) {
            case 'c': {
                checkFormat(L, form, form.substring(1), "-", false);
                long n = LuaAux.checkInteger(L, arg);
                out.addAscii(assemble(sp, "", "", Character.toString((char) (int) n), false));
                break;
            }
            case 'd':
            case 'i': {
                // C 的 intcase 是「先 checkinteger，再 checkformat」，顺序别反
                long n = LuaAux.checkInteger(L, arg);
                checkFormat(L, form, form.substring(1), "-+0 ", true);
                emitInt(out, sp, conv, n, false);
                break;
            }
            case 'u': {
                long n = LuaAux.checkInteger(L, arg);
                checkFormat(L, form, form.substring(1), "-0", true);
                emitInt(out, sp, conv, n, false);
                break;
            }
            case 'o':
            case 'x':
            case 'X': {
                long n = LuaAux.checkInteger(L, arg);
                checkFormat(L, form, form.substring(1), "-#0", true);
                emitInt(out, sp, conv, n, false);
                break;
            }
            case 'a':
            case 'A': {
                checkFormat(L, form, form.substring(1), "-+#0 ", true);
                double x = LuaAux.checkNumber(L, arg);
                emitHexFloat(out, sp, x, conv == 'A');
                break;
            }
            case 'f': {
                double x = LuaAux.checkNumber(L, arg);
                checkFormat(L, form, form.substring(1), "-+#0 ", true);
                int prec = sp.hasPrec ? sp.prec : 6;
                String d = fixedDigits(Math.abs(x), prec);
                emitFloat(out, sp, isNeg(x), keepPoint(d, sp.hash), conv == 'F');
                break;
            }
            case 'e':
            case 'E': {
                double x = LuaAux.checkNumber(L, arg);
                checkFormat(L, form, form.substring(1), "-+#0 ", true);
                int prec = sp.hasPrec ? sp.prec : 6;
                int[] exp = new int[1];
                String m = expDigits(Math.abs(x), prec, exp);
                if (!isSpecial(m)) {
                    m = keepPoint(m, sp.hash) + "e" + signedExp(exp[0]);
                }
                emitFloat(out, sp, isNeg(x), m, conv == 'E');
                break;
            }
            case 'g':
            case 'G': {
                double x = LuaAux.checkNumber(L, arg);
                checkFormat(L, form, form.substring(1), "-+#0 ", true);
                int prec = sp.hasPrec ? sp.prec : 6;
                emitFloat(out, sp, isNeg(x), generalDigits(Math.abs(x), prec, sp.hash), conv == 'G');
                break;
            }
            case 'p': {
                checkFormat(L, form, form.substring(1), "-", false);
                LuaValue v = L.arg1(arg);
                /*
                 * {@code lua_topointer} 只对「可回收对象」有地址，数字、布尔、
                 * nil 一律返回 {@code NULL}。C 版拿到 NULL 就把转换符改成
                 * {@code %s}、拿 {@code "(null)"} 当字符串格式化——所以
                 * {@code string.format("%p", 4)} 是 {@code "(null)"}，
                 * 不是 {@code 0x0}（上游 {@code strings.lua} 有这一条）。
                 */
                if (v.isNil() || v.isBoolean() || v.isNumber()) {
                    String digits = "(null)";
                    if (sp.hasPrec) {
                        digits = digits.substring(0, Math.min(sp.prec, digits.length()));
                    }
                    out.addAscii(assemble(sp, "", "", digits, false));
                    break;
                }
                /*
                 * 与参考实现必然不同：C 打印的是真实地址，我们只有 Java 的
                 * identity hash。硬编一个假地址不如如实给一个稳定的数——
                 * 至少同一个值在同一次运行里得到同一个串。
                 */
                String hex = "0x" + Long.toHexString(System.identityHashCode(v.o));
                out.addAscii(assemble(sp, "", "", hex, false));
                break;
            }
            case 'q': {
                if (form.length() > 2) {
                    throw L.libError("specifier '%q' cannot have modifiers");
                }
                addLiteral(L, out, arg);
                break;
            }
            case 's': {
                LuaString s = LuaAux.toLString(L, L.arg1(arg));
                if (form.length() == 2) { // 没有修饰符
                    out.addString(s);
                    break;
                }
                for (int k = 0; k < s.length(); k++) {
                    if (s.byteAt(k) == 0) {
                        throw L.argError(arg, "string contains zeros");
                    }
                }
                checkFormat(L, form, form.substring(1), "-", true);
                if (form.indexOf('.') < 0 && s.length() >= 100) {
                    out.addString(s); // 太长，不进 buff
                    break;
                }
                String digits = ascii(s.bytes, 0, s.length());
                if (sp.hasPrec) {
                    digits = digits.substring(0, Math.min(sp.prec, digits.length()));
                }
                out.addAscii(assemble(sp, "", "", digits, false));
                break;
            }
            default:
                throw L.libError("invalid conversion '" + form + "' to 'format'");
            }
        }
        L.push(LuaValue.ofString(out.result()));
        return 1;
    }

    private static String ascii(byte[] b, int off, int len) {
        char[] c = new char[len];
        for (int k = 0; k < len; k++) {
            c[k] = (char) (b[off + k] & 0xFF);
        }
        return new String(c);
    }

    /**
     * {@code lauxlib.c: checkformat}：格式项里除了 flags / width / precision，
     * 最后必须落到一个字母（转换字符）上。
     */
    private static void checkFormat(LuaState L, String form, String spec, String flags,
            boolean precision) {
        int p = 0;
        while (p < spec.length() && flags.indexOf(spec.charAt(p)) >= 0) {
            p++;
        }
        if (p < spec.length() && spec.charAt(p) != '0') {
            p = twoDigits(spec, p);
            if (p < spec.length() && spec.charAt(p) == '.' && precision) {
                p++;
                p = twoDigits(spec, p);
            }
        }
        if (p >= spec.length() || !isAlpha(spec.charAt(p))) {
            throw L.libError("invalid conversion specification: '" + form + "'");
        }
    }

    /** {@code get2digits}：宽度与精度最多读两位——所以 {@code %.200d} 是不合法的 */
    private static int twoDigits(String s, int p) {
        for (int k = 0; k < 2 && p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9'; k++) {
            p++;
        }
        return p;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** 解析出的格式项 */
    private static final class Spec {
        boolean left;
        boolean plus;
        boolean space;
        boolean hash;
        boolean zero;
        int width;
        boolean hasPrec;
        int prec;
    }

    private static Spec parseSpec(String form) {
        Spec sp = new Spec();
        int p = 1; // 跳过 '%'
        while (p < form.length() && "-+ #0".indexOf(form.charAt(p)) >= 0) {
            switch (form.charAt(p)) {
            case '-':
                sp.left = true;
                break;
            case '+':
                sp.plus = true;
                break;
            case ' ':
                sp.space = true;
                break;
            case '#':
                sp.hash = true;
                break;
            default:
                sp.zero = true;
                break;
            }
            p++;
        }
        while (p < form.length() && form.charAt(p) >= '0' && form.charAt(p) <= '9') {
            sp.width = sp.width * 10 + (form.charAt(p) - '0');
            p++;
        }
        if (p < form.length() && form.charAt(p) == '.') {
            p++;
            sp.hasPrec = true;
            while (p < form.length() && form.charAt(p) >= '0' && form.charAt(p) <= '9') {
                sp.prec = sp.prec * 10 + (form.charAt(p) - '0');
                p++;
            }
        }
        return sp;
    }

    /** 符号：{@code '-'} 优先，其次 {@code '+'}，再其次空格 */
    private static String signOf(boolean neg, Spec sp) {
        if (neg) {
            return "-";
        }
        if (sp.plus) {
            return "+";
        }
        if (sp.space) {
            return " ";
        }
        return "";
    }

    /**
     * C printf 的「组装」：把符号、前缀、数字串按 flags / width 摆好。
     * <p>
     * 零填充的位置是唯一容易写错的地方：<b>在符号与 {@code 0x} 前缀之后、数字之前</b>。
     * {@code ("%010a"):format(1.5)} 要给 {@code 0x001.8p+0}——填在 {@code 0x}
     * 后面，不是前面。
     */
    private static String assemble(Spec sp, String sign, String prefix, String digits,
            boolean upper) {
        String body = prefix + digits;
        if (upper) {
            body = body.toUpperCase();
        }
        String lead = sign + body;
        if (sp.width <= lead.length()) {
            return lead;
        }
        int pad = sp.width - lead.length();
        StringBuilder sb = new StringBuilder();
        if (sp.left) {
            sb.append(lead);
            repeat(sb, ' ', pad);
        } else if (sp.zero && !isSpecial(digits)) {
            /*
             * inf / nan 不参与零填充：C printf 把它们当「没有数字可言」，
             * {@code 0} 标志被忽略，宽度改用<b>空格</b>补。
             * {@code ("%020.5a"):format(math.huge)} 因此是 17 个空格加
             * {@code inf}（共 20 列），而 {@code ("%020d"):format(42)} 照旧是
             * {@code 00000000000000000042}——整数动词不受影响。
             */
            sb.append(sign).append(prefix);
            repeat(sb, '0', pad);
            sb.append(upper ? digits.toUpperCase() : digits);
        } else {
            repeat(sb, ' ', pad);
            sb.append(lead);
        }
        return sb.toString();
    }

    /** 去掉 {@code 0} 标志的副本（整数动词给了精度时用） */
    private static Spec copyWithoutZero(Spec s) {
        Spec c = new Spec();
        c.left = s.left;
        c.plus = s.plus;
        c.space = s.space;
        c.hash = s.hash;
        c.zero = false;
        c.width = s.width;
        c.hasPrec = s.hasPrec;
        c.prec = s.prec;
        return c;
    }

    private static void repeat(StringBuilder sb, char c, int n) {
        for (int k = 0; k < n; k++) {
            sb.append(c);
        }
    }

    /** {@code %.0d} 对 0 得到空串——C 的行为，别「顺手」补一个 0 */
    private static String applyPrecision(String digits, boolean hasPrec, int prec) {
        if (!hasPrec) {
            return digits;
        }
        if (prec == 0 && digits.equals("0")) {
            return "";
        }
        if (digits.length() >= prec) {
            return digits;
        }
        StringBuilder sb = new StringBuilder();
        repeat(sb, '0', prec - digits.length());
        sb.append(digits);
        return sb.toString();
    }

    private static void emitInt(LuaAux.Buf out, Spec sp, int conv, long n, boolean unused) {
        String digits;
        String prefix = "";
        boolean neg = false;
        switch (conv) {
        case 'u':
            digits = Long.toUnsignedString(n);
            break;
        case 'o':
            digits = Long.toUnsignedString(n, 8);
            break;
        case 'x':
        case 'X':
            digits = Long.toHexString(n);
            if (sp.hash && n != 0) {
                prefix = (conv == 'X') ? "0X" : "0x";
            }
            break;
        default:
            neg = n < 0;
            digits = (n == Long.MIN_VALUE) ? "9223372036854775808" : Long.toString(Math.abs(n));
            break;
        }
        digits = applyPrecision(digits, sp.hasPrec, sp.prec);
        /*
         * C 的规则：<b>整数转换一旦指定了精度，{@code 0} 标志就被忽略</b>
         * （C99 7.19.6.1：「For d, i, o, u, x, and X conversions, if a
         * precision is specified, the 0 flag is ignored」）。
         * {@code ("%05.3d"):format(42)} 因此是 {@code "  042"}——精度先把值补成
         * {@code "042"}，宽度 5 再用<b>空格</b>补，不是 {@code "00042"}。
         * 实测矩阵里这一类错了 220 行，全是「{@code 0} 标志 + 精度 + 整数动词」。
         * 浮点动词没有这条规则（{@code %08.3f} 照旧补零），所以只在这里改。
         */
        if (sp.hasPrec) {
            sp = copyWithoutZero(sp);
        }
        if (conv == 'o' && sp.hash && !digits.startsWith("0")) {
            /*
             * {@code #} 对 {@code %o} 的作用是「逼出前导 0」。必须在补完精度
             * <b>之后</b>再补：{@code ("%#.0o"):format(0)} 的精度把 {@code "0"}
             * 削成了空串，而 C 的规则是「值与精度都是 0 时要打印一个 0」——
             * 顺序反了就会给出空串。
             */
            digits = "0" + digits;
        }
        out.addAscii(assemble(sp, signOf(neg, sp), prefix, digits, conv == 'X'));
    }

    /* ---------- 浮点 ---------- */

    /** 负号取自符号位，所以 {@code -0.0} 也算负——{@code %f} 要给 {@code -0.000000} */
    private static boolean isNeg(double x) {
        return !Double.isNaN(x) && Double.doubleToRawLongBits(x) < 0;
    }

    private static void emitFloat(LuaAux.Buf out, Spec sp, boolean neg, String digits,
            boolean upper) {
        out.addAscii(assemble(sp, signOf(neg, sp), "", digits, upper));
    }

    /**
     * {@code %f}：定点表示。
     * <p>
     * 用 {@link BigDecimal} 而不是自己写十进制展开，是因为 C printf 的舍入是
     * <b>round-half-to-even</b>，而 {@code RoundingMode.HALF_EVEN} 正好就是它。
     * 顺带也白拿了任意精度：{@code ("%.99f"):format(-maxfloat)} 有 410 个字符。
     */
    private static String fixedDigits(double ax, int prec) {
        if (Double.isInfinite(ax)) {
            return "inf";
        }
        if (Double.isNaN(ax)) {
            return "nan";
        }
        return new BigDecimal(ax).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** {@code %e} 的尾数部分，指数从 {@code expOut} 带出来 */
    private static String expDigits(double ax, int prec, int[] expOut) {
        if (Double.isInfinite(ax)) {
            expOut[0] = 0;
            return "inf";
        }
        if (Double.isNaN(ax)) {
            expOut[0] = 0;
            return "nan";
        }
        BigDecimal r = new BigDecimal(ax).round(new MathContext(prec + 1, RoundingMode.HALF_EVEN));
        int exp = r.precision() - r.scale() - 1;
        expOut[0] = exp;
        return r.movePointLeft(exp).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * {@code #} 对浮点转换的作用是「<b>一定</b>保留小数点」。
     * <p>
     * {@code ("%#.0f"):format(1)} 是 {@code 1.} 而不是 {@code 1}——小数点留在
     * 末尾，且必须在拼指数<b>之前</b>加，{@code %#.0e} 给的是 {@code 0.e+00}。
     */
    private static String keepPoint(String digits, boolean hash) {
        if (!hash || digits.indexOf('.') >= 0) {
            return digits;
        }
        return digits + ".";
    }

    /** inf / nan 的“数字串”后面不再拼指数 */
    private static boolean isSpecial(String digits) {
        return digits.equals("inf") || digits.equals("nan");
    }

    /** {@code %a} 的指数：与 {@code %e} 不同，<b>不</b>补足两位 */
    private static String signedHexExp(int exp) {
        return (exp < 0 ? "-" : "+") + Math.abs(exp);
    }

    private static String signedExp(int exp) {
        StringBuilder sb = new StringBuilder();
        sb.append(exp < 0 ? '-' : '+');
        int a = Math.abs(exp);
        if (a < 10) {
            sb.append('0'); // 指数至少两位
        }
        sb.append(a);
        return sb.toString();
    }

    /**
     * {@code %g}：先按 P 位有效数字定出十进制指数 X，再决定走 {@code f} 还是 {@code e}。
     * <p>
     * 规则照抄 C：{@code P > X >= -4} 走 {@code f}（精度 {@code P-(X+1)}），
     * 否则走 {@code e}（精度 {@code P-1}）；最后<b>去掉尾部的零</b>，
     * 除非有 {@code #}。{@code ("%g"):format(1000000)} 得到 {@code 1e+06}
     * 就是这条规则，不是什么「看起来像整数就换记数法」。
     * <p>
     * 包内可见，因为 {@code luaO_tostring} 的 {@code %.14g} 与这里的 {@code %g}
     * 是同一个 C 转换的两处调用，必须共用一份实现 —— 各写一份正是它们分家的
     * 原因（{@link Vm#formatG} 曾经用 {@code String.format} 自己写了一遍）。
     * 传进来的 {@code ax} 是绝对值：正负号由调用方处理，{@code -0.0} 也靠它保住。
     */
    static String generalDigits(double ax, int p, boolean hash) {
        int bigP = (p == 0) ? 1 : p;
        if (Double.isInfinite(ax) || Double.isNaN(ax)) {
            return fixedDigits(ax, 0);
        }
        BigDecimal r = new BigDecimal(ax).round(new MathContext(bigP, RoundingMode.HALF_EVEN));
        int x = (ax == 0) ? 0 : r.precision() - r.scale() - 1;
        if (bigP > x && x >= -4) {
            String s = fixedDigits(ax, bigP - (x + 1));
            return hash ? keepPoint(s, true) : stripZeros(s);
        }
        int[] exp = new int[1];
        String m = expDigits(ax, bigP - 1, exp);
        // 先剥尾零再拼指数：指数本身以 "e-05" 结尾，直接对整个串剥零是剥不到的
        if (!isSpecial(m) && !hash) {
            m = stripZeros(m);
        }
        return isSpecial(m) ? m : keepPoint(m, hash) + "e" + signedExp(exp[0]);
    }

    /** 去掉小数部分尾部的零；都去掉了就连小数点一起去掉 */
    private static String stripZeros(String s) {
        int dot = s.indexOf('.');
        if (dot < 0) {
            return s;
        }
        int end = s.length();
        while (end > dot + 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end == dot + 1) {
            end = dot;
        }
        return s.substring(0, end);
    }

    /**
     * {@code %a}：十六进制浮点。
     * <p>
     * 形状是 {@code 0xh.<hex>p±dd}，指数不补两位（{@code p+0} 不是 {@code p+00}）。
     */
    private static void emitHexFloat(LuaAux.Buf out, Spec sp, double x, boolean upper) {
        boolean neg = isNeg(x);
        double ax = Math.abs(x);
        boolean special = Double.isInfinite(ax) || Double.isNaN(ax);
        out.addAscii(assemble(sp, signOf(neg, sp), special ? "" : "0x",
                hexFloatDigits(ax, sp), upper));
    }

    /**
     * {@code %a} 的「尾数 + {@code p±dd}」部分，不含 {@code 0x} 前缀与符号。
     * <p>
     * {@code %q} 与 {@code %a} 在 C 里是<b>同一个转换</b>（约定 #16）：
     * {@code quotefloat} 用的就是 {@code snprintf("%" LA_FRMLEN "a")}。
     * 之前 {@code %q} 另写了一份直接吃 {@code Double.toHexString} 的实现，
     * 于是和 {@code %a} 那条路径在次正规数上给出两种答案
     * （{@code ("%q"):format(5e-324)} 与 {@code ("%a"):format(5e-324)} 不一致）。
     * 合并到这一处之后两边同源。
     */
    private static String hexFloatDigits(double ax, Spec sp) {
        if (Double.isInfinite(ax) || Double.isNaN(ax)) {
            return Double.isNaN(ax) ? "nan" : "inf";
        }
        if (ax == 0) {
            String frac = sp.hasPrec ? zeros(sp.prec) : "";
            return "0" + dotFrac(frac, sp) + "p" + signedHexExp(0);
        }
        /*
         * 小数点左边那一位直接取自 {@code Double.toHexString}：正规数是
         * {@code 1}，次正规数是 {@code 0}。
         * <p>
         * 这里<b>刻意不把次正规规格化</b>。C99 只规定「小数点左边恰好一位」，
         * 并且「这个数字只在数次正规时为 0，其它情况取值由实现定义」——
         * 次正规怎么写属于 <i>unspecified</i>，两个 libc 正好相反：
         * glibc（CI 的 ubuntu）忠实于机器编码，{@code 5e-324} 给
         * {@code 0x0.0000000000001p-1022}；Apple libc / musl 先规格化，给
         * {@code 0x1p-1074}。取 glibc：CI 的一致性闸门才是主闸门（约定 #52）。
         * <p>
         * 之前这里有一段「剥掉前导 0 再减指数」的规格化逻辑，于是次正规被
         * 规格化成了 Apple 那种写法，而 {@code %q} 走的另一份实现保留机器
         * 编码——同一个 {@code %a} 转换在两处给出两种答案。删掉它之后两边同源，
         * 且 {@link StringLibTest#subnormalQuoteFollowsGlibc} 钉的就是这份。
         */
        String h = Double.toHexString(ax); // 形如 "0x1.8p0" / "0x0.0000000000001p-1022"
        int pIdx = h.indexOf('p');
        String mant = h.substring(2, pIdx);
        int exp = Integer.parseInt(h.substring(pIdx + 1));
        int dot = mant.indexOf('.');
        String intPart = (dot < 0) ? mant : mant.substring(0, dot);
        String frac = (dot < 0) ? "" : mant.substring(dot + 1);
        if (sp.hasPrec) {
            frac = roundFrac(intPart, frac, sp.prec);
            intPart = frac.substring(0, frac.length() - sp.prec);
            frac = frac.substring(frac.length() - sp.prec);
        } else {
            frac = stripZerosFrac(frac);
        }
        return intPart + dotFrac(frac, sp) + "p" + signedHexExp(exp);
    }

    /**
     * 小数点什么时候留。
     * <p>
     * C99：{@code #} 标志下 {@code a}/{@code A} 的结果<b>必须带小数点</b>，哪怕
     * 后面一位数字都没有。实测（参考实现）：{@code ("%#a"):format(0.0)} 是
     * {@code 0x0.p+0}、{@code ("%#a"):format(1.0)} 是 {@code 0x1.p+0}、
     * {@code ("%#.0a"):format(1.0)} 是 {@code 0x1.p+0}，而去掉 {@code #} 之后
     * 三种都是 {@code 0x0p+0} / {@code 0x1p+0} / {@code 0x1p+0}。
     * 曾经只有非零分支照这条写，零值分支漏了 —— 于是
     * {@code ("%#a"):format(0.0)} 给 {@code 0x0p+0}。
     */
    private static String dotFrac(String frac, Spec sp) {
        return (frac.isEmpty() && !sp.hash) ? "" : "." + frac;
    }

    private static String zeros(int n) {
        StringBuilder sb = new StringBuilder();
        repeat(sb, '0', n);
        return sb.toString();
    }

    private static String stripZerosFrac(String frac) {
        int end = frac.length();
        while (end > 0 && frac.charAt(end - 1) == '0') {
            end--;
        }
        return frac.substring(0, end);
    }

    /**
     * 把 {@code intPart.frac} 舍到 {@code prec} 位十六进制小数。返回合并后的串。
     * <p>
     * 判据逐字照抄 glibc 的 {@code stdio-common/printf_fphex.c}：
     * <pre>
     * bool more_bits = ((next_digit_value &amp; 7) != 0
     *                   || precision + 1 &lt; numend - numstr);
     * do_round_away = round_away (negative, last_digit_value &amp; 1,
     *                             next_digit_value &gt;= 8, more_bits, mode);
     * </pre>
     * 而 {@code round_away} 在 {@code FE_TONEAREST} 下就是
     * {@code half_bit && (last_digit_odd || more_bits)}——即
     * <b>就近舍入、平局取偶</b>。
     * <p>
     * 平局（舍弃的第一位恰好是 {@code 8}、后面全是 0）时<b>看保留的最后一位
     * 的奇偶</b>：{@code ("%.0a"):format(1.5)} 保留的最后一位是整数位 {@code 1}
     * （奇），进位成 {@code 0x2p+0}；{@code ("%.1a"):format(0x1.08p0)} 保留的最
     * 后一位是 {@code 0}（偶），不进位，给 {@code 0x1.0p+0}。
     * <p>
     * <b>Apple libc 在这里是错的</b>，错得可以量化：{@code ("%.4a")} 作用在
     * {@code 0x1.500082p+0} 上给 {@code 0x1.5000p+0}——舍弃的 {@code 0x82}
     * 明明大于半 {@code 0x80} 却舍了下去；而 {@code 0x1.500081p+0} 反倒进上来。
     * 它的判据成了「第二位舍弃数字的奇偶」。这两条在 glibc 上都该进位。
     * 所以带精度的 {@code %a} 里「舍弃的第一位恰为 8」的那一批是平台相关的，
     * 不进差分表（同一族问题见 {@code subnormalQuoteFollowsGlibc}）。
     */
    private static String roundFrac(String intPart, String frac, int prec) {
        if (frac.length() <= prec) {
            StringBuilder sb = new StringBuilder(intPart);
            sb.append(frac);
            repeat(sb, '0', prec - frac.length());
            return sb.toString();
        }
        int next = Character.digit(frac.charAt(prec), 16);
        boolean moreBits = (next & 7) != 0;
        if (!moreBits) {
            for (int i = prec + 1; i < frac.length(); i++) {
                if (frac.charAt(i) != '0') {
                    moreBits = true;
                    break;
                }
            }
        }
        /* prec == 0 时「保留的最后一位」是小数点左边那一位 */
        int last = Character.digit((prec > 0) ? frac.charAt(prec - 1)
                : intPart.charAt(0), 16);
        boolean up = (next >= 8) && (moreBits || (last % 2 == 1));
        StringBuilder sb = new StringBuilder(intPart);
        sb.append(frac, 0, prec);
        if (up) {
            for (int i = sb.length() - 1; i >= 0; i--) {
                int d = Character.digit(sb.charAt(i), 16);
                if (d < 15) {
                    sb.setCharAt(i, "0123456789abcdef".charAt(d + 1));
                    break;
                }
                sb.setCharAt(i, '0');
            }
        }
        return sb.toString();
    }

    /* ---------- %q ---------- */

    /** {@code addliteral} */
    private static void addLiteral(LuaState L, LuaAux.Buf b, int arg) {
        LuaValue v = L.arg1(arg);
        if (v.isString()) {
            addQuoted(b, v.asString());
        } else if (v.isNumber()) {
            if (v.isInteger()) {
                long n = v.i;
                // MIN_INTEGER 走十六进制：写成十进制再取负会溢出
                b.addAscii((n == Long.MIN_VALUE) ? ("0x" + Long.toHexString(n))
                        : Long.toString(n));
            } else {
                b.addAscii(quoteFloat(v.n));
            }
        } else if (v.isNil() || v.isBoolean()) {
            b.addString(LuaAux.toLString(L, v));
        } else {
            throw L.argError(arg, "value has no literal form");
        }
    }

    /** {@code quotefloat}：十六进制保精度，三个非有限值各有一套固定写法 */
    private static String quoteFloat(double n) {
        if (n == Double.POSITIVE_INFINITY) {
            return "1e9999";
        }
        if (n == Double.NEGATIVE_INFINITY) {
            return "-1e9999";
        }
        if (Double.isNaN(n)) {
            return "(0/0)";
        }
        StringBuilder sb = new StringBuilder();
        if (isNeg(n)) {
            sb.append('-');
        }
        // 与 %a 同源（约定 #16）：C 的 quotefloat 就是 snprintf("%a")，
        // 无标志、无宽度、无精度。
        sb.append("0x").append(hexFloatDigits(Math.abs(n), new Spec()));
        return sb.toString();
    }

    /**
     * {@code addquoted}。
     * <p>
     * 控制字符写成 {@code \d} 或 {@code \ddd}——<b>后面那一位是数字时才补成三位</b>，
     * 否则 {@code \1} 后面紧跟 {@code '2'} 会被读成 {@code \12}。这条不是风格问题，
     * 是 {@code %q} 要能被 Lua 原样读回的前提。
     */
    private static void addQuoted(LuaAux.Buf b, LuaString s) {
        b.addChar('"');
        for (int i = 0; i < s.length(); i++) {
            int c = s.byteAt(i);
            if (c == '"' || c == '\\' || c == '\n') {
                b.addChar('\\');
                b.addChar(c);
            } else if (c < 32 || c == 127) {
                int next = (i + 1 < s.length()) ? s.byteAt(i + 1) : 0;
                boolean nextDigit = next >= '0' && next <= '9';
                b.addChar('\\');
                b.addAscii(nextDigit ? String.format("%03d", c) : Integer.toString(c));
            } else {
                b.addChar(c);
            }
        }
        b.addChar('"');
    }
}
