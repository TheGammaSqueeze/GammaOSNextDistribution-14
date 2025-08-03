/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <string.h>
#include <cstdint>
#include <iostream>
#include <thread>
#include <type_traits>

#include "chpp/app.h"
#include "chpp/crc.h"
#include "chpp/link.h"
#include "chpp/log.h"
#include "chpp/platform/platform_link.h"
#include "chpp/transport.h"
#include "fake_link.h"
#include "packet_util.h"

using chpp::test::FakeLink;

namespace {

static void init(void *linkContext,
                 struct ChppTransportState *transportContext) {
  auto context = static_cast<struct ChppTestLinkState *>(linkContext);
  context->fake = new FakeLink();
  context->transportContext = transportContext;
}

static void deinit(void *linkContext) {
  auto context = static_cast<struct ChppTestLinkState *>(linkContext);
  auto *fake = reinterpret_cast<FakeLink *>(context->fake);
  delete fake;
}

static enum ChppLinkErrorCode send(void *linkContext, size_t len) {
  auto context = static_cast<struct ChppTestLinkState *>(linkContext);
  auto *fake = reinterpret_cast<FakeLink *>(context->fake);
  fake->appendTxPacket(&context->txBuffer[0], len);
  return CHPP_LINK_ERROR_NONE_SENT;
}

static void doWork(void * /*linkContext*/, uint32_t /*signal*/) {}

static void reset(void * /*linkContext*/) {}

struct ChppLinkConfiguration getConfig(void * /*linkContext*/) {
  return ChppLinkConfiguration{
      .txBufferLen = CHPP_TEST_LINK_TX_MTU_BYTES,
      .rxBufferLen = CHPP_TEST_LINK_RX_MTU_BYTES,
  };
}

uint8_t *getTxBuffer(void *linkContext) {
  auto context = static_cast<struct ChppTestLinkState *>(linkContext);
  return &context->txBuffer[0];
}

}  // namespace

const struct ChppLinkApi gLinkApi = {
    .init = &init,
    .deinit = &deinit,
    .send = &send,
    .doWork = &doWork,
    .reset = &reset,
    .getConfig = &getConfig,
    .getTxBuffer = &getTxBuffer,
};

namespace chpp::test {

class FakeLinkSyncTests : public testing::Test {
 protected:
  void SetUp() override {
    memset(&mLinkContext, 0, sizeof(mLinkContext));
    chppTransportInit(&mTransportContext, &mAppContext, &mLinkContext,
                      &gLinkApi);
    chppAppInitWithClientServiceSet(&mAppContext, &mTransportContext,
                                    /*clientServiceSet=*/{});
    mFakeLink = reinterpret_cast<FakeLink *>(mLinkContext.fake);

    mWorkThread = std::thread(chppWorkThreadStart, &mTransportContext);

    // Proceed to the initialized state by performing the CHPP 3-way handshake
    CHPP_LOGI("Send a RESET packet");
    ASSERT_TRUE(mFakeLink->waitForTxPacket());
    std::vector<uint8_t> resetPkt = mFakeLink->popTxPacket();
    ASSERT_TRUE(comparePacket(resetPkt, generateResetPacket()))
        << "Full packet: " << asResetPacket(resetPkt);

    CHPP_LOGI("Receive a RESET ACK packet");
    ChppResetPacket resetAck = generateResetAckPacket();
    chppRxDataCb(&mTransportContext, reinterpret_cast<uint8_t *>(&resetAck),
                 sizeof(resetAck));

    // chppProcessResetAck() results in sending a no error packet.
    CHPP_LOGI("Send CHPP_TRANSPORT_ERROR_NONE packet");
    ASSERT_TRUE(mFakeLink->waitForTxPacket());
    std::vector<uint8_t> ackPkt = mFakeLink->popTxPacket();
    ASSERT_TRUE(comparePacket(ackPkt, generateEmptyPacket()))
        << "Full packet: " << asChpp(ackPkt);
    CHPP_LOGI("CHPP handshake complete");
  }

  void TearDown() override {
    chppWorkThreadStop(&mTransportContext);
    mWorkThread.join();
    EXPECT_EQ(mFakeLink->getTxPacketCount(), 0);
  }

  void txPacket() {
    uint32_t *payload = static_cast<uint32_t *>(chppMalloc(sizeof(uint32_t)));
    *payload = 0xdeadbeef;
    bool enqueued = chppEnqueueTxDatagramOrFail(&mTransportContext, payload,
                                                sizeof(uint32_t));
    EXPECT_TRUE(enqueued);
  }

  ChppTransportState mTransportContext = {};
  ChppAppState mAppContext = {};
  ChppTestLinkState mLinkContext;
  FakeLink *mFakeLink;
  std::thread mWorkThread;
};

TEST_F(FakeLinkSyncTests, CheckRetryOnTimeout) {
  txPacket();
  ASSERT_TRUE(mFakeLink->waitForTxPacket());
  EXPECT_EQ(mFakeLink->getTxPacketCount(), 1);

  std::vector<uint8_t> pkt1 = mFakeLink->popTxPacket();

  // Not calling chppRxDataCb() will result in a timeout.
  // Ideally, to speed up the test, we'd have a mechanism to trigger
  // chppNotifierWait() to return immediately, to simulate timeout
  ASSERT_TRUE(mFakeLink->waitForTxPacket());
  EXPECT_EQ(mFakeLink->getTxPacketCount(), 1);
  std::vector<uint8_t> pkt2 = mFakeLink->popTxPacket();

  // The retry packet should be an exact match of the first one
  EXPECT_EQ(pkt1, pkt2);
}

TEST_F(FakeLinkSyncTests, NoRetryAfterAck) {
  txPacket();
  ASSERT_TRUE(mFakeLink->waitForTxPacket());
  EXPECT_EQ(mFakeLink->getTxPacketCount(), 1);

  // Generate and reply back with an ACK
  std::vector<uint8_t> pkt = mFakeLink->popTxPacket();
  ChppEmptyPacket ack = generateAck(pkt);
  chppRxDataCb(&mTransportContext, reinterpret_cast<uint8_t *>(&ack),
               sizeof(ack));

  // We shouldn't get that packet again
  EXPECT_FALSE(mFakeLink->waitForTxPacket());
}

TEST_F(FakeLinkSyncTests, MultipleNotifications) {
  constexpr int kNumPackets = 5;
  for (int i = 0; i < kNumPackets; i++) {
    txPacket();
  }

  for (int i = 0; i < kNumPackets; i++) {
    ASSERT_TRUE(mFakeLink->waitForTxPacket());

    // Generate and reply back with an ACK
    std::vector<uint8_t> pkt = mFakeLink->popTxPacket();
    ChppEmptyPacket ack = generateAck(pkt);
    chppRxDataCb(&mTransportContext, reinterpret_cast<uint8_t *>(&ack),
                 sizeof(ack));
  }

  EXPECT_FALSE(mFakeLink->waitForTxPacket());
}

}  // namespace chpp::test
