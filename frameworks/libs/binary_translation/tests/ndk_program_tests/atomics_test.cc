/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <atomic>
#include <cstdint>
#include <thread>

namespace {

void LockContentionWorkLoad(std::atomic<uint32_t>* data, int data_size) {
  for (int k = 0; k < 10000; k++) {
    // Contend for the set of the atomics to make sure they are locked independently.
    for (int i = 0; i < data_size; i++) {
      data[i].fetch_add(1, std::memory_order_relaxed);
    }
  }
}

}  // namespace

TEST(Atomics, CompareAndSwap) {
  std::atomic<int> data = {0};
  int data_expected = 1;
  int data_desired = -1;
  // data != data_expected, data_expected is assigned with the value of data, data is unchanged.
  ASSERT_FALSE(data.compare_exchange_strong(data_expected, data_desired));
  ASSERT_EQ(data_expected, 0);
  ASSERT_EQ(data.load(), 0);
  // data == data_expected, data is assigned with the value of data_desired, data_expected is
  // unchanged.
  ASSERT_TRUE(data.compare_exchange_strong(data_expected, data_desired));
  ASSERT_EQ(data_expected, 0);
  ASSERT_EQ(data.load(), -1);
}

TEST(Atomics, LockContentionTest) {
  std::atomic<uint32_t> data[10];
  std::thread threads[16];
  for (int i = 0; i < 10; i++) {
    data[i].store(0, std::memory_order_relaxed);
  }
  for (int i = 0; i < 16; i++) {
    threads[i] = std::thread(LockContentionWorkLoad, data, 10);
  }
  for (int i = 0; i < 16; i++) {
    threads[i].join();
  }
  for (int i = 0; i < 10; i++) {
    ASSERT_EQ(data[i].load(std::memory_order_relaxed), 160000U);
  }
}
