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

#include "utils/grammar/parsing/matcher.h"

#include <string>
#include <vector>

#include "utils/base/arena.h"
#include "utils/grammar/rules_generated.h"
#include "utils/grammar/types.h"
#include "utils/grammar/utils/rules.h"
#include "utils/strings/append.h"
#include "utils/utf8/unilib.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::DescribeMatcher;
using ::testing::ElementsAre;
using ::testing::ExplainMatchResult;
using ::testing::IsEmpty;

struct TestMatchResult {
  CodepointSpan codepoint_span;
  std::string terminal;
  std::string nonterminal;
  int rule_id;

  friend std::ostream& operator<<(std::ostream& os,
                                  const TestMatchResult& match) {
    return os << "Result(rule_id=" << match.rule_id
              << ", begin=" << match.codepoint_span.first
              << ", end=" << match.codepoint_span.second
              << ", terminal=" << match.terminal
              << ", nonterminal=" << match.nonterminal << ")";
  }
};

MATCHER_P3(IsTerminal, begin, end, terminal,
           "is terminal with begin that " +
               DescribeMatcher<int>(begin, negation) + ", end that " +
               DescribeMatcher<int>(end, negation) + ", value that " +
               DescribeMatcher<std::string>(terminal, negation)) {
  return ExplainMatchResult(CodepointSpan(begin, end), arg.codepoint_span,
                            result_listener) &&
         ExplainMatchResult(terminal, arg.terminal, result_listener);
}

MATCHER_P3(IsNonterminal, begin, end, name,
           "is nonterminal with begin that " +
               DescribeMatcher<int>(begin, negation) + ", end that " +
               DescribeMatcher<int>(end, negation) + ", name that " +
               DescribeMatcher<std::string>(name, negation)) {
  return ExplainMatchResult(CodepointSpan(begin, end), arg.codepoint_span,
                            result_listener) &&
         ExplainMatchResult(name, arg.nonterminal, result_listener);
}

MATCHER_P4(IsDerivation, begin, end, name, rule_id,
           "is derivation of rule that " +
               DescribeMatcher<int>(rule_id, negation) + ", begin that " +
               DescribeMatcher<int>(begin, negation) + ", end that " +
               DescribeMatcher<int>(end, negation) + ", name that " +
               DescribeMatcher<std::string>(name, negation)) {
  return ExplainMatchResult(IsNonterminal(begin, end, name), arg,
                            result_listener) &&
         ExplainMatchResult(rule_id, arg.rule_id, result_listener);
}

// Superclass of all tests.
class MatcherTest : public testing::Test {
 protected:
  MatcherTest()
      : INIT_UNILIB_FOR_TESTING(unilib_), arena_(/*block_size=*/16 << 10) {}

  std::string GetNonterminalName(
      const RulesSet_::DebugInformation* debug_information,
      const Nonterm nonterminal) const {
    if (const RulesSet_::DebugInformation_::NonterminalNamesEntry* entry =
            debug_information->nonterminal_names()->LookupByKey(nonterminal)) {
      return entry->value()->str();
    }
    // Unnamed Nonterm.
    return "()";
  }

  std::vector<TestMatchResult> GetMatchResults(
      const Chart<>& chart,
      const RulesSet_::DebugInformation* debug_information) {
    std::vector<TestMatchResult> result;
    for (const Derivation& derivation : chart.derivations()) {
      result.emplace_back();
      result.back().rule_id = derivation.rule_id;
      result.back().codepoint_span = derivation.parse_tree->codepoint_span;
      result.back().nonterminal =
          GetNonterminalName(debug_information, derivation.parse_tree->lhs);
      if (derivation.parse_tree->IsTerminalRule()) {
        result.back().terminal = derivation.parse_tree->terminal;
      }
    }
    return result;
  }

  UniLib unilib_;
  UnsafeArena arena_;
};

TEST_F(MatcherTest, HandlesBasicOperations) {
  // Create an example grammar.
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<test>", {"the", "quick", "brown", "fox"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));
  rules.Add("<action>", {"<test>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());
  Matcher matcher(&unilib_, rules_set, &arena_);

  matcher.AddTerminal(0, 1, "the");
  matcher.AddTerminal(1, 2, "quick");
  matcher.AddTerminal(2, 3, "brown");
  matcher.AddTerminal(3, 4, "fox");

  EXPECT_THAT(GetMatchResults(matcher.chart(), rules_set->debug_information()),
              ElementsAre(IsNonterminal(0, 4, "<test>"),
                          IsNonterminal(0, 4, "<action>")));
}

std::string CreateTestGrammar() {
  // Create an example grammar.
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);

  // Callbacks on terminal rules.
  rules.Add("<output_5>", {"quick"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 6);
  rules.Add("<output_0>", {"the"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 1);

  // Callbacks on non-terminal rules.
  rules.Add("<output_1>", {"the", "quick", "brown", "fox"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 2);
  rules.Add("<output_2>", {"the", "quick"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 3);
  rules.Add("<output_3>", {"brown", "fox"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 4);

  // Now a complex thing: "the* brown fox".
  rules.Add("<thestarbrownfox>", {"brown", "fox"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 5);
  rules.Add("<thestarbrownfox>", {"the", "<thestarbrownfox>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule), 5);

  return rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);
}

Nonterm FindNontermForName(const RulesSet* rules,
                           const std::string& nonterminal_name) {
  for (const RulesSet_::DebugInformation_::NonterminalNamesEntry* entry :
       *rules->debug_information()->nonterminal_names()) {
    if (entry->value()->str() == nonterminal_name) {
      return entry->key();
    }
  }
  return kUnassignedNonterm;
}

TEST_F(MatcherTest, HandlesDerivationsOfRules) {
  const std::string rules_buffer = CreateTestGrammar();
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());
  Matcher matcher(&unilib_, rules_set, &arena_);

  matcher.AddTerminal(0, 1, "the");
  matcher.AddTerminal(1, 2, "quick");
  matcher.AddTerminal(2, 3, "brown");
  matcher.AddTerminal(3, 4, "fox");
  matcher.AddTerminal(3, 5, "fox");
  matcher.AddTerminal(4, 6, "fox");  // Not adjacent to "brown".

  EXPECT_THAT(GetMatchResults(matcher.chart(), rules_set->debug_information()),
              ElementsAre(
                  // the
                  IsDerivation(0, 1, "<output_0>", 1),

                  // quick
                  IsDerivation(1, 2, "<output_5>", 6),
                  IsDerivation(0, 2, "<output_2>", 3),

                  // brown

                  // fox
                  IsDerivation(0, 4, "<output_1>", 2),
                  IsDerivation(2, 4, "<output_3>", 4),
                  IsDerivation(2, 4, "<thestarbrownfox>", 5),

                  // fox
                  IsDerivation(0, 5, "<output_1>", 2),
                  IsDerivation(2, 5, "<output_3>", 4),
                  IsDerivation(2, 5, "<thestarbrownfox>", 5)));
}

TEST_F(MatcherTest, HandlesRecursiveRules) {
  const std::string rules_buffer = CreateTestGrammar();
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());
  Matcher matcher(&unilib_, rules_set, &arena_);

  matcher.AddTerminal(0, 1, "the");
  matcher.AddTerminal(1, 2, "the");
  matcher.AddTerminal(2, 4, "the");
  matcher.AddTerminal(3, 4, "the");
  matcher.AddTerminal(4, 5, "brown");
  matcher.AddTerminal(5, 6, "fox");  // Generates 5 of <thestarbrownfox>

  EXPECT_THAT(GetMatchResults(matcher.chart(), rules_set->debug_information()),
              ElementsAre(IsTerminal(0, 1, "the"), IsTerminal(1, 2, "the"),
                          IsTerminal(2, 4, "the"), IsTerminal(3, 4, "the"),
                          IsNonterminal(4, 6, "<output_3>"),
                          IsNonterminal(4, 6, "<thestarbrownfox>"),
                          IsNonterminal(3, 6, "<thestarbrownfox>"),
                          IsNonterminal(2, 6, "<thestarbrownfox>"),
                          IsNonterminal(1, 6, "<thestarbrownfox>"),
                          IsNonterminal(0, 6, "<thestarbrownfox>")));
}

TEST_F(MatcherTest, HandlesManualAddParseTreeCalls) {
  const std::string rules_buffer = CreateTestGrammar();
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());
  Matcher matcher(&unilib_, rules_set, &arena_);

  // Test having the lexer call AddParseTree() instead of AddTerminal()
  matcher.AddTerminal(-4, 37, "the");
  matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
      FindNontermForName(rules_set, "<thestarbrownfox>"), CodepointSpan{37, 42},
      /*match_offset=*/37, ParseTree::Type::kDefault));

  EXPECT_THAT(GetMatchResults(matcher.chart(), rules_set->debug_information()),
              ElementsAre(IsTerminal(-4, 37, "the"),
                          IsNonterminal(-4, 42, "<thestarbrownfox>")));
}

TEST_F(MatcherTest, HandlesOptionalRuleElements) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<output_0>", {"a?", "b?", "c?", "d?", "e"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));
  rules.Add("<output_1>", {"a", "b?", "c", "d?", "e"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));
  rules.Add("<output_2>", {"a", "b?", "c", "d", "e?"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));

  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);

  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());
  Matcher matcher(&unilib_, rules_set, &arena_);

  // Run the matcher on "a b c d e".
  matcher.AddTerminal(0, 1, "a");
  matcher.AddTerminal(1, 2, "b");
  matcher.AddTerminal(2, 3, "c");
  matcher.AddTerminal(3, 4, "d");
  matcher.AddTerminal(4, 5, "e");

  EXPECT_THAT(
      GetMatchResults(matcher.chart(), rules_set->debug_information()),
      ElementsAre(
          IsNonterminal(0, 4, "<output_2>"), IsTerminal(4, 5, "e"),
          IsNonterminal(0, 5, "<output_0>"), IsNonterminal(0, 5, "<output_1>"),
          IsNonterminal(0, 5, "<output_2>"), IsNonterminal(1, 5, "<output_0>"),
          IsNonterminal(2, 5, "<output_0>"),
          IsNonterminal(3, 5, "<output_0>")));
}

TEST_F(MatcherTest, HandlesWhitespaceGapLimits) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"lx"});
  rules.Add("<iata>", {"aa"});
  // Require no whitespace between code and flight number.
  rules.Add("<flight_number>", {"<iata>", "<4_digits>"},
            /*callback=*/static_cast<CallbackId>(DefaultCallback::kRootRule), 0,
            /*max_whitespace_gap=*/0);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());

  // Check that the grammar triggers on LX1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(0, 2, "LX");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{2, 6}, /*match_offset=*/2, ParseTree::Type::kDefault));
    EXPECT_THAT(
        GetMatchResults(matcher.chart(), rules_set->debug_information()),
        ElementsAre(IsNonterminal(0, 6, "<flight_number>")));
  }

  // Check that the grammar doesn't trigger on LX 1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(6, 8, "LX");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{9, 13}, /*match_offset=*/8, ParseTree::Type::kDefault));
    EXPECT_THAT(
        GetMatchResults(matcher.chart(), rules_set->debug_information()),
        IsEmpty());
  }
}

TEST_F(MatcherTest, HandlesCaseSensitiveTerminals) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);
  rules.Add("<iata>", {"LX"}, /*callback=*/kNoCallback, 0,
            /*max_whitespace_gap*/ -1, /*case_sensitive=*/true);
  rules.Add("<iata>", {"AA"}, /*callback=*/kNoCallback, 0,
            /*max_whitespace_gap*/ -1, /*case_sensitive=*/true);
  rules.Add("<iata>", {"dl"}, /*callback=*/kNoCallback, 0,
            /*max_whitespace_gap*/ -1, /*case_sensitive=*/false);
  // Require no whitespace between code and flight number.
  rules.Add("<flight_number>", {"<iata>", "<4_digits>"},
            /*callback=*/static_cast<CallbackId>(DefaultCallback::kRootRule), 0,
            /*max_whitespace_gap=*/0);
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());

  // Check that the grammar triggers on LX1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(0, 2, "LX");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{2, 6}, /*match_offset=*/2, ParseTree::Type::kDefault));
    EXPECT_THAT(
        GetMatchResults(matcher.chart(), rules_set->debug_information()),
        ElementsAre(IsNonterminal(0, 6, "<flight_number>")));
  }

  // Check that the grammar doesn't trigger on lx1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(6, 8, "lx");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{8, 12}, /*match_offset=*/8, ParseTree::Type::kDefault));
    EXPECT_THAT(matcher.chart().derivations(), IsEmpty());
  }

  // Check that the grammar does trigger on dl1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(12, 14, "dl");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{14, 18}, /*match_offset=*/14, ParseTree::Type::kDefault));
    EXPECT_THAT(
        GetMatchResults(matcher.chart(), rules_set->debug_information()),
        ElementsAre(IsNonterminal(12, 18, "<flight_number>")));
  }
}

TEST_F(MatcherTest, HandlesExclusions) {
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  Rules rules(locale_shard_map);

  rules.Add("<all_zeros>", {"0000"});
  rules.AddWithExclusion("<flight_code>", {"<4_digits>"},
                         /*excluded_nonterminal=*/"<all_zeros>");
  rules.Add("<iata>", {"lx"});
  rules.Add("<iata>", {"aa"});
  rules.Add("<iata>", {"dl"});
  // Require no whitespace between code and flight number.
  rules.Add("<flight_number>", {"<iata>", "<flight_code>"},
            static_cast<CallbackId>(DefaultCallback::kRootRule));
  const std::string rules_buffer = rules.Finalize().SerializeAsFlatbuffer(
      /*include_debug_information=*/true);
  const RulesSet* rules_set =
      flatbuffers::GetRoot<RulesSet>(rules_buffer.data());

  // Check that the grammar triggers on LX1138.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(0, 2, "LX");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{2, 6}, /*match_offset=*/2, ParseTree::Type::kDefault));
    matcher.Finish();
    EXPECT_THAT(
        GetMatchResults(matcher.chart(), rules_set->debug_information()),
        ElementsAre(IsNonterminal(0, 6, "<flight_number>")));
  }

  // Check that the grammar doesn't trigger on LX0000.
  {
    Matcher matcher(&unilib_, rules_set, &arena_);
    matcher.AddTerminal(6, 8, "LX");
    matcher.AddTerminal(8, 12, "0000");
    matcher.AddParseTree(arena_.AllocAndInit<ParseTree>(
        rules_set->nonterminals()->n_digits_nt()->Get(4 - 1),
        CodepointSpan{8, 12}, /*match_offset=*/8, ParseTree::Type::kDefault));
    matcher.Finish();
    EXPECT_THAT(matcher.chart().derivations(), IsEmpty());
  }
}

}  // namespace
}  // namespace libtextclassifier3::grammar
