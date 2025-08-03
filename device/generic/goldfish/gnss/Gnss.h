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

#pragma once
#include <chrono>
#include <memory>
#include <mutex>
#include <aidl/android/hardware/gnss/BnGnss.h>
#include "GnssBatching.h"
#include "GnssConfiguration.h"
#include "GnssHwConn.h"
#include "IDataSink.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

using ::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsInterface;
using ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControl;

struct Gnss : public BnGnss, public IDataSink {
    Gnss();
    ~Gnss();

    ndk::ScopedAStatus setCallback(const std::shared_ptr<IGnssCallback>& callback) override;
    ndk::ScopedAStatus close() override;

    ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<IGnssPsds>* iGnssPsds) override;
    ndk::ScopedAStatus getExtensionGnssConfiguration(
            std::shared_ptr<IGnssConfiguration>* iGnssConfiguration) override;
    ndk::ScopedAStatus getExtensionGnssMeasurement(
            std::shared_ptr<IGnssMeasurementInterface>* iGnssMeasurement) override;
    ndk::ScopedAStatus getExtensionGnssPowerIndication(
            std::shared_ptr<IGnssPowerIndication>* iGnssPowerIndication) override;
    ndk::ScopedAStatus getExtensionGnssBatching(
            std::shared_ptr<IGnssBatching>* iGnssBatching) override;
    ndk::ScopedAStatus getExtensionGnssGeofence(
            std::shared_ptr<IGnssGeofence>* iGnssGeofence) override;
    ndk::ScopedAStatus getExtensionGnssNavigationMessage(
            std::shared_ptr<IGnssNavigationMessageInterface>* iGnssNavigationMessage) override;
    ndk::ScopedAStatus getExtensionAGnss(std::shared_ptr<IAGnss>* iAGnss) override;
    ndk::ScopedAStatus getExtensionAGnssRil(std::shared_ptr<IAGnssRil>* iAGnssRil) override;
    ndk::ScopedAStatus getExtensionGnssDebug(std::shared_ptr<IGnssDebug>* iGnssDebug) override;
    ndk::ScopedAStatus getExtensionGnssVisibilityControl(
            std::shared_ptr<IGnssVisibilityControl>* iGnssVisibilityControl) override;

    ndk::ScopedAStatus start() override;
    ndk::ScopedAStatus stop() override;

    ndk::ScopedAStatus injectTime(int64_t timeMs, int64_t timeReferenceMs,
                                  int uncertaintyMs) override;
    ndk::ScopedAStatus injectLocation(const GnssLocation& location) override;
    ndk::ScopedAStatus injectBestLocation(const GnssLocation& location) override;
    ndk::ScopedAStatus deleteAidingData(GnssAidingData aidingDataFlags) override;
    ndk::ScopedAStatus setPositionMode(const PositionModeOptions& options) override;

    ndk::ScopedAStatus getExtensionGnssAntennaInfo(
            std::shared_ptr<IGnssAntennaInfo>* iGnssAntennaInfo) override;
    ndk::ScopedAStatus getExtensionMeasurementCorrections(
            std::shared_ptr<IMeasurementCorrectionsInterface>* iMeasurementCorrections)
            override;

    ndk::ScopedAStatus startSvStatus() override;
    ndk::ScopedAStatus stopSvStatus() override;
    ndk::ScopedAStatus startNmea() override;
    ndk::ScopedAStatus stopNmea() override;

    void onGnssStatusCb(IGnssCallback::GnssStatusValue) override;
    void onGnssSvStatusCb(std::vector<IGnssCallback::GnssSvInfo>) override;
    void onGnssNmeaCb(int64_t timestampMs, std::string nmea) override;
    void onGnssLocationCb(GnssLocation location) override;

private:
    enum class SessionState {
        OFF, STARTING, STARTED, STOPPED
    };

    using Clock = std::chrono::steady_clock;

    double getRunningTime() const;
    double getRunningTimeLocked(Clock::time_point now) const;
    bool isWarmedUpLocked(Clock::time_point now) const;

    const std::shared_ptr<GnssBatching> mGnssBatching;
    const std::shared_ptr<GnssConfiguration> mGnssConfiguration;

    std::shared_ptr<IGnssCallback> mCallback;        // protected by mMtx
    std::optional<Clock::time_point> mStartT;        // protected by mMtx
    int mRecurrence = -1;                            // protected by mMtx
    Clock::duration mMinInterval;                    // protected by mMtx
    Clock::time_point mFirstFix;                     // protected by mMtx
    Clock::time_point mLastFix;                      // protected by mMtx
    SessionState mSessionState = SessionState::OFF;  // protected by mMtx
    bool mLowPowerMode = false;                      // protected by mMtx
    bool mSendSvStatus = false;                      // protected by mMtx
    bool mSendNmea = false;                          // protected by mMtx
    mutable std::mutex mMtx;

    std::unique_ptr<GnssHwConn> mGnssHwConn;
};

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
