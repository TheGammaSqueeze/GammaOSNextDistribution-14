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

#include "utils/grammar/semantics/evaluators/arithmetic-eval.h"

#include <limits>

namespace libtextclassifier3::grammar {
namespace {

template <typename T>
StatusOr<const SemanticValue*> Reduce(
    const SemanticExpressionEvaluator* composer, const EvalContext& context,
    const ArithmeticExpression* expression, UnsafeArena* arena) {
  T result;
  switch (expression->op()) {
    case ArithmeticExpression_::Operator_OP_ADD: {
      result = 0;
      break;
    }
    case ArithmeticExpression_::Operator_OP_MUL: {
      result = 1;
      break;
    }
    case ArithmeticExpression_::Operator_OP_MIN: {
      result = std::numeric_limits<T>::max();
      break;
    }
    case ArithmeticExpression_::Operator_OP_MAX: {
      result = std::numeric_limits<T>::min();
      break;
    }
    default: {
      return Status(StatusCode::INVALID_ARGUMENT,
                    "Unexpected op: " +
                        std::string(ArithmeticExpression_::EnumNameOperator(
                            expression->op())));
    }
  }
  if (expression->values() != nullptr) {
    for (const SemanticExpression* semantic_expression :
         *expression->values()) {
      TC3_ASSIGN_OR_RETURN(
          const SemanticValue* value,
          composer->Apply(context, semantic_expression, arena));
      if (value == nullptr) {
        continue;
      }
      if (!value->Has<T>()) {
        return Status(
            StatusCode::INVALID_ARGUMENT,
            "Argument didn't evaluate as expected type: " +
                std::string(reflection::EnumNameBaseType(value->base_type())));
      }
      const T scalar_value = value->Value<T>();
      switch (expression->op()) {
        case ArithmeticExpression_::Operator_OP_ADD: {
          result += scalar_value;
          break;
        }
        case ArithmeticExpression_::Operator_OP_MUL: {
          result *= scalar_value;
          break;
        }
        case ArithmeticExpression_::Operator_OP_MIN: {
          result = std::min(result, scalar_value);
          break;
        }
        case ArithmeticExpression_::Operator_OP_MAX: {
          result = std::max(result, scalar_value);
          break;
        }
        default: {
          break;
        }
      }
    }
  }
  return SemanticValue::Create(result, arena);
}

}  // namespace

StatusOr<const SemanticValue*> ArithmeticExpressionEvaluator::Apply(
    const EvalContext& context, const SemanticExpression* expression,
    UnsafeArena* arena) const {
  TC3_DCHECK_EQ(expression->expression_type(),
                SemanticExpression_::Expression_ArithmeticExpression);
  const ArithmeticExpression* arithmetic_expression =
      expression->expression_as_ArithmeticExpression();
  switch (arithmetic_expression->base_type()) {
    case reflection::BaseType::Byte:
      return Reduce<int8>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::UByte:
      return Reduce<uint8>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::Short:
      return Reduce<int16>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::UShort:
      return Reduce<uint16>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::Int:
      return Reduce<int32>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::UInt:
      return Reduce<uint32>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::Long:
      return Reduce<int64>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::ULong:
      return Reduce<uint64>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::Float:
      return Reduce<float>(composer_, context, arithmetic_expression, arena);
    case reflection::BaseType::Double:
      return Reduce<double>(composer_, context, arithmetic_expression, arena);
    default:
      return Status(StatusCode::INVALID_ARGUMENT,
                    "Unsupported for ArithmeticExpression: " +
                        std::string(reflection::EnumNameBaseType(
                            static_cast<reflection::BaseType>(
                                arithmetic_expression->base_type()))));
  }
}

}  // namespace libtextclassifier3::grammar
