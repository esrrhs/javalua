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
    S_for_lua_stmt(94),            /* for_lua_stmt  */
    S_while_stmt(95),              /* while_stmt  */
    S_if_stmt(96),                 /* if_stmt  */
    S_elseif_stmt_list(97),        /* elseif_stmt_list  */
    S_elseif_stmt(98),             /* elseif_stmt  */
    S_else_stmt(99),               /* else_stmt  */
    S_cmp(100),                    /* cmp  */
    S_cmp_value(101),              /* cmp_value  */
    S_return_stmt(102),            /* return_stmt  */
    S_return_value_list(103),      /* return_value_list  */
    S_return_value(104),           /* return_value  */
    S_assign_stmt(105),            /* assign_stmt  */
    S_multi_assign_stmt(106),      /* multi_assign_stmt  */
    S_var_list(107),               /* var_list  */
    S_assign_value(108),           /* assign_value  */
    S_math_assign_stmt(109),       /* math_assign_stmt  */
    S_var(110),                    /* var  */
    S_variable(111),               /* variable  */
    S_math_expr(112),              /* math_expr  */
    S_expr_value(113),             /* expr_value  */
    S_unary_expr(114),             /* unary_expr  */
    S_expr(115),                   /* expr  */
    S_break(116),                  /* break  */
    S_continue(117),               /* continue  */
    S_sleep(118),                  /* sleep  */
    S_yield(119),                  /* yield  */
    S_switch_stmt(120),            /* switch_stmt  */
    S_switch_case_list(121),       /* switch_case_list  */
    S_switch_case_define(122),     /* switch_case_define  */
    S_package_head(123),           /* package_head  */
    S_include_head(124),           /* include_head  */
    S_include_define(125),         /* include_define  */
    S_struct_head(126),            /* struct_head  */
    S_struct_define(127),          /* struct_define  */
    S_struct_mem_declaration(128), /* struct_mem_declaration  */
    S_const_head(129),             /* const_head  */
    S_const_define(130),           /* const_define  */
    S_explicit_value(131),         /* explicit_value  */
    S_const_map_list_value(132),   /* const_map_list_value  */
    S_const_map_value(133),        /* const_map_value  */
    S_const_array_list_value(134); /* const_array_list_value  */


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
      SymbolKind.S_for_lua_stmt,
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
  "fake_call_stmt", "for_stmt", "for_loop_stmt", "for_lua_stmt",
  "while_stmt", "if_stmt", "elseif_stmt_list", "elseif_stmt", "else_stmt",
  "cmp", "cmp_value", "return_stmt", "return_value_list", "return_value",
  "assign_stmt", "multi_assign_stmt", "var_list", "assign_value",
  "math_assign_stmt", "var", "variable", "math_expr", "expr_value",
  "unary_expr", "expr", "break", "continue", "sleep", "yield",
  "switch_stmt", "switch_case_list", "switch_case_define", "package_head",
  "include_head", "include_define", "struct_head", "struct_define",
  "struct_mem_declaration", "const_head", "const_define", "explicit_value",
  "const_map_list_value", "const_map_value", "const_array_list_value", null
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
    /* "YYParser.y":141  */
        {
	};
  break;


  case 6: /* function_declaration: FUNC IDENTIFIER OPEN_BRACKET function_declaration_arguments CLOSE_BRACKET block END  */
  if (yyn == 6)
    /* "YYParser.y":151  */
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
    /* "YYParser.y":162  */
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
    /* "YYParser.y":174  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
	};
  break;


  case 9: /* function_declaration_arguments: function_declaration_arguments ARG_SPLITTER arg  */
  if (yyn == 9)
    /* "YYParser.y":179  */
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
    /* "YYParser.y":190  */
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
    /* "YYParser.y":203  */
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
    /* "YYParser.y":217  */
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
    /* "YYParser.y":231  */
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
    /* "YYParser.y":245  */
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
    /* "YYParser.y":259  */
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
    /* "YYParser.y":278  */
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
    /* "YYParser.y":299  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
	};
  break;


  case 18: /* function_call_arguments: function_call_arguments ARG_SPLITTER arg_expr  */
  if (yyn == 18)
    /* "YYParser.y":304  */
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
    /* "YYParser.y":315  */
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
    /* "YYParser.y":328  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 21: /* block: block stmt  */
  if (yyn == 21)
    /* "YYParser.y":338  */
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
    /* "YYParser.y":349  */
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
    /* "YYParser.y":362  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: while_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 24: /* stmt: if_stmt  */
  if (yyn == 24)
    /* "YYParser.y":368  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: if_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 25: /* stmt: return_stmt  */
  if (yyn == 25)
    /* "YYParser.y":374  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 26: /* stmt: assign_stmt  */
  if (yyn == 26)
    /* "YYParser.y":380  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 27: /* stmt: multi_assign_stmt  */
  if (yyn == 27)
    /* "YYParser.y":386  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: multi_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 28: /* stmt: break  */
  if (yyn == 28)
    /* "YYParser.y":392  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: break");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 29: /* stmt: continue  */
  if (yyn == 29)
    /* "YYParser.y":398  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: continue");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 30: /* stmt: expr  */
  if (yyn == 30)
    /* "YYParser.y":404  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 31: /* stmt: math_assign_stmt  */
  if (yyn == 31)
    /* "YYParser.y":410  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_assign_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 32: /* stmt: for_stmt  */
  if (yyn == 32)
    /* "YYParser.y":416  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 33: /* stmt: for_loop_stmt  */
  if (yyn == 33)
    /* "YYParser.y":422  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_loop_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 34: /* stmt: for_lua_stmt  */
  if (yyn == 34)
    /* "YYParser.y":428  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: for_lua_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 35: /* stmt: fake_call_stmt  */
  if (yyn == 35)
    /* "YYParser.y":434  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: fake_call_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 36: /* stmt: sleep  */
  if (yyn == 36)
    /* "YYParser.y":440  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: sleep_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 37: /* stmt: yield  */
  if (yyn == 37)
    /* "YYParser.y":446  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: yield_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 38: /* stmt: switch_stmt  */
  if (yyn == 38)
    /* "YYParser.y":452  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_stmt");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 39: /* fake_call_stmt: FAKE function_call  */
  if (yyn == 39)
    /* "YYParser.y":460  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FAKE function_call");
		function_call_node p = (function_call_node)((ParserVal)yystack.valueAt (0)).obj;
		p.m_fakecall = true;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 40: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO block END  */
  if (yyn == 40)
    /* "YYParser.y":476  */
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


  case 41: /* for_stmt: FOR block ARG_SPLITTER cmp ARG_SPLITTER block DO END  */
  if (yyn == 41)
    /* "YYParser.y":490  */
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


  case 42: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO block END  */
  if (yyn == 42)
    /* "YYParser.y":508  */
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


  case 43: /* for_loop_stmt: FOR var ASSIGN assign_value RIGHT_POINTER cmp_value ARG_SPLITTER expr_value DO END  */
  if (yyn == 43)
    /* "YYParser.y":523  */
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


  case 44: /* for_lua_stmt: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value ARG_SPLITTER expr_value DO block END  */
  if (yyn == 44)
    /* "YYParser.y":550  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value ARG_SPLITTER expr_value DO block END");
		yyval = ((ParserVal)((Yylex)yylexer).make_lua_for(
				((ParserVal)yystack.valueAt (9)), ((ParserVal)yystack.valueAt (7)), ((ParserVal)yystack.valueAt (5)), ((ParserVal)yystack.valueAt (3)), ((ParserVal)yystack.valueAt (1))));
	};
  break;


  case 45: /* for_lua_stmt: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value DO block END  */
  if (yyn == 45)
    /* "YYParser.y":557  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value DO block END");
		yyval = ((ParserVal)((Yylex)yylexer).make_lua_for(
				((ParserVal)yystack.valueAt (7)), ((ParserVal)yystack.valueAt (5)), ((ParserVal)yystack.valueAt (3)), null, ((ParserVal)yystack.valueAt (1))));
	};
  break;


  case 46: /* for_lua_stmt: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value ARG_SPLITTER expr_value DO END  */
  if (yyn == 46)
    /* "YYParser.y":564  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value ARG_SPLITTER expr_value DO END");
		yyval = ((ParserVal)((Yylex)yylexer).make_lua_for(
				((ParserVal)yystack.valueAt (8)), ((ParserVal)yystack.valueAt (6)), ((ParserVal)yystack.valueAt (4)), ((ParserVal)yystack.valueAt (2)), null));
	};
  break;


  case 47: /* for_lua_stmt: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value DO END  */
  if (yyn == 47)
    /* "YYParser.y":571  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FOR IDENTIFIER ASSIGN expr_value ARG_SPLITTER expr_value DO END");
		yyval = ((ParserVal)((Yylex)yylexer).make_lua_for(
				((ParserVal)yystack.valueAt (6)), ((ParserVal)yystack.valueAt (4)), ((ParserVal)yystack.valueAt (2)), null, null));
	};
  break;


  case 48: /* while_stmt: WHILE cmp DO block END  */
  if (yyn == 48)
    /* "YYParser.y":580  */
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


  case 49: /* while_stmt: WHILE cmp DO END  */
  if (yyn == 49)
    /* "YYParser.y":592  */
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


  case 50: /* if_stmt: IF cmp THEN block elseif_stmt_list else_stmt END  */
  if (yyn == 50)
    /* "YYParser.y":606  */
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


  case 51: /* if_stmt: IF cmp THEN elseif_stmt_list else_stmt END  */
  if (yyn == 51)
    /* "YYParser.y":620  */
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


  case 52: /* elseif_stmt_list: %empty  */
  if (yyn == 52)
    /* "YYParser.y":636  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 53: /* elseif_stmt_list: elseif_stmt_list elseif_stmt  */
  if (yyn == 53)
    /* "YYParser.y":645  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt_list elseif_stmt");
		elseif_stmt_list p = (elseif_stmt_list)((ParserVal)yystack.valueAt (1)).obj;
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 54: /* elseif_stmt_list: elseif_stmt  */
  if (yyn == 54)
    /* "YYParser.y":656  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: elseif_stmt");
		elseif_stmt_list p = ((Yylex)yylexer).new_node(elseif_stmt_list.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_stmt((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 55: /* elseif_stmt: ELSEIF cmp THEN block  */
  if (yyn == 55)
    /* "YYParser.y":669  */
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


  case 56: /* elseif_stmt: ELSEIF cmp THEN  */
  if (yyn == 56)
    /* "YYParser.y":681  */
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


  case 57: /* else_stmt: %empty  */
  if (yyn == 57)
    /* "YYParser.y":695  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty");
		
		ParserVal ret = new ParserVal(null);
		ret.ival = ((Yylex)yylexer).get_mybison().get_jflex().get_line();
		yyval = ret;
	};
  break;


  case 58: /* else_stmt: ELSE block  */
  if (yyn == 58)
    /* "YYParser.y":704  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE block");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_block = (block_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 59: /* else_stmt: ELSE  */
  if (yyn == 59)
    /* "YYParser.y":715  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: ELSE");
		else_stmt p = ((Yylex)yylexer).new_node(else_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_block = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 60: /* cmp: OPEN_BRACKET cmp CLOSE_BRACKET  */
  if (yyn == 60)
    /* "YYParser.y":728  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET cmp CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 61: /* cmp: cmp AND cmp  */
  if (yyn == 61)
    /* "YYParser.y":734  */
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


  case 62: /* cmp: cmp OR cmp  */
  if (yyn == 62)
    /* "YYParser.y":747  */
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


  case 63: /* cmp: cmp_value LESS cmp_value  */
  if (yyn == 63)
    /* "YYParser.y":760  */
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


  case 64: /* cmp: cmp_value MORE cmp_value  */
  if (yyn == 64)
    /* "YYParser.y":773  */
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


  case 65: /* cmp: cmp_value EQUAL cmp_value  */
  if (yyn == 65)
    /* "YYParser.y":786  */
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


  case 66: /* cmp: cmp_value MORE_OR_EQUAL cmp_value  */
  if (yyn == 66)
    /* "YYParser.y":799  */
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


  case 67: /* cmp: cmp_value LESS_OR_EQUAL cmp_value  */
  if (yyn == 67)
    /* "YYParser.y":812  */
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


  case 68: /* cmp: cmp_value NOT_EQUAL cmp_value  */
  if (yyn == 68)
    /* "YYParser.y":825  */
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


  case 69: /* cmp: cmp_value  */
  if (yyn == 69)
    /* "YYParser.y":841  */
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


  case 70: /* cmp: IS cmp_value  */
  if (yyn == 70)
    /* "YYParser.y":854  */
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


  case 71: /* cmp: NOT cmp_value  */
  if (yyn == 71)
    /* "YYParser.y":867  */
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


  case 72: /* cmp_value: expr_value  */
  if (yyn == 72)
    /* "YYParser.y":885  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 73: /* return_stmt: RETURN return_value_list  */
  if (yyn == 73)
    /* "YYParser.y":893  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN return_value_list");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_returnlist = (return_value_list_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 74: /* return_stmt: RETURN  */
  if (yyn == 74)
    /* "YYParser.y":904  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: RETURN");
		return_stmt p = ((Yylex)yylexer).new_node(return_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		p.m_returnlist = null;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 75: /* return_value_list: return_value_list ARG_SPLITTER return_value  */
  if (yyn == 75)
    /* "YYParser.y":917  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value_list ARG_SPLITTER return_value");
		return_value_list_node p = (return_value_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 76: /* return_value_list: return_value  */
  if (yyn == 76)
    /* "YYParser.y":928  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: return_value");
		return_value_list_node p = ((Yylex)yylexer).new_node(return_value_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 77: /* return_value: explicit_value  */
  if (yyn == 77)
    /* "YYParser.y":941  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 78: /* return_value: variable  */
  if (yyn == 78)
    /* "YYParser.y":947  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 79: /* return_value: expr  */
  if (yyn == 79)
    /* "YYParser.y":953  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 80: /* assign_stmt: var ASSIGN assign_value  */
  if (yyn == 80)
    /* "YYParser.y":961  */
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


  case 81: /* assign_stmt: var NEW_ASSIGN assign_value  */
  if (yyn == 81)
    /* "YYParser.y":974  */
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


  case 82: /* multi_assign_stmt: var_list ASSIGN function_call  */
  if (yyn == 82)
    /* "YYParser.y":989  */
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


  case 83: /* multi_assign_stmt: var_list NEW_ASSIGN function_call  */
  if (yyn == 83)
    /* "YYParser.y":1002  */
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


  case 84: /* var_list: var_list ARG_SPLITTER var  */
  if (yyn == 84)
    /* "YYParser.y":1017  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var_list ARG_SPLITTER var");
		var_list_node p = (var_list_node)((ParserVal)yystack.valueAt (2)).obj;
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 85: /* var_list: var  */
  if (yyn == 85)
    /* "YYParser.y":1028  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: var");
		var_list_node p = ((Yylex)yylexer).new_node(var_list_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_arg((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 86: /* assign_value: explicit_value  */
  if (yyn == 86)
    /* "YYParser.y":1041  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 87: /* assign_value: variable  */
  if (yyn == 87)
    /* "YYParser.y":1047  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 88: /* assign_value: expr  */
  if (yyn == 88)
    /* "YYParser.y":1053  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 89: /* math_assign_stmt: variable PLUS_ASSIGN assign_value  */
  if (yyn == 89)
    /* "YYParser.y":1061  */
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


  case 90: /* math_assign_stmt: variable MINUS_ASSIGN assign_value  */
  if (yyn == 90)
    /* "YYParser.y":1074  */
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


  case 91: /* math_assign_stmt: variable DIVIDE_ASSIGN assign_value  */
  if (yyn == 91)
    /* "YYParser.y":1087  */
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


  case 92: /* math_assign_stmt: variable DIVIDE_FLOOR_ASSIGN assign_value  */
  if (yyn == 92)
    /* "YYParser.y":1100  */
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


  case 93: /* math_assign_stmt: variable MULTIPLY_ASSIGN assign_value  */
  if (yyn == 93)
    /* "YYParser.y":1113  */
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


  case 94: /* math_assign_stmt: variable DIVIDE_MOD_ASSIGN assign_value  */
  if (yyn == 94)
    /* "YYParser.y":1126  */
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


  case 95: /* math_assign_stmt: variable INC  */
  if (yyn == 95)
    /* "YYParser.y":1139  */
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


  case 96: /* var: VAR_BEGIN IDENTIFIER  */
  if (yyn == 96)
    /* "YYParser.y":1158  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: VAR_BEGIN IDENTIFIER");
		var_node p = ((Yylex)yylexer).new_node(var_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 97: /* var: variable  */
  if (yyn == 97)
    /* "YYParser.y":1169  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 98: /* variable: IDENTIFIER  */
  if (yyn == 98)
    /* "YYParser.y":1177  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 99: /* variable: IDENTIFIER OPEN_SQUARE_BRACKET expr_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 99)
    /* "YYParser.y":1188  */
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


  case 100: /* variable: IDENTIFIER_POINTER  */
  if (yyn == 100)
    /* "YYParser.y":1200  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_POINTER");
		struct_pointer_node p = ((Yylex)yylexer).new_node(struct_pointer_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 101: /* variable: IDENTIFIER_DOT  */
  if (yyn == 101)
    /* "YYParser.y":1211  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER_DOT");
		variable_node p = ((Yylex)yylexer).new_node(variable_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_str = ((ParserVal)((ParserVal)yystack.valueAt (0))).sval;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 102: /* math_expr: OPEN_BRACKET expr_value CLOSE_BRACKET  */
  if (yyn == 102)
    /* "YYParser.y":1227  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: OPEN_BRACKET expr_value CLOSE_BRACKET");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (1)));
	};
  break;


  case 103: /* math_expr: expr_value PLUS expr_value  */
  if (yyn == 103)
    /* "YYParser.y":1233  */
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
    /* "YYParser.y":1246  */
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
    /* "YYParser.y":1259  */
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
    /* "YYParser.y":1272  */
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
    /* "YYParser.y":1285  */
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


  case 108: /* math_expr: expr_value DIVIDE_FLOOR expr_value  */
  if (yyn == 108)
    /* "YYParser.y":1298  */
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


  case 109: /* math_expr: expr_value BIT_AND expr_value  */
  if (yyn == 109)
    /* "YYParser.y":1311  */
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


  case 110: /* math_expr: expr_value BIT_OR expr_value  */
  if (yyn == 110)
    /* "YYParser.y":1324  */
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


  case 111: /* math_expr: expr_value WAVE expr_value  */
  if (yyn == 111)
    /* "YYParser.y":1337  */
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


  case 112: /* math_expr: expr_value SHIFT_LEFT expr_value  */
  if (yyn == 112)
    /* "YYParser.y":1350  */
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


  case 113: /* math_expr: expr_value SHIFT_RIGHT expr_value  */
  if (yyn == 113)
    /* "YYParser.y":1363  */
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


  case 114: /* math_expr: expr_value STRING_CAT expr_value  */
  if (yyn == 114)
    /* "YYParser.y":1376  */
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


  case 115: /* expr_value: math_expr  */
  if (yyn == 115)
    /* "YYParser.y":1392  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 116: /* expr_value: unary_expr  */
  if (yyn == 116)
    /* "YYParser.y":1398  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 117: /* expr_value: explicit_value  */
  if (yyn == 117)
    /* "YYParser.y":1404  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 118: /* expr_value: function_call  */
  if (yyn == 118)
    /* "YYParser.y":1410  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 119: /* expr_value: variable  */
  if (yyn == 119)
    /* "YYParser.y":1416  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: variable");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 120: /* unary_expr: UNARY_MINUS expr_value  */
  if (yyn == 120)
    /* "YYParser.y":1428  */
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


  case 121: /* unary_expr: UNARY_WAVE expr_value  */
  if (yyn == 121)
    /* "YYParser.y":1441  */
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


  case 122: /* unary_expr: SHARP expr_value  */
  if (yyn == 122)
    /* "YYParser.y":1460  */
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


  case 123: /* expr: function_call  */
  if (yyn == 123)
    /* "YYParser.y":1484  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: function_call");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 124: /* expr: math_expr  */
  if (yyn == 124)
    /* "YYParser.y":1490  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: math_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 125: /* expr: unary_expr  */
  if (yyn == 125)
    /* "YYParser.y":1496  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: unary_expr");
		yyval = ((ParserVal)((ParserVal)yystack.valueAt (0)));
	};
  break;


  case 126: /* break: BREAK  */
  if (yyn == 126)
    /* "YYParser.y":1504  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: BREAK");
		break_stmt p = ((Yylex)yylexer).new_node(break_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 127: /* continue: CONTINUE  */
  if (yyn == 127)
    /* "YYParser.y":1516  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: CONTINUE");
		continue_stmt p = ((Yylex)yylexer).new_node(continue_stmt.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 128: /* sleep: SLEEP expr_value  */
  if (yyn == 128)
    /* "YYParser.y":1528  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: SLEEP");
		sleep_stmt p = ((Yylex)yylexer).new_node(sleep_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 129: /* yield: YIELD expr_value  */
  if (yyn == 129)
    /* "YYParser.y":1541  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: YIELD");
		yield_stmt p = ((Yylex)yylexer).new_node(yield_stmt.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.m_time = (syntree_node)((ParserVal)yystack.valueAt (0)).obj;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 130: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT block END  */
  if (yyn == 130)
    /* "YYParser.y":1554  */
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


  case 131: /* switch_stmt: SWITCH cmp_value switch_case_list DEFAULT END  */
  if (yyn == 131)
    /* "YYParser.y":1567  */
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


  case 132: /* switch_case_list: switch_case_define  */
  if (yyn == 132)
    /* "YYParser.y":1582  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_define");
		switch_caselist_node p = ((Yylex)yylexer).new_node(switch_caselist_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 133: /* switch_case_list: switch_case_list switch_case_define  */
  if (yyn == 133)
    /* "YYParser.y":1593  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: switch_case_list switch_case_define");
		switch_caselist_node p = (switch_caselist_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_case((syntree_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 134: /* switch_case_define: CASE cmp_value THEN block  */
  if (yyn == 134)
    /* "YYParser.y":1606  */
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


  case 135: /* switch_case_define: CASE cmp_value THEN  */
  if (yyn == 135)
    /* "YYParser.y":1618  */
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


  case 136: /* package_head: %empty  */
  if (yyn == 136)
    /* "YYParser.y":1632  */
        {
	};
  break;


  case 137: /* package_head: PACKAGE IDENTIFIER  */
  if (yyn == 137)
    /* "YYParser.y":1636  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 138: /* package_head: PACKAGE IDENTIFIER_DOT  */
  if (yyn == 138)
    /* "YYParser.y":1642  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: PACKAGE IDENTIFIER_DOT ");
		((Yylex)yylexer).get_mybison().set_package(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 139: /* include_head: %empty  */
  if (yyn == 139)
    /* "YYParser.y":1650  */
        {
	};
  break;


  case 142: /* include_define: INCLUDE STRING_DEFINITION  */
  if (yyn == 142)
    /* "YYParser.y":1660  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: INCLUDE STRING_DEFINITION ");
		((Yylex)yylexer).get_mybison().add_include(((ParserVal)yystack.valueAt (0)).sval);
	};
  break;


  case 143: /* struct_head: %empty  */
  if (yyn == 143)
    /* "YYParser.y":1668  */
        {
	};
  break;


  case 146: /* struct_define: STRUCT IDENTIFIER struct_mem_declaration END  */
  if (yyn == 146)
    /* "YYParser.y":1678  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: STRUCT IDENTIFIER struct_mem_declaration END ");
		((Yylex)yylexer).get_mybison().add_struct_desc(((ParserVal)yystack.valueAt (2)).sval);
	};
  break;


  case 147: /* struct_mem_declaration: struct_mem_declaration IDENTIFIER  */
  if (yyn == 147)
    /* "YYParser.y":1686  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: struct_mem_declaration IDENTIFIER ");
	};
  break;


  case 148: /* struct_mem_declaration: IDENTIFIER  */
  if (yyn == 148)
    /* "YYParser.y":1691  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: IDENTIFIER ");
	};
  break;


  case 149: /* const_head: %empty  */
  if (yyn == 149)
    /* "YYParser.y":1698  */
        {
	};
  break;


  case 152: /* const_define: FCONST IDENTIFIER ASSIGN explicit_value  */
  if (yyn == 152)
    /* "YYParser.y":1708  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FCONST IDENTIFIER ASSIGN explicit_value ");
		((Yylex)yylexer).get_mybison().add_const_desc(((ParserVal)yystack.valueAt (2)).sval, (syntree_node)((ParserVal)yystack.valueAt (0)).obj);
	};
  break;


  case 153: /* explicit_value: NULL  */
  if (yyn == 153)
    /* "YYParser.y":1716  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: NULL ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_NULL;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 154: /* explicit_value: FTRUE  */
  if (yyn == 154)
    /* "YYParser.y":1727  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FTRUE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_TRUE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 155: /* explicit_value: FFALSE  */
  if (yyn == 155)
    /* "YYParser.y":1738  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: FFALSE ");
		explicit_value_node p = ((Yylex)yylexer).new_node(explicit_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line() + 1);
		p.m_type = explicit_value_type.EVT_FALSE;
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 156: /* explicit_value: NUMBER  */
  if (yyn == 156)
    /* "YYParser.y":1749  */
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


  case 157: /* explicit_value: FKUUID  */
  if (yyn == 157)
    /* "YYParser.y":1761  */
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


  case 158: /* explicit_value: STRING_DEFINITION  */
  if (yyn == 158)
    /* "YYParser.y":1773  */
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


  case 159: /* explicit_value: FKFLOAT  */
  if (yyn == 159)
    /* "YYParser.y":1785  */
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


  case 160: /* explicit_value: OPEN_BIG_BRACKET const_map_list_value CLOSE_BIG_BRACKET  */
  if (yyn == 160)
    /* "YYParser.y":1797  */
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


  case 161: /* explicit_value: OPEN_SQUARE_BRACKET const_array_list_value CLOSE_SQUARE_BRACKET  */
  if (yyn == 161)
    /* "YYParser.y":1809  */
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


  case 162: /* const_map_list_value: %empty  */
  if (yyn == 162)
    /* "YYParser.y":1824  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 163: /* const_map_list_value: const_map_value  */
  if (yyn == 163)
    /* "YYParser.y":1834  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_value ");
		const_map_list_value_node p = ((Yylex)yylexer).new_node(const_map_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 164: /* const_map_list_value: const_map_list_value const_map_value  */
  if (yyn == 164)
    /* "YYParser.y":1845  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_map_list_value const_map_value ");
		const_map_list_value_node p = (const_map_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((const_map_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 165: /* const_map_value: explicit_value COLON explicit_value  */
  if (yyn == 165)
    /* "YYParser.y":1859  */
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


  case 166: /* const_array_list_value: %empty  */
  if (yyn == 166)
    /* "YYParser.y":1873  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: empty ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((Yylex)yylexer).get_mybison().get_jflex().get_line());
				
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 167: /* const_array_list_value: explicit_value  */
  if (yyn == 167)
    /* "YYParser.y":1883  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: explicit_value ");
		const_array_list_value_node p = ((Yylex)yylexer).new_node(const_array_list_value_node.class, ((ParserVal)((ParserVal)yystack.valueAt (0))).ival);
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;


  case 168: /* const_array_list_value: const_array_list_value explicit_value  */
  if (yyn == 168)
    /* "YYParser.y":1894  */
        {
		types.log(((Yylex)yylexer).get_mybison().get_fake(), "[BISON]: const_array_list_value explicit_value ");
		const_array_list_value_node p = (const_array_list_value_node)((ParserVal)yystack.valueAt (1)).obj;
		p.add_ele((explicit_value_node)((ParserVal)yystack.valueAt (0)).obj);
		
		ParserVal ret = new ParserVal(p);
		ret.ival = p.m_lno;
		yyval = ret;
	};
  break;



/* "/Users/mingming/project/fakescript_java/src/main/java/com/github/esrrhs/fakescript/YYParser.java":3038  */

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
  private static final short yytable_ninf_ = -120;

/* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
   STATE-NUM.  */
  private static final short[] yypact_ = yypact_init();
  private static final short[] yypact_init()
  {
    return new short[]
    {
     -31,    18,    36,   -11,  -210,  -210,  -210,    46,   -12,  -210,
    -210,    49,  -210,   118,  -210,    67,    79,  -210,    16,  -210,
    -210,   153,    83,   108,   110,  -210,  -210,  -210,  -210,   788,
     101,  -210,  -210,  -210,  -210,  -210,  -210,  -210,   788,   788,
    -210,  -210,   129,  -210,   646,   123,   711,  -210,  -210,    19,
    -210,  -210,  -210,   788,  -210,  -210,   129,   643,  -210,  -210,
     154,  2042,  -210,   518,   518,  -210,     0,  2042,  2042,  1962,
       3,   146,  -210,  -210,  2042,  2042,  2042,  2042,  2042,   277,
     730,  -210,  -210,  -210,  -210,  -210,  -210,  -210,  -210,  -210,
    -210,    -4,  -210,   -18,   870,  2150,  2179,  2189,  -210,  -210,
    -210,  -210,  -210,  -210,  -210,  -210,   160,  -210,  2082,  -210,
    2208,   518,  2042,  2042,    -1,    82,   287,   140,  -210,  2179,
    -210,    62,  2042,  2042,  -210,  2092,    87,   807,   -10,    -1,
     140,  2042,  2179,  2179,   125,  -210,  -210,  2042,   173,  -210,
    -210,    15,     3,     3,  2042,  2042,  2042,  2042,  2042,  2042,
    2042,   174,  -210,  2042,  2042,  2042,  2042,  2042,  2042,  2042,
    2042,  2042,  2042,  2042,  2042,  2042,  2042,    92,  2092,  -210,
    -210,   884,   518,   518,  2042,  2042,  2042,  2042,  2042,  2042,
     961,    85,  -210,  2179,  2111,  -210,  2042,   518,  2042,   112,
    2042,    95,  -210,   142,   162,   145,  -210,  -210,  -210,    -1,
      -1,  -210,  2082,  -210,  2208,  -210,  -210,  -210,  -210,  -210,
    -210,   163,  -210,  -210,   -14,   -14,  -210,  -210,   312,   343,
     883,  2218,   648,   312,  -210,  -210,  -210,  -210,  1038,   150,
     150,  -210,  -210,  -210,  -210,  -210,  -210,   518,   961,    -7,
    -210,  2042,  -210,  -210,  2121,   113,   131,  -210,   187,  1115,
    -210,  -210,  2042,  2042,  -210,    71,    -7,  2039,  -210,   188,
    -210,  2042,  2039,  2042,  2039,  -210,  1192,   143,   144,  2039,
     189,  2039,  -210,     5,  1269,   181,  2039,  -210,  -210,  -210,
    2039,  -210,  1346,  2042,  1423,  2042,  -210,  1500,   390,  -210,
    1577,   568,  -210,  1654,  -210,  1731,  -210,  1808,  -210,  1885,
    -210,  -210
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
     136,     0,     0,   139,   137,   138,     1,     0,   143,   140,
     142,     0,   141,   149,   144,     0,     0,   145,     3,   150,
     148,     0,     0,     0,     2,     4,   151,   146,   147,     0,
       0,     5,   154,   155,   158,   156,   159,   157,   166,   162,
     153,   152,     8,   167,     0,     0,     0,   163,    11,     0,
      10,   161,   168,     0,   160,   164,     0,     0,   165,     9,
       0,    74,   126,     0,     0,     7,    98,     0,     0,     0,
       0,   101,   100,   127,     0,     0,     0,     0,     0,   123,
       0,    22,    35,    32,    33,    34,    23,    24,    25,    26,
      27,     0,    31,    85,   119,   124,     0,   125,    30,    28,
      29,    36,    37,    38,   117,    96,    73,    76,    78,    79,
      77,     0,     0,     0,   118,     0,    69,   119,   115,    72,
     116,     0,    17,     0,   122,     0,    98,     0,    85,    39,
       0,    17,   129,   128,     0,   120,   121,    17,     0,     6,
      21,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    95,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    72,    70,
      71,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      52,     0,    19,    20,     0,   102,     0,     0,     0,     0,
       0,     0,   132,     0,     0,    98,   101,    84,    97,    82,
      83,    80,    87,    88,    86,    81,    89,    90,    91,    93,
      94,     0,    92,   107,   103,   104,   106,   105,   114,   111,
     109,   110,   112,   113,   108,    75,    60,    49,     0,    61,
      62,    64,    63,    66,    67,    65,    68,     0,    52,    57,
      54,     0,    12,    99,     0,     0,    80,    13,     0,     0,
     133,    14,    17,    17,    48,     0,    57,    59,    53,     0,
      18,     0,     0,     0,   135,   131,     0,     0,     0,    56,
       0,    58,    51,     0,     0,     0,   134,   130,    16,    15,
      55,    50,     0,     0,     0,     0,    47,     0,     0,    41,
       0,     0,    45,     0,    40,     0,    46,     0,    43,     0,
      44,    42
    };
  }

/* YYPGOTO[NTERM-NUM].  */
  private static final short[] yypgoto_ = yypgoto_init();
  private static final short[] yypgoto_init()
  {
    return new short[]
    {
    -210,  -210,  -210,   184,  -210,   157,    -6,  -123,   -26,   -58,
     104,  -210,  -210,  -210,  -210,  -210,  -210,   -28,  -209,   -38,
     -62,   -75,  -210,  -210,    55,  -210,  -210,  -210,   391,  -210,
     -66,   -57,   159,   447,   210,   100,  -210,  -210,  -210,  -210,
    -210,  -210,    38,  -210,  -210,   226,  -210,   228,  -210,  -210,
     234,     6,  -210,   208,  -210
    };
  }

/* YYDEFGOTO[NTERM-NUM].  */
  private static final short[] yydefgoto_ = yydefgoto_init();
  private static final short[] yydefgoto_init()
  {
    return new short[]
    {
       0,     2,    24,    25,    49,    50,   114,   181,   182,    80,
      81,    82,    83,    84,    85,    86,    87,   239,   240,   259,
     115,   116,    88,   106,   107,    89,    90,    91,   201,    92,
      93,   117,   118,    96,   120,    98,    99,   100,   101,   102,
     103,   191,   192,     3,     8,     9,    13,    14,    21,    18,
      19,   104,    46,    47,    44
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
      94,   134,   121,   128,   108,   154,   257,   144,   189,   157,
     158,   127,    94,   130,   193,   188,   141,   282,    60,    66,
       1,   142,    23,    94,   154,   283,   155,   156,   157,   158,
     258,   195,   137,   122,     4,    41,     6,   169,   170,    56,
       7,     7,   138,    11,    43,    45,   145,   258,   123,   167,
      52,    79,    45,    57,   145,    79,    71,    72,   237,    58,
     143,    10,   165,    79,   129,    15,    16,   110,   196,    72,
      94,     5,   159,   180,    79,   197,   160,   161,   162,   163,
     164,   165,   269,    20,   198,   130,   130,   202,   202,   202,
     202,   202,   202,   202,   171,    22,   202,   172,   173,   231,
     232,   233,   234,   235,   236,   241,   172,   173,    29,   108,
     229,   230,   186,   228,    94,   248,    23,   172,   173,   242,
     122,    79,   238,    94,    30,   245,   226,   172,   173,   267,
     268,   202,   241,   262,    42,   123,   199,   200,    79,    79,
      79,    79,    79,    79,    79,    48,   247,    79,   172,   173,
     204,   204,   204,   204,   204,   204,   204,   190,   249,   204,
      79,   109,   241,   241,   241,    79,    53,    27,    16,    28,
     105,    94,   110,    11,    79,   255,   251,   278,   279,   131,
     166,    94,    79,   151,   140,   172,   173,   190,   275,   194,
     211,   266,    94,   123,   204,   252,   253,   263,   264,   271,
      94,   285,   272,   281,   274,    94,   276,    94,    31,    94,
     256,   280,    94,    59,    94,   260,    95,    94,   270,    94,
      95,   225,    79,    94,   287,    94,   290,    94,    95,   250,
      94,   140,    79,    94,    12,   297,    94,   299,    94,    95,
      94,    17,    94,    79,   203,   203,   203,   203,   203,   203,
     203,    79,    26,   203,    55,     0,    79,     0,    79,     0,
      79,     0,     0,    79,     0,    79,   109,    97,    79,     0,
      79,    97,     0,     0,    79,     0,    79,     0,    79,    97,
       0,    79,     0,     0,    79,     0,    95,    79,   203,    79,
      97,    79,     0,    79,     0,     0,  -118,     0,  -118,  -118,
    -118,  -118,     0,    95,    95,    95,    95,    95,    95,    95,
     137,     0,    95,   174,   175,   176,   177,   178,   179,     0,
     138,     0,     0,     0,     0,    95,     0,     0,     0,     0,
      95,   154,   140,   155,   156,   157,   158,    97,     0,    95,
       0,     0,   140,     0,  -118,     0,     0,    95,  -118,  -118,
    -118,  -118,  -118,  -118,    97,    97,    97,    97,    97,    97,
      97,     0,   154,    97,   155,   156,   157,   158,     0,     0,
     140,     0,     0,     0,     0,   140,    97,     0,   140,   159,
     140,    97,     0,     0,   140,     0,     0,    95,   165,     0,
      97,   140,     0,     0,   140,     0,     0,    95,    97,     0,
       0,   140,   293,   140,     0,     0,     0,     0,    95,   154,
     159,   155,   156,   157,   158,   161,    95,   163,   164,   165,
       0,    95,     0,    95,     0,    95,     0,     0,    95,     0,
      95,     0,     0,    95,     0,    95,     0,     0,    97,    95,
       0,    95,     0,    95,     0,     0,    95,     0,    97,    95,
       0,     0,    95,     0,    95,     0,    95,   159,    95,    97,
       0,   160,   161,   162,   163,   164,   165,    97,     0,     0,
       0,     0,    97,     0,    97,     0,    97,     0,     0,    97,
       0,    97,     0,     0,    97,     0,    97,     0,     0,     0,
      97,     0,    97,     0,    97,     0,     0,    97,     0,     0,
      97,     0,     0,    97,     0,    97,     0,    97,     0,    97,
     119,   119,     0,     0,   124,   125,     0,     0,     0,     0,
       0,   132,   133,   119,   135,   136,    32,    33,     0,     0,
       0,     0,     0,    34,    66,    35,   205,   206,   207,   208,
     209,   210,     0,     0,   212,     0,     0,     0,     0,     0,
      67,   111,     0,     0,     0,    36,     0,     0,   168,   119,
     119,     0,     0,     0,     0,    37,    38,     0,     0,   183,
     184,    71,    72,     0,   112,   113,     0,     0,   183,   246,
     295,     0,     0,     0,   183,     0,    39,   154,    40,   155,
     156,   157,   158,     0,     0,     0,    77,    78,     0,     0,
       0,   213,   214,   215,   216,   217,   218,   219,   220,   221,
     222,   223,   224,     0,     0,     0,     0,     0,     0,   119,
     119,   119,   119,   119,   119,   119,   119,     0,     0,     0,
       0,     0,     0,   244,   119,   159,     0,   119,     0,   160,
     161,   162,   163,   164,   165,     0,    60,    61,    62,     0,
      63,    32,    33,    64,    32,    33,     0,    65,    34,    66,
      35,    34,     0,    35,     0,     0,     0,   154,     0,   155,
     156,   157,   158,     0,     0,    67,    68,     0,     0,     0,
      36,     0,     0,    36,   119,     0,     0,    69,   183,    70,
      37,    38,     0,    37,    38,    51,    71,    72,     0,   183,
     183,    73,    74,    75,    76,     0,     0,     0,   273,     0,
     119,    39,     0,    40,    39,   159,    40,     0,     0,    32,
      33,    77,    78,   164,   165,     0,    34,     0,    35,     0,
     288,     0,   291,    60,    61,    62,     0,    63,    32,    33,
      64,     0,     0,     0,   139,    34,    66,    35,    36,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    37,    38,
       0,     0,    67,    68,     0,     0,     0,    36,     0,     0,
       0,     0,     0,     0,    69,     0,    70,    37,    38,    39,
      54,    40,     0,    71,    72,     0,     0,     0,    73,    74,
      75,    76,     0,     0,     0,     0,    32,    33,    39,     0,
      40,     0,     0,    34,     0,    35,     0,     0,    77,    78,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
       0,     0,    34,    66,    35,    36,     0,   187,     0,     0,
       0,     0,     0,     0,     0,    37,    38,     0,     0,    67,
      68,     0,     0,     0,    36,     0,     0,     0,     0,     0,
       0,    69,     0,    70,    37,    38,    39,     0,    40,     0,
      71,    72,     0,     0,     0,    73,    74,    75,    76,     0,
       0,     0,     0,     0,     0,    39,     0,    40,     0,     0,
       0,     0,     0,     0,     0,    77,    78,    60,    61,    62,
     -97,    63,    32,    33,    64,   -97,     0,     0,   227,    34,
      66,    35,   154,     0,   155,   156,   157,   158,   146,   147,
     148,   149,   150,   151,     0,   152,    67,    68,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    69,     0,
      70,    37,    38,     0,   -97,     0,     0,    71,    72,     0,
       0,     0,    73,    74,    75,    76,     0,   153,     0,     0,
     159,     0,    39,     0,    40,     0,     0,   163,   164,   165,
       0,     0,    77,    78,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,     0,     0,    34,    66,    35,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    67,    68,     0,     0,     0,    36,     0,
       0,     0,     0,     0,     0,    69,     0,    70,    37,    38,
       0,     0,     0,     0,    71,    72,     0,     0,     0,    73,
      74,    75,    76,     0,     0,     0,   237,     0,     0,    39,
       0,    40,     0,     0,     0,     0,     0,     0,     0,    77,
      78,    60,    61,    62,     0,    63,    32,    33,    64,     0,
       0,     0,   254,    34,    66,    35,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      67,    68,     0,     0,     0,    36,     0,     0,     0,     0,
       0,     0,    69,     0,    70,    37,    38,     0,     0,     0,
       0,    71,    72,     0,     0,     0,    73,    74,    75,    76,
       0,     0,     0,     0,     0,     0,    39,     0,    40,     0,
       0,     0,     0,     0,     0,     0,    77,    78,    60,    61,
      62,     0,    63,    32,    33,    64,     0,     0,     0,   265,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,    68,     0,
       0,     0,    36,     0,     0,     0,     0,     0,     0,    69,
       0,    70,    37,    38,     0,     0,     0,     0,    71,    72,
       0,     0,     0,    73,    74,    75,    76,     0,     0,     0,
       0,     0,     0,    39,     0,    40,     0,     0,     0,     0,
       0,     0,     0,    77,    78,    60,    61,    62,     0,    63,
      32,    33,    64,     0,     0,     0,   277,    34,    66,    35,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    67,    68,     0,     0,     0,    36,
       0,     0,     0,     0,     0,     0,    69,     0,    70,    37,
      38,     0,     0,     0,     0,    71,    72,     0,     0,     0,
      73,    74,    75,    76,     0,     0,     0,     0,     0,     0,
      39,     0,    40,     0,     0,     0,     0,     0,     0,     0,
      77,    78,    60,    61,    62,     0,    63,    32,    33,    64,
       0,   284,     0,     0,    34,    66,    35,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,    67,    68,     0,     0,     0,    36,     0,     0,     0,
       0,     0,     0,    69,     0,    70,    37,    38,     0,     0,
       0,     0,    71,    72,     0,     0,     0,    73,    74,    75,
      76,     0,     0,     0,     0,     0,     0,    39,     0,    40,
       0,     0,     0,     0,     0,     0,     0,    77,    78,    60,
      61,    62,     0,    63,    32,    33,    64,     0,     0,     0,
     286,    34,    66,    35,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    67,    68,
       0,     0,     0,    36,     0,     0,     0,     0,     0,     0,
      69,     0,    70,    37,    38,     0,     0,     0,     0,    71,
      72,     0,     0,     0,    73,    74,    75,    76,     0,     0,
       0,     0,     0,     0,    39,     0,    40,     0,     0,     0,
       0,     0,     0,     0,    77,    78,    60,    61,    62,     0,
      63,    32,    33,    64,     0,     0,     0,   289,    34,    66,
      35,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,    67,    68,     0,     0,     0,
      36,     0,     0,     0,     0,     0,     0,    69,     0,    70,
      37,    38,     0,     0,     0,     0,    71,    72,     0,     0,
       0,    73,    74,    75,    76,     0,     0,     0,     0,     0,
       0,    39,     0,    40,     0,     0,     0,     0,     0,     0,
       0,    77,    78,    60,    61,    62,     0,    63,    32,    33,
      64,     0,     0,     0,   292,    34,    66,    35,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    67,    68,     0,     0,     0,    36,     0,     0,
       0,     0,     0,     0,    69,     0,    70,    37,    38,     0,
       0,     0,     0,    71,    72,     0,     0,     0,    73,    74,
      75,    76,     0,     0,     0,     0,     0,     0,    39,     0,
      40,     0,     0,     0,     0,     0,     0,     0,    77,    78,
      60,    61,    62,     0,    63,    32,    33,    64,     0,     0,
       0,   294,    34,    66,    35,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    67,
      68,     0,     0,     0,    36,     0,     0,     0,     0,     0,
       0,    69,     0,    70,    37,    38,     0,     0,     0,     0,
      71,    72,     0,     0,     0,    73,    74,    75,    76,     0,
       0,     0,     0,     0,     0,    39,     0,    40,     0,     0,
       0,     0,     0,     0,     0,    77,    78,    60,    61,    62,
       0,    63,    32,    33,    64,     0,     0,     0,   296,    34,
      66,    35,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    67,    68,     0,     0,
       0,    36,     0,     0,     0,     0,     0,     0,    69,     0,
      70,    37,    38,     0,     0,     0,     0,    71,    72,     0,
       0,     0,    73,    74,    75,    76,     0,     0,     0,     0,
       0,     0,    39,     0,    40,     0,     0,     0,     0,     0,
       0,     0,    77,    78,    60,    61,    62,     0,    63,    32,
      33,    64,     0,     0,     0,   298,    34,    66,    35,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    67,    68,     0,     0,     0,    36,     0,
       0,     0,     0,     0,     0,    69,     0,    70,    37,    38,
       0,     0,     0,     0,    71,    72,     0,     0,     0,    73,
      74,    75,    76,     0,     0,     0,     0,     0,     0,    39,
       0,    40,     0,     0,     0,     0,     0,     0,     0,    77,
      78,    60,    61,    62,     0,    63,    32,    33,    64,     0,
       0,     0,   300,    34,    66,    35,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      67,    68,     0,     0,     0,    36,     0,     0,     0,     0,
       0,     0,    69,     0,    70,    37,    38,     0,     0,     0,
       0,    71,    72,     0,     0,     0,    73,    74,    75,    76,
       0,     0,     0,     0,     0,     0,    39,     0,    40,     0,
       0,     0,     0,     0,     0,     0,    77,    78,    60,    61,
      62,     0,    63,    32,    33,    64,     0,     0,     0,   301,
      34,    66,    35,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    67,    68,     0,
       0,     0,    36,     0,     0,     0,     0,     0,     0,    69,
       0,    70,    37,    38,     0,     0,     0,     0,    71,    72,
       0,     0,     0,    73,    74,    75,    76,     0,     0,     0,
       0,     0,     0,    39,     0,    40,     0,     0,     0,     0,
       0,     0,     0,    77,    78,    60,    61,    62,     0,    63,
      32,    33,    64,     0,     0,     0,     0,    34,   126,    35,
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
      38,     0,    71,    72,     0,    71,    72,    73,    74,    75,
      76,  -119,     0,  -119,  -119,  -119,  -119,    39,     0,    40,
      39,   154,    40,   155,   156,   157,   158,    77,    78,     0,
      77,    78,     0,     0,     0,   151,   185,     0,     0,     0,
     154,     0,   155,   156,   157,   158,     0,     0,     0,     0,
     154,   261,   155,   156,   157,   158,     0,     0,     0,  -119,
       0,     0,     0,  -119,  -119,  -119,  -119,  -119,  -119,   159,
     243,     0,     0,   160,   161,   162,   163,   164,   165,  -115,
       0,  -115,  -115,  -115,  -115,     0,     0,     0,   159,     0,
       0,     0,   160,   161,   162,   163,   164,   165,   159,     0,
       0,     0,   160,   161,   162,   163,   164,   165,   154,     0,
     155,   156,   157,   158,     0,     0,     0,     0,  -116,     0,
    -116,  -116,  -116,  -116,     0,     0,     0,  -115,     0,     0,
       0,  -115,  -115,  -115,  -115,  -115,  -115,  -117,     0,  -117,
    -117,  -117,  -117,     0,     0,     0,     0,   154,     0,   155,
     156,   157,   158,     0,     0,     0,   159,     0,     0,     0,
     160,   161,   162,   163,   164,   165,  -116,     0,     0,     0,
    -116,  -116,  -116,  -116,  -116,  -116,     0,     0,     0,     0,
       0,     0,     0,     0,     0,  -117,     0,     0,     0,  -117,
    -117,  -117,  -117,  -117,  -117,   159,     0,     0,     0,   160,
     161,     0,   163,   164,   165
    };
  }

private static final short[] yycheck_ = yycheck_init();
  private static final short[] yycheck_init()
  {
    return new short[]
    {
      57,    76,    64,    69,    61,    19,    13,    25,   131,    23,
      24,    69,    69,    70,   137,    25,    20,    12,     3,    16,
      51,    25,     6,    80,    19,    20,    21,    22,    23,    24,
     239,    16,    33,    33,    16,    29,     0,   112,   113,    20,
      52,    52,    43,    55,    38,    39,    64,   256,    48,   111,
      44,    57,    46,    34,    64,    61,    53,    54,    65,    53,
      64,    15,    76,    69,    70,    16,    50,    61,    53,    54,
     127,    53,    67,    11,    80,   141,    71,    72,    73,    74,
      75,    76,    11,    16,   141,   142,   143,   144,   145,   146,
     147,   148,   149,   150,    12,    16,   153,    35,    36,   174,
     175,   176,   177,   178,   179,    20,    35,    36,    25,   166,
     172,   173,    25,   171,   171,   190,     6,    35,    36,    34,
      33,   127,   180,   180,    16,   187,    34,    35,    36,   252,
     253,   188,    20,    20,    33,    48,   142,   143,   144,   145,
     146,   147,   148,   149,   150,    16,    34,   153,    35,    36,
     144,   145,   146,   147,   148,   149,   150,    62,    63,   153,
     166,    61,    20,    20,    20,   171,    43,    14,    50,    16,
      16,   228,   166,    55,   180,   237,    34,    34,    34,    33,
      20,   238,   188,    43,    80,    35,    36,    62,   263,    16,
      16,   249,   249,    48,   188,    33,    33,    66,    11,   257,
     257,    20,    14,    14,   262,   262,   264,   264,    24,   266,
     238,   269,   269,    56,   271,   241,    57,   274,   256,   276,
      61,   166,   228,   280,   282,   282,   284,   284,    69,   191,
     287,   127,   238,   290,     8,   293,   293,   295,   295,    80,
     297,    13,   299,   249,   144,   145,   146,   147,   148,   149,
     150,   257,    18,   153,    46,    -1,   262,    -1,   264,    -1,
     266,    -1,    -1,   269,    -1,   271,   166,    57,   274,    -1,
     276,    61,    -1,    -1,   280,    -1,   282,    -1,   284,    69,
      -1,   287,    -1,    -1,   290,    -1,   127,   293,   188,   295,
      80,   297,    -1,   299,    -1,    -1,    19,    -1,    21,    22,
      23,    24,    -1,   144,   145,   146,   147,   148,   149,   150,
      33,    -1,   153,    26,    27,    28,    29,    30,    31,    -1,
      43,    -1,    -1,    -1,    -1,   166,    -1,    -1,    -1,    -1,
     171,    19,   228,    21,    22,    23,    24,   127,    -1,   180,
      -1,    -1,   238,    -1,    67,    -1,    -1,   188,    71,    72,
      73,    74,    75,    76,   144,   145,   146,   147,   148,   149,
     150,    -1,    19,   153,    21,    22,    23,    24,    -1,    -1,
     266,    -1,    -1,    -1,    -1,   271,   166,    -1,   274,    67,
     276,   171,    -1,    -1,   280,    -1,    -1,   228,    76,    -1,
     180,   287,    -1,    -1,   290,    -1,    -1,   238,   188,    -1,
      -1,   297,    12,   299,    -1,    -1,    -1,    -1,   249,    19,
      67,    21,    22,    23,    24,    72,   257,    74,    75,    76,
      -1,   262,    -1,   264,    -1,   266,    -1,    -1,   269,    -1,
     271,    -1,    -1,   274,    -1,   276,    -1,    -1,   228,   280,
      -1,   282,    -1,   284,    -1,    -1,   287,    -1,   238,   290,
      -1,    -1,   293,    -1,   295,    -1,   297,    67,   299,   249,
      -1,    71,    72,    73,    74,    75,    76,   257,    -1,    -1,
      -1,    -1,   262,    -1,   264,    -1,   266,    -1,    -1,   269,
      -1,   271,    -1,    -1,   274,    -1,   276,    -1,    -1,    -1,
     280,    -1,   282,    -1,   284,    -1,    -1,   287,    -1,    -1,
     290,    -1,    -1,   293,    -1,   295,    -1,   297,    -1,   299,
      63,    64,    -1,    -1,    67,    68,    -1,    -1,    -1,    -1,
      -1,    74,    75,    76,    77,    78,     8,     9,    -1,    -1,
      -1,    -1,    -1,    15,    16,    17,   145,   146,   147,   148,
     149,   150,    -1,    -1,   153,    -1,    -1,    -1,    -1,    -1,
      32,    33,    -1,    -1,    -1,    37,    -1,    -1,   111,   112,
     113,    -1,    -1,    -1,    -1,    47,    48,    -1,    -1,   122,
     123,    53,    54,    -1,    56,    57,    -1,    -1,   131,   188,
      12,    -1,    -1,    -1,   137,    -1,    68,    19,    70,    21,
      22,    23,    24,    -1,    -1,    -1,    78,    79,    -1,    -1,
      -1,   154,   155,   156,   157,   158,   159,   160,   161,   162,
     163,   164,   165,    -1,    -1,    -1,    -1,    -1,    -1,   172,
     173,   174,   175,   176,   177,   178,   179,    -1,    -1,    -1,
      -1,    -1,    -1,   186,   187,    67,    -1,   190,    -1,    71,
      72,    73,    74,    75,    76,    -1,     3,     4,     5,    -1,
       7,     8,     9,    10,     8,     9,    -1,    14,    15,    16,
      17,    15,    -1,    17,    -1,    -1,    -1,    19,    -1,    21,
      22,    23,    24,    -1,    -1,    32,    33,    -1,    -1,    -1,
      37,    -1,    -1,    37,   237,    -1,    -1,    44,   241,    46,
      47,    48,    -1,    47,    48,    49,    53,    54,    -1,   252,
     253,    58,    59,    60,    61,    -1,    -1,    -1,   261,    -1,
     263,    68,    -1,    70,    68,    67,    70,    -1,    -1,     8,
       9,    78,    79,    75,    76,    -1,    15,    -1,    17,    -1,
     283,    -1,   285,     3,     4,     5,    -1,     7,     8,     9,
      10,    -1,    -1,    -1,    14,    15,    16,    17,    37,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    47,    48,
      -1,    -1,    32,    33,    -1,    -1,    -1,    37,    -1,    -1,
      -1,    -1,    -1,    -1,    44,    -1,    46,    47,    48,    68,
      69,    70,    -1,    53,    54,    -1,    -1,    -1,    58,    59,
      60,    61,    -1,    -1,    -1,    -1,     8,     9,    68,    -1,
      70,    -1,    -1,    15,    -1,    17,    -1,    -1,    78,    79,
       3,     4,     5,    -1,     7,     8,     9,    10,    -1,    -1,
      -1,    -1,    15,    16,    17,    37,    -1,    20,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    47,    48,    -1,    -1,    32,
      33,    -1,    -1,    -1,    37,    -1,    -1,    -1,    -1,    -1,
      -1,    44,    -1,    46,    47,    48,    68,    -1,    70,    -1,
      53,    54,    -1,    -1,    -1,    58,    59,    60,    61,    -1,
      -1,    -1,    -1,    -1,    -1,    68,    -1,    70,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    78,    79,     3,     4,     5,
      20,     7,     8,     9,    10,    25,    -1,    -1,    14,    15,
      16,    17,    19,    -1,    21,    22,    23,    24,    38,    39,
      40,    41,    42,    43,    -1,    45,    32,    33,    -1,    -1,
      -1,    37,    -1,    -1,    -1,    -1,    -1,    -1,    44,    -1,
      46,    47,    48,    -1,    64,    -1,    -1,    53,    54,    -1,
      -1,    -1,    58,    59,    60,    61,    -1,    77,    -1,    -1,
      67,    -1,    68,    -1,    70,    -1,    -1,    74,    75,    76,
      -1,    -1,    78,    79,     3,     4,     5,    -1,     7,     8,
       9,    10,    -1,    -1,    -1,    -1,    15,    16,    17,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    32,    33,    -1,    -1,    -1,    37,    -1,
      -1,    -1,    -1,    -1,    -1,    44,    -1,    46,    47,    48,
      -1,    -1,    -1,    -1,    53,    54,    -1,    -1,    -1,    58,
      59,    60,    61,    -1,    -1,    -1,    65,    -1,    -1,    68,
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
      -1,    12,    -1,    -1,    15,    16,    17,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    32,    33,    -1,    -1,    -1,    37,    -1,    -1,    -1,
      -1,    -1,    -1,    44,    -1,    46,    47,    48,    -1,    -1,
      -1,    -1,    53,    54,    -1,    -1,    -1,    58,    59,    60,
      61,    -1,    -1,    -1,    -1,    -1,    -1,    68,    -1,    70,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    78,    79,     3,
       4,     5,    -1,     7,     8,     9,    10,    -1,    -1,    -1,
      14,    15,    16,    17,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    32,    33,
      -1,    -1,    -1,    37,    -1,    -1,    -1,    -1,    -1,    -1,
      44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,    53,
      54,    -1,    -1,    -1,    58,    59,    60,    61,    -1,    -1,
      -1,    -1,    -1,    -1,    68,    -1,    70,    -1,    -1,    -1,
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
      -1,     7,     8,     9,    10,    -1,    -1,    -1,    14,    15,
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
       8,     9,    10,    -1,    -1,    -1,    -1,    15,    16,    17,
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
      48,    -1,    53,    54,    -1,    53,    54,    58,    59,    60,
      61,    19,    -1,    21,    22,    23,    24,    68,    -1,    70,
      68,    19,    70,    21,    22,    23,    24,    78,    79,    -1,
      78,    79,    -1,    -1,    -1,    43,    34,    -1,    -1,    -1,
      19,    -1,    21,    22,    23,    24,    -1,    -1,    -1,    -1,
      19,    20,    21,    22,    23,    24,    -1,    -1,    -1,    67,
      -1,    -1,    -1,    71,    72,    73,    74,    75,    76,    67,
      49,    -1,    -1,    71,    72,    73,    74,    75,    76,    19,
      -1,    21,    22,    23,    24,    -1,    -1,    -1,    67,    -1,
      -1,    -1,    71,    72,    73,    74,    75,    76,    67,    -1,
      -1,    -1,    71,    72,    73,    74,    75,    76,    19,    -1,
      21,    22,    23,    24,    -1,    -1,    -1,    -1,    19,    -1,
      21,    22,    23,    24,    -1,    -1,    -1,    67,    -1,    -1,
      -1,    71,    72,    73,    74,    75,    76,    19,    -1,    21,
      22,    23,    24,    -1,    -1,    -1,    -1,    19,    -1,    21,
      22,    23,    24,    -1,    -1,    -1,    67,    -1,    -1,    -1,
      71,    72,    73,    74,    75,    76,    67,    -1,    -1,    -1,
      71,    72,    73,    74,    75,    76,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    67,    -1,    -1,    -1,    71,
      72,    73,    74,    75,    76,    67,    -1,    -1,    -1,    71,
      72,    -1,    74,    75,    76
    };
  }

/* YYSTOS[STATE-NUM] -- The symbol kind of the accessing symbol of
   state STATE-NUM.  */
  private static final short[] yystos_ = yystos_init();
  private static final short[] yystos_init()
  {
    return new short[]
    {
       0,    51,    81,   123,    16,    53,     0,    52,   124,   125,
      15,    55,   125,   126,   127,    16,    50,   127,   129,   130,
      16,   128,    16,     6,    82,    83,   130,    14,    16,    25,
      16,    83,     8,     9,    15,    17,    37,    47,    48,    68,
      70,   131,    33,   131,   134,   131,   132,   133,    16,    84,
      85,    49,   131,    43,    69,   133,    20,    34,   131,    85,
       3,     4,     5,     7,    10,    14,    16,    32,    33,    44,
      46,    53,    54,    58,    59,    60,    61,    78,    79,    86,
      89,    90,    91,    92,    93,    94,    95,    96,   102,   105,
     106,   107,   109,   110,   111,   112,   113,   114,   115,   116,
     117,   118,   119,   120,   131,    16,   103,   104,   111,   115,
     131,    33,    56,    57,    86,   100,   101,   111,   112,   113,
     114,   100,    33,    48,   113,   113,    16,    89,   110,    86,
     111,    33,   113,   113,   101,   113,   113,    33,    43,    14,
      90,    20,    25,    64,    25,    64,    38,    39,    40,    41,
      42,    43,    45,    77,    19,    21,    22,    23,    24,    67,
      71,    72,    73,    74,    75,    76,    20,   100,   113,   101,
     101,    12,    35,    36,    26,    27,    28,    29,    30,    31,
      11,    87,    88,   113,   113,    34,    25,    20,    25,    87,
      62,   121,   122,    87,    16,    16,    53,   110,   111,    86,
      86,   108,   111,   115,   131,   108,   108,   108,   108,   108,
     108,    16,   108,   113,   113,   113,   113,   113,   113,   113,
     113,   113,   113,   113,   113,   104,    34,    14,    89,   100,
     100,   101,   101,   101,   101,   101,   101,    65,    89,    97,
      98,    20,    34,    49,   113,   100,   108,    34,   101,    63,
     122,    34,    33,    33,    14,   100,    97,    13,    98,    99,
      88,    20,    20,    66,    11,    14,    89,    87,    87,    11,
      99,    89,    14,   113,    89,   101,    89,    14,    34,    34,
      89,    14,    12,    20,    12,    20,    14,    89,   113,    14,
      89,   113,    14,    12,    14,    12,    14,    89,    14,    89,
      14,    14
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
      90,    90,    90,    90,    90,    90,    90,    90,    90,    91,
      92,    92,    93,    93,    94,    94,    94,    94,    95,    95,
      96,    96,    97,    97,    97,    98,    98,    99,    99,    99,
     100,   100,   100,   100,   100,   100,   100,   100,   100,   100,
     100,   100,   101,   102,   102,   103,   103,   104,   104,   104,
     105,   105,   106,   106,   107,   107,   108,   108,   108,   109,
     109,   109,   109,   109,   109,   109,   110,   110,   111,   111,
     111,   111,   112,   112,   112,   112,   112,   112,   112,   112,
     112,   112,   112,   112,   112,   113,   113,   113,   113,   113,
     114,   114,   114,   115,   115,   115,   116,   117,   118,   119,
     120,   120,   121,   121,   122,   122,   123,   123,   123,   124,
     124,   124,   125,   126,   126,   126,   127,   128,   128,   129,
     129,   129,   130,   131,   131,   131,   131,   131,   131,   131,
     131,   131,   132,   132,   132,   133,   134,   134,   134
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
       1,     1,     1,     1,     1,     1,     1,     1,     1,     2,
       9,     8,    11,    10,    11,     9,    10,     8,     5,     4,
       7,     6,     0,     2,     1,     4,     3,     0,     2,     1,
       3,     3,     3,     3,     3,     3,     3,     3,     3,     1,
       2,     2,     1,     2,     1,     3,     1,     1,     1,     1,
       3,     3,     3,     3,     3,     1,     1,     1,     1,     3,
       3,     3,     3,     3,     3,     2,     2,     1,     1,     4,
       1,     1,     3,     3,     3,     3,     3,     3,     3,     3,
       3,     3,     3,     3,     3,     1,     1,     1,     1,     1,
       2,     2,     2,     1,     1,     1,     1,     1,     2,     2,
       6,     5,     1,     2,     4,     3,     0,     2,     2,     0,
       1,     2,     2,     0,     1,     2,     4,     2,     1,     0,
       1,     2,     4,     1,     1,     1,     1,     1,     1,     1,
       3,     3,     0,     1,     2,     3,     0,     1,     2
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


  private static final int YYLAST_ = 2294;
  private static final int YYEMPTY_ = -2;
  private static final int YYFINAL_ = 6;
  private static final int YYNTOKENS_ = 80;


}
/* "YYParser.y":1905  */


