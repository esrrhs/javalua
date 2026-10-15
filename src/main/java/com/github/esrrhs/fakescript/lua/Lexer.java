package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 词法分析器 —— 逐行翻译 {@code llex.c} / {@code llex.h}。
 * <p>
 * 这是「直接能从 C 源码翻译」这一路线上的第一块真骨头，也是最容易在
 * 「看起来能跑」之后悄悄错的地方。翻译时守住的几条上游约定，改任何一条都会
 * 在某个真实脚本上炸掉：
 * <ol>
 *   <li><b>单字符 token 的值就是字符本身的编码</b>（{@code '+' → 43}），
 *       其余 token 从 {@code FIRST_RESERVED}（256+1）开始。这条决定了后面
 *       整个 parser 的 {@code switch} 长什么样；</li>
 *   <li><b>数字先按整数解析、失败才按浮点</b>（{@link Numeral#str2num}），
 *       所以 {@code 1} 是 TK_INT 而 {@code 1.0} 是 TK_FLT；</li>
 *   <li><b>转义序列全部支持</b>：{@code \z} 吞空白、{@code \xXX} 十六进制、
 *       {@code \ddd} 十进制、以及反斜杠+u+花括号的 Unicode 码点形式，
 *       且错误信息与上游逐字一致（差分测试比对的就是这些字符串）；</li>
 *   <li><b>长字符串/长注释开头的第一个换行被吞掉</b>，且中间的
 *       {@code \r\n} / {@code \n\r} 统一归一成一个 {@code \n}。</li>
 * </ol>
 * <p>
 * 没有 {@code luaX_init}：C 版那里把保留字预先塞进字符串表并打上
 * {@code extra} 标记，这里保留字查一张静态 HashMap 即可。
 * <p>
 * 也<b>没有 {@code ls->h} 那张「避免长字符串被回收」的表</b>：C 版它同时兼作
 * 长字符串去重，但长字符串不驻留、比较一律按内容，去重与否不可观测，
 * 去重只是省内存。这里只做短字符串驻留（{@link StringPool}），语义一致。
 */
public final class Lexer {

    /**
     * 单字符 token 用自身编码表示，其余从这里开始（{@code UCHAR_MAX + 1}）。
     */
    public static final int FIRST_RESERVED = 256 + 1;

    /* ORDER RESERVED —— 顺序必须与 C 的 enum RESERVED 完全一致 */
    public static final int TK_AND = FIRST_RESERVED + 0;
    public static final int TK_BREAK = FIRST_RESERVED + 1;
    public static final int TK_DO = FIRST_RESERVED + 2;
    public static final int TK_ELSE = FIRST_RESERVED + 3;
    public static final int TK_ELSEIF = FIRST_RESERVED + 4;
    public static final int TK_END = FIRST_RESERVED + 5;
    public static final int TK_FALSE = FIRST_RESERVED + 6;
    public static final int TK_FOR = FIRST_RESERVED + 7;
    public static final int TK_FUNCTION = FIRST_RESERVED + 8;
    public static final int TK_GOTO = FIRST_RESERVED + 9;
    public static final int TK_IF = FIRST_RESERVED + 10;
    public static final int TK_IN = FIRST_RESERVED + 11;
    public static final int TK_LOCAL = FIRST_RESERVED + 12;
    public static final int TK_NIL = FIRST_RESERVED + 13;
    public static final int TK_NOT = FIRST_RESERVED + 14;
    public static final int TK_OR = FIRST_RESERVED + 15;
    public static final int TK_REPEAT = FIRST_RESERVED + 16;
    public static final int TK_RETURN = FIRST_RESERVED + 17;
    public static final int TK_THEN = FIRST_RESERVED + 18;
    public static final int TK_TRUE = FIRST_RESERVED + 19;
    public static final int TK_UNTIL = FIRST_RESERVED + 20;
    public static final int TK_WHILE = FIRST_RESERVED + 21;
    public static final int TK_IDIV = FIRST_RESERVED + 22;
    public static final int TK_CONCAT = FIRST_RESERVED + 23;
    public static final int TK_DOTS = FIRST_RESERVED + 24;
    public static final int TK_EQ = FIRST_RESERVED + 25;
    public static final int TK_GE = FIRST_RESERVED + 26;
    public static final int TK_LE = FIRST_RESERVED + 27;
    public static final int TK_NE = FIRST_RESERVED + 28;
    public static final int TK_SHL = FIRST_RESERVED + 29;
    public static final int TK_SHR = FIRST_RESERVED + 30;
    public static final int TK_DBCOLON = FIRST_RESERVED + 31;
    public static final int TK_EOS = FIRST_RESERVED + 32;
    public static final int TK_FLT = FIRST_RESERVED + 33;
    public static final int TK_INT = FIRST_RESERVED + 34;
    public static final int TK_NAME = FIRST_RESERVED + 35;
    public static final int TK_STRING = FIRST_RESERVED + 36;

    /** 保留字个数（{@code NUM_RESERVED}） */
    public static final int NUM_RESERVED = TK_WHILE - FIRST_RESERVED + 1;

    /** {@code luaX_tokens} */
    private static final String[] TOKENS = {
        "and", "break", "do", "else", "elseif",
        "end", "false", "for", "function", "goto", "if",
        "in", "local", "nil", "not", "or", "repeat",
        "return", "then", "true", "until", "while",
        "//", "..", "...", "==", ">=", "<=", "~=",
        "<<", ">>", "::", "<eof>",
        "<number>", "<integer>", "<name>", "<string>"
    };

    private static final Map<String, Integer> RESERVED = new HashMap<>();

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            RESERVED.put(TOKENS[i], FIRST_RESERVED + i);
        }
    }

    /** {@code SemInfo + Token}：语义信息随 token 类型不同落在不同的字段上 */
    public static final class Token {
        public int token = TK_EOS;
        /** TK_NAME / TK_STRING */
        public LuaString s;
        /** TK_INT */
        public long i;
        /** TK_FLT */
        public double r;

        void reset() {
            token = TK_EOS;
            s = null;
            i = 0;
            r = 0;
        }

        /** 快照：{@code next()} 之后 {@code t} 会被原地改写，需要留存的场合必须先复制 */
        public Token copy() {
            Token c = new Token();
            c.token = token;
            c.s = s;
            c.i = i;
            c.r = r;
            return c;
        }

        @Override
        public String toString() {
            switch (token) {
            case TK_NAME:
            case TK_STRING:
                return tokenName(token) + "(" + s.toJavaString() + ")";
            case TK_INT:
                return tokenName(token) + "(" + i + ")";
            case TK_FLT:
                return tokenName(token) + "(" + r + ")";
            default:
                return tokenName(token);
            }
        }
    }

    /**
     * {@code luaX_token2str}。
     * <p>
     * 保留字要带引号（{@code 'end'}），而 {@code <eof>} / {@code <name>} /
     * {@code <number>} / {@code <integer>} / {@code <string>} 不带——上游的分界点
     * 就是 {@code token < TK_EOS}。写反了消息会变成 {@code end expected}
     * 而不是 {@code 'end' expected}。
     */
    public static String tokenName(int token) {
        if (token < FIRST_RESERVED) {
            return LCType.isPrint(token) ? "'" + (char) token + "'" : "'<\\" + token + ">'";
        }
        String s = TOKENS[token - FIRST_RESERVED];
        return token < TK_EOS ? "'" + s + "'" : s;
    }

    /* ---------- 状态（对应 LexState） ---------- */

    private final Zio z;
    private final StringPool strings;

    /**
     * 源名的原始字节 —— 错误信息里用（{@code luaG_addinfo} 的 {@code src}）。
     * <p>
     * 存字节而不是 {@code String}：源名本身是 Lua 字符串（字节串），而错误信息里
     * 还会夹带 token 的原始字节（可能是非法 UTF-8）。两侧差分比对是<b>逐字节</b>
     * 比 stdout，任何一次「先解码成 String 再编码回去」都会在真实脚本上露出破绽。
     */
    private final byte[] sourceBytes;

    /** {@link #sourceBytes} 按 UTF-8 解码后的 Java 视图，仅用于日志与诊断 */
    public final String source;

    public int linenumber = 1;
    public int lastline = 1;

    /** 源名的原始字节（Lua 字符串是字节串，不当 {@code String} 处理） */
    public byte[] sourceBytes() {
        return sourceBytes;
    }

    /** 当前 token */
    public Token t = new Token();
    /** 前瞻 token（{@code lookahead.token == TK_EOS} 表示没有） */
    private Token lookahead = new Token();

    private int current;
    private byte[] buff = new byte[64];
    private int buffLen;

    public Lexer(byte[] data, String source) {
        this(new Zio(data), source, new StringPool());
    }

    public Lexer(Zio z, String source) {
        this(z, source, new StringPool());
    }

    public Lexer(Zio z, String source, StringPool strings) {
        this(z, (source == null ? "?" : source).getBytes(StandardCharsets.UTF_8), strings);
    }

    public Lexer(byte[] data, byte[] chunkName, StringPool strings) {
        this(new Zio(data), chunkName, strings);
    }

    public Lexer(Zio z, byte[] chunkName, StringPool strings) {
        this.z = z;
        this.strings = strings;
        this.sourceBytes = (chunkName == null) ? new byte[] { '?' } : chunkName;
        this.source = new String(sourceBytes, StandardCharsets.UTF_8);
        this.current = z.getc(); // firstchar
    }

    /* ---------- 字符与缓冲区 ---------- */

    private void nextChar() {
        current = z.getc();
    }

    private boolean currIsNewline() {
        return current == '\n' || current == '\r';
    }

    /** {@code save} */
    private void save(int c) {
        if (buffLen + 1 > buff.length) {
            if (buff.length >= Integer.MAX_VALUE / 2) {
                lexError("lexical element too long", 0);
            }
            buff = Arrays.copyOf(buff, buff.length * 2);
        }
        buff[buffLen++] = (byte) c;
    }

    /** {@code save_and_next} */
    private void saveAndNext() {
        save(current);
        nextChar();
    }

    private void resetBuffer() {
        buffLen = 0;
    }

    /** {@code luaZ_buffremove} */
    private void buffRemove(int n) {
        buffLen -= n;
    }

    private String bufferText() {
        return new String(buff, 0, buffLen, StandardCharsets.ISO_8859_1);
    }

    /* ---------- 错误 ---------- */

    /** {@code lexerror}：{@code source:line: msg[ near <token>]}，逐字节拼装 */
    private LuaError lexError(String msg, int token) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(sourceBytes, 0, sourceBytes.length);
        writeAscii(out, ":" + linenumber + ": " + msg);
        if (token != 0) {
            writeAscii(out, " near ");
            // 只有 <eof> 不带引号（上游对 TK_EOS 之后的「名字/字符串/数字」
            // 由 txtToken 补回引号，而 TK_EOS 本身不补）
            boolean quoted = token != TK_EOS;
            if (quoted) {
                writeAscii(out, "'");
            }
            switch (token) {
            case TK_NAME:
            case TK_STRING:
            case TK_FLT:
            case TK_INT:
                out.write(buff, 0, buffLen); // token 原始字节，可能是非法 UTF-8
                break;
            default:
                writeAscii(out, textOf(token));
                break;
            }
            if (quoted) {
                writeAscii(out, "'");
            }
        }
        LuaError e = new LuaError(LuaValue.ofString(LuaString.of(out.toByteArray())));
        throw e;
    }

    /** {@code luaX_syntaxerror} */
    public LuaError syntaxError(String msg) {
        return lexError(msg, t.token);
    }

    /**
     * {@code luaX_token2str} 去掉两侧引号后的内容。
     * <p>
     * 注意 {@code txtToken} 给单字符 token 加的引号是 '<' 和 '>' 之外的普通单引号，
     * 而 {@code token < FIRST_RESERVED} 且不可打印时形如 {@code '<\228>'}。
     */
    private static String textOf(int token) {
        if (token < FIRST_RESERVED) {
            return LCType.isPrint(token) ? String.valueOf((char) token) : "<\\" + token + ">";
        }
        return TOKENS[token - FIRST_RESERVED];
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        out.write(b, 0, b.length);
    }

    /* ---------- 对外接口 ---------- */

    /** {@code luaX_next} */
    public void next() {
        lastline = linenumber;
        if (lookahead.token != TK_EOS) {
            Token tmp = t;
            t = lookahead;
            lookahead = tmp;
            lookahead.reset();
        } else {
            t.token = llex(t);
        }
    }

    /** {@code luaX_lookahead} */
    public int lookahead() {
        lookahead.token = llex(lookahead);
        return lookahead.token;
    }

    public int token() {
        return t.token;
    }

    /** {@code luaX_newstring} */
    public LuaString newString(byte[] buf, int off, int len) {
        return strings.intern(LuaString.of(buf, off, len));
    }

    /* ---------- 常量缓存（lcode.c: addk 用的 ls->h） ---------- */

    /**
     * {@code ls->h}：<b>整份源码共享</b>的「常量 → 下标」缓存。
     * <p>
     * 共享（而不是每个函数一份）会带出两个反直觉的行为，都必须照搬，否则编出来的
     * 常量表与参考实现不一致：
     * <ol>
     *   <li><b>跨函数会串味</b>。内层函数从 0 开始编号，写完就把缓存里的下标改成了
     *       内层的小下标；回到外层，外层拿这个下标一查——指向的是外层自己的另一个
     *       常量——校验失败，于是<b>同一个字符串再加一份</b>。上游的常量表里确实会
     *       出现重复项，不是优化漏了；</li>
     *   <li><b>整数与浮点在缓存里互相覆盖</b>。Lua 表用 {@code ==} 判键相等，所以
     *       {@code 1} 与 {@code 1.0} 是<b>同一个键</b>；命中后靠变体标签校验否决，
     *       但缓存项已经被 {@code 1.0} 的下标覆盖了——下次再写 {@code 1} 又会新建。
     *       这也是上游常量表会出现「看起来该复用却没复用」的原因。</li>
     * </ol>
     */
    private final Map<Object, Integer> constCache = new HashMap<>();

    /** nil 不能做 Lua 表的键，上游拿表自身当键；这里直接用一个哨兵对象 */
    private static final Object NIL_KEY = new Object();

    /**
     * 数值型缓存键，相等性按 Lua 的 {@code ==}（跨整数/浮点也成立）。
     * <p>
     * 不能用 {@code Double}：{@code Double.equals} 认为 {@code -0.0 != 0.0}、
     * {@code NaN == NaN}，而 Lua 恰恰相反（{@code -0.0 == 0.0} 成立；NaN 与
     * 任何值都不等，所以每个 NaN 字面量各占一个常量槽）。
     * <p>
     * 整数要留着原始的 {@code long}：超过 2^53 的整数转 double 会丢精度，
     * 两个不同的大整数会共用同一个哈希桶——那不要紧（{@link #equals} 判得准），
     * 但若只存 double 就会把两个不同的常量误判成同一个。
     */
    private static final class NumKey {
        private final boolean isInt;
        private final long i;
        private final double d;

        NumKey(boolean isInt, long i, double d) {
            this.isInt = isInt;
            this.i = i;
            this.d = d;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NumKey)) {
                return false;
            }
            NumKey k = (NumKey) o;
            if (isInt && k.isInt) {
                return i == k.i;
            }
            if (!isInt && !k.isInt) {
                return d == k.d;
            }
            return isInt ? ((double) i) == k.d : d == ((double) k.i);
        }

        @Override
        public int hashCode() {
            double v = isInt ? (double) i : d;
            long bits = Double.doubleToLongBits(v == 0.0 ? 0.0 : v);
            return (int) (bits ^ (bits >>> 32));
        }
    }

    /** double 的尾数位数（{@code DBL_MANT_DIG}） */
    private static final int MANT_DIG = 53;

    /**
     * 常量缓存键；{@code null} 表示这个值不该进缓存。
     * <p>
     * 整值浮点要换一个键（{@code r + r*2^-52}，{@code 0} 取 {@code 2^-52}），
     * 这是 {@code lcode.c: luaK_numberK} 里刻意的一步：Lua 的表用 {@code ==}
     * 判键，{@code 100000} 与 {@code 100000.0} 会撞成同一个键，于是两个常量
     * 互相把对方从缓存里挤掉，每写一次都新建一份。上游因此专门给整值浮点
     * 造了个「不太可能是整数」的替代键。
     */
    static Object constKey(LuaValue v) {
        if (v.isNil()) {
            return NIL_KEY;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isInteger()) {
            return new NumKey(true, v.i, 0);
        }
        if (v.isFloat()) {
            double r = v.n;
            Long ik = Arith.fltToInteger(r, Arith.F2I_EQ);
            if (ik == null) {
                return new NumKey(false, 0, r); // 非整值：用自身当键
            }
            double q = Math.scalb(1.0, -MANT_DIG + 1);
            return new NumKey(false, 0, (ik == 0L) ? q : r + r * q);
        }
        if (v.isString()) {
            return v.asString();
        }
        return null;
    }

    /**
     * {@code addk} 的查询一半：命中<b>且</b>校验通过才返回下标。
     * <p>
     * {@code k < fs->nk} 这一句不能省：缓存里的下标可能是内层函数留下的，
     * 对外层的常量表来说越界了。
     */
    Integer lookupConstant(FuncState fs, LuaValue v) {
        Object key = constKey(v);
        if (key == null) {
            return null;
        }
        Integer idx = constCache.get(key);
        if (idx == null || idx >= fs.f.sizek) {
            return null;
        }
        LuaValue existing = fs.f.k[idx];
        if (existing.tt != v.tt || !LuaValue.rawEqual(existing, v)) {
            return null;
        }
        return idx;
    }

    /** 追加一个常量并（覆盖）写进缓存，返回下标 */
    int addConstant(FuncState fs, LuaValue v) {
        int idx = fs.f.addConstant(v);
        Object key = constKey(v);
        if (key != null) {
            constCache.put(key, idx);
        }
        return idx;
    }

    /* ---------- 扫描 ---------- */

    /** {@code inclinenumber}：吃掉 {@code \n} / {@code \r} / {@code \n\r} / {@code \r\n} */
    private void inclinenumber() {
        int old = current;
        nextChar();
        if (currIsNewline() && current != old) {
            nextChar();
        }
        if (++linenumber >= Integer.MAX_VALUE) {
            lexError("chunk has too many lines", 0);
        }
    }

    private boolean checkNext1(int c) {
        if (current == c) {
            nextChar();
            return true;
        }
        return false;
    }

    private boolean checkNext2(String set) {
        if (current == set.charAt(0) || current == set.charAt(1)) {
            saveAndNext();
            return true;
        }
        return false;
    }

    /**
     * {@code read_numeral}。
     * <p>
     * 这里刻意「很宽松」，真正的格式校验交给 {@link Numeral#str2num}：
     * 数字后面紧跟字母时（{@code 1a}、{@code 0x}）多读一个字符进来，
     * 让 {@code str2num} 必然失败，从而报出上游同款 malformed number。
     */
    private int readNumeral(Token sem) {
        String expo = "Ee";
        int first = current;
        saveAndNext();
        if (first == '0' && checkNext2("xX")) {
            expo = "Pp"; // 十六进制的指数标记是 p
        }
        for (;;) {
            if (checkNext2(expo)) {
                checkNext2("-+"); // 指数符号只在指数标记之后才合法
            } else if (LCType.isXdigit(current) || current == '.') {
                saveAndNext();
            } else {
                break;
            }
        }
        if (LCType.isAlpha(current)) {
            saveAndNext(); // 强制报错
        }
        LuaValue obj = Numeral.str2num(bufferText());
        if (obj == null) {
            lexError("malformed number", TK_FLT);
        }
        if (obj.isInteger()) {
            sem.i = obj.i;
            return TK_INT;
        }
        sem.r = obj.n;
        return TK_FLT;
    }

    /**
     * {@code skip_sep}：读 {@code [=*[} 或 {@code ]=*]}。
     *
     * @return '=' 个数 + 2；单个括号返回 1；{@code [==} 这种残缺返回 0
     */
    private int skipSep() {
        int count = 0;
        int s = current;
        saveAndNext();
        while (current == '=') {
            saveAndNext();
            count++;
        }
        return (current == s) ? count + 2 : (count == 0) ? 1 : 0;
    }

    /** {@code read_long_string}；{@code sem == null} 表示这是长注释 */
    private void readLongString(Token sem, int sep) {
        int line = linenumber;
        saveAndNext(); // 跳过第二个 '['
        if (currIsNewline()) {
            inclinenumber(); // 紧跟在开头的换行被吞掉
        }
        for (;;) {
            if (current == Zio.EOZ) {
                String what = (sem != null) ? "string" : "comment";
                lexError("unfinished long " + what + " (starting at line " + line + ")", TK_EOS);
            } else if (current == ']') {
                if (skipSep() == sep) {
                    saveAndNext(); // 跳过第二个 ']'
                    break;
                }
            } else if (current == '\n' || current == '\r') {
                save('\n'); // 归一化换行
                inclinenumber();
                if (sem == null) {
                    resetBuffer(); // 长注释不攒内容
                }
            } else {
                if (sem != null) {
                    saveAndNext();
                } else {
                    nextChar();
                }
            }
        }
        if (sem != null) {
            sem.s = newString(buff, sep, buffLen - 2 * sep);
        }
    }

    /** {@code esccheck} */
    private void esccheck(boolean ok, String msg) {
        if (!ok) {
            if (current != Zio.EOZ) {
                saveAndNext(); // 把出错字符放进缓冲区，好让错误信息带上它
            }
            lexError(msg, TK_STRING);
        }
    }

    private int gethexa() {
        saveAndNext();
        esccheck(LCType.isXdigit(current), "hexadecimal digit expected");
        return Numeral.hexaValue(current);
    }

    private int readhexaesc() {
        int r = gethexa();
        r = (r << 4) + gethexa();
        buffRemove(2);
        return r;
    }

    private long readutf8esc() {
        long r;
        int i = 4; // 要移除的字符：'\', 'u', '{', 和第一个数字
        saveAndNext(); // 跳过 'u'
        esccheck(current == '{', "missing '{'");
        r = gethexa(); // 至少要有一个数字
        while (true) {
            saveAndNext();
            if (!LCType.isXdigit(current)) {
                break;
            }
            i++;
            esccheck(r <= (0x7FFFFFFFL >> 4), "UTF-8 value too large");
            r = (r << 4) + Numeral.hexaValue(current);
        }
        esccheck(current == '}', "missing '}'");
        nextChar(); // 跳过 '}'
        buffRemove(i);
        return r;
    }

    private void utf8esc() {
        byte[] b = new byte[Numeral.UTF8BUFFSZ];
        int n = Numeral.utf8esc(readutf8esc(), b);
        for (; n > 0; n--) {
            save(b[Numeral.UTF8BUFFSZ - n]);
        }
    }

    private int readdecesc() {
        int i;
        int r = 0;
        for (i = 0; i < 3 && LCType.isDigit(current); i++) {
            r = 10 * r + current - '0';
            saveAndNext();
        }
        esccheck(r <= 255, "decimal escape too large");
        buffRemove(i);
        return r;
    }

    /**
     * {@code read_string}。
     * <p>
     * C 版用 {@code goto read_save / only_save / no_save} 表达「先读、只写、
     * 不写」三种收尾；Java 没有 goto，这里用一个小整数 {@code how} 表达同一件事，
     * 顺序严格保持 2=先读后写、1=只写、0=不写。
     */
    private void readString(int del, Token sem) {
        saveAndNext(); // 保留起始引号，错误信息里要用
        while (current != del) {
            if (current == Zio.EOZ) {
                lexError("unfinished string", TK_EOS);
            } else if (current == '\n' || current == '\r') {
                lexError("unfinished string", TK_STRING);
            } else if (current == '\\') {
                int c = 0;
                int how = 0;
                saveAndNext(); // 保留 '\\'，错误信息里要用
                switch (current) {
                case 'a':
                    c = 7;
                    how = 2;
                    break;
                case 'b':
                    c = 8;
                    how = 2;
                    break;
                case 'f':
                    c = 12;
                    how = 2;
                    break;
                case 'n':
                    c = '\n';
                    how = 2;
                    break;
                case 'r':
                    c = '\r';
                    how = 2;
                    break;
                case 't':
                    c = '\t';
                    how = 2;
                    break;
                case 'v':
                    c = 11;
                    how = 2;
                    break;
                case 'x':
                    c = readhexaesc();
                    how = 2;
                    break;
                case 'u':
                    utf8esc();
                    how = 0;
                    break;
                case '\n':
                case '\r':
                    inclinenumber();
                    c = '\n';
                    how = 1;
                    break;
                case '\\':
                case '"':
                case '\'':
                    c = current;
                    how = 2;
                    break;
                case Zio.EOZ:
                    how = 0; // 下一轮循环会报错
                    break;
                case 'z': { // 吞掉后面一整段空白
                    buffRemove(1); // 移除 '\\'
                    nextChar(); // 跳过 'z'
                    while (LCType.isSpace(current)) {
                        if (currIsNewline()) {
                            inclinenumber();
                        } else {
                            nextChar();
                        }
                    }
                    how = 0;
                    break;
                }
                default:
                    esccheck(LCType.isDigit(current), "invalid escape sequence");
                    c = readdecesc();
                    how = 1;
                    break;
                }
                if (how == 2) {
                    nextChar();
                }
                if (how >= 1) {
                    buffRemove(1); // 移除 '\\'
                    save(c);
                }
            } else {
                saveAndNext();
            }
        }
        saveAndNext(); // 跳过结束引号
        sem.s = newString(buff, 1, buffLen - 2);
    }

    /** {@code llex} */
    private int llex(Token sem) {
        resetBuffer();
        for (;;) {
            switch (current) {
            case '\n':
            case '\r':
                inclinenumber();
                break;
            case ' ':
            case '\f':
            case '\t':
            case 11: // '\v'（Java 没有 \v 字符转义）
                nextChar();
                break;
            case '-': {
                nextChar();
                if (current != '-') {
                    return '-';
                }
                nextChar(); // 是注释
                if (current == '[') { // 长注释？
                    int sep = skipSep();
                    resetBuffer(); // skipSep 可能弄脏缓冲区
                    if (sep >= 2) {
                        readLongString(null, sep);
                        resetBuffer();
                        break;
                    }
                }
                // 短注释
                while (!currIsNewline() && current != Zio.EOZ) {
                    nextChar();
                }
                break;
            }
            case '[': {
                int sep = skipSep();
                if (sep >= 2) {
                    readLongString(sem, sep);
                    return TK_STRING;
                } else if (sep == 0) {
                    lexError("invalid long string delimiter", TK_STRING);
                }
                return '[';
            }
            case '=':
                nextChar();
                if (checkNext1('=')) {
                    return TK_EQ;
                }
                return '=';
            case '<':
                nextChar();
                if (checkNext1('=')) {
                    return TK_LE;
                } else if (checkNext1('<')) {
                    return TK_SHL;
                }
                return '<';
            case '>':
                nextChar();
                if (checkNext1('=')) {
                    return TK_GE;
                } else if (checkNext1('>')) {
                    return TK_SHR;
                }
                return '>';
            case '/':
                nextChar();
                if (checkNext1('/')) {
                    return TK_IDIV;
                }
                return '/';
            case '~':
                nextChar();
                if (checkNext1('=')) {
                    return TK_NE;
                }
                return '~';
            case ':':
                nextChar();
                if (checkNext1(':')) {
                    return TK_DBCOLON;
                }
                return ':';
            case '"':
            case '\'':
                readString(current, sem);
                return TK_STRING;
            case '.':
                saveAndNext();
                if (checkNext1('.')) {
                    if (checkNext1('.')) {
                        return TK_DOTS;
                    }
                    return TK_CONCAT;
                } else if (!LCType.isDigit(current)) {
                    return '.';
                }
                return readNumeral(sem);
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return readNumeral(sem);
            case Zio.EOZ:
                return TK_EOS;
            default: {
                if (LCType.isAlpha(current)) { // 标识符或保留字
                    do {
                        saveAndNext();
                    } while (LCType.isAlnum(current));
                    LuaString ts = newString(buff, 0, buffLen);
                    sem.s = ts;
                    Integer reserved = RESERVED.get(ts.toJavaString());
                    if (reserved != null) {
                        return reserved;
                    }
                    return TK_NAME;
                }
                // 单字符 token：'+', '*', '%', '{', '}', ...
                int c = current;
                nextChar();
                return c;
            }
            }
        }
    }
}
