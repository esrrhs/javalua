package com.github.esrrhs.fakescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖率清扫:调试器剩余命令、绑定错误路径、内建剩余分支、解析错误路径
 */
public class CoverageSweepTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    // ==================== 调试器剩余命令 ====================

    @Test
    public void testDebugListAndRoutineErrors(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lr.fk");
        Files.write(file, ("func f()\n" +
                "    var i = 1\n" +
                "    return i\n" +
                "end\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(fk.parse(f, file.toString()), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        String l = s.execute("l 1");
        assertTrue(l.contains("var i"), l);

        String ir = s.execute("i r");
        assertTrue(ir.contains("f"), ir);

        s.execute("en 9");
        s.execute("dis 9");
        s.execute("d");
        s.execute("c");
        assertTrue(s.is_end());
    }

    // ==================== 绑定错误路径 ====================

    public static class Dummy {
        public int member() {
            return 1;
        }
    }

    @Test
    public void testBindMemberWithoutInstance() {
        fk.regclass(f, Dummy.class);
        // 非静态方法绑定名 = 全限定类名 + 方法名(无点分隔)
        String bindname = "com.github.esrrhs.fakescript.CoverageSweepTest$Dummymember";
        assertTrue(fk.isfunc(f, bindname), "member should be bound");

        // $无法出现在脚本标识符中,ref is null分支改为直接调用fkfunctor验证
        funcunion fu = f.fm.get_func_by_name(bindname);
        assertNotNull(fu);
        assertTrue(fu.m_haveff);
        // 无实例调用非静态绑定:fkfunctor抛"ref is null"(异常直接传播,不走seterror)
        Exception e = assertThrows(Exception.class, () -> fu.m_ff.call(f));
        assertTrue(e.getMessage().contains("ref is null"), e.getMessage());
    }

    // ==================== 内建剩余分支 ====================

    @Test
    public void testDumpfuncBuiltin() {
        String script =
                "func f()\n" +
                "    return dumpfunc(\"f\")\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object ret = fk.run(f, "f");
        assertTrue(ret instanceof String);
        assertTrue(((String) ret).contains("byte code"), (String) ret);
    }

    @Test
    public void testGetcurBuiltinsInScript() {
        String script =
                "func f()\n" +
                "    var a = 0\n" +
                "    var b = 0\n" +
                "    var c = 0\n" +
                "    var d = 0\n" +
                "    if size(getcurfile()) == 0 then\n" +
                "        a = 1\n" +
                "    end\n" +
                "    if getcurline() == 0 then\n" +
                "        b = 1\n" +
                "    end\n" +
                "    if size(getcurfunc()) > 0 then\n" +
                "        c = 1\n" +
                "    end\n" +
                "    if size(getcurcallstack()) > 0 then\n" +
                "        d = 1\n" +
                "    end\n" +
                "    return a, b, c, d\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(4, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());  // 文件名空(size==0)
        assertEquals(0L, ((Long) rets[1]).longValue());  // getcurline非0
        assertEquals(1L, ((Long) rets[2]).longValue());  // 函数名非空
        assertEquals(1L, ((Long) rets[3]).longValue());  // 调用栈非空
    }

    @Test
    public void testTonumberTolongErrorInputs() {
        // tonumber非数字字符串:NumberFormatException向上抛,脚本以错误结束
        String script =
                "func f()\n" +
                "    return tonumber(\"notnum\")\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        fk.run(f, "f");
        assertTrue(fk.error(f));
        assertTrue(fk.geterror(f).contains("notnum"), fk.geterror(f));
    }

    @Test
    public void testSplitAndSubstrEdges() {
        String script =
                "func f()\n" +
                "    var arr = split(\"abc\", \"x\")\n" +
                "    var sub = substr(\"abc\", 10, 2)\n" +
                "    var sub2 = substr(\"abc\", 1, 0)\n" +
                "    var sub3 = substr(\"abc\", -1, 2)\n" +
                "    return size(arr), arr[0], sub, sub2, sub3\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        Object[] rets = fk.runmulti(f, "f");
        assertEquals(5, rets.length, fk.geterror(f));
        assertEquals(1L, ((Long) rets[0]).longValue());
        assertEquals("abc", rets[1]);
        assertEquals("", rets[2]);
        assertEquals("", rets[3]);
        assertEquals("ab", rets[4]);
    }

    @Test
    public void testJsonWriteErrors() throws Exception {
        // POINTER(null)以外的POINTER:不可序列化
        variant v = new variant();
        v.set_pointer(new Object());
        try {
            json.write(v);
            fail("should reject pointer");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not supported"), e.getMessage());
        }

        // map键为容器:不可序列化
        variant mv = new variant();
        mv.set_map(new variant_map());
        variant kv = new variant();
        kv.set_array(new variant_array());
        try {
            variant slot = mv.get_map().con_map_get(kv);
            slot.set_int(1);
            json.write(mv);
            fail("should reject container key");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("map key"), e.getMessage());
        }

        // 解析错误:未闭合字符串、坏转义、未闭合数组、坏unicode
        String[] bad = { "\"unclosed", "\"bad\\escape\"", "[1", "{\"a\":", "\"\\uZZ\"" };
        for (String b : bad) {
            try {
                json.parse(b);
                fail("should reject: " + b);
            } catch (Exception e) {
                assertNotNull(e.getMessage());
            }
        }

        // \/转义与反斜杠转义
        variant p = json.parse("\"a\\/b\\\\c\"");
        assertEquals("a/b\\c", p.get_string());
    }

    // ==================== 解析错误路径 ====================

    @Test
    public void testParseIncludeErrors(@TempDir Path dir) throws Exception {
        // include不存在的文件
        Files.write(dir.resolve("main.fk"),
                "include \"nope.fk\"\nfunc f()\nend\n".getBytes(StandardCharsets.UTF_8));
        boolean ok = fk.parse(f, dir.resolve("main.fk").toString());
        assertFalse(ok);
        assertTrue(fk.geterror(f).contains("open nope.fk fail") || fk.geterror(f).contains("nope"), fk.geterror(f));

        // include语法错误的文件
        Files.write(dir.resolve("bad.fk"), "func broken(\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("main2.fk"),
                "include \"bad.fk\"\nfunc f()\nend\n".getBytes(StandardCharsets.UTF_8));
        f.clearerr();
        ok = fk.parse(f, dir.resolve("main2.fk").toString());
        assertFalse(ok);
        assertFalse(fk.geterror(f).isEmpty());
    }

    @Test
    public void testParserValWrappers() {
        ParserVal pv1 = new ParserVal(7);
        assertEquals(7, pv1.ival);
        ParserVal pv2 = new ParserVal("s");
        assertEquals("s", pv2.sval);
        com.github.esrrhs.fakescript.syntree.struct_pointer_node node =
                new com.github.esrrhs.fakescript.syntree.struct_pointer_node();
        ParserVal pv3 = new ParserVal(node);
        assertSame(node, pv3.obj);
    }

    @Test
    public void testProfileSampling() {
        fk.openprofile(f);
        String script =
                "func inner()\n" +
                "    return 1\n" +
                "end\n" +
                "func f()\n" +
                "    return inner()\n" +
                "end\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));
        fk.run(f, "f");
        fk.closeprofile(f);
        String dump = fk.dumpprofile(f);
        assertTrue(dump.contains("inner"), dump);
    }

    @Test
    public void testPointertoa() {
        Object obj = new Object();
        assertTrue(types.pointertoa(obj).contains("java.lang.Object"));
        assertEquals("", types.pointertoa(null));
    }
}
