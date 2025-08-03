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

#include "disassembler_riscv64.h"

#include "android-base/logging.h"
#include "android-base/stringprintf.h"

#include "base/bit_utils.h"
#include "base/casts.h"

using android::base::StringPrintf;

namespace art {
namespace riscv64 {

class DisassemblerRiscv64::Printer {
 public:
  Printer(DisassemblerRiscv64* disassembler, std::ostream& os)
      : disassembler_(disassembler), os_(os) {}

  void Dump32(const uint8_t* insn);
  void Dump16(const uint8_t* insn);
  void Dump2Byte(const uint8_t* data);
  void DumpByte(const uint8_t* data);

 private:
  // This enumeration should mirror the declarations in runtime/arch/riscv64/registers_riscv64.h.
  // We do not include that file to avoid a dependency on libart.
  enum {
    Zero = 0,
    RA = 1,
    FP  = 8,
    TR  = 9,
  };

  enum class MemAddressMode : uint32_t {
    kUnitStride = 0b00,
    kIndexedUnordered = 0b01,
    kStrided = 0b10,
    kIndexedOrdered = 0b11,
  };

  enum class Nf : uint32_t {
    k1 = 0b000,
    k2 = 0b001,
    k3 = 0b010,
    k4 = 0b011,
    k5 = 0b100,
    k6 = 0b101,
    k7 = 0b110,
    k8 = 0b111,
  };

  enum class VAIEncodings : uint32_t {
    kOpIVV = 0b000,
    kOpFVV = 0b001,
    kOpMVV = 0b010,
    kOpIVI = 0b011,
    kOpIVX = 0b100,
    kOpFVF = 0b101,
    kOpMVX = 0b110,
    kOpCFG = 0b111,
  };

  class ScopedNewLinePrinter {
    std::ostream& os_;

   public:
    explicit ScopedNewLinePrinter(std::ostream& os) : os_(os) {}
    ~ScopedNewLinePrinter() { os_ << '\n'; }
  };

  static const char* XRegName(uint32_t regno);
  static const char* FRegName(uint32_t regno);
  static const char* VRegName(uint32_t regno);
  static const char* RoundingModeName(uint32_t rm);

  // Regular instruction immediate utils

  static int32_t Decode32Imm12(uint32_t insn32) {
    uint32_t sign = (insn32 >> 31);
    uint32_t imm12 = (insn32 >> 20);
    return static_cast<int32_t>(imm12) - static_cast<int32_t>(sign << 12);  // Sign-extend.
  }

  static uint32_t Decode32UImm7(uint32_t insn32) { return (insn32 >> 25) & 0x7Fu; }

  static uint32_t Decode32UImm12(uint32_t insn32) { return (insn32 >> 20) & 0xFFFu; }

  static int32_t Decode32StoreOffset(uint32_t insn32) {
    uint32_t bit11 = insn32 >> 31;
    uint32_t bits5_11 = insn32 >> 25;
    uint32_t bits0_4 = (insn32 >> 7) & 0x1fu;
    uint32_t imm = (bits5_11 << 5) + bits0_4;
    return static_cast<int32_t>(imm) - static_cast<int32_t>(bit11 << 12);  // Sign-extend.
  }

  // Compressed instruction immediate utils

  // Extracts the offset from a compressed instruction
  // where `offset[5:3]` is in bits `[12:10]` and `offset[2|6]` is in bits `[6:5]`
  static uint32_t Decode16CMOffsetW(uint32_t insn16) {
    DCHECK(IsUint<16>(insn16));
    return BitFieldExtract(insn16, 5, 1) << 6 | BitFieldExtract(insn16, 10, 3) << 3 |
           BitFieldExtract(insn16, 6, 1) << 2;
  }

  // Extracts the offset from a compressed instruction
  // where `offset[5:3]` is in bits `[12:10]` and `offset[7:6]` is in bits `[6:5]`
  static uint32_t Decode16CMOffsetD(uint32_t insn16) {
    DCHECK(IsUint<16>(insn16));
    return BitFieldExtract(insn16, 5, 2) << 6 | BitFieldExtract(insn16, 10, 3) << 3;
  }

  // Re-orders raw immediatate into real value
  // where `imm[5:3]` is in bits `[5:3]` and `imm[8:6]` is in bits `[2:0]`
  static uint32_t Uimm6ToOffsetD16(uint32_t uimm6) {
    DCHECK(IsUint<6>(uimm6));
    return (BitFieldExtract(uimm6, 3, 3) << 3) | (BitFieldExtract(uimm6, 0, 3) << 6);
  }

  // Re-orders raw immediatate to form real value
  // where `imm[5:2]` is in bits `[5:2]` and `imm[7:6]` is in bits `[1:0]`
  static uint32_t Uimm6ToOffsetW16(uint32_t uimm6) {
    DCHECK(IsUint<6>(uimm6));
    return (BitFieldExtract(uimm6, 2, 4) << 2) | (BitFieldExtract(uimm6, 0, 2) << 6);
  }

  // Re-orders raw immediatate to form real value
  // where `imm[1]` is in bit `[0]` and `imm[0]` is in bit `[1]`
  static uint32_t Uimm2ToOffset10(uint32_t uimm2) {
    DCHECK(IsUint<2>(uimm2));
    return (uimm2 >> 1) | (uimm2 & 0x1u) << 1;
  }

  // Re-orders raw immediatate to form real value
  // where `imm[1]` is in bit `[0]` and `imm[0]` is `0`
  static uint32_t Uimm2ToOffset1(uint32_t uimm2) {
    DCHECK(IsUint<2>(uimm2));
    return (uimm2 & 0x1u) << 1;
  }

  template <size_t kWidth>
  static constexpr int32_t SignExtendBits(uint32_t bits) {
    static_assert(kWidth < BitSizeOf<uint32_t>());
    const uint32_t sign_bit = (bits >> kWidth) & 1u;
    return static_cast<int32_t>(bits) - static_cast<int32_t>(sign_bit << kWidth);
  }

  // Extracts the immediate from a compressed instruction
  // where `imm[5]` is in bit `[12]` and `imm[4:0]` is in bits `[6:2]`
  // and performs sign-extension if required
  template <typename T>
  static T Decode16Imm6(uint32_t insn16) {
    DCHECK(IsUint<16>(insn16));
    static_assert(std::is_integral_v<T>, "T must be integral");
    const T bits =
        BitFieldInsert(BitFieldExtract(insn16, 2, 5), BitFieldExtract(insn16, 12, 1), 5, 1);
    const T checked_bits = dchecked_integral_cast<T>(bits);
    if (std::is_unsigned_v<T>) {
      return checked_bits;
    }
    return SignExtendBits<6>(checked_bits);
  }

  // Regular instruction register utils

  static uint32_t GetRd(uint32_t insn32) { return (insn32 >> 7) & 0x1fu; }
  static uint32_t GetRs1(uint32_t insn32) { return (insn32 >> 15) & 0x1fu; }
  static uint32_t GetRs2(uint32_t insn32) { return (insn32 >> 20) & 0x1fu; }
  static uint32_t GetRs3(uint32_t insn32) { return insn32 >> 27; }
  static uint32_t GetRoundingMode(uint32_t insn32) { return (insn32 >> 12) & 7u; }

  // Compressed instruction register utils

  static uint32_t GetRs1Short16(uint32_t insn16) { return BitFieldExtract(insn16, 7, 3) + 8u; }
  static uint32_t GetRs2Short16(uint32_t insn16) { return BitFieldExtract(insn16, 2, 3) + 8u; }
  static uint32_t GetRs1_16(uint32_t insn16) { return BitFieldExtract(insn16, 7, 5); }
  static uint32_t GetRs2_16(uint32_t insn16) { return BitFieldExtract(insn16, 2, 5); }

  void PrintBranchOffset(int32_t offset);
  void PrintLoadStoreAddress(uint32_t rs1, int32_t offset);

  void Print32Lui(uint32_t insn32);
  void Print32Auipc(const uint8_t* insn, uint32_t insn32);
  void Print32Jal(const uint8_t* insn, uint32_t insn32);
  void Print32Jalr(const uint8_t* insn, uint32_t insn32);
  void Print32BCond(const uint8_t* insn, uint32_t insn32);
  void Print32Load(uint32_t insn32);
  void Print32Store(uint32_t insn32);
  void Print32FLoad(uint32_t insn32);
  void Print32FStore(uint32_t insn32);
  void Print32BinOpImm(uint32_t insn32);
  void Print32BinOp(uint32_t insn32);
  void Print32Atomic(uint32_t insn32);
  void Print32FpOp(uint32_t insn32);
  void Print32RVVOp(uint32_t insn32);
  void Print32FpFma(uint32_t insn32);
  void Print32Zicsr(uint32_t insn32);
  void Print32Fence(uint32_t insn32);

  void AppendVType(uint32_t zimm);
  static const char* DecodeRVVMemMnemonic(const uint32_t insn32,
                                          bool is_load,
                                          /*out*/ const char** rs2);

  DisassemblerRiscv64* const disassembler_;
  std::ostream& os_;
};

const char* DisassemblerRiscv64::Printer::XRegName(uint32_t regno) {
  static const char* const kXRegisterNames[] = {
      "zero",
      "ra",
      "sp",
      "gp",
      "tp",
      "t0",
      "t1",
      "t2",
      "fp",  // s0/fp
      "tr",  // s1/tr - ART thread register
      "a0",
      "a1",
      "a2",
      "a3",
      "a4",
      "a5",
      "a6",
      "a7",
      "s2",
      "s3",
      "s4",
      "s5",
      "s6",
      "s7",
      "s8",
      "s9",
      "s10",
      "s11",
      "t3",
      "t4",
      "t5",
      "t6",
  };
  static_assert(std::size(kXRegisterNames) == 32);
  DCHECK_LT(regno, 32u);
  return kXRegisterNames[regno];
}

const char* DisassemblerRiscv64::Printer::FRegName(uint32_t regno) {
  static const char* const kFRegisterNames[] = {
      "ft0",
      "ft1",
      "ft2",
      "ft3",
      "ft4",
      "ft5",
      "ft6",
      "ft7",
      "fs0",
      "fs1",
      "fa0",
      "fa1",
      "fa2",
      "fa3",
      "fa4",
      "fa5",
      "fa6",
      "fa7",
      "fs2",
      "fs3",
      "fs4",
      "fs5",
      "fs6",
      "fs7",
      "fs8",
      "fs9",
      "fs10",
      "fs11",
      "ft8",
      "ft9",
      "ft10",
      "ft11",
  };
  static_assert(std::size(kFRegisterNames) == 32);
  DCHECK_LT(regno, 32u);
  return kFRegisterNames[regno];
}

const char* DisassemblerRiscv64::Printer::VRegName(uint32_t regno) {
  static const char* const kVRegisterNames[] = {
      "V0",
      "V1",
      "V2",
      "V3",
      "V4",
      "V5",
      "V6",
      "V7",
      "V8",
      "V9",
      "V10",
      "V11",
      "V12",
      "V13",
      "V14",
      "V15",
      "V16",
      "V17",
      "V18",
      "V19",
      "V20",
      "V21",
      "V22",
      "V23",
      "V24",
      "V25",
      "V26",
      "V27",
      "V28",
      "V29",
      "V30",
      "V31",
  };
  static_assert(std::size(kVRegisterNames) == 32);
  DCHECK_LT(regno, 32u);
  return kVRegisterNames[regno];
}

const char* DisassemblerRiscv64::Printer::RoundingModeName(uint32_t rm) {
  // Note: We do not print the rounding mode for DYN.
  static const char* const kRoundingModeNames[] = {
      ".rne", ".rtz", ".rdn", ".rup", ".rmm", ".<reserved-rm>", ".<reserved-rm>", /*DYN*/ ""
  };
  static_assert(std::size(kRoundingModeNames) == 8);
  DCHECK_LT(rm, 8u);
  return kRoundingModeNames[rm];
}

void DisassemblerRiscv64::Printer::PrintBranchOffset(int32_t offset) {
  os_ << (offset >= 0 ? "+" : "") << offset;
}

void DisassemblerRiscv64::Printer::PrintLoadStoreAddress(uint32_t rs1, int32_t offset) {
  if (offset != 0) {
    os_ << StringPrintf("%d", offset);
  }
  os_ << "(" << XRegName(rs1) << ")";

  if (rs1 == TR && offset >= 0) {
    // Add entrypoint name.
    os_ << " ; ";
    disassembler_->GetDisassemblerOptions()->thread_offset_name_function_(
        os_, dchecked_integral_cast<uint32_t>(offset));
  }
}

void DisassemblerRiscv64::Printer::Print32Lui(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x37u);
  // TODO(riscv64): Should we also print the actual sign-extend value?
  os_ << StringPrintf("lui %s, %u", XRegName(GetRd(insn32)), insn32 >> 12);
}

void DisassemblerRiscv64::Printer::Print32Auipc([[maybe_unused]] const uint8_t* insn,
                                                uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x17u);
  // TODO(riscv64): Should we also print the calculated address?
  os_ << StringPrintf("auipc %s, %u", XRegName(GetRd(insn32)), insn32 >> 12);
}

void DisassemblerRiscv64::Printer::Print32Jal(const uint8_t* insn, uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x6fu);
  // Print an alias if available.
  uint32_t rd = GetRd(insn32);
  os_ << (rd == Zero ? "j " : "jal ");
  if (rd != Zero && rd != RA) {
    os_ << XRegName(rd) << ", ";
  }
  uint32_t bit20 = (insn32 >> 31);
  uint32_t bits1_10 = (insn32 >> 21) & 0x3ffu;
  uint32_t bit11 = (insn32 >> 20) & 1u;
  uint32_t bits12_19 = (insn32 >> 12) & 0xffu;
  uint32_t imm = (bits1_10 << 1) + (bit11 << 11) + (bits12_19 << 12) + (bit20 << 20);
  int32_t offset = static_cast<int32_t>(imm) - static_cast<int32_t>(bit20 << 21);  // Sign-extend.
  PrintBranchOffset(offset);
  os_ << " ; " << disassembler_->FormatInstructionPointer(insn + offset);

  // TODO(riscv64): When we implement shared thunks to reduce AOT slow-path code size,
  // check if this JAL lands at an entrypoint load from TR and, if so, print its name.
}

void DisassemblerRiscv64::Printer::Print32Jalr([[maybe_unused]] const uint8_t* insn,
                                               uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x67u);
  DCHECK_EQ((insn32 >> 12) & 7u, 0u);
  uint32_t rd = GetRd(insn32);
  uint32_t rs1 = GetRs1(insn32);
  int32_t imm12 = Decode32Imm12(insn32);
  // Print shorter macro instruction notation if available.
  if (rd == Zero && rs1 == RA && imm12 == 0) {
    os_ << "ret";
  } else if (rd == Zero && imm12 == 0) {
    os_ << "jr " << XRegName(rs1);
  } else if (rd == RA && imm12 == 0) {
    os_ << "jalr " << XRegName(rs1);
  } else {
    // TODO(riscv64): Should we also print the calculated address if the preceding
    // instruction is AUIPC? (We would need to record the previous instruction.)
    os_ << "jalr " << XRegName(rd) << ", ";
    // Use the same format as llvm-objdump: "rs1" if `imm12` is zero, otherwise "imm12(rs1)".
    if (imm12 == 0) {
      os_ << XRegName(rs1);
    } else {
      os_ << imm12 << "(" << XRegName(rs1) << ")";
    }
  }
}

void DisassemblerRiscv64::Printer::Print32BCond(const uint8_t* insn, uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x63u);
  static const char* const kOpcodes[] = {
      "beq", "bne", nullptr, nullptr, "blt", "bge", "bltu", "bgeu"
  };
  uint32_t funct3 = (insn32 >> 12) & 7u;
  const char* opcode = kOpcodes[funct3];
  if (opcode == nullptr) {
    os_ << "<unknown32>";
    return;
  }

  // Print shorter macro instruction notation if available.
  uint32_t rs1 = GetRs1(insn32);
  uint32_t rs2 = GetRs2(insn32);
  if (rs2 == Zero) {
    os_ << opcode << "z " << XRegName(rs1);
  } else if (rs1 == Zero && (funct3 == 4u || funct3 == 5u)) {
    // blt zero, rs2, offset ... bgtz rs2, offset
    // bge zero, rs2, offset ... blez rs2, offset
    os_ << (funct3 == 4u ? "bgtz " : "blez ") << XRegName(rs2);
  } else {
    os_ << opcode << " " << XRegName(rs1) << ", " << XRegName(rs2);
  }
  os_ << ", ";

  uint32_t bit12 = insn32 >> 31;
  uint32_t bits5_10 = (insn32 >> 25) & 0x3fu;
  uint32_t bits1_4 = (insn32 >> 8) & 0xfu;
  uint32_t bit11 = (insn32 >> 7) & 1u;
  uint32_t imm = (bit12 << 12) + (bit11 << 11) + (bits5_10 << 5) + (bits1_4 << 1);
  int32_t offset = static_cast<int32_t>(imm) - static_cast<int32_t>(bit12 << 13);  // Sign-extend.
  PrintBranchOffset(offset);
  os_ << " ; " << disassembler_->FormatInstructionPointer(insn + offset);
}

void DisassemblerRiscv64::Printer::Print32Load(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x03u);
  static const char* const kOpcodes[] = {
      "lb", "lh", "lw", "ld", "lbu", "lhu", "lwu", nullptr
  };
  uint32_t funct3 = (insn32 >> 12) & 7u;
  const char* opcode = kOpcodes[funct3];
  if (opcode == nullptr) {
    os_ << "<unknown32>";
    return;
  }

  os_ << opcode << " " << XRegName(GetRd(insn32)) << ", ";
  PrintLoadStoreAddress(GetRs1(insn32), Decode32Imm12(insn32));

  // TODO(riscv64): If previous instruction is AUIPC for current `rs1` and we load
  // from the range specified by assembler options, print the loaded literal.
}

void DisassemblerRiscv64::Printer::Print32Store(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x23u);
  static const char* const kOpcodes[] = {
      "sb", "sh", "sw", "sd", nullptr, nullptr, nullptr, nullptr
  };
  uint32_t funct3 = (insn32 >> 12) & 7u;
  const char* opcode = kOpcodes[funct3];
  if (opcode == nullptr) {
    os_ << "<unknown32>";
    return;
  }

  os_ << opcode << " " << XRegName(GetRs2(insn32)) << ", ";
  PrintLoadStoreAddress(GetRs1(insn32), Decode32StoreOffset(insn32));
}

const char* DisassemblerRiscv64::Printer::DecodeRVVMemMnemonic(const uint32_t insn32,
                                                               bool is_load,
                                                               /*out*/ const char** rs2) {
  const uint32_t width_index = (insn32 >> 12) & 3u;
  DCHECK_EQ(width_index != 0u, (insn32 & 0x4000u) != 0u);
  const uint32_t imm7 = Decode32UImm7(insn32);
  const enum Nf nf = static_cast<enum Nf>((imm7 >> 4) & 0x7u);
  const enum MemAddressMode mop = static_cast<enum MemAddressMode>((imm7 >> 1) & 0x3u);
  const uint32_t mew = (insn32 >> 28) & 1u;

  if (mew == 1u) {
    // 7.3. Vector Load/Store Width Encoding
    // The mew bit (inst[28]) when set is expected to be used to encode
    // expanded memory sizes of 128 bits and above,
    // but these encodings are currently reserved.
    return nullptr;
  }

  switch (mop) {
    case MemAddressMode::kUnitStride: {
      const uint32_t umop = GetRs2(insn32);
      switch (umop) {
        case 0b00000:  // Vector Unit-Stride Load/Store
          static constexpr const char* kVUSMnemonics[8][4] = {
              {"e8.v", "e16.v", "e32.v", "e64.v"},
              {"seg2e8.v", "seg2e16.v", "seg2e32.v", "seg2e64.v"},
              {"seg3e8.v", "seg3e16.v", "seg3e32.v", "seg3e64.v"},
              {"seg4e8.v", "seg4e16.v", "seg4e32.v", "seg4e64.v"},
              {"seg5e8.v", "seg5e16.v", "seg5e32.v", "seg5e64.v"},
              {"seg6e8.v", "seg6e16.v", "seg6e32.v", "seg6e64.v"},
              {"seg7e8.v", "seg7e16.v", "seg7e32.v", "seg7e64.v"},
              {"seg8e8.v", "seg8e16.v", "seg8e32.v", "seg8e64.v"},
          };
          return kVUSMnemonics[enum_cast<uint32_t>(nf)][width_index];
        case 0b01000: {  // Vector Whole Register Load/Store
          if (is_load) {
            static constexpr const char* kVWRLMnemonics[8][4] = {
                {"1re8.v", "1re16.v", "1re32.v", "1re64.v"},
                {"2re8.v", "2re16.v", "2re32.v", "2re64.v"},
                {nullptr, nullptr, nullptr, nullptr},
                {"4re8.v", "4re16.v", "4re32.v", "4re64.v"},
                {nullptr, nullptr, nullptr, nullptr},
                {nullptr, nullptr, nullptr, nullptr},
                {nullptr, nullptr, nullptr, nullptr},
                {"8re8.v", "8re16.v", "8re32.v", "8re64.v"},
            };
            return kVWRLMnemonics[enum_cast<uint32_t>(nf)][width_index];
          } else {
            if (width_index != 0) {
              return nullptr;
            }
            static constexpr const char* kVWRSMnemonics[8] = {
                "1r", "2r", nullptr, "4r", nullptr, nullptr, nullptr, "8r"
            };
            return kVWRSMnemonics[enum_cast<uint32_t>(nf)];
          }
        }
        case 0b01011:  // Vector Unit-Stride Mask Load/Store
          if (nf == Nf::k1 && width_index == 0 && (imm7 & 1u) == 1u) {
            return "m.v";
          } else {
            return nullptr;
          }
        case 0b10000:  // Vector Unit-Stride Fault-Only-First Load
          static constexpr const char* kVUSFFLMnemonics[8][4] = {
              {"e8ff.v", "e16ff.v", "e32ff.v", "e64ff.v"},
              {"seg2e8ff.v", "seg2e16ff.v", "seg2e32ff.v", "seg2e64ff.v"},
              {"seg3e8ff.v", "seg3e16ff.v", "seg3e32ff.v", "seg3e64ff.v"},
              {"seg4e8ff.v", "seg4e16ff.v", "seg4e32ff.v", "seg4e64ff.v"},
              {"seg5e8ff.v", "seg5e16ff.v", "seg5e32ff.v", "seg5e64ff.v"},
              {"seg6e8ff.v", "seg6e16ff.v", "seg6e32ff.v", "seg6e64ff.v"},
              {"seg7e8ff.v", "seg7e16ff.v", "seg7e32ff.v", "seg7e64ff.v"},
              {"seg8e8ff.v", "seg8e16ff.v", "seg8e32ff.v", "seg8e64ff.v"},
          };
          return is_load ? kVUSFFLMnemonics[enum_cast<uint32_t>(nf)][width_index] : nullptr;
        default:  // Unknown
          return nullptr;
      }
    }
    case MemAddressMode::kIndexedUnordered: {
      static constexpr const char* kVIUMnemonics[8][4] = {
          {"uxei8.v", "uxei16.v", "uxei32.v", "uxei64.v"},
          {"uxseg2ei8.v", "uxseg2ei16.v", "uxseg2ei32.v", "uxseg2ei64.v"},
          {"uxseg3ei8.v", "uxseg3ei16.v", "uxseg3ei32.v", "uxseg3ei64.v"},
          {"uxseg4ei8.v", "uxseg4ei16.v", "uxseg4ei32.v", "uxseg4ei64.v"},
          {"uxseg5ei8.v", "uxseg5ei16.v", "uxseg5ei32.v", "uxseg5ei64.v"},
          {"uxseg6ei8.v", "uxseg6ei16.v", "uxseg6ei32.v", "uxseg6ei64.v"},
          {"uxseg7ei8.v", "uxseg7ei16.v", "uxseg7ei32.v", "uxseg7ei64.v"},
          {"uxseg8ei8.v", "uxseg8ei16.v", "uxseg8ei32.v", "uxseg8ei64.v"},
      };
      *rs2 = VRegName(GetRs2(insn32));
      return kVIUMnemonics[enum_cast<uint32_t>(nf)][width_index];
    }
    case MemAddressMode::kStrided: {
      static constexpr const char* kVSMnemonics[8][4] = {
          {"se8.v", "se16.v", "se32.v", "se64.v"},
          {"sseg2e8.v", "sseg2e16.v", "sseg2e32.v", "sseg2e64.v"},
          {"sseg3e8.v", "sseg3e16.v", "sseg3e32.v", "sseg3e64.v"},
          {"sseg4e8.v", "sseg4e16.v", "sseg4e32.v", "sseg4e64.v"},
          {"sseg5e8.v", "sseg5e16.v", "sseg5e32.v", "sseg5e64.v"},
          {"sseg6e8.v", "sseg6e16.v", "sseg6e32.v", "sseg6e64.v"},
          {"sseg7e8.v", "sseg7e16.v", "sseg7e32.v", "sseg7e64.v"},
          {"sseg8e8.v", "sseg8e16.v", "sseg8e32.v", "sseg8e64.v"},
      };
      *rs2 = XRegName(GetRs2(insn32));
      return kVSMnemonics[enum_cast<uint32_t>(nf)][width_index];
    }
    case MemAddressMode::kIndexedOrdered: {
      static constexpr const char* kVIOMnemonics[8][4] = {
          {"oxei8.v", "oxei16.v", "oxei32.v", "oxei64.v"},
          {"oxseg2ei8.v", "oxseg2ei16.v", "oxseg2ei32.v", "oxseg2ei64.v"},
          {"oxseg3ei8.v", "oxseg3ei16.v", "oxseg3ei32.v", "oxseg3ei64.v"},
          {"oxseg4ei8.v", "oxseg4ei16.v", "oxseg4ei32.v", "oxseg4ei64.v"},
          {"oxseg5ei8.v", "oxseg5ei16.v", "oxseg5ei32.v", "oxseg5ei64.v"},
          {"oxseg6ei8.v", "oxseg6ei16.v", "oxseg6ei32.v", "oxseg6ei64.v"},
          {"oxseg7ei8.v", "oxseg7ei16.v", "oxseg7ei32.v", "oxseg7ei64.v"},
          {"oxseg8ei8.v", "oxseg8ei16.v", "oxseg8ei32.v", "oxseg8ei64.v"},
      };
      *rs2 = VRegName(GetRs2(insn32));
      return kVIOMnemonics[enum_cast<uint32_t>(nf)][width_index];
    }
  }
}

static constexpr const char* kFpMemMnemonics[] = {
    nullptr, "h", "w", "d", "q", nullptr, nullptr, nullptr
};

void DisassemblerRiscv64::Printer::Print32FLoad(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x07u);
  int32_t offset = 0;
  const char* rd = nullptr;
  const char* rs2 = nullptr;
  const char* vm = "";
  const uint32_t funct3 = (insn32 >> 12) & 7u;
  const char* mnemonic = kFpMemMnemonics[funct3];
  const char* prefix = "f";
  if (mnemonic == nullptr) {
    // Vector Loads
    prefix = "v";
    mnemonic = DecodeRVVMemMnemonic(insn32, /*is_load=*/true, &rs2);
    rd = VRegName(GetRd(insn32));

    if ((Decode32UImm7(insn32) & 0x1U) == 0) {
      vm = ", v0.t";
    }
  } else {
    rd = FRegName(GetRd(insn32));
    offset = Decode32Imm12(insn32);
  }

  if (mnemonic == nullptr) {
    os_ << "<unknown32>";
    return;
  }

  os_ << prefix << "l" << mnemonic << " " << rd << ", ";
  PrintLoadStoreAddress(GetRs1(insn32), offset);

  if (rs2) {
    os_ << ", " << rs2;
  }

  os_ << vm;

  // TODO(riscv64): If previous instruction is AUIPC for current `rs1` and we load
  // from the range specified by assembler options, print the loaded literal.
}

void DisassemblerRiscv64::Printer::Print32FStore(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x27u);
  uint32_t funct3 = (insn32 >> 12) & 3u;
  const char* prefix = "f";
  const char* mnemonic = kFpMemMnemonics[funct3];

  if (mnemonic == nullptr) {
    // Vector Stores
    const char* rs2 = nullptr;
    prefix = "v";
    mnemonic = DecodeRVVMemMnemonic(insn32, /*is_load=*/false, &rs2);

    if (mnemonic == nullptr) {
      os_ << "<unknown32>";
      return;
    }

    os_ << prefix << "s" << mnemonic << " " << VRegName(GetRd(insn32)) << ", ";
    PrintLoadStoreAddress(GetRs1(insn32), 0);

    if (rs2) {
      os_ << ", " << rs2;
    }

    if ((Decode32UImm7(insn32) & 0x1U) == 0) {
      os_ << ", v0.t";
    }
  } else {
    os_ << prefix << "s" << mnemonic << " " << FRegName(GetRs2(insn32)) << ", ";
    PrintLoadStoreAddress(GetRs1(insn32), Decode32StoreOffset(insn32));
  }
}

void DisassemblerRiscv64::Printer::Print32BinOpImm(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x77u, 0x13u);  // Note: Bit 0x8 selects narrow binop.
  bool narrow = (insn32 & 0x8u) != 0u;
  uint32_t funct3 = (insn32 >> 12) & 7u;
  uint32_t rd = GetRd(insn32);
  uint32_t rs1 = GetRs1(insn32);
  int32_t imm = Decode32Imm12(insn32);

  // Print shorter macro instruction notation if available.
  if (funct3 == /*ADDI*/ 0u && imm == 0u) {
    if (narrow) {
      os_ << "sextw " << XRegName(rd) << ", " << XRegName(rs1);
    } else if (rd == Zero && rs1 == Zero) {
      os_ << "nop";  // Only canonical nop. Non-Zero `rd == rs1` nops are printed as "mv".
    } else {
      os_ << "mv " << XRegName(rd) << ", " << XRegName(rs1);
    }
  } else if (!narrow && funct3 == /*XORI*/ 4u && imm == -1) {
    os_ << "not " << XRegName(rd) << ", " << XRegName(rs1);
  } else if (!narrow && funct3 == /*ANDI*/ 7u && imm == 0xff) {
    os_ << "zextb " << XRegName(rd) << ", " << XRegName(rs1);
  } else if (!narrow && funct3 == /*SLTIU*/ 3u && imm == 1) {
    os_ << "seqz " << XRegName(rd) << ", " << XRegName(rs1);
  } else if ((insn32 & 0xfc00707fu) == 0x0800101bu) {
    os_ << "slli.uw " << XRegName(rd) << ", " << XRegName(rs1) << ", " << (imm & 0x3fu);
  } else if ((imm ^ 0x600u) < 3u && funct3 == 1u) {
    static const char* const kBitOpcodes[] = { "clz", "ctz", "cpop" };
    os_ << kBitOpcodes[imm ^ 0x600u] << (narrow ? "w " : " ")
        << XRegName(rd) << ", " << XRegName(rs1);
  } else if ((imm ^ 0x600u) < (narrow ? 32 : 64) && funct3 == 5u) {
    os_ << "rori" << (narrow ? "w " : " ")
        << XRegName(rd) << ", " << XRegName(rs1) << ", " << (imm ^ 0x600u);
  } else if (imm == 0x287u && !narrow && funct3 == 5u) {
    os_ << "orc.b " << XRegName(rd) << ", " << XRegName(rs1);
  } else if (imm == 0x6b8u && !narrow && funct3 == 5u) {
    os_ << "rev8 " << XRegName(rd) << ", " << XRegName(rs1);
  } else {
    bool bad_high_bits = false;
    if (funct3 == /*SLLI*/ 1u || funct3 == /*SRLI/SRAI*/ 5u) {
      imm &= (narrow ? 0x1fu : 0x3fu);
      uint32_t high_bits = insn32 & (narrow ? 0xfe000000u : 0xfc000000u);
      if (high_bits == 0x40000000u && funct3 == /*SRAI*/ 5u) {
        os_ << "srai";
      } else {
        os_ << ((funct3 == /*SRLI*/ 5u) ? "srli" : "slli");
        bad_high_bits = (high_bits != 0u);
      }
    } else if (!narrow || funct3 == /*ADDI*/ 0u) {
      static const char* const kOpcodes[] = {
          "addi", nullptr, "slti", "sltiu", "xori", nullptr, "ori", "andi"
      };
      DCHECK(kOpcodes[funct3] != nullptr);
      os_ << kOpcodes[funct3];
    } else {
      os_ << "<unknown32>";  // There is no SLTIW/SLTIUW/XORIW/ORIW/ANDIW.
      return;
    }
    os_ << (narrow ? "w " : " ") << XRegName(rd) << ", " << XRegName(rs1) << ", " << imm;
    if (bad_high_bits) {
      os_ << " (invalid high bits)";
    }
  }
}

void DisassemblerRiscv64::Printer::Print32BinOp(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x77u, 0x33u);  // Note: Bit 0x8 selects narrow binop.
  bool narrow = (insn32 & 0x8u) != 0u;
  uint32_t funct3 = (insn32 >> 12) & 7u;
  uint32_t rd = GetRd(insn32);
  uint32_t rs1 = GetRs1(insn32);
  uint32_t rs2 = GetRs2(insn32);
  uint32_t high_bits = insn32 & 0xfe000000u;

  // Print shorter macro instruction notation if available.
  if (high_bits == 0x40000000u && funct3 == /*SUB*/ 0u && rs1 == Zero) {
    os_ << (narrow ? "negw " : "neg ") << XRegName(rd) << ", " << XRegName(rs2);
  } else if (!narrow && funct3 == /*SLT*/ 2u && rs2 == Zero) {
    os_ << "sltz " << XRegName(rd) << ", " << XRegName(rs1);
  } else if (!narrow && funct3 == /*SLT*/ 2u && rs1 == Zero) {
    os_ << "sgtz " << XRegName(rd) << ", " << XRegName(rs2);
  } else if (!narrow && funct3 == /*SLTU*/ 3u && rs1 == Zero) {
    os_ << "snez " << XRegName(rd) << ", " << XRegName(rs2);
  } else if (narrow && high_bits == 0x08000000u && funct3 == /*ADD.UW*/ 0u && rs2 == Zero) {
    os_ << "zext.w " << XRegName(rd) << ", " << XRegName(rs1);
  } else {
    bool bad_high_bits = false;
    if (high_bits == 0x40000000u && (funct3 == /*SUB*/ 0u || funct3 == /*SRA*/ 5u)) {
      os_ << ((funct3 == /*SUB*/ 0u) ? "sub" : "sra");
    } else if (high_bits == 0x02000000u &&
               (!narrow || (funct3 == /*MUL*/ 0u || funct3 >= /*DIV/DIVU/REM/REMU*/ 4u))) {
      static const char* const kOpcodes[] = {
          "mul", "mulh", "mulhsu", "mulhu", "div", "divu", "rem", "remu"
      };
      os_ << kOpcodes[funct3];
    } else if (high_bits == 0x08000000u && narrow && funct3 == /*ADD.UW*/ 0u) {
      os_ << "add.u";  // "w" is added below.
    } else if (high_bits == 0x20000000u && (funct3 & 1u) == 0u && funct3 != 0u) {
      static const char* const kZbaOpcodes[] = { nullptr, "sh1add", "sh2add", "sh3add" };
      DCHECK(kZbaOpcodes[funct3 >> 1] != nullptr);
      os_ << kZbaOpcodes[funct3 >> 1] << (narrow ? ".u" /* "w" is added below. */ : "");
    } else if (high_bits == 0x40000000u && !narrow && funct3 >= 4u && funct3 != 5u) {
      static const char* const kZbbNegOpcodes[] = { "xnor", nullptr, "orn", "andn" };
      DCHECK(kZbbNegOpcodes[funct3 - 4u] != nullptr);
      os_ << kZbbNegOpcodes[funct3 - 4u];
    } else if (high_bits == 0x0a000000u && !narrow && funct3 >= 4u) {
      static const char* const kZbbMinMaxOpcodes[] = { "min", "minu", "max", "maxu" };
      DCHECK(kZbbMinMaxOpcodes[funct3 - 4u] != nullptr);
      os_ << kZbbMinMaxOpcodes[funct3 - 4u];
    } else if (high_bits == 0x60000000u && (funct3 == /*ROL*/ 1u || funct3 == /*ROL*/ 5u)) {
      os_ << (funct3 == /*ROL*/ 1u ? "rol" : "ror");
    } else if (!narrow || (funct3 == /*ADD*/ 0u || funct3 == /*SLL*/ 1u || funct3 == /*SRL*/ 5u)) {
      static const char* const kOpcodes[] = {
          "add", "sll", "slt", "sltu", "xor", "srl", "or", "and"
      };
      os_ << kOpcodes[funct3];
      bad_high_bits = (high_bits != 0u);
    } else {
      DCHECK(narrow);
      os_ << "<unknown32>";  // Some of the above instructions do not have a narrow version.
      return;
    }
    os_ << (narrow ? "w " : " ") << XRegName(rd) << ", " << XRegName(rs1) << ", " << XRegName(rs2);
    if (bad_high_bits) {
      os_ << " (invalid high bits)";
    }
  }
}

void DisassemblerRiscv64::Printer::Print32Atomic(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x2fu);
  uint32_t funct3 = (insn32 >> 12) & 7u;
  uint32_t funct5 = (insn32 >> 27);
  if ((funct3 != 2u && funct3 != 3u) ||  // There are only 32-bit and 64-bit LR/SC/AMO*.
      (((funct5 & 3u) != 0u) && funct5 >= 4u)) {  // Only multiples of 4, or 1-3.
    os_ << "<unknown32>";
    return;
  }
  static const char* const kMul4Opcodes[] = {
      "amoadd", "amoxor", "amoor", "amoand", "amomin", "amomax", "amominu", "amomaxu"
  };
  static const char* const kOtherOpcodes[] = {
      nullptr, "amoswap", "lr", "sc"
  };
  const char* opcode = ((funct5 & 3u) == 0u) ? kMul4Opcodes[funct5 >> 2] : kOtherOpcodes[funct5];
  DCHECK(opcode != nullptr);
  uint32_t rd = GetRd(insn32);
  uint32_t rs1 = GetRs1(insn32);
  uint32_t rs2 = GetRs2(insn32);
  const char* type = (funct3 == 2u) ? ".w" : ".d";
  const char* aq = (((insn32 >> 26) & 1u) != 0u) ? ".aq" : "";
  const char* rl = (((insn32 >> 25) & 1u) != 0u) ? ".rl" : "";
  os_ << opcode << type << aq << rl << " " << XRegName(rd) << ", " << XRegName(rs1);
  if (funct5 == /*LR*/ 2u) {
    if (rs2 != 0u) {
      os_ << " (bad rs2)";
    }
  } else {
    os_ << ", " << XRegName(rs2);
  }
}

void DisassemblerRiscv64::Printer::Print32FpOp(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x53u);
  uint32_t rd = GetRd(insn32);
  uint32_t rs1 = GetRs1(insn32);
  uint32_t rs2 = GetRs2(insn32);  // Sometimes used to to differentiate opcodes.
  uint32_t rm = GetRoundingMode(insn32);  // Sometimes used to to differentiate opcodes.
  uint32_t funct7 = insn32 >> 25;
  const char* type = ((funct7 & 1u) != 0u) ? ".d" : ".s";
  if ((funct7 & 2u) != 0u) {
    os_ << "<unknown32>";  // Note: This includes the "H" and "Q" extensions.
    return;
  }
  switch (funct7 >> 2) {
    case 0u:
    case 1u:
    case 2u:
    case 3u: {
      static const char* const kOpcodes[] = { "fadd", "fsub", "fmul", "fdiv" };
      os_ << kOpcodes[funct7 >> 2] << type << RoundingModeName(rm) << " "
          << FRegName(rd) << ", " << FRegName(rs1) << ", " << FRegName(rs2);
      return;
    }
    case 4u: {  // FSGN*
      // Print shorter macro instruction notation if available.
      static const char* const kOpcodes[] = { "fsgnj", "fsgnjn", "fsgnjx" };
      if (rm < std::size(kOpcodes)) {
        if (rs1 == rs2) {
          static const char* const kAltOpcodes[] = { "fmv", "fneg", "fabs" };
          static_assert(std::size(kOpcodes) == std::size(kAltOpcodes));
          os_ << kAltOpcodes[rm] << type << " " << FRegName(rd) << ", " << FRegName(rs1);
        } else {
          os_ << kOpcodes[rm] << type << " "
              << FRegName(rd) << ", " << FRegName(rs1) << ", " << FRegName(rs2);
        }
        return;
      }
      break;
    }
    case 5u: {  // FMIN/FMAX
      static const char* const kOpcodes[] = { "fmin", "fmax" };
      if (rm < std::size(kOpcodes)) {
        os_ << kOpcodes[rm] << type << " "
            << FRegName(rd) << ", " << FRegName(rs1) << ", " << FRegName(rs2);
        return;
      }
      break;
    }
    case 0x8u:  // FCVT between FP numbers.
      if ((rs2 ^ 1u) == (funct7 & 1u)) {
        os_ << ((rs2 != 0u) ? "fcvt.s.d" : "fcvt.d.s") << RoundingModeName(rm) << " "
            << FRegName(rd) << ", " << FRegName(rs1);
      }
      break;
    case 0xbu:
      if (rs2 == 0u) {
        os_ << "fsqrt" << type << RoundingModeName(rm) << " "
            << FRegName(rd) << ", " << FRegName(rs1);
        return;
      }
      break;
    case 0x14u: {  // FLE/FLT/FEQ
      static const char* const kOpcodes[] = { "fle", "flt", "feq" };
      if (rm < std::size(kOpcodes)) {
        os_ << kOpcodes[rm] << type << " "
            << XRegName(rd) << ", " << FRegName(rs1) << ", " << FRegName(rs2);
        return;
      }
      break;
    }
    case 0x18u: {  // FCVT from floating point numbers to integers
      static const char* const kIntTypes[] = { "w", "wu", "l", "lu" };
      if (rs2 < std::size(kIntTypes)) {
        os_ << "fcvt." << kIntTypes[rs2] << type << RoundingModeName(rm) << " "
            << XRegName(rd) << ", " << FRegName(rs1);
        return;
      }
      break;
    }
    case 0x1au: {  // FCVT from integers to floating point numbers
      static const char* const kIntTypes[] = { "w", "wu", "l", "lu" };
      if (rs2 < std::size(kIntTypes)) {
        os_ << "fcvt" << type << "." << kIntTypes[rs2] << RoundingModeName(rm) << " "
            << FRegName(rd) << ", " << XRegName(rs1);
        return;
      }
      break;
    }
    case 0x1cu:  // FMV from FPR to GPR, or FCLASS
      if (rs2 == 0u && rm == 0u) {
        os_ << (((funct7 & 1u) != 0u) ? "fmv.x.d " : "fmv.x.w ")
            << XRegName(rd) << ", " << FRegName(rs1);
        return;
      } else if (rs2 == 0u && rm == 1u) {
        os_ << "fclass" << type << " " << XRegName(rd) << ", " << FRegName(rs1);
        return;
      }
      break;
    case 0x1eu:  // FMV from GPR to FPR
      if (rs2 == 0u && rm == 0u) {
        os_ << (((funct7 & 1u) != 0u) ? "fmv.d.x " : "fmv.w.x ")
            << FRegName(rd) << ", " << XRegName(rs1);
        return;
      }
      break;
    default:
      break;
  }
  os_ << "<unknown32>";
}

void DisassemblerRiscv64::Printer::AppendVType(uint32_t vtype) {
  const uint32_t lmul_v = vtype & 0x7U;
  const uint32_t vsew_v = (vtype >> 3) & 0x7U;
  const uint32_t vta_v = (vtype >> 6) & 0x1U;
  const uint32_t vma_v = (vtype >> 7) & 0x1U;

  if ((vsew_v & 0x4U) == 0u) {
    if (lmul_v != 0b100) {
      static const char* const vsews[] = {"e8", "e16", "e32", "e64"};
      static const char* const lmuls[] = {
          "m1", "m2", "m4", "m8", nullptr, "mf8", "mf4", "mf2"
      };

      const char* vma = vma_v ? "ma" : "mu";
      const char* vta = vta_v ? "ta" : "tu";
      const char* vsew = vsews[vsew_v & 0x3u];
      const char* lmul = lmuls[lmul_v];

      os_ << vsew << ", " << lmul << ", " << vta << ", " << vma;
      return;
    }
  }

  os_ << StringPrintf("0x%08x", vtype) << "\t# incorrect VType literal";
}

static constexpr uint32_t VWXUNARY0 = 0b010000;
static constexpr uint32_t VRXUNARY0 = 0b010000;
static constexpr uint32_t VXUNARY0 = 0b010010;
static constexpr uint32_t VMUNARY0 = 0b010100;

static constexpr uint32_t VWFUNARY0 = 0b010000;
static constexpr uint32_t VRFUNARY0 = 0b010000;
static constexpr uint32_t VFUNARY0 = 0b010010;
static constexpr uint32_t VFUNARY1 = 0b010011;

static void MaybeSwapOperands(uint32_t funct6,
                              /*inout*/ const char*& rs1,
                              /*inout*/ const char*& rs2) {
  if ((0x28u <= funct6 && funct6 < 0x30u) || funct6 >= 0x3Cu) {
    std::swap(rs1, rs2);
  }
}

void DisassemblerRiscv64::Printer::Print32RVVOp(uint32_t insn32) {
  // TODO(riscv64): Print pseudo-instruction aliases when applicable.
  DCHECK_EQ(insn32 & 0x7fu, 0x57u);
  const enum VAIEncodings vai = static_cast<enum VAIEncodings>((insn32 >> 12) & 7u);
  const uint32_t funct7 = Decode32UImm7(insn32);
  const uint32_t funct6 = funct7 >> 1;
  const bool masked = (funct7 & 1) == 0;
  const char* vm = masked ? ", v0.t" : "";
  const char* opcode = nullptr;
  const char* rd = nullptr;
  const char* rs1 = nullptr;
  const char* rs2 = nullptr;

  switch (vai) {
    case VAIEncodings::kOpIVV: {
      static constexpr const char* kOPIVVOpcodes[64] = {
          "vadd.vv",      nullptr,       "vsub.vv",         nullptr,
          "vminu.vv",     "vmin.vv",     "vmaxu.vv",        "vmax.vv",
          nullptr,        "vand.vv",     "vor.vv",          "vxor.vv",
          "vrgather.vv",  nullptr,       "vrgatherei16.vv", nullptr,
          "vadc.vvm",     "vmadc.vvm",   "vsbc.vvm",        "vmsbc.vvm",
          nullptr,        nullptr,       nullptr,           "<vmerge/vmv>",
          "vmseq.vv",     "vmsne.vv",    "vmsltu.vv",       "vmslt.vv",
          "vmsleu.vv",    "vmsle.vv",    nullptr,           nullptr,
          "vsaddu.vv",    "vsadd.vv",    "vssubu.vv",       "vssub.vv",
          nullptr,        "vsll.vv",     nullptr,           "vsmul.vv",
          "vsrl.vv",      "vsra.vv",     "vssrl.vv",        "vssra.vv",
          "vnsrl.wv",     "vnsra.wv",    "vnclipu.wv",      "vnclip.wv",
          "vwredsumu.vs", "vwredsum.vs", nullptr,           nullptr,
          nullptr,        nullptr,       nullptr,           nullptr,
          nullptr,        nullptr,       nullptr,           nullptr,
          nullptr,        nullptr,       nullptr,           nullptr,
      };

      rs2 = VRegName(GetRs2(insn32));
      if (funct6 == 0b010111) {
        // vmerge/vmv
        if (masked) {
          opcode = "vmerge.vvm";
          vm = ", v0";
        } else if (GetRs2(insn32) == 0) {
          opcode = "vmv.v.v";
          rs2 = nullptr;
        } else {
          opcode = nullptr;
        }
      } else {
        opcode = kOPIVVOpcodes[funct6];
      }

      rd = VRegName(GetRd(insn32));
      rs1 = VRegName(GetRs1(insn32));
      break;
    }
    case VAIEncodings::kOpIVX: {
      static constexpr const char* kOPIVXOpcodes[64] = {
          "vadd.vx",     nullptr,     "vsub.vx",     "vrsub.vx",
          "vminu.vx",    "vmin.vx",   "vmaxu.vx",    "vmax.vx",
          nullptr,       "vand.vx",   "vor.vx",      "vxor.vx",
          "vrgather.vx", nullptr,     "vslideup.vx", "vslidedown.vx",
          "vadc.vxm",    "vmadc.vxm", "vsbc.vxm",    "vmsbc.vxm",
          nullptr,       nullptr,     nullptr,       "<vmerge/vmv>",
          "vmseq.vx",    "vmsne.vx",  "vmsltu.vx",   "vmslt.vx",
          "vmsleu.vx",   "vmsle.vx",  "vmsgtu.vx",   "vmsgt.vx",
          "vsaddu.vx",   "vsadd.vx",  "vssubu.vx",   "vssub.vx",
          nullptr,       "vsll.vx",   nullptr,       "vsmul.vx",
          "vsrl.vx",     "vsra.vx",   "vssrl.vx",    "vssra.vx",
          "vnsrl.wx",    "vnsra.wx",  "vnclipu.wx",  "vnclip.wx",
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
      };

      rs2 = VRegName(GetRs2(insn32));
      if (funct6 == 0b010111) {
        // vmerge/vmv
        if (masked) {
          opcode = "vmerge.vxm";
          vm = ", v0";
        } else if (GetRs2(insn32) == 0) {
          opcode = "vmv.v.x";
          rs2 = nullptr;
        } else {
          opcode = nullptr;
        }
      } else {
        opcode = kOPIVXOpcodes[funct6];
      }

      rd = VRegName(GetRd(insn32));
      rs1 = XRegName(GetRs1(insn32));
      break;
    }
    case VAIEncodings::kOpIVI: {
      static constexpr const char* kOPIVIOpcodes[64] = {
          "vadd.vi",     nullptr,     nullptr,       "vrsub.vi",
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       "vand.vi",   "vor.vi",      "vxor.vi",
          "vrgather.vi", nullptr,     "vslideup.vi", "vslidedown.vi",
          "vadc.vim",    "vmadc.vim", nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       "<vmerge/vmv>",
          "vmseq.vi",    "vmsne.vi",  nullptr,       nullptr,
          "vmsleu.vi",   "vmsle.vi",  "vmsgtu.vi",   "vmsgt.vi",
          "vsaddu.vi",   "vsadd.vi",  nullptr,       nullptr,
          nullptr,       "vsll.vi",   nullptr,       "<vmvNr.v>",
          "vsrl.vi",     "vsra.vi",   "vssrl.vi",    "vssra.vi",
          "vnsrl.wi",    "vnsra.wi",  "vnclipu.wi",  "vnclip.wi",
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
          nullptr,       nullptr,     nullptr,       nullptr,
      };

      rs2 = VRegName(GetRs2(insn32));

      if (funct6 == 0b010111) {
        // vmerge/vmv
        if (masked) {
          opcode = "vmerge.vim";
          vm = ", v0";
        } else if (GetRs2(insn32) == 0) {
          opcode = "vmv.v.i";
          rs2 = nullptr;
        } else {
          opcode = nullptr;
        }
      } else if (funct6 == 0b100111) {
        uint32_t rs1V = GetRs1(insn32);
        static constexpr const char* kVmvnrOpcodes[8] = {
            "vmv1r.v", "vmv2r.v", nullptr, "vmv4r.v",
            nullptr,   nullptr,   nullptr, "vmv8r.v",
        };
        if (IsUint<3>(rs1V)) {
          opcode = kVmvnrOpcodes[rs1V];
        }
      } else {
        opcode = kOPIVIOpcodes[funct6];
      }

      rd = VRegName(GetRd(insn32));
      break;
    }
    case VAIEncodings::kOpMVV: {
      switch (funct6) {
        case VWXUNARY0: {
          static constexpr const char* kVWXUNARY0Opcodes[32] = {
              "vmv.x.s", nullptr,    nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
              "vcpop.m", "vfirst.m", nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
              nullptr,   nullptr,    nullptr, nullptr,
          };
          opcode = kVWXUNARY0Opcodes[GetRs1(insn32)];
          rd = XRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        case VXUNARY0: {
          static constexpr const char* kVXUNARY0Opcodes[32] = {
              nullptr,     nullptr,     "vzext.vf8", "vsext.vf8",
              "vzext.vf4", "vsext.vf4", "vzext.vf2", "vsext.vf2",
              nullptr,     nullptr,     nullptr,     nullptr,
              nullptr,     nullptr,     nullptr,     nullptr,
              nullptr,     nullptr,     nullptr,     nullptr,
              nullptr,     nullptr,     nullptr,     nullptr,
              nullptr,     nullptr,     nullptr,     nullptr,
              nullptr,     nullptr,     nullptr,     nullptr,
          };
          opcode = kVXUNARY0Opcodes[GetRs1(insn32)];
          rd = VRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        case VMUNARY0: {
          static constexpr const char* kVMUNARY0Opcodes[32] = {
              nullptr,   "vmsbf.m", "vmsof.m", "vmsif.m",
              nullptr,   nullptr,   nullptr,   nullptr,
              nullptr,   nullptr,   nullptr,   nullptr,
              nullptr,   nullptr,   nullptr,   nullptr,
              "viota.m", "vid.v",   nullptr,   nullptr,
              nullptr,   nullptr,   nullptr,   nullptr,
              nullptr,   nullptr,   nullptr,   nullptr,
              nullptr,   nullptr,   nullptr,   nullptr,
          };
          opcode = kVMUNARY0Opcodes[GetRs1(insn32)];
          rd = VRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        default: {
          static constexpr const char* kOPMVVOpcodes[64] = {
              "vredsum.vs",  "vredand.vs", "vredor.vs",   "vredxor.vs",
              "vredminu.vs", "vredmin.vs", "vredmaxu.vs", "vredmax.vs",
              "vaaddu.vv",   "vaadd.vv",   "vasubu.vv",   "vasub.vv",
              nullptr,       nullptr,      nullptr,       nullptr,
              "<VWXUNARY0>", nullptr,      "<VXUNARY0>",  nullptr,
              "<VMUNARY0>",  nullptr,      nullptr,       "vcompress.vm",
              "vmandn.mm",   "vmand.mm",   "vmor.mm",     "vmxor.mm",
              "vmorn.mm",    "vmnand.mm",  "vmnor.mm",    "vmxnor.mm",
              "vdivu.vv",    "vdiv.vv",    "vremu.vv",    "vrem.vv",
              "vmulhu.vv",   "vmul.vv",    "vmulhsu.vv",  "vmulh.vv",
              nullptr,       "vmadd.vv",   nullptr,       "vnmsub.vv",
              nullptr,       "vmacc.vv",   nullptr,       "vnmsac.vv",
              "vwaddu.vv",   "vwadd.vv",   "vwsubu.vv",   "vwsub.vv",
              "vwaddu.wv",   "vwadd.wv",   "vwsubu.wv",   "vwsub.wv",
              "vwmulu.vv",   nullptr,      "vwmulsu.vv",  "vwmul.vv",
              "vwmaccu.vv", "vwmacc.vv",   nullptr,       "vwmaccsu.vv",
          };

          opcode = kOPMVVOpcodes[funct6];
          rd = VRegName(GetRd(insn32));
          rs1 = VRegName(GetRs1(insn32));
          rs2 = VRegName(GetRs2(insn32));

          if (0x17u <= funct6 && funct6 <= 0x1Fu) {
            if (masked) {
              // for vcompress.vm and *.mm encodings with vm=0 are reserved
              opcode = nullptr;
            }
          }

          MaybeSwapOperands(funct6, rs1, rs2);

          break;
        }
      }
      break;
    }
    case VAIEncodings::kOpMVX: {
      switch (funct6) {
        case VRXUNARY0: {
          opcode = GetRs2(insn32) == 0u ? "vmv.s.x" : nullptr;
          rd = VRegName(GetRd(insn32));
          rs1 = XRegName(GetRs1(insn32));
          break;
        }
        default: {
          static constexpr const char* kOPMVXOpcodes[64] = {
              nullptr,      nullptr,      nullptr,        nullptr,
              nullptr,      nullptr,      nullptr,        nullptr,
              "vaaddu.vx",  "vaadd.vx",   "vasubu.vx",    "vasub.vx",
              nullptr,       nullptr,     "vslide1up.vx", "vslide1down.vx",
              "<VRXUNARY0>", nullptr,     nullptr,        nullptr,
              nullptr,       nullptr,     nullptr,        nullptr,
              nullptr,       nullptr,     nullptr,        nullptr,
              nullptr,       nullptr,     nullptr,        nullptr,
              "vdivu.vx",    "vdiv.vx",   "vremu.vx",     "vrem.vx",
              "vmulhu.vx",   "vmul.vx",   "vmulhsu.vx",   "vmulh.vx",
              nullptr,       "vmadd.vx",  nullptr,        "vnmsub.vx",
              nullptr,       "vmacc.vx",  nullptr,        "vnmsac.vx",
              "vwaddu.vx",   "vwadd.vx",  "vwsubu.vx",    "vwsub.vx",
              "vwaddu.wv",   "vwadd.wv",  "vwsubu.wv",    "vwsub.wv",
              "vwmulu.vx",   nullptr,     "vwmulsu.vx",   "vwmul.vx",
              "vwmaccu.vx",  "vwmacc.vx", "vwmaccus.vx",  "vwmaccsu.vx",
          };
          opcode = kOPMVXOpcodes[funct6];
          rd = VRegName(GetRd(insn32));
          rs1 = XRegName(GetRs1(insn32));
          rs2 = VRegName(GetRs2(insn32));

          MaybeSwapOperands(funct6, rs1, rs2);

          break;
        }
      }
      break;
    }
    case VAIEncodings::kOpFVV: {
      switch (funct6) {
        case VWFUNARY0: {
          opcode = GetRs1(insn32) == 0u ? "vfmv.f.s" : nullptr;
          rd = XRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        case VFUNARY0: {
          static constexpr const char* kVFUNARY0Opcodes[32] = {
              "vfcvt.xu.f.v",  "vfcvt.x.f.v",      "vfcvt.f.xu.v",      "vfcvt.f.x.v",
              nullptr,         nullptr,            "vfcvt.rtz.xu.f.v",  "vfcvt.rtz.x.f.v",
              "vfwcvt.xu.f.v", "vfwcvt.x.f.v",     "vfwcvt.f.xu.v",     "vfwcvt.f.x.v",
              "vfwcvt.f.f.v",  nullptr,            "vfwcvt.rtz.xu.f.v", "vfwcvt.rtz.x.f.v",
              "vfncvt.xu.f.w", "vfncvt.x.f.w",     "vfncvt.f.xu.w",     "vfncvt.f.x.w",
              "vfncvt.f.f.w",  "vfncvt.rod.f.f.w", "vfncvt.rtz.xu.f.w", "vfncvt.rtz.x.f.w",
              nullptr,         nullptr,            nullptr,             nullptr,
              nullptr,         nullptr,            nullptr,             nullptr,
          };
          opcode = kVFUNARY0Opcodes[GetRs1(insn32)];
          rd = VRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        case VFUNARY1: {
          static constexpr const char* kVFUNARY1Opcodes[32] = {
              "vfsqrt.v",   nullptr,    nullptr, nullptr,
              "vfrsqrt7.v", "vfrec7.v", nullptr, nullptr,
              nullptr,      nullptr,    nullptr, nullptr,
              nullptr,      nullptr,    nullptr, nullptr,
              "vfclass.v",  nullptr,    nullptr, nullptr,
              nullptr,      nullptr,    nullptr, nullptr,
              nullptr,      nullptr,    nullptr, nullptr,
              nullptr,      nullptr,    nullptr, nullptr,
          };
          opcode = kVFUNARY1Opcodes[GetRs1(insn32)];
          rd = VRegName(GetRd(insn32));
          rs2 = VRegName(GetRs2(insn32));
          break;
        }
        default: {
          static constexpr const char* kOPFVVOpcodes[64] = {
              "vfadd.vv",    "vfredusum.vs",  "vfsub.vv",   "vfredosum.vs",
              "vfmin.vv",    "vfredmin.vs",   "vfmax.vv",   "vfredmax.vs",
              "vfsgnj.vv",   "vfsgnjn.vv",    "vfsgnjx.vv", nullptr,
              nullptr,       nullptr,         nullptr,      nullptr,
              "<VWFUNARY0>", nullptr,         "<VFUNARY0>", "<VFUNARY1>",
              nullptr,       nullptr,         nullptr,      nullptr,
              "vmfeq.vv",    "vmfle.vv",      nullptr,      "vmflt.vv",
              "vmfne.vv",    nullptr,         nullptr,      nullptr,
              "vfdiv.vv",    nullptr,         nullptr,      nullptr,
              "vfmul.vv",    nullptr,         nullptr,      nullptr,
              "vfmadd.vv",   "vfnmadd.vv",    "vfmsub.vv",  "vfnmsub.vv",
              "vfmacc.vv",   "vfnmacc.vv",    "vfmsac.vv",  "vfnmsac.vv",
              "vfwadd.vv",   "vfwredusum.vs", "vfwsub.vv",  "vfwredosum.vs",
              "vfwadd.wv",   nullptr,         "vfwsub.wv",  nullptr,
              "vfwmul.vv",   nullptr,         nullptr,      nullptr,
              "vfwmacc.vv",  "vfwnmacc.vv",   "vfwmsac.vv", "vfwnmsac.vv",
          };
          opcode = kOPFVVOpcodes[funct6];
          rd = VRegName(GetRd(insn32));
          rs1 = VRegName(GetRs1(insn32));
          rs2 = VRegName(GetRs2(insn32));

          MaybeSwapOperands(funct6, rs1, rs2);

          break;
        }
      }
      break;
    }
    case VAIEncodings::kOpFVF: {
      switch (funct6) {
        case VRFUNARY0: {
          opcode = GetRs2(insn32) == 0u ? "vfmv.s.f" : nullptr;
          rd = VRegName(GetRd(insn32));
          rs1 = FRegName(GetRs1(insn32));
          break;
        }
        default: {
          static constexpr const char* kOPFVFOpcodes[64] = {
              "vfadd.vf",    nullptr,       "vfsub.vf",      nullptr,
              "vfmin.vf",    nullptr,       "vfmax.vf",      nullptr,
              "vfsgnj.vf",   "vfsgnjn.vf",  "vfsgnjx.vf",    nullptr,
              nullptr,       nullptr,       "vfslide1up.vf", "vfslide1down.vf",
              "<VRFUNARY0>", nullptr,       nullptr,         nullptr,
              nullptr,       nullptr,       nullptr,         "<vfmerge.vfm/vfmv>",
              "vmfeq.vf",    "vmfle.vf",    nullptr,         "vmflt.vf",
              "vmfne.vf",    "vmfgt.vf",    nullptr,         "vmfge.vf",
              "vfdiv.vf",    "vfrdiv.vf",   nullptr,         nullptr,
              "vfmul.vf",    nullptr,       nullptr,         "vfrsub.vf",
              "vfmadd.vf",   "vfnmadd.vf",  "vfmsub.vf",     "vfnmsub.vf",
              "vfmacc.vf",   "vfnmacc.vf",  "vfmsac.vf",     "vfnmsac.vf",
              "vfwadd.vf",   nullptr,       "vfwsub.vf",     nullptr,
              "vfwadd.wf",   nullptr,       "vfwsub.wf",     nullptr,
              "vfwmul.vf",   nullptr,       nullptr,         nullptr,
              "vfwmacc.vf",  "vfwnmacc.vf", "vfwmsac.vf",    "vfwnmsac.vf",
          };

          rs2 = VRegName(GetRs2(insn32));
          if (funct6 == 0b010111) {
            // vfmerge/vfmv
            if (masked) {
              opcode = "vfmerge.vfm";
              vm = ", v0";
            } else if (GetRs2(insn32) == 0) {
              opcode = "vfmv.v.f";
              rs2 = nullptr;
            } else {
              opcode = nullptr;
            }
          } else {
            opcode = kOPFVFOpcodes[funct6];
          }

          rd = VRegName(GetRd(insn32));
          rs1 = FRegName(GetRs1(insn32));

          MaybeSwapOperands(funct6, rs1, rs2);

          break;
        }
      }
      break;
    }
    case VAIEncodings::kOpCFG: {  // vector ALU control instructions
      if ((insn32 >> 31) != 0u) {
        if (((insn32 >> 30) & 0x1U) != 0u) {  // vsetivli
          const uint32_t zimm = Decode32UImm12(insn32) & ~0xC00U;
          const uint32_t imm5 = GetRs1(insn32);
          os_ << "vsetivli " << XRegName(GetRd(insn32)) << ", " << StringPrintf("0x%08x", imm5)
              << ", ";
          AppendVType(zimm);
        } else {  // vsetvl
          os_ << "vsetvl " << XRegName(GetRd(insn32)) << ", " << XRegName(GetRs1(insn32)) << ", "
              << XRegName(GetRs2(insn32));
          if ((Decode32UImm7(insn32) & 0x40u) != 0u) {
            os_ << "\t# incorrect funct7 literal : "
                << StringPrintf("0x%08x", Decode32UImm7(insn32));
          }
        }
      } else {  // vsetvli
        const uint32_t zimm = Decode32UImm12(insn32) & ~0x800U;
        os_ << "vsetvli " << XRegName(GetRd(insn32)) << ", " << XRegName(GetRs1(insn32)) << ", ";
        AppendVType(zimm);
      }
      return;
    }
  }

  if (opcode == nullptr) {
    os_ << "<unknown32>";
    return;
  }

  os_ << opcode << " " << rd;

  if (rs2 != nullptr) {
    os_ << ", " << rs2;
  }

  if (rs1 != nullptr) {
    os_ << ", " << rs1;
  } else if (vai == VAIEncodings::kOpIVI) {
    os_ << StringPrintf(", 0x%08x", GetRs1(insn32));
  }

  os_ << vm;
}

void DisassemblerRiscv64::Printer::Print32FpFma(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x73u, 0x43u);  // Note: Bits 0xc select the FMA opcode.
  uint32_t funct2 = (insn32 >> 25) & 3u;
  if (funct2 >= 2u) {
    os_ << "<unknown32>";  // Note: This includes the "H" and "Q" extensions.
    return;
  }
  static const char* const kOpcodes[] = { "fmadd", "fmsub", "fnmsub", "fnmadd" };
  os_ << kOpcodes[(insn32 >> 2) & 3u] << ((funct2 != 0u) ? ".d" : ".s")
      << RoundingModeName(GetRoundingMode(insn32)) << " "
      << FRegName(GetRd(insn32)) << ", " << FRegName(GetRs1(insn32)) << ", "
      << FRegName(GetRs2(insn32)) << ", " << FRegName(GetRs3(insn32));
}

void DisassemblerRiscv64::Printer::Print32Zicsr(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x73u);
  uint32_t funct3 = (insn32 >> 12) & 7u;
  static const char* const kOpcodes[] = {
      nullptr, "csrrw", "csrrs", "csrrc", nullptr, "csrrwi", "csrrsi", "csrrci"
  };
  const char* opcode = kOpcodes[funct3];
  if (opcode == nullptr) {
    os_ << "<unknown32>";
    return;
  }
  uint32_t rd = GetRd(insn32);
  uint32_t rs1_or_uimm = GetRs1(insn32);
  uint32_t csr = insn32 >> 20;
  // Print shorter macro instruction notation if available.
  if (funct3 == /*CSRRW*/ 1u && rd == 0u && rs1_or_uimm == 0u && csr == 0xc00u) {
    os_ << "unimp";
    return;
  } else if (funct3 == /*CSRRS*/ 2u && rs1_or_uimm == 0u) {
    if (csr == 0xc00u) {
      os_ << "rdcycle " << XRegName(rd);
    } else if (csr == 0xc01u) {
      os_ << "rdtime " << XRegName(rd);
    } else if (csr == 0xc02u) {
      os_ << "rdinstret " << XRegName(rd);
    } else {
      os_ << "csrr " << XRegName(rd) << ", " << csr;
    }
    return;
  }

  if (rd == 0u) {
    static const char* const kAltOpcodes[] = {
        nullptr, "csrw", "csrs", "csrc", nullptr, "csrwi", "csrsi", "csrci"
    };
    DCHECK(kAltOpcodes[funct3] != nullptr);
    os_ << kAltOpcodes[funct3] << " " << csr << ", ";
  } else {
    os_ << opcode << " " << XRegName(rd) << ", " << csr << ", ";
  }
  if (funct3 >= /*CSRRWI/CSRRSI/CSRRCI*/ 4u) {
    os_ << rs1_or_uimm;
  } else {
    os_ << XRegName(rs1_or_uimm);
  }
}

void DisassemblerRiscv64::Printer::Print32Fence(uint32_t insn32) {
  DCHECK_EQ(insn32 & 0x7fu, 0x0fu);
  if ((insn32 & 0xf00fffffu) == 0x0000000fu) {
    auto print_flags = [&](uint32_t flags) {
      if (flags == 0u) {
        os_ << "0";
      } else {
        DCHECK_LT(flags, 0x10u);
        static const char kFlagNames[] = "wroi";
        for (size_t bit : { 3u, 2u, 1u, 0u }) {  // Print in the "iorw" order.
          if ((flags & (1u << bit)) != 0u) {
            os_ << kFlagNames[bit];
          }
        }
      }
    };
    os_ << "fence.";
    print_flags((insn32 >> 24) & 0xfu);
    os_ << ".";
    print_flags((insn32 >> 20) & 0xfu);
  } else if (insn32 == 0x8330000fu) {
    os_ << "fence.tso";
  } else if (insn32 == 0x0000100fu) {
    os_ << "fence.i";
  } else {
    os_ << "<unknown32>";
  }
}

void DisassemblerRiscv64::Printer::Dump32(const uint8_t* insn) {
  uint32_t insn32 = static_cast<uint32_t>(insn[0]) +
                    (static_cast<uint32_t>(insn[1]) << 8) +
                    (static_cast<uint32_t>(insn[2]) << 16) +
                    (static_cast<uint32_t>(insn[3]) << 24);
  CHECK_EQ(insn32 & 3u, 3u);
  os_ << disassembler_->FormatInstructionPointer(insn) << StringPrintf(": %08x\t", insn32);
  switch (insn32 & 0x7fu) {
    case 0x37u:
      Print32Lui(insn32);
      break;
    case 0x17u:
      Print32Auipc(insn, insn32);
      break;
    case 0x6fu:
      Print32Jal(insn, insn32);
      break;
    case 0x67u:
      switch ((insn32 >> 12) & 7u) {  // funct3
        case 0:
          Print32Jalr(insn, insn32);
          break;
        default:
          os_ << "<unknown32>";
          break;
      }
      break;
    case 0x63u:
      Print32BCond(insn, insn32);
      break;
    case 0x03u:
      Print32Load(insn32);
      break;
    case 0x23u:
      Print32Store(insn32);
      break;
    case 0x07u:
      Print32FLoad(insn32);
      break;
    case 0x27u:
      Print32FStore(insn32);
      break;
    case 0x13u:
    case 0x1bu:
      Print32BinOpImm(insn32);
      break;
    case 0x33u:
    case 0x3bu:
      Print32BinOp(insn32);
      break;
    case 0x2fu:
      Print32Atomic(insn32);
      break;
    case 0x53u:
      Print32FpOp(insn32);
      break;
    case 0x57u:
      Print32RVVOp(insn32);
      break;
    case 0x43u:
    case 0x47u:
    case 0x4bu:
    case 0x4fu:
      Print32FpFma(insn32);
      break;
    case 0x73u:
      if ((insn32 & 0xffefffffu) == 0x00000073u) {
        os_ << ((insn32 == 0x00000073u) ? "ecall" : "ebreak");
      } else {
        Print32Zicsr(insn32);
      }
      break;
    case 0x0fu:
      Print32Fence(insn32);
      break;
    default:
      // TODO(riscv64): Disassemble more instructions.
      os_ << "<unknown32>";
      break;
  }
  os_ << "\n";
}

void DisassemblerRiscv64::Printer::Dump16(const uint8_t* insn) {
  uint32_t insn16 = static_cast<uint32_t>(insn[0]) + (static_cast<uint32_t>(insn[1]) << 8);
  ScopedNewLinePrinter nl(os_);
  CHECK_NE(insn16 & 3u, 3u);
  os_ << disassembler_->FormatInstructionPointer(insn) << StringPrintf(": %04x    \t", insn16);

  uint32_t funct3 = BitFieldExtract(insn16, 13, 3);
  int32_t offset = -1;

  switch (insn16 & 3u) {
    case 0b00u:  // Quadrant 0
      switch (funct3) {
        case 0b000u:
          if (insn16 == 0u) {
            os_ << "c.unimp";
          } else {
            uint32_t nzuimm = BitFieldExtract(insn16, 5, 8);
            if (nzuimm != 0u) {
              uint32_t decoded =
                  BitFieldExtract(nzuimm, 0, 1) << 3 | BitFieldExtract(nzuimm, 1, 1) << 2 |
                  BitFieldExtract(nzuimm, 2, 4) << 6 | BitFieldExtract(nzuimm, 6, 2) << 4;
              os_ << "c.addi4spn " << XRegName(GetRs2Short16(insn16)) << ", sp, " << decoded;
            } else {
              os_ << "<unknown16>";
            }
          }
          return;
        case 0b001u:
          offset = Decode16CMOffsetD(insn16);
          os_ << "c.fld " << FRegName(GetRs2Short16(insn16));
          break;
        case 0b010u:
          offset = Decode16CMOffsetW(insn16);
          os_ << "c.lw " << XRegName(GetRs2Short16(insn16));
          break;
        case 0b011u:
          offset = Decode16CMOffsetD(insn16);
          os_ << "c.ld " << XRegName(GetRs2Short16(insn16));
          break;
        case 0b100u: {
          uint32_t opcode2 = BitFieldExtract(insn16, 10, 3);
          uint32_t imm = BitFieldExtract(insn16, 5, 2);
          switch (opcode2) {
            case 0b000:
              offset = Uimm2ToOffset10(imm);
              os_ << "c.lbu " << XRegName(GetRs2Short16(insn16));
              break;
            case 0b001:
              offset = Uimm2ToOffset1(imm);
              os_ << (BitFieldExtract(imm, 1, 1) == 0u ? "c.lhu " : "c.lh ");
              os_ << XRegName(GetRs2Short16(insn16));
              break;
            case 0b010:
              offset = Uimm2ToOffset10(imm);
              os_ << "c.sb " << XRegName(GetRs2Short16(insn16));
              break;
            case 0b011:
              if (BitFieldExtract(imm, 1, 1) == 0u) {
                offset = Uimm2ToOffset1(imm);
                os_ << "c.sh " << XRegName(GetRs2Short16(insn16));
                break;
              }
              FALLTHROUGH_INTENDED;
            default:
              os_ << "<unknown16>";
              return;
          }
          break;
        }
        case 0b101u:
          offset = Decode16CMOffsetD(insn16);
          os_ << "c.fsd " << FRegName(GetRs2Short16(insn16));
          break;
        case 0b110u:
          offset = Decode16CMOffsetW(insn16);
          os_ << "c.sw " << XRegName(GetRs2Short16(insn16));
          break;
        case 0b111u:
          offset = Decode16CMOffsetD(insn16);
          os_ << "c.sd " << XRegName(GetRs2Short16(insn16));
          break;
        default:
          UNREACHABLE();
      }
      os_ << ", ";
      PrintLoadStoreAddress(GetRs1Short16(insn16), offset);
      return;
    case 0b01u:  // Quadrant 1
      switch (funct3) {
        case 0b000u: {
          uint32_t rd = GetRs1_16(insn16);
          if (rd == 0) {
            if (Decode16Imm6<uint32_t>(insn16) != 0u) {
              os_ << "<hint16>";
            } else {
              os_ << "c.nop";
            }
          } else {
            int32_t imm = Decode16Imm6<int32_t>(insn16);
            if (imm != 0) {
              os_ << "c.addi " << XRegName(rd) << ", " << imm;
            } else {
              os_ << "<hint16>";
            }
          }
          break;
        }
        case 0b001u: {
          uint32_t rd = GetRs1_16(insn16);
          if (rd != 0) {
            os_ << "c.addiw " << XRegName(rd) << ", " << Decode16Imm6<int32_t>(insn16);
          } else {
            os_ << "<unknown16>";
          }
          break;
        }
        case 0b010u: {
          uint32_t rd = GetRs1_16(insn16);
          if (rd != 0) {
            os_ << "c.li " << XRegName(rd) << ", " << Decode16Imm6<int32_t>(insn16);
          } else {
            os_ << "<hint16>";
          }
          break;
        }
        case 0b011u: {
          uint32_t rd = GetRs1_16(insn16);
          uint32_t imm6_bits = Decode16Imm6<uint32_t>(insn16);
          if (imm6_bits != 0u) {
            if (rd == 2) {
              int32_t nzimm =
                  BitFieldExtract(insn16, 6, 1) << 4 | BitFieldExtract(insn16, 2, 1) << 5 |
                  BitFieldExtract(insn16, 5, 1) << 6 | BitFieldExtract(insn16, 3, 2) << 7 |
                  BitFieldExtract(insn16, 12, 1) << 9;
              os_ << "c.addi16sp sp, " << SignExtendBits<10>(nzimm);
            } else if (rd != 0) {
              // sign-extend bits and mask with 0xfffff as llvm-objdump does
              uint32_t mask = MaskLeastSignificant<uint32_t>(20);
              os_ << "c.lui " << XRegName(rd) << ", " << (SignExtendBits<6>(imm6_bits) & mask);
            } else {
              os_ << "<hint16>";
            }
          } else {
            os_ << "<unknown16>";
          }
          break;
        }
        case 0b100u: {
          uint32_t funct2 = BitFieldExtract(insn16, 10, 2);
          switch (funct2) {
            case 0b00: {
              int32_t nzuimm = Decode16Imm6<uint32_t>(insn16);
              if (nzuimm != 0) {
                os_ << "c.srli " << XRegName(GetRs1Short16(insn16)) << ", " << nzuimm;
              } else {
                os_ << "<hint16>";
              }
              break;
            }
            case 0b01: {
              int32_t nzuimm = Decode16Imm6<uint32_t>(insn16);
              if (nzuimm != 0) {
                os_ << "c.srai " << XRegName(GetRs1Short16(insn16)) << ", " << nzuimm;
              } else {
                os_ << "<hint16>";
              }
              break;
            }
            case 0b10:
              os_ << "c.andi " << XRegName(GetRs1Short16(insn16)) << ", "
                  << Decode16Imm6<int32_t>(insn16);
              break;
            case 0b11: {
              constexpr static const char* mnemonics[] = {
                  "c.sub", "c.xor", "c.or", "c.and", "c.subw", "c.addw", "c.mul", nullptr
              };
              uint32_t opc = BitFieldInsert(
                  BitFieldExtract(insn16, 5, 2), BitFieldExtract(insn16, 12, 1), 2, 1);
              DCHECK(IsUint<3>(opc));
              const char* mnem = mnemonics[opc];
              if (mnem != nullptr) {
                os_ << mnem << " " << XRegName(GetRs1Short16(insn16)) << ", "
                    << XRegName(GetRs2Short16(insn16));
              } else {
                constexpr static const char* zbc_mnemonics[] = {
                    "c.zext.b", "c.sext.b", "c.zext.h", "c.sext.h",
                    "c.zext.w", "c.not",    nullptr,    nullptr,
                };
                mnem = zbc_mnemonics[BitFieldExtract(insn16, 2, 3)];
                if (mnem != nullptr) {
                  os_ << mnem << " " << XRegName(GetRs1Short16(insn16));
                } else {
                  os_ << "<unknown16>";
                }
              }
              break;
            }
            default:
              UNREACHABLE();
          }
          break;
        }
        case 0b101u: {
          int32_t disp = BitFieldExtract(insn16, 3, 3) << 1  | BitFieldExtract(insn16, 11, 1) << 4 |
                         BitFieldExtract(insn16, 2, 1) << 5  | BitFieldExtract(insn16, 7,  1) << 6 |
                         BitFieldExtract(insn16, 6, 1) << 7  | BitFieldExtract(insn16, 9,  2) << 8 |
                         BitFieldExtract(insn16, 8, 1) << 10 | BitFieldExtract(insn16, 12, 1) << 11;
          os_ << "c.j ";
          PrintBranchOffset(SignExtendBits<12>(disp));
          break;
        }
        case 0b110u:
        case 0b111u: {
          int32_t disp = BitFieldExtract(insn16, 3, 2) << 1 | BitFieldExtract(insn16, 10, 2) << 3 |
                         BitFieldExtract(insn16, 2, 1) << 5 | BitFieldExtract(insn16, 5,  2) << 6 |
                         BitFieldExtract(insn16, 12, 1) << 8;

          os_ << (funct3 == 0b110u ? "c.beqz " : "c.bnez ");
          os_ << XRegName(GetRs1Short16(insn16)) << ", ";
          PrintBranchOffset(SignExtendBits<9>(disp));
          break;
        }
        default:
          UNREACHABLE();
      }
      break;
    case 0b10u:  // Quadrant 2
      switch (funct3) {
        case 0b000u: {
          uint32_t nzuimm = Decode16Imm6<uint32_t>(insn16);
          uint32_t rd = GetRs1_16(insn16);
          if (rd == 0 || nzuimm == 0) {
            os_ << "<hint16>";
          } else {
            os_ << "c.slli " << XRegName(rd) << ", " << nzuimm;
          }
          return;
        }
        case 0b001u: {
          offset = Uimm6ToOffsetD16(Decode16Imm6<uint32_t>(insn16));
          os_ << "c.fldsp " << FRegName(GetRs1_16(insn16));
          break;
        }
        case 0b010u: {
          uint32_t rd = GetRs1_16(insn16);
          if (rd != 0) {
            offset = Uimm6ToOffsetW16(Decode16Imm6<uint32_t>(insn16));
            os_ << "c.lwsp " << XRegName(GetRs1_16(insn16));
          } else {
            os_ << "<unknown16>";
            return;
          }
          break;
        }
        case 0b011u: {
          uint32_t rd = GetRs1_16(insn16);
          if (rd != 0) {
            offset = Uimm6ToOffsetD16(Decode16Imm6<uint32_t>(insn16));
            os_ << "c.ldsp " << XRegName(GetRs1_16(insn16));
          } else {
            os_ << "<unknown16>";
            return;
          }
          break;
        }
        case 0b100u: {
          uint32_t rd_rs1 = GetRs1_16(insn16);
          uint32_t rs2 = GetRs2_16(insn16);
          uint32_t b12 = BitFieldExtract(insn16, 12, 1);
          if (b12 == 0) {
            if (rd_rs1 != 0 && rs2 != 0) {
              os_ << "c.mv " << XRegName(rd_rs1) << ", " << XRegName(rs2);
            } else if (rd_rs1 != 0) {
              os_ << "c.jr " << XRegName(rd_rs1);
            } else if (rs2 != 0) {
              os_ << "<hint16>";
            } else {
              os_ << "<unknown16>";
            }
          } else {
            if (rd_rs1 != 0 && rs2 != 0) {
              os_ << "c.add " << XRegName(rd_rs1) << ", " << XRegName(rs2);
            } else if (rd_rs1 != 0) {
              os_ << "c.jalr " << XRegName(rd_rs1);
            } else if (rs2 != 0) {
              os_ << "<hint16>";
            } else {
              os_ << "c.ebreak";
            }
          }
          return;
        }
        case 0b101u:
          offset = BitFieldExtract(insn16, 7, 3) << 6 | BitFieldExtract(insn16, 10, 3) << 3;
          os_ << "c.fsdsp " << FRegName(GetRs2_16(insn16));
          break;
        case 0b110u:
          offset = BitFieldExtract(insn16, 7, 2) << 6 | BitFieldExtract(insn16, 9, 4) << 2;
          os_ << "c.swsp " << XRegName(GetRs2_16(insn16));
          break;
        case 0b111u:
          offset = BitFieldExtract(insn16, 7, 3) << 6 | BitFieldExtract(insn16, 10, 3) << 3;
          os_ << "c.sdsp " << XRegName(GetRs2_16(insn16));
          break;
        default:
          UNREACHABLE();
      }

      os_ << ", ";
      PrintLoadStoreAddress(/* sp */ 2, offset);

      break;
    default:
      UNREACHABLE();
  }
}

void DisassemblerRiscv64::Printer::Dump2Byte(const uint8_t* data) {
  uint32_t value = data[0] + (data[1] << 8);
  os_ << disassembler_->FormatInstructionPointer(data)
      << StringPrintf(": %04x    \t.2byte %u\n", value, value);
}

void DisassemblerRiscv64::Printer::DumpByte(const uint8_t* data) {
  uint32_t value = *data;
  os_ << disassembler_->FormatInstructionPointer(data)
      << StringPrintf(": %02x      \t.byte %u\n", value, value);
}

size_t DisassemblerRiscv64::Dump(std::ostream& os, const uint8_t* begin) {
  if (begin < GetDisassemblerOptions()->base_address_ ||
      begin >= GetDisassemblerOptions()->end_address_) {
    return 0u;  // Outside the range.
  }
  Printer printer(this, os);
  if (!IsAligned<2u>(begin) || GetDisassemblerOptions()->end_address_ - begin == 1) {
    printer.DumpByte(begin);
    return 1u;
  }
  if ((*begin & 3u) == 3u) {
    if (GetDisassemblerOptions()->end_address_ - begin >= 4) {
      printer.Dump32(begin);
      return 4u;
    } else {
      printer.Dump2Byte(begin);
      return 2u;
    }
  } else {
    printer.Dump16(begin);
    return 2u;
  }
}

void DisassemblerRiscv64::Dump(std::ostream& os, const uint8_t* begin, const uint8_t* end) {
  Printer printer(this, os);
  const uint8_t* cur = begin;
  if (cur < end && !IsAligned<2u>(cur)) {
    // Unaligned, dump as a `.byte` to get to an aligned address.
    printer.DumpByte(cur);
    cur += 1;
  }
  if (cur >= end) {
    return;
  }
  while (end - cur >= 4) {
    if ((*cur & 3u) == 3u) {
      printer.Dump32(cur);
      cur += 4;
    } else {
      printer.Dump16(cur);
      cur += 2;
    }
  }
  if (end - cur >= 2) {
    if ((*cur & 3u) == 3u) {
      // Not enough data for a 32-bit instruction. Dump as `.2byte`.
      printer.Dump2Byte(cur);
    } else {
      printer.Dump16(cur);
    }
    cur += 2;
  }
  if (end != cur) {
    CHECK_EQ(end - cur, 1);
    printer.DumpByte(cur);
  }
}

}  // namespace riscv64
}  // namespace art
