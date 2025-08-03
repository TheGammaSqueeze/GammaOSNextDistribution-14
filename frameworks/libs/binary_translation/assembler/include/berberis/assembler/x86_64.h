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

// Assembler to produce x86-64 instructions. Somewhat influenced by V8 assembler.

#ifndef BERBERIS_ASSEMBLER_X86_64_H_
#define BERBERIS_ASSEMBLER_X86_64_H_

#include <type_traits>  // std::is_same

#include "berberis/assembler/common_x86.h"
#include "berberis/base/logging.h"
#include "berberis/base/macros.h"  // DISALLOW_IMPLICIT_CONSTRUCTORS

namespace berberis {

class MachindeCode;

namespace x86_64 {

class Assembler : public AssemblerX86<Assembler> {
 public:
  explicit Assembler(MachineCode* code) : AssemblerX86(code) {}

  static constexpr Register no_register{0x80};
  static constexpr Register rax{0};
  static constexpr Register rcx{1};
  static constexpr Register rdx{2};
  static constexpr Register rbx{3};
  static constexpr Register rsp{4};
  static constexpr Register rbp{5};
  static constexpr Register rsi{6};
  static constexpr Register rdi{7};
  static constexpr Register r8{8};
  static constexpr Register r9{9};
  static constexpr Register r10{10};
  static constexpr Register r11{11};
  static constexpr Register r12{12};
  static constexpr Register r13{13};
  static constexpr Register r14{14};
  static constexpr Register r15{15};

  static constexpr XMMRegister no_xmm_register{0x80};
  static constexpr XMMRegister xmm0{0};
  static constexpr XMMRegister xmm1{1};
  static constexpr XMMRegister xmm2{2};
  static constexpr XMMRegister xmm3{3};
  static constexpr XMMRegister xmm4{4};
  static constexpr XMMRegister xmm5{5};
  static constexpr XMMRegister xmm6{6};
  static constexpr XMMRegister xmm7{7};
  static constexpr XMMRegister xmm8{8};
  static constexpr XMMRegister xmm9{9};
  static constexpr XMMRegister xmm10{10};
  static constexpr XMMRegister xmm11{11};
  static constexpr XMMRegister xmm12{12};
  static constexpr XMMRegister xmm13{13};
  static constexpr XMMRegister xmm14{14};
  static constexpr XMMRegister xmm15{15};

  // Macroassembler uses these names to support both x86-32 and x86-64 modes.
  static constexpr Register gpr_a{0};
  static constexpr Register gpr_c{1};
  static constexpr Register gpr_d{2};
  static constexpr Register gpr_s{4};

// Instructions.
#include "berberis/assembler/gen_assembler_x86_64-inl.h"  // NOLINT generated file!

  // Historical curiosity: x86-32 mode has Movq for memory-to-xmm operations.
  // x86-64 added another one, with different opcode but since they are functionally equivalent
  // GNU Assembler and Clang use old one both in 32-bit mode and 64-bit mode thus we are doing
  // the same.

  // Unhide Decl(Mem) hidden by Decl(Reg).
  using AssemblerX86::Decl;

  // Unhide Decw(Mem) hidden by Decw(Reg).
  using AssemblerX86::Decw;

  // Unhide Incl(Mem) hidden by Incl(Reg).
  using AssemblerX86::Incl;

  // Unhide Incw(Mem) hidden by Incw(Reg).
  using AssemblerX86::Incw;

  // Unhide Movq(Mem, XMMReg) and Movq(XMMReg, Mem) hidden by Movq(Reg, Imm) and many others.
  using AssemblerX86::Movq;

  // Unhide Xchgl(Mem, Reg) hidden by modified version below.
  using AssemblerX86::Xchgl;

  // Unhide Vmov*(Mem, Reg) hidden by Vmov*(Reg, Reg).
  using AssemblerX86::Vmovapd;
  using AssemblerX86::Vmovaps;
  using AssemblerX86::Vmovdqa;
  using AssemblerX86::Vmovdqu;
  using AssemblerX86::Vmovq;
  using AssemblerX86::Vmovsd;
  using AssemblerX86::Vmovss;

  void Xchgl(Register dest, Register src) {
    // In 32-bit mode "xchgl %eax, %eax" did nothing and was often reused as "nop".
    //
    // On x86-64 "xchgl %eax, %eax" clears top half of %eax register, but having single-byte nop
    // is too convenient, thus, as special exception, 0x90 is not interpreted as "xchgl %eax, %eax",
    // but was kept as "nop" - thus longer encoding for "xchgl %eax, %eax" must be used.

    if (IsAccumulator(src) && IsAccumulator(dest)) {
      Emit16(0xc087);
    } else {
      AssemblerX86::Xchgl(dest, src);
    }
  }

  // TODO(b/127356868): decide what to do with these functions when cross-arch assembler is used.

#ifdef __amd64__

  // Unhide Call(Reg), hidden by special version below.
  using AssemblerX86::Call;

  void Call(const void* target) {
    // There are no call instruction with properties we need thus we emulate it.
    // This is what the following code looks like when decoded with objdump (if
    // target address is 0x123456789abcdef0):
    //   0: ff 15 02 00 00 00        callq  *0x2(%rip) # 0x8
    //   6: eb 08                    jmp    0x10
    //   8: f0 de bc 9a 78 56 34 12  lock fidivrs 0x12345678(%rdx,%rbx,4)
    // First we do call - with address taken from last 8 bytes, then we jump over
    // these 8 bytes.
    Emit64(0x08eb0000000215ff);
    Emit64(bit_cast<int64_t>(target));
  }

  // Unhide Jcc(Label), hidden by special version below.
  using AssemblerX86::Jcc;

  // Make sure only type void* can be passed to function below, not Label* or any other type.
  template <typename T>
  auto Jcc(Condition cc, T* target) -> void = delete;

  void Jcc(Condition cc, const void* target) {
    if (cc == Condition::kAlways) {
      Jmp(target);
      return;
    } else if (cc == Condition::kNever) {
      return;
    }
    CHECK_EQ(0, static_cast<uint8_t>(cc) & 0xF0);
    // There are no Jcc instruction with properties we need thus we emulate it.
    // This is what the following code looks like when decoded with objdump (if
    // target address is 0x123456789abcdef0):
    //   0: 75 0e                   jne    0x10
    //   2: ff 25 00 00 00 00       jmpq   *0x0(%rip) # 0x8
    //   8: f0 de bc 9a 78 56 34 12 lock fidivrs 0x12345678(%rdx,%rbx,4)
    // We are doing relative jump for the inverted condition (because Jcc could
    // only jump ±2GiB and in 64 bit mode which is not enough to reach arbitrary
    // address), then jmpq with address stored right after jmpq.
    Emit64(0x0000000025ff'0e70 | static_cast<int8_t>(ToReverseCond(cc)));
    Emit64(bit_cast<int64_t>(target));
  }

  // Unhide Jmp(Reg), hidden by special version below.
  using AssemblerX86::Jmp;

  // Make sure only type void* can be passed to function below, not Label* or any other type.
  template <typename T>
  auto Jmp(Condition cc, T* target) -> void = delete;

  void Jmp(const void* target) {
    // There are no jump instruction with properties we need thus we emulate it.
    // This is what the following code looks like when decoded with objdump (if
    // target address is 0x123456789abcdef0):
    //   0: ff 25 00 00 00 00       jmpq   *0x0(%rip) # 0x6
    //   6: f0 de bc 9a 78 56 34 12 lock fidivrs 0x12345678(%rdx,%rbx,4)
    // We are doing jump to the address stored right after jmpq using %rip-relative
    // addressing (with offset 0).
    Emit16(0x25ff);
    Emit32(0x00000000);
    Emit64(bit_cast<int64_t>(target));
  }

#endif

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(Assembler);

  static Register Accumulator() { return rax; }
  static bool IsAccumulator(Register reg) { return reg == rax; }

  struct Register64Bit {
    explicit constexpr Register64Bit(Register reg) : num(reg.num) {}
    uint8_t num;
  };

  struct Memory64Bit {
    explicit Memory64Bit(const Operand& op) : operand(op) {}
    Operand operand;
  };

  struct Label64Bit {
    explicit Label64Bit(const LabelOperand& l) : label(l.label) {}
    const Label& label;
  };

  // This type is only used by CmpXchg16b and acts similarly to Memory64Bit there.
  using Memory128Bit = Memory64Bit;
  using Label128Bit = Label64Bit;

  // Check if a given type is "a register with size" (for EmitInstruction).
  template <typename ArgumentType>
  struct IsRegister {
    static constexpr bool value = std::is_same_v<ArgumentType, Register8Bit> ||
                                  std::is_same_v<ArgumentType, Register32Bit> ||
                                  std::is_same_v<ArgumentType, Register64Bit>;
  };

  // Check if a given type is "a memory operand with size" (for EmitInstruction).
  template <typename ArgumentType>
  struct IsMemoryOperand {
    static constexpr bool value =
        std::is_same_v<ArgumentType, Memory32Bit> || std::is_same_v<ArgumentType, Memory64Bit>;
  };

  template <typename ArgumentType>
  struct IsLabelOperand {
    static constexpr bool value =
        std::is_same_v<ArgumentType, Label32Bit> || std::is_same_v<ArgumentType, Label64Bit>;
  };

  template <typename... ArgumentsTypes>
  void EmitRex(ArgumentsTypes... arguments) {
    constexpr auto registers_count = kCountArguments<IsRegister, ArgumentsTypes...>;
    constexpr auto operands_count = kCountArguments<IsMemoryOperand, ArgumentsTypes...>;
    static_assert(registers_count + operands_count <= 2,
                  "Only two-arguments instructions are supported, not VEX or EVEX");
    uint8_t rex = 0;
    if constexpr (registers_count == 2) {
      rex = Rex<0b0100>(ArgumentByType<0, IsRegister>(arguments...)) |
            Rex<0b0001>(ArgumentByType<1, IsRegister>(arguments...));
    } else if constexpr (registers_count == 1 && operands_count == 1) {
      rex = Rex<0b0100>(ArgumentByType<0, IsRegister>(arguments...)) |
            Rex(ArgumentByType<0, IsMemoryOperand>(arguments...));
    } else if constexpr (registers_count == 1) {
      rex = Rex<0b0001>(ArgumentByType<0, IsRegister>(arguments...));
    } else if constexpr (operands_count == 1) {
      rex = Rex(ArgumentByType<0, IsMemoryOperand>(arguments...));
    }
    if (rex) {
      Emit8(rex);
    }
  }

  template <uint8_t base_rex, typename ArgumentType>
  uint8_t Rex(ArgumentType argument) {
    if (argument.num & 0b1000) {
      // 64-bit argument requires REX.W bit
      if (std::is_same_v<ArgumentType, Register64Bit>) {
        return 0b0100'1000 | base_rex;
      }
      return 0b0100'0000 | base_rex;
    }
    // 8-bit argument requires REX (even if without any bits).
    if (std::is_same_v<ArgumentType, Register8Bit> && argument.num > 3) {
      return 0b0100'0000;
    }
    if (std::is_same_v<ArgumentType, Register64Bit>) {
      return 0b0100'1000;
    }
    return 0;
  }

  uint8_t Rex(Operand operand) {
    // REX.B and REX.X always come from operand.
    uint8_t rex = ((operand.base.num & 0b1000) >> 3) | ((operand.index.num & 0b1000) >> 2);
    if (rex) {
      // We actually need rex byte here.
      return 0b0100'0000 | rex;
    } else {
      return 0;
    }
  }

  uint8_t Rex(Memory32Bit operand) { return Rex(operand.operand); }

  uint8_t Rex(Memory64Bit operand) {
    // 64-bit argument requires REX.W bit - and thus REX itself.
    return 0b0100'1000 | Rex(operand.operand);
  }

  template <typename RegisterType>
  [[nodiscard]] static bool IsSwapProfitable(RegisterType rm_arg, RegisterType vex_arg) {
    // In 64bit mode we may use more compact encoding if operand encoded in rm is low register.
    // Return true if we may achieve that by swapping arguments.
    return rm_arg.num >= 8 && vex_arg.num < 8;
  }

  template <uint8_t byte1,
            uint8_t byte2,
            uint8_t byte3,
            bool reg_is_opcode_extension,
            typename... ArgumentsTypes>
  void EmitVex(ArgumentsTypes... arguments) {
    constexpr auto registers_count = kCountArguments<IsRegister, ArgumentsTypes...>;
    constexpr auto operands_count = kCountArguments<IsMemoryOperand, ArgumentsTypes...>;
    constexpr auto labels_count = kCountArguments<IsLabelOperand, ArgumentsTypes...>;
    constexpr auto vvvv_parameter = 2 - reg_is_opcode_extension - operands_count - labels_count;
    int vvvv = 0;
    if constexpr (registers_count > vvvv_parameter) {
      vvvv = ArgumentByType<vvvv_parameter, IsRegister>(arguments...).num;
    }
    auto vex2 = byte2 | 0b111'00000;
    if constexpr (operands_count == 1) {
      auto operand = ArgumentByType<0, IsMemoryOperand>(arguments...);
      vex2 ^= (operand.operand.base.num & 0b1000) << 2;
      vex2 ^= (operand.operand.index.num & 0b1000) << 3;
      if constexpr (!reg_is_opcode_extension) {
        vex2 ^= (ArgumentByType<0, IsRegister>(arguments...).num & 0b1000) << 4;
      }
    } else if constexpr (labels_count == 1) {
      if constexpr (!reg_is_opcode_extension) {
        vex2 ^= (ArgumentByType<0, IsRegister>(arguments...).num & 0b1000) << 4;
      }
    } else if constexpr (registers_count > 0) {
      if constexpr (reg_is_opcode_extension) {
        vex2 ^= (ArgumentByType<0, IsRegister>(arguments...).num & 0b1000) << 2;
      } else {
        vex2 ^= (ArgumentByType<0, IsRegister>(arguments...).num & 0b1000) << 4;
        vex2 ^= (ArgumentByType<1, IsRegister>(arguments...).num & 0b1000) << 2;
      }
    }
    if (byte1 == 0xC4 && (vex2 & 0b0'1'1'11111) == 0b0'1'1'00001 && (byte3 & 0b1'0000'0'00) == 0) {
      Emit16((0xc5 | ((vex2 & 0b1'0'0'00000) << 8) | (byte3 << 8) |
              0b0'1111'000'00000000) ^ (vvvv << 11));
    } else {
      Emit8(byte1);
      Emit16((vex2 | (byte3 << 8) | 0b0'1111'000'00000000) ^ (vvvv << 11));
    }
  }

  template <typename ArgumentType>
  void EmitRegisterInOpcode(uint8_t opcode, ArgumentType argument) {
    Emit8(opcode | (argument.num & 0b111));
  }

  template <typename ArgumentType1, typename ArgumentType2>
  void EmitModRM(ArgumentType1 argument1, ArgumentType2 argument2) {
    Emit8(0xC0 | ((argument1.num & 0b111) << 3) | (argument2.num & 0b111));
  }

  template <typename ArgumentType>
  void EmitModRM(uint8_t opcode_extension, ArgumentType argument) {
    CHECK_LE(opcode_extension, 0b111);
    Emit8(0xC0 | (opcode_extension << 3) | (argument.num & 0b111));
  }

  template <typename ArgumentType>
  void EmitOperandOp(ArgumentType argument, Operand operand) {
    EmitOperandOp(static_cast<int>(argument.num & 0b111), operand);
  }

  template <size_t kImmediatesSize, typename ArgumentType>
  void EmitRipOp(ArgumentType argument, const Label& label) {
    EmitRipOp<kImmediatesSize>(static_cast<int>(argument.num) & 0b111, label);
  }

  // Emit the ModR/M byte, and optionally the SIB byte and
  // 1- or 4-byte offset for a memory operand.  Also used to encode
  // a three-bit opcode extension into the ModR/M byte.
  void EmitOperandOp(int number, const Operand& addr);
  // Helper functions to handle various ModR/M and SIB combinations.
  // Should *only* be called from EmitOperandOp!
  void EmitIndexDispOperand(int reg, const Operand& addr);
  template <typename ArgType, void (AssemblerBase::*)(ArgType)>
  void EmitBaseIndexDispOperand(int base_modrm_and_sib, const Operand& addr);
  // Emit ModR/M for rip-addressig.
  template <size_t kImmediatesSize>
  void EmitRipOp(int num, const Label& label);

  friend AssemblerX86<Assembler>;
};

// This function looks big, but when we are emitting Operand with fixed registers
// (which is the most common case) all "if"s below are calculated statically which
// makes effective size of that function very small.
//
// But for this to happen function have to be inline and in header.
inline void Assembler::EmitOperandOp(int number, const Operand& addr) {
  // Additional info (register number, etc) is limited to 3 bits.
  CHECK_LE(unsigned(number), 7);

  // Reg field must be shifted by 3 bits.
  int reg = number << 3;

  // On x86 %rsp cannot be index, only base.
  CHECK(addr.index != rsp);

  // If base is not %rsp/r12 and we don't have index, then we don't have SIB byte.
  // All other cases have "ModR/M" and SIB bytes.
  if (addr.base != rsp && addr.base != r12 && addr.index == no_register) {
    // If we have base register then we could use the same logic as for other common cases.
    if (addr.base != no_register) {
      EmitBaseIndexDispOperand<uint8_t, &Assembler::Emit8>((addr.base.num & 7) | reg, addr);
    } else {
      Emit16(0x2504 | reg);
      Emit32(addr.disp);
    }
  } else if (addr.index == no_register) {
    // Note: when ModR/M and SIB are used "no index" is encoded as if %rsp is used in place of
    // index (that's why %rsp couldn't be used as index - see check above).
    EmitBaseIndexDispOperand<int16_t, &Assembler::Emit16>(0x2004 | ((addr.base.num & 7) << 8) | reg,
                                                          addr);
  } else if (addr.base == no_register) {
    EmitIndexDispOperand(reg, addr);
  } else {
    EmitBaseIndexDispOperand<int16_t, &Assembler::Emit16>(
        0x04 | (addr.scale << 14) | ((addr.index.num & 7) << 11) | ((addr.base.num & 7) << 8) | reg,
        addr);
  }
}

inline void Assembler::EmitIndexDispOperand(int reg, const Operand& addr) {
  // We only have index here, no base, use SIB but put %rbp in "base" field.
  Emit16(0x0504 | (addr.scale << 14) | ((addr.index.num & 7) << 11) | reg);
  Emit32(addr.disp);
}

template <size_t kImmediatesSize>
inline void Assembler::EmitRipOp(int num, const Label& label) {
  Emit8(0x05 | (num << 3));
  jumps_.push_back(Jump{&label, pc(), false});
  Emit32(0xfffffffc - kImmediatesSize);
}

template <typename ArgType, void (AssemblerBase::*EmitBase)(ArgType)>
inline void Assembler::EmitBaseIndexDispOperand(int base_modrm_and_sib, const Operand& addr) {
  if (addr.disp == 0 && addr.base != rbp && addr.base != r13) {
    // We can omit zero displacement only if base isn't %rbp/%r13
    (this->*EmitBase)(base_modrm_and_sib);
  } else if (IsInRange<int8_t>(addr.disp)) {
    // If disp could it in byte then use byte-disp.
    (this->*EmitBase)(base_modrm_and_sib | 0x40);
    Emit8(addr.disp);
  } else {
    // Otherwise use full-disp.
    (this->*EmitBase)(base_modrm_and_sib | 0x80);
    Emit32(addr.disp);
  }
}

inline void Assembler::Movq(Register dest, int64_t imm64) {
  if (IsInRange<uint32_t>(imm64)) {
    // Shorter encoding.
    Movl(dest, static_cast<uint32_t>(imm64));
  } else if (IsInRange<int32_t>(imm64)) {
    // Slightly longer encoding.
    EmitInstruction<Opcodes<0xc7, 0x00>>(Register64Bit(dest), static_cast<int32_t>(imm64));
  } else {
    // Longest encoding.
    EmitInstruction<Opcodes<0xb8>>(Register64Bit(dest), imm64);
  }
}

inline void Assembler::Vmovapd(XMMRegister arg0, XMMRegister arg1) {
  if (arg0.num < 8 && arg1.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x01, 0x29>>(VectorRegister128Bit(arg1),
                                                            VectorRegister128Bit(arg0));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x01, 0x28>>(VectorRegister128Bit(arg0),
                                                   VectorRegister128Bit(arg1));
}

inline void Assembler::Vmovaps(XMMRegister arg0, XMMRegister arg1) {
  if (arg0.num < 8 && arg1.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x00, 0x29>>(VectorRegister128Bit(arg1),
                                                            VectorRegister128Bit(arg0));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x00, 0x28>>(VectorRegister128Bit(arg0),
                                                   VectorRegister128Bit(arg1));
}

inline void Assembler::Vmovdqa(XMMRegister arg0, XMMRegister arg1) {
  if (arg0.num < 8 && arg1.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x01, 0x7F>>(VectorRegister128Bit(arg1),
                                                            VectorRegister128Bit(arg0));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x01, 0x6F>>(VectorRegister128Bit(arg0),
                                                   VectorRegister128Bit(arg1));
}

inline void Assembler::Vmovdqu(XMMRegister arg0, XMMRegister arg1) {
  if (arg0.num < 8 && arg1.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x02, 0x7F>>(VectorRegister128Bit(arg1),
                                                            VectorRegister128Bit(arg0));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x02, 0x6F>>(VectorRegister128Bit(arg0),
                                                   VectorRegister128Bit(arg1));
}

inline void Assembler::Vmovsd(XMMRegister arg0, XMMRegister arg1, XMMRegister arg2) {
  if (arg0.num < 8 && arg2.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x03, 0x11>>(
        VectorRegister128Bit(arg2), VectorRegister128Bit(arg0), VectorRegister128Bit(arg1));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x03, 0x10>>(
      VectorRegister128Bit(arg0), VectorRegister128Bit(arg2), VectorRegister128Bit(arg1));
}

inline void Assembler::Vmovss(XMMRegister arg0, XMMRegister arg1, XMMRegister arg2) {
  if (arg0.num < 8 && arg2.num >= 8) {
    return EmitInstruction<Opcodes<0xc4, 0x01, 0x02, 0x11>>(
        VectorRegister128Bit(arg2), VectorRegister128Bit(arg0), VectorRegister128Bit(arg1));
  }
  EmitInstruction<Opcodes<0xc4, 0x01, 0x02, 0x10>>(
      VectorRegister128Bit(arg0), VectorRegister128Bit(arg2), VectorRegister128Bit(arg1));
}

inline void Assembler::Xchgq(Register dest, Register src) {
  // We compare output to that from clang and thus want to produce the same code.
  // 0x48 0x90 is suboptimal encoding for that operation (pure 0x90 does the same
  // and this is what gcc + gas are producing), but this is what clang <= 8 does.
#if __clang_major__ >= 8
  if (IsAccumulator(src) && IsAccumulator(dest)) {
    Emit8(0x90);
  } else
#endif
  if (IsAccumulator(src) || IsAccumulator(dest)) {
    Register other = IsAccumulator(src) ? dest : src;
    EmitInstruction<Opcodes<0x90>>(Register64Bit(other));
  } else {
  // Clang 8 (after r330298) swaps these two arguments.  We are comparing output
  // to clang in exhaustive test thus we want to match clang behavior exactly.
#if __clang_major__ >= 8
    EmitInstruction<Opcodes<0x87>>(Register64Bit(dest), Register64Bit(src));
#else
    EmitInstruction<Opcodes<0x87>>(Register64Bit(src), Register64Bit(dest));
#endif
  }
}

}  // namespace x86_64

}  // namespace berberis

#endif  // BERBERIS_ASSEMBLER_X86_64_H_
