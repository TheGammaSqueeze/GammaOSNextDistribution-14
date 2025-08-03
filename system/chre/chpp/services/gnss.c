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

#include "chpp/services/gnss.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/common/gnss.h"
#include "chpp/common/gnss_types.h"
#include "chpp/common/standard_uuids.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/services.h"
#include "chre/pal/gnss.h"

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppDispatchGnssRequest(void *serviceContext,
                                                     uint8_t *buf, size_t len);
static void chppGnssServiceNotifyReset(void *serviceContext);

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Configuration parameters for this service
 */
static const struct ChppService kGnssServiceConfig = {
    .descriptor.uuid = CHPP_UUID_GNSS_STANDARD,

    // Human-readable name
    .descriptor.name = "GNSS",

    // Version
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,

    // Notifies service if CHPP is reset
    .resetNotifierFunctionPtr = &chppGnssServiceNotifyReset,

    // Client request dispatch function pointer
    .requestDispatchFunctionPtr = &chppDispatchGnssRequest,

    // Client notification dispatch function pointer
    .notificationDispatchFunctionPtr = NULL,  // Not supported

    // Min length is the entire header
    .minLength = sizeof(struct ChppAppHeader),
};

/**
 * Structure to maintain state for the GNSS service and its Request/Response
 * (RR) functionality.
 */
struct ChppGnssServiceState {
  struct ChppEndpointState service;  // CHPP service state
  const struct chrePalGnssApi *api;  // GNSS PAL API

  // Based on chre/pal/gnss.h and chrePalGnssApi
  struct ChppIncomingRequestState open;             // Service init state
  struct ChppIncomingRequestState close;            // Service deinit state
  struct ChppIncomingRequestState getCapabilities;  // Get Capabilities state
  struct ChppIncomingRequestState
      controlLocationSession;  // Control Location measurement state
  struct ChppIncomingRequestState
      controlMeasurementSession;  // Control Raw GNSS measurement state
  struct ChppIncomingRequestState
      configurePassiveLocationListener;  // Configure Passive location receiving
                                         // state
};

// Note: The CHRE PAL API only allows for one definition - see comment in WWAN
// service for details.
// Note: There is no notion of a cookie in the CHRE GNSS API so we need to use
// the global service state (gGnssServiceContext) directly in all callbacks.
struct ChppGnssServiceState gGnssServiceContext;

/************************************************
 *  Prototypes
 ***********************************************/

static enum ChppAppErrorCode chppGnssServiceOpen(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppGnssServiceClose(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppGnssServiceGetCapabilities(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader);
static enum ChppAppErrorCode chppGnssServiceControlLocationSession(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppGnssServiceControlMeasurementSession(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);
static enum ChppAppErrorCode chppGnssServiceConfigurePassiveLocationListener(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len);

static void chppGnssServiceRequestStateResyncCallback(void);
static void chppGnssServiceLocationStatusChangeCallback(bool enabled,
                                                        uint8_t errorCode);
static void chppGnssServiceLocationEventCallback(
    struct chreGnssLocationEvent *event);
static void chppGnssServiceMeasurementStatusChangeCallback(bool enabled,
                                                           uint8_t errorCode);
static void chppGnssServiceMeasurementEventCallback(
    struct chreGnssDataEvent *event);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Dispatches a client request from the transport layer that is determined to be
 * for the GNSS service. If the result of the dispatch is an error, this
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
static enum ChppAppErrorCode chppDispatchGnssRequest(void *serviceContext,
                                                     uint8_t *buf, size_t len) {
  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  buf += sizeof(struct ChppAppHeader);
  len -= sizeof(struct ChppAppHeader);

  struct ChppGnssServiceState *gnssServiceContext =
      (struct ChppGnssServiceState *)serviceContext;
  struct ChppIncomingRequestState *inReqState = NULL;
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;
  bool dispatched = true;

  switch (rxHeader->command) {
    case CHPP_GNSS_OPEN: {
      inReqState = &gnssServiceContext->open;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceOpen(gnssServiceContext, rxHeader);
      break;
    }

    case CHPP_GNSS_CLOSE: {
      inReqState = &gnssServiceContext->close;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceClose(gnssServiceContext, rxHeader);
      break;
    }

    case CHPP_GNSS_GET_CAPABILITIES: {
      inReqState = &gnssServiceContext->getCapabilities;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceGetCapabilities(gnssServiceContext, rxHeader);
      break;
    }

    case CHPP_GNSS_CONTROL_LOCATION_SESSION: {
      inReqState = &gnssServiceContext->controlLocationSession;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceControlLocationSession(gnssServiceContext,
                                                    rxHeader, buf, len);
      break;
    }

    case CHPP_GNSS_CONTROL_MEASUREMENT_SESSION: {
      inReqState = &gnssServiceContext->controlMeasurementSession;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceControlMeasurementSession(gnssServiceContext,
                                                       rxHeader, buf, len);
      break;
    }

    case CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER: {
      inReqState = &gnssServiceContext->configurePassiveLocationListener;
      chppTimestampIncomingRequest(inReqState, rxHeader);
      error = chppGnssServiceConfigurePassiveLocationListener(
          gnssServiceContext, rxHeader, buf, len);
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
 * Initializes the GNSS service upon an open request from the client and
 * responds to the client with the result.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceOpen(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader) {
  static const struct chrePalGnssCallbacks palCallbacks = {
      .requestStateResync = chppGnssServiceRequestStateResyncCallback,
      .locationStatusChangeCallback =
          chppGnssServiceLocationStatusChangeCallback,
      .locationEventCallback = chppGnssServiceLocationEventCallback,
      .measurementStatusChangeCallback =
          chppGnssServiceMeasurementStatusChangeCallback,
      .measurementEventCallback = chppGnssServiceMeasurementEventCallback,
  };

  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (gnssServiceContext->service.openState == CHPP_OPEN_STATE_OPENED) {
    CHPP_DEBUG_ASSERT_LOG(false, "GNSS service already open");
    error = CHPP_APP_ERROR_INVALID_COMMAND;

  } else if (!gnssServiceContext->api->open(
                 gnssServiceContext->service.appContext->systemApi,
                 &palCallbacks)) {
    CHPP_DEBUG_ASSERT_LOG(false, "GNSS PAL open failed");
    error = CHPP_APP_ERROR_BEYOND_CHPP;

  } else {
    CHPP_LOGD("GNSS service opened");
    gnssServiceContext->service.openState = CHPP_OPEN_STATE_OPENED;

    struct ChppAppHeader *response =
        chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
    size_t responseLen = sizeof(*response);

    if (response == NULL) {
      CHPP_LOG_OOM();
      error = CHPP_APP_ERROR_OOM;
    } else {
      chppSendTimestampedResponseOrFail(gnssServiceContext->service.appContext,
                                        &gnssServiceContext->open, response,
                                        responseLen);
    }
  }

  return error;
}

/**
 * Deinitializes the GNSS service.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceClose(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  gnssServiceContext->api->close();
  gnssServiceContext->service.openState = CHPP_OPEN_STATE_CLOSED;

  CHPP_LOGD("GNSS service closed");

  struct ChppAppHeader *response =
      chppAllocResponseFixed(requestHeader, struct ChppAppHeader);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    error = CHPP_APP_ERROR_OOM;
  } else {
    chppSendTimestampedResponseOrFail(gnssServiceContext->service.appContext,
                                      &gnssServiceContext->close, response,
                                      responseLen);
  }

  return error;
}

/**
 * Notifies the service of an incoming reset.
 *
 * @param serviceContext Maintains status for each service instance.
 */
static void chppGnssServiceNotifyReset(void *serviceContext) {
  struct ChppGnssServiceState *gnssServiceContext =
      (struct ChppGnssServiceState *)serviceContext;

  if (gnssServiceContext->service.openState != CHPP_OPEN_STATE_OPENED) {
    CHPP_LOGW("GNSS service reset but wasn't open");
  } else {
    CHPP_LOGD("GNSS service reset. Closing");
    gnssServiceContext->service.openState = CHPP_OPEN_STATE_CLOSED;
    gnssServiceContext->api->close();
  }
}

/**
 * Retrieves a set of flags indicating the GNSS features supported by the
 * current implementation.
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceGetCapabilities(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader) {
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  struct ChppGnssGetCapabilitiesResponse *response = chppAllocResponseFixed(
      requestHeader, struct ChppGnssGetCapabilitiesResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    error = CHPP_APP_ERROR_OOM;
  } else {
    response->params.capabilities = gnssServiceContext->api->getCapabilities();

    CHPP_LOGD("chppGnssServiceGetCapabilities returning 0x%" PRIx32
              ", %" PRIuSIZE " bytes",
              response->params.capabilities, responseLen);
    chppSendTimestampedResponseOrFail(gnssServiceContext->service.appContext,
                                      &gnssServiceContext->getCapabilities,
                                      response, responseLen);
  }

  return error;
}

/**
 * Start/stop/modify the GNSS location session.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppGnssServiceLocationStatusChangeCallback() will be
 * used to communicate the result of this request (as a service response).
 * A subsequent call to chppGnssServiceLocationEventCallback() will be used to
 * communicate the location fixes (as service notifications).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceControlLocationSession(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (len < sizeof(struct ChppGnssControlLocationSessionParameters)) {
    error = CHPP_APP_ERROR_INVALID_ARG;

  } else {
    struct ChppGnssControlLocationSessionParameters *parameters =
        (struct ChppGnssControlLocationSessionParameters *)buf;

    if (!gnssServiceContext->api->controlLocationSession(
            parameters->enable, parameters->minIntervalMs,
            parameters->minTimeToNextFixMs)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;
    }
  }

  return error;
}

/**
 * Start/stop/modify the raw GNSS measurement session.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppGnssServiceMeasurementStatusChangeCallback() will be
 * used to communicate the result of this request (as a service response).
 * A subsequent call to chppGnssServiceMeasurementEventCallback() will be used
 * to communicate the measurements (as service notifications).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceControlMeasurementSession(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (len < sizeof(struct ChppGnssControlMeasurementSessionParameters)) {
    error = CHPP_APP_ERROR_INVALID_ARG;

  } else {
    struct ChppGnssControlMeasurementSessionParameters *parameters =
        (struct ChppGnssControlMeasurementSessionParameters *)buf;

    if (!gnssServiceContext->api->controlMeasurementSession(
            parameters->enable, parameters->minIntervalMs)) {
      error = CHPP_APP_ERROR_UNSPECIFIED;
    }
  }

  return error;
}

/**
 * Configures whether to opportunistically deliver any location fixes produced
 * for other clients of the GNSS engine.
 *
 * This function returns an error code synchronously.
 * A subsequent call to chppGnssServiceLocationEventCallback() will be used to
 * communicate the location fixes (as service notifications).
 *
 * @param serviceContext Maintains status for each service instance.
 * @param requestHeader App layer header of the request.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates the result of this function call.
 */
static enum ChppAppErrorCode chppGnssServiceConfigurePassiveLocationListener(
    struct ChppGnssServiceState *gnssServiceContext,
    struct ChppAppHeader *requestHeader, uint8_t *buf, size_t len) {
  UNUSED_VAR(requestHeader);
  enum ChppAppErrorCode error = CHPP_APP_ERROR_NONE;

  if (len < sizeof(struct ChppGnssConfigurePassiveLocationListenerParameters)) {
    error = CHPP_APP_ERROR_INVALID_ARG;
  } else {
    struct ChppGnssConfigurePassiveLocationListenerParameters *parameters =
        (struct ChppGnssConfigurePassiveLocationListenerParameters *)buf;

    if (!gnssServiceContext->api->configurePassiveLocationListener(
            parameters->enable)) {
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
            gnssServiceContext->service.appContext,
            &gnssServiceContext->configurePassiveLocationListener, response,
            responseLen);
      }
    }
  }

  return error;
}

/**
 * GNSS PAL callback to request that the core CHRE system re-send requests for
 * any active sessions and its current passive location listener setting.
 */
static void chppGnssServiceRequestStateResyncCallback(void) {
  struct ChppAppHeader *notification =
      chppAllocServiceNotificationFixed(struct ChppAppHeader);
  size_t notificationLen = sizeof(*notification);

  if (notification == NULL) {
    CHPP_LOG_OOM();
    CHPP_ASSERT(false);

  } else {
    notification->handle = gGnssServiceContext.service.handle;
    notification->command = CHPP_GNSS_REQUEST_STATE_RESYNC_NOTIFICATION;

    chppEnqueueTxDatagramOrFail(
        gGnssServiceContext.service.appContext->transportContext, notification,
        notificationLen);
  }
}

/**
 * GNSS PAL callback to inform the CHRE of the result of changes to the location
 * session status.
 */
static void chppGnssServiceLocationStatusChangeCallback(bool enabled,
                                                        uint8_t errorCode) {
  // Recreate request header
  struct ChppAppHeader requestHeader = {
      .handle = gGnssServiceContext.service.handle,
      .transaction = gGnssServiceContext.controlLocationSession.transaction,
      .command = CHPP_GNSS_CONTROL_LOCATION_SESSION,
  };

  struct ChppGnssControlLocationSessionResponse *response =
      chppAllocResponseFixed(&requestHeader,
                             struct ChppGnssControlLocationSessionResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    CHPP_ASSERT(false);

  } else {
    response->enabled = enabled;
    response->errorCode = errorCode;

    chppSendTimestampedResponseOrFail(
        gGnssServiceContext.service.appContext,
        &gGnssServiceContext.controlLocationSession, response, responseLen);
  }
}

/**
 * GNSS PAL callback to pass GNSS location fixes to the core CHRE system.
 */
static void chppGnssServiceLocationEventCallback(
    struct chreGnssLocationEvent *event) {
  // Craft response per parser script
  struct ChppGnssLocationEventWithHeader *notification = NULL;
  size_t notificationLen = 0;

  if (!chppGnssLocationEventFromChre(event, &notification, &notificationLen)) {
    CHPP_LOGE("LocationEvent conversion failed (OOM?)");

    notification = chppMalloc(sizeof(struct ChppAppHeader));
    if (notification == NULL) {
      CHPP_LOG_OOM();
    } else {
      notificationLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notification != NULL) {
    notification->header.handle = gGnssServiceContext.service.handle;
    notification->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notification->header.transaction =
        0;  // Because we don't know this is in response to a Location Session
            // or Passive Location Listener
    notification->header.error =
        (notificationLen > sizeof(struct ChppAppHeader))
            ? CHPP_APP_ERROR_NONE
            : CHPP_APP_ERROR_CONVERSION_FAILED;
    notification->header.command = CHPP_GNSS_LOCATION_RESULT_NOTIFICATION;

    chppEnqueueTxDatagramOrFail(
        gGnssServiceContext.service.appContext->transportContext, notification,
        notificationLen);
  }

  gGnssServiceContext.api->releaseLocationEvent(event);
}

/**
 * GNSS PAL callback to inform the CHRE of the result of changes to the raw GNSS
 * measurement session status.
 */
static void chppGnssServiceMeasurementStatusChangeCallback(bool enabled,
                                                           uint8_t errorCode) {
  // Recreate request header
  struct ChppAppHeader requestHeader = {
      .handle = gGnssServiceContext.service.handle,
      .transaction = gGnssServiceContext.controlMeasurementSession.transaction,
      .command = CHPP_GNSS_CONTROL_MEASUREMENT_SESSION,
  };

  struct ChppGnssControlMeasurementSessionResponse *response =
      chppAllocResponseFixed(&requestHeader,
                             struct ChppGnssControlMeasurementSessionResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    CHPP_ASSERT(false);

  } else {
    response->enabled = enabled;
    response->errorCode = errorCode;

    chppSendTimestampedResponseOrFail(
        gGnssServiceContext.service.appContext,
        &gGnssServiceContext.controlMeasurementSession, response, responseLen);
  }
}

/**
 * GNSS PAL callback to pass raw GNSS measurement data to the core CHRE system.
 */
static void chppGnssServiceMeasurementEventCallback(
    struct chreGnssDataEvent *event) {
  // Craft response per parser script
  struct ChppGnssDataEventWithHeader *notification = NULL;
  size_t notificationLen = 0;

  if (!chppGnssDataEventFromChre(event, &notification, &notificationLen)) {
    CHPP_LOGE("DataEvent conversion failed (OOM?) ID=%" PRIu8,
              gGnssServiceContext.controlMeasurementSession.transaction);

    notification = chppMalloc(sizeof(struct ChppAppHeader));
    if (notification == NULL) {
      CHPP_LOG_OOM();
    } else {
      notificationLen = sizeof(struct ChppAppHeader);
    }
  }

  if (notification != NULL) {
    notification->header.handle = gGnssServiceContext.service.handle;
    notification->header.type = CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION;
    notification->header.transaction =
        gGnssServiceContext.controlMeasurementSession.transaction;
    notification->header.error =
        (notificationLen > sizeof(struct ChppAppHeader))
            ? CHPP_APP_ERROR_NONE
            : CHPP_APP_ERROR_CONVERSION_FAILED;
    notification->header.command = CHPP_GNSS_MEASUREMENT_RESULT_NOTIFICATION;

    chppEnqueueTxDatagramOrFail(
        gGnssServiceContext.service.appContext->transportContext, notification,
        notificationLen);
  }

  gGnssServiceContext.api->releaseMeasurementDataEvent(event);
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterGnssService(struct ChppAppState *appContext) {
  gGnssServiceContext.api = chrePalGnssGetApi(CHPP_PAL_GNSS_API_VERSION);

  if (gGnssServiceContext.api == NULL) {
    CHPP_DEBUG_ASSERT_LOG(false,
                          "GNSS PAL API incompatible. Cannot register service");

  } else {
    chppRegisterService(appContext, (void *)&gGnssServiceContext,
                        &gGnssServiceContext.service, NULL /*outReqState*/,
                        &kGnssServiceConfig);
    CHPP_DEBUG_ASSERT(gGnssServiceContext.service.handle);
  }
}

void chppDeregisterGnssService(struct ChppAppState *appContext) {
  // TODO

  UNUSED_VAR(appContext);
}
