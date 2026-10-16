package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
        throw new LuaError(LuaValue.ofString(LuaString.of(out.toByteArray())));
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
            if (x >= limit) {
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

    private static int loadSize(LoadState S) {
        long v = loadUnsigned(S, MAX_SIZET);
        /*
         * size_t 是 64 位，Java 的数组下标是 int。真拿到一个放不下的长度时，
         * C 会去分配并抛 {@code LUA_ERRMEM}；本实现没有「内存不足」这一类错误，
         * 而让它溢出成负数会更糟（{@code new byte[-1]} 抛的是 Java 异常，
         * 会被引擎吞成脚本错误）。退而报 integer overflow。
         */
        if (v > Integer.MAX_VALUE) {
            error(S, "integer overflow");
        }
        return (int) v;
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
        int size = loadSize(S);
        if (size == 0) {
            return null;
        }
        size--; // 存的是 len+1，好让 0 表示「没有」
        byte[] buff = new byte[size];
        if (size > 0) {
            loadBlock(S, buff, 0, size);
        }
        LuaString ts = LuaString.of(buff);
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

    /* ---------- 各段 ---------- */

    private static void loadCode(LoadState S, Proto f) {
        int n = loadInt(S);
        f.code = new int[Math.max(n, 0)];
        f.sizecode = n;
        byte[] b = new byte[n * SIZE_INSTRUCTION];
        loadBlock(S, b, 0, b.length);
        for (int i = 0; i < n; i++) {
            int v = 0;
            for (int k = SIZE_INSTRUCTION - 1; k >= 0; k--) { // 小端
                v = (v << 8) | (b[i * SIZE_INSTRUCTION + k] & 0xFF);
            }
            f.code[i] = v;
        }
    }

    private static void loadConstants(LoadState S, Proto f) {
        int n = loadInt(S);
        f.k = new LuaValue[Math.max(n, 0)];
        f.sizek = n;
        for (int i = 0; i < n; i++) {
            f.k[i] = LuaValue.ofNil();
        }
        for (int i = 0; i < n; i++) {
            LuaValue o = f.k[i];
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
    }

    private static void loadProtos(LoadState S, Proto f) {
        int n = loadInt(S);
        f.p = new Proto[Math.max(n, 0)];
        f.sizep = n;
        for (int i = 0; i < n; i++) {
            f.p[i] = new Proto();
        }
        for (int i = 0; i < n; i++) {
            loadFunction(S, f.p[i], f.source);
        }
    }

    private static void loadUpvalues(LoadState S, Proto f) {
        int n = loadInt(S);
        f.upvalues = new Proto.Upvaldesc[Math.max(n, 0)];
        f.sizeupvalues = n;
        for (int i = 0; i < n; i++) {
            f.upvalues[i] = new Proto.Upvaldesc();
        }
        for (int i = 0; i < n; i++) {
            f.upvalues[i].instack = loadByte(S) != 0;
            f.upvalues[i].idx = loadByte(S);
            f.upvalues[i].kind = loadByte(S);
        }
    }

    private static void loadDebug(LoadState S, Proto f) {
        int n = loadInt(S);
        f.lineinfo = new byte[Math.max(n, 0)];
        f.sizelineinfo = n;
        loadBlock(S, f.lineinfo, 0, n);
        n = loadInt(S);
        f.abslineinfo = new Proto.AbsLineInfo[Math.max(n, 0)];
        f.sizeabslineinfo = n;
        for (int i = 0; i < n; i++) {
            int pc = loadInt(S);
            int line = loadInt(S);
            f.abslineinfo[i] = new Proto.AbsLineInfo(pc, line);
        }
        n = loadInt(S);
        f.locvars = new Proto.LocVar[Math.max(n, 0)];
        f.sizelocvars = n;
        for (int i = 0; i < n; i++) {
            f.locvars[i] = new Proto.LocVar();
        }
        for (int i = 0; i < n; i++) {
            f.locvars[i].varname = loadStringN(S, f);
            f.locvars[i].startpc = loadInt(S);
            f.locvars[i].endpc = loadInt(S);
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
