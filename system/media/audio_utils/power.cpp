/*
 * Copyright 2017 The Android Open Source Project
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

// #define LOG_NDEBUG 0
#define LOG_TAG "audio_utils_power"
#include <log/log.h>

#include <audio_utils/power.h>

#include <audio_utils/intrinsic_utils.h>
#include <audio_utils/primitives.h>

#if defined(__aarch64__) || defined(__ARM_NEON__)
#define USE_NEON
#endif

namespace {

constexpr inline bool isFormatSupported(audio_format_t format) {
    switch (format) {
    case AUDIO_FORMAT_PCM_8_BIT:
    case AUDIO_FORMAT_PCM_16_BIT:
    case AUDIO_FORMAT_PCM_24_BIT_PACKED:
    case AUDIO_FORMAT_PCM_8_24_BIT:
    case AUDIO_FORMAT_PCM_32_BIT:
    case AUDIO_FORMAT_PCM_FLOAT:
        return true;
    default:
        return false;
    }
}

template <typename T>
inline T getPtrPtrValueAndIncrement(const void **data)
{
    return *(*reinterpret_cast<const T **>(data))++;
}

template <audio_format_t FORMAT>
inline float convertToFloatAndIncrement(const void **data)
{
    switch (FORMAT) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return float_from_u8(getPtrPtrValueAndIncrement<uint8_t>(data));

    case AUDIO_FORMAT_PCM_16_BIT:
        return float_from_i16(getPtrPtrValueAndIncrement<int16_t>(data));

    case AUDIO_FORMAT_PCM_24_BIT_PACKED: {
        const uint8_t *uptr = reinterpret_cast<const uint8_t *>(*data);
        *data = uptr + 3;
        return float_from_p24(uptr);
    }

    case AUDIO_FORMAT_PCM_8_24_BIT:
        return float_from_q8_23(getPtrPtrValueAndIncrement<int32_t>(data));

    case AUDIO_FORMAT_PCM_32_BIT:
        return float_from_i32(getPtrPtrValueAndIncrement<int32_t>(data));

    case AUDIO_FORMAT_PCM_FLOAT:
        return getPtrPtrValueAndIncrement<float>(data);

    default:
        // static_assert cannot use false because the compiler may interpret it
        // even though this code path may never be taken.
        static_assert(isFormatSupported(FORMAT), "unsupported format");
    }
}

// used to normalize integer fixed point value to the floating point equivalent.
template <audio_format_t FORMAT>
constexpr inline float normalizeAmplitude()
{
    switch (FORMAT) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return 1.f / (1 << 7);

    case AUDIO_FORMAT_PCM_16_BIT:
        return 1.f / (1 << 15);

    case AUDIO_FORMAT_PCM_24_BIT_PACKED: // fall through
    case AUDIO_FORMAT_PCM_8_24_BIT:
        return 1.f / (1 << 23);

    case AUDIO_FORMAT_PCM_32_BIT:
        return 1.f / (1U << 31);

    case AUDIO_FORMAT_PCM_FLOAT:
         return 1.f;

    default:
        // static_assert cannot use false because the compiler may interpret it
        // even though this code path may never be taken.
        static_assert(isFormatSupported(FORMAT), "unsupported format");
    }
}

template <audio_format_t FORMAT>
constexpr inline float normalizeEnergy()
{
    const float val = normalizeAmplitude<FORMAT>();
    return val * val;
}

template <audio_format_t FORMAT>
inline float energyMonoRef(const void *amplitudes, size_t size)
{
    float accum(0.f);
    for (size_t i = 0; i < size; ++i) {
        const float amplitude = convertToFloatAndIncrement<FORMAT>(&amplitudes);
        accum += amplitude * amplitude;
    }
    return accum;
}

template <audio_format_t FORMAT>
inline void energyRef(const void *amplitudes, size_t size, size_t numChannels, float* out)
{
    const size_t framesSize = size / numChannels;
    for (size_t i = 0; i < framesSize; ++i) {
        for (size_t c = 0; c < numChannels; ++c) {
            const float amplitude = convertToFloatAndIncrement<FORMAT>(&amplitudes);
            out[c] += amplitude * amplitude;
        }
    }
}

template <audio_format_t FORMAT>
inline float energyMono(const void *amplitudes, size_t size)
{
    return energyMonoRef<FORMAT>(amplitudes, size);
}

// TODO: optimize with NEON
template <audio_format_t FORMAT>
inline void energy(const void *amplitudes, size_t size, size_t numChannels, float* out)
{
    energyRef<FORMAT>(amplitudes, size, numChannels, out);
}

// TODO(b/323611666) in some cases having a large kVectorWidth generic internal array is
// faster than the NEON intrinsic version.  Optimize this.
#ifdef USE_NEON
// The type conversion appears faster if we use a neon accumulator type.
// Using a vector length of 4 triggers the code below to use the neon type float32x4_t.
constexpr size_t kVectorWidth16 = 4;     // neon float32x4_t
constexpr size_t kVectorWidth32 = 4;     // neon float32x4_t
constexpr size_t kVectorWidthFloat = 8;  // use generic intrinsics for float.
#else
constexpr size_t kVectorWidth16 = 8;
constexpr size_t kVectorWidth32 = 8;
constexpr size_t kVectorWidthFloat = 8;
#endif

template <typename Scalar, size_t N>
inline float energyMonoVector(const void *amplitudes, size_t size)
{    // check pointer validity, must be aligned with scalar type.
    const Scalar *samplitudes = reinterpret_cast<const Scalar *>(amplitudes);
    LOG_ALWAYS_FATAL_IF((uintptr_t)samplitudes % alignof(Scalar) != 0,
            "Non-element aligned address: %p %zu", samplitudes, alignof(Scalar));

    float accumulator = 0;

#ifdef USE_NEON
    using AccumulatorType = std::conditional_t<N == 4, float32x4_t,
            android::audio_utils::intrinsics::internal_array_t<float, N>>;
#else
    using AccumulatorType = android::audio_utils::intrinsics::internal_array_t<float, N>;
#endif

    // seems that loading input data is fine using our generic intrinsic.
    using Vector = android::audio_utils::intrinsics::internal_array_t<Scalar, N>;

    // handle pointer unaligned to vector type.
    while ((uintptr_t)samplitudes % sizeof(Vector) != 0 /* compiler optimized */ && size > 0) {
        const float amp = (float)*samplitudes++;
        accumulator += amp * amp;
        --size;
    }

    // samplitudes is now adjusted for proper vector alignment, cast to Vector *
    const Vector *vamplitudes = reinterpret_cast<const Vector *>(samplitudes);

    // clear vector accumulator
    AccumulatorType accum{};

    // iterate over array getting sum of squares in vectorLength lanes.
    size_t i;
    const size_t limit = size - size % N;
    for (i = 0; i < limit; i += N) {
        const auto famplitude = vconvert<AccumulatorType>(*vamplitudes++);
        accum = android::audio_utils::intrinsics::vmla(accum, famplitude, famplitude);
    }

    // add all components of the vector.
    accumulator += android::audio_utils::intrinsics::vaddv(accum);

    // accumulate any trailing elements too small for vector size
    for (; i < size; ++i) {
        const float amp = (float)samplitudes[i];
        accumulator += amp * amp;
    }
    return accumulator;
}

template <>
inline float energyMono<AUDIO_FORMAT_PCM_FLOAT>(const void *amplitudes, size_t size)
{
    return energyMonoVector<float, kVectorWidthFloat>(amplitudes, size);
}

template <>
inline float energyMono<AUDIO_FORMAT_PCM_16_BIT>(const void *amplitudes, size_t size)
{
    return energyMonoVector<int16_t, kVectorWidth16>(amplitudes, size)
            * normalizeEnergy<AUDIO_FORMAT_PCM_16_BIT>();
}

// fast int32_t power computation for PCM_32
template <>
inline float energyMono<AUDIO_FORMAT_PCM_32_BIT>(const void *amplitudes, size_t size)
{
    return energyMonoVector<int32_t, kVectorWidth32>(amplitudes, size)
            * normalizeEnergy<AUDIO_FORMAT_PCM_32_BIT>();
}

// fast int32_t power computation for PCM_8_24 (essentially identical to PCM_32 above)
template <>
inline float energyMono<AUDIO_FORMAT_PCM_8_24_BIT>(const void *amplitudes, size_t size)
{
    return energyMonoVector<int32_t, kVectorWidth32>(amplitudes, size)
            * normalizeEnergy<AUDIO_FORMAT_PCM_8_24_BIT>();
}

} // namespace

float audio_utils_compute_energy_mono(const void *buffer, audio_format_t format, size_t samples)
{
    switch (format) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return energyMono<AUDIO_FORMAT_PCM_8_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_16_BIT:
        return energyMono<AUDIO_FORMAT_PCM_16_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_24_BIT_PACKED:
        return energyMono<AUDIO_FORMAT_PCM_24_BIT_PACKED>(buffer, samples);

    case AUDIO_FORMAT_PCM_8_24_BIT:
        return energyMono<AUDIO_FORMAT_PCM_8_24_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_32_BIT:
        return energyMono<AUDIO_FORMAT_PCM_32_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_FLOAT:
        return energyMono<AUDIO_FORMAT_PCM_FLOAT>(buffer, samples);

    default:
        LOG_ALWAYS_FATAL("invalid format: %#x", format);
    }
}

void audio_utils_accumulate_energy(const void* buffer,
                                   audio_format_t format,
                                   size_t samples,
                                   size_t numChannels,
                                   float* out)
{
    switch (format) {
    case AUDIO_FORMAT_PCM_8_BIT:
        energy<AUDIO_FORMAT_PCM_8_BIT>(buffer, samples, numChannels, out);
        break;

    case AUDIO_FORMAT_PCM_16_BIT:
        energy<AUDIO_FORMAT_PCM_16_BIT>(buffer, samples, numChannels, out);
        break;

    case AUDIO_FORMAT_PCM_24_BIT_PACKED:
        energy<AUDIO_FORMAT_PCM_24_BIT_PACKED>(buffer, samples, numChannels, out);
        break;

    case AUDIO_FORMAT_PCM_8_24_BIT:
        energy<AUDIO_FORMAT_PCM_8_24_BIT>(buffer, samples, numChannels, out);
        break;

    case AUDIO_FORMAT_PCM_32_BIT:
        energy<AUDIO_FORMAT_PCM_32_BIT>(buffer, samples, numChannels, out);
        break;

    case AUDIO_FORMAT_PCM_FLOAT:
        energy<AUDIO_FORMAT_PCM_FLOAT>(buffer, samples, numChannels, out);
        break;

    default:
        LOG_ALWAYS_FATAL("invalid format: %#x", format);
    }
}

float audio_utils_compute_power_mono(const void *buffer, audio_format_t format, size_t samples)
{
    return audio_utils_power_from_energy(
            audio_utils_compute_energy_mono(buffer, format, samples) / samples);
}

bool audio_utils_is_compute_power_format_supported(audio_format_t format)
{
    return isFormatSupported(format);
}
