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

#ifndef BERBERIS_CODE_GEN_LIB_CODE_GEN_LIB_H_
#define BERBERIS_CODE_GEN_LIB_CODE_GEN_LIB_H_

#include "berberis/guest_state/guest_addr.h"

#if defined(__i386__)
#include "berberis/assembler/x86_32.h"
#endif

#if defined(__x86_64__)
#include "berberis/assembler/x86_64.h"
#endif

namespace berberis {

#if defined(__i386__)
namespace x86_32 {

void EmitAllocStackFrame(Assembler* as, uint32_t frame_size);
void EmitFreeStackFrame(Assembler* as, uint32_t frame_size);

void EmitJump(Assembler* as, GuestAddr target);
void EmitIndirectJump(Assembler* as, Assembler::Register target);

}  // namespace x86_32
#endif

#if defined(__x86_64__)
void EmitSyscall(x86_64::Assembler* as, GuestAddr pc);
void EmitDirectDispatch(x86_64::Assembler* as, GuestAddr pc, bool check_pending_signals = true);
void EmitIndirectDispatch(x86_64::Assembler* as, x86_64::Assembler::Register target);
void EmitExitGeneratedCode(x86_64::Assembler* as, x86_64::Assembler::Register target);
void EmitAllocStackFrame(x86_64::Assembler* as, uint32_t frame_size);
void EmitFreeStackFrame(x86_64::Assembler* as, uint32_t frame_size);
#endif

}  // namespace berberis

#endif  // BERBERIS_CODE_GEN_LIB_CODE_GEN_LIB_H_
