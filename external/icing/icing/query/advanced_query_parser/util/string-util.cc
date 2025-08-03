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

#include "icing/query/advanced_query_parser/util/string-util.h"

#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {

namespace string_util {

libtextclassifier3::StatusOr<std::string> UnescapeStringValue(
    std::string_view value) {
  std::string result;
  bool in_escape = false;
  for (char c : value) {
    if (in_escape) {
      in_escape = false;
    } else if (c == '\\') {
      in_escape = true;
      continue;
    } else if (c == '"') {
      return absl_ports::InvalidArgumentError(
          "Encountered an unescaped quotation mark!");
    }
    result += c;
  }
  return result;
}

libtextclassifier3::StatusOr<std::string_view> FindEscapedToken(
    std::string_view escaped_string, std::string_view unescaped_token) {
  if (unescaped_token.empty()) {
    return absl_ports::InvalidArgumentError(
        "Cannot find escaped token in empty unescaped token.");
  }

  // Find the start of unescaped_token within the escaped_string
  const char* esc_string_end = escaped_string.data() + escaped_string.length();
  size_t pos = escaped_string.find(unescaped_token[0]);
  const char* esc_token_start = (pos == std::string_view::npos)
                                    ? esc_string_end
                                    : escaped_string.data() + pos;
  const char* esc_token_cur = esc_token_start;
  const char* possible_next_start = nullptr;
  bool is_escaped = false;
  int i = 0;
  for (; i < unescaped_token.length() && esc_token_cur < esc_string_end;
       ++esc_token_cur) {
    if (esc_token_cur != esc_token_start &&
        *esc_token_cur == unescaped_token[0] &&
        possible_next_start == nullptr) {
      possible_next_start = esc_token_cur;
    }

    // Every char in unescaped_token should either be an escape or match the
    // next char in unescaped_token.
    if (!is_escaped && *esc_token_cur == '\\') {
      is_escaped = true;
    } else if (*esc_token_cur == unescaped_token[i]) {
      is_escaped = false;
      ++i;
    } else {
      // No match. If we don't have a possible_next_start, then try to find one.
      if (possible_next_start == nullptr) {
        pos = escaped_string.find(unescaped_token[0],
                                  esc_token_cur - escaped_string.data());
        if (pos == std::string_view::npos) {
          break;
        }
        esc_token_start = escaped_string.data() + pos;
      } else {
        esc_token_start = possible_next_start;
        possible_next_start = nullptr;
      }
      // esc_token_start has been reset to a char that equals unescaped_token[0]
      // The for loop above will advance esc_token_cur so set i to 1.
      i = 1;
      esc_token_cur = esc_token_start;
    }
  }
  if (i != unescaped_token.length()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Couldn't match chars at token=", unescaped_token,
                           ") and raw_text=", escaped_string));
  }
  return std::string_view(esc_token_start, esc_token_cur - esc_token_start);
}

}  // namespace string_util

}  // namespace lib
}  // namespace icing