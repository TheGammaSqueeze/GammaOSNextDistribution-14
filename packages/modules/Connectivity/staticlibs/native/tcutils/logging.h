/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android/log.h>
#include <stdarg.h>

#ifndef LOG_TAG
#define LOG_TAG "TcUtils_Undef"
#endif

namespace android {

static inline void ALOGE(const char *fmt...) {
  va_list args;
  va_start(args, fmt);
  __android_log_vprint(ANDROID_LOG_ERROR, LOG_TAG, fmt, args);
  va_end(args);
}

static inline void ALOGW(const char *fmt...) {
  va_list args;
  va_start(args, fmt);
  __android_log_vprint(ANDROID_LOG_WARN, LOG_TAG, fmt, args);
  va_end(args);
}

static inline void ALOGI(const char *fmt...) {
  va_list args;
  va_start(args, fmt);
  __android_log_vprint(ANDROID_LOG_INFO, LOG_TAG, fmt, args);
  va_end(args);
}

static inline void ALOGD(const char *fmt...) {
  va_list args;
  va_start(args, fmt);
  __android_log_vprint(ANDROID_LOG_DEBUG, LOG_TAG, fmt, args);
  va_end(args);
}

}
