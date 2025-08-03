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

#include <aidl/android/hardware/vibrator/BnVibrator.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <linux/input.h>
#include <tinyalsa/asoundlib.h>

#include <array>
#include <chrono>
#include <ctime>
#include <fstream>
#include <future>

#include "CapoDetector.h"

using CapoDetector = android::chre::CapoDetector;

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

using ::android::base::StringPrintf;

class Vibrator : public BnVibrator {
  public:
    // APIs for interfacing with the kernel driver.
    class HwApi {
      public:
        virtual ~HwApi() = default;
        // Stores the LRA resonant frequency to be used for PWLE playback
        // and click compensation.
        virtual bool setF0(std::string value) = 0;
        // Stores the frequency offset for long vibrations.
        virtual bool setF0Offset(uint32_t value) = 0;
        // Stores the LRA series resistance to be used for click
        // compensation.
        virtual bool setRedc(std::string value) = 0;
        // Stores the LRA Q factor to be used for Q-dependent waveform
        // selection.
        virtual bool setQ(std::string value) = 0;
        // Reports the number of effect waveforms loaded in firmware.
        virtual bool getEffectCount(uint32_t *value) = 0;
        // Blocks until timeout or vibrator reaches desired state
        // (2 = ASP enabled, 1 = haptic enabled, 0 = disabled).
        virtual bool pollVibeState(uint32_t value, int32_t timeoutMs = -1) = 0;
        // Reports whether getOwtFreeSpace() is supported.
        virtual bool hasOwtFreeSpace() = 0;
        // Reports the available OWT bytes.
        virtual bool getOwtFreeSpace(uint32_t *value) = 0;
        // Enables/Disables F0 compensation enable status
        virtual bool setF0CompEnable(bool value) = 0;
        // Enables/Disables Redc compensation enable status
        virtual bool setRedcCompEnable(bool value) = 0;
        // Stores the minumun delay time between playback and stop effects.
        virtual bool setMinOnOffInterval(uint32_t value) = 0;
        // Determine the /dev and /sys paths for input force-feedback control.
        virtual bool initFF() = 0;
        // Gets the scaling factor for contextual haptic events.
        virtual uint32_t getContextScale() = 0;
        // Gets the enable status for contextual haptic events.
        virtual bool getContextEnable() = 0;
        // Gets the settling time for contextual haptic events.
        // This will allow the device to stay face up for the duration given,
        // even if InMotion events were detected.
        virtual uint32_t getContextSettlingTime() = 0;
        // Gets the cooldown time for contextual haptic events.
        // This is used to avoid changing the scale of close playback events.
        virtual uint32_t getContextCooldownTime() = 0;
        // Checks the enable status for contextual haptics fade feature.  When enabled
        // this feature will cause the scaling factor to fade back up to max over
        // the setting time set, instead of instantaneously changing it back to max.
        virtual bool getContextFadeEnable() = 0;
        // Indicates the number of 0.125-dB steps of attenuation to apply to
        // waveforms triggered in response to vibration calls from the
        // Android vibrator HAL.
        virtual bool setFFGain(uint16_t value) = 0;
        // Create/modify custom effects for all physical waveforms.
        virtual bool setFFEffect(struct ff_effect *effect, uint16_t timeoutMs) = 0;
        // Activates/deactivates the effect index after setFFGain() and setFFEffect().
        virtual bool setFFPlay(int8_t index, bool value) = 0;
        // Get the Alsa device for the audio coupled haptics effect
        virtual bool getHapticAlsaDevice(int *card, int *device) = 0;
        // Set haptics PCM amplifier before triggering audio haptics feature
        virtual bool setHapticPcmAmp(struct pcm **haptic_pcm, bool enable, int card,
                                     int device) = 0;
        // Checks to see if the passthrough i2s haptics feature is supported by
        // the target device.
        virtual bool isPassthroughI2sHapticSupported() = 0;
        // Set OWT waveform for compose or compose PWLE request
        virtual bool uploadOwtEffect(const uint8_t *owtData, const uint32_t numBytes,
                                     struct ff_effect *effect, uint32_t *outEffectIndex,
                                     int *status) = 0;
        // Erase OWT waveform
        virtual bool eraseOwtEffect(int8_t effectIndex, std::vector<ff_effect> *effect) = 0;
        // Checks to see if DBC (Dynamic Boost Control) feature is supported
        // by the target device.
        virtual bool isDbcSupported() = 0;
        // Configures and enables the DBC feature and all associated parameters
        virtual bool enableDbc() = 0;
        // Emit diagnostic information to the given file.
        virtual void debug(int fd) = 0;
    };

    // APIs for obtaining calibration/configuration data from persistent memory.
    class HwCal {
      public:
        virtual ~HwCal() = default;
        // Obtain the calibration version
        virtual bool getVersion(uint32_t *value) = 0;
        // Obtains the LRA resonant frequency to be used for PWLE playback
        // and click compensation.
        virtual bool getF0(std::string *value) = 0;
        // Obtains the LRA series resistance to be used for click
        // compensation.
        virtual bool getRedc(std::string *value) = 0;
        // Obtains the LRA Q factor to be used for Q-dependent waveform
        // selection.
        virtual bool getQ(std::string *value) = 0;
        // Obtains frequency shift for long vibrations.
        virtual bool getLongFrequencyShift(int32_t *value) = 0;
        // Obtains device mass for calculating the bandwidth amplitude map
        virtual bool getDeviceMass(float *value) = 0;
        // Obtains loc coeff for calculating the bandwidth amplitude map
        virtual bool getLocCoeff(float *value) = 0;
        // Obtains the v0/v1(min/max) voltage levels to be applied for
        // tick/click/long in units of 1%.
        virtual bool getTickVolLevels(std::array<uint32_t, 2> *value) = 0;
        virtual bool getClickVolLevels(std::array<uint32_t, 2> *value) = 0;
        virtual bool getLongVolLevels(std::array<uint32_t, 2> *value) = 0;
        // Checks if the chirp feature is enabled.
        virtual bool isChirpEnabled() = 0;
        // Obtains the supported primitive effects.
        virtual bool getSupportedPrimitives(uint32_t *value) = 0;
        // Checks if the f0 compensation feature needs to be enabled.
        virtual bool isF0CompEnabled() = 0;
        // Checks if the redc compensation feature needs to be enabled.
        virtual bool isRedcCompEnabled() = 0;
        // Emit diagnostic information to the given file.
        virtual void debug(int fd) = 0;
    };

    // APIs for logging data to statistics backend
    class StatsApi {
      public:
        virtual ~StatsApi() = default;
        // Increment count for effect
        virtual bool logPrimitive(uint16_t effectIndex) = 0;
        // Increment count for long/short waveform and duration bucket
        virtual bool logWaveform(uint16_t effectIndex, int32_t duration) = 0;
        // Increment count for error
        virtual bool logError(uint16_t errorIndex) = 0;
        // Start new latency measurement
        virtual bool logLatencyStart(uint16_t latencyIndex) = 0;
        // Finish latency measurement and update latency statistics with result
        virtual bool logLatencyEnd() = 0;
        // Emit diagnostic information to the given file.
        virtual void debug(int fd) = 0;
    };

  public:
    Vibrator(std::unique_ptr<HwApi> hwapi, std::unique_ptr<HwCal> hwcal,
             std::unique_ptr<StatsApi> statsapi);

    ndk::ScopedAStatus getCapabilities(int32_t *_aidl_return) override;
    ndk::ScopedAStatus off() override;
    ndk::ScopedAStatus on(int32_t timeoutMs,
                          const std::shared_ptr<IVibratorCallback> &callback) override;
    ndk::ScopedAStatus perform(Effect effect, EffectStrength strength,
                               const std::shared_ptr<IVibratorCallback> &callback,
                               int32_t *_aidl_return) override;
    ndk::ScopedAStatus getSupportedEffects(std::vector<Effect> *_aidl_return) override;
    ndk::ScopedAStatus setAmplitude(float amplitude) override;
    ndk::ScopedAStatus setExternalControl(bool enabled) override;
    ndk::ScopedAStatus getCompositionDelayMax(int32_t *maxDelayMs);
    ndk::ScopedAStatus getCompositionSizeMax(int32_t *maxSize);
    ndk::ScopedAStatus getSupportedPrimitives(std::vector<CompositePrimitive> *supported) override;
    ndk::ScopedAStatus getPrimitiveDuration(CompositePrimitive primitive,
                                            int32_t *durationMs) override;
    ndk::ScopedAStatus compose(const std::vector<CompositeEffect> &composite,
                               const std::shared_ptr<IVibratorCallback> &callback) override;
    ndk::ScopedAStatus getSupportedAlwaysOnEffects(std::vector<Effect> *_aidl_return) override;
    ndk::ScopedAStatus alwaysOnEnable(int32_t id, Effect effect, EffectStrength strength) override;
    ndk::ScopedAStatus alwaysOnDisable(int32_t id) override;
    ndk::ScopedAStatus getResonantFrequency(float *resonantFreqHz) override;
    ndk::ScopedAStatus getQFactor(float *qFactor) override;
    ndk::ScopedAStatus getFrequencyResolution(float *freqResolutionHz) override;
    ndk::ScopedAStatus getFrequencyMinimum(float *freqMinimumHz) override;
    ndk::ScopedAStatus getBandwidthAmplitudeMap(std::vector<float> *_aidl_return) override;
    ndk::ScopedAStatus getPwlePrimitiveDurationMax(int32_t *durationMs) override;
    ndk::ScopedAStatus getPwleCompositionSizeMax(int32_t *maxSize) override;
    ndk::ScopedAStatus getSupportedBraking(std::vector<Braking> *supported) override;
    ndk::ScopedAStatus composePwle(const std::vector<PrimitivePwle> &composite,
                                   const std::shared_ptr<IVibratorCallback> &callback) override;

    binder_status_t dump(int fd, const char **args, uint32_t numArgs) override;

  private:
    ndk::ScopedAStatus on(uint32_t timeoutMs, uint32_t effectIndex, const class DspMemChunk *ch,
                          const std::shared_ptr<IVibratorCallback> &callback);
    // set 'amplitude' based on an arbitrary scale determined by 'maximum'
    ndk::ScopedAStatus setEffectAmplitude(float amplitude, float maximum, bool scalable);
    ndk::ScopedAStatus setGlobalAmplitude(bool set);
    // 'simple' effects are those precompiled and loaded into the controller
    ndk::ScopedAStatus getSimpleDetails(Effect effect, EffectStrength strength,
                                        uint32_t *outEffectIndex, uint32_t *outTimeMs,
                                        uint32_t *outVolLevel);
    // 'compound' effects are those composed by stringing multiple 'simple' effects
    ndk::ScopedAStatus getCompoundDetails(Effect effect, EffectStrength strength,
                                          uint32_t *outTimeMs, class DspMemChunk *outCh);
    ndk::ScopedAStatus getPrimitiveDetails(CompositePrimitive primitive, uint32_t *outEffectIndex);
    ndk::ScopedAStatus performEffect(Effect effect, EffectStrength strength,
                                     const std::shared_ptr<IVibratorCallback> &callback,
                                     int32_t *outTimeMs);
    ndk::ScopedAStatus performEffect(uint32_t effectIndex, uint32_t volLevel,
                                     const class DspMemChunk *ch,
                                     const std::shared_ptr<IVibratorCallback> &callback);
    ndk::ScopedAStatus setPwle(const std::string &pwleQueue);
    bool isUnderExternalControl();
    void waitForComplete(std::shared_ptr<IVibratorCallback> &&callback);
    uint32_t intensityToVolLevel(float intensity, uint32_t effectIndex);
    bool findHapticAlsaDevice(int *card, int *device);
    bool hasHapticAlsaDevice();
    bool enableHapticPcmAmp(struct pcm **haptic_pcm, bool enable, int card, int device);
    void createPwleMaxLevelLimitMap();
    void createBandwidthAmplitudeMap();
    uint16_t amplitudeToScale(float amplitude, float maximum, bool scalable);
    void updateContext();

    std::unique_ptr<HwApi> mHwApi;
    std::unique_ptr<HwCal> mHwCal;
    std::unique_ptr<StatsApi> mStatsApi;
    uint32_t mF0Offset;
    std::array<uint32_t, 2> mTickEffectVol;
    std::array<uint32_t, 2> mClickEffectVol;
    std::array<uint32_t, 2> mLongEffectVol;
    std::vector<ff_effect> mFfEffects;
    std::vector<uint32_t> mEffectDurations;
    std::vector<std::vector<int16_t>> mEffectCustomData;
    std::future<void> mAsyncHandle;
    int8_t mActiveId{-1};
    struct pcm *mHapticPcm;
    int mCard;
    int mDevice;
    bool mHasHapticAlsaDevice{false};
    bool mHasPassthroughHapticDevice;
    bool mIsUnderExternalControl;
    float mLongEffectScale = 1.0;
    bool mIsChirpEnabled;
    uint32_t mSupportedPrimitivesBits = 0x0;
    float mRedc{0};
    float mResonantFrequency{0};
    std::vector<CompositePrimitive> mSupportedPrimitives;
    bool mConfigHapticAlsaDeviceDone{false};
    std::vector<float> mBandwidthAmplitudeMap{};
    bool mCreateBandwidthAmplitudeMapDone{false};
    uint32_t mScaleTime;
    bool mFadeEnable;
    uint32_t mScalingFactor;
    uint32_t mScaleCooldown;
    bool mContextEnable;
    bool mContextEnabledPreviously{false};
    uint32_t mLastEffectPlayedTime = 0;
    float mLastPlayedScale = 0;
    sp<CapoDetector> mContextListener;
    enum hal_state {
        IDLE,
        PREPARING,
        ISSUED,
        PLAYING,
        STOPPED,
        RESTORED,
    };
    hal_state halState = IDLE;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
