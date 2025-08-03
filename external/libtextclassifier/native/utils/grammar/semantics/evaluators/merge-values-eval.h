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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_MERGE_VALUES_EVAL_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_MERGE_VALUES_EVAL_H_

#include "utils/base/arena.h"
#include "utils/base/status_macros.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"

namespace libtextclassifier3::grammar {

// Evaluate the “merge” semantic function expression.
// Conceptually, the way this merge evaluator works is that each of the
// arguments (semantic value) is merged into a return type semantic value.
class MergeValuesEvaluator : public SemanticExpressionEvaluator {
 public:
  explicit MergeValuesEvaluator(
      const SemanticExpressionEvaluator* composer,
      const reflection::Schema* semantic_values_schema)
      : composer_(composer), semantic_value_builder_(semantic_values_schema) {}

  StatusOr<const SemanticValue*> Apply(const EvalContext& context,
                                       const SemanticExpression* expression,
                                       UnsafeArena* arena) const override;

 private:
  const SemanticExpressionEvaluator* composer_;
  const MutableFlatbufferBuilder semantic_value_builder_;
};
}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_MERGE_VALUES_EVAL_H_
