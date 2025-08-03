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

#pragma once

#include <elf.h>
#include <string>
#include <vector>

using namespace std;

namespace android {
namespace elf64 {

// Section content representation
typedef struct {
    std::vector<char> data;   // Raw content of the data section.
    uint64_t size;   // Size of the data section.
    std::string name;      // The name of the section.
    uint16_t index;  // Index of the section.
} Elf64_Sc;

// Class to represent an ELF64 binary.
//
// An ELF binary is formed by 4 parts:
//
// - Executable header.
// - Program headers (present in executables or shared libraries).
// - Sections (.interp, .init, .plt, .text, .rodata, .data, .bss, .shstrtab, etc).
// - Section headers.
//
//                ______________________
//                |                    |
//                | Executable header  |
//                |____________________|
//                |                    |
//                |                    |
//                |  Program headers   |
//                |                    |
//                |____________________|
//                |                    |
//                |                    |
//                |      Sections      |
//                |                    |
//                |____________________|
//                |                    |
//                |                    |
//                |  Section headers   |
//                |                    |
//                |____________________|
//
//
// The structs defined in linux for ELF parts can be found in:
//
//   - /usr/include/elf.h.
//   - https://elixir.bootlin.com/linux/v5.14.21/source/include/uapi/linux/elf.h#L222
class Elf64Binary {
  public:
    Elf64_Ehdr ehdr;
    std::vector<Elf64_Phdr> phdrs;
    std::vector<Elf64_Shdr> shdrs;
    std::vector<Elf64_Sc> sections;
    std::string path;
};

}  // namespace elf64
}  // namespace android
