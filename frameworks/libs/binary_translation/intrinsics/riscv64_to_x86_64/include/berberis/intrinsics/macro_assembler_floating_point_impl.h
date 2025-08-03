/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_FLOATING_POINT_IMPL_H_
#define RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_FLOATING_POINT_IMPL_H_

#include "berberis/base/bit_util.h"
#include "berberis/intrinsics/macro_assembler.h"

namespace berberis {

namespace {

// Exceptions are at position 0 in both x87 status word and mxcsr.
// But rounding is in different positions for both.
constexpr int8_t kX87RmPosition = 10;
constexpr int8_t kMxcsrRmPosition = 13;
// Mask to clean exceptions and rm fields.
constexpr int8_t kX87MxcsrExceptionBits = 0b11'1101;  // No denormals: RISC-V doesn't have them.
constexpr int16_t kX87RoundingBits = 0b11 << kX87RmPosition;
constexpr int16_t kMxcsrRoundingBits = 0b11 << kMxcsrRmPosition;
// Because rouding mode is only two bits on x86 we can compress table which converts from
// RISC-V rounding mode to x87/SSE rounding mode into one integer.
// Each element of table is two bits here:
//   FE_TONEAREST, FE_TOWARDZERO, FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO table.
// Note: we never convert from x86 rounding mode to RISC-V rounding mode because there are
// more roudning modes on RISC-V which means we have to keep these in the emulated CPU state.
constexpr int32_t kRiscVRoundingModes = 0b1110'0111'00;

}  // namespace

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroCanonicalizeNan(XMMRegister result, XMMRegister src) {
  Pmov(result, src);
  Cmpords<FloatType>(result, src);
  Pand(src, result);
  Pandn(result, {.disp = constants_pool::kCanonicalNans<FloatType>});
  Por(result, src);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroCanonicalizeNanAVX(XMMRegister result, XMMRegister src) {
  Vcmpords<FloatType>(result, src, src);
  Vpand(src, src, result);
  Vpandn(result, result, {.disp = constants_pool::kCanonicalNans<FloatType>});
  Vpor(result, result, src);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFeq(Register result, XMMRegister src1, XMMRegister src2) {
  Cmpeqs<FloatType>(src1, src2);
  Mov<FloatType>(result, src1);
  And<int32_t>(result, 1);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFeqAVX(Register result,
                                            XMMRegister src1,
                                            XMMRegister src2,
                                            XMMRegister tmp) {
  Vcmpeqs<FloatType>(tmp, src1, src2);
  Vmov<FloatType>(result, tmp);
  And<int32_t>(result, 1);
}

// Note: result is returned in %rax which is implicit argument of that macro-instruction.
// Explicit argument is temporary needed to handle Stmxcsr instruction.
template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeGetExceptionsTranslate(const Operand& mxcsr_scratch) {
  // Store x87 status word in the AX.
  Fnstsw();
  // Store MXCSR in scratch slot.
  Stmxcsr(mxcsr_scratch);
  // Merge x87 status word and MXCSR.
  Or<uint32_t>(gpr_a, mxcsr_scratch);
  // Leave only exceptions.
  And<uint32_t>(gpr_a, kX87MxcsrExceptionBits);
  // Convert exception bits.
  Expand<uint64_t, uint8_t>(gpr_a,
                            {.index = gpr_a,
                             .scale = Assembler::kTimesOne,
                             .disp = constants_pool::kX87ToRiscVExceptions});
}

template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetExceptionsAndRoundImmTranslate(
    const Operand& fenv_scratch,
    int8_t exceptions_and_rm) {
  int8_t exceptions = exceptions_and_rm & 0b1'1111;
  int8_t rm = static_cast<uint8_t>(exceptions_and_rm) >> 5;
  // Note: in 32bit/64bit mode it's at offset 4, not 2 as one may imagine.
  // Two bytes after control word are ignored.
  Operand x87_status_word = {.base = fenv_scratch.base,
                             .index = fenv_scratch.index,
                             .scale = fenv_scratch.scale,
                             .disp = fenv_scratch.disp + 4};
  // Place mxcsr right after 28bytes-sized x87 environment.
  Operand mxcsr = {.base = fenv_scratch.base,
                   .index = fenv_scratch.index,
                   .scale = fenv_scratch.scale,
                   .disp = fenv_scratch.disp + 28};
  // Convert RISC-V exceptions into x87 exceptions.
  uint8_t x87_exceptions = bit_cast<unsigned char*>(
      static_cast<uintptr_t>(constants_pool::kRiscVToX87Exceptions))[exceptions];
  // We have to store the whole floating point environment since it's not possible to just change
  // status word without affecting other state.
  Fnstenv(fenv_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr);
  // Clean exceptions in the x87 environment.
  And<uint8_t>(x87_status_word, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  // Clean-out x87-RM field in x87 control word.
  And<uint16_t>(fenv_scratch, static_cast<uint16_t>(~kX87RoundingBits));
  // Clean-out MXCSR-RM field and exception bits in MXCSR.
  And<uint32_t>(mxcsr, static_cast<uint32_t>(~(kX87MxcsrExceptionBits | kMxcsrRoundingBits)));
  if (x87_exceptions) {
    // If exceptions are not zero then put exceptions in the x87 environment.
    Or<uint8_t>(x87_status_word, x87_exceptions);
  }
  if (rm) {
    // If rounding mode is not zero then convert RISC-V rounding mode and store it in control word.
    Or<uint16_t>(fenv_scratch,
                 (((kRiscVRoundingModes << kX87RmPosition) >> (rm * 2)) & kX87RoundingBits));
  }
  if (exceptions_and_rm) {
    // If exceptions or roudning mode are not zero then then convert RISC-V rounding mode and store
    // them it in MXCSR.
    Or<uint32_t>(mxcsr,
                 x87_exceptions | (((kRiscVRoundingModes << kMxcsrRmPosition) >> (rm * 2)) &
                                   kMxcsrRoundingBits));
  }
  // Load x87 environment.
  Fldenv(fenv_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr);
}

template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetExceptionsAndRoundTranslate(Register exceptions,
                                                                      const Operand& fenv_scratch,
                                                                      Register scratch_register) {
  // Note: in 32bit/64bit mode it's at offset 4, not 2 as one may imagine.
  // Two bytes after control word are ignored.
  Operand x87_status_word = {.base = fenv_scratch.base,
                             .index = fenv_scratch.index,
                             .scale = fenv_scratch.scale,
                             .disp = fenv_scratch.disp + 4};
  // Place mxcsr right after 28bytes-sized x87 environment.
  Operand mxcsr = {.base = fenv_scratch.base,
                   .index = fenv_scratch.index,
                   .scale = fenv_scratch.scale,
                   .disp = fenv_scratch.disp + 28};
  // We have to store the whole floating point environment since it's not possible to just change
  // status word without affecting other state.
  Fnstenv(fenv_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr);
  // Convert exceptions from RISC-V format to x87 format.
  Mov<uint8_t>(scratch_register,
               {.index = exceptions,
                .scale = Assembler::kTimesOne,
                .disp = constants_pool::kRiscVToX87Exceptions});
  // Clean exceptions in the x87 environment. Note: in 32bit/64bit mode it's at offset 4, not 2 as
  // one may imagine. Two bytes after control word are ignored.
  And<uint8_t>(x87_status_word, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  // Clean-out x87-RM field in x87 control word.
  And<uint16_t>(fenv_scratch, static_cast<uint16_t>(~kX87RoundingBits));
  // Clean-out MXCSR-RM field and exception bits in MXCSR.
  And<uint32_t>(mxcsr, static_cast<uint32_t>(~(kX87MxcsrExceptionBits | kMxcsrRoundingBits)));
  // Put exceptions in the x87 environment.
  Or<uint8_t>(x87_status_word, scratch_register);
  // Put exceptions in the MXCSR environment.
  Or<uint8_t>(mxcsr, scratch_register);
  // FE_TONEAREST, FE_TOWARDZERO, FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO table from bits 10-11:
  Mov<uint32_t>(scratch_register, kRiscVRoundingModes << kX87RmPosition);
  // Shift by “rm” to get appropriate bits, suitable for x87 FPU control word.
  ShrByCl<uint32_t>(scratch_register);
  // Each field is two bits so we need to shift by “rm” twice.
  // By doing it with 2x shifts we keep “rm” in CL intact (and speed is the same on most CPUs).
  ShrByCl<uint32_t>(scratch_register);
  // Mask only x87-RM bits.
  And<uint32_t>(scratch_register, kX87RoundingBits);
  // Push x87-RM field into x87 control world.
  Or<uint16_t>(fenv_scratch, scratch_register);
  // Move x87-RM field into MSCXR-RM field.
  Shl<uint32_t>(scratch_register, int8_t{3});
  // Push MXCSR-RM field into MXCSR.
  Or<uint32_t>(mxcsr, scratch_register);
  // Load x87 environment.
  Fldenv(fenv_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr);
}

template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetExceptionsImmTranslate(const Operand& fenv_scratch,
                                                                 int8_t exceptions) {
  // Note: in 32bit/64bit mode it's at offset 4, not 2 as one may imagine.
  // Two bytes after control word are ignored.
  Operand x87_status_word = {.base = fenv_scratch.base,
                             .index = fenv_scratch.index,
                             .scale = fenv_scratch.scale,
                             .disp = fenv_scratch.disp + 4};
  // Place mxcsr right after 28bytes-sized x87 environment.
  Operand mxcsr = {.base = fenv_scratch.base,
                   .index = fenv_scratch.index,
                   .scale = fenv_scratch.scale,
                   .disp = fenv_scratch.disp + 28};
  // Convert RISC-V exceptions into x87 exceptions.
  uint8_t x87_exceptions = bit_cast<unsigned char*>(
      static_cast<uintptr_t>(constants_pool::kRiscVToX87Exceptions))[exceptions];
  // We have to store the whole floating point environment since it's not possible to just change
  // status word without affecting other state.
  Fnstenv(fenv_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr);
  // Clean exceptions in the x87 environment. Note: in 32bit/64bit mode it's at offset 4, not 2 as
  // one may imagine. Two bytes after control word are ignored.
  And<uint8_t>(x87_status_word, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  // Clean exception bits
  And<uint8_t>(mxcsr, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  if (x87_exceptions) {
    // Put exceptions in the x87 environment.
    Or<uint8_t>(x87_status_word, x87_exceptions);
    // Put exceptions in the MXCSR environment.
    Or<uint8_t>(mxcsr, x87_exceptions);
  }
  // Load x87 environment.
  Fldenv(fenv_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr);
}

template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetExceptionsTranslate(Register exceptions,
                                                              const Operand& fenv_scratch,
                                                              Register x87_exceptions) {
  // Note: in 32bit/64bit mode it's at offset 4, not 2 as one may imagine.
  // Two bytes after control word are ignored.
  Operand x87_status_word = {.base = fenv_scratch.base,
                             .index = fenv_scratch.index,
                             .scale = fenv_scratch.scale,
                             .disp = fenv_scratch.disp + 4};
  // Place mxcsr right after 28bytes-sized x87 environment.
  Operand mxcsr = {.base = fenv_scratch.base,
                   .index = fenv_scratch.index,
                   .scale = fenv_scratch.scale,
                   .disp = fenv_scratch.disp + 28};
  // We have to store the whole floating point environment since it's not possible to just change
  // status word without affecting other state.
  Fnstenv(fenv_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr);
  // Convert exceptions from RISC-V format to x87 format.
  Mov<uint8_t>(x87_exceptions,
               {.index = exceptions,
                .scale = Assembler::kTimesOne,
                .disp = constants_pool::kRiscVToX87Exceptions});
  // Clean exceptions in the x87 environment. Note: in 32bit/64bit mode it's at offset 4, not 2 as
  // one may imagine. Two bytes after control word are ignored.
  And<uint8_t>(x87_status_word, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  // Clean exception bits
  And<uint8_t>(mxcsr, static_cast<uint8_t>(~kX87MxcsrExceptionBits));
  // Put exceptions in the x87 environment.
  Or<uint8_t>(x87_status_word, x87_exceptions);
  // Put exceptions in the MXCSR environment.
  Or<uint8_t>(mxcsr, x87_exceptions);
  // Load x87 environment.
  Fldenv(fenv_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr);
}

// Note: actual rounding mode comes in %cl which is implicit argument of that macro-instruction.
// All explicit arguments are temporaries.
template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetRound(Register x87_sse_round,
                                                const Operand& cw_scratch,
                                                const Operand& mxcsr_scratch) {
  // Store x87 control world in first scratch slot.
  Fnstcw(cw_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr_scratch);
  // Clean-out x87-RM field in x87 control word.
  And<uint16_t>(cw_scratch, static_cast<uint16_t>(~kX87RoundingBits));
  // Clean-out MXCSR-RM field in MXCSR.
  And<uint32_t>(mxcsr_scratch, static_cast<uint32_t>(~kMxcsrRoundingBits));
  // FE_TONEAREST, FE_TOWARDZERO, FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO table from bits 10-11:
  Mov<uint32_t>(x87_sse_round, kRiscVRoundingModes << kX87RmPosition);
  // Shift by “rm” to get appropriate bits, suitable for x87 FPU control word.
  ShrByCl<uint32_t>(x87_sse_round);
  // Each field is two bits so we need to shift by “rm” twice.
  // By doing it with 2x shifts we keep “rm” in CL intact (and speed is the same on most CPUs).
  ShrByCl<uint32_t>(x87_sse_round);
  // Mask only x87-RM bits.
  And<uint32_t>(x87_sse_round, kX87RoundingBits);
  // Push x87-RM field into x87 control world.
  Or<uint16_t>(cw_scratch, x87_sse_round);
  // Move x87-RM field into MSCXR-RM field.
  Shl<uint32_t>(x87_sse_round, int8_t{3});
  // Push MXCSR-RM field into MXCSR.
  Or<uint32_t>(mxcsr_scratch, x87_sse_round);
  // Load new control world into x87 FPU.
  Fldcw(cw_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr_scratch);
}

template <typename Assembler>
void MacroAssembler<Assembler>::MacroFeSetRoundImmTranslate(const Operand& cw_scratch,
                                                            const Operand& mxcsr_scratch,
                                                            int8_t rm) {
  // Store x87 control world in first scratch slot.
  Fnstcw(cw_scratch);
  // Store MXCSR in second scratch slot.
  Stmxcsr(mxcsr_scratch);
  // Clean-out x87-RM field in x87 control word.
  And<uint16_t>(cw_scratch, static_cast<uint16_t>(~kX87RoundingBits));
  // Clean-out MXCSR-RM field in MXCSR.
  And<uint32_t>(mxcsr_scratch, static_cast<uint32_t>(~kMxcsrRoundingBits));
  if (rm) {
    // If rounding mode is not zero then convert RISC-V rounding mode and store it in control word.
    Or<uint16_t>(cw_scratch,
                 (((kRiscVRoundingModes << kX87RmPosition) >> (rm * 2)) & kX87RoundingBits));
    // If rounding mode is not zero then convert RISC-V rounding mode and store it in MXCSR.
    Or<uint32_t>(mxcsr_scratch,
                 ((kRiscVRoundingModes << kMxcsrRmPosition) >> (rm * 2)) & kMxcsrRoundingBits);
  }
  // Load new control world into x87 FPU.
  Fldcw(cw_scratch);
  // Load Mxcsr.
  Ldmxcsr(mxcsr_scratch);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFle(Register result, XMMRegister src1, XMMRegister src2) {
  Cmples<FloatType>(src1, src2);
  Mov<FloatType>(result, src1);
  And<int32_t>(result, 1);
}

template <typename Assembler>
template <typename FormatTo, typename FormatFrom>
void MacroAssembler<Assembler>::MacroFCvtFloatToInteger(Register result, XMMRegister src) {
  Cvt<FormatFrom, FormatTo>(result, src);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFleAVX(Register result,
                                            XMMRegister src1,
                                            XMMRegister src2,
                                            XMMRegister tmp) {
  Vcmples<FloatType>(tmp, src1, src2);
  Vmov<FloatType>(result, tmp);
  And<int32_t>(result, 1);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFlt(Register result, XMMRegister src1, XMMRegister src2) {
  Cmplts<FloatType>(src1, src2);
  Mov<FloatType>(result, src1);
  And<int32_t>(result, 1);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroFltAVX(Register result,
                                            XMMRegister src1,
                                            XMMRegister src2,
                                            XMMRegister tmp) {
  Vcmplts<FloatType>(tmp, src1, src2);
  Vmov<FloatType>(result, tmp);
  And<int32_t>(result, 1);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroNanBox(XMMRegister arg) {
  static_assert(std::is_same_v<FloatType, Float32>);

  Por(arg, {.disp = constants_pool::kNanBox<Float32>});
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroNanBoxAVX(XMMRegister result, XMMRegister src) {
  static_assert(std::is_same_v<FloatType, Float32>);

  Vpor(result, src, {.disp = constants_pool::kNanBox<Float32>});
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroUnboxNan(XMMRegister result, XMMRegister src) {
  static_assert(std::is_same_v<FloatType, Float32>);

  Pmov(result, src);
  Pcmpeq<typename TypeTraits<FloatType>::Int>(result, {.disp = constants_pool::kNanBox<Float32>});
  Pshufd(result, result, kShuffleDDBB);
  Pand(src, result);
  Pandn(result, {.disp = constants_pool::kNanBoxedNans<Float32>});
  Por(result, src);
}

template <typename Assembler>
template <typename FloatType>
void MacroAssembler<Assembler>::MacroUnboxNanAVX(XMMRegister result, XMMRegister src) {
  static_assert(std::is_same_v<FloatType, Float32>);

  Vpcmpeq<typename TypeTraits<FloatType>::Int>(
      result, src, {.disp = constants_pool::kNanBox<Float32>});
  Vpshufd(result, result, kShuffleDDBB);
  Vpand(src, src, result);
  Vpandn(result, result, {.disp = constants_pool::kNanBoxedNans<Float32>});
  Vpor(result, result, src);
}

}  // namespace berberis

#endif  // RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_FLOATING_POINT_IMPL_H_
