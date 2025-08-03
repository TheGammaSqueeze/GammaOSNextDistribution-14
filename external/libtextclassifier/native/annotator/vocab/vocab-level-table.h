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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_LEVEL_TABLE_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_LEVEL_TABLE_H_

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/container/bit-vector.h"
#include "marisa/trie.h"

namespace libtextclassifier3 {

struct LookupResult {
  // Whether to trigger define for users of beginner proficiency.
  bool beginner_level;
  // Whether if we should avoid triggering define if the leading character is in
  // upper case.
  bool do_not_trigger_in_upper_case;
};

// A table of vocabs and their levels which is backed by a marisa trie.
// See http://www.s-yata.jp/marisa-trie/docs/readme.en.html.
class VocabLevelTable {
 public:
  static std::unique_ptr<VocabLevelTable> Create(const VocabModel* model);

  Optional<LookupResult> Lookup(const std::string& vocab) const;

 private:
  explicit VocabLevelTable(const VocabModel* model,
                           std::unique_ptr<marisa::Trie> vocab_trie,
                           const BitVector beginner_level,
                           const BitVector do_not_trigger_in_upper_case);
  static const VocabModel* LoadAndVerifyModel();

  const VocabModel* model_;
  const std::unique_ptr<marisa::Trie> vocab_trie_;
  const BitVector beginner_level_;
  const BitVector do_not_trigger_in_upper_case_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_LEVEL_TABLE_H_
