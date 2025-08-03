/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <pixelhealth/StatsHelper.h>

#include "pixelatoms_defs.h"

#define LOG_TAG "pixelhealth-vendor"

#include <utils/Log.h>

namespace hardware {
namespace google {
namespace pixel {
namespace health {

using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;

namespace PixelAtoms = hardware::google::pixel::PixelAtoms;

std::shared_ptr<IStats> getStatsService() {
    const std::string instance = std::string() + IStats::descriptor + "/default";
    static bool isStatsDeclared = false;
    if (!isStatsDeclared) {
        // It is good to cache the result - it would not be changed
        isStatsDeclared = AServiceManager_isDeclared(instance.c_str());
        if (!isStatsDeclared) {
            LOG(ERROR) << "Stats service is not registered.";
            return nullptr;
        }
    }
    /* TODO stayfan@: b/187221893 Review implementing separate thread to log atoms
     * to prevent data loss at device boot stage, while IStats might not be ready
     */
    return IStats::fromBinder(ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));
}

void reportBatteryHealthSnapshot(const std::shared_ptr<IStats> &stats_client, int32_t type,
                                 int32_t temperature_deci_celsius, int32_t voltage_micro_volt,
                                 int32_t current_micro_amps, int32_t open_circuit_micro_volt,
                                 int32_t resistance_micro_ohm, int32_t level_percent) {
    // Load values array
    std::vector<VendorAtomValue> values(7);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(type);
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(temperature_deci_celsius);
    values[1] = tmp;
    tmp.set<VendorAtomValue::intValue>(voltage_micro_volt);
    values[2] = tmp;
    tmp.set<VendorAtomValue::intValue>(current_micro_amps);
    values[3] = tmp;
    tmp.set<VendorAtomValue::intValue>(open_circuit_micro_volt);
    values[4] = tmp;
    tmp.set<VendorAtomValue::intValue>(resistance_micro_ohm);
    values[5] = tmp;
    tmp.set<VendorAtomValue::intValue>(level_percent);
    values[6] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.atomId = PixelAtoms::VENDOR_BATTERY_HEALTH_SNAPSHOT,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        LOG(ERROR) << "Unable to report VendorBatteryHealthSnapshot to IStats service";
}

void reportBatteryCausedShutdown(const std::shared_ptr<IStats> &stats_client,
                                 int32_t last_recorded_micro_volt) {
    // Load values array
    std::vector<VendorAtomValue> values(1);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(last_recorded_micro_volt);
    values[0] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.atomId = PixelAtoms::VENDOR_BATTERY_CAUSED_SHUTDOWN,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        LOG(ERROR) << "Unable to report VendorBatteryHealthSnapshot to IStats service";
}

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware
