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

#include <gtest/gtest.h>

#include <stddef.h>
#include <stdint.h>
#include <thread>

#include "app_test_base.h"
#include "chpp/app.h"
#include "chpp/clients/discovery.h"
#include "chpp/clients/loopback.h"
#include "chpp/clients/timesync.h"
#include "chpp/log.h"
#include "chpp/platform/platform_link.h"
#include "chpp/transport.h"

/*
 * Test suite for the CHPP Loopback client/service
 */
namespace chpp {
namespace {

class ChppAppTest : public AppTestBase {};

TEST_F(ChppAppTest, SimpleStartStop) {
  // Simple test to make sure start/stop work threads work without crashing
  ASSERT_TRUE(mClientLinkContext.linkEstablished);
  ASSERT_TRUE(mServiceLinkContext.linkEstablished);
}

TEST_F(ChppAppTest, TransportLayerLoopback) {
  // This tests the more limited transport-layer-looopback. In contrast,
  // the regular application-layer loopback test provides a more thorough test
  // and test results.
  constexpr size_t kTestLen =
      CHPP_LINUX_LINK_TX_MTU_BYTES - CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES;
  uint8_t buf[kTestLen];
  for (size_t i = 0; i < kTestLen; i++) {
    buf[i] = (uint8_t)(i + 100);
  }

  std::this_thread::sleep_for(std::chrono::milliseconds(2000));
  CHPP_LOGI("Starting transport-layer loopback test (max buffer = %zu)...",
            kTestLen);

  EXPECT_EQ(CHPP_APP_ERROR_NONE,
            chppRunTransportLoopback(mClientAppContext.transportContext, buf,
                                     kTestLen));
  std::this_thread::sleep_for(std::chrono::milliseconds(300));
  EXPECT_EQ(CHPP_APP_ERROR_NONE,
            mClientAppContext.transportContext->loopbackResult);

  EXPECT_EQ(
      CHPP_APP_ERROR_NONE,
      chppRunTransportLoopback(mClientAppContext.transportContext, buf, 100));
  std::this_thread::sleep_for(std::chrono::milliseconds(300));
  EXPECT_EQ(CHPP_APP_ERROR_NONE,
            mClientAppContext.transportContext->loopbackResult);

  EXPECT_EQ(
      CHPP_APP_ERROR_NONE,
      chppRunTransportLoopback(mClientAppContext.transportContext, buf, 1));
  std::this_thread::sleep_for(std::chrono::milliseconds(300));
  EXPECT_EQ(CHPP_APP_ERROR_NONE,
            mClientAppContext.transportContext->loopbackResult);

  EXPECT_EQ(
      CHPP_APP_ERROR_INVALID_LENGTH,
      chppRunTransportLoopback(mClientAppContext.transportContext, buf, 0));
  std::this_thread::sleep_for(std::chrono::milliseconds(300));
  EXPECT_EQ(CHPP_APP_ERROR_INVALID_LENGTH,
            mClientAppContext.transportContext->loopbackResult);
}

TEST_F(ChppAppTest, SimpleLoopback) {
  constexpr size_t kTestLen = CHPP_LINUX_LINK_TX_MTU_BYTES -
                              CHPP_TRANSPORT_ENCODING_OVERHEAD_BYTES -
                              CHPP_LOOPBACK_HEADER_LEN;
  uint8_t buf[kTestLen];
  for (size_t i = 0; i < kTestLen; i++) {
    buf[i] = (uint8_t)(i + 100);
  }

  CHPP_LOGI(
      "Starting loopback test without fragmentation (max buffer = %zu)...",
      kTestLen);

  struct ChppLoopbackTestResult result =
      chppRunLoopbackTest(&mClientAppContext, buf, kTestLen);

  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);

  result = chppRunLoopbackTest(&mClientAppContext, buf, 10);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);

  result = chppRunLoopbackTest(&mClientAppContext, buf, 1);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);

  result = chppRunLoopbackTest(&mClientAppContext, buf, 0);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_INVALID_LENGTH);
}

TEST_F(ChppAppTest, FragmentedLoopback) {
  constexpr size_t kTestLen = UINT16_MAX;
  uint8_t buf[kTestLen];
  for (size_t i = 0; i < kTestLen; i++) {
    // Arbitrary data. A modulus of 251, a prime number, reduces the chance of
    // alignment with the MTU.
    buf[i] = (uint8_t)((i % 251) + 64);
  }

  CHPP_LOGI("Starting loopback test with fragmentation (max buffer = %zu)...",
            kTestLen);

  struct ChppLoopbackTestResult result =
      chppRunLoopbackTest(&mClientAppContext, buf, kTestLen);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);

  result = chppRunLoopbackTest(&mClientAppContext, buf, 50000);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);

  result = chppRunLoopbackTest(
      &mClientAppContext, buf,
      chppTransportTxMtuSize(mClientAppContext.transportContext) -
          CHPP_LOOPBACK_HEADER_LEN + 1);
  EXPECT_EQ(result.error, CHPP_APP_ERROR_NONE);
}

TEST_F(ChppAppTest, Timesync) {
  // Upper bound for the RTT (response received - request sent).
  constexpr uint64_t kMaxRttNs = 20 * CHPP_NSEC_PER_MSEC;
  // The offset is defined as (Time when the service sent the response) - (Time
  // when the client got the response).
  // We use half the RTT as the upper bound.
  constexpr int64_t kMaxOffsetNs = kMaxRttNs / 2;

  CHPP_LOGI("Starting timesync test...");

  std::this_thread::sleep_for(std::chrono::milliseconds(100));
  EXPECT_TRUE(chppTimesyncMeasureOffset(&mClientAppContext));
  std::this_thread::sleep_for(std::chrono::milliseconds(100));

  EXPECT_EQ(chppTimesyncGetResult(&mClientAppContext)->error,
            CHPP_APP_ERROR_NONE);

  EXPECT_LT(chppTimesyncGetResult(&mClientAppContext)->rttNs, kMaxRttNs);
  EXPECT_NE(chppTimesyncGetResult(&mClientAppContext)->rttNs, 0);

  EXPECT_LT(chppTimesyncGetResult(&mClientAppContext)->offsetNs, kMaxOffsetNs);
  EXPECT_GT(chppTimesyncGetResult(&mClientAppContext)->offsetNs, -kMaxOffsetNs);
  EXPECT_NE(chppTimesyncGetResult(&mClientAppContext)->offsetNs, 0);
}

TEST_F(ChppAppTest, DiscoveryMatched) {
  constexpr uint64_t kTimeoutMs = 5000;
  EXPECT_TRUE(chppWaitForDiscoveryComplete(&mClientAppContext, kTimeoutMs));
  EXPECT_TRUE(chppAreAllClientsMatched(&mClientAppContext));
}

}  // namespace
}  // namespace chpp
