package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 差分 fuzz —— 四道闸门里的第三道，也是唯一能抓住「不崩、但答案错」的那道。
 * <p>
 * 手写用例的弱点很明显：它们是照着实现者的理解写的，理解错的地方压根不会被
 * 覆盖到。生成器不知道我们认为 Lua 该怎样，它只会往外吐组合，
 * 于是「闭包读到的 upvalue 是哪个」「多重赋值的求值顺序」「返回值在第几层被截断」
 * 这类没有显式断言的语义会被大量随机组合反复踩到。
 * <p>
 * 两条硬约束，违反任何一条都会把测试变成麻烦制造机：
 * <ul>
 *   <li><b>必须终止。</b>只生成有界的 {@code for}（上界是字面小整数）与
 *       {@code ipairs}/<code>pairs</code>，<b>不生成</b> {@code while}、
 *       {@code repeat}、<code>goto</code>；
 *       函数调用只允许指向<b>之前</b>已定义完的函数，于是调用图是 DAG，
 *       不可能递归、也不可能死循环。</li>
 *   <li><b>必须可复现。</b>种子固定递增，不用 {@code Math.random}。
 *       失败时能靠「第几个种子」精确复现。</li>
 * </ul>
 * 出错的用例照样参与比对：两边报同样的错也是一致性的一部分，
 * 比「一边报错一边不报」更有价值。
 */
class FuzzTest {

    /**
     * 种子数量。每个种子生成一整段程序，跑两边比对。
     * <p>
     * 默认值是「CI 跑得完」的量。本地要长跑就用
     * {@code ./mvnw test -Dtest=FuzzTest -Dfuzz.seeds=20000 -Dfuzz.coSeeds=5000}
     * 把它调大——生成器是确定性的（种子递增，不用 {@code Math.random}），
     * 所以多跑只会多覆盖，不会让已有用例变得不稳。
     */
    private static final int SEEDS = Integer.getInteger("fuzz.seeds", 1500);

    /** 协程模式的种子数 */
    private static final int CO_SEEDS = Integer.getInteger("fuzz.coSeeds", 600);

    /** 协程模式的种子偏移：与常规模式的种子错开，落盘文件名不会撞车 */
    private static final int CO_SEED_BASE = 100000;

    @BeforeAll
    static void requireReference() {
        assumeTrue(ReferenceLua.available(),
                "no lua5.4 reference binary; set LUA54_BIN. found: " + ReferenceLua.describe());
    }

    @Test
    void generatedProgramsMatchReference() {
        for (int seed = 0; seed < SEEDS; seed++) {
            String src = new Gen(new Random(seed), false).program();
            String want = Diff.ref(src);
            String got = Diff.mine(src);
            if (!want.equals(got)) {
                // 落到磁盘上：断言信息里的源码被转义过，直接抄出来跑不通
                String path = dump(seed, src, want, got);
                assertEquals(want, got,
                        "seed=" + seed + "，程序已落到 " + path + "\n" + src);
            }
        }
    }

    /**
     * 同一套生成器，但主 chunk 跑在协程里，并随机撒 {@code coroutine.yield}。
     * <p>
     * 手工用例只能覆盖「我知道该怎么测」的地方；yield 会落在哪条指令的中间
     * 是生成器说了算的，所以诸如「元方法里 yield」「pcall 里 yield」
     * 「for 迭代器里 yield」这些组合，只有靠随机撒点才撞得出来——而
     * {@code luaV_finishOp} 的每个分支恰好对应其中一种。
     * <p>
     * 驱动循环必须<b>有界</b>：协程可能永远 yield 下去，两边一起挂住的话
     * 测试就再也跑不完。
     */
    @Test
    void generatedCoroutineProgramsMatchReference() {
        for (int seed = 0; seed < CO_SEEDS; seed++) {
            String src = new Gen(new Random(seed + CO_SEED_BASE), true).program();
            String want = Diff.ref(src);
            String got = Diff.mine(src);
            if (!want.equals(got)) {
                String path = dump(seed + CO_SEED_BASE, src, want, got);
                assertEquals(want, got,
                        "seed=" + (seed + CO_SEED_BASE) + "，程序已落到 " + path + "\n" + src);
            }
        }
    }

    /** 把失败的用例落盘，附一份两边输出的对照，方便直接拿去跑参考实现 */
    private static String dump(int seed, String src, String want, String got) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target/fuzz");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve("seed_" + seed + ".lua"),
                    src.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(dir.resolve("seed_" + seed + ".diff"),
                    ("--- reference ---\n" + want + "\n--- mine ---\n" + got + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return dir.resolve("seed_" + seed + ".lua").toString();
        } catch (Exception e) {
            return "(落盘失败: " + e + ")";
        }
    }

    /* ==================== 生成器 ==================== */

    /**
     * 递归下降的小生成器。
     * <p>
     * 刻意只覆盖阶段 4 关心的那几件事：局部变量与 upvalue、闭包、有界循环、
     * 变长参数、多重赋值与多重返回、表与元方法的浅层用法。不做字符串库、
     * 不做协程——那些还没有实现，生成了只会得到一堆「未实现」的假失败。
     */
    private static final class Gen {

        private final Random r;
        private final List<String> vars = new ArrayList<>();
        private final List<String> funcs = new ArrayList<>();
        private int depth = 0;
        private int tmpCounter = 0;

        /** 协程模式：撒 {@code coroutine.yield}，并把主 chunk 塞进协程里跑 */
        private final boolean co;

        Gen(Random r, boolean co) {
            this.r = r;
            this.co = co;
        }

        private int pick(int n) {
            return r.nextInt(n);
        }

        private boolean chance(int pct) {
            return r.nextInt(100) < pct;
        }

        String program() {
            StringBuilder sb = new StringBuilder();
            // 先定义几个函数（彼此只能调更早定义的，保证调用图无环）
            int nfunc = 1 + pick(3);
            for (int i = 0; i < nfunc; i++) {
                sb.append(functionDef()).append('\n');
            }
            // 再写主 chunk
            StringBuilder main = new StringBuilder();
            int n = 3 + pick(6);
            for (int i = 0; i < n; i++) {
                main.append(blockStatement(0)).append('\n');
            }
            if (co) {
                sb.append("local co = coroutine.create(function()\n")
                        .append(indent(main.toString().stripTrailing())).append('\n');
            } else {
                sb.append(main);
            }
            // 最后把还在作用域里的变量全打印出来，让「值不对」能被观察到
            sb.append("print(");
            int printed = 0;
            for (String v : vars) {
                if (printed > 0) {
                    sb.append(", ");
                }
                sb.append(v);
                printed++;
            }
            if (printed == 0) {
                sb.append("'empty'");
            }
            sb.append(")\n");
            if (co) {
                sb.append("end)\n")
                        .append("local n = 0\n")
                        .append("while coroutine.status(co) ~= 'dead' and n < 30 do\n")
                        .append("  print(coroutine.resume(co, 1))\n")
                        .append("  n = n + 1\n")
                        .append("end\n");
            }
            return sb.toString();
        }

        /** 函数定义：参数固定 + 变长参数，返回 0~3 个值 */
        private String functionDef() {
            String name = "f" + funcs.size();
            int nparams = pick(3);
            StringBuilder sb = new StringBuilder();
            sb.append("local function ").append(name).append('(');
            List<String> savedVars = new ArrayList<>(vars);
            List<String> params = new ArrayList<>();
            for (int i = 0; i < nparams; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String p = "p" + i;
                sb.append(p);
                params.add(p);
            }
            if (chance(40)) {
                if (nparams > 0) {
                    sb.append(", ");
                }
                sb.append("...");
            }
            sb.append(")\n");
            vars.clear();
            vars.addAll(params);
            // 一个被捕获的局部变量：闭包语义的核心
            String cap = "c" + funcs.size();
            sb.append("  local ").append(cap).append(" = ").append(atom()).append('\n');
            vars.add(cap);
            int n = 2 + pick(4);
            for (int i = 0; i < n; i++) {
                sb.append("  ").append(blockStatement(0)).append('\n');
            }
            // 闭包：捕获 cap，返回它
            if (chance(50)) {
                sb.append("  local ").append("k").append(funcs.size()).append(" = function()")
                        .append(" return ").append(cap).append(" end\n");
                vars.add("k" + funcs.size());
            }
            sb.append("  return ");
            int nret = pick(4);
            if (nret == 0) {
                sb.append('\n');
            } else {
                for (int i = 0; i < nret; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(expression(0));
                }
                sb.append('\n');
            }
            sb.append("end\n");
            vars.clear();
            vars.addAll(savedVars);
            vars.add(name);
            funcs.add(name);
            return sb.toString();
        }

        /** 语句（函数体内外都能用） */
        private String blockStatement(int d) {
            if (depth > 3 || d > 2) {
                return simpleStatement();
            }
            depth++;
            String s;
            int kinds = co ? 7 : 6;
            switch (pick(kinds)) {
            case 6: // 协程模式专属：在任意深度 yield 一下
                s = "coroutine.yield(" + atom() + (chance(30) ? ", " + atom() : "") + ")";
                break;
            case 0: // 有界数值 for
                s = "for i" + d + " = " + (pick(3)) + ", " + (1 + pick(4))
                        + (chance(30) ? ", 2" : "") + " do\n"
                        + indent(body(d + 1, 1 + pick(2))) + "\nend";
                break;
            case 1: // 泛型 for
                s = "for _, v" + d + " in ipairs({" + listLiteral() + "}) do\n"
                        + indent(body(d + 1, 1 + pick(2))) + "\nend";
                break;
            case 2: // if / else
                s = "if " + comparison() + " then\n"
                        + indent(body(d + 1, 1 + pick(2)))
                        + (chance(40) ? "\nelseif " + comparison() + " then\n"
                                + indent(body(d + 1, 1)) : "")
                        + (chance(40) ? "\nelse\n" + indent(body(d + 1, 1)) : "")
                        + "\nend";
                break;
            case 3: // do 块（顺带制造一层作用域）
                s = "do\n" + indent(body(d + 1, 1 + pick(2))) + "\nend";
                break;
            case 4: // pcall 包一层调用
                s = "local ok" + d + ", err" + d + " = pcall("
                        + (funcs.isEmpty() ? "function() return 1 end" : call(0))
                        + ")\nprint(ok" + d + ")";
                break;
            default:
                s = simpleStatement();
                break;
            }
            depth--;
            return s;
        }

        private String body(int d, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(blockStatement(d));
            }
            return sb.toString();
        }

        private static String indent(String s) {
            StringBuilder sb = new StringBuilder();
            for (String line : s.split("\n")) {
                sb.append("  ").append(line).append('\n');
            }
            return sb.toString().stripTrailing();
        }

        private String simpleStatement() {
            switch (pick(9)) {
            case 8: // 带元方法的表
                return metaTable();
            case 0: { // 局部声明（可能多重）
                int n = 1 + pick(3);
                StringBuilder sb = new StringBuilder("local ");
                List<String> names = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    String v = "v" + (tmpCounter++);
                    sb.append(v);
                    names.add(v);
                }
                sb.append(" = ");
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(expression(0));
                }
                vars.addAll(names);
                return sb.toString();
            }
            case 1: { // 多重赋值（交换是重点）
                if (vars.isEmpty()) {
                    return "local z" + (tmpCounter++) + " = 1";
                }
                int n = Math.min(1 + pick(3), vars.size());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(vars.get(pick(vars.size())));
                }
                sb.append(" = ");
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(expression(0));
                }
                return sb.toString();
            }
            case 2: { // 表写入
                String t = "t" + (tmpCounter++);
                vars.add(t);
                return "local " + t + " = " + tableLiteral(0) + "\n"
                        + t + "[" + keyExpr().replace("'", "\"") + "] = " + expression(1);
            }
            case 3: { // 闭包捕获当前变量
                if (vars.isEmpty()) {
                    return "local z" + (tmpCounter++) + " = 1";
                }
                String k = "k" + (tmpCounter++);
                String cap = vars.get(pick(vars.size()));
                vars.add(k);
                return "local " + k + " = function() return " + cap + " end";
            }
            case 4: { // 调用一个之前定义的函数
                if (funcs.isEmpty()) {
                    return "local z" + (tmpCounter++) + " = 1";
                }
                String lhs = "r" + (tmpCounter++);
                vars.add(lhs);
                return "local " + lhs + " = " + call(0);
            }
            case 6: { // 通过闭包写 upvalue：开放 upvalue 的写路径
                if (vars.size() < 2) {
                    return "local z" + (tmpCounter++) + " = 1";
                }
                String target = vars.get(pick(vars.size()));
                String setter = "s" + (tmpCounter++);
                vars.add(setter);
                return "local " + setter + " = function(v) " + target + " = v end\n"
                        + setter + "(" + expression(1) + ")";
            }
            case 7: { // 方法定义与调用：self 与 upvalue 混在一起
                String t = "o" + (tmpCounter++);
                vars.add(t);
                return "local " + t + " = {n = " + (pick(5)) + "}\n"
                        + "function " + t + ":m(x) return self.n + x end\n"
                        + "print(" + t + ":m(" + expression(1) + "))";
            }
            default:
                return "print(" + expression(0) + ")";
            }
        }

        private String call(int d) {
            String f = funcs.get(pick(funcs.size()));
            int n = pick(3);
            StringBuilder sb = new StringBuilder(f).append('(');
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(d > 1 ? atom() : expression(d + 1));
            }
            return sb.append(')').toString();
        }

        private String expression(int d) {
            if (d > 2) {
                return atom();
            }
            switch (pick(9)) {
            case 0:
                return atom();
            case 8: // 标准库调用
                return libCall(d);
            case 1: { // 二元算术
                if (chance(15)) {
                    return atom(); // 偶尔退化，免得表达式过长
                }
                String[] ops = { "+", "-", "*", "/", "//", "%", "^" };
                return "(" + expression(d + 1) + " " + ops[pick(ops.length)]
                        + " " + expression(d + 1) + ")";
            }
            case 2: { // 拼接
                return "(" + atom() + " .. " + atom() + ")";
            }
            case 3:
                return tableLiteral(d + 1);
            case 4:
                return funcs.isEmpty() ? atom() : call(d + 1);
            case 5: { // 变长参数个数（主 chunk 与函数里都能用）
                return "(select('#', ...))";
            }
            case 6: { // 取长度
                return "(#" + atom() + ")";
            }
            default:
                return comparison();
            }
        }

        private String comparison() {
            String[] ops = { "<", "<=", ">", ">=", "==", "~=" };
            return "(" + atom() + " " + ops[pick(ops.length)] + " " + atom() + ")";
        }

        /* ==================== 标准库 ==================== */

        /*
         * 生成器原本只碰语言本身（闭包 / upvalue / 变长参数 / 多重赋值），
         * 而阶段 10 从上游用例里挖出来的缺陷——tunpack 的溢出判断、tmove 的
         * 越界判据、tonumber 的 \0、luaG_tointerror 的 varinfo——全在<b>库</b>
         * 里。语言本身已经很干净了，继续只生成语言构造等于在同一块地里反复锄。
         * 所以这里让表达式也能长出库调用，参数照样是随机的：类型不对、下标越界、
         * 进制越界这些分支会被大量组合踩到。
         */

        /** 不放在字符串里的格式串，避免转义把生成结果弄成语法错误 */
        private static final String[] FORMATS = {
                "%d", "%s", "%x", "%o", "%c", "%5d", "%-5d", "%05d", "%+d",
                "%.3f", "%e", "%g", "%q", "%%", "%d%%",
        };

        private static final String[] PATTERNS = {
                "%a+", "%d+", "%s*", "[abc]", "[^abc]", "(%a)", "(%d)(%d)",
                "^a", "a$", ".", "%.", "%w*", "[a-c]+",
        };

        private static final String[] REPLS = {
                "x", "%0", "%1", "%1%1", "", "-",
        };

        private static final String[] MATH1 = {
                "floor", "ceil", "abs", "type", "tointeger", "sqrt",
        };

        private String libCall(int d) {
            switch (pick(14)) {
            case 0:
                return "string.len(" + atom() + ")";
            case 1:
                return "string.sub(" + atom() + ", " + (pick(7) - 3)
                        + (chance(50) ? ", " + (pick(7) - 3) : "") + ")";
            case 2:
                return "string.rep(" + atom() + ", " + pick(3) + ")";
            case 3:
                return "string.byte(" + atom() + (chance(50) ? ", " + (1 + pick(4)) : "") + ")";
            case 4:
                return "string.char(" + (48 + pick(30)) + (chance(40) ? ", " + (48 + pick(30)) : "")
                        + ")";
            case 5:
                return "string.format('" + FORMATS[pick(FORMATS.length)] + "', " + atom() + ")";
            case 6:
                return "string.upper(" + atom() + ")";
            case 7:
                return "string.lower(" + atom() + ")";
            case 8:
                return "string.reverse(" + atom() + ")";
            case 9:
                return "string.find(" + atom() + ", '" + PATTERNS[pick(PATTERNS.length)] + "'"
                        + (chance(40) ? ", " + (1 + pick(4)) : "") + ")";
            case 10:
                return "string.match(" + atom() + ", '" + PATTERNS[pick(PATTERNS.length)] + "')";
            case 11:
                return "string.gsub(" + atom() + ", '" + PATTERNS[pick(PATTERNS.length)] + "', '"
                        + REPLS[pick(REPLS.length)] + "'" + (chance(40) ? ", " + pick(3) : "") + ")";
            case 12:
                return "math." + MATH1[pick(MATH1.length)] + "(" + atom() + ")";
            default:
                return "tonumber(" + atom() + (chance(45) ? ", " + (2 + pick(35)) : "") + ")";
            }
        }

        /** 挂一两个元方法的新表：元方法派发与 luaV_finishOp 的组合全靠这里踩 */
        private String metaTable() {
            String t = "m" + (tmpCounter++);
            StringBuilder mm = new StringBuilder();
            int n = 1 + pick(2);
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    mm.append(", ");
                }
                switch (pick(8)) {
                case 0:
                    mm.append("__add = function(a, b) return 7 end");
                    break;
                case 1:
                    mm.append("__concat = function(a, b) return 'C' end");
                    break;
                case 2:
                    mm.append("__len = function() return 3 end");
                    break;
                case 3:
                    mm.append("__index = function(t, k) return tostring(k) .. '!' end");
                    break;
                case 4:
                    mm.append("__call = function(s, x) return x end");
                    break;
                case 5:
                    mm.append("__lt = function(a, b) return true end");
                    break;
                case 6:
                    mm.append("__unm = function(a) return -1 end");
                    break;
                default:
                    mm.append("__tostring = function() return 'T' end");
                    break;
                }
            }
            vars.add(t);
            return "local " + t + " = setmetatable({}, {" + mm + "})";
        }

        private String keyExpr() {
            switch (pick(3)) {
            case 0:
                return String.valueOf(pick(4) + 1);
            case 1:
                return "'" + ("key" + pick(3)) + "'";
            default:
                return String.valueOf(pick(3) + 1);
            }
        }

        private String tableLiteral(int d) {
            int n = pick(4);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                if (chance(50)) {
                    // 数字键必须写成 [n] = v；n = v 是语法错误
                    String k = keyExpr();
                    sb.append(k.startsWith("'") ? k : ("[" + k + "]")).append(" = ");
                }
                sb.append(d > 1 ? atom() : expression(d + 1));
            }
            return sb.append('}').toString();
        }

        private String listLiteral() {
            int n = 1 + pick(4);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(atom());
            }
            return sb.toString();
        }

        private String atom() {
            switch (pick(12)) {
            case 9: // 无穷：tonumber / string.format('%d') / 位运算都会在这里翻车
                return pick(2) == 0 ? "(1/0)" : "(-1/0)";
            case 10: { // 数字形状的字符串：tonumber 与算术的隐式转换
                String[] ss = { "'3.5'", "'-7'", "'0x10'", "'  12  '", "'1e2'", "'abc'" };
                return ss[pick(ss.length)];
            }
            case 11: // 整数上下界：溢出那条路
                return pick(2) == 0 ? "math.maxinteger" : "math.mininteger";
            case 0:
                return String.valueOf(pick(21) - 10); // 小整数，含负数
            case 1:
                return String.valueOf(pick(21) - 10) + ".5"; // 浮点
            case 2:
                return String.valueOf(pick(5)) + ".0"; // 整值浮点
            case 3:
                return "'" + ("s" + pick(5)) + "'";
            case 4:
                return pick(2) == 0 ? "true" : "false";
            case 5:
                return "nil";
            case 6:
                return "(0/0)";
            case 7:
                return vars.isEmpty() ? "0" : vars.get(pick(vars.size()));
            default:
                return funcs.isEmpty() ? "0" : funcs.get(pick(funcs.size()));
            }
        }
    }
}
