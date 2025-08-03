/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ART_TEST_TI_AGENT_TI_UTF_H_
#define ART_TEST_TI_AGENT_TI_UTF_H_

#include <inttypes.h>
#include <string.h>

#include "android-base/logging.h"
#include "ti_macros.h"

namespace art {
namespace ti {

inline size_t CountModifiedUtf8Chars(const char* utf8, size_t byte_count) {
  DCHECK_LE(byte_count, strlen(utf8));
  size_t len = 0;
  const char* end = utf8 + byte_count;
  for (; utf8 < end; ++utf8) {
    int ic = *utf8;
    len++;
    if (LIKELY((ic & 0x80) == 0)) {
      // One-byte encoding.
      continue;
    }
    // Two- or three-byte encoding.
    utf8++;
    if ((ic & 0x20) == 0) {
      // Two-byte encoding.
      continue;
    }
    utf8++;
    if ((ic & 0x10) == 0) {
      // Three-byte encoding.
      continue;
    }

    // Four-byte encoding: needs to be converted into a surrogate
    // pair.
    utf8++;
    len++;
  }
  return len;
}

inline uint16_t GetTrailingUtf16Char(uint32_t maybe_pair) {
  return static_cast<uint16_t>(maybe_pair >> 16);
}

inline uint16_t GetLeadingUtf16Char(uint32_t maybe_pair) {
  return static_cast<uint16_t>(maybe_pair & 0x0000FFFF);
}

inline uint32_t GetUtf16FromUtf8(const char** utf8_data_in) {
  const uint8_t one = *(*utf8_data_in)++;
  if ((one & 0x80) == 0) {
    // one-byte encoding
    return one;
  }

  const uint8_t two = *(*utf8_data_in)++;
  if ((one & 0x20) == 0) {
    // two-byte encoding
    return ((one & 0x1f) << 6) | (two & 0x3f);
  }

  const uint8_t three = *(*utf8_data_in)++;
  if ((one & 0x10) == 0) {
    return ((one & 0x0f) << 12) | ((two & 0x3f) << 6) | (three & 0x3f);
  }

  // Four byte encodings need special handling. We'll have
  // to convert them into a surrogate pair.
  const uint8_t four = *(*utf8_data_in)++;

  // Since this is a 4 byte UTF-8 sequence, it will lie between
  // U+10000 and U+1FFFFF.
  //
  // TODO: What do we do about values in (U+10FFFF, U+1FFFFF) ? The
  // spec says they're invalid but nobody appears to check for them.
  const uint32_t code_point = ((one & 0x0f) << 18) | ((two & 0x3f) << 12)
      | ((three & 0x3f) << 6) | (four & 0x3f);

  uint32_t surrogate_pair = 0;
  // Step two: Write out the high (leading) surrogate to the bottom 16 bits
  // of the of the 32 bit type.
  surrogate_pair |= ((code_point >> 10) + 0xd7c0) & 0xffff;
  // Step three : Write out the low (trailing) surrogate to the top 16 bits.
  surrogate_pair |= ((code_point & 0x03ff) + 0xdc00) << 16;

  return surrogate_pair;
}

// Note: This is a copy of the code in `libdexfile`.
template <bool kUseShortZero, bool kUse4ByteSequence, bool kReplaceBadSurrogates, typename Append>
inline void ConvertUtf16ToUtf8(const uint16_t* utf16, size_t char_count, Append&& append) {
  static_assert(kUse4ByteSequence || !kReplaceBadSurrogates);

  // Use local helpers instead of macros from `libicu` to avoid the dependency on `libicu`.
  auto is_lead = [](uint16_t ch) ALWAYS_INLINE { return (ch & 0xfc00u) == 0xd800u; };
  auto is_trail = [](uint16_t ch) ALWAYS_INLINE { return (ch & 0xfc00u) == 0xdc00u; };
  auto is_surrogate = [](uint16_t ch) ALWAYS_INLINE { return (ch & 0xf800u) == 0xd800u; };
  auto is_surrogate_lead = [](uint16_t ch) ALWAYS_INLINE { return (ch & 0x0400u) == 0u; };
  auto get_supplementary = [](uint16_t lead, uint16_t trail) ALWAYS_INLINE {
    constexpr uint32_t offset = (0xd800u << 10) + 0xdc00u - 0x10000u;
    return (static_cast<uint32_t>(lead) << 10) + static_cast<uint32_t>(trail) - offset;
  };

  for (size_t i = 0u; i < char_count; ++i) {
    auto has_trail = [&]() { return i + 1u != char_count && is_trail(utf16[i + 1u]); };

    uint16_t ch = utf16[i];
    if (ch < 0x80u && (kUseShortZero || ch != 0u)) {
      // One byte.
      append(ch);
    } else if (ch < 0x800u) {
      // Two bytes.
      append((ch >> 6) | 0xc0);
      append((ch & 0x3f) | 0x80);
    } else if (kReplaceBadSurrogates
                   ? is_surrogate(ch)
                   : kUse4ByteSequence && is_lead(ch) && has_trail()) {
      if (kReplaceBadSurrogates && (!is_surrogate_lead(ch) || !has_trail())) {
        append('?');
      } else {
        // We have a *valid* surrogate pair.
        uint32_t code_point = get_supplementary(ch, utf16[i + 1u]);
        ++i;  //  Consume the leading surrogate.
        // Four bytes.
        append((code_point >> 18) | 0xf0);
        append(((code_point >> 12) & 0x3f) | 0x80);
        append(((code_point >> 6) & 0x3f) | 0x80);
        append((code_point & 0x3f) | 0x80);
      }
    } else {
      // Three bytes.
      append((ch >> 12) | 0xe0);
      append(((ch >> 6) & 0x3f) | 0x80);
      append((ch & 0x3f) | 0x80);
    }
  }
}

inline void ConvertUtf16ToModifiedUtf8(char* utf8_out,
                                       size_t byte_count,
                                       const uint16_t* utf16_in,
                                       size_t char_count) {
  if (LIKELY(byte_count == char_count)) {
    // Common case where all characters are ASCII.
    const uint16_t *utf16_end = utf16_in + char_count;
    for (const uint16_t *p = utf16_in; p < utf16_end;) {
      *utf8_out++ = static_cast<char>(*p++);
    }
    return;
  }

  // String contains non-ASCII characters.
  // FIXME: We should not emit 4-byte sequences. Bug: 192935764
  auto append = [&](char c) { *utf8_out++ = c; };
  ConvertUtf16ToUtf8</*kUseShortZero=*/ false,
                     /*kUse4ByteSequence=*/ true,
                     /*kReplaceBadSurrogates=*/ false>(utf16_in, char_count, append);
}

inline size_t CountModifiedUtf8BytesInUtf16(const uint16_t* chars, size_t char_count) {
  // FIXME: We should not emit 4-byte sequences. Bug: 192935764
  size_t result = 0;
  auto append = [&]([[maybe_unused]] char c) { ++result; };
  ConvertUtf16ToUtf8</*kUseShortZero=*/ false,
                     /*kUse4ByteSequence=*/ true,
                     /*kReplaceBadSurrogates=*/ false>(chars, char_count, append);
  return result;
}

}  // namespace ti
}  // namespace art

#endif  // ART_TEST_TI_AGENT_TI_UTF_H_
