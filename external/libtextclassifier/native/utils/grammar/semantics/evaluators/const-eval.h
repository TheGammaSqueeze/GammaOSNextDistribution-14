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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONST_EVAL_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONST_EVAL_H_

#include "utils/base/arena.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"

namespace libtextclassifier3::grammar {

// Returns a constant value of a given type.
class ConstEvaluator : public SemanticExpressionEvaluator {
 public:
  explicit ConstEvaluator(const reflection::Schema* semantic_values_schema)
      : semantic_values_schema_(semantic_values_schema) {}

  StatusOr<const SemanticValue*> Apply(const EvalContext&,
                                       const SemanticExpression* expression,
                                       UnsafeArena* arena) const override {
    TC3_DCHECK_EQ(expression->expression_type(),
                  SemanticExpression_::Expression_ConstValueExpression);
    const ConstValueExpression* const_value_expression =
        expression->expression_as_ConstValueExpression();
    const reflection::BaseType base_type =
        static_cast<reflection::BaseType>(const_value_expression->base_type());
    const StringPiece data = StringPiece(
        reinterpret_cast<const char*>(const_value_expression->value()->data()),
        const_value_expression->value()->size());

    if (base_type == reflection::BaseType::Obj) {
      // Resolve the object type.
      const int type_id = const_value_expression->type();
      if (type_id < 0 ||
          type_id >= semantic_values_schema_->objects()->size()) {
        return Status(StatusCode::INVALID_ARGUMENT, "Invalid type.");
      }
      return SemanticValue::Create(semantic_values_schema_->objects()->Get(
                                       const_value_expression->type()),
                                   data, arena);
    } else {
      return SemanticValue::Create(base_type, data, arena);
    }
  }

 private:
  const reflection::Schema* semantic_values_schema_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_CONST_EVAL_H_
