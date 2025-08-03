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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_H_

#include <csetjmp>  // jmp_buf
#include <csignal>  // stack_t

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/signal_queue.h"

struct NativeBridgeStaticTlsConfig;

namespace berberis {

int CreateNewGuestThread(pthread_t* thread_id,
                         const pthread_attr_t* attr,
                         void* guest_stack,
                         size_t guest_stack_size,
                         size_t guest_guard_size,
                         GuestAddr func,
                         GuestAddr arg);

pid_t CloneGuestThread(GuestThread* thread,
                       int flags,
                       GuestAddr guest_stack_top,
                       GuestAddr parent_tid,
                       GuestAddr new_tls,
                       GuestAddr child_tid);

struct GuestArgumentBuffer;
void RunGuestPthreadKeyDtor(GuestAddr pc, GuestArgumentBuffer* buf);

struct GuestCallExecution {
  GuestCallExecution* parent = nullptr;
  GuestAddr sp = {};
  jmp_buf buf;
};

// ATTENTION: GuestThread object can only be used by the current thread!
class GuestThread {
 public:
  static GuestThread* CreatePthread(void* stack, size_t stack_size, size_t guard_size);
  static GuestThread* CreateClone(const GuestThread* parent);
  static void Destroy(GuestThread* thread);
  static void Exit(GuestThread* thread, int status);

  // Initialize *current* guest thread.
  void InitStaticTls();

  // Configure static tls for *current* *main* guest thread.
  void ConfigStaticTls(const NativeBridgeStaticTlsConfig* config);

  void ProcessPendingSignals();

  // Both return *previous* pending signals status (false: disabled, true: enabled).
  bool ProcessAndDisablePendingSignals();
  bool TestAndEnablePendingSignals();

  void SetSignalFromHost(const siginfo_t& info);

  void GetAttr(GuestAddr* stack_base, size_t* stack_size, size_t* guard_size) const {
    *stack_base = ToGuestAddr(stack_);
    *stack_size = stack_size_;
    *guard_size = guard_size_;
  }

  const ThreadState* state() const { return state_; }
  ThreadState* state() { return state_; }

  GuestCallExecution* guest_call_execution() const { return guest_call_execution_; }
  void set_guest_call_execution(GuestCallExecution* guest_call_execution) {
    guest_call_execution_ = guest_call_execution;
  }

  bool SigAltStack(const stack_t* ss, stack_t* old_ss, int* error);
  void SwitchToSigAltStack();
  bool IsOnSigAltStack() const;

  void DisallowStackUnmap() { mmap_size_ = 0; }
  [[nodiscard]] GuestAddr GetStackTop() const { return stack_top_; }

  // TODO(b/156271630): Refactor to make this private.
  void* GetHostStackTop() const;

 private:
  GuestThread() = default;
  static GuestThread* Create();

  bool AllocStack(void* stack, size_t stack_size, size_t guard_size);
  bool AllocShadowCallStack();
  bool AllocStaticTls();

  void ProcessPendingSignalsImpl();

  // Host stack. Valid for cloned threads only.
  void* host_stack_ = nullptr;

  // Stack.
  void* stack_ = nullptr;
  size_t stack_size_ = 0;
  size_t guard_size_ = 0;
  size_t mmap_size_ = 0;
  GuestAddr stack_top_ = {};

  void* static_tls_ = nullptr;

  // Shadow call stack.
  void* scs_region_ = nullptr;
  GuestAddr scs_base_ = {};

  ThreadState* state_ = nullptr;

  SignalQueue pending_signals_;

  GuestCallExecution* guest_call_execution_ = nullptr;

  void* sig_alt_stack_ = nullptr;
  size_t sig_alt_stack_size_ = 0;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_H_
