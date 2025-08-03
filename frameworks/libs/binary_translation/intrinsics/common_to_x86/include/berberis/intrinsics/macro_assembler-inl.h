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

#ifndef DEFINE_MACRO_ASSEMBLER_GENERIC_FUNCTIONS
#error This file is supposed to be included from berberis/intrinsics/macro_assembler-inl.h
#else
#undef DEFINE_MACRO_ASSEMBLER_GENERIC_FUNCTIONS
#endif

using Condition = typename Assembler::Condition;
using Label = typename Assembler::Label;
using Operand = typename Assembler::Operand;
using Register = typename Assembler::Register;
using ScaleFactor = typename Assembler::ScaleFactor;
using XMMRegister = typename Assembler::XMMRegister;

using Float32 = intrinsics::Float32;
using Float64 = intrinsics::Float64;

template <typename IntType>
using ImmFormat =
    std::conditional_t<sizeof(IntType) <= sizeof(int32_t), std::make_signed_t<IntType>, int32_t>;

template <typename format, typename... allowed_formats>
static constexpr bool kFormatIs = (std::is_same_v<format, allowed_formats> || ...);

template <typename IntType>
static constexpr bool kIntType =
    kFormatIs<IntType, int8_t, uint8_t, int16_t, uint16_t, int32_t, uint32_t, int64_t, uint64_t>;

template <typename IntType>
static constexpr bool kIntTypeBW = kFormatIs<IntType, int8_t, uint8_t, int16_t, uint16_t>;

template <typename IntType>
static constexpr bool kIntTypeBWL =
    kFormatIs<IntType, int8_t, uint8_t, int16_t, uint16_t, int32_t, uint32_t>;

template <typename IntType>
static constexpr bool kIntTypeWL = kFormatIs<IntType, int16_t, uint16_t, int32_t, uint32_t>;

template <typename IntType>
static constexpr bool kIntTypeWLQ =
    kFormatIs<IntType, int16_t, uint16_t, int32_t, uint32_t, int64_t, uint64_t>;

template <typename IntType>
static constexpr bool kIntTypeLQ = kFormatIs<IntType, int32_t, uint32_t, int64_t, uint64_t>;

// Psr is the only asymmetric instruction where unsigned 64bit variant was supported in MMX in CPUs
// released last century while signed 64bit variant is only added in AVX10 which is not yet even
// available for purchase!
template <typename IntType>
static constexpr bool kIntTypePsr =
    kFormatIs<IntType, int16_t, uint16_t, int32_t, uint32_t, uint64_t>;

template <typename IntType>
static constexpr bool kSignedIntType = kFormatIs<IntType, int8_t, int16_t, int32_t, int64_t>;

template <typename IntType>
static constexpr bool kUnsignedIntType = kFormatIs<IntType, uint8_t, uint16_t, uint32_t, uint64_t>;

template <typename FloatType>
static constexpr bool kFloatType = kFormatIs<FloatType, Float32, Float64>;

#define DEFINE_EXPAND_INSTRUCTION(Declare_dest, Declare_src)         \
  template <typename format_out, typename format_in>                 \
  std::enable_if_t<kIntType<format_out> && kIntType<format_in> &&    \
                   sizeof(format_in) <= sizeof(format_out)>          \
  Expand(Declare_dest, Declare_src) {                                \
    if constexpr (std::is_same_v<decltype(dest), decltype(src)> &&   \
                  sizeof(format_out) == sizeof(format_in)) {         \
      if (dest == src) {                                             \
        return;                                                      \
      }                                                              \
    }                                                                \
    if constexpr (kFormatIs<format_out, int8_t, uint8_t> &&          \
                  kFormatIs<format_in, int8_t, uint8_t>) {           \
      Assembler::Movb(dest, src);                                    \
    } else if constexpr (kFormatIs<format_out, int16_t, uint16_t> && \
                         kFormatIs<format_in, int8_t>) {             \
      if constexpr (std::is_same_v<decltype(dest), decltype(src)>) { \
        if (dest == Assembler::gpr_a && src == Assembler::gpr_a) {   \
          Assembler::Cbw();                                          \
          return;                                                    \
        }                                                            \
      }                                                              \
      Assembler::Movsxbw(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int16_t, uint16_t> && \
                         kFormatIs<format_in, uint8_t>) {            \
      Assembler::Movzxbw(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int16_t, uint16_t> && \
                         kFormatIs<format_in, int16_t, uint16_t>) {  \
      Assembler::Movw(dest, src);                                    \
    } else if constexpr (kFormatIs<format_out, int32_t, uint32_t> && \
                         kFormatIs<format_in, int8_t>) {             \
      Assembler::Movsxbl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int32_t, uint32_t> && \
                         kFormatIs<format_in, uint8_t>) {            \
      Assembler::Movzxbl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int32_t, uint32_t> && \
                         kFormatIs<format_in, int16_t>) {            \
      if constexpr (std::is_same_v<decltype(dest), decltype(src)>) { \
        if (dest == Assembler::gpr_a && src == Assembler::gpr_a) {   \
          Assembler::Cwde();                                         \
          return;                                                    \
        }                                                            \
      }                                                              \
      Assembler::Movsxwl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int32_t, uint32_t> && \
                         kFormatIs<format_in, uint16_t>) {           \
      Assembler::Movzxwl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int32_t, uint32_t> && \
                         kFormatIs<format_in, int32_t, uint32_t>) {  \
      Assembler::Movl(dest, src);                                    \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, int8_t>) {             \
      Assembler::Movsxbq(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, uint8_t>) {            \
      Assembler::Movzxbl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, int16_t>) {            \
      Assembler::Movsxwq(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, uint16_t>) {           \
      Assembler::Movzxwl(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, int32_t>) {            \
      if constexpr (std::is_same_v<decltype(dest), decltype(src)>) { \
        if (dest == Assembler::gpr_a && src == Assembler::gpr_a) {   \
          Assembler::Cdqe();                                         \
          return;                                                    \
        }                                                            \
      }                                                              \
      Assembler::Movsxlq(dest, src);                                 \
    } else if constexpr (kFormatIs<format_out, int64_t, uint64_t> && \
                         kFormatIs<format_in, uint32_t>) {           \
      Assembler::Movl(dest, src);                                    \
    } else {                                                         \
      Assembler::Movq(dest, src);                                    \
    }                                                                \
  }
DEFINE_EXPAND_INSTRUCTION(Register dest, Operand src)
DEFINE_EXPAND_INSTRUCTION(Register dest, Register src)
#undef DEFINE_EXPAND_INSTRUCTION

#define DEFINE_INT_INSTRUCTION(                                            \
    insn_name, asm_name, insn_siffix, type_check, parameters, arguments)   \
  template <typename format>                                               \
  std::enable_if_t<type_check<format>> insn_name##insn_siffix parameters { \
    if constexpr (kFormatIs<format, int8_t, uint8_t>) {                    \
      Assembler::asm_name##b##insn_siffix arguments;                       \
    } else if constexpr (kFormatIs<format, int16_t, uint16_t>) {           \
      Assembler::asm_name##w##insn_siffix arguments;                       \
    } else if constexpr (kFormatIs<format, int32_t, uint32_t>) {           \
      Assembler::asm_name##l##insn_siffix arguments;                       \
    } else {                                                               \
      Assembler::asm_name##q##insn_siffix arguments;                       \
    }                                                                      \
  }
DEFINE_INT_INSTRUCTION(CmpXchg, CmpXchg, , kIntType, (Operand dest, Register src), (dest, src))
DEFINE_INT_INSTRUCTION(CmpXchg, CmpXchg, , kIntType, (Register dest, Register src), (dest, src))
DEFINE_INT_INSTRUCTION(LockCmpXchg,
                       LockCmpXchg,
                       ,
                       kIntType,
                       (Operand dest, Register src),
                       (dest, src))
DEFINE_INT_INSTRUCTION(Mov, Mov, , kIntType, (Operand dest, ImmFormat<format> imm), (dest, imm))
DEFINE_INT_INSTRUCTION(Mov, Mov, , kIntType, (Operand dest, Register src), (dest, src))
DEFINE_INT_INSTRUCTION(Mov,
                       Mov,
                       ,
                       kIntType,
                       (Register dest, std::make_signed_t<format> imm),
                       (dest, imm))
DEFINE_INT_INSTRUCTION(Mov, Mov, , kIntType, (Register dest, Operand src), (dest, src))
DEFINE_INT_INSTRUCTION(Mul, Imul, , kIntTypeWLQ, (Operand dest, Register src), (dest, src))
DEFINE_INT_INSTRUCTION(Mul, Imul, , kIntTypeWLQ, (Operand dest, Operand src), (dest, src))
DEFINE_INT_INSTRUCTION(Mul,
                       Imul,
                       ,
                       kIntTypeWLQ,
                       (Operand dest, Register src, ImmFormat<format> imm),
                       (dest, src, imm))
DEFINE_INT_INSTRUCTION(Mul,
                       Imul,
                       ,
                       kIntTypeWLQ,
                       (Operand dest, Operand src, ImmFormat<format> imm),
                       (dest, src, imm))
DEFINE_INT_INSTRUCTION(Test, Test, , kIntType, (Operand dest, ImmFormat<format> imm), (dest, imm))
DEFINE_INT_INSTRUCTION(Test, Test, , kIntType, (Operand dest, Register src), (dest, src))
DEFINE_INT_INSTRUCTION(Test, Test, , kIntType, (Register dest, ImmFormat<format> imm), (dest, imm))
DEFINE_INT_INSTRUCTION(Test, Test, , kIntType, (Register dest, Register src), (dest, src))
#define DEFINE_ARITH_INSTRUCTION(insn_name, asm_name, type_check)                 \
  DEFINE_INT_INSTRUCTION(insn_name, asm_name, , type_check, (Operand arg), (arg)) \
  DEFINE_INT_INSTRUCTION(insn_name, asm_name, , type_check, (Register arg), (arg))
DEFINE_ARITH_INSTRUCTION(Dec, Dec, kIntType)
DEFINE_ARITH_INSTRUCTION(Div, Div, kUnsignedIntType)
DEFINE_ARITH_INSTRUCTION(Div, Idiv, kSignedIntType)
DEFINE_ARITH_INSTRUCTION(Inc, Inc, kIntType)
DEFINE_ARITH_INSTRUCTION(Mul, Imul, kSignedIntType)
DEFINE_ARITH_INSTRUCTION(Mul, Mul, kUnsignedIntType)
DEFINE_ARITH_INSTRUCTION(Neg, Neg, kIntType)
DEFINE_ARITH_INSTRUCTION(Not, Not, kIntType)
#undef DEFINE_ARITH_INSTRUCTION
#define DEFINE_ARITH_INSTRUCTION(insn_name)                                                  \
  DEFINE_INT_INSTRUCTION(                                                                    \
      insn_name, insn_name, , kIntType, (Operand dest, ImmFormat<format> imm), (dest, imm))  \
  DEFINE_INT_INSTRUCTION(                                                                    \
      insn_name, insn_name, , kIntType, (Operand dest, Register src), (dest, src))           \
  DEFINE_INT_INSTRUCTION(                                                                    \
      insn_name, insn_name, , kIntType, (Register dest, ImmFormat<format> imm), (dest, imm)) \
  DEFINE_INT_INSTRUCTION(                                                                    \
      insn_name, insn_name, , kIntType, (Register dest, Operand src), (dest, src))           \
  DEFINE_INT_INSTRUCTION(                                                                    \
      insn_name, insn_name, , kIntType, (Register dest, Register src), (dest, src))
DEFINE_ARITH_INSTRUCTION(Adc)
DEFINE_ARITH_INSTRUCTION(Add)
DEFINE_ARITH_INSTRUCTION(And)
DEFINE_ARITH_INSTRUCTION(Cmp)
DEFINE_ARITH_INSTRUCTION(Or)
DEFINE_ARITH_INSTRUCTION(Sbb)
DEFINE_ARITH_INSTRUCTION(Sub)
DEFINE_ARITH_INSTRUCTION(Xor)
#undef DEFINE_ARITH_INSTRUCTION
#define DEFINE_SHIFT_INSTRUCTION(insn_name)                                            \
  DEFINE_INT_INSTRUCTION(                                                              \
      insn_name, insn_name, , kIntType, (Operand dest, int8_t imm), (dest, imm))       \
  DEFINE_INT_INSTRUCTION(insn_name, insn_name, ByCl, kIntType, (Operand dest), (dest)) \
  DEFINE_INT_INSTRUCTION(                                                              \
      insn_name, insn_name, , kIntType, (Register dest, int8_t imm), (dest, imm))      \
  DEFINE_INT_INSTRUCTION(insn_name, insn_name, ByCl, kIntType, (Register dest), (dest))
DEFINE_SHIFT_INSTRUCTION(Rcl)
DEFINE_SHIFT_INSTRUCTION(Rcr)
DEFINE_SHIFT_INSTRUCTION(Rol)
DEFINE_SHIFT_INSTRUCTION(Ror)
DEFINE_SHIFT_INSTRUCTION(Sar)
DEFINE_SHIFT_INSTRUCTION(Shl)
DEFINE_SHIFT_INSTRUCTION(Shr)
#undef DEFINE_INT_INSTRUCTION
#undef DEFINE_SHIFT_INSTRUCTION

#define DEFINE_INT_INSTRUCTION(insn_name, parameters, arguments) \
  template <typename format>                                     \
  std::enable_if_t<kIntTypeWLQ<format>> insn_name parameters {   \
    if constexpr (kFormatIs<format, int16_t, uint16_t>) {        \
      Assembler::insn_name##w arguments;                         \
    } else if constexpr (kFormatIs<format, int32_t, uint32_t>) { \
      Assembler::insn_name##l arguments;                         \
    } else {                                                     \
      Assembler::insn_name##q arguments;                         \
    }                                                            \
  }
DEFINE_INT_INSTRUCTION(Cmov, (Condition cond, Register dest, Operand src), (cond, dest, src))
DEFINE_INT_INSTRUCTION(Cmov, (Condition cond, Register dest, Register src), (cond, dest, src))
#define DEFINE_BIT_INSTRUCTION(insn_name)                                                \
  DEFINE_INT_INSTRUCTION(insn_name, (Operand dest, ImmFormat<format> imm), (dest, imm))  \
  DEFINE_INT_INSTRUCTION(insn_name, (Operand dest, Register src), (dest, src))           \
  DEFINE_INT_INSTRUCTION(insn_name, (Register dest, ImmFormat<format> imm), (dest, imm)) \
  DEFINE_INT_INSTRUCTION(insn_name, (Register dest, Register src), (dest, src))
DEFINE_BIT_INSTRUCTION(Bt)
DEFINE_BIT_INSTRUCTION(Btc)
DEFINE_BIT_INSTRUCTION(Btr)
DEFINE_BIT_INSTRUCTION(Bts)
#undef DEFINE_BIT_INSTRUCTION
#define DEFINE_BIT_INSTRUCTION(insn_name)                                      \
  DEFINE_INT_INSTRUCTION(insn_name, (Register dest, Operand src), (dest, src)) \
  DEFINE_INT_INSTRUCTION(insn_name, (Register dest, Register src), (dest, src))
DEFINE_BIT_INSTRUCTION(Bsf)
DEFINE_BIT_INSTRUCTION(Bsr)
DEFINE_BIT_INSTRUCTION(Lzcnt)
DEFINE_BIT_INSTRUCTION(Tzcnt)
#undef DEFINE_BIT_INSTRUCTION
#undef DEFINE_INT_INSTRUCTION

// Note: Mov<int32_t> from one register to that same register doesn't zero-out top 32bits,
// like real Movq would! If you want that effect then use Expand<tnt32_t, int32_t> instead!
template <typename format>
std::enable_if_t<kIntType<format>> Mov(Register dest, Register src) {
  if (dest == src) {
    return;
  }
  if constexpr (kFormatIs<format, int8_t, uint8_t>) {
    Assembler::Movb(dest, src);
  } else if constexpr (kFormatIs<format, int16_t, uint16_t>) {
    Assembler::Movw(dest, src);
  } else if constexpr (kFormatIs<format, int32_t, uint32_t>) {
    Assembler::Movl(dest, src);
  } else {
    Assembler::Movq(dest, src);
  }
}

#define DEFINE_INT_INSTRUCTION(insn_name, parameters, arguments)     \
  template <typename target_format>                                  \
  std::enable_if_t<kIntTypeBW<target_format>> insn_name parameters { \
    if constexpr (kFormatIs<target_format, int8_t>) {                \
      Assembler::insn_name##sswb arguments;                          \
    } else if constexpr (kFormatIs<target_format, uint8_t>) {        \
      Assembler::insn_name##uswb arguments;                          \
    } else if constexpr (kFormatIs<target_format, int16_t>) {        \
      Assembler::insn_name##ssdw arguments;                          \
    } else {                                                         \
      Assembler::insn_name##usdw arguments;                          \
    }                                                                \
  }
#define DEFINE_XMM_INT_INSTRUCTIONS_GROUP(insn_name)                                             \
  DEFINE_INT_INSTRUCTION(P##insn_name, (XMMRegister dest, XMMRegister src), (dest, src))         \
  DEFINE_INT_INSTRUCTION(P##insn_name, (XMMRegister dest, Operand src), (dest, src))             \
  DEFINE_INT_INSTRUCTION(                                                                        \
      Vp##insn_name, (XMMRegister dest, XMMRegister src1, XMMRegister src2), (dest, src1, src2)) \
  DEFINE_INT_INSTRUCTION(                                                                        \
      Vp##insn_name, (XMMRegister dest, XMMRegister src1, Operand src2), (dest, src1, src2))
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(ack)
#undef DEFINE_INT_INSTRUCTION
#define DEFINE_INT_INSTRUCTION(insn_name, parameters, arguments)    \
  template <typename format>                                        \
  std::enable_if_t<kUnsignedIntType<format>> insn_name parameters { \
    if constexpr (kFormatIs<format, uint8_t>) {                     \
      Assembler::insn_name##bw arguments;                           \
    } else if constexpr (kFormatIs<format, uint16_t>) {             \
      Assembler::insn_name##wd arguments;                           \
    } else if constexpr (kFormatIs<format, uint32_t>) {             \
      Assembler::insn_name##dq arguments;                           \
    } else {                                                        \
      Assembler::insn_name##qdq arguments;                          \
    }                                                               \
  }
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(unpckh)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(unpckl)
#undef DEFINE_XMM_INT_INSTRUCTIONS_GROUP
#undef DEFINE_INT_INSTRUCTION

#define DEFINE_XMM_INT_INSTRUCTION(                                           \
    insn_name, asm_name, type_check, parameters, arguments, signed, unsigned) \
  template <typename format>                                                  \
  std::enable_if_t<type_check<format>> insn_name parameters {                 \
    if constexpr (kFormatIs<format, int8_t>) {                                \
      Assembler::asm_name##signed##b arguments;                               \
    } else if constexpr (kFormatIs<format, uint8_t>) {                        \
      Assembler::asm_name##unsigned##b arguments;                             \
    } else if constexpr (kFormatIs<format, int16_t>) {                        \
      Assembler::asm_name##signed##w arguments;                               \
    } else if constexpr (kFormatIs<format, uint16_t>) {                       \
      Assembler::asm_name##unsigned##w arguments;                             \
    } else if constexpr (kFormatIs<format, int32_t>) {                        \
      Assembler::asm_name##signed##d arguments;                               \
    } else if constexpr (kFormatIs<format, uint32_t>) {                       \
      Assembler::asm_name##unsigned##d arguments;                             \
    } else if constexpr (kFormatIs<format, int64_t>) {                        \
      Assembler::asm_name##signed##q arguments;                               \
    } else {                                                                  \
      Assembler::asm_name##unsigned##q arguments;                             \
    }                                                                         \
  }
#define DEFINE_XMM_INT_INSTRUCTIONS_GROUP(insn_name, asm_name, type_check, signed, unsigned) \
  DEFINE_XMM_INT_INSTRUCTION(P##insn_name,                                                   \
                             P##asm_name,                                                    \
                             type_check,                                                     \
                             (XMMRegister dest, Operand src),                                \
                             (dest, src),                                                    \
                             signed,                                                         \
                             unsigned)                                                       \
  DEFINE_XMM_INT_INSTRUCTION(P##insn_name,                                                   \
                             P##asm_name,                                                    \
                             type_check,                                                     \
                             (XMMRegister dest, XMMRegister src),                            \
                             (dest, src),                                                    \
                             signed,                                                         \
                             unsigned)                                                       \
  DEFINE_XMM_INT_INSTRUCTION(Vp##insn_name,                                                  \
                             Vp##asm_name,                                                   \
                             type_check,                                                     \
                             (XMMRegister dest, XMMRegister src1, Operand src2),             \
                             (dest, src1, src2),                                             \
                             signed,                                                         \
                             unsigned)                                                       \
  DEFINE_XMM_INT_INSTRUCTION(Vp##insn_name,                                                  \
                             Vp##asm_name,                                                   \
                             type_check,                                                     \
                             (XMMRegister dest, XMMRegister src1, XMMRegister src2),         \
                             (dest, src1, src2),                                             \
                             signed,                                                         \
                             unsigned)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(add, add, kIntType, , )
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(adds, add, kIntTypeBW, s, us)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(cmpeq, cmpeq, kIntType, , )
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(cmpgt, cmpgt, kSignedIntType, , )
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(max, max, kIntTypeBWL, s, u)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(min, min, kIntTypeBWL, s, u)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(mull, mull, kIntTypeWL, , )
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(sl, sl, kIntTypeWLQ, l, l)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(sr, sr, kIntTypePsr, a, l)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(subs, sub, kIntTypeBW, s, us)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(sub, sub, kIntType, , )
#undef DEFINE_XMM_INT_INSTRUCTIONS_GROUP
// Shifts have immediate form in addition to register-to-register form.
#define DEFINE_XMM_INT_INSTRUCTIONS_GROUP(insn_name, asm_name, type_check, signed, unsigned) \
  DEFINE_XMM_INT_INSTRUCTION(P##insn_name,                                                   \
                             P##asm_name,                                                    \
                             type_check,                                                     \
                             (XMMRegister dest, int8_t imm),                                 \
                             (dest, imm),                                                    \
                             signed,                                                         \
                             unsigned)                                                       \
  DEFINE_XMM_INT_INSTRUCTION(Vp##insn_name,                                                  \
                             Vp##asm_name,                                                   \
                             type_check,                                                     \
                             (XMMRegister dest, XMMRegister src, int8_t imm),                \
                             (dest, src, imm),                                               \
                             signed,                                                         \
                             unsigned)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(sl, sl, kIntTypeWLQ, l, l)
DEFINE_XMM_INT_INSTRUCTIONS_GROUP(sr, sr, kIntTypePsr, a, l)
#undef DEFINE_XMM_INT_INSTRUCTIONS_GROUP
#undef DEFINE_XMM_INT_INSTRUCTION

#define DEFINE_MOVS_INSTRUCTION(insn_name, opt_check, parameters, arguments) \
  template <typename format>                                                 \
  std::enable_if_t<kFloatType<format>> insn_name parameters {                \
    if constexpr (kFormatIs<format, Float32>) {                              \
      opt_check;                                                             \
      Assembler::insn_name##s arguments;                                     \
    } else {                                                                 \
      opt_check;                                                             \
      Assembler::insn_name##d arguments;                                     \
    }                                                                        \
  }
DEFINE_MOVS_INSTRUCTION(Movs, , (XMMRegister dest, Operand src), (dest, src))
DEFINE_MOVS_INSTRUCTION(Movs, , (Operand dest, XMMRegister src), (dest, src))
DEFINE_MOVS_INSTRUCTION(Movs,
                        if (dest == src) return,
                        (XMMRegister dest, XMMRegister src),
                        (dest, src))
DEFINE_MOVS_INSTRUCTION(Vmovs, , (XMMRegister dest, Operand src), (dest, src))
DEFINE_MOVS_INSTRUCTION(Vmovs, , (Operand dest, XMMRegister src), (dest, src))
DEFINE_MOVS_INSTRUCTION(Vmovs,
                        if ((dest == src1) && (dest == src2)) return,
                        (XMMRegister dest, XMMRegister src1, XMMRegister src2),
                        (dest, src1, src2))
#undef DEFINE_MOVS_INSTRUCTION

#define DEFINE_XMM_MOV_INSTRUCTION(insn_name, parameters, arguments) \
  template <typename format>                                         \
  std::enable_if_t<kFloatType<format>> insn_name parameters {        \
    if constexpr (kFormatIs<format, Float32>) {                      \
      Assembler::insn_name##d arguments;                             \
    } else {                                                         \
      Assembler::insn_name##q arguments;                             \
    }                                                                \
  }
DEFINE_XMM_MOV_INSTRUCTION(Mov, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Mov, (Operand dest, XMMRegister src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Mov, (XMMRegister dest, Register src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Mov, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Vmov, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Vmov, (Operand dest, XMMRegister src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Vmov, (XMMRegister dest, Register src), (dest, src))
DEFINE_XMM_MOV_INSTRUCTION(Vmov, (Register dest, XMMRegister src), (dest, src))
#undef DEFINE_XMM_MOV_INSTRUCTION

#define DEFINE_XMM_CVT_INSTRUCTION(insn_name, parameters, arguments)                           \
  template <typename FormatFrom, typename FormatTo>                                            \
  std::enable_if_t<kFloatType<FormatFrom> && kSignedIntType<FormatTo> && kIntTypeLQ<FormatTo>> \
      insn_name parameters {                                                                   \
    if constexpr (kFormatIs<FormatFrom, Float32> && kFormatIs<FormatTo, int32_t>) {            \
      Assembler::insn_name##ss2sil arguments;                                                  \
    } else if constexpr (kFormatIs<FormatFrom, Float32> && kFormatIs<FormatTo, int64_t>) {     \
      Assembler::insn_name##ss2siq(dest, src);                                                 \
    } else if constexpr (kFormatIs<FormatFrom, Float64> && kFormatIs<FormatTo, int32_t>) {     \
      Assembler::insn_name##sd2sil(dest, src);                                                 \
    } else {                                                                                   \
      Assembler::insn_name##sd2siq(dest, src);                                                 \
    }                                                                                          \
  }
DEFINE_XMM_CVT_INSTRUCTION(Cvt, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvt, (Register dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvtt, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvtt, (Register dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, (Register dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvtt, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvtt, (Register dest, Operand src), (dest, src))
#undef DEFINE_XMM_CVT_INSTRUCTION

#define DEFINE_XMM_CVT_INSTRUCTION(insn_name, parameters, arguments)                             \
  template <typename FormatFrom, typename FormatTo>                                              \
  std::enable_if_t<kSignedIntType<FormatFrom> && kIntTypeWL<FormatFrom> && kFloatType<FormatTo>> \
      insn_name parameters {                                                                     \
    if constexpr (kFormatIs<FormatFrom, Float32> && kFormatIs<FormatTo, int32_t>) {              \
      Assembler::insn_name##sil2ss arguments;                                                    \
    } else if constexpr (kFormatIs<FormatFrom, Float32> && kFormatIs<FormatTo, int64_t>) {       \
      Assembler::insn_name##siq2ss(dest, src);                                                   \
    } else if constexpr (kFormatIs<FormatFrom, Float64> && kFormatIs<FormatTo, int32_t>) {       \
      Assembler::insn_name##sil2sd(dest, src);                                                   \
    } else {                                                                                     \
      Assembler::insn_name##siq2sd(dest, src);                                                   \
    }                                                                                            \
  }
DEFINE_XMM_CVT_INSTRUCTION(Cvt, (XMMRegister dest, Register src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvt, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, (XMMRegister dest, Register src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, (XMMRegister dest, Operand src), (dest, src))
#undef DEFINE_XMM_CVT_INSTRUCTION

#define DEFINE_XMM_CVT_INSTRUCTION(insn_name, insn_suffix, parameters, arguments)   \
  template <typename FormatFrom, typename FormatTo>                                 \
  std::enable_if_t<kFloatType<FormatFrom> && kFloatType<FormatTo> &&                \
                   sizeof(FormatFrom) != sizeof(FormatTo)>                          \
      insn_name##insn_suffix parameters {                                           \
    if constexpr (kFormatIs<FormatFrom, Float32> && kFormatIs<FormatTo, Float64>) { \
      Assembler::insn_name##insn_suffix##s2##insn_suffix##d arguments;              \
    } else {                                                                        \
      Assembler::insn_name##insn_suffix##d2##insn_suffix##s arguments;              \
    }                                                                               \
  }
DEFINE_XMM_CVT_INSTRUCTION(Cvt, p, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvt, p, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvt, s, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Cvt, s, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, p, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt, p, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt,
                           s,
                           (XMMRegister dest, XMMRegister src1, XMMRegister src2),
                           (dest, src1, src2))
DEFINE_XMM_CVT_INSTRUCTION(Vcvt,
                           s,
                           (XMMRegister dest, XMMRegister src1, Operand src2),
                           (dest, src1, src2))
#undef DEFINE_XMM_CVT_INSTRUCTION

#define DEFINE_XMM_FLOAT_INSTRUCTION(insn_name, parameters, arguments) \
  template <typename format>                                           \
  std::enable_if_t<kFloatType<format>> insn_name parameters {          \
    if constexpr (kFormatIs<format, Float32>) {                        \
      Assembler::insn_name##s arguments;                               \
    } else {                                                           \
      Assembler::insn_name##d arguments;                               \
    }                                                                  \
  }
DEFINE_XMM_FLOAT_INSTRUCTION(Comis, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Comis, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Ucomis, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Ucomis, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Vcomis, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Vcomis, (XMMRegister dest, XMMRegister src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Vucomis, (XMMRegister dest, Operand src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Vucomis, (XMMRegister dest, XMMRegister src), (dest, src))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR(insn_name, vinsn_name)                      \
  DEFINE_XMM_FLOAT_INSTRUCTION(insn_name##s, (XMMRegister dest, Operand src), (dest, src))     \
  DEFINE_XMM_FLOAT_INSTRUCTION(insn_name##s, (XMMRegister dest, XMMRegister src), (dest, src)) \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                                \
      vinsn_name##s, (XMMRegister dest, XMMRegister src1, Operand src2), (dest, src1, src2))   \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                                \
      vinsn_name##s, (XMMRegister dest, XMMRegister src1, XMMRegister src2), (dest, src1, src2))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(insn_name, vinsn_name)                      \
  DEFINE_XMM_FLOAT_INSTRUCTION(insn_name##p, (XMMRegister dest, Operand src), (dest, src))     \
  DEFINE_XMM_FLOAT_INSTRUCTION(insn_name##p, (XMMRegister dest, XMMRegister src), (dest, src)) \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                                \
      vinsn_name##p, (XMMRegister dest, XMMRegister src1, Operand src2), (dest, src1, src2))   \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                                \
      vinsn_name##p, (XMMRegister dest, XMMRegister src1, XMMRegister src2), (dest, src1, src2))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(insn_name, vinsn_name)  \
  DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR(insn_name, vinsn_name) \
  DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(insn_name, vinsn_name)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Add, Vadd)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpeq, Vcmpeq)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmple, Vcmple)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmplt, Vcmplt)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpord, Vcmpord)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpneq, Vcmpneq)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpnle, Vcmpnle)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpnlt, Vcmpnlt)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Cmpunord, Vcmpunord)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Hadd, Vhadd)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Max, Vmax)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Min, Vmin)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Mul, Vmul)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Sub, Vsub)
// Note: logical operations 𝐫𝐞𝐚𝐥𝐥𝐲 don't have scalar versions!
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(And, Vand)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(Or, Vor)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(Xor, Vxor)
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR(insn_name)                               \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                             \
      insn_name##s, (XMMRegister dest, XMMRegister src1, Operand src2), (dest, src1, src2)) \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                             \
      insn_name##s, (XMMRegister dest, XMMRegister src1, XMMRegister src2), (dest, src1, src2))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(insn_name)                               \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                             \
      insn_name##p, (XMMRegister dest, XMMRegister src1, Operand src2), (dest, src1, src2)) \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                             \
      insn_name##p, (XMMRegister dest, XMMRegister src1, XMMRegister src2), (dest, src1, src2))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(insn_name)  \
  DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR(insn_name) \
  DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(insn_name)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmadd132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmadd213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmadd231)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmaddsub132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmaddsub213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmaddsub231)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsub132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsub213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsub231)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsubadd132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsubadd213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsubadd231)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmadd132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmadd213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmadd231)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmsub132)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmsub213)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmsub231)
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR(insn_name)                   \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##s,                                                             \
      (XMMRegister dest, XMMRegister src1, XMMRegister src2, Operand src3),     \
      (dest, src1, src2, src3))                                                 \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##s,                                                             \
      (XMMRegister dest, XMMRegister src1, Operand src2, XMMRegister src3),     \
      (dest, src1, src2, src3))                                                 \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##s,                                                             \
      (XMMRegister dest, XMMRegister src1, XMMRegister src2, XMMRegister src3), \
      (dest, src1, src2, src3))
#define DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED(insn_name)                   \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##p,                                                             \
      (XMMRegister dest, XMMRegister src1, XMMRegister src2, Operand src3),     \
      (dest, src1, src2, src3))                                                 \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##p,                                                             \
      (XMMRegister dest, XMMRegister src1, Operand src2, XMMRegister src3),     \
      (dest, src1, src2, src3))                                                 \
  DEFINE_XMM_FLOAT_INSTRUCTION(                                                 \
      insn_name##p,                                                             \
      (XMMRegister dest, XMMRegister src1, XMMRegister src2, XMMRegister src3), \
      (dest, src1, src2, src3))
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmadd)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmaddsub)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsubadd)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfmsub)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmadd)
DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP(Vfnmsub)
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_PACKED
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP_SCALAR
#undef DEFINE_XMM_FLOAT_INSTRUCTIONS_GROUP
// Special, unique, instructions
//   Movmskps/Movmskpd doesn't support memoru operand, Round[sp][sd] two arguments and immediate.
DEFINE_XMM_FLOAT_INSTRUCTION(Movmskp, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Vmovmskp, (Register dest, XMMRegister src), (dest, src))
DEFINE_XMM_FLOAT_INSTRUCTION(Roundp,
                             (XMMRegister dest, XMMRegister src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Rounds,
                             (XMMRegister dest, XMMRegister src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Roundp,
                             (XMMRegister dest, Operand src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Rounds,
                             (XMMRegister dest, Operand src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Vroundp,
                             (XMMRegister dest, XMMRegister src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Vrounds,
                             (XMMRegister dest, XMMRegister src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Vroundp,
                             (XMMRegister dest, Operand src, uint8_t imm8),
                             (dest, src, imm8))
DEFINE_XMM_FLOAT_INSTRUCTION(Vrounds,
                             (XMMRegister dest, Operand src, uint8_t imm8),
                             (dest, src, imm8))
#undef DEFINE_XMM_FLOAT_INSTRUCTION
