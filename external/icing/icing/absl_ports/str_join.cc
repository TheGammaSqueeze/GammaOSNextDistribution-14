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

#include "icing/absl_ports/str_join.h"

namespace icing {
namespace lib {
namespace absl_ports {

std::vector<std::string_view> StrSplit(std::string_view text,
                                       std::string_view sep) {
  std::vector<std::string_view> substrings;
  size_t separator_position = text.find(sep);
  size_t current_start = 0;
  size_t current_end = separator_position;
  while (separator_position != std::string_view::npos) {
    substrings.push_back(
        text.substr(current_start, current_end - current_start));
    current_start = current_end + sep.length();
    separator_position = text.find(sep, current_start);
    current_end = separator_position;
  }
  current_end = text.length();
  substrings.push_back(text.substr(current_start, current_end - current_start));
  return substrings;
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing
