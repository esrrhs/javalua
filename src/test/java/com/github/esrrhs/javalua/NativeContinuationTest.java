package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Real stock-header C API witnesses; no cases in explicitly native-disabled mode. */
class NativeContinuationTest {
    @TestFactory List<DynamicTest> continuationContract() throws Exception {
        if (!NativeBridge.AVAILABLE) {
            assertNotEquals("native", System.getProperty("javalua.test.mode"), NativeBridge.LOAD_ERROR);
            return List.of();
        }
        Path dir=Files.createDirectories(Path.of("target/native-continuation"));
        Path source=dir.resolve("probe.c");
        Path library=dir.resolve("probe.so").toAbsolutePath();
        Files.writeString(source, """
                #include "lua.h"
                #include "lauxlib.h"
                static int after(lua_State *L, int s, lua_KContext ctx) {
                  lua_pushinteger(L,s); lua_insert(L,1);
                  lua_pushinteger(L,ctx); lua_insert(L,2);
                  return lua_gettop(L);
                }
                static int call(lua_State *L) {
                  lua_settop(L,1); lua_callk(L,0,LUA_MULTRET,91,after);
                  return after(L,LUA_OK,91);
                }
                static int protected(lua_State *L) {
                  int ef=0;
                  if (lua_isfunction(L,2)) {lua_settop(L,2);lua_insert(L,1);ef=1;}
                  else lua_settop(L,1);
                  int s=lua_pcallk(L,0,LUA_MULTRET,ef,92,after);
                  return after(L,s,92);
                }
                static int nok(lua_State *L) {
                  lua_settop(L,1); int s=lua_pcall(L,0,LUA_MULTRET,0);
                  return after(L,s,93);
                }
                int luaopen_contprobe(lua_State *L) {
                  luaL_Reg f[]={{"call",call},{"protected",protected},{"nok",nok},{NULL,NULL}};
                  luaL_newlib(L,f);return 1;
                }
                """,StandardCharsets.UTF_8);
        List<String> command=new ArrayList<>(List.of("cc","-shared","-fPIC","-I","target/lua53/src",
                "-o",library.toString(),source.toString()));
        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"))
            command.addAll(1,List.of("-undefined","dynamic_lookup"));
        Path log=dir.resolve("compile.log");
        Process compiler=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(compiler.waitFor(30,TimeUnit.SECONDS),"compile timeout");
            assertEquals(0,compiler.exitValue(),Files.readString(log));
        } finally { compiler.destroyForcibly(); }
        String pre="local n=assert(package.loadlib([==["+library+"]==], 'luaopen_contprobe'))()\n";
        String drive="""
                local function drive(f)
                  local c=coroutine.create(f)
                  for i=1,5 do
                    if coroutine.status(c)=='dead' then break end
                    print(coroutine.resume(c,'resume'..i))
                  end
                  print('state',coroutine.status(c))
                end
                """;
        return List.of(
            DynamicTest.dynamicTest("null continuation cannot cross pcall boundary",()->ContinuationRecoveryTest.witness(pre+drive+"""
                drive(function()
                  local s,ctx,e=n.nok(function() coroutine.yield('forbidden') end)
                  print(s,ctx,type(e),not not e:find('attempt to yield across a C-call boundary',1,true))
                end)
                """)),
            DynamicTest.dynamicTest("native callk nested repeated yields and hooks",()->ContinuationRecoveryTest.witness(pre+drive+"""
                drive(function()
                  local hits=0; debug.sethook(function() hits=hits+1 end,'',9)
                  print(n.call(function() coroutine.yield('a'); coroutine.yield('b'); return 10,nil,12 end))
                  local ok,e=pcall(n.call,function() coroutine.yield('c'); error('call-failed',0) end)
                  debug.sethook();print(ok,e,hits>0)
                end)
                """)),
            DynamicTest.dynamicTest("native pcallk statuses and upvalue recovery",()->ContinuationRecoveryTest.witness(pre+drive+"""
                local saved
                drive(function()
                  print(n.protected(function() coroutine.yield('a'); coroutine.yield('b'); return 4,nil,6 end))
                  print(n.protected(function()
                    local x=57;saved=function() return x end;coroutine.yield('c');error('after-resume',0)
                  end))
                  print(saved())
                end)
                """)),
            DynamicTest.dynamicTest("native pcallk message handler cannot yield",()->ContinuationRecoveryTest.witness(pre+drive+"""
                drive(function()
                  local s,ctx,h,e=n.protected(function() coroutine.yield('first');error('not enough memory',0) end,
                    function(e) return 'H:'..e end)
                  print(s,ctx,type(h),e)
                  local s,ctx,h,e=n.protected(function() coroutine.yield('second');error('x',0) end,
                    function() coroutine.yield('forbidden') end)
                  print(s,ctx,type(h),e)
                end)
                """)));
    }
}
