/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "berberis/assembler/machine_code.h"

#include <string>

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"

namespace berberis {

uint8_t* MachineCode::AddrOf(uint32_t offset) {
  CHECK_LT(offset, code_.size());
  return &code_[offset];
}

const uint8_t* MachineCode::AddrOf(uint32_t offset) const {
  CHECK_LT(offset, code_.size());
  return &code_[offset];
}

uint32_t MachineCode::Grow(uint32_t count) {
  size_t old_size = code_.size();
  code_.resize(old_size + count);
  return old_size;
}

inline char print_halfbyte(uint8_t b) {
  return b < 0xa ? b + '0' : (b - 0xa) + 'a';
}

void MachineCode::AsString(std::string* result) const {
  for (uint8_t insn : code_) {
    *result += print_halfbyte(insn >> 4);
    *result += print_halfbyte(insn & 0xf);
    *result += ' ';
  }
}

void MachineCode::PerformRelocations(const uint8_t* code, RecoveryMap* recovery_map) {
  for (const auto& rel : relocations_) {
    switch (rel.type) {
      case RelocationType::RelocAbsToDisp32: {
        intptr_t start = reinterpret_cast<intptr_t>(code);
        intptr_t pc = start + rel.pc;
        intptr_t disp = rel.data - pc;
        CHECK(IsInRange<int32_t>(disp));
        *AddrAs<int32_t>(rel.dst) = disp;
        break;
      }
      case RelocationType::RelocRecoveryPoint: {
        uintptr_t start = reinterpret_cast<uintptr_t>(code);
        uintptr_t fault_addr = start + rel.pc;
        uintptr_t recovery_addr = start + rel.data;
        (*recovery_map)[fault_addr] = recovery_addr;
        break;
      }
    }
  }
}

void MachineCode::DumpCode() const {
  std::string code_str;
  AsString(&code_str);
  ALOGE("%s\n", code_str.c_str());
}

}  // namespace berberis
