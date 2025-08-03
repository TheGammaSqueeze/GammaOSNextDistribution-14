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

#include <chrono>  // chrono_literals::operator""ms
#include <string>
#include <thread>  // this_thread::sleep_for

#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/runtime_library.h"  // kEntry*
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

namespace {

using std::chrono_literals::operator""ms;

TEST(TranslationCacheTest, DefaultNotTranslated) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  EXPECT_EQ(tc.GetHostCodePtr(pc)->load(), kEntryNotTranslated);
  EXPECT_EQ(tc.GetHostCodePtr(pc + 1024)->load(), kEntryNotTranslated);
  EXPECT_EQ(tc.GetInvocationCounter(pc), 0U);
}

TEST(TranslationCacheTest, UpdateInvocationCounter) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  // Create entry
  GuestCodeEntry* entry = tc.AddAndLockForTranslation(pc, 0);
  ASSERT_TRUE(entry);
  EXPECT_EQ(entry->invocation_counter, 0U);
  entry->invocation_counter = 42;
  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kSpecialHandler, {kEntryNoExec, 0});

  EXPECT_EQ(tc.GetInvocationCounter(pc), 42U);
}

TEST(TranslationCacheTest, AddAndLockForTranslation) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  // Cannot lock if counter is below the threshold, but entry is created anyway.
  ASSERT_FALSE(tc.AddAndLockForTranslation(pc, 1));
  GuestCodeEntry* entry = tc.LookupGuestCodeEntryUnsafeForTesting(pc);
  ASSERT_TRUE(entry);
  EXPECT_EQ(tc.GetHostCodePtr(pc)->load(), kEntryNotTranslated);
  EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kInterpreted);
  EXPECT_EQ(tc.GetInvocationCounter(pc), 1U);

  // Lock when counter is equal or above the threshold.
  entry = tc.AddAndLockForTranslation(pc, 1);
  ASSERT_TRUE(entry);
  EXPECT_EQ(tc.GetHostCodePtr(pc)->load(), kEntryTranslating);
  EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);
  EXPECT_EQ(tc.GetInvocationCounter(pc), 1U);

  // Cannot lock locked.
  ASSERT_FALSE(tc.AddAndLockForTranslation(pc, 0));

  // Unlock.
  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kSpecialHandler, {kEntryNoExec, 0});
  EXPECT_EQ(tc.GetHostCodePtr(pc)->load(), kEntryNoExec);
  EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kSpecialHandler);

  // Cannot lock translated.
  ASSERT_FALSE(tc.AddAndLockForTranslation(pc, 0));
}

constexpr bool kWrappedHostFunc = true;

TEST(TranslationCacheTest, AddAndLockForWrapping) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  // Add and lock nonexistent.
  GuestCodeEntry* entry = tc.AddAndLockForWrapping(pc);
  ASSERT_TRUE(entry);
  ASSERT_EQ(kEntryWrapping, tc.GetHostCodePtr(pc)->load());
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);

  // Cannot lock locked.
  ASSERT_FALSE(tc.AddAndLockForWrapping(pc));

  // Unlock.
  tc.SetWrappedAndUnlock(pc, entry, kWrappedHostFunc, {kEntryNoExec, 0});
  ASSERT_EQ(kEntryNoExec, tc.GetHostCodePtr(pc)->load());
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kHostWrapped);

  // Cannot lock wrapped.
  ASSERT_FALSE(tc.AddAndLockForWrapping(pc));

  // Cannot lock not translated but already interpreted.
  ASSERT_FALSE(tc.AddAndLockForTranslation(pc + 64, 1));
  entry = tc.LookupGuestCodeEntryUnsafeForTesting(pc + 64);
  ASSERT_TRUE(entry);
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kInterpreted);
  ASSERT_FALSE(tc.AddAndLockForWrapping(pc + 64));
}

HostCode kHostCodeStub = AsHostCode(0xdeadbeef);

void TestWrappingWorker(TranslationCache* tc, GuestAddr pc) {
  while (true) {
    GuestCodeEntry* entry = tc->AddAndLockForWrapping(pc);
    if (entry) {
      EXPECT_EQ(entry->host_code->load(), kEntryWrapping);
      EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);
      // Give other threads some time to run the loop. Typical test
      // time is 1 ms, make sleep order of magnitude longer - 10ms.
      std::this_thread::sleep_for(10ms);
      tc->SetWrappedAndUnlock(pc, entry, kWrappedHostFunc, {kHostCodeStub, 0});
      EXPECT_EQ(entry->host_code->load(), kHostCodeStub);
      EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kHostWrapped);
      return;
    }

    auto host_code = tc->GetHostCodePtr(pc)->load();

    // Warning: the order of comparisons here is
    // important since code can change in between.
    if (host_code == kEntryWrapping) {
      continue;
    }

    EXPECT_EQ(host_code, kHostCodeStub);
    break;
  }

  return;
}

void TestTranslationWorker(TranslationCache* tc, GuestAddr pc) {
  while (true) {
    GuestCodeEntry* entry = tc->AddAndLockForTranslation(pc, 0);
    if (entry) {
      EXPECT_EQ(entry->host_code->load(), kEntryTranslating);
      EXPECT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);
      // Give other threads some time to run the loop. Typical test
      // time is 1 ms, make sleep order of magnitude longer - 10ms.
      std::this_thread::sleep_for(10ms);
      tc->SetTranslatedAndUnlock(
          pc, entry, 1, GuestCodeEntry::Kind::kSpecialHandler, {kHostCodeStub, 0});
      EXPECT_EQ(entry->host_code->load(), kHostCodeStub);
      return;
    }

    auto host_code = tc->GetHostCodePtr(pc)->load();
    if (host_code == kEntryTranslating) {
      continue;
    }
    EXPECT_EQ(host_code, kHostCodeStub);
    break;
  }

  return;
}

template <void(WorkerFunc)(TranslationCache*, GuestAddr)>
void TranslationCacheTestRunThreads() {
  TranslationCache tc;
  constexpr uint32_t kNumThreads = 16;
  std::array<std::thread, kNumThreads> threads;

  // First test situation, when every thread has its own pc.
  for (uint32_t i = 0; i < kNumThreads; i++) {
    threads[i] = std::thread(WorkerFunc, &tc, i);
  }

  for (auto& thread : threads) {
    thread.join();
  }

  // Now introduce heavy contention.
  GuestAddr pc = 0x12345678;
  for (auto& thread : threads) {
    thread = std::thread(WorkerFunc, &tc, pc);
  }

  for (auto& thread : threads) {
    thread.join();
  }
}

TEST(TranslationCacheTest, InvalidateNotTranslated) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());

  tc.InvalidateGuestRange(pc, pc + 1);

  // Not translated stays not translated
  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());
  ASSERT_FALSE(tc.LookupGuestCodeEntryUnsafeForTesting(pc));
}

TEST(TranslationCacheTest, InvalidateTranslated) {
  constexpr GuestAddr pc = 0x12345678;
  const auto host_code = AsHostCode(0xdeadbeef);

  TranslationCache tc;

  GuestCodeEntry* entry = tc.AddAndLockForTranslation(pc, 0);
  ASSERT_TRUE(entry);
  ASSERT_EQ(kEntryTranslating, tc.GetHostCodePtr(pc)->load());

  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kHeavyOptimized, {host_code, 4});
  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc)->load());

  tc.InvalidateGuestRange(pc, pc + 1);

  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());
  ASSERT_FALSE(tc.LookupGuestCodeEntryUnsafeForTesting(pc));
}

TEST(TranslationCacheTest, InvalidateTranslating) {
  constexpr GuestAddr pc = 0x12345678;
  const auto host_code = AsHostCode(0xdeadbeef);

  TranslationCache tc;

  GuestCodeEntry* entry = tc.AddAndLockForTranslation(pc, 0);
  ASSERT_TRUE(entry);
  ASSERT_EQ(kEntryTranslating, tc.GetHostCodePtr(pc)->load());

  tc.InvalidateGuestRange(pc, pc + 1);
  ASSERT_EQ(kEntryInvalidating, tc.GetHostCodePtr(pc)->load());
  entry = tc.LookupGuestCodeEntryUnsafeForTesting(pc);
  ASSERT_TRUE(entry);
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);

  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kSpecialHandler, {host_code, 4});
  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());
  ASSERT_FALSE(tc.LookupGuestCodeEntryUnsafeForTesting(pc));
}

TEST(TranslationCacheTest, InvalidateTranslatingOutOfRange) {
  constexpr GuestAddr pc = 0x12345678;
  const auto host_code = AsHostCode(0xdeadbeef);

  TranslationCache tc;

  GuestCodeEntry* entry = tc.AddAndLockForTranslation(pc, 0);
  ASSERT_TRUE(entry);
  ASSERT_EQ(kEntryTranslating, tc.GetHostCodePtr(pc)->load());

  // Invalidate range that does *not* contain translating address.
  // The entry should still be invalidated, as translated region is only known after translation,
  // and it might overlap with the invalidated range.
  tc.InvalidateGuestRange(pc + 100, pc + 101);
  ASSERT_EQ(kEntryInvalidating, tc.GetHostCodePtr(pc)->load());

  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kSpecialHandler, {host_code, 4});
  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());
}

bool Translate(TranslationCache* tc, GuestAddr pc, uint32_t size, HostCode host_code) {
  GuestCodeEntry* entry = tc->AddAndLockForTranslation(pc, 0);
  if (!entry) {
    return false;
  }
  tc->SetTranslatedAndUnlock(
      pc, entry, size, GuestCodeEntry::Kind::kSpecialHandler, {host_code, 4});
  return true;
}

TEST(TranslationCacheTest, LockForGearUpTranslation) {
  constexpr GuestAddr pc = 0x12345678;
  const auto host_code = AsHostCode(0xdeadbeef);

  TranslationCache tc;

  // Cannot lock if not yet added.
  ASSERT_FALSE(tc.LockForGearUpTranslation(pc));

  ASSERT_TRUE(Translate(&tc, pc + 0, 1, host_code));
  GuestCodeEntry* entry = tc.LookupGuestCodeEntryUnsafeForTesting(pc);
  ASSERT_TRUE(entry);
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kSpecialHandler);

  // Cannot lock if kind is not kLightTranslated.
  ASSERT_FALSE(tc.LockForGearUpTranslation(pc));

  entry->kind = GuestCodeEntry::Kind::kLightTranslated;

  entry = tc.LockForGearUpTranslation(pc);
  ASSERT_TRUE(entry);
  ASSERT_EQ(kEntryTranslating, tc.GetHostCodePtr(pc)->load());
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kUnderProcessing);

  // Unlock.
  tc.SetTranslatedAndUnlock(pc, entry, 1, GuestCodeEntry::Kind::kHeavyOptimized, {kEntryNoExec, 0});
  ASSERT_EQ(kEntryNoExec, tc.GetHostCodePtr(pc)->load());
  ASSERT_EQ(entry->kind, GuestCodeEntry::Kind::kHeavyOptimized);

  // Cannot lock translated.
  ASSERT_FALSE(tc.AddAndLockForTranslation(pc, 0));
}

TEST(TranslationCacheTest, InvalidateRange) {
  constexpr GuestAddr pc = 0x12345678;
  const auto host_code = AsHostCode(0xdeadbeef);

  TranslationCache tc;

  ASSERT_TRUE(Translate(&tc, pc + 0, 1, host_code));
  ASSERT_TRUE(Translate(&tc, pc + 1, 1, host_code));
  ASSERT_TRUE(Translate(&tc, pc + 2, 1, host_code));

  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc + 0)->load());
  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc + 1)->load());
  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc + 2)->load());

  tc.InvalidateGuestRange(pc + 1, pc + 2);

  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc + 0)->load());
  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc + 1)->load());
  ASSERT_EQ(host_code, tc.GetHostCodePtr(pc + 2)->load());
}

bool Wrap(TranslationCache* tc, GuestAddr pc, HostCode host_code) {
  GuestCodeEntry* entry = tc->AddAndLockForWrapping(pc);
  if (!entry) {
    return false;
  }
  tc->SetWrappedAndUnlock(pc, entry, kWrappedHostFunc, {host_code, 0});
  return true;
}

TEST(TranslationCacheTest, InvalidateWrapped) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  ASSERT_TRUE(Wrap(&tc, pc, kEntryNoExec));

  tc.InvalidateGuestRange(pc, pc + 1);

  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());
}

TEST(TranslationCacheTest, InvalidateWrappingWrap) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  GuestCodeEntry* entry = tc.AddAndLockForWrapping(pc);
  ASSERT_TRUE(entry);

  tc.InvalidateGuestRange(pc, pc + 1);
  ASSERT_EQ(kEntryInvalidating, tc.GetHostCodePtr(pc)->load());

  tc.SetWrappedAndUnlock(pc, entry, kWrappedHostFunc, {kEntryNoExec, 0});
  ASSERT_EQ(kEntryNotTranslated, tc.GetHostCodePtr(pc)->load());

  ASSERT_TRUE(Wrap(&tc, pc, kEntryNoExec));
}

TEST(TranslationCacheTest, WrapInvalidateWrap) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  ASSERT_TRUE(Wrap(&tc, pc, kEntryNoExec));

  tc.InvalidateGuestRange(pc, pc + 1);

  ASSERT_TRUE(Wrap(&tc, pc, kEntryNoExec));
}

TEST(TranslationCacheTest, WrapInvalidateTranslate) {
  constexpr GuestAddr pc = 0x12345678;

  TranslationCache tc;

  ASSERT_TRUE(Wrap(&tc, pc, kEntryNoExec));

  tc.InvalidateGuestRange(pc, pc + 1);

  ASSERT_TRUE(Translate(&tc, pc, 1, kEntryNoExec));
}

TEST(NdkTest, TranslationCacheWrappingStatesTest) {
  TranslationCacheTestRunThreads<TestWrappingWorker>();
}

TEST(NdkTest, TranslationCacheTranslationStatesTest) {
  TranslationCacheTestRunThreads<TestTranslationWorker>();
}

}  // namespace

}  // namespace berberis
