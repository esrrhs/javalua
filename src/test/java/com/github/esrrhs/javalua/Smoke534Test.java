package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class Smoke534Test {
    @Test
    void dumpUndumpRoundtrip() {
        Proto p = Parser.parse("return 1+2*3", "=smoke");
        byte[] ours = Dump.dumpBytes(p, false);
        assertTrue(ours.length > 12);
        assertEquals(0x1b, ours[0] & 0xff);
        assertEquals('L', ours[1] & 0xff);
        assertEquals(0x53, ours[4] & 0xff); // version
        // sizeof int / size_t after LUAC_DATA
        // sig(4)+ver(1)+fmt(1)+data(6)=12; then int=4, size_t=8
        assertEquals(4, ours[12] & 0xff);
        assertEquals(8, ours[13] & 0xff);

        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        Zio z = new Zio(ours);
        z.getc();
        LClosure cl = Undump.undump(L, z, "=smoke".getBytes());
        assertEquals(p.sizecode, cl.p.sizecode);
        assertEquals(p.sizek, cl.p.sizek);
    }

    @Test
    void basicDiffs() {
        Assumptions.assumeTrue(ReferenceLua.available(), ReferenceLua.describe());
        Diff.assertMatches("print(1+2*3)");
        Diff.assertMatches("print(_VERSION)");
        Diff.assertMatches("local s=0; for i=1,5 do s=s+i end; print(s)");
        Diff.assertMatches("local t={}; for i=1,3 do t[i]=i*i end; print(t[1],t[2],t[3])");
        Diff.assertMatches("print(bit32.band(0xFF, 0x0F), bit32.bxor(1,3), bit32.bnot(0))");
        Diff.assertMatches("local s=0; for k,v in pairs({a=10,b=20}) do s=s+v end; print(s)");
        Diff.assertMatches("local function f(n) if n<=1 then return 1 end; return n*f(n-1) end; print(f(5))");
        Diff.assertMatches("print(string.format('%d', 42))");
        Diff.assertMatches("local function counter() local n = 0 return function() n = n + 1 return n end end local c = counter() print(c(), c(), c())");
        Diff.assertMatches("local function f(...) return ... end print(f(1, 2, 3))");
        Diff.assertMatches("local function f(...) return select('#', ...) end print(f(), f(1), f(1,2,3))");
    }

    @Test
    void dumpMatchesLuacHeaderAndLoad() throws Exception {
        Assumptions.assumeTrue(ReferenceLua.available(), ReferenceLua.describe());
        String src = "local a=1; local b='hi'; return a,b,a+1";
        Proto p = Parser.parse(src, "@t.lua");
        byte[] ours = Dump.dumpBytes(p, false);
        // stock luac
        java.io.File tmp = java.io.File.createTempFile("javalua", ".lua");
        java.io.File out = java.io.File.createTempFile("javalua", ".out");
        java.nio.file.Files.write(tmp.toPath(), src.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String luac = ReferenceLua.find().replaceAll("/lua$", "/luac");
        Process pr = new ProcessBuilder(luac, "-o", out.getAbsolutePath(), tmp.getAbsolutePath())
                .redirectErrorStream(true).start();
        pr.waitFor();
        byte[] stock = java.nio.file.Files.readAllBytes(out.toPath());
        // headers must match through LUAC_NUM
        int hdr = 4+1+1+6+5+8+8; // sig ver fmt data sizes int num
        assertArrayEquals(
                java.util.Arrays.copyOf(stock, hdr),
                java.util.Arrays.copyOf(ours, hdr),
                "binary header must match stock luac 5.3.4");
        // Our undump must load stock bytecode
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        Zio z = new Zio(stock);
        z.getc();
        LClosure cl = Undump.undump(L, z, "@t.lua".getBytes());
        assertTrue(cl.p.sizecode > 0);
        // stock lua must load our dump
        java.nio.file.Files.write(out.toPath(), ours);
        Process pr2 = new ProcessBuilder(ReferenceLua.find(), out.getAbsolutePath())
                .redirectErrorStream(true).start();
        String o = new String(pr2.getInputStream().readAllBytes());
        assertEquals(0, pr2.waitFor(), o);
        tmp.delete();
        out.delete();
    }
}
