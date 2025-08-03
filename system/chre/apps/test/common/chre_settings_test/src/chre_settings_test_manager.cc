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

#include "chre_settings_test_manager.h"

#include <pb_decode.h>
#include <pb_encode.h>

#include "chre/util/macros.h"
#include "chre/util/nanoapp/ble.h"
#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_settings_test.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[ChreSettingsTest]"

using chre::createBleScanFilterForKnownBeacons;
using chre::ble_constants::kNumScanFilters;

namespace chre {

namespace settings_test {

namespace {

constexpr uint32_t kWifiScanningCookie = 0x1234;
constexpr uint32_t kWifiRttCookie = 0x2345;
constexpr uint32_t kGnssLocationCookie = 0x3456;
constexpr uint32_t kGnssMeasurementCookie = 0x4567;
constexpr uint32_t kWwanCellInfoCookie = 0x5678;

// The default audio handle.
constexpr uint32_t kAudioHandle = 0;

// Flag to verify if an audio data event was received after a valid sampling
// change event (i.e., we only got the data event after a source-enabled-and-
// not-suspended event).
bool gGotSourceEnabledEvent = false;

uint32_t gAudioDataTimerHandle = CHRE_TIMER_INVALID;
constexpr uint32_t kAudioDataTimerCookie = 0xc001cafe;
uint32_t gAudioStatusTimerHandle = CHRE_TIMER_INVALID;
constexpr uint32_t kAudioStatusTimerCookie = 0xb01dcafe;

bool getFeature(const chre_settings_test_TestCommand &command,
                Manager::Feature *feature) {
  bool success = true;
  switch (command.feature) {
    case chre_settings_test_TestCommand_Feature_WIFI_SCANNING:
      *feature = Manager::Feature::WIFI_SCANNING;
      break;
    case chre_settings_test_TestCommand_Feature_WIFI_RTT:
      *feature = Manager::Feature::WIFI_RTT;
      break;
    case chre_settings_test_TestCommand_Feature_GNSS_LOCATION:
      *feature = Manager::Feature::GNSS_LOCATION;
      break;
    case chre_settings_test_TestCommand_Feature_GNSS_MEASUREMENT:
      *feature = Manager::Feature::GNSS_MEASUREMENT;
      break;
    case chre_settings_test_TestCommand_Feature_WWAN_CELL_INFO:
      *feature = Manager::Feature::WWAN_CELL_INFO;
      break;
    case chre_settings_test_TestCommand_Feature_AUDIO:
      *feature = Manager::Feature::AUDIO;
      break;
    case chre_settings_test_TestCommand_Feature_BLE_SCANNING:
      *feature = Manager::Feature::BLE_SCANNING;
      break;
    default:
      LOGE("Unknown feature %d", command.feature);
      success = false;
  }

  return success;
}

bool getFeatureState(const chre_settings_test_TestCommand &command,
                     Manager::FeatureState *state) {
  bool success = true;
  switch (command.state) {
    case chre_settings_test_TestCommand_State_ENABLED:
      *state = Manager::FeatureState::ENABLED;
      break;
    case chre_settings_test_TestCommand_State_DISABLED:
      *state = Manager::FeatureState::DISABLED;
      break;
    default:
      LOGE("Unknown feature state %d", command.state);
      success = false;
  }

  return success;
}

bool getTestStep(const chre_settings_test_TestCommand &command,
                 Manager::TestStep *step) {
  bool success = true;
  switch (command.step) {
    case chre_settings_test_TestCommand_Step_SETUP:
      *step = Manager::TestStep::SETUP;
      break;
    case chre_settings_test_TestCommand_Step_START:
      *step = Manager::TestStep::START;
      break;
    default:
      LOGE("Unknown test step %d", command.step);
      success = false;
  }

  return success;
}

bool isTestSupported() {
  // CHRE settings requirements were introduced in CHRE v1.4
  return chreGetVersion() >= CHRE_API_VERSION_1_4;
}

}  // anonymous namespace

void Manager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                          const void *eventData) {
  if (eventType == CHRE_EVENT_MESSAGE_FROM_HOST) {
    handleMessageFromHost(
        senderInstanceId,
        static_cast<const chreMessageFromHostData *>(eventData));
  } else if (senderInstanceId == CHRE_INSTANCE_ID) {
    handleDataFromChre(eventType, eventData);
  } else {
    LOGW("Got unknown event type from senderInstanceId %" PRIu32
         " and with eventType %" PRIu16,
         senderInstanceId, eventType);
  }
}

bool Manager::isFeatureSupported(Feature feature) {
  bool supported = false;

  uint32_t version = chreGetVersion();
  switch (feature) {
    case Feature::WIFI_SCANNING: {
      uint32_t capabilities = chreWifiGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_1) &&
                  ((capabilities & CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN) != 0);
      break;
    }
    case Feature::WIFI_RTT: {
      uint32_t capabilities = chreWifiGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_2) &&
                  ((capabilities & CHRE_WIFI_CAPABILITIES_RTT_RANGING) != 0);
      break;
    }
    case Feature::GNSS_LOCATION: {
      uint32_t capabilities = chreGnssGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_1) &&
                  ((capabilities & CHRE_GNSS_CAPABILITIES_LOCATION) != 0);
      break;
    }
    case Feature::GNSS_MEASUREMENT: {
      uint32_t capabilities = chreGnssGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_1) &&
                  ((capabilities & CHRE_GNSS_CAPABILITIES_MEASUREMENTS) != 0);
      break;
    }
    case Feature::WWAN_CELL_INFO: {
      uint32_t capabilities = chreWwanGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_1) &&
                  ((capabilities & CHRE_WWAN_GET_CELL_INFO) != 0);
      break;
    }
    case Feature::AUDIO: {
      struct chreAudioSource source;
      supported = chreAudioGetSource(kAudioHandle, &source);
      break;
    }
    case Feature::BLE_SCANNING: {
      uint32_t capabilities = chreBleGetCapabilities();
      supported = (version >= CHRE_API_VERSION_1_7) &&
                  ((capabilities & CHRE_BLE_CAPABILITIES_SCAN) != 0);
      break;
    }
    default:
      LOGE("Unknown feature %" PRIu8, static_cast<uint8_t>(feature));
  }

  return supported;
}

void Manager::handleMessageFromHost(uint32_t senderInstanceId,
                                    const chreMessageFromHostData *hostData) {
  bool success = false;
  uint32_t messageType = hostData->messageType;
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else if (messageType != chre_settings_test_MessageType_TEST_COMMAND) {
    LOGE("Invalid message type %" PRIu32, messageType);
  } else {
    pb_istream_t istream = pb_istream_from_buffer(
        static_cast<const pb_byte_t *>(hostData->message),
        hostData->messageSize);
    chre_settings_test_TestCommand testCommand =
        chre_settings_test_TestCommand_init_default;

    if (!pb_decode(&istream, chre_settings_test_TestCommand_fields,
                   &testCommand)) {
      LOGE("Failed to decode start command error %s", PB_GET_ERROR(&istream));
    } else {
      Feature feature;
      FeatureState state;
      TestStep step;
      if (getFeature(testCommand, &feature) &&
          getFeatureState(testCommand, &state) &&
          getTestStep(testCommand, &step)) {
        LOGD("starting test: feature: %u, state %u, step %u",
             static_cast<uint8_t>(feature), static_cast<uint8_t>(state),
             static_cast<uint8_t>(step));
        handleStartTestMessage(hostData->hostEndpoint, feature, state, step);
        success = true;
      }
    }
  }

  if (!success) {
    test_shared::sendTestResultToHost(
        hostData->hostEndpoint, chre_settings_test_MessageType_TEST_RESULT,
        false /* success */);
  }
}

void Manager::handleStartTestMessage(uint16_t hostEndpointId, Feature feature,
                                     FeatureState state, TestStep step) {
  // If the test/feature is not supported, treat as success and skip the test.
  if (!isTestSupported() || !isFeatureSupported(feature)) {
    LOGW("Skipping test - TestSupported: %u, FeatureSupported: %u",
         isTestSupported(), isFeatureSupported(feature));
    sendTestResult(hostEndpointId, true /* success */);
  } else {
    bool success = false;
    if (step == TestStep::SETUP) {
      if (feature != Feature::WIFI_RTT) {
        LOGE("Unexpected feature %" PRIu8 " for test step",
             static_cast<uint8_t>(feature));
      } else {
        success = chreWifiRequestScanAsyncDefault(&kWifiScanningCookie);
      }
    } else {
      success = startTestForFeature(feature);
    }

    if (!success) {
      sendTestResult(hostEndpointId, false /* success */);
    } else {
      mTestSession = TestSession(hostEndpointId, feature, state, step);
    }
  }
}

void Manager::handleDataFromChre(uint16_t eventType, const void *eventData) {
  if (mTestSession.has_value()) {
    // The validation for the correct data w.r.t. the current test session
    // will be done in the methods called from here.
    switch (eventType) {
      case CHRE_EVENT_AUDIO_DATA:
        handleAudioDataEvent(
            static_cast<const struct chreAudioDataEvent *>(eventData));
        break;

      case CHRE_EVENT_AUDIO_SAMPLING_CHANGE:
        handleAudioSourceStatusEvent(
            static_cast<const struct chreAudioSourceStatusEvent *>(eventData));
        break;

      case CHRE_EVENT_TIMER:
        handleTimeout(eventData);
        break;

      case CHRE_EVENT_WIFI_ASYNC_RESULT:
        handleWifiAsyncResult(static_cast<const chreAsyncResult *>(eventData));
        break;

      case CHRE_EVENT_WIFI_SCAN_RESULT:
        handleWifiScanResult(static_cast<const chreWifiScanEvent *>(eventData));
        break;

      case CHRE_EVENT_GNSS_ASYNC_RESULT:
        handleGnssAsyncResult(static_cast<const chreAsyncResult *>(eventData));
        break;

      case CHRE_EVENT_WWAN_CELL_INFO_RESULT:
        handleWwanCellInfoResult(
            static_cast<const chreWwanCellInfoResult *>(eventData));
        break;

      case CHRE_EVENT_BLE_ASYNC_RESULT:
        handleBleAsyncResult(static_cast<const chreAsyncResult *>(eventData));
        break;

      default:
        LOGE("Unknown event type %" PRIu16, eventType);
    }
  }
}

bool Manager::startTestForFeature(Feature feature) {
  bool success = true;
  switch (feature) {
    case Feature::WIFI_SCANNING:
      success = chreWifiRequestScanAsyncDefault(&kWifiScanningCookie);
      break;

    case Feature::WIFI_RTT: {
      if (!mCachedRangingTarget.has_value()) {
        LOGE("No cached WiFi RTT ranging target");
      } else {
        struct chreWifiRangingParams params = {
            .targetListLen = 1, .targetList = &mCachedRangingTarget.value()};
        success = chreWifiRequestRangingAsync(&params, &kWifiRttCookie);
      }
      break;
    }

    case Feature::GNSS_LOCATION:
      success = chreGnssLocationSessionStartAsync(1000 /* minIntervalMs */,
                                                  0 /* minTimeToNextFixMs */,
                                                  &kGnssLocationCookie);
      break;

    case Feature::GNSS_MEASUREMENT:
      success = chreGnssMeasurementSessionStartAsync(1000 /* minIntervalMs */,
                                                     &kGnssMeasurementCookie);
      break;

    case Feature::WWAN_CELL_INFO:
      success = chreWwanGetCellInfoAsync(&kWwanCellInfoCookie);
      break;

    case Feature::AUDIO: {
      struct chreAudioSource source;
      if ((success = chreAudioGetSource(kAudioHandle, &source))) {
        success = chreAudioConfigureSource(kAudioHandle, true /* enable */,
                                           source.minBufferDuration,
                                           source.minBufferDuration);
      }
      break;
    }

    case Feature::BLE_SCANNING: {
      struct chreBleScanFilter filter;
      chreBleGenericFilter uuidFilters[kNumScanFilters];
      createBleScanFilterForKnownBeacons(filter, uuidFilters, kNumScanFilters);
      success = chreBleStartScanAsync(CHRE_BLE_SCAN_MODE_FOREGROUND /* mode */,
                                      0 /* reportDelayMs */, &filter);
      break;
    }

    default:
      LOGE("Unknown feature %" PRIu8, static_cast<uint8_t>(feature));
      return false;
  }

  if (!success) {
    LOGE("Failed to make request for test feature %" PRIu8,
         static_cast<uint8_t>(feature));
  } else {
    LOGI("Starting test for feature %" PRIu8, static_cast<uint8_t>(feature));
  }

  return success;
}

bool Manager::validateAsyncResult(const chreAsyncResult *result,
                                  const void *expectedCookie) {
  bool success = false;
  if (result->cookie != expectedCookie) {
    LOGE("Unexpected cookie on async result");
  } else {
    bool featureEnabled = (mTestSession->featureState == FeatureState::ENABLED);
    bool disabledErrorCode =
        (result->errorCode == CHRE_ERROR_FUNCTION_DISABLED);

    if (featureEnabled && disabledErrorCode) {
      LOGE("Got disabled error code when feature is enabled");
    } else if (!featureEnabled && !disabledErrorCode) {
      LOGE("Got non-disabled error code when feature is disabled");
    } else {
      success = true;
    }
  }

  return success;
}

void Manager::handleWifiAsyncResult(const chreAsyncResult *result) {
  bool success = false;
  switch (result->requestType) {
    case CHRE_WIFI_REQUEST_TYPE_REQUEST_SCAN: {
      if (mTestSession->feature == Feature::WIFI_RTT) {
        // Ignore validating the scan async response since we only care about
        // the actual scan event to initiate the RTT request. A failure to
        // receive the scan response should cause a timeout at the host.
        return;
      }
      if (mTestSession->feature != Feature::WIFI_SCANNING) {
        LOGE("Unexpected WiFi scan async result: test feature %" PRIu8,
             static_cast<uint8_t>(mTestSession->feature));
      } else {
        success = validateAsyncResult(
            result, static_cast<const void *>(&kWifiScanningCookie));
      }
      break;
    }
    case CHRE_WIFI_REQUEST_TYPE_RANGING: {
      if (mTestSession->feature != Feature::WIFI_RTT) {
        LOGE("Unexpected WiFi ranging async result: test feature %" PRIu8,
             static_cast<uint8_t>(mTestSession->feature));
      } else {
        success = validateAsyncResult(
            result, static_cast<const void *>(&kWifiRttCookie));
      }
      break;
    }
    default:
      LOGE("Unexpected WiFi request type %" PRIu8, result->requestType);
  }

  sendTestResult(mTestSession->hostEndpointId, success);
}

void Manager::handleWifiScanResult(const chreWifiScanEvent *result) {
  if (mTestSession->feature == Feature::WIFI_RTT &&
      mTestSession->step == TestStep::SETUP) {
    if (result->resultCount == 0) {
      LOGE("Received empty WiFi scan result");
      sendTestResult(mTestSession->hostEndpointId, false /* success */);
    } else {
      mReceivedScanResults += result->resultCount;
      chreWifiRangingTarget target;
      // Try to find an AP with the FTM responder flag set. The RTT ranging
      // request should still work equivalently even if the flag is not set (but
      // possibly with an error in the ranging result), so we use the last entry
      // if none is found.
      size_t index = result->resultCount - 1;
      for (uint8_t i = 0; i < result->resultCount - 1; i++) {
        if ((result->results[i].flags &
             CHRE_WIFI_SCAN_RESULT_FLAGS_IS_FTM_RESPONDER) != 0) {
          index = i;
          break;
        }
      }
      chreWifiRangingTargetFromScanResult(&result->results[index], &target);
      mCachedRangingTarget = target;
      if (result->resultTotal == mReceivedScanResults) {
        mReceivedScanResults = 0;
        test_shared::sendEmptyMessageToHost(
            mTestSession->hostEndpointId,
            chre_settings_test_MessageType_TEST_SETUP_COMPLETE);
      }
    }
  }
}

void Manager::handleGnssAsyncResult(const chreAsyncResult *result) {
  bool success = false;
  switch (result->requestType) {
    case CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_START: {
      if (mTestSession->feature != Feature::GNSS_LOCATION) {
        LOGE("Unexpected GNSS location async result: test feature %" PRIu8,
             static_cast<uint8_t>(mTestSession->feature));
      } else {
        success = validateAsyncResult(
            result, static_cast<const void *>(&kGnssLocationCookie));
        chreGnssLocationSessionStopAsync(&kGnssLocationCookie);
      }
      break;
    }
    case CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_START: {
      if (mTestSession->feature != Feature::GNSS_MEASUREMENT) {
        LOGE("Unexpected GNSS measurement async result: test feature %" PRIu8,
             static_cast<uint8_t>(mTestSession->feature));
      } else {
        success = validateAsyncResult(
            result, static_cast<const void *>(&kGnssMeasurementCookie));
        chreGnssMeasurementSessionStopAsync(&kGnssMeasurementCookie);
      }
      break;
    }
    default:
      LOGE("Unexpected GNSS request type %" PRIu8, result->requestType);
  }

  sendTestResult(mTestSession->hostEndpointId, success);
}

void Manager::handleWwanCellInfoResult(const chreWwanCellInfoResult *result) {
  bool success = false;
  // For WWAN, we treat "DISABLED" as success but with empty results, per
  // CHRE API requirements.
  if (mTestSession->feature != Feature::WWAN_CELL_INFO) {
    LOGE("Unexpected WWAN cell info result: test feature %" PRIu8,
         static_cast<uint8_t>(mTestSession->feature));
  } else if (result->cookie != &kWwanCellInfoCookie) {
    LOGE("Unexpected cookie on WWAN cell info result");
  } else if (result->errorCode != CHRE_ERROR_NONE) {
    LOGE("WWAN cell info result failed: error code %" PRIu8, result->errorCode);
  } else if (mTestSession->featureState == FeatureState::DISABLED &&
             result->cellInfoCount > 0) {
    LOGE("WWAN cell info result should be empty when disabled: count %" PRIu8,
         result->cellInfoCount);
  } else {
    success = true;
  }

  sendTestResult(mTestSession->hostEndpointId, success);
}

// The MicDisabled Settings test works as follows:
// * The contents of the Source Status Event are parsed, and there are 4
//   possible scenarios for the flow of our test:
//
// - Mic Access was disabled, source was suspended
// -- Since CHRE guarantees that we'll receive audio data events spaced at
//    the source's minBufferDuration apart (plus a small delay/latency),
//    we set a timer for (minBufferDuration + 1) seconds to verify that no
//    data event was received. We pass the test on a timeout.
//
// - Mic Access was disabled, source wasn't suspended
// -- We fail the test
//
// - Mic Access was enabled, source was suspended
// -- We fail the test
//
// - Mic Access was enabled, source wasn't suspended
// -- We set a flag 'GotSourceEnabledEvent'. The audio data event checks this
// flag, and reports success/failure appropriately.

void Manager::handleAudioSourceStatusEvent(
    const struct chreAudioSourceStatusEvent *event) {
  LOGI("Received sampling status event suspended %d", event->status.suspended);
  mAudioSamplingEnabled = !event->status.suspended;
  if (!mTestSession.has_value()) {
    return;
  }

  bool success = false;
  if (mTestSession->featureState == FeatureState::ENABLED) {
    if (event->status.suspended) {
      if (gAudioStatusTimerHandle != CHRE_TIMER_INVALID) {
        chreTimerCancel(gAudioStatusTimerHandle);
        gAudioStatusTimerHandle = CHRE_TIMER_INVALID;
      }

      struct chreAudioSource source;
      if (chreAudioGetSource(kAudioHandle, &source)) {
        const uint64_t duration =
            source.minBufferDuration + kOneSecondInNanoseconds;
        gAudioDataTimerHandle =
            chreTimerSet(duration, &kAudioDataTimerCookie, true /* oneShot */);

        if (gAudioDataTimerHandle == CHRE_TIMER_INVALID) {
          LOGE("Failed to set data check timer");
        } else {
          success = true;
        }
      } else {
        LOGE("Failed to query audio source");
      }
    } else {
      // There might be a corner case where CHRE might have queued an audio
      // available event just as the microphone disable setting change is
      // received that might wrongfully indicate that microphone access
      // wasn't disabled when it is dispatched. We add a 2 second timer to
      // allow CHRE to send the source status change event to account for
      // this, and fail the test if the timer expires without getting said
      // event.
      LOGW("Source wasn't suspended when Mic Access disabled, waiting 2 sec");
      gAudioStatusTimerHandle =
          chreTimerSet(2 * kOneSecondInNanoseconds, &kAudioStatusTimerCookie,
                       true /* oneShot */);
      if (gAudioStatusTimerHandle == CHRE_TIMER_INVALID) {
        LOGE("Failed to set audio status check timer");
      } else {
        // continue the test, fail on timeout.
        success = true;
      }
    }
  } else {
    gGotSourceEnabledEvent = true;
    success = true;
  }

  if (!success) {
    sendTestResult(mTestSession->hostEndpointId, success);
  }
}

void Manager::handleAudioDataEvent(const struct chreAudioDataEvent *event) {
  UNUSED_VAR(event);

  bool success = false;
  if (mTestSession.has_value()) {
    if (mTestSession->featureState == FeatureState::ENABLED) {
      if (gAudioDataTimerHandle != CHRE_TIMER_INVALID) {
        chreTimerCancel(gAudioDataTimerHandle);
        gAudioDataTimerHandle = CHRE_TIMER_INVALID;
      }
    } else if (gGotSourceEnabledEvent) {
      success = true;
    }
    chreAudioConfigureSource(kAudioHandle, false /* enable */,
                             0 /* minBufferDuration */,
                             0 /* maxbufferDuration */);
    sendTestResult(mTestSession->hostEndpointId, success);
  }
}

void Manager::handleTimeout(const void *eventData) {
  bool testSuccess = false;
  auto *cookie = static_cast<const uint32_t *>(eventData);
  // Ignore the audio status timer if the suspended status was received.
  if (*cookie == kAudioStatusTimerCookie && !mAudioSamplingEnabled) {
    gAudioStatusTimerHandle = CHRE_TIMER_INVALID;
    return;
  }

  if (*cookie == kAudioDataTimerCookie) {
    gAudioDataTimerHandle = CHRE_TIMER_INVALID;
    testSuccess = true;
  } else if (*cookie == kAudioStatusTimerCookie) {
    gAudioStatusTimerHandle = CHRE_TIMER_INVALID;
    LOGE("Source wasn't suspended when Mic Access was disabled");
  } else {
    LOGE("Invalid timer cookie: %" PRIx32, *cookie);
  }
  chreAudioConfigureSource(0 /*handle*/, false /*enable*/,
                           0 /*minBufferDuration*/, 0 /*maxBufferDuration*/);
  sendTestResult(mTestSession->hostEndpointId, testSuccess);
}

void Manager::handleBleAsyncResult(const chreAsyncResult *result) {
  bool success = false;
  switch (result->requestType) {
    case CHRE_BLE_REQUEST_TYPE_START_SCAN: {
      if (mTestSession->feature != Feature::BLE_SCANNING) {
        LOGE("Unexpected BLE scan async result: test feature %" PRIu8,
             static_cast<uint8_t>(mTestSession->feature));
      } else {
        success = validateAsyncResult(result, nullptr);
      }
      break;
    }
    default:
      LOGE("Unexpected BLE request type %" PRIu8, result->requestType);
  }

  sendTestResult(mTestSession->hostEndpointId, success);
}

void Manager::sendTestResult(uint16_t hostEndpointId, bool success) {
  test_shared::sendTestResultToHost(
      hostEndpointId, chre_settings_test_MessageType_TEST_RESULT, success);
  mTestSession.reset();
  mCachedRangingTarget.reset();
}

}  // namespace settings_test

}  // namespace chre
