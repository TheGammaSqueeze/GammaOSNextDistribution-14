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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_HOST_FUNCTION_WRAPPER_IMPL_H_
#define BERBERIS_RUNTIME_PRIMITIVES_HOST_FUNCTION_WRAPPER_IMPL_H_

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/checks.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

// When guest branches to 'pc', call the host function 'func' via trampoline 'trampoline_func'.
// Trampoline is a helper function invoked as 'trampoline_func(func, process_state)'.
// It extracts guest parameters, applies necessary conversions and calls 'func', then converts
// the return value and writes it back to the guest state.
// 'name' is used for debugging.
using TrampolineFunc = void (*)(HostCode, ThreadState*);

struct NamedTrampolineFunc {
  const char* name;
  TrampolineFunc trampoline;
};

void MakeTrampolineCallable(GuestAddr pc,
                            bool is_host_func,
                            TrampolineFunc trampoline_func,
                            HostCode func,
                            const char* name);

inline void WrapHostFunctionImpl(HostCode func, TrampolineFunc trampoline_func, const char* name) {
  MakeTrampolineCallable(ToGuestAddr(func), true, trampoline_func, func, name);
}

void* UnwrapHostFunction(GuestAddr pc);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_HOST_FUNCTION_WRAPPER_IMPL_H_
