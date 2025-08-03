/*
 * Copyright (C) 2024 The Android Open Source Project
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
#ifndef CHRE_PLATFORM_SHARED_NANOAPP_TOKENIZED_LOG_H_
#define CHRE_PLATFORM_SHARED_NANOAPP_TOKENIZED_LOG_H_

#include "chre_api/chre/re.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Platform implementation of chre pigweed tokenized log for nanoapps. The log
 * message is sent via the pigweed tokenization format.
 *
 * This function must not be called directly from any nanoapp. It should only be
 * used for the CHRE nanoapp LOG MACRO util.
 *
 * @param level  The CHRE log level for this message.
 * @param token  The token representing the string log.
 * @param types  A series of arguments to a compact format that replaces the
 * format string literal.
 * @param ... A variable number of arguments necessary for the given arg_types.
 */
void platform_chrePwTokenizedLog(enum chreLogLevel level, uint32_t token,
                                 uint64_t types, ...);

#ifdef __cplusplus
}
#endif

#endif  // CHRE_PLATFORM_SHARED_NANOAPP_TOKENIZED_LOG_H_
