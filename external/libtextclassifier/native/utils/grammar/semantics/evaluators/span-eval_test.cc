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

#include "utils/grammar/semantics/evaluators/span-eval.h"

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/testing/value_generated.h"
#include "utils/grammar/types.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3::grammar {
namespace {

class SpanTextEvaluatorTest : public GrammarTest {};

TEST_F(SpanTextEvaluatorTest, CreatesSpanTextValues) {
  OwnedFlatbuffer<SemanticExpression> expression =
      CreateExpression(SpanAsStringExpressionT());
  SpanAsStringEvaluator span_eval;
  TextContext text = TextContextForText("This a test.");
  ParseTree derivation(/*lhs=*/kUnassignedNonterm, CodepointSpan{5, 11},
                       /*match_offset=*/0, /*type=*/ParseTree::Type::kDefault);

  StatusOr<const SemanticValue*> result = span_eval.Apply(
      /*context=*/{&text, &derivation}, expression.get(), &arena_);

  ASSERT_TRUE(result.ok());
  EXPECT_EQ(result.ValueOrDie()->Value<StringPiece>().ToString(), "a test");
}

}  // namespace
}  // namespace libtextclassifier3::grammar
