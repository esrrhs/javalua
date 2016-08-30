package fakescript;

import java_cup.runtime.*;

%%

%public
%class jflex
%implements sym

%unicode

%line
%column

%cup
%cupdebug

%{
  fake m_f;

  StringBuilder string = new StringBuilder();
  
  public void set_fake(fake f)
  {
	m_f = f;
  }
  
  private Symbol symbol(int type) {
	types.log(m_f, "[JFLEX]: " + sym.terminalNames[type]);
    return new javasymbol(type, yyline+1, yycolumn+1);
  }

  private Symbol symbol(int type, Object value) {
	types.log(m_f, "[JFLEX]: " + sym.terminalNames[type] + "(" + value.toString() + ")");
    return new javasymbol(type, yyline+1, yycolumn+1, value);
  }
  
  public int get_line()
  {
	return yyline;
  }

  /** 
   * assumes correct representation of a long value for 
   * specified radix in scanner buffer from <code>start</code> 
   * to <code>end</code> 
   */
  private long parseLong(int start, int end, int radix) {
    long result = 0;
    long digit;

    for (int i = start; i < end; i++) {
      digit  = Character.digit(yycharat(i),radix);
      result*= radix;
      result+= digit;
    }

    return result;
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
	return symbol(VAR_BEGIN);
}

"return"  {
	return symbol(RETURN);
}

"break" {
    return symbol(BREAK);
}

"func" {
	return symbol(FUNC);
}

"fake" {
	return symbol(FAKE);
}

"while" {
	return symbol(WHILE);
}

"for" {
	return symbol(FOR);
}

"true" {
  return symbol(FTRUE);
}

"false" {
  return symbol(FFALSE);
}

"if" {
	return symbol(IF);
}

"then" {
	return symbol(THEN);
}

"else" {
	return symbol(ELSE);
}

"elseif" {
	return symbol(ELSEIF);
}

"end" {
	return symbol(END);
}

"const" {
	return symbol(FCONST);
}

"package" {
	return symbol(PACKAGE);
}

"null" {
	return symbol(NULL);
}

"include" {
	return symbol(INCLUDE);
}

"struct" {
	return symbol(STRUCT);
}

"and" {
	return symbol(AND);
}

"or" {
	return symbol(OR);
}

"is" {
	return symbol(IS);
}

"not" {
	return symbol(NOT);
}

"continue" {
	return symbol(CONTINUE);
}

"yield" {
	return symbol(YIELD);
}

"sleep" {
	return symbol(SLEEP);
}

"switch" {
	return symbol(SWITCH);
}

"case" {
	return symbol(CASE);
}

"default" {
	return symbol(DEFAULT);
}

[a-zA-Z_][a-zA-Z0-9_]* {
	return symbol(IDENTIFIER, yytext());
}

[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+ {
	return symbol(IDENTIFIER_DOT, yytext());
}

[a-zA-Z_][a-zA-Z0-9_]*(\-\>[a-zA-Z_][a-zA-Z0-9_]*)+ {
	return symbol(IDENTIFIER_POINTER, yytext());
}

[0-9]+u {
	return symbol(FKUUID, yytext());
}

-?[0-9]+ {
	return symbol(NUMBER, yytext());
}

-?[0-9]+\.[0-9]+([Ee]-?[0-9]+)? {
	return symbol(FKFLOAT, yytext());
}

"%" {
  return symbol(DIVIDE_MOD);
}

"," {
	return symbol(ARG_SPLITTER);
}

"->" {
	return symbol(RIGHT_POINTER);
}

"++" {
	return symbol(INC);
}

"+" {
	return symbol(PLUS);
}

"-" {
	return symbol(MINUS);
}

"/" {
	return symbol(DIVIDE);
}

"*" {
	return symbol(MULTIPLY);
}

":=" {
	return symbol(NEW_ASSIGN);
}

"+=" {
	return symbol(PLUS_ASSIGN);
}

"-=" {
	return symbol(MINUS_ASSIGN);
}

"/=" {
	return symbol(DIVIDE_ASSIGN);
}

"*=" {
	return symbol(MULTIPLY_ASSIGN);
}

"%=" {
  return symbol(DIVIDE_MOD_ASSIGN);
}

"=" {
	return symbol(ASSIGN);
}

">" {
	return symbol(MORE);
}

"<" {
	return symbol(LESS);
}

">=" {
	return symbol(MORE_OR_EQUAL);
}

"<=" {
	return symbol(LESS_OR_EQUAL);
}

"==" {
	return symbol(EQUAL);
}

"!=" {
	return symbol(NOT_EQUAL);
}

"(" {
	return symbol(OPEN_BRACKET);
}

")" {
	return symbol(CLOSE_BRACKET);
}

":" {
	return symbol(COLON);
}

"[" {
	return symbol(OPEN_SQUARE_BRACKET);
}

"]" {
	return symbol(CLOSE_SQUARE_BRACKET);
}

"{" {
	return symbol(OPEN_BIG_BRACKET);
}

"}" {
	return symbol(CLOSE_BIG_BRACKET);
}

".." {
	return symbol(STRING_CAT);
}

{WhiteSpace} { }

<<EOF>> { 
	return symbol(EOF); 
}

}

<STRING> {
  \"                             { yybegin(YYINITIAL); return symbol(STRING_DEFINITION, string); }
  
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
<<EOF>>                          { return symbol(EOF); }
