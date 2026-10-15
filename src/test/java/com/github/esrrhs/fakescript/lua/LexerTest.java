package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 词法层测试。
 * <p>
 * 所有预期值都不是凭记忆写的：字面量子类型、转义结果、错误信息的完整文本
 * 都先跑 {@code lua5.4} 参考实现裁定过（见 {@code /tmp/probe.lua} 那类探针）。
 * 错误信息的<b>逐字一致</b>是硬性要求——后面阶段做两侧差分比对时，比的就是
 * 这些字符串。
 */
class LexerTest {

    private static final String SRC = "test.lua";

    private static Lexer lexer(String src) {
        return new Lexer(src.getBytes(StandardCharsets.UTF_8), SRC);
    }

    /** 扫完整个输入，返回 token 序列（不含 TK_EOS） */
    private static List<Lexer.Token> scan(String src) {
        Lexer ls = lexer(src);
        List<Lexer.Token> out = new ArrayList<>();
        ls.next();
        while (ls.t.token != Lexer.TK_EOS) {
            out.add(ls.t.copy());
            ls.next();
        }
        return out;
    }

    /** 只取一个 token */
    private static Lexer.Token first(String src) {
        List<Lexer.Token> ts = scan(src);
        assertEquals(1, ts.size(), "期望恰好一个 token: " + src);
        return ts.get(0);
    }

    private static String render(List<Lexer.Token> ts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ts.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            Lexer.Token t = ts.get(i);
            switch (t.token) {
            case Lexer.TK_NAME:
                sb.append("NAME(").append(t.s.toJavaString()).append(')');
                break;
            case Lexer.TK_STRING:
                sb.append("STR(").append(t.s.toJavaString()).append(')');
                break;
            case Lexer.TK_INT:
                sb.append("INT(").append(t.i).append(')');
                break;
            case Lexer.TK_FLT:
                sb.append("FLT(").append(t.r).append(')');
                break;
            default:
                sb.append(Lexer.tokenName(t.token));
            }
        }
        return sb.toString();
    }

    private static String render(String src) {
        return render(scan(src));
    }

    /** 断言扫描出错，且错误信息与上游逐字一致 */
    private static String errorOf(String src) {
        LuaError e = assertThrows(LuaError.class, () -> scan(src), "期望词法错误: " + src);
        return e.getMessage();
    }

    /* ---------- 基本 token ---------- */

    @Test
    void scansSimpleStatement() {
        assertEquals("'local' NAME(x) '=' INT(1)", render("local x = 1"));
    }

    @Test
    void singleCharTokensUseTheirOwnCode() {
        // 单字符 token 的值就是字符编码本身，这是整个 parser 的 switch 的基础
        assertEquals('+', first("+").token);
        assertEquals('{', first("{").token);
        assertEquals(';', first(";").token);
        assertEquals('(', first("(").token);
        assertTrue(Lexer.FIRST_RESERVED > 255, "FIRST_RESERVED 必须在单字节之外");
    }

    @Test
    void scansAllReservedWords() {
        String[] words = {
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
            "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return",
            "then", "true", "until", "while"
        };
        int[] expected = {
            Lexer.TK_AND, Lexer.TK_BREAK, Lexer.TK_DO, Lexer.TK_ELSE, Lexer.TK_ELSEIF,
            Lexer.TK_END, Lexer.TK_FALSE, Lexer.TK_FOR, Lexer.TK_FUNCTION, Lexer.TK_GOTO,
            Lexer.TK_IF, Lexer.TK_IN, Lexer.TK_LOCAL, Lexer.TK_NIL, Lexer.TK_NOT,
            Lexer.TK_OR, Lexer.TK_REPEAT, Lexer.TK_RETURN, Lexer.TK_THEN, Lexer.TK_TRUE,
            Lexer.TK_UNTIL, Lexer.TK_WHILE
        };
        assertEquals(words.length, Lexer.NUM_RESERVED);
        for (int i = 0; i < words.length; i++) {
            List<Lexer.Token> ts = scan(words[i]);
            assertEquals(1, ts.size(), words[i]);
            assertEquals(expected[i], ts.get(0).token, words[i]);
        }
    }

    @Test
    void scansOperators() {
        assertEquals("NAME(a) '//' NAME(b) '..' NAME(c) '==' NAME(d) '>=' NAME(e) "
                + "'<=' NAME(f) '~=' NAME(g) '<<' NAME(h) '>>' NAME(i)",
                render("a // b .. c == d >= e <= f ~= g << h >> i"));
    }

    @Test
    void scansDoubleColonAndSingleColon() {
        assertEquals("'::' NAME(l) '::' 'goto' NAME(l)", render("::l:: goto l"));
        assertEquals("NAME(a) ':' NAME(b)", render("a:b"));
    }

    @Test
    void distinguishesDotConcatAndDots() {
        assertEquals("NAME(a) '.' NAME(b)", render("a.b"));
        assertEquals("NAME(a) '..' NAME(b)", render("a..b"));
        assertEquals("'(' '...' ')'", render("(...)"));
        assertEquals("'(' '.' ')'", render("(.)"));
        assertEquals("INT(1) '..' INT(2)", render("1 ..2"));
    }

    /* ---------- 数字 ---------- */

    @Test
    void keepsIntegerAndFloatSubtypes() {
        // 参考实现裁定：math.type(1)=integer, math.type(1.0)=float,
        // math.type(0x10)=integer, math.type(0x1p4)=float, math.type(0x.8)=float
        Lexer.Token a = first("1");
        assertEquals(Lexer.TK_INT, a.token);
        assertEquals(1L, a.i);

        Lexer.Token b = first("1.0");
        assertEquals(Lexer.TK_FLT, b.token);
        assertEquals(1.0, b.r);

        Lexer.Token c = first("0x10");
        assertEquals(Lexer.TK_INT, c.token, "十六进制整数字面量是 integer");
        assertEquals(16L, c.i);

        Lexer.Token d = first("0x1p4");
        assertEquals(Lexer.TK_FLT, d.token, "带 p 指数的十六进制是 float");
        assertEquals(16.0, d.r);

        Lexer.Token e = first("0x.8");
        assertEquals(Lexer.TK_FLT, e.token);
        assertEquals(0.5, e.r);

        Lexer.Token f = first(".5");
        assertEquals(Lexer.TK_FLT, f.token);
        assertEquals(0.5, f.r);

        Lexer.Token g = first("1e2");
        assertEquals(Lexer.TK_FLT, g.token, "十进制指数一律是 float");
        assertEquals(100.0, g.r);

        Lexer.Token h = first("1e10");
        assertEquals(Lexer.TK_FLT, h.token);
        assertEquals(1e10, h.r);
    }

    @Test
    void scansLargeIntegers() {
        assertEquals(Long.MAX_VALUE, first("9223372036854775807").i);
        Lexer.Token t = first("9223372036854775808");
        assertEquals(Lexer.TK_FLT, t.token, "溢出整数要退化成浮点而不是报错");
        assertEquals(9.223372036854776E18, t.r);
        // 参考实现裁定：math.type(-9223372036854775808) 是 float —— 负号是独立
        // token，字面量本身 9223372036854775808 溢出整数范围，所以退化成浮点
        //（且是正的，取负发生在运行期）。这正是 Lua 要单开 math.mininteger 的原因。
        List<Lexer.Token> ts = scan("(-9223372036854775808)");
        assertEquals(Lexer.TK_FLT, ts.get(2).token);
        assertEquals(9.223372036854776E18, ts.get(2).r);

        ts = scan("(-9223372036854775807)");
        assertEquals(Lexer.TK_INT, ts.get(2).token);
        assertEquals(Long.MAX_VALUE, ts.get(2).i);
    }

    @Test
    void rejectsMalformedNumbers() {
        assertEquals(SRC + ":1: malformed number near '1a'", errorOf("1a"));
        assertEquals(SRC + ":1: malformed number near '0x'", errorOf("0x"));
        assertEquals(SRC + ":1: malformed number near '1e'", errorOf("1e"));
        assertEquals(SRC + ":1: malformed number near '1e+'", errorOf("1e+"));
    }

    @Test
    void doesNotGlueNumberAcrossMinus() {
        // 指数符号只在指数标记之后才合法，所以 3-4 必须是三个 token。
        // 注意 0xe 本身是合法的十六进制整数 14（参考实现 print(0xe-1) 得 13）
        assertEquals("INT(3) '-' INT(4)", render("3-4"));
        assertEquals("INT(14) '-' INT(1)", render("0xe-1"));
    }

    /* ---------- 短字符串 ---------- */

    @Test
    void scansShortStringsWithBothQuotes() {
        assertEquals("STR(hi)", render("'hi'"));
        assertEquals("STR(hi)", render("\"hi\""));
        assertEquals("STR(it's)", render("\"it's\""));
        assertEquals("STR(say \"hi\")", render("'say \"hi\"'"));
        assertEquals("STR()", render("''"));
    }

    @Test
    void scansSimpleEscapes() {
        assertArrayEquals(new byte[] { 7, 8, 12, 10, 13, 9, 11 },
                first("\"\\a\\b\\f\\n\\r\\t\\v\"").s.bytes);
    }

    @Test
    void scansHexEscape() {
        assertEquals("A", first("\"\\x41\"").s.toJavaString());
        LuaString t = first("\"\\x00\\xff\"").s;
        assertEquals(2, t.length());
        assertEquals(0, t.byteAt(0));
        assertEquals(0xFF, t.byteAt(1));
    }

    @Test
    void scansUtf8Escape() {
        assertEquals("A", first("\"\\u{41}\"").s.toJavaString());
        LuaString zh = first("\"\\u{4E2D}\"").s;
        assertEquals(3, zh.length(), "\\u{4E2D} 是 3 个字节");
        assertEquals("中", zh.toJavaString());
        assertEquals(4, first("\"\\u{1F600}\"").s.length());
    }

    @Test
    void zapsSpacesAfterBackslashZ() {
        assertEquals(1, first("\"\\z   x\"").s.length());
        assertEquals("x", first("\"\\z   x\"").s.toJavaString());
        assertEquals("ab", first("\"a\\z\n   b\"").s.toJavaString(), "\\z 也吞换行且不产生字符");
    }

    @Test
    void scansDecimalEscapes() {
        assertEquals("A", first("\"\\65\"").s.toJavaString());
        assertEquals("AB", first("\"\\65\\066\"").s.toJavaString());
        LuaString nul = first("\"\\0\"").s;
        assertEquals(1, nul.length());
        assertEquals(0, nul.byteAt(0));
        // 最多读三位：\000 吃掉三个 0，剩下的 '7' 是普通字符
        assertArrayEquals(new byte[] { 0, '7' }, first("\"\\0007\"").s.bytes);
    }

    @Test
    void scansEscapedNewlineAndQuotes() {
        assertArrayEquals(new byte[] { 'a', '\n', 'b' }, first("\"a\\\nb\"").s.bytes);
        assertArrayEquals(new byte[] { 'a', '\n', 'b' }, first("\"a\\\r\nb\"").s.bytes,
                "\\r\\n 续行也归一化成一个 \\n");
        assertEquals("\"", first("\"\\\"\"").s.toJavaString());
        assertEquals("\\", first("\"\\\\\"").s.toJavaString());
        assertEquals("'", first("'\\''").s.toJavaString());
    }

    @Test
    void acceptsRawBytesInLongString() {
        // 除 ']' 与换行外的全部字节（含 \0 与非法 UTF-8）都要无损通过。
        // 换行要排除：长字符串会把 \n / \r / \r\n 统一归一化成 \n。
        byte[] payload = new byte[253];
        int n = 0;
        for (int i = 0; i < 256; i++) {
            if (i != ']' && i != '\n' && i != '\r') {
                payload[n++] = (byte) i;
            }
        }
        assertEquals(253, n);
        byte[] src = new byte[n + 4];
        src[0] = '[';
        src[1] = '[';
        System.arraycopy(payload, 0, src, 2, n);
        src[n + 2] = ']';
        src[n + 3] = ']';
        Lexer ls = new Lexer(src, SRC);
        ls.next();
        assertEquals(Lexer.TK_STRING, ls.t.token);
        assertArrayEquals(payload, ls.t.s.bytes);
    }

    /* ---------- 长字符串与注释 ---------- */

    @Test
    void longStringSkipsFirstNewline() {
        assertEquals("abc", first("[[\nabc]]").s.toJavaString());
        assertEquals(3, first("[[\nabc]]").s.length());
        assertEquals("\nabc", first("[[\n\nabc]]").s.toJavaString(), "只吞紧跟开头的第一个换行");
    }

    @Test
    void longStringNormalizesCrLf() {
        assertArrayEquals(new byte[] { 'a', '\n', 'b' }, first("[[\na\r\nb]]").s.bytes);
    }

    @Test
    void longStringRespectsLevel() {
        assertEquals("abc", first("[==[abc]==]").s.toJavaString());
        assertEquals("a]]b", first("[==[a]]b]==]").s.toJavaString());
        assertEquals("", first("[[]]").s.toJavaString());
        // 结束符是贪婪匹配到的第一个同级 ']]'：'[[]]]' 切成空长字符串 + 一个 ']'。
        // 参考实现 load("[[]]]") 报 "unexpected symbol near '[[]]'"，正是这个切分。
        assertEquals("STR() ']'", render("[[]]]"));
    }

    @Test
    void skipsComments() {
        assertEquals("NAME(a) '=' INT(1)", render("a = 1 -- 短注释"));
        assertEquals("NAME(a) '=' INT(1)", render("a = 1 --[[ 长注释 ]]"));
        assertEquals("NAME(a) '=' INT(1)", render("-- 开头\n a = 1"));
        assertEquals("", render("--[[ 只有注释 ]]"));
        assertEquals("", render("--[==[ 只有注释 ]==]"));
        // 注释里的引号不能把词法状态搞乱
        assertEquals("NAME(a) '=' INT(1) NAME(a)", render("a = 1 -- it's a comment\na"));
    }

    @Test
    void unbalancedBracketInCommentFallsBackToShortComment() {
        // '[==' 没有第二个 '['，退化成短注释，把整行吃掉
        assertEquals("NAME(a)", render("--[== not a long comment\na"));
    }

    /* ---------- 错误信息（全部逐字对齐参考实现） ---------- */

    @Test
    void reportsUnfinishedString() {
        assertEquals(SRC + ":1: unfinished string near <eof>", errorOf("x = \"abc"));
        assertEquals(SRC + ":1: unfinished string near '\"abc'", errorOf("x = \"abc\n"));
    }

    @Test
    void reportsUnfinishedLongString() {
        assertEquals(SRC + ":1: unfinished long string (starting at line 1) near <eof>",
                errorOf("x = [[abc"));
        assertEquals(SRC + ":3: unfinished long string (starting at line 2) near <eof>",
                errorOf("x = 1\n[[abc\n"));
    }

    @Test
    void reportsUnfinishedLongComment() {
        assertEquals(SRC + ":1: unfinished long comment (starting at line 1) near <eof>",
                errorOf("--[[ x"));
    }

    @Test
    void reportsInvalidLongStringDelimiter() {
        assertEquals(SRC + ":1: invalid long string delimiter near '[=='", errorOf("x = [==abc]==]"));
    }

    @Test
    void reportsInvalidEscapeSequence() {
        assertEquals(SRC + ":1: invalid escape sequence near '\"a\\q'", errorOf("x = \"a\\qb\""));
    }

    @Test
    void reportsDecimalEscapeTooLarge() {
        assertEquals(SRC + ":1: decimal escape too large near '\"\\256\"'", errorOf("x = \"\\256\""));
        assertEquals(SRC + ":1: decimal escape too large near '\"\\654\"'", errorOf("x = \"\\654\""));
    }

    @Test
    void reportsHexadecimalDigitExpected() {
        assertEquals(SRC + ":1: hexadecimal digit expected near '\"\\xZ'", errorOf("x = \"\\xZ1\""));
        assertEquals(SRC + ":1: hexadecimal digit expected near '\"\\x4\"'", errorOf("x = \"\\x4\""));
    }

    @Test
    void reportsUtf8EscErrors() {
        assertEquals(SRC + ":1: UTF-8 value too large near '\"\\u{80000000'",
                errorOf("x = \"\\u{80000000}\""));
        assertEquals(SRC + ":1: missing '}' near '\"\\u{41\"'", errorOf("x = \"\\u{41\""));
        assertEquals(SRC + ":1: missing '{' near '\"\\uA'", errorOf("x = \"\\uA\""));
    }

    @Test
    void nonAsciiByteIsNotAnIdentifier() {
        // 默认配置（未定义 LUA_UCID）下非 ASCII 字节不是字母，参考实现报 '<\228>'
        byte[] src = new byte[] { 'x', ' ', (byte) 0xE4 };
        Lexer ls = new Lexer(src, SRC);
        ls.next();
        ls.next();
        assertEquals(0xE4, ls.t.token, "非 ASCII 字节作为单字符 token 原样返回");
        assertEquals("'<\\228>'", Lexer.tokenName(0xE4));
    }

    /* ---------- 行号与前瞻 ---------- */

    @Test
    void countsLines() {
        Lexer ls = lexer("a\nb\r\nc\n\rd");
        ls.next();
        assertEquals(1, ls.linenumber);
        assertEquals(1, ls.lastline);
        ls.next();
        assertEquals(2, ls.linenumber);
        ls.next();
        assertEquals(3, ls.linenumber, "\\r\\n 只算一行");
        ls.next();
        assertEquals(4, ls.linenumber, "\\n\\r 也只算一行");
        ls.next();
        assertEquals(Lexer.TK_EOS, ls.t.token);
    }

    @Test
    void lookaheadDoesNotConsume() {
        Lexer ls = lexer("a b");
        ls.next();
        assertEquals(Lexer.TK_NAME, ls.t.token);
        assertEquals("a", ls.t.s.toJavaString());
        assertEquals(Lexer.TK_NAME, ls.lookahead());
        assertEquals(Lexer.TK_NAME, ls.t.token, "前瞻不能改动当前 token");
        assertEquals("a", ls.t.s.toJavaString());
        ls.next();
        assertEquals("b", ls.t.s.toJavaString(), "next 直接取用前瞻结果");
        ls.next();
        assertEquals(Lexer.TK_EOS, ls.t.token);
    }

    @Test
    void lineBreakInsideStringIsCounted() {
        Lexer ls = lexer("'a\\\nb'\nc");
        ls.next();
        assertEquals(Lexer.TK_STRING, ls.t.token);
        assertEquals(2, ls.linenumber, "续行转义要计行");
        ls.next();
        assertEquals("c", ls.t.s.toJavaString());
    }

    /* ---------- 驻留 ---------- */

    @Test
    void internsShortStrings() {
        StringPool pool = new StringPool();
        Lexer a = new Lexer(new Zio("'abc' 'abc'".getBytes(StandardCharsets.UTF_8)), SRC, pool);
        a.next();
        LuaString s1 = a.t.s;
        a.next();
        LuaString s2 = a.t.s;
        assertSame(s1, s2, "同一个短字符串字面量必须是同一个实例");
        assertEquals("abc", s1.toJavaString());
    }
}
