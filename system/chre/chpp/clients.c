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

#include "chpp/clients.h"

#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
#include "chpp/clients/discovery.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_GNSS
#include "chpp/clients/gnss.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_LOOPBACK
#include "chpp/clients/loopback.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
#include "chpp/clients/timesync.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_WIFI
#include "chpp/clients/wifi.h"
#endif
#ifdef CHPP_CLIENT_ENABLED_WWAN
#include "chpp/clients/wwan.h"
#endif
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/time.h"
#include "chpp/transport.h"

/************************************************
 *  Prototypes
 ***********************************************/

static bool chppIsClientApiReady(struct ChppEndpointState *clientState);
static ChppClientDeinitFunction *chppGetClientDeinitFunction(
    struct ChppAppState *context, uint8_t index);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Determines whether a client is ready to accept commands via its API (i.e. is
 * initialized and opened). If the client is in the process of reopening, it
 * will wait for the client to reopen.
 *
 * @param clientState State of the client sending the client request.
 *
 * @return Indicates whether the client is ready.
 */
static bool chppIsClientApiReady(struct ChppEndpointState *clientState) {
  CHPP_DEBUG_NOT_NULL(clientState);

  bool result = false;

  if (clientState->initialized) {
    switch (clientState->openState) {
      case (CHPP_OPEN_STATE_CLOSED):
      case (CHPP_OPEN_STATE_WAITING_TO_OPEN): {
        // result remains false
        break;
      }

      case (CHPP_OPEN_STATE_OPENED): {
        result = true;
        break;
      }

      case (CHPP_OPEN_STATE_OPENING): {
        // Allow the open request to go through
        clientState->openState = CHPP_OPEN_STATE_WAITING_TO_OPEN;
        result = true;
        break;
      }
    }
  }

  if (!result) {
    CHPP_LOGE("Client not ready (everInit=%d, init=%d, open=%" PRIu8 ")",
              clientState->everInitialized, clientState->initialized,
              clientState->openState);
  }
  return result;
}

/**
 * Returns the deinitialization function pointer of a particular negotiated
 * client.
 *
 * @param context Maintains status for each app layer instance.
 * @param index Index of the registered client.
 *
 * @return Pointer to the match notification function.
 */
static ChppClientDeinitFunction *chppGetClientDeinitFunction(
    struct ChppAppState *context, uint8_t index) {
  CHPP_DEBUG_NOT_NULL(context);

  return context->registeredClients[index]->deinitFunctionPtr;
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterCommonClients(struct ChppAppState *context) {
  UNUSED_VAR(context);
  CHPP_DEBUG_NOT_NULL(context);

  CHPP_LOGD("Registering Clients");

#ifdef CHPP_CLIENT_ENABLED_WWAN
  if (context->clientServiceSet.wwanClient) {
    chppRegisterWwanClient(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_WIFI
  if (context->clientServiceSet.wifiClient) {
    chppRegisterWifiClient(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_GNSS
  if (context->clientServiceSet.gnssClient) {
    chppRegisterGnssClient(context);
  }
#endif
}

void chppDeregisterCommonClients(struct ChppAppState *context) {
  UNUSED_VAR(context);
  CHPP_DEBUG_NOT_NULL(context);

  CHPP_LOGD("Deregistering Clients");

#ifdef CHPP_CLIENT_ENABLED_WWAN
  if (context->clientServiceSet.wwanClient) {
    chppDeregisterWwanClient(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_WIFI
  if (context->clientServiceSet.wifiClient) {
    chppDeregisterWifiClient(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_GNSS
  if (context->clientServiceSet.gnssClient) {
    chppDeregisterGnssClient(context);
  }
#endif
}

void chppRegisterClient(struct ChppAppState *appContext, void *clientContext,
                        struct ChppEndpointState *clientState,
                        struct ChppOutgoingRequestState *outReqStates,
                        const struct ChppClient *newClient) {
  CHPP_NOT_NULL(newClient);
  CHPP_DEBUG_NOT_NULL(appContext);
  CHPP_DEBUG_NOT_NULL(clientContext);
  CHPP_DEBUG_NOT_NULL(clientState);
  CHPP_DEBUG_NOT_NULL(newClient);

  if (appContext->registeredClientCount >= CHPP_MAX_REGISTERED_CLIENTS) {
    CHPP_LOGE("Max clients registered: %" PRIu8,
              appContext->registeredClientCount);
    return;
  }
  clientState->appContext = appContext;
  clientState->outReqStates = outReqStates;
  clientState->index = appContext->registeredClientCount;
  clientState->context = clientContext;
  appContext->registeredClientStates[appContext->registeredClientCount] =
      clientState;

  appContext->registeredClients[appContext->registeredClientCount] = newClient;

  char uuidText[CHPP_SERVICE_UUID_STRING_LEN];
  chppUuidToStr(newClient->descriptor.uuid, uuidText);
  CHPP_LOGD("Client # %" PRIu8 " UUID=%s, version=%" PRIu8 ".%" PRIu8
            ".%" PRIu16 ", min_len=%" PRIuSIZE,
            appContext->registeredClientCount, uuidText,
            newClient->descriptor.version.major,
            newClient->descriptor.version.minor,
            newClient->descriptor.version.patch, newClient->minLength);

  appContext->registeredClientCount++;
}

void chppInitBasicClients(struct ChppAppState *context) {
  UNUSED_VAR(context);
  CHPP_DEBUG_NOT_NULL(context);

  CHPP_LOGD("Initializing basic clients");

#ifdef CHPP_CLIENT_ENABLED_LOOPBACK
  if (context->clientServiceSet.loopbackClient) {
    chppLoopbackClientInit(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
  chppTimesyncClientInit(context);
#endif

#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
  chppDiscoveryInit(context);
#endif
}

void chppClientInit(struct ChppEndpointState *clientState, uint8_t handle) {
  CHPP_DEBUG_NOT_NULL(clientState);
  CHPP_ASSERT_LOG(!clientState->initialized,
                  "Client H#%" PRIu8 " already initialized", handle);

  if (!clientState->everInitialized) {
    clientState->handle = handle;
    chppMutexInit(&clientState->syncResponse.mutex);
    chppConditionVariableInit(&clientState->syncResponse.condVar);
    clientState->everInitialized = true;
  }

  clientState->initialized = true;
}

void chppClientDeinit(struct ChppEndpointState *clientState) {
  CHPP_DEBUG_NOT_NULL(clientState);
  CHPP_ASSERT_LOG(clientState->initialized,
                  "Client H#%" PRIu8 " already deinitialized",
                  clientState->handle);

  clientState->initialized = false;
}

void chppDeinitBasicClients(struct ChppAppState *context) {
  UNUSED_VAR(context);
  CHPP_DEBUG_NOT_NULL(context);

  CHPP_LOGD("Deinitializing basic clients");

#ifdef CHPP_CLIENT_ENABLED_LOOPBACK
  if (context->clientServiceSet.loopbackClient) {
    chppLoopbackClientDeinit(context);
  }
#endif

#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
  chppTimesyncClientDeinit(context);
#endif

#ifdef CHPP_CLIENT_ENABLED_DISCOVERY
  chppDiscoveryDeinit(context);
#endif
}

void chppDeinitMatchedClients(struct ChppAppState *context) {
  CHPP_DEBUG_NOT_NULL(context);
  CHPP_LOGD("Deinitializing matched clients");

  for (uint8_t i = 0; i < context->discoveredServiceCount; i++) {
    uint8_t clientIndex = context->clientIndexOfServiceIndex[i];
    if (clientIndex != CHPP_CLIENT_INDEX_NONE) {
      // Discovered service has a matched client
      ChppClientDeinitFunction *clientDeinitFunction =
          chppGetClientDeinitFunction(context, clientIndex);

      CHPP_LOGD("Client #%" PRIu8 " (H#%d) deinit fp found=%d", clientIndex,
                CHPP_SERVICE_HANDLE_OF_INDEX(i),
                (clientDeinitFunction != NULL));

      if (clientDeinitFunction != NULL) {
        clientDeinitFunction(
            context->registeredClientStates[clientIndex]->context);
      }
    }
  }
}

struct ChppAppHeader *chppAllocClientRequest(
    struct ChppEndpointState *clientState, size_t len) {
  CHPP_DEBUG_NOT_NULL(clientState);
  return chppAllocRequest(CHPP_MESSAGE_TYPE_CLIENT_REQUEST, clientState, len);
}

struct ChppAppHeader *chppAllocClientRequestCommand(
    struct ChppEndpointState *clientState, uint16_t command) {
  struct ChppAppHeader *request =
      chppAllocClientRequest(clientState, sizeof(struct ChppAppHeader));

  if (request != NULL) {
    request->command = command;
  }
  return request;
}

bool chppClientSendTimestampedRequestOrFail(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs) {
  CHPP_DEBUG_NOT_NULL(clientState);
  CHPP_DEBUG_NOT_NULL(outReqState);
  CHPP_DEBUG_NOT_NULL(buf);

  if (!chppIsClientApiReady(clientState)) {
    CHPP_FREE_AND_NULLIFY(buf);
    return false;
  }

  return chppSendTimestampedRequestOrFail(clientState, outReqState, buf, len,
                                          timeoutNs);
}

bool chppClientSendTimestampedRequestAndWait(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len) {
  return chppClientSendTimestampedRequestAndWaitTimeout(
      clientState, outReqState, buf, len, CHPP_REQUEST_TIMEOUT_DEFAULT);
}

bool chppClientSendTimestampedRequestAndWaitTimeout(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs) {
  bool result = chppClientSendTimestampedRequestOrFail(
      clientState, outReqState, buf, len, CHPP_REQUEST_TIMEOUT_INFINITE);

  if (!result) {
    return false;
  }

  return chppWaitForResponseWithTimeout(&clientState->syncResponse, outReqState,
                                        timeoutNs);
}

void chppClientPseudoOpen(struct ChppEndpointState *clientState) {
  clientState->pseudoOpen = true;
}

bool chppClientSendOpenRequest(struct ChppEndpointState *clientState,
                               struct ChppOutgoingRequestState *openReqState,
                               uint16_t openCommand, bool blocking) {
  CHPP_NOT_NULL(clientState);
  CHPP_NOT_NULL(openReqState);

  bool result = false;
  uint8_t priorState = clientState->openState;

#ifdef CHPP_CLIENT_ENABLED_TIMESYNC
  chppTimesyncMeasureOffset(clientState->appContext);
#endif

  struct ChppAppHeader *request =
      chppAllocClientRequestCommand(clientState, openCommand);

  if (request == NULL) {
    return false;
  }

  clientState->openState = CHPP_OPEN_STATE_OPENING;

  if (blocking) {
    CHPP_LOGD("Opening service - blocking");
    result = chppClientSendTimestampedRequestAndWait(clientState, openReqState,
                                                     request, sizeof(*request));
  } else {
    CHPP_LOGD("Opening service - non-blocking");
    result = chppClientSendTimestampedRequestOrFail(
        clientState, openReqState, request, sizeof(*request),
        CHPP_REQUEST_TIMEOUT_INFINITE);
  }

  if (!result) {
    CHPP_LOGE("Service open fail from state=%" PRIu8 " psudo=%d blocking=%d",
              priorState, clientState->pseudoOpen, blocking);
    clientState->openState = CHPP_OPEN_STATE_CLOSED;

  } else if (blocking) {
    result = (clientState->openState == CHPP_OPEN_STATE_OPENED);
  }

  return result;
}

void chppClientProcessOpenResponse(struct ChppEndpointState *clientState,
                                   uint8_t *buf, size_t len) {
  CHPP_DEBUG_NOT_NULL(clientState);
  CHPP_DEBUG_NOT_NULL(buf);

  UNUSED_VAR(len);  // Necessary depending on assert macro below
  // Assert condition already guaranteed by chppAppProcessRxDatagram() but
  // checking again since this is a public function
  CHPP_ASSERT(len >= sizeof(struct ChppAppHeader));

  struct ChppAppHeader *rxHeader = (struct ChppAppHeader *)buf;
  if (rxHeader->error != CHPP_APP_ERROR_NONE) {
    CHPP_LOGE("Service open failed at service");
    clientState->openState = CHPP_OPEN_STATE_CLOSED;
  } else {
    CHPP_LOGD("Service open succeeded at service");
    clientState->openState = CHPP_OPEN_STATE_OPENED;
  }
}

void chppClientCloseOpenRequests(struct ChppEndpointState *clientState,
                                 const struct ChppClient *client,
                                 bool clearOnly) {
  UNUSED_VAR(client);
  chppCloseOpenRequests(clientState, CHPP_ENDPOINT_CLIENT, clearOnly);
}

struct ChppAppHeader *chppAllocClientNotification(size_t len) {
  return chppAllocNotification(CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION, len);
}