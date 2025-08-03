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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_MANAGER_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_MANAGER_H_

#include "berberis/guest_os_primitives/guest_thread.h"

namespace berberis {

void InitGuestThreadManager();

GuestThread* GetCurrentGuestThread();

void ResetCurrentGuestThreadAfterFork(GuestThread* thread);

bool GetGuestThreadAttr(pid_t tid,
                        GuestAddr* stack_base,
                        size_t* stack_size,
                        size_t* guard_size,
                        int* error);

void ExitCurrentThread(int status);

// Ensure guest threads don't run obsolete code.
void FlushGuestCodeCache();

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_THREAD_MANAGER_H_
