package com.github.esrrhs.fakescript.lua;

import java.util.Arrays;

/**
 * 辅助库 —— 对应 {@code lauxlib.c} 里「库函数写起来要用的那半」。
 * <p>
 * 之前每个库各写一份 {@code checkInteger} / {@code "bad argument..."}，
 * 拼出来的错误文本彼此不同、也都与参考实现不同。阶段 7 要一口气开六七个库，
 * 再抄七遍就再也收不回来了，所以先抽出这一层。
 * <p>
 * <b>参数编号一律 1 起</b>（与 C 的 {@code luaL_check*} 一致），因为
 * {@link LuaState#argError} 里那句「方法调用时 {@code arg--}」只有按 1 起
 * 才说得通：{@code s:rep(nil)} 报的是 {@code bad argument #1}。
 */
public final class LuaAux {

    private LuaAux() {
    }

    /* ==================== 字节缓冲区 ==================== */

    /**
     * {@code luaL_Buffer}。
     * <p>
     * 装的是<b>字节</b>不是字符。字符串库里每一处都在拼字节串
     * （{@code string.rep} 的结果、{@code gsub} 的累积、{@code format} 的输出），
     * 用 Java {@code String} 或 {@code StringBuilder} 会先把字节按某种编码
     * 解释一遍——而 Lua 字符串可能根本不是合法 UTF-8。
     */
    public static final class Buf {
        private byte[] data = new byte[64];
        private int len;

        private void ensure(int extra) {
            if (len + extra <= data.length) {
                return;
            }
            int cap = data.length;
            while (cap < len + extra) {
                cap *= 2;
            }
            data = Arrays.copyOf(data, cap);
        }

        public void addChar(int c) {
            ensure(1);
            data[len++] = (byte) c;
        }

        public void addBytes(byte[] b, int off, int n) {
            ensure(n);
            System.arraycopy(b, off, data, len, n);
            len += n;
        }

        public void addString(LuaString s) {
            addBytes(s.bytes, 0, s.bytes.length);
        }

        public void addAscii(String s) {
            ensure(s.length());
            for (int i = 0; i < s.length(); i++) {
                data[len++] = (byte) s.charAt(i);
            }
        }

        public LuaString result() {
            return LuaString.of(data, 0, len);
        }
    }

    /** {@code luaL_addvalue}：把栈顶那个值追加进缓冲区并弹栈（值必须是字符串） */
    public static void addTop(LuaState L, Buf b) {
        LuaValue v = L.stack[--L.top];
        b.addString(asString(v));
    }

    /* ==================== 取值与检查 ==================== */

    /** {@code luaL_checklstring}：数字<b>会</b>转成字符串（{@code string.len(42) == 2}） */
    public static LuaString checkString(LuaState L, int arg) {
        LuaValue v = L.arg1(arg);
        if (v.isString()) {
            return v.asString();
        }
        if (v.isNumber()) {
            return LuaString.latin1(Vm.numberToString(v));
        }
        throw L.typeArgError(arg, "string");
    }

    /** {@code luaL_optlstring}：没传或 nil 时用默认值 */
    public static LuaString optString(LuaState L, int arg, LuaString def) {
        if (L.isNoneArg(arg) || L.arg1(arg).isNil()) {
            return def;
        }
        return checkString(L, arg);
    }

    /**
     * {@code luaL_checkinteger}。
     * <p>
     * 字符串<b>会</b>被解释成数字（{@code string.sub("abcdef","2","4")} 是成立的），
     * 但解释不出整数就报「number has no integer representation」——与
     * 「number expected」是两种文本，别合并。
     */
    public static long checkInteger(LuaState L, int arg) {
        LuaValue v = L.arg1(arg);
        if (L.isNoneArg(arg)) {
            throw L.typeArgError(arg, "number");
        }
        Long r = Vm.toInteger(v, Arith.F2I_EQ);
        if (r != null) {
            return r;
        }
        if (v.isNumber()) {
            throw L.argError(arg, "number has no integer representation");
        }
        throw L.typeArgError(arg, "number");
    }

    /** {@code luaL_optinteger} */
    public static long optInteger(LuaState L, int arg, long def) {
        if (L.isNoneArg(arg) || L.arg1(arg).isNil()) {
            return def;
        }
        return checkInteger(L, arg);
    }

    /** {@code luaL_checknumber}：一律返回 double，整数也转（与 C 版一致） */
    public static double checkNumber(LuaState L, int arg) {
        LuaValue v = L.arg1(arg);
        if (L.isNoneArg(arg)) {
            throw L.typeArgError(arg, "number");
        }
        Double d = Vm.toNumber(v);
        if (d != null) {
            return d;
        }
        throw L.typeArgError(arg, "number");
    }

    /** {@code luaL_optnumber} */
    public static double optNumber(LuaState L, int arg, double def) {
        if (L.isNoneArg(arg) || L.arg1(arg).isNil()) {
            return def;
        }
        return checkNumber(L, arg);
    }

    /**
     * {@code luaL_checkany}：只要求「这个参数传了」，不要求类型。
     * <p>
     * {@code math.type} / {@code math.tointeger} 用它——两者对任何非数字都给
     * nil，唯独「一个参数也没传」要报错，报出来是 {@code (value expected)}。
     */
    public static void checkAny(LuaState L, int arg) {
        if (L.isNoneArg(arg)) {
            throw L.argError(arg, "value expected");
        }
    }

    /** {@code luaL_checktype} */
    public static void checkType(LuaState L, int arg, int baseType) {
        if (L.isNoneArg(arg) || L.arg1(arg).baseType() != baseType) {
            throw L.typeArgError(arg, Lua.typeName(baseType));
        }
    }

    /**
     * {@code luaL_argexpected}：条件不成立就报「{@code tname} expected」。
     * <p>
     * 专供那种「接受若干种类型」的场合，比如 {@code gsub} 的第三个参数
     * 可以是 string/function/table，报出来是
     * 「string/function/table expected, got boolean」。
     */
    public static void argExpected(LuaState L, boolean cond, int arg, String tname) {
        if (!cond) {
            throw L.typeArgError(arg, tname);
        }
    }

    /** {@code luaL_checkstack} */
    public static void checkStack(LuaState L, int n, String msg) {
        if (n > 0) {
            L.checkStack(n);
        }
    }

    /** {@code luaL_pushfail}：5.4 里「失败」就是 nil */
    public static void pushFail(LuaState L) {
        L.pushNil();
    }

    /* ==================== 小工具 ==================== */

    /** 把一个值当成字符串读出来（数字已由调用点转换过） */
    public static LuaString asString(LuaValue v) {
        return v.isString() ? v.asString() : LuaString.latin1(Vm.numberToString(v));
    }

    /** {@code luaL_tolstring}：走 {@code __tostring}，再按类型格式化 */
    public static LuaString toLString(LuaState L, LuaValue v) {
        return BaseLib.toLString(L, v);
    }

    /** 往表里装一个函数 */
    public static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(name, LuaValue.ofFunction(f));
    }

    /**
     * {@code luaL_requiref} 的登记部分：库表同时进 {@code _G} 与 {@code package.loaded}。
     * <p>
     * 进 {@code _LOADED} 不只是为了 {@code require}：{@code luaL_argerror}
     * 反查函数名时就在那张表里找，漏登记会让
     * {@code pcall(string.len)} 报出「to '?'」。
     */
    public static void registerLib(LuaState L, String name, LuaTable lib) {
        L.globals.set(name, LuaValue.ofTable(lib));
        L.loadedTable().set(name, LuaValue.ofTable(lib));
    }
}
