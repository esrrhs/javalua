package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;

/**
 * 写预编译 chunk —— 对应 {@code ldump.c}，{@link Undump} 的逆操作。
 * <p>
 * 对外只有一处入口：{@code string.dump}。{@code luac} 用的是同一份代码，
 * 但那是命令行工具，不在运行期里，这里不搬。
 *
 * <h2>{@code strip}</h2>
 * 剥掉调试信息（行号、局部变量名、upvalue 名）后，chunk 仍能正常执行，
 * 只是报错时没有行号：{@code Proto.getFuncline} 在 {@code sizelineinfo == 0}
 * 时返回 {@code -1}。剥的时候<b>源名也要一起剥</b>——否则行号没了而源名还在，
 * 反而暴露了它本该隐藏的东西。
 */
public final class Dump {

    private Dump() {
    }

    /** 写出器：{@code lua_Writer}，返回非 0 表示失败 */
    public interface Writer {
        int write(byte[] b, int off, int len);
    }

    private static final byte[] SIGNATURE = { 0x1b, 'L', 'u', 'a' };
    private static final int LUAC_VERSION = 0x54;
    private static final int LUAC_FORMAT = 0;
    private static final byte[] LUAC_DATA = { 0x19, (byte) 0x93, '\r', '\n', 0x1a, '\n' };
    private static final long LUAC_INT = 0x5678L;
    private static final double LUAC_NUM = 370.5;
    private static final int SIZE_INSTRUCTION = 4;
    private static final int SIZE_INTEGER = 8;
    private static final int SIZE_NUMBER = 8;

    /** 每个字节存 7 位，64 位最多 10 组 */
    private static final int DIBS = 10;

    private static final class DumpState {
        final Writer w;
        final boolean strip;
        int status;

        DumpState(Writer w, boolean strip) {
            this.w = w;
            this.strip = strip;
        }
    }

    private static void dumpBlock(DumpState D, byte[] b, int off, int size) {
        if (D.status == 0 && size > 0) {
            D.status = D.w.write(b, off, size);
        }
    }

    private static void dumpByte(DumpState D, int y) {
        dumpBlock(D, new byte[] { (byte) y }, 0, 1);
    }

    /** 变长无符号数：倒着填，最后一组的最高位置 1（与 {@link Undump} 的读法对称） */
    private static void dumpSize(DumpState D, long x) {
        byte[] buff = new byte[DIBS];
        int n = 0;
        do {
            buff[DIBS - (++n)] = (byte) (x & 0x7f);
            x >>>= 7;
        } while (x != 0);
        buff[DIBS - 1] |= (byte) 0x80;
        dumpBlock(D, buff, DIBS - n, n);
    }

    private static void dumpInt(DumpState D, int x) {
        dumpSize(D, x);
    }

    private static void dumpInteger(DumpState D, long x) {
        byte[] b = new byte[SIZE_INTEGER];
        Undump.putInteger(b, 0, x); // 小端
        dumpBlock(D, b, 0, SIZE_INTEGER);
    }

    private static void dumpNumber(DumpState D, double x) {
        dumpInteger(D, Double.doubleToRawLongBits(x));
    }

    private static void dumpString(DumpState D, LuaString s) {
        if (s == null) {
            dumpSize(D, 0);
        } else {
            dumpSize(D, s.length() + 1); // 0 留给「没有」
            dumpBlock(D, s.bytes, 0, s.length());
        }
    }

    private static void dumpCode(DumpState D, Proto f) {
        dumpInt(D, f.sizecode);
        byte[] b = new byte[f.sizecode * SIZE_INSTRUCTION];
        for (int i = 0; i < f.sizecode; i++) {
            int v = f.code[i];
            for (int k = 0; k < SIZE_INSTRUCTION; k++) { // 小端
                b[i * SIZE_INSTRUCTION + k] = (byte) ((v >>> (8 * k)) & 0xFF);
            }
        }
        dumpBlock(D, b, 0, b.length);
    }

    private static void dumpConstants(DumpState D, Proto f) {
        dumpInt(D, f.sizek);
        for (int i = 0; i < f.sizek; i++) {
            LuaValue o = f.k[i];
            int tt = o.tt;
            switch (tt) {
            case Lua.TNIL:
                dumpByte(D, Lua.VNIL);
                break;
            case Lua.TBOOLEAN:
                dumpByte(D, o.i != 0 ? Lua.VTRUE : Lua.VFALSE);
                break;
            case Lua.VNUMINT:
                dumpByte(D, Lua.VNUMINT);
                dumpInteger(D, o.i);
                break;
            case Lua.VNUMFLT:
                dumpByte(D, Lua.VNUMFLT);
                dumpNumber(D, o.n);
                break;
            case Lua.VSHRSTR:
            case Lua.VLNGSTR:
                dumpByte(D, tt);
                dumpString(D, (LuaString) o.o);
                break;
            default:
                // 常量表里只可能有这几种——Parser 不会别的东西塞进来
                throw new IllegalStateException("bad constant type " + tt);
            }
        }
    }

    private static void dumpProtos(DumpState D, Proto f) {
        dumpInt(D, f.sizep);
        for (int i = 0; i < f.sizep; i++) {
            dumpFunction(D, f.p[i], f.source);
        }
    }

    private static void dumpUpvalues(DumpState D, Proto f) {
        dumpInt(D, f.sizeupvalues);
        for (int i = 0; i < f.sizeupvalues; i++) {
            dumpByte(D, f.upvalues[i].instack ? 1 : 0);
            dumpByte(D, f.upvalues[i].idx);
            dumpByte(D, f.upvalues[i].kind);
        }
    }

    private static void dumpDebug(DumpState D, Proto f) {
        int n = D.strip ? 0 : f.sizelineinfo;
        dumpInt(D, n);
        dumpBlock(D, f.lineinfo, 0, n);
        n = D.strip ? 0 : f.sizeabslineinfo;
        dumpInt(D, n);
        for (int i = 0; i < n; i++) {
            dumpInt(D, f.abslineinfo[i].pc);
            dumpInt(D, f.abslineinfo[i].line);
        }
        n = D.strip ? 0 : f.sizelocvars;
        dumpInt(D, n);
        for (int i = 0; i < n; i++) {
            dumpString(D, f.locvars[i].varname);
            dumpInt(D, f.locvars[i].startpc);
            dumpInt(D, f.locvars[i].endpc);
        }
        n = D.strip ? 0 : f.sizeupvalues;
        dumpInt(D, n);
        for (int i = 0; i < n; i++) {
            dumpString(D, f.upvalues[i].name);
        }
    }

    private static void dumpFunction(DumpState D, Proto f, LuaString psource) {
        /*
         * 与父函数同源（或本来就剥调试信息）就不重复写源名。
         * <b>比的是引用而不是内容</b>——C 版是 {@code f->source == psource}，
         * 两个指针比。短字符串驻留之后这等价于比内容，长字符串则不驻留，
         * 于是 C 会为每个内嵌函数各写一遍源名；照内容比会写得比 C 少。
         */
        if (D.strip || f.source == psource) {
            dumpString(D, null);
        } else {
            dumpString(D, f.source);
        }
        dumpInt(D, f.linedefined);
        dumpInt(D, f.lastlinedefined);
        dumpByte(D, f.numparams);
        dumpByte(D, f.isVararg ? 1 : 0);
        dumpByte(D, f.maxstacksize);
        dumpCode(D, f);
        dumpConstants(D, f);
        dumpUpvalues(D, f);
        dumpProtos(D, f);
        dumpDebug(D, f);
    }

    private static void dumpHeader(DumpState D) {
        dumpBlock(D, SIGNATURE, 0, SIGNATURE.length);
        dumpByte(D, LUAC_VERSION);
        dumpByte(D, LUAC_FORMAT);
        dumpBlock(D, LUAC_DATA, 0, LUAC_DATA.length);
        dumpByte(D, SIZE_INSTRUCTION);
        dumpByte(D, SIZE_INTEGER);
        dumpByte(D, SIZE_NUMBER);
        dumpInteger(D, LUAC_INT);
        dumpNumber(D, LUAC_NUM);
    }

    /** {@code luaU_dump} */
    public static int dump(Proto f, Writer w, boolean strip) {
        DumpState D = new DumpState(w, strip);
        dumpHeader(D);
        dumpByte(D, f.sizeupvalues);
        dumpFunction(D, f, null);
        return D.status;
    }

    /** 把整颗函数树 dump 成字节数组；{@code string.dump} 用这个 */
    public static byte[] dumpBytes(Proto f, boolean strip) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dump(f, (b, off, len) -> {
            out.write(b, off, len);
            return 0;
        }, strip);
        return out.toByteArray();
    }

}
