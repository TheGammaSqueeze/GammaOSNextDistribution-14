/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <cstdio>

#include "chre_host/log.h"

namespace android::chre {

void outputHostLog(int priority, FILE *stream, const char *format,
                   const char *func, unsigned int line, ...) {
  va_list args;
  va_start(args, line);
  LOG_PRI_VA(priority, LOG_TAG, format, args);
  va_end(args);
  va_start(args, line);
  fprintf(stream, "%s:%d: ", func, line);
  vfprintf(stream, format, args);
  fprintf(stream, "\n");
  fflush(stream);  // flush the buffer to print out the log immediately
  va_end(args);
}

}  // namespace android::chre