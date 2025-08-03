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

#ifndef NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_CXA_TRAMPOLINES_H_
#define NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_CXA_TRAMPOLINES_H_

#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

// TODO(b/65052237): Currently we don't expose __cxa_finalize and __cxa_atexit
// to the guest code. This means that functions registered with __cxa_atexit by
// guest are not called when host calls exit().  Investigate and fix the issue.

void DoCustomTrampoline_native_bridge___cxa_thread_atexit_impl(
    HostCode callee, ProcessState* state);

}  // namespace berberis

#endif  // NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_CXA_TRAMPOLINES_H_
