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

#ifndef ICING_TESTING_SNIPPET_HELPERS_H_
#define ICING_TESTING_SNIPPET_HELPERS_H_

#include <string>

#include "icing/proto/document.pb.h"
#include "icing/proto/search.pb.h"

namespace icing {
namespace lib {

// Retrieve pointer to the PropertyProto identified by property_name.
// Returns nullptr if no such property exists.
//
// NOTE: This function does not handle nesting or indexes. "foo.bar" will return
// a nullptr even if document contains a property called "foo" that contains a
// subproperty called "bar".
const PropertyProto* GetProperty(const DocumentProto& document,
                                 const std::string& property_name);

// Retrieves all windows defined by the snippet_proto for the content.
std::vector<std::string_view> GetWindows(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto);

// Retrieves all matches defined by the snippet_proto for the content.
std::vector<std::string_view> GetMatches(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto);

// Retrieves all submatches defined by the snippet_proto for the content.
std::vector<std::string_view> GetSubMatches(
    std::string_view content, const SnippetProto::EntryProto& snippet_proto);

// Retrieves the string value held in the document corresponding to the
// property_path_expr.
// Example:
//   - GetString(doc, "foo") will retrieve the first string value in the
//     property "foo" in document or an empty string if it doesn't exist.
//   - GetString(doc, "foo[1].bar[2]") will retrieve the third string value in
//     the subproperty "bar" of the second document value in the property "foo".
std::string_view GetString(const DocumentProto* document,
                           std::string_view property_path_expr);

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_SNIPPET_HELPERS_H_
