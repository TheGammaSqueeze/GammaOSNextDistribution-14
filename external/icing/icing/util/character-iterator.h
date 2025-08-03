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

#ifndef ICING_UTIL_CHARACTER_ITERATOR_H_
#define ICING_UTIL_CHARACTER_ITERATOR_H_

#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/i18n-utils.h"

namespace icing {
namespace lib {

class CharacterIterator {
 public:
  explicit CharacterIterator(std::string_view text)
      : CharacterIterator(text, 0, 0, 0) {}

  CharacterIterator(std::string_view text, int utf8_index, int utf16_index,
                    int utf32_index)
      : text_(text),
        cached_current_char_(i18n_utils::kInvalidUChar32),
        utf8_index_(utf8_index),
        utf16_index_(utf16_index),
        utf32_index_(utf32_index) {}

  // Returns the character that the iterator currently points to.
  // i18n_utils::kInvalidUChar32 if unable to read that character.
  UChar32 GetCurrentChar();

  // Moves current position to desired_utf8_index.
  // REQUIRES: 0 <= desired_utf8_index <= text_.length()
  bool MoveToUtf8(int desired_utf8_index);

  // Advances from current position to the character that includes the specified
  // UTF-8 index.
  // REQUIRES: desired_utf8_index <= text_.length()
  // desired_utf8_index is allowed to point one index past the end, but no
  // further.
  bool AdvanceToUtf8(int desired_utf8_index);

  // Rewinds from current position to the character that includes the specified
  // UTF-8 index.
  // REQUIRES: 0 <= desired_utf8_index
  bool RewindToUtf8(int desired_utf8_index);

  // Moves current position to desired_utf16_index.
  // REQUIRES: 0 <= desired_utf16_index <= text_.utf16_length()
  bool MoveToUtf16(int desired_utf16_index);

  // Advances current position to desired_utf16_index.
  // REQUIRES: desired_utf16_index <= text_.utf16_length()
  // desired_utf16_index is allowed to point one index past the end, but no
  // further.
  bool AdvanceToUtf16(int desired_utf16_index);

  // Rewinds current position to desired_utf16_index.
  // REQUIRES: 0 <= desired_utf16_index
  bool RewindToUtf16(int desired_utf16_index);

  // Moves current position to desired_utf32_index.
  // REQUIRES: 0 <= desired_utf32_index <= text_.utf32_length()
  bool MoveToUtf32(int desired_utf32_index);

  // Advances current position to desired_utf32_index.
  // REQUIRES: desired_utf32_index <= text_.utf32_length()
  // desired_utf32_index is allowed to point one index past the end, but no
  // further.
  bool AdvanceToUtf32(int desired_utf32_index);

  // Rewinds current position to desired_utf32_index.
  // REQUIRES: 0 <= desired_utf32_index
  bool RewindToUtf32(int desired_utf32_index);

  int utf8_index() const { return utf8_index_; }
  int utf16_index() const { return utf16_index_; }
  int utf32_index() const { return utf32_index_; }

  bool operator==(const CharacterIterator& rhs) const {
    // cached_current_char_ is just that: a cached value. As such, it's not
    // considered for equality.
    return text_ == rhs.text_ && utf8_index_ == rhs.utf8_index_ &&
           utf16_index_ == rhs.utf16_index_ && utf32_index_ == rhs.utf32_index_;
  }

  std::string DebugString() const {
    return IcingStringUtil::StringPrintf("(u8:%d,u16:%d,u32:%d)", utf8_index_,
                                         utf16_index_, utf32_index_);
  }

 private:
  // Resets the character iterator to the start of the text if any of the
  // indices are negative.
  void ResetToStartIfNecessary();

  std::string_view text_;
  UChar32 cached_current_char_;
  int utf8_index_;
  int utf16_index_;
  int utf32_index_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_CHARACTER_ITERATOR_H_
