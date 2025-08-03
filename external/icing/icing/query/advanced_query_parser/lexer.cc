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

#include "icing/query/advanced_query_parser/lexer.h"

#include <string>

#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/util/i18n-utils.h"

namespace icing {
namespace lib {

bool Lexer::ConsumeWhitespace() {
  if (current_char_ == '\0') {
    return false;
  }
  if (i18n_utils::IsWhitespaceAt(query_, current_index_)) {
    UChar32 uchar32 = i18n_utils::GetUChar32At(query_.data(), query_.length(),
                                               current_index_);
    int length = i18n_utils::GetUtf8Length(uchar32);
    Advance(length);
    return true;
  }
  return false;
}

bool Lexer::ConsumeQuerySingleChar() {
  std::string_view original_text = query_.substr(current_index_, 1);
  switch (current_char_) {
    case ':':
      tokens_.push_back({":", original_text, TokenType::COMPARATOR});
      break;
    case '*':
      tokens_.push_back({"", original_text, TokenType::STAR});
      break;
    case '-':
      if (in_text_) {
        // MINUS ('-') is considered to be a part of a text segment if it is
        // in the middle of a TEXT segment (ex. `foo-bar`).
        return false;
      }
      tokens_.push_back({"", original_text, TokenType::MINUS});
      break;
    default:
      return false;
  }
  Advance();
  return true;
}

bool Lexer::ConsumeScoringSingleChar() {
  std::string_view original_text = query_.substr(current_index_, 1);
  switch (current_char_) {
    case '+':
      tokens_.push_back({"", original_text, TokenType::PLUS});
      break;
    case '*':
      tokens_.push_back({"", original_text, TokenType::TIMES});
      break;
    case '/':
      tokens_.push_back({"", original_text, TokenType::DIV});
      break;
    case '-':
      tokens_.push_back({"", original_text, TokenType::MINUS});
      break;
    default:
      return false;
  }
  Advance();
  return true;
}

bool Lexer::ConsumeGeneralSingleChar() {
  std::string_view original_text = query_.substr(current_index_, 1);
  switch (current_char_) {
    case ',':
      tokens_.push_back({"", original_text, TokenType::COMMA});
      break;
    case '.':
      tokens_.push_back({"", original_text, TokenType::DOT});
      break;
    case '(':
      tokens_.push_back({"", original_text, TokenType::LPAREN});
      break;
    case ')':
      tokens_.push_back({"", original_text, TokenType::RPAREN});
      break;
    default:
      return false;
  }
  Advance();
  return true;
}

bool Lexer::ConsumeSingleChar() {
  if (language_ == Language::QUERY) {
    if (ConsumeQuerySingleChar()) {
      return true;
    }
  } else if (language_ == Language::SCORING) {
    if (ConsumeScoringSingleChar()) {
      return true;
    }
  }
  return ConsumeGeneralSingleChar();
}

bool Lexer::ConsumeComparator() {
  if (current_char_ != '<' && current_char_ != '>' && current_char_ != '!' &&
      current_char_ != '=') {
    return false;
  }
  // Now, current_char_ must be one of '<', '>', '!', or '='.
  // Matching for '<=', '>=', '!=', or '=='.
  char next_char = PeekNext(1);
  if (next_char == '=') {
    tokens_.push_back({{current_char_, next_char},
                       query_.substr(current_index_, 2),
                       TokenType::COMPARATOR});
    Advance(2);
    return true;
  }
  // Now, next_char must not be '='. Let's match for '<' and '>'.
  if (current_char_ == '<' || current_char_ == '>') {
    tokens_.push_back({{current_char_},
                       query_.substr(current_index_, 1),
                       TokenType::COMPARATOR});
    Advance();
    return true;
  }
  return false;
}

bool Lexer::ConsumeAndOr() {
  if (current_char_ != '&' && current_char_ != '|') {
    return false;
  }
  char next_char = PeekNext(1);
  if (current_char_ != next_char) {
    return false;
  }
  std::string_view original_text = query_.substr(current_index_, 2);
  if (current_char_ == '&') {
    tokens_.push_back({"", original_text, TokenType::AND});
  } else {
    tokens_.push_back({"", original_text, TokenType::OR});
  }
  Advance(2);
  return true;
}

bool Lexer::ConsumeStringLiteral() {
  if (current_char_ != '"') {
    return false;
  }
  Advance();
  int32_t unnormalized_start_pos = current_index_;
  while (current_char_ != '\0' && current_char_ != '"') {
    // When getting a backslash, we will always match the next character, even
    // if the next character is a quotation mark
    if (current_char_ == '\\') {
      Advance();
      if (current_char_ == '\0') {
        // In this case, we are missing a terminating quotation mark.
        break;
      }
    }
    Advance();
  }
  if (current_char_ == '\0') {
    SyntaxError("missing terminating \" character");
    return false;
  }
  int32_t unnormalized_length = current_index_ - unnormalized_start_pos;
  std::string_view raw_token_text =
      query_.substr(unnormalized_start_pos, unnormalized_length);
  std::string token_text(raw_token_text);
  tokens_.push_back({std::move(token_text), raw_token_text, TokenType::STRING});
  Advance();
  return true;
}

bool Lexer::ConsumeText() {
  if (current_char_ == '\0') {
    return false;
  }
  tokens_.push_back({"", query_.substr(current_index_, 0), TokenType::TEXT});
  int token_index = tokens_.size() - 1;

  int32_t unnormalized_start_pos = current_index_;
  int32_t unnormalized_end_pos = current_index_;
  while (!ConsumeNonText() && current_char_ != '\0') {
    in_text_ = true;
    // When getting a backslash in TEXT, unescape it by accepting its following
    // character no matter which character it is, including white spaces,
    // operator symbols, parentheses, etc.
    if (current_char_ == '\\') {
      Advance();
      if (current_char_ == '\0') {
        SyntaxError("missing a escaping character after \\");
        break;
      }
    }
    tokens_[token_index].text.push_back(current_char_);
    Advance();
    unnormalized_end_pos = current_index_;
  }
  in_text_ = false;

  tokens_[token_index].original_text = query_.substr(
      unnormalized_start_pos, unnormalized_end_pos - unnormalized_start_pos);
  if (unnormalized_end_pos < query_.length() &&
      query_[unnormalized_end_pos] == '(') {
    // A TEXT followed by a LPAREN is a FUNCTION_NAME.
    tokens_[token_index].type = TokenType::FUNCTION_NAME;
  }

  if (language_ == Lexer::Language::QUERY) {
    std::string &text = tokens_[token_index].text;
    TokenType &type = tokens_[token_index].type;
    if (text == "AND") {
      text.clear();
      type = TokenType::AND;
    } else if (text == "OR") {
      text.clear();
      type = TokenType::OR;
    } else if (text == "NOT") {
      text.clear();
      type = TokenType::NOT;
    }
  }
  return true;
}

libtextclassifier3::StatusOr<std::vector<Lexer::LexerToken>>
Lexer::ExtractTokens() {
  while (current_char_ != '\0') {
    // Clear out any non-text before matching a Text.
    while (ConsumeNonText()) {
    }
    ConsumeText();
  }
  if (!error_.empty()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Syntax Error: ", error_));
  }
  if (tokens_.size() > kMaxNumTokens) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("The maximum number of tokens allowed is ",
                           std::to_string(kMaxNumTokens), ", but got ",
                           std::to_string(tokens_.size()), " tokens."));
  }
  return tokens_;
}

}  // namespace lib
}  // namespace icing
