/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "Vibrator.h"

#include <android-base/properties.h>
#include <hardware/hardware.h>
#include <hardware/vibrator.h>
#include <log/log.h>
#include <stdio.h>
#include <utils/Trace.h>

#include <cinttypes>
#include <cmath>
#include <fstream>
#include <iostream>
#include <map>
#include <sstream>

#include "Stats.h"

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (sizeof((x)) / sizeof((x)[0]))
#endif

#define PROC_SND_PCM "/proc/asound/pcm"
#define HAPTIC_PCM_DEVICE_SYMBOL "haptic nohost playback"

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

#ifdef HAPTIC_TRACE
#define HAPTICS_TRACE(...) ALOGD(__VA_ARGS__)
#else
#define HAPTICS_TRACE(...)
#endif

static constexpr uint32_t BASE_CONTINUOUS_EFFECT_OFFSET = 32768;

static constexpr uint32_t WAVEFORM_EFFECT_0_20_LEVEL = 0;
static constexpr uint32_t WAVEFORM_EFFECT_1_00_LEVEL = 4;
static constexpr uint32_t WAVEFORM_EFFECT_LEVEL_MINIMUM = 4;

static constexpr uint32_t WAVEFORM_DOUBLE_CLICK_SILENCE_MS = 100;

static constexpr uint32_t WAVEFORM_LONG_VIBRATION_EFFECT_INDEX = 0;
static constexpr uint32_t WAVEFORM_LONG_VIBRATION_THRESHOLD_MS = 50;
static constexpr uint32_t WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX = 3 + BASE_CONTINUOUS_EFFECT_OFFSET;

static constexpr uint32_t WAVEFORM_CLICK_INDEX = 2;
static constexpr uint32_t WAVEFORM_THUD_INDEX = 4;
static constexpr uint32_t WAVEFORM_SPIN_INDEX = 5;
static constexpr uint32_t WAVEFORM_QUICK_RISE_INDEX = 6;
static constexpr uint32_t WAVEFORM_SLOW_RISE_INDEX = 7;
static constexpr uint32_t WAVEFORM_QUICK_FALL_INDEX = 8;
static constexpr uint32_t WAVEFORM_LIGHT_TICK_INDEX = 9;
static constexpr uint32_t WAVEFORM_LOW_TICK_INDEX = 10;

static constexpr uint32_t WAVEFORM_UNSAVED_TRIGGER_QUEUE_INDEX = 65529;
static constexpr uint32_t WAVEFORM_TRIGGER_QUEUE_INDEX = 65534;
static constexpr uint32_t VOLTAGE_GLOBAL_SCALE_LEVEL = 5;
static constexpr uint8_t VOLTAGE_SCALE_MAX = 100;

static constexpr int8_t MAX_COLD_START_LATENCY_MS = 6;  // I2C Transaction + DSP Return-From-Standby
static constexpr int8_t MAX_PAUSE_TIMING_ERROR_MS = 1;  // ALERT Irq Handling
static constexpr uint32_t MAX_TIME_MS = UINT32_MAX;

static constexpr float AMP_ATTENUATE_STEP_SIZE = 0.125f;
static constexpr float EFFECT_FREQUENCY_KHZ = 48.0f;

static constexpr auto ASYNC_COMPLETION_TIMEOUT = std::chrono::milliseconds(100);
static constexpr auto POLLING_TIMEOUT = 20;

static constexpr int32_t COMPOSE_DELAY_MAX_MS = 10000;
static constexpr int32_t COMPOSE_SIZE_MAX = 127;
static constexpr int32_t COMPOSE_PWLE_SIZE_LIMIT = 82;
static constexpr int32_t CS40L2X_PWLE_LENGTH_MAX = 4094;

// Measured resonant frequency, f0_measured, is represented by Q10.14 fixed
// point format on cs40l2x devices. The expression to calculate f0 is:
//   f0 = f0_measured / 2^Q14_BIT_SHIFT
// See the LRA Calibration Support documentation for more details.
static constexpr int32_t Q14_BIT_SHIFT = 14;

// Measured Q factor, q_measured, is represented by Q8.16 fixed
// point format on cs40l2x devices. The expression to calculate q is:
//   q = q_measured / 2^Q16_BIT_SHIFT
// See the LRA Calibration Support documentation for more details.
static constexpr int32_t Q16_BIT_SHIFT = 16;

// Measured ReDC, redc_measured, is represented by Q7.17 fixed
// point format on cs40l2x devices. The expression to calculate redc is:
//   redc = redc_measured * 5.857 / 2^Q17_BIT_SHIFT
// See the LRA Calibration Support documentation for more details.
static constexpr int32_t Q17_BIT_SHIFT = 17;

static constexpr int32_t COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS = 999;
static constexpr float PWLE_LEVEL_MIN = 0.0f;
static constexpr float PWLE_LEVEL_MAX = 1.0f;
static constexpr float CS40L2X_PWLE_LEVEL_MAX = 0.99f;
static constexpr float PWLE_FREQUENCY_RESOLUTION_HZ = 1.0f;
static constexpr float PWLE_FREQUENCY_MIN_HZ = 30.0f;
static constexpr float RESONANT_FREQUENCY_DEFAULT = 145.0f;
static constexpr float PWLE_FREQUENCY_MAX_HZ = 300.0f;
static constexpr float PWLE_BW_MAP_SIZE =
    1 + ((PWLE_FREQUENCY_MAX_HZ - PWLE_FREQUENCY_MIN_HZ) / PWLE_FREQUENCY_RESOLUTION_HZ);
static constexpr float RAMP_DOWN_CONSTANT = 1048.576f;
static constexpr float RAMP_DOWN_TIME_MS = 0.0f;

static struct pcm_config haptic_nohost_config = {
    .channels = 1,
    .rate = 48000,
    .period_size = 80,
    .period_count = 2,
    .format = PCM_FORMAT_S16_LE,
};

static uint8_t amplitudeToScale(float amplitude, float maximum) {
    return std::round((-20 * std::log10(amplitude / static_cast<float>(maximum))) /
                      (AMP_ATTENUATE_STEP_SIZE));
}

// Discrete points of frequency:max_level pairs as recommended by the document
#if defined(LUXSHARE_ICT_081545)
static std::map<float, float> discretePwleMaxLevels = {{120.0, 0.4},  {130.0, 0.31}, {140.0, 0.14},
                                                       {145.0, 0.09}, {150.0, 0.15}, {160.0, 0.35},
                                                       {170.0, 0.4}};
// Discrete points of frequency:max_level pairs as recommended by the document
#elif defined(LUXSHARE_ICT_LT_XLRA1906D)
static std::map<float, float> discretePwleMaxLevels = {{145.0, 0.38}, {150.0, 0.35}, {160.0, 0.35},
                                                       {170.0, 0.15}, {180.0, 0.35}, {190.0, 0.35},
                                                       {200.0, 0.38}};
#else
static std::map<float, float> discretePwleMaxLevels = {};
#endif

// Initialize all limits to 0.4 according to the document Max. Allowable Chirp Levels
#if defined(LUXSHARE_ICT_081545)
std::vector<float> pwleMaxLevelLimitMap(PWLE_BW_MAP_SIZE, 0.4);
// Initialize all limits to 0.38 according to the document Max. Allowable Chirp Levels
#elif defined(LUXSHARE_ICT_LT_XLRA1906D)
std::vector<float> pwleMaxLevelLimitMap(PWLE_BW_MAP_SIZE, 0.38);
#else
std::vector<float> pwleMaxLevelLimitMap(PWLE_BW_MAP_SIZE, 1.0);
#endif

void Vibrator::createPwleMaxLevelLimitMap() {
    HAPTICS_TRACE("createPwleMaxLevelLimitMap()");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_FREQUENCY_CONTROL) {
        std::map<float, float>::iterator itr0, itr1;

        if (discretePwleMaxLevels.empty()) {
            return;
        }
        if (discretePwleMaxLevels.size() == 1) {
            itr0 = discretePwleMaxLevels.begin();
            float pwleMaxLevelLimitMapIdx =
                    (itr0->first - PWLE_FREQUENCY_MIN_HZ) / PWLE_FREQUENCY_RESOLUTION_HZ;
            pwleMaxLevelLimitMap[pwleMaxLevelLimitMapIdx] = itr0->second;
            return;
        }

        itr0 = discretePwleMaxLevels.begin();
        itr1 = std::next(itr0, 1);

        while (itr1 != discretePwleMaxLevels.end()) {
            float x0 = itr0->first;
            float y0 = itr0->second;
            float x1 = itr1->first;
            float y1 = itr1->second;
            float pwleMaxLevelLimitMapIdx =
                    (itr0->first - PWLE_FREQUENCY_MIN_HZ) / PWLE_FREQUENCY_RESOLUTION_HZ;

            // FixLater: avoid floating point loop counters
            // NOLINTBEGIN(clang-analyzer-security.FloatLoopCounter,cert-flp30-c)
            for (float xp = x0; xp < (x1 + PWLE_FREQUENCY_RESOLUTION_HZ);
                 xp += PWLE_FREQUENCY_RESOLUTION_HZ) {
                // NOLINTEND(clang-analyzer-security.FloatLoopCounter,cert-flp30-c)
                float yp = y0 + ((y1 - y0) / (x1 - x0)) * (xp - x0);

                pwleMaxLevelLimitMap[pwleMaxLevelLimitMapIdx++] = yp;
            }

            itr0++;
            itr1++;
        }
    }
}

enum class AlwaysOnId : uint32_t {
    GPIO_RISE,
    GPIO_FALL,
};

Vibrator::Vibrator(std::unique_ptr<HwApi> hwapi, std::unique_ptr<HwCal> hwcal,
                   std::unique_ptr<StatsApi> statsapi)
    : mHwApi(std::move(hwapi)),
      mHwCal(std::move(hwcal)),
      mStatsApi(std::move(statsapi)),
      mAsyncHandle(std::async([] {})) {
    int32_t longFreqencyShift;
    uint32_t calVer;
    uint32_t caldata;
    uint32_t effectCount;

    if (!mHwApi->setState(true)) {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to set state (%d): %s", errno, strerror(errno));
    }

    if (mHwCal->getF0(&caldata)) {
        mHwApi->setF0(caldata);
        mResonantFrequency = static_cast<float>(caldata) / (1 << Q14_BIT_SHIFT);
    } else {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to get resonant frequency (%d): %s, using default resonant HZ: %f", errno,
              strerror(errno), RESONANT_FREQUENCY_DEFAULT);
        mResonantFrequency = RESONANT_FREQUENCY_DEFAULT;
    }
    if (mHwCal->getRedc(&caldata)) {
        mHwApi->setRedc(caldata);
        mRedc = caldata;
    }
    if (mHwCal->getQ(&caldata)) {
        mHwApi->setQ(caldata);
    }

    mHwCal->getLongFrequencyShift(&longFreqencyShift);
    if (longFreqencyShift > 0) {
        mF0Offset = longFreqencyShift * std::pow(2, 14);
    } else if (longFreqencyShift < 0) {
        mF0Offset = std::pow(2, 24) - std::abs(longFreqencyShift) * std::pow(2, 14);
    } else {
        mF0Offset = 0;
    }

    mHwCal->getVersion(&calVer);
    if (calVer == 1) {
        std::array<uint32_t, 6> volLevels;
        mHwCal->getVolLevels(&volLevels);
        /*
         * Given voltage levels for two intensities, assuming a linear function,
         * solve for 'f(0)' in 'v = f(i) = a + b * i' (i.e 'v0 - (v1 - v0) / ((i1 - i0) / i0)').
         */
        mClickEffectVol[0] = std::max(std::lround(volLevels[WAVEFORM_EFFECT_0_20_LEVEL] -
                                             (volLevels[WAVEFORM_EFFECT_1_00_LEVEL] -
                                              volLevels[WAVEFORM_EFFECT_0_20_LEVEL]) /
                                                     4.0f),
                                 static_cast<long>(WAVEFORM_EFFECT_LEVEL_MINIMUM));
        mClickEffectVol[1] = volLevels[WAVEFORM_EFFECT_1_00_LEVEL];
        mTickEffectVol = mClickEffectVol;
        mLongEffectVol[0] = 0;
        mLongEffectVol[1] = volLevels[VOLTAGE_GLOBAL_SCALE_LEVEL];
    } else {
        mHwCal->getTickVolLevels(&mTickEffectVol);
        mHwCal->getClickVolLevels(&mClickEffectVol);
        mHwCal->getLongVolLevels(&mLongEffectVol);
    }
    HAPTICS_TRACE("Vibrator(hwapi, hwcal:%u)", calVer);

    mHwApi->getEffectCount(&effectCount);
    mEffectDurations.resize(effectCount);
    for (size_t effectIndex = 0; effectIndex < effectCount; effectIndex++) {
        mHwApi->setEffectIndex(effectIndex);
        uint32_t effectDuration;
        if (mHwApi->getEffectDuration(&effectDuration)) {
            mEffectDurations[effectIndex] = std::ceil(effectDuration / EFFECT_FREQUENCY_KHZ);
        }
    }

    mHwApi->setClabEnable(true);

    if (!(getPwleCompositionSizeMax(&mCompositionSizeMax).isOk())) {
        mStatsApi->logError(kInitError);
        ALOGE("Failed to get pwle composition size max, using default size: %d",
              COMPOSE_PWLE_SIZE_LIMIT);
        mCompositionSizeMax = COMPOSE_PWLE_SIZE_LIMIT;
    }

    mIsChirpEnabled = mHwCal->isChirpEnabled();
    createPwleMaxLevelLimitMap();
    mGenerateBandwidthAmplitudeMapDone = false;
    mBandwidthAmplitudeMap = generateBandwidthAmplitudeMap();
    mIsUnderExternalControl = false;
    setPwleRampDown();
}

ndk::ScopedAStatus Vibrator::getCapabilities(int32_t *_aidl_return) {
    HAPTICS_TRACE("getCapabilities(_aidl_return)");
    ATRACE_NAME("Vibrator::getCapabilities");
    int32_t ret = IVibrator::CAP_ON_CALLBACK | IVibrator::CAP_PERFORM_CALLBACK |
                  IVibrator::CAP_COMPOSE_EFFECTS | IVibrator::CAP_ALWAYS_ON_CONTROL |
                  IVibrator::CAP_GET_RESONANT_FREQUENCY | IVibrator::CAP_GET_Q_FACTOR;
    if (mHwApi->hasEffectScale()) {
        ret |= IVibrator::CAP_AMPLITUDE_CONTROL;
    }
    if (mHwApi->hasAspEnable() || hasHapticAlsaDevice()) {
        ret |= IVibrator::CAP_EXTERNAL_CONTROL;
    }
    if (mHwApi->hasPwle() && mIsChirpEnabled) {
        ret |= IVibrator::CAP_FREQUENCY_CONTROL | IVibrator::CAP_COMPOSE_PWLE_EFFECTS;
    }
    *_aidl_return = ret;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::off() {
    HAPTICS_TRACE("off()");
    ATRACE_NAME("Vibrator::off");
    ALOGD("off");
    setGlobalAmplitude(false);
    mHwApi->setF0Offset(0);
    if (!mHwApi->setActivate(0)) {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to turn vibrator off (%d): %s", errno, strerror(errno));
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }
    mActiveId = -1;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::on(int32_t timeoutMs,
                                const std::shared_ptr<IVibratorCallback> &callback) {
    HAPTICS_TRACE("on(timeoutMs:%u, callback)", timeoutMs);
    ATRACE_NAME("Vibrator::on");
    ALOGD("on");
    mStatsApi->logLatencyStart(kWaveformEffectLatency);
    const uint32_t index = timeoutMs < WAVEFORM_LONG_VIBRATION_THRESHOLD_MS
                                   ? WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX
                                   : WAVEFORM_LONG_VIBRATION_EFFECT_INDEX;
    mStatsApi->logWaveform(index, timeoutMs);
    if (MAX_COLD_START_LATENCY_MS <= UINT32_MAX - timeoutMs) {
        timeoutMs += MAX_COLD_START_LATENCY_MS;
    }
    setGlobalAmplitude(true);
    mHwApi->setF0Offset(mF0Offset);
    return on(timeoutMs, index, callback);
}

ndk::ScopedAStatus Vibrator::perform(Effect effect, EffectStrength strength,
                                     const std::shared_ptr<IVibratorCallback> &callback,
                                     int32_t *_aidl_return) {
    HAPTICS_TRACE("perform(effect:%s, strength:%s, callback, _aidl_return)",
                  toString(effect).c_str(), toString(strength).c_str());
    ATRACE_NAME("Vibrator::perform");
    ALOGD("perform");

    mStatsApi->logLatencyStart(kPrebakedEffectLatency);

    return performEffect(effect, strength, callback, _aidl_return);
}

ndk::ScopedAStatus Vibrator::getSupportedEffects(std::vector<Effect> *_aidl_return) {
    HAPTICS_TRACE("getSupportedEffects(_aidl_return)");
    *_aidl_return = {Effect::TEXTURE_TICK, Effect::TICK, Effect::CLICK, Effect::HEAVY_CLICK,
                     Effect::DOUBLE_CLICK};
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::setAmplitude(float amplitude) {
    HAPTICS_TRACE("setAmplitude(amplitude:%f)", amplitude);
    ATRACE_NAME("Vibrator::setAmplitude");
    if (amplitude <= 0.0f || amplitude > 1.0f) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    if (!isUnderExternalControl()) {
        return setEffectAmplitude(amplitude, 1.0);
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::setExternalControl(bool enabled) {
    HAPTICS_TRACE("setExternalControl(enabled:%u)", enabled);
    ATRACE_NAME("Vibrator::setExternalControl");
    setGlobalAmplitude(enabled);

    if (isUnderExternalControl() == enabled) {
        if (enabled) {
            ALOGE("Restart the external process.");
            if (mHasHapticAlsaDevice) {
                if (!enableHapticPcmAmp(&mHapticPcm, !enabled, mCard, mDevice)) {
                    mStatsApi->logError(kAlsaFailError);
                    ALOGE("Failed to %s haptic pcm device: %d", (enabled ? "enable" : "disable"),
                          mDevice);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
                }
            }
            if (mHwApi->hasAspEnable()) {
                if (!mHwApi->setAspEnable(!enabled)) {
                    mStatsApi->logError(kHwApiError);
                    ALOGE("Failed to set external control (%d): %s", errno, strerror(errno));
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
                }
            }
        } else {
            ALOGE("The external control is already disabled.");
            return ndk::ScopedAStatus::ok();
        }
    }
    if (mHasHapticAlsaDevice) {
        if (!enableHapticPcmAmp(&mHapticPcm, enabled, mCard, mDevice)) {
            mStatsApi->logError(kAlsaFailError);
            ALOGE("Failed to %s haptic pcm device: %d", (enabled ? "enable" : "disable"), mDevice);
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
    }
    if (mHwApi->hasAspEnable()) {
        if (!mHwApi->setAspEnable(enabled)) {
            mStatsApi->logError(kHwApiError);
            ALOGE("Failed to set external control (%d): %s", errno, strerror(errno));
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
    }

    mIsUnderExternalControl = enabled;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getCompositionDelayMax(int32_t *maxDelayMs) {
    HAPTICS_TRACE("getCompositionDelayMax(maxDelayMs)");
    ATRACE_NAME("Vibrator::getCompositionDelayMax");
    *maxDelayMs = COMPOSE_DELAY_MAX_MS;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getCompositionSizeMax(int32_t *maxSize) {
    HAPTICS_TRACE("getCompositionSizeMax(maxSize)");
    ATRACE_NAME("Vibrator::getCompositionSizeMax");
    *maxSize = COMPOSE_SIZE_MAX;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getSupportedPrimitives(std::vector<CompositePrimitive> *supported) {
    HAPTICS_TRACE("getSupportedPrimitives(supported)");
    *supported = {
            CompositePrimitive::NOOP,       CompositePrimitive::CLICK,
            CompositePrimitive::THUD,       CompositePrimitive::SPIN,
            CompositePrimitive::QUICK_RISE, CompositePrimitive::SLOW_RISE,
            CompositePrimitive::QUICK_FALL, CompositePrimitive::LIGHT_TICK,
            CompositePrimitive::LOW_TICK,
    };
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getPrimitiveDuration(CompositePrimitive primitive,
                                                  int32_t *durationMs) {
    HAPTICS_TRACE("getPrimitiveDuration(primitive:%s, durationMs)", toString(primitive).c_str());
    ndk::ScopedAStatus status;
    uint32_t effectIndex;

    if (primitive != CompositePrimitive::NOOP) {
        status = getPrimitiveDetails(primitive, &effectIndex);
        if (!status.isOk()) {
            return status;
        }

        *durationMs = mEffectDurations[effectIndex];
    } else {
        *durationMs = 0;
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::compose(const std::vector<CompositeEffect> &composite,
                                     const std::shared_ptr<IVibratorCallback> &callback) {
    HAPTICS_TRACE("compose(composite, callback)");
    ATRACE_NAME("Vibrator::compose");
    ALOGD("compose");
    std::ostringstream effectBuilder;
    std::string effectQueue;

    mStatsApi->logLatencyStart(kCompositionEffectLatency);

    if (composite.size() > COMPOSE_SIZE_MAX) {
        mStatsApi->logError(kBadCompositeError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);

    // Reset the mTotalDuration
    mTotalDuration = 0;
    for (auto &e : composite) {
        if (e.scale < 0.0f || e.scale > 1.0f) {
            mStatsApi->logError(kBadCompositeError);
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
        }

        if (e.delayMs) {
            if (e.delayMs > COMPOSE_DELAY_MAX_MS) {
                mStatsApi->logError(kBadCompositeError);
                return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
            }
            effectBuilder << e.delayMs << ",";
            mTotalDuration += e.delayMs;
        }
        if (e.primitive != CompositePrimitive::NOOP) {
            ndk::ScopedAStatus status;
            uint32_t effectIndex;

            status = getPrimitiveDetails(e.primitive, &effectIndex);
            mStatsApi->logPrimitive(effectIndex);
            if (!status.isOk()) {
                mStatsApi->logError(kBadCompositeError);
                return status;
            }

            effectBuilder << effectIndex << "." << intensityToVolLevel(e.scale, effectIndex) << ",";
            mTotalDuration += mEffectDurations[effectIndex];
        }
    }

    if (effectBuilder.tellp() == 0) {
        mStatsApi->logError(kComposeFailError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    effectBuilder << 0;

    effectQueue = effectBuilder.str();

    return performEffect(0 /*ignored*/, 0 /*ignored*/, &effectQueue, callback);
}

ndk::ScopedAStatus Vibrator::on(uint32_t timeoutMs, uint32_t effectIndex,
                                const std::shared_ptr<IVibratorCallback> &callback) {
    HAPTICS_TRACE("on(timeoutMs:%u, effectIndex:%u, callback)", timeoutMs, effectIndex);
    if (isUnderExternalControl()) {
        setExternalControl(false);
        ALOGE("Device is under external control mode. Force to disable it to prevent chip hang "
              "problem.");
    }
    if (mAsyncHandle.wait_for(ASYNC_COMPLETION_TIMEOUT) != std::future_status::ready) {
        mStatsApi->logError(kAsyncFailError);
        ALOGE("Previous vibration pending: prev: %d, curr: %d", mActiveId, effectIndex);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    ALOGD("on");
    mHwApi->setEffectIndex(effectIndex);
    mHwApi->setDuration(timeoutMs);
    mStatsApi->logLatencyEnd();
    mHwApi->setActivate(1);
    // Using the mToalDuration for composed effect.
    // For composed effect, we set the UINT32_MAX to the duration sysfs node,
    // but it not a practical way to use it to monitor the total duration time.
    if (timeoutMs != UINT32_MAX) {
        const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);
        mTotalDuration = timeoutMs;
    }

    mActiveId = effectIndex;

    mAsyncHandle = std::async(&Vibrator::waitForComplete, this, callback);

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::setEffectAmplitude(float amplitude, float maximum) {
    HAPTICS_TRACE("setEffectAmplitude(amplitude:%f, maximum:%f)", amplitude, maximum);
    int32_t scale = amplitudeToScale(amplitude, maximum);

    if (!mHwApi->setEffectScale(scale)) {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to set effect amplitude (%d): %s", errno, strerror(errno));
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::setGlobalAmplitude(bool set) {
    HAPTICS_TRACE("setGlobalAmplitude(set:%u)", set);
    uint8_t amplitude = set ? mLongEffectVol[1] : VOLTAGE_SCALE_MAX;
    int32_t scale = amplitudeToScale(amplitude, VOLTAGE_SCALE_MAX);

    if (!mHwApi->setGlobalScale(scale)) {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to set global amplitude (%d): %s", errno, strerror(errno));
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getSupportedAlwaysOnEffects(std::vector<Effect> *_aidl_return) {
    HAPTICS_TRACE("getSupportedAlwaysOnEffects(_aidl_return)");
    *_aidl_return = {Effect::TEXTURE_TICK, Effect::TICK, Effect::CLICK, Effect::HEAVY_CLICK};
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::alwaysOnEnable(int32_t id, Effect effect, EffectStrength strength) {
    HAPTICS_TRACE("alwaysOnEnable(id:%d, effect:%s, strength:%s)", id, toString(effect).c_str(),
                  toString(strength).c_str());
    ndk::ScopedAStatus status;
    uint32_t effectIndex;
    uint32_t timeMs;
    uint32_t volLevel;
    uint32_t scale;

    status = getSimpleDetails(effect, strength, &effectIndex, &timeMs, &volLevel);
    if (!status.isOk()) {
        return status;
    }

    scale = amplitudeToScale(volLevel, VOLTAGE_SCALE_MAX);

    switch (static_cast<AlwaysOnId>(id)) {
        case AlwaysOnId::GPIO_RISE:
            mHwApi->setGpioRiseIndex(effectIndex);
            mHwApi->setGpioRiseScale(scale);
            return ndk::ScopedAStatus::ok();
        case AlwaysOnId::GPIO_FALL:
            mHwApi->setGpioFallIndex(effectIndex);
            mHwApi->setGpioFallScale(scale);
            return ndk::ScopedAStatus::ok();
    }

    mStatsApi->logError(kUnsupportedOpError);
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Vibrator::alwaysOnDisable(int32_t id) {
    HAPTICS_TRACE("alwaysOnDisable(id: %d)", id);
    switch (static_cast<AlwaysOnId>(id)) {
        case AlwaysOnId::GPIO_RISE:
            mHwApi->setGpioRiseIndex(0);
            return ndk::ScopedAStatus::ok();
        case AlwaysOnId::GPIO_FALL:
            mHwApi->setGpioFallIndex(0);
            return ndk::ScopedAStatus::ok();
    }

    mStatsApi->logError(kUnsupportedOpError);
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Vibrator::getResonantFrequency(float *resonantFreqHz) {
    HAPTICS_TRACE("getResonantFrequency(resonantFreqHz)");
    *resonantFreqHz = mResonantFrequency;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getQFactor(float *qFactor) {
    HAPTICS_TRACE("getQFactor(qFactor)");
    uint32_t caldata;
    if (!mHwCal->getQ(&caldata)) {
        mStatsApi->logError(kHwCalError);
        ALOGE("Failed to get q factor (%d): %s", errno, strerror(errno));
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }
    *qFactor = static_cast<float>(caldata) / (1 << Q16_BIT_SHIFT);

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getFrequencyResolution(float *freqResolutionHz) {
    HAPTICS_TRACE("getFrequencyResolution(freqResolutionHz)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_FREQUENCY_CONTROL) {
        *freqResolutionHz = PWLE_FREQUENCY_RESOLUTION_HZ;
        return ndk::ScopedAStatus::ok();
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::getFrequencyMinimum(float *freqMinimumHz) {
    HAPTICS_TRACE("getFrequencyMinimum(freqMinimumHz)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_FREQUENCY_CONTROL) {
        *freqMinimumHz = PWLE_FREQUENCY_MIN_HZ;
        return ndk::ScopedAStatus::ok();
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

static float redcToFloat(uint32_t redcMeasured) {
    HAPTICS_TRACE("redcToFloat(redcMeasured: %u)", redcMeasured);
    return redcMeasured * 5.857 / (1 << Q17_BIT_SHIFT);
}

std::vector<float> Vibrator::generateBandwidthAmplitudeMap() {
    HAPTICS_TRACE("generateBandwidthAmplitudeMap()");
    // Use constant Q Factor of 10 from HW's suggestion
    const float qFactor = 10.0f;
    const float blSys = 1.1f;
    const float gravity = 9.81f;
    const float maxVoltage = 12.3f;
    float deviceMass = 0, locCoeff = 0;

    mHwCal->getDeviceMass(&deviceMass);
    mHwCal->getLocCoeff(&locCoeff);
    if (!deviceMass || !locCoeff) {
        mStatsApi->logError(kInitError);
        ALOGE("Failed to get Device Mass: %f and Loc Coeff: %f", deviceMass, locCoeff);
        return std::vector<float>();
    }

    // Resistance value need to be retrieved from calibration file
    if (!mRedc) {
        mStatsApi->logError(kInitError);
        ALOGE("Failed to get redc");
        return std::vector<float>();
    }
    const float rSys = redcToFloat(mRedc);

    std::vector<float> bandwidthAmplitudeMap(PWLE_BW_MAP_SIZE, 1.0);

    const float wnSys = mResonantFrequency * 2 * M_PI;

    float frequencyHz = PWLE_FREQUENCY_MIN_HZ;
    float frequencyRadians = 0.0f;
    float vLevel = 0.4f;
    float vSys = (mLongEffectVol[1] / 100.0) * maxVoltage * vLevel;
    float maxAsys = 0;

    for (int i = 0; i < PWLE_BW_MAP_SIZE; i++) {
        frequencyRadians = frequencyHz * 2 * M_PI;
        vLevel = pwleMaxLevelLimitMap[i];
        vSys = (mLongEffectVol[1] / 100.0) * maxVoltage * vLevel;

        float var1 = pow((pow(wnSys, 2) - pow(frequencyRadians, 2)), 2);
        float var2 = pow((wnSys * frequencyRadians / qFactor), 2);

        float psysAbs = sqrt(var1 + var2);
        // The equation and all related details: b/170919640#comment5
        float amplitudeSys = (vSys * blSys * locCoeff / rSys / deviceMass) *
                             pow(frequencyRadians, 2) / psysAbs / gravity;
        // Record the maximum acceleration for the next for loop
        if (amplitudeSys > maxAsys)
            maxAsys = amplitudeSys;

        bandwidthAmplitudeMap[i] = amplitudeSys;
        frequencyHz += PWLE_FREQUENCY_RESOLUTION_HZ;
    }
    // Scaled the map between 0.00 and 1.00
    if (maxAsys > 0) {
        for (int j = 0; j < PWLE_BW_MAP_SIZE; j++) {
            bandwidthAmplitudeMap[j] = std::floor((bandwidthAmplitudeMap[j] / maxAsys) * 100) / 100;
        }
        mGenerateBandwidthAmplitudeMapDone = true;
    } else {
        return std::vector<float>();
    }

    return bandwidthAmplitudeMap;
}

ndk::ScopedAStatus Vibrator::getBandwidthAmplitudeMap(std::vector<float> *_aidl_return) {
    HAPTICS_TRACE("getBandwidthAmplitudeMap(_aidl_return)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_FREQUENCY_CONTROL) {
        if (!mGenerateBandwidthAmplitudeMapDone) {
            mBandwidthAmplitudeMap = generateBandwidthAmplitudeMap();
        }
        *_aidl_return = mBandwidthAmplitudeMap;
        return (!mBandwidthAmplitudeMap.empty())
                       ? ndk::ScopedAStatus::ok()
                       : ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::getPwlePrimitiveDurationMax(int32_t *durationMs) {
    HAPTICS_TRACE("getPwlePrimitiveDurationMax(durationMs)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_COMPOSE_PWLE_EFFECTS) {
        *durationMs = COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS;
        return ndk::ScopedAStatus::ok();
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::getPwleCompositionSizeMax(int32_t *maxSize) {
    HAPTICS_TRACE("getPwleCompositionSizeMax(maxSize)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_COMPOSE_PWLE_EFFECTS) {
        uint32_t segments;
        if (!mHwApi->getAvailablePwleSegments(&segments)) {
            mStatsApi->logError(kHwApiError);
            ALOGE("Failed to get availablePwleSegments (%d): %s", errno, strerror(errno));
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
        *maxSize = (segments > COMPOSE_PWLE_SIZE_LIMIT) ? COMPOSE_PWLE_SIZE_LIMIT : segments;
        mCompositionSizeMax = *maxSize;
        return ndk::ScopedAStatus::ok();
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::getSupportedBraking(std::vector<Braking> *supported) {
    HAPTICS_TRACE("getSupportedBraking(supported)");
    int32_t capabilities;
    Vibrator::getCapabilities(&capabilities);
    if (capabilities & IVibrator::CAP_COMPOSE_PWLE_EFFECTS) {
        *supported = {
            Braking::NONE,
            Braking::CLAB,
        };
        return ndk::ScopedAStatus::ok();
    } else {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
}

ndk::ScopedAStatus Vibrator::setPwle(const std::string &pwleQueue) {
    HAPTICS_TRACE("setPwle(pwleQueue:%s)", pwleQueue.c_str());
    if (!mHwApi->setPwle(pwleQueue)) {
        mStatsApi->logError(kHwApiError);
        ALOGE("Failed to write \"%s\" to pwle (%d): %s", pwleQueue.c_str(), errno, strerror(errno));
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}

static void incrementIndex(int *index) {
    *index += 1;
}

static void constructActiveDefaults(std::ostringstream &pwleBuilder, const int &segmentIdx) {
    HAPTICS_TRACE("constructActiveDefaults(pwleBuilder, segmentIdx:%d)", segmentIdx);
    pwleBuilder << ",C" << segmentIdx << ":1";
    pwleBuilder << ",B" << segmentIdx << ":0";
    pwleBuilder << ",AR" << segmentIdx << ":0";
    pwleBuilder << ",V" << segmentIdx << ":0";
}

static void constructActiveSegment(std::ostringstream &pwleBuilder, const int &segmentIdx,
                                   int duration, float amplitude, float frequency) {
    HAPTICS_TRACE(
            "constructActiveSegment(pwleBuilder, segmentIdx:%d, duration:%d, amplitude:%f, "
            "frequency:%f)",
            segmentIdx, duration, amplitude, frequency);
    pwleBuilder << ",T" << segmentIdx << ":" << duration;
    pwleBuilder << ",L" << segmentIdx << ":" << std::setprecision(1) << amplitude;
    pwleBuilder << ",F" << segmentIdx << ":" << std::lroundf(frequency);
    constructActiveDefaults(pwleBuilder, segmentIdx);
}

static void constructBrakingSegment(std::ostringstream &pwleBuilder, const int &segmentIdx,
                                    int duration, Braking brakingType, float frequency) {
    HAPTICS_TRACE(
            "constructActiveSegment(pwleBuilder, segmentIdx:%d, duration:%d, brakingType:%s, "
            "frequency:%f)",
            segmentIdx, duration, toString(brakingType).c_str(), frequency);
    pwleBuilder << ",T" << segmentIdx << ":" << duration;
    pwleBuilder << ",L" << segmentIdx << ":" << 0;
    pwleBuilder << ",F" << segmentIdx << ":" << std::lroundf(frequency);
    pwleBuilder << ",C" << segmentIdx << ":0";
    pwleBuilder << ",B" << segmentIdx << ":"
                << static_cast<std::underlying_type<Braking>::type>(brakingType);
    pwleBuilder << ",AR" << segmentIdx << ":0";
    pwleBuilder << ",V" << segmentIdx << ":0";
}

ndk::ScopedAStatus Vibrator::composePwle(const std::vector<PrimitivePwle> &composite,
                                         const std::shared_ptr<IVibratorCallback> &callback) {
    HAPTICS_TRACE("composePwle(composite, callback)");
    ATRACE_NAME("Vibrator::composePwle");
    std::ostringstream pwleBuilder;
    std::string pwleQueue;

    mStatsApi->logLatencyStart(kPwleEffectLatency);

    if (!mIsChirpEnabled) {
        mStatsApi->logError(kUnsupportedOpError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    if (composite.size() <= 0 || composite.size() > mCompositionSizeMax) {
        mStatsApi->logError(kBadCompositeError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    float prevEndAmplitude = 0;
    float prevEndFrequency = mResonantFrequency;

    int segmentIdx = 0;
    uint32_t totalDuration = 0;

    pwleBuilder << "S:0,WF:4,RP:0,WT:0";

    for (auto &e : composite) {
        switch (e.getTag()) {
            case PrimitivePwle::active: {
                auto active = e.get<PrimitivePwle::active>();
                if (active.duration < 0 ||
                    active.duration > COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS) {
                    mStatsApi->logError(kBadCompositeError);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
                }
                if (active.startAmplitude < PWLE_LEVEL_MIN ||
                    active.startAmplitude > PWLE_LEVEL_MAX ||
                    active.endAmplitude < PWLE_LEVEL_MIN || active.endAmplitude > PWLE_LEVEL_MAX) {
                    mStatsApi->logError(kBadCompositeError);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
                }
                if (active.startAmplitude > CS40L2X_PWLE_LEVEL_MAX) {
                    active.startAmplitude = CS40L2X_PWLE_LEVEL_MAX;
                }
                if (active.endAmplitude > CS40L2X_PWLE_LEVEL_MAX) {
                    active.endAmplitude = CS40L2X_PWLE_LEVEL_MAX;
                }

                if (active.startFrequency < PWLE_FREQUENCY_MIN_HZ ||
                    active.startFrequency > PWLE_FREQUENCY_MAX_HZ ||
                    active.endFrequency < PWLE_FREQUENCY_MIN_HZ ||
                    active.endFrequency > PWLE_FREQUENCY_MAX_HZ) {
                    mStatsApi->logError(kBadCompositeError);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
                }

                // clip to the hard limit on input level from pwleMaxLevelLimitMap
                float maxLevelLimit =
                    pwleMaxLevelLimitMap[active.startFrequency / PWLE_FREQUENCY_RESOLUTION_HZ - 1];
                if (active.startAmplitude > maxLevelLimit) {
                    active.startAmplitude = maxLevelLimit;
                }
                maxLevelLimit =
                    pwleMaxLevelLimitMap[active.endFrequency / PWLE_FREQUENCY_RESOLUTION_HZ - 1];
                if (active.endAmplitude > maxLevelLimit) {
                    active.endAmplitude = maxLevelLimit;
                }

                if (!((active.startAmplitude == prevEndAmplitude) &&
                      (active.startFrequency == prevEndFrequency))) {
                    constructActiveSegment(pwleBuilder, segmentIdx, 0, active.startAmplitude,
                                           active.startFrequency);
                    incrementIndex(&segmentIdx);
                }

                constructActiveSegment(pwleBuilder, segmentIdx, active.duration,
                                       active.endAmplitude, active.endFrequency);
                incrementIndex(&segmentIdx);

                prevEndAmplitude = active.endAmplitude;
                prevEndFrequency = active.endFrequency;
                totalDuration += active.duration;
                break;
            }
            case PrimitivePwle::braking: {
                auto braking = e.get<PrimitivePwle::braking>();
                if (braking.braking > Braking::CLAB) {
                    mStatsApi->logError(kBadPrimitiveError);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
                }
                if (braking.duration > COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS) {
                    mStatsApi->logError(kBadPrimitiveError);
                    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
                }

                constructBrakingSegment(pwleBuilder, segmentIdx, braking.duration, braking.braking,
                                        prevEndFrequency);
                incrementIndex(&segmentIdx);

                prevEndAmplitude = 0;
                totalDuration += braking.duration;
                break;
            }
        }
    }

    pwleQueue = pwleBuilder.str();
    ALOGD("composePwle queue: (%s)", pwleQueue.c_str());

    if (pwleQueue.size() > CS40L2X_PWLE_LENGTH_MAX) {
        ALOGE("PWLE string too large(%u)", static_cast<uint32_t>(pwleQueue.size()));
        mStatsApi->logError(kPwleConstructionFailError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    } else {
        ALOGD("PWLE string : %u", static_cast<uint32_t>(pwleQueue.size()));
        ndk::ScopedAStatus status = setPwle(pwleQueue);
        if (!status.isOk()) {
            mStatsApi->logError(kPwleConstructionFailError);
            ALOGE("Failed to write pwle queue");
            return status;
        }
    }
    setEffectAmplitude(VOLTAGE_SCALE_MAX, VOLTAGE_SCALE_MAX);
    mHwApi->setEffectIndex(WAVEFORM_UNSAVED_TRIGGER_QUEUE_INDEX);

    totalDuration += MAX_COLD_START_LATENCY_MS;
    mHwApi->setDuration(totalDuration);
    {
        const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);
        mTotalDuration = totalDuration;
    }

    mStatsApi->logLatencyEnd();
    mHwApi->setActivate(1);

    mAsyncHandle = std::async(&Vibrator::waitForComplete, this, callback);

    return ndk::ScopedAStatus::ok();
}

bool Vibrator::isUnderExternalControl() {
    HAPTICS_TRACE("isUnderExternalControl()");
    return mIsUnderExternalControl;
}

binder_status_t Vibrator::dump(int fd, const char **args, uint32_t numArgs) {
    HAPTICS_TRACE("dump(fd:%d, args, numArgs:%u)", fd, numArgs);
    if (fd < 0) {
        ALOGE("Called debug() with invalid fd.");
        return STATUS_OK;
    }

    (void)args;
    (void)numArgs;

    dprintf(fd, "AIDL:\n");

    dprintf(fd, "  F0 Offset: %" PRIu32 "\n", mF0Offset);

    dprintf(fd, "  Voltage Levels:\n");
    dprintf(fd, "    Tick Effect Min: %" PRIu32 " Max: %" PRIu32 "\n",
            mTickEffectVol[0], mTickEffectVol[1]);
    dprintf(fd, "    Click Effect Min: %" PRIu32 " Max: %" PRIu32 "\n",
            mClickEffectVol[0], mClickEffectVol[1]);
    dprintf(fd, "    Long Effect Min: %" PRIu32 " Max: %" PRIu32 "\n",
            mLongEffectVol[0], mLongEffectVol[1]);

    dprintf(fd, "  Effect Durations:");
    for (auto d : mEffectDurations) {
        dprintf(fd, " %" PRIu32, d);
    }
    dprintf(fd, "\n");

    dprintf(fd, "\n");

    mHwApi->debug(fd);

    dprintf(fd, "\n");

    mHwCal->debug(fd);

    dprintf(fd, "\n");

    mStatsApi->debug(fd);

    fsync(fd);
    return STATUS_OK;
}

ndk::ScopedAStatus Vibrator::getSimpleDetails(Effect effect, EffectStrength strength,
                                              uint32_t *outEffectIndex, uint32_t *outTimeMs,
                                              uint32_t *outVolLevel) {
    HAPTICS_TRACE(
            "getSimpleDetails(effect:%s, strength:%s, outEffectIndex, outTimeMs"
            ", outVolLevel)",
            toString(effect).c_str(), toString(strength).c_str());
    uint32_t effectIndex;
    uint32_t timeMs;
    float intensity;
    uint32_t volLevel;

    switch (strength) {
        case EffectStrength::LIGHT:
            intensity = 0.5f;
            break;
        case EffectStrength::MEDIUM:
            intensity = 0.7f;
            break;
        case EffectStrength::STRONG:
            intensity = 1.0f;
            break;
        default:
            mStatsApi->logError(kUnsupportedOpError);
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    switch (effect) {
        case Effect::TEXTURE_TICK:
            effectIndex = WAVEFORM_LIGHT_TICK_INDEX;
            intensity *= 0.5f;
            break;
        case Effect::TICK:
            effectIndex = WAVEFORM_CLICK_INDEX;
            intensity *= 0.5f;
            break;
        case Effect::CLICK:
            effectIndex = WAVEFORM_CLICK_INDEX;
            intensity *= 0.7f;
            break;
        case Effect::HEAVY_CLICK:
            effectIndex = WAVEFORM_CLICK_INDEX;
            intensity *= 1.0f;
            break;
        default:
            mStatsApi->logError(kUnsupportedOpError);
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    volLevel = intensityToVolLevel(intensity, effectIndex);
    timeMs = mEffectDurations[effectIndex] + MAX_COLD_START_LATENCY_MS;
    {
        const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);
        mTotalDuration = timeMs;
    }

    *outEffectIndex = effectIndex;
    *outTimeMs = timeMs;
    *outVolLevel = volLevel;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getCompoundDetails(Effect effect, EffectStrength strength,
                                                uint32_t *outTimeMs, uint32_t * /*outVolLevel*/,
                                                std::string *outEffectQueue) {
    HAPTICS_TRACE(
            "getCompoundDetails(effect:%s, strength:%s, outTimeMs, outVolLevel, outEffectQueue)",
            toString(effect).c_str(), toString(strength).c_str());
    ndk::ScopedAStatus status;
    uint32_t timeMs;
    std::ostringstream effectBuilder;
    uint32_t thisEffectIndex;
    uint32_t thisTimeMs;
    uint32_t thisVolLevel;

    switch (effect) {
        case Effect::DOUBLE_CLICK:
            timeMs = 0;

            status = getSimpleDetails(Effect::CLICK, strength, &thisEffectIndex, &thisTimeMs,
                                      &thisVolLevel);
            if (!status.isOk()) {
                return status;
            }
            effectBuilder << thisEffectIndex << "." << thisVolLevel;
            timeMs += thisTimeMs;

            effectBuilder << ",";

            effectBuilder << WAVEFORM_DOUBLE_CLICK_SILENCE_MS;
            timeMs += WAVEFORM_DOUBLE_CLICK_SILENCE_MS + MAX_PAUSE_TIMING_ERROR_MS;

            effectBuilder << ",";

            status = getSimpleDetails(Effect::HEAVY_CLICK, strength, &thisEffectIndex, &thisTimeMs,
                                      &thisVolLevel);
            if (!status.isOk()) {
                return status;
            }
            effectBuilder << thisEffectIndex << "." << thisVolLevel;
            timeMs += thisTimeMs;
            {
                const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);
                mTotalDuration = timeMs;
            }

            break;
        default:
            mStatsApi->logError(kUnsupportedOpError);
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    *outTimeMs = timeMs;
    *outEffectQueue = effectBuilder.str();

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getPrimitiveDetails(CompositePrimitive primitive,
                                                 uint32_t *outEffectIndex) {
    HAPTICS_TRACE("getPrimitiveDetails(primitive:%s, outEffectIndex)", toString(primitive).c_str());
    uint32_t effectIndex;

    switch (primitive) {
        case CompositePrimitive::NOOP:
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
        case CompositePrimitive::CLICK:
            effectIndex = WAVEFORM_CLICK_INDEX;
            break;
        case CompositePrimitive::THUD:
            effectIndex = WAVEFORM_THUD_INDEX;
            break;
        case CompositePrimitive::SPIN:
            effectIndex = WAVEFORM_SPIN_INDEX;
            break;
        case CompositePrimitive::QUICK_RISE:
            effectIndex = WAVEFORM_QUICK_RISE_INDEX;
            break;
        case CompositePrimitive::SLOW_RISE:
            effectIndex = WAVEFORM_SLOW_RISE_INDEX;
            break;
        case CompositePrimitive::QUICK_FALL:
            effectIndex = WAVEFORM_QUICK_FALL_INDEX;
            break;
        case CompositePrimitive::LIGHT_TICK:
            effectIndex = WAVEFORM_LIGHT_TICK_INDEX;
            break;
        case CompositePrimitive::LOW_TICK:
            effectIndex = WAVEFORM_LOW_TICK_INDEX;
            break;
        default:
            mStatsApi->logError(kUnsupportedOpError);
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    *outEffectIndex = effectIndex;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::setEffectQueue(const std::string &effectQueue) {
    HAPTICS_TRACE("setEffectQueue(effectQueue:%s)", effectQueue.c_str());
    if (!mHwApi->setEffectQueue(effectQueue)) {
        ALOGE("Failed to write \"%s\" to effect queue (%d): %s", effectQueue.c_str(), errno,
              strerror(errno));
        mStatsApi->logError(kHwApiError);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::performEffect(Effect effect, EffectStrength strength,
                                           const std::shared_ptr<IVibratorCallback> &callback,
                                           int32_t *outTimeMs) {
    HAPTICS_TRACE("performEffect(effect:%s, strength:%s, callback, outTimeMs)",
                  toString(effect).c_str(), toString(strength).c_str());
    ndk::ScopedAStatus status;
    uint32_t effectIndex;
    uint32_t timeMs = 0;
    uint32_t volLevel;
    std::string effectQueue;

    switch (effect) {
        case Effect::TEXTURE_TICK:
            // fall-through
        case Effect::TICK:
            // fall-through
        case Effect::CLICK:
            // fall-through
        case Effect::HEAVY_CLICK:
            status = getSimpleDetails(effect, strength, &effectIndex, &timeMs, &volLevel);
            break;
        case Effect::DOUBLE_CLICK:
            status = getCompoundDetails(effect, strength, &timeMs, &volLevel, &effectQueue);
            break;
        default:
            mStatsApi->logError(kUnsupportedOpError);
            status = ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
            break;
    }
    if (!status.isOk()) {
        goto exit;
    }

    status = performEffect(effectIndex, volLevel, &effectQueue, callback);

exit:

    *outTimeMs = timeMs;
    return status;
}

ndk::ScopedAStatus Vibrator::performEffect(uint32_t effectIndex, uint32_t volLevel,
                                           const std::string *effectQueue,
                                           const std::shared_ptr<IVibratorCallback> &callback) {
    HAPTICS_TRACE("performEffect(effectIndex:%u, volLevel:%u, effectQueue:%s, callback)",
                  effectIndex, volLevel, effectQueue->c_str());
    if (effectQueue && !effectQueue->empty()) {
        ndk::ScopedAStatus status = setEffectQueue(*effectQueue);
        if (!status.isOk()) {
            return status;
        }
        setEffectAmplitude(VOLTAGE_SCALE_MAX, VOLTAGE_SCALE_MAX);
        effectIndex = WAVEFORM_TRIGGER_QUEUE_INDEX;
    } else {
        setEffectAmplitude(volLevel, VOLTAGE_SCALE_MAX);
    }

    return on(MAX_TIME_MS, effectIndex, callback);
}

void Vibrator::waitForComplete(std::shared_ptr<IVibratorCallback> &&callback) {
    HAPTICS_TRACE("waitForComplete(callback)");
    ALOGD("waitForComplete");
    uint32_t duration;
    {
        const std::scoped_lock<std::mutex> lock(mTotalDurationMutex);
        duration = ((mTotalDuration + POLLING_TIMEOUT) < UINT32_MAX)
                           ? mTotalDuration + POLLING_TIMEOUT
                           : UINT32_MAX;
    }
    if (!mHwApi->pollVibeState(false, duration)) {
        ALOGE("Timeout(%u)! Fail to poll STOP state", duration);
    } else {
        ALOGD("waitForComplete: Get STOP! Set active to 0.");
    }
    mHwApi->setActivate(false);

    if (callback) {
        auto ret = callback->onComplete();
        if (!ret.isOk()) {
            mStatsApi->logError(kAsyncFailError);
            ALOGE("Failed completion callback: %d", ret.getExceptionCode());
        }
    }
}

uint32_t Vibrator::intensityToVolLevel(float intensity, uint32_t effectIndex) {
    HAPTICS_TRACE("intensityToVolLevel(intensity:%f, effectIndex:%u)", intensity, effectIndex);
    uint32_t volLevel;
    auto calc = [](float intst, std::array<uint32_t, 2> v) -> uint32_t {
                return std::lround(intst * (v[1] - v[0])) + v[0]; };

    switch (effectIndex) {
        case WAVEFORM_LIGHT_TICK_INDEX:
            volLevel = calc(intensity, mTickEffectVol);
            break;
        case WAVEFORM_QUICK_RISE_INDEX:
            // fall-through
        case WAVEFORM_QUICK_FALL_INDEX:
            volLevel = calc(intensity, mLongEffectVol);
            break;
        case WAVEFORM_CLICK_INDEX:
            // fall-through
        case WAVEFORM_THUD_INDEX:
            // fall-through
        case WAVEFORM_SPIN_INDEX:
            // fall-through
        case WAVEFORM_SLOW_RISE_INDEX:
            // fall-through
        default:
            volLevel = calc(intensity, mClickEffectVol);
            break;
    }

    return volLevel;
}

bool Vibrator::findHapticAlsaDevice(int *card, int *device) {
    HAPTICS_TRACE("findHapticAlsaDevice(card, device)");
    std::string line;
    std::ifstream myfile(PROC_SND_PCM);
    if (myfile.is_open()) {
        while (getline(myfile, line)) {
            if (line.find(HAPTIC_PCM_DEVICE_SYMBOL) != std::string::npos) {
                std::stringstream ss(line);
                std::string currentToken;
                std::getline(ss, currentToken, ':');
                sscanf(currentToken.c_str(), "%d-%d", card, device);
                return true;
            }
        }
        myfile.close();
    } else {
        mStatsApi->logError(kAlsaFailError);
        ALOGE("Failed to read file: %s", PROC_SND_PCM);
    }
    return false;
}

bool Vibrator::hasHapticAlsaDevice() {
    HAPTICS_TRACE("hasHapticAlsaDevice()");
    // We need to call findHapticAlsaDevice once only. Calling in the
    // constructor is too early in the boot process and the pcm file contents
    // are empty. Hence we make the call here once only right before we need to.
    static bool configHapticAlsaDeviceDone = false;
    if (!configHapticAlsaDeviceDone) {
        if (findHapticAlsaDevice(&mCard, &mDevice)) {
            mHasHapticAlsaDevice = true;
            configHapticAlsaDeviceDone = true;
        } else {
            mStatsApi->logError(kAlsaFailError);
            ALOGE("Haptic ALSA device not supported");
        }
    }
    return mHasHapticAlsaDevice;
}

bool Vibrator::enableHapticPcmAmp(struct pcm **haptic_pcm, bool enable, int card, int device) {
    HAPTICS_TRACE("enableHapticPcmAmp(pcm, enable:%u, card:%d, device:%d)", enable, card, device);
    int ret = 0;

    if (enable) {
        *haptic_pcm = pcm_open(card, device, PCM_OUT, &haptic_nohost_config);
        if (!pcm_is_ready(*haptic_pcm)) {
            ALOGE("cannot open pcm_out driver: %s", pcm_get_error(*haptic_pcm));
            goto fail;
        }

        ret = pcm_prepare(*haptic_pcm);
        if (ret < 0) {
            ALOGE("cannot prepare haptic_pcm: %s", pcm_get_error(*haptic_pcm));
            goto fail;
        }

        ret = pcm_start(*haptic_pcm);
        if (ret < 0) {
            ALOGE("cannot start haptic_pcm: %s", pcm_get_error(*haptic_pcm));
            goto fail;
        }

        return true;
    } else {
        if (*haptic_pcm) {
            pcm_close(*haptic_pcm);
            *haptic_pcm = NULL;
        }
        return true;
    }

fail:
    pcm_close(*haptic_pcm);
    *haptic_pcm = NULL;
    return false;
}

void Vibrator::setPwleRampDown() {
    HAPTICS_TRACE("setPwleRampDown()");
    // The formula for calculating the ramp down coefficient to be written into
    // pwle_ramp_down is as follows:
    //    Crd = 1048.576 / Trd
    // where Trd is the desired ramp down time in seconds
    // pwle_ramp_down accepts only 24 bit integers values

    if (RAMP_DOWN_TIME_MS != 0.0) {
        const float seconds = RAMP_DOWN_TIME_MS / 1000;
        const auto ramp_down_coefficient = static_cast<uint32_t>(RAMP_DOWN_CONSTANT / seconds);
        if (!mHwApi->setPwleRampDown(ramp_down_coefficient)) {
            mStatsApi->logError(kHwApiError);
            ALOGE("Failed to write \"%d\" to pwle_ramp_down (%d): %s", ramp_down_coefficient, errno,
                  strerror(errno));
        }
    } else {
        // Turn off the low level PWLE Ramp Down feature
        if (!mHwApi->setPwleRampDown(0)) {
            mStatsApi->logError(kHwApiError);
            ALOGE("Failed to write 0 to pwle_ramp_down (%d): %s", errno, strerror(errno));
        }
    }
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
