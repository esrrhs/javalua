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
/* "YYParser.y":1  */

package com.github.esrrhs.fakescript;

import com.github.esrrhs.fakescript.syntree.*;
import java.io.*;


/* "/Users/mingming/project/fakescript_java/src/main/java/com/github/esrrhs/fakescript/YYParser.java":48  */


import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * A Bison parser, automatically generated from <tt>YYParser.y</tt>.
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
    S_DO(12),                      /* DO  */
    S_ELSE(13),                    /* ELSE  */
    S_END(14),                     /* END  */
    S_STRING_DEFINITION(15),       /* STRING_DEFINITION  */
    S_IDENTIFIER(16),              /* IDENTIFIER  */
    S_NUMBER(17),                  /* NUMBER  */
    S_SINGLE_LINE_COMMENT(18),     /* SINGLE_LINE_COMMENT  */
    S_DIVIDE_MOD(19),              /* DIVIDE_MOD  */
    S_ARG_SPLITTER(20),            /* ARG_SPLITTER  */
    S_PLUS(21),                    /* PLUS  */
    S_MINUS(22),                   /* MINUS  */
    S_DIVIDE(23),                  /* DIVIDE  */
    S_MULTIPLY(24),                /* MULTIPLY  */
    S_ASSIGN(25),                  /* ASSIGN  */
    S_MORE(26),                    /* MORE  */
    S_LESS(27),                    /* LESS  */
    S_MORE_OR_EQUAL(28),           /* MORE_OR_EQUAL  */
    S_LESS_OR_EQUAL(29),           /* LESS_OR_EQUAL  */
    S_EQUAL(30),                   /* EQUAL  */
    S_NOT_EQUAL(31),               /* NOT_EQUAL  */
    S_SHARP(32),                   /* SHARP  */
    S_OPEN_BRACKET(33),            /* OPEN_BRACKET  */
    S_CLOSE_BRACKET(34),           /* CLOSE_BRACKET  */
    S_AND(35),                     /* AND  */
    S_OR(36),                      /* OR  */
    S_FKFLOAT(37),                 /* FKFLOAT  */
    S_PLUS_ASSIGN(38),             /* PLUS_ASSIGN  */
    S_MINUS_ASSIGN(39),            /* MINUS_ASSIGN  */
    S_DIVIDE_ASSIGN(40),           /* DIVIDE_ASSIGN  */
    S_MULTIPLY_ASSIGN(41),         /* MULTIPLY_ASSIGN  */
    S_DIVIDE_MOD_ASSIGN(42),       /* DIVIDE_MOD_ASSIGN  */
    S_COLON(43),                   /* COLON  */
    S_FOR(44),                     /* FOR  */
    S_INC(45),                     /* INC  */
    S_FAKE(46),                    /* FAKE  */
    S_FKUUID(47),                  /* FKUUID  */
    S_OPEN_SQUARE_BRACKET(48),     /* OPEN_SQUARE_BRACKET  */
    S_CLOSE_SQUARE_BRACKET(49),    /* CLOSE_SQUARE_BRACKET  */
    S_FCONST(50),                  /* FCONST  */
    S_PACKAGE(51),                 /* PACKAGE  */
    S_INCLUDE(52),                 /* INCLUDE  */
    S_IDENTIFIER_DOT(53),          /* IDENTIFIER_DOT  */
    S_IDENTIFIER_POINTER(54),      /* IDENTIFIER_POINTER  */
    S_STRUCT(55),                  /* STRUCT  */
    S_IS(56),                      /* IS  */
    S_NOT(57),                     /* NOT  */
    S_CONTINUE(58),                /* CONTINUE  */
    S_YIELD(59),                   /* YIELD  */
    S_SLEEP(60),                   /* SLEEP  */
    S_SWITCH(61),                  /* SWITCH  */
    S_CASE(62),                    /* CASE  */
    S_DEFAULT(63),                 /* DEFAULT  */
    S_NEW_ASSIGN(64),              /* NEW_ASSIGN  */
    S_ELSEIF(65),                  /* ELSEIF  */
    S_RIGHT_POINTER(66),           /* RIGHT_POINTER  */
    S_STRING_CAT(67),              /* STRING_CAT  */
    S_OPEN_BIG_BRACKET(68),        /* OPEN_BIG_BRACKET  */
    S_CLOSE_BIG_BRACKET(69),       /* CLOSE_BIG_BRACKET  */
    S_NULL(70),                    /* NULL  */
    S_WAVE(71),                    /* WAVE  */
    S_BIT_AND(72),                 /* BIT_AND  */
    S_BIT_OR(73),                  /* BIT_OR  */
    S_SHIFT_LEFT(74),              /* SHIFT_LEFT  */
    S_SHIFT_RIGHT(75),             /* SHIFT_RIGHT  */
    S_DIVIDE_FLOOR(76),            /* DIVIDE_FLOOR  */
    S_DIVIDE_FLOOR_ASSIGN(77),     /* DIVIDE_FLOOR_ASSIGN  */
    S_UNARY_MINUS(78),             /* UNARY_MINUS  */
    S_UNARY_WAVE(79),              /* UNARY_WAVE  */
    S_YYACCEPT(80),                /* $accept  */
    S_program(81),                 /* program  */
    S_body(82),                    /* body  */
    S_function_declaration(83),    /* function_declaration  */
    S_function_declaration_arguments(84), /* function_declaration_arguments  */
    S_arg(85),                     /* arg  */
    S_function_call(86),           /* function_call  */
    S_function_call_arguments(87), /* function_call_arguments  */
    S_arg_expr(88),                /* arg_expr  */
    S_block(89),                   /* block  */
    S_stmt(90),                    /* stmt  */
    S_fake_call_stmt(91),          /* fake_call_stmt  */
    S_for_stmt(92),                /* for_stmt  */
    S_for_loop_stmt(93),           /* for_loop_stmt  */
    S_while_stmt(94),              /* while_stmt  */
    S_if_stmt(95),                 /* if_stmt  */
    S_elseif_stmt_list(96),        /* elseif_stmt_list  */
    S_elseif_stmt(97),             /* elseif_stmt  */
    S_else_stmt(98),               /* else_stmt  */
    S_cmp(99),                     /* cmp  */
    S_cmp_value(100),              /* cmp_value  */
    S_return_stmt(101),            /* return_stmt  */
    S_return_value_list(102),      /* return_value_list  */
    S_return_value(103),           /* return_value  */
    S_assign_stmt(104),            /* assign_stmt  */
    S_multi_assign_stmt(105),      /* multi_assign_stmt  */
    S_var_list(106),               /* var_list  */
    S_assign_value(107),           /* assign_value  */
    S_math_assign_stmt(108),       /* math_assign_stmt  */
    S_var(109),                    /* var  */
    S_variable(110),               /* variable  */
    S_math_expr(111),              /* math_expr  */
    S_expr_value(112),             /* expr_value  */
    S_unary_expr(113),             /* unary_expr  */
    S_expr(114),                   /* expr  */
    S_break(115),                  /* break  */
    S_continue(116),               /* continue  */
    S_sleep(117),                  /* sleep  */
    S_yield(118),                  /* yield  */
    S_switch_stmt(119),            /* switch_stmt  */
    S_switch_case_list(120),       /* switch_case_list  */
    S_switch_case_define(121),     /* switch_case_define  */
    S_package_head(122),           /* package_head  */
    S_include_head(123),           /* include_head  */
    S_include_define(124),         /* include_define  */
    S_struct_head(125),            /* struct_head  */
    S_struct_define(126),          /* struct_define  */
    S_struct_mem_declaration(127), /* struct_mem_declaration  */
    S_const_head(128),             /* const_head  */
    S_const_define(129),           /* const_define  */
    S_explicit_value(130),         /* explicit_value  */
    S_const_map_list_value(131),   /* const_map_list_value  */
    S_const_map_value(132),        /* const_map_value  */
    S_const_array_list_value(133); /* const_array_list_value  */


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
      SymbolKind.S_DO,
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
      SymbolKind.S_SHARP,
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
  "BREAK", "FUNC", "WHILE", "FTRUE", "FFALSE", "IF", "THEN", "DO", "ELSE",
  "END", "STRING_DEFINITION", "IDENTIFIER", "NUMBER",
  "SINGLE_LINE_COMMENT", "DIVIDE_MOD", "ARG_SPLITTER", "PLUS", "MINUS",
  "DIVIDE", "MULTIPLY", "ASSIGN", "MORE", "LESS", "MORE_OR_EQUAL",
  "LESS_OR_EQUAL", "EQUAL", "NOT_EQUAL", "SHARP", "OPEN_BRACKET",
  "CLOSE_BRACKET", "AND", "OR", "FKFLOAT", "PLUS_ASSIGN", "MINUS_ASSIGN",
  "DIVIDE_ASSIGN", "MULTIPLY_ASSIGN", "DIVIDE_MOD_ASSIGN", "COLON", "FOR",
  "INC", "FAKE", "FKUUID", "OPEN_SQUARE_BRACKET", "CLOSE_SQUARE_BRACKET",
  "FCONST", "PACKAGE", "INCLUDE", "IDENTIFIER_DOT", "IDENTIFIER_POINTER",
  "STRUCT", "IS", "NOT", "CONTINUE", "YIELD", "SLEEP", "SWITCH", "CASE",
  "DEFAULT", "NEW_ASSIGN", "ELSEIF", "RIGHT_POINTER", "STRING_CAT",
  "OPEN_BIG_BRACKET", "CLOSE_BIG_BRACKET", "NULL", "WAVE", "BIT_AND",
  "BIT_OR", "SHIFT_LEFT", "SHIFT_RIGHT", "DIVIDE_FLOOR",
  "DIVIDE_FLOOR_ASSIGN", "UNARY_MINUS", "UNARY_WAVE", "$accept", "program",
  "body", "function_declaration", "function_declaration_arguments", "arg",
  "function_call", "function_call_arguments", "arg_expr", "block", "stmt",
  "fake_call_stmt", "for_stmt", "for_loop_stmt", "while_stmt", "if_stmt",
  "elseif_stmt_list", "elseif_stmt", "else_stmt", "cmp", "cmp_value",
  "return_stmt", "return_value_list", "return_value", "assign_stmt",
  "multi_assign_stmt", "var_list", "assign_value", "math_assign_stmt",
  "var", "variable", "math_expr", "expr_value", "unary_expr", "expr",
  "break", "continue", "sleep", "yield", "switch_stmt", "switch_case_list",
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
    /** Token DO, to be returned by the scanner.  */
    static final int DO = 267;
    /** Token ELSE, to be returned by the scanner.  */
    static final int ELSE = 268;
    /** Token END, to be returned by the scanner.  */
    static final int END = 269;
    /** Token STRING_DEFINITION, to be returned by the scanner.  */
    static final int STRING_DEFINITION = 270;
    /** Token IDENTIFIER, to be returned by the scanner.  */
    static final int IDENTIFIER = 271;
    /** Token NUMBER, to be returned by the scanner.  */
    static final int NUMBER = 272;
    /** Token SINGLE_LINE_COMMENT, to be returned by the scanner.  */
    static final int SINGLE_LINE_COMMENT = 273;
    /** Token DIVIDE_MOD, to be returned by the scanner.  */
    static final int DIVIDE_MOD = 274;
    /** Token ARG_SPLITTER, to be returned by the scanner.  */
    static final int ARG_SPLITTER = 275;
    /** Token PLUS, to be returned by the scanner.  */
    static final int PLUS = 276;
    /** Token MINUS, to be returned by the scanner.  */
    static final int MINUS = 277;
    /** Token DIVIDE, to be returned by the scanner.  */
    static final int DIVIDE = 278;
    /** Token MULTIPLY, to be returned by the scanner.  */
    static final int MULTIPLY = 279;
    /** Token ASSIGN, to be returned by the scanner.  */
    static final int ASSIGN = 280;
    /** Token MORE, to be returned by the scanner.  */
    static final int MORE = 281;
    /** Token LESS, to be returned by the scanner.  */
    static final int LESS = 282;
    /** Token MORE_OR_EQUAL, to be returned by the scanner.  */
    static final int MORE_OR_EQUAL = 283;
    /** Token LESS_OR_EQUAL, to be returned by the scanner.  */
    static final int LESS_OR_EQUAL = 284;
    /** Token EQUAL, to be returned by the scanner.  */
    static final int EQUAL = 285;
    /** Token NOT_EQUAL, to be returned by the scanner.  */
    static final int NOT_EQUAL = 286;
    /** Token SHARP, to be returned by the scanner.  */
    static final int SHARP = 287;
    /** Token OPEN_BRACKET, to be returned by the scanner.  */
    static final int OPEN_BRACKET = 288;
    /** Token CLOSE_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_BRACKET = 289;
    /** Token AND, to be returned by the scanner.  */
    static final int AND = 290;
    /** Token OR, to be returned by the scanner.  */
    static final int OR = 291;
    /** Token FKFLOAT, to be returned by the scanner.  */
    static final int FKFLOAT = 292;
    /** Token PLUS_ASSIGN, to be returned by the scanner.  */
    static final int PLUS_ASSIGN = 293;
    /** Token MINUS_ASSIGN, to be returned by the scanner.  */
    static final int MINUS_ASSIGN = 294;
    /** Token DIVIDE_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_ASSIGN = 295;
    /** Token MULTIPLY_ASSIGN, to be returned by the scanner.  */
    static final int MULTIPLY_ASSIGN = 296;
    /** Token DIVIDE_MOD_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_MOD_ASSIGN = 297;
    /** Token COLON, to be returned by the scanner.  */
    static final int COLON = 298;
    /** Token FOR, to be returned by the scanner.  */
    static final int FOR = 299;
    /** Token INC, to be returned by the scanner.  */
    static final int INC = 300;
    /** Token FAKE, to be returned by the scanner.  */
    static final int FAKE = 301;
    /** Token FKUUID, to be returned by the scanner.  */
    static final int FKUUID = 302;
    /** Token OPEN_SQUARE_BRACKET, to be returned by the scanner.  */
    static final int OPEN_SQUARE_BRACKET = 303;
    /** Token CLOSE_SQUARE_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_SQUARE_BRACKET = 304;
    /** Token FCONST, to be returned by the scanner.  */
    static final int FCONST = 305;
    /** Token PACKAGE, to be returned by the scanner.  */
    static final int PACKAGE = 306;
    /** Token INCLUDE, to be returned by the scanner.  */
    static final int INCLUDE = 307;
    /** Token IDENTIFIER_DOT, to be returned by the scanner.  */
    static final int IDENTIFIER_DOT = 308;
    /** Token IDENTIFIER_POINTER, to be returned by the scanner.  */
    static final int IDENTIFIER_POINTER = 309;
    /** Token STRUCT, to be returned by the scanner.  */
    static final int STRUCT = 310;
    /** Token IS, to be returned by the scanner.  */
    static final int IS = 311;
    /** Token NOT, to be returned by the scanner.  */
    static final int NOT = 312;
    /** Token CONTINUE, to be returned by the scanner.  */
    static final int CONTINUE = 313;
    /** Token YIELD, to be returned by the scanner.  */
    static final int YIELD = 314;
    /** Token SLEEP, to be returned by the scanner.  */
    static final int SLEEP = 315;
    /** Token SWITCH, to be returned by the scanner.  */
    static final int SWITCH = 316;
    /** Token CASE, to be returned by the scanner.  */
    static final int CASE = 317;
    /** Token DEFAULT, to be returned by the scanner.  */
    static final int DEFAULT = 318;
    /** Token NEW_ASSIGN, to be returned by the scanner.  */
    static final int NEW_ASSIGN = 319;
    /** Token ELSEIF, to be returned by the scanner.  */
    static final int ELSEIF = 320;
    /** Token RIGHT_POINTER, to be returned by the scanner.  */
    static final int RIGHT_POINTER = 321;
    /** Token STRING_CAT, to be returned by the scanner.  */
    static final int STRING_CAT = 322;
    /** Token OPEN_BIG_BRACKET, to be returned by the scanner.  */
    static final int OPEN_BIG_BRACKET = 323;
    /** Token CLOSE_BIG_BRACKET, to be returned by the scanner.  */
    static final int CLOSE_BIG_BRACKET = 324;
    /** Token NULL, to be returned by the scanner.  */
    static final int NULL = 325;
    /** Token WAVE, to be returned by the scanner.  */
    static final int WAVE = 326;
    /** Token BIT_AND, to be returned by the scanner.  */
    static final int BIT_AND = 327;
    /** Token BIT_OR, to be returned by the scanner.  */
    static final int BIT_OR = 328;
    /** Token SHIFT_LEFT, to be returned by the scanner.  */
    static final int SHIFT_LEFT = 329;
    /** Token SHIFT_RIGHT, to be returned by the scanner.  */
    static final int SHIFT_RIGHT = 330;
    /** Token DIVIDE_FLOOR, to be returned by the scanner.  */
    static final int DIVIDE_FLOOR = 331;
    /** Token DIVIDE_FLOOR_ASSIGN, to be returned by the scanner.  */
    static final int DIVIDE_FLOOR_ASSIGN = 332;
    /** Token UNARY_MINUS, to be returned by the scanner.  */
    static final int UNARY_MINUS = 333;
    /** Token UNARY_WAVE, to be returned by the scanner.  */
    static final int UNARY_WAVE = 334;

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
    /* "YYParser.y":138  */
        {
	};
  break;


  case 6: /* function_declaration: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END  */
  if (yyn == 6)
    /* "YYParser.y":148  */
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
    /* "YYParser.y":159  */
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
    /* "YYParser.y":171  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
	};
  break;


  case 9: /* function_declaration_arguments: function_declaration_arguments ARG_SPLITTER arg  */
  if (yyn == 9)
    /* "YYParser.y":176  */
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
    /* "YYParser.y":187  */
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
    /* "YYParser.y":200  */
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
    /* "YYParser.y":214  */
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
    /* "YYParser.y":228  */
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
    /* "YYParser.y":242  */
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
    /* "YYParser.y":256  */
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
    /* "YYParser.y":275  */
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
    /* "YYParser.y":296  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
	};
  break;


  case 18: /* function_call_arguments: function_call_arguments ARG_SPLITTER arg_expr  */
  if (yyn == 18)
    /* "YYParser.y":301  */
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
    /* "YYParser.y":312  */
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
    /* "YYParser.y":325  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 21: /* block: block stmt  */
  if (yyn == 21)
    /* "YYParser.y":335  */
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
    /* "YYParser.y":346  */
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
    /* "YYParser.y":359  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: while_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 24: /* stmt: if_stmt  */
  if (yyn == 24)
    /* "YYParser.y":365  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: if_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 25: /* stmt: return_stmt  */
  if (yyn == 25)
    /* "YYParser.y":371  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 26: /* stmt: assign_stmt  */
  if (yyn == 26)
    /* "YYParser.y":377  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 27: /* stmt: multi_assign_stmt  */
  if (yyn == 27)
    /* "YYParser.y":383  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: multi_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 28: /* stmt: break  */
  if (yyn == 28)
    /* "YYParser.y":389  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: break");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 29: /* stmt: continue  */
  if (yyn == 29)
    /* "YYParser.y":395  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: continue");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 30: /* stmt: expr  */
  if (yyn == 30)
    /* "YYParser.y":401  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 31: /* stmt: math_assign_stmt  */
  if (yyn == 31)
    /* "YYParser.y":407  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 32: /* stmt: for_stmt  */
  if (yyn == 32)
    /* "YYParser.y":413  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 33: /* stmt: for_loop_stmt  */
  if (yyn == 33)
    /* "YYParser.y":419  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_loop_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 34: /* stmt: fake_call_stmt  */
  if (yyn == 34)
    /* "YYParser.y":425  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: fake_call_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 35: /* stmt: sleep  */
  if (yyn == 35)
    /* "YYParser.y":431  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: sleep_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 36: /* stmt: yield  */
  if (yyn == 36)
    /* "YYParser.y":437  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: yield_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 37: /* stmt: switch_stmt  */
  if (yyn == 37)
    /* "YYParser.y":443  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 38: /* fake_call_stmt: FAKE function_call  */
  if (yyn == 38)
    /* "YYParser.y":451  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FAKE function_call");
		function_call_node p = (function_call_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_fakecall = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 39: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO block END  */
  if (yyn == 39)
    /* "YYParser.y":467  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO block END");
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


  case 40: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO END  */
  if (yyn == 40)
    /* "YYParser.y":481  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO END");
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


  case 41: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO block END  */
  if (yyn == 41)
    /* "YYParser.y":499  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO block END");
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


  case 42: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO END  */
  if (yyn == 42)
    /* "YYParser.y":514  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO END");
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


  case 43: /* while_stmt: WHILE cmp DO block END  */
  if (yyn == 43)
    /* "YYParser.y":531  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp DO block END ");
		while_stmt p = ((Yylex)yylexer).new_node(while_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (3))).ival);
		p.m_cmp = (cmp_stmt)((ParserVal)yystack.valueAt (3)).obj;
		p.m_block = (block_node)((ParserVal)yystack.valueAt (1)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 44: /* while_stmt: WHILE cmp DO END  */
  if (yyn == 44)
    /* "YYParser.y":543  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: WHILE cmp DO END ");
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
    /* "YYParser.y":557  */
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
    /* "YYParser.y":571  */
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
    /* "YYParser.y":587  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 48: /* elseif_stmt_list: elseif_stmt_list elseif_stmt  */
  if (yyn == 48)
    /* "YYParser.y":596  */
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
    /* "YYParser.y":607  */
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
    /* "YYParser.y":620  */
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
    /* "YYParser.y":632  */
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
    /* "YYParser.y":646  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 53: /* else_stmt: ELSE block  */
  if (yyn == 53)
    /* "YYParser.y":655  */
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
    /* "YYParser.y":666  */
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
    /* "YYParser.y":679  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET cmp CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 56: /* cmp: cmp AND cmp  */
  if (yyn == 56)
    /* "YYParser.y":685  */
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
    /* "YYParser.y":698  */
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
    /* "YYParser.y":711  */
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
    /* "YYParser.y":724  */
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
    /* "YYParser.y":737  */
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
    /* "YYParser.y":750  */
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
    /* "YYParser.y":763  */
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
    /* "YYParser.y":776  */
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
    /* "YYParser.y":792  */
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
    /* "YYParser.y":805  */
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
    /* "YYParser.y":818  */
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
    /* "YYParser.y":836  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 68: /* return_stmt: RETURN return_value_list  */
  if (yyn == 68)
    /* "YYParser.y":844  */
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
    /* "YYParser.y":855  */
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
    /* "YYParser.y":868  */
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
    /* "YYParser.y":879  */
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
    /* "YYParser.y":892  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 73: /* return_value: variable  */
  if (yyn == 73)
    /* "YYParser.y":898  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 74: /* return_value: expr  */
  if (yyn == 74)
    /* "YYParser.y":904  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 75: /* assign_stmt: var ASSIGN assign_value  */
  if (yyn == 75)
    /* "YYParser.y":912  */
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
    /* "YYParser.y":925  */
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
    /* "YYParser.y":940  */
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
    /* "YYParser.y":953  */
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
    /* "YYParser.y":968  */
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
    /* "YYParser.y":979  */
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
    /* "YYParser.y":992  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 82: /* assign_value: variable  */
  if (yyn == 82)
    /* "YYParser.y":998  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 83: /* assign_value: expr  */
  if (yyn == 83)
    /* "YYParser.y":1004  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 84: /* math_assign_stmt: variable PLUS_ASSIGN assign_value  */
  if (yyn == 84)
    /* "YYParser.y":1012  */
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
    /* "YYParser.y":1025  */
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
    /* "YYParser.y":1038  */
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
    /* "YYParser.y":1051  */
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
    /* "YYParser.y":1064  */
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
    /* "YYParser.y":1077  */
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
    /* "YYParser.y":1090  */
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
    /* "YYParser.y":1109  */
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
    /* "YYParser.y":1120  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 93: /* variable: IDENTIFIER  */
  if (yyn == 93)
    /* "YYParser.y":1128  */
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
    /* "YYParser.y":1139  */
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
    /* "YYParser.y":1151  */
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
    /* "YYParser.y":1162  */
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
    /* "YYParser.y":1178  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET expr_value CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 98: /* math_expr: expr_value PLUS expr_value  */
  if (yyn == 98)
    /* "YYParser.y":1184  */
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
    /* "YYParser.y":1197  */
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
    /* "YYParser.y":1210  */
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
    /* "YYParser.y":1223  */
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
    /* "YYParser.y":1236  */
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
    /* "YYParser.y":1249  */
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
    /* "YYParser.y":1262  */
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
    /* "YYParser.y":1275  */
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
    /* "YYParser.y":1288  */
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
    /* "YYParser.y":1301  */
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
    /* "YYParser.y":1314  */
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
    /* "YYParser.y":1327  */
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
    /* "YYParser.y":1343  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 111: /* expr_value: unary_expr  */
  if (yyn == 111)
    /* "YYParser.y":1349  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 112: /* expr_value: explicit_value  */
  if (yyn == 112)
    /* "YYParser.y":1355  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 113: /* expr_value: function_call  */
  if (yyn == 113)
    /* "YYParser.y":1361  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 114: /* expr_value: variable  */
  if (yyn == 114)
    /* "YYParser.y":1367  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 115: /* unary_expr: UNARY_MINUS expr_value  */
  if (yyn == 115)
    /* "YYParser.y":1379  */
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
    /* "YYParser.y":1392  */
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


  case 117: /* unary_expr: SHARP expr_value  */
  if (yyn == 117)
    /* "YYParser.y":1411  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SHARP expr_value");
		int lno = ((ParserVal)((ParserVal)yystack.valueAt (0))).ival;

		function_call_arglist_node args = ((Yylex)yylexer).new_node(function_call_arglist_node.class, lno);
		args.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);

		function_call_node p = ((Yylex)yylexer).new_node(function_call_node.class, lno);
		p.m_fuc = "size";
		p.m_arglist = args;
		p.m_fakecall = false;
		p.m_classmem_call = false;

		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 118: /* expr: function_call  */
  if (yyn == 118)
    /* "YYParser.y":1435  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 119: /* expr: math_expr  */
  if (yyn == 119)
    /* "YYParser.y":1441  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 120: /* expr: unary_expr  */
  if (yyn == 120)
    /* "YYParser.y":1447  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 121: /* break: BREAK  */
  if (yyn == 121)
    /* "YYParser.y":1455  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: BREAK");
		break_stmt p = ((Yylex)yylexer).new_node(break_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 122: /* continue: CONTINUE  */
  if (yyn == 122)
    /* "YYParser.y":1467  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CONTINUE");
		continue_stmt p = ((Yylex)yylexer).new_node(continue_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 123: /* sleep: SLEEP expr_value  */
  if (yyn == 123)
    /* "YYParser.y":1479  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SLEEP");
		sleep_stmt p = ((Yylex)yylexer).new_node(sleep_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 124: /* yield: YIELD expr_value  */
  if (yyn == 124)
    /* "YYParser.y":1492  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: YIELD");
		yield_stmt p = ((Yylex)yylexer).new_node(yield_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 125: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT block END  */
  if (yyn == 125)
    /* "YYParser.y":1505  */
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


  case 126: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT END  */
  if (yyn == 126)
    /* "YYParser.y":1518  */
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


  case 127: /* switch_case_list: switch_case_define  */
  if (yyn == 127)
    /* "YYParser.y":1533  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_define");
		switch_caselist_node p = ((Yylex)yylexer).new_node(switch_caselist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 128: /* switch_case_list: switch_case_list switch_case_define  */
  if (yyn == 128)
    /* "YYParser.y":1544  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_list switch_case_define");
		switch_caselist_node p = (switch_caselist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 129: /* switch_case_define: CASE cmp_value THEN block  */
  if (yyn == 129)
    /* "YYParser.y":1557  */
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


  case 130: /* switch_case_define: CASE cmp_value THEN  */
  if (yyn == 130)
    /* "YYParser.y":1569  */
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


  case 131: /* package_head: %empty  */
  if (yyn == 131)
    /* "YYParser.y":1583  */
        {
	};
  break;


  case 132: /* package_head: PACKAGE IDENTIFIER  */
  if (yyn == 132)
    /* "YYParser.y":1587  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 133: /* package_head: PACKAGE IDENTIFIER_DOT  */
  if (yyn == 133)
    /* "YYParser.y":1593  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER_DOT ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 134: /* include_head: %empty  */
  if (yyn == 134)
    /* "YYParser.y":1601  */
        {
	};
  break;


  case 137: /* include_define: INCLUDE STRING_DEFINITION  */
  if (yyn == 137)
    /* "YYParser.y":1611  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: INCLUDE STRING_DEFINITION ");
		((Yylex)yylexer).get_mybison().add_include(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 138: /* struct_head: %empty  */
  if (yyn == 138)
    /* "YYParser.y":1619  */
        {
	};
  break;


  case 141: /* struct_define: STRUCT IDENTIFIER struct_mem_declaration END  */
  if (yyn == 141)
    /* "YYParser.y":1629  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRUCT IDENTIFIER struct_mem_declaration END ");
		((Yylex)yylexer).get_mybison().add_struct_desc(((ParserVal)yystack.valueAt (2)).sval);
	};
  break;


  case 142: /* struct_mem_declaration: struct_mem_declaration IDENTIFIER  */
  if (yyn == 142)
    /* "YYParser.y":1637  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: struct_mem_declaration IDENTIFIER ");
	};
  break;


  case 143: /* struct_mem_declaration: IDENTIFIER  */
  if (yyn == 143)
    /* "YYParser.y":1642  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER ");
	};
  break;


  case 144: /* const_head: %empty  */
  if (yyn == 144)
    /* "YYParser.y":1649  */
        {
	};
  break;


  case 147: /* const_define: FCONST IDENTIFIER ASSIGN explicit_value  */
  if (yyn == 147)
    /* "YYParser.y":1659  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FCONST IDENTIFIER ASSIGN explicit_value ");
		((Yylex)yylexer).get_mybison().add_const_desc(((ParserVal)yystack.valueAt (2)).sval, (syntree_node)((ParserVal)yystack.valueAt (0)).obj);
	};
  break;


  case 148: /* explicit_value: NULL  */
  if (yyn == 148)
    /* "YYParser.y":1667  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NULL ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_NULL;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 149: /* explicit_value: FTRUE  */
  if (yyn == 149)
    /* "YYParser.y":1678  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_TRUE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 150: /* explicit_value: FFALSE  */
  if (yyn == 150)
    /* "YYParser.y":1689  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_FALSE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 151: /* explicit_value: NUMBER  */
  if (yyn == 151)
    /* "YYParser.y":1700  */
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


  case 152: /* explicit_value: FKUUID  */
  if (yyn == 152)
    /* "YYParser.y":1712  */
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


  case 153: /* explicit_value: STRING_DEFINITION  */
  if (yyn == 153)
    /* "YYParser.y":1724  */
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


  case 154: /* explicit_value: FKFLOAT  */
  if (yyn == 154)
    /* "YYParser.y":1736  */
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


  case 155: /* explicit_value: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET  */
  if (yyn == 155)
    /* "YYParser.y":1748  */
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


  case 156: /* explicit_value: OPEN_SQUARE_BRACKET const_array_list_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 156)
    /* "YYParser.y":1760  */
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


  case 157: /* const_map_list_value: %empty  */
  if (yyn == 157)
    /* "YYParser.y":1775  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 158: /* const_map_list_value: const_map_value  */
  if (yyn == 158)
    /* "YYParser.y":1785  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_value ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 159: /* const_map_list_value: const_map_list_value const_map_value  */
  if (yyn == 159)
    /* "YYParser.y":1796  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_list_value const_map_value ");
		const_map_list_value_node p = (const_map_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 160: /* const_map_value: explicit_value COLON explicit_value  */
  if (yyn == 160)
    /* "YYParser.y":1810  */
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


  case 161: /* const_array_list_value: %empty  */
  if (yyn == 161)
    /* "YYParser.y":1824  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 162: /* const_array_list_value: explicit_value  */
  if (yyn == 162)
    /* "YYParser.y":1834  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 163: /* const_array_list_value: const_array_list_value explicit_value  */
  if (yyn == 163)
    /* "YYParser.y":1845  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_array_list_value explicit_value ");
		const_array_list_value_node p = (const_array_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;



/* "/Users/mingming/project/fakescript_java/src/main/java/com/github/esrrhs/fakescript/YYParser.java":2982  */

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

  private static final short yypact_ninf_ = -142;
  private static final short yytable_ninf_ = -115;

/* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
   STATE-NUM.  */
  private static final short[] yypact_ = yypact_init();
  private static final short[] yypact_init()
  {
    return new short[]
    {
     -43,    20,    39,   -32,  -142,  -142,  -142,    37,    91,  -142,
    -142,    49,  -142,   -36,  -142,    77,    89,  -142,     1,  -142,
    -142,    47,    72,    99,   120,  -142,  -142,  -142,  -142,     7,
      85,  -142,  -142,  -142,  -142,  -142,  -142,  -142,     7,     7,
    -142,  -142,   123,  -142,   142,   101,   578,  -142,  -142,    -2,
    -142,  -142,  -142,     7,  -142,  -142,   123,   597,  -142,  -142,
     139,  1602,  -142,   475,   475,  -142,    -6,  1602,  1602,  1599,
      13,   144,  -142,  -142,  1602,  1602,  1602,  1602,  1602,  1652,
     675,  -142,  -142,  -142,  -142,  -142,  -142,  -142,  -142,  -142,
       8,  -142,   -16,   277,  1745,  1755,  1766,  -142,  -142,  -142,
    -142,  -142,  -142,  -142,  -142,   152,  -142,  1665,  -142,  1776,
     475,  1602,  1602,    63,     5,   395,   137,  -142,  1755,  -142,
      -1,  1602,  1602,  -142,  1677,   752,     6,    63,   137,  1602,
    1755,  1755,   119,  -142,  -142,  1602,   167,  -142,  -142,    27,
      13,    13,  1602,  1602,  1602,  1602,  1602,  1602,  1602,   169,
    -142,  1602,  1602,  1602,  1602,  1602,  1602,  1602,  1602,  1602,
    1602,  1602,  1602,  1602,  1602,   113,  1677,  -142,  -142,   829,
     475,   475,  1602,  1602,  1602,  1602,  1602,  1602,   906,    40,
    -142,  1755,  1735,  -142,   475,  1602,    88,  1602,   108,  -142,
     107,   153,   146,  -142,  -142,  -142,    63,    63,  -142,  1665,
    -142,  1776,  -142,  -142,  -142,  -142,  -142,  -142,   154,  -142,
    -142,     2,     2,  -142,  -142,   674,   474,   650,  1834,   410,
     674,  -142,  -142,  -142,  -142,   983,   140,   140,  -142,  -142,
    -142,  -142,  -142,  -142,   475,   906,   -12,  -142,  1602,  -142,
    -142,    84,   129,  -142,   187,  1060,  -142,  -142,  1602,  1602,
    -142,    81,   -12,  1599,  -142,   185,  -142,  1599,  1602,  1599,
    -142,  1137,   118,   122,  1599,   192,  1599,  -142,  1214,   181,
    1599,  -142,  -142,  -142,  1599,  -142,  1291,  1602,  -142,  1368,
    1642,  -142,  1445,  -142,  1522,  -142
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
     131,     0,     0,   134,   132,   133,     1,     0,   138,   135,
     137,     0,   136,   144,   139,     0,     0,   140,     3,   145,
     143,     0,     0,     0,     2,     4,   146,   141,   142,     0,
       0,     5,   149,   150,   153,   151,   154,   152,   161,   157,
     148,   147,     8,   162,     0,     0,     0,   158,    11,     0,
      10,   156,   163,     0,   155,   159,     0,     0,   160,     9,
       0,    69,   121,     0,     0,     7,    93,     0,     0,     0,
       0,    96,    95,   122,     0,     0,     0,     0,     0,   118,
       0,    22,    34,    32,    33,    23,    24,    25,    26,    27,
       0,    31,    80,   114,   119,     0,   120,    30,    28,    29,
      35,    36,    37,   112,    91,    68,    71,    73,    74,    72,
       0,     0,     0,   113,     0,    64,   114,   110,    67,   111,
       0,    17,     0,   117,     0,     0,    80,    38,     0,    17,
     124,   123,     0,   115,   116,    17,     0,     6,    21,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      90,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    67,    65,    66,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    47,     0,
      19,    20,     0,    97,     0,     0,     0,     0,     0,   127,
       0,     0,    93,    96,    79,    92,    77,    78,    75,    82,
      83,    81,    76,    84,    85,    86,    88,    89,     0,    87,
     102,    98,    99,   101,   100,   109,   106,   104,   105,   107,
     108,   103,    70,    55,    44,     0,    56,    57,    59,    58,
      61,    62,    60,    63,     0,    47,    52,    49,     0,    12,
      94,     0,    75,    13,     0,     0,   128,    14,    17,    17,
      43,     0,    52,    54,    48,     0,    18,     0,     0,   130,
     126,     0,     0,     0,    51,     0,    53,    46,     0,     0,
     129,   125,    16,    15,    50,    45,     0,     0,    40,     0,
       0,    39,     0,    42,     0,    41
    };
  }

/* YYPGOTO[NTERM-NUM].  */
  private static final short[] yypgoto_ = yypgoto_init();
  private static final short[] yypgoto_init()
  {
    return new short[]
    {
    -142,  -142,  -142,   191,  -142,   162,   -11,  -124,   -18,   -24,
     112,  -142,  -142,  -142,  -142,  -142,    -9,  -141,   -29,   -61,
     -74,  -142,  -142,    64,  -142,  -142,  -142,   476,  -142,   -63,
     -57,   136,   404,   182,   147,  -142,  -142,  -142,  -142,  -142,
    -142,    42,  -142,  -142,   223,  -142,   219,  -142,  -142,   218,
      18,  -142,   195,  -142
    };
  }

/* YYDEFGOTO[NTERM-NUM].  */
  private static final short[] yydefgoto_ = yydefgoto_init();
  private static final short[] yydefgoto_init()
  {
    return new short[]
    {
       0,     2,    24,    25,    49,    50,   113,   179,   180,    80,
      81,    82,    83,    84,    85,    86,   236,   237,   255,   114,
     115,    87,   105,   106,    88,    89,    90,   198,    91,    92,
     116,   117,    95,   119,    97,    98,    99,   100,   101,   102,
     188,   189,     3,     8,     9,    13,    14,    21,    18,    19,
     103,    46,    47,    44
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
      93,   253,   132,   120,   107,   186,   126,    23,     1,   142,
     178,   190,    93,   128,    16,    32,    33,   169,    56,    11,
       7,   152,    34,    93,    35,   155,   156,   121,   139,    66,
      60,   185,    57,   140,   170,   171,     4,   167,   168,     6,
     170,   171,   122,   192,    36,   125,    79,    41,   143,   165,
      79,    16,    10,   234,    37,    38,    43,    45,    79,   127,
     238,    27,    52,    28,    45,    15,    71,    72,    93,    79,
     143,    58,   141,     5,   239,    39,   194,    40,   163,   109,
     193,    72,   195,   128,   128,   199,   199,   199,   199,   199,
     199,   199,   264,    20,   199,   254,   135,    29,   228,   229,
     230,   231,   232,   233,   257,    22,   136,   107,   238,   226,
     227,   254,    93,   244,    79,    30,   170,   171,    42,   170,
     171,    93,   243,   241,   262,   263,    23,   238,   199,   196,
     197,    79,    79,    79,    79,    79,    79,    79,   238,    48,
      79,   247,   238,     7,    53,   225,    11,   223,   170,   171,
      32,    33,   272,    79,   235,   104,   273,    34,    79,    35,
     201,   201,   201,   201,   201,   201,   201,    79,    93,   201,
     187,   245,   164,   251,    79,   170,   171,   129,    93,    36,
     149,   187,   109,   191,   269,   208,   248,   249,    93,    37,
      38,    51,   138,    94,   122,   258,    93,    94,   259,   267,
      93,   277,    93,   201,    93,    94,   275,    93,   108,    93,
      39,    93,    40,    93,    79,    31,    94,    93,    59,    93,
     256,   261,    93,   265,    79,    93,   252,    93,   222,   266,
     246,    12,    17,   268,    79,   270,    26,   138,     0,    96,
     274,    55,    79,    96,     0,     0,    79,     0,    79,     0,
      79,    96,   279,    79,     0,    79,     0,    79,   284,    79,
       0,    94,    96,    79,     0,    79,     0,     0,    79,     0,
       0,    79,     0,    79,     0,     0,     0,     0,    94,    94,
      94,    94,    94,    94,    94,     0,     0,    94,     0,   200,
     200,   200,   200,   200,   200,   200,     0,   -92,   200,     0,
      94,     0,   -92,     0,     0,    94,     0,    96,     0,     0,
       0,   108,     0,     0,    94,   144,   145,   146,   147,   148,
     149,    94,   150,     0,    96,    96,    96,    96,    96,    96,
      96,     0,   200,    96,     0,     0,     0,   138,     0,     0,
       0,   -92,     0,     0,     0,     0,    96,   138,     0,     0,
       0,    96,     0,     0,   151,     0,     0,     0,     0,     0,
      96,    94,     0,     0,     0,     0,     0,    96,     0,     0,
       0,    94,     0,   138,     0,     0,     0,     0,   138,     0,
     138,    94,   138,     0,     0,     0,   138,     0,     0,    94,
       0,   138,     0,    94,     0,    94,   138,    94,     0,     0,
      94,     0,    94,     0,    94,     0,    94,    96,     0,     0,
      94,     0,    94,     0,     0,    94,     0,    96,    94,     0,
      94,   172,   173,   174,   175,   176,   177,    96,     0,   152,
       0,   153,   154,   155,   156,    96,     0,     0,     0,    96,
       0,    96,     0,    96,     0,     0,    96,     0,    96,     0,
      96,     0,    96,     0,     0,     0,    96,     0,    96,     0,
       0,    96,     0,     0,    96,     0,    96,   118,   118,     0,
       0,   123,   124,     0,     0,     0,     0,   157,   130,   131,
     118,   133,   134,    32,    33,   162,   163,     0,     0,     0,
      34,    66,    35,   152,     0,   153,   154,   155,   156,     0,
       0,     0,     0,     0,     0,     0,     0,    67,   110,     0,
       0,     0,    36,     0,   166,   118,   118,     0,     0,     0,
       0,     0,    37,    38,     0,   181,   182,     0,    71,    72,
       0,   111,   112,   181,     0,     0,     0,     0,     0,   181,
       0,   157,     0,    39,     0,    40,   159,     0,   161,   162,
     163,     0,     0,    77,    78,     0,   210,   211,   212,   213,
     214,   215,   216,   217,   218,   219,   220,   221,     0,     0,
       0,     0,     0,     0,   118,   118,   118,   118,   118,   118,
     118,   118,     0,     0,     0,     0,    32,    33,   118,     0,
       0,   118,     0,    34,     0,    35,     0,     0,     0,     0,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
       0,    65,    34,    66,    35,    36,     0,     0,     0,   202,
     203,   204,   205,   206,   207,    37,    38,   209,     0,    67,
      68,     0,     0,     0,    36,     0,     0,     0,   118,     0,
       0,    69,   181,    70,    37,    38,    39,    54,    40,     0,
      71,    72,   181,   181,     0,    73,    74,    75,    76,     0,
       0,   242,   118,     0,     0,    39,     0,    40,     0,   152,
       0,   153,   154,   155,   156,    77,    78,     0,    60,    61,
      62,   280,    63,    32,    33,    64,     0,     0,     0,   137,
      34,    66,    35,   152,     0,   153,   154,   155,   156,     0,
       0,     0,     0,     0,     0,     0,     0,    67,    68,     0,
       0,     0,    36,     0,     0,     0,     0,   157,     0,    69,
       0,    70,    37,    38,   161,   162,   163,     0,    71,    72,
       0,     0,     0,    73,    74,    75,    76,     0,     0,     0,
       0,   157,     0,    39,     0,    40,     0,     0,     0,     0,
     163,     0,     0,    77,    78,    60,    61,    62,     0,    63,
      32,    33,    64,     0,     0,     0,     0,    34,    66,    35,
       0,     0,   184,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    67,    68,     0,     0,     0,    36,
       0,     0,     0,     0,     0,     0,    69,     0,    70,    37,
      38,     0,     0,     0,     0,    71,    72,     0,     0,     0,
      73,    74,    75,    76,     0,     0,     0,     0,     0,     0,
      39,     0,    40,     0,     0,     0,     0,     0,     0,     0,
      77,    78,    60,    61,    62,     0,    63,    32,    33,    64,
       0,     0,     0,   224,    34,    66,    35,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,    67,    68,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    69,     0,    70,    37,    38,     0,     0,
       0,     0,    71,    72,     0,     0,     0,    73,    74,    75,
      76,     0,     0,     0,     0,     0,     0,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    77,    78,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,     0,
       0,    34,    66,    35,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    67,    68,
       0,     0,     0,    36,     0,     0,     0,     0,     0,     0,
      69,     0,    70,    37,    38,     0,     0,     0,     0,    71,
      72,     0,     0,     0,    73,    74,    75,    76,     0,     0,
       0,   234,     0,     0,    39,     0,    40,     0,     0,     0,
       0,     0,     0,     0,    77,    78,    60,    61,    62,     0,
      63,    32,    33,    64,     0,     0,     0,   250,    34,    66,
      35,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,    67,    68,     0,     0,     0,
      36,     0,     0,     0,     0,     0,     0,    69,     0,    70,
      37,    38,     0,     0,     0,     0,    71,    72,     0,     0,
       0,    73,    74,    75,    76,     0,     0,     0,     0,     0,
       0,    39,     0,    40,     0,     0,     0,     0,     0,     0,
       0,    77,    78,    60,    61,    62,     0,    63,    32,    33,
      64,     0,     0,     0,   260,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,    68,     0,     0,     0,    36,     0,     0,
       0,     0,     0,     0,    69,     0,    70,    37,    38,     0,
       0,     0,     0,    71,    72,     0,     0,     0,    73,    74,
      75,    76,     0,     0,     0,     0,     0,     0,    39,     0,
      40,     0,     0,     0,     0,     0,     0,     0,    77,    78,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
       0,   271,    34,    66,    35,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    67,
      68,     0,     0,     0,    36,     0,     0,     0,     0,     0,
       0,    69,     0,    70,    37,    38,     0,     0,     0,     0,
      71,    72,     0,     0,     0,    73,    74,    75,    76,     0,
       0,     0,     0,     0,     0,    39,     0,    40,     0,     0,
       0,     0,     0,     0,     0,    77,    78,    60,    61,    62,
       0,    63,    32,    33,    64,     0,   276,     0,     0,    34,
      66,    35,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    67,    68,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    69,     0,
      70,    37,    38,     0,     0,     0,     0,    71,    72,     0,
       0,     0,    73,    74,    75,    76,     0,     0,     0,     0,
       0,     0,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    77,    78,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,     0,   278,    34,    66,    35,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    67,    68,     0,     0,     0,    36,     0,
       0,     0,     0,     0,     0,    69,     0,    70,    37,    38,
       0,     0,     0,     0,    71,    72,     0,     0,     0,    73,
      74,    75,    76,     0,     0,     0,     0,     0,     0,    39,
       0,    40,     0,     0,     0,     0,     0,     0,     0,    77,
      78,    60,    61,    62,     0,    63,    32,    33,    64,     0,
       0,     0,   281,    34,    66,    35,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      67,    68,     0,     0,     0,    36,     0,     0,     0,     0,
       0,     0,    69,     0,    70,    37,    38,     0,     0,     0,
       0,    71,    72,     0,     0,     0,    73,    74,    75,    76,
       0,     0,     0,     0,     0,     0,    39,     0,    40,     0,
       0,     0,     0,     0,     0,     0,    77,    78,    60,    61,
      62,     0,    63,    32,    33,    64,     0,     0,     0,   283,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,    68,     0,
       0,     0,    36,     0,     0,     0,     0,     0,     0,    69,
       0,    70,    37,    38,     0,     0,     0,     0,    71,    72,
       0,     0,     0,    73,    74,    75,    76,     0,     0,     0,
       0,     0,     0,    39,     0,    40,     0,     0,     0,     0,
       0,     0,     0,    77,    78,    60,    61,    62,     0,    63,
      32,    33,    64,     0,     0,     0,   285,    34,    66,    35,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    67,    68,     0,     0,     0,    36,
       0,     0,     0,     0,     0,     0,    69,     0,    70,    37,
      38,     0,     0,     0,     0,    71,    72,     0,     0,     0,
      73,    74,    75,    76,     0,     0,     0,     0,     0,     0,
      39,     0,    40,     0,     0,     0,     0,     0,     0,     0,
      77,    78,    60,    61,    62,     0,    63,    32,    33,    64,
      32,    33,     0,     0,    34,    66,    35,    34,    66,    35,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,    67,    68,     0,    67,    68,    36,     0,     0,    36,
       0,     0,     0,    69,     0,    70,    37,    38,     0,    37,
      38,     0,    71,    72,   282,    71,    72,    73,    74,    75,
      76,   152,     0,   153,   154,   155,   156,    39,     0,    40,
      39,  -113,    40,  -113,  -113,  -113,  -113,    77,    78,     0,
      77,    78,     0,     0,  -114,   135,  -114,  -114,  -114,  -114,
       0,     0,     0,     0,     0,   136,   152,     0,   153,   154,
     155,   156,     0,     0,     0,     0,     0,     0,   149,   157,
       0,   183,     0,   158,   159,   160,   161,   162,   163,  -113,
       0,     0,     0,  -113,  -113,  -113,  -113,  -113,  -113,     0,
       0,     0,  -114,     0,     0,     0,  -114,  -114,  -114,  -114,
    -114,  -114,     0,     0,   157,     0,     0,     0,   158,   159,
     160,   161,   162,   163,   152,     0,   153,   154,   155,   156,
       0,     0,     0,     0,  -110,     0,  -110,  -110,  -110,  -110,
       0,     0,     0,     0,   152,     0,   153,   154,   155,   156,
       0,     0,     0,     0,   240,  -111,     0,  -111,  -111,  -111,
    -111,     0,     0,     0,     0,  -112,     0,  -112,  -112,  -112,
    -112,     0,   157,     0,     0,     0,   158,   159,   160,   161,
     162,   163,  -110,     0,     0,     0,  -110,  -110,  -110,  -110,
    -110,  -110,   157,     0,     0,     0,   158,   159,   160,   161,
     162,   163,     0,  -111,     0,     0,     0,  -111,  -111,  -111,
    -111,  -111,  -111,  -112,     0,     0,     0,  -112,  -112,  -112,
    -112,  -112,  -112,   152,     0,   153,   154,   155,   156,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,   157,     0,     0,     0,   158,   159,     0,   161,   162,
     163
    };
  }

private static final short[] yycheck_ = yycheck_init();
  private static final short[] yycheck_init()
  {
    return new short[]
    {
      57,    13,    76,    64,    61,   129,    69,     6,    51,    25,
      11,   135,    69,    70,    50,     8,     9,    12,    20,    55,
      52,    19,    15,    80,    17,    23,    24,    33,    20,    16,
       3,    25,    34,    25,    35,    36,    16,   111,   112,     0,
      35,    36,    48,    16,    37,    69,    57,    29,    64,   110,
      61,    50,    15,    65,    47,    48,    38,    39,    69,    70,
      20,    14,    44,    16,    46,    16,    53,    54,   125,    80,
      64,    53,    64,    53,    34,    68,   139,    70,    76,    61,
      53,    54,   139,   140,   141,   142,   143,   144,   145,   146,
     147,   148,    11,    16,   151,   236,    33,    25,   172,   173,
     174,   175,   176,   177,    20,    16,    43,   164,    20,   170,
     171,   252,   169,   187,   125,    16,    35,    36,    33,    35,
      36,   178,    34,   184,   248,   249,     6,    20,   185,   140,
     141,   142,   143,   144,   145,   146,   147,   148,    20,    16,
     151,    34,    20,    52,    43,   169,    55,    34,    35,    36,
       8,     9,    34,   164,   178,    16,    34,    15,   169,    17,
     142,   143,   144,   145,   146,   147,   148,   178,   225,   151,
      62,    63,    20,   234,   185,    35,    36,    33,   235,    37,
      43,    62,   164,    16,   258,    16,    33,    33,   245,    47,
      48,    49,    80,    57,    48,    66,   253,    61,    11,    14,
     257,    20,   259,   185,   261,    69,    14,   264,    61,   266,
      68,   268,    70,   270,   225,    24,    80,   274,    56,   276,
     238,   245,   279,   252,   235,   282,   235,   284,   164,   253,
     188,     8,    13,   257,   245,   259,    18,   125,    -1,    57,
     264,    46,   253,    61,    -1,    -1,   257,    -1,   259,    -1,
     261,    69,   276,   264,    -1,   266,    -1,   268,   282,   270,
      -1,   125,    80,   274,    -1,   276,    -1,    -1,   279,    -1,
      -1,   282,    -1,   284,    -1,    -1,    -1,    -1,   142,   143,
     144,   145,   146,   147,   148,    -1,    -1,   151,    -1,   142,
     143,   144,   145,   146,   147,   148,    -1,    20,   151,    -1,
     164,    -1,    25,    -1,    -1,   169,    -1,   125,    -1,    -1,
      -1,   164,    -1,    -1,   178,    38,    39,    40,    41,    42,
      43,   185,    45,    -1,   142,   143,   144,   145,   146,   147,
     148,    -1,   185,   151,    -1,    -1,    -1,   225,    -1,    -1,
      -1,    64,    -1,    -1,    -1,    -1,   164,   235,    -1,    -1,
      -1,   169,    -1,    -1,    77,    -1,    -1,    -1,    -1,    -1,
     178,   225,    -1,    -1,    -1,    -1,    -1,   185,    -1,    -1,
      -1,   235,    -1,   261,    -1,    -1,    -1,    -1,   266,    -1,
     268,   245,   270,    -1,    -1,    -1,   274,    -1,    -1,   253,
      -1,   279,    -1,   257,    -1,   259,   284,   261,    -1,    -1,
     264,    -1,   266,    -1,   268,    -1,   270,   225,    -1,    -1,
     274,    -1,   276,    -1,    -1,   279,    -1,   235,   282,    -1,
     284,    26,    27,    28,    29,    30,    31,   245,    -1,    19,
      -1,    21,    22,    23,    24,   253,    -1,    -1,    -1,   257,
      -1,   259,    -1,   261,    -1,    -1,   264,    -1,   266,    -1,
     268,    -1,   270,    -1,    -1,    -1,   274,    -1,   276,    -1,
      -1,   279,    -1,    -1,   282,    -1,   284,    63,    64,    -1,
      -1,    67,    68,    -1,    -1,    -1,    -1,    67,    74,    75,
      76,    77,    78,     8,     9,    75,    76,    -1,    -1,    -1,
      15,    16,    17,    19,    -1,    21,    22,    23,    24,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,    33,    -1,
      -1,    -1,    37,    -1,   110,   111,   112,    -1,    -1,    -1,
      -1,    -1,    47,    48,    -1,   121,   122,    -1,    53,    54,
      -1,    56,    57,   129,    -1,    -1,    -1,    -1,    -1,   135,
      -1,    67,    -1,    68,    -1,    70,    72,    -1,    74,    75,
      76,    -1,    -1,    78,    79,    -1,   152,   153,   154,   155,
     156,   157,   158,   159,   160,   161,   162,   163,    -1,    -1,
      -1,    -1,    -1,    -1,   170,   171,   172,   173,   174,   175,
     176,   177,    -1,    -1,    -1,    -1,     8,     9,   184,    -1,
      -1,   187,    -1,    15,    -1,    17,    -1,    -1,    -1,    -1,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      -1,    14,    15,    16,    17,    37,    -1,    -1,    -1,   143,
     144,   145,   146,   147,   148,    47,    48,   151,    -1,    32,
      33,    -1,    -1,    -1,    37,    -1,    -1,    -1,   234,    -1,
      -1,    44,   238,    46,    47,    48,    68,    69,    70,    -1,
      53,    54,   248,   249,    -1,    58,    59,    60,    61,    -1,
      -1,   185,   258,    -1,    -1,    68,    -1,    70,    -1,    19,
      -1,    21,    22,    23,    24,    78,    79,    -1,     3,     4,
       5,   277,     7,     8,     9,    10,    -1,    -1,    -1,    14,
      15,    16,    17,    19,    -1,    21,    22,    23,    24,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,    33,    -1,
      -1,    -1,    37,    -1,    -1,    -1,    -1,    67,    -1,    44,
      -1,    46,    47,    48,    74,    75,    76,    -1,    53,    54,
      -1,    -1,    -1,    58,    59,    60,    61,    -1,    -1,    -1,
      -1,    67,    -1,    68,    -1,    70,    -1,    -1,    -1,    -1,
      76,    -1,    -1,    78,    79,     3,     4,     5,    -1,     7,
       8,     9,    10,    -1,    -1,    -1,    -1,    15,    16,    17,
      -1,    -1,    20,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    32,    33,    -1,    -1,    -1,    37,
      -1,    -1,    -1,    -1,    -1,    -1,    44,    -1,    46,    47,
      48,    -1,    -1,    -1,    -1,    53,    54,    -1,    -1,    -1,
      58,    59,    60,    61,    -1,    -1,    -1,    -1,    -1,    -1,
      68,    -1,    70,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      78,    79,     3,     4,     5,    -1,     7,     8,     9,    10,
      -1,    -1,    -1,    14,    15,    16,    17,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    32,    33,    -1,    -1,    -1,    37,    -1,    -1,    -1,
      -1,    -1,    -1,    44,    -1,    46,    47,    48,    -1,    -1,
      -1,    -1,    53,    54,    -1,    -1,    -1,    58,    59,    60,
      61,    -1,    -1,    -1,    -1,    -1,    -1,    68,    -1,    70,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    78,    79,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    -1,
      -1,    15,    16,    17,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,    33,
      -1,    -1,    -1,    37,    -1,    -1,    -1,    -1,    -1,    -1,
      44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,    53,
      54,    -1,    -1,    -1,    58,    59,    60,    61,    -1,    -1,
      -1,    65,    -1,    -1,    68,    -1,    70,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    78,    79,     3,     4,     5,    -1,
       7,     8,     9,    10,    -1,    -1,    -1,    14,    15,    16,
      17,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    32,    33,    -1,    -1,    -1,
      37,    -1,    -1,    -1,    -1,    -1,    -1,    44,    -1,    46,
      47,    48,    -1,    -1,    -1,    -1,    53,    54,    -1,    -1,
      -1,    58,    59,    60,    61,    -1,    -1,    -1,    -1,    -1,
      -1,    68,    -1,    70,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    78,    79,     3,     4,     5,    -1,     7,     8,     9,
      10,    -1,    -1,    -1,    14,    15,    16,    17,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    32,    33,    -1,    -1,    -1,    37,    -1,    -1,
      -1,    -1,    -1,    -1,    44,    -1,    46,    47,    48,    -1,
      -1,    -1,    -1,    53,    54,    -1,    -1,    -1,    58,    59,
      60,    61,    -1,    -1,    -1,    -1,    -1,    -1,    68,    -1,
      70,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    78,    79,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      -1,    14,    15,    16,    17,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,
      33,    -1,    -1,    -1,    37,    -1,    -1,    -1,    -1,    -1,
      -1,    44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,
      53,    54,    -1,    -1,    -1,    58,    59,    60,    61,    -1,
      -1,    -1,    -1,    -1,    -1,    68,    -1,    70,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    78,    79,     3,     4,     5,
      -1,     7,     8,     9,    10,    -1,    12,    -1,    -1,    15,
      16,    17,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    32,    33,    -1,    -1,
      -1,    37,    -1,    -1,    -1,    -1,    -1,    -1,    44,    -1,
      46,    47,    48,    -1,    -1,    -1,    -1,    53,    54,    -1,
      -1,    -1,    58,    59,    60,    61,    -1,    -1,    -1,    -1,
      -1,    -1,    68,    -1,    70,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    78,    79,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    -1,    14,    15,    16,    17,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    32,    33,    -1,    -1,    -1,    37,    -1,
      -1,    -1,    -1,    -1,    -1,    44,    -1,    46,    47,    48,
      -1,    -1,    -1,    -1,    53,    54,    -1,    -1,    -1,    58,
      59,    60,    61,    -1,    -1,    -1,    -1,    -1,    -1,    68,
      -1,    70,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    78,
      79,     3,     4,     5,    -1,     7,     8,     9,    10,    -1,
      -1,    -1,    14,    15,    16,    17,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      32,    33,    -1,    -1,    -1,    37,    -1,    -1,    -1,    -1,
      -1,    -1,    44,    -1,    46,    47,    48,    -1,    -1,    -1,
      -1,    53,    54,    -1,    -1,    -1,    58,    59,    60,    61,
      -1,    -1,    -1,    -1,    -1,    -1,    68,    -1,    70,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    78,    79,     3,     4,
       5,    -1,     7,     8,     9,    10,    -1,    -1,    -1,    14,
      15,    16,    17,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,    33,    -1,
      -1,    -1,    37,    -1,    -1,    -1,    -1,    -1,    -1,    44,
      -1,    46,    47,    48,    -1,    -1,    -1,    -1,    53,    54,
      -1,    -1,    -1,    58,    59,    60,    61,    -1,    -1,    -1,
      -1,    -1,    -1,    68,    -1,    70,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    78,    79,     3,     4,     5,    -1,     7,
       8,     9,    10,    -1,    -1,    -1,    14,    15,    16,    17,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    32,    33,    -1,    -1,    -1,    37,
      -1,    -1,    -1,    -1,    -1,    -1,    44,    -1,    46,    47,
      48,    -1,    -1,    -1,    -1,    53,    54,    -1,    -1,    -1,
      58,    59,    60,    61,    -1,    -1,    -1,    -1,    -1,    -1,
      68,    -1,    70,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      78,    79,     3,     4,     5,    -1,     7,     8,     9,    10,
       8,     9,    -1,    -1,    15,    16,    17,    15,    16,    17,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    32,    33,    -1,    32,    33,    37,    -1,    -1,    37,
      -1,    -1,    -1,    44,    -1,    46,    47,    48,    -1,    47,
      48,    -1,    53,    54,    12,    53,    54,    58,    59,    60,
      61,    19,    -1,    21,    22,    23,    24,    68,    -1,    70,
      68,    19,    70,    21,    22,    23,    24,    78,    79,    -1,
      78,    79,    -1,    -1,    19,    33,    21,    22,    23,    24,
      -1,    -1,    -1,    -1,    -1,    43,    19,    -1,    21,    22,
      23,    24,    -1,    -1,    -1,    -1,    -1,    -1,    43,    67,
      -1,    34,    -1,    71,    72,    73,    74,    75,    76,    67,
      -1,    -1,    -1,    71,    72,    73,    74,    75,    76,    -1,
      -1,    -1,    67,    -1,    -1,    -1,    71,    72,    73,    74,
      75,    76,    -1,    -1,    67,    -1,    -1,    -1,    71,    72,
      73,    74,    75,    76,    19,    -1,    21,    22,    23,    24,
      -1,    -1,    -1,    -1,    19,    -1,    21,    22,    23,    24,
      -1,    -1,    -1,    -1,    19,    -1,    21,    22,    23,    24,
      -1,    -1,    -1,    -1,    49,    19,    -1,    21,    22,    23,
      24,    -1,    -1,    -1,    -1,    19,    -1,    21,    22,    23,
      24,    -1,    67,    -1,    -1,    -1,    71,    72,    73,    74,
      75,    76,    67,    -1,    -1,    -1,    71,    72,    73,    74,
      75,    76,    67,    -1,    -1,    -1,    71,    72,    73,    74,
      75,    76,    -1,    67,    -1,    -1,    -1,    71,    72,    73,
      74,    75,    76,    67,    -1,    -1,    -1,    71,    72,    73,
      74,    75,    76,    19,    -1,    21,    22,    23,    24,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    67,    -1,    -1,    -1,    71,    72,    -1,    74,    75,
      76
    };
  }

/* YYSTOS[STATE-NUM] -- The symbol kind of the accessing symbol of
   state STATE-NUM.  */
  private static final short[] yystos_ = yystos_init();
  private static final short[] yystos_init()
  {
    return new short[]
    {
       0,    51,    81,   122,    16,    53,     0,    52,   123,   124,
      15,    55,   124,   125,   126,    16,    50,   126,   128,   129,
      16,   127,    16,     6,    82,    83,   129,    14,    16,    25,
      16,    83,     8,     9,    15,    17,    37,    47,    48,    68,
      70,   130,    33,   130,   133,   130,   131,   132,    16,    84,
      85,    49,   130,    43,    69,   132,    20,    34,   130,    85,
       3,     4,     5,     7,    10,    14,    16,    32,    33,    44,
      46,    53,    54,    58,    59,    60,    61,    78,    79,    86,
      89,    90,    91,    92,    93,    94,    95,   101,   104,   105,
     106,   108,   109,   110,   111,   112,   113,   114,   115,   116,
     117,   118,   119,   130,    16,   102,   103,   110,   114,   130,
      33,    56,    57,    86,    99,   100,   110,   111,   112,   113,
      99,    33,    48,   112,   112,    89,   109,    86,   110,    33,
     112,   112,   100,   112,   112,    33,    43,    14,    90,    20,
      25,    64,    25,    64,    38,    39,    40,    41,    42,    43,
      45,    77,    19,    21,    22,    23,    24,    67,    71,    72,
      73,    74,    75,    76,    20,    99,   112,   100,   100,    12,
      35,    36,    26,    27,    28,    29,    30,    31,    11,    87,
      88,   112,   112,    34,    20,    25,    87,    62,   120,   121,
      87,    16,    16,    53,   109,   110,    86,    86,   107,   110,
     114,   130,   107,   107,   107,   107,   107,   107,    16,   107,
     112,   112,   112,   112,   112,   112,   112,   112,   112,   112,
     112,   112,   103,    34,    14,    89,    99,    99,   100,   100,
     100,   100,   100,   100,    65,    89,    96,    97,    20,    34,
      49,    99,   107,    34,   100,    63,   121,    34,    33,    33,
      14,    99,    96,    13,    97,    98,    88,    20,    66,    11,
      14,    89,    87,    87,    11,    98,    89,    14,    89,   100,
      89,    14,    34,    34,    89,    14,    12,    20,    14,    89,
     112,    14,    12,    14,    89,    14
    };
  }

/* YYR1[RULE-NUM] -- Symbol kind of the left-hand side of rule RULE-NUM.  */
  private static final short[] yyr1_ = yyr1_init();
  private static final short[] yyr1_init()
  {
    return new short[]
    {
       0,    80,    81,    82,    82,    82,    83,    83,    84,    84,
      84,    85,    86,    86,    86,    86,    86,    87,    87,    87,
      88,    89,    89,    90,    90,    90,    90,    90,    90,    90,
      90,    90,    90,    90,    90,    90,    90,    90,    91,    92,
      92,    93,    93,    94,    94,    95,    95,    96,    96,    96,
      97,    97,    98,    98,    98,    99,    99,    99,    99,    99,
      99,    99,    99,    99,    99,    99,    99,   100,   101,   101,
     102,   102,   103,   103,   103,   104,   104,   105,   105,   106,
     106,   107,   107,   107,   108,   108,   108,   108,   108,   108,
     108,   109,   109,   110,   110,   110,   110,   111,   111,   111,
     111,   111,   111,   111,   111,   111,   111,   111,   111,   111,
     112,   112,   112,   112,   112,   113,   113,   113,   114,   114,
     114,   115,   116,   117,   118,   119,   119,   120,   120,   121,
     121,   122,   122,   122,   123,   123,   123,   124,   125,   125,
     125,   126,   127,   127,   128,   128,   128,   129,   130,   130,
     130,   130,   130,   130,   130,   130,   130,   131,   131,   131,
     132,   133,   133,   133
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
       1,     1,     1,     1,     1,     2,     2,     2,     1,     1,
       1,     1,     1,     2,     2,     6,     5,     1,     2,     4,
       3,     0,     2,     2,     0,     1,     2,     2,     0,     1,
       2,     4,     2,     1,     0,     1,     2,     4,     1,     1,
       1,     1,     1,     1,     1,     3,     3,     0,     1,     2,
       3,     0,     1,     2
    };
  }




  /* YYTRANSLATE_(TOKEN-NUM) -- Symbol number corresponding to TOKEN-NUM
     as returned by yylex, with out-of-bounds checking.  */
  private static final SymbolKind yytranslate_(int t)
  {
    // Last valid token kind.
    int code_max = 334;
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
      75,    76,    77,    78,    79
    };
  }


  private static final int YYLAST_ = 1910;
  private static final int YYEMPTY_ = -2;
  private static final int YYFINAL_ = 6;
  private static final int YYNTOKENS_ = 80;


}
/* "YYParser.y":1856  */


