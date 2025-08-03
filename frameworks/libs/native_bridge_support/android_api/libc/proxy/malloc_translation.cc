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

#include "malloc_translation.h"

#include <malloc.h>
#include <stdio.h>

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_state.h"

using MallocIterateCallback = void (*)(uintptr_t base, size_t size, void* arg);

// malloc_iterate is not declared in any public bionic headers.
extern "C" int malloc_iterate(uintptr_t base, size_t size,
                              MallocIterateCallback callback, void* arg);

namespace berberis {

// int native_bridge_malloc_info(int options, int fd);
void DoCustomTrampoline_native_bridge_malloc_info_helper(HostCode /*callee*/,
                                                         ProcessState* state) {
  // Note: we couldn't handle malloc_info here.  It has the following prototype:
  //   int malloc_info(int options, FILE *stream);
  // And it's really hard to deal with Guest type FILE on host.
  // Instead we call fileno(stream) in guest code and pass fd to
  // native_bridge_malloc_info.
  using NativeBridgeMallocInfo = int (*)(int options, int fd);
  auto [options, fd] = GuestParamsValues<NativeBridgeMallocInfo>(state);
  FILE* fp = fdopen(dup(fd), "w");  // fdopen "w" doesn't truncate fd!
  CHECK(fp);
  auto&& [ret] = GuestReturnReference<NativeBridgeMallocInfo>(state);
  ret = malloc_info(options, fp);
  fclose(fp);
}

// struct mallinfo mallinfo(void);
// Using custom trampoline to handle struct return type.
void DoCustomTrampoline_native_bridge_mallinfo(HostCode /* callee */,
                                               ProcessState* state) {
  auto&& [ret] = GuestReturnReference<decltype(mallinfo)>(state);
  ret = mallinfo();
}

// Using custom trampoline to handle callback.
void DoCustomTrampoline_native_bridge_malloc_iterate(HostCode /* callee */,
                                                     ProcessState* state) {
  auto [base, size, guest_callback, arg] =
      GuestParamsValues<decltype(malloc_iterate)>(state);
  auto callback = WrapGuestFunction(guest_callback, "MallocIterateCallback");
  auto&& [ret] = GuestReturnReference<decltype(malloc_iterate)>(state);
  ret = malloc_iterate(base, size, callback, arg);
}

}  // namespace berberis
