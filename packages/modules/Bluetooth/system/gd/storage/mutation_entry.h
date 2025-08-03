/*
 * Copyright 2020 The Android Open Source Project
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
#pragma once

#include <string>
#include <type_traits>

#include "common/strings.h"
#include "common/type_helper.h"
#include "storage/serializable.h"

namespace bluetooth {
namespace storage {

class MutationEntry {
 public:
  enum EntryType { SET, REMOVE_PROPERTY, REMOVE_SECTION };

  enum PropertyType { NORMAL, MEMORY_ONLY };

  template <typename T, typename std::enable_if<std::is_integral_v<T>, int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, T value_param) {
    return MutationEntry::Set(
        property_type, std::move(section_param), std::move(property_param), std::to_string(value_param));
  }

  template <typename T, typename std::enable_if<std::is_enum_v<T>, int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, T value_param) {
    using EnumUnderlyingType = typename std::underlying_type_t<T>;
    return MutationEntry::Set<EnumUnderlyingType>(
        property_type,
        std::move(section_param),
        std::move(property_param),
        static_cast<EnumUnderlyingType>(value_param));
  }

  template <typename T, typename std::enable_if<std::is_same_v<T, bool>, int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, T value_param) {
    return MutationEntry::Set(
        property_type, std::move(section_param), std::move(property_param), common::ToString(value_param));
  }

  template <typename T, typename std::enable_if<std::is_same_v<T, std::string>, int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, T value_param) {
    return MutationEntry::Set(
        property_type, std::move(section_param), std::move(property_param), std::move(value_param));
  }

  template <typename T, typename std::enable_if<std::is_base_of_v<Serializable<T>, T>, int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, const T& value_param) {
    return MutationEntry::Set(
        property_type, std::move(section_param), std::move(property_param), value_param.ToLegacyConfigString());
  }

  template <
      typename T,
      typename std::enable_if<
          bluetooth::common::is_specialization_of<T, std::vector>::value &&
              std::is_base_of_v<Serializable<typename T::value_type>, typename T::value_type>,
          int>::type = 0>
  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, const T& value_param) {
    std::vector<std::string> str_values;
    str_values.reserve(value_param.size());
    for (const auto& v : value_param) {
      str_values.push_back(v.ToLegacyConfigString());
    }
    return MutationEntry::Set(
        property_type, std::move(section_param), std::move(property_param), common::StringJoin(str_values, " "));
  }

  static MutationEntry Set(
      PropertyType property_type, std::string section_param, std::string property_param, std::string value_param) {
    return MutationEntry(
        EntryType::SET, property_type, std::move(section_param), std::move(property_param), std::move(value_param));
  }

  static MutationEntry Remove(PropertyType property_type, std::string section_param) {
    return MutationEntry(EntryType::REMOVE_SECTION, property_type, std::move(section_param));
  }

  static MutationEntry Remove(PropertyType property_type, std::string section_param, std::string property_param) {
    return MutationEntry(
        EntryType::REMOVE_PROPERTY, property_type, std::move(section_param), std::move(property_param));
  }

 private:
  friend class ConfigCache;
  friend class Mutation;

  MutationEntry(
      EntryType entry_type_param,
      PropertyType property_type_param,
      std::string section_param,
      std::string property_param = "",
      std::string value_param = "");

  EntryType entry_type;
  PropertyType property_type;
  std::string section;
  std::string property;
  std::string value;
};

}  // namespace storage
}  // namespace bluetooth