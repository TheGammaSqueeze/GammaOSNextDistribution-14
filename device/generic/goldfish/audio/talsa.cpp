/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <mutex>
#include <cutils/properties.h>
#include <log/log.h>
#include "talsa.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {
namespace talsa {

namespace {

struct mixer *gMixer0 = nullptr;
int gMixerRefcounter0 = 0;
std::mutex gMixerMutex;
PcmPeriodSettings gPcmPeriodSettings;
unsigned gPcmHostLatencyMs;

void mixerSetValueAll(struct mixer_ctl *ctl, int value) {
    const unsigned int n = mixer_ctl_get_num_values(ctl);
    for (unsigned int i = 0; i < n; i++) {
        ::mixer_ctl_set_value(ctl, i, value);
    }
}

void mixerSetPercentAll(struct mixer_ctl *ctl, int percent) {
    const unsigned int n = mixer_ctl_get_num_values(ctl);
    for (unsigned int i = 0; i < n; i++) {
        ::mixer_ctl_set_percent(ctl, i, percent);
    }
}

struct mixer *mixerGetOrOpenImpl(const unsigned card,
                                 struct mixer *&gMixer,
                                 int &refcounter) {
    if (!gMixer) {
        struct mixer *mixer = ::mixer_open(card);
        if (!mixer) {
            return FAILURE(nullptr);
        }

        mixerSetPercentAll(::mixer_get_ctl_by_name(mixer, "Master Playback Volume"), 100);
        mixerSetPercentAll(::mixer_get_ctl_by_name(mixer, "Capture Volume"), 100);

        mixerSetValueAll(::mixer_get_ctl_by_name(mixer, "Master Playback Switch"), 1);
        mixerSetValueAll(::mixer_get_ctl_by_name(mixer, "Capture Switch"), 1);

        gMixer = mixer;
    }

    ++refcounter;
    return gMixer;
}

struct mixer *mixerGetOrOpen(const unsigned card) {
    std::lock_guard<std::mutex> guard(gMixerMutex);

    switch (card) {
    case 0:  return mixerGetOrOpenImpl(card, gMixer0, gMixerRefcounter0);
    default: return FAILURE(nullptr);
    }
}

bool mixerUnrefImpl(struct mixer *mixer, struct mixer *&gMixer, int &refcounter) {
    if (mixer == gMixer) {
        if (0 == --refcounter) {
            ::mixer_close(mixer);
            gMixer = nullptr;
        }
        return true;
    } else {
        return false;
    }
}

bool mixerUnref(struct mixer *mixer) {
    std::lock_guard<std::mutex> guard(gMixerMutex);

    return mixerUnrefImpl(mixer, gMixer0, gMixerRefcounter0);
}

unsigned readUnsignedProperty(const char *propName, const unsigned defaultValue) {
    char propValue[PROPERTY_VALUE_MAX];

    if (property_get(propName, propValue, nullptr) < 0) {
        return defaultValue;
    }

    unsigned value;
    return (sscanf(propValue, "%u", &value) == 1) ? value : defaultValue;
}
}  // namespace

void init() {
    gPcmPeriodSettings.periodCount =
        readUnsignedProperty("ro.hardware.audio.tinyalsa.period_count", 4);

    gPcmPeriodSettings.periodSizeMultiplier =
        readUnsignedProperty("ro.hardware.audio.tinyalsa.period_size_multiplier", 1);

    gPcmHostLatencyMs =
        readUnsignedProperty("ro.hardware.audio.tinyalsa.host_latency_ms", 0);
}

PcmPeriodSettings pcmGetPcmPeriodSettings() {
    return gPcmPeriodSettings;
}

unsigned pcmGetHostLatencyMs() {
    return gPcmHostLatencyMs;
}

void PcmDeleter::operator()(pcm_t *x) const {
    LOG_ALWAYS_FATAL_IF(::pcm_close(x) != 0);
};

PcmPtr pcmOpen(const unsigned int dev,
               const unsigned int card,
               const unsigned int nChannels,
               const size_t sampleRateHz,
               const size_t frameCount,
               const bool isOut) {
    const PcmPeriodSettings periodSettings = pcmGetPcmPeriodSettings();

    struct pcm_config pcm_config;
    memset(&pcm_config, 0, sizeof(pcm_config));

    pcm_config.channels = nChannels;
    pcm_config.rate = sampleRateHz;
    // Approx interrupts per buffer
    pcm_config.period_count = periodSettings.periodCount;
    // Approx frames between interrupts
    pcm_config.period_size =
        periodSettings.periodSizeMultiplier * frameCount / periodSettings.periodCount;
    pcm_config.format = PCM_FORMAT_S16_LE;
    if (isOut) {
        pcm_config.start_threshold = pcm_config.period_size * (pcm_config.period_count - 1);
        pcm_config.stop_threshold = pcm_config.period_size * pcm_config.period_count;
    }

    pcm_t *pcmRaw = ::pcm_open(dev, card,
                               (isOut ? PCM_OUT : PCM_IN) | PCM_MONOTONIC,
                               &pcm_config);
    if (!pcmRaw) {
        ALOGE("%s:%d pcm_open returned nullptr for nChannels=%u sampleRateHz=%zu "
              "period_count=%d period_size=%d isOut=%d", __func__, __LINE__,
              nChannels, sampleRateHz, pcm_config.period_count, pcm_config.period_size, isOut);
        return FAILURE(nullptr);
    }

    PcmPtr pcm(pcmRaw);
    if (!::pcm_is_ready(pcmRaw)) {
        ALOGE("%s:%d pcm_open failed for nChannels=%u sampleRateHz=%zu "
              "period_count=%d period_size=%d isOut=%d with %s", __func__, __LINE__,
              nChannels, sampleRateHz, pcm_config.period_count, pcm_config.period_size, isOut,
              ::pcm_get_error(pcmRaw));
        return FAILURE(nullptr);
    }

    if (const int err = ::pcm_prepare(pcmRaw)) {
        ALOGE("%s:%d pcm_prepare failed for nChannels=%u sampleRateHz=%zu "
              "period_count=%d period_size=%d isOut=%d with %s (%d)", __func__, __LINE__,
              nChannels, sampleRateHz, pcm_config.period_count, pcm_config.period_size, isOut,
              ::pcm_get_error(pcmRaw), err);
        return FAILURE(nullptr);
    }

    return pcm;
}

int pcmRead(pcm_t *pcm, void *data, const int szBytes,
             const unsigned int frameSize) {
    LOG_ALWAYS_FATAL_IF(frameSize == 0);
    LOG_ALWAYS_FATAL_IF(szBytes < 0, "szBytes=%d", szBytes);
    LOG_ALWAYS_FATAL_IF((szBytes % frameSize) != 0, "szBytes=%d frameSize=%u",
                        szBytes, frameSize);
    if (!pcm) {
        return FAILURE(-1);
    }

    const int szFrames = szBytes / frameSize;
    int tries = 3;
    while (true) {
        const int framesRead = ::pcm_readi(pcm, data, szFrames);
        if (framesRead > 0) {
            LOG_ALWAYS_FATAL_IF(framesRead > szFrames,
                                "framesRead=%d szFrames=%d szBytes=%u frameSize=%u",
                                framesRead, szFrames, szBytes, frameSize);
            return framesRead * frameSize;
        } else {
            --tries;
            switch (-framesRead) {
            case EIO:
            case EAGAIN:
                if (tries > 0) {
                    break;
                }
                [[fallthrough]];

            default:
                ALOGW("%s:%d pcm_readi failed with '%s' (%d)",
                      __func__, __LINE__, ::pcm_get_error(pcm), framesRead);
                return FAILURE(-1);
            }
        }
    }
}

int pcmWrite(pcm_t *pcm, const void *data, const int szBytes,
              const unsigned int frameSize) {
    LOG_ALWAYS_FATAL_IF(frameSize == 0);
    LOG_ALWAYS_FATAL_IF(szBytes < 0, "szBytes=%d", szBytes);
    LOG_ALWAYS_FATAL_IF((szBytes % frameSize) != 0, "szBytes=%d frameSize=%u",
                        szBytes, frameSize);
    if (!pcm) {
        return FAILURE(-1);
    }

    const int szFrames = szBytes / frameSize;
    int tries = 3;
    while (true) {
        const int framesWritten = ::pcm_writei(pcm, data, szFrames);
        if (framesWritten > 0) {
            LOG_ALWAYS_FATAL_IF(framesWritten > szFrames,
                                "framesWritten=%d szFrames=%d szBytes=%u frameSize=%u",
                                framesWritten, szFrames, szBytes, frameSize);
            return framesWritten * frameSize;
        } else {
            --tries;
            switch (-framesWritten) {
            case EIO:
            case EAGAIN:
                if (tries > 0) {
                    break;
                }
                [[fallthrough]];

            default:
                ALOGW("%s:%d pcm_writei failed with '%s' (%d)",
                      __func__, __LINE__, ::pcm_get_error(pcm), framesWritten);
                return FAILURE(-1);
            }
        }
    }
}

Mixer::Mixer(unsigned card): mMixer(mixerGetOrOpen(card)) {}

Mixer::~Mixer() {
    if (mMixer) {
        LOG_ALWAYS_FATAL_IF(!mixerUnref(mMixer));
    }
}

}  // namespace talsa
}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
