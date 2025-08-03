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

class MergeValuesEvaluatorTest : public GrammarTest {
 protected:
  explicit MergeValuesEvaluatorTest()
      : const_eval_(semantic_values_schema_.get()) {}

  // Evaluator that just returns a constant value.
  ConstEvaluator const_eval_;
};

TEST_F(MergeValuesEvaluatorTest, MergeSemanticValues) {
  // Setup the data
  TestDateT date_value_day;
  date_value_day.day = 23;
  TestDateT date_value_month;
  date_value_month.month = 9;
  TestDateT date_value_year;
  date_value_year.year = 2019;

  OwnedFlatbuffer<SemanticExpression> expression =
      CreateAndPackMergeValuesExpression(
          {date_value_day, date_value_month, date_value_year});

  MergeValuesEvaluator merge_values_eval(&const_eval_,
                                         semantic_values_schema_.get());

  StatusOr<const SemanticValue*> result =
      merge_values_eval.Apply(/*context=*/{}, expression.get(), &arena_);

  EXPECT_TRUE(result.ok());
  const SemanticValue* result_value = result.ValueOrDie();
  ASSERT_NE(result_value, nullptr);
  EXPECT_EQ(result_value->type()->name()->str(),
            "libtextclassifier3.grammar.TestDate");
  const TestDate* result_test_date = result_value->Table<TestDate>();
  EXPECT_EQ(result_test_date->day(), 23);
  EXPECT_EQ(result_test_date->month(), 9);
  EXPECT_EQ(result_test_date->year(), 2019);
}

}  // namespace
}  // namespace libtextclassifier3::grammar
