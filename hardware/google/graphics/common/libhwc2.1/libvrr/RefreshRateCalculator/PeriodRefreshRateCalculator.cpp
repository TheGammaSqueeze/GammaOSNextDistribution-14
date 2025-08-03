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

#include "PeriodRefreshRateCalculator.h"

#include "../Utils.h"

namespace android::hardware::graphics::composer {

PeriodRefreshRateCalculator::PeriodRefreshRateCalculator(
        EventQueue* eventQueue, const PeriodRefreshRateCalculatorParameters& params)
      : mEventQueue(eventQueue), mParams(params) {
    mName = "PeriodRefreshRateCalculator";

    mMeasureEvent.mEventType = VrrControllerEventType::kPeriodRefreshRateCalculatorUpdate;
    mLastMeasureTimeNs = getNowNs() + params.mMeasurePeriodNs;
    mMeasureEvent.mWhenNs = mLastMeasureTimeNs;
    mMeasureEvent.mFunctor = std::move(std::bind(&PeriodRefreshRateCalculator::onMeasure, this));
    mEventQueue->mPriorityQueue.emplace(mMeasureEvent);

    mMeasurePeriodRatio = (static_cast<float>(mParams.mMeasurePeriodNs) / std::nano::den);
    mNumVsyncPerMeasure = static_cast<int>(mMaxFrameRate * mMeasurePeriodRatio);
}

int PeriodRefreshRateCalculator::getRefreshRate() const {
    return mLastRefreshRate;
}

void PeriodRefreshRateCalculator::onPowerStateChange(int from, int to) {
    if (to != HWC_POWER_MODE_NORMAL) {
        // We bypass inspection of the previous power state , as it is irrelevant to discard events
        // when there is no event.
        setEnabled(false);
    } else {
        if (from == HWC_POWER_MODE_NORMAL) {
            ALOGE("Disregard power state change notification by staying current power state.");
            return;
        }
        setEnabled(true);
    }
    mPowerMode = to;
}

void PeriodRefreshRateCalculator::onPresent(int64_t presentTimeNs, int flag) {
    if (hasPresentFrameFlag(flag, PresentFrameFlag::kPresentingWhenDoze)) {
        return;
    }
    if (mLastPresentTimeNs >= 0) {
        auto periodNs = presentTimeNs - mLastPresentTimeNs;
        if (periodNs <= std::nano::den) {
            int numVsync = std::max(1, durationToVsync(periodNs));
            ++mStatistics[numVsync];
        }
    }
    mLastPresentTimeNs = presentTimeNs;
}

void PeriodRefreshRateCalculator::reset() {
    mStatistics.clear();
    mLastRefreshRate = kDefaultInvalidRefreshRate;
    mLastPresentTimeNs = kDefaultInvalidPresentTimeNs;
}

void PeriodRefreshRateCalculator::setEnabled(bool isEnabled) {
    if (!isEnabled) {
        mEventQueue->dropEvent(VrrControllerEventType::kPeriodRefreshRateCalculatorUpdate);
    } else {
        mLastMeasureTimeNs = getNowNs() + mParams.mMeasurePeriodNs;
        mMeasureEvent.mWhenNs = mLastMeasureTimeNs;
        mMeasureEvent.mFunctor =
                std::move(std::bind(&PeriodRefreshRateCalculator::onMeasure, this));
        mEventQueue->mPriorityQueue.emplace(mMeasureEvent);
    }
}

int PeriodRefreshRateCalculator::onMeasure() {
    int currentRefreshRate = kDefaultInvalidRefreshRate;
    int totalPresent = 0;
    int totalVsync = 0;
    int maxOccurrence = 0;
    int majorVsync = 0;

    for (const auto& it : mStatistics) {
        totalPresent += it.second;
        totalVsync += (it.first * it.second);
        if (it.second > maxOccurrence) {
            maxOccurrence = it.second;
            majorVsync = it.first;
        }
    }

    if (totalPresent > 0 &&
        ((totalVsync * 100) > (mNumVsyncPerMeasure * mParams.mConfidencePercentage))) {
        if (mParams.mType == PeriodRefreshRateCalculatorType::kAverage) {
            float averageVsync = (static_cast<float>(totalVsync) / totalPresent);
            currentRefreshRate = std::round(mMaxFrameRate / averageVsync);
        } else {
            currentRefreshRate = roundDivide(mMaxFrameRate, majorVsync);
        }
    }
    mStatistics.clear();
    currentRefreshRate = std::max(currentRefreshRate, 1);
    currentRefreshRate = std::min(currentRefreshRate, mMaxFrameRate);
    setNewRefreshRate(currentRefreshRate);

    // Prepare next measurement event.
    mLastMeasureTimeNs += mParams.mMeasurePeriodNs;
    mMeasureEvent.mWhenNs = mLastMeasureTimeNs;
    mEventQueue->mPriorityQueue.emplace(mMeasureEvent);
    return NO_ERROR;
}

void PeriodRefreshRateCalculator::setNewRefreshRate(int newRefreshRate) {
    if ((newRefreshRate != mLastRefreshRate) || mParams.mAlwaysCallback) {
        mLastRefreshRate = newRefreshRate;
        if (mRefreshRateChangeCallback) {
            mRefreshRateChangeCallback(mLastRefreshRate);
        }
    }
}

} // namespace android::hardware::graphics::composer
