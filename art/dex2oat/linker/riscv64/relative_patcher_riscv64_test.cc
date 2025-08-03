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

#include "relative_patcher_riscv64.h"

#include "linker/linker_patch.h"
#include "linker/relative_patcher_test.h"
#include "oat/oat_quick_method_header.h"

namespace art {
namespace linker {

class Riscv64RelativePatcherTest : public RelativePatcherTest {
 public:
  Riscv64RelativePatcherTest()
      : RelativePatcherTest(InstructionSet::kRiscv64, "default") { }

 protected:
  // C.NOP instruction.
  static constexpr uint32_t kCNopInsn = 0x0001u;
  static constexpr size_t kCNopSize = 2u;

  // Placeholder instructions with unset (zero) registers and immediates.
  static constexpr uint32_t kAuipcInsn = 0x00000017u;
  static constexpr uint32_t kAddiInsn = 0x00000013u;
  static constexpr uint32_t kLwuInsn = 0x00006003u;
  static constexpr uint32_t kLdInsn = 0x00003003u;

  // Placeholder offset encoded in AUIPC and used before patching.
  static constexpr uint32_t kUnpatchedOffset = 0x12345678u;

  static void PushBackInsn16(std::vector<uint8_t>* code, uint32_t insn16) {
    const uint8_t insn_code[] = {
        static_cast<uint8_t>(insn16),
        static_cast<uint8_t>(insn16 >> 8),
    };
    code->insert(code->end(), insn_code, insn_code + sizeof(insn_code));
  }

  static void PushBackInsn32(std::vector<uint8_t>* code, uint32_t insn32) {
    const uint8_t insn_code[] = {
        static_cast<uint8_t>(insn32),
        static_cast<uint8_t>(insn32 >> 8),
        static_cast<uint8_t>(insn32 >> 16),
        static_cast<uint8_t>(insn32 >> 24),
    };
    code->insert(code->end(), insn_code, insn_code + sizeof(insn_code));
  }

  uint32_t GetMethodOffset(uint32_t method_idx) {
    auto result = method_offset_map_.FindMethodOffset(MethodRef(method_idx));
    CHECK(result.first);
    CHECK_ALIGNED(result.second, 4u);
    return result.second;
  }

  static constexpr uint32_t ExtractRs1ToRd(uint32_t insn) {
    // The `rs1` is in bits 15..19 and we need to move it to bits 7..11.
    return (insn >> (15 - 7)) & (0x1fu << 7);
  }

  std::vector<uint8_t> GenNopsAndAuipcAndUse(size_t start_cnops,
                                             size_t mid_cnops,
                                             uint32_t method_offset,
                                             uint32_t target_offset,
                                             uint32_t use_insn) {
    CHECK_ALIGNED(method_offset, 4u);
    uint32_t auipc_offset = method_offset + start_cnops * kCNopSize;
    uint32_t offset = target_offset - auipc_offset;
    if (offset != /* unpatched */ 0x12345678u) {
      CHECK_ALIGNED(target_offset, 4u);
    }
    CHECK_EQ(use_insn & 0xfff00000u, 0u);
    // Prepare `imm12` for `use_insn` and `imm20` for AUIPC, adjusted for sign-extension of `imm12`.
    uint32_t imm12 = offset & 0xfffu;
    uint32_t imm20 = (offset >> 12) + ((offset >> 11) & 1u);
    // Prepare the AUIPC and use instruction.
    DCHECK_EQ(use_insn & 0xfff00000u, 0u);    // Check that `imm12` in `use_insn` is empty.
    use_insn |= imm12 << 20;                  // Update `imm12` in `use_insn`.
    uint32_t auipc = kAuipcInsn |             // AUIPC rd, imm20
        ExtractRs1ToRd(use_insn) |            // where `rd` is `rs1` from `use_insn`.
        (imm20 << 12);
    // Create the code.
    std::vector<uint8_t> result;
    result.reserve((start_cnops + mid_cnops) * kCNopSize + 8u);
    for (size_t i = 0; i != start_cnops; ++i) {
      PushBackInsn16(&result, kCNopInsn);
    }
    PushBackInsn32(&result, auipc);
    for (size_t i = 0; i != mid_cnops; ++i) {
      PushBackInsn16(&result, kCNopInsn);
    }
    PushBackInsn32(&result, use_insn);
    return result;
  }

  std::vector<uint8_t> GenNopsAndAuipcAndUseUnpatched(size_t start_cnops,
                                                      size_t mid_cnops,
                                                      uint32_t use_insn) {
    uint32_t target_offset = start_cnops * kCNopSize + kUnpatchedOffset;
    return GenNopsAndAuipcAndUse(start_cnops, mid_cnops, 0u, target_offset, use_insn);
  }

  void TestNopsAuipcAddi(size_t start_cnops, size_t mid_cnops, uint32_t string_offset) {
    constexpr uint32_t kStringIndex = 1u;
    string_index_to_offset_map_.Put(kStringIndex, string_offset);
    constexpr uint32_t kAddi = kAddiInsn | (10 << 15) | (11 << 7);  // ADDI A1, A0, <unfilled>
    auto code = GenNopsAndAuipcAndUseUnpatched(start_cnops, mid_cnops, kAddi);
    size_t auipc_offset = start_cnops * kCNopSize;
    size_t addi_offset = auipc_offset + 4u + mid_cnops * kCNopSize;
    const LinkerPatch patches[] = {
        LinkerPatch::RelativeStringPatch(auipc_offset, nullptr, auipc_offset, kStringIndex),
        LinkerPatch::RelativeStringPatch(addi_offset, nullptr, auipc_offset, kStringIndex),
    };
    AddCompiledMethod(MethodRef(1u),
                      ArrayRef<const uint8_t>(code),
                      ArrayRef<const LinkerPatch>(patches));
    Link();

    uint32_t method1_offset = GetMethodOffset(1u);
    auto expected_code =
        GenNopsAndAuipcAndUse(start_cnops, mid_cnops, method1_offset, string_offset, kAddi);
    EXPECT_TRUE(CheckLinkedMethod(MethodRef(1u), ArrayRef<const uint8_t>(expected_code)));
  }

  void TestNopsAuipcLwu(
      size_t start_cnops, size_t mid_cnops, uint32_t bss_begin, uint32_t string_entry_offset) {
    constexpr uint32_t kStringIndex = 1u;
    string_index_to_offset_map_.Put(kStringIndex, string_entry_offset);
    bss_begin_ = bss_begin;
    constexpr uint32_t kLwu = kLwuInsn | (10 << 15) | (10 << 7);  // LWU A0, A0, <unfilled>
    auto code = GenNopsAndAuipcAndUseUnpatched(start_cnops, mid_cnops, kLwu);
    size_t auipc_offset = start_cnops * kCNopSize;
    size_t lwu_offset = auipc_offset + 4u + mid_cnops * kCNopSize;
    const LinkerPatch patches[] = {
        LinkerPatch::StringBssEntryPatch(auipc_offset, nullptr, auipc_offset, kStringIndex),
        LinkerPatch::StringBssEntryPatch(lwu_offset, nullptr, auipc_offset, kStringIndex),
    };
    AddCompiledMethod(MethodRef(1u),
                      ArrayRef<const uint8_t>(code),
                      ArrayRef<const LinkerPatch>(patches));
    Link();

    uint32_t method1_offset = GetMethodOffset(1u);
    uint32_t target_offset = bss_begin_ + string_entry_offset;
    auto expected_code =
        GenNopsAndAuipcAndUse(start_cnops, mid_cnops, method1_offset, target_offset, kLwu);
    EXPECT_TRUE(CheckLinkedMethod(MethodRef(1u), ArrayRef<const uint8_t>(expected_code)));
  }

  void TestNopsAuipcLd(
      size_t start_cnops, size_t mid_cnops, uint32_t bss_begin, uint32_t method_entry_offset) {
    constexpr uint32_t kMethodIndex = 100u;
    method_index_to_offset_map_.Put(kMethodIndex, method_entry_offset);
    bss_begin_ = bss_begin;
    constexpr uint32_t kLd = kLdInsn | (11 << 15) | (10 << 7);  // LD A0, A1, <unfilled>
    auto code = GenNopsAndAuipcAndUseUnpatched(start_cnops, mid_cnops, kLd);
    size_t auipc_offset = start_cnops * kCNopSize;
    size_t ld_offset = auipc_offset + 4u + mid_cnops * kCNopSize;
    const LinkerPatch patches[] = {
        LinkerPatch::MethodBssEntryPatch(auipc_offset, nullptr, auipc_offset, kMethodIndex),
        LinkerPatch::MethodBssEntryPatch(ld_offset, nullptr, auipc_offset, kMethodIndex),
    };
    AddCompiledMethod(MethodRef(1u),
                      ArrayRef<const uint8_t>(code),
                      ArrayRef<const LinkerPatch>(patches));
    Link();

    uint32_t method1_offset = GetMethodOffset(1u);
    uint32_t target_offset = bss_begin_ + method_entry_offset;
    auto expected_code =
        GenNopsAndAuipcAndUse(start_cnops, mid_cnops, method1_offset, target_offset, kLd);
    EXPECT_TRUE(CheckLinkedMethod(MethodRef(1u), ArrayRef<const uint8_t>(expected_code)));
  }
};

TEST_F(Riscv64RelativePatcherTest, StringReference) {
  for (size_t start_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
    for (size_t mid_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
      for (uint32_t string_offset : { 0x12345678u, -0x12345678u, 0x123457fcu, 0x12345800u}) {
        Reset();
        TestNopsAuipcAddi(start_cnops, mid_cnops, string_offset);
      }
    }
  }
}

TEST_F(Riscv64RelativePatcherTest, StringBssEntry) {
  for (size_t start_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
    for (size_t mid_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
      for (uint32_t bss_begin : { 0x12345678u, -0x12345678u, 0x10000000u, 0x12345000u }) {
        for (uint32_t string_entry_offset : { 0x1234u, 0x4444u, 0x37fcu, 0x3800u }) {
          Reset();
          TestNopsAuipcLwu(start_cnops, mid_cnops, bss_begin, string_entry_offset);
        }
      }
    }
  }
}

TEST_F(Riscv64RelativePatcherTest, MethodBssEntry) {
  for (size_t start_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
    for (size_t mid_cnops : {0, 1, 2, 3, 4, 5, 6, 7}) {
      for (uint32_t bss_begin : { 0x12345678u, -0x12345678u, 0x10000000u, 0x12345000u }) {
        for (uint32_t method_entry_offset : { 0x1234u, 0x4444u, 0x37f8u, 0x3800u }) {
          Reset();
          TestNopsAuipcLd(start_cnops, mid_cnops, bss_begin, method_entry_offset);
        }
      }
    }
  }
}

}  // namespace linker
}  // namespace art
