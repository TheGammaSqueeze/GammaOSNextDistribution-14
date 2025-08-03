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

#include "chre/core/event_loop_manager.h"
#include "chre/platform/shared/host_protocol_chre.h"
#include "chre/platform/shared/nanoapp_load_manager.h"

namespace chre {

NanoappLoadManager gLoadManager;

inline NanoappLoadManager &getLoadManager() {
  return gLoadManager;
}

void HostMessageHandlers::handleDebugConfiguration(
    const fbs::DebugConfiguration *debugConfiguration) {
  EventLoopManagerSingleton::get()
      ->getSystemHealthMonitor()
      .setFatalErrorOnCheckFailure(
          debugConfiguration->health_monitor_failure_crash());
}

void HostMessageHandlers::finishLoadingNanoappCallback(
    SystemCallbackType /*type*/, UniquePtr<LoadNanoappCallbackData> &&cbData) {
  constexpr size_t kInitialBufferSize = 48;
  ChreFlatBufferBuilder builder(kInitialBufferSize);

  CHRE_ASSERT(cbData != nullptr);

  EventLoop &eventLoop = EventLoopManagerSingleton::get()->getEventLoop();
  bool success = false;

  if (cbData->nanoapp->isLoaded()) {
    success = eventLoop.startNanoapp(cbData->nanoapp);
  } else {
    LOGE("Nanoapp is not loaded");
  }

  if (cbData->sendFragmentResponse) {
    sendFragmentResponse(cbData->hostClientId, cbData->transactionId,
                         cbData->fragmentId, success);
  }
}

void HostMessageHandlers::loadNanoappData(
    uint16_t hostClientId, uint32_t transactionId, uint64_t appId,
    uint32_t appVersion, uint32_t appFlags, uint32_t targetApiVersion,
    const void *buffer, size_t bufferLen, uint32_t fragmentId,
    size_t appBinaryLen, bool respondBeforeStart) {
  bool success = true;

  if (fragmentId == 0 || fragmentId == 1) {
    size_t totalAppBinaryLen = (fragmentId == 0) ? bufferLen : appBinaryLen;
    LOGD("Load nanoapp request for app ID 0x%016" PRIx64 " ver 0x%" PRIx32
         " flags 0x%" PRIx32 " target API 0x%08" PRIx32
         " size %zu (txnId %" PRIu32 " client %" PRIu16 ")",
         appId, appVersion, appFlags, targetApiVersion, totalAppBinaryLen,
         transactionId, hostClientId);

    if (getLoadManager().hasPendingLoadTransaction()) {
      FragmentedLoadInfo info = getLoadManager().getTransactionInfo();
      sendFragmentResponse(info.hostClientId, info.transactionId,
                           0 /* fragmentId */, false /* success */);
      getLoadManager().markFailure();
    }

    success = getLoadManager().prepareForLoad(
        hostClientId, transactionId, appId, appVersion, appFlags,
        totalAppBinaryLen, targetApiVersion);
  }

  if (success) {
    success = getLoadManager().copyNanoappFragment(
        hostClientId, transactionId, (fragmentId == 0) ? 1 : fragmentId, buffer,
        bufferLen);
  } else {
    LOGE("Failed to prepare for load");
  }

  if (getLoadManager().isLoadComplete()) {
    LOGD("Load manager load complete...");
    auto cbData = MakeUnique<LoadNanoappCallbackData>();
    if (cbData.isNull()) {
      LOG_OOM();
    } else {
      cbData->transactionId = transactionId;
      cbData->hostClientId = hostClientId;
      cbData->appId = appId;
      cbData->fragmentId = fragmentId;
      cbData->nanoapp = getLoadManager().releaseNanoapp();
      cbData->sendFragmentResponse = !respondBeforeStart;

      LOGD("Instance ID %" PRIu16 " assigned to app ID 0x%" PRIx64,
           cbData->nanoapp->getInstanceId(), appId);

      // Note that if this fails, we'll generate the error response in
      // the normal deferred callback
      EventLoopManagerSingleton::get()->deferCallback(
          SystemCallbackType::FinishLoadingNanoapp, std::move(cbData),
          finishLoadingNanoappCallback);
      if (respondBeforeStart) {
        sendFragmentResponse(hostClientId, transactionId, fragmentId, success);
      }  // else the response will be sent in finishLoadingNanoappCallback
    }
  } else {
    // send a response for this fragment
    sendFragmentResponse(hostClientId, transactionId, fragmentId, success);
  }
}

}  // namespace chre
