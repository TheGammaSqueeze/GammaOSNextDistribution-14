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

#ifndef ICING_SCHEMA_PROPERTY_UTIL_H_
#define ICING_SCHEMA_PROPERTY_UTIL_H_

#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/proto/document.pb.h"

namespace icing {
namespace lib {

namespace property_util {

// Definition:
// - Expr (short for expression): with or without index.
// - property_name: one level of property name without index. E.g. "abc", "def".
// - property_name_expr: one level of property name with or without index. E.g.
//                       "abc", "abc[0]", "def[1]".
// - property_path: multiple levels (including one) of property names without
//                  indices. E.g. "abc", "abc.def".
// - property_path_expr: multiple levels (including one) of property name
//                       expressions. E.g. "abc", "abc[0]", "abc.def",
//                       "abc[0].def", "abc[0].def[1]".
//
// Set relationship graph (A -> B: A is a subset of B):
//
// property_path -> property_path_expr
//      ^                   ^
//      |                   |
// property_name -> property_name_expr
inline constexpr std::string_view kPropertyPathSeparator = ".";
inline constexpr std::string_view kLBracket = "[";
inline constexpr std::string_view kRBracket = "]";

inline constexpr int kWildcardPropertyIndex = -1;

struct PropertyInfo {
  std::string name;
  int index;

  explicit PropertyInfo(std::string name_in, int index_in)
      : name(std::move(name_in)), index(index_in) {}
};

// Converts a property (value) index to string, wrapped by kLBracket and
// kRBracket.
//
// REQUIRES: index should be valid or kWildcardPropertyIndex.
//
// Returns:
//   - "" if index is kWildcardPropertyIndex.
//   - kLBracket + std::to_string(index) + kRBracket for all non
//     kWildcardPropertyIndex indices.
std::string ConvertToPropertyExprIndexStr(int index);

// Concatenates 2 property path expressions.
//
// Returns:
//   - property_path_expr1 + "." + property_path_expr2 if both are not empty.
//   - property_path_expr1 if property_path_expr2 is empty.
//   - property_path_expr2 if property_path_expr1 is empty.
//   - "" if both are empty.
std::string ConcatenatePropertyPathExpr(std::string_view property_path_expr1,
                                        std::string_view property_path_expr2);

// Splits a property path expression into multiple property name expressions.
//
// Returns: a vector of property name expressions.
std::vector<std::string_view> SplitPropertyPathExpr(
    std::string_view property_path_expr);

// Parses a property name expression into (property name, property index). If
// the index expression is missing, then the returned property index will be
// kWildcardPropertyIndex.
//
// Examples:
//   - ParsePropertyNameExpr("foo") will return ("foo",
//     kWildcardPropertyIndex).
//   - ParsePropertyNameExpr("foo[5]") will return ("foo", 5).
//
// Returns: a PropertyInfo instance.
PropertyInfo ParsePropertyNameExpr(std::string_view property_name_expr);

// Parses a property path expression into multiple (property name, property
// index). It is similar to ParsePropertyPathExpr, except property path
// expression can contain multiple name expressions.
//
// Examples:
//   - ParsePropertyPathExpr("foo") will return [("foo",
//     kWildcardPropertyIndex)].
//   - ParsePropertyPathExpr("foo[5]") will return [("foo", 5)].
//   - ParsePropertyPathExpr("foo.bar[2]") will return [("foo",
//     kWildcardPropertyIndex), ("bar", 2)]
//
// Returns: a vector of PropertyInfo instances.
std::vector<PropertyInfo> ParsePropertyPathExpr(
    std::string_view property_path_expr);

// A property path property_path_expr1 is considered a parent of another
// property path property_path_expr2 if:
// 1. property_path_expr2 == property_path_expr1, OR
// 2. property_path_expr2 consists of the entire path of property_path_expr1
//    + "." + [some other property path].
//
// Note that this can only be used for property name strings that do not
// contain the property index.
//
// Examples:
//   - IsParentPropertyPath("foo", "foo") will return true.
//   - IsParentPropertyPath("foo", "foo.bar") will return true.
//   - IsParentPropertyPath("foo", "bar.foo") will return false.
//   - IsParentPropertyPath("foo.bar", "foo.foo.bar") will return false.
//
// Returns: true if property_path_expr1 is a parent property path of
// property_path_expr2.
bool IsParentPropertyPath(std::string_view property_path_expr1,
                          std::string_view property_path_expr2);

// Gets the desired PropertyProto from the document by given property name.
// Since the input parameter is property name, this function only deals with
// the first level of properties in the document and cannot deal with nested
// documents.
//
// Returns:
//   - const PropertyInfo* if property name exists in the document.
//   - nullptr if property name not found.
const PropertyProto* GetPropertyProto(const DocumentProto& document,
                                      std::string_view property_name);

template <typename T>
libtextclassifier3::StatusOr<std::vector<T>> ExtractPropertyValues(
    const PropertyProto& property) {
  return absl_ports::UnimplementedError(
      "Unimplemented template type for ExtractPropertyValues");
}

template <>
libtextclassifier3::StatusOr<std::vector<std::string>>
ExtractPropertyValues<std::string>(const PropertyProto& property);

template <>
libtextclassifier3::StatusOr<std::vector<std::string_view>>
ExtractPropertyValues<std::string_view>(const PropertyProto& property);

template <>
libtextclassifier3::StatusOr<std::vector<int64_t>>
ExtractPropertyValues<int64_t>(const PropertyProto& property);

template <typename T>
libtextclassifier3::StatusOr<std::vector<T>> ExtractPropertyValuesFromDocument(
    const DocumentProto& document, std::string_view property_path) {
  // Finds the first property name in property_path
  size_t separator_position = property_path.find(kPropertyPathSeparator);
  std::string_view current_property_name =
      (separator_position == std::string::npos)
          ? property_path
          : property_path.substr(0, separator_position);

  const PropertyProto* property_proto =
      GetPropertyProto(document, current_property_name);
  if (property_proto == nullptr) {
    // Property name not found, it could be one of the following 2 cases:
    // 1. The property is optional and it's not in the document
    // 2. The property name is invalid
    return std::vector<T>();
  }

  if (separator_position == std::string::npos) {
    // Current property name is the last one in property path.
    return ExtractPropertyValues<T>(*property_proto);
  }

  // Extracts property values recursively
  std::string_view sub_property_path =
      property_path.substr(separator_position + 1);
  std::vector<T> nested_document_content;
  for (const DocumentProto& nested_document :
       property_proto->document_values()) {
    auto content_or = ExtractPropertyValuesFromDocument<T>(nested_document,
                                                           sub_property_path);
    if (content_or.ok()) {
      std::vector<T> content = std::move(content_or).ValueOrDie();
      std::move(content.begin(), content.end(),
                std::back_inserter(nested_document_content));
    }
  }
  return nested_document_content;
}

}  // namespace property_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_PROPERTY_UTIL_H_
