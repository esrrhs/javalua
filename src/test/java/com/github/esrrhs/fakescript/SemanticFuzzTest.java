package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式语义的模糊测试（不变式驱动）
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
 * 固定种子保证可复现；失败时打印三种写法。
 */
public class SemanticFuzzTest {

    private static final long SEED = 0x5EA5A11E;

    /* 深度上限，避免表达式爆炸 */
    private static final int MAX_DEPTH = 3;
    private static final int RENDER_ROUNDS = 2000;
    private static final int VAR_ROUNDS = 1000;
    private static final int COMMUTE_ROUNDS = 800;

    /* ---------- 表达式 AST ---------- */

    private abstract static class Expr {
        abstract String render(boolean spaced, boolean fullParen);

        abstract void collectLeaves(List<Leaf> out);
    }

    private static final class Leaf extends Expr {
        final String text;

        Leaf(String text) {
            this.text = text;
        }

        @Override
        String render(boolean spaced, boolean fullParen) {
            return text;
        }

        @Override
        void collectLeaves(List<Leaf> out) {
            out.add(this);
        }
    }

    private static final class Bin extends Expr {
        final String op;
        final Expr l;
        final Expr r;

        Bin(String op, Expr l, Expr r) {
            this.op = op;
            this.l = l;
            this.r = r;
        }

        @Override
        String render(boolean spaced, boolean fullParen) {
            String body = glue(l.render(spaced, fullParen), spaced ? " " + op + " " : op,
                    r.render(spaced, fullParen));
            return fullParen ? "(" + body + ")" : body;
        }

        @Override
        void collectLeaves(List<Leaf> out) {
            l.collectLeaves(out);
            r.collectLeaves(out);
        }
    }

    private static final class Neg extends Expr {
        final Expr e;

        Neg(Expr e) {
            this.e = e;
        }

        @Override
        String render(boolean spaced, boolean fullParen) {
            String body = glue("-", spaced ? " " : "", e.render(spaced, fullParen));
            return fullParen ? "(" + body + ")" : body;
        }

        @Override
        void collectLeaves(List<Leaf> out) {
            e.collectLeaves(out);
        }
    }

    /**
     * 拼接片段。若拼接处会形成 {@code --}，插入一个空格——那是行注释起始符，
     * 与 Lua 的词法一致（Lua 里也必须写 1 - -1）。这不是被测对象的缺陷，
     * 而是生成器需要规避的写法歧义。
     */
    private static String glue(String left, String sep, String right) {
        StringBuilder sb = new StringBuilder(left).append(sep);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-' && right.startsWith("-")) {
            sb.append(' ');
        }
        return sb.append(right).toString();
    }

    /* ---------- 随机生成 ---------- */

    /**
     * 运算符优先级档位（低 -> 高）：加减一档，乘除 /*
     * // % 另一档。
     * 与 YYParser.y 的 %left 声明应保持同构。
     */
    private static int prec(String op) {
        return "+".equals(op) || "-".equals(op) ? 1 : 2;
    }

    private static final String[] ALL_OPS = { "+", "-", "*", "/", "//", "%" };

    /**
     * 按优先级约束生成表达式树。
     * <p>
     * 这一点很关键：随机生成的树形必须是「无括号写法自然解析得出来的形状」，
     * 否则去掉括号后语义就变了，不变式本身不成立。对左结合的二元运算，
     * 约束是——左子树顶层运算符优先级 >= 本节点，右子树顶层 > 本节点。
     * <p>
     * 该约束不会削弱检测能力：a/b*c（根为 *、左子树为 /，同档）与
     * a-b+c（根为 +、左子树为 -，同档）都仍在生成范围内，
     * 恰好是曾经出错的那两种写法。
     */
    private Expr genExpr(Random r, int depth, int minPrec) {
        if (depth >= MAX_DEPTH || r.nextInt(100) < 25) {
            return genOperand(r, depth);
        }
        List<String> cands = new ArrayList<>();
        for (String o : ALL_OPS) {
            if (prec(o) >= minPrec) {
                cands.add(o);
            }
        }
        if (cands.isEmpty()) {
            return genOperand(r, depth);
        }
        String op = cands.get(r.nextInt(cands.size()));
        int p = prec(op);
        Expr left = genExpr(r, depth + 1, p);
        Expr right = genRight(r, depth + 1, p + 1, op);
        return new Bin(op, left, right);
    }

    /**
     * // 与 % 的整数零除数是运行时错误。多数时候给非零右操作数，让更多轮次
     * 真正算出值；偶尔放零以覆盖错误路径。/ 除零得到 inf，不需要规避。
     */
    private Expr genRight(Random r, int depth, int minPrec, String op) {
        boolean zeroThrows = "//".equals(op) || "%".equals(op);
        if (zeroThrows && r.nextInt(100) < 90) {
            return new Leaf(nonZeroLiteral(r));
        }
        return genExpr(r, depth, minPrec);
    }

    /** 一元负号优先级最高，可出现在任何需要操作数的位置 */
    private Expr genOperand(Random r, int depth) {
        if (depth < MAX_DEPTH && r.nextInt(100) < 18) {
            return new Neg(genLeaf(r));
        }
        return genLeaf(r);
    }

    private Expr genLeaf(Random r) {
        int kind = r.nextInt(10);
        if (kind < 6) {
            return new Leaf(String.valueOf(r.nextInt(101) - 50));
        } else if (kind < 8) {
            return new Leaf(nonZeroLiteral(r));
        } else {
            return r.nextBoolean()
                    ? new Leaf(String.format("%d.5", r.nextInt(20) - 10))
                    : new Leaf(String.format("%de2", r.nextInt(9) + 1));
        }
    }

    private String nonZeroLiteral(Random r) {
        int v = r.nextInt(20) + 1;
        return r.nextBoolean() ? String.valueOf(v) : String.valueOf(-v);
    }

    /* ---------- 求值 ---------- */

    private fkconfig fuzzConfig() {
        fkconfig cfg = new fkconfig();
        cfg.max_run_cmd_num = 20000;
        cfg.run_timeout_ms = 2000;
        cfg.allow_dofile = false;
        return cfg;
    }

    /**
     * 求值并返回归一化的结果字符串。带上 typeof 是为了把 INT/REAL 的类型差异
     * 也纳入比较——1+1 必须是 INT、1/1 必须是 REAL，只比值会漏掉这类缺陷。
     */
    private String evalBody(String body) {
        fake f = fk.newfake(fuzzConfig());
        fk.openbaselib(f);
        String script = "func f()\n" + body + "\nend\n";
        if (!fk.parsestr(f, script)) {
            return "PARSE_FAIL";
        }
        Object[] rets;
        try {
            rets = fk.runmulti(f, "f");
        } catch (Throwable t) {
            return "THROW:" + t.getClass().getSimpleName();
        }
        if (fk.error(f)) {
            return "ERROR";
        }
        return rets[0] + "|" + rets[1];
    }

    private String evalExpr(String expr) {
        return evalBody("    return typeof(" + expr + "), tostring(" + expr + ")");
    }

    /* ---------- 不变式 ---------- */

    @Test
    @Timeout(300)
    public void fuzzExpressionRenderingInvariants() {
        Random r = new Random(SEED);
        int valued = 0;
        int errored = 0;

        for (int i = 0; i < RENDER_ROUNDS; i++) {
            Expr e = genExpr(r, 0, 0);

            // 参考写法：带空格 + 完全括号化，是唯一没有歧义的表达形式
            String paren = e.render(true, true);
            String compact = e.render(false, false);
            String spaced = e.render(true, false);

            String rp = evalExpr(paren);
            String rc = evalExpr(compact);
            String rs = evalExpr(spaced);

            String ctx = "case " + i + "\n  paren  (reference) =" + paren + "\n  compact            ="
                    + compact + "\n  spaced             =" + spaced;

            assertEquals(rp, rc, "无括号写法与完全括号化写法结果不一致 —— 优先级可能错位\n" + ctx);
            assertEquals(rp, rs, "紧凑写法(部分)与参考写法结果不一致\n" + ctx);

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
        int checked = 0;

        for (int i = 0; i < VAR_ROUNDS; i++) {
            Expr e = genExpr(r, 0, 0);
            List<Leaf> leaves = new ArrayList<>();
            e.collectLeaves(leaves);
            if (leaves.isEmpty()) {
                continue;
            }

            String literalVer = e.render(true, true);
            String rl = evalExpr(literalVer);

            StringBuilder assigns = new StringBuilder();
            List<String> values = new ArrayList<>();
            for (Leaf leaf : leaves) {
                values.add(leaf.text);
            }
            for (int j = 0; j < values.size(); j++) {
                assigns.append("    var v").append(j).append(" = ").append(values.get(j)).append('\n');
            }
            String varVer = renderWithVars(e);
            String rv = evalBody(assigns + "    return typeof(" + varVer + "), tostring(" + varVer + ")");

            String ctx = "case " + i + "\n  literal (reference) =" + literalVer + "\n  variable           ="
                    + varVer + "\n  assigns: " + assigns.toString().replace('\n', ';');

            assertEquals(rl, rv, "字面量直接参与与先赋给变量结果不一致 —— 常量折叠路径可能有缺陷\n" + ctx);
            checked++;
        }

        assertTrue(checked > 800, "checked=" + checked);
    }

    private String renderWithVars(Expr e) {
        return renderWithVars(e, new int[] { 0 });
    }

    private String renderWithVars(Expr e, int[] idx) {
        if (e instanceof Leaf) {
            return "v" + (idx[0]++);
        }
        if (e instanceof Neg) {
            return glue("-", "", renderWithVars(((Neg) e).e, idx));
        }
        Bin b = (Bin) e;
        return "(" + renderWithVars(b.l, idx) + " " + b.op + " " + renderWithVars(b.r, idx) + ")";
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
            // 减去是按结合的,- 不做逆序比较
            String forward = String.join(" " + op + " ", terms);

            List<String> reversed = new ArrayList<>(terms);
            Collections.reverse(reversed);
            String backward = String.join(" " + op + " ", reversed);

            String rf = evalExpr(forward);
            String rb = evalExpr(backward);

            String ctx = "case " + i + "\n  forward  (reference) =" + forward + "\n  backward            ="
                    + backward;
            assertEquals(rf, rb, "整数 " + op + " 链逆序后结果不一致\n" + ctx);
            checked++;
        }

        assertTrue(checked > 700, "checked=" + checked);
    }
}
