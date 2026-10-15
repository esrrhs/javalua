package com.github.esrrhs.fakescript;

import com.github.esrrhs.fakescript.syntree.*;

%%

%unicode

%line
%column
%implements YYParser.Lexer

%byaccj

/* 底层扫描方法改名,由手写的 yylex() 包装,以便根据上下文
   把操作数位置的 - / ~ 替换成 UNARY_MINUS / UNARY_WAVE */
%function yylex_raw

%{
  fake m_f;
  StringBuilder string = new StringBuilder();
  /* store a reference to the YYParser object */
  private YYParser yyparser;
  
  private ParserVal yylval;
  
  private mybison m_mybison;
  
  public fake get_fake()
  {
	return m_f;
  }
  
  public void set_fake(fake f)
  {
	m_f = f;
  }
  
  public void set_mybison(mybison mb)
  {
	m_mybison = mb;
  }
  
  public mybison get_mybison()
  {
	return m_mybison;
  }
  
  public <T> T new_node(Class<? extends syntree_node> c, int lineno)
  {
	try
	{
		syntree_node t = null;
		t = c.newInstance();
		t.m_lno = lineno;
		return (T) t;
	}
	catch (Exception e)
	{
	  return null;
	}
  }

  /* Lua 5.4 数值 for 的构造入口:for i = begin, end[, step] do block end
     脱糖成既有的 for_loop_stmt(与 `for local i = begin -> end, step do` 同构),
     因此不引入新的 opcode 或 AST 节点。step 省略时补常量 1。
     放在这里而不是文法动作里,是为了让四条候选规则(带/不带 step、
     带/不带 block)共用同一段构造代码。 */
  public ParserVal make_lua_for(ParserVal idv, ParserVal beginv, ParserVal endv,
                                ParserVal addv, ParserVal blockv)
  {
	var_node v = new_node(var_node.class, idv.ival);
	v.m_str = idv.sval;

	for_loop_stmt p = new_node(for_loop_stmt.class, idv.ival);
	p.m_var = (syntree_node) v;
	p.m_begin = (syntree_node) beginv.obj;
	p.m_end = (syntree_node) endv.obj;

	if (addv != null)
	{
		p.m_add = (syntree_node) addv.obj;
	}
	else
	{
		explicit_value_node one = new_node(explicit_value_node.class, idv.ival);
		one.m_str = "1";
		one.m_type = explicit_value_type.EVT_NUM;
		p.m_add = one;
	}

	p.m_block = (blockv == null) ? null : (block_node) blockv.obj;

	// Lua 的数值 for 是闭区间 [begin, end];既有的 -> 写法是半开区间,
	// 靠这个标志选 *_CLOSED 变体,避免抄来的 Lua 循环少跑一次
	p.m_closed = true;

	ParserVal ret = new ParserVal(p);
	ret.ival = p.m_lno;
	return ret;
  }

  public int get_line()
  {
	return yyline;
  }

  public Object getLVal() 
  {
    return yylval;
  }

  public void yyerror(String s)
  {
	m_mybison.lexer_error(s, yyline, yytext());
  }

  /* true 表示下一个 token 位于"操作数位"(表达式起始、运算符之后、
     左括号、逗号等),此时的 - / ~ 是一元运算符。块内语句没有分隔符,
     f() - x 既可能是两条语句也可能是一个二元表达式;用词法上下文把
     一元 MINUS/WAVE 与二元 token 区分开,文法上的 reduce/reduce
     冲突就不存在了,二元减法得以优先(贪婪)归约 */
  private boolean m_operandPos = true;

  public int yylex() throws java.io.IOException
  {
	int t = yylex_raw();
	if (m_operandPos)
	{
		if (t == YYParser.Lexer.MINUS)
		{
			t = YYParser.Lexer.UNARY_MINUS;
		}
		else if (t == YYParser.Lexer.WAVE)
		{
			t = YYParser.Lexer.UNARY_WAVE;
		}
	}
	m_operandPos = isOperandPos(t);
	return t;
  }

  private static boolean isOperandPos(int t)
  {
	switch (t)
	{
	/* 这些 token 标志一个(或结束一个)值,其后的 - / ~ 必为二元 */
	case YYParser.Lexer.NUMBER:
	case YYParser.Lexer.FKFLOAT:
	case YYParser.Lexer.STRING_DEFINITION:
	case YYParser.Lexer.IDENTIFIER:
	case YYParser.Lexer.IDENTIFIER_DOT:
	case YYParser.Lexer.IDENTIFIER_POINTER:
	case YYParser.Lexer.FKUUID:
	case YYParser.Lexer.FTRUE:
	case YYParser.Lexer.FFALSE:
	case YYParser.Lexer.NULL:
	case YYParser.Lexer.CLOSE_BRACKET:
	case YYParser.Lexer.CLOSE_SQUARE_BRACKET:
	case YYParser.Lexer.CLOSE_BIG_BRACKET:
		return false;
	default:
		return true;
	}
  }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
Comment = "--" {InputCharacter}* {LineTerminator}?
/* Lua 5.4 长注释 --[[ ... ]]。
   注意:它必须排在 {Comment} 规则之前。JFlex 取最长匹配,长度相同时取靠前的规则;
   而 {Comment} 会把 "--[[ x ]]" 整行吃掉(含行尾换行),长度与本规则持平,
   靠顺序才能选中长注释分支。尾部 {LineTerminator}? 就是为了让长度不至于输。
   循环体 ([^\]] | "]" [^\]])* 保证在第一个 "]]" 处收尾,与 Lua 一致。 */
LongComment = "--[[" ([^\]] | "]" [^\]])* "]]" {LineTerminator}?
StringCharacter = [^\r\n\"\\]
WhiteSpace = {LineTerminator} | [ \t\f]

%state STRING

%%

<YYINITIAL> {

{LongComment}                  { /* ignore: Lua 长注释,必须排在行注释之前 */ }

{Comment}                      { /* ignore */ }

\"                           { yybegin(STRING); string.setLength(0); }

/* Lua 5.4 的变量声明关键字。旧写法 var 已移除:保留两套拼写会让
   var/local 在错误信息、文档、fuzz 语料里长期分裂,不如一次性切干净 */
"local"	{
	return YYParser.Lexer.VAR_BEGIN;
}

"return"  {
	return YYParser.Lexer.RETURN;
}

"break" {
    return YYParser.Lexer.BREAK;
}

/* Lua 5.4 的函数定义关键字;旧写法 func 已移除 */
"function" {
	return YYParser.Lexer.FUNC;
}

"fake" {
	return YYParser.Lexer.FAKE;
}

"while" {
	return YYParser.Lexer.WHILE;
}

"for" {
	return YYParser.Lexer.FOR;
}

"true" {
  return YYParser.Lexer.FTRUE;
}

"false" {
  return YYParser.Lexer.FFALSE;
}

"if" {
	return YYParser.Lexer.IF;
}

"then" {
	return YYParser.Lexer.THEN;
}

"else" {
	return YYParser.Lexer.ELSE;
}

"elseif" {
	return YYParser.Lexer.ELSEIF;
}

"end" {
	return YYParser.Lexer.END;
}

"do" {
	return YYParser.Lexer.DO;
}

"const" {
	return YYParser.Lexer.FCONST;
}

"package" {
	return YYParser.Lexer.PACKAGE;
}

"null" {
	return YYParser.Lexer.NULL;
}

"include" {
	return YYParser.Lexer.INCLUDE;
}

"struct" {
	return YYParser.Lexer.STRUCT;
}

"and" {
	return YYParser.Lexer.AND;
}

"or" {
	return YYParser.Lexer.OR;
}

"is" {
	return YYParser.Lexer.IS;
}

"not" {
	return YYParser.Lexer.NOT;
}

"continue" {
	return YYParser.Lexer.CONTINUE;
}

"yield" {
	return YYParser.Lexer.YIELD;
}

"sleep" {
	return YYParser.Lexer.SLEEP;
}

"switch" {
	return YYParser.Lexer.SWITCH;
}

"case" {
	return YYParser.Lexer.CASE;
}

"default" {
	return YYParser.Lexer.DEFAULT;
}

[a-zA-Z_][a-zA-Z0-9_]* {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.IDENTIFIER;
}

[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.IDENTIFIER_DOT;
}

[a-zA-Z_][a-zA-Z0-9_]*(\-\>[a-zA-Z_][a-zA-Z0-9_]*)+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.IDENTIFIER_POINTER;
}

[0-9]+u {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKUUID;
}

/* 十六进制浮点:0x1p4、0X1.8p1、0x.8p0、0x1.8

   注:数值规则一律不接受前导 -,负号由 yylex() 的上下文判定转成 UNARY_MINUS。
   若在此处允许 -,JFlex 最长匹配会把 x-1 切成 IDENTIFIER(x) + NUMBER(-1),
   中间没有运算符 -> 语法错误(x - 1 带空格反而正常) */
0[xX]([0-9a-fA-F]+\.?[0-9a-fA-F]*|\.[0-9a-fA-F]+)[pP][+-]?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}
0[xX]([0-9a-fA-F]+\.[0-9a-fA-F]*|\.[0-9a-fA-F]+) {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* 十六进制整数:0xff,按 uint64 位模式解析;-0x10 走 UNARY_MINUS */
0[xX][0-9a-fA-F]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.NUMBER;
}

/* Lua 风格浮点:带指数(指数可带 +/-)的一定是浮点,如 1e20 */
[0-9]+(\.[0-9]*)?[Ee][+-]?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* .5 形式浮点 */
\.[0-9]+([Ee][+-]?[0-9]+)? {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* 1. / 1.5 形式浮点(小数点后可无数字) */
[0-9]+\.[0-9]* {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.NUMBER;
}

"%" {
  return YYParser.Lexer.DIVIDE_MOD;
}

"," {
	return YYParser.Lexer.ARG_SPLITTER;
}

"->" {
	return YYParser.Lexer.RIGHT_POINTER;
}

"++" {
	return YYParser.Lexer.INC;
}

/* Lua 5.4 取长度运算符。动作层脱糖为 size(...),不新增 opcode */
"#" {
	return YYParser.Lexer.SHARP;
}

"+" {
	return YYParser.Lexer.PLUS;
}

"-" {
	return YYParser.Lexer.MINUS;
}

"//=" {
	return YYParser.Lexer.DIVIDE_FLOOR_ASSIGN;
}

"//" {
	return YYParser.Lexer.DIVIDE_FLOOR;
}

"/" {
	return YYParser.Lexer.DIVIDE;
}

"*" {
	return YYParser.Lexer.MULTIPLY;
}

":=" {
	return YYParser.Lexer.NEW_ASSIGN;
}

"+=" {
	return YYParser.Lexer.PLUS_ASSIGN;
}

"-=" {
	return YYParser.Lexer.MINUS_ASSIGN;
}

"/=" {
	return YYParser.Lexer.DIVIDE_ASSIGN;
}

"*=" {
	return YYParser.Lexer.MULTIPLY_ASSIGN;
}

"%=" {
  return YYParser.Lexer.DIVIDE_MOD_ASSIGN;
}

"=" {
	return YYParser.Lexer.ASSIGN;
}

">" {
	return YYParser.Lexer.MORE;
}

"<" {
	return YYParser.Lexer.LESS;
}

">=" {
	return YYParser.Lexer.MORE_OR_EQUAL;
}

"<=" {
	return YYParser.Lexer.LESS_OR_EQUAL;
}

"==" {
	return YYParser.Lexer.EQUAL;
}

/* and / or / not 走关键字规则(见上);C 风格的 && || 已移除,
   只保留位运算的 & 与 | */

"&" {
	return YYParser.Lexer.BIT_AND;
}

"|" {
	return YYParser.Lexer.BIT_OR;
}

"~" {
	return YYParser.Lexer.WAVE;
}

/* Lua 5.4 的不等于。~= 比 ~ 长,JFlex 最长匹配会优先选中它,
   因此 a ~= b 不会被误切成 a ~ (= b) */
"~=" {
	return YYParser.Lexer.NOT_EQUAL;
}

"<<" {
	return YYParser.Lexer.SHIFT_LEFT;
}

">>" {
	return YYParser.Lexer.SHIFT_RIGHT;
}

/* 旧写法 ! 与 != 已移除:Lua 用 not 与 ~= */

"(" {
	return YYParser.Lexer.OPEN_BRACKET;
}

")" {
	return YYParser.Lexer.CLOSE_BRACKET;
}

":" {
	return YYParser.Lexer.COLON;
}

"[" {
	return YYParser.Lexer.OPEN_SQUARE_BRACKET;
}

"]" {
	return YYParser.Lexer.CLOSE_SQUARE_BRACKET;
}

"{" {
	return YYParser.Lexer.OPEN_BIG_BRACKET;
}

"}" {
	return YYParser.Lexer.CLOSE_BIG_BRACKET;
}

".." {
	return YYParser.Lexer.STRING_CAT;
}

{WhiteSpace} { }

<<EOF>> { 
	return YYParser.Lexer.EOF; 
}

}

<STRING> {
  \"                             { yybegin(YYINITIAL); 
									yylval = new ParserVal(string.toString()); 
									yylval.ival = yyline + 1;
									return YYParser.Lexer.STRING_DEFINITION; }
  
  {StringCharacter}+             { string.append( yytext() ); }
  
  /* escape sequences */
  "\\b"                          { string.append( '\b' ); }
  "\\t"                          { string.append( '\t' ); }
  "\\n"                          { string.append( '\n' ); }
  "\\f"                          { string.append( '\f' ); }
  "\\r"                          { string.append( '\r' ); }
  "\\\""                         { string.append( '\"' ); }
  "\\'"                          { string.append( '\'' ); }
  "\\\\"                         { string.append( '\\' ); }

  /* Lua 5.4 扩展转义 */
  "\\a"                          { string.append( (char)0x07 ); }
  "\\v"                          { string.append( (char)0x0B ); }
  "\\x"[0-9a-fA-F]{2}            { string.append( (char)Integer.parseInt(yytext().substring(2), 16) ); }
  "\\u{"[0-9a-fA-F]+"}"          { int cp = Integer.parseInt(yytext().substring(3, yytext().length()-1), 16);
                                  string.append( new String(Character.toChars(cp)) ); }
  "\\z"({WhiteSpace})*            { /* Lua \z: 跳过后续空白 */ }
  "\\"[0-9]{1,3}                 { string.append( (char)Integer.parseInt(yytext().substring(1)) ); }

}

/* error fallback
   注意 JFlex 里 [^] 是「否定空集」,等价于匹配任意字符,而非字面量 ^。
   原先这里静默跳过未识别字符:1 @ 2 会先被吞掉 @ 变成 1 2,再由语法层在离真正
   出错位置很远的地方报一个莫名的错;更糟的是 return a b + c 这类写法会丢掉后半
   段却照常「解析成功」。改为显式报错,让非法字符在词法层就暴露。
   这里必须抛错而不能改返回 EOF——截断后剩下的半个脚本可能被语法层接受,
   把一个拼写错误变成「静默算错」。 */
[^]                              { throw new java.io.IOException(
                                     String.format("lex fail, illegal character '%s' at line(%d)", yytext(), yyline)); }
<<EOF>>                          { return YYParser.Lexer.EOF; }
