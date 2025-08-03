/*
 * Copyright (C) 2023 The Android Open Source Project
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

namespace android::hardware::graphics::composer {

class PresentListener {
public:
    virtual ~PresentListener() = default;

    virtual void setExpectedPresentTime(int64_t timestampNanos, int frameIntervalNs) = 0;

    virtual void onPresent(int32_t fence) = 0;
};

class VsyncListener {
public:
    virtual ~VsyncListener() = default;

    virtual void onVsync(int64_t timestamp, int32_t vsyncPeriodNanos) = 0;
};

class PowerModeListener {
public:
    virtual ~PowerModeListener() = default;

    virtual void onPowerStateChange(int from, int to) = 0;
};

} // namespace android::hardware::graphics::composer
