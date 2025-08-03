// Copyright (C) 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_LEXER_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_LEXER_H_

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"

namespace icing {
namespace lib {

class Lexer {
 public:
  enum class Language { QUERY, SCORING };

  // The maximum number of tokens allowed, in order to prevent stack overflow
  // issues in the parsers or visitors.
  static constexpr uint32_t kMaxNumTokens = 2048;

  enum class TokenType {
    COMMA,       // ','
    DOT,         // '.'
    PLUS,        // '+'            Not allowed in QUERY language.
    MINUS,       // '-'
    STAR,        // '*'            Not allowed in SCORING language.
    TIMES,       // '*'            Not allowed in QUERY language.
    DIV,         // '/'            Not allowed in QUERY language.
    LPAREN,      // '('
    RPAREN,      // ')'
    COMPARATOR,  // '<=' | '<' | '>=' | '>' | '!=' | '==' | ':'
                 //                Not allowed in SCORING language.
    AND,         // 'AND' | '&&'   Not allowed in SCORING language.
    OR,          // 'OR' | '||'    Not allowed in SCORING language.
    NOT,         // 'NOT'          Not allowed in SCORING language.
    STRING,      // String literal surrounded by quotation marks. The
                 // original_text of a STRING token will not include quotation
                 // marks.
    TEXT,        // A sequence of chars that are not any above-listed operator
    FUNCTION_NAME,  // A TEXT followed by LPAREN.
    // Whitespaces not inside a string literal will be skipped.
    // WS: " " | "\t" | "\n" | "\r" | "\f" -> skip ;
  };

  struct LexerToken {
    // For STRING, text will contain the raw original text of the token
    // in between quotation marks, without unescaping.
    //
    // For TEXT, text will contain the text of the token after unescaping all
    // escaped characters.
    //
    // For FUNCTION_NAME, this field will contain the name of the function.
    //
    // For COMPARATOR, this field will contain the comparator.
    //
    // For other types, this field will be empty.
    std::string text;

    // Lifecycle is dependent on the lifecycle of the string pointed to by
    // query_.
    std::string_view original_text;

    // The type of the token.
    TokenType type;
  };

  explicit Lexer(std::string_view query, Language language)
      : query_(query), language_(language) {
    Advance();
  }

  // Get a vector of LexerToken after lexing the query given in the constructor.
  //
  // Returns:
  //   A vector of LexerToken on success
  //   INVALID_ARGUMENT on syntax error.
  libtextclassifier3::StatusOr<std::vector<LexerToken>> ExtractTokens();

 private:
  // Advance to current_index_ + n.
  void Advance(uint32_t n = 1) {
    if (current_index_ + n >= query_.size()) {
      current_index_ = query_.size();
      current_char_ = '\0';
    } else {
      current_index_ += n;
      current_char_ = query_[current_index_];
    }
  }

  // Get the character at current_index_ + n.
  char PeekNext(uint32_t n = 1) {
    if (current_index_ + n >= query_.size()) {
      return '\0';
    } else {
      return query_[current_index_ + n];
    }
  }

  void SyntaxError(std::string error) {
    current_index_ = query_.size();
    current_char_ = '\0';
    error_ = std::move(error);
  }

  // Try to match a whitespace token and skip it.
  bool ConsumeWhitespace();

  // Try to match a single-char token other than '<' and '>'.
  bool ConsumeSingleChar();
  bool ConsumeQuerySingleChar();
  bool ConsumeScoringSingleChar();
  bool ConsumeGeneralSingleChar();

  // Try to match a comparator token other than ':'.
  bool ConsumeComparator();

  // Try to match '&&' and '||'.
  // 'AND' and 'OR' will be handled in Text() instead, so that 'ANDfoo' and
  // 'fooOR' is a TEXT, instead of an 'AND' or 'OR'.
  bool ConsumeAndOr();

  // Try to match a string literal.
  bool ConsumeStringLiteral();

  // Try to match a non-text.
  bool ConsumeNonText() {
    return ConsumeWhitespace() || ConsumeSingleChar() ||
           (language_ == Language::QUERY && ConsumeComparator()) ||
           (language_ == Language::QUERY && ConsumeAndOr()) ||
           ConsumeStringLiteral();
  }

  // Try to match TEXT, FUNCTION_NAME, 'AND', 'OR' and 'NOT'.
  // REQUIRES: ConsumeNonText() must be called immediately before calling this
  // function.
  bool ConsumeText();

  std::string_view query_;
  std::string error_;
  Language language_;
  int32_t current_index_ = -1;
  char current_char_ = '\0';
  std::vector<LexerToken> tokens_;

  // Stores whether the lexer is currently inspecting a TEXT segment while
  // handling current_char_.
  bool in_text_ = false;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_LEXER_H_
