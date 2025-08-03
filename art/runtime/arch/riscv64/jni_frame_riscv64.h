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

#ifndef ART_RUNTIME_ARCH_RISCV64_JNI_FRAME_RISCV64_H_
#define ART_RUNTIME_ARCH_RISCV64_JNI_FRAME_RISCV64_H_

#include <string.h>

#include "arch/instruction_set.h"
#include "base/bit_utils.h"
#include "base/globals.h"
#include "base/logging.h"
#include "base/macros.h"

namespace art HIDDEN {
namespace riscv64 {

constexpr size_t kFramePointerSize = static_cast<size_t>(PointerSize::k64);
static_assert(kRiscv64PointerSize == PointerSize::k64, "Unexpected RISCV64 pointer size");

// The RISCV64 requires 16-byte alignment. This is the same as the Managed ABI stack alignment.
static constexpr size_t kNativeStackAlignment = 16u;
static_assert(kNativeStackAlignment == kStackAlignment);

// Up to how many float-like (float, double) args can be in FP registers.
// The rest of the args must go to general purpose registers (native ABI only) or on the stack.
constexpr size_t kMaxFloatOrDoubleArgumentRegisters = 8u;
// Up to how many integer-like (pointers, objects, longs, int, short, bool, etc) args can be
// in registers. The rest of the args must go on the stack. Note that even FP args can use these
// registers in native ABI after using all FP arg registers. We do not pass FP args in registers in
// managed ABI to avoid some complexity in the compiler - more than 8 FP args are quite rare anyway.
constexpr size_t kMaxIntLikeArgumentRegisters = 8u;

// Get the size of the arguments for a native call.
inline size_t GetNativeOutArgsSize(size_t num_fp_args, size_t num_non_fp_args) {
  // Account for FP arguments passed through FA0-FA7.
  size_t num_fp_args_without_fprs =
      num_fp_args - std::min(kMaxFloatOrDoubleArgumentRegisters, num_fp_args);
  // All other args are passed through A0-A7 (even FP args) and the stack.
  size_t num_gpr_and_stack_args = num_non_fp_args + num_fp_args_without_fprs;
  size_t num_stack_args =
      num_gpr_and_stack_args - std::min(kMaxIntLikeArgumentRegisters, num_gpr_and_stack_args);
  // Each stack argument takes 8 bytes.
  return num_stack_args * static_cast<size_t>(kRiscv64PointerSize);
}

// Get stack args size for @CriticalNative method calls.
inline size_t GetCriticalNativeCallArgsSize(const char* shorty, uint32_t shorty_len) {
  DCHECK_EQ(shorty_len, strlen(shorty));

  size_t num_fp_args =
      std::count_if(shorty + 1, shorty + shorty_len, [](char c) { return c == 'F' || c == 'D'; });
  size_t num_non_fp_args = shorty_len - 1u - num_fp_args;

  return GetNativeOutArgsSize(num_fp_args, num_non_fp_args);
}

// Get the frame size for @CriticalNative method stub.
// This must match the size of the extra frame emitted by the compiler at the native call site.
inline size_t GetCriticalNativeStubFrameSize(const char* shorty, uint32_t shorty_len) {
  // The size of outgoing arguments.
  size_t size = GetCriticalNativeCallArgsSize(shorty, shorty_len);

  // We can make a tail call if there are no stack args. Otherwise, add space for return PC.
  // Note: Result does not neeed to be zero- or sign-extended.
  if (size != 0u) {
    size += kFramePointerSize;  // We need to spill RA with the args.
  }
  return RoundUp(size, kNativeStackAlignment);
}

// Get the frame size for direct call to a @CriticalNative method.
// This must match the size of the frame emitted by the JNI compiler at the native call site.
inline size_t GetCriticalNativeDirectCallFrameSize(const char* shorty, uint32_t shorty_len) {
  // The size of outgoing arguments.
  size_t size = GetCriticalNativeCallArgsSize(shorty, shorty_len);

  // No return PC to save.
  return RoundUp(size, kNativeStackAlignment);
}

}  // namespace riscv64
}  // namespace art

#endif  // ART_RUNTIME_ARCH_RISCV64_JNI_FRAME_RISCV64_H_
