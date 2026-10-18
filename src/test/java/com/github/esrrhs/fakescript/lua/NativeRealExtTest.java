package com.github.esrrhs.fakescript.lua;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * <b>真·第三方 C 扩展</b>的端到端闸门：把真实扩展编成 .so，用 {@code require}
 * 装进来，两边跑同一份脚本逐字节比。
 * <p>
 * 为什么另开一个类：{@link NativeApiTest} / {@link NativeExtTest} 的探针是<b>我们
 * 自己写的</b>，只调我们想要它调的那些 {@code lua_*}。真实扩展不顺着我们的清单
 * 走——lpeg 第一跑就撞在 {@code lua_getallocf} 上：它拿 {@code lua_getallocf}
 * 的返回值是<b>为了直接当函数指针调用</b>（{@code lpcode.c} 的 {@code newcode} /
 * {@code freecode} / {@code realloccode} 三处），而这个符号在 {@code Api} 里一直
 * 是 {@code unimplemented}。自己写的探针一条都发现不了，因为压根没人想过要去调它。
 * <p>
 * 第二个扩展（cjson）咬出来的是另一类：{@code cjson.null} 是
 * {@code lua_pushlightuserdata(L, NULL)} 推出来的轻量 userdata，
 * {@code print} 它时参考给 {@code userdata: 0x0}——地址取自载荷里的指针值，
 * 而 {@code BaseLib#tostring} 用的是 Java 对象的 identity hash。
 * <p>
 * 源码不入库（第三方代码、体积不小），用环境变量指到解压后的目录；没设就整类跳过。
 * 差别只在「有没有装」，不在「对不对」——所以跳过是安全的。
 */
final class NativeRealExtTest {

    /**
     * 一个真实扩展：环境变量、模块名、源码文件、脚本网格。
     * <p>
     * {@code defs} 是额外的 {@code -D}：luabitop 的 {@code bit.c} 里有一句
     * <pre>
     * #ifdef LUA_NUMBER_DOUBLE ... #else #error "Unknown number type"
     * </pre>
     * 而 5.4 的 {@code luaconf.h} 早就不定义这个 5.1/5.2 时代的宏了，
     * 所以必须我们自己加——不加连编都编不过，更别说跑。
     * <p>
     * {@code out} 是产物文件名，默认 {@code mod + ".so"}。含点的模块名要改它：
     * {@code require("mime.core")} 会把点换成 {@code _} 去找
     * {@code luaopen_mime_core}，所以产物得叫 {@code mime_core.so}。
     * （cpath 里那条是没有 {@code ?} 的绝对路径，Lua 会<b>原样</b>用它，
     * 因此文件名本身叫什么都能装上。）
     */
    private static final class Ext {
        final String env;
        final String mod;
        final String[] src;
        final String[][] cases;
        final String[] defs;
        final String out;
        /** 编好的 .so 绝对路径；编一次就复用 */
        String path;

        Ext(String env, String mod, String[] src, String[][] cases) {
            this(env, mod, src, cases, new String[0], mod + ".so");
        }

        Ext(String env, String mod, String[] src, String[][] cases,
                String[] defs, String out) {
            this.env = env;
            this.mod = mod;
            this.src = src;
            this.cases = cases;
            this.defs = defs;
            this.out = out;
        }
    }

    private static final Ext[] EXTS = {
        new Ext("LPEG_SRC", "lpeg",
            new String[]{"lpcap.c", "lpcode.c", "lpcset.c", "lpprint.c",
                    "lptree.c", "lpvm.c"},
            new String[][]{
                {"装进来",
                    "print(type(n), type(n.P), type(n.match))",
                    "print(n.match(n.P('a'), 'a'))"},
                {"匹配",
                    "print(n.match(n.P('a'), 'a'))",
                    "print(n.match(n.P('a'), 'b'))",
                    "print(n.match(n.P('abc'), 'abc'))",
                    "print(n.match(n.R('az')^1, 'hello'))",
                    "print(n.match(n.S('xy')^0, 'xxyyx'))",
                    "print(n.match(n.P(1)^0, '\\xe4\\xb8\\xad\\xe6\\x96\\x87'))"},
                {"捕获",
                    "print(n.match(n.C(n.R('az')^1), 'hello there'))",
                    "local t = n.match(n.Ct(n.C(n.R('az')^1) * n.P(' ') * n.C(n.R('09')^1)), 'abc 123')",
                    "print(type(t), t[1], t[2])",
                    "print(n.match(n.Cc('x') * n.P('y'), 'y'))",
                    "print(n.match(n.Ct((n.C(n.P(1)) * n.P(',')^-1)^0), 'a,b,c')[3])",
                    "print(n.match(n.C(n.P('a')^-1) * n.C(n.P(1)^0), 'bb'))"},
                {"折叠 / 替换 / 语法捕获",
                    "local add = function(a, b) return a + b end",
                    "print(n.match(n.Cf(n.Cc(0) * (n.C(n.R('09')) / tonumber * n.P(',')^-1)^0, add), '1,2,3,4'))",
                    "print(n.match(n.Cs((n.P('a') / 'X' + n.P(1))^0), 'banana'))",
                    "print(n.match(n.P{'S', S = n.V('V') * n.P('!'), V = n.C(n.R('az')^1)}, 'hi!'))"},
                {"错误路径",
                    "print(pcall(n.P, {}))",
                    "print(pcall(n.match, n.P('a')))",
                    "print(pcall(n.P, 1.5))",
                    "print(pcall(function() return n.P('a') + 1 end))",
                    "print(pcall(function() return n.P('a'):match() end))"},
                {"扩展自己的 userdata 与元表",
                    "local p = n.P('a')",
                    "print(type(p), getmetatable(p) ~= nil)",
                    "print(pcall(function() return p + 1 end))",
                    "print(pcall(function() return #p end))",
                    "print(tostring(p):sub(1, 7))"},
            }),
        new Ext("CJSON_SRC", "cjson",
            new String[]{"lua_cjson.c", "strbuf.c", "dtoa.c", "g_fmt.c"},
            new String[][]{
                {"装进来",
                    "print(type(n), type(n.encode), type(n.decode))"},
                /*
                 * 刻意不 encode 字符串键的表：{@code pairs} 顺序是<b>有意偏离</b>
                 * （README 约定），打出来两边必然不同，那种红毫无意义。
                 * 只打数组与标量，这两类是确定的。
                 */
                {"encode",
                    "print(n.encode({1, 2, 3}))",
                    "print(n.encode({}))",
                    "print(n.encode(1), n.encode(1.5), n.encode('hi'))",
                    "print(n.encode(true), n.encode(false), n.encode(nil))",
                    "print(#n.encode({1, 2, 3}))"},
                {"decode",
                    "local t = n.decode('{\"a\":1,\"b\":[1,2],\"c\":null}')",
                    "print(type(t), t.a, t.b[1], t.b[2])",
                    "print(n.decode('1'), n.decode('1.5'), n.decode('\"s\"'))",
                    "print(n.decode('true'), n.decode('false'))",
                    "print(n.decode('[]')[1], #n.decode('[1,2,3]'))"},
                {"null 是轻量 userdata",
                    "local z = n.decode('null')",
                    "print(type(z))",
                    "print(tostring(z))",
                    "print(rawequal(z, n.decode('null')))"},
                {"错误路径",
                    "print(pcall(n.decode, '{'))",
                    "print(pcall(n.decode, '{\"a\"}'))",
                    "print(pcall(n.decode, 1))",
                    "print(pcall(n.decode))",
                    "print(pcall(n.encode, {[1] = function() end}))"},
            },
            new String[]{"USE_INTERNAL_FPCONV"}, "cjson.so"),
        /*
         * luafilesystem：跟上面两个不一样的一类——它把 <b>C 的 errno 原文</b>
         * 经 {@code luaL_argerror} 报出来（{@code "No such file or directory"}，
         * 而且第二个返回值就是 errno 数字），还用带 {@code __gc} 的 userdata
         * 包 {@code DIR*}。这两条都是只跑真扩展才会走到的路。
         */
        new Ext("LFS_SRC", "lfs",
            new String[]{"src/lfs.c"},
            new String[][]{
                {"装进来",
                    "print(type(n), type(n.attributes), type(n.dir))"},
                {"建目录 / 取属性",
                    "local d = 'target/rt/lfsd'",
                    "os.remove(d .. '/t.txt')",
                    "n.rmdir(d)",
                    "print(n.mkdir(d))",
                    "print(n.attributes(d).mode)",
                    "local f = io.open(d .. '/t.txt', 'w')",
                    "f:write('abc') f:close()",
                    "print(n.attributes(d .. '/t.txt').size)"},
                {"目录迭代",
                    "local d = 'target/rt/lfsd'",
                    "local names = {}",
                    "for x in n.dir(d) do names[#names + 1] = x end",
                    "table.sort(names)",
                    "print(table.concat(names, ','))"},
                {"errno 走 luaL_argerror",
                    "print(n.attributes('target/rt/lfsd/nope'))",
                    "print(n.attributes('target/rt/lfsd/t.txt/nope'))"},
                {"错误路径",
                    "print(pcall(n.attributes))",
                    "print(pcall(n.attributes, 1))",
                    "print(pcall(n.dir, 'target/rt/lfsd/nope'))",
                    "print(type(n.currentdir()))"},
            }),
        /*
         * luabitop：第三类的代表——它是个<b>纯计算</b>扩展，没有 userdata、没有
         * 文件、没有 errno，但它把「C 侧往 Lua 数上按位抠」这条路的每一格都踩
         * 到：{@code lua_Number} 与 {@code lua_Unsigned} 的来回、32 位环绕、
         * 算术右移、{@code luaL_checknumber} 对<b>浮点</b>参数的态度。
         * 而且它返回值一律是<b>浮点</b>（{@code print} 出来是 {@code 15.0}
         * 而不是 {@code 15}），本实现的整数／浮点分裂在这里会被直接看见。
         */
        new Ext("BIT_SRC", "bit",
            new String[]{"bit.c"},
            new String[][]{
                {"装进来",
                    "print(type(n), type(n.band), type(n.bor), type(n.bxor))",
                    "print(type(n.bnot), type(n.lshift), type(n.rshift))",
                    "print(type(n.arshift), type(n.rol), type(n.ror))",
                    "print(type(n.bswap), type(n.tobit), type(n.tohex))"},
                {"位运算",
                    "print(n.band(0xff, 0x0f))",
                    "print(n.bor(1, 2, 4))",
                    "print(n.bxor(0xff, 0x0f))",
                    "print(n.bnot(0))",
                    "print(n.lshift(1, 31))",
                    "print(n.rshift(0x80000000, 31))",
                    "print(n.arshift(-8, 2))",
                    "print(n.rol(1, 4))",
                    "print(n.ror(0x10, 4))",
                    "print(n.bswap(0x11223344))",
                    "print(n.tobit(0xffffffff))",
                    "print(n.band(-1, -1))"},
                {"tohex / 浮点参数 / 越界移位",
                    "print(n.tohex(0xdeadbeef))",
                    "print(n.tohex(-1))",
                    "print(n.tohex(255, 4))",
                    "print(n.tohex(0, -1))",
                    "print('[' .. n.tohex(1, 0) .. ']')",
                    "print(n.band(1.5))",
                    "print(n.band(3.9, 1.9))",
                    "print(n.tobit(2^53))",
                    "print(n.lshift(1, 100))",
                    "print(n.rshift(-1, 100))"},
                {"错误路径",
                    "print(pcall(n.band))",
                    "print(pcall(n.band, 'x'))",
                    "print(pcall(n.band, 1, nil))",
                    "print(pcall(n.tohex, 'x'))",
                    "print(pcall(n.tohex, 1, 'x'))",
                    "print(pcall(n.lshift))",
                    "print(pcall(n.bnot))"},
            },
            new String[]{"LUA_NUMBER_DOUBLE"}, "bit.so"),
        /*
         * luasocket 的 {@code mime.core}：这一类专治 {@code luaL_Buffer}。
         * {@code luaL_buffinit / luaL_addlstring / luaL_addchar / luaL_pushresult}
         * 在 {@code Api} 里全是「在 luashim.c 里用别的 lua_* 拼出来」的壳，
         * Java 侧永远走不到——只有真跑一个重度用 buffer 的扩展才知道那层壳
         * 拼得对不对。而且它一<b>路返回两个值</b>（结果 + 没吃完的尾巴），
         * 多返回值在 C 帧上的摆位也一并被验到。
         */
        new Ext("MIME_SRC", "mime.core",
            new String[]{"src/mime.c"},
            new String[][]{
                {"装进来",
                    "print(type(n), type(n.b64), type(n.unb64), type(n.qp))",
                    "print(type(n.unqp), type(n.qpwrp), type(n.eol))",
                    "print(type(n.dot), type(n.wrp))"},
                {"base64",
                    "print(n.b64('hello'))",
                    "print(n.b64(''))",
                    "print(n.b64('a'))",
                    "print(n.b64('ab'))",
                    "print(n.b64('abc'))",
                    "print(n.b64('hel', 'lo'))",
                    "print(n.unb64('aGVsbG8='))",
                    "print(n.unb64('aGVsbG'))",
                    "print(n.b64(nil))",
                    "print(n.b64(1))"},
                {"quoted-printable",
                    "print(n.qp('hello'))",
                    "print(n.qp('a=b'))",
                    "print(n.qp('caf\\xc3\\xa9'))",
                    "print(n.unqp('a=3Db'))"},
                {"带状态的过滤器：qpwrp / eol / dot / wrp",
                    "print(n.qpwrp(0, 'hello world', 5))",
                    "print(n.qpwrp(0, nil, 5))",
                    "print(n.eol(0, 'a\\nb\\n', '\\r\\n'))",
                    "print(n.eol(0, nil))",
                    "print(n.dot(2, 'a\\nb'))",
                    "print(n.dot(2, nil))",
                    "print(n.wrp(0, 'hello', 3))"},
                {"错误路径",
                    "print(pcall(n.eol, 'x'))",
                    "print(pcall(n.qpwrp, 'x'))"},
            },
            new String[0], "mime_core.so"),
    };

    private static String skipReason = "";

    @TestFactory
    List<DynamicTest> realExtensionMatchesReference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Ext e : EXTS) {
            String path = fixture(e);
            if (path == null) {
                continue;
            }
            String pre = "package.cpath = [==[" + path + "]==] .. \";\" .. package.cpath\n"
                    + "local n = require('" + e.mod + "')\n";
            for (String[] c : e.cases) {
                StringBuilder src = new StringBuilder(pre);
                for (int i = 1; i < c.length; i++) {
                    src.append(c[i]).append('\n');
                }
                String script = src.toString();
                tests.add(DynamicTest.dynamicTest(e.mod + ": " + c[0],
                        () -> Diff.assertMatches(script)));
            }
        }
        Assumptions.assumeTrue(!tests.isEmpty(),
                "跳过真实扩展用例：" + skipReason);
        return tests;
    }

    private static String fixture(Ext e) {
        if (e.path != null) {
            return e.path;
        }
        String dir = System.getenv(e.env);
        if (dir == null || dir.isEmpty()) {
            skipReason += e.env + " 没设；";
            return null;
        }
        if (!NativeBridge.AVAILABLE) {
            skipReason += "native 层没装起来: " + NativeBridge.LOAD_ERROR + "；";
            return null;
        }
        String cc = firstOf("cc", "gcc", "clang");
        if (cc == null) {
            skipReason += "PATH 里没有 C 编译器；";
            return null;
        }
        try {
            File root = new File(dir);
            File out = new File("target/rt/" + e.out);
            List<String> cmd = new ArrayList<>(Arrays.asList(
                    cc, "-shared", "-fPIC", "-undefined", "dynamic_lookup",
                    "-I", "native/lua", "-o", out.getPath()));
            for (String d : e.defs) {
                cmd.add("-D" + d);
            }
            for (String s : e.src) {
                File f = new File(root, s);
                if (!f.isFile()) {
                    skipReason += dir + " 里缺 " + s + "；";
                    return null;
                }
                cmd.add(f.getPath());
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (p.waitFor() != 0 || !out.isFile()) {
                Files.write(new File("target/rt/" + e.mod + "_build.log").toPath(),
                        log.getBytes(StandardCharsets.UTF_8));
                skipReason += "编译 " + e.mod + " 失败，见 target/rt/"
                        + e.mod + "_build.log；";
                return null;
            }
            e.path = out.getAbsolutePath();
        } catch (Exception ex) {
            skipReason += "编译 " + e.mod + " 失败: " + ex + "；";
            return null;
        }
        return e.path;
    }

    private static String firstOf(String... names) {
        for (String n : names) {
            for (String d : System.getenv("PATH").split(File.pathSeparator)) {
                if (new File(d, n).canExecute()) {
                    return n;
                }
            }
        }
        return null;
    }
}
