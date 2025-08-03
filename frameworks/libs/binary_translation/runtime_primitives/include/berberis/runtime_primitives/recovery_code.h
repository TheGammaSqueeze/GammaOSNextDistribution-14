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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_RECOVERY_CODE_H_
#define BERBERIS_RUNTIME_PRIMITIVES_RECOVERY_CODE_H_

#include <cstdint>
#include <initializer_list>
#include <utility>

#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis {

// Can only be called one time during single-threaded initialization.
void InitExtraRecoveryCodeUnsafe(
    std::initializer_list<std::pair<uintptr_t, uintptr_t>> fault_recovery_pairs);

uintptr_t FindRecoveryCode(uintptr_t fault_addr, ThreadState* state);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_RECOVERY_CODE_H_
