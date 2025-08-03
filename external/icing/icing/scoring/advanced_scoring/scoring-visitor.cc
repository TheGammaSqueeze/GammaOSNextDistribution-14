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

#include "icing/scoring/advanced_scoring/scoring-visitor.h"

#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {

void ScoringVisitor::VisitFunctionName(const FunctionNameNode* node) {
  pending_error_ = absl_ports::InternalError(
      "FunctionNameNode should be handled in VisitFunction!");
}

void ScoringVisitor::VisitString(const StringNode* node) {
  pending_error_ =
      absl_ports::InvalidArgumentError("Scoring does not support String!");
}

void ScoringVisitor::VisitText(const TextNode* node) {
  pending_error_ =
      absl_ports::InternalError("TextNode should be handled in VisitMember!");
}

void ScoringVisitor::VisitMember(const MemberNode* node) {
  bool is_member_function = node->function() != nullptr;
  if (is_member_function) {
    // If the member node represents a member function, it must have only one
    // child for "this".
    if (node->children().size() != 1 ||
        node->children()[0]->value() != "this") {
      pending_error_ = absl_ports::InvalidArgumentError(
          "Member functions can only be called via \"this\".");
      return;
    }
    return VisitFunctionHelper(node->function(), is_member_function);
  }
  std::string value;
  if (node->children().size() == 1) {
    // If a member has only one child, then it represents a integer literal.
    value = node->children()[0]->value();
  } else if (node->children().size() == 2) {
    // If a member has two children, then it can only represent a floating point
    // number, so we need to join them by "." to build the numeric literal.
    value = absl_ports::StrCat(node->children()[0]->value(), ".",
                               node->children()[1]->value());
  } else {
    pending_error_ = absl_ports::InvalidArgumentError(
        "MemberNode must have 1 or 2 children.");
    return;
  }
  char* end;
  double number = std::strtod(value.c_str(), &end);
  if (end != value.c_str() + value.length()) {
    // While it would be doable to support property references in the scoring
    // grammar, we currently don't have an efficient way to support such a
    // lookup (we'd have to read each document). As such, it's simpler to just
    // restrict the scoring language to not include properties.
    pending_error_ = absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Expect a numeric literal, but got ", value));
    return;
  }
  stack_.push_back(ConstantScoreExpression::Create(number));
}

void ScoringVisitor::VisitFunctionHelper(const FunctionNode* node,
                                         bool is_member_function) {
  std::vector<std::unique_ptr<ScoreExpression>> args;
  if (is_member_function) {
    args.push_back(ThisExpression::Create());
  }
  for (const auto& arg : node->args()) {
    arg->Accept(this);
    if (has_pending_error()) {
      return;
    }
    args.push_back(pop_stack());
  }
  const std::string& function_name = node->function_name()->value();
  libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>> expression =
      absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Unknown function: ", function_name));

  if (DocumentFunctionScoreExpression::kFunctionNames.find(function_name) !=
      DocumentFunctionScoreExpression::kFunctionNames.end()) {
    // Document-based function
    expression = DocumentFunctionScoreExpression::Create(
        DocumentFunctionScoreExpression::kFunctionNames.at(function_name),
        std::move(args), &document_store_, default_score_, current_time_ms_);
  } else if (function_name ==
             RelevanceScoreFunctionScoreExpression::kFunctionName) {
    // relevanceScore function
    expression = RelevanceScoreFunctionScoreExpression::Create(
        std::move(args), &bm25f_calculator_, default_score_);
  } else if (function_name ==
             ChildrenRankingSignalsFunctionScoreExpression::kFunctionName) {
    // childrenRankingSignals function
    expression = ChildrenRankingSignalsFunctionScoreExpression::Create(
        std::move(args), join_children_fetcher_);
  } else if (function_name ==
             PropertyWeightsFunctionScoreExpression::kFunctionName) {
    // propertyWeights function
    expression = PropertyWeightsFunctionScoreExpression::Create(
        std::move(args), &document_store_, &section_weights_, current_time_ms_);
  } else if (MathFunctionScoreExpression::kFunctionNames.find(function_name) !=
             MathFunctionScoreExpression::kFunctionNames.end()) {
    // Math functions
    expression = MathFunctionScoreExpression::Create(
        MathFunctionScoreExpression::kFunctionNames.at(function_name),
        std::move(args));
  }

  if (!expression.ok()) {
    pending_error_ = expression.status();
    return;
  }
  stack_.push_back(std::move(expression).ValueOrDie());
}

void ScoringVisitor::VisitUnaryOperator(const UnaryOperatorNode* node) {
  if (node->operator_text() != "MINUS") {
    pending_error_ = absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Unknown unary operator: ", node->operator_text()));
    return;
  }
  node->child()->Accept(this);
  if (has_pending_error()) {
    return;
  }
  std::vector<std::unique_ptr<ScoreExpression>> children;
  children.push_back(pop_stack());

  libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>> expression =
      OperatorScoreExpression::Create(
          OperatorScoreExpression::OperatorType::kNegative,
          std::move(children));
  if (!expression.ok()) {
    pending_error_ = expression.status();
    return;
  }
  stack_.push_back(std::move(expression).ValueOrDie());
}

void ScoringVisitor::VisitNaryOperator(const NaryOperatorNode* node) {
  std::vector<std::unique_ptr<ScoreExpression>> children;
  for (const auto& arg : node->children()) {
    arg->Accept(this);
    if (has_pending_error()) {
      return;
    }
    children.push_back(pop_stack());
  }

  libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>> expression =
      absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Unknown Nary operator: ", node->operator_text()));

  if (node->operator_text() == "PLUS") {
    expression = OperatorScoreExpression::Create(
        OperatorScoreExpression::OperatorType::kPlus, std::move(children));
  } else if (node->operator_text() == "MINUS") {
    expression = OperatorScoreExpression::Create(
        OperatorScoreExpression::OperatorType::kMinus, std::move(children));
  } else if (node->operator_text() == "TIMES") {
    expression = OperatorScoreExpression::Create(
        OperatorScoreExpression::OperatorType::kTimes, std::move(children));
  } else if (node->operator_text() == "DIV") {
    expression = OperatorScoreExpression::Create(
        OperatorScoreExpression::OperatorType::kDiv, std::move(children));
  }
  if (!expression.ok()) {
    pending_error_ = expression.status();
    return;
  }
  stack_.push_back(std::move(expression).ValueOrDie());
}

}  // namespace lib
}  // namespace icing
