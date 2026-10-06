package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖率收官:脚本驱动的分支矩阵
 */
public class CoverageScriptTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        f.cfg.open_debug_log = 1; // 覆盖types.log
        fk.openbaselib(f);
    }

    @Test
    public void testNestedLogicViaIf() {
        // 语法限制:&&/||不支持关系比较链式,逻辑组合用嵌套if表达
        String script =
                "func f(a, b, c)\n" +
                "    var r1 = 0\n" +
                "    if a > 0 then\n" +
                "        if b > 0 then\n" +
                "            if c > 0 then\n" +
                "                r1 = 1\n" +
                "            end\n" +
                "        end\n" +
                "    end\n" +
                "    var r4 = 0\n" +
                "    if a == 0 then\n" +
                "        r4 = 1\n" +
                "    end\n" +
                "    var r5 = 0\n" +
                "    if a != 0 then\n" +
                "        r5 = 1\n" +
                "    end\n" +
                "    return r1, r4, r5\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] all = fk.runmulti(f, "f", 1, 1, 1);
        assertEquals(3, all.length, fk.geterror(f));
        assertEquals(1L, ((Long) all[0]).longValue());
        assertEquals(0L, ((Long) all[1]).longValue());
        assertEquals(1L, ((Long) all[2]).longValue());

        Object[] zero = fk.runmulti(f, "f", 0, 0, 0);
        assertEquals(0L, ((Long) zero[0]).longValue());
        assertEquals(1L, ((Long) zero[1]).longValue());
        assertEquals(0L, ((Long) zero[2]).longValue());
    }

    @Test
    public void testNotOnBareValue() {
        // !只作用于裸变量/显式值(NOT cmp_value),不能作用于括号比较
        String script =
                "func f(a)\n" +
                "    var r = 0\n" +
                "    if !a then\n" +
                "        r = 1\n" +
                "    end\n" +
                "    return r\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(1L, ((Long) fk.run(f, "f", 0)).longValue());
        assertEquals(0L, ((Long) fk.run(f, "f", 2)).longValue());
    }

    @Test
    public void testNestedForBreakContinue() {
        String script =
                "func f()\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < 3, i++ then\n" +
                "        for var j = 0, j < 3, j++ then\n" +
                "            if j == 1 then\n" +
                "                continue\n" +
                "            end\n" +
                "            if j == 2 then\n" +
                "                break\n" +
                "            end\n" +
                "            s = s + 10\n" +
                "        end\n" +
                "        s = s + 1\n" +
                "    end\n" +
                "    return s\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        // 每轮外层:内层j=0加10后,j=1 continue,j=2 break;外层+1 → 11*3=33
        assertEquals(33L, ((Long) fk.run(f, "f")).longValue());
    }

    @Test
    public void testElseifChains() {
        String script =
                "func f(x)\n" +
                "    if x == 1 then\n" +
                "        return \"a\"\n" +
                "    elseif x == 2 then\n" +
                "        return \"b\"\n" +
                "    elseif x == 3 then\n" +
                "        return \"c\"\n" +
                "    else\n" +
                "        return \"d\"\n" +
                "    end\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals("a", fk.run(f, "f", 1));
        assertEquals("b", fk.run(f, "f", 2));
        assertEquals("c", fk.run(f, "f", 3));
        assertEquals("d", fk.run(f, "f", 9));
    }

    @Test
    public void testRangeAllBranches() {
        String script =
                "func f()\n" +
                "    var arr = array()\n" +
                "    push(arr, \"x\")\n" +
                "    var m = map()\n" +
                "    m[\"k\"] = \"v\"\n" +
                "    k, v := range(m, 0)\n" +
                "    return range(arr, 0), range(arr, 5), k, v\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        // map的range返回键值对两个值
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals("x", rets[0]);
        assertEquals(0.0, ((Double) rets[1]).doubleValue(), 0.0000001); // 数组越界:false→0
        assertEquals("k", rets[2]);
        assertEquals("v", rets[3]);

        // map越界返回两个false:用双接收语法
        assertTrue(fk.parsestr(f, "func g()\n    var m = map()\n    a, b := range(m, 9)\n    return a, b\nend\n"), fk.geterror(f));
        Object[] bad = fk.runmulti(f, "g");
        assertEquals(2, bad.length, fk.geterror(f));
        assertEquals(0.0, ((Double) bad[0]).doubleValue(), 0.0000001);
        assertEquals(0.0, ((Double) bad[1]).doubleValue(), 0.0000001);
    }

    @Test
    public void testResumeErrorAndRestart() throws Exception {
        // resume不存在的函数:启动失败带错误
        Object[] rets = fk.resume(f, "nosuch");
        assertEquals(1, rets.length);
        assertNull(rets[0]);
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("no func"), fk.geterror(f));

        // 无返回值函数:结束返回[null]
        assertTrue(fk.parsestr(f, "func f()\n    var x = 1\nend\n"), fk.geterror(f));
        Object[] done = fk.resume(f, "f");
        assertNotNull(done);
        assertNull(done[0]);
    }

    @Test
    public void testDebugrunPaths() throws Exception {
        // 正常debugrun
        String script =
                "func f(x)\n" +
                "    return x * 2\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        // debug()内部阻塞读取System.in,必须喂入调试命令流("c"=continue,EOF兜底)
        System.setIn(new java.io.ByteArrayInputStream("c\n".getBytes(StandardCharsets.UTF_8)));
        Object ret = fk.debugrun(f, "f", 21);
        System.setIn(new java.io.ByteArrayInputStream(new byte[0]));
        assertEquals(42L, ((Long) ret).longValue());

        // 出错脚本:错误被记录而非逃逸
        assertTrue(fk.parsestr(f, "func g()\n    return 1 / 0\nend\n"), fk.geterror(f));
        System.setIn(new java.io.ByteArrayInputStream("c\n".getBytes(StandardCharsets.UTF_8)));
        Object ret2 = fk.debugrun(f, "g");
        System.setIn(new java.io.ByteArrayInputStream(new byte[0]));
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("divide"), fk.geterror(f));

        // 不存在的函数
        Object ret3 = fk.debugrun(f, "nosuch");
        assertTrue(fk.error(f));
    }

    @Test
    public void testBuiltinTonumberTolongAllBranches() {
        String script =
                "func f()\n" +
                "    return tonumber(\"42\"), tonumber(\"4.5\"), tonumber(7), tonumber(7.5), tolong(3.9), typeof(tolong(\"9\"))\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(6, rets.length, fk.geterror(f));
        assertEquals(42L, ((Long) rets[0]).longValue());
        assertEquals(4.5, ((Double) rets[1]).doubleValue(), 0.0000001);
        assertEquals(7L, ((Long) rets[2]).longValue());
        assertEquals(7.5, ((Double) rets[3]).doubleValue(), 0.0000001);
        assertEquals(3L, ((Long) rets[4]).longValue());
        assertEquals("INT", rets[5]);
    }

    @Test
    public void testRegallScansPackage() {
        // regall扫描包下所有类并绑定全部public方法
        assertDoesNotThrow(() -> fk.regall(f, "com.github.esrrhs.fakescript"));
        assertTrue(fk.isfunc(f, "BindingTest.intP"));
    }

    @Test
    public void testPrintAndFormatNoArgs() {
        String script =
                "func f()\n" +
                "    print()\n" +
                "    return format()\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals("", fk.run(f, "f"));
    }

    @Test
    public void testStructMemberChain() {
        // struct成员读写全覆盖
        String script =
                "struct P\n" +
                "    x\n" +
                "    y\n" +
                "end\n" +
                "func f()\n" +
                "    var a = P()\n" +
                "    a->x = 1\n" +
                "    a->y = a->x + 1\n" +
                "    var b = a\n" +
                "    b->x = 10\n" +
                "    return a->x, a->y, b->x\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        // struct是引用语义:b与a同一实例
        assertEquals(10L, ((Long) rets[0]).longValue());
        assertEquals(2L, ((Long) rets[1]).longValue());
        assertEquals(10L, ((Long) rets[2]).longValue());
    }

    @Test
    public void testIsOperator() throws Exception {
        // is是一元存在判断语法(C++移植遗留):is <值> 等价于取该值真假
        String script =
                "func f(x)\n" +
                "    if is x then\n" +
                "        return \"exists\"\n" +
                "    end\n" +
                "    return \"none\"\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals("exists", fk.run(f, "f", 1));
        // 0即假:is 0为"不存在"
        assertEquals("none", fk.run(f, "f", 0));
    }

    @Test
    public void testDebugSessionFileBased(@TempDir Path dir) throws Exception {
        // 文件脚本:调试器的list/l带范围、断点enable/disable展示
        Path file = dir.resolve("dbg.fk");
        Files.write(file,
                ("func f()\n" +
                "    var i = 0\n" +
                "    while i < 5 then\n" +
                "        i = i + 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n").getBytes(StandardCharsets.UTF_8));

        assertTrue(fk.parse(f, file.toString()), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        // 按文件:行号断点
        String b = s.execute("b " + file.getFileName() + ":4");
        assertTrue(b.contains("Breakpoint 0"), b);

        // info b:断点列表
        String ib = s.execute("i b");
        assertTrue(ib.contains("file dbg.fk, line 4"), ib);

        // disable后continue不再触发
        String dis = s.execute("dis 0");
        String c = s.execute("c");
        assertFalse(c.contains("Trigger Breakpoint"), c);
        assertTrue(s.is_end());

        // enable回来(已结束无影响)
        s.execute("en 0");

        // l带范围:文件源码显示
        assertTrue(fk.parse(f, file.toString()), fk.geterror(f));
        debug_session s2 = f.dbg.createsession("f");
        String l = s2.execute("l 2");
        assertTrue(l.contains("var i"), l);
        assertTrue(l.contains("func f"), l);

        s2.execute("c");
        assertTrue(s2.is_end());
        assertEquals(5L, s2.get_ret().get_int());
    }

    @Test
    public void testPackagehelperJarScan(@TempDir Path dir) throws Exception {
        // 把target/classes打成jar,通过URLClassLoader验证jar扫描分支
        java.io.File jarFile = dir.resolve("scan.jar").toFile();
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                new java.io.FileOutputStream(jarFile))) {
            java.io.File classesDir = new java.io.File("target/classes/com/github/esrrhs/fakescript");
            java.io.File[] classFiles = classesDir.listFiles((d, name) -> name.endsWith(".class"));
            assertNotNull(classFiles);
            for (java.io.File cf : classFiles) {
                jos.putNextEntry(new java.util.jar.JarEntry("com/github/esrrhs/fakescript/" + cf.getName()));
                Files.copy(cf.toPath(), jos);
                jos.closeEntry();
            }
        }

        java.net.URLClassLoader jarLoader = new java.net.URLClassLoader(
                new java.net.URL[] { jarFile.toURI().toURL() }, getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(jarLoader);
        try {
            java.util.List<Class<?>> classes = packagehelper.getClasses(f, "com.github.esrrhs.fakescript");
            assertTrue(classes.size() > 10, "should scan classes from jar: " + classes.size());
        } finally {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        }
    }

    @Test
    public void testStaleRetNotLeaked() {
        // 回归:同routine内先调用多返回值函数再单值return,陈旧值不得泄漏
        String script =
                "func g()\n" +
                "    return 1, 2, 3\n" +
                "end\n" +
                "func f()\n" +
                "    g()\n" +
                "    return 9\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(1, rets.length, "stale rets leaked: got " + rets.length);
        assertEquals(9L, ((Long) rets[0]).longValue());
    }

    @Test
    public void testEmptyReturnDestructure() {
        // 回归:空return被多接收解构时,缺失值补nil而非IOOBE
        String script =
                "func p()\n" +
                "    return\n" +
                "end\n" +
                "func f()\n" +
                "    a, b := p()\n" +
                "    return a\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(1, rets.length, fk.geterror(f));
        assertNull(rets[0]);
        assertFalse(fk.error(f), fk.geterror(f));
    }

    @Test
    public void testSetRunningVariantBranches() throws Exception {
        String script =
                "const C = 5\n" +
                "func f()\n" +
                "    var s = \"old\"\n" +
                "    var n = 1\n" +
                "    yield 1\n" +
                "    return s\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        assertNull(fk.resume(f, "f"));
        int rid = fk.getcurroutineid(f);

        // 带引号的字符串值
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "s", "\"new\"", -1);
        assertEquals("\"new\"", fk.getcurvariantbyroutinebyframe(f, rid, 0, "s", -1));

        // true/false
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "n", "true", -1);
        assertEquals("1", fk.getcurvariantbyroutinebyframe(f, rid, 0, "n", -1));
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "n", "false", -1);
        assertEquals("0", fk.getcurvariantbyroutinebyframe(f, rid, 0, "n", -1));

        // 空值:静默返回
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "n", "", -1);

        // 指定不匹配的行号:静默不生效(行号是语句块内部记录行,对调用方不可预知,外部契约用-1)
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "s", "\"miss\"", 999);
        assertEquals("\"new\"", fk.getcurvariantbyroutinebyframe(f, rid, 0, "s", -1));

        // 不存在的变量:无效果不崩溃
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "nosuch", "1", -1);

        // const名拒绝
        fk.setcurvariantbyroutinebyframe(f, rid, 0, "C", "1", -1);

        while (fk.resume(f, "f") == null && !fk.error(f))
        {
        }
    }
}
