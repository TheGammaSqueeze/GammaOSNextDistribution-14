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

#include "chpp/services.h"

#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/mutex.h"
#ifdef CHPP_SERVICE_ENABLED_GNSS
#include "chpp/services/gnss.h"
#endif
#ifdef CHPP_SERVICE_ENABLED_WIFI
#include "chpp/services/wifi.h"
#endif
#ifdef CHPP_SERVICE_ENABLED_WWAN
#include "chpp/services/wwan.h"
#endif
#include "chpp/transport.h"

/************************************************
 *  Public Functions
 ***********************************************/

void chppRegisterCommonServices(struct ChppAppState *context) {
  CHPP_DEBUG_NOT_NULL(context);
  UNUSED_VAR(context);

#ifdef CHPP_SERVICE_ENABLED_WWAN
  if (context->clientServiceSet.wwanService) {
    chppRegisterWwanService(context);
  }
#endif

#ifdef CHPP_SERVICE_ENABLED_WIFI
  if (context->clientServiceSet.wifiService) {
    chppRegisterWifiService(context);
  }
#endif

#ifdef CHPP_SERVICE_ENABLED_GNSS
  if (context->clientServiceSet.gnssService) {
    chppRegisterGnssService(context);
  }
#endif
}

void chppDeregisterCommonServices(struct ChppAppState *context) {
  CHPP_DEBUG_NOT_NULL(context);
  UNUSED_VAR(context);

#ifdef CHPP_SERVICE_ENABLED_WWAN
  if (context->clientServiceSet.wwanService) {
    chppDeregisterWwanService(context);
  }
#endif

#ifdef CHPP_SERVICE_ENABLED_WIFI
  if (context->clientServiceSet.wifiService) {
    chppDeregisterWifiService(context);
  }
#endif

#ifdef CHPP_SERVICE_ENABLED_GNSS
  if (context->clientServiceSet.gnssService) {
    chppDeregisterGnssService(context);
  }
#endif
}

void chppRegisterService(struct ChppAppState *appContext, void *serviceContext,
                         struct ChppEndpointState *serviceState,
                         struct ChppOutgoingRequestState *outReqStates,
                         const struct ChppService *newService) {
  CHPP_DEBUG_NOT_NULL(appContext);
  CHPP_DEBUG_NOT_NULL(serviceContext);
  CHPP_DEBUG_NOT_NULL(serviceState);
  CHPP_DEBUG_NOT_NULL(newService);

  const uint8_t numServices = appContext->registeredServiceCount;

  serviceState->openState = CHPP_OPEN_STATE_CLOSED;
  serviceState->appContext = appContext;
  serviceState->outReqStates = outReqStates;
  serviceState->context = serviceContext;

  if (numServices >= CHPP_MAX_REGISTERED_SERVICES) {
    CHPP_LOGE("Max services registered: # %" PRIu8, numServices);
    serviceState->handle = CHPP_HANDLE_NONE;
    return;
  }

  serviceState->index = numServices;
  serviceState->handle = CHPP_SERVICE_HANDLE_OF_INDEX(numServices);

  appContext->registeredServices[numServices] = newService;
  appContext->registeredServiceStates[numServices] = serviceState;
  appContext->registeredServiceCount++;

  chppMutexInit(&serviceState->syncResponse.mutex);
  chppConditionVariableInit(&serviceState->syncResponse.condVar);

  char uuidText[CHPP_SERVICE_UUID_STRING_LEN];
  chppUuidToStr(newService->descriptor.uuid, uuidText);
  CHPP_LOGD("Registered service # %" PRIu8
            " on handle %d"
            " with name=%s, UUID=%s, version=%" PRIu8 ".%" PRIu8 ".%" PRIu16
            ", min_len=%" PRIuSIZE " ",
            numServices, serviceState->handle, newService->descriptor.name,
            uuidText, newService->descriptor.version.major,
            newService->descriptor.version.minor,
            newService->descriptor.version.patch, newService->minLength);
}

struct ChppAppHeader *chppAllocServiceNotification(size_t len) {
  return chppAllocNotification(CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION, len);
}

struct ChppAppHeader *chppAllocServiceRequest(
    struct ChppEndpointState *serviceState, size_t len) {
  CHPP_DEBUG_NOT_NULL(serviceState);
  return chppAllocRequest(CHPP_MESSAGE_TYPE_SERVICE_REQUEST, serviceState, len);
}

struct ChppAppHeader *chppAllocServiceRequestCommand(
    struct ChppEndpointState *serviceState, uint16_t command) {
  struct ChppAppHeader *request =
      chppAllocServiceRequest(serviceState, sizeof(struct ChppAppHeader));

  if (request != NULL) {
    request->command = command;
  }
  return request;
}

bool chppServiceSendTimestampedRequestOrFail(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs) {
  return chppSendTimestampedRequestOrFail(serviceState, outReqState, buf, len,
                                          timeoutNs);
}

bool chppServiceSendTimestampedRequestAndWait(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len) {
  return chppServiceSendTimestampedRequestAndWaitTimeout(
      serviceState, outReqState, buf, len, CHPP_REQUEST_TIMEOUT_DEFAULT);
}

bool chppServiceSendTimestampedRequestAndWaitTimeout(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs) {
  CHPP_DEBUG_NOT_NULL(serviceState);

  bool result = chppServiceSendTimestampedRequestOrFail(
      serviceState, outReqState, buf, len, CHPP_REQUEST_TIMEOUT_INFINITE);

  if (!result) {
    return false;
  }

  return chppWaitForResponseWithTimeout(&serviceState->syncResponse,
                                        outReqState, timeoutNs);
}

void chppServiceCloseOpenRequests(struct ChppEndpointState *serviceState,
                                  const struct ChppService *service,
                                  bool clearOnly) {
  UNUSED_VAR(service);
  chppCloseOpenRequests(serviceState, CHPP_ENDPOINT_SERVICE, clearOnly);
}