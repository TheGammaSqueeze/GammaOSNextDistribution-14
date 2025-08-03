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

#include <glob.h>

#include <algorithm>

#include "HardwareBase.h"
#include "Vibrator.h"

#define PROC_SND_PCM "/proc/asound/pcm"
#define HAPTIC_PCM_DEVICE_SYMBOL "haptic nohost playback"

static struct pcm_config haptic_nohost_config = {
        .channels = 1,
        .rate = 48000,
        .period_size = 80,
        .period_count = 2,
        .format = PCM_FORMAT_S16_LE,
};

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

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

class HwApi : public Vibrator::HwApi, private HwApiBase {
  public:
    HwApi() {
        HwApi::initFF();
        open("calibration/f0_stored", &mF0);
        open("default/f0_offset", &mF0Offset);
        open("calibration/redc_stored", &mRedc);
        open("calibration/q_stored", &mQ);
        open("default/vibe_state", &mVibeState);
        open("default/num_waves", &mEffectCount);
        open("default/owt_free_space", &mOwtFreeSpace);
        open("default/f0_comp_enable", &mF0CompEnable);
        open("default/redc_comp_enable", &mRedcCompEnable);
        open("default/delay_before_stop_playback_us", &mMinOnOffInterval);
    }

    bool setF0(std::string value) override { return set(value, &mF0); }
    bool setF0Offset(uint32_t value) override { return set(value, &mF0Offset); }
    bool setRedc(std::string value) override { return set(value, &mRedc); }
    bool setQ(std::string value) override { return set(value, &mQ); }
    bool getEffectCount(uint32_t *value) override { return get(value, &mEffectCount); }
    bool pollVibeState(uint32_t value, int32_t timeoutMs) override {
        return poll(value, &mVibeState, timeoutMs);
    }
    bool hasOwtFreeSpace() override { return has(mOwtFreeSpace); }
    bool getOwtFreeSpace(uint32_t *value) override { return get(value, &mOwtFreeSpace); }
    bool setF0CompEnable(bool value) override { return set(value, &mF0CompEnable); }
    bool setRedcCompEnable(bool value) override { return set(value, &mRedcCompEnable); }
    bool setMinOnOffInterval(uint32_t value) override { return set(value, &mMinOnOffInterval); }
    uint32_t getContextScale() override {
        return utils::getProperty("persist.vendor.vibrator.hal.context.scale", 100);
    }
    bool getContextEnable() override {
        return utils::getProperty("persist.vendor.vibrator.hal.context.enable", false);
    }
    uint32_t getContextSettlingTime() override {
        return utils::getProperty("persist.vendor.vibrator.hal.context.settlingtime", 3000);
    }
    uint32_t getContextCooldownTime() override {
        return utils::getProperty("persist.vendor.vibrator.hal.context.cooldowntime", 1000);
    }
    bool getContextFadeEnable() override {
        return utils::getProperty("persist.vendor.vibrator.hal.context.fade", false);
    }

    // TODO(b/234338136): Need to add the force feedback HW API test cases
    bool initFF() override {
        ATRACE_NAME(__func__);
        const std::string INPUT_EVENT_NAME = std::getenv("INPUT_EVENT_NAME") ?: "";
        if (INPUT_EVENT_NAME.find("cs40l26") == std::string::npos) {
            ALOGE("Invalid input name: %s", INPUT_EVENT_NAME.c_str());
            return false;
        }

        glob_t g = {};
        const std::string INPUT_EVENT_PATH = "/dev/input/event*";
        int fd = -1, ret;
        uint32_t val = 0;
        char str[256] = {0x00};
        // Scan /dev/input/event* to get the correct input device path for FF effects manipulation.
        // Then constructs the /sys/class/input/event*/../../../ for driver attributes accessing
        // across different platforms and different kernels.
        for (uint8_t retry = 1; retry < 11 && !mInputFd.ok(); retry++) {
            ret = glob(INPUT_EVENT_PATH.c_str(), 0, nullptr, &g);
            if (ret) {
                ALOGE("Failed to get input event paths (%d): %s", errno, strerror(errno));
            } else {
                for (size_t i = 0; i < g.gl_pathc; i++) {
                    fd = TEMP_FAILURE_RETRY(::open(g.gl_pathv[i], O_RDWR));
                    if (fd < 0) {
                        continue;
                    }
                    // Determine the input device path:
                    // 1. Check if EV_FF is flagged in event bits.
                    // 2. Match device name(s) with this CS40L26 HAL instance.
                    if (ioctl(fd, EVIOCGBIT(0, sizeof(val)), &val) > 0 && (val & (1 << EV_FF)) &&
                        ioctl(fd, EVIOCGNAME(sizeof(str)), &str) > 0 &&
                        strcmp(str, INPUT_EVENT_NAME.c_str()) == 0) {
                        // Get fd ready for input event ioctl().
                        mInputFd.reset(fd);  // mInputFd.ok() becomes true.
                        ALOGI("Control %s through %s", INPUT_EVENT_NAME.c_str(), g.gl_pathv[i]);

                        std::string path = g.gl_pathv[i];
                        // Get fstream ready for input event write().
                        saveName(path, &mInputIoStream);
                        mInputIoStream.open(
                                path, std::fstream::out | std::fstream::in | std::fstream::binary);
                        if (!mInputIoStream) {
                            ALOGE("Failed to open %s (%d): %s", path.c_str(), errno,
                                  strerror(errno));
                        }

                        // Construct the sysfs device path.
                        path = "/sys/class/input/" +
                               path.substr(path.find("event"), std::string::npos) + "/../../../";
                        updatePathPrefix(path);
                        break;
                    }
                    close(fd);
                    memset(str, 0x00, sizeof(str));
                    val = 0;
                }
            }

            if (!mInputFd.ok()) {
                sleep(1);
                ALOGW("Retry #%d to search in %zu input devices...", retry, g.gl_pathc);
            }
        }
        globfree(&g);

        if (!mInputFd.ok()) {
            ALOGE("Failed to get an input event with name %s", INPUT_EVENT_NAME.c_str());
            return false;
        }

        return true;
    }
    bool setFFGain(uint16_t value) override {
        ATRACE_NAME(StringPrintf("%s %d%%", __func__, value).c_str());
        struct input_event gain = {
                .type = EV_FF,
                .code = FF_GAIN,
                .value = value,
        };
        if (value > 100) {
            ALOGE("Invalid gain");
            return false;
        }
        mInputIoStream.write((const char *)&gain, sizeof(gain));
        mInputIoStream.flush();
        if (mInputIoStream.fail()) {
            ALOGE("setFFGain fail");
            return false;
        }
        HWAPI_RECORD(StringPrintf("%d%%", value), &mInputIoStream);
        return true;
    }
    bool setFFEffect(struct ff_effect *effect, uint16_t timeoutMs) override {
        ATRACE_NAME(StringPrintf("%s %dms", __func__, timeoutMs).c_str());
        if (effect == nullptr) {
            ALOGE("Invalid ff_effect");
            return false;
        }
        if (ioctl(mInputFd, EVIOCSFF, effect) < 0) {
            ALOGE("setFFEffect fail");
            return false;
        }
        HWAPI_RECORD(StringPrintf("#%d: %dms", (*effect).id, timeoutMs), &mInputIoStream);
        return true;
    }
    bool setFFPlay(int8_t index, bool value) override {
        ATRACE_NAME(StringPrintf("%s index:%d %s", __func__, index, value ? "on" : "off").c_str());
        struct input_event play = {
                .type = EV_FF,
                .code = static_cast<uint16_t>(index),
                .value = value,
        };
        mInputIoStream.write((const char *)&play, sizeof(play));
        mInputIoStream.flush();
        if (mInputIoStream.fail()) {
            ALOGE("setFFPlay fail");
            return false;
        }
        HWAPI_RECORD(StringPrintf("#%d: %b", index, value), &mInputIoStream);
        return true;
    }
    bool getHapticAlsaDevice(int *card, int *device) override {
        ATRACE_NAME(__func__);
        std::string line;
        std::ifstream myfile(PROC_SND_PCM);
        if (myfile.is_open()) {
            while (getline(myfile, line)) {
                if (line.find(HAPTIC_PCM_DEVICE_SYMBOL) != std::string::npos) {
                    std::stringstream ss(line);
                    std::string currentToken;
                    std::getline(ss, currentToken, ':');
                    sscanf(currentToken.c_str(), "%d-%d", card, device);
                    saveName(StringPrintf("/dev/snd/pcmC%uD%up", *card, *device), &mPcmStream);
                    return true;
                }
            }
            myfile.close();
        } else {
            ALOGE("Failed to read file: %s", PROC_SND_PCM);
        }
        return false;
    }
    bool setHapticPcmAmp(struct pcm **haptic_pcm, bool enable, int card, int device) override {
        ATRACE_NAME(StringPrintf("%s %s", __func__, enable ? "enable" : "disable").c_str());
        int ret = 0;

        if (enable) {
            *haptic_pcm = pcm_open(card, device, PCM_OUT, &haptic_nohost_config);
            if (!pcm_is_ready(*haptic_pcm)) {
                ALOGE("cannot open pcm_out driver: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }
            HWAPI_RECORD(std::string("pcm_open"), &mPcmStream);

            ret = pcm_prepare(*haptic_pcm);
            if (ret < 0) {
                ALOGE("cannot prepare haptic_pcm: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }
            HWAPI_RECORD(std::string("pcm_prepare"), &mPcmStream);

            ret = pcm_start(*haptic_pcm);
            if (ret < 0) {
                ALOGE("cannot start haptic_pcm: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }
            HWAPI_RECORD(std::string("pcm_start"), &mPcmStream);

            return true;
        } else {
            if (*haptic_pcm) {
                pcm_close(*haptic_pcm);
                HWAPI_RECORD(std::string("pcm_close"), &mPcmStream);
                *haptic_pcm = NULL;
            }
            return true;
        }

    fail:
        pcm_close(*haptic_pcm);
        HWAPI_RECORD(std::string("pcm_close"), &mPcmStream);
        *haptic_pcm = NULL;
        return false;
    }
    bool isPassthroughI2sHapticSupported() override {
        return utils::getProperty("ro.vendor.vibrator.hal.passthrough_i2s_supported", false);
    }
    bool uploadOwtEffect(const uint8_t *owtData, const uint32_t numBytes, struct ff_effect *effect,
                         uint32_t *outEffectIndex, int *status) override {
        ATRACE_NAME(__func__);
        if (owtData == nullptr || effect == nullptr || outEffectIndex == nullptr) {
            ALOGE("Invalid argument owtData, ff_effect or outEffectIndex");
            *status = EX_NULL_POINTER;
            return false;
        }
        if (status == nullptr) {
            ALOGE("Invalid argument status");
            return false;
        }

        (*effect).u.periodic.custom_len = numBytes / sizeof(uint16_t);
        memcpy((*effect).u.periodic.custom_data, owtData, numBytes);

        if ((*effect).id != -1) {
            ALOGE("(*effect).id != -1");
        }

        /* Create a new OWT waveform to update the PWLE or composite effect. */
        (*effect).id = -1;
        if (ioctl(mInputFd, EVIOCSFF, effect) < 0) {
            ALOGE("Failed to upload effect %d (%d): %s", *outEffectIndex, errno, strerror(errno));
            *status = EX_ILLEGAL_STATE;
            return false;
        }

        if ((*effect).id >= FF_MAX_EFFECTS || (*effect).id < 0) {
            ALOGE("Invalid waveform index after upload OWT effect: %d", (*effect).id);
            *status = EX_ILLEGAL_ARGUMENT;
            return false;
        }
        *outEffectIndex = (*effect).id;
        *status = 0;
        HWAPI_RECORD(StringPrintf("#%d: %dB", *outEffectIndex, numBytes), &mInputIoStream);
        return true;
    }
    bool eraseOwtEffect(int8_t effectIndex, std::vector<ff_effect> *effect) override {
        ATRACE_NAME(__func__);
        uint32_t effectCountBefore, effectCountAfter, i, successFlush = 0;

        if (effectIndex < WAVEFORM_MAX_PHYSICAL_INDEX) {
            ALOGE("Invalid waveform index for OWT erase: %d", effectIndex);
            return false;
        }
        if (effect == nullptr || (*effect).empty()) {
            ALOGE("Invalid argument effect");
            return false;
        }

        if (effectIndex < WAVEFORM_MAX_INDEX) {
            /* Normal situation. Only erase the effect which we just played. */
            if (ioctl(mInputFd, EVIOCRMFF, effectIndex) < 0) {
                ALOGE("Failed to erase effect %d (%d): %s", effectIndex, errno, strerror(errno));
            }
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < WAVEFORM_MAX_INDEX; i++) {
                if ((*effect)[i].id == effectIndex) {
                    (*effect)[i].id = -1;
                    break;
                }
            }
            HWAPI_RECORD(StringPrintf("#%d", effectIndex), &mInputIoStream);
        } else {
            /* Flush all non-prestored effects of ff-core and driver. */
            getEffectCount(&effectCountBefore);
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < FF_MAX_EFFECTS; i++) {
                if (ioctl(mInputFd, EVIOCRMFF, i) >= 0) {
                    successFlush++;
                    HWAPI_RECORD(StringPrintf("#%d", i), &mInputIoStream);
                }
            }
            getEffectCount(&effectCountAfter);
            ALOGW("Flushed effects: ff: %d; driver: %d -> %d; success: %d", effectIndex,
                  effectCountBefore, effectCountAfter, successFlush);
            /* Reset all OWT effect index of HAL. */
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < WAVEFORM_MAX_INDEX; i++) {
                (*effect)[i].id = -1;
            }
        }
        return true;
    }
    bool isDbcSupported() override {
        ATRACE_NAME(__func__);
        return utils::getProperty("ro.vendor.vibrator.hal.dbc.enable", false);
    }

    bool enableDbc() override {
        ATRACE_NAME(__func__);
        if (isDbcSupported()) {
            open("dbc/dbc_env_rel_coef", &mDbcEnvRelCoef);
            open("dbc/dbc_rise_headroom", &mDbcRiseHeadroom);
            open("dbc/dbc_fall_headroom", &mDbcFallHeadroom);
            open("dbc/dbc_tx_lvl_thresh_fs", &mDbcTxLvlThreshFs);
            open("dbc/dbc_tx_lvl_hold_off_ms", &mDbcTxLvlHoldOffMs);
            open("default/pm_active_timeout_ms", &mPmActiveTimeoutMs);
            open("dbc/dbc_enable", &mDbcEnable);

            // Set values from config. Default if not found.
            set(utils::getProperty("ro.vendor.vibrator.hal.dbc.envrelcoef", kDbcDefaultEnvRelCoef),
                &mDbcEnvRelCoef);
            set(utils::getProperty("ro.vendor.vibrator.hal.dbc.riseheadroom",
                                   kDbcDefaultRiseHeadroom),
                &mDbcRiseHeadroom);
            set(utils::getProperty("ro.vendor.vibrator.hal.dbc.fallheadroom",
                                   kDbcDefaultFallHeadroom),
                &mDbcFallHeadroom);
            set(utils::getProperty("ro.vendor.vibrator.hal.dbc.txlvlthreshfs",
                                   kDbcDefaultTxLvlThreshFs),
                &mDbcTxLvlThreshFs);
            set(utils::getProperty("ro.vendor.vibrator.hal.dbc.txlvlholdoffms",
                                   kDbcDefaultTxLvlHoldOffMs),
                &mDbcTxLvlHoldOffMs);
            set(utils::getProperty("ro.vendor.vibrator.hal.pm.activetimeout",
                                   kDefaultPmActiveTimeoutMs),
                &mPmActiveTimeoutMs);
            set(kDbcEnable, &mDbcEnable);
            return true;
        }
        return false;
    }

    void debug(int fd) override { HwApiBase::debug(fd); }

  private:
    static constexpr uint32_t kDbcDefaultEnvRelCoef = 8353728;
    static constexpr uint32_t kDbcDefaultRiseHeadroom = 1909602;
    static constexpr uint32_t kDbcDefaultFallHeadroom = 1909602;
    static constexpr uint32_t kDbcDefaultTxLvlThreshFs = 2516583;
    static constexpr uint32_t kDbcDefaultTxLvlHoldOffMs = 0;
    static constexpr uint32_t kDefaultPmActiveTimeoutMs = 5;
    static constexpr uint32_t kDbcEnable = 1;

    std::ofstream mF0;
    std::ofstream mF0Offset;
    std::ofstream mRedc;
    std::ofstream mQ;
    std::ifstream mEffectCount;
    std::ifstream mVibeState;
    std::ifstream mOwtFreeSpace;
    std::ofstream mF0CompEnable;
    std::ofstream mRedcCompEnable;
    std::ofstream mMinOnOffInterval;
    std::ofstream mInputIoStream;
    std::ofstream mPcmStream;
    ::android::base::unique_fd mInputFd;

    // DBC Parameters
    std::ofstream mDbcEnvRelCoef;
    std::ofstream mDbcRiseHeadroom;
    std::ofstream mDbcFallHeadroom;
    std::ofstream mDbcTxLvlThreshFs;
    std::ofstream mDbcTxLvlHoldOffMs;
    std::ofstream mDbcEnable;
    std::ofstream mPmActiveTimeoutMs;
};

class HwCal : public Vibrator::HwCal, private HwCalBase {
  private:
    static constexpr char VERSION[] = "version";
    static constexpr char F0_CONFIG[] = "f0_measured";
    static constexpr char REDC_CONFIG[] = "redc_measured";
    static constexpr char Q_CONFIG[] = "q_measured";
    static constexpr char TICK_VOLTAGES_CONFIG[] = "v_tick";
    static constexpr char CLICK_VOLTAGES_CONFIG[] = "v_click";
    static constexpr char LONG_VOLTAGES_CONFIG[] = "v_long";

    static constexpr uint32_t VERSION_DEFAULT = 2;
    static constexpr int32_t DEFAULT_FREQUENCY_SHIFT = 0;
    static constexpr float DEFAULT_DEVICE_MASS = 0.21;
    static constexpr float DEFAULT_LOC_COEFF = 2.5;
    static constexpr std::array<uint32_t, 2> V_TICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_CLICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_LONG_DEFAULT = {1, 100};

  public:
    HwCal() {}

    bool getVersion(uint32_t *value) override {
        if (getPersist(VERSION, value)) {
            return true;
        }
        *value = VERSION_DEFAULT;
        return true;
    }
    bool getLongFrequencyShift(int32_t *value) override {
        return getProperty("long.frequency.shift", value, DEFAULT_FREQUENCY_SHIFT);
    }
    bool getDeviceMass(float *value) override {
        return getProperty("device.mass", value, DEFAULT_DEVICE_MASS);
    }
    bool getLocCoeff(float *value) override {
        return getProperty("loc.coeff", value, DEFAULT_LOC_COEFF);
    }
    bool getF0(std::string *value) override { return getPersist(F0_CONFIG, value); }
    bool getRedc(std::string *value) override { return getPersist(REDC_CONFIG, value); }
    bool getQ(std::string *value) override { return getPersist(Q_CONFIG, value); }
    bool getTickVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(TICK_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_TICK_DEFAULT;
        return true;
    }
    bool getClickVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(CLICK_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_CLICK_DEFAULT;
        return true;
    }
    bool getLongVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(LONG_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_LONG_DEFAULT;
        return true;
    }
    bool isChirpEnabled() override {
        return utils::getProperty("persist.vendor.vibrator.hal.chirp.enabled", false);
    }
    bool getSupportedPrimitives(uint32_t *value) override {
        return getProperty("supported_primitives", value, (uint32_t)0);
    }
    bool isF0CompEnabled() override {
        bool value;
        getProperty("f0.comp.enabled", &value, true);
        return value;
    }
    bool isRedcCompEnabled() override {
        bool value;
        getProperty("redc.comp.enabled", &value, false);
        return value;
    }
    void debug(int fd) override { HwCalBase::debug(fd); }
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
