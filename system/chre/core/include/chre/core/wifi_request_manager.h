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

#ifndef CHRE_CORE_WIFI_REQUEST_MANAGER_H_
#define CHRE_CORE_WIFI_REQUEST_MANAGER_H_

#include "chre/core/api_manager_common.h"
#include "chre/core/nanoapp.h"
#include "chre/core/settings.h"
#include "chre/core/timer_pool.h"
#include "chre/platform/platform_wifi.h"
#include "chre/util/buffer.h"
#include "chre/util/non_copyable.h"
#include "chre/util/optional.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/time.h"
#include "chre_api/chre/wifi.h"

namespace chre {

/**
 * The WifiRequestManager handles requests from nanoapps for Wifi information.
 * This includes multiplexing multiple requests into one for the platform to
 * handle.
 *
 * This class is effectively a singleton as there can only be one instance of
 * the PlatformWifi instance.
 */
class WifiRequestManager : public NonCopyable {
 public:
  /**
   * Specifies what type of ranging request is being issued.
   *
   * WIFI_AP denotes a ranging request to a (list of) device(s) via an access
   * point. WIFI_AWARE denotes  a NAN ranging request to a single peer NAN
   * device. Even though the abbreviation 'NAN' is used throughout the CHRE
   * WiFi code and documentation, the simplified enumerator NAN is avoided here
   * to prevent possible symbol/identifier clashes to a NAN (not-a-number)
   * defines in clang and GCC's math header.
   */
  enum class RangingType { WIFI_AP, WIFI_AWARE };

  /**
   * Initializes the WifiRequestManager with a default state and memory for any
   * requests.
   */
  WifiRequestManager();

  /**
   * Initializes the underlying platform-specific WiFi module. Must be called
   * prior to invoking any other methods in this class.
   */
  void init();

  /**
   * @return the WiFi capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  /**
   * Handles a request from a nanoapp to configure the scan monitor. This
   * includes merging multiple requests for scan monitoring to the PAL (ie: if
   * multiple apps enable the scan monitor the PAL is only enabled once).
   *
   * @param nanoapp The nanoapp that has requested that the scan monitor be
   *        configured.
   * @param enable true to enable scan monitoring, false to disable scan
   *        monitoring.
   * @param cookie A cookie that is round-tripped back to the nanoapp to
   *        provide a context when making the request.
   *
   * @return true if the request was accepted. The result is delivered
   *         asynchronously through a CHRE event.
   */
  bool configureScanMonitor(Nanoapp *nanoapp, bool enable, const void *cookie);

  /**
   * Handles a nanoapp's request for RTT ranging against a set of devices.
   *
   * @param rangingType Specifies if ranging is desired for a single NAN device
   *        or an AP (access point) ranging request for a list of devices.
   * @param nanoapp Nanoapp issuing the request.
   * @param params Non-null pointer to parameters, supplied by the nanoapp via
   *        chreWifiRequestRangingAsync() or chreWifiNanRequestRangingAsync().
   * @param cookie Opaque pointer supplied by the nanoapp and passed back in the
   *        async result.
   *
   * @return true if the request was accepted. The result is delivered
   *         asynchronously through a CHRE event.
   */
  bool requestRanging(RangingType rangingType, Nanoapp *nanoapp,
                      const void *params, const void *cookie);

  /**
   * Performs an active wifi scan.
   *
   * This is currently a 1:1 mapping into the PAL. If more than one nanoapp
   * requests an active wifi scan, this will be an assertion failure for
   * debug builds and a no-op in production (ie: subsequent requests are
   * ignored).
   *
   * @param nanoapp The nanoapp that has requested an active wifi scan.
   * @param params Non-null pointer to the scan parameters structure
   *        supplied by the nanoapp.
   * @param cookie A cookie that is round-tripped back to the nanoapp to
   *        provide a context when making the request.
   * @return true if the request was accepted. The result is delivered
   *         asynchronously through a CHRE event.
   */
  bool requestScan(Nanoapp *nanoapp, const chreWifiScanParams *params,
                   const void *cookie);

  /**
   * Subscribe to a NAN service.
   *
   * @param nanoapp The nanoapp that has requested a service subscription.
   * @param config Service-specific nanoapp subscription configuration
   *        parameters.
   * @param cookie A cookie that is round-tripped back to the nanoapp to provide
   *        a context when making the request.
   * @return true if a subscription request was successful. The result is
   *         provided asynchronously through a CHRE event.
   */
  bool nanSubscribe(Nanoapp *nanoapp,
                    const struct chreWifiNanSubscribeConfig *config,
                    const void *cookie);

  /**
   * Cancel a NAN subscription.
   *
   * @param nanoapp The nanoapp that has requested a subscription cancelation.
   * @param subscriptionId The subscription ID assigned by the NAN engine for
   *        the original subscription request.
   *        cancelation request was successful.
   * @return true if the cancelation was successful, false otherwise.
   */
  bool nanSubscribeCancel(Nanoapp *nanoapp, uint32_t subscriptionId);

  /**
   * Passes the result of an RTT ranging request on to the requesting nanoapp.
   *
   * @param errorCode Value from enum chreError
   * @param event Event containing ranging results, or null if errorCode is not
   *        chreError
   */
  void handleRangingEvent(uint8_t errorCode,
                          struct chreWifiRangingEvent *event);

  /**
   * Handles the result of a request to PlatformWifi to change the state of the
   * scan monitor.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanMonitorStateChange(bool enabled, uint8_t errorCode);

  /**
   * Handles the result of a request to the PlatformWifi to request an active
   * Wifi scan.
   *
   * @param pending The result of the request was successful and the results
   *        be sent via the handleScanEvent method.
   * @param errorCode an error code that is used to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanResponse(bool pending, uint8_t errorCode);

  /**
   * Handles a CHRE wifi scan event.
   *
   * @param event The wifi scan event provided to the wifi request manager. This
   *        memory is guaranteed not to be modified until it has been explicitly
   *        released through the PlatformWifi instance.
   */
  void handleScanEvent(struct chreWifiScanEvent *event);

  /**
   * Updates the NAN availability state.
   *
   * @param available Whether NAN is available to use.
   */
  void updateNanAvailability(bool available);

  /**
   * Handles a NAN service identifier event. This event is the asynchronous
   * result of a NAN subscription request by a nanoapp.
   *
   * @param errorCode CHRE_ERROR_NONE if the NAN engine was able to successfully
   *        assign an ID to the subscription request, an appropriate error code
   *        from @ref enum chreError otherwise.
   * @param subscriptionId The ID assigned by the NAN engine to the subscription
   *        request. Note that this argument is invalid if the errorCode is not
   *        CHRE_ERROR_NONE.
   */
  void handleNanServiceIdentifierEvent(uint8_t errorCode,
                                       uint32_t subscriptionId);

  /**
   * Handles a NAN service discovery event. This event is invoked when a NAN
   * publisher was found that conforms to the configuration parameters in the
   * service subscription request.
   *
   * @param event Structure that contains information specific to the publisher
   *        that was discovered.
   */
  void handleNanServiceDiscoveryEvent(struct chreWifiNanDiscoveryEvent *event);

  /**
   * Handles a NAN service lost event that is initiated when a publisher has
   * disappeared.
   *
   * @param subscriptionId The subscriber to notify of the publisher's
   *        disappearance.
   * @param publisherId The publisher who has gone away.
   */
  void handleNanServiceLostEvent(uint32_t subscriptionId, uint32_t publisherId);

  /**
   * Handles a NAN service terminated event.
   *
   * @param errorCode A value in @ref enum chreError that indicates the reason
   *        for the termination.
   * @param subscriptionId The ID of the subscriber who should be notified of
   *        the service termination.
   */
  void handleNanServiceTerminatedEvent(uint8_t errorCode,
                                       uint32_t subscriptionId);

  /**
   * Handles a NAN service subscription cancelation event.
   *
   * @param errorCode An error code from enum chreError, with CHRE_ERROR_NONE
   *        indicating successfully canceling a subscription.
   * @param subscriptionId The ID of the subscribe session which has now been
   *        canceled.
   */
  void handleNanServiceSubscriptionCanceledEvent(uint8_t errorCode,
                                                 uint32_t subscriptionId);

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   *     into one of the buffers.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump) const;

  /**
   * Invoked when the host notifies CHRE that there has been a change in the
   * WiFi access via the user settings.
   *
   * @param setting The setting that changed.
   * @param enabled Whether setting is enabled or not.
   */
  void onSettingChanged(Setting setting, bool enabled);

  /**
   * Disables pending scan monitoring and NAN subscription for a nanoapp
   *
   * @param nanoapp A non-null pointer to the nanoapp.
   *
   * @return The number of subscriptions disabled.
   */
  uint32_t disableAllSubscriptions(Nanoapp *nanoapp);

  /**
   * Get the number of current active NAN subscriptions.
   *
   * @return Number of active NAN subscriptions.
   */
  size_t getNumNanSubscriptions() const {
    return mNanoappSubscriptions.size();
  }

 private:
  struct PendingRequestBase {
    uint16_t nanoappInstanceId;  //!< ID of the Nanoapp issuing this request
    const void *cookie;          //!< User data supplied by the nanoapp

    PendingRequestBase() = default;
    PendingRequestBase(uint16_t nanoappInstanceId_, const void *cookie_)
        : nanoappInstanceId(nanoappInstanceId_), cookie(cookie_) {}
  };

  struct PendingRangingRequestBase : public PendingRequestBase {
    RangingType type;
  };

  struct PendingNanSubscribeRequest : public PendingRequestBase {
    uint8_t type;
    Buffer<char> service;
    Buffer<uint8_t> serviceSpecificInfo;
    Buffer<uint8_t> matchFilter;
  };

  /**
   * Structure used to store ranging target information in the ranging
   * requests pending queue. Since NAN and AP ranging target params are
   * heterogeneous structures (NAN ranging params is a small subset of an AP
   * ranging target), both structures are included in the pending request
   * with the appropriate structure populated based on the ranging type.
   */
  struct PendingRangingRequest : public PendingRangingRequestBase {
    //! If the request was queued, a variable-length list of devices to
    //! perform ranging against (used to reconstruct chreWifiRangingParams).
    Buffer<struct chreWifiRangingTarget> targetList;

    //! Structure which contains the MAC address of a peer NAN device with
    //! which ranging is desired.
    struct chreWifiNanRangingParams nanRangingParams;
  };

  struct PendingScanMonitorRequest : public PendingRequestBase {
    bool enable;  //!< Requested scan monitor state
  };

  struct PendingScanRequest : public PendingRequestBase {
    struct chreWifiScanParams scanParams;

    PendingScanRequest(uint16_t nanoappInstanceId_, const void *cookie_,
                       const struct chreWifiScanParams *scanParams_)
        : PendingRequestBase(nanoappInstanceId_, cookie_),
          scanParams(*scanParams_) {}
  };

  //! An internal struct to hold scan request data for logging
  struct WifiScanRequestLog {
    WifiScanRequestLog(Nanoseconds timestampIn, uint16_t instanceIdIn,
                       chreWifiScanType scanTypeIn, Milliseconds maxScanAgeMsIn)
        : timestamp(timestampIn),
          instanceId(instanceIdIn),
          scanType(scanTypeIn),
          maxScanAgeMs(maxScanAgeMsIn) {}

    Nanoseconds timestamp;
    uint16_t instanceId;
    enum chreWifiScanType scanType;
    Milliseconds maxScanAgeMs;
  };

  struct NanoappNanSubscriptions {
    uint16_t nanoappInstanceId;
    uint32_t subscriptionId;

    NanoappNanSubscriptions(uint16_t nappId, uint32_t subId)
        : nanoappInstanceId(nappId), subscriptionId(subId) {}
  };

  enum class PendingNanConfigType { UNKNOWN, ENABLE, DISABLE };

  static constexpr size_t kMaxPendingScanRequest = 4;
  static constexpr size_t kMaxScanMonitorStateTransitions = 8;
  static constexpr size_t kMaxPendingRangingRequests = 4;
  static constexpr size_t kMaxPendingNanSubscriptionRequests = 4;

  PlatformWifi mPlatformWifi;

  //! The queue of state transition requests for the scan monitor. Only one
  //! asynchronous scan monitor state transition can be in flight at one time.
  //! Any further requests are queued here.
  ArrayQueue<PendingScanMonitorRequest, kMaxScanMonitorStateTransitions>
      mPendingScanMonitorRequests;

  //! The queue of scan request. Only one asynchronous scan monitor state
  //! transition can be in flight at one time. Any further requests are queued
  //! here.
  ArrayQueue<PendingScanRequest, kMaxPendingScanRequest> mPendingScanRequests;

  //! The list of nanoapps who have enabled scan monitoring. This list is
  //! maintained to ensure that nanoapps are always subscribed to wifi scan
  //! results as requested. Note that a request for wifi scan monitoring can
  //! exceed the duration of a single active wifi scan request. This makes it
  //! insuitable only subscribe to wifi scan events when an active request is
  //! made and the scan monitor must remain enabled when an active request has
  //! completed.
  DynamicVector<uint16_t> mScanMonitorNanoapps;

  //! The list of nanoapps that have an active NAN subscription. The pair
  //! format that is used is <subscriptionId, nanoappInstanceId>.
  DynamicVector<NanoappNanSubscriptions> mNanoappSubscriptions;

  //! This is set to true if the results of an active scan request are pending.
  bool mScanRequestResultsArePending = false;

  //! Accumulates the number of scan event results to determine when the last
  //! in a scan event stream has been received.
  uint8_t mScanEventResultCountAccumulator = 0;

  bool mNanIsAvailable = false;
  bool mNanConfigRequestToHostPending = false;
  PendingNanConfigType mNanConfigRequestToHostPendingType =
      PendingNanConfigType::UNKNOWN;

  //! Tracks the in-flight ranging request and any others queued up behind it
  ArrayQueue<PendingRangingRequest, kMaxPendingRangingRequests>
      mPendingRangingRequests;

  //! Tracks pending NAN subscribe requests.
  ArrayQueue<PendingNanSubscribeRequest, kMaxPendingNanSubscriptionRequests>
      mPendingNanSubscribeRequests;

  //! List of most recent wifi scan request logs
  static constexpr size_t kNumWifiRequestLogs = 10;
  ArrayQueue<WifiScanRequestLog, kNumWifiRequestLogs> mWifiScanRequestLogs;

  //! Manages the timer when a ranging request is dispatched to the PAL.
  TimerHandle mRequestRangingTimeoutHandle;

  //! Manages the timer that starts when a configure scan monitor request is
  //! dispatched to the PAL.
  TimerHandle mConfigureScanMonitorTimeoutHandle;

  //! Manages the timer that starts when a configure scan request is dispatched
  //! to the PAL.
  TimerHandle mScanRequestTimeoutHandle = CHRE_TIMER_INVALID;

  //! System time when the last WiFi scan event was received.
  Milliseconds mLastScanEventTime;

  //! ErrorCode Histogram for collected errors, the index of this array
  //! corresponds to the type of the errorcode
  uint32_t mScanMonitorErrorHistogram[CHRE_ERROR_SIZE] = {0};
  uint32_t mActiveScanErrorHistogram[CHRE_ERROR_SIZE] = {0};

  /**
   * @return true if the scan monitor is enabled by any nanoapps.
   */
  bool scanMonitorIsEnabled() const;

  /**
   * Check if a nanoapp already has a pending scan request.
   *
   * @param instanceId the instance ID of the nanoapp.
   * @return true if the nanoapp already has a pending scan request in queue.
   */
  bool nanoappHasPendingScanRequest(uint16_t instanceId) const;

  /**
   * @param instanceId the instance ID of the nanoapp.
   * @param index an optional pointer to a size_t to populate with the index of
   *        the nanoapp in the list of nanoapps.
   *
   * @return true if the nanoapp has an active request for scan monitoring.
   */
  bool nanoappHasScanMonitorRequest(uint16_t instanceId,
                                    size_t *index = nullptr) const;

  /**
   * Returns whether the nanoapp has a pending activation for scan monitoring.
   *
   * @param instanceId the instance ID of the nanoapp.
   *
   * @return whether the nanoapp has a pending request for scan monitoring.
   */
  bool nanoappHasPendingScanMonitorRequest(uint16_t instanceId) const;

  /**
   * @param requestedState The requested state to compare against.
   * @param nanoappHasRequest The requesting nanoapp has an existing request.
   *
   * @return true if the scan monitor is in the requested state.
   */
  bool scanMonitorIsInRequestedState(bool requestedState,
                                     bool nanoappHasRequest) const;

  /**
   * @param requestedState The requested state to compare against.
   * @param nanoappHasRequest The requesting nanoapp has an existing request.
   *
   * @return true if a state transition is required to reach the requested
   * state.
   */
  bool scanMonitorStateTransitionIsRequired(bool requestedState,
                                            bool nanoappHasRequest) const;

  /**
   * Builds a scan monitor state transition and adds it to the queue of incoming
   * requests.
   * @param nanoapp A non-null pointer to a nanoapp that is requesting the
   *        change.
   * @param enable The target requested scan monitoring state.
   * @param cookie The pointer cookie passed in by the calling nanoapp to return
   *        to the nanoapp when the request completes.
   *
   * @return true if the request is enqueued or false if the queue is full.
   */
  bool addScanMonitorRequestToQueue(Nanoapp *nanoapp, bool enable,
                                    const void *cookie);

  /**
   * Adds a nanoapp to the list of nanoapps that are monitoring for wifi scans.
   * @param enable true if enabling scan monitoring.
   * @param instanceId The instance ID of the scan monitoring nanoapp.
   *
   * @return true if the nanoapp was added to the list.
   */
  bool updateNanoappScanMonitoringList(bool enable, uint16_t instanceId);

  /**
   * Posts an event to a nanoapp indicating the result of a wifi scan monitoring
   * configuration change.
   *
   * @param nanoappInstanceId The nanoapp instance ID to direct the event to.
   * @param success If the request for a wifi resource was successful.
   * @param enable The target state of the request. If enable is set to false
   *        and the request was successful, the nanoapp is removed from the
   *        list of nanoapps requesting scan monitoring.
   * @param errorCode The error code when success is set to false.
   * @param cookie The cookie to be provided to the nanoapp. This is
   *        round-tripped from the nanoapp to provide context.
   *
   * @return true if the event was successfully posted to the event loop.
   */
  bool postScanMonitorAsyncResultEvent(uint16_t nanoappInstanceId, bool success,
                                       bool enable, uint8_t errorCode,
                                       const void *cookie);

  /**
   * Calls through to postScanMonitorAsyncResultEvent but invokes the
   * FATAL_ERROR macro if the event is not posted successfully. This is used in
   * asynchronous contexts where a nanoapp could be stuck waiting for a response
   * but CHRE failed to enqueue one. For parameter details,
   * @see postScanMonitorAsyncResultEvent
   */
  void postScanMonitorAsyncResultEventFatal(uint16_t nanoappInstanceId,
                                            bool success, bool enable,
                                            uint8_t errorCode,
                                            const void *cookie);

  /**
   * Posts an event to a nanoapp indicating the result of a request for an
   * active wifi scan.
   *
   * @param nanoappInstanceId The nanoapp instance ID to direct the event to.
   * @param success If the request for a wifi resource was successful.
   * @param errorCode The error code when success is set to false.
   * @param cookie The cookie to be provided to the nanoapp. This is
   *        round-tripped from the nanoapp to provide context.
   *
   * @return true if the event was successfully posted to the event loop.
   */
  bool postScanRequestAsyncResultEvent(uint16_t nanoappInstanceId, bool success,
                                       uint8_t errorCode, const void *cookie);

  /**
   * Calls through to postScanRequestAsyncResultEvent but invokes the
   * FATAL_ERROR macro if the event is not posted successfully. This is used in
   * asynchronous contexts where a nanoapp could be stuck waiting for a response
   * but CHRE failed to enqueue one. For parameter details,
   * @see postScanRequestAsyncResultEvent
   */
  void postScanRequestAsyncResultEventFatal(uint16_t nanoappInstanceId,
                                            bool success, uint8_t errorCode,
                                            const void *cookie);

  /**
   * Posts a broadcast event containing the results of a wifi scan. Failure to
   * post this event is a FATAL_ERROR. This is unrecoverable as the nanoapp will
   * be stuck waiting for wifi scan results but there may be a gap.
   *
   * @param event the wifi scan event.
   */
  void postScanEventFatal(chreWifiScanEvent *event);

  /**
   * Posts an event to a nanoapp indicating the async result of a NAN operation.
   *
   * @param nanoappInstanceId Instance ID of the nanoapp to post the event to.
   * @param requestType A value in @ref enum chreWifiRequestType that indicates
   *        the type of the NAN request this event is a response to.
   * @param success true if the request was successful, false otherwise.
   * @param errorCode A value in @ref enum chreError that indicates a failure
   *        reason (if any, CHRE_ERROR_NONE indicates success) for the request.
   * @param cookie A cookie that is round-tripped back to the nanoapp to
   *        provide a context when making the request.
   */
  void postNanAsyncResultEvent(uint16_t nanoappInstanceId, uint8_t requestType,
                               bool success, uint8_t errorCode,
                               const void *cookie);

  /**
   * Handles the result of a request to PlatformWifi to change the state of the
   * scan monitor. See the handleScanMonitorStateChange method which may be
   * called from any thread. This method is intended to be invoked on the CHRE
   * event loop thread.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanMonitorStateChangeSync(bool enabled, uint8_t errorCode);

  /**
   * Handles the result of a request to PlatformWifi to perform an active WiFi
   * scan. See the handleScanResponse method which may be called from any
   * thread. This method is intended to be invoked on the CHRE event loop
   * thread.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanResponseSync(bool pending, uint8_t errorCode);

  /**
   * Handles the result of a NAN subscription request.
   *
   * @param errorCode A value in @ref enum chrError that indicates the status
   *        of the operation, with CHRE_ERROR_NONE indicating success.
   * @param subscriptionId An identifier that is assigned to the subscribing
   *        NAN service after a subscription request. This ID is only valid
   *        if the errorCode is CHRE_ERROR_NONE.
   */
  void handleNanServiceIdentifierEventSync(uint8_t errorCode,
                                           uint32_t subscriptionId);

  /**
   * Handles the result of the successful discovery of a publishing service
   * that matches the configuration specified by the subscription request.
   *
   * @param event Structure containing information specific to the publishing
   *        service. CHRE retains ownership of the memory associated with this
   *        structure until it releases it via a call to the function
   *        freeNanDiscoveryEventCallback().
   */
  void handleNanServiceDiscoveryEventSync(
      struct chreWifiNanDiscoveryEvent *event);

  /**
   * Handles the event informing CHRE that a publishing service has gone away.
   *
   * @param subscriptionId The ID of the subscribing service which will be
   *        informed of the publisher's disappearance.
   * @param publisherId The ID of the publishing service that has gone away.
   */
  void handleNanServiceLostEventSync(uint32_t subscriptionId,
                                     uint32_t publisherId);

  /**
   * Handles the event informing CHRE that a subscription has been terminated.
   *
   * @param errorCode A value in @ref enum chreError that indicates the reason
   *        for the service's subscription termination.
   * @param subscriptionId The ID of the service whose subscription has ended.
   */
  void handleNanServiceTerminatedEventSync(uint8_t errorCode,
                                           uint32_t subscriptionId);

  /**
   * Handles the event informing CHRE the result of a subscription cancelation.
   *
   * @param errorCode A value in @ref enum chreError with CHRE_ERROR_NONE
   *        indicating successful cancelation, an error code otherwise.
   * @param subscriptionId The ID of the service whose subscription has been
   *        canceled.
   */
  void handleNanServiceSubscriptionCanceledEventSync(uint8_t errorCode,
                                                     uint32_t subscriptionId);

  /**
   * Handles event informing CHRE whether NAN is available.
   *
   * @param available Whether NAN is available to use.
   */
  void handleNanAvailabilitySync(bool available);

  /**
   * Sends CHRE_EVENT_WIFI_ASYNC_RESULT for the ranging request at the head
   * of the pending queue.
   *
   * @param errorCode Indicates the overall result of the ranging operation
   *
   * @return true on success
   */
  bool postRangingAsyncResult(uint8_t errorCode);

  /**
   * Keep issuing pending configure scan monitor request to the platform in
   * queued order util a successful dispatch or the queue is empty
   */
  void dispatchQueuedConfigureScanMonitorRequests();

  /**
   * Issues the pending scan requests to the platform in queued order until one
   * dispatched successfully or the queue is empty.
   *
   * @param postAsyncResult if a dispatch failure should post a async result.
   * @return true if successfully dispatched one request.
   */
  bool dispatchQueuedScanRequests(bool postAsyncResult);

  /**
   * Issues the next pending ranging request to the platform.
   *
   * @return Result of PlatformWifi::requestRanging()
   */
  bool dispatchQueuedRangingRequest();

  /**
   * Issues the next pending NAN ranging request to the platform.
   */
  bool dispatchQueuedNanSubscribeRequest();

  /**
   * If a failure while dispatching the NAN subscribe requests, tries to
   * dispatch it again until the first one succeeds.
   */
  void dispatchQueuedNanSubscribeRequestWithRetry();

  /**
   * Processes the result of a ranging request within the context of the CHRE
   * thread.
   *
   * @param errorCode Result of the ranging operation
   * @param event On success, pointer to event data provided by platform
   */
  void handleRangingEventSync(uint8_t errorCode,
                              struct chreWifiRangingEvent *event);

  /**
   * Handles the releasing of a WiFi scan event and unsubscribes a nanoapp who
   * has made an active request for a wifi scan from WiFi scan events in the
   * future (if it has not subscribed to passive events).
   *
   * @param scanEvent The scan event to release.
   */
  void handleFreeWifiScanEvent(chreWifiScanEvent *scanEvent);

  /**
   * Adds a wifi scan request log onto list possibly kicking earliest log out
   * if full.
   *
   * @param nanoappInstanceId The instance Id of the requesting nanoapp
   * @param params The chre wifi scan params
   */
  void addWifiScanRequestLog(uint16_t nanoappInstanceId,
                             const chreWifiScanParams *params);

  /**
   * Releases a wifi event (scan, ranging, NAN discovery) after nanoapps have
   * consumed it.
   *
   * @param eventType the type of event being freed.
   * @param eventData a pointer to the scan event to release.
   */
  static void freeWifiScanEventCallback(uint16_t eventType, void *eventData);
  static void freeWifiRangingEventCallback(uint16_t eventType, void *eventData);
  static void freeNanDiscoveryEventCallback(uint16_t eventType,
                                            void *eventData);

  /**
   * Copy a NAN subscription configuration to a pending NAN subscription
   * request before dispatch.
   *
   * @param request The pending subscribe request being queued up for dispatch.
   * @param config NAN service subscription configuration parameters.
   * @return true if the copy was successful, false otherwise.
   */
  bool copyNanSubscribeConfigToRequest(
      PendingNanSubscribeRequest &request,
      const struct chreWifiNanSubscribeConfig *config);

  /**
   * Rebuild a NAN subscription configuration from a dequed subscription
   * request.
   *
   * @param request The pending NAN subscription request that was dequeued.
   * @param config The subscription configuration that is to be built from
   *        the pending request.
   */
  void buildNanSubscribeConfigFromRequest(
      const PendingNanSubscribeRequest &request,
      struct chreWifiNanSubscribeConfig *config);

  /**
   * Scan through the nanoapp-subscription ID pair list to find the nanoapp
   * that holds a subscription ID.
   *
   * @param subscriptionId The subscription ID that the nanoapp being searched
   *        for owns.
   * @return The instance ID of the nanoapp which owns the subscription ID if
   *         it was found in the list, an invalid value otherwise.
   */
  bool getNappIdFromSubscriptionId(uint32_t subscriptionId,
                                   uint16_t *nanoappInstanceId);

  /**
   * Sends an AP (access point) or NAN ranging request to the platform.
   *
   * @param rangingType A value in WifiRequestManager::RangingType that denotes
   *        the type of the ranging request.
   * @param rangingParams The parameters of the ranging request.
   */
  bool requestRangingByType(RangingType rangingType, const void *rangingParams);

  /**
   * Update a ranging request with the provided ranging parameters.
   *
   * @param rangingType A value in WifiRequestManager::RangingType that denotes
   *        the type of the ranging request.
   * @param request A pending ranging request which needs to be updated with the
   *        provided ranging parameters.
   * @param rangingParams The parameters of the ranging request.
   */
  bool updateRangingRequest(RangingType rangingType,
                            PendingRangingRequest &request,
                            const void *rangingParams);

  /**
   * Send a pending AP or NAN ranging request to the platform.
   *
   * @param request A pending ranging request which needs to be updated with the
   *        provided ranging parameters.
   * @return true if the request was successfully sent, false otherwise.
   */
  bool sendRangingRequest(PendingRangingRequest &request);

  /**
   * Helper function to determine if all the settings required for a ranging
   * request (viz. Location, WiFi-available) are enabled.
   *
   * @return true if the necessary settings are enabled, false otherwise.
   */
  bool areRequiredSettingsEnabled();

  /**
   * Helper function to cancel all existing nanoapp NAN subscriptions and
   * inform the nanoapps owning the subscriptions of the cancelation with
   * a NAN session terminated event.
   */
  void cancelNanSubscriptionsAndInformNanoapps();

  /**
   * Helper function to cancel all pending NAN subscription requests and
   * inform the nanoapps making the request of the cancelation with a WiFi
   * async result event.
   */
  void cancelNanPendingRequestsAndInformNanoapps();

  /**
   * Sends a config request to the host to enable or disable NAN functionality.
   * The function checks if a request is already pending or if the pending
   * request type opposes the current request and only then sends the request
   * across to avoid duplicate requests.
   *
   * @param enable Indicates if a NAN enable or disable is being requested.
   */
  void sendNanConfiguration(bool enable);

  /**
   * Invoked on no response for a configure scan monitor request in the expected
   * window.
   */
  void handleConfigureScanMonitorTimeout();

  /**
   * Sets up the system timer that invokes handleConfigureScanMonitorTimeout
   * when the PAL does not respond to configure scan monitor request on time.
   *
   * @return TimerHandle that can be used later to cancel the timer if the PAL
   * has responded in the expected time window.
   */
  TimerHandle setConfigureScanMonitorTimer();

  /**
   * Invoked on no response for a ranging request in the expected window.
   */
  void handleRangingRequestTimeout();

  /**
   * Sets up the system timer that invokes handleRangingRequestTimeout when the
   * PAL does not respond on time.
   *
   * @return TimerHandle that can be used later to cancel the timer if the PAL
   * has responded in the expected time window.
   */
  TimerHandle setRangingRequestTimer();

  /**
   * Invoked on no response for a scan request in the expected window.
   */
  void handleScanRequestTimeout();

  /**
   * Sets up the system timer that invokes handleScanRequestTimeout when the
   * PAL does not respond on time.
   *
   * @return TimerHandle that can be used later to cancel the timer if the PAL
   * has responded in the expected time window.
   */
  TimerHandle setScanRequestTimer();
};

}  // namespace chre

#endif  // CHRE_CORE_WIFI_REQUEST_MANAGER_H_
