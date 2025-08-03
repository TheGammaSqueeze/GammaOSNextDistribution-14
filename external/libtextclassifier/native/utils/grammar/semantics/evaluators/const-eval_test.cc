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

#include "utils/grammar/semantics/evaluators/const-eval.h"

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/testing/value_generated.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3::grammar {
namespace {

class ConstEvaluatorTest : public GrammarTest {
 protected:
  explicit ConstEvaluatorTest() : const_eval_(semantic_values_schema_.get()) {}

  const ConstEvaluator const_eval_;
};

TEST_F(ConstEvaluatorTest, CreatesConstantSemanticValues) {
  TestValueT value;
  value.a_float_value = 64.42;
  value.test_string = "test string";
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateAndPackConstExpression(value);

  StatusOr<const SemanticValue*> result =
      const_eval_.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->test_string()->str(), "test string");
  EXPECT_FLOAT_EQ(result_test_value->a_float_value(), 64.42);
}

template <typename T>
class PrimitiveValueTest : public ConstEvaluatorTest {
 protected:
  T Eval(const T value) {
    OwnedFlatbuffer<SemanticExpression> expression =
        CreateAndPackPrimitiveConstExpression<T>(value);
    StatusOr<const SemanticValue*> result =
        const_eval_.Apply(/*context=*/{}, expression.get(), &arena_);
    EXPECT_TRUE(result.ok());
    const SemanticValue* result_value = result.ValueOrDie();
    EXPECT_NE(result_value, nullptr);
    return result_value->Value<T>();
  }
};

using PrimitiveTypes = ::testing::Types<int8, uint8, int16, uint16, int32,
                                        uint32, int64, uint64, double, float>;
TYPED_TEST_SUITE(PrimitiveValueTest, PrimitiveTypes);

TYPED_TEST(PrimitiveValueTest, CreatesConstantPrimitiveValues) {
  EXPECT_EQ(this->Eval(42), 42);
}

TEST_F(ConstEvaluatorTest, CreatesStringValues) {
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateAndPackPrimitiveConstExpression<StringPiece>("this is a test.");
  StatusOr<const SemanticValue*> result =
      const_eval_.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->Value<StringPiece>().ToString(), "this is a test.");
}

TEST_F(ConstEvaluatorTest, CreatesBoolValues) {
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateAndPackPrimitiveConstExpression<bool>(true);
  StatusOr<const SemanticValue*> result =
      const_eval_.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_TRUE(result_value->Value<bool>());
}

}  // namespace
}  // namespace libtextclassifier3::grammar
