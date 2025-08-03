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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATOR_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATOR_H_

#include "utils/base/arena.h"
#include "utils/base/statusor.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"

namespace libtextclassifier3::grammar {

// Interface for a semantic function that evaluates an expression and returns
// a semantic value.
class SemanticExpressionEvaluator {
 public:
  virtual ~SemanticExpressionEvaluator() = default;

  // Applies `expression` to the `context` to produce a semantic value.
  virtual StatusOr<const SemanticValue*> Apply(
      const EvalContext& context, const SemanticExpression* expression,
      UnsafeArena* arena) const = 0;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATOR_H_
