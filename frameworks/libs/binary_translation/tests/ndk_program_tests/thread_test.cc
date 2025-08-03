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

void* ThreadCreateJoinFunc(void* arg) {
  *reinterpret_cast<int*>(arg) = 1;
  return nullptr;
}

TEST(Thread, CreateJoin) {
  pthread_t thread;
  int test_variable = 0;
  ASSERT_EQ(pthread_create(
                &thread, nullptr, ThreadCreateJoinFunc, reinterpret_cast<void*>(&test_variable)),
            0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
  ASSERT_EQ(test_variable, 1);
}

void IncrementCounter(void* arg) {
  int* counter = reinterpret_cast<int*>(arg);
  (*counter)++;
}

void* ThreadKeyFunc(void* arg) {
  pthread_key_t* key = reinterpret_cast<pthread_key_t*>(arg);
  if (pthread_getspecific(*key) != nullptr) {
    return nullptr;
  }
  int* count = new int(0);
  if (pthread_setspecific(*key, count) != 0) {
    delete count;
    return nullptr;
  }
  return reinterpret_cast<void*>(count);
}

void CleanupHandler(void* arg) {
  int* cleanup = reinterpret_cast<int*>(arg);
  *cleanup = 239;
}

void* ThreadCleanupFunc(void* arg) {
  int* var = reinterpret_cast<int*>(arg);

  *var = 0;
  pthread_cleanup_push(CleanupHandler, var);
  pthread_cleanup_pop(1);
  EXPECT_EQ(*var, 239);

  *var = 1;
  pthread_cleanup_push(CleanupHandler, var);
  pthread_cleanup_pop(0);
  EXPECT_EQ(*var, 1);

  *var = 2;
  pthread_cleanup_push(CleanupHandler, var);
  pthread_exit(nullptr);
  pthread_cleanup_pop(0);

  return nullptr;
}

TEST(Thread, Keys) {
  pthread_key_t key;
  pthread_t thread;
  int count = 0;
  int* thread_count;
  ASSERT_EQ(pthread_key_create(&key, IncrementCounter), 0);
  ASSERT_EQ(pthread_setspecific(key, &count), 0);
  ASSERT_EQ(reinterpret_cast<void*>(&count), pthread_getspecific(key));
  ASSERT_EQ(pthread_create(&thread, nullptr, ThreadKeyFunc, reinterpret_cast<void*>(&key)), 0);
  ASSERT_EQ(pthread_join(thread, reinterpret_cast<void**>(&thread_count)), 0);
  // delete does not call destructor.
  ASSERT_EQ(pthread_key_delete(key), 0);
  EXPECT_EQ(count, 0);
  ASSERT_NE(thread_count, nullptr);
  EXPECT_EQ(*thread_count, 1);
  delete thread_count;
}

int g_thread_once_var = 0;

void ThreadOnceFunction() {
  g_thread_once_var++;
}

TEST(Thread, Once) {
  if (g_thread_once_var > 0) {
    GTEST_SKIP() << "This test cannot be repeated";
  }
  static pthread_once_t once = PTHREAD_ONCE_INIT;
  ASSERT_EQ(pthread_once(&once, ThreadOnceFunction), 0);
  ASSERT_EQ(g_thread_once_var, 1);
  ASSERT_EQ(pthread_once(&once, ThreadOnceFunction), 0);
  ASSERT_EQ(g_thread_once_var, 1);
}

TEST(Thread, PThreadAttr) {
  pthread_attr_t attr;
  int state;
  size_t stack_size;
  pthread_attr_init(&attr);
  ASSERT_EQ(pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED), 0);
  ASSERT_EQ(pthread_attr_getdetachstate(&attr, &state), 0);
  EXPECT_EQ(state, PTHREAD_CREATE_DETACHED);
  ASSERT_EQ(pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE), 0);
  ASSERT_EQ(pthread_attr_getdetachstate(&attr, &state), 0);
  EXPECT_EQ(state, PTHREAD_CREATE_JOINABLE);
  ASSERT_EQ(pthread_attr_setstacksize(&attr, 16 * 1024U), 0);
  ASSERT_EQ(pthread_attr_getstacksize(&attr, &stack_size), 0);
  ASSERT_EQ(stack_size, 16 * 1024U);
  ASSERT_EQ(pthread_attr_destroy(&attr), 0);
}

TEST(Thread, CreateWithAttrs) {
  pthread_t thread;
  pthread_attr_t attr;
  int var = 0;
  pthread_attr_init(&attr);
  ASSERT_EQ(pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE), 0);
  ASSERT_EQ(pthread_attr_setstacksize(&attr, 16 * 1024), 0);
  ASSERT_EQ(pthread_create(&thread, &attr, ThreadCreateJoinFunc, &var), 0);
  ASSERT_EQ(pthread_attr_destroy(&attr), 0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
  ASSERT_EQ(var, 1);
}

TEST(Thread, PushPop) {
  int var = 0;
  pthread_t thread;
  ASSERT_EQ(pthread_create(&thread, nullptr, ThreadCleanupFunc, &var), 0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
  EXPECT_EQ(var, 239);
}

void* StoreTid(void* param) {
  int* tid_ptr = reinterpret_cast<int*>(param);
  *tid_ptr = gettid();
  return nullptr;
}

TEST(Thread, GetTid) {
  pid_t tid = gettid();
  ASSERT_GT(tid, 0);
  ASSERT_EQ(tid, gettid());
  pid_t background_tid = 0;
  pthread_t thread;
  ASSERT_EQ(pthread_create(&thread, nullptr, StoreTid, reinterpret_cast<void*>(&background_tid)),
            0);
  ASSERT_EQ(pthread_join(thread, nullptr), 0);
  ASSERT_NE(background_tid, tid);
}

TEST(Thread, GetSetPriority) {
  int orig_priority = getpriority(PRIO_PROCESS, gettid());
  ASSERT_LE(orig_priority, 19);   // The lowest priority.
  ASSERT_GE(orig_priority, -20);  // The highest priority.

  // Make sure there is room to lower the priority in the test.
  // Priority grows toward the negative numbers.
  // Note, that we may not have the permission (CAP_SYS_NICE) to set a higher priority.
  if (orig_priority + 2 > 19) {
    GTEST_SKIP() << "No room to further lower the priority, skipping";
  }

  ASSERT_EQ(setpriority(PRIO_PROCESS, 0, orig_priority + 1), 0);
  ASSERT_EQ(getpriority(PRIO_PROCESS, gettid()), orig_priority + 1);
  ASSERT_EQ(setpriority(PRIO_PROCESS, gettid(), orig_priority + 2), 0);
  ASSERT_EQ(getpriority(PRIO_PROCESS, gettid()), orig_priority + 2);

  // -1 |who| must fail.
  errno = 0;
  ASSERT_EQ(setpriority(PRIO_PROCESS, -1, 0), -1);
  ASSERT_EQ(errno, ESRCH);

  // Try to restore the original priority. May fail if we don't have the permission.
  setpriority(PRIO_PROCESS, gettid(), orig_priority);
}
