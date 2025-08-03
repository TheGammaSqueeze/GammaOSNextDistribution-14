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

#include "unistd_thunks.h"

#include <unistd.h>

#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"

namespace berberis {

pid_t DoThunk___clone_for_fork() {
  // 'fork' will invalidate thread table, so cache current thread before.
  GuestThread* thread = GetCurrentGuestThread();
  int pid = fork();
  if (pid == 0) {
    // Child, reset thread table.
    ResetCurrentGuestThreadAfterFork(thread);
  }
  return pid;
}

}  // namespace berberis
