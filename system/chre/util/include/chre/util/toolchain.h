/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef CHRE_UTIL_TOOLCHAIN_H_
#define CHRE_UTIL_TOOLCHAIN_H_

/**
 * @file
 * Compiler/build toolchain-specific macros used by nanoapps and the CHRE system
 */

#if defined(__GNUC__) || defined(__clang__)

// Promoting float to double is a necessary part of vararg-based logging, so
// ignore those warnings when logging via chre/util/nanoapp/log.h
// clang-format off
#define CHRE_LOG_PREAMBLE \
    _Pragma("GCC diagnostic push") \
    _Pragma("GCC diagnostic ignored \"-Wdouble-promotion\"")
// clang-format on

#define CHRE_LOG_EPILOGUE _Pragma("GCC diagnostic pop")

// Allows using deprecated APIs locally.
#define CHRE_DEPRECATED_PREAMBLE \
  _Pragma("GCC diagnostic push") \
      _Pragma("GCC diagnostic ignored \"-Wdeprecated-declarations\"")

#define CHRE_DEPRECATED_EPILOGUE _Pragma("GCC diagnostic pop")

// Instructs the compiler to warn about unused results.
// When annotating a function, this macro must appear before the function name
// in the declaration.
// Example Usage:
//    bool CHRE_MUST_USE_RESULT pleaseUseMyResult();
//
// A cast-to-void to suppress this warning only works on clang and not on gcc
// because of this bug:
// https://gcc.gnu.org/bugzilla/show_bug.cgi?id=66425
//
// A possible workaround (other than just filtering out this macro) may be
// just negating the return value before a void-cast as follows::
//    void void_cast_should_not_warn() {
//      (void) !foo();
//      //     ^-- here
//    }
#if defined __has_attribute
#if __has_attribute(warn_unused_result)
#define CHRE_MUST_USE_RESULT __attribute__((warn_unused_result))
#endif
#endif
#ifndef CHRE_MUST_USE_RESULT
#define CHRE_MUST_USE_RESULT
#endif

#elif defined(IS_CHPP_BUILD)
// These macros need to be defined for CHPP on other compilers

#define CHRE_LOG_PREAMBLE
#define CHRE_LOG_EPILOGUE
#define CHRE_DEPRECATED_PREAMBLE
#define CHRE_DEPRECATED_EPILOGUE
#define CHRE_MUST_USE_RESULT

#else

#error Need to add support for new compiler

#endif
#endif  // CHRE_UTIL_TOOLCHAIN_H_
