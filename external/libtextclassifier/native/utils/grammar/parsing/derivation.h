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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_DERIVATION_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_DERIVATION_H_

#include <vector>

#include "utils/grammar/parsing/parse-tree.h"

namespace libtextclassifier3::grammar {

// A parse tree for a root rule.
struct Derivation {
  const ParseTree* parse_tree;
  int64 rule_id;

  // Checks that all assertions are fulfilled.
  bool IsValid() const;
  int64 GetRuleId() const { return rule_id; }
  const ParseTree* GetParseTree() const { return parse_tree; }
};

// Deduplicates rule derivations by containing overlap.
// The grammar system can output multiple candidates for optional parts.
// For example if a rule has an optional suffix, we
// will get two rule derivations when the suffix is present: one with and one
// without the suffix. We therefore deduplicate by containing overlap, viz. from
// two candidates we keep the longer one if it completely contains the shorter.
// This factory function works with any type T that extends Derivation.
template <typename T, typename std::enable_if<std::is_base_of<
                          Derivation, T>::value>::type* = nullptr>
// std::vector<T> DeduplicateDerivations(const std::vector<T>& derivations);
std::vector<T> DeduplicateDerivations(const std::vector<T>& derivations) {
  std::vector<T> sorted_candidates = derivations;

  std::stable_sort(sorted_candidates.begin(), sorted_candidates.end(),
                   [](const T& a, const T& b) {
                     // Sort by id.
                     if (a.GetRuleId() != b.GetRuleId()) {
                       return a.GetRuleId() < b.GetRuleId();
                     }

                     // Sort by increasing start.
                     if (a.GetParseTree()->codepoint_span.first !=
                         b.GetParseTree()->codepoint_span.first) {
                       return a.GetParseTree()->codepoint_span.first <
                              b.GetParseTree()->codepoint_span.first;
                     }

                     // Sort by decreasing end.
                     return a.GetParseTree()->codepoint_span.second >
                            b.GetParseTree()->codepoint_span.second;
                   });

  // Deduplicate by overlap.
  std::vector<T> result;
  for (int i = 0; i < sorted_candidates.size(); i++) {
    const T& candidate = sorted_candidates[i];
    bool eliminated = false;

    // Due to the sorting above, the candidate can only be completely
    // intersected by a match before it in the sorted order.
    for (int j = i - 1; j >= 0; j--) {
      if (sorted_candidates[j].rule_id != candidate.rule_id) {
        break;
      }
      if (sorted_candidates[j].parse_tree->codepoint_span.first <=
              candidate.parse_tree->codepoint_span.first &&
          sorted_candidates[j].parse_tree->codepoint_span.second >=
              candidate.parse_tree->codepoint_span.second) {
        eliminated = true;
        break;
      }
    }
    if (!eliminated) {
      result.push_back(candidate);
    }
  }
  return result;
}

// Deduplicates and validates rule derivations.
std::vector<Derivation> ValidDeduplicatedDerivations(
    const std::vector<Derivation>& derivations);

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_DERIVATION_H_
