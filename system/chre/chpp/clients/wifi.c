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

#include "chpp/clients/wifi.h"

#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/clients/discovery.h"
#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
#include "chpp/clients/timesync.h"
#endif
#include "chpp/common/standard_uuids.h"
#include "chpp/common/wifi.h"
#include "chpp/common/wifi_types.h"
#include "chpp/common/wifi_utils.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chre/pal/wifi.h"
#include "chre_api/chre/wifi.h"

#ifndef CHPP_WIFI_DISCOVERY_TIMEOUT_MS
#define CHPP_WIFI_DISCOVERY_TIMEOUT_MS CHPP_DISCOVERY_DEFAULT_TIMEOUT_MS
#endif

#ifndef CHPP_WIFI_MAX_TIMESYNC_AGE_NS
#define CHPP_WIFI_MAX_TIMESYNC_AGE_NS CHPP_TIMESYNC_DEFAULT_MAX_AGE_NS
#endif

#ifndef CHPP_WIFI_SCAN_RESULT_TIMEOUT_NS
#define CHPP_WIFI_SCAN_RESULT_TIMEOUT_NS \
  (CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS - CHRE_NSEC_PER_SEC)
#endif

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppDispatchWifiResponse(void *clientContext,
                                                      uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppDispatchWifiNotification(void *clientContext,
                                                          uint8_t *buf,
                                                          size_t len);
static bool chppWifiClientInit(void *clientContext, uint8_t handle,
                               struct ChppVersion serviceVersion);
static void chppWifiClientDeinit(void *clientContext);
static void chppWifiClientNotifyReset(void *clientContext);
static void chppWifiClientNotifyMatch(void *clientContext);

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Structure to maintain state for the WiFi client and its Request/Response
 * (RR) functionality.
 */
struct ChppWifiClientState {
  struct ChppEndpointState client;   // CHPP client state
  const struct chrePalWifiApi *api;  // WiFi PAL API

  struct ChppOutgoingRequestState
      outReqStates[CHPP_WIFI_CLIENT_REQUEST_MAX + 1];

  uint32_t capabilities;            // Cached GetCapabilities result
  bool scanMonitorEnabled;          // Scan monitoring is enabled
  bool scanMonitorSilenceCallback;  // Silence callback during recovery from a
                                    // service reset
  bool capabilitiesValid;  // Flag to indicate if the capabilities result
                           // is valid
};

// Note: This global definition of gWifiClientContext supports only one
// instance of the CHPP WiFi client at a time.
struct ChppWifiClientState gWifiClientContext;
static const struct chrePalSystemApi *gSystemApi;
static const struct chrePalWifiCallbacks *gCallbacks;

/**
 * Configuration parameters for this client
 */
static const struct ChppClient kWifiClientConfig = {
    .descriptor.uuid = CHPP_UUID_WIFI_STANDARD,

    // Version
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,

    // Notifies client if CHPP is reset
    .resetNotifierFunctionPtr = &chppWifiClientNotifyReset,

    // Notifies client if they are matched to a service
    .matchNotifierFunctionPtr = &chppWifiClientNotifyMatch,

    // Service response dispatch function pointer
    .responseDispatchFunctionPtr = &chppDispatchWifiResponse,

    // Service notification dispatch function pointer
    .notificationDispatchFunctionPtr = &chppDispatchWifiNotification,

    // Service response dispatch function pointer
    .initFunctionPtr = &chppWifiClientInit,

    // Service notification dispatch function pointer
    .deinitFunctionPtr = &chppWifiClientDeinit,

    // Number of request-response states in the outReqStates array.
    .outReqCount = ARRAY_SIZE(gWifiClientContext.outReqStates),

    // Min length is the entire header
    .minLength = sizeof(struct ChppAppHeader),
};

/************************************************
 *  Prototypes
 ***********************************************/

static bool chppWifiClientOpen(const struct chrePalSystemApi *systemApi,
                               const struct chrePalWifiCallbacks *callbacks);
static void chppWifiClientClose(void);
static uint32_t chppWifiClientGetCapabilities(void);
static bool chppWifiClientConfigureScanMonitor(bool enable);
static bool chppWifiClientRequestScan(const struct chreWifiScanParams *params);
static void chppWifiClientReleaseScanEvent(struct chreWifiScanEvent *event);
static bool chppWifiClientRequestRanging(
    const struct chreWifiRangingParams *params);
static void chppWifiClientReleaseRangingEvent(
    struct chreWifiRangingEvent *event);

static void chppWiFiRecoverScanMonitor(
    struct ChppWifiClientState *clientContext);
static void chppWifiCloseResult(struct ChppWifiClientState *clientContext,
                                uint8_t *buf, size_t len);
static void chppWifiGetCapabilitiesResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len);
static void chppWifiConfigureScanMonitorResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len);
static void chppWifiRequestScanResult(struct ChppWifiClientState *clientContext,
                                      uint8_t *buf, size_t len);
static void chppWifiRequestRangingResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len);
static void chppWifiRequestNanSubscribeResult(uint8_t *buf, size_t len);

static void chppWifiScanEventNotification(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len);
static void chppWifiRangingEventNotification(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len);
static void chppWifiDiscoveryEventNotification(uint8_t *buf, size_t len);
static void chppWifiNanServiceLostEventNotification(uint8_t *buf, size_t len);
static void chppWifiNanServiceTerminatedEventNotification(uint8_t *buf,
                                                          size_t len);
static void chppWifiRequestNanSubscribeNotification(uint8_t *buf, size_t len);
static void chppWifiNanSubscriptionCanceledNotification(uint8_t *buf,
                                                        size_t len);
static void chppWifiNanSubscriptionCanceledResult(uint8_t *buf, size_t len);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Dispatches a service response from the transport layer that is determined to
 * be for the WiFi client.
 *
 * This function is called from the app layer using its function pointer given
 * during client registration.
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppDispatchWifiResponse(void *clientContext,
                                                      uint8_t *buf,
                                                      size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (rxHeader->command > CHPP_WIFI_CLIENT_REQUEST_MAX) {
    error = CHPP_APP_ERROR_INVALID_COMMAND;

  } else if (!chppTimestampIncomingResponse(
                 wifiClientContext->client.appContext,
                 &wifiClientContext->outReqStates[rxHeader->command],
                 rxHeader)) {
    error = CHPP_APP_ERROR_UNEXPECTED_RESPONSE;

  } else {
    switch (rxHeader->command) {
      case CHPP_WIFI_OPEN: {
        chppClientProcessOpenResponse(&wifiClientContext->client, buf, len);
        chppWiFiRecoverScanMonitor(wifiClientContext);
        break;
      }

      case CHPP_WIFI_CLOSE: {
        chppWifiCloseResult(wifiClientContext, buf, len);
        break;
      }

      case CHPP_WIFI_GET_CAPABILITIES: {
        chppWifiGetCapabilitiesResult(wifiClientContext, buf, len);
        break;
      }

      case CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC: {
        chppWifiConfigureScanMonitorResult(wifiClientContext, buf, len);
        break;
      }

      case CHPP_WIFI_REQUEST_SCAN_ASYNC: {
        chppWifiRequestScanResult(wifiClientContext, buf, len);
        break;
      }

      case CHPP_WIFI_REQUEST_RANGING_ASYNC:
      case CHPP_WIFI_REQUEST_NAN_RANGING_ASYNC: {
        chppWifiRequestRangingResult(wifiClientContext, buf, len);
        break;
      }

      case CHPP_WIFI_REQUEST_NAN_SUB: {
        chppWifiRequestNanSubscribeResult(buf, len);
        break;
      }

      case CHPP_WIFI_REQUEST_NAN_SUB_CANCEL: {
        chppWifiNanSubscriptionCanceledResult(buf, len);
        break;
      }

      default: {
        error = CHPP_APP_ERROR_INVALID_COMMAND;
        break;
      }
    }
  }

  return error;
}

/**
 * Dispatches a service notification from the transport layer that is determined
 * to be for the WiFi client.
 *
 * This function is called from the app layer using its function pointer given
 * during client registration.
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppDispatchWifiNotification(void *clientContext,
                                                          uint8_t *buf,
                                                          size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  switch (rxHeader->command) {
    case CHPP_WIFI_REQUEST_SCAN_ASYNC: {
      chppWifiScanEventNotification(wifiClientContext, buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_RANGING_ASYNC:
    case CHPP_WIFI_REQUEST_NAN_RANGING_ASYNC: {
      chppWifiRangingEventNotification(wifiClientContext, buf, len);
      break;
    }

    case CHPP_WIFI_NOTIFICATION_NAN_SERVICE_DISCOVERY: {
      chppWifiDiscoveryEventNotification(buf, len);
      break;
    }

    case CHPP_WIFI_NOTIFICATION_NAN_SERVICE_LOST: {
      chppWifiNanServiceLostEventNotification(buf, len);
      break;
    }

    case CHPP_WIFI_NOTIFICATION_NAN_SERVICE_TERMINATED: {
      chppWifiNanServiceTerminatedEventNotification(buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_NAN_SUB: {
      chppWifiRequestNanSubscribeNotification(buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_NAN_SUB_CANCEL: {
      chppWifiNanSubscriptionCanceledNotification(buf, len);
      break;
    }

    default: {
      error = CHPP_APP_ERROR_INVALID_COMMAND;
      break;
    }
  }

  return error;
}

/**
 * Initializes the client and provides its handle number and the version of the
 * matched service when/if it the client is matched with a service during
 * discovery.
 *
 * @param clientContext Maintains status for each client instance.
 * @param handle Handle number for this client.
 * @param serviceVersion Version of the matched service.
 *
 * @return True if client is compatible and successfully initialized.
 */
static bool chppWifiClientInit(void *clientContext, uint8_t handle,
                               struct ChppVersion serviceVersion) {
  UNUSED_VAR(serviceVersion);

  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;
  chppClientInit(&wifiClientContext->client, handle);

  return true;
}

/**
 * Deinitializes the client.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppWifiClientDeinit(void *clientContext) {
  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;
  chppClientDeinit(&wifiClientContext->client);
}

/**
 * Notifies the client of an incoming reset.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppWifiClientNotifyReset(void *clientContext) {
  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;

  chppClientCloseOpenRequests(&wifiClientContext->client, &kWifiClientConfig,
                              false /* clearOnly */);
  chppCheckWifiScanEventNotificationReset();

  if (wifiClientContext->client.openState != CHPP_OPEN_STATE_OPENED &&
      !wifiClientContext->client.pseudoOpen) {
    CHPP_LOGW("WiFi client reset but wasn't open");
  } else {
    CHPP_LOGI("WiFi client reopening from state=%" PRIu8,
              wifiClientContext->client.openState);
    chppClientSendOpenRequest(&wifiClientContext->client,
                              &wifiClientContext->outReqStates[CHPP_WIFI_OPEN],
                              CHPP_WIFI_OPEN,
                              /*blocking=*/false);
  }
}

/**
 * Notifies the client of being matched to a service.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppWifiClientNotifyMatch(void *clientContext) {
  struct ChppWifiClientState *wifiClientContext =
      (struct ChppWifiClientState *)clientContext;

  if (wifiClientContext->client.pseudoOpen) {
    CHPP_LOGD("Pseudo-open WiFi client opening");
    chppClientSendOpenRequest(&wifiClientContext->client,
                              &wifiClientContext->outReqStates[CHPP_WIFI_OPEN],
                              CHPP_WIFI_OPEN,
                              /*blocking=*/false);
  }
}

/**
 * Restores the state of scan monitoring after an incoming reset.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppWiFiRecoverScanMonitor(
    struct ChppWifiClientState *clientContext) {
  if (clientContext->scanMonitorEnabled) {
    CHPP_LOGD("Re-enabling WiFi scan monitoring after reset");
    clientContext->scanMonitorEnabled = false;
    clientContext->scanMonitorSilenceCallback = true;

    if (!chppWifiClientConfigureScanMonitor(true)) {
      clientContext->scanMonitorSilenceCallback = false;
      CHPP_DEBUG_ASSERT_LOG(false, "Failed to re-enable WiFi scan monitoring");
    }
  }
}

/**
 * Handles the service response for the close client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiCloseResult(struct ChppWifiClientState *clientContext,
                                uint8_t *buf, size_t len) {
  // TODO
  UNUSED_VAR(clientContext);
  UNUSED_VAR(buf);
  UNUSED_VAR(len);
}

/**
 * Handles the service response for the get capabilities client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiGetCapabilitiesResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len) {
  if (len < sizeof(struct ChppWifiGetCapabilitiesResponse)) {
    CHPP_LOGE("Bad WiFi capabilities len=%" PRIuSIZE, len);

  } else {
    struct ChppWifiGetCapabilitiesParameters *result =
        &((struct ChppWifiGetCapabilitiesResponse *)buf)->params;

    CHPP_LOGD("chppWifiGetCapabilitiesResult received capabilities=0x%" PRIx32,
              result->capabilities);

    CHPP_ASSERT((result->capabilities & CHPP_WIFI_DEFAULT_CAPABILITIES) ==
                CHPP_WIFI_DEFAULT_CAPABILITIES);
    if (result->capabilities != CHPP_WIFI_DEFAULT_CAPABILITIES) {
      CHPP_LOGE("WiFi capabilities 0x%" PRIx32 " != 0x%" PRIx32,
                result->capabilities, CHPP_WIFI_DEFAULT_CAPABILITIES);
    }

    clientContext->capabilitiesValid = true;
    clientContext->capabilities = result->capabilities;
  }
}

/**
 * Handles the service response for the Configure Scan Monitor client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiConfigureScanMonitorResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);

  if (len < sizeof(struct ChppWifiConfigureScanMonitorAsyncResponse)) {
    // Short response length indicates an error
    gCallbacks->scanMonitorStatusChangeCallback(
        false, chppAppShortResponseErrorHandler(buf, len, "ScanMonitor"));

  } else {
    struct ChppWifiConfigureScanMonitorAsyncResponseParameters *result =
        &((struct ChppWifiConfigureScanMonitorAsyncResponse *)buf)->params;

    gWifiClientContext.scanMonitorEnabled = result->enabled;
    CHPP_LOGD(
        "chppWifiConfigureScanMonitorResult received enable=%d, "
        "errorCode=%" PRIu8,
        result->enabled, result->errorCode);

    if (!gWifiClientContext.scanMonitorSilenceCallback) {
      // Per the scanMonitorStatusChangeCallback API contract, unsolicited
      // calls to scanMonitorStatusChangeCallback must not be made, and it
      // should only be invoked as the direct result of an earlier call to
      // configureScanMonitor.
      gCallbacks->scanMonitorStatusChangeCallback(result->enabled,
                                                  result->errorCode);
    }  // Else, the WiFi subsystem has been reset and we are required to
       // silently reenable the scan monitor.

    gWifiClientContext.scanMonitorSilenceCallback = false;
  }
}

/**
 * Handles the service response for the Request Scan Result client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiRequestScanResult(struct ChppWifiClientState *clientContext,
                                      uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);

  if (len < sizeof(struct ChppWifiRequestScanResponse)) {
    // Short response length indicates an error
    gCallbacks->scanResponseCallback(
        false, chppAppShortResponseErrorHandler(buf, len, "ScanRequest"));

  } else {
    struct ChppWifiRequestScanResponseParameters *result =
        &((struct ChppWifiRequestScanResponse *)buf)->params;
    CHPP_LOGI("Scan request success=%d at service", result->pending);
    gCallbacks->scanResponseCallback(result->pending, result->errorCode);
  }
}

/**
 * Handles the service response for the Request Ranging Result client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiRequestRangingResult(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);
  UNUSED_VAR(len);

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    gCallbacks->rangingEventCallback(chppAppErrorToChreError(rxHeader->error),
                                     NULL);

  } else {
    CHPP_LOGD("Ranging request accepted at service");
  }
}

/**
 * Handles the service response for the NAN subscribe client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiRequestNanSubscribeResult(uint8_t *buf, size_t len) {
  UNUSED_VAR(len);

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    gCallbacks->nanServiceIdentifierCallback(
        chppAppErrorToChreError(rxHeader->error), 0 /* subscriptionId */);

  } else {
    CHPP_LOGD("NAN sub accepted at service");
  }
}

/**
 * Handles the service response for the NAN subscription cancel client request.
 *
 * This function is called from chppDispatchWifiResponse().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiNanSubscriptionCanceledResult(uint8_t *buf, size_t len) {
  UNUSED_VAR(len);

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    gCallbacks->nanSubscriptionCanceledCallback(
        chppAppErrorToChreError(rxHeader->error), 0 /* subscriptionId */);

  } else {
    CHPP_LOGD("NAN sub cancel accepted at service");
  }
}

/**
 * Handles the WiFi scan event service notification.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiScanEventNotification(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);
  CHPP_LOGD("chppWifiScanEventNotification received data len=%" PRIuSIZE, len);

  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct chreWifiScanEvent *chre =
      chppWifiScanEventToChre((struct ChppWifiScanEvent *)buf, len);

  if (chre == NULL) {
    CHPP_LOGE("Scan event conversion failed len=%" PRIuSIZE, len);
  } else {
#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
    uint64_t correctedTime =
        chre->referenceTime -
        (uint64_t)chppTimesyncGetOffset(gWifiClientContext.client.appContext,
                                        CHPP_WIFI_MAX_TIMESYNC_AGE_NS);
    CHPP_LOGD("WiFi scan time corrected from %" PRIu64 "to %" PRIu64,
              chre->referenceTime / CHPP_NSEC_PER_MSEC,
              correctedTime / CHPP_NSEC_PER_MSEC);
    chre->referenceTime = correctedTime;
#endif

    CHPP_DEBUG_ASSERT(chppCheckWifiScanEventNotification(chre));

    gCallbacks->scanEventCallback(chre);
  }
}

/**
 * Handles the WiFi ranging event service notification.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiRangingEventNotification(
    struct ChppWifiClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);

  CHPP_LOGD("chppWifiRangingEventNotification received data len=%" PRIuSIZE,
            len);

  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  // Timestamp correction prior to conversion to avoid const casting issues.
#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
  struct ChppWifiRangingEvent *event = (struct ChppWifiRangingEvent *)buf;

  for (size_t i = 0; i < event->resultCount; i++) {
    struct ChppWifiRangingResult *results =
        (struct ChppWifiRangingResult *)&buf[event->results.offset];

    uint64_t correctedTime =
        results[i].timestamp -
        (uint64_t)chppTimesyncGetOffset(gWifiClientContext.client.appContext,
                                        CHPP_WIFI_MAX_TIMESYNC_AGE_NS);
    CHPP_LOGD("WiFi ranging result time corrected from %" PRIu64 "to %" PRIu64,
              results[i].timestamp / CHPP_NSEC_PER_MSEC,
              correctedTime / CHPP_NSEC_PER_MSEC);
    results[i].timestamp = correctedTime;
  }
#endif

  struct chreWifiRangingEvent *chre =
      chppWifiRangingEventToChre((struct ChppWifiRangingEvent *)buf, len);

  uint8_t error = CHRE_ERROR_NONE;
  if (chre == NULL) {
    error = CHRE_ERROR;
    CHPP_LOGE("Ranging event conversion failed len=%" PRIuSIZE, len);
  }

  gCallbacks->rangingEventCallback(error, chre);
}

/**
 * Handles the NAN discovery event service notification.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiDiscoveryEventNotification(uint8_t *buf, size_t len) {
  CHPP_LOGD("chppWifiDiscoveryEventNotification data len=%" PRIuSIZE, len);

  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct ChppWifiNanDiscoveryEvent *chppEvent =
      (struct ChppWifiNanDiscoveryEvent *)buf;
  struct chreWifiNanDiscoveryEvent *event =
      chppWifiNanDiscoveryEventToChre(chppEvent, len);

  if (event == NULL) {
    CHPP_LOGE("Discovery event CHPP -> CHRE conversion failed");
  } else {
    gCallbacks->nanServiceDiscoveryCallback(event);
  }
}

/**
 * Handles the NAN connection lost event service notification.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiNanServiceLostEventNotification(uint8_t *buf, size_t len) {
  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct ChppWifiNanSessionLostEvent *chppEvent =
      (struct ChppWifiNanSessionLostEvent *)buf;
  struct chreWifiNanSessionLostEvent *event =
      chppWifiNanSessionLostEventToChre(chppEvent, len);

  if (event == NULL) {
    CHPP_LOGE("Session lost event CHPP -> CHRE conversion failed");
  } else {
    gCallbacks->nanServiceLostCallback(event->id, event->peerId);
  }
}

/**
 * Handles the NAN subscription termination event service notification.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiNanServiceTerminatedEventNotification(uint8_t *buf,
                                                          size_t len) {
  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct ChppWifiNanSessionTerminatedEvent *chppEvent =
      (struct ChppWifiNanSessionTerminatedEvent *)buf;
  struct chreWifiNanSessionTerminatedEvent *event =
      chppWifiNanSessionTerminatedEventToChre(chppEvent, len);

  if (event == NULL) {
    CHPP_LOGE("Session terminated event CHPP -> CHRE conversion failed");
  } else {
    gCallbacks->nanServiceTerminatedCallback(event->reason, event->id);
  }
}

/**
 * Handles the service response for the NAN subscribe client request.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiRequestNanSubscribeNotification(uint8_t *buf, size_t len) {
  uint8_t errorCode = CHRE_ERROR_NONE;
  uint32_t subscriptionId = 0;

  if (len < sizeof(struct ChppWifiNanServiceIdentifier)) {
    errorCode = CHRE_ERROR;
  } else {
    struct ChppWifiNanServiceIdentifier *id =
        (struct ChppWifiNanServiceIdentifier *)buf;
    errorCode = id->errorCode;
    subscriptionId = id->subscriptionId;
  }
  gCallbacks->nanServiceIdentifierCallback(errorCode, subscriptionId);
}

/**
 * Handles the service response for the NAN subscription cancel client request.
 *
 * This function is called from chppDispatchWifiNotification().
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppWifiNanSubscriptionCanceledNotification(uint8_t *buf,
                                                        size_t len) {
  uint8_t errorCode = CHRE_ERROR_NONE;
  uint32_t subscriptionId = 0;
  if (len < (sizeof(struct ChppWifiNanSubscriptionCanceledResponse))) {
    errorCode = CHRE_ERROR;
  } else {
    struct ChppWifiNanSubscriptionCanceledResponse *chppNotif =
        (struct ChppWifiNanSubscriptionCanceledResponse *)buf;
    errorCode = chppNotif->errorCode;
    subscriptionId = chppNotif->subscriptionId;
  }
  gCallbacks->nanSubscriptionCanceledCallback(errorCode, subscriptionId);
}

/**
 * Initializes the WiFi client upon an open request from CHRE and responds
 * with the result.
 *
 * @param systemApi CHRE system function pointers.
 * @param callbacks CHRE entry points.
 *
 * @return True if successful. False otherwise.
 */
static bool chppWifiClientOpen(const struct chrePalSystemApi *systemApi,
                               const struct chrePalWifiCallbacks *callbacks) {
  CHPP_DEBUG_NOT_NULL(systemApi);
  CHPP_DEBUG_NOT_NULL(callbacks);

  bool result = false;
  gSystemApi = systemApi;
  gCallbacks = callbacks;

  CHPP_LOGD("WiFi client opening");
  if (gWifiClientContext.client.appContext == NULL) {
    CHPP_LOGE("WiFi client app is null");
  } else {
    if (chppWaitForDiscoveryComplete(gWifiClientContext.client.appContext,
                                     CHPP_WIFI_DISCOVERY_TIMEOUT_MS)) {
      result = chppClientSendOpenRequest(
          &gWifiClientContext.client,
          &gWifiClientContext.outReqStates[CHPP_WIFI_OPEN], CHPP_WIFI_OPEN,
          /*blocking=*/true);
    }

    // Since CHPP_WIFI_DEFAULT_CAPABILITIES is mandatory, we can always
    // pseudo-open and return true. Otherwise, these should have been gated.
    chppClientPseudoOpen(&gWifiClientContext.client);
    result = true;
  }

  return result;
}

/**
 * Deinitializes the WiFi client.
 */
static void chppWifiClientClose(void) {
  // Remote
  struct ChppAppHeader *request = chppAllocClientRequestCommand(
      &gWifiClientContext.client, CHPP_WIFI_CLOSE);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else if (chppClientSendTimestampedRequestAndWait(
                 &gWifiClientContext.client,
                 &gWifiClientContext.outReqStates[CHPP_WIFI_CLOSE], request,
                 sizeof(*request))) {
    gWifiClientContext.client.openState = CHPP_OPEN_STATE_CLOSED;
    gWifiClientContext.capabilities = CHRE_WIFI_CAPABILITIES_NONE;
    gWifiClientContext.capabilitiesValid = false;
    chppClientCloseOpenRequests(&gWifiClientContext.client, &kWifiClientConfig,
                                true /* clearOnly */);
  }
}

/**
 * Retrieves a set of flags indicating the WiFi features supported by the
 * current implementation.
 *
 * @return Capabilities flags.
 */
static uint32_t chppWifiClientGetCapabilities(void) {
  uint32_t capabilities = CHPP_WIFI_DEFAULT_CAPABILITIES;

  if (gWifiClientContext.capabilitiesValid) {
    // Result already cached
    capabilities = gWifiClientContext.capabilities;

  } else {
    struct ChppAppHeader *request = chppAllocClientRequestCommand(
        &gWifiClientContext.client, CHPP_WIFI_GET_CAPABILITIES);

    if (request == NULL) {
      CHPP_LOG_OOM();
    } else {
      if (chppClientSendTimestampedRequestAndWait(
              &gWifiClientContext.client,
              &gWifiClientContext.outReqStates[CHPP_WIFI_GET_CAPABILITIES],
              request, sizeof(*request))) {
        // Success. gWifiClientContext.capabilities is now populated
        if (gWifiClientContext.capabilitiesValid) {
          capabilities = gWifiClientContext.capabilities;
        }
      }
    }
  }

  return capabilities;
}

/**
 * Enables/disables receiving unsolicited scan results (scan monitoring).
 *
 * @param enable True to enable.
 *
 * @return True indicates the request was sent off to the service.
 */
static bool chppWifiClientConfigureScanMonitor(bool enable) {
  bool result = false;

  struct ChppWifiConfigureScanMonitorAsyncRequest *request =
      chppAllocClientRequestFixed(
          &gWifiClientContext.client,
          struct ChppWifiConfigureScanMonitorAsyncRequest);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else {
    request->header.command = CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC;
    request->params.enable = enable;
    request->params.cookie =
        &gWifiClientContext
             .outReqStates[CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC];

    result = chppClientSendTimestampedRequestOrFail(
        &gWifiClientContext.client,
        &gWifiClientContext
             .outReqStates[CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC],
        request, sizeof(*request), CHPP_REQUEST_TIMEOUT_DEFAULT);
  }

  return result;
}

/**
 * Request that the WiFi chipset perform a scan or deliver results from its
 * cache.
 *
 * @param params See chreWifiRequestScanAsync().
 *
 * @return True indicates the request was sent off to the service.
 */
static bool chppWifiClientRequestScan(const struct chreWifiScanParams *params) {
  struct ChppWifiScanParamsWithHeader *request;
  size_t requestLen;

  bool result = chppWifiScanParamsFromChre(params, &request, &requestLen);

  if (!result) {
    CHPP_LOG_OOM();
  } else {
    request->header.handle = gWifiClientContext.client.handle;
    request->header.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
    request->header.transaction = gWifiClientContext.client.transaction++;
    request->header.error = CHPP_APP_ERROR_NONE;
    request->header.command = CHPP_WIFI_REQUEST_SCAN_ASYNC;

    CHPP_STATIC_ASSERT(
        CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS > CHPP_WIFI_SCAN_RESULT_TIMEOUT_NS,
        "Chpp wifi scan timeout needs to be smaller than CHRE wifi scan "
        "timeout");
    result = chppClientSendTimestampedRequestOrFail(
        &gWifiClientContext.client,
        &gWifiClientContext.outReqStates[CHPP_WIFI_REQUEST_SCAN_ASYNC], request,
        requestLen, CHPP_WIFI_SCAN_RESULT_TIMEOUT_NS);
  }

  return result;
}

/**
 * Releases the memory held for the scan event callback.
 *
 * @param event Location event to be released.
 */
static void chppWifiClientReleaseScanEvent(struct chreWifiScanEvent *event) {
  if (event->scannedFreqListLen > 0) {
    void *scannedFreqList = CHPP_CONST_CAST_POINTER(event->scannedFreqList);
    CHPP_FREE_AND_NULLIFY(scannedFreqList);
  }

  if (event->resultCount > 0) {
    void *results = CHPP_CONST_CAST_POINTER(event->results);
    CHPP_FREE_AND_NULLIFY(results);
  }

  CHPP_FREE_AND_NULLIFY(event);
}

/**
 * Request that the WiFi chipset perform RTT ranging.
 *
 * @param params See chreWifiRequestRangingAsync().
 *
 * @return True indicates the request was sent off to the service.
 */
static bool chppWifiClientRequestRanging(
    const struct chreWifiRangingParams *params) {
  struct ChppWifiRangingParamsWithHeader *request;
  size_t requestLen;

  bool result = chppWifiRangingParamsFromChre(params, &request, &requestLen);

  if (!result) {
    CHPP_LOG_OOM();
  } else {
    request->header.handle = gWifiClientContext.client.handle;
    request->header.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
    request->header.transaction = gWifiClientContext.client.transaction++;
    request->header.error = CHPP_APP_ERROR_NONE;
    request->header.command = CHPP_WIFI_REQUEST_RANGING_ASYNC;

    result = chppClientSendTimestampedRequestOrFail(
        &gWifiClientContext.client,
        &gWifiClientContext.outReqStates[CHPP_WIFI_REQUEST_RANGING_ASYNC],
        request, requestLen, CHRE_WIFI_RANGING_RESULT_TIMEOUT_NS);
  }

  return result;
}

/**
 * Releases the memory held for the RTT ranging event callback.
 *
 * @param event Location event to be released.
 */
static void chppWifiClientReleaseRangingEvent(
    struct chreWifiRangingEvent *event) {
  if (event->resultCount > 0) {
    void *results = CHPP_CONST_CAST_POINTER(event->results);
    CHPP_FREE_AND_NULLIFY(results);
  }

  CHPP_FREE_AND_NULLIFY(event);
}

/**
 * Request that the WiFi chipset perform a NAN subscription.
 * @see chreWifiNanSubscribe for more information.
 *
 * @param config NAN service subscription configuration.
 * @return true if subscribe request was successful, false otherwise.
 */
static bool chppWifiClientNanSubscribe(
    const struct chreWifiNanSubscribeConfig *config) {
  struct ChppWifiNanSubscribeConfigWithHeader *request;
  size_t requestLen;

  bool result =
      chppWifiNanSubscribeConfigFromChre(config, &request, &requestLen);

  if (!result) {
    CHPP_LOG_OOM();
  } else {
    request->header.handle = gWifiClientContext.client.handle;
    request->header.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
    request->header.transaction = gWifiClientContext.client.transaction++;
    request->header.error = CHPP_APP_ERROR_NONE;
    request->header.command = CHPP_WIFI_REQUEST_NAN_SUB;

    result = chppClientSendTimestampedRequestOrFail(
        &gWifiClientContext.client,
        &gWifiClientContext.outReqStates[CHPP_WIFI_REQUEST_NAN_SUB], request,
        requestLen, CHRE_ASYNC_RESULT_TIMEOUT_NS);
  }
  return result;
}

/**
 * Request the WiFi chipset to cancel a NAN subscription.
 * @param subscriptionId Identifier assigned by the NAN engine for a service
 *        subscription.
 * @return true if cancelation request was successfully dispatched, false
 *         otherwise.
 */
static bool chppWifiClientNanSubscribeCancel(uint32_t subscriptionId) {
  bool result = false;
  struct ChppWifiNanSubscribeCancelRequest *request =
      chppAllocClientRequestFixed(&gWifiClientContext.client,
                                  struct ChppWifiNanSubscribeCancelRequest);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else {
    request->header.handle = gWifiClientContext.client.handle;
    request->header.command = CHPP_WIFI_REQUEST_NAN_SUB_CANCEL;
    request->header.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
    request->header.transaction = gWifiClientContext.client.transaction++;
    request->header.error = CHPP_APP_ERROR_NONE;
    request->subscriptionId = subscriptionId;

    result = chppClientSendTimestampedRequestAndWait(
        &gWifiClientContext.client,
        &gWifiClientContext.outReqStates[CHPP_WIFI_REQUEST_NAN_SUB_CANCEL],
        request, sizeof(*request));
  }
  return result;
}

/**
 * Release the memory held for the NAN service discovery callback.
 *
 * @param event Discovery event to be freed.
 */
static void chppWifiClientNanReleaseDiscoveryEvent(
    struct chreWifiNanDiscoveryEvent *event) {
  if (event != NULL) {
    if (event->serviceSpecificInfo != NULL) {
      void *info = CHPP_CONST_CAST_POINTER(event->serviceSpecificInfo);
      CHPP_FREE_AND_NULLIFY(info);
    }
    CHPP_FREE_AND_NULLIFY(event);
  }
}

/**
 * Request that the WiFi chipset perform NAN ranging.
 *
 * @param params WiFi NAN ranging parameters.
 * @return true if the ranging request was successfully dispatched, false
 *         otherwise.
 */
static bool chppWifiClientNanRequestNanRanging(
    const struct chreWifiNanRangingParams *params) {
  struct ChppWifiNanRangingParamsWithHeader *request;
  size_t requestLen;
  bool result = chppWifiNanRangingParamsFromChre(params, &request, &requestLen);

  if (!result) {
    CHPP_LOG_OOM();
  } else {
    request->header.handle = gWifiClientContext.client.handle;
    request->header.command = CHPP_WIFI_REQUEST_NAN_RANGING_ASYNC;
    request->header.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
    request->header.transaction = gWifiClientContext.client.transaction++;
    request->header.error = CHPP_APP_ERROR_NONE;

    result = chppClientSendTimestampedRequestOrFail(
        &gWifiClientContext.client,
        &gWifiClientContext.outReqStates[CHPP_WIFI_REQUEST_NAN_RANGING_ASYNC],
        request, requestLen, CHRE_ASYNC_RESULT_TIMEOUT_NS);
  }
  return result;
}

static bool chppWifiGetNanCapabilites(
    struct chreWifiNanCapabilities *capabilities) {
  // Not implemented yet.
  UNUSED_VAR(capabilities);
  return false;
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterWifiClient(struct ChppAppState *appContext) {
  memset(&gWifiClientContext, 0, sizeof(gWifiClientContext));
  chppRegisterClient(appContext, (void *)&gWifiClientContext,
                     &gWifiClientContext.client,
                     gWifiClientContext.outReqStates, &kWifiClientConfig);
}

void chppDeregisterWifiClient(struct ChppAppState *appContext) {
  // TODO

  UNUSED_VAR(appContext);
}

struct ChppEndpointState *getChppWifiClientState(void) {
  return &gWifiClientContext.client;
}

#ifdef CHPP_CLIENT_ENABLED_WIFI

#ifdef CHPP_CLIENT_ENABLED_CHRE_WIFI
const struct chrePalWifiApi *chrePalWifiGetApi(uint32_t requestedApiVersion) {
#else
const struct chrePalWifiApi *chppPalWifiGetApi(uint32_t requestedApiVersion) {
#endif

  static const struct chrePalWifiApi api = {
      .moduleVersion = CHPP_PAL_WIFI_API_VERSION,
      .open = chppWifiClientOpen,
      .close = chppWifiClientClose,
      .getCapabilities = chppWifiClientGetCapabilities,
      .configureScanMonitor = chppWifiClientConfigureScanMonitor,
      .requestScan = chppWifiClientRequestScan,
      .releaseScanEvent = chppWifiClientReleaseScanEvent,
      .requestRanging = chppWifiClientRequestRanging,
      .releaseRangingEvent = chppWifiClientReleaseRangingEvent,
      .nanSubscribe = chppWifiClientNanSubscribe,
      .nanSubscribeCancel = chppWifiClientNanSubscribeCancel,
      .releaseNanDiscoveryEvent = chppWifiClientNanReleaseDiscoveryEvent,
      .requestNanRanging = chppWifiClientNanRequestNanRanging,
      .getNanCapabilities = chppWifiGetNanCapabilites,
  };

  CHPP_STATIC_ASSERT(
      CHRE_PAL_WIFI_API_CURRENT_VERSION == CHPP_PAL_WIFI_API_VERSION,
      "A newer CHRE PAL API version is available. Please update.");

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(api.moduleVersion,
                                        requestedApiVersion)) {
    return NULL;
  } else {
    return &api;
  }
}

#endif
