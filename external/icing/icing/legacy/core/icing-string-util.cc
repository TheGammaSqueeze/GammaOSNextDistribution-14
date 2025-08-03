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
#include "icing/legacy/core/icing-string-util.h"

#include <algorithm>
#include <cstdarg>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <string>

#include "icing/legacy/portable/icing-zlib.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

uint32_t IcingStringUtil::UpdateCrc32(uint32_t crc, const char *str, int len) {
  if (len > 0) {
    crc = ~crc32(~crc, reinterpret_cast<const Bytef *>(str), len);
  }
  return crc;
}

uint32_t IcingStringUtil::UpdateAtPositionCrc32(uint32_t crc, int orig_len,
                                                int offset,
                                                const char *xored_str,
                                                int len) {
  // For appending, use UpdateCrc32.
  if (offset + len > orig_len) {
    ICING_LOG(FATAL) << "offset: " << offset << " length: " << len
                     << "original_length: " << orig_len;
  }

  // We have CRC(A|U|B) and we want CRC(A|V|B) where U is the slice
  // that updated to V.
  //
  // Then if xored_str = X = U ^ V:
  //
  // CRC(A|V|B) = CRC(0_lenA|X|0_lenB ^ A|U|B)
  // = CRC(0_lenA|X|0_lenB) ^ CRC(A|U|B)
  // = CRC_COMBINE(CRC(0_lenA), CRC_COMBINE(CRC(X), CRC(0_lenB)) ^ CRC(A|U|B)
  //
  // But CRC(0s) = 0, and CRC_COMBINE(0, X) = X, so this becomes
  // = CRC_COMBINE(CRC(X), CRC(0_lenB)) ^ CRC(A|U|B)
  uint32_t update_crc = UpdateCrc32(0, xored_str, len);
  update_crc = crc32_combine(update_crc, 0, orig_len - (offset + len));
  return crc ^ update_crc;
}

void IcingStringUtil::SStringAppendV(std::string *strp, int bufsize,
                                     const char *fmt, va_list arglist) {
  int capacity = bufsize;
  if (capacity <= 0) {
    va_list backup;
    va_copy(backup, arglist);
    capacity = vsnprintf(nullptr, 0, fmt, backup);
    va_end(arglist);
  }

  size_t start = strp->size();
  strp->resize(strp->size() + capacity + 1);

  int written = vsnprintf(&(*strp)[start], capacity + 1, fmt, arglist);
  va_end(arglist);
  strp->resize(start + std::min(capacity, written));
}

void IcingStringUtil::SStringAppendF(std::string *strp, int bufsize,
                                     const char *fmt, ...) {
  va_list arglist;
  va_start(arglist, fmt);
  SStringAppendV(strp, bufsize, fmt, arglist);
}

std::string IcingStringUtil::StringPrintf(const char *fmt, ...) {
  std::string s;
  va_list arglist;
  va_start(arglist, fmt);
  SStringAppendV(&s, 0, fmt, arglist);
  return s;
}

}  // namespace lib
}  // namespace icing
