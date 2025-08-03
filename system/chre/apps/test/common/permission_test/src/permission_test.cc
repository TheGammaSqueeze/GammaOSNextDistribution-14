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

#include <pb_decode.h>
#include <cinttypes>

#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_api/chre.h"
#include "permission_test.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[PermissionTest]"

/**
 * This nanoapp is compiled with the ability to refer to permission gated APIs
 * but doesn't actually declare permissions to use them. Its purpose is to
 * validate the permission gated APIs always return false for any of its
 * requests to ensure CHRE implementations are validating nanoapp permissions
 * prior to accepting a nanoapp request.
 */

namespace chre {
namespace {

constexpr Nanoseconds kGnssMinInterval = Seconds(1);

void checkAudioApis(uint32_t hostEndpointId) {
  struct chreAudioSource audioSource;
  for (uint32_t i = 0; chreAudioGetSource(i, &audioSource); i++) {
    if (chreAudioConfigureSource(i, true /* enable */,
                                 audioSource.minBufferDuration,
                                 audioSource.minBufferDuration)) {
      test_shared::sendTestResultWithMsgToHost(
          hostEndpointId, permission_test_MessageType_TEST_RESULT,
          false /* success */, "Configured audio without audio permission");
    }

    // TODO(b/174590023): Check chreAudioGetStatus once it's supported by our
    // CHRE versions.
  }
}

void checkGnssApis(uint32_t hostEndpointId) {
  if (chreGnssLocationSessionStartAsync(kGnssMinInterval.toRawNanoseconds(),
                                        kGnssMinInterval.toRawNanoseconds(),
                                        nullptr /* cookie */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */,
        "Requested GNSS location without the GNSS permission");
  }

  if (chreGnssMeasurementSessionStartAsync(kGnssMinInterval.toRawNanoseconds(),
                                           nullptr /* cookie */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */,
        "Requested GNSS measurements without the GNSS permission");
  }

  if (chreGnssConfigurePassiveLocationListener(true /* enable */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */,
        "Requested GNSS passive locations without the GNSS permission");
  }
}

void checkWifiApis(uint32_t hostEndpointId) {
  if (chreWifiConfigureScanMonitorAsync(true /* enable */,
                                        nullptr /* cookie */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */,
        "Requested WiFi scan monitor without WiFi permission");
  }

  if (chreWifiRequestScanAsyncDefault(nullptr /* cookie */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */, "Requested WiFi scan without WiFi permission");
  }

  // RTT ranging not included in WiFi test because it requires a valid AP to
  // request which can't be obtained due to invalid permissions.
}

void checkWwanApis(uint32_t hostEndpointId) {
  if (chreWwanGetCellInfoAsync(nullptr /* cookie */)) {
    test_shared::sendTestResultWithMsgToHost(
        hostEndpointId, permission_test_MessageType_TEST_RESULT,
        false /* success */, "Requested cell info without WWAN permission");
  }
}

// Call APIs that require permissions and verify that they all refuse requests
// from this nanoapp since it has no permissions.
void checkPermissionGatedApis(uint32_t hostEndpointId) {
  checkAudioApis(hostEndpointId);
  checkGnssApis(hostEndpointId);
  checkWifiApis(hostEndpointId);
  checkWwanApis(hostEndpointId);
}

void handleMessageFromHost(uint32_t senderInstanceId,
                           const chreMessageFromHostData *hostData) {
  bool success = false;
  uint32_t messageType = hostData->messageType;
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else if (messageType != permission_test_MessageType_TEST_COMMAND) {
    LOGE("Invalid message type %" PRIu32, messageType);
  } else {
    // Method will abort if any failures are encountered.
    checkPermissionGatedApis(hostData->hostEndpoint);
    success = true;
  }

  test_shared::sendTestResultWithMsgToHost(
      hostData->hostEndpoint,
      permission_test_MessageType_TEST_RESULT /* messageType */, success,
      nullptr /* errMessage */);
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
