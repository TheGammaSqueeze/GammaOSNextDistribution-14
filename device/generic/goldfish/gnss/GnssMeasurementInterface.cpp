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

#include <chrono>
#include <aidl/android/hardware/gnss/IGnss.h>
#include <debug.h>
#include "GnssMeasurementInterface.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {
namespace {
using Clock = std::chrono::steady_clock;

void initGnssData(GnssData& data,
                  const int64_t elapsedRealtimeNs,
                  const int64_t timeNs,
                  const int64_t fullBiasNs,
                  const double biasUncertaintyNs,
                  const size_t nMeasurements) {
    data.elapsedRealtime.flags = ElapsedRealtime::HAS_TIMESTAMP_NS;
    data.elapsedRealtime.timestampNs = elapsedRealtimeNs;
    data.clock.gnssClockFlags = GnssClock::HAS_FULL_BIAS;
    data.clock.timeNs = timeNs;
    data.clock.fullBiasNs = fullBiasNs;
    data.clock.biasUncertaintyNs = biasUncertaintyNs;
    data.measurements.resize(nMeasurements);
}

GnssMeasurement makeGnssMeasurement(const bool enableCorrVecOutputs,
                                    const int svid,
                                    const int state,
                                    const int64_t receivedSvTimeInNs,
                                    const int64_t receivedSvTimeUncertaintyInNs,
                                    const double cN0DbHz,
                                    const double pseudorangeRateMps,
                                    const double pseudorangeRateUncertaintyMps,
                                    const int accumulatedDeltaRangeState,
                                    const double accumulatedDeltaRangeM,
                                    const double accumulatedDeltaRangeUncertaintyM,
                                    const int multipathIndicator,
                                    const int constellation) {
    GnssMeasurement m;

    m.flags = GnssMeasurement::HAS_CARRIER_FREQUENCY;
    m.svid = svid;

    m.signalType.constellation = static_cast<GnssConstellationType>(constellation);
    m.signalType.carrierFrequencyHz = 1.59975e+09;
    m.signalType.codeType = "UNKNOWN";

    m.timeOffsetNs = 0;
    m.state = GnssMeasurement::STATE_UNKNOWN | state;
    m.receivedSvTimeInNs = receivedSvTimeInNs;
    m.receivedSvTimeUncertaintyInNs = receivedSvTimeUncertaintyInNs;
    m.antennaCN0DbHz = cN0DbHz;
    m.basebandCN0DbHz = cN0DbHz - 4;
    m.pseudorangeRateMps = pseudorangeRateMps;
    m.pseudorangeRateUncertaintyMps = pseudorangeRateUncertaintyMps;
    m.accumulatedDeltaRangeState = accumulatedDeltaRangeState;
    m.accumulatedDeltaRangeM = accumulatedDeltaRangeM;
    m.accumulatedDeltaRangeUncertaintyM = accumulatedDeltaRangeUncertaintyM;
    m.multipathIndicator = static_cast<GnssMultipathIndicator>(multipathIndicator);

    if (enableCorrVecOutputs) {
        const CorrelationVector correlationVector1 = {
                .frequencyOffsetMps = 10,
                .samplingWidthM = 30,
                .samplingStartM = 0,
                .magnitude = {0, 5000, 10000, 5000, 0, 0, 3000, 0}};

        const CorrelationVector correlationVector2 = {
                .frequencyOffsetMps = 20,
                .samplingWidthM = 30,
                .samplingStartM = -10,
                .magnitude = {0, 3000, 5000, 3000, 0, 0, 1000, 0}};

        m.correlationVectors = {correlationVector1, correlationVector2};
        m.flags = GnssMeasurement::HAS_CORRELATION_VECTOR;
    }

    return m;
}

}  // namsepace

GnssMeasurementInterface::~GnssMeasurementInterface() {
    closeImpl();
}

ndk::ScopedAStatus GnssMeasurementInterface::setCallback(
        const std::shared_ptr<IGnssMeasurementCallback>& callback,
        const bool /*enableFullTracking*/,
        const bool enableCorrVecOutputs) {
    return setCallbackImpl(callback, enableCorrVecOutputs, 1000);
}

ndk::ScopedAStatus GnssMeasurementInterface::close() {
    closeImpl();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssMeasurementInterface::setCallbackWithOptions(
        const std::shared_ptr<IGnssMeasurementCallback>& callback,
        const Options& options) {
    return setCallbackImpl(callback, options.enableCorrVecOutputs, options.intervalMs);
}

void GnssMeasurementInterface::closeImpl() {
    bool needJoin;

    {
        std::lock_guard<std::mutex> lock(mMtx);
        if (mThread.joinable()) {
            mRunning = false;
            mThreadNotification.notify_all();
            needJoin = true;
        } else {
            needJoin = false;
        }
    }

    if (needJoin) {
        mThread.join();
    }
}

ndk::ScopedAStatus GnssMeasurementInterface::setCallbackImpl(
        const std::shared_ptr<IGnssMeasurementCallback>& callback,
        const bool enableCorrVecOutputs,
        const int intervalMs) {
    if (!callback) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    if (intervalMs <= 0) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    mGnssData.resize(1);

    initGnssData(mGnssData[0], 139287, 116834000000, -1189181444165780000, 5.26068202130163, 7);
    mGnssData[0].measurements[0] = makeGnssMeasurement(enableCorrVecOutputs, 22,  47, 3927349114,      29, 29.9917297363281,  245.509362821673,  0.148940800975766, 1,  6620.74237064615,  0.00271145859733223, 0, 1);
    mGnssData[0].measurements[1] = makeGnssMeasurement(enableCorrVecOutputs, 23,  47, 3920005435,      14, 36.063377380371,  -731.947951627658, 0.0769754027959242, 1,  -23229.096048105,  0.00142954161856323, 0, 1);
    mGnssData[0].measurements[2] = makeGnssMeasurement(enableCorrVecOutputs, 25,  47, 3923720994,      56, 24.5171585083007, -329.789995021822,  0.277918601850871, 1, -15511.1976492851,  0.00509250536561012, 0, 1);
    mGnssData[0].measurements[3] = makeGnssMeasurement(enableCorrVecOutputs, 31,  47, 3925772934,      11, 37.9193840026855,  -380.23772244582, 0.0602980729893803, 1, -11325.9094456612,  0.00115450704470276, 0, 1);
    mGnssData[0].measurements[4] = makeGnssMeasurement(enableCorrVecOutputs, 32,  47, 3919018415,      21, 32.8980560302734,  581.800347848025,  0.109060249597082, 1,  15707.8963147985,  0.00205808319151401, 0, 1);
    mGnssData[0].measurements[5] = makeGnssMeasurement(enableCorrVecOutputs, 10, 227, 69142929947304, 127, 23.432445526123,    259.17838762857,   0.31591691295607, 4,  8152.78081298147, 3.40282346638528E+38, 0, 3);
    mGnssData[0].measurements[6] = makeGnssMeasurement(enableCorrVecOutputs, 2,  227, 69142935176327,  41, 33.180908203125,  -53.8773853795901,  0.104984458760586, 1, -1708.08166640048,  0.00196184404194355, 0, 3);

    const Clock::duration interval = std::chrono::milliseconds(intervalMs);

    closeImpl();

    std::lock_guard<std::mutex> lock(mMtx);
    mRunning = true;

    mThread = std::thread([this, callback, interval](){
        Clock::time_point wakeupT = Clock::now() + interval;

        for (unsigned gnssDataIndex = 0;; gnssDataIndex = (gnssDataIndex + 1) % mGnssData.size(),
                                          wakeupT += interval) {
            std::unique_lock<std::mutex> lock(mMtx);
            if ((mThreadNotification.wait_until(lock, wakeupT) == std::cv_status::no_timeout) &&
                    !mRunning) {
                return;
            }

            callback->gnssMeasurementCb(mGnssData[gnssDataIndex]);
        }
    });

    return ndk::ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
