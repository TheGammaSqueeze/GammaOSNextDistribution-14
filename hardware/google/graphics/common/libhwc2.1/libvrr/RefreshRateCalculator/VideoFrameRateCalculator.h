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

#include <stdint.h>
#include <chrono>
#include <list>

#include "../EventQueue.h"
#include "PeriodRefreshRateCalculator.h"
#include "RefreshRateCalculator.h"

namespace android::hardware::graphics::composer {

struct VideoFrameRateCalculatorParameters {
    VideoFrameRateCalculatorParameters() {
        mPeriodParams.mAlwaysCallback = true;
        mPeriodParams.mConfidencePercentage = 95;
    }

    int mDelta = 5;

    int mWindowSize = 5;

    int mMinStableRuns = 3;

    PeriodRefreshRateCalculatorParameters mPeriodParams;

    int mMinInterestedFrameRate = 1;
    int mMaxInterestedFrameRate = 120;
};

class VideoFrameRateCalculator : public RefreshRateCalculator {
public:
    VideoFrameRateCalculator(EventQueue* eventQueue)
          : VideoFrameRateCalculator(eventQueue, VideoFrameRateCalculatorParameters()) {}

    VideoFrameRateCalculator(EventQueue* eventQueue,
                             const VideoFrameRateCalculatorParameters& params);

    int getRefreshRate() const final;

    void onPowerStateChange(int from, int to) final;

    void onPresent(int64_t presentTimeNs, int flag) override;

    void reset() override;

    void setEnabled(bool isEnabled) final;

private:
    int onReportRefreshRate(int);

    void setNewRefreshRate(int newRefreshRate);

    std::unique_ptr<RefreshRateCalculator> mRefreshRateCalculator;

    EventQueue* mEventQueue;
    VideoFrameRateCalculatorParameters mParams;

    int mLastVideoFrameRate = kDefaultInvalidRefreshRate;

    int mLastPeriodFrameRate = kDefaultInvalidRefreshRate;
    int mLastPeriodFrameRateRuns = 0;

    std::list<int> mHistory;
};

} // namespace android::hardware::graphics::composer
