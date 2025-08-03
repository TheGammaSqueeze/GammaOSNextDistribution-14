%{
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

%}

%define api.pure

/* Change the globals to use tracefs_ prefix */
%define api.prefix{tracefs_}
%code provides
{
  #define YYSTYPE TRACEFS_STYPE
  #define yylex tracefs_lex
  #define yyerror tracefs_error
}

%lex-param {void *scanner}
%parse-param {struct sqlhist_bison *sb}

%union {
	int	s32;
	char	*string;
	long	number;
	void	*expr;
}

%token AS SELECT FROM JOIN ON WHERE PARSE_ERROR CAST
%token <number> NUMBER field_type
%token <string> STRING
%token <string> FIELD
%token <string> LE GE EQ NEQ AND OR

%left '+' '-'
%left '*' '/'
%left '<' '>'
%left AND OR

%type <string> name label

%type <expr>  selection_expr field item named_field
%type <expr>  selection_addition
%type <expr>  compare compare_list compare_cmds compare_items
%type <expr>  compare_and_or
%type <expr>  str_val val

%%

start :
   select_statement
 ;

label : AS name { CHECK_RETURN_PTR($$ = store_str(sb, $2)); }
 | name { CHECK_RETURN_PTR($$ = store_str(sb, $1)); }
 ;

select : SELECT  { table_start(sb); }
  ;

select_statement :
    select selection_list table_exp
  ;

selection_list :
   selection
 | selection ',' selection_list
 ;

selection :
    selection_expr
				{
					CHECK_RETURN_VAL(add_selection(sb, $1, NULL));
				}
  | selection_expr label
				{
					CHECK_RETURN_VAL(add_selection(sb, $1, $2));
				}
  ;

selection_expr :
   field
 | '(' field ')'		{  $$ = $2; }
 | selection_addition
 | '(' selection_addition ')'	{  $$ = $2; }
 | CAST '(' field AS FIELD ')'	{
					 $$ = add_cast(sb, $3, $5);
					 CHECK_RETURN_PTR($$);
				}
 ;

selection_addition :
   field '+' field
				{
					$$ = add_compare(sb, $1, $3, COMPARE_ADD);
					CHECK_RETURN_PTR($$);
				}
 | field '-' field
				{
					$$ = add_compare(sb, $1, $3, COMPARE_SUB);
					CHECK_RETURN_PTR($$);
				}
 ;

item :
   named_field
 | field
 ;

field :
   FIELD	{ $$ = add_field(sb, $1, NULL); CHECK_RETURN_PTR($$); }
 ;

named_field :
   FIELD label { $$ = add_field(sb, $1, $2); CHECK_RETURN_PTR($$); }
 ;

name :
   FIELD
 ;

str_val :
   STRING	{ $$ = add_string(sb, $1); CHECK_RETURN_PTR($$); }
 ;

val :
   str_val
 | NUMBER	{ $$ = add_number(sb, $1); CHECK_RETURN_PTR($$); }
 ;


compare :
   field '<' val	{ $$ = add_filter(sb, $1, $3, FILTER_LT); CHECK_RETURN_PTR($$); }
 | field '>' val	{ $$ = add_filter(sb, $1, $3, FILTER_GT); CHECK_RETURN_PTR($$); }
 | field LE val	{ $$ = add_filter(sb, $1, $3, FILTER_LE); CHECK_RETURN_PTR($$); }
 | field GE val	{ $$ = add_filter(sb, $1, $3, FILTER_GE); CHECK_RETURN_PTR($$); }
 | field '=' val	{ $$ = add_filter(sb, $1, $3, FILTER_EQ); CHECK_RETURN_PTR($$); }
 | field EQ val	{ $$ = add_filter(sb, $1, $3, FILTER_EQ); CHECK_RETURN_PTR($$); }
 | field NEQ val	{ $$ = add_filter(sb, $1, $3, FILTER_NE); CHECK_RETURN_PTR($$); }
 | field "!=" val	{ $$ = add_filter(sb, $1, $3, FILTER_NE); CHECK_RETURN_PTR($$); }
 | field '&' val	{ $$ = add_filter(sb, $1, $3, FILTER_BIN_AND); CHECK_RETURN_PTR($$); }
 | field '~' str_val	{ $$ = add_filter(sb, $1, $3, FILTER_STR_CMP); CHECK_RETURN_PTR($$); }
;

compare_and_or :
   compare_and_or OR compare_and_or	{ $$ = add_filter(sb, $1, $3, FILTER_OR); CHECK_RETURN_PTR($$); }
 | compare_and_or AND compare_and_or	{ $$ = add_filter(sb, $1, $3, FILTER_AND); CHECK_RETURN_PTR($$); }
 | '!' '(' compare_and_or ')'		{ $$ = add_filter(sb, $3, NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR($$); }
 | '!' compare				{ $$ = add_filter(sb, $2, NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR($$); }
 | compare
 ;

compare_items :
   compare_items OR compare_items	{ $$ = add_filter(sb, $1, $3, FILTER_OR); CHECK_RETURN_PTR($$); }
 | '(' compare_and_or ')'		{ $$ = add_filter(sb, $2, NULL, FILTER_GROUP); CHECK_RETURN_PTR($$); }
 | '!' '(' compare_and_or ')'		{ $$ = add_filter(sb, $3, NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR($$); }
 | '!' compare				{ $$ = add_filter(sb, $2, NULL, FILTER_NOT_GROUP); CHECK_RETURN_PTR($$); }
 | compare
 ;

compare_cmds :
   compare_items		{ CHECK_RETURN_VAL(add_where(sb, $1)); }
 ;

/*
 * Top level AND is equal to ',' but the compare_cmds in them must
 * all be of for the same event (start or end exclusive).
 * That is, OR is not to be used between start and end events.
 */
compare_list :
   compare_cmds
 | compare_cmds ',' compare_list
 | compare_cmds AND compare_list
 ;

where_clause :
   WHERE compare_list
 ;

opt_where_clause :
   /* empty */
 | where_clause
;

opt_join_clause :
  /* empty set */
  | join_clause
 ;

table_exp :
   from_clause opt_join_clause opt_where_clause
 ;

from_clause :
   FROM item		{ CHECK_RETURN_VAL(add_from(sb, $2)); }

/*
 * Select from a from clause confuses the variable parsing.
 * disable it for now.

   | FROM '(' select_statement ')' label
				{
					from_table_end($5);
					$$ = store_printf("FROM (%s) AS %s", $3, $5);
				}
*/
 ;

join_clause :
 JOIN item ON match_clause	{ add_to(sb, $2); }
 ;

match :
   item '=' item { CHECK_RETURN_VAL(add_match(sb, $1, $3)); }
 | item EQ item { CHECK_RETURN_VAL(add_match(sb, $1, $3)); }

 ;

match_clause :
   match
 | match ',' match_clause
 ;

%%
