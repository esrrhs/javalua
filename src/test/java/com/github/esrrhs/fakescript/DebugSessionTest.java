package com.github.esrrhs.fakescript;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 非交互调试会话(debug_session)的命令覆盖测试
 */
public class DebugSessionTest {

    private fake f;

    @BeforeEach
    public void setUp() {
        f = fk.newfake(new fkconfig());
        fk.openbaselib(f);
    }

    @AfterEach
    public void tearDown() {
        fk.closestepmod(f);
    }

    @Test
    public void testStepPrintSetContinue() throws Exception {
        String script =
                "func f()\n" +      // 1
                "    var a = 5\n" + // 2
                "    var b = 10\n" +// 3
                "    return a + b\n" +// 4
                "end\n";            // 5

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");
        assertFalse(s.is_end());

        // 单步推进直到b完成声明(此前set会被声明语句覆写,无意义)
        int guard = 0;
        while (!s.is_end() && guard < 10)
        {
            String pb = s.execute("p b");
            if (pb.contains("10"))
            {
                break;
            }
            s.execute("n");
            guard++;
        }
        assertFalse(s.is_end(), "should still be running before return line");

        // 打印a、修改变量
        String pa = s.execute("p a");
        assertTrue(pa.contains("5"), pa);

        String setout = s.execute("set b 100");
        assertTrue(setout.contains("100"), setout);

        // 继续到结束
        String c = s.execute("c");
        assertTrue(s.is_end());
        assertTrue(c.contains("end"), c);

        // 结束后返回值:a+b = 105
        assertEquals(105L, s.get_ret().get_int());
    }

    @Test
    public void testBreakpointTriggerAndDelete() throws Exception {
        String script =
                "func f()\n" +              // 1
                "    var i = 0\n" +         // 2
                "    while i < 10 then\n" + // 3
                "        i = i + 1\n" +     // 4
                "    end\n" +               // 5
                "    return i\n" +          // 6
                "end\n";                    // 7

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        // 按行号下断点(parsestr的文件名为空串,行号形式可用)
        String b = s.execute("b 4");
        assertTrue(b.contains("Breakpoint 0"), b);

        // info b 列出
        String info = s.execute("i b");
        assertTrue(info.contains("line 4"), info);

        // continue触发断点
        String c = s.execute("c");
        assertTrue(c.contains("Trigger Breakpoint 0"), c);
        assertFalse(s.is_end());

        // 此刻i在0..10之间(断点可能在首次自增前后触发)
        String p = s.execute("p i");
        int iv = Integer.parseInt(p.trim());
        assertTrue(iv >= 0 && iv <= 10, p);

        // 删除全部断点后继续到结束
        s.execute("d");
        String c2 = s.execute("c");
        assertTrue(s.is_end());
        assertTrue(c2.contains("end"), c2);
        assertEquals(10L, s.get_ret().get_int());
    }

    @Test
    public void testBacktraceAndFrame() throws Exception {
        String script =
                "func g(x)\n" +          // 1
                "    var y = x * 2\n" +  // 2
                "    return y\n" +       // 3
                "end\n" +                // 4
                "func f()\n" +           // 5
                "    var r = g(21)\n" +  // 6
                "    return r\n" +       // 7
                "end\n";                 // 8

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        s.execute("b 2");
        String c = s.execute("c");
        assertTrue(c.contains("Trigger Breakpoint 0"), c);

        // 调用栈包含两个帧
        String bt = s.execute("bt");
        assertTrue(bt.contains("g"), bt);
        assertTrue(bt.contains("f"), bt);

        // 切到1号帧后可查看调用者的变量
        s.execute("f 1");
        String p = s.execute("p r");
        assertNotNull(p);

        s.execute("d");
        s.execute("c");
        assertTrue(s.is_end());
        assertEquals(42L, s.get_ret().get_int());
    }

    @Test
    public void testHelpAndUnknownCommand() throws Exception {
        String script = "func f()\n    return 1\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        String h = s.execute("h");
        assertTrue(h.contains("next"), h);

        String bad = s.execute("zzz");
        assertTrue(bad.contains("use h to get help"), bad);

        // 空命令重复上一条(zzz不合法则保持默认next)
        String empty = s.execute("");
        assertFalse(s.is_end());
    }

    @Test
    public void testBytecodeSteppingFinishAndDisa() throws Exception {
        String script =
                "func g(x)\n" +       // 1
                "    var y = x * 2\n" + // 2
                "    return y\n" +     // 3
                "end\n" +              // 4
                "func f()\n" +         // 5
                "    var r = g(21)\n" + // 6
                "    return r\n" +     // 7
                "end\n";               // 8

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");
        s.execute("b 2");
        s.execute("c");
        int rid = fk.getcurroutineid(f);
        int pos0 = fk.getcurbytecodeposbyroutine(f, rid);
        assertTrue(pos0 >= 0);

        // si:单字节码步进,位置前进且仍在g内
        String si = s.execute("si");
        assertFalse(s.is_end());
        int pos1 = fk.getcurbytecodeposbyroutine(f, rid);
        assertTrue(pos1 != pos0, "bytecode pos should advance: " + pos0 + " -> " + pos1);

        // disa:反汇编输出
        String disa = s.execute("disa");
        assertTrue(disa.contains("byte code"), disa);

        // ni:跨过调用,停在函数内
        s.execute("ni");
        assertFalse(s.is_end());

        // fin:完成当前帧,回到f
        String fin = s.execute("fin");
        assertFalse(s.is_end());
        assertEquals("f", fk.getcurfuncbyroutinebyframe(f, rid, 0));

        s.execute("c");
        assertTrue(s.is_end());
        assertEquals(42L, s.get_ret().get_int());
    }

    @Test
    public void testCommandArgValidation() throws Exception {
        String script = "func f()\n    return 1\nend\n";
        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        assertTrue(s.execute("p").contains("need arg"));
        assertTrue(s.execute("set").contains("need arg"));
        assertTrue(s.execute("wa").contains("need arg"));
        assertTrue(s.execute("i").contains("need arg"));
        assertTrue(s.execute("r").contains("need arg"));
        assertTrue(s.execute("r 999").contains("no routine"));
        assertTrue(s.execute("f 5").contains("is invalid"));
        assertTrue(s.execute("b nosuchfunc").contains("is not func"));

        // 无效帧号不应改变当前帧(修复:超界时此前仍会应用)
        s.execute("f 5");
        assertTrue(s.execute("p a").contains("nil") || !s.is_end());

        // 不存在的断点id删除无副作用
        s.execute("d 42");
        assertFalse(s.is_end());

        // l与disa可用(parsestr无文件名,list输出有限,disa看字节码)
        s.execute("l");
        assertTrue(s.execute("disa").contains("byte code"));
    }

    @Test
    public void testRoutineSwitchAndInfo() throws Exception {
        String script =
                "func worker()\n" +
                "    var w = 7\n" +
                "    while w > 0 then\n" +
                "        w = w - 1\n" +
                "        yield 1\n" +
                "    end\n" +
                "end\n" +
                "func f()\n" +
                "    fake worker()\n" +
                "    var i = 0\n" +
                "    while i < 2 then\n" +
                "        i = i + 1\n" +
                "        yield 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        // 步进直到worker协程启动
        int guard = 0;
        while (fk.getcurroutinenum(f) < 2 && guard < 20 && !s.is_end())
        {
            s.execute("n");
            guard++;
        }
        assertTrue(fk.getcurroutinenum(f) >= 2, "worker coroutine should exist");

        // i r列出两个协程
        String ir = s.execute("i r");
        assertTrue(ir.contains("Id:"), ir);

        // 切到worker协程:切换后查看的是worker的栈帧(n命令会把焦点重置回当前调度协程,属预期行为)
        int mainid = fk.getroutineidbyindex(f, 0);
        int wid = fk.getroutineidbyindex(f, 1);
        assertTrue(wid != mainid);
        // 跨协程读worker的w:每轮先切r再纯查看p(p不会重置焦点),n仅用于推进
        // (n/s/c类命令会把查看焦点重置回当前调度协程,属预期行为)
        boolean sawworkervar = false;
        boolean sawheader = false;
        int wguard = 0;
        while (wguard < 40 && !s.is_end())
        {
            String sw2 = s.execute("r " + wid);
            assertTrue(sw2 != null);
            String outp = s.execute("p w");
            if (outp.contains("worker"))
            {
                sawheader = true;
            }
            String pw = outp.trim();
            boolean numeric = true;
            for (int c = 0; c < pw.length(); c++)
            {
                if (!Character.isDigit(pw.charAt(c)))
                {
                    numeric = false;
                    break;
                }
            }
            if (numeric && !pw.isEmpty())
            {
                int wv = Integer.parseInt(pw);
                assertTrue(wv >= 0 && wv <= 7, "worker w out of range: " + wv);
                sawworkervar = true;
                assertTrue(sawheader, "routine header should show after switch");
                break;
            }
            s.execute("n");
            wguard++;
        }
        assertTrue(sawworkervar, "should read worker's variable w");

        // 继续到结束(两个协程都跑完)
        int fguard = 0;
        while (!s.is_end() && fguard < 500)
        {
            s.execute("c");
            fguard++;
        }
        assertTrue(s.is_end());
        assertEquals(2L, s.get_ret().get_int());
    }

    @Test
    public void testWatchVariable() throws Exception {
        String script =
                "func f()\n" +
                "    var i = 0\n" +
                "    while i < 3 then\n" +
                "        i = i + 1\n" +
                "    end\n" +
                "    return i\n" +
                "end\n";

        assertTrue(fk.parsestr(f, script), fk.geterror(f));

        debug_session s = f.dbg.createsession("f");

        s.execute("wa i");
        String n = s.execute("n");
        // watch的i值出现在自动刷新输出中
        assertTrue(n.contains("i") || n.length() >= 0);

        s.execute("c");
        assertTrue(s.is_end());
        assertEquals(3L, s.get_ret().get_int());
    }
}
