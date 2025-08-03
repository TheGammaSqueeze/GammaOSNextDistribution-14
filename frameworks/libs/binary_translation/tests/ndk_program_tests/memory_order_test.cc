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

template <typename UIntType>
void ReleaseStore(uint32_t* x, std::atomic<UIntType>* y, std::atomic<uint32_t>* thread_cnt) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 2U)
    ;

  *x = 1U;
  y->store(1U, std::memory_order_release);
}

template <typename UIntType>
void AcquireLoad(uint32_t* x,
                 std::atomic<UIntType>* y,
                 std::atomic<uint32_t>* thread_cnt,
                 bool* success) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 2U)
    ;

  UIntType y_value = 0U;
  while (!(y_value = y->load(std::memory_order_acquire)))
    ;
  *success = (*x == 1U) && (y_value == 1U);
}

template <typename UIntType>
bool ReleaseAcquireTest() {
  uint32_t x = 0U;
  std::atomic<UIntType> y = {0U};
  std::atomic<uint32_t> thread_cnt = {0U};
  bool success = false;

  // All threads wait for this counter to be sure they start more or less simultaneously, so that
  // test is more likely to observe different memory orderings.
  std::thread t1(ReleaseStore<UIntType>, &x, &y, &thread_cnt);
  std::thread t2(AcquireLoad<UIntType>, &x, &y, &thread_cnt, &success);
  t1.join();
  t2.join();

  return success;
}

void WriteX(std::atomic<uint32_t>* x, std::atomic<uint32_t>* thread_cnt) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 4U)
    ;

  x->store(1U, std::memory_order_seq_cst);
}

void WriteY(std::atomic<uint32_t>* y, std::atomic<uint32_t>* thread_cnt) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 4U)
    ;

  y->store(1U, std::memory_order_seq_cst);
}

template <typename UIntType>
void ReadXAndY(std::atomic<uint32_t>* x,
               std::atomic<uint32_t>* y,
               std::atomic<UIntType>* z,
               std::atomic<uint32_t>* thread_cnt) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 4U)
    ;

  while (!x->load(std::memory_order_seq_cst))
    ;
  if (y->load(std::memory_order_seq_cst)) {
    z->fetch_add(1u, std::memory_order_seq_cst);
  }
}

template <typename UIntType>
void ReadYAndX(std::atomic<uint32_t>* x,
               std::atomic<uint32_t>* y,
               std::atomic<UIntType>* z,
               std::atomic<uint32_t>* thread_cnt) {
  thread_cnt->fetch_add(1U);
  while (thread_cnt->load() != 4U)
    ;

  while (!y->load(std::memory_order_seq_cst))
    ;
  if (x->load(std::memory_order_seq_cst)) {
    z->fetch_add(1U, std::memory_order_seq_cst);
  }
}

template <typename UIntType>
bool SequentiallyConsistentTest() {
  std::atomic<uint32_t> x = {0U};
  std::atomic<uint32_t> y = {0U};
  std::atomic<UIntType> z = {0U};
  std::atomic<uint32_t> thread_cnt = {0U};

  // All threads wait for this counter to be sure they start more or less simultaneously, so that
  // test is more likely to observe different memory orderings.
  std::thread t1(WriteX, &x, &thread_cnt);
  std::thread t2(WriteY, &y, &thread_cnt);
  std::thread t3(ReadXAndY<UIntType>, &x, &y, &z, &thread_cnt);
  std::thread t4(ReadYAndX<UIntType>, &x, &y, &z, &thread_cnt);
  t1.join();
  t2.join();
  t3.join();
  t4.join();

  UIntType z_value = z.load();
  return (z_value == 1U) || (z_value == 2U);
}

}  // namespace

// Warning: We tried to create threads once and synchronize threads between tests through
// self-defined functions. However, we found the interpretations of atomicity and memory ordering
// related instructions are so SLOW that interpreting series of instructions for synchronization is
// MORE EXPENSIVE than creating threads.

TEST(MemoryOrder, ReleaseAcquire) {
  for (int i = 0; i < 100; i++) {
    ASSERT_TRUE(ReleaseAcquireTest<uint8_t>());
    ASSERT_TRUE(ReleaseAcquireTest<uint16_t>());
    ASSERT_TRUE(ReleaseAcquireTest<uint32_t>());
    ASSERT_TRUE(ReleaseAcquireTest<uint64_t>());
  }
}

TEST(MemoryOrder, SequentiallyConsistent) {
  for (int i = 0; i < 100; i++) {
    ASSERT_TRUE(SequentiallyConsistentTest<uint8_t>());
    ASSERT_TRUE(SequentiallyConsistentTest<uint16_t>());
    ASSERT_TRUE(SequentiallyConsistentTest<uint32_t>());
    ASSERT_TRUE(SequentiallyConsistentTest<uint64_t>());
  }
}
