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

#include "annotator/vocab/vocab-level-table.h"

#include <cstddef>
#include <memory>

#include "annotator/model_generated.h"
#include "utils/base/endian.h"
#include "utils/container/bit-vector.h"
#include "utils/optional.h"
#include "marisa/trie.h"

namespace libtextclassifier3 {

std::unique_ptr<VocabLevelTable> VocabLevelTable::Create(
    const VocabModel* model) {
  if (!LittleEndian::IsLittleEndian()) {
    // TODO(tonymak) Consider making this work on a big endian device.
    TC3_LOG(ERROR)
        << "VocabLevelTable is only working on a little endian device.";
    return nullptr;
  }
  const flatbuffers::Vector<uint8_t>* trie_data = model->vocab_trie();
  if (trie_data == nullptr) {
    TC3_LOG(ERROR) << "vocab_trie is missing from the model file.";
    return nullptr;
  }
  std::unique_ptr<marisa::Trie> vocab_trie(new marisa::Trie);
  vocab_trie->map(trie_data->data(), trie_data->size());

  return std::unique_ptr<VocabLevelTable>(new VocabLevelTable(
      model, std::move(vocab_trie), BitVector(model->beginner_level()),
      BitVector(model->do_not_trigger_in_upper_case())));
}

VocabLevelTable::VocabLevelTable(const VocabModel* model,
                                 std::unique_ptr<marisa::Trie> vocab_trie,
                                 const BitVector beginner_level,
                                 const BitVector do_not_trigger_in_upper_case)
    : model_(model),
      vocab_trie_(std::move(vocab_trie)),
      beginner_level_(beginner_level),
      do_not_trigger_in_upper_case_(do_not_trigger_in_upper_case) {}

Optional<LookupResult> VocabLevelTable::Lookup(const std::string& vocab) const {
  marisa::Agent agent;
  agent.set_query(vocab.data(), vocab.size());
  if (vocab_trie_->lookup(agent)) {
    const int vector_idx = agent.key().id();
    return Optional<LookupResult>({beginner_level_[vector_idx],
                                   do_not_trigger_in_upper_case_[vector_idx]});
  }
  return Optional<LookupResult>();
}
}  // namespace libtextclassifier3
