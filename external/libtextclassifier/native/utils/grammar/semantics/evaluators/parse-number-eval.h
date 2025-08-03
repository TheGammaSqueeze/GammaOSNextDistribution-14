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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_PARSE_NUMBER_EVAL_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_PARSE_NUMBER_EVAL_H_

#include <string>

#include "utils/base/arena.h"
#include "utils/base/statusor.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/semantics/value.h"
#include "utils/strings/numbers.h"

namespace libtextclassifier3::grammar {

// Parses a string as a number.
class ParseNumberEvaluator : public SemanticExpressionEvaluator {
 public:
  explicit ParseNumberEvaluator(const SemanticExpressionEvaluator* composer)
      : composer_(composer) {}

  StatusOr<const SemanticValue*> Apply(const EvalContext& context,
                                       const SemanticExpression* expression,
                                       UnsafeArena* arena) const override {
    TC3_DCHECK_EQ(expression->expression_type(),
                  SemanticExpression_::Expression_ParseNumberExpression);
    const ParseNumberExpression* parse_number_expression =
        expression->expression_as_ParseNumberExpression();

    // Evaluate argument.
    TC3_ASSIGN_OR_RETURN(
        const SemanticValue* value,
        composer_->Apply(context, parse_number_expression->value(), arena));
    if (value == nullptr) {
      return nullptr;
    }
    if (!value->Has<StringPiece>()) {
      return Status(StatusCode::INVALID_ARGUMENT,
                    "Argument didn't evaluate as a string value.");
    }
    const std::string data = value->Value<std::string>();

    // Parse the string data as a number.
    const reflection::BaseType type =
        static_cast<reflection::BaseType>(parse_number_expression->base_type());
    if (flatbuffers::IsLong(type)) {
      TC3_ASSIGN_OR_RETURN(const int64 value, TryParse<int64>(data));
      return SemanticValue::Create(type, value, arena);
    } else if (flatbuffers::IsInteger(type)) {
      TC3_ASSIGN_OR_RETURN(const int32 value, TryParse<int32>(data));
      return SemanticValue::Create(type, value, arena);
    } else if (flatbuffers::IsFloat(type)) {
      TC3_ASSIGN_OR_RETURN(const double value, TryParse<double>(data));
      return SemanticValue::Create(type, value, arena);
    } else {
      return Status(StatusCode::INVALID_ARGUMENT,
                    "Unsupported type: " + std::to_string(type));
    }
  }

 private:
  template <typename T>
  bool Parse(const std::string& data, T* value) const;

  template <>
  bool Parse(const std::string& data, int32* value) const {
    return ParseInt32(data.data(), value);
  }

  template <>
  bool Parse(const std::string& data, int64* value) const {
    return ParseInt64(data.data(), value);
  }

  template <>
  bool Parse(const std::string& data, double* value) const {
    return ParseDouble(data.data(), value);
  }

  template <typename T>
  StatusOr<T> TryParse(const std::string& data) const {
    T result;
    if (!Parse<T>(data, &result)) {
      return Status(StatusCode::INVALID_ARGUMENT, "Could not parse value.");
    }
    return result;
  }

  const SemanticExpressionEvaluator* composer_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_EVALUATORS_PARSE_NUMBER_EVAL_H_
