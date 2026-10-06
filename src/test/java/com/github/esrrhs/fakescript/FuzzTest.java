package com.github.esrrhs.fakescript;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 解析与执行的模糊测试
 * <p>
 * 固定种子保证可复现;任何逃逸出错误处理机制的Throwable、任何超时都视为缺陷。<br>
 * 失败时打印可复现的输入(种子与序号)。
 */
public class FuzzTest {

    private static final long SEED = 0xF00DC0DE;

    private static final String[] SEED_SCRIPTS = {
            "func f()\n    var a = 1\n    if a > 0 then\n        return a\n    end\n    return 0\nend\n",
            "func f()\n    var m = map()\n    m[\"k\"] = array()\n    push(m[\"k\"], 1)\n    return m[\"k\"][0]\nend\n",
            "func f(n)\n    if n < 2 then\n        return n\n    end\n    return f(n - 1) + f(n - 2)\nend\n",
            "func f()\n    var j = tojson({\"a\" : 1})\n    var b = fromjson(j)\n    return b[\"a\"]\nend\n",
            "package p\nconst N = 3\nfunc f()\n    return N\nend\n",
            "func f()\n    var arr = array()\n    for var i = 0, i < 10, i++ then\n        push(arr, i * 2)\n    end\n    sort(arr)\n    return arr[5]\nend\n",
            "func f()\n    var s = \"\"\n    for var i = 0, i < 8, i++ then\n        s = format(\"$$v=$$\", i)\n    end\n    return s\nend\n",
            "struct P\n    x\n    y\nend\nfunc f()\n    var p = P()\n    p->x = 1\n    return p->y\nend\n",
    };

    private static final String[] TOKENS = {
            "func", "end", "if", "then", "else", "elseif", "while", "for", "return", "var", "break", "continue",
            "switch", "case", "default", "null", "true", "false", "yield", "sleep", "package", "include", "struct",
            "print", "array", "map", "_G", "push", "pop", "size", "sort", "keys", "values", "copy", "tojson",
            "fromjson", "new", "dostring", "dofile", "tonumber", "tostring", "tolong", "format", "yield", "0", "1",
            "-1", "3.14", "100000000000", "1u", "\"str\"", "\"unterminated", "{", "}", "[", "]", "(", ")", ",", ";",
            ":=", ":", "=", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/", "%", "&&", "||", "!", "->", ".",
            "\n", "\t", "\\", "$", "中", "é", " ", "+=", "*=", "f", "x", "123u",
            "!", "&&", "||", "is"
    };

    private String tokenSoup(Random r) {
        int n = 10 + r.nextInt(120);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(TOKENS[r.nextInt(TOKENS.length)]);
            if (r.nextInt(3) == 0) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private String charSoup(Random r) {
        int n = 1 + r.nextInt(256);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int t = r.nextInt(10);
            if (t < 6) {
                sb.append((char) (' ' + r.nextInt(90)));
            } else if (t < 8) {
                sb.append((char) r.nextInt(0x2FFF));
            } else {
                sb.append((char) r.nextInt(128));
            }
        }
        return sb.toString();
    }

    private String mutate(Random r) {
        String s = SEED_SCRIPTS[r.nextInt(SEED_SCRIPTS.length)];
        int ops = 1 + r.nextInt(3);
        for (int i = 0; i < ops; i++) {
            if (s.isEmpty()) {
                s = TOKENS[r.nextInt(TOKENS.length)];
            }
            int op = r.nextInt(5);
            if (op == 0) {
                // 截断
                s = s.substring(0, Math.max(0, r.nextInt(s.length())));
            } else if (op == 1) {
                // 插入随机token
                int pos = r.nextInt(s.length() + 1);
                s = s.substring(0, pos) + TOKENS[r.nextInt(TOKENS.length)] + s.substring(pos);
            } else if (op == 2) {
                // 复制一段拼接
                int a = r.nextInt(s.length());
                int b = Math.min(s.length(), a + 1 + r.nextInt(40));
                s = s + s.substring(a, b);
            } else if (op == 3) {
                // 拼接两个种子
                s = s + SEED_SCRIPTS[r.nextInt(SEED_SCRIPTS.length)];
            } else {
                // 随机字符替换
                if (!s.isEmpty()) {
                    int pos = r.nextInt(s.length());
                    s = s.substring(0, pos) + (char) (' ' + r.nextInt(90)) + s.substring(pos + 1);
                }
            }
        }
        return s;
    }

    private fkconfig fuzzConfig() {
        fkconfig cfg = new fkconfig();
        cfg.max_run_cmd_num = 20000;
        cfg.run_timeout_ms = 1000;
        cfg.container_max_size = 10000;
        cfg.stack_max = 1000;
        cfg.allow_dofile = false;
        cfg.new_class_white_list = new String[] {};
        return cfg;
    }

    private String printable(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && i < 2000; i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20) {
                sb.append(String.format("\\x%02x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Test
    @Timeout(300)
    public void fuzzParseAndRun() {
        Random r = new Random(SEED);
        int parsed = 0;
        int errors = 0;

        for (int i = 0; i < 10000; i++) {
            String input;
            int kind = r.nextInt(3);
            if (kind == 0) {
                input = tokenSoup(r);
            } else if (kind == 1) {
                input = mutate(r);
            } else {
                input = charSoup(r);
            }

            fake f = fk.newfake(fuzzConfig());
            fk.openbaselib(f);

            try {
                boolean ok = fk.parsestr(f, input);
                if (ok) {
                    parsed++;
                    fk.runmulti(f, "f");
                    assertFalse(f.error && fk.geterror(f).isEmpty(), "error flag set without message");
                } else {
                    errors++;
                    assertFalse(fk.geterror(f).isEmpty(), "parse failed without error message");
                }
            } catch (Throwable t) {
                fail("fuzz case " + i + " (kind " + kind + ") escaped error handling: " + t
                        + "\ninput: " + printable(input));
            }
        }

        assertTrue(parsed > 10, "fuzz should parse some valid scripts, parsed=" + parsed);
        assertTrue(errors > 10, "fuzz should reject garbage, errors=" + errors);
    }

    @Test
    @Timeout(120)
    public void fuzzJson() {
        Random r = new Random(SEED + 1);
        String pieces[] = { "{", "}", "[", "]", ",", ":", "\"", "\\", "a", "1", "-1", "1.5", "1e999", "null", "true",
                "false", " ", "\n", "\\u00e9", "\\ud800", "0.", "01", "-", "+", "00000" };

        int roundtrips = 0;
        for (int i = 0; i < 50000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + r.nextInt(30);
            for (int j = 0; j < n; j++) {
                sb.append(pieces[r.nextInt(pieces.length)]);
            }
            String input = sb.toString();

            try {
                variant v = json.parse(input);
                // 解析成功则必须能序列化回来且再解析不抛Error
                String out = json.write(v);
                json.parse(out);
                roundtrips++;
            } catch (Exception e) {
                // 合法拒绝
            } catch (Throwable t) {
                fail("json fuzz case " + i + " escaped: " + t + "\ninput: " + printable(input));
            }
        }

        assertTrue(roundtrips > 100, "json fuzz should parse some valid input, roundtrips=" + roundtrips);
    }
}
