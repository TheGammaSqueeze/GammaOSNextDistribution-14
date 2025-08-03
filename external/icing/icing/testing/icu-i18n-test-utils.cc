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

#include "icing/testing/icu-i18n-test-utils.h"

#include <cstdint>
#include <string>

#include "icing/util/logging.h"
#include "unicode/umachine.h"
#include "unicode/utf8.h"

namespace icing {
namespace lib {

std::string UCharToString(UChar32 uchar) {
  std::string result;
  uint8_t utf8_buffer[4];  // U8_APPEND writes 0 to 4 bytes

  int utf8_index = 0;
  UBool has_error = false;

  // utf8_index is advanced to the end of the contents if successful
  U8_APPEND(utf8_buffer, utf8_index, sizeof(utf8_buffer), uchar, has_error);

  if (has_error) {
    ICING_VLOG(1) << "Error converting UChar32 to UTF8";
    return "";
  }
  result.append(reinterpret_cast<char*>(utf8_buffer), utf8_index);
  return result;
}

}  // namespace lib
}  // namespace icing
