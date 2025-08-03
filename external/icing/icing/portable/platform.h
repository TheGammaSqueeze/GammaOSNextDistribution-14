// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_PORTABLE_PLATFORM_H_
#define ICING_PORTABLE_PLATFORM_H_

#include "unicode/uconfig.h"  // IWYU pragma: keep
// clang-format: do not reorder the above include.

#include "unicode/uvernum.h"

namespace icing {
namespace lib {

// Returns true if the test was built with the CFStringTokenizer as the
// implementation of LanguageSegmenter.
inline bool IsCfStringTokenization() {
#if defined(__APPLE__) && !defined(ICING_IOS_ICU4C_SEGMENTATION)
  return true;
#endif  // defined(__APPLE__) && !defined(ICING_IOS_ICU4C_SEGMENTATION)
  return false;
}

inline bool IsReverseJniTokenization() {
#ifdef ICING_REVERSE_JNI_SEGMENTATION
  return true;
#endif  // ICING_REVERSE_JNI_SEGMENTATION
  return false;
}

inline bool IsIcuTokenization() {
  return !IsReverseJniTokenization() && !IsCfStringTokenization();
}

inline int GetIcuTokenizationVersion() {
  return IsIcuTokenization() ? U_ICU_VERSION_MAJOR_NUM : 0;
}

// Whether we're running on android_x86
inline bool IsAndroidX86() {
#if defined(__ANDROID__) && defined(__i386__)
  return true;
#endif  // defined(__ANDROID__) && defined(__i386__)
  return false;
}

// Whether we're running on android_armeabi-v7a
inline bool IsAndroidArm() {
#if defined(__ANDROID__) && defined(__arm__)
  return true;
#endif  // defined(__ANDROID__) && defined(__arm__)
  return false;
}

// Whether the running test is an iOS test.
inline bool IsIosPlatform() {
#if defined(__APPLE__)
  return true;
#endif  // defined(__APPLE__)
  return false;
}

// TODO(b/259129263): verify the flag works for different platforms.
#if defined(__arm__) || defined(__i386__)
#define ICING_ARCH_BIT_32
#elif defined(__aarch64__) || defined(__x86_64__)
#define ICING_ARCH_BIT_64
#else
#define ICING_ARCH_BIT_UNKNOWN
#endif

enum Architecture {
  UNKNOWN,
  BIT_32,
  BIT_64,
};

// Returns which architecture we're running on.
//
// Architecture macros pulled from
// https://developer.android.com/ndk/guides/cpu-features
inline Architecture GetArchitecture() {
#if defined(ICING_ARCH_BIT_32)
  return BIT_32;
#elif defined(ICING_ARCH_BIT_64)
  return BIT_64;
#else
  return UNKNOWN;
#endif
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_PORTABLE_PLATFORM_H_
