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
#include <pthread.h>
#include <semaphore.h>

TEST(Sem, SingleThread) {
  sem_t sem;
  ASSERT_EQ(sem_init(&sem, 0, 0), 0);
  ASSERT_EQ(sem_post(&sem), 0);
  int value;
  ASSERT_EQ(sem_getvalue(&sem, &value), 0);
  ASSERT_EQ(value, 1);
  ASSERT_EQ(sem_wait(&sem), 0);
  ASSERT_EQ(sem_trywait(&sem), -1);
  ASSERT_EQ(errno, EAGAIN);
  ASSERT_EQ(sem_destroy(&sem), 0);
}

static void* SeparateThread(void* arg) {
  sem_post(reinterpret_cast<sem_t*>(arg));
  return nullptr;
}

TEST(Sem, UnlockOnDifferentThread) {
  sem_t sem;
  ASSERT_EQ(sem_init(&sem, 0, 0), 0);
  pthread_t thread;
  ASSERT_EQ(pthread_create(&thread, nullptr, &SeparateThread, reinterpret_cast<void*>(&sem)), 0);
  ASSERT_EQ(sem_wait(&sem), 0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
}
