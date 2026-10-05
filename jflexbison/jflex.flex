package com.github.esrrhs.fakescript;

import com.github.esrrhs.fakescript.syntree.*;

%%

%unicode

%line
%column
%implements YYParser.Lexer

%byaccj

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

-?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.NUMBER;
}

-?[0-9]+\.[0-9]+([Ee]-?[0-9]+)? {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.Lexer.FKFLOAT;
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

"||" {
	return YYParser.Lexer.OR;
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

}

/* error fallback */
[^]                              {  }
<<EOF>>                          { return YYParser.Lexer.EOF; }
