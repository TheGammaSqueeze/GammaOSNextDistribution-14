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

#include <csignal>
#include <cstddef>
#include <cstdint>

#include "berberis/base/checks.h"

#include "faulty_memory_accesses.h"

namespace berberis {

namespace {

#if defined(__i386__)
constexpr size_t kRegIP = REG_EIP;
#elif defined(__x86_64__)
constexpr size_t kRegIP = REG_RIP;
#else
#error "Unsupported arch"
#endif

void FaultHandler(int /* sig */, siginfo_t* /* info */, void* ctx) {
  ucontext_t* ucontext = reinterpret_cast<ucontext_t*>(ctx);
  static_assert(sizeof(void*) == sizeof(greg_t), "Unsupported type sizes");
  void* fault_addr = reinterpret_cast<void*>(ucontext->uc_mcontext.gregs[kRegIP]);
  void* recovery_addr = FindFaultyMemoryAccessRecoveryAddrForTesting(fault_addr);
  CHECK(recovery_addr);
  ucontext->uc_mcontext.gregs[kRegIP] = reinterpret_cast<greg_t>(recovery_addr);
}

class ScopedFaultySigaction {
 public:
  ScopedFaultySigaction() {
    struct sigaction sa;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = FaultHandler;
    CHECK_EQ(sigaction(SIGSEGV, &sa, &old_sa_), 0);
  }

  ~ScopedFaultySigaction() { CHECK_EQ(sigaction(SIGSEGV, &old_sa_, nullptr), 0); }

 private:
  struct sigaction old_sa_;
};

TEST(FaultyMemoryAccessesTest, FaultyLoadSuccess) {
  ScopedFaultySigaction scoped_sa;
  uint64_t data = 0xffff'eeee'cccc'bbaaULL;
  FaultyLoadResult result;

  result = FaultyLoad(&data, 1);
  EXPECT_EQ(result.value, static_cast<uint8_t>(data));
  EXPECT_FALSE(result.is_fault);

  result = FaultyLoad(&data, 2);
  EXPECT_EQ(result.value, static_cast<uint16_t>(data));
  EXPECT_FALSE(result.is_fault);

  result = FaultyLoad(&data, 4);
  EXPECT_EQ(result.value, static_cast<uint32_t>(data));
  EXPECT_FALSE(result.is_fault);

  result = FaultyLoad(&data, 8);
  EXPECT_EQ(result.value, data);
  EXPECT_FALSE(result.is_fault);
}

TEST(FaultyMemoryAccessesTest, FaultyLoadFault) {
  ScopedFaultySigaction scoped_sa;
  FaultyLoadResult result;

  result = FaultyLoad(nullptr, 1);
  EXPECT_TRUE(result.is_fault);
  result = FaultyLoad(nullptr, 2);
  EXPECT_TRUE(result.is_fault);
  result = FaultyLoad(nullptr, 4);
  EXPECT_TRUE(result.is_fault);
  result = FaultyLoad(nullptr, 8);
  EXPECT_TRUE(result.is_fault);
}

TEST(FaultyMemoryAccessesTest, FaultyStoreSuccess) {
  ScopedFaultySigaction scoped_sa;
  uint64_t data = 0xffff'eeee'cccc'bbaaULL;
  uint64_t storage = 0;

  bool is_fault = FaultyStore(&storage, 1, data);
  EXPECT_EQ(static_cast<uint8_t>(storage), static_cast<uint8_t>(data));
  EXPECT_FALSE(is_fault);

  is_fault = FaultyStore(&storage, 2, data);
  EXPECT_EQ(static_cast<uint16_t>(storage), static_cast<uint16_t>(data));
  EXPECT_FALSE(is_fault);

  is_fault = FaultyStore(&storage, 4, data);
  EXPECT_EQ(static_cast<uint32_t>(storage), static_cast<uint32_t>(data));
  EXPECT_FALSE(is_fault);

  is_fault = FaultyStore(&storage, 8, data);
  EXPECT_EQ(storage, data);
  EXPECT_FALSE(is_fault);
}

TEST(FaultyMemoryAccessesTest, FaultyStoreFault) {
  ScopedFaultySigaction scoped_sa;

  bool is_fault = FaultyStore(nullptr, 1, 0);
  EXPECT_TRUE(is_fault);
  is_fault = FaultyStore(nullptr, 2, 0);
  EXPECT_TRUE(is_fault);
  is_fault = FaultyStore(nullptr, 4, 0);
  EXPECT_TRUE(is_fault);
  is_fault = FaultyStore(nullptr, 8, 0);
  EXPECT_TRUE(is_fault);
}

}  // namespace

}  // namespace berberis
