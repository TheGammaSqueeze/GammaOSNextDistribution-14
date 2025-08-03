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

#include <log/log.h>
//#include <cutils/bitops.h>
#include <cutils/sched_policy.h>
#include <system/audio.h>
#include <sys/resource.h>
#include <pthread.h>
#include PATH(APM_XSD_ENUMS_H_FILENAME)
#include "util.h"
#include "debug.h"

namespace xsd {
using namespace ::android::audio::policy::configuration::CPP_VERSION;
}

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {
namespace util {

using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioConfigBaseOptional;
using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioPortExtendedInfo;
using ::android::hardware::audio::CORE_TYPES_CPP_VERSION::AudioMicrophoneDirectionality;

namespace {

const std::array<uint32_t, 8> kSupportedRatesHz = {
    8000, 11025, 16000, 22050, 32000, 44100, 48000
};

bool checkSampleRateHz(uint32_t value, uint32_t &suggested) {
    for (const uint32_t supported : kSupportedRatesHz) {
        if (value <= supported) {
            suggested = supported;
            return value == supported;
        }
    }

    suggested = kSupportedRatesHz.back();
    return FAILURE(false);
}

bool checkChannelMask(const bool isOut,
                      const AudioChannelMask &value,
                      AudioChannelMask &suggested) {
    switch (xsd::stringToAudioChannelMask(value)) {
    case xsd::AudioChannelMask::AUDIO_CHANNEL_OUT_MONO:
    case xsd::AudioChannelMask::AUDIO_CHANNEL_OUT_STEREO:
    case xsd::AudioChannelMask::AUDIO_CHANNEL_IN_MONO:
    case xsd::AudioChannelMask::AUDIO_CHANNEL_IN_STEREO:
        suggested = value;
        return true;

    default:
        suggested = toString(isOut ?
            xsd::AudioChannelMask::AUDIO_CHANNEL_OUT_STEREO :
            xsd::AudioChannelMask::AUDIO_CHANNEL_IN_MONO);
        return FAILURE(false);
    }
}

bool checkFormat(const AudioFormat &value, AudioFormat &suggested) {
    switch (xsd::stringToAudioFormat(value)) {
    case xsd::AudioFormat::AUDIO_FORMAT_PCM_16_BIT:
        suggested = value;
        return true;

    default:
        suggested = toString(xsd::AudioFormat::AUDIO_FORMAT_PCM_16_BIT);
        return FAILURE(false);
    }
}

size_t align(size_t v, size_t a) {
    return (v + a - 1) / a * a;
}

size_t getBufferSizeFrames(size_t duration_ms, size_t sample_rate, size_t extraAlignment) {
    // AudioFlinger requires the buffer to be aligned by 16 frames
    return align(sample_rate * duration_ms / 1000, 16 * extraAlignment);
}

}  // namespace

MicrophoneInfo getMicrophoneInfo() {
    MicrophoneInfo mic;

    mic.deviceId = "mic_goldfish";
    mic.group = 0;
    mic.indexInTheGroup = 0;
    mic.sensitivity = AUDIO_MICROPHONE_SENSITIVITY_UNKNOWN;
    mic.maxSpl = AUDIO_MICROPHONE_SENSITIVITY_UNKNOWN;
    mic.minSpl = AUDIO_MICROPHONE_SENSITIVITY_UNKNOWN;
    mic.directionality = AudioMicrophoneDirectionality::UNKNOWN;
    mic.position.x = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;
    mic.position.y = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;
    mic.position.z = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;
    mic.orientation.x = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;
    mic.orientation.y = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;
    mic.orientation.z = AUDIO_MICROPHONE_COORDINATE_UNKNOWN;

    return mic;
}

size_t countChannels(const AudioChannelMask &mask) {
    return xsd::getChannelCount(mask);
}

size_t getBytesPerSample(const AudioFormat &format) {
    if (format == "AUDIO_FORMAT_PCM_16_BIT") {
        return 2;
    } else {
        ALOGE("util::%s:%d unknown format, '%s'", __func__, __LINE__, format.c_str());
        return 0;
    }
}

bool checkAudioConfig(const AudioConfig &cfg) {
    if (xsd::isUnknownAudioFormat(cfg.base.format)
            || xsd::isUnknownAudioChannelMask(cfg.base.channelMask)) {
        return false;
    }
    if (cfg.offloadInfo.getDiscriminator() ==
            AudioConfig::OffloadInfo::hidl_discriminator::info) {
        if (const auto& info = cfg.offloadInfo.info();
                xsd::isUnknownAudioFormat(info.base.format)
                || xsd::isUnknownAudioChannelMask(info.base.channelMask)
                || (!info.streamType.empty() && xsd::isUnknownAudioStreamType(info.streamType))
                || xsd::isUnknownAudioUsage(info.usage)) {
            return false;
        }
    }
    return true;
}

bool checkAudioConfig(const bool isOut,
                      size_t duration_ms,
                      size_t extraAlignment,
                      const AudioConfig &src,
                      AudioConfig &suggested) {
    bool result = true;
    suggested = src;

    if (!checkSampleRateHz(src.base.sampleRateHz, suggested.base.sampleRateHz)) {
        result = false;
    }

    if (!checkChannelMask(isOut, src.base.channelMask, suggested.base.channelMask)) {
        result = false;
    }

    if (!checkFormat(src.base.format, suggested.base.format)) {
        result = false;
    }

    if (src.frameCount == 0) {
        suggested.frameCount = getBufferSizeFrames(duration_ms, src.base.sampleRateHz,
                                                   extraAlignment);
    }

    return result;
}

bool checkAudioPortConfig(const AudioPortConfig &cfg) {
    if (cfg.base.format.getDiscriminator() ==
            AudioConfigBaseOptional::Format::hidl_discriminator::value) {
        if (xsd::isUnknownAudioFormat(cfg.base.format.value())) {
            return false;
        }
    }
    if (cfg.base.channelMask.getDiscriminator() ==
            AudioConfigBaseOptional::ChannelMask::hidl_discriminator::value) {
        if (xsd::isUnknownAudioChannelMask(cfg.base.channelMask.value())) {
            return false;
        }
    }
    if (cfg.gain.getDiscriminator() ==
            AudioPortConfig::OptionalGain::hidl_discriminator::config) {
        for (const auto& gainMode : cfg.gain.config().mode) {
            if (xsd::isUnknownAudioGainMode(gainMode)) {
                return false;
            }
        }
        if (xsd::isUnknownAudioChannelMask(cfg.gain.config().channelMask)) {
            return false;
        }
    }
    switch (cfg.ext.getDiscriminator()) {
        case AudioPortExtendedInfo::hidl_discriminator::device:
            if (xsd::isUnknownAudioDevice(cfg.ext.device().deviceType)) {
                return false;
            }
            break;
        case AudioPortExtendedInfo::hidl_discriminator::mix:
            switch (cfg.ext.mix().useCase.getDiscriminator()) {
                case AudioPortExtendedInfo::AudioPortMixExt::UseCase::hidl_discriminator::stream:
                    if (const auto& stream = cfg.ext.mix().useCase.stream();
                            !stream.empty() && xsd::isUnknownAudioStreamType(stream)) {
                        return false;
                    }
                    break;
                case AudioPortExtendedInfo::AudioPortMixExt::UseCase::hidl_discriminator::source:
                    if (xsd::isUnknownAudioSource(cfg.ext.mix().useCase.source())) {
                        return false;
                    }
                    break;
            }
            break;
        default:
            break;
    }
    return true;
}

TimeSpec nsecs2TimeSpec(nsecs_t ns) {
    TimeSpec ts;
    ts.tvSec = ns2s(ns);
    ts.tvNSec = ns - s2ns(ts.tvSec);
    return ts;
}

bool setThreadPriority(const SchedPolicy policy, const int prio) {
    int e = set_sched_policy(0, policy);
    if (e < 0) {
        ALOGE("%s:%d set_sched_policy(%d) failed with %s (%d)",
              __func__, __LINE__, static_cast<int>(policy), strerror(-e), -e);
        return FAILURE(false);
    }

    if (setpriority(PRIO_PROCESS, 0, prio) < 0) {
        e = errno;
        ALOGE("%s:%d setpriority(%d) failed with %s (%d)",
              __func__, __LINE__, prio, strerror(-e), -e);
        return FAILURE(false);
    }

    return true;
}

}  // namespace util
}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
