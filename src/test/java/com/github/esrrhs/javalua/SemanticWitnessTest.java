package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Targeted differential witnesses for Lua 5.3.4 table boundaries, callbacks, and binary chunks. */
class SemanticWitnessTest {
    private static void witness(String source) {
        if (System.getProperty("javalua.nonative") != null) {
            assertTrue(!NativeBridge.AVAILABLE, "pure Java mode must disable the shim");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(ReferenceLua.available(),
                "prepare the exact Lua 5.3.4 oracle before running semantic witnesses");
        String complete = source + "\nprint('semantic-witness-complete')\n";
        ReferenceLua.Result oracle = ReferenceLua.run(complete);
        assertEquals(0, oracle.exitCode, "oracle witness must complete: " + oracle.output);
        assertTrue(oracle.output.endsWith("semantic-witness-complete\n"), oracle.output);
        Diff.assertMatchesLiteral(complete);
    }

    @Test
    void tableBoundaryBranches() {
        witness("""
                local t = table.pack(1, nil, 3, nil)
                print(#t, rawlen(t), t.n)
                for n = 0, 96 do
                  local a = {}
                  for i = 1, n do a[i] = i end
                  print(n, #a, rawlen(a))
                  if n > 0 then a[n] = nil end
                  print(#a)
                  a[2*n+1] = true
                  print(#a)
                end
                """);
    }

    @Test
    void compatIpairsAndFallback() {
        witness("""
                local t = setmetatable({}, {__ipairs = function(x)
                  return function(_, k) if k < 2 then return k+1, 'meta' end end, x, 0
                end})
                for k, v in ipairs(t) do print(k, v) end
                local f, x, k = ipairs(17)
                print(type(f), x, k)
                local n = 0
                for i,v in ipairs(setmetatable({}, {__index=function(_, i)
                  if i < 4 then return 10*i end
                end})) do n = n + i + v end
                print(n)
                """);
    }

    @Test
    void nonYieldableLibraryCallbacks() {
        witness("""
                local function probe(label, f)
                  local c = coroutine.create(f)
                  local ok, e = coroutine.resume(c)
                  print(label, ok, type(e), type(e)=='string' and
                    not not e:find('attempt to yield across a C-call boundary',1,true))
                  print(coroutine.status(c))
                end
                probe('pairs', function() pairs(setmetatable({}, {__pairs=coroutine.yield})) end)
                probe('ipairs', function() ipairs(setmetatable({}, {__ipairs=coroutine.yield})) end)
                probe('print', function()
                  local old = tostring
                  tostring = coroutine.yield
                  local ok, e = pcall(print, 42)
                  tostring = old
                  error(e, 0)
                end)
                probe('reader', function() load(coroutine.yield) end)
                probe('require', function()
                  package.preload.audit_yield = coroutine.yield
                  require('audit_yield')
                end)
                """);
    }

    @Test
    void protectedCallContinuationResults() {
        witness("""
                local c = coroutine.create(function()
                  local ok,a,b = pcall(function()
                    local x = coroutine.yield('first')
                    return x, 'last'
                  end)
                  print(ok,a,b)
                  local ok,e = xpcall(function() error('original',0) end,
                    function(e) return 'handled:'..e end)
                  print(ok,e)
                end)
                print(coroutine.resume(c))
                print(coroutine.resume(c, 'resumed'))
                print(coroutine.status(c))
                """);
    }

    @Test
    void booleanConstantPayloadsLoadOnBothRuntimes() {
        for (boolean value : new boolean[] { false, true }) {
            Proto p = Parser.parse("return 12345".getBytes(StandardCharsets.UTF_8),
                    "=audit".getBytes(StandardCharsets.UTF_8), new StringPool());
            assertEquals(1, p.sizek);
            p.k[0].setBoolean(value);
            byte[] chunk = Dump.dumpBytes(p, false);
            StringBuilder escaped = new StringBuilder();
            for (byte b : chunk) {
                escaped.append('\\').append(String.format("%03d", b & 255));
            }
            witness("local f=assert(load('" + escaped
                    + "', '=audit', 'b')); local v=f(); print(type(v), v)");
        }
    }

    @Test
    void debugNamesAndStrippedRoundTrip() {
        witness("""
                local n = 7
                local function outer(x)
                  return function(...) return n+x, ... end
                end
                for _, strip in ipairs{false,true} do
                  local f = assert(load(string.dump(outer, strip)))
                  debug.setupvalue(f,1,7)
                  print(f(3)('a','b'))
                  local name = debug.getupvalue(f,1)
                  print(type(name), strip or name == 'n')
                end
                """);
    }

    @Test
    void loadModeAndReaderErrorBoundary() {
        witness("""
                local b = string.dump(function() return 19 end)
                local f,e=load(b,'=audit','t')
                print(f, e)
                local f,e=load('return 20','=audit','b')
                print(f, e)
                print(assert(load(b,'=audit','b'))())
                local seen=0
                local f,e=load(function() error('reader-error',0) end)
                print(f,e,seen)
                """);
    }

    @Test
    void returnVarargBytecodeWitness() {
        // Existing DumpTest checks a larger grid. These direct field assertions
        // lock the two easy-to-miss 5.3.4 instruction initializers independently.
        Proto p = Parser.parse("local x=1; return".getBytes(StandardCharsets.UTF_8),
                "=audit".getBytes(StandardCharsets.UTF_8), new StringPool());
        boolean found = false;
        for (int ins : p.code) {
            if (OpCode.getOpCode(ins) == OpCode.OP_RETURN) {
                assertEquals(0, OpCode.getArgA(ins));
                assertEquals(1, OpCode.getArgB(ins));
                found = true;
            }
        }
        assertTrue(found);
        witness("local function f(...) local a,b=...; return a,b,select('#',...) end; print(f(1,nil,3))");
    }
}
