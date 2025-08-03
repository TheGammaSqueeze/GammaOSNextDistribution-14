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

#include "icing/schema/property-util.h"

#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/proto/document.pb.h"

namespace icing {
namespace lib {

namespace property_util {

std::string ConvertToPropertyExprIndexStr(int index) {
  if (index == kWildcardPropertyIndex) {
    return "";
  }
  return absl_ports::StrCat(kLBracket, std::to_string(index), kRBracket);
}

std::string ConcatenatePropertyPathExpr(std::string_view property_path_expr1,
                                        std::string_view property_path_expr2) {
  if (property_path_expr1.empty()) {
    return std::string(property_path_expr2);
  }
  if (property_path_expr2.empty()) {
    return std::string(property_path_expr1);
  }
  return absl_ports::StrCat(property_path_expr1, kPropertyPathSeparator,
                            property_path_expr2);
}

std::vector<std::string_view> SplitPropertyPathExpr(
    std::string_view property_path_expr) {
  return absl_ports::StrSplit(property_path_expr, kPropertyPathSeparator);
}

PropertyInfo ParsePropertyNameExpr(std::string_view property_name_expr) {
  size_t l_bracket = property_name_expr.find(kLBracket);
  if (l_bracket == std::string_view::npos ||
      l_bracket >= property_name_expr.length()) {
    return PropertyInfo(std::string(property_name_expr),
                        kWildcardPropertyIndex);
  }
  size_t r_bracket = property_name_expr.find(kRBracket, l_bracket);
  if (r_bracket == std::string_view::npos || r_bracket - l_bracket < 2) {
    return PropertyInfo(std::string(property_name_expr),
                        kWildcardPropertyIndex);
  }
  std::string index_string = std::string(
      property_name_expr.substr(l_bracket + 1, r_bracket - l_bracket - 1));
  return PropertyInfo(std::string(property_name_expr.substr(0, l_bracket)),
                      std::stoi(index_string));
}

std::vector<PropertyInfo> ParsePropertyPathExpr(
    std::string_view property_path_expr) {
  std::vector<std::string_view> property_name_exprs =
      SplitPropertyPathExpr(property_path_expr);

  std::vector<PropertyInfo> property_infos;
  property_infos.reserve(property_name_exprs.size());
  for (std::string_view property_name_expr : property_name_exprs) {
    property_infos.push_back(ParsePropertyNameExpr(property_name_expr));
  }
  return property_infos;
}

bool IsParentPropertyPath(std::string_view property_path_expr1,
                          std::string_view property_path_expr2) {
  if (property_path_expr2.length() < property_path_expr1.length()) {
    return false;
  }
  if (property_path_expr1 !=
      property_path_expr2.substr(0, property_path_expr1.length())) {
    return false;
  }
  if (property_path_expr2.length() > property_path_expr1.length() &&
      property_path_expr2[property_path_expr1.length()] !=
          kPropertyPathSeparator[0]) {
    return false;
  }
  return true;
}

const PropertyProto* GetPropertyProto(const DocumentProto& document,
                                      std::string_view property_name) {
  for (const PropertyProto& property : document.properties()) {
    if (property.name() == property_name) {
      return &property;
    }
  }
  return nullptr;
}

template <>
libtextclassifier3::StatusOr<std::vector<std::string>>
ExtractPropertyValues<std::string>(const PropertyProto& property) {
  return std::vector<std::string>(property.string_values().begin(),
                                  property.string_values().end());
}

template <>
libtextclassifier3::StatusOr<std::vector<std::string_view>>
ExtractPropertyValues<std::string_view>(const PropertyProto& property) {
  return std::vector<std::string_view>(property.string_values().begin(),
                                       property.string_values().end());
}

template <>
libtextclassifier3::StatusOr<std::vector<int64_t>>
ExtractPropertyValues<int64_t>(const PropertyProto& property) {
  return std::vector<int64_t>(property.int64_values().begin(),
                              property.int64_values().end());
}

}  // namespace property_util

}  // namespace lib
}  // namespace icing
