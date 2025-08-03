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

#include "gtest/gtest.h"

#include <cstdint>
#include <initializer_list>
#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/lite_translator/lite_translate_region.h"
#include "berberis/runtime_primitives/memory_region_reservation.h"
#include "berberis/test_utils/scoped_exec_region.h"
#include "berberis/test_utils/testing_run_generated_code.h"

#include "riscv64_to_x86_64/lite_translator.h"

namespace berberis {

namespace {

template <uint8_t kInsnSize = 4>
bool RunOneInstruction(ThreadState* state, GuestAddr stop_pc) {
  MachineCode machine_code;
  bool success = LiteTranslateRange(state->cpu.insn_addr,
                                    state->cpu.insn_addr + kInsnSize,
                                    &machine_code,
                                    LiteTranslateParams{.allow_dispatch = false});

  if (!success) {
    return false;
  }

  ScopedExecRegion exec(&machine_code);

  TestingRunGeneratedCode(state, exec.get(), stop_pc);
  return true;
}

#define TESTSUITE Riscv64LiteTranslateInsnTest
#define TESTING_LITE_TRANSLATOR

#include "berberis/test_utils/insn_tests_riscv64-inl.h"

#undef TESTING_LITE_TRANSLATOR
#undef TESTSUITE

}  // namespace

}  // namespace berberis
