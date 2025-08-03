/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "TypeChecker.h"

#include <algorithm>
#include <string>
#include <unordered_map>

#include <android-base/strings.h>

#include "Common.h"

using android::base::Result;
using android::properties::ParsePropertyInfoFile;
using android::properties::PropertyInfoEntry;

namespace {
std::string SyspropTypeToContextType(const sysprop::Property& prop) {
  switch (prop.type()) {
    case sysprop::Integer:
    case sysprop::Long:
      return "int";
    case sysprop::UInt:
    case sysprop::ULong:
      return "uint";
    case sysprop::Double:
      return "double";
    case sysprop::Boolean:
      return "bool";
    case sysprop::Enum: {
      // Sort both values and then join
      auto prop_values = android::base::Split(prop.enum_values(), "|");
      std::sort(prop_values.begin(), prop_values.end());
      return "enum " + android::base::Join(prop_values, " ");
    }
    default:
      // All other types (string, all kinds of list) should fall here
      return "string";
  }
}

bool IsCompatible(const sysprop::Property& prop, const std::string& ctx_type) {
  if (prop.type() == sysprop::Enum) {
    // special case: we need to first sort values and then compare
    auto prop_values = ParseEnumValues(prop.enum_values());
    std::sort(prop_values.begin(), prop_values.end());

    // ctx_type must be "enum [value1] [value2] ..."
    auto ctx_values = android::base::Split(ctx_type, " ");
    if (ctx_values.empty() || ctx_values[0] != "enum") {
      return false;
    }
    ctx_values.erase(ctx_values.begin());
    std::sort(ctx_values.begin(), ctx_values.end());

    return prop_values == ctx_values;
  }

  return SyspropTypeToContextType(prop) == ctx_type;
}

std::string GetTypeName(const sysprop::Property& prop) {
  if (prop.type() == sysprop::Enum) {
    return "Enum " + prop.enum_values();
  }

  return sysprop::Type_Name(prop.type());
}
}  // namespace

Result<void> CheckPropertyTypes(const sysprop::SyspropLibraryApis& api,
                                const std::vector<PropertyInfoEntry>& entries) {
  std::string err;

  // map from exact property names to types in property_contexts
  std::unordered_map<std::string, std::string> types;
  for (auto& entry : entries) {
    // skip prefix entries.
    if (!entry.exact_match) continue;

    // Duplicated prop check is intentionally skipped.
    // Build will fail if any duplication happens.
    types.emplace(entry.name, entry.type);
  }

  for (auto& props : api.props()) {
    for (auto& prop : props.prop()) {
      std::vector<std::string> prop_names{prop.prop_name()};
      std::string legacy_name = prop.legacy_prop_name();
      if (!legacy_name.empty()) prop_names.push_back(legacy_name);

      for (auto& prop_name : prop_names) {
        // Skip check if there is no exactly matched property.
        auto itr = types.find(prop_name);
        if (itr == types.end()) continue;

        if (!IsCompatible(prop, itr->second)) {
          if (!err.empty()) err += "\n";
          err += "Type of prop '" + prop_name +
                 "' is incompatible with property_contexts\n";
          err += "In sysprop_library: " + GetTypeName(prop) + "\n";
          err += "In property_contexts: " + itr->second + " (should be '" +
                 SyspropTypeToContextType(prop) + "')\n";
        }
      }
    }
  }

  if (err.empty())
    return {};
  else
    return Errorf("{}", err);
}
