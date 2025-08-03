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

#ifndef BERBERIS_BACKEND_X86_64_INSN_FOLDING_RENAME_REGS_H_
#define BERBERIS_BACKEND_X86_64_INSN_FOLDING_RENAME_REGS_H_

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/arena_vector.h"

namespace berberis {

namespace x86_64 {

void RenameVRegsLocal(MachineIR* machine_ir);

}  // namespace x86_64

}  // namespace berberis

#endif  // BERBERIS_BACKEND_X86_64_INSN_FOLDING_RENAME_REGS_H_
