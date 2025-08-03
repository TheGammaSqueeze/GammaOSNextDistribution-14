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

#include "utils/grammar/semantics/evaluators/compose-eval.h"

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

class ComposeEvaluatorTest : public GrammarTest {
 protected:
  explicit ComposeEvaluatorTest()
      : const_eval_(semantic_values_schema_.get()) {}

  // Evaluator that just returns a constant value.
  ConstEvaluator const_eval_;
};

TEST_F(ComposeEvaluatorTest, SetsSingleField) {
  TestDateT date;
  date.day = 1;
  date.month = 2;
  date.year = 2020;
  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path = CreateUnpackedFieldPath({"date"});
  compose_expression.fields.back()->value = CreateConstDateExpression(date);
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->date()->day(), 1);
  EXPECT_EQ(result_test_value->date()->month(), 2);
  EXPECT_EQ(result_test_value->date()->year(), 2020);
}

TEST_F(ComposeEvaluatorTest, SetsStringField) {
  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path =
      CreateUnpackedFieldPath({"test_string"});
  compose_expression.fields.back()->value =
      CreatePrimitiveConstExpression<StringPiece>("this is a test");
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->test_string()->str(), "this is a test");
}

TEST_F(ComposeEvaluatorTest, SetsPrimitiveField) {
  ComposeExpressionT compose_expression;
  compose_expression.type = TypeIdForName(semantic_values_schema_.get(),
                                          "libtextclassifier3.grammar.TestDate")
                                .value();
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path = CreateUnpackedFieldPath({"day"});
  compose_expression.fields.back()->value =
      CreatePrimitiveConstExpression<int>(1);

  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestDate");
  const TestDate* result_date = result_value->Table<TestDate>();
  EXPECT_EQ(result_date->day(), 1);
}

TEST_F(ComposeEvaluatorTest, MergesMultipleField) {
  TestDateT day;
  day.day = 1;

  TestDateT month;
  month.month = 2;

  TestDateT year;
  year.year = 2020;

  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  for (const TestDateT& component : std::vector<TestDateT>{day, month, year}) {
    compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
    compose_expression.fields.back()->path = CreateUnpackedFieldPath({"date"});
    compose_expression.fields.back()->value =
        CreateConstDateExpression(component);
  }
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->date()->day(), 1);
  EXPECT_EQ(result_test_value->date()->month(), 2);
  EXPECT_EQ(result_test_value->date()->year(), 2020);
}

TEST_F(ComposeEvaluatorTest, SucceedsEvenWhenEmpty) {
  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path = CreateUnpackedFieldPath({"date"});
  compose_expression.fields.back()->value.reset(new SemanticExpressionT);
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  // Just return null value.
  struct : public SemanticExpressionEvaluator {
    StatusOr<const SemanticValue*> Apply(const EvalContext&,
                                         const SemanticExpression*,
                                         UnsafeArena*) const override {
      return nullptr;
    }
  } null_eval;

  ComposeEvaluator compose_eval(&null_eval, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
}

TEST_F(ComposeEvaluatorTest, AddsRepeatedPrimitiveField) {
  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path =
      CreateUnpackedFieldPath({"repeated_enum"});
  compose_expression.fields.back()->value =
      CreatePrimitiveConstExpression<int>(TestEnum_ENUM_1);
  compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
  compose_expression.fields.back()->path =
      CreateUnpackedFieldPath({"repeated_enum"});
  compose_expression.fields.back()->value =
      CreatePrimitiveConstExpression<int>(TestEnum_ENUM_2);
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->repeated_enum()->size(), 2);
  EXPECT_EQ(result_test_value->repeated_enum()->Get(0), TestEnum_ENUM_1);
  EXPECT_EQ(result_test_value->repeated_enum()->Get(1), TestEnum_ENUM_2);
}

TEST_F(ComposeEvaluatorTest, AddsRepeatedSubmessage) {
  ComposeExpressionT compose_expression;
  compose_expression.type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  {
    compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
    compose_expression.fields.back()->path =
        CreateUnpackedFieldPath({"repeated_date"});
    TestDateT date;
    date.day = 1;
    date.month = 2;
    date.year = 2020;
    compose_expression.fields.back()->value = CreateConstDateExpression(date);
  }

  {
    compose_expression.fields.emplace_back(new ComposeExpression_::FieldT);
    compose_expression.fields.back()->path =
        CreateUnpackedFieldPath({"repeated_date"});
    TestDateT date;
    date.day = 3;
    date.month = 4;
    date.year = 2021;
    compose_expression.fields.back()->value = CreateConstDateExpression(date);
  }

  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(std::move(compose_expression));

  ComposeEvaluator compose_eval(&const_eval_, semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      compose_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestValue");
  const TestValue* result_test_value = result_value->Table<TestValue>();
  EXPECT_EQ(result_test_value->repeated_date()->size(), 2);
  EXPECT_EQ(result_test_value->repeated_date()->Get(0)->day(), 1);
  EXPECT_EQ(result_test_value->repeated_date()->Get(0)->month(), 2);
  EXPECT_EQ(result_test_value->repeated_date()->Get(0)->year(), 2020);
  EXPECT_EQ(result_test_value->repeated_date()->Get(1)->day(), 3);
  EXPECT_EQ(result_test_value->repeated_date()->Get(1)->month(), 4);
  EXPECT_EQ(result_test_value->repeated_date()->Get(1)->year(), 2021);
}

}  // namespace
}  // namespace libtextclassifier3::grammar
