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

#include "utils/grammar/semantics/evaluators/parse-number-eval.h"

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
class ParseNumberEvaluatorTest : public GrammarTest {
 protected:
  T Eval(const StringPiece value) {
    ParseNumberExpressionT parse_number_expression;
    parse_number_expression.base_type = flatbuffers_base_type<T>::value;
    parse_number_expression.value =
        CreatePrimitiveConstExpression<StringPiece>(value);
    OwnedFlatbuffer<SemanticExpression> expression =
        CreateExpression(std::move(parse_number_expression));

    ConstEvaluator const_eval(semantic_values_schema_.get());
    ParseNumberEvaluator parse_number_eval(&const_eval);

    StatusOr<const SemanticValue*> result =
        parse_number_eval.Apply(/*context=*/{}, expression.get(), &arena_);

    EXPECT_TRUE(result.ok());
    const SemanticValue* result_value = result.ValueOrDie();
    EXPECT_NE(result_value, nullptr);
    return result_value->Value<T>();
  }
};

using NumberTypes = ::testing::Types<int8, uint8, int16, uint16, int32, uint32,
                                     int64, uint64, double, float>;
TYPED_TEST_SUITE(ParseNumberEvaluatorTest, NumberTypes);

TYPED_TEST(ParseNumberEvaluatorTest, ParsesNumber) {
  EXPECT_EQ(this->Eval("42"), 42);
}

TEST_F(GrammarTest, FailsOnInvalidArgument) {
  ParseNumberExpressionT parse_number_expression;
  parse_number_expression.base_type = flatbuffers_base_type<int32>::value;
  parse_number_expression.value = CreatePrimitiveConstExpression<int32>(42);
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(parse_number_expression));

  ConstEvaluator const_eval(semantic_values_schema_.get());
  ParseNumberEvaluator parse_number_eval(&const_eval);

  StatusOr<const SemanticValue*> result =
      parse_number_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_FALSE(result.ok());
}

}  // namespace
}  // namespace libtextclassifier3::grammar
