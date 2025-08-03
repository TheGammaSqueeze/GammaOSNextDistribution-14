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

#include "chpp/pal_api.h"

#include <stdarg.h>
#include <stdio.h>

#include "chpp/app.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/time.h"
#include "chre/pal/system.h"
#include "chre_api/chre/re.h"

//! Define a format string for PAL logs. This is defined as a macro so that it
//! can be used as a string literal by platform implementations of the logging
//! macros.
#define PAL_LOG_FORMAT_STR "PAL: %s"

static uint64_t palSystemApiGetCurrentTime(void) {
  return chppGetCurrentTimeNs();
}

static void palSystemApiLog(enum chreLogLevel level, const char *formatStr,
                            ...) {
  char logBuf[512];
  va_list args;

  va_start(args, formatStr);
  vsnprintf(logBuf, sizeof(logBuf), formatStr, args);
  // TODO: not sure if vsnprintf will be well-supported on partner platforms (on
  // the CHRE side, we know it's the case for SLPI but even then it's not
  // supported in micro-image). We may instead want to expose a va_list version
  // of the platform log functionality, so we can just forward directly to the
  // underlying platform log functionality without handling format string
  // expansion in common code.
  va_end(args);

  switch (level) {
    case CHRE_LOG_ERROR:
      CHPP_LOGE(PAL_LOG_FORMAT_STR, logBuf);
      break;
    case CHRE_LOG_WARN:
      CHPP_LOGW(PAL_LOG_FORMAT_STR, logBuf);
      break;
    case CHRE_LOG_INFO:
      CHPP_LOGI(PAL_LOG_FORMAT_STR, logBuf);
      break;
    case CHRE_LOG_DEBUG:
    default:
      CHPP_LOGD(PAL_LOG_FORMAT_STR, logBuf);
  }
}

void chppPalSystemApiInit(struct ChppAppState *context) {
  // Initialize the CHRE System API with function implementations provided
  // above.
  static const struct chrePalSystemApi chrePalSystemApi = {
      .version = CHRE_PAL_SYSTEM_API_CURRENT_VERSION,
      .getCurrentTime = palSystemApiGetCurrentTime,
      .log = palSystemApiLog,
      .memoryAlloc = chppMalloc,
      .memoryFree = chppFree,
  };

  context->systemApi = &chrePalSystemApi;
}

void chppPalSystemApiDeinit(struct ChppAppState *context) {
  UNUSED_VAR(context);
}
