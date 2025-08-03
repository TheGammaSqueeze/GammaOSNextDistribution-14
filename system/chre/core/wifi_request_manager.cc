/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "chre/core/wifi_request_manager.h"

#include <cinttypes>
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/core/system_health_monitor.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/platform/system_time.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/event_callbacks.h"
#include "chre_api/chre/version.h"
#include "include/chre/core/event_loop_common.h"
#include "include/chre/core/wifi_request_manager.h"

// The default timeout values can be overwritten to lower the runtime
// for tests. Timeout values cannot be overwritten with a bigger value.
#ifdef CHRE_TEST_ASYNC_RESULT_TIMEOUT_NS
static_assert(CHRE_TEST_ASYNC_RESULT_TIMEOUT_NS <=
              CHRE_ASYNC_RESULT_TIMEOUT_NS);
#undef CHRE_ASYNC_RESULT_TIMEOUT_NS
#define CHRE_ASYNC_RESULT_TIMEOUT_NS CHRE_TEST_ASYNC_RESULT_TIMEOUT_NS
#endif

#ifdef CHRE_TEST_WIFI_RANGING_RESULT_TIMEOUT_NS
static_assert(CHRE_TEST_WIFI_RANGING_RESULT_TIMEOUT_NS <=
              CHRE_WIFI_RANGING_RESULT_TIMEOUT_NS);
#undef CHRE_WIFI_RANGING_RESULT_TIMEOUT_NS
#define CHRE_WIFI_RANGING_RESULT_TIMEOUT_NS \
  CHRE_TEST_WIFI_RANGING_RESULT_TIMEOUT_NS
#endif

#ifdef CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS
static_assert(CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS <=
              CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS);
#undef CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS
#define CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS
#endif

namespace chre {

WifiRequestManager::WifiRequestManager() {
  // Reserve space for at least one scan monitoring nanoapp. This ensures that
  // the first asynchronous push_back will succeed. Future push_backs will be
  // synchronous and failures will be returned to the client.
  if (!mScanMonitorNanoapps.reserve(1)) {
    FATAL_ERROR_OOM();
  }
}

void WifiRequestManager::init() {
  mPlatformWifi.init();
}

uint32_t WifiRequestManager::getCapabilities() {
  return mPlatformWifi.getCapabilities();
}

void WifiRequestManager::dispatchQueuedConfigureScanMonitorRequests() {
  while (!mPendingScanMonitorRequests.empty()) {
    const auto &stateTransition = mPendingScanMonitorRequests.front();
    bool hasScanMonitorRequest =
        nanoappHasScanMonitorRequest(stateTransition.nanoappInstanceId);
    if (scanMonitorIsInRequestedState(stateTransition.enable,
                                      hasScanMonitorRequest)) {
      // We are already in the target state so just post an event indicating
      // success
      postScanMonitorAsyncResultEventFatal(
          stateTransition.nanoappInstanceId, true /* success */,
          stateTransition.enable, CHRE_ERROR_NONE, stateTransition.cookie);
    } else if (scanMonitorStateTransitionIsRequired(stateTransition.enable,
                                                    hasScanMonitorRequest)) {
      if (!mPlatformWifi.configureScanMonitor(stateTransition.enable)) {
        postScanMonitorAsyncResultEventFatal(
            stateTransition.nanoappInstanceId, false /* success */,
            stateTransition.enable, CHRE_ERROR, stateTransition.cookie);
      } else {
        mConfigureScanMonitorTimeoutHandle = setConfigureScanMonitorTimer();
        break;
      }
    } else {
      CHRE_ASSERT_LOG(false, "Invalid scan monitor state");
    }
    mPendingScanMonitorRequests.pop();
  }
}

void WifiRequestManager::handleConfigureScanMonitorTimeout() {
  if (mPendingScanMonitorRequests.empty()) {
    LOGE("Configure Scan Monitor timer timedout with no pending request.");
  } else {
    EventLoopManagerSingleton::get()->getSystemHealthMonitor().onFailure(
        HealthCheckId::WifiConfigureScanMonitorTimeout);
    mPendingScanMonitorRequests.pop();

    dispatchQueuedConfigureScanMonitorRequests();
  }
}

TimerHandle WifiRequestManager::setConfigureScanMonitorTimer() {
  auto callback = [](uint16_t /*type*/, void * /*data*/, void * /*extraData*/) {
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleConfigureScanMonitorTimeout();
  };

  return EventLoopManagerSingleton::get()->setDelayedCallback(
      SystemCallbackType::RequestTimeoutEvent, nullptr, callback,
      Nanoseconds(CHRE_ASYNC_RESULT_TIMEOUT_NS));
}

bool WifiRequestManager::configureScanMonitor(Nanoapp *nanoapp, bool enable,
                                              const void *cookie) {
  CHRE_ASSERT(nanoapp);

  bool success = false;
  uint16_t instanceId = nanoapp->getInstanceId();
  bool hasScanMonitorRequest = nanoappHasScanMonitorRequest(instanceId);
  if (!mPendingScanMonitorRequests.empty()) {
    success = addScanMonitorRequestToQueue(nanoapp, enable, cookie);
  } else if (scanMonitorIsInRequestedState(enable, hasScanMonitorRequest)) {
    // The scan monitor is already in the requested state. A success event can
    // be posted immediately.
    success = postScanMonitorAsyncResultEvent(instanceId, true /* success */,
                                              enable, CHRE_ERROR_NONE, cookie);
  } else if (scanMonitorStateTransitionIsRequired(enable,
                                                  hasScanMonitorRequest)) {
    success = addScanMonitorRequestToQueue(nanoapp, enable, cookie);
    if (success) {
      success = mPlatformWifi.configureScanMonitor(enable);
      if (!success) {
        mPendingScanMonitorRequests.pop_back();
        LOGE("Failed to enable the scan monitor for nanoapp instance %" PRIu16,
             instanceId);
      } else {
        mConfigureScanMonitorTimeoutHandle = setConfigureScanMonitorTimer();
      }
    }
  } else {
    CHRE_ASSERT_LOG(false, "Invalid scan monitor configuration");
  }

  return success;
}

uint32_t WifiRequestManager::disableAllSubscriptions(Nanoapp *nanoapp) {
  uint32_t numSubscriptionsDisabled = 0;

  // Disable active scan monitoring.
  if (nanoappHasScanMonitorRequest(nanoapp->getInstanceId()) ||
      nanoappHasPendingScanMonitorRequest(nanoapp->getInstanceId())) {
    numSubscriptionsDisabled++;
    configureScanMonitor(nanoapp, false /*enabled*/, nullptr /*cookie*/);
  }

  // Disable active NAN subscriptions.
  for (size_t i = 0; i < mNanoappSubscriptions.size(); ++i) {
    if (mNanoappSubscriptions[i].nanoappInstanceId ==
        nanoapp->getInstanceId()) {
      numSubscriptionsDisabled++;
      nanSubscribeCancel(nanoapp, mNanoappSubscriptions[i].subscriptionId);
    }
  }

  return numSubscriptionsDisabled;
}

bool WifiRequestManager::requestRangingByType(RangingType type,
                                              const void *rangingParams) {
  bool success = false;
  if (type == RangingType::WIFI_AP) {
    auto *params =
        static_cast<const struct chreWifiRangingParams *>(rangingParams);
    success = mPlatformWifi.requestRanging(params);
  } else {
    auto *params =
        static_cast<const struct chreWifiNanRangingParams *>(rangingParams);
    success = mPlatformWifi.requestNanRanging(params);
  }
  if (success) {
    mRequestRangingTimeoutHandle = setRangingRequestTimer();
  }
  return success;
}

bool WifiRequestManager::updateRangingRequest(RangingType type,
                                              PendingRangingRequest &request,
                                              const void *rangingParams) {
  bool success = false;
  if (type == RangingType::WIFI_AP) {
    auto *params =
        static_cast<const struct chreWifiRangingParams *>(rangingParams);
    success = request.targetList.copy_array(params->targetList,
                                            params->targetListLen);
  } else {
    auto *params =
        static_cast<const struct chreWifiNanRangingParams *>(rangingParams);
    std::memcpy(request.nanRangingParams.macAddress, params->macAddress,
                CHRE_WIFI_BSSID_LEN);
    success = true;
  }
  return success;
}

bool WifiRequestManager::sendRangingRequest(PendingRangingRequest &request) {
  bool success = false;

  if (request.type == RangingType::WIFI_AP) {
    struct chreWifiRangingParams params = {};
    params.targetListLen = static_cast<uint8_t>(request.targetList.size());
    params.targetList = request.targetList.data();
    success = mPlatformWifi.requestRanging(&params);
  } else {
    struct chreWifiNanRangingParams params;
    std::memcpy(params.macAddress, request.nanRangingParams.macAddress,
                CHRE_WIFI_BSSID_LEN);
    success = mPlatformWifi.requestNanRanging(&params);
  }
  if (success) {
    mRequestRangingTimeoutHandle = setRangingRequestTimer();
  }
  return success;
}

void WifiRequestManager::handleRangingRequestTimeout() {
  if (mPendingRangingRequests.empty()) {
    LOGE("Request ranging timer timedout with no pending request.");
  } else {
    EventLoopManagerSingleton::get()->getSystemHealthMonitor().onFailure(
        HealthCheckId::WifiRequestRangingTimeout);
    mPendingRangingRequests.pop();
    while (!mPendingRangingRequests.empty() && !dispatchQueuedRangingRequest())
      ;
  }
}

TimerHandle WifiRequestManager::setRangingRequestTimer() {
  auto callback = [](uint16_t /*type*/, void * /*data*/, void * /*extraData*/) {
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleRangingRequestTimeout();
  };

  return EventLoopManagerSingleton::get()->setDelayedCallback(
      SystemCallbackType::RequestTimeoutEvent, nullptr, callback,
      Nanoseconds(CHRE_WIFI_RANGING_RESULT_TIMEOUT_NS));
}

bool WifiRequestManager::requestRanging(RangingType rangingType,
                                        Nanoapp *nanoapp,
                                        const void *rangingParams,
                                        const void *cookie) {
  CHRE_ASSERT(nanoapp);
  CHRE_ASSERT(rangingParams);

  bool success = false;
  if (!mPendingRangingRequests.emplace()) {
    LOGE("Can't issue new RTT request; pending queue full");
  } else {
    PendingRangingRequest &req = mPendingRangingRequests.back();
    req.nanoappInstanceId = nanoapp->getInstanceId();
    req.cookie = cookie;
    if (mPendingRangingRequests.size() == 1) {
      // First in line; dispatch request immediately
      if (!areRequiredSettingsEnabled()) {
        // Treat as success but post async failure per API.
        success = true;
        postRangingAsyncResult(CHRE_ERROR_FUNCTION_DISABLED);
        mPendingRangingRequests.pop_back();
      } else if (!requestRangingByType(rangingType, rangingParams)) {
        LOGE("WiFi ranging request of type %d failed",
             static_cast<int>(rangingType));
        mPendingRangingRequests.pop_back();
      } else {
        success = true;
      }
    } else {
      success = updateRangingRequest(rangingType, req, rangingParams);
      if (!success) {
        LOG_OOM();
        mPendingRangingRequests.pop_back();
      }
    }
  }
  return success;
}

void WifiRequestManager::handleScanRequestTimeout() {
  mScanRequestTimeoutHandle = CHRE_TIMER_INVALID;
  if (mPendingScanRequests.empty()) {
    LOGE("Scan Request timer timedout with no pending request.");
  } else {
    EventLoopManagerSingleton::get()->getSystemHealthMonitor().onFailure(
        HealthCheckId::WifiScanResponseTimeout);
    mPendingScanRequests.pop();
    dispatchQueuedScanRequests(true /* postAsyncResult */);
  }
}

TimerHandle WifiRequestManager::setScanRequestTimer() {
  CHRE_ASSERT(mScanRequestTimeoutHandle == CHRE_TIMER_INVALID);

  auto callback = [](uint16_t /*type*/, void * /*data*/, void * /*extraData*/) {
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleScanRequestTimeout();
  };

  return EventLoopManagerSingleton::get()->setDelayedCallback(
      SystemCallbackType::RequestTimeoutEvent, nullptr, callback,
      Nanoseconds(CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS));
}

bool WifiRequestManager::nanoappHasPendingScanRequest(
    uint16_t instanceId) const {
  for (const auto &scanRequest : mPendingScanRequests) {
    if (scanRequest.nanoappInstanceId == instanceId) {
      return true;
    }
  }
  return false;
}

bool WifiRequestManager::requestScan(Nanoapp *nanoapp,
                                     const struct chreWifiScanParams *params,
                                     const void *cookie) {
  CHRE_ASSERT(nanoapp);

  // Handle compatibility with nanoapps compiled against API v1.1, which doesn't
  // include the radioChainPref parameter in chreWifiScanParams
  struct chreWifiScanParams paramsCompat;
  if (nanoapp->getTargetApiVersion() < CHRE_API_VERSION_1_2) {
    memcpy(&paramsCompat, params, offsetof(chreWifiScanParams, radioChainPref));
    paramsCompat.radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT;
    params = &paramsCompat;
  }

  bool success = false;
  uint16_t nanoappInstanceId = nanoapp->getInstanceId();
  if (nanoappHasPendingScanRequest(nanoappInstanceId)) {
    LOGE("Can't issue new scan request: nanoapp: %" PRIx64
         " already has a pending request",
         nanoapp->getAppId());
  } else if (!mPendingScanRequests.emplace(nanoappInstanceId, cookie, params)) {
    LOG_OOM();
  } else if (!EventLoopManagerSingleton::get()
                  ->getSettingManager()
                  .getSettingEnabled(Setting::WIFI_AVAILABLE)) {
    // Treat as success, but send an async failure per API contract.
    success = true;
    handleScanResponse(false /* pending */, CHRE_ERROR_FUNCTION_DISABLED);
  } else {
    if (mPendingScanRequests.size() == 1) {
      success = dispatchQueuedScanRequests(false /* postAsyncResult */);
    } else {
      success = true;
    }
  }

  return success;
}

void WifiRequestManager::handleScanMonitorStateChange(bool enabled,
                                                      uint8_t errorCode) {
  EventLoopManagerSingleton::get()->cancelDelayedCallback(
      mConfigureScanMonitorTimeoutHandle);
  struct CallbackState {
    bool enabled;
    uint8_t errorCode;
  };

  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    CallbackState cbState = NestedDataPtr<CallbackState>(data);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleScanMonitorStateChangeSync(cbState.enabled, cbState.errorCode);
  };

  CallbackState cbState = {};
  cbState.enabled = enabled;
  cbState.errorCode = errorCode;
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiScanMonitorStateChange,
      NestedDataPtr<CallbackState>(cbState), callback);
}

void WifiRequestManager::handleScanResponse(bool pending, uint8_t errorCode) {
  struct CallbackState {
    bool pending;
    uint8_t errorCode;
  };

  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    CallbackState cbState = NestedDataPtr<CallbackState>(data);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleScanResponseSync(cbState.pending, cbState.errorCode);
  };

  CallbackState cbState = {};
  cbState.pending = pending;
  cbState.errorCode = errorCode;
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiRequestScanResponse,
      NestedDataPtr<CallbackState>(cbState), callback);
}

void WifiRequestManager::handleRangingEvent(
    uint8_t errorCode, struct chreWifiRangingEvent *event) {
  EventLoopManagerSingleton::get()->cancelDelayedCallback(
      mRequestRangingTimeoutHandle);
  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    uint8_t cbErrorCode = NestedDataPtr<uint8_t>(extraData);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleRangingEventSync(
            cbErrorCode, static_cast<struct chreWifiRangingEvent *>(data));
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiHandleRangingEvent, event, callback,
      NestedDataPtr<uint8_t>(errorCode));
}

void WifiRequestManager::handleScanEvent(struct chreWifiScanEvent *event) {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    auto *scanEvent = static_cast<struct chreWifiScanEvent *>(data);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .postScanEventFatal(scanEvent);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiHandleScanEvent, event, callback);
}

void WifiRequestManager::handleNanServiceIdentifierEventSync(
    uint8_t errorCode, uint32_t subscriptionId) {
  if (!mPendingNanSubscribeRequests.empty()) {
    auto &req = mPendingNanSubscribeRequests.front();
    chreWifiNanIdentifierEvent *event =
        memoryAlloc<chreWifiNanIdentifierEvent>();

    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->id = subscriptionId;
      event->result.requestType = CHRE_WIFI_REQUEST_TYPE_NAN_SUBSCRIBE;
      event->result.success = (errorCode == CHRE_ERROR_NONE);
      event->result.errorCode = errorCode;
      event->result.cookie = req.cookie;

      if (errorCode == CHRE_ERROR_NONE) {
        // It is assumed that the NAN discovery engine guarantees a unique ID
        // for each subscription - avoid redundant checks on uniqueness here.
        if (!mNanoappSubscriptions.push_back(NanoappNanSubscriptions(
                req.nanoappInstanceId, subscriptionId))) {
          LOG_OOM();
          // Even though the subscription request was able to successfully
          // obtain an ID, CHRE ran out of memory and couldn't store the
          // instance ID - subscription ID pair. Indicate this in the event
          // result.
          // TODO(b/204226580): Cancel the subscription if we run out of
          // memory.
          event->result.errorCode = CHRE_ERROR_NO_MEMORY;
        }
      }

      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event, freeEventDataCallback,
          req.nanoappInstanceId);
    }

    mPendingNanSubscribeRequests.pop();
    dispatchQueuedNanSubscribeRequestWithRetry();
  } else {
    LOGE("Received a NAN identifier event with no pending request!");
  }
}

void WifiRequestManager::handleNanServiceIdentifierEvent(
    uint8_t errorCode, uint32_t subscriptionId) {
  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    uint8_t errorCode = NestedDataPtr<uint8_t>(data);
    uint32_t subscriptionId = NestedDataPtr<uint32_t>(extraData);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanServiceIdentifierEventSync(errorCode, subscriptionId);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanServiceIdEvent,
      NestedDataPtr<uint8_t>(errorCode), callback,
      NestedDataPtr<uint32_t>(subscriptionId));
}

bool WifiRequestManager::getNappIdFromSubscriptionId(
    uint32_t subscriptionId, uint16_t *nanoappInstanceId) {
  bool success = false;
  for (auto &sub : mNanoappSubscriptions) {
    if (sub.subscriptionId == subscriptionId) {
      *nanoappInstanceId = sub.nanoappInstanceId;
      success = true;
      break;
    }
  }
  return success;
}

void WifiRequestManager::handleNanServiceDiscoveryEventSync(
    struct chreWifiNanDiscoveryEvent *event) {
  CHRE_ASSERT(event != nullptr);
  uint16_t nanoappInstanceId;
  if (getNappIdFromSubscriptionId(event->subscribeId, &nanoappInstanceId)) {
    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, event,
        freeNanDiscoveryEventCallback, nanoappInstanceId);
  } else {
    LOGE("Failed to find a nanoapp owning subscription ID %" PRIu32,
         event->subscribeId);
  }
}

void WifiRequestManager::handleNanServiceDiscoveryEvent(
    struct chreWifiNanDiscoveryEvent *event) {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    auto *event = static_cast<chreWifiNanDiscoveryEvent *>(data);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanServiceDiscoveryEventSync(event);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanServiceDiscoveryEvent, event, callback);
}

void WifiRequestManager::handleNanServiceLostEventSync(uint32_t subscriptionId,
                                                       uint32_t publisherId) {
  uint16_t nanoappInstanceId;
  if (getNappIdFromSubscriptionId(subscriptionId, &nanoappInstanceId)) {
    chreWifiNanSessionLostEvent *event =
        memoryAlloc<chreWifiNanSessionLostEvent>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->id = subscriptionId;
      event->peerId = publisherId;
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_NAN_SESSION_LOST, event, freeEventDataCallback,
          nanoappInstanceId);
    }
  } else {
    LOGE("Failed to find a nanoapp owning subscription ID %" PRIu32,
         subscriptionId);
  }
}

void WifiRequestManager::handleNanServiceLostEvent(uint32_t subscriptionId,
                                                   uint32_t publisherId) {
  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    auto subscriptionId = NestedDataPtr<uint32_t>(data);
    auto publisherId = NestedDataPtr<uint32_t>(extraData);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanServiceLostEventSync(subscriptionId, publisherId);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanServiceSessionLostEvent,
      NestedDataPtr<uint32_t>(subscriptionId), callback,
      NestedDataPtr<uint32_t>(publisherId));
}

void WifiRequestManager::handleNanServiceTerminatedEventSync(
    uint8_t errorCode, uint32_t subscriptionId) {
  uint16_t nanoappInstanceId;
  if (getNappIdFromSubscriptionId(subscriptionId, &nanoappInstanceId)) {
    chreWifiNanSessionTerminatedEvent *event =
        memoryAlloc<chreWifiNanSessionTerminatedEvent>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->id = subscriptionId;
      event->reason = errorCode;
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED, event, freeEventDataCallback,
          nanoappInstanceId);
    }
  } else {
    LOGE("Failed to find a nanoapp owning subscription ID %" PRIu32,
         subscriptionId);
  }
}

void WifiRequestManager::handleNanServiceSubscriptionCanceledEventSync(
    uint8_t errorCode, uint32_t subscriptionId) {
  for (size_t i = 0; i < mNanoappSubscriptions.size(); ++i) {
    if (mNanoappSubscriptions[i].subscriptionId == subscriptionId) {
      if (errorCode != CHRE_ERROR_NONE) {
        LOGE("Subscription %" PRIu32 " cancelation error: %" PRIu8,
             subscriptionId, errorCode);
      }
      mNanoappSubscriptions.erase(i);
      break;
    }
  }
}

void WifiRequestManager::handleNanServiceTerminatedEvent(
    uint8_t errorCode, uint32_t subscriptionId) {
  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    auto errorCode = NestedDataPtr<uint8_t>(data);
    auto subscriptionId = NestedDataPtr<uint32_t>(extraData);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanServiceTerminatedEventSync(errorCode, subscriptionId);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanServiceTerminatedEvent,
      NestedDataPtr<uint8_t>(errorCode), callback,
      NestedDataPtr<uint32_t>(subscriptionId));
}

void WifiRequestManager::handleNanServiceSubscriptionCanceledEvent(
    uint8_t errorCode, uint32_t subscriptionId) {
  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    auto errorCode = NestedDataPtr<uint8_t>(data);
    auto subscriptionId = NestedDataPtr<uint32_t>(extraData);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanServiceSubscriptionCanceledEventSync(errorCode,
                                                       subscriptionId);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanServiceTerminatedEvent,
      NestedDataPtr<uint8_t>(errorCode), callback,
      NestedDataPtr<uint32_t>(subscriptionId));
}

void WifiRequestManager::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print("\nWifi scan monitor %s\n",
                  scanMonitorIsEnabled() ? "enabled" : "disabled");

  if (scanMonitorIsEnabled()) {
    debugDump.print(" Wifi scan monitor enabled nanoapps:\n");
    for (uint16_t instanceId : mScanMonitorNanoapps) {
      debugDump.print("  nappId=%" PRIu16 "\n", instanceId);
    }
  }

  if (!mPendingScanRequests.empty()) {
    debugDump.print(" Wifi scan request queue:\n");
    for (const auto &request : mPendingScanRequests) {
      debugDump.print(" nappId=%" PRIu16, request.nanoappInstanceId);
    }
  }

  if (!mPendingScanMonitorRequests.empty()) {
    debugDump.print(" Wifi transition queue:\n");
    for (const auto &transition : mPendingScanMonitorRequests) {
      debugDump.print("  enable=%s nappId=%" PRIu16 "\n",
                      transition.enable ? "true" : "false",
                      transition.nanoappInstanceId);
    }
  }

  debugDump.print(" Last %zu wifi scan requests:\n",
                  mWifiScanRequestLogs.size());
  static_assert(kNumWifiRequestLogs <= INT8_MAX,
                "kNumWifiRequestLogs must be <= INT8_MAX");

  for (int8_t i = static_cast<int8_t>(mWifiScanRequestLogs.size()) - 1; i >= 0;
       i--) {
    const auto &log = mWifiScanRequestLogs[static_cast<size_t>(i)];
    debugDump.print("  ts=%" PRIu64 " nappId=%" PRIu16 " scanType=%" PRIu8
                    " maxScanAge(ms)=%" PRIu64 "\n",
                    log.timestamp.toRawNanoseconds(), log.instanceId,
                    log.scanType, log.maxScanAgeMs.getMilliseconds());
  }

  debugDump.print(" Last scan event @ %" PRIu64 " ms\n",
                  mLastScanEventTime.getMilliseconds());

  debugDump.print(" API error distribution (error-code indexed):\n");
  debugDump.print("   Scan monitor:\n");
  debugDump.logErrorHistogram(mScanMonitorErrorHistogram,
                              ARRAY_SIZE(mScanMonitorErrorHistogram));
  debugDump.print("   Active Scan:\n");
  debugDump.logErrorHistogram(mActiveScanErrorHistogram,
                              ARRAY_SIZE(mActiveScanErrorHistogram));

  if (!mNanoappSubscriptions.empty()) {
    debugDump.print(" Active NAN service subscriptions:\n");
    for (const auto &sub : mNanoappSubscriptions) {
      debugDump.print("  nappID=%" PRIu16 " sub ID=%" PRIu32 "\n",
                      sub.nanoappInstanceId, sub.subscriptionId);
    }
  }

  if (!mPendingNanSubscribeRequests.empty()) {
    debugDump.print(" Pending NAN service subscriptions:\n");
    for (const auto &req : mPendingNanSubscribeRequests) {
      debugDump.print("  nappID=%" PRIu16 " (type %" PRIu8 ") to svc: %s\n",
                      req.nanoappInstanceId, req.type, req.service.data());
    }
  }
}

bool WifiRequestManager::scanMonitorIsEnabled() const {
  return !mScanMonitorNanoapps.empty();
}

bool WifiRequestManager::nanoappHasScanMonitorRequest(
    uint16_t instanceId, size_t *nanoappIndex) const {
  size_t index = mScanMonitorNanoapps.find(instanceId);
  bool hasScanMonitorRequest = (index != mScanMonitorNanoapps.size());
  if (hasScanMonitorRequest && nanoappIndex != nullptr) {
    *nanoappIndex = index;
  }

  return hasScanMonitorRequest;
}

bool WifiRequestManager::scanMonitorIsInRequestedState(
    bool requestedState, bool nanoappHasRequest) const {
  return (requestedState == scanMonitorIsEnabled() ||
          (!requestedState &&
           (!nanoappHasRequest || mScanMonitorNanoapps.size() > 1)));
}

bool WifiRequestManager::scanMonitorStateTransitionIsRequired(
    bool requestedState, bool nanoappHasRequest) const {
  return ((requestedState && mScanMonitorNanoapps.empty()) ||
          (!requestedState && nanoappHasRequest &&
           mScanMonitorNanoapps.size() == 1));
}

bool WifiRequestManager::addScanMonitorRequestToQueue(Nanoapp *nanoapp,
                                                      bool enable,
                                                      const void *cookie) {
  PendingScanMonitorRequest scanMonitorStateTransition;
  scanMonitorStateTransition.nanoappInstanceId = nanoapp->getInstanceId();
  scanMonitorStateTransition.cookie = cookie;
  scanMonitorStateTransition.enable = enable;

  bool success = mPendingScanMonitorRequests.push(scanMonitorStateTransition);
  if (!success) {
    LOGW("Too many scan monitor state transitions");
  }

  return success;
}

bool WifiRequestManager::nanoappHasPendingScanMonitorRequest(
    uint16_t instanceId) const {
  const int numRequests = static_cast<int>(mPendingScanMonitorRequests.size());
  for (int i = numRequests - 1; i >= 0; i--) {
    const PendingScanMonitorRequest &request =
        mPendingScanMonitorRequests[static_cast<size_t>(i)];
    // The last pending request determines the state of the scan monitoring.
    if (request.nanoappInstanceId == instanceId) {
      return request.enable;
    }
  }

  return false;
}

bool WifiRequestManager::updateNanoappScanMonitoringList(bool enable,
                                                         uint16_t instanceId) {
  bool success = true;
  Nanoapp *nanoapp =
      EventLoopManagerSingleton::get()->getEventLoop().findNanoappByInstanceId(
          instanceId);
  size_t nanoappIndex;
  bool hasExistingRequest =
      nanoappHasScanMonitorRequest(instanceId, &nanoappIndex);

  if (nanoapp == nullptr) {
    // When the scan monitoring is disabled from inside nanoappEnd() or when
    // CHRE cleanup the subscription automatically it is possible that the
    // current method is called after the nanoapp is unloaded. In such a case
    // we still want to remove the nanoapp from mScanMonitorNanoapps.
    if (!enable && hasExistingRequest) {
      mScanMonitorNanoapps.erase(nanoappIndex);
    } else {
      LOGW("Failed to update scan monitoring list for non-existent nanoapp");
    }
  } else {
    if (enable) {
      if (!hasExistingRequest) {
        // The scan monitor was successfully enabled for this nanoapp and
        // there is no existing request. Add it to the list of scan monitoring
        // nanoapps.
        success = mScanMonitorNanoapps.push_back(instanceId);
        if (!success) {
          LOG_OOM();
        } else {
          nanoapp->registerForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
        }
      }
    } else if (hasExistingRequest) {
      // The scan monitor was successfully disabled for a previously enabled
      // nanoapp. Remove it from the list of scan monitoring nanoapps.
      mScanMonitorNanoapps.erase(nanoappIndex);
      nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
    }  // else disabling an inactive request, treat as success per the CHRE API.
  }

  return success;
}

bool WifiRequestManager::postScanMonitorAsyncResultEvent(
    uint16_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
    const void *cookie) {
  // Allocate and post an event to the nanoapp requesting wifi.
  bool eventPosted = false;
  if (!success || updateNanoappScanMonitoringList(enable, nanoappInstanceId)) {
    chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->requestType = CHRE_WIFI_REQUEST_TYPE_CONFIGURE_SCAN_MONITOR;
      event->success = success;
      event->errorCode = errorCode;
      event->reserved = 0;
      event->cookie = cookie;

      mScanMonitorErrorHistogram[errorCode]++;

      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeEventDataCallback,
          nanoappInstanceId);
      eventPosted = true;
    }
  }

  return eventPosted;
}

void WifiRequestManager::postScanMonitorAsyncResultEventFatal(
    uint16_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
    const void *cookie) {
  if (!postScanMonitorAsyncResultEvent(nanoappInstanceId, success, enable,
                                       errorCode, cookie)) {
    FATAL_ERROR("Failed to send WiFi scan monitor async result event");
  }
}

bool WifiRequestManager::postScanRequestAsyncResultEvent(
    uint16_t nanoappInstanceId, bool success, uint8_t errorCode,
    const void *cookie) {
  // TODO: the body of this function can be extracted to a common helper for use
  // across this function, postScanMonitorAsyncResultEvent,
  // postRangingAsyncResult, and GnssSession::postAsyncResultEvent
  bool eventPosted = false;
  chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
  if (event == nullptr) {
    LOG_OOM();
  } else {
    event->requestType = CHRE_WIFI_REQUEST_TYPE_REQUEST_SCAN;
    event->success = success;
    event->errorCode = errorCode;
    event->reserved = 0;
    event->cookie = cookie;

    mActiveScanErrorHistogram[errorCode]++;

    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeEventDataCallback,
        nanoappInstanceId);
    eventPosted = true;
  }

  return eventPosted;
}

void WifiRequestManager::postScanRequestAsyncResultEventFatal(
    uint16_t nanoappInstanceId, bool success, uint8_t errorCode,
    const void *cookie) {
  if (!postScanRequestAsyncResultEvent(nanoappInstanceId, success, errorCode,
                                       cookie)) {
    FATAL_ERROR("Failed to send WiFi scan request async result event");
  }
}

void WifiRequestManager::postScanEventFatal(chreWifiScanEvent *event) {
  mLastScanEventTime = Milliseconds(SystemTime::getMonotonicTime());
  EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
      CHRE_EVENT_WIFI_SCAN_RESULT, event, freeWifiScanEventCallback);
}

void WifiRequestManager::handleScanMonitorStateChangeSync(bool enabled,
                                                          uint8_t errorCode) {
  // Success is defined as having no errors ... in life ༼ つ ◕_◕ ༽つ
  bool success = (errorCode == CHRE_ERROR_NONE);

  // TODO(b/62904616): re-enable this assertion
  // CHRE_ASSERT_LOG(!mScanMonitorStateTransitions.empty(),
  //                "handleScanMonitorStateChangeSync called with no
  //                transitions");
  if (mPendingScanMonitorRequests.empty()) {
    LOGE(
        "WiFi PAL error: handleScanMonitorStateChangeSync called with no "
        "transitions (enabled %d errorCode %" PRIu8 ")",
        enabled, errorCode);
  }

  // Always check the front of the queue.
  if (!mPendingScanMonitorRequests.empty()) {
    const auto &stateTransition = mPendingScanMonitorRequests.front();
    success &= (stateTransition.enable == enabled);
    postScanMonitorAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                         success, stateTransition.enable,
                                         errorCode, stateTransition.cookie);
    mPendingScanMonitorRequests.pop();
  }

  dispatchQueuedConfigureScanMonitorRequests();
}

void WifiRequestManager::postNanAsyncResultEvent(uint16_t nanoappInstanceId,
                                                 uint8_t requestType,
                                                 bool success,
                                                 uint8_t errorCode,
                                                 const void *cookie) {
  chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
  if (event == nullptr) {
    LOG_OOM();
  } else {
    event->requestType = requestType;
    event->cookie = cookie;
    event->errorCode = errorCode;
    event->success = success;

    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeEventDataCallback,
        nanoappInstanceId);
  }
}

void WifiRequestManager::handleScanResponseSync(bool pending,
                                                uint8_t errorCode) {
  // TODO(b/65206783): re-enable this assertion
  // CHRE_ASSERT_LOG(mPendingScanRequests.empty(),
  //                "handleScanResponseSync called with no outstanding
  //                request");
  if (mPendingScanRequests.empty()) {
    LOGE("handleScanResponseSync called with no outstanding request");
  }

  if (mScanRequestTimeoutHandle != CHRE_TIMER_INVALID) {
    EventLoopManagerSingleton::get()->cancelDelayedCallback(
        mScanRequestTimeoutHandle);
    mScanRequestTimeoutHandle = CHRE_TIMER_INVALID;
  }

  // TODO: raise this to CHRE_ASSERT_LOG
  if (!pending && errorCode == CHRE_ERROR_NONE) {
    LOGE("Invalid wifi scan response");
    errorCode = CHRE_ERROR;
  }

  if (!mPendingScanRequests.empty()) {
    bool success = (pending && errorCode == CHRE_ERROR_NONE);
    if (!success) {
      LOGW("Wifi scan request failed: pending %d, errorCode %" PRIu8, pending,
           errorCode);
    }
    PendingScanRequest &currentScanRequest = mPendingScanRequests.front();
    postScanRequestAsyncResultEventFatal(currentScanRequest.nanoappInstanceId,
                                         success, errorCode,
                                         currentScanRequest.cookie);

    // Set a flag to indicate that results may be pending.
    mScanRequestResultsArePending = pending;

    if (pending) {
      Nanoapp *nanoapp =
          EventLoopManagerSingleton::get()
              ->getEventLoop()
              .findNanoappByInstanceId(currentScanRequest.nanoappInstanceId);
      if (nanoapp == nullptr) {
        LOGW("Received WiFi scan response for unknown nanoapp");
      } else {
        nanoapp->registerForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
      }
    } else {
      // If the scan results are not pending, pop the first event since it's no
      // longer waiting for anything. Otherwise, wait for the results to be
      // delivered and then pop the first request.
      mPendingScanRequests.pop();
      dispatchQueuedScanRequests(true /* postAsyncResult */);
    }
  }
}

bool WifiRequestManager::postRangingAsyncResult(uint8_t errorCode) {
  bool eventPosted = false;

  if (mPendingRangingRequests.empty()) {
    LOGE("Unexpected ranging event callback");
  } else {
    auto *event = memoryAlloc<struct chreAsyncResult>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      const PendingRangingRequest &req = mPendingRangingRequests.front();

      event->requestType = CHRE_WIFI_REQUEST_TYPE_RANGING;
      event->success = (errorCode == CHRE_ERROR_NONE);
      event->errorCode = errorCode;
      event->reserved = 0;
      event->cookie = req.cookie;

      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeEventDataCallback,
          req.nanoappInstanceId);
      eventPosted = true;
    }
  }

  return eventPosted;
}

bool WifiRequestManager::dispatchQueuedRangingRequest() {
  bool success = false;
  uint8_t asyncError = CHRE_ERROR_NONE;
  PendingRangingRequest &req = mPendingRangingRequests.front();

  if (!areRequiredSettingsEnabled()) {
    asyncError = CHRE_ERROR_FUNCTION_DISABLED;
  } else if (!sendRangingRequest(req)) {
    asyncError = CHRE_ERROR;
  } else {
    success = true;
  }

  if (asyncError != CHRE_ERROR_NONE) {
    postRangingAsyncResult(asyncError);
    mPendingRangingRequests.pop();
  }

  return success;
}

bool WifiRequestManager::dispatchQueuedNanSubscribeRequest() {
  bool success = false;

  if (!mPendingNanSubscribeRequests.empty()) {
    uint8_t asyncError = CHRE_ERROR_NONE;
    const auto &req = mPendingNanSubscribeRequests.front();
    struct chreWifiNanSubscribeConfig config = {};
    buildNanSubscribeConfigFromRequest(req, &config);

    if (!areRequiredSettingsEnabled()) {
      asyncError = CHRE_ERROR_FUNCTION_DISABLED;
    } else if (!mPlatformWifi.nanSubscribe(&config)) {
      asyncError = CHRE_ERROR;
    }

    if (asyncError != CHRE_ERROR_NONE) {
      postNanAsyncResultEvent(req.nanoappInstanceId,
                              CHRE_WIFI_REQUEST_TYPE_NAN_SUBSCRIBE,
                              false /*success*/, asyncError, req.cookie);
      mPendingNanSubscribeRequests.pop();
    } else {
      success = true;
    }
  }
  return success;
}

void WifiRequestManager::dispatchQueuedNanSubscribeRequestWithRetry() {
  while (!mPendingNanSubscribeRequests.empty() &&
         !dispatchQueuedNanSubscribeRequest())
    ;
}

bool WifiRequestManager::dispatchQueuedScanRequests(bool postAsyncResult) {
  while (!mPendingScanRequests.empty()) {
    uint8_t asyncError = CHRE_ERROR_NONE;
    const PendingScanRequest &currentScanRequest = mPendingScanRequests.front();

    if (!EventLoopManagerSingleton::get()
             ->getSettingManager()
             .getSettingEnabled(Setting::WIFI_AVAILABLE)) {
      asyncError = CHRE_ERROR_FUNCTION_DISABLED;
    } else if (!mPlatformWifi.requestScan(&currentScanRequest.scanParams)) {
      asyncError = CHRE_ERROR;
    } else {
      mScanRequestTimeoutHandle = setScanRequestTimer();
      return true;
    }

    if (postAsyncResult) {
      postScanRequestAsyncResultEvent(currentScanRequest.nanoappInstanceId,
                                      false /*success*/, asyncError,
                                      currentScanRequest.cookie);
    } else {
      LOGE("Wifi scan request failed");
    }
    mPendingScanRequests.pop();
  }
  return false;
}

void WifiRequestManager::handleRangingEventSync(
    uint8_t errorCode, struct chreWifiRangingEvent *event) {
  if (!areRequiredSettingsEnabled()) {
    errorCode = CHRE_ERROR_FUNCTION_DISABLED;
  }

  if (postRangingAsyncResult(errorCode)) {
    if (errorCode != CHRE_ERROR_NONE) {
      LOGW("RTT ranging failed with error %d", errorCode);
      if (event != nullptr) {
        freeWifiRangingEventCallback(CHRE_EVENT_WIFI_RANGING_RESULT, event);
      }
    } else {
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_RANGING_RESULT, event, freeWifiRangingEventCallback,
          mPendingRangingRequests.front().nanoappInstanceId);
    }
    mPendingRangingRequests.pop();
  }

  // If we have any pending requests, try issuing them to the platform until the
  // first one succeeds.
  while (!mPendingRangingRequests.empty() && !dispatchQueuedRangingRequest())
    ;
}

void WifiRequestManager::handleFreeWifiScanEvent(chreWifiScanEvent *scanEvent) {
  if (mScanRequestResultsArePending) {
    // Reset the event distribution logic once an entire scan event has been
    // received and processed by the nanoapp requesting the scan event.
    mScanEventResultCountAccumulator += scanEvent->resultCount;
    if (mScanEventResultCountAccumulator >= scanEvent->resultTotal) {
      mScanEventResultCountAccumulator = 0;
      mScanRequestResultsArePending = false;
    }

    if (!mScanRequestResultsArePending && !mPendingScanRequests.empty()) {
      uint16_t pendingNanoappInstanceId =
          mPendingScanRequests.front().nanoappInstanceId;
      Nanoapp *nanoapp = EventLoopManagerSingleton::get()
                             ->getEventLoop()
                             .findNanoappByInstanceId(pendingNanoappInstanceId);
      if (nanoapp == nullptr) {
        LOGW("Attempted to unsubscribe unknown nanoapp from WiFi scan events");
      } else if (!nanoappHasScanMonitorRequest(pendingNanoappInstanceId)) {
        nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
      }
      mPendingScanRequests.pop();
      dispatchQueuedScanRequests(true /* postAsyncResult */);
    }
  }

  mPlatformWifi.releaseScanEvent(scanEvent);
}

void WifiRequestManager::addWifiScanRequestLog(
    uint16_t nanoappInstanceId, const chreWifiScanParams *params) {
  mWifiScanRequestLogs.kick_push(
      WifiScanRequestLog(SystemTime::getMonotonicTime(), nanoappInstanceId,
                         static_cast<chreWifiScanType>(params->scanType),
                         static_cast<Milliseconds>(params->maxScanAgeMs)));
}

void WifiRequestManager::freeWifiScanEventCallback(uint16_t /* eventType */,
                                                   void *eventData) {
  auto *scanEvent = static_cast<struct chreWifiScanEvent *>(eventData);
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleFreeWifiScanEvent(scanEvent);
}

void WifiRequestManager::freeWifiRangingEventCallback(uint16_t /* eventType */,
                                                      void *eventData) {
  auto *event = static_cast<struct chreWifiRangingEvent *>(eventData);
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .mPlatformWifi.releaseRangingEvent(event);
}

void WifiRequestManager::freeNanDiscoveryEventCallback(uint16_t /* eventType */,
                                                       void *eventData) {
  auto *event = static_cast<struct chreWifiNanDiscoveryEvent *>(eventData);
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .mPlatformWifi.releaseNanDiscoveryEvent(event);
}

bool WifiRequestManager::nanSubscribe(
    Nanoapp *nanoapp, const struct chreWifiNanSubscribeConfig *config,
    const void *cookie) {
  CHRE_ASSERT(nanoapp);

  bool success = false;

  if (!areRequiredSettingsEnabled()) {
    success = true;
    postNanAsyncResultEvent(
        nanoapp->getInstanceId(), CHRE_WIFI_REQUEST_TYPE_NAN_SUBSCRIBE,
        false /*success*/, CHRE_ERROR_FUNCTION_DISABLED, cookie);
  } else {
    if (!mPendingNanSubscribeRequests.emplace()) {
      LOG_OOM();
    } else {
      auto &req = mPendingNanSubscribeRequests.back();
      req.nanoappInstanceId = nanoapp->getInstanceId();
      req.cookie = cookie;
      if (!copyNanSubscribeConfigToRequest(req, config)) {
        LOG_OOM();
      }

      if (mNanIsAvailable) {
        if (mPendingNanSubscribeRequests.size() == 1) {
          // First in line; dispatch request immediately.
          success = mPlatformWifi.nanSubscribe(config);
          if (!success) {
            mPendingNanSubscribeRequests.pop_back();
          }
        } else {
          success = true;
        }
      } else {
        success = true;
        sendNanConfiguration(true /*enable*/);
      }
    }
  }
  return success;
}

bool WifiRequestManager::nanSubscribeCancel(Nanoapp *nanoapp,
                                            uint32_t subscriptionId) {
  bool success = false;
  for (size_t i = 0; i < mNanoappSubscriptions.size(); ++i) {
    if (mNanoappSubscriptions[i].subscriptionId == subscriptionId &&
        mNanoappSubscriptions[i].nanoappInstanceId ==
            nanoapp->getInstanceId()) {
      success = mPlatformWifi.nanSubscribeCancel(subscriptionId);
      break;
    }
  }

  if (!success) {
    LOGE("Failed to cancel subscription %" PRIu32 " for napp %" PRIu16,
         subscriptionId, nanoapp->getInstanceId());
  }

  return success;
}

bool WifiRequestManager::copyNanSubscribeConfigToRequest(
    PendingNanSubscribeRequest &req,
    const struct chreWifiNanSubscribeConfig *config) {
  bool success = false;
  req.type = config->subscribeType;

  if (req.service.copy_array(config->service,
                             std::strlen(config->service) + 1) &&
      req.serviceSpecificInfo.copy_array(config->serviceSpecificInfo,
                                         config->serviceSpecificInfoSize) &&
      req.matchFilter.copy_array(config->matchFilter,
                                 config->matchFilterLength)) {
    success = true;
  } else {
    LOG_OOM();
  }

  return success;
}

void WifiRequestManager::buildNanSubscribeConfigFromRequest(
    const PendingNanSubscribeRequest &req,
    struct chreWifiNanSubscribeConfig *config) {
  config->subscribeType = req.type;
  config->service = req.service.data();
  config->serviceSpecificInfo = req.serviceSpecificInfo.data();
  config->serviceSpecificInfoSize =
      static_cast<uint32_t>(req.serviceSpecificInfo.size());
  config->matchFilter = req.matchFilter.data();
  config->matchFilterLength = static_cast<uint32_t>(req.matchFilter.size());
}

inline bool WifiRequestManager::areRequiredSettingsEnabled() {
  SettingManager &settingManager =
      EventLoopManagerSingleton::get()->getSettingManager();
  return settingManager.getSettingEnabled(Setting::LOCATION) &&
         settingManager.getSettingEnabled(Setting::WIFI_AVAILABLE);
}

void WifiRequestManager::cancelNanSubscriptionsAndInformNanoapps() {
  for (size_t i = 0; i < mNanoappSubscriptions.size(); ++i) {
    chreWifiNanSessionTerminatedEvent *event =
        memoryAlloc<chreWifiNanSessionTerminatedEvent>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->id = mNanoappSubscriptions[i].subscriptionId;
      event->reason = CHRE_ERROR_FUNCTION_DISABLED;
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED, event, freeEventDataCallback,
          mNanoappSubscriptions[i].nanoappInstanceId);
    }
  }
  mNanoappSubscriptions.clear();
}

void WifiRequestManager::cancelNanPendingRequestsAndInformNanoapps() {
  for (size_t i = 0; i < mPendingNanSubscribeRequests.size(); ++i) {
    auto &req = mPendingNanSubscribeRequests[i];
    chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
    if (event == nullptr) {
      LOG_OOM();
      break;
    } else {
      event->requestType = CHRE_WIFI_REQUEST_TYPE_NAN_SUBSCRIBE;
      event->success = false;
      event->errorCode = CHRE_ERROR_FUNCTION_DISABLED;
      event->cookie = req.cookie;
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeEventDataCallback,
          req.nanoappInstanceId);
    }
  }
  mPendingNanSubscribeRequests.clear();
}

void WifiRequestManager::handleNanAvailabilitySync(bool available) {
  PendingNanConfigType nanState =
      available ? PendingNanConfigType::ENABLE : PendingNanConfigType::DISABLE;
  mNanIsAvailable = available;

  if (nanState == mNanConfigRequestToHostPendingType) {
    mNanConfigRequestToHostPending = false;
    mNanConfigRequestToHostPendingType = PendingNanConfigType::UNKNOWN;
  }

  if (available) {
    dispatchQueuedNanSubscribeRequestWithRetry();
  } else {
    cancelNanPendingRequestsAndInformNanoapps();
    cancelNanSubscriptionsAndInformNanoapps();
  }
}

void WifiRequestManager::updateNanAvailability(bool available) {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    bool cbAvail = NestedDataPtr<bool>(data);
    EventLoopManagerSingleton::get()
        ->getWifiRequestManager()
        .handleNanAvailabilitySync(cbAvail);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiNanAvailabilityEvent,
      NestedDataPtr<bool>(available), callback);
}

void WifiRequestManager::sendNanConfiguration(bool enable) {
  PendingNanConfigType requiredState =
      enable ? PendingNanConfigType::ENABLE : PendingNanConfigType::DISABLE;
  if (!mNanConfigRequestToHostPending ||
      (mNanConfigRequestToHostPendingType != requiredState)) {
    mNanConfigRequestToHostPending = true;
    mNanConfigRequestToHostPendingType = requiredState;
    EventLoopManagerSingleton::get()
        ->getHostCommsManager()
        .sendNanConfiguration(enable);
  }
}

void WifiRequestManager::onSettingChanged(Setting setting, bool enabled) {
  if ((setting == Setting::WIFI_AVAILABLE) && !enabled) {
    cancelNanPendingRequestsAndInformNanoapps();
    cancelNanSubscriptionsAndInformNanoapps();
  }
}

}  // namespace chre
