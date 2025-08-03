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

#ifndef ICING_TRANSFORM_MAP_MAP_NORMALIZER_H_
#define ICING_TRANSFORM_MAP_MAP_NORMALIZER_H_

#include <string>
#include <string_view>

#include "icing/transform/normalizer.h"
#include "icing/util/character-iterator.h"

namespace icing {
namespace lib {

class MapNormalizer : public Normalizer {
 public:
  explicit MapNormalizer(int max_term_byte_size)
      : max_term_byte_size_(max_term_byte_size){};

  // Normalizes the input term based on character mappings. The mappings
  // contain the following categories:
  //   - Uppercase -> lowercase
  //   - Hiragana -> Katakana
  //   - Common full-width characters -> ASCII
  //   - Common ideographic punctuation marks -> ASCII
  //   - Common diacritic Latin characters -> ASCII
  //
  // Read more mapping details in normalization-map.cc
  std::string NormalizeTerm(std::string_view term) const override;

  // Returns a CharacterIterator pointing to one past the end of the segment of
  // term that (once normalized) matches with normalized_term.
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "yell") will return
  // CharacterIterator(u8:4, u16:4, u32:4).
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "red") will return
  // CharacterIterator(u8:0, u16:0, u32:0).
  CharacterIterator FindNormalizedMatchEndPosition(
      std::string_view term, std::string_view normalized_term) const override;

 private:
  // The maximum term length allowed after normalization.
  int max_term_byte_size_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TRANSFORM_MAP_MAP_NORMALIZER_H_
