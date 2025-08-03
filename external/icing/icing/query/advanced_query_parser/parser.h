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

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_PARSER_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_PARSER_H_

#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/query/advanced_query_parser/lexer.h"

namespace icing {
namespace lib {

class Parser {
 public:
  static Parser Create(std::vector<Lexer::LexerToken>&& lexer_tokens) {
    return Parser(std::move(lexer_tokens));
  }

  // Returns:
  //   On success, pointer to the root node of the AST
  //   INVALID_ARGUMENT for input that does not conform to the grammar
  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeQuery();

  // Returns:
  //   On success, pointer to the root node of the AST
  //   INVALID_ARGUMENT for input that does not conform to the grammar
  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeScoring();

 private:
  explicit Parser(std::vector<Lexer::LexerToken>&& lexer_tokens)
      : lexer_tokens_(std::move(lexer_tokens)),
        current_token_(lexer_tokens_.begin()) {}

  // Match Functions
  // These functions are used to test whether the current_token matches a member
  // of the FIRST set of a particular symbol in our grammar.
  bool Match(Lexer::TokenType token_type) const {
    return current_token_ != lexer_tokens_.end() &&
           current_token_->type == token_type;
  }

  bool MatchMember() const { return Match(Lexer::TokenType::TEXT); }

  bool MatchFunction() const { return Match(Lexer::TokenType::FUNCTION_NAME); }

  bool MatchComparable() const {
    return Match(Lexer::TokenType::STRING) || MatchMember() || MatchFunction();
  }

  bool MatchComposite() const { return Match(Lexer::TokenType::LPAREN); }

  bool MatchRestriction() const { return MatchComparable(); }

  bool MatchSimple() const { return MatchRestriction() || MatchComposite(); }

  bool MatchTerm() const {
    return MatchSimple() || Match(Lexer::TokenType::NOT) ||
           Match(Lexer::TokenType::MINUS);
  }

  bool MatchFactor() const { return MatchTerm(); }

  // Consume Functions
  // These functions attempt to parse the token sequence starting at
  // current_token_.
  // Returns INVALID_ARGUMENT if unable to parse the token sequence starting at
  // current_token_ as that particular grammar symbol. There are no guarantees
  // about what state current_token and lexer_tokens_ are in when returning an
  // error.
  //
  // Consume functions for terminal symbols. These are the only Consume
  // functions that will directly modify current_token_.
  // The Consume functions for terminals will guarantee not to modify
  // current_token_ and lexer_tokens_ when returning an error.
  libtextclassifier3::Status Consume(Lexer::TokenType token_type);

  libtextclassifier3::StatusOr<std::unique_ptr<TextNode>> ConsumeText();

  libtextclassifier3::StatusOr<std::unique_ptr<FunctionNameNode>>
  ConsumeFunctionName();

  libtextclassifier3::StatusOr<std::unique_ptr<StringNode>>
  ConsumeStringElement();

  libtextclassifier3::StatusOr<std::string> ConsumeComparator();

  // Consume functions for non-terminal symbols.
  libtextclassifier3::StatusOr<std::unique_ptr<MemberNode>> ConsumeMember();

  libtextclassifier3::StatusOr<std::unique_ptr<FunctionNode>> ConsumeFunction();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeComparable();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeComposite();

  libtextclassifier3::StatusOr<std::vector<std::unique_ptr<Node>>>
  ConsumeArgs();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeRestriction();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeSimple();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeTerm();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeFactor();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeSequence();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeQueryExpression();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeMultExpr();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>>
  ConsumeScoringExpression();

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ConsumeExpression();

  std::vector<Lexer::LexerToken> lexer_tokens_;
  std::vector<Lexer::LexerToken>::const_iterator current_token_;
  Lexer::Language language_ = Lexer::Language::QUERY;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_PARSER_H_
