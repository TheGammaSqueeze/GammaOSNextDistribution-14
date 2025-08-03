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

#ifndef CHPP_APP_H_
#define CHPP_APP_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/condition_variable.h"
#include "chpp/macros.h"
#include "chpp/transport.h"
#include "chre_api/chre/common.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

/**
 * Allocates a variable-length response message of a specific type.
 *
 * @param requestHeader request header, as per chppAllocResponse().
 * @param type Type of response which includes an arrayed member.
 * @param count number of items in the array of arrayField.
 * @param arrayField The arrayed member field.
 *
 * @return Pointer to allocated memory.
 */
#define chppAllocResponseTypedArray(requestHeader, type, count, arrayField) \
  (type *)chppAllocResponse(                                                \
      requestHeader,                                                        \
      sizeof(type) + (count)*sizeof_member(type, arrayField[0]))

/**
 * Allocates a response message of a specific type and its corresponding length.
 *
 * @param requestHeader request header, as per chppAllocResponse().
 * @param type Type of response.
 *
 * @return Pointer to allocated memory.
 */
#define chppAllocResponseFixed(requestHeader, type) \
  (type *)chppAllocResponse(requestHeader, sizeof(type))

/**
 * Maximum number of services that can be registered by CHPP (not including
 * predefined services), if not defined by the build system.
 */
#ifndef CHPP_MAX_REGISTERED_SERVICES
#define CHPP_MAX_REGISTERED_SERVICES 1
#endif

/**
 * Maximum number of clients that can be registered by CHPP (not including
 * predefined clients), if not defined by the build system.
 */
#ifndef CHPP_MAX_REGISTERED_CLIENTS
#define CHPP_MAX_REGISTERED_CLIENTS 1
#endif

/**
 * Maximum number of services that can be discovered by CHPP (not including
 * predefined services), if not defined by the build system.
 */
#ifndef CHPP_MAX_DISCOVERED_SERVICES
#define CHPP_MAX_DISCOVERED_SERVICES \
  MAX(CHPP_MAX_REGISTERED_SERVICES, CHPP_MAX_REGISTERED_CLIENTS)
#endif

#define CHPP_REQUEST_TIMEOUT_INFINITE CHPP_TIME_MAX

#if defined(CHPP_REQUEST_TIMEOUT_DEFAULT) && \
    defined(CHPP_CLIENT_REQUEST_TIMEOUT_DEFAULT)
// Build systems should prefer to only set CHPP_REQUEST_TIMEOUT_DEFAULT
#error Can not set both CHPP_REQUEST_TIMEOUT_DEFAULT and CHPP_CLIENT_REQUEST_TIMEOUT_DEFAULT
#endif

// For backwards compatibility with vendor build systems
#ifdef CHPP_CLIENT_REQUEST_TIMEOUT_DEFAULT
#define CHPP_REQUEST_TIMEOUT_DEFAULT CHPP_CLIENT_REQUEST_TIMEOUT_DEFAULT
#undef CHPP_CLIENT_REQUEST_TIMEOUT_DEFAULT
#endif

// If not customized in the build, we default to CHRE expectations
#ifndef CHPP_REQUEST_TIMEOUT_DEFAULT
#define CHPP_REQUEST_TIMEOUT_DEFAULT CHRE_ASYNC_RESULT_TIMEOUT_NS
#endif

/**
 * Default value for reserved fields.
 */
#define CHPP_RESERVED 0

/**
 * Client index number when there is no matching client
 */
#define CHPP_CLIENT_INDEX_NONE 0xff

/**
 * App layer command at initialization.
 */
#define CHPP_APP_COMMAND_NONE 0

/**
 * Type of endpoint (either client or service)
 */
enum ChppEndpointType {
  CHPP_ENDPOINT_CLIENT = 0,
  CHPP_ENDPOINT_SERVICE = 1,
};

/**
 * Handle Numbers in ChppAppHeader
 */
enum ChppHandleNumber {
  //! Handleless communication
  CHPP_HANDLE_NONE = 0x00,

  //! Loopback Service
  CHPP_HANDLE_LOOPBACK = 0x01,

  //! Time Service
  CHPP_HANDLE_TIMESYNC = 0x02,

  //! Discovery Service
  CHPP_HANDLE_DISCOVERY = 0x0F,

  //! Negotiated Services (starting from this offset)
  CHPP_HANDLE_NEGOTIATED_RANGE_START = 0x10,
};

/**
 * Message Types as used in ChppAppHeader
 */
#define CHPP_APP_MASK_MESSAGE_TYPE LEAST_SIGNIFICANT_NIBBLE
#define CHPP_APP_GET_MESSAGE_TYPE(value) \
  ((enum ChppMessageType)(               \
      (value)&CHPP_APP_MASK_MESSAGE_TYPE))  // TODO: Consider checking if this
                                            // maps into a valid enum
enum ChppMessageType {
  //! Request from client. Needs response from service.
  CHPP_MESSAGE_TYPE_CLIENT_REQUEST = 0,

  //! Response from service (with the same Command and Transaction ID as the
  //! client request).
  CHPP_MESSAGE_TYPE_SERVICE_RESPONSE = 1,

  //! Notification from client. Service shall not respond.
  CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION = 2,

  //! Notification from service. Client shall not respond.
  CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION = 3,

  //! Request from service. Needs response from client.
  CHPP_MESSAGE_TYPE_SERVICE_REQUEST = 4,

  //! Response from client (with the same Command and Transaction ID as the
  //! service request).
  CHPP_MESSAGE_TYPE_CLIENT_RESPONSE = 5,
};

/**
 * Error codes used by the app layer / clients / services.
 */
enum ChppAppErrorCode {
  //! Success (no error)
  CHPP_APP_ERROR_NONE = 0,
  //! Invalid command
  CHPP_APP_ERROR_INVALID_COMMAND = 1,
  //! Invalid argument(s)
  CHPP_APP_ERROR_INVALID_ARG = 2,
  //! Busy
  CHPP_APP_ERROR_BUSY = 3,
  //! Out of memory
  CHPP_APP_ERROR_OOM = 4,
  //! Feature not supported
  CHPP_APP_ERROR_UNSUPPORTED = 5,
  //! Timeout
  CHPP_APP_ERROR_TIMEOUT = 6,
  //! Functionality disabled (e.g. per user configuration)
  CHPP_APP_ERROR_DISABLED = 7,
  //! Rate limit exceeded (try again later)
  CHPP_APP_ERROR_RATELIMITED = 8,
  //! Function in use / blocked by another entity (e.g. the AP)
  CHPP_APP_ERROR_BLOCKED = 9,
  //! Invalid length
  CHPP_APP_ERROR_INVALID_LENGTH = 10,
  //! CHPP Not Ready
  CHPP_APP_ERROR_NOT_READY = 11,
  //! Error outside of CHPP (e.g. PAL API)
  CHPP_APP_ERROR_BEYOND_CHPP = 12,
  //! Response not matching a pending request
  CHPP_APP_ERROR_UNEXPECTED_RESPONSE = 13,
  //! Conversion failed
  CHPP_APP_ERROR_CONVERSION_FAILED = 14,
  //! Unspecified failure
  CHPP_APP_ERROR_UNSPECIFIED = 255
};

/**
 * Open status for clients / services.
 */
enum ChppOpenState {
  CHPP_OPEN_STATE_CLOSED = 0,           // Closed
  CHPP_OPEN_STATE_OPENING = 1,          // Enables the open request to pass
  CHPP_OPEN_STATE_WAITING_TO_OPEN = 2,  // Waiting for open response
  CHPP_OPEN_STATE_OPENED = 3,           // Opened
};

/**
 * CHPP Application Layer header
 */
CHPP_PACKED_START
struct ChppAppHeader {
  //! Service Handle
  uint8_t handle;

  //! Most significant nibble (MSN): Reserved
  //! Least significant nibble (LSN): Message Type from enum ChppMessageType
  uint8_t type;

  //! Transaction ID
  uint8_t transaction;

  //! Error if any, from enum ChppAppErrorCode
  uint8_t error;

  //! Command
  uint16_t command;

} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Function type that dispatches incoming datagrams for any client or service.
 *
 * The buffer is freed shortly after the function returns.
 * User code must make a copy for later processing if needed.
 */
typedef enum ChppAppErrorCode(ChppDispatchFunction)(void *context, uint8_t *buf,
                                                    size_t len);

/**
 * Function type that initializes a client and assigns it its handle number
 */
typedef bool(ChppClientInitFunction)(void *context, uint8_t handle,
                                     struct ChppVersion serviceVersion);

/**
 * Function type that deinitializes a client.
 */
typedef void(ChppClientDeinitFunction)(void *context);

/**
 * Function type that dispatches a reset notification to any client or service
 */
typedef void(ChppNotifierFunction)(void *context);

/**
 * Length of a service UUID and its human-readable printed form in bytes
 */
#define CHPP_SERVICE_UUID_LEN 16
#define CHPP_SERVICE_UUID_STRING_LEN (16 * 2 + 4 + 1)

/**
 * Length of a version number, in bytes (major + minor + revision), per CHPP
 * spec.
 */
#define CHPP_SERVICE_VERSION_LEN (1 + 1 + 2)

/**
 * Maximum length of a human-readable service name, per CHPP spec.
 * (15 ASCII characters + null)
 */
#define CHPP_SERVICE_NAME_MAX_LEN (15 + 1)

/**
 * Support for sync response.
 *
 * @see chppClientSendTimestampedRequestAndWaitTimeout.
 */
struct ChppSyncResponse {
  struct ChppMutex mutex;
  struct ChppConditionVariable condVar;
  bool ready;
};

/**
 * CHPP definition of a service descriptor as sent over the wire.
 */
CHPP_PACKED_START
struct ChppServiceDescriptor {
  //! UUID of the service.
  //! Must be generated according to RFC 4122, UUID version 4 (random).
  uint8_t uuid[CHPP_SERVICE_UUID_LEN];

  //! Human-readable name of the service for debugging.
  char name[CHPP_SERVICE_NAME_MAX_LEN];

  //! Version of the service.
  struct ChppVersion version;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * CHPP definition of a service as supported on a server.
 */
struct ChppService {
  //! Service Descriptor as sent over the wire.
  struct ChppServiceDescriptor descriptor;

  //! Notifies the service if CHPP is reset.
  ChppNotifierFunction *resetNotifierFunctionPtr;

  //! Dispatches incoming client requests.
  //! When an error is returned by the dispatch function it is logged and an
  //! error response is automatically sent to the remote endpoint.
  ChppDispatchFunction *requestDispatchFunctionPtr;

  //! Dispatches incoming client notifications.
  //! Errors returned by the dispatch function are logged.
  ChppDispatchFunction *notificationDispatchFunctionPtr;

  //! Dispatches incoming client responses.
  //! Errors returned by the dispatch function are logged.
  ChppDispatchFunction *responseDispatchFunctionPtr;

  //! Number of outgoing requests supported by this service.
  //! ChppAppHeader.command must be in the range [0, outReqCount - 1]
  //! ChppEndpointState.outReqStates must contains that many elements.
  uint16_t outReqCount;

  //! Minimum valid length of datagrams for the service:
  //! - client requests
  //! - client notifications
  //! - client responses
  size_t minLength;
};

/**
 * CHPP definition of a client descriptor.
 */
struct ChppClientDescriptor {
  //! UUID of the client.
  //! Must be generated according to RFC 4122, UUID version 4 (random).
  uint8_t uuid[CHPP_SERVICE_UUID_LEN];

  //! Version of the client.
  struct ChppVersion version;
};

/**
 * CHPP definition of a client.
 */
struct ChppClient {
  //! Client descriptor.
  struct ChppClientDescriptor descriptor;

  //! Notifies the client if CHPP is reset.
  ChppNotifierFunction *resetNotifierFunctionPtr;

  //! Notifies the client if CHPP is matched to a service.
  ChppNotifierFunction *matchNotifierFunctionPtr;

  //! Dispatches incoming service responses.
  //! Service responses are only dispatched to clients that have been opened or
  //! are in the process of being (re)opened. @see ChppOpenState.
  //! Errors returned by the dispatch function are logged.
  ChppDispatchFunction *responseDispatchFunctionPtr;

  //! Dispatches incoming service notifications.
  //! Service notifications are only dispatched to clients that have been
  //! opened. @see ChppOpenState
  //! Errors returned by the dispatch function are logged.
  ChppDispatchFunction *notificationDispatchFunctionPtr;

  //! Dispatches incoming service requests.
  //! When an error is returned by the dispatch function it is logged and an
  //! error response is automatically sent to the remote endpoint.
  ChppDispatchFunction *requestDispatchFunctionPtr;

  //! Initializes the client (after it is matched with a service at discovery)
  //! and assigns it its handle number.
  ChppClientInitFunction *initFunctionPtr;

  //! Deinitializes the client.
  ChppClientDeinitFunction *deinitFunctionPtr;

  //! Number of outgoing requests supported by this client.
  //! ChppAppHeader.command must be in the range [0, outReqCount - 1]
  //! ChppEndpointState.outReqStates must contains that many elements.
  uint16_t outReqCount;

  //! Minimum valid length of datagrams for the service:
  //! - service responses
  //! - service notifications
  //! - service requests
  size_t minLength;
};

/**
 * Request status for clients.
 */
enum ChppRequestState {
  CHPP_REQUEST_STATE_NONE = 0,              //!< No request sent ever
  CHPP_REQUEST_STATE_REQUEST_SENT = 1,      //!< Sent, waiting for a response
  CHPP_REQUEST_STATE_RESPONSE_RCV = 2,      //!< Sent and response received
  CHPP_REQUEST_STATE_RESPONSE_TIMEOUT = 3,  //!< Timeout. Responded as need be
};

/**
 * State of each outgoing request and their response.
 *
 * There must be as many ChppOutgoingRequestState in the client or service state
 * (ChppEndpointState) as the number of commands they support.
 */
struct ChppOutgoingRequestState {
  uint64_t requestTimeNs;  // Time of the last request
  // When requestState is CHPP_REQUEST_STATE_REQUEST_SENT,
  // indicates the timeout time for the request.
  // When requestState is CHPP_REQUEST_STATE_RESPONSE_RCV,
  // indicates when the response was received.
  uint64_t responseTimeNs;
  uint8_t requestState;  // From enum ChppRequestState
  uint8_t transaction;   // Transaction ID for the last request/response
};

/**
 * State of each incoming request and their response.
 *
 * There must be as many ChppIncomingRequestState in the client or service state
 * as the number of commands supported by the other side (corresponding service
 * for a client and corresponding client for a service).
 *
 * Contrary to ChppOutgoingRequestState those are not part of
 * CChppEndpointState. They must be stored to and retrieved from the context
 * passed to chppRegisterClient / chppRegisterService.
 *
 * Note: while ChppIncomingRequestState and ChppOutgoingRequestState have the
 * same layout, we want the types to be distinct to be enforced at compile time.
 * Using a typedef would make both types equivalent.
 *
 * @see ChppOutgoingRequestState for field details.
 */
struct ChppIncomingRequestState {
  uint64_t requestTimeNs;
  uint64_t responseTimeNs;
  uint8_t requestState;
  uint8_t transaction;
};

/**
 * Enabled clients and services.
 */
struct ChppClientServiceSet {
  bool wifiService : 1;
  bool gnssService : 1;
  bool wwanService : 1;
  bool wifiClient : 1;
  bool gnssClient : 1;
  bool wwanClient : 1;
  bool loopbackClient : 1;
};

struct ChppLoopbackClientState;
struct ChppTimesyncClientState;

/**
 * CHPP state of a client or a service.
 *
 * This is the CHPP internal client/service state.
 * Their private state is store in the context field.
 */
struct ChppEndpointState {
  struct ChppAppState *appContext;  // Pointer to app layer context

  // State for the outgoing requests.
  // It must accommodate Chpp{Client,Service}.outReqCount elements.
  // It also tracks corresponding incoming responses.
  // NULL when outReqCount = 0.
  struct ChppOutgoingRequestState *outReqStates;

  void *context;  //!< Private state of the endpoint.

  struct ChppSyncResponse syncResponse;

  uint8_t index;        //!< Index (in ChppAppState lists).
  uint8_t handle;       //!< Handle used to match client and service.
  uint8_t transaction;  //!< Next Transaction ID to be used.

  uint8_t openState;  //!< see enum ChppOpenState

  bool pseudoOpen : 1;       //!< Client to be opened upon a reset
  bool initialized : 1;      //!< Client is initialized
  bool everInitialized : 1;  //!< Client sync primitives initialized
};

struct ChppAppState {
  struct ChppTransportState *transportContext;  // Pointing to transport context

  const struct chrePalSystemApi *systemApi;  // Pointing to the PAL system APIs

  uint8_t registeredServiceCount;  // Number of services currently registered

  const struct ChppService *registeredServices[CHPP_MAX_REGISTERED_SERVICES];

  struct ChppEndpointState
      *registeredServiceStates[CHPP_MAX_REGISTERED_SERVICES];

  uint8_t registeredClientCount;  // Number of clients currently registered

  const struct ChppClient *registeredClients[CHPP_MAX_REGISTERED_CLIENTS];

  struct ChppEndpointState *registeredClientStates[CHPP_MAX_REGISTERED_CLIENTS];

  // When the first outstanding request sent from the client timeouts.
  uint64_t nextClientRequestTimeoutNs;
  // When the first outstanding request sent from the service timeouts.
  uint64_t nextServiceRequestTimeoutNs;

  uint8_t
      clientIndexOfServiceIndex[CHPP_MAX_DISCOVERED_SERVICES];  // Lookup table

  struct ChppClientServiceSet clientServiceSet;  // Enabled client/services

  // Pointers to the contexts of basic clients, which are allocated if and when
  // they are initialized
  struct ChppLoopbackClientState *loopbackClientContext;
  struct ChppTimesyncClientState *timesyncClientContext;

  // For discovery clients
  bool isDiscoveryClientEverInitialized;
  bool isDiscoveryClientInitialized;
  bool isDiscoveryComplete;

  // The number of clients that matched a service during discovery.
  uint8_t matchedClientCount;

  // The number of services that were found during discovery.
  uint8_t discoveredServiceCount;

  struct ChppMutex discoveryMutex;
  struct ChppConditionVariable discoveryCv;
};

#define CHPP_SERVICE_INDEX_OF_HANDLE(handle) \
  ((handle)-CHPP_HANDLE_NEGOTIATED_RANGE_START)

#define CHPP_SERVICE_HANDLE_OF_INDEX(index) \
  ((index) + CHPP_HANDLE_NEGOTIATED_RANGE_START)

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Initializes the CHPP app layer state stored in the parameter appContext.
 * It is necessary to initialize state for each app layer instance on
 * every platform.
 *
 * @param appContext Maintains status for each app layer instance.
 * @param transportContext The transport layer status struct associated with
 * this app layer instance.
 */
void chppAppInit(struct ChppAppState *appContext,
                 struct ChppTransportState *transportContext);

/**
 * Same as chppAppInit(), but specifies the client/service endpoints to be
 * enabled.
 *
 * @param appContext Maintains status for each app layer instance.
 * @param transportContext The transport layer status struct associated with
 * this app layer instance.
 * @param clientServiceSet Bitmap specifying the client/service endpoints to be
 * enabled.
 */
void chppAppInitWithClientServiceSet(
    struct ChppAppState *appContext,
    struct ChppTransportState *transportContext,
    struct ChppClientServiceSet clientServiceSet);

/**
 * Deinitializes the CHPP app layer for e.g. clean shutdown.
 *
 * @param appContext A non-null pointer to ChppAppState initialized previously
 * in chppAppInit().
 */
void chppAppDeinit(struct ChppAppState *appContext);

/**
 * Processes an Rx Datagram from the transport layer.
 *
 * @param context Maintains status for each app layer instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 */
void chppAppProcessRxDatagram(struct ChppAppState *context, uint8_t *buf,
                              size_t len);

/**
 * Used by the transport layer to notify the app layer of a reset during
 * operation. This function is called after the transport layer has sent a reset
 * or reset-ack packet.
 * In turn, this function notifies clients and services to allow them to reset
 * or recover state as necessary.
 *
 * @param context Maintains status for each app layer instance.
 */
void chppAppProcessReset(struct ChppAppState *context);

/**
 * Convert UUID to a human-readable, null-terminated string.
 *
 * @param uuid Input UUID
 * @param strOut Output null-terminated string
 */
void chppUuidToStr(const uint8_t uuid[CHPP_SERVICE_UUID_LEN],
                   char strOut[CHPP_SERVICE_UUID_STRING_LEN]);

/**
 * Maps a CHPP app layer error to a CHRE error.
 *
 * @param chppError CHPP app layer error (from enum ChppAppErrorCode).
 *
 * @return CHRE error (from enum chreError).
 */
uint8_t chppAppErrorToChreError(uint8_t error);

/**
 * Handles logging and error conversion when an app layer response is too short.
 *
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 * @param responseName Name of the request/response to be logged.
 *
 * @return CHRE error (from enum chreError).
 */
uint8_t chppAppShortResponseErrorHandler(uint8_t *buf, size_t len,
                                         const char *responseName);

/**
 * Allocates a notification of a specified length.
 *
 * This function is internal. Instead use either
 * - chppAllocClientNotification
 * - or chppAllocServiceNotification
 *
 * The caller must initialize at least the handle and command fields of the
 * ChppAppHeader.
 *
 * @param type CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION or
 *        CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION.
 * @param len Length of the notification (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory.
 */
struct ChppAppHeader *chppAllocNotification(uint8_t type, size_t len);

/**
 * Allocates a request message.
 *
 * This function is internal. Instead use either:
 * - chppAllocClientRequest
 * - or chppAllocServiceRequest
 *
 * @param type CHPP_MESSAGE_TYPE_CLIENT_REQUEST or
 *        CHPP_MESSAGE_TYPE_SERVICE_REQUEST.
 * @param endpointState State of the endpoint.
 * @param len Length of the response message (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory.
 */
struct ChppAppHeader *chppAllocRequest(uint8_t type,
                                       struct ChppEndpointState *endpointState,
                                       size_t len);

/**
 * Allocates a response message of a specified length, populating the (app
 * layer) response header according to the provided request (app layer) header.
 *
 * This function can be used to allocate either client or service response.
 *
 * @param requestHeader request header.
 * @param len Length of the response message (including header) in bytes. Note
 *        that the specified length must be at least equal to the length of the
 *        app layer header.
 *
 * @return Pointer to allocated memory.
 */
struct ChppAppHeader *chppAllocResponse(
    const struct ChppAppHeader *requestHeader, size_t len);

/**
 * This function shall be called for all incoming requests in order to
 * A) Timestamp them, and
 * B) Save their Transaction ID
 *
 * This function prints an error message if a duplicate request is received
 * while outstanding request is still pending without a response.
 *
 * @param inReqState State of the request/response.
 * @param requestHeader Request header.
 */
void chppTimestampIncomingRequest(struct ChppIncomingRequestState *inReqState,
                                  const struct ChppAppHeader *requestHeader);

/**
 * This function shall be called for all outgoing requests in order to
 * A) Timestamp them, and
 * B) Save their Transaction ID
 *
 * This function prints an error message if a duplicate request is sent
 * while outstanding request is still pending without a response.
 *
 * @param appState App layer state.
 * @param outReqState state of the request/response.
 * @param requestHeader Client request header.
 * @param timeoutNs The timeout.
 */
void chppTimestampOutgoingRequest(struct ChppAppState *appState,
                                  struct ChppOutgoingRequestState *outReqState,
                                  const struct ChppAppHeader *requestHeader,
                                  uint64_t timeoutNs);

/**
 * This function shall be called for incoming responses to a request in
 * order to
 * A) Verify the correct transaction ID
 * B) Timestamp them, and
 * C) Mark them as fulfilled
 *
 * This function prints an error message if a response is received without an
 * outstanding request.
 *
 * @param appState App layer state.
 * @param outReqState state of the request/response.
 * @param requestHeader Request header.
 *
 * @return false if there is an error. true otherwise.
 */
bool chppTimestampIncomingResponse(struct ChppAppState *appState,
                                   struct ChppOutgoingRequestState *outReqState,
                                   const struct ChppAppHeader *responseHeader);

/**
 * This function shall be called for the outgoing response to a request in order
 * to:
 * A) Timestamp them, and
 * B) Mark them as fulfilled part of the request/response's
 *    ChppOutgoingRequestState struct.
 *
 * For most responses, it is expected that chppSendTimestampedResponseOrFail()
 * shall be used to both timestamp and send the response in one shot.
 *
 * @param inReqState State of the request/response.
 * @return The last response time (CHPP_TIME_NONE for the first response).
 */
uint64_t chppTimestampOutgoingResponse(
    struct ChppIncomingRequestState *inReqState);

/**
 * Timestamps a response using chppTimestampOutgoingResponse() and enqueues it
 * using chppEnqueueTxDatagramOrFail().
 *
 * Refer to their respective documentation for details.
 *
 * This function logs an error message if a response is attempted without an
 * outstanding request.
 *
 * @param appState App layer state.
 * @param inReqState State of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return whether the datagram was successfully enqueued. false means that the
 *         queue was full and the payload discarded.
 */
bool chppSendTimestampedResponseOrFail(
    struct ChppAppState *appState, struct ChppIncomingRequestState *inReqState,
    void *buf, size_t len);

/**
 * Timestamps and enqueues a request.
 *
 * This function is internal. User either:
 * - chppClientSendTimestampedRequestOrFail
 * - or chppServiceSendTimestampedRequestOrFail
 *
 * Note that the ownership of buf is taken from the caller when this method is
 * invoked.
 *
 * @param endpointState state of the endpoint.
 * @param outReqState state of the request/response.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 * @param timeoutNs Time in nanoseconds before a timeout response is generated.
 *        Zero means no timeout response.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 *         False informs the sender that the queue was full and the payload
 *         discarded.
 */
bool chppSendTimestampedRequestOrFail(
    struct ChppEndpointState *endpointState,
    struct ChppOutgoingRequestState *outReqState, void *buf, size_t len,
    uint64_t timeoutNs);

/**
 * Wait for a response to be received.
 *
 * @param syncResponse sync primitives.
 * @param outReqState state of the request/response.
 * @param timeoutNs Time in nanoseconds before a timeout response is generated.
 */
bool chppWaitForResponseWithTimeout(
    struct ChppSyncResponse *syncResponse,
    struct ChppOutgoingRequestState *outReqState, uint64_t timeoutNs);

/**
 * Returns the state of a registered endpoint.
 *
 * @param appState State of the app layer.
 * @param index Index of the client or service.
 * @param type Type of the endpoint to return.
 * @return state of the client or service.
 */
struct ChppEndpointState *getRegisteredEndpointState(
    struct ChppAppState *appState, uint8_t index, enum ChppEndpointType type);

/**
 * Returns the number of possible outgoing requests.
 *
 * @param appState State of the app layer.
 * @param index Index of the client or service.
 * @param type Type of the endpoint to return.
 * @return The number of possible outgoing requests.
 */
uint16_t getRegisteredEndpointOutReqCount(struct ChppAppState *appState,
                                          uint8_t index,
                                          enum ChppEndpointType type);

/**
 * Returns the number of registered endpoints of the given type.
 *
 * @param appState State of the app layer.
 * @param type Type of the endpoint to return.
 * @return The number of endpoints.
 */
uint8_t getRegisteredEndpointCount(struct ChppAppState *appState,
                                   enum ChppEndpointType type);

/**
 * Recalculates the next upcoming request timeout.
 *
 * The timeout is updated in the app layer state.
 *
 * @param appState State of the app layer.
 * @param type Type of the endpoint.
 */
void chppRecalculateNextTimeout(struct ChppAppState *appState,
                                enum ChppEndpointType type);

/**
 * Returns a pointer to the next request timeout for the given endpoint type.
 *
 * @param appState State of the app layer.
 * @param type Type of the endpoint.
 * @return Pointer to the timeout in nanoseconds.
 */
uint64_t *getNextRequestTimeoutNs(struct ChppAppState *appState,
                                  enum ChppEndpointType type);

/**
 * Closes any remaining open requests by simulating a timeout.
 *
 * This function is used when an endpoint is reset.
 *
 * @param endpointState State of the endpoint.
 * @param type The type of the endpoint.
 * @param clearOnly If true, indicates that a timeout response shouldn't be
 *        sent. This must only be set if the requests are being cleared as
 *        part of the closing.
 */
void chppCloseOpenRequests(struct ChppEndpointState *endpointState,
                           enum ChppEndpointType type, bool clearOnly);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_APP_H_
