/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <log/log.h>
#include <utils/Trace.h>

#include <algorithm>
#include <chrono>
#include <mutex>

#include "Vibrator.h"

constexpr int32_t DURATION_BUCKET_WIDTH = 50;
constexpr int32_t DURATION_50MS_BUCKET_COUNT = 20;
constexpr int32_t DURATION_BUCKET_COUNT = DURATION_50MS_BUCKET_COUNT + 1;
constexpr uint32_t MAX_TIME_MS = UINT16_MAX;

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

enum EffectLatency : uint16_t {
    kPrebakedEffectLatency = 0,
    kCompositionEffectLatency,
    kPwleEffectLatency,

    kEffectLatencyCount
};

enum VibratorError : uint16_t {
    kInitError = 0,
    kHwApiError,
    kHwCalError,
    kComposeFailError,
    kAlsaFailError,
    kAsyncFailError,
    kBadTimeoutError,
    kBadAmplitudeError,
    kBadEffectError,
    kBadEffectStrengthError,
    kBadPrimitiveError,
    kBadCompositeError,
    kPwleConstructionFailError,
    kUnsupportedOpError,

    kVibratorErrorCount
};

class StatsApi : public Vibrator::StatsApi {
  private:
    static constexpr uint32_t BASE_CONTINUOUS_EFFECT_OFFSET = 32768;
    enum WaveformIndex : uint16_t {
        /* Physical waveform */
        WAVEFORM_LONG_VIBRATION_EFFECT_INDEX = 0,
        WAVEFORM_RESERVED_INDEX_1 = 1,
        WAVEFORM_CLICK_INDEX = 2,
        WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX = 3,
        WAVEFORM_THUD_INDEX = 4,
        WAVEFORM_SPIN_INDEX = 5,
        WAVEFORM_QUICK_RISE_INDEX = 6,
        WAVEFORM_SLOW_RISE_INDEX = 7,
        WAVEFORM_QUICK_FALL_INDEX = 8,
        WAVEFORM_LIGHT_TICK_INDEX = 9,
        WAVEFORM_LOW_TICK_INDEX = 10,
        WAVEFORM_RESERVED_MFG_1,
        WAVEFORM_RESERVED_MFG_2,
        WAVEFORM_RESERVED_MFG_3,
        WAVEFORM_MAX_PHYSICAL_INDEX,
        /* OWT waveform */
        WAVEFORM_COMPOSE = WAVEFORM_MAX_PHYSICAL_INDEX,
        WAVEFORM_PWLE,
        /*
         * Refer to <linux/input.h>, the WAVEFORM_MAX_INDEX must not exceed 96.
         * #define FF_GAIN          0x60  // 96 in decimal
         * #define FF_MAX_EFFECTS   FF_GAIN
         */
        WAVEFORM_MAX_INDEX,
    };

  public:
    StatsApi() {
        mWaveformCounts = std::vector<int32_t>(WAVEFORM_MAX_INDEX, 0);
        mDurationCounts = std::vector<int32_t>(DURATION_BUCKET_COUNT, 0);
        mMinLatencies = std::vector<int32_t>(kEffectLatencyCount, 0);
        mMaxLatencies = std::vector<int32_t>(kEffectLatencyCount, 0);
        mLatencyTotals = std::vector<int32_t>(kEffectLatencyCount, 0);
        mLatencyCounts = std::vector<int32_t>(kEffectLatencyCount, 0);
        mErrorCounts = std::vector<int32_t>(kVibratorErrorCount, 0);
    }

    bool logPrimitive(uint16_t effectIndex) override {
        if (effectIndex >= WAVEFORM_MAX_PHYSICAL_INDEX ||
            effectIndex == WAVEFORM_LONG_VIBRATION_EFFECT_INDEX ||
            effectIndex == WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX) {
            ALOGE("Invalid waveform index for logging primitive: %d", effectIndex);
            return false;
        }

        {
            std::scoped_lock<std::mutex> lock(mDataAccess);
            mWaveformCounts[effectIndex]++;
        }

        return true;
    }

    bool logWaveform(uint16_t effectIndex, int32_t duration) override {
        if (effectIndex != WAVEFORM_LONG_VIBRATION_EFFECT_INDEX &&
            effectIndex != WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX + BASE_CONTINUOUS_EFFECT_OFFSET) {
            ALOGE("Invalid waveform index for logging waveform: %d", effectIndex);
            return false;
        } else if (effectIndex ==
                   WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX + BASE_CONTINUOUS_EFFECT_OFFSET) {
            effectIndex = WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX;
        }

        if (duration > MAX_TIME_MS || duration < 0) {
            ALOGE("Invalid waveform duration for logging waveform: %d", duration);
            return false;
        }

        {
            std::scoped_lock<std::mutex> lock(mDataAccess);
            mWaveformCounts[effectIndex]++;
            if (duration < DURATION_BUCKET_WIDTH * DURATION_50MS_BUCKET_COUNT) {
                mDurationCounts[duration / DURATION_BUCKET_WIDTH]++;
            } else {
                mDurationCounts[DURATION_50MS_BUCKET_COUNT]++;
            }
        }

        return true;
    }

    bool logError(uint16_t errorIndex) override {
        if (errorIndex >= kVibratorErrorCount) {
            ALOGE("Invalid index for logging error: %d", errorIndex);
            return false;
        }

        {
            std::scoped_lock<std::mutex> lock(mDataAccess);
            mErrorCounts[errorIndex]++;
        }

        return true;
    }

    bool logLatencyStart(uint16_t latencyIndex) override {
        if (latencyIndex >= kEffectLatencyCount) {
            ALOGE("Invalid index for measuring latency: %d", latencyIndex);
            return false;
        }

        mCurrentLatencyStart = std::chrono::steady_clock::now();
        mCurrentLatencyIndex = latencyIndex;

        return true;
    }

    bool logLatencyEnd() override {
        if (mCurrentLatencyIndex >= kEffectLatencyCount) {
            return false;
        }

        int32_t latency = (std::chrono::duration_cast<std::chrono::milliseconds>(
                                   std::chrono::steady_clock::now() - mCurrentLatencyStart))
                                  .count();

        {
            std::scoped_lock<std::mutex> lock(mDataAccess);
            if (latency < mMinLatencies[mCurrentLatencyIndex] ||
                mMinLatencies[mCurrentLatencyIndex] == 0) {
                mMinLatencies[mCurrentLatencyIndex] = latency;
            }
            if (latency > mMaxLatencies[mCurrentLatencyIndex]) {
                mMinLatencies[mCurrentLatencyIndex] = latency;
            }
            mLatencyTotals[mCurrentLatencyIndex] += latency;
            mLatencyCounts[mCurrentLatencyIndex]++;
        }

        mCurrentLatencyIndex = kEffectLatencyCount;
        return true;
    }

    void debug(int fd) override { (void)fd; }

  private:
    uint16_t mCurrentLatencyIndex;
    std::chrono::time_point<std::chrono::steady_clock> mCurrentLatencyStart;
    std::vector<int32_t> mWaveformCounts;
    std::vector<int32_t> mDurationCounts;
    std::vector<int32_t> mMinLatencies;
    std::vector<int32_t> mMaxLatencies;
    std::vector<int32_t> mLatencyTotals;
    std::vector<int32_t> mLatencyCounts;
    std::vector<int32_t> mErrorCounts;
    std::mutex mDataAccess;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
