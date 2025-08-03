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

#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <mutex>
#include <vector>

#include <android-base/thread_annotations.h>

#include "chpp/transport.h"

using ::std::literals::chrono_literals::operator""ms;

namespace chpp::test {

/**
 * Wrapper for a fake CHPP link layer which puts outgoing packets on a queue
 * where they can be extracted and inspected.
 */
class FakeLink {
 public:
  //! How long CHPP is expected to wait on an ACK for a transmitted packet
  static constexpr auto kTransportTimeout =
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::nanoseconds(CHPP_TRANSPORT_TX_TIMEOUT_NS));

  // Our default timeout covers the retry timeout, plus some extra buffer to
  // account for processing delays
  static constexpr auto kDefaultTimeout = 10 * (kTransportTimeout + 5ms);

  /**
   * Call from link send. Makes a copy of the provided buffer and
   * appends it to the TX packet queue.
   */
  void appendTxPacket(uint8_t *data, size_t len);

  //! Returns the number of TX packets waiting to be popped
  int getTxPacketCount();  // int to make EXPECT_EQ against a literal simpler
                           // with -Wsign-compare enabled

  /**
   * Wait up to the provided timeout for a packet to hit the TX queue, or return
   * immediately if a packet is already waiting to be popped.
   *
   * @return true if a packet is waiting, false on timeout
   */
  bool waitForTxPacket(std::chrono::milliseconds timeout = kDefaultTimeout);

  //! Pop and return the oldest packet on the TX queue, or assert if queue is
  //! empty
  std::vector<uint8_t> popTxPacket();

  //! Empties the TX packet queue
  void reset();

 private:
  std::mutex mMutex;
  std::condition_variable mCondVar;
  std::deque<std::vector<uint8_t>> mTxPackets GUARDED_BY(mMutex);
};

}  // namespace chpp::test