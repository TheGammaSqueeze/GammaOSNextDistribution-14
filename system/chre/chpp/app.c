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

#include "chpp/app.h"

#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "chpp/clients.h"
#include "chpp/clients/discovery.h"
#include "chpp/services.h"
#ifdef CHPP_CLIENT_ENABLED_LOOPBACK
#include "chpp/clients/loopback.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
#include "chpp/clients/timesync.h"
#endif
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/notifier.h"
#include "chpp/pal_api.h"
#include "chpp/services.h"
#include "chpp/services/discovery.h"
#include "chpp/services/loopback.h"
#include "chpp/services/nonhandle.h"
#include "chpp/services/timesync.h"
#include "chpp/time.h"

/************************************************
 *  Prototypes
 ***********************************************/

static bool chppProcessPredefinedClientRequest(struct ChppAppState *context,
                                               uint8_t *buf, size_t len);
static bool chppProcessPredefinedServiceResponse(struct ChppAppState *context,
                                                 uint8_t *buf, size_t len);

static bool chppDatagramLenIsOk(struct ChppAppState *context,
                                const struct ChppAppHeader *rxHeader,
                                size_t len);
static ChppDispatchFunction *chppGetDispatchFunction(
    struct ChppAppState *context, uint8_t handle, enum ChppMessageType type);
#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
static ChppNotifierFunction *chppGetClientResetNotifierFunction(
    struct ChppAppState *context, uint8_t index);
#endif  // CHPP_CLIENT_ENABLED_DISCOVERY
static ChppNotifierFunction *chppGetServiceResetNotifierFunction(
    struct ChppAppState *context, uint8_t index);
static inline const struct ChppService *chppServiceOfHandle(
    struct ChppAppState *appContext, uint8_t handle);
static inline const struct ChppClient *chppClientOfHandle(
    struct ChppAppState *appContext, uint8_t handle);
static inline struct ChppEndpointState *chppServiceStateOfHandle(
    struct ChppAppState *appContext, uint8_t handle);
static inline struct ChppEndpointState *chppClientStateOfHandle(
    struct ChppAppState *appContext, uint8_t handle);
static struct ChppEndpointState *chppClientOrServiceStateOfHandle(
    struct ChppAppState *appContext, uint8_t handle, enum ChppMessageType type);

static void chppProcessPredefinedHandleDatagram(struct ChppAppState *context,
                                                uint8_t *buf, size_t len);
static void chppProcessNegotiatedHandleDatagram(struct ChppAppState *context,
                                                uint8_t *buf, size_t len);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Processes a client request that is determined to be for a predefined CHPP
 * service.
 *
 * @param context State of the app layer.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return False if handle is invalid. True otherwise.
 */
static bool chppProcessPredefinedClientRequest(struct ChppAppState *context,
                                               uint8_t *buf, size_t len) {
  const struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  bool handleValid = true;
  bool dispatchResult = true;

  switch (rxHeader->handle) {
    case CHPP_HANDLE_LOOPBACK: {
      dispatchResult = chppDispatchLoopbackClientRequest(context, buf, len);
      break;
    }

    case CHPP_HANDLE_TIMESYNC: {
      dispatchResult = chppDispatchTimesyncClientRequest(context, buf, len);
      break;
    }

    case CHPP_HANDLE_DISCOVERY: {
      dispatchResult = chppDispatchDiscoveryClientRequest(context, buf, len);
      break;
    }

    default: {
      handleValid = false;
    }
  }

  if (dispatchResult == false) {
    CHPP_LOGE("H#%" PRIu8 " unknown request. cmd=%#x, ID=%" PRIu8,
              rxHeader->handle, rxHeader->command, rxHeader->transaction);
  }

  return handleValid;
}

/**
 * Processes a service response that is determined to be for a predefined CHPP
 * client.
 *
 * @param context State of the app layer.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return False if handle is invalid. True otherwise.
 */
static bool chppProcessPredefinedServiceResponse(struct ChppAppState *context,
                                                 uint8_t *buf, size_t len) {
  CHPP_DEBUG_NOT_NULL(buf);
  // Possibly unused if compiling without the clients below enabled
  UNUSED_VAR(context);
  UNUSED_VAR(len);

  const struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  bool handleValid = true;
  bool dispatchResult = true;

  switch (rxHeader->handle) {
#ifdef CHPP_CLIENT_ENABLED_LOOPBACK
    case CHPP_HANDLE_LOOPBACK: {
      dispatchResult = chppDispatchLoopbackServiceResponse(context, buf, len);
      break;
    }
#endif  // CHPP_CLIENT_ENABLED_LOOPBACK

#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
    case CHPP_HANDLE_TIMESYNC: {
      dispatchResult = chppDispatchTimesyncServiceResponse(context, buf, len);
      break;
    }
#endif  // CHPP_CLIENT_ENABLED_TIMESYNC

#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
    case CHPP_HANDLE_DISCOVERY: {
      dispatchResult = chppDispatchDiscoveryServiceResponse(context, buf, len);
      break;
    }
#endif  // CHPP_CLIENT_ENABLED_DISCOVERY

    default: {
      handleValid = false;
    }
  }

  if (dispatchResult == false) {
    CHPP_LOGE("H#%" PRIu8 " unknown response. cmd=%#x, ID=%" PRIu8
              ", len=%" PRIuSIZE,
              rxHeader->handle, rxHeader->command, rxHeader->transaction, len);
  }

  return handleValid;
}

/**
 * Verifies if the length of a Rx Datagram from the transport layer is
 * sufficient for the associated service/client.
 *
 * @param context State of the app layer.
 * @param rxHeader The pointer to the datagram RX header.
 * @param len Length of the datagram in bytes.
 *
 * @return true if length is ok.
 */
static bool chppDatagramLenIsOk(struct ChppAppState *context,
                                const struct ChppAppHeader *rxHeader,
                                size_t len) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_DEBUG_NOT_NULL(rxHeader);

  size_t minLen = SIZE_MAX;
  uint8_t handle = rxHeader->handle;

  if (handle < CHPP_HANDLE_NEGOTIATED_RANGE_START) {  // Predefined
    switch (handle) {
      case CHPP_HANDLE_NONE:
        minLen = sizeof_member(struct ChppAppHeader, handle);
        break;

      case CHPP_HANDLE_LOOPBACK:
        minLen = sizeof_member(struct ChppAppHeader, handle) +
                 sizeof_member(struct ChppAppHeader, type);
        break;

      case CHPP_HANDLE_TIMESYNC:
      case CHPP_HANDLE_DISCOVERY:
        minLen = sizeof(struct ChppAppHeader);
        break;

      default:
        // len remains SIZE_MAX
        CHPP_LOGE("Invalid H#%" PRIu8, handle);
        return false;
    }

  } else {  // Negotiated
    enum ChppMessageType messageType =
        CHPP_APP_GET_MESSAGE_TYPE(rxHeader->type);

    switch (messageType) {
      case CHPP_MESSAGE_TYPE_CLIENT_REQUEST:
      case CHPP_MESSAGE_TYPE_CLIENT_RESPONSE:
      case CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION: {
        const struct ChppService *service =
            chppServiceOfHandle(context, handle);
        if (service != NULL) {
          minLen = service->minLength;
        }
        break;
      }
      case CHPP_MESSAGE_TYPE_SERVICE_RESPONSE:
      case CHPP_MESSAGE_TYPE_SERVICE_REQUEST:
      case CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION: {
        const struct ChppClient *client = chppClientOfHandle(context, handle);
        if (client != NULL) {
          minLen = client->minLength;
        }
        break;
      }
      default:
        CHPP_LOGE("Invalid type=%d or H#%" PRIu8, messageType, handle);
        return false;
    }
  }

  if (len < minLen) {
    CHPP_LOGE("Datagram len=%" PRIuSIZE " < %" PRIuSIZE " for H#%" PRIu8, len,
              minLen, handle);
    return false;
  }

  return true;
}

/**
 * Returns the dispatch function of a particular negotiated client/service
 * handle and message type.
 *
 * Returns null if it is unsupported by the service.
 *
 * @param context State of the app layer.
 * @param handle Handle number for the client/service.
 * @param type Message type.
 *
 * @return Pointer to a function that dispatches incoming datagrams for any
 * particular client/service.
 */
static ChppDispatchFunction *chppGetDispatchFunction(
    struct ChppAppState *context, uint8_t handle, enum ChppMessageType type) {
  CHPP_DEBUG_NOT_NULL(context);
  // chppDatagramLenIsOk() has already confirmed that the handle # is valid.
  // Therefore, no additional checks are necessary for chppClientOfHandle(),
  // chppServiceOfHandle(), or chppClientOrServiceStateOfHandle().

  // Make sure the client is open before it can receive any message:
  switch (CHPP_APP_GET_MESSAGE_TYPE(type)) {
    case CHPP_MESSAGE_TYPE_SERVICE_RESPONSE:
    case CHPP_MESSAGE_TYPE_SERVICE_REQUEST:
    case CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION: {
      struct ChppEndpointState *clientState =
          chppClientStateOfHandle(context, handle);
      if (clientState->openState == CHPP_OPEN_STATE_CLOSED) {
        CHPP_LOGE("RX service response but client closed");
        return NULL;
      }
      break;
    }
    default:
      // no check needed on the service side
      break;
  }

  switch (CHPP_APP_GET_MESSAGE_TYPE(type)) {
    case CHPP_MESSAGE_TYPE_CLIENT_REQUEST:
      return chppServiceOfHandle(context, handle)->requestDispatchFunctionPtr;
    case CHPP_MESSAGE_TYPE_SERVICE_RESPONSE:
      return chppClientOfHandle(context, handle)->responseDispatchFunctionPtr;
    case CHPP_MESSAGE_TYPE_SERVICE_REQUEST:
      return chppClientOfHandle(context, handle)->requestDispatchFunctionPtr;
    case CHPP_MESSAGE_TYPE_CLIENT_RESPONSE:
      return chppServiceOfHandle(context, handle)->responseDispatchFunctionPtr;
    case CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION:
      return chppServiceOfHandle(context, handle)
          ->notificationDispatchFunctionPtr;
    case CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION:
      return chppClientOfHandle(context, handle)
          ->notificationDispatchFunctionPtr;
  }

  return NULL;
}

#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
/**
 * Returns the reset notification function pointer of a particular negotiated
 * client.
 *
 * Returns null for clients that do not need or support a reset notification.
 *
 * @param context State of the app layer.
 * @param index Index of the registered client.
 *
 * @return Pointer to the reset notification function.
 */
static ChppNotifierFunction *chppGetClientResetNotifierFunction(
    struct ChppAppState *context, uint8_t index) {
  CHPP_DEBUG_NOT_NULL(context);
  return context->registeredClients[index]->resetNotifierFunctionPtr;
}
#endif  // CHPP_CLIENT_ENABLED_DISCOVERY

/**
 * Returns the reset function pointer of a particular registered service.
 *
 * Returns null for services that do not need or support a reset notification.
 *
 * @param context State of the app layer.
 * @param index Index of the registered service.
 *
 * @return Pointer to the reset function.
 */
ChppNotifierFunction *chppGetServiceResetNotifierFunction(
    struct ChppAppState *context, uint8_t index) {
  CHPP_DEBUG_NOT_NULL(context);
  return context->registeredServices[index]->resetNotifierFunctionPtr;
}

/**
 * Returns a pointer to the ChppService struct of the service matched to a
 * negotiated handle.
 *
 * Returns null if a service doesn't exist for the handle.
 *
 * @param context State of the app layer.
 * @param handle Handle number.
 *
 * @return Pointer to the ChppService struct of a particular service handle.
 */
static inline const struct ChppService *chppServiceOfHandle(
    struct ChppAppState *context, uint8_t handle) {
  CHPP_DEBUG_NOT_NULL(context);
  uint8_t serviceIndex = CHPP_SERVICE_INDEX_OF_HANDLE(handle);
  if (serviceIndex < context->registeredServiceCount) {
    return context->registeredServices[serviceIndex];
  }

  return NULL;
}

/**
 * Returns a pointer to the ChppClient struct of the client matched to a
 * negotiated handle.
 *
 * Returns null if a client doesn't exist for the handle.
 *
 * @param context State of the app layer.
 * @param handle Handle number.
 *
 * @return Pointer to the ChppClient struct matched to a particular handle.
 */
static inline const struct ChppClient *chppClientOfHandle(
    struct ChppAppState *context, uint8_t handle) {
  CHPP_DEBUG_NOT_NULL(context);
  uint8_t serviceIndex = CHPP_SERVICE_INDEX_OF_HANDLE(handle);
  if (serviceIndex < context->discoveredServiceCount) {
    uint8_t clientIndex = context->clientIndexOfServiceIndex[serviceIndex];
    if (clientIndex < context->registeredClientCount) {
      return context->registeredClients[clientIndex];
    }
  }

  return NULL;
}

/**
 * Returns the service state for a given handle.
 *
 * The caller must pass a valid handle.
 *
 * @param context State of the app layer.
 * @param handle Handle number for the service.
 *
 * @return Pointer to a ChppEndpointState.
 */
static inline struct ChppEndpointState *chppServiceStateOfHandle(
    struct ChppAppState *context, uint8_t handle) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_DEBUG_ASSERT(CHPP_SERVICE_INDEX_OF_HANDLE(handle) <
                    context->registeredServiceCount);

  const uint8_t serviceIdx = CHPP_SERVICE_INDEX_OF_HANDLE(handle);
  return context->registeredServiceStates[serviceIdx];
}

/**
 * Returns a pointer to the client state for a given handle.
 *
 * The caller must pass a valid handle.
 *
 * @param context State of the app layer.
 * @param handle Handle number for the service.
 *
 * @return Pointer to the endpoint state.
 */
static inline struct ChppEndpointState *chppClientStateOfHandle(
    struct ChppAppState *context, uint8_t handle) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_DEBUG_ASSERT(CHPP_SERVICE_INDEX_OF_HANDLE(handle) <
                    context->registeredClientCount);
  const uint8_t serviceIdx = CHPP_SERVICE_INDEX_OF_HANDLE(handle);
  const uint8_t clientIdx = context->clientIndexOfServiceIndex[serviceIdx];
  return context->registeredClientStates[clientIdx]->context;
}

/**
 * Returns a pointer to the client or service state for a given handle.
 *
 * The caller must pass a valid handle.
 *
 * @param appContext State of the app layer.
 * @param handle Handle number for the service.
 * @param type Message type (indicates if this is for a client or service).
 *
 * @return Pointer to the endpoint state (NULL if wrong type).
 */
static struct ChppEndpointState *chppClientOrServiceStateOfHandle(
    struct ChppAppState *appContext, uint8_t handle,
    enum ChppMessageType type) {
  switch (CHPP_APP_GET_MESSAGE_TYPE(type)) {
    case CHPP_MESSAGE_TYPE_CLIENT_REQUEST:
    case CHPP_MESSAGE_TYPE_CLIENT_RESPONSE:
    case CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION:
      return chppServiceStateOfHandle(appContext, handle);
    case CHPP_MESSAGE_TYPE_SERVICE_REQUEST:
    case CHPP_MESSAGE_TYPE_SERVICE_RESPONSE:
    case CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION:
      return chppClientStateOfHandle(appContext, handle);
    default:
      CHPP_LOGE("Unknown type=0x%" PRIx8 " (H#%" PRIu8 ")", type, handle);
      return NULL;
  }
}

/**
 * Processes a received datagram that is determined to be for a predefined CHPP
 * service. Responds with an error if unsuccessful.
 *
 * Predefined requests are only sent by the client side.
 * Predefined responses are only sent by the service side.
 *
 * @param context State of the app layer.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppProcessPredefinedHandleDatagram(struct ChppAppState *context,
                                                uint8_t *buf, size_t len) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_DEBUG_NOT_NULL(buf);

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  bool success = false;

  switch (CHPP_APP_GET_MESSAGE_TYPE(rxHeader->type)) {
    case CHPP_MESSAGE_TYPE_CLIENT_REQUEST: {
      success = chppProcessPredefinedClientRequest(context, buf, len);
      break;
    }
    case CHPP_MESSAGE_TYPE_SERVICE_RESPONSE: {
      success = chppProcessPredefinedServiceResponse(context, buf, len);
      break;
    }
    default:
      // Predefined client/services do not use
      // - notifications,
      // - service requests / client responses
      break;
  }

  if (!success) {
    CHPP_LOGE("H#%" PRIu8 " undefined msg type=0x%" PRIx8 " (len=%" PRIuSIZE
              ", ID=%" PRIu8 ")",
              rxHeader->handle, rxHeader->type, len, rxHeader->transaction);
    chppEnqueueTxErrorDatagram(context->transportContext,
                               CHPP_TRANSPORT_ERROR_APPLAYER);
  }
}

/**
 * Processes a received datagram that is determined to be for a negotiated CHPP
 * client or service.
 *
 * The datagram is processed by the dispatch function matching the datagram
 * type. @see ChppService and ChppClient.
 *
 * If a request dispatch function returns an error (anything different from
 * CHPP_APP_ERROR_NONE) then an error response is automatically sent back to the
 * remote endpoint.
 *
 * @param appContext State of the app layer.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
static void chppProcessNegotiatedHandleDatagram(struct ChppAppState *appContext,
                                                uint8_t *buf, size_t len) {
  CHPP_DEBUG_NOT_NULL(appContext);
  CHPP_DEBUG_NOT_NULL(buf);

  const struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  enum ChppMessageType messageType = CHPP_APP_GET_MESSAGE_TYPE(rxHeader->type);

  // Could be either the client or the service state depending on the message
  // type.
  struct ChppEndpointState *endpointState = chppClientOrServiceStateOfHandle(
      appContext, rxHeader->handle, messageType);
  if (endpointState == NULL) {
    CHPP_LOGE("H#%" PRIu8 " missing ctx (msg=0x%" PRIx8 " len=%" PRIuSIZE
              ", ID=%" PRIu8 ")",
              rxHeader->handle, rxHeader->type, len, rxHeader->transaction);
    chppEnqueueTxErrorDatagram(appContext->transportContext,
                               CHPP_TRANSPORT_ERROR_APPLAYER);
    CHPP_DEBUG_ASSERT(false);
    return;
  }

  ChppDispatchFunction *dispatchFunc =
      chppGetDispatchFunction(appContext, rxHeader->handle, messageType);
  if (dispatchFunc == NULL) {
    CHPP_LOGE("H#%" PRIu8 " unsupported msg=0x%" PRIx8 " (len=%" PRIuSIZE
              ", ID=%" PRIu8 ")",
              rxHeader->handle, rxHeader->type, len, rxHeader->transaction);
    chppEnqueueTxErrorDatagram(appContext->transportContext,
                               CHPP_TRANSPORT_ERROR_APPLAYER);
    return;
  }

  // All good. Dispatch datagram and possibly notify a waiting client
  enum ChppAppErrorCode error = dispatchFunc(endpointState->context, buf, len);

  if (error != CHPP_APP_ERROR_NONE) {
    CHPP_LOGE("RX dispatch err=0x%" PRIx16 " H#%" PRIu8 " type=0x%" PRIx8
              " ID=%" PRIu8 " cmd=0x%" PRIx16 " len=%" PRIuSIZE,
              error, rxHeader->handle, rxHeader->type, rxHeader->transaction,
              rxHeader->command, len);

    // Requests require a dispatch failure response.
    if (messageType == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ||
        messageType == CHPP_MESSAGE_TYPE_SERVICE_REQUEST) {
      struct ChppAppHeader *response =
          chppAllocResponseFixed(rxHeader, struct ChppAppHeader);
      if (response != NULL) {
        response->error = (uint8_t)error;
        chppEnqueueTxDatagramOrFail(appContext->transportContext, response,
                                    sizeof(*response));
      }
    }
    return;
  }

  // Datagram is a response.
  // Check for synchronous operation and notify waiting endpoint if needed.
  if (messageType == CHPP_MESSAGE_TYPE_SERVICE_RESPONSE ||
      messageType == CHPP_MESSAGE_TYPE_CLIENT_RESPONSE) {
    struct ChppSyncResponse *syncResponse = &endpointState->syncResponse;
    chppMutexLock(&syncResponse->mutex);
    syncResponse->ready = true;
    CHPP_LOGD("Finished dispatching a response -> synchronous notification");
    chppConditionVariableSignal(&syncResponse->condVar);
    chppMutexUnlock(&syncResponse->mutex);
  }
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppAppInit(struct ChppAppState *appContext,
                 struct ChppTransportState *transportContext) {
  // Default initialize all service/clients
  struct ChppClientServiceSet set;
  memset(&set, 0xff, sizeof(set));  // set all bits to 1

  chppAppInitWithClientServiceSet(appContext, transportContext, set);
}

void chppAppInitWithClientServiceSet(
    struct ChppAppState *appContext,
    struct ChppTransportState *transportContext,
    struct ChppClientServiceSet clientServiceSet) {
  CHPP_NOT_NULL(appContext);
  CHPP_DEBUG_NOT_NULL(transportContext);

  CHPP_LOGD("App Init");

  memset(appContext, 0, sizeof(*appContext));

  appContext->clientServiceSet = clientServiceSet;
  appContext->transportContext = transportContext;
  appContext->nextClientRequestTimeoutNs = CHPP_TIME_MAX;
  appContext->nextServiceRequestTimeoutNs = CHPP_TIME_MAX;

  chppPalSystemApiInit(appContext);

#ifdef CHPP_SERVICE_ENABLED
  chppRegisterCommonServices(appContext);
#endif

#ifdef CHPP_CLIENT_ENABLED
  chppRegisterCommonClients(appContext);
  chppInitBasicClients(appContext);
#endif
}

void chppAppDeinit(struct ChppAppState *appContext) {
  CHPP_LOGD("App deinit");

#ifdef CHPP_CLIENT_ENABLED
  chppDeinitMatchedClients(appContext);
  chppDeinitBasicClients(appContext);
  chppDeregisterCommonClients(appContext);
#endif

#ifdef CHPP_SERVICE_ENABLED
  chppDeregisterCommonServices(appContext);
#endif

  chppPalSystemApiDeinit(appContext);
}

void chppAppProcessRxDatagram(struct ChppAppState *context, uint8_t *buf,
                              size_t len) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_DEBUG_NOT_NULL(buf);

  const struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (len == 0) {
    CHPP_DEBUG_ASSERT_LOG(false, "App rx w/ len 0");

  } else if (len < sizeof(struct ChppAppHeader)) {
    uint8_t *handle = (uint8_t *)buf;
    CHPP_LOGD("RX datagram len=%" PRIuSIZE " H#%" PRIu8, len, *handle);

  } else if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    CHPP_LOGE("RX datagram len=%" PRIuSIZE " H#%" PRIu8 " type=0x%" PRIx8
              " ID=%" PRIu8 " ERR=%" PRIu8 " cmd=0x%" PRIx16,
              len, rxHeader->handle, rxHeader->type, rxHeader->transaction,
              rxHeader->error, rxHeader->command);
  } else {
    CHPP_LOGD("RX datagram len=%" PRIuSIZE " H#%" PRIu8 " type=0x%" PRIx8
              " ID=%" PRIu8 " err=%" PRIu8 " cmd=0x%" PRIx16,
              len, rxHeader->handle, rxHeader->type, rxHeader->transaction,
              rxHeader->error, rxHeader->command);
  }

  if (!chppDatagramLenIsOk(context, rxHeader, len)) {
    chppEnqueueTxErrorDatagram(context->transportContext,
                               CHPP_TRANSPORT_ERROR_APPLAYER);

  } else {
    if (rxHeader->handle == CHPP_HANDLE_NONE) {
      chppDispatchNonHandle(context, buf, len);

    } else if (rxHeader->handle < CHPP_HANDLE_NEGOTIATED_RANGE_START) {
      chppProcessPredefinedHandleDatagram(context, buf, len);

    } else {
      chppProcessNegotiatedHandleDatagram(context, buf, len);
    }
  }

  chppDatagramProcessDoneCb(context->transportContext, buf);
}

void chppAppProcessReset(struct ChppAppState *context) {
  CHPP_DEBUG_NOT_NULL(context);

#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
  if (!context->isDiscoveryComplete) {
    chppInitiateDiscovery(context);

  } else {
    // Notify matched clients that a reset happened
    for (uint8_t i = 0; i < context->discoveredServiceCount; i++) {
      uint8_t clientIndex = context->clientIndexOfServiceIndex[i];
      if (clientIndex != CHPP_CLIENT_INDEX_NONE) {
        // Discovered service has a matched client
        ChppNotifierFunction *ResetNotifierFunction =
            chppGetClientResetNotifierFunction(context, clientIndex);

        CHPP_LOGD("Client #%" PRIu8 " (H#%d) reset notifier found=%d",
                  clientIndex, CHPP_SERVICE_HANDLE_OF_INDEX(i),
                  (ResetNotifierFunction != NULL));

        if (ResetNotifierFunction != NULL) {
          ResetNotifierFunction(
              context->registeredClientStates[clientIndex]->context);
        }
      }
    }
  }
#endif  // CHPP_CLIENT_ENABLED_DISCOVERY

  // Notify registered services that a reset happened
  for (uint8_t i = 0; i < context->registeredServiceCount; i++) {
    ChppNotifierFunction *ResetNotifierFunction =
        chppGetServiceResetNotifierFunction(context, i);

    CHPP_LOGD("Service #%" PRIu8 " (H#%d) reset notifier found=%d", i,
              CHPP_SERVICE_HANDLE_OF_INDEX(i), (ResetNotifierFunction != NULL));

    if (ResetNotifierFunction != NULL) {
      ResetNotifierFunction(context->registeredServiceStates[i]->context);
    }
  }

#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
  // Reinitialize time offset
  chppTimesyncClientReset(context);
#endif
}

void chppUuidToStr(const uint8_t uuid[CHPP_SERVICE_UUID_LEN],
                   char strOut[CHPP_SERVICE_UUID_STRING_LEN]) {
  snprintf(
      strOut, CHPP_SERVICE_UUID_STRING_LEN,
      "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
      uuid[0], uuid[1], uuid[2], uuid[3], uuid[4], uuid[5], uuid[6], uuid[7],
      uuid[8], uuid[9], uuid[10], uuid[11], uuid[12], uuid[13], uuid[14],
      uuid[15]);
}

uint8_t chppAppErrorToChreError(uint8_t chppError) {
  switch (chppError) {
    case CHPP_APP_ERROR_NONE:
    case CHPP_APP_ERROR_INVALID_ARG:
    case CHPP_APP_ERROR_BUSY:
    case CHPP_APP_ERROR_OOM:
    case CHPP_APP_ERROR_UNSUPPORTED:
    case CHPP_APP_ERROR_TIMEOUT:
    case CHPP_APP_ERROR_DISABLED:
    case CHPP_APP_ERROR_RATELIMITED: {
      // CHRE and CHPP error values are identical in these cases
      return chppError;
    }
    default: {
      return CHRE_ERROR;
    }
  }
}

uint8_t chppAppShortResponseErrorHandler(uint8_t *buf, size_t len,
                                         const char *responseName) {
  CHPP_DEBUG_NOT_NULL(buf);
  CHPP_DEBUG_NOT_NULL(responseName);

  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));
  const struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;

  if (rxHeader->error == CHPP_APP_ERROR_NONE) {
    CHPP_LOGE("%s resp short len=%" PRIuSIZE, responseName, len);
    return CHRE_ERROR;
  }

  CHPP_LOGD("%s resp short len=%" PRIuSIZE, responseName, len);
  return chppAppErrorToChreError(rxHeader->error);
}

struct ChppAppHeader *chppAllocNotification(uint8_t type, size_t len) {
  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));
  CHPP_ASSERT(type == CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION ||
              type == CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION);

  struct ChppAppHeader *notification = chppMalloc(len);
  if (notification != NULL) {
    notification->type = type;
    notification->handle = CHPP_HANDLE_NONE;
    notification->transaction = 0;
    notification->error = CHPP_APP_ERROR_NONE;
    notification->command = CHPP_APP_COMMAND_NONE;
  } else {
    CHPP_LOG_OOM();
  }
  return notification;
}

struct ChppAppHeader *chppAllocRequest(uint8_t type,
                                       struct ChppEndpointState *endpointState,
                                       size_t len) {
  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));
  CHPP_ASSERT(type == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ||
              type == CHPP_MESSAGE_TYPE_SERVICE_REQUEST);
  CHPP_DEBUG_NOT_NULL(endpointState);

  struct ChppAppHeader *request = chppMalloc(len);
  if (request != NULL) {
    request->handle = endpointState->handle;
    request->type = type;
    request->transaction = endpointState->transaction;
    request->error = CHPP_APP_ERROR_NONE;
    request->command = CHPP_APP_COMMAND_NONE;

    endpointState->transaction++;
  } else {
    CHPP_LOG_OOM();
  }
  return request;
}

struct ChppAppHeader *chppAllocResponse(
    const struct ChppAppHeader *requestHeader, size_t len) {
  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));
  CHPP_DEBUG_NOT_NULL(requestHeader);
  uint8_t type = requestHeader->type;
  CHPP_ASSERT(type == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ||
              type == CHPP_MESSAGE_TYPE_SERVICE_REQUEST);

  struct ChppAppHeader *response = chppMalloc(len);
  if (response != NULL) {
    *response = *requestHeader;
    response->type = type == CHPP_MESSAGE_TYPE_CLIENT_REQUEST
                         ? CHPP_MESSAGE_TYPE_SERVICE_RESPONSE
                         : CHPP_MESSAGE_TYPE_CLIENT_RESPONSE;
    response->error = CHPP_APP_ERROR_NONE;
  } else {
    CHPP_LOG_OOM();
  }
  return response;
}

void chppTimestampIncomingRequest(struct ChppIncomingRequestState *inReqState,
                                  const struct ChppAppHeader *requestHeader) {
  CHPP_DEBUG_NOT_NULL(inReqState);
  CHPP_DEBUG_NOT_NULL(requestHeader);
  if (inReqState->responseTimeNs == CHPP_TIME_NONE &&
      inReqState->requestTimeNs != CHPP_TIME_NONE) {
    CHPP_LOGE("RX dupe req t=%" PRIu64,
              inReqState->requestTimeNs / CHPP_NSEC_PER_MSEC);
  }
  inReqState->requestTimeNs = chppGetCurrentTimeNs();
  inReqState->responseTimeNs = CHPP_TIME_NONE;
  inReqState->transaction = requestHeader->transaction;
}

void chppTimestampOutgoingRequest(struct ChppAppState *appState,
                                  struct ChppOutgoingRequestState *outReqState,
                                  const struct ChppAppHeader *requestHeader,
                                  uint64_t timeoutNs) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_NOT_NULL(outReqState);
  CHPP_DEBUG_NOT_NULL(requestHeader);
  enum ChppMessageType msgType = requestHeader->type;
  enum ChppEndpointType endpointType =
      msgType == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ? CHPP_ENDPOINT_CLIENT
                                                  : CHPP_ENDPOINT_SERVICE;

  CHPP_ASSERT(msgType == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ||
              msgType == CHPP_MESSAGE_TYPE_SERVICE_REQUEST);

  if (outReqState->requestState == CHPP_REQUEST_STATE_REQUEST_SENT) {
    CHPP_LOGE("Dupe req ID=%" PRIu8 " existing ID=%" PRIu8 " from t=%" PRIu64,
              requestHeader->transaction, outReqState->transaction,
              outReqState->requestTimeNs / CHPP_NSEC_PER_MSEC);

    // Clear a possible pending timeout from the previous request
    outReqState->responseTimeNs = CHPP_TIME_MAX;
    chppRecalculateNextTimeout(appState, endpointType);
  }

  outReqState->requestTimeNs = chppGetCurrentTimeNs();
  outReqState->requestState = CHPP_REQUEST_STATE_REQUEST_SENT;
  outReqState->transaction = requestHeader->transaction;

  uint64_t *nextRequestTimeoutNs =
      getNextRequestTimeoutNs(appState, endpointType);

  if (timeoutNs == CHPP_REQUEST_TIMEOUT_INFINITE) {
    outReqState->responseTimeNs = CHPP_TIME_MAX;

  } else {
    outReqState->responseTimeNs = timeoutNs + outReqState->requestTimeNs;

    *nextRequestTimeoutNs =
        MIN(*nextRequestTimeoutNs, outReqState->responseTimeNs);
  }

  CHPP_LOGD("Timestamp req ID=%" PRIu8 " at t=%" PRIu64 " timeout=%" PRIu64
            " (requested=%" PRIu64 "), next timeout=%" PRIu64,
            outReqState->transaction,
            outReqState->requestTimeNs / CHPP_NSEC_PER_MSEC,
            outReqState->responseTimeNs / CHPP_NSEC_PER_MSEC,
            timeoutNs / CHPP_NSEC_PER_MSEC,
            *nextRequestTimeoutNs / CHPP_NSEC_PER_MSEC);
}

bool chppTimestampIncomingResponse(struct ChppAppState *appState,
                                   struct ChppOutgoingRequestState *outReqState,
                                   const struct ChppAppHeader *responseHeader) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_NOT_NULL(outReqState);
  CHPP_DEBUG_NOT_NULL(responseHeader);

  uint8_t type = responseHeader->type;

  CHPP_ASSERT(type == CHPP_MESSAGE_TYPE_CLIENT_RESPONSE ||
              type == CHPP_MESSAGE_TYPE_SERVICE_RESPONSE);

  bool success = false;
  uint64_t responseTime = chppGetCurrentTimeNs();

  switch (outReqState->requestState) {
    case CHPP_REQUEST_STATE_NONE: {
      CHPP_LOGE("Resp with no req t=%" PRIu64,
                responseTime / CHPP_NSEC_PER_MSEC);
      break;
    }

    case CHPP_REQUEST_STATE_RESPONSE_RCV: {
      CHPP_LOGE("Extra resp at t=%" PRIu64 " for req t=%" PRIu64,
                responseTime / CHPP_NSEC_PER_MSEC,
                outReqState->requestTimeNs / CHPP_NSEC_PER_MSEC);
      break;
    }

    case CHPP_REQUEST_STATE_RESPONSE_TIMEOUT: {
      CHPP_LOGE("Late resp at t=%" PRIu64 " for req t=%" PRIu64,
                responseTime / CHPP_NSEC_PER_MSEC,
                outReqState->requestTimeNs / CHPP_NSEC_PER_MSEC);
      break;
    }

    case CHPP_REQUEST_STATE_REQUEST_SENT: {
      if (responseHeader->transaction != outReqState->transaction) {
        CHPP_LOGE("Invalid resp ID=%" PRIu8 " at t=%" PRIu64
                  " expected=%" PRIu8,
                  responseHeader->transaction,
                  responseTime / CHPP_NSEC_PER_MSEC, outReqState->transaction);
      } else {
        outReqState->requestState = (responseTime > outReqState->responseTimeNs)
                                        ? CHPP_REQUEST_STATE_RESPONSE_TIMEOUT
                                        : CHPP_REQUEST_STATE_RESPONSE_RCV;
        success = true;

        CHPP_LOGD(
            "Timestamp resp ID=%" PRIu8 " req t=%" PRIu64 " resp t=%" PRIu64
            " timeout t=%" PRIu64 " (RTT=%" PRIu64 ", timeout = %s)",
            outReqState->transaction,
            outReqState->requestTimeNs / CHPP_NSEC_PER_MSEC,
            responseTime / CHPP_NSEC_PER_MSEC,
            outReqState->responseTimeNs / CHPP_NSEC_PER_MSEC,
            (responseTime - outReqState->requestTimeNs) / CHPP_NSEC_PER_MSEC,
            (responseTime > outReqState->responseTimeNs) ? "yes" : "no");
      }
      break;
    }

    default: {
      CHPP_DEBUG_ASSERT_LOG(false, "Invalid req state");
    }
  }

  if (success) {
    // When the received request is the next one that was expected
    // to timeout we need to recompute the timeout considering the
    // other pending requests.
    enum ChppEndpointType endpointType =
        type == CHPP_MESSAGE_TYPE_SERVICE_RESPONSE ? CHPP_ENDPOINT_CLIENT
                                                   : CHPP_ENDPOINT_SERVICE;
    if (outReqState->responseTimeNs ==
        *getNextRequestTimeoutNs(appState, endpointType)) {
      chppRecalculateNextTimeout(appState, endpointType);
    }
    outReqState->responseTimeNs = responseTime;
  }
  return success;
}

uint64_t chppTimestampOutgoingResponse(
    struct ChppIncomingRequestState *inReqState) {
  CHPP_DEBUG_NOT_NULL(inReqState);

  uint64_t previousResponseTime = inReqState->responseTimeNs;
  inReqState->responseTimeNs = chppGetCurrentTimeNs();
  return previousResponseTime;
}

bool chppSendTimestampedResponseOrFail(
    struct ChppAppState *appState, struct ChppIncomingRequestState *inReqState,
    void *buf, size_t len) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_NOT_NULL(inReqState);
  CHPP_DEBUG_NOT_NULL(buf);
  uint64_t previousResponseTime = chppTimestampOutgoingResponse(inReqState);

  if (inReqState->requestTimeNs == CHPP_TIME_NONE) {
    CHPP_LOGE("TX response w/ no req t=%" PRIu64,
              inReqState->responseTimeNs / CHPP_NSEC_PER_MSEC);

  } else if (previousResponseTime != CHPP_TIME_NONE) {
    CHPP_LOGW("TX additional response t=%" PRIu64 " for req t=%" PRIu64,
              inReqState->responseTimeNs / CHPP_NSEC_PER_MSEC,
              inReqState->requestTimeNs / CHPP_NSEC_PER_MSEC);

  } else {
    CHPP_LOGD("Sending initial response at t=%" PRIu64
              " for request at t=%" PRIu64 " (RTT=%" PRIu64 ")",
              inReqState->responseTimeNs / CHPP_NSEC_PER_MSEC,
              inReqState->requestTimeNs / CHPP_NSEC_PER_MSEC,
              (inReqState->responseTimeNs - inReqState->requestTimeNs) /
                  CHPP_NSEC_PER_MSEC);
  }

  return chppEnqueueTxDatagramOrFail(appState->transportContext, buf, len);
}

bool chppSendTimestampedRequestOrFail(
    struct ChppEndpointState *endpointState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs) {
  CHPP_DEBUG_NOT_NULL(outReqState);
  CHPP_DEBUG_NOT_NULL(buf);
  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));

  if (timeoutNs < CHPP_TRANSPORT_TX_TIMEOUT_NS) {
    // The app layer sits above the transport layer.
    // Request timeout (app layer) should be longer than the transport timeout.
    CHPP_LOGW("Request timeout (%" PRIu64
              "ns) should be longer than the transport timeout (%" PRIu64 "ns)",
              timeoutNs, (uint64_t)CHPP_TRANSPORT_TX_TIMEOUT_NS);
  }

  chppTimestampOutgoingRequest(endpointState->appContext, outReqState, buf,
                               timeoutNs);
  endpointState->syncResponse.ready = false;

  bool success = chppEnqueueTxDatagramOrFail(
      endpointState->appContext->transportContext, buf, len);

  // Failure to enqueue a TX datagram means that a request was known to be not
  // transmitted. We explicitly set requestState to be in the NONE state, so
  // that unintended app layer timeouts do not occur.
  if (!success) {
    outReqState->requestState = CHPP_REQUEST_STATE_NONE;
  }

  return success;
}

bool chppWaitForResponseWithTimeout(
    struct ChppSyncResponse *syncResponse,
    struct ChppOutgoingRequestState *outReqState, uint64_t timeoutNs) {
  CHPP_DEBUG_NOT_NULL(syncResponse);
  CHPP_DEBUG_NOT_NULL(outReqState);

  bool result = true;

  chppMutexLock(&syncResponse->mutex);

  while (result && !syncResponse->ready) {
    result = chppConditionVariableTimedWait(&syncResponse->condVar,
                                            &syncResponse->mutex, timeoutNs);
  }
  if (!syncResponse->ready) {
    outReqState->requestState = CHPP_REQUEST_STATE_RESPONSE_TIMEOUT;
    CHPP_LOGE("Response timeout after %" PRIu64 " ms",
              timeoutNs / CHPP_NSEC_PER_MSEC);
    result = false;
  }

  chppMutexUnlock(&syncResponse->mutex);

  return result;
}

struct ChppEndpointState *getRegisteredEndpointState(
    struct ChppAppState *appState, uint8_t index, enum ChppEndpointType type) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_ASSERT(index < getRegisteredEndpointCount(appState, type));

  return type == CHPP_ENDPOINT_CLIENT
             ? appState->registeredClientStates[index]
             : appState->registeredServiceStates[index];
}

uint16_t getRegisteredEndpointOutReqCount(struct ChppAppState *appState,
                                          uint8_t index,
                                          enum ChppEndpointType type) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_ASSERT(index < getRegisteredEndpointCount(appState, type));

  return type == CHPP_ENDPOINT_CLIENT
             ? appState->registeredClients[index]->outReqCount
             : appState->registeredServices[index]->outReqCount;
}

uint8_t getRegisteredEndpointCount(struct ChppAppState *appState,
                                   enum ChppEndpointType type) {
  return type == CHPP_ENDPOINT_CLIENT ? appState->registeredClientCount
                                      : appState->registeredServiceCount;
}

void chppRecalculateNextTimeout(struct ChppAppState *appState,
                                enum ChppEndpointType type) {
  CHPP_DEBUG_NOT_NULL(appState);

  uint64_t timeoutNs = CHPP_TIME_MAX;

  const uint8_t endpointCount = getRegisteredEndpointCount(appState, type);

  for (uint8_t endpointIdx = 0; endpointIdx < endpointCount; endpointIdx++) {
    uint16_t reqCount =
        getRegisteredEndpointOutReqCount(appState, endpointIdx, type);
    struct ChppEndpointState *endpointState =
        getRegisteredEndpointState(appState, endpointIdx, type);
    struct ChppOutgoingRequestState *reqStates = endpointState->outReqStates;
    for (uint16_t cmdIdx = 0; cmdIdx < reqCount; cmdIdx++) {
      struct ChppOutgoingRequestState *reqState = &reqStates[cmdIdx];

      if (reqState->requestState == CHPP_REQUEST_STATE_REQUEST_SENT) {
        timeoutNs = MIN(timeoutNs, reqState->responseTimeNs);
      }
    }
  }

  CHPP_LOGD("nextReqTimeout=%" PRIu64, timeoutNs / CHPP_NSEC_PER_MSEC);

  if (type == CHPP_ENDPOINT_CLIENT) {
    appState->nextClientRequestTimeoutNs = timeoutNs;
  } else {
    appState->nextServiceRequestTimeoutNs = timeoutNs;
  }
}

uint64_t *getNextRequestTimeoutNs(struct ChppAppState *appState,
                                  enum ChppEndpointType type) {
  return type == CHPP_ENDPOINT_CLIENT ? &appState->nextClientRequestTimeoutNs
                                      : &appState->nextServiceRequestTimeoutNs;
}

void chppCloseOpenRequests(struct ChppEndpointState *endpointState,
                           enum ChppEndpointType type, bool clearOnly) {
  CHPP_DEBUG_NOT_NULL(endpointState);

  bool recalcNeeded = false;

  struct ChppAppState *appState = endpointState->appContext;
  const uint8_t enpointIdx = endpointState->index;
  const uint16_t cmdCount =
      getRegisteredEndpointOutReqCount(appState, enpointIdx, type);

  for (uint16_t cmdIdx = 0; cmdIdx < cmdCount; cmdIdx++) {
    if (endpointState->outReqStates[cmdIdx].requestState ==
        CHPP_REQUEST_STATE_REQUEST_SENT) {
      recalcNeeded = true;

      CHPP_LOGE("Closing open req #%" PRIu16 " clear %d", cmdIdx, clearOnly);

      if (clearOnly) {
        endpointState->outReqStates[cmdIdx].requestState =
            CHPP_REQUEST_STATE_RESPONSE_TIMEOUT;
      } else {
        struct ChppAppHeader *response =
            chppMalloc(sizeof(struct ChppAppHeader));
        if (response == NULL) {
          CHPP_LOG_OOM();
        } else {
          // Simulate receiving a timeout response.
          response->handle = endpointState->handle;
          response->type = type == CHPP_ENDPOINT_CLIENT
                               ? CHPP_MESSAGE_TYPE_SERVICE_RESPONSE
                               : CHPP_MESSAGE_TYPE_CLIENT_RESPONSE;
          response->transaction =
              endpointState->outReqStates[cmdIdx].transaction;
          response->error = CHPP_APP_ERROR_TIMEOUT;
          response->command = cmdIdx;

          chppAppProcessRxDatagram(appState, (uint8_t *)response,
                                   sizeof(struct ChppAppHeader));
        }
      }
    }
  }
  if (recalcNeeded) {
    chppRecalculateNextTimeout(appState, type);
  }
}