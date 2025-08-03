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

#include <array>
#include <thread>

#include "berberis/runtime_primitives/signal_queue.h"

namespace berberis {

namespace {

TEST(SignalQueue, EnqueueDequeue) {
  SignalQueue q;
  siginfo_t* p;

  EXPECT_EQ(nullptr, q.DequeueSignalUnsafe());

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 1;
  q.EnqueueSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(1, p->si_value.sival_int);
  q.FreeSignal(p);

  EXPECT_EQ(nullptr, q.DequeueSignalUnsafe());
}

TEST(SignalQueue, FIFO) {
  SignalQueue q;
  siginfo_t* p;

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 1;
  q.EnqueueSignal(p);

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 2;
  q.EnqueueSignal(p);

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 3;
  q.EnqueueSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(1, p->si_value.sival_int);
  q.FreeSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(2, p->si_value.sival_int);
  q.FreeSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(3, p->si_value.sival_int);
  q.FreeSignal(p);
}

TEST(SignalQueue, Priority) {
  SignalQueue q;
  siginfo_t* p;

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 1;
  q.EnqueueSignal(p);

  p = q.AllocSignal();
  p->si_signo = 6;
  p->si_value.sival_int = 2;
  q.EnqueueSignal(p);

  p = q.AllocSignal();
  p->si_signo = 11;
  p->si_value.sival_int = 3;
  q.EnqueueSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(6, p->si_signo);
  EXPECT_EQ(2, p->si_value.sival_int);
  q.FreeSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(1, p->si_value.sival_int);
  q.FreeSignal(p);

  p = q.AllocSignal();
  p->si_signo = 6;
  p->si_value.sival_int = 4;
  q.EnqueueSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(6, p->si_signo);
  EXPECT_EQ(4, p->si_value.sival_int);
  q.FreeSignal(p);

  p = q.DequeueSignalUnsafe();
  ASSERT_TRUE(p);
  EXPECT_EQ(11, p->si_signo);
  EXPECT_EQ(3, p->si_value.sival_int);
  q.FreeSignal(p);
}

const int kStressThreads = 40;
const int kStressIters = 40;
const int kStressPri = 10;

void* StressEnqueueFunc(SignalQueue* q) {
  for (int i = 0; i < kStressIters; ++i) {
    for (int j = 0; j < kStressPri; ++j) {
      siginfo_t* p = q->AllocSignal();
      // Don't pass signo = 0, let's reserve it for special uses.
      p->si_signo = j + 1;
      q->EnqueueSignal(p);
    }
  }

  return nullptr;
}

TEST(SignalQueue, Stress) {
  SignalQueue q;
  std::array<std::thread, kStressThreads> threads;

  for (int i = 0; i < kStressThreads; ++i) {
    threads[i] = std::thread(StressEnqueueFunc, &q);
  }

  const int kEnqueueCount = kStressThreads * kStressIters * kStressPri;
  int dequeue_count = 0;

  // Dequeue some signals.
  // As they are added while consumed, we can't really check dequeue order.
  while (dequeue_count < kEnqueueCount / 2) {
    siginfo_t* p = q.DequeueSignalUnsafe();
    // We can consume faster than signals are added, though very unlikely :)
    if (p) {
      q.FreeSignal(p);
      ++dequeue_count;
    }
  }

  for (int i = 0; i < kStressThreads; ++i) {
    threads[i].join();
  }

  // Dequeue remaining signals.
  // As we don't add signals any more, check dequeue order.
  int pri = 0;
  for (; dequeue_count < kEnqueueCount; ++dequeue_count) {
    siginfo_t* p = q.DequeueSignalUnsafe();
    ASSERT_TRUE(p);
    EXPECT_LE(pri, p->si_signo);
    pri = p->si_signo;
    q.FreeSignal(p);
  }
}

}  // namespace

}  // namespace berberis
