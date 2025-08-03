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

#include <vector>

#include "utils/base/statusor.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/reflection.h"
#include "utils/flatbuffers/test-utils.h"
#include "utils/grammar/semantics/evaluator.h"
#include "utils/grammar/semantics/evaluators/const-eval.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/testing/value_generated.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3::grammar {
namespace {

template <typename T>
class ArithmeticExpressionEvaluatorTest : public GrammarTest {
 protected:
  T Eval(const ArithmeticExpression_::Operator op) {
    ArithmeticExpressionT arithmetic_expression;
    arithmetic_expression.base_type = flatbuffers_base_type<T>::value;
    arithmetic_expression.op = op;
    arithmetic_expression.values.push_back(
        CreatePrimitiveConstExpression<T>(1));
    arithmetic_expression.values.push_back(
        CreatePrimitiveConstExpression<T>(2));
    arithmetic_expression.values.push_back(
        CreatePrimitiveConstExpression<T>(3));
    arithmetic_expression.values.push_back(
        CreatePrimitiveConstExpression<T>(4));
    OwnedFlatbuffer<SemanticExpression> expression =
        CreateExpression(std::move(arithmetic_expression));

    // Setup evaluators.
    ConstEvaluator const_eval(semantic_values_schema_.get());
    ArithmeticExpressionEvaluator arithmetic_eval(&const_eval);

    // Run evaluator.
    StatusOr<const SemanticValue*> result =
        arithmetic_eval.Apply(/*context=*/{}, expression.get(), &arena_);

    // Check result.
    EXPECT_TRUE(result.ok());
    const SemanticValue* result_value = result.ValueOrDie();
    EXPECT_TRUE(result_value != nullptr);
    return result_value->Value<T>();
  }
};

using NumberTypes = ::testing::Types<int8, uint8, int16, uint16, int32, uint32,
                                     int64, uint64, double, float>;
TYPED_TEST_SUITE(ArithmeticExpressionEvaluatorTest, NumberTypes);

TYPED_TEST(ArithmeticExpressionEvaluatorTest, ParsesNumber) {
  EXPECT_EQ(this->Eval(ArithmeticExpression_::Operator_OP_ADD), 10);
  EXPECT_EQ(this->Eval(ArithmeticExpression_::Operator_OP_MUL), 24);
  EXPECT_EQ(this->Eval(ArithmeticExpression_::Operator_OP_MIN), 1);
  EXPECT_EQ(this->Eval(ArithmeticExpression_::Operator_OP_MAX), 4);
}

}  // namespace
}  // namespace libtextclassifier3::grammar
