/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "send_message.h"

#include <pb_encode.h>
#include <cinttypes>

#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre_api/chre.h"
#include "chre_test_common.nanopb.h"

#ifndef LOG_TAG
#define LOG_TAG "[TestShared]"
#endif

namespace chre {
namespace test_shared {
namespace {

bool encodeErrorMessage(pb_ostream_t *stream, const pb_field_t * /*field*/,
                        void *const *arg) {
  const char *str = static_cast<const char *>(const_cast<const void *>(*arg));
  size_t len = strlen(str);
  return pb_encode_tag_for_field(
             stream, &chre_test_common_TestResult_fields
                         [chre_test_common_TestResult_errorMessage_tag - 1]) &&
         pb_encode_string(stream, reinterpret_cast<const pb_byte_t *>(str),
                          len);
}

}  // namespace

void sendTestResultWithMsgToHost(uint16_t hostEndpointId, uint32_t messageType,
                                 bool success, const char *errMessage,
                                 bool abortOnFailure) {
  // Unspecified endpoint is not allowed in chreSendMessageToHostEndpoint.
  if (hostEndpointId == CHRE_HOST_ENDPOINT_UNSPECIFIED) {
    hostEndpointId = CHRE_HOST_ENDPOINT_BROADCAST;
    LOGE("Unspecified endpoint ID is not allowed");
    success = false;
  }

  chre_test_common_TestResult result = chre_test_common_TestResult_init_default;
  result.has_code = true;
  result.code = success ? chre_test_common_TestResult_Code_PASSED
                        : chre_test_common_TestResult_Code_FAILED;
  if (!success && errMessage != nullptr) {
    result.errorMessage = {.funcs = {.encode = encodeErrorMessage},
                           .arg = const_cast<char *>(errMessage)};
    LOGE("%s", errMessage);
  }

  sendMessageToHost(hostEndpointId, &result, chre_test_common_TestResult_fields,
                    messageType);

  if (!success && abortOnFailure) {
    chreAbort(0);
  }
}

void sendTestResultToHost(uint16_t hostEndpointId, uint32_t messageType,
                          bool success) {
  sendTestResultWithMsgToHost(hostEndpointId, messageType, success,
                              nullptr /* errMessage */);
}

void sendEmptyMessageToHost(uint16_t hostEndpointId, uint32_t messageType) {
  // Unspecified endpoint is not allowed in chreSendMessageToHostEndpoint.
  if (hostEndpointId == CHRE_HOST_ENDPOINT_UNSPECIFIED) {
    hostEndpointId = CHRE_HOST_ENDPOINT_BROADCAST;
    LOGE("Unspecified endpoint ID is not allowed");
    // TODO: Send failure message to host
    return;
  }

  chreSendMessageToHostEndpoint(nullptr /* message */, 0 /* messageSize */,
                                messageType, hostEndpointId,
                                nullptr /* freeCallback */);
}

void sendMessageToHost(uint16_t hostEndpointId, const void *message,
                       const pb_field_t *fields, uint32_t messageType) {
  size_t size;
  if (!pb_get_encoded_size(&size, fields, message)) {
    LOGE("Failed to get message size");
  } else {
    pb_byte_t *bytes = static_cast<pb_byte_t *>(chreHeapAlloc(size));
    if (size > 0 && bytes == nullptr) {
      LOG_OOM();
    } else {
      pb_ostream_t stream = pb_ostream_from_buffer(bytes, size);
      if (!pb_encode(&stream, fields, message)) {
        LOGE("Failed to encode message error %s", PB_GET_ERROR(&stream));
        chreHeapFree(bytes);
      } else if (!chreSendMessageToHostEndpoint(bytes, size, messageType,
                                                hostEndpointId,
                                                heapFreeMessageCallback)) {
        LOGE("Failed to send message to host");
      }
    }
  }
}

}  // namespace test_shared

}  // namespace chre
