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

#include <sched.h>
#include <semaphore.h>

#include "berberis/base/checks.h"
#include "berberis/base/host_signal.h"  // RTSigprocmaskSyscallOrDie
#include "berberis/guest_abi/guest_call.h"

#include "guest_thread_manager_impl.h"  // InsertCurrentThread
#include "guest_thread_pthread_create.h"

namespace berberis {

void* RunGuestThread(void* arg) {
  GuestThreadCreateInfo* info = static_cast<GuestThreadCreateInfo*>(arg);

  // The thread is created by pthread_create, use pthread_key dtor for destruction.
  // Might handle destruction in pthread_join/pthread_exit instead, but seems more complex.
  InsertCurrentThread(info->thread, true);
  info->thread->InitStaticTls();

  // Caller will destroy info after we notify it, so save thread function locally.
  GuestAddr guest_func = info->func;
  GuestAddr guest_arg = info->arg;

  RTSigprocmaskSyscallOrDie(SIG_SETMASK, &info->mask, nullptr);

  // Notify the caller that thread is ready.
  CHECK_EQ(0, sem_post(&info->sem));
  // TODO(b/77574158): Ensure caller has a chance to handle the notification.
  sched_yield();

  GuestCall call;
  call.AddArgGuestAddr(guest_arg);
  return ToHostAddr<void>(call.RunResGuestAddr(guest_func));
}

}  // namespace berberis