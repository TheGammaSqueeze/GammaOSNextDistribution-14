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

#ifndef ART_RUNTIME_ARCH_RISCV64_CONTEXT_RISCV64_H_
#define ART_RUNTIME_ARCH_RISCV64_CONTEXT_RISCV64_H_

#include <android-base/logging.h>

#include <cstdlib>

#include "arch/context.h"
#include "base/macros.h"
#include "registers_riscv64.h"

namespace art HIDDEN {
namespace riscv64 {

class Riscv64Context final : public Context {
 public:
  Riscv64Context() { Reset(); }

  ~Riscv64Context() {}

  void Reset() override;

  void FillCalleeSaves(uint8_t* frame, const QuickMethodFrameInfo& fr) override;

  void SetSP(uintptr_t new_sp) override { SetGPR(SP, new_sp); }

  void SetPC(uintptr_t new_pc) override { SetGPR(kPC, new_pc); }

  void SetNterpDexPC(uintptr_t dex_pc_ptr) override { SetGPR(S3, dex_pc_ptr); }

  void SetArg0(uintptr_t new_arg0_value) override { SetGPR(A0, new_arg0_value); }

  bool IsAccessibleGPR(uint32_t reg) override {
    DCHECK_LT(reg, arraysize(gprs_));
    return gprs_[reg] != nullptr;
  }

  uintptr_t* GetGPRAddress(uint32_t reg) override {
    DCHECK_LT(reg, arraysize(gprs_));
    return gprs_[reg];
  }

  uintptr_t GetGPR(uint32_t reg) override {
    // Note: PC isn't an available GPR (outside of internals), so don't allow retrieving the value.
    DCHECK_LT(reg, static_cast<uint32_t>(kNumberOfXRegisters));
    DCHECK(IsAccessibleGPR(reg));
    return *gprs_[reg];
  }

  void SetGPR(uint32_t reg, uintptr_t value) override;

  bool IsAccessibleFPR(uint32_t reg) override {
    DCHECK_LT(reg, static_cast<uint32_t>(kNumberOfFRegisters));
    return fprs_[reg] != nullptr;
  }

  uintptr_t GetFPR(uint32_t reg) override {
    DCHECK_LT(reg, static_cast<uint32_t>(kNumberOfFRegisters));
    DCHECK(IsAccessibleFPR(reg));
    return *fprs_[reg];
  }

  void SetFPR(uint32_t reg, uintptr_t value) override;

  void SmashCallerSaves() override;
  NO_RETURN void DoLongJump() override;

  static constexpr size_t kPC = kNumberOfXRegisters;

 private:
  // Pointers to register locations, initialized to null or the specific registers below. We need
  // an additional one for the PC.
  uintptr_t* gprs_[kNumberOfXRegisters + 1];
  uint64_t* fprs_[kNumberOfFRegisters];
  // Hold values for sp, pc and arg0 if they are not located within a stack frame.
  uintptr_t sp_, pc_, arg0_;
};

}  // namespace riscv64
}  // namespace art

#endif  // ART_RUNTIME_ARCH_RISCV64_CONTEXT_RISCV64_H_
