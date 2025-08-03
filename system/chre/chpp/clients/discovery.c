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

#include "chpp/clients/discovery.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/common/discovery.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/transport.h"

/************************************************
 *  Prototypes
 ***********************************************/

static inline bool chppIsClientCompatibleWithService(
    const struct ChppClientDescriptor *client,
    const struct ChppServiceDescriptor *service);
static uint8_t chppFindMatchingClientIndex(
    struct ChppAppState *appState, const struct ChppServiceDescriptor *service);
static void chppProcessDiscoverAllResponse(
    struct ChppAppState *appState, const struct ChppDiscoveryResponse *response,
    size_t responseLen);
static ChppNotifierFunction *chppGetClientMatchNotifierFunction(
    struct ChppAppState *appState, uint8_t index);

/************************************************
 *  Private Functions
 ***********************************************/

/**
 * Determines if a client is compatible with a service.
 *
 * Compatibility requirements are:
 * 1. UUIDs must match
 * 2. Major version numbers must match
 *
 * @param client ChppClientDescriptor of client.
 * @param service ChppServiceDescriptor of service.
 *
 * @param return True if compatible.
 */
static inline bool chppIsClientCompatibleWithService(
    const struct ChppClientDescriptor *client,
    const struct ChppServiceDescriptor *service) {
  return memcmp(client->uuid, service->uuid, CHPP_SERVICE_UUID_LEN) == 0 &&
         client->version.major == service->version.major;
}

/**
 * Matches a registered client to a (discovered) service.
 *
 * @param appState Application layer state.
 * @param service ChppServiceDescriptor of service.
 *
 * @param return Index of client matching the service, or CHPP_CLIENT_INDEX_NONE
 * if there is none.
 */
static uint8_t chppFindMatchingClientIndex(
    struct ChppAppState *appState,
    const struct ChppServiceDescriptor *service) {
  uint8_t result = CHPP_CLIENT_INDEX_NONE;

  const struct ChppClient **clients = appState->registeredClients;

  for (uint8_t i = 0; i < appState->registeredClientCount; i++) {
    if (chppIsClientCompatibleWithService(&clients[i]->descriptor, service)) {
      result = i;
      break;
    }
  }

  return result;
}

/**
 * Processes the Discover All Services response
 * (CHPP_DISCOVERY_COMMAND_DISCOVER_ALL).
 *
 * @param appState Application layer state.
 * @param response The response from the discovery service.
 * @param responseLen Length of the in bytes.
 */
static void chppProcessDiscoverAllResponse(
    struct ChppAppState *appState, const struct ChppDiscoveryResponse *response,
    size_t responseLen) {
  if (appState->isDiscoveryComplete) {
    CHPP_LOGE("Dupe discovery resp");
    return;
  }

  size_t servicesLen = responseLen - sizeof(struct ChppAppHeader);
  uint8_t serviceCount =
      (uint8_t)(servicesLen / sizeof(struct ChppServiceDescriptor));

  CHPP_DEBUG_ASSERT_LOG(
      servicesLen == serviceCount * sizeof(struct ChppServiceDescriptor),
      "Discovery desc len=%" PRIuSIZE " != count=%" PRIu8 " * size=%" PRIuSIZE,
      servicesLen, serviceCount, sizeof(struct ChppServiceDescriptor));

  CHPP_DEBUG_ASSERT_LOG(serviceCount <= CHPP_MAX_DISCOVERED_SERVICES,
                        "Service count=%" PRIu8 " > max=%d", serviceCount,
                        CHPP_MAX_DISCOVERED_SERVICES);

  CHPP_LOGI("Discovered %" PRIu8 " services", serviceCount);

  uint8_t matchedClients = 0;
  for (uint8_t i = 0; i < MIN(serviceCount, CHPP_MAX_DISCOVERED_SERVICES);
       i++) {
    const struct ChppServiceDescriptor *service = &response->services[i];

    // Update lookup table
    uint8_t clientIndex = chppFindMatchingClientIndex(appState, service);
    appState->clientIndexOfServiceIndex[i] = clientIndex;

    char uuidText[CHPP_SERVICE_UUID_STRING_LEN];
    chppUuidToStr(service->uuid, uuidText);

    if (clientIndex == CHPP_CLIENT_INDEX_NONE) {
      CHPP_LOGE(
          "No client for service #%d"
          " name=%s, UUID=%s, v=%" PRIu8 ".%" PRIu8 ".%" PRIu16,
          CHPP_SERVICE_HANDLE_OF_INDEX(i), service->name, uuidText,
          service->version.major, service->version.minor,
          service->version.patch);
      continue;
    }

    const struct ChppClient *client = appState->registeredClients[clientIndex];

    CHPP_LOGD("Client # %" PRIu8
              " matched to service on handle %d"
              " with name=%s, UUID=%s. "
              "client version=%" PRIu8 ".%" PRIu8 ".%" PRIu16
              ", service version=%" PRIu8 ".%" PRIu8 ".%" PRIu16,
              clientIndex, CHPP_SERVICE_HANDLE_OF_INDEX(i), service->name,
              uuidText, client->descriptor.version.major,
              client->descriptor.version.minor,
              client->descriptor.version.patch, service->version.major,
              service->version.minor, service->version.patch);

    // Initialize client
    if (!client->initFunctionPtr(
            appState->registeredClientStates[clientIndex]->context,
            CHPP_SERVICE_HANDLE_OF_INDEX(i), service->version)) {
      CHPP_LOGE("Client v=%" PRIu8 ".%" PRIu8 ".%" PRIu16
                " rejected init. Service v=%" PRIu8 ".%" PRIu8 ".%" PRIu16,
                client->descriptor.version.major,
                client->descriptor.version.minor,
                client->descriptor.version.patch, service->version.major,
                service->version.minor, service->version.patch);
      continue;
    }

    matchedClients++;
  }

  CHPP_LOGD("Matched %" PRIu8 " out of %" PRIu8 " clients and %" PRIu8
            " services",
            matchedClients, appState->registeredClientCount, serviceCount);

  // Notify any clients waiting on discovery completion
  chppMutexLock(&appState->discoveryMutex);
  appState->isDiscoveryComplete = true;
  appState->matchedClientCount = matchedClients;
  appState->discoveredServiceCount = serviceCount;
  chppConditionVariableSignal(&appState->discoveryCv);
  chppMutexUnlock(&appState->discoveryMutex);

  // Notify clients of match
  for (uint8_t i = 0; i < appState->discoveredServiceCount; i++) {
    uint8_t clientIndex = appState->clientIndexOfServiceIndex[i];
    if (clientIndex != CHPP_CLIENT_INDEX_NONE) {
      // Discovered service has a matched client
      ChppNotifierFunction *matchNotifierFunction =
          chppGetClientMatchNotifierFunction(appState, clientIndex);

      CHPP_LOGD("Client #%" PRIu8 " (H#%d) match notifier found=%d",
                clientIndex, CHPP_SERVICE_HANDLE_OF_INDEX(i),
                (matchNotifierFunction != NULL));

      if (matchNotifierFunction != NULL) {
        matchNotifierFunction(
            appState->registeredClientStates[clientIndex]->context);
      }
    }
  }
}

/**
 * Returns the match notification function pointer of a particular negotiated
 * client. The function pointer will be set to null by clients that do not need
 * or support a match notification.
 *
 * @param appState Application layer state.
 * @param index Index of the registered client.
 *
 * @return Pointer to the match notification function.
 */
static ChppNotifierFunction *chppGetClientMatchNotifierFunction(
    struct ChppAppState *appState, uint8_t index) {
  return appState->registeredClients[index]->matchNotifierFunctionPtr;
}

/************************************************
 *  Public Functions
 ***********************************************/

void chppDiscoveryInit(struct ChppAppState *appState) {
  CHPP_ASSERT_LOG(!appState->isDiscoveryClientInitialized,
                  "Discovery client already initialized");

  CHPP_LOGD("Initializing CHPP discovery client");

  if (!appState->isDiscoveryClientInitialized) {
    chppMutexInit(&appState->discoveryMutex);
    chppConditionVariableInit(&appState->discoveryCv);
    appState->isDiscoveryClientInitialized = true;
  }

  appState->matchedClientCount = 0;
  appState->isDiscoveryComplete = false;
  appState->isDiscoveryClientInitialized = true;
}

void chppDiscoveryDeinit(struct ChppAppState *appState) {
  CHPP_ASSERT_LOG(appState->isDiscoveryClientInitialized,
                  "Discovery client already deinitialized");

  CHPP_LOGD("Deinitializing CHPP discovery client");
  appState->isDiscoveryClientInitialized = false;
}

bool chppWaitForDiscoveryComplete(struct ChppAppState *appState,
                                  uint64_t timeoutMs) {
  bool success = false;

  if (!appState->isDiscoveryClientInitialized) {
    timeoutMs = 0;
  } else {
    success = true;

    chppMutexLock(&appState->discoveryMutex);
    if (timeoutMs == 0) {
      success = appState->isDiscoveryComplete;
    } else {
      while (success && !appState->isDiscoveryComplete) {
        success = chppConditionVariableTimedWait(
            &appState->discoveryCv, &appState->discoveryMutex,
            timeoutMs * CHPP_NSEC_PER_MSEC);
      }
    }
    chppMutexUnlock(&appState->discoveryMutex);
  }

  if (!success) {
    CHPP_LOGE("Discovery incomplete after %" PRIu64 " ms", timeoutMs);
  }
  return success;
}

bool chppDispatchDiscoveryServiceResponse(struct ChppAppState *appState,
                                          const uint8_t *buf, size_t len) {
  const struct ChppAppHeader *rxHeader = (const struct ChppAppHeader *)buf;
  bool success = true;

  switch (rxHeader->command) {
    case CHPP_DISCOVERY_COMMAND_DISCOVER_ALL: {
      chppProcessDiscoverAllResponse(
          appState, (const struct ChppDiscoveryResponse *)buf, len);
      break;
    }
    default: {
      success = false;
      break;
    }
  }
  return success;
}

void chppInitiateDiscovery(struct ChppAppState *appState) {
  if (appState->isDiscoveryComplete) {
    CHPP_LOGE("Duplicate discovery init");
    return;
  }

  for (uint8_t i = 0; i < CHPP_MAX_DISCOVERED_SERVICES; i++) {
    appState->clientIndexOfServiceIndex[i] = CHPP_CLIENT_INDEX_NONE;
  }

  struct ChppAppHeader *request = chppMalloc(sizeof(struct ChppAppHeader));
  request->handle = CHPP_HANDLE_DISCOVERY;
  request->type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
  request->transaction = 0;
  request->error = CHPP_APP_ERROR_NONE;
  request->command = CHPP_DISCOVERY_COMMAND_DISCOVER_ALL;

  chppEnqueueTxDatagramOrFail(appState->transportContext, request,
                              sizeof(*request));
}

bool chppAreAllClientsMatched(struct ChppAppState *appState) {
  bool success = false;
  chppMutexLock(&appState->discoveryMutex);
  success = (appState->isDiscoveryComplete) &&
            (appState->registeredClientCount == appState->matchedClientCount);
  chppMutexUnlock(&appState->discoveryMutex);
  return success;
}
