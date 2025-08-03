/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef CHPP_TRANSPORT_H_
#define CHPP_TRANSPORT_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/condition_variable.h"
#include "chpp/link.h"
#include "chpp/macros.h"
#include "chpp/mutex.h"
#include "chpp/notifier.h"
#include "chpp/transport_signals.h"
#include "chpp/work_monitor.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

/**
 * CHPP Transport layer reset timeout in ns. The transport layer will attempt
 * another reset if the previous reset is not acked in time.
 */
#ifndef CHPP_TRANSPORT_RESET_TIMEOUT_NS
#define CHPP_TRANSPORT_RESET_TIMEOUT_NS \
  (UINT64_C(1500) * CHPP_NSEC_PER_MSEC)  // 1500 ms
#endif

/**
 * CHPP Transport layer timeout for tx packets.
 */
#ifndef CHPP_TRANSPORT_TX_TIMEOUT_NS
#define CHPP_TRANSPORT_TX_TIMEOUT_NS \
  (UINT64_C(100) * CHPP_NSEC_PER_MSEC)  // 100 ms
#endif

/**
 * CHPP Transport layer timeout for rx packets.
 */
#ifndef CHPP_TRANSPORT_RX_TIMEOUT_NS
#define CHPP_TRANSPORT_RX_TIMEOUT_NS \
  (UINT64_C(80) * CHPP_NSEC_PER_MSEC)  // 80 ms
#endif

/**
 * CHPP Transport layer maximum retransmission attempts, after which a reset is
 * attempted. Setting this to zero disables retransmissions.
 */
#ifndef CHPP_TRANSPORT_MAX_RETX
#define CHPP_TRANSPORT_MAX_RETX UINT16_C(4)
#endif

/**
 * CHPP Transport layer maximum reset attempts. Current functional values are 1
 * or higher (setting to 0 currently functions identically to 1).
 */
#ifndef CHPP_TRANSPORT_MAX_RESET
#define CHPP_TRANSPORT_MAX_RESET UINT16_C(3)
#endif

/**
 * CHPP Transport layer predefined timeout values.
 */
#define CHPP_TRANSPORT_TIMEOUT_INFINITE UINT64_MAX
#define CHPP_TRANSPORT_TIMEOUT_IMMEDIATE 0

// This lint rule is meant to ensure we make appropriate test updates whenever
// there are changes to the transport protocol.
// LINT.IfChange
/**
 * CHPP Transport header flags bitmap
 *
 * @defgroup CHPP_TRANSPORT_FLAG
 * @{
 */
// This packet concludes a (fragmented or unfragmented) datagram
#define CHPP_TRANSPORT_FLAG_FINISHED_DATAGRAM 0x00
// Set if packet is part of a fragmented datagram, except for the last fragment
#define CHPP_TRANSPORT_FLAG_UNFINISHED_DATAGRAM 0x01
// Reserved for future use
#define CHPP_TRANSPORT_FLAG_RESERVED 0xfe
/** @} */

/**
 * Preamble (i.e. packet start delimiter) for this version of CHPP is "Ch".
 * Any future backwards-incompatible versions of CHPP Transport will use a
 * different preamble.
 *
 * @defgroup CHPP_PREAMBLE
 * @{
 */
#define CHPP_PREAMBLE_DATA 0x6843
#define CHPP_PREAMBLE_LEN_BYTES 2
/** @} */

/**
 * Macros for a specific byte in the CHPP_PREAMBLE.
 * Using the CHPP_PREAMBLE_BYTE_... macros are preferred due to a reduced risk
 * of mistakes.
 */
#define chppPreambleByte(loc) \
  ((CHPP_PREAMBLE_DATA >> (8 * (CHPP_PREAMBLE_LEN_BYTES - (loc)-1))) & 0xff)
#define CHPP_PREAMBLE_BYTE_FIRST chppPreambleByte(0)
#define CHPP_PREAMBLE_BYTE_SECOND chppPreambleByte(1)

/**
 * Maximum number of datagrams in the Tx queue.
 * CHPP will return an error if it is provided with a new Tx datagram when this
 * queue is full.
 * To be safe, this should be less than half of the maximum uint8_t value.
 * Otherwise, ChppTxDatagramQueue should be updated accordingly.
 */
#define CHPP_TX_DATAGRAM_QUEUE_LEN ((uint8_t)16)

/**
 * Encoding overhead of the transport layer in bytes.
 */
#define CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES                               \
  ((uint16_t)(CHPP_PREAMBLE_LEN_BYTES + sizeof(struct ChppTransportHeader) + \
              sizeof(struct ChppTransportFooter)))

/************************************************
 *  Status variables to store context in lieu of global variables (this)
 ***********************************************/

/**
 * Error codes optionally reported in ChppTransportHeader (Least significant
 * nibble of int8_t packetCode).
 */
#define CHPP_TRANSPORT_ERROR_MASK LEAST_SIGNIFICANT_NIBBLE
#define CHPP_TRANSPORT_GET_ERROR(value) \
  ((enum ChppTransportErrorCode)(       \
      (value)&CHPP_TRANSPORT_ERROR_MASK))  // TODO: Consider checking if this
                                           // maps into a valid enum
enum ChppTransportErrorCode {
  //! No error reported (either ACK or implicit NACK)
  CHPP_TRANSPORT_ERROR_NONE = 0,
  //! Checksum failure
  CHPP_TRANSPORT_ERROR_CHECKSUM = 1,
  //! Out of memory
  CHPP_TRANSPORT_ERROR_OOM = 2,
  //! Busy
  CHPP_TRANSPORT_ERROR_BUSY = 3,
  //! Invalid header
  CHPP_TRANSPORT_ERROR_HEADER = 4,
  //! Out of order
  CHPP_TRANSPORT_ERROR_ORDER = 5,
  //! Timeout
  CHPP_TRANSPORT_ERROR_TIMEOUT = 6,
  //! Too many retries
  CHPP_TRANSPORT_ERROR_MAX_RETRIES = 7,
  //! Message incomprehensible at App Layer
  CHPP_TRANSPORT_ERROR_APPLAYER = 0xF,
};

/**
 * Packet attributes in ChppTransportHeader (Most significant nibble (MSN) of
 * int8_t packetCode).
 */
#define CHPP_TRANSPORT_ATTR_VALUE(value) (((value)&0x0f) << 4)
#define CHPP_TRANSPORT_ATTR_MASK MOST_SIGNIFICANT_NIBBLE
#define CHPP_TRANSPORT_GET_ATTR(value)   \
  ((enum ChppTransportPacketAttributes)( \
      (value)&CHPP_TRANSPORT_ATTR_MASK))  // TODO: Consider checking if this
                                          // maps into a valid enum

enum ChppTransportPacketAttributes {
  //! None
  CHPP_TRANSPORT_ATTR_NONE = CHPP_TRANSPORT_ATTR_VALUE(0),
  //! Reset
  CHPP_TRANSPORT_ATTR_RESET = CHPP_TRANSPORT_ATTR_VALUE(1),
  //! Reset Ack
  CHPP_TRANSPORT_ATTR_RESET_ACK = CHPP_TRANSPORT_ATTR_VALUE(2),
  //! Transport-Layer Loopback Request
  CHPP_TRANSPORT_ATTR_LOOPBACK_REQUEST = CHPP_TRANSPORT_ATTR_VALUE(3),
  //! Transport-Layer Loopback Response
  CHPP_TRANSPORT_ATTR_LOOPBACK_RESPONSE = CHPP_TRANSPORT_ATTR_VALUE(4),
};

#define CHPP_ATTR_AND_ERROR_TO_PACKET_CODE(attr, error) \
  ((uint8_t)(attr & CHPP_TRANSPORT_ATTR_MASK) |         \
   (uint8_t)(error & CHPP_TRANSPORT_ERROR_MASK))

/**
 * CHPP Transport Layer header (not including the preamble)
 */
CHPP_PACKED_START
struct ChppTransportHeader {
  //! Flags bitmap, defined as CHPP_TRANSPORT_FLAG_...
  uint8_t flags;

  //! LS Nibble: Defined in enum ChppTransportErrorCode
  //! MS Nibble: Defined in enum ChppTransportPacketAttributes
  uint8_t packetCode;

  //! Next expected sequence number for a payload-bearing packet
  uint8_t ackSeq;

  //! Sequence number
  uint8_t seq;

  //! Payload length in bytes (not including header / footer)
  uint16_t length;

  //! Reserved
  uint16_t reserved;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * CHPP Transport Layer footer (containing the checksum)
 */
CHPP_PACKED_START
struct ChppTransportFooter {
  uint32_t checksum;  // IEEE CRC-32 initialized to 0xFFFFFFFF
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

enum ChppRxState {
  //! Waiting for, or processing, the preamble (i.e. packet start delimiter)
  //! Moves to CHPP_STATE_HEADER as soon as it has seen a complete preamble.
  CHPP_STATE_PREAMBLE = 0,

  //! Processing the packet header. Moves to CHPP_STATE_PAYLOAD after processing
  //! the expected length of the header.
  CHPP_STATE_HEADER = 1,

  //! Copying the packet payload. The payload length is determined by the
  //! header.
  //! Moves to CHPP_STATE_FOOTER afterwards.
  CHPP_STATE_PAYLOAD = 2,

  //! Processing the packet footer (checksum) and responding accordingly. Moves
  //! to CHPP_STATE_PREAMBLE afterwards.
  CHPP_STATE_FOOTER = 3,
};

enum ChppResetState {
  CHPP_RESET_STATE_RESETTING = 0,          //! Reset in progress
  CHPP_RESET_STATE_NONE = 1,               //! Not in the middle of a reset
  CHPP_RESET_STATE_PERMANENT_FAILURE = 2,  //! Failed, will not retry
};

/**
 * Semantic Versioning system of CHRE.
 */
CHPP_PACKED_START
struct ChppVersion {
  //! Major version of (breaking changes).
  uint8_t major;

  //! Minor version (backwards compatible changes).
  uint8_t minor;

  //! Patch version (bug fixes).
  uint16_t patch;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Payload that is sent along reset and reset-ack packets.
 */
CHPP_PACKED_START
struct ChppTransportConfiguration {
  //! CHPP transport version.
  struct ChppVersion version;

  //! CHPP 1.0.0 unused "Receive MTU size".
  uint16_t reserved1;

  //! CHPP 1.0.0 unused "window size".
  uint16_t reserved2;

  //! CHPP 1.0.0 unused "Transport layer timeout in milliseconds".
  uint16_t reserved3;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END
// LINT.ThenChange(../../../chpp/test/packet_util.cpp)

struct ChppRxStatus {
  //! Current receiving state, as described in ChppRxState.
  enum ChppRxState state;

  //! Location counter in bytes within each state. Must always be reinitialized
  //! to 0 when switching states.
  size_t locInState;

  //! Next expected sequence number (for a payload-bearing packet)
  uint8_t expectedSeq;

  //! Packet (error) code, if any, of the last received packet
  uint8_t receivedPacketCode;

  //! Last received ACK sequence number (i.e. next expected sequence number for
  //! an outgoing payload-bearing packet)
  uint8_t receivedAckSeq;

  //! Time when starting to receive the current packet (i.e. after preamble).
  uint64_t packetStartTimeNs;

  //! Location counter in bytes within the current Rx datagram.
  size_t locInDatagram;

  //! The total number of data received in chppRxDataCb.
  size_t numTotalDataBytes;

  //! The timestamp when the transport received any data through chppRxDataCb.
  uint32_t lastDataTimeMs;

  //! The timestamp when the transport received a good RX packet.
  uint32_t lastGoodPacketTimeMs;
};

struct ChppTxStatus {
  //! Last sent ACK sequence number (i.e. next expected sequence number for
  //! an incoming payload-bearing packet)
  uint8_t sentAckSeq;

  //! Last sent sequence number (irrespective of whether it has been received /
  //! ACKed or not)
  uint8_t sentSeq;

  //! Does the transport layer have any packets (with or without payload) it
  //! needs to send out?
  bool hasPacketsToSend;

  //! Error code, if any, of the next packet the transport layer will send out.
  uint8_t packetCodeToSend;

  //! How many times the last sent sequence number has been (re-)sent.
  size_t txAttempts;

  //! Time when the last packet was sent to the link layer.
  uint64_t lastTxTimeNs;

  //! How many bytes of the front-of-queue datagram has been sent out
  size_t sentLocInDatagram;

  //! Note: For a future ACK window >1, sentLocInDatagram doesn't always apply
  //! to the front-of-queue datagram. Instead, we need to track the queue
  //! position the datagram being sent as well (relative to the front-of-queue).
  //! e.g. uint8_t datagramBeingSent

  //! How many bytes of the front-of-queue datagram has been acked
  size_t ackedLocInDatagram;

  //! Whether the link layer is still processing the pending packet
  bool linkBusy;
};

struct ChppDatagram {
  //! Length of datagram payload in bytes (A datagram can be constituted from
  //! one or more packets)
  size_t length;

  // Datagram payload
  uint8_t *payload;
};

struct ChppTxDatagramQueue {
  //! Number of pending datagrams in the queue.
  uint8_t pending;

  //! Index of the datagram at the front of the queue.
  uint8_t front;

  //! Location counter within the front datagram (i.e. the datagram at the front
  //! of the queue), showing how many bytes of this datagram have already been
  //! packetized and processed.
  size_t loc;

  //! Array of datagrams
  struct ChppDatagram datagram[CHPP_TX_DATAGRAM_QUEUE_LEN];
};

struct ChppTransportState {
  struct ChppAppState *appContext;  // Pointer to app layer context

  struct ChppRxStatus rxStatus;         // Rx state and location within
  struct ChppTransportHeader rxHeader;  // Rx packet header
  struct ChppTransportFooter rxFooter;  // Rx packet footer (checksum)
  struct ChppDatagram rxDatagram;       // Rx datagram
  uint8_t loopbackResult;  // Last transport-layer loopback test result as an
                           // enum ChppAppErrorCode

  struct ChppTxStatus txStatus;                // Tx state
  struct ChppTxDatagramQueue txDatagramQueue;  // Queue of datagrams to be Tx

  size_t linkBufferSize;  // Number of bytes currently in the Tx Buffer
  void *linkContext;      // Pointer to the link layer state
  const struct ChppLinkApi *linkApi;  // Link API

  struct ChppDatagram transportLoopbackData;   // Transport-layer loopback
                                               // request data, if any

  struct ChppMutex mutex;          // Lock for transport state (i.e. context)
  struct ChppNotifier notifier;    // Notifier for main thread
  bool initialized;                // Has been initialized
  enum ChppResetState resetState;  // Maintains state of a reset
  uint16_t resetCount;             // (Unsuccessful) reset attempts
  uint64_t resetTimeNs;            // Time of last reset

  struct ChppConditionVariable
      resetCondVar;  // Condvar specifically to wait for resetState

#ifdef CHPP_ENABLE_WORK_MONITOR
  struct ChppWorkMonitor workMonitor;  // Monitor used for the transport thread
#endif
};

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Initializes the CHPP transport layer state stored in the parameter
 * transportContext.
 * It is necessary to initialize state for each transport layer instance on
 * every platform.
 * Each transport layer instance is associated with a single application layer
 * instance. appContext points to the application layer status struct associated
 * with this transport layer instance.
 *
 * Calling this method will in turn call the init method of the linkApi with the
 * linkContext as the first parameter.
 *
 * @param transportContext Maintains state for each transport layer instance.
 * @param appContext The app layer state associated with this transport
 *                   layer instance.
 * @param linkContext The associated link layer state.
 * @param linkApi The API of the link layer
 */
void chppTransportInit(struct ChppTransportState *transportContext,
                       struct ChppAppState *appContext, void *linkContext,
                       const struct ChppLinkApi *linkApi);

/**
 * Deinitializes the CHPP transport layer and does necessary clean-ups for
 * e.g. clean shutdown.
 *
 * @param transportContext A non-null pointer to ChppTransportState
 * initialized previously in chppTransportInit().
 */
void chppTransportDeinit(struct ChppTransportState *transportContext);

/**
 * Blocking call until CHPP has finished resetting.
 *
 * @param transportContext, A non-null pointer to ChppTransportState
 * initialized previously in chppTransportDeinit().
 * @param timeoutMs The timeout in milliseconds.
 *
 * @return False if timed out.
 */
bool chppTransportWaitForResetComplete(
    struct ChppTransportState *transportContext, uint64_t timeoutMs);

/**
 * Processes all incoming data on the serial port based on the Rx state.
 * stream. Checks checksum, triggering the correct response (ACK / NACK).
 * Moves the state to CHPP_STATE_PREAMBLE afterwards.
 *
 * TODO: Add requirements, e.g. context must not be modified unless locked via
 * mutex.
 *
 * TODO: Add sufficient outward facing documentation
 *
 * @param context Maintains state for each transport layer instance.
 * @param buf Input data. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return true informs the serial port driver that we are waiting for a
 * preamble. This allows the driver to (optionally) filter incoming zeros and
 * save processing
 */
bool chppRxDataCb(struct ChppTransportState *context, const uint8_t *buf,
                  size_t len);

/**
 * Optional callback function for the link layer to indicate the end of a
 * packet. The availability of this information depends on the link layer
 * implementation.
 *
 * @param context Maintains state for each transport layer instance.
 */
void chppRxPacketCompleteCb(struct ChppTransportState *context);

/**
 * Enqueues an outgoing datagram of a specified length and frees the payload
 * asynchronously after it is sent. The payload must have been allocated by the
 * caller using chppMalloc.
 *
 * If enqueueing a datagram is unsuccessful, the payload is freed (discarded)
 * and an error message printed.
 *
 * Note that the ownership of buf is taken from the caller when this method is
 * invoked.
 *
 * @param context Maintains state for each transport layer instance.
 * @param buf Datagram payload allocated through chppMalloc. Cannot be null.
 * @param len Datagram length in bytes.
 *
 * @return True informs the sender that the datagram was successfully enqueued.
 * False informs the sender that the queue was full and the payload discarded.
 */
bool chppEnqueueTxDatagramOrFail(struct ChppTransportState *context, void *buf,
                                 size_t len);

/**
 * Enables the App Layer to enqueue an outgoing error datagram, for example for
 * an OOM situation over the wire.
 *
 * @param context Maintains state for each transport layer instance.
 * @param errorCode Error code to be sent.
 */
void chppEnqueueTxErrorDatagram(struct ChppTransportState *context,
                                enum ChppTransportErrorCode errorCode);

/**
 * Provides systems that do not use chppWorkThreadStart() and its associated
 * timeout mechanisms (that relies on chppNotifierTimedWait()) how long they
 * should wait until they run chppTransportDoWork() again, in nanoseconds.
 *
 * For these implementations, chppTransportDoWork() should be run at or slightly
 * after the wait time returned by this function.
 *
 * A return value of CHPP_TRANSPORT_TIMEOUT_INFINITE indicates that there is no
 * need to run chppTransportDoWork() based on a timeout (i.e. CHPP is not
 * waiting on an ACK).
 *
 * A return value of CHPP_TRANSPORT_TIMEOUT_IMMEDIATE indicates that
 * chppTransportDoWork() should be run immediately.
 *
 * @param context Maintains state for each transport layer instance.
 *
 * @return Time until chppTransportDoWork() must be called in nanoseconds.
 */
uint64_t chppTransportGetTimeUntilNextDoWorkNs(
    struct ChppTransportState *context);

/**
 * Starts the main thread for CHPP's Transport Layer. This thread needs to be
 * started after the Transport Layer is initialized through chppTransportInit().
 * Note that a platform may implement this as a new thread or as part of an
 * existing thread.
 *
 * If needed (e.g. for testing and debugging), this thread can be stopped by
 * calling chppWorkThreadStop().
 *
 * If a system does not support multi-threading, the system MUST replicate the
 * high-level behavior of chppWorkThreadStart(). More details in the
 * documentation of chppWorkThreadHandleSignal(). For such systems,
 * chppTransportGetTimeUntilNextDoWorkNs() can be used to replicate the
 * functionality of chppNotifierTimedWait().
 *
 * @param context Maintains state for each transport layer instance.
 */
void chppWorkThreadStart(struct ChppTransportState *context);

/**
 * Handles signals set for the CHPP transport instance. This method should be
 * invoked externally if chppWorkThreadStart() cannot be directly used, for
 * example if the system does not support thread signaling and needs explicit
 * control of the CHPP work thread from an outer control loop. By "outer control
 * loop," we mean the code path triggering work on the CHPP transport layer.
 *
 * Note that if a platform uses this method, the outer control loop MUST
 * replicate the behavior in the chppWorkThreadStart() method exactly. All
 * pending signals MUST be handled prior to the suspension of the outer control
 * loop, and any initialization sequence MUST be replicated.
 *
 * @param context Maintains state for each transport layer instance.
 * @param signals The signals to process. Should be obtained via
 * chppNotifierTimedWait() for the given transport context's notifier.
 *
 * @return true if the CHPP work thread should exit.
 */
bool chppWorkThreadHandleSignal(struct ChppTransportState *context,
                                uint32_t signals);

/**
 * Signals the main thread for CHPP's Transport Layer to perform some work. This
 * method should only be called from the link layer.
 *
 * Note that this method must be safe to call from an interrupt context, as the
 * platform link layer implementation may send a signal from one (e.g. handling
 * an interrupt from the physical layer or inputs from the remote endpoint).
 *
 * @param params Platform-specific struct with link details / parameters.
 * @param signal The signal that describes the work to be performed. Only bits
 * specified by CHPP_TRANSPORT_SIGNAL_PLATFORM_MASK can be set.
 */
static inline void chppWorkThreadSignalFromLink(
    struct ChppTransportState *context, uint32_t signal) {
  CHPP_ASSERT((signal & ~(CHPP_TRANSPORT_SIGNAL_PLATFORM_MASK)) == 0);
  chppNotifierSignal(&context->notifier,
                     signal & CHPP_TRANSPORT_SIGNAL_PLATFORM_MASK);
}

/**
 * Stops the main thread for CHPP's Transport Layer that has been started by
 * calling chppWorkThreadStart(). Stopping this thread may be necessary for
 * testing and debugging purposes.
 *
 * @param context Maintains state for each transport layer instance.
 */
void chppWorkThreadStop(struct ChppTransportState *context);

/**
 * Notifies the transport layer that the link layer is done sending the previous
 * payload (as provided to platformLinkSend() through buf and len) and can
 * accept more data.
 *
 * On systems that implement the link layer Tx asynchronously, where
 * platformLinkSend() returns False before consuming the payload provided to it
 * (i.e. buf and len), the platform implementation must call this function after
 * platformLinkSend() is done with the payload (i.e. buf and len).
 *
 * @param context Maintains state for each transport layer instance.
 * @param error Indicates success or failure type.
 */
void chppLinkSendDoneCb(struct ChppTransportState *context,
                        enum ChppLinkErrorCode error);

/**
 * Notifies the transport layer that the app layer is done with the previous
 * payload (as provided to chppAppProcessRxDatagram() through buf and len), so
 * it is freed appropriately etc.
 *
 * TODO: Look into automatically doing this when a response is sent back by a
 * service.
 *
 * @param context Maintains state for each transport layer instance.
 * @param buf Pointer to the buf given to chppAppProcessRxDatagram. Cannot be
 * null.
 */
void chppDatagramProcessDoneCb(struct ChppTransportState *context,
                               uint8_t *buf);

/**
 * Sends out transport-layer loopback data. Note that in most situations, an
 * application-layer loopback test is pprefrable as it is more thorough and
 * provides statistics regarding the correctness of the loopbacked data.
 *
 * The result will be available later, asynchronously, as a ChppAppErrorCode
 * enum in context->loopbackResult.
 *
 * @param context Maintains state for each transport layer instance.
 * @param buf Pointer to the loopback data to be sent. Cannot be null.
 * @param len Length of the loopback data.
 *
 * @return A ChppLinkErrorCode enum indicating if the transport-layer-loopback
 * request was accepted. Note that the actual test result will be available
 * later, asynchronously, in context->loopbackResult.
 */
uint8_t chppRunTransportLoopback(struct ChppTransportState *context,
                                 uint8_t *buf, size_t len);

/**
 * Sends a reset or reset-ack packet over the link in order to reset the remote
 * side or inform the counterpart of a reset, respectively. The transport
 * layer's configuration is sent as the payload of the reset packet.
 *
 * This function is typically used only internally, either immediately after
 * initialization via chppWorkThreadStart() or for subsequent resets or
 * reset-acks via chppReset(). However, implementations that do not rely on
 * chppWorkThreadStart() would require to call this function after initializing
 * CHPP.
 *
 * @param context Maintains state for each transport layer instance.
 * @param resetType Distinguishes a reset from a reset-ack, as defined in the
 * ChppTransportPacketAttributes struct.
 * @param error Provides the error that led to the reset.
 */
void chppTransportSendReset(struct ChppTransportState *context,
                            enum ChppTransportPacketAttributes resetType,
                            enum ChppTransportErrorCode error);

/**
 * Returns the Tx MTU size at the transport layer in bytes.
 *
 * This the link MTU minus the transport overhead.
 *
 * @param context Maintains state for each transport layer instance.
 */
size_t chppTransportTxMtuSize(const struct ChppTransportState *context);

/**
 * Returns the Rx MTU size at the transport layer in bytes.
 *
 * This the link MTU minus the transport overhead.
 *
 * @param context Maintains state for each transport layer instance.
 */
size_t chppTransportRxMtuSize(const struct ChppTransportState *context);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_TRANSPORT_H_
