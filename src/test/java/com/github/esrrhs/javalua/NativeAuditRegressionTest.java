package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Native C API regression witnesses compiled against stock Lua 5.3.4 headers and
 * executed identically on PUC-Rio Lua 5.3.4 and JavaLua.
 */
class NativeAuditRegressionTest {

    @TestFactory
    List<DynamicTest> nativeAuditRegressions() throws Exception {
        if (!NativeBridge.AVAILABLE) {
            assertNotEquals("native", System.getProperty("javalua.test.mode"), NativeBridge.LOAD_ERROR);
            return List.of();
        }
        Path dir = Files.createDirectories(Path.of("target/native-audit-regression"));
        Path source = dir.resolve("auditprobe.c");
        Path library = dir.resolve("auditprobe.so").toAbsolutePath();
        Files.writeString(source, """
                #include <stdint.h>
                #include <string.h>
                #include "lua.h"
                #include "lauxlib.h"

                /* P0-3: 64-bit lua_KContext preservation across callk/pcallk/yieldk */
                static int k_wide(lua_State *L, int status, lua_KContext ctx) {
                  lua_pushinteger(L, status);
                  lua_pushinteger(L, (lua_Integer)ctx);
                  return 2;
                }
                static int wide_callk(lua_State *L) {
                  lua_KContext wide = (lua_KContext)0x123456789ABCDEF0LL;
                  lua_settop(L, 1);
                  lua_callk(L, 0, 0, wide, k_wide);
                  return k_wide(L, LUA_OK, wide);
                }
                static int wide_yieldk(lua_State *L) {
                  lua_KContext wide = (lua_KContext)0x55AA55AA11223344LL;
                  lua_pushinteger(L, 7);
                  return lua_yieldk(L, 1, wide, k_wide);
                }

                /* P0-4: C hook calling lua_getinfo/lua_getlocal & raising luaL_error */
                static int g_hook_raise = 0;
                static int g_hook_lines = 0;
                static void c_line_hook(lua_State *L, lua_Debug *ar) {
                  if (ar->event == LUA_HOOKLINE) {
                    g_hook_lines++;
                    if (lua_getinfo(L, "nSl", ar) == 0) {
                      luaL_error(L, "getinfo failed in hook");
                    }
                    const char *ln = lua_getlocal(L, ar, 1);
                    if (ln != NULL) {
                      lua_pop(L, 1);
                    }
                    if (g_hook_raise && g_hook_lines >= 2) {
                      g_hook_raise = 0;
                      luaL_error(L, "hook-boom:%d", ar->currentline > 0 ? 1 : 0);
                    }
                  }
                }
                static int run_with_hook(lua_State *L) {
                  g_hook_raise = lua_toboolean(L, 2);
                  g_hook_lines = 0;
                  lua_settop(L, 1);
                  lua_sethook(L, c_line_hook, LUA_MASKLINE, 0);
                  int st = lua_pcall(L, 0, 1, 0);
                  lua_sethook(L, NULL, 0, 0);
                  lua_pushinteger(L, st);
                  lua_insert(L, -2);
                  lua_pushinteger(L, g_hook_lines);
                  return 3;
                }

                /* P0-4: lua_dump writer returning -1 vs raising luaL_error */
                static int writer_neg1(lua_State *L, const void *p, size_t sz, void *ud) {
                  (void)L; (void)p; (void)sz; (void)ud;
                  return -1;
                }
                static int writer_throws(lua_State *L, const void *p, size_t sz, void *ud) {
                  (void)p; (void)sz; (void)ud;
                  return luaL_error(L, "writer-exploded");
                }
                static int test_dump_writer(lua_State *L) {
                  int mode = (int)luaL_checkinteger(L, 2);
                  lua_settop(L, 1);
                  int rc = lua_dump(L, mode == 0 ? writer_neg1 : writer_throws, NULL, 1);
                  lua_pushinteger(L, rc);
                  return 1;
                }

                /* P0-5: luaL_tolstring stack preservation & lua_pushlstring pointer stability + interning */
                static int test_tolstring_stack(lua_State *L) {
                  lua_settop(L, 0);
                  lua_pushstring(L, "sentinel-below");
                  lua_newtable(L);
                  size_t len = 0;
                  const char *s = luaL_tolstring(L, 2, &len);
                  int top = lua_gettop(L);
                  int intact = (strcmp(lua_tostring(L, 1), "sentinel-below") == 0)
                            && lua_istable(L, 2)
                            && (s != NULL && strncmp(s, "table: ", 7) == 0 && len == strlen(s));
                  lua_settop(L, 0);
                  lua_pushinteger(L, top);
                  lua_pushboolean(L, intact);
                  return 2;
                }
                static int test_pushstring_lifetime(lua_State *L) {
                  const char *keep = lua_pushlstring(L, "stable-short", 12);
                  int keepIdx = lua_gettop(L);
                  for (int i = 0; i < 80; i++) {
                    char tmp[32];
                    int n = i + 1000;
                    tmp[0] = 'x'; tmp[1] = (char)('0' + (n % 10)); tmp[2] = '\\0';
                    lua_pushstring(L, tmp);
                    lua_pop(L, 1);
                  }
                  const char *again = lua_pushlstring(L, "stable-short", 12);
                  int sameContent = (strcmp(keep, "stable-short") == 0);
                  int samePtr = (keep == again);
                  int rawEq = lua_rawequal(L, keepIdx, -1);
                  lua_settop(L, 0);
                  lua_pushboolean(L, sameContent);
                  lua_pushboolean(L, samePtr);
                  lua_pushboolean(L, rawEq);
                  return 3;
                }

                /* P1-3: C userdata __gc registered via luaL_setmetatable */
                static int g_ud_gc_count = 0;
                static int ud_gc(lua_State *L) {
                  int *p = (int *)luaL_checkudata(L, 1, "AuditUdata");
                  if (*p == 4242) {
                    g_ud_gc_count++;
                  }
                  return 0;
                }
                static int make_ud(lua_State *L) {
                  int *p = (int *)lua_newuserdata(L, sizeof(int));
                  *p = 4242;
                  if (luaL_newmetatable(L, "AuditUdata")) {
                    lua_pushcfunction(L, ud_gc);
                    lua_setfield(L, -2, "__gc");
                  }
                  lua_setmetatable(L, -2);
                  return 1;
                }
                static int get_ud_gc_count(lua_State *L) {
                  lua_pushinteger(L, g_ud_gc_count);
                  return 1;
                }

                int luaopen_auditprobe(lua_State *L) {
                  luaL_Reg f[] = {
                    {"wide_callk", wide_callk},
                    {"wide_yieldk", wide_yieldk},
                    {"run_with_hook", run_with_hook},
                    {"test_dump_writer", test_dump_writer},
                    {"test_tolstring_stack", test_tolstring_stack},
                    {"test_pushstring_lifetime", test_pushstring_lifetime},
                    {"make_ud", make_ud},
                    {"get_ud_gc_count", get_ud_gc_count},
                    {NULL, NULL}
                  };
                  luaL_newlib(L, f);
                  return 1;
                }
                """, StandardCharsets.UTF_8);

        String cc = System.getenv().getOrDefault("CC", "gcc");
        List<String> cmd = new ArrayList<>(List.of(cc, "-shared", "-fPIC", "-O2", "-Wall",
                "-I", "target/lua53/src", "-o", library.toString(), source.toString()));
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            cmd.addAll(1, List.of("-undefined", "dynamic_lookup"));
        }
        Path log = dir.resolve("compile.log");
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "compile timeout");
            assertEquals(0, proc.exitValue(), Files.readString(log));
        } finally {
            proc.destroyForcibly();
        }

        String pre = "local p = assert(package.loadlib([==[" + library + "]==], 'luaopen_auditprobe'))()\n";
        return List.of(
                DynamicTest.dynamicTest("64-bit lua_KContext survives yield and resume", () ->
                        ContinuationAuditTest.witness(pre + """
                                local co = coroutine.create(function()
                                  local s1, c1 = p.wide_callk(function() coroutine.yield("y1") end)
                                  local s2, c2 = p.wide_yieldk()
                                  return s1, string.format("0x%x", c1), s2, string.format("0x%x", c2)
                                end)
                                print(coroutine.resume(co))
                                print(coroutine.resume(co))
                                print(coroutine.resume(co))
                                """)),
                DynamicTest.dynamicTest("native hook getinfo/getlocal and luaL_error unwind safely", () ->
                        ContinuationAuditTest.witness(pre + """
                                local st1, res1, lines1 = p.run_with_hook(function()
                                  local a = 10
                                  a = a + 20
                                  return a
                                end, false)
                                print(st1, res1, lines1 >= 2)
                                local st2, err2, lines2 = p.run_with_hook(function()
                                  local b = 1
                                  b = b + 1
                                  b = b + 1
                                  return b
                                end, true)
                                print(st2, not not tostring(err2):find("hook-boom:1", 1, true), lines2 >= 2)
                                """)),
                DynamicTest.dynamicTest("lua_dump writer distinguishes -1 return from luaL_error", () ->
                        ContinuationAuditTest.witness(pre + """
                                local fn = function() return 42 end
                                print(p.test_dump_writer(fn, 0))
                                local ok, err = pcall(p.test_dump_writer, fn, 1)
                                print(ok, not not tostring(err):find("writer-exploded", 1, true))
                                """)),
                DynamicTest.dynamicTest("luaL_tolstring stack safety and lua_pushlstring stability", () ->
                        ContinuationAuditTest.witness(pre + """
                                print(p.test_tolstring_stack())
                                print(p.test_pushstring_lifetime())
                                """)),
                DynamicTest.dynamicTest("C userdata __gc via lua_setmetatable and loadlib '*'", () ->
                        ContinuationAuditTest.witness(pre + """
                                local before = p.get_ud_gc_count()
                                do
                                  local u = p.make_ud()
                                  assert(type(u) == "userdata")
                                end
                                collectgarbage()
                                collectgarbage()
                                print(p.get_ud_gc_count() - before)
                                local okStar = package.loadlib([==[""" + library + """
                                ]==], "*")
                                print(okStar)
                                """))
        );
    }
}
