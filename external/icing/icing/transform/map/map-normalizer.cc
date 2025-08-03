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

#include "icing/transform/map/map-normalizer.h"

#include <cctype>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>

#include "icing/absl_ports/str_cat.h"
#include "icing/transform/map/normalization-map.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/logging.h"
#include "unicode/utypes.h"

namespace icing {
namespace lib {

namespace {

UChar32 NormalizeChar(UChar32 c) {
  if (i18n_utils::GetUtf16Length(c) > 1) {
    // All the characters we need to normalize can be encoded into a
    // single char16_t. If this character needs more than 1 char16_t code
    // unit, we can skip normalization and append it directly.
    return c;
  }

  // The original character can be encoded into a single char16_t.
  const std::unordered_map<char16_t, char16_t>* normalization_map =
      GetNormalizationMap();
  if (normalization_map == nullptr) {
    // Normalization map couldn't be properly initialized, append the original
    // character.
    ICING_LOG(WARNING) << "Unable to get a valid pointer to normalization map!";
    return c;
  }
  auto iterator = normalization_map->find(static_cast<char16_t>(c));
  if (iterator == normalization_map->end()) {
    // Normalization mapping not found, append the original character.
    return c;
  }

  // Found a normalization mapping. The normalized character (stored in a
  // char16_t) can have 1 or 2 bytes.
  if (i18n_utils::IsAscii(iterator->second)) {
    // The normalized character has 1 byte. It may be an upper-case char.
    // Lower-case it before returning it.
    return std::tolower(static_cast<char>(iterator->second));
  } else {
    return iterator->second;
  }
}

}  // namespace

std::string MapNormalizer::NormalizeTerm(std::string_view term) const {
  std::string normalized_text;
  normalized_text.reserve(term.length());

  int current_pos = 0;
  while (current_pos < term.length()) {
    if (i18n_utils::IsAscii(term[current_pos])) {
      normalized_text.push_back(std::tolower(term[current_pos]));
      ++current_pos;
    } else {
      UChar32 uchar32 =
          i18n_utils::GetUChar32At(term.data(), term.length(), current_pos);
      if (uchar32 == i18n_utils::kInvalidUChar32) {
        ICING_LOG(WARNING) << "Unable to get uchar32 from " << term
                           << " at position" << current_pos;
        ++current_pos;
        continue;
      }
      UChar32 normalized_char32 = NormalizeChar(uchar32);
      if (i18n_utils::IsAscii(normalized_char32)) {
        normalized_text.push_back(normalized_char32);
      } else {
        // The normalized character has 2 bytes.
        i18n_utils::AppendUchar32ToUtf8(&normalized_text, normalized_char32);
      }
      current_pos += i18n_utils::GetUtf8Length(uchar32);
    }
  }

  if (normalized_text.length() > max_term_byte_size_) {
    i18n_utils::SafeTruncateUtf8(&normalized_text, max_term_byte_size_);
  }

  return normalized_text;
}

CharacterIterator MapNormalizer::FindNormalizedMatchEndPosition(
    std::string_view term, std::string_view normalized_term) const {
  CharacterIterator char_itr(term);
  CharacterIterator normalized_char_itr(normalized_term);
  while (char_itr.utf8_index() < term.length() &&
         normalized_char_itr.utf8_index() < normalized_term.length()) {
    UChar32 c = char_itr.GetCurrentChar();
    if (i18n_utils::IsAscii(c)) {
      c = std::tolower(c);
    } else {
      c = NormalizeChar(c);
    }
    UChar32 normalized_c = normalized_char_itr.GetCurrentChar();
    if (c != normalized_c) {
      return char_itr;
    }
    char_itr.AdvanceToUtf32(char_itr.utf32_index() + 1);
    normalized_char_itr.AdvanceToUtf32(normalized_char_itr.utf32_index() + 1);
  }
  return char_itr;
}

}  // namespace lib
}  // namespace icing
