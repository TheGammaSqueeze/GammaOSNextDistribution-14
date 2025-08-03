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
#include <condition_variable>
#include <mutex>
#include <thread>
#include <aidl/android/hardware/gnss/BnGnssMeasurementInterface.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

struct GnssMeasurementInterface : public BnGnssMeasurementInterface {
    GnssMeasurementInterface() = default;
    ~GnssMeasurementInterface();

    ndk::ScopedAStatus setCallback(const std::shared_ptr<IGnssMeasurementCallback>& callback,
                                   const bool enableFullTracking,
                                   const bool enableCorrVecOutputs) override;
    ndk::ScopedAStatus close() override;
    ndk::ScopedAStatus setCallbackWithOptions(
            const std::shared_ptr<IGnssMeasurementCallback>& callback,
            const Options& options) override;

private:
    void closeImpl();
    ndk::ScopedAStatus setCallbackImpl(const std::shared_ptr<IGnssMeasurementCallback>& callback,
                                       bool enableCorrVecOutputs,
                                       int intervalMs);
    void stopLocked();
    void update();

    std::shared_ptr<IGnssMeasurementCallback> mCallback;
    std::vector<GnssData>                     mGnssData;
    int                                       mGnssDataIndex = 0;
    std::condition_variable                   mThreadNotification;
    bool                                      mRunning = false;
    std::thread                               mThread;
    mutable std::mutex                        mMtx;
};

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
