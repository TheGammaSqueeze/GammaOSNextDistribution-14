/* A Bison parser, made by GNU Bison 3.6.4.  */

/* Bison interface for Yacc-like parsers in C

   Copyright (C) 1984, 1989-1990, 2000-2015, 2018-2020 Free Software Foundation,
   Inc.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.  */

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

#ifndef YY_TRACEFS_SQLHIST_TAB_H_INCLUDED
# define YY_TRACEFS_SQLHIST_TAB_H_INCLUDED
/* Debug traces.  */
#ifndef TRACEFS_DEBUG
# if defined YYDEBUG
#if YYDEBUG
#   define TRACEFS_DEBUG 1
#  else
#   define TRACEFS_DEBUG 0
#  endif
# else /* ! defined YYDEBUG */
#  define TRACEFS_DEBUG 1
# endif /* ! defined YYDEBUG */
#endif  /* ! defined TRACEFS_DEBUG */
#if TRACEFS_DEBUG
extern int tracefs_debug;
#endif

/* Token kinds.  */
#ifndef TRACEFS_TOKENTYPE
# define TRACEFS_TOKENTYPE
  enum tracefs_tokentype
  {
    TRACEFS_EMPTY = -2,
    TRACEFS_EOF = 0,               /* "end of file"  */
    TRACEFS_error = 256,           /* error  */
    TRACEFS_UNDEF = 257,           /* "invalid token"  */
    AS = 258,                      /* AS  */
    SELECT = 259,                  /* SELECT  */
    FROM = 260,                    /* FROM  */
    JOIN = 261,                    /* JOIN  */
    ON = 262,                      /* ON  */
    WHERE = 263,                   /* WHERE  */
    PARSE_ERROR = 264,             /* PARSE_ERROR  */
    CAST = 265,                    /* CAST  */
    NUMBER = 266,                  /* NUMBER  */
    field_type = 267,              /* field_type  */
    STRING = 268,                  /* STRING  */
    FIELD = 269,                   /* FIELD  */
    LE = 270,                      /* LE  */
    GE = 271,                      /* GE  */
    EQ = 272,                      /* EQ  */
    NEQ = 273,                     /* NEQ  */
    AND = 274,                     /* AND  */
    OR = 275                       /* OR  */
  };
  typedef enum tracefs_tokentype tracefs_token_kind_t;
#endif

/* Value type.  */
#if ! defined TRACEFS_STYPE && ! defined TRACEFS_STYPE_IS_DECLARED
union TRACEFS_STYPE
{
#line 46 "sqlhist.y"

	int	s32;
	char	*string;
	long	number;
	void	*expr;

#line 99 "sqlhist.tab.h"

};
typedef union TRACEFS_STYPE TRACEFS_STYPE;
# define TRACEFS_STYPE_IS_TRIVIAL 1
# define TRACEFS_STYPE_IS_DECLARED 1
#endif



int tracefs_parse (struct sqlhist_bison *sb);
/* "%code provides" blocks.  */
#line 37 "sqlhist.y"

  #define YYSTYPE TRACEFS_STYPE
  #define yylex tracefs_lex
  #define yyerror tracefs_error

#line 117 "sqlhist.tab.h"

#endif /* !YY_TRACEFS_SQLHIST_TAB_H_INCLUDED  */
