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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_PTHREAD_CREATE_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_PTHREAD_CREATE_H_

#include <semaphore.h>

#include "berberis/base/host_signal.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

struct GuestThreadCreateInfo {
  GuestThread* thread;
  HostSigset mask;
  GuestAddr func;
  GuestAddr arg;
  sem_t sem;
};

// Function that runs via pthread_create.
// Must be compiled according to the target guest abi.
void* RunGuestThread(void* arg);

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_PTHREAD_CREATE_H_