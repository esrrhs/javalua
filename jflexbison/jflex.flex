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
	return YYParser.VAR_BEGIN;
}

"return"  {
	return YYParser.RETURN;
}

"break" {
    return YYParser.BREAK;
}

"func" {
	return YYParser.FUNC;
}

"fake" {
	return YYParser.FAKE;
}

"while" {
	return YYParser.WHILE;
}

"for" {
	return YYParser.FOR;
}

"true" {
  return YYParser.FTRUE;
}

"false" {
  return YYParser.FFALSE;
}

"if" {
	return YYParser.IF;
}

"then" {
	return YYParser.THEN;
}

"else" {
	return YYParser.ELSE;
}

"elseif" {
	return YYParser.ELSEIF;
}

"end" {
	return YYParser.END;
}

"const" {
	return YYParser.FCONST;
}

"package" {
	return YYParser.PACKAGE;
}

"null" {
	return YYParser.NULL;
}

"include" {
	return YYParser.INCLUDE;
}

"struct" {
	return YYParser.STRUCT;
}

"and" {
	return YYParser.AND;
}

"or" {
	return YYParser.OR;
}

"is" {
	return YYParser.IS;
}

"not" {
	return YYParser.NOT;
}

"continue" {
	return YYParser.CONTINUE;
}

"yield" {
	return YYParser.YIELD;
}

"sleep" {
	return YYParser.SLEEP;
}

"switch" {
	return YYParser.SWITCH;
}

"case" {
	return YYParser.CASE;
}

"default" {
	return YYParser.DEFAULT;
}

[a-zA-Z_][a-zA-Z0-9_]* {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.IDENTIFIER;
}

[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.IDENTIFIER_DOT;
}

[a-zA-Z_][a-zA-Z0-9_]*(\-\>[a-zA-Z_][a-zA-Z0-9_]*)+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.IDENTIFIER_POINTER;
}

[0-9]+u {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.FKUUID;
}

-?[0-9]+ {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.NUMBER;
}

-?[0-9]+\.[0-9]+([Ee]-?[0-9]+)? {
	yylval = new ParserVal(yytext());
	yylval.ival = yyline + 1;
	return YYParser.FKFLOAT;
}

"%" {
  return YYParser.DIVIDE_MOD;
}

"," {
	return YYParser.ARG_SPLITTER;
}

"->" {
	return YYParser.RIGHT_POINTER;
}

"++" {
	return YYParser.INC;
}

"+" {
	return YYParser.PLUS;
}

"-" {
	return YYParser.MINUS;
}

"/" {
	return YYParser.DIVIDE;
}

"*" {
	return YYParser.MULTIPLY;
}

":=" {
	return YYParser.NEW_ASSIGN;
}

"+=" {
	return YYParser.PLUS_ASSIGN;
}

"-=" {
	return YYParser.MINUS_ASSIGN;
}

"/=" {
	return YYParser.DIVIDE_ASSIGN;
}

"*=" {
	return YYParser.MULTIPLY_ASSIGN;
}

"%=" {
  return YYParser.DIVIDE_MOD_ASSIGN;
}

"=" {
	return YYParser.ASSIGN;
}

">" {
	return YYParser.MORE;
}

"<" {
	return YYParser.LESS;
}

">=" {
	return YYParser.MORE_OR_EQUAL;
}

"<=" {
	return YYParser.LESS_OR_EQUAL;
}

"==" {
	return YYParser.EQUAL;
}

"!=" {
	return YYParser.NOT_EQUAL;
}

"(" {
	return YYParser.OPEN_BRACKET;
}

")" {
	return YYParser.CLOSE_BRACKET;
}

":" {
	return YYParser.COLON;
}

"[" {
	return YYParser.OPEN_SQUARE_BRACKET;
}

"]" {
	return YYParser.CLOSE_SQUARE_BRACKET;
}

"{" {
	return YYParser.OPEN_BIG_BRACKET;
}

"}" {
	return YYParser.CLOSE_BIG_BRACKET;
}

".." {
	return YYParser.STRING_CAT;
}

{WhiteSpace} { }

<<EOF>> { 
	return YYParser.EOF; 
}

}

<STRING> {
  \"                             { yybegin(YYINITIAL); 
									yylval = new ParserVal(string.toString()); 
									yylval.ival = yyline + 1;
									return YYParser.STRING_DEFINITION; }
  
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
<<EOF>>                          { return YYParser.EOF; }
