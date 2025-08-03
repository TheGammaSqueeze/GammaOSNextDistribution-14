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

#ifndef ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_H_
#define ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_H_

#include "asm_support.h"
#include "entrypoints/entrypoint_asm_constants.h"

// Each frame size constant must be a whole multiple of 16 to ensure 16-byte alignment on the stack.

// clang-format off
// S0, S2 - S11, RA, ArtMethod*, and padding,
//            total 8*(11 + 1 + 1 + 1) = 112
#define FRAME_SIZE_SAVE_REFS_ONLY        112

// A0 - A7, FA0 - FA7, total 8*(8 + 8) = 128
#define FRAME_SIZE_SAVE_ARGS_ONLY        128

// FS0 - FS11, S0, S2 - S11, RA, ArtMethod* and padding,
//       total 8*(12 + 11 + 1 + 1 + 1) = 208
#define FRAME_SIZE_SAVE_ALL_CALLEE_SAVES 208

// FA0 - FA7, A1 - A7, S0, S2 - S11, RA and ArtMethod*,
//        total 8*(8 + 7 + 11 + 1 + 1) = 224
// Excluded GPRs are: A0 (ArtMethod*), S1/TR (ART thread register).
#define FRAME_SIZE_SAVE_REFS_AND_ARGS    224

// All 32 FPRs, 27 GPRs and ArtMethod*,
//               total 8*(32 + 27 + 1) = 480
// Excluded GPRs are: SP, Zero, TP, GP, S1/TR (ART thread register).
#define FRAME_SIZE_SAVE_EVERYTHING       480

// FS0 - FS11, S0, S2 - S11, RA,
//           total 8*(12 + 1 + 10 + 1) = 192
#define NTERP_SIZE_SAVE_CALLEE_SAVES     192
// clang-format on

#endif  // ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_H_
