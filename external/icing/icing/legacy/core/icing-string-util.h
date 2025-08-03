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

#ifndef ICING_LEGACY_CORE_ICING_STRING_UTIL_H_
#define ICING_LEGACY_CORE_ICING_STRING_UTIL_H_

#include <cstdarg>
#include <cstdint>
#include <string>

#include "icing/legacy/core/icing-compat.h"

namespace icing {
namespace lib {

class IcingStringUtil {
 public:
  // Returns true if the character is not the first byte of
  // a multi-byte UTF8 character
  static bool IsContinuationByte(char byte) {
    return (static_cast<uint8_t>(byte) & 0xC0) == 0x80;
  }

  // Update a rolling crc32. This undoes the one's complement
  // pre-conditioning and post-conditioning of zlib's
  // crc32. Therefore, UpdateCrc32(0, str, len) != HashCrc32(str,
  // len).
  static uint32_t UpdateCrc32(uint32_t crc, const char *str, int len);

  // Update a string's rolling crc for when its value at offset is
  // xor'ed with the buffer [xored_str, xored_str + len).
  //
  // REQUIRES: orig_len >= offset + len.
  static uint32_t UpdateAtPositionCrc32(uint32_t crc, int orig_len, int offset,
                                        const char *xored_str, int len);

  // Append vsnprintf to strp. If bufsize hint is > 0 it is
  // used. Otherwise we compute the required bufsize (which is somewhat
  // expensive).
  static void SStringAppendV(std::string *strp, int bufsize, const char *fmt,
                             va_list arglist);
  static void SStringAppendF(std::string *strp, int bufsize, const char *fmt,
                             ...) __attribute__((format(printf, 3, 4)));
  static std::string StringPrintf(const char *fmt, ...)
      __attribute__((format(printf, 1, 2)));
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_CORE_ICING_STRING_UTIL_H_
