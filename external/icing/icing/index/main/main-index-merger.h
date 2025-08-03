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

#ifndef ICING_INDEX_MAIN_MAIN_INDEX_MERGER_H_
#define ICING_INDEX_MAIN_MAIN_INDEX_MERGER_H_

#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/lite/lite-index.h"
#include "icing/index/main/main-index.h"
#include "icing/index/term-id-codec.h"

namespace icing {
namespace lib {

// Class used to merge hits from the lite_index and lite_lexicon into main_index
// and main_lexicon.
class MainIndexMerger {
 public:
  // Retrieves all hits in the lite index, translates the term ids of each
  // LiteIndex::Element and expands prefix hits based on the mapping from
  // lexicon_merge_outputs.other_tvi_to_prefix_main_tvis.
  //
  // RETURNS:
  //  - OK on success
  //  - INVALID_ARGUMENT if one of the elements in the lite index has a term_id
  //  that exceeds the max TermId
  static libtextclassifier3::StatusOr<std::vector<TermIdHitPair>>
  TranslateAndExpandLiteHits(
      const LiteIndex& lite_index, const TermIdCodec& term_id_codec,
      const MainIndex::LexiconMergeOutputs& lexicon_merge_outputs);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_MAIN_MAIN_INDEX_MERGER_H_
