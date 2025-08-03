/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre/platform/platform_audio.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/macros.h"
#include "chre_api/chre/audio.h"

namespace chre {

const chrePalAudioCallbacks PlatformAudioBase::sCallbacks = {
    .audioDataEventCallback = PlatformAudioBase::audioDataEventCallback,
    .audioAvailabilityCallback = PlatformAudioBase::audioAvailabilityCallback,
};

PlatformAudio::PlatformAudio() {}

PlatformAudio::~PlatformAudio() {
  if (mApi != nullptr) {
    LOGD("Platform audio closing");
    prePalApiCall(PalType::AUDIO);
    mApi->close();
    LOGD("Platform audio closed");
  }
}

void PlatformAudio::init() {
  prePalApiCall(PalType::AUDIO);
  mApi = chrePalAudioGetApi(CHRE_PAL_AUDIO_API_CURRENT_VERSION);
  if (mApi != nullptr) {
    if (!mApi->open(&gChrePalSystemApi, &sCallbacks)) {
      LOGE("Audio PAL open returned false");

#ifdef CHRE_TELEMETRY_SUPPORT_ENABLED
      EventLoopManagerSingleton::get()->getTelemetryManager().onPalOpenFailure(
          TelemetryManager::PalType::AUDIO);
#endif  // CHRE_TELEMETRY_SUPPORT_ENABLED

      mApi = nullptr;
    } else {
      LOGD("Opened audio PAL version 0x%08" PRIx32, mApi->moduleVersion);
    }
  } else {
    LOGW("Requested audio PAL (version 0x%08" PRIx32 ") not found",
         CHRE_PAL_AUDIO_API_CURRENT_VERSION);
  }
}

void PlatformAudio::setHandleEnabled(uint32_t handle, bool enabled) {
  UNUSED_VAR(handle);
  UNUSED_VAR(enabled);
}

bool PlatformAudio::requestAudioDataEvent(uint32_t handle, uint32_t numSamples,
                                          Nanoseconds eventDelay) {
  if (mApi != nullptr) {
    prePalApiCall(PalType::AUDIO);
    return mApi->requestAudioDataEvent(handle, numSamples,
                                       eventDelay.toRawNanoseconds());
  }

  return false;
}

void PlatformAudio::cancelAudioDataEventRequest(uint32_t handle) {
  if (mApi != nullptr) {
    prePalApiCall(PalType::AUDIO);
    mApi->cancelAudioDataEvent(handle);
  }
}

void PlatformAudio::releaseAudioDataEvent(struct chreAudioDataEvent *event) {
  if (mApi != nullptr) {
    prePalApiCall(PalType::AUDIO);
    mApi->releaseAudioDataEvent(event);
  }
}

size_t PlatformAudio::getSourceCount() {
  if (mApi != nullptr) {
    prePalApiCall(PalType::AUDIO);
    return static_cast<size_t>(mApi->getSourceCount());
  }

  return 0;
}

bool PlatformAudio::getAudioSource(uint32_t handle,
                                   chreAudioSource *audioSource) const {
  if (mApi != nullptr) {
    prePalApiCall(PalType::AUDIO);
    return mApi->getAudioSource(handle, audioSource);
  }

  return false;
}

void PlatformAudioBase::audioDataEventCallback(
    struct chreAudioDataEvent *event) {
  EventLoopManagerSingleton::get()
      ->getAudioRequestManager()
      .handleAudioDataEvent(event);
}

void PlatformAudioBase::audioAvailabilityCallback(uint32_t handle,
                                                  bool available) {
  EventLoopManagerSingleton::get()
      ->getAudioRequestManager()
      .handleAudioAvailability(handle, available);
}

}  // namespace chre