package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Regression suite for findings from the project-wide architecture and code audit.
 */
class AuditRegressionTest {

    private static void witnessLiteral(String script) {
        Assumptions.assumeTrue(ReferenceLua.available(), "Lua 5.3.4 oracle required");
        String full = script + "\nprint('audit-reg-ok')\n";
        ReferenceLua.Result oracle = ReferenceLua.run(full);
        assertEquals(0, oracle.exitCode, "oracle failed: " + oracle.output);
        assertTrue(oracle.output.endsWith("audit-reg-ok\n"), oracle.output);
        Diff.assertMatchesLiteral(full);
    }

    @Test
    void tostringDoesNotPinWeakTableEntriesOrSuppressGc() {
        // P0-1: tostring/print calls Api.topointer -> NativeBridge.handle; must not retain objects.
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        L.out = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.run("""
                local finalized = 0
                local wt = setmetatable({}, { __mode = "kv" })
                do
                  local t = setmetatable({}, { __gc = function() finalized = finalized + 1 end })
                  local fn = function() return t end
                  local co = coroutine.create(function() end)
                  wt.t = t
                  wt.fn = fn
                  wt.co = co
                  assert(tostring(t):find("^table: 0x"))
                  assert(tostring(fn):find("^function: 0x"))
                  assert(tostring(co):find("^thread: 0x"))
                end
                -- Note: Lua 5.3 closes lexical upvalues on OP_RETURN or OP_JMP with A>0
                do
                  local clearSlot = 0
                end
                collectgarbage()
                collectgarbage()
                print(finalized, wt.t == nil, wt.fn == nil, wt.co == nil)
                """);
        L.close();
        assertEquals("1\ttrue\ttrue\ttrue\n", bos.toString(StandardCharsets.UTF_8));
    }

    @Test
    void discardedLuaStateWithIoLibIsGarbageCollected() throws InterruptedException {
        // P0-2: IoLib standard streams and opened files must not pin LuaState in static memory.
        WeakReference<LuaState> stateRef = createAndDiscardIoState();
        for (int i = 0; i < 15 && stateRef.get() != null; i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(10);
        }
        assertNull(stateRef.get(), "closed/discarded LuaState was retained in static memory");
    }

    private static WeakReference<LuaState> createAndDiscardIoState() {
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        L.run("""
                local f = assert(io.tmpfile())
                f:write("payload")
                assert(f:close())
                local ok, err = pcall(function() f:read("a") end)
                assert(not ok and err:find("attempt to use a closed file", 1, true))
                """);
        L.close();
        return new WeakReference<>(L);
    }

    @Test
    void runProtectedCatchesSyntaxAndTruncatedBinaryChunkErrors() {
        // P0-6: runProtected must return LuaValue error rather than throwing LuaError on load failure.
        LuaState L = new LuaState();
        LuaLibs.openAll(L);

        LuaValue synErr = L.runProtected("local x = = 1".getBytes(StandardCharsets.UTF_8), "@bad.lua");
        assertNotNull(synErr);
        assertTrue(synErr.isString());
        assertTrue(synErr.asString().toJavaString().contains("unexpected symbol"));

        LuaValue binErr = L.runProtected(new byte[] { 0x1b, 'L', 'u', 'a', 0x00 }, "@bad.luac");
        assertNotNull(binErr);
        assertTrue(binErr.isString());
        assertTrue(binErr.asString().toJavaString().contains("precompiled chunk"));

        assertNull(L.runProtected("local ok = 40 + 2".getBytes(StandardCharsets.UTF_8), "@ok.lua"));
        L.close();
    }

    @Test
    void bufEnsureAndUndumpNegativeCountsFailCleanly() {
        // P1-1: LuaAux.Buf integer overflow must throw LuaError (not spin forever).
        LuaAux.Buf buf = new LuaAux.Buf();
        buf.addAscii("abcd");
        LuaError err = assertThrows(LuaError.class,
                () -> buf.addBytes(new byte[0], 0, Integer.MAX_VALUE - 4));
        assertTrue(err.errMem);

        // Negative vector counts in binary chunks must match stock Lua 5.3.4 ("not enough memory").
        witnessLiteral("""
                local d = string.dump(function(a) local b = a + 1; return b end)
                -- Locate sizecode == 3 (03 00 00 00) after header and mutate to -1 and -2
                local target = "\\003\\000\\000\\000"
                local pos = assert(d:find(target, 35, true))
                for _, neg in ipairs({ "\\255\\255\\255\\255", "\\254\\255\\255\\255" }) do
                  local bad = d:sub(1, pos - 1) .. neg .. d:sub(pos + 4)
                  local fn, msg = load(bad, "negchunk", "b")
                  print(fn, msg)
                end
                """);
    }

    @Test
    void tableLibSupportsFullUserdataProxies() {
        // P1-2: TableLib.checkTab must inspect LuaUserdata instance metatable via LuaTm.metatableOf.
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        LuaTable backing = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.set("__index", LuaValue.ofFunction(s -> {
            LuaValue v = backing.get(s.arg(1));
            s.push(v == null ? LuaValue.ofNil() : v);
            return 1;
        }));
        mt.set("__newindex", LuaValue.ofFunction(s -> {
            backing.set(s.arg(1), s.arg(2));
            return 0;
        }));
        mt.set("__len", LuaValue.ofFunction(s -> {
            s.push(LuaValue.ofInteger(backing.length()));
            return 1;
        }));
        LuaUserdata ud = new LuaUserdata(new Object());
        ud.metatable = mt;
        L.globals.set("proxy", LuaValue.ofUserdata(ud));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        L.out = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.run("""
                table.insert(proxy, "c")
                table.insert(proxy, "a")
                table.insert(proxy, "b")
                table.sort(proxy)
                print(table.concat(proxy, "-"))
                print(table.remove(proxy, 2), table.concat(proxy, ","))
                local dst = {}
                table.move(proxy, 1, 2, 1, dst)
                print(table.concat(dst, ":"))
                """);
        L.close();
        assertEquals("a-b-c\nb\ta,c\na:c\n", bos.toString(StandardCharsets.UTF_8));
    }

    @Test
    void debugSetMetatableRegistersFinalizerForTableAndUserdata() {
        // P1-3: LuaTm.setMetatable must call markFinalizable(L) for both tables and userdata.
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        AtomicBoolean udFinalized = new AtomicBoolean(false);
        LuaTable udMt = new LuaTable();
        udMt.set("__gc", LuaValue.ofFunction(s -> {
            udFinalized.set(true);
            return 0;
        }));
        L.globals.set("ud", LuaValue.ofUserdata(new LuaUserdata(new Object())));
        L.globals.set("udMt", LuaValue.ofTable(udMt));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        L.out = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.run("""
                local tblFinalized = false
                do
                  local t = {}
                  debug.setmetatable(t, { __gc = function() tblFinalized = true end })
                end
                debug.setmetatable(ud, udMt)
                ud = nil
                collectgarbage()
                collectgarbage()
                print(tblFinalized)
                """);
        L.close();
        assertEquals("true\n", bos.toString(StandardCharsets.UTF_8));
        assertTrue(udFinalized.get(), "userdata __gc registered via debug.setmetatable did not run");
    }

    @Test
    void coroutineDelegatesStandardStreamsToMainThread() {
        // P1-4: Coroutines created before stream reassignment must use mainThread.in/out/err.
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        L.run("""
                co = coroutine.create(function()
                  print("co-print")
                  io.stderr:write("co-err\\n")
                  local f = assert(loadfile())
                  print("stdin-res", f())
                end)
                """);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        L.out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        L.err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        L.in = new ByteArrayInputStream("return 777".getBytes(StandardCharsets.UTF_8));

        L.run("assert(coroutine.resume(co))");
        L.close();
        assertEquals("co-print\nstdin-res\t777\n", outBuf.toString(StandardCharsets.UTF_8));
        assertEquals("co-err\n", errBuf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void lua534SemanticsForWrappedCoroutineTracebackGcErrorsLocalsAndHexFloats() {
        // P1-5: coroutine.wrap dead status/traceback, __gc LUA_ERRGCMM, non-ASCII local names, hex-float exponent clamp.
        witnessLiteral("""
                -- 1) Dead coroutine.wrap preserves LUA_ERRRUN frames for debug.traceback(co)
                local capturedCo
                local function leaf()
                  error("wrap-boom", 0)
                end
                local wf = coroutine.wrap(function()
                  capturedCo = coroutine.running()
                  leaf()
                end)
                local ok1, e1 = pcall(wf)
                local tb = debug.traceback(capturedCo)
                local ok2, e2 = pcall(wf)
                print(ok1, e1, coroutine.status(capturedCo))
                print(not not tb:find("leaf", 1, true), ok2, e2)

                -- 2) __gc error propagation: bypasses xpcall msgh, formats non-string as 'no message', ignores non-function
                do local t = setmetatable({}, { __gc = function() error("gc-fail", 0) end }) end
                print(xpcall(collectgarbage, function(x) return "MSGH:" .. tostring(x) end))
                do local t = setmetatable({}, { __gc = function() error(987) end }) end
                print(pcall(collectgarbage))
                do local t = setmetatable({}, { __gc = 123 }) end
                print(pcall(collectgarbage))

                -- 3) Hex float huge positive/negative exponents clamp instead of wrapping int32
                print(tonumber("0x1p4294967296"), tonumber("0x1p-4294967296"))
                """);

        // 4) Non-ASCII local variable names in precompiled chunks must not be double-UTF-8-encoded
        Proto p = Parser.parse("return function(x) local k, v = debug.getlocal(1, 1); local s = debug.setlocal(1, 1, x + 1); return k, s, x end"
                .getBytes(StandardCharsets.UTF_8), "=loc".getBytes(StandardCharsets.UTF_8), new StringPool());
        LuaString rawName = LuaString.of(new byte[] { (byte) 0xE5, (byte) 0x8F, (byte) 0x98, (byte) 0xFF });
        p.p[0].locvars[0].varname = rawName;
        byte[] dumped = Dump.dumpBytes(p, false);

        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        L.globals.set("chunk", LuaValue.ofString(LuaString.of(dumped)));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        L.out = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.run("""
                local fn = assert(load(chunk, "=loc", "b"))()
                local nm = debug.getlocal(fn, 1)
                local k, s, val = fn(41)
                print(#nm, nm == k, nm == s, val, nm:byte(1, 4))
                """);
        L.close();
        assertEquals("4\ttrue\ttrue\t42\t229\t143\t152\t255\n", bos.toString(StandardCharsets.UTF_8));
    }

    @Test
    void mathRandomSeedIsIsolatedPerLuaState() {
        // P1-6: Reseeding math.randomseed in one LuaState must not mutate another LuaState's sequence.
        LuaState s1 = new LuaState();
        LuaLibs.openAll(s1);
        LuaState s2 = new LuaState();
        LuaLibs.openAll(s2);

        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        s1.out = new PrintStream(b1, true, StandardCharsets.UTF_8);
        s2.out = new PrintStream(b2, true, StandardCharsets.UTF_8);

        s1.run("math.randomseed(12345); print(math.random(1, 1000000))");
        // Reseed s2 to a completely different seed before drawing a second number from s1
        s2.run("math.randomseed(99999); print(math.random(1, 1000000))");
        s1.run("print(math.random(1, 1000000))");

        // Compare against an undisturbed state seeded with 12345
        LuaState ref = new LuaState();
        LuaLibs.openAll(ref);
        ByteArrayOutputStream bRef = new ByteArrayOutputStream();
        ref.out = new PrintStream(bRef, true, StandardCharsets.UTF_8);
        ref.run("math.randomseed(12345); print(math.random(1, 1000000)); print(math.random(1, 1000000))");

        s1.close();
        s2.close();
        ref.close();
        assertEquals(bRef.toString(StandardCharsets.UTF_8), b1.toString(StandardCharsets.UTF_8));
    }
}
