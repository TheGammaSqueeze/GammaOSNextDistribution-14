/* A Bison parser, made by GNU Bison 3.6.4.  */

/* Bison implementation for Yacc-like parsers in C

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

/* C LALR(1) parser skeleton written by Richard Stallman, by
   simplifying the original so-called "semantic" parser.  */

/* DO NOT RELY ON FEATURES THAT ARE NOT DOCUMENTED in the manual,
   especially those whose name start with YY_ or yy_.  They are
   private implementation details that can be changed or removed.  */

/* All symbols defined below should begin with yy or YY, to avoid
   infringing on user name space.  This should be done even for local
   variables, as they might otherwise be expanded by user macros.
   There are some unavoidable exceptions within include files to
   define necessary library symbols; they are noted "INFRINGES ON
   USER NAME SPACE" below.  */

/* Identify Bison output.  */
#define YYBISON 1

/* Bison version.  */
#define YYBISON_VERSION "3.6.4"

/* Skeleton name.  */
#define YYSKELETON_NAME "yacc.c"

/* Pure parsers.  */
#define YYPURE 1

/* Push parsers.  */
#define YYPUSH 0

/* Pull parsers.  */
#define YYPULL 1

/* Substitute the type names.  */
#define YYSTYPE         TRACEFS_STYPE
/* Substitute the variable and function names.  */
#define yyparse         tracefs_parse
#define yylex           tracefs_lex
#define yyerror         tracefs_error
#define yydebug         tracefs_debug
#define yynerrs         tracefs_nerrs

/* First part of user prologue.  */
#line 1 "sqlhist.y"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "sqlhist-parse.h"

#define scanner sb->scanner

extern int yylex(YYSTYPE *yylval, void *);
extern void yyerror(struct sqlhist_bison *, char *fmt, ...);

#define CHECK_RETURN_PTR(x)					\
	do {							\
		if (!(x)) {					\
			printf("FAILED MEMORY: %s\n", #x);	\
			return -ENOMEM;				\
		}						\
	} while (0)

#define CHECK_RETURN_VAL(x)					\
	do {							\
		if ((x) < 0) {					\
			printf("FAILED MEMORY: %s\n", #x);	\
			return -ENOMEM;				\
		}						\
	} while (0)


#line 108 "sqlhist.tab.c"

# ifndef YY_CAST
#  ifdef __cplusplus
#   define YY_CAST(Type, Val) static_cast<Type> (Val)
#   define YY_REINTERPRET_CAST(Type, Val) reinterpret_cast<Type> (Val)
#  else
#   define YY_CAST(Type, Val) ((Type) (Val))
#   define YY_REINTERPRET_CAST(Type, Val) ((Type) (Val))
#  endif
# endif
# ifndef YY_NULLPTR
#  if defined __cplusplus
#   if 201103L <= __cplusplus
#    define YY_NULLPTR nullptr
#   else
#    define YY_NULLPTR 0
#   endif
#  else
#   define YY_NULLPTR ((void*)0)
#  endif
# endif

/* Use api.header.include to #include this header
   instead of duplicating it here.  */
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

#line 193 "sqlhist.tab.c"

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

#line 211 "sqlhist.tab.c"

#endif /* !YY_TRACEFS_SQLHIST_TAB_H_INCLUDED  */
/* Symbol kind.  */
enum yysymbol_kind_t
{
  YYSYMBOL_YYEMPTY = -2,
  YYSYMBOL_YYEOF = 0,                      /* "end of file"  */
  YYSYMBOL_YYerror = 1,                    /* error  */
  YYSYMBOL_YYUNDEF = 2,                    /* "invalid token"  */
  YYSYMBOL_AS = 3,                         /* AS  */
  YYSYMBOL_SELECT = 4,                     /* SELECT  */
  YYSYMBOL_FROM = 5,                       /* FROM  */
  YYSYMBOL_JOIN = 6,                       /* JOIN  */
  YYSYMBOL_ON = 7,                         /* ON  */
  YYSYMBOL_WHERE = 8,                      /* WHERE  */
  YYSYMBOL_PARSE_ERROR = 9,                /* PARSE_ERROR  */
  YYSYMBOL_CAST = 10,                      /* CAST  */
  YYSYMBOL_NUMBER = 11,                    /* NUMBER  */
  YYSYMBOL_field_type = 12,                /* field_type  */
  YYSYMBOL_STRING = 13,                    /* STRING  */
  YYSYMBOL_FIELD = 14,                     /* FIELD  */
  YYSYMBOL_LE = 15,                        /* LE  */
  YYSYMBOL_GE = 16,                        /* GE  */
  YYSYMBOL_EQ = 17,                        /* EQ  */
  YYSYMBOL_NEQ = 18,                       /* NEQ  */
  YYSYMBOL_AND = 19,                       /* AND  */
  YYSYMBOL_OR = 20,                        /* OR  */
  YYSYMBOL_21_ = 21,                       /* '+'  */
  YYSYMBOL_22_ = 22,                       /* '-'  */
  YYSYMBOL_23_ = 23,                       /* '*'  */
  YYSYMBOL_24_ = 24,                       /* '/'  */
  YYSYMBOL_25_ = 25,                       /* '<'  */
  YYSYMBOL_26_ = 26,                       /* '>'  */
  YYSYMBOL_27_ = 27,                       /* ','  */
  YYSYMBOL_28_ = 28,                       /* '('  */
  YYSYMBOL_29_ = 29,                       /* ')'  */
  YYSYMBOL_30_ = 30,                       /* '='  */
  YYSYMBOL_31_ = 31,                       /* "!="  */
  YYSYMBOL_32_ = 32,                       /* '&'  */
  YYSYMBOL_33_ = 33,                       /* '~'  */
  YYSYMBOL_34_ = 34,                       /* '!'  */
  YYSYMBOL_YYACCEPT = 35,                  /* $accept  */
  YYSYMBOL_start = 36,                     /* start  */
  YYSYMBOL_label = 37,                     /* label  */
  YYSYMBOL_select = 38,                    /* select  */
  YYSYMBOL_select_statement = 39,          /* select_statement  */
  YYSYMBOL_selection_list = 40,            /* selection_list  */
  YYSYMBOL_selection = 41,                 /* selection  */
  YYSYMBOL_selection_expr = 42,            /* selection_expr  */
  YYSYMBOL_selection_addition = 43,        /* selection_addition  */
  YYSYMBOL_item = 44,                      /* item  */
  YYSYMBOL_field = 45,                     /* field  */
  YYSYMBOL_named_field = 46,               /* named_field  */
  YYSYMBOL_name = 47,                      /* name  */
  YYSYMBOL_str_val = 48,                   /* str_val  */
  YYSYMBOL_val = 49,                       /* val  */
  YYSYMBOL_compare = 50,                   /* compare  */
  YYSYMBOL_compare_and_or = 51,            /* compare_and_or  */
  YYSYMBOL_compare_items = 52,             /* compare_items  */
  YYSYMBOL_compare_cmds = 53,              /* compare_cmds  */
  YYSYMBOL_compare_list = 54,              /* compare_list  */
  YYSYMBOL_where_clause = 55,              /* where_clause  */
  YYSYMBOL_opt_where_clause = 56,          /* opt_where_clause  */
  YYSYMBOL_opt_join_clause = 57,           /* opt_join_clause  */
  YYSYMBOL_table_exp = 58,                 /* table_exp  */
  YYSYMBOL_from_clause = 59,               /* from_clause  */
  YYSYMBOL_join_clause = 60,               /* join_clause  */
  YYSYMBOL_match = 61,                     /* match  */
  YYSYMBOL_match_clause = 62               /* match_clause  */
};
typedef enum yysymbol_kind_t yysymbol_kind_t;




#ifdef short
# undef short
#endif

/* On compilers that do not define __PTRDIFF_MAX__ etc., make sure
   <limits.h> and (if available) <stdint.h> are included
   so that the code can choose integer types of a good width.  */

#ifndef __PTRDIFF_MAX__
# include <limits.h> /* INFRINGES ON USER NAME SPACE */
# if defined __STDC_VERSION__ && 199901 <= __STDC_VERSION__
#  include <stdint.h> /* INFRINGES ON USER NAME SPACE */
#  define YY_STDINT_H
# endif
#endif

/* Narrow types that promote to a signed type and that can represent a
   signed or unsigned integer of at least N bits.  In tables they can
   save space and decrease cache pressure.  Promoting to a signed type
   helps avoid bugs in integer arithmetic.  */

#ifdef __INT_LEAST8_MAX__
typedef __INT_LEAST8_TYPE__ yytype_int8;
#elif defined YY_STDINT_H
typedef int_least8_t yytype_int8;
#else
typedef signed char yytype_int8;
#endif

#ifdef __INT_LEAST16_MAX__
typedef __INT_LEAST16_TYPE__ yytype_int16;
#elif defined YY_STDINT_H
typedef int_least16_t yytype_int16;
#else
typedef short yytype_int16;
#endif

#if defined __UINT_LEAST8_MAX__ && __UINT_LEAST8_MAX__ <= __INT_MAX__
typedef __UINT_LEAST8_TYPE__ yytype_uint8;
#elif (!defined __UINT_LEAST8_MAX__ && defined YY_STDINT_H \
       && UINT_LEAST8_MAX <= INT_MAX)
typedef uint_least8_t yytype_uint8;
#elif !defined __UINT_LEAST8_MAX__ && UCHAR_MAX <= INT_MAX
typedef unsigned char yytype_uint8;
#else
typedef short yytype_uint8;
#endif

#if defined __UINT_LEAST16_MAX__ && __UINT_LEAST16_MAX__ <= __INT_MAX__
typedef __UINT_LEAST16_TYPE__ yytype_uint16;
#elif (!defined __UINT_LEAST16_MAX__ && defined YY_STDINT_H \
       && UINT_LEAST16_MAX <= INT_MAX)
typedef uint_least16_t yytype_uint16;
#elif !defined __UINT_LEAST16_MAX__ && USHRT_MAX <= INT_MAX
typedef unsigned short yytype_uint16;
#else
typedef int yytype_uint16;
#endif

#ifndef YYPTRDIFF_T
# if defined __PTRDIFF_TYPE__ && defined __PTRDIFF_MAX__
#  define YYPTRDIFF_T __PTRDIFF_TYPE__
#  define YYPTRDIFF_MAXIMUM __PTRDIFF_MAX__
# elif defined PTRDIFF_MAX
#  ifndef ptrdiff_t
#   include <stddef.h> /* INFRINGES ON USER NAME SPACE */
#  endif
#  define YYPTRDIFF_T ptrdiff_t
#  define YYPTRDIFF_MAXIMUM PTRDIFF_MAX
# else
#  define YYPTRDIFF_T long
#  define YYPTRDIFF_MAXIMUM LONG_MAX
# endif
#endif

#ifndef YYSIZE_T
# ifdef __SIZE_TYPE__
#  define YYSIZE_T __SIZE_TYPE__
# elif defined size_t
#  define YYSIZE_T size_t
# elif defined __STDC_VERSION__ && 199901 <= __STDC_VERSION__
#  include <stddef.h> /* INFRINGES ON USER NAME SPACE */
#  define YYSIZE_T size_t
# else
#  define YYSIZE_T unsigned
# endif
#endif

#define YYSIZE_MAXIMUM                                  \
  YY_CAST (YYPTRDIFF_T,                                 \
           (YYPTRDIFF_MAXIMUM < YY_CAST (YYSIZE_T, -1)  \
            ? YYPTRDIFF_MAXIMUM                         \
            : YY_CAST (YYSIZE_T, -1)))

#define YYSIZEOF(X) YY_CAST (YYPTRDIFF_T, sizeof (X))


/* Stored state numbers (used for stacks). */
typedef yytype_int8 yy_state_t;

/* State numbers in computations.  */
typedef int yy_state_fast_t;

#ifndef YY_
# if defined YYENABLE_NLS && YYENABLE_NLS
#  if ENABLE_NLS
#   include <libintl.h> /* INFRINGES ON USER NAME SPACE */
#   define YY_(Msgid) dgettext ("bison-runtime", Msgid)
#  endif
# endif
# ifndef YY_
#  define YY_(Msgid) Msgid
# endif
#endif


#ifndef YY_ATTRIBUTE_PURE
# if defined __GNUC__ && 2 < __GNUC__ + (96 <= __GNUC_MINOR__)
#  define YY_ATTRIBUTE_PURE __attribute__ ((__pure__))
# else
#  define YY_ATTRIBUTE_PURE
# endif
#endif

#ifndef YY_ATTRIBUTE_UNUSED
# if defined __GNUC__ && 2 < __GNUC__ + (7 <= __GNUC_MINOR__)
#  define YY_ATTRIBUTE_UNUSED __attribute__ ((__unused__))
# else
#  define YY_ATTRIBUTE_UNUSED
# endif
#endif

/* Suppress unused-variable warnings by "using" E.  */
#if ! defined lint || defined __GNUC__
# define YYUSE(E) ((void) (E))
#else
# define YYUSE(E) /* empty */
#endif

#if defined __GNUC__ && ! defined __ICC && 407 <= __GNUC__ * 100 + __GNUC_MINOR__
/* Suppress an incorrect diagnostic about yylval being uninitialized.  */
# define YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN                            \
    _Pragma ("GCC diagnostic push")                                     \
    _Pragma ("GCC diagnostic ignored \"-Wuninitialized\"")              \
    _Pragma ("GCC diagnostic ignored \"-Wmaybe-uninitialized\"")
# define YY_IGNORE_MAYBE_UNINITIALIZED_END      \
    _Pragma ("GCC diagnostic pop")
#else
# define YY_INITIAL_VALUE(Value) Value
#endif
#ifndef YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
# define YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
# define YY_IGNORE_MAYBE_UNINITIALIZED_END
#endif
#ifndef YY_INITIAL_VALUE
# define YY_INITIAL_VALUE(Value) /* Nothing. */
#endif

#if defined __cplusplus && defined __GNUC__ && ! defined __ICC && 6 <= __GNUC__
# define YY_IGNORE_USELESS_CAST_BEGIN                          \
    _Pragma ("GCC diagnostic push")                            \
    _Pragma ("GCC diagnostic ignored \"-Wuseless-cast\"")
# define YY_IGNORE_USELESS_CAST_END            \
    _Pragma ("GCC diagnostic pop")
#endif
#ifndef YY_IGNORE_USELESS_CAST_BEGIN
# define YY_IGNORE_USELESS_CAST_BEGIN
# define YY_IGNORE_USELESS_CAST_END
#endif


#define YY_ASSERT(E) ((void) (0 && (E)))

#if !defined yyoverflow

/* The parser invokes alloca or malloc; define the necessary symbols.  */

# ifdef YYSTACK_USE_ALLOCA
#  if YYSTACK_USE_ALLOCA
#   ifdef __GNUC__
#    define YYSTACK_ALLOC __builtin_alloca
#   elif defined __BUILTIN_VA_ARG_INCR
#    include <alloca.h> /* INFRINGES ON USER NAME SPACE */
#   elif defined _AIX
#    define YYSTACK_ALLOC __alloca
#   elif defined _MSC_VER
#    include <malloc.h> /* INFRINGES ON USER NAME SPACE */
#    define alloca _alloca
#   else
#    define YYSTACK_ALLOC alloca
#    if ! defined _ALLOCA_H && ! defined EXIT_SUCCESS
#     include <stdlib.h> /* INFRINGES ON USER NAME SPACE */
      /* Use EXIT_SUCCESS as a witness for stdlib.h.  */
#     ifndef EXIT_SUCCESS
#      define EXIT_SUCCESS 0
#     endif
#    endif
#   endif
#  endif
# endif

# ifdef YYSTACK_ALLOC
   /* Pacify GCC's 'empty if-body' warning.  */
#  define YYSTACK_FREE(Ptr) do { /* empty */; } while (0)
#  ifndef YYSTACK_ALLOC_MAXIMUM
    /* The OS might guarantee only one guard page at the bottom of the stack,
       and a page size can be as small as 4096 bytes.  So we cannot safely
       invoke alloca (N) if N exceeds 4096.  Use a slightly smaller number
       to allow for a few compiler-allocated temporary stack slots.  */
#   define YYSTACK_ALLOC_MAXIMUM 4032 /* reasonable circa 2006 */
#  endif
# else
#  define YYSTACK_ALLOC YYMALLOC
#  define YYSTACK_FREE YYFREE
#  ifndef YYSTACK_ALLOC_MAXIMUM
#   define YYSTACK_ALLOC_MAXIMUM YYSIZE_MAXIMUM
#  endif
#  if (defined __cplusplus && ! defined EXIT_SUCCESS \
       && ! ((defined YYMALLOC || defined malloc) \
             && (defined YYFREE || defined free)))
#   include <stdlib.h> /* INFRINGES ON USER NAME SPACE */
#   ifndef EXIT_SUCCESS
#    define EXIT_SUCCESS 0
#   endif
#  endif
#  ifndef YYMALLOC
#   define YYMALLOC malloc
#   if ! defined malloc && ! defined EXIT_SUCCESS
void *malloc (YYSIZE_T); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
#  ifndef YYFREE
#   define YYFREE free
#   if ! defined free && ! defined EXIT_SUCCESS
void free (void *); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
# endif
#endif /* !defined yyoverflow */

#if (! defined yyoverflow \
     && (! defined __cplusplus \
         || (defined TRACEFS_STYPE_IS_TRIVIAL && TRACEFS_STYPE_IS_TRIVIAL)))

/* A type that is properly aligned for any stack member.  */
union yyalloc
{
  yy_state_t yyss_alloc;
  YYSTYPE yyvs_alloc;
};

/* The size of the maximum gap between one aligned stack and the next.  */
# define YYSTACK_GAP_MAXIMUM (YYSIZEOF (union yyalloc) - 1)

/* The size of an array large to enough to hold all stacks, each with
   N elements.  */
# define YYSTACK_BYTES(N) \
     ((N) * (YYSIZEOF (yy_state_t) + YYSIZEOF (YYSTYPE)) \
      + YYSTACK_GAP_MAXIMUM)

# define YYCOPY_NEEDED 1

/* Relocate STACK from its old location to the new one.  The
   local variables YYSIZE and YYSTACKSIZE give the old and new number of
   elements in the stack, and YYPTR gives the new location of the
   stack.  Advance YYPTR to a properly aligned location for the next
   stack.  */
# define YYSTACK_RELOCATE(Stack_alloc, Stack)                           \
    do                                                                  \
      {                                                                 \
        YYPTRDIFF_T yynewbytes;                                         \
        YYCOPY (&yyptr->Stack_alloc, Stack, yysize);                    \
        Stack = &yyptr->Stack_alloc;                                    \
        yynewbytes = yystacksize * YYSIZEOF (*Stack) + YYSTACK_GAP_MAXIMUM; \
        yyptr += yynewbytes / YYSIZEOF (*yyptr);                        \
      }                                                                 \
    while (0)

#endif

#if defined YYCOPY_NEEDED && YYCOPY_NEEDED
/* Copy COUNT objects from SRC to DST.  The source and destination do
   not overlap.  */
# ifndef YYCOPY
#  if defined __GNUC__ && 1 < __GNUC__
#   define YYCOPY(Dst, Src, Count) \
      __builtin_memcpy (Dst, Src, YY_CAST (YYSIZE_T, (Count)) * sizeof (*(Src)))
#  else
#   define YYCOPY(Dst, Src, Count)              \
      do                                        \
        {                                       \
          YYPTRDIFF_T yyi;                      \
          for (yyi = 0; yyi < (Count); yyi++)   \
            (Dst)[yyi] = (Src)[yyi];            \
        }                                       \
      while (0)
#  endif
# endif
#endif /* !YYCOPY_NEEDED */

/* YYFINAL -- State number of the termination state.  */
#define YYFINAL  5
/* YYLAST -- Last index in YYTABLE.  */
#define YYLAST   104

/* YYNTOKENS -- Number of terminals.  */
#define YYNTOKENS  35
/* YYNNTS -- Number of nonterminals.  */
#define YYNNTS  28
/* YYNRULES -- Number of rules.  */
#define YYNRULES  61
/* YYNSTATES -- Number of states.  */
#define YYNSTATES  111

#define YYMAXUTOK   276


/* YYTRANSLATE(TOKEN-NUM) -- Symbol number corresponding to TOKEN-NUM
   as returned by yylex, with out-of-bounds checking.  */
#define YYTRANSLATE(YYX)                                \
  (0 <= (YYX) && (YYX) <= YYMAXUTOK                     \
   ? YY_CAST (yysymbol_kind_t, yytranslate[YYX])        \
   : YYSYMBOL_YYUNDEF)

/* YYTRANSLATE[TOKEN-NUM] -- Symbol number corresponding to TOKEN-NUM
   as returned by yylex.  */
static const yytype_int8 yytranslate[] =
{
       0,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,    34,     2,     2,     2,     2,    32,     2,
      28,    29,    23,    21,    27,    22,     2,    24,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
      25,    30,    26,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,    33,     2,     2,     2,
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
      15,    16,    17,    18,    19,    20,    31
};

#if TRACEFS_DEBUG
  /* YYRLINE[YYN] -- Source line where rule number YYN was defined.  */
static const yytype_uint8 yyrline[] =
{
       0,    75,    75,    78,    79,    82,    86,    90,    91,    95,
      99,   106,   107,   108,   109,   110,   117,   122,   130,   131,
     135,   139,   143,   147,   151,   152,   157,   158,   159,   160,
     161,   162,   163,   164,   165,   166,   170,   171,   172,   173,
     174,   178,   179,   180,   181,   182,   186,   195,   196,   197,
     201,   204,   206,   209,   211,   215,   219,   234,   238,   239,
     244,   245
};
#endif

/** Accessing symbol of state STATE.  */
#define YY_ACCESSING_SYMBOL(State) YY_CAST (yysymbol_kind_t, yystos[State])

#if TRACEFS_DEBUG || 0
/* The user-facing name of the symbol whose (internal) number is
   YYSYMBOL.  No bounds checking.  */
static const char *yysymbol_name (yysymbol_kind_t yysymbol) YY_ATTRIBUTE_UNUSED;

/* YYTNAME[SYMBOL-NUM] -- String name of the symbol SYMBOL-NUM.
   First, the terminals, then, starting at YYNTOKENS, nonterminals.  */
static const char *const yytname[] =
{
  "\"end of file\"", "error", "\"invalid token\"", "AS", "SELECT", "FROM",
  "JOIN", "ON", "WHERE", "PARSE_ERROR", "CAST", "NUMBER", "field_type",
  "STRING", "FIELD", "LE", "GE", "EQ", "NEQ", "AND", "OR", "'+'", "'-'",
  "'*'", "'/'", "'<'", "'>'", "','", "'('", "')'", "'='", "\"!=\"", "'&'",
  "'~'", "'!'", "$accept", "start", "label", "select", "select_statement",
  "selection_list", "selection", "selection_expr", "selection_addition",
  "item", "field", "named_field", "name", "str_val", "val", "compare",
  "compare_and_or", "compare_items", "compare_cmds", "compare_list",
  "where_clause", "opt_where_clause", "opt_join_clause", "table_exp",
  "from_clause", "join_clause", "match", "match_clause", YY_NULLPTR
};

static const char *
yysymbol_name (yysymbol_kind_t yysymbol)
{
  return yytname[yysymbol];
}
#endif

#ifdef YYPRINT
/* YYTOKNUM[NUM] -- (External) token number corresponding to the
   (internal) symbol number NUM (which must be that of a token).  */
static const yytype_int16 yytoknum[] =
{
       0,   256,   257,   258,   259,   260,   261,   262,   263,   264,
     265,   266,   267,   268,   269,   270,   271,   272,   273,   274,
     275,    43,    45,    42,    47,    60,    62,    44,    40,    41,
      61,   276,    38,   126,    33
};
#endif

#define YYPACT_NINF (-58)

#define yypact_value_is_default(Yyn) \
  ((Yyn) == YYPACT_NINF)

#define YYTABLE_NINF (-1)

#define yytable_value_is_error(Yyn) \
  0

  /* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
     STATE-NUM.  */
static const yytype_int8 yypact[] =
{
       4,   -58,    13,    11,   -58,   -58,     5,   -58,    43,    53,
      45,    29,   -58,    19,    43,    44,    32,    60,   -58,    73,
      11,    75,   -58,   -58,   -58,    43,    43,    87,   -58,   -58,
      29,   -58,   -58,   -58,    60,    83,   -58,   -58,   -58,   -58,
     -58,    78,   -58,    86,    14,   -58,   -58,    65,    60,    -4,
     -12,    34,   -58,    76,   -15,   -58,   -58,   -10,    68,   -58,
       1,   -58,    18,    -4,   -58,    33,    33,    33,    33,    33,
      33,    33,    33,    33,    84,    14,    14,    14,    60,    60,
      60,    -4,   -58,    -4,    -4,   -58,    49,   -58,   -58,   -58,
     -58,   -58,   -58,   -58,   -58,   -58,   -58,   -58,   -58,   -58,
     -58,   -58,   -58,   -58,   -58,   -58,    51,   -58,   -58,   -58,
     -58
};

  /* YYDEFACT[STATE-NUM] -- Default reduction number in state STATE-NUM.
     Performed when YYTABLE does not specify something else to do.  Zero
     means the default is an error.  */
static const yytype_int8 yydefact[] =
{
       0,     5,     0,     0,     2,     1,     0,    20,     0,     0,
       7,     9,    13,    11,     0,     0,     0,     0,     6,    53,
       0,     0,    22,    10,     4,     0,     0,     0,    14,    12,
      20,    56,    19,    18,     0,    51,    54,     8,     3,    16,
      17,     0,    21,     0,     0,    52,    55,     0,     0,     0,
       0,     0,    45,    46,    47,    50,    15,     0,    60,    57,
       0,    40,     0,     0,    44,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    39,     0,     0,    42,     0,    25,    23,    24,
      28,    29,    31,    32,    26,    27,    30,    33,    34,    35,
      41,    49,    48,    59,    58,    61,     0,    37,    36,    43,
      38
};

  /* YYPGOTO[NTERM-NUM].  */
static const yytype_int8 yypgoto[] =
{
     -58,   -58,    69,   -58,   -58,    80,   -58,   -58,    90,   -16,
      -3,   -58,    81,    27,    15,   -41,   -57,    28,   -58,   -21,
     -58,   -58,   -58,   -58,   -58,   -58,   -58,    24
};

  /* YYDEFGOTO[NTERM-NUM].  */
static const yytype_int8 yydefgoto[] =
{
      -1,     2,    23,     3,     4,     9,    10,    11,    12,    57,
      51,    33,    24,    89,    90,    61,    62,    53,    54,    55,
      45,    46,    35,    18,    19,    36,    58,    59
};

  /* YYTABLE[YYPACT[STATE-NUM]] -- What to do in state STATE-NUM.  If
     positive, shift that token.  If negative, reduce the rule whose
     number is the opposite.  If YYTABLE_NINF, syntax error.  */
static const yytype_int8 yytable[] =
{
      13,    31,     7,    52,    76,    16,    86,    78,     1,    64,
       7,    27,    77,     5,    32,     7,    63,    13,    43,    82,
      79,     6,    39,    40,   106,     7,   107,   108,     7,    81,
      60,    32,    21,    14,    52,    52,    52,    83,    84,     8,
      25,    26,    49,    22,    87,    32,    88,    85,    50,    65,
      66,    67,    68,    25,    26,   101,   102,     7,    17,    69,
      70,    29,   103,   104,    71,    72,    73,    74,    83,    84,
      83,    84,    20,    28,    30,    32,    32,    32,   109,    34,
     110,    91,    92,    93,    94,    95,    96,    97,    98,    22,
      41,    44,    47,    48,    56,    80,    75,    88,    15,    42,
      37,    99,    38,   100,   105
};

static const yytype_int8 yycheck[] =
{
       3,    17,    14,    44,    19,     8,    63,    17,     4,    50,
      14,    14,    27,     0,    17,    14,    28,    20,    34,    60,
      30,    10,    25,    26,    81,    14,    83,    84,    14,    28,
      34,    34,     3,    28,    75,    76,    77,    19,    20,    28,
      21,    22,    28,    14,    11,    48,    13,    29,    34,    15,
      16,    17,    18,    21,    22,    76,    77,    14,     5,    25,
      26,    29,    78,    79,    30,    31,    32,    33,    19,    20,
      19,    20,    27,    29,    14,    78,    79,    80,    29,     6,
      29,    66,    67,    68,    69,    70,    71,    72,    73,    14,
       3,     8,    14,     7,    29,    27,    20,    13,     8,    30,
      20,    74,    21,    75,    80
};

  /* YYSTOS[STATE-NUM] -- The (internal number of the) accessing
     symbol of state STATE-NUM.  */
static const yytype_int8 yystos[] =
{
       0,     4,    36,    38,    39,     0,    10,    14,    28,    40,
      41,    42,    43,    45,    28,    43,    45,     5,    58,    59,
      27,     3,    14,    37,    47,    21,    22,    45,    29,    29,
      14,    44,    45,    46,     6,    57,    60,    40,    47,    45,
      45,     3,    37,    44,     8,    55,    56,    14,     7,    28,
      34,    45,    50,    52,    53,    54,    29,    44,    61,    62,
      34,    50,    51,    28,    50,    15,    16,    17,    18,    25,
      26,    30,    31,    32,    33,    20,    19,    27,    17,    30,
      27,    28,    50,    19,    20,    29,    51,    11,    13,    48,
      49,    49,    49,    49,    49,    49,    49,    49,    49,    48,
      52,    54,    54,    44,    44,    62,    51,    51,    51,    29,
      29
};

  /* YYR1[YYN] -- Symbol number of symbol that rule YYN derives.  */
static const yytype_int8 yyr1[] =
{
       0,    35,    36,    37,    37,    38,    39,    40,    40,    41,
      41,    42,    42,    42,    42,    42,    43,    43,    44,    44,
      45,    46,    47,    48,    49,    49,    50,    50,    50,    50,
      50,    50,    50,    50,    50,    50,    51,    51,    51,    51,
      51,    52,    52,    52,    52,    52,    53,    54,    54,    54,
      55,    56,    56,    57,    57,    58,    59,    60,    61,    61,
      62,    62
};

  /* YYR2[YYN] -- Number of symbols on the right hand side of rule YYN.  */
static const yytype_int8 yyr2[] =
{
       0,     2,     1,     2,     1,     1,     3,     1,     3,     1,
       2,     1,     3,     1,     3,     6,     3,     3,     1,     1,
       1,     2,     1,     1,     1,     1,     3,     3,     3,     3,
       3,     3,     3,     3,     3,     3,     3,     3,     4,     2,
       1,     3,     3,     4,     2,     1,     1,     1,     3,     3,
       2,     0,     1,     0,     1,     3,     2,     4,     3,     3,
       1,     3
};


enum { YYENOMEM = -2 };

#define yyerrok         (yyerrstatus = 0)
#define yyclearin       (yychar = TRACEFS_EMPTY)

#define YYACCEPT        goto yyacceptlab
#define YYABORT         goto yyabortlab
#define YYERROR         goto yyerrorlab


#define YYRECOVERING()  (!!yyerrstatus)

#define YYBACKUP(Token, Value)                                    \
  do                                                              \
    if (yychar == TRACEFS_EMPTY)                                        \
      {                                                           \
        yychar = (Token);                                         \
        yylval = (Value);                                         \
        YYPOPSTACK (yylen);                                       \
        yystate = *yyssp;                                         \
        goto yybackup;                                            \
      }                                                           \
    else                                                          \
      {                                                           \
        yyerror (sb, YY_("syntax error: cannot back up")); \
        YYERROR;                                                  \
      }                                                           \
  while (0)

/* Backward compatibility with an undocumented macro.
   Use TRACEFS_error or TRACEFS_UNDEF. */
#define YYERRCODE TRACEFS_UNDEF


/* Enable debugging if requested.  */
#if TRACEFS_DEBUG

# ifndef YYFPRINTF
#  include <stdio.h> /* INFRINGES ON USER NAME SPACE */
#  define YYFPRINTF fprintf
# endif

# define YYDPRINTF(Args)                        \
do {                                            \
  if (yydebug)                                  \
    YYFPRINTF Args;                             \
} while (0)

/* This macro is provided for backward compatibility. */
# ifndef YY_LOCATION_PRINT
#  define YY_LOCATION_PRINT(File, Loc) ((void) 0)
# endif


# define YY_SYMBOL_PRINT(Title, Kind, Value, Location)                    \
do {                                                                      \
  if (yydebug)                                                            \
    {                                                                     \
      YYFPRINTF (stderr, "%s ", Title);                                   \
      yy_symbol_print (stderr,                                            \
                  Kind, Value, sb); \
      YYFPRINTF (stderr, "\n");                                           \
    }                                                                     \
} while (0)


/*-----------------------------------.
| Print this symbol's value on YYO.  |
`-----------------------------------*/

static void
yy_symbol_value_print (FILE *yyo,
                       yysymbol_kind_t yykind, YYSTYPE const * const yyvaluep, struct sqlhist_bison *sb)
{
  FILE *yyoutput = yyo;
  YYUSE (yyoutput);
  YYUSE (sb);
  if (!yyvaluep)
    return;
# ifdef YYPRINT
  if (yykind < YYNTOKENS)
    YYPRINT (yyo, yytoknum[yykind], *yyvaluep);
# endif
  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  YYUSE (yykind);
  YY_IGNORE_MAYBE_UNINITIALIZED_END
}


/*---------------------------.
| Print this symbol on YYO.  |
`---------------------------*/

static void
yy_symbol_print (FILE *yyo,
                 yysymbol_kind_t yykind, YYSTYPE const * const yyvaluep, struct sqlhist_bison *sb)
{
  YYFPRINTF (yyo, "%s %s (",
             yykind < YYNTOKENS ? "token" : "nterm", yysymbol_name (yykind));

  yy_symbol_value_print (yyo, yykind, yyvaluep, sb);
  YYFPRINTF (yyo, ")");
}

/*------------------------------------------------------------------.
| yy_stack_print -- Print the state stack from its BOTTOM up to its |
| TOP (included).                                                   |
`------------------------------------------------------------------*/

static void
yy_stack_print (yy_state_t *yybottom, yy_state_t *yytop)
{
  YYFPRINTF (stderr, "Stack now");
  for (; yybottom <= yytop; yybottom++)
    {
      int yybot = *yybottom;
      YYFPRINTF (stderr, " %d", yybot);
    }
  YYFPRINTF (stderr, "\n");
}

# define YY_STACK_PRINT(Bottom, Top)                            \
do {                                                            \
  if (yydebug)                                                  \
    yy_stack_print ((Bottom), (Top));                           \
} while (0)


/*------------------------------------------------.
| Report that the YYRULE is going to be reduced.  |
`------------------------------------------------*/

static void
yy_reduce_print (yy_state_t *yyssp, YYSTYPE *yyvsp,
                 int yyrule, struct sqlhist_bison *sb)
{
  int yylno = yyrline[yyrule];
  int yynrhs = yyr2[yyrule];
  int yyi;
  YYFPRINTF (stderr, "Reducing stack by rule %d (line %d):\n",
             yyrule - 1, yylno);
  /* The symbols being reduced.  */
  for (yyi = 0; yyi < yynrhs; yyi++)
    {
      YYFPRINTF (stderr, "   $%d = ", yyi + 1);
      yy_symbol_print (stderr,
                       YY_ACCESSING_SYMBOL (+yyssp[yyi + 1 - yynrhs]),
                       &yyvsp[(yyi + 1) - (yynrhs)], sb);
      YYFPRINTF (stderr, "\n");
    }
}

# define YY_REDUCE_PRINT(Rule)          \
do {                                    \
  if (yydebug)                          \
    yy_reduce_print (yyssp, yyvsp, Rule, sb); \
} while (0)

/* Nonzero means print parse trace.  It is left uninitialized so that
   multiple parsers can coexist.  */
int yydebug;
#else /* !TRACEFS_DEBUG */
# define YYDPRINTF(Args) ((void) 0)
# define YY_SYMBOL_PRINT(Title, Kind, Value, Location)
# define YY_STACK_PRINT(Bottom, Top)
# define YY_REDUCE_PRINT(Rule)
#endif /* !TRACEFS_DEBUG */


/* YYINITDEPTH -- initial size of the parser's stacks.  */
#ifndef YYINITDEPTH
# define YYINITDEPTH 200
#endif

/* YYMAXDEPTH -- maximum size the stacks can grow to (effective only
   if the built-in stack extension method is used).

   Do not make this value too large; the results are undefined if
   YYSTACK_ALLOC_MAXIMUM < YYSTACK_BYTES (YYMAXDEPTH)
   evaluated with infinite-precision integer arithmetic.  */

#ifndef YYMAXDEPTH
# define YYMAXDEPTH 10000
#endif






/*-----------------------------------------------.
| Release the memory associated to this symbol.  |
`-----------------------------------------------*/

static void
yydestruct (const char *yymsg,
            yysymbol_kind_t yykind, YYSTYPE *yyvaluep, struct sqlhist_bison *sb)
{
  YYUSE (yyvaluep);
  YYUSE (sb);
  if (!yymsg)
    yymsg = "Deleting";
  YY_SYMBOL_PRINT (yymsg, yykind, yyvaluep, yylocationp);

  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  YYUSE (yykind);
  YY_IGNORE_MAYBE_UNINITIALIZED_END
}






/*----------.
| yyparse.  |
`----------*/

int
yyparse (struct sqlhist_bison *sb)
{
/* The lookahead symbol.  */
int yychar;


/* The semantic value of the lookahead symbol.  */
/* Default value used for initialization, for pacifying older GCCs
   or non-GCC compilers.  */
YY_INITIAL_VALUE (static YYSTYPE yyval_default;)
YYSTYPE yylval YY_INITIAL_VALUE (= yyval_default);

    /* Number of syntax errors so far.  */
    int yynerrs;

    yy_state_fast_t yystate;
    /* Number of tokens to shift before error messages enabled.  */
    int yyerrstatus;

    /* The stacks and their tools:
       'yyss': related to states.
       'yyvs': related to semantic values.

       Refer to the stacks through separate pointers, to allow yyoverflow
       to reallocate them elsewhere.  */

    /* Their size.  */
    YYPTRDIFF_T yystacksize;

    /* The state stack.  */
    yy_state_t yyssa[YYINITDEPTH];
    yy_state_t *yyss;
    yy_state_t *yyssp;

    /* The semantic value stack.  */
    YYSTYPE yyvsa[YYINITDEPTH];
    YYSTYPE *yyvs;
    YYSTYPE *yyvsp;

  int yyn;
  /* The return value of yyparse.  */
  int yyresult;
  /* Lookahead token as an internal (translated) token number.  */
  yysymbol_kind_t yytoken = YYSYMBOL_YYEMPTY;
  /* The variables used to return semantic value and location from the
     action routines.  */
  YYSTYPE yyval;



#define YYPOPSTACK(N)   (yyvsp -= (N), yyssp -= (N))

  /* The number of symbols on the RHS of the reduced rule.
     Keep to zero when no symbol should be popped.  */
  int yylen = 0;

  yynerrs = 0;
  yystate = 0;
  yyerrstatus = 0;

  yystacksize = YYINITDEPTH;
  yyssp = yyss = yyssa;
  yyvsp = yyvs = yyvsa;


  YYDPRINTF ((stderr, "Starting parse\n"));

  yychar = TRACEFS_EMPTY; /* Cause a token to be read.  */
  goto yysetstate;


/*------------------------------------------------------------.
| yynewstate -- push a new state, which is found in yystate.  |
`------------------------------------------------------------*/
yynewstate:
  /* In all cases, when you get here, the value and location stacks
     have just been pushed.  So pushing a state here evens the stacks.  */
  yyssp++;


/*--------------------------------------------------------------------.
| yysetstate -- set current state (the top of the stack) to yystate.  |
`--------------------------------------------------------------------*/
yysetstate:
  YYDPRINTF ((stderr, "Entering state %d\n", yystate));
  YY_ASSERT (0 <= yystate && yystate < YYNSTATES);
  YY_IGNORE_USELESS_CAST_BEGIN
  *yyssp = YY_CAST (yy_state_t, yystate);
  YY_IGNORE_USELESS_CAST_END
  YY_STACK_PRINT (yyss, yyssp);

  if (yyss + yystacksize - 1 <= yyssp)
#if !defined yyoverflow && !defined YYSTACK_RELOCATE
    goto yyexhaustedlab;
#else
    {
      /* Get the current used size of the three stacks, in elements.  */
      YYPTRDIFF_T yysize = yyssp - yyss + 1;

# if defined yyoverflow
      {
        /* Give user a chance to reallocate the stack.  Use copies of
           these so that the &'s don't force the real ones into
           memory.  */
        yy_state_t *yyss1 = yyss;
        YYSTYPE *yyvs1 = yyvs;

        /* Each stack pointer address is followed by the size of the
           data in use in that stack, in bytes.  This used to be a
           conditional around just the two extra args, but that might
           be undefined if yyoverflow is a macro.  */
        yyoverflow (YY_("memory exhausted"),
                    &yyss1, yysize * YYSIZEOF (*yyssp),
                    &yyvs1, yysize * YYSIZEOF (*yyvsp),
                    &yystacksize);
        yyss = yyss1;
        yyvs = yyvs1;
      }
# else /* defined YYSTACK_RELOCATE */
      /* Extend the stack our own way.  */
      if (YYMAXDEPTH <= yystacksize)
        goto yyexhaustedlab;
      yystacksize *= 2;
      if (YYMAXDEPTH < yystacksize)
        yystacksize = YYMAXDEPTH;

      {
        yy_state_t *yyss1 = yyss;
        union yyalloc *yyptr =
          YY_CAST (union yyalloc *,
                   YYSTACK_ALLOC (YY_CAST (YYSIZE_T, YYSTACK_BYTES (yystacksize))));
        if (! yyptr)
          goto yyexhaustedlab;
        YYSTACK_RELOCATE (yyss_alloc, yyss);
        YYSTACK_RELOCATE (yyvs_alloc, yyvs);
#  undef YYSTACK_RELOCATE
        if (yyss1 != yyssa)
          YYSTACK_FREE (yyss1);
      }
# endif

      yyssp = yyss + yysize - 1;
      yyvsp = yyvs + yysize - 1;

      YY_IGNORE_USELESS_CAST_BEGIN
      YYDPRINTF ((stderr, "Stack size increased to %ld\n",
                  YY_CAST (long, yystacksize)));
      YY_IGNORE_USELESS_CAST_END

      if (yyss + yystacksize - 1 <= yyssp)
        YYABORT;
    }
#endif /* !defined yyoverflow && !defined YYSTACK_RELOCATE */

  if (yystate == YYFINAL)
    YYACCEPT;

  goto yybackup;


/*-----------.
| yybackup.  |
`-----------*/
yybackup:
  /* Do appropriate processing given the current state.  Read a
     lookahead token if we need one and don't already have one.  */

  /* First try to decide what to do without reference to lookahead token.  */
  yyn = yypact[yystate];
  if (yypact_value_is_default (yyn))
    goto yydefault;

  /* Not known => get a lookahead token if don't already have one.  */

  /* YYCHAR is either empty, or end-of-input, or a valid lookahead.  */
  if (yychar == TRACEFS_EMPTY)
    {
      YYDPRINTF ((stderr, "Reading a token\n"));
      yychar = yylex (&yylval, scanner);
    }

  if (yychar <= TRACEFS_EOF)
    {
      yychar = TRACEFS_EOF;
      yytoken = YYSYMBOL_YYEOF;
      YYDPRINTF ((stderr, "Now at end of input.\n"));
    }
  else if (yychar == TRACEFS_error)
    {
      /* The scanner already issued an error message, process directly
         to error recovery.  But do not keep the error token as
         lookahead, it is too special and may lead us to an endless
         loop in error recovery. */
      yychar = TRACEFS_UNDEF;
      yytoken = YYSYMBOL_YYerror;
      goto yyerrlab1;
    }
  else
    {
      yytoken = YYTRANSLATE (yychar);
      YY_SYMBOL_PRINT ("Next token is", yytoken, &yylval, &yylloc);
    }

  /* If the proper action on seeing token YYTOKEN is to reduce or to
     detect an error, take that action.  */
  yyn += yytoken;
  if (yyn < 0 || YYLAST < yyn || yycheck[yyn] != yytoken)
    goto yydefault;
  yyn = yytable[yyn];
  if (yyn <= 0)
    {
      if (yytable_value_is_error (yyn))
        goto yyerrlab;
      yyn = -yyn;
      goto yyreduce;
    }

  /* Count tokens shifted since error; after three, turn off error
     status.  */
  if (yyerrstatus)
    yyerrstatus--;

  /* Shift the lookahead token.  */
  YY_SYMBOL_PRINT ("Shifting", yytoken, &yylval, &yylloc);
  yystate = yyn;
  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  *++yyvsp = yylval;
  YY_IGNORE_MAYBE_UNINITIALIZED_END

  /* Discard the shifted token.  */
  yychar = TRACEFS_EMPTY;
  goto yynewstate;


/*-----------------------------------------------------------.
| yydefault -- do the default action for the current state.  |
`-----------------------------------------------------------*/
yydefault:
  yyn = yydefact[yystate];
  if (yyn == 0)
    goto yyerrlab;
  goto yyreduce;


/*-----------------------------.
| yyreduce -- do a reduction.  |
`-----------------------------*/
yyreduce:
  /* yyn is the number of a rule to reduce with.  */
  yylen = yyr2[yyn];

  /* If YYLEN is nonzero, implement the default value of the action:
     '$$ = $1'.

     Otherwise, the following line sets YYVAL to garbage.
     This behavior is undocumented and Bison
     users should not rely upon it.  Assigning to YYVAL
     unconditionally makes the parser a bit smaller, and it avoids a
     GCC warning that YYVAL may be used uninitialized.  */
  yyval = yyvsp[1-yylen];


  YY_REDUCE_PRINT (yyn);
  switch (yyn)
    {
  case 3:
#line 78 "sqlhist.y"
                { CHECK_RETURN_PTR((yyval.string) = store_str(sb, (yyvsp[0].string))); }
#line 1328 "sqlhist.tab.c"
    break;

  case 4:
#line 79 "sqlhist.y"
        { CHECK_RETURN_PTR((yyval.string) = store_str(sb, (yyvsp[0].string))); }
#line 1334 "sqlhist.tab.c"
    break;

  case 5:
#line 82 "sqlhist.y"
                 { table_start(sb); }
#line 1340 "sqlhist.tab.c"
    break;

  case 9:
#line 96 "sqlhist.y"
                                {
					CHECK_RETURN_VAL(add_selection(sb, (yyvsp[0].expr), NULL));
				}
#line 1348 "sqlhist.tab.c"
    break;

  case 10:
#line 100 "sqlhist.y"
                                {
					CHECK_RETURN_VAL(add_selection(sb, (yyvsp[-1].expr), (yyvsp[0].string)));
				}
#line 1356 "sqlhist.tab.c"
    break;

  case 12:
#line 107 "sqlhist.y"
                                {  (yyval.expr) = (yyvsp[-1].expr); }
#line 1362 "sqlhist.tab.c"
    break;

  case 14:
#line 109 "sqlhist.y"
                                {  (yyval.expr) = (yyvsp[-1].expr); }
#line 1368 "sqlhist.tab.c"
    break;

  case 15:
#line 110 "sqlhist.y"
                                {
					 (yyval.expr) = add_cast(sb, (yyvsp[-3].expr), (yyvsp[-1].string));
					 CHECK_RETURN_PTR((yyval.expr));
				}
#line 1377 "sqlhist.tab.c"
    break;

  case 16:
#line 118 "sqlhist.y"
                                {
					(yyval.expr) = add_compare(sb, (yyvsp[-2].expr), (yyvsp[0].expr), COMPARE_ADD);
					CHECK_RETURN_PTR((yyval.expr));
				}
#line 1386 "sqlhist.tab.c"
    break;

  case 17:
#line 123 "sqlhist.y"
                                {
					(yyval.expr) = add_compare(sb, (yyvsp[-2].expr), (yyvsp[0].expr), COMPARE_SUB);
					CHECK_RETURN_PTR((yyval.expr));
				}
#line 1395 "sqlhist.tab.c"
    break;

  case 20:
#line 135 "sqlhist.y"
                { (yyval.expr) = add_field(sb, (yyvsp[0].string), NULL); CHECK_RETURN_PTR((yyval.expr)); }
#line 1401 "sqlhist.tab.c"
    break;

  case 21:
#line 139 "sqlhist.y"
               { (yyval.expr) = add_field(sb, (yyvsp[-1].string), (yyvsp[0].string)); CHECK_RETURN_PTR((yyval.expr)); }
#line 1407 "sqlhist.tab.c"
    break;

  case 23:
#line 147 "sqlhist.y"
                { (yyval.expr) = add_string(sb, (yyvsp[0].string)); CHECK_RETURN_PTR((yyval.expr)); }
#line 1413 "sqlhist.tab.c"
    break;

  case 25:
#line 152 "sqlhist.y"
                { (yyval.expr) = add_number(sb, (yyvsp[0].number)); CHECK_RETURN_PTR((yyval.expr)); }
#line 1419 "sqlhist.tab.c"
    break;

  case 26:
#line 157 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_LT); CHECK_RETURN_PTR((yyval.expr)); }
#line 1425 "sqlhist.tab.c"
    break;

  case 27:
#line 158 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_GT); CHECK_RETURN_PTR((yyval.expr)); }
#line 1431 "sqlhist.tab.c"
    break;

  case 28:
#line 159 "sqlhist.y"
                { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_LE); CHECK_RETURN_PTR((yyval.expr)); }
#line 1437 "sqlhist.tab.c"
    break;

  case 29:
#line 160 "sqlhist.y"
                { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_GE); CHECK_RETURN_PTR((yyval.expr)); }
#line 1443 "sqlhist.tab.c"
    break;

  case 30:
#line 161 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_EQ); CHECK_RETURN_PTR((yyval.expr)); }
#line 1449 "sqlhist.tab.c"
    break;

  case 31:
#line 162 "sqlhist.y"
                { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_EQ); CHECK_RETURN_PTR((yyval.expr)); }
#line 1455 "sqlhist.tab.c"
    break;

  case 32:
#line 163 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_NE); CHECK_RETURN_PTR((yyval.expr)); }
#line 1461 "sqlhist.tab.c"
    break;

  case 33:
#line 164 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_NE); CHECK_RETURN_PTR((yyval.expr)); }
#line 1467 "sqlhist.tab.c"
    break;

  case 34:
#line 165 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_BIN_AND); CHECK_RETURN_PTR((yyval.expr)); }
#line 1473 "sqlhist.tab.c"
    break;

  case 35:
#line 166 "sqlhist.y"
                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_STR_CMP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1479 "sqlhist.tab.c"
    break;

  case 36:
#line 170 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_OR); CHECK_RETURN_PTR((yyval.expr)); }
#line 1485 "sqlhist.tab.c"
    break;

  case 37:
#line 171 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_AND); CHECK_RETURN_PTR((yyval.expr)); }
#line 1491 "sqlhist.tab.c"
    break;

  case 38:
#line 172 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-1].expr), NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1497 "sqlhist.tab.c"
    break;

  case 39:
#line 173 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[0].expr), NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1503 "sqlhist.tab.c"
    break;

  case 41:
#line 178 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-2].expr), (yyvsp[0].expr), FILTER_OR); CHECK_RETURN_PTR((yyval.expr)); }
#line 1509 "sqlhist.tab.c"
    break;

  case 42:
#line 179 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-1].expr), NULL, FILTER_GROUP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1515 "sqlhist.tab.c"
    break;

  case 43:
#line 180 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[-1].expr), NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1521 "sqlhist.tab.c"
    break;

  case 44:
#line 181 "sqlhist.y"
                                        { (yyval.expr) = add_filter(sb, (yyvsp[0].expr), NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR((yyval.expr)); }
#line 1527 "sqlhist.tab.c"
    break;

  case 46:
#line 186 "sqlhist.y"
                                { CHECK_RETURN_VAL(add_where(sb, (yyvsp[0].expr))); }
#line 1533 "sqlhist.tab.c"
    break;

  case 56:
#line 219 "sqlhist.y"
                        { CHECK_RETURN_VAL(add_from(sb, (yyvsp[0].expr))); }
#line 1539 "sqlhist.tab.c"
    break;

  case 57:
#line 234 "sqlhist.y"
                                { add_to(sb, (yyvsp[-2].expr)); }
#line 1545 "sqlhist.tab.c"
    break;

  case 58:
#line 238 "sqlhist.y"
                 { CHECK_RETURN_VAL(add_match(sb, (yyvsp[-2].expr), (yyvsp[0].expr))); }
#line 1551 "sqlhist.tab.c"
    break;

  case 59:
#line 239 "sqlhist.y"
                { CHECK_RETURN_VAL(add_match(sb, (yyvsp[-2].expr), (yyvsp[0].expr))); }
#line 1557 "sqlhist.tab.c"
    break;


#line 1561 "sqlhist.tab.c"

      default: break;
    }
  /* User semantic actions sometimes alter yychar, and that requires
     that yytoken be updated with the new translation.  We take the
     approach of translating immediately before every use of yytoken.
     One alternative is translating here after every semantic action,
     but that translation would be missed if the semantic action invokes
     YYABORT, YYACCEPT, or YYERROR immediately after altering yychar or
     if it invokes YYBACKUP.  In the case of YYABORT or YYACCEPT, an
     incorrect destructor might then be invoked immediately.  In the
     case of YYERROR or YYBACKUP, subsequent parser actions might lead
     to an incorrect destructor call or verbose syntax error message
     before the lookahead is translated.  */
  YY_SYMBOL_PRINT ("-> $$ =", YY_CAST (yysymbol_kind_t, yyr1[yyn]), &yyval, &yyloc);

  YYPOPSTACK (yylen);
  yylen = 0;

  *++yyvsp = yyval;

  /* Now 'shift' the result of the reduction.  Determine what state
     that goes to, based on the state we popped back to and the rule
     number reduced by.  */
  {
    const int yylhs = yyr1[yyn] - YYNTOKENS;
    const int yyi = yypgoto[yylhs] + *yyssp;
    yystate = (0 <= yyi && yyi <= YYLAST && yycheck[yyi] == *yyssp
               ? yytable[yyi]
               : yydefgoto[yylhs]);
  }

  goto yynewstate;


/*--------------------------------------.
| yyerrlab -- here on detecting error.  |
`--------------------------------------*/
yyerrlab:
  /* Make sure we have latest lookahead translation.  See comments at
     user semantic actions for why this is necessary.  */
  yytoken = yychar == TRACEFS_EMPTY ? YYSYMBOL_YYEMPTY : YYTRANSLATE (yychar);
  /* If not already recovering from an error, report this error.  */
  if (!yyerrstatus)
    {
      ++yynerrs;
      yyerror (sb, YY_("syntax error"));
    }

  if (yyerrstatus == 3)
    {
      /* If just tried and failed to reuse lookahead token after an
         error, discard it.  */

      if (yychar <= TRACEFS_EOF)
        {
          /* Return failure if at end of input.  */
          if (yychar == TRACEFS_EOF)
            YYABORT;
        }
      else
        {
          yydestruct ("Error: discarding",
                      yytoken, &yylval, sb);
          yychar = TRACEFS_EMPTY;
        }
    }

  /* Else will try to reuse lookahead token after shifting the error
     token.  */
  goto yyerrlab1;


/*---------------------------------------------------.
| yyerrorlab -- error raised explicitly by YYERROR.  |
`---------------------------------------------------*/
yyerrorlab:
  /* Pacify compilers when the user code never invokes YYERROR and the
     label yyerrorlab therefore never appears in user code.  */
  if (0)
    YYERROR;

  /* Do not reclaim the symbols of the rule whose action triggered
     this YYERROR.  */
  YYPOPSTACK (yylen);
  yylen = 0;
  YY_STACK_PRINT (yyss, yyssp);
  yystate = *yyssp;
  goto yyerrlab1;


/*-------------------------------------------------------------.
| yyerrlab1 -- common code for both syntax error and YYERROR.  |
`-------------------------------------------------------------*/
yyerrlab1:
  yyerrstatus = 3;      /* Each real token shifted decrements this.  */

  /* Pop stack until we find a state that shifts the error token.  */
  for (;;)
    {
      yyn = yypact[yystate];
      if (!yypact_value_is_default (yyn))
        {
          yyn += YYSYMBOL_YYerror;
          if (0 <= yyn && yyn <= YYLAST && yycheck[yyn] == YYSYMBOL_YYerror)
            {
              yyn = yytable[yyn];
              if (0 < yyn)
                break;
            }
        }

      /* Pop the current state because it cannot handle the error token.  */
      if (yyssp == yyss)
        YYABORT;


      yydestruct ("Error: popping",
                  YY_ACCESSING_SYMBOL (yystate), yyvsp, sb);
      YYPOPSTACK (1);
      yystate = *yyssp;
      YY_STACK_PRINT (yyss, yyssp);
    }

  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  *++yyvsp = yylval;
  YY_IGNORE_MAYBE_UNINITIALIZED_END


  /* Shift the error token.  */
  YY_SYMBOL_PRINT ("Shifting", YY_ACCESSING_SYMBOL (yyn), yyvsp, yylsp);

  yystate = yyn;
  goto yynewstate;


/*-------------------------------------.
| yyacceptlab -- YYACCEPT comes here.  |
`-------------------------------------*/
yyacceptlab:
  yyresult = 0;
  goto yyreturn;


/*-----------------------------------.
| yyabortlab -- YYABORT comes here.  |
`-----------------------------------*/
yyabortlab:
  yyresult = 1;
  goto yyreturn;


#if !defined yyoverflow
/*-------------------------------------------------.
| yyexhaustedlab -- memory exhaustion comes here.  |
`-------------------------------------------------*/
yyexhaustedlab:
  yyerror (sb, YY_("memory exhausted"));
  yyresult = 2;
  /* Fall through.  */
#endif


/*-----------------------------------------------------.
| yyreturn -- parsing is finished, return the result.  |
`-----------------------------------------------------*/
yyreturn:
  if (yychar != TRACEFS_EMPTY)
    {
      /* Make sure we have latest lookahead translation.  See comments at
         user semantic actions for why this is necessary.  */
      yytoken = YYTRANSLATE (yychar);
      yydestruct ("Cleanup: discarding lookahead",
                  yytoken, &yylval, sb);
    }
  /* Do not reclaim the symbols of the rule whose action triggered
     this YYABORT or YYACCEPT.  */
  YYPOPSTACK (yylen);
  YY_STACK_PRINT (yyss, yyssp);
  while (yyssp != yyss)
    {
      yydestruct ("Cleanup: popping",
                  YY_ACCESSING_SYMBOL (+*yyssp), yyvsp, sb);
      YYPOPSTACK (1);
    }
#ifndef yyoverflow
  if (yyss != yyssa)
    YYSTACK_FREE (yyss);
#endif

  return yyresult;
}

#line 248 "sqlhist.y"

