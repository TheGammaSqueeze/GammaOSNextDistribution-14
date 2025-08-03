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

#include "utils/grammar/utils/rules.h"

#include "utils/grammar/rules_generated.h"
#include "utils/grammar/utils/ir.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::IsEmpty;
using ::testing::SizeIs;

TEST(SerializeRulesTest, HandlesSimpleRuleSet) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);

  rules.Add("<verb>", {"buy"});
  rules.Add("<verb>", {"bring"});
  rules.Add("<verb>", {"remind"});
  rules.Add("<reminder>", {"remind", "me", "to", "<verb>"});
  rules.Add("<action>", {"<reminder>"});

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_THAT(frozen_rules.lhs, IsEmpty());
  EXPECT_EQ(frozen_rules.terminals,
            std::string("bring\0buy\0me\0remind\0to\0", 23));
  EXPECT_THAT(frozen_rules.rules.front()->binary_rules, SizeIs(3));
  EXPECT_THAT(frozen_rules.rules.front()->unary_rules, SizeIs(1));
}

TEST(SerializeRulesTest, HandlesRulesSetWithCallbacks) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  const CallbackId output = 1;

  rules.Add("<verb>", {"buy"});
  rules.Add("<verb>", {"bring"});
  rules.Add("<verb>", {"remind"});
  rules.Add("<reminder>", {"remind", "me", "to", "<verb>"});
  rules.Add("<action>", {"<reminder>"}, output, 0);

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals,
            std::string("bring\0buy\0me\0remind\0to\0", 23));

  EXPECT_THAT(frozen_rules.lhs, SizeIs(1));

  EXPECT_THAT(frozen_rules.rules.front()->binary_rules, SizeIs(3));
  EXPECT_THAT(frozen_rules.rules.front()->unary_rules, SizeIs(1));
}

TEST(SerializeRulesTest, HandlesRulesWithWhitespaceGapLimits) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"lx"});
  rules.Add("<iata>", {"aa"});
  rules.Add("<flight>", {"<iata>", "<4_digits>"}, kNoCallback, 0,
            /*max_whitespace_gap=*/0);

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals, std::string("aa\0lx\0", 6));
  EXPECT_THAT(frozen_rules.lhs, SizeIs(1));
}

TEST(SerializeRulesTest, HandlesCaseSensitiveTerminals) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"LX"}, kNoCallback, 0, /*max_whitespace_gap=*/-1,
            /*case_sensitive=*/true);
  rules.Add("<iata>", {"AA"}, kNoCallback, 0, /*max_whitespace_gap=*/-1,
            /*case_sensitive=*/true);
  rules.Add("<iata>", {"dl"}, kNoCallback, 0, /*max_whitespace_gap=*/-1,
            /*case_sensitive=*/false);
  rules.Add("<flight>", {"<iata>", "<4_digits>"}, kNoCallback, 0,
            /*max_whitespace_gap=*/0);

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals, std::string("AA\0LX\0dl\0", 9));
  EXPECT_THAT(frozen_rules.lhs, SizeIs(1));
}

TEST(SerializeRulesTest, HandlesMultipleShards) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({"", "de"});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"LX"}, kNoCallback, 0, /*max_whitespace_gap=*/-1,
            /*case_sensitive=*/true, /*shard=*/0);
  rules.Add("<iata>", {"aa"}, kNoCallback, 0, /*max_whitespace_gap=*/-1,
            /*case_sensitive=*/false, /*shard=*/1);

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(2));
  EXPECT_EQ(frozen_rules.terminals, std::string("LX\0aa\0", 6));
}

TEST(SerializeRulesTest, HandlesRegexRules) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  // Rules rules;
  rules.AddRegex("<code>", "[A-Z]+");
  rules.AddRegex("<numbers>", "\\d+");
  RulesSetT frozen_rules;
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             &frozen_rules);
  EXPECT_THAT(frozen_rules.regex_annotator, SizeIs(2));
}

TEST(SerializeRulesTest, HandlesAlias) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"lx"});
  rules.Add("<iata>", {"aa"});
  rules.Add("<flight>", {"<iata>", "<4_digits>"});
  rules.AddAlias("<flight_number>", "<flight>");

  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals, std::string("aa\0lx\0", 6));
  EXPECT_THAT(frozen_rules.rules.front()->binary_rules, SizeIs(1));

  // Only alias, no rule.
  EXPECT_THAT(frozen_rules.rules.front()->unary_rules, IsEmpty());

  EXPECT_THAT(frozen_rules.lhs, IsEmpty());
}

TEST(SerializeRulesTest, ResolvesAnchorsAndFillers) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<code>",
            {"<^>", "<filler>", "this", "is", "a", "test", "<filler>", "<$>"});
  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals, std::string("a\0test\0this\0", 12));

  // Expect removal of anchors and fillers in this case.
  // The rule above is equivalent to: <code> ::= this is a test, binarized into
  // <tmp_0> ::= this is
  // <tmp_1> ::= <tmp_0> a
  // <code>  ::= <tmp_1> test
  EXPECT_THAT(frozen_rules.rules.front()->binary_rules, SizeIs(3));

  EXPECT_THAT(frozen_rules.rules.front()->unary_rules, IsEmpty());
  EXPECT_THAT(frozen_rules.lhs, IsEmpty());
}

TEST(SerializeRulesTest, HandlesFillers) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<test>", {"<filler>?", "a", "test"});
  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_EQ(frozen_rules.terminals, std::string("a\0test\0", 7));

  // Expect removal of anchors and fillers in this case.
  // The rule above is equivalent to: <code> ::= this is a test, binarized into
  // <tmp_0>  ::= <filler> a
  // <test>   ::= <tmp_0> test
  // <test>   ::= a test
  // <filler> ::= <token> <filler>
  EXPECT_THAT(frozen_rules.rules.front()->binary_rules, SizeIs(4));
  // <filler> ::= <token>
  EXPECT_THAT(frozen_rules.rules.front()->unary_rules, SizeIs(1));
}

TEST(SerializeRulesTest, HandlesAnnotations) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.AddAnnotation("phone");
  rules.AddAnnotation("url");
  rules.AddAnnotation("tracking_number");
  const Ir ir = rules.Finalize();
  RulesSetT frozen_rules;
  ir.Serialize(/*include_debug_information=*/false, &frozen_rules);

  EXPECT_THAT(frozen_rules.rules, SizeIs(1));
  EXPECT_THAT(frozen_rules.nonterminals->annotation_nt, SizeIs(3));
  EXPECT_EQ(frozen_rules.nonterminals->annotation_nt[0]->key, "phone");
  EXPECT_EQ(frozen_rules.nonterminals->annotation_nt[1]->key,
            "tracking_number");
  EXPECT_EQ(frozen_rules.nonterminals->annotation_nt[2]->key, "url");
}

}  // namespace
}  // namespace libtextclassifier3::grammar
