/* A Bison parser, made by GNU Bison 3.8.2.  */

/* Skeleton implementation for Bison LALR(1) parsers in Java

   Copyright (C) 2007-2015, 2018-2021 Free Software Foundation, Inc.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <https://www.gnu.org/licenses/>.  */

/* As a special exception, you may create a larger work that contains
   part or all of the Bison parser skeleton and distribute that work
   under terms of your choice, so long as that work isn't itself a
   parser generator using the skeleton or a modified version thereof
   as a parser skeleton.  Alternatively, if you modify or redistribute
   the parser skeleton itself, you may (at your option) remove this
   special exception, which will cause the skeleton and the resulting
   Bison output files to be licensed under the GNU General Public
   License without this special exception.

   This special exception was added by the Free Software Foundation in
   version 2.2 of Bison.  */

/* DO NOT RELY ON FEATURES THAT ARE NOT DOCUMENTED in the manual,
   especially those whose name start with YY_ or yy_.  They are
   private implementation details that can be changed or removed.  */


/* First part of user prologue.  */
/* "jflexbison/YYParser.y":1  */

package com.github.esrrhs.fakescript;

import com.github.esrrhs.fakescript.syntree.*;
import java.io.*;


/* "src/main/java/com/github/esrrhs/fakescript/YYParser.java":48  */


import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * A Bison parser, automatically generated from <tt>jflexbison/YYParser.y</tt>.
 *
 * @author LALR (1) parser skeleton written by Paolo Bonzini.
 */
class YYParser
{
  /** Version number for the Bison executable that generated this parser.  */
  public static final String bisonVersion = "3.8.2";

  /** Name of the skeleton that generated this parser.  */
  public static final String bisonSkeleton = "lalr1.java";






  public enum SymbolKind
  {
    S_YYEOF(0),                    /* "end of file"  */
    S_YYerror(1),                  /* error  */
    S_YYUNDEF(2),                  /* "invalid token"  */
    S_VAR_BEGIN(3),                /* VAR_BEGIN  */
    S_RETURN(4),                   /* RETURN  */
    S_BREAK(5),                    /* BREAK  */
    S_FUNC(6),                     /* FUNC  */
    S_WHILE(7),                    /* WHILE  */
    S_FTRUE(8),                    /* FTRUE  */
    S_FFALSE(9),                   /* FFALSE  */
    S_IF(10),                      /* IF  */
    S_THEN(11),                    /* THEN  */
    S_ELSE(12),                    /* ELSE  */
    S_END(13),                     /* END  */
    S_STRING_DEFINITION(14),       /* STRING_DEFINITION  */
    S_IDENTIFIER(15),              /* IDENTIFIER  */
    S_NUMBER(16),                  /* NUMBER  */
    S_SINGLE_LINE_COMMENT(17),     /* SINGLE_LINE_COMMENT  */
    S_DIVIDE_MOD(18),              /* DIVIDE_MOD  */
    S_ARG_SPLITTER(19),            /* ARG_SPLITTER  */
    S_PLUS(20),                    /* PLUS  */
    S_MINUS(21),                   /* MINUS  */
    S_DIVIDE(22),                  /* DIVIDE  */
    S_MULTIPLY(23),                /* MULTIPLY  */
    S_ASSIGN(24),                  /* ASSIGN  */
    S_MORE(25),                    /* MORE  */
    S_LESS(26),                    /* LESS  */
    S_MORE_OR_EQUAL(27),           /* MORE_OR_EQUAL  */
    S_LESS_OR_EQUAL(28),           /* LESS_OR_EQUAL  */
    S_EQUAL(29),                   /* EQUAL  */
    S_NOT_EQUAL(30),               /* NOT_EQUAL  */
    S_OPEN_BRACKET(31),            /* OPEN_BRACKET  */
    S_CLOSE_BRACKET(32),           /* CLOSE_BRACKET  */
    S_AND(33),                     /* AND  */
    S_OR(34),                      /* OR  */
    S_FKFLOAT(35),                 /* FKFLOAT  */
    S_PLUS_ASSIGN(36),             /* PLUS_ASSIGN  */
    S_MINUS_ASSIGN(37),            /* MINUS_ASSIGN  */
    S_DIVIDE_ASSIGN(38),           /* DIVIDE_ASSIGN  */
    S_MULTIPLY_ASSIGN(39),         /* MULTIPLY_ASSIGN  */
    S_DIVIDE_MOD_ASSIGN(40),       /* DIVIDE_MOD_ASSIGN  */
    S_COLON(41),                   /* COLON  */
    S_FOR(42),                     /* FOR  */
    S_INC(43),                     /* INC  */
    S_FAKE(44),                    /* FAKE  */
    S_FKUUID(45),                  /* FKUUID  */
    S_OPEN_SQUARE_BRACKET(46),     /* OPEN_SQUARE_BRACKET  */
    S_CLOSE_SQUARE_BRACKET(47),    /* CLOSE_SQUARE_BRACKET  */
    S_FCONST(48),                  /* FCONST  */
    S_PACKAGE(49),                 /* PACKAGE  */
    S_INCLUDE(50),                 /* INCLUDE  */
    S_IDENTIFIER_DOT(51),          /* IDENTIFIER_DOT  */
    S_IDENTIFIER_POINTER(52),      /* IDENTIFIER_POINTER  */
    S_STRUCT(53),                  /* STRUCT  */
    S_IS(54),                      /* IS  */
    S_NOT(55),                     /* NOT  */
    S_CONTINUE(56),                /* CONTINUE  */
    S_YIELD(57),                   /* YIELD  */
    S_SLEEP(58),                   /* SLEEP  */
    S_SWITCH(59),                  /* SWITCH  */
    S_CASE(60),                    /* CASE  */
    S_DEFAULT(61),                 /* DEFAULT  */
    S_NEW_ASSIGN(62),              /* NEW_ASSIGN  */
    S_ELSEIF(63),                  /* ELSEIF  */
    S_RIGHT_POINTER(64),           /* RIGHT_POINTER  */
    S_STRING_CAT(65),              /* STRING_CAT  */
    S_OPEN_BIG_BRACKET(66),        /* OPEN_BIG_BRACKET  */
    S_CLOSE_BIG_BRACKET(67),       /* CLOSE_BIG_BRACKET  */
    S_NULL(68),                    /* NULL  */
    S_YYACCEPT(69),                /* $accept  */
    S_program(70),                 /* program  */
    S_body(71),                    /* body  */
    S_function_declaration(72),    /* function_declaration  */
    S_function_declaration_arguments(73), /* function_declaration_arguments  */
    S_arg(74),                     /* arg  */
    S_function_call(75),           /* function_call  */
    S_function_call_arguments(76), /* function_call_arguments  */
    S_arg_expr(77),                /* arg_expr  */
    S_block(78),                   /* block  */
    S_stmt(79),                    /* stmt  */
    S_fake_call_stmt(80),          /* fake_call_stmt  */
    S_for_stmt(81),                /* for_stmt  */
    S_for_loop_stmt(82),           /* for_loop_stmt  */
    S_while_stmt(83),              /* while_stmt  */
    S_if_stmt(84),                 /* if_stmt  */
    S_elseif_stmt_list(85),        /* elseif_stmt_list  */
    S_elseif_stmt(86),             /* elseif_stmt  */
    S_else_stmt(87),               /* else_stmt  */
    S_cmp(88),                     /* cmp  */
    S_cmp_value(89),               /* cmp_value  */
    S_return_stmt(90),             /* return_stmt  */
    S_return_value_list(91),       /* return_value_list  */
    S_return_value(92),            /* return_value  */
    S_assign_stmt(93),             /* assign_stmt  */
    S_multi_assign_stmt(94),       /* multi_assign_stmt  */
    S_var_list(95),                /* var_list  */
    S_assign_value(96),            /* assign_value  */
    S_math_assign_stmt(97),        /* math_assign_stmt  */
    S_var(98),                     /* var  */
    S_variable(99),                /* variable  */
    S_expr(100),                   /* expr  */
    S_math_expr(101),              /* math_expr  */
    S_expr_value(102),             /* expr_value  */
    S_break(103),                  /* break  */
    S_continue(104),               /* continue  */
    S_sleep(105),                  /* sleep  */
    S_yield(106),                  /* yield  */
    S_switch_stmt(107),            /* switch_stmt  */
    S_switch_case_list(108),       /* switch_case_list  */
    S_switch_case_define(109),     /* switch_case_define  */
    S_package_head(110),           /* package_head  */
    S_include_head(111),           /* include_head  */
    S_include_define(112),         /* include_define  */
    S_struct_head(113),            /* struct_head  */
    S_struct_define(114),          /* struct_define  */
    S_struct_mem_declaration(115), /* struct_mem_declaration  */
    S_const_head(116),             /* const_head  */
    S_const_define(117),           /* const_define  */
    S_explicit_value(118),         /* explicit_value  */
    S_const_map_list_value(119),   /* const_map_list_value  */
    S_const_map_value(120),        /* const_map_value  */
    S_const_array_list_value(121); /* const_array_list_value  */


    private final int yycode_;

    SymbolKind (int n) {
      this.yycode_ = n;
    }

    private static final SymbolKind[] values_ = {
      SymbolKind.S_YYEOF,
      SymbolKind.S_YYerror,
      SymbolKind.S_YYUNDEF,
      SymbolKind.S_VAR_BEGIN,
      SymbolKind.S_RETURN,
      SymbolKind.S_BREAK,
      SymbolKind.S_FUNC,
      SymbolKind.S_WHILE,
      SymbolKind.S_FTRUE,
      SymbolKind.S_FFALSE,
      SymbolKind.S_IF,
      SymbolKind.S_THEN,
      SymbolKind.S_ELSE,
      SymbolKind.S_END,
      SymbolKind.S_STRING_DEFINITION,
      SymbolKind.S_IDENTIFIER,
      SymbolKind.S_NUMBER,
      SymbolKind.S_SINGLE_LINE_COMMENT,
      SymbolKind.S_DIVIDE_MOD,
      SymbolKind.S_ARG_SPLITTER,
      SymbolKind.S_PLUS,
      SymbolKind.S_MINUS,
      SymbolKind.S_DIVIDE,
      SymbolKind.S_MULTIPLY,
      SymbolKind.S_ASSIGN,
      SymbolKind.S_MORE,
      SymbolKind.S_LESS,
      SymbolKind.S_MORE_OR_EQUAL,
      SymbolKind.S_LESS_OR_EQUAL,
      SymbolKind.S_EQUAL,
      SymbolKind.S_NOT_EQUAL,
      SymbolKind.S_OPEN_BRACKET,
      SymbolKind.S_CLOSE_BRACKET,
      SymbolKind.S_AND,
      SymbolKind.S_OR,
      SymbolKind.S_FKFLOAT,
      SymbolKind.S_PLUS_ASSIGN,
      SymbolKind.S_MINUS_ASSIGN,
      SymbolKind.S_DIVIDE_ASSIGN,
      SymbolKind.S_MULTIPLY_ASSIGN,
      SymbolKind.S_DIVIDE_MOD_ASSIGN,
      SymbolKind.S_COLON,
      SymbolKind.S_FOR,
      SymbolKind.S_INC,
      SymbolKind.S_FAKE,
      SymbolKind.S_FKUUID,
      SymbolKind.S_OPEN_SQUARE_BRACKET,
      SymbolKind.S_CLOSE_SQUARE_BRACKET,
      SymbolKind.S_FCONST,
      SymbolKind.S_PACKAGE,
      SymbolKind.S_INCLUDE,
      SymbolKind.S_IDENTIFIER_DOT,
      SymbolKind.S_IDENTIFIER_POINTER,
      SymbolKind.S_STRUCT,
      SymbolKind.S_IS,
      SymbolKind.S_NOT,
      SymbolKind.S_CONTINUE,
      SymbolKind.S_YIELD,
      SymbolKind.S_SLEEP,
      SymbolKind.S_SWITCH,
      SymbolKind.S_CASE,
      SymbolKind.S_DEFAULT,
      SymbolKind.S_NEW_ASSIGN,
      SymbolKind.S_ELSEIF,
      SymbolKind.S_RIGHT_POINTER,
      SymbolKind.S_STRING_CAT,
      SymbolKind.S_OPEN_BIG_BRACKET,
      SymbolKind.S_CLOSE_BIG_BRACKET,
      SymbolKind.S_NULL,
      SymbolKind.S_YYACCEPT,
      SymbolKind.S_program,
      SymbolKind.S_body,
      SymbolKind.S_function_declaration,
      SymbolKind.S_function_declaration_arguments,
      SymbolKind.S_arg,
      SymbolKind.S_function_call,
      SymbolKind.S_function_call_arguments,
      SymbolKind.S_arg_expr,
      SymbolKind.S_block,
      SymbolKind.S_stmt,
      SymbolKind.S_fake_call_stmt,
      SymbolKind.S_for_stmt,
      SymbolKind.S_for_loop_stmt,
      SymbolKind.S_while_stmt,
      SymbolKind.S_if_stmt,
      SymbolKind.S_elseif_stmt_list,
      SymbolKind.S_elseif_stmt,
      SymbolKind.S_else_stmt,
      SymbolKind.S_cmp,
      SymbolKind.S_cmp_value,
      SymbolKind.S_return_stmt,
      SymbolKind.S_return_value_list,
      SymbolKind.S_return_value,
      SymbolKind.S_assign_stmt,
      SymbolKind.S_multi_assign_stmt,
      SymbolKind.S_var_list,
      SymbolKind.S_assign_value,
      SymbolKind.S_math_assign_stmt,
      SymbolKind.S_var,
      SymbolKind.S_variable,
      SymbolKind.S_expr,
      SymbolKind.S_math_expr,
      SymbolKind.S_expr_value,
      SymbolKind.S_break,
      SymbolKind.S_continue,
      SymbolKind.S_sleep,
      SymbolKind.S_yield,
      SymbolKind.S_switch_stmt,
      SymbolKind.S_switch_case_list,
      SymbolKind.S_switch_case_define,
      SymbolKind.S_package_head,
      SymbolKind.S_include_head,
      SymbolKind.S_include_define,
      SymbolKind.S_struct_head,
      SymbolKind.S_struct_define,
      SymbolKind.S_struct_mem_declaration,
      SymbolKind.S_const_head,
      SymbolKind.S_const_define,
      SymbolKind.S_explicit_value,
      SymbolKind.S_const_map_list_value,
      SymbolKind.S_const_map_value,
      SymbolKind.S_const_array_list_value
    };

    static final SymbolKind get(int code) {
      return values_[code];
    }

    public final int getCode() {
      return this.yycode_;
    }

    /* Return YYSTR after stripping away unnecessary quotes and
       backslashes, so that it's suitable for yyerror.  The heuristic is
       that double-quoting is unnecessary unless the string contains an
       apostrophe, a comma, or backslash (other than backslash-backslash).
       YYSTR is taken from yytname.  */
    private static String yytnamerr_(String yystr)
    {
      if (yystr.charAt (0) == '"')
        {
          StringBuffer yyr = new StringBuffer();
          strip_quotes: for (int i = 1; i < yystr.length(); i++)
            switch (yystr.charAt(i))
              {
              case '\'':
              case ',':
                break strip_quotes;

              case '\\':
                if (yystr.charAt(++i) != '\\')
                  break strip_quotes;
                /* Fall through.  */
              default:
                yyr.append(yystr.charAt(i));
                break;

              case '"':
                return yyr.toString();
              }
        }
      return yystr;
    }

    /* YYTNAME[SYMBOL-NUM] -- String name of the symbol SYMBOL-NUM.
       First, the terminals, then, starting at \a YYNTOKENS_, nonterminals.  */
    private static final String[] yytname_ = yytname_init();
  private static final String[] yytname_init()
  {
    return new String[]
    {
  "\"end of file\"", "error", "\"invalid token\"", "VAR_BEGIN", "RETURN",
  "BREAK", "FUNC", "WHILE", "FTRUE", "FFALSE", "IF", "THEN", "ELSE", "END",
  "STRING_DEFINITION", "IDENTIFIER", "NUMBER", "SINGLE_LINE_COMMENT",
  "DIVIDE_MOD", "ARG_SPLITTER", "PLUS", "MINUS", "DIVIDE", "MULTIPLY",
  "ASSIGN", "MORE", "LESS", "MORE_OR_EQUAL", "LESS_OR_EQUAL", "EQUAL",
  "NOT_EQUAL", "OPEN_BRACKET", "CLOSE_BRACKET", "AND", "OR", "FKFLOAT",
  "PLUS_ASSIGN", "MINUS_ASSIGN", "DIVIDE_ASSIGN", "MULTIPLY_ASSIGN",
  "DIVIDE_MOD_ASSIGN", "COLON", "FOR", "INC", "FAKE", "FKUUID",
  "OPEN_SQUARE_BRACKET", "CLOSE_SQUARE_BRACKET", "FCONST", "PACKAGE",
  "INCLUDE", "IDENTIFIER_DOT", "IDENTIFIER_POINTER", "STRUCT", "IS", "NOT",
  "CONTINUE", "YIELD", "SLEEP", "SWITCH", "CASE", "DEFAULT", "NEW_ASSIGN",
  "ELSEIF", "RIGHT_POINTER", "STRING_CAT", "OPEN_BIG_BRACKET",
  "CLOSE_BIG_BRACKET", "NULL", "$accept", "program", "body",
  "function_declaration", "function_declaration_arguments", "arg",
  "function_call", "function_call_arguments", "arg_expr", "block", "stmt",
  "fake_call_stmt", "for_stmt", "for_loop_stmt", "while_stmt", "if_stmt",
  "elseif_stmt_list", "elseif_stmt", "else_stmt", "cmp", "cmp_value",
  "return_stmt", "return_value_list", "return_value", "assign_stmt",
  "multi_assign_stmt", "var_list", "assign_value", "math_assign_stmt",
  "var", "variable", "expr", "math_expr", "expr_value", "break",
  "continue", "sleep", "yield", "switch_stmt", "switch_case_list",
  "switch_case_define", "package_head", "include_head", "include_define",
  "struct_head", "struct_define", "struct_mem_declaration", "const_head",
  "const_define", "explicit_value", "const_map_list_value",
  "const_map_value", "const_array_list_value", null
    };
  }

    /* The user-facing name of this symbol.  */
    public final String getName() {
      return yytnamerr_(yytname_[yycode_]);
    }

  };


  /**
   * Communication interface between the scanner and the Bison-generated
   * parser <tt>YYParser</tt>.
   */
  public interface Lexer {
    /* Token kinds.  */
    /** Token "end of file", to be returned by the scanner.  */
    static final int YYEOF = 0;
    /** Token error, to be returned by the scanner.  */
    static final int YYerror = 256;
    /** Token "invalid token", to be returned by the scanner.  */
    static final int YYUNDEF = 257;
    /** Token VAR_BEGIN, to be returned by the scanner.  */
    static final int VAR_BEGIN = 258;
    /** Token RETURN, to be returned by the scanner.  */
    static final int RETURN = 259;
    /** Token BREAK, to be returned by the scanner.  */
    static final int BREAK = 260;
    /** Token FUNC, to be returned by the scanner.  */
    static final int FUNC = 261;
    /** Token WHILE, to be returned by the scanner.  */
    static final int WHILE = 262;
    /** Token FTRUE, to be returned by the scanner.  */
    static final int FTRUE = 263;
    /** Token FFALSE, to be returned by the scanner.  */
    static final int FFALSE = 264;
    /** Token IF, to be returned by the scanner.  */
    static final int IF = 265;
    /** Token THEN, to be returned by the scanner.  */
    static final int THEN = 266;
    /** Token ELSE, to be returned by the scanner.  */
    static final int ELSE = 267;
    /** Token END, to be returned by the scanner.  */
    static final int END = 268;
    /** Token STRING_DEFINITION, to be returned by the scanner.  */
    static final int STRING_DEFINITION = 269;
    /** Token IDENTIFIER, to be returned by the scanner.  */
    static final int IDENTIFIER = 270;
    /** Token NUMBER, to be returned by the scanner.  */
    static final int NUMBER = 271;
    /** Token SINGLE_LINE_COMMENT, to be returned by the scanner.  */
    static final int SINGLE_LINE_COMMENT = 272;
    /** Token DIVIDE_MOD, to be returned by the scanner.  */
    static final int DIVIDE_MOD = 273;
    /** Token ARG_SPLITTER, to be returned by the scanner.  */
    static final int ARG_SPLITTER = 274;
    /** Token PLUS, to be returned by the scanner.  */
    static final int PLUS = 275;
    /** Token MINUS, to be returned by the scanner.  */
    static final int MINUS = 276;
    /** Token DIVIDE, to be returned by the scanner.  */
    static final int DIVIDE = 277;
    /** Token MULTIPLY, to be returned by the scanner.  */
    static final int MULTIPLY = 278;
    /** Token ASSIGN, to be returned by the scanner.  */
    static final int ASSIGN = 279;
    /** Token MORE, to be returned by the scanner.  */
    static final int MORE = 280;
    /** Token LESS, to be returned by the scanner.  */
    static final int LESS = 281;
    /** Token MORE_OR_EQUAL, to be returned by the scanner.  */
    static final int MORE_OR_EQUAL = 282;
    /** Token LESS_OR_EQUAL, to be returned by the scanner.  */
    static final int LESS_OR_EQUAL = 283;
    /** Token EQUAL, to be returned by the scanner.  */
    static final int EQUAL = 284;
    /** Token NOT_EQUAL, to be returned by the scanner.  */
    static final int NOT_EQUAL = 285;
    /** Token OPEN_BRACKET, to be returned by the scanner.  */
    static final int OPEN_BRACKET = 286;
    /** Token CLOSE_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_BRACKET = 287;
    /** Token AND, to be returned by the scanner.  */
    static final int AND = 288;
    /** Token OR, to be returned by the scanner.  */
    static final int OR = 289;
    /** Token FKFLOAT, to be returned by the scanner.  */
    static final int FKFLOAT = 290;
    /** Token PLUS_ASSIGN, to be returned by the scanner.  */
    static final int PLUS_ASSIGN = 291;
    /** Token MINUS_ASSIGN, to be returned by the scanner.  */
    static final int MINUS_ASSIGN = 292;
    /** Token DIVIDE_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_ASSIGN = 293;
    /** Token MULTIPLY_ASSIGN, to be returned by the scanner.  */
    static final int MULTIPLY_ASSIGN = 294;
    /** Token DIVIDE_MOD_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_MOD_ASSIGN = 295;
    /** Token COLON, to be returned by the scanner.  */
    static final int COLON = 296;
    /** Token FOR, to be returned by the scanner.  */
    static final int FOR = 297;
    /** Token INC, to be returned by the scanner.  */
    static final int INC = 298;
    /** Token FAKE, to be returned by the scanner.  */
    static final int FAKE = 299;
    /** Token FKUUID, to be returned by the scanner.  */
    static final int FKUUID = 300;
    /** Token OPEN_SQUARE_BRACKET, to be returned by the scanner.  */
    static final int OPEN_SQUARE_BRACKET = 301;
    /** Token CLOSE_SQUARE_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_SQUARE_BRACKET = 302;
    /** Token FCONST, to be returned by the scanner.  */
    static final int FCONST = 303;
    /** Token PACKAGE, to be returned by the scanner.  */
    static final int PACKAGE = 304;
    /** Token INCLUDE, to be returned by the scanner.  */
    static final int INCLUDE = 305;
    /** Token IDENTIFIER_DOT, to be returned by the scanner.  */
    static final int IDENTIFIER_DOT = 306;
    /** Token IDENTIFIER_POINTER, to be returned by the scanner.  */
    static final int IDENTIFIER_POINTER = 307;
    /** Token STRUCT, to be returned by the scanner.  */
    static final int STRUCT = 308;
    /** Token IS, to be returned by the scanner.  */
    static final int IS = 309;
    /** Token NOT, to be returned by the scanner.  */
    static final int NOT = 310;
    /** Token CONTINUE, to be returned by the scanner.  */
    static final int CONTINUE = 311;
    /** Token YIELD, to be returned by the scanner.  */
    static final int YIELD = 312;
    /** Token SLEEP, to be returned by the scanner.  */
    static final int SLEEP = 313;
    /** Token SWITCH, to be returned by the scanner.  */
    static final int SWITCH = 314;
    /** Token CASE, to be returned by the scanner.  */
    static final int CASE = 315;
    /** Token DEFAULT, to be returned by the scanner.  */
    static final int DEFAULT = 316;
    /** Token NEW_ASSIGN, to be returned by the scanner.  */
    static final int NEW_ASSIGN = 317;
    /** Token ELSEIF, to be returned by the scanner.  */
    static final int ELSEIF = 318;
    /** Token RIGHT_POINTER, to be returned by the scanner.  */
    static final int RIGHT_POINTER = 319;
    /** Token STRING_CAT, to be returned by the scanner.  */
    static final int STRING_CAT = 320;
    /** Token OPEN_BIG_BRACKET, to be returned by the scanner.  */
    static final int OPEN_BIG_BRACKET = 321;
    /** Token CLOSE_BIG_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_BIG_BRACKET = 322;
    /** Token NULL, to be returned by the scanner.  */
    static final int NULL = 323;

    /** Deprecated, use YYEOF instead.  */
    public static final int EOF = YYEOF;


    /**
     * Method to retrieve the semantic value of the last scanned token.
     * @return the semantic value of the last scanned token.
     */
    Object getLVal();

    /**
     * Entry point for the scanner.  Returns the token identifier corresponding
     * to the next token and prepares to return the semantic value
     * of the token.
     * @return the token identifier corresponding to the next token.
     */
    int yylex() throws java.io.IOException;

    /**
     * Emit an errorin a user-defined way.
     *
     *
     * @param msg The string for the error message.
     */
     void yyerror(String msg);


  }


  /**
   * The object doing lexical analysis for us.
   */
  private Lexer yylexer;





  /**
   * Instantiates the Bison-generated parser.
   * @param yylexer The scanner that will supply tokens to the parser.
   */
  public YYParser(Lexer yylexer)
  {

    this.yylexer = yylexer;

  }



  private int yynerrs = 0;

  /**
   * The number of syntax errors so far.
   */
  public final int getNumberOfErrors() { return yynerrs; }

  /**
   * Print an error message via the lexer.
   *
   * @param msg The error message.
   */
  public final void yyerror(String msg) {
      yylexer.yyerror(msg);
  }



  private final class YYStack {
    private int[] stateStack = new int[16];
    private Object[] valueStack = new Object[16];

    public int size = 16;
    public int height = -1;

    public final void push(int state, Object value) {
      height++;
      if (size == height) {
        int[] newStateStack = new int[size * 2];
        System.arraycopy(stateStack, 0, newStateStack, 0, height);
        stateStack = newStateStack;

        Object[] newValueStack = new Object[size * 2];
        System.arraycopy(valueStack, 0, newValueStack, 0, height);
        valueStack = newValueStack;

        size *= 2;
      }

      stateStack[height] = state;
      valueStack[height] = value;
    }

    public final void pop() {
      pop(1);
    }

    public final void pop(int num) {
      // Avoid memory leaks... garbage collection is a white lie!
      if (0 < num) {
        java.util.Arrays.fill(valueStack, height - num + 1, height + 1, null);
      }
      height -= num;
    }

    public final int stateAt(int i) {
      return stateStack[height - i];
    }

    public final Object valueAt(int i) {
      return valueStack[height - i];
    }

    // Print the state stack on the debug stream.
    public void print(java.io.PrintStream out) {
      out.print ("Stack now");

      for (int i = 0; i <= height; i++) {
        out.print(' ');
        out.print(stateStack[i]);
      }
      out.println();
    }
  }

  /**
   * Returned by a Bison action in order to stop the parsing process and
   * return success (<tt>true</tt>).
   */
  public static final int YYACCEPT = 0;

  /**
   * Returned by a Bison action in order to stop the parsing process and
   * return failure (<tt>false</tt>).
   */
  public static final int YYABORT = 1;



  /**
   * Returned by a Bison action in order to start error recovery without
   * printing an error message.
   */
  public static final int YYERROR = 2;

  /**
   * Internal return codes that are not supported for user semantic
   * actions.
   */
  private static final int YYERRLAB = 3;
  private static final int YYNEWSTATE = 4;
  private static final int YYDEFAULT = 5;
  private static final int YYREDUCE = 6;
  private static final int YYERRLAB1 = 7;
  private static final int YYRETURN = 8;


  private int yyerrstatus_ = 0;


  /**
   * Whether error recovery is being done.  In this state, the parser
   * reads token until it reaches a known state, and then restarts normal
   * operation.
   */
  public final boolean recovering ()
  {
    return yyerrstatus_ == 0;
  }

  /** Compute post-reduction state.
   * @param yystate   the current state
   * @param yysym     the nonterminal to push on the stack
   */
  private int yyLRGotoState(int yystate, int yysym) {
    int yyr = yypgoto_[yysym - YYNTOKENS_] + yystate;
    if (0 <= yyr && yyr <= YYLAST_ && yycheck_[yyr] == yystate)
      return yytable_[yyr];
    else
      return yydefgoto_[yysym - YYNTOKENS_];
  }

  private int yyaction(int yyn, YYStack yystack, int yylen)
  {
    /* If YYLEN is nonzero, implement the default value of the action:
       '$$ = $1'.  Otherwise, use the top of the stack.

       Otherwise, the following line sets YYVAL to garbage.
       This behavior is undocumented and Bison
       users should not rely upon it.  */
    Object yyval = (0 < yylen) ? yystack.valueAt(yylen - 1) : yystack.valueAt(0);

    switch (yyn)
      {
          case 3: /* body: %empty  */
  if (yyn == 3)
    /* "jflexbison/YYParser.y":99  */
        {
	};
  break;


  case 6: /* function_declaration: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END  */
  if (yyn == 6)
    /* "jflexbison/YYParser.y":109  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END");
		func_desc_node p = ((Yylex)yylexer).new_node(func_desc_node.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		p.m_funcname = ((ParserVal)((ParserVal)yystack.valueAt (5))).sval;
		p.m_arglist = (func_desc_arglist_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_endline = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		((Yylex)yylexer).get_mybison().add_func_desc(p);
	};
  break;


  case 7: /* function_declaration: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET END  */
  if (yyn == 7)
    /* "jflexbison/YYParser.y":120  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET END");
		func_desc_node p = ((Yylex)yylexer).new_node(func_desc_node.class, ((ParserVal)((ParserVal)yystack.valueAt (4))).ival);
		p.m_funcname = ((ParserVal)((ParserVal)yystack.valueAt (4))).sval;
		p.m_arglist = (func_desc_arglist_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_endline = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		((Yylex)yylexer).get_mybison().add_func_desc(p);
	};
  break;


  case 8: /* function_declaration_arguments: %empty  */
  if (yyn == 8)
    /* "jflexbison/YYParser.y":132  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
	};
  break;


  case 9: /* function_declaration_arguments: function_declaration_arguments ARG_SPLITTER arg  */
  if (yyn == 9)
    /* "jflexbison/YYParser.y":137  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_declaration_arguments ARG_SPLITTER arg ");
		func_desc_arglist_node p = (func_desc_arglist_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 10: /* function_declaration_arguments: arg  */
  if (yyn == 10)
    /* "jflexbison/YYParser.y":148  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: arg");
		func_desc_arglist_node p = ((Yylex)yylexer).new_node(func_desc_arglist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 11: /* arg: IDENTIFIER  */
  if (yyn == 11)
    /* "jflexbison/YYParser.y":161  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		identifier_node p = ((Yylex)yylexer).new_node(identifier_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 12: /* function_call: IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET  */
  if (yyn == 12)
    /* "jflexbison/YYParser.y":175  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_fuc = ((ParserVal)((ParserVal)yystack.valueAt (3))).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 13: /* function_call: IDENTIFIER_DOT OPEN_BRACKET function_call_arguments CLOSE_BRACKET  */
  if (yyn == 13)
    /* "jflexbison/YYParser.y":189  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_fuc = ((ParserVal)((ParserVal)yystack.valueAt (3))).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 14: /* function_call: function_call OPEN_BRACKET function_call_arguments CLOSE_BRACKET  */
  if (yyn == 14)
    /* "jflexbison/YYParser.y":203  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_prefuc = (syntree_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_arglist = (function_call_arglist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_fakecall = false;
		p.m_classmem_call = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 15: /* function_call: variable COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET  */
  if (yyn == 15)
    /* "jflexbison/YYParser.y":217  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		p.m_fuc = ((ParserVal)((ParserVal)yystack.valueAt (3))).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)yystack.valueAt (1)).obj;
		if (p.m_arglist == null)
		{
			p.m_arglist = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		}
		p.m_arglist.add_arg((syntree_node)((ParserVal)yystack.valueAt (5)).obj);
		p.m_fakecall = false;
		p.m_classmem_call = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 16: /* function_call: function_call COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET  */
  if (yyn == 16)
    /* "jflexbison/YYParser.y":236  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call COLON IDENTIFIER OPEN_BRACKET function_call_arguments CLOSE_BRACKET ");
		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		p.m_fuc = ((ParserVal)((ParserVal)yystack.valueAt (3))).sval;
		p.m_arglist = (function_call_arglist_node)((ParserVal)yystack.valueAt (1)).obj;
		if (p.m_arglist == null)
		{
			p.m_arglist = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		}
		p.m_arglist.add_arg((syntree_node)((ParserVal)yystack.valueAt (5)).obj);
		p.m_fakecall = false;
		p.m_classmem_call = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 17: /* function_call_arguments: %empty  */
  if (yyn == 17)
    /* "jflexbison/YYParser.y":257  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
	};
  break;


  case 18: /* function_call_arguments: function_call_arguments ARG_SPLITTER arg_expr  */
  if (yyn == 18)
    /* "jflexbison/YYParser.y":262  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call_arguments ARG_SPLITTER arg_expr ");
		function_call_arglist_node p = (function_call_arglist_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 19: /* function_call_arguments: arg_expr  */
  if (yyn == 19)
    /* "jflexbison/YYParser.y":273  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: arg_expr ");
		function_call_arglist_node p = ((Yylex)yylexer).new_node(function_call_arglist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 20: /* arg_expr: expr_value  */
  if (yyn == 20)
    /* "jflexbison/YYParser.y":286  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 21: /* block: block stmt  */
  if (yyn == 21)
    /* "jflexbison/YYParser.y":296  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: block stmt ");
		block_node p = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 22: /* block: stmt  */
  if (yyn == 22)
    /* "jflexbison/YYParser.y":307  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: stmt");
		block_node p = ((Yylex)yylexer).new_node(block_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 23: /* stmt: while_stmt  */
  if (yyn == 23)
    /* "jflexbison/YYParser.y":320  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: while_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 24: /* stmt: if_stmt  */
  if (yyn == 24)
    /* "jflexbison/YYParser.y":326  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: if_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 25: /* stmt: return_stmt  */
  if (yyn == 25)
    /* "jflexbison/YYParser.y":332  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 26: /* stmt: assign_stmt  */
  if (yyn == 26)
    /* "jflexbison/YYParser.y":338  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 27: /* stmt: multi_assign_stmt  */
  if (yyn == 27)
    /* "jflexbison/YYParser.y":344  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: multi_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 28: /* stmt: break  */
  if (yyn == 28)
    /* "jflexbison/YYParser.y":350  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: break");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 29: /* stmt: continue  */
  if (yyn == 29)
    /* "jflexbison/YYParser.y":356  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: continue");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 30: /* stmt: expr  */
  if (yyn == 30)
    /* "jflexbison/YYParser.y":362  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 31: /* stmt: math_assign_stmt  */
  if (yyn == 31)
    /* "jflexbison/YYParser.y":368  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 32: /* stmt: for_stmt  */
  if (yyn == 32)
    /* "jflexbison/YYParser.y":374  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 33: /* stmt: for_loop_stmt  */
  if (yyn == 33)
    /* "jflexbison/YYParser.y":380  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_loop_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 34: /* stmt: fake_call_stmt  */
  if (yyn == 34)
    /* "jflexbison/YYParser.y":386  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: fake_call_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 35: /* stmt: sleep  */
  if (yyn == 35)
    /* "jflexbison/YYParser.y":392  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: sleep_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 36: /* stmt: yield  */
  if (yyn == 36)
    /* "jflexbison/YYParser.y":398  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: yield_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 37: /* stmt: switch_stmt  */
  if (yyn == 37)
    /* "jflexbison/YYParser.y":404  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 38: /* fake_call_stmt: FAKE function_call  */
  if (yyn == 38)
    /* "jflexbison/YYParser.y":412  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FAKE function_call");
		function_call_node p = (function_call_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_fakecall = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 39: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN block END  */
  if (yyn == 39)
    /* "jflexbison/YYParser.y":425  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN block END");
		for_stmt p = ((Yylex)yylexer).new_node(for_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (7))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (5)).obj;
		p.m_beginblock = (block_node)((ParserVal)yystack.valueAt (7)).obj;
		p.m_endblock = (block_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 40: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN END  */
  if (yyn == 40)
    /* "jflexbison/YYParser.y":439  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block THEN END");
		for_stmt p = ((Yylex)yylexer).new_node(for_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (6))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (4)).obj;
		p.m_beginblock = (block_node)((ParserVal)yystack.valueAt (6)).obj;
		p.m_endblock = (block_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 41: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN block END  */
  if (yyn == 41)
    /* "jflexbison/YYParser.y":455  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN block END");
		for_loop_stmt p = ((Yylex)yylexer).new_node(for_loop_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (9))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (9)).obj;
		p.m_begin = (syntree_node)((ParserVal)yystack.valueAt (7)).obj;
		p.m_end = (syntree_node)((ParserVal)yystack.valueAt (5)).obj;
		p.m_add = (syntree_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 42: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN END  */
  if (yyn == 42)
    /* "jflexbison/YYParser.y":470  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value THEN END");
		for_loop_stmt p = ((Yylex)yylexer).new_node(for_loop_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (8))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (8)).obj;
		p.m_begin = (syntree_node)((ParserVal)yystack.valueAt (6)).obj;
		p.m_end = (syntree_node)((ParserVal)yystack.valueAt (4)).obj;
		p.m_add = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 43: /* while_stmt: WHILE cmp THEN block END  */
  if (yyn == 43)
    /* "jflexbison/YYParser.y":487  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp THEN block END ");
		while_stmt p = ((Yylex)yylexer).new_node(while_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (3)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 44: /* while_stmt: WHILE cmp THEN END  */
  if (yyn == 44)
    /* "jflexbison/YYParser.y":499  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp THEN END ");
		while_stmt p = ((Yylex)yylexer).new_node(while_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (2)).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 45: /* if_stmt: IF cmp THEN block elseif_stmt_list else_stmt END  */
  if (yyn == 45)
    /* "jflexbison/YYParser.y":513  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IF cmp THEN block elseif_stmt_list else_stmt END");
		if_stmt p = ((Yylex)yylexer).new_node(if_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (5))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (5)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_elseifs = (elseif_stmt_list)((ParserVal)yystack.valueAt (2)).obj;
		p.m_elses = (else_stmt)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 46: /* if_stmt: IF cmp THEN elseif_stmt_list else_stmt END  */
  if (yyn == 46)
    /* "jflexbison/YYParser.y":527  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IF cmp THEN elseif_stmt_list else_stmt END");
		if_stmt p = ((Yylex)yylexer).new_node(if_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (4))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (4)).obj;
		p.m_block = null;
		p.m_elseifs = (elseif_stmt_list)((ParserVal)yystack.valueAt (2)).obj;
		p.m_elses = (else_stmt)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 47: /* elseif_stmt_list: %empty  */
  if (yyn == 47)
    /* "jflexbison/YYParser.y":543  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 48: /* elseif_stmt_list: elseif_stmt_list elseif_stmt  */
  if (yyn == 48)
    /* "jflexbison/YYParser.y":552  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt_list elseif_stmt");
		elseif_stmt_list p = (elseif_stmt_list)((ParserVal)yystack.valueAt (1)).obj;
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 49: /* elseif_stmt_list: elseif_stmt  */
  if (yyn == 49)
    /* "jflexbison/YYParser.y":563  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt");
		elseif_stmt_list p = ((Yylex)yylexer).new_node(elseif_stmt_list.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 50: /* elseif_stmt: ELSEIF cmp THEN block  */
  if (yyn == 50)
    /* "jflexbison/YYParser.y":576  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSEIF cmp THEN block");
		elseif_stmt p = ((Yylex)yylexer).new_node(elseif_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (2)).obj;
		p.m_block = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 51: /* elseif_stmt: ELSEIF cmp THEN  */
  if (yyn == 51)
    /* "jflexbison/YYParser.y":588  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSEIF cmp THEN");
		elseif_stmt p = ((Yylex)yylexer).new_node(elseif_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (1)).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 52: /* else_stmt: %empty  */
  if (yyn == 52)
    /* "jflexbison/YYParser.y":602  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 53: /* else_stmt: ELSE block  */
  if (yyn == 53)
    /* "jflexbison/YYParser.y":611  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE block");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_block = (block_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 54: /* else_stmt: ELSE  */
  if (yyn == 54)
    /* "jflexbison/YYParser.y":622  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 55: /* cmp: OPEN_BRACKET cmp CLOSE_BRACKET  */
  if (yyn == 55)
    /* "jflexbison/YYParser.y":635  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET cmp CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 56: /* cmp: cmp AND cmp  */
  if (yyn == 56)
    /* "jflexbison/YYParser.y":641  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp AND cmp");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "&&";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 57: /* cmp: cmp OR cmp  */
  if (yyn == 57)
    /* "jflexbison/YYParser.y":654  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp OR cmp");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "||";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 58: /* cmp: cmp_value LESS cmp_value  */
  if (yyn == 58)
    /* "jflexbison/YYParser.y":667  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value LESS cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "<";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 59: /* cmp: cmp_value MORE cmp_value  */
  if (yyn == 59)
    /* "jflexbison/YYParser.y":680  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value MORE cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = ">";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 60: /* cmp: cmp_value EQUAL cmp_value  */
  if (yyn == 60)
    /* "jflexbison/YYParser.y":693  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "==";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 61: /* cmp: cmp_value MORE_OR_EQUAL cmp_value  */
  if (yyn == 61)
    /* "jflexbison/YYParser.y":706  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value MORE_OR_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = ">=";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 62: /* cmp: cmp_value LESS_OR_EQUAL cmp_value  */
  if (yyn == 62)
    /* "jflexbison/YYParser.y":719  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value LESS_OR_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "<=";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 63: /* cmp: cmp_value NOT_EQUAL cmp_value  */
  if (yyn == 63)
    /* "jflexbison/YYParser.y":732  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value NOT_EQUAL cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = "!=";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 64: /* cmp: FTRUE  */
  if (yyn == 64)
    /* "jflexbison/YYParser.y":745  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_cmp = "true";
		p.m_left = null;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 65: /* cmp: FFALSE  */
  if (yyn == 65)
    /* "jflexbison/YYParser.y":758  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_cmp = "false";
		p.m_left = null;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 66: /* cmp: IS cmp_value  */
  if (yyn == 66)
    /* "jflexbison/YYParser.y":771  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IS cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_cmp = "is";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 67: /* cmp: NOT cmp_value  */
  if (yyn == 67)
    /* "jflexbison/YYParser.y":784  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NOT cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_cmp = "not";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_right = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 68: /* cmp_value: explicit_value  */
  if (yyn == 68)
    /* "jflexbison/YYParser.y":799  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 69: /* cmp_value: variable  */
  if (yyn == 69)
    /* "jflexbison/YYParser.y":805  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 70: /* cmp_value: expr  */
  if (yyn == 70)
    /* "jflexbison/YYParser.y":811  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 71: /* return_stmt: RETURN return_value_list  */
  if (yyn == 71)
    /* "jflexbison/YYParser.y":819  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN return_value_list");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_returnlist = (return_value_list_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 72: /* return_stmt: RETURN  */
  if (yyn == 72)
    /* "jflexbison/YYParser.y":830  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_returnlist = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 73: /* return_value_list: return_value_list ARG_SPLITTER return_value  */
  if (yyn == 73)
    /* "jflexbison/YYParser.y":843  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value_list ARG_SPLITTER return_value");
		return_value_list_node p = (return_value_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 74: /* return_value_list: return_value  */
  if (yyn == 74)
    /* "jflexbison/YYParser.y":854  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value");
		return_value_list_node p = ((Yylex)yylexer).new_node(return_value_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 75: /* return_value: explicit_value  */
  if (yyn == 75)
    /* "jflexbison/YYParser.y":867  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 76: /* return_value: variable  */
  if (yyn == 76)
    /* "jflexbison/YYParser.y":873  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 77: /* return_value: expr  */
  if (yyn == 77)
    /* "jflexbison/YYParser.y":879  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 78: /* assign_stmt: var ASSIGN assign_value  */
  if (yyn == 78)
    /* "jflexbison/YYParser.y":887  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var ASSIGN assign_value");
		assign_stmt p = ((Yylex)yylexer).new_node(assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_isnew = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 79: /* assign_stmt: var NEW_ASSIGN assign_value  */
  if (yyn == 79)
    /* "jflexbison/YYParser.y":900  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var NEW_ASSIGN assign_value");
		assign_stmt p = ((Yylex)yylexer).new_node(assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_isnew = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 80: /* multi_assign_stmt: var_list ASSIGN function_call  */
  if (yyn == 80)
    /* "jflexbison/YYParser.y":915  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ASSIGN function_call");
		multi_assign_stmt p = ((Yylex)yylexer).new_node(multi_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_varlist = (var_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_isnew = false;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 81: /* multi_assign_stmt: var_list NEW_ASSIGN function_call  */
  if (yyn == 81)
    /* "jflexbison/YYParser.y":928  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list NEW_ASSIGN function_call");
		multi_assign_stmt p = ((Yylex)yylexer).new_node(multi_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_varlist = (var_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_isnew = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 82: /* var_list: var_list ARG_SPLITTER var  */
  if (yyn == 82)
    /* "jflexbison/YYParser.y":943  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ARG_SPLITTER var");
		var_list_node p = (var_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 83: /* var_list: var  */
  if (yyn == 83)
    /* "jflexbison/YYParser.y":954  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var");
		var_list_node p = ((Yylex)yylexer).new_node(var_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 84: /* assign_value: explicit_value  */
  if (yyn == 84)
    /* "jflexbison/YYParser.y":967  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 85: /* assign_value: variable  */
  if (yyn == 85)
    /* "jflexbison/YYParser.y":973  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 86: /* assign_value: expr  */
  if (yyn == 86)
    /* "jflexbison/YYParser.y":979  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 87: /* math_assign_stmt: variable PLUS_ASSIGN assign_value  */
  if (yyn == 87)
    /* "jflexbison/YYParser.y":987  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable PLUS_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "+=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 88: /* math_assign_stmt: variable MINUS_ASSIGN assign_value  */
  if (yyn == 88)
    /* "jflexbison/YYParser.y":1000  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable MINUS_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "-=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 89: /* math_assign_stmt: variable DIVIDE_ASSIGN assign_value  */
  if (yyn == 89)
    /* "jflexbison/YYParser.y":1013  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable DIVIDE_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "/=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 90: /* math_assign_stmt: variable MULTIPLY_ASSIGN assign_value  */
  if (yyn == 90)
    /* "jflexbison/YYParser.y":1026  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable MULTIPLY_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "*=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 91: /* math_assign_stmt: variable DIVIDE_MOD_ASSIGN assign_value  */
  if (yyn == 91)
    /* "jflexbison/YYParser.y":1039  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable DIVIDE_MOD_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "%=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 92: /* math_assign_stmt: variable INC  */
  if (yyn == 92)
    /* "jflexbison/YYParser.y":1052  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable INC");
		explicit_value_node pp = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		pp.m_str = "1";
		pp.m_type = explicit_value_type.EVT_NUM;
		
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_oper = "+=";
		p.m_value = pp;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 93: /* var: VAR_BEGIN IDENTIFIER  */
  if (yyn == 93)
    /* "jflexbison/YYParser.y":1071  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: VAR_BEGIN IDENTIFIER");
		var_node p = ((Yylex)yylexer).new_node(var_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 94: /* var: variable  */
  if (yyn == 94)
    /* "jflexbison/YYParser.y":1082  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 95: /* variable: IDENTIFIER  */
  if (yyn == 95)
    /* "jflexbison/YYParser.y":1090  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 96: /* variable: IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 96)
    /* "jflexbison/YYParser.y":1101  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET");
		container_get_node p = ((Yylex)yylexer).new_node(container_get_node.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_container = ((ParserVal)((ParserVal)yystack.valueAt (3))).sval;
		p.m_key = (syntree_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 97: /* variable: IDENTIFIER_POINTER  */
  if (yyn == 97)
    /* "jflexbison/YYParser.y":1113  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_POINTER");
		struct_pointer_node p = ((Yylex)yylexer).new_node(struct_pointer_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 98: /* variable: IDENTIFIER_DOT  */
  if (yyn == 98)
    /* "jflexbison/YYParser.y":1124  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 99: /* expr: OPEN_BRACKET expr CLOSE_BRACKET  */
  if (yyn == 99)
    /* "jflexbison/YYParser.y":1137  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET expr CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 100: /* expr: function_call  */
  if (yyn == 100)
    /* "jflexbison/YYParser.y":1143  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 101: /* expr: math_expr  */
  if (yyn == 101)
    /* "jflexbison/YYParser.y":1149  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 102: /* math_expr: OPEN_BRACKET math_expr CLOSE_BRACKET  */
  if (yyn == 102)
    /* "jflexbison/YYParser.y":1157  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET math_expr CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 103: /* math_expr: expr_value PLUS expr_value  */
  if (yyn == 103)
    /* "jflexbison/YYParser.y":1163  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value PLUS expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "+";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 104: /* math_expr: expr_value MINUS expr_value  */
  if (yyn == 104)
    /* "jflexbison/YYParser.y":1176  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value MINUS expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "-";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 105: /* math_expr: expr_value MULTIPLY expr_value  */
  if (yyn == 105)
    /* "jflexbison/YYParser.y":1189  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value MULTIPLY expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "*";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 106: /* math_expr: expr_value DIVIDE expr_value  */
  if (yyn == 106)
    /* "jflexbison/YYParser.y":1202  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value DIVIDE expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "/";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 107: /* math_expr: expr_value DIVIDE_MOD expr_value  */
  if (yyn == 107)
    /* "jflexbison/YYParser.y":1215  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value DIVIDE_MOD expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "%";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 108: /* math_expr: expr_value STRING_CAT expr_value  */
  if (yyn == 108)
    /* "jflexbison/YYParser.y":1228  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value STRING_CAT expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "..";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 109: /* expr_value: math_expr  */
  if (yyn == 109)
    /* "jflexbison/YYParser.y":1243  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 110: /* expr_value: explicit_value  */
  if (yyn == 110)
    /* "jflexbison/YYParser.y":1249  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 111: /* expr_value: function_call  */
  if (yyn == 111)
    /* "jflexbison/YYParser.y":1255  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 112: /* expr_value: variable  */
  if (yyn == 112)
    /* "jflexbison/YYParser.y":1261  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 113: /* break: BREAK  */
  if (yyn == 113)
    /* "jflexbison/YYParser.y":1269  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: BREAK");
		break_stmt p = ((Yylex)yylexer).new_node(break_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 114: /* continue: CONTINUE  */
  if (yyn == 114)
    /* "jflexbison/YYParser.y":1281  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CONTINUE");
		continue_stmt p = ((Yylex)yylexer).new_node(continue_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 115: /* sleep: SLEEP expr_value  */
  if (yyn == 115)
    /* "jflexbison/YYParser.y":1293  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SLEEP");
		sleep_stmt p = ((Yylex)yylexer).new_node(sleep_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 116: /* yield: YIELD expr_value  */
  if (yyn == 116)
    /* "jflexbison/YYParser.y":1306  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: YIELD");
		yield_stmt p = ((Yylex)yylexer).new_node(yield_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 117: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT block END  */
  if (yyn == 117)
    /* "jflexbison/YYParser.y":1319  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SWITCH cmp_value switch_case_list DEFAULT block END");
		switch_stmt p = ((Yylex)yylexer).new_node(switch_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (4))).ival);
		p.m_cmp = (syntree_node)((ParserVal)yystack.valueAt (4)).obj;
		p.m_caselist = (syntree_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_def = (syntree_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 118: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT END  */
  if (yyn == 118)
    /* "jflexbison/YYParser.y":1332  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SWITCH cmp_value switch_case_list DEFAULT END");
		switch_stmt p = ((Yylex)yylexer).new_node(switch_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_cmp = (syntree_node)((ParserVal)yystack.valueAt (3)).obj;
		p.m_caselist = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_def = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 119: /* switch_case_list: switch_case_define  */
  if (yyn == 119)
    /* "jflexbison/YYParser.y":1347  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_define");
		switch_caselist_node p = ((Yylex)yylexer).new_node(switch_caselist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 120: /* switch_case_list: switch_case_list switch_case_define  */
  if (yyn == 120)
    /* "jflexbison/YYParser.y":1358  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_list switch_case_define");
		switch_caselist_node p = (switch_caselist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 121: /* switch_case_define: CASE cmp_value THEN block  */
  if (yyn == 121)
    /* "jflexbison/YYParser.y":1371  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CASE cmp_value THEN block");
		switch_case_node p = ((Yylex)yylexer).new_node(switch_case_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_cmp = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_block = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 122: /* switch_case_define: CASE cmp_value THEN  */
  if (yyn == 122)
    /* "jflexbison/YYParser.y":1383  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CASE cmp_value THEN");
		switch_case_node p = ((Yylex)yylexer).new_node(switch_case_node.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		p.m_cmp = (syntree_node)((ParserVal)yystack.valueAt (1)).obj;
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 123: /* package_head: %empty  */
  if (yyn == 123)
    /* "jflexbison/YYParser.y":1397  */
        {
	};
  break;


  case 124: /* package_head: PACKAGE IDENTIFIER  */
  if (yyn == 124)
    /* "jflexbison/YYParser.y":1401  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 125: /* package_head: PACKAGE IDENTIFIER_DOT  */
  if (yyn == 125)
    /* "jflexbison/YYParser.y":1407  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER_DOT ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 126: /* include_head: %empty  */
  if (yyn == 126)
    /* "jflexbison/YYParser.y":1415  */
        {
	};
  break;


  case 129: /* include_define: INCLUDE STRING_DEFINITION  */
  if (yyn == 129)
    /* "jflexbison/YYParser.y":1425  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: INCLUDE STRING_DEFINITION ");
		((Yylex)yylexer).get_mybison().add_include(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 130: /* struct_head: %empty  */
  if (yyn == 130)
    /* "jflexbison/YYParser.y":1433  */
        {
	};
  break;


  case 133: /* struct_define: STRUCT IDENTIFIER struct_mem_declaration END  */
  if (yyn == 133)
    /* "jflexbison/YYParser.y":1443  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRUCT IDENTIFIER struct_mem_declaration END ");
		((Yylex)yylexer).get_mybison().add_struct_desc(((ParserVal)yystack.valueAt (2)).sval);
	};
  break;


  case 134: /* struct_mem_declaration: struct_mem_declaration IDENTIFIER  */
  if (yyn == 134)
    /* "jflexbison/YYParser.y":1451  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: struct_mem_declaration IDENTIFIER ");
	};
  break;


  case 135: /* struct_mem_declaration: IDENTIFIER  */
  if (yyn == 135)
    /* "jflexbison/YYParser.y":1456  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER ");
	};
  break;


  case 136: /* const_head: %empty  */
  if (yyn == 136)
    /* "jflexbison/YYParser.y":1463  */
        {
	};
  break;


  case 139: /* const_define: FCONST IDENTIFIER ASSIGN explicit_value  */
  if (yyn == 139)
    /* "jflexbison/YYParser.y":1473  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FCONST IDENTIFIER ASSIGN explicit_value ");
		((Yylex)yylexer).get_mybison().add_const_desc(((ParserVal)yystack.valueAt (2)).sval, (syntree_node)((ParserVal)yystack.valueAt (0)).obj);
	};
  break;


  case 140: /* explicit_value: NULL  */
  if (yyn == 140)
    /* "jflexbison/YYParser.y":1481  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NULL ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_NULL;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 141: /* explicit_value: FTRUE  */
  if (yyn == 141)
    /* "jflexbison/YYParser.y":1492  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_TRUE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 142: /* explicit_value: FFALSE  */
  if (yyn == 142)
    /* "jflexbison/YYParser.y":1503  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_FALSE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 143: /* explicit_value: NUMBER  */
  if (yyn == 143)
    /* "jflexbison/YYParser.y":1514  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NUMBER ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		p.m_type = explicit_value_type.EVT_NUM;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 144: /* explicit_value: FKUUID  */
  if (yyn == 144)
    /* "jflexbison/YYParser.y":1526  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FKUUID ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		p.m_type = explicit_value_type.EVT_UUID;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 145: /* explicit_value: STRING_DEFINITION  */
  if (yyn == 145)
    /* "jflexbison/YYParser.y":1538  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRING_DEFINITION ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		p.m_type = explicit_value_type.EVT_STR;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 146: /* explicit_value: FKFLOAT  */
  if (yyn == 146)
    /* "jflexbison/YYParser.y":1550  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FKFLOAT ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		p.m_type = explicit_value_type.EVT_FLOAT;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 147: /* explicit_value: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET  */
  if (yyn == 147)
    /* "jflexbison/YYParser.y":1562  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		p.m_type = explicit_value_type.EVT_MAP;
		p.m_v = (const_map_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 148: /* explicit_value: OPEN_SQUARE_BRACKET const_array_list_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 148)
    /* "jflexbison/YYParser.y":1574  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BIG_BRACKET const_array_list_value CLOSE_BIG_BRACKET ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (1))).ival);
		p.m_type = explicit_value_type.EVT_ARRAY;
		p.m_v = (const_array_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 149: /* const_map_list_value: %empty  */
  if (yyn == 149)
    /* "jflexbison/YYParser.y":1589  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 150: /* const_map_list_value: const_map_value  */
  if (yyn == 150)
    /* "jflexbison/YYParser.y":1599  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_value ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 151: /* const_map_list_value: const_map_list_value const_map_value  */
  if (yyn == 151)
    /* "jflexbison/YYParser.y":1610  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_list_value const_map_value ");
		const_map_list_value_node p = (const_map_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 152: /* const_map_value: explicit_value COLON explicit_value  */
  if (yyn == 152)
    /* "jflexbison/YYParser.y":1624  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value COLON explicit_value ");
		const_map_value_node p = ((Yylex)yylexer).new_node(const_map_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_k = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_v = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 153: /* const_array_list_value: %empty  */
  if (yyn == 153)
    /* "jflexbison/YYParser.y":1638  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 154: /* const_array_list_value: explicit_value  */
  if (yyn == 154)
    /* "jflexbison/YYParser.y":1648  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 155: /* const_array_list_value: const_array_list_value explicit_value  */
  if (yyn == 155)
    /* "jflexbison/YYParser.y":1659  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_array_list_value explicit_value ");
		const_array_list_value_node p = (const_array_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;



/* "src/main/java/com/github/esrrhs/fakescript/YYParser.java":2784  */

        default: break;
      }

    yystack.pop(yylen);
    yylen = 0;
    /* Shift the result of the reduction.  */
    int yystate = yyLRGotoState(yystack.stateAt(0), yyr1_[yyn]);
    yystack.push(yystate, yyval);
    return YYNEWSTATE;
  }




  /**
   * Parse input from the scanner that was specified at object construction
   * time.  Return whether the end of the input was reached successfully.
   *
   * @return <tt>true</tt> if the parsing succeeds.  Note that this does not
   *          imply that there were no syntax errors.
   */
  public boolean parse() throws java.io.IOException

  {


    /* Lookahead token kind.  */
    int yychar = YYEMPTY_;
    /* Lookahead symbol kind.  */
    SymbolKind yytoken = null;

    /* State.  */
    int yyn = 0;
    int yylen = 0;
    int yystate = 0;
    YYStack yystack = new YYStack ();
    int label = YYNEWSTATE;



    /* Semantic value of the lookahead.  */
    Object yylval = null;



    yyerrstatus_ = 0;
    yynerrs = 0;

    /* Initialize the stack.  */
    yystack.push (yystate, yylval);



    for (;;)
      switch (label)
      {
        /* New state.  Unlike in the C/C++ skeletons, the state is already
           pushed when we come here.  */
      case YYNEWSTATE:

        /* Accept?  */
        if (yystate == YYFINAL_)
          return true;

        /* Take a decision.  First try without lookahead.  */
        yyn = yypact_[yystate];
        if (yyPactValueIsDefault (yyn))
          {
            label = YYDEFAULT;
            break;
          }

        /* Read a lookahead token.  */
        if (yychar == YYEMPTY_)
          {

            yychar = yylexer.yylex ();
            yylval = yylexer.getLVal();

          }

        /* Convert token to internal form.  */
        yytoken = yytranslate_ (yychar);

        if (yytoken == SymbolKind.S_YYerror)
          {
            // The scanner already issued an error message, process directly
            // to error recovery.  But do not keep the error token as
            // lookahead, it is too special and may lead us to an endless
            // loop in error recovery. */
            yychar = Lexer.YYUNDEF;
            yytoken = SymbolKind.S_YYUNDEF;
            label = YYERRLAB1;
          }
        else
          {
            /* If the proper action on seeing token YYTOKEN is to reduce or to
               detect an error, take that action.  */
            yyn += yytoken.getCode();
            if (yyn < 0 || YYLAST_ < yyn || yycheck_[yyn] != yytoken.getCode()) {
              label = YYDEFAULT;
            }

            /* <= 0 means reduce or error.  */
            else if ((yyn = yytable_[yyn]) <= 0)
              {
                if (yyTableValueIsError(yyn)) {
                  label = YYERRLAB;
                } else {
                  yyn = -yyn;
                  label = YYREDUCE;
                }
              }

            else
              {
                /* Shift the lookahead token.  */
                /* Discard the token being shifted.  */
                yychar = YYEMPTY_;

                /* Count tokens shifted since error; after three, turn off error
                   status.  */
                if (yyerrstatus_ > 0)
                  --yyerrstatus_;

                yystate = yyn;
                yystack.push(yystate, yylval);
                label = YYNEWSTATE;
              }
          }
        break;

      /*-----------------------------------------------------------.
      | yydefault -- do the default action for the current state.  |
      `-----------------------------------------------------------*/
      case YYDEFAULT:
        yyn = yydefact_[yystate];
        if (yyn == 0)
          label = YYERRLAB;
        else
          label = YYREDUCE;
        break;

      /*-----------------------------.
      | yyreduce -- Do a reduction.  |
      `-----------------------------*/
      case YYREDUCE:
        yylen = yyr2_[yyn];
        label = yyaction(yyn, yystack, yylen);
        yystate = yystack.stateAt(0);
        break;

      /*------------------------------------.
      | yyerrlab -- here on detecting error |
      `------------------------------------*/
      case YYERRLAB:
        /* If not already recovering from an error, report this error.  */
        if (yyerrstatus_ == 0)
          {
            ++yynerrs;
            if (yychar == YYEMPTY_)
              yytoken = null;
            yyreportSyntaxError(new Context(this, yystack, yytoken));
          }

        if (yyerrstatus_ == 3)
          {
            /* If just tried and failed to reuse lookahead token after an
               error, discard it.  */

            if (yychar <= Lexer.YYEOF)
              {
                /* Return failure if at end of input.  */
                if (yychar == Lexer.YYEOF)
                  return false;
              }
            else
              yychar = YYEMPTY_;
          }

        /* Else will try to reuse lookahead token after shifting the error
           token.  */
        label = YYERRLAB1;
        break;

      /*-------------------------------------------------.
      | errorlab -- error raised explicitly by YYERROR.  |
      `-------------------------------------------------*/
      case YYERROR:
        /* Do not reclaim the symbols of the rule which action triggered
           this YYERROR.  */
        yystack.pop (yylen);
        yylen = 0;
        yystate = yystack.stateAt(0);
        label = YYERRLAB1;
        break;

      /*-------------------------------------------------------------.
      | yyerrlab1 -- common code for both syntax error and YYERROR.  |
      `-------------------------------------------------------------*/
      case YYERRLAB1:
        yyerrstatus_ = 3;       /* Each real token shifted decrements this.  */

        // Pop stack until we find a state that shifts the error token.
        for (;;)
          {
            yyn = yypact_[yystate];
            if (!yyPactValueIsDefault (yyn))
              {
                yyn += SymbolKind.S_YYerror.getCode();
                if (0 <= yyn && yyn <= YYLAST_
                    && yycheck_[yyn] == SymbolKind.S_YYerror.getCode())
                  {
                    yyn = yytable_[yyn];
                    if (0 < yyn)
                      break;
                  }
              }

            /* Pop the current state because it cannot handle the
             * error token.  */
            if (yystack.height == 0)
              return false;


            yystack.pop ();
            yystate = yystack.stateAt(0);
          }

        if (label == YYABORT)
          /* Leave the switch.  */
          break;



        /* Shift the error token.  */

        yystate = yyn;
        yystack.push (yyn, yylval);
        label = YYNEWSTATE;
        break;

        /* Accept.  */
      case YYACCEPT:
        return true;

        /* Abort.  */
      case YYABORT:
        return false;
      }
}




  /**
   * Information needed to get the list of expected tokens and to forge
   * a syntax error diagnostic.
   */
  public static final class Context {
    Context(YYParser parser, YYStack stack, SymbolKind token) {
      yyparser = parser;
      yystack = stack;
      yytoken = token;
    }

    private YYParser yyparser;
    private YYStack yystack;


    /**
     * The symbol kind of the lookahead token.
     */
    public final SymbolKind getToken() {
      return yytoken;
    }

    private SymbolKind yytoken;
    static final int NTOKENS = YYParser.YYNTOKENS_;

    /**
     * Put in YYARG at most YYARGN of the expected tokens given the
     * current YYCTX, and return the number of tokens stored in YYARG.  If
     * YYARG is null, return the number of expected tokens (guaranteed to
     * be less than YYNTOKENS).
     */
    int getExpectedTokens(SymbolKind yyarg[], int yyargn) {
      return getExpectedTokens (yyarg, 0, yyargn);
    }

    int getExpectedTokens(SymbolKind yyarg[], int yyoffset, int yyargn) {
      int yycount = yyoffset;
      int yyn = yypact_[this.yystack.stateAt(0)];
      if (!yyPactValueIsDefault(yyn))
        {
          /* Start YYX at -YYN if negative to avoid negative
             indexes in YYCHECK.  In other words, skip the first
             -YYN actions for this state because they are default
             actions.  */
          int yyxbegin = yyn < 0 ? -yyn : 0;
          /* Stay within bounds of both yycheck and yytname.  */
          int yychecklim = YYLAST_ - yyn + 1;
          int yyxend = yychecklim < NTOKENS ? yychecklim : NTOKENS;
          for (int yyx = yyxbegin; yyx < yyxend; ++yyx)
            if (yycheck_[yyx + yyn] == yyx && yyx != SymbolKind.S_YYerror.getCode()
                && !yyTableValueIsError(yytable_[yyx + yyn]))
              {
                if (yyarg == null)
                  yycount += 1;
                else if (yycount == yyargn)
                  return 0; // FIXME: this is incorrect.
                else
                  yyarg[yycount++] = SymbolKind.get(yyx);
              }
        }
      if (yyarg != null && yycount == yyoffset && yyoffset < yyargn)
        yyarg[yycount] = null;
      return yycount - yyoffset;
    }
  }





  /**
   * Build and emit a "syntax error" message in a user-defined way.
   *
   * @param ctx  The context of the error.
   */
  private void yyreportSyntaxError(Context yyctx) {
      yyerror("syntax error");
  }

  /**
   * Whether the given <code>yypact_</code> value indicates a defaulted state.
   * @param yyvalue   the value to check
   */
  private static boolean yyPactValueIsDefault(int yyvalue) {
    return yyvalue == yypact_ninf_;
  }

  /**
   * Whether the given <code>yytable_</code>
   * value indicates a syntax error.
   * @param yyvalue the value to check
   */
  private static boolean yyTableValueIsError(int yyvalue) {
    return yyvalue == yytable_ninf_;
  }

  private static final short yypact_ninf_ = -210;
  private static final short yytable_ninf_ = -113;

/* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
   STATE-NUM.  */
  private static final short[] yypact_ = yypact_init();
  private static final short[] yypact_init()
  {
    return new short[]
    {
     -33,   -11,    49,    16,  -210,  -210,  -210,    56,   103,  -210,
    -210,    79,  -210,   107,  -210,    87,   105,  -210,    -1,  -210,
    -210,   148,   122,   139,   158,  -210,  -210,  -210,  -210,   392,
     159,  -210,  -210,  -210,  -210,  -210,  -210,  -210,   392,   392,
    -210,  -210,   174,  -210,   197,   178,   345,  -210,  -210,    -5,
    -210,  -210,  -210,   392,  -210,  -210,   174,   511,  -210,  -210,
     195,  1223,  -210,    22,    22,  -210,    14,  1223,  1177,    46,
     190,  -210,  -210,  1232,  1232,  1223,    21,   577,  -210,  -210,
    -210,  -210,  -210,  -210,  -210,  -210,  -210,    65,  -210,     1,
     498,  -210,   101,   344,  -210,  -210,  -210,  -210,  -210,  -210,
    -210,   204,  -210,   216,  -210,   445,   169,   277,    22,  1223,
    1223,    58,   296,   216,  -210,   445,    97,  1232,  1232,   186,
     198,   295,   595,     2,    17,   186,  1232,  1232,    17,  -210,
     344,   344,   171,  1232,   214,  -210,  -210,     4,    46,    46,
    1223,  1223,  1223,  1223,  1223,  1223,  1223,   220,  -210,  1232,
    1232,  1232,  1232,  1232,  1232,  1223,   257,   198,  -210,  -210,
     661,    22,    22,  1223,  1223,  1223,  1223,  1223,  1223,   679,
      53,  -210,   344,   399,  -210,  -210,    22,  1223,    68,   208,
    1223,   156,  -210,   125,   210,   201,  -210,  -210,  -210,    17,
      17,  -210,   216,  -210,   445,  -210,  -210,  -210,  -210,  -210,
    -210,   217,   193,   264,    60,    10,    -6,  -210,  -210,  -210,
    -210,   745,    95,    95,  -210,  -210,  -210,  -210,  -210,  -210,
      22,   679,    -9,  -210,  1232,  -210,  -210,   227,   189,  -210,
     251,   811,  -210,  -210,  1232,  1232,  -210,   134,    -9,  1177,
    -210,   266,  -210,  1177,  1223,  1177,  -210,   877,   160,   188,
    1177,   270,  1177,  -210,   895,   245,  1177,  -210,  -210,  -210,
    1177,  -210,   961,  1232,  -210,  1027,     0,  -210,  1093,  -210,
    1159,  -210
    };
  }

/* YYDEFACT[STATE-NUM] -- Default reduction number in state STATE-NUM.
   Performed when YYTABLE does not specify something else to do.  Zero
   means the default is an error.  */
  private static final short[] yydefact_ = yydefact_init();
  private static final short[] yydefact_init()
  {
    return new short[]
    {
     123,     0,     0,   126,   124,   125,     1,     0,   130,   127,
     129,     0,   128,   136,   131,     0,     0,   132,     3,   137,
     135,     0,     0,     0,     2,     4,   138,   133,   134,     0,
       0,     5,   141,   142,   145,   143,   146,   144,   153,   149,
     140,   139,     8,   154,     0,     0,     0,   150,    11,     0,
      10,   148,   155,     0,   147,   151,     0,     0,   152,     9,
       0,    72,   113,     0,     0,     7,    95,     0,     0,     0,
      98,    97,   114,     0,     0,     0,   100,     0,    22,    34,
      32,    33,    23,    24,    25,    26,    27,     0,    31,    83,
     112,    30,   101,     0,    28,    29,    35,    36,    37,   110,
      93,    71,    74,    76,    77,    75,   141,   142,     0,     0,
       0,     0,     0,    69,    70,    68,     0,    17,     0,   112,
       0,   101,     0,    83,    38,     0,    17,     0,   111,   109,
     116,   115,     0,    17,     0,     6,    21,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    92,     0,
       0,     0,     0,     0,     0,     0,     0,    70,    66,    67,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    47,
       0,    19,    20,     0,    99,   102,     0,     0,     0,   109,
       0,     0,   119,     0,     0,    95,    98,    82,    94,    80,
      81,    78,    85,    86,    84,    79,    87,    88,    89,    90,
      91,     0,   107,   103,   104,   106,   105,   108,    73,    55,
      44,     0,    56,    57,    59,    58,    61,    62,    60,    63,
       0,    47,    52,    49,     0,    12,    96,     0,    78,    13,
       0,     0,   120,    14,    17,    17,    43,     0,    52,    54,
      48,     0,    18,     0,     0,   122,   118,     0,     0,     0,
      51,     0,    53,    46,     0,     0,   121,   117,    16,    15,
      50,    45,     0,     0,    40,     0,     0,    39,     0,    42,
       0,    41
    };
  }

/* YYPGOTO[NTERM-NUM].  */
  private static final short[] yypgoto_ = yypgoto_init();
  private static final short[] yypgoto_init()
  {
    return new short[]
    {
    -210,  -210,  -210,   256,  -210,   238,   181,  -125,    73,   -17,
     127,  -210,  -210,  -210,  -210,  -210,    91,  -209,    90,   -58,
     267,  -210,  -210,   184,  -210,  -210,  -210,   129,  -210,   -66,
      32,   332,   151,   219,  -210,  -210,  -210,  -210,  -210,  -210,
     162,  -210,  -210,   333,  -210,   327,  -210,  -210,   326,   -29,
    -210,   300,  -210
    };
  }

/* YYDEFGOTO[NTERM-NUM].  */
  private static final short[] yydefgoto_ = yydefgoto_init();
  private static final short[] yydefgoto_init()
  {
    return new short[]
    {
       0,     2,    24,    25,    49,    50,    76,   170,   171,    77,
      78,    79,    80,    81,    82,    83,   222,   223,   241,   111,
     112,    84,   101,   102,    85,    86,    87,   191,    88,    89,
      90,    91,    92,    93,    94,    95,    96,    97,    98,   181,
     182,     3,     8,     9,    13,    14,    21,    18,    19,    99,
      46,    47,    44
    };
  }

/* YYTABLE[YYPACT[STATE-NUM]] -- What to do in state STATE-NUM.  If
   positive, shift that token.  If negative, reduce the rule whose
   number is the opposite.  If YYTABLE_NINF, syntax error.  */
  private static final short[] yytable_ = yytable_init();
  private static final short[] yytable_init()
  {
    return new short[]
    {
      41,   178,   123,   239,     4,    23,   116,    60,   183,    43,
      45,   268,   149,   240,    56,    52,     1,    45,   149,   185,
     150,   151,   152,   153,    58,   140,   177,    57,   149,   240,
     106,   107,   105,   153,   115,   115,    34,    66,    35,  -111,
       5,  -111,  -111,  -111,  -111,   117,   115,    16,   133,     6,
     156,   122,   133,   108,   220,   186,    71,    36,   134,   154,
     118,    66,   134,   141,   141,   154,     7,    37,    38,   160,
      10,   187,   224,    70,    71,   154,   109,   110,   149,   115,
     115,   115,   152,   153,   137,   225,  -111,   224,    39,   138,
      40,   161,   162,   103,    15,   113,   113,    70,    71,   119,
     229,   125,    20,   212,   213,   119,   119,   113,   169,   248,
     249,   194,   194,   194,   194,   194,   194,   194,   227,  -109,
      22,  -109,  -109,  -109,  -109,   154,   105,   139,   161,   162,
     161,   162,   115,   115,   115,   115,   115,   115,   115,   115,
     113,   113,   113,   211,   224,   250,    29,   115,   194,   119,
     119,   115,   221,     7,    30,    16,    11,   233,   119,   119,
      11,    27,   237,    28,    23,   119,  -109,   161,   162,   188,
     125,   125,   192,   192,   192,   192,   192,   192,   192,   224,
     -64,   119,   119,   119,   119,   119,   119,   103,   -64,    48,
      42,   115,   258,   113,   113,   113,   113,   113,   113,   113,
     113,   -64,   -64,   -64,   136,    32,    33,   224,   113,   192,
     100,    34,   113,    35,   247,   115,   180,   231,   121,    53,
     259,   126,   252,   155,   129,   129,   254,   147,   256,   184,
     174,   180,    36,   260,  -112,   201,  -112,  -112,  -112,  -112,
     175,   234,    37,    38,    51,   265,   243,   118,   235,   136,
     124,   270,   113,   244,   128,   128,   119,   147,   154,   121,
     161,   162,   245,    39,   263,    40,   119,   119,   129,   129,
     195,   196,   197,   198,   199,   200,   113,   129,   179,   253,
      31,  -112,   149,   261,   129,   151,   152,   153,   -65,   209,
     161,   162,   130,   131,    59,   119,   -65,   242,   128,   128,
     129,   129,   129,   129,   129,   129,   228,   128,   128,   -65,
     -65,   -65,   238,  -109,   128,  -109,  -109,  -109,  -109,   189,
     190,   163,   164,   165,   166,   167,   168,   175,   251,   154,
     128,   128,   128,   128,   128,   128,   172,   173,   136,   208,
      17,    12,   132,   232,    26,   172,    55,     0,   136,     0,
       0,     0,   172,    32,    33,     0,     0,     0,     0,    34,
    -109,    35,   149,     0,   150,   151,   152,   153,   202,   203,
     204,   205,   206,   207,   136,   129,   158,   159,     0,   136,
      36,   136,     0,   136,     0,   129,   129,   136,     0,     0,
      37,    38,   136,   104,     0,   114,   114,   136,     0,   120,
      32,    33,     0,     0,     0,   128,    34,   114,    35,   154,
       0,    39,    54,    40,   129,   128,   128,   149,     0,   150,
     151,   152,   153,     0,     0,     0,     0,    36,     0,     0,
     214,   215,   216,   217,   218,   219,     0,    37,    38,     0,
     157,   114,   114,   172,   128,     0,   226,   230,     0,     0,
       0,     0,     0,   172,   172,     0,     0,     0,    39,     0,
      40,     0,     0,  -110,   154,  -110,  -110,  -110,  -110,     0,
       0,     0,   193,   193,   193,   193,   193,   193,   193,     0,
       0,     0,   266,     0,     0,     0,     0,   104,     0,     0,
       0,     0,     0,   114,   114,   114,   114,   114,   114,   114,
     114,     0,     0,     0,     0,     0,     0,     0,   114,   193,
    -110,   255,   114,     0,    60,    61,    62,   -94,    63,    32,
      33,    64,   -94,     0,    65,    34,    66,    35,     0,     0,
       0,     0,     0,     0,   142,   143,   144,   145,   146,   147,
       0,   148,    67,     0,     0,     0,    36,     0,     0,     0,
       0,     0,   114,    68,     0,    69,    37,    38,     0,     0,
     -94,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,     0,     0,     0,     0,     0,   114,    39,     0,    40,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
     135,    34,    66,    35,     0,     0,     0,     0,    60,    61,
      62,     0,    63,    32,    33,    64,     0,     0,    67,    34,
      66,    35,    36,     0,   176,     0,     0,     0,     0,    68,
       0,    69,    37,    38,     0,     0,    67,     0,    70,    71,
      36,     0,     0,    72,    73,    74,    75,    68,     0,    69,
      37,    38,     0,    39,     0,    40,    70,    71,     0,     0,
       0,    72,    73,    74,    75,     0,     0,     0,     0,     0,
       0,    39,     0,    40,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   210,    34,    66,    35,     0,     0,
       0,     0,    60,    61,    62,     0,    63,    32,    33,    64,
       0,     0,    67,    34,    66,    35,    36,     0,     0,     0,
       0,     0,     0,    68,     0,    69,    37,    38,     0,     0,
      67,     0,    70,    71,    36,     0,     0,    72,    73,    74,
      75,    68,     0,    69,    37,    38,     0,    39,     0,    40,
      70,    71,     0,     0,     0,    72,    73,    74,    75,     0,
       0,     0,   220,     0,     0,    39,     0,    40,    60,    61,
      62,     0,    63,    32,    33,    64,     0,     0,   236,    34,
      66,    35,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    67,     0,     0,     0,
      36,     0,     0,     0,     0,     0,     0,    68,     0,    69,
      37,    38,     0,     0,     0,     0,    70,    71,     0,     0,
       0,    72,    73,    74,    75,     0,     0,     0,     0,     0,
       0,    39,     0,    40,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   246,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,     0,     0,     0,     0,     0,     0,    39,     0,    40,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
     257,    34,    66,    35,     0,     0,     0,     0,    60,    61,
      62,     0,    63,    32,    33,    64,   262,     0,    67,    34,
      66,    35,    36,     0,     0,     0,     0,     0,     0,    68,
       0,    69,    37,    38,     0,     0,    67,     0,    70,    71,
      36,     0,     0,    72,    73,    74,    75,    68,     0,    69,
      37,    38,     0,    39,     0,    40,    70,    71,     0,     0,
       0,    72,    73,    74,    75,     0,     0,     0,     0,     0,
       0,    39,     0,    40,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   264,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,     0,     0,     0,     0,     0,     0,    39,     0,    40,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
     267,    34,    66,    35,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    67,     0,
       0,     0,    36,     0,     0,     0,     0,     0,     0,    68,
       0,    69,    37,    38,     0,     0,     0,     0,    70,    71,
       0,     0,     0,    72,    73,    74,    75,     0,     0,     0,
       0,     0,     0,    39,     0,    40,    60,    61,    62,     0,
      63,    32,    33,    64,     0,     0,   269,    34,    66,    35,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    67,     0,     0,     0,    36,     0,
       0,     0,     0,     0,     0,    68,     0,    69,    37,    38,
       0,     0,     0,     0,    70,    71,     0,     0,     0,    72,
      73,    74,    75,     0,     0,     0,     0,     0,     0,    39,
       0,    40,    60,    61,    62,     0,    63,    32,    33,    64,
       0,     0,   271,    34,    66,    35,     0,     0,     0,     0,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
      67,    34,    66,    35,    36,     0,     0,     0,     0,     0,
       0,    68,     0,    69,    37,    38,     0,     0,    67,     0,
      70,    71,    36,     0,     0,    72,    73,    74,    75,    68,
       0,    69,    37,    38,     0,    39,     0,    40,    70,    71,
       0,    32,    33,    72,    73,    74,    75,    34,    66,    35,
      32,    33,     0,    39,     0,    40,    34,    66,    35,     0,
       0,     0,     0,     0,    67,     0,     0,     0,    36,     0,
       0,     0,     0,   127,     0,     0,     0,    36,    37,    38,
       0,     0,     0,     0,    70,    71,     0,    37,    38,     0,
       0,     0,     0,    70,    71,     0,     0,     0,     0,    39,
       0,    40,     0,     0,     0,     0,     0,     0,    39,     0,
      40
    };
  }

private static final short[] yycheck_ = yycheck_init();
  private static final short[] yycheck_init()
  {
    return new short[]
    {
      29,   126,    68,    12,    15,     6,    64,     3,   133,    38,
      39,    11,    18,   222,    19,    44,    49,    46,    18,    15,
      20,    21,    22,    23,    53,    24,    24,    32,    18,   238,
       8,     9,    61,    23,    63,    64,    14,    15,    16,    18,
      51,    20,    21,    22,    23,    31,    75,    48,    31,     0,
     108,    68,    31,    31,    63,    51,    52,    35,    41,    65,
      46,    15,    41,    62,    62,    65,    50,    45,    46,    11,
      14,   137,    19,    51,    52,    65,    54,    55,    18,   108,
     109,   110,    22,    23,    19,    32,    65,    19,    66,    24,
      68,    33,    34,    61,    15,    63,    64,    51,    52,    67,
      32,    69,    15,   161,   162,    73,    74,    75,    11,   234,
     235,   140,   141,   142,   143,   144,   145,   146,   176,    18,
      15,    20,    21,    22,    23,    65,   155,    62,    33,    34,
      33,    34,   161,   162,   163,   164,   165,   166,   167,   168,
     108,   109,   110,   160,    19,    11,    24,   176,   177,   117,
     118,   180,   169,    50,    15,    48,    53,    32,   126,   127,
      53,    13,   220,    15,     6,   133,    65,    33,    34,   137,
     138,   139,   140,   141,   142,   143,   144,   145,   146,    19,
      11,   149,   150,   151,   152,   153,   154,   155,    19,    15,
      31,   220,    32,   161,   162,   163,   164,   165,   166,   167,
     168,    32,    33,    34,    77,     8,     9,    19,   176,   177,
      15,    14,   180,    16,   231,   244,    60,    61,    67,    41,
      32,    31,   239,    19,    73,    74,   243,    41,   245,    15,
      32,    60,    35,   250,    18,    15,    20,    21,    22,    23,
      32,    31,    45,    46,    47,   262,    19,    46,    31,   122,
      69,   268,   220,    64,    73,    74,   224,    41,    65,   108,
      33,    34,    11,    66,    19,    68,   234,   235,   117,   118,
     141,   142,   143,   144,   145,   146,   244,   126,   127,    13,
      24,    65,    18,    13,   133,    21,    22,    23,    11,    32,
      33,    34,    73,    74,    56,   263,    19,   224,   117,   118,
     149,   150,   151,   152,   153,   154,   177,   126,   127,    32,
      33,    34,   221,    18,   133,    20,    21,    22,    23,   138,
     139,    25,    26,    27,    28,    29,    30,    32,   238,    65,
     149,   150,   151,   152,   153,   154,   117,   118,   211,   155,
      13,     8,    75,   181,    18,   126,    46,    -1,   221,    -1,
      -1,    -1,   133,     8,     9,    -1,    -1,    -1,    -1,    14,
      65,    16,    18,    -1,    20,    21,    22,    23,   149,   150,
     151,   152,   153,   154,   247,   224,   109,   110,    -1,   252,
      35,   254,    -1,   256,    -1,   234,   235,   260,    -1,    -1,
      45,    46,   265,    61,    -1,    63,    64,   270,    -1,    67,
       8,     9,    -1,    -1,    -1,   224,    14,    75,    16,    65,
      -1,    66,    67,    68,   263,   234,   235,    18,    -1,    20,
      21,    22,    23,    -1,    -1,    -1,    -1,    35,    -1,    -1,
     163,   164,   165,   166,   167,   168,    -1,    45,    46,    -1,
     108,   109,   110,   224,   263,    -1,    47,   180,    -1,    -1,
      -1,    -1,    -1,   234,   235,    -1,    -1,    -1,    66,    -1,
      68,    -1,    -1,    18,    65,    20,    21,    22,    23,    -1,
      -1,    -1,   140,   141,   142,   143,   144,   145,   146,    -1,
      -1,    -1,   263,    -1,    -1,    -1,    -1,   155,    -1,    -1,
      -1,    -1,    -1,   161,   162,   163,   164,   165,   166,   167,
     168,    -1,    -1,    -1,    -1,    -1,    -1,    -1,   176,   177,
      65,   244,   180,    -1,     3,     4,     5,    19,     7,     8,
       9,    10,    24,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    36,    37,    38,    39,    40,    41,
      -1,    43,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      -1,    -1,   220,    42,    -1,    44,    45,    46,    -1,    -1,
      62,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    -1,    -1,    -1,    -1,    -1,   244,    66,    -1,    68,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      13,    14,    15,    16,    -1,    -1,    -1,    -1,     3,     4,
       5,    -1,     7,     8,     9,    10,    -1,    -1,    31,    14,
      15,    16,    35,    -1,    19,    -1,    -1,    -1,    -1,    42,
      -1,    44,    45,    46,    -1,    -1,    31,    -1,    51,    52,
      35,    -1,    -1,    56,    57,    58,    59,    42,    -1,    44,
      45,    46,    -1,    66,    -1,    68,    51,    52,    -1,    -1,
      -1,    56,    57,    58,    59,    -1,    -1,    -1,    -1,    -1,
      -1,    66,    -1,    68,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,     3,     4,     5,    -1,     7,     8,     9,    10,
      -1,    -1,    31,    14,    15,    16,    35,    -1,    -1,    -1,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    -1,
      31,    -1,    51,    52,    35,    -1,    -1,    56,    57,    58,
      59,    42,    -1,    44,    45,    46,    -1,    66,    -1,    68,
      51,    52,    -1,    -1,    -1,    56,    57,    58,    59,    -1,
      -1,    -1,    63,    -1,    -1,    66,    -1,    68,     3,     4,
       5,    -1,     7,     8,     9,    10,    -1,    -1,    13,    14,
      15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    31,    -1,    -1,    -1,
      35,    -1,    -1,    -1,    -1,    -1,    -1,    42,    -1,    44,
      45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,    -1,
      -1,    56,    57,    58,    59,    -1,    -1,    -1,    -1,    -1,
      -1,    66,    -1,    68,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    -1,    -1,    -1,    -1,    -1,    -1,    66,    -1,    68,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      13,    14,    15,    16,    -1,    -1,    -1,    -1,     3,     4,
       5,    -1,     7,     8,     9,    10,    11,    -1,    31,    14,
      15,    16,    35,    -1,    -1,    -1,    -1,    -1,    -1,    42,
      -1,    44,    45,    46,    -1,    -1,    31,    -1,    51,    52,
      35,    -1,    -1,    56,    57,    58,    59,    42,    -1,    44,
      45,    46,    -1,    66,    -1,    68,    51,    52,    -1,    -1,
      -1,    56,    57,    58,    59,    -1,    -1,    -1,    -1,    -1,
      -1,    66,    -1,    68,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    -1,    -1,    -1,    -1,    -1,    -1,    66,    -1,    68,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      13,    14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    -1,
      -1,    -1,    35,    -1,    -1,    -1,    -1,    -1,    -1,    42,
      -1,    44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,
      -1,    -1,    -1,    56,    57,    58,    59,    -1,    -1,    -1,
      -1,    -1,    -1,    66,    -1,    68,     3,     4,     5,    -1,
       7,     8,     9,    10,    -1,    -1,    13,    14,    15,    16,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    31,    -1,    -1,    -1,    35,    -1,
      -1,    -1,    -1,    -1,    -1,    42,    -1,    44,    45,    46,
      -1,    -1,    -1,    -1,    51,    52,    -1,    -1,    -1,    56,
      57,    58,    59,    -1,    -1,    -1,    -1,    -1,    -1,    66,
      -1,    68,     3,     4,     5,    -1,     7,     8,     9,    10,
      -1,    -1,    13,    14,    15,    16,    -1,    -1,    -1,    -1,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      31,    14,    15,    16,    35,    -1,    -1,    -1,    -1,    -1,
      -1,    42,    -1,    44,    45,    46,    -1,    -1,    31,    -1,
      51,    52,    35,    -1,    -1,    56,    57,    58,    59,    42,
      -1,    44,    45,    46,    -1,    66,    -1,    68,    51,    52,
      -1,     8,     9,    56,    57,    58,    59,    14,    15,    16,
       8,     9,    -1,    66,    -1,    68,    14,    15,    16,    -1,
      -1,    -1,    -1,    -1,    31,    -1,    -1,    -1,    35,    -1,
      -1,    -1,    -1,    31,    -1,    -1,    -1,    35,    45,    46,
      -1,    -1,    -1,    -1,    51,    52,    -1,    45,    46,    -1,
      -1,    -1,    -1,    51,    52,    -1,    -1,    -1,    -1,    66,
      -1,    68,    -1,    -1,    -1,    -1,    -1,    -1,    66,    -1,
      68
    };
  }

/* YYSTOS[STATE-NUM] -- The symbol kind of the accessing symbol of
   state STATE-NUM.  */
  private static final byte[] yystos_ = yystos_init();
  private static final byte[] yystos_init()
  {
    return new byte[]
    {
       0,    49,    70,   110,    15,    51,     0,    50,   111,   112,
      14,    53,   112,   113,   114,    15,    48,   114,   116,   117,
      15,   115,    15,     6,    71,    72,   117,    13,    15,    24,
      15,    72,     8,     9,    14,    16,    35,    45,    46,    66,
      68,   118,    31,   118,   121,   118,   119,   120,    15,    73,
      74,    47,   118,    41,    67,   120,    19,    32,   118,    74,
       3,     4,     5,     7,    10,    13,    15,    31,    42,    44,
      51,    52,    56,    57,    58,    59,    75,    78,    79,    80,
      81,    82,    83,    84,    90,    93,    94,    95,    97,    98,
      99,   100,   101,   102,   103,   104,   105,   106,   107,   118,
      15,    91,    92,    99,   100,   118,     8,     9,    31,    54,
      55,    88,    89,    99,   100,   118,    88,    31,    46,    99,
     100,   101,    78,    98,    75,    99,    31,    31,    75,   101,
     102,   102,    89,    31,    41,    13,    79,    19,    24,    62,
      24,    62,    36,    37,    38,    39,    40,    41,    43,    18,
      20,    21,    22,    23,    65,    19,    88,   100,    89,    89,
      11,    33,    34,    25,    26,    27,    28,    29,    30,    11,
      76,    77,   102,   102,    32,    32,    19,    24,    76,   101,
      60,   108,   109,    76,    15,    15,    51,    98,    99,    75,
      75,    96,    99,   100,   118,    96,    96,    96,    96,    96,
      96,    15,   102,   102,   102,   102,   102,   102,    92,    32,
      13,    78,    88,    88,    89,    89,    89,    89,    89,    89,
      63,    78,    85,    86,    19,    32,    47,    88,    96,    32,
      89,    61,   109,    32,    31,    31,    13,    88,    85,    12,
      86,    87,    77,    19,    64,    11,    13,    78,    76,    76,
      11,    87,    78,    13,    78,    89,    78,    13,    32,    32,
      78,    13,    11,    19,    13,    78,   102,    13,    11,    13,
      78,    13
    };
  }

/* YYR1[RULE-NUM] -- Symbol kind of the left-hand side of rule RULE-NUM.  */
  private static final byte[] yyr1_ = yyr1_init();
  private static final byte[] yyr1_init()
  {
    return new byte[]
    {
       0,    69,    70,    71,    71,    71,    72,    72,    73,    73,
      73,    74,    75,    75,    75,    75,    75,    76,    76,    76,
      77,    78,    78,    79,    79,    79,    79,    79,    79,    79,
      79,    79,    79,    79,    79,    79,    79,    79,    80,    81,
      81,    82,    82,    83,    83,    84,    84,    85,    85,    85,
      86,    86,    87,    87,    87,    88,    88,    88,    88,    88,
      88,    88,    88,    88,    88,    88,    88,    88,    89,    89,
      89,    90,    90,    91,    91,    92,    92,    92,    93,    93,
      94,    94,    95,    95,    96,    96,    96,    97,    97,    97,
      97,    97,    97,    98,    98,    99,    99,    99,    99,   100,
     100,   100,   101,   101,   101,   101,   101,   101,   101,   102,
     102,   102,   102,   103,   104,   105,   106,   107,   107,   108,
     108,   109,   109,   110,   110,   110,   111,   111,   111,   112,
     113,   113,   113,   114,   115,   115,   116,   116,   116,   117,
     118,   118,   118,   118,   118,   118,   118,   118,   118,   119,
     119,   119,   120,   121,   121,   121
    };
  }

/* YYR2[RULE-NUM] -- Number of symbols on the right-hand side of rule RULE-NUM.  */
  private static final byte[] yyr2_ = yyr2_init();
  private static final byte[] yyr2_init()
  {
    return new byte[]
    {
       0,     2,     5,     0,     1,     2,     7,     6,     0,     3,
       1,     1,     4,     4,     4,     6,     6,     0,     3,     1,
       1,     2,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     1,     2,     9,
       8,    11,    10,     5,     4,     7,     6,     0,     2,     1,
       4,     3,     0,     2,     1,     3,     3,     3,     3,     3,
       3,     3,     3,     3,     1,     1,     2,     2,     1,     1,
       1,     2,     1,     3,     1,     1,     1,     1,     3,     3,
       3,     3,     3,     1,     1,     1,     1,     3,     3,     3,
       3,     3,     2,     2,     1,     1,     4,     1,     1,     3,
       1,     1,     3,     3,     3,     3,     3,     3,     3,     1,
       1,     1,     1,     1,     1,     2,     2,     6,     5,     1,
       2,     4,     3,     0,     2,     2,     0,     1,     2,     2,
       0,     1,     2,     4,     2,     1,     0,     1,     2,     4,
       1,     1,     1,     1,     1,     1,     1,     3,     3,     0,
       1,     2,     3,     0,     1,     2
    };
  }




  /* YYTRANSLATE_(TOKEN-NUM) -- Symbol number corresponding to TOKEN-NUM
     as returned by yylex, with out-of-bounds checking.  */
  private static final SymbolKind yytranslate_(int t)
  {
    // Last valid token kind.
    int code_max = 323;
    if (t <= 0)
      return SymbolKind.S_YYEOF;
    else if (t <= code_max)
      return SymbolKind.get(yytranslate_table_[t]);
    else
      return SymbolKind.S_YYUNDEF;
  }
  private static final byte[] yytranslate_table_ = yytranslate_table_init();
  private static final byte[] yytranslate_table_init()
  {
    return new byte[]
    {
       0,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     1,     2,     3,     4,
       5,     6,     7,     8,     9,    10,    11,    12,    13,    14,
      15,    16,    17,    18,    19,    20,    21,    22,    23,    24,
      25,    26,    27,    28,    29,    30,    31,    32,    33,    34,
      35,    36,    37,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,    54,
      55,    56,    57,    58,    59,    60,    61,    62,    63,    64,
      65,    66,    67,    68
    };
  }


  private static final int YYLAST_ = 1300;
  private static final int YYEMPTY_ = -2;
  private static final int YYFINAL_ = 6;
  private static final int YYNTOKENS_ = 69;


}
/* "jflexbison/YYParser.y":1670  */


