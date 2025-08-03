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

#include "../EventQueue.h"
#include "../Utils.h"

namespace android::hardware::graphics::composer {

class InstantRefreshRateCalculator : public RefreshRateCalculator {
public:
    InstantRefreshRateCalculator(EventQueue* eventQueue);

    InstantRefreshRateCalculator(EventQueue* eventQueue, int64_t maxValidTimeNs);

    int getRefreshRate() const override;

    void onPresent(int64_t presentTimeNs, int flag) override;

    void reset() override;

    void setEnabled(bool isEnabled) final;

private:
    static constexpr int64_t kDefaultMaxValidTimeNs = 1000000000; // 1 second.

    InstantRefreshRateCalculator(const InstantRefreshRateCalculator&) = delete;
    InstantRefreshRateCalculator& operator=(const InstantRefreshRateCalculator&) = delete;

    bool isOutdated(int64_t timeNs) const;

    void setNewRefreshRate(int newRefreshRate);

    int updateRefreshRate();

    EventQueue* mEventQueue;
    VrrControllerEvent mTimeoutEvent;

    const int64_t mMaxValidTimeNs;

    int64_t mLastPresentTimeNs = kDefaultInvalidPresentTimeNs;
    int mLastRefreshRate = kDefaultInvalidRefreshRate;
};

} // namespace android::hardware::graphics::composer
