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

#include "utils/strings/utf8.h"

#include "utils/base/logging.h"

namespace libtextclassifier3 {

bool IsValidUTF8(const char *src, int size) {
  int char_length;
  for (int i = 0; i < size;) {
    if (!IsValidChar(src + i, size - i, &char_length)) {
      return false;
    }
    i += char_length;
  }
  return true;
}

int SafeTruncateLength(const char *str, int truncate_at) {
  // Always want to truncate at the start of a character, so if
  // it's in a middle, back up toward the start
  while (IsTrailByte(str[truncate_at]) && (truncate_at > 0)) {
    truncate_at--;
  }
  return truncate_at;
}

char32 ValidCharToRune(const char *str) {
  TC3_DCHECK(!IsTrailByte(str[0]) && GetNumBytesForUTF8Char(str) > 0);

  // Convert from UTF-8
  unsigned char byte1 = static_cast<unsigned char>(str[0]);
  if (byte1 < 0x80) {
    // One character sequence: 00000 - 0007F.
    return byte1;
  }

  unsigned char byte2 = static_cast<unsigned char>(str[1]);
  if (byte1 < 0xE0) {
    // Two character sequence: 00080 - 007FF.
    return ((byte1 & 0x1F) << 6) | (byte2 & 0x3F);
  }

  unsigned char byte3 = static_cast<unsigned char>(str[2]);
  if (byte1 < 0xF0) {
    // Three character sequence: 00800 - 0FFFF.
    return ((byte1 & 0x0F) << 12) | ((byte2 & 0x3F) << 6) | (byte3 & 0x3F);
  }

  unsigned char byte4 = static_cast<unsigned char>(str[3]);
  // Four character sequence: 10000 - 1FFFF.
  return ((byte1 & 0x07) << 18) | ((byte2 & 0x3F) << 12) |
         ((byte3 & 0x3F) << 6) | (byte4 & 0x3F);
}

bool IsValidChar(const char *str, int size, int *num_bytes) {
  // Unexpected trail byte.
  if (IsTrailByte(str[0])) {
    return false;
  }

  *num_bytes = GetNumBytesForUTF8Char(str);
  if (*num_bytes <= 0 || *num_bytes > size) {
    return false;
  }

  // Check that remaining bytes in the codepoint are trailing bytes.
  for (int k = 1; k < *num_bytes; k++) {
    if (!IsTrailByte(str[k])) {
      return false;
    }
  }

  // Exclude overlong encodings.
  // Check that the codepoint is encoded with the minimum number of required
  // bytes. An ascii value could be encoded in 4, 3 or 2 bytes but requires
  // only 1. There is a unique valid encoding for each code point.
  // This ensures that string comparisons and searches are well-defined.
  // See: https://en.wikipedia.org/wiki/UTF-8
  const char32 codepoint = ValidCharToRune(str);
  switch (*num_bytes) {
    case 1:
      return true;
    case 2:
      // Everything below 128 can be encoded in one byte.
      return (codepoint >= (1 << 7 /* num. payload bits in one byte */));
    case 3:
      return (codepoint >= (1 << 11 /* num. payload bits in two utf8 bytes */));
    case 4:
      return (codepoint >=
              (1 << 16 /* num. payload bits in three utf8 bytes */)) &&
             (codepoint < 0x10FFFF /* maximum rune value */);
  }
  return false;
}

int ValidRuneToChar(const char32 rune, char *dest) {
  // Convert to unsigned for range check.
  uint32 c;

  // 1 char 00-7F
  c = rune;
  if (c <= 0x7F) {
    dest[0] = static_cast<char>(c);
    return 1;
  }

  // 2 char 0080-07FF
  if (c <= 0x07FF) {
    dest[0] = 0xC0 | static_cast<char>(c >> 1 * 6);
    dest[1] = 0x80 | (c & 0x3F);
    return 2;
  }

  // 3 char 0800-FFFF
  if (c <= 0xFFFF) {
    dest[0] = 0xE0 | static_cast<char>(c >> 2 * 6);
    dest[1] = 0x80 | ((c >> 1 * 6) & 0x3F);
    dest[2] = 0x80 | (c & 0x3F);
    return 3;
  }

  // 4 char 10000-1FFFFF
  dest[0] = 0xF0 | static_cast<char>(c >> 3 * 6);
  dest[1] = 0x80 | ((c >> 2 * 6) & 0x3F);
  dest[2] = 0x80 | ((c >> 1 * 6) & 0x3F);
  dest[3] = 0x80 | (c & 0x3F);
  return 4;
}

}  // namespace libtextclassifier3
