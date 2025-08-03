/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <elf.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>

#include <memory>
#include <vector>

#include <android-base/unique_fd.h>
#include <gtest/gtest.h>

#include <unwindstack/ElfInterface.h>

#include "DwarfEncoding.h"
#include "ElfInterfaceArm.h"
#include "MemoryRange.h"

#include "ElfFake.h"
#include "ElfTestUtils.h"
#include "utils/MemoryFake.h"

#if !defined(PT_ARM_EXIDX)
#define PT_ARM_EXIDX 0x70000001
#endif

#if !defined(EM_AARCH64)
#define EM_AARCH64 183
#endif

#if __has_feature(address_sanitizer)
// There is a test that tries to allocate a large value, allow it to fail
// if asan is enabled.
extern "C" const char* __asan_default_options() {
  return "allocator_may_return_null=1";
}
#endif

namespace unwindstack {

class ElfInterfaceTest : public ::testing::Test {
 protected:
  void SetUp() override {
    fake_memory_ = new MemoryFake;
    memory_.reset(fake_memory_);
  }

  void SetStringMemory(uint64_t offset, const char* string) {
    fake_memory_->SetMemory(offset, string, strlen(string) + 1);
  }

  template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
  void SinglePtLoad();

  template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
  void MultipleExecutablePtLoads();

  template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
  void MultipleExecutablePtLoadsIncrementsNotSizeOfPhdr();

  template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
  void NonExecutablePtLoads();

  template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
  void ManyPhdrs();

  enum SonameTestEnum : uint8_t {
    SONAME_NORMAL,
    SONAME_DTNULL_AFTER,
    SONAME_DTSIZE_SMALL,
    SONAME_MISSING_MAP,
  };

  template <typename Ehdr, typename Phdr, typename Shdr, typename Dyn>
  void SonameInit(SonameTestEnum test_type = SONAME_NORMAL);

  template <typename ElfInterfaceType>
  void Soname();

  template <typename ElfInterfaceType>
  void SonameAfterDtNull();

  template <typename ElfInterfaceType>
  void SonameSize();

  template <typename ElfInterfaceType>
  void SonameMissingMap();

  template <typename ElfType>
  void InitHeadersEhFrameTest();

  template <typename ElfType>
  void InitHeadersDebugFrame();

  template <typename ElfType>
  void InitHeadersEhFrameFail();

  template <typename ElfType>
  void InitHeadersDebugFrameFail();

  template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
  void InitProgramHeadersMalformed();

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersMalformed();

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersMalformedSymData();

  template <typename Ehdr, typename Shdr, typename Sym, typename ElfInterfaceType>
  void InitSectionHeaders(uint64_t entry_size);

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersOffsets();

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersOffsetsEhFrameSectionBias(uint64_t addr, uint64_t offset,
                                                   int64_t expected_bias);

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersOffsetsEhFrameHdrSectionBias(uint64_t addr, uint64_t offset,
                                                      int64_t expected_bias);

  template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
  void InitSectionHeadersOffsetsDebugFrameSectionBias(uint64_t addr, uint64_t offset,
                                                      int64_t expected_bias);

  template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
  void CheckGnuEhFrame(uint64_t addr, uint64_t offset, int64_t expected_bias);

  template <typename Sym>
  void InitSym(uint64_t offset, uint32_t value, uint32_t size, uint32_t name_offset,
               uint64_t sym_offset, const char* name);

  template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
  void BuildID();

  template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
  void BuildIDTwoNotes();

  template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
  void BuildIDSectionTooSmallForName();

  template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
  void BuildIDSectionTooSmallForDesc();

  template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
  void BuildIDSectionTooSmallForHeader();

  template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
  void CheckLoadBiasInFirstPhdr(int64_t load_bias);

  template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
  void CheckLoadBiasInFirstExecPhdr(uint64_t offset, uint64_t vaddr, int64_t load_bias);

  MemoryFake* fake_memory_;
  std::shared_ptr<Memory> memory_;
};

template <typename Sym>
void ElfInterfaceTest::InitSym(uint64_t offset, uint32_t value, uint32_t size, uint32_t name_offset,
                               uint64_t sym_offset, const char* name) {
  Sym sym = {};
  sym.st_info = STT_FUNC;
  sym.st_value = value;
  sym.st_size = size;
  sym.st_name = name_offset;
  sym.st_shndx = SHN_COMMON;

  fake_memory_->SetMemory(offset, &sym, sizeof(sym));
  fake_memory_->SetMemory(sym_offset + name_offset, name, strlen(name) + 1);
}

template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
void ElfInterfaceTest::SinglePtLoad() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 1;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x2000, load_bias);

  const std::unordered_map<uint64_t, LoadInfo>& pt_loads = elf->pt_loads();
  ASSERT_EQ(1U, pt_loads.size());
  LoadInfo load_data = pt_loads.at(0);
  ASSERT_EQ(0U, load_data.offset);
  ASSERT_EQ(0x2000U, load_data.table_offset);
  ASSERT_EQ(0x10000U, load_data.table_size);
}

TEST_F(ElfInterfaceTest, single_pt_load_32) {
  SinglePtLoad<Elf32_Ehdr, Elf32_Phdr, Elf32_Dyn, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, single_pt_load_64) {
  SinglePtLoad<Elf64_Ehdr, Elf64_Phdr, Elf64_Dyn, ElfInterface64>();
}

template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
void ElfInterfaceTest::MultipleExecutablePtLoads() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 3;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x1000;
  phdr.p_vaddr = 0x2001;
  phdr.p_memsz = 0x10001;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1001;
  fake_memory_->SetMemory(0x100 + sizeof(phdr), &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x2000;
  phdr.p_vaddr = 0x2002;
  phdr.p_memsz = 0x10002;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1002;
  fake_memory_->SetMemory(0x100 + 2 * sizeof(phdr), &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x2000, load_bias);

  const std::unordered_map<uint64_t, LoadInfo>& pt_loads = elf->pt_loads();
  ASSERT_EQ(3U, pt_loads.size());

  LoadInfo load_data = pt_loads.at(0);
  ASSERT_EQ(0U, load_data.offset);
  ASSERT_EQ(0x2000U, load_data.table_offset);
  ASSERT_EQ(0x10000U, load_data.table_size);

  load_data = pt_loads.at(0x1000);
  ASSERT_EQ(0x1000U, load_data.offset);
  ASSERT_EQ(0x2001U, load_data.table_offset);
  ASSERT_EQ(0x10001U, load_data.table_size);

  load_data = pt_loads.at(0x2000);
  ASSERT_EQ(0x2000U, load_data.offset);
  ASSERT_EQ(0x2002U, load_data.table_offset);
  ASSERT_EQ(0x10002U, load_data.table_size);
}

TEST_F(ElfInterfaceTest, multiple_executable_pt_loads_32) {
  MultipleExecutablePtLoads<Elf32_Ehdr, Elf32_Phdr, Elf32_Dyn, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, multiple_executable_pt_loads_64) {
  MultipleExecutablePtLoads<Elf64_Ehdr, Elf64_Phdr, Elf64_Dyn, ElfInterface64>();
}

template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
void ElfInterfaceTest::MultipleExecutablePtLoadsIncrementsNotSizeOfPhdr() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 3;
  ehdr.e_phentsize = sizeof(Phdr) + 100;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x1000;
  phdr.p_vaddr = 0x2001;
  phdr.p_memsz = 0x10001;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1001;
  fake_memory_->SetMemory(0x100 + sizeof(phdr) + 100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x2000;
  phdr.p_vaddr = 0x2002;
  phdr.p_memsz = 0x10002;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1002;
  fake_memory_->SetMemory(0x100 + 2 * (sizeof(phdr) + 100), &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x2000, load_bias);

  const std::unordered_map<uint64_t, LoadInfo>& pt_loads = elf->pt_loads();
  ASSERT_EQ(3U, pt_loads.size());

  LoadInfo load_data = pt_loads.at(0);
  ASSERT_EQ(0U, load_data.offset);
  ASSERT_EQ(0x2000U, load_data.table_offset);
  ASSERT_EQ(0x10000U, load_data.table_size);

  load_data = pt_loads.at(0x1000);
  ASSERT_EQ(0x1000U, load_data.offset);
  ASSERT_EQ(0x2001U, load_data.table_offset);
  ASSERT_EQ(0x10001U, load_data.table_size);

  load_data = pt_loads.at(0x2000);
  ASSERT_EQ(0x2000U, load_data.offset);
  ASSERT_EQ(0x2002U, load_data.table_offset);
  ASSERT_EQ(0x10002U, load_data.table_size);
}

TEST_F(ElfInterfaceTest, multiple_executable_pt_loads_increments_not_size_of_phdr_32) {
  MultipleExecutablePtLoadsIncrementsNotSizeOfPhdr<Elf32_Ehdr, Elf32_Phdr, Elf32_Dyn,
                                                   ElfInterface32>();
}

TEST_F(ElfInterfaceTest, multiple_executable_pt_loads_increments_not_size_of_phdr_64) {
  MultipleExecutablePtLoadsIncrementsNotSizeOfPhdr<Elf64_Ehdr, Elf64_Phdr, Elf64_Dyn,
                                                   ElfInterface64>();
}

template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
void ElfInterfaceTest::NonExecutablePtLoads() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 3;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x1000;
  phdr.p_vaddr = 0x2001;
  phdr.p_memsz = 0x10001;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1001;
  fake_memory_->SetMemory(0x100 + sizeof(phdr), &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x2000;
  phdr.p_vaddr = 0x2002;
  phdr.p_memsz = 0x10002;
  phdr.p_flags = PF_R;
  phdr.p_align = 0x1002;
  fake_memory_->SetMemory(0x100 + 2 * sizeof(phdr), &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x1001, load_bias);

  const std::unordered_map<uint64_t, LoadInfo>& pt_loads = elf->pt_loads();
  ASSERT_EQ(1U, pt_loads.size());

  LoadInfo load_data = pt_loads.at(0x1000);
  ASSERT_EQ(0x1000U, load_data.offset);
  ASSERT_EQ(0x2001U, load_data.table_offset);
  ASSERT_EQ(0x10001U, load_data.table_size);
}

TEST_F(ElfInterfaceTest, non_executable_pt_loads_32) {
  NonExecutablePtLoads<Elf32_Ehdr, Elf32_Phdr, Elf32_Dyn, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, non_executable_pt_loads_64) {
  NonExecutablePtLoads<Elf64_Ehdr, Elf64_Phdr, Elf64_Dyn, ElfInterface64>();
}

template <typename Ehdr, typename Phdr, typename Dyn, typename ElfInterfaceType>
void ElfInterfaceTest::ManyPhdrs() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 7;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  uint64_t phdr_offset = 0x100;

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_GNU_EH_FRAME;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_DYNAMIC;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_INTERP;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_NOTE;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_SHLIB;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_GNU_EH_FRAME;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x2000, load_bias);

  const std::unordered_map<uint64_t, LoadInfo>& pt_loads = elf->pt_loads();
  ASSERT_EQ(1U, pt_loads.size());

  LoadInfo load_data = pt_loads.at(0);
  ASSERT_EQ(0U, load_data.offset);
  ASSERT_EQ(0x2000U, load_data.table_offset);
  ASSERT_EQ(0x10000U, load_data.table_size);
}

TEST_F(ElfInterfaceTest, many_phdrs_32) {
  ElfInterfaceTest::ManyPhdrs<Elf32_Ehdr, Elf32_Phdr, Elf32_Dyn, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, many_phdrs_64) {
  ElfInterfaceTest::ManyPhdrs<Elf64_Ehdr, Elf64_Phdr, Elf64_Dyn, ElfInterface64>();
}

TEST_F(ElfInterfaceTest, arm32) {
  ElfInterfaceArm elf_arm(memory_);

  Elf32_Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 1;
  ehdr.e_phentsize = sizeof(Elf32_Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Elf32_Phdr phdr = {};
  phdr.p_type = PT_ARM_EXIDX;
  phdr.p_offset = 0x2000;
  phdr.p_filesz = 16;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  // Add arm exidx entries.
  fake_memory_->SetData32(0x2000, 0x1000);
  fake_memory_->SetData32(0x2008, 0x1000);

  int64_t load_bias = 0;
  ASSERT_TRUE(elf_arm.Init(&load_bias));
  EXPECT_EQ(0, load_bias);

  std::vector<uint32_t> entries;
  for (auto addr : elf_arm) {
    entries.push_back(addr);
  }
  ASSERT_EQ(2U, entries.size());
  ASSERT_EQ(0x3000U, entries[0]);
  ASSERT_EQ(0x3008U, entries[1]);

  ASSERT_EQ(0x2000U, elf_arm.start_offset());
  ASSERT_EQ(2U, elf_arm.total_entries());
}

template <typename Ehdr, typename Phdr, typename Shdr, typename Dyn>
void ElfInterfaceTest::SonameInit(SonameTestEnum test_type) {
  Ehdr ehdr = {};
  ehdr.e_shoff = 0x200;
  ehdr.e_shnum = 2;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 1;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Shdr shdr = {};
  shdr.sh_type = SHT_STRTAB;
  if (test_type == SONAME_MISSING_MAP) {
    shdr.sh_addr = 0x20100;
  } else {
    shdr.sh_addr = 0x10100;
  }
  shdr.sh_offset = 0x10000;
  fake_memory_->SetMemory(0x200 + sizeof(shdr), &shdr, sizeof(shdr));

  Phdr phdr = {};
  phdr.p_type = PT_DYNAMIC;
  phdr.p_offset = 0x2000;
  phdr.p_memsz = sizeof(Dyn) * 3;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  uint64_t offset = 0x2000;
  Dyn dyn;

  dyn.d_tag = DT_STRTAB;
  dyn.d_un.d_ptr = 0x10100;
  fake_memory_->SetMemory(offset, &dyn, sizeof(dyn));
  offset += sizeof(dyn);

  dyn.d_tag = DT_STRSZ;
  if (test_type == SONAME_DTSIZE_SMALL) {
    dyn.d_un.d_val = 0x10;
  } else {
    dyn.d_un.d_val = 0x1000;
  }
  fake_memory_->SetMemory(offset, &dyn, sizeof(dyn));
  offset += sizeof(dyn);

  if (test_type == SONAME_DTNULL_AFTER) {
    dyn.d_tag = DT_NULL;
    fake_memory_->SetMemory(offset, &dyn, sizeof(dyn));
    offset += sizeof(dyn);
  }

  dyn.d_tag = DT_SONAME;
  dyn.d_un.d_val = 0x10;
  fake_memory_->SetMemory(offset, &dyn, sizeof(dyn));
  offset += sizeof(dyn);

  dyn.d_tag = DT_NULL;
  fake_memory_->SetMemory(offset, &dyn, sizeof(dyn));

  SetStringMemory(0x10010, "fake_soname.so");
}

template <typename ElfInterfaceType>
void ElfInterfaceTest::Soname() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);

  ASSERT_EQ("fake_soname.so", elf->GetSoname());
}

TEST_F(ElfInterfaceTest, soname_32) {
  SonameInit<Elf32_Ehdr, Elf32_Phdr, Elf32_Shdr, Elf32_Dyn>();
  Soname<ElfInterface32>();
}

TEST_F(ElfInterfaceTest, soname_64) {
  SonameInit<Elf64_Ehdr, Elf64_Phdr, Elf64_Shdr, Elf64_Dyn>();
  Soname<ElfInterface64>();
}

template <typename ElfInterfaceType>
void ElfInterfaceTest::SonameAfterDtNull() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);

  ASSERT_EQ("", elf->GetSoname());
}

TEST_F(ElfInterfaceTest, soname_after_dt_null_32) {
  SonameInit<Elf32_Ehdr, Elf32_Phdr, Elf32_Shdr, Elf32_Dyn>(SONAME_DTNULL_AFTER);
  SonameAfterDtNull<ElfInterface32>();
}

TEST_F(ElfInterfaceTest, soname_after_dt_null_64) {
  SonameInit<Elf64_Ehdr, Elf64_Phdr, Elf64_Shdr, Elf64_Dyn>(SONAME_DTNULL_AFTER);
  SonameAfterDtNull<ElfInterface64>();
}

template <typename ElfInterfaceType>
void ElfInterfaceTest::SonameSize() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);

  ASSERT_EQ("", elf->GetSoname());
}

TEST_F(ElfInterfaceTest, soname_size_32) {
  SonameInit<Elf32_Ehdr, Elf32_Phdr, Elf32_Shdr, Elf32_Dyn>(SONAME_DTSIZE_SMALL);
  SonameSize<ElfInterface32>();
}

TEST_F(ElfInterfaceTest, soname_size_64) {
  SonameInit<Elf64_Ehdr, Elf64_Phdr, Elf64_Shdr, Elf64_Dyn>(SONAME_DTSIZE_SMALL);
  SonameSize<ElfInterface64>();
}

// Verify that there is no map from STRTAB in the dynamic section to a
// STRTAB entry in the section headers.
template <typename ElfInterfaceType>
void ElfInterfaceTest::SonameMissingMap() {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);

  ASSERT_EQ("", elf->GetSoname());
}

TEST_F(ElfInterfaceTest, soname_missing_map_32) {
  SonameInit<Elf32_Ehdr, Elf32_Phdr, Elf32_Shdr, Elf32_Dyn>(SONAME_MISSING_MAP);
  SonameMissingMap<ElfInterface32>();
}

TEST_F(ElfInterfaceTest, soname_missing_map_64) {
  SonameInit<Elf64_Ehdr, Elf64_Phdr, Elf64_Shdr, Elf64_Dyn>(SONAME_MISSING_MAP);
  SonameMissingMap<ElfInterface64>();
}

template <typename ElfType>
void ElfInterfaceTest::InitHeadersEhFrameTest() {
  ElfType elf(memory_);

  elf.FakeSetEhFrameInfo(SectionInfo{.offset = 0x10000});
  elf.FakeSetDebugFrameInfo(SectionInfo{});

  fake_memory_->SetMemory(
      0x10000, std::vector<uint8_t>{0x1, DW_EH_PE_udata2, DW_EH_PE_udata2, DW_EH_PE_udata2});
  fake_memory_->SetData32(0x10004, 0x500);
  fake_memory_->SetData32(0x10008, 250);

  elf.InitHeaders();

  EXPECT_FALSE(elf.eh_frame() == nullptr);
  EXPECT_TRUE(elf.debug_frame() == nullptr);
}

TEST_F(ElfInterfaceTest, init_headers_eh_frame_32) {
  InitHeadersEhFrameTest<ElfInterface32Fake>();
}

TEST_F(ElfInterfaceTest, init_headers_eh_frame_64) {
  InitHeadersEhFrameTest<ElfInterface64Fake>();
}

template <typename ElfType>
void ElfInterfaceTest::InitHeadersDebugFrame() {
  ElfType elf(memory_);

  elf.FakeSetEhFrameInfo(SectionInfo{});
  elf.FakeSetDebugFrameInfo(SectionInfo{.offset = 0x5000, .size = 0x200});

  fake_memory_->SetData32(0x5000, 0xfc);
  fake_memory_->SetData32(0x5004, 0xffffffff);
  fake_memory_->SetMemory(0x5008, std::vector<uint8_t>{1, '\0', 4, 8, 2});

  fake_memory_->SetData32(0x5100, 0xfc);
  fake_memory_->SetData32(0x5104, 0);
  fake_memory_->SetData32(0x5108, 0x1500);
  fake_memory_->SetData32(0x510c, 0x200);

  elf.InitHeaders();

  EXPECT_TRUE(elf.eh_frame() == nullptr);
  EXPECT_FALSE(elf.debug_frame() == nullptr);
}

TEST_F(ElfInterfaceTest, init_headers_debug_frame_32) {
  InitHeadersDebugFrame<ElfInterface32Fake>();
}

TEST_F(ElfInterfaceTest, init_headers_debug_frame_64) {
  InitHeadersDebugFrame<ElfInterface64Fake>();
}

template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitProgramHeadersMalformed() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 3;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
}

TEST_F(ElfInterfaceTest, init_program_headers_malformed_32) {
  InitProgramHeadersMalformed<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, init_program_headers_malformed_64) {
  InitProgramHeadersMalformed<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersMalformed() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_shoff = 0x1000;
  ehdr.e_shnum = 10;
  ehdr.e_shentsize = sizeof(Shdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
}

TEST_F(ElfInterfaceTest, init_section_headers_malformed_32) {
  InitSectionHeadersMalformed<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, init_section_headers_malformed_64) {
  InitSectionHeadersMalformed<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersMalformedSymData() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x1000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 5;
  ehdr.e_shentsize = sizeof(Shdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_SYMTAB;
  shdr.sh_link = 4;
  shdr.sh_addr = 0x5000;
  shdr.sh_offset = 0x5000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = shdr.sh_entsize * 10;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_DYNSYM;
  shdr.sh_link = 10;
  shdr.sh_addr = 0x6000;
  shdr.sh_offset = 0x6000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = shdr.sh_entsize * 10;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_DYNSYM;
  shdr.sh_link = 2;
  shdr.sh_addr = 0x6000;
  shdr.sh_offset = 0x6000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = shdr.sh_entsize * 10;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for the entries.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(0U, elf->debug_frame_info().offset);
  EXPECT_EQ(0U, elf->debug_frame_info().size);
  EXPECT_EQ(0U, elf->gnu_debugdata_offset());
  EXPECT_EQ(0U, elf->gnu_debugdata_size());

  SharedString name;
  uint64_t name_offset;
  ASSERT_FALSE(elf->GetFunctionName(0x90010, &name, &name_offset));
}

TEST_F(ElfInterfaceTest, init_section_headers_malformed_symdata_32) {
  InitSectionHeadersMalformedSymData<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, init_section_headers_malformed_symdata_64) {
  InitSectionHeadersMalformedSymData<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename Sym, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeaders(uint64_t entry_size) {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x1000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 5;
  ehdr.e_shentsize = entry_size;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_SYMTAB;
  shdr.sh_link = 4;
  shdr.sh_addr = 0x5000;
  shdr.sh_offset = 0x5000;
  shdr.sh_entsize = sizeof(Sym);
  shdr.sh_size = shdr.sh_entsize * 10;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_DYNSYM;
  shdr.sh_link = 4;
  shdr.sh_addr = 0x6000;
  shdr.sh_offset = 0x6000;
  shdr.sh_entsize = sizeof(Sym);
  shdr.sh_size = shdr.sh_entsize * 10;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_name = 0xa000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for the entries.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  InitSym<Sym>(0x5000, 0x90000, 0x1000, 0x100, 0xf000, "function_one");
  InitSym<Sym>(0x6000, 0xd0000, 0x1000, 0x300, 0xf000, "function_two");

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(0U, elf->debug_frame_info().offset);
  EXPECT_EQ(0U, elf->debug_frame_info().size);
  EXPECT_EQ(0U, elf->gnu_debugdata_offset());
  EXPECT_EQ(0U, elf->gnu_debugdata_size());

  // Look in the first symbol table.
  SharedString name;
  uint64_t name_offset;
  ASSERT_TRUE(elf->GetFunctionName(0x90010, &name, &name_offset));
  EXPECT_EQ("function_one", name);
  EXPECT_EQ(16U, name_offset);
  ASSERT_TRUE(elf->GetFunctionName(0xd0020, &name, &name_offset));
  EXPECT_EQ("function_two", name);
  EXPECT_EQ(32U, name_offset);
}

TEST_F(ElfInterfaceTest, init_section_headers_32) {
  InitSectionHeaders<Elf32_Ehdr, Elf32_Shdr, Elf32_Sym, ElfInterface32>(sizeof(Elf32_Shdr));
}

TEST_F(ElfInterfaceTest, init_section_headers_64) {
  InitSectionHeaders<Elf64_Ehdr, Elf64_Shdr, Elf64_Sym, ElfInterface64>(sizeof(Elf64_Shdr));
}

TEST_F(ElfInterfaceTest, init_section_headers_non_std_entry_size_32) {
  InitSectionHeaders<Elf32_Ehdr, Elf32_Shdr, Elf32_Sym, ElfInterface32>(0x100);
}

TEST_F(ElfInterfaceTest, init_section_headers_non_std_entry_size_64) {
  InitSectionHeaders<Elf64_Ehdr, Elf64_Shdr, Elf64_Sym, ElfInterface64>(0x100);
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersOffsets() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 7;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x200;
  shdr.sh_addr = 0x5000;
  shdr.sh_offset = 0x5000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x800;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x100;
  shdr.sh_addr = 0x6000;
  shdr.sh_offset = 0x6000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x500;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x300;
  shdr.sh_addr = 0x7000;
  shdr.sh_offset = 0x7000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x800;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x400;
  shdr.sh_addr = 0xa000;
  shdr.sh_offset = 0xa000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0xf00;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_addr = 0xb000;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = 0xf00;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf100, ".debug_frame", sizeof(".debug_frame"));
  fake_memory_->SetMemory(0xf200, ".gnu_debugdata", sizeof(".gnu_debugdata"));
  fake_memory_->SetMemory(0xf300, ".eh_frame", sizeof(".eh_frame"));
  fake_memory_->SetMemory(0xf400, ".eh_frame_hdr", sizeof(".eh_frame_hdr"));
  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(0x6000U, elf->debug_frame_info().offset);
  EXPECT_EQ(0, elf->debug_frame_info().bias);
  EXPECT_EQ(0x500U, elf->debug_frame_info().size);
  EXPECT_EQ(0U, elf->debug_frame_info().flags);

  EXPECT_EQ(0x5000U, elf->gnu_debugdata_offset());
  EXPECT_EQ(0x800U, elf->gnu_debugdata_size());

  EXPECT_EQ(0x7000U, elf->eh_frame_info().offset);
  EXPECT_EQ(0, elf->eh_frame_info().bias);
  EXPECT_EQ(0x800U, elf->eh_frame_info().size);
  EXPECT_EQ(0U, elf->eh_frame_info().flags);

  EXPECT_EQ(0xa000U, elf->eh_frame_hdr_info().offset);
  EXPECT_EQ(0, elf->eh_frame_hdr_info().bias);
  EXPECT_EQ(0xf00U, elf->eh_frame_hdr_info().size);
  EXPECT_EQ(0U, elf->eh_frame_hdr_info().flags);

  EXPECT_EQ(0xb000U, elf->gnu_build_id_offset());
  EXPECT_EQ(0xf00U, elf->gnu_build_id_size());
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_32) {
  InitSectionHeadersOffsets<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_64) {
  InitSectionHeadersOffsets<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersOffsetsEhFrameSectionBias(uint64_t addr, uint64_t offset,
                                                                   int64_t expected_bias) {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t elf_offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = elf_offset;
  ehdr.e_shnum = 4;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  elf_offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x200;
  shdr.sh_addr = 0x8000;
  shdr.sh_offset = 0x8000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x800;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));
  elf_offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));
  elf_offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x100;
  shdr.sh_addr = addr;
  shdr.sh_offset = offset;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x500;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf100, ".eh_frame", sizeof(".eh_frame"));
  fake_memory_->SetMemory(0xf200, ".eh_frame_hdr", sizeof(".eh_frame_hdr"));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(offset, elf->eh_frame_info().offset);
  EXPECT_EQ(expected_bias, elf->eh_frame_info().bias);
  EXPECT_EQ(0x500U, elf->eh_frame_info().size);
  EXPECT_EQ(0U, elf->eh_frame_info().flags);

  EXPECT_EQ(0x8000U, elf->eh_frame_hdr_info().offset);
  EXPECT_EQ(0, elf->eh_frame_hdr_info().bias);
  EXPECT_EQ(0x800U, elf->eh_frame_hdr_info().size);
  EXPECT_EQ(0U, elf->eh_frame_hdr_info().flags);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_zero_32) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(0x4000,
                                                                                      0x4000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_zero_64) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(0x6000,
                                                                                      0x6000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_positive_32) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x5000, 0x4000, 0x1000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_positive_64) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x6000, 0x4000, 0x2000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_negative_32) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x3000, 0x4000, -0x1000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_section_bias_negative_64) {
  InitSectionHeadersOffsetsEhFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x6000, 0x9000, -0x3000);
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersOffsetsEhFrameHdrSectionBias(uint64_t addr,
                                                                      uint64_t offset,
                                                                      int64_t expected_bias) {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t elf_offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = elf_offset;
  ehdr.e_shnum = 4;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  elf_offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x200;
  shdr.sh_addr = addr;
  shdr.sh_offset = offset;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x800;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));
  elf_offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));
  elf_offset += ehdr.e_shentsize;

  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x100;
  shdr.sh_addr = 0x5000;
  shdr.sh_offset = 0x5000;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x500;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf100, ".eh_frame", sizeof(".eh_frame"));
  fake_memory_->SetMemory(0xf200, ".eh_frame_hdr", sizeof(".eh_frame_hdr"));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(0x5000U, elf->eh_frame_info().offset);
  EXPECT_EQ(0, elf->eh_frame_info().bias);
  EXPECT_EQ(0x500U, elf->eh_frame_info().size);
  EXPECT_EQ(0U, elf->eh_frame_info().flags);
  EXPECT_EQ(offset, elf->eh_frame_hdr_info().offset);
  EXPECT_EQ(expected_bias, elf->eh_frame_hdr_info().bias);
  EXPECT_EQ(0x800U, elf->eh_frame_hdr_info().size);
  EXPECT_EQ(0U, elf->eh_frame_hdr_info().flags);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_zero_32) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(0x9000,
                                                                                         0x9000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_zero_64) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(0xa000,
                                                                                         0xa000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_positive_32) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x9000, 0x4000, 0x5000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_positive_64) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x6000, 0x1000, 0x5000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_negative_32) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x3000, 0x5000, -0x2000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_eh_frame_hdr_section_bias_negative_64) {
  InitSectionHeadersOffsetsEhFrameHdrSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x5000, 0x9000, -0x4000);
}

template <typename Ehdr, typename Shdr, typename ElfInterfaceType>
void ElfInterfaceTest::InitSectionHeadersOffsetsDebugFrameSectionBias(uint64_t addr,
                                                                      uint64_t offset,
                                                                      int64_t expected_bias) {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t elf_offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = elf_offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  elf_offset += ehdr.e_shentsize;

  Shdr shdr = {};
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_link = 2;
  shdr.sh_name = 0x100;
  shdr.sh_addr = addr;
  shdr.sh_offset = offset;
  shdr.sh_entsize = 0x100;
  shdr.sh_size = 0x800;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));
  elf_offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(elf_offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf100, ".debug_frame", sizeof(".debug_frame"));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(offset, elf->debug_frame_info().offset);
  EXPECT_EQ(expected_bias, elf->debug_frame_info().bias);
  EXPECT_EQ(0x800U, elf->debug_frame_info().size);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_zero_32) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(0x5000,
                                                                                         0x5000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_zero_64) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(0xa000,
                                                                                         0xa000, 0);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_positive_32) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x5000, 0x2000, 0x3000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_positive_64) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x7000, 0x1000, 0x6000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_negative_32) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf32_Ehdr, Elf32_Shdr, ElfInterface32>(
      0x6000, 0x7000, -0x1000);
}

TEST_F(ElfInterfaceTest, init_section_headers_offsets_debug_frame_section_bias_negative_64) {
  InitSectionHeadersOffsetsDebugFrameSectionBias<Elf64_Ehdr, Elf64_Shdr, ElfInterface64>(
      0x3000, 0x5000, -0x2000);
}

template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
void ElfInterfaceTest::CheckGnuEhFrame(uint64_t addr, uint64_t offset, int64_t expected_bias) {
  std::unique_ptr<ElfInterface> elf(new ElfInterfaceType(memory_));

  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 2;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  uint64_t phdr_offset = 0x100;

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));
  phdr_offset += sizeof(phdr);

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_GNU_EH_FRAME;
  phdr.p_vaddr = addr;
  phdr.p_offset = offset;
  fake_memory_->SetMemory(phdr_offset, &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_EQ(expected_bias, elf->eh_frame_hdr_info().bias);
}

TEST_F(ElfInterfaceTest, eh_frame_zero_section_bias_32) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x4000, 0x4000, 0);
}

TEST_F(ElfInterfaceTest, eh_frame_zero_section_bias_64) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x4000, 0x4000, 0);
}

TEST_F(ElfInterfaceTest, eh_frame_positive_section_bias_32) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x4000, 0x1000, 0x3000);
}

TEST_F(ElfInterfaceTest, eh_frame_positive_section_bias_64) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x4000, 0x1000, 0x3000);
}

TEST_F(ElfInterfaceTest, eh_frame_negative_section_bias_32) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x4000, 0x5000,
                                                                            -0x1000);
}

TEST_F(ElfInterfaceTest, eh_frame_negative_section_bias_64) {
  ElfInterfaceTest::CheckGnuEhFrame<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x4000, 0x5000,
                                                                            -0x1000);
}

TEST_F(ElfInterfaceTest, is_valid_pc_from_pt_load) {
  std::unique_ptr<ElfInterface> elf(new ElfInterface32(memory_));

  Elf32_Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 1;
  ehdr.e_phentsize = sizeof(Elf32_Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Elf32_Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0, load_bias);
  EXPECT_TRUE(elf->IsValidPc(0));
  EXPECT_TRUE(elf->IsValidPc(0x5000));
  EXPECT_TRUE(elf->IsValidPc(0xffff));
  EXPECT_FALSE(elf->IsValidPc(0x10000));
}

TEST_F(ElfInterfaceTest, is_valid_pc_from_pt_load_non_zero_load_bias) {
  std::unique_ptr<ElfInterface> elf(new ElfInterface32(memory_));

  Elf32_Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 1;
  ehdr.e_phentsize = sizeof(Elf32_Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Elf32_Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  EXPECT_EQ(0x2000, load_bias);
  EXPECT_FALSE(elf->IsValidPc(0));
  EXPECT_FALSE(elf->IsValidPc(0x1000));
  EXPECT_FALSE(elf->IsValidPc(0x1fff));
  EXPECT_TRUE(elf->IsValidPc(0x2000));
  EXPECT_TRUE(elf->IsValidPc(0x5000));
  EXPECT_TRUE(elf->IsValidPc(0x11fff));
  EXPECT_FALSE(elf->IsValidPc(0x12000));
}

TEST_F(ElfInterfaceTest, is_valid_pc_from_debug_frame) {
  std::unique_ptr<ElfInterface> elf(new ElfInterface32(memory_));

  uint64_t sh_offset = 0x100;

  Elf32_Ehdr ehdr = {};
  ehdr.e_shstrndx = 1;
  ehdr.e_shoff = sh_offset;
  ehdr.e_shentsize = sizeof(Elf32_Shdr);
  ehdr.e_shnum = 3;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Elf32_Shdr shdr = {};
  shdr.sh_type = SHT_NULL;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));

  sh_offset += sizeof(shdr);
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 1;
  shdr.sh_offset = 0x500;
  shdr.sh_size = 0x100;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));
  fake_memory_->SetMemory(0x500, ".debug_frame");

  sh_offset += sizeof(shdr);
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_name = 0;
  shdr.sh_addr = 0x600;
  shdr.sh_offset = 0x600;
  shdr.sh_size = 0x200;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));

  // CIE 32.
  fake_memory_->SetData32(0x600, 0xfc);
  fake_memory_->SetData32(0x604, 0xffffffff);
  fake_memory_->SetMemory(0x608, std::vector<uint8_t>{1, '\0', 4, 4, 1});

  // FDE 32.
  fake_memory_->SetData32(0x700, 0xfc);
  fake_memory_->SetData32(0x704, 0);
  fake_memory_->SetData32(0x708, 0x2100);
  fake_memory_->SetData32(0x70c, 0x200);

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  elf->InitHeaders();
  EXPECT_EQ(0, load_bias);
  EXPECT_FALSE(elf->IsValidPc(0));
  EXPECT_FALSE(elf->IsValidPc(0x20ff));
  EXPECT_TRUE(elf->IsValidPc(0x2100));
  EXPECT_TRUE(elf->IsValidPc(0x2200));
  EXPECT_TRUE(elf->IsValidPc(0x22ff));
  EXPECT_FALSE(elf->IsValidPc(0x2300));
}

TEST_F(ElfInterfaceTest, is_valid_pc_from_eh_frame) {
  std::unique_ptr<ElfInterface> elf(new ElfInterface32(memory_));

  uint64_t sh_offset = 0x100;

  Elf32_Ehdr ehdr = {};
  ehdr.e_shstrndx = 1;
  ehdr.e_shoff = sh_offset;
  ehdr.e_shentsize = sizeof(Elf32_Shdr);
  ehdr.e_shnum = 3;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Elf32_Shdr shdr = {};
  shdr.sh_type = SHT_NULL;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));

  sh_offset += sizeof(shdr);
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 1;
  shdr.sh_offset = 0x500;
  shdr.sh_size = 0x100;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));
  fake_memory_->SetMemory(0x500, ".eh_frame");

  sh_offset += sizeof(shdr);
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_PROGBITS;
  shdr.sh_name = 0;
  shdr.sh_addr = 0x600;
  shdr.sh_offset = 0x600;
  shdr.sh_size = 0x200;
  fake_memory_->SetMemory(sh_offset, &shdr, sizeof(shdr));

  // CIE 32.
  fake_memory_->SetData32(0x600, 0xfc);
  fake_memory_->SetData32(0x604, 0);
  fake_memory_->SetMemory(0x608, std::vector<uint8_t>{1, '\0', 4, 4, 1});

  // FDE 32.
  fake_memory_->SetData32(0x700, 0xfc);
  fake_memory_->SetData32(0x704, 0x104);
  fake_memory_->SetData32(0x708, 0x20f8);
  fake_memory_->SetData32(0x70c, 0x200);

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  elf->InitHeaders();
  EXPECT_EQ(0, load_bias);
  EXPECT_FALSE(elf->IsValidPc(0));
  EXPECT_FALSE(elf->IsValidPc(0x27ff));
  EXPECT_TRUE(elf->IsValidPc(0x2800));
  EXPECT_TRUE(elf->IsValidPc(0x2900));
  EXPECT_TRUE(elf->IsValidPc(0x29ff));
  EXPECT_FALSE(elf->IsValidPc(0x2a00));
}

template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
void ElfInterfaceTest::BuildID() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  char note_section[128];
  Nhdr note_header = {};
  note_header.n_namesz = 4;  // "GNU"
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section, &note_header, sizeof(note_header));
  size_t note_offset = sizeof(note_header);
  // The note information contains the GNU and trailing '\0'.
  memcpy(&note_section[note_offset], "GNU", sizeof("GNU"));
  note_offset += sizeof("GNU");
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);

  Shdr shdr = {};
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = sizeof(note_section);
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));
  fake_memory_->SetMemory(0xb000, note_section, sizeof(note_section));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  ASSERT_EQ("BUILDID", elf->GetBuildID());
}

TEST_F(ElfInterfaceTest, build_id_32) {
  BuildID<Elf32_Ehdr, Elf32_Shdr, Elf32_Nhdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, build_id_64) {
  BuildID<Elf64_Ehdr, Elf64_Shdr, Elf64_Nhdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
void ElfInterfaceTest::BuildIDTwoNotes() {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  char note_section[128];
  Nhdr note_header = {};
  note_header.n_namesz = 8;  // "WRONG" aligned to 4
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section, &note_header, sizeof(note_header));
  size_t note_offset = sizeof(note_header);
  memcpy(&note_section[note_offset], "WRONG", sizeof("WRONG"));
  note_offset += 8;
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);
  note_offset += 8;

  note_header.n_namesz = 4;  // "GNU"
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section[note_offset], &note_header, sizeof(note_header));
  note_offset += sizeof(note_header);
  // The note information contains the GNU and trailing '\0'.
  memcpy(&note_section[note_offset], "GNU", sizeof("GNU"));
  note_offset += sizeof("GNU");
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);

  Shdr shdr = {};
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = sizeof(note_section);
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));
  fake_memory_->SetMemory(0xb000, note_section, sizeof(note_section));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  ASSERT_EQ("BUILDID", elf->GetBuildID());
}

TEST_F(ElfInterfaceTest, build_id_two_notes_32) {
  BuildIDTwoNotes<Elf32_Ehdr, Elf32_Shdr, Elf32_Nhdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, build_id_two_notes_64) {
  BuildIDTwoNotes<Elf64_Ehdr, Elf64_Shdr, Elf64_Nhdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
void ElfInterfaceTest::BuildIDSectionTooSmallForName () {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  char note_section[128];
  Nhdr note_header = {};
  note_header.n_namesz = 4;  // "GNU"
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section, &note_header, sizeof(note_header));
  size_t note_offset = sizeof(note_header);
  // The note information contains the GNU and trailing '\0'.
  memcpy(&note_section[note_offset], "GNU", sizeof("GNU"));
  note_offset += sizeof("GNU");
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);

  Shdr shdr = {};
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = sizeof(note_header) + 1;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));
  fake_memory_->SetMemory(0xb000, note_section, sizeof(note_section));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  ASSERT_EQ("", elf->GetBuildID());
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_name_32) {
  BuildIDSectionTooSmallForName<Elf32_Ehdr, Elf32_Shdr, Elf32_Nhdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_name_64) {
  BuildIDSectionTooSmallForName<Elf64_Ehdr, Elf64_Shdr, Elf64_Nhdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
void ElfInterfaceTest::BuildIDSectionTooSmallForDesc () {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  char note_section[128];
  Nhdr note_header = {};
  note_header.n_namesz = 4;  // "GNU"
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section, &note_header, sizeof(note_header));
  size_t note_offset = sizeof(note_header);
  // The note information contains the GNU and trailing '\0'.
  memcpy(&note_section[note_offset], "GNU", sizeof("GNU"));
  note_offset += sizeof("GNU");
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);

  Shdr shdr = {};
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = sizeof(note_header) + sizeof("GNU") + 1;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));
  fake_memory_->SetMemory(0xb000, note_section, sizeof(note_section));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  ASSERT_EQ("", elf->GetBuildID());
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_desc_32) {
  BuildIDSectionTooSmallForDesc<Elf32_Ehdr, Elf32_Shdr, Elf32_Nhdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_desc_64) {
  BuildIDSectionTooSmallForDesc<Elf64_Ehdr, Elf64_Shdr, Elf64_Nhdr, ElfInterface64>();
}

template <typename Ehdr, typename Shdr, typename Nhdr, typename ElfInterfaceType>
void ElfInterfaceTest::BuildIDSectionTooSmallForHeader () {
  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));

  uint64_t offset = 0x2000;

  Ehdr ehdr = {};
  ehdr.e_shoff = offset;
  ehdr.e_shnum = 3;
  ehdr.e_shentsize = sizeof(Shdr);
  ehdr.e_shstrndx = 2;
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  offset += ehdr.e_shentsize;

  char note_section[128];
  Nhdr note_header = {};
  note_header.n_namesz = 4;  // "GNU"
  note_header.n_descsz = 7;  // "BUILDID"
  note_header.n_type = NT_GNU_BUILD_ID;
  memcpy(&note_section, &note_header, sizeof(note_header));
  size_t note_offset = sizeof(note_header);
  // The note information contains the GNU and trailing '\0'.
  memcpy(&note_section[note_offset], "GNU", sizeof("GNU"));
  note_offset += sizeof("GNU");
  // This part of the note does not contain any trailing '\0'.
  memcpy(&note_section[note_offset], "BUILDID", 7);

  Shdr shdr = {};
  shdr.sh_type = SHT_NOTE;
  shdr.sh_name = 0x500;
  shdr.sh_offset = 0xb000;
  shdr.sh_size = sizeof(note_header) - 1;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));
  offset += ehdr.e_shentsize;

  // The string data for section header names.
  memset(&shdr, 0, sizeof(shdr));
  shdr.sh_type = SHT_STRTAB;
  shdr.sh_name = 0x20000;
  shdr.sh_offset = 0xf000;
  shdr.sh_size = 0x1000;
  fake_memory_->SetMemory(offset, &shdr, sizeof(shdr));

  fake_memory_->SetMemory(0xf500, ".note.gnu.build-id", sizeof(".note.gnu.build-id"));
  fake_memory_->SetMemory(0xb000, note_section, sizeof(note_section));

  int64_t load_bias = 0;
  ASSERT_TRUE(elf->Init(&load_bias));
  ASSERT_EQ("", elf->GetBuildID());
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_header_32) {
  BuildIDSectionTooSmallForHeader<Elf32_Ehdr, Elf32_Shdr, Elf32_Nhdr, ElfInterface32>();
}

TEST_F(ElfInterfaceTest, build_id_section_too_small_for_header_64) {
  BuildIDSectionTooSmallForHeader<Elf64_Ehdr, Elf64_Shdr, Elf64_Nhdr, ElfInterface64>();
}

template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
void ElfInterfaceTest::CheckLoadBiasInFirstPhdr(int64_t load_bias) {
  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 2;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0;
  phdr.p_vaddr = load_bias;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x1000;
  phdr.p_memsz = 0x2000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100 + sizeof(phdr), &phdr, sizeof(phdr));

  int64_t static_load_bias = ElfInterface::GetLoadBias<Ehdr, Phdr>(memory_.get());
  ASSERT_EQ(load_bias, static_load_bias);

  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));
  int64_t init_load_bias = 0;
  ASSERT_TRUE(elf->Init(&init_load_bias));
  ASSERT_EQ(init_load_bias, static_load_bias);
}

TEST_F(ElfInterfaceTest, get_load_bias_zero_32) {
  CheckLoadBiasInFirstPhdr<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0);
}

TEST_F(ElfInterfaceTest, get_load_bias_zero_64) {
  CheckLoadBiasInFirstPhdr<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0);
}

TEST_F(ElfInterfaceTest, get_load_bias_non_zero_32) {
  CheckLoadBiasInFirstPhdr<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x1000);
}

TEST_F(ElfInterfaceTest, get_load_bias_non_zero_64) {
  CheckLoadBiasInFirstPhdr<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x1000);
}

template <typename Ehdr, typename Phdr, typename ElfInterfaceType>
void ElfInterfaceTest::CheckLoadBiasInFirstExecPhdr(uint64_t offset, uint64_t vaddr,
                                                    int64_t load_bias) {
  Ehdr ehdr = {};
  ehdr.e_phoff = 0x100;
  ehdr.e_phnum = 3;
  ehdr.e_phentsize = sizeof(Phdr);
  fake_memory_->SetMemory(0, &ehdr, sizeof(ehdr));

  Phdr phdr = {};
  phdr.p_type = PT_LOAD;
  phdr.p_memsz = 0x10000;
  phdr.p_flags = PF_R;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100, &phdr, sizeof(phdr));

  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = offset;
  phdr.p_vaddr = vaddr;
  phdr.p_memsz = 0x2000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x100 + sizeof(phdr), &phdr, sizeof(phdr));

  // Second executable load should be ignored for load bias computation.
  memset(&phdr, 0, sizeof(phdr));
  phdr.p_type = PT_LOAD;
  phdr.p_offset = 0x1234;
  phdr.p_vaddr = 0x2000;
  phdr.p_memsz = 0x2000;
  phdr.p_flags = PF_R | PF_X;
  phdr.p_align = 0x1000;
  fake_memory_->SetMemory(0x200 + sizeof(phdr), &phdr, sizeof(phdr));

  int64_t static_load_bias = ElfInterface::GetLoadBias<Ehdr, Phdr>(memory_.get());
  ASSERT_EQ(load_bias, static_load_bias);

  std::unique_ptr<ElfInterfaceType> elf(new ElfInterfaceType(memory_));
  int64_t init_load_bias = 0;
  ASSERT_TRUE(elf->Init(&init_load_bias));
  ASSERT_EQ(init_load_bias, static_load_bias);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_zero_32) {
  CheckLoadBiasInFirstExecPhdr<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x1000, 0x1000, 0);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_zero_64) {
  CheckLoadBiasInFirstExecPhdr<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x1000, 0x1000, 0);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_positive_32) {
  CheckLoadBiasInFirstExecPhdr<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x1000, 0x4000, 0x3000);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_positive_64) {
  CheckLoadBiasInFirstExecPhdr<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x1000, 0x4000, 0x3000);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_negative_32) {
  CheckLoadBiasInFirstExecPhdr<Elf32_Ehdr, Elf32_Phdr, ElfInterface32>(0x5000, 0x1000, -0x4000);
}

TEST_F(ElfInterfaceTest, get_load_bias_exec_negative_64) {
  CheckLoadBiasInFirstExecPhdr<Elf64_Ehdr, Elf64_Phdr, ElfInterface64>(0x5000, 0x1000, -0x4000);
}

TEST_F(ElfInterfaceTest, huge_gnu_debugdata_size) {
  std::shared_ptr<Memory> empty;
  ElfInterfaceFake interface(empty);

  interface.FakeSetGnuDebugdataOffset(0x1000);
  interface.FakeSetGnuDebugdataSize(0xffffffffffffffffUL);
  ASSERT_TRUE(interface.CreateGnuDebugdataMemory() == nullptr);

  interface.FakeSetGnuDebugdataSize(0x4000000000000UL);
  ASSERT_TRUE(interface.CreateGnuDebugdataMemory() == nullptr);

  // This should exceed the size_t value of the first allocation.
#if defined(__LP64__)
  interface.FakeSetGnuDebugdataSize(0x3333333333333334ULL);
#else
  interface.FakeSetGnuDebugdataSize(0x33333334);
#endif
  ASSERT_TRUE(interface.CreateGnuDebugdataMemory() == nullptr);
}

TEST_F(ElfInterfaceTest, compressed_eh_frames) {
  SectionInfo eh_hdr_info = {.offset = 0x1000};
  uint8_t data[5] = {/*version*/ 1, /*ptr_encoding DW_EH_PE_omit*/ 0xff,
                     /*fde_count_encoding DW_EH_PE_udata1*/ 0xd,
                     /*table_encoding DW_EH_PE_absptr*/ 0, /*fde_count*/ 1};
  fake_memory_->SetMemory(0x1000, data, sizeof(data));
  SectionInfo eh_info = {.offset = 0x2000};

  // Verify that the eh_frame and eh_frame_hdr are created properly.
  ElfInterface32Fake interface(memory_);
  eh_hdr_info.flags = 0;
  interface.FakeSetEhFrameHdrInfo(eh_hdr_info);
  eh_info.flags = 0;
  interface.FakeSetEhFrameInfo(eh_info);
  interface.InitHeaders();
  EXPECT_NE(0U, interface.eh_frame_hdr_info().offset);
  EXPECT_NE(0U, interface.eh_frame_info().offset);
  EXPECT_TRUE(interface.eh_frame() != nullptr);

  // Init setting SHF_COMPRESSED for both sections, both should fail to init.
  ElfInterface32Fake interface_both(memory_);
  eh_hdr_info.flags = 0x800;
  interface_both.FakeSetEhFrameHdrInfo(eh_hdr_info);
  eh_info.flags = 0x800;
  interface_both.FakeSetEhFrameInfo(eh_info);
  interface_both.InitHeaders();
  EXPECT_EQ(0U, interface_both.eh_frame_hdr_info().offset);
  EXPECT_EQ(0U, interface_both.eh_frame_info().offset);
  EXPECT_TRUE(interface_both.eh_frame() == nullptr);

  // Init setting SHF_COMPRESSED for only the eh_frame_hdr, eh_frame should init.
  ElfInterface32Fake interface_hdr(memory_);
  eh_hdr_info.flags = 0x800;
  interface_hdr.FakeSetEhFrameHdrInfo(eh_hdr_info);
  eh_info.flags = 0;
  interface_hdr.FakeSetEhFrameInfo(eh_info);
  interface_hdr.InitHeaders();
  EXPECT_EQ(0U, interface_hdr.eh_frame_hdr_info().offset);
  EXPECT_NE(0U, interface_hdr.eh_frame_info().offset);
  EXPECT_TRUE(interface_hdr.eh_frame() != nullptr);

  // Init setting SHF_COMPRESSED for only the eh_frame, both should fail to init.
  ElfInterface32Fake interface_eh(memory_);
  eh_hdr_info.flags = 0;
  interface_eh.FakeSetEhFrameHdrInfo(eh_hdr_info);
  eh_info.flags = 0x800;
  interface_eh.FakeSetEhFrameInfo(eh_info);
  interface_eh.InitHeaders();
  EXPECT_EQ(0U, interface_eh.eh_frame_hdr_info().offset);
  EXPECT_EQ(0U, interface_eh.eh_frame_info().offset);
  EXPECT_TRUE(interface_eh.eh_frame() == nullptr);
}

TEST_F(ElfInterfaceTest, compressed_debug_frame_fde_verify) {
  std::string lib_dir = TestGetFileDirectory() + "libs/";
  auto elf_memory = Memory::CreateFileMemory(lib_dir + "libc.so", 0);
  Elf elf(elf_memory);
  ASSERT_TRUE(elf.Init());
  ASSERT_TRUE(elf.valid());
  auto section = elf.interface()->debug_frame();
  ASSERT_TRUE(section != nullptr);

  elf_memory = Memory::CreateFileMemory(lib_dir + "libc_zlib.so", 0);
  Elf elf_zlib(elf_memory);
  ASSERT_TRUE(elf_zlib.Init());
  ASSERT_TRUE(elf_zlib.valid());
  auto section_zlib = elf_zlib.interface()->debug_frame();
  ASSERT_TRUE(section_zlib != nullptr);

  elf_memory = Memory::CreateFileMemory(lib_dir + "libc_zstd.so", 0);
  Elf elf_zstd(elf_memory);
  ASSERT_TRUE(elf_zstd.Init());
  ASSERT_TRUE(elf_zstd.valid());
  auto section_zstd = elf_zstd.interface()->debug_frame();
  ASSERT_TRUE(section_zstd != nullptr);

  auto iter = section->begin();
  auto iter_zlib = section_zlib->begin();
  auto iter_zstd = section_zstd->begin();

  // Check that all of the fdes are in the same order, and contain the same data.
  size_t total_fdes = 0;
  while (iter != section->end() && iter_zlib != section_zlib->end() &&
         iter_zstd != section_zstd->end()) {
    ASSERT_TRUE(iter != section->end());
    ASSERT_TRUE(iter_zlib != section_zlib->end());
    ASSERT_TRUE(iter_zstd != section_zstd->end());
    auto fde = *iter;
    auto fde_zlib = *iter_zlib;
    auto fde_zstd = *iter_zstd;
    EXPECT_EQ(fde->cie_offset, fde_zlib->cie_offset);
    EXPECT_EQ(fde->cie_offset, fde_zstd->cie_offset);
    EXPECT_EQ(fde->cfa_instructions_offset, fde_zlib->cfa_instructions_offset);
    EXPECT_EQ(fde->cfa_instructions_offset, fde_zstd->cfa_instructions_offset);
    EXPECT_EQ(fde->cfa_instructions_end, fde_zlib->cfa_instructions_end);
    EXPECT_EQ(fde->cfa_instructions_end, fde_zstd->cfa_instructions_end);
    EXPECT_EQ(fde->pc_start, fde_zlib->pc_start);
    EXPECT_EQ(fde->pc_start, fde_zstd->pc_start);
    EXPECT_EQ(fde->pc_end, fde_zlib->pc_end);
    EXPECT_EQ(fde->pc_end, fde_zstd->pc_end);
    EXPECT_EQ(fde->lsda_address, fde_zlib->lsda_address);
    EXPECT_EQ(fde->lsda_address, fde_zstd->lsda_address);
    ++iter;
    ++iter_zlib;
    ++iter_zstd;
    ++total_fdes;
  }
  EXPECT_EQ(2320U, total_fdes);
}

TEST_F(ElfInterfaceTest, compressed_debug_frame_from_memory) {
  std::string lib_dir = TestGetFileDirectory() + "libs/";
  android::base::unique_fd fd(
      TEMP_FAILURE_RETRY(open((lib_dir + "libc_zstd.so").c_str(), O_RDONLY | O_CLOEXEC)));
  ASSERT_NE(-1, fd);
  struct stat buf;
  ASSERT_NE(-1, fstat(fd, &buf));
  void* map_addr = mmap(nullptr, buf.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
  ASSERT_NE(MAP_FAILED, map_addr);
  auto process_memory = Memory::CreateProcessMemory(getpid());
  std::shared_ptr<Memory> elf_memory(
      new MemoryRange(process_memory, reinterpret_cast<uint64_t>(map_addr), buf.st_size, 0));

  Elf elf(elf_memory);
  ASSERT_TRUE(elf.Init());
  ASSERT_TRUE(elf.valid());
  auto section = elf.interface()->debug_frame();
  ASSERT_TRUE(section != nullptr);

  // Don't check all of the fdes, just verify the first one.
  std::vector<const DwarfFde*> fdes;
  section->GetFdes(&fdes);
  EXPECT_EQ(0x9309cU, fdes[0]->cie_offset);
  EXPECT_EQ(0x930c0U, fdes[0]->cfa_instructions_offset);
  EXPECT_EQ(0x930c0U, fdes[0]->cfa_instructions_end);
  EXPECT_EQ(0U, fdes[0]->pc_start);
  EXPECT_EQ(2U, fdes[0]->pc_end);
  EXPECT_EQ(0U, fdes[0]->lsda_address);
  EXPECT_EQ(2320U, fdes.size());

  munmap(map_addr, buf.st_size);
}

TEST_F(ElfInterfaceTest, bad_compressed_debug_frame) {
  std::string lib_dir = TestGetFileDirectory() + "libs/";
  auto elf_memory = Memory::CreateFileMemory(TestGetFileDirectory() + "libs/elf_bad_compress", 0);
  Elf elf(elf_memory);
  ASSERT_TRUE(elf.Init());
  ASSERT_TRUE(elf.valid());
  // This elf file has a compressed debug frame, but it's bad compress data.
  ASSERT_TRUE(elf.interface()->debug_frame() == nullptr);
}

}  // namespace unwindstack
