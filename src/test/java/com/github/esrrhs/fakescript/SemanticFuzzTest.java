package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式语义的模糊测试（不变式驱动，固定种子）
 * <p>
 * 已有的 {@link FuzzTest} 是 crash-only 的：只断言不逃逸 Throwable、错误消息非空。
 * 这让一类「不崩但算错」的缺陷溜了过去——例如 a/b*c 曾因优先级表把运算符拆成多行
 * 声明而归约为 a/(b*c) 得 1；又如 x-1 曾因数值词法规则带 -? 被最长匹配切成
 * IDENTIFIER + NUMBER(-1) 而语法错误，偏偏 x - 1 正常。
 * <p>
 * 这里换个角度：随机构造表达式 AST，再用多种等价写法渲染成源码，要求结果守恒：
 * <ol>
 *   <li>紧凑写法 vs 运算符两侧带空格 —— 必须同结果（抓词法吞并）</li>
 *   <li>无括号写法 vs 完全括号化写法 —— 完全括号版由 AST 自身生成，天然携带
 *       正确优先级，两者同结果即证明解析器的优先级正确（抓优先级错位）</li>
 *   <li>字面量直接参与 vs 先赋给变量再参与 —— 抓常量折叠与运行时路径不一致</li>
 *   <li>整数 +/* 链的正序与逆序 —— 抓结合性相关缺陷</li>
 * </ol>
 * 不做随机 token 乱炖：那种输入绝大多数在语法层就被拒，真正执行到编译器与
 * 解释器内部的比例极低。这里生成的都是合法表达式，每一轮都走得进去。
 * <p>
 * 固定种子保证可复现，因此可以直接进 CI。想要更强的输入探索能力，看同目录的
 * JazzerFuzzTest——它复用同一套不变式，只是把随机源换成 libFuzzer 的覆盖率引导。
 */
public class SemanticFuzzTest {

    private static final long SEED = 0x5EA5A11E;

    private static final int RENDER_ROUNDS = 2000;
    private static final int VAR_ROUNDS = 1000;
    private static final int COMMUTE_ROUNDS = 800;

    /** 把固定种子的 Random 包装成生成器需要的随机源 */
    private static ExprFuzzSupport.Rng rngOf(Random r) {
        return new ExprFuzzSupport.Rng() {
            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }

            @Override
            public boolean nextBoolean() {
                return r.nextBoolean();
            }
        };
    }

    private static String nonZeroLiteral(Random r) {
        int v = r.nextInt(20) + 1;
        return r.nextBoolean() ? String.valueOf(v) : String.valueOf(-v);
    }

    /* ---------- 标准库差分不变式 ---------- */

    /* ---------- 标准库差分不变式 ---------- */

    /**
     * 下面四个用例共用一种 oracle：先用 Java 独立实现一遍同样的操作序列得到
     * 期望值，再与脚本执行结果逐项比对。断言「求值成功」本身也算断言——
     * 参考模型认为应当算出结果的输入，若实现侧报了错，同样是缺陷。
     */

    /** 数组 push/pop/insert/remove/sort 的操作序列必须与 Java List 模型逐步吻合 */
    @Test
    @Timeout(300)
    public void fuzzArrayOpsAgainstModel() {
        Random r = new Random(SEED + 3);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 300; i++) {
            StdlibFuzzSupport.Case c = StdlibFuzzSupport.genArrayCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "数组操作与参考模型不一致 —— " + c.describe("array", i) + "  actual = " + actual);
        }
    }

    /** 字符串 substr/find/upper/lower/trim/replace/.. 必须与 Java String 模型逐步吻合 */
    @Test
    @Timeout(300)
    public void fuzzStringOpsAgainstModel() {
        Random r = new Random(SEED + 4);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 300; i++) {
            StdlibFuzzSupport.Case c = StdlibFuzzSupport.genStringCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "字符串操作与参考模型不一致 —— " + c.describe("string", i) + "  actual = " + actual);
        }
    }

    /** map 赋值/读取/size/keys/values 必须与 Java LinkedHashMap 模型逐步吻合 */
    @Test
    @Timeout(300)
    public void fuzzMapOpsAgainstModel() {
        Random r = new Random(SEED + 5);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 250; i++) {
            StdlibFuzzSupport.Case c = StdlibFuzzSupport.genMapCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "map 操作与参考模型不一致 —— " + c.describe("map", i) + "  actual = " + actual);
        }
    }

    /** tojson -> fromjson 往返必须是恒等变换（值与类型都不许变） */
    @Test
    @Timeout(300)
    public void fuzzJsonRoundTrip() {
        Random r = new Random(SEED + 6);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 200; i++) {
            StdlibFuzzSupport.Case c = StdlibFuzzSupport.genJsonCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "JSON 往返结果不守恒 —— " + c.describe("json", i) + "  actual = " + actual);
        }
    }

    /* ---------- Lua 新语法不变式 ---------- */

    /**
     * 下面几条覆盖的是最近才加进来的语法：D10 的数值 for、D13 的运行时表构造。
     * 已有的表达式/标准库 oracle 碰不到它们，所以这里单独建了一套，构件在
     * {@link LuaSyntaxFuzzSupport}。
     */

    /** 数值 for 的迭代序列必须与 Lua 5.4 的参考模型逐步吻合（带步长写法） */
    @Test
    @Timeout(300)
    public void fuzzLuaNumericForAgainstModel() {
        Random r = new Random(SEED + 7);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 400; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genLuaForCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "数值 for 与 Lua 参考模型不一致 —— " + c.describe("lua-for", i) + "  actual = " + actual);
        }
    }

    /** 省略步长（默认 1）走的是另一条文法候选，单独覆盖 */
    @Test
    @Timeout(300)
    public void fuzzLuaNumericForNoStepAgainstModel() {
        Random r = new Random(SEED + 8);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 300; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genLuaForNoStepCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "省略步长的数值 for 与参考模型不一致 —— " + c.describe("lua-for-nostep", i) + "  actual = " + actual);
        }
    }

    /** 表构造必须与它脱糖后的显式构造逐字节相同 */
    @Test
    @Timeout(300)
    public void fuzzTableConstructorMatchesDesugaredForm() {
        Random r = new Random(SEED + 9);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 300; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genTableCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "表构造与显式构造结果不一致 —— 脱糖可能写错了\n" + c.describe("table", i) + "  actual = " + actual);
        }
    }

    /** 元素为表达式（含函数调用）时，表构造仍必须与显式构造一致 */
    @Test
    @Timeout(300)
    public void fuzzTableExprElementsMatchDesugaredForm() {
        Random r = new Random(SEED + 10);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 250; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genTableExprElemCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "含表达式元素的表构造与显式构造不一致 —— " + c.describe("table-expr", i) + "  actual = " + actual);
        }
    }

    /**
     * 同一函数里两个表构造不能共用临时变量。
     * <p>
     * 这条是反向验证逼出来的：把临时变量名改成常量后，其他表构造 oracle 全绿，
     * 只有这条会红——先有能红的用例，前面的覆盖才有意义。
     */
    @Test
    @Timeout(300)
    public void fuzzTwoTablesDoNotCollide() {
        Random r = new Random(SEED + 12);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 250; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genTwoTablesCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "同一函数里两个表构造互相串了 —— " + c.describe("two-tables", i) + "  actual = " + actual);
        }
    }

    /** 循环体内构造表：每次迭代必须是全新的容器，不能互相串 */
    @Test
    @Timeout(300)
    public void fuzzTableInLoop() {
        Random r = new Random(SEED + 11);
        ExprFuzzSupport.Rng rng = rngOf(r);
        for (int i = 0; i < 200; i++) {
            StdlibFuzzSupport.Case c = LuaSyntaxFuzzSupport.genTableInLoopCase(rng);
            String actual = StdlibFuzzSupport.evalBody(c.body);
            assertEquals(c.expected, actual,
                    "循环内构造的表与参考模型不一致 —— " + c.describe("table-in-loop", i) + "  actual = " + actual);
        }
    }

    /* ---------- 不变式 ---------- */

    @Test
    @Timeout(300)
    public void fuzzExpressionRenderingInvariants() {
        Random r = new Random(SEED);
        ExprFuzzSupport.Rng rng = rngOf(r);
        int valued = 0;
        int errored = 0;

        for (int i = 0; i < RENDER_ROUNDS; i++) {
            ExprFuzzSupport.Expr e = ExprFuzzSupport.genExpr(rng, 0, 0);

            // 参考写法：带空格 + 完全括号化，是唯一没有歧义的表达形式
            String paren = e.render(true, true);
            String compact = e.render(false, false);
            String spaced = e.render(true, false);

            String rp = ExprFuzzSupport.evalExpr(paren);
            String rc = ExprFuzzSupport.evalExpr(compact);
            String rs = ExprFuzzSupport.evalExpr(spaced);

            String ctx = "case " + i + "\n  paren  (reference) =" + paren + "\n  compact            ="
                    + compact + "\n  spaced             =" + spaced;

            assertEquals(rp, rc, "无括号写法与完全括号化写法结果不一致 —— 优先级可能错位\n" + ctx);
            assertEquals(rp, rs, "紧凑写法与参考写法结果不一致\n" + ctx);

            if ("ERROR".equals(rp)) {
                errored++;
            } else {
                valued++;
            }
        }

        assertTrue(valued > 300, "应有大量表达式成功求值, valued=" + valued);
        assertTrue(errored > 0, "应覆盖到运行时错误路径(整数零除), errored=" + errored);
    }

    @Test
    @Timeout(300)
    public void fuzzLiteralVsVariableInvariants() {
        Random r = new Random(SEED + 1);
        ExprFuzzSupport.Rng rng = rngOf(r);
        int checked = 0;

        for (int i = 0; i < VAR_ROUNDS; i++) {
            ExprFuzzSupport.Expr e = ExprFuzzSupport.genExpr(rng, 0, 0);
            List<ExprFuzzSupport.Leaf> leaves = new ArrayList<>();
            e.collectLeaves(leaves);
            if (leaves.isEmpty()) {
                continue;
            }

            String literalVer = e.render(true, true);
            String rl = ExprFuzzSupport.evalExpr(literalVer);

            StringBuilder assigns = new StringBuilder();
            for (int j = 0; j < leaves.size(); j++) {
                assigns.append("    local v").append(j).append(" = ").append(leaves.get(j).text).append('\n');
            }
            String varVer = ExprFuzzSupport.renderWithVars(e);
            String rv = ExprFuzzSupport.evalBody(
                    assigns + "    return typeof(" + varVer + "), tostring(" + varVer + ")");

            String ctx = "case " + i + "\n  literal (reference) =" + literalVer + "\n  variable           ="
                    + varVer + "\n  assigns: " + assigns.toString().replace('\n', ';');

            assertEquals(rl, rv, "字面量直接参与与先赋给变量结果不一致 —— 常量折叠路径可能有缺陷\n" + ctx);
            checked++;
        }

        assertTrue(checked > 800, "checked=" + checked);
    }

    /**
     * 加法与乘法对整数满足交换律与结合律。这里只用整数字面量——浮点加法不
     * 满足结合律，混进去会产出假阳性。
     */
    @Test
    @Timeout(120)
    public void fuzzIntegerCommutativity() {
        Random r = new Random(SEED + 2);
        String[] ops = { "+", "*" };
        int checked = 0;

        for (int i = 0; i < COMMUTE_ROUNDS; i++) {
            String op = ops[r.nextInt(ops.length)];
            int n = 2 + r.nextInt(3);

            List<String> terms = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                terms.add(nonZeroLiteral(r));
            }
            String forward = String.join(" " + op + " ", terms);

            List<String> reversed = new ArrayList<>(terms);
            Collections.reverse(reversed);
            String backward = String.join(" " + op + " ", reversed);

            String rf = ExprFuzzSupport.evalExpr(forward);
            String rb = ExprFuzzSupport.evalExpr(backward);

            String ctx = "case " + i + "\n  forward  (reference) =" + forward + "\n  backward            ="
                    + backward;
            assertEquals(rf, rb, "整数 " + op + " 链逆序后结果不一致\n" + ctx);
            checked++;
        }

        assertTrue(checked > 700, "checked=" + checked);
    }
}
