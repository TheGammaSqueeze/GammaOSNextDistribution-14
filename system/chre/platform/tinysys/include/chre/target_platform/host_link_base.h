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

#ifndef CHRE_PLATFORM_TINYSYS_HOST_LINK_BASE_H_
#define CHRE_PLATFORM_TINYSYS_HOST_LINK_BASE_H_

#include <cinttypes>
#include <cstddef>

#include "chre/platform/atomic.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/host_protocol_chre.h"
#include "chre/util/lock_guard.h"

namespace chre {

/**
 * Helper function to send debug dump result to host.
 */
void sendDebugDumpResultToHost(uint16_t hostClientId, const char *debugStr,
                               size_t debugStrSize, bool complete,
                               uint32_t dataCount);

/**
 * @brief Platform specific host link.
 */
class HostLinkBase {
 public:
  HostLinkBase();
  ~HostLinkBase();

  static void vChreReceiveTask(void * /*pvParameters*/);
  static void vChreSendTask(void * /*pvParameters*/);
  static void chreIpiHandler(unsigned int /*id*/, void * /*prdata*/,
                             void * /*data*/, unsigned int /*len*/);
  void initializeIpi(void);

  /**
   * Implements the IPC message receive handler.
   *
   * @param cookie An opaque pointer that was provided to the IPC driver during
   *        callback registration.
   * @param message The host message sent to CHRE.
   * @param messageLen The host message length in bytes.
   */
  static void receive(HostLinkBase * /*instance*/, void * /*message*/,
                      int /*messageLen*/);
  /**
   * Send a message to the host.
   *
   * @param data The message to host payload.
   * @param dataLen Size of the message payload in bytes.
   * @return true if the operation succeeds, false otherwise.
   */
  static bool send(uint8_t * /*data*/, size_t /*dataLen*/);

  void setInitialized(bool initialized) {
    mInitialized = initialized;
  }

  bool isInitialized() const {
    return mInitialized;
  }

  /**
   * Sends a request to the host for a time sync message.
   */
  static void sendTimeSyncRequest();
  /**
   * Enqueues a V2 log message to be sent to the host.
   *
   * @param logMessage Pointer to a buffer that has the log message. Note that
   * the message might be encoded
   * @param logMessageSize length of the log message buffer
   * @param numLogsDropped the number of logs dropped since CHRE started
   */
  void sendLogMessageV2(const uint8_t * /*logMessage*/,
                        size_t /*logMessageSize*/,
                        uint32_t /*num_logs_dropped*/);

 private:
  AtomicBool mInitialized = false;
};

/**
 * Sends a request to the host to enable the audio feature.
 */
void sendAudioRequest();

/**
 * Sends a request to the host to disable the audio feature.
 */
void sendAudioRelease();

}  // namespace chre

#endif  // CHRE_PLATFORM_TINYSYS_HOST_LINK_BASE_H_
