/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <stdint.h>
#include <string.h>
#include <sys/mman.h>

#include <vector>

#include <gtest/gtest.h>

#include "MemoryLocalUnsafe.h"

namespace unwindstack {

TEST(MemoryLocalUnsafeTest, read_smoke) {
  std::vector<uint8_t> src(1024);
  memset(src.data(), 0x4c, 1024);

  auto local = Memory::CreateProcessMemoryLocalUnsafe();

  std::vector<uint8_t> dst(1024);
  ASSERT_TRUE(local->ReadFully(reinterpret_cast<uint64_t>(src.data()), dst.data(), 1024));
  ASSERT_EQ(0, memcmp(src.data(), dst.data(), 1024));
  for (size_t i = 0; i < 1024; i++) {
    ASSERT_EQ(0x4cU, dst[i]);
  }

  memset(src.data(), 0x23, 512);
  ASSERT_TRUE(local->ReadFully(reinterpret_cast<uint64_t>(src.data()), dst.data(), 1024));
  ASSERT_EQ(0, memcmp(src.data(), dst.data(), 1024));
  for (size_t i = 0; i < 512; i++) {
    ASSERT_EQ(0x23U, dst[i]);
  }
  for (size_t i = 512; i < 1024; i++) {
    ASSERT_EQ(0x4cU, dst[i]);
  }
}

TEST(MemoryLocalUnsafeTest, read_crash) {
  void* mapping =
      mmap(nullptr, getpagesize(), PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  ASSERT_NE(MAP_FAILED, mapping);

  mprotect(mapping, getpagesize(), PROT_NONE);

  auto local = Memory::CreateProcessMemoryLocalUnsafe();
  std::vector<uint8_t> buffer(100);
  ASSERT_DEATH(local->Read(reinterpret_cast<uint64_t>(mapping), buffer.data(), buffer.size()), "");
}

}  // namespace unwindstack
