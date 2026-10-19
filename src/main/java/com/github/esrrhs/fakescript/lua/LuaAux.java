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
    public static void fileResult(LuaState L, boolean ok, LuaString fname, int en) {
        if (ok) {
            L.push(LuaValue.ofBoolean(true));
            return;
        }
        L.pushNil();
        /*
         * {@code lauxlib.c} 是 {@code (en != 0) ? strerror(en) : "(no extra info)"}。
         * errno 为 0 时<b>没有</b> strerror 文本，参考实测
         * {@code luaL_fileresult(L, 0, NULL)}（errno 事先清成 0）给的就是
         * {@code nil, "(no extra info)", 0}。早先这里一律走 {@code strError}，
         * 于是 {@code strError(0)} 兜底成了 {@code "Input/output error"}。
         */
        String msg = (en == 0) ? "(no extra info)" : strError(en);
        if (fname != null) {
            // 文件名可以是任意字节：消息里那一段必须原样是字节，
            // 不能绕 Java String（UTF-8 解码会把 0xFF 变成 U+FFFD）
            L.push(LuaValue.ofString(fname.concat(LuaString.latin1(": " + msg))));
        } else {
            L.push(LuaValue.ofString(LuaString.latin1(msg)));
        }
        L.push(LuaValue.ofInteger(en));
    }

    /**
     * {@code io.popen():close()} 的收尾（与 {@code luaL_execresult} 同形，但
     * 那条已经在 {@code luashim.c} 里自己走完了）。
     * <p>
     * 成功 {@code true, "exit", 0}；非零退出 {@code nil, "exit", 码}。
     * {@code en} 是 C 侧的 {@code errno}；{@code io.popen} 这条没有 C 的
     * errno，传 0。早先这里写的是 {@code errnoOf(null)}，直接 NPE。
     * <p>
     * 判据照 {@code lauxlib.c} 抄：<b>{@code stat != 0 && errno != 0}</b>
     * 走 {@code luaL_fileresult(0)}，不是「{@code stat == -1}」。实测
     * （参考二进制，macOS）{@code execresult(256, 13)} 给
     * {@code nil, "Permission denied", 13}，而 {@code execresult(-1, 0)}
     * 给 {@code nil, "exit", -1}——按退出码判断会两条全错。
     */
    public static void execResult(LuaState L, int stat, int en) {
        if (stat != 0 && en != 0) {
            fileResult(L, false, null, en);
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

    /**
     * {@code strError} 的反查表。
     * <p>
     * 放在这里而不是 {@link #errnoOf(IOException)} 里逐个判：errno 与 strerror
     * 是<b>一对逆函数</b>，反查直接遍历这张表即可，不用再维护第二份 errno 清单。
     */
    private static final int[] ERRNO_BY_NAME =
            { 1, 2, 13, 16, 17, 20, 21, 22, 29, ENOTEMPTY, 9 };

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
        /*
         * 按类型判不出来时，<b>看异常消息里那段 strerror 文本</b>。
         * <p>
         * Java 的 IO 异常把 libc 的 {@code strerror} 原文带在消息里：
         * {@code FileNotFoundException} 是 {@code "<path> (Permission denied)"}，
         * {@code FileSystemException} 是 {@code "<a> -> <b>: Not a directory"}
         * 或 {@code "<path>: Invalid argument"}（{@code getReason()} 直接给）。
         * 于是 errno 可以<b>反查</b>出来，不用猜。
         * <p>
         * 不做这一步，这些形状会全部落进 {@code ENOENT} / {@code EIO} 的兜底：
         * 实测（参考二进制）{@code io.open("文件/y", "r")} 是
         * {@code Not a directory, 20}、{@code io.open("/etc/hosts", "w")} 是
         * {@code Permission denied, 13}、{@code os.remove(".")} 是
         * {@code Invalid argument, 22}、{@code os.remove("/")} 是
         * {@code Is a directory, 21}、{@code os.remove("/etc")} 是
         * {@code Operation not permitted, 1} —— 五个全错。
         */
        int n = errnoFromMessage(e);
        if (n >= 0) {
            return n;
        }
        if (e instanceof java.io.FileNotFoundException) {
            return 2; // ENOENT
        }
        return 5; // EIO
    }

    /** 从异常消息里那段 {@code strerror} 文本反查 errno；认不出来给 {@code -1} */
    private static int errnoFromMessage(IOException e) {
        String s = (e instanceof java.nio.file.FileSystemException)
                ? ((java.nio.file.FileSystemException) e).getReason()
                : inParens(e.getMessage());
        if (s == null || s.isEmpty()) {
            return -1;
        }
        for (int en : ERRNO_BY_NAME) {
            if (strError(en).equals(s)) {
                return en;
            }
        }
        return -1;
    }

    /** 取 {@code "<path> (文本)"} 里括号中那一段 */
    private static String inParens(String m) {
        if (m == null) {
            return null;
        }
        int a = m.lastIndexOf('(');
        int b = (a < 0) ? -1 : m.lastIndexOf(')');
        return (b > a) ? m.substring(a + 1, b) : null;
    }

    /** {@code strerror}：只备了文件操作真正会碰到的那几个 errno */
    public static String strError(int en) {
        switch (en) {
        case 1: // EPERM
            return "Operation not permitted";
        case 2:
            return "No such file or directory";
        case 13:
            return "Permission denied";
        /*
         * EBUSY：{@code os.remove("/")} 在 macOS 上是 {@code EISDIR}(21)，
         * 在 Linux 上是 {@code EBUSY}(16) —— 两个平台都不一样，但两边都是
         * 从各自的 libc 推出来的，所以各自对得上。少了这一条，Linux 上就会
         * 退化成 EIO。
         */
        case 16:
            return "Device or resource busy";
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
