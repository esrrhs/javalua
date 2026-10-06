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
