package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExecutionControlTest {
    private static void run(LuaState state, String source) {
        int base = state.top;
        state.pushClosure(state.load(source.getBytes(StandardCharsets.UTF_8), "=controls"));
        LuaValue error = state.pcall(base, 0);
        assertNull(error, () -> String.valueOf(error));
    }

    @Test void disabledNormalCodeAndDefaultLibraries() {
        LuaState state = new LuaState();
        LuaLibs.openAll(state);
        run(state, "local n=0; for i=1,10000 do n=n+i end; assert(n==50005000); "
                + "assert(type(package)=='table' and type(debug)=='table'); "
                + "assert(type(io)=='table' and type(os)=='table' and type(load)=='function')");
        assertEquals(-1, state.executionControl().remainingInstructions());
        assertFalse(state.executionControl().isAborted());
        state.close();
    }

    @Test void cumulativeBudgetIdleResetAndUnlimitedReset() {
        LuaState state = new LuaState();
        ExecutionControl control = state.executionControl();
        control.reset(1000, null);
        run(state, "local x=1+2");
        long first = control.remainingInstructions();
        assertTrue(first < 1000 && first > 0);
        run(state, "local x=1+2");
        assertEquals(1000 - 2 * (1000 - first), control.remainingInstructions());
        control.reset(1000, null);
        assertEquals(1000, control.remainingInstructions());
        control.reset(-1, null);
        run(state, "local x=0; for i=1,2000 do x=x+i end");
        assertEquals(-1, control.remainingInstructions());
        state.close();
    }

    @Test void exactSingleDispatchAllowance() {
        LuaState state = new LuaState();
        state.executionControl().reset(1, null);
        run(state, "return");
        assertEquals(0, state.executionControl().remainingInstructions());
        assertThrows(ExecutionAbortedError.class, () -> run(state, "return"));
        state.close();
    }

    @Test void cancellationCannotBeLostByAnIdleReset() throws Exception {
        for (int i = 0; i < 30; i++) {
            LuaState state = new LuaState();
            ExecutionControl control = state.executionControl();
            Thread cancel = new Thread(control::cancel);
            cancel.start();
            try {
                control.reset(-1, null);
            } catch (IllegalStateException alreadyCancelled) {
                assertTrue(control.isAborted());
            }
            cancel.join(1000);
            assertFalse(cancel.isAlive());
            assertEquals(ExecutionAbortedError.Reason.CANCELLED,
                    assertThrows(ExecutionAbortedError.class, control::checkpoint).reason());
            state.close();
        }
    }

    @Test void cancellationAppliesToSuspendedAndFutureCoroutines() {
        LuaState state = new LuaState();
        LuaLibs.openAll(state);
        LuaState co = state.newThread();
        co.pushClosure(co.load("coroutine.yield(); return 42".getBytes(StandardCharsets.UTF_8), "=co"));
        assertEquals(LuaState.LUA_YIELD, co.resume(null, 0, new int[1]));
        state.executionControl().cancel();
        assertEquals(ExecutionAbortedError.Reason.CANCELLED,
                assertThrows(ExecutionAbortedError.class, () -> co.resume(null, 0, new int[1])).reason());
        assertThrows(ExecutionAbortedError.class,
                () -> state.newThread().resume(null, 0, new int[1]));
        state.close();
    }

    @Test void zeroBudgetAbortsOutsidePcallAndCannotBeRearmed() {
        LuaState state = new LuaState();
        LuaState before = state.newThread();
        ExecutionControl control = state.executionControl();
        assertSame(control, before.executionControl());
        control.reset(0, null);
        ExecutionAbortedError error = assertThrows(ExecutionAbortedError.class,
                () -> run(state, "return 1"));
        assertEquals(ExecutionAbortedError.Reason.INSTRUCTION_BUDGET, error.reason());
        assertTrue(control.isAborted());
        assertThrows(IllegalStateException.class, () -> control.reset(-1, null));
        control.cancel(); // First observed reason stays latched.
        assertEquals(error.reason(), assertThrows(ExecutionAbortedError.class,
                control::checkpoint).reason());
        assertSame(control, state.newThread().executionControl());
        assertThrows(ExecutionAbortedError.class, () -> before.resume(null, 0, new int[1]));
        assertThrows(ExecutionAbortedError.class, () -> state.call(1, 0));
        state.close(); // No re-entry of partially unwound script frames.
    }

    @Test void validationAndNoResetInsideCallback() {
        LuaState state = new LuaState();
        ExecutionControl c = state.executionControl();
        assertThrows(IllegalArgumentException.class, () -> c.reset(-2, null));
        assertThrows(IllegalArgumentException.class, () -> c.reset(1, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> c.reset(1, Duration.ofSeconds(Long.MAX_VALUE)));
        state.globals.set("callback", LuaValue.ofFunction(l -> {
            assertThrows(IllegalStateException.class, () -> c.reset(100, null));
            c.checkpoint();
            return 0;
        }));
        run(state, "callback()");
        c.reset(10, null); // Active-call accounting restored after return.
        state.close();
    }

    @Test void ordinaryLuaErrorsAllowReuseButSwallowedAbortDoesNot() {
        LuaState state = new LuaState();
        LuaLibs.openAll(state);
        ExecutionControl control = state.executionControl();
        control.reset(10000, null);
        run(state, "local ok,e=pcall(error,'expected'); assert(not ok)");
        assertFalse(control.isAborted());
        control.reset(1000, null);
        run(state, "assert(1+2==3)");
        state.globals.set("callback", LuaValue.ofFunction(l -> {
            control.cancel();
            try {
                control.checkpoint();
            } catch (ExecutionAbortedError ignored) {
                // Even a callback that swallows one signal cannot clear the latch.
            }
            return 0;
        }));
        assertEquals(ExecutionAbortedError.Reason.CANCELLED,
                assertThrows(ExecutionAbortedError.class,
                        () -> run(state, "pcall(callback)")).reason());
        state.close();
    }

    @Test void deadlineIncludesIdleAndCallbackReturn() throws Exception {
        LuaState state = new LuaState();
        state.executionControl().reset(-1, Duration.ZERO);
        assertEquals(ExecutionAbortedError.Reason.DEADLINE,
                assertThrows(ExecutionAbortedError.class, () -> run(state, "return")).reason());
        state.close();
        LuaState callback = new LuaState();
        callback.globals.set("callback", LuaValue.ofFunction(l -> {
            // Finite blocking callback: time cannot preempt it, but return is checked.
            try { Thread.sleep(40); } catch (InterruptedException e) { throw new AssertionError(e); }
            return 0;
        }));
        callback.executionControl().reset(-1, Duration.ofMillis(10));
        assertEquals(ExecutionAbortedError.Reason.DEADLINE,
                assertThrows(ExecutionAbortedError.class, () -> run(callback, "callback()")).reason());
        callback.close();
        LuaState idle = new LuaState();
        idle.executionControl().reset(-1, Duration.ofMillis(1));
        Thread.sleep(10);
        assertThrows(ExecutionAbortedError.class, idle.executionControl()::checkpoint);
        idle.close();
    }

    @Test void suspendedCoroutineSharesCumulativeBudgetAcrossHostResumes() {
        LuaState state = new LuaState();
        LuaLibs.openAll(state);
        LuaState co = state.newThread();
        co.pushClosure(co.load(("for i=1,3 do coroutine.yield(i) end")
                .getBytes(StandardCharsets.UTF_8), "=co"));
        ExecutionControl control = state.executionControl();
        control.reset(1000, null);
        assertEquals(LuaState.LUA_YIELD, co.resume(null, 0, new int[1]));
        long left = control.remainingInstructions();
        assertTrue(left < 1000);
        co.top--; // Host consumes yielded value before the next resume.
        assertEquals(LuaState.LUA_YIELD, co.resume(null, 0, new int[1]));
        assertTrue(control.remainingInstructions() < left);
        co.top--;
        control.reset(0, null); // Host idle reset applies to pre-existing suspended threads.
        assertEquals(ExecutionAbortedError.Reason.INSTRUCTION_BUDGET,
                assertThrows(ExecutionAbortedError.class,
                        () -> co.resume(null, 0, new int[1])).reason());
        state.close();
    }

    @Test void cancellationBeforeExecutionAndStateIsolation() {
        LuaState cancelled = new LuaState();
        LuaState independent = new LuaState();
        cancelled.executionControl().cancel();
        assertEquals(ExecutionAbortedError.Reason.CANCELLED,
                assertThrows(ExecutionAbortedError.class, () -> run(cancelled, "return")).reason());
        run(independent, "local n=1+2");
        assertFalse(independent.executionControl().isAborted());
        independent.executionControl().reset(1000, null);
        run(independent, "local n=1+2");
        cancelled.close();
        independent.close();
    }

    @Test void abortedCloseDoesNotRunFinalizers() {
        LuaState state = new LuaState();
        LuaLibs.openAll(state);
        int[] calls = {0};
        state.globals.set("mark", LuaValue.ofFunction(l -> { calls[0]++; return 0; }));
        run(state, "obj=setmetatable({}, {__gc=function() mark() end})");
        state.executionControl().cancel();
        state.close();
        state.close();
        assertEquals(0, calls[0]);
    }

    @Test void restrictedFactoryNeverLoadsDangerousLibraries() {
        LuaState minimal = LuaLibs.newRestricted(EnumSet.noneOf(LuaLibs.RestrictedLibrary.class));
        run(minimal, "assert(_VERSION=='Lua 5.3'); assert(string==nil and coroutine==nil)");
        minimal.close();
        LuaState state = LuaLibs.newRestricted(EnumSet.allOf(LuaLibs.RestrictedLibrary.class));
        run(state, "assert(package==nil and require==nil and debug==nil and io==nil and os==nil); "
                + "assert(load==nil and loadfile==nil and dofile==nil and print==nil and collectgarbage==nil); "
                + "assert(('abc'):upper()=='ABC'); assert(table.concat({'a','b'})=='ab'); "
                + "assert(math.floor(2.5)==2 and utf8.len('abc')==3 and bit32.band(7,3)==3); "
                + "local co=coroutine.create(function() return 42 end); "
                + "local ok,n=coroutine.resume(co); assert(ok and n==42)");
        for (String name : new String[] {"package", "debug", "io", "os"}) {
            LuaValue value = state.loadedTable().get(name);
            assertTrue(value == null || value.isNil(), name);
        }
        assertNull(state.registry.get("_PRELOAD"));
        state.close();
        assertThrows(NullPointerException.class, () -> LuaLibs.newRestricted(null));
    }

    @Test void infiniteLoopAdversariesHaveAnExternalHostTimeout() throws Exception {
        java.util.List<String> scenarios = new java.util.ArrayList<>(java.util.List.of(
                "plain", "pcall", "xpcall", "handler", "resume", "wrap", "newthreads",
                "hook", "removehook", "deadline", "cancel"));
        if (NativeBridge.AVAILABLE) {
            scenarios.add("nativepcall");
            scenarios.add("nativecontinuation");
        }
        for (String scenario : scenarios) {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            if (System.getProperty("javalua.nonative") != null) {
                command.add("-Djavalua.nonative=true");
            }
            command.add("-cp");
            command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
            command.add(Probe.class.getName());
            command.add(scenario);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(8, TimeUnit.SECONDS), "host timeout: " + scenario);
                assertEquals(0, process.exitValue(), () -> {
                    try { return scenario + ": " + new String(process.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8); }
                    catch (java.io.IOException e) { return e.toString(); }
                });
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(3, TimeUnit.SECONDS), "could not reap probe");
            }
        }
    }

    /** Child process provides an OS-enforced upper bound even if VM enforcement regresses. */
    public static final class Probe {
        private Probe() { }

        private static void nativeAbortSurvivesBridge(LuaState state, String scenario) throws Exception {
            assertTrue(NativeBridge.AVAILABLE);
            Path dir = java.nio.file.Files.createTempDirectory(Path.of("target"), "resource-control-native-");
            Path source = dir.resolve("probe.c");
            Path library = dir.resolve("probe.so");
            java.nio.file.Files.writeString(source, """
                    #include "lua.h"
                    #include "lauxlib.h"
                    static int protected_call(lua_State *L) {
                        lua_settop(L, 1);
                        lua_pcall(L, 0, 0, 0);
                        return 0;
                    }
                    static int after(lua_State *L, int status, lua_KContext ctx) {
                        (void)status; (void)ctx;
                        lua_pushvalue(L, 1);
                        lua_pcall(L, 0, 0, 0);
                        return 0;
                    }
                    static int start(lua_State *L) { return lua_yieldk(L, 0, 0, after); }
                    int luaopen_limitprobe(lua_State *L) {
                        luaL_Reg f[] = {{"protected", protected_call}, {"start", start}, {NULL, NULL}};
                        luaL_newlib(L, f);
                        return 1;
                    }
                    """, StandardCharsets.UTF_8);
            java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                    "cc", "-shared", "-fPIC", "-I", "native/lua", "-o", library.toString(), source.toString()));
            if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
                command.addAll(1, java.util.List.of("-undefined", "dynamic_lookup"));
            }
            Process compiler = new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                assertTrue(compiler.waitFor(3, TimeUnit.SECONDS), "C probe compile timeout");
                assertEquals(0, compiler.exitValue(), () -> {
                    try { return new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
                    catch (java.io.IOException e) { return e.toString(); }
                });
            } finally {
                compiler.destroyForcibly();
            }
            run(state, "probe=assert(package.loadlib([==[" + library.toAbsolutePath()
                    + "]==], 'luaopen_limitprobe'))()");
            org.junit.jupiter.api.function.Executable invoke;
            if (scenario.equals("nativepcall")) {
                LuaFunction nativeFunction = (LuaFunction) state.globals.get("probe")
                        .asTable().get("protected").o;
                state.pushClosure(state.load("while true do end".getBytes(StandardCharsets.UTF_8), "=native-loop"));
                // Direct invocation verifies the JNI wrapper itself preserves the Error;
                // there is no later VM checkpoint masking a mistaken LuaError conversion.
                invoke = () -> nativeFunction.call(state);
            } else {
                run(state, "co=coroutine.create(function() probe.start(function() while true do end end) end); "
                        + "assert(coroutine.resume(co))");
                LuaState co = (LuaState) state.globals.get("co").o;
                assertNotNull(co.ci.k);
                invoke = () -> co.ci.k.call(co, LuaState.LUA_YIELD, 0);
            }
            state.executionControl().reset(2000, null);
            assertEquals(ExecutionAbortedError.Reason.INSTRUCTION_BUDGET,
                    assertThrows(ExecutionAbortedError.class, invoke).reason());
            state.close();
        }

        public static void main(String[] args) throws Exception {
            LuaState state = new LuaState();
            LuaLibs.openAll(state);
            String scenario = args[0];
            if (scenario.startsWith("native")) {
                nativeAbortSurvivesBridge(state, scenario);
                return;
            }
            String source = switch (scenario) {
                case "pcall" -> "while true do pcall(function() while true do end end) end";
                case "xpcall" -> "while true do xpcall(function() while true do end end,function() return 1 end) end";
                case "handler" -> "xpcall(function() error('x') end,function() while true do end end)";
                case "resume" -> "while true do coroutine.resume(coroutine.create(function() while true do end end)) end";
                case "wrap" -> "while true do pcall(coroutine.wrap(function() while true do end end)) end";
                case "newthreads" -> "while true do coroutine.resume(coroutine.create(function() return 1 end)) end";
                case "hook" -> "debug.sethook(function() while true do end end, '', 1); return 1";
                case "removehook" -> "debug.sethook(); while true do end";
                default -> "while true do end";
            };
            ExecutionControl control = state.executionControl();
            CountDownLatch started = new CountDownLatch(1);
            if (scenario.equals("cancel")) {
                state.globals.set("started", LuaValue.ofFunction(l -> { started.countDown(); return 0; }));
                source = "started(); " + source;
            }
            int base = state.top;
            state.pushClosure(state.load(source.getBytes(StandardCharsets.UTF_8), "=adversary"));
            ExecutionAbortedError.Reason expected = ExecutionAbortedError.Reason.INSTRUCTION_BUDGET;
            Thread canceller = null;
            if (scenario.equals("deadline")) {
                control.reset(-1, Duration.ofMillis(25));
                expected = ExecutionAbortedError.Reason.DEADLINE;
            } else if (scenario.equals("cancel")) {
                canceller = new Thread(() -> {
                    try {
                        if (!started.await(2, TimeUnit.SECONDS)) { throw new AssertionError("not started"); }
                        Thread.sleep(25); // Give the VM time to enter its unbounded loop.
                        control.cancel();
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                });
                canceller.setDaemon(true);
                canceller.start(); // Cancellation works even with controls initially disabled.
                expected = ExecutionAbortedError.Reason.CANCELLED;
            } else {
                control.reset(2000, null);
            }
            ExecutionAbortedError error = assertThrows(ExecutionAbortedError.class, () -> state.pcall(base, 0));
            assertEquals(expected, error.reason());
            assertTrue(control.isAborted());
            if (canceller != null) { canceller.join(1000); assertFalse(canceller.isAlive()); }
            state.close();
        }
    }
}
