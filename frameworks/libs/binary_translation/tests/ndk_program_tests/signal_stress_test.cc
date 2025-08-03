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

// Signal stress test is in a separate source file because we disable it for static test version
// (see Android.bp). Also this way we isolate some extra includes from the main signal test source.

#include "gtest/gtest.h"

#include <errno.h>
#include <pthread.h>
#include <semaphore.h>
#include <signal.h>
#include <sys/epoll.h>

#include "berberis/ndk_program_tests/scoped_sigaction.h"

namespace {

volatile bool g_stress_finished;
sem_t g_stress_sem;

void StressResumeHandler(int signal) {
  ASSERT_EQ(signal, SIGXCPU);
  ASSERT_EQ(0, sem_post(&g_stress_sem));

  // Warning: next SIGPWR is blocked in sigaction, so that we don't have deep recursive handler
  // calls.
}

void StressSuspendHandler(int signal) {
  ASSERT_EQ(signal, SIGPWR);
  ASSERT_EQ(0, sem_post(&g_stress_sem));

  // Warning: SIGXCPU is blocked in sigaction, so that we don't receive it before sigsuspend.

  sigset_t suspend_mask;
  ASSERT_EQ(0, sigemptyset(&suspend_mask));
  ASSERT_EQ(-1, sigsuspend(&suspend_mask));
  ASSERT_EQ(errno, EINTR);
}

struct StressArg {
  int epoll_fd;
};

void* StressWaitForSuspendRunner(void* a) {
  StressArg* arg = reinterpret_cast<StressArg*>(a);
  epoll_event events[1];

  while (!g_stress_finished) {
    // TODO: Add pthread_cond_wait here.

    // Warning: cannot use ASSERT in the function returning non-void.
    EXPECT_EQ(-1, epoll_wait(arg->epoll_fd, events, 1, -1));
    EXPECT_EQ(errno, EINTR);
  }
  return NULL;
}

TEST(Signal, SignalStressTest) {
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));

  // Set suspend sigaction.
  // Block SIGXCPU to make sem_post / sigsuspend combination functional.
  ASSERT_EQ(0, sigemptyset(&sa.sa_mask));
  ASSERT_EQ(0, sigaddset(&sa.sa_mask, SIGXCPU));
  sa.sa_handler = StressSuspendHandler;
  ScopedSigaction scoped_pwr(SIGPWR, &sa);

  // Set resume sigaction.
  // Block SIGPWR to prevent deep stacks in recursive handler calls.
  ASSERT_EQ(0, sigemptyset(&sa.sa_mask));
  ASSERT_EQ(0, sigaddset(&sa.sa_mask, SIGPWR));
  sa.sa_handler = StressResumeHandler;
  ScopedSigaction scoped_xcpu(SIGXCPU, &sa);

  g_stress_finished = false;
  sem_init(&g_stress_sem, 0, 0);

  StressArg arg;
  arg.epoll_fd = epoll_create(1);
  ASSERT_NE(-1, arg.epoll_fd);

  // Start threads.
  static int kStressNumChild = 32;
  pthread_t child_id[kStressNumChild];
  for (int i = 0; i < kStressNumChild; ++i) {
    ASSERT_EQ(0, pthread_create(&child_id[i], NULL, StressWaitForSuspendRunner, &arg));
  }

  for (int stress_rep = 0; stress_rep < 1000; ++stress_rep) {
    // Suspend and wait.
    for (int i = 0; i < kStressNumChild; ++i) {
      ASSERT_EQ(pthread_kill(child_id[i], SIGPWR), 0);
    }

    for (int i = 0; i < kStressNumChild; ++i) {
      // After the first sem_post children wait on sigsuspend.
      ASSERT_EQ(sem_wait(&g_stress_sem), 0);
    }

    // Resume and wait.
    for (int i = 0; i < kStressNumChild; ++i) {
      ASSERT_EQ(pthread_kill(child_id[i], SIGXCPU), 0);
    }

    for (int i = 0; i < kStressNumChild; ++i) {
      // After the second sem_post children wait continue looping in
      // StressWaitForSuspendRunner.
      ASSERT_EQ(sem_wait(&g_stress_sem), 0);
    }
  }

  g_stress_finished = true;

  // Make sure child threads wake up.
  for (int i = 0; i < kStressNumChild; ++i) {
    // Do not check return status, as child may have already exited.
    pthread_kill(child_id[i], SIGXCPU);
  }

  for (int i = 0; i < kStressNumChild; i++) {
    ASSERT_EQ(0, pthread_join(child_id[i], NULL));
  }

  close(arg.epoll_fd);
}

}  // namespace
