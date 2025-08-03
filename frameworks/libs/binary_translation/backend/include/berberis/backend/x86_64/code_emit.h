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

// Do not import directly. Only intended to be used by generated code.

#ifndef BERBERIS_BACKEND_X86_64_CODE_EMIT_H_
#define BERBERIS_BACKEND_X86_64_CODE_EMIT_H_

#include "berberis/assembler/x86_64.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/intrinsics/intrinsics_float.h"

namespace berberis::x86_64 {

Assembler::Register GetGReg(MachineReg r);
Assembler::XMMRegister GetXReg(MachineReg r);
Assembler::ScaleFactor ToScaleFactor(MachineMemOperandScale scale);

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_CODE_EMIT_H_
