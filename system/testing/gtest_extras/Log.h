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

#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include <android/log.h>

#define GTEST_EXTRAS_TAG "gtest_extras"

#define FATAL_PLOG(msg)                                                                    \
  __android_log_print(ANDROID_LOG_FATAL, GTEST_EXTRAS_TAG, "%s:%d] " msg ": %s", __FILE__, \
                      __LINE__, strerror(errno));                                          \
  abort();

#define FATAL_LOG(msg)                                                                         \
  __android_log_print(ANDROID_LOG_FATAL, GTEST_EXTRAS_TAG, "%s:%d] " msg, __FILE__, __LINE__); \
  abort();
