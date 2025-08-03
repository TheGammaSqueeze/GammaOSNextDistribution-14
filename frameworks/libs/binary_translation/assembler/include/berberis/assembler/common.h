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

#ifndef BERBERIS_ASSEMBLER_COMMON_H_
#define BERBERIS_ASSEMBLER_COMMON_H_

#include <stdint.h>

#include <string>
#include <utility>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"
#include "berberis/base/macros.h"  // DISALLOW_IMPLICIT_CONSTRUCTORS

namespace berberis {

class AssemblerBase {
 public:
  explicit AssemblerBase(MachineCode* code) : jumps_(code->arena()), code_(code) {}

  ~AssemblerBase() {}

  class Label {
   public:
    Label() : position_(kInvalid) {}

    // Label position is offset from the start of MachineCode
    // where it is bound to.
    uint32_t position() const { return position_; }

    void Bind(uint32_t position) {
      CHECK(!IsBound());
      position_ = position;
    }

    bool IsBound() const { return position_ != kInvalid; }

   protected:
    static const uint32_t kInvalid = 0xffffffff;
    uint32_t position_;

   private:
    DISALLOW_COPY_AND_ASSIGN(Label);
  };

  uint32_t pc() const { return code_->code_offset(); }

  // Macro operations.
  void Emit8(uint8_t v) { code_->AddU8(v); }

  void Emit16(int16_t v) { code_->Add<int16_t>(v); }

  void Emit32(int32_t v) { code_->Add<int32_t>(v); }

  void Emit64(int64_t v) { code_->Add<int64_t>(v); }

  template <typename T>
  void EmitSequence(const T* v, uint32_t count) {
    code_->AddSequence(v, sizeof(T) * count);
  }

  void Bind(Label* label) { label->Bind(pc()); }

  Label* MakeLabel() { return NewInArena<Label>(code_->arena()); }

  void SetRecoveryPoint(Label* recovery_label) {
    jumps_.push_back(Jump{recovery_label, pc(), true});
  }

 protected:
  template <typename T>
  T* AddrAs(uint32_t offset) {
    return code_->AddrAs<T>(offset);
  }

  void AddRelocation(uint32_t dst, RelocationType type, uint32_t pc, intptr_t data) {
    code_->AddRelocation(dst, type, pc, data);
  }

  // These are 'static' relocations, resolved when code is finalized.
  // We also have 'dynamic' relocations, resolved when code is installed.
  struct Jump {
    const Label* label;
    // Position of field to store offset.  Note: unless it's recovery label precomputed
    // "distance from the end of instruction" is stored there.
    //
    // This is needed because we keep pointer to the rip-offset field while value stored
    // there is counted from the end of instruction (on x86) or, sometimes, from the end
    // of next instruction (ARM).
    uint32_t pc;
    bool is_recovery;
  };
  using JumpList = ArenaVector<Jump>;
  JumpList jumps_;

 private:
  MachineCode* code_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(AssemblerBase);
};

}  // namespace berberis

#endif  // BERBERIS_ASSEMBLER_COMMON_H_
