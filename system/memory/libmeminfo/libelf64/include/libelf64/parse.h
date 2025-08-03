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

#include <libelf64/elf64.h>

#include <fstream>

namespace android {
namespace elf64 {

// Class to parse ELF64 binaries.
//
// The class will parse the 4 parts if present:
//
// - Executable header.
// - Program headers (present in executables or shared libraries).
// - Sections (.interp, .init, .plt, .text, .rodata, .data, .bss, .shstrtab, etc).
// - Section headers.
//
class Elf64Parser {
  public:
    // Parse the elf file and populate the elfBinary object.
    static bool ParseElfFile(const std::string& fileName, Elf64Binary& elfBinary);

  private:
    static bool OpenElfFile(const std::string& fileName, std::ifstream& elfFile);
    static void CloseElfFile(std::ifstream& elfFile);
    static bool ParseExecutableHeader(std::ifstream& elfFile, Elf64Binary& elfBinary);
    static bool IsElf64(Elf64Binary& elf64Binary);
    static bool ParseProgramHeaders(std::ifstream& elfFile, Elf64Binary& elfBinary);
    static bool ParseSections(std::ifstream& elfFile, Elf64Binary& elfBinary);
    static bool ParseSectionHeaders(std::ifstream& elfFile, Elf64Binary& elfBinary);
};

}  // namespace elf64
}  // namespace android
