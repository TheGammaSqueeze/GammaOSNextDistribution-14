/* * Copyright (C) 2020 The Android Open Source Project
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

#include "chre_cross_validator_wifi_manager.h"

#include <stdio.h>
#include <algorithm>
#include <cinttypes>
#include <cstring>

#include "chre/util/nanoapp/assert.h"
#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/nanoapp/wifi.h"
#include "chre_api/chre.h"
#include "chre_cross_validation_wifi.nanopb.h"
#include "chre_test_common.nanopb.h"
#include "send_message.h"

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

namespace chre {

namespace cross_validator_wifi {

// Fake scan monitor cookie which is not used
constexpr uint32_t kScanMonitoringCookie = 0;

void Manager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                          const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_MESSAGE_FROM_HOST:
      handleMessageFromHost(
          senderInstanceId,
          static_cast<const chreMessageFromHostData *>(eventData));
      break;
    case CHRE_EVENT_WIFI_ASYNC_RESULT:
      handleWifiAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;
    case CHRE_EVENT_WIFI_SCAN_RESULT:
      handleWifiScanResult(static_cast<const chreWifiScanEvent *>(eventData));
      break;
    default:
      LOGE("Unknown message type %" PRIu16 "received when handling event",
           eventType);
  }
}

void Manager::handleMessageFromHost(uint32_t senderInstanceId,
                                    const chreMessageFromHostData *hostData) {
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else {
    mCrossValidatorState.hostEndpoint = hostData->hostEndpoint;
    switch (hostData->messageType) {
      case chre_cross_validation_wifi_MessageType_STEP_START: {
        pb_istream_t stream = pb_istream_from_buffer(
            static_cast<const pb_byte_t *>(
                const_cast<const void *>(hostData->message)),
            hostData->messageSize);
        chre_cross_validation_wifi_StepStartCommand stepStartCommand;
        if (!pb_decode(&stream,
                       chre_cross_validation_wifi_StepStartCommand_fields,
                       &stepStartCommand)) {
          LOGE("Error decoding StepStartCommand");
        } else {
          handleStepStartMessage(stepStartCommand);
        }
        break;
      }
      case chre_cross_validation_wifi_MessageType_SCAN_RESULT:
        handleDataMessage(hostData);
        break;
      default:
        LOGE("Unknown message type %" PRIu32 " for host message",
             hostData->messageType);
    }
  }
}

void Manager::handleStepStartMessage(
    chre_cross_validation_wifi_StepStartCommand stepStartCommand) {
  switch (stepStartCommand.step) {
    case chre_cross_validation_wifi_Step_INIT:
      LOGE("Received StepStartCommand for INIT step");
      break;
    case chre_cross_validation_wifi_Step_CAPABILITIES: {
      chre_cross_validation_wifi_WifiCapabilities wifiCapabilities =
          makeWifiCapabilitiesMessage(chreWifiGetCapabilities());
      test_shared::sendMessageToHost(
          mCrossValidatorState.hostEndpoint, &wifiCapabilities,
          chre_cross_validation_wifi_WifiCapabilities_fields,
          chre_cross_validation_wifi_MessageType_WIFI_CAPABILITIES);
      break;
    }
    case chre_cross_validation_wifi_Step_SETUP: {
      if (!chreWifiConfigureScanMonitorAsync(true /* enable */,
                                             &kScanMonitoringCookie)) {
        LOGE("chreWifiConfigureScanMonitorAsync() failed");
        test_shared::sendTestResultWithMsgToHost(
            mCrossValidatorState.hostEndpoint,
            chre_cross_validation_wifi_MessageType_STEP_RESULT,
            false /*success*/, "setupWifiScanMonitoring failed",
            false /*abortOnFailure*/);
      } else {
        LOGD("chreWifiConfigureScanMonitorAsync() succeeded");
        if (stepStartCommand.has_chreScanCapacity) {
          mExpectedMaxChreResultCanHandle = stepStartCommand.chreScanCapacity;
        }
      }
      break;
    }
    case chre_cross_validation_wifi_Step_VALIDATE:
      break;
  }
  mStep = stepStartCommand.step;
}

void Manager::handleDataMessage(const chreMessageFromHostData *hostData) {
  pb_istream_t stream =
      pb_istream_from_buffer(reinterpret_cast<const pb_byte_t *>(
                                 const_cast<const void *>(hostData->message)),
                             hostData->messageSize);
  WifiScanResult scanResult(&stream);
  uint8_t scanResultIndex = scanResult.getResultIndex();
  if (scanResultIndex > scanResult.getTotalNumResults()) {
    LOGE("AP scan result index is greater than scan results size");
  } else {
    if (!mApScanResults.push_back(scanResult)) {
      LOG_OOM();
    }
    if (scanResult.isLastMessage()) {
      mApDataCollectionDone = true;
      if (mChreDataCollectionDone) {
        compareAndSendResultToHost();
      }
    }
  }
}

void Manager::handleWifiScanResult(const chreWifiScanEvent *event) {
  for (uint8_t i = 0; i < event->resultCount; i++) {
    mChreScanResults.push_back(event->results[i]);
  }
  if (mChreScanResults.size() >= event->resultTotal) {
    mChreDataCollectionDone = true;
    if (mApDataCollectionDone) {
      compareAndSendResultToHost();
    }
  }
}

void Manager::compareAndSendResultToHost() {
  chre_test_common_TestResult testResult;
  bool belowMaxSizeCheck =
      mApScanResults.size() <= mExpectedMaxChreResultCanHandle &&
      mApScanResults.size() != mChreScanResults.size();
  bool aboveMaxSizeCheck =
      mApScanResults.size() > mExpectedMaxChreResultCanHandle &&
      mApScanResults.size() < mChreScanResults.size();

  verifyScanResults(&testResult);

  if (belowMaxSizeCheck || aboveMaxSizeCheck) {
    LOGE(
        "AP and CHRE wifi scan result counts differ, AP = %zu, CHRE = %zu, MAX "
        "= %" PRIu8,
        mApScanResults.size(), mChreScanResults.size(),
        mExpectedMaxChreResultCanHandle);
    test_shared::sendTestResultWithMsgToHost(
        mCrossValidatorState.hostEndpoint,
        chre_cross_validation_wifi_MessageType_STEP_RESULT, false /*success*/,
        "There is a different number of AP and CHRE scan results.",
        false /*abortOnFailure*/);

    return;
  }

  test_shared::sendMessageToHost(
      mCrossValidatorState.hostEndpoint, &testResult,
      chre_test_common_TestResult_fields,
      chre_cross_validation_wifi_MessageType_STEP_RESULT);
}

void Manager::verifyScanResults(chre_test_common_TestResult *testResultOut) {
  bool allResultsValid = true;
  for (uint8_t i = 0; i < mChreScanResults.size(); i++) {
    const WifiScanResult chreScanResult = WifiScanResult(mChreScanResults[i]);
    bool isValidResult = true;
    size_t index = getMatchingScanResultIndex(mApScanResults, chreScanResult);

    const char *bssidStr = "<non-printable>";
    char bssidBuffer[chre::kBssidStrLen];
    if (chre::parseBssidToStr(chreScanResult.getBssid(), bssidBuffer,
                              sizeof(bssidBuffer))) {
      bssidStr = bssidBuffer;
    }

    if (index != SIZE_MAX) {
      WifiScanResult &apScanResult = mApScanResults[index];
      if (apScanResult.getSeen()) {
        *testResultOut = makeTestResultProtoMessage(
            false, "Saw a CHRE scan result with a duplicate BSSID.");
        isValidResult = false;
        LOGE("CHRE Scan Result with bssid: %s has a dupplicate BSSID",
             bssidStr);
      }
      if (!WifiScanResult::areEqual(chreScanResult, apScanResult)) {
        *testResultOut =
            makeTestResultProtoMessage(false,
                                       "Fields differ between an AP and "
                                       "CHRE scan result with same Bssid.");
        isValidResult = false;
        LOGE(
            "CHRE Scan Result with bssid: %s found fields differ with "
            "an AP scan result with same Bssid",
            bssidStr);
      }
      // Mark this scan result as already seen so that the next time it is used
      // as a match the test will fail because of duplicate scan results.
      apScanResult.didSee();
    } else {
      // Error CHRE BSSID does not match any AP
      *testResultOut = makeTestResultProtoMessage(
          false,
          "Could not find an AP scan result with the same Bssid as a CHRE "
          "result");
      isValidResult = false;
      LOGE(
          "CHRE Scan Result with bssid: %s fail to find an AP scan "
          "with same Bssid",
          bssidStr);
    }
    if (!isValidResult) {
      LOGE("False CHRE Scan Result with the following info:");
      logChreWifiResult(mChreScanResults[i]);
      allResultsValid = false;
    }
  }

  for (auto &scanResult : mApScanResults) {
    if (!scanResult.getSeen()) {
      LOGW("AP %s is not seen in CHRE", scanResult.getSsid());
      // Since CHRE is more constrained in memory, it is expected that if we
      // receive over a cretin amount of AP, we will drop some of them.
      if (mApScanResults.size() <= mExpectedMaxChreResultCanHandle) {
        *testResultOut = makeTestResultProtoMessage(
            false /*success*/,
            "Extra AP information shown in host "
            "when small number of AP results presenting");
        allResultsValid = false;
      }
    }
  }
  if (allResultsValid) {
    *testResultOut = makeTestResultProtoMessage(true);
  }
}

size_t Manager::getMatchingScanResultIndex(
    const DynamicVector<WifiScanResult> &results,
    const WifiScanResult &queryResult) {
  for (size_t i = 0; i < results.size(); i++) {
    if (WifiScanResult::bssidsAreEqual(results[i], queryResult)) {
      return i;
    }
  }
  return SIZE_MAX;
}

bool Manager::encodeErrorMessage(pb_ostream_t *stream,
                                 const pb_field_t * /*field*/,
                                 void *const *arg) {
  const char *str = static_cast<const char *>(const_cast<const void *>(*arg));
  size_t len = strlen(str);
  return pb_encode_tag_for_field(
             stream, &chre_test_common_TestResult_fields
                         [chre_test_common_TestResult_errorMessage_tag - 1]) &&
         pb_encode_string(stream, reinterpret_cast<const pb_byte_t *>(str),
                          len);
}

chre_test_common_TestResult Manager::makeTestResultProtoMessage(
    bool success, const char *errMessage) {
  // TODO(b/154271547): Move this implementation into
  // common/shared/send_message.cc::sendTestResultToHost
  chre_test_common_TestResult testResult =
      chre_test_common_TestResult_init_default;
  testResult.has_code = true;
  testResult.code = success ? chre_test_common_TestResult_Code_PASSED
                            : chre_test_common_TestResult_Code_FAILED;
  if (!success && errMessage != nullptr) {
    testResult.errorMessage = {.funcs = {.encode = encodeErrorMessage},
                               .arg = const_cast<char *>(errMessage)};
  }
  return testResult;
}

chre_cross_validation_wifi_WifiCapabilities
Manager::makeWifiCapabilitiesMessage(uint32_t capabilitiesFromChre) {
  chre_cross_validation_wifi_WifiCapabilities capabilities;
  capabilities.has_wifiCapabilities = true;
  capabilities.wifiCapabilities = capabilitiesFromChre;
  return capabilities;
}

void Manager::handleWifiAsyncResult(const chreAsyncResult *result) {
  chre_test_common_TestResult testResult;
  bool sendMessage = false;
  if (result->requestType == CHRE_WIFI_REQUEST_TYPE_CONFIGURE_SCAN_MONITOR) {
    if (mStep != chre_cross_validation_wifi_Step_SETUP) {
      testResult = makeTestResultProtoMessage(
          false, "Received scan monitor result event when step is not SETUP");
      sendMessage = true;
    } else {
      if (result->success) {
        LOGD("Wifi scan monitoring setup successfully");
        testResult = makeTestResultProtoMessage(true);
        sendMessage = true;
      } else {
        LOGE("Wifi scan monitoring setup failed async w/ error code %" PRIu8
             ".",
             result->errorCode);
        testResult = makeTestResultProtoMessage(
            false, "Wifi scan monitoring setup failed async.");
        sendMessage = true;
      }
    }
  } else {
    testResult = makeTestResultProtoMessage(
        false, "Unknown chre async result type received");
    sendMessage = true;
  }
  if (sendMessage) {
    test_shared::sendMessageToHost(
        mCrossValidatorState.hostEndpoint, &testResult,
        chre_test_common_TestResult_fields,
        chre_cross_validation_wifi_MessageType_STEP_RESULT);
  }
}

}  // namespace cross_validator_wifi

}  // namespace chre
