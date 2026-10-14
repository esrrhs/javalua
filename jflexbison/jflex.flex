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
StringCharacter = [^\r\n\"\\]
WhiteSpace = {LineTerminator} | [ \t\f]

%state STRING

%%

<YYINITIAL> {

{Comment}                      { /* ignore */ }

\"                           { yybegin(STRING); string.setLength(0); }

"var"	{
	return YYParser.Lexer.VAR_BEGIN;
}

"return"  {
	return YYParser.Lexer.RETURN;
}

"break" {
    return YYParser.Lexer.BREAK;
}

"func" {
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

/* 十六进制浮点:0x1p4、0X1.8p1、0x.8p0、0x1.8 */
-?0[xX]([0-9a-fA-F]+\.?[0-9a-fA-F]*|\.[0-9a-fA-F]+)[pP][+-]?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}
-?0[xX]([0-9a-fA-F]+\.[0-9a-fA-F]*|\.[0-9a-fA-F]+) {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* 十六进制整数:0xff、-0x10,按 uint64 位模式解析 */
-?0[xX][0-9a-fA-F]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.NUMBER;
}

/* Lua 风格浮点:带指数(指数可带 +/-)的一定是浮点,如 1e20、-3.0E+5 */
-?[0-9]+(\.[0-9]*)?[Ee][+-]?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* .5 形式浮点 */
-?\.[0-9]+([Ee][+-]?[0-9]+)? {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

/* 1. / 1.5 形式浮点(小数点后可无数字) */
-?[0-9]+\.[0-9]* {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
}

-?[0-9]+ {
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

"&&" {
	return YYParser.Lexer.AND;
}

"&" {
	return YYParser.Lexer.BIT_AND;
}

"||" {
	return YYParser.Lexer.OR;
}

"|" {
	return YYParser.Lexer.BIT_OR;
}

"~" {
	return YYParser.Lexer.WAVE;
}

"<<" {
	return YYParser.Lexer.SHIFT_LEFT;
}

">>" {
	return YYParser.Lexer.SHIFT_RIGHT;
}

"!" {
	return YYParser.Lexer.NOT;
}

"!=" {
	return YYParser.Lexer.NOT_EQUAL;
}

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

/* error fallback */
[^]                              {  }
<<EOF>>                          { return YYParser.Lexer.EOF; }
