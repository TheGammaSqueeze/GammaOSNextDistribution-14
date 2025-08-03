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

#include "pthread_translation.h"

#include <dlfcn.h>  // dlsym
#include <errno.h>
#include <unistd.h>  // getpid

#include <algorithm>  // std::max
#include <vector>

#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_call.h"
#include "berberis/guest_abi/guest_params_arch.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_os_primitives/scoped_pending_signals.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_stack.h"

namespace berberis {

namespace {

using PthreadCleanupFunc = void (*)(void* arg);

}  // namespace

// int pthread_create(pthread_t *thread, const pthread_attr_t *attr,
//                    void *(*start_routine)(void*), void *arg);
void DoCustomTrampoline_pthread_create(HostCode /* callee */,
                                       ProcessState* state) {
  auto [thread, guest_attr, func, arg] =
      GuestParamsValues<decltype(pthread_create)>(state);

  pthread_attr_t attr;
  if (guest_attr) {
    // We'll change the attr, so make a copy.
    // ATTENTION: this is not standard-compliant, just OK for bionic!
    memcpy(&attr, guest_attr, sizeof(attr));
  } else {
    pthread_attr_init(&attr);
  }

  // Avoid using pthread_attr_get/set*, as they do error-checking we don't want
  // here! ATTENTION: this is not standard-compliant, just OK for bionic!
  void* guest_stack = attr.stack_base;
  size_t guest_stack_size = attr.stack_size;
  size_t guest_guard_size = attr.guard_size;
  // Ensure host stack is big enough to do translation.
  // ATTENTION: don't make host stack smaller than guest stack (so don't use the
  // default size)! Guest might be calling stack hungry host code via
  // trampolines. Also, this way we don't need to check if guest guard size is
  // OK for host stack size.
  attr.stack_base = nullptr;
  attr.stack_size = std::max(guest_stack_size, GetStackSizeForTranslation());

  auto&& [ret] = GuestReturnReference<decltype(pthread_create)>(state);
  ret = CreateNewGuestThread(thread, &attr, guest_stack, guest_stack_size,
                             guest_guard_size, ToGuestAddr(func),
                             ToGuestAddr(arg));

  pthread_attr_destroy(&attr);
}

// int pthread_join(pthread_t thread, void **retval);
void DoCustomTrampoline_pthread_join(HostCode /* callee */,
                                     ProcessState* state) {
  auto [guest_thread, retval] =
      GuestParamsValues<decltype(pthread_join)>(state);

  ScopedPendingSignalsDisabler scoped_pending_signals_disabler(state->thread);
  auto&& [ret] = GuestReturnReference<decltype(pthread_create)>(state);
  ret = pthread_join(guest_thread, retval);
}

// int pthread_getattr_np(pthread_t thread, pthread_attr_t *attr);
void DoCustomTrampoline_pthread_getattr_np(HostCode /* callee */,
                                           ProcessState* state) {
  auto [guest_thread, guest_attr] =
      GuestParamsValues<decltype(pthread_getattr_np)>(state);

  // Query host attr.
  auto&& [ret] = GuestReturnReference<decltype(pthread_getattr_np)>(state);
  ret = pthread_getattr_np(guest_thread, guest_attr);
  if (ret != 0) {
    return;
  }

  // Overwrite attr with guest stack values.
  // ATTENTION: if we fail to find thread in guest threads table, then it means
  // the thread exists (pthread_getattr_np above succeeded!) but simply doesn't
  // run any guest code... so don't fail and return attr as reported by host!
  GuestAddr stack_base;
  size_t stack_size;
  size_t guard_size;
  int error;
  pid_t tid = pthread_gettid_np(guest_thread);
  if (GetGuestThreadAttr(tid, &stack_base, &stack_size, &guard_size, &error)) {
    guest_attr->stack_base = ToHostAddr<void>(stack_base);
    // TODO(b/78156520): main thread's stack has no guard and its size is
    // affected by setrlimit(RLIMIT_STACK). At the moment, keep these as
    // reported by host...
    if (tid != getpid()) {
      guest_attr->stack_size = stack_size;
      guest_attr->guard_size = guard_size;
    }
  }
}

// void __pthread_cleanup_push( __pthread_cleanup_t*      c,
//                              __pthread_cleanup_func_t  routine,
//                              void*                     arg)
void DoCustomTrampoline___pthread_cleanup_push(HostCode /* callee */,
                                               ProcessState* state) {
  auto [cleanup, routine, arg] =
      GuestParamsValues<decltype(__pthread_cleanup_push)>(state);
  __pthread_cleanup_push(
      cleanup, WrapGuestFunction(routine, "__pthread_cleanup_push-callback"),
      arg);
}

// int pthread_key_create(pthread_key_t* key, void (*destructor)(void*));
void DoCustomTrampoline_pthread_key_create(HostCode /* callee */,
                                           ProcessState* state) {
  auto [key, guest_destructor] =
      GuestParamsValues<decltype(pthread_key_create)>(state);
  auto&& [ret] = GuestReturnReference<decltype(pthread_key_create)>(state);
  if (ToGuestAddr(guest_destructor) == 0) {
    ret = pthread_key_create(key, nullptr);
  } else {
    using Destructor = void (*)(void* value);
    Destructor host_destructor = AsFuncPtr<Destructor>(WrapGuestFunctionImpl(
        ToGuestAddr(guest_destructor),
        kGuestFunctionWrapperSignature<Destructor>, RunGuestPthreadKeyDtor,
        "pthread_key_create-destructor"));
    ret = pthread_key_create(key, host_destructor);
  }
}

// uintptr_t __get_thread_stack_top();
void DoCustomTrampoline___get_thread_stack_top(HostCode /* callee */,
                                               ProcessState* state) {
  auto&& [ret] = GuestReturnReference<uintptr_t()>(state);
  ret = state->thread->GetStackTop();
}

}  // namespace berberis
