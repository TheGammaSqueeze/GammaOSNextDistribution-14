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

#pragma once
#include <array>
#include PATH(android/hardware/audio/common/COMMON_TYPES_FILE_VERSION/types.h)
#include PATH(android/hardware/audio/CORE_TYPES_FILE_VERSION/types.h)
#include <utils/Timers.h>
#include <cutils/sched_policy.h>

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {
namespace util {

using ::android::hardware::hidl_bitfield;
using ::android::hardware::hidl_string;
using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioFormat;
using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioChannelMask;
using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioConfig;
using ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION::AudioPortConfig;
using ::android::hardware::audio::CORE_TYPES_CPP_VERSION::MicrophoneInfo;
using ::android::hardware::audio::CORE_TYPES_CPP_VERSION::TimeSpec;

MicrophoneInfo getMicrophoneInfo();

size_t countChannels(const AudioChannelMask &mask);
size_t getBytesPerSample(const AudioFormat &format);

bool checkAudioConfig(const AudioConfig &cfg);
bool checkAudioConfig(bool isOut,
                      size_t duration_ms,
                      size_t extraAlignment,
                      const AudioConfig &cfg,
                      AudioConfig &suggested);

bool checkAudioPortConfig(const AudioPortConfig& cfg);

TimeSpec nsecs2TimeSpec(nsecs_t);

inline constexpr nsecs_t timespec2Nsecs(const TimeSpec &ts) {
    return s2ns(ts.tvSec) + ts.tvNSec;
}

bool setThreadPriority(SchedPolicy policy, int prio);

}  // namespace util
}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
