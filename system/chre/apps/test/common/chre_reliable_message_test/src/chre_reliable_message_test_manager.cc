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

#include "chre_reliable_message_test_manager.h"

#include <pb_decode.h>
#include <string.h>
#include <cstddef>
#include <cstdint>

#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/optional.h"
#include "chre_api/chre.h"
#include "send_message.h"

namespace chre::reliable_message_test {

namespace {

using ::chre::test_shared::sendTestResultWithMsgToHost;

void freeCallback(void *message, size_t messageSize) {
  ManagerSingleton::get()->handleMessageFreeCallback(message, messageSize);
}

}  // namespace

bool Manager::start() {
  return true;
}

void Manager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                          const void *eventData) {
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    completeTest(false, "Received an event not from CHRE");
  }
  switch (eventType) {
    case CHRE_EVENT_MESSAGE_FROM_HOST: {
      auto hostData = static_cast<const chreMessageFromHostData *>(eventData);
      if (!handleMessageFromHost(hostData)) {
        return;
      }

      break;
    }
    case CHRE_EVENT_RELIABLE_MSG_ASYNC_RESULT: {
      auto result = static_cast<const chreAsyncResult *>(eventData);
      handleAsyncMessageStatus(result);
      break;
    }
  }

  if (mNumExpectedAsyncResults == 0 && mNumExpectedHostEchoMessages == 0 &&
      mNumExpectedFreeMessageCallbacks == 0) {
    completeTest(true);
  }
}

void Manager::end() {}

void Manager::handleMessageFreeCallback(void *message, size_t messageSize) {
  if (message != mMessage) {
    return completeTest(false, "Unexpected message pointer in free callback");
  }

  if (messageSize != mExpectedHostEchoMessageSize) {
    return completeTest(false, "Unexpected message size in free callback");
  }

  mNumExpectedFreeMessageCallbacks--;
}

void Manager::completeTest(bool success, const char *message) {
  if (!mTestRunning) {
    return;
  }

  if (success) {
    LOGI("Test completed successfully");
  } else {
    LOGE("Test completed in error with message \"%s\"", message);
  }

  mTestRunning = false;
  chreHeapFree(mMessage);
  sendTestResultWithMsgToHost(
      mHostEndpointId, chre_reliable_message_test_MessageType_TEST_RESULT,
      success, message, /* abortOnFailure= */ false);
}

bool Manager::sendMessages(
    const chre_reliable_message_test_SendMessagesCommand *command) {
  if (command->messageSize == 0) {
    mMessage = nullptr;
  } else {
    mMessage = static_cast<uint8_t *>(chreHeapAlloc(command->messageSize));
    if (mMessage == nullptr) {
      LOG_OOM();
      return false;
    }
    for (uint32_t i = 0; i < command->messageSize; i++) {
      mMessage[i] = i;
    }
  }

  LOGI("Sending %" PRIu32 " messages of size %" PRIu32, command->numMessages,
       command->messageSize);

  bool success = true;

  mExpectedHostEchoMessageSize = command->messageSize;
  mNumExpectedHostEchoMessages = command->numMessages;
  mNumExpectedAsyncResults = command->numMessages;
  mNextExpectedCookie = 0;
  mNumExpectedFreeMessageCallbacks = command->numMessages;

  for (uint32_t i = 0; i < command->numMessages; i++) {
    NestedDataPtr cookie(i);
    if (!chreSendReliableMessageAsync(
            mMessage, command->messageSize,
            chre_reliable_message_test_MessageType_HOST_ECHO_MESSAGE,
            mHostEndpointId,
            /* messagePermissions= */ 0, freeCallback, cookie)) {
      success = false;
      break;
    }
  }

  return success;
}

void Manager::handleHostEchoMessage(const uint8_t *message,
                                    uint32_t messageSize) {
  if (mNumExpectedHostEchoMessages == 0) {
    return completeTest(false, "Unexpected message received");
  }

  if (messageSize != mExpectedHostEchoMessageSize) {
    return completeTest(false, "Unexpected message size");
  }

  for (uint32_t i = 0; i < mExpectedHostEchoMessageSize; i++) {
    if (message[i] != mMessage[i]) {
      return completeTest(false, "Unexpected message content");
    }
  }

  mNumExpectedHostEchoMessages--;
}

void Manager::handleAsyncMessageStatus(const chreAsyncResult *result) {
  if (mNumExpectedAsyncResults == 0) {
    return completeTest(false, "Unexpected message status received");
  }

  if (result->cookie != NestedDataPtr(mNextExpectedCookie)) {
    return completeTest(false, "Unexpected cookie value");
  }

  if (!result->success) {
    return completeTest(false, "Transaction did not succeed");
  }

  mNextExpectedCookie++;
  mNumExpectedAsyncResults--;
}

bool Manager::handleMessageFromHost(const chreMessageFromHostData *hostData) {
  mHostEndpointId = hostData->hostEndpoint;

  switch (hostData->messageType) {
    case chre_reliable_message_test_MessageType_SEND_MESSAGES: {
      mTestRunning = true;

      pb_istream_t istream = pb_istream_from_buffer(
          static_cast<const pb_byte_t *>(hostData->message),
          hostData->messageSize);

      chre_reliable_message_test_SendMessagesCommand command =
          chre_reliable_message_test_SendMessagesCommand_init_default;

      if (!pb_decode(&istream,
                     chre_reliable_message_test_SendMessagesCommand_fields,
                     &command)) {
        completeTest(false, "Failed to decode the proto");
        return false;
      }

      if (!sendMessages(&command)) {
        completeTest(false, "Failed to send the messages");
        return false;
      }

      break;
    }
    case chre_reliable_message_test_MessageType_HOST_ECHO_MESSAGE: {
      handleHostEchoMessage(static_cast<const uint8_t *>(hostData->message),
                            hostData->messageSize);
      break;
    }
    case chre_reliable_message_test_MessageType_NANOAPP_ECHO_MESSAGE: {
      const uint32_t kSize = hostData->messageSize;

      void *message = nullptr;
      if (kSize > 0) {
        message = chreHeapAlloc(kSize);
        if (message == nullptr) {
          LOG_OOM();
          return false;
        }
      }
      memcpy(message, hostData->message, kSize);
      if (!chreSendReliableMessageAsync(
              message, kSize, hostData->messageType, hostData->hostEndpoint,
              /* messagePermissions= */ 0, heapFreeMessageCallback,
              /* cookie= */ nullptr)) {
        LOGE("Failed to send the message");
        completeTest(false, "Failed to send messages");
        return false;
      }
      break;
    }
  }
  return true;
}

}  // namespace chre::reliable_message_test
