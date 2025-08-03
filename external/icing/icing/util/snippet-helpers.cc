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

#include "icing/util/snippet-helpers.h"

#include <algorithm>
#include <string_view>

#include "icing/proto/document.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/property-util.h"

namespace icing {
namespace lib {

std::vector<std::string_view> GetWindows(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto) {
  std::vector<std::string_view> windows;
  for (const SnippetMatchProto& match : snippet_proto.snippet_matches()) {
    windows.push_back(content.substr(match.window_byte_position(),
                                     match.window_byte_length()));
  }
  return windows;
}

std::vector<std::string_view> GetMatches(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto) {
  std::vector<std::string_view> matches;
  for (const SnippetMatchProto& match : snippet_proto.snippet_matches()) {
    matches.push_back(content.substr(match.exact_match_byte_position(),
                                     match.exact_match_byte_length()));
  }
  return matches;
}

std::vector<std::string_view> GetSubMatches(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto) {
  std::vector<std::string_view> matches;
  for (const SnippetMatchProto& match : snippet_proto.snippet_matches()) {
    matches.push_back(content.substr(match.exact_match_byte_position(),
                                     match.submatch_byte_length()));
  }
  return matches;
}

std::string_view GetString(const DocumentProto* document,
                           std::string_view property_path_expr) {
  std::vector<std::string_view> properties =
      property_util::SplitPropertyPathExpr(property_path_expr);
  for (int i = 0; i < properties.size(); ++i) {
    property_util::PropertyInfo property_info =
        property_util::ParsePropertyNameExpr(properties.at(i));
    if (property_info.index == property_util::kWildcardPropertyIndex) {
      // Use index = 0 by default.
      property_info.index = 0;
    }

    const PropertyProto* prop =
        property_util::GetPropertyProto(*document, property_info.name);
    if (prop == nullptr) {
      // requested property doesn't exist in the document. Return empty string.
      return "";
    }
    if (i == properties.size() - 1) {
      // The last property. Get the string_value
      if (prop->string_values_size() - 1 < property_info.index) {
        // The requested string doesn't exist. Return empty string.
        return "";
      }
      return prop->string_values(property_info.index);
    } else if (prop->document_values_size() - 1 < property_info.index) {
      // The requested subproperty doesn't exist. return an empty string.
      return "";
    } else {
      // Go to the next subproperty.
      document = &prop->document_values(property_info.index);
    }
  }
  return "";
}

}  // namespace lib
}  // namespace icing
