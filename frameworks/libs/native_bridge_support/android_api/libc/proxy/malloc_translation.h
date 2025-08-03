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

#ifndef NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_MALLOC_TRANSLATION_H_
#define NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_MALLOC_TRANSLATION_H_

#include <malloc.h>

#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_code.h"

// These symbols are not declared in any public bionic headers.
extern "C" void malloc_disable();
extern "C" void malloc_enable();

// valloc(3) and pvalloc(3) were removed from POSIX 2004.
// These symbols remain only in LP32 bionic for binary compatibility.
extern "C" void* pvalloc(size_t);
extern "C" void* valloc(size_t);

namespace berberis {

void DoCustomTrampoline_native_bridge_malloc_info_helper(HostCode callee,
                                                         ProcessState* state);

void DoCustomTrampoline_native_bridge_mallinfo(HostCode callee,
                                               ProcessState* state);

void DoCustomTrampoline_native_bridge_malloc_iterate(HostCode callee,
                                                     ProcessState* state);

}  // namespace berberis

#endif  // NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_MALLOC_TRANSLATION_H_
