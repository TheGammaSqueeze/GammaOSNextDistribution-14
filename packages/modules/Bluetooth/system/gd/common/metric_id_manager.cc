/******************************************************************************
 *
 *  Copyright 2020 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#define LOG_TAG "BluetoothMetricIdManager"

#include <functional>
#include <iterator>
#include <mutex>
#include <optional>
#include <thread>

#include "os/log.h"
#include "common/metric_id_manager.h"

namespace bluetooth {
namespace common {

using hci::Address;

const size_t MetricIdManager::kMaxNumUnpairedDevicesInMemory = 200;
const size_t MetricIdManager::kMaxNumPairedDevicesInMemory = 65000;
const int MetricIdManager::kMinId = 1;
const int MetricIdManager::kMaxId = 65534;  // 2^16 - 2

// id space should always be larger than kMaxNumPairedDevicesInMemory +
// kMaxNumUnpairedDevicesInMemory
static_assert((MetricIdManager::kMaxNumUnpairedDevicesInMemory +
               MetricIdManager::kMaxNumPairedDevicesInMemory) <
                  (MetricIdManager::kMaxId - MetricIdManager::kMinId),
              "id space should always be larger than "
              "kMaxNumPairedDevicesInMemory + MaxNumUnpairedDevicesInMemory");

MetricIdManager::MetricIdManager()
    : paired_device_cache_(kMaxNumPairedDevicesInMemory),
      temporary_device_cache_(kMaxNumUnpairedDevicesInMemory) {}

bool MetricIdManager::Init(
    const std::unordered_map<Address, int>& paired_device_map,
    Callback save_id_callback, Callback forget_device_callback) {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  if (initialized_) {
    return false;
  }

  // init paired_devices_map
  if (paired_device_map.size() > kMaxNumPairedDevicesInMemory) {
    LOG_ALWAYS_FATAL(
        "Paired device map has size %zu, which is bigger than "
        "kMaxNumPairedDevicesInMemory %zu",
        paired_device_map.size(), kMaxNumPairedDevicesInMemory);
    // fail loudly to let caller know
    return false;
  }

  next_id_ = kMinId;
  for (const auto& p : paired_device_map) {
    if (p.second < kMinId || p.second > kMaxId) {
      LOG_ALWAYS_FATAL(
          "Invalid Bluetooth Metric Id in config. "
          "Id %d of %s is out of range [%d, %d]",
          p.second,
          ADDRESS_TO_LOGGABLE_CSTR(p.first),
          kMinId,
          kMaxId);
    }
    auto evicted = paired_device_cache_.insert_or_assign(p.first, p.second);
    if (evicted) {
      ForgetDevicePostprocess(evicted->first, evicted->second);
    }
    id_set_.insert(p.second);
    next_id_ = std::max(next_id_, p.second + 1);
  }
  if (next_id_ > kMaxId) {
    next_id_ = kMinId;
  }

  // init callbacks
  save_id_callback_ = save_id_callback;
  forget_device_callback_ = forget_device_callback;

  return initialized_ = true;
}

MetricIdManager::~MetricIdManager() { Close(); }

bool MetricIdManager::Close() {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  if (!initialized_) {
    return false;
  }
  paired_device_cache_.clear();
  temporary_device_cache_.clear();
  id_set_.clear();
  initialized_ = false;
  return true;
}

MetricIdManager& MetricIdManager::GetInstance() {
  static MetricIdManager metric_id_allocator;
  return metric_id_allocator;
}

bool MetricIdManager::IsEmpty() const {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  return paired_device_cache_.size() == 0 &&
         temporary_device_cache_.size() == 0;
}

// call this function when a new device is scanned
int MetricIdManager::AllocateId(const Address& mac_address) {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  auto it = paired_device_cache_.find(mac_address);
  // if already have an id, return it
  if (it != paired_device_cache_.end()) {
    return it->second;
  }
  it = temporary_device_cache_.find(mac_address);
  if (it != temporary_device_cache_.end()) {
    return it->second;
  }

  // find next available id
  while (id_set_.count(next_id_) > 0) {
    next_id_++;
    if (next_id_ > kMaxId) {
      next_id_ = kMinId;
      LOG_WARN("Bluetooth metric id overflow.");
    }
  }
  int id = next_id_++;
  id_set_.insert(id);
  auto evicted = temporary_device_cache_.insert_or_assign(mac_address, id);
  if (evicted) {
    this->id_set_.extract(evicted->second);
  }

  if (next_id_ > kMaxId) {
    next_id_ = kMinId;
  }
  return id;
}

// call this function when a device is paired
bool MetricIdManager::SaveDevice(const Address& mac_address) {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  if (paired_device_cache_.contains(mac_address)) {
    return true;
  }
  if (!temporary_device_cache_.contains(mac_address)) {
    LOG_ERROR("Failed to save device because device is not in "
              "temporary_device_cache_");
    return false;
  }
  auto opt = temporary_device_cache_.extract(mac_address);
  if (!opt) {
    LOG_ERROR("Failed to remove device from temporary_device_cache_");
    return false;
  }
  int id = opt->second;
  auto evicted = paired_device_cache_.insert_or_assign(mac_address, id);
  if (evicted) {
    ForgetDevicePostprocess(evicted->first, evicted->second);
  }
  if (!save_id_callback_(mac_address, id)) {
    LOG_ERROR("Callback returned false after saving the device");
    return false;
  }
  return true;
}

// call this function when a device is forgotten
void MetricIdManager::ForgetDevice(const Address& mac_address) {
  std::lock_guard<std::mutex> lock(id_allocator_mutex_);
  auto opt = paired_device_cache_.extract(mac_address);
  if (!opt) {
    LOG_ERROR("Failed to remove device from paired_device_cache_");
    return;
  }
  ForgetDevicePostprocess(mac_address, opt->second);
}

bool MetricIdManager::IsValidId(const int id) {
  return id >= kMinId && id <= kMaxId;
}

void MetricIdManager::ForgetDevicePostprocess(const Address& mac_address,
                                                const int id) {
  id_set_.erase(id);
  forget_device_callback_(mac_address, id);
}

}  // namespace common
}  // namespace bluetooth
