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

#include "chre_audio_concurrency_test_manager.h"

#include <pb_decode.h>

#include "audio_validation.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_audio_concurrency_test.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[ChreAudioConcurrencyTest]"

namespace chre {

using test_shared::checkAudioSamplesAllSame;
using test_shared::checkAudioSamplesAllZeros;
using test_shared::sendEmptyMessageToHost;
using test_shared::sendTestResultToHost;

namespace audio_concurrency_test {

namespace {

//! The message type to use with sendTestResultToHost()
constexpr uint32_t kTestResultMessageType =
    chre_audio_concurrency_test_MessageType_TEST_RESULT;

//! The maximum number of samples that can be missed before triggering a suspend
//! event. 50 samples at a sample rate of 44100 (typical) is approximately 1 ms
//! of audio gap.
constexpr uint32_t kMaxMissedSamples = 50;

bool isTestSupported() {
  // CHRE audio was supported in CHRE v1.2
  return chreGetVersion() >= CHRE_API_VERSION_1_2;
}

bool getTestStep(const chre_audio_concurrency_test_TestCommand &command,
                 Manager::TestStep *step) {
  bool success = true;
  switch (command.step) {
    case chre_audio_concurrency_test_TestCommand_Step_ENABLE_AUDIO: {
      *step = Manager::TestStep::ENABLE_AUDIO;
      break;
    }
    case chre_audio_concurrency_test_TestCommand_Step_VERIFY_AUDIO_RESUME: {
      *step = Manager::TestStep::VERIFY_AUDIO_RESUME;
      break;
    }
    case chre_audio_concurrency_test_TestCommand_Step_ENABLE_AUDIO_WITH_GAP_VERIFICATION: {
      *step = Manager::TestStep::ENABLE_AUDIO_WITH_GAP_VERIFICATION;
      break;
    }
    default: {
      LOGE("Unknown test step %d", command.step);
      success = false;
    }
  }

  return success;
}

}  // anonymous namespace

Manager::~Manager() {
  if (mAudioEnabled) {
    chreAudioConfigureSource(kAudioHandle, false /* enable */,
                             0 /* bufferDuration */, 0 /* deliveryInterval */);
  }
  cancelTimeoutTimer();
}

bool Manager::handleTestCommandMessage(uint16_t hostEndpointId, TestStep step) {
  // Treat as success if CHRE audio is unsupported
  // TODO: Use all available audio sources
  if (!isTestSupported() || !chreAudioGetSource(kAudioHandle, &mAudioSource)) {
    sendTestResultToHost(hostEndpointId, kTestResultMessageType,
                         true /* success */);
    return true;
  }

  bool success = false;
  switch (step) {
    case TestStep::ENABLE_AUDIO_WITH_GAP_VERIFICATION:
    case TestStep::ENABLE_AUDIO: {
      mVerifyAudioGaps = step == TestStep::ENABLE_AUDIO_WITH_GAP_VERIFICATION;

      if (!chreAudioConfigureSource(kAudioHandle, true /* enable */,
                                    mAudioSource.minBufferDuration,
                                    mAudioSource.minBufferDuration)) {
        LOGE("Failed to configure audio source");
      } else {
        mAudioEnabled = true;
        // Start a timer to ensure we receive the first audio data event
        // quickly. Since it may take some time to load the sound model, choose
        // a reasonably long timeout.
        success = setTimeoutTimer(20 /* durationSeconds */);
      }
      break;
    }
    case TestStep::VERIFY_AUDIO_RESUME: {
      success = setTimeoutTimer(20 /* durationSeconds */);
      break;
    }
    default: {
      break;
    }
  }

  if (success) {
    mTestSession = TestSession(hostEndpointId, step);
    LOGI("Starting test step %" PRIu8,
         static_cast<uint8_t>(mTestSession->step));
  }

  return success;
}

void Manager::handleMessageFromHost(uint32_t senderInstanceId,
                                    const chreMessageFromHostData *hostData) {
  bool success = false;
  uint32_t messageType = hostData->messageType;
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else if (messageType !=
             chre_audio_concurrency_test_MessageType_TEST_COMMAND) {
    LOGE("Invalid message type %" PRIu32, messageType);
  } else {
    pb_istream_t istream = pb_istream_from_buffer(
        static_cast<const pb_byte_t *>(hostData->message),
        hostData->messageSize);
    chre_audio_concurrency_test_TestCommand testCommand =
        chre_audio_concurrency_test_TestCommand_init_default;

    TestStep step;
    if (!pb_decode(&istream, chre_audio_concurrency_test_TestCommand_fields,
                   &testCommand)) {
      LOGE("Failed to decode start command error %s", PB_GET_ERROR(&istream));
    } else if (getTestStep(testCommand, &step)) {
      success = handleTestCommandMessage(hostData->hostEndpoint, step);
    }
  }

  if (!success) {
    sendTestResultToHost(hostData->hostEndpoint, kTestResultMessageType,
                         false /* success */);
  }
}

void Manager::handleDataFromChre(uint16_t eventType, const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_AUDIO_DATA:
      handleAudioDataEvent(static_cast<const chreAudioDataEvent *>(eventData));
      break;

    case CHRE_EVENT_TIMER:
      handleTimer();
      break;

    case CHRE_EVENT_AUDIO_SAMPLING_CHANGE:
      handleAudioSourceStatusEvent(
          static_cast<const chreAudioSourceStatusEvent *>(eventData));
      break;

    default:
      LOGE("Unexpected event type %" PRIu16, eventType);
  }
}

void Manager::handleTimer() {
  if (mTimerHandle != CHRE_TIMER_INVALID && mTestSession.has_value()) {
    LOGE("Timed out during test: step %" PRIu8,
         static_cast<uint8_t>(mTestSession->step));
    sendTestResultToHost(mTestSession->hostEndpointId, kTestResultMessageType,
                         false /* success */);
    mTestSession.reset();
  }
}

bool Manager::setTimeoutTimer(uint32_t durationSeconds) {
  mTimerHandle = chreTimerSet(durationSeconds * kOneSecondInNanoseconds,
                              nullptr /* cookie */, true /* oneShot */);
  if (mTimerHandle == CHRE_TIMER_INVALID) {
    LOGE("Failed to set timeout timer");
  }

  return mTimerHandle != CHRE_TIMER_INVALID;
}

void Manager::cancelTimeoutTimer() {
  if (mTimerHandle != CHRE_TIMER_INVALID) {
    chreTimerCancel(mTimerHandle);
    mTimerHandle = CHRE_TIMER_INVALID;
  }
}

bool Manager::validateAudioDataEvent(const chreAudioDataEvent *data) {
  bool success = false;
  if (data == nullptr) {
    LOGE("data is nullptr");
  } else if (data->format == CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW) {
    if (data->samplesULaw8 == nullptr) {
      LOGE("samplesULaw8 is nullptr");
    }
  } else if (data->format != CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM) {
    LOGE("Invalid format %" PRIu8, data->format);
  } else if (data->samplesS16 == nullptr) {
    LOGE("samplesS16 is nullptr");
  } else if (data->sampleCount == 0) {
    LOGE("The sample count is 0");
  } else if (data->sampleCount <
             static_cast<uint64_t>(mAudioSource.minBufferDuration *
                                   static_cast<double>(data->sampleRate) /
                                   kOneSecondInNanoseconds)) {
    LOGE("The sample count is less than the minimum number of samples");
  } else if (!checkAudioSamplesAllZeros(data->samplesS16, data->sampleCount)) {
    LOGE("Audio samples are all zero");
  } else if (!checkAudioSamplesAllSame(data->samplesS16, data->sampleCount)) {
    LOGE("Audio samples are all the same");
  } else {
    // Verify that timestamp increases.
    static uint64_t lastTimestamp = 0;
    bool timestampValid = data->timestamp > lastTimestamp;
    lastTimestamp = data->timestamp;

    // Verify the gap was properly announced.
    bool gapValidationValid = true;
    double sampleTimeInNs =
        kOneSecondInNanoseconds / static_cast<double>(data->sampleRate);
    if (mLastAudioBufferEndTimestampNs.has_value() &&
        data->timestamp > *mLastAudioBufferEndTimestampNs) {
      uint64_t gapNs = data->timestamp - *mLastAudioBufferEndTimestampNs;
      if (gapNs > kMaxMissedSamples * sampleTimeInNs &&
          !mSawSuspendAudioEvent) {
        LOGE(
            "Audio was suspended, but we did not receive a "
            "CHRE_EVENT_AUDIO_SAMPLING_CHANGE event.");
        LOGE("gap = %" PRIu64 " ns", gapNs);
        gapValidationValid = false;
      }
    }

    // Record last audio timestamp at end of buffer
    mLastAudioBufferEndTimestampNs =
        data->timestamp + data->sampleCount * sampleTimeInNs;

    success = timestampValid && (!mVerifyAudioGaps || gapValidationValid);
  }

  return success;
}

void Manager::handleAudioDataEvent(const chreAudioDataEvent *data) {
  if (!mTestSession.has_value()) {
    return;
  }

  if (!validateAudioDataEvent(data)) {
    sendTestResultToHost(mTestSession->hostEndpointId, kTestResultMessageType,
                         false /* success */);
    mTestSession.reset();
    return;
  }

  switch (mTestSession->step) {
    case TestStep::ENABLE_AUDIO_WITH_GAP_VERIFICATION:
    case TestStep::ENABLE_AUDIO: {
      cancelTimeoutTimer();
      sendEmptyMessageToHost(
          mTestSession->hostEndpointId,
          chre_audio_concurrency_test_MessageType_TEST_AUDIO_ENABLED);

      // Reset the test session to avoid sending multiple TEST_AUDIO_ENABLED
      // messages to the host, while we wait for the next step.
      mTestSession.reset();
      break;
    }

    case TestStep::VERIFY_AUDIO_RESUME: {
      cancelTimeoutTimer();
      sendTestResultToHost(mTestSession->hostEndpointId, kTestResultMessageType,
                           true /* success */);
      mTestSession.reset();
      break;
    }

    default:
      LOGE("Unexpected test step %" PRIu8,
           static_cast<uint8_t>(mTestSession->step));
      break;
  }
}

void Manager::handleAudioSourceStatusEvent(
    const chreAudioSourceStatusEvent *data) {
  if (data != nullptr) {
    LOGI("Audio source status event received");
    LOGI("Event: handle: %" PRIu32 ", enabled: %s, suspended: %s", data->handle,
         data->status.enabled ? "true" : "false",
         data->status.suspended ? "true" : "false");

    if (mTestSession.has_value() &&
        (mTestSession->step == TestStep::ENABLE_AUDIO ||
         mTestSession->step == TestStep::ENABLE_AUDIO_WITH_GAP_VERIFICATION ||
         mTestSession->step == TestStep::VERIFY_AUDIO_RESUME) &&
        data->handle == kAudioHandle && data->status.suspended) {
      mSawSuspendAudioEvent = true;
    }
  } else if (mTestSession.has_value()) {
    LOGE("Invalid data (data == nullptr)");
    sendTestResultToHost(mTestSession->hostEndpointId, kTestResultMessageType,
                         false /* success */);
    mTestSession.reset();
  }
}

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

}  // namespace audio_concurrency_test

}  // namespace chre
