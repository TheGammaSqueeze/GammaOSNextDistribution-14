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

#ifndef CHRE_TEST_SHARED_SEND_MESSAGE_H_
#define CHRE_TEST_SHARED_SEND_MESSAGE_H_

#include <pb_encode.h>
#include <cinttypes>

namespace chre {

namespace test_shared {

/**
 * Same as sendTestResultWithMsgToHost, but doesn't accept an error message and
 * uses the free callback specified in chre/util/nanoapp/callbacks.h
 */
void sendTestResultToHost(uint16_t hostEndpointId, uint32_t messageType,
                          bool success);

/**
 * Sends a test result to the host using the chre_test_common.TestResult
 * message.
 *
 * @param hostEndpointId The endpoint ID of the host to send the result to.
 * @param messageType The message type to associate with the test result.
 * @param success True if the test succeeded.
 * @param errMessage Nullable error message to send to the host. Error message
 *     will only be sent if success is false.
 * @param abortOnFailure If true, calls chreAbort() if success is false.
 */
void sendTestResultWithMsgToHost(uint16_t hostEndpointId, uint32_t messageType,
                                 bool success, const char *errMessage,
                                 bool abortOnFailure = true);

/**
 * Sends a message to the host with an empty payload.
 *
 * @param hostEndpointId The endpoint Id of the host to send the message to.
 * @param messageType The message type.
 */
void sendEmptyMessageToHost(uint16_t hostEndpointId, uint32_t messageType);

/**
 * Sends a message to the host.
 *
 * @param hostEndpointId The endpoint Id of the host to send the message to.
 * @param message The proto message struct pointer.
 * @param fields The fields descriptor of the proto message to encode.
 * @param messageType The message type of the message.
 */
void sendMessageToHost(uint16_t hostEndpointId, const void *message,
                       const pb_field_t *fields, uint32_t messageType);

}  // namespace test_shared

}  // namespace chre

#endif  // CHRE_TEST_SHARED_SEND_MESSAGE_H_
