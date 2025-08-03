/*
 * Copyright (C) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

%{
#include <string.h>
#include <stdlib.h>

#include "aidl_language.h"
#include "parser.h"
#include "aidl_language_y.h"

#ifndef YYSTYPE
#define YYSTYPE yy::parser::semantic_type
#endif

#ifndef YYLTYPE
#define YYLTYPE yy::parser::location_type
#endif

#define YY_USER_ACTION yylloc->columns(yyleng);
%}

%option noyywrap
%option nounput
%option noinput
%option reentrant
%option bison-bridge
%option bison-locations

%x LONG_COMMENT

identifier  [_a-zA-Z][_a-zA-Z0-9]*
whitespace  ([ \t\r]+)
intvalue    [0-9_]+(u8|u32|u64|[lL])*
hexvalue    0[x|X][0-9a-fA-F_]+(u8|u32|u64|[lL])*
floatvalue  [0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?f?

%%
%{
  /* This happens at every call to yylex (every time we receive one token) */
  using android::aidl::Comments;
  using android::aidl::Comment;
  std::string extra_text;
  Comments comments;
  yylloc->step();
%}

\/\*                  { extra_text += yytext; BEGIN(LONG_COMMENT); }
<LONG_COMMENT>\*+\/   { extra_text += yytext; yylloc->step(); BEGIN(INITIAL);
                        comments.push_back({extra_text});
                        extra_text.clear(); }
<LONG_COMMENT>\*+     { extra_text += yytext; }
<LONG_COMMENT>\n+     { extra_text += yytext; yylloc->lines(yyleng); }
<LONG_COMMENT>[^*\n]+ { extra_text += yytext; }

\"([^\"]|\\.)*\"      { yylval->token = new AidlToken(yytext, comments);
                        return yy::parser::token::C_STR; }

\/\/.*                { extra_text += yytext; extra_text += "\n";
                        comments.push_back({extra_text});
                        extra_text.clear(); }

\n+                   { yylloc->lines(yyleng); yylloc->step(); }
{whitespace}          {}
<<EOF>>               { yyterminate(); }

    /* symbols */
"("                   { return('('); }
")"                   { return(')'); }
"<"                   { return('<'); }
">"                   { return('>'); }
"{"                   { return('{'); }
"}"                   { return('}'); }
"["                   { return('['); }
"]"                   { return(']'); }
":"                   { return(':'); }
";"                   { return(';'); }
","                   { return(','); }
"."                   { return('.'); }
"="                   { return('='); }
"+"                   { return('+'); }
"-"                   { return('-'); }
"*"                   { return('*'); }
"/"                   { return('/'); }
"%"                   { return('%'); }
"&"                   { return('&'); }
"|"                   { return('|'); }
"^"                   { return('^'); }
"<<"                  { return(yy::parser::token::LSHIFT); }
">>"                  { return(yy::parser::token::RSHIFT); }
"&&"                  { return(yy::parser::token::LOGICAL_AND); }
"||"                  { return(yy::parser::token::LOGICAL_OR);  }
"!"                   { return('!'); }
"~"                   { return('~'); }
"<="                  { return(yy::parser::token::LEQ); }
">="                  { return(yy::parser::token::GEQ); }
"=="                  { return(yy::parser::token::EQUALITY); }
"!="                  { return(yy::parser::token::NEQ); }

    /* annotations */
@{identifier}         { yylval->token = new AidlToken(yytext + 1, comments);
                        return yy::parser::token::ANNOTATION;
                      }

    /* keywords */
parcelable            { yylval->token = new AidlToken("parcelable", comments);
                        return yy::parser::token::PARCELABLE;
                      }
import                { yylval->token = new AidlToken("import", comments);
                        return yy::parser::token::IMPORT; }
package               { yylval->token = new AidlToken("package", comments);
                        return yy::parser::token::PACKAGE; }
in                    { return yy::parser::token::IN; }
out                   { return yy::parser::token::OUT; }
inout                 { return yy::parser::token::INOUT; }
cpp_header            { yylval->token = new AidlToken("cpp_header", comments);
                        return yy::parser::token::CPP_HEADER; }
ndk_header            { yylval->token = new AidlToken("ndk_header", comments);
                        return yy::parser::token::NDK_HEADER; }
rust_type            { yylval->token = new AidlToken("rust_type", comments);
                        return yy::parser::token::RUST_TYPE; }
const                 { yylval->token = new AidlToken("const", comments);
                        return yy::parser::token::CONST; }
true                  { return yy::parser::token::TRUE_LITERAL; }
false                 { return yy::parser::token::FALSE_LITERAL; }

interface             { yylval->token = new AidlToken("interface", comments);
                        return yy::parser::token::INTERFACE;
                      }
oneway                { yylval->token = new AidlToken("oneway", comments);
                        return yy::parser::token::ONEWAY;
                      }
enum                  { yylval->token = new AidlToken("enum", comments);
                        return yy::parser::token::ENUM;
                      }
union                 { yylval->token = new AidlToken("union", comments);
                        return yy::parser::token::UNION;
                      }

    /* scalars */
{identifier}          { yylval->token = new AidlToken(yytext, comments);
                        return yy::parser::token::IDENTIFIER;
                      }
'.'                   { yylval->token = new AidlToken(std::string(yytext, yyleng), comments);
                        return yy::parser::token::CHARVALUE; }
{intvalue}            { yylval->token = new AidlToken(yytext, comments);
                        return yy::parser::token::INTVALUE; }
{floatvalue}          { yylval->token = new AidlToken(yytext, comments);
                        return yy::parser::token::FLOATVALUE; }
{hexvalue}            { yylval->token = new AidlToken(yytext, comments);
                        return yy::parser::token::HEXVALUE; }

  /* lexical error! */
.                     { return yy::parser::token::UNKNOWN; }

%%

// comment and whitespace handling
// ================================================
