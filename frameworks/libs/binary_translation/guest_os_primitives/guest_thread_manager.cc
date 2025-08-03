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

#include <pthread.h>
#include <sys/types.h>  // pid_t
#include <cstddef>      // size_t

#include "berberis/base/checks.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/instrument/guest_thread.h"
#include "berberis/runtime_primitives/code_pool.h"  // ResetAllExecRegions
#include "guest_thread_manager_impl.h"
#include "guest_thread_map.h"
#include "scoped_signal_blocker.h"

namespace berberis {

// Manages thread local storage (TLS) for the current thread's GuestThread instance.
pthread_key_t g_guest_thread_key;

// Tracks GuestThread instances across all threads.
GuestThreadMap g_guest_thread_map_;

namespace {

void GuestThreadDtor(void* /* arg */) {
  // TLS cache was cleared by pthread_exit.
  // TODO(b/280671643): Postpone detach to last pthread destructor iteration.
  // On previous iterations, simply restore TLS cache and return.
  DetachCurrentThread();
}

}  // namespace

// Not thread safe, not async signals safe!
void InitGuestThreadManager() {
  // Here we don't need pthread_once, which is not reentrant due to spinlocks.
  CHECK_EQ(0, pthread_key_create(&g_guest_thread_key, GuestThreadDtor));
}

GuestThread* GetCurrentGuestThread() {
  bool attached;
  return AttachCurrentThread(true, &attached);
}

void ResetCurrentGuestThreadAfterFork(GuestThread* thread) {
  g_guest_thread_map_.ResetThreadTable(GettidSyscall(), thread);
#if defined(__BIONIC__)
  // Force (host) bionic to update cached tid if necessary
  // 1. Bionic `clone` implementation resets cached `tid` before syscall
  //    so that it does not get accidentally propagate to the child.
  // 2. pthread_lock/unlock implementations do not call `gettid()` they
  //    instead access cached value directly from TLS. Which leads to
  //    a situation where cached `tid` is updated in the middle of
  //    `dlopen` and it fails to unlock the mutex because the
  //    ownership check fails. Subsequent `dlsym` (or any other
  //    dl* call) stops on locked mutex.
  //
  // By calling `gettid()` here we force bionic to set the cached
  // value to the correct one.
  CHECK_NE(gettid(), -1);
#endif
  ResetAllExecRegions();
}

bool GetGuestThreadAttr(pid_t tid,
                        GuestAddr* stack_base,
                        size_t* stack_size,
                        size_t* guard_size,
                        int* error) {
  GuestThread* thread = g_guest_thread_map_.FindThread(tid);
  if (thread) {
    thread->GetAttr(stack_base, stack_size, guard_size);
    return true;
  }
  *error = ESRCH;
  return false;
}

void ExitCurrentThread(int status) {
  pid_t tid = GettidSyscall();

  // The following code is not reentrant!
  ScopedSignalBlocker signal_blocker;

  // Remove thread from global table.
  GuestThread* thread = g_guest_thread_map_.RemoveThread(tid);
  if (kInstrumentGuestThread) {
    OnRemoveGuestThread(tid, thread);
  }

  TRACE("guest thread exited %d", tid);
  GuestThread::Exit(thread, status);
}

// We assume translation cache is already modified. If any thread still runs
// a region that is already obsolete, we should force the thread to dispatcher
// to re-read from translation cache. We should also wait for that thread to
// acknowledge the dispatch, so code that called cache invalidation can be sure
// that obsolete code is never run after this point.
void FlushGuestCodeCache() {
  // TODO(b/28081995): at the moment we don't know what range was flushed, so
  // we have to force ALL guest threads to dispatcher. This is really, really,
  // REALLY bad for performance.
  // TODO(b/28081995): at the moment we don't wait for acknowledgment. This
  // might cause subtle guest logic failures.
  pid_t current_tid = GettidSyscall();
  g_guest_thread_map_.ForEachThread([current_tid](pid_t tid, GuestThread* thread) {
    // ATTENTION: we probably don't want to force current thread to dispatcher
    // and to wait for it to acknowledge :) Assume caller of this function
    // (syscall emulation or trampoline) will force re-read from translation
    // cache before continuing to guest code.
    if (tid != current_tid) {
      // Set thread's pending signals to present to force it to dispatcher.
      // ATTENTION! this is the only place we access pending_signals_status
      // from other thread!
      uint8_t old_status = kPendingSignalsEnabled;
      GetPendingSignalsStatusAtomic(*thread->state())
          .compare_exchange_strong(old_status, kPendingSignalsPresent, std::memory_order_acq_rel);
    }
  });
}

// Common guest thread function attaches GuestThread lazily on first call and detaches in pthread
// key destructor (register_dtor = true).
//
// Guest signal handlers and guest pthread key destructors are special as they might be called when
// GuestThread is not yet attached or is already detached. Moreover, they cannot determine between
// latter cases. Thus, signal handlers and key destructors reuse GuestThread if it is attached,
// otherwise they attach AND detach themselves, so GuestThread attach state is preserved and
// GuestThread is never leaked (register_dtor = false).
//
// ATTENTION: When signal handler or key destructor attach GuestThread themselves, they might get
// GuestThread stack different from one used in thread function. It might confuse several
// (ill-formed?) apks, so we issue a warning.
//
// ATTENTION: Can be interrupted!
GuestThread* AttachCurrentThread(bool register_dtor, bool* attached) {
  // The following code is not reentrant!
  ScopedSignalBlocker signal_blocker;

  pid_t tid = GettidSyscall();
  GuestThread* thread = g_guest_thread_map_.FindThread(tid);
  if (thread) {
    // Thread was already attached.
    *attached = false;
    return thread;
  }

  // Copy host stack size attributes.
  size_t stack_size;
  size_t guard_size;
  pthread_attr_t attr;
  CHECK_EQ(0, pthread_getattr_np(pthread_self(), &attr));
  CHECK_EQ(0, pthread_attr_getstacksize(&attr, &stack_size));
  CHECK_EQ(0, pthread_attr_getguardsize(&attr, &guard_size));
  thread = GuestThread::CreatePthread(nullptr, stack_size, guard_size);
  CHECK(thread);

  InsertCurrentThread(thread, register_dtor);
  thread->InitStaticTls();

  // If thread is attached in HandleHostSignal we must run guest handler
  // immediately because we detach guest thread before exit from HandleHostSignal.
  // All non-reentrant code in runtime must be protected with ScopedPendingSignalsEnabler.
  GetPendingSignalsStatusAtomic(*thread->state()) = kPendingSignalsDisabled;
  // AttachCurrentThread is never called from generated code.
  SetResidence(*thread->state(), kOutsideGeneratedCode);

  *attached = true;
  return thread;
}

void InsertCurrentThread(GuestThread* thread, bool register_dtor) {
  pid_t tid = GettidSyscall();

  // The following code is not reentrant!
  ScopedSignalBlocker signal_blocker;

  // Thread should not be already in the table!
  // If signal came after we checked tls cache or table but before we blocked signals, it should
  // have attached AND detached the thread!
  g_guest_thread_map_.InsertThread(tid, thread);
  if (register_dtor) {
    CHECK_EQ(0, pthread_setspecific(g_guest_thread_key, thread));
  }
  if (kInstrumentGuestThread) {
    OnInsertGuestThread(tid, thread);
  }

  TRACE("guest thread attached %d", tid);
}

// ATTENTION: Can be interrupted!
void DetachCurrentThread() {
  pid_t tid = GettidSyscall();

  // The following code is not reentrant!
  ScopedSignalBlocker signal_blocker;

  // Remove thread from global table.
  GuestThread* thread = g_guest_thread_map_.RemoveThread(tid);
  if (kInstrumentGuestThread) {
    OnRemoveGuestThread(tid, thread);
  }

  TRACE("guest thread detached %d", tid);
  GuestThread::Destroy(thread);
}

}  // namespace berberis
