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

#ifndef CHPP_CLIENTS_H_
#define CHPP_CLIENTS_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/condition_variable.h"
#include "chpp/macros.h"
#include "chpp/mutex.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

/**
 * Allocates a client request message of a specific type and its corresponding
 * length.
 *
 * @param clientState State of the client.
 * @param type Type of response.
 *
 * @return Pointer to allocated memory
 */
#define chppAllocClientRequestFixed(clientState, type) \
  (type *)chppAllocClientRequest(clientState, sizeof(type))

/**
 * Allocates a variable-length client request message of a specific type.
 *
 * @param clientState State of the client.
 * @param type Type of response which includes an arrayed member.
 * @param count number of items in the array of arrayField.
 * @param arrayField The arrayed member field.
 *
 * @return Pointer to allocated memory
 */
#define chppAllocClientRequestTypedArray(clientState, type, count, arrayField) \
  (type *)chppAllocClientRequest(                                              \
      clientState, sizeof(type) + (count)*sizeof_member(type, arrayField[0]))

/**
 * Allocates a variable-length notification of a specific type.
 *
 * @param type Type of notification which includes an arrayed member.
 * @param count number of items in the array of arrayField.
 * @param arrayField The arrayed member field.
 *
 * @return Pointer to allocated memory
 */
#define chppAllocClientNotificationTypedArray(type, count, arrayField) \
  (type *)chppAllocClientNotification(                                 \
      sizeof(type) + (count)*sizeof_member(type, arrayField[0]))

/**
 * Allocates a notification of a specific type and its corresponding length.
 *
 * @param type Type of notification.
 *
 * @return Pointer to allocated memory
 */
#define chppAllocClientNotificationFixed(type) \
  (type *)chppAllocClientNotification(sizeof(type))

#ifdef CHPP_CLIENT_ENABLED_CHRE_WWAN
#define CHPP_CLIENT_ENABLED_WWAN
#endif

#ifdef CHPP_CLIENT_ENABLED_CHRE_WIFI
#define CHPP_CLIENT_ENABLED_WIFI
#endif

#ifdef CHPP_CLIENT_ENABLED_CHRE_GNSS
#define CHPP_CLIENT_ENABLED_GNSS
#endif

#if defined(CHPP_CLIENT_ENABLED_LOOPBACK) ||                                  \
    defined(CHPP_CLIENT_ENABLED_TIMESYNC) ||                                  \
    defined(CHPP_CLIENT_ENABLED_DISCOVERY) ||                                 \
    defined(CHPP_CLIENT_ENABLED_WWAN) || defined(CHPP_CLIENT_ENABLED_WIFI) || \
    defined(CHPP_CLIENT_ENABLED_GNSS)
#define CHPP_CLIENT_ENABLED
#endif

// Default timeout for discovery completion.
#ifndef CHPP_DISCOVERY_DEFAULT_TIMEOUT_MS
#define CHPP_DISCOVERY_DEFAULT_TIMEOUT_MS UINT64_C(10000)  // 10s
#endif

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Registers common clients with the CHPP app layer. These clients are enabled
 * by CHPP_CLIENT_ENABLED_xxxx definitions. This function is automatically
 * called by chppAppInit().
 *
 * @param context State of the app layer.
 */
void chppRegisterCommonClients(struct ChppAppState *context);

/**
 * Deregisters common clients with the CHPP app layer. These clients are enabled
 * by CHPP_CLIENT_ENABLED_xxxx definitions. This function is automatically
 * called by chppAppDeinit().
 *
 * @param context State of the app layer.
 */
void chppDeregisterCommonClients(struct ChppAppState *context);

/**
 * Registers a new client on CHPP.
 *
 * This function is to be called by the platform initialization code for every
 * non-common client available on a server (if any), i.e. except those that are
 * registered through chppRegisterCommonClients().
 *
 * Registered clients are matched with discovered services during discovery.
 * When a match succeeds, the client's initialization function (pointer) is
 * called, assigning them their handle number.
 *
 * outReqStates must point to an array of ChppOutgoingRequestState with
 * ChppEndpointState.outReqCount elements. It must be NULL when the client
 * does not send requests (ChppEndpointState.outReqCount = 0).
 *
 * inReqStates must point to an array of ChppIncomingRequestState with
 * as many elements as the corresponding service can send. It must be NULL when
 * the service does not send requests (ChppEndpointState.outReqCount = 0).
 *
 * Note that the maximum number of clients that can be registered on a platform
 * can specified as CHPP_MAX_REGISTERED_CLIENTS by the initialization code.
 * Otherwise, a default value will be used.
 *
 * @param appContext State of the app layer.
 * @param clientContext State of the client instance.
 * @param clientState State of the client.
 * @param outReqStates List of outgoing request states.
 * @param newClient The client to be registered on this platform.
 */
void chppRegisterClient(struct ChppAppState *appContext, void *clientContext,
                        struct ChppEndpointState *clientState,
                        struct ChppOutgoingRequestState *outReqStates,
                        const struct ChppClient *newClient);

/**
 * Initializes basic CHPP clients.
 *
 * @param context State of the app layer.
 */
void chppInitBasicClients(struct ChppAppState *context);

/**
 * Initializes a client. This function must be called when a client is matched
 * with a service during discovery to provides its handle number.
 *
 * @param clientState State of the client.
 * @param handle Handle number for this client.
 */
void chppClientInit(struct ChppEndpointState *clientState, uint8_t handle);

/**
 * Deinitializes a client.
 *
 * @param clientState State of the client.
 */
void chppClientDeinit(struct ChppEndpointState *clientState);

/**
 * Deinitializes basic clients.
 *
 * @param context State of the app layer.
 */
void chppDeinitBasicClients(struct ChppAppState *context);

/**
 * Deinitializes all matched clients.
 *
 * @param context State of the app layer.
 */
void chppDeinitMatchedClients(struct ChppAppState *context);

/**
 * Allocates a client request message of a specified length.
 *
 * It populates the request header, including the transaction number which is
 * then incremented.
 *
 * For most use cases, the chppAllocClientRequestFixed() or
 * chppAllocClientRequestTypedArray() macros shall be preferred.
 *
 * @param clientState State of the client.
 * @param len Length of the response message (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory
 */
struct ChppAppHeader *chppAllocClientRequest(
    struct ChppEndpointState *clientState, size_t len);

/**
 * Allocates a specific client request command without any additional payload.
 *
 * @param clientState State of the client.
 * @param command Type of response.
 *
 * @return Pointer to allocated memory
 */
struct ChppAppHeader *chppAllocClientRequestCommand(
    struct ChppEndpointState *clientState, uint16_t command);

/**
 * Timestamps and enqueues a request.
 *
 * Note that the ownership of buf is taken from the caller when this method is
 * invoked.
 *
 * @param clientState State of the client sending the request.
 * @param outReqState State of the request/response
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 * @param timeoutNs Time in nanoseconds before a timeout response is generated.
 *        Zero means no timeout response.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the queue was full and the payload
 *         discarded.
 */
bool chppClientSendTimestampedRequestOrFail(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs);

/**
 * Similar to chppClientSendTimestampedRequestOrFail() but blocks execution
 * until a response is received. Used for synchronous requests.
 *
 * In order to use this function, clientState->responseNotifier must have been
 * initialized using chppNotifierInit() upon initialization of the client.
 *
 * @param clientState State of the client sending the request.
 * @param outReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the payload was discarded because
 *         either the queue was full, or the request timed out.
 */
bool chppClientSendTimestampedRequestAndWait(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len);

/**
 * Same as chppClientSendTimestampedRequestAndWait() but with a specified
 * timeout.
 *
 * @param clientState State of the client sending the request.
 * @param outReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the payload was discarded because
 *         either the queue was full, or the request timed out.
 */
bool chppClientSendTimestampedRequestAndWaitTimeout(
    struct ChppEndpointState *clientState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs);

/**
 * Marks a closed client as pseudo-open, so that it would be opened upon a
 * reset.
 *
 * @param clientState State of the client.
 */
void chppClientPseudoOpen(struct ChppEndpointState *clientState);

/**
 * Sends a client request for the open command in a blocking or non-blocking
 * manner.
 * A non-blocking open is used to for reopening a service after a reset or for
 * opening a pseudo-open service.
 *
 * @param clientState State of the client.
 * @param openReqState State of the request/response for the open command.
 * @param openCommand Open command to be sent.
 * @param blocking Indicates a blocking (vs. non-blocking) open request.
 *
 * @return Indicates success or failure.
 */
bool chppClientSendOpenRequest(struct ChppEndpointState *clientState,
                               struct ChppOutgoingRequestState *openReqState,
                               uint16_t openCommand, bool blocking);

/**
 * Processes a service response for the open command.
 *
 * @param clientState State of the client.
 */
void chppClientProcessOpenResponse(struct ChppEndpointState *clientState,
                                   uint8_t *buf, size_t len);

/**
 * Closes any remaining open requests by simulating a timeout.

 * This function is used when a client is reset.
 *
 * @param clientState State of the client.
 * @param client The client for which to clear out open requests.
 * @param clearOnly If true, indicates that a timeout response shouldn't be
 *        sent. This must only be set if the requests are being cleared as part
 *        of the client closing.
 */
void chppClientCloseOpenRequests(struct ChppEndpointState *clientState,
                                 const struct ChppClient *client,
                                 bool clearOnly);

/**
 * Allocates a client notification of a specified length.
 *
 * It is expected that for most use cases, the
 * chppAllocClientNotificationFixed() or
 * chppAllocClientNotificationTypedArray() macros shall be used rather than
 * calling this function directly.
 *
 * The caller must initialize at least the handle and command fields of the
 * ChppAppHeader.
 *
 * @param len Length of the notification (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory.
 */
struct ChppAppHeader *chppAllocClientNotification(size_t len);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_CLIENTS_H_
