/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef LIBTEXTCLASSIFIER_UTILS_JVM_TEST_UTILS_H_
#define LIBTEXTCLASSIFIER_UTILS_JVM_TEST_UTILS_H_

#include "utils/calendar/calendar.h"
#include "utils/utf8/unilib.h"

#if defined(__ANDROID__)
#include <jni.h>

// Use these with jvm_test_launcher which provides these global variables.
extern JNIEnv* g_jenv;
extern jobject g_context;
#endif

namespace libtextclassifier3 {
inline std::unique_ptr<UniLib> CreateUniLibForTesting() {
#if defined TC3_UNILIB_JAVAICU
  return std::make_unique<UniLib>(JniCache::Create(g_jenv));
#else
  return std::make_unique<UniLib>();
#endif
}

inline std::unique_ptr<CalendarLib> CreateCalendarLibForTesting() {
#if defined TC3_CALENDAR_JAVAICU
  return std::make_unique<CalendarLib>(JniCache::Create(g_jenv));
#else
  return std::make_unique<CalendarLib>();
#endif
}

#if defined(__ANDROID__)
inline JNIEnv* GetJenv() { return g_jenv; }

inline jobject GetAndroidContext() { return g_context; }
#endif

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_JVM_TEST_UTILS_H_
