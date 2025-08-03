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

#include "Hardware.h"
#include "StatsBase.h"
#include "Vibrator.h"

constexpr int32_t DURATION_BUCKET_WIDTH = 50;
constexpr int32_t DURATION_50MS_BUCKET_COUNT = 20;
constexpr int32_t DURATION_BUCKET_COUNT = DURATION_50MS_BUCKET_COUNT + 1;
constexpr uint32_t MAX_TIME_MS = UINT16_MAX;

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (sizeof((x)) / sizeof((x)[0]))
#endif

#ifdef HAPTIC_TRACE
static const char *kWaveformLookup[] = {"WAVEFORM_LONG_VIBRATION_EFFECT",
                                        "WAVEFORM_RESERVED_1",
                                        "WAVEFORM_CLICK",
                                        "WAVEFORM_SHORT_VIBRATION_EFFECT",
                                        "WAVEFORM_THUD",
                                        "WAVEFORM_SPIN",
                                        "WAVEFORM_QUICK_RISE",
                                        "WAVEFORM_SLOW_RISE",
                                        "WAVEFORM_QUICK_FALL",
                                        "WAVEFORM_LIGHT_TICK",
                                        "WAVEFORM_LOW_TICK",
                                        "WAVEFORM_RESERVED_MFG_1",
                                        "WAVEFORM_RESERVED_MFG_2",
                                        "WAVEFORM_RESERVED_MFG_3",
                                        "WAVEFORM_COMPOSE",
                                        "WAVEFORM_PWLE",
                                        "INVALID"};
static const char *kLatencyLookup[] = {"kWaveformEffectLatency", "kPrebakedEffectLatency",
                                       "kCompositionEffectLatency", "kPwleEffectLatency",
                                       "INVALID"};
static const char *kErrorLookup[] = {"kInitError",
                                     "kHwApiError",
                                     "kHwCalError",
                                     "kComposeFailError",
                                     "kAlsaFailError",
                                     "kAsyncFailError",
                                     "kBadTimeoutError",
                                     "kBadAmplitudeError",
                                     "kBadEffectError",
                                     "kBadEffectStrengthError",
                                     "kBadPrimitiveError",
                                     "kBadCompositeError",
                                     "kPwleConstructionFailError",
                                     "kUnsupportedOpError",
                                     "INVALID"};

const char *waveformToString(uint16_t index) {
    return kWaveformLookup[(index < ARRAY_SIZE(kWaveformLookup)) ? index
                                                                 : ARRAY_SIZE(kWaveformLookup) - 1];
}

const char *latencyToString(uint16_t index) {
    return kLatencyLookup[(index < ARRAY_SIZE(kLatencyLookup)) ? index
                                                               : ARRAY_SIZE(kLatencyLookup) - 1];
}

const char *errorToString(uint16_t index) {
    return kErrorLookup[(index < ARRAY_SIZE(kErrorLookup)) ? index : ARRAY_SIZE(kErrorLookup) - 1];
}

#define STATS_TRACE(...)   \
    ATRACE_NAME(__func__); \
    ALOGD(__VA_ARGS__)
#else
#define STATS_TRACE(...) ATRACE_NAME(__func__)
#define waveformToString(x)
#define latencyToString(x)
#define errorToString(x)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

enum EffectLatency : uint16_t {
    kWaveformEffectLatency = 0,
    kPrebakedEffectLatency,
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

class StatsApi : public Vibrator::StatsApi, private StatsBase {
  public:
    StatsApi()
        : StatsBase(std::string(std::getenv("STATS_INSTANCE"))),
          mCurrentLatencyIndex(kEffectLatencyCount) {
        mWaveformCounts = std::vector<int32_t>(WAVEFORM_MAX_INDEX, 0);
        mDurationCounts = std::vector<int32_t>(DURATION_BUCKET_COUNT, 0);
        mMinLatencies = std::vector<int32_t>(kEffectLatencyCount, 0);
        mMaxLatencies = std::vector<int32_t>(kEffectLatencyCount, 0);
        mLatencyTotals = std::vector<int32_t>(kEffectLatencyCount, 0);
        mLatencyCounts = std::vector<int32_t>(kEffectLatencyCount, 0);
        mErrorCounts = std::vector<int32_t>(kVibratorErrorCount, 0);
    }

    bool logPrimitive(uint16_t effectIndex) override {
        STATS_TRACE("logPrimitive(effectIndex: %s)", waveformToString(effectIndex));

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
        STATS_TRACE("logWaveform(effectIndex: %s, duration: %d)", waveformToString(effectIndex),
                    duration);

        if (effectIndex != WAVEFORM_LONG_VIBRATION_EFFECT_INDEX &&
            effectIndex != WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX) {
            ALOGE("Invalid waveform index for logging waveform: %d", effectIndex);
            return false;
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
        STATS_TRACE("logError(errorIndex: %s)", errorToString(errorIndex));

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
        STATS_TRACE("logLatencyStart(latencyIndex: %s)", latencyToString(latencyIndex));

        if (latencyIndex >= kEffectLatencyCount) {
            ALOGE("Invalid index for measuring latency: %d", latencyIndex);
            return false;
        }

        mCurrentLatencyStart = std::chrono::steady_clock::now();
        mCurrentLatencyIndex = latencyIndex;

        return true;
    }

    bool logLatencyEnd() override {
        STATS_TRACE("logLatencyEnd()");

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
                mMaxLatencies[mCurrentLatencyIndex] = latency;
            }
            mLatencyTotals[mCurrentLatencyIndex] += latency;
            mLatencyCounts[mCurrentLatencyIndex]++;
        }

        mCurrentLatencyIndex = kEffectLatencyCount;
        return true;
    }

    void debug(int fd) override { StatsBase::debug(fd); }

  private:
    uint16_t mCurrentLatencyIndex;
    std::chrono::time_point<std::chrono::steady_clock> mCurrentLatencyStart;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
