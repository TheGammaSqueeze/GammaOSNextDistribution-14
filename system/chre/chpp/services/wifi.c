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

#include "chpp/services/wifi.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/common/standard_uuids.h"
#include "chpp/common/wifi.h"
#include "chpp/common/wifi_types.h"
#include "chpp/common/wifi_utils.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/services.h"
#include "chre/pal/wifi.h"

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppDispatchWifiRequest(void *serviceContext,
                                                     uint8_t *buf, size_t len);
static void chppWifiServiceNotifyReset(void *serviceContext);

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Configuration parameters for this service
 */
static const struct ChppService kWifiServiceConfig = {
    .descriptor.uuid = CHPP_UUID_WIFI_STANDARD,

    // Human-readable name
    .descriptor.name = "WiFi",

    // Version
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,

    // Notifies service if CHPP is reset
    .resetNotifierFunctionPtr = &chppWifiServiceNotifyReset,

    // Client request dispatch function pointer
    .requestDispatchFunctionPtr = &chppDispatchWifiRequest,

    // Client notification dispatch function pointer
    .notificationDispatchFunctionPtr = NULL,  // Not supported

    // Min length is the entire header
    .minLength = sizeof(struct ChppAppHeader),
};

/**
 * Structure to maintain state for the WiFi service and its Request/Response
 * (RR) functionality.
 */
struct ChppWifiServiceState {
  struct ChppEndpointState service;  // CHPP service state
  const struct chrePalWifiApi *api;  // WiFi PAL API

  // Based on chre/pal/wifi.h and chrePalWifiApi
  struct ChppIncomingRequestState open;             // Service init state
  struct ChppIncomingRequestState close;            // Service deinit state
  struct ChppIncomingRequestState getCapabilities;  // Get Capabilities state
  struct ChppIncomingRequestState
      configureScanMonitorAsync;  // Configure scan monitor state
  struct ChppIncomingRequestState requestScanAsync;     // Request scan state
  struct ChppIncomingRequestState requestRangingAsync;  // Request ranging state
  struct ChppIncomingRequestState
      requestNanSubscribe;  // Request Nan Subscription state
  struct ChppIncomingRequestState
      requestNanSubscribeCancel;  // Request Nan Subscription cancelation state
  struct ChppIncomingRequestState
      requestNanRangingAsync;  // Request NAN ranging state
};

// Note: The CHRE PAL API only allows for one definition - see comment in WWAN
// service for details.
// Note: There is no notion of a cookie in the CHRE WiFi API so we need to use
// the global service state (gWifiServiceContext) directly in all callbacks.
struct ChppWifiServiceState gWifiServiceContext;

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppWifiServiceOpen(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppWifiServiceClose(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppWifiServiceGetCapabilities(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppWifiServiceConfigureScanMonitorAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppWifiServiceRequestScanAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppWifiServiceRequestRangingAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppWifiServiceRequestNanSubscribe(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppWifiServiceRequestNanSubscribeCancel(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static bool chppWifiServiceRequestNanRanging(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);

static void chppWifiServiceScanMonitorStatusChangeCallback(bool enabled,
                                                           uint8_t errorCode);
static void chppWifiServiceScanResponseCallback(bool pending,
                                                uint8_t errorCode);
static void chppWifiServiceScanEventCallback(struct chreWifiScanEvent *event);
static void chppWifiServiceRangingEventCallback(
    uint8_t errorCode, struct chreWifiRangingEvent *event);
static void chppWifiServiceNanIdentifierCallback(uint8_t errorCode,
                                                 uint32_t subscriptionId);
static void chppWifiServiceNanDiscoveryCallback(
    struct chreWifiNanDiscoveryEvent *event);
static void chppWifiServiceNanLostCallback(uint32_t subscriptionId,
                                           uint32_t publisherId);
static void chppWifiServiceNanTerminatedCallback(uint32_t reason,
                                                 uint32_t subscriptionId);
static void chppWifiServiceNanSubscriptionCanceledCallback(
    uint8_t errorCode, uint32_t subscriptionId);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Dispatches a client request from the transport layer that is determined to be
 * for the WiFi service. If the result of the dispatch is an error, this
 * function responds to the client with the same error.
 *
 * This function is called from the app layer using its function pointer given
 * during service registration.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppDispatchWifiRequest(void *serviceContext,
                                                     uint8_t *buf, size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct ChppWifiServiceState *wifiServiceContext =
      (struct ChppWifiServiceState *)serviceContext;
  struct ChppIncomingRequestState *inReqState = NULL;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;
  bool dispatched = true;

  switch (rxHeader->command) {
    case CHPP_WIFI_OPEN: {
      inReqState = &wifiServiceContext->open;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceOpen(wifiServiceContext, rxHeader);
      break;
    }

    case CHPP_WIFI_CLOSE: {
      inReqState = &wifiServiceContext->close;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceClose(wifiServiceContext, rxHeader);
      break;
    }

    case CHPP_WIFI_GET_CAPABILITIES: {
      inReqState = &wifiServiceContext->getCapabilities;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceGetCapabilities(wifiServiceContext, rxHeader);
      break;
    }

    case CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC: {
      inReqState = &wifiServiceContext->configureScanMonitorAsync;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceConfigureScanMonitorAsync(wifiServiceContext,
                                                       rxHeader, buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_SCAN_ASYNC: {
      inReqState = &wifiServiceContext->requestScanAsync;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceRequestScanAsync(wifiServiceContext, rxHeader, buf,
                                              len);
      break;
    }

    case CHPP_WIFI_REQUEST_RANGING_ASYNC: {
      inReqState = &wifiServiceContext->requestRangingAsync;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceRequestRangingAsync(wifiServiceContext, rxHeader,
                                                 buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_NAN_SUB: {
      inReqState = &wifiServiceContext->requestNanSubscribe;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceRequestNanSubscribe(wifiServiceContext, rxHeader,
                                                 buf, len);
      break;
    }

    case CHPP_WIFI_REQUEST_NAN_SUB_CANCEL: {
      inReqState = &wifiServiceContext->requestNanSubscribeCancel;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceRequestNanSubscribeCancel(wifiServiceContext,
                                                       rxHeader, buf, len);
      break;
    };

    case CHPP_WIFI_REQUEST_NAN_RANGING_ASYNC: {
      inReqState = &wifiServiceContext->requestNanRangingAsync;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppWifiServiceRequestNanRanging(wifiServiceContext, rxHeader,
                                               buf, len);
      break;
    }

    default: {
      dispatched = false;
      error = CHPP_APP_ERROR_INVALID_COMMAND;
      break;
    }
  }

  if (dispatched == true && error != CHPP_APP_ERROR_NONE) {
    // Request was dispatched but an error was returned. Close out
    // chppTimestampIncomingRequest()
    chppTimestampOutgoingResponse(inReqState);
  }

  return error;
}

/**
 * Initializes the WiFi service upon an open request from the client and
 * responds to the client with the result.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceOpen(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader) {
  static const struct chrePalWifiCallbacks palCallbacks = {
      .scanMonitorStatusChangeCallback =
          chppWifiServiceScanMonitorStatusChangeCallback,
      .scanResponseCallback = chppWifiServiceScanResponseCallback,
      .scanEventCallback = chppWifiServiceScanEventCallback,
      .rangingEventCallback = chppWifiServiceRangingEventCallback,
      .nanServiceIdentifierCallback = chppWifiServiceNanIdentifierCallback,
      .nanServiceDiscoveryCallback = chppWifiServiceNanDiscoveryCallback,
      .nanServiceLostCallback = chppWifiServiceNanLostCallback,
      .nanServiceTerminatedCallback = chppWifiServiceNanTerminatedCallback,
      .nanSubscriptionCanceledCallback =
          chppWifiServiceNanSubscriptionCanceledCallback,
  };

  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (wifiServiceContext->service.openState == CHPP_OPEN_STATE_OPENED) {
    CHPP_DEBUG_ASSERT_LOG(false, "WiFi service already open");
    error = CHPP_APP_ERROR_INVALID_COMMAND;

  } else if (!wifiServiceContext->api->open(
                 wifiServiceContext->service.appContext->systemApi,
                 &palCallbacks)) {
    CHPP_DEBUG_ASSERT_LOG(false, "WiFi PAL open failed");
    error = CHPP_APP_ERROR_BEYOND_CHPP;

  } else {
    CHPP_LOGD("WiFi service opened");
    wifiServiceContext->service.openState = CHPP_OPEN_STATE_OPENED;

    struct ChppAppHeader *response =
        chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
    size_t responseLen = sizeof(*response);

    if (response == NULL) {
      CHPP_LOG_OOM();
      error = CHPP_APP_ERROR_OOM;
    } else {
      chppSendTimestampedResponseOrFail(wifiServiceContext->service.appContext,
                                        &wifiServiceContext->open, response,
                                        responseLen);
    }
  }

  return error;
}

/**
 * Deinitializes the WiFi service.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceClose(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  wifiServiceContext->api->close();
  wifiServiceContext->service.openState = CHPP_OPEN_STATE_CLOSED;

  CHPP_LOGD("WiFi service closed");

  struct ChppAppHeader *response =
      chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    error = CHPP_APP_ERROR_OOM;
  } else {
    chppSendTimestampedResponseOrFail(wifiServiceContext->service.appContext,
                                      &wifiServiceContext->close, response,
                                      responseLen);
  }
  return error;
}

/**
 * Notifies the service of an incoming reset.
 *
 * @param serviceContext Maintains status for each service instance.
 */
static void chppWifiServiceNotifyReset(void *serviceContext) {
  struct ChppWifiServiceState *wifiServiceContext =
      (struct ChppWifiServiceState *)serviceContext;

  if (wifiServiceContext->service.openState != CHPP_OPEN_STATE_OPENED) {
    CHPP_LOGW("WiFi service reset but wasn't open");
  } else {
    CHPP_LOGD("WiFi service reset. Closing");
    wifiServiceContext->service.openState = CHPP_OPEN_STATE_CLOSED;
    wifiServiceContext->api->close();
  }
}

/**
 * Retrieves a set of flags indicating the WiFi features supported by the
 * current implementation.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceGetCapabilities(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct ChppWifiGetCapabilitiesResponse *response = chppAllocResponseFixed(
      requestHeader, struct ChppWifiGetCapabilitiesResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    error = CHPP_APP_ERROR_OOM;
  } else {
    response->params.capabilities = wifiServiceContext->api->getCapabilities();

    CHPP_LOGD("chppWifiServiceGetCapabilities returning 0x%" PRIx32
              ", %" PRIuSIZE " bytes",
              response->params.capabilities, responseLen);
    chppSendTimestampedResponseOrFail(wifiServiceContext->service.appContext,
                                      &wifiServiceContext->getCapabilities,
                                      response, responseLen);
  }

  return error;
}

/**
 * Configures whether scanEventCallback receives unsolicited scan results, i.e.
 * the results of scans not performed at the request of CHRE.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppWifiServiceScanMonitorStatusChangeCallback() will be
 * used to communicate the result of this request (as a service response).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceConfigureScanMonitorAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (len < sizeof(bool)) {
    error = CHPP_APP_ERROR_INVALID_ARG;
  } else {
    bool *enable = (bool *)buf;
    if (!wifiServiceContext->api->configureScanMonitor(*enable)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;
    }
  }

  return error;
}

/**
 * Request that the WiFi chipset perform a scan, or deliver results from its
 * cache if the parameters allow for it.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppWifiServiceScanResponseCallback() will be used to
 * communicate the result of this request (as a service response).
 * A subsequent call to chppWifiServiceScanEventCallback() will be used to
 * communicate the scan results (as a service notification).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceRequestScanAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct chreWifiScanParams *chre =
      chppWifiScanParamsToChre((struct ChppWifiScanParams *)buf, len);

  if (chre == NULL) {
    CHPP_LOGE(
        "WifiServiceRequestScanAsync CHPP -> CHRE conversion failed. Input "
        "len=%" PRIuSIZE,
        len);
    error = CHPP_APP_ERROR_INVALID_ARG;

  } else {
    if (!wifiServiceContext->api->requestScan(chre)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;
    }

    if (chre->frequencyListLen > 0) {
      void *frequencyList = CHPP_CONST_CAST_POINTER(chre->frequencyList);
      CHPP_FREE_AND_NULLIFY(frequencyList);
    }
    if (chre->ssidListLen > 0) {
      void *ssidList = CHPP_CONST_CAST_POINTER(chre->ssidList);
      CHPP_FREE_AND_NULLIFY(ssidList);
    }
    CHPP_FREE_AND_NULLIFY(chre);
  }

  return error;
}

/**
 * Request that the WiFi chipset perform RTT ranging against a set of access
 * points specified in params.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppWifiServiceRangingEventCallback() will be used to
 * communicate the ranging results (as a service notification).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppWifiServiceRequestRangingAsync(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct chreWifiRangingParams *chre =
      chppWifiRangingParamsToChre((struct ChppWifiRangingParams *)buf, len);

  if (chre == NULL) {
    CHPP_LOGE(
        "WifiServiceRequestRangingAsync CHPP -> CHRE conversion failed. Input "
        "len=%" PRIuSIZE,
        len);
    error = CHPP_APP_ERROR_INVALID_ARG;

  } else {
    if (!wifiServiceContext->api->requestRanging(chre)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;

    } else {
      struct ChppAppHeader *response =
          chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
      size_t responseLen = sizeof(*response);

      if (response == NULL) {
        CHPP_LOG_OOM();
        error = CHPP_APP_ERROR_OOM;
      } else {
        chppSendTimestampedResponseOrFail(
            wifiServiceContext->service.appContext,
            &wifiServiceContext->requestRangingAsync, response, responseLen);
      }
    }

    if (chre->targetListLen > 0) {
      void *targetList = CHPP_CONST_CAST_POINTER(chre->targetList);
      CHPP_FREE_AND_NULLIFY(targetList);
    }
    CHPP_FREE_AND_NULLIFY(chre);
  }

  return error;
}

static enum ChppAppErrorCode chppWifiServiceRequestNanSubscribe(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct chreWifiNanSubscribeConfig *chreConfig =
      chppWifiNanSubscribeConfigToChre((struct ChppWifiNanSubscribeConfig *)buf,
                                       len);
  if (chreConfig == NULL) {
    CHPP_LOGE(
        "WifiServiceNanSubscribeConfig CHPP -> CHRE conversion failed."
        "Input len: %" PRIuSIZE,
        len);
    error = CHPP_APP_ERROR_INVALID_ARG;
  } else {
    if (!wifiServiceContext->api->nanSubscribe(chreConfig)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;

    } else {
      struct ChppAppHeader *response =
          chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
      size_t responseLen = sizeof(*response);

      if (response == NULL) {
        CHPP_LOG_OOM();
        error = CHPP_APP_ERROR_OOM;
      } else {
        chppSendTimestampedResponseOrFail(
            wifiServiceContext->service.appContext,
            &wifiServiceContext->requestNanSubscribe, response, responseLen);
      }
    }
  }
  return error;
}

static enum ChppAppErrorCode chppWifiServiceRequestNanSubscribeCancel(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (len < sizeof(struct ChppWifiNanSubscribeCancelRequest)) {
    CHPP_LOGE(
        "WifiServiceRequestNanSubscribecancel invalid input len = %" PRIuSIZE,
        len);
    error = CHPP_APP_ERROR_INVALID_ARG;
  } else {
    struct ChppWifiNanSubscribeCancelRequest *chppRequest =
        (struct ChppWifiNanSubscribeCancelRequest *)buf;
    uint32_t subscriptionId = chppRequest->subscriptionId;
    if (!wifiServiceContext->api->nanSubscribeCancel(subscriptionId)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;

    } else {
      struct ChppAppHeader *response =
          chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
      size_t responseLen = sizeof(*response);

      if (response == NULL) {
        CHPP_LOG_OOM();
        error = CHPP_APP_ERROR_OOM;
      } else {
        chppSendTimestampedResponseOrFail(
            wifiServiceContext->service.appContext,
            &wifiServiceContext->requestNanSubscribeCancel, response,
            responseLen);
      }
    }
  }
  return error;
}

static bool chppWifiServiceRequestNanRanging(
    struct ChppWifiServiceState *wifiServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct chreWifiNanRangingParams *chreParams = chppWifiNanRangingParamsToChre(
      (struct ChppWifiNanRangingParams *)buf, len);
  if (chreParams == NULL) {
    CHPP_LOGE(
        "WifiServiceRequestNanRanging CHPP -> CHRE conversion failed. "
        "Input len: %" PRIuSIZE,
        len);
    error = CHPP_APP_ERROR_INVALID_ARG;

  } else {
    if (!wifiServiceContext->api->requestNanRanging(chreParams)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;

    } else {
      struct ChppAppHeader *response =
          chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
      size_t responseLen = sizeof(*response);

      if (response == NULL) {
        CHPP_LOG_OOM();
        error = CHPP_APP_ERROR_OOM;
      } else {
        chppSendTimestampedResponseOrFail(
            wifiServiceContext->service.appContext,
            &wifiServiceContext->requestNanRangingAsync, response, responseLen);
      }
    }
  }
  return error;
}

/**
 * PAL callback with the result of changes to the scan monitor registration
 * status requested via configureScanMonitor.
 *
 * @param enabled true if the scan monitor is currently active
 * @param errorCode An error code from enum chreError
 */
static void chppWifiServiceScanMonitorStatusChangeCallback(bool enabled,
                                                           uint8_t errorCode) {
  // Recreate request header
  struct ChppAppHeader requestHeader = {
      .handle = gWifiServiceContext.service.handle,
      .transaction = gWifiServiceContext.configureScanMonitorAsync.transaction,
      .command = CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC,
  };

  struct ChppWifiConfigureScanMonitorAsyncResponse *response =
      chppAllocResponseFixed(&requestHeader,
                             struct ChppWifiConfigureScanMonitorAsyncResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    CHPP_ASSERT(false);

  } else {
    response->params.enabled = enabled;
    response->params.errorCode = errorCode;

    chppSendTimestampedResponseOrFail(
        gWifiServiceContext.service.appContext,
        &gWifiServiceContext.configureScanMonitorAsync, response, responseLen);
  }
}

/**
 * PAL callback with the result of a requestScan.
 *
 * @param pending true if the request was successful.
 * @param errorCode An error code from enum chreError.
 */
static void chppWifiServiceScanResponseCallback(bool pending,
                                                uint8_t errorCode) {
  // Recreate request header
  struct ChppAppHeader requestHeader = {
      .handle = gWifiServiceContext.service.handle,
      .transaction = gWifiServiceContext.requestScanAsync.transaction,
      .command = CHPP_WIFI_REQUEST_SCAN_ASYNC,
  };

  struct ChppWifiRequestScanResponse *response = chppAllocResponseFixed(
      &requestHeader, struct ChppWifiRequestScanResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    CHPP_ASSERT(false);

  } else {
    response->params.pending = pending;
    response->params.errorCode = errorCode;

    chppSendTimestampedResponseOrFail(gWifiServiceContext.service.appContext,
                                      &gWifiServiceContext.requestScanAsync,
                                      response, responseLen);
  }
}

/**
 * PAL callback with WiFi scan results.
 *
 * @param event Scan result data.
 */
static void chppWifiServiceScanEventCallback(struct chreWifiScanEvent *event) {
  // Craft response per parser script
  struct ChppWifiScanEventWithHeader *notification = NULL;
  size_t notificationLen = 0;

  CHPP_DEBUG_ASSERT(chppCheckWifiScanEventNotification(event));

  if (!chppWifiScanEventFromChre(event, &notification, &notificationLen)) {
    CHPP_LOGE("ScanEvent conversion failed (OOM?). ID=%" PRIu8,
              gWifiServiceContext.requestScanAsync.transaction);

    notification = chppMalloc(sizeof(struct ChppAppHeader));
    if (notification == NULL) {
      CHPP_LOG_OOM();
    } else {
      notificationLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notification != NULL) {
    notification->header.handle = gWifiServiceContext.service.handle;
    notification->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notification->header.transaction =
        gWifiServiceContext.requestScanAsync.transaction;
    notification->header.error =
        (notificationLen > sizeof(struct ChppAppHeader))
            ? CHPP_APP_ERROR_NONE
            : CHPP_APP_ERROR_CONVERSION_FAILED;
    notification->header.command = CHPP_WIFI_REQUEST_SCAN_ASYNC;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, notification,
        notificationLen);
  }

  gWifiServiceContext.api->releaseScanEvent(event);
}

/**
 * PAL callback with RTT ranging results from the WiFi module.
 *
 * @param errorCode An error code from enum chreError.
 * @param event Ranging data.
 */
static void chppWifiServiceRangingEventCallback(
    uint8_t errorCode, struct chreWifiRangingEvent *event) {
  struct ChppWifiRangingEventWithHeader *notification = NULL;
  size_t notificationLen = 0;

  if (!chppWifiRangingEventFromChre(event, &notification, &notificationLen)) {
    CHPP_LOGE("RangingEvent conversion failed (OOM?) ID=%" PRIu8,
              gWifiServiceContext.requestRangingAsync.transaction);

    notification = chppMalloc(sizeof(struct ChppAppHeader));
    if (notification == NULL) {
      CHPP_LOG_OOM();
    } else {
      notificationLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notification != NULL) {
    uint16_t command = CHPP_WIFI_REQUEST_RANGING_ASYNC;

    // Per CHRE's API contract, only one kind of ranging request can be pending
    // at a time - use the higher of the two for the notification.
    uint8_t transaction =
        MAX(gWifiServiceContext.requestRangingAsync.transaction,
            gWifiServiceContext.requestNanRangingAsync.transaction);
    notification->header.handle = gWifiServiceContext.service.handle;
    notification->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notification->header.transaction = transaction;
    notification->header.command = command;
    notification->header.error =
        (notificationLen > sizeof(struct ChppAppHeader))
            ? CHPP_APP_ERROR_NONE
            : CHPP_APP_ERROR_CONVERSION_FAILED;

    if (errorCode != CHRE_ERROR_NONE) {
      notification->header.error = CHPP_APP_ERROR_BEYOND_CHPP;
      notificationLen = MIN(notificationLen, sizeof(struct ChppAppHeader));
    }

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, notification,
        notificationLen);
  }

  gWifiServiceContext.api->releaseRangingEvent(event);
}

/**
 * PAL callback with NAN service subscription identifier information.
 *
 * @param errorCode Error code indicating if a NAN subscription failed. The
 *        subscriptionId field is only valid if the error code is
 *        CHRE_ERROR_NONE.
 * @param subscriptionId The ID assigned to the service subscription request.
 *        This value is only valid if the error code is CHRE_ERROR_NONE.
 */
static void chppWifiServiceNanIdentifierCallback(uint8_t errorCode,
                                                 uint32_t subscriptionId) {
  size_t idLen = sizeof(struct ChppWifiNanServiceIdentifier);
  struct ChppWifiNanServiceIdentifier *id = chppMalloc(idLen);
  if (id == NULL) {
    CHPP_LOG_OOM();
  } else {
    id->header.command = CHPP_WIFI_REQUEST_NAN_SUB;
    id->header.handle = gWifiServiceContext.service.handle;
    id->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    id->header.error = CHPP_APP_ERROR_NONE;
    id->header.transaction =
        gWifiServiceContext.requestNanSubscribe.transaction;
    id->errorCode = errorCode;
    id->subscriptionId = subscriptionId;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, id, idLen);
  }
}

/**
 * PAL callback with NAN service discovery information.
 *
 * @param event Information about a discovered publishing service.
 */
static void chppWifiServiceNanDiscoveryCallback(
    struct chreWifiNanDiscoveryEvent *event) {
  struct ChppWifiNanDiscoveryEventWithHeader *notif = NULL;
  size_t notifLen = 0;

  if (!chppWifiNanDiscoveryEventFromChre(event, &notif, &notifLen)) {
    CHPP_LOGE("Discovery event conversion failed");
    notif = chppMalloc(sizeof(struct ChppAppHeader));
    if (notif == NULL) {
      CHPP_LOG_OOM();
    } else {
      notifLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notif != NULL) {
    notif->header.handle = gWifiServiceContext.service.handle;
    notif->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notif->header.error = (notifLen > sizeof(struct ChppAppHeader))
                              ? CHPP_APP_ERROR_NONE
                              : CHPP_APP_ERROR_CONVERSION_FAILED;
    notif->header.command = CHPP_WIFI_NOTIFICATION_NAN_SERVICE_DISCOVERY;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, notif,
        notifLen);
  }

  if (event != NULL) {
    gWifiServiceContext.api->releaseNanDiscoveryEvent(event);
  }
}

/**
 * PAL callback invoked when a publishing NAN service goes away.
 *
 * @param subscriptionId ID of the subscribing service.
 * @param publisherId ID of the publishing service that has gone away.
 */
static void chppWifiServiceNanLostCallback(uint32_t subscriptionId,
                                           uint32_t publisherId) {
  struct chreWifiNanSessionLostEvent chreEvent = {
      .id = subscriptionId,
      .peerId = publisherId,
  };
  struct ChppWifiNanSessionLostEventWithHeader *notif = NULL;
  size_t notifLen = 0;

  if (!chppWifiNanSessionLostEventFromChre(&chreEvent, &notif, &notifLen)) {
    CHPP_LOGE("Session lost event conversion failed");
    notif = chppMalloc(sizeof(struct ChppAppHeader));
    if (notif == NULL) {
      CHPP_LOG_OOM();
    } else {
      notifLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notif != NULL) {
    notif->header.handle = gWifiServiceContext.service.handle;
    notif->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notif->header.error = (notifLen > sizeof(struct ChppAppHeader))
                              ? CHPP_APP_ERROR_NONE
                              : CHPP_APP_ERROR_CONVERSION_FAILED;
    notif->header.command = CHPP_WIFI_NOTIFICATION_NAN_SERVICE_LOST;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, notif,
        notifLen);
  }
}

/**
 * PAL callback invoked when a NAN service subscription is terminated.
 *
 * @param reason Error code indicating the reason for the termination.
 * @param subscriptionId The subscription ID of the terminated NAN service.
 */
static void chppWifiServiceNanTerminatedCallback(uint32_t reason,
                                                 uint32_t subscriptionId) {
  uint8_t chreReason = (uint8_t)reason;
  struct chreWifiNanSessionTerminatedEvent chreEvent = {
      .id = subscriptionId,
      .reason = chreReason,
  };
  struct ChppWifiNanSessionTerminatedEventWithHeader *notif = NULL;
  size_t notifLen = 0;

  if (!chppWifiNanSessionTerminatedEventFromChre(&chreEvent, &notif,
                                                 &notifLen)) {
    CHPP_LOGE("Session terminated event conversion failed");
    notif = chppMalloc(sizeof(struct ChppAppHeader));
    if (notif == NULL) {
      CHPP_LOG_OOM();
    } else {
      notifLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notif != NULL) {
    notif->header.handle = gWifiServiceContext.service.handle;
    notif->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notif->header.error = (notifLen > sizeof(struct ChppAppHeader))
                              ? CHPP_APP_ERROR_NONE
                              : CHPP_APP_ERROR_CONVERSION_FAILED;
    notif->header.command = CHPP_WIFI_NOTIFICATION_NAN_SERVICE_TERMINATED;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, notif,
        notifLen);
  }
}

/**
 * PAL callback invoked when a NAN service subscription is canceled.
 *
 * @param errorCode A value in @ref chreError indicating the result of the
 *        cancelation, with CHRE_ERROR_NONE indicating success.
 * @param subscriptionId The subscription ID of the canceled NAN service.
 */
static void chppWifiServiceNanSubscriptionCanceledCallback(
    uint8_t errorCode, uint32_t subscriptionId) {
  size_t responseLen = sizeof(struct ChppWifiNanSubscriptionCanceledResponse);
  struct ChppWifiNanSubscriptionCanceledResponse *response =
      chppMalloc(responseLen);
  if (response == NULL) {
    CHPP_LOG_OOM();
  } else {
    response->header.command = CHPP_WIFI_REQUEST_NAN_SUB_CANCEL;
    response->header.handle = gWifiServiceContext.service.handle;
    response->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    response->header.error = CHPP_APP_ERROR_NONE;
    response->header.transaction =
        gWifiServiceContext.requestNanSubscribeCancel.transaction;
    response->errorCode = errorCode;
    response->subscriptionId = subscriptionId;

    chppEnqueueTxDatagramOrFail(
        gWifiServiceContext.service.appContext->transportContext, response,
        responseLen);
  }
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterWifiService(struct ChppAppState *appContext) {
  gWifiServiceContext.api = chrePalWifiGetApi(CHRE_PAL_WIFI_API_V1_2);

  chppCheckWifiScanEventNotificationReset();

  if (gWifiServiceContext.api == NULL) {
    CHPP_DEBUG_ASSERT_LOG(false,
                          "WiFi PAL API incompatible. Cannot register service");

  } else {
    chppRegisterService(appContext, (void *)&gWifiServiceContext,
                        &gWifiServiceContext.service, NULL /*outReqStates*/,
                        &kWifiServiceConfig);
    CHPP_DEBUG_ASSERT(gWifiServiceContext.service.handle);
  }
}

void chppDeregisterWifiService(struct ChppAppState *appContext) {
  // TODO

  UNUSED_VAR(appContext);
}
