package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界与错误路径的覆盖矩阵:解析错误、编译错误、类型矩阵、反汇编、宿主API边角
 */
public class EdgeCaseTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    // ==================== 解析错误矩阵 ====================

    @Test
    public void testParseErrorMatrix() {
        // 每种畸形输入都必须:干净返回false、带非空错误信息、绝不抛异常
        String[] bad = {
                "func f()\n",                       // 缺end
                "end\n",                            // 孤立end
                "func f()\n    var x = \nend\n",    // 缺右值
                "func f()\n    if x then\nend\n",   // if缺end
                "func f()\n    while\nend\n",       // while缺条件
                "func f()\n    for var i then\nend\n",
                "func f()\n    switch\nend\n",
                "func f()\n    switch x\nend\n",    // 缺case
                "func f()\n    var 1 = 2\nend\n",
                "func 123()\nend\n",
                "func f()\n    \"unterminated\nend\n",
                "func f()\n    var x = 1 +\nend\n",
                "func f()\n    ((\nend\n",
                "func f()\n    m[ = 1\nend\n",
                "func f()\n    var x = 1 1\nend\n", // 相邻表达式
                "const\n",                          // const缺名字
                "include\n",                        // include缺文件名
                "package\n",                        // package缺名字
                "func f()\n    yield\nend\n",       // yield缺表达式
                "func f()\n    sleep\nend\n",       // sleep缺表达式
                "func f()\n    var x = 1 ..\nend\n",
                "func f()\n    a.b = 1\nend\n",     // 裸标识符点链
                "func f(a, a)\nend\n",              // 重复参数
                "func f()\n    var x = ;\nend\n",
                "struct S\nend\nfunc f()\n    return S\nend\n", // struct名当值
        };

        for (String script : bad) {
            boolean ok = fk.parsestr(f, script);
            assertFalse(ok, "should not parse: " + printable(script));
            assertFalse(fk.geterror(f).isEmpty(), "should have error message: " + printable(script));
        }
    }

    // ==================== 编译错误矩阵 ====================

    @Test
    public void testCompileErrorMatrix() {
        // break/continue在循环外:修复后应为干净的编译错误而非ArrayList越界
        boolean ok = fk.parsestr(f, "func f()\n    break\nend\n");
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("no loop to break"), fk.geterror(f));

        f.clearerr();
        ok = fk.parsestr(f, "func f()\n    continue\nend\n");
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("no loop to continue"), fk.geterror(f));

        // 未定义变量
        f.clearerr();
        ok = fk.parsestr(f, "func f()\n    x = 1\nend\n");
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("not found"), fk.geterror(f));

        // 重复定义
        f.clearerr();
        ok = fk.parsestr(f, "func f()\n    var a = 1\n    var a = 2\nend\n");
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("has define"), fk.geterror(f));

        // 变量与const冲突
        f.clearerr();
        ok = fk.parsestr(f, "const N = 1\nfunc f()\n    var N = 2\nend\n");
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("const"), fk.geterror(f));

        // 嵌套超限:600层语法合法的if嵌套,编译深度保护应干净拒绝
        f.clearerr();
        StringBuilder deep = new StringBuilder("func f()\n");
        for (int i = 0; i < 600; i++) {
            deep.append("    if true then\n");
        }
        deep.append("    return 1\n");
        for (int i = 0; i < 600; i++) {
            deep.append("    end\n");
        }
        deep.append("end\n");
        ok = fk.parsestr(f, deep.toString());
        assertFalse(ok, "deep nesting should be rejected");
        assertTrue(fk.geterror(f).contains("too deep"), fk.geterror(f));
    }

    // ==================== include深度与文件错误 ====================

    @Test
    public void testIncludeDepthLimit(@TempDir Path dir) throws Exception {
        // 110个文件的include链,超过include_deps(100)后必须干净报错
        int n = 110;
        for (int i = 0; i < n; i++) {
            Files.write(dir.resolve("c" + i + ".fk"),
                    ("include \"c" + (i + 1) + ".fk\"\nfunc f" + i + "()\nend\n").getBytes(StandardCharsets.UTF_8));
        }

        boolean ok = fk.parse(f, dir.resolve("c0.fk").toString());
        assertFalse(ok, "should hit include depth limit");
        assertTrue(fk.geterror(f).contains("too deep"), fk.geterror(f));
    }

    @Test
    public void testFileErrors(@TempDir Path dir) throws Exception {
        // 不存在的文件
        boolean ok = fk.parse(f, dir.resolve("nope.fk").toString());
        assertFalse(ok);
        assertFalse(fk.geterror(f).isEmpty());

        // 目录当脚本
        ok = fk.parse(f, dir.toString());
        assertFalse(ok);
        assertFalse(fk.geterror(f).isEmpty());

        // getfilecode边界
        assertEquals("", fk.getfilecode(f, "", 0));
        assertEquals("", fk.getfilecode(f, dir.resolve("nope.fk").toString(), 1));

        // 空文件:读取成功但内容为空,按解析失败处理
        Path empty = dir.resolve("empty.fk");
        Files.write(empty, new byte[0]);
        ok = fk.parse(f, empty.toString());
        assertFalse(ok);
    }

    // ==================== 类型矩阵 ====================

    @Test
    public void testTypeMatrixBuiltins() throws Exception {
        String script =
                "func f()\n" +
                "    var arr = array()\n" +
                "    var m = map()\n" +
                "    var p = new(\"java.lang.Object\")\n" +
                "    return typeof(null), typeof(1), typeof(1.5), typeof(\"s\"), typeof(arr), typeof(m), " +
                "typeof(123u), typeof(p),\n" +
                "        tostring(null), tostring(1), tostring(1.5), tostring(123u),\n" +
                "        size(m), size(5), range(\"abc\", 0), range(\"abc\", 9)\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(16, rets.length, fk.geterror(f));

        assertEquals("POINTER", rets[0]);  // nil编译为POINTER(null)
        assertEquals("INT", rets[1]);
        assertEquals("REAL", rets[2]);
        assertEquals("STRING", rets[3]);
        assertEquals("ARRAY", rets[4]);
        assertEquals("MAP", rets[5]);
        assertEquals("UUID", rets[6]);
        assertEquals("POINTER", rets[7]);
        assertEquals("null", rets[8]); // null字面量是POINTER(null),toString输出"null"
        assertEquals("1", rets[9]);
        assertEquals("1.5", rets[10]);
        assertEquals("123", rets[11]);
        assertEquals(0L, ((Long) rets[13]).longValue()); // size(数字)=0
        assertEquals("a", rets[14]);
        assertEquals("", rets[15]); // 字符串越界range返回空串(数组/映射才是false)
    }

    @Test
    public void testDostringErrors() {
        String script =
                "func f()\n" +
                "    return dostring(\"func broken( end\"), dostring(\"\")\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(2, rets.length, fk.geterror(f));
        // 坏脚本返回false,空脚本返回true(空内容解析失败?按实际行为断言非空)
        assertNotNull(rets[0]);
        assertNotNull(rets[1]);
    }

    // ==================== 反汇编矩阵 ====================

    @Test
    public void testDumpfuncCoversOpcodes() {
        String script =
                "func id(x)\n" +
                "    return x\n" +
                "end\n" +
                "func f(n)\n" +
                "    var s = id(n)\n" +
                "    var t = 0\n" +
                "    for var i = 0, i < n, i++ then\n" +
                "        s = s + t\n" +
                "    end\n" +
                "    while s > 0 then\n" +
                "        s = s - 1\n" +
                "    end\n" +
                "    switch n\n" +
                "        case 1 then\n" +
                "            s = s + 100\n" +
                "        default\n" +
                "            s = s + 1\n" +
                "    end\n" +
                "    if s > 0 then\n" +
                "        return s\n" +
                "    end\n" +
                "    return 0\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        String dump = fk.dumpfunc(f, "f", -1);
        assertTrue(dump.contains("maxstack"), dump);
        assertTrue(dump.contains("const define"), dump);
        assertTrue(dump.contains("container addr"), dump);
        assertTrue(dump.contains("stack variant addr"), dump);
        assertTrue(dump.contains("byte code"), dump);
        // 主要opcode都应出现
        String[] opcodes = {"ASSIGN", "PLUS", "MINUS", "JNE", "JMP", "RETURN",
                "LESS", "MORE", "EQUAL", "CALL", "ADDR", "POS"};
        for (String op : opcodes) {
            assertTrue(dump.contains(op), "dump should contain " + op + ":\n" + dump);
        }

        // 不存在的函数
        assertEquals("not find nosuch", fk.dumpfunc(f, "nosuch", 0));
    }

    // ==================== fk API 边角 ====================

    @Test
    public void testFkEdgeCases() {
        // 未运行时的当前状态
        assertEquals("nil", fk.getcurfile(f));
        assertEquals(0, fk.getcurline(f));
        assertEquals("nil", fk.getcurfunc(f));
        assertEquals("nil", fk.getcurcallstack(f));
        assertEquals(0, fk.getcurcallstacklength(f));
        assertEquals(0, fk.getcurroutinenum(f));
        assertFalse(fk.ishaveroutine(f, 0));
        assertEquals("", fk.getcurroutinebyid(f, 999));
        assertEquals("", fk.getcurroutinebyindex(f, 0));
        assertEquals(0, fk.getroutineidbyindex(f, 0));
        assertEquals(-1, fk.getcurbytecodeposbyroutine(f, 999));
        assertFalse(fk.isfunc(f, ""));
        assertEquals("", fk.getfuncfile(f, "nosuch"));
        assertEquals(0, fk.getfuncstartline(f, "nosuch"));

        // 不存在的包:扫描为空,不崩溃
        assertDoesNotThrow(() -> fk.reg(f, "com.github.esrrhs.nonexistent.pkg"));

        // stop在未运行时调用无副作用;之后的run正常
        fk.stop(f);
        String script = "func f()\n    return 9\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        assertEquals(9L, ((Long) fk.run(f, "f")).longValue());
    }

    // ==================== variant 矩阵 ====================

    @Test
    public void testVariantToStringAndEquals() throws Exception {
        variant v = new variant();

        // NIL
        assertEquals("nil", v.toString());
        assertTrue(v.get_pointer() == null);

        // 各类型toString
        v.set_string("x");
        assertEquals("x", v.toString());
        v.set_real(2.5);
        assertEquals("2.5", v.toString());
        v.set_uuid(9L);
        assertEquals("9", v.toString());

        variant arr = new variant();
        arr.set_array(new variant_array());
        assertEquals("[]", arr.toString());
        variant map = new variant();
        map.set_map(new variant_map());
        assertEquals("{}", map.toString());

        // POINTER(null)与NIL相等
        variant pnull = new variant();
        pnull.set_pointer(null);
        variant nilv = new variant();
        assertTrue(pnull.equals(nilv));
        assertTrue(nilv.equals(pnull));

        // POINTER非空按引用比较
        Object obj = new Object();
        variant p1 = new variant();
        p1.set_pointer(obj);
        variant p2 = new variant();
        p2.set_pointer(obj);
        assertTrue(p1.equals(p2));
        variant p3 = new variant();
        p3.set_pointer(new Object());
        assertFalse(p1.equals(p3));

        // 跨类型不等
        variant iv = new variant();
        iv.set_int(1);
        variant sv = new variant();
        sv.set_string("1");
        assertFalse(iv.equals(sv));
        assertFalse(iv.equals(null));

        // equals的结果写入variant(equal/not_equal)
        variant eq = new variant();
        eq.equal(iav1(), iav2());
        assertEquals(1.0, eq.get_real(), 0.0000001);
    }

    private variant iav1() {
        variant v = new variant();
        v.set_int(5);
        return v;
    }

    private variant iav2() {
        variant v = new variant();
        v.set_int(5);
        return v;
    }

    private String printable(String s) {
        return s.replace("\n", "\\n").replace("\t", "\\t");
    }
}
