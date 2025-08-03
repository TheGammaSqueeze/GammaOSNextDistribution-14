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
#include <setjmp.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/time.h>

#include <atomic>

#include "berberis/ndk_program_tests/scoped_sigaction.h"

namespace {

void EnsureSignalsChecked() {
  // Emulated signals should be checked on return from wrapped syscall.
  // Real signals should be checked on exit from kernel mode.
  syscall(SYS_gettid);
}

void HandleSignal(int) {
  return;
}

TEST(Signal, SigkillSigactionFails) {
  struct sigaction sa {};
  sa.sa_handler = HandleSignal;
  ASSERT_EQ(sigaction(SIGKILL, &sa, nullptr), -1);
}

struct ThreadParam {
  pthread_t self;
  int id;
  std::atomic_bool started;
  std::atomic_bool stop;
};

const int kMaxThreads = 20;
ThreadParam g_params[kMaxThreads];

bool AreAllThreadsStarted() {
  for (int i = 0; i < kMaxThreads; i++) {
    if (!g_params[i].started) {
      return false;
    }
  }
  return true;
}

bool AreAllThreadsStopped() {
  for (int i = 0; i < kMaxThreads; i++) {
    if (!g_params[i].stop) {
      return false;
    }
  }
  return true;
}

void ThreadSignalHandler(int /* sig */, siginfo_t* /* info */, void* /* ctx */) {
  pthread_t me = pthread_self();
  for (int i = 0; i < kMaxThreads; i++) {
    if (g_params[i].self == me) {
      g_params[i].stop = true;
      return;
    }
  }
}

void* ThreadRunner(void* arg) {
  ThreadParam* param = reinterpret_cast<ThreadParam*>(arg);
  fprintf(stderr, "Thread %d started\n", param->id);
  param->started = true;
  while (!param->stop) {
    sched_yield();
  }
  fprintf(stderr, "Thread %d exited\n", param->id);
  return nullptr;
}

TEST(Signal, PthreadKillTest) {
  const int sig_num = SIGPWR;

  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = ThreadSignalHandler;
  ScopedSigaction scoped_sa(sig_num, &sa);

  // Initialize globals here to allow test repetition.
  for (int i = 0; i < kMaxThreads; i++) {
    g_params[i].id = i;
    g_params[i].started = false;
    g_params[i].stop = false;
  }

  for (int i = 0; i < kMaxThreads; i++) {
    int rv = pthread_create(&g_params[i].self, nullptr, ThreadRunner, &g_params[i]);
    ASSERT_EQ(rv, 0);
  }
  fprintf(stderr, "All threads created\n");

  while (!AreAllThreadsStarted()) {
    sched_yield();
  }
  fprintf(stderr, "All threads started\n");

  // Send them a signal.
  for (int i = 0; i < kMaxThreads; i++) {
    int rv = pthread_kill(g_params[i].self, sig_num);
    ASSERT_EQ(rv, 0);
  }
  fprintf(stderr, "All threads killed\n");

  while (!AreAllThreadsStopped()) {
    sched_yield();
  }
  fprintf(stderr, "All threads stopped\n");

  for (int i = 0; i < kMaxThreads; i++) {
    int rv = pthread_join(g_params[i].self, nullptr);
    ASSERT_EQ(rv, 0);
  }
  fprintf(stderr, "All threads exited\n");
}

int* g_data_page;

void SigsegvSignalHandler(int /* sig */, siginfo_t* info, void* /* ctx */) {
  fprintf(stderr, "SIGSEGV caught\n");
  EXPECT_TRUE(SI_FROMKERNEL(info));
  mprotect(g_data_page, 4096, PROT_WRITE);
}

TEST(Signal, Sigsegv) {
  g_data_page = static_cast<int*>(mmap(0, 4096, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));

  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = SigsegvSignalHandler;
  ScopedSigaction scoped_sa(SIGSEGV, &sa);

  g_data_page[5] = 0;

  munmap(g_data_page, 4096);
}

bool g_async_sigsegv_received = false;

void AsyncSigsegvSignalHandler(int /* sig */, siginfo_t* info, void* /* ctx */) {
  fprintf(stderr, "Async SIGSEGV caught\n");
  // si_pid must be set for signals sent by kill.
  EXPECT_EQ(getpid(), info->si_pid);
  EXPECT_TRUE(SI_FROMUSER(info));
  g_async_sigsegv_received = true;
}

void* AsyncSigsegvSender(void* arg) {
  pthread_t* parent_id = static_cast<pthread_t*>(arg);
  EXPECT_EQ(pthread_kill(*parent_id, SIGSEGV), 0);
  return nullptr;
}

TEST(Signal, AsyncSigsegv) {
  struct sigaction sa {};
  sa.sa_flags = SA_SIGINFO;
  sa.sa_sigaction = AsyncSigsegvSignalHandler;
  ScopedSigaction scoped_sa(SIGSEGV, &sa);

  g_async_sigsegv_received = false;
  pthread_t parent_id = pthread_self();
  pthread_t child_id;
  ASSERT_EQ(pthread_create(&child_id, nullptr, AsyncSigsegvSender, &parent_id), 0);
  ASSERT_EQ(pthread_join(child_id, nullptr), 0);
  EnsureSignalsChecked();
  ASSERT_TRUE(g_async_sigsegv_received);
}

// Must be valid instruction address. 0 is invalid as the compiler optimizes call(0) to UD.
constexpr uintptr_t kNoExecAddr = 4096;

sigjmp_buf g_recover_no_exec;

void NoExecSignalHandler(int /* sig */, siginfo_t* info, void* /* ctx */) {
  uintptr_t addr = reinterpret_cast<uintptr_t>(info->si_addr);
  EXPECT_EQ(addr, kNoExecAddr);
  longjmp(g_recover_no_exec, 1);
}

TEST(Signal, RecoverFromNoExec) {
  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = NoExecSignalHandler;
  ScopedSigaction scoped_sa(SIGSEGV, &sa);

  if (setjmp(g_recover_no_exec) == 0) {
    using Func = void (*)();
    (reinterpret_cast<Func>(kNoExecAddr))();
    // Signal handler should longjmp out!
    FAIL();
  }
}

int g_expected_signal;
bool g_is_received;

void CheckExpectedSignalHandler(int signal) {
  ASSERT_EQ(signal, g_expected_signal);
  g_is_received = true;
}

TEST(Signal, SigMask) {
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sigemptyset(&sa.sa_mask);
  sa.sa_handler = CheckExpectedSignalHandler;

  // Walk signals customizable by apps. Avoid signals handled by ART.
  const int test_signals[] = {SIGILL, SIGXCPU, SIGPWR};

  for (size_t i = 0; i < sizeof(test_signals) / sizeof(test_signals[0]); ++i) {
    int signal = test_signals[i];
    ScopedSigaction scoped_sa(signal, &sa);

    sigset_t mask;

    // Block signal.
    ASSERT_EQ(sigemptyset(&mask), 0);
    ASSERT_EQ(sigaddset(&mask, signal), 0);
    ASSERT_EQ(pthread_sigmask(SIG_BLOCK, &mask, nullptr), 0);

    // Send signal to itself. Expect it not to be delivered.
    // NOTE: sending SIGILL with pthread_kill when blocked is well-defined!
    g_expected_signal = -1;
    g_is_received = false;
    // raise() is not supported.
    ASSERT_EQ(pthread_kill(pthread_self(), signal), 0);

    // This shouldn't trigger delivering of blocked signal.
    EnsureSignalsChecked();

    // Unblock signal and expect it to be delivered.
    g_expected_signal = signal;
    ASSERT_EQ(sigemptyset(&mask), 0);
    ASSERT_EQ(pthread_sigmask(SIG_SETMASK, &mask, nullptr), 0);

    // Wait until we receive it.
    while (!g_is_received) {
      sched_yield();
    }
  }
}

std::atomic_bool g_started;
std::atomic_bool g_suspend_sent;
std::atomic_bool g_resume_sent;
std::atomic_bool g_suspend_handler_visited;
std::atomic_bool g_resume_handler_visited;
int g_expected_resume_signal;

void ResumeHandler(int signal) {
  ASSERT_EQ(signal, g_expected_resume_signal);
  g_resume_handler_visited = true;
}

void SuspendHandler(int signal) {
  ASSERT_EQ(signal, SIGPWR);
  g_suspend_handler_visited = true;

  // Check resume signal is blocked.
  sigset_t current_mask;
  pthread_sigmask(SIG_BLOCK, nullptr, &current_mask);
  ASSERT_EQ(sigismember(&current_mask, SIGXCPU), 1);

  while (!g_resume_sent) {
    sched_yield();
  }
  // Resume is sent, but should still be blocked.
  EnsureSignalsChecked();

  // Now catch signal in sigsuspend with empty mask.
  g_expected_resume_signal = SIGXCPU;
  sigset_t suspend_mask;
  sigemptyset(&suspend_mask);
  sigsuspend(&suspend_mask);

  // Mask should be restored.
  pthread_sigmask(SIG_BLOCK, nullptr, &current_mask);
  ASSERT_EQ(sigismember(&current_mask, SIGXCPU), 1);
}

void* WaitForSuspendRunner(void* /* arg */) {
  g_started = true;
  while (!g_suspend_sent) {
    sched_yield();
  }
  EnsureSignalsChecked();
  return nullptr;
}

TEST(Signal, SigActionAndSuspendMasks) {
  // Set resume sigaction.
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sigemptyset(&sa.sa_mask);
  sa.sa_handler = ResumeHandler;
  ScopedSigaction scoped_xcpu(SIGXCPU, &sa);

  // Set suspend sigaction to block SIGXCPU in handler.
  sigaddset(&sa.sa_mask, SIGXCPU);
  sa.sa_handler = SuspendHandler;
  ScopedSigaction scoped_pwr(SIGPWR, &sa);

  g_started = false;
  g_suspend_sent = false;
  g_resume_sent = false;
  g_suspend_handler_visited = false;
  g_resume_handler_visited = false;
  g_expected_resume_signal = -1;

  // Start the second thread.
  pthread_t child_id;
  ASSERT_EQ(pthread_create(&child_id, nullptr, WaitForSuspendRunner, nullptr), 0);
  while (!g_started) {
    sched_yield();
  }

  // Direct it into suspend handler and wait while it gets there.
  ASSERT_EQ(pthread_kill(child_id, SIGPWR), 0);
  g_suspend_sent = true;
  while (!g_suspend_handler_visited) {
    sched_yield();
  }

  // Direct it further into resume handler and wait while it gets there.
  ASSERT_EQ(pthread_kill(child_id, SIGXCPU), 0);
  g_resume_sent = true;
  while (!g_resume_handler_visited) {
    sched_yield();
  }
}

volatile int g_handler_counter;

void SigActionDeferHandler(int signal) {
  ASSERT_EQ(signal, SIGPWR);

  static volatile bool in_handler = false;
  ASSERT_FALSE(in_handler);
  in_handler = true;

  if (g_handler_counter++ == 0) {
    ASSERT_EQ(pthread_kill(pthread_self(), SIGPWR), 0);
    EnsureSignalsChecked();
  }

  in_handler = false;
}

TEST(Signal, SigActionDefer) {
  // Set resume sigaction.
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));

  g_handler_counter = 0;

  // When SA_NODEFER is unset, signal in blocked in its handler.
  ASSERT_EQ(sigemptyset(&sa.sa_mask), 0);
  sa.sa_handler = SigActionDeferHandler;
  ScopedSigaction scoped_sa(SIGPWR, &sa);

  ASSERT_EQ(pthread_kill(pthread_self(), SIGPWR), 0);
  // Should catch two signals: one from here and one from handler.
  EnsureSignalsChecked();
  EnsureSignalsChecked();
  ASSERT_EQ(g_handler_counter, 2);
}

void SigActionNoDeferHandler(int signal) {
  ASSERT_EQ(signal, SIGPWR);

  static volatile bool in_handler = false;
  ASSERT_EQ(in_handler, (g_handler_counter == 1));
  in_handler = true;

  if (g_handler_counter++ == 0) {
    ASSERT_EQ(pthread_kill(pthread_self(), SIGPWR), 0);
    EnsureSignalsChecked();
  }

  // We set is to false while returning from the second handler
  // to the first one. But it is ok, since we don't use it in
  // the first handler anymore.
  in_handler = false;
}

TEST(Signal, SigActionNoDefer) {
  // Set resume sigaction.
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));

  g_handler_counter = 0;

  // Now set sigaction with SA_NODEFER.
  sa.sa_handler = SigActionNoDeferHandler;
  sa.sa_flags |= SA_NODEFER;
  ScopedSigaction scoped_sa(SIGPWR, &sa);

  ASSERT_EQ(pthread_kill(pthread_self(), SIGPWR), 0);
  EnsureSignalsChecked();
  ASSERT_EQ(g_handler_counter, 2);
}

sem_t g_kill_and_wait_sem;

void KillAndSemWaitHandler(int signal) {
  ASSERT_EQ(signal, SIGPWR);
  // Notify parent that child is in handler.
  ASSERT_EQ(sem_post(&g_kill_and_wait_sem), 0);
}

void KillAndSemWaitRunner(void* /* arg */) {
  // Register handler
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  ASSERT_EQ(sigemptyset(&sa.sa_mask), 0);
  sa.sa_handler = KillAndSemWaitHandler;
  ScopedSigaction scoped_sa(SIGPWR, &sa);

  // Notify parent that child is ready to receive signals.
  ASSERT_EQ(sem_post(&g_kill_and_wait_sem), 0);

  sigset_t suspend_mask;
  ASSERT_EQ(sigemptyset(&suspend_mask), 0);

  // Ensure receiving some signals before exiting.
  // Warning: we receive signals even outside sigsuspend!
  for (int i = 0; i < 10; ++i) {
    ASSERT_EQ(sigsuspend(&suspend_mask), -1);
    ASSERT_EQ(errno, EINTR);
  }
}

void* KillAndSemWaitRunnerWrapper(void* arg) {
  // Assertions cannot be used in a function that returns non-void.
  KillAndSemWaitRunner(arg);
  return nullptr;
}

// TODO(b/28014551): this test might be wrong, it seems even when pthread_kill returns 0 there is no
// guarantee that signal handler will be executed. For example, signal might be blocked until thread
// exit. Or, thread might be killed before starting the signal handler. Also, it is not clear what
// happens if signal arrives right when thread is going to finish.
// Investigate more if this test is valid or not!
TEST(Signal, DISABLED_SignalKillAndSemWaitTest) {
  sem_init(&g_kill_and_wait_sem, 0, 0);

  // Start thread.
  pthread_t child_id;
  ASSERT_EQ(pthread_create(&child_id, nullptr, KillAndSemWaitRunnerWrapper, nullptr), 0);

  // Wait for child able to receive signals.
  ASSERT_EQ(sem_wait(&g_kill_and_wait_sem), 0);

  // If signal is successfully sent, child must handle it
  // notifying parent with semaphore.
  while (pthread_kill(child_id, SIGPWR) == 0) {
    ASSERT_EQ(sem_wait(&g_kill_and_wait_sem), 0);
  }
}

std::atomic_bool g_is_in_loop;
std::atomic_bool g_is_received_in_loop;

// POSIX recommends using itimerspec instead, but we don't support it in Berberis yet.
constexpr itimerval kTenMillisecondIntervalTimer = itimerval{
    // Fire after 10 millisecond initially then every 10 millisecond further on (in case we haven't
    // entered the loop when the first signal arrived).
    .it_interval = {.tv_sec = 0, .tv_usec = 10000},
    .it_value = {.tv_sec = 0, .tv_usec = 10000},
};

void InterruptLoopHandler(int signal) {
  EXPECT_EQ(signal, SIGALRM);
  g_is_received_in_loop = g_is_in_loop.load();
}

void InterruptLoopHelper(void (*LoopRunner)()) {
  struct sigaction sa {};
  sa.sa_handler = InterruptLoopHandler;
  ScopedSigaction scoped_sa(SIGALRM, &sa);

  g_is_in_loop = false;
  g_is_received_in_loop = false;

  struct itimerval old_itimer;
  setitimer(ITIMER_REAL, &kTenMillisecondIntervalTimer, &old_itimer);

  LoopRunner();

  setitimer(ITIMER_REAL, &old_itimer, nullptr);
}

TEST(Signal, InterruptLoopWithinRegion) {
  InterruptLoopHelper(+[]() {
    while (!g_is_received_in_loop) {
      // Keep it simple to facilitate having it in single translation region.
      g_is_in_loop = true;
    }
  });
}

void __attribute__((noinline)) RegionBreaker() {
  g_is_in_loop = true;
}

TEST(Signal, InterruptInterRegionLoop) {
  InterruptLoopHelper(+[]() {
    while (!g_is_received_in_loop) {
      // Facilitate translated regions break (due to call/return) so that the loop
      // is not inside one region.
      RegionBreaker();
    }
  });
}

}  // namespace
