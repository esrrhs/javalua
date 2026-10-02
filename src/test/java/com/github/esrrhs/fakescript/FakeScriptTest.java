package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    // 供testFkStop使用:脚本执行中调用,触发停止
    private static fake stopTarget;

    @fakescript
    public static void stopNow() {
        fk.stop(stopTarget);
    }

    @fakescript
    public static int toInt(int a) {
        return a;
    }

    @fakescript
    public static boolean toBool(boolean b) {
        return b;
    }

    @fakescript
    public static long toLong(long a) {
        return a;
    }

    @Test
    public void testVersion() {
        assertNotNull(fk.version);
        assertFalse(fk.version.isEmpty());
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

    @Test
    public void testPrintWithoutCallback() throws Exception {
        String script =
                "func hello()\n" +
                "    print(\"hello\", \" world\")\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        assertDoesNotThrow(() -> fk.run(f, "hello"));
        assertFalse(fk.error(f), fk.geterror(f));
    }

    @Test
    public void testPrintWithCallback() throws Exception {
        final StringBuilder out = new StringBuilder();
        fk.set_callback(f, new callback() {
            @Override
            public void on_error(fake ff, String file, int lineno, String func, String str) {
            }

            @Override
            public void on_print(fake ff, String str) {
                out.append(str);
            }
        });

        String script =
                "func hello()\n" +
                "    print(\"hello\", \" \", \"callback\")\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        fk.run(f, "hello");
        assertEquals("hello callback", out.toString());
    }

    @Test
    public void testUuidLiteralAndReturn() throws Exception {
        String script =
                "func f()\n" +
                "    return 123456789012345u\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNotNull(ret, fk.geterror(f));
        assertEquals(123456789012345L, ((Long) ret).longValue());
    }

    @Test
    public void testUuidVariantAccessor() throws Exception {
        variant uv = new variant();
        uv.set_uuid(42L);
        assertEquals(42L, uv.get_uuid());

        variant nv = new variant();
        nv.set_nil();
        assertEquals(0L, nv.get_uuid());

        variant sv = new variant();
        sv.set_string("not a uuid");
        assertThrows(Exception.class, sv::get_uuid);
    }

    @Test
    public void testIncludeCycleDetection(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("a.fk"),
                "include \"b.fk\"\nfunc fa()\nend\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("b.fk"),
                "include \"a.fk\"\nfunc fb()\nend\n".getBytes(StandardCharsets.UTF_8));

        boolean ok = fk.parse(f, dir.resolve("a.fk").toString());
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("already parsing"), fk.geterror(f));
    }

    @Test
    public void testParseFileWithOddLength(@TempDir Path dir) throws Exception {
        // 29字节,不是10的倍数,旧的分块读取会在末尾混入'\0'
        String content = "func f()\n    return \"OK\"\nend\n";
        Files.write(dir.resolve("odd.fk"), content.getBytes(StandardCharsets.UTF_8));

        boolean ok = fk.parse(f, dir.resolve("odd.fk").toString());
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNotNull(ret, fk.geterror(f));
        assertEquals("OK", ret);
    }

    @Test
    public void testFormatWithMultibyteChars() throws Exception {
        String script =
                "func f()\n" +
                "    return format(\"第$个测试\", 3)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNotNull(ret, fk.geterror(f));
        assertEquals("第3个测试", ret);
    }

    @Test
    public void testFormatEscapingAndPlaceholders() throws Exception {
        String script =
                "func f()\n" +
                "    return format(\"$$a$\", 1)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNotNull(ret, fk.geterror(f));
        assertEquals("$a1", ret);
    }

    @Test
    public void testRunmultiMultipleReturns() throws Exception {
        String script =
                "func f()\n" +
                "    return 1, \"two\", 3.5\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(3, rets.length);
        assertEquals(1.0, ((Double) rets[0]).doubleValue(), 0.0001);
        assertEquals("two", rets[1]);
        assertEquals(3.5, ((Double) rets[2]).doubleValue(), 0.0001);
    }

    @Test
    public void testRunFirstOfMultipleReturns() throws Exception {
        String script =
                "func f()\n" +
                "    return \"first\", \"second\"\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        assertEquals("first", fk.run(f, "f"));
    }

    @Test
    public void testMaxRunCmdNumStopsDeadLoop() throws Exception {
        fkconfig config = new fkconfig();
        config.max_run_cmd_num = 100;
        fake ff = fk.newfake(config);
        fk.openbaselib(ff);

        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while true then\n" +
                "        i = i + 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        boolean ok = fk.parsestr(ff, script);
        assertTrue(ok, fk.geterror(ff));

        Object ret = fk.run(ff, "f");
        assertNull(ret);
        assertTrue(fk.error(ff));
        assertTrue(fk.geterror(ff).contains("max_run_cmd_num"), fk.geterror(ff));
    }

    @Test
    public void testRunTimeoutStopsDeadLoop() throws Exception {
        fkconfig config = new fkconfig();
        config.run_timeout_ms = 200;
        fake ff = fk.newfake(config);
        fk.openbaselib(ff);

        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while true then\n" +
                "        i = i + 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        boolean ok = fk.parsestr(ff, script);
        assertTrue(ok, fk.geterror(ff));

        Object ret = fk.run(ff, "f");
        assertNull(ret);
        assertTrue(fk.error(ff));
        assertTrue(fk.geterror(ff).contains("run_timeout_ms"), fk.geterror(ff));
    }

    @Test
    public void testFkStop() throws Exception {
        stopTarget = f;

        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while true then\n" +
                "        i = i + 1\n" +
                "        FakeScriptTest.stopNow()\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        fk.regclass(f, FakeScriptTest.class);

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNull(ret);
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("stopped by fk.stop"), fk.geterror(f));
    }

    @Test
    public void testContainerMaxSize() throws Exception {
        fkconfig config = new fkconfig();
        config.container_max_size = 10;
        fake ff = fk.newfake(config);
        fk.openbaselib(ff);

        String arrayScript =
                "func f()\n" +
                "    var arr = array()\n" +
                "    arr[15] = 1\n" +
                "end\n";

        boolean ok = fk.parsestr(ff, arrayScript);
        assertTrue(ok, fk.geterror(ff));

        fk.run(ff, "f");
        assertTrue(fk.error(ff));
        assertTrue(fk.geterror(ff).contains("container too big"), fk.geterror(ff));

        ff.clearerr();

        String mapScript =
                "func f()\n" +
                "    var m = map()\n" +
                "    for var i = 0, i < 100, i++ then\n" +
                "        m[i] = i\n" +
                "    end\n" +
                "end\n";

        ok = fk.parsestr(ff, mapScript);
        assertTrue(ok, fk.geterror(ff));

        fk.run(ff, "f");
        assertTrue(fk.error(ff));
        assertTrue(fk.geterror(ff).contains("container too big"), fk.geterror(ff));
    }

    @Test
    public void testRuntimeErrorContainsScriptCallStack() throws Exception {
        String script =
                "func g()\n" +
                "    var arr = array()\n" +
                "    return arr[-1]\n" +
                "end\n" +
                "func f()\n" +
                "    return g()\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        fk.run(f, "f");
        assertTrue(fk.error(f));
        String err = fk.geterror(f);
        assertTrue(err.contains("call stack:"), err);
        assertTrue(err.contains("g"), err);
    }

    @Test
    public void testBoolVariantSemantics() {
        variant rv = new variant();
        rv.set_real(0);
        assertFalse(rv.bool());
        rv.set_real(3);
        assertTrue(rv.bool());

        variant nv = new variant();
        nv.set_nil();
        assertFalse(nv.bool());

        variant sv = new variant();
        sv.set_string("x");
        assertFalse(sv.bool());
    }

    @Test
    public void testParseClearsPreviousError() throws Exception {
        boolean ok = fk.parsestr(f, "func f(\nend\n");
        assertFalse(ok);
        assertTrue(fk.error(f));
        assertFalse(fk.geterror(f).isEmpty());

        ok = fk.parsestr(f, "func g()\n    return 1\nend\n");
        assertTrue(ok, fk.geterror(f));
        assertFalse(fk.error(f), fk.geterror(f));
    }

    @Test
    public void testTypeConversionThroughBinding() throws Exception {
        fk.regclass(f, FakeScriptTest.class);

        String script =
                "func f()\n" +
                "    var a = FakeScriptTest.toInt(3.7)\n" +
                "    var b = FakeScriptTest.toBool(2.5)\n" +
                "    var c = FakeScriptTest.toBool(0)\n" +
                "    var d = FakeScriptTest.toLong(7.9)\n" +
                "    return a, b, c, d\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals(3.0, ((Double) rets[0]).doubleValue(), 0.0001);
        assertEquals(1.0, ((Double) rets[1]).doubleValue(), 0.0001);
        assertEquals(0.0, ((Double) rets[2]).doubleValue(), 0.0001);
        assertEquals(7L, ((Long) rets[3]).longValue());
    }

    @Test
    public void testNewClassWhiteList() throws Exception {
        fkconfig config = new fkconfig();
        config.new_class_white_list = new String[] { "java.lang.Object" };
        fake ff = fk.newfake(config);
        fk.openbaselib(ff);

        String script =
                "func f()\n" +
                "    var a = new(\"java.lang.Object\")\n" +
                "    var b = new(\"java.io.File\")\n" +
                "    return a, b\n" +
                "end\n";

        boolean ok = fk.parsestr(ff, script);
        assertTrue(ok, fk.geterror(ff));

        Object[] rets = fk.runmulti(ff, "f");
        assertEquals(2, rets.length);
        assertTrue(rets[0] instanceof Object);
        assertEquals("java.lang.Object", rets[0].getClass().getName());
        assertTrue(rets[1] instanceof String, rets[1].toString());
        assertTrue(((String) rets[1]).contains("not in new_class_white_list"), rets[1].toString());
    }

    @Test
    public void testResumeFrameSliced() throws Exception {
        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while i < 100 then\n" +
                "        i = i + 1\n" +
                "        yield 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = null;
        int frames = 0;
        while (frames < 10000)
        {
            Object[] r = fk.resume(f, "f");
            if (r != null)
            {
                rets = r;
                break;
            }
            frames++;
        }

        assertNotNull(rets, "resume did not finish in 10000 frames: " + fk.geterror(f));
        assertTrue(frames > 10, "expected frame-sliced execution, finished in " + frames + " frames");
        assertEquals(100.0, ((Double) rets[0]).doubleValue(), 0.0001);

        // 结束后可以重新启动,同样按帧执行直到结束
        Object[] again = null;
        int restartFrames = 0;
        while (restartFrames < 10000)
        {
            Object[] r = fk.resume(f, "f");
            if (r != null)
            {
                again = r;
                break;
            }
            restartFrames++;
        }
        assertNotNull(again, fk.geterror(f));
        assertEquals(100.0, ((Double) again[0]).doubleValue(), 0.0001);
    }

    @Test
    public void testResumeWithArgs() throws Exception {
        String script =
                "func f(a, b)\n" +
                "    return a + b, a - b\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.resume(f, "f", 10, 4);
        assertNotNull(rets, fk.geterror(f));
        assertEquals(14.0, ((Double) rets[0]).doubleValue(), 0.0001);
        assertEquals(6.0, ((Double) rets[1]).doubleValue(), 0.0001);
    }
}
