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

#ifndef ART_COMPILER_UTILS_RISCV64_MANAGED_REGISTER_RISCV64_H_
#define ART_COMPILER_UTILS_RISCV64_MANAGED_REGISTER_RISCV64_H_

#include <android-base/logging.h>

#include "arch/riscv64/registers_riscv64.h"
#include "base/globals.h"
#include "base/macros.h"
#include "utils/managed_register.h"

namespace art HIDDEN {
namespace riscv64 {

const int kNumberOfXRegIds = kNumberOfXRegisters;
const int kNumberOfXAllocIds = kNumberOfXRegisters;

const int kNumberOfFRegIds = kNumberOfFRegisters;
const int kNumberOfFAllocIds = kNumberOfFRegisters;

const int kNumberOfRegIds = kNumberOfXRegIds + kNumberOfFRegIds;
const int kNumberOfAllocIds = kNumberOfXAllocIds + kNumberOfFAllocIds;

// Register ids map:
//   [0..R[  core registers (enum XRegister)
//   [R..F[  floating-point registers (enum FRegister)
// where
//   R = kNumberOfXRegIds
//   F = R + kNumberOfFRegIds

// An instance of class 'ManagedRegister' represents a single Riscv64 register.
// A register can be one of the following:
//  * core register (enum XRegister)
//  * floating-point register (enum FRegister)
//
// 'ManagedRegister::NoRegister()' provides an invalid register.
// There is a one-to-one mapping between ManagedRegister and register id.
class Riscv64ManagedRegister : public ManagedRegister {
 public:
  constexpr XRegister AsXRegister() const {
    CHECK(IsXRegister());
    return static_cast<XRegister>(id_);
  }

  constexpr FRegister AsFRegister() const {
    CHECK(IsFRegister());
    return static_cast<FRegister>(id_ - kNumberOfXRegIds);
  }

  constexpr bool IsXRegister() const {
    CHECK(IsValidManagedRegister());
    return (0 <= id_) && (id_ < kNumberOfXRegIds);
  }

  constexpr bool IsFRegister() const {
    CHECK(IsValidManagedRegister());
    const int test = id_ - kNumberOfXRegIds;
    return (0 <= test) && (test < kNumberOfFRegIds);
  }

  void Print(std::ostream& os) const;

  // Returns true if the two managed-registers ('this' and 'other') overlap.
  // Either managed-register may be the NoRegister. If both are the NoRegister
  // then false is returned.
  bool Overlaps(const Riscv64ManagedRegister& other) const;

  static constexpr Riscv64ManagedRegister FromXRegister(XRegister r) {
    CHECK_NE(r, kNoXRegister);
    return FromRegId(r);
  }

  static constexpr Riscv64ManagedRegister FromFRegister(FRegister r) {
    CHECK_NE(r, kNoFRegister);
    return FromRegId(r + kNumberOfXRegIds);
  }

 private:
  constexpr bool IsValidManagedRegister() const { return (0 <= id_) && (id_ < kNumberOfRegIds); }

  constexpr int RegId() const {
    CHECK(!IsNoRegister());
    return id_;
  }

  int AllocId() const {
    CHECK(IsValidManagedRegister());
    CHECK_LT(id_, kNumberOfAllocIds);
    return id_;
  }

  int AllocIdLow() const;
  int AllocIdHigh() const;

  friend class ManagedRegister;

  explicit constexpr Riscv64ManagedRegister(int reg_id) : ManagedRegister(reg_id) {}

  static constexpr Riscv64ManagedRegister FromRegId(int reg_id) {
    Riscv64ManagedRegister reg(reg_id);
    CHECK(reg.IsValidManagedRegister());
    return reg;
  }
};

std::ostream& operator<<(std::ostream& os, const Riscv64ManagedRegister& reg);

}  // namespace riscv64

constexpr inline riscv64::Riscv64ManagedRegister ManagedRegister::AsRiscv64() const {
  riscv64::Riscv64ManagedRegister reg(id_);
  CHECK(reg.IsNoRegister() || reg.IsValidManagedRegister());
  return reg;
}

}  // namespace art

#endif  // ART_COMPILER_UTILS_RISCV64_MANAGED_REGISTER_RISCV64_H_
