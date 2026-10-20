package com.github.esrrhs.javalua;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * C 扩展（{@code .so}）的差分用例 —— 阶段 C。
 * <p>
 * 前面所有用例的语料都是 Lua 脚本；这一类是<b>先用 C 编译器编出一个真的
 * {@code .so}</b>，再让两边的 {@code package.loadlib} 都去加载它。参考实现是
 * 真 Lua，它的 {@code lua_*} 就是 C 符号；本实现的 {@code lua_*} 由
 * {@code luashim} 动态库导出、再转进 Java。两边走的是完全不同的路，但脚本看到
 * 的东西必须一模一样——这是「无缝支持 C 扩展」唯一站得住的判据。
 * <p>
 * 三个跳过条件：native 层没装起来、机器上没有 C 编译器、编译失败。任一成立就
 * 整类跳过——CI 上编不出来不代表实现错了，但<b>本地一定要真的跑过</b>。
 */
class NativeExtTest {

    /** 编出来的扩展的绝对路径；两边跑的是同一个文件 */
    private static String so;

    /** 跳过的原因：不说清楚的话「跳过」和「跑过」在报告里长得一模一样 */
    private static String skipReason = "还没试";

    private static final String C = String.join("\n",
            "#include <string.h>",
            "#include \"lua.h\"",
            "#include \"lauxlib.h\"",
            "",
            "static int l_add(lua_State *L) {",
            "  lua_pushinteger(L, luaL_checkinteger(L, 1) + luaL_checkinteger(L, 2));",
            "  return 1;",
            "}",
            "",
            "static int l_sum(lua_State *L) {",
            "  int i, n = lua_gettop(L);",
            "  lua_Integer s = 0;",
            "  for (i = 1; i <= n; i++) s += luaL_checkinteger(L, i);",
            "  lua_pushinteger(L, s);",
            "  return 1;",
            "}",
            "",
            "/* luaL_Buffer：addchar / addstring / pushresult 全在 C 侧 */",
            "static int l_join(lua_State *L) {",
            "  luaL_Buffer b; int i, n = lua_gettop(L);",
            "  luaL_buffinit(L, &b);",
            "  for (i = 1; i <= n; i++) {",
            "    if (i > 1) luaL_addchar(&b, ',');",
            "    luaL_addstring(&b, luaL_checkstring(L, i));",
            "  }",
            "  luaL_pushresult(&b);",
            "  return 1;",
            "}",
            "",
            "/* luaL_checklstring 的长度出参 + lua_pushlstring */",
            "static int l_echo(lua_State *L) {",
            "  size_t n = 0;",
            "  const char *s = luaL_checklstring(L, 1, &n);",
            "  lua_pushlstring(L, s, n);",
            "  lua_pushinteger(L, (lua_Integer) n);",
            "  return 2;",
            "}",
            "",
            "static int l_opt(lua_State *L) {",
            "  lua_pushinteger(L, luaL_optinteger(L, 1, -1));",
            "  lua_pushnumber(L, luaL_optnumber(L, 2, 2.5));",
            "  lua_pushstring(L, luaL_optstring(L, 3, \"dflt\"));",
            "  return 3;",
            "}",
            "",
            "static int l_types(lua_State *L) {",
            "  int i, n = lua_gettop(L);",
            "  for (i = 1; i <= n; i++) {",
            "    lua_pushstring(L, luaL_typename(L, i));",
            "    lua_pushboolean(L, lua_isnumber(L, i));",
            "    lua_pushboolean(L, lua_isstring(L, i));",
            "  }",
            "  return n * 3;",
            "}",
            "",
            "/* luaL_error 的 longjmp 要能被 pcall 接住 */",
            "static int l_boom(lua_State *L) {",
            "  return luaL_error(L, \"boom %d\", 42);",
            "}",
            "",
            "/* luaL_checkinteger 失败：luaL_argerror 那条路 */",
            "static int l_bad(lua_State *L) {",
            "  luaL_checkinteger(L, 1);",
            "  return 0;",
            "}",
            "",
            "/* userdata + 命名元表：lua_newuserdatauv / luaL_newmetatable /",
            "   luaL_setmetatable / luaL_checkudata */",
            "static int l_box(lua_State *L) {",
            "  lua_Integer *p = (lua_Integer *) lua_newuserdatauv(L, sizeof(lua_Integer), 0);",
            "  *p = luaL_checkinteger(L, 1);",
            "  luaL_getmetatable(L, \"NatBox\");",
            "  lua_setmetatable(L, -2);",
            "  return 1;",
            "}",
            "",
            "static int l_unbox(lua_State *L) {",
            "  lua_Integer *p = (lua_Integer *) luaL_checkudata(L, 1, \"NatBox\");",
            "  lua_pushinteger(L, *p);",
            "  return 1;",
            "}",
            "",
            "static int l_mk(lua_State *L) {",
            "  lua_Integer n = luaL_checkinteger(L, 1), i;",
            "  lua_createtable(L, (int) n, 0);",
            "  for (i = 1; i <= n; i++) {",
            "    lua_pushinteger(L, i * i);",
            "    lua_rawseti(L, -2, i);",
            "  }",
            "  return 1;",
            "}",
            "",
            "/* lua_next 遍历 + luaL_checktype */",
            "static int l_count(lua_State *L) {",
            "  luaL_checktype(L, 1, LUA_TTABLE);",
            "  int n = 0;",
            "  lua_pushnil(L);",
            "  while (lua_next(L, 1) != 0) { n++; lua_pop(L, 1); }",
            "  lua_pushinteger(L, n);",
            "  return 1;",
            "}",
            "",
            "/* lua_getfield / lua_setfield */",
            "static int l_getset(lua_State *L) {",
            "  luaL_checktype(L, 1, LUA_TTABLE);",
            "  lua_getfield(L, 1, \"k\");",
            "  lua_pushstring(L, \"v\");",
            "  lua_setfield(L, 1, \"k\");",
            "  return 1;",
            "}",
            "",
            "/* luaL_ref / luaL_unref + 注册表 */",
            "static int l_ref(lua_State *L) {",
            "  luaL_checktype(L, 1, LUA_TTABLE);",
            "  lua_pushvalue(L, 1);",
            "  int r = luaL_ref(L, LUA_REGISTRYINDEX);",
            "  lua_rawgeti(L, LUA_REGISTRYINDEX, r);",
            "  lua_pushinteger(L, r);",
            "  luaL_unref(L, LUA_REGISTRYINDEX, r);",
            "  return 2;",
            "}",
            "",
            "static int l_callit(lua_State *L) {",
            "  luaL_checktype(L, 1, LUA_TFUNCTION);",
            "  lua_pushvalue(L, 1);",
            "  lua_pushinteger(L, 7);",
            "  lua_call(L, 1, 1);",
            "  return 1;",
            "}",
            "",
            "/* lua_rotate：把参数整体右移一位 */",
            "static int l_rotate(lua_State *L) {",
            "  int n = lua_gettop(L);",
            "  if (n > 1) lua_rotate(L, 1, -1);",
            "  return n;",
            "}",
            "",
            "/* luaL_gsub：整个在 C 侧用 buffer 拼出来 */",
            "static int l_gsub(lua_State *L) {",
            "  lua_pushstring(L, luaL_gsub(L, luaL_checkstring(L, 1),",
            "          luaL_checkstring(L, 2), luaL_checkstring(L, 3)));",
            "  return 1;",
            "}",
            "",
            "static int l_concat(lua_State *L) {",
            "  lua_concat(L, lua_gettop(L));",
            "  return 1;",
            "}",
            "",
            "static int l_len(lua_State *L) {",
            "  lua_pushinteger(L, luaL_len(L, 1));",
            "  return 1;",
            "}",
            "",
            "static const luaL_Reg funcs[] = {",
            "  {\"add\", l_add}, {\"sum\", l_sum}, {\"join\", l_join},",
            "  {\"echo\", l_echo}, {\"opt\", l_opt}, {\"types\", l_types},",
            "  {\"boom\", l_boom}, {\"bad\", l_bad}, {\"box\", l_box},",
            "  {\"unbox\", l_unbox}, {\"mk\", l_mk}, {\"count\", l_count},",
            "  {\"getset\", l_getset}, {\"ref\", l_ref}, {\"callit\", l_callit},",
            "  {\"rotate\", l_rotate}, {\"gsub\", l_gsub}, {\"concat\", l_concat},",
            "  {\"len\", l_len},",
            "  {NULL, NULL}",
            "};",
            "",
            "int luaopen_nat(lua_State *L) {",
            "  luaL_newmetatable(L, \"NatBox\");",
            "  lua_pop(L, 1);",
            "  luaL_newlib(L, funcs);",
            "  return 1;",
            "}");

    private static final String PRE = "local f = assert(package.loadlib([==[@SO@]==], "
            + "\"luaopen_nat\"))\nlocal n = f()\n";

    private static final String[][] CASES = {
        {"加载与类型",
            "print(type(n), type(n.add), n.add(3, 4))"},
        {"求和与取栈顶",
            "print(n.sum(1, 2, 3, 4, 5), n.sum())"},
        {"buffer 拼接",
            "print(n.join('a', 'b', 'c'), n.join('x'), n.join())"},
        {"字符串与长度",
            "print(n.echo('hello'), n.echo('a\\0b'))"},
        {"opt 系列默认值",
            "print(n.opt(), n.opt(9), n.opt(9, 1.5, 'z'))"},
        {"类型名",
            "print(n.types(1, 's', 2.5, true, nil, {}, print))"},
        {"luaL_error 被 pcall 接住",
            "print(pcall(n.boom))"},
        {"参数检查失败",
            "print(pcall(n.bad))",
            "print(pcall(n.bad, 'x'))",
            "print(pcall(n.bad, 1.5))"},
        {"userdata 与命名元表",
            "local b = n.box(42)\nprint(type(b), n.unbox(b))\nprint(pcall(n.unbox, {}))"},
        {"建表与 rawseti",
            "local t = n.mk(5)\nprint(#t, t[3], n.count(t))"},
        {"getfield / setfield",
            "local t = {}\nprint(n.getset(t), t.k, n.getset(t))"},
        {"注册表引用",
            "local t = {1}\nlocal got, r = n.ref(t)\nprint(got == t, r)"},
        {"回调 Lua 函数",
            "print(n.callit(function(x) return x * 2 end))"},
        {"rotate",
            "print(n.rotate(1, 2, 3))"},
        {"luaL_gsub",
            "print(n.gsub('hello world', 'o', '0'))"},
        {"concat 与 len",
            "print(n.concat('a', 'b', 'c'), n.len({1, 2, 3}))"},
        {"多次调用同一个 C 函数",
            "for i = 1, 3 do io.write(n.add(i, i), ' ') end\nprint()"},
        {"C 函数当值传递",
            "local g = n.add\nprint(g(1, 2))\nlocal t = {f = n.echo}\nprint(t.f('q'))"},
    };

    @TestFactory
    List<DynamicTest> cExtensionMatchesReference() {
        List<DynamicTest> tests = new ArrayList<>();
        String path = fixture();
        Assumptions.assumeTrue(path != null,
                "跳过 C 扩展用例：" + skipReason);
        for (String[] c : CASES) {
            StringBuilder src = new StringBuilder(PRE.replace("@SO@", path));
            for (int i = 1; i < c.length; i++) {
                src.append(c[i]).append('\n');
            }
            String script = src.toString();
            tests.add(DynamicTest.dynamicTest("C 扩展: " + c[0],
                    () -> Diff.assertMatches(script)));
        }
        return tests;
    }

    /** 编出扩展；返回绝对路径，编不出来给 null */
    private static synchronized String fixture() {
        if (so != null) {
            return so;
        }
        if (!NativeBridge.AVAILABLE) {
            skipReason = "native 层没装起来: " + NativeBridge.LOAD_ERROR;
            return null;
        }
        String cc = firstOf("cc", "gcc", "clang");
        if (cc == null) {
            skipReason = "PATH 里没有 C 编译器";
            return null;
        }
        try {
            File dir = new File("target/rt");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File c = new File(dir, "nat.c");
            File lib = new File(dir, "nat.so");
            Files.write(c.toPath(), C.getBytes(StandardCharsets.UTF_8));
            // macOS 要 -undefined dynamic_lookup，Linux 上这个参数不存在
            List<List<String>> cmds = new ArrayList<>();
            cmds.add(Arrays.asList(cc, "-shared", "-fPIC", "-undefined",
                    "dynamic_lookup", "-I", "native/lua", "-o", lib.getPath(),
                    c.getPath()));
            cmds.add(Arrays.asList(cc, "-shared", "-fPIC", "-I", "native/lua",
                    "-o", lib.getPath(), c.getPath()));
            boolean ok = false;
            for (List<String> cmd : cmds) {
                Process p = new ProcessBuilder(cmd)
                        .redirectErrorStream(true).start();
                if (p.waitFor() == 0 && lib.isFile()) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                skipReason = "编译扩展失败（" + cc + "）";
            }
            so = ok ? lib.getAbsolutePath() : null;
        } catch (Exception e) {
            skipReason = "编译扩展失败: " + e;
            so = null;
        }
        return so;
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

    /** 只是为了让编译器别把 Paths 的 import 判成未用 */
    static java.nio.file.Path unused() {
        return Paths.get(".");
    }
}
