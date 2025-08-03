/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "intrinsic_utils.h"

#include <array>
#include <cmath>
#include <functional>
#include <utility>
#include <vector>

#include <assert.h>

// We conditionally include neon optimizations for ARM devices
#pragma push_macro("USE_NEON")
#undef USE_NEON

#if defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define USE_NEON
#endif

// Use dither to prevent subnormals for CPUs that raise an exception.
#pragma push_macro("USE_DITHER")
#undef USE_DITHER

#if defined(__i386__) || defined(__x86_x64__)
#define USE_DITHER
#endif

namespace android::audio_utils {

static constexpr size_t kBiquadNumCoefs  = 5;
static constexpr size_t kBiquadNumDelays = 2;

/**
 * The BiquadDirect2Transpose is a low overhead
 * Biquad filter with coefficients b0, b1, b2, a1, a2.
 *
 * This can be used by itself, but it is preferred for best data management
 * to use the BiquadFilter abstraction below.
 *
 * T is the data type (scalar or vector).
 * F is the filter coefficient type.  It is either a scalar or vector (matching T).
 */
template <typename T, typename F>
struct BiquadDirect2Transpose {
    F coef_[5]; // these are stored with the denominator a's negated.
    T s_[2]; // delay states

    // These are the coefficient occupancies we optimize for (from b0, b1, b2, a1, a2)
    // as expressed by a bitmask.
    static inline constexpr size_t required_occupancies_[] = {
        0x1,  // constant scale
        0x3,  // single zero
        0x7,  // double zero
        0x9,  // single pole
        0xb,  // (11) first order IIR
        0x1b, // (27) double pole + single zero
        0x1f, // (31) second order IIR (full Biquad)
    };

    // Take care the order of arguments - starts with b's then goes to a's.
    // The a's are "positive" reference, some filters take negative.
    BiquadDirect2Transpose(const F& b0, const F& b1, const F& b2, const F& a1, const F& a2,
            const T& s0 = {}, const T& s1 = {})
        // : coef_{b0, b1, b2, -a1, -a2}
        : coef_{ b0,
            b1,
            b2,
            intrinsics::vneg(a1),
            intrinsics::vneg(a2) }
        , s_{ s0, s1 } {
    }

    // D is the data type.  It must be the same element type of T or F.
    // Take care the order of input and output.
    template<typename D, size_t OCCUPANCY = 0x1f>
    __attribute__((always_inline)) // required for 1ch speedup (30% faster)
    void process(D* output, const D* input, size_t frames, size_t stride) {
        using namespace intrinsics;
        // For SSE it is possible to vdup F to T if F is scalar.
        const F b0 = coef_[0];         // b0
        const F b1 = coef_[1];         // b1
        const F b2 = coef_[2];         // b2
        const F negativeA1 = coef_[3]; // -a1
        const F negativeA2 = coef_[4]; // -a2
        T s[2] = { s_[0], s_[1] };
        T xn, yn; // OK to declare temps outside loop rather than at the point of initialization.
#ifdef USE_DITHER
        constexpr D DITHER_VALUE = std::numeric_limits<float>::min() * (1 << 24); // use FLOAT
        T dither = vdupn<T>(DITHER_VALUE); // NEON does not have vector + scalar acceleration.
#endif

        // Unroll control.  Make sure the constexpr remains constexpr :-).
        constexpr size_t CHANNELS = sizeof(T) / sizeof(D);
        constexpr size_t UNROLL_CHANNEL_LOWER_LIMIT = 2;   // below this won't be unrolled.
        constexpr size_t UNROLL_CHANNEL_UPPER_LIMIT = 16;  // above this won't be unrolled.
        constexpr size_t UNROLL_LOOPS = (CHANNELS >= UNROLL_CHANNEL_LOWER_LIMIT &&
                CHANNELS <= UNROLL_CHANNEL_UPPER_LIMIT) ? 2 : 1;
        size_t remainder = 0;
        if constexpr (UNROLL_LOOPS > 1) {
            remainder = frames % UNROLL_LOOPS;
            frames /= UNROLL_LOOPS;
        }

        // For this lambda, attribute always_inline must be used to inline past CHANNELS > 4.
        // The other alternative is to use a MACRO, but that doesn't read as well.
        const auto KERNEL = [&]() __attribute__((always_inline)) {
            xn = vld1<T>(input);
            input += stride;
#ifdef USE_DITHER
            xn = vadd(xn, dither);
            dither = vneg(dither);
#endif

            yn = s[0];
            if constexpr (OCCUPANCY >> 0 & 1) {
                yn = vmla(yn, b0, xn);
            }
            vst1(output, yn);
            output += stride;

            s[0] = s[1];
            if constexpr (OCCUPANCY >> 3 & 1) {
                s[0] = vmla(s[0], negativeA1, yn);
            }
            if constexpr (OCCUPANCY >> 1 & 1) {
                s[0] = vmla(s[0], b1, xn);
            }
            if constexpr (OCCUPANCY >> 2 & 1) {
                s[1] = vmul(b2, xn);
            } else {
                s[1] = vdupn<T>(0.f);
            }
            if constexpr (OCCUPANCY >> 4 & 1) {
                s[1] = vmla(s[1], negativeA2, yn);
            }
        };

        while (frames > 0) {
            #pragma unroll
            for (size_t i = 0; i < UNROLL_LOOPS; ++i) {
                KERNEL();
            }
            frames--;
        }
        if constexpr (UNROLL_LOOPS > 1) {
            for (size_t i = 0; i < remainder; ++i) {
                KERNEL();
            }
        }
        s_[0] = s[0];
        s_[1] = s[1];
    }
};

/**
 * A state space formulation of filtering converts a n-th order difference equation update
 * to a first order vector difference equation. For the Biquad filter, the state space form
 * has improved numerical precision properties with poles near the unit circle as well as
 * increased speed due to better parallelization of the state update [1][2].
 *
 * [1] Raph Levien: (observerable canonical form)
 * https://github.com/google/music-synthesizer-for-android/blob/master/lab/biquad%20in%20two.ipynb
 *
 * [2] Julius O Smith III: (controllable canonical form)
 * https://ccrma.stanford.edu/~jos/filters/State_Space_Filters.html
 *
 * The signal flow is as follows, where for scalar x and y, the matrix D is a scalar d.
 *
 *
 *        +------[ d ]--------------------------+
 *        |                         S           |
 *  x ----+--[ B ]--(+)--[ z^-1 ]---+---[ C ]--(+)--- y
 *                   |              |
 *                   +----[ A ]-----+
 *
 * The 2nd order Biquad IIR coefficients are as follows in observerable canonical form:
 *
 * y = [C_1 C_2] | S_1 | + d x
 *               | S_2 |
 *
 *
 * | S_1 | = | A_11 A_12 | | S_1 | + | B_1 | x
 * | S_2 |   | A_21 A_22 | | S_2 |   | B_2 |
 *
 *
 * A_11 = -a1
 * A_12 = 1
 * A_21 = -a2
 * A_22 = 0
 *
 * B_1 = b1 - b0 * a1
 * B_2 = b2 - b0 * a2
 *
 * C_1 = 1
 * C_2 = 0
 *
 * d = b0
 *
 * Implementation details: The state space filter is typically expressed in either observable or
 * controllable canonical form [3].  We follow the observable form here.
 * Raph [4] discovered that the single channel Biquad implementation can be further optimized
 * by doing 2 filter updates at once (improving speed on NEON by about 20%).
 * Doing 2 updates at once costs 8 multiplies / sample instead of 5 multiples / sample,
 * but uses a 4 x 4 matrix multiply, exploiting single cycle multiply-add SIMD throughput.
 *
 * [3] Mathworks
 * https://www.mathworks.com/help/control/ug/canonical-state-space-realizations.html
 * [4] Raph Levien
 * https://github.com/kysko/music-synthesizer-for-android/blob/master/lab/biquad%20in%20two.ipynb
 *
 * The template variables
 * T is the data type (scalar or vector).
 * F is the filter coefficient type.  It is either a scalar or vector (matching T).
 */
template <typename T, typename F, bool SEPARATE_CHANNEL_OPTIMIZATION = false>
struct BiquadStateSpace {
    F coef_[5]; // these are stored as state-space converted.
    T s_[2]; // delay states

    // These are the coefficient occupancies we optimize for (from b0, b1, b2, a1, a2)
    // as expressed by a bitmask.  This must include 31.
    static inline constexpr size_t required_occupancies_[] = {
        1,  // constant scale
        3,  // single zero
        7,  // double zero
        9,  // single pole
        11, // first order IIR
        27, // double pole + single zero
        31, // second order IIR (full Biquad)
    };

    // Take care the order of arguments - starts with b's then goes to a's.
    // The a's are "positive" reference, some filters take negative.
    BiquadStateSpace(const F& b0, const F& b1, const F& b2, const F& a1, const F& a2,
            const T& s0 = {}, const T& s1 = {})
        // : coef_{b0, b1 - b0 * a1, b2 - b0 * a2, -a1, -a2}
        : coef_{ b0,
            intrinsics::vsub(b1, intrinsics::vmul(b0, a1)),
            intrinsics::vsub(b2, intrinsics::vmul(b0, a2)),
            intrinsics::vneg(a1),
            intrinsics::vneg(a2) }
        , s_{s0, s1} {
    }

    // D is the data type.  It must be the same element type of T or F.
    // Take care the order of input and output.
    template<typename D, size_t OCCUPANCY = 0x1f>
    void process(D* output, const D* input, size_t frames, size_t stride) {
        using namespace intrinsics;
        const F b0 = coef_[0];         // b0
        const F b1ss = coef_[1];       // b1 - b0 * a1,
        const F b2ss = coef_[2];       // b2 - b0 * a2,
        const F negativeA1 = coef_[3]; // -a1
        const F negativeA2 = coef_[4]; // -a2
        T s[2] = { s_[0], s_[1] };
        T x, new_s0; // OK to declare temps here rather than at the point of initialization.
#ifdef USE_DITHER
        constexpr D DITHER_VALUE = std::numeric_limits<float>::min() * (1 << 24); // use FLOAT
        T dither = vdupn<T>(DITHER_VALUE); // NEON does not have vector + scalar acceleration.
#endif
        constexpr bool b0_present = (OCCUPANCY & 0x1) != 0;
        constexpr bool a1_present = (OCCUPANCY & 0x8) != 0;
        constexpr bool a2_present = (OCCUPANCY & 0x10) != 0;
        constexpr bool b1ss_present = (OCCUPANCY & 0x2) != 0 ||
                (b0_present && a1_present);
        constexpr bool b2ss_present = (OCCUPANCY & 0x4) != 0 ||
                (b0_present && a2_present);

        // Unroll control.  Make sure the constexpr remains constexpr :-).
        constexpr size_t CHANNELS = sizeof(T) / sizeof(D);
        constexpr size_t UNROLL_CHANNEL_LOWER_LIMIT = 1;   // below this won't be unrolled.
        constexpr size_t UNROLL_CHANNEL_UPPER_LIMIT = 16;  // above this won't be unrolled.
        constexpr size_t UNROLL_LOOPS = (CHANNELS >= UNROLL_CHANNEL_LOWER_LIMIT &&
                CHANNELS <= UNROLL_CHANNEL_UPPER_LIMIT) ? 2 : 1;

        if constexpr (SEPARATE_CHANNEL_OPTIMIZATION && CHANNELS == 1 && OCCUPANCY >= 11) {
            // Special acceleration which computes 2 samples at a time.
            // see reference [4] for computation of this matrix.
            intrinsics::internal_array_t<T, 4> A[4] = {
                {b0, b1ss, negativeA1 * b1ss + b2ss, negativeA2 * b1ss},
                {0, b0, b1ss, b2ss},
                {1, negativeA1, negativeA2 + negativeA1 * negativeA1, negativeA1 * negativeA2},
                {0, 1, negativeA1, negativeA2},
                };
            intrinsics::internal_array_t<T, 4> y;
            while (frames > 1) {
                x = vld1<T>(input);
                input += stride;
#ifdef USE_DITHER
                x = vadd(x, dither);
                dither = vneg(dither);
#endif
                y = vmul(A[0], x);

                x = vld1<T>(input);
                input += stride;
#ifdef USE_DITHER
                x = vadd(x, dither);
                dither = vneg(dither);
#endif
                y = vmla(y, A[1], x);
                y = vmla(y, A[2], s[0]);
                y = vmla(y, A[3], s[1]);

                vst1(output, y.v[0]);
                output += stride;

                vst1(output, y.v[1]);
                output += stride;

                s[0] = y.v[2];
                s[1] = y.v[3];
                frames -= 2;
            }
            if (frames == 0) {
                s_[0] = s[0];
                s_[1] = s[1];
                return;
            }
        }

        size_t remainder = 0;
        if constexpr (UNROLL_LOOPS > 1) {
            remainder = frames % UNROLL_LOOPS;
            frames /= UNROLL_LOOPS;
        }

        // For this lambda, attribute always_inline must be used to inline past CHANNELS > 4.
        // The other alternative is to use a MACRO, but that doesn't read as well.
        const auto KERNEL = [&]() __attribute__((always_inline)) {
            x = vld1<T>(input);
            input += stride;
#ifdef USE_DITHER
            x = vadd(x, dither);
            dither = vneg(dither);
#endif
            // vst1(output, vadd(s[0], vmul(b0, x)));
            // output += stride;
            // new_s0 = vadd(vadd(vmul(b1ss, x), vmul(negativeA1, s[0])), s[1]);
            // s[1] = vadd(vmul(b2ss, x), vmul(negativeA2, s[0]));

            if constexpr (b0_present) {
                vst1(output, vadd(s[0], vmul(b0, x)));
            } else /* constexpr */ {
                vst1(output, s[0]);
            }
            output += stride;
            new_s0 = s[1];
            if constexpr (b1ss_present) {
                new_s0 = vadd(new_s0, vmul(b1ss, x));
            }
            if constexpr (a1_present) {
                new_s0 = vadd(new_s0, vmul(negativeA1, s[0]));
            }
            if constexpr (b2ss_present) {
                s[1] = vmul(b2ss, x);
                if constexpr (a2_present) {
                    s[1] = vadd(s[1], vmul(negativeA2, s[0]));
                }
            } else if constexpr (a2_present) {
                s[1] = vmul(negativeA2, s[0]);
            }
            s[0] = new_s0;
        };

        while (frames > 0) {
            #pragma unroll
            for (size_t i = 0; i < UNROLL_LOOPS; ++i) {
                KERNEL();
            }
            frames--;
        }
        if constexpr (UNROLL_LOOPS > 1) {
            for (size_t i = 0; i < remainder; ++i) {
                KERNEL();
            }
        }
        s_[0] = s[0];
        s_[1] = s[1];
    }
};

namespace details {

// Helper methods for constructing a constexpr array of function pointers.
// As function pointers are efficient and have no constructor/destructor
// this is preferred over std::function.
//
// SC stands for SAME_COEF_PER_CHANNEL, a compile time boolean constant.
template <template <size_t, bool, typename ...> typename F, bool SC, size_t... Is>
static inline constexpr auto make_functional_array_from_index_sequence(std::index_sequence<Is...>) {
    using first_t = decltype(&F<0, false>::func);  // type from function
    using result_t = std::array<first_t, sizeof...(Is)>;   // type of array
    return result_t{{F<Is, SC>::func...}};      // initialize with functions.
}

template <template <size_t, bool, typename ...> typename F, size_t M, bool SC>
static inline constexpr auto make_functional_array() {
    return make_functional_array_from_index_sequence<F, SC>(std::make_index_sequence<M>());
}

// Returns true if the poles are stable for a Biquad.
template <typename D>
static inline constexpr bool isStable(const D& a1, const D& a2) {
    return fabs(a2) < D(1) && fabs(a1) < D(1) + a2;
}

// Simplifies Biquad coefficients.
// TODO: consider matched pole/zero cancellation.
//       consider subnormal elimination for Intel processors.
template <typename D, typename T>
std::array<D, kBiquadNumCoefs> reduceCoefficients(const T& coef) {
    std::array<D, kBiquadNumCoefs> lcoef;
    if (coef.size() == kBiquadNumCoefs + 1) {
        // General form of Biquad.
        // Remove matched z^-1 factors in top and bottom (e.g. coefs[0] == coefs[3] == 0).
        size_t offset = 0;
        for (; offset < 2 && coef[offset] == 0 && coef[offset + 3] == 0; ++offset);
        assert(coefs[offset + 3] != 0); // hmm... shouldn't we be causal?

        // Normalize 6 coefficients to 5 for storage.
        lcoef[0] = coef[offset] / coef[offset + 3];
        for (size_t i = 1; i + offset < 3; ++i) {
            lcoef[i] = coef[i + offset] / coef[offset + 3];
            lcoef[i + 2] = coef[i + offset + 3] / coef[offset + 3];
         }
    } else if (coef.size() == kBiquadNumCoefs) {
        std::copy(coef.begin(), coef.end(), lcoef.begin());
    } else {
        assert(coef.size() == kBiquadNumCoefs + 1 || coef.size() == kBiquadNumCoefs);
    }
    return lcoef;
}

// Sets a container of coefficients to storage.
template <typename D, typename T, typename DEST>
static inline void setCoefficients(
        DEST& dest, size_t offset, size_t stride, size_t channelCount, const T& coef) {
    auto lcoef = reduceCoefficients<D, T>(coef);
    // replicate as needed
    for (size_t i = 0; i < kBiquadNumCoefs; ++i) {
        for (size_t j = 0; j < channelCount; ++j) {
            dest[i * stride + offset + j] = lcoef[i];
        }
    }
}

// Helper function to zero channels in the input buffer.
// This is used for the degenerate coefficient case which results in all zeroes.
template <typename D>
void zeroChannels(D *out, size_t frames, size_t stride, size_t channelCount) {
    if (stride == channelCount) {
        memset(out, 0, sizeof(float) * frames * channelCount);
    } else {
        for (size_t i = 0; i < frames; i++) {
            memset(out, 0, sizeof(float) * channelCount);
            out += stride;
        }
    }
}

template <typename ConstOptions,
        size_t OCCUPANCY, bool SAME_COEF_PER_CHANNEL, typename T, typename F>
void biquad_filter_func_impl(F *out, const F *in, size_t frames, size_t stride,
        size_t channelCount, F *delays, const F *coefs, size_t localStride) {
    using namespace android::audio_utils::intrinsics;

    constexpr size_t elements = sizeof(T) / sizeof(F); // how many float elements in T.
    const size_t coefStride = SAME_COEF_PER_CHANNEL ? 1 : localStride;
    using CoefType = std::conditional_t<SAME_COEF_PER_CHANNEL, F, T>;
    using KernelType = typename ConstOptions::template FilterType<T, CoefType>;

    for (size_t i = 0; i < channelCount; i += elements) {
        T s1 = vld1<T>(&delays[0]);
        T s2 = vld1<T>(&delays[localStride]);

        KernelType kernel(
                vld1<CoefType>(coefs), vld1<CoefType>(coefs + coefStride),
                vld1<CoefType>(coefs + coefStride * 2), vld1<CoefType>(coefs + coefStride * 3),
                vld1<CoefType>(coefs + coefStride * 4),
                s1, s2);
        if constexpr (!SAME_COEF_PER_CHANNEL) coefs += elements;
        kernel.template process<F, OCCUPANCY>(&out[i], &in[i], frames, stride);
        vst1(&delays[0], kernel.s_[0]);
        vst1(&delays[localStride], kernel.s_[1]);
        delays += elements;
    }
}

// Find the nearest occupancy mask that includes all the desired bits.
template <typename T, size_t N>
static constexpr size_t nearestOccupancy(T occupancy, const T (&occupancies)[N]) {
    if (occupancy < 32) {
        for (auto test : occupancies) {
            if ((occupancy & test) == occupancy) return test;
        }
    }
    return 31;
}

enum FILTER_OPTION {
    FILTER_OPTION_SCALAR_ONLY = (1 << 0),
};


/**
 * DefaultBiquadConstOptions holds the default set of options for customizing
 * the Biquad filter at compile time.
 *
 * Consider inheriting from this structure and overriding the options
 * desired; this is backward compatible to new options added in the future.
 */
struct DefaultBiquadConstOptions {

    // Sets the Biquad filter type.
    // Can be one of the already defined BiquadDirect2Transpose or BiquadStateSpace
    // filter kernels; also can be a user defined filter kernel as well.
    template <typename T, typename F>
    using FilterType = BiquadStateSpace<T, F, false /* SEPARATE_CHANNEL_OPTIMIZATION */>;
};

#define BIQUAD_FILTER_CASE(N, ... /* type */) \
            case N: { \
                using VectorType = __VA_ARGS__; \
                biquad_filter_func_impl<ConstOptions, \
                        nearestOccupancy(OCCUPANCY, \
                                ConstOptions::template FilterType<VectorType, D> \
                                            ::required_occupancies_), \
                        SAME_COEF_PER_CHANNEL, VectorType>( \
                        out + offset, in + offset, frames, stride, remaining, \
                        delays + offset, c, localStride); \
                goto exit; \
            }

template <typename ConstOptions, size_t OCCUPANCY, bool SAME_COEF_PER_CHANNEL, typename D>
void biquad_filter_func(D *out, const D *in, size_t frames, size_t stride,
        size_t channelCount, D *delays, const D *coefs, size_t localStride,
        FILTER_OPTION filterOptions) {
    if constexpr ((OCCUPANCY & 7) == 0) { // all b's are zero, output is zero.
        zeroChannels(out, frames, stride, channelCount);
        return;
    }

    // Possible alternative intrinsic types for 2, 9, 15 float elements.
    // using alt_2_t = struct {struct { float a; float b; } s; };
    // using alt_9_t = struct { struct { float32x4x2_t a; float b; } s; };
    // using alt_15_t = struct { struct { float32x4x2_t a; struct { float v[7]; } b; } s; };

#ifdef USE_NEON
    // use NEON types to ensure we have the proper intrinsic acceleration.
    using alt_16_t = float32x4x4_t;
    using alt_8_t = float32x4x2_t;
    using alt_4_t = float32x4_t;
#else
    // Use C++ types, no NEON needed.
    using alt_16_t = intrinsics::internal_array_t<float, 16>;
    using alt_8_t = intrinsics::internal_array_t<float, 8>;
    using alt_4_t = intrinsics::internal_array_t<float, 4>;
#endif

    for (size_t offset = 0; offset < channelCount; ) {
        size_t remaining = channelCount - offset;
        auto *c = SAME_COEF_PER_CHANNEL ? coefs : coefs + offset;
        if (filterOptions & FILTER_OPTION_SCALAR_ONLY) goto scalar;
        if constexpr (std::is_same_v<D, float>) {
            switch (remaining) {
            default:
                if (remaining >= 16) {
                    remaining &= ~15;
                    biquad_filter_func_impl<ConstOptions,
                            nearestOccupancy(OCCUPANCY,
                                    ConstOptions::template FilterType<D, D>
                                                ::required_occupancies_),
                            SAME_COEF_PER_CHANNEL, alt_16_t>(
                            out + offset, in + offset, frames, stride, remaining,
                            delays + offset, c, localStride);
                    offset += remaining;
                    continue;
                }
                break;  // case 1 handled at bottom.
            BIQUAD_FILTER_CASE(15, intrinsics::internal_array_t<float, 15>)
            BIQUAD_FILTER_CASE(14, intrinsics::internal_array_t<float, 14>)
            BIQUAD_FILTER_CASE(13, intrinsics::internal_array_t<float, 13>)
            BIQUAD_FILTER_CASE(12, intrinsics::internal_array_t<float, 12>)
            BIQUAD_FILTER_CASE(11, intrinsics::internal_array_t<float, 11>)
            BIQUAD_FILTER_CASE(10, intrinsics::internal_array_t<float, 10>)
            BIQUAD_FILTER_CASE(9, intrinsics::internal_array_t<float, 9>)
            BIQUAD_FILTER_CASE(8, alt_8_t)
            BIQUAD_FILTER_CASE(7, intrinsics::internal_array_t<float, 7>)
            BIQUAD_FILTER_CASE(6, intrinsics::internal_array_t<float, 6>)
            BIQUAD_FILTER_CASE(5, intrinsics::internal_array_t<float, 5>)
            BIQUAD_FILTER_CASE(4, alt_4_t)
            BIQUAD_FILTER_CASE(3, intrinsics::internal_array_t<float, 3>)
            BIQUAD_FILTER_CASE(2, intrinsics::internal_array_t<float, 2>)
            // BIQUAD_FILTER_CASE(1, BiquadFilterType, intrinsics::internal_array_t<float, 1>)
            }
        } else if constexpr (std::is_same_v<D, double>) {
#if defined(__aarch64__)
            switch (remaining) {
            default:
                if (remaining >= 8) {
                    remaining &= ~7;
                    biquad_filter_func_impl<ConstOptions,
                            nearestOccupancy(OCCUPANCY,
                                     ConstOptions::template FilterType<D, D>
                                                 ::required_occupancies_),
                            SAME_COEF_PER_CHANNEL,
                            intrinsics::internal_array_t<double, 8>>(
                            out + offset, in + offset, frames, stride, remaining,
                            delays + offset, c, localStride);
                    offset += remaining;
                    continue;
                }
                break; // case 1 handled at bottom.
            BIQUAD_FILTER_CASE(7, intrinsics::internal_array_t<double, 7>)
            BIQUAD_FILTER_CASE(6, intrinsics::internal_array_t<double, 6>)
            BIQUAD_FILTER_CASE(5, intrinsics::internal_array_t<double, 5>)
            BIQUAD_FILTER_CASE(4, intrinsics::internal_array_t<double, 4>)
            BIQUAD_FILTER_CASE(3, intrinsics::internal_array_t<double, 3>)
            BIQUAD_FILTER_CASE(2, intrinsics::internal_array_t<double, 2>)
            };
#endif
        }
        scalar:
        // Essentially the code below is scalar, the same as
        // biquad_filter_1fast<OCCUPANCY, SAME_COEF_PER_CHANNEL>,
        // but formulated with NEON intrinsic-like call pattern.
        biquad_filter_func_impl<ConstOptions,
                nearestOccupancy(OCCUPANCY,
                        ConstOptions::template FilterType<D, D>::required_occupancies_),
                 SAME_COEF_PER_CHANNEL, D>(
                out + offset, in + offset, frames, stride, remaining,
                delays + offset, c, localStride);
        offset += remaining;
    }
    exit:;
}

} // namespace details

/**
 * BiquadFilter
 *
 * A multichannel Biquad filter implementation of the following transfer function.
 *
 * \f[
 *  H(z) = \frac { b_0 + b_1 z^{-1} + b_2 z^{-2} }
 *               { 1   + a_1 z^{-1} + a_2 z^{-2} }
 * \f]
 *
 * <!--
 *        b_0 + b_1 z^{-1} + b_2 z^{-2}
 *  H(z)= -----------------------------
 *        1 + a_1 z^{-1} + a_2 z^{-2}
 * -->
 *
 *  Details:
 *    1. The transposed direct type 2 implementation allows zeros to be computed
 *       before poles in the internal state for improved filter precision and
 *       better time-varying coefficient performance.
 *    2. We optimize for zero coefficients using a compile-time generated function table.
 *    3. We optimize for vector operations using column vector operations with stride
 *       into interleaved audio data.
 *    4. The denominator coefficients a_1 and a_2 are stored in positive form, despite the
 *       negated form being slightly simpler for optimization (addition is commutative but
 *       subtraction is not commutative).  This is to permit obtaining the coefficients
 *       as a const reference.
 *
 *       Compatibility issue: Some Biquad libraries store the denominator coefficients
 *       in negated form.  We explicitly negate before entering into the inner loop.
 *    5. The full 6 coefficient Biquad filter form with a_0 != 1 may be used for setting
 *       coefficients.  See setCoefficients() below.
 *
 * If SAME_COEFFICIENTS_PER_CHANNEL is false, then mCoefs is stored interleaved by channel.
 *
 * The Biquad filter update equation in transposed Direct form 2 is as follows:
 *
 * \f{eqnarray*}{
 * y[n] &=& b0 * x[n] + s1[n - 1] \\
 * s1[n] &=& s2[n - 1] + b1 * x[n] - a1 * y[n] \\
 * s2[n] &=& b2 * x[n] - a2 * y[n]
 * \f}
 *
 * For the transposed Direct form 2 update equation s1 and s2 represent the delay state
 * contained in the internal vector mDelays[].  This is stored interleaved by channel.
 *
 * Use -ffast-math` to permit associative math optimizations to get non-zero optimization as
 * we do not rely on strict C operator precedence and associativity here.
 * TODO(b/159373530): Use compound statement scoped pragmas instead of `-ffast-math`.
 *
 * \param D type variable representing the data type, one of float or double.
 *         The default is float.
 * \param SAME_COEF_PER_CHANNEL bool which is true if all the Biquad coefficients
 *         are shared between channels, or false if the Biquad coefficients
 *         may differ between channels. The default is true.
 */

template <typename D = float,
        bool SAME_COEF_PER_CHANNEL = true,
        typename ConstOptions = details::DefaultBiquadConstOptions>
class BiquadFilter {
public:
    template <typename T = std::array<D, kBiquadNumCoefs>>
    explicit BiquadFilter(size_t channelCount,
            const T& coefs = {}, bool optimized = true)
            : mChannelCount(channelCount)
            , mCoefs(kBiquadNumCoefs * (SAME_COEF_PER_CHANNEL ? 1 : mChannelCount))
            , mDelays(channelCount * kBiquadNumDelays) {
        setCoefficients(coefs, optimized);
    }

    // copy constructors
    BiquadFilter(const BiquadFilter<D, SAME_COEF_PER_CHANNEL>& other) {
        *this = other;
    }

    BiquadFilter(BiquadFilter<D, SAME_COEF_PER_CHANNEL>&& other) {
        *this = std::move(other);
    }

    // copy assignment
    BiquadFilter<D, SAME_COEF_PER_CHANNEL>& operator=(
            const BiquadFilter<D, SAME_COEF_PER_CHANNEL>& other) {
        mChannelCount = other.mChannelCount;
        mCoefs = other.mCoefs;
        mDelays = other.mDelays;
        return *this;
    }

    BiquadFilter<D, SAME_COEF_PER_CHANNEL>& operator=(
            BiquadFilter<D, SAME_COEF_PER_CHANNEL>&& other) {
        mChannelCount = other.mChannelCount;
        mCoefs = std::move(other.mCoefs);
        mDelays = std::move(other.mDelays);
        return *this;
    }

    // operator overloads for equality tests
    bool operator==(const BiquadFilter<D, SAME_COEF_PER_CHANNEL>& other) const {
        return mChannelCount == other.mChannelCount
                && mCoefs == other.mCoefs
                && mDelays == other.mDelays;
    }

    bool operator!=(const BiquadFilter<D, SAME_COEF_PER_CHANNEL>& other) const {
        return !operator==(other);
    }

    /**
     * \brief Sets filter coefficients
     *
     * \param coefs  pointer to the filter coefficients array.
     * \param optimized whether to use processor optimized function (optional, defaults true).
     * \return true if the BiquadFilter is stable, otherwise, return false.
     *
     * The input coefficients are interpreted in the following manner:
     *
     * If size of container is 5 (normalized Biquad):
     * coefs[0] is b0,
     * coefs[1] is b1,
     * coefs[2] is b2,
     * coefs[3] is a1,
     * coefs[4] is a2.
     *
     * \f[
     *  H(z) = \frac { b_0 + b_1 z^{-1} + b_2 z^{-2} }
     *               { 1   + a_1 z^{-1} + a_2 z^{-2} }
     * \f]
     * <!--
     *        b_0 + b_1 z^{-1} + b_2 z^{-2}
     *  H(z)= -----------------------------
     *        1 + a_1 z^{-1} + a_2 z^{-2}
     * -->
     *
     * If size of container is 6 (general Biquad):
     * coefs[0] is b0,
     * coefs[1] is b1,
     * coefs[2] is b2,
     * coefs[3] is a0,
     * coefs[4] is a1,
     * coefs[5] is a2.
     *
     * \f[
     *  H(z) = \frac { b_0 + b_1 z^{-1} + b_2 z^{-2} }
     *               { a_0 + a_1 z^{-1} + a_2 z^{-2} }
     * \f]
     * <!--
     *        b_0 + b_1 z^{-1} + b_2 z^{-2}
     *  H(z)= -----------------------------
     *        a_0 + a_1 z^{-1} + a_2 z^{-2}
     * -->
     *
     * The internal representation is a normalized Biquad.
     */
    template <typename T = std::array<D, kBiquadNumCoefs>>
    bool setCoefficients(const T& coefs, bool optimized = true) {
        if constexpr (SAME_COEF_PER_CHANNEL) {
            details::setCoefficients<D, T>(
                    mCoefs, 0 /* offset */, 1 /* stride */, 1 /* channelCount */, coefs);
        } else {
            if (coefs.size() == mCoefs.size()) {
                std::copy(coefs.begin(), coefs.end(), mCoefs.begin());
            } else {
                details::setCoefficients<D, T>(
                        mCoefs, 0 /* offset */, mChannelCount, mChannelCount, coefs);
            }
        }
        setOptimization(optimized);
        return isStable();
    }

    /**
     * Sets coefficients for one of the filter channels, specified by channelIndex.
     *
     * This method is only available if SAME_COEF_PER_CHANNEL is false.
     *
     * \param coefs the coefficients to set.
     * \param channelIndex the particular channel index to set.
     * \param optimized whether to use optimized function (optional, defaults true).
     */
    template <typename T = std::array<D, kBiquadNumCoefs>>
    bool setCoefficients(const T& coefs, size_t channelIndex, bool optimized = true) {
        static_assert(!SAME_COEF_PER_CHANNEL);

        details::setCoefficients<D, T>(
                mCoefs, channelIndex, mChannelCount, 1 /* channelCount */, coefs);
        setOptimization(optimized);
        return isStable();
    }

    /**
     * Returns the coefficients as a const vector reference.
     *
     * If multichannel and the template variable SAME_COEF_PER_CHANNEL is true,
     * the coefficients are interleaved by channel.
     */
    const std::vector<D>& getCoefficients() const {
        return mCoefs;
    }

    /**
     * Returns true if the filter is stable.
     *
     * \param channelIndex ignored if SAME_COEF_PER_CHANNEL is true,
     *        asserts if channelIndex >= channel count (zero based index).
     */
    bool isStable(size_t channelIndex = 0) const {
        if constexpr (SAME_COEF_PER_CHANNEL) {
            return details::isStable(mCoefs[3], mCoefs[4]);
        } else {
            assert(channelIndex < mChannelCount);
            return details::isStable(
                    mCoefs[3 * mChannelCount + channelIndex],
                    mCoefs[4 * mChannelCount + channelIndex]);
        }
    }

    /**
     * Updates the filter function based on processor optimization.
     *
     * \param optimized if true, enables Processor based optimization.
     */
    void setOptimization(bool optimized) {
        // Determine which coefficients are nonzero as a bit field.
        size_t category = 0;
        for (size_t i = 0; i < kBiquadNumCoefs; ++i) {
            if constexpr (SAME_COEF_PER_CHANNEL) {
                category |= (mCoefs[i] != 0) << i;
            } else {
                for (size_t j = 0; j < mChannelCount; ++j) {
                    if (mCoefs[i * mChannelCount + j] != 0) {
                        category |= 1 << i;
                        break;
                    }
                }
            }
        }

        // Select the proper filtering function from our array.
        if (optimized) {
            mFilterOptions = (details::FILTER_OPTION)
                    (mFilterOptions & ~details::FILTER_OPTION_SCALAR_ONLY);
        } else {
             mFilterOptions = (details::FILTER_OPTION)
                     (mFilterOptions | details::FILTER_OPTION_SCALAR_ONLY);
        }
        mFunc = mFilterFuncs[category];
    }

    /**
     * \brief Filters the input data
     *
     * \param out     pointer to the output data
     * \param in      pointer to the input data
     * \param frames  number of audio frames to be processed
     */
    void process(D* out, const D* in, size_t frames) {
        process(out, in, frames, mChannelCount);
    }

    /**
     * \brief Filters the input data with stride
     *
     * \param out     pointer to the output data
     * \param in      pointer to the input data
     * \param frames  number of audio frames to be processed
     * \param stride  the total number of samples associated with a frame, if not channelCount.
     */
    void process(D* out, const D* in, size_t frames, size_t stride) {
        assert(stride >= mChannelCount);
        mFunc(out, in, frames, stride, mChannelCount, mDelays.data(),
                mCoefs.data(), mChannelCount, mFilterOptions);
    }

    /**
     * EXPERIMENTAL:
     * Processes 1D input data, with mChannel Biquads, using sliding window parallelism.
     *
     * Instead of considering mChannel Biquads as one-per-input channel, this method treats
     * the mChannel biquads as applied in sequence to a single 1D input stream,
     * with the last channel count Biquad being applied first.
     *
     * input audio data -> BQ_{n-1} -> BQ{n-2} -> BQ_{n-3} -> BQ_{0} -> output
     *
     * TODO: Make this code efficient for NEON and split the destination from the source.
     *
     * Theoretically this code should be much faster for 1D input if one has 4+ Biquads to be
     * sequentially applied, but in practice it is *MUCH* slower.
     * On NEON, the data cannot be written then read in-place without incurring
     * memory stall penalties.  A shifting NEON holding register is required to make this
     * a practical improvement.
     */
    void process1D(D* inout, size_t frames) {
        size_t remaining = mChannelCount;
#ifdef USE_NEON
        // We apply NEON acceleration striped with 4 filters (channels) at once.
        // Filters operations commute, nevertheless we apply the filters in order.
        if (frames >= 2 * mChannelCount) {
            constexpr size_t channelBlock = 4;
            for (; remaining >= channelBlock; remaining -= channelBlock) {
                const size_t baseIdx = remaining - channelBlock;
                // This is the 1D accelerated method.
                // prime the data pipe.
                for (size_t i = 0; i < channelBlock - 1; ++i) {
                    size_t fromEnd = remaining - i - 1;
                    auto coefs = mCoefs.data() + (SAME_COEF_PER_CHANNEL ? 0 : fromEnd);
                    auto delays = mDelays.data() + fromEnd;
                    mFunc(inout, inout, 1 /* frames */, 1 /* stride */, i + 1,
                            delays, coefs, mChannelCount, mFilterOptions);
                }

                auto delays = mDelays.data() + baseIdx;
                auto coefs = mCoefs.data() + (SAME_COEF_PER_CHANNEL ? 0 : baseIdx);
                // Parallel processing - we use a sliding window doing channelBlock at once,
                // sliding one audio sample at a time.
                mFunc(inout, inout,
                        frames - channelBlock + 1, 1 /* stride */, channelBlock,
                        delays, coefs, mChannelCount, mFilterOptions);

                // drain data pipe.
                for (size_t i = 1; i < channelBlock; ++i) {
                    mFunc(inout + frames - channelBlock + i, inout + frames - channelBlock + i,
                            1 /* frames */, 1 /* stride */, channelBlock - i,
                            delays, coefs, mChannelCount, mFilterOptions);
                }
            }
        }
#endif
        // For short data sequences, we use the serial single channel logical equivalent
        for (; remaining > 0; --remaining) {
            size_t fromEnd = remaining - 1;
            auto coefs = mCoefs.data() + (SAME_COEF_PER_CHANNEL ? 0 : fromEnd);
            mFunc(inout, inout,
                    frames, 1 /* stride */, 1 /* channelCount */,
                    mDelays.data() + fromEnd, coefs, mChannelCount, mFilterOptions);
        }
    }

    /**
     * \brief Clears the delay elements
     *
     * This function clears the delay elements representing the filter state.
     */
    void clear() {
        std::fill(mDelays.begin(), mDelays.end(), 0.f);
    }

    /**
     * \brief Sets the internal delays from a vector
     *
     * For a multichannel stream, the delays are interleaved by channel:
     * delays[2 * i + 0] is s1 of i-th channel,
     * delays[2 * i + 1] is s2 of i-th channel,
     * where index i runs from 0 to (mChannelCount - 1).
     *
     * \param delays reference to vector containing delays.
     */
    void setDelays(std::vector<D>& delays) {
        assert(delays.size() == mDelays.size());
        mDelays = std::move(delays);
    }

    /**
     * \brief Gets delay elements as a vector
     *
     * For a multichannel stream, the delays are interleaved by channel:
     * delays[2 * i + 0] is s1 of i-th channel,
     * delays[2 * i + 1] is s2 of i-th channel,
     * where index i runs from 0 to (mChannelCount - 1).
     *
     * \return a const vector reference of delays.
     */
    const std::vector<D>& getDelays() const {
        return mDelays;
    }

private:
    /* const */ size_t mChannelCount; // not const because we can assign to it on operator equals.

    /*
     * \var D mCoefs
     * \brief Stores the filter coefficients
     *
     * If SAME_COEF_PER_CHANNEL is false, the filter coefficients are stored
     * interleaved by channel.
     */
    std::vector<D> mCoefs;

    /**
     * \var D mDelays
     * \brief The delay state.
     *
     * The delays are stored channel interleaved in the following manner,
     * mDelays[2 * i + 0] is s1 of i-th channel
     * mDelays[2 * i + 1] is s2 of i-th channel
     * index i runs from 0 to (mChannelCount - 1).
     */
    std::vector<D> mDelays;

    details::FILTER_OPTION mFilterOptions{};

    // Consider making a separate delegation class.
    /*
     * We store an array of functions based on the occupancy.
     *
     * OCCUPANCY is a bitmask corresponding to the presence of nonzero Biquad coefficients
     * b0 b1 b2 a1 a2  (from lsb to msb)
     *
     *  static inline constexpr std::array<filter_func*, M> mArray = {
     *     biquad_filter_func<0>,
     *     biquad_filter_func<1>,
     *     biquad_filter_func<2>,
     *      ...
     *     biquad_filter_func<(1 << kBiquadNumCoefs) - 1>,
     *  };
     *
     * Every time the coefficients are changed, we select the processing function from
     * this table.
     */

    // Used to build the functional array.
    template <size_t OCCUPANCY, bool SC> // note SC == SAME_COEF_PER_CHANNEL
    struct FuncWrap {
        static void func(D* out, const D *in, size_t frames, size_t stride,
                size_t channelCount, D *delays, const D *coef, size_t localStride,
                details::FILTER_OPTION filterOptions) {
            constexpr size_t NEAREST_OCCUPANCY =
                details::nearestOccupancy(
                        OCCUPANCY, ConstOptions::template FilterType<D, D>
                                               ::required_occupancies_);
            details::biquad_filter_func<ConstOptions, NEAREST_OCCUPANCY, SC>(
                    out, in, frames, stride, channelCount, delays, coef, localStride,
                    filterOptions);
        }
    };

    // Vector optimized array of functions.
    static inline constexpr auto mFilterFuncs =
            details::make_functional_array<
                    FuncWrap, 1 << kBiquadNumCoefs, SAME_COEF_PER_CHANNEL>();

    /**
     * \var filter_func* mFunc
     *
     * The current filter function selected for the channel occupancy of the Biquad.
     * It will be one of mFilterFuncs.
     */
    std::decay_t<decltype(mFilterFuncs[0])> mFunc;
};

} // namespace android::audio_utils

#pragma pop_macro("USE_DITHER")
#pragma pop_macro("USE_NEON")
