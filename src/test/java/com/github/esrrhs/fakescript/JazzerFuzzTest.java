package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.List;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 覆盖率引导的模糊测试（Jazzer / libFuzzer）
 * <p>
 * 与 {@link SemanticFuzzTest} 的关系：那边是固定种子的随机源，可复现、进 CI；
 * 这边把随机源换成 libFuzzer 的覆盖率引导变异，能主动探索手搓随机到不了的路径。
 * <b>两边共用同一套不变式</b>，所以 Jazzer 找到的反例落盘后，回归模式会一直重放它。
 * <p>
 * 两种运行模式：
 * <pre>
 *   # 回归模式：只回放 inputs 目录中已保存的输入，行为等同普通单元测试
 *   ./mvnw test -Dtest=JazzerFuzzTest
 *
 *   # 持续模式：libFuzzer 不断变异探索，命中的输入落盘到 inputs 目录
 *   JAZZER_FUZZ=1 ./mvnw test -Dtest=JazzerFuzzTest
 * </pre>
 * 注意：Jazzer 自带的能力是「找崩溃」，而本项目最危险的缺陷是「不崩但算错」。
 * 所以这里除了崩溃检测，还把表达式的渲染不变式接了进来当 oracle——
 * 输入由 libFuzzer 负责变聪明，判断由不变式负责变严格。
 */
public class JazzerFuzzTest {

    /** 把 Jazzer 的输入流包装成表达式生成器需要的随机源 */
    private static ExprFuzzSupport.Rng rngOf(FuzzedDataProvider data) {
        return new ExprFuzzSupport.Rng() {
            @Override
            public int nextInt(int bound) {
                return bound <= 0 ? 0 : data.consumeInt(0, bound - 1);
            }

            @Override
            public boolean nextBoolean() {
                return data.consumeBoolean();
            }
        };
    }

    /**
     * 崩溃检测：任何逃逸出错误处理机制的 Throwable 都算缺陷。
     * 这是 Jazzer 的强项——它还会自动检测路径穿越、反序列化等常见漏洞模式。
     */
    @FuzzTest
    void fuzzParseAndRun(FuzzedDataProvider data) {
        String script = data.consumeRemainingAsString();
        fake f = fk.newfake(ExprFuzzSupport.fuzzConfig());
        fk.openbaselib(f);
        try {
            if (fk.parsestr(f, script)) {
                fk.runmulti(f, "f");
            }
        } catch (Throwable t) {
            throw new AssertionError("escaped: " + t + " for input " + script, t);
        }
    }

    /**
     * 语义不变式：同一个表达式 AST 的不同写法必须得到相同结果。
     * 参考写法是「带空格 + 完全括号化」，它由 AST 自身生成因而没有歧义；
     * 紧凑写法与无括号写法若与它不一致，即说明词法或优先级存在问题。
     */
    @FuzzTest
    void fuzzExpressionRenderingInvariants(FuzzedDataProvider data) {
        ExprFuzzSupport.Rng rng = rngOf(data);
        ExprFuzzSupport.Expr e = ExprFuzzSupport.genExpr(rng, 0, 0);

        String paren = e.render(true, true);
        String compact = e.render(false, false);
        String spaced = e.render(true, false);

        String rp = ExprFuzzSupport.evalExpr(paren);

        assertEquals(rp, ExprFuzzSupport.evalExpr(compact),
                "紧凑写法与完全括号化写法结果不一致 —— 词法可能吞掉了运算符"
                        + "\n  paren  (reference) =" + paren + "\n  compact            =" + compact);
        assertEquals(rp, ExprFuzzSupport.evalExpr(spaced),
                "无括号写法与完全括号化写法结果不一致 —— 优先级可能错位"
                        + "\n  paren  (reference) =" + paren + "\n  spaced             =" + spaced);
    }

    /** 常量折叠：字面量直接参与，与先赋给变量再参与，必须同结果 */
    @FuzzTest
    void fuzzLiteralVsVariableInvariants(FuzzedDataProvider data) {
        ExprFuzzSupport.Rng rng = rngOf(data);
        ExprFuzzSupport.Expr e = ExprFuzzSupport.genExpr(rng, 0, 0);

        List<ExprFuzzSupport.Leaf> leaves = new ArrayList<>();
        e.collectLeaves(leaves);
        if (leaves.isEmpty()) {
            return;
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

        assertEquals(rl, rv, "字面量直接参与与先赋给变量结果不一致 —— 常量折叠路径可能有缺陷"
                + "\n  literal (reference) =" + literalVer + "\n  variable           =" + varVer
                + "\n  assigns: " + assigns.toString().replace('\n', ';'));
    }

    /* ---------- 标准库差分 oracle ---------- */

    /**
     * 下面四个目标与 {@link SemanticFuzzTest} 的同名用例共用同一套参考模型，
     * 区别只在随机源。让 libFuzzer 去探索操作序列的组合，判断交给模型。
     */

    @FuzzTest
    void fuzzArrayOpsAgainstModel(FuzzedDataProvider data) {
        StdlibFuzzSupport.Case c = StdlibFuzzSupport.genArrayCase(rngOf(data));
        check(c, "数组操作与参考模型不一致");
    }

    @FuzzTest
    void fuzzStringOpsAgainstModel(FuzzedDataProvider data) {
        StdlibFuzzSupport.Case c = StdlibFuzzSupport.genStringCase(rngOf(data));
        check(c, "字符串操作与参考模型不一致");
    }

    @FuzzTest
    void fuzzMapOpsAgainstModel(FuzzedDataProvider data) {
        StdlibFuzzSupport.Case c = StdlibFuzzSupport.genMapCase(rngOf(data));
        check(c, "map 操作与参考模型不一致");
    }

    @FuzzTest
    void fuzzJsonRoundTrip(FuzzedDataProvider data) {
        StdlibFuzzSupport.Case c = StdlibFuzzSupport.genJsonCase(rngOf(data));
        check(c, "JSON 往返结果不守恒");
    }

    /* ---------- Lua 新语法 oracle ---------- */

    /**
     * 下面几个目标与 {@link SemanticFuzzTest} 的同名用例共用 {@link LuaSyntaxFuzzSupport}
     * 的构件。它们覆盖的是最近才加进来的语法（D10 数值 for、D13 运行时表构造）——
     * 表达式与标准库那两套老 oracle 碰不到这两块，不单独开一路就一直没人测。
     */

    @FuzzTest
    void fuzzLuaNumericForAgainstModel(FuzzedDataProvider data) {
        ExprFuzzSupport.Rng rng = rngOf(data);
        check(rng.nextBoolean() ? LuaSyntaxFuzzSupport.genLuaForCase(rng)
                : LuaSyntaxFuzzSupport.genLuaForNoStepCase(rng), "数值 for 与 Lua 参考模型不一致");
    }

    @FuzzTest
    void fuzzTableConstructorMatchesDesugaredForm(FuzzedDataProvider data) {
        ExprFuzzSupport.Rng rng = rngOf(data);
        int kind = rng.nextInt(3);
        StdlibFuzzSupport.Case c = kind == 0 ? LuaSyntaxFuzzSupport.genTableCase(rng)
                : kind == 1 ? LuaSyntaxFuzzSupport.genTableExprElemCase(rng)
                        : LuaSyntaxFuzzSupport.genTwoTablesCase(rng);
        check(c, "表构造与它脱糖后的显式构造不一致");
    }

    @FuzzTest
    void fuzzTableInLoop(FuzzedDataProvider data) {
        check(LuaSyntaxFuzzSupport.genTableInLoopCase(rngOf(data)), "循环内构造的表与参考模型不一致");
    }

    private static void check(StdlibFuzzSupport.Case c, String msg) {
        String actual = StdlibFuzzSupport.evalBody(c.body);
        assertEquals(c.expected, actual, msg + "\n  expected = " + c.expected + "\n  actual   = " + actual
                + "\n  body:\n" + c.body);
    }
}
