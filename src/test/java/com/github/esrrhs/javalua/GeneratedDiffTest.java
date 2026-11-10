package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Typed, deep-execution complement to FuzzTest's mostly ill-typed random grammar.
 * No Diff normalization: output bytes, protected-call status and error objects all count.
 * Every seed exercises every family; bounded loops and an instruction budget prevent hangs.
 */
class GeneratedDiffTest {
    static final int CASES = Integer.getInteger("diff.cases", 64);
    static final long START = Long.getLong("diff.seed", 534000L);
    private static final Path ROOT = Path.of("target/generated-diff",
            System.getProperty("javalua.nonative") == null ? "native" : "java");

    @BeforeAll
    static void oracle() throws Exception {
        if (Boolean.getBoolean("javalua.requireReference")) {
            assertNotNull(ReferenceLua.find(), "exact Lua 5.3.4 is required");
            assertNotNull(ReferenceLua.findLuac(), "exact luac 5.3.4 is required");
        } else {
            assumeTrue(ReferenceLua.available() && ReferenceLua.findLuac() != null,
                    "build Lua/luac 5.3.4; see tools/prepare_reference.sh");
        }
        assertTrue(CASES > 0 && CASES <= 100000, "diff.cases must be 1..100000");
        Files.createDirectories(ROOT);
        Files.writeString(ROOT.resolve("campaign.txt"),
                "seed=" + START + "\ncases=" + CASES + "\n");
    }

    @TestFactory
    Stream<DynamicTest> generatedPrograms() {
        return LongStream.range(START, START + CASES).mapToObj(seed ->
                DynamicTest.dynamicTest("seed=" + seed, () -> check(seed)));
    }

    private static void check(long seed) throws Exception {
        Path dir = ROOT.resolve("seed-" + seed);
        Files.createDirectories(dir);
        Path source = dir.resolve("source.lua");
        String src = program(seed);
        // Written BEFORE executing, so a killed JVM still leaves the failing input.
        Files.writeString(source, src);
        Path stock = dir.resolve("stock.luac");
        Path ours = dir.resolve("java.luac");
        Result compiled = process(dir, "compile", ReferenceLua.findLuac(),
                "-o", stock.toString(), source.toString());
        assertEquals(0, compiled.exit, "generator must compile: " + dir + "\n" + compiled.output);
        Proto proto = Parser.parse(src.getBytes(StandardCharsets.UTF_8),
                ("@" + source).getBytes(StandardCharsets.UTF_8), new StringPool());
        Files.write(ours, Dump.dumpBytes(proto, false));
        // Compare the entire serialized prototype, including instructions/constants/upvalues.
        // Both debug-preserving and stripped cross-loads; no address/source-name masking.
        for (boolean strip : new boolean[] {false, true}) {
            if (strip) {
                compiled = process(dir, "compile-strip", ReferenceLua.findLuac(),
                        "-s", "-o", stock.toString(), source.toString());
                assertEquals(0, compiled.exit, compiled.output);
                Files.write(ours, Dump.dumpBytes(proto, true));
            }
            assertArrayEquals(Files.readAllBytes(stock), Files.readAllBytes(ours),
                    "bytecode identity (strip=" + strip + "): " + dir);
            String suffix = strip ? "-strip" : "";
            String runner = runner(source);
            Result expected = reference(dir, "source" + suffix, runner);
            assertEquals(0, expected.exit, expected.output);
            assertTrue(expected.output.contains("complete\t" + seed + "\n"),
                    "not all semantic families ran: " + expected.output);
            assertTrue(expected.output.contains("status\tfalse\nlua-status\t2\nerror\tstring\tterminal-" + seed),
                    "unprotected error/status branch did not execute: " + expected.output);
            assertResult(expected, java(dir, "java-source" + suffix, runner), dir);
            assertResult(expected, java(dir, "java-stock" + suffix, runner(stock)), dir);
            assertResult(expected, reference(dir, "stock-java" + suffix, runner(ours)), dir);
        }
        // Retain failures (including chunks/output). Successful sources are reproducible by seed.
        // Keep them too: a compact CI corpus is useful evidence of actual generated coverage.
    }

    private static void assertResult(Result want, Result got, Path dir) {
        assertEquals(want.exit, got.exit, "host exit: " + dir);
        assertEquals(want.output, got.output, "literal output/status/error: " + dir);
    }

    private static String quote(Path path) {
        return "'" + path.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String runner(Path path) {
        return """
                local ticks = 0
                debug.sethook(function() ticks=ticks+1; if ticks>1000 then error('instruction budget',0) end end, '', 1000)
                local f,err=loadfile(%s, 'bt')
                assert(f,err)
                local ok,e=pcall(f)
                debug.sethook()
                print('status',ok)
                print('lua-status',ok and 0 or 2)
                if not ok then print('error',type(e),e) end
                """.formatted(quote(path));
    }

    private record Result(int exit, String output) { }

    private static Result reference(Path dir, String label, String runner) throws Exception {
        Path file = dir.resolve(label + "-runner.lua");
        Files.writeString(file, runner);
        return process(dir, label, ReferenceLua.find(), file.toString());
    }

    private static Result process(Path dir, String label, String... args) throws Exception {
        Path output = dir.resolve(label + ".out");
        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
                .redirectOutput(output.toFile());
        // Do not inherit a developer's Lua startup script or module path.
        pb.environment().keySet().removeIf(k -> k.startsWith("LUA_"));
        Process p = pb.start();
        try {
            assertTrue(p.waitFor(10, TimeUnit.SECONDS), "oracle timeout: " + dir + "/" + label);
            assertTrue(Files.size(output) <= 1024 * 1024, "output budget exceeded: " + output);
            return new Result(p.exitValue(), Files.readString(output, StandardCharsets.ISO_8859_1));
        } finally {
            if (p.isAlive()) p.destroyForcibly().waitFor();
        }
    }

    private static Result java(Path dir, String label, String runner) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (NativeBridge.AVAILABLE) NativeBridge.cSetlocale(0, new byte[] {'C'});
        LuaState state = new LuaState();
        try (PrintStream ps = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            LuaLibs.openAll(state);
            state.out = ps;
            state.err = ps;
            int base = state.top;
            state.pushClosure(state.load(runner.getBytes(StandardCharsets.UTF_8), "=diff-runner"));
            LuaValue err = state.pcall(base, 0);
            assertNull(err, "runner failed: " + dir + "/" + label + ": " + err);
        } finally {
            state.close();
            Files.write(dir.resolve(label + ".out"), output.toByteArray());
        }
        return new Result(0, output.toString(StandardCharsets.ISO_8859_1));
    }

    /** Each typed expression has observable intermediates, so wrong values cannot cancel out. */
    private static String expr(Random r, int depth) {
        if (depth == 0) {
            String[] atoms = {"a", "b", "math.maxinteger", "math.mininteger", "0", "-1",
                    Integer.toString(r.nextInt(129) - 64)};
            return atoms[r.nextInt(atoms.length)];
        }
        String[] ops = {"+", "-", "*", "&", "|", "~", "<<", ">>"};
        String op = ops[r.nextInt(ops.length)];
        return "(" + expr(r, depth - 1) + " " + op + " "
                + (op.contains("<") || op.contains(">") ? r.nextInt(145) - 72 : expr(r, depth - 1)) + ")";
    }

    static String program(long seed) {
        Random r = new Random(seed);
        StringBuilder s = new StringBuilder("local a,b=" + (r.nextInt(101)-50) + "," + (1+r.nextInt(31)) + "\n");
        for (int i=0; i<12; i++) s.append("print('arith',").append(expr(r, 1+r.nextInt(3))).append(")\n");
        s.append("print('divide',a//b,a%b,a/b,math.type(a+b),math.type(a+0.0))\n");
        s.append("local t={}\n");
        for (int i=0; i<20; i++) {
            int key = r.nextInt(18)-4;
            s.append("t[").append(key).append("]=").append(r.nextInt(4)==0 ? "nil" : Integer.toString(r.nextInt(91))).append("\n");
            s.append("print('length',#t)\n");
        }
        s.append("""
                for i=-4,14 do print('slot',i,rawget(t,i)) end
                t[2.0]=99; print('coerce',t[2]); t[2]=nil
                local n,sum=0,0; for k,v in pairs(t) do n=n+1; sum=sum+k+v end
                print('contents',n,sum)
                local p=table.pack(1,nil,3,nil); print('pack',p.n,table.unpack(p,1,p.n))
                local closed={}
                for i=1,5 do local v=i+a; closed[i]=function(d) v=v+d; return v end end
                for i=1,5 do print('upvalue',closed[i](b),closed[i](-1)) end
                local x=0
                ::again:: x=x+1
                do local v=x; if x<4 then goto again end; print('goto',v) end
                repeat x=x-1 until x==0
                while x<3 do x=x+1; if x==2 then break end end
                print('control',x, a<b and 'less' or 'other')
                local function multi(...) return select('#',...),... end
                local function tail(...) return multi(...) end
                print('tail',tail(a,nil,b)); print('truncate',(tail(a,nil,b)))
                local target={}
                local proxy=setmetatable({}, {
                  __index=function(_,k) return target[k] or b end,
                  __newindex=function(_,k,v) target[k]=v+a end,
                  __len=function() return b end,
                  __add=function(_,v) return a+v end,
                  __call=function(_,...) return multi(...) end})
                proxy.key=b; print('meta',proxy.key,proxy.missing,#proxy,proxy+b,proxy(a,nil,b))
                local function protected(f) local ok,e=pcall(f); print('protected',ok,type(e),e) end
                protected(function() error('raw-error',0) end)
                protected(assert(load('return 1//0','=arithmetic-error')))
                print('handler',xpcall(function() error('inner',0) end,function(e) return 'H:'..e end))
                local co=coroutine.create(function()
                  local obj=setmetatable({}, {__add=function(_,v) local z=coroutine.yield('add',v); return z+a end})
                  local v=obj+b
                  local ok,w=pcall(function() return coroutine.yield('protected',v) end)
                  return ok,w,v
                end)
                print('co',coroutine.resume(co)); print('co',coroutine.resume(co,b)); print('co',coroutine.resume(co,a))
                print('co-dead',coroutine.status(co),coroutine.resume(co))
                local blocked=coroutine.create(assert(load([[local obj=setmetatable({}, {__tostring=function() coroutine.yield('forbidden'); return 'x' end}); print(obj)]], '=boundary')))
                print('noyield',coroutine.resume(blocked))
                local f,e=load('local = 1','=syntax','t'); print('syntax',f==nil,e)
                """);
        return s + "print('complete'," + seed + ")\nerror('terminal-" + seed + "',0)\n";
    }
}
