package com.github.esrrhs.fakescript;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准库模糊测试的共享构件：参考模型、脚本生成、求值。
 * <p>
 * 与 {@link ExprFuzzSupport} 的分工：那边管「算术表达式」，这边管「标准库」——
 * 数组、字符串、map、JSON。两者的 oracle 思路不同：表达式那边靠
 * <b>同一 AST 的多种等价写法必须同结果</b>（自身一致），这里靠
 * <b>Java 参考模型与脚本执行结果必须逐项相同</b>（外部差分）。
 * <p>
 * 为什么需要差分：数组 push/pop/insert/remove/sort、字符串 substr/find/replace
 * 这类操作即使全错也不会抛异常、不会崩，只会安静地给出错误结果。只断言
 * 「不抛 Throwable」的 fuzz 完全看不见这类缺陷。用 Java 把同样的操作序列
 * 独立实现一遍再比对，才能把它们逼出来。
 * <p>
 * 随机源同样是 {@link ExprFuzzSupport.Rng}，因此可被固定种子（进 CI）和
 * Jazzer 的覆盖率引导两种驱动复用。
 */
final class StdlibFuzzSupport {

    private StdlibFuzzSupport() {
    }

    /* ---------- 求值 ---------- */

    /** 求值成功返回结果串（用 '|' 连接多个返回值）；否则返回错误标记 */
    static String evalBody(String body) {
        fake f = fk.newfake(ExprFuzzSupport.fuzzConfig());
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
        // 用下标而非 sb.length() 判断是否加分隔符:首个返回值可能是空串,
        // 那时 StringBuilder 仍为空,靠长度判断会吞掉分隔符,把两项并成一项
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rets.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            Object r = rets[i];
            sb.append(r == null ? "null" : String.valueOf(r));
        }
        return sb.toString();
    }

    /* ---------- 取值：字面量 / 期望的 tostring / 排序用的数值 ---------- */

    /**
     * 用于数组差分的值。选这些字面量是因为它们的 tostring 与字面量写法一致
     * （INT 打印整数形态，REAL 按 Lua %.14g 打印），模型侧无需另写格式化。
     */
    private static final String[] VALUES = { "0", "1", "2", "3", "10", "-2", "1.5", "-1.5", "2.5" };

    private static String pickValue(ExprFuzzSupport.Rng r) {
        return VALUES[r.nextInt(VALUES.length)];
    }

    private static double numOf(String literal) {
        return Double.parseDouble(literal);
    }

    /* ---------- 1. 数组：随机操作序列 vs Java List 模型 ---------- */

    /**
     * 生成一串数组操作，同时用 Java 维护期望状态，返回脚本与期望结果。
     * <p>
     * 覆盖的操作语义（与 variant_array 的实现约定一致）：
     * push 追加；pop 弹尾（空数组得 nil）；insert(i,v) 把 i 夹紧到 [0,size]；
     * remove(i) 越界得 nil；sort 按 double 视图升序且稳定。
     */
    static Case genArrayCase(ExprFuzzSupport.Rng r) {
        List<String> model = new ArrayList<>();
        StringBuilder body = new StringBuilder("    var a = array()\n");
        StringBuilder expected = new StringBuilder();
        int varIdx = 0;
        List<String> recorded = new ArrayList<>();

        int nops = 2 + r.nextInt(9);
        for (int o = 0; o < nops; o++) {
            int kind = r.nextInt(5);
            if (kind == 0) {
                String v = pickValue(r);
                model.add(v);
                body.append("    push(a, ").append(v).append(")\n");
            } else if (kind == 1) {
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = pop(a)\n");
                recorded.add(model.isEmpty() ? "nil" : model.remove(model.size() - 1));
            } else if (kind == 2) {
                int i = r.nextInt(model.size() + 3) - 1;
                String v = pickValue(r);
                int ci = Math.max(0, Math.min(model.size(), i));
                model.add(ci, v);
                body.append("    insert(a, ").append(i).append(", ").append(v).append(")\n");
            } else if (kind == 3) {
                int i = r.nextInt(model.size() + 3) - 1;
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = remove(a, ").append(i).append(")\n");
                boolean inRange = i >= 0 && i < model.size();
                recorded.add(inRange ? model.remove(i) : "nil");
            } else {
                model.sort(Comparator.comparingDouble(StdlibFuzzSupport::numOf));
                body.append("    sort(a)\n");
            }
        }

        // tostring(array) 的输出形如 "[1,2,]"，逐元素后跟逗号，空槽为空格
        StringBuilder dump = new StringBuilder("[");
        for (String v : model) {
            dump.append(v).append(',');
        }
        dump.append(']');
        expected.append(dump);
        for (String s : recorded) {
            expected.append('|').append(s);
        }

        StringBuilder ret = new StringBuilder("    return tostring(a)");
        for (int i = 0; i < varIdx; i++) {
            ret.append(", tostring(q").append(i).append(")");
        }
        body.append(ret).append('\n');

        return new Case(body.toString(), expected.toString());
    }

    /* ---------- 2. 字符串：随机操作序列 vs Java String 模型 ---------- */

    private static final String[] WORDS = { "ab", "a", "", "aa", "b", "ba", "A", "  x " };

    /**
     * 生成一串字符串操作，同时用 Java String 维护期望状态。
     * <p>
     * substr 的夹紧语义与 builtin_substr 一致：起点小于 0 取 0；起点越界或
     * 长度非正得空串；终点夹到串尾。replace 的空 from 直接返回原串。
     */
    static Case genStringCase(ExprFuzzSupport.Rng r) {
        String model = "hello world";
        StringBuilder body = new StringBuilder("    var s = \"hello world\"\n");
        List<String> recorded = new ArrayList<>();
        int varIdx = 0;

        int nops = 1 + r.nextInt(6);
        for (int o = 0; o < nops; o++) {
            int kind = r.nextInt(7);
            if (kind == 0) {
                int start = r.nextInt(20) - 4;
                int len = r.nextInt(10) - 2;
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = substr(s, ").append(start).append(", ")
                        .append(len).append(")\n");
                int cs = Math.max(0, start);
                String res;
                if (cs >= model.length() || len <= 0) {
                    res = "";
                } else {
                    res = model.substring(cs, Math.min(model.length(), cs + len));
                }
                recorded.add(res);
            } else if (kind == 1) {
                String sub = WORDS[r.nextInt(WORDS.length)];
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = find(s, \"").append(sub).append("\")\n");
                recorded.add(String.valueOf(model.indexOf(sub)));
            } else if (kind == 2) {
                body.append("    s = upper(s)\n");
                model = model.toUpperCase();
            } else if (kind == 3) {
                body.append("    s = lower(s)\n");
                model = model.toLowerCase();
            } else if (kind == 4) {
                body.append("    s = trim(s)\n");
                model = model.trim();
            } else if (kind == 5) {
                String from = WORDS[r.nextInt(WORDS.length)];
                String to = WORDS[r.nextInt(WORDS.length)];
                body.append("    s = replace(s, \"").append(from).append("\", \"").append(to).append("\")\n");
                if (!from.isEmpty()) {
                    model = model.replace(from, to);
                }
            } else {
                String suf = WORDS[r.nextInt(WORDS.length)];
                body.append("    s = s .. \"").append(suf).append("\"\n");
                model = model + suf;
            }
        }

        StringBuilder expected = new StringBuilder(model).append('|').append(model.length());
        for (String s : recorded) {
            expected.append('|').append(s);
        }

        StringBuilder ret = new StringBuilder("    return tostring(s), size(s)");
        for (int i = 0; i < varIdx; i++) {
            ret.append(", tostring(q").append(i).append(")");
        }
        body.append(ret).append('\n');

        return new Case(body.toString(), expected.toString());
    }

    /* ---------- 3. map：随机操作序列 vs Java LinkedHashMap 模型 ---------- */

    /**
     * 生成一串 map 操作。
     * <p>
     * 模型必须复刻一条反直觉的语义：<b>读不存在的键会把该键插进去</b>。
     * 解释器取容器元素走的是 con_map_get（interpreter.java），它为嵌套赋值
     * 如 m["a"]["b"]=1 提供自动创建，代价是纯粹的读取也有副作用。这不是
     * 缺陷而是既有设计，但任何改动都必须同步更新这里的模型。
     */
    static Case genMapCase(ExprFuzzSupport.Rng r) {
        LinkedHashMap<String, String> model = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder("    var m = map()\n");
        List<String> recorded = new ArrayList<>();
        int varIdx = 0;

        int nops = 2 + r.nextInt(8);
        for (int o = 0; o < nops; o++) {
            long k = r.nextInt(6);
            int kind = r.nextInt(3);
            if (kind == 0) {
                long v = r.nextInt(50);
                model.put(String.valueOf(k), String.valueOf(v));
                body.append("    m[").append(k).append("] = ").append(v).append('\n');
            } else if (kind == 1) {
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = m[").append(k).append("]\n");
                String got = model.get(String.valueOf(k));
                if (got == null) {
                    got = "nil";
                    model.put(String.valueOf(k), got);
                }
                recorded.add(got);
            } else {
                String vn = "q" + (varIdx++);
                body.append("    var ").append(vn).append(" = size(m)\n");
                recorded.add(String.valueOf(model.size()));
            }
        }

        // keys/values 走 HashMap 遍历序，顺序不可断言，只能断言个数
        StringBuilder expected = new StringBuilder(String.valueOf(model.size())).append('|')
                .append(model.size()).append('|').append(model.size());
        for (String s : recorded) {
            expected.append('|').append(s);
        }

        StringBuilder ret = new StringBuilder("    return size(m), size(keys(m)), size(values(m))");
        for (int i = 0; i < varIdx; i++) {
            ret.append(", tostring(q").append(i).append(")");
        }
        body.append(ret).append('\n');

        return new Case(body.toString(), expected.toString());
    }

    /* ---------- 4. JSON 往返 ---------- */

    private static final String[] JSON_SCALARS = { "0", "1", "-1", "10", "1.5", "-2.25", "100000",
            "\"abc\"", "\"\"", "\"a b\"" };

    /**
     * 生成一次 tojson -> fromjson 往返。
     * <p>
     * 只生成标量与数组：map 的 tostring 是 HashMap 遍历序，多键时顺序不确定，
     * 无法作为断言依据。数组顺序确定，可以断言「往返后逐元素相同」。
     */
    static Case genJsonCase(ExprFuzzSupport.Rng r) {
        int kind = r.nextInt(3);
        if (kind == 2) {
            // 数组往返：顺序确定,可断言逐元素相同
            int n = 1 + r.nextInt(3);
            List<String> elems = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                elems.add(JSON_SCALARS[r.nextInt(JSON_SCALARS.length)]);
            }
            StringBuilder pushes = new StringBuilder();
            StringBuilder dump = new StringBuilder("[");
            for (String e : elems) {
                pushes.append("    push(__v, ").append(e).append(")\n");
                dump.append(unwrap(e)).append(',');
            }
            dump.append(']');
            String body = "    var __v = array()\n" + pushes
                    + "    var __r = fromjson(tojson(__v))\n"
                    + "    return tostring(__r), typeof(__r), size(__r)\n";
            return new Case(body, dump + "|ARRAY|" + n);
        }
        // 标量往返：含一个带引号的用例,验证转义不被破坏
        String literal = kind == 1 ? "\"a\\\"b\"" : JSON_SCALARS[r.nextInt(JSON_SCALARS.length)];
        String expected = kind == 1 ? "a\"b" : unwrap(literal);
        String type = literal.startsWith("\"") ? "STRING" : (literal.indexOf('.') >= 0 ? "REAL" : "INT");
        String body = "    var __v = " + literal + "\n"
                + "    var __r = fromjson(tojson(__v))\n"
                + "    return tostring(__r), typeof(__r)\n";
        return new Case(body, expected + "|" + type);
    }

    /** 去掉字面量的引号,得到它在脚本里的实际字符串值 */
    private static String unwrap(String literal) {
        return literal.startsWith("\"") ? literal.substring(1, literal.length() - 1) : literal;
    }

    /* ---------- 用例容器 ---------- */

    static final class Case {
        final String body;
        final String expected;

        Case(String body, String expected) {
            this.body = body;
            this.expected = expected;
        }

        String describe(String tag, int i) {
            return tag + " case " + i + "\n  expected = " + expected + "\n  body:\n" + body;
        }
    }
}
