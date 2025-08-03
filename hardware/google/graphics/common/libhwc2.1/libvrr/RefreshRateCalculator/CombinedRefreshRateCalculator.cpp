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

#include "CombinedRefreshRateCalculator.h"

#include <algorithm>

#include "../Utils.h"

namespace android::hardware::graphics::composer {

CombinedRefreshRateCalculator::CombinedRefreshRateCalculator(
        std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators)
      : CombinedRefreshRateCalculator(refreshRateCalculators, kDefaultMinValidRefreshRate,
                                      kDefaultMaxValidRefreshRate) {}

CombinedRefreshRateCalculator::CombinedRefreshRateCalculator(
        std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators,
        int minValidRefreshRate, int maxValidRefreshRate)
      : mRefreshRateCalculators(std::move(refreshRateCalculators)),
        mMinValidRefreshRate(minValidRefreshRate),
        mMaxValidRefreshRate(maxValidRefreshRate) {
    mName = "CombinedRefreshRateCalculator";
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        refreshRateCalculator->registerRefreshRateChangeCallback(
                std::bind(&CombinedRefreshRateCalculator::onRefreshRateChanged, this,
                          std::placeholders::_1));
    }
}

int CombinedRefreshRateCalculator::getRefreshRate() const {
    return mLastRefreshRate;
}

void CombinedRefreshRateCalculator::onPowerStateChange(int from, int to) {
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        refreshRateCalculator->onPowerStateChange(from, to);
    }
    mPowerMode = to;
}

void CombinedRefreshRateCalculator::onPresent(int64_t presentTimeNs, int flag) {
    mHasRefreshRateChage = false;

    mIsOnPresent = true;
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        refreshRateCalculator->onPresent(presentTimeNs, flag);
    }
    mIsOnPresent = false;

    if (mHasRefreshRateChage) {
        updateRefreshRate();
    }
}

void CombinedRefreshRateCalculator::reset() {
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        refreshRateCalculator->reset();
    }
    setNewRefreshRate(kDefaultInvalidRefreshRate);
    mHasRefreshRateChage = false;
}

void CombinedRefreshRateCalculator::setEnabled(bool isEnabled) {
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        refreshRateCalculator->setEnabled(isEnabled);
    }
}

void CombinedRefreshRateCalculator::onRefreshRateChanged(int refreshRate) {
    if (mIsOnPresent) {
        mHasRefreshRateChage = true;
    } else {
        updateRefreshRate();
    }
}

void CombinedRefreshRateCalculator::setNewRefreshRate(int newRefreshRate) {
    if (newRefreshRate != mLastRefreshRate) {
        mLastRefreshRate = newRefreshRate;
        if (mRefreshRateChangeCallback) {
            mRefreshRateChangeCallback(newRefreshRate);
        }
    }
}

void CombinedRefreshRateCalculator::updateRefreshRate() {
    int currentRefreshRate = kDefaultInvalidRefreshRate;
    for (auto& refreshRateCalculator : mRefreshRateCalculators) {
        auto refreshRate = refreshRateCalculator->getRefreshRate();
        if ((refreshRate >= mMinValidRefreshRate) && (refreshRate <= mMaxValidRefreshRate)) {
            currentRefreshRate = refreshRate;
            break;
        }
    }
    setNewRefreshRate(currentRefreshRate);
}

} // namespace android::hardware::graphics::composer
