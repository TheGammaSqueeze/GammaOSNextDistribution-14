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

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER__STRING_UTIL_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER__STRING_UTIL_H_

#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"

namespace icing {
namespace lib {

namespace string_util {

// Returns:
//   - On success, value with the escapes removed.
//   - INVALID_ARGUMENT if an non-escaped quote is encountered.
//  Ex. "fo\\\\o" -> "fo\\o"
libtextclassifier3::StatusOr<std::string> UnescapeStringValue(
    std::string_view value);

// Returns:
//   - On success, string_view pointing to the segment of escaped_string that,
//     if unescaped, would match unescaped_token.
//   - INVALID_ARGUMENT
//  Ex. escaped_string="foo b\\a\\\"r baz", unescaped_token="ba\"r"
//      returns "b\\a\\\"r"
libtextclassifier3::StatusOr<std::string_view> FindEscapedToken(
    std::string_view escaped_string, std::string_view unescaped_token);

}  // namespace string_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER__STRING_UTIL_H_
