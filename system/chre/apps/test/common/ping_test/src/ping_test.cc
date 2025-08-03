/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <cinttypes>

#include <pb_decode.h>

#include "chre/util/nanoapp/log.h"
#include "chre_api/chre.h"
#include "ping_test.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[PingTest]"

namespace chre {

namespace {

//! The message payload to use when responding to a ping request.
const char *kPingResponseMsg = "Pong!";

void handleMessageFromHost(uint32_t senderInstanceId,
                           const chreMessageFromHostData *hostData) {
  bool success = false;
  uint32_t messageType = hostData->messageType;
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else if (messageType != ping_test_MessageType_PING_COMMAND) {
    LOGE("Invalid message type %" PRIu32, messageType);
  } else {
    pb_istream_t istream = pb_istream_from_buffer(
        static_cast<const pb_byte_t *>(hostData->message),
        hostData->messageSize);
    ping_test_PingCommand command = ping_test_PingCommand_init_default;

    if (!pb_decode(&istream, ping_test_PingCommand_fields, &command)) {
      LOGE("Failed to decode ping command error %s", PB_GET_ERROR(&istream));
    } else {
      uint32_t permissions = command.permissions;
      LOGI("Got ping command message with permission 0x%" PRIx32, permissions);
      success = chreSendMessageWithPermissions(
          const_cast<char *>(kPingResponseMsg), strlen(kPingResponseMsg) + 1,
          ping_test_MessageType_PING_RESPONSE, hostData->hostEndpoint,
          permissions, nullptr /* freeCallback */);
    }
  }

  if (!success) {
    test_shared::sendTestResultToHost(
        hostData->hostEndpoint,
        ping_test_MessageType_TEST_RESULT /* messageType */,
        false /* success */);
  }
}

}  // anonymous namespace

extern "C" void nanoappHandleEvent(uint32_t senderInstanceId,
                                   uint16_t eventType, const void *eventData) {
  if (eventType == CHRE_EVENT_MESSAGE_FROM_HOST) {
    handleMessageFromHost(
        senderInstanceId,
        static_cast<const chreMessageFromHostData *>(eventData));
  } else {
    LOGW("Got unknown event type from senderInstanceId %" PRIu32
         " and with eventType %" PRIu16,
         senderInstanceId, eventType);
  }
}

extern "C" bool nanoappStart(void) {
  return true;
}

extern "C" void nanoappEnd(void) {}

}  // namespace chre
