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

#include "utils/grammar/semantics/evaluators/merge-values-eval.h"

namespace libtextclassifier3::grammar {

StatusOr<const SemanticValue*> MergeValuesEvaluator::Apply(
    const EvalContext& context, const SemanticExpression* expression,
    UnsafeArena* arena) const {
  const MergeValueExpression* merge_value_expression =
      expression->expression_as_MergeValueExpression();
  std::unique_ptr<MutableFlatbuffer> result =
      semantic_value_builder_.NewTable(merge_value_expression->type());

  if (result == nullptr) {
    return Status(StatusCode::INVALID_ARGUMENT, "Invalid result type.");
  }

  for (const SemanticExpression* semantic_expression :
       *merge_value_expression->values()) {
    TC3_ASSIGN_OR_RETURN(const SemanticValue* value,
                         composer_->Apply(context, semantic_expression, arena));
    if (value == nullptr) {
      continue;
    }
    if ((value->type() != result->type()) ||
        !result->MergeFrom(value->Table())) {
      return Status(StatusCode::INVALID_ARGUMENT,
                    "Could not merge the results.");
    }
  }
  return SemanticValue::Create<const MutableFlatbuffer*>(result.get(), arena);
}

}  // namespace libtextclassifier3::grammar
