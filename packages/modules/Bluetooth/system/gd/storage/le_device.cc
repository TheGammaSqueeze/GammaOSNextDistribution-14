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

#include "storage/le_device.h"

namespace bluetooth {
namespace storage {

namespace {
const std::string kLeIdentityAddressKey = "LeIdentityAddr";
// TODO(siyuanh): check if we still need these keys in GD
// const std::string kLePencKey = "LE_KEY_PENC";
// const std::string kLePidKey = "LE_KEY_PENC";
// const std::string kLePsrkKey = "LE_KEY_PENC";
// const std::string kLeLencKey = "LE_KEY_PENC";
// const std::string kLeLcsrkKey = "LE_KEY_PENC";
// const std::string kLeLidKey = "LE_KEY_PENC";
}  // namespace

const std::unordered_set<std::string_view> LeDevice::kLinkKeyProperties = {
    "LE_KEY_PENC", "LE_KEY_PID", "LE_KEY_PCSRK", "LE_KEY_LENC", "LE_KEY_LCSRK"};

LeDevice::LeDevice(ConfigCache* config, ConfigCache* memory_only_config, std::string section)
    : config_(config), memory_only_config_(memory_only_config), section_(std::move(section)) {}

Device LeDevice::Parent() {
  return Device(config_, memory_only_config_, section_);
}

std::string LeDevice::ToLogString() const {
  return section_;
}

bool LeDevice::IsPaired() const {
  // This first check is here only to speed up the checking process
  if (!config_->IsPersistentSection(section_)) {
    return false;
  }
  return config_->HasAtLeastOneMatchingPropertiesInSection(section_, kLinkKeyProperties);
}

}  // namespace storage
}  // namespace bluetooth