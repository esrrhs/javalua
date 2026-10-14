package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式模糊测试的共享构件：AST、渲染、求值。
 * <p>
 * 被两处复用，区别只在「随机源」从哪来：
 * <ul>
 *   <li>{@link SemanticFuzzTest} —— 固定种子的 {@link java.util.Random}，
 *       每次跑同样的用例，进 CI 当回归测试</li>
 *   <li>JazzerFuzzTest —— Jazzer 的 FuzzedDataProvider，由 libFuzzer 做
 *       覆盖率引导的变异，能主动探索到手搓随机到不了的路径</li>
 * </ul>
 * 两边共用同一套不变式，因此 Jazzer 找到的反例可以直接落盘成回归用例。
 */
final class ExprFuzzSupport {

    private ExprFuzzSupport() {
    }

    /** 随机源抽象，便于在固定种子与覆盖率引导的输入之间切换 */
    interface Rng {
        int nextInt(int bound);

        boolean nextBoolean();
    }

    /* ---------- 表达式 AST ---------- */

    abstract static class Expr {
        abstract String render(boolean spaced, boolean fullParen);

        abstract void collectLeaves(List<Leaf> out);
    }

    static final class Leaf extends Expr {
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

    static final class Bin extends Expr {
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

    static final class Neg extends Expr {
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
     * 拼接片段。若拼接处会形成连续的减号，插入一个空格——那是行注释起始符，
     * 与 Lua 的词法一致（Lua 里同样必须写 1 - -1）。这是生成器要规避的写法
     * 歧义，不是被测对象的缺陷。
     */
    static String glue(String left, String sep, String right) {
        StringBuilder sb = new StringBuilder(left).append(sep);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-' && right.startsWith("-")) {
            sb.append(' ');
        }
        return sb.append(right).toString();
    }

    /* ---------- 随机生成 ---------- */

    static final int MAX_DEPTH = 3;

    /** 运算符优先级档位（低 -> 高）：加减一档，乘除与取模另一档 */
    static int prec(String op) {
        return "+".equals(op) || "-".equals(op) ? 1 : 2;
    }

    static final String[] ALL_OPS = { "+", "-", "*", "/", "//", "%" };

    /**
     * 按优先级约束生成表达式树。
     * <p>
     * 这一点很关键：生成的树必须是「无括号写法自然解析得出的形状」，否则去掉
     * 括号后语义就变了，不变式本身不成立。对左结合的二元运算，约束是——
     * 左子树顶层运算符优先级 >= 本节点，右子树顶层 > 本节点。
     * <p>
     * 该约束不削弱检测能力：a/b*c（根为 *、左子树为 /，同档）与 a-b+c 都仍在
     * 生成范围内，恰好是曾经出错的那两种写法。
     */
    static Expr genExpr(Rng r, int depth, int minPrec) {
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
     * floor 整除与取模的整数零除数是运行时错误。多数时候给非零右操作数，让更多
     * 轮次真正算出值；偶尔放零以覆盖错误路径。普通除法除零得 inf，无需规避。
     */
    private static Expr genRight(Rng r, int depth, int minPrec, String op) {
        boolean zeroThrows = "//".equals(op) || "%".equals(op);
        if (zeroThrows && r.nextInt(100) < 90) {
            return new Leaf(nonZeroLiteral(r));
        }
        return genExpr(r, depth, minPrec);
    }

    /** 一元负号优先级最高，可出现在任何需要操作数的位置 */
    private static Expr genOperand(Rng r, int depth) {
        if (depth < MAX_DEPTH && r.nextInt(100) < 18) {
            return new Neg(genLeaf(r));
        }
        return genLeaf(r);
    }

    private static Expr genLeaf(Rng r) {
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

    private static String nonZeroLiteral(Rng r) {
        int v = r.nextInt(20) + 1;
        return r.nextBoolean() ? String.valueOf(v) : String.valueOf(-v);
    }

    /* ---------- 求值 ---------- */

    static fkconfig fuzzConfig() {
        fkconfig cfg = new fkconfig();
        cfg.max_run_cmd_num = 20000;
        cfg.run_timeout_ms = 2000;
        cfg.allow_dofile = false;
        return cfg;
    }

    /**
     * 求值并返回归一化的结果字符串。带上 typeof 是为了把 INT/REAL 的类型差异也
     * 纳入比较——1+1 必须是 INT、1/1 必须是 REAL，只比值会漏掉这类缺陷。
     */
    static String evalBody(String body) {
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

    static String evalExpr(String expr) {
        return evalBody("    return typeof(" + expr + "), tostring(" + expr + ")");
    }

    /* ---------- 变量版渲染 ---------- */

    static String renderWithVars(Expr e) {
        return renderWithVars(e, new int[] { 0 });
    }

    private static String renderWithVars(Expr e, int[] idx) {
        if (e instanceof Leaf) {
            return "v" + (idx[0]++);
        }
        if (e instanceof Neg) {
            return glue("-", "", renderWithVars(((Neg) e).e, idx));
        }
        Bin b = (Bin) e;
        return "(" + renderWithVars(b.l, idx) + " " + b.op + " " + renderWithVars(b.r, idx) + ")";
    }
}
