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

#ifndef RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_H_
#define RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_H_

#include <limits.h>
#include <type_traits>  // is_same_v

#include <functional>
#include <tuple>
#include <utility>

#include "berberis/intrinsics/intrinsics_float.h"
#include "berberis/intrinsics/macro_assembler_constants_pool.h"

namespace berberis {

template <typename Assembler>
class MacroAssembler : public Assembler {
 public:
  using MacroAssemblers = std::tuple<MacroAssembler<Assembler>>;

  template <typename... Args>
  explicit MacroAssembler(Args&&... args) : Assembler(std::forward<Args>(args)...) {
  }

#define DEFINE_MACRO_ASSEMBLER_GENERIC_FUNCTIONS
#include "berberis/intrinsics/macro_assembler-inl.h"

  void PNot(XMMRegister result) {
    Pandn(result, {.disp = constants_pool::kVectorConst<uint8_t{0b1111'1111}>});
  }

  void Vpnot(XMMRegister result, XMMRegister src) {
    Vpandn(result, src, {.disp = constants_pool::kVectorConst<uint8_t{0b1111'1111}>});
  }

#include "berberis/intrinsics/macro_assembler_interface-inl.h"  // NOLINT generated file

  using Assembler::Bind;
  using Assembler::Btq;
  using Assembler::Fldcw;
  using Assembler::Fldenv;
  using Assembler::Fnstcw;
  using Assembler::Fnstenv;
  using Assembler::Fnstsw;
  using Assembler::Jcc;
  using Assembler::Ldmxcsr;
  using Assembler::Leal;
  using Assembler::Leaq;
  using Assembler::MakeLabel;
  using Assembler::Movl;
  using Assembler::Pand;
  using Assembler::Pandn;
  using Assembler::Pcmpeqb;
  using Assembler::Pmov;
  using Assembler::Por;
  using Assembler::Pshufd;
  using Assembler::Setcc;
  using Assembler::Stmxcsr;
  using Assembler::Vpand;
  using Assembler::Vpandn;
  using Assembler::Vpcmpeqb;
  using Assembler::Vpor;
  using Assembler::Vpshufd;

  using Assembler::gpr_a;
  using Assembler::gpr_c;
  using Assembler::gpr_d;

 private:

  // Useful constants for PshufXXX instructions.
  enum {
    kShuffleDDBB = 0b11110101
  };
};

}  // namespace berberis

// Macro specializations.
#include "berberis/intrinsics/macro_assembler_bitmanip_impl.h"
#include "berberis/intrinsics/macro_assembler_floating_point_impl.h"

#endif  // RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_H_
