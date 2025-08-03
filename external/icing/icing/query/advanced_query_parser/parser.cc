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

#include "icing/query/advanced_query_parser/parser.h"

#include <memory>
#include <string_view>

#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

std::unique_ptr<Node> CreateNaryNode(
    std::string_view operator_text,
    std::vector<std::unique_ptr<Node>>&& operands) {
  if (operands.empty()) {
    return nullptr;
  }
  if (operands.size() == 1) {
    return std::move(operands.at(0));
  }
  return std::make_unique<NaryOperatorNode>(std::string(operator_text),
                                            std::move(operands));
}

}  // namespace

libtextclassifier3::Status Parser::Consume(Lexer::TokenType token_type) {
  if (!Match(token_type)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Unable to consume token %d.", static_cast<int>(token_type)));
  }
  ++current_token_;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<std::unique_ptr<TextNode>> Parser::ConsumeText() {
  if (!Match(Lexer::TokenType::TEXT)) {
    return absl_ports::InvalidArgumentError("Unable to consume token as TEXT.");
  }
  auto text_node = std::make_unique<TextNode>(std::move(current_token_->text),
                                              current_token_->original_text);
  ++current_token_;
  return text_node;
}

libtextclassifier3::StatusOr<std::unique_ptr<FunctionNameNode>>
Parser::ConsumeFunctionName() {
  if (!Match(Lexer::TokenType::FUNCTION_NAME)) {
    return absl_ports::InvalidArgumentError(
        "Unable to consume token as FUNCTION_NAME.");
  }
  auto function_name_node =
      std::make_unique<FunctionNameNode>(std::move(current_token_->text));
  ++current_token_;
  return function_name_node;
}

// stringElement
//    : STRING STAR?
libtextclassifier3::StatusOr<std::unique_ptr<StringNode>>
Parser::ConsumeStringElement() {
  if (!Match(Lexer::TokenType::STRING)) {
    return absl_ports::InvalidArgumentError(
        "Unable to consume token as STRING.");
  }
  std::string text = std::move(current_token_->text);
  std::string_view raw_text = current_token_->original_text;
  ++current_token_;

  bool is_prefix = false;
  if (Match(Lexer::TokenType::STAR)) {
    is_prefix = true;
    ++current_token_;
  }

  return std::make_unique<StringNode>(std::move(text), raw_text, is_prefix);
}

libtextclassifier3::StatusOr<std::string> Parser::ConsumeComparator() {
  if (!Match(Lexer::TokenType::COMPARATOR)) {
    return absl_ports::InvalidArgumentError(
        "Unable to consume token as COMPARATOR.");
  }
  std::string comparator = std::move(current_token_->text);
  ++current_token_;
  return comparator;
}

// member
//    :  TEXT (DOT TEXT)* (DOT function)?
//    |  TEXT STAR
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<MemberNode>>
Parser::ConsumeMember() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<TextNode> text_node, ConsumeText());
  std::vector<std::unique_ptr<TextNode>> children;

  // Member could be either `TEXT (DOT TEXT)* (DOT function)?` or `TEXT STAR`
  // at this point. So check for 'STAR' to differentiate the two cases.
  if (Match(Lexer::TokenType::STAR)) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::STAR));
    std::string_view raw_text = text_node->raw_value();
    std::string text = std::move(*text_node).value();
    text_node = std::make_unique<TextNode>(std::move(text), raw_text,
                                           /*is_prefix=*/true);
    children.push_back(std::move(text_node));
  } else {
    children.push_back(std::move(text_node));
    while (Match(Lexer::TokenType::DOT)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::DOT));
      if (MatchFunction()) {
        ICING_ASSIGN_OR_RETURN(std::unique_ptr<FunctionNode> function_node,
                               ConsumeFunction());
        // Once a function is matched, we should exit the current rule based on
        // the grammar.
        return std::make_unique<MemberNode>(std::move(children),
                                            std::move(function_node));
      }
      ICING_ASSIGN_OR_RETURN(text_node, ConsumeText());
      children.push_back(std::move(text_node));
    }
  }
  return std::make_unique<MemberNode>(std::move(children),
                                      /*function=*/nullptr);
}

// function
//    : FUNCTION_NAME LPAREN argList? RPAREN
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<FunctionNode>>
Parser::ConsumeFunction() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<FunctionNameNode> function_name,
                         ConsumeFunctionName());
  ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::LPAREN));

  std::vector<std::unique_ptr<Node>> args;
  if (Match(Lexer::TokenType::RPAREN)) {
    // Got empty argument.
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::RPAREN));
  } else {
    ICING_ASSIGN_OR_RETURN(args, ConsumeArgs());
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::RPAREN));
  }
  return std::make_unique<FunctionNode>(std::move(function_name),
                                        std::move(args));
}

// comparable
//     : stringElement
//     | member
//     | function
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>>
Parser::ConsumeComparable() {
  if (Match(Lexer::TokenType::STRING)) {
    return ConsumeStringElement();
  } else if (MatchMember()) {
    return ConsumeMember();
  }
  // The current token sequence isn't a STRING or member. Therefore, it must be
  // a function.
  return ConsumeFunction();
}

// composite
//    : LPAREN expression RPAREN
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeComposite() {
  ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::LPAREN));

  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> expression, ConsumeExpression());

  ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::RPAREN));
  return expression;
}

// argList
//    : expression (COMMA expression)*
//    ;
libtextclassifier3::StatusOr<std::vector<std::unique_ptr<Node>>>
Parser::ConsumeArgs() {
  std::vector<std::unique_ptr<Node>> args;
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> arg, ConsumeExpression());
  args.push_back(std::move(arg));
  while (Match(Lexer::TokenType::COMMA)) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::COMMA));
    ICING_ASSIGN_OR_RETURN(arg, ConsumeExpression());
    args.push_back(std::move(arg));
  }
  return args;
}

// restriction
//     : comparable (COMPARATOR MINUS? (comparable | composite))?
//     ;
// COMPARATOR will not be produced in Scoring Lexer.
libtextclassifier3::StatusOr<std::unique_ptr<Node>>
Parser::ConsumeRestriction() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> comparable, ConsumeComparable());

  if (!Match(Lexer::TokenType::COMPARATOR)) {
    return comparable;
  }
  ICING_ASSIGN_OR_RETURN(std::string operator_text, ConsumeComparator());

  bool has_minus = Match(Lexer::TokenType::MINUS);
  if (has_minus) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::MINUS));
  }

  std::unique_ptr<Node> arg;
  if (MatchComposite()) {
    ICING_ASSIGN_OR_RETURN(arg, ConsumeComposite());
  } else if (MatchComparable()) {
    ICING_ASSIGN_OR_RETURN(arg, ConsumeComparable());
  } else {
    return absl_ports::InvalidArgumentError(
        "ARG: must begin with LPAREN or FIRST(comparable)");
  }

  if (has_minus) {
    arg = std::make_unique<UnaryOperatorNode>("MINUS", std::move(arg));
  }

  std::vector<std::unique_ptr<Node>> args;
  args.push_back(std::move(comparable));
  args.push_back(std::move(arg));
  return std::make_unique<NaryOperatorNode>(std::move(operator_text),
                                            std::move(args));
}

// simple
//     : restriction
//     | composite
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeSimple() {
  if (MatchComposite()) {
    return ConsumeComposite();
  } else if (MatchRestriction()) {
    return ConsumeRestriction();
  }
  return absl_ports::InvalidArgumentError(
      "SIMPLE: must be a restriction or composite");
}

// term
//     : NOT? simple
//     | MINUS simple
//     ;
// NOT will not be produced in Scoring Lexer.
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeTerm() {
  if (!Match(Lexer::TokenType::NOT) && !Match(Lexer::TokenType::MINUS)) {
    return ConsumeSimple();
  }
  std::string operator_text;
  if (language_ == Lexer::Language::SCORING) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::MINUS));
    operator_text = "MINUS";
  } else {
    if (Match(Lexer::TokenType::NOT)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::NOT));
      operator_text = "NOT";
    } else {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::MINUS));
      operator_text = "MINUS";
    }
  }
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> simple, ConsumeSimple());
  return std::make_unique<UnaryOperatorNode>(operator_text, std::move(simple));
}

// factor
//     : term (OR term)*
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeFactor() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> term, ConsumeTerm());
  std::vector<std::unique_ptr<Node>> terms;
  terms.push_back(std::move(term));

  while (Match(Lexer::TokenType::OR)) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::OR));
    ICING_ASSIGN_OR_RETURN(term, ConsumeTerm());
    terms.push_back(std::move(term));
  }

  return CreateNaryNode("OR", std::move(terms));
}

// sequence
//    : (factor)+
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeSequence() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> factor, ConsumeFactor());
  std::vector<std::unique_ptr<Node>> factors;
  factors.push_back(std::move(factor));

  while (MatchFactor()) {
    ICING_ASSIGN_OR_RETURN(factor, ConsumeFactor());
    factors.push_back(std::move(factor));
  }

  return CreateNaryNode("AND", std::move(factors));
}

// expression
//    : sequence (AND sequence)*
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>>
Parser::ConsumeQueryExpression() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> sequence, ConsumeSequence());
  std::vector<std::unique_ptr<Node>> sequences;
  sequences.push_back(std::move(sequence));

  while (Match(Lexer::TokenType::AND)) {
    ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::AND));
    ICING_ASSIGN_OR_RETURN(sequence, ConsumeSequence());
    sequences.push_back(std::move(sequence));
  }

  return CreateNaryNode("AND", std::move(sequences));
}

// multExpr
//     : term ((TIMES | DIV) term)*
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeMultExpr() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> node, ConsumeTerm());
  std::vector<std::unique_ptr<Node>> stack;
  stack.push_back(std::move(node));

  while (Match(Lexer::TokenType::TIMES) || Match(Lexer::TokenType::DIV)) {
    while (Match(Lexer::TokenType::TIMES)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::TIMES));
      ICING_ASSIGN_OR_RETURN(node, ConsumeTerm());
      stack.push_back(std::move(node));
    }
    node = CreateNaryNode("TIMES", std::move(stack));
    stack.clear();
    stack.push_back(std::move(node));

    while (Match(Lexer::TokenType::DIV)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::DIV));
      ICING_ASSIGN_OR_RETURN(node, ConsumeTerm());
      stack.push_back(std::move(node));
    }
    node = CreateNaryNode("DIV", std::move(stack));
    stack.clear();
    stack.push_back(std::move(node));
  }

  return std::move(stack[0]);
}

// expression
//    : multExpr ((PLUS | MINUS) multExpr)*
//    ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>>
Parser::ConsumeScoringExpression() {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Node> node, ConsumeMultExpr());
  std::vector<std::unique_ptr<Node>> stack;
  stack.push_back(std::move(node));

  while (Match(Lexer::TokenType::PLUS) || Match(Lexer::TokenType::MINUS)) {
    while (Match(Lexer::TokenType::PLUS)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::PLUS));
      ICING_ASSIGN_OR_RETURN(node, ConsumeMultExpr());
      stack.push_back(std::move(node));
    }
    node = CreateNaryNode("PLUS", std::move(stack));
    stack.clear();
    stack.push_back(std::move(node));

    while (Match(Lexer::TokenType::MINUS)) {
      ICING_RETURN_IF_ERROR(Consume(Lexer::TokenType::MINUS));
      ICING_ASSIGN_OR_RETURN(node, ConsumeMultExpr());
      stack.push_back(std::move(node));
    }
    node = CreateNaryNode("MINUS", std::move(stack));
    stack.clear();
    stack.push_back(std::move(node));
  }

  return std::move(stack[0]);
}

libtextclassifier3::StatusOr<std::unique_ptr<Node>>
Parser::ConsumeExpression() {
  switch (language_) {
    case Lexer::Language::QUERY:
      return ConsumeQueryExpression();
    case Lexer::Language::SCORING:
      return ConsumeScoringExpression();
  }
}

// query
//     : expression? EOF
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeQuery() {
  language_ = Lexer::Language::QUERY;
  std::unique_ptr<Node> node;
  if (current_token_ != lexer_tokens_.end()) {
    ICING_ASSIGN_OR_RETURN(node, ConsumeExpression());
  }
  if (current_token_ != lexer_tokens_.end()) {
    return absl_ports::InvalidArgumentError(
        "Error parsing Query. Must reach EOF after parsing Expression!");
  }
  return node;
}

// scoring
//     : expression EOF
//     ;
libtextclassifier3::StatusOr<std::unique_ptr<Node>> Parser::ConsumeScoring() {
  language_ = Lexer::Language::SCORING;
  std::unique_ptr<Node> node;
  if (current_token_ == lexer_tokens_.end()) {
    return absl_ports::InvalidArgumentError("Got empty scoring expression!");
  }
  ICING_ASSIGN_OR_RETURN(node, ConsumeExpression());
  if (current_token_ != lexer_tokens_.end()) {
    return absl_ports::InvalidArgumentError(
        "Error parsing the scoring expression. Must reach EOF after parsing "
        "Expression!");
  }
  return node;
}

}  // namespace lib
}  // namespace icing
