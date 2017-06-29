%{
package fakescript;

import fakescript.syntree.*;
import java.io.*;

%}

/* Terminals (tokens returned by the scanner). */
%token VAR_BEGIN;
%token RETURN;
%token BREAK;
%token FUNC;
%token WHILE;
%token FTRUE;
%token FFALSE;
%token IF;
%token THEN;
%token ELSE;
%token END;
%token STRING_DEFINITION;
%token IDENTIFIER;
%token NUMBER;
%token SINGLE_LINE_COMMENT;
%token DIVIDE_MOD;
%token ARG_SPLITTER;
%token PLUS;
%token MINUS;
%token DIVIDE;
%token MULTIPLY;
%token ASSIGN;
%token MORE;
%token LESS;
%token MORE_OR_EQUAL;
%token LESS_OR_EQUAL;
%token EQUAL;
%token NOT_EQUAL;
%token OPEN_BRACKET;
%token CLOSE_BRACKET;
%token AND;
%token OR;
%token FKFLOAT;
%token PLUS_ASSIGN;
%token MINUS_ASSIGN;
%token DIVIDE_ASSIGN;
%token MULTIPLY_ASSIGN;
%token DIVIDE_MOD_ASSIGN;
%token COLON;
%token FOR;
%token INC;
%token FAKE;
%token FKUUID;
%token OPEN_SQUARE_BRACKET;
%token CLOSE_SQUARE_BRACKET;
%token FCONST;
%token PACKAGE;
%token INCLUDE;
%token IDENTIFIER_DOT;
%token IDENTIFIER_POINTER;
%token STRUCT;
%token IS;
%token NOT; 
%token CONTINUE;
%token YIELD;
%token SLEEP;
%token SWITCH;
%token CASE; 
%token DEFAULT;
%token NEW_ASSIGN;
%token ELSEIF;
%token RIGHT_POINTER;
%token STRING_CAT;
%token OPEN_BIG_BRACKET;
%token CLOSE_BIG_BRACKET;
%token NULL;

/* Precedences */
%left PLUS;
%left MINUS;
%left DIVIDE;
%left MULTIPLY;
%left DIVIDE_MOD;
%left STRING_CAT;

%expect 55

%%

/* Top level rules */
program : package_head
	include_head
	struct_head
	const_head
	body
	;
	
body :
	/* empty */
	{
	}
	|
	function_declaration
	|
	body function_declaration
	;

function_declaration :
	FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END");
		func_desc_node p = ((Yylex)yylexer).new_node(func_desc_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_funcname = ((ParserVal)((ParserVal)$2)).sval;
		p.m_arglist = (func_desc_arglist_node)((ParserVal)$4).obj;
		p.m_block = (block_node)((ParserVal)$6).obj;
		p.m_endline = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		((Yylex)yylexer).get_mybison().add_func_desc(p);
	}
	|
	FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET END");
		func_desc_node p = ((Yylex)yylexer).new_node(func_desc_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_funcname = ((ParserVal)((ParserVal)$2)).sval;
		p.m_arglist = (func_desc_arglist_node)((ParserVal)$4).obj;
		p.m_endline = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		((Yylex)yylexer).get_mybison().add_func_desc(p);
	}
	;

function_declaration_arguments :
	/* empty */
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
	}
	| 
	function_declaration_arguments ARG_SPLITTER arg 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_declaration_arguments ARG_SPLITTER arg ");
		func_desc_arglist_node p = (func_desc_arglist_node)((ParserVal)$1).obj;
		p.add_arg((syntree_node)((ParserVal)$3).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	| 
	arg
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: arg");
		func_desc_arglist_node p = ((Yylex)yylexer).new_node(func_desc_arglist_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_arg((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

arg :
	IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		identifier_node p = ((Yylex)yylexer).new_node(identifier_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
	
function_call :
	IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_fuc = ((ParserVal)((ParserVal)$1)).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)$3).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	} 
	|
	IDENTIFIER_DOT OPEN_BRACKET function_call_arguments CLOSE_BRACKET 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_fuc = ((ParserVal)((ParserVal)$1)).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)$3).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	} 
	|
	function_call OPEN_BRACKET function_call_arguments CLOSE_BRACKET 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_prefuc = (syntree_node)((ParserVal)$1).obj;
		p.m_arglist = (function_call_arglist_node)((ParserVal)$3).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	} 
	|
	variable COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_fuc = ((ParserVal)((ParserVal)$3)).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)$5).obj;
		if (p.m_arglist == null)
		{
			p.m_arglist = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)$1)).ival);
		}
		p.m_arglist.add_arg((syntree_node)((ParserVal)$1).obj);
		p.m_fakecall = false;
		p.m_classmem_call = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	} 
	|
	function_call COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_fuc = ((ParserVal)((ParserVal)$3)).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)$5).obj;
		if (p.m_arglist == null)
		{
			p.m_arglist = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)$1)).ival);
		}
		p.m_arglist.add_arg((syntree_node)((ParserVal)$1).obj);
		p.m_fakecall = false;
		p.m_classmem_call = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	} 
	;
	
function_call_arguments :
	/* empty */
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
	}
	| 
	function_call_arguments ARG_SPLITTER arg_expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call_arguments ARG_SPLITTER arg_expr ");
		function_call_arglist_node p = (function_call_arglist_node)((ParserVal)$1).obj;
		p.add_arg((syntree_node)((ParserVal)$3).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	| 
	arg_expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: arg_expr ");
		function_call_arglist_node p = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_arg((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;  

arg_expr :
	expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;

/* function declaration end */

block :
	block stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: block stmt ");
		block_node p = (block_node)((ParserVal)$1).obj;
		p.add_stmt((syntree_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: stmt");
		block_node p = ((Yylex)yylexer).new_node(block_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_stmt((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
  
stmt :
	while_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: while_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	if_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: if_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	return_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	assign_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: assign_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	multi_assign_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: multi_assign_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	break
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: break");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	continue
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: continue");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	math_assign_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_assign_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	for_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	for_loop_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_loop_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	fake_call_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: fake_call_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	sleep
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: sleep_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	yield
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: yield_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	switch_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_stmt");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;

fake_call_stmt :
	FAKE function_call
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FAKE function_call");
		function_call_node p = (function_call_node)((ParserVal)$2).obj;
		p.m_fakecall = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
for_stmt :
	FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN block END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN block END");
		for_stmt p = ((Yylex)yylexer).new_node(for_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$4).obj;
		p.m_beginblock = (block_node)((ParserVal)$2).obj;
		p.m_endblock = (block_node)((ParserVal)$6).obj;
		p.m_block = (block_node)((ParserVal)$8).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN END");
		for_stmt p = ((Yylex)yylexer).new_node(for_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$4).obj;
		p.m_beginblock = (block_node)((ParserVal)$2).obj;
		p.m_endblock = (block_node)((ParserVal)$6).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

for_loop_stmt :
	FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN block END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN block END");
		for_loop_stmt p = ((Yylex)yylexer).new_node(for_loop_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_var = (syntree_node)((ParserVal)$2).obj;
		p.m_begin = (syntree_node)((ParserVal)$4).obj;
		p.m_end = (syntree_node)((ParserVal)$6).obj;
		p.m_add = (syntree_node)((ParserVal)$8).obj;
		p.m_block = (block_node)((ParserVal)$10).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN END");
		for_loop_stmt p = ((Yylex)yylexer).new_node(for_loop_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_var = (syntree_node)((ParserVal)$2).obj;
		p.m_begin = (syntree_node)((ParserVal)$4).obj;
		p.m_end = (syntree_node)((ParserVal)$6).obj;
		p.m_add = (syntree_node)((ParserVal)$8).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
while_stmt :
	WHILE cmp THEN block END 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp THEN block END ");
		while_stmt p = ((Yylex)yylexer).new_node(while_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = (block_node)((ParserVal)$4).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	WHILE cmp THEN END 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp THEN END ");
		while_stmt p = ((Yylex)yylexer).new_node(while_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
if_stmt :
	IF cmp THEN block elseif_stmt_list else_stmt END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IF cmp THEN block elseif_stmt_list else_stmt END");
		if_stmt p = ((Yylex)yylexer).new_node(if_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = (block_node)((ParserVal)$4).obj;
		p.m_elseifs = (elseif_stmt_list)((ParserVal)$5).obj;
		p.m_elses = (else_stmt)((ParserVal)$6).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	IF cmp THEN elseif_stmt_list else_stmt END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IF cmp THEN elseif_stmt_list else_stmt END");
		if_stmt p = ((Yylex)yylexer).new_node(if_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = null;
		p.m_elseifs = (elseif_stmt_list)((ParserVal)$4).obj;
		p.m_elses = (else_stmt)((ParserVal)$5).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
elseif_stmt_list :
	/* empty */
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		$$ = ret;
	}
	| 
	elseif_stmt_list elseif_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt_list elseif_stmt");
		elseif_stmt_list p = (elseif_stmt_list)((ParserVal)$1).obj;
		p.add_stmt((syntree_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	| 
	elseif_stmt
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt");
		elseif_stmt_list p = ((Yylex)yylexer).new_node(elseif_stmt_list.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_stmt((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
elseif_stmt :
	ELSEIF cmp THEN block
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSEIF cmp THEN block");
		elseif_stmt p = ((Yylex)yylexer).new_node(elseif_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = (syntree_node)((ParserVal)$4).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	ELSEIF cmp THEN
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSEIF cmp THEN");
		elseif_stmt p = ((Yylex)yylexer).new_node(elseif_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)$2).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
else_stmt :
	/*empty*/
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		$$ = ret;
	}
	|
	ELSE block
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE block");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_block = (block_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	ELSE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

cmp :
	OPEN_BRACKET cmp CLOSE_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET cmp CLOSE_BRACKET");
		$$ = ((ParserVal)((ParserVal)$2));
	}
	|
	cmp AND cmp
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp AND cmp");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "&&";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp OR cmp
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp OR cmp");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "||";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value LESS cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value LESS cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "<";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value MORE cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value MORE cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = ">";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value EQUAL cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "==";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value MORE_OR_EQUAL cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value MORE_OR_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = ">=";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value LESS_OR_EQUAL cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value LESS_OR_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "<=";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	cmp_value NOT_EQUAL cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value NOT_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_cmp = "!=";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FTRUE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_cmp = "true";
		p.m_left = null;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FFALSE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_cmp = "false";
		p.m_left = null;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	IS cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IS cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = "is";
		p.m_left = (syntree_node)((ParserVal)$2).obj;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	NOT cmp_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NOT cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = "not";
		p.m_left = (syntree_node)((ParserVal)$2).obj;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

cmp_value :
	explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	variable
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;
	
return_stmt :
	RETURN return_value_list
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN return_value_list");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_returnlist = (return_value_list_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	RETURN
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_returnlist = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
 
return_value_list :
	return_value_list ARG_SPLITTER return_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value_list ARG_SPLITTER return_value");
		return_value_list_node p = (return_value_list_node)((ParserVal)$1).obj;
		p.add_arg((syntree_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	return_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value");
		return_value_list_node p = ((Yylex)yylexer).new_node(return_value_list_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_arg((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
 
return_value :
	explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	variable
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;

assign_stmt :
	var ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var ASSIGN assign_value");
		assign_stmt p = ((Yylex)yylexer).new_node(assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		p.m_isnew = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	var NEW_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var NEW_ASSIGN assign_value");
		assign_stmt p = ((Yylex)yylexer).new_node(assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		p.m_isnew = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

multi_assign_stmt :
	var_list ASSIGN function_call
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ASSIGN function_call");
		multi_assign_stmt p = ((Yylex)yylexer).new_node(multi_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_varlist = (var_list_node)((ParserVal)$1).obj;
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		p.m_isnew = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	var_list NEW_ASSIGN function_call
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list NEW_ASSIGN function_call");
		multi_assign_stmt p = ((Yylex)yylexer).new_node(multi_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_varlist = (var_list_node)((ParserVal)$1).obj;
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		p.m_isnew = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
var_list :
	var_list ARG_SPLITTER var
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ARG_SPLITTER var");
		var_list_node p = (var_list_node)((ParserVal)$1).obj;
		p.add_arg((syntree_node)((ParserVal)$3).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	var
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var");
		var_list_node p = ((Yylex)yylexer).new_node(var_list_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_arg((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
assign_value :
	explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	variable
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;
	
math_assign_stmt :
	variable PLUS_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable PLUS_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "+=";
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable MINUS_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable MINUS_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "-=";
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable DIVIDE_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable DIVIDE_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "/=";
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable MULTIPLY_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable MULTIPLY_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "*=";
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable DIVIDE_MOD_ASSIGN assign_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable DIVIDE_MOD_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "%=";
		p.m_value = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable INC
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable INC");
		explicit_value_node pp = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		pp.m_str = "1";
		pp.m_type = explicit_value_type.EVT_NUM;
		
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_var = (syntree_node)((ParserVal)$1).obj;
		p.m_oper = "+=";
		p.m_value = pp;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
var :
	VAR_BEGIN IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: VAR_BEGIN IDENTIFIER");
		var_node p = ((Yylex)yylexer).new_node(var_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_str = ((ParserVal)((ParserVal)$2)).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	variable
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;

variable :
	IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET");
		container_get_node p = ((Yylex)yylexer).new_node(container_get_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_container = ((ParserVal)((ParserVal)$1)).sval;
		p.m_key = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	IDENTIFIER_POINTER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_POINTER");
		struct_pointer_node p = ((Yylex)yylexer).new_node(struct_pointer_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	IDENTIFIER_DOT
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

expr :
	OPEN_BRACKET expr CLOSE_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET expr CLOSE_BRACKET");
		$$ = ((ParserVal)((ParserVal)$2));
	}
	|
	function_call
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	math_expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;

math_expr :
	OPEN_BRACKET math_expr CLOSE_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET math_expr CLOSE_BRACKET");
		$$ = ((ParserVal)((ParserVal)$2));
	}
	|
	expr_value PLUS expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value PLUS expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "+";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	expr_value MINUS expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value MINUS expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "-";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	expr_value MULTIPLY expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value MULTIPLY expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "*";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	expr_value DIVIDE expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value DIVIDE expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "/";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	expr_value DIVIDE_MOD expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value DIVIDE_MOD expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "%";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	expr_value STRING_CAT expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value STRING_CAT expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_oper = "..";
		p.m_left = (syntree_node)((ParserVal)$1).obj;
		p.m_right = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;	

expr_value :
	math_expr
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	function_call
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	|
	variable
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		$$ = ((ParserVal)((ParserVal)$1));
	}
	;
	
break :
	BREAK
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: BREAK");
		break_stmt p = ((Yylex)yylexer).new_node(break_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
continue :
	CONTINUE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CONTINUE");
		continue_stmt p = ((Yylex)yylexer).new_node(continue_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

sleep :
	SLEEP expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SLEEP");
		sleep_stmt p = ((Yylex)yylexer).new_node(sleep_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_time = (syntree_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
yield :
	YIELD expr_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: YIELD");
		yield_stmt p = ((Yylex)yylexer).new_node(yield_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_time = (syntree_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
switch_stmt :
	SWITCH cmp_value switch_case_list DEFAULT block END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SWITCH cmp_value switch_case_list DEFAULT block END");
		switch_stmt p = ((Yylex)yylexer).new_node(switch_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (syntree_node)((ParserVal)$2).obj;
		p.m_caselist = (syntree_node)((ParserVal)$3).obj;
		p.m_def = (syntree_node)((ParserVal)$5).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	SWITCH cmp_value switch_case_list DEFAULT END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SWITCH cmp_value switch_case_list DEFAULT END");
		switch_stmt p = ((Yylex)yylexer).new_node(switch_stmt.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (syntree_node)((ParserVal)$2).obj;
		p.m_caselist = (syntree_node)((ParserVal)$3).obj;
		p.m_def = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
	
switch_case_list :
	switch_case_define
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_define");
		switch_caselist_node p = ((Yylex)yylexer).new_node(switch_caselist_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_case((syntree_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	switch_case_list switch_case_define
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_list switch_case_define");
		switch_caselist_node p = (switch_caselist_node)((ParserVal)$1).obj;
		p.add_case((syntree_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

switch_case_define :
	CASE cmp_value THEN block
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CASE cmp_value THEN block");
		switch_case_node p = ((Yylex)yylexer).new_node(switch_case_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (syntree_node)((ParserVal)$2).obj;
		p.m_block = (syntree_node)((ParserVal)$4).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	CASE cmp_value THEN
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CASE cmp_value THEN");
		switch_case_node p = ((Yylex)yylexer).new_node(switch_case_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_cmp = (syntree_node)((ParserVal)$2).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
		
package_head :
	/* empty */
	{
	}
	|
	PACKAGE IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)$2).sval);
	}
	|
	PACKAGE IDENTIFIER_DOT
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER_DOT ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)$2).sval);
	}
	;

include_head :
	/* empty */
	{
	}
	|
	include_define
	|
	include_head include_define
	;
	
include_define :
	INCLUDE STRING_DEFINITION
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: INCLUDE STRING_DEFINITION ");
		((Yylex)yylexer).get_mybison().add_include(((ParserVal)$2).sval);
	}
	;

struct_head :
	/* empty */
	{
	}
	|
	struct_define
	|
	struct_head struct_define
	;

struct_define :
	STRUCT IDENTIFIER struct_mem_declaration END
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRUCT IDENTIFIER struct_mem_declaration END ");
		((Yylex)yylexer).get_mybison().add_struct_desc(((ParserVal)$2).sval);
	}
	;
	
struct_mem_declaration :
	struct_mem_declaration IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: struct_mem_declaration IDENTIFIER ");
	}
	| 
	IDENTIFIER
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER ");
	}
	;

const_head :
	/* empty */
	{
	}
	|
	const_define
	|
	const_head const_define
	;

const_define :
	FCONST IDENTIFIER ASSIGN explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FCONST IDENTIFIER ASSIGN explicit_value ");
		((Yylex)yylexer).get_mybison().add_const_desc(((ParserVal)$2).sval, (syntree_node)((ParserVal)$4).obj);
	}
	;

explicit_value :
	NULL
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NULL ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_type = explicit_value_type.EVT_NULL;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FTRUE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_type = explicit_value_type.EVT_TRUE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FFALSE
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_type = explicit_value_type.EVT_FALSE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	NUMBER 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NUMBER ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		p.m_type = explicit_value_type.EVT_NUM;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FKUUID
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FKUUID ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		p.m_type = explicit_value_type.EVT_UUID;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	STRING_DEFINITION 
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRING_DEFINITION ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		p.m_type = explicit_value_type.EVT_STR;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	FKFLOAT
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FKFLOAT ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_str = ((ParserVal)((ParserVal)$1)).sval;
		p.m_type = explicit_value_type.EVT_FLOAT;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_type = explicit_value_type.EVT_MAP;
		p.m_v = (const_map_list_value_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	OPEN_SQUARE_BRACKET const_array_list_value CLOSE_SQUARE_BRACKET
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BIG_BRACKET const_array_list_value CLOSE_BIG_BRACKET ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)$2)).ival);
		p.m_type = explicit_value_type.EVT_ARRAY;
		p.m_v = (const_array_list_value_node)((ParserVal)$2).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
 

const_map_list_value :
	/* empty */
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	const_map_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_value ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_ele((const_map_value_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	const_map_list_value const_map_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_list_value const_map_value ");
		const_map_list_value_node p = (const_map_list_value_node)((ParserVal)$1).obj;
		p.add_ele((const_map_value_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;


const_map_value :
	explicit_value COLON explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value COLON explicit_value ");
		const_map_value_node p = ((Yylex)yylexer).new_node(const_map_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.m_k = (syntree_node)((ParserVal)$1).obj;
		p.m_v = (syntree_node)((ParserVal)$3).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;

const_array_list_value :
	/* empty */
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((ParserVal)((ParserVal)$1)).ival);
		p.add_ele((explicit_value_node)((ParserVal)$1).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	|
	const_array_list_value explicit_value
	{
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_array_list_value explicit_value ");
		const_array_list_value_node p = (const_array_list_value_node)((ParserVal)$1).obj;
		p.add_ele((explicit_value_node)((ParserVal)$2).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		$$ = ret;
	}
	;
		
%%

