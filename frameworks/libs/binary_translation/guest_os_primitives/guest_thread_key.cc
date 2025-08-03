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

#include "berberis/guest_os_primitives/guest_thread.h"

#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/runtime_library.h"

#include "guest_thread_manager_impl.h"

namespace berberis {

// Custom runner for handling detached GuestThread case.
void RunGuestPthreadKeyDtor(GuestAddr pc, GuestArgumentBuffer* buf) {
  bool attached;
  AttachCurrentThread(false, &attached);
  if (attached) {
    TRACE("guest pthread key destructor called with detached GuestThread");
  }

  RunGuestCall(pc, buf);

  if (attached) {
    DetachCurrentThread();
  }
}

}  // namespace berberis
