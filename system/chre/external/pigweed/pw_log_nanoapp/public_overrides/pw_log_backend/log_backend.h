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

#ifndef _PW_LOG_NANOAPP_PW_LOG_BACKEND_H_
#define _PW_LOG_NANOAPP_PW_LOG_BACKEND_H_

#include "chre/re.h"
#include "pw_log/levels.h"

#define PW_HANDLE_LOG(level, module, flags, fmt, ...)            \
  do {                                                           \
      enum chreLogLevel chreLevel = CHRE_LOG_ERROR;              \
      switch (level) {                                           \
        case PW_LOG_LEVEL_DEBUG:                                 \
          chreLevel = CHRE_LOG_DEBUG;                            \
          break;                                                 \
        case PW_LOG_LEVEL_INFO:                                  \
          chreLevel = CHRE_LOG_INFO;                             \
          break;                                                 \
        case PW_LOG_LEVEL_WARN:                                  \
          chreLevel = CHRE_LOG_WARN;                             \
          break;                                                 \
        default:                                                 \
          chreLevel = CHRE_LOG_ERROR;                            \
      }                                                          \
      chreLog(chreLevel, "PW " module ": "  fmt, ##__VA_ARGS__); \
  } while (0)

#endif // _PW_LOG_NANOAPP_PW_LOG_BACKEND_H_
