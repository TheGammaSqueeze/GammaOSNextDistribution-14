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

#include "utils/grammar/analyzer.h"

#include "utils/grammar/testing/utils.h"
#include "utils/grammar/types.h"
#include "utils/grammar/utils/rules.h"
#include "utils/utf8/unicodetext.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::SizeIs;

class AnalyzerTest : public GrammarTest {};

TEST_F(AnalyzerTest, ParsesTextWithGrammar) {
  RulesSetT model;

  // Add semantic values schema.
  model.semantic_values_schema.assign(semantic_values_schema_.buffer().begin(),
                                      semantic_values_schema_.buffer().end());

  // Define rules and semantics.
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<month>", {"january"},
            static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
            /*callback_param=*/model.semantic_expression.size());
  model.semantic_expression.push_back(CreatePrimitiveConstExpression(1));

  rules.Add("<month>", {"february"},
            static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
            /*callback_param=*/model.semantic_expression.size());
  model.semantic_expression.push_back(CreatePrimitiveConstExpression(2));

  const int kMonth = 0;
  rules.Add("<month_rule>", {"<month>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kMonth);
  rules.Finalize().Serialize(/*include_debug_information=*/false, &model);
  const std::string model_buffer = PackFlatbuffer<RulesSet>(&model);

  Analyzer analyzer(unilib_.get(),
                    flatbuffers::GetRoot<RulesSet>(model_buffer.data()));

  {
    auto maybe_results = analyzer.Parse(
        UTF8ToUnicodeText("The month is January 2020", /*do_copy=*/false),
        /*locales=*/{}, &arena_);
    EXPECT_TRUE(maybe_results.ok());

    const std::vector<EvaluatedDerivation> results = maybe_results.ValueOrDie();
    EXPECT_THAT(results, SizeIs(1));

    // Check parse tree.
    EXPECT_THAT(results[0], IsDerivation(kMonth /* rule_id */, 13 /* begin */,
                                         20 /* end */));

    // Check semantic result.
    EXPECT_EQ(results[0].value->Value<int32>(), 1);
  }

  {
    auto maybe_results =
        analyzer.Parse(UTF8ToUnicodeText("february", /*do_copy=*/false),
                       /*locales=*/{}, &arena_);
    EXPECT_TRUE(maybe_results.ok());

    const std::vector<EvaluatedDerivation> results = maybe_results.ValueOrDie();
    EXPECT_THAT(results, SizeIs(1));

    // Check parse tree.
    EXPECT_THAT(results[0],
                IsDerivation(kMonth /* rule_id */, 0 /* begin */, 8 /* end */));

    // Check semantic result.
    EXPECT_EQ(results[0].value->Value<int32>(), 2);
  }
}

}  // namespace
}  // namespace libtextclassifier3::grammar
