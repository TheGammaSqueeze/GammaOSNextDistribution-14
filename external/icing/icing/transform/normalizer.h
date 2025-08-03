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

#ifndef ICING_TRANSFORM_NORMALIZER_H_
#define ICING_TRANSFORM_NORMALIZER_H_

#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/util/character-iterator.h"

namespace icing {
namespace lib {

// Normalizes strings for text matching.
//
// Example use:
//   ICING_ASSIGN_OR_RETURN(auto normalizer,
//       normalizer_factory::Create(/*max_term_byte_size=*/5);
//
//   std::string normalized_text = normalizer->NormalizeText("HELLO!");
//   ICING_LOG(INFO) << normalized_text; // prints "hello"
class Normalizer {
 public:
  virtual ~Normalizer() = default;

  // Normalizes the input term based on rules. See implementation classes for
  // specific transformation rules.
  virtual std::string NormalizeTerm(std::string_view term) const = 0;

  // Returns a CharacterIterator pointing to one past the end of the segment of
  // term that (once normalized) matches with normalized_term.
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "yell") will return
  // CharacterIterator(u8:4, u16:4, u32:4).
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "red") will return
  // CharacterIterator(u8:0, u16:0, u32:0).
  virtual CharacterIterator FindNormalizedMatchEndPosition(
      std::string_view term, std::string_view normalized_term) const = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TRANSFORM_NORMALIZER_H_
