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
    S_WAVE(69),                    /* WAVE  */
    S_BIT_AND(70),                 /* BIT_AND  */
    S_BIT_OR(71),                  /* BIT_OR  */
    S_SHIFT_LEFT(72),              /* SHIFT_LEFT  */
    S_SHIFT_RIGHT(73),             /* SHIFT_RIGHT  */
    S_DIVIDE_FLOOR(74),            /* DIVIDE_FLOOR  */
    S_DIVIDE_FLOOR_ASSIGN(75),     /* DIVIDE_FLOOR_ASSIGN  */
    S_UNARY_MINUS(76),             /* UNARY_MINUS  */
    S_UNARY_WAVE(77),              /* UNARY_WAVE  */
    S_YYACCEPT(78),                /* $accept  */
    S_program(79),                 /* program  */
    S_body(80),                    /* body  */
    S_function_declaration(81),    /* function_declaration  */
    S_function_declaration_arguments(82), /* function_declaration_arguments  */
    S_arg(83),                     /* arg  */
    S_function_call(84),           /* function_call  */
    S_function_call_arguments(85), /* function_call_arguments  */
    S_arg_expr(86),                /* arg_expr  */
    S_block(87),                   /* block  */
    S_stmt(88),                    /* stmt  */
    S_fake_call_stmt(89),          /* fake_call_stmt  */
    S_for_stmt(90),                /* for_stmt  */
    S_for_loop_stmt(91),           /* for_loop_stmt  */
    S_while_stmt(92),              /* while_stmt  */
    S_if_stmt(93),                 /* if_stmt  */
    S_elseif_stmt_list(94),        /* elseif_stmt_list  */
    S_elseif_stmt(95),             /* elseif_stmt  */
    S_else_stmt(96),               /* else_stmt  */
    S_cmp(97),                     /* cmp  */
    S_cmp_value(98),               /* cmp_value  */
    S_return_stmt(99),             /* return_stmt  */
    S_return_value_list(100),      /* return_value_list  */
    S_return_value(101),           /* return_value  */
    S_assign_stmt(102),            /* assign_stmt  */
    S_multi_assign_stmt(103),      /* multi_assign_stmt  */
    S_var_list(104),               /* var_list  */
    S_assign_value(105),           /* assign_value  */
    S_math_assign_stmt(106),       /* math_assign_stmt  */
    S_var(107),                    /* var  */
    S_variable(108),               /* variable  */
    S_math_expr(109),              /* math_expr  */
    S_expr_value(110),             /* expr_value  */
    S_unary_expr(111),             /* unary_expr  */
    S_expr(112),                   /* expr  */
    S_break(113),                  /* break  */
    S_continue(114),               /* continue  */
    S_sleep(115),                  /* sleep  */
    S_yield(116),                  /* yield  */
    S_switch_stmt(117),            /* switch_stmt  */
    S_switch_case_list(118),       /* switch_case_list  */
    S_switch_case_define(119),     /* switch_case_define  */
    S_package_head(120),           /* package_head  */
    S_include_head(121),           /* include_head  */
    S_include_define(122),         /* include_define  */
    S_struct_head(123),            /* struct_head  */
    S_struct_define(124),          /* struct_define  */
    S_struct_mem_declaration(125), /* struct_mem_declaration  */
    S_const_head(126),             /* const_head  */
    S_const_define(127),           /* const_define  */
    S_explicit_value(128),         /* explicit_value  */
    S_const_map_list_value(129),   /* const_map_list_value  */
    S_const_map_value(130),        /* const_map_value  */
    S_const_array_list_value(131); /* const_array_list_value  */


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
      SymbolKind.S_WAVE,
      SymbolKind.S_BIT_AND,
      SymbolKind.S_BIT_OR,
      SymbolKind.S_SHIFT_LEFT,
      SymbolKind.S_SHIFT_RIGHT,
      SymbolKind.S_DIVIDE_FLOOR,
      SymbolKind.S_DIVIDE_FLOOR_ASSIGN,
      SymbolKind.S_UNARY_MINUS,
      SymbolKind.S_UNARY_WAVE,
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
      SymbolKind.S_math_expr,
      SymbolKind.S_expr_value,
      SymbolKind.S_unary_expr,
      SymbolKind.S_expr,
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
  "CLOSE_BIG_BRACKET", "NULL", "WAVE", "BIT_AND", "BIT_OR", "SHIFT_LEFT",
  "SHIFT_RIGHT", "DIVIDE_FLOOR", "DIVIDE_FLOOR_ASSIGN", "UNARY_MINUS",
  "UNARY_WAVE", "$accept", "program", "body", "function_declaration",
  "function_declaration_arguments", "arg", "function_call",
  "function_call_arguments", "arg_expr", "block", "stmt", "fake_call_stmt",
  "for_stmt", "for_loop_stmt", "while_stmt", "if_stmt", "elseif_stmt_list",
  "elseif_stmt", "else_stmt", "cmp", "cmp_value", "return_stmt",
  "return_value_list", "return_value", "assign_stmt", "multi_assign_stmt",
  "var_list", "assign_value", "math_assign_stmt", "var", "variable",
  "math_expr", "expr_value", "unary_expr", "expr", "break", "continue",
  "sleep", "yield", "switch_stmt", "switch_case_list",
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
    /** Token WAVE, to be returned by the scanner.  */
    static final int WAVE = 324;
    /** Token BIT_AND, to be returned by the scanner.  */
    static final int BIT_AND = 325;
    /** Token BIT_OR, to be returned by the scanner.  */
    static final int BIT_OR = 326;
    /** Token SHIFT_LEFT, to be returned by the scanner.  */
    static final int SHIFT_LEFT = 327;
    /** Token SHIFT_RIGHT, to be returned by the scanner.  */
    static final int SHIFT_RIGHT = 328;
    /** Token DIVIDE_FLOOR, to be returned by the scanner.  */
    static final int DIVIDE_FLOOR = 329;
    /** Token DIVIDE_FLOOR_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_FLOOR_ASSIGN = 330;
    /** Token UNARY_MINUS, to be returned by the scanner.  */
    static final int UNARY_MINUS = 331;
    /** Token UNARY_WAVE, to be returned by the scanner.  */
    static final int UNARY_WAVE = 332;

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
    /* "jflexbison/YYParser.y":120  */
        {
	};
  break;


  case 6: /* function_declaration: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END  */
  if (yyn == 6)
    /* "jflexbison/YYParser.y":130  */
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
    /* "jflexbison/YYParser.y":141  */
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
    /* "jflexbison/YYParser.y":153  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
	};
  break;


  case 9: /* function_declaration_arguments: function_declaration_arguments ARG_SPLITTER arg  */
  if (yyn == 9)
    /* "jflexbison/YYParser.y":158  */
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
    /* "jflexbison/YYParser.y":169  */
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
    /* "jflexbison/YYParser.y":182  */
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
    /* "jflexbison/YYParser.y":196  */
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
    /* "jflexbison/YYParser.y":210  */
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
    /* "jflexbison/YYParser.y":224  */
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
    /* "jflexbison/YYParser.y":238  */
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
    /* "jflexbison/YYParser.y":257  */
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
    /* "jflexbison/YYParser.y":278  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
	};
  break;


  case 18: /* function_call_arguments: function_call_arguments ARG_SPLITTER arg_expr  */
  if (yyn == 18)
    /* "jflexbison/YYParser.y":283  */
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
    /* "jflexbison/YYParser.y":294  */
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
    /* "jflexbison/YYParser.y":307  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 21: /* block: block stmt  */
  if (yyn == 21)
    /* "jflexbison/YYParser.y":317  */
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
    /* "jflexbison/YYParser.y":328  */
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
    /* "jflexbison/YYParser.y":341  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: while_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 24: /* stmt: if_stmt  */
  if (yyn == 24)
    /* "jflexbison/YYParser.y":347  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: if_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 25: /* stmt: return_stmt  */
  if (yyn == 25)
    /* "jflexbison/YYParser.y":353  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 26: /* stmt: assign_stmt  */
  if (yyn == 26)
    /* "jflexbison/YYParser.y":359  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 27: /* stmt: multi_assign_stmt  */
  if (yyn == 27)
    /* "jflexbison/YYParser.y":365  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: multi_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 28: /* stmt: break  */
  if (yyn == 28)
    /* "jflexbison/YYParser.y":371  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: break");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 29: /* stmt: continue  */
  if (yyn == 29)
    /* "jflexbison/YYParser.y":377  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: continue");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 30: /* stmt: expr  */
  if (yyn == 30)
    /* "jflexbison/YYParser.y":383  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 31: /* stmt: math_assign_stmt  */
  if (yyn == 31)
    /* "jflexbison/YYParser.y":389  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 32: /* stmt: for_stmt  */
  if (yyn == 32)
    /* "jflexbison/YYParser.y":395  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 33: /* stmt: for_loop_stmt  */
  if (yyn == 33)
    /* "jflexbison/YYParser.y":401  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_loop_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 34: /* stmt: fake_call_stmt  */
  if (yyn == 34)
    /* "jflexbison/YYParser.y":407  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: fake_call_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 35: /* stmt: sleep  */
  if (yyn == 35)
    /* "jflexbison/YYParser.y":413  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: sleep_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 36: /* stmt: yield  */
  if (yyn == 36)
    /* "jflexbison/YYParser.y":419  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: yield_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 37: /* stmt: switch_stmt  */
  if (yyn == 37)
    /* "jflexbison/YYParser.y":425  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 38: /* fake_call_stmt: FAKE function_call  */
  if (yyn == 38)
    /* "jflexbison/YYParser.y":433  */
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
    /* "jflexbison/YYParser.y":446  */
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
    /* "jflexbison/YYParser.y":460  */
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
    /* "jflexbison/YYParser.y":476  */
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
    /* "jflexbison/YYParser.y":491  */
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
    /* "jflexbison/YYParser.y":508  */
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
    /* "jflexbison/YYParser.y":520  */
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
    /* "jflexbison/YYParser.y":534  */
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
    /* "jflexbison/YYParser.y":548  */
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
    /* "jflexbison/YYParser.y":564  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 48: /* elseif_stmt_list: elseif_stmt_list elseif_stmt  */
  if (yyn == 48)
    /* "jflexbison/YYParser.y":573  */
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
    /* "jflexbison/YYParser.y":584  */
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
    /* "jflexbison/YYParser.y":597  */
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
    /* "jflexbison/YYParser.y":609  */
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
    /* "jflexbison/YYParser.y":623  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 53: /* else_stmt: ELSE block  */
  if (yyn == 53)
    /* "jflexbison/YYParser.y":632  */
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
    /* "jflexbison/YYParser.y":643  */
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
    /* "jflexbison/YYParser.y":656  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET cmp CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 56: /* cmp: cmp AND cmp  */
  if (yyn == 56)
    /* "jflexbison/YYParser.y":662  */
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
    /* "jflexbison/YYParser.y":675  */
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
    /* "jflexbison/YYParser.y":688  */
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
    /* "jflexbison/YYParser.y":701  */
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
    /* "jflexbison/YYParser.y":714  */
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
    /* "jflexbison/YYParser.y":727  */
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
    /* "jflexbison/YYParser.y":740  */
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
    /* "jflexbison/YYParser.y":753  */
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


  case 64: /* cmp: cmp_value  */
  if (yyn == 64)
    /* "jflexbison/YYParser.y":769  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: cmp_value");
		cmp_stmt p = ((Yylex)yylexer).new_node(cmp_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_cmp = "is";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_right = null;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 65: /* cmp: IS cmp_value  */
  if (yyn == 65)
    /* "jflexbison/YYParser.y":782  */
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


  case 66: /* cmp: NOT cmp_value  */
  if (yyn == 66)
    /* "jflexbison/YYParser.y":795  */
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


  case 67: /* cmp_value: expr_value  */
  if (yyn == 67)
    /* "jflexbison/YYParser.y":813  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 68: /* return_stmt: RETURN return_value_list  */
  if (yyn == 68)
    /* "jflexbison/YYParser.y":821  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN return_value_list");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_returnlist = (return_value_list_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 69: /* return_stmt: RETURN  */
  if (yyn == 69)
    /* "jflexbison/YYParser.y":832  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_returnlist = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 70: /* return_value_list: return_value_list ARG_SPLITTER return_value  */
  if (yyn == 70)
    /* "jflexbison/YYParser.y":845  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value_list ARG_SPLITTER return_value");
		return_value_list_node p = (return_value_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 71: /* return_value_list: return_value  */
  if (yyn == 71)
    /* "jflexbison/YYParser.y":856  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value");
		return_value_list_node p = ((Yylex)yylexer).new_node(return_value_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 72: /* return_value: explicit_value  */
  if (yyn == 72)
    /* "jflexbison/YYParser.y":869  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 73: /* return_value: variable  */
  if (yyn == 73)
    /* "jflexbison/YYParser.y":875  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 74: /* return_value: expr  */
  if (yyn == 74)
    /* "jflexbison/YYParser.y":881  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 75: /* assign_stmt: var ASSIGN assign_value  */
  if (yyn == 75)
    /* "jflexbison/YYParser.y":889  */
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


  case 76: /* assign_stmt: var NEW_ASSIGN assign_value  */
  if (yyn == 76)
    /* "jflexbison/YYParser.y":902  */
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


  case 77: /* multi_assign_stmt: var_list ASSIGN function_call  */
  if (yyn == 77)
    /* "jflexbison/YYParser.y":917  */
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


  case 78: /* multi_assign_stmt: var_list NEW_ASSIGN function_call  */
  if (yyn == 78)
    /* "jflexbison/YYParser.y":930  */
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


  case 79: /* var_list: var_list ARG_SPLITTER var  */
  if (yyn == 79)
    /* "jflexbison/YYParser.y":945  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ARG_SPLITTER var");
		var_list_node p = (var_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 80: /* var_list: var  */
  if (yyn == 80)
    /* "jflexbison/YYParser.y":956  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var");
		var_list_node p = ((Yylex)yylexer).new_node(var_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 81: /* assign_value: explicit_value  */
  if (yyn == 81)
    /* "jflexbison/YYParser.y":969  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 82: /* assign_value: variable  */
  if (yyn == 82)
    /* "jflexbison/YYParser.y":975  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 83: /* assign_value: expr  */
  if (yyn == 83)
    /* "jflexbison/YYParser.y":981  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 84: /* math_assign_stmt: variable PLUS_ASSIGN assign_value  */
  if (yyn == 84)
    /* "jflexbison/YYParser.y":989  */
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


  case 85: /* math_assign_stmt: variable MINUS_ASSIGN assign_value  */
  if (yyn == 85)
    /* "jflexbison/YYParser.y":1002  */
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


  case 86: /* math_assign_stmt: variable DIVIDE_ASSIGN assign_value  */
  if (yyn == 86)
    /* "jflexbison/YYParser.y":1015  */
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


  case 87: /* math_assign_stmt: variable DIVIDE_FLOOR_ASSIGN assign_value  */
  if (yyn == 87)
    /* "jflexbison/YYParser.y":1028  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable DIVIDE_FLOOR_ASSIGN assign_value");
		math_assign_stmt p = ((Yylex)yylexer).new_node(math_assign_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_var = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_oper = "//=";
		p.m_value = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 88: /* math_assign_stmt: variable MULTIPLY_ASSIGN assign_value  */
  if (yyn == 88)
    /* "jflexbison/YYParser.y":1041  */
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


  case 89: /* math_assign_stmt: variable DIVIDE_MOD_ASSIGN assign_value  */
  if (yyn == 89)
    /* "jflexbison/YYParser.y":1054  */
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


  case 90: /* math_assign_stmt: variable INC  */
  if (yyn == 90)
    /* "jflexbison/YYParser.y":1067  */
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


  case 91: /* var: VAR_BEGIN IDENTIFIER  */
  if (yyn == 91)
    /* "jflexbison/YYParser.y":1086  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: VAR_BEGIN IDENTIFIER");
		var_node p = ((Yylex)yylexer).new_node(var_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 92: /* var: variable  */
  if (yyn == 92)
    /* "jflexbison/YYParser.y":1097  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 93: /* variable: IDENTIFIER  */
  if (yyn == 93)
    /* "jflexbison/YYParser.y":1105  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 94: /* variable: IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 94)
    /* "jflexbison/YYParser.y":1116  */
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


  case 95: /* variable: IDENTIFIER_POINTER  */
  if (yyn == 95)
    /* "jflexbison/YYParser.y":1128  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_POINTER");
		struct_pointer_node p = ((Yylex)yylexer).new_node(struct_pointer_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 96: /* variable: IDENTIFIER_DOT  */
  if (yyn == 96)
    /* "jflexbison/YYParser.y":1139  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 97: /* math_expr: OPEN_BRACKET expr_value CLOSE_BRACKET  */
  if (yyn == 97)
    /* "jflexbison/YYParser.y":1155  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET expr_value CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 98: /* math_expr: expr_value PLUS expr_value  */
  if (yyn == 98)
    /* "jflexbison/YYParser.y":1161  */
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


  case 99: /* math_expr: expr_value MINUS expr_value  */
  if (yyn == 99)
    /* "jflexbison/YYParser.y":1174  */
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


  case 100: /* math_expr: expr_value MULTIPLY expr_value  */
  if (yyn == 100)
    /* "jflexbison/YYParser.y":1187  */
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


  case 101: /* math_expr: expr_value DIVIDE expr_value  */
  if (yyn == 101)
    /* "jflexbison/YYParser.y":1200  */
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


  case 102: /* math_expr: expr_value DIVIDE_MOD expr_value  */
  if (yyn == 102)
    /* "jflexbison/YYParser.y":1213  */
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


  case 103: /* math_expr: expr_value DIVIDE_FLOOR expr_value  */
  if (yyn == 103)
    /* "jflexbison/YYParser.y":1226  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value DIVIDE_FLOOR expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "//";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 104: /* math_expr: expr_value BIT_AND expr_value  */
  if (yyn == 104)
    /* "jflexbison/YYParser.y":1239  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value BIT_AND expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "&";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 105: /* math_expr: expr_value BIT_OR expr_value  */
  if (yyn == 105)
    /* "jflexbison/YYParser.y":1252  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value BIT_OR expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "|";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 106: /* math_expr: expr_value WAVE expr_value  */
  if (yyn == 106)
    /* "jflexbison/YYParser.y":1265  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value WAVE expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "~";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 107: /* math_expr: expr_value SHIFT_LEFT expr_value  */
  if (yyn == 107)
    /* "jflexbison/YYParser.y":1278  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value SHIFT_LEFT expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = "<<";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 108: /* math_expr: expr_value SHIFT_RIGHT expr_value  */
  if (yyn == 108)
    /* "jflexbison/YYParser.y":1291  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value SHIFT_RIGHT expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (2))).ival);
		p.m_oper = ">>";
		p.m_left = (syntree_node)((ParserVal)yystack.valueAt (2)).obj;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 109: /* math_expr: expr_value STRING_CAT expr_value  */
  if (yyn == 109)
    /* "jflexbison/YYParser.y":1304  */
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


  case 110: /* expr_value: math_expr  */
  if (yyn == 110)
    /* "jflexbison/YYParser.y":1320  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 111: /* expr_value: unary_expr  */
  if (yyn == 111)
    /* "jflexbison/YYParser.y":1326  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 112: /* expr_value: explicit_value  */
  if (yyn == 112)
    /* "jflexbison/YYParser.y":1332  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 113: /* expr_value: function_call  */
  if (yyn == 113)
    /* "jflexbison/YYParser.y":1338  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 114: /* expr_value: variable  */
  if (yyn == 114)
    /* "jflexbison/YYParser.y":1344  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 115: /* unary_expr: UNARY_MINUS expr_value  */
  if (yyn == 115)
    /* "jflexbison/YYParser.y":1356  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: UNARY MINUS expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_oper = "-";
		p.m_unary = true;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 116: /* unary_expr: UNARY_WAVE expr_value  */
  if (yyn == 116)
    /* "jflexbison/YYParser.y":1369  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: UNARY WAVE expr_value");
		math_expr_node p = ((Yylex)yylexer).new_node(math_expr_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_oper = "~";
		p.m_unary = true;
		p.m_right = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 117: /* expr: function_call  */
  if (yyn == 117)
    /* "jflexbison/YYParser.y":1387  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 118: /* expr: math_expr  */
  if (yyn == 118)
    /* "jflexbison/YYParser.y":1393  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 119: /* expr: unary_expr  */
  if (yyn == 119)
    /* "jflexbison/YYParser.y":1399  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 120: /* break: BREAK  */
  if (yyn == 120)
    /* "jflexbison/YYParser.y":1407  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: BREAK");
		break_stmt p = ((Yylex)yylexer).new_node(break_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 121: /* continue: CONTINUE  */
  if (yyn == 121)
    /* "jflexbison/YYParser.y":1419  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CONTINUE");
		continue_stmt p = ((Yylex)yylexer).new_node(continue_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 122: /* sleep: SLEEP expr_value  */
  if (yyn == 122)
    /* "jflexbison/YYParser.y":1431  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SLEEP");
		sleep_stmt p = ((Yylex)yylexer).new_node(sleep_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 123: /* yield: YIELD expr_value  */
  if (yyn == 123)
    /* "jflexbison/YYParser.y":1444  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: YIELD");
		yield_stmt p = ((Yylex)yylexer).new_node(yield_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 124: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT block END  */
  if (yyn == 124)
    /* "jflexbison/YYParser.y":1457  */
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


  case 125: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT END  */
  if (yyn == 125)
    /* "jflexbison/YYParser.y":1470  */
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


  case 126: /* switch_case_list: switch_case_define  */
  if (yyn == 126)
    /* "jflexbison/YYParser.y":1485  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_define");
		switch_caselist_node p = ((Yylex)yylexer).new_node(switch_caselist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 127: /* switch_case_list: switch_case_list switch_case_define  */
  if (yyn == 127)
    /* "jflexbison/YYParser.y":1496  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_list switch_case_define");
		switch_caselist_node p = (switch_caselist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 128: /* switch_case_define: CASE cmp_value THEN block  */
  if (yyn == 128)
    /* "jflexbison/YYParser.y":1509  */
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


  case 129: /* switch_case_define: CASE cmp_value THEN  */
  if (yyn == 129)
    /* "jflexbison/YYParser.y":1521  */
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


  case 130: /* package_head: %empty  */
  if (yyn == 130)
    /* "jflexbison/YYParser.y":1535  */
        {
	};
  break;


  case 131: /* package_head: PACKAGE IDENTIFIER  */
  if (yyn == 131)
    /* "jflexbison/YYParser.y":1539  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 132: /* package_head: PACKAGE IDENTIFIER_DOT  */
  if (yyn == 132)
    /* "jflexbison/YYParser.y":1545  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER_DOT ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 133: /* include_head: %empty  */
  if (yyn == 133)
    /* "jflexbison/YYParser.y":1553  */
        {
	};
  break;


  case 136: /* include_define: INCLUDE STRING_DEFINITION  */
  if (yyn == 136)
    /* "jflexbison/YYParser.y":1563  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: INCLUDE STRING_DEFINITION ");
		((Yylex)yylexer).get_mybison().add_include(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 137: /* struct_head: %empty  */
  if (yyn == 137)
    /* "jflexbison/YYParser.y":1571  */
        {
	};
  break;


  case 140: /* struct_define: STRUCT IDENTIFIER struct_mem_declaration END  */
  if (yyn == 140)
    /* "jflexbison/YYParser.y":1581  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRUCT IDENTIFIER struct_mem_declaration END ");
		((Yylex)yylexer).get_mybison().add_struct_desc(((ParserVal)yystack.valueAt (2)).sval);
	};
  break;


  case 141: /* struct_mem_declaration: struct_mem_declaration IDENTIFIER  */
  if (yyn == 141)
    /* "jflexbison/YYParser.y":1589  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: struct_mem_declaration IDENTIFIER ");
	};
  break;


  case 142: /* struct_mem_declaration: IDENTIFIER  */
  if (yyn == 142)
    /* "jflexbison/YYParser.y":1594  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER ");
	};
  break;


  case 143: /* const_head: %empty  */
  if (yyn == 143)
    /* "jflexbison/YYParser.y":1601  */
        {
	};
  break;


  case 146: /* const_define: FCONST IDENTIFIER ASSIGN explicit_value  */
  if (yyn == 146)
    /* "jflexbison/YYParser.y":1611  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FCONST IDENTIFIER ASSIGN explicit_value ");
		((Yylex)yylexer).get_mybison().add_const_desc(((ParserVal)yystack.valueAt (2)).sval, (syntree_node)((ParserVal)yystack.valueAt (0)).obj);
	};
  break;


  case 147: /* explicit_value: NULL  */
  if (yyn == 147)
    /* "jflexbison/YYParser.y":1619  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NULL ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_NULL;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 148: /* explicit_value: FTRUE  */
  if (yyn == 148)
    /* "jflexbison/YYParser.y":1630  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_TRUE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 149: /* explicit_value: FFALSE  */
  if (yyn == 149)
    /* "jflexbison/YYParser.y":1641  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_FALSE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 150: /* explicit_value: NUMBER  */
  if (yyn == 150)
    /* "jflexbison/YYParser.y":1652  */
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


  case 151: /* explicit_value: FKUUID  */
  if (yyn == 151)
    /* "jflexbison/YYParser.y":1664  */
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


  case 152: /* explicit_value: STRING_DEFINITION  */
  if (yyn == 152)
    /* "jflexbison/YYParser.y":1676  */
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


  case 153: /* explicit_value: FKFLOAT  */
  if (yyn == 153)
    /* "jflexbison/YYParser.y":1688  */
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


  case 154: /* explicit_value: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET  */
  if (yyn == 154)
    /* "jflexbison/YYParser.y":1700  */
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


  case 155: /* explicit_value: OPEN_SQUARE_BRACKET const_array_list_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 155)
    /* "jflexbison/YYParser.y":1712  */
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


  case 156: /* const_map_list_value: %empty  */
  if (yyn == 156)
    /* "jflexbison/YYParser.y":1727  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 157: /* const_map_list_value: const_map_value  */
  if (yyn == 157)
    /* "jflexbison/YYParser.y":1737  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_value ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 158: /* const_map_list_value: const_map_list_value const_map_value  */
  if (yyn == 158)
    /* "jflexbison/YYParser.y":1748  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_list_value const_map_value ");
		const_map_list_value_node p = (const_map_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 159: /* const_map_value: explicit_value COLON explicit_value  */
  if (yyn == 159)
    /* "jflexbison/YYParser.y":1762  */
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


  case 160: /* const_array_list_value: %empty  */
  if (yyn == 160)
    /* "jflexbison/YYParser.y":1776  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 161: /* const_array_list_value: explicit_value  */
  if (yyn == 161)
    /* "jflexbison/YYParser.y":1786  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 162: /* const_array_list_value: const_array_list_value explicit_value  */
  if (yyn == 162)
    /* "jflexbison/YYParser.y":1797  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_array_list_value explicit_value ");
		const_array_list_value_node p = (const_array_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;



/* "src/main/java/com/github/esrrhs/fakescript/YYParser.java":2950  */

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

  private static final short yypact_ninf_ = -221;
  private static final short yytable_ninf_ = -115;

/* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
   STATE-NUM.  */
  private static final short[] yypact_ = yypact_init();
  private static final short[] yypact_init()
  {
    return new short[]
    {
      53,    12,    49,    15,  -221,  -221,  -221,   100,   -24,  -221,
    -221,   106,  -221,   132,  -221,   109,   133,  -221,    11,  -221,
    -221,   164,   112,   137,   165,  -221,  -221,  -221,  -221,    25,
     150,  -221,  -221,  -221,  -221,  -221,  -221,  -221,    25,    25,
    -221,  -221,   168,  -221,     7,   154,   219,  -221,  -221,    -1,
    -221,  -221,  -221,    25,  -221,  -221,   168,   576,  -221,  -221,
     182,  1554,  -221,   464,   464,  -221,    -3,  1554,  1551,    57,
     173,  -221,  -221,  1554,  1554,  1554,  1554,  1554,  1593,   651,
    -221,  -221,  -221,  -221,  -221,  -221,  -221,  -221,  -221,    16,
    -221,   -18,   863,   843,   993,  1068,  -221,  -221,  -221,  -221,
    -221,  -221,  -221,  -221,   180,  -221,  1615,  -221,  1143,   464,
    1554,  1554,    38,   128,   280,   167,  -221,   993,  -221,   256,
    1554,  1554,   768,   726,   -17,    38,   167,  1554,   993,   993,
     146,  -221,  -221,  1554,   198,  -221,  -221,   122,    57,    57,
    1554,  1554,  1554,  1554,  1554,  1554,  1554,   201,  -221,  1554,
    1554,  1554,  1554,  1554,  1554,  1554,  1554,  1554,  1554,  1554,
    1554,  1554,  1554,   266,   768,  -221,  -221,   801,   464,   464,
    1554,  1554,  1554,  1554,  1554,  1554,   876,     0,  -221,   993,
    1625,  -221,   464,  1554,    19,  1554,   127,  -221,    58,   190,
     178,  -221,  -221,  -221,    38,    38,  -221,  1615,  -221,  1143,
    -221,  -221,  -221,  -221,  -221,  -221,   195,  -221,    29,   136,
       2,   -10,    30,  -221,   875,    95,  1218,   625,   400,   169,
    -221,  -221,  -221,   951,   185,   185,  -221,  -221,  -221,  -221,
    -221,  -221,   464,   876,    -2,  -221,  1554,  -221,  -221,   156,
     166,  -221,   218,  1026,  -221,  -221,  1554,  1554,  -221,   261,
      -2,  1551,  -221,   223,  -221,  1551,  1554,  1551,  -221,  1101,
     121,   159,  1551,   224,  1551,  -221,  1176,   222,  1551,  -221,
    -221,  -221,  1551,  -221,  1251,  1554,  -221,  1326,   693,  -221,
    1401,  -221,  1476,  -221
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
     130,     0,     0,   133,   131,   132,     1,     0,   137,   134,
     136,     0,   135,   143,   138,     0,     0,   139,     3,   144,
     142,     0,     0,     0,     2,     4,   145,   140,   141,     0,
       0,     5,   148,   149,   152,   150,   153,   151,   160,   156,
     147,   146,     8,   161,     0,     0,     0,   157,    11,     0,
      10,   155,   162,     0,   154,   158,     0,     0,   159,     9,
       0,    69,   120,     0,     0,     7,    93,     0,     0,     0,
      96,    95,   121,     0,     0,     0,     0,     0,   117,     0,
      22,    34,    32,    33,    23,    24,    25,    26,    27,     0,
      31,    80,   114,   118,     0,   119,    30,    28,    29,    35,
      36,    37,   112,    91,    68,    71,    73,    74,    72,     0,
       0,     0,   113,     0,    64,   114,   110,    67,   111,     0,
      17,     0,     0,     0,    80,    38,     0,    17,   123,   122,
       0,   115,   116,    17,     0,     6,    21,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    90,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    67,    65,    66,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    47,     0,    19,    20,
       0,    97,     0,     0,     0,     0,     0,   126,     0,     0,
      93,    96,    79,    92,    77,    78,    75,    82,    83,    81,
      76,    84,    85,    86,    88,    89,     0,    87,   102,    98,
      99,   101,   100,   109,   106,   104,   105,   107,   108,   103,
      70,    55,    44,     0,    56,    57,    59,    58,    61,    62,
      60,    63,     0,    47,    52,    49,     0,    12,    94,     0,
      75,    13,     0,     0,   127,    14,    17,    17,    43,     0,
      52,    54,    48,     0,    18,     0,     0,   129,   125,     0,
       0,     0,    51,     0,    53,    46,     0,     0,   128,   124,
      16,    15,    50,    45,     0,     0,    40,     0,     0,    39,
       0,    42,     0,    41
    };
  }

/* YYPGOTO[NTERM-NUM].  */
  private static final short[] yypgoto_ = yypgoto_init();
  private static final short[] yypgoto_init()
  {
    return new short[]
    {
    -221,  -221,  -221,   228,  -221,   191,   -11,  -124,    20,   -12,
      70,  -221,  -221,  -221,  -221,  -221,    26,  -220,    23,   -62,
     -74,  -221,  -221,   108,  -221,  -221,  -221,   171,  -221,   -63,
     -57,   135,   393,   181,     1,  -221,  -221,  -221,  -221,  -221,
    -221,    88,  -221,  -221,   274,  -221,   270,  -221,  -221,   273,
     345,  -221,   242,  -221
    };
  }

/* YYDEFGOTO[NTERM-NUM].  */
  private static final short[] yydefgoto_ = yydefgoto_init();
  private static final short[] yydefgoto_init()
  {
    return new short[]
    {
       0,     2,    24,    25,    49,    50,   112,   177,   178,    79,
      80,    81,    82,    83,    84,    85,   234,   235,   253,   113,
     114,    86,   104,   105,    87,    88,    89,   196,    90,    91,
     115,   116,    94,   118,    96,    97,    98,    99,   100,   101,
     186,   187,     3,     8,     9,    13,    14,    21,    18,    19,
     102,    46,    47,    44
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
      92,   130,   119,   184,   106,   124,   140,   183,   150,   188,
     251,    92,   126,   154,   252,    32,    33,    23,    56,   236,
     150,    34,    92,    35,   153,   154,     7,     4,   120,    11,
     252,    57,   237,    32,    33,   137,   165,   166,   236,    34,
     138,    35,    36,   121,   141,   141,    78,   163,   150,     6,
      78,   241,    37,    38,    51,   155,   123,    78,   125,    16,
      36,   232,   107,     5,   161,     7,    92,   155,    78,   133,
      37,    38,    66,    39,   192,    40,   161,   236,   139,   134,
     193,   126,   126,   197,   197,   197,   197,   197,   197,   197,
     245,    39,   197,    40,   155,   155,   226,   227,   228,   229,
     230,   231,     1,   161,   161,   106,   224,   225,    70,    71,
      92,   242,    78,   150,    10,   151,   152,   153,   154,    92,
     239,    15,   260,   261,    20,    60,   197,   194,   195,    78,
      78,    78,    78,    78,    78,    78,    29,   190,    78,   167,
     236,   198,   198,   198,   198,   198,   198,   198,    22,   136,
     198,    78,    30,   270,   150,   223,    78,   152,   153,   154,
     155,   168,   169,   107,   233,    78,    92,   159,   160,   161,
     249,    23,    78,   191,    71,   255,    92,    27,   236,    28,
      16,    42,   267,    48,   198,    11,    92,   185,   243,   168,
     169,   271,    93,   136,    92,    53,    93,   103,    92,   162,
      92,   155,    92,    93,   127,    92,   185,    92,   147,    92,
     161,    92,    78,   189,    93,    92,   206,    92,   168,   169,
      92,   246,    78,    92,   121,    92,   247,    32,    33,   257,
     256,   259,    78,    34,   155,    35,   265,   273,    95,   264,
      78,   275,    95,   266,    78,   268,    78,    59,    78,    95,
     272,    78,    31,    78,    36,    78,   254,    78,    93,   250,
      95,    78,   277,    78,    37,    38,    78,   176,   282,    78,
     220,    78,   262,   263,   244,    93,    93,    93,    93,    93,
      93,    93,    12,    17,    93,    39,    54,    40,    55,   168,
     169,    26,     0,   136,   168,   169,     0,    93,   221,   168,
     169,     0,    93,   136,    95,   170,   171,   172,   173,   174,
     175,    93,   200,   201,   202,   203,   204,   205,    93,     0,
     207,    95,    95,    95,    95,    95,    95,    95,     0,   136,
      95,     0,     0,     0,   136,     0,   136,     0,   136,     0,
       0,     0,   136,    95,     0,     0,     0,   136,    95,     0,
       0,     0,   136,     0,   240,     0,     0,    95,    93,     0,
       0,     0,     0,     0,    95,     0,     0,     0,    93,     0,
       0,     0,     0,     0,    41,     0,     0,     0,    93,     0,
       0,     0,     0,    43,    45,     0,    93,     0,     0,    52,
      93,    45,    93,     0,    93,     0,     0,    93,    58,    93,
       0,    93,     0,    93,    95,     0,   108,    93,     0,    93,
       0,     0,    93,     0,    95,    93,     0,    93,   150,     0,
     151,   152,   153,   154,    95,     0,     0,     0,     0,     0,
       0,     0,    95,     0,     0,     0,    95,     0,    95,     0,
      95,     0,     0,    95,     0,    95,     0,    95,     0,    95,
       0,     0,     0,    95,     0,    95,   117,   117,    95,     0,
     122,    95,     0,    95,     0,   155,   128,   129,   117,   131,
     132,     0,    32,    33,   161,     0,     0,     0,    34,    66,
      35,     0,     0,     0,     0,   199,   199,   199,   199,   199,
     199,   199,     0,     0,   199,   109,     0,     0,     0,    36,
       0,     0,   164,   117,   117,     0,     0,   108,     0,    37,
      38,     0,     0,   179,   180,    70,    71,     0,   110,   111,
     179,     0,     0,     0,     0,     0,   179,     0,   199,     0,
      39,     0,    40,     0,     0,     0,     0,     0,     0,     0,
      76,    77,     0,   208,   209,   210,   211,   212,   213,   214,
     215,   216,   217,   218,   219,     0,     0,     0,     0,     0,
       0,   117,   117,   117,   117,   117,   117,   117,   117,     0,
       0,     0,     0,     0,     0,   117,     0,     0,   117,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,    65,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    68,     0,
      69,    37,    38,     0,     0,   117,     0,    70,    71,   179,
       0,     0,    72,    73,    74,    75,     0,     0,     0,   179,
     179,     0,    39,   150,    40,   151,   152,   153,   154,   117,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   135,    34,    66,    35,   278,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,     0,    36,     0,     0,     0,
     155,     0,     0,    68,     0,    69,    37,    38,   160,   161,
       0,     0,    70,    71,   280,     0,     0,    72,    73,    74,
      75,   150,     0,   151,   152,   153,   154,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,     0,
      34,    66,    35,     0,     0,   182,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,   155,     0,
       0,    36,   156,   157,   158,   159,   160,   161,    68,     0,
      69,    37,    38,     0,     0,     0,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,   150,     0,   151,   152,
     153,   154,    39,     0,    40,     0,     0,     0,     0,     0,
     181,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   222,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,   155,     0,     0,    36,   156,   157,   158,
     159,   160,   161,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,  -110,     0,  -110,  -110,  -110,  -110,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,   -92,    63,    32,    33,    64,   -92,     0,     0,
      34,    66,    35,   150,     0,   151,   152,   153,   154,   142,
     143,   144,   145,   146,   147,     0,   148,    67,  -110,     0,
       0,    36,  -110,  -110,  -110,  -110,  -110,  -110,    68,     0,
      69,    37,    38,     0,     0,   -92,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,     0,     0,   149,   232,
     155,     0,    39,     0,    40,   157,     0,   159,   160,   161,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   248,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,   150,     0,   151,   152,   153,   154,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,   258,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,   155,     0,
       0,    36,   156,   157,   158,   159,   160,   161,    68,     0,
      69,    37,    38,     0,     0,     0,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,  -111,     0,  -111,  -111,
    -111,  -111,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   269,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,  -111,     0,     0,    36,  -111,  -111,  -111,
    -111,  -111,  -111,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,  -112,     0,  -112,  -112,  -112,  -112,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,     0,    63,    32,    33,    64,   274,     0,     0,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,  -112,     0,
       0,    36,  -112,  -112,  -112,  -112,  -112,  -112,    68,     0,
      69,    37,    38,     0,     0,     0,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,   150,     0,   151,   152,
     153,   154,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   276,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,   155,     0,     0,    36,   156,   157,     0,
     159,   160,   161,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,     0,     0,     0,     0,     0,     0,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,   279,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    68,     0,
      69,    37,    38,     0,     0,     0,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,     0,     0,     0,     0,
       0,     0,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,   281,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    68,     0,    69,    37,    38,     0,     0,
       0,     0,    70,    71,     0,     0,     0,    72,    73,    74,
      75,     0,     0,     0,     0,     0,     0,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    76,    77,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,   283,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    68,     0,
      69,    37,    38,     0,     0,     0,     0,    70,    71,     0,
       0,     0,    72,    73,    74,    75,     0,     0,     0,     0,
       0,     0,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    76,    77,    60,    61,    62,     0,    63,    32,
      33,    64,    32,    33,     0,    34,    66,    35,    34,    66,
      35,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,     0,     0,    67,    36,     0,     0,    36,
       0,     0,     0,    68,     0,    69,    37,    38,     0,    37,
      38,     0,    70,    71,     0,    70,    71,    72,    73,    74,
      75,  -113,     0,  -113,  -113,  -113,  -113,    39,     0,    40,
      39,     0,    40,     0,   133,     0,     0,    76,    77,     0,
      76,    77,     0,  -114,   134,  -114,  -114,  -114,  -114,     0,
       0,     0,     0,   150,     0,   151,   152,   153,   154,     0,
       0,     0,     0,     0,     0,     0,   147,     0,  -113,     0,
       0,     0,  -113,  -113,  -113,  -113,  -113,  -113,     0,     0,
       0,     0,   238,     0,     0,     0,     0,     0,     0,     0,
    -114,     0,     0,     0,  -114,  -114,  -114,  -114,  -114,  -114,
     155,     0,     0,     0,   156,   157,   158,   159,   160,   161
    };
  }

private static final short[] yycheck_ = yycheck_init();
  private static final short[] yycheck_init()
  {
    return new short[]
    {
      57,    75,    64,   127,    61,    68,    24,    24,    18,   133,
      12,    68,    69,    23,   234,     8,     9,     6,    19,    19,
      18,    14,    79,    16,    22,    23,    50,    15,    31,    53,
     250,    32,    32,     8,     9,    19,   110,   111,    19,    14,
      24,    16,    35,    46,    62,    62,    57,   109,    18,     0,
      61,    32,    45,    46,    47,    65,    68,    68,    69,    48,
      35,    63,    61,    51,    74,    50,   123,    65,    79,    31,
      45,    46,    15,    66,   137,    68,    74,    19,    62,    41,
     137,   138,   139,   140,   141,   142,   143,   144,   145,   146,
      32,    66,   149,    68,    65,    65,   170,   171,   172,   173,
     174,   175,    49,    74,    74,   162,   168,   169,    51,    52,
     167,   185,   123,    18,    14,    20,    21,    22,    23,   176,
     182,    15,   246,   247,    15,     3,   183,   138,   139,   140,
     141,   142,   143,   144,   145,   146,    24,    15,   149,    11,
      19,   140,   141,   142,   143,   144,   145,   146,    15,    79,
     149,   162,    15,    32,    18,   167,   167,    21,    22,    23,
      65,    33,    34,   162,   176,   176,   223,    72,    73,    74,
     232,     6,   183,    51,    52,    19,   233,    13,    19,    15,
      48,    31,   256,    15,   183,    53,   243,    60,    61,    33,
      34,    32,    57,   123,   251,    41,    61,    15,   255,    19,
     257,    65,   259,    68,    31,   262,    60,   264,    41,   266,
      74,   268,   223,    15,    79,   272,    15,   274,    33,    34,
     277,    31,   233,   280,    46,   282,    31,     8,     9,    11,
      64,   243,   243,    14,    65,    16,    13,    13,    57,   251,
     251,    19,    61,   255,   255,   257,   257,    56,   259,    68,
     262,   262,    24,   264,    35,   266,   236,   268,   123,   233,
      79,   272,   274,   274,    45,    46,   277,    11,   280,   280,
     162,   282,    11,   250,   186,   140,   141,   142,   143,   144,
     145,   146,     8,    13,   149,    66,    67,    68,    46,    33,
      34,    18,    -1,   223,    33,    34,    -1,   162,    32,    33,
      34,    -1,   167,   233,   123,    25,    26,    27,    28,    29,
      30,   176,   141,   142,   143,   144,   145,   146,   183,    -1,
     149,   140,   141,   142,   143,   144,   145,   146,    -1,   259,
     149,    -1,    -1,    -1,   264,    -1,   266,    -1,   268,    -1,
      -1,    -1,   272,   162,    -1,    -1,    -1,   277,   167,    -1,
      -1,    -1,   282,    -1,   183,    -1,    -1,   176,   223,    -1,
      -1,    -1,    -1,    -1,   183,    -1,    -1,    -1,   233,    -1,
      -1,    -1,    -1,    -1,    29,    -1,    -1,    -1,   243,    -1,
      -1,    -1,    -1,    38,    39,    -1,   251,    -1,    -1,    44,
     255,    46,   257,    -1,   259,    -1,    -1,   262,    53,   264,
      -1,   266,    -1,   268,   223,    -1,    61,   272,    -1,   274,
      -1,    -1,   277,    -1,   233,   280,    -1,   282,    18,    -1,
      20,    21,    22,    23,   243,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,   251,    -1,    -1,    -1,   255,    -1,   257,    -1,
     259,    -1,    -1,   262,    -1,   264,    -1,   266,    -1,   268,
      -1,    -1,    -1,   272,    -1,   274,    63,    64,   277,    -1,
      67,   280,    -1,   282,    -1,    65,    73,    74,    75,    76,
      77,    -1,     8,     9,    74,    -1,    -1,    -1,    14,    15,
      16,    -1,    -1,    -1,    -1,   140,   141,   142,   143,   144,
     145,   146,    -1,    -1,   149,    31,    -1,    -1,    -1,    35,
      -1,    -1,   109,   110,   111,    -1,    -1,   162,    -1,    45,
      46,    -1,    -1,   120,   121,    51,    52,    -1,    54,    55,
     127,    -1,    -1,    -1,    -1,    -1,   133,    -1,   183,    -1,
      66,    -1,    68,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      76,    77,    -1,   150,   151,   152,   153,   154,   155,   156,
     157,   158,   159,   160,   161,    -1,    -1,    -1,    -1,    -1,
      -1,   168,   169,   170,   171,   172,   173,   174,   175,    -1,
      -1,    -1,    -1,    -1,    -1,   182,    -1,    -1,   185,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    13,
      14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    -1,    -1,
      -1,    35,    -1,    -1,    -1,    -1,    -1,    -1,    42,    -1,
      44,    45,    46,    -1,    -1,   232,    -1,    51,    52,   236,
      -1,    -1,    56,    57,    58,    59,    -1,    -1,    -1,   246,
     247,    -1,    66,    18,    68,    20,    21,    22,    23,   256,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,   275,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      65,    -1,    -1,    42,    -1,    44,    45,    46,    73,    74,
      -1,    -1,    51,    52,    11,    -1,    -1,    56,    57,    58,
      59,    18,    -1,    20,    21,    22,    23,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    -1,
      14,    15,    16,    -1,    -1,    19,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    65,    -1,
      -1,    35,    69,    70,    71,    72,    73,    74,    42,    -1,
      44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    18,    -1,    20,    21,
      22,    23,    66,    -1,    68,    -1,    -1,    -1,    -1,    -1,
      32,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    65,    -1,    -1,    35,    69,    70,    71,
      72,    73,    74,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    18,    -1,    20,    21,    22,    23,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    19,     7,     8,     9,    10,    24,    -1,    -1,
      14,    15,    16,    18,    -1,    20,    21,    22,    23,    36,
      37,    38,    39,    40,    41,    -1,    43,    31,    65,    -1,
      -1,    35,    69,    70,    71,    72,    73,    74,    42,    -1,
      44,    45,    46,    -1,    -1,    62,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    -1,    -1,    75,    63,
      65,    -1,    66,    -1,    68,    70,    -1,    72,    73,    74,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    18,    -1,    20,    21,    22,    23,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    13,
      14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    65,    -1,
      -1,    35,    69,    70,    71,    72,    73,    74,    42,    -1,
      44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    18,    -1,    20,    21,
      22,    23,    66,    -1,    68,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    65,    -1,    -1,    35,    69,    70,    71,
      72,    73,    74,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    18,    -1,    20,    21,    22,    23,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    -1,     7,     8,     9,    10,    11,    -1,    -1,
      14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    65,    -1,
      -1,    35,    69,    70,    71,    72,    73,    74,    42,    -1,
      44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    18,    -1,    20,    21,
      22,    23,    66,    -1,    68,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    65,    -1,    -1,    35,    69,    70,    -1,
      72,    73,    74,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    -1,    -1,    -1,    -1,    -1,    -1,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    13,
      14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    -1,    -1,
      -1,    35,    -1,    -1,    -1,    -1,    -1,    -1,    42,    -1,
      44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    -1,    -1,    -1,    -1,
      -1,    -1,    66,    -1,    68,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    13,    14,    15,    16,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    -1,    35,    -1,    -1,    -1,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    -1,
      -1,    -1,    51,    52,    -1,    -1,    -1,    56,    57,    58,
      59,    -1,    -1,    -1,    -1,    -1,    -1,    66,    -1,    68,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    13,
      14,    15,    16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    31,    -1,    -1,
      -1,    35,    -1,    -1,    -1,    -1,    -1,    -1,    42,    -1,
      44,    45,    46,    -1,    -1,    -1,    -1,    51,    52,    -1,
      -1,    -1,    56,    57,    58,    59,    -1,    -1,    -1,    -1,
      -1,    -1,    66,    -1,    68,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    76,    77,     3,     4,     5,    -1,     7,     8,
       9,    10,     8,     9,    -1,    14,    15,    16,    14,    15,
      16,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    31,    -1,    -1,    31,    35,    -1,    -1,    35,
      -1,    -1,    -1,    42,    -1,    44,    45,    46,    -1,    45,
      46,    -1,    51,    52,    -1,    51,    52,    56,    57,    58,
      59,    18,    -1,    20,    21,    22,    23,    66,    -1,    68,
      66,    -1,    68,    -1,    31,    -1,    -1,    76,    77,    -1,
      76,    77,    -1,    18,    41,    20,    21,    22,    23,    -1,
      -1,    -1,    -1,    18,    -1,    20,    21,    22,    23,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    41,    -1,    65,    -1,
      -1,    -1,    69,    70,    71,    72,    73,    74,    -1,    -1,
      -1,    -1,    47,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      65,    -1,    -1,    -1,    69,    70,    71,    72,    73,    74,
      65,    -1,    -1,    -1,    69,    70,    71,    72,    73,    74
    };
  }

/* YYSTOS[STATE-NUM] -- The symbol kind of the accessing symbol of
   state STATE-NUM.  */
  private static final short[] yystos_ = yystos_init();
  private static final short[] yystos_init()
  {
    return new short[]
    {
       0,    49,    79,   120,    15,    51,     0,    50,   121,   122,
      14,    53,   122,   123,   124,    15,    48,   124,   126,   127,
      15,   125,    15,     6,    80,    81,   127,    13,    15,    24,
      15,    81,     8,     9,    14,    16,    35,    45,    46,    66,
      68,   128,    31,   128,   131,   128,   129,   130,    15,    82,
      83,    47,   128,    41,    67,   130,    19,    32,   128,    83,
       3,     4,     5,     7,    10,    13,    15,    31,    42,    44,
      51,    52,    56,    57,    58,    59,    76,    77,    84,    87,
      88,    89,    90,    91,    92,    93,    99,   102,   103,   104,
     106,   107,   108,   109,   110,   111,   112,   113,   114,   115,
     116,   117,   128,    15,   100,   101,   108,   112,   128,    31,
      54,    55,    84,    97,    98,   108,   109,   110,   111,    97,
      31,    46,   110,    87,   107,    84,   108,    31,   110,   110,
      98,   110,   110,    31,    41,    13,    88,    19,    24,    62,
      24,    62,    36,    37,    38,    39,    40,    41,    43,    75,
      18,    20,    21,    22,    23,    65,    69,    70,    71,    72,
      73,    74,    19,    97,   110,    98,    98,    11,    33,    34,
      25,    26,    27,    28,    29,    30,    11,    85,    86,   110,
     110,    32,    19,    24,    85,    60,   118,   119,    85,    15,
      15,    51,   107,   108,    84,    84,   105,   108,   112,   128,
     105,   105,   105,   105,   105,   105,    15,   105,   110,   110,
     110,   110,   110,   110,   110,   110,   110,   110,   110,   110,
     101,    32,    13,    87,    97,    97,    98,    98,    98,    98,
      98,    98,    63,    87,    94,    95,    19,    32,    47,    97,
     105,    32,    98,    61,   119,    32,    31,    31,    13,    97,
      94,    12,    95,    96,    86,    19,    64,    11,    13,    87,
      85,    85,    11,    96,    87,    13,    87,    98,    87,    13,
      32,    32,    87,    13,    11,    19,    13,    87,   110,    13,
      11,    13,    87,    13
    };
  }

/* YYR1[RULE-NUM] -- Symbol kind of the left-hand side of rule RULE-NUM.  */
  private static final short[] yyr1_ = yyr1_init();
  private static final short[] yyr1_init()
  {
    return new short[]
    {
       0,    78,    79,    80,    80,    80,    81,    81,    82,    82,
      82,    83,    84,    84,    84,    84,    84,    85,    85,    85,
      86,    87,    87,    88,    88,    88,    88,    88,    88,    88,
      88,    88,    88,    88,    88,    88,    88,    88,    89,    90,
      90,    91,    91,    92,    92,    93,    93,    94,    94,    94,
      95,    95,    96,    96,    96,    97,    97,    97,    97,    97,
      97,    97,    97,    97,    97,    97,    97,    98,    99,    99,
     100,   100,   101,   101,   101,   102,   102,   103,   103,   104,
     104,   105,   105,   105,   106,   106,   106,   106,   106,   106,
     106,   107,   107,   108,   108,   108,   108,   109,   109,   109,
     109,   109,   109,   109,   109,   109,   109,   109,   109,   109,
     110,   110,   110,   110,   110,   111,   111,   112,   112,   112,
     113,   114,   115,   116,   117,   117,   118,   118,   119,   119,
     120,   120,   120,   121,   121,   121,   122,   123,   123,   123,
     124,   125,   125,   126,   126,   126,   127,   128,   128,   128,
     128,   128,   128,   128,   128,   128,   129,   129,   129,   130,
     131,   131,   131
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
       3,     3,     3,     3,     1,     2,     2,     1,     2,     1,
       3,     1,     1,     1,     1,     3,     3,     3,     3,     3,
       1,     1,     1,     1,     3,     3,     3,     3,     3,     3,
       2,     2,     1,     1,     4,     1,     1,     3,     3,     3,
       3,     3,     3,     3,     3,     3,     3,     3,     3,     3,
       1,     1,     1,     1,     1,     2,     2,     1,     1,     1,
       1,     1,     2,     2,     6,     5,     1,     2,     4,     3,
       0,     2,     2,     0,     1,     2,     2,     0,     1,     2,
       4,     2,     1,     0,     1,     2,     4,     1,     1,     1,
       1,     1,     1,     1,     3,     3,     0,     1,     2,     3,
       0,     1,     2
    };
  }




  /* YYTRANSLATE_(TOKEN-NUM) -- Symbol number corresponding to TOKEN-NUM
     as returned by yylex, with out-of-bounds checking.  */
  private static final SymbolKind yytranslate_(int t)
  {
    // Last valid token kind.
    int code_max = 332;
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
      65,    66,    67,    68,    69,    70,    71,    72,    73,    74,
      75,    76,    77
    };
  }


  private static final int YYLAST_ = 1699;
  private static final int YYEMPTY_ = -2;
  private static final int YYFINAL_ = 6;
  private static final int YYNTOKENS_ = 78;


}
/* "jflexbison/YYParser.y":1808  */


