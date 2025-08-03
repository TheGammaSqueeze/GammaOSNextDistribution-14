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

#include "DspMemChunk.h"

#include <linux/version.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <cmath>

#include "Trace.h"

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

#ifdef VIBRATOR_TRACE
/* Function Trace */
#define VFTRACE(...)                                                             \
    ATRACE_NAME(StringPrintf("Vibrator::%s", __func__).c_str());                 \
    auto f_trace_ = std::make_unique<FunctionTrace>("Vibrator", __func__);       \
    __VA_OPT__(f_trace_->addParameter(PREPEND_EACH_ARG_WITH_NAME(__VA_ARGS__))); \
    f_trace_->save()
/* Effect Trace */
#define VETRACE(i, s, d, ch)                                    \
    auto e_trace_ = std::make_unique<EffectTrace>(i, s, d, ch); \
    e_trace_->save()
#else
#define VFTRACE(...) ATRACE_NAME(StringPrintf("Vibrator::%s", __func__).c_str())
#define VETRACE(...)
#endif

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
     * #define FF_GAIN      0x60  // 96 in decimal
     * #define FF_MAX_EFFECTS   FF_GAIN
     */
    WAVEFORM_MAX_INDEX,
};

DspMemChunk::DspMemChunk(uint8_t type, size_t size) : head(new uint8_t[size]{0x00}) {
    VFTRACE(type, size);
    waveformType = type;
    _current = head.get();
    _max = _current + size;

    if (waveformType == WAVEFORM_COMPOSE) {
        write(8, 0); /* Padding */
        write(8, 0); /* nsections placeholder */
        write(8, 0); /* repeat */
    } else if (waveformType == WAVEFORM_PWLE) {
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)
        write(16, (PWLE_FTR_BUZZ_BIT | PWLE_FTR_DVL_BIT)
                          << PWLE_HEADER_FTR_SHIFT); /* Feature flag */
        write(8, PWLE_WT_TYPE);                      /* type12 */
        write(24, PWLE_HEADER_WORD_COUNT);           /* Header word count */
        write(24, 0);                                /* Body word count placeholder */
#endif
        write(24, 0); /* Waveform length placeholder */
        write(8, 0);  /* Repeat */
        write(12, 0); /* Wait time between repeats */
        write(8, 0);  /* nsections placeholder */
    } else {
        ALOGE("%s: Invalid type: %u", __func__, waveformType);
    }
}

int DspMemChunk::write(int nbits, uint32_t val) {
    VFTRACE(nbits, val);
    int nwrite;

    nwrite = min(24 - _cachebits, nbits);
    _cache <<= nwrite;
    _cache |= val >> (nbits - nwrite);
    _cachebits += nwrite;
    nbits -= nwrite;

    if (_cachebits == 24) {
        if (isEnd())
            return -ENOSPC;

        _cache &= 0xFFFFFF;
        for (size_t i = 0; i < sizeof(_cache); i++, _cache <<= 8)
            *_current++ = (_cache & 0xFF000000) >> 24;

        bytes += sizeof(_cache);
        _cachebits = 0;
    }

    if (nbits)
        return write(nbits, val);

    return 0;
}

int DspMemChunk::fToU16(float input, uint16_t *output, float scale, float min, float max) {
    VFTRACE(input, output, scale, min, max);
    if (input < min || input > max)
        return -ERANGE;

    *output = roundf(input * scale);
    return 0;
}

void DspMemChunk::constructPwleSegment(uint16_t delay, uint16_t amplitude, uint16_t frequency,
                                       uint8_t flags, uint32_t vbemfTarget) {
    VFTRACE(delay, amplitude, frequency, flags, vbemfTarget);
    write(16, delay);
    write(12, amplitude);
    write(12, frequency);
    /* feature flags to control the chirp, CLAB braking, back EMF amplitude regulation */
    write(8, (flags | 1) << 4);
    if (flags & PWLE_AMP_REG_BIT) {
        write(24, vbemfTarget); /* target back EMF voltage */
    }
}

int DspMemChunk::flush() {
    VFTRACE();
    if (!_cachebits)
        return 0;

    return write(24 - _cachebits, 0);
}

int DspMemChunk::constructComposeSegment(uint32_t effectVolLevel, uint32_t effectIndex,
                                         uint8_t repeat, uint8_t flags, uint16_t nextEffectDelay) {
    VFTRACE(effectVolLevel, effectIndex, repeat, flags, nextEffectDelay);
    if (waveformType != WAVEFORM_COMPOSE) {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }
    if (effectVolLevel > 100 || effectIndex > WAVEFORM_MAX_PHYSICAL_INDEX) {
        ALOGE("%s: Invalid argument: %u, %u", __func__, effectVolLevel, effectIndex);
        return -EINVAL;
    }
    write(8, effectVolLevel);   /* amplitude */
    write(8, effectIndex);      /* index */
    write(8, repeat);           /* repeat */
    write(8, flags);            /* flags */
    write(16, nextEffectDelay); /* delay */
    return 0;
}

int DspMemChunk::constructActiveSegment(int duration, float amplitude, float frequency,
                                        bool chirp) {
    VFTRACE(duration, amplitude, frequency, chirp);
    uint16_t delay = 0;
    uint16_t amp = 0;
    uint16_t freq = 0;
    uint8_t flags = 0x0;
    if (waveformType != WAVEFORM_PWLE) {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }
    if ((fToU16(duration, &delay, 4, 0.0f, COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS) < 0) ||
        (fToU16(amplitude, &amp, 2048, CS40L26_PWLE_LEVEL_MIN, CS40L26_PWLE_LEVEL_MAX) < 0) ||
        (fToU16(frequency, &freq, 4, PWLE_FREQUENCY_MIN_HZ, PWLE_FREQUENCY_MAX_HZ) < 0)) {
        ALOGE("%s: Invalid argument: %d, %f, %f", __func__, duration, amplitude, frequency);
        return -ERANGE;
    }
    if (chirp) {
        flags |= PWLE_CHIRP_BIT;
    }
    constructPwleSegment(delay, amp, freq, flags, 0 /*ignored*/);
    return 0;
}

int DspMemChunk::constructBrakingSegment(int duration, Braking brakingType) {
    VFTRACE(duration, brakingType);
    uint16_t delay = 0;
    uint16_t freq = 0;
    uint8_t flags = 0x00;
    if (waveformType != WAVEFORM_PWLE) {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }
    if (fToU16(duration, &delay, 4, 0.0f, COMPOSE_PWLE_PRIMITIVE_DURATION_MAX_MS) < 0) {
        ALOGE("%s: Invalid argument: %d", __func__, duration);
        return -ERANGE;
    }
    fToU16(PWLE_FREQUENCY_MIN_HZ, &freq, 4, PWLE_FREQUENCY_MIN_HZ, PWLE_FREQUENCY_MAX_HZ);
    if (static_cast<std::underlying_type<Braking>::type>(brakingType)) {
        flags |= PWLE_BRAKE_BIT;
    }

    constructPwleSegment(delay, 0 /*ignored*/, freq, flags, 0 /*ignored*/);
    return 0;
}

int DspMemChunk::updateWLength(uint32_t totalDuration) {
    VFTRACE(totalDuration);
    uint8_t *f = front();
    if (f == nullptr) {
        ALOGE("%s: head does not exist!", __func__);
        return -ENOMEM;
    }
    if (waveformType != WAVEFORM_PWLE) {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }
    if (totalDuration > 0x7FFFF) {
        ALOGE("%s: Invalid argument: %u", __func__, totalDuration);
        return -EINVAL;
    }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)
    f += PWLE_HEADER_WORD_COUNT * PWLE_WORD_SIZE;
#endif
    totalDuration *= 8;            /* Unit: 0.125 ms (since wlength played @ 8kHz). */
    totalDuration |= WT_LEN_CALCD; /* Bit 23 is for WT_LEN_CALCD; Bit 22 is for WT_INDEFINITE. */
    *(f + 0) = (totalDuration >> 24) & 0xFF;
    *(f + 1) = (totalDuration >> 16) & 0xFF;
    *(f + 2) = (totalDuration >> 8) & 0xFF;
    *(f + 3) = totalDuration & 0xFF;
    return 0;
}

int DspMemChunk::updateNSection(int segmentIdx) {
    VFTRACE(segmentIdx);
    uint8_t *f = front();
    if (f == nullptr) {
        ALOGE("%s: head does not exist!", __func__);
        return -ENOMEM;
    }

    if (waveformType == WAVEFORM_COMPOSE) {
        if (segmentIdx > COMPOSE_SIZE_MAX + 1 /*1st effect may have a delay*/) {
            ALOGE("%s: Invalid argument: %d", __func__, segmentIdx);
            return -EINVAL;
        }
        *(f + 2) = (0xFF & segmentIdx);
    } else if (waveformType == WAVEFORM_PWLE) {
        if (segmentIdx > COMPOSE_PWLE_SIZE_MAX_DEFAULT) {
            ALOGE("%s: Invalid argument: %d", __func__, segmentIdx);
            return -EINVAL;
        }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)
        f += PWLE_HEADER_WORD_COUNT * PWLE_WORD_SIZE;
#endif
        *(f + 7) |= (0xF0 & segmentIdx) >> 4; /* Bit 4 to 7 */
        *(f + 9) |= (0x0F & segmentIdx) << 4; /* Bit 3 to 0 */
    } else {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }

    return 0;
}

int DspMemChunk::updateWCount(int segmentCount) {
    uint8_t *f = front();

    if (segmentCount > COMPOSE_SIZE_MAX + 1 /*1st effect may have a delay*/) {
        ALOGE("%s: Invalid argument: %d", __func__, segmentCount);
        return -EINVAL;
    }
    if (f == nullptr) {
        ALOGE("%s: head does not exist!", __func__);
        return -ENOMEM;
    }
    if (waveformType != WAVEFORM_PWLE) {
        ALOGE("%s: Invalid type: %d", __func__, waveformType);
        return -EDOM;
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)
    f += PWLE_HEADER_WORD_COUNT * PWLE_WORD_SIZE;
#endif
    uint32_t dataSize = segmentCount * PWLE_SEGMENT_WORD_COUNT + PWLE_HEADER_WORD_COUNT;
    *(f + 0) = (dataSize >> 24) & 0xFF;
    *(f + 1) = (dataSize >> 16) & 0xFF;
    *(f + 2) = (dataSize >> 8) & 0xFF;
    *(f + 3) = dataSize & 0xFF;

    return 0;
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
