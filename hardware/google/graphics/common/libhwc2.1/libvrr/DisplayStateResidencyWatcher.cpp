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

#include "DisplayStateResidencyWatcher.h"

#include <android/binder_ibinder.h>

#include "ExynosHWCHelper.h"
#include "Utils.h"

using aidl::android::vendor::powerstats::IPixelStateResidencyCallback;
using aidl::android::vendor::powerstats::IPixelStateResidencyProvider;

namespace android::hardware::graphics::composer {

static const std::string kEntityName = "Display";

DisplayStateResidencyWatcher::DisplayStateResidencyWatcher(ExynosDisplay* display)
      : mRunning(true),
        mDeathRecipient(AIBinder_DeathRecipient_new(binderDied)),
        mDisplay(display) {
    registerWithPowerStats();
}

DisplayStateResidencyWatcher::~DisplayStateResidencyWatcher() {
    mRunning = false;
    if (mTaskHandler.joinable()) {
        mTaskHandler.join();
    }
    if (mProvider) {
        AIBinder_unlinkToDeath(mProvider->asBinder().get(), mDeathRecipient.get(),
                               reinterpret_cast<void*>(this));
        mProvider->unregisterCallback(this->ref<IPixelStateResidencyCallback>());
    }
}

::ndk::ScopedAStatus DisplayStateResidencyWatcher::getStateResidency(
        std::vector<StateResidency>* stats) {
    std::scoped_lock lock(mMutex);
    for (auto& [state, data] : mResidencyData) {
        if (data.id < 0) {
            LOG(ERROR) << "Unmonitored state: " << state;
            continue;
        }
        StateResidency result;
        result.id = data.id;
        result.totalTimeInStateMs = data.totalTimeInStateMs;
        if (state == mLatestStateName) {
            result.totalTimeInStateMs += getNowMs() - data.lastEntryTimestampMs;
        }
        result.totalStateEntryCount = data.totalStateEntryCount;
        result.lastEntryTimestampMs = data.lastEntryTimestampMs;
        stats->push_back(result);
    }
    return ::ndk::ScopedAStatus::ok();
}

constexpr std::chrono::seconds kOneSecondPeriod{1};

void DisplayStateResidencyWatcher::registerWithPowerStats() {
    if (mProvider) {
        LOG(INFO) << "Need to reconnect PowerStats service";
        mProvider.reset();
    }
    LOG(INFO) << "Registering PowerStats callback";
    if (mTaskHandler.joinable()) {
        mTaskHandler.join();
    }
    mTaskHandler = std::thread([this]() {
        static const std::string kInstance = "power.stats-vendor";

        int retryCount = 0;
        while (mRunning && retryCount++ < 100) {
            ndk::SpAIBinder binder(AServiceManager_waitForService(kInstance.c_str()));
            mProvider = IPixelStateResidencyProvider::fromBinder(binder);

            if (mProvider) {
                break;
            } else {
                LOG(ERROR) << "PowerStats service: " << kInstance
                           << " unavailable, retry: " << retryCount;
                std::this_thread::sleep_for(kOneSecondPeriod);
            }
        }

        if (!mRunning) {
            return;
        }

        if (auto status = mProvider->registerCallback(kEntityName,
                                                      this->ref<IPixelStateResidencyCallback>());
            status.isOk()) {
            LOG(INFO) << "PowerStats callback is successfully registered.";
        } else {
            LOG(ERROR) << "failed to register callback: " << status.getDescription();
        }
        AIBinder_linkToDeath(mProvider->asBinder().get(), mDeathRecipient.get(),
                             reinterpret_cast<void*>(this));
    });
}

void DisplayStateResidencyWatcher::binderDied(void* cookie) {
    LOG(ERROR) << "power.stats died";

    auto residencyWatcher = reinterpret_cast<DisplayStateResidencyWatcher*>(cookie);

    residencyWatcher->registerWithPowerStats();
}

void DisplayStateResidencyWatcher::updateResidencyDataLocked(const std::string& newState) {
    if (newState.empty()) {
        LOG(ERROR) << __func__ << ": Invalid display state";
        return;
    }
    if (mLatestStateName == newState) {
        return;
    }
    auto nowMs = static_cast<uint64_t>(getNowMs());
    if (auto it = mResidencyData.find(mLatestStateName); it != mResidencyData.end()) {
        it->second.totalTimeInStateMs += nowMs - it->second.lastEntryTimestampMs;
    } else {
        LOG(ERROR) << "Latest state: " << mLatestStateName << " does not have any residency data";
    }
    if (auto it = mResidencyData.find(newState); it != mResidencyData.end()) {
        it->second.lastEntryTimestampMs = nowMs;
        it->second.totalStateEntryCount++;
    } else {
        mResidencyData.emplace(newState,
                               ResidencyData{
                                       .totalTimeInStateMs = 0,
                                       .totalStateEntryCount = 1,
                                       .lastEntryTimestampMs = nowMs,
                               });
    }
    mLatestStateName = newState;
}

void DisplayStateResidencyWatcher::setRefreshRate(int32_t refreshRate) {
    std::scoped_lock lock(mMutex);

    if (mCurrentState.refreshRate == refreshRate) {
        return;
    }
    mCurrentState.refreshRate = refreshRate;
    updateResidencyDataLocked(toString(mCurrentState));
}

void DisplayStateResidencyWatcher::setActiveConfig(int32_t config) {
    std::scoped_lock lock(mMutex);

    if (mActiveConfig == config) {
        return;
    }

    if (auto it = mDisplay->mDisplayConfigs.find(config); it != mDisplay->mDisplayConfigs.end()) {
        mCurrentState.width = it->second.width;
        mCurrentState.height = it->second.height;
        mCurrentState.teFreq = durationNsToFreq(it->second.vsyncPeriod);
        mActiveConfig = config;
    } else {
        LOG(ERROR) << " Config " << config << " not found";
        return;
    }

    updateResidencyDataLocked(toString(mCurrentState));
}

void DisplayStateResidencyWatcher::setPowerMode(int32_t powerMode) {
    std::scoped_lock lock(mMutex);

    if (mPowerMode == powerMode) {
        return;
    }
    mPowerMode = powerMode;
    if (powerMode == HWC2_POWER_MODE_OFF) {
        mCurrentState.mode = BrightnessMode::kOff;
    } else if (powerMode == HWC2_POWER_MODE_DOZE) {
        mCurrentState.mode = BrightnessMode::kLp;
    } else if (powerMode == HWC2_POWER_MODE_ON) {
        // TODO(b/315497129): check HBM state
        mCurrentState.mode = BrightnessMode::kOn;
    }

    updateResidencyDataLocked(toString(mCurrentState));
}

std::string DisplayStateResidencyWatcher::toString(
        const DisplayStateResidencyWatcher::State& state) {
    if (state.mode == DisplayStateResidencyWatcher::BrightnessMode::kOff) {
        return "Off";
    }
    std::string modeName = "";
    switch (state.mode) {
        case BrightnessMode::kOn:
            modeName = "On";
            break;
        case BrightnessMode::kLp:
            modeName = "LP";
            break;
        case BrightnessMode::kHbm:
            modeName = "HBM";
            break;
        default:
            LOG(ERROR) << __func__ << ": Unknown brightness mode " << toUnderlying(state.mode);
            return "";
    }
    auto name = String8::format("%s:%dx%d@%d:%d", modeName.c_str(), state.width, state.height,
                                state.refreshRate, state.teFreq);
    switch (state.operationRate) {
        case OperationRate::kNone:
            break;
        case OperationRate::kLs:
            name.append("+LS");
            break;
        case OperationRate::kNs:
            name.append("+NS");
            break;
        case OperationRate::kHs:
            name.append("+HS");
            break;
        default:
            LOG(ERROR) << __func__ << ": Unknown operation rate "
                       << toUnderlying(state.operationRate);
            return "";
    }
    return std::string(name.c_str());
}

} // namespace android::hardware::graphics::composer
