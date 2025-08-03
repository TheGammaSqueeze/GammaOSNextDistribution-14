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

#include "icing/util/crc32.h"

#include <cstdint>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/portable/zlib.h"

namespace icing {
namespace lib {

namespace {
uint32_t UpdateCrc32(uint32_t crc, const std::string_view str) {
  if (str.length() > 0) {
    // crc32() already includes a pre- and post-condition of taking the one's
    // complement of the value.
    crc =
        ~crc32(~crc, reinterpret_cast<const Bytef*>(str.data()), str.length());
  }
  return crc;
}
}  // namespace

uint32_t Crc32::Get() const { return crc_; }

uint32_t Crc32::Append(const std::string_view str) {
  crc_ = UpdateCrc32(crc_, str);
  return crc_;
}

libtextclassifier3::StatusOr<uint32_t> Crc32::UpdateWithXor(
    const std::string_view xored_str, int full_data_size, int position) {
  // For appending, use Append().
  if (position + xored_str.length() > full_data_size) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "offset position %d + length %zd > full data size %d", position,
        xored_str.length(), full_data_size));
  }

  // We have CRC(A|U|B) and we want CRC(A|V|B) where U is the slice
  // that updated to V.
  //
  // xored_str = X = U ^ V
  //
  // Some terminology:
  //   `|`: denotes concatenation, NOT the bitwise operator OR
  //
  //   (A|U|B): a concatenated string of A+U+B
  //
  //   CRC(A|U|B): The crc of a concatenated string of A+U+B
  //
  //   0_lenA: a string of 0's of the length of string A
  //
  //
  // (A|V|B) = (0_lenA|X|0_lenB) ^ (A|U|B)
  //
  // since CRC(D) = CRC(E) ^ CRC(F), where D = E ^ F:
  // CRC(A|V|B)
  //   = CRC(0_lenA|X|0_lenB) ^ CRC(A|U|B)
  //
  // and CRC(D|E) = CRC_COMBINE(D, E), so
  //   = CRC_COMBINE(CRC(0_lenA), CRC_COMBINE(CRC(X), CRC(0_lenB)) ^ CRC(A|U|B)
  //
  // and CRC(0) = 0, so
  //   = CRC_COMBINE(0, CRC_COMBINE(CRC(X), CRC(0_lenB)) ^ CRC(A|U|B)
  //
  // and CRC(0|B) = CRC(B), so
  //   = CRC_COMBINE(CRC(X), CRC(0_lenB)) ^ CRC(A|U|B)
  //
  // For more details, see this post by Mark Adler, one of the authors of zlib:
  // https://stackoverflow.com/questions/23122312/crc-calculation-of-a-mostly-static-data-stream/23126768#23126768

  uint32_t update_crc = UpdateCrc32(0, xored_str);
  update_crc = crc32_combine(update_crc, 0,
                             full_data_size - (position + xored_str.length()));
  crc_ ^= update_crc;
  return crc_;
}

}  // namespace lib
}  // namespace icing
