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

#include <cstdint>
#include <limits>
#include <optional>
#include <type_traits>

#include "common/numbers.h"
#include "common/strings.h"
#include "common/type_helper.h"
#include "hci/enum_helper.h"
#include "storage/config_cache.h"
#include "storage/mutation_entry.h"
#include "storage/serializable.h"

namespace bluetooth {
namespace storage {

// A thin wrapper around ConfigCache and implement more type supports other than std::string
//
// - all SetX methods accept value as copy and std::move() in encouraged
// - all GetX methods return std::optional<X> and std::nullopt if not exist. std::optional<> can be treated as bool
class ConfigCacheHelper {
 public:
  static ConfigCacheHelper FromConfigCache(ConfigCache& config_cache) {
    return ConfigCacheHelper(config_cache);
  }
  explicit ConfigCacheHelper(ConfigCache& config_cache) : config_cache_(config_cache) {}
  virtual ~ConfigCacheHelper() = default;
  virtual void SetBool(const std::string& section, const std::string& property, bool value);
  virtual std::optional<bool> GetBool(const std::string& section, const std::string& property) const;
  virtual void SetUint64(const std::string& section, const std::string& property, uint64_t value);
  virtual std::optional<uint64_t> GetUint64(const std::string& section, const std::string& property) const;
  virtual void SetUint32(const std::string& section, const std::string& property, uint32_t value);
  virtual std::optional<uint32_t> GetUint32(const std::string& section, const std::string& property) const;
  virtual void SetInt64(const std::string& section, const std::string& property, int64_t value);
  virtual std::optional<int64_t> GetInt64(const std::string& section, const std::string& property) const;
  virtual void SetInt(const std::string& section, const std::string& property, int value);
  virtual std::optional<int> GetInt(const std::string& section, const std::string& property) const;
  virtual void SetBin(const std::string& section, const std::string& property, const std::vector<uint8_t>& value);
  virtual std::optional<std::vector<uint8_t>> GetBin(const std::string& section, const std::string& property) const;

  template <typename T, typename std::enable_if<std::is_signed_v<T> && std::is_integral_v<T>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    auto value = GetInt64(section, property);
    if (!value) {
      return std::nullopt;
    }
    if (!common::IsNumberInNumericLimits<T>(*value)) {
      return std::nullopt;
    }
    return static_cast<T>(*value);
  }

  template <typename T, typename std::enable_if<std::is_unsigned_v<T> && std::is_integral_v<T>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    auto value = GetUint64(section, property);
    if (!value) {
      return std::nullopt;
    }
    if (!common::IsNumberInNumericLimits<T>(*value)) {
      return std::nullopt;
    }
    return static_cast<T>(*value);
  }

  template <typename T, typename std::enable_if<std::is_same_v<T, std::string>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    return config_cache_.GetProperty(section, property);
  }

  template <typename T, typename std::enable_if<std::is_same_v<T, std::vector<uint8_t>>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    return GetBin(section, property);
  }

  template <typename T, typename std::enable_if<std::is_same_v<T, bool>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    return GetBool(section, property);
  }

  template <typename T, typename std::enable_if<std::is_base_of_v<Serializable<T>, T>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    auto value = config_cache_.GetProperty(section, property);
    if (!value) {
      return std::nullopt;
    }
    return T::FromLegacyConfigString(*value);
  }

  template <typename T, typename std::enable_if<std::is_enum_v<T>, int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    auto value = config_cache_.GetProperty(section, property);
    if (!value) {
      return std::nullopt;
    }
    return bluetooth::FromLegacyConfigString<T>(*value);
  }

  template <
      typename T,
      typename std::enable_if<
          bluetooth::common::is_specialization_of<T, std::vector>::value &&
              std::is_base_of_v<Serializable<typename T::value_type>, typename T::value_type>,
          int>::type = 0>
  std::optional<T> Get(const std::string& section, const std::string& property) {
    auto value = config_cache_.GetProperty(section, property);
    if (!value) {
      return std::nullopt;
    }
    auto values = common::StringSplit(*value, " ");
    T result;
    result.reserve(values.size());
    for (const auto& str : values) {
      auto v = T::value_type::FromLegacyConfigString(str);
      if (!v) {
        return std::nullopt;
      }
      result.push_back(*v);
    }
    return result;
  }

 private:
  ConfigCache& config_cache_;
};

}  // namespace storage
}  // namespace bluetooth
