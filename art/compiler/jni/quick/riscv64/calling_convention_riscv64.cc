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

#include "calling_convention_riscv64.h"

#include <android-base/logging.h>

#include "arch/instruction_set.h"
#include "arch/riscv64/jni_frame_riscv64.h"
#include "utils/riscv64/managed_register_riscv64.h"

namespace art HIDDEN {
namespace riscv64 {

static constexpr ManagedRegister kXArgumentRegisters[] = {
    Riscv64ManagedRegister::FromXRegister(A0),
    Riscv64ManagedRegister::FromXRegister(A1),
    Riscv64ManagedRegister::FromXRegister(A2),
    Riscv64ManagedRegister::FromXRegister(A3),
    Riscv64ManagedRegister::FromXRegister(A4),
    Riscv64ManagedRegister::FromXRegister(A5),
    Riscv64ManagedRegister::FromXRegister(A6),
    Riscv64ManagedRegister::FromXRegister(A7),
};
static_assert(kMaxIntLikeArgumentRegisters == arraysize(kXArgumentRegisters));

static const FRegister kFArgumentRegisters[] = {
  FA0, FA1, FA2, FA3, FA4, FA5, FA6, FA7
};
static_assert(kMaxFloatOrDoubleArgumentRegisters == arraysize(kFArgumentRegisters));

static constexpr ManagedRegister kCalleeSaveRegisters[] = {
    // Core registers.
    Riscv64ManagedRegister::FromXRegister(S0),
    // ART thread register (TR = S1) is not saved on the stack.
    Riscv64ManagedRegister::FromXRegister(S2),
    Riscv64ManagedRegister::FromXRegister(S3),
    Riscv64ManagedRegister::FromXRegister(S4),
    Riscv64ManagedRegister::FromXRegister(S5),
    Riscv64ManagedRegister::FromXRegister(S6),
    Riscv64ManagedRegister::FromXRegister(S7),
    Riscv64ManagedRegister::FromXRegister(S8),
    Riscv64ManagedRegister::FromXRegister(S9),
    Riscv64ManagedRegister::FromXRegister(S10),
    Riscv64ManagedRegister::FromXRegister(S11),
    Riscv64ManagedRegister::FromXRegister(RA),

    // Hard float registers.
    Riscv64ManagedRegister::FromFRegister(FS0),
    Riscv64ManagedRegister::FromFRegister(FS1),
    Riscv64ManagedRegister::FromFRegister(FS2),
    Riscv64ManagedRegister::FromFRegister(FS3),
    Riscv64ManagedRegister::FromFRegister(FS4),
    Riscv64ManagedRegister::FromFRegister(FS5),
    Riscv64ManagedRegister::FromFRegister(FS6),
    Riscv64ManagedRegister::FromFRegister(FS7),
    Riscv64ManagedRegister::FromFRegister(FS8),
    Riscv64ManagedRegister::FromFRegister(FS9),
    Riscv64ManagedRegister::FromFRegister(FS10),
    Riscv64ManagedRegister::FromFRegister(FS11),
};

template <size_t size>
static constexpr uint32_t CalculateCoreCalleeSpillMask(
    const ManagedRegister (&callee_saves)[size]) {
  uint32_t result = 0u;
  for (auto&& r : callee_saves) {
    if (r.AsRiscv64().IsXRegister()) {
      result |= (1u << r.AsRiscv64().AsXRegister());
    }
  }
  return result;
}

template <size_t size>
static constexpr uint32_t CalculateFpCalleeSpillMask(const ManagedRegister (&callee_saves)[size]) {
  uint32_t result = 0u;
  for (auto&& r : callee_saves) {
    if (r.AsRiscv64().IsFRegister()) {
      result |= (1u << r.AsRiscv64().AsFRegister());
    }
  }
  return result;
}

static constexpr uint32_t kCoreCalleeSpillMask = CalculateCoreCalleeSpillMask(kCalleeSaveRegisters);
static constexpr uint32_t kFpCalleeSpillMask = CalculateFpCalleeSpillMask(kCalleeSaveRegisters);

static constexpr ManagedRegister kNativeCalleeSaveRegisters[] = {
    // Core registers.
    Riscv64ManagedRegister::FromXRegister(S0),
    Riscv64ManagedRegister::FromXRegister(S1),
    Riscv64ManagedRegister::FromXRegister(S2),
    Riscv64ManagedRegister::FromXRegister(S3),
    Riscv64ManagedRegister::FromXRegister(S4),
    Riscv64ManagedRegister::FromXRegister(S5),
    Riscv64ManagedRegister::FromXRegister(S6),
    Riscv64ManagedRegister::FromXRegister(S7),
    Riscv64ManagedRegister::FromXRegister(S8),
    Riscv64ManagedRegister::FromXRegister(S9),
    Riscv64ManagedRegister::FromXRegister(S10),
    Riscv64ManagedRegister::FromXRegister(S11),
    Riscv64ManagedRegister::FromXRegister(RA),

    // Hard float registers.
    Riscv64ManagedRegister::FromFRegister(FS0),
    Riscv64ManagedRegister::FromFRegister(FS1),
    Riscv64ManagedRegister::FromFRegister(FS2),
    Riscv64ManagedRegister::FromFRegister(FS3),
    Riscv64ManagedRegister::FromFRegister(FS4),
    Riscv64ManagedRegister::FromFRegister(FS5),
    Riscv64ManagedRegister::FromFRegister(FS6),
    Riscv64ManagedRegister::FromFRegister(FS7),
    Riscv64ManagedRegister::FromFRegister(FS8),
    Riscv64ManagedRegister::FromFRegister(FS9),
    Riscv64ManagedRegister::FromFRegister(FS10),
    Riscv64ManagedRegister::FromFRegister(FS11),
};

static constexpr uint32_t kNativeCoreCalleeSpillMask =
    CalculateCoreCalleeSpillMask(kNativeCalleeSaveRegisters);
static constexpr uint32_t kNativeFpCalleeSpillMask =
    CalculateFpCalleeSpillMask(kNativeCalleeSaveRegisters);

static ManagedRegister ReturnRegisterForShorty(const char* shorty) {
  if (shorty[0] == 'F' || shorty[0] == 'D') {
    return Riscv64ManagedRegister::FromFRegister(FA0);
  } else if (shorty[0] == 'V') {
    return Riscv64ManagedRegister::NoRegister();
  } else {
    // All other return types use A0. Note that there is no managed type wide enough to use A1/FA1.
    return Riscv64ManagedRegister::FromXRegister(A0);
  }
}

// Managed runtime calling convention

ManagedRegister Riscv64ManagedRuntimeCallingConvention::ReturnRegister() const {
  return ReturnRegisterForShorty(GetShorty());
}

ManagedRegister Riscv64ManagedRuntimeCallingConvention::MethodRegister() {
  return Riscv64ManagedRegister::FromXRegister(A0);
}

ManagedRegister Riscv64ManagedRuntimeCallingConvention::ArgumentRegisterForMethodExitHook() {
  DCHECK(!Riscv64ManagedRegister::FromXRegister(A4).Overlaps(ReturnRegister().AsRiscv64()));
  return Riscv64ManagedRegister::FromXRegister(A4);
}

bool Riscv64ManagedRuntimeCallingConvention::IsCurrentParamInRegister() {
  // Note: The managed ABI does not pass FP args in general purpose registers.
  // This differs from the native ABI which does that after using all FP arg registers.
  if (IsCurrentParamAFloatOrDouble()) {
    return itr_float_and_doubles_ < kMaxFloatOrDoubleArgumentRegisters;
  } else {
    size_t non_fp_arg_number = itr_args_ - itr_float_and_doubles_;
    return /* method */ 1u + non_fp_arg_number < kMaxIntLikeArgumentRegisters;
  }
}

bool Riscv64ManagedRuntimeCallingConvention::IsCurrentParamOnStack() {
  return !IsCurrentParamInRegister();
}

ManagedRegister Riscv64ManagedRuntimeCallingConvention::CurrentParamRegister() {
  DCHECK(IsCurrentParamInRegister());
  if (IsCurrentParamAFloatOrDouble()) {
    return Riscv64ManagedRegister::FromFRegister(kFArgumentRegisters[itr_float_and_doubles_]);
  } else {
    size_t non_fp_arg_number = itr_args_ - itr_float_and_doubles_;
    return kXArgumentRegisters[/* method */ 1u + non_fp_arg_number];
  }
}

FrameOffset Riscv64ManagedRuntimeCallingConvention::CurrentParamStackOffset() {
  return FrameOffset(displacement_.Int32Value() +  // displacement
                     kFramePointerSize +  // Method ref
                     (itr_slots_ * sizeof(uint32_t)));  // offset into in args
}

// JNI calling convention

Riscv64JniCallingConvention::Riscv64JniCallingConvention(bool is_static,
                                                         bool is_synchronized,
                                                         bool is_fast_native,
                                                         bool is_critical_native,
                                                         const char* shorty)
    : JniCallingConvention(is_static,
                           is_synchronized,
                           is_fast_native,
                           is_critical_native,
                           shorty,
                           kRiscv64PointerSize) {
}

ManagedRegister Riscv64JniCallingConvention::ReturnRegister() const {
  return ReturnRegisterForShorty(GetShorty());
}

ManagedRegister Riscv64JniCallingConvention::IntReturnRegister() const {
  return Riscv64ManagedRegister::FromXRegister(A0);
}

size_t Riscv64JniCallingConvention::FrameSize() const {
  if (is_critical_native_) {
    CHECK(!SpillsMethod());
    CHECK(!HasLocalReferenceSegmentState());
    return 0u;  // There is no managed frame for @CriticalNative.
  }

  // Method*, callee save area size, local reference segment state
  DCHECK(SpillsMethod());
  size_t method_ptr_size = static_cast<size_t>(kFramePointerSize);
  size_t callee_save_area_size = CalleeSaveRegisters().size() * kFramePointerSize;
  size_t total_size = method_ptr_size + callee_save_area_size;

  DCHECK(HasLocalReferenceSegmentState());
  // Cookie is saved in one of the spilled registers.

  return RoundUp(total_size, kStackAlignment);
}

size_t Riscv64JniCallingConvention::OutFrameSize() const {
  // Count param args, including JNIEnv* and jclass*.
  size_t all_args = NumberOfExtraArgumentsForJni() + NumArgs();
  size_t num_fp_args = NumFloatOrDoubleArgs();
  DCHECK_GE(all_args, num_fp_args);
  size_t num_non_fp_args = all_args - num_fp_args;
  // The size of outgoing arguments.
  size_t size = GetNativeOutArgsSize(num_fp_args, num_non_fp_args);

  // @CriticalNative can use tail call as all managed callee saves are preserved by AAPCS64.
  static_assert((kCoreCalleeSpillMask & ~kNativeCoreCalleeSpillMask) == 0u);
  static_assert((kFpCalleeSpillMask & ~kNativeFpCalleeSpillMask) == 0u);

  // For @CriticalNative, we can make a tail call if there are no stack args.
  // Otherwise, add space for return PC.
  // Note: Result does not neeed to be zero- or sign-extended.
  DCHECK(!RequiresSmallResultTypeExtension());
  if (is_critical_native_ && size != 0u) {
    size += kFramePointerSize;  // We need to spill RA with the args.
  }
  size_t out_args_size = RoundUp(size, kNativeStackAlignment);
  if (UNLIKELY(IsCriticalNative())) {
    DCHECK_EQ(out_args_size, GetCriticalNativeStubFrameSize(GetShorty(), NumArgs() + 1u));
  }
  return out_args_size;
}

ArrayRef<const ManagedRegister> Riscv64JniCallingConvention::CalleeSaveRegisters() const {
  if (UNLIKELY(IsCriticalNative())) {
    if (UseTailCall()) {
      return ArrayRef<const ManagedRegister>();  // Do not spill anything.
    } else {
      // Spill RA with out args.
      static_assert((kCoreCalleeSpillMask & (1 << RA)) != 0u);  // Contains RA.
      constexpr size_t ra_index = POPCOUNT(kCoreCalleeSpillMask) - 1u;
      static_assert(kCalleeSaveRegisters[ra_index].Equals(
                        Riscv64ManagedRegister::FromXRegister(RA)));
      return ArrayRef<const ManagedRegister>(kCalleeSaveRegisters).SubArray(
          /*pos=*/ ra_index, /*length=*/ 1u);
    }
  } else {
    return ArrayRef<const ManagedRegister>(kCalleeSaveRegisters);
  }
}

ArrayRef<const ManagedRegister> Riscv64JniCallingConvention::CalleeSaveScratchRegisters() const {
  DCHECK(!IsCriticalNative());
  // Use S3-S11 from managed callee saves. All these registers are also native callee saves.
  constexpr size_t kStart = 2u;
  constexpr size_t kLength = 9u;
  static_assert(kCalleeSaveRegisters[kStart].Equals(Riscv64ManagedRegister::FromXRegister(S3)));
  static_assert(kCalleeSaveRegisters[kStart + kLength - 1u].Equals(
                    Riscv64ManagedRegister::FromXRegister(S11)));
  static_assert((kCoreCalleeSpillMask & ~kNativeCoreCalleeSpillMask) == 0u);
  return ArrayRef<const ManagedRegister>(kCalleeSaveRegisters).SubArray(kStart, kLength);
}

ArrayRef<const ManagedRegister> Riscv64JniCallingConvention::ArgumentScratchRegisters() const {
  DCHECK(!IsCriticalNative());
  ArrayRef<const ManagedRegister> scratch_regs(kXArgumentRegisters);
  // Exclude return register (A0) even if unused. Using the same scratch registers helps
  // making more JNI stubs identical for better reuse, such as deduplicating them in oat files.
  static_assert(kXArgumentRegisters[0].Equals(Riscv64ManagedRegister::FromXRegister(A0)));
  scratch_regs = scratch_regs.SubArray(/*pos=*/ 1u);
  DCHECK(std::none_of(scratch_regs.begin(),
                      scratch_regs.end(),
                      [return_reg = ReturnRegister().AsRiscv64()](ManagedRegister reg) {
                        return return_reg.Overlaps(reg.AsRiscv64());
                      }));
  return scratch_regs;
}

uint32_t Riscv64JniCallingConvention::CoreSpillMask() const {
  return is_critical_native_ ? 0u : kCoreCalleeSpillMask;
}

uint32_t Riscv64JniCallingConvention::FpSpillMask() const {
  return is_critical_native_ ? 0u : kFpCalleeSpillMask;
}

size_t Riscv64JniCallingConvention::CurrentParamSize() const {
  if (IsCurrentArgExtraForJni()) {
    return static_cast<size_t>(frame_pointer_size_);  // JNIEnv or jobject/jclass
  } else {
    size_t arg_pos = GetIteratorPositionWithinShorty();
    DCHECK_LT(arg_pos, NumArgs());
    if (IsStatic()) {
      ++arg_pos;  // 0th argument must skip return value at start of the shorty
    } else if (arg_pos == 0) {
      return static_cast<size_t>(kRiscv64PointerSize);  // this argument
    }
    // The riscv64 native calling convention specifies that integers narrower than XLEN (64)
    // bits are "widened according to the sign of their type up to 32 bits, then sign-extended
    // to XLEN bits." Thus, everything other than `float` (which has the high 32 bits undefined)
    // is passed as 64 bits, whether in register, or on the stack.
    return (GetShorty()[arg_pos] == 'F') ? 4u : static_cast<size_t>(kRiscv64PointerSize);
  }
}

bool Riscv64JniCallingConvention::IsCurrentParamInRegister() {
  // FP args use FPRs, then GPRs and only then the stack.
  if (itr_float_and_doubles_ < kMaxFloatOrDoubleArgumentRegisters) {
    if (IsCurrentParamAFloatOrDouble()) {
      return true;
    } else {
      size_t num_non_fp_args = itr_args_ - itr_float_and_doubles_;
      return num_non_fp_args < kMaxIntLikeArgumentRegisters;
    }
  } else {
    return (itr_args_ < kMaxFloatOrDoubleArgumentRegisters + kMaxIntLikeArgumentRegisters);
  }
}

bool Riscv64JniCallingConvention::IsCurrentParamOnStack() {
  return !IsCurrentParamInRegister();
}

ManagedRegister Riscv64JniCallingConvention::CurrentParamRegister() {
  // FP args use FPRs, then GPRs and only then the stack.
  CHECK(IsCurrentParamInRegister());
  if (itr_float_and_doubles_ < kMaxFloatOrDoubleArgumentRegisters) {
    if (IsCurrentParamAFloatOrDouble()) {
      return Riscv64ManagedRegister::FromFRegister(kFArgumentRegisters[itr_float_and_doubles_]);
    } else {
      size_t num_non_fp_args = itr_args_ - itr_float_and_doubles_;
      DCHECK_LT(num_non_fp_args, kMaxIntLikeArgumentRegisters);
      return kXArgumentRegisters[num_non_fp_args];
    }
  } else {
    // This argument is in a GPR, whether it's a FP arg or a non-FP arg.
    DCHECK_LT(itr_args_, kMaxFloatOrDoubleArgumentRegisters + kMaxIntLikeArgumentRegisters);
    return kXArgumentRegisters[itr_args_ - kMaxFloatOrDoubleArgumentRegisters];
  }
}

FrameOffset Riscv64JniCallingConvention::CurrentParamStackOffset() {
  CHECK(IsCurrentParamOnStack());
  // Account for FP arguments passed through FA0-FA7.
  // All other args are passed through A0-A7 (even FP args) and the stack.
  size_t num_gpr_and_stack_args =
      itr_args_ - std::min<size_t>(kMaxFloatOrDoubleArgumentRegisters, itr_float_and_doubles_);
  size_t args_on_stack =
      num_gpr_and_stack_args - std::min(kMaxIntLikeArgumentRegisters, num_gpr_and_stack_args);
  size_t offset = displacement_.Int32Value() - OutFrameSize() + (args_on_stack * kFramePointerSize);
  CHECK_LT(offset, OutFrameSize());
  return FrameOffset(offset);
}

bool Riscv64JniCallingConvention::RequiresSmallResultTypeExtension() const {
  // RISC-V native calling convention requires values to be returned the way that the first
  // argument would be passed. Arguments are zero-/sign-extended to 32 bits based on their
  // type, then sign-extended to 64 bits. This is the same as in the ART mamaged ABI.
  // (Not applicable to FP args which are returned in `FA0`. A `float` is NaN-boxed.)
  return false;
}

// T0 is neither managed callee-save, nor argument register. It is suitable for use as the
// locking argument for synchronized methods and hidden argument for @CriticalNative methods.
static void AssertT0IsNeitherCalleeSaveNorArgumentRegister() {
  // TODO: Change to static_assert; std::none_of should be constexpr since C++20.
  DCHECK(std::none_of(kCalleeSaveRegisters,
                      kCalleeSaveRegisters + std::size(kCalleeSaveRegisters),
                      [](ManagedRegister callee_save) constexpr {
                        return callee_save.Equals(Riscv64ManagedRegister::FromXRegister(T0));
                      }));
  DCHECK(std::none_of(kXArgumentRegisters,
                      kXArgumentRegisters + std::size(kXArgumentRegisters),
                      [](ManagedRegister arg) { return arg.AsRiscv64().AsXRegister() == T0; }));
}

ManagedRegister Riscv64JniCallingConvention::LockingArgumentRegister() const {
  DCHECK(!IsFastNative());
  DCHECK(!IsCriticalNative());
  DCHECK(IsSynchronized());
  AssertT0IsNeitherCalleeSaveNorArgumentRegister();
  return Riscv64ManagedRegister::FromXRegister(T0);
}

ManagedRegister Riscv64JniCallingConvention::HiddenArgumentRegister() const {
  DCHECK(IsCriticalNative());
  AssertT0IsNeitherCalleeSaveNorArgumentRegister();
  return Riscv64ManagedRegister::FromXRegister(T0);
}

// Whether to use tail call (used only for @CriticalNative).
bool Riscv64JniCallingConvention::UseTailCall() const {
  CHECK(IsCriticalNative());
  return OutFrameSize() == 0u;
}

}  // namespace riscv64
}  // namespace art
