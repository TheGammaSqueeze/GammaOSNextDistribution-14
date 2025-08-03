/*
 * Copyright (C) 2018 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <sys/syscall.h>

#include <functional>

#include <gtest/gtest.h>

#include "sigchain.h"

#if defined(__clang__) && __has_feature(hwaddress_sanitizer)
#define DISABLE_HWASAN __attribute__((no_sanitize("hwaddress")))
#else
#define DISABLE_HWASAN
#endif

#if !defined(__BIONIC__)
using sigset64_t = sigset_t;

static int sigemptyset64(sigset64_t* set) {
  return sigemptyset(set);
}

static int sigismember64(sigset64_t* set, int member) {
  return sigismember(set, member);
}
#endif

static int RealSigprocmask(int how, const sigset64_t* new_sigset, sigset64_t* old_sigset) {
  // glibc's sigset_t is overly large, so sizeof(*new_sigset) doesn't work.
  return syscall(__NR_rt_sigprocmask, how, new_sigset, old_sigset, NSIG/8);
}

class SigchainTest : public ::testing::Test {
  void SetUp() final {
    art::AddSpecialSignalHandlerFn(SIGSEGV, &action);
  }

  void TearDown() final {
    art::RemoveSpecialSignalHandlerFn(SIGSEGV, action.sc_sigaction);
  }

  art::SigchainAction action = {
      .sc_sigaction = [](int, siginfo_t* info, void*) -> bool {
        return info->si_value.sival_ptr;
      },
      .sc_mask = {},
      .sc_flags = 0,
  };

 protected:
  void RaiseHandled() {
      sigval value;
      value.sival_ptr = &value;
      // pthread_sigqueue would guarantee the signal is delivered to this
      // thread, but it is a nonstandard extension and does not exist in
      // musl.  Gtest is single threaded, and these tests don't create any
      // threads, so sigqueue can be used and will deliver to this thread.
      sigqueue(getpid(), SIGSEGV, value);
  }

  void RaiseUnhandled() {
      sigval value;
      value.sival_ptr = nullptr;
      sigqueue(getpid(), SIGSEGV, value);
  }
};


static void TestSignalBlocking(const std::function<void()>& fn) {
  // Unblock SIGSEGV, make sure it stays unblocked.
  sigset64_t mask;
  sigemptyset64(&mask);
  ASSERT_EQ(0, RealSigprocmask(SIG_SETMASK, &mask, nullptr)) << strerror(errno);

  fn();

  if (testing::Test::HasFatalFailure()) return;
  ASSERT_EQ(0, RealSigprocmask(SIG_SETMASK, nullptr, &mask));
  ASSERT_FALSE(sigismember64(&mask, SIGSEGV));
}

TEST_F(SigchainTest, sigprocmask_setmask) {
  TestSignalBlocking([]() {
    sigset_t mask;
    sigfillset(&mask);
    ASSERT_EQ(0, sigprocmask(SIG_SETMASK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, sigprocmask_block) {
  TestSignalBlocking([]() {
    sigset_t mask;
    sigfillset(&mask);
    ASSERT_EQ(0, sigprocmask(SIG_BLOCK, &mask, nullptr));
  });
}

// bionic-only wide variants for LP32.
#if defined(__BIONIC__)
TEST_F(SigchainTest, sigprocmask64_setmask) {
  TestSignalBlocking([]() {
    sigset64_t mask;
    sigfillset64(&mask);
    ASSERT_EQ(0, sigprocmask64(SIG_SETMASK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, sigprocmask64_block) {
  TestSignalBlocking([]() {
    sigset64_t mask;
    sigfillset64(&mask);
    ASSERT_EQ(0, sigprocmask64(SIG_BLOCK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, pthread_sigmask64_setmask) {
  TestSignalBlocking([]() {
    sigset64_t mask;
    sigfillset64(&mask);
    ASSERT_EQ(0, pthread_sigmask64(SIG_SETMASK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, pthread_sigmask64_block) {
  TestSignalBlocking([]() {
    sigset64_t mask;
    sigfillset64(&mask);
    ASSERT_EQ(0, pthread_sigmask64(SIG_BLOCK, &mask, nullptr));
  });
}
#endif

// glibc doesn't implement most of these in terms of sigprocmask, which we rely on.
#if defined(__BIONIC__)
TEST_F(SigchainTest, pthread_sigmask_setmask) {
  TestSignalBlocking([]() {
    sigset_t mask;
    sigfillset(&mask);
    ASSERT_EQ(0, pthread_sigmask(SIG_SETMASK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, pthread_sigmask_block) {
  TestSignalBlocking([]() {
    sigset_t mask;
    sigfillset(&mask);
    ASSERT_EQ(0, pthread_sigmask(SIG_BLOCK, &mask, nullptr));
  });
}

TEST_F(SigchainTest, sigset_mask) {
  TestSignalBlocking([]() {
    sigset(SIGSEGV, SIG_HOLD);
  });
}

TEST_F(SigchainTest, sighold) {
  TestSignalBlocking([]() {
    sighold(SIGSEGV);
  });
}

#if !defined(__riscv)
// Not exposed via headers, but the symbols are available if you declare them yourself.
extern "C" int sigblock(int);
TEST_F(SigchainTest, sigblock) {
  TestSignalBlocking([]() {
    int mask = ~0U;
    ASSERT_EQ(0, sigblock(mask));
  });
}
extern "C" int sigsetmask(int);
TEST_F(SigchainTest, sigsetmask) {
  TestSignalBlocking([]() {
    int mask = ~0U;
    ASSERT_EQ(0, sigsetmask(mask));
  });
}
#endif

#endif

// Make sure that we properly put ourselves back in front if we get circumvented.
TEST_F(SigchainTest, EnsureFrontOfChain) {
#if defined(__BIONIC__)
  constexpr char kLibcSoName[] = "libc.so";
#elif defined(__GNU_LIBRARY__) && __GNU_LIBRARY__ == 6
  constexpr char kLibcSoName[] = "libc.so.6";
#elif defined(ANDROID_HOST_MUSL)
  constexpr char kLibcSoName[] = "libc_musl.so";
#else
  #error Unknown libc
#endif
  void* libc = dlopen(kLibcSoName, RTLD_LAZY | RTLD_NOLOAD);
  ASSERT_TRUE(libc);

  auto libc_sigaction = reinterpret_cast<decltype(&sigaction)>(dlsym(libc, "sigaction"));
  ASSERT_TRUE(libc_sigaction);

  static sig_atomic_t called = 0;
  struct sigaction action = {};
  action.sa_flags = SA_SIGINFO;
  action.sa_sigaction = [](int, siginfo_t*, void*) { called = 1; };

  ASSERT_EQ(0, libc_sigaction(SIGSEGV, &action, nullptr));

  // Try before EnsureFrontOfChain.
  RaiseHandled();
  ASSERT_EQ(1, called);
  called = 0;

  RaiseUnhandled();
  ASSERT_EQ(1, called);
  called = 0;

  // ...and after.
  art::EnsureFrontOfChain(SIGSEGV);
  RaiseHandled();
  ASSERT_EQ(0, called);

  RaiseUnhandled();
  ASSERT_EQ(1, called);
  called = 0;
}

#if defined(__aarch64__)
// The test intentionally dereferences (tagged) null to trigger SIGSEGV.
// We need to disable HWASAN since it would catch the dereference first.
DISABLE_HWASAN void fault_address_tag_impl() {
  struct sigaction action = {};
  action.sa_flags = SA_SIGINFO;
  action.sa_sigaction = [](int, siginfo_t* siginfo, void*) {
    _exit(reinterpret_cast<uintptr_t>(siginfo->si_addr) >> 56);
  };
  ASSERT_EQ(0, sigaction(SIGSEGV, &action, nullptr));

  auto* tagged_null = reinterpret_cast<int*>(0x2bULL << 56);
  EXPECT_EXIT(
      { [[maybe_unused]] volatile int load = *tagged_null; }, testing::ExitedWithCode(0), "");

  // Our sigaction implementation always implements the "clear unknown bits"
  // semantics for oldact.sa_flags regardless of kernel version so we rely on it
  // here to test for kernel support for SA_EXPOSE_TAGBITS.
  action.sa_flags = SA_SIGINFO | SA_EXPOSE_TAGBITS;
  ASSERT_EQ(0, sigaction(SIGSEGV, &action, nullptr));
  ASSERT_EQ(0, sigaction(SIGSEGV, nullptr, &action));
  if (action.sa_flags & SA_EXPOSE_TAGBITS) {
      EXPECT_EXIT({ [[maybe_unused]] volatile int load = *tagged_null; },
                  testing::ExitedWithCode(0x2b),
                  "");
  }
}
#endif

TEST_F(SigchainTest, fault_address_tag) {
#define SA_EXPOSE_TAGBITS 0x00000800
#if defined(__aarch64__)
  fault_address_tag_impl();
#else
  GTEST_SKIP() << "arm64 only";
#endif
}
