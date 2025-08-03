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

#ifndef CHRE_PLATFORM_SLPI_HOST_LINK_BASE_H_
#define CHRE_PLATFORM_SLPI_HOST_LINK_BASE_H_

#include <cstdbool>
#include <cstddef>
#include <cstdint>

#include "timer.h"

namespace chre {

/**
 * Helper function to send debug dump result to host.
 */
void sendDebugDumpResultToHost(uint16_t hostClientId, const char *debugStr,
                               size_t debugStrSize, bool complete,
                               uint32_t dataCount);

class HostLinkBase {
 public:
  /**
   * Blocks the current thread until the host has retrieved all messages pending
   * in the outbound queue, or a timeout occurs. For proper function, it should
   * not be possible for new messages to be added to the queue at the point when
   * this is called.
   *
   * @return true if the outbound queue was successfully emptied
   */
  static bool flushOutboundQueue();

  /**
   * Enqueues a log message to be sent to the host.
   *
   * @param logMessage Pointer to a buffer that has the log message. Note that
   * the message might be encoded
   *
   * @param logMessageSize length of the log message buffer
   */
  void sendLogMessage(const uint8_t *logMessage, size_t logMessageSize);

  /**
   * Enqueues a V2 log message to be sent to the host.
   *
   * @param logMessage Pointer to a buffer that has the log message. Note that
   * the message might be encoded
   *
   * @param logMessageSize length of the log message buffer
   *
   * @param numLogsDropped number of logs dropped since CHRE start
   */
  void sendLogMessageV2(const uint8_t *logMessage, size_t logMessageSize,
                        uint32_t numLogsDropped);

  /**
   * Enqueues a NAN configuration request to be sent to the host.
   *
   * @param enable Requests that NAN be enabled or disabled based on the
   *        boolean's value.
   */
  void sendNanConfiguration(bool enable);

  /**
   * Attempts to flush the outbound queue and gracefully inform the host that we
   * are exiting.
   */
  static void shutdown();

 private:
  static constexpr time_timetick_type kPollingIntervalUsec = 5000;
};

/**
 * Requests that the HostLink send the log buffer to the host.
 */
void requestHostLinkLogBufferFlush();

/**
 * Sends a request to the host to enable the audio feature.
 */
void sendAudioRequest();

/**
 * Sends a request to the host to disable the audio feature.
 */
void sendAudioRelease();

}  // namespace chre

#endif  // CHRE_PLATFORM_SLPI_HOST_LINK_BASE_H_
