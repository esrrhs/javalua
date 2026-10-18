package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 读预编译 chunk —— 对应 {@code lundump.c}。
 * <p>
 * 与 {@link Parser} 是并列的两条加载路径：{@code f_parser} 先看第一个字节，
 * 是 {@code LUA_SIGNATURE[0]}（{@code \27}）就走这里，否则走词法／语法分析。
 * <b>没有这条路径，{@code string.dump} 的产物就回不来</b>，而
 * {@code luac} 预编译过的发行脚本是相当常见的形态，所以这不是可选项。
 *
 * <h2>格式</h2>
 * 头部：签名 {@code \27Lua}（首字节已被 {@code f_parser} 吃掉）、版本、格式号、
 * 6 字节校验串 {@code \x19\x93\r\n\x1a\n}、三个尺寸字节、以及一个整数与一个
 * 浮点用于抓字节序／端序转换错误。之后是一颗函数：源名、行号范围、参数信息、
 * 指令、常量、upvalue、内嵌函数、调试信息。
 * <p>
 * 长度用的是「7 位一组、最高位置 1 表示最后一组」的变长编码，<b>大端在前</b>；
 * 整数与浮点都是 8 字节小端原样铺开。
 *
 * <h2>去掉的部分</h2>
 * {@code loadString} 里给长字符串上的 {@code luaC_objbarrier}、以及
 * {@code luai_verifycode}，都是给 {@code lgc.c} 用的，这里没有 GC，不需要。
 */
public final class Undump {

    private Undump() {
    }

    /* ---------- 常量 ---------- */

    /** {@code LUA_SIGNATURE} */
    private static final byte[] SIGNATURE = { 0x1b, 'L', 'u', 'a' };

    /** {@code LUAC_VERSION}：主版本号放高 4 位、次版本号放低 4 位（504 → 0x54） */
    private static final int LUAC_VERSION = 0x54;

    /** {@code LUAC_FORMAT}：官方格式 */
    private static final int LUAC_FORMAT = 0;

    /** {@code LUAC_DATA}：用来抓文本／二进制转换错误的校验串 */
    private static final byte[] LUAC_DATA = { 0x19, (byte) 0x93, '\r', '\n', 0x1a, '\n' };

    /** {@code LUAC_INT}：整数格式的探针值 */
    private static final long LUAC_INT = 0x5678L;

    /** {@code LUAC_NUM}：浮点格式的探针值 */
    private static final double LUAC_NUM = 370.5;

    /** {@code sizeof(Instruction)} */
    private static final int SIZE_INSTRUCTION = 4;

    /** {@code sizeof(lua_Integer)} */
    private static final int SIZE_INTEGER = 8;

    /** {@code sizeof(lua_Number)} */
    private static final int SIZE_NUMBER = 8;

    /** {@code LUAI_MAXSHORTLEN} */
    private static final int MAXSHORTLEN = 40;

    /**
     * 长字符串声明长度的上限（128 TiB）。
     * <p>
     * 走到长字符串分支时，C 是 {@code luaS_createlngstrobj(L, size)} 整块 malloc：
     * 成了就接着读、读到流末尾报 {@code truncated chunk}；失败就抛
     * {@code LUA_ERRMEM}，消息是 {@code globalState} 里写死的那份
     * {@code "not enough memory"}（<b>不带位置前缀</b>）。
     * <p>
     * 「成不成」取决于机器内存与过量提交策略，不是 Lua 的语义，所以这里只能
     * 取一个定值。取 128 TiB 的理由是两头都够远：正常 chunk 里不会有一条上百
     * TB 的字符串常量，而这么大的申请在任何机器上都会失败，于是这一档能
     * 与参考实现对齐。夹在中间的那一档（十几 GB 到上百 TB，取决于内存）
     * <b>观测不到</b>，不进差分表。
     */
    private static final long MAX_LONG_STRING = 1L << 47;

    /* ---------- LoadState ---------- */

    private static final class LoadState {
        final LuaState L;
        final Zio z;
        /** 错误信息里用的源名（已按 {@code luaU_undump} 的规则整理过） */
        final byte[] name;

        LoadState(LuaState L, Zio z, byte[] name) {
            this.L = L;
            this.z = z;
            this.name = name;
        }
    }

    /** {@code lundump.c: error}：{@code "<name>: bad binary format (<why>)"} */
    private static LuaError error(LoadState S, String why) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(S.name, 0, S.name.length);
        byte[] tail = (": bad binary format (" + why + ")").getBytes(StandardCharsets.UTF_8);
        out.write(tail, 0, tail.length);
        LuaError e = new LuaError(LuaValue.ofString(LuaString.of(out.toByteArray())));
        // 与 lexerror 同一条路：luaD_throw(LUA_ERRSYNTAX) 直跳，不过 errfunc
        e.directThrow = true;
        throw e;
    }

    /* ---------- 基本读取 ---------- */

    private static void loadBlock(LoadState S, byte[] b, int off, int size) {
        for (int i = 0; i < size; i++) {
            int c = S.z.getc();
            if (c == Zio.EOZ) {
                error(S, "truncated chunk");
            }
            b[off + i] = (byte) c;
        }
    }

    private static int loadByte(LoadState S) {
        int b = S.z.getc();
        if (b == Zio.EOZ) {
            error(S, "truncated chunk");
        }
        return b;
    }

    /**
     * 变长无符号数：7 位一组、大端在前，最高位置 1 的那组是最后一组。
     * <p>
     * {@code limit >>= 7} 之后再比，是 C 版防溢出的写法——照搬，否则
     * 一个恶意 chunk 能用一堆 {@code 0x7f} 把 {@code x} 顶到回绕。
     */
    private static long loadUnsigned(LoadState S, long limit) {
        long x = 0;
        int b;
        /*
         * 必须用<b>逻辑</b>右移：limit 传进来的是 C 的 {@code size_t} 上限，
         * 而 {@code MAX_SIZET} 是 {@code ~(size_t)0}（全 1），不是
         * {@code Long.MAX_VALUE}。两者 {@code >>7} 之后差一整倍——
         * 用 {@code Long.MAX_VALUE} 会在第 9 组就误报 integer overflow，
         * 而参考实现那时要到第 10 组才报（第 9 组之后是截断）。
         */
        limit >>>= 7;
        do {
            b = loadByte(S);
            /*
             * C 里 {@code x} 和 {@code limit} 都是 {@code size_t}，这个
             * {@code x >= limit} 是<b>无符号</b>比较。这里必须用
             * {@code compareUnsigned}：变长整数被一堆 {@code 0x00} 顶到第 10
             * 组时会跨过 {@code 2^63}、折成负数，有符号比较就<b>认不出溢出</b>
             * ——于是继续往下读，最终报 truncated chunk，而参考实现报
             * integer overflow。这是第 14 条（C 的 {@code unsigned} 一律按
             * 无符号处理）的第四例，前三例是 tunpack / checkStack / sort 枢轴。
             */
            if (Long.compareUnsigned(x, limit) >= 0) {
                error(S, "integer overflow");
            }
            x = (x << 7) | (b & 0x7f);
        } while ((b & 0x80) == 0);
        return x;
    }

    /**
     * C 的 {@code MAX_SIZET}（{@code ~(size_t)0}，64 位全 1）。
     * <p>
     * Java 的 {@code long} 没有无符号，全 1 就是 {@code -1}；配合
     * {@link #loadUnsigned} 里的 {@code >>>} 正好得到 C 的那个上限。
     */
    private static final long MAX_SIZET = -1L;

    /**
     * 返回 {@code size_t}。Java 没有无符号 64 位，用 {@code long} 接，
     * 由调用方（只有 {@link #loadStringN}）按实际能读到的字节数决定怎么存。
     * <p>
     * <b>这里不能对「大到 Java 数组放不下」的长度提前报错。</b>上一版就是这么
     * 写的，注释还断言「C 会去分配并抛 {@code LUA_ERRMEM}」——照着 C 源码读
     * 出来的结论，实测是错的：把源名长度写成 {@code 2^35-1}（变长整数
     * {@code 7f 7f 7f 7f ff}），参考实现给的是 {@code truncated chunk} 而不是
     * 内存错误。64 位上 malloc 34GB 是成功的，C 于是老老实实去读那 34GB，
     * 读到流末尾报截断。提前报 {@code integer overflow} 等于凭空多出一个
     * C 里不存在的分支，白白对不上一个用例。
     */
    private static long loadSize(LoadState S) {
        return loadUnsigned(S, MAX_SIZET);
    }

    private static int loadInt(LoadState S) {
        return (int) loadUnsigned(S, Integer.MAX_VALUE);
    }

    private static double loadNumber(LoadState S) {
        return Double.longBitsToDouble(loadInteger(S));
    }

    private static long loadInteger(LoadState S) {
        byte[] b = new byte[SIZE_INTEGER];
        loadBlock(S, b, 0, SIZE_INTEGER);
        long v = 0;
        for (int i = SIZE_INTEGER - 1; i >= 0; i--) { // 小端
            v = (v << 8) | (b[i] & 0xFF);
        }
        return v;
    }

    /* ---------- 字符串 ---------- */

    /** {@code loadStringN}：可为 null 的字符串。size==0 表示没有 */
    private static LuaString loadStringN(LoadState S, Proto p) {
        long size = loadSize(S);
        if (size == 0) {
            return null;
        }
        size--; // 存的是 len+1，好让 0 表示「没有」
        LuaString ts;
        // 同上：C 的 size 是 size_t，这个比较也是无符号的
        if (Long.compareUnsigned(size, MAXSHORTLEN) <= 0) {
            byte[] buff = new byte[(int) size];
            if (size > 0) {
                loadBlock(S, buff, 0, (int) size);
            }
            ts = LuaString.of(buff);
        } else {
            /*
             * 声明的长度越过 {@link #MAX_LONG_STRING} 时，C 那头的 malloc
             * 必然失败，于是报 {@code nil, "not enough memory"}；这里照做
             * ——注意消息<b>不带位置前缀</b>，与参考实现一致。
             * <p>
             * 没越线就<b>边读边攒</b>：攒多少只看流里真有多少，而不是声明了
             * 多少（声明值来自不可信输入，真拿去预分配会 OOM）；流一尽照样由
             * {@code loadBlock} 报 truncated chunk。
             */
            if (Long.compareUnsigned(size, MAX_LONG_STRING) > 0) {
                // 同上：C 的 malloc 失败是 luaM_error（LUA_ERRMEM），不是普通错误
                LuaError e = new LuaError(
                        LuaValue.ofString(LuaString.latin1("not enough memory")));
                e.errMem = true;
                throw e;
            }
            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (long left = size; left > 0;) {
                int n = (int) Math.min(left, block.length);
                loadBlock(S, block, 0, n); // 中途流尽 → truncated chunk
                acc.write(block, 0, n);
                left -= n;
            }
            ts = LuaString.of(acc.toByteArray());
        }
        if (ts.isShort()) {
            ts = S.L.strings.intern(ts);
        }
        return ts;
    }

    /** {@code loadString}：不可为 null，取到 null 就是格式错 */
    private static LuaString loadString(LoadState S, Proto p) {
        LuaString st = loadStringN(S, p);
        if (st == null) {
            error(S, "bad format for constant string");
        }
        return st;
    }

    /* ---------- 惰性增长 ---------- */

    /**
     * 下面几段都要「按流里声明的个数建数组」。畸形 chunk 可以声明 20 亿个
     * upvalue / 常量 / 指令，而 C 那头的 {@code luaM_reallocvectorchecked}
     * 是整块 malloc —— Linux/macOS 有过量提交，这种 malloc 往往<b>成功</b>
     * （只虚占地址空间，真正触碰的只有流里那几十个字节），于是接着读、读到流
     * 末尾报 {@code truncated chunk}。
     * <p>
     * Java 里 {@code new X[2000000000]} 是<b>真的</b>要那么多堆，抛的是
     * {@code OutOfMemoryError}——{@code Error} 不是 {@code LuaError}，会穿过
     * {@code pcall} 把宿主进程整个掀掉（第 4 条）。实测就是这样炸的：把源名
     * 长度那个变长整数改坏，参考给 {@code nil, "not enough memory"}，我们直接
     * OOM 崩。
     * <p>
     * 所以一律改成<b>按需增长</b>：实际占用只跟流里真有的字节成正比，正好等于
     * C 真正触碰到的那些页；流一尽照样由 {@code loadBlock} 报
     * {@code truncated chunk}，与参考实现一致。
     */
    private static final int MIN_CAP = 8;

    /** 从 {@code cur} 起翻倍，返回能装下 {@code want} 个元素的容量。 */
    private static int grown(int cur, int want) {
        int c = cur <= 0 ? MIN_CAP : cur;
        while (c < want) {
            if (c >= (Integer.MAX_VALUE >> 1)) {
                return want; // 到头了：够一个是一个，剩下的交给 loadBlock 报错
            }
            c <<= 1;
        }
        return c;
    }

    /* ---------- 各段 ---------- */

    /**
     * 指令逐条读，缓冲复用。
     * <p>
     * 上一版是先 {@code new byte[n * 4]} 整块读再拆。两个问题：
     * <ul>
     *   <li><b>{@code n * 4} 会整数溢出</b>。{@code n} 来自 {@code loadInt}，
     *       上限受 {@code loadUnsigned} 约束，但那层约束一松（把
     *       {@code limit >>>= 7} 写错一点点就够），{@code n * 4} 立刻变负数，
     *       {@code new byte[-1073676284]} 抛的是
     *       {@code NegativeArraySizeException}——<b>Java 异常，
     *       {@code pcall} 抓不住</b>，一个畸形 chunk 就能把宿主进程炸掉。</li>
     *   <li><b>多占一倍内存</b>。C 只分配 {@code n} 条指令；这边额外再铺
     *       {@code 4n} 字节，等于照声明的尺寸双倍付款，而这个尺寸是
     *       不可信输入给的。</li>
     * </ul>
     * 逐条读既不溢出、也只按 {@code n} 分配；流一尽照样由
     * {@code loadBlock} 报 {@code truncated chunk}，与参考实现一致。
     */
    private static void loadCode(LoadState S, Proto f) {
        int n = loadInt(S);
        f.code = new int[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizecode = n;
        byte[] b = new byte[SIZE_INSTRUCTION];
        for (int i = 0; i < n; i++) {
            if (i >= f.code.length) {
                f.code = Arrays.copyOf(f.code, grown(f.code.length, i + 1));
            }
            loadBlock(S, b, 0, SIZE_INSTRUCTION);
            int v = 0;
            for (int k = SIZE_INSTRUCTION - 1; k >= 0; k--) { // 小端
                v = (v << 8) | (b[k] & 0xFF);
            }
            f.code[i] = v;
        }
        if (f.code.length != n) {
            f.code = Arrays.copyOf(f.code, Math.max(n, 0));
        }
    }

    private static void loadConstants(LoadState S, Proto f) {
        int n = loadInt(S);
        f.k = new LuaValue[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizek = n;
        // 建一个、立刻解析一个。分成「先铺 nil」「再解析」两趟的话，
        // 第一趟不读流，畸形计数会直接把堆吃光（实测 OOM）。
        for (int i = 0; i < n; i++) {
            if (i >= f.k.length) {
                f.k = Arrays.copyOf(f.k, grown(f.k.length, i + 1));
            }
            LuaValue o = LuaValue.ofNil();
            f.k[i] = o;
            int t = loadByte(S);
            switch (t) {
            case Lua.VNIL:
                o.setNil();
                break;
            case Lua.VFALSE:
                o.setBoolean(false);
                break;
            case Lua.VTRUE:
                o.setBoolean(true);
                break;
            case Lua.VNUMFLT:
                o.setFloat(loadNumber(S));
                break;
            case Lua.VNUMINT:
                o.setInteger(loadInteger(S));
                break;
            case Lua.VSHRSTR:
            case Lua.VLNGSTR:
                o.setString(loadString(S, f));
                break;
            default:
                /*
                 * C 这里是 {@code default: lua_assert(0)}，而 {@code lua_assert}
                 * 在默认构建下是 {@code ((void)0)}——也就是说<b>不报错</b>，
                 * 那个常量就停在循环开头铺好的 nil 上。实测：
                 * 常量类型字节写 99 的 chunk 能被正常加载成一个函数。
                 * 改成报错看着更「安全」，但那是我们自己发明的语义。
                 */
                break;
            }
        }
        if (f.k.length != n) {
            f.k = Arrays.copyOf(f.k, Math.max(n, 0));
        }
    }

    private static void loadProtos(LoadState S, Proto f) {
        int n = loadInt(S);
        f.p = new Proto[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizep = n;
        /*
         * C 是先整块 malloc n 个指针、再逐个加载；这里把两步合一，
         * 「建一个、立刻加载一个」。声明的个数不可信，但流里的字节数可信——
         * 所以按需建既能对上 C 的结果（读到流末尾报 truncated chunk），
         * 又不会为一个畸形数字去要几十 GB 堆。
         */
        for (int i = 0; i < n; i++) {
            if (i >= f.p.length) {
                f.p = Arrays.copyOf(f.p, grown(f.p.length, i + 1));
            }
            f.p[i] = new Proto();
            loadFunction(S, f.p[i], f.source);
        }
        if (f.p.length != n) {
            f.p = Arrays.copyOf(f.p, Math.max(n, 0));
        }
    }

    private static void loadUpvalues(LoadState S, Proto f) {
        int n = loadInt(S);
        f.upvalues = new Proto.Upvaldesc[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizeupvalues = n;
        // 同上：建一个、读一个，别先照声明的个数全铺开
        for (int i = 0; i < n; i++) {
            if (i >= f.upvalues.length) {
                f.upvalues = Arrays.copyOf(f.upvalues, grown(f.upvalues.length, i + 1));
            }
            Proto.Upvaldesc u = new Proto.Upvaldesc();
            u.instack = loadByte(S) != 0;
            u.idx = loadByte(S);
            u.kind = loadByte(S);
            f.upvalues[i] = u;
        }
        if (f.upvalues.length != n) {
            f.upvalues = Arrays.copyOf(f.upvalues, Math.max(n, 0));
        }
    }

    private static void loadDebug(LoadState S, Proto f) {
        int n = loadInt(S);
        f.lineinfo = new byte[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizelineinfo = n;
        /*
         * 行号表是<b>整块</b> loadBlock 的，不像上面那几段可以「读一个建一个」。
         * 所以换成 8192 一块地攒——声明的长度不可信，攒多少只看流里真有多少；
         * 流一尽同样报 truncated chunk。
         */
        int li = 0;
        byte[] lblk = new byte[8192];
        for (long left = n; left > 0;) {
            int k = (int) Math.min(left, lblk.length);
            loadBlock(S, lblk, 0, k);
            if (li + k > f.lineinfo.length) {
                f.lineinfo = Arrays.copyOf(f.lineinfo, grown(f.lineinfo.length, li + k));
            }
            System.arraycopy(lblk, 0, f.lineinfo, li, k);
            li += k;
            left -= k;
        }
        n = loadInt(S);
        f.abslineinfo = new Proto.AbsLineInfo[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizeabslineinfo = n;
        for (int i = 0; i < n; i++) {
            int pc = loadInt(S);
            int line = loadInt(S);
            if (i >= f.abslineinfo.length) {
                f.abslineinfo = Arrays.copyOf(f.abslineinfo, grown(f.abslineinfo.length, i + 1));
            }
            f.abslineinfo[i] = new Proto.AbsLineInfo(pc, line);
        }
        if (f.abslineinfo.length != n) {
            f.abslineinfo = Arrays.copyOf(f.abslineinfo, Math.max(n, 0));
        }
        n = loadInt(S);
        f.locvars = new Proto.LocVar[Math.min(Math.max(n, 0), MIN_CAP)];
        f.sizelocvars = n;
        // 同样合成一趟：先建 LocVar 再立刻解析，别分两趟
        for (int i = 0; i < n; i++) {
            if (i >= f.locvars.length) {
                f.locvars = Arrays.copyOf(f.locvars, grown(f.locvars.length, i + 1));
            }
            Proto.LocVar v = new Proto.LocVar();
            f.locvars[i] = v;
            v.varname = loadStringN(S, f);
            v.startpc = loadInt(S);
            v.endpc = loadInt(S);
        }
        if (f.locvars.length != n) {
            f.locvars = Arrays.copyOf(f.locvars, Math.max(n, 0));
        }
        n = loadInt(S);
        if (n != 0) { // 有调试信息就必须给出全部 upvalue 名
            n = f.sizeupvalues;
        }
        for (int i = 0; i < n; i++) {
            f.upvalues[i].name = loadStringN(S, f);
        }
    }

    private static void loadFunction(LoadState S, Proto f, LuaString psource) {
        f.source = loadStringN(S, f);
        if (f.source == null) { // 这份 dump 里没有源名 → 沿用父函数的
            f.source = psource;
        }
        f.linedefined = loadInt(S);
        f.lastlinedefined = loadInt(S);
        f.numparams = loadByte(S);
        f.isVararg = loadByte(S) != 0;
        f.maxstacksize = loadByte(S);
        loadCode(S, f);
        loadConstants(S, f);
        loadUpvalues(S, f);
        loadProtos(S, f);
        loadDebug(S, f);
    }

    /* ---------- 头部校验 ---------- */

    private static void checkLiteral(LoadState S, byte[] s, String msg) {
        byte[] buff = new byte[s.length];
        loadBlock(S, buff, 0, buff.length);
        for (int i = 0; i < s.length; i++) {
            if (buff[i] != s[i]) {
                error(S, msg);
            }
        }
    }

    private static void checkSize(LoadState S, int size, String tname) {
        if (loadByte(S) != size) {
            error(S, tname + " size mismatch");
        }
    }

    private static void checkHeader(LoadState S) {
        // 首字节已被 f_parser 吃掉并比对过，这里从第二个字节开始
        byte[] rest = new byte[SIGNATURE.length - 1];
        System.arraycopy(SIGNATURE, 1, rest, 0, rest.length);
        checkLiteral(S, rest, "not a binary chunk");
        if (loadByte(S) != LUAC_VERSION) {
            error(S, "version mismatch");
        }
        if (loadByte(S) != LUAC_FORMAT) {
            error(S, "format mismatch");
        }
        checkLiteral(S, LUAC_DATA, "corrupted chunk");
        checkSize(S, SIZE_INSTRUCTION, "Instruction");
        checkSize(S, SIZE_INTEGER, "lua_Integer");
        checkSize(S, SIZE_NUMBER, "lua_Number");
        if (loadInteger(S) != LUAC_INT) {
            error(S, "integer format mismatch");
        }
        if (loadNumber(S) != LUAC_NUM) {
            error(S, "float format mismatch");
        }
    }

    /* ---------- 入口 ---------- */

    /**
     * {@code luaU_undump}。
     * <p>
     * 源名的整理规则看着怪，但必须照抄：以 {@code @} 或 {@code =} 开头就去掉那个
     * 前缀字符，以签名首字节开头（也就是<b>源名本身就是那段二进制 chunk</b>，
     * 正是 {@code load(s)} 不给第二个参数时的情形）则整段换成
     * {@code "binary string"}，其余原样。漏掉最后一条，
     * {@code load("\27Lua")} 报出的就会是一串 ESC 开头的乱码。
     */
    public static LClosure undump(LuaState L, Zio z, byte[] name) {
        byte[] sname = name;
        if (name.length > 0 && (name[0] == '@' || name[0] == '=')) {
            sname = new byte[name.length - 1];
            System.arraycopy(name, 1, sname, 0, sname.length);
        } else if (name.length > 0 && name[0] == SIGNATURE[0]) {
            sname = "binary string".getBytes(StandardCharsets.US_ASCII);
        }
        LoadState S = new LoadState(L, z, sname);
        checkHeader(S);
        int nupvals = loadByte(S);
        Proto p = new Proto();
        loadFunction(S, p, null);
        LClosure cl = new LClosure(p, nupvals);
        cl.initUpvals();
        // 第 0 号 upvalue 是 _ENV，与 lua_load 一致：装上全局表并关闭
        if (cl.upvals.length >= 1) {
            cl.upvals[0] = UpVal.closed(LuaValue.ofTable(L.globals));
        }
        return cl;
    }

    /** 把 {@code long} 按小端写进 8 字节（{@link #undump} 的对称操作，{@link Dump} 也用） */
    static void putInteger(byte[] b, int off, long v) {
        for (int i = 0; i < SIZE_INTEGER; i++) {
            b[off + i] = (byte) ((v >>> (8 * i)) & 0xFF);
        }
    }
}
