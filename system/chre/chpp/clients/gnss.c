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

#include "chpp/clients/gnss.h"

#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/clients/discovery.h"
#include "chpp/common/gnss.h"
#include "chpp/common/gnss_types.h"
#include "chpp/common/standard_uuids.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chre/pal/gnss.h"
#include "chre_api/chre/gnss.h"

#ifndef CHPP_GNSS_DISCOVERY_TIMEOUT_MS
#define CHPP_GNSS_DISCOVERY_TIMEOUT_MS CHPP_DISCOVERY_DEFAULT_TIMEOUT_MS
#endif

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppDispatchGnssResponse(void *clientContext,
                                                      uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppDispatchGnssNotification(void *clientContext,
                                                          uint8_t *buf,
                                                          size_t len);
static bool chppGnssClientInit(void *clientContext, uint8_t handle,
                               struct ChppVersion serviceVersion);
static void chppGnssClientDeinit(void *clientContext);
static void chppGnssClientNotifyReset(void *clientContext);
static void chppGnssClientNotifyMatch(void *clientContext);

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Structure to maintain state for the GNSS client and its Request/Response
 * (RR) functionality.
 */
struct ChppGnssClientState {
  struct ChppEndpointState client;   // CHPP client state
  const struct chrePalGnssApi *api;  // GNSS PAL API

  struct ChppOutgoingRequestState
      outReqStates[CHPP_GNSS_CLIENT_REQUEST_MAX + 1];

  uint32_t capabilities;           // Cached GetCapabilities result
  bool requestStateResyncPending;  // requestStateResync() is waiting to be
                                   // processed
  bool capabilitiesValid;  // Flag to indicate if the capabilities result
                           // is valid
};

// Note: This global definition of gGnssClientContext supports only one
// instance of the CHPP GNSS client at a time.
struct ChppGnssClientState gGnssClientContext;
static const struct chrePalSystemApi *gSystemApi;
static const struct chrePalGnssCallbacks *gCallbacks;

/**
 * Configuration parameters for this client
 */
static const struct ChppClient kGnssClientConfig = {
    .descriptor.uuid = CHPP_UUID_GNSS_STANDARD,

    // Version
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,

    // Notifies client if CHPP is reset
    .resetNotifierFunctionPtr = &chppGnssClientNotifyReset,

    // Notifies client if they are matched to a service
    .matchNotifierFunctionPtr = &chppGnssClientNotifyMatch,

    // Service response dispatch function pointer
    .responseDispatchFunctionPtr = &chppDispatchGnssResponse,

    // Service notification dispatch function pointer
    .notificationDispatchFunctionPtr = &chppDispatchGnssNotification,

    // Service response dispatch function pointer
    .initFunctionPtr = &chppGnssClientInit,

    // Service notification dispatch function pointer
    .deinitFunctionPtr = &chppGnssClientDeinit,

    // Number of request-response states in the outReqStates array.
    .outReqCount = ARRAY_SIZE(gGnssClientContext.outReqStates),

    // Min length is the entire header
    .minLength = sizeof(struct ChppAppHeader),
};

/************************************************
 *  Prototypes
 ***********************************************/

static bool chppGnssClientOpen(const struct chrePalSystemApi *systemApi,
                               const struct chrePalGnssCallbacks *callbacks);
static void chppGnssClientClose(void);
static uint32_t chppGnssClientGetCapabilities(void);
static bool chppGnssClientControlLocationSession(bool enable,
                                                 uint32_t minIntervalMs,
                                                 uint32_t minTimeToNextFixMs);
static void chppGnssClientReleaseLocationEvent(
    struct chreGnssLocationEvent *event);
static bool chppGnssClientControlMeasurementSession(bool enable,
                                                    uint32_t minIntervalMs);
static void chppGnssClientReleaseMeasurementDataEvent(
    struct chreGnssDataEvent *event);
static bool chppGnssClientConfigurePassiveLocationListener(bool enable);

static void chppGnssCloseResult(struct ChppGnssClientState *clientContext,
                                uint8_t *buf, size_t len);
static void chppGnssGetCapabilitiesResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);
static void chppGnssControlLocationSessionResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);
static void chppGnssControlMeasurementSessionResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);
static void chppGnssConfigurePassiveLocationListenerResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);

static void chppGnssStateResyncNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);
static void chppGnssLocationResultNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);
static void chppGnssMeasurementResultNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Dispatches a service response from the transport layer that is determined to
 * be for the GNSS client.
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
static enum ChppAppErrorCode chppDispatchGnssResponse(void *clientContext,
                                                      uint8_t *buf,
                                                      size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (rxHeader->command > CHPP_GNSS_CLIENT_REQUEST_MAX) {
    error = CHPP_APP_ERROR_INVALID_COMMAND;

  } else if (!chppTimestampIncomingResponse(
                 gnssClientContext->client.appContext,
                 &gnssClientContext->outReqStates[rxHeader->command],
                 rxHeader)) {
    error = CHPP_APP_ERROR_UNEXPECTED_RESPONSE;

  } else {
    switch (rxHeader->command) {
      case CHPP_GNSS_OPEN: {
        chppClientProcessOpenResponse(&gnssClientContext->client, buf, len);
        if (gnssClientContext->requestStateResyncPending) {
          gCallbacks->requestStateResync();
          gnssClientContext->requestStateResyncPending = false;
        }
        break;
      }

      case CHPP_GNSS_CLOSE: {
        chppGnssCloseResult(gnssClientContext, buf, len);
        break;
      }

      case CHPP_GNSS_GET_CAPABILITIES: {
        chppGnssGetCapabilitiesResult(gnssClientContext, buf, len);
        break;
      }

      case CHPP_GNSS_CONTROL_LOCATION_SESSION: {
        chppGnssControlLocationSessionResult(gnssClientContext, buf, len);
        break;
      }

      case CHPP_GNSS_CONTROL_MEASUREMENT_SESSION: {
        chppGnssControlMeasurementSessionResult(gnssClientContext, buf, len);
        break;
      }

      case CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER: {
        chppGnssConfigurePassiveLocationListenerResult(gnssClientContext, buf,
                                                       len);
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
 * to be for the GNSS client.
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
static enum ChppAppErrorCode chppDispatchGnssNotification(void *clientContext,
                                                          uint8_t *buf,
                                                          size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  switch (rxHeader->command) {
    case CHPP_GNSS_REQUEST_STATE_RESYNC_NOTIFICATION: {
      chppGnssStateResyncNotification(gnssClientContext, buf, len);
      break;
    }

    case CHPP_GNSS_LOCATION_RESULT_NOTIFICATION: {
      chppGnssLocationResultNotification(gnssClientContext, buf, len);
      break;
    }

    case CHPP_GNSS_MEASUREMENT_RESULT_NOTIFICATION: {
      chppGnssMeasurementResultNotification(gnssClientContext, buf, len);
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
static bool chppGnssClientInit(void *clientContext, uint8_t handle,
                               struct ChppVersion serviceVersion) {
  UNUSED_VAR(serviceVersion);

  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;
  chppClientInit(&gnssClientContext->client, handle);

  return true;
}

/**
 * Deinitializes the client.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppGnssClientDeinit(void *clientContext) {
  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;
  chppClientDeinit(&gnssClientContext->client);
}

/**
 * Notifies the client of an incoming reset.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppGnssClientNotifyReset(void *clientContext) {
  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;

  chppClientCloseOpenRequests(&gnssClientContext->client, &kGnssClientConfig,
                              false /* clearOnly */);

  if (gnssClientContext->client.openState != CHPP_OPEN_STATE_OPENED &&
      !gnssClientContext->client.pseudoOpen) {
    CHPP_LOGW("GNSS client reset but wasn't open");
  } else {
    CHPP_LOGD("GNSS client reopening from state=%" PRIu8,
              gnssClientContext->client.openState);
    gnssClientContext->requestStateResyncPending = true;
    chppClientSendOpenRequest(&gGnssClientContext.client,
                              &gGnssClientContext.outReqStates[CHPP_GNSS_OPEN],
                              CHPP_GNSS_OPEN,
                              /*blocking=*/false);
  }
}

/**
 * Notifies the client of being matched to a service.
 *
 * @param clientContext Maintains status for each client instance.
 */
static void chppGnssClientNotifyMatch(void *clientContext) {
  struct ChppGnssClientState *gnssClientContext =
      (struct ChppGnssClientState *)clientContext;

  if (gnssClientContext->client.pseudoOpen) {
    CHPP_LOGD("Pseudo-open GNSS client opening");
    chppClientSendOpenRequest(&gGnssClientContext.client,
                              &gGnssClientContext.outReqStates[CHPP_GNSS_OPEN],
                              CHPP_GNSS_OPEN,
                              /*blocking=*/false);
  }
}

/**
 * Handles the service response for the close client request.
 *
 * This function is called from chppDispatchGnssResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssCloseResult(struct ChppGnssClientState *clientContext,
                                uint8_t *buf, size_t len) {
  // TODO
  UNUSED_VAR(clientContext);
  UNUSED_VAR(buf);
  UNUSED_VAR(len);
}

/**
 * Handles the service response for the get capabilities client request.
 *
 * This function is called from chppDispatchGnssResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssGetCapabilitiesResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  if (len < sizeof(struct ChppGnssGetCapabilitiesResponse)) {
    CHPP_LOGE("Bad GNSS capabilities len=%" PRIuSIZE, len);

  } else {
    struct ChppGnssGetCapabilitiesParameters *result =
        &((struct ChppGnssGetCapabilitiesResponse *)buf)->params;

    CHPP_LOGD("chppGnssGetCapabilitiesResult received capabilities=0x%" PRIx32,
              result->capabilities);

    CHPP_ASSERT((result->capabilities & CHPP_GNSS_DEFAULT_CAPABILITIES) ==
                CHPP_GNSS_DEFAULT_CAPABILITIES);
    if (result->capabilities != CHPP_GNSS_DEFAULT_CAPABILITIES) {
      CHPP_LOGE("GNSS capabilities 0x%" PRIx32 " != 0x%" PRIx32,
                result->capabilities, (uint32_t)CHPP_GNSS_DEFAULT_CAPABILITIES);
    }

    clientContext->capabilitiesValid = true;
    clientContext->capabilities = result->capabilities;
  }
}

/**
 * Handles the service response for the Control Location Session client request.
 *
 * This function is called from chppDispatchGnssResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssControlLocationSessionResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);

  if (len < sizeof(struct ChppGnssControlLocationSessionResponse)) {
    // Short response length indicates an error
    gCallbacks->locationStatusChangeCallback(
        false, chppAppShortResponseErrorHandler(buf, len, "ControlLocation"));

  } else {
    struct ChppGnssControlLocationSessionResponse *result =
        (struct ChppGnssControlLocationSessionResponse *)buf;

    CHPP_LOGD(
        "chppGnssControlLocationSessionResult received enable=%d, "
        "errorCode=%" PRIu8,
        result->enabled, result->errorCode);

    gCallbacks->locationStatusChangeCallback(result->enabled,
                                             result->errorCode);
  }
}

/**
 * Handles the service response for the Control Measurement Session client
 * request.
 *
 * This function is called from chppDispatchGnssResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssControlMeasurementSessionResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);

  if (len < sizeof(struct ChppGnssControlMeasurementSessionResponse)) {
    // Short response length indicates an error
    gCallbacks->measurementStatusChangeCallback(
        false, chppAppShortResponseErrorHandler(buf, len, "Measurement"));

  } else {
    struct ChppGnssControlMeasurementSessionResponse *result =
        (struct ChppGnssControlMeasurementSessionResponse *)buf;

    CHPP_LOGD(
        "chppGnssControlMeasurementSessionResult received enable=%d, "
        "errorCode=%" PRIu8,
        result->enabled, result->errorCode);

    gCallbacks->measurementStatusChangeCallback(result->enabled,
                                                result->errorCode);
  }
}

/**
 * Handles the service response for the Configure Passive Location Listener
 * client request.
 *
 * This function is called from chppDispatchGnssResponse().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssConfigurePassiveLocationListenerResult(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);
  UNUSED_VAR(len);

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    CHPP_DEBUG_ASSERT_LOG(false, "Passive scan failed at service");

  } else {
    CHPP_LOGD(
        "WiFi ConfigurePassiveLocationListener request accepted at service");
  }
}

/**
 * Handles the State Resync service notification.
 *
 * This function is called from chppDispatchGnssNotification().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssStateResyncNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(buf);
  UNUSED_VAR(len);
  if (clientContext->client.openState == CHPP_OPEN_STATE_WAITING_TO_OPEN) {
    // If the GNSS client is waiting for the open to proceed, the CHRE handler
    // for requestStateResync() may fail, so we set a flag to process it later
    // when the open has succeeded.
    clientContext->requestStateResyncPending = true;
  } else {
    gCallbacks->requestStateResync();
    clientContext->requestStateResyncPending = false;
  }
}

/**
 * Handles the Location Result service notification.
 *
 * This function is called from chppDispatchGnssNotification().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssLocationResultNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);
  CHPP_LOGD("chppGnssLocationResultNotification received data len=%" PRIuSIZE,
            len);

  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct chreGnssLocationEvent *chre =
      chppGnssLocationEventToChre((struct ChppGnssLocationEvent *)buf, len);

  if (chre == NULL) {
    CHPP_LOGE("Location result conversion failed: len=%" PRIuSIZE, len);
  } else {
    gCallbacks->locationEventCallback(chre);
  }
}

/**
 * Handles the Measurement Result service notification.
 *
 * This function is called from chppDispatchGnssNotification().
 *
 * @param clientContext Maintains status for each client instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppGnssMeasurementResultNotification(
    struct ChppGnssClientState *clientContext, uint8_t *buf, size_t len) {
  UNUSED_VAR(clientContext);
  CHPP_LOGD(
      "chppGnssMeasurementResultNotification received data len=%" PRIuSIZE,
      len);

  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct chreGnssDataEvent *chre =
      chppGnssDataEventToChre((struct ChppGnssDataEvent *)buf, len);

  if (chre == NULL) {
    CHPP_LOGE("Measurement result conversion failed len=%" PRIuSIZE, len);
  } else {
    gCallbacks->measurementEventCallback(chre);
  }
}

/**
 * Initializes the GNSS client upon an open request from CHRE and responds
 * with the result.
 *
 * @param systemApi CHRE system function pointers.
 * @param callbacks CHRE entry points.
 *
 * @return True if successful. False otherwise.
 */
static bool chppGnssClientOpen(const struct chrePalSystemApi *systemApi,
                               const struct chrePalGnssCallbacks *callbacks) {
  CHPP_DEBUG_NOT_NULL(systemApi);
  CHPP_DEBUG_NOT_NULL(callbacks);

  bool result = false;
  gSystemApi = systemApi;
  gCallbacks = callbacks;

  CHPP_LOGD("GNSS client opening");
  if (gGnssClientContext.client.appContext == NULL) {
    CHPP_LOGE("GNSS client app is null");
  } else {
    if (chppWaitForDiscoveryComplete(gGnssClientContext.client.appContext,
                                     CHPP_GNSS_DISCOVERY_TIMEOUT_MS)) {
      result = chppClientSendOpenRequest(
          &gGnssClientContext.client,
          &gGnssClientContext.outReqStates[CHPP_GNSS_OPEN], CHPP_GNSS_OPEN,
          /*blocking=*/true);
    }

    // Since CHPP_GNSS_DEFAULT_CAPABILITIES is mandatory, we can always
    // pseudo-open and return true. Otherwise, these should have been gated.
    chppClientPseudoOpen(&gGnssClientContext.client);
    result = true;
  }

  return result;
}

/**
 * Deinitializes the GNSS client.
 */
static void chppGnssClientClose(void) {
  // Remote
  struct ChppAppHeader *request = chppAllocClientRequestCommand(
      &gGnssClientContext.client, CHPP_GNSS_CLOSE);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else if (chppClientSendTimestampedRequestAndWait(
                 &gGnssClientContext.client,
                 &gGnssClientContext.outReqStates[CHPP_GNSS_CLOSE], request,
                 sizeof(*request))) {
    gGnssClientContext.client.openState = CHPP_OPEN_STATE_CLOSED;
    gGnssClientContext.capabilities = CHRE_GNSS_CAPABILITIES_NONE;
    gGnssClientContext.capabilitiesValid = false;
    chppClientCloseOpenRequests(&gGnssClientContext.client, &kGnssClientConfig,
                                true /* clearOnly */);
  }
}

/**
 * Retrieves a set of flags indicating the GNSS features supported by the
 * current implementation.
 *
 * @return Capabilities flags.
 */
static uint32_t chppGnssClientGetCapabilities(void) {
  uint32_t capabilities = CHPP_GNSS_DEFAULT_CAPABILITIES;

  if (gGnssClientContext.capabilitiesValid) {
    // Result already cached
    capabilities = gGnssClientContext.capabilities;

  } else {
    struct ChppAppHeader *request = chppAllocClientRequestCommand(
        &gGnssClientContext.client, CHPP_GNSS_GET_CAPABILITIES);

    if (request == NULL) {
      CHPP_LOG_OOM();
    } else {
      if (chppClientSendTimestampedRequestAndWait(
              &gGnssClientContext.client,
              &gGnssClientContext.outReqStates[CHPP_GNSS_GET_CAPABILITIES],
              request, sizeof(*request))) {
        // Success. gGnssClientContext.capabilities is now populated
        if (gGnssClientContext.capabilitiesValid) {
          capabilities = gGnssClientContext.capabilities;
        }
      }
    }
  }

  return capabilities;
}

/**
 * Start/stop/modify the GNSS location session used for clients of the CHRE
 * API.
 *
 * @param enable true to start/modify the session, false to stop the
 *        session. If false, other parameters are ignored.
 * @param minIntervalMs See chreGnssLocationSessionStartAsync()
 * @param minTimeToNextFixMs See chreGnssLocationSessionStartAsync()
 *
 * @return True indicates the request was sent off to the service.
 */

static bool chppGnssClientControlLocationSession(bool enable,
                                                 uint32_t minIntervalMs,
                                                 uint32_t minTimeToNextFixMs) {
  bool result = false;

  struct ChppGnssControlLocationSessionRequest *request =
      chppAllocClientRequestFixed(&gGnssClientContext.client,
                                  struct ChppGnssControlLocationSessionRequest);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else {
    request->header.command = CHPP_GNSS_CONTROL_LOCATION_SESSION;
    request->params.enable = enable;
    request->params.minIntervalMs = minIntervalMs;
    request->params.minTimeToNextFixMs = minTimeToNextFixMs;

    result = chppClientSendTimestampedRequestOrFail(
        &gGnssClientContext.client,
        &gGnssClientContext.outReqStates[CHPP_GNSS_CONTROL_LOCATION_SESSION],
        request, sizeof(*request), CHRE_GNSS_ASYNC_RESULT_TIMEOUT_NS);
  }

  return result;
}

/**
 * Releases the memory held for the location event callback.
 *
 * @param event Location event to be released.
 */
static void chppGnssClientReleaseLocationEvent(
    struct chreGnssLocationEvent *event) {
  CHPP_FREE_AND_NULLIFY(event);
}

/**
 * Start/stop/modify the raw GNSS measurement session used for clients of the
 * CHRE API.
 *
 * @param enable true to start/modify the session, false to stop the
 *        session. If false, other parameters are ignored.
 * @param minIntervalMs See chreGnssMeasurementSessionStartAsync()
 *
 * @return True indicates the request was sent off to the service.
 */

static bool chppGnssClientControlMeasurementSession(bool enable,
                                                    uint32_t minIntervalMs) {
  bool result = false;

  struct ChppGnssControlMeasurementSessionRequest *request =
      chppAllocClientRequestFixed(
          &gGnssClientContext.client,
          struct ChppGnssControlMeasurementSessionRequest);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else {
    request->header.command = CHPP_GNSS_CONTROL_MEASUREMENT_SESSION;
    request->params.enable = enable;
    request->params.minIntervalMs = minIntervalMs;

    result = chppClientSendTimestampedRequestOrFail(
        &gGnssClientContext.client,
        &gGnssClientContext.outReqStates[CHPP_GNSS_CONTROL_MEASUREMENT_SESSION],
        request, sizeof(*request), CHRE_GNSS_ASYNC_RESULT_TIMEOUT_NS);
  }

  return result;
}

/**
 * Releases the memory held for the measurement event callback.
 *
 * @param event Measurement event to be released.
 */
static void chppGnssClientReleaseMeasurementDataEvent(
    struct chreGnssDataEvent *event) {
  if (event->measurement_count > 0) {
    void *measurements = CHPP_CONST_CAST_POINTER(event->measurements);
    CHPP_FREE_AND_NULLIFY(measurements);
  }

  CHPP_FREE_AND_NULLIFY(event);
}

/**
 * Starts/stops opportunistic delivery of location fixes.
 *
 * @param enable true to turn the passive location listener on, false to
 *        turn it off.
 *
 * @return True indicates the request was sent off to the service.
 */
static bool chppGnssClientConfigurePassiveLocationListener(bool enable) {
  bool result = false;

  struct ChppGnssConfigurePassiveLocationListenerRequest *request =
      chppAllocClientRequestFixed(
          &gGnssClientContext.client,
          struct ChppGnssConfigurePassiveLocationListenerRequest);

  if (request == NULL) {
    CHPP_LOG_OOM();
  } else {
    request->header.command = CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER;
    request->params.enable = enable;

    result = chppClientSendTimestampedRequestOrFail(
        &gGnssClientContext.client,
        &gGnssClientContext
             .outReqStates[CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER],
        request, sizeof(*request), CHPP_REQUEST_TIMEOUT_DEFAULT);
  }

  return result;
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterGnssClient(struct ChppAppState *appContext) {
  memset(&gGnssClientContext, 0, sizeof(gGnssClientContext));
  chppRegisterClient(appContext, (void *)&gGnssClientContext,
                     &gGnssClientContext.client,
                     gGnssClientContext.outReqStates, &kGnssClientConfig);
}

void chppDeregisterGnssClient(struct ChppAppState *appContext) {
  // TODO

  UNUSED_VAR(appContext);
}

struct ChppEndpointState *getChppGnssClientState(void) {
  return &gGnssClientContext.client;
}

#ifdef CHPP_CLIENT_ENABLED_GNSS

#ifdef CHPP_CLIENT_ENABLED_CHRE_GNSS
const struct chrePalGnssApi *chrePalGnssGetApi(uint32_t requestedApiVersion) {
#else
const struct chrePalGnssApi *chppPalGnssGetApi(uint32_t requestedApiVersion) {
#endif

  static const struct chrePalGnssApi api = {
      .moduleVersion = CHPP_PAL_GNSS_API_VERSION,
      .open = chppGnssClientOpen,
      .close = chppGnssClientClose,
      .getCapabilities = chppGnssClientGetCapabilities,
      .controlLocationSession = chppGnssClientControlLocationSession,
      .releaseLocationEvent = chppGnssClientReleaseLocationEvent,
      .controlMeasurementSession = chppGnssClientControlMeasurementSession,
      .releaseMeasurementDataEvent = chppGnssClientReleaseMeasurementDataEvent,
      .configurePassiveLocationListener =
          chppGnssClientConfigurePassiveLocationListener,
  };

  CHPP_STATIC_ASSERT(
      CHRE_PAL_GNSS_API_CURRENT_VERSION == CHPP_PAL_GNSS_API_VERSION,
      "A newer CHRE PAL API version is available. Please update.");

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(api.moduleVersion,
                                        requestedApiVersion)) {
    return NULL;
  } else {
    return &api;
  }
}

#endif
