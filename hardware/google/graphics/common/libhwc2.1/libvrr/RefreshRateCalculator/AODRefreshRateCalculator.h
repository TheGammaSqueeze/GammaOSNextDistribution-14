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

#include "RefreshRateCalculator.h"

#include "../Utils.h"

namespace android::hardware::graphics::composer {

class AODRefreshRateCalculator : public RefreshRateCalculator {
public:
    AODRefreshRateCalculator(EventQueue* eventQueue) : mEventQueue(eventQueue) {
        mResetRefreshRateEvent.mEventType = VrrControllerEventType::kAodRefreshRateCalculatorUpdate;
        mResetRefreshRateEvent.mFunctor = std::move(
                std::bind(&AODRefreshRateCalculator::changeRefreshRateDisplayState, this));
    }

    int getRefreshRate() const override {
        if (!mIsInDoze) {
            return kDefaultInvalidRefreshRate;
        }
        return mLastRefreshRate;
    }

    void onPresent(int64_t presentTimeNs, int flag) override {
        if (hasPresentFrameFlag(flag, PresentFrameFlag::kPresentingWhenDoze)) {
            mIsInDoze = true;
            if (mAodRefreshRateState != kAodActiveToIdleTransitionState) {
                setNewRefreshRate(kActiveRefreshRate);
                mEventQueue->dropEvent(VrrControllerEventType::kAodRefreshRateCalculatorUpdate);
                mResetRefreshRateEvent.mWhenNs = getNowNs() + kActiveRefreshRateDurationNs;
                mEventQueue->mPriorityQueue.emplace(mResetRefreshRateEvent);
                if (mAodRefreshRateState == kAodIdleRefreshRateState) {
                    changeRefreshRateDisplayState();
                }
            }
        } else {
            if (mIsInDoze) {
                // We are transitioning from doze mode to normal mode.
                reset();
                mIsInDoze = false;
            }
        }
    }

    void reset() override {
        setNewRefreshRate(kDefaultInvalidRefreshRate);
        mEventQueue->dropEvent(VrrControllerEventType::kAodRefreshRateCalculatorUpdate);
        mAodRefreshRateState = kAodIdleRefreshRateState;
    }

private:
    static constexpr int kDDICFrameInsertionNum = 8;
    static constexpr int kIdleRefreshRate = 1;
    static constexpr int kActiveRefreshRate = 30;
    static constexpr int kActiveFrameIntervalNs = (std::nano::den / 30);
    static constexpr int64_t kActiveRefreshRateDurationNs =
            kActiveFrameIntervalNs * kDDICFrameInsertionNum;
    static constexpr int kNumOfSkipRefreshRateUpdateFrames = 3;
    static constexpr int64_t kActiveToIdleTransitionDurationNs =
            kActiveFrameIntervalNs * kNumOfSkipRefreshRateUpdateFrames; // 33.33ms * 3 ~= 100ms

    enum AodRefreshRateState {
        kAodIdleRefreshRateState = 0,
        kAodActiveRefreshRateState,
        // State |kAodActiveToIdleTransitionState| is a special condition designed to prevent
        // looping issues. In this state, the refresh rate is initially set to idle (1 Hz).
        // Subsequently, during the subsequent |kActiveToIdleTransitionDurationNs| period, even if
        // new frames arrive, the refresh rate will not be changed to active. Finally, when the
        // timeout occurs, we return to the |kAodIdleRefreshRateState| state, ready to change the
        // refresh rate back to active (30Hz) again when new frames arrive.
        kAodActiveToIdleTransitionState,
    };

    void setNewRefreshRate(int newRefreshRate) {
        if (newRefreshRate != mLastRefreshRate) {
            mLastRefreshRate = newRefreshRate;
            if (mRefreshRateChangeCallback) {
                mRefreshRateChangeCallback(mLastRefreshRate);
            }
        }
    }

    int changeRefreshRateDisplayState() {
        if (mAodRefreshRateState == kAodIdleRefreshRateState) {
            mAodRefreshRateState = kAodActiveRefreshRateState;
        } else if (mAodRefreshRateState == kAodActiveRefreshRateState) {
            setNewRefreshRate(kIdleRefreshRate);
            mAodRefreshRateState = kAodActiveToIdleTransitionState;
            mResetRefreshRateEvent.mWhenNs = getNowNs() + kActiveToIdleTransitionDurationNs;
            mEventQueue->mPriorityQueue.emplace(mResetRefreshRateEvent);
        } else {
            mAodRefreshRateState = kAodIdleRefreshRateState;
        }
        return NO_ERROR;
    }

    EventQueue* mEventQueue;
    VrrControllerEvent mResetRefreshRateEvent;

    AodRefreshRateState mAodRefreshRateState = kAodIdleRefreshRateState;

    int mLastRefreshRate = kIdleRefreshRate;

    bool mIsInDoze = false;
};

} // namespace android::hardware::graphics::composer
