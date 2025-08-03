/*
 * Copyright (C) 2021 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <gtest/gtest.h>

#include <link.h>

#include "ldso_trampoline_phdr.h"

struct PhdrTrimSegmentTestParameters {
  std::string name;
  size_t in_vaddr, in_filesz, in_memsz;
  size_t out_vaddr, out_filesz, out_memsz;
  size_t trim_start, trim_end;
};

class PhdrTrimSegmentTests : public ::testing::TestWithParam<PhdrTrimSegmentTestParameters> {
 protected:
};

TEST_P(PhdrTrimSegmentTests, Tests) {
  auto params = GetParam();
  ElfW(Phdr) phdr{};
  phdr.p_vaddr = params.in_vaddr;
  phdr.p_paddr = params.in_vaddr;
  phdr.p_filesz = params.in_filesz;
  phdr.p_memsz = params.in_memsz;
  phdr_trim_segment(&phdr, params.trim_start, params.trim_end);
  ASSERT_EQ(phdr.p_vaddr, params.out_vaddr);
  ASSERT_EQ(phdr.p_paddr, params.out_vaddr);
  ASSERT_EQ(phdr.p_filesz, params.out_filesz);
  ASSERT_EQ(phdr.p_memsz, params.out_memsz);
}

INSTANTIATE_TEST_CASE_P(ldso_trampoline, PhdrTrimSegmentTests,
                        ::testing::Values(
                            PhdrTrimSegmentTestParameters{
                                .name = "noop",
                                .in_vaddr = 1,
                                .in_filesz = 2,
                                .in_memsz = 2,
                                .trim_start = 1,
                                .trim_end = 3,
                                .out_vaddr = 1,
                                .out_filesz = 2,
                                .out_memsz = 2,
                            },
                            PhdrTrimSegmentTestParameters{
                                .name = "trim_beginning",
                                .in_vaddr = 1,
                                .in_filesz = 2,
                                .in_memsz = 2,
                                .trim_start = 2,
                                .trim_end = 3,
                                .out_vaddr = 2,
                                .out_filesz = 1,
                                .out_memsz = 1,
                            },
                            PhdrTrimSegmentTestParameters{
                                .name = "trim_end",
                                .in_vaddr = 1,
                                .in_filesz = 2,
                                .in_memsz = 2,
                                .trim_start = 1,
                                .trim_end = 2,
                                .out_vaddr = 1,
                                .out_filesz = 1,
                                .out_memsz = 1,
                            },
                            PhdrTrimSegmentTestParameters{
                                .name = "trim_data_bss",
                                .in_vaddr = 1,
                                .in_filesz = 2,
                                .in_memsz = 3,
                                .trim_start = 2,
                                .trim_end = 4,
                                .out_vaddr = 2,
                                .out_filesz = 1,
                                .out_memsz = 2,
                            }),
                        [](const testing::TestParamInfo<PhdrTrimSegmentTestParameters>& info) {
                          return info.param.name;
                        });
