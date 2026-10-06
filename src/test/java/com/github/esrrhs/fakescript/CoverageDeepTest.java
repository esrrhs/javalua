package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖率深挖:非JNE算术/比较、struct指针、math-assign错误、调试器组合
 */
public class CoverageDeepTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    @Test
    public void testBracketedArithInAndOr() {
        String script = "func f(a, b)\n" +
                "    var r = 0\n" +
                "    if (a + 1 > 1) && (b * 2 > 0) then\n" +
                "        r = 1\n" +
                "    end\n" +
                "    return r\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(1L, ((Long) fk.run(f, "f", 1, 1)).longValue());
        assertEquals(0L, ((Long) fk.run(f, "f", 0, 0)).longValue());
    }

    @Test
    public void testStructPointerOperations() {
        String script = "struct P\n" +
                "    x\n" +
                "    y\n" +
                "end\n" +
                "func make()\n" +
                "    var p = P()\n" +
                "    p->x = 1\n" +
                "    p->y = 2\n" +
                "    return p\n" +
                "end\n" +
                "func f()\n" +
                "    var p1 = make()\n" +
                "    var p2 = make()\n" +
                "    p2->x = 100\n" +
                "    return p1->x, p1->y, p2->x, p2->y\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals(2L, ((Long) rets[1]).longValue());
        assertEquals(100L, ((Long) rets[2]).longValue());
        assertEquals(2L, ((Long) rets[3]).longValue());
    }



    @Test
    public void testMathAssignOnConstContainer() {
        String script = "const ARR = [1 2]\n" +
                "func f()\n" +
                "    ARR[0] += 1\n" +
                "    return ARR[0]\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("const"), fk.geterror(f));
    }

    @Test
    public void testAllMathAssignOpsValid() {
        String script = "func f()\n" +
                "    var x = 10\n" +
                "    x += 1\n" +
                "    x -= 1\n" +
                "    x *= 1\n" +
                "    x /= 1\n" +
                "    x %= 1\n" +
                "    return x\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(0.0, ((Double) fk.run(f, "f")).doubleValue(), 0.0000001);
    }

    @Test
    public void testPackagehelperJarAndSubdirs(@TempDir Path dir) throws Exception {
        java.io.File jarFile = dir.resolve("test.jar").toFile();
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                new java.io.FileOutputStream(jarFile))) {
            java.io.File classesDir = new java.io.File("target/classes/com/github/esrrhs/fakescript");
            addDirToJar(jos, classesDir, "com/github/esrrhs/fakescript");
        }

        java.net.URLClassLoader jarLoader = new java.net.URLClassLoader(
                new java.net.URL[] { jarFile.toURI().toURL() }, getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(jarLoader);
        try {
            java.util.List<Class<?>> classes = packagehelper.getClasses(f, "com.github.esrrhs.fakescript");
            assertTrue(classes.size() > 20, "jar scan should find many classes: " + classes.size());
        } finally {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        }
    }

    private void addDirToJar(java.util.jar.JarOutputStream jos, java.io.File dir, String prefix) throws Exception {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File cf : files) {
            String entryName = prefix + "/" + cf.getName();
            if (cf.isDirectory()) {
                addDirToJar(jos, cf, entryName);
            } else if (cf.getName().endsWith(".class")) {
                jos.putNextEntry(new java.util.jar.JarEntry(entryName));
                Files.copy(cf.toPath(), jos);
                jos.closeEntry();
            }
        }
    }

    @Test
    public void testDebugRoutineSwitchAndDisa(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("r.fk");
        Files.write(file, ("func inner()\n" +
                "    var x = 42\n" +
                "    return x\n" +
                "end\n" +
                "func f()\n" +
                "    return inner()\n" +
                "end\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(fk.parse(f, file.toString()), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");
        s.execute("b 2");
        s.execute("c");
        assertFalse(s.is_end());

        int rid = fk.getcurroutineid(f);
        String r = s.execute("r " + rid);
        assertNotNull(r);

        String disa = s.execute("disa");
        assertTrue(disa.contains("inner"), disa);

        String p = s.execute("p x");
        assertTrue(p.contains("42"), p);

        s.execute("fin");
        s.execute("c");
        assertTrue(s.is_end());
    }

    @Test
    public void testSwitchOnStructMember() {
        String script = "struct P\n" +
                "    kind\n" +
                "end\n" +
                "func f(k)\n" +
                "    var p = P()\n" +
                "    p->kind = k\n" +
                "    switch p->kind\n" +
                "        case 1 then\n" +
                "            return \"one\"\n" +
                "        default\n" +
                "            return \"other\"\n" +
                "    end\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals("one", fk.run(f, "f", 1));
        assertEquals("other", fk.run(f, "f", 2));
    }

    @Test
    public void testResumeWithNestedCall() {
        String script = "func inner()\n" +
                "    yield 1\n" +
                "    return 99\n" +
                "end\n" +
                "func f()\n" +
                "    var v = inner()\n" +
                "    return v\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        Object[] rets = null;
        int guard = 0;
        while (rets == null && guard < 100) {
            rets = fk.resume(f, "f");
            guard++;
        }
        assertNotNull(rets, fk.geterror(f));
        assertEquals(99L, ((Long) rets[0]).longValue());
    }

    // ==================== for循环OPCODE_FORBEGIN/FORLOOP路径 ====================

    @Test
    public void testForLoopWithBreakAndContinue() {
        // for循环:FORBEGIN/FORLOOP + break/continue的位置回填
        String script = "func f(n)\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < n, i++ then\n" +
                "        if i == 2 then\n" +
                "            continue\n" +
                "        end\n" +
                "        if i == 5 then\n" +
                "            break\n" +
                "        end\n" +
                "        s = s + i\n" +
                "    end\n" +
                "    return s\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        // i=0(s+=0), i=1(s+=1), i=2(continue), i=3(s+=3), i=4(s+=4), i=5(break) => s=0+1+3+4=8
        assertEquals(8L, ((Long) fk.run(f, "f", 10)).longValue());
    }

    @Test
    public void testForLoopContinueOnly() {
        String script = "func f(n)\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < n, i++ then\n" +
                "        if i % 2 == 0 then\n" +
                "            continue\n" +
                "        end\n" +
                "        s = s + i\n" +
                "    end\n" +
                "    return s\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        // 奇数和:1+3+5+7+9=25
        assertEquals(25L, ((Long) fk.run(f, "f", 10)).longValue());
    }

    @Test
    public void testForLoopZeroIterations() {
        String script = "func f()\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < 0, i++ then\n" +
                "        s = s + 1\n" +
                "    end\n" +
                "    return s\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(0L, ((Long) fk.run(f, "f")).longValue());
    }

    // ==================== elseif链的JNE占位回填 ====================

    @Test
    public void testElseifChainMultiple() {
        // elseif链:每个elseif都会生成JNE+占位+回填
        String script = "func f(x)\n" +
                "    if x == 1 then\n" +
                "        return \"a\"\n" +
                "    elseif x == 2 then\n" +
                "        return \"b\"\n" +
                "    elseif x == 3 then\n" +
                "        return \"c\"\n" +
                "    elseif x == 4 then\n" +
                "        return \"d\"\n" +
                "    elseif x == 5 then\n" +
                "        return \"e\"\n" +
                "    else\n" +
                "        return \"z\"\n" +
                "    end\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        for (int i = 1; i <= 5; i++) {
            assertEquals(String.valueOf((char) ('a' + i - 1)), fk.run(f, "f", i));
        }
        assertEquals("z", fk.run(f, "f", 99));
    }

    // ==================== debugger debug() System.in集成 ====================

    @Test
    public void testDebugSystemInIntegration() throws Exception {
        String script = "func f()\n    return 42\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // 喂调试命令流:n(单步)→ c(continue) → EOF
        String input = "n\nc\n";
        java.io.InputStream origIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            Object ret = fk.debugrun(f, "f");
            // debugrun走debug()循环,完成后返回值
            assertNotNull(ret);
        } finally {
            System.setIn(origIn);
        }
    }

    // ==================== JSON parse_string深层转义 ====================

    @Test
    public void testJsonParseUnicodeAndSlashes() throws Exception {
        // \\uXXXX合法
        variant v = json.parse("\"\\u4e2d\"");
        assertEquals("中", v.get_string());

        // \\/转义
        variant slash = json.parse("\"a\\/b\"");
        assertEquals("a/b", slash.get_string());

        // \\b \\f转义
        variant bf = json.parse("\"\\b\\f\"");
        assertEquals("\b\f", bf.get_string());

        // 坏hex
        try {
            json.parse("\"\\uZZZZ\"");
            fail("should reject bad unicode");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("ZZZZ") || e.getMessage().contains("radix")
                    || e.getMessage().contains("escape"), e.getMessage());
        }

        // 未闭合
        try {
            json.parse("\"unclosed\\\n");
            fail("should reject unclosed string");
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
    }

    // ==================== variant.equals更多分支 ====================

    @Test
    public void testVariantEqualsEdgeBranches() throws Exception {
        // STRING vs NIL
        variant s = new variant();
        s.set_string("");
        variant n = new variant();
        n.set_nil();
        assertFalse(s.equals(n));

        // UUID vs NIL
        variant u = new variant();
        u.set_uuid(1L);
        assertFalse(u.equals(n));

        // ARRAY vs NIL
        variant a = new variant();
        a.set_array(new variant_array());
        assertFalse(a.equals(n));

        // MAP vs NIL
        variant m = new variant();
        m.set_map(new variant_map());
        assertFalse(m.equals(n));

        // 同引用
        assertTrue(a.equals(a));

        // POINTER vs POINTER(null)等于NIL
        variant pnull = new variant();
        pnull.set_pointer(null);
        assertTrue(pnull.equals(n));

        // 不同类
        assertFalse(a.equals("not a variant"));
    }

    // ==================== fk.resume / runps 边界 ====================

    @Test
    public void testResumeAfterFinish() {
        String script = "func f()\n    return 1\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // 第一次resume完成
        Object[] rets = fk.resume(f, "f");
        assertNotNull(rets);

        // 结束后再resume:重新启动
        Object[] rets2 = fk.resume(f, "f");
        assertNotNull(rets2, "should restart after completion");
    }

    @Test
    public void testRunpsEmptyReturn() {
        String script = "func f()\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(1, rets.length);
        assertNull(rets[0]);
    }

    // ==================== json.write_value 更多分支 ====================

    @Test
    public void testJsonWriteRealEdgeCases() throws Exception {
        // REAL整数值输出为整数
        variant v = new variant();
        v.set_real(3.0);
        assertEquals("3", json.write(v));

        // NaN输出null
        variant nan = new variant();
        nan.set_real(Double.NaN);
        assertEquals("null", json.write(nan));

        // Infinity输出null
        variant inf = new variant();
        inf.set_real(Double.POSITIVE_INFINITY);
        assertEquals("null", json.write(inf));

        // 大数不转整数字面量
        variant big = new variant();
        big.set_real(1.0E15);
        assertEquals("1.0E15", json.write(big));
    }

    @Test
    public void testJsonWriteStringControlChars() throws Exception {
        variant v = new variant();
        v.set_string("a\b\f\n\r\t");
        String j = json.write(v);
        assertTrue(j.contains("\\b"), j);
        assertTrue(j.contains("\\f"), j);
        assertTrue(j.contains("\\n"), j);
        assertTrue(j.contains("\\r"), j);
        assertTrue(j.contains("\\t"), j);
    }

    @Test
    public void testJsonParseObjectAndArray() throws Exception {
        variant v = json.parse("{\"arr\":[{\"x\":1},{\"x\":2}],\"empty\":{}}");
        assertEquals(2, v.get_map().size());

        variant arr = json.parse("[1,\"two\",null,true,3.14]");
        assertEquals(5, arr.get_array().size());

        variant emptyObj = json.parse("{}");
        assertEquals(0, emptyObj.get_map().size());
        variant emptyArr = json.parse("[]");
        assertEquals(0, emptyArr.get_array().size());

        variant neg = json.parse("-42");
        assertEquals(-42L, neg.get_int());
        variant exp = json.parse("1e2");
        assertEquals(100.0, exp.get_real(), 0.0000001);
    }

    @Test
    public void testJsonWriteNested() throws Exception {
        fake f2 = fk.newfake(new fkconfig());
        fk.openbaselib(f2);
        String script = "func f()\n" +
                "    var inner = array()\n" +
                "    push(inner, 1)\n" +
                "    push(inner, \"two\")\n" +
                "    var m = map()\n" +
                "    m[\"data\"] = inner\n" +
                "    m[\"flag\"] = true\n" +
                "    return tojson(m)\n" +
                "end\n";
        assertTrue(fk.parsestr(f2, script), fk.geterror(f2));
        String j = (String) fk.run(f2, "f");
        assertTrue(j.contains("\"data\""), j);
        assertTrue(j.contains("\"two\""), j);
        variant back = json.parse(j);
        assertNotNull(back);
    }

    @Test
    public void testAllStmtTypesCompiled() {
        String script = "struct S\n" +
                "    x\n" +
                "end\n" +
                "const PI = 3\n" +
                "func helper(a)\n" +
                "    return a\n" +
                "end\n" +
                "func main()\n" +
                "    var x = PI\n" +
                "    var s = S()\n" +
                "    s->x = x\n" +
                "    if x > 0 then\n" +
                "        x = x + 1\n" +
                "    elseif x == 0 then\n" +
                "        x = x + 2\n" +
                "    else\n" +
                "        x = x + 3\n" +
                "    end\n" +
                "    var i = 0\n" +
                "    while i < 3 then\n" +
                "        i += 1\n" +
                "    end\n" +
                "    for var j = 0, j < 2, j++ then\n" +
                "        if j == 1 then\n" +
                "            continue\n" +
                "        end\n" +
                "        break\n" +
                "    end\n" +
                "    switch x\n" +
                "        case 1 then\n" +
                "            x = 10\n" +
                "        default\n" +
                "            x = 20\n" +
                "    end\n" +
                "    var arr = array()\n" +
                "    push(arr, \"a\")\n" +
                "    var m2 = map()\n" +
                "    m2[\"k\"] = 1\n" +
                "    sleep 0\n" +
                "    yield 1\n" +
                "    return x, s->x, size(arr)\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "main");
        assertEquals(3, rets.length, fk.geterror(f));
    }
}
