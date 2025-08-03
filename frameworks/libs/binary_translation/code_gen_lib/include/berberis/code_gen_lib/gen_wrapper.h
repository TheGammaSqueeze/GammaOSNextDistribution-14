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

#ifndef BERBERIS_CODE_GEN_LIB_GEN_WRAPPER_H_
#define BERBERIS_CODE_GEN_LIB_GEN_WRAPPER_H_

#include "berberis/assembler/machine_code.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

// Generate machine code fragment which converts arguments
// from host ABI to guest ABI, calls 'guest_runner' which executes
// guest code at 'pc', and converts results back to the host ABI.
// 'guest_runner' usually invokes binary translation engine, to actually
// execute the guest code at 'pc'.
// It allows calling guest functions, as if they were host
// functions, making them suitable arguments for host functions,
// taking callbacks.
void GenWrapGuestFunction(MachineCode* mc,
                          GuestAddr pc,
                          const char* signature,
                          HostCode guest_runner,
                          const char* name);

}  // namespace berberis

#endif  // BERBERIS_CODE_GEN_LIB_GEN_WRAPPER_H_
