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

#ifndef BERBERIS_BACKEND_CODE_EMITTER_H_
#define BERBERIS_BACKEND_CODE_EMITTER_H_

#include <cstdint>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/arena_vector.h"
#include "berberis/intrinsics/macro_assembler.h"

#if defined(__x86_64__)

#include "berberis/assembler/x86_64.h"
using CodeEmitterBase = berberis::MacroAssembler<berberis::x86_64::Assembler>;

#elif defined(__i386__)

#include "berberis/assembler/x86_32.h"
using CodeEmitterBase = berberis::MacroAssembler<berberis::x86_32::Assembler>;

#else

#error "Unsupported architecture"

#endif  // defined(__x86_64__)

namespace berberis {

class CompilerHooks;

class CodeEmitter : public CodeEmitterBase {
 public:
  CodeEmitter(MachineCode* mc, uint32_t frame_size)
      : CodeEmitterBase(mc),
        frame_size_{frame_size},
        next_label_{nullptr},
        exit_label_for_testing_{nullptr},
        labels_(nullptr) {}

  CodeEmitter(MachineCode* mc, uint32_t frame_size, size_t max_ids, Arena* arena)
      : CodeEmitterBase(mc),
        frame_size_{frame_size},
        next_label_{nullptr},
        exit_label_for_testing_{nullptr},
        labels_(max_ids, nullptr, arena) {}

  void set_next_label(const Label* label) { next_label_ = label; }

  [[nodiscard]] const Label* next_label() const { return next_label_; }

  [[nodiscard]] Label* GetLabelAt(int id) {
    if (labels_.at(id) == nullptr) {
      labels_.at(id) = MakeLabel();
    }

    return labels_.at(id);
  }

  [[nodiscard]] uint32_t frame_size() const { return frame_size_; }

  void set_exit_label_for_testing(const Label* label) { exit_label_for_testing_ = label; }

  [[nodiscard]] const Label* exit_label_for_testing() const { return exit_label_for_testing_; }

 private:
  const uint32_t frame_size_;

  // Used by PseudoBranch and PseudoCondBranch to avoid emitting jumps
  // to the next instruction.
  const Label* next_label_;

  // We use it in tests to avoid exiting using runtime library.
  const Label* exit_label_for_testing_;

  // The vector of labels indexed by integer IDs. The IDs are most
  // likely basic block IDs, but we don't really care exactly what
  // they are.
  ArenaVector<Label*> labels_;
};

}  // namespace berberis

#endif  // BERBERIS_BACKEND_CODE_EMITTER_H_
