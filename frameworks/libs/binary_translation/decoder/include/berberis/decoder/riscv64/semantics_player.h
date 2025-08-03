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

#ifndef BERBERIS_DECODER_RISCV64_SEMANTICS_PLAYER_H_
#define BERBERIS_DECODER_RISCV64_SEMANTICS_PLAYER_H_

#include "berberis/base/overloaded.h"
#include "berberis/decoder/riscv64/decoder.h"

namespace berberis {

// This class expresses the semantics of instructions by calling a sequence of SemanticsListener
// callbacks.
template <class SemanticsListener>
class SemanticsPlayer {
 public:
  using CsrName = typename SemanticsListener::CsrName;
  using Decoder = Decoder<SemanticsPlayer>;
  using Register = typename SemanticsListener::Register;
  using Float32 = typename SemanticsListener::Float32;
  using Float64 = typename SemanticsListener::Float64;
  using FpRegister = typename SemanticsListener::FpRegister;

  explicit SemanticsPlayer(SemanticsListener* listener) : listener_(listener) {}

  // Decoder's InsnConsumer implementation.

  void Amo(const typename Decoder::AmoArgs& args) {
    Register arg1 = GetRegOrZero(args.src1);
    Register arg2 = GetRegOrZero(args.src2);
    Register result;
    switch (args.operand_type) {
      case Decoder::MemoryDataOperandType::k32bit:
        result = Amo<int32_t>(args.opcode, arg1, arg2, args.aq, args.rl);
        break;
      case Decoder::MemoryDataOperandType::k64bit:
        result = Amo<int64_t>(args.opcode, arg1, arg2, args.aq, args.rl);
        break;
      default:
        Unimplemented();
        return;
    }
    SetRegOrIgnore(args.dst, result);
  }

  template <typename IntType>
  Register Amo(typename Decoder::AmoOpcode opcode, Register arg1, Register arg2, bool aq, bool rl) {
    if (aq) {
      if (rl) {
        return Amo<IntType, true, true>(opcode, arg1, arg2);
      } else {
        return Amo<IntType, true, false>(opcode, arg1, arg2);
      }
    } else {
      if (rl) {
        return Amo<IntType, false, true>(opcode, arg1, arg2);
      } else {
        return Amo<IntType, false, false>(opcode, arg1, arg2);
      }
    }
  }

  template <typename IntType, bool aq, bool rl>
  Register Amo(typename Decoder::AmoOpcode opcode, Register arg1, Register arg2) {
    switch (opcode) {
      case Decoder::AmoOpcode::kLr:
        return listener_->template Lr<IntType, aq, rl>(arg1);
      case Decoder::AmoOpcode::kSc:
        return listener_->template Sc<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmoswap:
        return listener_->template AmoSwap<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmoadd:
        return listener_->template AmoAdd<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmoxor:
        return listener_->template AmoXor<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmoand:
        return listener_->template AmoAnd<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmoor:
        return listener_->template AmoOr<IntType, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmomin:
        return listener_->template AmoMin<std::make_signed_t<IntType>, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmomax:
        return listener_->template AmoMax<std::make_signed_t<IntType>, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmominu:
        return listener_->template AmoMin<std::make_unsigned_t<IntType>, aq, rl>(arg1, arg2);
      case Decoder::AmoOpcode::kAmomaxu:
        return listener_->template AmoMax<std::make_unsigned_t<IntType>, aq, rl>(arg1, arg2);
      default:
        Unimplemented();
        return {};
    }
  }

  void Auipc(const typename Decoder::UpperImmArgs& args) {
    Register result = listener_->Auipc(args.imm);
    SetRegOrIgnore(args.dst, result);
  }

  void CompareAndBranch(const typename Decoder::BranchArgs& args) {
    Register arg1 = GetRegOrZero(args.src1);
    Register arg2 = GetRegOrZero(args.src2);
    listener_->CompareAndBranch(args.opcode, arg1, arg2, args.offset);
  }

  void Csr(const typename Decoder::CsrArgs& args) {
    if (args.opcode == Decoder::CsrOpcode::kCsrrw) {
      if (args.dst != 0) {
        auto [csr_supported, csr] = GetCsr(static_cast<CsrName>(args.csr));
        if (!csr_supported) {
          return Unimplemented();
        }
        Register arg = listener_->GetReg(args.src);
        SetCsr(static_cast<CsrName>(args.csr), arg);
        listener_->SetReg(args.dst, csr);
        return;
      }
      Register arg = listener_->GetReg(args.src);
      if (!SetCsr(static_cast<CsrName>(args.csr), arg)) {
        return Unimplemented();
      }
      return;
    }
    auto [csr_supported, csr] = GetCsr(static_cast<CsrName>(args.csr));
    if (!csr_supported) {
      return Unimplemented();
    }
    if (args.src != 0) {
      Register arg = listener_->GetReg(args.src);
      if (!SetCsr(static_cast<CsrName>(args.csr), listener_->UpdateCsr(args.opcode, arg, csr))) {
        return Unimplemented();
      }
    }
    SetRegOrIgnore(args.dst, csr);
  }

  void Csr(const typename Decoder::CsrImmArgs& args) {
    if (args.opcode == Decoder::CsrImmOpcode::kCsrrwi) {
      if (args.dst != 0) {
        auto [csr_supported, csr] = GetCsr(static_cast<CsrName>(args.csr));
        if (!csr_supported) {
          return Unimplemented();
        }
        if (!SetCsr(static_cast<CsrName>(args.csr), csr)) {
          return Unimplemented();
        }
        listener_->SetReg(args.dst, csr);
      }
      SetCsr(static_cast<CsrName>(args.csr), args.imm);
      return;
    }
    auto [csr_supported, csr] = GetCsr(static_cast<CsrName>(args.csr));
    if (!csr_supported) {
      return Unimplemented();
    }
    if (args.imm != 0) {
      if (!SetCsr(static_cast<CsrName>(args.csr),
                  listener_->UpdateCsr(args.opcode, args.imm, csr))) {
        return Unimplemented();
      }
    }
    SetRegOrIgnore(args.dst, csr);
  }

  void Fcvt(const typename Decoder::FcvtFloatToFloatArgs& args) {
    if (args.dst_type == Decoder::FloatOperandType::kFloat &&
        args.src_type == Decoder::FloatOperandType::kDouble) {
      FpRegister arg = GetFRegAndUnboxNan<Float64>(args.src);
      Register frm = listener_->template GetCsr<CsrName::kFrm>();
      FpRegister result = listener_->template FCvtFloatToFloat<Float32, Float64>(args.rm, frm, arg);
      NanBoxAndSetFpReg<Float32>(args.dst, result);
    } else if (args.dst_type == Decoder::FloatOperandType::kDouble &&
               args.src_type == Decoder::FloatOperandType::kFloat) {
      FpRegister arg = GetFRegAndUnboxNan<Float32>(args.src);
      Register frm = listener_->template GetCsr<CsrName::kFrm>();
      FpRegister result = listener_->template FCvtFloatToFloat<Float64, Float32>(args.rm, frm, arg);
      NanBoxAndSetFpReg<Float64>(args.dst, result);
    } else {
      Unimplemented();
      return;
    }
  }

  void Fcvt(const typename Decoder::FcvtFloatToIntegerArgs& args) {
    switch (args.src_type) {
      case Decoder::FloatOperandType::kFloat:
        return FcvtloatToInteger<Float32>(args.dst_type, args.rm, args.dst, args.src);
      case Decoder::FloatOperandType::kDouble:
        return FcvtloatToInteger<Float64>(args.dst_type, args.rm, args.dst, args.src);
      default:
        return Unimplemented();
    }
  }

  template <typename FLoatType>
  void FcvtloatToInteger(typename Decoder::FcvtOperandType dst_type,
                         int8_t rm,
                         int8_t dst,
                         int8_t src) {
    FpRegister arg = GetFRegAndUnboxNan<FLoatType>(src);
    Register frm = listener_->template GetCsr<CsrName::kFrm>();
    Register result;
    switch (dst_type) {
      case Decoder::FcvtOperandType::k32bitSigned:
        result = listener_->template FCvtFloatToInteger<int32_t, FLoatType>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k32bitUnsigned:
        result = listener_->template FCvtFloatToInteger<uint32_t, FLoatType>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k64bitSigned:
        result = listener_->template FCvtFloatToInteger<int64_t, FLoatType>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k64bitUnsigned:
        result = listener_->template FCvtFloatToInteger<uint64_t, FLoatType>(rm, frm, arg);
        break;
      default:
        return Unimplemented();
    }
    SetRegOrIgnore(dst, result);
  }

  void Fcvt(const typename Decoder::FcvtIntegerToFloatArgs& args) {
    switch (args.dst_type) {
      case Decoder::FloatOperandType::kFloat:
        return FcvtIntegerToFloat<Float32>(args.src_type, args.rm, args.dst, args.src);
      case Decoder::FloatOperandType::kDouble:
        return FcvtIntegerToFloat<Float64>(args.src_type, args.rm, args.dst, args.src);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void FcvtIntegerToFloat(typename Decoder::FcvtOperandType src_type,
                          int8_t rm,
                          int8_t dst,
                          int8_t src) {
    Register arg = GetRegOrZero(src);
    Register frm = listener_->template GetCsr<CsrName::kFrm>();
    FpRegister result;
    switch (src_type) {
      case Decoder::FcvtOperandType::k32bitSigned:
        result = listener_->template FCvtIntegerToFloat<FloatType, int32_t>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k32bitUnsigned:
        result = listener_->template FCvtIntegerToFloat<FloatType, uint32_t>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k64bitSigned:
        result = listener_->template FCvtIntegerToFloat<FloatType, int64_t>(rm, frm, arg);
        break;
      case Decoder::FcvtOperandType::k64bitUnsigned:
        result = listener_->template FCvtIntegerToFloat<FloatType, uint64_t>(rm, frm, arg);
        break;
      default:
        Unimplemented();
        return;
    }
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void Fma(const typename Decoder::FmaArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return Fma<Float32>(args.opcode, args.rm, args.dst, args.src1, args.src2, args.src3);
        break;
      case Decoder::FloatOperandType::kDouble:
        return Fma<Float64>(args.opcode, args.rm, args.dst, args.src1, args.src2, args.src3);
        break;
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void Fma(typename Decoder::FmaOpcode opcode,
           int8_t rm,
           int8_t dst,
           int8_t src1,
           int8_t src2,
           int8_t src3) {
    FpRegister arg1 = GetFRegAndUnboxNan<FloatType>(src1);
    FpRegister arg2 = GetFRegAndUnboxNan<FloatType>(src2);
    FpRegister arg3 = GetFRegAndUnboxNan<FloatType>(src3);
    Register frm = listener_->template GetCsr<CsrName::kFrm>();
    FpRegister result;
    switch (opcode) {
      case Decoder::FmaOpcode::kFmadd:
        result = listener_->template FMAdd<FloatType>(rm, frm, arg1, arg2, arg3);
        break;
      case Decoder::FmaOpcode::kFmsub:
        result = listener_->template FMSub<FloatType>(rm, frm, arg1, arg2, arg3);
        break;
      // Note (from RISC-V manual): The FNMSUB and FNMADD instructions are counterintuitively named,
      // owing to the naming of the corresponding instructions in MIPS-IV. The MIPS instructions
      // were defined to negate the sum, rather than negating the product as the RISC-V instructions
      // do, so the naming scheme was more rational at the time. The two definitions differ with
      // respect to signed-zero results. The RISC-V definition matches the behavior of the x86 and
      // ARM fused multiply-add instructions, but unfortunately the RISC-V FNMSUB and FNMADD
      // instruction names are swapped compared to x86 and ARM.
      //
      // Since even official documentation calls the names “counterintuitive” it's better to use x86
      // ones for intrinsics.
      case Decoder::FmaOpcode::kFnmsub:
        result = listener_->template FNMAdd<FloatType>(rm, frm, arg1, arg2, arg3);
        break;
      case Decoder::FmaOpcode::kFnmadd:
        result = listener_->template FNMSub<FloatType>(rm, frm, arg1, arg2, arg3);
        break;
      default:
        return Unimplemented();
    }
    result = CanonicalizeNan<FloatType>(result);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void Fence(const typename Decoder::FenceArgs& args) {
    listener_->Fence(args.opcode,
                     // args.src is currently unused - read below.
                     Register{},
                     args.sw,
                     args.sr,
                     args.so,
                     args.si,
                     args.pw,
                     args.pr,
                     args.po,
                     args.pi);
    // The unused fields in the FENCE instructions — args.src and args.dst — are reserved for
    // finer-grain fences in future extensions. For forward compatibility, base implementations
    // shall ignore these fields, and standard software shall zero these fields. Likewise, many
    // args.opcode and predecessor/successor set settings are also reserved for future use. Base
    // implementations shall treat all such reserved configurations as normal fences with
    // args.opcode=0000, and standard software shall use only non-reserved configurations.
  }

  void FenceI(const typename Decoder::FenceIArgs& args) {
    Register arg = GetRegOrZero(args.src);
    listener_->FenceI(arg, args.imm);
    // The unused fields in the FENCE.I instruction, imm[11:0], rs1, and rd, are reserved for
    // finer-grain fences in future extensions. For forward compatibility, base implementations
    // shall ignore these fields, and standard software shall zero these fields.
  }

  void JumpAndLink(const typename Decoder::JumpAndLinkArgs& args) {
    Register result = listener_->GetImm(listener_->GetInsnAddr() + args.insn_len);
    SetRegOrIgnore(args.dst, result);
    listener_->Branch(args.offset);
  }

  void JumpAndLinkRegister(const typename Decoder::JumpAndLinkRegisterArgs& args) {
    Register base = GetRegOrZero(args.base);
    if (args.base == args.dst) {
      // If base and dst are the same register and the listener implements register mapping
      // SetRegOrIgnore below will overwrite the original base register and make it invalid for
      // BranchRegister call. Note that this issue only exists for JumpAndLinkRegister since we
      // need to write the result before consuming all the arguments.
      base = listener_->Copy(base);
    }
    Register next_insn_addr = listener_->GetImm(listener_->GetInsnAddr() + args.insn_len);
    SetRegOrIgnore(args.dst, next_insn_addr);
    listener_->BranchRegister(base, args.offset);
  }

  void Load(const typename Decoder::LoadArgs& args) {
    Register arg = GetRegOrZero(args.src);
    Register result = listener_->Load(args.operand_type, arg, args.offset);
    SetRegOrIgnore(args.dst, result);
  }

  void Load(const typename Decoder::LoadFpArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return Load<Float32>(args.dst, args.src, args.offset);
      case Decoder::FloatOperandType::kDouble:
        return Load<Float64>(args.dst, args.src, args.offset);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void Load(int8_t dst, int8_t src, int16_t offset) {
    Register arg = GetRegOrZero(src);
    FpRegister result = listener_->template LoadFp<FloatType>(arg, offset);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void Lui(const typename Decoder::UpperImmArgs& args) {
    Register result = listener_->Lui(args.imm);
    SetRegOrIgnore(args.dst, result);
  }

  void Nop() { listener_->Nop(); }

  template <typename OpArgs>
  void Op(OpArgs&& args) {
    Register arg1 = GetRegOrZero(args.src1);
    Register arg2 = GetRegOrZero(args.src2);
    Register result = Overloaded{[&](const typename Decoder::OpArgs& args) {
                                   switch (args.opcode) {
                                     case Decoder::OpOpcode::kMax:
                                       return listener_->template Max<int64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kMaxu:
                                       return listener_->template Max<uint64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kMin:
                                       return listener_->template Min<int64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kMinu:
                                       return listener_->template Min<uint64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kRol:
                                       return listener_->template Rol<int64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kRor:
                                       return listener_->template Ror<int64_t>(arg1, arg2);
                                     case Decoder::OpOpcode::kSh1add:
                                       return listener_->Sh1add(arg1, arg2);
                                     case Decoder::OpOpcode::kSh2add:
                                       return listener_->Sh2add(arg1, arg2);
                                     case Decoder::OpOpcode::kSh3add:
                                       return listener_->Sh3add(arg1, arg2);
                                     case Decoder::OpOpcode::kBclr:
                                       return listener_->Bclr(arg1, arg2);
                                     case Decoder::OpOpcode::kBext:
                                       return listener_->Bext(arg1, arg2);
                                     case Decoder::OpOpcode::kBinv:
                                       return listener_->Binv(arg1, arg2);
                                     case Decoder::OpOpcode::kBset:
                                       return listener_->Bset(arg1, arg2);
                                     default:
                                       return listener_->Op(args.opcode, arg1, arg2);
                                   }
                                 },
                                 [&](const typename Decoder::Op32Args& args) {
                                   switch (args.opcode) {
                                     case Decoder::Op32Opcode::kAdduw:
                                       return listener_->Adduw(arg1, arg2);
                                     case Decoder::Op32Opcode::kRolw:
                                       return listener_->template Rol<int32_t>(arg1, arg2);
                                     case Decoder::Op32Opcode::kRorw:
                                       return listener_->template Ror<int32_t>(arg1, arg2);
                                     case Decoder::Op32Opcode::kSh1adduw:
                                       return listener_->Sh1adduw(arg1, arg2);
                                     case Decoder::Op32Opcode::kSh2adduw:
                                       return listener_->Sh2adduw(arg1, arg2);
                                     case Decoder::Op32Opcode::kSh3adduw:
                                       return listener_->Sh3adduw(arg1, arg2);
                                     default:
                                       return listener_->Op32(args.opcode, arg1, arg2);
                                   }
                                 }}(args);
    SetRegOrIgnore(args.dst, result);
  }

  void OpSingleInput(const typename Decoder::OpSingleInputArgs& args) {
    Register arg = GetRegOrZero(args.src);
    Register result;
    switch (args.opcode) {
      case Decoder::OpSingleInputOpcode::kZexth:
        result = listener_->Zexth(arg);
        break;
      default:
        Unimplemented();
        return;
    }
    SetRegOrIgnore(args.dst, result);
  }

  void OpFp(const typename Decoder::OpFpArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFp<Float32>(args.opcode, args.rm, args.dst, args.src1, args.src2);
      case Decoder::FloatOperandType::kDouble:
        return OpFp<Float64>(args.opcode, args.rm, args.dst, args.src1, args.src2);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFp(typename Decoder::OpFpOpcode opcode, int8_t rm, int8_t dst, int8_t src1, int8_t src2) {
    FpRegister arg1 = GetFRegAndUnboxNan<FloatType>(src1);
    FpRegister arg2 = GetFRegAndUnboxNan<FloatType>(src2);
    Register frm = listener_->template GetCsr<CsrName::kFrm>();
    FpRegister result;
    switch (opcode) {
      case Decoder::OpFpOpcode::kFAdd:
        result = listener_->template FAdd<FloatType>(rm, frm, arg1, arg2);
        break;
      case Decoder::OpFpOpcode::kFSub:
        result = listener_->template FSub<FloatType>(rm, frm, arg1, arg2);
        break;
      case Decoder::OpFpOpcode::kFMul:
        result = listener_->template FMul<FloatType>(rm, frm, arg1, arg2);
        break;
      case Decoder::OpFpOpcode::kFDiv:
        result = listener_->template FDiv<FloatType>(rm, frm, arg1, arg2);
        break;
      default:
        return Unimplemented();
    }
    result = CanonicalizeNan<FloatType>(result);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void OpFpGpRegisterTargetNoRounding(
      const typename Decoder::OpFpGpRegisterTargetNoRoundingArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFpGpRegisterTargetNoRounding<Float32>(args.opcode, args.dst, args.src1, args.src2);
      case Decoder::FloatOperandType::kDouble:
        return OpFpGpRegisterTargetNoRounding<Float64>(args.opcode, args.dst, args.src1, args.src2);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFpGpRegisterTargetNoRounding(typename Decoder::OpFpGpRegisterTargetNoRoundingOpcode opcode,
                                      int8_t dst,
                                      int8_t src1,
                                      int8_t src2) {
    FpRegister arg1 = GetFRegAndUnboxNan<FloatType>(src1);
    FpRegister arg2 = GetFRegAndUnboxNan<FloatType>(src2);
    Register result;
    switch (opcode) {
      case Decoder::OpFpGpRegisterTargetNoRoundingOpcode::kFle:
        result = listener_->template Fle<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpGpRegisterTargetNoRoundingOpcode::kFlt:
        result = listener_->template Flt<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpGpRegisterTargetNoRoundingOpcode::kFeq:
        result = listener_->template Feq<FloatType>(arg1, arg2);
        break;
      default:
        return Unimplemented();
    }
    SetRegOrIgnore(dst, result);
  }

  void OpFpGpRegisterTargetSingleInputNoRounding(
      const typename Decoder::OpFpGpRegisterTargetSingleInputNoRoundingArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFpGpRegisterTargetSingleInputNoRounding<Float32>(args.opcode, args.dst, args.src);
      case Decoder::FloatOperandType::kDouble:
        return OpFpGpRegisterTargetSingleInputNoRounding<Float64>(args.opcode, args.dst, args.src);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFpGpRegisterTargetSingleInputNoRounding(
      typename Decoder::OpFpGpRegisterTargetSingleInputNoRoundingOpcode opcode,
      int8_t dst,
      int8_t src) {
    FpRegister arg = GetFRegAndUnboxNan<FloatType>(src);
    Register result;
    switch (opcode) {
      case Decoder::OpFpGpRegisterTargetSingleInputNoRoundingOpcode::kFclass:
        result = listener_->template FClass<FloatType>(arg);
        break;
      default:
        return Unimplemented();
    }
    SetRegOrIgnore(dst, result);
  }

  void OpFpNoRounding(const typename Decoder::OpFpNoRoundingArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFpNoRounding<Float32>(args.opcode, args.dst, args.src1, args.src2);
      case Decoder::FloatOperandType::kDouble:
        return OpFpNoRounding<Float64>(args.opcode, args.dst, args.src1, args.src2);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFpNoRounding(const typename Decoder::OpFpNoRoundingOpcode opcode,
                      int8_t dst,
                      int8_t src1,
                      int8_t src2) {
    FpRegister arg1 = GetFRegAndUnboxNan<FloatType>(src1);
    FpRegister arg2 = GetFRegAndUnboxNan<FloatType>(src2);
    FpRegister result;
    switch (opcode) {
      case Decoder::OpFpNoRoundingOpcode::kFSgnj:
        result = listener_->template FSgnj<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpNoRoundingOpcode::kFSgnjn:
        result = listener_->template FSgnjn<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpNoRoundingOpcode::kFSgnjx:
        result = listener_->template FSgnjx<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpNoRoundingOpcode::kFMin:
        result = listener_->template FMin<FloatType>(arg1, arg2);
        break;
      case Decoder::OpFpNoRoundingOpcode::kFMax:
        result = listener_->template FMax<FloatType>(arg1, arg2);
        break;
      default:
        Unimplemented();
        return;
    }
    result = CanonicalizeNan<FloatType>(result);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void FmvFloatToInteger(const typename Decoder::FmvFloatToIntegerArgs& args) {
    FpRegister arg = GetFpReg(args.src);
    Register result;
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        result = listener_->template FmvFloatToInteger<int32_t, Float32>(arg);
        break;
      case Decoder::FloatOperandType::kDouble:
        result = listener_->template FmvFloatToInteger<int64_t, Float64>(arg);
        break;
      default:
        Unimplemented();
        return;
    }
    SetRegOrIgnore(args.dst, result);
  }

  void FmvIntegerToFloat(const typename Decoder::FmvIntegerToFloatArgs& args) {
    Register arg = GetRegOrZero(args.src);
    FpRegister result;
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        result = listener_->template FmvIntegerToFloat<Float32, int32_t>(arg);
        NanBoxAndSetFpReg<Float32>(args.dst, result);
        break;
      case Decoder::FloatOperandType::kDouble:
        result = listener_->template FmvIntegerToFloat<Float64, int64_t>(arg);
        NanBoxAndSetFpReg<Float64>(args.dst, result);
        break;
      default:
        Unimplemented();
        return;
    }
  }

  void OpFpSingleInput(const typename Decoder::OpFpSingleInputArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFpSingleInput<Float32>(args.opcode, args.rm, args.dst, args.src);
      case Decoder::FloatOperandType::kDouble:
        return OpFpSingleInput<Float64>(args.opcode, args.rm, args.dst, args.src);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFpSingleInput(typename Decoder::OpFpSingleInputOpcode opcode,
                       int8_t rm,
                       int8_t dst,
                       int8_t src) {
    FpRegister arg = GetFRegAndUnboxNan<FloatType>(src);
    FpRegister result;
    Register frm = listener_->template GetCsr<CsrName::kFrm>();
    switch (opcode) {
      case Decoder::OpFpSingleInputOpcode::kFSqrt:
        result = listener_->template FSqrt<FloatType>(rm, frm, arg);
        break;
      default:
        return Unimplemented();
    }
    result = CanonicalizeNan<FloatType>(result);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  void OpFpSingleInputNoRounding(const typename Decoder::OpFpSingleInputNoRoundingArgs& args) {
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        return OpFpSingleInputNoRounding<Float32>(args.opcode, args.dst, args.src);
      case Decoder::FloatOperandType::kDouble:
        return OpFpSingleInputNoRounding<Float64>(args.opcode, args.dst, args.src);
      default:
        return Unimplemented();
    }
  }

  template <typename FloatType>
  void OpFpSingleInputNoRounding(typename Decoder::OpFpSingleInputNoRoundingOpcode opcode,
                                 int8_t dst,
                                 int8_t src) {
    FpRegister arg = GetFRegAndUnboxNan<FloatType>(src);
    FpRegister result;
    switch (opcode) {
      case Decoder::OpFpSingleInputNoRoundingOpcode::kFmv:
        result = listener_->Fmv(arg);
        break;
      default:
        Unimplemented();
        return;
    }
    result = CanonicalizeNan<FloatType>(result);
    NanBoxAndSetFpReg<FloatType>(dst, result);
  }

  template <typename OpImmArgs>
  void OpImm(OpImmArgs&& args) {
    Register arg = GetRegOrZero(args.src);
    Register result = Overloaded{[&](const typename Decoder::OpImmArgs& args) {
                                   return listener_->OpImm(args.opcode, arg, args.imm);
                                 },
                                 [&](const typename Decoder::OpImm32Args& args) {
                                   return listener_->OpImm32(args.opcode, arg, args.imm);
                                 },
                                 [&](const typename Decoder::ShiftImmArgs& args) {
                                   switch (args.opcode) {
                                     case Decoder::ShiftImmOpcode::kSlli:
                                       return listener_->Slli(arg, args.imm);
                                     case Decoder::ShiftImmOpcode::kSrli:
                                       return listener_->Srli(arg, args.imm);
                                     case Decoder::ShiftImmOpcode::kSrai:
                                       return listener_->Srai(arg, args.imm);
                                     default:
                                       Unimplemented();
                                       return Register{};
                                   }
                                 },
                                 [&](const typename Decoder::ShiftImm32Args& args) {
                                   return listener_->ShiftImm32(args.opcode, arg, args.imm);
                                 },
                                 [&](const typename Decoder::BitmanipImmArgs& args) {
                                   switch (args.opcode) {
                                     case Decoder::BitmanipImmOpcode::kClz:
                                       return listener_->template Clz<int64_t>(arg);
                                     case Decoder::BitmanipImmOpcode::kCpop:
                                       return listener_->template Cpop<int64_t>(arg);
                                     case Decoder::BitmanipImmOpcode::kCtz:
                                       return listener_->template Ctz<int64_t>(arg);
                                     case Decoder::BitmanipImmOpcode::kSextb:
                                       return listener_->template Sext<int8_t>(arg);
                                     case Decoder::BitmanipImmOpcode::kSexth:
                                       return listener_->template Sext<int16_t>(arg);
                                     case Decoder::BitmanipImmOpcode::kOrcb:
                                       return listener_->Orcb(arg);
                                     case Decoder::BitmanipImmOpcode::kRev8:
                                       return listener_->Rev8(arg);
                                     case Decoder::BitmanipImmOpcode::kRori:
                                       return listener_->Rori(arg, args.shamt);
                                     case Decoder::BitmanipImmOpcode::kBclri:
                                       return listener_->Bclri(arg, args.shamt);
                                     case Decoder::BitmanipImmOpcode::kBexti:
                                       return listener_->Bexti(arg, args.shamt);
                                     case Decoder::BitmanipImmOpcode::kBinvi:
                                       return listener_->Binvi(arg, args.shamt);
                                     case Decoder::BitmanipImmOpcode::kBseti:
                                       return listener_->Bseti(arg, args.shamt);
                                     default:
                                       Unimplemented();
                                       return Register{};
                                   }
                                 },
                                 [&](const typename Decoder::BitmanipImm32Args& args) {
                                   switch (args.opcode) {
                                     case Decoder::BitmanipImm32Opcode::kClzw:
                                       return listener_->template Clz<int32_t>(arg);
                                     case Decoder::BitmanipImm32Opcode::kCpopw:
                                       return listener_->template Cpop<int32_t>(arg);
                                     case Decoder::BitmanipImm32Opcode::kCtzw:
                                       return listener_->template Ctz<int32_t>(arg);
                                     case Decoder::BitmanipImm32Opcode::kRoriw:
                                       return listener_->Roriw(arg, args.shamt);
                                     case Decoder::BitmanipImm32Opcode::kSlliuw:
                                       return listener_->Slliuw(arg, args.shamt);
                                     default:
                                       Unimplemented();
                                       return Register{};
                                   }
                                 }}(args);
    SetRegOrIgnore(args.dst, result);
  }

  // TODO(b/300690740): develop and implement strategy which would allow us to support vector
  // intrinsics not just in the interpreter.

  void OpVector(const typename Decoder::VLoadIndexedArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VLoadStrideArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    Register arg3 = GetRegOrZero(args.std);
    listener_->OpVector(args, arg2, arg3);
  }

  void OpVector(const typename Decoder::VLoadUnitStrideArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VOpFVfArgs& args) {
    // Note: we don't have information here to chosee between GetFRegAndUnboxNan<Float32> and
    // GetFRegAndUnboxNan<Float64> because that depends on vtype.
    FpRegister arg2 = GetFpReg(args.src2);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VOpFVvArgs& args) { listener_->OpVector(args); }

  void OpVector(const typename Decoder::VOpIViArgs& args) { listener_->OpVector(args); }

  void OpVector(const typename Decoder::VOpIVvArgs& args) { listener_->OpVector(args); }

  void OpVector(const typename Decoder::VOpMVvArgs& args) { listener_->OpVector(args); }

  void OpVector(const typename Decoder::VOpIVxArgs& args) {
    Register arg2 = GetRegOrZero(args.src2);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VOpMVxArgs& args) {
    Register arg2 = GetRegOrZero(args.src2);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VStoreIndexedArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    listener_->OpVector(args, arg2);
  }

  void OpVector(const typename Decoder::VStoreStrideArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    Register arg3 = GetRegOrZero(args.std);
    listener_->OpVector(args, arg2, arg3);
  }

  void OpVector(const typename Decoder::VStoreUnitStrideArgs& args) {
    Register arg2 = GetRegOrZero(args.src);
    listener_->OpVector(args, arg2);
  }

  void Vsetivli(const typename Decoder::VsetivliArgs& args) {
    // Note: it's unclear whether args.avl should be treated similarly to x0 in Vsetvli or not.
    // Keep implementation separate from Vsetvli to make it easier to adjust that code.
    if (args.avl == 0) {
      if (args.dst == 0) {
        auto [vl_orig, vtype_orig] = GetVlAndVtypeCsr();
        auto [vl, vtype] = listener_->Vtestvli(vl_orig, vtype_orig, args.vtype);
        SetVlAndVtypeCsr(vl, vtype);
      } else {
        auto [vl, vtype] = listener_->Vsetvlimax(args.vtype);
        SetVlAndVtypeCsr(vl, vtype);
        listener_->SetReg(args.dst, vl);
      }
    } else {
      auto [vl, vtype] = listener_->Vsetivli(args.avl, args.vtype);
      SetVlAndVtypeCsr(vl, vtype);
      SetRegOrIgnore(args.dst, vl);
    }
  }

  void Vsetvl(const typename Decoder::VsetvlArgs& args) {
    Register vtype_new = listener_->GetReg(args.src2);
    if (args.src1 == 0) {
      if (args.dst == 0) {
        auto [vl_orig, vtype_orig] = GetVlAndVtypeCsr();
        auto [vl, vtype] = listener_->Vtestvl(vl_orig, vtype_orig, vtype_new);
        SetVlAndVtypeCsr(vl, vtype);
      } else {
        auto [vl, vtype] = listener_->Vsetvlmax(vtype_new);
        SetVlAndVtypeCsr(vl, vtype);
        listener_->SetReg(args.dst, vl);
      }
    } else {
      Register avl = listener_->GetReg(args.src1);
      auto [vl, vtype] = listener_->Vsetvl(avl, vtype_new);
      SetVlAndVtypeCsr(vl, vtype);
      SetRegOrIgnore(args.dst, vl);
    }
  }

  void Vsetvli(const typename Decoder::VsetvliArgs& args) {
    if (args.src == 0) {
      if (args.dst == 0) {
        auto [vl_orig, vtype_orig] = GetVlAndVtypeCsr();
        auto [vl, vtype] = listener_->Vtestvli(vl_orig, vtype_orig, args.vtype);
        SetVlAndVtypeCsr(vl, vtype);
      } else {
        auto [vl, vtype] = listener_->Vsetvlimax(args.vtype);
        SetVlAndVtypeCsr(vl, vtype);
        listener_->SetReg(args.dst, vl);
      }
    } else {
      Register avl = listener_->GetReg(args.src);
      auto [vl, vtype] = listener_->Vsetvli(avl, args.vtype);
      SetVlAndVtypeCsr(vl, vtype);
      SetRegOrIgnore(args.dst, vl);
    }
  }

  void Store(const typename Decoder::StoreArgs& args) {
    Register arg = GetRegOrZero(args.src);
    Register data = GetRegOrZero(args.data);
    listener_->Store(args.operand_type, arg, args.offset, data);
  }

  void Store(const typename Decoder::StoreFpArgs& args) {
    Register arg = GetRegOrZero(args.src);
    FpRegister data = GetFpReg(args.data);
    switch (args.operand_type) {
      case Decoder::FloatOperandType::kFloat:
        listener_->template StoreFp<Float32>(arg, args.offset, data);
        break;
      case Decoder::FloatOperandType::kDouble:
        listener_->template StoreFp<Float64>(arg, args.offset, data);
        break;
      default:
        Unimplemented();
        return;
    }
  }

  // We may have executed a signal handler just after the syscall. If that handler changed x10, then
  // overwriting x10 here would be incorrect. On the other hand asynchronous signals are unlikely to
  // change CPU state, so we don't support this at the moment for simplicity."
  void System(const typename Decoder::SystemArgs& args) {
    if (args.opcode != Decoder::SystemOpcode::kEcall) {
      return Unimplemented();
    }
    Register syscall_nr = GetRegOrZero(17);
    Register arg0 = GetRegOrZero(10);
    Register arg1 = GetRegOrZero(11);
    Register arg2 = GetRegOrZero(12);
    Register arg3 = GetRegOrZero(13);
    Register arg4 = GetRegOrZero(14);
    Register arg5 = GetRegOrZero(15);
    Register result = listener_->Ecall(syscall_nr, arg0, arg1, arg2, arg3, arg4, arg5);
    SetRegOrIgnore(10, result);
  }

  void Unimplemented() { listener_->Unimplemented(); };

 private:
  Register GetRegOrZero(uint8_t reg) {
    return reg == 0 ? listener_->GetImm(0) : listener_->GetReg(reg);
  }

  void SetRegOrIgnore(uint8_t reg, Register value) {
    if (reg != 0) {
      listener_->SetReg(reg, value);
    }
  }

  // TODO(b/260725458): stop using GetCsrProcessor helper class and define lambda in GetCsr instead.
  // We need C++20 (https://wg21.link/P0428R2) for that.
  class GetCsrProcessor {
   public:
    GetCsrProcessor(Register& reg, SemanticsListener* listener) : reg_(reg), listener_(listener) {}
    template <CsrName kName>
    void operator()() {
      reg_ = listener_->template GetCsr<kName>();
    }

   private:
    Register& reg_;
    SemanticsListener* listener_;
  };

  std::tuple<bool, Register> GetCsr(CsrName csr) {
    Register reg;
    GetCsrProcessor get_csr(reg, listener_);
    return {ProcessCsrNameAsTemplateParameter(csr, get_csr), reg};
  }

  // TODO(b/260725458): stop using SetCsrProcessor helper class and define lambda in SetCsr instead.
  // We need C++20 (https://wg21.link/P0428R2) for that.
  class SetCsrImmProcessor {
   public:
    SetCsrImmProcessor(uint8_t imm, SemanticsListener* listener) : imm_(imm), listener_(listener) {}
    template <CsrName kName>
    void operator()() {
      // Csr registers with two top bits set are read-only.
      // Attempts to write into such register raise illegal instruction exceptions.
      if constexpr (CsrWritable(kName)) {
        listener_->template SetCsr<kName>(imm_);
      }
    }

   private:
    uint8_t imm_;
    SemanticsListener* listener_;
  };

  bool SetCsr(CsrName csr, uint8_t imm) {
    // Csr registers with two top bits set are read-only.
    // Attempts to write into such register raise illegal instruction exceptions.
    if (!CsrWritable(csr)) {
      return false;
    }
    SetCsrImmProcessor set_csr(imm, listener_);
    return ProcessCsrNameAsTemplateParameter(csr, set_csr);
  }

  // TODO(b/260725458): stop using SetCsrProcessor helper class and define lambda in SetCsr instead.
  // We need C++20 (https://wg21.link/P0428R2) for that.
  class SetCsrProcessor {
   public:
    SetCsrProcessor(Register reg, SemanticsListener* listener) : reg_(reg), listener_(listener) {}
    template <CsrName kName>
    void operator()() {
      // Csr registers with two top bits set are read-only.
      // Attempts to write into such register raise illegal instruction exceptions.
      if constexpr (CsrWritable(kName)) {
        listener_->template SetCsr<kName>(reg_);
      }
    }

   private:
    Register reg_;
    SemanticsListener* listener_;
  };

  bool SetCsr(CsrName csr, Register reg) {
    // Csr registers with two top bits set are read-only.
    // Attempts to write into such register raise illegal instruction exceptions.
    if (!CsrWritable(csr)) {
      return false;
    }
    SetCsrProcessor set_csr(reg, listener_);
    return ProcessCsrNameAsTemplateParameter(csr, set_csr);
  }

  // Floating point instructions in RISC-V are encoded in a way where you may find out size of
  // operand (single-precision, double-precision, half-precision or quad-precesion; the latter
  // two optional) from the instruction encoding without determining the full form of instruction.
  //
  // Sources and targets are also specified via dedicated bits in opcodes.
  //
  // This allows us to split instruction handling in four steps:
  //   1. Load operands from register and convert it into a form suitable for host.
  //   2. Execute operations specified by opcode.
  //   3. Normalize NaNs if host and guest architctures handled them differently.
  //   4. Encode results as required by RISC-V (if host doesn't do that).
  //
  // Note that in case of execution of RISC-V on RISC-V all steps except #2 are not doing anything.

  // Step #1:
  //  • GetFpReg — for instructions like fsw or fmv.x.w use GetFpReg which doesn't change value.
  //  • GetFRegAndBoxNan — for most instructions (improperly boxed narrow float is turned into NaN).
  FpRegister GetFpReg(uint8_t reg) { return listener_->GetFpReg(reg); }
  template <typename FloatType>
  FpRegister GetFRegAndUnboxNan(uint8_t reg) {
    return listener_->template GetFRegAndUnboxNan<FloatType>(reg);
  }

  // Step #3.
  template <typename FloatType>
  FpRegister CanonicalizeNan(FpRegister value) {
    return listener_->template CanonicalizeNan<FloatType>(value);
  }

  // Step #4. Note the assymetry: step #1 may skip the NaN unboxing (would use GetFpReg if so),
  // but step #4 boxes uncoditionally (if actual instruction doesn't do that on host).
  template <typename FloatType>
  void NanBoxAndSetFpReg(uint8_t reg, FpRegister value) {
    listener_->template NanBoxAndSetFpReg<FloatType>(reg, value);
  }

  std::tuple<Register, Register> GetVlAndVtypeCsr() {
    Register vl_orig = listener_->template GetCsr<CsrName::kVl>();
    Register vtype_orig = listener_->template GetCsr<CsrName::kVtype>();
    return {vl_orig, vtype_orig};
  }

  void SetVlAndVtypeCsr(Register vl, Register vtype) {
    listener_->template SetCsr<CsrName::kVtype>(vtype);
    listener_->template SetCsr<CsrName::kVl>(vl);
  }

  SemanticsListener* listener_;
};

}  // namespace berberis

#endif  // BERBERIS_DECODER_RISCV64_SEMANTICS_PLAYER_H_
