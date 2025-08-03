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

#ifndef BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_TEST_CHECKS_H_
#define BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_TEST_CHECKS_H_

#include "gtest/gtest.h"

#include "berberis/backend/x86_64/loop_guest_context_optimizer.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/checks.h"

namespace berberis::x86_64 {

inline MachineReg CheckCopyGetInsnAndObtainMappedReg(MachineInsn* get_insn,
                                                     MachineReg expected_dst) {
  EXPECT_EQ(get_insn->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(get_insn->RegAt(0), expected_dst);
  return get_insn->RegAt(1);
}

inline MachineReg CheckCopyPutInsnAndObtainMappedReg(MachineInsn* put_insn,
                                                     MachineReg expected_src) {
  EXPECT_EQ(put_insn->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(put_insn->RegAt(1), expected_src);
  return put_insn->RegAt(0);
}

inline void CheckMemRegMap(MemRegMap mem_reg_map,
                           size_t offset,
                           MachineReg mapped_reg,
                           MovType mov_type,
                           bool is_modified) {
  EXPECT_TRUE(mem_reg_map[offset].has_value());
  EXPECT_EQ(mem_reg_map[offset].value().reg, mapped_reg);
  EXPECT_EQ(mem_reg_map[offset].value().mov_type, mov_type);
  EXPECT_EQ(mem_reg_map[offset].value().is_modified, is_modified);
}

inline void CheckGetInsn(MachineInsn* insn, MachineOpcode opcode, MachineReg reg, size_t disp) {
  auto get_insn = AsMachineInsnX86_64(insn);
  EXPECT_TRUE(get_insn->IsCPUStateGet());
  EXPECT_EQ(get_insn->opcode(), opcode);
  EXPECT_EQ(get_insn->RegAt(0), reg);
  EXPECT_EQ(get_insn->disp(), disp);
}

inline void CheckPutInsn(MachineInsn* insn, MachineOpcode opcode, MachineReg reg, size_t disp) {
  auto put_insn = AsMachineInsnX86_64(insn);
  EXPECT_TRUE(put_insn->IsCPUStatePut());
  EXPECT_EQ(put_insn->opcode(), opcode);
  EXPECT_EQ(put_insn->RegAt(1), reg);
  EXPECT_EQ(put_insn->disp(), disp);
}

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_TEST_CHECKS_H_
