/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <pthread.h>

namespace {

// Test that host pthread_exit runs destructors of local objects.
// This should happen at pthread_cleanup (before pthread_specific destructors).

int g_count = 0;

struct ScopedCount {
  ScopedCount() { ++g_count; }
  ~ScopedCount() { --g_count; }
};

void RunPthreadExit() {
  ASSERT_EQ(1, g_count);
  pthread_exit(nullptr);
  FAIL();
}

void* ThreadFunc(void* /* arg */) {
  ScopedCount c;
  RunPthreadExit();  // does not return
  return nullptr;
}

TEST(GuestThreadTest, PthreadExitRunsLocalDtors) {
  ASSERT_EQ(0, g_count);
  pthread_t thread;
  ASSERT_EQ(0, pthread_create(&thread, nullptr, ThreadFunc, nullptr));
  ASSERT_EQ(0, pthread_join(thread, nullptr));
  // TODO(b/27860783): it turned out that on bionic pthread_exit doesn't run
  // destructors for local objects. If that gets fixed, change the code
  // accordingly (see other TODOs for this bug).
  // ASSERT_EQ(0, g_count);
  ASSERT_EQ(1, g_count);
}

}  // namespace
