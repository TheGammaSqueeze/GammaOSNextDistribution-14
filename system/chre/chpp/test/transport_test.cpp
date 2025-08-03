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

#include "transport_test.h"

#include <gtest/gtest.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <chrono>
#include <thread>

#include "chpp/app.h"
#include "chpp/common/discovery.h"
#include "chpp/common/gnss.h"
#include "chpp/common/gnss_types.h"
#include "chpp/common/standard_uuids.h"
#include "chpp/common/wifi.h"
#include "chpp/common/wifi_types.h"
#include "chpp/common/wwan.h"
#include "chpp/crc.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/platform/platform_link.h"
#include "chpp/platform/utils.h"
#include "chpp/services/discovery.h"
#include "chpp/services/loopback.h"
#include "chpp/transport.h"
#include "chre/pal/wwan.h"

namespace {

// Preamble as separate bytes for testing
constexpr uint8_t kChppPreamble0 = 0x68;
constexpr uint8_t kChppPreamble1 = 0x43;

// Max size of payload sent to chppRxDataCb (bytes)
constexpr size_t kMaxChunkSize = 20000;

constexpr size_t kMaxPacketSize =
    kMaxChunkSize + CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES;

// Input sizes to test the entire range of sizes with a few tests
constexpr int kChunkSizes[] = {0, 1, 2, 3, 4, 21, 100, 1000, 10001, 20000};

// Number of services
constexpr int kServiceCount = 3;

// State of the link layer.
struct ChppLinuxLinkState gChppLinuxLinkContext;

/*
 * Test suite for the CHPP Transport Layer
 */
class TransportTests : public testing::TestWithParam<int> {
 protected:
  void SetUp() override {
    chppClearTotalAllocBytes();
    memset(&gChppLinuxLinkContext, 0, sizeof(struct ChppLinuxLinkState));
    gChppLinuxLinkContext.manualSendCycle = true;
    gChppLinuxLinkContext.linkEstablished = true;
    gChppLinuxLinkContext.isLinkActive = true;
    const struct ChppLinkApi *linkApi = getLinuxLinkApi();
    chppTransportInit(&mTransportContext, &mAppContext, &gChppLinuxLinkContext,
                      linkApi);
    chppAppInit(&mAppContext, &mTransportContext);

    mTransportContext.resetState = CHPP_RESET_STATE_NONE;

    // Make sure CHPP has a correct count of the number of registered services
    // on this platform as registered in the function
    // chppRegisterCommonServices().
    ASSERT_EQ(mAppContext.registeredServiceCount, kServiceCount);
  }

  void TearDown() override {
    chppAppDeinit(&mAppContext);
    chppTransportDeinit(&mTransportContext);

    EXPECT_EQ(chppGetTotalAllocBytes(), 0);
  }

  ChppTransportState mTransportContext = {};
  ChppAppState mAppContext = {};
  uint8_t mBuf[kMaxPacketSize] = {};
};

/**
 * Wait for chppTransportDoWork() to finish after it is notified by
 * chppEnqueueTxPacket to run.
 */
void WaitForTransport(struct ChppTransportState *transportContext) {
  // Start sending data out.
  cycleSendThread();
  // Wait for data to be received and processed.
  std::this_thread::sleep_for(std::chrono::milliseconds(50));

  // Should have reset loc and length for next packet / datagram
  EXPECT_EQ(transportContext->rxStatus.locInDatagram, 0);
  EXPECT_EQ(transportContext->rxDatagram.length, 0);
}

/**
 * Validates a ChppTestResponse. Since the error field within the
 * ChppAppHeader struct is optional (and not used for common services), this
 * function returns the error field to be checked if desired, depending on the
 * service.
 *
 * @param buf Buffer containing response.
 * @param ackSeq Ack sequence to be verified.
 * @param handle Handle number to be verified
 * @param transactionID Transaction ID to be verified.
 *
 * @return The error field within the ChppAppHeader struct that is used by some
 * but not all services.
 */
uint8_t validateChppTestResponse(void *buf, uint8_t ackSeq, uint8_t handle,
                                 uint8_t transactionID) {
  struct ChppTestResponse *response = (ChppTestResponse *)buf;

  // Check preamble
  EXPECT_EQ(response->preamble0, kChppPreamble0);
  EXPECT_EQ(response->preamble1, kChppPreamble1);

  // Check response transport headers
  EXPECT_EQ(response->transportHeader.packetCode, CHPP_TRANSPORT_ERROR_NONE);
  EXPECT_EQ(response->transportHeader.ackSeq, ackSeq);

  // Check response app headers
  EXPECT_EQ(response->appHeader.handle, handle);
  EXPECT_EQ(response->appHeader.type, CHPP_MESSAGE_TYPE_SERVICE_RESPONSE);
  EXPECT_EQ(response->appHeader.transaction, transactionID);

  // Return optional response error to be checked if desired
  return response->appHeader.error;
}

/**
 * Aborts a packet and validates state.
 *
 * @param transportcontext Maintains status for each transport layer instance.
 */
void endAndValidatePacket(struct ChppTransportState *transportContext) {
  chppRxPacketCompleteCb(transportContext);
  EXPECT_EQ(transportContext->rxStatus.state, CHPP_STATE_PREAMBLE);
  EXPECT_EQ(transportContext->rxStatus.locInDatagram, 0);
  EXPECT_EQ(transportContext->rxDatagram.length, 0);
}

/**
 * Adds a preamble to a certain location in a buffer, and increases the location
 * accordingly, to account for the length of the added preamble.
 *
 * @param buf Buffer.
 * @param location Location to add the preamble, which its value will be
 * increased accordingly.
 */
void addPreambleToBuf(uint8_t *buf, size_t *location) {
  buf[(*location)++] = kChppPreamble0;
  buf[(*location)++] = kChppPreamble1;
}

/**
 * Adds a transport header (with default values) to a certain location in a
 * buffer, and increases the location accordingly, to account for the length of
 * the added transport header.
 *
 * @param buf Buffer.
 * @param location Location to add the transport header, which its value will be
 * increased accordingly.
 *
 * @return Pointer to the added transport header (e.g. to modify its fields).
 */
ChppTransportHeader *addTransportHeaderToBuf(uint8_t *buf, size_t *location) {
  size_t oldLoc = *location;

  // Default values for initial, minimum size request packet
  ChppTransportHeader transHeader = {};
  transHeader.flags = CHPP_TRANSPORT_FLAG_FINISHED_DATAGRAM;
  transHeader.packetCode = CHPP_TRANSPORT_ERROR_NONE;
  transHeader.ackSeq = 1;
  transHeader.seq = 0;
  transHeader.length = sizeof(ChppAppHeader);
  transHeader.reserved = 0;

  memcpy(&buf[*location], &transHeader, sizeof(transHeader));
  *location += sizeof(transHeader);

  return (ChppTransportHeader *)&buf[oldLoc];
}

/**
 * Adds an app header (with default values) to a certain location in a buffer,
 * and increases the location accordingly, to account for the length of the
 * added app header.
 *
 * @param buf Buffer.
 * @param location Location to add the app header, which its value will be
 * increased accordingly.
 *
 * @return Pointer to the added app header (e.g. to modify its fields).
 */
ChppAppHeader *addAppHeaderToBuf(uint8_t *buf, size_t *location) {
  size_t oldLoc = *location;

  // Default values - to be updated later as necessary
  ChppAppHeader appHeader = {};
  appHeader.handle = CHPP_HANDLE_NEGOTIATED_RANGE_START;
  appHeader.type = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;
  appHeader.transaction = 0;
  appHeader.error = CHPP_APP_ERROR_NONE;
  appHeader.command = 0;

  memcpy(&buf[*location], &appHeader, sizeof(appHeader));
  *location += sizeof(appHeader);

  return (ChppAppHeader *)&buf[oldLoc];
}

/**
 * Adds a transport footer to a certain location in a buffer, and increases the
 * location accordingly, to account for the length of the added preamble.
 *
 * @param buf Buffer.
 * @param location Location to add the footer. The value of location will be
 * increased accordingly.
 *
 */
void addTransportFooterToBuf(uint8_t *buf, size_t *location) {
  uint32_t *checksum = (uint32_t *)&buf[*location];

  *checksum = chppCrc32(0, &buf[CHPP_PREAMBLE_LEN_BYTES],
                        *location - CHPP_PREAMBLE_LEN_BYTES);

  *location += sizeof(ChppTransportFooter);
}

/**
 * Opens a service and checks to make sure it was opened correctly.
 *
 * @param transportContext Transport layer context.
 * @param buf Buffer.
 * @param ackSeq Ack sequence of the packet to be sent out
 * @param seq Sequence number of the packet to be sent out.
 * @param handle Handle of the service to be opened.
 * @param transactionID Transaction ID for the open request.
 * @param command Open command.
 */
void openService(ChppTransportState *transportContext, uint8_t *buf,
                 uint8_t ackSeq, uint8_t seq, uint8_t handle,
                 uint8_t transactionID, uint16_t command) {
  size_t len = 0;

  addPreambleToBuf(buf, &len);

  ChppTransportHeader *transHeader = addTransportHeaderToBuf(buf, &len);
  transHeader->ackSeq = ackSeq;
  transHeader->seq = seq;

  ChppAppHeader *appHeader = addAppHeaderToBuf(buf, &len);
  appHeader->handle = handle;
  appHeader->transaction = transactionID;
  appHeader->command = command;

  addTransportFooterToBuf(buf, &len);

  // Send header + payload (if any) + footer
  EXPECT_TRUE(chppRxDataCb(transportContext, buf, len));

  // Check for correct state
  uint8_t nextSeq = transHeader->seq + 1;
  EXPECT_EQ(transportContext->rxStatus.expectedSeq, nextSeq);
  EXPECT_EQ(transportContext->rxStatus.state, CHPP_STATE_PREAMBLE);

  // Wait for response
  WaitForTransport(transportContext);

  // Validate common response fields
  EXPECT_EQ(validateChppTestResponse(gChppLinuxLinkContext.buf, nextSeq, handle,
                                     transactionID),
            CHPP_APP_ERROR_NONE);

  // Check response length
  EXPECT_EQ(sizeof(ChppTestResponse), CHPP_PREAMBLE_LEN_BYTES +
                                          sizeof(ChppTransportHeader) +
                                          sizeof(ChppAppHeader));
  EXPECT_EQ(transportContext->linkBufferSize,
            sizeof(ChppTestResponse) + sizeof(ChppTransportFooter));
}

/**
 * Sends a command to a service and checks for errors.
 *
 * @param transportContext Transport layer context.
 * @param buf Buffer.
 * @param ackSeq Ack sequence of the packet to be sent out
 * @param seq Sequence number of the packet to be sent out.
 * @param handle Handle of the service to be opened.
 * @param transactionID Transaction ID for the open request.
 * @param command Command to be sent.
 */
void sendCommandToService(ChppTransportState *transportContext, uint8_t *buf,
                          uint8_t ackSeq, uint8_t seq, uint8_t handle,
                          uint8_t transactionID, uint16_t command) {
  size_t len = 0;

  addPreambleToBuf(buf, &len);

  ChppTransportHeader *transHeader = addTransportHeaderToBuf(buf, &len);
  transHeader->ackSeq = ackSeq;
  transHeader->seq = seq;

  ChppAppHeader *appHeader = addAppHeaderToBuf(buf, &len);
  appHeader->handle = handle;
  appHeader->transaction = transactionID;
  appHeader->command = command;

  addTransportFooterToBuf(buf, &len);

  // Send header + payload (if any) + footer
  EXPECT_TRUE(chppRxDataCb(transportContext, buf, len));

  // Check for correct state
  uint8_t nextSeq = transHeader->seq + 1;
  EXPECT_EQ(transportContext->rxStatus.expectedSeq, nextSeq);
  EXPECT_EQ(transportContext->rxStatus.state, CHPP_STATE_PREAMBLE);

  // Wait for response
  WaitForTransport(transportContext);

  // Validate common response fields
  EXPECT_EQ(validateChppTestResponse(gChppLinuxLinkContext.buf, nextSeq, handle,
                                     transactionID),
            CHPP_APP_ERROR_NONE);
}

/**
 * A series of zeros shouldn't change state from CHPP_STATE_PREAMBLE
 */
TEST_P(TransportTests, ZeroNoPreambleInput) {
  size_t len = static_cast<size_t>(GetParam());
  if (len <= kMaxChunkSize) {
    EXPECT_TRUE(chppRxDataCb(&mTransportContext, mBuf, len));
    EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);
  }
}

/**
 * A preamble after a series of zeros input should change state from
 * CHPP_STATE_PREAMBLE to CHPP_STATE_HEADER
 */
TEST_P(TransportTests, ZeroThenPreambleInput) {
  size_t len = static_cast<size_t>(GetParam());

  if (len <= kMaxChunkSize) {
    // Add preamble at the end of mBuf, as individual bytes instead of using
    // chppAddPreamble(&mBuf[preambleLoc])
    size_t preambleLoc = MAX(0, len - CHPP_PREAMBLE_LEN_BYTES);
    mBuf[preambleLoc] = kChppPreamble0;
    mBuf[preambleLoc + 1] = kChppPreamble1;

    if (len >= CHPP_PREAMBLE_LEN_BYTES) {
      EXPECT_FALSE(chppRxDataCb(&mTransportContext, mBuf, len));
      EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_HEADER);
    } else {
      EXPECT_TRUE(chppRxDataCb(&mTransportContext, mBuf, len));
      EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);
    }
  }
}

/**
 * Rx Testing with various length payloads of zeros
 */
TEST_P(TransportTests, RxPayloadOfZeros) {
  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  size_t len = static_cast<size_t>(GetParam());
  bool isLenValid = (len <= chppTransportRxMtuSize(&mTransportContext));

  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  if (len <= kMaxChunkSize) {
    size_t loc = 0;
    addPreambleToBuf(mBuf, &loc);
    ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);

    transHeader->length = static_cast<uint16_t>(len);
    loc += len;

    addTransportFooterToBuf(mBuf, &loc);

    // Send header and check for correct state
    EXPECT_EQ(
        chppRxDataCb(&mTransportContext, mBuf,
                     CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader)),
        !isLenValid);

    if (!isLenValid) {
      EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);
    } else if (len > 0) {
      EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PAYLOAD);
    } else {
      EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_FOOTER);
    }

    // Correct decoding of packet length
    EXPECT_EQ(mTransportContext.rxHeader.length, len);
    EXPECT_EQ(mTransportContext.rxStatus.locInDatagram, 0);
    EXPECT_EQ(mTransportContext.rxDatagram.length, isLenValid ? len : 0);

    // Send payload if any and check for correct state
    if (len > 0) {
      EXPECT_EQ(
          chppRxDataCb(
              &mTransportContext,
              &mBuf[CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader)],
              len),
          !isLenValid);
      EXPECT_EQ(mTransportContext.rxStatus.state,
                isLenValid ? CHPP_STATE_FOOTER : CHPP_STATE_PREAMBLE);
    }

    // Should have complete packet payload by now
    EXPECT_EQ(mTransportContext.rxStatus.locInDatagram, isLenValid ? len : 0);

    // But no ACK yet
    EXPECT_EQ(mTransportContext.rxStatus.expectedSeq, transHeader->seq);

    // Send footer
    EXPECT_TRUE(chppRxDataCb(
        &mTransportContext,
        &mBuf[CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) + len],
        sizeof(ChppTransportFooter)));

    // The next expected packet sequence # should incremented only if the
    // received packet is payload-bearing.
    uint8_t nextSeq = transHeader->seq + ((isLenValid && len > 0) ? 1 : 0);
    EXPECT_EQ(mTransportContext.rxStatus.expectedSeq, nextSeq);

    // Check for correct ACK crafting if applicable (i.e. if the received packet
    // is payload-bearing).
    if (isLenValid && len > 0) {
      EXPECT_EQ(mTransportContext.txStatus.packetCodeToSend,
                CHPP_TRANSPORT_ERROR_NONE);
      EXPECT_EQ(mTransportContext.txDatagramQueue.pending, 0);

      WaitForTransport(&mTransportContext);

      // Check response packet fields
      struct ChppTransportHeader *txHeader =
          (struct ChppTransportHeader *)&gChppLinuxLinkContext
              .buf[CHPP_PREAMBLE_LEN_BYTES];
      EXPECT_EQ(txHeader->flags, CHPP_TRANSPORT_FLAG_FINISHED_DATAGRAM);
      EXPECT_EQ(txHeader->packetCode, CHPP_TRANSPORT_ERROR_NONE);
      EXPECT_EQ(txHeader->ackSeq, nextSeq);
      EXPECT_EQ(txHeader->length, 0);

      // Check outgoing packet length
      EXPECT_EQ(mTransportContext.linkBufferSize,
                CHPP_PREAMBLE_LEN_BYTES + sizeof(struct ChppTransportHeader) +
                    sizeof(struct ChppTransportFooter));
    }

    // Check for correct state
    EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);

    // Should have reset loc and length for next packet / datagram
    EXPECT_EQ(mTransportContext.rxStatus.locInDatagram, 0);
    EXPECT_EQ(mTransportContext.rxDatagram.length, 0);
  }

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

/**
 * End of Packet Link Notification during preamble
 */
TEST_F(TransportTests, LinkSendDonePreamble) {
  size_t payloadLen = 1000;
  size_t partLenPreamble = CHPP_PREAMBLE_LEN_BYTES - 1;

  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  size_t loc = 0;
  addPreambleToBuf(mBuf, &loc);
  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);
  transHeader->length = static_cast<uint16_t>(payloadLen);
  loc += payloadLen;
  addTransportFooterToBuf(mBuf, &loc);

  EXPECT_FALSE(chppRxDataCb(&mTransportContext, mBuf, partLenPreamble));
  EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);
  endAndValidatePacket(&mTransportContext);

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

/**
 * End of Packet Link Notification during header
 */
TEST_F(TransportTests, LinkSendDoneHeader) {
  size_t payloadLen = 1000;
  size_t partLenHeader =
      CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) - 1;

  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  size_t loc = 0;
  addPreambleToBuf(mBuf, &loc);
  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);
  transHeader->length = static_cast<uint16_t>(payloadLen);
  loc += payloadLen;
  addTransportFooterToBuf(mBuf, &loc);

  EXPECT_FALSE(chppRxDataCb(&mTransportContext, mBuf, partLenHeader));
  EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_HEADER);
  EXPECT_EQ(mTransportContext.rxHeader.length, payloadLen);
  endAndValidatePacket(&mTransportContext);

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

/**
 * End of Packet Link Notification during payload
 */
TEST_F(TransportTests, LinkSendDonePayload) {
  size_t payloadLen = 1000;
  size_t partLenPayload = 500;

  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  size_t loc = 0;
  addPreambleToBuf(mBuf, &loc);
  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);
  transHeader->length = static_cast<uint16_t>(payloadLen);
  loc += payloadLen;
  addTransportFooterToBuf(mBuf, &loc);

  EXPECT_FALSE(chppRxDataCb(&mTransportContext, mBuf, partLenPayload));
  EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PAYLOAD);
  EXPECT_EQ(mTransportContext.rxHeader.length, payloadLen);
  EXPECT_EQ(
      mTransportContext.rxStatus.locInDatagram,
      partLenPayload - CHPP_PREAMBLE_LEN_BYTES - sizeof(ChppTransportHeader));
  EXPECT_EQ(mTransportContext.rxDatagram.length, payloadLen);
  endAndValidatePacket(&mTransportContext);

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

/**
 * End of Packet Link Notification during footer
 */
TEST_F(TransportTests, LinkSendDoneFooter) {
  size_t payloadLen = 1000;
  size_t partLenFooter = CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) +
                         payloadLen + sizeof(ChppTransportFooter) - 1;

  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  size_t loc = 0;
  addPreambleToBuf(mBuf, &loc);
  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);
  transHeader->length = static_cast<uint16_t>(payloadLen);
  loc += payloadLen;
  addTransportFooterToBuf(mBuf, &loc);

  EXPECT_FALSE(chppRxDataCb(&mTransportContext, mBuf, partLenFooter));
  EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_FOOTER);
  EXPECT_EQ(mTransportContext.rxHeader.length, payloadLen);
  EXPECT_EQ(mTransportContext.rxStatus.locInDatagram, payloadLen);
  EXPECT_EQ(mTransportContext.rxDatagram.length, payloadLen);
  endAndValidatePacket(&mTransportContext);

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

TEST_P(TransportTests, EnqueueDatagrams) {
  size_t len = static_cast<size_t>(GetParam());

  if (len <= CHPP_TX_DATAGRAM_QUEUE_LEN) {
    // Add (len) datagrams of various length to queue

    int fr = 0;

    for (int j = 0; j == CHPP_TX_DATAGRAM_QUEUE_LEN; j++) {
      for (size_t i = 1; i <= len; i++) {
        uint8_t *mBuf = (uint8_t *)chppMalloc(i + 100);
        EXPECT_TRUE(
            chppEnqueueTxDatagramOrFail(&mTransportContext, mBuf, i + 100));

        EXPECT_EQ(mTransportContext.txDatagramQueue.pending, i);
        EXPECT_EQ(mTransportContext.txDatagramQueue.front, fr);
        EXPECT_EQ(mTransportContext.txDatagramQueue
                      .datagram[(i - 1 + fr) % CHPP_TX_DATAGRAM_QUEUE_LEN]
                      .length,
                  i + 100);
      }

      if (mTransportContext.txDatagramQueue.pending ==
          CHPP_TX_DATAGRAM_QUEUE_LEN) {
        uint8_t *mBuf = (uint8_t *)chppMalloc(100);
        EXPECT_FALSE(
            chppEnqueueTxDatagramOrFail(&mTransportContext, mBuf, 100));
        CHPP_FREE_AND_NULLIFY(mBuf);
      }

      for (size_t i = len; i > 0; i--) {
        fr++;
        fr %= CHPP_TX_DATAGRAM_QUEUE_LEN;

        EXPECT_TRUE(chppDequeueTxDatagram(&mTransportContext));

        EXPECT_EQ(mTransportContext.txDatagramQueue.front, fr);
        EXPECT_EQ(mTransportContext.txDatagramQueue.pending, i - 1);
      }

      EXPECT_FALSE(chppDequeueTxDatagram(&mTransportContext));

      EXPECT_EQ(mTransportContext.txDatagramQueue.front, fr);
      EXPECT_EQ(mTransportContext.txDatagramQueue.pending, 0);
    }
  }
}

/**
 * Loopback testing with various length payloads of zeros
 */
TEST_P(TransportTests, LoopbackPayloadOfZeros) {
  mTransportContext.rxStatus.state = CHPP_STATE_PREAMBLE;
  size_t len = static_cast<size_t>(GetParam());

  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  if (len > 1 && len <= kMaxChunkSize) {
    // Loopback App header (only 2 fields required)
    mBuf[0] = CHPP_HANDLE_LOOPBACK;
    mBuf[1] = CHPP_MESSAGE_TYPE_CLIENT_REQUEST;

    EXPECT_TRUE(chppDispatchLoopbackClientRequest(&mAppContext, mBuf, len));

    uint16_t end = (mTransportContext.txDatagramQueue.front +
                    mTransportContext.txDatagramQueue.pending - 1) %
                   CHPP_TX_DATAGRAM_QUEUE_LEN;

    EXPECT_EQ(mTransportContext.txDatagramQueue.datagram[end].length, len);
    EXPECT_EQ(mTransportContext.txDatagramQueue.datagram[end].payload[0],
              CHPP_HANDLE_LOOPBACK);
    EXPECT_EQ(mTransportContext.txDatagramQueue.datagram[end].payload[1],
              CHPP_MESSAGE_TYPE_SERVICE_RESPONSE);
  }
}

/**
 * Discovery service + Transaction ID
 */
TEST_P(TransportTests, DiscoveryAndTransactionID) {
  uint8_t transactionID = static_cast<uint8_t>(GetParam());
  size_t len = 0;

  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  ChppAppHeader *appHeader = addAppHeaderToBuf(mBuf, &len);
  appHeader->handle = CHPP_HANDLE_DISCOVERY;
  appHeader->transaction = transactionID;
  appHeader->command = CHPP_DISCOVERY_COMMAND_DISCOVER_ALL;

  EXPECT_TRUE(chppDispatchDiscoveryClientRequest(&mAppContext, mBuf, len));

  uint16_t end = (mTransportContext.txDatagramQueue.front +
                  mTransportContext.txDatagramQueue.pending - 1) %
                 CHPP_TX_DATAGRAM_QUEUE_LEN;

  struct ChppAppHeader *responseHeader =
      (ChppAppHeader *)mTransportContext.txDatagramQueue.datagram[end].payload;

  EXPECT_EQ(responseHeader->handle, CHPP_HANDLE_DISCOVERY);
  EXPECT_EQ(responseHeader->type, CHPP_MESSAGE_TYPE_SERVICE_RESPONSE);
  EXPECT_EQ(responseHeader->transaction, transactionID);
  EXPECT_EQ(responseHeader->error, CHPP_APP_ERROR_NONE);
  EXPECT_EQ(responseHeader->command, CHPP_DISCOVERY_COMMAND_DISCOVER_ALL);

  // Decode discovery response
  ChppServiceDescriptor *services =
      (ChppServiceDescriptor *)&mTransportContext.txDatagramQueue.datagram[end]
          .payload[sizeof(ChppAppHeader)];

  // Check total length (and implicit service count)
  EXPECT_EQ(
      mTransportContext.txDatagramQueue.datagram[end].length,
      sizeof(ChppAppHeader) + kServiceCount * sizeof(ChppServiceDescriptor));

  // Check service configuration response
  ChppServiceDescriptor wwanServiceDescriptor = {};
  static const uint8_t uuid[CHPP_SERVICE_UUID_LEN] = CHPP_UUID_WWAN_STANDARD;
  memcpy(&wwanServiceDescriptor.uuid, &uuid,
         sizeof(wwanServiceDescriptor.uuid));
  static const char name[CHPP_SERVICE_NAME_MAX_LEN] = "WWAN";
  memcpy(&wwanServiceDescriptor.name, &name,
         sizeof(wwanServiceDescriptor.name));
  wwanServiceDescriptor.version.major = 1;
  wwanServiceDescriptor.version.minor = 0;
  wwanServiceDescriptor.version.patch = 0;

  EXPECT_EQ(std::memcmp(services[0].uuid, wwanServiceDescriptor.uuid,
                        sizeof(wwanServiceDescriptor.uuid)),
            0);
  EXPECT_EQ(std::memcmp(services[0].name, wwanServiceDescriptor.name,
                        sizeof(wwanServiceDescriptor.name)),
            0);
  EXPECT_EQ(services[0].version.major, wwanServiceDescriptor.version.major);
  EXPECT_EQ(services[0].version.minor, wwanServiceDescriptor.version.minor);
  EXPECT_EQ(services[0].version.patch, wwanServiceDescriptor.version.patch);
}

/**
 * CRC-32 calculation for several pre-known test vectors.
 */
TEST_F(TransportTests, CRC32Basic) {
  static const char kTest1Str[] = "Hello World Test!";
  static const uint8_t *kTest1 = (const uint8_t *)kTest1Str;
  EXPECT_EQ(chppCrc32(0, kTest1, 17), 0x613B1D74);
  EXPECT_EQ(chppCrc32(0, kTest1, 16), 0x5F88D7D9);
  EXPECT_EQ(chppCrc32(0, kTest1, 1), 0xAA05262F);
  EXPECT_EQ(chppCrc32(0, kTest1, 0), 0x00000000);

  static const uint8_t kTest2[] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff};
  EXPECT_EQ(chppCrc32(0, kTest2, 6), 0x41D9ED00);
  EXPECT_EQ(chppCrc32(0, kTest2, 5), 0xD2FD1072);
  EXPECT_EQ(chppCrc32(0, kTest2, 4), 0xFFFFFFFF);
  EXPECT_EQ(chppCrc32(0, kTest2, 3), 0xFFFFFF00);
  EXPECT_EQ(chppCrc32(0, kTest2, 2), 0xFFFF0000);
  EXPECT_EQ(chppCrc32(0, kTest2, 1), 0xFF000000);
  EXPECT_EQ(chppCrc32(0, kTest2, 0), 0x00000000);

  static const char kTest3Str[] = "123456789";
  static const uint8_t *kTest3 = (const uint8_t *)kTest3Str;
  EXPECT_EQ(chppCrc32(0, kTest3, 9), 0xCBF43926);

  static const uint8_t kTest4[] = {0x00, 0x00, 0x00, 0x00};
  EXPECT_EQ(chppCrc32(0, kTest4, sizeof(kTest4)), 0x2144DF1C);

  static const uint8_t kTest5[] = {0xF2, 0x01, 0x83};
  EXPECT_EQ(chppCrc32(0, kTest5, sizeof(kTest5)), 0x24AB9D77);

  static const uint8_t kTest6[] = {0x0F, 0xAA, 0x00, 0x55};
  EXPECT_EQ(chppCrc32(0, kTest6, sizeof(kTest6)), 0xB6C9B287);

  static const uint8_t kTest7[] = {0x00, 0xFF, 0x55, 0x11};
  EXPECT_EQ(chppCrc32(0, kTest7, sizeof(kTest7)), 0x32A06212);

  static const uint8_t kTest8[] = {0x33, 0x22, 0x55, 0xAA, 0xBB,
                                   0xCC, 0xDD, 0xEE, 0xFF};
  EXPECT_EQ(chppCrc32(0, kTest8, sizeof(kTest8)), 0xB0AE863D);

  static const uint8_t kTest9[] = {0x92, 0x6B, 0x55};
  EXPECT_EQ(chppCrc32(0, kTest9, sizeof(kTest9)), 0x9CDEA29B);
}

/**
 * CRC-32 calculation for daisy-chained input.
 */
TEST_F(TransportTests, CRC32DaisyChained) {
  static const size_t kMaxLen = 10000;
  uint8_t test[kMaxLen];
  // Populate test with 8-bit LFSR
  // Feedback polynomial is x^8 + x^6 + x^5 + x^4 + 1
  static uint8_t lfsr = 1;
  for (size_t i = 0; i < kMaxLen; i++) {
    test[i] = lfsr;
    lfsr = (lfsr >> 1) |
           (((lfsr << 7) ^ (lfsr << 5) ^ (lfsr << 4) ^ (lfsr << 3)) & 0x80);
  }

  for (size_t len = 0; len < kMaxLen; len += 1000) {
    uint32_t fullCRC = chppCrc32(0, &test[0], len);
    for (size_t partition = 0; partition <= len; partition++) {
      uint32_t partialCRC = chppCrc32(0, &test[0], partition);
      EXPECT_EQ(chppCrc32(partialCRC, &test[partition], len - partition),
                fullCRC);
    }
  }
}

/**
 * WWAN service Open and GetCapabilities.
 */
TEST_F(TransportTests, WwanOpen) {
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  uint8_t ackSeq = 1;
  uint8_t seq = 0;
  uint8_t handle = CHPP_HANDLE_NEGOTIATED_RANGE_START;
  uint8_t transactionID = 0;
  size_t len = 0;

  openService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
              transactionID++, CHPP_WWAN_OPEN);

  addPreambleToBuf(mBuf, &len);

  uint16_t command = CHPP_WWAN_GET_CAPABILITIES;
  sendCommandToService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
                       transactionID++, command);

  size_t responseLoc = sizeof(ChppTestResponse);

  // Validate capabilities
  uint32_t *capabilities = (uint32_t *)&gChppLinuxLinkContext.buf[responseLoc];
  responseLoc += sizeof(uint32_t);

  // Cleanup
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  uint32_t capabilitySet = CHRE_WWAN_GET_CELL_INFO;
  EXPECT_EQ((*capabilities) & ~(capabilitySet), 0);

  // Check total length
  EXPECT_EQ(responseLoc, CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) +
                             sizeof(ChppWwanGetCapabilitiesResponse));
}

/**
 * WiFi service Open and GetCapabilities.
 */
TEST_F(TransportTests, WifiOpen) {
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  uint8_t ackSeq = 1;
  uint8_t seq = 0;
  uint8_t handle = CHPP_HANDLE_NEGOTIATED_RANGE_START + 1;
  uint8_t transactionID = 0;

  openService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
              transactionID++, CHPP_WIFI_OPEN);

  uint16_t command = CHPP_WIFI_GET_CAPABILITIES;
  sendCommandToService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
                       transactionID++, command);

  size_t responseLoc = sizeof(ChppTestResponse);

  // Cleanup
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  // Validate capabilities
  uint32_t *capabilities = (uint32_t *)&gChppLinuxLinkContext.buf[responseLoc];
  responseLoc += sizeof(uint32_t);

  uint32_t capabilitySet = CHRE_WIFI_CAPABILITIES_SCAN_MONITORING |
                           CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN |
                           CHRE_WIFI_CAPABILITIES_RADIO_CHAIN_PREF |
                           CHRE_WIFI_CAPABILITIES_RTT_RANGING |
                           CHRE_WIFI_CAPABILITIES_NAN_SUB;
  EXPECT_EQ((*capabilities) & ~(capabilitySet), 0);

  // Check total length
  EXPECT_EQ(responseLoc, CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) +
                             sizeof(ChppWwanGetCapabilitiesResponse));
}

/**
 * GNSS service Open and GetCapabilities.
 */
TEST_F(TransportTests, GnssOpen) {
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  uint8_t ackSeq = 1;
  uint8_t seq = 0;
  uint8_t handle = CHPP_HANDLE_NEGOTIATED_RANGE_START + 2;
  uint8_t transactionID = 0;
  size_t len = 0;

  openService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
              transactionID++, CHPP_GNSS_OPEN);

  addPreambleToBuf(mBuf, &len);

  uint16_t command = CHPP_GNSS_GET_CAPABILITIES;
  sendCommandToService(&mTransportContext, mBuf, ackSeq++, seq++, handle,
                       transactionID++, command);

  size_t responseLoc = sizeof(ChppTestResponse);

  // Cleanup
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  // Validate capabilities
  uint32_t *capabilities = (uint32_t *)&gChppLinuxLinkContext.buf[responseLoc];
  responseLoc += sizeof(uint32_t);

  uint32_t capabilitySet =
      CHRE_GNSS_CAPABILITIES_LOCATION | CHRE_GNSS_CAPABILITIES_MEASUREMENTS |
      CHRE_GNSS_CAPABILITIES_GNSS_ENGINE_BASED_PASSIVE_LISTENER;
  EXPECT_EQ((*capabilities) & ~(capabilitySet), 0);

  // Check total length
  EXPECT_EQ(responseLoc, CHPP_PREAMBLE_LEN_BYTES + sizeof(ChppTransportHeader) +
                             sizeof(ChppGnssGetCapabilitiesResponse));
}

/**
 * Discovery client.
 */
TEST_F(TransportTests, Discovery) {
  size_t len = 0;

  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  addPreambleToBuf(mBuf, &len);

  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &len);

  ChppAppHeader *appHeader = addAppHeaderToBuf(mBuf, &len);
  appHeader->handle = CHPP_HANDLE_DISCOVERY;
  appHeader->command = CHPP_DISCOVERY_COMMAND_DISCOVER_ALL;
  appHeader->type = CHPP_MESSAGE_TYPE_SERVICE_RESPONSE;

  ChppServiceDescriptor wwanServiceDescriptor = {};
  static const uint8_t uuid[CHPP_SERVICE_UUID_LEN] = CHPP_UUID_WWAN_STANDARD;
  memcpy(&wwanServiceDescriptor.uuid, &uuid,
         sizeof(wwanServiceDescriptor.uuid));
  static const char name[CHPP_SERVICE_NAME_MAX_LEN] = "WWAN";
  memcpy(&wwanServiceDescriptor.name, &name,
         sizeof(wwanServiceDescriptor.name));
  wwanServiceDescriptor.version.major = 1;
  wwanServiceDescriptor.version.minor = 0;
  wwanServiceDescriptor.version.patch = 0;

  memcpy(&mBuf[len], &wwanServiceDescriptor, sizeof(ChppServiceDescriptor));
  len += sizeof(ChppServiceDescriptor);

  transHeader->length = static_cast<uint16_t>(
      len - sizeof(ChppTransportHeader) - CHPP_PREAMBLE_LEN_BYTES);

  addTransportFooterToBuf(mBuf, &len);

  // Initialize clientIndexOfServiceIndex[0] to see if it correctly updated
  // upon discovery
  mAppContext.clientIndexOfServiceIndex[0] = CHPP_CLIENT_INDEX_NONE;

  // Send header + payload (if any) + footer
  EXPECT_TRUE(chppRxDataCb(&mTransportContext, mBuf, len));

  // Cleanup
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  // Check for correct state
  EXPECT_EQ(mAppContext.clientIndexOfServiceIndex[0], 0);
  uint8_t nextSeq = transHeader->seq + 1;
  EXPECT_EQ(mTransportContext.rxStatus.expectedSeq, nextSeq);
  EXPECT_EQ(mTransportContext.rxStatus.state, CHPP_STATE_PREAMBLE);
}

/**
 * Unopened clients should not crash upon an unsolicitated service response.
 */
TEST_F(TransportTests, UnopenedClient) {
  size_t len = 0;
  uint8_t *buf = (uint8_t *)chppMalloc(100);

  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);
  chppWorkThreadStop(&mTransportContext);
  t1.join();

  ChppAppHeader *appHeader = addAppHeaderToBuf(buf, &len);
  appHeader->handle = CHPP_HANDLE_NEGOTIATED_RANGE_START + 1;
  appHeader->command = CHPP_WIFI_CONFIGURE_SCAN_MONITOR_ASYNC;
  appHeader->type = CHPP_MESSAGE_TYPE_SERVICE_RESPONSE;
  len = sizeof(struct ChppWifiConfigureScanMonitorAsyncResponse);

  ASSERT_EQ(mAppContext.registeredServiceCount, kServiceCount);

  chppAppProcessRxDatagram(&mAppContext, buf, len);

  EXPECT_EQ(mTransportContext.txStatus.packetCodeToSend,
            CHPP_TRANSPORT_ERROR_APPLAYER);
}

TEST_F(TransportTests, DiscardedPacketTest) {
  mTransportContext.txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, &mTransportContext);
  WaitForTransport(&mTransportContext);

  // Send packet to RX thread after manually setting to resetting state.
  // We expect this packet to get dropped, but this test checks for any
  // problematic behavior (e.g. memory leaks).
  mTransportContext.resetState = CHPP_RESET_STATE_RESETTING;

  size_t loc = 0;
  addPreambleToBuf(mBuf, &loc);
  ChppTransportHeader *transHeader = addTransportHeaderToBuf(mBuf, &loc);

  ChppAppHeader *appHeader = addAppHeaderToBuf(mBuf, &loc);
  appHeader->handle = CHPP_HANDLE_DISCOVERY;
  appHeader->command = CHPP_DISCOVERY_COMMAND_DISCOVER_ALL;
  appHeader->type = CHPP_MESSAGE_TYPE_SERVICE_RESPONSE;

  ChppServiceDescriptor wwanServiceDescriptor = {};
  static const uint8_t uuid[CHPP_SERVICE_UUID_LEN] = CHPP_UUID_WWAN_STANDARD;
  memcpy(&wwanServiceDescriptor.uuid, &uuid,
         sizeof(wwanServiceDescriptor.uuid));
  static const char name[CHPP_SERVICE_NAME_MAX_LEN] = "WWAN";
  memcpy(&wwanServiceDescriptor.name, &name,
         sizeof(wwanServiceDescriptor.name));
  wwanServiceDescriptor.version.major = 1;
  wwanServiceDescriptor.version.minor = 0;
  wwanServiceDescriptor.version.patch = 0;

  memcpy(&mBuf[loc], &wwanServiceDescriptor, sizeof(ChppServiceDescriptor));
  loc += sizeof(ChppServiceDescriptor);

  transHeader->length = static_cast<uint16_t>(
      loc - sizeof(ChppTransportHeader) - CHPP_PREAMBLE_LEN_BYTES);

  addTransportFooterToBuf(mBuf, &loc);

  mAppContext.clientIndexOfServiceIndex[0] = CHPP_CLIENT_INDEX_NONE;

  EXPECT_TRUE(chppRxDataCb(&mTransportContext, mBuf, loc));

  chppWorkThreadStop(&mTransportContext);
  t1.join();
}

/*
 * Correctly handle messages directed to clients / services with an invalid
 * handle number.
 */
void messageToInvalidHandle(ChppTransportState *transportContext,
                            uint8_t type) {
  size_t len = 0;
  uint8_t *buf = (uint8_t *)chppMalloc(100);

  transportContext->txStatus.hasPacketsToSend = true;
  std::thread t1(chppWorkThreadStart, transportContext);
  WaitForTransport(transportContext);
  chppWorkThreadStop(transportContext);
  t1.join();

  ChppAppHeader *appHeader = addAppHeaderToBuf(buf, &len);
  appHeader->handle =
      CHPP_HANDLE_NEGOTIATED_RANGE_START + CHPP_MAX_REGISTERED_CLIENTS;
  appHeader->type = type;
  len = sizeof(struct ChppAppHeader);

  chppAppProcessRxDatagram(transportContext->appContext, buf, len);

  EXPECT_EQ(transportContext->txStatus.packetCodeToSend,
            CHPP_TRANSPORT_ERROR_APPLAYER);
}

TEST_F(TransportTests, RequestToInvalidService) {
  messageToInvalidHandle(&mTransportContext, CHPP_MESSAGE_TYPE_CLIENT_REQUEST);
}

TEST_F(TransportTests, ResponseToInvalidClient) {
  messageToInvalidHandle(&mTransportContext,
                         CHPP_MESSAGE_TYPE_SERVICE_RESPONSE);
}

TEST_F(TransportTests, NotificationToInvalidService) {
  messageToInvalidHandle(&mTransportContext,
                         CHPP_MESSAGE_TYPE_CLIENT_NOTIFICATION);
}

TEST_F(TransportTests, NotificationToInvalidClient) {
  messageToInvalidHandle(&mTransportContext,
                         CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION);
}

TEST_F(TransportTests, WorkMonitorInvoked) {
  // Send message to spin work thread so it interacts with the work monitor
  messageToInvalidHandle(&mTransportContext,
                         CHPP_MESSAGE_TYPE_SERVICE_NOTIFICATION);

  // 1 pre/post call for executing the work and 1 for shutting down the thread.
  EXPECT_EQ(mTransportContext.workMonitor.numPreProcessCalls, 2);
  EXPECT_EQ(mTransportContext.workMonitor.numPostProcessCalls, 2);
}

INSTANTIATE_TEST_SUITE_P(TransportTestRange, TransportTests,
                         testing::ValuesIn(kChunkSizes));
}  // namespace
