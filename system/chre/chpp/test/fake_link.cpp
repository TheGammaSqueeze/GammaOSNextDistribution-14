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

#include "fake_link.h"

#include <cstring>

#include "chpp/log.h"
#include "packet_util.h"

namespace chpp::test {

void FakeLink::appendTxPacket(uint8_t *data, size_t len) {
  std::vector<uint8_t> pkt;
  pkt.resize(len);
  memcpy(pkt.data(), data, len);
  checkPacketValidity(pkt);
  {
    std::lock_guard<std::mutex> lock(mMutex);
    mTxPackets.emplace_back(std::move(pkt));
    mCondVar.notify_all();
  }
}

int FakeLink::getTxPacketCount() {
  std::lock_guard<std::mutex> lock(mMutex);
  return static_cast<int>(mTxPackets.size());
}

bool FakeLink::waitForTxPacket(std::chrono::milliseconds timeout) {
  std::unique_lock<std::mutex> lock(mMutex);
  auto now = std::chrono::system_clock::now();
  CHPP_LOGD("FakeLink::WaitForTxPacket waiting...");
  while (mTxPackets.empty()) {
    std::cv_status status = mCondVar.wait_until(lock, now + timeout);
    if (status == std::cv_status::timeout) {
      return false;
    }
  }
  return true;
}

std::vector<uint8_t> FakeLink::popTxPacket() {
  std::lock_guard<std::mutex> lock(mMutex);
  assert(!mTxPackets.empty());
  std::vector<uint8_t> vec = std::move(mTxPackets.back());
  mTxPackets.pop_back();
  return vec;
}

void FakeLink::reset() {
  std::lock_guard<std::mutex> lock(mMutex);
  mTxPackets.clear();
}

}  // namespace chpp::test