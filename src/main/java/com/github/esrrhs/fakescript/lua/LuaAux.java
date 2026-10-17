package com.github.esrrhs.fakescript.lua;

import java.io.IOException;
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
     * <p>
     * 哪种是 {@code lauxlib.c: interror} 定的，判据是
     * {@code lua_isnumber(L, arg)} 而不是 {@code lua_type(L, arg) == LUA_TNUMBER}：
     *
     * <pre>
     * static void interror (lua_State *L, int arg) {
     *   if (lua_isnumber(L, arg))
     *     luaL_argerror(L, arg, "number has no integer representation");
     *   else
     *     tag_error(L, arg, LUA_TNUMBER);
     * }
     * </pre>
     *
     * 而 {@code lua_isnumber} 是「是数字，或字符串能解释成数字」。所以
     * {@code string.format("%d", "3.5")} 报的是「no integer representation」
     * （{@code "3.5"} 解释得成 3.5，但 3.5 不是整数），
     * {@code string.format("%d", "abc")} 报的才是「number expected, got string」。
     * 早年这里写成 {@code v.isNumber()}，两条都成了后者。
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
        if (Vm.toNumber(v) != null) {
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

    /**
     * {@code luaL_checkoption}：在一串名字里挑，挑不到就报
     * {@code invalid option '<名字>'}。
     * <p>
     * {@code os.setlocale} 的第二个参数用它——就算本实现实际上不认那些 locale，
     * 这条报错也得在，否则 {@code os.setlocale(nil, 'bogus')} 会静默通过。
     */
    public static int checkOption(LuaState L, int arg, String def, String[] lst) {
        // C 用 strcmp 比的是 const char*：\0 之后的内容既不参与比较，也不写进报错
        String name = ((def != null) ? optString(L, arg, LuaString.latin1(def))
                : checkString(L, arg)).toCString().toJavaString();
        for (int i = 0; i < lst.length; i++) {
            if (lst[i].equals(name)) {
                return i;
            }
        }
        throw L.argError(arg, "invalid option '" + name + "'");
    }

    /**
     * {@code luaL_argcheck}：条件不成立就报 {@code argError(arg, msg)}。
     * <p>
     * 与 {@link #argExpected} 是<b>两条不同的报错</b>，不能混：
     * {@code luaL_argexpected} 给 {@code "<tname> expected, got <实际类型>"}，
     * 而 {@code luaL_argcheck} 只给裸消息。实测
     * {@code io.open("x", "bogus")} 报的是
     * {@code bad argument #2 to 'io.open' (invalid mode)}，
     * 后面没有 {@code expected, got}。
     */
    public static void argCheck(LuaState L, boolean cond, int arg, String msg) {
        if (!cond) {
            throw L.argError(arg, msg);
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
    /**
     * {@code lauxlib.c: luaL_checkstack}。
     * <p>
     * 措辞是 {@code "stack overflow (%s)"}——括号里那段才是各个库自己的说明
     * （{@code too many results to unpack}、{@code string slice too long} 等）。
     * 之前这里只调 {@link LuaState#checkStack}，把 {@code msg} 丢了，
     * 于是大参数 {@code table.unpack} 报的是裸的 {@code stack overflow}，
     * 与参考实现逐字不符。
     */
    public static void checkStack(LuaState L, int n, String msg) {
        if (n > 0 && !L.checkStackOk(n)) {
            throw L.libError((msg != null) ? "stack overflow (" + msg + ")" : "stack overflow");
        }
    }

    /** {@code luaL_pushfail}：5.4 里「失败」就是 nil */
    public static void pushFail(LuaState L) {
        L.pushNil();
    }

    /* ==================== 命名元表与 userdata ==================== */

    /**
     * {@code luaL_newmetatable}：在注册表里按名字建（或取）一张元表。
     * <p>
     * 名字就是 C 里那个 {@code LUA_FILEHANDLE}（{@code "FILE*"}）。
     * 建完被 {@code setMetatableByName} 用掉，调用方接着往里塞元方法。
     * <p>
     * <b>必须顺手设 {@code __name}</b>——这是实测出来的：
     * {@code print(#io.stdout)} 报的是
     * {@code attempt to get length of a FILE* value}，那个 {@code FILE*}
     * 就来自元表的 {@code __name} 字段，而 C 版正是在这里写进去的。
     */
    public static LuaTable newMetatable(LuaState L, String tname) {
        LuaValue v = L.registry.get(tname);
        if (v != null && !v.isNil()) {
            throw L.libError("metatable '" + tname + "' already exists");
        }
        LuaTable mt = new LuaTable();
        mt.set("__name", LuaValue.ofString(LuaString.latin1(tname)));
        L.registry.set(tname, LuaValue.ofTable(mt));
        return mt;
    }

    /** 取命名元表；没有返回 {@code null} */
    public static LuaTable getMetatableByName(LuaState L, String tname) {
        LuaValue v = L.registry.get(tname);
        return (v == null || !v.isTable()) ? null : v.asTable();
    }

    /**
     * {@code luaL_getmetatable} 的取值部分：把命名元表压栈（没有就压 nil）。
     */
    public static void pushMetatable(LuaState L, String tname) {
        LuaTable mt = getMetatableByName(L, tname);
        if (mt == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofTable(mt));
        }
    }

    /**
     * {@code luaL_testudata}：是 userdata 且元表正好是 {@code tname} 那张才给载荷，
     * 否则给 {@code null}（<b>不报错</b>——{@code io.type} 靠它区分「不是文件」）。
     */
    public static Object testUdata(LuaState L, int arg, String tname) {
        if (L.isNoneArg(arg)) {
            return null;
        }
        LuaValue v = L.arg1(arg);
        if (v.baseType() != Lua.TUSERDATA) {
            return null;
        }
        LuaTable mt = ((LuaUserdata) v.o).metatable;
        if (mt == null || mt != getMetatableByName(L, tname)) {
            return null;
        }
        return ((LuaUserdata) v.o).data;
    }

    /**
     * {@code luaL_checkudata}：同上但失败要报错。
     * <p>
     * 报错文本是 {@code bad argument #1 to 'xxx' (FILEHANDLE* expected, got table)}。
     */
    public static Object checkUdata(LuaState L, int arg, String tname) {
        Object p = testUdata(L, arg, tname);
        if (p == null) {
            throw L.typeArgError(arg, tname);
        }
        return p;
    }

    /**
     * {@code luaL_setmetatable}：把命名元表挂到栈顶那个 userdata 上。
     * <p>
     * 顺带跑 {@code luaC_checkfinalizer}——C 版 {@code lua_setmetatable} 里有这一步，
     * 漏掉的话文件句柄的 {@code __gc} 永远不会被登记，脚本里忘了 {@code f:close()}
     * 的文件就一直开着。
     */
    public static void setMetatableByName(LuaState L, String tname) {
        LuaTable mt = getMetatableByName(L, tname);
        LuaUserdata ud = (LuaUserdata) L.stack[L.top - 1].o;
        ud.metatable = mt;
        ud.markFinalizable(L);
    }

    /* ==================== 文件结果三元组 ==================== */

    /**
     * {@code luaL_fileresult}：成功只给 {@code true}；失败给 {@code nil, msg, errno}。
     * <p>
     * 失败时<b>总是三个值</b>——{@code io.open} 打不开文件时返回的就是
     * {@code nil, "xxx: No such file or directory", 2}。
     */
    public static void fileResult(LuaState L, boolean ok, String fname, int en) {
        if (ok) {
            L.push(LuaValue.ofBoolean(true));
            return;
        }
        L.pushNil();
        String msg = strError(en);
        if (fname != null) {
            L.push(LuaValue.ofString(LuaString.latin1(fname + ": " + msg)));
        } else {
            L.push(LuaValue.ofString(LuaString.latin1(msg)));
        }
        L.push(LuaValue.ofInteger(en));
    }

    /**
     * {@code luaL_execresult}：{@code os.execute} 与 {@code io.popen} 的收尾。
     * <p>
     * 成功 {@code true, "exit", 0}；非零退出 {@code nil, "exit", 码}。
     * 失败（shell 都起不来）时 {@code stat == -1}，给 {@code nil, msg, errno}。
     */
    public static void execResult(LuaState L, int stat) {
        if (stat == -1) {
            fileResult(L, false, null, errnoOf(null));
        } else {
            /*
             * 失败时压的是 {@code luaL_pushfail}，而 5.4 里它<b>就是 nil</b>
             * （{@code lauxlib.h: #define luaL_pushfail(L) lua_pushnil(L)}），
             * 不是 false。早先这里写的是 {@code ofBoolean(stat == 0)}，于是
             * {@code io.popen("exit 3"):close()} 给的是
             * {@code false, "exit", 3} 而参考实现给 {@code nil, "exit", 3}。
             * <p>
             * 只有 {@code io.popen} 的收尾走这里 —— {@code os.execute} 有自己
             * 一份（那边本来就是对的，所以这个 bug 在 os 那边看不出来）。
             */
            if (stat == 0) {
                L.push(LuaValue.ofBoolean(true));
            } else {
                pushFail(L);
            }
            L.push(LuaValue.ofString(LuaString.latin1("exit")));
            L.push(LuaValue.ofInteger(stat));
        }
    }

    /**
     * 把 Java 的 {@code IOException} 翻译成 errno。
     * <p>
     * Lua 脚本里 {@code io.open} 失败后要看 {@code errno} 的<b>数值</b>
     * （{@code "No such file or directory" == 2}），所以不能只留消息文本。
     */
    /**
     * {@link #errnoOf(IOException)} 的兜底重载：路径里带上 Java 文件系统不接受的东西时
     * （{@code InvalidPathException} 是个 {@code RuntimeException}），C 那边
     * {@code remove} / {@code rename} 拿到的是同一个被 {@code \0} 砍过的路径、
     * 报的是 {@code ENOENT}。
     */
    public static int errnoOf(Exception e) {
        return (e instanceof IOException) ? errnoOf((IOException) e) : 2; // ENOENT
    }

    /**
     * {@code ENOTEMPTY} 的<b>数值</b>是操作系统定的，不是 C 库定的：
     * Linux 是 {@code 39}，macOS / BSD 是 {@code 66}。
     * <p>
     * 实测（参考二进制，本机 macOS）：{@code os.remove(非空目录)} 给
     * {@code nil, "<p>: Directory not empty", 66} —— 之前这里写死 39，
     * 消息对了、数字差 27。
     */
    private static final int ENOTEMPTY;

    static {
        String os = System.getProperty("os.name", "");
        ENOTEMPTY = (os.contains("Mac") || os.contains("Darwin") || os.contains("BSD"))
                ? 66
                : 39;
    }

    public static int errnoOf(IOException e) {
        if (e instanceof java.nio.file.NoSuchFileException) {
            return 2; // ENOENT
        }
        if (e instanceof java.nio.file.DirectoryNotEmptyException) {
            return ENOTEMPTY;
        }
        if (e instanceof java.nio.file.FileAlreadyExistsException) {
            return 17; // EEXIST
        }
        if (e instanceof java.nio.file.AccessDeniedException) {
            return 13; // EACCES
        }
        if (e instanceof java.io.FileNotFoundException) {
            return 2; // ENOENT
        }
        return 5; // EIO
    }

    /** {@code strerror}：只备了文件操作真正会碰到的那几个 errno */
    public static String strError(int en) {
        switch (en) {
        case 2:
            return "No such file or directory";
        case 13:
            return "Permission denied";
        case 17:
            return "File exists";
        /*
         * ENOTEMPTY 的<b>两个</b>数值都要认：Linux 39、macOS/BSD 66。
         * 只写 39 的话 macOS 上 errno 对了、消息却落进兜底的
         * "Input/output error"（实测就是这么红的：数字 66 对、消息错）。
         */
        case 39:
        case 66:
            return "Directory not empty";
        case 9: // EBADF
            return "Bad file descriptor";
        case 22: // EINVAL
            return "Invalid argument";
        case 20: // ENOTDIR
            return "Not a directory";
        case 21: // EISDIR
            return "Is a directory";
        case 29: // ESPIPE
            return "Illegal seek";
        default:
            return "Input/output error";
        }
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
