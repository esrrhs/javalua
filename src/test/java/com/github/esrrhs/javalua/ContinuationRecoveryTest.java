package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Differential witnesses for Lua 5.3.4 protected continuation recovery. */
class ContinuationRecoveryTest {
    static void witness(String source) {
        String script = source + "\nprint('continuation-witness-complete')\n";
        ReferenceLua.Result stock = ReferenceLua.run(script);
        assertEquals(0, stock.exitCode, stock.output);
        assertTrue(stock.output.endsWith("continuation-witness-complete\n"), stock.output);
        Diff.assertMatchesLiteral(script);
    }

    @Test void memoryMessageIsNotMemoryStatusAfterYield() {
        witness("""
                local co=coroutine.create(function()
                  print(xpcall(function() coroutine.yield('paused'); error('not enough memory',0) end,
                    function(e) return 'handled:'..e end))
                end)
                print(coroutine.resume(co)); print(coroutine.resume(co)); print(coroutine.status(co))
                """);
    }

    @Test void failingHandlerAfterYieldUsesErrorHandlingStatus() {
        witness("""
                local co=coroutine.create(function()
                  print(xpcall(function() coroutine.yield('paused'); error('original',0) end,
                    function(e) error('handler-failed',0) end))
                end)
                print(coroutine.resume(co)); print(coroutine.resume(co)); print(coroutine.status(co))
                """);
    }

    @Test void handlerCannotYieldAfterProtectedFunctionResumes() {
        witness("""
                local co=coroutine.create(function()
                  local ok,e=xpcall(function() coroutine.yield('paused'); error('original',0) end,
                    function(e) coroutine.yield('forbidden-handler'); return e end)
                  print(ok,e)
                end)
                print(coroutine.resume(co)); print(coroutine.resume(co)); print(coroutine.status(co))
                """);
    }

    @Test void resumedMessageHandlerSeesTheThrowingFrame() {
        witness("""
                local co=coroutine.create(function()
                  print(xpcall(function() coroutine.yield('paused'); error('original',0) end,
                    function(e) local i=debug.getinfo(2,'n'); print('caller',i and i.name); return e end))
                end)
                print(coroutine.resume(co)); print(coroutine.resume(co)); print(coroutine.status(co))
                """);
    }

    @Test void hookAllowanceRestoredAfterResumedProtectedError() {
        witness("""
                local co=coroutine.create(function()
                  local ok,e=pcall(function()
                    coroutine.yield('paused')
                    debug.sethook(function() error('hook-failed',0) end,'',1)
                    local a=1+2
                  end)
                  debug.sethook()
                  print(ok,e)
                  local hits=0;debug.sethook(function() hits=hits+1 end,'',1)
                  local x=0;for i=1,5 do x=x+i end
                  debug.sethook();print(hits>0,x)
                end)
                print(coroutine.resume(co));print(coroutine.resume(co));print(coroutine.status(co))
                """);
    }

    @Test void repeatedYieldNestedRecoveryUpvaluesAndHooks() {
        witness("""
                local saved
                local co=coroutine.create(function()
                  local n=0
                  debug.sethook(function() n=n+1 end,'',7)
                  local ok,e=pcall(function()
                    local a=41; saved=function() return a end
                    coroutine.yield('one',a); coroutine.yield('two'); error({tag='nested'},0)
                  end)
                  debug.sethook()
                  print(ok,type(e),e.tag,saved(),n>0)
                  print(xpcall(function() coroutine.yield('three'); return 9,nil,11 end,
                    function(e) return e end))
                end)
                for i=1,4 do print(coroutine.resume(co, i)) end
                print(coroutine.status(co),saved())
                local ok,e=load('local a <close> = {}')
                print(ok==nil,type(e))
                """);
    }
}
