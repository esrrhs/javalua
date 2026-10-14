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
                assigns.append("    var v").append(j).append(" = ").append(leaves.get(j).text).append('\n');
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
