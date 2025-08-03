/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H
#define ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H

#include <array>  // std::size
#include <type_traits>

/*
  The intrinsics utility library contain helper functions for wide width DSP support.
  We use templated types to allow testing from scalar to vector values.

  See the Eigen project for general abstracted linear algebra acceleration.
  http://eigen.tuxfamily.org/
*/

// We conditionally include neon optimizations for ARM devices
#pragma push_macro("USE_NEON")
#undef USE_NEON

#if defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define USE_NEON
#endif

namespace android::audio_utils::intrinsics {

// For static assert(false) we need a template version to avoid early failure.
// See: https://stackoverflow.com/questions/51523965/template-dependent-false
template <typename T>
inline constexpr bool dependent_false_v = false;

// Type of array embedded in a struct that is usable in the Neon template functions below.
// This type must satisfy std::is_array_v<>.
template<typename T, size_t N>
struct internal_array_t {
    T v[N];
    static constexpr size_t size() { return N; }
};

// Detect if the value is directly addressable as an array.
// This is more advanced than std::is_array and works with neon intrinsics.
template<typename T>
concept is_array_like = requires(T a) {
    a[0];  // can index first element
};

// Vector convert between type T to type S.
template <typename S, typename T>
inline S vconvert(const T& in) {
    S out;

    if constexpr (is_array_like<S>) {
        if constexpr (is_array_like<T>) {
#pragma unroll
            // neon intrinsics need sizeof.
            for (size_t i = 0; i < sizeof(in) / sizeof(in[0]); ++i) {
                out[i] = in[i];
            }
        } else { /* constexpr */
            const auto& [inv] = in;
#pragma unroll
            for (size_t i = 0; i < T::size(); ++i) {
                out[i] = inv[i];
            }
        }
    } else { /* constexpr */
        auto& [outv] = out;
        if constexpr (is_array_like<T>) {
#pragma unroll
            // neon intrinsics need sizeof.
            for (size_t i = 0; i < sizeof(in) / sizeof(in[0]); ++i) {
                outv[i] = in[i];
            }
        } else { /* constexpr */
            const auto& [inv] = in;
#pragma unroll
            for (size_t i = 0; i < T::size(); ++i) {
                outv[i] = inv[i];
            }
        }
    }
    return out;
}

/*
  Generalized template functions for the Neon instruction set.

  See here for some general comments from ARM.
  https://developer.arm.com/documentation/dht0004/a/neon-support-in-compilation-tools/automatic-vectorization/floating-point-vectorization

  Notes:
  1) We provide scalar equivalents which are compilable even on non-ARM processors.
  2) We use recursive calls to decompose array types, e.g. float32x4x4_t -> float32x4_t
  3) NEON double SIMD acceleration is only available on 64 bit architectures.
     On Pixel 3XL, NEON double x 2 SIMD is actually slightly slower than the FP unit.

  We create a generic Neon acceleration to be applied to a composite type.

  The type follows the following compositional rules for simplicity:
      1) must be a primitive floating point type.
      2) must be a NEON data type.
      3) must be a struct with one member, either
           a) an array of types 1-3.
           b) a cons-pair struct of 2 possibly different members of types 1-3.

  Examples of possible struct definitions:
  using alternative_2_t = struct { struct { float a; float b; } s; };
  using alternative_9_t = struct { struct { float32x4x2_t a; float b; } s; };
  using alternative_15_t = struct { struct { float32x4x2_t a; struct { float v[7]; } b; } s; };
*/

// add a + b
template<typename T>
static inline T vadd(T a, T b) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return a + b;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vadd_f32(a, b);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vaddq_f32(a, b);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vaddq_f64(a, b);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        const auto &[bval] = b;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vadd(aval[i], bval[i]);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             const auto &[b1, b2] = bval;
             r1 = vadd(a1, b1);
             r2 = vadd(a2, b2);
             return ret;
        }
    }
}

// add internally
template<typename T>
inline auto vaddv(const T& a) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return a;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vaddv_f32(a);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vaddvq_f32(a);
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vaddvq_f64(a);
#endif
#endif // USE_NEON
    } else if constexpr (is_array_like<T>) {
        using ret_t = std::decay_t<decltype(a[0])>;

        ret_t ret{};
        // array_like is not the same as an array, so we use sizeof here
        // to handle neon instrinsics.
#pragma unroll
        for (size_t i = 0; i < sizeof(a) / sizeof(a[0]); ++i) {
            ret += a[i];
        }
        return ret;
    } else /* constexpr */ {
        const auto &[aval] = a;
        using ret_t = std::decay_t<decltype(aval[0])>;
        ret_t ret{};

#pragma unroll
        for (size_t i = 0; i < std::size(aval); ++i) {
            ret += aval[i];
        }
        return ret;
    }
}

// duplicate float into all elements.
template<typename T, typename F>
static inline T vdupn(F f) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return f;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vdup_n_f32(f);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vdupq_n_f32(f);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vdupq_n_f64(f);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (auto& val : retval) {
                val = vdupn<std::decay_t<decltype(val)>>(f);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             using r1_type = std::decay_t<decltype(r1)>;
             using r2_type = std::decay_t<decltype(r2)>;
             r1 = vdupn<r1_type>(f);
             r2 = vdupn<r2_type>(f);
             return ret;
        }
    }
}

// load from float pointer.
template<typename T, typename F>
static inline T vld1(const F *f) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return *f;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vld1_f32(f);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vld1q_f32(f);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vld1q_f64(f);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        if constexpr (std::is_array_v<decltype(retval)>) {
            using element_type = std::decay_t<decltype(retval[0])>;
            constexpr size_t subelements = sizeof(element_type) / sizeof(F);
#pragma unroll
            for (size_t i = 0; i < std::size(retval); ++i) {
                retval[i] = vld1<element_type>(f);
                f += subelements;
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             using r1_type = std::decay_t<decltype(r1)>;
             using r2_type = std::decay_t<decltype(r2)>;
             r1 = vld1<r1_type>(f);
             f += sizeof(r1) / sizeof(F);
             r2 = vld1<r2_type>(f);
             return ret;
        }
    }
}

/**
 * Returns c as follows:
 * c_i = a_i * b_i if a and b are the same vector type or
 * c_i = a_i * b if a is a vector and b is scalar or
 * c_i = a * b_i if a is scalar and b is a vector.
 */
template<typename T, typename S, typename F>
static inline T vmla(T a, S b, F c) {
    // Both types T and S are non-primitive and they are not equal.  T == S handled below.
    (void) a;
    (void) b;
    (void) c;
    static_assert(dependent_false_v<T>);
}

template<typename T, typename F>
static inline T vmla(T a, T b, F c) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        if constexpr (std::is_same_v<F, float> || std::is_same_v<F, double>) {
            return a + b * c;
        } else {
            static_assert(dependent_false_v<T>);
        }
    } else if constexpr (std::is_same_v<F, float> || std::is_same_v<F, double>) {
        // handle the lane variant
#ifdef USE_NEON
        if constexpr (std::is_same_v<T, float32x2_t>) {
            return vmla_n_f32(a, b, c);
        } else if constexpr (std::is_same_v<T, float32x4_t>) {
            return vmlaq_n_f32(a, b,c);
#if defined(__aarch64__)
        } else if constexpr (std::is_same_v<T, float64x2_t>) {
            return vmlaq_n_f64(a, b);
#endif
        } else
#endif // USE_NEON
        {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        const auto &[bval] = b;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vmla(aval[i], bval[i], c);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             const auto &[b1, b2] = bval;
             r1 = vmla(a1, b1, c);
             r2 = vmla(a2, b2, c);
             return ret;
        }
        }
    } else {
        // Both types T and F are non-primitive and they are not equal.
        static_assert(dependent_false_v<T>);
    }
}

template<typename T, typename F>
static inline T vmla(T a, F b, T c) {
    return vmla(a, c, b);
}

// fused multiply-add a + b * c
template<typename T>
inline T vmla(const T& a, const T& b, const T& c) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return a + b * c;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vmla_f32(a, b, c);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vmlaq_f32(a, b, c);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vmlaq_f64(a, b, c);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        const auto &[bval] = b;
        const auto &[cval] = c;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vmla(aval[i], bval[i], cval[i]);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             const auto &[b1, b2] = bval;
             const auto &[c1, c2] = cval;
             r1 = vmla(a1, b1, c1);
             r2 = vmla(a2, b2, c2);
             return ret;
        }
    }
}

/**
 * Returns c as follows:
 * c_i = a_i * b_i if a and b are the same vector type or
 * c_i = a_i * b if a is a vector and b is scalar or
 * c_i = a * b_i if a is scalar and b is a vector.
 */
template<typename T, typename F>
static inline auto vmul(T a, F b) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        if constexpr (std::is_same_v<F, float> || std::is_same_v<F, double>) {
            return a * b;
        } else /* constexpr */ {
            return vmul(b, a); // we prefer T to be the vector/struct form.
        }
    } else if constexpr (std::is_same_v<F, float> || std::is_same_v<F, double>) {
        // handle the lane variant
#ifdef USE_NEON
        if constexpr (std::is_same_v<T, float32x2_t>) {
            return vmul_n_f32(a, b);
        } else if constexpr (std::is_same_v<T, float32x4_t>) {
            return vmulq_n_f32(a, b);
#if defined(__aarch64__)
        } else if constexpr (std::is_same_v<T, float64x2_t>) {
            return vmulq_n_f64(a, b);
#endif
        } else
#endif // USE_NEON
        {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vmul(aval[i], b);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             r1 = vmul(a1, b);
             r2 = vmul(a2, b);
             return ret;
        }
        }
    } else {
        // Both types T and F are non-primitive and they are not equal.
        static_assert(dependent_false_v<T>);
    }
}

template<typename T>
static inline T vmul(T a, T b) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return a * b;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vmul_f32(a, b);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vmulq_f32(a, b);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vmulq_f64(a, b);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        const auto &[bval] = b;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vmul(aval[i], bval[i]);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             const auto &[b1, b2] = bval;
             r1 = vmul(a1, b1);
             r2 = vmul(a2, b2);
             return ret;
        }
    }
}

// negate
template<typename T>
static inline T vneg(T f) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return -f;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vneg_f32(f);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vnegq_f32(f);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vnegq_f64(f);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[fval] = f;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(fval); ++i) {
                retval[i] = vneg(fval[i]);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[f1, f2] = fval;
             r1 = vneg(f1);
             r2 = vneg(f2);
             return ret;
        }
    }
}

// store to float pointer.
template<typename T, typename F>
static inline void vst1(F *f, T a) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        *f = a;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vst1_f32(f, a);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vst1q_f32(f, a);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vst1q_f64(f, a);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        const auto &[aval] = a;
        if constexpr (std::is_array_v<decltype(aval)>) {
            constexpr size_t subelements = sizeof(std::decay_t<decltype(aval[0])>) / sizeof(F);
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                vst1(f, aval[i]);
                f += subelements;
            }
        } else /* constexpr */ {
             const auto &[a1, a2] = aval;
             vst1(f, a1);
             f += sizeof(std::decay_t<decltype(a1)>) / sizeof(F);
             vst1(f, a2);
        }
    }
}

// subtract a - b
template<typename T>
static inline T vsub(T a, T b) {
    if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
        return a - b;

#ifdef USE_NEON
    } else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vsub_f32(a, b);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vsubq_f32(a, b);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vsubq_f64(a, b);
#endif
#endif // USE_NEON

    } else /* constexpr */ {
        T ret;
        auto &[retval] = ret;  // single-member struct
        const auto &[aval] = a;
        const auto &[bval] = b;
        if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
            for (size_t i = 0; i < std::size(aval); ++i) {
                retval[i] = vsub(aval[i], bval[i]);
            }
            return ret;
        } else /* constexpr */ {
             auto &[r1, r2] = retval;
             const auto &[a1, a2] = aval;
             const auto &[b1, b2] = bval;
             r1 = vsub(a1, b1);
             r2 = vsub(a2, b2);
             return ret;
        }
    }
}

} // namespace android::audio_utils::intrinsics

#pragma pop_macro("USE_NEON")

#endif // !ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H
