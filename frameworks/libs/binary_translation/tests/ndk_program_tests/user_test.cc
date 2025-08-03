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

#include <errno.h>
#include <grp.h>
#include <sys/types.h>
#include <unistd.h>

TEST(User, getgrgid) {
  errno = 0;
  struct group* group = getgrgid(0);  // NOLINT(runtime/threadsafe_fn)
  ASSERT_TRUE(group);
  EXPECT_EQ(errno, 0);
  EXPECT_STREQ(group->gr_name, "root");
  EXPECT_STREQ(group->gr_passwd, nullptr);
  EXPECT_EQ(group->gr_gid, 0U);
  EXPECT_TRUE(group->gr_mem);
}
