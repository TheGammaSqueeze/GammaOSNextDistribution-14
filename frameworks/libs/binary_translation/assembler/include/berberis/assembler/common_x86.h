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

#ifndef BERBERIS_ASSEMBLER_COMMON_X86_H_
#define BERBERIS_ASSEMBLER_COMMON_X86_H_

#include <cstddef>  // std::size_t
#include <cstdint>
#include <type_traits>  // std::enable_if, std::is_integral

#include "berberis/assembler/common.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/base/macros.h"  // DISALLOW_IMPLICIT_CONSTRUCTORS

namespace berberis {

// AssemblerX86 includes implementation of most x86 assembler instructions.
//
// x86-32 and x86-64 assemblers are nearly identical, but difference lies in handling
// of very low-level instruction details: almost all instructions on x86-64 could include
// REX byte which is needed if new registers (%r8 to %r15 or %xmm8 to %xmm15) are used.
//
// To handle that difference efficiently AssemblerX86 is CRTP class: it's parameterized
// by its own descendant and pull certain functions (e.g. GetHighBit or Rex8Size) from
// its implementation.
//
// Certain functions are only implemented by its descendant (since there are instructions
// which only exist in x86-32 mode and instructions which only exist in x86-64 mode).

template <typename Assembler>
class AssemblerX86 : public AssemblerBase {
 public:
  explicit AssemblerX86(MachineCode* code) : AssemblerBase(code) {}

  enum class Condition {
    kInvalidCondition = -1,

    kOverflow = 0,
    kNoOverflow = 1,
    kBelow = 2,
    kAboveEqual = 3,
    kEqual = 4,
    kNotEqual = 5,
    kBelowEqual = 6,
    kAbove = 7,
    kNegative = 8,
    kPositive = 9,
    kParityEven = 10,
    kParityOdd = 11,
    kLess = 12,
    kGreaterEqual = 13,
    kLessEqual = 14,
    kGreater = 15,
    kAlways = 16,
    kNever = 17,

    // aka...
    kCarry = kBelow,
    kNotCarry = kAboveEqual,
    kZero = kEqual,
    kNotZero = kNotEqual,
    kSign = kNegative,
    kNotSign = kPositive
  };

  struct Register {
    // Note: we couldn't make the following private because of peculiarities of C++ (see
    // https://stackoverflow.com/questions/24527395/compiler-error-when-initializing-constexpr-static-class-member
    // for explanation), but you are not supposed to access num or use GetHighBit() and GetLowBits()
    // functions.  Treat that type as opaque cookie.

    constexpr bool operator==(const Register& reg) const { return num == reg.num; }

    constexpr bool operator!=(const Register& reg) const { return num != reg.num; }

    uint8_t num;
  };

  struct X87Register {
    // Note: we couldn't make the following private because of peculiarities of C++ (see
    // https://stackoverflow.com/questions/24527395/compiler-error-when-initializing-constexpr-static-class-member
    // for explanation), but you are not supposed to access num or use GetHighBit() and GetLowBits()
    // functions.  Treat that type as opaque cookie.

    constexpr bool operator==(const Register& reg) const { return num == reg.num; }

    constexpr bool operator!=(const Register& reg) const { return num != reg.num; }

    uint8_t num;
  };

  static constexpr X87Register st{0};
  static constexpr X87Register st0{0};
  static constexpr X87Register st1{1};
  static constexpr X87Register st2{2};
  static constexpr X87Register st3{3};
  static constexpr X87Register st4{4};
  static constexpr X87Register st5{5};
  static constexpr X87Register st6{6};
  static constexpr X87Register st7{7};

  struct XMMRegister {
    // Note: we couldn't make the following private because of peculiarities of C++ (see
    // https://stackoverflow.com/questions/24527395/compiler-error-when-initializing-constexpr-static-class-member
    // for explanation), but you are not supposed to access num or use GetHighBit() and GetLowBits()
    // functions.  Treat that type as opaque cookie.

    constexpr bool operator==(const XMMRegister& reg) const { return num == reg.num; }

    constexpr bool operator!=(const XMMRegister& reg) const { return num != reg.num; }

    uint8_t num;
  };

  enum ScaleFactor { kTimesOne = 0, kTimesTwo = 1, kTimesFour = 2, kTimesEight = 3 };

  struct Operand {
    constexpr uint8_t rex() const {
      return Assembler::kIsX86_64 ? ((index.num & 0x08) >> 2) | ((base.num & 0x08) >> 3) : 0;
    }

    constexpr bool RequiresRex() const {
      return Assembler::kIsX86_64 ? ((index.num & 0x08) | (base.num & 0x08)) : false;
    }

    Register base = Assembler::no_register;
    Register index = Assembler::no_register;
    ScaleFactor scale = kTimesOne;
    int32_t disp = 0;
  };

  struct LabelOperand {
    const Label& label;
  };

  // Macro operations.
  void Finalize() { ResolveJumps(); }

  void Align(uint32_t m) {
    uint32_t mask = m - 1;
    uint32_t addr = pc();
    Nop((m - (addr & mask)) & mask);
  }

  void Nop(uint32_t bytes) {
    static const uint32_t kNumNops = 15;
    static const uint8_t nop1[] = {0x90};
    static const uint8_t nop2[] = {0x66, 0x90};
    static const uint8_t nop3[] = {0x0f, 0x1f, 0x00};
    static const uint8_t nop4[] = {0x0f, 0x1f, 0x40, 0x00};
    static const uint8_t nop5[] = {0x0f, 0x1f, 0x44, 0x00, 0x00};
    static const uint8_t nop6[] = {0x66, 0x0f, 0x1f, 0x44, 0x00, 0x0};
    static const uint8_t nop7[] = {0x0f, 0x1f, 0x80, 0x00, 0x00, 0x0, 0x00};
    static const uint8_t nop8[] = {0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop9[] = {0x66, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop10[] = {0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop11[] = {
        0x66, 0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop12[] = {
        0x66, 0x66, 0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop13[] = {
        0x66, 0x66, 0x66, 0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop14[] = {
        0x66, 0x66, 0x66, 0x66, 0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};
    static const uint8_t nop15[] = {
        0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x2e, 0x0f, 0x1f, 0x84, 0x00, 0x00, 0x00, 0x00, 0x00};

    static const uint8_t* nops[kNumNops] = {nop1,
                                            nop2,
                                            nop3,
                                            nop4,
                                            nop5,
                                            nop6,
                                            nop7,
                                            nop8,
                                            nop9,
                                            nop10,
                                            nop11,
                                            nop12,
                                            nop13,
                                            nop14,
                                            nop15};
    // Common case.
    if (bytes == 1) {
      Emit8(nop1[0]);
      return;
    }

    while (bytes > 0) {
      uint32_t len = bytes;
      if (len > kNumNops) {
        len = kNumNops;
      }
      EmitSequence(nops[len - 1], len);
      bytes -= len;
    }
  }

// Instructions.
#include "berberis/assembler/gen_assembler_common_x86-inl.h"  // NOLINT generated file

  // Flow control.
  void Jmp(int32_t offset) {
    CHECK_GE(offset, INT32_MIN + 2);
    int32_t short_offset = offset - 2;
    if (IsInRange<int8_t>(short_offset)) {
      Emit8(0xeb);
      Emit8(static_cast<int8_t>(short_offset));
    } else {
      CHECK_GE(offset, INT32_MIN + 5);
      Emit8(0xe9);
      Emit32(offset - 5);
    }
  }

  void Call(int32_t offset) {
    CHECK_GE(offset, INT32_MIN + 5);
    Emit8(0xe8);
    Emit32(offset - 5);
  }

  void Jcc(Condition cc, int32_t offset) {
    if (cc == Condition::kAlways) {
      Jmp(offset);
      return;
    }
    if (cc == Condition::kNever) {
      return;
    }
    CHECK_EQ(0, static_cast<uint8_t>(cc) & 0xf0);
    CHECK_GE(offset, INT32_MIN + 2);
    int32_t short_offset = offset - 2;
    if (IsInRange<int8_t>(short_offset)) {
      Emit8(0x70 | static_cast<uint8_t>(cc));
      Emit8(static_cast<int8_t>(short_offset));
    } else {
      CHECK_GE(offset, INT32_MIN + 6);
      Emit8(0x0f);
      Emit8(0x80 | static_cast<uint8_t>(cc));
      Emit32(offset - 6);
    }
  }

 protected:
  // Helper types to distinguish argument types.
  struct Register8Bit {
    explicit constexpr Register8Bit(Register reg) : num(reg.num) {}
    uint8_t num;
  };

  struct Register32Bit {
    explicit constexpr Register32Bit(Register reg) : num(reg.num) {}
    explicit constexpr Register32Bit(XMMRegister reg) : num(reg.num) {}
    uint8_t num;
  };

  // 16-bit and 128-bit vector registers follow the same rules as 32-bit registers.
  using Register16Bit = Register32Bit;
  using VectorRegister128Bit = Register32Bit;
  // Certain instructions (Enter/Leave, Jcc/Jmp/Loop, Call/Ret, Push/Pop) always operate
  // on registers of default size (32-bit in 32-bit mode, 64-bit in 64-bit mode (see
  // "Instructions Not Requiring REX Prefix in 64-Bit Mode" table in 24594 AMD Manual)
  // Map these to Register32Bit, too, since they don't need REX.W even in 64-bit mode.
  //
  // x87 instructions fall into that category, too, since they were not expanded in x86-64 mode.
  using RegisterDefaultBit = Register32Bit;

  struct Memory32Bit {
    explicit Memory32Bit(const Operand& op) : operand(op) {}
    Operand operand;
  };

  // 8-bit, 16-bit, 128-bit memory behave the same as 32-bit memory.
  // Only 64-bit memory is different.
  using Memory8Bit = Memory32Bit;
  using Memory16Bit = Memory32Bit;
  // X87 instructions always use the same encoding - even for 64-bit or 28-bytes
  // memory operands (like in fldenv/fnstenv)
  using MemoryX87 = Memory32Bit;
  using MemoryX8716Bit = Memory32Bit;
  using MemoryX8732Bit = Memory32Bit;
  using MemoryX8764Bit = Memory32Bit;
  using MemoryX8780Bit = Memory32Bit;
  // Most vector instructions don't need to use REX.W to access 64-bit or 128-bit memory.
  using VectorMemory32Bit = Memory32Bit;
  using VectorMemory64Bit = Memory32Bit;
  using VectorMemory128Bit = Memory32Bit;

  // Labels types for memory quantities.  Note that names are similar to the ones before because
  // they are autogenerated.  E.g. VectorLabel32Bit should be read as “VECTOR's operation LABEL
  // for 32-BIT quantity in memory”.
  struct Label32Bit {
    explicit Label32Bit(const struct LabelOperand& l) : label(l.label) {}
    const Label& label;
  };

  // 8-bit, 16-bit, 128-bit memory behave the same as 32-bit memory.
  // Only 64-bit memory is different.
  using Label8Bit = Label32Bit;
  using Label16Bit = Label32Bit;
  // X87 instructions always use the same encoding - even for 64-bit or 28-bytes
  // memory operands (like in fldenv/fnstenv)
  using LabelX87 = Label32Bit;
  using LabelX8716Bit = Label32Bit;
  using LabelX8732Bit = Label32Bit;
  using LabelX8764Bit = Label32Bit;
  using LabelX8780Bit = Label32Bit;
  // Most vector instructions don't need to use REX.W to access 64-bit or 128-bit memory.
  using VectorLabel32Bit = Label32Bit;
  using VectorLabel64Bit = Label32Bit;
  using VectorLabel128Bit = Label32Bit;

  static constexpr bool IsLegacyPrefix(int code) {
    // Legacy prefixes used as opcode extensions in SSE.
    // Lock is used by cmpxchg.
    return (code == 0x66) || (code == 0xf2) || (code == 0xf3) || (code == 0xf0);
  }

  // Delegate check to Assembler::template IsRegister.
  template <typename ArgumentType>
  struct IsCondition {
    static constexpr bool value = std::is_same_v<ArgumentType, Condition>;
  };

  template <typename ArgumentType>
  struct IsRegister {
    static constexpr bool value = Assembler::template IsRegister<ArgumentType>::value ||
                                  std::is_same_v<ArgumentType, X87Register>;
  };

  template <typename ArgumentType>
  struct IsMemoryOperand {
    static constexpr bool value = Assembler::template IsMemoryOperand<ArgumentType>::value;
  };

  template <typename ArgumentType>
  struct IsLabelOperand {
    static constexpr bool value = Assembler::template IsLabelOperand<ArgumentType>::value;
  };

  template <typename ArgumentType>
  struct IsImmediate {
    static constexpr bool value =
        std::is_integral_v<ArgumentType> &&
        ((sizeof(ArgumentType) == sizeof(int8_t)) || (sizeof(ArgumentType) == sizeof(int16_t)) ||
         (sizeof(ArgumentType) == sizeof(int32_t)) || (sizeof(ArgumentType) == sizeof(int64_t)));
  };

  // Count number of arguments selected by Predicate.
  template <template <typename> typename Predicate, typename... ArgumentTypes>
  static constexpr std::size_t kCountArguments = ((Predicate<ArgumentTypes>::value ? 1 : 0) + ... +
                                                  0);

  // Extract arguments selected by Predicate.
  //
  // Note: This interface begs for the trick used in EmitFunctionTypeHelper in make_intrinsics.cc
  // in conjunction with structured bindings.
  //
  // Unfortunately returning std::tuple slows down AssemblerTest by about 30% when libc++ and clang
  // are used together (no slowdown on GCC, no slowdown on clang+libstdc++).
  //
  // TODO(http://b/140721204): refactor when it would be safe to return std::tuple from function.
  //
  template <std::size_t index,
            template <typename>
            typename Predicate,
            typename ArgumentType,
            typename... ArgumentTypes>
  static constexpr auto ArgumentByType(ArgumentType argument, ArgumentTypes... arguments) {
    if constexpr (Predicate<std::decay_t<ArgumentType>>::value) {
      if constexpr (index == 0) {
        return argument;
      } else {
        return ArgumentByType<index - 1, Predicate>(arguments...);
      }
    } else {
      return ArgumentByType<index, Predicate>(arguments...);
    }
  }

  // Emit immediates - they always come at the end and don't affect anything except rip-addressig.
  static constexpr void EmitImmediates() {}

  template <typename FirstArgumentType, typename... ArgumentTypes>
  void EmitImmediates(FirstArgumentType first_argument, ArgumentTypes... other_arguments) {
    if constexpr (std::is_integral_v<FirstArgumentType> &&
                  sizeof(FirstArgumentType) == sizeof(int8_t)) {
      Emit8(first_argument);
    } else if constexpr (std::is_integral_v<FirstArgumentType> &&
                         sizeof(FirstArgumentType) == sizeof(int16_t)) {
      Emit16(first_argument);
    } else if constexpr (std::is_integral_v<FirstArgumentType> &&
                         sizeof(FirstArgumentType) == sizeof(int32_t)) {
      Emit32(first_argument);
    } else if constexpr (std::is_integral_v<FirstArgumentType> &&
                         sizeof(FirstArgumentType) == sizeof(int64_t)) {
      Emit64(first_argument);
    }
    EmitImmediates(other_arguments...);
  }

  template <typename ArgumentType>
  static constexpr size_t ImmediateSize() {
    if constexpr (std::is_integral_v<ArgumentType> && sizeof(ArgumentType) == sizeof(int8_t)) {
      return 1;
    } else if constexpr (std::is_integral_v<ArgumentType> &&
                         sizeof(ArgumentType) == sizeof(int16_t)) {
      return 2;
    } else if constexpr (std::is_integral_v<ArgumentType> &&
                         sizeof(ArgumentType) == sizeof(int32_t)) {
      return 4;
    } else if constexpr (std::is_integral_v<ArgumentType> &&
                         sizeof(ArgumentType) == sizeof(int64_t)) {
      return 8;
    } else {
      static_assert(!std::is_integral_v<ArgumentType>);
      return 0;
    }
  }

  template <typename... ArgumentTypes>
  static constexpr size_t ImmediatesSize() {
    return (ImmediateSize<ArgumentTypes>() + ... + 0);
  }

  // Struct type to pass information about opcodes.
  template <uint8_t... kOpcodes>
  struct Opcodes {};

  template <uint8_t... kOpcodes>
  static constexpr size_t OpcodesCount(Opcodes<kOpcodes...>) {
    return sizeof...(kOpcodes);
  }

  template <uint8_t kOpcode, uint8_t... kOpcodes>
  static constexpr uint8_t FirstOpcode(Opcodes<kOpcode, kOpcodes...>) {
    return kOpcode;
  }

  template <uint8_t kOpcode, uint8_t... kOpcodes>
  static constexpr auto SkipFirstOpcodeFromType(Opcodes<kOpcode, kOpcodes...>) {
    return Opcodes<kOpcodes...>{};
  }

  template <uint8_t kOpcode, uint8_t... kOpcodes>
  auto EmitLegacyPrefixes(Opcodes<kOpcode, kOpcodes...> opcodes) {
    if constexpr (IsLegacyPrefix(kOpcode)) {
      Emit8(kOpcode);
      return EmitLegacyPrefixes(Opcodes<kOpcodes...>{});
    } else {
      return opcodes;
    }
  }

  // Note: We may need separate x87 EmitInstruction if we would want to support
  // full set of x86 instructions.
  //
  // That's because 8087 was completely separate piece of silicone which was only
  // partially driven by 8086:
  //     https://en.wikipedia.org/wiki/Intel_8087
  //
  // In particular it had the following properties:
  //   1. It had its own separate subset of opcodes - because it did its own decoding.
  //   2. It had separate set of registers and could *only* access these.
  //   2a. The 8086, in turn, *couldn't* access these registers at all.
  //   3. To access memory it was designed to take address from address bus.
  //
  // This means that:
  //   1. x87 instructions are easily recognizable - all instructions with opcodes 0xd8
  //      to 0xdf are x87 instructions, all instructions with other opcodes are not.
  //   2. We could be sure that x87 registers would only be used with x87 instructions
  //      and other types of registers wouldn't be used with these.
  //   3. We still would use normal registers for memory access, but REX.W bit wouldn't
  //      be used for 64-bit quantities, whether they are floating point numbers or integers.
  //
  // Right now we only use EmitInstruction to emit x87 instructions which are using memory
  // operands - and it works well enough for that because of #3.

  // If you want to understand how this function works (and how helper function like Vex and
  // Rex work), you need good understanding of AMD/Intel Instruction format.
  //
  // Intel manual includes the most precise explanation, but it's VERY hard to read.
  //
  // AMD manual is much easier to read, but it doesn't include description of EVEX
  // instructions and is less precise. Diagram on page 2 of Volume 3 is especially helpful:
  //   https://www.amd.com/system/files/TechDocs/24594.pdf#page=42
  //
  // And the most concise (albeit unofficial) in on osdev Wiki:
  //   https://wiki.osdev.org/X86-64_Instruction_Encoding

  // Note: if you change this function (or any of the helper functions) then remove --fast
  // option from ExhaustiveAssemblerTest to run full blackbox comparison to clang.

  template <typename InstructionOpcodes, typename... ArgumentsTypes>
  void EmitInstruction(ArgumentsTypes... arguments) {
    auto opcodes_no_prefixes = EmitLegacyPrefixes(InstructionOpcodes{});
    // We don't yet support any XOP-encoded instructions, but they are 100% identical to vex ones,
    // except they are using 0x8F prefix, not 0xC4 prefix.
    constexpr auto vex_xop = [&](auto opcodes) {
      if constexpr (OpcodesCount(opcodes) < 3) {
        return false;
      // Note that JSON files use AMD approach: bytes are specified as in AMD manual (only we are
      // replacing ¬R/¬X/¬B and vvvv bits with zeros).
      //
      // In particular it means that vex-encoded instructions should be specified with 0xC4 even if
      // they are always emitted with 0xC4-to-0xC5 folding.
      } else if constexpr (FirstOpcode(opcodes) == 0xC4 || FirstOpcode(opcodes) == 0x8F) {
        return true;
      }
      return false;
    }(opcodes_no_prefixes);
    constexpr auto conditions_count = kCountArguments<IsCondition, ArgumentsTypes...>;
    constexpr auto operands_count = kCountArguments<IsMemoryOperand, ArgumentsTypes...>;
    constexpr auto labels_count = kCountArguments<IsLabelOperand, ArgumentsTypes...>;
    constexpr auto registers_count = kCountArguments<IsRegister, ArgumentsTypes...>;
    // We need to know if Reg field (in ModRM byte) is an opcode extension or if opcode extension
    // goes into the immediate field.
    constexpr auto reg_is_opcode_extension =
        (registers_count + operands_count > 0) &&
        (registers_count + operands_count + labels_count <
         2 + vex_xop * (OpcodesCount(opcodes_no_prefixes) - 4));
    static_assert((registers_count + operands_count + labels_count + conditions_count +
                   kCountArguments<IsImmediate, ArgumentsTypes...>) == sizeof...(ArgumentsTypes),
                  "Only registers (with specified size), Operands (with specified size), "
                  "Conditions, and Immediates are supported.");
    static_assert(operands_count <= 1, "Only one operand is allowed in instruction.");
    static_assert(labels_count <= 1, "Only one label is allowed in instruction.");
    // 0x0f is an opcode extension, if it's not there then we only have one byte opcode.
    auto opcodes_no_prefixes_no_opcode_extension = [&](auto opcodes) {
      if constexpr (vex_xop) {
        static_assert(conditions_count == 0,
                      "No conditionals are supported in vex/xop instructions.");
        static_assert((registers_count + operands_count + labels_count) <= 4,
                      "Up to four-arguments in vex/xop instructions are supported.");
        constexpr auto vex_xop_byte1 = FirstOpcode(opcodes);
        constexpr auto vex_xop_byte2 = FirstOpcode(SkipFirstOpcodeFromType(opcodes));
        constexpr auto vex_xop_byte3 =
            FirstOpcode(SkipFirstOpcodeFromType(SkipFirstOpcodeFromType(opcodes)));
        static_cast<Assembler*>(this)
            ->template EmitVex<vex_xop_byte1,
                               vex_xop_byte2,
                               vex_xop_byte3,
                               reg_is_opcode_extension>(arguments...);
        return SkipFirstOpcodeFromType(SkipFirstOpcodeFromType(SkipFirstOpcodeFromType(opcodes)));
      } else {
        static_assert(conditions_count <= 1, "Only one condition is allowed in instruction.");
        static_assert((registers_count + operands_count + labels_count) <= 2,
                      "Only two-arguments legacy instructions are supported.");
        static_cast<Assembler*>(this)->EmitRex(arguments...);
        if constexpr (FirstOpcode(opcodes) == 0x0F) {
          Emit8(0x0F);
          auto opcodes_no_prefixes_no_opcode_0x0F_extension = SkipFirstOpcodeFromType(opcodes);
          if constexpr (FirstOpcode(opcodes_no_prefixes_no_opcode_0x0F_extension) == 0x38) {
            Emit8(0x38);
            return SkipFirstOpcodeFromType(opcodes_no_prefixes_no_opcode_0x0F_extension);
          } else if constexpr (FirstOpcode(opcodes_no_prefixes_no_opcode_0x0F_extension) == 0x3A) {
            Emit8(0x3A);
            return SkipFirstOpcodeFromType(opcodes_no_prefixes_no_opcode_0x0F_extension);
          } else {
            return opcodes_no_prefixes_no_opcode_0x0F_extension;
          }
        } else {
          return opcodes;
        }
      }
    }(opcodes_no_prefixes);
    // These are older 8086 instructions which encode register number in the opcode itself.
    if constexpr (registers_count == 1 && operands_count == 0 && labels_count == 0 &&
                  OpcodesCount(opcodes_no_prefixes_no_opcode_extension) == 1) {
      static_cast<Assembler*>(this)->EmitRegisterInOpcode(
          FirstOpcode(opcodes_no_prefixes_no_opcode_extension),
          ArgumentByType<0, IsRegister>(arguments...));
      EmitImmediates(arguments...);
    } else {
      // Emit "main" single-byte opcode.
      if constexpr (conditions_count == 1) {
        auto condition_code = static_cast<uint8_t>(ArgumentByType<0, IsCondition>(arguments...));
        CHECK_EQ(0, condition_code & 0xF0);
        Emit8(FirstOpcode(opcodes_no_prefixes_no_opcode_extension) | condition_code);
      } else {
        Emit8(FirstOpcode(opcodes_no_prefixes_no_opcode_extension));
      }
      auto extra_opcodes = SkipFirstOpcodeFromType(opcodes_no_prefixes_no_opcode_extension);
      if constexpr (reg_is_opcode_extension) {
        if constexpr (operands_count == 1) {
          static_cast<Assembler*>(this)->EmitOperandOp(
              static_cast<int>(FirstOpcode(extra_opcodes)),
              ArgumentByType<0, IsMemoryOperand>(arguments...).operand);
        } else if constexpr (labels_count == 1) {
          static_cast<Assembler*>(this)->template EmitRipOp<ImmediatesSize<ArgumentsTypes...>()>(
              static_cast<int>(FirstOpcode(extra_opcodes)),
              ArgumentByType<0, IsLabelOperand>(arguments...).label);
        } else {
          static_cast<Assembler*>(this)->EmitModRM(this->FirstOpcode(extra_opcodes),
                                                   ArgumentByType<0, IsRegister>(arguments...));
        }
      } else if constexpr (registers_count > 0) {
        if constexpr (operands_count == 1) {
          static_cast<Assembler*>(this)->EmitOperandOp(
              ArgumentByType<0, IsRegister>(arguments...),
              ArgumentByType<0, IsMemoryOperand>(arguments...).operand);
        } else if constexpr (labels_count == 1) {
          static_cast<Assembler*>(this)->template EmitRipOp<ImmediatesSize<ArgumentsTypes...>()>(
              ArgumentByType<0, IsRegister>(arguments...),
              ArgumentByType<0, IsLabelOperand>(arguments...).label);
        } else {
          static_cast<Assembler*>(this)->EmitModRM(ArgumentByType<0, IsRegister>(arguments...),
                                                   ArgumentByType<1, IsRegister>(arguments...));
        }
      }
      // If reg is an opcode extension then we already used that element.
      if constexpr (reg_is_opcode_extension) {
        static_assert(OpcodesCount(extra_opcodes) == 1);
      } else if constexpr (OpcodesCount(extra_opcodes) > 0) {
        // Final opcode byte(s) - they are in the place where immediate is expected.
        // Cmpsps/Cmppd and 3DNow! instructions are using it.
        static_assert(OpcodesCount(extra_opcodes) == 1);
        Emit8(FirstOpcode(extra_opcodes));
      }
      if constexpr (registers_count + operands_count + labels_count == 4) {
        if constexpr (kCountArguments<IsImmediate, ArgumentsTypes...> == 1) {
          Emit8((ArgumentByType<registers_count - 1, IsRegister>(arguments...).num << 4) |
                ArgumentByType<0, IsImmediate>(arguments...));
        } else {
          static_assert(kCountArguments<IsImmediate, ArgumentsTypes...> == 0);
          Emit8(ArgumentByType<registers_count - 1, IsRegister>(arguments...).num << 4);
        }
      } else {
        EmitImmediates(arguments...);
      }
    }
  }

  void ResolveJumps();

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(AssemblerX86);
};

// Return the reverse condition.
template <typename Condition>
inline constexpr Condition ToReverseCond(Condition cond) {
  CHECK(cond != Condition::kInvalidCondition);
  // Condition has a nice property that given a condition, you can get
  // its reverse condition by flipping the least significant bit.
  return Condition(static_cast<int>(cond) ^ 1);
}

template <typename Condition>
inline constexpr const char* GetCondName(Condition cond) {
  switch (cond) {
    case Condition::kOverflow:
      return "O";
    case Condition::kNoOverflow:
      return "NO";
    case Condition::kBelow:
      return "B";
    case Condition::kAboveEqual:
      return "AE";
    case Condition::kEqual:
      return "Z";
    case Condition::kNotEqual:
      return "NZ";
    case Condition::kBelowEqual:
      return "BE";
    case Condition::kAbove:
      return "A";
    case Condition::kNegative:
      return "N";
    case Condition::kPositive:
      return "PL";
    case Condition::kParityEven:
      return "PE";
    case Condition::kParityOdd:
      return "PO";
    case Condition::kLess:
      return "LS";
    case Condition::kGreaterEqual:
      return "GE";
    case Condition::kLessEqual:
      return "LE";
    case Condition::kGreater:
      return "GT";
    default:
      return "??";
  }
}

template <typename Assembler>
inline void AssemblerX86<Assembler>::Pmov(XMMRegister dest, XMMRegister src) {
  // SSE does not have operations for register-to-register integer move and
  // Intel explicitly recommends to use pshufd instead on Pentium4:
  //   See https://software.intel.com/en-us/articles/
  //               fast-simd-integer-move-for-the-intel-pentiumr-4-processor
  // These recommendations are CPU-dependent, though, thus we will need to
  // investigate this question further before we could decide when to use
  // movaps (or movapd) and when to use pshufd.
  //
  // TODO(khim): investigate performance problems related to integer MOVs
  Movaps(dest, src);
}

template <typename Assembler>
inline void AssemblerX86<Assembler>::Call(const Label& label) {
  if (label.IsBound()) {
    int32_t offset = label.position() - pc();
    Call(offset);
  } else {
    Emit8(0xe8);
    Emit32(0xfffffffc);
    jumps_.push_back(Jump{&label, pc() - 4, false});
  }
}

template <typename Assembler>
inline void AssemblerX86<Assembler>::Jcc(Condition cc, const Label& label) {
  if (cc == Condition::kAlways) {
    Jmp(label);
    return;
  } else if (cc == Condition::kNever) {
    return;
  }
  CHECK_EQ(0, static_cast<uint8_t>(cc) & 0xF0);
  // TODO(eaeltsin): may be remove IsBound case?
  // Then jcc by label will be of fixed size (5 bytes)
  if (label.IsBound()) {
    int32_t offset = label.position() - pc();
    Jcc(cc, offset);
  } else {
    Emit16(0x800f | (static_cast<uint8_t>(cc) << 8));
    Emit32(0xfffffffc);
    jumps_.push_back(Jump{&label, pc() - 4, false});
  }
}

template <typename Assembler>
inline void AssemblerX86<Assembler>::Jmp(const Label& label) {
  // TODO(eaeltsin): may be remove IsBound case?
  // Then jmp by label will be of fixed size (5 bytes)
  if (label.IsBound()) {
    int32_t offset = label.position() - pc();
    Jmp(offset);
  } else {
    Emit8(0xe9);
    Emit32(0xfffffffc);
    jumps_.push_back(Jump{&label, pc() - 4, false});
  }
}

template <typename Assembler>
inline void AssemblerX86<Assembler>::ResolveJumps() {
  for (const auto& jump : jumps_) {
    const Label* label = jump.label;
    uint32_t pc = jump.pc;
    CHECK(label->IsBound());
    if (jump.is_recovery) {
      // Add pc -> label correspondence to recovery map.
      AddRelocation(0, RelocationType::RelocRecoveryPoint, pc, label->position());
    } else {
      int32_t offset = label->position() - pc;
      *AddrAs<int32_t>(pc) += offset;
    }
  }
}

// Code size optimized instructions: they have different variants depending on registers used.

template <typename Assembler>
inline void AssemblerX86<Assembler>::Xchgl(Register dest, Register src) {
  if (Assembler::IsAccumulator(src) || Assembler::IsAccumulator(dest)) {
    Register other = Assembler::IsAccumulator(src) ? dest : src;
    EmitInstruction<Opcodes<0x90>>(Register32Bit(other));
  } else {
    // Clang 8 (after r330298) swaps these two arguments.  We are comparing output
    // to clang in exhaustive test thus we want to match clang behavior exactly.
#if __clang_major__ >= 8
    EmitInstruction<Opcodes<0x87>>(Register32Bit(dest), Register32Bit(src));
#else
    EmitInstruction<Opcodes<0x87>>(Register32Bit(src), Register32Bit(dest));
#endif
  }
}

}  // namespace berberis

#endif  // BERBERIS_ASSEMBLER_COMMON_X86_H_
