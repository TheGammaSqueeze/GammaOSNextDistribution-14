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

#include "context_riscv64.h"

#include <stdint.h>

#include "base/bit_utils.h"
#include "base/bit_utils_iterator.h"
#include "quick/quick_method_frame_info.h"
#include "thread-current-inl.h"

extern "C" __attribute__((weak)) void __hwasan_handle_longjmp(const void* sp_dst);

namespace art HIDDEN {
namespace riscv64 {

static constexpr uint64_t gZero = 0;

void Riscv64Context::Reset() {
  std::fill_n(gprs_, arraysize(gprs_), nullptr);
  std::fill_n(fprs_, arraysize(fprs_), nullptr);
  gprs_[SP] = &sp_;
  gprs_[kPC] = &pc_;
  gprs_[A0] = &arg0_;
  // Initialize registers with easy to spot debug values.
  sp_ = kBadGprBase + SP;
  pc_ = kBadGprBase + kPC;
  arg0_ = 0;
}

void Riscv64Context::FillCalleeSaves(uint8_t* frame, const QuickMethodFrameInfo& frame_info) {
  // RA is at top of the frame
  DCHECK_NE(frame_info.CoreSpillMask() & (1u << RA), 0u);
  gprs_[RA] = CalleeSaveAddress(frame, 0, frame_info.FrameSizeInBytes());

  // Core registers come first, from the highest down to the lowest, with the exception of RA/X1.
  int spill_pos = 1;
  for (uint32_t core_reg : HighToLowBits(frame_info.CoreSpillMask() & ~(1u << RA))) {
    gprs_[core_reg] = CalleeSaveAddress(frame, spill_pos, frame_info.FrameSizeInBytes());
    ++spill_pos;
  }
  DCHECK_EQ(spill_pos, POPCOUNT(frame_info.CoreSpillMask()));

  // FP registers come second, from the highest down to the lowest.
  for (uint32_t fp_reg : HighToLowBits(frame_info.FpSpillMask())) {
    fprs_[fp_reg] = CalleeSaveAddress(frame, spill_pos, frame_info.FrameSizeInBytes());
    ++spill_pos;
  }
  DCHECK_EQ(spill_pos, POPCOUNT(frame_info.CoreSpillMask()) + POPCOUNT(frame_info.FpSpillMask()));
}

void Riscv64Context::SetGPR(uint32_t reg, uintptr_t value) {
  DCHECK_LT(reg, arraysize(gprs_));
  DCHECK_NE(reg, static_cast<uint32_t>(Zero));  // Zero/X0 is immutable (hard-wired zero)
  DCHECK(IsAccessibleGPR(reg));
  DCHECK_NE(gprs_[reg], &gZero);  // Can't overwrite this static value since they are never reset.
  *gprs_[reg] = value;
}

void Riscv64Context::SetFPR(uint32_t reg, uintptr_t value) {
  DCHECK_LT(reg, static_cast<uint32_t>(kNumberOfFRegisters));
  DCHECK(IsAccessibleFPR(reg));
  DCHECK_NE(fprs_[reg], &gZero);  // Can't overwrite this static value since they are never reset.
  *fprs_[reg] = value;
}

void Riscv64Context::SmashCallerSaves() {
  // Temporary registers T0 - T6 and argument registers A0 - A7 are caller-saved.
  gprs_[Zero] = const_cast<uint64_t*>(&gZero);  // hard-wired zero
  gprs_[T0] = nullptr;
  gprs_[T1] = nullptr;
  gprs_[T2] = nullptr;
  gprs_[T3] = nullptr;
  gprs_[T4] = nullptr;
  gprs_[T5] = nullptr;
  gprs_[T6] = nullptr;
  gprs_[A0] = const_cast<uint64_t*>(&gZero);  // must be 0 because we want a null/zero return value
  gprs_[A1] = nullptr;
  gprs_[A2] = nullptr;
  gprs_[A3] = nullptr;
  gprs_[A4] = nullptr;
  gprs_[A5] = nullptr;
  gprs_[A6] = nullptr;
  gprs_[A7] = nullptr;

  // Temporary registers FT0 - FT11 and argument registers FA0 - FA7 are caller-saved.
  fprs_[FT0] = nullptr;
  fprs_[FT1] = nullptr;
  fprs_[FT2] = nullptr;
  fprs_[FT3] = nullptr;
  fprs_[FT4] = nullptr;
  fprs_[FT5] = nullptr;
  fprs_[FT6] = nullptr;
  fprs_[FT7] = nullptr;
  fprs_[FT8] = nullptr;
  fprs_[FT9] = nullptr;
  fprs_[FT10] = nullptr;
  fprs_[FT11] = nullptr;
  fprs_[FA0] = nullptr;
  fprs_[FA1] = nullptr;
  fprs_[FA2] = nullptr;
  fprs_[FA3] = nullptr;
  fprs_[FA4] = nullptr;
  fprs_[FA5] = nullptr;
  fprs_[FA6] = nullptr;
  fprs_[FA7] = nullptr;
}

extern "C" NO_RETURN void art_quick_do_long_jump(uint64_t*, uint64_t*);

void Riscv64Context::DoLongJump() {
  uint64_t gprs[arraysize(gprs_)];
  uint64_t fprs[kNumberOfFRegisters];

  // The long jump routine called below expects to find the value for SP at index 2.
  DCHECK_EQ(SP, 2);

  for (size_t i = 0; i < arraysize(gprs_); ++i) {
    gprs[i] = gprs_[i] != nullptr ? *gprs_[i] : kBadGprBase + i;
  }
  for (size_t i = 0; i < kNumberOfFRegisters; ++i) {
    fprs[i] = fprs_[i] != nullptr ? *fprs_[i] : kBadFprBase + i;
  }

  // Fill in TR (the ART Thread Register) with the address of the current thread.
  gprs[TR] = reinterpret_cast<uintptr_t>(Thread::Current());

  // Tell HWASan about the new stack top.
  if (__hwasan_handle_longjmp != nullptr) {
    __hwasan_handle_longjmp(reinterpret_cast<void*>(gprs[SP]));
  }
  art_quick_do_long_jump(gprs, fprs);
}

}  // namespace riscv64
}  // namespace art
