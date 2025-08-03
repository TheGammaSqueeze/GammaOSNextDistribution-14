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

#ifndef BERBERIS_BACKEND_X86_64_VREG_BIT_SET_H_
#define BERBERIS_BACKEND_X86_64_VREG_BIT_SET_H_

#include <cstddef>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/arena_vector.h"

namespace berberis::x86_64 {

// TODO(b/179708579): Use something with fast bitwise operators like std::bitset,
// but with the dynamic size.
class VRegBitSet {
 public:
  VRegBitSet(size_t size, Arena* arena) : bit_set_(size, false, arena) {}

  void Set(MachineReg reg) { bit_set_[reg.GetVRegIndex()] = true; }
  void Reset(MachineReg reg) { bit_set_[reg.GetVRegIndex()] = false; }
  void Clear() { bit_set_.clear(); }
  size_t Size() const { return bit_set_.size(); }

  bool operator[](MachineReg reg) const { return bit_set_[reg.GetVRegIndex()]; }

  VRegBitSet& operator|=(const VRegBitSet& other) {
    CHECK_EQ(this->bit_set_.size(), other.bit_set_.size());
    for (size_t i = 0; i < this->bit_set_.size(); i++) {
      this->bit_set_[i] = this->bit_set_[i] || other.bit_set_[i];
    }
    return *this;
  }

  bool operator!=(const VRegBitSet& other) const { return this->bit_set_ != other.bit_set_; }

 private:
  ArenaVector<bool> bit_set_;
};

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_VREG_BIT_SET_H_
