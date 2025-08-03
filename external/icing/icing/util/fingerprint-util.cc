// Copyright (C) 2022 Google LLC
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

#include "icing/util/fingerprint-util.h"

namespace icing {
namespace lib {

namespace fingerprint_util {

// A formatter to properly handle a string that is actually just a hash value.
std::string GetFingerprintString(uint64_t fingerprint) {
  std::string encoded_fprint;
  // DynamicTrie cannot handle keys with '0' as bytes. So, we encode it in
  // base128 and add 1 to make sure that no byte is '0'. This increases the
  // size of the encoded_fprint from 8-bytes to 10-bytes.
  while (fingerprint) {
    encoded_fprint.push_back((fingerprint & 0x7F) + 1);
    fingerprint >>= 7;
  }
  return encoded_fprint;
}

uint64_t GetFingerprint(std::string_view fingerprint_string) {
  uint64_t fprint = 0;
  for (int i = fingerprint_string.length() - 1; i >= 0; --i) {
    fprint <<= 7;
    char c = fingerprint_string[i] - 1;
    fprint |= (c & 0x7F);
  }
  return fprint;
}

}  // namespace fingerprint_util

}  // namespace lib
}  // namespace icing
