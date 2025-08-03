/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_CORE_BLE_REQUEST_MANAGER_H_
#define CHRE_CORE_BLE_REQUEST_MANAGER_H_

#include "chre/core/ble_request.h"
#include "chre/core/ble_request_multiplexer.h"
#include "chre/core/nanoapp.h"
#include "chre/core/settings.h"
#include "chre/core/timer_pool.h"
#include "chre/platform/platform_ble.h"
#include "chre/platform/system_time.h"
#include "chre/util/array_queue.h"
#include "chre/util/non_copyable.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/time.h"

namespace chre {

/**
 * Manages requests for ble resources from nanoapps and multiplexes these
 * requests into the platform-specific implementation of the ble subsystem.
 */
class BleRequestManager : public NonCopyable {
 public:
  /**
   * Initializes the underlying platform-specific BLE module. Must be called
   * prior to invoking any other methods in this class.
   */
  void init();

  /**
   * @return the BLE capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  /**
   * @return the BLE filter capabilities exposed by this platform.
   */
  uint32_t getFilterCapabilities();

  /**
   * Begins a BLE scan asynchronously. The result is delivered through a
   * CHRE_EVENT_BLE_ASYNC_RESULT event.
   *
   * @param nanoapp The nanoapp starting the request.
   * @param mode Scanning mode selected among enum chreBleScanMode
   * @param reportDelayMs Maximum requested batching delay in ms. 0 indicates no
   *                      batching. Note that the system may deliver results
   *                      before the maximum specified delay is reached.
   * @param filter Pointer to the requested best-effort filter configuration as
   *               defined by struct chreBleScanFilterV1_9. The ownership of
   *               filter and its nested elements remains with the caller, and
   *               the caller may release it as soon as
   *               chreBleStartScanAsyncV1_9() returns.
   * @param cookie The cookie to be provided to the nanoapp. This is
   *               round-tripped from the nanoapp to provide context.
   * @return true if scan was successfully enabled.
   */
  bool startScanAsync(Nanoapp *nanoapp, chreBleScanMode mode,
                      uint32_t reportDelayMs,
                      const struct chreBleScanFilterV1_9 *filter,
                      const void *cookie);

  /**
   * End a BLE scan asynchronously. The result is delivered through a
   * CHRE_EVENT_BLE_ASYNC_RESULT event.
   *
   * @param nanoapp The nanoapp stopping the request.
   * @param cookie A cookie that is round-tripped back to the nanoapp to
   *               provide a context when making the request.
   * @return whether the scan was successfully ended.
   */
  bool stopScanAsync(Nanoapp *nanoapp, const void *cookie);

#ifdef CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
  /**
   * Requests to read the RSSI of a peer device on the given LE connection
   * handle.
   *
   * If the request is accepted, the response will be delivered in a
   * CHRE_EVENT_BLE_RSSI_READ event with the same cookie.
   *
   * The request may be rejected if resources are not available to service the
   * request (such as if too many outstanding requests already exist). If so,
   * the client may retry later.
   *
   * Note that the connectionHandle is valid only while the connection remains
   * active. If a peer device disconnects then reconnects, the handle may
   * change. BluetoothGatt#getAclHandle() can be used from the Android framework
   * to get the latest handle upon reconnection.
   *
   * @param connectionHandle
   * @param cookie An opaque value that will be included in the chreAsyncResult
   *               embedded in the response to this request.
   * @return True if the request has been accepted and dispatched to the
   *         controller. False otherwise.
   *
   * @since v1.8
   *
   */
  bool readRssiAsync(Nanoapp *nanoapp, uint16_t connectionHandle,
                     const void *cookie);
#endif

  /**
   * Initiates a flush operation where all batched advertisement events will be
   * immediately processed and delivered. The nanoapp must have an existing
   * active BLE scan.
   *
   * @param nanoapp the nanoapp requesting the flush operation.
   * @param cookie the cookie value stored with the request.
   * @return true if the request has been accepted and dispatched to the
   *         controller. false otherwise.
   */
  bool flushAsync(Nanoapp *nanoapp, const void *cookie);

  /**
   * Disables active scan for a nanoapp (no-op if no active scan).
   *
   * @param nanoapp A non-null pointer to the nanoapp.
   * @return the number of scans cancelled (1 or 0).
   */
  uint32_t disableActiveScan(const Nanoapp *nanoapp);

  /**
   * Frees an advertising event that was previously provided to the BLE
   * manager.
   *
   * @param event the event to release.
   */
  void handleFreeAdvertisingEvent(struct chreBleAdvertisementEvent *event);

  /**
   * Releases BLE Advertising Event after nanoapps have processed it.
   *
   * @param eventType the type of event being freed.
   * @param eventData a pointer to the scan event to release.
   */
  static void freeAdvertisingEventCallback(uint16_t eventType, void *eventData);

  /**
   * Handles a CHRE BLE advertisement event.
   *
   * @param event The BLE advertisement event containing BLE advertising
   *              reports. This memory is guaranteed not to be modified until it
   *              has been explicitly released through the PlatformBle instance.
   */
  void handleAdvertisementEvent(struct chreBleAdvertisementEvent *event);

  /**
   * Handles the result of a request to the PlatformBle to enable or end a scan.
   *
   * @param enable true if the scan is being enabled, false if not.
   * @param errorCode an error code that is used to indicate success or what
   *                  type of error has occurred. See chreError enum in the CHRE
   *                  API for additional details.
   */
  void handlePlatformChange(bool enable, uint8_t errorCode);

  /**
   * Invoked as a result of a requestStateResync() callback from the BLE PAL.
   * Runs asynchronously in the context of the callback immediately.
   */
  void handleRequestStateResyncCallback();

#ifdef CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
  /**
   * Handles a readRssi response from the BLE PAL.
   *
   * @param errorCode error code from enum chreError, with CHRE_ERROR_NONE
   *        indicating a successful response.
   * @param connectionHandle the handle upon which the RSSI was read
   * @param rssi the RSSI read, if successful
   */
  void handleReadRssi(uint8_t errorCode, uint16_t connectionHandle,
                      int8_t rssi);
#endif

  /**
   * Handler for the flush complete operation. Called when a flush operation is
   * complete. Processes in an asynchronous manner.
   *
   * @param errorCode the error code from the flush operation.
   */
  void handleFlushComplete(uint8_t errorCode);

  //! Timeout handler for the flush operation. Called on a timeout.
  void handleFlushCompleteTimeout();

  /**
   * Retrieves the current scan status.
   *
   * @param status A non-null pointer to where the scan status will be
   *               populated.
   *
   * @return True if the status was obtained successfully.
   */
  bool getScanStatus(struct chreBleScanStatus *status);

  /**
   * Invoked when the host notifies CHRE that ble access has been
   * disabled via the user settings.
   *
   * @param setting The setting that changed.
   * @param enabled Whether setting is enabled or not.
   */
  void onSettingChanged(Setting setting, bool enabled);

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   *     into one of the buffers.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump) const;

 private:
  friend class BleRequestManagerTest;

  //! An internal structure to store incoming sensor flush requests
  struct FlushRequest {
    FlushRequest(uint16_t id, const void *cookiePtr)
        : nanoappInstanceId(id), cookie(cookiePtr) {}

    //! The timestamp at which this request should complete.
    Nanoseconds deadlineTimestamp =
        SystemTime::getMonotonicTime() +
        Nanoseconds(CHRE_BLE_FLUSH_COMPLETE_TIMEOUT_NS);
    //! The ID of the nanoapp that requested the flush.
    uint16_t nanoappInstanceId;
    //! The opaque pointer provided in flushAsync().
    const void *cookie;
    //! True if this flush request is active and is pending completion.
    bool isActive = false;
  };

  // Multiplexer used to keep track of BLE requests from nanoapps.
  BleRequestMultiplexer mRequests;

  // The platform BLE interface.
  PlatformBle mPlatformBle;

  // Expected platform state after completion of async platform request.
  BleRequest mPendingPlatformRequest;

  // Current state of the platform.
  BleRequest mActivePlatformRequest;

  // True if a platform request is in progress.
  bool mPlatformRequestInProgress;

  // True if a state resync request is pending to be processed.
  bool mResyncPending;

  // True if a setting change request is pending to be processed.
  bool mSettingChangePending;

  //! A queue of flush requests made by nanoapps.
  static constexpr size_t kMaxFlushRequests = 16;
  ArrayQueue<FlushRequest, kMaxFlushRequests> mFlushRequestQueue;

  //! The timer handle for the flush operation. Used to track a flush timeout.
  TimerHandle mFlushRequestTimerHandle = CHRE_TIMER_INVALID;

#ifdef CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
  // A pending request from a nanoapp
  struct BleReadRssiRequest {
    uint16_t instanceId;
    uint16_t connectionHandle;
    const void *cookie;
  };

  // RSSI requests that have been accepted by the framework. The first entry (if
  // present) has been dispatched to the PAL, and subsequent entries are queued.
  static constexpr size_t kMaxPendingRssiRequests = 2;
  ArrayQueue<BleReadRssiRequest, kMaxPendingRssiRequests> mPendingRssiRequests;
#endif

  // Struct to hold ble request data for logging
  struct BleRequestLog {
    BleRequestLog(Nanoseconds timestamp_, uint32_t instanceId_, bool enable_,
                  bool compliesWithBleSetting_)
        : timestamp(timestamp_),
          instanceId(instanceId_),
          enable(enable_),
          compliesWithBleSetting(compliesWithBleSetting_) {}
    void populateRequestData(const BleRequest &req) {
      mode = req.getMode();
      reportDelayMs = req.getReportDelayMs();
      rssiThreshold = req.getRssiThreshold();
      scanFilterCount = static_cast<uint8_t>(req.getGenericFilters().size());
      broadcasterFilterCount =
          static_cast<uint8_t>(req.getBroadcasterFilters().size());
    }
    Nanoseconds timestamp;
    uint32_t instanceId;
    bool enable;
    bool compliesWithBleSetting;
    chreBleScanMode mode;
    uint32_t reportDelayMs;
    int8_t rssiThreshold;
    uint8_t scanFilterCount;
    uint8_t broadcasterFilterCount;
  };

  // List of most recent ble request logs
  static constexpr size_t kNumBleRequestLogs = 10;
  ArrayQueue<BleRequestLog, kNumBleRequestLogs> mBleRequestLogs;

  /**
   * Configures BLE platform based on the current maximal BleRequest.
   */
  bool controlPlatform();

  /**
   * Processes nanoapp requests to start and stop a scan and updates BLE
   * platform if necessary.
   *
   * @param request BLE request to start or stop scan.
   * @return true if request was successfully processed.
   */
  bool configure(BleRequest &&request);

  /**
   * Handle sending an async response if a nanoapp attempts to override an
   * existing request.
   *
   * @param instanceId Instance id of nanoapp that made the request.
   * @param hasExistingRequest Indicates whether a request exists corresponding
   * to the nanoapp instance id of the new request.
   * @param requestIndex If hasExistingRequest is true, requestIndex
   * corresponds to the index of that request.
   */
  void handleExistingRequest(uint16_t instanceId, bool *hasExistingRequest,
                             size_t *requestIndex);

  /**
   * Check whether a request is attempting to enable the BLE platform while the
   * BLE setting is disabled.
   *
   * @param instanceId Instance id of nanoapp that made the request.
   * @param enabled Whether the request should start or stop a scan.
   * @param hasExistingRequest Indicates whether a request exists corresponding
   * to the nanoapp instance id of the new request.
   * @param requestIndex If hasExistingRequest is true, requestIndex
   * corresponds to the index of that request.
   * @param cookie The cookie to be provided to the nanoapp.
   * @return true if the request does not attempt to enable the platform while
   * the BLE setting is disabled.
   */
  bool compliesWithBleSetting(uint16_t instanceId, bool enabled,
                              bool hasExistingRequest, size_t requestIndex,
                              const void *cookie);

  /**
   * Add a log to list of BLE request logs possibly pushing out the oldest log.
   *
   * @param instanceId Instance id of nanoapp that made the request.
   * @param enabled Whether the request should start or stop a scan.
   * @param requestIndex Index of request in multiplexer. Must check whether it
   * is valid range before using.
   * @param compliesWithBleSetting true if the request does not attempt to
   * enable the platform while the BLE setting is disabled.
   */
  void addBleRequestLog(uint32_t instanceId, bool enabled, size_t requestIndex,
                        bool compliesWithBleSetting);

  /**
   * Update active BLE scan requests upon successful starting or ending a scan
   * and register or unregister nanoapp for BLE broadcast events.
   *
   * @param request Scan requested by nanoapp, only valid if nanoappEnabled is
   *                true.
   * @param requestChanged Indicates when the new request resulted in a change
   *                       to the underlying maximal request
   * @param hasExistingRequest Indicates whether a request exists for the
   * corresponding nanoapp instance Id of the new request.
   * @param requestIndex If equal to mRequests.size(), indicates the request
   *                     wasn't added (perhaps due to removing a non-existent
   *                     request). Otherwise, indicates the correct index for
   *                     the request.
   * @return true if requests were successfully updated.
   */
  bool updateRequests(BleRequest &&request, bool hasExistingRequest,
                      bool *requestChanged, size_t *requestIndex);

  /**
   * Handles the result of a request to the PlatformBle to enable or end a scan.
   * This method is intended to be invoked on the CHRE event loop thread. The
   * handlePlatformChange method which may be called from any thread. For
   * parameter details,
   * @see handleAdvertisementEvent
   */
  void handlePlatformChangeSync(bool enable, uint8_t errorCode);

  /**
   * Dispatches pending BLE requests from nanoapps.
   */
  void dispatchPendingRequests();

  /**
   * Handles registering/unregistering a nanoapp to the appropriate broadcast
   * event.
   *
   * @param instanceId Nanoapp instance to send result to.
   * @param enabled Whether nanoapp was enabled or disabled for BLE events.
   * @param success Whether the request was processed by the PAL successfully.
   * @param forceUnregister Whether the nanoapp should be force unregistered
   *                        from BLE broadcast events.
   */
  void handleNanoappEventRegistration(uint16_t instanceId, bool enabled,
                                      bool success, bool forceUnregister);

  /**
   * Handles an async result, sending the result to the requesting nanoapp and
   * registering/unregistering it from the appropriate broadcast
   *
   * @param instanceId Nanoapp instance to send result to.
   * @param enabled Whether nanoapp was enabled or disabled for BLE events.
   * @param success Whether the request was processed by the PAL successfully
   * @param errorCode Error code resulting from the request
   * @param forceUnregister Whether the nanoapp should be force unregistered
   * @param cookie The cookie to be provided to the nanoapp. This is
   *               round-tripped from the nanoapp to provide context.
   *                        from BLE broadcast events.
   */
  void handleAsyncResult(uint16_t instanceId, bool enabled, bool success,
                         uint8_t errorCode, const void *cookie,
                         bool forceUnregister = false);

  /**
   * Invoked as a result of a requestStateResync() callback from the BLE PAL.
   * Runs in the context of the CHRE thread.
   */
  void handleRequestStateResyncCallbackSync();

  /**
   * Updates the platform BLE request according to the current state. It should
   * be used to synchronize the BLE to the desired state, e.g. for setting
   * changes or handling a state resync request.
   *
   * @param forceUpdate if true force the platform BLE request to be made.
   */
  void updatePlatformRequest(bool forceUpdate = false);

  /**
   * Helper function for flush complete handling in all cases - normal and
   * timeout. This function defers a call to handleFlushCompleteSync.
   *
   * @param errorCode the error code for the flush operation.
   */
  void handleFlushCompleteInternal(uint8_t errorCode);

  /**
   * Synchronously processed a flush complete operation. Starts a new flush
   * operation if there is one in the queue. Properly sends the flush complete
   * event.
   *
   * @param errorCode the error code for the flush operation.
   */
  void handleFlushCompleteSync(uint8_t errorCode);

  /**
   * Sends the flush request to the controller if there is a non-active flush
   * request in the flush request queue. Sets the timer callback to handle
   * timeouts.
   *
   * @return the error code, chreError enum (CHRE_ERROR_NONE for success).
   */
  uint8_t doFlushRequest();

  /**
   * Sends the flush complete event or aborts CHRE.
   *
   * @param flushRequest the current active flush request.
   * @param errorCode the error code, chreError enum.
   */
  void sendFlushCompleteEventOrDie(const FlushRequest &flushRequest,
                                   uint8_t errorCode);

  /**
   * Processes flush requests in the flush request queue in order. Calls
   * doFlushRequest on the request. If an error is detected, it sends the flush
   * complete event with the error. This function continues to process requests
   * until one flush request is successfully made. Once this happens, the
   * request manager waits for a timeout or for a callback from the BLE
   * platform.
   *
   * @return true if there was one flush request that was successfully
   * initiated, false otherwise.
   */
  bool processFlushRequests();

  /**
   * Validates the parameters given to ensure that they can be issued to the
   * PAL.
   *
   * @param request BleRequest sent by a nanoapp.
   */
  static bool validateParams(const BleRequest &request);

  /**
   * Posts the result of a BLE start/stop scan request.
   *
   * @param instanceId The nanoapp instance ID that made the request.
   * @param requestType The type of BLE request the nanoapp issued.
   * @param success true if the operation was successful.
   * @param errorCode the error code as a result of this operation.
   * @param cookie The cookie to be provided to the nanoapp.
   */
  static void postAsyncResultEventFatal(uint16_t instanceId,
                                        uint8_t requestType, bool success,
                                        uint8_t errorCode, const void *cookie);

  /**
   * @return True if the given advertisement type is valid
   */
  static bool isValidAdType(uint8_t adType);

#ifdef CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
  /**
   * Handles a readRssi response from the BLE PAL.
   * Runs in the context of the CHRE thread.
   *
   * @param errorCode error code from enum chreError, with CHRE_ERROR_NONE
   *        indicating a successful response.
   * @param connectionHandle the handle upon which the RSSI was read
   * @param rssi the RSSI read, if successful
   */
  void handleReadRssiSync(uint8_t errorCode, uint16_t connectionHandle,
                          int8_t rssi);

  /**
   * Posts a CHRE_EVENT_BLE_RSSI_READ event for the first request in
   * mPendingRssiRequests with the specified errorCode and RSSI, and dequeues it
   * from the queue.
   *
   * It is assumed that a pending request exists. Note that this does not
   * dispatch the next request in the queue.
   *
   * @param errorCode the errorCode to include in the event
   * @param rssi the RSSI to include in the event
   */
  void resolvePendingRssiRequest(uint8_t errorCode, int8_t rssi);

  /**
   * Dispatches the next RSSI request in the queue, if one exists. Must only
   * be called if no request is presently outstanding (i.e. right after the
   * previous request completes, or when no previous request existed).
   *
   * If the request fails synchronously, it will be dequeued and the failure
   * event CHRE_EVENT_BLE_RSSI_READ will be sent. It will then try to
   * dispatch the next request in the queue until either a request succeeds,
   * or the queue is depleted.
   */
  void dispatchNextRssiRequestIfAny();

  /**
   * Checks BLE settings and, if enabled, issues a request to the PAL to read
   * RSSI. Returns CHRE_ERROR_FUNCTION_DISABLED if BLE is disabled and
   * CHRE_ERROR if the PAL returns an error.
   *
   * @param connectionHandle
   * @return uint8_t the error code, with CHRE_ERROR_NONE indicating success
   */
  uint8_t readRssi(uint16_t connectionHandle);
#endif

  /**
   * @return true if BLE setting is enabled.
   */
  bool bleSettingEnabled();
};

}  // namespace chre

#endif  // CHRE_CORE_BLE_REQUEST_MANAGER_H_
