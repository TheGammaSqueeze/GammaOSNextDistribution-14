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

#include <thread>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/table_of_tables.h"

namespace {

TEST(TableOfTables, Smoke) {
  berberis::TableOfTables<berberis::GuestAddr, uintptr_t> tot(42);
  ASSERT_EQ(42U, tot.Get(25));
  ASSERT_EQ(1729U, *tot.Put(25, 1729));
  ASSERT_EQ(1729U, tot.Get(25));
  ASSERT_EQ(42U, tot.Get(255));
  ASSERT_EQ(42U, tot.Get((25 << 16) | 25));
}

TEST(TableOfTables, GetPointer) {
  berberis::TableOfTables<berberis::GuestAddr, uintptr_t> tot(42);
  ASSERT_EQ(42U, tot.Get(25));
  auto* addr = tot.GetPointer(25);
  ASSERT_EQ(42U, *addr);
  ASSERT_EQ(42U, tot.Get(25));
  ASSERT_EQ(1729U, *tot.Put(25, 1729));
  ASSERT_EQ(1729U, *addr);
  ASSERT_EQ(1729U, tot.Get(25));
  ASSERT_EQ(42U, tot.Get(255));
}

TEST(TableOfTables, Stress) {
  berberis::TableOfTables<berberis::GuestAddr, uintptr_t> tot(42);

  std::thread threads[64];

  for (size_t i = 0; i < 64; ++i) {
    uint32_t base_num = (i % 2 == 0) ? 0 : 65520;
    threads[i] = std::thread(
        [](berberis::TableOfTables<berberis::GuestAddr, uintptr_t>* tot, uint32_t base) {
          for (uint32_t page_num = 0; page_num < 4098; ++page_num) {
            uint32_t page = (page_num << 17);
            ASSERT_EQ(42U, tot->Get(page | (base + 4)));
            auto* addr = tot->GetPointer(page | (base + 5));
            ASSERT_EQ(42U, tot->Get(page | (base + 4)));
            ASSERT_EQ(1729U, *tot->Put(page | (base + 5), 1729));
            ASSERT_EQ(1U, *tot->Put(page | (base + 6), 1));
            ASSERT_EQ(42U, tot->Get(page | (base + 4)));
            ASSERT_EQ(1729U, *addr);
          }
        },
        &tot,
        base_num);
  }

  for (auto& thread : threads) {
    thread.join();
  }

  for (uint32_t page_num = 0; page_num < 4098; ++page_num) {
    uint32_t page = (page_num << 17);
    ASSERT_EQ(1729U, tot.Get(page | 5));
    ASSERT_EQ(1U, tot.Get(page | 6));
    ASSERT_EQ(42U, tot.Get(page | 4));
    ASSERT_EQ(42U, tot.Get(page | 255));

    ASSERT_EQ(1729U, tot.Get(page | 65525));
    ASSERT_EQ(1U, tot.Get(page | 65526));
    ASSERT_EQ(42U, tot.Get(page | 65524));
    ASSERT_EQ(42U, tot.Get(page | 65535));
  }
}

TEST(TableOfTables_DeathTest, InvalidAddress) {
#ifdef BERBERIS_GUEST_LP64
  berberis::TableOfTables<berberis::GuestAddr, uintptr_t> tot(42);

  // Try an address with its top 16 bits nonzero.
  EXPECT_DEATH((void)tot.Get(0xdeadbeef12345678ULL), "");
#endif
}

}  // namespace
