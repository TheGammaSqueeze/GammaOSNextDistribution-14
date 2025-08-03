/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/grammar/semantics/composer.h"

#include "utils/base/status_macros.h"
#include "utils/grammar/semantics/evaluators/arithmetic-eval.h"
#include "utils/grammar/semantics/evaluators/compose-eval.h"
#include "utils/grammar/semantics/evaluators/const-eval.h"
#include "utils/grammar/semantics/evaluators/constituent-eval.h"
#include "utils/grammar/semantics/evaluators/merge-values-eval.h"
#include "utils/grammar/semantics/evaluators/parse-number-eval.h"
#include "utils/grammar/semantics/evaluators/span-eval.h"

namespace libtextclassifier3::grammar {
namespace {

// Gathers all constituents of a rule and index them.
// The constituents are numbered in the rule construction. But consituents could
// be in optional parts of the rule and might not be present in a match.
// This finds all constituents that are present in a match and allows to
// retrieve them by their index.
std::unordered_map<int, const ParseTree*> GatherConstituents(
    const ParseTree* root) {
  std::unordered_map<int, const ParseTree*> constituents;
  Traverse(root, [root, &constituents](const ParseTree* node) {
    switch (node->type) {
      case ParseTree::Type::kMapping:
        TC3_CHECK(node->IsUnaryRule());
        constituents[static_cast<const MappingNode*>(node)->id] =
            node->unary_rule_rhs();
        return false;
      case ParseTree::Type::kDefault:
        // Continue traversal.
        return true;
      default:
        // Don't continue the traversal if we are not at the root node.
        // This could e.g. be an assertion node.
        return (node == root);
    }
  });
  return constituents;
}

}  // namespace

SemanticComposer::SemanticComposer(
    const reflection::Schema* semantic_values_schema) {
  evaluators_.emplace(SemanticExpression_::Expression_ArithmeticExpression,
                      std::make_unique<ArithmeticExpressionEvaluator>(this));
  evaluators_.emplace(SemanticExpression_::Expression_ConstituentExpression,
                      std::make_unique<ConstituentEvaluator>());
  evaluators_.emplace(SemanticExpression_::Expression_ParseNumberExpression,
                      std::make_unique<ParseNumberEvaluator>(this));
  evaluators_.emplace(SemanticExpression_::Expression_SpanAsStringExpression,
                      std::make_unique<SpanAsStringEvaluator>());
  if (semantic_values_schema != nullptr) {
    // Register semantic functions.
    evaluators_.emplace(
        SemanticExpression_::Expression_ComposeExpression,
        std::make_unique<ComposeEvaluator>(this, semantic_values_schema));
    evaluators_.emplace(
        SemanticExpression_::Expression_ConstValueExpression,
        std::make_unique<ConstEvaluator>(semantic_values_schema));
    evaluators_.emplace(
        SemanticExpression_::Expression_MergeValueExpression,
        std::make_unique<MergeValuesEvaluator>(this, semantic_values_schema));
  }
}

StatusOr<const SemanticValue*> SemanticComposer::Eval(
    const TextContext& text_context, const Derivation& derivation,
    UnsafeArena* arena) const {
  if (!derivation.parse_tree->IsUnaryRule() ||
      derivation.parse_tree->unary_rule_rhs()->type !=
          ParseTree::Type::kExpression) {
    return nullptr;
  }
  return Eval(text_context,
              static_cast<const SemanticExpressionNode*>(
                  derivation.parse_tree->unary_rule_rhs()),
              arena);
}

StatusOr<const SemanticValue*> SemanticComposer::Eval(
    const TextContext& text_context, const SemanticExpressionNode* derivation,
    UnsafeArena* arena) const {
  // Evaluate constituents.
  EvalContext context{&text_context, derivation};
  for (const auto& [constituent_index, constituent] :
       GatherConstituents(derivation)) {
    if (constituent->type == ParseTree::Type::kExpression) {
      TC3_ASSIGN_OR_RETURN(
          context.rule_constituents[constituent_index],
          Eval(text_context,
               static_cast<const SemanticExpressionNode*>(constituent), arena));
    } else {
      // Just use the text of the constituent if no semantic expression was
      // defined.
      context.rule_constituents[constituent_index] = SemanticValue::Create(
          text_context.Span(constituent->codepoint_span), arena);
    }
  }
  return Apply(context, derivation->expression, arena);
}

StatusOr<const SemanticValue*> SemanticComposer::Apply(
    const EvalContext& context, const SemanticExpression* expression,
    UnsafeArena* arena) const {
  const auto handler_it = evaluators_.find(expression->expression_type());
  if (handler_it == evaluators_.end()) {
    return Status(StatusCode::INVALID_ARGUMENT,
                  std::string("Unhandled expression type: ") +
                      EnumNameExpression(expression->expression_type()));
  }
  return handler_it->second->Apply(context, expression, arena);
}

}  // namespace libtextclassifier3::grammar
