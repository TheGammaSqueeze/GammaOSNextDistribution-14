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

#include "utils/grammar/parsing/parser.h"

#include <string>
#include <vector>

#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/rules_generated.h"
#include "utils/grammar/testing/utils.h"
#include "utils/grammar/types.h"
#include "utils/grammar/utils/ir.h"
#include "utils/grammar/utils/rules.h"
#include "utils/i18n/locale.h"
#include "utils/tokenizer.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;

class ParserTest : public GrammarTest {};

TEST_F(ParserTest, ParsesSimpleRules) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<day>", {"<2_digits>"});
  rules.Add("<month>", {"<2_digits>"});
  rules.Add("<year>", {"<4_digits>"});
  constexpr int kDate = 0;
  rules.Add("<date>", {"<year>", "/", "<month>", "/", "<day>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kDate);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Event: 2020/05/08"), &arena_)),
              ElementsAre(IsDerivation(kDate, 7, 17)));
}

TEST_F(ParserTest, HandlesEmptyInput) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  constexpr int kTest = 0;
  rules.Add("<test>", {"test"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kTest);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(
                  parser.Parse(TextContextForText("Event: test"), &arena_)),
              ElementsAre(IsDerivation(kTest, 7, 11)));

  // Check that we bail out in case of empty input.
  EXPECT_THAT(ValidDeduplicatedDerivations(
                  parser.Parse(TextContextForText(""), &arena_)),
              IsEmpty());
  EXPECT_THAT(ValidDeduplicatedDerivations(
                  parser.Parse(TextContextForText("    "), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesUppercaseTokens) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  constexpr int kScriptedReply = 0;
  rules.Add("<test>", {"please?", "reply", "<uppercase_token>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule),
            kScriptedReply);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Reply STOP to cancel."), &arena_)),
              ElementsAre(IsDerivation(kScriptedReply, 0, 10)));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Reply stop to cancel."), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesAnchors) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  constexpr int kScriptedReply = 0;
  rules.Add("<test>", {"<^>", "reply", "<uppercase_token>", "<$>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule),
            kScriptedReply);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(
                  parser.Parse(TextContextForText("Reply STOP"), &arena_)),
              ElementsAre(IsDerivation(kScriptedReply, 0, 10)));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Please reply STOP to cancel."), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesWordBreaks) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  constexpr int kFlight = 0;
  rules.Add("<flight>", {"<carrier>", "<digits>", "<\b>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kFlight);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  // Make sure the grammar recognizes "LX 38".
  EXPECT_THAT(
      ValidDeduplicatedDerivations(parser.Parse(
          TextContextForText("My flight is: LX 38. Arriving later"), &arena_)),
      ElementsAre(IsDerivation(kFlight, 14, 19)));

  // Make sure the grammar doesn't trigger on "LX 38.00".
  EXPECT_THAT(ValidDeduplicatedDerivations(
                  parser.Parse(TextContextForText("LX 38.00"), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesAnnotations) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  constexpr int kCallPhone = 0;
  rules.Add("<flight>", {"dial", "<phone>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kCallPhone);
  rules.BindAnnotation("<phone>", "phone");
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  TextContext context = TextContextForText("Please dial 911");

  // Sanity check that we don't trigger if we don't feed the correct
  // annotations.
  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(context, &arena_)),
              IsEmpty());

  // Create a phone annotion.
  AnnotatedSpan phone_span;
  phone_span.span = CodepointSpan{12, 15};
  phone_span.classification.emplace_back("phone", 1.0);
  context.annotations.push_back(phone_span);
  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(context, &arena_)),
              ElementsAre(IsDerivation(kCallPhone, 7, 15)));
}

TEST_F(ParserTest, HandlesRegexAnnotators) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.AddRegex("<code>",
                 "(\"([A-Za-z]+)\"|\\b\"?(?:[A-Z]+[0-9]*|[0-9])\"?\\b)");
  constexpr int kScriptedReply = 0;
  rules.Add("<test>", {"please?", "reply", "<code>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule),
            kScriptedReply);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Reply STOP to cancel."), &arena_)),
              ElementsAre(IsDerivation(kScriptedReply, 0, 10)));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("Reply Stop to cancel."), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesExclusions) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<excluded>", {"be", "safe"});
  rules.AddWithExclusion("<tokens_but_not_excluded>", {"<token>", "<token>"},
                         /*excluded_nonterminal=*/"<excluded>");
  constexpr int kSetReminder = 0;
  rules.Add("<set_reminder>",
            {"do", "not", "forget", "to", "<tokens_but_not_excluded>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kSetReminder);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("do not forget to be there"), &arena_)),
              ElementsAre(IsDerivation(kSetReminder, 0, 25)));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("do not forget to be safe"), &arena_)),
              IsEmpty());
}

TEST_F(ParserTest, HandlesFillers) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  constexpr int kSetReminder = 0;
  rules.Add("<set_reminder>", {"do", "not", "forget", "to", "<filler>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kSetReminder);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("do not forget to be there"), &arena_)),
              ElementsAre(IsDerivation(kSetReminder, 0, 25)));
}

TEST_F(ParserTest, HandlesAssertions) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  // Flight: carrier + flight code and check right context.
  constexpr int kFlight = 0;
  rules.Add("<track_flight>",
            {"<carrier>", "<flight_code>", "<context_assertion>?"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kFlight);
  // Exclude matches like: LX 38.00 etc.
  rules.AddAssertion("<context_assertion>", {".?", "<digits>"},
                     /*negative=*/true);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(
      ValidDeduplicatedDerivations(
          parser.Parse(TextContextForText("LX38 aa 44 LX 38.38"), &arena_)),
      ElementsAre(IsDerivation(kFlight, 0, 4), IsDerivation(kFlight, 5, 10)));
}

TEST_F(ParserTest, HandlesWhitespaceGapLimit) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  // Flight: carrier + flight code and check right context.
  constexpr int kFlight = 0;
  rules.Add("<track_flight>", {"<carrier>", "<flight_code>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kFlight,
            /*max_whitespace_gap=*/0);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(ValidDeduplicatedDerivations(parser.Parse(
                  TextContextForText("LX38 aa 44 LX 38"), &arena_)),
              ElementsAre(IsDerivation(kFlight, 0, 4)));
}

TEST_F(ParserTest, HandlesCaseSensitiveMatching) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"Lx"}, /*callback=*/kNoCallback, /*callback_param=*/0,
            /*max_whitespace_gap=*/-1, /*case_sensitive=*/true);
  rules.Add("<carrier>", {"AA"}, /*callback=*/kNoCallback, /*callback_param=*/0,
            /*max_whitespace_gap=*/-1, /*case_sensitive=*/true);
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  // Flight: carrier + flight code and check right context.
  constexpr int kFlight = 0;
  rules.Add("<track_flight>", {"<carrier>", "<flight_code>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), kFlight);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer();
  Parser parser(unilib_.get(),
                flatbuffers::GetRoot<RulesSet>(rules_buffer.data()));

  EXPECT_THAT(
      ValidDeduplicatedDerivations(
          parser.Parse(TextContextForText("Lx38 AA 44 LX 38"), &arena_)),
      ElementsAre(IsDerivation(kFlight, 0, 4), IsDerivation(kFlight, 5, 10)));
}

}  // namespace
}  // namespace libtextclassifier3::grammar
