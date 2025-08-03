/*
 * Copyright (C) 2014 The Android Open Source Project
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
#include <semaphore.h>

#include <cerrno>
#include <ctime>

// Provide prototypes of these two functions: they are not defined anywhere
// but since they are actually provided by Bionic we could actually call them.
extern "C" int __futex_wake(volatile void* ftx, int count) __attribute__((__weak__));
extern "C" int __futex_wait(volatile void* ftx, int value, const struct timespec* timeout)
    __attribute__((__weak__));

TEST(Futex, SingleThread) {
  volatile int dummy_var = 0;
  volatile void* futex = reinterpret_cast<volatile void*>(&dummy_var);
  ASSERT_EQ(__futex_wait(futex, 1, nullptr), -EWOULDBLOCK);
  ASSERT_EQ(__futex_wake(futex, 1), 0);
  struct timespec timeout;
  timeout.tv_sec = 0;
  timeout.tv_nsec = 1000000;  // 1ms
  ASSERT_EQ(__futex_wait(futex, 0, &timeout), -ETIMEDOUT);
}

static void* FutexThread(void* arg) {
  volatile void* futex = *reinterpret_cast<volatile void**>(arg);
  // Sleep a little to improve chances that main thread started waiting.
  struct timespec time;
  time.tv_sec = 0;
  time.tv_nsec = 10 * 1000000;  // 10 ms
  nanosleep(&time, nullptr);

  *reinterpret_cast<volatile int*>(futex) = 1;
  int res = __futex_wake(futex, 1);

  return new int(res);
}

TEST(Futex, Wake) {
  volatile int dummy_var = 0;
  volatile void* futex = reinterpret_cast<volatile void*>(&dummy_var);
  pthread_t thread;
  ASSERT_EQ(pthread_create(&thread, nullptr, &FutexThread, reinterpret_cast<void*>(&futex)), 0);

  int waked = 0;
  while (*reinterpret_cast<volatile int*>(futex) == 0) {
    if (__futex_wait(futex, 0, nullptr) == 0) {
      waked = 1;
    }
  }

  int* futex_wake_result = nullptr;
  ASSERT_EQ(pthread_join(thread, reinterpret_cast<void**>(&futex_wake_result)), 0);
  ASSERT_NE(futex_wake_result, nullptr);
  EXPECT_EQ(*futex_wake_result, waked);
  delete futex_wake_result;
}

struct WakeMultiple {
  volatile void* futex;
  sem_t sem;
};

void* FutexWaitThread(void* arg) {
  WakeMultiple* data = reinterpret_cast<WakeMultiple*>(arg);
  sem_post(&data->sem);

  int* waked = new int(0);
  while (*reinterpret_cast<volatile int*>(data->futex) == 0) {
    if (__futex_wait(data->futex, 0, nullptr) == 0) {
      *waked = 1;
    }
  }

  return waked;
}

TEST(Futex, WakeMultiple) {
  volatile int dummy_var = 0;
  WakeMultiple data;
  data.futex = reinterpret_cast<volatile void*>(&dummy_var);
  ASSERT_EQ(sem_init(&data.sem, 0, 0), 0);
  const int kThreads = 3;
  pthread_t thread[kThreads];
  for (int i = 0; i < kThreads; i++) {
    ASSERT_EQ(pthread_create(&thread[i], nullptr, &FutexWaitThread, reinterpret_cast<void*>(&data)),
              0);
  }
  // Use semaphore to improve chances that threads started waiting on the futex.
  for (int i = 0; i < kThreads; i++) {
    ASSERT_EQ(sem_wait(&data.sem), 0);
  }

  *reinterpret_cast<volatile int*>(data.futex) = 1;
  int wake = __futex_wake(data.futex, kThreads);

  int waked = 0;
  for (int i = 0; i < kThreads; i++) {
    int* wake_result = nullptr;
    ASSERT_EQ(pthread_join(thread[i], reinterpret_cast<void**>(&wake_result)), 0);
    ASSERT_NE(wake_result, nullptr);
    waked += *wake_result;
    delete wake_result;
  }
  ASSERT_EQ(waked, wake);
}
