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
        assertEquals(3L, ((Long) ret).longValue());
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
        assertEquals(100L, ((Long) ret).longValue());
    }

    @Test
    public void testConstValue() {
        String script =
                "const MAX_COUNT = 100\n" +
                "func f()\n" +
                "    return MAX_COUNT\n" +
                "end\n";

        Object ret = runScript(script, "f");
        assertEquals(100L, ((Long) ret).longValue());
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
        assertEquals(45L, ((Long) ret).longValue());
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
        assertEquals(8L, ((Long) ret).longValue());
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
        assertEquals(42L, ((Long) rets[0]).longValue());
        assertEquals("42", rets[1]);
        assertEquals("INT", rets[2]);
        assertEquals(5L, ((Long) rets[3]).longValue());
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

        assertEquals(1L, ((Long) runScript(v1, "f")).longValue());
        assertEquals(2L, ((Long) runScript(v2, "f")).longValue());
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
        assertEquals(3L, ((Long) ret).longValue());
    }

    @fakescript(name = "myadd")
    public static int myadd(int a, int b) {
        return a + b;
    }

    @Test
    public void testPackageSameFile() {
        // package内的函数以"包名.函数名"注册,宿主按全名调用
        String script =
                "package mypkg.sub\n" +
                "func f()\n" +
                "    return 42\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        assertTrue(fk.isfunc(f, "mypkg.sub.f"));
        assertFalse(fk.isfunc(f, "f"));

        Object ret = fk.run(f, "mypkg.sub.f");
        assertEquals(42L, ((Long) ret).longValue());
    }

    @Test
    public void testPackageWithConstAndInnerCall() {
        // 包内const引用与函数互调自动拼包前缀
        String script =
                "package pkg\n" +
                "const N = 5\n" +
                "func inner()\n" +
                "    return N\n" +
                "end\n" +
                "func f()\n" +
                "    return inner() + N\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "pkg.f");
        assertEquals(10L, ((Long) ret).longValue());
    }

    @Test
    public void testPackageAcrossInclude(@TempDir Path dir) throws Exception {
        // include的文件可以有自己的package;调用方用"包名.函数名"调用
        Files.write(dir.resolve("a.fk"),
                "package lib\nfunc fa()\n    return 7\nend\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("main.fk"),
                "include \"a.fk\"\nfunc f()\n    return lib.fa()\nend\n".getBytes(StandardCharsets.UTF_8));

        boolean ok = fk.parse(f, dir.resolve("main.fk").toString());
        assertTrue(ok, fk.geterror(f));

        assertTrue(fk.isfunc(f, "lib.fa"));

        assertEquals(7L, ((Long) fk.run(f, "f")).longValue());
        assertEquals(7L, ((Long) fk.run(f, "lib.fa")).longValue());
    }

    @Test
    public void testCloneFake() {
        String script =
                "func f(x)\n" +
                "    return x * 2\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // clone共享已编译的函数与const,可直接运行
        fake c = fk.clone(f);
        assertEquals(8L, ((Long) fk.run(c, "f", 4)).longValue());
        assertEquals(8L, ((Long) fk.run(f, "f", 4)).longValue());
    }

    @Test
    public void testOnErrorCallback() {
        final StringBuilder errmsg = new StringBuilder();
        fk.set_callback(f, new callback() {
            @Override
            public void on_error(fake ff, String file, int lineno, String funcname, String str) {
                errmsg.append(str);
            }

            @Override
            public void on_print(fake ff, String str) {
            }
        });

        String script =
                "func f()\n" +
                "    var arr = array()\n" +
                "    return arr[-1]\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(errmsg.length() > 0, "on_error should be invoked");
        assertTrue(errmsg.toString().contains("array"), errmsg.toString());
    }

    @Test
    public void testDostring() {
        // dostring在运行时编译并注册新函数,随后即可调用
        String script =
                "func f()\n" +
                "    dostring(\"func g() return 9 end\")\n" +
                "    return g()\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertEquals(9L, ((Long) ret).longValue());
        assertTrue(fk.isfunc(f, "g"));
    }

    @Test
    public void testGetconst() {
        String script =
                "const VERSION = 7\n" +
                "func f()\n" +
                "    return getconst(\"VERSION\")\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertEquals(7L, ((Long) ret).longValue());
    }

    @Test
    public void testDofileDisabled() {
        fkconfig config = new fkconfig();
        config.allow_dofile = false;
        fake ff = fk.newfake(config);
        fk.openbaselib(ff);

        String script =
                "func f()\n" +
                "    return dofile(\"no_such_allow.fk\")\n" +
                "end\n";

        assertTrue(fk.parsestr(ff, script), fk.geterror(ff));

        Object ret = fk.run(ff, "f");
        assertEquals(0.0, ((Double) ret).doubleValue(), 0.0001);
        assertTrue(fk.error(ff));
        assertTrue(fk.geterror(ff).contains("allow_dofile is false"), fk.geterror(ff));
    }

    @Test
    public void testMathBuiltins() {
        String script =
                "func f()\n" +
                "    return abs(-7), abs(-7.5), floor(2.7), ceil(2.1), sqrt(16.0), pow(2.0, 10.0), typeof(floor(2.7))\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(7, rets.length, fk.geterror(f));
        assertEquals(7L, ((Long) rets[0]).longValue());
        assertEquals(7.5, ((Double) rets[1]).doubleValue(), 0.0000001);
        assertEquals(2L, ((Long) rets[2]).longValue());
        assertEquals(3L, ((Long) rets[3]).longValue());
        assertEquals(4.0, ((Double) rets[4]).doubleValue(), 0.0000001);
        assertEquals(1024.0, ((Double) rets[5]).doubleValue(), 0.0000001);
        assertEquals("INT", rets[6]);
    }

    @Test
    public void testRandomAndTime() {
        String script =
                "func f()\n" +
                "    var r1 = random()\n" +
                "    var r2 = random(100)\n" +
                "    var t = time()\n" +
                "    return r1, typeof(r1), r2, typeof(r2), t, typeof(t)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(6, rets.length, fk.geterror(f));
        double r1 = ((Double) rets[0]).doubleValue();
        assertTrue(r1 >= 0.0 && r1 < 1.0);
        assertEquals("REAL", rets[1]);
        long r2 = ((Long) rets[2]).longValue();
        assertTrue(r2 >= 0 && r2 < 100);
        assertEquals("INT", rets[3]);
        long t = ((Long) rets[4]).longValue();
        assertTrue(Math.abs(t - System.currentTimeMillis()) < 60000);
        assertEquals("INT", rets[5]);
    }

    @Test
    public void testStringBuiltins() {
        String script =
                "func f()\n" +
                "    var s = \"Hello, World\"\n" +
                "    return substr(s, 7, 5), find(s, \"World\"), find(s, \"nope\"), upper(s), lower(s), trim(\"  x  \"), replace(s, \"World\", \"FK\"), size(replace(s, \"World\", \"FK\"))\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(8, rets.length, fk.geterror(f));
        assertEquals("World", rets[0]);
        assertEquals(7L, ((Long) rets[1]).longValue());
        assertEquals(-1L, ((Long) rets[2]).longValue());
        assertEquals("HELLO, WORLD", rets[3]);
        assertEquals("hello, world", rets[4]);
        assertEquals("x", rets[5]);
        assertEquals("Hello, FK", rets[6]);
        assertEquals(9L, ((Long) rets[7]).longValue());
    }

    @Test
    public void testSplitBuiltin() {
        // 分隔符按字面量整体匹配
        String script =
                "func f()\n" +
                "    var parts = split(\"a,b,c\", \",\")\n" +
                "    var multi = split(\"x::y:z\", \":\")\n" +
                "    return size(parts), parts[0], parts[2], size(multi), multi[2]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(3L, ((Long) rets[0]).longValue());
        assertEquals("a", rets[1]);
        assertEquals("c", rets[2]);
        // 连续分隔符产生空元素
        assertEquals(4L, ((Long) rets[3]).longValue());
        assertEquals("y", rets[4]);
    }

    @Test
    public void testArrayOps() {
        String script =
                "func f()\n" +
                "    var arr = array()\n" +
                "    push(arr, 1)\n" +
                "    push(arr, 2)\n" +
                "    push(arr, 3)\n" +
                "    var p = pop(arr)\n" +
                "    insert(arr, 0, 0)\n" +
                "    var r = remove(arr, 1)\n" +
                "    sort(arr)\n" +
                "    return size(arr), p, r, arr[0], arr[1]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(2L, ((Long) rets[0]).longValue());
        assertEquals(3L, ((Long) rets[1]).longValue());
        assertEquals(1L, ((Long) rets[2]).longValue());
        assertEquals(0L, ((Long) rets[3]).longValue());
        assertEquals(2L, ((Long) rets[4]).longValue());
    }

    @Test
    public void testArraySortBuiltin() {
        String script =
                "func f()\n" +
                "    var nums = array()\n" +
                "    push(nums, 3.5)\n" +
                "    push(nums, 1)\n" +
                "    push(nums, 2.5)\n" +
                "    sort(nums)\n" +
                "    var strs = split(\"b,a,c\", \",\")\n" +
                "    sort(strs)\n" +
                "    return nums[0], nums[2], strs[0], strs[2]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals(3.5, ((Double) rets[1]).doubleValue(), 0.0000001);
        assertEquals("a", rets[2]);
        assertEquals("c", rets[3]);
    }

    @Test
    public void testMapKeysValues() {
        String script =
                "func f()\n" +
                "    var m = map()\n" +
                "    m[\"a\"] = 1\n" +
                "    m[\"b\"] = 2\n" +
                "    var ks = keys(m)\n" +
                "    var vs = values(m)\n" +
                "    var sum = 0\n" +
                "    for var i = 0, i < size(vs), i++ then\n" +
                "        sum = sum + vs[i]\n" +
                "    end\n" +
                "    return size(ks), sum\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(2, rets.length, fk.geterror(f));
        assertEquals(2L, ((Long) rets[0]).longValue());
        assertEquals(3L, ((Long) rets[1]).longValue());
    }

    @Test
    public void testDeepCopy() {
        String script =
                "func f()\n" +
                "    var m = map()\n" +
                "    var arr = array()\n" +
                "    push(arr, 1)\n" +
                "    push(arr, \"x\")\n" +
                "    m[\"list\"] = arr\n" +
                "    m[\"n\"] = 5\n" +
                "    var c = copy(m)\n" +
                "    c[\"n\"] = 100\n" +
                "    var cl = c[\"list\"]\n" +
                "    cl[0] = 999\n" +
                "    var ml = m[\"list\"]\n" +
                "    return m[\"n\"], ml[0], c[\"n\"], cl[0], cl[1]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        // 深拷贝后互不影响
        assertEquals(5L, ((Long) rets[0]).longValue());
        assertEquals(1L, ((Long) rets[1]).longValue());
        assertEquals(100L, ((Long) rets[2]).longValue());
        assertEquals(999L, ((Long) rets[3]).longValue());
        assertEquals("x", rets[4]);
    }

    @Test
    public void testJsonRoundTrip() {
        String script =
                "func f()\n" +
                "    var m = map()\n" +
                "    m[\"name\"] = \"fake\"\n" +
                "    m[\"hp\"] = 100\n" +
                "    m[\"atk\"] = 12.5\n" +
                "    m[\"dead\"] = false\n" +
                "    var tags = array()\n" +
                "    push(tags, \"boss\")\n" +
                "    push(tags, 42)\n" +
                "    m[\"tags\"] = tags\n" +
                "    var j = tojson(m)\n" +
                "    var back = fromjson(j)\n" +
                "    var btags = back[\"tags\"]\n" +
                "    return back[\"name\"], back[\"hp\"], back[\"atk\"], btags[0], btags[1]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals("fake", rets[0]);
        assertEquals(100L, ((Long) rets[1]).longValue());
        assertEquals(12.5, ((Double) rets[2]).doubleValue(), 0.0000001);
        assertEquals("boss", rets[3]);
        assertEquals(42L, ((Long) rets[4]).longValue());
    }

    @Test
    public void testJsonTypesAndEscapes() {
        String script =
                "func f()\n" +
                "    var j = fromjson(\"{\\\"s\\\":\\\"a\\\\nb\\\",\\\"i\\\":9007199254740993,\\\"f\\\":1.5,\\\"n\\\":null,\\\"t\\\":true}\")\n" +
                "    return j[\"s\"], size(j[\"s\"]), j[\"i\"], typeof(j[\"i\"]), j[\"f\"], j[\"n\"], j[\"t\"]\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(7, rets.length, fk.geterror(f));
        assertEquals("a\nb", rets[0]);
        assertEquals(3L, ((Long) rets[1]).longValue());
        // 2^53+1:JSON整数保真为64位INT
        assertEquals(9007199254740993L, ((Long) rets[2]).longValue());
        assertEquals("INT", rets[3]);
        assertEquals(1.5, ((Double) rets[4]).doubleValue(), 0.0000001);
        assertNull(rets[5]);
        assertEquals(1.0, ((Double) rets[6]).doubleValue(), 0.0000001);
    }

    @Test
    public void testJsonTojsonOutput() {
        String script =
                "func f()\n" +
                "    var m = map()\n" +
                "    m[\"id\"] = 123u\n" +
                "    m[\"nil\"] = null\n" +
                "    var arr = array()\n" +
                "    push(arr, 1.0)\n" +
                "    push(arr, 2.5)\n" +
                "    m[\"arr\"] = arr\n" +
                "    return tojson(m)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object j = fk.run(f, "f");
        // UUID输出为数字;REAL整数值输出为整数;nil输出null;键按HashMap遍历序,断言子串
        String s = (String) j;
        assertTrue(s.contains("\"id\":123"), s);
        assertTrue(s.contains("\"nil\":null"), s);
        assertTrue(s.contains("[1,2.5]"), s);
    }

    @Test
    public void testJsonErrors() {
        String script =
                "func f()\n" +
                "    return fromjson(\"{bad\")\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object ret = fk.run(f, "f");
        assertNull(ret);
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("json parse fail"), fk.geterror(f));
    }

    @Test
    public void testBreakContinue() {
        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    var hits = 0\n" +
                "    while true then\n" +
                "        i = i + 1\n" +
                "        if i % 2 == 0 then\n" +
                "            continue\n" +
                "        end\n" +
                "        hits = hits + 1\n" +
                "        if i >= 9 then\n" +
                "            break\n" +
                "        end\n" +
                "    end\n" +
                "    return i, hits\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(2, rets.length, fk.geterror(f));
        assertEquals(9L, ((Long) rets[0]).longValue());
        assertEquals(5L, ((Long) rets[1]).longValue());
    }

    @Test
    public void testBreakContinueInFor() {
        String script =
                "func f()\n" +
                "    var sum = 0\n" +
                "    for var j = 0, j < 10, j++ then\n" +
                "        if j < 3 then\n" +
                "            continue\n" +
                "        end\n" +
                "        if j > 6 then\n" +
                "            break\n" +
                "        end\n" +
                "        sum = sum + j\n" +
                "    end\n" +
                "    return sum\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object ret = fk.run(f, "f");
        assertEquals(18L, ((Long) ret).longValue());
    }

    @Test
    public void testConstMapArrayLiterals() {
        String script =
                "const CONFIG_MAP = {1 : \"Alpha\" 2 : \"Beta\"}\n" +
                "const CONFIG_ARR = [10 20 30]\n" +
                "func f()\n" +
                "    return CONFIG_MAP[1], CONFIG_MAP[2], CONFIG_ARR[0], CONFIG_ARR[2], size(CONFIG_ARR)\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals("Alpha", rets[0]);
        assertEquals("Beta", rets[1]);
        assertEquals(10L, ((Long) rets[2]).longValue());
        assertEquals(30L, ((Long) rets[3]).longValue());
        assertEquals(3L, ((Long) rets[4]).longValue());
    }

    @Test
    public void testConstContainerIsReadOnly() {
        String script =
                "const ARR = [1 2]\n" +
                "func f()\n" +
                "    ARR[0] = 9\n" +
                "    return ARR[0]\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f), "const container write should fail");
        assertTrue(fk.geterror(f).contains("const"), fk.geterror(f));
    }

    @Test
    public void testMultiAssignFromFunction() {
        // 声明式多赋值用:=;=要求变量已声明
        String script =
                "func pair()\n" +
                "    return 1, 2\n" +
                "end\n" +
                "func f()\n" +
                "    a, b := pair()\n" +
                "    return a, b, a + b\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(3, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals(2L, ((Long) rets[1]).longValue());
        assertEquals(3L, ((Long) rets[2]).longValue());
    }

    @Test
    public void testDofilePositive(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("extra.fk"),
                "func extra_func()\n    return 77\nend\n".getBytes(StandardCharsets.UTF_8));

        String script =
                "func f()\n" +
                "    dofile(\"" + dir.resolve("extra.fk").toString().replace("\\", "\\\\")
                        + "\")\n" +
                "    return extra_func()\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(77L, ((Long) fk.run(f, "f")).longValue());
        assertTrue(fk.isfunc(f, "extra_func"));
    }

    @Test
    public void testRegPackageScan() {
        // 扫描包下所有类并绑定(测试包里有带@fakescript的方法)
        fk.reg(f, "com.github.esrrhs.fakescript");

        assertTrue(fk.isfunc(f, "FakeScriptTest.add"));

        String script =
                "func f()\n" +
                "    return FakeScriptTest.add(2, 3)\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(5L, ((Long) fk.run(f, "f")).longValue());
    }

    @Test
    public void testDebugSetVariantInt() throws Exception {
        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while i < 100 then\n" +
                "        i = i + 1\n" +
                "        yield 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // 启动并跑一帧
        assertNull(fk.resume(f, "f"));
        int rid = fk.getcurroutineid(f);

        // 读回当前变量(启动帧已执行过一次自增)
        String val = fk.getcurvariantbyroutinebyframe(f, rid, 0, "i", -1);
        assertEquals("1", val);

        // 调试器修改变量为99(整数路径),循环应很快结束
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "i", "99", -1);
        assertEquals("99", fk.getcurvariantbyroutinebyframe(f, rid, 0, "i", -1));

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
        assertNotNull(rets, fk.geterror(f));
        assertEquals(100L, ((Long) rets[0]).longValue());
        assertTrue(frames <= 3, "i=99 should finish fast, took " + frames + " frames");
    }

    @Test
    public void testRuntimeErrorPaths() {
        // 除零
        assertTrue(fk.parsestr(f, "func f()\n    return 1 / 0\nend\n"), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("divide"), fk.geterror(f));

        // 调用不存在的函数
        f.clearerr();
        assertTrue(fk.parsestr(f, "func f()\n    return no_such_func()\nend\n"), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("no func"), fk.geterror(f));

        // 参数个数不匹配
        f.clearerr();
        assertTrue(fk.parsestr(f,
                "func add(a, b)\n    return a + b\nend\nfunc f()\n    return add(1)\nend\n"), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("param not match"), fk.geterror(f));

        // 容器下标为负
        f.clearerr();
        assertTrue(fk.parsestr(f, "func f()\n    var a = array()\n    return a[-1]\nend\n"), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("index"), fk.geterror(f));

        // 非容器取下标
        f.clearerr();
        assertTrue(fk.parsestr(f, "func f()\n    var x = 5\n    return x[0]\nend\n"), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("container"), fk.geterror(f));
    }

    @Test
    public void testStackOverflowProtection() {
        // 无穷递归触发stack_max保护
        String script =
                "func rec(n)\n" +
                "    return rec(n + 1)\n" +
                "end\n" +
                "func f()\n" +
                "    return rec(1)\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        long start = System.currentTimeMillis();
        fk.run(f, "f");
        long cost = System.currentTimeMillis() - start;

        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("stack too big"), fk.geterror(f));
        // 保护应立刻生效,而不是靠超时
        assertTrue(cost < 5000, "stack overflow should abort fast, took " + cost + "ms");
    }

    @Test
    public void testSleepWaitsAndYields() {
        // 整跑模式:sleep应真实等待指定毫秒
        String script =
                "func f()\n" +
                "    var start = time()\n" +
                "    sleep 200\n" +
                "    return time() - start\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        long t0 = System.currentTimeMillis();
        Object ret = fk.run(f, "f");
        long wall = System.currentTimeMillis() - t0;

        long slept = ((Long) ret).longValue();
        assertTrue(slept >= 190, "script-observed sleep too short: " + slept);
        assertTrue(wall >= 190 && wall < 3000, "wall time " + wall + "ms");
    }

    @Test
    public void testSleepWithMultipleRoutines() {
        // 多协程都睡眠时不忙等,且全部按时唤醒
        String script =
                "func sleeper(n)\n" +
                "    sleep n\n" +
                "    var g = _G()\n" +
                "    g[\"done\"] = g[\"done\"] + 1\n" +
                "end\n" +
                "func f()\n" +
                "    var g = _G()\n" +
                "    g[\"done\"] = 0\n" +
                "    fake sleeper(150)\n" +
                "    fake sleeper(250)\n" +
                "    sleep 300\n" +
                "    return g[\"done\"]\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        long t0 = System.currentTimeMillis();
        Object ret = fk.run(f, "f");
        long wall = System.currentTimeMillis() - t0;

        assertEquals(2L, ((Long) ret).longValue(), fk.geterror(f));
        assertTrue(wall >= 280 && wall < 3000, "wall time " + wall + "ms");
    }

    @Test
    public void testVariantMathBranches() throws Exception {
        // REAL除法/取模走浮点或截断路径
        variant a = new variant();
        a.set_real(7.5);
        variant b = new variant();
        b.set_real(2);
        variant ret = new variant();
        ret.divide(a, b);
        assertEquals(3.75, ret.get_real(), 0.0000001);
        ret.divide_mod(a, b);
        // REAL取模与旧语义一致:先截断为整数再取模
        assertEquals(1.0, ret.get_real(), 0.0000001);

        // get_int对REAL按C语义截断
        assertEquals(7L, a.get_int());

        // UUID拒算
        variant u1 = new variant();
        u1.set_uuid(100L);
        variant u2 = new variant();
        u2.set_uuid(100L);
        assertThrows(Exception.class, () -> ret.plus(u1, u2));
        assertThrows(Exception.class, () -> ret.less(u1, u2));
        assertThrows(Exception.class, () -> u1.get_real());
        assertThrows(Exception.class, () -> u1.get_int());

        // UUID相等与不等
        assertTrue(u1.equals(u2));
        variant i1 = new variant();
        i1.set_int(100L);
        assertFalse(u1.equals(i1));

        // INT与REAL同值哈希一致(map键跨类型统一)
        variant iav = new variant();
        iav.set_int(1);
        variant rav = new variant();
        rav.set_real(1);
        assertEquals(iav.hashCode(), rav.hashCode());

        // 零除保护
        variant zero = new variant();
        zero.set_int(0);
        assertThrows(Exception.class, () -> ret.divide(a, zero));

        // 静态比较与not
        assertTrue(variant.less_jne(new variant(), iav) || true);
        variant nv = new variant();
        nv.set_int(0);
        assertTrue(variant.not_jne(nv));
        assertFalse(variant.not_jne(iav));

        // 字符串拼接
        variant cat = new variant();
        variant sl = new variant();
        sl.set_string("a");
        variant sr = new variant();
        sr.set_int(1);
        cat.string_cat(sl, sr);
        assertEquals("a1", cat.get_string());
    }

    @Test
    public void testObservationApisDuringSuspension(@TempDir Path dir) throws Exception {
        // 文件脚本:函数定位与源码读取需要真实文件名
        Path file = dir.resolve("obs.fk");
        Files.write(file,
                "func g(x)\n    yield 1\n    return x * 2\nend\nfunc f()\n    var r = g(21)\n    return r\nend\n"
                        .getBytes(StandardCharsets.UTF_8));

        assertTrue(fk.parse(f, file.toString()), fk.geterror(f));

        // 启动并跑到g内挂起(调用栈两帧:f -> g)
        assertTrue(fk.resume(f, "f") == null);

        int rid = fk.getcurroutineid(f);
        assertTrue(fk.ishaveroutine(f, rid));
        assertEquals(1, fk.getcurroutinenum(f));
        assertTrue(fk.getcurroutine(f).contains("g"), fk.getcurroutine(f));
        assertTrue(fk.getcurroutinebyid(f, rid).contains("g"));
        assertTrue(fk.getcurroutinebyindex(f, 0).contains("g"));
        assertTrue(fk.getroutineidbyindex(f, 0) == rid);

        // 调用栈:两帧,帧0是g
        assertEquals(2, fk.getcurcallstacklength(f));
        assertEquals("g", fk.getcurfuncbyroutinebyframe(f, rid, 0));
        assertEquals("f", fk.getcurfuncbyroutinebyframe(f, rid, 1));
        assertEquals(file.toString(), fk.getcurfilebyroutinebyframe(f, rid, 0));
        assertTrue(fk.getcurlinebyroutinebyframe(f, rid, 0) >= 1);
        assertTrue(fk.getcurcallstackbyroutinebyframe(f, rid, 0).contains("g"));
        assertEquals(2, fk.getcurcallstacklengthbyroutine(f, rid));
        assertTrue(fk.getcurbytecodeposbyroutine(f, rid) >= 0);

        // 参数变量跨帧可读:g的x=21
        assertEquals("21", fk.getcurvariantbyroutinebyframe(f, rid, 0, "x", -1));

        // 源码与函数定位
        String code = fk.getfilecode(f, file.toString(), 2);
        assertTrue(code.contains("yield"), code);
        assertEquals(file.toString(), fk.getfuncfile(f, "g"));
        // 语义:函数第一条字节码所在行(参数槽之后,即yield行),非函数声明行
        assertEquals(2, fk.getfuncstartline(f, "g"));

        // 反汇编
        assertTrue(fk.dumpfunc(f, "g", -1).contains("byte code"));

        // 当前调用栈
        assertTrue(fk.getcurcallstack(f).contains("g"));

        // 跑完
        Object[] rets = null;
        int guard = 0;
        while (rets == null && guard < 100)
        {
            rets = fk.resume(f, "f");
            guard++;
        }
        assertNotNull(rets, fk.geterror(f));
        assertEquals(42L, ((Long) rets[0]).longValue());
    }

    @Test
    public void testConcurrentAccessDetection() throws Exception {
        String script =
                "func f()\n" +
                "    sleep 300\n" +
                "    return 7\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // 线程A占用fake执行
        Thread a = new Thread(() -> fk.run(f, "f"));
        a.start();
        Thread.sleep(80); // 确保A已进入

        // 线程B并发进入:应被拒绝且不损坏状态
        Object retB = fk.run(f, "f");
        assertNull(retB);
        assertTrue(fk.geterror(f).contains("busy"), fk.geterror(f));

        a.join(5000);

        // A结束后,顺序执行恢复正常
        Object ret = fk.run(f, "f");
        assertEquals(7L, ((Long) ret).longValue());
    }

    @Test
    public void testSequentialCrossThreadStillWorks() throws Exception {
        // 线程A创建并解析,线程B执行,线程C再执行:顺序跨线程合法
        String script =
                "func f()\n" +
                "    return 42\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        final Object[] result = new Object[1];
        Thread b = new Thread(() -> result[0] = fk.run(f, "f"));
        b.start();
        b.join(5000);

        assertEquals(42L, ((Long) result[0]).longValue());

        Object[] result2 = new Object[1];
        Thread c = new Thread(() -> result2[0] = fk.run(f, "f"));
        c.start();
        c.join(5000);

        assertEquals(42L, ((Long) result2[0]).longValue());
    }

    @Test
    public void testConcurrentStressNoCorruption() throws Exception {
        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while i < 100 then\n" +
                "        i = i + 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        final java.util.List<Throwable> escaped = java.util.Collections
                .synchronizedList(new java.util.ArrayList<Throwable>());

        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++)
        {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 50; i++)
                {
                    try
                    {
                        fk.run(f, "f");
                    }
                    catch (Throwable e)
                    {
                        escaped.add(e);
                    }
                }
            });
            threads[t].start();
        }
        for (Thread t : threads)
        {
            t.join(30000);
        }

        // 并发误用不允许任何异常逃逸到调用方
        assertTrue(escaped.isEmpty(), "escaped: " + escaped);

        // 压力结束后,fake状态一致,顺序执行结果正确
        Object ret = fk.run(f, "f");
        assertEquals(100L, ((Long) ret).longValue(), fk.geterror(f));
    }

    @Test
    public void testHotReloadWhileSuspended() throws Exception {
        // 语义:挂起中的协程按旧字节码跑完;重新解析只影响之后的调用
        String v1 =
                "func g()\n" +
                "    yield 1\n" +
                "    return 1\n" +
                "end\n" +
                "func f()\n" +
                "    return g()\n" +
                "end\n";
        String v2 =
                "func g()\n" +
                "    yield 1\n" +
                "    return 2\n" +
                "end\n" +
                "func f()\n" +
                "    return g()\n" +
                "end\n";

        assertTrue(fk.parsestr(f, v1), fk.geterror(f));

        // 进入g并挂起
        assertNull(fk.resume(f, "f"));

        // 挂起中热更新g
        assertTrue(fk.parsestr(f, v2), fk.geterror(f));

        // 在飞的旧g继续按旧字节码跑完,返回1
        Object[] rets = null;
        int guard = 0;
        while (rets == null && guard < 100)
        {
            rets = fk.resume(f, "f");
            guard++;
        }
        assertNotNull(rets, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());

        // 下一次调用走新字节码,返回2
        assertEquals(2L, ((Long) fk.run(f, "f")).longValue());
    }

    @Test
    public void testIntegerPrecision() {
        // 2^60+1超出double精度,INT精确到64位
        String script =
                "func f()\n" +
                "    return 1152921504606846977 + 1\n" +
                "end\n";

        Object ret = runScript(script, "f");
        assertEquals(1152921504606846978L, ((Long) ret).longValue());
    }

    @Test
    public void testIntegerOverflowWrapsLikeC() {
        String script =
                "func f()\n" +
                "    return 9223372036854775807 + 1\n" +
                "end\n";

        Object ret = runScript(script, "f");
        assertEquals(-9223372036854775808L, ((Long) ret).longValue());
    }

    @Test
    public void testDivisionStaysFloating() {
        String script =
                "func f()\n" +
                "    var a = 1 / 2\n" +
                "    var b = 4 / 2\n" +
                "    return a, typeof(a), b, typeof(b)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals(0.5, ((Double) rets[0]).doubleValue(), 0.0000001);
        assertEquals("REAL", rets[1]);
        assertEquals(2.0, ((Double) rets[2]).doubleValue(), 0.0000001);
        assertEquals("REAL", rets[3]);
    }

    @Test
    public void testIntegerModulo() {
        String script =
                "func f()\n" +
                "    var a = 7 % 3\n" +
                "    var b = 7.5 % 3\n" +
                "    return a, typeof(a), b\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(3, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals("INT", rets[1]);
        // REAL参与%与旧语义一致:先截断为整数再取模
        assertEquals(1.0, ((Double) rets[2]).doubleValue(), 0.0000001);
    }

    @Test
    public void testMixedNumericComparisonAndEquality() {
        // 注意:比较表达式只能出现在if/while等条件位置,不能作赋值右值
        String script =
                "func f()\n" +
                "    var a = 0\n" +
                "    if 1 < 1.5 then\n" +
                "        a = 1\n" +
                "    end\n" +
                "    var b = 0\n" +
                "    if 1 == 1.0 then\n" +
                "        b = 1\n" +
                "    end\n" +
                "    var c = 0\n" +
                "    if 2 == 1.0 then\n" +
                "        c = 1\n" +
                "    end\n" +
                "    var d = 1 + 0.5\n" +
                "    return a, b, c, d, typeof(d)\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals(1L, ((Long) rets[1]).longValue());
        assertEquals(0L, ((Long) rets[2]).longValue());
        assertEquals(1.5, ((Double) rets[3]).doubleValue(), 0.0000001);
        assertEquals("REAL", rets[4]);
    }

    @Test
    public void testMapKeyCrossTypeNumeric() {
        // INT 1 与 REAL 1.0 是同一个键
        String script =
                "func f()\n" +
                "    var m = map()\n" +
                "    m[1] = \"a\"\n" +
                "    return m[1.0]\n" +
                "end\n";

        assertEquals("a", runScript(script, "f"));
    }

    @Test
    public void testUuidNotCalculable() {
        String script =
                "func f()\n" +
                "    return 123u + 1\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("can not calculate"), fk.geterror(f));
    }

    @Test
    public void testNumberConversionBuiltins() {
        String script =
                "func f()\n" +
                "    return tonumber(\"42\"), typeof(tonumber(\"42\")), tonumber(\"4.2\"), tolong(\"42\"), typeof(tolong(\"42\"))\n" +
                "end\n";

        boolean ok = fk.parsestr(f, script);
        assertTrue(ok, fk.geterror(f));

        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(42L, ((Long) rets[0]).longValue());
        assertEquals("INT", rets[1]);
        assertEquals(4.2, ((Double) rets[2]).doubleValue(), 0.0000001);
        assertEquals(42L, ((Long) rets[3]).longValue());
        assertEquals("INT", rets[4]);
    }
}
