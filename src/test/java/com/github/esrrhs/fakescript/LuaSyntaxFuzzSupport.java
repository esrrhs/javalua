package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.List;

/**
 * Lua 新语法的模糊测试构件：参考模型、脚本生成、求值。
 * <p>
 * 与 {@link ExprFuzzSupport}（算术表达式自身一致）、{@link StdlibFuzzSupport}
 * （标准库 vs Java 模型）并列，这边管的是<b>新加进来的 Lua 语法</b>：
 * D10 的数值 for、D13 的运行时表构造。
 * <p>
 * 为什么必须单独建一套：这两条语法是本项目里最年轻的代码，且都属于
 * 「不崩但算错」的高危类型——数值 for 错一格就是静默少跑一次，表构造错一格
 * 就是静默构造出另一个容器。而已有的表达式/标准库 oracle 一条都覆盖不到它们
 * （表达式生成器只产算术 AST，标准库生成器只从 array()/map() 起步）。
 * 换句话说：语法越新，越不能指望老 oracle 顺手兜住。
 * <p>
 * 两种 oracle 思路：
 * <ol>
 *   <li>数值 for —— <b>Java 参考模型</b>。Lua 5.4 的语义是 begin/end/step
 *       在循环开始前各求值一次，步长正时 <code>i &lt;= end</code>、为负时
 *       <code>i &gt;= end</code>。模型按这个规则独立算一遍迭代序列。</li>
 *   <li>表构造 —— <b>脱糖不变式</b>。<code>{1,2,3}</code> 与它脱糖后的
 *       <code>array(); t[0]=1; ...</code> 必须逐字节相同。参考实现就是既有
 *       的显式构造路径，因此这条 oracle 不需要自己维护容器模型。</li>
 * </ol>
 * 随机源同样是 {@link ExprFuzzSupport.Rng}，可被固定种子（进 CI）与 Jazzer
 * 的覆盖率引导两种驱动复用。
 */
final class LuaSyntaxFuzzSupport {

    private LuaSyntaxFuzzSupport() {
    }

    /* ---------- 1. 数值 for（D10）---------- */

    /**
     * 生成一次数值 for 求和，同时用 Java 算期望的「和 / 迭代次数」。
     * <p>
     * 边界与步长都用<b>整数字面量</b>，因此不涉及浮点累加漂移，模型可用 long
     * 精确复现。范围压在 [-5, 5]，迭代次数天然有界，不会触发死循环保护。
     * <p>
     * 步长**不生成 0**：Lua 对零步长直接报错，而本实现在 begin ≥ end 时会跑成
     * 死循环（已知缺陷，见 {@link #ZERO_STEP_NOTE}），一旦生成，测试要跑满
     * max_run_cmd_num 才返回，会把整个套件拖垮。零步长由专门的用例覆盖。
     */
    static final String ZERO_STEP_NOTE = "step=0 且 begin>=end 时本实现跑成死循环,"
            + "直到 run exceed max_run_cmd_num 才被掐断;Lua 的行为是报 'for' step is zero";

    static StdlibFuzzSupport.Case genLuaForCase(ExprFuzzSupport.Rng r) {
        int begin = r.nextInt(11) - 5;
        int end = r.nextInt(11) - 5;
        int step = r.nextInt(6) - 3;
        if (step == 0) {
            step = 1;
        }

        long sum = 0;
        long count = 0;
        long i = begin;
        if (step > 0) {
            while (i <= end) {
                sum += i;
                count++;
                i += step;
            }
        } else {
            while (i >= end) {
                sum += i;
                count++;
                i += step;
            }
        }

        String body = "    local s = 0\n" + "    local c = 0\n" + "    for i = " + begin + ", " + end + ", "
                + step + " do\n" + "        s = s + i\n" + "        c = c + 1\n" + "    end\n"
                + "    return tostring(s), tostring(c)\n";

        return new StdlibFuzzSupport.Case(body, sum + "|" + count);
    }

    /**
     * 变体：省略步长（默认 1）。走的是文法的另一条候选规则
     * （{@code for i = b, e do}），值得单独生成，否则随机生成永远带步长、
     * 这条候选会被覆盖率引导之外的输入漏掉。
     */
    static StdlibFuzzSupport.Case genLuaForNoStepCase(ExprFuzzSupport.Rng r) {
        int begin = r.nextInt(11) - 5;
        int end = r.nextInt(11) - 5;

        long sum = 0;
        long count = 0;
        for (long i = begin; i <= end; i++) {
            sum += i;
            count++;
        }

        String body = "    local s = 0\n" + "    local c = 0\n" + "    for i = " + begin + ", " + end + " do\n"
                + "        s = s + i\n" + "        c = c + 1\n" + "    end\n"
                + "    return tostring(s), tostring(c)\n";

        return new StdlibFuzzSupport.Case(body, sum + "|" + count);
    }

    /* ---------- 2. 运行时表构造（D13）---------- */

    /** 与 StdlibFuzzSupport.VALUES 同理：tostring 与字面量写法一致，模型侧无需格式化 */
    private static final String[] VALUES = { "0", "1", "2", "-3", "10", "1.5", "-2.5" };

    private static final String[] KEYS = { "a", "b", "k1", "x", "name" };

    /**
     * 生成一次表构造，参考结果是它脱糖后的显式构造写法。
     * <p>
     * 参考写法由<b>既有实现</b>求值得到（而不是 Java 模型），因为表构造的设计
     * 本来就是「脱糖成 array()/map() + 逐元素赋值」——拿既有路径当参照物，
     * 既是 oracle 又是契约：一旦两条路径分叉，说明脱糖写错了。
     */
    static StdlibFuzzSupport.Case genTableCase(ExprFuzzSupport.Rng r) {
        int n = r.nextInt(5); // 0..4
        // 空表按设计是数组(`local t = {}` 得 ARRAY),所以 n == 0 时不该走 map 分支,
        // 否则参考写法写成 map() 而构造写法是 array(),两边必然分叉——那是生成器
        // 自己的错,不是实现的错
        boolean map = n > 0 && r.nextBoolean();

        StringBuilder ctorItems = new StringBuilder();
        StringBuilder explicit = new StringBuilder();
        List<String> retItems = new ArrayList<>();

        if (map) {
            explicit.append("    local t = map()\n");
            List<String> used = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                // 键必须去重:重复键会互相覆盖,参考写法与构造写法若以不同顺序
                // 覆盖,结果会分叉——那是键重复的语义问题,不是脱糖问题
                String k;
                int guard = 0;
                do {
                    k = KEYS[r.nextInt(KEYS.length)];
                } while (used.contains(k) && ++guard < 16);
                if (used.contains(k)) {
                    continue;
                }
                used.add(k);
                String v = VALUES[r.nextInt(VALUES.length)];
                if (j > 0) {
                    ctorItems.append(", ");
                }
                ctorItems.append(k).append(" = ").append(v);
                explicit.append("    t[\"").append(k).append("\"] = ").append(v).append('\n');
                retItems.add("tostring(t[\"" + k + "\"])");
            }
        } else {
            explicit.append("    local t = array()\n");
            for (int j = 0; j < n; j++) {
                String v = VALUES[r.nextInt(VALUES.length)];
                if (j > 0) {
                    ctorItems.append(", ");
                }
                ctorItems.append(v);
                explicit.append("    t[").append(j).append("] = ").append(v).append('\n');
                retItems.add("tostring(t[" + j + "])");
            }
        }

        String ret = "    return typeof(t), size(t), tostring(t)"
                + (retItems.isEmpty() ? "" : ", " + String.join(", ", retItems)) + "\n";

        String ctorBody = "    local t = {" + ctorItems + "}\n" + ret;
        String explicitBody = explicit + ret;

        return new StdlibFuzzSupport.Case(ctorBody, StdlibFuzzSupport.evalBody(explicitBody));
    }

    /**
     * 变体：元素不是常量而是表达式（含函数调用）。
     * <p>
     * 常量元素走的是一条更简单的路径，表达式元素才会真的在循环外先求值再赋值；
     * 只测常量会漏掉「元素里含有函数调用/副作用」时脱糖是否仍然等价。
     */
    static StdlibFuzzSupport.Case genTableExprElemCase(ExprFuzzSupport.Rng r) {
        int n = 1 + r.nextInt(3);
        StringBuilder ctorItems = new StringBuilder();
        StringBuilder explicit = new StringBuilder("    local t = array()\n");
        StringBuilder retItems = new StringBuilder();

        for (int j = 0; j < n; j++) {
            int k = r.nextInt(4);
            String e;
            if (k == 0) {
                e = String.valueOf(r.nextInt(9) - 4);
            } else if (k == 1) {
                e = "size(\"abcd\")";
            } else if (k == 2) {
                int a = r.nextInt(6) + 1;
                int b = r.nextInt(6) + 1;
                e = a + " * " + b;
            } else {
                e = "tostring(" + r.nextInt(20) + ")";
            }
            if (j > 0) {
                ctorItems.append(", ");
            }
            ctorItems.append(e);
            explicit.append("    t[").append(j).append("] = ").append(e).append('\n');
            retItems.append(", ").append("tostring(t[").append(j).append("])");
        }

        String ret = "    return typeof(t), size(t)" + retItems + "\n";
        String ctorBody = "    local t = {" + ctorItems + "}\n" + ret;
        String explicitBody = explicit + ret;

        return new StdlibFuzzSupport.Case(ctorBody, StdlibFuzzSupport.evalBody(explicitBody));
    }

    /**
     * 变体：同一个函数里构造<b>两个</b>表。
     * <p>
     * 单独列出来是反向验证逼出来的：把临时变量名固定成常量（不再按构造点递增）
     * 之后，前面所有表构造 oracle 依然全绿——因为它们每次只造一个表，压根碰不到
     * 「两个构造共用同一个临时变量」这种串味。这条用例专门堵这个洞。
     */
    static StdlibFuzzSupport.Case genTwoTablesCase(ExprFuzzSupport.Rng r) {
        StringBuilder ctor = new StringBuilder();
        StringBuilder explicit = new StringBuilder();
        StringBuilder ret = new StringBuilder("    return ");

        for (int t = 0; t < 2; t++) {
            int n = r.nextInt(4); // 0..3
            String name = t == 0 ? "a" : "b";
            StringBuilder items = new StringBuilder();
            explicit.append("    local ").append(name).append(" = array()\n");
            for (int j = 0; j < n; j++) {
                String v = VALUES[r.nextInt(VALUES.length)];
                if (j > 0) {
                    items.append(", ");
                }
                items.append(v);
                explicit.append("    ").append(name).append('[').append(j).append("] = ").append(v).append('\n');
            }
            ctor.append("    local ").append(name).append(" = {").append(items).append("}\n");
            if (t > 0) {
                ret.append(", ");
            }
            ret.append("tostring(").append(name).append("), size(").append(name).append(")");
        }
        ret.append('\n');

        String ctorBody = ctor.toString() + ret.toString();
        String explicitBody = explicit.toString() + ret.toString();
        return new StdlibFuzzSupport.Case(ctorBody, StdlibFuzzSupport.evalBody(explicitBody));
    }

    /* ---------- 3. 表构造与数值 for 的组合 ---------- */

    /**
     * 循环体内构造表。单独列出来是因为它同时穿过两条新语法：每次迭代都要
     * 分配一个新的临时变量（{@code __fktable_N}），若计数器按函数而非按
     * 构造点递增、或临时变量被循环复用，结果就会串。
     */
    static StdlibFuzzSupport.Case genTableInLoopCase(ExprFuzzSupport.Rng r) {
        int begin = 0;
        int end = r.nextInt(5); // 0..4

        StringBuilder model = new StringBuilder();
        long total = 0;
        for (long i = begin; i <= end; i++) {
            long v0 = i;
            long v1 = i * 2;
            total += v0 + v1;
            model.append("[").append(v0).append(',').append(v1).append(",]");
        }

        String body = "    local s = 0\n" + "    local seen = \"\"\n" + "    for i = " + begin + ", " + end
                + " do\n" + "        local t = {i, i * 2}\n" + "        s = s + t[0] + t[1]\n"
                + "        seen = seen .. tostring(t)\n" + "    end\n" + "    return tostring(s), seen\n";

        return new StdlibFuzzSupport.Case(body, total + "|" + model);
    }
}
