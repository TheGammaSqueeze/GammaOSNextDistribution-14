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
#pragma once

#include <aidl/android/hardware/vibrator/BnVibrator.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

constexpr int32_t COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS = 16383;

constexpr uint32_t WT_LEN_CALCD = 0x00800000;
constexpr uint8_t PWLE_CHIRP_BIT = 0x8;  // Dynamic/frequency and voltage
constexpr uint8_t PWLE_BRAKE_BIT = 0x4;
constexpr uint8_t PWLE_AMP_REG_BIT = 0x2;

static constexpr uint8_t PWLE_WT_TYPE = 12;
static constexpr uint8_t PWLE_HEADER_WORD_COUNT = 3;
static constexpr uint8_t PWLE_HEADER_FTR_SHIFT = 8;
static constexpr uint8_t PWLE_SVC_METADATA_WORD_COUNT = 3;
static constexpr uint32_t PWLE_SVC_METADATA_TERMINATOR = 0xFFFFFF;
static constexpr uint8_t PWLE_SEGMENT_WORD_COUNT = 2;
static constexpr uint8_t PWLE_HEADER_WCOUNT_WORD_OFFSET = 2;
static constexpr uint8_t PWLE_WORD_SIZE = sizeof(uint32_t);

static constexpr uint8_t PWLE_SVC_NO_BRAKING = -1;
static constexpr uint8_t PWLE_SVC_CAT_BRAKING = 0;
static constexpr uint8_t PWLE_SVC_OPEN_BRAKING = 1;
static constexpr uint8_t PWLE_SVC_CLOSED_BRAKING = 2;
static constexpr uint8_t PWLE_SVC_MIXED_BRAKING = 3;

static constexpr uint32_t PWLE_SVC_MAX_BRAKING_TIME_MS = 1000;

static constexpr uint8_t PWLE_FTR_BUZZ_BIT = 0x80;
static constexpr uint8_t PWLE_FTR_CLICK_BIT = 0x00;
static constexpr uint8_t PWLE_FTR_DYNAMIC_F0_BIT = 0x10;
static constexpr uint8_t PWLE_FTR_SVC_METADATA_BIT = 0x04;
static constexpr uint8_t PWLE_FTR_DVL_BIT = 0x02;
static constexpr uint8_t PWLE_FTR_LF0T_BIT = 0x01;

constexpr float CS40L26_PWLE_LEVEL_MIN = -1.0;
constexpr float CS40L26_PWLE_LEVEL_MAX = 0.9995118;

constexpr float PWLE_FREQUENCY_MIN_HZ = 30.0f;
constexpr float PWLE_FREQUENCY_MAX_HZ = 300.0f;

/* nsections is 8 bits. Need to preserve 1 section for the first delay before the first effect. */
static constexpr int32_t COMPOSE_SIZE_MAX = 254;
static constexpr int32_t COMPOSE_PWLE_SIZE_MAX_DEFAULT = 127;

class DspMemChunk {
  public:
    DspMemChunk(uint8_t type, size_t size);

    uint8_t *front() const { return head.get(); }
    uint8_t type() const { return waveformType; }
    size_t size() const { return bytes; }

    int flush();

    int constructComposeSegment(uint32_t effectVolLevel, uint32_t effectIndex, uint8_t repeat,
                                uint8_t flags, uint16_t nextEffectDelay);
    int constructActiveSegment(int duration, float amplitude, float frequency, bool chirp);
    int constructBrakingSegment(int duration, Braking brakingType);

    int updateWLength(uint32_t totalDuration);
    int updateNSection(int segmentIdx);
    int updateWCount(int segmentCount);

  private:
    std::unique_ptr<uint8_t[]> head;
    size_t bytes = 0;
    uint8_t waveformType;
    uint8_t *_current;
    const uint8_t *_max;
    uint32_t _cache = 0;
    int _cachebits = 0;

    bool isEnd() const { return _current == _max; }
    int min(int x, int y) { return x < y ? x : y; }

    int write(int nbits, uint32_t val);

    int fToU16(float input, uint16_t *output, float scale, float min, float max);

    void constructPwleSegment(uint16_t delay, uint16_t amplitude, uint16_t frequency, uint8_t flags,
                              uint32_t vbemfTarget = 0);
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
