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

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include <fstream>
#include <memory>
#include <optional>
#include <vector>

#include "elf/relocation.h"
#include "libelf/elf.h"

namespace {
// Read an entire ELF file into the memory
std::vector<uint8_t> ReadFile(const std::string& file_name) {
  std::ifstream elf_file(file_name, std::ios::binary | std::ios::ate);
  std::streamsize file_size = elf_file.tellg();
  elf_file.seekg(0, std::ios::beg);

  std::vector<uint8_t> ret(file_size);
  elf_file.read(reinterpret_cast<char*>(ret.data()), file_size);
  return ret;
}
}  // namespace

#define ASSERT(cond)                                                                 \
  {                                                                                  \
    if (!(cond)) {                                                                   \
      fprintf(stderr, "%s:%d. Assert failed \"(%s)\"\n", __FILE__, __LINE__, #cond); \
      exit(1);                                                                       \
    }                                                                                \
  }

// Simulate an ELF relocation processs using GBL relocation library. Fail if the ELF contains
// relocation types that we don't expect.
//
// Usage:
//   <executable> <ELF file> <Binary only version (ELF header stripped) of the ELF>
//
// The binary only version can be obtained by `llvm-objcopy -O binary <ELF> <output>`
//
// Return 0 if successful.
int main(int argc, char* argv[]) {
  if (argc != 3) {
    return 1;
  }

  std::vector<uint8_t> elf_file = ReadFile(argv[1]);
  std::vector<uint8_t> bin_file = ReadFile(argv[2]);

  Elf64_Ehdr elf_header;
  ASSERT(elf_file.size() >= sizeof(elf_header));
  memcpy(&elf_header, elf_file.data(), sizeof(elf_header));

  // Iterate all program headers and find out the address for dynamic section.
  const uint8_t* program_hdr_start = elf_file.data() + elf_header.e_phoff;
  std::optional<uint64_t> dynamic_section_offset = std::nullopt;
  for (size_t i = 0; i < elf_header.e_phnum; i++) {
    const uint8_t* header_start = program_hdr_start + i * sizeof(Elf64_Phdr);
    Elf64_Phdr elf_program_header;
    ASSERT(static_cast<size_t>(header_start + sizeof(elf_program_header) - elf_file.data()) <=
           elf_file.size());
    memcpy(&elf_program_header, header_start, sizeof(elf_program_header));
    if (elf_program_header.p_type == PT_DYNAMIC) {
      // Store the physical address
      // For PIE ELF this is the relative offset to ELF file start.
      dynamic_section_offset = elf_program_header.p_paddr;
      break;
    }
  }

  // Dynamic section not found.
  if (!dynamic_section_offset) {
    return 1;
  }

  ASSERT(bin_file.size() >= *dynamic_section_offset);
  // Perform relocation as if we have loaded the program @ address bin_file.data().
  return ApplyRelocation(reinterpret_cast<uintptr_t>(bin_file.data()),
                         reinterpret_cast<uintptr_t>(bin_file.data() + *dynamic_section_offset))
             ? 0
             : 1;
}
