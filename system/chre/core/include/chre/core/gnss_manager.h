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

#ifndef CHRE_CORE_GNSS_MANAGER_H_
#define CHRE_CORE_GNSS_MANAGER_H_

#include <cstdint>

#include "chre/core/api_manager_common.h"
#include "chre/core/nanoapp.h"
#include "chre/core/settings.h"
#include "chre/platform/platform_gnss.h"
#include "chre/util/non_copyable.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/time.h"

namespace chre {

class GnssManager;

/**
 * A helper class that manages requests for a GNSS location or measurement
 * session.
 */
class GnssSession {
 public:
  /**
   * Adds a request to a session asynchronously. The result is delivered
   * through a CHRE_EVENT_GNSS_ASYNC_RESULT event.
   *
   * @param nanoapp The nanoapp adding the request.
   * @param minInterval The minimum reporting interval for results.
   * @param timeToNext The amount of time that the GNSS system is allowed to
   *        delay generating a report.
   * @param cookie A cookie that is round-tripped to provide context to the
   *        nanoapp making the request.
   *
   * @return true if the request was accepted for processing.
   */
  bool addRequest(Nanoapp *nanoapp, Milliseconds minInterval,
                  Milliseconds minTimeToNext, const void *cookie);

  /**
   * Removes a request from a session asynchronously. The result is delivered
   * through a CHRE_EVENT_GNSS_ASYNC_RESULT event.
   *
   * @param nanoapp The nanoapp removing the request.
   * @param cookie A cookie that is round-tripped to provide context to the
   *        nanoapp making the request.
   *
   * @return true if the request was accepted for processing.
   */
  bool removeRequest(Nanoapp *nanoapp, const void *cookie);

  /**
   * Checks if a nanoapp has an open session request.
   *
   * @param nanoapp The nanoapp removing the request.
   *
   * @return whether the nanoapp has an active request.
   */
  bool nanoappHasRequest(Nanoapp *nanoapp) const;

  /**
   * Handles the result of a request to the PlatformGnss to request a change to
   * the session.
   *
   * @param enabled true if the session is currently active.
   * @param errorCode an error code that is used to indicate success or what
   *        type of error has occured. See chreError enum in the CHRE API for
   *        additional details.
   */
  void handleStatusChange(bool enabled, uint8_t errorCode);

  /**
   * Handles a CHRE GNSS report (location/data) event.
   *
   * @param event The GNSS report event provided to the GNSS session. This
   *        memory is guaranteed not to be modified until it has been explicitly
   *        released through the PlatformGnss instance.
   */
  void handleReportEvent(void *event);

  /**
   * @return true if an async response is pending from GNSS. This method should
   * be used to check if a GNSS session request is in flight.
   */
  bool asyncResponsePending() const {
    return !mStateTransitions.empty() || mInternalRequestPending;
  }

  /**
   * Invoked when the host notifies CHRE of a settings change.
   *
   * @param setting The setting that changed.
   * @param enabled Whether setting is enabled or not.
   */
  void onSettingChanged(Setting setting, bool enabled);

  /**
   * Updates the platform GNSS request according to the current state. It should
   * be used to synchronize the GNSS to the desired state, e.g. for setting
   * updates or handling a state resync request.
   *
   * @param forceUpdate If true, force the platform GNSS request to be made.
   *
   * @return true if the invocation resulted in dispatching an internal
   *         request to control the platform layer
   */
  bool updatePlatformRequest(bool forceUpdate = false);

  /**
   * Invoked as a result of a requestStateResync() callback from the GNSS PAL.
   * Runs in the context of the CHRE thread.
   */
  void handleRequestStateResyncCallbackSync();

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   *     into one of the buffers.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump) const;

 private:
  /**
   * Tracks a nanoapp that has subscribed to a session and the reporting
   * interval.
   */
  struct Request {
    //! The nanoapp instance ID that made this request.
    uint32_t nanoappInstanceId;

    //! The interval of results requested.
    Milliseconds minInterval;
  };

  //! Internal struct with data needed to log last X session requests
  struct SessionRequestLog {
    SessionRequestLog(Nanoseconds timestampIn, uint16_t instanceIdIn,
                      Milliseconds intervalIn, bool startIn)
        : timestamp(timestampIn),
          instanceId(instanceIdIn),
          interval(intervalIn),
          start(startIn) {}
    Nanoseconds timestamp;
    uint16_t instanceId;
    Milliseconds interval;
    bool start;
  };

  /**
   * Tracks the state of the GNSS engine.
   */
  struct StateTransition {
    //! The cookie provided to the CHRE API when the nanoapp requested a
    //! change to the state of the GNSS engine.
    const void *cookie;

    //! The nanoapp instance ID that prompted the change.
    uint16_t nanoappInstanceId;

    //! The target state of the GNSS engine.
    bool enable;

    //! The target minimum reporting interval for the GNSS engine. This is only
    //! valid if enable is set to true.
    Milliseconds minInterval;
  };

  //! The event type of the session's report data.
  const uint16_t kReportEventType;

  //! The request type to start and stop a session.
  uint8_t mStartRequestType;
  uint8_t mStopRequestType;

  //! The session name, used in logging state.
  const char *mName;

  //! The maximum number of pending state transitions allowed.
  static constexpr size_t kMaxGnssStateTransitions = 8;

  //! The queue of state transitions for the GNSS engine. Only one asynchronous
  //! state transition can be in flight at one time. Any further requests are
  //! queued here.
  ArrayQueue<StateTransition, kMaxGnssStateTransitions> mStateTransitions;

  //! The list of most recent session request logs
  static constexpr size_t kNumSessionRequestLogs = 10;
  ArrayQueue<SessionRequestLog, kNumSessionRequestLogs> mSessionRequestLogs;

  //! The request multiplexer for GNSS session requests.
  DynamicVector<Request> mRequests;

  //! The current report interval being sent to the session. This is only valid
  //! if the mRequests is non-empty.
  Milliseconds mCurrentInterval = Milliseconds(UINT64_MAX);

  //! The state of the last successful request to the platform.
  bool mPlatformEnabled = false;

  //! True if a request from the CHRE framework is currently pending.
  bool mInternalRequestPending = false;

  //! True if a setting change event is pending to be processed.
  bool mSettingChangePending = false;

  //! True if a state resync callback is pending to be processed.
  bool mResyncPending = false;

  // Allows GnssManager to access constructor.
  friend class GnssManager;

  //! The histogram of error codes for collected errors, the index of this array
  //! corresponds to the type of the errorcode
  uint32_t mGnssErrorHistogram[CHRE_ERROR_SIZE] = {0};

  /**
   * Constructs a GnssSesson.
   *
   * @param reportEventType The report event type of this GNSS session.
   *        Currently, only CHRE_EVENT_GNSS_LOCATION for a location session and
   *        CHRE_EVENT_GNSS_LOCATION for a measurement session are supported.
   */
  GnssSession(uint16_t reportEventType);

  /**
   * Configures the GNSS engine to be enabled/disabled. If enable is set to true
   * then the minInterval and minTimeToNext values are valid.
   *
   * @param nanoapp The nanoapp requesting the state change for the engine.
   * @param enable Whether to enable or disable the engine.
   * @param minInterval The minimum reporting interval requested by the nanoapp.
   * @param minTimeToNext The minimum time to the next report.
   * @param cookie The cookie provided by the nanoapp to round-trip for context.
   *
   * @return true if the request was accepted.
   */
  bool configure(Nanoapp *nanoapp, bool enable, Milliseconds minInterval,
                 Milliseconds minTimeToNext, const void *cookie);

  /**
   * Checks if a nanoapp has an open session request.
   *
   * @param instanceId The nanoapp instance ID to search for.
   * @param requestIndex A pointer to an index to populate if the nanoapp has an
   *        open session request.
   *
   * @return true if the provided instanceId was found.
   */
  bool nanoappHasRequest(uint16_t instanceId,
                         size_t *requestIndex = nullptr) const;

  /**
   * Adds a request for a session to the queue of state transitions.
   *
   * @param instanceId The nanoapp instance ID requesting a session.
   * @param enable Whether the session is being enabled or disabled for this
   *        nanoapp.
   * @param minInterval The minimum interval requested by the nanoapp.
   * @param cookie A cookie that is round-tripped to the nanoapp for context.
   *
   * @return true if the state transition was added to the queue.
   */
  bool addRequestToQueue(uint16_t instanceId, bool enable,
                         Milliseconds minInterval, const void *cookie);

  /**
   * @return true if the session is currently enabled.
   */
  bool isEnabled() const;

  /**
   * Determines if a change to the session state is required given a set of
   * parameters.
   *
   * @param requestedState The target state requested by a nanoapp.
   * @param minInterval The minimum reporting interval.
   * @param nanoappHasRequest If the nanoapp already has a request.
   * @param requestIndex The index of the request in the list of open requests
   *        if nanoappHasRequest is set to true.
   *
   * @return true if a state transition is required.
   */
  bool stateTransitionIsRequired(bool requestedState, Milliseconds minInterval,
                                 bool nanoappHasRequest,
                                 size_t requestIndex) const;

  /**
   * Updates the session requests given a nanoapp and the interval requested.
   *
   * @param enable true if enabling the session.
   * @param minInterval the minimum reporting interval if enable is true.
   * @param instanceId the nanoapp instance ID that owns the request.
   *
   * @return true if the session request list was updated.
   */
  bool updateRequests(bool enable, Milliseconds minInterval,
                      uint16_t instanceId);

  /**
   * Posts the result of a GNSS session add/remove request.
   *
   * @param instanceId The nanoapp instance ID that made the request.
   * @param success true if the operation was successful.
   * @param enable true if enabling the session.
   * @param minInterval the minimum reporting interval.
   * @param errorCode the error code as a result of this operation.
   * @param cookie the cookie that the nanoapp is provided for context.
   *
   * @return true if the event was successfully posted.
   */
  bool postAsyncResultEvent(uint16_t instanceId, bool success, bool enable,
                            Milliseconds minInterval, uint8_t errorCode,
                            const void *cookie);

  /**
   * Calls through to postAsyncResultEvent but invokes FATAL_ERROR if the
   * event is not posted successfully. This is used in asynchronous contexts
   * where a nanoapp could be stuck waiting for a response but CHRE failed to
   * enqueue one. For parameter details,
   * @see postAsyncResultEvent
   */
  void postAsyncResultEventFatal(uint16_t instanceId, bool success, bool enable,
                                 Milliseconds minInterval, uint8_t errorCode,
                                 const void *cookie);

  /**
   * Handles the result of a request to PlatformGnss to change the state of
   * the session. See the handleStatusChange method which may be called from
   * any thread. This method is intended to be invoked on the CHRE event loop
   * thread.
   *
   * @param enabled true if the session was enabled
   * @param errorCode an error code that is provided to indicate success.
   */
  void handleStatusChangeSync(bool enabled, uint8_t errorCode);

  /**
   * Releases a GNSS report event after nanoapps have consumed it.
   *
   * @param eventType the type of event being freed.
   * @param eventData a pointer to the scan event to release.
   */
  static void freeReportEventCallback(uint16_t eventType, void *eventData);

  /**
   * Configures PlatformGnss based on session settings.
   *
   * @return true if PlatformGnss has accepted the setting.
   */
  bool controlPlatform(bool enable, Milliseconds minInterval,
                       Milliseconds minTimeToNext);

  /**
   * Add a log to list of session logs possibly pushing out the oldest log.
   *
   * @param nanoappInstanceId the instance of id of nanoapp requesting
   * @param interval the interval in milliseconds for request
   * @param start true if the is a start request, false if a stop request
   */
  void addSessionRequestLog(uint16_t nanoappInstanceId, Milliseconds interval,
                            bool start);

  /**
   * Dispatches pending state transitions on the queue until the first one
   * succeeds.
   */
  void dispatchQueuedStateTransitions();
};

/**
 * The GnssManager handles platform init, capability query, and delagates debug
 * dump and all GNSS request management to GnssSession(s), which includes
 * multiplexing multiple requests into one for the platform to handle.
 *
 * This class is effectively a singleton as there can only be one instance of
 * the PlatformGnss instance.
 */
class GnssManager : public NonCopyable {
 public:
  /**
   * Constructs a GnssManager.
   */
  GnssManager();

  /**
   * Initializes the underlying platform-specific GNSS module. Must be called
   * prior to invoking any other methods in this class.
   */
  void init();

  /**
   * @return the GNSS capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  GnssSession &getLocationSession() {
    return mLocationSession;
  }

  GnssSession &getMeasurementSession() {
    return mMeasurementSession;
  }

  /**
   * Invoked when the host notifies CHRE of a settings change.
   *
   * @param setting The setting that changed.
   * @param enabled Whether setting is enabled or not.
   */
  void onSettingChanged(Setting setting, bool enabled);

  /**
   * Invoked as a result of a requestStateResync() callback from the GNSS PAL.
   * Runs asynchronously in the context of the callback immediately.
   */
  void handleRequestStateResyncCallback();

  /**
   * Invoked as a result of a requestStateResync() callback from the GNSS PAL.
   * Runs in the context of the CHRE thread.
   */
  void handleRequestStateResyncCallbackSync();

  /**
   * @param nanoapp The nanoapp invoking
   * chreGnssConfigurePassiveLocationListener.
   * @param enable true to enable the configuration.
   *
   * @return true if the configuration succeeded.
   */
  bool configurePassiveLocationListener(Nanoapp *nanoapp, bool enable);

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   *     into one of the buffers.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump) const;

  /**
   * Disables the location session, the measurement session and the passive
   * location listener associated to a nanoapp.
   *
   * @param nanoapp A non-null pointer to the nanoapp.
   *
   * @return The number of subscriptions disabled.
   */
  uint32_t disableAllSubscriptions(Nanoapp *nanoapp);

 private:
  // Allows GnssSession to access mPlatformGnss.
  friend class GnssSession;

  //! The platform GNSS interface.
  PlatformGnss mPlatformGnss;

  //! The instance of location session.
  GnssSession mLocationSession;

  //! The instance of measurement session.
  GnssSession mMeasurementSession;

  //! The list of instance ID of nanoapps that has a passive location listener
  //! request.
  DynamicVector<uint16_t> mPassiveLocationListenerNanoapps;

  //! true if the passive location listener is enabled at the platform.
  bool mPlatformPassiveLocationListenerEnabled;

  /**
   * @param nanoappInstanceId The instance ID of the nanoapp to check.
   * @param index If non-null and this function returns true, stores the index
   * of mPassiveLocationListenerNanoapps where the instance ID is stored.
   *
   * @return true if the nanoapp currently has a passive location listener
   * request.
   */
  bool nanoappHasPassiveLocationListener(uint16_t nanoappInstanceId,
                                         size_t *index = nullptr);

  /**
   * Helper function to invoke configurePassiveLocationListener at the platform
   * and handle the result.
   *
   * @param enable true to enable the configuration.
   *
   * @return true if success.
   */
  bool platformConfigurePassiveLocationListener(bool enable);
};

}  // namespace chre

#endif  // CHRE_CORE_GNSS_MANAGER_H_
