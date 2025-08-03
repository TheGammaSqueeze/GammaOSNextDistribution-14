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

#include <functional>
#include <list>
#include <mutex>
#include <optional>
#include <queue>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include "common/list_map.h"
#include "common/lru_cache.h"
#include "hci/address.h"
#include "os/utils.h"
#include "storage/mutation_entry.h"

namespace bluetooth {
namespace storage {

class Mutation;

// A memory operated section-key-value structured config
//
// A section can be either persistent or temporary. When a section becomes persistent, all its properties are
// written to disk.
//
// A section becomes persistent when a property that is part of persistent_property_names_ is written to config cache;
// A section becomes temporary when all properties that are part of persistent_property_names_ is removed
//
// The definition of persistent sections is up to the user and is defined through the |persistent_property_names|
// argument. When these properties are link key properties, then persistent sections is equal to bonded devices
//
// This class is thread safe
class ConfigCache {
 public:
  ConfigCache(size_t temp_device_capacity, std::unordered_set<std::string_view> persistent_property_names);

  ConfigCache(const ConfigCache&) = delete;
  ConfigCache& operator=(const ConfigCache&) = delete;

  virtual ~ConfigCache() = default;

  // no copy

  // can move
  ConfigCache(ConfigCache&& other) noexcept;
  ConfigCache& operator=(ConfigCache&& other) noexcept;

  // comparison operators, callback doesn't count
  bool operator==(const ConfigCache& rhs) const;
  bool operator!=(const ConfigCache& rhs) const;

  // observers
  virtual bool HasSection(const std::string& section) const;
  virtual bool HasProperty(const std::string& section, const std::string& property) const;
  // Get property, return std::nullopt if section or property does not exist
  virtual std::optional<std::string> GetProperty(const std::string& section, const std::string& property) const;
  // Returns a copy of persistent device MAC addresses
  virtual std::vector<std::string> GetPersistentSections() const;
  // Return true if a section is persistent
  virtual bool IsPersistentSection(const std::string& section) const;
  // Return true if a section has one of the properties in |property_names|
  virtual bool HasAtLeastOneMatchingPropertiesInSection(
      const std::string& section, const std::unordered_set<std::string_view>& property_names) const;
  // Return true if a property is part of persistent_property_names_
  virtual bool IsPersistentProperty(const std::string& property) const;
  // Serialize to legacy config format
  virtual std::string SerializeToLegacyFormat() const;
  // Return a copy of pair<section_name, property_value> with property
  struct SectionAndPropertyValue {
    std::string section;
    std::string property;
    bool operator==(const SectionAndPropertyValue& rhs) const {
      return section == rhs.section && property == rhs.property;
    }
    bool operator!=(const SectionAndPropertyValue& rhs) const {
      return !(*this == rhs);
    }
  };
  virtual std::vector<SectionAndPropertyValue> GetSectionNamesWithProperty(const std::string& property) const;

  // modifiers
  // Commit all mutation entries in sequence while holding the config mutex
  virtual void Commit(std::queue<MutationEntry>& mutation);
  virtual void SetProperty(std::string section, std::string property, std::string value);
  virtual bool RemoveSection(const std::string& section);
  virtual bool RemoveProperty(const std::string& section, const std::string& property);
  virtual void ConvertEncryptOrDecryptKeyIfNeeded();
  // TODO: have a systematic way of doing this instead of specialized methods
  // Remove sections with |property| set
  virtual void RemoveSectionWithProperty(const std::string& property);
  // remove all content in this config cache, restore it to the state after the explicit constructor
  virtual void Clear();
  // Set a callback to notify interested party that a persistent config change has just happened
  virtual void SetPersistentConfigChangedCallback(std::function<void()> persistent_config_changed_callback);

  // Device config specific methods
  // TODO: methods here should be moved to a device specific config cache if this config cache is supposed to be generic
  // Legacy stack has device type inconsistencies, this method is trying to fix it
  virtual bool FixDeviceTypeInconsistencies();

  // static methods
  // Check if section is formatted as a MAC address
  static bool IsDeviceSection(const std::string& section);

  // constants
  static const std::string kDefaultSectionName;

 private:
  mutable std::recursive_mutex mutex_;
  // A callback to notify interested party that a persistent config change has just happened, empty by default
  std::function<void()> persistent_config_changed_callback_;
  // A set of property names that if set would make a section persistent and if non of these properties are set, a
  // section would become temporary again
  std::unordered_set<std::string_view> persistent_property_names_;
  // Common section that does not relate to remote device, will be written to disk
  common::ListMap<std::string, common::ListMap<std::string, std::string>> information_sections_;
  // Information about persistent devices, normally paired, will be written to disk
  common::ListMap<std::string, common::ListMap<std::string, std::string>> persistent_devices_;
  // Information about temporary devices, normally unpaired, will not be written to disk, will be evicted automatically
  // if capacity exceeds given value during initialization
  common::LruCache<std::string, common::ListMap<std::string, std::string>> temporary_devices_;

  // Convenience method to check if the callback is valid before calling it
  inline void PersistentConfigChangedCallback() const {
    if (persistent_config_changed_callback_) {
      persistent_config_changed_callback_();
    }
  }
};

}  // namespace storage
}  // namespace bluetooth
