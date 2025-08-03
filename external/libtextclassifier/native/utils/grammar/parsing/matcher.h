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

// A token based context-free grammar matcher.
//
// A parser passes token to the matcher: literal terminal strings and token
// types.
// The parser passes each token along with the [begin, end) position range
// in which it occurs.  So for an input string "Groundhog February 2, 2007", the
// parser would tell the matcher that:
//
//     "Groundhog" occurs at [0, 9)
//     "February" occurs at [9, 18)
//     <digits> occurs at [18, 20)
//     "," occurs at [20, 21)
//     <digits> occurs at [21, 26)
//
// Multiple overlapping symbols can be passed.
// The only constraint on symbol order is that they have to be passed in
// left-to-right order, strictly speaking, their "end" positions must be
// nondecreasing. This constraint allows a more efficient matching algorithm.
// The "begin" positions can be in any order.

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_MATCHER_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_MATCHER_H_

#include <array>
#include <functional>
#include <vector>

#include "annotator/types.h"
#include "utils/base/arena.h"
#include "utils/grammar/parsing/chart.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/parse-tree.h"
#include "utils/grammar/rules_generated.h"
#include "utils/strings/stringpiece.h"
#include "utils/utf8/unilib.h"

namespace libtextclassifier3::grammar {

class Matcher {
 public:
  explicit Matcher(const UniLib* unilib, const RulesSet* rules,
                   const std::vector<const RulesSet_::Rules*> rules_shards,
                   UnsafeArena* arena)
      : unilib_(*unilib),
        arena_(arena),
        last_end_(std::numeric_limits<int>().lowest()),
        rules_(rules),
        rules_shards_(rules_shards),
        pending_items_(nullptr),
        pending_exclusion_items_(nullptr) {
    TC3_CHECK_NE(rules, nullptr);
  }

  explicit Matcher(const UniLib* unilib, const RulesSet* rules,
                   UnsafeArena* arena)
      : Matcher(unilib, rules, {}, arena) {
    rules_shards_.reserve(rules->rules()->size());
    rules_shards_.insert(rules_shards_.end(), rules->rules()->begin(),
                         rules->rules()->end());
  }

  // Finish the matching.
  void Finish();

  // Tells the matcher that the given terminal was found occupying position
  // range [begin, end) in the input.
  // The matcher may invoke callback functions before returning, if this
  // terminal triggers any new matches for rules in the grammar.
  // Calls to AddTerminal() and AddParseTree() must be in left-to-right order,
  // that is, the sequence of `end` values must be non-decreasing.
  void AddTerminal(const CodepointSpan codepoint_span, const int match_offset,
                   StringPiece terminal);
  void AddTerminal(const CodepointIndex begin, const CodepointIndex end,
                   StringPiece terminal) {
    AddTerminal(CodepointSpan{begin, end}, begin, terminal);
  }

  // Adds predefined parse tree.
  void AddParseTree(ParseTree* parse_tree);

  const Chart<> chart() const { return chart_; }

 private:
  // Process matches from lhs set.
  void ExecuteLhsSet(const CodepointSpan codepoint_span, const int match_offset,
                     const int whitespace_gap,
                     const std::function<void(ParseTree*)>& initializer_fn,
                     const RulesSet_::LhsSet* lhs_set);

  // Queues a newly created match item.
  void QueueForProcessing(ParseTree* item);

  // Queues a match item for later post checking of the exclusion condition.
  // For exclusions we need to check that the `item->excluded_nonterminal`
  // doesn't match the same span. As we cannot know which matches have already
  // been added, we queue the item for later post checking - once all matches
  // up to `item->codepoint_span.second` have been added.
  void QueueForPostCheck(ExclusionNode* item);

  // Adds pending items to the chart, possibly generating new matches as a
  // result.
  void ProcessPendingSet();

  // Checks all pending exclusion matches that their exclusion condition is
  // fulfilled.
  void ProcessPendingExclusionMatches();

  UniLib unilib_;

  // Memory arena for match allocation.
  UnsafeArena* arena_;

  // The end position of the most recent match or terminal, for sanity
  // checking.
  int last_end_;

  // Rules.
  const RulesSet* rules_;
  // The active rule shards.
  std::vector<const RulesSet_::Rules*> rules_shards_;

  // The set of items pending to be added to the chart as a singly-linked list.
  ParseTree* pending_items_;

  // The set of items pending to be post-checked as a singly-linked list.
  ExclusionNode* pending_exclusion_items_;

  // The chart data structure: a hashtable containing all matches, indexed by
  // their end positions.
  Chart<> chart_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_MATCHER_H_
