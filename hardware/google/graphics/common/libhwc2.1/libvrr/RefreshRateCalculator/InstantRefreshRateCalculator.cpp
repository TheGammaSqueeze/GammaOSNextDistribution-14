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

#include "InstantRefreshRateCalculator.h"

#include <algorithm>

namespace android::hardware::graphics::composer {

InstantRefreshRateCalculator::InstantRefreshRateCalculator(EventQueue* eventQueue)
      : InstantRefreshRateCalculator(eventQueue, kDefaultMaxValidTimeNs) {}

InstantRefreshRateCalculator::InstantRefreshRateCalculator(EventQueue* eventQueue,
                                                           int64_t maxValidTimeNs)
      : mEventQueue(eventQueue), mMaxValidTimeNs(maxValidTimeNs) {
    mName = "InstantRefreshRateCalculator";
    mTimeoutEvent.mEventType = VrrControllerEventType::kInstantRefreshRateCalculatorUpdate;
    mTimeoutEvent.mFunctor =
            std::move(std::bind(&InstantRefreshRateCalculator::updateRefreshRate, this));
}

int InstantRefreshRateCalculator::getRefreshRate() const {
    return mLastRefreshRate;
}

void InstantRefreshRateCalculator::onPresent(int64_t presentTimeNs, int flag) {
    if (hasPresentFrameFlag(flag, PresentFrameFlag::kPresentingWhenDoze)) {
        return;
    }
    if (mLastPresentTimeNs != kDefaultInvalidPresentTimeNs) {
        if (presentTimeNs <= mLastPresentTimeNs) {
            // Disregard incoming frames that are out of sequence.
            return;
        }
        if (isOutdated(presentTimeNs)) {
            reset();
        } else {
            auto numVsync = durationToVsync((presentTimeNs - mLastPresentTimeNs));
            numVsync = std::max(1, std::min(mMaxFrameRate, numVsync));
            auto currentRefreshRate = roundDivide(mMaxFrameRate, numVsync);
            currentRefreshRate = std::max(1, currentRefreshRate);
            setNewRefreshRate(currentRefreshRate);
        }
    }
    mLastPresentTimeNs = presentTimeNs;

    mEventQueue->dropEvent(VrrControllerEventType::kInstantRefreshRateCalculatorUpdate);
    mTimeoutEvent.mWhenNs = presentTimeNs + mMaxValidTimeNs;
    mEventQueue->mPriorityQueue.emplace(mTimeoutEvent);
}

void InstantRefreshRateCalculator::reset() {
    mLastPresentTimeNs = kDefaultInvalidPresentTimeNs;
    setNewRefreshRate(kDefaultInvalidRefreshRate);
}

void InstantRefreshRateCalculator::setEnabled(bool isEnabled) {
    if (!isEnabled) {
        mEventQueue->dropEvent(VrrControllerEventType::kInstantRefreshRateCalculatorUpdate);
    } else {
        mTimeoutEvent.mWhenNs = getNowNs() + mMaxValidTimeNs;
        mEventQueue->mPriorityQueue.emplace(mTimeoutEvent);
    }
}

bool InstantRefreshRateCalculator::isOutdated(int64_t timeNs) const {
    return (mLastPresentTimeNs == kDefaultInvalidPresentTimeNs) ||
            ((timeNs - mLastPresentTimeNs) > mMaxValidTimeNs);
}

void InstantRefreshRateCalculator::setNewRefreshRate(int newRefreshRate) {
    if (newRefreshRate != mLastRefreshRate) {
        mLastRefreshRate = newRefreshRate;
        if (mRefreshRateChangeCallback) {
            mRefreshRateChangeCallback(newRefreshRate);
        }
    }
}

int InstantRefreshRateCalculator::updateRefreshRate() {
    if (isOutdated(getNowNs())) {
        reset();
    }
    return NO_ERROR;
}

} // namespace android::hardware::graphics::composer
