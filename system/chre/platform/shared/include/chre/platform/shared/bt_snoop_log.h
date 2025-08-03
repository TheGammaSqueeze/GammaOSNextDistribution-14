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

#ifndef CHRE_PLATFORM_SHARED_BT_SNOOP_LOG_H_
#define CHRE_PLATFORM_SHARED_BT_SNOOP_LOG_H_

#include <cinttypes>
#include <cstddef>

//! Indicates direction of a BT snoop log.
//! TODO(b/294884658): Make the fbs definition as the single source of truth.
enum class BtSnoopDirection : uint8_t {
  INCOMING_FROM_BT_CONTROLLER = 0,
  OUTGOING_TO_ARBITER = 1,
};

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Sends a BT snoop log to CHRE. The log is handled separately from
 * normal CHRE logs.
 *
 * @param direction Direction of the BT snoop log.
 * @param buffer a byte buffer containing the encoded log message.
 * @param size size of the bt log message buffer.
 */
void chrePlatformBtSnoopLog(BtSnoopDirection direction, const uint8_t *buffer,
                            size_t size);

#ifdef __cplusplus
}
#endif

#endif  // CHRE_PLATFORM_SHARED_BT_SNOOP_LOG_H_
