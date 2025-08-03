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

#pragma once

#include <functional>
#include <unordered_map>
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {
using CallbackLegacy =
    std::function<bool(const RawAddress& address, const int id)>;
/**
 * Initialize the allocator
 *
 * @param paired_device_map map from mac_address to id already saved
 * in the disk before init
 * @param save_id_callback a callback that will be called after successfully
 * saving id for a paired device
 * @param forget_device_callback a callback that will be called after
 * successful id deletion for forgotten device,
 * @return true if successfully initialized
 */
bool InitMetricIdAllocator(
    const std::unordered_map<RawAddress, int>& paired_device_map,
    CallbackLegacy save_id_callback, CallbackLegacy forget_device_callback);

/**
 * Close the allocator. should be called when Bluetooth process is killed
 *
 * @return true if successfully close
 */
bool CloseMetricIdAllocator();

/**
 * Check if no id saved in memory
 *
 * @return true if no id is saved
 */
bool IsEmptyMetricIdAllocator();

/**
 * Allocate an id for a scanned device, or return the id if there is already
 * one
 *
 * @param raw_address mac address of Bluetooth device
 * @return the id of device
 */
int AllocateIdFromMetricIdAllocator(const RawAddress& raw_address);

/**
 * Save the id for a paired device
 *
 * @param raw_address mac address of Bluetooth device
 * @return true if save successfully
 */
bool SaveDeviceOnMetricIdAllocator(const RawAddress& raw_address);

/**
 * Delete the id for a device to be forgotten
 *
 * @param raw_address mac address of Bluetooth device
 */
void ForgetDeviceFromMetricIdAllocator(const RawAddress& raw_address);

/**
 * Check if an id is valid.
 * The id should be less than or equal to kMaxId and bigger than or equal to
 * kMinId
 *
 * @param mac_address mac address of Bluetooth device
 * @return true if delete successfully
 */
bool IsValidIdFromMetricIdAllocator(const int id);

}  // namespace shim
}  // namespace bluetooth
