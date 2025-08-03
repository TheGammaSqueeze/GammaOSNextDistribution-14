/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef LOG_TAG
#define LOG_TAG "CHRE.HAL.CLIENT"
#endif

#include "chre_host/hal_client.h"
#include "chre_host/log.h"

#include <android-base/properties.h>
#include <utils/SystemClock.h>

#include <cinttypes>
#include <thread>

namespace android::chre {

using ::aidl::android::hardware::contexthub::IContextHub;
using ::aidl::android::hardware::contexthub::IContextHubCallback;
using ::android::base::GetBoolProperty;
using ::ndk::ScopedAStatus;

namespace {
constexpr char kHalEnabledProperty[]{"vendor.chre.multiclient_hal.enabled"};
}  // namespace

bool HalClient::isServiceAvailable() {
  return GetBoolProperty(kHalEnabledProperty, /* default_value= */ false);
}

std::unique_ptr<HalClient> HalClient::create(
    const std::shared_ptr<IContextHubCallback> &callback,
    int32_t contextHubId) {
  if (callback == nullptr) {
    LOGE("Callback function must not be null");
    return nullptr;
  }

  if (!isServiceAvailable()) {
    LOGE("CHRE Multiclient HAL is not enabled on this device");
    return nullptr;
  }

  auto halClient =
      std::unique_ptr<HalClient>(new HalClient(callback, contextHubId));
  HalError result = halClient->initConnection();
  if (result != HalError::SUCCESS) {
    LOGE("Failed to create a hal client. Error code: %" PRIi32, result);
    return nullptr;
  }
  return halClient;
}

HalError HalClient::initConnection() {
  std::lock_guard<std::shared_mutex> lockGuard{mConnectionLock};

  if (mContextHub != nullptr) {
    LOGW("Context hub is already connected");
    return HalError::SUCCESS;
  }

  // Wait to connect to the service. Note that we don't do local retries
  // because we're relying on the internal retries in
  // AServiceManager_waitForService(). If HAL service has just restarted, it
  // can take a few seconds to connect.
  ndk::SpAIBinder binder{
      AServiceManager_waitForService(kAidlServiceName.c_str())};
  if (binder.get() == nullptr) {
    return HalError::BINDER_CONNECTION_FAILED;
  }

  // Link the death recipient to handle the binder disconnection event.
  if (AIBinder_linkToDeath(binder.get(), mDeathRecipient.get(), this) !=
      STATUS_OK) {
    LOGE("Failed to link the binder death recipient");
    return HalError::LINK_DEATH_RECIPIENT_FAILED;
  }

  // Retrieve a handle of context hub service.
  mContextHub = IContextHub::fromBinder(binder);
  if (mContextHub == nullptr) {
    LOGE("Got null context hub from the binder connection");
    return HalError::NULL_CONTEXT_HUB_FROM_BINDER;
  }

  // Register an IContextHubCallback.
  ScopedAStatus status =
      mContextHub->registerCallback(kDefaultContextHubId, mCallback);
  if (!status.isOk()) {
    LOGE("Unable to register callback: %s", status.getDescription().c_str());
    return HalError::CALLBACK_REGISTRATION_FAILED;
  }
  LOGI("Successfully connected to HAL");
  return HalError::SUCCESS;
}

void HalClient::onHalDisconnected(void *cookie) {
  LOGW("CHRE HAL is disconnected. Reconnecting...");
  int64_t startTime = ::android::elapsedRealtime();
  auto *halClient = static_cast<HalClient *>(cookie);
  {
    std::lock_guard<std::shared_mutex> lock(halClient->mConnectionLock);
    halClient->mContextHub = nullptr;
  }

  HalError result = halClient->initConnection();
  uint64_t duration = ::android::elapsedRealtime() - startTime;
  if (result != HalError::SUCCESS) {
    LOGE("Failed to fully reconnect to HAL after %" PRIu64
         "ms, HalErrorCode: %" PRIi32,
         duration, result);
    return;
  }
  tryReconnectEndpoints(halClient);
  LOGI("Reconnected to HAL after %" PRIu64 "ms", duration);
}

ScopedAStatus HalClient::connectEndpoint(
    const HostEndpointInfo &hostEndpointInfo) {
  HostEndpointId endpointId = hostEndpointInfo.hostEndpointId;
  if (isEndpointConnected(endpointId)) {
    // Connecting the endpoint again even though it is already connected to let
    // HAL and/or CHRE be the single place to control the behavior.
    LOGW("Endpoint id %" PRIu16 " is already connected", endpointId);
  }
  ScopedAStatus result = callIfConnected(
      [&]() { return mContextHub->onHostEndpointConnected(hostEndpointInfo); });
  if (result.isOk()) {
    insertConnectedEndpoint(hostEndpointInfo);
  } else {
    LOGE("Failed to connect the endpoint id %" PRIu16,
         hostEndpointInfo.hostEndpointId);
  }
  return result;
}

ScopedAStatus HalClient::disconnectEndpoint(HostEndpointId hostEndpointId) {
  if (!isEndpointConnected(hostEndpointId)) {
    // Disconnecting the endpoint again even though it is already disconnected
    // to let HAL and/or CHRE be the single place to control the behavior.
    LOGW("Endpoint id %" PRIu16 " is already disconnected", hostEndpointId);
  }
  ScopedAStatus result = callIfConnected([&]() {
    return mContextHub->onHostEndpointDisconnected(hostEndpointId);
  });
  if (result.isOk()) {
    removeConnectedEndpoint(hostEndpointId);
  } else {
    LOGE("Failed to disconnect the endpoint id %" PRIu16, hostEndpointId);
  }
  return result;
}

ScopedAStatus HalClient::sendMessage(const ContextHubMessage &message) {
  uint16_t hostEndpointId = message.hostEndPoint;
  if (!isEndpointConnected(hostEndpointId)) {
    // This is still allowed now but in the future an error will be returned.
    LOGW("Endpoint id %" PRIu16
         " is unknown or disconnected. Message sending will be skipped in the "
         "future");
  }
  return callIfConnected(
      [&]() { return mContextHub->sendMessageToHub(mContextHubId, message); });
}

void HalClient::tryReconnectEndpoints(HalClient *halClient) {
  LOGW("CHRE has restarted. Reconnecting endpoints");
  std::lock_guard<std::shared_mutex> lock(halClient->mConnectedEndpointsLock);
  for (const auto &[endpointId, endpointInfo] :
       halClient->mConnectedEndpoints) {
    if (!halClient
             ->callIfConnected([&]() {
               return halClient->mContextHub->onHostEndpointConnected(
                   endpointInfo);
             })
             .isOk()) {
      LOGE("Failed to set up the connected state for endpoint %" PRIu16
           " after HAL restarts.",
           endpointId);
      halClient->mConnectedEndpoints.erase(endpointId);
    } else {
      LOGI("Reconnected endpoint %" PRIu16 " to CHRE HAL", endpointId);
    }
  }
}
}  // namespace android::chre