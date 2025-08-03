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

#ifndef ICING_UTIL_FINGERPRINT_UTIL_H_
#define ICING_UTIL_FINGERPRINT_UTIL_H_

#include <cstdint>
#include <string>
#include <string_view>

namespace icing {
namespace lib {

namespace fingerprint_util {

// Converts from a fingerprint to a fingerprint string.
std::string GetFingerprintString(uint64_t fingerprint);

// Converts from a fingerprint string to a fingerprint.
uint64_t GetFingerprint(std::string_view fingerprint_string);

// A formatter to properly handle a string that is actually just a hash value.
class FingerprintStringFormatter {
 public:
  std::string operator()(std::string_view fingerprint_string) {
    uint64_t fingerprint = GetFingerprint(fingerprint_string);
    return std::to_string(fingerprint);
  }
};

}  // namespace fingerprint_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_FINGERPRINT_UTIL_H_
