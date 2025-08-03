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

#include "storage/config_cache.h"

#include <ios>
#include <sstream>
#include <utility>

#include "hci/enum_helper.h"
#include "os/parameter_provider.h"
#include "storage/mutation.h"

namespace {

const std::unordered_set<std::string_view> kEncryptKeyNameList = {
    "LinkKey", "LE_KEY_PENC", "LE_KEY_PID", "LE_KEY_LID", "LE_KEY_PCSRK", "LE_KEY_LENC", "LE_KEY_LCSRK"};

bool TrimAfterNewLine(std::string& value) {
  std::string value_no_newline;
  size_t newline_position = value.find_first_of('\n');
  if (newline_position != std::string::npos) {
    value.erase(newline_position);
    return true;
  }
  return false;
}

bool InEncryptKeyNameList(std::string key) {
  return kEncryptKeyNameList.find(key) != kEncryptKeyNameList.end();
}

}  // namespace

namespace bluetooth {
namespace storage {

const std::unordered_set<std::string_view> kLePropertyNames = {
    "LE_KEY_PENC", "LE_KEY_PID", "LE_KEY_PCSRK", "LE_KEY_LENC", "LE_KEY_LCSRK"};

const std::unordered_set<std::string_view> kClassicPropertyNames = {
    "LinkKey", "SdpDiMaufacturer", "SdpDiModel", "SdpDiHardwareVersion", "SdpDiVendorSource"};

const std::string ConfigCache::kDefaultSectionName = "Global";

std::string kEncryptedStr = "encrypted";

ConfigCache::ConfigCache(size_t temp_device_capacity, std::unordered_set<std::string_view> persistent_property_names)
    : persistent_property_names_(std::move(persistent_property_names)),
      information_sections_(),
      persistent_devices_(),
      temporary_devices_(temp_device_capacity) {}

void ConfigCache::SetPersistentConfigChangedCallback(std::function<void()> persistent_config_changed_callback) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  persistent_config_changed_callback_ = std::move(persistent_config_changed_callback);
}

ConfigCache::ConfigCache(ConfigCache&& other) noexcept
    : persistent_config_changed_callback_(nullptr),
      persistent_property_names_(std::move(other.persistent_property_names_)),
      information_sections_(std::move(other.information_sections_)),
      persistent_devices_(std::move(other.persistent_devices_)),
      temporary_devices_(std::move(other.temporary_devices_)) {
  ASSERT_LOG(
      other.persistent_config_changed_callback_ == nullptr,
      "Can't assign after setting the callback");
}

ConfigCache& ConfigCache::operator=(ConfigCache&& other) noexcept {
  if (&other == this) {
    return *this;
  }
  std::lock_guard<std::recursive_mutex> my_lock(mutex_);
  std::lock_guard<std::recursive_mutex> others_lock(other.mutex_);
  ASSERT_LOG(
      other.persistent_config_changed_callback_ == nullptr,
      "Can't assign after setting the callback");
  persistent_config_changed_callback_ = {};
  persistent_property_names_ = std::move(other.persistent_property_names_);
  information_sections_ = std::move(other.information_sections_);
  persistent_devices_ = std::move(other.persistent_devices_);
  temporary_devices_ = std::move(other.temporary_devices_);
  return *this;
}

bool ConfigCache::operator==(const ConfigCache& rhs) const {
  std::lock_guard<std::recursive_mutex> my_lock(mutex_);
  std::lock_guard<std::recursive_mutex> others_lock(rhs.mutex_);
  return persistent_property_names_ == rhs.persistent_property_names_ &&
         information_sections_ == rhs.information_sections_ && persistent_devices_ == rhs.persistent_devices_ &&
         temporary_devices_ == rhs.temporary_devices_;
}

bool ConfigCache::operator!=(const ConfigCache& rhs) const {
  return !(*this == rhs);
}

void ConfigCache::Clear() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  if (information_sections_.size() > 0) {
    information_sections_.clear();
    PersistentConfigChangedCallback();
  }
  if (persistent_devices_.size() > 0) {
    persistent_devices_.clear();
    PersistentConfigChangedCallback();
  }
  if (temporary_devices_.size() > 0) {
    temporary_devices_.clear();
  }
}

bool ConfigCache::HasSection(const std::string& section) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return information_sections_.contains(section) || persistent_devices_.contains(section) ||
         temporary_devices_.contains(section);
}

bool ConfigCache::HasProperty(const std::string& section, const std::string& property) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  auto section_iter = information_sections_.find(section);
  if (section_iter != information_sections_.end()) {
    return section_iter->second.find(property) != section_iter->second.end();
  }
  section_iter = persistent_devices_.find(section);
  if (section_iter != persistent_devices_.end()) {
    return section_iter->second.find(property) != section_iter->second.end();
  }
  section_iter = temporary_devices_.find(section);
  if (section_iter != temporary_devices_.end()) {
    return section_iter->second.find(property) != section_iter->second.end();
  }
  return false;
}

std::optional<std::string> ConfigCache::GetProperty(const std::string& section, const std::string& property) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  auto section_iter = information_sections_.find(section);
  if (section_iter != information_sections_.end()) {
    auto property_iter = section_iter->second.find(property);
    if (property_iter != section_iter->second.end()) {
      return property_iter->second;
    }
  }
  section_iter = persistent_devices_.find(section);
  if (section_iter != persistent_devices_.end()) {
    auto property_iter = section_iter->second.find(property);
    if (property_iter != section_iter->second.end()) {
      std::string value = property_iter->second;
      if (os::ParameterProvider::GetBtKeystoreInterface() != nullptr && value == kEncryptedStr) {
        return os::ParameterProvider::GetBtKeystoreInterface()->get_key(section + "-" + property);
      }
      return value;
    }
  }
  section_iter = temporary_devices_.find(section);
  if (section_iter != temporary_devices_.end()) {
    auto property_iter = section_iter->second.find(property);
    if (property_iter != section_iter->second.end()) {
      return property_iter->second;
    }
  }
  return std::nullopt;
}

void ConfigCache::SetProperty(std::string section, std::string property, std::string value) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  TrimAfterNewLine(section);
  TrimAfterNewLine(property);
  TrimAfterNewLine(value);
  ASSERT_LOG(!section.empty(), "Empty section name not allowed");
  ASSERT_LOG(!property.empty(), "Empty property name not allowed");
  if (!IsDeviceSection(section)) {
    auto section_iter = information_sections_.find(section);
    if (section_iter == information_sections_.end()) {
      section_iter = information_sections_.try_emplace_back(section, common::ListMap<std::string, std::string>{}).first;
    }
    section_iter->second.insert_or_assign(property, std::move(value));
    PersistentConfigChangedCallback();
    return;
  }
  auto section_iter = persistent_devices_.find(section);
  if (section_iter == persistent_devices_.end() && IsPersistentProperty(property)) {
    // move paired devices or create new paired device when a link key is set
    auto section_properties = temporary_devices_.extract(section);
    if (section_properties) {
      section_iter = persistent_devices_.try_emplace_back(section, std::move(section_properties->second)).first;
    } else {
      section_iter = persistent_devices_.try_emplace_back(section, common::ListMap<std::string, std::string>{}).first;
    }
  }
  if (section_iter != persistent_devices_.end()) {
    bool is_encrypted = value == kEncryptedStr;
    if ((!value.empty()) && os::ParameterProvider::GetBtKeystoreInterface() != nullptr &&
        os::ParameterProvider::IsCommonCriteriaMode() && InEncryptKeyNameList(property) && !is_encrypted) {
      if (os::ParameterProvider::GetBtKeystoreInterface()->set_encrypt_key_or_remove_key(
              section + "-" + property, value)) {
        value = kEncryptedStr;
      }
    }
    section_iter->second.insert_or_assign(property, std::move(value));
    PersistentConfigChangedCallback();
    return;
  }
  section_iter = temporary_devices_.find(section);
  if (section_iter == temporary_devices_.end()) {
    auto triple = temporary_devices_.try_emplace(section, common::ListMap<std::string, std::string>{});
    section_iter = std::get<0>(triple);
  }
  section_iter->second.insert_or_assign(property, std::move(value));
}

bool ConfigCache::RemoveSection(const std::string& section) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  // sections are unique among all three maps, hence removing from one of them is enough
  if (information_sections_.extract(section) || persistent_devices_.extract(section)) {
    PersistentConfigChangedCallback();
    return true;
  } else {
    return temporary_devices_.extract(section).has_value();
  }
}

bool ConfigCache::RemoveProperty(const std::string& section, const std::string& property) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  auto section_iter = information_sections_.find(section);
  if (section_iter != information_sections_.end()) {
    auto value = section_iter->second.extract(property);
    // if section is empty after removal, remove the whole section as empty section is not allowed
    if (section_iter->second.size() == 0) {
      information_sections_.erase(section_iter);
    }
    if (value.has_value()) {
      PersistentConfigChangedCallback();
      return true;
    } else {
      return false;
    }
  }
  section_iter = persistent_devices_.find(section);
  if (section_iter != persistent_devices_.end()) {
    auto value = section_iter->second.extract(property);
    // if section is empty after removal, remove the whole section as empty section is not allowed
    if (section_iter->second.size() == 0) {
      persistent_devices_.erase(section_iter);
    } else if (value && IsPersistentProperty(property)) {
      // move unpaired device
      auto section_properties = persistent_devices_.extract(section);
      temporary_devices_.insert_or_assign(section, std::move(section_properties->second));
    }
    if (value.has_value()) {
      PersistentConfigChangedCallback();
      if (os::ParameterProvider::GetBtKeystoreInterface() != nullptr && os::ParameterProvider::IsCommonCriteriaMode() &&
          InEncryptKeyNameList(property)) {
        os::ParameterProvider::GetBtKeystoreInterface()->set_encrypt_key_or_remove_key(section + "-" + property, "");
      }
      return true;
    } else {
      return false;
    }
  }
  section_iter = temporary_devices_.find(section);
  if (section_iter != temporary_devices_.end()) {
    auto value = section_iter->second.extract(property);
    if (section_iter->second.size() == 0) {
      temporary_devices_.erase(section_iter);
    }
    return value.has_value();
  }
  return false;
}

void ConfigCache::ConvertEncryptOrDecryptKeyIfNeeded() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  LOG_INFO("%s", __func__);
  auto persistent_sections = GetPersistentSections();
  for (const auto& section : persistent_sections) {
    auto section_iter = persistent_devices_.find(section);
    for (const auto& property : kEncryptKeyNameList) {
      auto property_iter = section_iter->second.find(std::string(property));
      if (property_iter != section_iter->second.end()) {
        bool is_encrypted = property_iter->second == kEncryptedStr;
        if ((!property_iter->second.empty()) && os::ParameterProvider::GetBtKeystoreInterface() != nullptr &&
            os::ParameterProvider::IsCommonCriteriaMode() && !is_encrypted) {
          if (os::ParameterProvider::GetBtKeystoreInterface()->set_encrypt_key_or_remove_key(
                  section + "-" + std::string(property), property_iter->second)) {
            SetProperty(section, std::string(property), kEncryptedStr);
          }
        }
        if (os::ParameterProvider::GetBtKeystoreInterface() != nullptr && is_encrypted) {
          std::string value_str =
              os::ParameterProvider::GetBtKeystoreInterface()->get_key(section + "-" + std::string(property));
          if (!os::ParameterProvider::IsCommonCriteriaMode()) {
            SetProperty(section, std::string(property), value_str);
          }
        }
      }
    }
  }
}

bool ConfigCache::IsDeviceSection(const std::string& section) {
  return hci::Address::IsValidAddress(section);
}

bool ConfigCache::IsPersistentProperty(const std::string& property) const {
  return persistent_property_names_.find(property) != persistent_property_names_.end();
}

void ConfigCache::RemoveSectionWithProperty(const std::string& property) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  size_t num_persistent_removed = 0;
  for (auto* config_section : {&information_sections_, &persistent_devices_}) {
    for (auto it = config_section->begin(); it != config_section->end();) {
      if (it->second.contains(property)) {
        LOG_INFO("Removing persistent section %s with property %s", it->first.c_str(), property.c_str());
        it = config_section->erase(it);
        num_persistent_removed++;
        continue;
      }
      it++;
    }
  }
  for (auto it = temporary_devices_.begin(); it != temporary_devices_.end();) {
    if (it->second.contains(property)) {
      LOG_INFO("Removing temporary section %s with property %s", it->first.c_str(), property.c_str());
      it = temporary_devices_.erase(it);
      continue;
    }
    it++;
  }
  if (num_persistent_removed > 0) {
    PersistentConfigChangedCallback();
  }
}

std::vector<std::string> ConfigCache::GetPersistentSections() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  std::vector<std::string> paired_devices;
  paired_devices.reserve(persistent_devices_.size());
  for (const auto& elem : persistent_devices_) {
    paired_devices.emplace_back(elem.first);
  }
  return paired_devices;
}

void ConfigCache::Commit(std::queue<MutationEntry>& mutation_entries) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  while (!mutation_entries.empty()) {
    auto entry = std::move(mutation_entries.front());
    mutation_entries.pop();
    switch (entry.entry_type) {
      case MutationEntry::EntryType::SET:
        SetProperty(std::move(entry.section), std::move(entry.property), std::move(entry.value));
        break;
      case MutationEntry::EntryType::REMOVE_PROPERTY:
        RemoveProperty(entry.section, entry.property);
        break;
      case MutationEntry::EntryType::REMOVE_SECTION:
        RemoveSection(entry.section);
        break;
        // do not write a default case so that when a new enum is defined, compilation would fail automatically
    }
  }
}

std::string ConfigCache::SerializeToLegacyFormat() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  std::stringstream serialized;
  for (const auto* config_section : {&information_sections_, &persistent_devices_}) {
    for (const auto& section : *config_section) {
      serialized << "[" << section.first << "]" << std::endl;
      for (const auto& property : section.second) {
        serialized << property.first << " = " << property.second << std::endl;
      }
      serialized << std::endl;
    }
  }
  return serialized.str();
}

std::vector<ConfigCache::SectionAndPropertyValue> ConfigCache::GetSectionNamesWithProperty(
    const std::string& property) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  std::vector<SectionAndPropertyValue> result;
  for (auto* config_section : {&information_sections_, &persistent_devices_}) {
    for (const auto& elem : *config_section) {
      auto it = elem.second.find(property);
      if (it != elem.second.end()) {
        result.emplace_back(SectionAndPropertyValue{.section = elem.first, .property = it->second});
        continue;
      }
    }
  }
  for (const auto& elem : temporary_devices_) {
    auto it = elem.second.find(property);
    if (it != elem.second.end()) {
      result.emplace_back(SectionAndPropertyValue{.section = elem.first, .property = it->second});
      continue;
    }
  }
  return result;
}

namespace {

bool FixDeviceTypeInconsistencyInSection(
    const std::string& section_name, common::ListMap<std::string, std::string>& device_section_entries) {
  if (!hci::Address::IsValidAddress(section_name)) {
    return false;
  }
  auto device_type_iter = device_section_entries.find("DevType");
  if (device_type_iter != device_section_entries.end() &&
      device_type_iter->second == std::to_string(hci::DeviceType::DUAL)) {
    // We might only have one of classic/LE keys for a dual device, but it is still a dual device,
    // so we should not change the DevType.
    return false;
  }

  // we will ignore the existing DevType, since it is not known to be a DUAL device so
  // the keys we have should be sufficient to infer the correct DevType
  bool is_le = false;
  bool is_classic = false;
  // default
  hci::DeviceType device_type = hci::DeviceType::BR_EDR;
  for (const auto& entry : device_section_entries) {
    if (kLePropertyNames.find(entry.first) != kLePropertyNames.end()) {
      is_le = true;
    }
    if (kClassicPropertyNames.find(entry.first) != kClassicPropertyNames.end()) {
      is_classic = true;
    }
  }
  if (is_classic && is_le) {
    device_type = hci::DeviceType::DUAL;
  } else if (is_classic) {
    device_type = hci::DeviceType::BR_EDR;
  } else if (is_le) {
    device_type = hci::DeviceType::LE;
  }
  bool inconsistent = true;
  std::string device_type_str = std::to_string(device_type);
  if (device_type_iter != device_section_entries.end()) {
    inconsistent = device_type_str != device_type_iter->second;
    if (inconsistent) {
      device_type_iter->second = std::move(device_type_str);
    }
  } else {
    device_section_entries.insert_or_assign("DevType", std::move(device_type_str));
  }
  return inconsistent;
}

}  // namespace

bool ConfigCache::FixDeviceTypeInconsistencies() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  bool persistent_device_changed = false;
  for (auto* config_section : {&information_sections_, &persistent_devices_}) {
    for (auto& elem : *config_section) {
      if (FixDeviceTypeInconsistencyInSection(elem.first, elem.second)) {
        persistent_device_changed = true;
      }
    }
  }
  bool temp_device_changed = false;
  for (auto& elem : temporary_devices_) {
    if (FixDeviceTypeInconsistencyInSection(elem.first, elem.second)) {
      temp_device_changed = true;
    }
  }
  if (persistent_device_changed) {
    PersistentConfigChangedCallback();
  }
  return persistent_device_changed || temp_device_changed;
}

bool ConfigCache::HasAtLeastOneMatchingPropertiesInSection(
    const std::string& section, const std::unordered_set<std::string_view>& property_names) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  const common::ListMap<std::string, std::string>* section_ptr;
  if (!IsDeviceSection(section)) {
    auto section_iter = information_sections_.find(section);
    if (section_iter == information_sections_.end()) {
      return false;
    }
    section_ptr = &section_iter->second;
  } else {
    auto section_iter = persistent_devices_.find(section);
    if (section_iter == persistent_devices_.end()) {
      section_iter = temporary_devices_.find(section);
      if (section_iter == temporary_devices_.end()) {
        return false;
      }
    }
    section_ptr = &section_iter->second;
  }
  for (const auto& property : *section_ptr) {
    if (property_names.count(property.first) > 0) {
      return true;
    }
  }
  return false;
}

bool ConfigCache::IsPersistentSection(const std::string& section) const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return persistent_devices_.contains(section);
}

}  // namespace storage
}  // namespace bluetooth