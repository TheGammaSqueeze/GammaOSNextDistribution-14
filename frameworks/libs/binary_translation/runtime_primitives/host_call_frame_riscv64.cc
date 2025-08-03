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

#include "berberis/runtime_primitives/host_call_frame.h"

#include <cstdint>

#include "berberis/base/checks.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

GuestAddr ScopedHostCallFrame::g_host_call_frame_guest_pc_ = {};

// For RISC-V, guest function preserves at least sp and returns by jumping
// to address provided in ra. So here ctor emulates the following code:
//
//   # save register to be changed and maintain stack alignment
//   addi sp, sp, -16
//   sd fp, 0(sp)
//   sd ra, 8(sp)
//   addi fp, x0, sp
//
//   <parameters passing happens after ctor, adjusts a0-7, sp>
//
//   ra = 'special-return-addr'  # ensure stop after return and call guest function, as
//   pc = 'pc'                   #   'special-return-addr': jalr ra, 0('pc')
//
// and dtor emulates the following code:
//
//   # restore registers
//   ld fp, 0(sp)
//   ld ra, 8(sp)
//   addi sp, sp, 16
//
ScopedHostCallFrame::ScopedHostCallFrame(CPUState* cpu, GuestAddr pc) : cpu_(cpu) {
  // addi sp, sp, -16
  SetXReg<SP>(*cpu_, GetXReg<SP>(*cpu_) - 16);
  // sd fp, 0(sp)
  // sd ra, 8(sp)
  uint64_t* saved_regs = ToHostAddr<uint64_t>(GetXReg<SP>(*cpu_));
  saved_regs[0] = GetXReg<FP>(*cpu_);
  saved_regs[1] = GetXReg<RA>(*cpu_);
  // addi fp, x0, sp
  SetXReg<FP>(*cpu_, GetXReg<SP>(*cpu_));

  // For safety checks!
  stack_pointer_ = GetXReg<SP>(*cpu_);
  link_register_ = GetXReg<RA>(*cpu_);

  program_counter_ = cpu_->insn_addr;

  // Set pc and ra as for 'jalr ra, <guest>'.
  SetXReg<RA>(*cpu_, g_host_call_frame_guest_pc_);
  cpu_->insn_addr = pc;
}

ScopedHostCallFrame::~ScopedHostCallFrame() {
  // Safety check - returned to correct pc?
  CHECK_EQ(g_host_call_frame_guest_pc_, cpu_->insn_addr);
  // Safety check - guest call didn't preserve sp?
  CHECK_EQ(stack_pointer_, GetXReg<SP>(*cpu_));

  const uint64_t* saved_regs = ToHostAddr<uint64_t>(GetXReg<SP>(*cpu_));
  // ld fp, 0(sp)
  // ld ra, 8(sp)
  SetXReg<FP>(*cpu_, saved_regs[0]);
  SetXReg<RA>(*cpu_, saved_regs[1]);
  // addi sp, sp, 16
  SetXReg<SP>(*cpu_, GetXReg<SP>(*cpu_) + 16);
  cpu_->insn_addr = program_counter_;

  // Safety checks - guest stack was smashed?
  CHECK_EQ(link_register_, GetXReg<RA>(*cpu_));
}

}  // namespace berberis
