/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "berberis/base/large_mmap.h"
#include "berberis/base/mmap.h"

namespace berberis {

namespace {

TEST(LargeMmap, Smoke) {
  InitLargeMmap();

  auto large_p1 = static_cast<uint8_t*>(LargeMmapOrDie(kPageSize));
  auto p1 = static_cast<uint8_t*>(MmapOrDie(kPageSize));
  auto p2 = static_cast<uint8_t*>(MmapOrDie(kPageSize));
  auto large_p2 = static_cast<uint8_t*>(LargeMmapOrDie(kPageSize));

#if defined(__LP64__)
  // We can only guarantee small mmaps are not in between of large mmaps.
  EXPECT_LT(large_p1, large_p2);
  EXPECT_TRUE(p1 < large_p1 || p1 > large_p2);
  EXPECT_TRUE(p2 < large_p1 || p2 > large_p2);
#endif

  MunmapOrDie(large_p1, kPageSize);
  MunmapOrDie(large_p2, kPageSize);
  MunmapOrDie(p1, kPageSize);
  MunmapOrDie(p2, kPageSize);
}

}  // namespace

}  // namespace berberis
