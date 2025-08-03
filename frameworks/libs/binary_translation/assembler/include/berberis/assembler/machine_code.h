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

//
// Generated machine code.

#ifndef BERBERIS_ASSEMBLER_MACHINE_CODE_H_
#define BERBERIS_ASSEMBLER_MACHINE_CODE_H_

#include <cstdint>
#include <string>

#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/exec_region_anonymous.h"
#include "berberis/base/forever_map.h"
#include "berberis/base/macros.h"  // DISALLOW_COPY_AND_ASSIGN

namespace berberis {

enum class RelocationType {
  // Convert absolute address to PC-relative displacement.
  // Ensure displacement fits in 32-bit value.
  RelocAbsToDisp32,
  // Add recovery point and recovery code to global recovery code map.
  // TODO(eaeltsin): have recovery map for each region instead!
  RelocRecoveryPoint,
};

using RecoveryMap = ForeverMap<uintptr_t, uintptr_t>;

// Generated machine code for host architecture. Used by trampolines
// and JIT translator.
// NOTE: this class is not intended for concurrent usage by multiple threads.
class MachineCode {
 public:
  MachineCode() : code_(&arena_), relocations_(&arena_) {
    // The amount is chosen according to the performance of spec2000 benchmarks.
    code_.reserve(1024);
  }

  Arena* arena() { return &arena_; }

  // TODO(eaeltsin): this will include const pool size when supported.
  [[nodiscard]] uint32_t install_size() const { return code_.size(); }

  [[nodiscard]] uint32_t code_offset() const { return code_.size(); }

  template <typename T>
  T* AddrAs(uint32_t offset) {
    return reinterpret_cast<T*>(AddrOf(offset));
  }

  template <typename T>
  [[nodiscard]] const T* AddrAs(uint32_t offset) const {
    return reinterpret_cast<const T*>(AddrOf(offset));
  }

  template <typename T>
  void Add(const T& v) {
    memcpy(AddrAs<T>(Grow(sizeof(T))), &v, sizeof(T));
  }

  template <typename T>
  void AddSequence(const T* v, uint32_t count) {
    memcpy(AddrAs<T>(Grow(sizeof(T) * count)), v, sizeof(T) * count);
  }

  void AddU8(uint8_t v) { code_.push_back(v); }

  void AsString(std::string* result) const;

  void AddRelocation(uint32_t dst, RelocationType type, uint32_t pc, intptr_t data) {
    relocations_.push_back(Relocation{dst, type, pc, data});
  }

  // Install to executable memory.
  template <typename ExecRegionType>
  void Install(ExecRegionType* exec, const uint8_t* code, RecoveryMap* recovery_map) {
    PerformRelocations(code, recovery_map);
    exec->Write(code, AddrAs<uint8_t>(0), code_.size());
  }

  // Install to writable memory.
  void InstallUnsafe(uint8_t* code, RecoveryMap* recovery_map) {
    PerformRelocations(code, recovery_map);
    memcpy(code, AddrAs<uint8_t>(0), code_.size());
  }

  // Print generated code to stderr.
  void DumpCode() const;

 private:
  struct Relocation {
    uint32_t dst;
    RelocationType type;
    uint32_t pc;
    intptr_t data;
  };
  using RelocationList = ArenaVector<Relocation>;

  [[nodiscard]] uint8_t* AddrOf(uint32_t offset);
  [[nodiscard]] const uint8_t* AddrOf(uint32_t offset) const;
  uint32_t Grow(uint32_t count);

  // Relocate the code, in assumption it is to be installed at address 'code'.
  void PerformRelocations(const uint8_t* code, RecoveryMap* recovery_map);

  Arena arena_;
  ArenaVector<uint8_t> code_;
  RelocationList relocations_;

  DISALLOW_COPY_AND_ASSIGN(MachineCode);
};

}  // namespace berberis

#endif  // BERBERIS_ASSEMBLER_MACHINE_CODE_H_
