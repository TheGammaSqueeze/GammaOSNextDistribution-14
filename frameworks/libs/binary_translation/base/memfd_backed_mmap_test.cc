/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "berberis/base/memfd_backed_mmap.h"
#include "berberis/base/mmap.h"

namespace berberis {

namespace {

TEST(MemfdBackedMap, type_uintptr_t) {
#if defined(__LP64__)
  constexpr size_t kTableSize = 1 << 26;      // 64M mapping size
  constexpr size_t kMemfdFileSize = 1 << 24;  // 16M file size
#else
  constexpr size_t kTableSize = 1 << 14;      // 16k mapping size
  constexpr size_t kMemfdFileSize = 1 << 12;  // 4k file size
#endif

  uintptr_t default_value = 42;
  int memfd = CreateAndFillMemfd("uintptr", kMemfdFileSize, default_value);
  uintptr_t* ptr = reinterpret_cast<uintptr_t*>(
      CreateMemfdBackedMapOrDie(memfd, kTableSize * sizeof(default_value), kMemfdFileSize));
  close(memfd);

  ASSERT_EQ(ptr[3], default_value);

  ptr[3] = 0;

  ASSERT_EQ(ptr[3], 0U);
  ASSERT_EQ(ptr[73], default_value);
  ASSERT_EQ(ptr[kMemfdFileSize + 3], default_value);
  ASSERT_EQ(ptr[kMemfdFileSize + 73], default_value);
  ASSERT_EQ(ptr[3 * kMemfdFileSize + 3], default_value);

  MunmapOrDie(ptr, kTableSize * sizeof(default_value));
}

}  // namespace

}  // namespace berberis
