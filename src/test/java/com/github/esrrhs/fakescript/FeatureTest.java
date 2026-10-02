package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * README中宣称的语言特性与宿主API的覆盖测试
 */
public class FeatureTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        fkconfig config = new fkconfig();
        f = fk.newfake(config);
        fk.openbaselib(f);
        assertNotNull(f);
    }

    private Object runScript(String script, String func, Object... args) {
        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));
        return fk.run(f, func, args);
    }

    @Test
    public void testStructDefinitionAndAccess() {
        String script =
                "struct User\n" +
                "    id\n" +
                "    name\n" +
                "end\n" +
                "func f()\n" +
                "    var u = User()\n" +
                "    u->id = 1001\n" +
                "    u->name = \"Alice\"\n" +
                "    return u->name\n" +
                "end\n";

        assertEquals("Alice", runScript(script, "f"));
    }

    @Test
    public void testIncludeSharedFunction(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("common.fk"),
                "func common_add(a, b)\n    return a + b\nend\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("main.fk"),
                "include \"common.fk\"\nfunc f()\n    return common_add(1, 2)\nend\n"
                        .getBytes(StandardCharsets.UTF_8));

        boolean ok = fk.parse(f, dir.resolve("main.fk").toString());
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertEquals(3.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @Test
    public void testSwitchStatement() {
        String script =
                "func f(x)\n" +
                "    switch x\n" +
                "        case 1 then\n" +
                "            return \"one\"\n" +
                "        case 2 then\n" +
                "            return \"two\"\n" +
                "        default\n" +
                "            return \"other\"\n" +
                "    end\n" +
                "end\n";

        assertEquals("one", runScript(script, "f", 1));
        assertEquals("two", runScript(script, "f", 2));
        assertEquals("other", runScript(script, "f", 3));
    }

    @Test
    public void testNestedContainers() {
        String script =
                "func f()\n" +
                "    var arr = array()\n" +
                "    arr[0] = 100\n" +
                "    arr[1] = 200\n" +
                "    var m = map()\n" +
                "    m[1] = arr\n" +
                "    var outer = map()\n" +
                "    outer[\"inner\"] = m\n" +
                "    var got1 = outer[\"inner\"]\n" +
                "    var got2 = got1[1]\n" +
                "    return got2[0]\n" +
                "end\n";

        Object ret = runScript(script, "f");
        assertEquals(100.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @Test
    public void testConstValue() {
        String script =
                "const MAX_COUNT = 100\n" +
                "func f()\n" +
                "    return MAX_COUNT\n" +
                "end\n";

        Object ret = runScript(script, "f");
        assertEquals(100.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @Test
    public void testForLoop() {
        String script =
                "func f(n)\n" +
                "    var sum = 0\n" +
                "    for var i = 0, i < n, i++ then\n" +
                "        sum = sum + i\n" +
                "    end\n" +
                "    return sum\n" +
                "end\n";

        Object ret = runScript(script, "f", 10);
        assertEquals(45.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @Test
    public void testWhileLoop() {
        String script =
                "func f(n)\n" +
                "    var i = 0\n" +
                "    while i < n then\n" +
                "        i = i + 2\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        Object ret = runScript(script, "f", 7);
        assertEquals(8.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @Test
    public void testIfElseifElse() {
        String script =
                "func f(x)\n" +
                "    if x < 0 then\n" +
                "        return \"neg\"\n" +
                "    elseif x == 0 then\n" +
                "        return \"zero\"\n" +
                "    else\n" +
                "        return \"pos\"\n" +
                "    end\n" +
                "end\n";

        assertEquals("neg", runScript(script, "f", -1));
        assertEquals("zero", runScript(script, "f", 0));
        assertEquals("pos", runScript(script, "f", 5));
    }

    @Test
    public void testStringAndTypeBuiltins() {
        String script =
                "func f()\n" +
                "    var n = tonumber(\"42\")\n" +
                "    var s = tostring(42)\n" +
                "    var t = typeof(n)\n" +
                "    var l = size(\"hello\")\n" +
                "    var e = range(\"hello\", 1)\n" +
                "    return n, s, t, l, e\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(42.0, ((Double) rets[0]).doubleValue(), 0.0001);
        assertEquals("42", rets[1]);
        assertEquals("REAL", rets[2]);
        assertEquals(5.0, ((Double) rets[3]).doubleValue(), 0.0001);
        assertEquals("e", rets[4]);
    }

    @Test
    public void testProfiler() {
        String script =
                "func work()\n" +
                "    var i = 0\n" +
                "    for var j = 0, j < 1000, j++ then\n" +
                "        i = i + j\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        fk.openprofile(f);
        fk.run(f, "work");
        fk.closeprofile(f);

        String dump = fk.dumpprofile(f);
        assertNotNull(dump);
        assertTrue(dump.contains("work"), dump);
    }

    @Test
    public void testHotReload() {
        String v1 = "func f()\n    return 1\nend\n";
        String v2 = "func f()\n    return 2\nend\n";

        assertEquals(1.0, ((Double) runScript(v1, "f")).doubleValue(), 0.0001);
        assertEquals(2.0, ((Double) runScript(v2, "f")).doubleValue(), 0.0001);
    }

    @Test
    public void testParseErrorReportsError() {
        boolean ok = fk.parsestr(f, "func f(\n    return 1\nend\n");
        assertFalse(ok);
        assertTrue(fk.error(f));
        assertFalse(fk.geterror(f).isEmpty());
    }

    @Test
    public void testRuntimeErrorReportsError() {
        // 对非容器值做下标访问,运行时报错
        String script =
                "func f()\n" +
                "    var x = 5\n" +
                "    return x[0]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNull(ret);
        assertTrue(fk.error(f));
        assertFalse(fk.geterror(f).isEmpty());
    }

    @Test
    public void testCustomBindName() {
        String script =
                "func f()\n" +
                "    return FeatureTest.myadd(1, 2)\n" +
                "end\n";

        fk.regclass(f, FeatureTest.class);

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNotNull(ret, fk.geterror(f));
        assertEquals(3.0, ((Double) ret).doubleValue(), 0.0001);
    }

    @fakescript(name = "myadd")
    public static int myadd(int a, int b) {
        return a + b;
    }
}
