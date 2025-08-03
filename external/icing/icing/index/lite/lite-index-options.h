// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_LEGACY_INDEX_ICING_LITE_INDEX_OPTIONS_H_
#define ICING_LEGACY_INDEX_ICING_LITE_INDEX_OPTIONS_H_

#include <cstdint>
#include <string>

#include "icing/legacy/index/icing-dynamic-trie.h"

namespace icing {
namespace lib {

struct LiteIndexOptions {
  LiteIndexOptions() = default;
  // Creates LiteIndexOptions based off of the specified parameters. All
  // other fields are calculated based on the value of
  // hit_buffer_want_merge_bytes and the logic in CalculateHitBufferSize and
  // CalculateTrieOptions.
  LiteIndexOptions(const std::string& filename_base,
                   uint32_t hit_buffer_want_merge_bytes,
                   bool hit_buffer_sort_at_indexing,
                   uint32_t hit_buffer_sort_threshold_bytes);

  IcingDynamicTrie::Options lexicon_options;
  IcingDynamicTrie::Options display_mappings_options;

  std::string filename_base;
  uint32_t hit_buffer_want_merge_bytes = 0;
  uint32_t hit_buffer_size = 0;
  bool hit_buffer_sort_at_indexing = false;
  uint32_t hit_buffer_sort_threshold_bytes = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_LITE_INDEX_OPTIONS_H_
