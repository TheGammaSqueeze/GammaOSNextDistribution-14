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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_CHART_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_CHART_H_

#include <array>

#include "annotator/types.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/parse-tree.h"

namespace libtextclassifier3::grammar {

// Chart is a hashtable container for use with a CYK style parser.
// The hashtable contains all matches, indexed by their end positions.
template <int NumBuckets = 1 << 8>
class Chart {
 public:
  explicit Chart() { std::fill(chart_.begin(), chart_.end(), nullptr); }

  // Iterator that allows iterating through recorded matches that end at a given
  // match offset.
  class Iterator {
   public:
    explicit Iterator(const int match_offset, const ParseTree* value)
        : match_offset_(match_offset), value_(value) {}

    bool Done() const {
      return value_ == nullptr ||
             (value_->codepoint_span.second < match_offset_);
    }
    const ParseTree* Item() const { return value_; }
    void Next() {
      TC3_DCHECK(!Done());
      value_ = value_->next;
    }

   private:
    const int match_offset_;
    const ParseTree* value_;
  };

  // Returns whether the chart contains a match for a given nonterminal.
  bool HasMatch(const Nonterm nonterm, const CodepointSpan& span) const;

  // Adds a match to the chart.
  void Add(ParseTree* item) {
    item->next = chart_[item->codepoint_span.second & kChartHashTableBitmask];
    chart_[item->codepoint_span.second & kChartHashTableBitmask] = item;
  }

  // Records a derivation of a root rule.
  void AddDerivation(const Derivation& derivation) {
    root_derivations_.push_back(derivation);
  }

  // Returns an iterator through all matches ending at `match_offset`.
  Iterator MatchesEndingAt(const int match_offset) const {
    const ParseTree* value = chart_[match_offset & kChartHashTableBitmask];
    // The chain of items is in decreasing `end` order.
    // Find the ones that have prev->end == item->begin.
    while (value != nullptr && (value->codepoint_span.second > match_offset)) {
      value = value->next;
    }
    return Iterator(match_offset, value);
  }

  const std::vector<Derivation> derivations() const {
    return root_derivations_;
  }

 private:
  static constexpr int kChartHashTableBitmask = NumBuckets - 1;
  std::array<ParseTree*, NumBuckets> chart_;
  std::vector<Derivation> root_derivations_;
};

template <int NumBuckets>
bool Chart<NumBuckets>::HasMatch(const Nonterm nonterm,
                                 const CodepointSpan& span) const {
  // Lookup by end.
  for (Chart<NumBuckets>::Iterator it = MatchesEndingAt(span.second);
       !it.Done(); it.Next()) {
    if (it.Item()->lhs == nonterm &&
        it.Item()->codepoint_span.first == span.first) {
      return true;
    }
  }
  return false;
}

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_CHART_H_
