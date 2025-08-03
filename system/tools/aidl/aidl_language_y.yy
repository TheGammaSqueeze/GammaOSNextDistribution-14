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
#include "aidl_language.h"
#include "parser.h"
#include "aidl_language_y.h"
#include "logging.h"
#include <android-base/parseint.h>
#include <set>
#include <map>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int yylex(yy::parser::semantic_type *, yy::parser::location_type *, void *);

AidlLocation loc(const yy::parser::location_type& begin, const yy::parser::location_type& end) {
  AIDL_FATAL_IF(begin.begin.filename != begin.end.filename, AIDL_LOCATION_HERE);
  AIDL_FATAL_IF(begin.end.filename != end.begin.filename, AIDL_LOCATION_HERE);
  AIDL_FATAL_IF(end.begin.filename != end.end.filename, AIDL_LOCATION_HERE);
  AidlLocation::Point begin_point {
    .line = begin.begin.line,
    .column = begin.begin.column,
  };
  AidlLocation::Point end_point {
    .line = end.end.line,
    .column = end.end.column,
  };
  return AidlLocation(*begin.begin.filename, begin_point, end_point, AidlLocation::Source::EXTERNAL);
}

AidlLocation loc(const yy::parser::location_type& l) {
  return loc(l, l);
}

#define lex_scanner ps->Scanner()

%}

%initial-action {
    @$.begin.filename = @$.end.filename =
        const_cast<std::string *>(&ps->FileName());
}

%parse-param { Parser* ps }
%lex-param { void *lex_scanner }

%glr-parser
%skeleton "glr.cc"

%expect-rr 0

%define parse.error verbose
%locations

%union {
    AidlToken* token;
    char character;
    std::string *str;
    std::vector<std::unique_ptr<AidlToken>> *token_list;
    AidlAnnotation* annotation;
    AidlAnnotationParameter* param;
    std::map<std::string, std::shared_ptr<AidlConstantValue>>* param_list;
    std::vector<std::unique_ptr<AidlAnnotation>>* annotation_list;
    AidlTypeSpecifier* type;
    AidlArgument* arg;
    AidlArgument::Direction direction;
    AidlConstantValue* const_expr;
    AidlEnumerator* enumerator;
    std::vector<std::unique_ptr<AidlEnumerator>>* enumerators;
    std::vector<std::unique_ptr<AidlConstantValue>>* constant_value_list;
    std::vector<std::unique_ptr<AidlArgument>>* arg_list;
    AidlVariableDeclaration* variable;
    AidlMethod* method;
    AidlMember* constant;
    std::vector<std::unique_ptr<AidlMember>>* members;
    AidlDefinedType* declaration;
    std::vector<std::unique_ptr<AidlTypeSpecifier>>* type_args;
    std::vector<std::string>* type_params;
    std::vector<std::unique_ptr<AidlDefinedType>>* declarations;
    AidlUnstructuredHeaders* unstructured_headers;
}

%destructor { } <direction>
%destructor { delete ($$); } <*>

%token<token> PACKAGE "package"
%token<token> IMPORT "import"
%token<token> ANNOTATION "annotation"
%token<token> C_STR "string literal"
%token<token> IDENTIFIER "identifier"
%token<token> INTERFACE "interface"
%token<token> PARCELABLE "parcelable"
%token<token> ONEWAY "oneway"
%token<token> ENUM "enum"
%token<token> UNION "union"
%token<token> CONST "const"

%token<token> CHARVALUE "char literal"
%token<token> FLOATVALUE "float literal"
%token<token> HEXVALUE "hex literal"
%token<token> INTVALUE "int literal"

%token '(' ')' ',' '=' '[' ']' '.' '{' '}' ';'
%token UNKNOWN "unrecognized character"
%token<token> CPP_HEADER "cpp_header (which can also be used as an identifier)"
%token<token> NDK_HEADER "ndk_header (which can also be used as an identifier)"
%token<token> RUST_TYPE "rust_type (which can also be used as an identifier)"
%token IN "in"
%token INOUT "inout"
%token OUT "out"
%token TRUE_LITERAL "true"
%token FALSE_LITERAL "false"

/* Operator precedence and associativity, as per
 * http://en.cppreference.com/w/cpp/language/operator_precedence */
/* Precedence level 13 - 14, LTR, logical operators*/
%left LOGICAL_OR
%left LOGICAL_AND
/* Precedence level 10 - 12, LTR, bitwise operators*/
%left '|'
%left '^'
%left '&'
/* Precedence level 9, LTR */
%left EQUALITY NEQ
/* Precedence level 8, LTR */
%left '<' '>' LEQ GEQ
/* Precedence level 7, LTR */
%left LSHIFT RSHIFT
/* Precedence level 6, LTR */
%left '+' '-'
/* Precedence level 5, LTR */
%left '*' '/' '%'
/* Precedence level 3, RTL; but we have to use %left here */
%right UNARY_PLUS UNARY_MINUS  '!' '~'

%type<declaration> decl
%type<declaration> unannotated_decl
%type<declaration> interface_decl
%type<declaration> parcelable_decl
%type<declaration> enum_decl
%type<declaration> union_decl
%type<members> parcelable_members interface_members
%type<variable> variable_decl
%type<type_params> optional_type_params
%type<method> method_decl
%type<constant> constant_decl
%type<enumerator> enumerator
%type<enumerators> enumerators enum_decl_body
%type<param> parameter
%type<param_list> parameter_list
%type<param_list> parameter_non_empty_list
%type<annotation> annotation
%type<annotation_list>annotation_list
%type<type> type
%type<type> non_array_type
%type<arg_list> arg_list arg_non_empty_list
%type<arg> arg
%type<direction> direction
%type<type_args> type_args
%type<type_params> type_params
%type<const_expr> const_expr
%type<constant_value_list> constant_value_list
%type<constant_value_list> constant_value_non_empty_list
%type<token_list> imports
%type<declarations> decls
%type<token> import identifier error qualified_name optional_package
%type<unstructured_headers> optional_unstructured_headers

%%

document
 : optional_package imports decls {
    std::vector<std::string> imports;
    for (const auto& import : *$2) {
      imports.push_back(import->GetText());
    }

    ps->MakeDocument(loc(@1), Comments(), std::move(imports), std::move(*$3));

    for (auto i = $2->rbegin(); i != $2->rend(); ++i) {
      ps->GetDocument()->PrependComments((*i)->GetComments());
    }
    if ($1) {
      ps->GetDocument()->PrependComments($1->GetComments());
    }

    delete $1;
    delete $2;
    delete $3;
  }
 ;

/* A couple of tokens that are keywords elsewhere are identifiers when
 * occurring in the identifier position. Therefore identifier is a
 * non-terminal, which is either an IDENTIFIER token, or one of the
 * aforementioned keyword tokens.
 */
identifier
 : IDENTIFIER
 | CPP_HEADER
 | NDK_HEADER
 | RUST_TYPE
 ;

optional_package
 : {
    $$ = nullptr;
 }
 | PACKAGE qualified_name ';' {
    ps->SetPackage($2->GetText());
    $$ = $1; // for comments
    delete $2;
  }
 ;

imports
 : { $$ = new std::vector<std::unique_ptr<AidlToken>>(); }
 | imports import
  {
    $$ = $1;
    $$->emplace_back($2);
  }

import
 : IMPORT qualified_name ';' {
    // carry the comments before "import" token
    $$ = new AidlToken($2->GetText(), $1->GetComments());
    delete $1;
    delete $2;
  };

qualified_name
 : identifier {
    $$ = $1;
  }
 | qualified_name '.' identifier
  { $$ = $1;
    $$->Append('.');
    $$->Append($3->GetText());
    delete $3;
  };

decls
 : decl
  { $$ = new std::vector<std::unique_ptr<AidlDefinedType>>();
    if ($1 != nullptr) {
      $$->emplace_back($1);
    }
  }
 | decls decl
  { $$ = $1;
    if ($2 != nullptr) {
      $$->emplace_back($2);
    }
  }

decl
 : annotation_list unannotated_decl
   {
    $$ = $2;

    if ($$ != nullptr) {
      $$->Annotate(std::move(*$1));
    }

    delete $1;
   }
 ;

unannotated_decl
 : parcelable_decl
 | interface_decl
 | enum_decl
 | union_decl
 ;

type_params
 : identifier {
    $$ = new std::vector<std::string>();
    $$->emplace_back($1->GetText());
    delete $1;
  }
 | type_params ',' identifier {
    $1->emplace_back($3->GetText());
    $$ = $1;
    delete $3;
  };

optional_type_params
 : /* none */ { $$ = nullptr; }
 | '<' type_params '>' {
   $$ = $2;
 };

optional_unstructured_headers
 : /* none */ { $$ = new AidlUnstructuredHeaders; }
 | optional_unstructured_headers CPP_HEADER C_STR {
     $$ = $1;
     $$->cpp = $3->GetText();
     delete $2;
     delete $3;
 }
 | optional_unstructured_headers NDK_HEADER C_STR {
     $$ = $1;
     $$->ndk = $3->GetText();
     delete $2;
     delete $3;
 }
 | optional_unstructured_headers RUST_TYPE C_STR {
     $$ = $1;
     $$->rust_type = $3->GetText();
     delete $2;
     delete $3;
 }
 ;

parcelable_decl
 : PARCELABLE qualified_name optional_type_params optional_unstructured_headers ';' {
    // No check for type name here. We allow nested types for unstructured parcelables.
    $$ = new AidlParcelable(loc(@2), $2->GetText(), ps->Package(), $1->GetComments(), *$4, $3);
    delete $1;
    delete $2;
    delete $4;
 }
 | PARCELABLE qualified_name optional_type_params '{' parcelable_members '}' {
    ps->CheckValidTypeName(*$2, loc(@2));
    $$ = new AidlStructuredParcelable(loc(@2), $2->GetText(), ps->Package(), $1->GetComments(), $3, $5);
    delete $1;
    delete $2;
 }
 | PARCELABLE error ';' {
    ps->AddError();
    $$ = nullptr;
    delete $1;
  };

parcelable_members
 : /* empty */ {
    $$ = new std::vector<std::unique_ptr<AidlMember>>();
  }
 | parcelable_members variable_decl {
    $1->emplace_back($2);
    $$ = $1;
  }
 | parcelable_members constant_decl {
    $1->emplace_back($2);
    $$ = $1;
  }
 | parcelable_members decl {
    if ($2) $1->emplace_back($2);  // decl may be nullptr on error
    $$ = $1;
  }
 | parcelable_members error ';' {
    ps->AddError();
    $$ = $1;
  };

variable_decl
 : type identifier ';' {
   $$ = new AidlVariableDeclaration(loc(@2), $1, $2->GetText());
   delete $2;
 }
 | type identifier '=' const_expr ';' {
   // TODO(b/123321528): Support enum type default assignments (TestEnum foo = TestEnum.FOO).
   $$ = new AidlVariableDeclaration(loc(@2), $1, $2->GetText(),  $4);
   delete $2;
 }
 ;

interface_decl
 : INTERFACE qualified_name ';' {
    ps->CheckValidTypeName(*$2, loc(@2));
    $$ = new AidlInterface(loc(@1), $2->GetText(), $1->GetComments(), false, ps->Package(), nullptr);
    delete $1;
    delete $2;
  }
 | INTERFACE qualified_name '{' interface_members '}' {
    ps->CheckValidTypeName(*$2, loc(@2));
    $$ = new AidlInterface(loc(@1), $2->GetText(), $1->GetComments(), false, ps->Package(), $4);
    delete $1;
    delete $2;
  }
 | ONEWAY INTERFACE qualified_name '{' interface_members '}' {
    ps->CheckValidTypeName(*$3, loc(@3));
    $$ = new AidlInterface(loc(@2), $3->GetText(), $1->GetComments(), true, ps->Package(), $5);
    delete $1;
    delete $2;
    delete $3;
  }
 | INTERFACE error '{' interface_members '}' {
    ps->AddError();
    $$ = nullptr;
    delete $1;
    delete $4;
  };

interface_members
 :
  { $$ = new std::vector<std::unique_ptr<AidlMember>>(); }
 | interface_members method_decl
  { $1->push_back(std::unique_ptr<AidlMember>($2)); $$ = $1; }
 | interface_members constant_decl
  { $1->push_back(std::unique_ptr<AidlMember>($2)); $$ = $1; }
 | interface_members decl
  {
    if ($2) $1->emplace_back($2);  // decl may be nullptr on error
    $$ = $1;
  }
 | interface_members error ';' {
    ps->AddError();
    $$ = $1;
  };

const_expr
 : TRUE_LITERAL { $$ = AidlConstantValue::Boolean(loc(@1), true); }
 | FALSE_LITERAL { $$ = AidlConstantValue::Boolean(loc(@1), false); }
 | CHARVALUE {
    $$ = AidlConstantValue::Character(loc(@1), $1->GetText());
    delete $1;
  }
 | INTVALUE {
    $$ = AidlConstantValue::Integral(loc(@1), $1->GetText());
    if ($$ == nullptr) {
      AIDL_ERROR(loc(@1)) << "Could not parse integer: "
                << $1->GetText();
      ps->AddError();
      $$ = AidlConstantValue::Integral(loc(@1), "0");
    }
    delete $1;
  }
 | FLOATVALUE {
    $$ = AidlConstantValue::Floating(loc(@1), $1->GetText());
    delete $1;
  }
 | HEXVALUE {
    $$ = AidlConstantValue::Integral(loc(@1), $1->GetText());
    if ($$ == nullptr) {
      AIDL_ERROR(loc(@1)) << "Could not parse hexvalue: "
                << $1->GetText();
      ps->AddError();
      $$ = AidlConstantValue::Integral(loc(@1), "0");
    }
    delete $1;
  }
 | C_STR {
    $$ = AidlConstantValue::String(loc(@1), $1->GetText());
    delete $1;
  }
 | qualified_name {
    $$ = new AidlConstantReference(loc(@1), $1->GetText());
    delete $1;
 }
 | '{' constant_value_list '}' {
    $$ = AidlConstantValue::Array(loc(@1), std::unique_ptr<vector<unique_ptr<AidlConstantValue>>>($2));
  }
 | const_expr LOGICAL_OR const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "||", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr LOGICAL_AND const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "&&", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '|' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "|" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '^' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "^" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '&' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "&" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr EQUALITY const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "==", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr NEQ const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "!=", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '<' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "<" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '>' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), ">" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr LEQ const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "<=", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr GEQ const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), ">=", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr LSHIFT const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "<<", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr RSHIFT const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), ">>", std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '+' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "+" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '-' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "-" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '*' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "*" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '/' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "/" , std::unique_ptr<AidlConstantValue>($3));
  }
 | const_expr '%' const_expr {
    $$ = new AidlBinaryConstExpression(loc(@1), std::unique_ptr<AidlConstantValue>($1), "%" , std::unique_ptr<AidlConstantValue>($3));
  }
 | '+' const_expr %prec UNARY_PLUS  {
    $$ = new AidlUnaryConstExpression(loc(@1), "+", std::unique_ptr<AidlConstantValue>($2));
  }
 | '-' const_expr %prec UNARY_MINUS {
    $$ = new AidlUnaryConstExpression(loc(@1), "-", std::unique_ptr<AidlConstantValue>($2));
  }
 | '!' const_expr {
    $$ = new AidlUnaryConstExpression(loc(@1), "!", std::unique_ptr<AidlConstantValue>($2));
  }
 | '~' const_expr {
    $$ = new AidlUnaryConstExpression(loc(@1), "~", std::unique_ptr<AidlConstantValue>($2));
  }
 | '(' const_expr ')'
  {
    $$ = $2;
  }
 | '(' error ')'
   {
     AIDL_ERROR(loc(@1)) << "invalid const expression within parenthesis";
     ps->AddError();
     // to avoid segfaults
     $$ = AidlConstantValue::Integral(loc(@1), "0");
   }
 ;

constant_value_list
 : /* empty */ {
    $$ = new std::vector<std::unique_ptr<AidlConstantValue>>;
 }
 | constant_value_non_empty_list {
    $$ = $1;
 }
 | constant_value_non_empty_list  ',' {
    $$ = $1;
 }
 ;

constant_value_non_empty_list
 : const_expr {
    $$ = new std::vector<std::unique_ptr<AidlConstantValue>>;
    $$->push_back(std::unique_ptr<AidlConstantValue>($1));
 }
 | constant_value_non_empty_list ',' const_expr {
    $$ = $1;
    $$->push_back(std::unique_ptr<AidlConstantValue>($3));
 }
 ;

constant_decl
 : annotation_list CONST type identifier '=' const_expr ';' {
    $3->PrependComments($2->GetComments());
    // TODO(b/151102494) do not merge annotations.
    $3->Annotate(std::move(*$1));
    $$ = new AidlConstantDeclaration(loc(@4), $3, $4->GetText(), $6);
    delete $1;
    delete $2;
    delete $4;
   }
 ;

enumerator
 : identifier '=' const_expr {
    $$ = new AidlEnumerator(loc(@1), $1->GetText(), $3, $1->GetComments());
    delete $1;
   }
 | identifier {
    $$ = new AidlEnumerator(loc(@1), $1->GetText(), nullptr, $1->GetComments());
    delete $1;
   }
 ;

enumerators
 : enumerator {
    $$ = new std::vector<std::unique_ptr<AidlEnumerator>>();
    $$->push_back(std::unique_ptr<AidlEnumerator>($1));
   }
 | enumerators ',' enumerator {
    $1->push_back(std::unique_ptr<AidlEnumerator>($3));
    $$ = $1;
   }
 ;

enum_decl_body
 : '{' enumerators '}' { $$ = $2; }
 | '{' enumerators ',' '}' { $$ = $2; }
 ;

enum_decl
 : ENUM qualified_name enum_decl_body {
    ps->CheckValidTypeName(*$2, loc(@2));
    $$ = new AidlEnumDeclaration(loc(@2), $2->GetText(), $3, ps->Package(), $1->GetComments());
    delete $1;
    delete $2;
    delete $3;
   }
 ;

union_decl
 : UNION qualified_name optional_type_params '{' parcelable_members '}' {
    ps->CheckValidTypeName(*$2, loc(@2));
    $$ = new AidlUnionDecl(loc(@2), $2->GetText(), ps->Package(), $1->GetComments(), $3, $5);
    delete $1;
    delete $2;
  }
 ;

method_decl
 : type identifier '(' arg_list ')' ';' {
    $$ = new AidlMethod(loc(@2), false, $1, $2->GetText(), $4, $2->GetComments());
    $$->PrependComments($1->GetComments());
    delete $2;
  }
 | annotation_list ONEWAY type identifier '(' arg_list ')' ';' {
    $$ = new AidlMethod(loc(@4), true, $3, $4->GetText(), $6, $4->GetComments());
    $3->PrependComments($2->GetComments());
    $3->Annotate(std::move(*$1));
    $$->PrependComments($3->GetComments());
    delete $1;
    delete $2;
    delete $4;
  }
 | type identifier '(' arg_list ')' '=' INTVALUE ';' {
    int32_t serial = 0;
    if (!android::base::ParseInt($7->GetText(), &serial)) {
        AIDL_ERROR(loc(@7)) << "Could not parse int value: " << $7->GetText();
        ps->AddError();
    }
    $$ = new AidlMethod(loc(@2), false, $1, $2->GetText(), $4, $2->GetComments(), serial);
    $$->PrependComments($1->GetComments());
    delete $2;
    delete $7;
  }
 | annotation_list ONEWAY type identifier '(' arg_list ')' '=' INTVALUE ';' {
    int32_t serial = 0;
    if (!android::base::ParseInt($9->GetText(), &serial)) {
        AIDL_ERROR(loc(@9)) << "Could not parse int value: " << $9->GetText();
        ps->AddError();
    }
    $$ = new AidlMethod(loc(@4), true, $3, $4->GetText(), $6, $4->GetComments(), serial);
    $3->PrependComments($2->GetComments());
    $3->Annotate(std::move(*$1));
    $$->PrependComments($3->GetComments());
    delete $1;
    delete $2;
    delete $4;
    delete $9;
  };

arg_non_empty_list
 : arg {
    $$ = new std::vector<std::unique_ptr<AidlArgument>>();
    $$->push_back(std::unique_ptr<AidlArgument>($1));
  }
 | arg_non_empty_list ',' arg {
    $$ = $1;
    $$->push_back(std::unique_ptr<AidlArgument>($3));
  };

arg_list
 : /*empty*/
   { $$ = new std::vector<std::unique_ptr<AidlArgument>>(); }
 | arg_non_empty_list { $$ = $1; }
 ;

arg
 : direction type identifier {
    $$ = new AidlArgument(loc(@3), $1, $2, $3->GetText());
    delete $3;
  }
 | type identifier {
    $$ = new AidlArgument(loc(@2), $1, $2->GetText());
    delete $2;
  };

non_array_type
 : annotation_list qualified_name {
    $$ = new AidlTypeSpecifier(loc(@2), $2->GetText(), /*array=*/std::nullopt, nullptr, $2->GetComments());
    $$->Annotate(std::move(*$1));
    delete $1;
    delete $2;
  }
 | non_array_type '<' type_args '>' {
    ps->SetTypeParameters($1, $3);
    $$ = $1;
  }
 | non_array_type '<' non_array_type '<' type_args RSHIFT {
    ps->SetTypeParameters($3, $5);
    auto params = new std::vector<std::unique_ptr<AidlTypeSpecifier>>();
    params->emplace_back($3);
    ps->SetTypeParameters($1, params);
    $$ = $1;
  }
 | non_array_type '<' type_args ',' non_array_type '<' type_args RSHIFT {
    ps->SetTypeParameters($5, $7);
    $3->emplace_back($5);
    ps->SetTypeParameters($1, $3);
    $$ = $1;
  };

type
 : non_array_type
 | type annotation_list '[' ']' {
    if (!$2->empty()) {
      AIDL_ERROR(loc(@2)) << "Annotations for arrays are not supported.";
      ps->AddError();
    }
    if (!$1->MakeArray(DynamicArray{})) {
      AIDL_ERROR(loc(@1)) << "Multi-dimensional arrays must be fixed size.";
      ps->AddError();
    }
    $$ = $1;
    delete $2;
  }
 | type annotation_list '[' const_expr ']' {
    if (!$2->empty()) {
      AIDL_ERROR(loc(@2)) << "Annotations for arrays are not supported.";
      ps->AddError();
    }
    if (!$1->MakeArray(FixedSizeArray{std::unique_ptr<AidlConstantValue>($4)})) {
      AIDL_ERROR(loc(@1)) << "Multi-dimensional arrays must be fixed size.";
      ps->AddError();
    }
    $$ = $1;
    delete $2;
  }
 ;

type_args
 : type {
    if (!$1->GetAnnotations().empty()) {
      AIDL_ERROR(loc(@1)) << "Annotations for type arguments are not supported.";
      ps->AddError();
    }
    $$ = new std::vector<std::unique_ptr<AidlTypeSpecifier>>();
    $$->emplace_back($1);
  }
 | type_args ',' type {
    $1->emplace_back($3);
    $$ = $1;
  };

annotation_list
 :
  { $$ = new std::vector<std::unique_ptr<AidlAnnotation>>(); }
 | annotation_list annotation
  {
    if ($2 != nullptr) {
      $1->emplace_back(std::unique_ptr<AidlAnnotation>($2));
    }
    $$ = $1;
  };

parameter
  : identifier '=' const_expr {
    $$ = new AidlAnnotationParameter{$1->GetText(), std::unique_ptr<AidlConstantValue>($3)};
    delete $1;
  };

parameter_list
  : /*empty*/{
    $$ = new std::map<std::string, std::shared_ptr<AidlConstantValue>>();
  }
  | parameter_non_empty_list  {
    $$ = $1;
  };

parameter_non_empty_list
  : parameter {
    $$ = new std::map<std::string, std::shared_ptr<AidlConstantValue>>();
    $$->emplace(std::move($1->name), $1->value.release());
    delete $1;
  }
  | parameter_non_empty_list ',' parameter {
    $$ = $1;
    if ($$->find($3->name) != $$->end()) {
      AIDL_ERROR(loc(@3)) << "Trying to redefine parameter " << $3->name << ".";
      ps->AddError();
    }
    $$->emplace(std::move($3->name), std::move($3->value));
    delete $3;
  };

annotation
 : ANNOTATION {
    // release() returns nullptr if unique_ptr is empty.
    $$ = AidlAnnotation::Parse(loc(@1), $1->GetText(), {}, $1->GetComments()).release();
    if (!$$) {
      ps->AddError();
    }
    delete $1;
  }
 | ANNOTATION '(' const_expr ')' {
    auto value = std::shared_ptr<AidlConstantValue>($3);
    std::map<std::string, std::shared_ptr<AidlConstantValue>> parameter_list {{"value" , value}};
    $$ = AidlAnnotation::Parse(loc(@1, @4), $1->GetText(), std::move(parameter_list), $1->GetComments()).release();
    if (!$$) {
      ps->AddError();
    }
    delete $1;
  }
 | ANNOTATION '(' parameter_list ')' {
    // release() returns nullptr if unique_ptr is empty.
    $$ = AidlAnnotation::Parse(loc(@1, @4), $1->GetText(), std::move(*$3), $1->GetComments()).release();
    if (!$$) {
      ps->AddError();
    }
    delete $1;
    delete $3;
  }
 ;

direction
 : IN
  { $$ = AidlArgument::IN_DIR; }
 | OUT
  { $$ = AidlArgument::OUT_DIR; }
 | INOUT
  { $$ = AidlArgument::INOUT_DIR; };

%%

#include <ctype.h>
#include <stdio.h>

void yy::parser::error(const yy::parser::location_type& l, const std::string& errstr) {
  AIDL_ERROR(loc(l)) << errstr;
  // parser will return error value
}
