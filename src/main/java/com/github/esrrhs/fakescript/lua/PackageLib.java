package com.github.esrrhs.fakescript.lua;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * package 库与 {@code require} —— 对应 {@code loadlib.c}。
 * <p>
 * 与 {@code io} 那类库不同，这里<b>没有 libc 可借</b>：{@code dlopen} /
 * {@code dlsym} 在 JVM 上没有对应物（真要接得走 JNI 或 FFI，那是另一个工程）。
 * 所以 C 搜索器走的是 {@code loadlib.c} 里那段「未启用动态库」的兜底分支——
 * 不是偷懒，那就是 C 版在这种平台上的真实行为。
 * <p>
 * 与参考实现<b>不一致</b>的地方（参考二进制是用 {@code dlopen} 编出来的）：
 * <ul>
 *   <li>{@code package.loadlib} 的第三个数是 {@code "absent"} 而不是
 *       {@code "open"}，消息是
 *       {@code "dynamic libraries not enabled; check your Lua installation"}
 *       而不是 {@code dlerror()} 的文本；</li>
 *   <li>{@code require} 一个真能找到 {@code .so} 的模块时，本实现会停在
 *       「动态库未启用」这一步，参考实现会真的去 {@code dlopen}。</li>
 * </ul>
 * 两者都只影响 C 模块；纯 Lua 的 {@code require} 逐字节一致。
 */
public final class PackageLib {

    private PackageLib() {
    }

    /* ==================== 常量 ==================== */

    /** {@code LUA_LOADED_TABLE} */
    private static final String LOADED = "_LOADED";

    /** {@code LUA_PRELOAD_TABLE} */
    private static final String PRELOAD = "_PRELOAD";

    /** {@code LUA_DIRSEP} */
    private static final String DIRSEP = "/";

    /** {@code LUA_PATH_SEP} */
    private static final String PATH_SEP = ";";

    /** {@code LUA_PATH_MARK} */
    private static final String PATH_MARK = "?";

    /** {@code LUA_EXEC_DIR} */
    private static final String EXEC_DIR = "!";

    /** {@code LUA_IGMARK} */
    private static final String IGMARK = "-";

    /** {@code LUA_VERSUFFIX} */
    private static final String VER_SUFFIX = "_5_4";

    private static final String LUA_PATH_VAR = "LUA_PATH";
    private static final String LUA_CPATH_VAR = "LUA_CPATH";

    /** {@code LUA_PATH_DEFAULT}（{@code LUA_ROOT} 为 {@code /usr/local/} 时展开的结果） */
    private static final String PATH_DEFAULT = "/usr/local/share/lua/5.4/?.lua;"
            + "/usr/local/share/lua/5.4/?/init.lua;"
            + "/usr/local/lib/lua/5.4/?.lua;"
            + "/usr/local/lib/lua/5.4/?/init.lua;"
            + "./?.lua;./?/init.lua";

    /** {@code LUA_CPATH_DEFAULT} */
    private static final String CPATH_DEFAULT = "/usr/local/lib/lua/5.4/?.so;"
            + "/usr/local/lib/lua/5.4/loadall.so;./?.so";

    /** 未启用动态库时的消息（{@code loadlib.c} 兜底分支的 {@code DLMSG}） */
    private static final String DLMSG =
            "dynamic libraries not enabled; check your Lua installation";

    /**
     * {@code LIB_FAIL}：{@code lookforfunc} 返回 {@code ERRLIB}（库打不开）时的
     * 第三个返回值。启用 dlopen 的构建里是 {@code "open"}；本实现没有动态库，
     * 失败一律发生在「打开」这一步，所以也必须是 {@code "open"}。
     */
    private static final String LIB_FAIL = "open";

    private static final int ERRLIB = 1;
    private static final int ERRFUNC = 2;

    /* ==================== 注册 ==================== */

    public static void open(LuaState L) {
        LuaTable pkg = new LuaTable();
        LuaAux.setFn(pkg, "loadlib", PackageLib::loadlib);
        LuaAux.setFn(pkg, "searchpath", PackageLib::searchpath);

        /*
         * searchers 的四个搜索器都把 package 绑成 upvalue 1。C 是
         * lua_pushvalue(-2) + lua_pushcclosure；这里直接填 CClosure，
         * 排布一样。
         */
        LuaTable searchers = new LuaTable();
        LuaFunction[] sf = {
                PackageLib::searcherPreload,
                PackageLib::searcherLua,
                PackageLib::searcherC,
                PackageLib::searcherCroot,
        };
        for (int i = 0; i < sf.length; i++) {
            CClosure cl = new CClosure(sf[i], 1);
            cl.upvalue[0].set(LuaValue.ofTable(pkg));
            searchers.set(i + 1, LuaValue.ofCClosure(cl));
        }
        pkg.set("searchers", LuaValue.ofTable(searchers));

        pkg.set("path", LuaValue.ofString(LuaString.latin1(
                setPath(L, LUA_PATH_VAR, PATH_DEFAULT))));
        pkg.set("cpath", LuaValue.ofString(LuaString.latin1(
                setPath(L, LUA_CPATH_VAR, CPATH_DEFAULT))));

        pkg.set("config", LuaValue.ofString(LuaString.latin1(DIRSEP + "\n" + PATH_SEP + "\n"
                + PATH_MARK + "\n" + EXEC_DIR + "\n" + IGMARK + "\n")));

        pkg.set("loaded", LuaValue.ofTable(L.loadedTable()));
        pkg.set("preload", LuaValue.ofTable(preloadTable(L)));
        LuaAux.registerLib(L, "package", pkg);

        // require 进 _G，带 package 作 upvalue（不是进 package 表）
        CClosure req = new CClosure(PackageLib::requireFn, 1);
        req.upvalue[0].set(LuaValue.ofTable(pkg));
        L.globals.set("require", LuaValue.ofCClosure(req));
    }

    private static LuaTable preloadTable(LuaState L) {
        LuaValue v = L.registry.get(PRELOAD);
        if (v != null && v.isTable()) {
            return v.asTable();
        }
        LuaTable t = new LuaTable();
        L.registry.set(PRELOAD, LuaValue.ofTable(t));
        return t;
    }

    /** {@code noenv}：注册表里的 {@code LUA_NOENV} 为真就不读环境变量 */
    private static boolean noEnv(LuaState L) {
        LuaValue v = L.registry.get("LUA_NOENV");
        return v != null && v.toBoolean();
    }

    /**
     * {@code setpath}：先看 {@code LUA_PATH_5_4}，再看 {@code LUA_PATH}，都没有
     * 才用默认值。
     * <p>
     * 变量值里出现 {@code ";;"} 时把默认路径插在那个位置——这是 Lua 让用户
     * 「在默认路径前面/后面追加」的机制。
     */
    private static String setPath(LuaState L, String envname, String dft) {
        String path = System.getenv(envname + VER_SUFFIX);
        if (path == null) {
            path = System.getenv(envname);
        }
        if (path == null || noEnv(L)) {
            return dft;
        }
        int mark = path.indexOf(";;");
        if (mark < 0) {
            return path;
        }
        StringBuilder b = new StringBuilder();
        if (mark > 0) { // ';;' 之前还有一段
            b.append(path, 0, mark).append(PATH_SEP);
        }
        b.append(dft);
        if (mark < path.length() - 2) { // ';;' 之后还有一段
            b.append(PATH_SEP).append(path, mark + 2, path.length());
        }
        return b.toString();
    }

    /* ==================== 路径搜索 ==================== */

    /**
     * {@code getnextfilename} 的等价物：按 {@code ';'} 切出下一个模板。
     * <p>
     * 没有直接用 {@code String.split}：两者的边界行为不同。实测
     * {@code "a;"} 在 C 里只出一项 {@code "a"}（末尾分号不产生空项），而
     * {@code String.split(";", -1)} 会多给一个空串；空串模板在拼错误消息时
     * 会多出一行 {@code no file ''}。
     */
    private static List<String> splitPath(String path) {
        List<String> out = new ArrayList<>();
        char[] s = path.toCharArray();
        int end = s.length;
        int p = 0;
        while (true) {
            if (p >= end) {
                break; // name == end：没有更多模板了
            }
            if (s[p] == '\0') { // 上一轮把分隔符改成了 '\0'：还原并跳过
                s[p] = PATH_SEP.charAt(0);
                p++;
                if (p >= end) {
                    break;
                }
            }
            int sep = p;
            while (sep < end && s[sep] != PATH_SEP.charAt(0)) {
                sep++;
            }
            /*
             * 这步不能省。C 是把分隔符位置写成 '\0' 来切出当前模板的，
             * 下一轮正是靠「这个位置是 \0」认出「刚切过」并跳过它。不写就会
             * 在遇到连续分隔符（{@code ";;"}）时停在原地死循环。
             */
            if (sep < end) {
                s[sep] = '\0';
            }
            out.add(new String(s, p, sep - p));
            p = sep;
        }
        return out;
    }

    /** {@code readable}：能打开读就算数 */
    private static boolean readable(String filename) {
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(filename, "r")) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code pusherrornotfound}：{@code no file 'a'\n\tno file 'b'} */
    private static String errorNotFound(String path) {
        StringBuilder b = new StringBuilder("no file '");
        b.append(path.replace(PATH_SEP, "'\n\tno file '"));
        b.append("'");
        return b.toString();
    }

    /**
     * {@code searchpath}。
     *
     * @return 找到的文件名；找不到返回 {@code null} 且<b>错误消息已压栈</b>
     */
    private static String searchPath(LuaState L, String name, String path, String sep,
                                     String dirsep) {
        // C 只判 sep 的第一个字符在不在 name 里，替换时却替换整个 sep
        if (!sep.isEmpty() && name.indexOf(sep.charAt(0)) >= 0) {
            name = name.replace(sep, dirsep);
        }
        String pathname = path.replace(PATH_MARK, name);
        for (String f : splitPath(pathname)) {
            if (readable(f)) {
                return f;
            }
        }
        L.push(LuaValue.ofString(LuaString.latin1(errorNotFound(pathname))));
        return null;
    }

    /** {@code ll_searchpath} */
    private static int searchpath(LuaState L) {
        // 四个参数全都是 const char*（ll_searchpath 的签名 + luaL_gsub 的替换串），
        // 在第一个 \0 处砍断：searchpath("a\0b", "./?.lua") 找的是 "./a.lua"。
        String name = LuaAux.checkString(L, 1).toCString().toJavaString();
        String path = LuaAux.checkString(L, 2).toCString().toJavaString();
        String sep = LuaAux.optString(L, 3, LuaString.latin1(".")).toCString().toJavaString();
        String dirsep = LuaAux.optString(L, 4, LuaString.latin1(DIRSEP)).toCString().toJavaString();
        String f = searchPath(L, name, path, sep, dirsep);
        if (f != null) {
            L.push(LuaValue.ofString(LuaString.latin1(f)));
            return 1;
        }
        // 错误消息已在栈顶：把它挪到 nil 之后，返回 nil + 消息
        LuaValue msg = new LuaValue(L.stack[--L.top]);
        LuaAux.pushFail(L);
        L.push(msg);
        return 2;
    }

    /** {@code findfile}：从 {@code package.<pname>} 里找模块文件 */
    private static String findFile(LuaState L, String name, String pname, String dirsep) {
        LuaTable pkg = (LuaTable) L.upvalue(0).o;
        LuaValue p = pkg.get(pname);
        // C 用的是 lua_tostring：<b>数字也算</b>，会被就地转成字符串
        if (p == null || !(p.isString() || p.isNumber())) {
            throw L.libError("'package." + pname + "' must be a string");
        }
        return searchPath(L, name, LuaAux.asString(p).toJavaString(), ".", dirsep);
    }

    /* ==================== 动态库（兜底：一律失败） ==================== */

    /** {@code lookforfunc}：本实现没有动态库，永远是 {@code ERRLIB} + {@code DLMSG} */
    private static int lookForFunc(LuaState L, String path, String sym) {
        L.push(LuaValue.ofString(LuaString.latin1(DLMSG)));
        return ERRLIB;
    }

    /** {@code loadfunc}：{@code a.b-c} 先试 {@code luaopen_a_b}，失败再试 {@code luaopen_c} */
    private static int loadFunc(LuaState L, String filename, String modname) {
        String m = modname.replace('.', '_');
        int mark = m.indexOf(IGMARK);
        if (mark >= 0) {
            int stat = lookForFunc(L, filename, "luaopen_" + m.substring(0, mark));
            if (stat != ERRFUNC) {
                return stat;
            }
            m = m.substring(mark + 1);
        }
        return lookForFunc(L, filename, "luaopen_" + m);
    }

    /** {@code ll_loadlib} */
    private static int loadlib(LuaState L) {
        String path = LuaAux.checkString(L, 1).toJavaString();
        String init = LuaAux.checkString(L, 2).toJavaString();
        int stat = lookForFunc(L, path, init);
        if (stat == 0) {
            return 1;
        }
        LuaValue msg = new LuaValue(L.stack[--L.top]);
        LuaAux.pushFail(L);
        L.push(msg);
        /*
         * 第三个返回值的判据是 <b>失败发生在哪一步</b>：{@code ERRLIB} 是
         * 「库打不开」（{@code lsys_load} 失败），给 {@code "open"}；
         * {@code ERRFUNC} 是「库里没这个符号」，给 {@code "absent"}。
         * 之前写死 {@code "absent"}，是因为兜底分支（没有 dlopen 的构建）
         * 里 {@code LIB_FAIL} 恰好是 {@code "absent"}——但那个分支下
         * {@code lookforfunc} 恒返回 {@code ERRLIB}，于是照样该给
         * {@code "open"}。靠第三个值区分「缺库 / 缺符号」的代码（不少
         * 模块用它决定要不要继续找）会因此走错分支。
         */
        L.push(LuaValue.ofString(LuaString.latin1(stat == ERRLIB ? LIB_FAIL : "absent")));
        return 3;
    }

    /* ==================== 搜索器 ==================== */

    /** {@code checkload}：成功返回 {@code (loader, filename)}，失败报错 */
    private static int checkLoad(LuaState L, boolean stat, String filename) {
        if (stat) {
            L.push(LuaValue.ofString(LuaString.latin1(filename)));
            return 2;
        }
        LuaValue msg = L.stack[L.top - 1];
        throw L.libError("error loading module '" + L.arg1(1).asString().toJavaString()
                + "' from file '" + filename + "':\n\t"
                + LuaAux.asString(msg).toJavaString());
    }

    /** {@code searcher_preload} */
    private static int searcherPreload(LuaState L) {
        // 模块名在 C 里是 const char*，第一个 \0 之后的内容连错误消息里都不出现
        String name = LuaAux.checkString(L, 1).toCString().toJavaString();
        LuaValue v = preloadTable(L).get(name);
        if (v == null || v.isNil()) {
            L.push(LuaValue.ofString(LuaString.latin1(
                    "no field package.preload['" + name + "']")));
            return 1;
        }
        L.push(v);
        L.push(LuaValue.ofString(LuaString.latin1(":preload:")));
        return 2;
    }

    /** {@code searcher_Lua} */
    private static int searcherLua(LuaState L) {
        String name = LuaAux.checkString(L, 1).toCString().toJavaString();
        String filename = findFile(L, name, "path", DIRSEP);
        if (filename == null) {
            return 1; // 消息已压栈
        }
        try {
            byte[] src = Files.readAllBytes(Paths.get(filename));
            /*
             * luaL_loadfilex 的 chunk 名是 "@" + 文件名。不带这个前缀，
             * 模块里的运行期错误报出来的位置就对不上。
             */
            LClosure cl = L.loadFile(src, "@" + filename);
            L.push(LuaValue.ofClosure(cl));
        } catch (Exception e) {
            String m = (e instanceof LuaError)
                    ? LuaAux.asString(((LuaError) e).value).toJavaString()
                    : String.valueOf(e.getMessage());
            L.push(LuaValue.ofString(LuaString.latin1(m)));
            return checkLoad(L, false, filename);
        }
        return checkLoad(L, true, filename);
    }

    /** {@code searcher_C} */
    private static int searcherC(LuaState L) {
        String name = LuaAux.checkString(L, 1).toJavaString();
        String filename = findFile(L, name, "cpath", DIRSEP);
        if (filename == null) {
            return 1;
        }
        return checkLoad(L, loadFunc(L, filename, name) == 0, filename);
    }

    /** {@code searcher_Croot} */
    private static int searcherCroot(LuaState L) {
        String name = LuaAux.checkString(L, 1).toJavaString();
        int p = name.indexOf('.');
        if (p < 0) {
            return 0; // 就是根，交给别的搜索器
        }
        String filename = findFile(L, name.substring(0, p), "cpath", DIRSEP);
        if (filename == null) {
            return 1;
        }
        int stat = loadFunc(L, filename, name);
        if (stat != 0) {
            if (stat != ERRFUNC) {
                return checkLoad(L, false, filename);
            }
            L.push(LuaValue.ofString(LuaString.latin1(
                    "no module '" + name + "' in file '" + filename + "'")));
            return 1;
        }
        L.push(LuaValue.ofString(LuaString.latin1(filename)));
        return 2;
    }

    /* ==================== require ==================== */

    /** 调一个 Lua 可调用值，取 {@code nres} 个结果（不足补 nil） */
    private static LuaValue[] callGet(LuaState L, LuaValue f, LuaValue a1, int nres) {
        int idx = L.top;
        L.ensureSlots(idx + 2 + nres);
        L.stack[idx].set(f);
        L.stack[idx + 1].set(a1);
        L.top = idx + 2;
        L.call(idx, nres);
        LuaValue[] r = new LuaValue[nres];
        for (int i = 0; i < nres; i++) {
            r[i] = new LuaValue(L.stack[idx + i]);
        }
        L.top = idx;
        return r;
    }

    /**
     * {@code findloader}：挨个试搜索器，攒错误消息。
     *
     * @return {@code [loader, loaderData]}
     */
    private static LuaValue[] findLoader(LuaState L, String name) {
        LuaTable pkg = (LuaTable) L.upvalue(0).o;
        LuaValue sv = pkg.get("searchers");
        if (sv == null || !sv.isTable()) {
            throw L.libError("'package.searchers' must be a table");
        }
        LuaTable searchers = sv.asTable();
        StringBuilder msg = new StringBuilder();
        for (int i = 1;; i++) {
            msg.append("\n\t");
            LuaValue s = searchers.get(LuaValue.ofInteger(i));
            if (s == null || s.isNil()) {
                msg.setLength(msg.length() - 2); // 去掉刚加的前缀
                throw L.libError("module '" + name + "' not found:" + msg);
            }
            LuaValue[] r = callGet(L, s, LuaValue.ofString(LuaString.latin1(name)), 2);
            if (r[0].baseType() == Lua.TFUNCTION) {
                return r;
            }
            /*
             * 判据是 lua_isstring，<b>数字也算</b>——搜索器返回 42 时消息里
             * 会出现 "42"。写成 isString 会把它当成「没给消息」整条丢掉。
             */
            if (r[0].isString() || r[0].isNumber()) {
                msg.append(LuaAux.asString(r[0]).toJavaString());
            } else {
                msg.setLength(msg.length() - 2); // 这个搜索器没给消息
            }
        }
    }

    /** {@code ll_require} */
    private static int requireFn(LuaState L) {
        // 模块名是 const char*，第一个 \0 之后的内容连 package.loaded 的键都进不去
        String name = LuaAux.checkString(L, 1).toCString().toJavaString();
        L.setTop(1);
        LuaTable loaded = L.loadedTable();
        LuaValue cached = loaded.get(name);
        if (cached != null && cached.toBoolean()) {
            L.push(cached);
            return 1; // 已经加载过
        }
        LuaValue[] found = findLoader(L, name);
        LuaValue loader = found[0];
        LuaValue loaderData = found[1];

        int idx = L.top;
        L.ensureSlots(idx + 3);
        L.stack[idx].set(loader);
        L.stack[idx + 1].set(LuaValue.ofString(LuaString.latin1(name)));
        L.stack[idx + 2].set(loaderData);
        L.top = idx + 3;
        L.call(idx, 1);
        LuaValue res = new LuaValue(L.stack[idx]);
        L.top = idx;

        if (!res.isNil()) {
            loaded.set(name, res);
        }
        LuaValue mod = loaded.get(name);
        if (mod == null || mod.isNil()) {
            // 模块没设值：用 true 顶上，require 返回 true
            loaded.set(name, LuaValue.ofBoolean(true));
            mod = LuaValue.ofBoolean(true);
        }
        L.push(mod);
        L.push(loaderData);
        return 2;
    }
}
