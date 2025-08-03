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

#include "chre/core/gnss_manager.h"

#include <cstddef>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/assert.h"
#include "chre/platform/fatal_error.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/event_callbacks.h"

namespace chre {

namespace {

bool getCallbackType(uint16_t eventType, SystemCallbackType *callbackType) {
  bool success = true;
  switch (eventType) {
    case CHRE_EVENT_GNSS_LOCATION: {
      *callbackType = SystemCallbackType::GnssLocationReportEvent;
      break;
    }
    case CHRE_EVENT_GNSS_DATA: {
      *callbackType = SystemCallbackType::GnssMeasurementReportEvent;
      break;
    }
    default: {
      LOGE("Unknown event type %" PRIu16, eventType);
      success = false;
    }
  }

  return success;
}

bool getReportEventType(SystemCallbackType callbackType, uint16_t *eventType) {
  bool success = true;
  switch (callbackType) {
    case SystemCallbackType::GnssLocationReportEvent: {
      *eventType = CHRE_EVENT_GNSS_LOCATION;
      break;
    }
    case SystemCallbackType::GnssMeasurementReportEvent: {
      *eventType = CHRE_EVENT_GNSS_DATA;
      break;
    }
    default: {
      LOGE("Unknown callback type %" PRIu16,
           static_cast<uint16_t>(callbackType));
      success = false;
    }
  }

  return success;
}

}  // anonymous namespace

GnssManager::GnssManager()
    : mLocationSession(CHRE_EVENT_GNSS_LOCATION),
      mMeasurementSession(CHRE_EVENT_GNSS_DATA) {}

void GnssManager::init() {
  mPlatformGnss.init();
}

uint32_t GnssManager::getCapabilities() {
  return mPlatformGnss.getCapabilities();
}

void GnssManager::onSettingChanged(Setting setting, bool enabled) {
  mLocationSession.onSettingChanged(setting, enabled);
  mMeasurementSession.onSettingChanged(setting, enabled);
}

void GnssManager::handleRequestStateResyncCallback() {
  auto callback = [](uint16_t /* eventType */, void * /* eventData */,
                     void * /* extraData */) {
    EventLoopManagerSingleton::get()
        ->getGnssManager()
        .handleRequestStateResyncCallbackSync();
  };
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::GnssRequestResyncEvent, nullptr /* data */, callback);
}

bool GnssManager::configurePassiveLocationListener(Nanoapp *nanoapp,
                                                   bool enable) {
  bool success = false;
  uint16_t instanceId = nanoapp->getInstanceId();

  size_t index;
  if (nanoappHasPassiveLocationListener(instanceId, &index) != enable) {
    uint32_t capabilities = getCapabilities();
    bool locationSupported =
        (capabilities & CHRE_GNSS_CAPABILITIES_LOCATION) != 0;
    bool passiveLocationListenerSupported =
        (capabilities &
         CHRE_GNSS_CAPABILITIES_GNSS_ENGINE_BASED_PASSIVE_LISTENER) != 0;

    if (!locationSupported) {
      LOGE("Platform does not have the location capability");
    } else if (enable && !mPassiveLocationListenerNanoapps.prepareForPush()) {
      LOG_OOM();
    } else {
      bool platformEnable = enable && mPassiveLocationListenerNanoapps.empty();
      bool platformDisable =
          !enable && (mPassiveLocationListenerNanoapps.size() == 1);

      if (!passiveLocationListenerSupported) {
        // Silently succeed per API, since listener capability will occur within
        // CHRE (nanoapp requests).
        success = true;
      } else if (platformEnable || platformDisable) {
        success = platformConfigurePassiveLocationListener(enable);
      } else {
        // Platform was already in the configured state.
        success = true;
      }

      if (success) {
        if (enable) {
          mPassiveLocationListenerNanoapps.push_back(instanceId);
          nanoapp->registerForBroadcastEvent(CHRE_EVENT_GNSS_LOCATION);
        } else {
          mPassiveLocationListenerNanoapps.erase(index);
          if (!mLocationSession.nanoappHasRequest(instanceId)) {
            nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_GNSS_LOCATION);
          }
        }
      }
    }
  } else {  // else nanoapp request is already at the desired state.
    success = true;
  }

  return success;
}

bool GnssManager::nanoappHasPassiveLocationListener(uint16_t nanoappInstanceId,
                                                    size_t *index) {
  size_t foundIndex = mPassiveLocationListenerNanoapps.find(nanoappInstanceId);
  bool found = (foundIndex != mPassiveLocationListenerNanoapps.size());
  if (found && index != nullptr) {
    *index = foundIndex;
  }

  return found;
}

bool GnssManager::platformConfigurePassiveLocationListener(bool enable) {
  bool success = mPlatformGnss.configurePassiveLocationListener(enable);
  if (!success) {
    LOGE("Platform failed to %s passive location listener",
         enable ? "enable" : "disable");
  } else {
    mPlatformPassiveLocationListenerEnabled = enable;
  }

  return success;
}

void GnssManager::handleRequestStateResyncCallbackSync() {
  mLocationSession.handleRequestStateResyncCallbackSync();
  mMeasurementSession.handleRequestStateResyncCallbackSync();

  mPlatformPassiveLocationListenerEnabled = false;
  if (!mPassiveLocationListenerNanoapps.empty()) {
    if (!platformConfigurePassiveLocationListener(true /* enable */)) {
      FATAL_ERROR("Failed to resync passive location listener");
    }
  }
}

void GnssManager::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print("\nGNSS:");
  mLocationSession.logStateToBuffer(debugDump);
  mMeasurementSession.logStateToBuffer(debugDump);

  debugDump.print("\n API error distribution (error-code indexed):\n");
  debugDump.print("   GNSS Location:\n");
  debugDump.logErrorHistogram(mLocationSession.mGnssErrorHistogram,
                              ARRAY_SIZE(mLocationSession.mGnssErrorHistogram));
  debugDump.print("   GNSS Measurement:\n");
  debugDump.logErrorHistogram(
      mMeasurementSession.mGnssErrorHistogram,
      ARRAY_SIZE(mMeasurementSession.mGnssErrorHistogram));

  debugDump.print(
      "\n Passive location listener %s\n",
      mPlatformPassiveLocationListenerEnabled ? "enabled" : "disabled");
  for (uint16_t instanceId : mPassiveLocationListenerNanoapps) {
    debugDump.print("  nappId=%" PRIu16 "\n", instanceId);
  }
}

uint32_t GnssManager::disableAllSubscriptions(Nanoapp *nanoapp) {
  uint32_t numDisabledSubscriptions = 0;
  size_t index;

  if (mLocationSession.nanoappHasRequest(nanoapp)) {
    numDisabledSubscriptions++;
    mLocationSession.removeRequest(nanoapp, nullptr /*cookie*/);
  }

  if (mMeasurementSession.nanoappHasRequest(nanoapp)) {
    numDisabledSubscriptions++;
    mMeasurementSession.removeRequest(nanoapp, nullptr /*cookie*/);
  }

  if (nanoappHasPassiveLocationListener(nanoapp->getInstanceId(), &index)) {
    numDisabledSubscriptions++;
    configurePassiveLocationListener(nanoapp, false /*enable*/);
  }

  return numDisabledSubscriptions;
}

GnssSession::GnssSession(uint16_t reportEventType)
    : kReportEventType(reportEventType) {
  switch (kReportEventType) {
    case CHRE_EVENT_GNSS_LOCATION:
      mStartRequestType = CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_START;
      mStopRequestType = CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_STOP;
      mName = "Location";
      break;

    case CHRE_EVENT_GNSS_DATA:
      mStartRequestType = CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_START;
      mStopRequestType = CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_STOP;
      mName = "Measurement";
      break;

    default:
      CHRE_ASSERT_LOG(false, "Unsupported eventType %" PRIu16, reportEventType);
  }

  if (!mRequests.reserve(1)) {
    FATAL_ERROR_OOM();
  }
}

bool GnssSession::addRequest(Nanoapp *nanoapp, Milliseconds minInterval,
                             Milliseconds minTimeToNext, const void *cookie) {
  CHRE_ASSERT(nanoapp);
  return configure(nanoapp, true /* enable */, minInterval, minTimeToNext,
                   cookie);
}

bool GnssSession::removeRequest(Nanoapp *nanoapp, const void *cookie) {
  CHRE_ASSERT(nanoapp);
  return configure(nanoapp, false /* enable */, Milliseconds(UINT64_MAX),
                   Milliseconds(UINT64_MAX), cookie);
}

void GnssSession::handleStatusChange(bool enabled, uint8_t errorCode) {
  struct CallbackState {
    bool enabled;
    uint8_t errorCode;
  };

  auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
    auto *session = static_cast<GnssSession *>(data);
    CallbackState cbState = NestedDataPtr<CallbackState>(extraData);
    session->handleStatusChangeSync(cbState.enabled, cbState.errorCode);
  };

  CallbackState cbState = {};
  cbState.enabled = enabled;
  cbState.errorCode = errorCode;
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::GnssSessionStatusChange, /*data=*/this, callback,
      NestedDataPtr<CallbackState>(cbState));
}

void GnssSession::handleReportEvent(void *event) {
  if (mRequests.empty()) {
    LOGW("Unexpected %s event", mName);
  }

  auto callback = [](uint16_t type, void *data, void * /*extraData*/) {
    uint16_t reportEventType = 0;
    if (!getReportEventType(static_cast<SystemCallbackType>(type),
                            &reportEventType) ||
        !EventLoopManagerSingleton::get()
             ->getSettingManager()
             .getSettingEnabled(Setting::LOCATION)) {
      freeReportEventCallback(reportEventType, data);
    } else {
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          reportEventType, data, freeReportEventCallback);
    }
  };

  SystemCallbackType type;
  if (!getCallbackType(kReportEventType, &type) ||
      !EventLoopManagerSingleton::get()->deferCallback(type, event, callback)) {
    freeReportEventCallback(kReportEventType, event);
  }
}

void GnssSession::onSettingChanged(Setting setting, bool /*enabled*/) {
  if (setting == Setting::LOCATION) {
    if (asyncResponsePending()) {
      // A request is in progress, so we wait until the async response arrives
      // to handle the state change.
      mSettingChangePending = true;
    } else {
      mInternalRequestPending = updatePlatformRequest();
      mSettingChangePending = false;
    }
  }
}

bool GnssSession::updatePlatformRequest(bool forceUpdate) {
  bool enabled =
      EventLoopManagerSingleton::get()->getSettingManager().getSettingEnabled(
          Setting::LOCATION);

  bool desiredPlatformState = enabled && !mRequests.empty();
  bool shouldUpdatePlatform =
      forceUpdate ||
      (desiredPlatformState != mPlatformEnabled) /* (enable/disable) */;

  bool requestPending = false;
  if (shouldUpdatePlatform) {
    if (controlPlatform(desiredPlatformState, mCurrentInterval,
                        Milliseconds(0) /* minTimeToNext */)) {
      LOGD("Configured GNSS %s: enable %d", mName, desiredPlatformState);
      addSessionRequestLog(CHRE_INSTANCE_ID, mCurrentInterval,
                           desiredPlatformState);
      requestPending = true;
    } else {
      LOGE("Failed to configure GNSS %s: enable %d", mName,
           desiredPlatformState);
    }
  }

  return requestPending;
}

void GnssSession::handleRequestStateResyncCallbackSync() {
  if (asyncResponsePending()) {
    // A request is in progress, so we wait until the async response arrives
    // to handle the resync callback.
    mResyncPending = true;
  } else {
    mInternalRequestPending = updatePlatformRequest(true /* forceUpdate */);
  }
}

void GnssSession::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  // TODO: have all interval values print as INVALID if they are the max
  // unsigned value
  debugDump.print("\n %s: Curr int(ms)=%" PRIu64 "\n", mName,
                  mCurrentInterval.getMilliseconds());
  debugDump.print("  Requests:\n");
  for (const auto &request : mRequests) {
    debugDump.print("   minInt(ms)=%" PRIu64 " nappId=%" PRIu32 "\n",
                    request.minInterval.getMilliseconds(),
                    request.nanoappInstanceId);
  }

  if (!mStateTransitions.empty()) {
    debugDump.print("  Transition queue:\n");
    for (const auto &transition : mStateTransitions) {
      debugDump.print("   minInt(ms)=%" PRIu64 " enable=%d nappId=%" PRIu16
                      "\n",
                      transition.minInterval.getMilliseconds(),
                      transition.enable, transition.nanoappInstanceId);
    }
  }

  debugDump.print("  Last %zu session requests:\n", mSessionRequestLogs.size());
  static_assert(kNumSessionRequestLogs <= INT8_MAX,
                "kNumSessionRequestLogs must be less than INT8_MAX.");
  for (int8_t i = static_cast<int8_t>(mSessionRequestLogs.size()) - 1; i >= 0;
       i--) {
    const auto &log = mSessionRequestLogs[static_cast<size_t>(i)];
    debugDump.print("   ts=%" PRIu64 " nappId=%" PRIu16 " %s",
                    log.timestamp.toRawNanoseconds(), log.instanceId,
                    log.start ? "start" : "stop\n");
    if (log.start) {
      debugDump.print(" int(ms)=%" PRIu64 "\n", log.interval.getMilliseconds());
    }
  }
}

bool GnssSession::configure(Nanoapp *nanoapp, bool enable,
                            Milliseconds minInterval,
                            Milliseconds minTimeToNext, const void *cookie) {
  bool success = false;
  uint16_t instanceId = nanoapp->getInstanceId();
  size_t requestIndex = 0;
  bool hasRequest = nanoappHasRequest(instanceId, &requestIndex);

  if (asyncResponsePending()) {
    success = addRequestToQueue(instanceId, enable, minInterval, cookie);
  } else if (stateTransitionIsRequired(enable, minInterval, hasRequest,
                                       requestIndex)) {
    if (enable && !EventLoopManagerSingleton::get()
                       ->getSettingManager()
                       .getSettingEnabled(Setting::LOCATION)) {
      // Treat as success but post async failure per API.
      success = postAsyncResultEvent(instanceId, false /* success */, enable,
                                     minInterval, CHRE_ERROR_FUNCTION_DISABLED,
                                     cookie);
    } else if (addRequestToQueue(instanceId, enable, minInterval, cookie)) {
      success = controlPlatform(enable, minInterval, minTimeToNext);
      if (!success) {
        mStateTransitions.pop_back();
        LOGE("Failed to request a GNSS session for nanoapp instance %" PRIu16
             " enable %d",
             instanceId, enable);
      }
    }
  } else {
    success = postAsyncResultEvent(instanceId, true /* success */, enable,
                                   minInterval, CHRE_ERROR_NONE, cookie);
  }

  if (success) {
    addSessionRequestLog(nanoapp->getInstanceId(), minInterval, enable);
  }

  return success;
}

bool GnssSession::nanoappHasRequest(uint16_t instanceId,
                                    size_t *requestIndex) const {
  bool hasRequest = false;
  for (size_t i = 0; i < mRequests.size(); i++) {
    if (mRequests[i].nanoappInstanceId == instanceId) {
      hasRequest = true;
      if (requestIndex != nullptr) {
        *requestIndex = i;
      }

      break;
    }
  }

  return hasRequest;
}

bool GnssSession::nanoappHasRequest(Nanoapp *nanoapp) const {
  return nanoappHasRequest(nanoapp->getInstanceId(), nullptr /*requestIndex*/);
}

bool GnssSession::addRequestToQueue(uint16_t instanceId, bool enable,
                                    Milliseconds minInterval,
                                    const void *cookie) {
  StateTransition stateTransition;
  stateTransition.nanoappInstanceId = instanceId;
  stateTransition.enable = enable;
  stateTransition.minInterval = minInterval;
  stateTransition.cookie = cookie;

  bool success = mStateTransitions.push(stateTransition);
  if (!success) {
    LOGW("Too many session state transitions");
  }

  return success;
}

bool GnssSession::isEnabled() const {
  return !mRequests.empty();
}

bool GnssSession::stateTransitionIsRequired(bool requestedState,
                                            Milliseconds minInterval,
                                            bool nanoappHasRequest,
                                            size_t requestIndex) const {
  bool requestToEnable = (requestedState && !isEnabled());
  bool requestToIncreaseRate =
      (requestedState && isEnabled() && minInterval < mCurrentInterval);
  bool requestToDisable =
      (!requestedState && nanoappHasRequest && mRequests.size() == 1);

  // An effective rate decrease for the session can only occur if the nanoapp
  // has an existing request.
  bool requestToDecreaseRate = false;
  if (nanoappHasRequest) {
    // The nanoapp has an existing request. Check that the request does not
    // result in a rate decrease by checking if no other nanoapps have the
    // same request, the nanoapp's existing request is not equal to the current
    // requested interval and the new request is slower than the current
    // requested rate.
    size_t requestCount = 0;
    const auto &currentRequest = mRequests[requestIndex];
    for (size_t i = 0; i < mRequests.size(); i++) {
      const Request &request = mRequests[i];
      if (i != requestIndex &&
          request.minInterval == currentRequest.minInterval) {
        requestCount++;
      }
    }

    requestToDecreaseRate =
        (minInterval > mCurrentInterval &&
         currentRequest.minInterval == mCurrentInterval && requestCount == 0);
  }

  return (requestToEnable || requestToDisable || requestToIncreaseRate ||
          requestToDecreaseRate);
}

bool GnssSession::updateRequests(bool enable, Milliseconds minInterval,
                                 uint16_t instanceId) {
  bool success = true;
  Nanoapp *nanoapp =
      EventLoopManagerSingleton::get()->getEventLoop().findNanoappByInstanceId(
          instanceId);
  if (nanoapp == nullptr) {
    LOGW("Failed to update GNSS session request list for non-existent nanoapp");
  } else {
    size_t requestIndex;
    bool hasExistingRequest = nanoappHasRequest(instanceId, &requestIndex);
    if (enable) {
      if (hasExistingRequest) {
        // If the nanoapp has an open request ensure that the minInterval is
        // kept up to date.
        mRequests[requestIndex].minInterval = minInterval;
      } else {
        // The GNSS session was successfully enabled for this nanoapp and
        // there is no existing request. Add it to the list of GNSS session
        // nanoapps.
        Request request;
        request.nanoappInstanceId = instanceId;
        request.minInterval = minInterval;
        success = mRequests.push_back(request);
        if (!success) {
          LOG_OOM();
        } else {
          nanoapp->registerForBroadcastEvent(kReportEventType);
        }
      }
    } else if (hasExistingRequest) {
      // The session was successfully disabled for a previously enabled
      // nanoapp. Remove it from the list of requests.
      mRequests.erase(requestIndex);

      // We can only unregister the location events from nanoapps if it has no
      // request and has not configured the passive listener.
      if ((kReportEventType != CHRE_EVENT_GNSS_LOCATION) ||
          !EventLoopManagerSingleton::get()
               ->getGnssManager()
               .nanoappHasPassiveLocationListener(instanceId)) {
        nanoapp->unregisterForBroadcastEvent(kReportEventType);
      }
    }  // else disabling an inactive request, treat as success per CHRE API
  }

  return success;
}

bool GnssSession::postAsyncResultEvent(uint16_t instanceId, bool success,
                                       bool enable, Milliseconds minInterval,
                                       uint8_t errorCode, const void *cookie) {
  bool eventPosted = false;
  if (!success || updateRequests(enable, minInterval, instanceId)) {
    chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
    if (event == nullptr) {
      LOG_OOM();
    } else {
      event->requestType = enable ? mStartRequestType : mStopRequestType;
      event->success = success;
      event->errorCode = errorCode;
      event->reserved = 0;
      event->cookie = cookie;

      mGnssErrorHistogram[errorCode]++;

      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          CHRE_EVENT_GNSS_ASYNC_RESULT, event, freeEventDataCallback,
          instanceId);
      eventPosted = true;
    }
  }

  return eventPosted;
}

void GnssSession::postAsyncResultEventFatal(uint16_t instanceId, bool success,
                                            bool enable,
                                            Milliseconds minInterval,
                                            uint8_t errorCode,
                                            const void *cookie) {
  if (!postAsyncResultEvent(instanceId, success, enable, minInterval, errorCode,
                            cookie)) {
    FATAL_ERROR("Failed to send GNSS session request async result event");
  }
}

void GnssSession::handleStatusChangeSync(bool enabled, uint8_t errorCode) {
  bool success = (errorCode == CHRE_ERROR_NONE);

  if (mInternalRequestPending) {
    // Silently handle internal requests from CHRE, since they are not pushed
    // to the mStateTransitions queue.
    mInternalRequestPending = false;
  } else if (!mStateTransitions.empty()) {
    const auto &stateTransition = mStateTransitions.front();

    if (success) {
      mCurrentInterval = stateTransition.minInterval;
    }

    if (success && stateTransition.enable != enabled) {
      success = false;
      errorCode = CHRE_ERROR;
      LOGE("GNSS PAL did not transition to expected state");
    }
    postAsyncResultEventFatal(
        stateTransition.nanoappInstanceId, success, stateTransition.enable,
        stateTransition.minInterval, errorCode, stateTransition.cookie);
    mStateTransitions.pop();
  } else {
    // TODO(b/296222493): change this back to an assert once issue resolved
    LOGE("GnssSession::handleStatusChangeSync called with no transitions");
    return;
  }

  // If a previous setting change or resync event is pending process, do that
  // first.
  if (mResyncPending && !success) {
    // We only send a platform request on resync if a pending request failed,
    // because we still need to restore the previous request state.
    mInternalRequestPending = updatePlatformRequest(true /* forceUpdate */);
  } else if (mSettingChangePending) {
    mInternalRequestPending = updatePlatformRequest();
  }

  mResyncPending = false;
  mSettingChangePending = false;

  // If we didn't issue an internally-generated update via
  // updatePlatformRequest(), process pending nanoapp requests (otherwise,
  // wait for it to finish, then process any pending requests)
  if (!mInternalRequestPending) {
    dispatchQueuedStateTransitions();
  }
}

void GnssSession::freeReportEventCallback(uint16_t eventType, void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_GNSS_LOCATION:
      EventLoopManagerSingleton::get()
          ->getGnssManager()
          .mPlatformGnss.releaseLocationEvent(
              static_cast<chreGnssLocationEvent *>(eventData));
      break;

    case CHRE_EVENT_GNSS_DATA:
      EventLoopManagerSingleton::get()
          ->getGnssManager()
          .mPlatformGnss.releaseMeasurementDataEvent(
              static_cast<chreGnssDataEvent *>(eventData));
      break;

    default:
      CHRE_ASSERT_LOG(false, "Unhandled event type %" PRIu16, eventType);
  }
}

bool GnssSession::controlPlatform(bool enable, Milliseconds minInterval,
                                  Milliseconds /* minTimeToNext */) {
  bool success = false;

  switch (kReportEventType) {
    case CHRE_EVENT_GNSS_LOCATION:
      // TODO: Provide support for min time to next report. It is currently sent
      // to the platform as zero.
      success = EventLoopManagerSingleton::get()
                    ->getGnssManager()
                    .mPlatformGnss.controlLocationSession(enable, minInterval,
                                                          Milliseconds(0));
      break;

    case CHRE_EVENT_GNSS_DATA:
      success =
          EventLoopManagerSingleton::get()
              ->getGnssManager()
              .mPlatformGnss.controlMeasurementSession(enable, minInterval);
      break;

    default:
      CHRE_ASSERT_LOG(false, "Unhandled event type %" PRIu16, kReportEventType);
  }

  if (success) {
    mPlatformEnabled = enable;
  }

  return success;
}

void GnssSession::addSessionRequestLog(uint16_t nanoappInstanceId,
                                       Milliseconds interval, bool start) {
  mSessionRequestLogs.kick_push(SessionRequestLog(
      SystemTime::getMonotonicTime(), nanoappInstanceId, interval, start));
}

void GnssSession::dispatchQueuedStateTransitions() {
  while (!mStateTransitions.empty()) {
    const auto &stateTransition = mStateTransitions.front();

    size_t requestIndex = 0;
    bool hasRequest =
        nanoappHasRequest(stateTransition.nanoappInstanceId, &requestIndex);

    if (stateTransitionIsRequired(stateTransition.enable,
                                  stateTransition.minInterval, hasRequest,
                                  requestIndex)) {
      if (!EventLoopManagerSingleton::get()
               ->getSettingManager()
               .getSettingEnabled(Setting::LOCATION)) {
        postAsyncResultEventFatal(
            stateTransition.nanoappInstanceId, false /* success */,
            stateTransition.enable, stateTransition.minInterval,
            CHRE_ERROR_FUNCTION_DISABLED, stateTransition.cookie);
        mStateTransitions.pop();
      } else if (controlPlatform(stateTransition.enable,
                                 stateTransition.minInterval,
                                 Milliseconds(0))) {
        break;
      } else {
        LOGE("Failed to enable a GNSS session for nanoapp instance %" PRIu16,
             stateTransition.nanoappInstanceId);
        postAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                  false /* success */, stateTransition.enable,
                                  stateTransition.minInterval, CHRE_ERROR,
                                  stateTransition.cookie);
        mStateTransitions.pop();
      }
    } else {
      postAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                true /* success */, stateTransition.enable,
                                stateTransition.minInterval, CHRE_ERROR_NONE,
                                stateTransition.cookie);
      mStateTransitions.pop();
    }
  }
}

}  // namespace chre
