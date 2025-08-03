// Copyright (C) 2023 Google LLC
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

#include "icing/util/encode-util.h"

#include <cstdint>
#include <string>
#include <string_view>

namespace icing {
namespace lib {

namespace encode_util {

std::string EncodeIntToCString(uint64_t value) {
  std::string encoded_str;
  // Encode it in base128 and add 1 to make sure that there is no 0-byte. This
  // increases the size of the encoded_str from 8-bytes to 10-bytes at worst.
  do {
    encoded_str.push_back((value & 0x7F) + 1);
    value >>= 7;
  } while (value);
  return encoded_str;
}

uint64_t DecodeIntFromCString(std::string_view encoded_str) {
  uint64_t value = 0;
  for (int i = encoded_str.length() - 1; i >= 0; --i) {
    value <<= 7;
    char c = encoded_str[i] - 1;
    value |= (c & 0x7F);
  }
  return value;
}

}  // namespace encode_util

}  // namespace lib
}  // namespace icing
