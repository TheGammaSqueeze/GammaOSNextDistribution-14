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

#include "cxa_trampolines.h"

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"

namespace berberis {

// That function is normally only called by a compiler, but here we need to call
// it somehow.
extern "C" void* __dso_handle;
extern "C" int __cxa_thread_atexit_impl(void (*func)(void*), void* arg,
                                        void* dso_handle);

void DoCustomTrampoline_native_bridge___cxa_thread_atexit_impl(
    HostCode /* callee */, ProcessState* state) {
  auto [guest_func, arg, dso_handle] =
      GuestParamsValues<decltype(__cxa_thread_atexit_impl)>(state);
  auto func =
      WrapGuestFunction(guest_func, "__cxa_thread_atexit_impl-callback");
  auto&& [ret] =
      GuestReturnReference<decltype(__cxa_thread_atexit_impl)>(state);
  ret = __cxa_thread_atexit_impl(func, arg, &__dso_handle);
}

}  // namespace berberis
