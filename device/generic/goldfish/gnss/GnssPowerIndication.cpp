/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <utils/SystemClock.h>
#include <aidl/android/hardware/gnss/IGnss.h>

#include "GnssPowerIndication.h"
#include "debug.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

GnssPowerIndication::GnssPowerIndication(std::function<double()> getRunningTime)
        : mGetRunningTime(std::move(getRunningTime)) {}

ndk::ScopedAStatus GnssPowerIndication::setCallback(
        const std::shared_ptr<IGnssPowerIndicationCallback>& callback) {
    mCb = callback;

    callback->setCapabilitiesCb(IGnssPowerIndicationCallback::CAPABILITY_TOTAL |
                                IGnssPowerIndicationCallback::CAPABILITY_SINGLEBAND_TRACKING |
                                IGnssPowerIndicationCallback::CAPABILITY_MULTIBAND_TRACKING |
                                IGnssPowerIndicationCallback::CAPABILITY_SINGLEBAND_ACQUISITION |
                                IGnssPowerIndicationCallback::CAPABILITY_MULTIBAND_ACQUISITION |
                                IGnssPowerIndicationCallback::CAPABILITY_OTHER_MODES);

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssPowerIndication::requestGnssPowerStats() {
    if (mCb) {
        return doRequestGnssPowerStats(*mCb);
    } else {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }
}

ndk::ScopedAStatus GnssPowerIndication::doRequestGnssPowerStats(
        IGnssPowerIndicationCallback& cb) {
    GnssPowerStats gnssPowerStats;

    const double d = mGetRunningTime();

    if (d > 0.0) {
        ElapsedRealtime elapsedRealtime = {
                .flags = ElapsedRealtime::HAS_TIMESTAMP_NS |
                         ElapsedRealtime::HAS_TIME_UNCERTAINTY_NS,
                .timestampNs = ::android::elapsedRealtimeNano(),
                .timeUncertaintyNs = 1000,
        };

        gnssPowerStats.elapsedRealtime = elapsedRealtime;
        gnssPowerStats.totalEnergyMilliJoule = 1.500e+3 + d * 22.0;
        gnssPowerStats.singlebandTrackingModeEnergyMilliJoule = 0.0;
        gnssPowerStats.multibandTrackingModeEnergyMilliJoule = 1.28e+2 + d * 4.0;
        gnssPowerStats.singlebandAcquisitionModeEnergyMilliJoule = 0.0;
        gnssPowerStats.multibandAcquisitionModeEnergyMilliJoule = 3.65e+2 + d * 15.0;
        gnssPowerStats.otherModesEnergyMilliJoule = {1.232e+2, 3.234e+3};
    }

    cb.gnssPowerStatsCb(gnssPowerStats);

    return ndk::ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
