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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONSTITUENT_EVAL_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONSTITUENT_EVAL_H_

#include "utils/base/arena.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"

namespace libtextclassifier3::grammar {

// Returns the semantic value of an evaluated constituent.
class ConstituentEvaluator : public SemanticExpressionEvaluator {
 public:
  StatusOr<const SemanticValue*> Apply(const EvalContext& context,
                                       const SemanticExpression* expression,
                                       UnsafeArena*) const override {
    TC3_DCHECK_EQ(expression->expression_type(),
                  SemanticExpression_::Expression_ConstituentExpression);
    const ConstituentExpression* constituent_expression =
        expression->expression_as_ConstituentExpression();
    const auto constituent_it =
        context.rule_constituents.find(constituent_expression->id());
    if (constituent_it != context.rule_constituents.end()) {
      return constituent_it->second;
    }
    // The constituent was not present in the rule parse tree, return a
    // null value for it.
    return nullptr;
  }
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONSTITUENT_EVAL_H_
