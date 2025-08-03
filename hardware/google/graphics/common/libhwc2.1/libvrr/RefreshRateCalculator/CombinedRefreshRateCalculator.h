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

#include "../EventQueue.h"
#include "RefreshRateCalculator.h"

namespace android::hardware::graphics::composer {

class CombinedRefreshRateCalculator : public RefreshRateCalculator {
public:
    CombinedRefreshRateCalculator(
            std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators);

    CombinedRefreshRateCalculator(
            std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators,
            int minValidRefreshRate, int maxValidRefreshRate);

    int getRefreshRate() const override;

    void onPowerStateChange(int from, int to) final;

    void onPresent(int64_t presentTimeNs, int flag) override;

    void reset() override;

    void setEnabled(bool isEnabled) final;

private:
    static constexpr int kDefaultMinValidRefreshRate = 1;
    static constexpr int kDefaultMaxValidRefreshRate = 120;

    void onRefreshRateChanged(int refreshRate);

    void setNewRefreshRate(int newRefreshRate);

    void updateRefreshRate();

    std::vector<std::unique_ptr<RefreshRateCalculator>> mRefreshRateCalculators;

    VrrControllerEvent mMeasureEvent;

    int mMinValidRefreshRate;
    int mMaxValidRefreshRate;

    int mLastRefreshRate = kDefaultInvalidRefreshRate;

    bool mIsOnPresent = false;
    int mHasRefreshRateChage = false;
};

} // namespace android::hardware::graphics::composer
