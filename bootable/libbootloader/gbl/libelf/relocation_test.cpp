/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <vector>

#include "gtest/gtest.h"

#include "elf/relocation.h"
#include "libelf/elf.h"

template <typename T>
struct DtTags {};

template <>
struct DtTags<Elf64_Rela> {
  static constexpr uint64_t kTableTag = DT_RELA;
  static constexpr uint64_t kTableSizeTag = DT_RELASZ;
};

template <>
struct DtTags<Elf64_Rel> {
  static constexpr uint64_t kTableTag = DT_REL;
  static constexpr uint64_t kTableSizeTag = DT_RELSZ;
};

template <size_t num_relocs, typename RelocType>
class Program {
 public:
  struct DynamicSection {
    Elf64_Dyn symtab;
    Elf64_Dyn relx;
    Elf64_Dyn relx_size;
    Elf64_Dyn other_entry;
    Elf64_Dyn end;
  };

  Program() {
    dynamic_section_ = {
        .symtab =
            {
                .d_tag = DT_SYMTAB,
                .d_un = {.d_ptr = offsetof(ThisType, symtab_)},
            },
        .relx =
            {
                .d_tag = DtTags<RelocType>::kTableTag,
                .d_un = {.d_ptr = offsetof(ThisType, table_)},
            },
        .relx_size =
            {
                .d_tag = DtTags<RelocType>::kTableSizeTag,
                .d_un = {.d_ptr = sizeof(table_)},
            },
        .other_entry =
            {
                .d_tag = DT_NULL,
            },
        .end =
            {
                .d_tag = DT_NULL,
            },
    };

    for (size_t i = 0; i < num_relocs; i++) {
      table_[i].r_offset = offsetof(ThisType, relocated_addr_table_) + i * sizeof(uint64_t);
    }
  }

  uint64_t GetRelocatedAddr(size_t index) { return relocated_addr_table_[index]; }

  void SetRelocationInfo(size_t index, uint64_t type, uint64_t addend) {
    ASSERT_LE(index, num_relocs);
    table_[index].r_info = type;
    SetAddend(table_[index], relocated_addr_table_[index], addend);
  }

  uint64_t DynamicSectionAddr() { return reinterpret_cast<uint64_t>(&dynamic_section_); }

  uint64_t ProgramBase() { return reinterpret_cast<uint64_t>(this); }

  DynamicSection& dynamic_section() { return dynamic_section_; }

 public:
  using ThisType = Program<num_relocs, RelocType>;
  DynamicSection dynamic_section_;
  Elf64_Sym symtab_;
  RelocType table_[num_relocs];
  uint64_t relocated_addr_table_[num_relocs];

  void SetAddend(Elf64_Rela& entry, uint64_t&, uint64_t addend) { entry.r_addend = addend; }

  void SetAddend(Elf64_Rel&, uint64_t& relocated_addr, uint64_t addend) { relocated_addr = addend; }
};

template <typename T>
void TestRelxRelocation() {
  Program<2, T> program;

  const uint64_t kAddend_0 = 0x100;
  program.SetRelocationInfo(0, R_RISCV_RELATIVE, kAddend_0);
  const uint64_t kAddend_1 = 0x200;
  program.SetRelocationInfo(1, R_RISCV_RELATIVE, kAddend_1);

  EXPECT_TRUE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));

  EXPECT_EQ(program.GetRelocatedAddr(0), program.ProgramBase() + kAddend_0);
  EXPECT_EQ(program.GetRelocatedAddr(1), program.ProgramBase() + kAddend_1);
}

TEST(RelocationTest, RelaRelocation) {
  TestRelxRelocation<Elf64_Rela>();
}

TEST(RelocationTest, RelRelocation) {
  TestRelxRelocation<Elf64_Rel>();
}

template <typename T>
void TestUnrecognizeRelcoationType() {
  Program<1, T> program;
  program.SetRelocationInfo(0, R_RISCV_RELATIVE + 1, 0x1000);
  EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
}

TEST(RelocationTest, RelaUnrecognizeRelcoationType) {
  TestUnrecognizeRelcoationType<Elf64_Rela>();
}

TEST(RelocationTest, RelUnrecognizeRelcoationType) {
  TestUnrecognizeRelcoationType<Elf64_Rel>();
}

template <typename T>
void TestRelxIncompleteInformation() {
  {
    Program<1, T> program;
    program.SetRelocationInfo(0, R_RISCV_RELATIVE, 0x1000);
    // Missing table size.
    program.dynamic_section().relx_size.d_tag = DT_HASH;  // Don't care
    EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
  }

  {
    Program<1, T> program;
    program.SetRelocationInfo(0, R_RISCV_RELATIVE, 0x1000);
    // Missing table itself.
    program.dynamic_section().relx.d_tag = DT_HASH;  // Don't care
    EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
  }
}

TEST(RelocationTest, TestRelaIncompleteInformation) {
  TestRelxIncompleteInformation<Elf64_Rela>();
}

TEST(RelocationTest, TestRelIncompleteInformation) {
  TestRelxIncompleteInformation<Elf64_Rel>();
}

TEST(RelocationTest, PltUnsupported) {
  Program<1, Elf64_Rela> program;
  program.SetRelocationInfo(0, R_RISCV_RELATIVE, 0x1000);
  program.dynamic_section().other_entry.d_tag = DT_JMPREL;
  EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
}

TEST(RelocationTest, RelrUnsupported) {
  Program<1, Elf64_Rela> program;
  program.SetRelocationInfo(0, R_RISCV_RELATIVE, 0x1000);
  program.dynamic_section().other_entry.d_tag = DT_RELR;
  EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
}

TEST(RelocationTest, MissingSymtab) {
  Program<1, Elf64_Rela> program;
  program.SetRelocationInfo(0, R_RISCV_RELATIVE, 0x1000);
  program.dynamic_section().symtab.d_tag = DT_HASH;  // Don't care
  EXPECT_FALSE(ApplyRelocation(program.ProgramBase(), program.DynamicSectionAddr()));
}
