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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_GUEST_FUNCTION_WRAPPER_IMPL_H_
#define BERBERIS_RUNTIME_PRIMITIVES_GUEST_FUNCTION_WRAPPER_IMPL_H_

#include <cstddef>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

struct GuestArgumentBuffer;

// Wrap guest function so that host can call it as if it was host function.
// Signature is "<return-type><param-type>*":
//   v - void
//   p - pointer
//   i - int32
//   l - int64
//   f - fp32
//   d - fp64
// Guest runner is a function to actually invoke guest code. The default
// is RunGuestCall, custom runners might add pre- and post-processing.

using GuestRunnerFunc = void (*)(GuestAddr pc, GuestArgumentBuffer* buf);
using IsAddressGuestExecutableFunc = bool (*)(GuestAddr pc);

HostCode WrapGuestFunctionImpl(GuestAddr pc,
                               const char* signature,
                               GuestRunnerFunc guest_runner,
                               const char* name);

struct NamedGuestFunctionWrapper {
  const char* name;
  HostCode (*wrapper)(GuestAddr pc);
};

GuestAddr SlowFindGuestAddrByWrapperAddr(void* wrapper_addr);

void InitGuestFunctionWrapper(IsAddressGuestExecutableFunc func);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_GUEST_FUNCTION_WRAPPER_IMPL_H_
