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

#ifndef BERBERIS_BACKEND_X86_64_INSN_FOLDING_H_
#define BERBERIS_BACKEND_X86_64_INSN_FOLDING_H_

#include <tuple>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/arena_vector.h"

namespace berberis::x86_64 {

// The DefMap class stores a map between registers and their latest definitions and positions.
class DefMap {
 public:
  DefMap(size_t size, Arena* arena)
      : def_map_(size, {nullptr, 0}, arena), flags_reg_(kInvalidMachineReg), index_(0) {}
  [[nodiscard]] std::pair<const MachineInsn*, int> Get(MachineReg reg) const {
    if (!reg.IsVReg()) {
      return {nullptr, 0};
    }
    return def_map_.at(reg.GetVRegIndex());
  }
  [[nodiscard]] std::pair<const MachineInsn*, int> Get(MachineReg reg, int use_index) const {
    if (!reg.IsVReg()) {
      return {nullptr, 0};
    }
    auto [def_insn, def_insn_index] = def_map_.at(reg.GetVRegIndex());
    if (!def_insn || def_insn_index > use_index) {
      return {nullptr, 0};
    }
    return {def_insn, def_insn_index};
  }
  void ProcessInsn(const MachineInsn* insn);
  void Initialize();

 private:
  void Set(MachineReg reg, const MachineInsn* insn) {
    if (reg.IsVReg()) {
      def_map_.at(reg.GetVRegIndex()) = std::pair(insn, index_);
    }
  }
  void MapDefRegs(const MachineInsn* insn);
  ArenaVector<std::pair<const MachineInsn*, int>> def_map_;
  MachineReg flags_reg_;
  int index_;
};

class InsnFolding {
 public:
  explicit InsnFolding(DefMap& def_map, MachineIR* machine_ir)
      : def_map_(def_map), machine_ir_(machine_ir) {}

  std::tuple<bool, MachineInsn*> TryFoldInsn(const MachineInsn* insn);

 private:
  DefMap& def_map_;
  MachineIR* machine_ir_;
  bool IsRegImm(MachineReg reg, uint64_t* imm) const;
  bool IsWritingSameFlagsValue(const MachineInsn* insn) const;
  template <bool is_input_64bit>
  std::tuple<bool, MachineInsn*> TryFoldImmediateInput(const MachineInsn* insn);
  std::tuple<bool, MachineInsn*> TryFoldRedundantMovl(const MachineInsn* insn);
  MachineInsn* NewImmInsnFromRegInsn(const MachineInsn* insn, int32_t imm);
};

void FoldInsns(MachineIR* machine_ir);

void FoldWriteFlags(MachineIR* machine_ir);

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_INSN_FOLDING_H_
