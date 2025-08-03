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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_CONTEXT_ARCH_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_CONTEXT_ARCH_H_

#include <cstdint>
#include <cstring>  // memcpy

#include "berberis/base/checks.h"
#include "berberis/base/struct_check.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

class GuestContext {
 public:
  GuestContext() = default;
  GuestContext(const GuestContext&) = delete;
  GuestContext& operator=(const GuestContext&) = delete;

  void Save(const CPUState* cpu) {
    // Save everything.
    cpu_ = *cpu;

    // Save context.
    memset(&ctx_, 0, sizeof(ctx_));
    static_assert(sizeof(cpu->x) == sizeof(ctx_.uc_mcontext.sc_regs));
    memcpy(&ctx_.uc_mcontext.sc_regs, cpu->x, sizeof(ctx_.uc_mcontext.sc_regs));
    // Use double FP state since GuestState supports both F and D extensions using 64-bit registers.
    static_assert(sizeof(cpu->f) == sizeof(ctx_.uc_mcontext.sc_fpregs.d.f));
    memcpy(ctx_.uc_mcontext.sc_fpregs.d.f, cpu->f, sizeof(ctx_.uc_mcontext.sc_fpregs.d.f));
    ctx_.uc_mcontext.sc_regs.pc = cpu->insn_addr;
  }

  void Restore(CPUState* cpu) const {
    // Restore everything.
    *cpu = cpu_;

    // Overwrite from context.
    memcpy(cpu->x, &ctx_.uc_mcontext.sc_regs, sizeof(ctx_.uc_mcontext.sc_regs));
    memcpy(cpu->f, ctx_.uc_mcontext.sc_fpregs.d.f, sizeof(ctx_.uc_mcontext.sc_fpregs.d.f));
    cpu->insn_addr = ctx_.uc_mcontext.sc_regs.pc;
  }

  void* ptr() { return &ctx_; }

 private:
  // See bionic/libc/kernel/uapi/asm-riscv/asm/ptrace.h
  struct Guest_user_regs_struct {
    uint64_t pc;
    uint64_t ra;
    uint64_t sp;
    uint64_t gp;
    uint64_t tp;
    uint64_t t0;
    uint64_t t1;
    uint64_t t2;
    uint64_t s0;
    uint64_t s1;
    uint64_t a0;
    uint64_t a1;
    uint64_t a2;
    uint64_t a3;
    uint64_t a4;
    uint64_t a5;
    uint64_t a6;
    uint64_t a7;
    uint64_t s2;
    uint64_t s3;
    uint64_t s4;
    uint64_t s5;
    uint64_t s6;
    uint64_t s7;
    uint64_t s8;
    uint64_t s9;
    uint64_t s10;
    uint64_t s11;
    uint64_t t3;
    uint64_t t4;
    uint64_t t5;
    uint64_t t6;
  };
  struct Guest__riscv_f_ext_state {
    uint32_t f[32];
    uint32_t fcsr;
  };
  struct Guest__riscv_d_ext_state {
    uint64_t f[32];
    uint32_t fcsr;
  };
  struct Guest__riscv_q_ext_state {
    uint64_t f[64] __attribute__((aligned(16)));
    uint32_t fcsr;
    uint32_t reserved[3];
  };
  union Guest__riscv_fp_state {
    struct Guest__riscv_f_ext_state f;
    struct Guest__riscv_d_ext_state d;
    struct Guest__riscv_q_ext_state q;
  };

  // See bionic/libc/kernel/uapi/asm-riscv/asm/sigcontext.h
  struct Guest_sigcontext {
    struct Guest_user_regs_struct sc_regs;
    union Guest__riscv_fp_state sc_fpregs;
  };

  // See bionic/libc/kernel/uapi/asm-riscv/asm/ucontext.h
  struct Guest_ucontext {
    uint64_t uc_flags;
    Guest_ucontext* uc_link;
    // We assume guest stack_t is compatible with host (see RunGuestSyscall___NR_sigaltstack).
    stack_t uc_stack;
    Guest_sigset_t uc_sigmask;
    uint8_t __linux_unused[1024 / 8 - sizeof(Guest_sigset_t)];
    Guest_sigcontext uc_mcontext;
  };

  CHECK_STRUCT_LAYOUT(Guest_ucontext, 7680, 128);
  CHECK_FIELD_LAYOUT(Guest_ucontext, uc_flags, 0, 64);
  CHECK_FIELD_LAYOUT(Guest_ucontext, uc_link, 64, 64);
  CHECK_FIELD_LAYOUT(Guest_ucontext, uc_stack, 128, 192);
  // Bionic RISC-V sigset_t is 64 bits (generic implementation).
  CHECK_FIELD_LAYOUT(Guest_ucontext, uc_sigmask, 320, 64);
  CHECK_FIELD_LAYOUT(Guest_ucontext, uc_mcontext, 1408, 6272);

  Guest_ucontext ctx_;
  CPUState cpu_;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_CONTEXT_ARCH_H_