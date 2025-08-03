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

#include "utils/grammar/semantics/evaluators/constituent-eval.h"

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/grammar/semantics/eval-context.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/testing/value_generated.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3::grammar {
namespace {

class ConstituentEvaluatorTest : public GrammarTest {
 protected:
  explicit ConstituentEvaluatorTest() {}

  OwnedFlatbuffer<SemanticExpression> CreateConstituentExpression(
      const int id) {
    ConstituentExpressionT constituent_expression;
    constituent_expression.id = id;
    return CreateExpression(constituent_expression);
  }

  const ConstituentEvaluator constituent_eval_;
};

TEST_F(ConstituentEvaluatorTest, HandlesNotDefinedConstituents) {
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateConstituentExpression(/*id=*/42);

  StatusOr<const SemanticValue*> result = constituent_eval_.Apply(
      /*context=*/{}, expression.get(), /*arena=*/nullptr);

  EXPECT_TRUE(result.ok());
  EXPECT_EQ(result.ValueOrDie(), nullptr);
}

TEST_F(ConstituentEvaluatorTest, ForwardsConstituentSemanticValues) {
  // Create example values for constituents.
  EvalContext context;
  TestValueT value_0;
  value_0.test_string = "constituent 0 value";
  context.rule_constituents[0] = CreateSemanticValue(value_0);

  TestValueT value_42;
  value_42.test_string = "constituent 42 value";
  context.rule_constituents[42] = CreateSemanticValue(value_42);

  OwnedFlatbuffer<SemanticExpression> expression =
      CreateConstituentExpression(/*id=*/42);

  StatusOr<const SemanticValue*> result =
      constituent_eval_.Apply(context, expression.get(), /*arena=*/nullptr);

  EXPECT_TRUE(result.ok());
  const TestValue* result_value = result.ValueOrDie()->Table<TestValue>();
  EXPECT_EQ(result_value->test_string()->str(), "constituent 42 value");
}

}  // namespace
}  // namespace libtextclassifier3::grammar
