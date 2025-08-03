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

TEST(Mutex, Init) {
  pthread_mutexattr_t attr;
  pthread_mutex_t mutex;
  ASSERT_EQ(pthread_mutexattr_init(&attr), 0);
  ASSERT_EQ(pthread_mutex_init(&mutex, &attr), 0);
  ASSERT_EQ(pthread_mutex_destroy(&mutex), 0);
  ASSERT_EQ(pthread_mutexattr_destroy(&attr), 0);
}

TEST(Mutex, Lock) {
  pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
  ASSERT_EQ(pthread_mutex_lock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_trylock(&mutex), EBUSY);
  ASSERT_EQ(pthread_mutex_unlock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_destroy(&mutex), 0);
}

TEST(Mutex, RecursiveLock) {
  // The proper name for that define is with _NP (_NP means non-portable), but old versions of
  // Bionic use a version without the _NP suffix.
#ifdef PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP
  pthread_mutex_t mutex = PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP;
#else
  pthread_mutex_t mutex = PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
#endif
  ASSERT_EQ(pthread_mutex_lock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_trylock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_unlock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_unlock(&mutex), 0);
  ASSERT_EQ(pthread_mutex_destroy(&mutex), 0);
}
