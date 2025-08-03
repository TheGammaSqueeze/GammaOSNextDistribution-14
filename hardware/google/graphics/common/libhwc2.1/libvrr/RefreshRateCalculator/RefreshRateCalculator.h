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

#include <utils/Errors.h>
#include <utils/Log.h>
#include <chrono>
#include <functional>
#include <string>

#include <hardware/hwcomposer_defs.h>

#include "../Utils.h"
#include "../interface/VariableRefreshRateInterface.h"

namespace android::hardware::graphics::composer {

enum class RefreshRateCalculatorType : int {
    kInvalid = -1,
    kAod = 0,
    kInstant,
    kPeriodical,
    kVideoPlayback,
    kCombined,
    kTotal,
};

constexpr int64_t kDefaultMinimumRefreshRate = 1;

constexpr int64_t kDefaultInvalidPresentTimeNs = -1;

constexpr int64_t kDefaultInvalidRefreshRate = -1;

class RefreshRateCalculator : public PowerModeListener {
public:
    RefreshRateCalculator()
          : mMinFrameIntervalNs(roundDivide(std::nano::den, static_cast<int64_t>(mMaxFrameRate))) {}

    virtual ~RefreshRateCalculator() = default;

    std::string getName() const { return mName; };

    virtual int getRefreshRate() const = 0;

    virtual void onPowerStateChange(int __unused from, int to) override { mPowerMode = to; }

    virtual void onPresent(int64_t presentTimeNs, int flag) = 0;

    virtual void reset() = 0;

    virtual void registerRefreshRateChangeCallback(std::function<void(int)> callback) {
        mRefreshRateChangeCallback = std::move(callback);
    }

    virtual void setEnabled(bool __unused isEnabled){};

    // Should be invoked during the transition from HS to NS or vice versa.
    void setMinFrameInterval(int64_t minFrameIntervalNs) {
        mMinFrameIntervalNs = minFrameIntervalNs;
        mMaxFrameRate = durationToVsync(mMinFrameIntervalNs);
    }

    void setName(const std::string& name) { mName = name; }

protected:
    static constexpr int64_t kDefaultMaxFrameRate = 120;

    int durationToVsync(int64_t duration) const {
        return roundDivide(duration, mMinFrameIntervalNs);
    }

    std::function<void(int)> mRefreshRateChangeCallback;

    int mMaxFrameRate = kDefaultMaxFrameRate;

    int64_t mMinFrameIntervalNs;

    std::string mName;
    int32_t mPowerMode = -1;
};

} // namespace android::hardware::graphics::composer
