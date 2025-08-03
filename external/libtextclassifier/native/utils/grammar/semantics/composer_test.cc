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

#include "utils/grammar/semantics/composer.h"

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/reflection.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/parser.h"
#include "utils/grammar/rules_generated.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/testing/value_generated.h"
#include "utils/grammar/types.h"
#include "utils/grammar/utils/rules.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::ElementsAre;

class SemanticComposerTest : public GrammarTest {};

TEST_F(SemanticComposerTest, EvaluatesSimpleMapping) {
  RulesSetT model;
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  const int test_value_type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  {
    rules.Add("<month>", {"january"},
              static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
              /*callback_param=*/model.semantic_expression.size());
    TestValueT value;
    value.value = 1;
    const std::string serialized_value = PackFlatbuffer<TestValue>(&value);
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.type = test_value_type;
    const_value.value.assign(serialized_value.begin(), serialized_value.end());
    model.semantic_expression.emplace_back(new SemanticExpressionT);
    model.semantic_expression.back()->expression.Set(const_value);
  }
  {
    rules.Add("<month>", {"february"},
              static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
              /*callback_param=*/model.semantic_expression.size());
    TestValueT value;
    value.value = 2;
    const std::string serialized_value = PackFlatbuffer<TestValue>(&value);
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.type = test_value_type;
    const_value.value.assign(serialized_value.begin(), serialized_value.end());
    model.semantic_expression.emplace_back(new SemanticExpressionT);
    model.semantic_expression.back()->expression.Set(const_value);
  }
  const int kMonth = 0;
  rules.Add("<month_rule>", {"<month>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kMonth);
  rules.Finalize().Serialize(/*include_debug_information=*/false, &model);
  const std::string model_buffer = PackFlatbuffer<RulesSet>(&model);
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(model_buffer.data()));
  SemanticComposer composer(semantic_values_schema_.get());

  {
    const TextContext text = TextContextForText("Month: January");
    const std::vector<Derivation> derivations = parser.Parse(text, &arena_);
    EXPECT_THAT(derivations, ElementsAre(IsDerivation(kMonth, 7, 14)));

    StatusOr<const SemanticValue*> maybe_value =
        composer.Eval(text, derivations.front(), &arena_);
    EXPECT_TRUE(maybe_value.ok());

    const TestValue* value = maybe_value.ValueOrDie()->Table<TestValue>();
    EXPECT_EQ(value->value(), 1);
  }

  {
    const TextContext text = TextContextForText("Month: February");
    const std::vector<Derivation> derivations = parser.Parse(text, &arena_);
    EXPECT_THAT(derivations, ElementsAre(IsDerivation(kMonth, 7, 15)));

    StatusOr<const SemanticValue*> maybe_value =
        composer.Eval(text, derivations.front(), &arena_);
    EXPECT_TRUE(maybe_value.ok());

    const TestValue* value = maybe_value.ValueOrDie()->Table<TestValue>();
    EXPECT_EQ(value->value(), 2);
  }
}

TEST_F(SemanticComposerTest, RecursivelyEvaluatesConstituents) {
  RulesSetT model;
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  const int test_value_type =
      TypeIdForName(semantic_values_schema_.get(),
                    "libtextclassifier3.grammar.TestValue")
          .value();
  constexpr int kDateRule = 0;
  {
    rules.Add("<month>", {"january"},
              static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
              /*callback_param=*/model.semantic_expression.size());
    TestValueT value;
    value.value = 42;
    const std::string serialized_value = PackFlatbuffer<TestValue>(&value);
    ConstValueExpressionT const_value;
    const_value.type = test_value_type;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.value.assign(serialized_value.begin(), serialized_value.end());
    model.semantic_expression.emplace_back(new SemanticExpressionT);
    model.semantic_expression.back()->expression.Set(const_value);
  }
  {
    // Define constituents of the rule.
    // TODO(smillius): Add support in the rules builder to directly specify
    // constituent ids in the rule, e.g. `<date> ::= <month>@0? <4_digits>`.
    rules.Add("<date_@0>", {"<month>"},
              static_cast<CallbackId>(DefaultCallback::kMapping),
              /*callback_param=*/1);
    rules.Add("<date>", {"<date_@0>?", "<4_digits>"},
              static_cast<CallbackId>(DefaultCallback::kSemanticExpression),
              /*callback_param=*/model.semantic_expression.size());
    ConstituentExpressionT constituent;
    constituent.id = 1;
    model.semantic_expression.emplace_back(new SemanticExpressionT);
    model.semantic_expression.back()->expression.Set(constituent);
    rules.Add("<date_rule>", {"<date>"},
              static_cast<CallbackId>(DefaultCallback::kRootRule),
              /*callback_param=*/kDateRule);
  }

  rules.Finalize().Serialize(/*include_debug_information=*/false, &model);
  const std::string model_buffer = PackFlatbuffer<RulesSet>(&model);
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(model_buffer.data()));
  SemanticComposer composer(semantic_values_schema_.get());

  {
    const TextContext text = TextContextForText("Event: January 2020");
    const std::vector<Derivation> derivations =
        ValidDeduplicatedDerivations(parser.Parse(text, &arena_));
    EXPECT_THAT(derivations, ElementsAre(IsDerivation(kDateRule, 7, 19)));

    StatusOr<const SemanticValue*> maybe_value =
        composer.Eval(text, derivations.front(), &arena_);
    EXPECT_TRUE(maybe_value.ok());

    const TestValue* value = maybe_value.ValueOrDie()->Table<TestValue>();
    EXPECT_EQ(value->value(), 42);
  }
}

}  // namespace
}  // namespace libtextclassifier3::grammar
