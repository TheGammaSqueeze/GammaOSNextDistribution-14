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

#include <pthread.h>

struct CondVarTestData {
  int variable;
  pthread_mutex_t mutex;
  pthread_cond_t cond;
};

void* ThreadCondVarFunc(void* arg) {
  CondVarTestData* data = reinterpret_cast<CondVarTestData*>(arg);
  pthread_mutex_lock(&data->mutex);
  data->variable = 1;
  pthread_cond_broadcast(&data->cond);
  pthread_mutex_unlock(&data->mutex);
  return nullptr;
}

TEST(CondVar, Init) {
  pthread_condattr_t attr;
  pthread_cond_t cond;
  ASSERT_EQ(pthread_condattr_init(&attr), 0);
  ASSERT_EQ(pthread_cond_init(&cond, &attr), 0);
  ASSERT_EQ(pthread_cond_destroy(&cond), 0);
  ASSERT_EQ(pthread_condattr_destroy(&attr), 0);
}

TEST(CondVar, Synchronize) {
  pthread_t thread;
  CondVarTestData data = {0, PTHREAD_MUTEX_INITIALIZER, PTHREAD_COND_INITIALIZER};
  ASSERT_EQ(pthread_mutex_lock(&data.mutex), 0);
  // Hold mutex to ensure that broadcast is called after wait.
  ASSERT_EQ(pthread_create(&thread, nullptr, ThreadCondVarFunc, reinterpret_cast<void*>(&data)), 0);
  do {
    ASSERT_EQ(pthread_cond_wait(&data.cond, &data.mutex), 0);
  } while (data.variable == 0);
  ASSERT_EQ(pthread_mutex_unlock(&data.mutex), 0);
  ASSERT_EQ(pthread_cond_destroy(&data.cond), 0);
  ASSERT_EQ(pthread_mutex_destroy(&data.mutex), 0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
  ASSERT_EQ(data.variable, 1);
}
