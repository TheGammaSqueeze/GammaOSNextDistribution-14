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

#ifndef ART_COMPILER_UTILS_RISCV64_ASSEMBLER_RISCV64_H_
#define ART_COMPILER_UTILS_RISCV64_ASSEMBLER_RISCV64_H_

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "arch/riscv64/instruction_set_features_riscv64.h"
#include "base/arena_containers.h"
#include "base/enums.h"
#include "base/globals.h"
#include "base/macros.h"
#include "managed_register_riscv64.h"
#include "utils/assembler.h"
#include "utils/label.h"

namespace art HIDDEN {
namespace riscv64 {

class ScratchRegisterScope;

static constexpr size_t kRiscv64HalfwordSize = 2;
static constexpr size_t kRiscv64WordSize = 4;
static constexpr size_t kRiscv64DoublewordSize = 8;
static constexpr size_t kRiscv64FloatRegSizeInBytes = 8;

// The `Riscv64Extension` enumeration is used for restricting the instructions that the assembler
// can use. Some restrictions are checked only in debug mode (for example load and store
// instructions check `kLoadStore`), other restrictions are checked at run time and affect the
// emitted code (for example, the `SextW()` pseudo-instruction selects between an implementation
// from "Zcb", "Zbb" and a two-instruction sequence from the basic instruction set.
enum class Riscv64Extension : uint32_t {
  kLoadStore,  // Pseudo-extension encompassing all loads and stores. Used to check that
               // we do not have loads and stores in the middle of a LR/SC sequence.
  kZifencei,
  kM,
  kA,
  kZicsr,
  kF,
  kD,
  kZba,
  kZbb,
  kZbs,  // TODO(riscv64): Implement "Zbs" instructions.
  kV,
  kZca,  // "C" extension instructions except floating point loads/stores.
  kZcd,  // "C" extension double loads/stores.
         // Note: RV64 cannot implement Zcf ("C" extension float loads/stores).
  kZcb,  // Simple 16-bit operations not present in the original "C" extension.

  kLast = kZcb
};

using Riscv64ExtensionMask = uint32_t;

constexpr Riscv64ExtensionMask Riscv64ExtensionBit(Riscv64Extension ext) {
  return 1u << enum_cast<>(ext);
}

constexpr Riscv64ExtensionMask kRiscv64AllExtensionsMask =
    MaxInt<Riscv64ExtensionMask>(enum_cast<>(Riscv64Extension::kLast) + 1);

// Extensions allowed in a LR/SC sequence (between the LR and SC).
constexpr Riscv64ExtensionMask kRiscv64LrScSequenceExtensionsMask =
    Riscv64ExtensionBit(Riscv64Extension::kZca);

enum class FPRoundingMode : uint32_t {
  kRNE = 0x0,  // Round to Nearest, ties to Even
  kRTZ = 0x1,  // Round towards Zero
  kRDN = 0x2,  // Round Down (towards −Infinity)
  kRUP = 0x3,  // Round Up (towards +Infinity)
  kRMM = 0x4,  // Round to Nearest, ties to Max Magnitude
  kDYN = 0x7,  // Dynamic rounding mode
  kDefault = kDYN,
  // Some instructions never need to round even though the spec includes the RM field.
  // To simplify testing, emit the RM as 0 by default for these instructions because that's what
  // `clang` does and because the `llvm-objdump` fails to disassemble the other rounding modes.
  kIgnored = 0
};

enum class AqRl : uint32_t {
  kNone    = 0x0,
  kRelease = 0x1,
  kAcquire = 0x2,
  kAqRl    = kRelease | kAcquire
};

// the type for fence
enum FenceType {
  kFenceNone = 0,
  kFenceWrite = 1,
  kFenceRead = 2,
  kFenceOutput = 4,
  kFenceInput = 8,
  kFenceDefault = 0xf,
};

// Used to test the values returned by FClassS/FClassD.
enum FPClassMaskType {
  kNegativeInfinity  = 0x001,
  kNegativeNormal    = 0x002,
  kNegativeSubnormal = 0x004,
  kNegativeZero      = 0x008,
  kPositiveZero      = 0x010,
  kPositiveSubnormal = 0x020,
  kPositiveNormal    = 0x040,
  kPositiveInfinity  = 0x080,
  kSignalingNaN      = 0x100,
  kQuietNaN          = 0x200,
};

enum class CSRAddress : uint32_t {
  kVstart = 0x008,     // Vector start position, URW
  kVxsat = 0x009,      // Fixed-Point Saturate Flag, URW
  kVxrm = 0x00A,       // Fixed-Point Rounding Mode, URW
  kReserved1 = 0x00B,  // Reserved for future vector CSRs
  kReserved2 = 0x00C,
  kReserved3 = 0x00D,
  kReserved4 = 0x00E,
  kVcsr = 0x00F,   // Vector control and status register, URW
  kVl = 0xC20,     // Vector length, URO
  kVtype = 0xC21,  // Vector data type register, URO
  kVlenb = 0xC22,  // VLEN/8 (vector register length in bytes), URO
};

class Riscv64Label : public Label {
 public:
  Riscv64Label() : prev_branch_id_(kNoPrevBranchId) {}

  Riscv64Label(Riscv64Label&& src) noexcept
      // NOLINTNEXTLINE - src.prev_branch_id_ is valid after the move
      : Label(std::move(src)), prev_branch_id_(src.prev_branch_id_) {}

 private:
  static constexpr uint32_t kNoPrevBranchId = std::numeric_limits<uint32_t>::max();

  uint32_t prev_branch_id_;  // To get distance from preceding branch, if any.

  friend class Riscv64Assembler;
  DISALLOW_COPY_AND_ASSIGN(Riscv64Label);
};

// Assembler literal is a value embedded in code, retrieved using a PC-relative load.
class Literal {
 public:
  static constexpr size_t kMaxSize = 8;

  Literal(uint32_t size, const uint8_t* data) : label_(), size_(size) {
    DCHECK_LE(size, Literal::kMaxSize);
    memcpy(data_, data, size);
  }

  template <typename T>
  T GetValue() const {
    DCHECK_EQ(size_, sizeof(T));
    T value;
    memcpy(&value, data_, sizeof(T));
    return value;
  }

  uint32_t GetSize() const { return size_; }

  const uint8_t* GetData() const { return data_; }

  Riscv64Label* GetLabel() { return &label_; }

  const Riscv64Label* GetLabel() const { return &label_; }

 private:
  Riscv64Label label_;
  const uint32_t size_;
  uint8_t data_[kMaxSize];

  DISALLOW_COPY_AND_ASSIGN(Literal);
};

// Jump table: table of labels emitted after the code and before the literals. Similar to literals.
class JumpTable {
 public:
  explicit JumpTable(ArenaVector<Riscv64Label*>&& labels) : label_(), labels_(std::move(labels)) {}

  size_t GetSize() const { return labels_.size() * sizeof(int32_t); }

  const ArenaVector<Riscv64Label*>& GetData() const { return labels_; }

  Riscv64Label* GetLabel() { return &label_; }

  const Riscv64Label* GetLabel() const { return &label_; }

 private:
  Riscv64Label label_;
  ArenaVector<Riscv64Label*> labels_;

  DISALLOW_COPY_AND_ASSIGN(JumpTable);
};

class Riscv64Assembler final : public Assembler {
 public:
  explicit Riscv64Assembler(ArenaAllocator* allocator,
                            const Riscv64InstructionSetFeatures* instruction_set_features = nullptr)
      : Riscv64Assembler(allocator,
                         instruction_set_features != nullptr
                             ? ConvertExtensions(instruction_set_features)
                             : kRiscv64AllExtensionsMask) {}

  Riscv64Assembler(ArenaAllocator* allocator, Riscv64ExtensionMask enabled_extensions)
      : Assembler(allocator),
        branches_(allocator->Adapter(kArenaAllocAssembler)),
        finalized_(false),
        overwriting_(false),
        overwrite_location_(0),
        literals_(allocator->Adapter(kArenaAllocAssembler)),
        long_literals_(allocator->Adapter(kArenaAllocAssembler)),
        jump_tables_(allocator->Adapter(kArenaAllocAssembler)),
        last_position_adjustment_(0),
        last_old_position_(0),
        last_branch_id_(0),
        enabled_extensions_(enabled_extensions),
        available_scratch_core_registers_((1u << TMP) | (1u << TMP2)),
        available_scratch_fp_registers_(1u << FTMP) {
    cfi().DelayEmittingAdvancePCs();
  }

  virtual ~Riscv64Assembler() {
    for (auto& branch : branches_) {
      CHECK(branch.IsResolved());
    }
  }

  size_t CodeSize() const override { return Assembler::CodeSize(); }
  DebugFrameOpCodeWriterForAssembler& cfi() { return Assembler::cfi(); }

  bool IsExtensionEnabled(Riscv64Extension ext) {
    return (enabled_extensions_ & Riscv64ExtensionBit(ext)) != 0u;
  }

  // According to "The RISC-V Instruction Set Manual"

  // LUI/AUIPC (RV32I, with sign-extension on RV64I), opcode = 0x17, 0x37
  // Note: These take a 20-bit unsigned value to align with the clang assembler for testing,
  // but the value stored in the register shall actually be sign-extended to 64 bits.
  void Lui(XRegister rd, uint32_t imm20);
  void Auipc(XRegister rd, uint32_t imm20);

  // Jump instructions (RV32I), opcode = 0x67, 0x6f
  void Jal(XRegister rd, int32_t offset);
  void Jalr(XRegister rd, XRegister rs1, int32_t offset);

  // Branch instructions (RV32I), opcode = 0x63, funct3 from 0x0 ~ 0x1 and 0x4 ~ 0x7
  void Beq(XRegister rs1, XRegister rs2, int32_t offset);
  void Bne(XRegister rs1, XRegister rs2, int32_t offset);
  void Blt(XRegister rs1, XRegister rs2, int32_t offset);
  void Bge(XRegister rs1, XRegister rs2, int32_t offset);
  void Bltu(XRegister rs1, XRegister rs2, int32_t offset);
  void Bgeu(XRegister rs1, XRegister rs2, int32_t offset);

  // Load instructions (RV32I+RV64I): opcode = 0x03, funct3 from 0x0 ~ 0x6
  void Lb(XRegister rd, XRegister rs1, int32_t offset);
  void Lh(XRegister rd, XRegister rs1, int32_t offset);
  void Lw(XRegister rd, XRegister rs1, int32_t offset);
  void Ld(XRegister rd, XRegister rs1, int32_t offset);
  void Lbu(XRegister rd, XRegister rs1, int32_t offset);
  void Lhu(XRegister rd, XRegister rs1, int32_t offset);
  void Lwu(XRegister rd, XRegister rs1, int32_t offset);

  // Store instructions (RV32I+RV64I): opcode = 0x23, funct3 from 0x0 ~ 0x3
  void Sb(XRegister rs2, XRegister rs1, int32_t offset);
  void Sh(XRegister rs2, XRegister rs1, int32_t offset);
  void Sw(XRegister rs2, XRegister rs1, int32_t offset);
  void Sd(XRegister rs2, XRegister rs1, int32_t offset);

  // IMM ALU instructions (RV32I): opcode = 0x13, funct3 from 0x0 ~ 0x7
  void Addi(XRegister rd, XRegister rs1, int32_t imm12);
  void Slti(XRegister rd, XRegister rs1, int32_t imm12);
  void Sltiu(XRegister rd, XRegister rs1, int32_t imm12);
  void Xori(XRegister rd, XRegister rs1, int32_t imm12);
  void Ori(XRegister rd, XRegister rs1, int32_t imm12);
  void Andi(XRegister rd, XRegister rs1, int32_t imm12);
  void Slli(XRegister rd, XRegister rs1, int32_t shamt);
  void Srli(XRegister rd, XRegister rs1, int32_t shamt);
  void Srai(XRegister rd, XRegister rs1, int32_t shamt);

  // ALU instructions (RV32I): opcode = 0x33, funct3 from 0x0 ~ 0x7
  void Add(XRegister rd, XRegister rs1, XRegister rs2);
  void Sub(XRegister rd, XRegister rs1, XRegister rs2);
  void Slt(XRegister rd, XRegister rs1, XRegister rs2);
  void Sltu(XRegister rd, XRegister rs1, XRegister rs2);
  void Xor(XRegister rd, XRegister rs1, XRegister rs2);
  void Or(XRegister rd, XRegister rs1, XRegister rs2);
  void And(XRegister rd, XRegister rs1, XRegister rs2);
  void Sll(XRegister rd, XRegister rs1, XRegister rs2);
  void Srl(XRegister rd, XRegister rs1, XRegister rs2);
  void Sra(XRegister rd, XRegister rs1, XRegister rs2);

  // 32bit Imm ALU instructions (RV64I): opcode = 0x1b, funct3 from 0x0, 0x1, 0x5
  void Addiw(XRegister rd, XRegister rs1, int32_t imm12);
  void Slliw(XRegister rd, XRegister rs1, int32_t shamt);
  void Srliw(XRegister rd, XRegister rs1, int32_t shamt);
  void Sraiw(XRegister rd, XRegister rs1, int32_t shamt);

  // 32bit ALU instructions (RV64I): opcode = 0x3b, funct3 from 0x0 ~ 0x7
  void Addw(XRegister rd, XRegister rs1, XRegister rs2);
  void Subw(XRegister rd, XRegister rs1, XRegister rs2);
  void Sllw(XRegister rd, XRegister rs1, XRegister rs2);
  void Srlw(XRegister rd, XRegister rs1, XRegister rs2);
  void Sraw(XRegister rd, XRegister rs1, XRegister rs2);

  // Environment call and breakpoint (RV32I), opcode = 0x73
  void Ecall();
  void Ebreak();

  // Fence instruction (RV32I): opcode = 0xf, funct3 = 0
  void Fence(uint32_t pred = kFenceDefault, uint32_t succ = kFenceDefault);
  void FenceTso();

  // "Zifencei" Standard Extension, opcode = 0xf, funct3 = 1
  void FenceI();

  // RV32M Standard Extension: opcode = 0x33, funct3 from 0x0 ~ 0x7
  void Mul(XRegister rd, XRegister rs1, XRegister rs2);
  void Mulh(XRegister rd, XRegister rs1, XRegister rs2);
  void Mulhsu(XRegister rd, XRegister rs1, XRegister rs2);
  void Mulhu(XRegister rd, XRegister rs1, XRegister rs2);
  void Div(XRegister rd, XRegister rs1, XRegister rs2);
  void Divu(XRegister rd, XRegister rs1, XRegister rs2);
  void Rem(XRegister rd, XRegister rs1, XRegister rs2);
  void Remu(XRegister rd, XRegister rs1, XRegister rs2);

  // RV64M Standard Extension: opcode = 0x3b, funct3 0x0 and from 0x4 ~ 0x7
  void Mulw(XRegister rd, XRegister rs1, XRegister rs2);
  void Divw(XRegister rd, XRegister rs1, XRegister rs2);
  void Divuw(XRegister rd, XRegister rs1, XRegister rs2);
  void Remw(XRegister rd, XRegister rs1, XRegister rs2);
  void Remuw(XRegister rd, XRegister rs1, XRegister rs2);

  // RV32A/RV64A Standard Extension
  void LrW(XRegister rd, XRegister rs1, AqRl aqrl);
  void LrD(XRegister rd, XRegister rs1, AqRl aqrl);
  void ScW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void ScD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoSwapW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoSwapD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoAddW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoAddD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoXorW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoXorD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoAndW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoAndD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoOrW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoOrD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMinW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMinD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMaxW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMaxD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMinuW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMinuD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMaxuW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);
  void AmoMaxuD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl);

  // "Zicsr" Standard Extension, opcode = 0x73, funct3 from 0x1 ~ 0x3 and 0x5 ~ 0x7
  void Csrrw(XRegister rd, uint32_t csr, XRegister rs1);
  void Csrrs(XRegister rd, uint32_t csr, XRegister rs1);
  void Csrrc(XRegister rd, uint32_t csr, XRegister rs1);
  void Csrrwi(XRegister rd, uint32_t csr, uint32_t uimm5);
  void Csrrsi(XRegister rd, uint32_t csr, uint32_t uimm5);
  void Csrrci(XRegister rd, uint32_t csr, uint32_t uimm5);

  // FP load/store instructions (RV32F+RV32D): opcode = 0x07, 0x27
  void FLw(FRegister rd, XRegister rs1, int32_t offset);
  void FLd(FRegister rd, XRegister rs1, int32_t offset);
  void FSw(FRegister rs2, XRegister rs1, int32_t offset);
  void FSd(FRegister rs2, XRegister rs1, int32_t offset);

  // FP FMA instructions (RV32F+RV32D): opcode = 0x43, 0x47, 0x4b, 0x4f
  void FMAddS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FMAddD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FMSubS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FMSubD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FNMSubS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FNMSubD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FNMAddS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);
  void FNMAddD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm);

  // FP FMA instruction helpers passing the default rounding mode.
  void FMAddS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FMAddS(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FMAddD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FMAddD(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FMSubS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FMSubS(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FMSubD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FMSubD(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FNMSubS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FNMSubS(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FNMSubD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FNMSubD(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FNMAddS(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FNMAddS(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }
  void FNMAddD(FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3) {
    FNMAddD(rd, rs1, rs2, rs3, FPRoundingMode::kDefault);
  }

  // Simple FP instructions (RV32F+RV32D): opcode = 0x53, funct7 = 0b0XXXX0D
  void FAddS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FAddD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FSubS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FSubD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FMulS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FMulD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FDivS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FDivD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm);
  void FSqrtS(FRegister rd, FRegister rs1, FPRoundingMode frm);
  void FSqrtD(FRegister rd, FRegister rs1, FPRoundingMode frm);
  void FSgnjS(FRegister rd, FRegister rs1, FRegister rs2);
  void FSgnjD(FRegister rd, FRegister rs1, FRegister rs2);
  void FSgnjnS(FRegister rd, FRegister rs1, FRegister rs2);
  void FSgnjnD(FRegister rd, FRegister rs1, FRegister rs2);
  void FSgnjxS(FRegister rd, FRegister rs1, FRegister rs2);
  void FSgnjxD(FRegister rd, FRegister rs1, FRegister rs2);
  void FMinS(FRegister rd, FRegister rs1, FRegister rs2);
  void FMinD(FRegister rd, FRegister rs1, FRegister rs2);
  void FMaxS(FRegister rd, FRegister rs1, FRegister rs2);
  void FMaxD(FRegister rd, FRegister rs1, FRegister rs2);
  void FCvtSD(FRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtDS(FRegister rd, FRegister rs1, FPRoundingMode frm);

  // Simple FP instruction helpers passing the default rounding mode.
  void FAddS(FRegister rd, FRegister rs1, FRegister rs2) {
    FAddS(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FAddD(FRegister rd, FRegister rs1, FRegister rs2) {
    FAddD(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FSubS(FRegister rd, FRegister rs1, FRegister rs2) {
    FSubS(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FSubD(FRegister rd, FRegister rs1, FRegister rs2) {
    FSubD(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FMulS(FRegister rd, FRegister rs1, FRegister rs2) {
    FMulS(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FMulD(FRegister rd, FRegister rs1, FRegister rs2) {
    FMulD(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FDivS(FRegister rd, FRegister rs1, FRegister rs2) {
    FDivS(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FDivD(FRegister rd, FRegister rs1, FRegister rs2) {
    FDivD(rd, rs1, rs2, FPRoundingMode::kDefault);
  }
  void FSqrtS(FRegister rd, FRegister rs1) {
    FSqrtS(rd, rs1, FPRoundingMode::kDefault);
  }
  void FSqrtD(FRegister rd, FRegister rs1) {
    FSqrtD(rd, rs1, FPRoundingMode::kDefault);
  }
  void FCvtSD(FRegister rd, FRegister rs1) {
    FCvtSD(rd, rs1, FPRoundingMode::kDefault);
  }
  void FCvtDS(FRegister rd, FRegister rs1) {
    FCvtDS(rd, rs1, FPRoundingMode::kIgnored);
  }

  // FP compare instructions (RV32F+RV32D): opcode = 0x53, funct7 = 0b101000D
  void FEqS(XRegister rd, FRegister rs1, FRegister rs2);
  void FEqD(XRegister rd, FRegister rs1, FRegister rs2);
  void FLtS(XRegister rd, FRegister rs1, FRegister rs2);
  void FLtD(XRegister rd, FRegister rs1, FRegister rs2);
  void FLeS(XRegister rd, FRegister rs1, FRegister rs2);
  void FLeD(XRegister rd, FRegister rs1, FRegister rs2);

  // FP conversion instructions (RV32F+RV32D+RV64F+RV64D): opcode = 0x53, funct7 = 0b110X00D
  void FCvtWS(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtWD(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtWuS(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtWuD(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtLS(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtLD(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtLuS(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtLuD(XRegister rd, FRegister rs1, FPRoundingMode frm);
  void FCvtSW(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtDW(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtSWu(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtDWu(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtSL(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtDL(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtSLu(FRegister rd, XRegister rs1, FPRoundingMode frm);
  void FCvtDLu(FRegister rd, XRegister rs1, FPRoundingMode frm);

  // FP conversion instruction helpers passing the default rounding mode.
  void FCvtWS(XRegister rd, FRegister rs1) { FCvtWS(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtWD(XRegister rd, FRegister rs1) { FCvtWD(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtWuS(XRegister rd, FRegister rs1) { FCvtWuS(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtWuD(XRegister rd, FRegister rs1) { FCvtWuD(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtLS(XRegister rd, FRegister rs1) { FCvtLS(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtLD(XRegister rd, FRegister rs1) { FCvtLD(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtLuS(XRegister rd, FRegister rs1) { FCvtLuS(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtLuD(XRegister rd, FRegister rs1) { FCvtLuD(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtSW(FRegister rd, XRegister rs1) { FCvtSW(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtDW(FRegister rd, XRegister rs1) { FCvtDW(rd, rs1, FPRoundingMode::kIgnored); }
  void FCvtSWu(FRegister rd, XRegister rs1) { FCvtSWu(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtDWu(FRegister rd, XRegister rs1) { FCvtDWu(rd, rs1, FPRoundingMode::kIgnored); }
  void FCvtSL(FRegister rd, XRegister rs1) { FCvtSL(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtDL(FRegister rd, XRegister rs1) { FCvtDL(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtSLu(FRegister rd, XRegister rs1) { FCvtSLu(rd, rs1, FPRoundingMode::kDefault); }
  void FCvtDLu(FRegister rd, XRegister rs1) { FCvtDLu(rd, rs1, FPRoundingMode::kDefault); }

  // FP move instructions (RV32F+RV32D): opcode = 0x53, funct3 = 0x0, funct7 = 0b111X00D
  void FMvXW(XRegister rd, FRegister rs1);
  void FMvXD(XRegister rd, FRegister rs1);
  void FMvWX(FRegister rd, XRegister rs1);
  void FMvDX(FRegister rd, XRegister rs1);

  // FP classify instructions (RV32F+RV32D): opcode = 0x53, funct3 = 0x1, funct7 = 0b111X00D
  void FClassS(XRegister rd, FRegister rs1);
  void FClassD(XRegister rd, FRegister rs1);

  // "C" Standard Extension, Compresseed Instructions
  void CLwsp(XRegister rd, int32_t offset);
  void CLdsp(XRegister rd, int32_t offset);
  void CFLdsp(FRegister rd, int32_t offset);
  void CSwsp(XRegister rs2, int32_t offset);
  void CSdsp(XRegister rs2, int32_t offset);
  void CFSdsp(FRegister rs2, int32_t offset);

  void CLw(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CLd(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CFLd(FRegister rd_s, XRegister rs1_s, int32_t offset);
  void CSw(XRegister rs2_s, XRegister rs1_s, int32_t offset);
  void CSd(XRegister rs2_s, XRegister rs1_s, int32_t offset);
  void CFSd(FRegister rs2_s, XRegister rs1_s, int32_t offset);

  void CLi(XRegister rd, int32_t imm);
  void CLui(XRegister rd, uint32_t nzimm6);
  void CAddi(XRegister rd, int32_t nzimm);
  void CAddiw(XRegister rd, int32_t imm);
  void CAddi16Sp(int32_t nzimm);
  void CAddi4Spn(XRegister rd_s, uint32_t nzuimm);
  void CSlli(XRegister rd, int32_t shamt);
  void CSrli(XRegister rd_s, int32_t shamt);
  void CSrai(XRegister rd_s, int32_t shamt);
  void CAndi(XRegister rd_s, int32_t imm);
  void CMv(XRegister rd, XRegister rs2);
  void CAdd(XRegister rd, XRegister rs2);
  void CAnd(XRegister rd_s, XRegister rs2_s);
  void COr(XRegister rd_s, XRegister rs2_s);
  void CXor(XRegister rd_s, XRegister rs2_s);
  void CSub(XRegister rd_s, XRegister rs2_s);
  void CAddw(XRegister rd_s, XRegister rs2_s);
  void CSubw(XRegister rd_s, XRegister rs2_s);

  // "Zcb" Standard Extension, part of "C", opcode = 0b00, 0b01, funct3 = 0b100.
  void CLbu(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CLhu(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CLh(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CSb(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CSh(XRegister rd_s, XRegister rs1_s, int32_t offset);
  void CZextB(XRegister rd_rs1_s);
  void CSextB(XRegister rd_rs1_s);
  void CZextH(XRegister rd_rs1_s);
  void CSextH(XRegister rd_rs1_s);
  void CZextW(XRegister rd_rs1_s);
  void CNot(XRegister rd_rs1_s);
  void CMul(XRegister rd_s, XRegister rs2_s);
  // "Zcb" Standard Extension End; resume "C" Standard Extension.
  // TODO(riscv64): Reorder "Zcb" after remaining "C" instructions.

  void CJ(int32_t offset);
  void CJr(XRegister rs1);
  void CJalr(XRegister rs1);
  void CBeqz(XRegister rs1_s, int32_t offset);
  void CBnez(XRegister rs1_s, int32_t offset);

  void CEbreak();
  void CNop();
  void CUnimp();

  // "Zba" Standard Extension, opcode = 0x1b, 0x33 or 0x3b, funct3 and funct7 varies.
  void AddUw(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh1Add(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh1AddUw(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh2Add(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh2AddUw(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh3Add(XRegister rd, XRegister rs1, XRegister rs2);
  void Sh3AddUw(XRegister rd, XRegister rs1, XRegister rs2);
  void SlliUw(XRegister rd, XRegister rs1, int32_t shamt);

  // "Zbb" Standard Extension, opcode = 0x13, 0x1b, 0x33 or 0x3b, funct3 and funct7 varies.
  // Note: 32-bit sext.b, sext.h and zext.h from the Zbb extension are explicitly
  // prefixed with "Zbb" to differentiate them from the utility macros.
  void Andn(XRegister rd, XRegister rs1, XRegister rs2);
  void Orn(XRegister rd, XRegister rs1, XRegister rs2);
  void Xnor(XRegister rd, XRegister rs1, XRegister rs2);
  void Clz(XRegister rd, XRegister rs1);
  void Clzw(XRegister rd, XRegister rs1);
  void Ctz(XRegister rd, XRegister rs1);
  void Ctzw(XRegister rd, XRegister rs1);
  void Cpop(XRegister rd, XRegister rs1);
  void Cpopw(XRegister rd, XRegister rs1);
  void Min(XRegister rd, XRegister rs1, XRegister rs2);
  void Minu(XRegister rd, XRegister rs1, XRegister rs2);
  void Max(XRegister rd, XRegister rs1, XRegister rs2);
  void Maxu(XRegister rd, XRegister rs1, XRegister rs2);
  void Rol(XRegister rd, XRegister rs1, XRegister rs2);
  void Rolw(XRegister rd, XRegister rs1, XRegister rs2);
  void Ror(XRegister rd, XRegister rs1, XRegister rs2);
  void Rorw(XRegister rd, XRegister rs1, XRegister rs2);
  void Rori(XRegister rd, XRegister rs1, int32_t shamt);
  void Roriw(XRegister rd, XRegister rs1, int32_t shamt);
  void OrcB(XRegister rd, XRegister rs1);
  void Rev8(XRegister rd, XRegister rs1);
  void ZbbSextB(XRegister rd, XRegister rs1);
  void ZbbSextH(XRegister rd, XRegister rs1);
  void ZbbZextH(XRegister rd, XRegister rs1);

  ////////////////////////////// RISC-V Vector Instructions  START ///////////////////////////////
  enum class LengthMultiplier : uint32_t {
    kM1Over8 = 0b101,
    kM1Over4 = 0b110,
    kM1Over2 = 0b111,
    kM1 = 0b000,
    kM2 = 0b001,
    kM4 = 0b010,
    kM8 = 0b011,

    kReserved1 = 0b100,
  };

  enum class SelectedElementWidth : uint32_t {
    kE8 = 0b000,
    kE16 = 0b001,
    kE32 = 0b010,
    kE64 = 0b011,

    kReserved1 = 0b100,
    kReserved2 = 0b101,
    kReserved3 = 0b110,
    kReserved4 = 0b111,
  };

  enum class VectorMaskAgnostic : uint32_t {
    kUndisturbed = 0,
    kAgnostic = 1,
  };

  enum class VectorTailAgnostic : uint32_t {
    kUndisturbed = 0,
    kAgnostic = 1,
  };

  enum class VM : uint32_t {  // Vector mask
    kV0_t = 0b0,
    kUnmasked = 0b1
  };

  // Vector Conguration-Setting Instructions, opcode = 0x57, funct3 = 0x3
  void VSetvli(XRegister rd, XRegister rs1, uint32_t vtypei);
  void VSetivli(XRegister rd, uint32_t uimm, uint32_t vtypei);
  void VSetvl(XRegister rd, XRegister rs1, XRegister rs2);

  static uint32_t VTypeiValue(VectorMaskAgnostic vma,
                              VectorTailAgnostic vta,
                              SelectedElementWidth sew,
                              LengthMultiplier lmul) {
    return static_cast<uint32_t>(vma) << 7 | static_cast<uint32_t>(vta) << 6 |
           static_cast<uint32_t>(sew) << 3 | static_cast<uint32_t>(lmul);
  }

  // Vector Unit-Stride Load/Store Instructions
  void VLe8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLe16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLe32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLe64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLm(VRegister vd, XRegister rs1);

  void VSe8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSe16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSe32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSe64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSm(VRegister vs3, XRegister rs1);

  // Vector unit-stride fault-only-first Instructions
  void VLe8ff(VRegister vd, XRegister rs1);
  void VLe16ff(VRegister vd, XRegister rs1);
  void VLe32ff(VRegister vd, XRegister rs1);
  void VLe64ff(VRegister vd, XRegister rs1);

  // Vector Strided Load/Store Instructions
  void VLse8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLse16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLse32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLse64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);

  void VSse8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSse16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSse32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSse64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);

  // Vector Indexed Load/Store Instructions
  void VLoxei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  void VLuxei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  void VSoxei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  void VSuxei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector Segment Load/Store

  // Vector Unit-Stride Segment Loads/Stores

  void VLseg2e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e8(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e16(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e32(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e64(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);

  void VSseg2e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg2e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg2e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg2e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg3e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg3e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg3e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg3e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg4e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg4e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg4e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg4e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg5e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg5e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg5e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg5e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg6e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg6e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg6e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg6e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg7e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg7e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg7e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg7e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg8e8(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg8e16(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg8e32(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);
  void VSseg8e64(VRegister vs3, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector Unit-Stride Fault-only-First Segment Loads

  void VLseg2e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg2e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg3e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg4e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg5e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg6e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg7e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e8ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e16ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e32ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);
  void VLseg8e64ff(VRegister vd, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector Strided Segment Loads/Stores

  void VLsseg2e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg2e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg2e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg2e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg3e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg3e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg3e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg3e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg4e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg4e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg4e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg4e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg5e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg5e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg5e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg5e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg6e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg6e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg6e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg6e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg7e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg7e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg7e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg7e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg8e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg8e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg8e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VLsseg8e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);

  void VSsseg2e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg2e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg2e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg2e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg3e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg3e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg3e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg3e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg4e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg4e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg4e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg4e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg5e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg5e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg5e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg5e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg6e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg6e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg6e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg6e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg7e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg7e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg7e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg7e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg8e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg8e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg8e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);
  void VSsseg8e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm = VM::kUnmasked);

  // Vector Indexed-unordered Segment Loads/Stores

  void VLuxseg2ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg2ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg2ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg2ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg3ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg3ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg3ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg3ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg4ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg4ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg4ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg4ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg5ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg5ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg5ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg5ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg6ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg6ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg6ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg6ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg7ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg7ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg7ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg7ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg8ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg8ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg8ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLuxseg8ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  void VSuxseg2ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg2ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg2ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg2ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg3ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg3ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg3ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg3ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg4ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg4ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg4ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg4ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg5ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg5ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg5ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg5ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg6ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg6ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg6ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg6ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg7ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg7ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg7ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg7ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg8ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg8ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg8ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSuxseg8ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector Indexed-ordered Segment Loads/Stores

  void VLoxseg2ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg2ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg2ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg2ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg3ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg3ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg3ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg3ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg4ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg4ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg4ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg4ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg5ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg5ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg5ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg5ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg6ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg6ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg6ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg6ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg7ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg7ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg7ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg7ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg8ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg8ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg8ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VLoxseg8ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  void VSoxseg2ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg2ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg2ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg2ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg3ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg3ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg3ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg3ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg4ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg4ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg4ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg4ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg5ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg5ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg5ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg5ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg6ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg6ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg6ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg6ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg7ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg7ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg7ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg7ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg8ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg8ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg8ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VSoxseg8ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector Whole Register Load/Store Instructions

  void VL1re8(VRegister vd, XRegister rs1);
  void VL1re16(VRegister vd, XRegister rs1);
  void VL1re32(VRegister vd, XRegister rs1);
  void VL1re64(VRegister vd, XRegister rs1);

  void VL2re8(VRegister vd, XRegister rs1);
  void VL2re16(VRegister vd, XRegister rs1);
  void VL2re32(VRegister vd, XRegister rs1);
  void VL2re64(VRegister vd, XRegister rs1);

  void VL4re8(VRegister vd, XRegister rs1);
  void VL4re16(VRegister vd, XRegister rs1);
  void VL4re32(VRegister vd, XRegister rs1);
  void VL4re64(VRegister vd, XRegister rs1);

  void VL8re8(VRegister vd, XRegister rs1);
  void VL8re16(VRegister vd, XRegister rs1);
  void VL8re32(VRegister vd, XRegister rs1);
  void VL8re64(VRegister vd, XRegister rs1);

  void VL1r(VRegister vd, XRegister rs1);  // Pseudoinstruction equal to VL1re8
  void VL2r(VRegister vd, XRegister rs1);  // Pseudoinstruction equal to VL2re8
  void VL4r(VRegister vd, XRegister rs1);  // Pseudoinstruction equal to VL4re8
  void VL8r(VRegister vd, XRegister rs1);  // Pseudoinstruction equal to VL8re8

  void VS1r(VRegister vs3, XRegister rs1);  // Store {vs3} to address in a1
  void VS2r(VRegister vs3, XRegister rs1);  // Store {vs3}-{vs3 + 1} to address in a1
  void VS4r(VRegister vs3, XRegister rs1);  // Store {vs3}-{vs3 + 3} to address in a1
  void VS8r(VRegister vs3, XRegister rs1);  // Store {vs3}-{vs3 + 7} to address in a1

  // Vector Arithmetic Instruction

  // Vector vadd instructions, funct6 = 0b000000
  void VAdd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAdd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VAdd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vsub instructions, funct6 = 0b000010
  void VSub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vrsub instructions, funct6 = 0b000011
  void VRsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VRsub_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VRsub_vi
  void VNeg_v(VRegister vd, VRegister vs2);

  // Vector vminu instructions, funct6 = 0b000100
  void VMinu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMinu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmin instructions, funct6 = 0b000101
  void VMin_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMin_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmaxu instructions, funct6 = 0b000110
  void VMaxu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMaxu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmax instructions, funct6 = 0b000111
  void VMax_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMax_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vand instructions, funct6 = 0b001001
  void VAnd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAnd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VAnd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vor instructions, funct6 = 0b001010
  void VOr_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VOr_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VOr_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vxor instructions, funct6 = 0b001011
  void VXor_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VXor_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VXor_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VXor_vi
  void VNot_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vrgather instructions, funct6 = 0b001100
  void VRgather_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VRgather_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VRgather_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vslideup instructions, funct6 = 0b001110
  void VSlideup_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSlideup_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vrgatherei16 instructions, funct6 = 0b001110
  void VRgatherei16_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vslidedown instructions, funct6 = 0b001111
  void VSlidedown_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSlidedown_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vadc instructions, funct6 = 0b010000
  void VAdc_vvm(VRegister vd, VRegister vs2, VRegister vs1);
  void VAdc_vxm(VRegister vd, VRegister vs2, XRegister rs1);
  void VAdc_vim(VRegister vd, VRegister vs2, int32_t imm5);

  // Vector vmadc instructions, funct6 = 0b010001
  void VMadc_vvm(VRegister vd, VRegister vs2, VRegister vs1);
  void VMadc_vxm(VRegister vd, VRegister vs2, XRegister rs1);
  void VMadc_vim(VRegister vd, VRegister vs2, int32_t imm5);

  // Vector vmadc instructions, funct6 = 0b010001
  void VMadc_vv(VRegister vd, VRegister vs2, VRegister vs1);
  void VMadc_vx(VRegister vd, VRegister vs2, XRegister rs1);
  void VMadc_vi(VRegister vd, VRegister vs2, int32_t imm5);

  // Vector vsbc instructions, funct6 = 0b010010
  void VSbc_vvm(VRegister vd, VRegister vs2, VRegister vs1);
  void VSbc_vxm(VRegister vd, VRegister vs2, XRegister rs1);

  // Vector vmsbc instructions, funct6 = 0b010011
  void VMsbc_vvm(VRegister vd, VRegister vs2, VRegister vs1);
  void VMsbc_vxm(VRegister vd, VRegister vs2, XRegister rs1);
  void VMsbc_vv(VRegister vd, VRegister vs2, VRegister vs1);
  void VMsbc_vx(VRegister vd, VRegister vs2, XRegister rs1);

  // Vector vmerge instructions, funct6 = 0b010111, vm = 0
  void VMerge_vvm(VRegister vd, VRegister vs2, VRegister vs1);
  void VMerge_vxm(VRegister vd, VRegister vs2, XRegister rs1);
  void VMerge_vim(VRegister vd, VRegister vs2, int32_t imm5);

  // Vector vmv instructions, funct6 = 0b010111, vm = 1, vs2 = v0
  void VMv_vv(VRegister vd, VRegister vs1);
  void VMv_vx(VRegister vd, XRegister rs1);
  void VMv_vi(VRegister vd, int32_t imm5);

  // Vector vmseq instructions, funct6 = 0b011000
  void VMseq_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMseq_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMseq_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vmsne instructions, funct6 = 0b011001
  void VMsne_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMsne_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMsne_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vmsltu instructions, funct6 = 0b011010
  void VMsltu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMsltu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMsltu_vv
  void VMsgtu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vmslt instructions, funct6 = 0b011011
  void VMslt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMslt_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMslt_vv
  void VMsgt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vmsleu instructions, funct6 = 0b011100
  void VMsleu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMsleu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMsleu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instructions over VMsleu_*
  void VMsgeu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMsltu_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm = VM::kUnmasked);

  // Vector vmsle instructions, funct6 = 0b011101
  void VMsle_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMsle_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMsle_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instructions over VMsle_*
  void VMsge_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMslt_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm = VM::kUnmasked);

  // Vector vmsgtu instructions, funct6 = 0b011110
  void VMsgtu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMsgtu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMsgtu_vi
  void VMsgeu_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm = VM::kUnmasked);

  // Vector vmsgt instructions, funct6 = 0b011111
  void VMsgt_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VMsgt_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMsgt_vi
  void VMsge_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm = VM::kUnmasked);

  // Vector vsaddu instructions, funct6 = 0b100000
  void VSaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSaddu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vsadd instructions, funct6 = 0b100001
  void VSadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSadd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm = VM::kUnmasked);

  // Vector vssubu instructions, funct6 = 0b100010
  void VSsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vssub instructions, funct6 = 0b100011
  void VSsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vsll instructions, funct6 = 0b100101
  void VSll_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSll_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSll_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vsmul instructions, funct6 = 0b100111
  void VSmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSmul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmv<nr>r.v instructions, funct6 = 0b100111
  void Vmv1r_v(VRegister vd, VRegister vs2);
  void Vmv2r_v(VRegister vd, VRegister vs2);
  void Vmv4r_v(VRegister vd, VRegister vs2);
  void Vmv8r_v(VRegister vd, VRegister vs2);

  // Vector vsrl instructions, funct6 = 0b101000
  void VSrl_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSrl_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSrl_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vsra instructions, funct6 = 0b101001
  void VSra_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSra_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSra_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vssrl instructions, funct6 = 0b101010
  void VSsrl_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSsrl_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSsrl_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vssra instructions, funct6 = 0b101011
  void VSsra_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VSsra_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VSsra_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vnsrl instructions, funct6 = 0b101100
  void VNsrl_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VNsrl_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VNsrl_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VNsrl_wx
  void VNcvt_x_x_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vnsra instructions, funct6 = 0b101101
  void VNsra_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VNsra_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VNsra_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vnclipu instructions, funct6 = 0b101110
  void VNclipu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VNclipu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VNclipu_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vnclip instructions, funct6 = 0b101111
  void VNclip_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VNclip_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);
  void VNclip_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm = VM::kUnmasked);

  // Vector vwredsumu instructions, funct6 = 0b110000
  void VWredsumu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vwredsum instructions, funct6 = 0b110001
  void VWredsum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredsum instructions, funct6 = 0b000000
  void VRedsum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredand instructions, funct6 = 0b000001
  void VRedand_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredor instructions, funct6 = 0b000010
  void VRedor_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredxor instructions, funct6 = 0b000011
  void VRedxor_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredminu instructions, funct6 = 0b000100
  void VRedminu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredmin instructions, funct6 = 0b000101
  void VRedmin_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredmaxu instructions, funct6 = 0b000110
  void VRedmaxu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vredmax instructions, funct6 = 0b000111
  void VRedmax_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vaaddu instructions, funct6 = 0b001000
  void VAaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vaadd instructions, funct6 = 0b001001
  void VAadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vasubu instructions, funct6 = 0b001010
  void VAsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vasub instructions, funct6 = 0b001011
  void VAsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VAsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vslide1up instructions, funct6 = 0b001110
  void VSlide1up_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vslide1down instructions, funct6 = 0b001111
  void VSlide1down_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vcompress instructions, funct6 = 0b010111
  void VCompress_vm(VRegister vd, VRegister vs2, VRegister vs1);

  // Vector vmandn instructions, funct6 = 0b011000
  void VMandn_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Vector vmand instructions, funct6 = 0b011001
  void VMand_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Pseudo-instruction over VMand_mm
  void VMmv_m(VRegister vd, VRegister vs2);

  // Vector vmor instructions, funct6 = 0b011010
  void VMor_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Vector vmxor instructions, funct6 = 0b011011
  void VMxor_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Pseudo-instruction over VMxor_mm
  void VMclr_m(VRegister vd);

  // Vector vmorn instructions, funct6 = 0b011100
  void VMorn_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Vector vmnand instructions, funct6 = 0b011101
  void VMnand_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Pseudo-instruction over VMnand_mm
  void VMnot_m(VRegister vd, VRegister vs2);

  // Vector vmnor instructions, funct6 = 0b011110
  void VMnor_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Vector vmxnor instructions, funct6 = 0b011111
  void VMxnor_mm(VRegister vd, VRegister vs2, VRegister vs1);

  // Pseudo-instruction over VMxnor_mm
  void VMset_m(VRegister vd);

  // Vector vdivu instructions, funct6 = 0b100000
  void VDivu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VDivu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vdiv instructions, funct6 = 0b100001
  void VDiv_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VDiv_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vremu instructions, funct6 = 0b100010
  void VRemu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VRemu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vrem instructions, funct6 = 0b100011
  void VRem_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VRem_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmulhu instructions, funct6 = 0b100100
  void VMulhu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMulhu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmul instructions, funct6 = 0b100101
  void VMul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmulhsu instructions, funct6 = 0b100110
  void VMulhsu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMulhsu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmulh instructions, funct6 = 0b100111
  void VMulh_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMulh_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vmadd instructions, funct6 = 0b101001
  void VMadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VMadd_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vnmsub instructions, funct6 = 0b101011
  void VNmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VNmsub_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vmacc instructions, funct6 = 0b101101
  void VMacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VMacc_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vnmsac instructions, funct6 = 0b101111
  void VNmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VNmsac_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vwaddu instructions, funct6 = 0b110000
  void VWaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VWaddu_vx
  void VWcvtu_x_x_v(VRegister vd, VRegister vs, VM vm = VM::kUnmasked);

  // Vector vwadd instructions, funct6 = 0b110001
  void VWadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VWadd_vx
  void VWcvt_x_x_v(VRegister vd, VRegister vs, VM vm = VM::kUnmasked);

  // Vector vwsubu instructions, funct6 = 0b110010
  void VWsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwsub instructions, funct6 = 0b110011
  void VWsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwaddu.w instructions, funct6 = 0b110100
  void VWaddu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWaddu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwadd.w instructions, funct6 = 0b110101
  void VWadd_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWadd_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwsubu.w instructions, funct6 = 0b110110
  void VWsubu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWsubu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwsub.w instructions, funct6 = 0b110111
  void VWsub_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWsub_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwmulu instructions, funct6 = 0b111000
  void VWmulu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWmulu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwmulsu instructions, funct6 = 0b111010
  void VWmulsu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWmulsu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwmul instructions, funct6 = 0b111011
  void VWmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VWmul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm = VM::kUnmasked);

  // Vector vwmaccu instructions, funct6 = 0b111100
  void VWmaccu_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VWmaccu_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vwmacc instructions, funct6 = 0b111101
  void VWmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VWmacc_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vwmaccus instructions, funct6 = 0b111110
  void VWmaccus_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vwmaccsu instructions, funct6 = 0b111111
  void VWmaccsu_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VWmaccsu_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfadd instructions, funct6 = 0b000000
  void VFadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFadd_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfredusum instructions, funct6 = 0b000001
  void VFredusum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfsub instructions, funct6 = 0b000010
  void VFsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfredosum instructions, funct6 = 0b000011
  void VFredosum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfmin instructions, funct6 = 0b000100
  void VFmin_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFmin_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfredmin instructions, funct6 = 0b000101
  void VFredmin_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfmax instructions, funct6 = 0b000110
  void VFmax_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFmax_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfredmax instructions, funct6 = 0b000111
  void VFredmax_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfsgnj instructions, funct6 = 0b001000
  void VFsgnj_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFsgnj_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfsgnjn instructions, funct6 = 0b001001
  void VFsgnjn_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFsgnjn_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VFsgnjn_vv
  void VFneg_v(VRegister vd, VRegister vs);

  // Vector vfsgnjx instructions, funct6 = 0b001010
  void VFsgnjx_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFsgnjx_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VFsgnjx_vv
  void VFabs_v(VRegister vd, VRegister vs);

  // Vector vfslide1up instructions, funct6 = 0b001110
  void VFslide1up_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfslide1down instructions, funct6 = 0b001111
  void VFslide1down_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfmerge/vfmv instructions, funct6 = 0b010111
  void VFmerge_vfm(VRegister vd, VRegister vs2, FRegister fs1);
  void VFmv_v_f(VRegister vd, FRegister fs1);

  // Vector vmfeq instructions, funct6 = 0b011000
  void VMfeq_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMfeq_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vmfle instructions, funct6 = 0b011001
  void VMfle_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMfle_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMfle_vv
  void VMfge_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vmflt instructions, funct6 = 0b011011
  void VMflt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMflt_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Pseudo-instruction over VMflt_vv
  void VMfgt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vmfne instructions, funct6 = 0b011100
  void VMfne_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VMfne_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vmfgt instructions, funct6 = 0b011101
  void VMfgt_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vmfge instructions, funct6 = 0b011111
  void VMfge_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfdiv instructions, funct6 = 0b100000
  void VFdiv_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFdiv_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfrdiv instructions, funct6 = 0b100001
  void VFrdiv_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfmul instructions, funct6 = 0b100100
  void VFmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFmul_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfrsub instructions, funct6 = 0b100111
  void VFrsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfmadd instructions, funct6 = 0b101000
  void VFmadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFmadd_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfnmadd instructions, funct6 = 0b101001
  void VFnmadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFnmadd_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfmsub instructions, funct6 = 0b101010
  void VFmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFmsub_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfnmsub instructions, funct6 = 0b101011
  void VFnmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFnmsub_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfmacc instructions, funct6 = 0b101100
  void VFmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfnmacc instructions, funct6 = 0b101101
  void VFnmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFnmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfmsac instructions, funct6 = 0b101110
  void VFmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfnmsac instructions, funct6 = 0b101111
  void VFnmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFnmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfwadd instructions, funct6 = 0b110000
  void VFwadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFwadd_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfwredusum instructions, funct6 = 0b110001
  void VFwredusum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfwsub instructions, funct6 = 0b110010
  void VFwsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFwsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfwredosum instructions, funct6 = 0b110011
  void VFwredosum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);

  // Vector vfwadd.w instructions, funct6 = 0b110100
  void VFwadd_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFwadd_wf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfwsub.w instructions, funct6 = 0b110110
  void VFwsub_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFwsub_wf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfwmul instructions, funct6 = 0b111000
  void VFwmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm = VM::kUnmasked);
  void VFwmul_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm = VM::kUnmasked);

  // Vector vfwmacc instructions, funct6 = 0b111100
  void VFwmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfwnmacc instructions, funct6 = 0b111101
  void VFwnmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwnmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfwmsac instructions, funct6 = 0b111110
  void VFwmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector vfwnmsac instructions, funct6 = 0b111111
  void VFwnmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwnmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector VRXUNARY0 kind instructions, funct6 = 0b010000
  void VMv_s_x(VRegister vd, XRegister rs1);

  // Vector VWXUNARY0 kind instructions, funct6 = 0b010000
  void VMv_x_s(XRegister rd, VRegister vs2);
  void VCpop_m(XRegister rd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFirst_m(XRegister rd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector VXUNARY0 kind instructions, funct6 = 0b010010
  void VZext_vf8(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VSext_vf8(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VZext_vf4(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VSext_vf4(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VZext_vf2(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VSext_vf2(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector VRFUNARY0 kind instructions, funct6 = 0b010000
  void VFmv_s_f(VRegister vd, FRegister fs1);

  // Vector VWFUNARY0 kind instructions, funct6 = 0b010000
  void VFmv_f_s(FRegister fd, VRegister vs2);

  // Vector VFUNARY0 kind instructions, funct6 = 0b010010
  void VFcvt_xu_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFcvt_x_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFcvt_f_xu_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFcvt_f_x_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFcvt_rtz_xu_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFcvt_rtz_x_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_xu_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_x_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_f_xu_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_f_x_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_f_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_rtz_xu_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFwcvt_rtz_x_f_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_xu_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_x_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_f_xu_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_f_x_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_f_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_rod_f_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_rtz_xu_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFncvt_rtz_x_f_w(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector VFUNARY1 kind instructions, funct6 = 0b010011
  void VFsqrt_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFrsqrt7_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFrec7_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VFclass_v(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);

  // Vector VMUNARY0 kind instructions, funct6 = 0b010100
  void VMsbf_m(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VMsof_m(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VMsif_m(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VIota_m(VRegister vd, VRegister vs2, VM vm = VM::kUnmasked);
  void VId_v(VRegister vd, VM vm = VM::kUnmasked);

  ////////////////////////////// RISC-V Vector Instructions  END //////////////////////////////

  ////////////////////////////// RV64 MACRO Instructions  START ///////////////////////////////
  // These pseudo instructions are from "RISC-V Assembly Programmer's Manual".

  void Nop();
  void Li(XRegister rd, int64_t imm);
  void Mv(XRegister rd, XRegister rs);
  void Not(XRegister rd, XRegister rs);
  void Neg(XRegister rd, XRegister rs);
  void NegW(XRegister rd, XRegister rs);
  void SextB(XRegister rd, XRegister rs);
  void SextH(XRegister rd, XRegister rs);
  void SextW(XRegister rd, XRegister rs);
  void ZextB(XRegister rd, XRegister rs);
  void ZextH(XRegister rd, XRegister rs);
  void ZextW(XRegister rd, XRegister rs);
  void Seqz(XRegister rd, XRegister rs);
  void Snez(XRegister rd, XRegister rs);
  void Sltz(XRegister rd, XRegister rs);
  void Sgtz(XRegister rd, XRegister rs);
  void FMvS(FRegister rd, FRegister rs);
  void FAbsS(FRegister rd, FRegister rs);
  void FNegS(FRegister rd, FRegister rs);
  void FMvD(FRegister rd, FRegister rs);
  void FAbsD(FRegister rd, FRegister rs);
  void FNegD(FRegister rd, FRegister rs);

  // Branch pseudo instructions
  void Beqz(XRegister rs, int32_t offset);
  void Bnez(XRegister rs, int32_t offset);
  void Blez(XRegister rs, int32_t offset);
  void Bgez(XRegister rs, int32_t offset);
  void Bltz(XRegister rs, int32_t offset);
  void Bgtz(XRegister rs, int32_t offset);
  void Bgt(XRegister rs, XRegister rt, int32_t offset);
  void Ble(XRegister rs, XRegister rt, int32_t offset);
  void Bgtu(XRegister rs, XRegister rt, int32_t offset);
  void Bleu(XRegister rs, XRegister rt, int32_t offset);

  // Jump pseudo instructions
  void J(int32_t offset);
  void Jal(int32_t offset);
  void Jr(XRegister rs);
  void Jalr(XRegister rs);
  void Jalr(XRegister rd, XRegister rs);
  void Ret();

  // Pseudo instructions for accessing control and status registers
  void RdCycle(XRegister rd);
  void RdTime(XRegister rd);
  void RdInstret(XRegister rd);
  void Csrr(XRegister rd, uint32_t csr);
  void Csrw(uint32_t csr, XRegister rs);
  void Csrs(uint32_t csr, XRegister rs);
  void Csrc(uint32_t csr, XRegister rs);
  void Csrwi(uint32_t csr, uint32_t uimm5);
  void Csrsi(uint32_t csr, uint32_t uimm5);
  void Csrci(uint32_t csr, uint32_t uimm5);

  // Load/store macros for arbitrary 32-bit offsets.
  void Loadb(XRegister rd, XRegister rs1, int32_t offset);
  void Loadh(XRegister rd, XRegister rs1, int32_t offset);
  void Loadw(XRegister rd, XRegister rs1, int32_t offset);
  void Loadd(XRegister rd, XRegister rs1, int32_t offset);
  void Loadbu(XRegister rd, XRegister rs1, int32_t offset);
  void Loadhu(XRegister rd, XRegister rs1, int32_t offset);
  void Loadwu(XRegister rd, XRegister rs1, int32_t offset);
  void Storeb(XRegister rs2, XRegister rs1, int32_t offset);
  void Storeh(XRegister rs2, XRegister rs1, int32_t offset);
  void Storew(XRegister rs2, XRegister rs1, int32_t offset);
  void Stored(XRegister rs2, XRegister rs1, int32_t offset);
  void FLoadw(FRegister rd, XRegister rs1, int32_t offset);
  void FLoadd(FRegister rd, XRegister rs1, int32_t offset);
  void FStorew(FRegister rs2, XRegister rs1, int32_t offset);
  void FStored(FRegister rs2, XRegister rs1, int32_t offset);

  // Macros for loading constants.
  void LoadConst32(XRegister rd, int32_t value);
  void LoadConst64(XRegister rd, int64_t value);

  // Macros for adding constants.
  void AddConst32(XRegister rd, XRegister rs1, int32_t value);
  void AddConst64(XRegister rd, XRegister rs1, int64_t value);

  // Jumps and branches to a label.
  void Beqz(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Bnez(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Blez(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Bgez(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Bltz(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Bgtz(XRegister rs, Riscv64Label* label, bool is_bare = false);
  void Beq(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bne(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Ble(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bge(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Blt(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bgt(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bleu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bgeu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bltu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Bgtu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare = false);
  void Jal(XRegister rd, Riscv64Label* label, bool is_bare = false);
  void J(Riscv64Label* label, bool is_bare = false);
  void Jal(Riscv64Label* label, bool is_bare = false);

  // Literal load.
  void Loadw(XRegister rd, Literal* literal);
  void Loadwu(XRegister rd, Literal* literal);
  void Loadd(XRegister rd, Literal* literal);
  void FLoadw(FRegister rd, Literal* literal);
  void FLoadd(FRegister rd, Literal* literal);

  // Illegal instruction that triggers SIGILL.
  void Unimp();

  /////////////////////////////// RV64 MACRO Instructions END ///////////////////////////////

  void Bind(Label* label) override { Bind(down_cast<Riscv64Label*>(label)); }

  void Jump([[maybe_unused]] Label* label) override {
    UNIMPLEMENTED(FATAL) << "Do not use Jump for RISCV64";
  }

  void Jump(Riscv64Label* label) {
    J(label);
  }

  void Bind(Riscv64Label* label);

  // Load label address using PC-relative loads.
  void LoadLabelAddress(XRegister rd, Riscv64Label* label);

  // Create a new literal with a given value.
  // NOTE:Use `Identity<>` to force the template parameter to be explicitly specified.
  template <typename T>
  Literal* NewLiteral(typename Identity<T>::type value) {
    static_assert(std::is_integral<T>::value, "T must be an integral type.");
    return NewLiteral(sizeof(value), reinterpret_cast<const uint8_t*>(&value));
  }

  // Create a new literal with the given data.
  Literal* NewLiteral(size_t size, const uint8_t* data);

  // Create a jump table for the given labels that will be emitted when finalizing.
  // When the table is emitted, offsets will be relative to the location of the table.
  // The table location is determined by the location of its label (the label precedes
  // the table data) and should be loaded using LoadLabelAddress().
  JumpTable* CreateJumpTable(ArenaVector<Riscv64Label*>&& labels);

 public:
  // Emit slow paths queued during assembly, promote short branches to long if needed,
  // and emit branches.
  void FinalizeCode() override;

  template <typename Reg>
  static inline bool IsShortReg(Reg reg) {
    static_assert(std::is_same_v<Reg, XRegister> || std::is_same_v<Reg, FRegister>);
    uint32_t uv = enum_cast<uint32_t>(reg) - 8u;
    return IsUint<3>(uv);
  }

  // Returns the current location of a label.
  //
  // This function must be used instead of `Riscv64Label::GetPosition()`
  // which returns assembler's internal data instead of an actual location.
  //
  // The location can change during branch fixup in `FinalizeCode()`. Before that,
  // the location is not final and therefore not very useful to external users,
  // so they should preferably retrieve the location only after `FinalizeCode()`.
  uint32_t GetLabelLocation(const Riscv64Label* label) const;

  // Get the final position of a label after local fixup based on the old position
  // recorded before FinalizeCode().
  uint32_t GetAdjustedPosition(uint32_t old_position);

 private:
  static uint32_t ConvertExtensions(
      const Riscv64InstructionSetFeatures* instruction_set_features) {
    // The `Riscv64InstructionSetFeatures` currently does not support "Zcb",
    // only the original "C" extension. For riscv64 that means "Zca" and "Zcd".
    constexpr Riscv64ExtensionMask kCompressedExtensionsMask =
        Riscv64ExtensionBit(Riscv64Extension::kZca) | Riscv64ExtensionBit(Riscv64Extension::kZcd);
    return
        (Riscv64ExtensionBit(Riscv64Extension::kLoadStore)) |
        (Riscv64ExtensionBit(Riscv64Extension::kZifencei)) |
        (Riscv64ExtensionBit(Riscv64Extension::kM)) |
        (Riscv64ExtensionBit(Riscv64Extension::kA)) |
        (Riscv64ExtensionBit(Riscv64Extension::kZicsr)) |
        (Riscv64ExtensionBit(Riscv64Extension::kF)) |
        (Riscv64ExtensionBit(Riscv64Extension::kD)) |
        (instruction_set_features->HasZba() ? Riscv64ExtensionBit(Riscv64Extension::kZba) : 0u) |
        (instruction_set_features->HasZbb() ? Riscv64ExtensionBit(Riscv64Extension::kZbb) : 0u) |
        (instruction_set_features->HasZbs() ? Riscv64ExtensionBit(Riscv64Extension::kZbs) : 0u) |
        (instruction_set_features->HasVector() ? Riscv64ExtensionBit(Riscv64Extension::kV) : 0u) |
        (instruction_set_features->HasCompressed() ? kCompressedExtensionsMask : 0u);
  }

  void AssertExtensionsEnabled(Riscv64Extension ext) {
    DCHECK(IsExtensionEnabled(ext))
        << "ext=" << enum_cast<>(ext) << " enabled=0x" << std::hex << enabled_extensions_;
  }

  template <typename... OtherExt>
  void AssertExtensionsEnabled(Riscv64Extension ext, OtherExt... other_ext) {
    AssertExtensionsEnabled(ext);
    AssertExtensionsEnabled(other_ext...);
  }

  enum BranchCondition : uint8_t {
    kCondEQ,
    kCondNE,
    kCondLT,
    kCondGE,
    kCondLE,
    kCondGT,
    kCondLTU,
    kCondGEU,
    kCondLEU,
    kCondGTU,
    kUncond,
  };

  // Note that PC-relative literal loads are handled as pseudo branches because they need
  // to be emitted after branch relocation to use correct offsets.
  class Branch {
   public:
    enum Type : uint8_t {
      // TODO(riscv64): Support 16-bit instructions ("C" Standard Extension).

      // Short branches (can be promoted to longer).
      kCondBranch,
      kUncondBranch,
      kCall,
      // Short branches (can't be promoted to longer).
      kBareCondBranch,
      kBareUncondBranch,
      kBareCall,

      // Medium branch (can be promoted to long).
      kCondBranch21,

      // Long branches.
      kLongCondBranch,
      kLongUncondBranch,
      kLongCall,

      // Label.
      kLabel,

      // Literals.
      kLiteral,
      kLiteralUnsigned,
      kLiteralLong,
      kLiteralFloat,
      kLiteralDouble,
    };

    // Bit sizes of offsets defined as enums to minimize chance of typos.
    enum OffsetBits {
      kOffset13 = 13,
      kOffset21 = 21,
      kOffset32 = 32,
    };

    static constexpr uint32_t kUnresolved = 0xffffffff;  // Unresolved target_
    static constexpr uint32_t kMaxBranchLength = 12;  // In bytes.

    struct BranchInfo {
      // Branch length in bytes.
      uint32_t length;
      // The offset in bytes of the PC used in the (only) PC-relative instruction from
      // the start of the branch sequence. RISC-V always uses the address of the PC-relative
      // instruction as the PC, so this is essentially the offset of that instruction.
      uint32_t pc_offset;
      // How large (in bits) a PC-relative offset can be for a given type of branch.
      OffsetBits offset_size;
    };
    static const BranchInfo branch_info_[/* Type */];

    // Unconditional branch or call.
    Branch(uint32_t location, uint32_t target, XRegister rd, bool is_bare);
    // Conditional branch.
    Branch(uint32_t location,
           uint32_t target,
           BranchCondition condition,
           XRegister lhs_reg,
           XRegister rhs_reg,
           bool is_bare);
    // Label address or literal.
    Branch(uint32_t location, uint32_t target, XRegister rd, Type label_or_literal_type);
    Branch(uint32_t location, uint32_t target, FRegister rd, Type literal_type);

    // Some conditional branches with lhs = rhs are effectively NOPs, while some
    // others are effectively unconditional.
    static bool IsNop(BranchCondition condition, XRegister lhs, XRegister rhs);
    static bool IsUncond(BranchCondition condition, XRegister lhs, XRegister rhs);

    static BranchCondition OppositeCondition(BranchCondition cond);

    Type GetType() const;
    BranchCondition GetCondition() const;
    XRegister GetLeftRegister() const;
    XRegister GetRightRegister() const;
    FRegister GetFRegister() const;
    uint32_t GetTarget() const;
    uint32_t GetLocation() const;
    uint32_t GetOldLocation() const;
    uint32_t GetLength() const;
    uint32_t GetOldLength() const;
    uint32_t GetEndLocation() const;
    uint32_t GetOldEndLocation() const;
    bool IsBare() const;
    bool IsResolved() const;

    // Returns the bit size of the signed offset that the branch instruction can handle.
    OffsetBits GetOffsetSize() const;

    // Calculates the distance between two byte locations in the assembler buffer and
    // returns the number of bits needed to represent the distance as a signed integer.
    static OffsetBits GetOffsetSizeNeeded(uint32_t location, uint32_t target);

    // Resolve a branch when the target is known.
    void Resolve(uint32_t target);

    // Relocate a branch by a given delta if needed due to expansion of this or another
    // branch at a given location by this delta (just changes location_ and target_).
    void Relocate(uint32_t expand_location, uint32_t delta);

    // If necessary, updates the type by promoting a short branch to a longer branch
    // based on the branch location and target. Returns the amount (in bytes) by
    // which the branch size has increased.
    uint32_t PromoteIfNeeded();

    // Returns the offset into assembler buffer that shall be used as the base PC for
    // offset calculation. RISC-V always uses the address of the PC-relative instruction
    // as the PC, so this is essentially the location of that instruction.
    uint32_t GetOffsetLocation() const;

    // Calculates and returns the offset ready for encoding in the branch instruction(s).
    int32_t GetOffset() const;

   private:
    // Completes branch construction by determining and recording its type.
    void InitializeType(Type initial_type);
    // Helper for the above.
    void InitShortOrLong(OffsetBits ofs_size, Type short_type, Type long_type, Type longest_type);

    uint32_t old_location_;  // Offset into assembler buffer in bytes.
    uint32_t location_;      // Offset into assembler buffer in bytes.
    uint32_t target_;        // Offset into assembler buffer in bytes.

    XRegister lhs_reg_;          // Left-hand side register in conditional branches or
                                 // destination register in calls or literals.
    XRegister rhs_reg_;          // Right-hand side register in conditional branches.
    FRegister freg_;             // Destination register in FP literals.
    BranchCondition condition_;  // Condition for conditional branches.

    Type type_;      // Current type of the branch.
    Type old_type_;  // Initial type of the branch.
  };

  // Branch and literal fixup.

  void EmitBcond(BranchCondition cond, XRegister rs, XRegister rt, int32_t offset);
  void EmitBranch(Branch* branch);
  void EmitBranches();
  void EmitJumpTables();
  void EmitLiterals();

  void FinalizeLabeledBranch(Riscv64Label* label);
  void Bcond(Riscv64Label* label,
             bool is_bare,
             BranchCondition condition,
             XRegister lhs,
             XRegister rhs);
  void Buncond(Riscv64Label* label, XRegister rd, bool is_bare);
  template <typename XRegisterOrFRegister>
  void LoadLiteral(Literal* literal, XRegisterOrFRegister rd, Branch::Type literal_type);

  Branch* GetBranch(uint32_t branch_id);
  const Branch* GetBranch(uint32_t branch_id) const;

  void ReserveJumpTableSpace();
  void PromoteBranches();
  void PatchCFI();

  // Emit data (e.g. encoded instruction or immediate) to the instruction stream.
  template <typename T>
  void Emit(T value) {
    static_assert(std::is_same_v<T, uint32_t> || std::is_same_v<T, uint16_t>,
                  "Only Integer types are allowed");
    if (overwriting_) {
      // Branches to labels are emitted into their placeholders here.
      buffer_.Store<T>(overwrite_location_, value);
      overwrite_location_ += sizeof(T);
    } else {
      // Other instructions are simply appended at the end here.
      AssemblerBuffer::EnsureCapacity ensured(&buffer_);
      buffer_.Emit<T>(value);
    }
  }

  void Emit16(uint32_t value) { Emit(dchecked_integral_cast<uint16_t>(value)); }
  void Emit32(uint32_t value) { Emit(value); }

  // Adjust base register and offset if needed for load/store with a large offset.
  void AdjustBaseAndOffset(XRegister& base, int32_t& offset, ScratchRegisterScope& srs);

  // Helper templates for loads/stores with 32-bit offsets.
  template <void (Riscv64Assembler::*insn)(XRegister, XRegister, int32_t)>
  void LoadFromOffset(XRegister rd, XRegister rs1, int32_t offset);
  template <void (Riscv64Assembler::*insn)(XRegister, XRegister, int32_t)>
  void StoreToOffset(XRegister rs2, XRegister rs1, int32_t offset);
  template <void (Riscv64Assembler::*insn)(FRegister, XRegister, int32_t)>
  void FLoadFromOffset(FRegister rd, XRegister rs1, int32_t offset);
  template <void (Riscv64Assembler::*insn)(FRegister, XRegister, int32_t)>
  void FStoreToOffset(FRegister rs2, XRegister rs1, int32_t offset);

  // Implementation helper for `Li()`, `LoadConst32()` and `LoadConst64()`.
  void LoadImmediate(XRegister rd, int64_t imm, bool can_use_tmp);

  // RVV constants and helpers

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

  enum class VAIEncoding : uint32_t {
                     // ----Operands---- | Type of Scalar                | Instruction type
    kOPIVV = 0b000,  // vector-vector    | --                            | R-type
    kOPFVV = 0b001,  // vector-vector    | --                            | R-type
    kOPMVV = 0b010,  // vector-vector    | --                            | R-type
    kOPIVI = 0b011,  // vector-immediate | imm[4:0]                      | R-type
    kOPIVX = 0b100,  // vector-scalar    | GPR x register rs1            | R-type
    kOPFVF = 0b101,  // vector-scalar    | FP f register rs1             | R-type
    kOPMVX = 0b110,  // vector-scalar    | GPR x register rs1            | R-type
    kOPCFG = 0b111,  // scalars-imms     | GPR x register rs1 & rs2/imm  | R/I-type
  };

  enum class MemAddressMode : uint32_t {
    kUnitStride = 0b00,
    kIndexedUnordered = 0b01,
    kStrided = 0b10,
    kIndexedOrdered = 0b11,
  };

  enum class VectorWidth : uint32_t {
    k8 = 0b000,
    k16 = 0b101,
    k32 = 0b110,
    k64 = 0b111,

    kMask = 0b000,
    kWholeR = 0b000,
  };

  static constexpr uint32_t EncodeRVVMemF7(const Nf nf,
                                           const uint32_t mew,
                                           const MemAddressMode mop,
                                           const VM vm) {
    DCHECK(IsUint<3>(enum_cast<uint32_t>(nf)));
    DCHECK(IsUint<1>(mew));
    DCHECK(IsUint<2>(enum_cast<uint32_t>(mop)));
    DCHECK(IsUint<1>(enum_cast<uint32_t>(vm)));

    return enum_cast<uint32_t>(nf) << 4 | mew << 3 | enum_cast<uint32_t>(mop) << 1 |
           enum_cast<uint32_t>(vm);
  }

  static constexpr uint32_t EncodeRVVF7(const uint32_t funct6, const VM vm) {
    DCHECK(IsUint<6>(funct6));
    return funct6 << 1 | enum_cast<uint32_t>(vm);
  }

  template <unsigned kWidth>
  static constexpr uint32_t EncodeIntWidth(const int32_t imm) {
    DCHECK(IsInt<kWidth>(imm));
    return static_cast<uint32_t>(imm) & MaskLeastSignificant<uint32_t>(kWidth);
  }

  static constexpr uint32_t EncodeInt5(const int32_t imm) { return EncodeIntWidth<5>(imm); }
  static constexpr uint32_t EncodeInt6(const int32_t imm) { return EncodeIntWidth<6>(imm); }

  template <typename Reg>
  static constexpr uint32_t EncodeShortReg(const Reg reg) {
    DCHECK(IsShortReg(reg));
    return enum_cast<uint32_t>(reg) - 8u;
  }

  // Rearrange given offset in the way {offset[0] | offset[1]}
  static constexpr uint32_t EncodeOffset0_1(int32_t offset) {
    uint32_t u_offset = static_cast<uint32_t>(offset);
    DCHECK(IsUint<2>(u_offset));

    return u_offset >> 1 | (u_offset & 1u) << 1;
  }

  // Rearrange given offset, scaled by 4, in the way {offset[5:2] | offset[7:6]}
  static constexpr uint32_t ExtractOffset52_76(int32_t offset) {
    DCHECK(IsAligned<4>(offset)) << "Offset should be scalable by 4";

    uint32_t u_offset = static_cast<uint32_t>(offset);
    DCHECK(IsUint<6 + 2>(u_offset));

    uint32_t imm_52 = BitFieldExtract(u_offset, 2, 4);
    uint32_t imm_76 = BitFieldExtract(u_offset, 6, 2);

    return BitFieldInsert(imm_76, imm_52, 2, 4);
  }

  // Rearrange given offset, scaled by 8, in the way {offset[5:3] | offset[8:6]}
  static constexpr uint32_t ExtractOffset53_86(int32_t offset) {
    DCHECK(IsAligned<8>(offset)) << "Offset should be scalable by 8";

    uint32_t u_offset = static_cast<uint32_t>(offset);
    DCHECK(IsUint<6 + 3>(u_offset));

    uint32_t imm_53 = BitFieldExtract(u_offset, 3, 3);
    uint32_t imm_86 = BitFieldExtract(u_offset, 6, 3);

    return BitFieldInsert(imm_86, imm_53, 3, 3);
  }

  // Rearrange given offset, scaled by 4, in the way {offset[5:2] | offset[6]}
  static constexpr uint32_t ExtractOffset52_6(int32_t offset) {
    DCHECK(IsAligned<4>(offset)) << "Offset should be scalable by 4";

    uint32_t u_offset = static_cast<uint32_t>(offset);
    DCHECK(IsUint<5 + 2>(u_offset));

    uint32_t imm_52 = BitFieldExtract(u_offset, 2, 4);
    uint32_t imm_6  = BitFieldExtract(u_offset, 6, 1);

    return BitFieldInsert(imm_6, imm_52, 1, 4);
  }

  // Rearrange given offset, scaled by 8, in the way {offset[5:3], offset[7:6]}
  static constexpr uint32_t ExtractOffset53_76(int32_t offset) {
    DCHECK(IsAligned<8>(offset)) << "Offset should be scalable by 4";

    uint32_t u_offset = static_cast<uint32_t>(offset);
    DCHECK(IsUint<5 + 3>(u_offset));

    uint32_t imm_53 = BitFieldExtract(u_offset, 3, 3);
    uint32_t imm_76 = BitFieldExtract(u_offset, 6, 2);

    return BitFieldInsert(imm_76, imm_53, 2, 3);
  }

  static constexpr bool IsImmCLuiEncodable(uint32_t uimm) {
    // Instruction c.lui is odd and its immediate value is a bit tricky
    // Its value is not a full 32 bits value, but its bits [31:12]
    // (where the bit 17 marks the sign bit) shifted towards the bottom i.e. bits [19:0]
    // are the meaningful ones. Since that we want a signed non-zero 6-bit immediate to
    // keep values in the range [0, 0x1f], and the range [0xfffe0, 0xfffff] for negative values
    // since the sign bit was bit 17 (which is now bit 5 and replicated in the higher bits too)
    // Also encoding with immediate = 0 is reserved
    // For more details please see 16.5 chapter is the specification

    return uimm != 0u && (IsUint<5>(uimm) || IsUint<5>(uimm - 0xfffe0u));
  }

  // Emit helpers.

  // I-type instruction:
  //
  //    31                   20 19     15 14 12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . . . . . . . . | . . . . | . . | . . . . | . . . . . . ]
  //   [        imm11:0            rs1   funct3     rd        opcode   ]
  //   -----------------------------------------------------------------
  template <typename Reg1, typename Reg2>
  void EmitI(int32_t imm12, Reg1 rs1, uint32_t funct3, Reg2 rd, uint32_t opcode) {
    DCHECK(IsInt<12>(imm12)) << imm12;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = static_cast<uint32_t>(imm12) << 20 | static_cast<uint32_t>(rs1) << 15 |
                        funct3 << 12 | static_cast<uint32_t>(rd) << 7 | opcode;
    Emit32(encoding);
  }

  // R-type instruction:
  //
  //    31         25 24     20 19     15 14 12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . . . | . . . . | . . . . | . . | . . . . | . . . . . . ]
  //   [   funct7        rs2       rs1   funct3     rd        opcode   ]
  //   -----------------------------------------------------------------
  template <typename Reg1, typename Reg2, typename Reg3>
  void EmitR(uint32_t funct7, Reg1 rs2, Reg2 rs1, uint32_t funct3, Reg3 rd, uint32_t opcode) {
    DCHECK(IsUint<7>(funct7));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = funct7 << 25 | static_cast<uint32_t>(rs2) << 20 |
                        static_cast<uint32_t>(rs1) << 15 | funct3 << 12 |
                        static_cast<uint32_t>(rd) << 7 | opcode;
    Emit32(encoding);
  }

  // R-type instruction variant for floating-point fused multiply-add/sub (F[N]MADD/ F[N]MSUB):
  //
  //    31     27  25 24     20 19     15 14 12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . | . | . . . . | . . . . | . . | . . . . | . . . . . . ]
  //   [  rs3     fmt    rs2       rs1   funct3     rd        opcode   ]
  //   -----------------------------------------------------------------
  template <typename Reg1, typename Reg2, typename Reg3, typename Reg4>
  void EmitR4(
      Reg1 rs3, uint32_t fmt, Reg2 rs2, Reg3 rs1, uint32_t funct3, Reg4 rd, uint32_t opcode) {
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs3)));
    DCHECK(IsUint<2>(fmt));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = static_cast<uint32_t>(rs3) << 27 | static_cast<uint32_t>(fmt) << 25 |
                        static_cast<uint32_t>(rs2) << 20 | static_cast<uint32_t>(rs1) << 15 |
                        static_cast<uint32_t>(funct3) << 12 | static_cast<uint32_t>(rd) << 7 |
                        opcode;
    Emit32(encoding);
  }

  // S-type instruction:
  //
  //    31         25 24     20 19     15 14 12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . . . | . . . . | . . . . | . . | . . . . | . . . . . . ]
  //   [   imm11:5       rs2       rs1   funct3   imm4:0      opcode   ]
  //   -----------------------------------------------------------------
  template <typename Reg1, typename Reg2>
  void EmitS(int32_t imm12, Reg1 rs2, Reg2 rs1, uint32_t funct3, uint32_t opcode) {
    DCHECK(IsInt<12>(imm12)) << imm12;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = (static_cast<uint32_t>(imm12) & 0xFE0) << 20 |
                        static_cast<uint32_t>(rs2) << 20 | static_cast<uint32_t>(rs1) << 15 |
                        static_cast<uint32_t>(funct3) << 12 |
                        (static_cast<uint32_t>(imm12) & 0x1F) << 7 | opcode;
    Emit32(encoding);
  }

  // I-type instruction variant for shifts (SLLI / SRLI / SRAI):
  //
  //    31       26 25       20 19     15 14 12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . . | . . . . . | . . . . | . . | . . . . | . . . . . . ]
  //   [  imm11:6  imm5:0(shamt)   rs1   funct3     rd        opcode   ]
  //   -----------------------------------------------------------------
  void EmitI6(uint32_t funct6,
              uint32_t imm6,
              XRegister rs1,
              uint32_t funct3,
              XRegister rd,
              uint32_t opcode) {
    DCHECK(IsUint<6>(funct6));
    DCHECK(IsUint<6>(imm6)) << imm6;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = funct6 << 26 | static_cast<uint32_t>(imm6) << 20 |
                        static_cast<uint32_t>(rs1) << 15 | funct3 << 12 |
                        static_cast<uint32_t>(rd) << 7 | opcode;
    Emit32(encoding);
  }

  // B-type instruction:
  //
  //   31 30       25 24     20 19     15 14 12 11    8 7 6           0
  //   -----------------------------------------------------------------
  //   [ | . . . . . | . . . . | . . . . | . . | . . . | | . . . . . . ]
  //  imm12 imm11:5      rs2       rs1   funct3 imm4:1 imm11  opcode   ]
  //   -----------------------------------------------------------------
  void EmitB(int32_t offset, XRegister rs2, XRegister rs1, uint32_t funct3, uint32_t opcode) {
    DCHECK_ALIGNED(offset, 2);
    DCHECK(IsInt<13>(offset)) << offset;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs1)));
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<7>(opcode));
    uint32_t imm12 = (static_cast<uint32_t>(offset) >> 1) & 0xfffu;
    uint32_t encoding = (imm12 & 0x800u) << (31 - 11) | (imm12 & 0x03f0u) << (25 - 4) |
                        static_cast<uint32_t>(rs2) << 20 | static_cast<uint32_t>(rs1) << 15 |
                        static_cast<uint32_t>(funct3) << 12 |
                        (imm12 & 0xfu) << 8 | (imm12 & 0x400u) >> (10 - 7) | opcode;
    Emit32(encoding);
  }

  // U-type instruction:
  //
  //    31                                   12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ . . . . . . . . . . . . . . . . . . . | . . . . | . . . . . . ]
  //   [                imm31:12                    rd        opcode   ]
  //   -----------------------------------------------------------------
  void EmitU(uint32_t imm20, XRegister rd, uint32_t opcode) {
    CHECK(IsUint<20>(imm20)) << imm20;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t encoding = imm20 << 12 | static_cast<uint32_t>(rd) << 7 | opcode;
    Emit32(encoding);
  }

  // J-type instruction:
  //
  //   31 30               21   19           12 11      7 6           0
  //   -----------------------------------------------------------------
  //   [ | . . . . . . . . . | | . . . . . . . | . . . . | . . . . . . ]
  //  imm20    imm10:1      imm11   imm19:12        rd        opcode   ]
  //   -----------------------------------------------------------------
  void EmitJ(int32_t offset, XRegister rd, uint32_t opcode) {
    DCHECK_ALIGNED(offset, 2);
    CHECK(IsInt<21>(offset)) << offset;
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd)));
    DCHECK(IsUint<7>(opcode));
    uint32_t imm20 = (static_cast<uint32_t>(offset) >> 1) & 0xfffffu;
    uint32_t encoding = (imm20 & 0x80000u) << (31 - 19) | (imm20 & 0x03ffu) << 21 |
                        (imm20 & 0x400u) << (20 - 10) | (imm20 & 0x7f800u) << (12 - 11) |
                        static_cast<uint32_t>(rd) << 7 | opcode;
    Emit32(encoding);
  }

  // Compressed Instruction Encodings

  // CR-type instruction:
  //
  //   15    12 11      7 6       2 1 0
  //   ---------------------------------
  //   [ . . . | . . . . | . . . . | . ]
  //   [ func4   rd/rs1      rs2    op ]
  //   ---------------------------------
  //
  void EmitCR(uint32_t funct4, XRegister rd_rs1, XRegister rs2, uint32_t opcode) {
    DCHECK(IsUint<4>(funct4));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd_rs1)));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<2>(opcode));

    uint32_t encoding = funct4 << 12 | static_cast<uint32_t>(rd_rs1) << 7 |
                        static_cast<uint32_t>(rs2) << 2 | opcode;
    Emit16(encoding);
  }

  // CI-type instruction:
  //
  //   15  13   11      7 6       2 1 0
  //   ---------------------------------
  //   [ . . | | . . . . | . . . . | . ]
  //   [func3 imm rd/rs1     imm    op ]
  //   ---------------------------------
  //
  template <typename Reg>
  void EmitCI(uint32_t funct3, Reg rd_rs1, uint32_t imm6, uint32_t opcode) {
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rd_rs1)));
    DCHECK(IsUint<6>(imm6));
    DCHECK(IsUint<2>(opcode));

    uint32_t immH1 = BitFieldExtract(imm6, 5, 1);
    uint32_t immL5 = BitFieldExtract(imm6, 0, 5);

    uint32_t encoding =
        funct3 << 13 | immH1 << 12 | static_cast<uint32_t>(rd_rs1) << 7 | immL5 << 2 | opcode;
    Emit16(encoding);
  }

  // CSS-type instruction:
  //
  //   15  13 12        7 6       2 1 0
  //   ---------------------------------
  //   [ . . | . . . . . | . . . . | . ]
  //   [func3     imm6      rs2     op ]
  //   ---------------------------------
  //
  template <typename Reg>
  void EmitCSS(uint32_t funct3, uint32_t offset6, Reg rs2, uint32_t opcode) {
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<6>(offset6));
    DCHECK(IsUint<5>(static_cast<uint32_t>(rs2)));
    DCHECK(IsUint<2>(opcode));

    uint32_t encoding = funct3 << 13 | offset6 << 7 | static_cast<uint32_t>(rs2) << 2 | opcode;
    Emit16(encoding);
  }

  // CIW-type instruction:
  //
  //   15  13 12            5 4   2 1 0
  //   ---------------------------------
  //   [ . . | . . . . . . . | . . | . ]
  //   [func3     imm8         rd'  op ]
  //   ---------------------------------
  //
  void EmitCIW(uint32_t funct3, uint32_t imm8, XRegister rd_s, uint32_t opcode) {
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<8>(imm8));
    DCHECK(IsShortReg(rd_s)) << rd_s;
    DCHECK(IsUint<2>(opcode));

    uint32_t encoding = funct3 << 13 | imm8 << 5 | EncodeShortReg(rd_s) << 2 | opcode;
    Emit16(encoding);
  }

  // CL/S-type instruction:
  //
  //   15  13 12  10 9  7 6 5 4   2 1 0
  //   ---------------------------------
  //   [ . . | . . | . . | . | . . | . ]
  //   [func3  imm   rs1' imm rds2' op ]
  //   ---------------------------------
  //
  template <typename Reg>
  void EmitCM(uint32_t funct3, uint32_t imm5, XRegister rs1_s, Reg rd_rs2_s, uint32_t opcode) {
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<5>(imm5));
    DCHECK(IsShortReg(rs1_s)) << rs1_s;
    DCHECK(IsShortReg(rd_rs2_s)) << rd_rs2_s;
    DCHECK(IsUint<2>(opcode));

    uint32_t immH3 = BitFieldExtract(imm5, 2, 3);
    uint32_t immL2 = BitFieldExtract(imm5, 0, 2);

    uint32_t encoding = funct3 << 13 | immH3 << 10 | EncodeShortReg(rs1_s) << 7 | immL2 << 5 |
                        EncodeShortReg(rd_rs2_s) << 2 | opcode;
    Emit16(encoding);
  }

  // CA-type instruction:
  //
  //   15         10 9  7 6 5 4   2 1 0
  //   ---------------------------------
  //   [ . . . . . | . . | . | . . | . ]
  //   [    funct6 rds1' funct2 rs2' op]
  //   ---------------------------------
  //
  void EmitCA(
      uint32_t funct6, XRegister rd_rs1_s, uint32_t funct2, uint32_t rs2_v, uint32_t opcode) {
    DCHECK(IsUint<6>(funct6));
    DCHECK(IsShortReg(rd_rs1_s)) << rd_rs1_s;
    DCHECK(IsUint<2>(funct2));
    DCHECK(IsUint<3>(rs2_v));
    DCHECK(IsUint<2>(opcode));

    uint32_t encoding =
        funct6 << 10 | EncodeShortReg(rd_rs1_s) << 7 | funct2 << 5  | rs2_v << 2 | opcode;
    Emit16(encoding);
  }

  void EmitCAReg(
      uint32_t funct6, XRegister rd_rs1_s, uint32_t funct2, XRegister rs2_s, uint32_t opcode) {
    DCHECK(IsShortReg(rs2_s)) << rs2_s;
    EmitCA(funct6, rd_rs1_s, funct2, EncodeShortReg(rs2_s), opcode);
  }

  void EmitCAImm(
      uint32_t funct6, XRegister rd_rs1_s, uint32_t funct2, uint32_t funct3, uint32_t opcode) {
    EmitCA(funct6, rd_rs1_s, funct2, funct3, opcode);
  }

  // CB-type instruction:
  //
  //   15  13 12  10 9  7 6       2 1 0
  //   ---------------------------------
  //   [ . . | . . | . . | . . . . | . ]
  //   [func3 offset rs1'   offset  op ]
  //   ---------------------------------
  //
  void EmitCB(uint32_t funct3, int32_t offset8, XRegister rd_rs1_s, uint32_t opcode) {
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<8>(offset8));
    DCHECK(IsShortReg(rd_rs1_s)) << rd_rs1_s;
    DCHECK(IsUint<2>(opcode));

    uint32_t offsetH3 = BitFieldExtract<uint32_t>(offset8, 5, 3);
    uint32_t offsetL5 = BitFieldExtract<uint32_t>(offset8, 0, 5);

    uint32_t encoding =
        funct3 << 13 | offsetH3 << 10 | EncodeShortReg(rd_rs1_s) << 7 | offsetL5 << 2 | opcode;
    Emit16(encoding);
  }

  // Wrappers for EmitCB with different imm bit permutation
  void EmitCBBranch(uint32_t funct3, int32_t offset, XRegister rs1_s, uint32_t opcode) {
    DCHECK(IsInt<9>(offset));
    DCHECK_ALIGNED(offset, 2);

    uint32_t u_offset = static_cast<uint32_t>(offset);

    // offset[8|4:3]
    uint32_t offsetH3 = (BitFieldExtract(u_offset, 8, 1) << 2) |
                         BitFieldExtract(u_offset, 3, 2);
    // offset[7:6|2:1|5]
    uint32_t offsetL5 = (BitFieldExtract(u_offset, 6, 2) << 3) |
                        (BitFieldExtract(u_offset, 1, 2) << 1) |
                         BitFieldExtract(u_offset, 5, 1);

    EmitCB(funct3, BitFieldInsert(offsetL5, offsetH3, 5, 3), rs1_s, opcode);
  }

  void EmitCBArithmetic(
      uint32_t funct3, uint32_t funct2, uint32_t imm, XRegister rd_s, uint32_t opcode) {
    uint32_t imm_5 = BitFieldExtract(imm, 5, 1);
    uint32_t immH3 = BitFieldInsert(funct2, imm_5, 2, 1);
    uint32_t immL5 = BitFieldExtract(imm, 0, 5);

    EmitCB(funct3, BitFieldInsert(immL5, immH3, 5, 3), rd_s, opcode);
  }

  // CJ-type instruction:
  //
  //   15  13 12                  2 1 0
  //   ---------------------------------
  //   [ . . | . . . . . . . . . . | . ]
  //   [func3    jump target 11     op ]
  //   ---------------------------------
  //
  void EmitCJ(uint32_t funct3, int32_t offset, uint32_t opcode) {
    DCHECK_ALIGNED(offset, 2);
    DCHECK(IsInt<12>(offset)) << offset;
    DCHECK(IsUint<3>(funct3));
    DCHECK(IsUint<2>(opcode));

    uint32_t uoffset = static_cast<uint32_t>(offset);
    // offset[11|4|9:8|10|6|7|3:1|5]
    uint32_t jumpt = (BitFieldExtract(uoffset, 11, 1) << 10) |
                     (BitFieldExtract(uoffset, 4, 1) << 9)   |
                     (BitFieldExtract(uoffset, 8, 2) << 7)   |
                     (BitFieldExtract(uoffset, 10, 1) << 6)  |
                     (BitFieldExtract(uoffset, 6, 1) << 5)   |
                     (BitFieldExtract(uoffset, 7, 1) << 4)   |
                     (BitFieldExtract(uoffset, 1, 3) << 1)   |
                      BitFieldExtract(uoffset, 5, 1);

    DCHECK(IsUint<11>(jumpt));

    uint32_t encoding = funct3 << 13 | jumpt << 2 | opcode;
    Emit16(encoding);
  }

  ArenaVector<Branch> branches_;

  // For checking that we finalize the code only once.
  bool finalized_;

  // Whether appending instructions at the end of the buffer or overwriting the existing ones.
  bool overwriting_;
  // The current overwrite location.
  uint32_t overwrite_location_;

  // Use `std::deque<>` for literal labels to allow insertions at the end
  // without invalidating pointers and references to existing elements.
  ArenaDeque<Literal> literals_;
  ArenaDeque<Literal> long_literals_;  // 64-bit literals separated for alignment reasons.

  // Jump table list.
  ArenaDeque<JumpTable> jump_tables_;

  // Data for `GetAdjustedPosition()`, see the description there.
  uint32_t last_position_adjustment_;
  uint32_t last_old_position_;
  uint32_t last_branch_id_;

  Riscv64ExtensionMask enabled_extensions_;
  uint32_t available_scratch_core_registers_;
  uint32_t available_scratch_fp_registers_;

  static constexpr uint32_t kXlen = 64;

  friend class ScopedExtensionsOverride;
  friend class ScratchRegisterScope;

  DISALLOW_COPY_AND_ASSIGN(Riscv64Assembler);
};

class ScopedExtensionsOverride {
 public:
  ScopedExtensionsOverride(Riscv64Assembler* assembler, Riscv64ExtensionMask enabled_extensions)
      : assembler_(assembler),
        old_enabled_extensions_(assembler->enabled_extensions_) {
    assembler->enabled_extensions_ = enabled_extensions;
  }

  ~ScopedExtensionsOverride() {
    assembler_->enabled_extensions_ = old_enabled_extensions_;
  }

 protected:
  static Riscv64ExtensionMask GetEnabledExtensions(Riscv64Assembler* assembler) {
    return assembler->enabled_extensions_;
  }

 private:
  Riscv64Assembler* const assembler_;
  const Riscv64ExtensionMask old_enabled_extensions_;
};

template <Riscv64ExtensionMask kMask>
class ScopedExtensionsRestriction : public ScopedExtensionsOverride {
 public:
  explicit ScopedExtensionsRestriction(Riscv64Assembler* assembler)
      : ScopedExtensionsOverride(assembler, GetEnabledExtensions(assembler) & kMask) {}
};

template <Riscv64ExtensionMask kMask>
using ScopedExtensionsExclusion = ScopedExtensionsRestriction<~kMask>;

using ScopedLrScExtensionsRestriction =
    ScopedExtensionsRestriction<kRiscv64LrScSequenceExtensionsMask>;

class ScratchRegisterScope {
 public:
  explicit ScratchRegisterScope(Riscv64Assembler* assembler)
      : assembler_(assembler),
        old_available_scratch_core_registers_(assembler->available_scratch_core_registers_),
        old_available_scratch_fp_registers_(assembler->available_scratch_fp_registers_) {}

  ~ScratchRegisterScope() {
    assembler_->available_scratch_core_registers_ = old_available_scratch_core_registers_;
    assembler_->available_scratch_fp_registers_ = old_available_scratch_fp_registers_;
  }

  // Alocate a scratch `XRegister`. There must be an available register to allocate.
  XRegister AllocateXRegister() {
    CHECK_NE(assembler_->available_scratch_core_registers_, 0u);
    // Allocate the highest available scratch register (prefer TMP(T6) over TMP2(T5)).
    uint32_t reg_num = (BitSizeOf(assembler_->available_scratch_core_registers_) - 1u) -
                       CLZ(assembler_->available_scratch_core_registers_);
    assembler_->available_scratch_core_registers_ &= ~(1u << reg_num);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfXRegisters));
    return enum_cast<XRegister>(reg_num);
  }

  // Free a previously unavailable core register for use as a scratch register.
  // This can be an arbitrary register, not necessarly the usual `TMP` or `TMP2`.
  void FreeXRegister(XRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfXRegisters));
    CHECK_EQ((1u << reg_num) & assembler_->available_scratch_core_registers_, 0u);
    assembler_->available_scratch_core_registers_ |= 1u << reg_num;
  }

  // The number of available scratch core registers.
  size_t AvailableXRegisters() {
    return POPCOUNT(assembler_->available_scratch_core_registers_);
  }

  // Make sure a core register is available for use as a scratch register.
  void IncludeXRegister(XRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfXRegisters));
    assembler_->available_scratch_core_registers_ |= 1u << reg_num;
  }

  // Make sure a core register is not available for use as a scratch register.
  void ExcludeXRegister(XRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfXRegisters));
    assembler_->available_scratch_core_registers_ &= ~(1u << reg_num);
  }

  // Alocate a scratch `FRegister`. There must be an available register to allocate.
  FRegister AllocateFRegister() {
    CHECK_NE(assembler_->available_scratch_fp_registers_, 0u);
    // Allocate the highest available scratch register (same as for core registers).
    uint32_t reg_num = (BitSizeOf(assembler_->available_scratch_fp_registers_) - 1u) -
                       CLZ(assembler_->available_scratch_fp_registers_);
    assembler_->available_scratch_fp_registers_ &= ~(1u << reg_num);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfFRegisters));
    return enum_cast<FRegister>(reg_num);
  }

  // Free a previously unavailable FP register for use as a scratch register.
  // This can be an arbitrary register, not necessarly the usual `FTMP`.
  void FreeFRegister(FRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfFRegisters));
    CHECK_EQ((1u << reg_num) & assembler_->available_scratch_fp_registers_, 0u);
    assembler_->available_scratch_fp_registers_ |= 1u << reg_num;
  }

  // The number of available scratch FP registers.
  size_t AvailableFRegisters() {
    return POPCOUNT(assembler_->available_scratch_fp_registers_);
  }

  // Make sure an FP register is available for use as a scratch register.
  void IncludeFRegister(FRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfFRegisters));
    assembler_->available_scratch_fp_registers_ |= 1u << reg_num;
  }

  // Make sure an FP register is not available for use as a scratch register.
  void ExcludeFRegister(FRegister reg) {
    uint32_t reg_num = enum_cast<uint32_t>(reg);
    DCHECK_LT(reg_num, enum_cast<uint32_t>(kNumberOfFRegisters));
    assembler_->available_scratch_fp_registers_ &= ~(1u << reg_num);
  }

 private:
  Riscv64Assembler* const assembler_;
  const uint32_t old_available_scratch_core_registers_;
  const uint32_t old_available_scratch_fp_registers_;

  DISALLOW_COPY_AND_ASSIGN(ScratchRegisterScope);
};

}  // namespace riscv64
}  // namespace art

#endif  // ART_COMPILER_UTILS_RISCV64_ASSEMBLER_RISCV64_H_
