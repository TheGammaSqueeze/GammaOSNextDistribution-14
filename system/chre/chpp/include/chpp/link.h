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

#ifndef CHPP_LINK_H_
#define CHPP_LINK_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Error codes used by the link layer.
 */
enum ChppLinkErrorCode {
  //! No error - data queued to be sent asynchronously
  CHPP_LINK_ERROR_NONE_QUEUED = 0,

  //! No error - data successfully sent
  CHPP_LINK_ERROR_NONE_SENT = 1,

  //! Timeout
  CHPP_LINK_ERROR_TIMEOUT = 2,

  //! Busy
  CHPP_LINK_ERROR_BUSY = 3,

  //! Out of memory
  CHPP_LINK_ERROR_OOM = 4,

  //! Link not established
  CHPP_LINK_ERROR_NO_LINK = 5,

  //! Unspecified failure
  CHPP_LINK_ERROR_UNSPECIFIED = 255
};

struct ChppTransportState;

/**
 * Link layer configuration.
 */
struct ChppLinkConfiguration {
  /**
   * Size of the TX buffer in bytes.
   * The TX buffer is provided by the link layer (@see getTxBuffer).
   *
   * The TX buffer stores the effective payload and the transport encoding
   * overhead. The size of the effective payload is txBufferLen -
   * CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES.
   */
  size_t txBufferLen;
  /**
   * Size of the RX buffer in bytes.
   *
   * The RX buffer stores the effective payload and the transport encoding
   * overhead. The size of the effective payload is rxBufferLen -
   * CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES.
   */
  size_t rxBufferLen;
};

struct ChppLinkApi {
  /**
   * Platform-specific function to initialize the link layer.
   *
   * @param linkContext Platform-specific struct with link state.
   * @param transportContext State of the associated transport layer.
   *
   * The init function typically:
   * - stores the transportContext in the link state. It is needed when calling
   *   the transport layer callbacks,
   * - initializes anything required for the link layer operations.
   *
   * It can also store the configuration in the link state when the
   * configuration differs across link instances.
   */
  void (*init)(void *linkContext, struct ChppTransportState *transportContext);

  /**
   * Platform-specific function to deinitialize the link layer (e.g. clean
   * exit).
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   */
  void (*deinit)(void *linkContext);

  /**
   * Platform-specific function to send Tx data over to the link layer.
   *
   * The TX data is located in the Tx buffer, @see getTxBuffer.
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   * @param len Length of the data to be sent in bytes.
   *
   * @return CHPP_LINK_ERROR_NONE_SENT if the platform implementation for this
   * function is synchronous, i.e. it is done with buf and len once the function
   * returns. A return value of CHPP_LINK_ERROR_NONE_QUEUED indicates that this
   * function is implemented asynchronously. In this case, it is up to the
   * platform implementation to call chppLinkSendDoneCb() after processing the
   * contents TX buffer. Otherwise, an error code is returned per enum
   * ChppLinkErrorCode.
   */
  enum ChppLinkErrorCode (*send)(void *linkContext, size_t len);

  /**
   * Platform-specific function to perform a task from the main CHPP transport
   * work thread. The task can be specified by the signal argument, which is
   * triggered by previous call[s] to chppWorkThreadSignalFromLink(). An example
   * of the type of work that can be performed is processing RX data from the
   * physical layer.
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   * @param signal The signal that describes the work to be performed. Only bits
   * specified by CHPP_TRANSPORT_SIGNAL_PLATFORM_MASK can be set.
   */
  void (*doWork)(void *linkContext, uint32_t signal);

  /**
   * Platform-specific function to reset a non-synchronous link, where the link
   * implementation is responsible for calling chppLinkSendDoneCb() after
   * processing the contents of buf and len. For such links, a reset called
   * before chppLinkSendDoneCb() indicates to the link to abort sending out buf,
   * and that the contents of buf and len will become invalid.
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   */
  void (*reset)(void *linkContext);

  /**
   * Returns the link layer configuration.
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   */
  struct ChppLinkConfiguration (*getConfig)(void *linkContext);

  /**
   * Returns a pointer to the TX buffer.
   *
   * The associated transport layer will write control bytes and payload to this
   * buffer.
   *
   * The size of the buffer must be returned in the configuration.
   * @see getConfig.
   *
   * @param linkContext Platform-specific struct with link details / parameters.
   */
  uint8_t *(*getTxBuffer)(void *linkContext);
};

#ifdef __cplusplus
}
#endif

#endif  // CHPP_LINK_H_
