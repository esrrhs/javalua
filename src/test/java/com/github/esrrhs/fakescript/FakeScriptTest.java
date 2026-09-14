package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FakeScriptTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        fkconfig config = new fkconfig();
        f = fk.newfake(config);
        fk.openbaselib(f);
        assertNotNull(f);
    }

    @fakescript
    public static int add(int a, int b) {
        return a + b;
    }

    @fakescript
    public static String concat(String a, String b) {
        return a + b;
    }

    @Test
    public void testVersion() {
        assertEquals("1.0.14", fk.version);
    }

    @Test
    public void testBasicArithmeticAndReturn() throws Exception {
        String script = 
                "func calc(a, b)\n" +
                "    return a + b * 2\n" +
                "end\n";
        
        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));
        assertFalse(fk.error(f));

        Object ret = fk.run(f, "calc", 3, 4);
        assertNotNull(ret);
        assertEquals(11.0, ((Double) ret).doubleValue());
    }

    @Test
    public void testArrayAndMap() throws Exception {
        String script =
                "func test_container()\n" +
                "    var arr = array()\n" +
                "    arr[0] = 100\n" +
                "    arr[1] = 200\n" +
                "    var m = map()\n" +
                "    m[\"sum\"] = arr[0] + arr[1]\n" +
                "    return m[\"sum\"]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));
        assertFalse(fk.error(f));

        Object ret = fk.run(f, "test_container");
        assertNotNull(ret, fk.geterror(f));
        assertEquals(300.0, ((Double) ret).doubleValue());
    }

    @Test
    public void testJavaFunctionBinding() throws Exception {
        fk.regclass(f, FakeScriptTest.class);

        String script =
                "func call_java()\n" +
                "    var num = FakeScriptTest.add(15, 25)\n" +
                "    var text = FakeScriptTest.concat(\"hello \", \"fakescript\")\n" +
                "    return num\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));
        assertFalse(fk.error(f));

        Object ret = fk.run(f, "call_java");
        assertNotNull(ret, fk.geterror(f));
        assertEquals(40.0, ((Double) ret).doubleValue());
    }

    @Test
    public void testCoroutineFakeExecution() throws Exception {
        String script =
                "func worker(step)\n" +
                "    var g = _G()\n" +
                "    g[\"count\"] = g[\"count\"] + step\n" +
                "end\n" +
                "func main()\n" +
                "    var g = _G()\n" +
                "    g[\"count\"] = 0\n" +
                "    fake worker(10)\n" +
                "    fake worker(20)\n" +
                "    sleep 10\n" +
                "    return g[\"count\"]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));
        assertFalse(fk.error(f));

        Object ret = fk.run(f, "main");
        assertNotNull(ret, fk.geterror(f));
        assertEquals(30.0, ((Double) ret).doubleValue());
    }
}
