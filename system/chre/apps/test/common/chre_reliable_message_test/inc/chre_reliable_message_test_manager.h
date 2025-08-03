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

#ifndef CHRE_RELIABLE_MESSAGE_TEST_MANAGER_H_
#define CHRE_RELIABLE_MESSAGE_TEST_MANAGER_H_

#include <cstdint>

#include "chre/util/singleton.h"
#include "chre_api/chre.h"
#include "chre_reliable_message_test.nanopb.h"

namespace chre::reliable_message_test {

// The manager class for the CHRE reliable message test.
class Manager {
 public:
  // Called during nanoappStart().
  bool start();

  // Called during nanoapp handleEvent().
  void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData);

  // Called during nanoappEnd().
  void end();

  // Handles the message free callback.
  void handleMessageFreeCallback(void *message, size_t messageSize);

 private:
  // Completes the test by sending the result back to the host.
  // The msg is sent to the host as the error value.
  void completeTest(bool success, const char *msg = "");

  // Sends messages to the host.
  bool sendMessages(
      const chre_reliable_message_test_SendMessagesCommand *command);

  // Handles a host echo message.
  void handleHostEchoMessage(const uint8_t *message, uint32_t messageSize);

  // Handles an async message status.
  void handleAsyncMessageStatus(const chreAsyncResult *result);

  // Handles a message from the host. Returns true if the test should
  // continue.
  bool handleMessageFromHost(const chreMessageFromHostData *hostData);

  // Message sent to the host.
  uint8_t *mMessage;

  // Number of expected message async results.
  uint32_t mNumExpectedAsyncResults;

  // The next cookie value.
  uint32_t mNextExpectedCookie;

  // Number of expected host echo messages and theit size.
  uint32_t mNumExpectedHostEchoMessages;
  uint32_t mExpectedHostEchoMessageSize;

  // Number of expected freeCallback calls.
  uint32_t mNumExpectedFreeMessageCallbacks;

  // The host endpoint ID of the connected test app.
  uint16_t mHostEndpointId = CHRE_HOST_ENDPOINT_UNSPECIFIED;

  // If the test is running.
  bool mTestRunning = false;
};

// The CHRE reliable message test manager singleton.
typedef Singleton<Manager> ManagerSingleton;

}  // namespace chre::reliable_message_test

#endif  // CHRE_RELIABLE_MESSAGE_TEST_MANAGER_H_
