/*
 * Copyright 2021 The Android Open Source Project
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
#include "main/shim/metric_id_api.h"

#include "common/metric_id_manager.h"
#include "hci/address.h"
#include "main/shim/helpers.h"
#include "types/raw_address.h"

using bluetooth::common::MetricIdManager;
using bluetooth::hci::Address;

namespace bluetooth {
namespace shim {
using CallbackGd = std::function<bool(const Address& address, const int id)>;

bool InitMetricIdAllocator(
    const std::unordered_map<RawAddress, int>& paired_device_map,
    CallbackLegacy save_id_callback, CallbackLegacy forget_device_callback) {
  std::unordered_map<Address, int> paired_device_map_gd;
  for (const auto& device : paired_device_map) {
    Address address = bluetooth::ToGdAddress(device.first);
    paired_device_map_gd[address] = device.second;
  }

  CallbackGd save_id_callback_gd = [save_id_callback](const Address& address,
                                                      const int id) {
    return save_id_callback(bluetooth::ToRawAddress(address), id);
  };
  CallbackGd forget_device_callback_gd =
      [forget_device_callback](const Address& address, const int id) {
        return forget_device_callback(bluetooth::ToRawAddress(address), id);
      };
  return MetricIdManager::GetInstance().Init(
      paired_device_map_gd, save_id_callback_gd, forget_device_callback_gd);
}

bool CloseMetricIdAllocator() { return MetricIdManager::GetInstance().Close(); }

bool IsEmptyMetricIdAllocator() {
  return MetricIdManager::GetInstance().IsEmpty();
}

int AllocateIdFromMetricIdAllocator(const RawAddress& raw_address) {
  Address address = bluetooth::ToGdAddress(raw_address);
  return MetricIdManager::GetInstance().AllocateId(address);
}

bool SaveDeviceOnMetricIdAllocator(const RawAddress& raw_address) {
  Address address = bluetooth::ToGdAddress(raw_address);
  return MetricIdManager::GetInstance().SaveDevice(address);
}

void ForgetDeviceFromMetricIdAllocator(const RawAddress& raw_address) {
  Address address = bluetooth::ToGdAddress(raw_address);
  return MetricIdManager::GetInstance().ForgetDevice(address);
}

bool IsValidIdFromMetricIdAllocator(const int id) {
  return MetricIdManager::GetInstance().IsValidId(id);
}
}  // namespace shim
}  // namespace bluetooth
