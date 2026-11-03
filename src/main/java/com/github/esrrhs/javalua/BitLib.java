package com.github.esrrhs.javalua;

/**
 * bit32 library — {@code lbitlib.c} under {@code LUA_COMPAT_BITLIB}
 * (enabled by default via {@code LUA_COMPAT_5_2} in Lua 5.3.4).
 */
public final class BitLib {

    private BitLib() {
    }

    /** {@code LUA_NBITS} */
    private static final int NBITS = 32;

    /** first NBITS bits set */
    private static final long ALLONES = 0xFFFFFFFFL;

    private static long trim(long x) {
        return x & ALLONES;
    }

    /** builds a number with {@code n} ones ({@code 1 <= n <= NBITS}) */
    private static long mask(int n) {
        return trim(~((ALLONES << 1) << (n - 1)));
    }

    private static long checkUnsigned(LuaState L, int arg) {
        return trim(LuaAux.checkInteger(L, arg));
    }

    private static void pushUnsigned(LuaState L, long n) {
        L.push(LuaValue.ofInteger(trim(n)));
    }

    private static long andAux(LuaState L) {
        int n = L.getTop();
        long r = ALLONES;
        for (int i = 1; i <= n; i++) {
            r &= checkUnsigned(L, i);
        }
        return trim(r);
    }

    private static int band(LuaState L) {
        pushUnsigned(L, andAux(L));
        return 1;
    }

    private static int btest(LuaState L) {
        L.push(LuaValue.ofBoolean(andAux(L) != 0));
        return 1;
    }

    private static int bor(LuaState L) {
        int n = L.getTop();
        long r = 0;
        for (int i = 1; i <= n; i++) {
            r |= checkUnsigned(L, i);
        }
        pushUnsigned(L, r);
        return 1;
    }

    private static int bxor(LuaState L) {
        int n = L.getTop();
        long r = 0;
        for (int i = 1; i <= n; i++) {
            r ^= checkUnsigned(L, i);
        }
        pushUnsigned(L, r);
        return 1;
    }

    private static int bnot(LuaState L) {
        pushUnsigned(L, ~checkUnsigned(L, 1));
        return 1;
    }

    private static int shift(LuaState L, long r, long i) {
        if (i < 0) {
            i = -i;
            r = trim(r);
            if (i >= NBITS) {
                r = 0;
            } else {
                r >>>= i;
            }
        } else {
            if (i >= NBITS) {
                r = 0;
            } else {
                r <<= i;
            }
            r = trim(r);
        }
        pushUnsigned(L, r);
        return 1;
    }

    private static int lshift(LuaState L) {
        return shift(L, checkUnsigned(L, 1), LuaAux.checkInteger(L, 2));
    }

    private static int rshift(LuaState L) {
        return shift(L, checkUnsigned(L, 1), -LuaAux.checkInteger(L, 2));
    }

    private static int arshift(LuaState L) {
        long r = checkUnsigned(L, 1);
        long i = LuaAux.checkInteger(L, 2);
        if (i < 0 || (r & (1L << (NBITS - 1))) == 0) {
            return shift(L, r, -i);
        }
        // arithmetic shift for 'negative' number
        if (i >= NBITS) {
            r = ALLONES;
        } else {
            r = trim((r >>> i) | ~(trim(~0L) >>> i));
        }
        pushUnsigned(L, r);
        return 1;
    }

    private static int rot(LuaState L, long d) {
        long r = checkUnsigned(L, 1);
        int i = (int) (d & (NBITS - 1));
        r = trim(r);
        if (i != 0) {
            r = (r << i) | (r >>> (NBITS - i));
        }
        pushUnsigned(L, r);
        return 1;
    }

    private static int lrotate(LuaState L) {
        return rot(L, LuaAux.checkInteger(L, 2));
    }

    private static int rrotate(LuaState L) {
        return rot(L, -LuaAux.checkInteger(L, 2));
    }

    private static int fieldArgs(LuaState L, int farg, int[] width) {
        long f = LuaAux.checkInteger(L, farg);
        long w = LuaAux.optInteger(L, farg + 1, 1);
        LuaAux.argCheck(L, f >= 0, farg, "field cannot be negative");
        LuaAux.argCheck(L, w > 0, farg + 1, "width must be positive");
        if (f + w > NBITS) {
            throw L.libError("trying to access non-existent bits");
        }
        width[0] = (int) w;
        return (int) f;
    }

    private static int extract(LuaState L) {
        int[] w = new int[1];
        long r = trim(checkUnsigned(L, 1));
        int f = fieldArgs(L, 2, w);
        r = (r >>> f) & mask(w[0]);
        pushUnsigned(L, r);
        return 1;
    }

    private static int replace(LuaState L) {
        int[] w = new int[1];
        long r = trim(checkUnsigned(L, 1));
        long v = trim(checkUnsigned(L, 2));
        int f = fieldArgs(L, 3, w);
        long m = mask(w[0]);
        r = (r & ~(m << f)) | ((v & m) << f);
        pushUnsigned(L, r);
        return 1;
    }

    public static void open(LuaState L) {
        LuaTable t = new LuaTable();
        LuaAux.setFn(t, "arshift", BitLib::arshift);
        LuaAux.setFn(t, "band", BitLib::band);
        LuaAux.setFn(t, "bnot", BitLib::bnot);
        LuaAux.setFn(t, "bor", BitLib::bor);
        LuaAux.setFn(t, "bxor", BitLib::bxor);
        LuaAux.setFn(t, "btest", BitLib::btest);
        LuaAux.setFn(t, "extract", BitLib::extract);
        LuaAux.setFn(t, "lrotate", BitLib::lrotate);
        LuaAux.setFn(t, "lshift", BitLib::lshift);
        LuaAux.setFn(t, "replace", BitLib::replace);
        LuaAux.setFn(t, "rrotate", BitLib::rrotate);
        LuaAux.setFn(t, "rshift", BitLib::rshift);
        LuaAux.registerLib(L, "bit32", t);
    }
}
