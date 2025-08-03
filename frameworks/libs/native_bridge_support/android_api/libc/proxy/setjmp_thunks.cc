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

#include "setjmp_thunks.h"

#include <setjmp.h>

#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_setjmp.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"

namespace berberis {

// _longjmp(buf, ret) = siglongjmp(buf, ret) (see
// bionic/libc/arch-arm/bionic/setjmp.S)
void DoThunk__longjmp(void* guest_buf, int value) {
  DoThunk_siglongjmp(guest_buf, value);
}

// _setjmp(buf) = sigsetjmp(buf, 0) (see bionic/libc/arch-arm/bionic/setjmp.S)
int DoThunk__setjmp(void* guest_buf) { return DoThunk_sigsetjmp(guest_buf, 0); }

// longjmp(buf, ret) = siglongjmp(buf, ret) (see
// bionic/libc/arch-arm/bionic/setjmp.S)
void DoThunk_longjmp(void* guest_buf, int value) {
  DoThunk_siglongjmp(guest_buf, value);
}

// setjmp(buf) = sigsetjmp(buf, 1) (see bionic/libc/arch-arm/bionic/setjmp.S)
int DoThunk_setjmp(void* guest_buf) { return DoThunk_sigsetjmp(guest_buf, 1); }

void DoThunk_siglongjmp(void* guest_buf, int value) {
  TRACE("DoThunk_siglongjmp, guest_buf=%p", guest_buf);
  GuestThread* thread = GetCurrentGuestThread();
  RestoreRegsFromJumpBuf(thread->state(), guest_buf, value);
  // ATTENTION: don't restore signal mask, it is already restored!
  siglongjmp(**GetHostJmpBufPtr(guest_buf), 0);
}

int DoThunk_sigsetjmp(void* guest_buf, int save_sig_mask) {
  TRACE("DoThunk_sigsetjmp, guest_buf=%p", guest_buf);
  GuestThread* thread = GetCurrentGuestThread();
  SaveRegsToJumpBuf(thread->state(), guest_buf, save_sig_mask);
  *GetHostJmpBufPtr(guest_buf) = &thread->guest_call_execution()->buf;
  return 0;
}

}  // namespace berberis
