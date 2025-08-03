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

#pragma once

#include <unordered_map>

#include <android-base/logging.h>
#include <android-base/thread_annotations.h>
#include <android/binder_manager.h>

#include <aidl/android/hardware/power/stats/StateResidency.h>
#include <aidl/android/vendor/powerstats/BnPixelStateResidencyCallback.h>
#include <aidl/android/vendor/powerstats/IPixelStateResidencyCallback.h>
#include <aidl/android/vendor/powerstats/IPixelStateResidencyProvider.h>

#include "../libdevice/ExynosDisplay.h"

using aidl::android::hardware::power::stats::StateResidency;
using aidl::android::vendor::powerstats::BnPixelStateResidencyCallback;
using aidl::android::vendor::powerstats::IPixelStateResidencyCallback;
using aidl::android::vendor::powerstats::IPixelStateResidencyProvider;

namespace android::hardware::graphics::composer {

class DisplayStateResidencyWatcher : public BnPixelStateResidencyCallback {
public:
    enum class BrightnessMode : int32_t {
        kOff = 0,
        kOn,
        kLp,
        kHbm,
    };
    enum class OperationRate : int32_t {
        kNone = 0,
        kLs,
        kNs,
        kHs,
    };

    struct State {
        BrightnessMode mode = BrightnessMode::kOff;
        int32_t width = 0;
        int32_t height = 0;
        int32_t refreshRate = 0;
        int32_t teFreq = 0;
        OperationRate operationRate = OperationRate::kNone;
    };

    struct ResidencyData {
        int32_t id = -1;
        uint64_t totalTimeInStateMs = 0;
        uint64_t totalStateEntryCount = 0;
        uint64_t lastEntryTimestampMs = 0;
    };

    DisplayStateResidencyWatcher(ExynosDisplay* display);
    virtual ~DisplayStateResidencyWatcher();

    virtual ndk::ScopedAStatus getStateResidency(std::vector<StateResidency>* stats) override;

    void registerWithPowerStats();
    static void binderDied(void* cookie);

    void updateResidencyDataLocked(const std::string& newState) REQUIRES(mMutex);

    void setRefreshRate(int32_t refreshRate);
    void setActiveConfig(int32_t config);
    void setPowerMode(int32_t powerMode);

private:
    std::string toString(const State& state);

    bool mRunning = false;
    std::shared_ptr<IPixelStateResidencyProvider> mProvider;
    ::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
    std::thread mTaskHandler;

    mutable std::mutex mMutex;
    std::unordered_map<std::string, ResidencyData> mResidencyData GUARDED_BY(mMutex);
    std::string mLatestStateName GUARDED_BY(mMutex);
    State mCurrentState GUARDED_BY(mMutex);
    int32_t mPowerMode = HWC2_POWER_MODE_OFF;
    int32_t mActiveConfig = -1;
    ExynosDisplay* mDisplay;
};

} // namespace android::hardware::graphics::composer
