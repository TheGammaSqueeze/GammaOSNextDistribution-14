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

#ifndef ICING_UTIL_CRC32_H_
#define ICING_UTIL_CRC32_H_

#include <cstdint>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"

namespace icing {
namespace lib {

// Efficient mechanism to incrementally compute checksum of a file and keep it
// updated when its content changes. Internally uses zlib based crc32()
// implementation.
//
// See https://www.zlib.net/manual.html#Checksum for more details.
class Crc32 {
 public:
  // Default to the checksum of an empty string, that is "0".
  Crc32() : crc_(0) {}

  explicit Crc32(uint32_t init_crc) : crc_(init_crc) {}

  explicit Crc32(std::string_view str) : crc_(0) { Append(str); }

  inline bool operator==(const Crc32& other) const {
    return crc_ == other.Get();
  }

  // Returns the checksum of all the data that has been processed till now.
  uint32_t Get() const;

  // Incrementally update the current checksum to reflect the fact that the
  // underlying data has been appended with 'str'. It calculates a new crc32
  // based on the current crc value and the newly appended string.
  //
  // NOTE: As this method accepts incremental appends, all these 3 will lead to
  // the same checksum:
  // 1) crc32.Append("AAA"); crc32.Append("BBB");
  // 2) crc32.Append("AAABBB");
  // 3) crc32.Append("AA"); crc32.Append("AB"); crc32.Append("BB");
  //
  // NOTE: While this class internally uses zlib's crc32(),
  // Crc32(base_crc).Append(str) is not the same as zlib::crc32(base_crc, str);
  uint32_t Append(std::string_view str);

  // Update a string's rolling crc when some content is modified in the middle
  // at an offset. We need the xored_str, which is the new value xored with the
  // original value.
  //
  // Original string:
  //   string(original_start | original_mid | original_end)
  //          -------------------------------------------> full_data_size
  //                         ^ offset position
  //
  // Modified string:
  //   string(original_start | changed_mid | original_end)
  //                         ^ offset position
  //
  // And where
  //   xored_str = changed_mid ^ original_mid
  //   xored_len = length(xored_str)
  //   full_data_size = the length of all the strings that have been Appended to
  //                    generate the current checksum
  //
  // REQUIRES: offset position + xored_len <= full_data_size.
  //
  // E.g.
  // Old data: ABCDEF; New data: ABXYZF
  //
  // Crc32 crc32; crc32.Append("ABCDEF");
  // crc32.UpdateWithXor("CDE" xor "XYZ", 6, 2);
  //
  // This is the same as
  // Crc32 crc32; crc32.Append("ABXYZF");
  //
  // See .cc file for implementation notes.
  //
  // Returns:
  //   Updated crc on success
  //   INVALID_ARGUMENT if offset position + xored_len > full_data_size
  libtextclassifier3::StatusOr<uint32_t> UpdateWithXor(
      std::string_view xored_str, int full_data_size, int position);

 private:
  uint32_t crc_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_CRC32_H_
