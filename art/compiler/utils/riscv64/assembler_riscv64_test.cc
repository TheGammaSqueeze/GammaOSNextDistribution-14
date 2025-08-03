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

#include "assembler_riscv64.h"

#include <inttypes.h>

#include <map>

#include "base/bit_utils.h"
#include "utils/assembler_test.h"

#define __ GetAssembler()->

namespace art HIDDEN {
namespace riscv64 {

constexpr Riscv64ExtensionMask kRiscv64CompressedExtensionsMask =
    Riscv64ExtensionBit(Riscv64Extension::kZca) |
    Riscv64ExtensionBit(Riscv64Extension::kZcd) |
    Riscv64ExtensionBit(Riscv64Extension::kZcb);

struct RISCV64CpuRegisterCompare {
  bool operator()(const XRegister& a, const XRegister& b) const { return a < b; }
};

class AssemblerRISCV64Test : public AssemblerTest<Riscv64Assembler,
                                                  Riscv64Label,
                                                  XRegister,
                                                  FRegister,
                                                  int32_t,
                                                  VRegister> {
 public:
  using Base =
      AssemblerTest<Riscv64Assembler, Riscv64Label, XRegister, FRegister, int32_t, VRegister>;

  AssemblerRISCV64Test() {}

 protected:
  Riscv64Assembler* CreateAssembler(ArenaAllocator* allocator) override {
    return new (allocator) Riscv64Assembler(allocator, kRiscv64AllExtensionsMask);
  }

  InstructionSet GetIsa() override { return InstructionSet::kRiscv64; }

  class ScopedMarchOverride {
   public:
    ScopedMarchOverride(AssemblerRISCV64Test* test, const std::string& march)
        : test_(test), old_override_(test->march_override_) {
      test->march_override_ = march;
    }

    ~ScopedMarchOverride() {
      test_->march_override_ = old_override_;
    }

   private:
    AssemblerRISCV64Test* const test_;
    std::optional<std::string> const old_override_;
  };

  class ScopedCSuppression {
   public:
    explicit ScopedCSuppression(AssemblerRISCV64Test* test)
        : smo_(test, "-march=rv64imafdv_zba_zbb"),
          exclusion_(test->GetAssembler()) {}

   private:
    ScopedMarchOverride smo_;
    ScopedExtensionsExclusion<kRiscv64CompressedExtensionsMask> exclusion_;
  };

  class ScopedZbaAndCSuppression {
   public:
    explicit ScopedZbaAndCSuppression(AssemblerRISCV64Test* test)
        : smo_(test, "-march=rv64imafdv_zbb"),
          exclusion_(test->GetAssembler()) {}

   private:
    static constexpr Riscv64ExtensionMask kExcludedExtensions =
        Riscv64ExtensionBit(Riscv64Extension::kZba) | kRiscv64CompressedExtensionsMask;

    ScopedMarchOverride smo_;
    ScopedExtensionsExclusion<kExcludedExtensions> exclusion_;
  };

  class ScopedZbbAndCSuppression {
   public:
    explicit ScopedZbbAndCSuppression(AssemblerRISCV64Test* test)
        : smo_(test, "-march=rv64imafdv_zba"),
          exclusion_(test->GetAssembler()) {}

   private:
    static constexpr Riscv64ExtensionMask kExcludedExtensions =
        Riscv64ExtensionBit(Riscv64Extension::kZbb) | kRiscv64CompressedExtensionsMask;

    ScopedMarchOverride smo_;
    ScopedExtensionsExclusion<kExcludedExtensions> exclusion_;
  };

  std::vector<std::string> GetAssemblerCommand() override {
    std::vector<std::string> result = Base::GetAssemblerCommand();
    if (march_override_.has_value()) {
      auto it = std::find_if(result.begin(),
                             result.end(),
                             [](const std::string& s) { return StartsWith(s, "-march="); });
      CHECK(it != result.end());
      *it = march_override_.value();
    }
    return result;
  }

  void SetUpHelpers() override {
    if (secondary_register_names_.empty()) {
      secondary_register_names_.emplace(Zero, "zero");
      secondary_register_names_.emplace(RA, "ra");
      secondary_register_names_.emplace(SP, "sp");
      secondary_register_names_.emplace(GP, "gp");
      secondary_register_names_.emplace(TP, "tp");
      secondary_register_names_.emplace(T0, "t0");
      secondary_register_names_.emplace(T1, "t1");
      secondary_register_names_.emplace(T2, "t2");
      secondary_register_names_.emplace(S0, "s0");  // s0/fp
      secondary_register_names_.emplace(S1, "s1");
      secondary_register_names_.emplace(A0, "a0");
      secondary_register_names_.emplace(A1, "a1");
      secondary_register_names_.emplace(A2, "a2");
      secondary_register_names_.emplace(A3, "a3");
      secondary_register_names_.emplace(A4, "a4");
      secondary_register_names_.emplace(A5, "a5");
      secondary_register_names_.emplace(A6, "a6");
      secondary_register_names_.emplace(A7, "a7");
      secondary_register_names_.emplace(S2, "s2");
      secondary_register_names_.emplace(S3, "s3");
      secondary_register_names_.emplace(S4, "s4");
      secondary_register_names_.emplace(S5, "s5");
      secondary_register_names_.emplace(S6, "s6");
      secondary_register_names_.emplace(S7, "s7");
      secondary_register_names_.emplace(S8, "s8");
      secondary_register_names_.emplace(S9, "s9");
      secondary_register_names_.emplace(S10, "s10");
      secondary_register_names_.emplace(S11, "s11");
      secondary_register_names_.emplace(T3, "t3");
      secondary_register_names_.emplace(T4, "t4");
      secondary_register_names_.emplace(T5, "t5");
      secondary_register_names_.emplace(T6, "t6");
    }
  }

  void TearDown() override {
    AssemblerTest::TearDown();
  }

  std::vector<Riscv64Label> GetAddresses() override {
    UNIMPLEMENTED(FATAL) << "Feature not implemented yet";
    UNREACHABLE();
  }

  ArrayRef<const XRegister> GetRegisters() override {
    static constexpr XRegister kXRegisters[] = {
        Zero,
        RA,
        SP,
        GP,
        TP,
        T0,
        T1,
        T2,
        S0,
        S1,
        A0,
        A1,
        A2,
        A3,
        A4,
        A5,
        A6,
        A7,
        S2,
        S3,
        S4,
        S5,
        S6,
        S7,
        S8,
        S9,
        S10,
        S11,
        T3,
        T4,
        T5,
        T6,
    };
    return ArrayRef<const XRegister>(kXRegisters);
  }

  ArrayRef<const XRegister> GetRegistersShort() {
    static constexpr XRegister kXRegistersShort[] = {
        S0,
        S1,
        A0,
        A1,
        A2,
        A3,
        A4,
        A5,
    };
    return ArrayRef<const XRegister>(kXRegistersShort);
  }

  ArrayRef<const FRegister> GetFPRegisters() override {
    static constexpr FRegister kFRegisters[] = {
        FT0,
        FT1,
        FT2,
        FT3,
        FT4,
        FT5,
        FT6,
        FT7,
        FS0,
        FS1,
        FA0,
        FA1,
        FA2,
        FA3,
        FA4,
        FA5,
        FA6,
        FA7,
        FS2,
        FS3,
        FS4,
        FS5,
        FS6,
        FS7,
        FS8,
        FS9,
        FS10,
        FS11,
        FT8,
        FT9,
        FT10,
        FT11,
    };
    return ArrayRef<const FRegister>(kFRegisters);
  }

  ArrayRef<const VRegister> GetVectorRegisters() override {
    static constexpr VRegister kVRegisters[] = {
        V0,  V1,  V2,  V3,  V4,  V5,  V6,  V7,  V8,  V9,  V10, V11, V12, V13, V14, V15,
        V16, V17, V18, V19, V20, V21, V22, V23, V24, V25, V26, V27, V28, V29, V30, V31
    };
    return ArrayRef<const VRegister>(kVRegisters);
  }

  ArrayRef<const FRegister> GetFPRegistersShort() {
    static constexpr FRegister kFRegistersShort[] = {
        FS0,
        FS1,
        FA0,
        FA1,
        FA2,
        FA3,
        FA4,
        FA5,
    };
    return ArrayRef<const FRegister>(kFRegistersShort);
  }

  std::string GetSecondaryRegisterName(const XRegister& reg) override {
    CHECK(secondary_register_names_.find(reg) != secondary_register_names_.end());
    return secondary_register_names_[reg];
  }

  int32_t CreateImmediate(int64_t imm_value) override {
    return dchecked_integral_cast<int32_t>(imm_value);
  }

  template <typename Emit>
  std::string RepeatInsn(size_t count, const std::string& insn, Emit&& emit) {
    std::string result;
    for (; count != 0u; --count) {
      result += insn;
      emit();
    }
    return result;
  }

  std::string EmitNops(size_t size) {
    // TODO(riscv64): Support "C" Standard Extension.
    DCHECK_ALIGNED(size, sizeof(uint32_t));
    const size_t num_nops = size / sizeof(uint32_t);
    return RepeatInsn(num_nops, "nop\n", [&]() { __ Nop(); });
  }

  template <typename EmitLoadConst>
  void TestLoadConst64(const std::string& test_name,
                       bool can_use_tmp,
                       EmitLoadConst&& emit_load_const) {
    std::string expected;
    // Test standard immediates. Unlike other instructions, `Li()` accepts an `int64_t` but
    // this is unsupported by `CreateImmediate()`, so we cannot use `RepeatRIb()` for these.
    // Note: This `CreateImmediateValuesBits()` call does not produce any values where
    // `LoadConst64()` would emit different code from `Li()`.
    for (int64_t value : CreateImmediateValuesBits(64, /*as_uint=*/ false)) {
      emit_load_const(A0, value);
      expected += "li a0, " + std::to_string(value) + "\n";
    }
    // Test various registers with a few small values.
    // (Even Zero is an accepted register even if that does not really load the requested value.)
    for (XRegister reg : GetRegisters()) {
      ScratchRegisterScope srs(GetAssembler());
      srs.ExcludeXRegister(reg);
      std::string rd = GetRegisterName(reg);
      emit_load_const(reg, -1);
      expected += "li " + rd + ", -1\n";
      emit_load_const(reg, 0);
      expected += "li " + rd + ", 0\n";
      emit_load_const(reg, 1);
      expected += "li " + rd + ", 1\n";
    }
    // Test some significant values. Some may just repeat the tests above but other values
    // show some complex patterns, even exposing a value where clang (and therefore also this
    // assembler) does not generate the shortest sequence.
    // For the following values, `LoadConst64()` emits the same code as `Li()`.
    int64_t test_values1[] = {
        // Small values, either ADDI, ADDI+SLLI, LUI, or LUI+ADDIW.
        // The ADDI+LUI is presumably used to allow shorter code for RV64C.
        -4097, -4096, -4095, -2176, -2049, -2048, -2047, -1025, -1024, -1023, -2, -1,
        0, 1, 2, 1023, 1024, 1025, 2047, 2048, 2049, 2176, 4095, 4096, 4097,
        // Just below std::numeric_limits<int32_t>::min()
        INT64_C(-0x80000001),  // LUI+ADDI
        INT64_C(-0x80000800),  // LUI+ADDI
        INT64_C(-0x80000801),  // LUI+ADDIW+SLLI+ADDI; LUI+ADDI+ADDI would be shorter.
        INT64_C(-0x80000800123),  // LUI+ADDIW+SLLI+ADDI
        INT64_C(0x0123450000000123),  // LUI+SLLI+ADDI
        INT64_C(-0x7654300000000123),  // LUI+SLLI+ADDI
        INT64_C(0x0fffffffffff0000),  // LUI+SRLI
        INT64_C(0x0ffffffffffff000),  // LUI+SRLI
        INT64_C(0x0ffffffffffff010),  // LUI+ADDIW+SRLI
        INT64_C(0x0fffffffffffff10),  // ADDI+SLLI+ADDI; LUI+ADDIW+SRLI would be same length.
        INT64_C(0x0fffffffffffff80),  // ADDI+SRLI
        INT64_C(0x0ffffffff7ffff80),  // LUI+ADDI+SRLI
        INT64_C(0x0123450000001235),  // LUI+SLLI+ADDI+SLLI+ADDI
        INT64_C(0x0123450000001234),  // LUI+SLLI+ADDI+SLLI
        INT64_C(0x0000000fff808010),  // LUI+SLLI+SRLI
        INT64_C(0x00000000fff80801),  // LUI+SLLI+SRLI
        INT64_C(0x00000000ffffffff),  // ADDI+SRLI
        INT64_C(0x00000001ffffffff),  // ADDI+SRLI
        INT64_C(0x00000003ffffffff),  // ADDI+SRLI
        INT64_C(0x00000000ffc00801),  // LUI+ADDIW+SLLI+ADDI
        INT64_C(0x00000001fffff7fe),  // ADDI+SLLI+SRLI
    };
    for (int64_t value : test_values1) {
      emit_load_const(A0, value);
      expected += "li a0, " + std::to_string(value) + "\n";
    }
    // For the following values, `LoadConst64()` emits different code than `Li()`.
    std::pair<int64_t, const char*> test_values2[] = {
        // Li:        LUI+ADDIW+SLLI+ADDI+SLLI+ADDI+SLLI+ADDI
        // LoadConst: LUI+ADDIW+LUI+ADDIW+SLLI+ADD (using TMP)
        { INT64_C(0x1234567812345678),
          "li {reg1}, 0x12345678 / 8\n"  // Trailing zero bits in high word are handled by SLLI.
          "li {reg2}, 0x12345678\n"
          "slli {reg1}, {reg1}, 32 + 3\n"
          "add {reg1}, {reg1}, {reg2}\n" },
        { INT64_C(0x1234567887654321),
          "li {reg1}, 0x12345678 + 1\n"  // One higher to compensate for negative TMP.
          "li {reg2}, 0x87654321 - 0x100000000\n"
          "slli {reg1}, {reg1}, 32\n"
          "add {reg1}, {reg1}, {reg2}\n" },
        { INT64_C(-0x1234567887654321),
          "li {reg1}, -0x12345678 - 1\n"  // High 32 bits of the constant.
          "li {reg2}, 0x100000000 - 0x87654321\n"  // Low 32 bits of the constant.
          "slli {reg1}, {reg1}, 32\n"
          "add {reg1}, {reg1}, {reg2}\n" },

        // Li:        LUI+SLLI+ADDI+SLLI+ADDI+SLLI
        // LoadConst: LUI+LUI+SLLI+ADD (using TMP)
        { INT64_C(0x1234500012345000),
          "lui {reg1}, 0x12345\n"
          "lui {reg2}, 0x12345\n"
          "slli {reg1}, {reg1}, 44 - 12\n"
          "add {reg1}, {reg1}, {reg2}\n" },
        { INT64_C(0x0123450012345000),
          "lui {reg1}, 0x12345\n"
          "lui {reg2}, 0x12345\n"
          "slli {reg1}, {reg1}, 40 - 12\n"
          "add {reg1}, {reg1}, {reg2}\n" },

        // Li:        LUI+ADDIW+SLLI+ADDI+SLLI+ADDI
        // LoadConst: LUI+LUI+ADDIW+SLLI+ADD (using TMP)
        { INT64_C(0x0001234512345678),
          "lui {reg1}, 0x12345\n"
          "li {reg2}, 0x12345678\n"
          "slli {reg1}, {reg1}, 32 - 12\n"
          "add {reg1}, {reg1}, {reg2}\n" },
        { INT64_C(0x0012345012345678),
          "lui {reg1}, 0x12345\n"
          "li {reg2}, 0x12345678\n"
          "slli {reg1}, {reg1}, 36 - 12\n"
          "add {reg1}, {reg1}, {reg2}\n" },
    };
    for (auto [value, fmt] : test_values2) {
      emit_load_const(A0, value);
      if (can_use_tmp) {
        std::string base = fmt;
        ReplaceReg(REG1_TOKEN, GetRegisterName(A0), &base);
        ReplaceReg(REG2_TOKEN, GetRegisterName(TMP), &base);
        expected += base;
      } else {
        expected += "li a0, " + std::to_string(value) + "\n";
      }
    }

    DriverStr(expected, test_name);
  }

  auto GetPrintBcond() {
    return [](const std::string& cond,
              [[maybe_unused]] const std::string& opposite_cond,
              const std::string& args,
              const std::string& target) {
      return "b" + cond + args + ", " + target + "\n";
    };
  }

  auto GetPrintBcondOppositeAndJ(const std::string& skip_label) {
    return [=]([[maybe_unused]] const std::string& cond,
               const std::string& opposite_cond,
               const std::string& args,
               const std::string& target) {
      return "b" + opposite_cond + args + ", " + skip_label + "f\n" +
             "j " + target + "\n" +
             skip_label + ":\n";
    };
  }

  auto GetPrintBcondOppositeAndTail(const std::string& skip_label, const std::string& base_label) {
    return [=]([[maybe_unused]] const std::string& cond,
               const std::string& opposite_cond,
               const std::string& args,
               const std::string& target) {
      return "b" + opposite_cond + args + ", " + skip_label + "f\n" +
             base_label + ":\n" +
             "auipc t6, %pcrel_hi(" + target + ")\n" +
             "jalr x0, %pcrel_lo(" + base_label + "b)(t6)\n" +
             skip_label + ":\n";
    };
  }

  // Helper function for basic tests that all branch conditions map to the correct opcodes,
  // whether with branch expansion (a conditional branch with opposite condition over an
  // unconditional branch) or without.
  template <typename PrintBcond>
  std::string EmitBcondForAllConditions(Riscv64Label* label,
                                        const std::string& target,
                                        PrintBcond&& print_bcond,
                                        bool is_bare) {
    XRegister rs = A0;
    __ Beqz(rs, label, is_bare);
    __ Bnez(rs, label, is_bare);
    __ Blez(rs, label, is_bare);
    __ Bgez(rs, label, is_bare);
    __ Bltz(rs, label, is_bare);
    __ Bgtz(rs, label, is_bare);
    XRegister rt = A1;
    __ Beq(rs, rt, label, is_bare);
    __ Bne(rs, rt, label, is_bare);
    __ Ble(rs, rt, label, is_bare);
    __ Bge(rs, rt, label, is_bare);
    __ Blt(rs, rt, label, is_bare);
    __ Bgt(rs, rt, label, is_bare);
    __ Bleu(rs, rt, label, is_bare);
    __ Bgeu(rs, rt, label, is_bare);
    __ Bltu(rs, rt, label, is_bare);
    __ Bgtu(rs, rt, label, is_bare);

    return
        print_bcond("eq", "ne", "z a0", target) +
        print_bcond("ne", "eq", "z a0", target) +
        print_bcond("le", "gt", "z a0", target) +
        print_bcond("ge", "lt", "z a0", target) +
        print_bcond("lt", "ge", "z a0", target) +
        print_bcond("gt", "le", "z a0", target) +
        print_bcond("eq", "ne", " a0, a1", target) +
        print_bcond("ne", "eq", " a0, a1", target) +
        print_bcond("le", "gt", " a0, a1", target) +
        print_bcond("ge", "lt", " a0, a1", target) +
        print_bcond("lt", "ge", " a0, a1", target) +
        print_bcond("gt", "le", " a0, a1", target) +
        print_bcond("leu", "gtu", " a0, a1", target) +
        print_bcond("geu", "ltu", " a0, a1", target) +
        print_bcond("ltu", "geu", " a0, a1", target) +
        print_bcond("gtu", "leu", " a0, a1", target);
  }

  // Test Bcond for forward branches with all conditions.
  // The gap must be such that either all branches expand, or none does.
  template <typename PrintBcond>
  void TestBcondForward(const std::string& test_name,
                        size_t gap_size,
                        const std::string& target_label,
                        PrintBcond&& print_bcond,
                        bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    expected += EmitBcondForAllConditions(&label, target_label + "f", print_bcond, is_bare);
    expected += EmitNops(gap_size);
    __ Bind(&label);
    expected += target_label + ":\n";
    DriverStr(expected, test_name);
  }

  // Test Bcond for backward branches with all conditions.
  // The gap must be such that either all branches expand, or none does.
  template <typename PrintBcond>
  void TestBcondBackward(const std::string& test_name,
                         size_t gap_size,
                         const std::string& target_label,
                         PrintBcond&& print_bcond,
                         bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    __ Bind(&label);
    expected += target_label + ":\n";
    expected += EmitNops(gap_size);
    expected += EmitBcondForAllConditions(&label, target_label + "b", print_bcond, is_bare);
    DriverStr(expected, test_name);
  }

  size_t MaxOffset13BackwardDistance() {
    return 4 * KB;
  }

  size_t MaxOffset13ForwardDistance() {
    // TODO(riscv64): Support "C" Standard Extension, max forward distance 4KiB - 2.
    return 4 * KB - 4;
  }

  size_t MaxOffset21BackwardDistance() {
    return 1 * MB;
  }

  size_t MaxOffset21ForwardDistance() {
    // TODO(riscv64): Support "C" Standard Extension, max forward distance 1MiB - 2.
    return 1 * MB - 4;
  }

  template <typename PrintBcond>
  void TestBeqA0A1Forward(const std::string& test_name,
                          size_t nops_size,
                          const std::string& target_label,
                          PrintBcond&& print_bcond,
                          bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    __ Beq(A0, A1, &label, is_bare);
    expected += print_bcond("eq", "ne", " a0, a1", target_label + "f");
    expected += EmitNops(nops_size);
    __ Bind(&label);
    expected += target_label + ":\n";
    DriverStr(expected, test_name);
  }

  template <typename PrintBcond>
  void TestBeqA0A1Backward(const std::string& test_name,
                           size_t nops_size,
                           const std::string& target_label,
                           PrintBcond&& print_bcond,
                           bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    __ Bind(&label);
    expected += target_label + ":\n";
    expected += EmitNops(nops_size);
    __ Beq(A0, A1, &label, is_bare);
    expected += print_bcond("eq", "ne", " a0, a1", target_label + "b");
    DriverStr(expected, test_name);
  }

  // Test a branch setup where expanding one branch causes expanding another branch
  // which causes expanding another branch, etc. The argument `cascade` determines
  // whether we push the first branch to expand, or not.
  template <typename PrintBcond>
  void TestBeqA0A1MaybeCascade(const std::string& test_name,
                               bool cascade,
                               PrintBcond&& print_bcond) {
    const size_t kNumBeqs = MaxOffset13ForwardDistance() / sizeof(uint32_t) / 2u;
    auto label_name = [](size_t i) { return  ".L" + std::to_string(i); };

    std::string expected;
    std::vector<Riscv64Label> labels(kNumBeqs);
    for (size_t i = 0; i != kNumBeqs; ++i) {
      __ Beq(A0, A1, &labels[i]);
      expected += print_bcond("eq", "ne", " a0, a1", label_name(i));
    }
    if (cascade) {
      expected += EmitNops(sizeof(uint32_t));
    }
    for (size_t i = 0; i != kNumBeqs; ++i) {
      expected += EmitNops(2 * sizeof(uint32_t));
      __ Bind(&labels[i]);
      expected += label_name(i) + ":\n";
    }
    DriverStr(expected, test_name);
  }

  auto GetPrintJalRd() {
    return [=](XRegister rd, const std::string& target) {
      std::string rd_name = GetRegisterName(rd);
      return "jal " + rd_name + ", " + target + "\n";
    };
  }

  auto GetPrintCallRd(const std::string& base_label) {
    return [=](XRegister rd, const std::string& target) {
      std::string rd_name = GetRegisterName(rd);
      std::string temp_name = (rd != Zero) ? rd_name : GetRegisterName(TMP);
      return base_label + ":\n" +
             "auipc " + temp_name + ", %pcrel_hi(" + target + ")\n" +
             "jalr " + rd_name + ", %pcrel_lo(" + base_label + "b)(" + temp_name + ")\n";
    };
  }

  template <typename PrintJalRd>
  void TestJalRdForward(const std::string& test_name,
                        size_t gap_size,
                        const std::string& label_name,
                        PrintJalRd&& print_jalrd,
                        bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    for (XRegister reg : GetRegisters()) {
      __ Jal(reg, &label, is_bare);
      expected += print_jalrd(reg, label_name + "f");
    }
    expected += EmitNops(gap_size);
    __ Bind(&label);
    expected += label_name + ":\n";
    DriverStr(expected, test_name);
  }

  template <typename PrintJalRd>
  void TestJalRdBackward(const std::string& test_name,
                         size_t gap_size,
                         const std::string& label_name,
                         PrintJalRd&& print_jalrd,
                         bool is_bare = false) {
    std::string expected;
    Riscv64Label label;
    __ Bind(&label);
    expected += label_name + ":\n";
    expected += EmitNops(gap_size);
    for (XRegister reg : GetRegisters()) {
      __ Jal(reg, &label, is_bare);
      expected += print_jalrd(reg, label_name + "b");
    }
    DriverStr(expected, test_name);
  }

  auto GetEmitJ(bool is_bare = false) {
    return [=](Riscv64Label* label) { __ J(label, is_bare); };
  }

  auto GetEmitJal() {
    return [=](Riscv64Label* label) { __ Jal(label); };
  }

  auto GetPrintJ() {
    return [=](const std::string& target) {
      return "j " + target + "\n";
    };
  }

  auto GetPrintJal() {
    return [=](const std::string& target) {
      return "jal " + target + "\n";
    };
  }

  auto GetPrintTail(const std::string& base_label) {
    return [=](const std::string& target) {
      return base_label + ":\n" +
             "auipc t6, %pcrel_hi(" + target + ")\n" +
             "jalr x0, %pcrel_lo(" + base_label + "b)(t6)\n";
    };
  }

  auto GetPrintCall(const std::string& base_label) {
    return [=](const std::string& target) {
      return base_label + ":\n" +
             "auipc ra, %pcrel_hi(" + target + ")\n" +
             "jalr ra, %pcrel_lo(" + base_label + "b)(ra)\n";
    };
  }

  template <typename EmitBuncond, typename PrintBuncond>
  void TestBuncondForward(const std::string& test_name,
                          size_t gap_size,
                          const std::string& label_name,
                          EmitBuncond&& emit_buncond,
                          PrintBuncond&& print_buncond) {
    std::string expected;
    Riscv64Label label;
    emit_buncond(&label);
    expected += print_buncond(label_name + "f");
    expected += EmitNops(gap_size);
    __ Bind(&label);
    expected += label_name + ":\n";
    DriverStr(expected, test_name);
  }

  template <typename EmitBuncond, typename PrintBuncond>
  void TestBuncondBackward(const std::string& test_name,
                           size_t gap_size,
                           const std::string& label_name,
                           EmitBuncond&& emit_buncond,
                           PrintBuncond&& print_buncond) {
    std::string expected;
    Riscv64Label label;
    __ Bind(&label);
    expected += label_name + ":\n";
    expected += EmitNops(gap_size);
    emit_buncond(&label);
    expected += print_buncond(label_name + "b");
    DriverStr(expected, test_name);
  }

  template <typename EmitOp>
  void TestAddConst(const std::string& test_name,
                    size_t bits,
                    const std::string& suffix,
                    EmitOp&& emit_op) {
    int64_t kImm12s[] = {
        0, 1, 2, 0xff, 0x100, 0x1ff, 0x200, 0x3ff, 0x400, 0x7ff,
        -1, -2, -0x100, -0x101, -0x200, -0x201, -0x400, -0x401, -0x800,
    };
    int64_t kSimplePositiveValues[] = {
        0x800, 0x801, 0xbff, 0xc00, 0xff0, 0xff7, 0xff8, 0xffb, 0xffc, 0xffd, 0xffe,
    };
    int64_t kSimpleNegativeValues[] = {
        -0x801, -0x802, -0xbff, -0xc00, -0xff0, -0xff8, -0xffc, -0xffe, -0xfff, -0x1000,
    };
    std::vector<int64_t> large_values = CreateImmediateValuesBits(bits, /*as_uint=*/ false);
    auto kept_end = std::remove_if(large_values.begin(),
                                   large_values.end(),
                                   [](int64_t value) { return IsInt<13>(value); });
    large_values.erase(kept_end, large_values.end());
    large_values.push_back(0xfff);

    std::string expected;
    for (XRegister rd : GetRegisters()) {
      std::string rd_name = GetRegisterName(rd);
      std::string addi_rd = ART_FORMAT("addi{} {}, ", suffix, rd_name);
      std::string add_rd = ART_FORMAT("add{} {}, ", suffix, rd_name);
      for (XRegister rs1 : GetRegisters()) {
        ScratchRegisterScope srs(GetAssembler());
        srs.ExcludeXRegister(rs1);
        srs.ExcludeXRegister(rd);

        std::string rs1_name = GetRegisterName(rs1);
        std::string tmp_name = GetRegisterName((rs1 != TMP) ? TMP : TMP2);
        std::string addi_tmp = ART_FORMAT("addi{} {}, ", suffix, tmp_name);

        for (int64_t imm : kImm12s) {
          emit_op(rd, rs1, imm);
          expected += ART_FORMAT("{}{}, {}\n", addi_rd, rs1_name, std::to_string(imm));
        }

        auto emit_simple_ops = [&](ArrayRef<const int64_t> imms, int64_t adjustment) {
          for (int64_t imm : imms) {
            emit_op(rd, rs1, imm);
            expected += ART_FORMAT("{}{}, {}\n", addi_tmp, rs1_name, std::to_string(adjustment));
            expected +=
                ART_FORMAT("{}{}, {}\n", addi_rd, tmp_name, std::to_string(imm - adjustment));
          }
        };
        emit_simple_ops(ArrayRef<const int64_t>(kSimplePositiveValues), 0x7ff);
        emit_simple_ops(ArrayRef<const int64_t>(kSimpleNegativeValues), -0x800);

        for (int64_t imm : large_values) {
          emit_op(rd, rs1, imm);
          expected += ART_FORMAT("li {}, {}\n", tmp_name, std::to_string(imm));
          expected += ART_FORMAT("{}{}, {}\n", add_rd, rs1_name, tmp_name);
        }
      }
    }
    DriverStr(expected, test_name);
  }

  template <typename GetTemp, typename EmitOp>
  std::string RepeatLoadStoreArbitraryOffset(const std::string& head,
                                             GetTemp&& get_temp,
                                             EmitOp&& emit_op) {
    int64_t kImm12s[] = {
        0, 1, 2, 0xff, 0x100, 0x1ff, 0x200, 0x3ff, 0x400, 0x7ff,
        -1, -2, -0x100, -0x101, -0x200, -0x201, -0x400, -0x401, -0x800,
    };
    int64_t kSimplePositiveOffsetsAlign8[] = {
        0x800, 0x801, 0xbff, 0xc00, 0xff0, 0xff4, 0xff6, 0xff7
    };
    int64_t kSimplePositiveOffsetsAlign4[] = {
        0xff8, 0xff9, 0xffa, 0xffb
    };
    int64_t kSimplePositiveOffsetsAlign2[] = {
        0xffc, 0xffd
    };
    int64_t kSimplePositiveOffsetsNoAlign[] = {
        0xffe
    };
    int64_t kSimpleNegativeOffsets[] = {
        -0x801, -0x802, -0xbff, -0xc00, -0xff0, -0xff8, -0xffc, -0xffe, -0xfff, -0x1000,
    };
    int64_t kSplitOffsets[] = {
        0xfff, 0x1000, 0x1001, 0x17ff, 0x1800, 0x1fff, 0x2000, 0x2001, 0x27ff, 0x2800,
        0x7fffe7ff, 0x7fffe800, 0x7fffefff, 0x7ffff000, 0x7ffff001, 0x7ffff7ff,
        -0x1001, -0x1002, -0x17ff, -0x1800, -0x1801, -0x2000, -0x2001, -0x2800, -0x2801,
        -0x7ffff000, -0x7ffff001, -0x7ffff800, -0x7ffff801, -0x7fffffff, -0x80000000,
    };
    int64_t kSpecialOffsets[] = {
        0x7ffff800, 0x7ffff801, 0x7ffffffe, 0x7fffffff
    };

    std::string expected;
    for (XRegister rs1 : GetRegisters()) {
      XRegister tmp = get_temp(rs1);
      if (tmp == kNoXRegister) {
        continue;  // Unsupported register combination.
      }
      std::string tmp_name = GetRegisterName(tmp);
      ScratchRegisterScope srs(GetAssembler());
      srs.ExcludeXRegister(rs1);
      std::string rs1_name = GetRegisterName(rs1);

      for (int64_t imm : kImm12s) {
        emit_op(rs1, imm);
        expected += ART_FORMAT("{}, {}({})\n", head, std::to_string(imm), rs1_name);
      }

      auto emit_simple_ops = [&](ArrayRef<const int64_t> imms, int64_t adjustment) {
        for (int64_t imm : imms) {
          emit_op(rs1, imm);
          expected +=
              ART_FORMAT("addi {}, {}, {}\n", tmp_name, rs1_name, std::to_string(adjustment));
          expected += ART_FORMAT("{}, {}({})\n", head, std::to_string(imm - adjustment), tmp_name);
        }
      };
      emit_simple_ops(ArrayRef<const int64_t>(kSimplePositiveOffsetsAlign8), 0x7f8);
      emit_simple_ops(ArrayRef<const int64_t>(kSimplePositiveOffsetsAlign4), 0x7fc);
      emit_simple_ops(ArrayRef<const int64_t>(kSimplePositiveOffsetsAlign2), 0x7fe);
      emit_simple_ops(ArrayRef<const int64_t>(kSimplePositiveOffsetsNoAlign), 0x7ff);
      emit_simple_ops(ArrayRef<const int64_t>(kSimpleNegativeOffsets), -0x800);

      for (int64_t imm : kSplitOffsets) {
        emit_op(rs1, imm);
        uint32_t imm20 = ((imm >> 12) + ((imm >> 11) & 1)) & 0xfffff;
        int32_t small_offset = (imm & 0xfff) - ((imm & 0x800) << 1);
        expected += ART_FORMAT("lui {}, {}\n", tmp_name, std::to_string(imm20));
        expected += ART_FORMAT("add {}, {}, {}\n", tmp_name, tmp_name, rs1_name);
        expected += ART_FORMAT("{},{}({})\n", head, std::to_string(small_offset), tmp_name);
      }

      for (int64_t imm : kSpecialOffsets) {
        emit_op(rs1, imm);
        expected += ART_FORMAT("lui {}, 0x80000\n", tmp_name);
        expected +=
            ART_FORMAT("addiw {}, {}, {}\n", tmp_name, tmp_name, std::to_string(imm - 0x80000000));
        expected += ART_FORMAT("add {}, {}, {}\n", tmp_name, tmp_name, rs1_name);
        expected += ART_FORMAT("{}, ({})\n", head, tmp_name);
      }
    }
    return expected;
  }

  void TestLoadStoreArbitraryOffset(const std::string& test_name,
                                    const std::string& insn,
                                    void (Riscv64Assembler::*fn)(XRegister, XRegister, int32_t),
                                    bool is_store) {
    std::string expected;
    for (XRegister rd : GetRegisters()) {
      ScratchRegisterScope srs(GetAssembler());
      srs.ExcludeXRegister(rd);
      auto get_temp = [&](XRegister rs1) {
        if (is_store) {
          return (rs1 != TMP && rd != TMP)
              ? TMP
              : (rs1 != TMP2 && rd != TMP2) ? TMP2 : kNoXRegister;
        } else {
          return rs1 != TMP ? TMP : TMP2;
        }
      };
      expected += RepeatLoadStoreArbitraryOffset(
          insn + " " + GetRegisterName(rd),
          get_temp,
          [&](XRegister rs1, int64_t offset) { (GetAssembler()->*fn)(rd, rs1, offset); });
    }
    DriverStr(expected, test_name);
  }

  void TestFPLoadStoreArbitraryOffset(const std::string& test_name,
                                      const std::string& insn,
                                      void (Riscv64Assembler::*fn)(FRegister, XRegister, int32_t)) {
    std::string expected;
    for (FRegister rd : GetFPRegisters()) {
      expected += RepeatLoadStoreArbitraryOffset(
          insn + " " + GetFPRegName(rd),
          [&](XRegister rs1) { return rs1 != TMP ? TMP : TMP2; },
          [&](XRegister rs1, int64_t offset) { (GetAssembler()->*fn)(rd, rs1, offset); });
    }
    DriverStr(expected, test_name);
  }

  void TestLoadLiteral(const std::string& test_name, bool with_padding_for_long) {
    std::string expected;
    Literal* narrow_literal = __ NewLiteral<uint32_t>(0x12345678);
    Literal* wide_literal = __ NewLiteral<uint64_t>(0x1234567887654321);
    auto print_load = [&](const std::string& load, XRegister rd, const std::string& label) {
      std::string rd_name = GetRegisterName(rd);
      expected += "1:\n"
                  "auipc " + rd_name + ", %pcrel_hi(" + label + "f)\n" +
                  load + " " + rd_name + ", %pcrel_lo(1b)(" + rd_name + ")\n";
    };
    for (XRegister reg : GetRegisters()) {
      if (reg != Zero) {
        __ Loadw(reg, narrow_literal);
        print_load("lw", reg, "2");
        __ Loadwu(reg, narrow_literal);
        print_load("lwu", reg, "2");
        __ Loadd(reg, wide_literal);
        print_load("ld", reg, "3");
      }
    }
    std::string tmp = GetRegisterName(TMP);
    auto print_fp_load = [&](const std::string& load, FRegister rd, const std::string& label) {
      std::string rd_name = GetFPRegName(rd);
      expected += "1:\n"
                  "auipc " + tmp + ", %pcrel_hi(" + label + "f)\n" +
                  load + " " + rd_name + ", %pcrel_lo(1b)(" + tmp + ")\n";
    };
    for (FRegister freg : GetFPRegisters()) {
      __ FLoadw(freg, narrow_literal);
      print_fp_load("flw", freg, "2");
      __ FLoadd(freg, wide_literal);
      print_fp_load("fld", freg, "3");
    }
    // All literal loads above emit 8 bytes of code. The narrow literal shall emit 4 bytes of code.
    // If we do not add another instruction, we shall end up with padding before the long literal.
    expected += EmitNops(with_padding_for_long ? 0u : sizeof(uint32_t));
    expected += "2:\n"
                ".4byte 0x12345678\n" +
                std::string(with_padding_for_long ? ".4byte 0\n" : "") +
                "3:\n"
                ".8byte 0x1234567887654321\n";
    DriverStr(expected, test_name);
  }

  std::string RepeatFFFFRoundingMode(
      void (Riscv64Assembler::*f)(FRegister, FRegister, FRegister, FRegister, FPRoundingMode),
      const std::string& fmt) {
    CHECK(f != nullptr);
    std::string str;
    for (FRegister reg1 : GetFPRegisters()) {
      for (FRegister reg2 : GetFPRegisters()) {
        for (FRegister reg3 : GetFPRegisters()) {
          for (FRegister reg4 : GetFPRegisters()) {
            for (FPRoundingMode rm : kRoundingModes) {
              (GetAssembler()->*f)(reg1, reg2, reg3, reg4, rm);

              std::string base = fmt;
              ReplaceReg(REG1_TOKEN, GetFPRegName(reg1), &base);
              ReplaceReg(REG2_TOKEN, GetFPRegName(reg2), &base);
              ReplaceReg(REG3_TOKEN, GetFPRegName(reg3), &base);
              ReplaceReg(REG4_TOKEN, GetFPRegName(reg4), &base);
              ReplaceRoundingMode(rm, &base);
              str += base;
              str += "\n";
            }
          }
        }
      }
    }
    return str;
  }

  std::string RepeatFFFRoundingMode(
      void (Riscv64Assembler::*f)(FRegister, FRegister, FRegister, FPRoundingMode),
      const std::string& fmt) {
    CHECK(f != nullptr);
    std::string str;
    for (FRegister reg1 : GetFPRegisters()) {
      for (FRegister reg2 : GetFPRegisters()) {
        for (FRegister reg3 : GetFPRegisters()) {
          for (FPRoundingMode rm : kRoundingModes) {
            (GetAssembler()->*f)(reg1, reg2, reg3, rm);

            std::string base = fmt;
            ReplaceReg(REG1_TOKEN, GetFPRegName(reg1), &base);
            ReplaceReg(REG2_TOKEN, GetFPRegName(reg2), &base);
            ReplaceReg(REG3_TOKEN, GetFPRegName(reg3), &base);
            ReplaceRoundingMode(rm, &base);
            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  template <typename Reg, typename Imm>
  std::string RepeatCTemplateRegImm(void (Riscv64Assembler::*f)(Reg, Imm),
                                    ArrayRef<const Reg> registers,
                                    std::string (Base::*GetName)(const Reg&),
                                    int imm_bits,
                                    int shift,
                                    bool no_zero_imm,
                                    const std::string& fmt) {
    auto imms = CreateImmediateValuesBits(abs(imm_bits), /*as_uint=*/imm_bits > 0, shift);

    CHECK(f != nullptr);
    std::string str;
    for (Reg reg : registers) {
      for (int64_t imm_raw : imms) {
        if (no_zero_imm && imm_raw == 0) {
          continue;
        }

        Imm imm = CreateImmediate(imm_raw);
        (GetAssembler()->*f)(reg, imm);

        std::string base = fmt;
        ReplaceReg(REG_TOKEN, (this->*GetName)(reg), &base);
        ReplaceImm(imm, /*bias=*/0, /*multiplier=*/1, &base);
        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename Imm>
  std::string RepeatCRImm(void (Riscv64Assembler::*f)(XRegister, Imm),
                          bool is_short,
                          bool no_zero_reg,
                          bool no_zero_imm,
                          int imm_bits,
                          int shift,
                          const std::string& fmt) {
    auto regs = is_short ? GetRegistersShort() : GetRegisters();
    if (no_zero_reg) {
      CHECK(!is_short);
      CHECK_EQ(regs[0], Zero);
      regs = regs.SubArray(1);
    }
    return RepeatCTemplateRegImm(
        f, regs, &AssemblerRISCV64Test::GetRegisterName, imm_bits, shift, no_zero_imm, fmt);
  }

  template <typename Imm>
  std::string RepeatCFImm(void (Riscv64Assembler::*f)(FRegister, Imm),
                          int imm_bits,
                          int shift,
                          const std::string& fmt) {
    auto regs = GetFPRegisters();
    return RepeatCTemplateRegImm(
        f, regs, &AssemblerRISCV64Test::GetFPRegName, imm_bits, shift, /*no_zero_imm=*/false, fmt);
  }

  template <typename Reg1>
  std::string RepeatTemplatedShortRegistersImm(void (Riscv64Assembler::*f)(Reg1,
                                                                           XRegister,
                                                                           int32_t),
                                               ArrayRef<const Reg1> reg1_registers,
                                               std::string (Base::*GetName1)(const Reg1&),
                                               int imm_bits,
                                               int shift,
                                               bool no_zero_imm,
                                               const std::string& fmt) {
    CHECK(f != nullptr);
    auto imms = CreateImmediateValuesBits(abs(imm_bits), imm_bits > 0, shift);
    std::string str;
    for (Reg1 reg1 : reg1_registers) {
      for (XRegister reg2 : GetRegistersShort()) {
        for (int64_t imm_raw : imms) {
          if (no_zero_imm && imm_raw == 0) {
            continue;
          }

          int32_t imm = CreateImmediate(imm_raw);
          (GetAssembler()->*f)(reg1, reg2, imm);

          std::string base = fmt;
          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, GetRegisterName(reg2), &base);
          ReplaceImm(imm, /*bias=*/0, /*multiplier=*/1, &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  std::string RepeatCRRImm(void (Riscv64Assembler::*f)(XRegister, XRegister, int32_t),
                           int imm_bits,
                           int shift,
                           const std::string& fmt) {
    return RepeatTemplatedShortRegistersImm(f,
                                            GetRegistersShort(),
                                            &AssemblerRISCV64Test::GetRegisterName,
                                            imm_bits,
                                            shift,
                                            /*no_zero_imm=*/false,
                                            fmt);
  }

  std::string RepeatCFRImm(void (Riscv64Assembler::*f)(FRegister, XRegister, int32_t),
                           int imm_bits,
                           int shift,
                           const std::string& fmt) {
    return RepeatTemplatedShortRegistersImm(f,
                                            GetFPRegistersShort(),
                                            &AssemblerRISCV64Test::GetFPRegName,
                                            imm_bits,
                                            shift,
                                            /*no_zero_imm=*/false,
                                            fmt);
  }

  std::string RepeatCRRShort(void (Riscv64Assembler::*f)(XRegister, XRegister),
                             const std::string& fmt) {
    return RepeatTemplatedRegisters(f,
                                    GetRegistersShort(),
                                    GetRegistersShort(),
                                    &AssemblerRISCV64Test::GetRegisterName,
                                    &AssemblerRISCV64Test::GetRegisterName,
                                    fmt);
  }

  std::string RepeatCRRNonZero(void (Riscv64Assembler::*f)(XRegister, XRegister),
                               const std::string& fmt) {
    auto regs = GetRegisters();
    CHECK_EQ(regs[0], Zero);
    auto regs_no_zero = regs.SubArray(1);
    return RepeatTemplatedRegisters(f,
                                    regs_no_zero,
                                    regs_no_zero,
                                    &AssemblerRISCV64Test::GetRegisterName,
                                    &AssemblerRISCV64Test::GetRegisterName,
                                    fmt);
  }

  std::string RepeatCRShort(void (Riscv64Assembler::*f)(XRegister), const std::string& fmt) {
    return RepeatTemplatedRegister(
        f, GetRegistersShort(), &AssemblerRISCV64Test::GetRegisterName, fmt);
  }

  template <typename Imm>
  std::string RepeatImm(void (Riscv64Assembler::*f)(Imm),
                        bool no_zero_imm,
                        int imm_bits,
                        int shift,
                        const std::string& fmt) {
    auto imms = CreateImmediateValuesBits(abs(imm_bits), imm_bits > 0, shift);
    std::string str;
    for (int64_t imm_raw : imms) {
      if (no_zero_imm && imm_raw == 0) {
        continue;
      }

      Imm imm = CreateImmediate(imm_raw);
      (GetAssembler()->*f)(imm);

      std::string base = fmt;
      ReplaceImm(imm, /*bias=*/0, /*multiplier=*/1, &base);
      str += base;
      str += "\n";
    }

    return str;
  }

  std::string RepeatRNoZero(void (Riscv64Assembler::*f)(XRegister), const std::string& fmt) {
    auto regs = GetRegisters();
    CHECK_EQ(regs[0], Zero);
    return RepeatTemplatedRegister(
        f, regs.SubArray(1), &AssemblerRISCV64Test::GetRegisterName, fmt);
  }

  template <typename Reg1, typename Reg2>
  std::string RepeatTemplatedRegistersRoundingMode(
      void (Riscv64Assembler::*f)(Reg1, Reg2, FPRoundingMode),
      ArrayRef<const Reg1> reg1_registers,
      ArrayRef<const Reg2> reg2_registers,
      std::string (Base::*GetName1)(const Reg1&),
      std::string (Base::*GetName2)(const Reg2&),
      const std::string& fmt) {
    CHECK(f != nullptr);
    std::string str;
    for (Reg1 reg1 : reg1_registers) {
      for (Reg2 reg2 : reg2_registers) {
        for (FPRoundingMode rm : kRoundingModes) {
          (GetAssembler()->*f)(reg1, reg2, rm);

          std::string base = fmt;
          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceRoundingMode(rm, &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  std::string RepeatFFRoundingMode(
      void (Riscv64Assembler::*f)(FRegister, FRegister, FPRoundingMode),
      const std::string& fmt) {
    return RepeatTemplatedRegistersRoundingMode(f,
                                                GetFPRegisters(),
                                                GetFPRegisters(),
                                                &AssemblerRISCV64Test::GetFPRegName,
                                                &AssemblerRISCV64Test::GetFPRegName,
                                                fmt);
  }

  std::string RepeatrFRoundingMode(
      void (Riscv64Assembler::*f)(XRegister, FRegister, FPRoundingMode),
      const std::string& fmt) {
    return RepeatTemplatedRegistersRoundingMode(f,
                                                GetRegisters(),
                                                GetFPRegisters(),
                                                &Base::GetSecondaryRegisterName,
                                                &AssemblerRISCV64Test::GetFPRegName,
                                                fmt);
  }

  std::string RepeatFrRoundingMode(
      void (Riscv64Assembler::*f)(FRegister, XRegister, FPRoundingMode),
      const std::string& fmt) {
    return RepeatTemplatedRegistersRoundingMode(f,
                                                GetFPRegisters(),
                                                GetRegisters(),
                                                &AssemblerRISCV64Test::GetFPRegName,
                                                &Base::GetSecondaryRegisterName,
                                                fmt);
  }

  template <typename InvalidAqRl>
  std::string RepeatRRAqRl(void (Riscv64Assembler::*f)(XRegister, XRegister, AqRl),
                           const std::string& fmt,
                           InvalidAqRl&& invalid_aqrl) {
    CHECK(f != nullptr);
    std::string str;
    for (XRegister reg1 : GetRegisters()) {
      for (XRegister reg2 : GetRegisters()) {
        for (AqRl aqrl : kAqRls) {
          if (invalid_aqrl(aqrl)) {
            continue;
          }
          (GetAssembler()->*f)(reg1, reg2, aqrl);

          std::string base = fmt;
          ReplaceReg(REG1_TOKEN, GetRegisterName(reg1), &base);
          ReplaceReg(REG2_TOKEN, GetRegisterName(reg2), &base);
          ReplaceAqRl(aqrl, &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename InvalidAqRl>
  std::string RepeatRRRAqRl(void (Riscv64Assembler::*f)(XRegister, XRegister, XRegister, AqRl),
                            const std::string& fmt,
                            InvalidAqRl&& invalid_aqrl) {
    CHECK(f != nullptr);
    std::string str;
    for (XRegister reg1 : GetRegisters()) {
      for (XRegister reg2 : GetRegisters()) {
        for (XRegister reg3 : GetRegisters()) {
          for (AqRl aqrl : kAqRls) {
            if (invalid_aqrl(aqrl)) {
              continue;
            }
            (GetAssembler()->*f)(reg1, reg2, reg3, aqrl);

            std::string base = fmt;
            ReplaceReg(REG1_TOKEN, GetRegisterName(reg1), &base);
            ReplaceReg(REG2_TOKEN, GetRegisterName(reg2), &base);
            ReplaceReg(REG3_TOKEN, GetRegisterName(reg3), &base);
            ReplaceAqRl(aqrl, &base);
            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  std::string RepeatRRRAqRl(void (Riscv64Assembler::*f)(XRegister, XRegister, XRegister, AqRl),
                            const std::string& fmt) {
    return RepeatRRRAqRl(f, fmt, [](AqRl) { return false; });
  }

  std::string RepeatCsrrX(void (Riscv64Assembler::*f)(XRegister, uint32_t, XRegister),
                          const std::string& fmt) {
    CHECK(f != nullptr);
    std::vector<int64_t> csrs = CreateImmediateValuesBits(12, /*as_uint=*/ true);
    std::string str;
    for (XRegister reg1 : GetRegisters()) {
      for (int64_t csr : csrs) {
        for (XRegister reg2 : GetRegisters()) {
          (GetAssembler()->*f)(reg1, dchecked_integral_cast<uint32_t>(csr), reg2);

          std::string base = fmt;
          ReplaceReg(REG1_TOKEN, GetRegisterName(reg1), &base);
          ReplaceCsrrImm(CSR_TOKEN, csr, &base);
          ReplaceReg(REG2_TOKEN, GetRegisterName(reg2), &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  std::string RepeatCsrrXi(void (Riscv64Assembler::*f)(XRegister, uint32_t, uint32_t),
                           const std::string& fmt) {
    CHECK(f != nullptr);
    std::vector<int64_t> csrs = CreateImmediateValuesBits(12, /*as_uint=*/ true);
    std::vector<int64_t> uimms = CreateImmediateValuesBits(2, /*as_uint=*/ true);
    std::string str;
    for (XRegister reg : GetRegisters()) {
      for (int64_t csr : csrs) {
        for (int64_t uimm : uimms) {
          (GetAssembler()->*f)(
              reg, dchecked_integral_cast<uint32_t>(csr), dchecked_integral_cast<uint32_t>(uimm));

          std::string base = fmt;
          ReplaceReg(REG_TOKEN, GetRegisterName(reg), &base);
          ReplaceCsrrImm(CSR_TOKEN, csr, &base);
          ReplaceCsrrImm(UIMM_TOKEN, uimm, &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  std::string RepeatVRAligned(void (Riscv64Assembler::*f)(VRegister, XRegister),
                              uint32_t alignment,
                              const std::string& fmt) {
    WarnOnCombinations(GetVectorRegisters().size() * GetRegisters().size());
    CHECK(f != nullptr);

    std::string str;
    for (auto reg1 : GetVectorRegisters()) {
      for (auto reg2 : GetRegisters()) {
        if ((static_cast<uint32_t>(reg1) % alignment) != 0) {
          continue;
        }

        (GetAssembler()->*f)(reg1, reg2);
        std::string base = fmt;

        ReplaceReg(REG1_TOKEN, GetVecRegName(reg1), &base);
        ReplaceReg(REG2_TOKEN, GetRegisterName(reg2), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  std::string RepeatVVAligned(void (Riscv64Assembler::*f)(VRegister, VRegister),
                              uint32_t alignment,
                              const std::string& fmt) {
    WarnOnCombinations(GetVectorRegisters().size() * GetRegisters().size());
    CHECK(f != nullptr);

    std::string str;
    for (auto reg1 : GetVectorRegisters()) {
      if ((static_cast<uint32_t>(reg1) % alignment) != 0) {
        continue;
      }
      for (auto reg2 : GetVectorRegisters()) {
        if ((static_cast<uint32_t>(reg2) % alignment) != 0) {
          continue;
        }

        (GetAssembler()->*f)(reg1, reg2);
        std::string base = fmt;

        ReplaceReg(REG1_TOKEN, GetVecRegName(reg1), &base);
        ReplaceReg(REG2_TOKEN, GetVecRegName(reg2), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename Reg3, typename Predicate>
  std::string RepeatTemplatedRegistersVmFiltered(
      void (Riscv64Assembler::*f)(Reg1, Reg2, Reg3, Riscv64Assembler::VM),
      ArrayRef<const Reg1> reg1_registers,
      ArrayRef<const Reg2> reg2_registers,
      ArrayRef<const Reg3> reg3_registers,
      std::string (AssemblerTest::*GetName1)(const Reg1&),
      std::string (AssemblerTest::*GetName2)(const Reg2&),
      std::string (AssemblerTest::*GetName3)(const Reg3&),
      Predicate&& pred,
      const std::string& fmt) {
    WarnOnCombinations(reg1_registers.size() * reg2_registers.size() * reg3_registers.size());
    CHECK(f != nullptr);

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (auto reg3 : reg3_registers) {
          for (Riscv64Assembler::VM vm : kVMs) {
            if (!pred(reg1, reg2, reg3, vm)) {
              continue;
            }

            (GetAssembler()->*f)(reg1, reg2, reg3, vm);
            std::string base = fmt;

            ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
            ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
            ReplaceReg(REG3_TOKEN, (this->*GetName3)(reg3), &base);
            ReplaceVm(vm, &base);

            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  template <typename Predicate>
  std::string RepeatVRRVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, XRegister, XRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetRegisters(),
                                              GetRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  std::string RepeatVRRVm(
      void (Riscv64Assembler::*f)(VRegister, XRegister, XRegister, Riscv64Assembler::VM),
      const std::string& fmt) {
    return RepeatVRRVmFiltered(
        f, fmt, [](VRegister, XRegister, XRegister, Riscv64Assembler::VM) { return true; });
  }

  template <typename Predicate>
  std::string RepeatVVRVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, VRegister, XRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetVectorRegisters(),
                                              GetRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  template <typename Predicate>
  std::string RepeatVRVVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, XRegister, VRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetRegisters(),
                                              GetVectorRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  std::string RepeatVRVVm(
      void (Riscv64Assembler::*f)(VRegister, XRegister, VRegister, Riscv64Assembler::VM),
      const std::string& fmt) {
    return RepeatVRVVmFiltered(
        f, fmt, [](VRegister, XRegister, VRegister, Riscv64Assembler::VM) { return true; });
  }

  template <typename Predicate>
  std::string RepeatVVVVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, VRegister, VRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetVectorRegisters(),
                                              GetVectorRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  std::string RepeatVVVVm(
      void (Riscv64Assembler::*f)(VRegister, VRegister, VRegister, Riscv64Assembler::VM),
      const std::string& fmt) {
    return RepeatVVVVmFiltered(
        f, fmt, [](VRegister, VRegister, VRegister, Riscv64Assembler::VM) { return true; });
  }

  template <typename Predicate>
  std::string RepeatVVFVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, VRegister, FRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetVectorRegisters(),
                                              GetFPRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetFPRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  template <typename Predicate>
  std::string RepeatVFVVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, FRegister, VRegister, Riscv64Assembler::VM),
      const std::string& fmt,
      Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetFPRegisters(),
                                              GetVectorRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetFPRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  template <typename Reg1, typename Reg2, typename Reg3, typename Predicate>
  std::string RepeatTemplatedRegistersFiltered(void (Riscv64Assembler::*f)(Reg1, Reg2, Reg3),
                                               ArrayRef<const Reg1> reg1_registers,
                                               ArrayRef<const Reg2> reg2_registers,
                                               ArrayRef<const Reg3> reg3_registers,
                                               std::string (AssemblerTest::*GetName1)(const Reg1&),
                                               std::string (AssemblerTest::*GetName2)(const Reg2&),
                                               std::string (AssemblerTest::*GetName3)(const Reg3&),
                                               Predicate&& pred,
                                               const std::string& fmt) {
    WarnOnCombinations(reg1_registers.size() * reg2_registers.size() * reg3_registers.size());
    CHECK(f != nullptr);

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (auto reg3 : reg3_registers) {
          if (!pred(reg1, reg2, reg3)) {
            continue;
          }

          (GetAssembler()->*f)(reg1, reg2, reg3);
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceReg(REG3_TOKEN, (this->*GetName3)(reg3), &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename Predicate>
  std::string RepeatVVRFiltered(void (Riscv64Assembler::*f)(VRegister, VRegister, XRegister),
                                const std::string& fmt,
                                Predicate&& pred) {
    return RepeatTemplatedRegistersFiltered(f,
                                            GetVectorRegisters(),
                                            GetVectorRegisters(),
                                            GetRegisters(),
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetRegisterName,
                                            std::forward<Predicate>(pred),
                                            fmt);
  }

  template <typename Predicate>
  std::string RepeatVVVFiltered(void (Riscv64Assembler::*f)(VRegister, VRegister, VRegister),
                                const std::string& fmt,
                                Predicate&& pred) {
    return RepeatTemplatedRegistersFiltered(f,
                                            GetVectorRegisters(),
                                            GetVectorRegisters(),
                                            GetVectorRegisters(),
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            std::forward<Predicate>(pred),
                                            fmt);
  }

  template <typename Predicate>
  std::string RepeatVVFFiltered(void (Riscv64Assembler::*f)(VRegister, VRegister, FRegister),
                                const std::string& fmt,
                                Predicate&& pred) {
    return RepeatTemplatedRegistersFiltered(f,
                                            GetVectorRegisters(),
                                            GetVectorRegisters(),
                                            GetFPRegisters(),
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetVecRegName,
                                            &AssemblerRISCV64Test::GetFPRegName,
                                            std::forward<Predicate>(pred),
                                            fmt);
  }

  template <typename Predicate, typename ImmType>
  std::string RepeatVVIFiltered(void (Riscv64Assembler::*f)(VRegister, VRegister, ImmType),
                                int imm_bits,
                                const std::string& fmt,
                                Predicate&& pred) {
    CHECK(f != nullptr);
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    WarnOnCombinations(GetVectorRegisters().size() * GetVectorRegisters().size() * imms.size());

    for (VRegister reg1 : GetVectorRegisters()) {
      for (VRegister reg2 : GetVectorRegisters()) {
        for (int64_t imm : imms) {
          ImmType new_imm = CreateImmediate(imm);

          if (!pred(reg1, reg2, new_imm)) {
            continue;
          }

          (GetAssembler()->*f)(reg1, reg2, new_imm);

          std::string base = fmt;
          ReplaceReg(REG1_TOKEN, GetVecRegName(reg1), &base);
          ReplaceReg(REG2_TOKEN, GetVecRegName(reg2), &base);
          ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);
          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename Predicate, typename ImmType>
  std::string RepeatVVIbVmFiltered(
      void (Riscv64Assembler::*f)(VRegister, VRegister, ImmType, Riscv64Assembler::VM),
      int imm_bits,
      const std::string& fmt,
      Predicate&& pred,
      ImmType bias = 0) {
    CHECK(f != nullptr);
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    WarnOnCombinations(2 * GetVectorRegisters().size() * GetVectorRegisters().size() * imms.size());

    for (VRegister reg1 : GetVectorRegisters()) {
      for (VRegister reg2 : GetVectorRegisters()) {
        for (int64_t imm : imms) {
          for (Riscv64Assembler::VM vm : kVMs) {
            if (!pred(reg1, reg2, imm, vm)) {
              continue;
            }

            ImmType new_imm = CreateImmediate(imm) + bias;
            (GetAssembler()->*f)(reg1, reg2, new_imm, vm);

            std::string base = fmt;
            ReplaceReg(REG1_TOKEN, GetVecRegName(reg1), &base);
            ReplaceImm(imm, bias, /*multiplier=*/ 1, &base);
            ReplaceReg(REG2_TOKEN, GetVecRegName(reg2), &base);
            ReplaceVm(vm, &base);
            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename Predicate>
  std::string RepeatTemplatedRegistersVmFiltered(
      void (Riscv64Assembler::*f)(Reg1, Reg2, Riscv64Assembler::VM),
      ArrayRef<const Reg1> reg1_registers,
      ArrayRef<const Reg2> reg2_registers,
      std::string (AssemblerTest::*GetName1)(const Reg1&),
      std::string (AssemblerTest::*GetName2)(const Reg2&),
      Predicate&& pred,
      const std::string& fmt) {
    CHECK(f != nullptr);

    WarnOnCombinations(2 * reg2_registers.size() * reg1_registers.size());

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (Riscv64Assembler::VM vm : kVMs) {
          if (!pred(reg1, reg2, vm)) {
            continue;
          }

          (GetAssembler()->*f)(reg1, reg2, vm);
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceVm(vm, &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename Predicate>
  std::string RepeatRVVmFiltered(void (Riscv64Assembler::*f)(XRegister,
                                                             VRegister,
                                                             Riscv64Assembler::VM),
                                 const std::string& fmt,
                                 Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetRegisters(),
                                              GetVectorRegisters(),
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  std::string RepeatRVVm(void (Riscv64Assembler::*f)(XRegister, VRegister, Riscv64Assembler::VM vm),
                         const std::string& fmt) {
    return RepeatRVVmFiltered(
        f, fmt, [](XRegister, VRegister, Riscv64Assembler::VM) { return true; });
  }

  template <typename Predicate>
  std::string RepeatVRVmFiltered(void (Riscv64Assembler::*f)(VRegister,
                                                             XRegister,
                                                             Riscv64Assembler::VM),
                                 const std::string& fmt,
                                 Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetRegisterName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  std::string RepeatVRVm(void (Riscv64Assembler::*f)(VRegister, XRegister, Riscv64Assembler::VM),
                         const std::string& fmt) {
    return RepeatVRVmFiltered(
        f, fmt, [](VRegister, XRegister, Riscv64Assembler::VM) { return true; });
  }

  template <typename Predicate>
  std::string RepeatVVVmFiltered(void (Riscv64Assembler::*f)(VRegister,
                                                             VRegister,
                                                             Riscv64Assembler::VM),
                                 const std::string& fmt,
                                 Predicate&& pred) {
    return RepeatTemplatedRegistersVmFiltered(f,
                                              GetVectorRegisters(),
                                              GetVectorRegisters(),
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              &AssemblerRISCV64Test::GetVecRegName,
                                              std::forward<Predicate>(pred),
                                              fmt);
  }

  template <typename Predicate>
  std::string RepeatVVmFiltered(void (Riscv64Assembler::*f)(VRegister, Riscv64Assembler::VM),
                                const std::string& fmt,
                                Predicate&& pred) {
    WarnOnCombinations(2 * GetVectorRegisters().size());
    CHECK(f != nullptr);
    std::string str;
    for (VRegister reg1 : GetVectorRegisters()) {
      for (Riscv64Assembler::VM vm : kVMs) {
        if (!pred(reg1, vm)) {
          continue;
        }

        (GetAssembler()->*f)(reg1, vm);

        std::string base = fmt;
        ReplaceReg(REG_TOKEN, GetVecRegName(reg1), &base);
        ReplaceVm(vm, &base);
        str += base;
        str += "\n";
      }
    }
    return str;
  }

  static constexpr bool IsVdAllowed(VRegister vd, Riscv64Assembler::VM vm) {
    return vm != Riscv64Assembler::VM::kV0_t || vd != V0;
  }

  template <typename Reg2>
  auto VXVVmSkipV0VmAndNoR1R3Overlap() {
    return [](VRegister vd, Reg2, VRegister vs1, Riscv64Assembler::VM vm) {
      return IsVdAllowed(vd, vm) && vd != vs1;
    };
  }

  template <typename Reg3>
  auto VXVVmSkipV0VmAndNoR1R2Overlap() {
    return [](VRegister vd, VRegister vs2, Reg3, Riscv64Assembler::VM vm) {
      return IsVdAllowed(vd, vm) && vd != vs2;
    };
  }

  auto VXVVmSkipV0VmAndNoR1R2R3Overlap() {
    return [](VRegister vd, VRegister vs2, VRegister vs1, Riscv64Assembler::VM vm) {
      return IsVdAllowed(vd, vm) && vd != vs1 && vd != vs2;
    };
  }

  auto VVVmSkipV0VmAndNoR1R2Overlap() {
    return [](VRegister vd, VRegister vs2, Riscv64Assembler::VM vm) {
      return IsVdAllowed(vd, vm) && vd != vs2;
    };
  }

  template <typename Reg2, typename Reg3>
  auto SkipV0Vm() {
    return [](VRegister vd, Reg2, Reg3, Riscv64Assembler::VM vm) { return IsVdAllowed(vd, vm); };
  }

  template <typename Reg2>
  auto SkipV0Vm() {
    return [](VRegister vd, Reg2, Riscv64Assembler::VM vm) { return IsVdAllowed(vd, vm); };
  }

  auto SkipV0Vm() {
    return [](VRegister vd, Riscv64Assembler::VM vm) { return IsVdAllowed(vd, vm); };
  }

  template <typename Reg2, typename Reg3>
  auto SkipV0() {
    return [](VRegister vd, Reg2, Reg3) { return vd != V0; };
  }

  auto VVVNoR1R2R3Overlap() {
    return [](VRegister vd, VRegister vs2, VRegister vs1) { return vd != vs1 && vd != vs2; };
  }

  template <typename Arg, typename Args, typename Replacer>
  std::string TestVSetI(void (Riscv64Assembler::*f)(XRegister, Arg, uint32_t),
                        Args&& arguments,
                        Replacer&& replacer,
                        const std::string& fmt) {
    CHECK(f != nullptr);

    std::string str;
    for (auto reg1 : GetRegisters()) {
      for (auto arg : arguments) {
        for (Riscv64Assembler::VectorMaskAgnostic vma : kVMAs) {
          for (Riscv64Assembler::VectorTailAgnostic vta : kVTAs) {
            for (Riscv64Assembler::SelectedElementWidth sew : kSEWs) {
              for (Riscv64Assembler::LengthMultiplier lmul : kLMULs) {
                uint32_t vtype = Riscv64Assembler::VTypeiValue(vma, vta, sew, lmul);
                (GetAssembler()->*f)(reg1, arg, vtype);
                std::string base = fmt;

                ReplaceReg(REG1_TOKEN, GetRegisterName(reg1), &base);
                replacer(arg, &base);
                ReplaceVMA(vma, &base);
                ReplaceVTA(vta, &base);
                ReplaceSEW(sew, &base);
                ReplaceLMUL(lmul, &base);

                str += base;
                str += "\n";
              }
            }
          }
        }
      }
    }
    return str;
  }

  template <typename EmitCssrX>
  void TestCsrrXMacro(const std::string& test_name,
                      const std::string& fmt,
                      EmitCssrX&& emit_csrrx) {
    std::vector<int64_t> csrs = CreateImmediateValuesBits(12, /*as_uint=*/ true);
    std::string expected;
    for (XRegister reg : GetRegisters()) {
      for (int64_t csr : csrs) {
        emit_csrrx(dchecked_integral_cast<uint32_t>(csr), reg);

        std::string base = fmt;
        ReplaceReg(REG_TOKEN, GetRegisterName(reg), &base);
        ReplaceCsrrImm(CSR_TOKEN, csr, &base);
        expected += base;
        expected += "\n";
      }
    }
    DriverStr(expected, test_name);
  }

  template <typename EmitCssrXi>
  void TestCsrrXiMacro(const std::string& test_name,
                       const std::string& fmt,
                       EmitCssrXi&& emit_csrrxi) {
    std::vector<int64_t> csrs = CreateImmediateValuesBits(12, /*as_uint=*/ true);
    std::vector<int64_t> uimms = CreateImmediateValuesBits(2, /*as_uint=*/ true);
    std::string expected;
    for (int64_t csr : csrs) {
      for (int64_t uimm : uimms) {
        emit_csrrxi(dchecked_integral_cast<uint32_t>(csr), dchecked_integral_cast<uint32_t>(uimm));

        std::string base = fmt;
        ReplaceCsrrImm(CSR_TOKEN, csr, &base);
        ReplaceCsrrImm(UIMM_TOKEN, uimm, &base);
        expected += base;
        expected += "\n";
      }
    }
    DriverStr(expected, test_name);
  }

 private:
  static constexpr const char* RM_TOKEN = "{rm}";
  static constexpr const char* AQRL_TOKEN = "{aqrl}";
  static constexpr const char* CSR_TOKEN = "{csr}";
  static constexpr const char* UIMM_TOKEN = "{uimm}";
  static constexpr const char* VM_TOKEN = "{vm}";
  static constexpr const char* VMA_TOKEN = "{vma}";
  static constexpr const char* VTA_TOKEN = "{vta}";
  static constexpr const char* SEW_TOKEN = "{sew}";
  static constexpr const char* LMUL_TOKEN = "{lmul}";

  static constexpr AqRl kAqRls[] = { AqRl::kNone, AqRl::kRelease, AqRl::kAcquire, AqRl::kAqRl };

  static constexpr Riscv64Assembler::VM kVMs[] = {Riscv64Assembler::VM::kUnmasked,
                                                  Riscv64Assembler::VM::kV0_t};

  static constexpr Riscv64Assembler::VectorMaskAgnostic kVMAs[] = {
      Riscv64Assembler::VectorMaskAgnostic::kAgnostic,
      Riscv64Assembler::VectorMaskAgnostic::kUndisturbed};

  static constexpr Riscv64Assembler::VectorTailAgnostic kVTAs[] = {
      Riscv64Assembler::VectorTailAgnostic::kAgnostic,
      Riscv64Assembler::VectorTailAgnostic::kUndisturbed};

  static constexpr Riscv64Assembler::SelectedElementWidth kSEWs[] = {
      Riscv64Assembler::SelectedElementWidth::kE8,
      Riscv64Assembler::SelectedElementWidth::kE16,
      Riscv64Assembler::SelectedElementWidth::kE32,
      Riscv64Assembler::SelectedElementWidth::kE64};

  static constexpr Riscv64Assembler::LengthMultiplier kLMULs[] = {
      Riscv64Assembler::LengthMultiplier::kM1Over8,
      Riscv64Assembler::LengthMultiplier::kM1Over4,
      Riscv64Assembler::LengthMultiplier::kM1Over2,
      Riscv64Assembler::LengthMultiplier::kM1,
      Riscv64Assembler::LengthMultiplier::kM2,
      Riscv64Assembler::LengthMultiplier::kM4,
      Riscv64Assembler::LengthMultiplier::kM8};

  static constexpr FPRoundingMode kRoundingModes[] = {
      FPRoundingMode::kRNE,
      FPRoundingMode::kRTZ,
      FPRoundingMode::kRDN,
      FPRoundingMode::kRUP,
      FPRoundingMode::kRMM,
      FPRoundingMode::kDYN
  };

  void ReplaceRoundingMode(FPRoundingMode rm, /*inout*/ std::string* str) {
    const char* replacement;
    switch (rm) {
      case FPRoundingMode::kRNE:
        replacement = "rne";
        break;
      case FPRoundingMode::kRTZ:
        replacement = "rtz";
        break;
      case FPRoundingMode::kRDN:
        replacement = "rdn";
        break;
      case FPRoundingMode::kRUP:
        replacement = "rup";
        break;
      case FPRoundingMode::kRMM:
        replacement = "rmm";
        break;
      case FPRoundingMode::kDYN:
        replacement = "dyn";
        break;
      default:
        LOG(FATAL) << "Unexpected value for rm: " << enum_cast<uint32_t>(rm);
        UNREACHABLE();
    }
    size_t rm_index = str->find(RM_TOKEN);
    EXPECT_NE(rm_index, std::string::npos);
    if (rm_index != std::string::npos) {
      str->replace(rm_index, ConstexprStrLen(RM_TOKEN), replacement);
    }
  }

  void ReplaceAqRl(AqRl aqrl, /*inout*/ std::string* str) {
    const char* replacement;
    switch (aqrl) {
      case AqRl::kNone:
        replacement = "";
        break;
      case AqRl::kRelease:
        replacement = ".rl";
        break;
      case AqRl::kAcquire:
        replacement = ".aq";
        break;
      case AqRl::kAqRl:
        replacement = ".aqrl";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `aqrl`: " << enum_cast<uint32_t>(aqrl);
        UNREACHABLE();
    }
    size_t aqrl_index = str->find(AQRL_TOKEN);
    EXPECT_NE(aqrl_index, std::string::npos);
    if (aqrl_index != std::string::npos) {
      str->replace(aqrl_index, ConstexprStrLen(AQRL_TOKEN), replacement);
    }
  }

  void ReplaceVm(Riscv64Assembler::VM vm, /*inout*/ std::string* str) {
    const char* replacement;
    switch (vm) {
      case Riscv64Assembler::VM::kUnmasked:
        replacement = "";
        break;
      case Riscv64Assembler::VM::kV0_t:
        replacement = ", v0.t";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `VM`: " << enum_cast<uint32_t>(vm);
        UNREACHABLE();
    }
    size_t vm_index = str->find(VM_TOKEN);
    EXPECT_NE(vm_index, std::string::npos);
    if (vm_index != std::string::npos) {
      str->replace(vm_index, ConstexprStrLen(VM_TOKEN), replacement);
    }
  }

  void ReplaceVMA(Riscv64Assembler::VectorMaskAgnostic vma, /*inout*/ std::string* str) {
    const char* replacement;
    switch (vma) {
      case Riscv64Assembler::VectorMaskAgnostic::kAgnostic:
        replacement = "ma";
        break;
      case Riscv64Assembler::VectorMaskAgnostic::kUndisturbed:
        replacement = "mu";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `VectorMaskAgnostic`: " << enum_cast<uint32_t>(vma);
        UNREACHABLE();
    }
    size_t vma_index = str->find(VMA_TOKEN);
    EXPECT_NE(vma_index, std::string::npos);
    if (vma_index != std::string::npos) {
      str->replace(vma_index, ConstexprStrLen(VMA_TOKEN), replacement);
    }
  }

  void ReplaceVTA(Riscv64Assembler::VectorTailAgnostic vta, /*inout*/ std::string* str) {
    const char* replacement;
    switch (vta) {
      case Riscv64Assembler::VectorTailAgnostic::kAgnostic:
        replacement = "ta";
        break;
      case Riscv64Assembler::VectorTailAgnostic::kUndisturbed:
        replacement = "tu";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `VectorTailAgnostic`: " << enum_cast<uint32_t>(vta);
        UNREACHABLE();
    }
    size_t vta_index = str->find(VTA_TOKEN);
    EXPECT_NE(vta_index, std::string::npos);
    if (vta_index != std::string::npos) {
      str->replace(vta_index, ConstexprStrLen(VTA_TOKEN), replacement);
    }
  }

  void ReplaceSEW(Riscv64Assembler::SelectedElementWidth sew, /*inout*/ std::string* str) {
    const char* replacement;
    switch (sew) {
      case Riscv64Assembler::SelectedElementWidth::kE8:
        replacement = "e8";
        break;
      case Riscv64Assembler::SelectedElementWidth::kE16:
        replacement = "e16";
        break;
      case Riscv64Assembler::SelectedElementWidth::kE32:
        replacement = "e32";
        break;
      case Riscv64Assembler::SelectedElementWidth::kE64:
        replacement = "e64";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `SelectedElementWidth`: " << enum_cast<uint32_t>(sew);
        UNREACHABLE();
    }
    size_t sew_index = str->find(SEW_TOKEN);
    EXPECT_NE(sew_index, std::string::npos);
    if (sew_index != std::string::npos) {
      str->replace(sew_index, ConstexprStrLen(SEW_TOKEN), replacement);
    }
  }

  void ReplaceLMUL(Riscv64Assembler::LengthMultiplier lmul, /*inout*/ std::string* str) {
    const char* replacement;
    switch (lmul) {
      case Riscv64Assembler::LengthMultiplier::kM1Over8:
        replacement = "mf8";
        break;
      case Riscv64Assembler::LengthMultiplier::kM1Over4:
        replacement = "mf4";
        break;
      case Riscv64Assembler::LengthMultiplier::kM1Over2:
        replacement = "mf2";
        break;
      case Riscv64Assembler::LengthMultiplier::kM1:
        replacement = "m1";
        break;
      case Riscv64Assembler::LengthMultiplier::kM2:
        replacement = "m2";
        break;
      case Riscv64Assembler::LengthMultiplier::kM4:
        replacement = "m4";
        break;
      case Riscv64Assembler::LengthMultiplier::kM8:
        replacement = "m8";
        break;
      default:
        LOG(FATAL) << "Unexpected value for `LengthMultiplier`: " << enum_cast<uint32_t>(lmul);
        UNREACHABLE();
    }
    size_t lmul_index = str->find(LMUL_TOKEN);
    EXPECT_NE(lmul_index, std::string::npos);
    if (lmul_index != std::string::npos) {
      str->replace(lmul_index, ConstexprStrLen(LMUL_TOKEN), replacement);
    }
  }

  static void ReplaceCsrrImm(const std::string& imm_token,
                             int64_t imm,
                             /*inout*/ std::string* str) {
    size_t imm_index = str->find(imm_token);
    EXPECT_NE(imm_index, std::string::npos);
    if (imm_index != std::string::npos) {
      str->replace(imm_index, imm_token.length(), std::to_string(imm));
    }
  }

  std::map<XRegister, std::string, RISCV64CpuRegisterCompare> secondary_register_names_;

  std::optional<std::string> march_override_;
};

TEST_F(AssemblerRISCV64Test, Toolchain) { EXPECT_TRUE(CheckTools()); }

TEST_F(AssemblerRISCV64Test, Lui) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRIb(&Riscv64Assembler::Lui, 20, "lui {reg}, {imm}"), "Lui");
}

TEST_F(AssemblerRISCV64Test, Auipc) {
  DriverStr(RepeatRIb(&Riscv64Assembler::Auipc, 20, "auipc {reg}, {imm}"), "Auipc");
}

TEST_F(AssemblerRISCV64Test, Jal) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-19, 2" to "-20, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Jal, -19, 2, "jal {reg}, {imm}\n"), "Jal");
}

TEST_F(AssemblerRISCV64Test, Jalr) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIb(&Riscv64Assembler::Jalr, -12, "jalr {reg1}, {reg2}, {imm}\n"), "Jalr");
}

TEST_F(AssemblerRISCV64Test, Beq) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Beq, -11, 2, "beq {reg1}, {reg2}, {imm}\n"), "Beq");
}

TEST_F(AssemblerRISCV64Test, Bne) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bne, -11, 2, "bne {reg1}, {reg2}, {imm}\n"), "Bne");
}

TEST_F(AssemblerRISCV64Test, Blt) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Blt, -11, 2, "blt {reg1}, {reg2}, {imm}\n"), "Blt");
}

TEST_F(AssemblerRISCV64Test, Bge) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bge, -11, 2, "bge {reg1}, {reg2}, {imm}\n"), "Bge");
}

TEST_F(AssemblerRISCV64Test, Bltu) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bltu, -11, 2, "bltu {reg1}, {reg2}, {imm}\n"), "Bltu");
}

TEST_F(AssemblerRISCV64Test, Bgeu) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bgeu, -11, 2, "bgeu {reg1}, {reg2}, {imm}\n"), "Bgeu");
}

TEST_F(AssemblerRISCV64Test, Lb) {
  // Note: There is no 16-bit instruction for `Lb()`.
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lb, -12, "lb {reg1}, {imm}({reg2})"), "Lb");
}

TEST_F(AssemblerRISCV64Test, Lh) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lh, -12, "lh {reg1}, {imm}({reg2})"), "Lh");
}

TEST_F(AssemblerRISCV64Test, Lw) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lw, -12, "lw {reg1}, {imm}({reg2})"), "Lw");
}

TEST_F(AssemblerRISCV64Test, Ld) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Ld, -12, "ld {reg1}, {imm}({reg2})"), "Ld");
}

TEST_F(AssemblerRISCV64Test, Lbu) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lbu, -12, "lbu {reg1}, {imm}({reg2})"), "Lbu");
}

TEST_F(AssemblerRISCV64Test, Lhu) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lhu, -12, "lhu {reg1}, {imm}({reg2})"), "Lhu");
}

TEST_F(AssemblerRISCV64Test, Lwu) {
  // Note: There is no 16-bit instruction for `Lwu()`.
  DriverStr(RepeatRRIb(&Riscv64Assembler::Lwu, -12, "lwu {reg1}, {imm}({reg2})"), "Lwu");
}

TEST_F(AssemblerRISCV64Test, Sb) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sb, -12, "sb {reg1}, {imm}({reg2})"), "Sb");
}

TEST_F(AssemblerRISCV64Test, Sh) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sh, -12, "sh {reg1}, {imm}({reg2})"), "Sh");
}

TEST_F(AssemblerRISCV64Test, Sw) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sw, -12, "sw {reg1}, {imm}({reg2})"), "Sw");
}

TEST_F(AssemblerRISCV64Test, Sd) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sd, -12, "sd {reg1}, {imm}({reg2})"), "Sd");
}

TEST_F(AssemblerRISCV64Test, Addi) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Addi, -12, "addi {reg1}, {reg2}, {imm}"), "Addi");
}

TEST_F(AssemblerRISCV64Test, Slti) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Slti, -12, "slti {reg1}, {reg2}, {imm}"), "Slti");
}

TEST_F(AssemblerRISCV64Test, Sltiu) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sltiu, -12, "sltiu {reg1}, {reg2}, {imm}"), "Sltiu");
}

TEST_F(AssemblerRISCV64Test, Xori) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Xori, 11, "xori {reg1}, {reg2}, {imm}"), "Xori");
}

TEST_F(AssemblerRISCV64Test, Ori) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Ori, -12, "ori {reg1}, {reg2}, {imm}"), "Ori");
}

TEST_F(AssemblerRISCV64Test, Andi) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Andi, -12, "andi {reg1}, {reg2}, {imm}"), "Andi");
}

TEST_F(AssemblerRISCV64Test, Slli) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Slli, 6, "slli {reg1}, {reg2}, {imm}"), "Slli");
}

TEST_F(AssemblerRISCV64Test, Srli) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Srli, 6, "srli {reg1}, {reg2}, {imm}"), "Srli");
}

TEST_F(AssemblerRISCV64Test, Srai) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Srai, 6, "srai {reg1}, {reg2}, {imm}"), "Srai");
}

TEST_F(AssemblerRISCV64Test, Add) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Add, "add {reg1}, {reg2}, {reg3}"), "Add");
}

TEST_F(AssemblerRISCV64Test, Sub) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Sub, "sub {reg1}, {reg2}, {reg3}"), "Sub");
}

TEST_F(AssemblerRISCV64Test, Slt) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Slt, "slt {reg1}, {reg2}, {reg3}"), "Slt");
}

TEST_F(AssemblerRISCV64Test, Sltu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sltu, "sltu {reg1}, {reg2}, {reg3}"), "Sltu");
}

TEST_F(AssemblerRISCV64Test, Xor) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Xor, "xor {reg1}, {reg2}, {reg3}"), "Xor");
}

TEST_F(AssemblerRISCV64Test, Or) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Or, "or {reg1}, {reg2}, {reg3}"), "Or");
}

TEST_F(AssemblerRISCV64Test, And) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::And, "and {reg1}, {reg2}, {reg3}"), "And");
}

TEST_F(AssemblerRISCV64Test, Sll) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sll, "sll {reg1}, {reg2}, {reg3}"), "Sll");
}

TEST_F(AssemblerRISCV64Test, Srl) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Srl, "srl {reg1}, {reg2}, {reg3}"), "Srl");
}

TEST_F(AssemblerRISCV64Test, Sra) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sra, "sra {reg1}, {reg2}, {reg3}"), "Sra");
}

TEST_F(AssemblerRISCV64Test, Addiw) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRIb(&Riscv64Assembler::Addiw, -12, "addiw {reg1}, {reg2}, {imm}"), "Addiw");
}

TEST_F(AssemblerRISCV64Test, Slliw) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Slliw, /*imm_bits=*/ 5, "slliw {reg1}, {reg2}, {imm}"),
            "Slliw");
}

TEST_F(AssemblerRISCV64Test, Srliw) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Srliw, /*imm_bits=*/ 5, "srliw {reg1}, {reg2}, {imm}"),
            "Srliw");
}

TEST_F(AssemblerRISCV64Test, Sraiw) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Sraiw, /*imm_bits=*/ 5, "sraiw {reg1}, {reg2}, {imm}"),
            "Sraiw");
}

TEST_F(AssemblerRISCV64Test, Addw) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Addw, "addw {reg1}, {reg2}, {reg3}"), "Addw");
}

TEST_F(AssemblerRISCV64Test, Subw) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Subw, "subw {reg1}, {reg2}, {reg3}"), "Subw");
}

TEST_F(AssemblerRISCV64Test, Sllw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sllw, "sllw {reg1}, {reg2}, {reg3}"), "Sllw");
}

TEST_F(AssemblerRISCV64Test, Srlw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Srlw, "srlw {reg1}, {reg2}, {reg3}"), "Srlw");
}

TEST_F(AssemblerRISCV64Test, Sraw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sraw, "sraw {reg1}, {reg2}, {reg3}"), "Sraw");
}

TEST_F(AssemblerRISCV64Test, Ecall) {
  __ Ecall();
  DriverStr("ecall\n", "Ecall");
}

TEST_F(AssemblerRISCV64Test, Ebreak) {
  ScopedCSuppression scs(this);
  __ Ebreak();
  DriverStr("ebreak\n", "Ebreak");
}

TEST_F(AssemblerRISCV64Test, Fence) {
  auto get_fence_type_string = [](uint32_t fence_type) {
    CHECK_LE(fence_type, 0xfu);
    std::string result;
    if ((fence_type & kFenceInput) != 0u) {
      result += "i";
    }
    if ((fence_type & kFenceOutput) != 0u) {
      result += "o";
    }
    if ((fence_type & kFenceRead) != 0u) {
      result += "r";
    }
    if ((fence_type & kFenceWrite) != 0u) {
      result += "w";
    }
    if (result.empty()) {
      result += "0";
    }
    return result;
  };

  std::string expected;
  // Note: The `pred` and `succ` are 4 bits each.
  // Some combinations are not really useful but the assembler can emit them all.
  for (uint32_t pred = 0u; pred != 0x10; ++pred) {
    for (uint32_t succ = 0u; succ != 0x10; ++succ) {
      __ Fence(pred, succ);
      expected +=
          "fence " + get_fence_type_string(pred) + ", " + get_fence_type_string(succ) + "\n";
    }
  }
  DriverStr(expected, "Fence");
}

TEST_F(AssemblerRISCV64Test, FenceTso) {
  __ FenceTso();
  DriverStr("fence.tso", "FenceTso");
}

TEST_F(AssemblerRISCV64Test, FenceI) {
  __ FenceI();
  DriverStr("fence.i", "FenceI");
}

TEST_F(AssemblerRISCV64Test, Mul) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRRR(&Riscv64Assembler::Mul, "mul {reg1}, {reg2}, {reg3}"), "Mul");
}

TEST_F(AssemblerRISCV64Test, Mulh) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Mulh, "mulh {reg1}, {reg2}, {reg3}"), "Mulh");
}

TEST_F(AssemblerRISCV64Test, Mulhsu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Mulhsu, "mulhsu {reg1}, {reg2}, {reg3}"), "Mulhsu");
}

TEST_F(AssemblerRISCV64Test, Mulhu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Mulhu, "mulhu {reg1}, {reg2}, {reg3}"), "Mulhu");
}

TEST_F(AssemblerRISCV64Test, Div) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Div, "div {reg1}, {reg2}, {reg3}"), "Div");
}

TEST_F(AssemblerRISCV64Test, Divu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Divu, "divu {reg1}, {reg2}, {reg3}"), "Divu");
}

TEST_F(AssemblerRISCV64Test, Rem) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Rem, "rem {reg1}, {reg2}, {reg3}"), "Rem");
}

TEST_F(AssemblerRISCV64Test, Remu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Remu, "remu {reg1}, {reg2}, {reg3}"), "Remu");
}

TEST_F(AssemblerRISCV64Test, Mulw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Mulw, "mulw {reg1}, {reg2}, {reg3}"), "Mulw");
}

TEST_F(AssemblerRISCV64Test, Divw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Divw, "divw {reg1}, {reg2}, {reg3}"), "Divw");
}

TEST_F(AssemblerRISCV64Test, Divuw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Divuw, "divuw {reg1}, {reg2}, {reg3}"), "Divuw");
}

TEST_F(AssemblerRISCV64Test, Remw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Remw, "remw {reg1}, {reg2}, {reg3}"), "Remw");
}

TEST_F(AssemblerRISCV64Test, Remuw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Remuw, "remuw {reg1}, {reg2}, {reg3}"), "Remuw");
}

TEST_F(AssemblerRISCV64Test, LrW) {
  auto invalid_aqrl = [](AqRl aqrl) { return aqrl == AqRl::kRelease; };
  DriverStr(RepeatRRAqRl(&Riscv64Assembler::LrW, "lr.w{aqrl} {reg1}, ({reg2})", invalid_aqrl),
            "LrW");
}

TEST_F(AssemblerRISCV64Test, LrD) {
  auto invalid_aqrl = [](AqRl aqrl) { return aqrl == AqRl::kRelease; };
  DriverStr(RepeatRRAqRl(&Riscv64Assembler::LrD, "lr.d{aqrl} {reg1}, ({reg2})", invalid_aqrl),
            "LrD");
}

TEST_F(AssemblerRISCV64Test, ScW) {
  auto invalid_aqrl = [](AqRl aqrl) { return aqrl == AqRl::kAcquire; };
  DriverStr(
      RepeatRRRAqRl(&Riscv64Assembler::ScW, "sc.w{aqrl} {reg1}, {reg2}, ({reg3})", invalid_aqrl),
      "ScW");
}

TEST_F(AssemblerRISCV64Test, ScD) {
  auto invalid_aqrl = [](AqRl aqrl) { return aqrl == AqRl::kAcquire; };
  DriverStr(
      RepeatRRRAqRl(&Riscv64Assembler::ScD, "sc.d{aqrl} {reg1}, {reg2}, ({reg3})", invalid_aqrl),
      "ScD");
}

TEST_F(AssemblerRISCV64Test, AmoSwapW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoSwapW, "amoswap.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoSwapW");
}

TEST_F(AssemblerRISCV64Test, AmoSwapD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoSwapD, "amoswap.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoSwapD");
}

TEST_F(AssemblerRISCV64Test, AmoAddW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoAddW, "amoadd.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoAddW");
}

TEST_F(AssemblerRISCV64Test, AmoAddD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoAddD, "amoadd.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoAddD");
}

TEST_F(AssemblerRISCV64Test, AmoXorW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoXorW, "amoxor.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoXorW");
}

TEST_F(AssemblerRISCV64Test, AmoXorD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoXorD, "amoxor.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoXorD");
}

TEST_F(AssemblerRISCV64Test, AmoAndW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoAndW, "amoand.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoAndW");
}

TEST_F(AssemblerRISCV64Test, AmoAndD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoAndD, "amoand.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoAndD");
}

TEST_F(AssemblerRISCV64Test, AmoOrW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoOrW, "amoor.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoOrW");
}

TEST_F(AssemblerRISCV64Test, AmoOrD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoOrD, "amoor.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoOrD");
}

TEST_F(AssemblerRISCV64Test, AmoMinW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMinW, "amomin.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMinW");
}

TEST_F(AssemblerRISCV64Test, AmoMinD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMinD, "amomin.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMinD");
}

TEST_F(AssemblerRISCV64Test, AmoMaxW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMaxW, "amomax.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMaxW");
}

TEST_F(AssemblerRISCV64Test, AmoMaxD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMaxD, "amomax.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMaxD");
}

TEST_F(AssemblerRISCV64Test, AmoMinuW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMinuW, "amominu.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMinuW");
}

TEST_F(AssemblerRISCV64Test, AmoMinuD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMinuD, "amominu.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMinuD");
}

TEST_F(AssemblerRISCV64Test, AmoMaxuW) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMaxuW, "amomaxu.w{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMaxuW");
}

TEST_F(AssemblerRISCV64Test, AmoMaxuD) {
  DriverStr(RepeatRRRAqRl(&Riscv64Assembler::AmoMaxuD, "amomaxu.d{aqrl} {reg1}, {reg2}, ({reg3})"),
            "AmoMaxuD");
}

TEST_F(AssemblerRISCV64Test, Csrrw) {
  DriverStr(RepeatCsrrX(&Riscv64Assembler::Csrrw, "csrrw {reg1}, {csr}, {reg2}"), "Csrrw");
}

TEST_F(AssemblerRISCV64Test, Csrrs) {
  DriverStr(RepeatCsrrX(&Riscv64Assembler::Csrrs, "csrrs {reg1}, {csr}, {reg2}"), "Csrrs");
}

TEST_F(AssemblerRISCV64Test, Csrrc) {
  DriverStr(RepeatCsrrX(&Riscv64Assembler::Csrrc, "csrrc {reg1}, {csr}, {reg2}"), "Csrrc");
}

TEST_F(AssemblerRISCV64Test, Csrrwi) {
  DriverStr(RepeatCsrrXi(&Riscv64Assembler::Csrrwi, "csrrwi {reg}, {csr}, {uimm}"), "Csrrwi");
}

TEST_F(AssemblerRISCV64Test, Csrrsi) {
  DriverStr(RepeatCsrrXi(&Riscv64Assembler::Csrrsi, "csrrsi {reg}, {csr}, {uimm}"), "Csrrsi");
}

TEST_F(AssemblerRISCV64Test, Csrrci) {
  DriverStr(RepeatCsrrXi(&Riscv64Assembler::Csrrci, "csrrci {reg}, {csr}, {uimm}"), "Csrrci");
}

TEST_F(AssemblerRISCV64Test, FLw) {
  // Note: 16-bit variants of `flw` are not available on riscv64.
  DriverStr(RepeatFRIb(&Riscv64Assembler::FLw, -12, "flw {reg1}, {imm}({reg2})"), "FLw");
}

TEST_F(AssemblerRISCV64Test, FLd) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatFRIb(&Riscv64Assembler::FLd, -12, "fld {reg1}, {imm}({reg2})"), "FLw");
}

TEST_F(AssemblerRISCV64Test, FSw) {
  // Note: 16-bit variants of `fsw` are not available on riscv64.
  DriverStr(RepeatFRIb(&Riscv64Assembler::FSw, 2, "fsw {reg1}, {imm}({reg2})"), "FSw");
}

TEST_F(AssemblerRISCV64Test, FSd) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatFRIb(&Riscv64Assembler::FSd, 2, "fsd {reg1}, {imm}({reg2})"), "FSd");
}

TEST_F(AssemblerRISCV64Test, FMAddS) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FMAddS,
                                   "fmadd.s {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FMAddS");
}

TEST_F(AssemblerRISCV64Test, FMAddS_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FMAddS, "fmadd.s {reg1}, {reg2}, {reg3}, {reg4}"),
            "FMAddS_Default");
}

TEST_F(AssemblerRISCV64Test, FMAddD) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FMAddD,
                                   "fmadd.d {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FMAddD");
}

TEST_F(AssemblerRISCV64Test, FMAddD_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FMAddD, "fmadd.d {reg1}, {reg2}, {reg3}, {reg4}"),
            "FMAddD_Default");
}

TEST_F(AssemblerRISCV64Test, FMSubS) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FMSubS,
                                   "fmsub.s {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FMSubS");
}

TEST_F(AssemblerRISCV64Test, FMSubS_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FMSubS, "fmsub.s {reg1}, {reg2}, {reg3}, {reg4}"),
            "FMSubS_Default");
}

TEST_F(AssemblerRISCV64Test, FMSubD) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FMSubD,
                                  "fmsub.d {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FMSubD");
}

TEST_F(AssemblerRISCV64Test, FMSubD_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FMSubD, "fmsub.d {reg1}, {reg2}, {reg3}, {reg4}"),
            "FMSubD_Default");
}

TEST_F(AssemblerRISCV64Test, FNMSubS) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FNMSubS,
                                   "fnmsub.s {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FNMSubS");
}

TEST_F(AssemblerRISCV64Test, FNMSubS_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FNMSubS, "fnmsub.s {reg1}, {reg2}, {reg3}, {reg4}"),
            "FNMSubS_Default");
}

TEST_F(AssemblerRISCV64Test, FNMSubD) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FNMSubD,
                                   "fnmsub.d {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FNMSubD");
}

TEST_F(AssemblerRISCV64Test, FNMSubD_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FNMSubD, "fnmsub.d {reg1}, {reg2}, {reg3}, {reg4}"),
            "FNMSubD_Default");
}

TEST_F(AssemblerRISCV64Test, FNMAddS) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FNMAddS,
                                   "fnmadd.s {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FNMAddS");
}

TEST_F(AssemblerRISCV64Test, FNMAddS_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FNMAddS, "fnmadd.s {reg1}, {reg2}, {reg3}, {reg4}"),
            "FNMAddS_Default");
}

TEST_F(AssemblerRISCV64Test, FNMAddD) {
  DriverStr(RepeatFFFFRoundingMode(&Riscv64Assembler::FNMAddD,
                                   "fnmadd.d {reg1}, {reg2}, {reg3}, {reg4}, {rm}"), "FNMAddD");
}

TEST_F(AssemblerRISCV64Test, FNMAddD_Default) {
  DriverStr(RepeatFFFF(&Riscv64Assembler::FNMAddD, "fnmadd.d {reg1}, {reg2}, {reg3}, {reg4}"),
            "FNMAddD_Default");
}

TEST_F(AssemblerRISCV64Test, FAddS) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FAddS, "fadd.s {reg1}, {reg2}, {reg3}, {rm}"),
            "FAddS");
}

TEST_F(AssemblerRISCV64Test, FAddS_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FAddS, "fadd.s {reg1}, {reg2}, {reg3}"), "FAddS_Default");
}

TEST_F(AssemblerRISCV64Test, FAddD) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FAddD, "fadd.d {reg1}, {reg2}, {reg3}, {rm}"),
            "FAddD");
}

TEST_F(AssemblerRISCV64Test, FAddD_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FAddD, "fadd.d {reg1}, {reg2}, {reg3}"), "FAddD_Default");
}

TEST_F(AssemblerRISCV64Test, FSubS) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FSubS, "fsub.s {reg1}, {reg2}, {reg3}, {rm}"),
            "FSubS");
}

TEST_F(AssemblerRISCV64Test, FSubS_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSubS, "fsub.s {reg1}, {reg2}, {reg3}"), "FSubS_Default");
}

TEST_F(AssemblerRISCV64Test, FSubD) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FSubD, "fsub.d {reg1}, {reg2}, {reg3}, {rm}"),
            "FSubD");
}

TEST_F(AssemblerRISCV64Test, FSubD_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSubD, "fsub.d {reg1}, {reg2}, {reg3}"), "FSubD_Default");
}

TEST_F(AssemblerRISCV64Test, FMulS) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FMulS, "fmul.s {reg1}, {reg2}, {reg3}, {rm}"),
            "FMulS");
}

TEST_F(AssemblerRISCV64Test, FMulS_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMulS, "fmul.s {reg1}, {reg2}, {reg3}"), "FMulS_Default");
}

TEST_F(AssemblerRISCV64Test, FMulD) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FMulD, "fmul.d {reg1}, {reg2}, {reg3}, {rm}"),
            "FMulD");
}

TEST_F(AssemblerRISCV64Test, FMulD_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMulD, "fmul.d {reg1}, {reg2}, {reg3}"), "FMulD_Default");
}

TEST_F(AssemblerRISCV64Test, FDivS) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FDivS, "fdiv.s {reg1}, {reg2}, {reg3}, {rm}"),
            "FDivS");
}

TEST_F(AssemblerRISCV64Test, FDivS_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FDivS, "fdiv.s {reg1}, {reg2}, {reg3}"), "FDivS_Default");
}

TEST_F(AssemblerRISCV64Test, FDivD) {
  DriverStr(RepeatFFFRoundingMode(&Riscv64Assembler::FDivD, "fdiv.d {reg1}, {reg2}, {reg3}, {rm}"),
            "FDivD");
}

TEST_F(AssemblerRISCV64Test, FDivD_Default) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FDivD, "fdiv.d {reg1}, {reg2}, {reg3}"), "FDivD_Default");
}

TEST_F(AssemblerRISCV64Test, FSqrtS) {
  DriverStr(RepeatFFRoundingMode(&Riscv64Assembler::FSqrtS, "fsqrt.s {reg1}, {reg2}, {rm}"),
            "FSqrtS");
}

TEST_F(AssemblerRISCV64Test, FSqrtS_Default) {
  DriverStr(RepeatFF(&Riscv64Assembler::FSqrtS, "fsqrt.s {reg1}, {reg2}"), "FSqrtS_Default");
}

TEST_F(AssemblerRISCV64Test, FSqrtD) {
  DriverStr(RepeatFFRoundingMode(&Riscv64Assembler::FSqrtD, "fsqrt.d {reg1}, {reg2}, {rm}"),
            "FSqrtD");
}

TEST_F(AssemblerRISCV64Test, FSqrtD_Default) {
  DriverStr(RepeatFF(&Riscv64Assembler::FSqrtD, "fsqrt.d {reg1}, {reg2}"), "FSqrtD_Default");
}

TEST_F(AssemblerRISCV64Test, FSgnjS) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjS, "fsgnj.s {reg1}, {reg2}, {reg3}"), "FSgnjS");
}

TEST_F(AssemblerRISCV64Test, FSgnjD) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjD, "fsgnj.d {reg1}, {reg2}, {reg3}"), "FSgnjD");
}

TEST_F(AssemblerRISCV64Test, FSgnjnS) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjnS, "fsgnjn.s {reg1}, {reg2}, {reg3}"), "FSgnjnS");
}

TEST_F(AssemblerRISCV64Test, FSgnjnD) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjnD, "fsgnjn.d {reg1}, {reg2}, {reg3}"), "FSgnjnD");
}

TEST_F(AssemblerRISCV64Test, FSgnjxS) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjxS, "fsgnjx.s {reg1}, {reg2}, {reg3}"), "FSgnjxS");
}

TEST_F(AssemblerRISCV64Test, FSgnjxD) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FSgnjxD, "fsgnjx.d {reg1}, {reg2}, {reg3}"), "FSgnjxD");
}

TEST_F(AssemblerRISCV64Test, FMinS) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMinS, "fmin.s {reg1}, {reg2}, {reg3}"), "FMinS");
}

TEST_F(AssemblerRISCV64Test, FMinD) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMinD, "fmin.d {reg1}, {reg2}, {reg3}"), "FMinD");
}

TEST_F(AssemblerRISCV64Test, FMaxS) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMaxS, "fmax.s {reg1}, {reg2}, {reg3}"), "FMaxS");
}

TEST_F(AssemblerRISCV64Test, FMaxD) {
  DriverStr(RepeatFFF(&Riscv64Assembler::FMaxD, "fmax.d {reg1}, {reg2}, {reg3}"), "FMaxD");
}

TEST_F(AssemblerRISCV64Test, FCvtSD) {
  DriverStr(RepeatFFRoundingMode(&Riscv64Assembler::FCvtSD, "fcvt.s.d {reg1}, {reg2}, {rm}"),
            "FCvtSD");
}

TEST_F(AssemblerRISCV64Test, FCvtSD_Default) {
  DriverStr(RepeatFF(&Riscv64Assembler::FCvtSD, "fcvt.s.d {reg1}, {reg2}"), "FCvtSD_Default");
}

// This conversion is lossless, so the rounding mode is meaningless and the assembler we're
// testing against does not even accept the rounding mode argument, so this test is disabled.
TEST_F(AssemblerRISCV64Test, DISABLED_FCvtDS) {
  DriverStr(RepeatFFRoundingMode(&Riscv64Assembler::FCvtDS, "fcvt.d.s {reg1}, {reg2}, {rm}"),
            "FCvtDS");
}

TEST_F(AssemblerRISCV64Test, FCvtDS_Default) {
  DriverStr(RepeatFF(&Riscv64Assembler::FCvtDS, "fcvt.d.s {reg1}, {reg2}"), "FCvtDS_Default");
}

TEST_F(AssemblerRISCV64Test, FEqS) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FEqS, "feq.s {reg1}, {reg2}, {reg3}"), "FEqS");
}

TEST_F(AssemblerRISCV64Test, FEqD) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FEqD, "feq.d {reg1}, {reg2}, {reg3}"), "FEqD");
}

TEST_F(AssemblerRISCV64Test, FLtS) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FLtS, "flt.s {reg1}, {reg2}, {reg3}"), "FLtS");
}

TEST_F(AssemblerRISCV64Test, FLtD) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FLtD, "flt.d {reg1}, {reg2}, {reg3}"), "FLtD");
}

TEST_F(AssemblerRISCV64Test, FLeS) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FLeS, "fle.s {reg1}, {reg2}, {reg3}"), "FLeS");
}

TEST_F(AssemblerRISCV64Test, FLeD) {
  DriverStr(RepeatRFF(&Riscv64Assembler::FLeD, "fle.d {reg1}, {reg2}, {reg3}"), "FLeD");
}

TEST_F(AssemblerRISCV64Test, FCvtWS) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtWS, "fcvt.w.s {reg1}, {reg2}, {rm}"),
            "FCvtWS");
}

TEST_F(AssemblerRISCV64Test, FCvtWS_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtWS, "fcvt.w.s {reg1}, {reg2}"), "FCvtWS_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtWD) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtWD, "fcvt.w.d {reg1}, {reg2}, {rm}"),
            "FCvtWD");
}

TEST_F(AssemblerRISCV64Test, FCvtWD_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtWD, "fcvt.w.d {reg1}, {reg2}"), "FCvtWD_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtWuS) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtWuS, "fcvt.wu.s {reg1}, {reg2}, {rm}"),
            "FCvtWuS");
}

TEST_F(AssemblerRISCV64Test, FCvtWuS_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtWuS, "fcvt.wu.s {reg1}, {reg2}"), "FCvtWuS_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtWuD) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtWuD, "fcvt.wu.d {reg1}, {reg2}, {rm}"),
            "FCvtWuD");
}

TEST_F(AssemblerRISCV64Test, FCvtWuD_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtWuD, "fcvt.wu.d {reg1}, {reg2}"), "FCvtWuD_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtLS) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtLS, "fcvt.l.s {reg1}, {reg2}, {rm}"),
            "FCvtLS");
}

TEST_F(AssemblerRISCV64Test, FCvtLS_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtLS, "fcvt.l.s {reg1}, {reg2}"), "FCvtLS_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtLD) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtLD, "fcvt.l.d {reg1}, {reg2}, {rm}"),
            "FCvtLD");
}

TEST_F(AssemblerRISCV64Test, FCvtLD_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtLD, "fcvt.l.d {reg1}, {reg2}"), "FCvtLD_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtLuS) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtLuS, "fcvt.lu.s {reg1}, {reg2}, {rm}"),
            "FCvtLuS");
}

TEST_F(AssemblerRISCV64Test, FCvtLuS_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtLuS, "fcvt.lu.s {reg1}, {reg2}"), "FCvtLuS_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtLuD) {
  DriverStr(RepeatrFRoundingMode(&Riscv64Assembler::FCvtLuD, "fcvt.lu.d {reg1}, {reg2}, {rm}"),
            "FCvtLuD");
}

TEST_F(AssemblerRISCV64Test, FCvtLuD_Default) {
  DriverStr(RepeatrF(&Riscv64Assembler::FCvtLuD, "fcvt.lu.d {reg1}, {reg2}"), "FCvtLuD_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtSW) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtSW, "fcvt.s.w {reg1}, {reg2}, {rm}"),
            "FCvtSW");
}

TEST_F(AssemblerRISCV64Test, FCvtSW_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtSW, "fcvt.s.w {reg1}, {reg2}"), "FCvtSW_Default");
}

// This conversion is lossless, so the rounding mode is meaningless and the assembler we're
// testing against does not even accept the rounding mode argument, so this test is disabled.
TEST_F(AssemblerRISCV64Test, DISABLED_FCvtDW) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtDW, "fcvt.d.w {reg1}, {reg2}, {rm}"),
            "FCvtDW");
}

TEST_F(AssemblerRISCV64Test, FCvtDW_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtDW, "fcvt.d.w {reg1}, {reg2}"), "FCvtDW_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtSWu) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtSWu, "fcvt.s.wu {reg1}, {reg2}, {rm}"),
            "FCvtSWu");
}

TEST_F(AssemblerRISCV64Test, FCvtSWu_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtSWu, "fcvt.s.wu {reg1}, {reg2}"), "FCvtSWu_Default");
}

// This conversion is lossless, so the rounding mode is meaningless and the assembler we're
// testing against does not even accept the rounding mode argument, so this test is disabled.
TEST_F(AssemblerRISCV64Test, DISABLED_FCvtDWu) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtDWu, "fcvt.d.wu {reg1}, {reg2}, {rm}"),
            "FCvtDWu");
}

TEST_F(AssemblerRISCV64Test, FCvtDWu_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtDWu, "fcvt.d.wu {reg1}, {reg2}"), "FCvtDWu_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtSL) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtSL, "fcvt.s.l {reg1}, {reg2}, {rm}"),
            "FCvtSL");
}

TEST_F(AssemblerRISCV64Test, FCvtSL_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtSL, "fcvt.s.l {reg1}, {reg2}"), "FCvtSL_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtDL) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtDL, "fcvt.d.l {reg1}, {reg2}, {rm}"),
            "FCvtDL");
}

TEST_F(AssemblerRISCV64Test, FCvtDL_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtDL, "fcvt.d.l {reg1}, {reg2}"), "FCvtDL_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtSLu) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtSLu, "fcvt.s.lu {reg1}, {reg2}, {rm}"),
            "FCvtSLu");
}

TEST_F(AssemblerRISCV64Test, FCvtSLu_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtSLu, "fcvt.s.lu {reg1}, {reg2}"), "FCvtSLu_Default");
}

TEST_F(AssemblerRISCV64Test, FCvtDLu) {
  DriverStr(RepeatFrRoundingMode(&Riscv64Assembler::FCvtDLu, "fcvt.d.lu {reg1}, {reg2}, {rm}"),
            "FCvtDLu");
}

TEST_F(AssemblerRISCV64Test, FCvtDLu_Default) {
  DriverStr(RepeatFr(&Riscv64Assembler::FCvtDLu, "fcvt.d.lu {reg1}, {reg2}"), "FCvtDLu_Default");
}

TEST_F(AssemblerRISCV64Test, FMvXW) {
  DriverStr(RepeatRF(&Riscv64Assembler::FMvXW, "fmv.x.w {reg1}, {reg2}"), "FMvXW");
}

TEST_F(AssemblerRISCV64Test, FMvXD) {
  DriverStr(RepeatRF(&Riscv64Assembler::FMvXD, "fmv.x.d {reg1}, {reg2}"), "FMvXD");
}

TEST_F(AssemblerRISCV64Test, FMvWX) {
  DriverStr(RepeatFR(&Riscv64Assembler::FMvWX, "fmv.w.x {reg1}, {reg2}"), "FMvWX");
}

TEST_F(AssemblerRISCV64Test, FMvDX) {
  DriverStr(RepeatFR(&Riscv64Assembler::FMvDX, "fmv.d.x {reg1}, {reg2}"), "FMvDX");
}

TEST_F(AssemblerRISCV64Test, FClassS) {
  DriverStr(RepeatRF(&Riscv64Assembler::FClassS, "fclass.s {reg1}, {reg2}"), "FClassS");
}

TEST_F(AssemblerRISCV64Test, FClassD) {
  DriverStr(RepeatrF(&Riscv64Assembler::FClassD, "fclass.d {reg1}, {reg2}"), "FClassD");
}

TEST_F(AssemblerRISCV64Test, CLwsp) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CLwsp,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/6,
                        /*shift=*/2,
                        "c.lwsp {reg}, {imm}(sp)"),
            "CLwsp");
}

TEST_F(AssemblerRISCV64Test, CLdsp) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CLdsp,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/6,
                        /*shift=*/3,
                        "c.ldsp {reg}, {imm}(sp)"),
            "CLdsp");
}

TEST_F(AssemblerRISCV64Test, CFLdsp) {
  DriverStr(RepeatCFImm(
                &Riscv64Assembler::CFLdsp, /*imm_bits=*/6, /*shift=*/3, "c.fldsp {reg}, {imm}(sp)"),
            "CFLdsp");
}

TEST_F(AssemblerRISCV64Test, CSwsp) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CSwsp,
                        /*is_short=*/false,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/6,
                        /*shift=*/2,
                        "c.swsp {reg}, {imm}(sp)"),
            "CLwsp");
}

TEST_F(AssemblerRISCV64Test, CSdsp) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CSdsp,
                        /*is_short=*/false,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/6,
                        /*shift=*/3,
                        "c.sdsp {reg}, {imm}(sp)"),
            "CLdsp");
}

TEST_F(AssemblerRISCV64Test, CFSdsp) {
  DriverStr(RepeatCFImm(
                &Riscv64Assembler::CFSdsp, /*imm_bits=*/6, /*shift=*/3, "c.fsdsp {reg}, {imm}(sp)"),
            "CFLdsp");
}

TEST_F(AssemblerRISCV64Test, CLw) {
  DriverStr(RepeatCRRImm(
                &Riscv64Assembler::CLw, /*imm_bits=*/5, /*shift=*/2, "c.lw {reg1}, {imm}({reg2})"),
            "CLw");
}

TEST_F(AssemblerRISCV64Test, CLd) {
  DriverStr(RepeatCRRImm(
                &Riscv64Assembler::CLd, /*imm_bits=*/5, /*shift=*/3, "c.ld {reg1}, {imm}({reg2})"),
            "CLd");
}

TEST_F(AssemblerRISCV64Test, CFLd) {
  DriverStr(RepeatCFRImm(&Riscv64Assembler::CFLd,
                         /*imm_bits=*/5,
                         /*shift=*/3,
                         "c.fld {reg1}, {imm}({reg2})"),
            "CFLd");
}

TEST_F(AssemblerRISCV64Test, CSw) {
  DriverStr(RepeatCRRImm(
                &Riscv64Assembler::CSw, /*imm_bits=*/5, /*shift=*/2, "c.sw {reg1}, {imm}({reg2})"),
            "CSw");
}

TEST_F(AssemblerRISCV64Test, CSd) {
  DriverStr(RepeatCRRImm(
                &Riscv64Assembler::CSd, /*imm_bits=*/5, /*shift=*/3, "c.sd {reg1}, {imm}({reg2})"),
            "CSd");
}

TEST_F(AssemblerRISCV64Test, CFSd) {
  DriverStr(RepeatCFRImm(&Riscv64Assembler::CFSd,
                         /*imm_bits=*/5,
                         /*shift=*/3,
                         "c.fsd {reg1}, {imm}({reg2})"),
            "CFSd");
}

TEST_F(AssemblerRISCV64Test, CLi) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CLi,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/-6,
                        /*shift=*/0,
                        "c.li {reg}, {imm}"),
            "CLi");
}

TEST_F(AssemblerRISCV64Test, CLui) {
  std::string str;
  auto imms = CreateImmediateValuesBits(/*imm_bits=*/5, /*as_uint=*/true);
  for (uint32_t v = 0xfffe0; v <= 0xfffff; ++v) {
    imms.push_back(v);
  }

  for (XRegister reg : GetRegisters()) {
    for (int64_t imm_raw : imms) {
      if (imm_raw == 0) {
        continue;
      }

      if (reg == Zero || reg == SP) {
        continue;
      }

      uint32_t imm = CreateImmediate(imm_raw);
      GetAssembler()->CLui(reg, imm);

      std::string base = "c.lui {reg}, {imm}";
      ReplaceReg(REG_TOKEN, GetRegisterName(reg), &base);
      ReplaceImm(imm, /*bias=*/0, /*multiplier=*/1, &base);
      str += base;
      str += "\n";
    }
  }

  DriverStr(str, "CLui");
}

TEST_F(AssemblerRISCV64Test, CAddi) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CAddi,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/true,
                        /*imm_bits=*/-6,
                        /*shift=*/0,
                        "c.addi {reg}, {imm}"),
            "CAddi");
}

TEST_F(AssemblerRISCV64Test, CAddiw) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CAddiw,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/-6,
                        /*shift=*/0,
                        "c.addiw {reg}, {imm}"),
            "CAddiw");
}

TEST_F(AssemblerRISCV64Test, CAddi16Sp) {
  DriverStr(RepeatImm(&Riscv64Assembler::CAddi16Sp,
                      /*no_zero_imm=*/true,
                      /*imm_bits=*/-6,
                      /*shift=*/4,
                      "c.addi16sp sp, {imm}"),
            "CAddi16Sp");
}

TEST_F(AssemblerRISCV64Test, CAddi4Spn) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CAddi4Spn,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/true,
                        /*imm_bits=*/8,
                        /*shift=*/2,
                        "c.addi4spn {reg}, sp, {imm}"),
            "CAddi4Spn");
}

TEST_F(AssemblerRISCV64Test, CSlli) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CSlli,
                        /*is_short=*/false,
                        /*no_zero_reg=*/true,
                        /*no_zero_imm=*/true,
                        /*imm_bits=*/6,
                        /*shift=*/0,
                        "c.slli {reg}, {imm}"),
            "CSlli");
}

TEST_F(AssemblerRISCV64Test, CSRli) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CSrli,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/true,
                        /*imm_bits=*/6,
                        /*shift=*/0,
                        "c.srli {reg}, {imm}"),
            "CSRli");
}

TEST_F(AssemblerRISCV64Test, CSRai) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CSrai,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/true,
                        /*imm_bits=*/6,
                        /*shift=*/0,
                        "c.srai {reg}, {imm}"),
            "CSRai");
}

TEST_F(AssemblerRISCV64Test, CAndi) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CAndi,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/-6,
                        /*shift=*/0,
                        "c.andi {reg}, {imm}"),
            "CAndi");
}

TEST_F(AssemblerRISCV64Test, CMv) {
  DriverStr(RepeatCRRNonZero(&Riscv64Assembler::CMv, "c.mv {reg1}, {reg2}"), "CMv");
}

TEST_F(AssemblerRISCV64Test, CAdd) {
  DriverStr(RepeatCRRNonZero(&Riscv64Assembler::CAdd, "c.add {reg1}, {reg2}"), "CAdd");
}

TEST_F(AssemblerRISCV64Test, CAnd) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CAnd, "c.and {reg1}, {reg2}"), "CAnd");
}

TEST_F(AssemblerRISCV64Test, COr) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::COr, "c.or {reg1}, {reg2}"), "COr");
}

TEST_F(AssemblerRISCV64Test, CXor) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CXor, "c.xor {reg1}, {reg2}"), "CXor");
}

TEST_F(AssemblerRISCV64Test, CSub) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CSub, "c.sub {reg1}, {reg2}"), "CSub");
}

TEST_F(AssemblerRISCV64Test, CAddw) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CAddw, "c.addw {reg1}, {reg2}"), "CAddw");
}

TEST_F(AssemblerRISCV64Test, CSubw) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CSubw, "c.subw {reg1}, {reg2}"), "CSubw");
}

TEST_F(AssemblerRISCV64Test, CLbu) {
  DriverStr(RepeatCRRImm(&Riscv64Assembler::CLbu,
                         /*imm_bits=*/2,
                         /*shift=*/0,
                         "c.lbu {reg1}, {imm}({reg2})"),
            "CLbu");
}

TEST_F(AssemblerRISCV64Test, CLhu) {
  DriverStr(RepeatCRRImm(&Riscv64Assembler::CLhu,
                         /*imm_bits=*/1,
                         /*shift=*/1,
                         "c.lhu {reg1}, {imm}({reg2})"),
            "CLhu");
}

TEST_F(AssemblerRISCV64Test, CLh) {
  DriverStr(RepeatCRRImm(&Riscv64Assembler::CLh,
                         /*imm_bits=*/1,
                         /*shift=*/1,
                         "c.lh {reg1}, {imm}({reg2})"),
            "CLh");
}

TEST_F(AssemblerRISCV64Test, CSb) {
  DriverStr(RepeatCRRImm(&Riscv64Assembler::CSb,
                         /*imm_bits=*/2,
                         /*shift=*/0,
                         "c.sb {reg1}, {imm}({reg2})"),
            "CSb");
}

TEST_F(AssemblerRISCV64Test, CSh) {
  DriverStr(RepeatCRRImm(&Riscv64Assembler::CSh,
                         /*imm_bits=*/1,
                         /*shift=*/1,
                         "c.sh {reg1}, {imm}({reg2})"),
            "CSh");
}

TEST_F(AssemblerRISCV64Test, CZextB) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CZextB, "c.zext.b {reg}"), "CZextB");
}

TEST_F(AssemblerRISCV64Test, CSextB) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CSextB, "c.sext.b {reg}"), "CSextB");
}

TEST_F(AssemblerRISCV64Test, CZextH) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CZextH, "c.zext.h {reg}"), "CZextH");
}

TEST_F(AssemblerRISCV64Test, CSextH) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CSextH, "c.sext.h {reg}"), "CSextH");
}

TEST_F(AssemblerRISCV64Test, CZextW) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CZextW, "c.zext.w {reg}"), "CZextW");
}

TEST_F(AssemblerRISCV64Test, CNot) {
  DriverStr(RepeatCRShort(&Riscv64Assembler::CNot, "c.not {reg}"), "CNot");
}

TEST_F(AssemblerRISCV64Test, CMul) {
  DriverStr(RepeatCRRShort(&Riscv64Assembler::CMul, "c.mul {reg1}, {reg2}"), "CMul");
}

TEST_F(AssemblerRISCV64Test, CJ) {
  DriverStr(
      RepeatImm(
          &Riscv64Assembler::CJ, /*no_zero_imm=*/false, /*imm_bits=*/-11, /*shift=*/1, "c.j {imm}"),
      "CJ");
}

TEST_F(AssemblerRISCV64Test, CJr) {
  DriverStr(RepeatRNoZero(&Riscv64Assembler::CJr, "c.jr {reg}"), "CJr");
}

TEST_F(AssemblerRISCV64Test, CJalr) {
  DriverStr(RepeatRNoZero(&Riscv64Assembler::CJalr, "c.jalr {reg}"), "CJalr");
}

TEST_F(AssemblerRISCV64Test, CBeqz) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CBeqz,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/-8,
                        /*shift=*/1,
                        "c.beqz {reg}, {imm}"),
            "CBeqz");
}

TEST_F(AssemblerRISCV64Test, CBnez) {
  DriverStr(RepeatCRImm(&Riscv64Assembler::CBnez,
                        /*is_short=*/true,
                        /*no_zero_reg=*/false,
                        /*no_zero_imm=*/false,
                        /*imm_bits=*/-8,
                        /*shift=*/1,
                        "c.bnez {reg}, {imm}"),
            "CBnez");
}

TEST_F(AssemblerRISCV64Test, CEbreak) {
  __ CEbreak();
  DriverStr("c.ebreak", "CEbreak");
}

TEST_F(AssemblerRISCV64Test, CNop) {
  __ CNop();
  DriverStr("c.nop", "CNop");
}

TEST_F(AssemblerRISCV64Test, CUnimp) {
  __ CUnimp();
  DriverStr("c.unimp", "CUnimp");
}

TEST_F(AssemblerRISCV64Test, AddUw) {
  ScopedCSuppression scs(this);  // Avoid `c.zext.w`.
  DriverStr(RepeatRRR(&Riscv64Assembler::AddUw, "add.uw {reg1}, {reg2}, {reg3}"), "AddUw");
}

TEST_F(AssemblerRISCV64Test, Sh1Add) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh1Add, "sh1add {reg1}, {reg2}, {reg3}"), "Sh1Add");
}

TEST_F(AssemblerRISCV64Test, Sh1AddUw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh1AddUw, "sh1add.uw {reg1}, {reg2}, {reg3}"), "Sh1AddUw");
}

TEST_F(AssemblerRISCV64Test, Sh2Add) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh2Add, "sh2add {reg1}, {reg2}, {reg3}"), "Sh2Add");
}

TEST_F(AssemblerRISCV64Test, Sh2AddUw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh2AddUw, "sh2add.uw {reg1}, {reg2}, {reg3}"), "Sh2AddUw");
}

TEST_F(AssemblerRISCV64Test, Sh3Add) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh3Add, "sh3add {reg1}, {reg2}, {reg3}"), "Sh3Add");
}

TEST_F(AssemblerRISCV64Test, Sh3AddUw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Sh3AddUw, "sh3add.uw {reg1}, {reg2}, {reg3}"), "Sh3AddUw");
}

TEST_F(AssemblerRISCV64Test, SlliUw) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::SlliUw, 6, "slli.uw {reg1}, {reg2}, {imm}"), "SlliUw");
}

TEST_F(AssemblerRISCV64Test, Andn) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Andn, "andn {reg1}, {reg2}, {reg3}"), "Andn");
}

TEST_F(AssemblerRISCV64Test, Orn) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Orn, "orn {reg1}, {reg2}, {reg3}"), "Orn");
}

TEST_F(AssemblerRISCV64Test, Xnor) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Xnor, "xnor {reg1}, {reg2}, {reg3}"), "Xnor");
}

TEST_F(AssemblerRISCV64Test, Clz) {
  DriverStr(RepeatRR(&Riscv64Assembler::Clz, "clz {reg1}, {reg2}"), "Clz");
}

TEST_F(AssemblerRISCV64Test, Clzw) {
  DriverStr(RepeatRR(&Riscv64Assembler::Clzw, "clzw {reg1}, {reg2}"), "Clzw");
}

TEST_F(AssemblerRISCV64Test, Ctz) {
  DriverStr(RepeatRR(&Riscv64Assembler::Ctz, "ctz {reg1}, {reg2}"), "Ctz");
}

TEST_F(AssemblerRISCV64Test, Ctzw) {
  DriverStr(RepeatRR(&Riscv64Assembler::Ctzw, "ctzw {reg1}, {reg2}"), "Ctzw");
}

TEST_F(AssemblerRISCV64Test, Cpop) {
  DriverStr(RepeatRR(&Riscv64Assembler::Cpop, "cpop {reg1}, {reg2}"), "Cpop");
}

TEST_F(AssemblerRISCV64Test, Cpopw) {
  DriverStr(RepeatRR(&Riscv64Assembler::Cpopw, "cpopw {reg1}, {reg2}"), "Cpopw");
}

TEST_F(AssemblerRISCV64Test, Min) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Min, "min {reg1}, {reg2}, {reg3}"), "Min");
}

TEST_F(AssemblerRISCV64Test, Minu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Minu, "minu {reg1}, {reg2}, {reg3}"), "Minu");
}

TEST_F(AssemblerRISCV64Test, Max) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Max, "max {reg1}, {reg2}, {reg3}"), "Max");
}

TEST_F(AssemblerRISCV64Test, Maxu) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Maxu, "maxu {reg1}, {reg2}, {reg3}"), "Maxu");
}

TEST_F(AssemblerRISCV64Test, Rol) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Rol, "rol {reg1}, {reg2}, {reg3}"), "Rol");
}

TEST_F(AssemblerRISCV64Test, Rolw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Rolw, "rolw {reg1}, {reg2}, {reg3}"), "Rolw");
}

TEST_F(AssemblerRISCV64Test, Ror) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Ror, "ror {reg1}, {reg2}, {reg3}"), "Ror");
}

TEST_F(AssemblerRISCV64Test, Rorw) {
  DriverStr(RepeatRRR(&Riscv64Assembler::Rorw, "rorw {reg1}, {reg2}, {reg3}"), "Rorw");
}

TEST_F(AssemblerRISCV64Test, Rori) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Rori, 6, "rori {reg1}, {reg2}, {imm}"), "Rori");
}

TEST_F(AssemblerRISCV64Test, Roriw) {
  DriverStr(RepeatRRIb(&Riscv64Assembler::Roriw, /*imm_bits=*/ 5, "roriw {reg1}, {reg2}, {imm}"),
            "Roriw");
}

TEST_F(AssemblerRISCV64Test, OrcB) {
  DriverStr(RepeatRR(&Riscv64Assembler::OrcB, "orc.b {reg1}, {reg2}"), "OrcB");
}

TEST_F(AssemblerRISCV64Test, Rev8) {
  DriverStr(RepeatRR(&Riscv64Assembler::Rev8, "rev8 {reg1}, {reg2}"), "Rev8");
}

TEST_F(AssemblerRISCV64Test, ZbbSextB) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZbbSextB, "sext.b {reg1}, {reg2}"), "ZbbSextB");
}

TEST_F(AssemblerRISCV64Test, ZbbSextH) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZbbSextH, "sext.h {reg1}, {reg2}"), "ZbbSextH");
}

TEST_F(AssemblerRISCV64Test, ZbbZextH) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZbbZextH, "zext.h {reg1}, {reg2}"), "ZbbZextH");
}

// Vector Instructions

TEST_F(AssemblerRISCV64Test, VSetvl) {
  DriverStr(RepeatRRR(&Riscv64Assembler::VSetvl, "vsetvl {reg1}, {reg2}, {reg3}"), "VSetvl");
}

TEST_F(AssemblerRISCV64Test, VSetivli) {
  auto replacer = [=](uint32_t uimm, std::string* s) {
    ReplaceImm(uimm, /*bias=*/ 0, /*multiplier=*/ 1, s);
  };

  std::vector<int64_t> imms = CreateImmediateValuesBits(5, true);

  DriverStr(TestVSetI(&Riscv64Assembler::VSetivli,
                      imms,
                      replacer,
                      "vsetivli {reg1}, {imm}, {sew}, {lmul}, {vta}, {vma}"),
            "VSetivli");
}

TEST_F(AssemblerRISCV64Test, VSetvli) {
  auto replacer = [=](XRegister reg, std::string* s) {
    ReplaceReg(REG2_TOKEN, GetRegisterName(reg), s);
  };

  DriverStr(TestVSetI(&Riscv64Assembler::VSetvli,
                      GetRegisters(),
                      replacer,
                      "vsetvli {reg1}, {reg2}, {sew}, {lmul}, {vta}, {vma}"),
            "VSetvli");
}

TEST_F(AssemblerRISCV64Test, VLe8) {
  DriverStr(RepeatVRVmFiltered(
                &Riscv64Assembler::VLe8, "vle8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
            "VLe8");
}

TEST_F(AssemblerRISCV64Test, VLe16) {
  DriverStr(RepeatVRVmFiltered(
                &Riscv64Assembler::VLe16, "vle16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
            "VLe16");
}

TEST_F(AssemblerRISCV64Test, VLe32) {
  DriverStr(RepeatVRVmFiltered(
                &Riscv64Assembler::VLe32, "vle32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
            "VLe32");
}

TEST_F(AssemblerRISCV64Test, VLe64) {
  DriverStr(RepeatVRVmFiltered(
                &Riscv64Assembler::VLe64, "vle64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
            "VLe64");
}

TEST_F(AssemblerRISCV64Test, VLm) {
  DriverStr(RepeatVR(&Riscv64Assembler::VLm, "vlm.v {reg1}, ({reg2})"), "VLm");
}

TEST_F(AssemblerRISCV64Test, VSe8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSe8, "vse8.v {reg1}, ({reg2}){vm}"), "VSe8");
}

TEST_F(AssemblerRISCV64Test, VSe16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSe16, "vse16.v {reg1}, ({reg2}){vm}"), "VSe16");
}

TEST_F(AssemblerRISCV64Test, VSe32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSe32, "vse32.v {reg1}, ({reg2}){vm}"), "VSe32");
}

TEST_F(AssemblerRISCV64Test, VSe64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSe64, "vse64.v {reg1}, ({reg2}){vm}"), "VSe64");
}

TEST_F(AssemblerRISCV64Test, VSm) {
  DriverStr(RepeatVR(&Riscv64Assembler::VSm, "vsm.v {reg1}, ({reg2})"), "VSm");
}

TEST_F(AssemblerRISCV64Test, VLe8ff) {
  DriverStr(RepeatVR(&Riscv64Assembler::VLe8ff, "vle8ff.v {reg1}, ({reg2})"), "VLe8ff");
}

TEST_F(AssemblerRISCV64Test, VLe16ff) {
  DriverStr(RepeatVR(&Riscv64Assembler::VLe16ff, "vle16ff.v {reg1}, ({reg2})"), "VLe16ff");
}

TEST_F(AssemblerRISCV64Test, VLe32ff) {
  DriverStr(RepeatVR(&Riscv64Assembler::VLe32ff, "vle32ff.v {reg1}, ({reg2})"), "VLe32ff");
}

TEST_F(AssemblerRISCV64Test, VLe64ff) {
  DriverStr(RepeatVR(&Riscv64Assembler::VLe64ff, "vle64ff.v {reg1}, ({reg2})"), "VLe64ff");
}

TEST_F(AssemblerRISCV64Test, VLse8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLse8,
                                "vlse8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLse8");
}

TEST_F(AssemblerRISCV64Test, VLse16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLse16,
                                "vlse16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLse16");
}

TEST_F(AssemblerRISCV64Test, VLse32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLse32,
                                "vlse32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLse32");
}

TEST_F(AssemblerRISCV64Test, VLse64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLse64,
                                "vlse64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLse64");
}

TEST_F(AssemblerRISCV64Test, VSse8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSse8, "vsse8.v {reg1}, ({reg2}), {reg3}{vm}"), "VSse8");
}

TEST_F(AssemblerRISCV64Test, VSse16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSse16, "vsse16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSse16");
}

TEST_F(AssemblerRISCV64Test, VSse32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSse32, "vsse32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSse32");
}

TEST_F(AssemblerRISCV64Test, VSse64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSse64, "vsse64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSse64");
}

TEST_F(AssemblerRISCV64Test, VLoxei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxei8,
                                "vloxei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxei8");
}

TEST_F(AssemblerRISCV64Test, VLoxei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxei16,
                                "vloxei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxei16");
}

TEST_F(AssemblerRISCV64Test, VLoxei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxei32,
                                "vloxei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxei32");
}

TEST_F(AssemblerRISCV64Test, VLoxei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxei64,
                                "vloxei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxei64");
}

TEST_F(AssemblerRISCV64Test, VLuxei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxei8,
                                "vluxei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxei8");
}

TEST_F(AssemblerRISCV64Test, VLuxei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxei16,
                                "vluxei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxei16");
}

TEST_F(AssemblerRISCV64Test, VLuxei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxei32,
                                "vluxei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxei32");
}

TEST_F(AssemblerRISCV64Test, VLuxei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxei64,
                                "vluxei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxei64");
}

TEST_F(AssemblerRISCV64Test, VSoxei8) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSoxei8, "vsoxei8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSoxei8");
}

TEST_F(AssemblerRISCV64Test, VSoxei16) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSoxei16, "vsoxei16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSoxei16");
}

TEST_F(AssemblerRISCV64Test, VSoxei32) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSoxei32, "vsoxei32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSoxei32");
}

TEST_F(AssemblerRISCV64Test, VSoxei64) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSoxei64, "vsoxei64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSoxei64");
}

TEST_F(AssemblerRISCV64Test, VSuxei8) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSuxei8, "vsuxei8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSuxei8");
}

TEST_F(AssemblerRISCV64Test, VSuxei16) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSuxei16, "vsuxei16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSuxei16");
}

TEST_F(AssemblerRISCV64Test, VSuxei32) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSuxei32, "vsuxei32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSuxei32");
}

TEST_F(AssemblerRISCV64Test, VSuxei64) {
  DriverStr(RepeatVRVVm(&Riscv64Assembler::VSuxei64, "vsuxei64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSuxei64");
}

TEST_F(AssemblerRISCV64Test, VLseg2e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg2e8, "vlseg2e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg2e8");
}

TEST_F(AssemblerRISCV64Test, VLseg2e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg2e16, "vlseg2e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg2e16");
}

TEST_F(AssemblerRISCV64Test, VLseg2e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg2e32, "vlseg2e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg2e32");
}

TEST_F(AssemblerRISCV64Test, VLseg2e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg2e64, "vlseg2e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg2e64");
}

TEST_F(AssemblerRISCV64Test, VLseg3e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg3e8, "vlseg3e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg3e8");
}

TEST_F(AssemblerRISCV64Test, VLseg3e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg3e16, "vlseg3e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg3e16");
}

TEST_F(AssemblerRISCV64Test, VLseg3e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg3e32, "vlseg3e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg3e32");
}

TEST_F(AssemblerRISCV64Test, VLseg3e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg3e64, "vlseg3e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg3e64");
}

TEST_F(AssemblerRISCV64Test, VLseg4e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg4e8, "vlseg4e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg4e8");
}

TEST_F(AssemblerRISCV64Test, VLseg4e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg4e16, "vlseg4e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg4e16");
}

TEST_F(AssemblerRISCV64Test, VLseg4e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg4e32, "vlseg4e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg4e32");
}

TEST_F(AssemblerRISCV64Test, VLseg4e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg4e64, "vlseg4e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg4e64");
}

TEST_F(AssemblerRISCV64Test, VLseg5e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg5e8, "vlseg5e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg5e8");
}

TEST_F(AssemblerRISCV64Test, VLseg5e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg5e16, "vlseg5e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg5e16");
}

TEST_F(AssemblerRISCV64Test, VLseg5e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg5e32, "vlseg5e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg5e32");
}

TEST_F(AssemblerRISCV64Test, VLseg5e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg5e64, "vlseg5e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg5e64");
}

TEST_F(AssemblerRISCV64Test, VLseg6e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg6e8, "vlseg6e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg6e8");
}

TEST_F(AssemblerRISCV64Test, VLseg6e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg6e16, "vlseg6e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg6e16");
}

TEST_F(AssemblerRISCV64Test, VLseg6e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg6e32, "vlseg6e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg6e32");
}

TEST_F(AssemblerRISCV64Test, VLseg6e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg6e64, "vlseg6e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg6e64");
}

TEST_F(AssemblerRISCV64Test, VLseg7e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg7e8, "vlseg7e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg7e8");
}

TEST_F(AssemblerRISCV64Test, VLseg7e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg7e16, "vlseg7e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg7e16");
}

TEST_F(AssemblerRISCV64Test, VLseg7e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg7e32, "vlseg7e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg7e32");
}

TEST_F(AssemblerRISCV64Test, VLseg7e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg7e64, "vlseg7e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg7e64");
}

TEST_F(AssemblerRISCV64Test, VLseg8e8) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg8e8, "vlseg8e8.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg8e8");
}

TEST_F(AssemblerRISCV64Test, VLseg8e16) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg8e16, "vlseg8e16.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg8e16");
}

TEST_F(AssemblerRISCV64Test, VLseg8e32) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg8e32, "vlseg8e32.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg8e32");
}

TEST_F(AssemblerRISCV64Test, VLseg8e64) {
  DriverStr(
      RepeatVRVmFiltered(
          &Riscv64Assembler::VLseg8e64, "vlseg8e64.v {reg1}, ({reg2}){vm}", SkipV0Vm<XRegister>()),
      "VLseg8e64");
}

TEST_F(AssemblerRISCV64Test, VSseg2e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg2e8, "vsseg2e8.v {reg1}, ({reg2}){vm}"), "VSseg2e8");
}

TEST_F(AssemblerRISCV64Test, VSseg2e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg2e16, "vsseg2e16.v {reg1}, ({reg2}){vm}"),
            "VSseg2e16");
}

TEST_F(AssemblerRISCV64Test, VSseg2e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg2e32, "vsseg2e32.v {reg1}, ({reg2}){vm}"),
            "VSseg2e32");
}

TEST_F(AssemblerRISCV64Test, VSseg2e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg2e64, "vsseg2e64.v {reg1}, ({reg2}){vm}"),
            "VSseg2e64");
}

TEST_F(AssemblerRISCV64Test, VSseg3e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg3e8, "vsseg3e8.v {reg1}, ({reg2}){vm}"), "VSseg3e8");
}

TEST_F(AssemblerRISCV64Test, VSseg3e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg3e16, "vsseg3e16.v {reg1}, ({reg2}){vm}"),
            "VSseg3e16");
}

TEST_F(AssemblerRISCV64Test, VSseg3e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg3e32, "vsseg3e32.v {reg1}, ({reg2}){vm}"),
            "VSseg3e32");
}

TEST_F(AssemblerRISCV64Test, VSseg3e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg3e64, "vsseg3e64.v {reg1}, ({reg2}){vm}"),
            "VSseg3e64");
}

TEST_F(AssemblerRISCV64Test, VSseg4e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg4e8, "vsseg4e8.v {reg1}, ({reg2}){vm}"), "VSseg4e8");
}

TEST_F(AssemblerRISCV64Test, VSseg4e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg4e16, "vsseg4e16.v {reg1}, ({reg2}){vm}"),
            "VSseg4e16");
}

TEST_F(AssemblerRISCV64Test, VSseg4e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg4e32, "vsseg4e32.v {reg1}, ({reg2}){vm}"),
            "VSseg4e32");
}

TEST_F(AssemblerRISCV64Test, VSseg4e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg4e64, "vsseg4e64.v {reg1}, ({reg2}){vm}"),
            "VSseg4e64");
}

TEST_F(AssemblerRISCV64Test, VSseg5e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg5e8, "vsseg5e8.v {reg1}, ({reg2}){vm}"), "VSseg5e8");
}

TEST_F(AssemblerRISCV64Test, VSseg5e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg5e16, "vsseg5e16.v {reg1}, ({reg2}){vm}"),
            "VSseg5e16");
}

TEST_F(AssemblerRISCV64Test, VSseg5e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg5e32, "vsseg5e32.v {reg1}, ({reg2}){vm}"),
            "VSseg5e32");
}

TEST_F(AssemblerRISCV64Test, VSseg5e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg5e64, "vsseg5e64.v {reg1}, ({reg2}){vm}"),
            "VSseg5e64");
}

TEST_F(AssemblerRISCV64Test, VSseg6e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg6e8, "vsseg6e8.v {reg1}, ({reg2}){vm}"), "VSseg6e8");
}

TEST_F(AssemblerRISCV64Test, VSseg6e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg6e16, "vsseg6e16.v {reg1}, ({reg2}){vm}"),
            "VSseg6e16");
}

TEST_F(AssemblerRISCV64Test, VSseg6e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg6e32, "vsseg6e32.v {reg1}, ({reg2}){vm}"),
            "VSseg6e32");
}

TEST_F(AssemblerRISCV64Test, VSseg6e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg6e64, "vsseg6e64.v {reg1}, ({reg2}){vm}"),
            "VSseg6e64");
}

TEST_F(AssemblerRISCV64Test, VSseg7e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg7e8, "vsseg7e8.v {reg1}, ({reg2}){vm}"), "VSseg7e8");
}

TEST_F(AssemblerRISCV64Test, VSseg7e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg7e16, "vsseg7e16.v {reg1}, ({reg2}){vm}"),
            "VSseg7e16");
}

TEST_F(AssemblerRISCV64Test, VSseg7e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg7e32, "vsseg7e32.v {reg1}, ({reg2}){vm}"),
            "VSseg7e32");
}

TEST_F(AssemblerRISCV64Test, VSseg7e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg7e64, "vsseg7e64.v {reg1}, ({reg2}){vm}"),
            "VSseg7e64");
}

TEST_F(AssemblerRISCV64Test, VSseg8e8) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg8e8, "vsseg8e8.v {reg1}, ({reg2}){vm}"), "VSseg8e8");
}

TEST_F(AssemblerRISCV64Test, VSseg8e16) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg8e16, "vsseg8e16.v {reg1}, ({reg2}){vm}"),
            "VSseg8e16");
}

TEST_F(AssemblerRISCV64Test, VSseg8e32) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg8e32, "vsseg8e32.v {reg1}, ({reg2}){vm}"),
            "VSseg8e32");
}

TEST_F(AssemblerRISCV64Test, VSseg8e64) {
  DriverStr(RepeatVRVm(&Riscv64Assembler::VSseg8e64, "vsseg8e64.v {reg1}, ({reg2}){vm}"),
            "VSseg8e64");
}

TEST_F(AssemblerRISCV64Test, VLseg2e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg2e8ff,
                               "vlseg2e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg2e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg2e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg2e16ff,
                               "vlseg2e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg2e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg2e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg2e32ff,
                               "vlseg2e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg2e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg2e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg2e64ff,
                               "vlseg2e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg2e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg3e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg3e8ff,
                               "vlseg3e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg3e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg3e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg3e16ff,
                               "vlseg3e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg3e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg3e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg3e32ff,
                               "vlseg3e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg3e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg3e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg3e64ff,
                               "vlseg3e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg3e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg4e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg4e8ff,
                               "vlseg4e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg4e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg4e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg4e16ff,
                               "vlseg4e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg4e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg4e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg4e32ff,
                               "vlseg4e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg4e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg4e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg4e64ff,
                               "vlseg4e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg4e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg5e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg5e8ff,
                               "vlseg5e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg5e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg5e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg5e16ff,
                               "vlseg5e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg5e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg5e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg5e32ff,
                               "vlseg5e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg5e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg5e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg5e64ff,
                               "vlseg5e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg5e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg6e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg6e8ff,
                               "vlseg6e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg6e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg6e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg6e16ff,
                               "vlseg6e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg6e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg6e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg6e32ff,
                               "vlseg6e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg6e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg6e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg6e64ff,
                               "vlseg6e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg6e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg7e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg7e8ff,
                               "vlseg7e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg7e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg7e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg7e16ff,
                               "vlseg7e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg7e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg7e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg7e32ff,
                               "vlseg7e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg7e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg7e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg7e64ff,
                               "vlseg7e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg7e64ff");
}

TEST_F(AssemblerRISCV64Test, VLseg8e8ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg8e8ff,
                               "vlseg8e8ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg8e8ff");
}

TEST_F(AssemblerRISCV64Test, VLseg8e16ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg8e16ff,
                               "vlseg8e16ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg8e16ff");
}

TEST_F(AssemblerRISCV64Test, VLseg8e32ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg8e32ff,
                               "vlseg8e32ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg8e32ff");
}

TEST_F(AssemblerRISCV64Test, VLseg8e64ff) {
  DriverStr(RepeatVRVmFiltered(&Riscv64Assembler::VLseg8e64ff,
                               "vlseg8e64ff.v {reg1}, ({reg2}){vm}",
                               SkipV0Vm<XRegister>()),
            "VLseg8e64ff");
}

TEST_F(AssemblerRISCV64Test, VLsseg2e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg2e8,
                                "vlsseg2e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg2e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg2e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg2e16,
                                "vlsseg2e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg2e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg2e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg2e32,
                                "vlsseg2e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg2e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg2e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg2e64,
                                "vlsseg2e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg2e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg3e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg3e8,
                                "vlsseg3e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg3e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg3e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg3e16,
                                "vlsseg3e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg3e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg3e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg3e32,
                                "vlsseg3e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg3e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg3e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg3e64,
                                "vlsseg3e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg3e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg4e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg4e8,
                                "vlsseg4e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg4e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg4e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg4e16,
                                "vlsseg4e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg4e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg4e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg4e32,
                                "vlsseg4e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg4e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg4e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg4e64,
                                "vlsseg4e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg4e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg5e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg5e8,
                                "vlsseg5e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg5e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg5e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg5e16,
                                "vlsseg5e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg5e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg5e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg5e32,
                                "vlsseg5e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg5e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg5e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg5e64,
                                "vlsseg5e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg5e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg6e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg6e8,
                                "vlsseg6e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg6e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg6e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg6e16,
                                "vlsseg6e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg6e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg6e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg6e32,
                                "vlsseg6e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg6e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg6e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg6e64,
                                "vlsseg6e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg6e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg7e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg7e8,
                                "vlsseg7e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg7e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg7e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg7e16,
                                "vlsseg7e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg7e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg7e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg7e32,
                                "vlsseg7e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg7e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg7e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg7e64,
                                "vlsseg7e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg7e64");
}

TEST_F(AssemblerRISCV64Test, VLsseg8e8) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg8e8,
                                "vlsseg8e8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg8e8");
}

TEST_F(AssemblerRISCV64Test, VLsseg8e16) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg8e16,
                                "vlsseg8e16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg8e16");
}

TEST_F(AssemblerRISCV64Test, VLsseg8e32) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg8e32,
                                "vlsseg8e32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg8e32");
}

TEST_F(AssemblerRISCV64Test, VLsseg8e64) {
  DriverStr(RepeatVRRVmFiltered(&Riscv64Assembler::VLsseg8e64,
                                "vlsseg8e64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, XRegister>()),
            "VLsseg8e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg2e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg2e8, "vssseg2e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg2e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg2e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg2e16, "vssseg2e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg2e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg2e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg2e32, "vssseg2e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg2e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg2e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg2e64, "vssseg2e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg2e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg3e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg3e8, "vssseg3e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg3e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg3e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg3e16, "vssseg3e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg3e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg3e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg3e32, "vssseg3e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg3e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg3e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg3e64, "vssseg3e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg3e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg4e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg4e8, "vssseg4e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg4e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg4e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg4e16, "vssseg4e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg4e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg4e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg4e32, "vssseg4e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg4e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg4e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg4e64, "vssseg4e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg4e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg5e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg5e8, "vssseg5e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg5e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg5e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg5e16, "vssseg5e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg5e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg5e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg5e32, "vssseg5e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg5e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg5e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg5e64, "vssseg5e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg5e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg6e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg6e8, "vssseg6e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg6e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg6e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg6e16, "vssseg6e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg6e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg6e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg6e32, "vssseg6e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg6e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg6e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg6e64, "vssseg6e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg6e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg7e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg7e8, "vssseg7e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg7e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg7e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg7e16, "vssseg7e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg7e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg7e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg7e32, "vssseg7e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg7e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg7e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg7e64, "vssseg7e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg7e64");
}

TEST_F(AssemblerRISCV64Test, VSsseg8e8) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg8e8, "vssseg8e8.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg8e8");
}

TEST_F(AssemblerRISCV64Test, VSsseg8e16) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg8e16, "vssseg8e16.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg8e16");
}

TEST_F(AssemblerRISCV64Test, VSsseg8e32) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg8e32, "vssseg8e32.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg8e32");
}

TEST_F(AssemblerRISCV64Test, VSsseg8e64) {
  DriverStr(RepeatVRRVm(&Riscv64Assembler::VSsseg8e64, "vssseg8e64.v {reg1}, ({reg2}), {reg3}{vm}"),
            "VSsseg8e64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg2ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg2ei8,
                                "vluxseg2ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg2ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg2ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg2ei16,
                                "vluxseg2ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg2ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg2ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg2ei32,
                                "vluxseg2ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg2ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg2ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg2ei64,
                                "vluxseg2ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg2ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg3ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg3ei8,
                                "vluxseg3ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg3ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg3ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg3ei16,
                                "vluxseg3ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg3ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg3ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg3ei32,
                                "vluxseg3ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg3ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg3ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg3ei64,
                                "vluxseg3ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg3ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg4ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg4ei8,
                                "vluxseg4ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg4ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg4ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg4ei16,
                                "vluxseg4ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg4ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg4ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg4ei32,
                                "vluxseg4ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg4ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg4ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg4ei64,
                                "vluxseg4ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg4ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg5ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg5ei8,
                                "vluxseg5ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg5ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg5ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg5ei16,
                                "vluxseg5ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg5ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg5ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg5ei32,
                                "vluxseg5ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg5ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg5ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg5ei64,
                                "vluxseg5ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg5ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg6ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg6ei8,
                                "vluxseg6ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg6ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg6ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg6ei16,
                                "vluxseg6ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg6ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg6ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg6ei32,
                                "vluxseg6ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg6ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg6ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg6ei64,
                                "vluxseg6ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg6ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg7ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg7ei8,
                                "vluxseg7ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg7ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg7ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg7ei16,
                                "vluxseg7ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg7ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg7ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg7ei32,
                                "vluxseg7ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg7ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg7ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg7ei64,
                                "vluxseg7ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg7ei64");
}

TEST_F(AssemblerRISCV64Test, VLuxseg8ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg8ei8,
                                "vluxseg8ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg8ei8");
}

TEST_F(AssemblerRISCV64Test, VLuxseg8ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg8ei16,
                                "vluxseg8ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg8ei16");
}

TEST_F(AssemblerRISCV64Test, VLuxseg8ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg8ei32,
                                "vluxseg8ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg8ei32");
}

TEST_F(AssemblerRISCV64Test, VLuxseg8ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLuxseg8ei64,
                                "vluxseg8ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLuxseg8ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg2ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg2ei8, "vsuxseg2ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg2ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg2ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg2ei16, "vsuxseg2ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg2ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg2ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg2ei32, "vsuxseg2ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg2ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg2ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg2ei64, "vsuxseg2ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg2ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg3ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg3ei8, "vsuxseg3ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg3ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg3ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg3ei16, "vsuxseg3ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg3ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg3ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg3ei32, "vsuxseg3ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg3ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg3ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg3ei64, "vsuxseg3ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg3ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg4ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg4ei8, "vsuxseg4ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg4ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg4ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg4ei16, "vsuxseg4ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg4ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg4ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg4ei32, "vsuxseg4ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg4ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg4ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg4ei64, "vsuxseg4ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg4ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg5ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg5ei8, "vsuxseg5ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg5ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg5ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg5ei16, "vsuxseg5ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg5ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg5ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg5ei32, "vsuxseg5ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg5ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg5ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg5ei64, "vsuxseg5ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg5ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg6ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg6ei8, "vsuxseg6ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg6ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg6ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg6ei16, "vsuxseg6ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg6ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg6ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg6ei32, "vsuxseg6ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg6ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg6ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg6ei64, "vsuxseg6ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg6ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg7ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg7ei8, "vsuxseg7ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg7ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg7ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg7ei16, "vsuxseg7ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg7ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg7ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg7ei32, "vsuxseg7ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg7ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg7ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg7ei64, "vsuxseg7ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg7ei64");
}

TEST_F(AssemblerRISCV64Test, VSuxseg8ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg8ei8, "vsuxseg8ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg8ei8");
}

TEST_F(AssemblerRISCV64Test, VSuxseg8ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg8ei16, "vsuxseg8ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg8ei16");
}

TEST_F(AssemblerRISCV64Test, VSuxseg8ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg8ei32, "vsuxseg8ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg8ei32");
}

TEST_F(AssemblerRISCV64Test, VSuxseg8ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSuxseg8ei64, "vsuxseg8ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSuxseg8ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg2ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg2ei8,
                                "vloxseg2ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg2ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg2ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg2ei16,
                                "vloxseg2ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg2ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg2ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg2ei32,
                                "vloxseg2ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg2ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg2ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg2ei64,
                                "vloxseg2ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg2ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg3ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg3ei8,
                                "vloxseg3ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg3ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg3ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg3ei16,
                                "vloxseg3ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg3ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg3ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg3ei32,
                                "vloxseg3ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg3ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg3ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg3ei64,
                                "vloxseg3ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg3ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg4ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg4ei8,
                                "vloxseg4ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg4ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg4ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg4ei16,
                                "vloxseg4ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg4ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg4ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg4ei32,
                                "vloxseg4ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg4ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg4ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg4ei64,
                                "vloxseg4ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg4ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg5ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg5ei8,
                                "vloxseg5ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg5ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg5ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg5ei16,
                                "vloxseg5ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg5ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg5ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg5ei32,
                                "vloxseg5ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg5ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg5ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg5ei64,
                                "vloxseg5ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg5ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg6ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg6ei8,
                                "vloxseg6ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg6ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg6ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg6ei16,
                                "vloxseg6ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg6ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg6ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg6ei32,
                                "vloxseg6ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg6ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg6ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg6ei64,
                                "vloxseg6ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg6ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg7ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg7ei8,
                                "vloxseg7ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg7ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg7ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg7ei16,
                                "vloxseg7ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg7ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg7ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg7ei32,
                                "vloxseg7ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg7ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg7ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg7ei64,
                                "vloxseg7ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg7ei64");
}

TEST_F(AssemblerRISCV64Test, VLoxseg8ei8) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg8ei8,
                                "vloxseg8ei8.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg8ei8");
}

TEST_F(AssemblerRISCV64Test, VLoxseg8ei16) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg8ei16,
                                "vloxseg8ei16.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg8ei16");
}

TEST_F(AssemblerRISCV64Test, VLoxseg8ei32) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg8ei32,
                                "vloxseg8ei32.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg8ei32");
}

TEST_F(AssemblerRISCV64Test, VLoxseg8ei64) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VLoxseg8ei64,
                                "vloxseg8ei64.v {reg1}, ({reg2}), {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VLoxseg8ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg2ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg2ei8, "vsoxseg2ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg2ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg2ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg2ei16, "vsoxseg2ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg2ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg2ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg2ei32, "vsoxseg2ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg2ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg2ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg2ei64, "vsoxseg2ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg2ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg3ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg3ei8, "vsoxseg3ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg3ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg3ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg3ei16, "vsoxseg3ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg3ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg3ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg3ei32, "vsoxseg3ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg3ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg3ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg3ei64, "vsoxseg3ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg3ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg4ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg4ei8, "vsoxseg4ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg4ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg4ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg4ei16, "vsoxseg4ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg4ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg4ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg4ei32, "vsoxseg4ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg4ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg4ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg4ei64, "vsoxseg4ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg4ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg5ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg5ei8, "vsoxseg5ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg5ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg5ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg5ei16, "vsoxseg5ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg5ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg5ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg5ei32, "vsoxseg5ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg5ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg5ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg5ei64, "vsoxseg5ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg5ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg6ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg6ei8, "vsoxseg6ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg6ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg6ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg6ei16, "vsoxseg6ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg6ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg6ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg6ei32, "vsoxseg6ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg6ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg6ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg6ei64, "vsoxseg6ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg6ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg7ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg7ei8, "vsoxseg7ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg7ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg7ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg7ei16, "vsoxseg7ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg7ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg7ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg7ei32, "vsoxseg7ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg7ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg7ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg7ei64, "vsoxseg7ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg7ei64");
}

TEST_F(AssemblerRISCV64Test, VSoxseg8ei8) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg8ei8, "vsoxseg8ei8.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg8ei8");
}

TEST_F(AssemblerRISCV64Test, VSoxseg8ei16) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg8ei16, "vsoxseg8ei16.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg8ei16");
}

TEST_F(AssemblerRISCV64Test, VSoxseg8ei32) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg8ei32, "vsoxseg8ei32.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg8ei32");
}

TEST_F(AssemblerRISCV64Test, VSoxseg8ei64) {
  DriverStr(
      RepeatVRVVm(&Riscv64Assembler::VSoxseg8ei64, "vsoxseg8ei64.v {reg1}, ({reg2}), {reg3}{vm}"),
      "VSoxseg8ei64");
}

TEST_F(AssemblerRISCV64Test, VL1re8) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL1re8, /*alignment=*/ 1, "vl1re8.v {reg1}, ({reg2})"),
      "VL1re8");
}

TEST_F(AssemblerRISCV64Test, VL1re16) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL1re16, /*alignment=*/ 1, "vl1re16.v {reg1}, ({reg2})"),
      "VL1re16");
}

TEST_F(AssemblerRISCV64Test, VL1re32) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL1re32, /*alignment=*/ 1, "vl1re32.v {reg1}, ({reg2})"),
      "VL1re32");
}

TEST_F(AssemblerRISCV64Test, VL1re64) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL1re64, /*alignment=*/ 1, "vl1re64.v {reg1}, ({reg2})"),
      "VL1re64");
}

TEST_F(AssemblerRISCV64Test, VL2re8) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL2re8, /*alignment=*/ 2, "vl2re8.v {reg1}, ({reg2})"),
      "VL2re8");
}

TEST_F(AssemblerRISCV64Test, VL2re16) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL2re16, /*alignment=*/ 2, "vl2re16.v {reg1}, ({reg2})"),
      "VL2re16");
}

TEST_F(AssemblerRISCV64Test, VL2re32) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL2re32, /*alignment=*/ 2, "vl2re32.v {reg1}, ({reg2})"),
      "VL2re32");
}

TEST_F(AssemblerRISCV64Test, VL2re64) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL2re64, /*alignment=*/ 2, "vl2re64.v {reg1}, ({reg2})"),
      "VL2re64");
}

TEST_F(AssemblerRISCV64Test, VL4re8) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL4re8, /*alignment=*/ 4, "vl4re8.v {reg1}, ({reg2})"),
      "VL4re8");
}

TEST_F(AssemblerRISCV64Test, VL4re16) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL4re16, /*alignment=*/ 4, "vl4re16.v {reg1}, ({reg2})"),
      "VL4re16");
}

TEST_F(AssemblerRISCV64Test, VL4re32) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL4re32, /*alignment=*/ 4, "vl4re32.v {reg1}, ({reg2})"),
      "VL4re32");
}

TEST_F(AssemblerRISCV64Test, VL4re64) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL4re64, /*alignment=*/ 4, "vl4re64.v {reg1}, ({reg2})"),
      "VL4re64");
}

TEST_F(AssemblerRISCV64Test, VL8re8) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL8re8, /*alignment=*/ 8, "vl8re8.v {reg1}, ({reg2})"),
      "VL8re8");
}

TEST_F(AssemblerRISCV64Test, VL8re16) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL8re16, /*alignment=*/ 8, "vl8re16.v {reg1}, ({reg2})"),
      "VL8re16");
}

TEST_F(AssemblerRISCV64Test, VL8re32) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL8re32, /*alignment=*/ 8, "vl8re32.v {reg1}, ({reg2})"),
      "VL8re32");
}

TEST_F(AssemblerRISCV64Test, VL8re64) {
  DriverStr(
      RepeatVRAligned(&Riscv64Assembler::VL8re64, /*alignment=*/ 8, "vl8re64.v {reg1}, ({reg2})"),
      "VL8re64");
}

TEST_F(AssemblerRISCV64Test, VL1r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VL1r, /*alignment=*/ 1, "vl1r.v {reg1}, ({reg2})"),
            "VL1r");
}

TEST_F(AssemblerRISCV64Test, VL2r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VL2r, /*alignment=*/ 2, "vl2r.v {reg1}, ({reg2})"),
            "VL2r");
}

TEST_F(AssemblerRISCV64Test, VL4r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VL4r, /*alignment=*/ 4, "vl4r.v {reg1}, ({reg2})"),
            "VL4r");
}

TEST_F(AssemblerRISCV64Test, VL8r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VL8r, /*alignment=*/ 8, "vl8r.v {reg1}, ({reg2})"),
            "VL8r");
}

TEST_F(AssemblerRISCV64Test, VS1r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VS1r, /*alignment=*/ 1, "vs1r.v {reg1}, ({reg2})"),
            "VS1r");
}

TEST_F(AssemblerRISCV64Test, VS2r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VS2r, /*alignment=*/ 2, "vs2r.v {reg1}, ({reg2})"),
            "VS2r");
}

TEST_F(AssemblerRISCV64Test, VS4r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VS4r, /*alignment=*/ 4, "vs4r.v {reg1}, ({reg2})"),
            "VS4r");
}

TEST_F(AssemblerRISCV64Test, VS8r) {
  DriverStr(RepeatVRAligned(&Riscv64Assembler::VS8r, /*alignment=*/ 8, "vs8r.v {reg1}, ({reg2})"),
            "VS8r");
}

TEST_F(AssemblerRISCV64Test, VAdd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAdd_vv,
                                "vadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAdd_vv");
}

TEST_F(AssemblerRISCV64Test, VAdd_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAdd_vx,
                                "vadd.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAdd_vx");
}

TEST_F(AssemblerRISCV64Test, VAdd_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VAdd_vi,
                                 /*imm_bits=*/ -5,
                                 "vadd.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VAdd_vi");
}

TEST_F(AssemblerRISCV64Test, VSub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSub_vv,
                                "vsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSub_vv");
}

TEST_F(AssemblerRISCV64Test, VSub_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSub_vx,
                                "vsub.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSub_vx");
}

TEST_F(AssemblerRISCV64Test, VRsub_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VRsub_vx,
                                "vrsub.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VRsub_vx");
}

TEST_F(AssemblerRISCV64Test, VRsub_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VRsub_vi,
                                 /*imm_bits=*/ -5,
                                 "vrsub.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VRsub_vi");
}

TEST_F(AssemblerRISCV64Test, VNeg_v) {
  DriverStr(RepeatVV(&Riscv64Assembler::VNeg_v, "vneg.v {reg1}, {reg2}"), "VNeg_v");
}

TEST_F(AssemblerRISCV64Test, VMinu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMinu_vv,
                                "vminu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMinu_vv");
}

TEST_F(AssemblerRISCV64Test, VMinu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMinu_vx,
                                "vminu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMinu_vx");
}

TEST_F(AssemblerRISCV64Test, VMin_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMin_vv,
                                "vmin.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMin_vv");
}

TEST_F(AssemblerRISCV64Test, VMin_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMin_vx,
                                "vmin.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMin_vx");
}

TEST_F(AssemblerRISCV64Test, VMaxu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMaxu_vv,
                                "vmaxu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMaxu_vv");
}

TEST_F(AssemblerRISCV64Test, VMaxu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMaxu_vx,
                                "vmaxu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMaxu_vx");
}

TEST_F(AssemblerRISCV64Test, VMax_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMax_vv,
                                "vmax.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMax_vv");
}

TEST_F(AssemblerRISCV64Test, VMax_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMax_vx,
                                "vmax.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMax_vx");
}

TEST_F(AssemblerRISCV64Test, VAnd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAnd_vv,
                                "vand.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAnd_vv");
}

TEST_F(AssemblerRISCV64Test, VAnd_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAnd_vx,
                                "vand.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAnd_vx");
}

TEST_F(AssemblerRISCV64Test, VAnd_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VAnd_vi,
                                 /*imm_bits=*/ -5,
                                 "vand.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VAnd_vi");
}

TEST_F(AssemblerRISCV64Test, VOr_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VOr_vv,
                                "vor.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VOr_vv");
}

TEST_F(AssemblerRISCV64Test, VOr_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VOr_vx,
                                "vor.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VOr_vx");
}

TEST_F(AssemblerRISCV64Test, VOr_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VOr_vi,
                                 /*imm_bits=*/ -5,
                                 "vor.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VOr_vi");
}

TEST_F(AssemblerRISCV64Test, VXor_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VXor_vv,
                                "vxor.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VXor_vv");
}

TEST_F(AssemblerRISCV64Test, VXor_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VXor_vx,
                                "vxor.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VXor_vx");
}

TEST_F(AssemblerRISCV64Test, VXor_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VXor_vi,
                                 /*imm_bits=*/ -5,
                                 "vxor.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VXor_vi");
}

TEST_F(AssemblerRISCV64Test, VNot_v) {
  DriverStr(RepeatVVVmFiltered(
                &Riscv64Assembler::VNot_v, "vnot.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
            "VNot_v");
}

TEST_F(AssemblerRISCV64Test, VRgather_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VRgather_vv,
                                "vrgather.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VRgather_vv");
}

TEST_F(AssemblerRISCV64Test, VRgather_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VRgather_vx,
                                "vrgather.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VRgather_vx");
}

TEST_F(AssemblerRISCV64Test, VRgather_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VRgather_vi,
                                 /*imm_bits=*/ 5,
                                 "vrgather.vi {reg1}, {reg2}, {imm}{vm}",
                                 VXVVmSkipV0VmAndNoR1R2Overlap<uint32_t>()),
            "VRgather_vi");
}

TEST_F(AssemblerRISCV64Test, VSlideup_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSlideup_vx,
                                "vslideup.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VSlideup_vx");
}

TEST_F(AssemblerRISCV64Test, VSlideup_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSlideup_vi,
                                 /*imm_bits=*/ 5,
                                 "vslideup.vi {reg1}, {reg2}, {imm}{vm}",
                                 VXVVmSkipV0VmAndNoR1R2Overlap<uint32_t>()),
            "VSlideup_vi");
}

TEST_F(AssemblerRISCV64Test, VRgatherei16_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VRgatherei16_vv,
                                "vrgatherei16.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VRgatherei16_vv");
}

TEST_F(AssemblerRISCV64Test, VSlidedown_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSlidedown_vx,
                                "vslidedown.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VSlidedown_vx");
}

TEST_F(AssemblerRISCV64Test, VSlidedown_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSlidedown_vi,
                                 /*imm_bits=*/ 5,
                                 "vslidedown.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSlidedown_vi");
}

TEST_F(AssemblerRISCV64Test, VAdc_vvm) {
  DriverStr(RepeatVVVFiltered(&Riscv64Assembler::VAdc_vvm,
                              "vadc.vvm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, VRegister>()),
            "VAdc_vvm");
}

TEST_F(AssemblerRISCV64Test, VAdc_vxm) {
  DriverStr(RepeatVVRFiltered(&Riscv64Assembler::VAdc_vxm,
                              "vadc.vxm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, XRegister>()),
            "VAdc_vxm");
}

TEST_F(AssemblerRISCV64Test, VAdc_vim) {
  DriverStr(RepeatVVIFiltered(&Riscv64Assembler::VAdc_vim,
                              /*imm_bits=*/ -5,
                              "vadc.vim {reg1}, {reg2}, {imm}, v0",
                              SkipV0<VRegister, int32_t>()),
            "VAdc_vim");
}

TEST_F(AssemblerRISCV64Test, VMadc_vvm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMadc_vvm, "vmadc.vvm {reg1}, {reg2}, {reg3}, v0"),
            "VMadc_vvm");
}

TEST_F(AssemblerRISCV64Test, VMadc_vxm) {
  DriverStr(RepeatVVR(&Riscv64Assembler::VMadc_vxm, "vmadc.vxm {reg1}, {reg2}, {reg3}, v0"),
            "VMadc_vxm");
}

TEST_F(AssemblerRISCV64Test, VMadc_vim) {
  DriverStr(RepeatVVIb(&Riscv64Assembler::VMadc_vim,
                       /*imm_bits=*/ -5,
                       "vmadc.vim {reg1}, {reg2}, {imm}, v0"),
            "VMadc_vim");
}

TEST_F(AssemblerRISCV64Test, VMadc_vv) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMadc_vv, "vmadc.vv {reg1}, {reg2}, {reg3}"), "VMadc_vv");
}

TEST_F(AssemblerRISCV64Test, VMadc_vx) {
  DriverStr(RepeatVVR(&Riscv64Assembler::VMadc_vx, "vmadc.vx {reg1}, {reg2}, {reg3}"), "VMadc_vx");
}

TEST_F(AssemblerRISCV64Test, VMadc_vi) {
  DriverStr(RepeatVVIb(&Riscv64Assembler::VMadc_vi,
                       /*imm_bits=*/ -5,
                       "vmadc.vi {reg1}, {reg2}, {imm}"),
            "VMadc_vi");
}

TEST_F(AssemblerRISCV64Test, VSbc_vvm) {
  DriverStr(RepeatVVVFiltered(&Riscv64Assembler::VSbc_vvm,
                              "vsbc.vvm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, VRegister>()),
            "VSbc_vvm");
}

TEST_F(AssemblerRISCV64Test, VSbc_vxm) {
  DriverStr(RepeatVVRFiltered(&Riscv64Assembler::VSbc_vxm,
                              "vsbc.vxm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, XRegister>()),
            "VSbc_vxm");
}

TEST_F(AssemblerRISCV64Test, VMsbc_vvm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMsbc_vvm, "vmsbc.vvm {reg1}, {reg2}, {reg3}, v0"),
            "VMsbc_vvm");
}

TEST_F(AssemblerRISCV64Test, VMsbc_vxm) {
  DriverStr(RepeatVVR(&Riscv64Assembler::VMsbc_vxm, "vmsbc.vxm {reg1}, {reg2}, {reg3}, v0"),
            "VMsbc_vxm");
}

TEST_F(AssemblerRISCV64Test, VMsbc_vv) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMsbc_vv, "vmsbc.vv {reg1}, {reg2}, {reg3}"), "VMsbc_vvm");
}

TEST_F(AssemblerRISCV64Test, VMsbc_vx) {
  DriverStr(RepeatVVR(&Riscv64Assembler::VMsbc_vx, "vmsbc.vx {reg1}, {reg2}, {reg3}"), "VMsbc_vxm");
}

TEST_F(AssemblerRISCV64Test, VMerge_vvm) {
  DriverStr(RepeatVVVFiltered(&Riscv64Assembler::VMerge_vvm,
                              "vmerge.vvm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, VRegister>()),
            "VMerge_vvm");
}

TEST_F(AssemblerRISCV64Test, VMerge_vxm) {
  DriverStr(RepeatVVRFiltered(&Riscv64Assembler::VMerge_vxm,
                              "vmerge.vxm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, XRegister>()),
            "VMerge_vxm");
}

TEST_F(AssemblerRISCV64Test, VMerge_vim) {
  DriverStr(RepeatVVIFiltered(&Riscv64Assembler::VMerge_vim,
                              /*imm_bits=*/ -5,
                              "vmerge.vim {reg1}, {reg2}, {imm}, v0",
                              SkipV0<VRegister, int32_t>()),
            "VMerge_vim");
}

TEST_F(AssemblerRISCV64Test, VMv_vv) {
  DriverStr(RepeatVV(&Riscv64Assembler::VMv_vv, "vmv.v.v {reg1}, {reg2}"), "VMmv_vv");
}

TEST_F(AssemblerRISCV64Test, VMv_vx) {
  DriverStr(RepeatVR(&Riscv64Assembler::VMv_vx, "vmv.v.x {reg1}, {reg2}"), "VMv_vx");
}

TEST_F(AssemblerRISCV64Test, VMv_vi) {
  DriverStr(RepeatVIb(&Riscv64Assembler::VMv_vi, /*imm_bits=*/ -5, "vmv.v.i {reg}, {imm}"),
            "VMv_vi");
}

TEST_F(AssemblerRISCV64Test, VMseq_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMseq_vv,
                                "vmseq.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMseq_vv");
}

TEST_F(AssemblerRISCV64Test, VMseq_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMseq_vx,
                                "vmseq.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMseq_vx");
}

TEST_F(AssemblerRISCV64Test, VMseq_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMseq_vi,
                                 /*imm_bits=*/ -5,
                                 "vmseq.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMseq_vi");
}

TEST_F(AssemblerRISCV64Test, VMsne_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsne_vv,
                                "vmsne.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsne_vv");
}

TEST_F(AssemblerRISCV64Test, VMsne_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsne_vx,
                                "vmsne.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsne_vx");
}

TEST_F(AssemblerRISCV64Test, VMsne_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsne_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsne.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMsne_vi");
}

TEST_F(AssemblerRISCV64Test, VMsltu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsltu_vv,
                                "vmsltu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsltu_vv");
}

TEST_F(AssemblerRISCV64Test, VMsltu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsltu_vx,
                                "vmsltu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsltu_vx");
}

TEST_F(AssemblerRISCV64Test, VMsgtu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsgtu_vv,
                                "vmsgtu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsgtu_vv");
}

TEST_F(AssemblerRISCV64Test, VMslt_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMslt_vv,
                                "vmslt.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMslt_vv");
}

TEST_F(AssemblerRISCV64Test, VMslt_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMslt_vx,
                                "vmslt.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMslt_vx");
}

TEST_F(AssemblerRISCV64Test, VMsgt_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsgt_vv,
                                "vmsgt.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsgt_vv");
}

TEST_F(AssemblerRISCV64Test, VMsleu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsleu_vv,
                                "vmsleu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsleu_vv");
}

TEST_F(AssemblerRISCV64Test, VMsleu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsleu_vx,
                                "vmsleu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsleu_vx");
}

TEST_F(AssemblerRISCV64Test, VMsleu_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsleu_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsleu.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMsleu_vi");
}

TEST_F(AssemblerRISCV64Test, VMsgeu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsgeu_vv,
                                "vmsgeu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsgeu_vv");
}

TEST_F(AssemblerRISCV64Test, VMsltu_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsltu_vi,
                                 /*imm_bits=*/ 4,
                                 "vmsltu.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>(),
                                 /*bias=*/ 1),
            "VMsltu_vi");
}

TEST_F(AssemblerRISCV64Test, VMsle_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsle_vv,
                                "vmsle.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsle_vv");
}

TEST_F(AssemblerRISCV64Test, VMsle_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsle_vx,
                                "vmsle.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsle_vx");
}

TEST_F(AssemblerRISCV64Test, VMsle_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsle_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsle.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMsle_vi");
}

TEST_F(AssemblerRISCV64Test, VMsge_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMsge_vv,
                                "vmsge.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMsge_vv");
}

TEST_F(AssemblerRISCV64Test, VMslt_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMslt_vi,
                                 /*imm_bits=*/ -5,
                                 "vmslt.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>(),
                                 /*bias=*/ 1),
            "VMslt_vi");
}

TEST_F(AssemblerRISCV64Test, VMsgtu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsgtu_vx,
                                "vmsgtu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsgtu_vx");
}

TEST_F(AssemblerRISCV64Test, VMsgtu_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsgtu_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsgtu.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMsgtu_vi");
}

TEST_F(AssemblerRISCV64Test, VMsgeu_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsgeu_vi,
                                 /*imm_bits=*/ 4,
                                 "vmsgeu.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>(),
                                 /*bias=*/ 1),
            "VMsgeu_vi");
}

TEST_F(AssemblerRISCV64Test, VMsgt_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMsgt_vx,
                                "vmsgt.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMsgt_vx");
}

TEST_F(AssemblerRISCV64Test, VMsgt_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsgt_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsgt.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VMsgt_vi");
}

TEST_F(AssemblerRISCV64Test, VMsge_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VMsge_vi,
                                 /*imm_bits=*/ -5,
                                 "vmsge.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>(),
                                 /*bias=*/ 1),
            "VMsge_vi");
}

TEST_F(AssemblerRISCV64Test, VSaddu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSaddu_vv,
                                "vsaddu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSaddu_vv");
}

TEST_F(AssemblerRISCV64Test, VSaddu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSaddu_vx,
                                "vsaddu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSaddu_vx");
}

TEST_F(AssemblerRISCV64Test, VSaddu_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSaddu_vi,
                                 /*imm_bits=*/ -5,
                                 "vsaddu.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VSaddu_vi");
}

TEST_F(AssemblerRISCV64Test, VSadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSadd_vv,
                                "vsadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSadd_vv");
}

TEST_F(AssemblerRISCV64Test, VSadd_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSadd_vx,
                                "vsadd.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSadd_vx");
}

TEST_F(AssemblerRISCV64Test, VSadd_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSadd_vi,
                                 /*imm_bits=*/ -5,
                                 "vsadd.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, int32_t>()),
            "VSadd_vi");
}

TEST_F(AssemblerRISCV64Test, VSsubu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSsubu_vv,
                                "vssubu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSsubu_vv");
}

TEST_F(AssemblerRISCV64Test, VSsubu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSsubu_vx,
                                "vssubu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSsubu_vx");
}

TEST_F(AssemblerRISCV64Test, VSsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSsub_vv,
                                "vssub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSsub_vv");
}

TEST_F(AssemblerRISCV64Test, VSsub_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSsub_vx,
                                "vssub.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSsub_vx");
}

TEST_F(AssemblerRISCV64Test, VSll_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSll_vv,
                                "vsll.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSll_vv");
}

TEST_F(AssemblerRISCV64Test, VSll_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSll_vx,
                                "vsll.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSll_vx");
}

TEST_F(AssemblerRISCV64Test, VSll_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSll_vi,
                                 /*imm_bits=*/ 5,
                                 "vsll.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSll_vi");
}

TEST_F(AssemblerRISCV64Test, VSmul_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSmul_vv,
                                "vsmul.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSmul_vv");
}

TEST_F(AssemblerRISCV64Test, VSmul_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSmul_vx,
                                "vsmul.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSmul_vx");
}

TEST_F(AssemblerRISCV64Test, Vmv1r_v) {
  DriverStr(RepeatVVAligned(&Riscv64Assembler::Vmv1r_v, /*alignment=*/ 1, "vmv1r.v {reg1}, {reg2}"),
            "Vmv1r_v");
}

TEST_F(AssemblerRISCV64Test, Vmv2r_v) {
  DriverStr(RepeatVVAligned(&Riscv64Assembler::Vmv2r_v, /*alignment=*/ 2, "vmv2r.v {reg1}, {reg2}"),
            "Vmv2r_v");
}

TEST_F(AssemblerRISCV64Test, Vmv4r_v) {
  DriverStr(RepeatVVAligned(&Riscv64Assembler::Vmv4r_v, /*alignment=*/ 4, "vmv4r.v {reg1}, {reg2}"),
            "Vmv4r_v");
}

TEST_F(AssemblerRISCV64Test, Vmv8r_v) {
  DriverStr(RepeatVVAligned(&Riscv64Assembler::Vmv8r_v, /*alignment=*/ 8, "vmv8r.v {reg1}, {reg2}"),
            "Vmv8r_v");
}

TEST_F(AssemblerRISCV64Test, VSrl_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSrl_vv,
                                "vsrl.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSrl_vv");
}

TEST_F(AssemblerRISCV64Test, VSrl_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSrl_vx,
                                "vsrl.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSrl_vx");
}

TEST_F(AssemblerRISCV64Test, VSrl_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSrl_vi,
                                 /*imm_bits=*/ 5,
                                 "vsrl.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSrl_vi");
}

TEST_F(AssemblerRISCV64Test, VSra_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSra_vv,
                                "vsra.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSra_vv");
}

TEST_F(AssemblerRISCV64Test, VSra_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSra_vx,
                                "vsra.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSra_vx");
}

TEST_F(AssemblerRISCV64Test, VSra_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSra_vi,
                                 /*imm_bits=*/ 5,
                                 "vsra.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSra_vi");
}

TEST_F(AssemblerRISCV64Test, VSsrl_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSsrl_vv,
                                "vssrl.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSsrl_vv");
}

TEST_F(AssemblerRISCV64Test, VSsrl_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSsrl_vx,
                                "vssrl.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSsrl_vx");
}

TEST_F(AssemblerRISCV64Test, VSsrl_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSsrl_vi,
                                 /*imm_bits=*/ 5,
                                 "vssrl.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSsrl_vi");
}

TEST_F(AssemblerRISCV64Test, VSsra_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VSsra_vv,
                                "vssra.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VSsra_vv");
}

TEST_F(AssemblerRISCV64Test, VSsra_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSsra_vx,
                                "vssra.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSsra_vx");
}

TEST_F(AssemblerRISCV64Test, VSsra_vi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VSsra_vi,
                                 /*imm_bits=*/ 5,
                                 "vssra.vi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VSsra_vi");
}

TEST_F(AssemblerRISCV64Test, VNsrl_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNsrl_wv,
                                "vnsrl.wv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VNsrl_wv");
}

TEST_F(AssemblerRISCV64Test, VNsrl_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VNsrl_wx,
                                "vnsrl.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VNsrl_wx");
}

TEST_F(AssemblerRISCV64Test, VNsrl_wi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VNsrl_wi,
                                 /*imm_bits=*/ 5,
                                 "vnsrl.wi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VNsrl_wi");
}

TEST_F(AssemblerRISCV64Test, VNcvt_x_x_w) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VNcvt_x_x_w, "vncvt.x.x.w {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VNcvt_x_x_w");
}

TEST_F(AssemblerRISCV64Test, VNsra_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNsra_wv,
                                "vnsra.wv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VNsra_wv");
}

TEST_F(AssemblerRISCV64Test, VNsra_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VNsra_wx,
                                "vnsra.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VNsra_wx");
}

TEST_F(AssemblerRISCV64Test, VNsra_wi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VNsra_wi,
                                 /*imm_bits=*/ 5,
                                 "vnsra.wi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VNsra_wi");
}

TEST_F(AssemblerRISCV64Test, VNclipu_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNclipu_wv,
                                "vnclipu.wv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VNclipu_wv");
}

TEST_F(AssemblerRISCV64Test, VNclipu_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VNclipu_wx,
                                "vnclipu.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VNclipu_wx");
}

TEST_F(AssemblerRISCV64Test, VNclipu_wi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VNclipu_wi,
                                 /*imm_bits=*/ 5,
                                 "vnclipu.wi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VNclipu_wi");
}

TEST_F(AssemblerRISCV64Test, VNclip_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNclip_wv,
                                "vnclip.wv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VNclip_wv");
}

TEST_F(AssemblerRISCV64Test, VNclip_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VNclip_wx,
                                "vnclip.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VNclip_wx");
}

TEST_F(AssemblerRISCV64Test, VNclip_wi) {
  DriverStr(RepeatVVIbVmFiltered(&Riscv64Assembler::VNclip_wi,
                                 /*imm_bits=*/ 5,
                                 "vnclip.wi {reg1}, {reg2}, {imm}{vm}",
                                 SkipV0Vm<VRegister, uint32_t>()),
            "VNclip_wi");
}

TEST_F(AssemblerRISCV64Test, VWredsumu_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VWredsumu_vs, "vwredsumu.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VWredsumu_vs");
}

TEST_F(AssemblerRISCV64Test, VWredsum_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VWredsum_vs, "vwredsum.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VWredsum_vs");
}

TEST_F(AssemblerRISCV64Test, VRedsum_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedsum_vs, "vredsum.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedsum_vs");
}

TEST_F(AssemblerRISCV64Test, VRedand_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedand_vs, "vredand.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedand_vs");
}

TEST_F(AssemblerRISCV64Test, VRedor_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedor_vs, "vredor.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedor_vs");
}

TEST_F(AssemblerRISCV64Test, VRedxor_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedxor_vs, "vredxor.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedxor_vs");
}

TEST_F(AssemblerRISCV64Test, VRedminu_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedminu_vs, "vredminu.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedminu_vs");
}

TEST_F(AssemblerRISCV64Test, VRedmin_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedmin_vs, "vredmin.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedmin_vs");
}

TEST_F(AssemblerRISCV64Test, VRedmaxu_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedmaxu_vs, "vredmaxu.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedmaxu_vs");
}

TEST_F(AssemblerRISCV64Test, VRedmax_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VRedmax_vs, "vredmax.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VRedmax_vs");
}

TEST_F(AssemblerRISCV64Test, VAaddu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAaddu_vv,
                                "vaaddu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAaddu_vv");
}

TEST_F(AssemblerRISCV64Test, VAaddu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAaddu_vx,
                                "vaaddu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAaddu_vx");
}

TEST_F(AssemblerRISCV64Test, VAadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAadd_vv,
                                "vaadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAadd_vv");
}

TEST_F(AssemblerRISCV64Test, VAadd_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAadd_vx,
                                "vaadd.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAadd_vx");
}

TEST_F(AssemblerRISCV64Test, VAsubu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAsubu_vv,
                                "vasubu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAsubu_vv");
}

TEST_F(AssemblerRISCV64Test, VAsubu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAsubu_vx,
                                "vasubu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAsubu_vx");
}

TEST_F(AssemblerRISCV64Test, VAsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VAsub_vv,
                                "vasub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VAsub_vv");
}

TEST_F(AssemblerRISCV64Test, VAsub_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VAsub_vx,
                                "vasub.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VAsub_vx");
}

TEST_F(AssemblerRISCV64Test, VSlide1up_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSlide1up_vx,
                                "vslide1up.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VSlide1up_vx");
}

TEST_F(AssemblerRISCV64Test, VSlide1down_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VSlide1down_vx,
                                "vslide1down.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VSlide1down_vx");
}

TEST_F(AssemblerRISCV64Test, VCompress_vm) {
  DriverStr(RepeatVVVFiltered(&Riscv64Assembler::VCompress_vm,
                              "vcompress.vm {reg1}, {reg2}, {reg3}",
                              VVVNoR1R2R3Overlap()),
            "VCompress_vm");
}

TEST_F(AssemblerRISCV64Test, VMandn_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMandn_mm, "vmandn.mm {reg1}, {reg2}, {reg3}"),
            "VMandn_mm");
}

TEST_F(AssemblerRISCV64Test, VMand_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMand_mm, "vmand.mm {reg1}, {reg2}, {reg3}"), "VMand_mm");
}

TEST_F(AssemblerRISCV64Test, VMmv_m) {
  DriverStr(RepeatVV(&Riscv64Assembler::VMmv_m, "vmmv.m {reg1}, {reg2}"), "VMmv_m");
}

TEST_F(AssemblerRISCV64Test, VMor_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMor_mm, "vmor.mm {reg1}, {reg2}, {reg3}"), "VMor_mm");
}

TEST_F(AssemblerRISCV64Test, VMxor_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMxor_mm, "vmxor.mm {reg1}, {reg2}, {reg3}"), "VMxor_mm");
}

TEST_F(AssemblerRISCV64Test, VMclr_m) {
  DriverStr(RepeatV(&Riscv64Assembler::VMclr_m, "vmclr.m {reg}"), "VMclr_m");
}

TEST_F(AssemblerRISCV64Test, VMorn_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMorn_mm, "vmorn.mm {reg1}, {reg2}, {reg3}"), "VMorn_mm");
}

TEST_F(AssemblerRISCV64Test, VMnand_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMnand_mm, "vmnand.mm {reg1}, {reg2}, {reg3}"),
            "VMnand_m");
}

TEST_F(AssemblerRISCV64Test, VMnot_m) {
  DriverStr(RepeatVV(&Riscv64Assembler::VMnot_m, "vmnot.m {reg1}, {reg2}"), "VMnot_m");
}

TEST_F(AssemblerRISCV64Test, VMnor_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMnor_mm, "vmnor.mm {reg1}, {reg2}, {reg3}"), "VMnor_mm");
}

TEST_F(AssemblerRISCV64Test, VMxnor_mm) {
  DriverStr(RepeatVVV(&Riscv64Assembler::VMxnor_mm, "vmxnor.mm {reg1}, {reg2}, {reg3}"),
            "VMxnor_mm");
}

TEST_F(AssemblerRISCV64Test, VMset_m) {
  DriverStr(RepeatV(&Riscv64Assembler::VMset_m, "vmset.m {reg}"), "VMset_m");
}

TEST_F(AssemblerRISCV64Test, VDivu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VDivu_vv,
                                "vdivu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VDivu_vv");
}

TEST_F(AssemblerRISCV64Test, VDivu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VDivu_vx,
                                "vdivu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VDivu_vx");
}

TEST_F(AssemblerRISCV64Test, VDiv_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VDiv_vv,
                                "vdiv.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VDiv_vv");
}

TEST_F(AssemblerRISCV64Test, VDiv_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VDiv_vx,
                                "vdiv.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VDiv_vx");
}

TEST_F(AssemblerRISCV64Test, VRemu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VRemu_vv,
                                "vremu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VRemu_vv");
}

TEST_F(AssemblerRISCV64Test, VRemu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VRemu_vx,
                                "vremu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VRemu_vx");
}

TEST_F(AssemblerRISCV64Test, VRem_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VRem_vv,
                                "vrem.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VRem_vv");
}

TEST_F(AssemblerRISCV64Test, VRem_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VRem_vx,
                                "vrem.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VRem_vx");
}

TEST_F(AssemblerRISCV64Test, VMulhu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMulhu_vv,
                                "vmulhu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMulhu_vv");
}

TEST_F(AssemblerRISCV64Test, VMulhu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMulhu_vx,
                                "vmulhu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMulhu_vx");
}

TEST_F(AssemblerRISCV64Test, VMul_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMul_vv,
                                "vmul.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMul_vv");
}

TEST_F(AssemblerRISCV64Test, VMul_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMul_vx,
                                "vmul.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMul_vx");
}

TEST_F(AssemblerRISCV64Test, VMulhsu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMulhsu_vv,
                                "vmulhsu.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMulhsu_vv");
}

TEST_F(AssemblerRISCV64Test, VMulhsu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMulhsu_vx,
                                "vmulhsu.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMulhsu_vx");
}

TEST_F(AssemblerRISCV64Test, VMulh_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMulh_vv,
                                "vmulh.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMulh_vv");
}

TEST_F(AssemblerRISCV64Test, VMulh_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VMulh_vx,
                                "vmulh.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VMulh_vx");
}

TEST_F(AssemblerRISCV64Test, VMadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMadd_vv,
                                "vmadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMadd_vv");
}

TEST_F(AssemblerRISCV64Test, VMadd_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VMadd_vx,
                                "vmadd.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VMadd_vx");
}

TEST_F(AssemblerRISCV64Test, VNmsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNmsub_vv,
                                "vnmsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VNmsub_vv");
}

TEST_F(AssemblerRISCV64Test, VNmsub_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VNmsub_vx,
                                "vnmsub.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VNmsub_vx");
}

TEST_F(AssemblerRISCV64Test, VMacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMacc_vv,
                                "vmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMacc_vv");
}

TEST_F(AssemblerRISCV64Test, VMacc_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VMacc_vx,
                                "vmacc.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VMacc_vx");
}

TEST_F(AssemblerRISCV64Test, VNmsac_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VNmsac_vv,
                                "vnmsac.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VNmsac_vv");
}

TEST_F(AssemblerRISCV64Test, VNmsac_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VNmsac_vx,
                                "vnmsac.vx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<XRegister, VRegister>()),
            "VNmsac_vx");
}

TEST_F(AssemblerRISCV64Test, VWaddu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWaddu_vv,
                                "vwaddu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWaddu_vv");
}

TEST_F(AssemblerRISCV64Test, VWaddu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWaddu_vx,
                                "vwaddu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWaddu_vx");
}

TEST_F(AssemblerRISCV64Test, VWcvtu_x_x_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VWcvtu_x_x_v,
                               "vwcvtu.x.x.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VWcvtu_x_x_v");
}

TEST_F(AssemblerRISCV64Test, VWadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWadd_vv,
                                "vwadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWadd_vv");
}

TEST_F(AssemblerRISCV64Test, VWadd_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWadd_vx,
                                "vwadd.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWadd_vx");
}

TEST_F(AssemblerRISCV64Test, VWcvt_x_x_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VWcvt_x_x_v,
                               "vwcvt.x.x.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VWcvt_x_x_v");
}

TEST_F(AssemblerRISCV64Test, VWsubu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWsubu_vv,
                                "vwsubu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWsubu_vv");
}

TEST_F(AssemblerRISCV64Test, VWsubu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWsubu_vx,
                                "vwsubu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWsubu_vx");
}

TEST_F(AssemblerRISCV64Test, VWsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWsub_vv,
                                "vwsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWsub_vv");
}

TEST_F(AssemblerRISCV64Test, VWsub_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWsub_vx,
                                "vwsub.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWsub_vx");
}

TEST_F(AssemblerRISCV64Test, VWaddu_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWaddu_wv,
                                "vwaddu.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VWaddu_wv");
}

TEST_F(AssemblerRISCV64Test, VWaddu_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWaddu_wx,
                                "vwaddu.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VWaddu_wx");
}

TEST_F(AssemblerRISCV64Test, VWadd_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWadd_wv,
                                "vwadd.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VWadd_wv");
}

TEST_F(AssemblerRISCV64Test, VWadd_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWadd_wx,
                                "vwadd.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VWadd_wx");
}

TEST_F(AssemblerRISCV64Test, VWsubu_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWsubu_wv,
                                "vwsubu.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VWsubu_wv");
}

TEST_F(AssemblerRISCV64Test, VWsubu_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWsubu_wx,
                                "vwsubu.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VWsubu_wx");
}

TEST_F(AssemblerRISCV64Test, VWsub_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWsub_wv,
                                "vwsub.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VWsub_wv");
}

TEST_F(AssemblerRISCV64Test, VWsub_wx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWsub_wx,
                                "vwsub.wx {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, XRegister>()),
            "VWsub_wx");
}

TEST_F(AssemblerRISCV64Test, VWmulu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmulu_vv,
                                "vwmulu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmulu_vv");
}

TEST_F(AssemblerRISCV64Test, VWmulu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWmulu_vx,
                                "vwmulu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWmulu_vx");
}

TEST_F(AssemblerRISCV64Test, VWmulsu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmulsu_vv,
                                "vwmulsu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmulsu_vv");
}

TEST_F(AssemblerRISCV64Test, VWmulsu_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWmulsu_vx,
                                "vwmulsu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWmulsu_vx");
}

TEST_F(AssemblerRISCV64Test, VWmul_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmul_vv,
                                "vwmul.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmul_vv");
}

TEST_F(AssemblerRISCV64Test, VWmul_vx) {
  DriverStr(RepeatVVRVmFiltered(&Riscv64Assembler::VWmul_vx,
                                "vwmul.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<XRegister>()),
            "VWmul_vx");
}

TEST_F(AssemblerRISCV64Test, VWmaccu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmaccu_vv,
                                "vwmaccu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmaccu_vv");
}

TEST_F(AssemblerRISCV64Test, VWmaccu_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VWmaccu_vx,
                                "vwmaccu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<XRegister>()),
            "VWmaccu_vx");
}

TEST_F(AssemblerRISCV64Test, VWmacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmacc_vv,
                                "vwmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmacc_vv");
}

TEST_F(AssemblerRISCV64Test, VWmacc_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VWmacc_vx,
                                "vwmacc.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<XRegister>()),
            "VWmacc_vx");
}

TEST_F(AssemblerRISCV64Test, VWmaccus_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VWmaccus_vx,
                                "vwmaccus.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<XRegister>()),
            "VWmaccus_vx");
}

TEST_F(AssemblerRISCV64Test, VWmaccsu_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VWmaccsu_vv,
                                "vwmaccsu.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VWmaccsu_vv");
}

TEST_F(AssemblerRISCV64Test, VWmaccsu_vx) {
  DriverStr(RepeatVRVVmFiltered(&Riscv64Assembler::VWmaccsu_vx,
                                "vwmaccsu.vx {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<XRegister>()),
            "VWmaccsu_vx");
}

TEST_F(AssemblerRISCV64Test, VFadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFadd_vv,
                                "vfadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFadd_vv");
}

TEST_F(AssemblerRISCV64Test, VFadd_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFadd_vf,
                                "vfadd.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFadd_vf");
}

TEST_F(AssemblerRISCV64Test, VFredusum_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VFredusum_vs, "vfredusum.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VFredusum_vs");
}

TEST_F(AssemblerRISCV64Test, VFsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFsub_vv,
                                "vfsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFsub_vv");
}

TEST_F(AssemblerRISCV64Test, VFsub_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFsub_vf,
                                "vfsub.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFsub_vf");
}

TEST_F(AssemblerRISCV64Test, VFredosum_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VFredosum_vs, "vfredosum.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VFredosum_vs");
}

TEST_F(AssemblerRISCV64Test, VFmin_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmin_vv,
                                "vfmin.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmin_vv");
}

TEST_F(AssemblerRISCV64Test, VFmin_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFmin_vf,
                                "vfmin.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFmin_vf");
}

TEST_F(AssemblerRISCV64Test, VFredmin_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VFredmin_vs, "vfredmin.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VFredmin_vs");
}

TEST_F(AssemblerRISCV64Test, VFmax_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmax_vv,
                                "vfmax.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmax_vv");
}

TEST_F(AssemblerRISCV64Test, VFmax_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFmax_vf,
                                "vfmax.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFmax_vf");
}

TEST_F(AssemblerRISCV64Test, VFredmax_vs) {
  DriverStr(RepeatVVVVm(&Riscv64Assembler::VFredmax_vs, "vfredmax.vs {reg1}, {reg2}, {reg3}{vm}"),
            "VFredmax_vs");
}

TEST_F(AssemblerRISCV64Test, VFsgnj_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFsgnj_vv,
                                "vfsgnj.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFsgnj_vv");
}

TEST_F(AssemblerRISCV64Test, VFsgnj_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFsgnj_vf,
                                "vfsgnj.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFsgnj_vf");
}

TEST_F(AssemblerRISCV64Test, VFsgnjn_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFsgnjn_vv,
                                "vfsgnjn.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFsgnjn_vv");
}

TEST_F(AssemblerRISCV64Test, VFsgnjn_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFsgnjn_vf,
                                "vfsgnjn.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFsgnjn_vf");
}

TEST_F(AssemblerRISCV64Test, VFneg_v) {
  DriverStr(RepeatVV(&Riscv64Assembler::VFneg_v, "vfneg.v {reg1}, {reg2}"), "VFneg_v");
}

TEST_F(AssemblerRISCV64Test, VFsgnjx_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFsgnjx_vv,
                                "vfsgnjx.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFsgnjx_vv");
}

TEST_F(AssemblerRISCV64Test, VFsgnjx_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFsgnjx_vf,
                                "vfsgnjx.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFsgnjx_vf");
}

TEST_F(AssemblerRISCV64Test, VFabs_v) {
  DriverStr(RepeatVV(&Riscv64Assembler::VFabs_v, "vfabs.v {reg1}, {reg2}"), "VFabs_v");
}

TEST_F(AssemblerRISCV64Test, VFslide1up_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFslide1up_vf,
                                "vfslide1up.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<FRegister>()),
            "VFslide1up_vf");
}

TEST_F(AssemblerRISCV64Test, VFslide1down_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFslide1down_vf,
                                "vfslide1down.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFslide1down_vf");
}

TEST_F(AssemblerRISCV64Test, VFmerge_vfm) {
  DriverStr(RepeatVVFFiltered(&Riscv64Assembler::VFmerge_vfm,
                              "vfmerge.vfm {reg1}, {reg2}, {reg3}, v0",
                              SkipV0<VRegister, FRegister>()),
            "VFmerge_vfm");
}

TEST_F(AssemblerRISCV64Test, VFmv_v_f) {
  DriverStr(RepeatVF(&Riscv64Assembler::VFmv_v_f, "vfmv.v.f {reg1}, {reg2}"), "VFmv_v_f");
}

TEST_F(AssemblerRISCV64Test, VMfeq_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMfeq_vv,
                                "vmfeq.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMfeq_vv");
}

TEST_F(AssemblerRISCV64Test, VMfeq_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMfeq_vf,
                                "vmfeq.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMfeq_vf");
}

TEST_F(AssemblerRISCV64Test, VMfle_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMfle_vv,
                                "vmfle.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMfle_vv");
}

TEST_F(AssemblerRISCV64Test, VMfle_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMfle_vf,
                                "vmfle.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMfle_vf");
}

TEST_F(AssemblerRISCV64Test, VMfge_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMfge_vv,
                                "vmfge.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMfge_vv");
}

TEST_F(AssemblerRISCV64Test, VMflt_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMflt_vv,
                                "vmflt.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMflt_vv");
}

TEST_F(AssemblerRISCV64Test, VMflt_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMflt_vf,
                                "vmflt.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMflt_vf");
}

TEST_F(AssemblerRISCV64Test, VMfgt_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMfgt_vv,
                                "vmfgt.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMfgt_vv");
}

TEST_F(AssemblerRISCV64Test, VMfne_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VMfne_vv,
                                "vmfne.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VMfne_vv");
}

TEST_F(AssemblerRISCV64Test, VMfne_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMfne_vf,
                                "vmfne.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMfne_vf");
}

TEST_F(AssemblerRISCV64Test, VMfgt_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMfgt_vf,
                                "vmfgt.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMfgt_vf");
}

TEST_F(AssemblerRISCV64Test, VMfge_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VMfge_vf,
                                "vmfge.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VMfge_vf");
}

TEST_F(AssemblerRISCV64Test, VFdiv_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFdiv_vv,
                                "vfdiv.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFdiv_vv");
}

TEST_F(AssemblerRISCV64Test, VFdiv_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFdiv_vf,
                                "vfdiv.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFdiv_vf");
}

TEST_F(AssemblerRISCV64Test, VFrdiv_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFrdiv_vf,
                                "vfrdiv.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFrdiv_vf");
}

TEST_F(AssemblerRISCV64Test, VFmul_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmul_vv,
                                "vfmul.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmul_vv");
}

TEST_F(AssemblerRISCV64Test, VFmul_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFmul_vf,
                                "vfmul.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFmul_vf");
}

TEST_F(AssemblerRISCV64Test, VFrsub_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFrsub_vf,
                                "vfrsub.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFrsub_vf");
}

TEST_F(AssemblerRISCV64Test, VFmadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmadd_vv,
                                "vfmadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmadd_vv");
}

TEST_F(AssemblerRISCV64Test, VFmadd_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFmadd_vf,
                                "vfmadd.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFmadd_vf");
}

TEST_F(AssemblerRISCV64Test, VFnmadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFnmadd_vv,
                                "vfnmadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFnmadd_vv");
}

TEST_F(AssemblerRISCV64Test, VFnmadd_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFnmadd_vf,
                                "vfnmadd.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFnmadd_vf");
}

TEST_F(AssemblerRISCV64Test, VFmsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmsub_vv,
                                "vfmsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmsub_vv");
}

TEST_F(AssemblerRISCV64Test, VFmsub_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFmsub_vf,
                                "vfmsub.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFmsub_vf");
}

TEST_F(AssemblerRISCV64Test, VFnmsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFnmsub_vv,
                                "vfnmsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFnmsub_vv");
}

TEST_F(AssemblerRISCV64Test, VFnmsub_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFnmsub_vf,
                                "vfnmsub.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFnmsub_vf");
}

TEST_F(AssemblerRISCV64Test, VFmacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmacc_vv,
                                "vfmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmacc_vv");
}

TEST_F(AssemblerRISCV64Test, VFmacc_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFmacc_vf,
                                "vfmacc.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFmacc_vf");
}

TEST_F(AssemblerRISCV64Test, VFnmacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFnmacc_vv,
                                "vfnmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFnmacc_vv");
}

TEST_F(AssemblerRISCV64Test, VFnmacc_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFnmacc_vf,
                                "vfnmacc.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFnmacc_vf");
}

TEST_F(AssemblerRISCV64Test, VFmsac_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFmsac_vv,
                                "vfmsac.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFmsac_vv");
}

TEST_F(AssemblerRISCV64Test, VFmsac_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFmsac_vf,
                                "vfmsac.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFmsac_vf");
}

TEST_F(AssemblerRISCV64Test, VFnmsac_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFnmsac_vv,
                                "vfnmsac.vv {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFnmsac_vv");
}

TEST_F(AssemblerRISCV64Test, VFnmsac_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFnmsac_vf,
                                "vfnmsac.vf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<FRegister, VRegister>()),
            "VFnmsac_vf");
}

TEST_F(AssemblerRISCV64Test, VFwadd_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwadd_vv,
                                "vfwadd.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwadd_vv");
}

TEST_F(AssemblerRISCV64Test, VFwadd_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFwadd_vf,
                                "vfwadd.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<FRegister>()),
            "VFwadd_vf");
}

TEST_F(AssemblerRISCV64Test, VFwredusum_vs) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwredusum_vs,
                                "vfwredusum.vs {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, VRegister>()),
            "VFwredusum_vs");
}

TEST_F(AssemblerRISCV64Test, VFwsub_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwsub_vv,
                                "vfwsub.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwsub_vv");
}

TEST_F(AssemblerRISCV64Test, VFwsub_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFwsub_vf,
                                "vfwsub.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<FRegister>()),
            "VFwsub_vf");
}

TEST_F(AssemblerRISCV64Test, VFwredosum_vs) {
  DriverStr(
      RepeatVVVVm(&Riscv64Assembler::VFwredosum_vs, "vfwredosum.vs {reg1}, {reg2}, {reg3}{vm}"),
      "VFwredosum_vs");
}

TEST_F(AssemblerRISCV64Test, VFwadd_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwadd_wv,
                                "vfwadd.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VFwadd_wv");
}

TEST_F(AssemblerRISCV64Test, VFwadd_wf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFwadd_wf,
                                "vfwadd.wf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFwadd_wf");
}

TEST_F(AssemblerRISCV64Test, VFwsub_wv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwsub_wv,
                                "vfwsub.wv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<VRegister>()),
            "VFwsub_wv");
}

TEST_F(AssemblerRISCV64Test, VFwsub_wf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFwsub_wf,
                                "vfwsub.wf {reg1}, {reg2}, {reg3}{vm}",
                                SkipV0Vm<VRegister, FRegister>()),
            "VFwsub_wf");
}

TEST_F(AssemblerRISCV64Test, VFwmul_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwmul_vv,
                                "vfwmul.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwmul_vv");
}

TEST_F(AssemblerRISCV64Test, VFwmul_vf) {
  DriverStr(RepeatVVFVmFiltered(&Riscv64Assembler::VFwmul_vf,
                                "vfwmul.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2Overlap<FRegister>()),
            "VFwmul_vf");
}

TEST_F(AssemblerRISCV64Test, VFwmacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwmacc_vv,
                                "vfwmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwmacc_vv");
}

TEST_F(AssemblerRISCV64Test, VFwmacc_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFwmacc_vf,
                                "vfwmacc.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<FRegister>()),
            "VFwmacc_vf");
}

TEST_F(AssemblerRISCV64Test, VFwnmacc_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwnmacc_vv,
                                "vfwnmacc.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwnmacc_vv");
}

TEST_F(AssemblerRISCV64Test, VFwnmacc_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFwnmacc_vf,
                                "vfwnmacc.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<FRegister>()),
            "VFwnmacc_vf");
}

TEST_F(AssemblerRISCV64Test, VFwmsac_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwmsac_vv,
                                "vfwmsac.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwmsac_vv");
}

TEST_F(AssemblerRISCV64Test, VFwmsac_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFwmsac_vf,
                                "vfwmsac.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<FRegister>()),
            "VFwmsac_vf");
}

TEST_F(AssemblerRISCV64Test, VFwnmsac_vv) {
  DriverStr(RepeatVVVVmFiltered(&Riscv64Assembler::VFwnmsac_vv,
                                "vfwnmsac.vv {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R2R3Overlap()),
            "VFwnmsac_vv");
}

TEST_F(AssemblerRISCV64Test, VFwnmsac_vf) {
  DriverStr(RepeatVFVVmFiltered(&Riscv64Assembler::VFwnmsac_vf,
                                "vfwnmsac.vf {reg1}, {reg2}, {reg3}{vm}",
                                VXVVmSkipV0VmAndNoR1R3Overlap<FRegister>()),
            "VFwnmsac_vf");
}

TEST_F(AssemblerRISCV64Test, VMv_s_x) {
  DriverStr(RepeatVR(&Riscv64Assembler::VMv_s_x, "vmv.s.x {reg1}, {reg2}"), "VMv_s_x");
}

TEST_F(AssemblerRISCV64Test, VMv_x_s) {
  DriverStr(RepeatRV(&Riscv64Assembler::VMv_x_s, "vmv.x.s {reg1}, {reg2}"), "VMv_x_s");
}

TEST_F(AssemblerRISCV64Test, VCpop_m) {
  DriverStr(RepeatRVVm(&Riscv64Assembler::VCpop_m, "vcpop.m {reg1}, {reg2}{vm}"), "VCpop_m");
}

TEST_F(AssemblerRISCV64Test, VFirst_m) {
  DriverStr(RepeatRVVm(&Riscv64Assembler::VFirst_m, "vfirst.m {reg1}, {reg2}{vm}"), "VFirst_m");
}

TEST_F(AssemblerRISCV64Test, VZext_vf8) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VZext_vf8, "vzext.vf8 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VZext_vf8");
}

TEST_F(AssemblerRISCV64Test, VSext_vf8) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VSext_vf8, "vsext.vf8 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VSext_vf8");
}

TEST_F(AssemblerRISCV64Test, VZext_vf4) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VZext_vf4, "vzext.vf4 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VZext_vf4");
}

TEST_F(AssemblerRISCV64Test, VSext_vf4) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VSext_vf4, "vsext.vf4 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VSext_vf4");
}

TEST_F(AssemblerRISCV64Test, VZext_vf2) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VZext_vf2, "vzext.vf2 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VZext_vf2");
}

TEST_F(AssemblerRISCV64Test, VSext_vf2) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VSext_vf2, "vsext.vf2 {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VSext_vf2");
}

TEST_F(AssemblerRISCV64Test, VFmv_s_f) {
  DriverStr(RepeatVF(&Riscv64Assembler::VFmv_s_f, "vfmv.s.f {reg1}, {reg2}"), "VFmv_s_f");
}

TEST_F(AssemblerRISCV64Test, VFmv_f_s) {
  DriverStr(RepeatFV(&Riscv64Assembler::VFmv_f_s, "vfmv.f.s {reg1}, {reg2}"), "VFmv_f_s");
}

TEST_F(AssemblerRISCV64Test, VFcvt_xu_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFcvt_xu_f_v,
                               "vfcvt.xu.f.v {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFcvt_xu_f_v");
}

TEST_F(AssemblerRISCV64Test, VFcvt_x_f_v) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VFcvt_x_f_v, "vfcvt.x.f.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VFcvt_x_f_v");
}

TEST_F(AssemblerRISCV64Test, VFcvt_f_xu_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFcvt_f_xu_v,
                               "vfcvt.f.xu.v {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFcvt_f_xu_v");
}

TEST_F(AssemblerRISCV64Test, VFcvt_f_x_v) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VFcvt_f_x_v, "vfcvt.f.x.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VFcvt_f_x_v");
}

TEST_F(AssemblerRISCV64Test, VFcvt_rtz_xu_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFcvt_rtz_xu_f_v,
                               "vfcvt.rtz.xu.f.v {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFcvt_rtz_xu_f_v");
}

TEST_F(AssemblerRISCV64Test, VFcvt_rtz_x_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFcvt_rtz_x_f_v,
                               "vfcvt.rtz.x.f.v {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFcvt_rtz_x_f_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_xu_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_xu_f_v,
                               "vfwcvt.xu.f.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_xu_f_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_x_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_x_f_v,
                               "vfwcvt.x.f.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_x_f_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_f_xu_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_f_xu_v,
                               "vfwcvt.f.xu.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_f_xu_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_f_x_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_f_x_v,
                               "vfwcvt.f.x.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_f_x_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_f_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_f_f_v,
                               "vfwcvt.f.f.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_f_f_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_rtz_xu_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_rtz_xu_f_v,
                               "vfwcvt.rtz.xu.f.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_rtz_xu_f_v");
}

TEST_F(AssemblerRISCV64Test, VFwcvt_rtz_x_f_v) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFwcvt_rtz_x_f_v,
                               "vfwcvt.rtz.x.f.v {reg1}, {reg2}{vm}",
                               VVVmSkipV0VmAndNoR1R2Overlap()),
            "VFwcvt_rtz_x_f_v");
}

TEST_F(AssemblerRISCV64Test, VFncvt_xu_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_xu_f_w,
                               "vfncvt.xu.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_xu_f_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_x_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_x_f_w,
                               "vfncvt.x.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_x_f_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_f_xu_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_f_xu_w,
                               "vfncvt.f.xu.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_f_xu_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_f_x_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_f_x_w,
                               "vfncvt.f.x.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_f_x_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_f_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_f_f_w,
                               "vfncvt.f.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_f_f_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_rod_f_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_rod_f_f_w,
                               "vfncvt.rod.f.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_rod_f_f_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_rtz_xu_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_rtz_xu_f_w,
                               "vfncvt.rtz.xu.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_rtz_xu_f_w");
}

TEST_F(AssemblerRISCV64Test, VFncvt_rtz_x_f_w) {
  DriverStr(RepeatVVVmFiltered(&Riscv64Assembler::VFncvt_rtz_x_f_w,
                               "vfncvt.rtz.x.f.w {reg1}, {reg2}{vm}",
                               SkipV0Vm<VRegister>()),
            "VFncvt_rtz_x_f_w");
}

TEST_F(AssemblerRISCV64Test, VFsqrt_v) {
  DriverStr(RepeatVVVmFiltered(
                &Riscv64Assembler::VFsqrt_v, "vfsqrt.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
            "VFsqrt_v");
}

TEST_F(AssemblerRISCV64Test, VFrsqrt7_v) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VFrsqrt7_v, "vfrsqrt7.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VFrsqrt7_v");
}

TEST_F(AssemblerRISCV64Test, VFrec7_v) {
  DriverStr(RepeatVVVmFiltered(
                &Riscv64Assembler::VFrec7_v, "vfrec7.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
            "VFrec7_v");
}

TEST_F(AssemblerRISCV64Test, VFclass_v) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VFclass_v, "vfclass.v {reg1}, {reg2}{vm}", SkipV0Vm<VRegister>()),
      "VFclass_v");
}

TEST_F(AssemblerRISCV64Test, VMsbf_m) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VMsbf_m, "vmsbf.m {reg1}, {reg2}{vm}", VVVmSkipV0VmAndNoR1R2Overlap()),
      "VMsbf_m");
}

TEST_F(AssemblerRISCV64Test, VMsof_m) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VMsof_m, "vmsof.m {reg1}, {reg2}{vm}", VVVmSkipV0VmAndNoR1R2Overlap()),
      "VMsof_m");
}

TEST_F(AssemblerRISCV64Test, VMsif_m) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VMsif_m, "vmsif.m {reg1}, {reg2}{vm}", VVVmSkipV0VmAndNoR1R2Overlap()),
      "VMsif_m");
}

TEST_F(AssemblerRISCV64Test, VIota_m) {
  DriverStr(
      RepeatVVVmFiltered(
          &Riscv64Assembler::VIota_m, "viota.m {reg1}, {reg2}{vm}", VVVmSkipV0VmAndNoR1R2Overlap()),
      "VIota_m");
}

TEST_F(AssemblerRISCV64Test, VId_v) {
  DriverStr(RepeatVVmFiltered(&Riscv64Assembler::VId_v, "vid.v {reg} {vm}", SkipV0Vm()), "VId_v");
}

// Pseudo instructions.
TEST_F(AssemblerRISCV64Test, Nop) {
  ScopedCSuppression scs(this);
  __ Nop();
  DriverStr("addi zero,zero,0", "Nop");
}

TEST_F(AssemblerRISCV64Test, Li) {
  ScopedMarchOverride smo(this, "-march=rv64imafd");
  TestLoadConst64("Li",
                  /*can_use_tmp=*/ false,
                  [&](XRegister rd, int64_t value) { __ Li(rd, value); });
}

TEST_F(AssemblerRISCV64Test, Mv) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::Mv, "addi {reg1}, {reg2}, 0"), "Mv");
}

TEST_F(AssemblerRISCV64Test, Not) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::Not, "xori {reg1}, {reg2}, -1"), "Not");
}

TEST_F(AssemblerRISCV64Test, Neg) {
  DriverStr(RepeatRR(&Riscv64Assembler::Neg, "sub {reg1}, x0, {reg2}"), "Neg");
}

TEST_F(AssemblerRISCV64Test, NegW) {
  DriverStr(RepeatRR(&Riscv64Assembler::NegW, "subw {reg1}, x0, {reg2}"), "Neg");
}

TEST_F(AssemblerRISCV64Test, SextB) {
  DriverStr(RepeatRR(&Riscv64Assembler::SextB, "sext.b {reg1}, {reg2}\n"), "SextB");
}

TEST_F(AssemblerRISCV64Test, SextB_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::SextB, "sext.b {reg1}, {reg2}\n"), "SextB_WithoutC");
}

// TODO: Add test `SextB_WithoutZbb` when `Slli()` and `Srai()` auto-forward to 16-bit functions.
TEST_F(AssemblerRISCV64Test, SextB_WithoutZbbAndC) {
  ScopedZbbAndCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::SextB, "sext.b {reg1}, {reg2}\n"), "SextB_WithoutZbbAndC");
}

TEST_F(AssemblerRISCV64Test, SextH) {
  DriverStr(RepeatRR(&Riscv64Assembler::SextH, "sext.h {reg1}, {reg2}\n"), "SextH");
}

TEST_F(AssemblerRISCV64Test, SextH_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::SextH, "sext.h {reg1}, {reg2}\n"), "SextH_WithoutC");
}

// TODO: Add test `SextH_WithoutZbb` when `Slli()` and `Srai()` auto-forward to 16-bit functions.
TEST_F(AssemblerRISCV64Test, SextH_WithoutZbbAndC) {
  ScopedZbbAndCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::SextH, "sext.h {reg1}, {reg2}\n"), "SextH_WithoutZbbAndC");
}

TEST_F(AssemblerRISCV64Test, SextW) {
  DriverStr(RepeatRR(&Riscv64Assembler::SextW, "sext.w {reg1}, {reg2}\n"), "SextW");
}

TEST_F(AssemblerRISCV64Test, SextW_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::SextW, "sext.w {reg1}, {reg2}\n"), "SextW_WithoutC");
}

TEST_F(AssemblerRISCV64Test, ZextB) {
  DriverStr(RepeatRR(&Riscv64Assembler::ZextB, "zext.b {reg1}, {reg2}"), "ZextB");
}

TEST_F(AssemblerRISCV64Test, ZextB_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZextB, "zext.b {reg1}, {reg2}"), "ZextB_WithoutC");
}

TEST_F(AssemblerRISCV64Test, ZextH) {
  DriverStr(RepeatRR(&Riscv64Assembler::ZextH, "zext.h {reg1}, {reg2}\n"), "ZextH");
}

TEST_F(AssemblerRISCV64Test, ZextH_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZextH, "zext.h {reg1}, {reg2}\n"), "ZextH_WithoutC");
}

// TODO: Add test `ZextH_WithoutZbb` when `Slli()` and `Srli()` auto-forward to 16-bit functions.
TEST_F(AssemblerRISCV64Test, ZextH_WithoutZbbAndC) {
  ScopedZbbAndCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZextH, "zext.h {reg1}, {reg2}\n"), "ZextH_WithoutZbbAndC");
}

TEST_F(AssemblerRISCV64Test, ZextW) {
  DriverStr(RepeatRR(&Riscv64Assembler::ZextW, "zext.w {reg1}, {reg2}\n"), "ZextW");
}

TEST_F(AssemblerRISCV64Test, ZextW_WithoutC) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZextW, "zext.w {reg1}, {reg2}\n"), "ZextW_WithoutC");
}

// TODO: Add test `ZextW_WithoutZba` when `Slli()` and `Srli()` auto-forward to 16-bit functions.
TEST_F(AssemblerRISCV64Test, ZextW_WithoutZbaAndC) {
  ScopedZbaAndCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::ZextW, "zext.w {reg1}, {reg2}\n"), "ZextW_WithoutZbaAndC");
}

TEST_F(AssemblerRISCV64Test, Seqz) {
  DriverStr(RepeatRR(&Riscv64Assembler::Seqz, "sltiu {reg1}, {reg2}, 1\n"), "Seqz");
}

TEST_F(AssemblerRISCV64Test, Snez) {
  DriverStr(RepeatRR(&Riscv64Assembler::Snez, "sltu {reg1}, zero, {reg2}\n"), "Snez");
}

TEST_F(AssemblerRISCV64Test, Sltz) {
  DriverStr(RepeatRR(&Riscv64Assembler::Sltz, "slt {reg1}, {reg2}, zero\n"), "Sltz");
}

TEST_F(AssemblerRISCV64Test, Sgtz) {
  DriverStr(RepeatRR(&Riscv64Assembler::Sgtz, "slt {reg1}, zero, {reg2}\n"), "Sgtz");
}

TEST_F(AssemblerRISCV64Test, FMvS) {
  DriverStr(RepeatFF(&Riscv64Assembler::FMvS, "fsgnj.s {reg1}, {reg2}, {reg2}\n"), "FMvS");
}

TEST_F(AssemblerRISCV64Test, FAbsS) {
  DriverStr(RepeatFF(&Riscv64Assembler::FAbsS, "fsgnjx.s {reg1}, {reg2}, {reg2}\n"), "FAbsS");
}

TEST_F(AssemblerRISCV64Test, FNegS) {
  DriverStr(RepeatFF(&Riscv64Assembler::FNegS, "fsgnjn.s {reg1}, {reg2}, {reg2}\n"), "FNegS");
}

TEST_F(AssemblerRISCV64Test, FMvD) {
  DriverStr(RepeatFF(&Riscv64Assembler::FMvD, "fsgnj.d {reg1}, {reg2}, {reg2}\n"), "FMvD");
}

TEST_F(AssemblerRISCV64Test, FAbsD) {
  DriverStr(RepeatFF(&Riscv64Assembler::FAbsD, "fsgnjx.d {reg1}, {reg2}, {reg2}\n"), "FAbsD");
}

TEST_F(AssemblerRISCV64Test, FNegD) {
  DriverStr(RepeatFF(&Riscv64Assembler::FNegD, "fsgnjn.d {reg1}, {reg2}, {reg2}\n"), "FNegD");
}

TEST_F(AssemblerRISCV64Test, Beqz) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Beqz, -11, 2, "beq {reg}, zero, {imm}\n"), "Beqz");
}

TEST_F(AssemblerRISCV64Test, Bnez) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Bnez, -11, 2, "bne {reg}, zero, {imm}\n"), "Bnez");
}

TEST_F(AssemblerRISCV64Test, Blez) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Blez, -11, 2, "bge zero, {reg}, {imm}\n"), "Blez");
}

TEST_F(AssemblerRISCV64Test, Bgez) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Bgez, -11, 2, "bge {reg}, zero, {imm}\n"), "Bgez");
}

TEST_F(AssemblerRISCV64Test, Bltz) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Bltz, -11, 2, "blt {reg}, zero, {imm}\n"), "Bltz");
}

TEST_F(AssemblerRISCV64Test, Bgtz) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRIbS(&Riscv64Assembler::Bgtz, -11, 2, "blt zero, {reg}, {imm}\n"), "Bgtz");
}

TEST_F(AssemblerRISCV64Test, Bgt) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bgt, -11, 2, "blt {reg2}, {reg1}, {imm}\n"), "Bgt");
}

TEST_F(AssemblerRISCV64Test, Ble) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Ble, -11, 2, "bge {reg2}, {reg1}, {imm}\n"), "Bge");
}

TEST_F(AssemblerRISCV64Test, Bgtu) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bgtu, -11, 2, "bltu {reg2}, {reg1}, {imm}\n"), "Bgtu");
}

TEST_F(AssemblerRISCV64Test, Bleu) {
  // TODO(riscv64): Change "-11, 2" to "-12, 1" for "C" Standard Extension.
  DriverStr(RepeatRRIbS(&Riscv64Assembler::Bleu, -11, 2, "bgeu {reg2}, {reg1}, {imm}\n"), "Bgeu");
}

TEST_F(AssemblerRISCV64Test, J) {
  ScopedCSuppression scs(this);
  // TODO(riscv64): Change "-19, 2" to "-20, 1" for "C" Standard Extension.
  DriverStr(RepeatIbS<int32_t>(&Riscv64Assembler::J, -19, 2, "j {imm}\n"), "J");
}

TEST_F(AssemblerRISCV64Test, JalRA) {
  // TODO(riscv64): Change "-19, 2" to "-20, 1" for "C" Standard Extension.
  DriverStr(RepeatIbS<int32_t>(&Riscv64Assembler::Jal, -19, 2, "jal {imm}\n"), "JalRA");
}

TEST_F(AssemblerRISCV64Test, Jr) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatR(&Riscv64Assembler::Jr, "jr {reg}\n"), "Jr");
}

TEST_F(AssemblerRISCV64Test, JalrRA) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatR(&Riscv64Assembler::Jalr, "jalr {reg}\n"), "JalrRA");
}

TEST_F(AssemblerRISCV64Test, Jalr0) {
  ScopedCSuppression scs(this);
  DriverStr(RepeatRR(&Riscv64Assembler::Jalr, "jalr {reg1}, {reg2}\n"), "Jalr0");
}

TEST_F(AssemblerRISCV64Test, Ret) {
  ScopedCSuppression scs(this);
  __ Ret();
  DriverStr("ret\n", "Ret");
}

TEST_F(AssemblerRISCV64Test, RdCycle) {
  DriverStr(RepeatR(&Riscv64Assembler::RdCycle, "rdcycle {reg}\n"), "RdCycle");
}

TEST_F(AssemblerRISCV64Test, RdTime) {
  DriverStr(RepeatR(&Riscv64Assembler::RdTime, "rdtime {reg}\n"), "RdTime");
}

TEST_F(AssemblerRISCV64Test, RdInstret) {
  DriverStr(RepeatR(&Riscv64Assembler::RdInstret, "rdinstret {reg}\n"), "RdInstret");
}

TEST_F(AssemblerRISCV64Test, Csrr) {
  TestCsrrXMacro(
      "Csrr", "csrr {reg}, {csr}", [&](uint32_t csr, XRegister rd) { __ Csrr(rd, csr); });
}

TEST_F(AssemblerRISCV64Test, Csrw) {
  TestCsrrXMacro(
      "Csrw", "csrw {csr}, {reg}", [&](uint32_t csr, XRegister rs) { __ Csrw(csr, rs); });
}

TEST_F(AssemblerRISCV64Test, Csrs) {
  TestCsrrXMacro(
      "Csrs", "csrs {csr}, {reg}", [&](uint32_t csr, XRegister rs) { __ Csrs(csr, rs); });
}

TEST_F(AssemblerRISCV64Test, Csrc) {
  TestCsrrXMacro(
      "Csrc", "csrc {csr}, {reg}", [&](uint32_t csr, XRegister rs) { __ Csrc(csr, rs); });
}

TEST_F(AssemblerRISCV64Test, Csrwi) {
  TestCsrrXiMacro(
      "Csrwi", "csrwi {csr}, {uimm}", [&](uint32_t csr, uint32_t uimm) { __ Csrwi(csr, uimm); });
}

TEST_F(AssemblerRISCV64Test, Csrsi) {
  TestCsrrXiMacro(
      "Csrsi", "csrsi {csr}, {uimm}", [&](uint32_t csr, uint32_t uimm) { __ Csrsi(csr, uimm); });
}

TEST_F(AssemblerRISCV64Test, Csrci) {
  TestCsrrXiMacro(
      "Csrci", "csrci {csr}, {uimm}", [&](uint32_t csr, uint32_t uimm) { __ Csrci(csr, uimm); });
}

TEST_F(AssemblerRISCV64Test, LoadConst32) {
  ScopedCSuppression scs(this);
  // `LoadConst32()` emits the same code sequences as `Li()` for 32-bit values.
  ScratchRegisterScope srs(GetAssembler());
  srs.ExcludeXRegister(TMP);
  srs.ExcludeXRegister(TMP2);
  DriverStr(RepeatRIb(&Riscv64Assembler::LoadConst32, -32, "li {reg}, {imm}"), "LoadConst32");
}

TEST_F(AssemblerRISCV64Test, LoadConst64) {
  ScopedMarchOverride smo(this, "-march=rv64imafd");
  TestLoadConst64("LoadConst64",
                  /*can_use_tmp=*/ true,
                  [&](XRegister rd, int64_t value) { __ LoadConst64(rd, value); });
}

TEST_F(AssemblerRISCV64Test, AddConst32) {
  ScopedCSuppression scs(this);
  auto emit_op = [&](XRegister rd, XRegister rs1, int64_t value) {
    __ AddConst32(rd, rs1, dchecked_integral_cast<int32_t>(value));
  };
  TestAddConst("AddConst32", 32, /*suffix=*/ "w", emit_op);
}

TEST_F(AssemblerRISCV64Test, AddConst64) {
  ScopedMarchOverride smo(this, "-march=rv64imafd");
  auto emit_op = [&](XRegister rd, XRegister rs1, int64_t value) {
    __ AddConst64(rd, rs1, value);
  };
  TestAddConst("AddConst64", 64, /*suffix=*/ "", emit_op);
}

TEST_F(AssemblerRISCV64Test, BcondForward3KiB) {
  ScopedCSuppression scs(this);
  TestBcondForward("BcondForward3KiB", 3 * KB, "1", GetPrintBcond());
}

TEST_F(AssemblerRISCV64Test, BcondForward3KiBBare) {
  ScopedCSuppression scs(this);
  TestBcondForward("BcondForward3KiB", 3 * KB, "1", GetPrintBcond(), /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, BcondBackward3KiB) {
  ScopedCSuppression scs(this);
  TestBcondBackward("BcondBackward3KiB", 3 * KB, "1", GetPrintBcond());
}

TEST_F(AssemblerRISCV64Test, BcondBackward3KiBBare) {
  ScopedCSuppression scs(this);
  TestBcondBackward("BcondBackward3KiB", 3 * KB, "1", GetPrintBcond(), /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, BcondForward5KiB) {
  ScopedCSuppression scs(this);
  TestBcondForward("BcondForward5KiB", 5 * KB, "1", GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BcondBackward5KiB) {
  ScopedCSuppression scs(this);
  TestBcondBackward("BcondBackward5KiB", 5 * KB, "1", GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BcondForward2MiB) {
  ScopedCSuppression scs(this);
  TestBcondForward("BcondForward2MiB", 2 * MB, "1", GetPrintBcondOppositeAndTail("2", "3"));
}

TEST_F(AssemblerRISCV64Test, BcondBackward2MiB) {
  ScopedCSuppression scs(this);
  TestBcondBackward("BcondBackward2MiB", 2 * MB, "1", GetPrintBcondOppositeAndTail("2", "3"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset13Forward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Forward("BeqA0A1MaxOffset13Forward",
                     MaxOffset13ForwardDistance() - /*BEQ*/ 4u,
                     "1",
                     GetPrintBcond());
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset13ForwardBare) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Forward("BeqA0A1MaxOffset13ForwardBare",
                     MaxOffset13ForwardDistance() - /*BEQ*/ 4u,
                     "1",
                     GetPrintBcond(),
                      /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset13Backward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Backward("BeqA0A1MaxOffset13Forward",
                      MaxOffset13BackwardDistance(),
                      "1",
                      GetPrintBcond());
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset13BackwardBare) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Backward("BeqA0A1MaxOffset13ForwardBare",
                      MaxOffset13BackwardDistance(),
                      "1",
                      GetPrintBcond(),
                      /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, BeqA0A1OverMaxOffset13Forward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Forward("BeqA0A1OverMaxOffset13Forward",
                     MaxOffset13ForwardDistance() - /*BEQ*/ 4u + /*Exceed max*/ 4u,
                     "1",
                     GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1OverMaxOffset13Backward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Backward("BeqA0A1OverMaxOffset13Forward",
                      MaxOffset13BackwardDistance() + /*Exceed max*/ 4u,
                      "1",
                      GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Forward("BeqA0A1MaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u,
                     "1",
                     GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1MaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Backward("BeqA0A1MaxOffset21Backward",
                      MaxOffset21BackwardDistance() - /*BNE*/ 4u,
                      "1",
                      GetPrintBcondOppositeAndJ("2"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1OverMaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Forward("BeqA0A1OverMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u + /*Exceed max*/ 4u,
                     "1",
                     GetPrintBcondOppositeAndTail("2", "3"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1OverMaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBeqA0A1Backward("BeqA0A1OverMaxOffset21Backward",
                      MaxOffset21BackwardDistance() - /*BNE*/ 4u + /*Exceed max*/ 4u,
                      "1",
                      GetPrintBcondOppositeAndTail("2", "3"));
}

TEST_F(AssemblerRISCV64Test, BeqA0A1AlmostCascade) {
  ScopedCSuppression scs(this);
  TestBeqA0A1MaybeCascade("BeqA0A1AlmostCascade", /*cascade=*/ false, GetPrintBcond());
}

TEST_F(AssemblerRISCV64Test, BeqA0A1Cascade) {
  ScopedCSuppression scs(this);
  TestBeqA0A1MaybeCascade(
      "BeqA0A1AlmostCascade", /*cascade=*/ true, GetPrintBcondOppositeAndJ("1"));
}

TEST_F(AssemblerRISCV64Test, BcondElimination) {
  ScopedCSuppression scs(this);
  Riscv64Label label;
  __ Bind(&label);
  __ Nop();
  for (XRegister reg : GetRegisters()) {
    __ Bne(reg, reg, &label);
    __ Blt(reg, reg, &label);
    __ Bgt(reg, reg, &label);
    __ Bltu(reg, reg, &label);
    __ Bgtu(reg, reg, &label);
  }
  DriverStr("nop\n", "BcondElimination");
}

TEST_F(AssemblerRISCV64Test, BcondUnconditional) {
  ScopedCSuppression scs(this);
  Riscv64Label label;
  __ Bind(&label);
  __ Nop();
  for (XRegister reg : GetRegisters()) {
    __ Beq(reg, reg, &label);
    __ Bge(reg, reg, &label);
    __ Ble(reg, reg, &label);
    __ Bleu(reg, reg, &label);
    __ Bgeu(reg, reg, &label);
  }
  std::string expected =
      "1:\n"
      "nop\n" +
      RepeatInsn(5u * GetRegisters().size(), "j 1b\n", []() {});
  DriverStr(expected, "BcondUnconditional");
}

TEST_F(AssemblerRISCV64Test, JalRdForward3KiB) {
  ScopedCSuppression scs(this);
  TestJalRdForward("JalRdForward3KiB", 3 * KB, "1", GetPrintJalRd());
}

TEST_F(AssemblerRISCV64Test, JalRdForward3KiBBare) {
  ScopedCSuppression scs(this);
  TestJalRdForward("JalRdForward3KiB", 3 * KB, "1", GetPrintJalRd(), /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, JalRdBackward3KiB) {
  ScopedCSuppression scs(this);
  TestJalRdBackward("JalRdBackward3KiB", 3 * KB, "1", GetPrintJalRd());
}

TEST_F(AssemblerRISCV64Test, JalRdBackward3KiBBare) {
  ScopedCSuppression scs(this);
  TestJalRdBackward("JalRdBackward3KiB", 3 * KB, "1", GetPrintJalRd(), /*is_bare=*/ true);
}

TEST_F(AssemblerRISCV64Test, JalRdForward2MiB) {
  ScopedCSuppression scs(this);
  TestJalRdForward("JalRdForward2MiB", 2 * MB, "1", GetPrintCallRd("2"));
}

TEST_F(AssemblerRISCV64Test, JalRdBackward2MiB) {
  ScopedCSuppression scs(this);
  TestJalRdBackward("JalRdBackward2MiB", 2 * MB, "1", GetPrintCallRd("2"));
}

TEST_F(AssemblerRISCV64Test, JForward3KiB) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JForward3KiB", 3 * KB, "1", GetEmitJ(), GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JForward3KiBBare) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JForward3KiB", 3 * KB, "1", GetEmitJ(/*is_bare=*/ true), GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JBackward3KiB) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JBackward3KiB", 3 * KB, "1", GetEmitJ(), GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JBackward3KiBBare) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JBackward3KiB", 3 * KB, "1", GetEmitJ(/*is_bare=*/ true), GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JForward2MiB) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JForward2MiB", 2 * MB, "1", GetEmitJ(), GetPrintTail("2"));
}

TEST_F(AssemblerRISCV64Test, JBackward2MiB) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JBackward2MiB", 2 * MB, "1", GetEmitJ(), GetPrintTail("2"));
}

TEST_F(AssemblerRISCV64Test, JMaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u,
                     "1",
                     GetEmitJ(),
                     GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JMaxOffset21ForwardBare) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u,
                     "1",
                     GetEmitJ(/*is_bare=*/ true),
                     GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JMaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JMaxOffset21Backward",
                      MaxOffset21BackwardDistance(),
                      "1",
                      GetEmitJ(),
                      GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JMaxOffset21BackwardBare) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JMaxOffset21Backward",
                      MaxOffset21BackwardDistance(),
                      "1",
                      GetEmitJ(/*is_bare=*/ true),
                      GetPrintJ());
}

TEST_F(AssemblerRISCV64Test, JOverMaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBuncondForward("JOverMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u + /*Exceed max*/ 4u,
                     "1",
                     GetEmitJ(),
                     GetPrintTail("2"));
}

TEST_F(AssemblerRISCV64Test, JOverMaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("JMaxOffset21Backward",
                      MaxOffset21BackwardDistance() + /*Exceed max*/ 4u,
                      "1",
                      GetEmitJ(),
                      GetPrintTail("2"));
}

TEST_F(AssemblerRISCV64Test, CallForward3KiB) {
  ScopedCSuppression scs(this);
  TestBuncondForward("CallForward3KiB", 3 * KB, "1", GetEmitJal(), GetPrintJal());
}

TEST_F(AssemblerRISCV64Test, CallBackward3KiB) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("CallBackward3KiB", 3 * KB, "1", GetEmitJal(), GetPrintJal());
}

TEST_F(AssemblerRISCV64Test, CallForward2MiB) {
  ScopedCSuppression scs(this);
  TestBuncondForward("CallForward2MiB", 2 * MB, "1", GetEmitJal(), GetPrintCall("2"));
}

TEST_F(AssemblerRISCV64Test, CallBackward2MiB) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("CallBackward2MiB", 2 * MB, "1", GetEmitJal(), GetPrintCall("2"));
}

TEST_F(AssemblerRISCV64Test, CallMaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBuncondForward("CallMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u,
                     "1",
                     GetEmitJal(),
                     GetPrintJal());
}

TEST_F(AssemblerRISCV64Test, CallMaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("CallMaxOffset21Backward",
                      MaxOffset21BackwardDistance(),
                      "1",
                      GetEmitJal(),
                      GetPrintJal());
}

TEST_F(AssemblerRISCV64Test, CallOverMaxOffset21Forward) {
  ScopedCSuppression scs(this);
  TestBuncondForward("CallOverMaxOffset21Forward",
                     MaxOffset21ForwardDistance() - /*J*/ 4u + /*Exceed max*/ 4u,
                     "1",
                     GetEmitJal(),
                     GetPrintCall("2"));
}

TEST_F(AssemblerRISCV64Test, CallOverMaxOffset21Backward) {
  ScopedCSuppression scs(this);
  TestBuncondBackward("CallMaxOffset21Backward",
                      MaxOffset21BackwardDistance() + /*Exceed max*/ 4u,
                      "1",
                      GetEmitJal(),
                      GetPrintCall("2"));
}

TEST_F(AssemblerRISCV64Test, Loadb) {
  ScopedCSuppression scs(this);  // Suppress 16-bit instructions for address formation.
  TestLoadStoreArbitraryOffset("Loadb", "lb", &Riscv64Assembler::Loadb, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadh) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Loadh", "lh", &Riscv64Assembler::Loadh, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadw) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Loadw", "lw", &Riscv64Assembler::Loadw, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadd) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Loadd", "ld", &Riscv64Assembler::Loadd, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadbu) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Loadbu", "lbu", &Riscv64Assembler::Loadbu, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadhu) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Loadhu", "lhu", &Riscv64Assembler::Loadhu, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Loadwu) {
  ScopedCSuppression scs(this);  // Suppress 16-bit instructions for address formation.
  TestLoadStoreArbitraryOffset("Loadwu", "lwu", &Riscv64Assembler::Loadwu, /*is_store=*/ false);
}

TEST_F(AssemblerRISCV64Test, Storeb) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Storeb", "sb", &Riscv64Assembler::Storeb, /*is_store=*/ true);
}

TEST_F(AssemblerRISCV64Test, Storeh) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Storeh", "sh", &Riscv64Assembler::Storeh, /*is_store=*/ true);
}

TEST_F(AssemblerRISCV64Test, Storew) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Storew", "sw", &Riscv64Assembler::Storew, /*is_store=*/ true);
}

TEST_F(AssemblerRISCV64Test, Stored) {
  ScopedCSuppression scs(this);
  TestLoadStoreArbitraryOffset("Stored", "sd", &Riscv64Assembler::Stored, /*is_store=*/ true);
}

TEST_F(AssemblerRISCV64Test, FLoadw) {
  ScopedCSuppression scs(this);  // Suppress 16-bit instructions for address formation.
  TestFPLoadStoreArbitraryOffset("FLoadw", "flw", &Riscv64Assembler::FLoadw);
}

TEST_F(AssemblerRISCV64Test, FLoadd) {
  ScopedCSuppression scs(this);
  TestFPLoadStoreArbitraryOffset("FLoadd", "fld", &Riscv64Assembler::FLoadd);
}

TEST_F(AssemblerRISCV64Test, FStorew) {
  ScopedCSuppression scs(this);  // Suppress 16-bit instructions for address formation.
  TestFPLoadStoreArbitraryOffset("FStorew", "fsw", &Riscv64Assembler::FStorew);
}

TEST_F(AssemblerRISCV64Test, FStored) {
  ScopedCSuppression scs(this);
  TestFPLoadStoreArbitraryOffset("FStored", "fsd", &Riscv64Assembler::FStored);
}

TEST_F(AssemblerRISCV64Test, Unimp) {
  ScopedCSuppression scs(this);
  __ Unimp();
  DriverStr("unimp\n", "Unimp");
}

TEST_F(AssemblerRISCV64Test, LoadLabelAddress) {
  std::string expected;
  constexpr size_t kNumLoadsForward = 4 * KB;
  constexpr size_t kNumLoadsBackward = 4 * KB;
  Riscv64Label label;
  auto emit_batch = [&](size_t num_loads, const std::string& target_label) {
    for (size_t i = 0; i != num_loads; ++i) {
      // Cycle through non-Zero registers.
      XRegister rd = enum_cast<XRegister>((i % (kNumberOfXRegisters - 1)) + 1);
      DCHECK_NE(rd, Zero);
      std::string rd_name = GetRegisterName(rd);
      __ LoadLabelAddress(rd, &label);
      expected += "1:\n";
      expected += ART_FORMAT("auipc {}, %pcrel_hi({})\n", rd_name, target_label);
      expected += ART_FORMAT("addi {}, {}, %pcrel_lo(1b)\n", rd_name, rd_name);
    }
  };
  emit_batch(kNumLoadsForward, "2f");
  __ Bind(&label);
  expected += "2:\n";
  emit_batch(kNumLoadsBackward, "2b");
  DriverStr(expected, "LoadLabelAddress");
}

TEST_F(AssemblerRISCV64Test, LoadLiteralWithPaddingForLong) {
  ScopedCSuppression scs(this);
  TestLoadLiteral("LoadLiteralWithPaddingForLong", /*with_padding_for_long=*/ true);
}

TEST_F(AssemblerRISCV64Test, LoadLiteralWithoutPaddingForLong) {
  ScopedCSuppression scs(this);
  TestLoadLiteral("LoadLiteralWithoutPaddingForLong", /*with_padding_for_long=*/ false);
}

TEST_F(AssemblerRISCV64Test, JumpTable) {
  ScopedCSuppression scs(this);
  std::string expected;
  expected += EmitNops(sizeof(uint32_t));
  Riscv64Label targets[4];
  uint32_t target_locations[4];
  JumpTable* jump_table = __ CreateJumpTable(ArenaVector<Riscv64Label*>(
      {&targets[0], &targets[1], &targets[2], &targets[3]}, __ GetAllocator()->Adapter()));
  for (size_t i : {0, 1, 2, 3}) {
    target_locations[i] = __ CodeSize();
    __ Bind(&targets[i]);
    expected += std::to_string(i) + ":\n";
    expected += EmitNops(sizeof(uint32_t));
  }
  __ LoadLabelAddress(A0, jump_table->GetLabel());
  expected += "4:\n"
              "auipc a0, %pcrel_hi(5f)\n"
              "addi a0, a0, %pcrel_lo(4b)\n";
  expected += EmitNops(sizeof(uint32_t));
  uint32_t label5_location = __ CodeSize();
  auto target_offset = [&](size_t i) {
    // Even with `-mno-relax`, clang assembler does not fully resolve `.4byte 0b - 5b`
    // and emits a relocation, so we need to calculate target offsets ourselves.
    return std::to_string(static_cast<int64_t>(target_locations[i] - label5_location));
  };
  expected += "5:\n"
              ".4byte " + target_offset(0) + "\n"
              ".4byte " + target_offset(1) + "\n"
              ".4byte " + target_offset(2) + "\n"
              ".4byte " + target_offset(3) + "\n";
  DriverStr(expected, "JumpTable");
}

TEST_F(AssemblerRISCV64Test, ScratchRegisters) {
  ScratchRegisterScope srs(GetAssembler());
  ASSERT_EQ(2u, srs.AvailableXRegisters());  // Default: TMP(T6) and TMP2(T5).
  ASSERT_EQ(1u, srs.AvailableFRegisters());  // Default: FTMP(FT11).

  XRegister tmp = srs.AllocateXRegister();
  EXPECT_EQ(TMP, tmp);
  XRegister tmp2 = srs.AllocateXRegister();
  EXPECT_EQ(TMP2, tmp2);
  ASSERT_EQ(0u, srs.AvailableXRegisters());

  FRegister ftmp = srs.AllocateFRegister();
  EXPECT_EQ(FTMP, ftmp);
  ASSERT_EQ(0u, srs.AvailableFRegisters());

  // Test nesting.
  srs.FreeXRegister(A0);
  srs.FreeXRegister(A1);
  srs.FreeFRegister(FA0);
  srs.FreeFRegister(FA1);
  ASSERT_EQ(2u, srs.AvailableXRegisters());
  ASSERT_EQ(2u, srs.AvailableFRegisters());
  {
    ScratchRegisterScope srs2(GetAssembler());
    ASSERT_EQ(2u, srs2.AvailableXRegisters());
    ASSERT_EQ(2u, srs2.AvailableFRegisters());
    XRegister a1 = srs2.AllocateXRegister();
    EXPECT_EQ(A1, a1);
    XRegister a0 = srs2.AllocateXRegister();
    EXPECT_EQ(A0, a0);
    ASSERT_EQ(0u, srs2.AvailableXRegisters());
    FRegister fa1 = srs2.AllocateFRegister();
    EXPECT_EQ(FA1, fa1);
    FRegister fa0 = srs2.AllocateFRegister();
    EXPECT_EQ(FA0, fa0);
    ASSERT_EQ(0u, srs2.AvailableFRegisters());
  }
  ASSERT_EQ(2u, srs.AvailableXRegisters());
  ASSERT_EQ(2u, srs.AvailableFRegisters());

  srs.IncludeXRegister(A0);  // No-op as the register was already available.
  ASSERT_EQ(2u, srs.AvailableXRegisters());
  srs.IncludeFRegister(FA0);  // No-op as the register was already available.
  ASSERT_EQ(2u, srs.AvailableFRegisters());
  srs.IncludeXRegister(S0);
  ASSERT_EQ(3u, srs.AvailableXRegisters());
  srs.IncludeFRegister(FS0);
  ASSERT_EQ(3u, srs.AvailableFRegisters());

  srs.ExcludeXRegister(S1);  // No-op as the register was not available.
  ASSERT_EQ(3u, srs.AvailableXRegisters());
  srs.ExcludeFRegister(FS1);  // No-op as the register was not available.
  ASSERT_EQ(3u, srs.AvailableFRegisters());
  srs.ExcludeXRegister(A0);
  ASSERT_EQ(2u, srs.AvailableXRegisters());
  srs.ExcludeFRegister(FA0);
  ASSERT_EQ(2u, srs.AvailableFRegisters());
}

#undef __

}  // namespace riscv64
}  // namespace art
