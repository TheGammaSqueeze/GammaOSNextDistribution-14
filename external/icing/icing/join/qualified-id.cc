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

#include "icing/join/qualified-id.h"

#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Since we use '#' as the separator and '\' to escape '\' and '#', only these 2
// characters are considered special characters to parse qualified id.
bool IsSpecialCharacter(char c) {
  return c == QualifiedId::kEscapeChar ||
         c == QualifiedId::kNamespaceUriSeparator;
}

// Helper function to verify the format (check the escape format and make sure
// number of separator '#' is 1) and find the position of the unique separator.
//
// Returns:
//   A valid index of the separator on success.
//   std::string::npos if the escape format of content is incorrect.
//   std::string::npos if the content contains 0 or more than 1 separators.
//   std::string::npos if the content contains '\0'.
size_t VerifyFormatAndGetSeparatorPosition(std::string_view content) {
  size_t separator_pos = std::string::npos;
  for (size_t i = 0; i < content.length(); ++i) {
    if (content[i] == '\0') {
      return std::string::npos;
    }

    if (content[i] == QualifiedId::kEscapeChar) {
      // Advance to the next character.
      ++i;
      if (i >= content.length() || !IsSpecialCharacter(content[i])) {
        // Invalid escape format.
        return std::string::npos;
      }
    } else if (content[i] == QualifiedId::kNamespaceUriSeparator) {
      if (separator_pos != std::string::npos) {
        // Found another separator, so return std::string::npos since only one
        // separator is allowed.
        return std::string::npos;
      }
      separator_pos = i;
    }
  }
  return separator_pos;
}

// Helper function to unescape the content.
libtextclassifier3::StatusOr<std::string> Unescape(std::string_view content) {
  std::string unescaped_content;
  for (size_t i = 0; i < content.length(); ++i) {
    if (content[i] == QualifiedId::kEscapeChar) {
      // Advance to the next character.
      ++i;
      if (i >= content.length() || !IsSpecialCharacter(content[i])) {
        // Invalid escape format.
        return absl_ports::InvalidArgumentError("Invalid escape format");
      }
    }
    unescaped_content += content[i];
  }
  return unescaped_content;
}

}  // namespace

/* static */ libtextclassifier3::StatusOr<QualifiedId> QualifiedId::Parse(
    std::string_view qualified_id_str) {
  size_t separator_pos = VerifyFormatAndGetSeparatorPosition(qualified_id_str);
  if (separator_pos == std::string::npos) {
    return absl_ports::InvalidArgumentError(
        "Failed to find the position of separator");
  }

  if (separator_pos == 0 || separator_pos + 1 >= qualified_id_str.length()) {
    return absl_ports::InvalidArgumentError(
        "Namespace or uri cannot be empty after parsing");
  }

  ICING_ASSIGN_OR_RETURN(std::string name_space,
                         Unescape(qualified_id_str.substr(0, separator_pos)));
  ICING_ASSIGN_OR_RETURN(std::string uri,
                         Unescape(qualified_id_str.substr(separator_pos + 1)));
  return QualifiedId(std::move(name_space), std::move(uri));
}

}  // namespace lib
}  // namespace icing
