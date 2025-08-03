/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <general_test/basic_gnss_test.h>

#include <shared/send_message.h>

#include "chre_api/chre.h"

/*
 * Test to check expected functionality of the CHRE GNSS APIs.
 */
namespace general_test {

namespace {

using nanoapp_testing::sendFatalFailureToHost;

void testLocationSessionAsync() {
  if (!chreGnssLocationSessionStartAsync(1000 /* minIntervalMs */,
                                         0 /* minTimeToNextFixMs */,
                                         nullptr /* cookie */)) {
    sendFatalFailureToHost("Failed to start a location session");
  }
}

void testMeasurementSessionAsync() {
  if (!chreGnssMeasurementSessionStartAsync(1000 /* minIntervalMs */,
                                            nullptr /* cookie */)) {
    sendFatalFailureToHost("Failed to start a measurement session");
  }
}

bool testPassiveListener() {
  bool success = false;
  if (!chreGnssConfigurePassiveLocationListener(true /* enable */)) {
    sendFatalFailureToHost("Failed to enable passive location listener");
  } else if (!chreGnssConfigurePassiveLocationListener(false /* enable */)) {
    sendFatalFailureToHost("Failed to disable passive location listener");
  } else {
    success = true;
  }

  return success;
}

}  // anonymous namespace

BasicGnssTest::BasicGnssTest() : Test(CHRE_API_VERSION_1_1) {}

void BasicGnssTest::setUp(uint32_t messageSize, const void * /* message */) {
  if (messageSize != 0) {
    sendFatalFailureToHost("Expected 0 byte message, got more bytes:",
                           &messageSize);
  } else {
    if (isCapabilitySet(CHRE_GNSS_CAPABILITIES_LOCATION)) {
      testLocationSessionAsync();
    } else {
      mTestSuccessMarker.markStageAndSuccessOnFinish(
          BASIC_GNSS_TEST_STAGE_LOCATION);
    }

    if (isCapabilitySet(CHRE_GNSS_CAPABILITIES_MEASUREMENTS)) {
      testMeasurementSessionAsync();
    } else {
      mTestSuccessMarker.markStageAndSuccessOnFinish(
          BASIC_GNSS_TEST_STAGE_MEASUREMENT);
    }

    if ((mApiVersion < CHRE_API_VERSION_1_5) ||
        !isCapabilitySet(
            CHRE_GNSS_CAPABILITIES_GNSS_ENGINE_BASED_PASSIVE_LISTENER) ||
        !isCapabilitySet(CHRE_GNSS_CAPABILITIES_LOCATION) ||
        testPassiveListener()) {
      mTestSuccessMarker.markStageAndSuccessOnFinish(
          BASIC_GNSS_TEST_STAGE_LISTENER);
    }
  }
}

void BasicGnssTest::handleGnssAsyncResult(const chreAsyncResult *result) {
  if (!result->success) {
    sendFatalFailureToHost("Received unsuccessful GNSS async result");
  }

  switch (result->requestType) {
    case CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_START: {
      if (!chreGnssLocationSessionStopAsync(nullptr /* cookie */)) {
        sendFatalFailureToHost("Failed to stop a location session");
      }
      break;
    }
    case CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_STOP: {
      mTestSuccessMarker.markStageAndSuccessOnFinish(
          BASIC_GNSS_TEST_STAGE_LOCATION);
      break;
    }
    case CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_START: {
      if (!chreGnssMeasurementSessionStopAsync(nullptr /* cookie */)) {
        sendFatalFailureToHost("Failed to stop a measurement session");
      }
      break;
    }
    case CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_STOP: {
      mTestSuccessMarker.markStageAndSuccessOnFinish(
          BASIC_GNSS_TEST_STAGE_MEASUREMENT);
      break;
    }
    default:
      sendFatalFailureToHost("Unexpected request type");
      break;
  }
}

void BasicGnssTest::handleEvent(uint32_t /* senderInstanceId */,
                                uint16_t eventType, const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_GNSS_ASYNC_RESULT:
      handleGnssAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;
    case CHRE_EVENT_GNSS_LOCATION:
    case CHRE_EVENT_GNSS_DATA:
      // Do nothing
      break;
    default:
      unexpectedEvent(eventType);
      break;
  }
}

}  // namespace general_test
