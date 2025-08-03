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

#include <elf.h>
#include <stdio.h>

#include <iostream>
#include <string>

#include "./elf.h"

namespace shell_as {

namespace {
// The base address of a PIE binary when loaded with ASLR disabled.
#if defined(__arm__) || defined(__aarch64__)
constexpr uint64_t k32BitImageBase = 0xAAAAA000;
constexpr uint64_t k64BitImageBase = 0x5555555000;
#else
constexpr uint64_t k32BitImageBase = 0x56555000;
constexpr uint64_t k64BitImageBase = 0x555555554000;
#endif
}  // namespace

bool GetElfEntryPoint(const pid_t process_id, uint64_t* entry_address,
                      bool* is_arm_mode) {
  uint8_t elf_header_buffer[sizeof(Elf64_Ehdr)];
  std::string exe_path = "/proc/" + std::to_string(process_id) + "/exe";
  FILE* exe_file = fopen(exe_path.c_str(), "rb");
  if (exe_file == nullptr) {
    std::cerr << "Unable to open executable of process " << process_id
              << std::endl;
    return false;
  }

  int read_size =
      fread(elf_header_buffer, sizeof(elf_header_buffer), 1, exe_file);
  fclose(exe_file);
  if (read_size <= 0) {
    std::cerr << "Unable to read executable of process " << process_id
              << std::endl;
    return false;
  }

  const Elf32_Ehdr* file_header_32 = (Elf32_Ehdr*)elf_header_buffer;
  const Elf64_Ehdr* file_header_64 = (Elf64_Ehdr*)elf_header_buffer;
  // The first handful of bytes of a header do not depend on whether the file is
  // 32bit vs 64bit.
  const bool is_pie_binary = file_header_32->e_type == ET_DYN;

  if (file_header_32->e_ident[EI_CLASS] == ELFCLASS32) {
    *entry_address =
        file_header_32->e_entry + (is_pie_binary ? k32BitImageBase : 0);
  } else if (file_header_32->e_ident[EI_CLASS] == ELFCLASS64) {
    *entry_address =
        file_header_64->e_entry + (is_pie_binary ? k64BitImageBase : 0);
  } else {
    return false;
  }

  *is_arm_mode = false;
#if defined(__arm__)
  if ((*entry_address & 1) == 0) {
    *is_arm_mode = true;
  }
  // The entry address for ARM Elf binaries is branched to using a BX
  // instruction. The low bit of these instructions indicates the instruction
  // set of the code that is being jumped to. A low bit of 1 indicates thumb
  // mode while a low bit of 0 indicates ARM mode.
  *entry_address &= ~1;
#endif

  return true;
}

}  // namespace shell_as
