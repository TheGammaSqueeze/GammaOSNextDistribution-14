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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_COMPOSER_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_COMPOSER_H_

#include <unordered_map>
#include <vector>

#include "utils/base/arena.h"
#include "utils/base/status.h"
#include "utils/base/statusor.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/parse-tree.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"
#include "utils/grammar/text-context.h"

namespace libtextclassifier3::grammar {

// Semantic value composer.
// It evaluates a semantic expression of a syntactic parse tree as a semantic
// value.
// It evaluates the constituents of a rule match and applies them to semantic
// expression, calling out to semantic functions that implement the basic
// building blocks.
class SemanticComposer : public SemanticExpressionEvaluator {
 public:
  // Expects a flatbuffer schema that describes the possible result values of
  // an evaluation.
  explicit SemanticComposer(const reflection::Schema* semantic_values_schema);

  // Evaluates a semantic expression that is associated with the root of a parse
  // tree.
  StatusOr<const SemanticValue*> Eval(const TextContext& text_context,
                                      const Derivation& derivation,
                                      UnsafeArena* arena) const;

  // Applies a semantic expression to a list of constituents and
  // produces an output semantic value.
  StatusOr<const SemanticValue*> Apply(const EvalContext& context,
                                       const SemanticExpression* expression,
                                       UnsafeArena* arena) const override;

 private:
  // Evaluates a semantic expression against a parse tree.
  StatusOr<const SemanticValue*> Eval(const TextContext& text_context,
                                      const SemanticExpressionNode* derivation,
                                      UnsafeArena* arena) const;

  std::unordered_map<SemanticExpression_::Expression,
                     std::unique_ptr<SemanticExpressionEvaluator>>
      evaluators_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_COMPOSER_H_
