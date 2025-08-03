%{
/* code here */

#include <stdarg.h>
#include "sqlhist-parse.h"

extern int my_yyinput(void *extra, char *buf, int max);

#undef YY_INPUT
#define YY_INPUT(b, r, m) ({r = my_yyinput(yyextra, b, m);})

#define YY_NO_INPUT
#define YY_NO_UNPUT

#define YY_EXTRA_TYPE struct sqlhist_bison *

#define yytext yyg->yytext_r

#define TRACE_SB	((struct sqlhist_bison *)yyextra)
#define HANDLE_COLUMN do { TRACE_SB->line_idx += strlen(yytext); } while (0)

%}

%option caseless
%option reentrant
%option bison-bridge

field		\\?[a-z_][a-z0-9_\.]*
qstring		\"[^\"]*\"

hexnum		0x[0-9a-f]+
number		[0-9a-f]+
%%

select { HANDLE_COLUMN; return SELECT; }
as { HANDLE_COLUMN; return AS; }
from { HANDLE_COLUMN; return FROM; }
join { HANDLE_COLUMN; return JOIN; }
on { HANDLE_COLUMN; return ON; }
where { HANDLE_COLUMN; return WHERE; }
cast { HANDLE_COLUMN; return CAST; }

sym-offset {
	HANDLE_COLUMN;
	yylval->string = store_str(TRACE_SB, yyg->yytext_r);
	return FIELD;
}

{qstring} {
	HANDLE_COLUMN;
	yylval->string = store_str(TRACE_SB, yyg->yytext_r);
	return STRING;
}

{field} {
	const char *str = yyg->yytext_r;
	HANDLE_COLUMN;
	if (str[0] == '\\') { str++; };
	yylval->string = store_str(TRACE_SB, str);
	return FIELD;
}

{hexnum} {
	HANDLE_COLUMN;
	yylval->number = strtol(yyg->yytext_r, NULL, 0);
	return NUMBER;
}

{number} {
	HANDLE_COLUMN;
	yylval->number = strtol(yyg->yytext_r, NULL, 0);
	return NUMBER;
}

\!= { HANDLE_COLUMN; return NEQ; }
\<= { HANDLE_COLUMN; return LE; }
\>= { HANDLE_COLUMN; return GE; }
== { HANDLE_COLUMN; return EQ; }
&& { HANDLE_COLUMN; return AND; }
"||" { HANDLE_COLUMN; return OR; }
[<>&~] { HANDLE_COLUMN; return yytext[0]; }

[\!()\-\+\*/,=] { HANDLE_COLUMN; return yytext[0]; }

[ \t] { HANDLE_COLUMN; }
\n { TRACE_SB->line_idx = 0; TRACE_SB->line_no++; }

. { HANDLE_COLUMN; return PARSE_ERROR; }
%%

int yywrap(void *data)
{
	return 1;
}

void yyerror(struct sqlhist_bison *sb, char *fmt, ...)
{
	struct yyguts_t * yyg = (struct yyguts_t*)sb->scanner;
	va_list ap;

	va_start(ap, fmt);
	sql_parse_error(sb, yytext, fmt, ap);
	va_end(ap);
}
