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

#ifndef CHPP_SERVICES_H_
#define CHPP_SERVICES_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

#if defined(CHPP_SERVICE_ENABLED_WWAN) || \
    defined(CHPP_SERVICE_ENABLED_WIFI) || defined(CHPP_SERVICE_ENABLED_GNSS)
#define CHPP_SERVICE_ENABLED
#endif

/**
 * Allocates a service request message of a specific type and its corresponding
 * length.
 *
 * @param serviceState State of the service.
 * @param type Type of response.
 *
 * @return Pointer to allocated memory.
 */
#define chppAllocServiceRequestFixed(serviceState, type) \
  (type *)chppAllocServiceRequest(serviceState, sizeof(type))

/**
 * Allocate a variable-length service request message of a specific type.
 *
 * @param serviceState State of the service.
 * @param type Type of response which includes an arrayed member.
 * @param count number of items in the array of arrayField.
 * @param arrayField The arrayed member field.
 *
 * @return Pointer to allocated memory.
 */
#define chppAllocServiceRequestTypedArray(serviceState, type, count, \
                                          arrayField)                \
  (type *)chppAllocServiceRequest(                                   \
      serviceState, sizeof(type) + (count)*sizeof_member(type, arrayField[0]))

/**
 * Allocates a variable-length notification of a specific type.
 *
 * @param type Type of notification which includes an arrayed member.
 * @param count number of items in the array of arrayField.
 * @param arrayField The arrayed member field.
 *
 * @return Pointer to allocated memory.
 */
#define chppAllocServiceNotificationTypedArray(type, count, arrayField) \
  (type *)chppAllocServiceNotification(                                 \
      sizeof(type) + (count)*sizeof_member(type, arrayField[0]))

/**
 * Uses chppAllocServiceNotification() to allocate a response notification of a
 * specific type and its corresponding length.
 *
 * @param type Type of notification.
 *
 * @return Pointer to allocated memory
 */
#define chppAllocServiceNotificationFixed(type) \
  (type *)chppAllocServiceNotification(sizeof(type))

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Registers common services with the CHPP app layer. These services are enabled
 * by CHPP_SERVICE_ENABLED_xxxx definitions. This function is automatically
 * called by chppAppInit().
 *
 * @param context State of the app layer.
 */
void chppRegisterCommonServices(struct ChppAppState *context);

/**
 * Deregisters common services with the CHPP app layer. These services are
 * enabled by CHPP_SERVICE_ENABLED_xxxx definitions. This function is
 * automatically called by chppAppInit().
 *
 * @param context State of the app layer.
 */
void chppDeregisterCommonServices(struct ChppAppState *context);

/**
 * Registers a new service on CHPP. This function is to be called by the
 * platform initialization code for every non-common service available on a
 * server (if any), i.e. except those that are registered through
 * chppRegisterCommonServices().
 *
 * outReqStates must point to an array of ChppOutgoingRequestState with
 * ChppEndpointState.outReqCount elements. It must be NULL when the service
 * does not send requests (ChppEndpointState.outReqCount = 0).
 *
 * inReqStates must point to an array of ChppIncomingRequestState with
 * as many elements as the corresponding client can send. It must be NULL when
 * the client does not send requests (ChppEndpointState.outReqCount = 0).
 *
 * Note that the maximum number of services that can be registered on a platform
 * can specified as CHPP_MAX_REGISTERED_SERVICES by the initialization code.
 * Otherwise, a default value will be used.
 *
 * @param appContext State of the app layer.
 * @param serviceContext State of the service instance.
 * @param serviceState State of the client.
 * @param outReqStates List of outgoing request states.
 * @param newService The service to be registered on this platform.
 */
void chppRegisterService(struct ChppAppState *appContext, void *serviceContext,
                         struct ChppEndpointState *serviceState,
                         struct ChppOutgoingRequestState *outReqStates,
                         const struct ChppService *newService);

/**
 * Allocates a service notification of a specified length.
 *
 * It is expected that for most use cases, the
 * chppAllocServiceNotificationFixed() or
 * chppAllocServiceNotificationTypedArray() macros shall be used rather than
 * calling this function directly.
 *
 * The caller must initialize at least the handle and command fields of the
 * ChppAppHeader.
 *
 * @param len Length of the notification (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory
 */
struct ChppAppHeader *chppAllocServiceNotification(size_t len);

/**
 * Allocates a service request message of a specified length.
 *
 * It populates the request header, including the transaction number which is
 * then incremented.
 *
 * For most use cases, the chppAllocServiceRequestFixed() or
 * chppAllocServiceRequestTypedArray() macros shall be preferred.
 *
 * @param serviceState State of the service.
 * @param len Length of the response message (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory
 */
struct ChppAppHeader *chppAllocServiceRequest(
    struct ChppEndpointState *serviceState, size_t len);

/**
 * Allocates a specific service request command without any additional payload.
 *
 * @param serviceState State of the service.
 * @param command Type of response.
 *
 * @return Pointer to allocated memory
 */
struct ChppAppHeader *chppAllocServiceRequestCommand(
    struct ChppEndpointState *serviceState, uint16_t command);

/**
 * Timestamps and enqueues a request.
 *
 * Note that the ownership of buf is taken from the caller when this method is
 * invoked.
 *
 * @param serviceState State of the service sending the request.
 * @param outReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 * @param timeoutNs Time in nanoseconds before a timeout response is generated.
 *        Zero means no timeout response.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the queue was full and the payload
 *         discarded.
 */
bool chppServiceSendTimestampedRequestOrFail(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs);

/**
 * Similar to chppServiceSendTimestampedRequestOrFail() but blocks execution
 * until a response is received. Used for synchronous requests.
 *
 * @param serviceState State of the service sending the request.
 * @param outReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the payload was discarded because
 *         either the queue was full, or the request timed out.
 */
bool chppServiceSendTimestampedRequestAndWait(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len);

/**
 * Same as chppClientSendTimestampedRequestAndWait() but with a specified
 * timeout.
 *
 * @param serviceState State of the service sending the request.
 * @param outReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the payload was discarded because
 *         either the queue was full, or the request timed out.
 */
bool chppServiceSendTimestampedRequestAndWaitTimeout(
    struct ChppEndpointState *serviceState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs);

/**
 * Closes any remaining open requests by simulating a timeout.
 *
 * This function is used when a service is reset.
 *
 * @param serviceState State of the service.
 * @param service The service for which to clear out open requests.
 * @param clearOnly If true, indicates that a timeout response shouldn't be
 *        sent. This must only be set if the requests are being cleared as
 *        part of the closing.
 */
void chppServiceCloseOpenRequests(struct ChppEndpointState *serviceState,
                                  const struct ChppService *service,
                                  bool clearOnly);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_SERVICES_H_
