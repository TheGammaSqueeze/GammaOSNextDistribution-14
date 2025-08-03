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

#ifndef ICING_ABSL_PORTS_ASCII_STR_TO_LOWER_H_
#define ICING_ABSL_PORTS_ASCII_STR_TO_LOWER_H_

#include <string>

namespace icing {
namespace lib {
namespace absl_ports {

// Converts the characters in `s` to lowercase, changing the contents of `s`.
void AsciiStrToLower(std::string* s);

// Creates a lowercase string from a given std::string_view.
inline std::string AsciiStrToLower(std::string_view s) {
  std::string result(s);
  AsciiStrToLower(&result);
  return result;
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ABSL_PORTS_ASCII_TO_LOWER_H_
