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

#ifndef NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_SETJMP_THUNKS_H_
#define NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_SETJMP_THUNKS_H_

namespace berberis {

void DoThunk__longjmp(void* guest_buf, int value);

int DoThunk__setjmp(void* guest_buf);

int DoThunk_setjmp(void* guest_buf);

void DoThunk_longjmp(void* guest_buf, int value);

int DoThunk_sigsetjmp(void* guest_buf, int savesig);

void DoThunk_siglongjmp(void* guest_buf, int value);

}  // namespace berberis

#endif  // NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_SETJMP_THUNKS_H_
