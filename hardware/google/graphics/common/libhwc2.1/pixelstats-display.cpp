/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "pixelstats-display.h"
#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include "pixelatoms_defs.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <sys/types.h>
#include <utils/Errors.h>

namespace PixelAtoms = hardware::google::pixel::PixelAtoms;

std::shared_ptr<IStats> getStatsService() {
    const std::string instance = std::string() + IStats::descriptor + "/default";
    static std::mutex mutex;
    static bool isStatsDeclared = false;

    const std::lock_guard<std::mutex> lock(mutex);
    if (!isStatsDeclared) {
        isStatsDeclared = AServiceManager_isDeclared(instance.c_str());
        if (!isStatsDeclared) {
            LOG(ERROR) << "Stats service is not registered.";
            return nullptr;
        }
    }

    return IStats::fromBinder(ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));
}

void reportDisplayPortUsage(uint32_t width, uint32_t height, float refreshRate, uint32_t vendorId,
                            uint32_t productId, bool enabled) {
    const std::shared_ptr<IStats> stats_client = getStatsService();
    if (!stats_client) {
        LOG(ERROR) << "Unable to get AIDL Stats service";
        return;
    }

    std::vector<VendorAtomValue> values;
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(width);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::intValue>(height);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::floatValue>(refreshRate);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::intValue>(vendorId);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::intValue>(productId);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::boolValue>(enabled);
    values.push_back(tmp);

    VendorAtom event = {.atomId = PixelAtoms::DISPLAY_PORT_USAGE, .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) LOG(ERROR) << "Unable to report DisplayPortUsage to IStats service";
}
