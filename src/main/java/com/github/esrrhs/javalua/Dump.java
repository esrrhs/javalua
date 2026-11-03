package com.github.esrrhs.javalua;

import java.io.ByteArrayOutputStream;

/**
 * 写预编译 chunk —— Lua 5.3.4 {@code ldump.c}。
 * 固定宽度 4/8/4/8/8（int/size_t/Instruction/Integer/Number），小端。
 */
public final class Dump {

    private Dump() {
    }

    public interface Writer {
        int write(byte[] b, int off, int len);
    }

    private static final byte[] SIGNATURE = { 0x1b, 'L', 'u', 'a' };
    private static final int LUAC_VERSION = 0x53;
    private static final int LUAC_FORMAT = 0;
    private static final byte[] LUAC_DATA = { 0x19, (byte) 0x93, '\r', '\n', 0x1a, '\n' };
    private static final long LUAC_INT = 0x5678L;
    private static final double LUAC_NUM = 370.5;
    private static final int SIZE_INSTRUCTION = 4;
    private static final int SIZE_INTEGER = 8;
    private static final int SIZE_NUMBER = 8;

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

    private static void dumpInt(DumpState D, int x) {
        byte[] b = new byte[4];
        Undump.putInt32(b, 0, x);
        dumpBlock(D, b, 0, 4);
    }

    private static void dumpSizeT(DumpState D, long x) {
        byte[] b = new byte[8];
        Undump.putInteger(b, 0, x);
        dumpBlock(D, b, 0, 8);
    }

    private static void dumpInteger(DumpState D, long x) {
        byte[] b = new byte[SIZE_INTEGER];
        Undump.putInteger(b, 0, x);
        dumpBlock(D, b, 0, SIZE_INTEGER);
    }

    private static void dumpNumber(DumpState D, double x) {
        dumpInteger(D, Double.doubleToRawLongBits(x));
    }

    private static void dumpString(DumpState D, LuaString s) {
        if (s == null) {
            dumpByte(D, 0);
            return;
        }
        long size = s.length() + 1L;
        if (size < 0xFF) {
            dumpByte(D, (int) size);
        } else {
            dumpByte(D, 0xFF);
            dumpSizeT(D, size);
        }
        dumpBlock(D, s.bytes, 0, s.length());
    }

    private static void dumpCode(DumpState D, Proto f) {
        dumpInt(D, f.sizecode);
        byte[] b = new byte[f.sizecode * SIZE_INSTRUCTION];
        for (int i = 0; i < f.sizecode; i++) {
            int v = f.code[i];
            for (int k = 0; k < SIZE_INSTRUCTION; k++) {
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
                dumpByte(D, Lua.TNIL);
                break;
            case Lua.TBOOLEAN:
                dumpByte(D, Lua.TBOOLEAN);
                dumpByte(D, o.i != 0 ? 1 : 0);
                break;
            case Lua.VNUMFLT:
                dumpByte(D, Lua.VNUMFLT);
                dumpNumber(D, o.n);
                break;
            case Lua.VNUMINT:
                dumpByte(D, Lua.VNUMINT);
                dumpInteger(D, o.i);
                break;
            case Lua.VSHRSTR:
            case Lua.VLNGSTR:
                dumpByte(D, tt);
                dumpString(D, (LuaString) o.o);
                break;
            default:
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
        }
    }

    private static void dumpDebug(DumpState D, Proto f) {
        int n = D.strip ? 0 : f.sizelineinfo;
        dumpInt(D, n);
        if (n > 0) {
            byte[] b = new byte[n * 4];
            for (int i = 0; i < n; i++) {
                Undump.putInt32(b, i * 4, f.lineinfo[i]);
            }
            dumpBlock(D, b, 0, b.length);
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
        dumpByte(D, 4); // sizeof(int)
        dumpByte(D, 8); // sizeof(size_t)
        dumpByte(D, SIZE_INSTRUCTION);
        dumpByte(D, SIZE_INTEGER);
        dumpByte(D, SIZE_NUMBER);
        dumpInteger(D, LUAC_INT);
        dumpNumber(D, LUAC_NUM);
    }

    public static int dump(Proto f, Writer w, boolean strip) {
        DumpState D = new DumpState(w, strip);
        dumpHeader(D);
        dumpByte(D, f.sizeupvalues);
        dumpFunction(D, f, null);
        return D.status;
    }

    public static byte[] dumpBytes(Proto f, boolean strip) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dump(f, (b, off, len) -> {
            out.write(b, off, len);
            return 0;
        }, strip);
        return out.toByteArray();
    }
}
