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

#include <log/log.h>
#include <debug.h>

#include "Agnss.h"
#include "AgnssRil.h"
#include "GnssAntennaInfo.h"
#include "GnssBatching.h"
#include "GnssDebug.h"
#include "GnssGeofence.h"
#include "GnssMeasurementInterface.h"
#include "GnssNavigationMessageInterface.h"
#include "GnssPowerIndication.h"
#include "GnssPsds.h"
#include "GnssVisibilityControl.h"
#include "Gnss.h"
#include "MeasurementCorrectionsInterface.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {
namespace {
constexpr char kGnssDeviceName[] = "Android Studio Emulator GPS";
}  // namespace

Gnss::Gnss()
        : mGnssBatching(ndk::SharedRefBase::make<GnssBatching>())
        , mGnssConfiguration(ndk::SharedRefBase::make<GnssConfiguration>()) {
}

Gnss::~Gnss() {
}

ndk::ScopedAStatus Gnss::setCallback(const std::shared_ptr<IGnssCallback>& callback) {
    if (callback == nullptr) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    callback->gnssSetCapabilitiesCb(IGnssCallback::CAPABILITY_MEASUREMENTS |
                                    IGnssCallback::CAPABILITY_SCHEDULING);

    callback->gnssSetSystemInfoCb({.yearOfHw = 2023, .name = kGnssDeviceName});

    std::lock_guard<std::mutex> lock(mMtx);
    mCallback = callback;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::close() {
    mGnssHwConn.reset();

    std::lock_guard<std::mutex> lock(mMtx);
    mCallback.reset();

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionPsds(std::shared_ptr<IGnssPsds>* iGnssPsds) {
    *iGnssPsds = ndk::SharedRefBase::make<GnssPsds>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssConfiguration(
        std::shared_ptr<IGnssConfiguration>* iGnssConfiguration) {
    *iGnssConfiguration = mGnssConfiguration;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssMeasurement(
        std::shared_ptr<IGnssMeasurementInterface>* iGnssMeasurement) {
    *iGnssMeasurement = ndk::SharedRefBase::make<GnssMeasurementInterface>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssPowerIndication(
        std::shared_ptr<IGnssPowerIndication>* iGnssPowerIndication) {
    *iGnssPowerIndication = ndk::SharedRefBase::make<GnssPowerIndication>(
        std::bind(&Gnss::getRunningTime, this));
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssBatching(
        std::shared_ptr<IGnssBatching>* iGnssBatching) {
    *iGnssBatching = mGnssBatching;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssGeofence(
        std::shared_ptr<IGnssGeofence>* iGnssGeofence) {
    *iGnssGeofence = ndk::SharedRefBase::make<GnssGeofence>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssNavigationMessage(
        std::shared_ptr<IGnssNavigationMessageInterface>* iGnssNavigationMessage) {
    *iGnssNavigationMessage = ndk::SharedRefBase::make<GnssNavigationMessageInterface>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionAGnss(std::shared_ptr<IAGnss>* iAGnss) {
    *iAGnss = ndk::SharedRefBase::make<AGnss>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionAGnssRil(std::shared_ptr<IAGnssRil>* iAGnssRil) {
    *iAGnssRil = ndk::SharedRefBase::make<AGnssRil>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssDebug(std::shared_ptr<IGnssDebug>* iGnssDebug) {
    *iGnssDebug = ndk::SharedRefBase::make<GnssDebug>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssVisibilityControl(
        std::shared_ptr<IGnssVisibilityControl>* iGnssVisibilityControl) {
    *iGnssVisibilityControl = ndk::SharedRefBase::make<GnssVisibilityControl>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::start() {
    {
        std::lock_guard<std::mutex> lock(mMtx);
        if (!mCallback) {
            return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
        }
    }

    if (!mGnssHwConn) {
        auto conn = std::make_unique<GnssHwConn>(*this);
        if (!conn->ok()) {
            return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_GENERIC));
        }

        std::lock_guard<std::mutex> lock(mMtx);
        mSessionState = SessionState::STARTING;
        mStartT = std::chrono::steady_clock::now();
        mGnssHwConn = std::move(conn);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::stop() {
    if (!mGnssHwConn) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    {
        std::lock_guard<std::mutex> lock(mMtx);
        if (mCallback) {
            if (mSessionState == SessionState::STARTED) {
                mCallback->gnssStatusCb(IGnssCallback::GnssStatusValue::SESSION_END);
                mSessionState = SessionState::STOPPED;
            }
        } else {
            return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
        }
    }

    mGnssHwConn.reset();

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::injectTime(int64_t /*timeMs*/,
                                    int64_t /*timeReferenceMs*/,
                                    int /*uncertaintyMs*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::injectLocation(const GnssLocation& /*location*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::injectBestLocation(const GnssLocation& /*location*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::deleteAidingData(const GnssAidingData /*aidingDataFlags*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::setPositionMode(const PositionModeOptions& options) {
    if (options.minIntervalMs < 0) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    std::lock_guard<std::mutex> lock(mMtx);

    mRecurrence = options.recurrence ==
        (IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC) ? -1 : 1;
    mMinInterval = std::chrono::milliseconds(options.minIntervalMs);
    mFirstFix = Clock::now();
    mLastFix = mFirstFix - mMinInterval;
    mLowPowerMode = options.lowPowerMode;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionGnssAntennaInfo(
        std::shared_ptr<IGnssAntennaInfo>* iGnssAntennaInfo) {
    *iGnssAntennaInfo = ndk::SharedRefBase::make<GnssAntennaInfo>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::getExtensionMeasurementCorrections(
        std::shared_ptr<IMeasurementCorrectionsInterface>* iMeasurementCorrections) {
    *iMeasurementCorrections = ndk::SharedRefBase::make<MeasurementCorrectionsInterface>();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::startSvStatus() {
    std::lock_guard<std::mutex> lock(mMtx);
    mSendSvStatus = true;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::stopSvStatus() {
    std::lock_guard<std::mutex> lock(mMtx);
    mSendSvStatus = false;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::startNmea() {
    std::lock_guard<std::mutex> lock(mMtx);
    mSendNmea = true;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Gnss::stopNmea() {
    std::lock_guard<std::mutex> lock(mMtx);
    mSendNmea = false;
    return ndk::ScopedAStatus::ok();
}

void Gnss::onGnssStatusCb(const IGnssCallback::GnssStatusValue status) {
    std::lock_guard<std::mutex> lock(mMtx);
    if (mCallback) {
        mCallback->gnssStatusCb(status);
    }
}

void Gnss::onGnssSvStatusCb(std::vector<IGnssCallback::GnssSvInfo> svInfo) {
    std::lock_guard<std::mutex> lock(mMtx);
    if (!mCallback || !mSendSvStatus) {
        return;
    }

    switch (mSessionState) {
    case SessionState::STARTING:
        mCallback->gnssStatusCb(IGnssCallback::GnssStatusValue::SESSION_BEGIN);
        mSessionState = SessionState::STARTED;
        break;

    case SessionState::STARTED:
        break;  // do nothing

    default:
        return;
    }

    mCallback->gnssSvStatusCb(std::move(svInfo));
}

void Gnss::onGnssNmeaCb(const int64_t timestampMs, std::string nmea) {
    std::lock_guard<std::mutex> lock(mMtx);
    if (!mCallback || !mSendNmea) {
        return;
    }

    if (!isWarmedUpLocked(Clock::now())) {
        return;
    }

    switch (mSessionState) {
    case SessionState::STARTING:
        mCallback->gnssStatusCb(IGnssCallback::GnssStatusValue::SESSION_BEGIN);
        mSessionState = SessionState::STARTED;
        break;

    case SessionState::STARTED:
        break;  // do nothing

    default:
        return;
    }

    mCallback->gnssNmeaCb(timestampMs, std::move(nmea));
}

void Gnss::onGnssLocationCb(GnssLocation location) {
    ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);

    std::lock_guard<std::mutex> lock(mMtx);
    if (!mCallback) {
        ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);
        return;
    }

    const auto now = Clock::now();
    if (!isWarmedUpLocked(now) || (now < mFirstFix) || (now < (mLastFix + mMinInterval))) {
        ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);
        return;
    }

    switch (mSessionState) {
    case SessionState::STARTING:
        ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);
        mCallback->gnssStatusCb(IGnssCallback::GnssStatusValue::SESSION_BEGIN);
        mSessionState = SessionState::STARTED;
        break;

    case SessionState::STARTED:
        ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);
        break;  // do nothing

    default:
        ALOGD("%s:%s:%d", "Gnss", __func__, __LINE__);
        return;
    }

    if (mRecurrence == 0) {
        return;
    } else if (mRecurrence > 0) {
        --mRecurrence;
    }

    mLastFix = now;
    mCallback->gnssLocationCb(location);
    mGnssBatching->onGnssLocationCb(std::move(location));
}

double Gnss::getRunningTime() const {
    std::lock_guard<std::mutex> lock(mMtx);
    return getRunningTimeLocked(Clock::now());
}

double Gnss::getRunningTimeLocked(const Clock::time_point now) const {
    if (mStartT.has_value()) {
        return std::chrono::duration<double>(now - mStartT.value()).count();
    } else {
        return 0.0;
    }
}

bool Gnss::isWarmedUpLocked(const Clock::time_point now) const {
    return getRunningTimeLocked(now) >= 3.5;   // CTS requires warming up time
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
