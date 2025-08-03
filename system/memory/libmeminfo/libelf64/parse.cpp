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
 * limitations under the License. */

#include <libelf64/parse.h>

#include <elf.h>

#include <fstream>

using namespace std;

namespace android {
namespace elf64 {

bool Elf64Parser::OpenElfFile(const std::string& fileName, std::ifstream& elfFile) {
    elfFile.open(fileName.c_str(), std::ifstream::in);

    return elfFile.is_open();
}

void Elf64Parser::CloseElfFile(std::ifstream& elfFile) {
    if (!elfFile.is_open())
        elfFile.close();
}

// Parse the executable header.
//
// Note: The command below can be used to print the executable header:
//
//  $ readelf -h ../a.out
bool Elf64Parser::ParseExecutableHeader(std::ifstream& elfFile, Elf64Binary& elf64Binary) {
    // Move the cursor position to the very beginning.
    elfFile.seekg(0);
    elfFile.read((char*)&elf64Binary.ehdr, sizeof(elf64Binary.ehdr));

    return elfFile.good();
}

bool Elf64Parser::IsElf64(Elf64Binary& elf64Binary) {
    return elf64Binary.ehdr.e_ident[EI_CLASS] == ELFCLASS64;
}

// Parse the Program or Segment Headers.
//
// Note: The command below can be used to print the program headers:
//
//  $ readelf --program-headers ./example_4k
//  $ readelf -l ./example_4k
bool Elf64Parser::ParseProgramHeaders(std::ifstream& elfFile, Elf64Binary& elf64Binary) {
    uint64_t phOffset = elf64Binary.ehdr.e_phoff;
    uint16_t phNum = elf64Binary.ehdr.e_phnum;

    // Move the cursor position to the program header offset.
    elfFile.seekg(phOffset);

    for (int i = 0; i < phNum; i++) {
        Elf64_Phdr phdr;

        elfFile.read((char*)&phdr, sizeof(phdr));
        if (!elfFile.good())
            return false;

        elf64Binary.phdrs.push_back(phdr);
    }

    return true;
}

bool Elf64Parser::ParseSections(std::ifstream& elfFile, Elf64Binary& elf64Binary) {
    Elf64_Sc sStrTblPtr;

    // Parse sections after reading all the section headers.
    for (int i = 0; i < elf64Binary.shdrs.size(); i++) {
        Elf64_Shdr shdr = elf64Binary.shdrs[i];
        uint64_t sOffset = shdr.sh_offset;
        uint64_t sSize = shdr.sh_size;

        Elf64_Sc section;

        // Skip .bss section.
        if (shdr.sh_type != SHT_NOBITS) {
            section.data.resize(sSize);

            // Move the cursor position to the section offset.
            elfFile.seekg(sOffset);
            elfFile.read(section.data.data(), sSize);
            if (!elfFile.good())
                return false;
        }

        section.size = sSize;
        section.index = i;

        // The index of the string table is in the executable header.
        if (elf64Binary.ehdr.e_shstrndx == i) {
            sStrTblPtr = section;
        }

        elf64Binary.sections.push_back(section);
    }

    // Set the data section name.
    // This is done after reading the data section with index e_shstrndx.
    for (int i = 0; i < elf64Binary.sections.size(); i++) {
        Elf64_Sc section = elf64Binary.sections[i];
        Elf64_Shdr shdr = elf64Binary.shdrs[i];
        uint32_t nameIdx = shdr.sh_name;
        char* st = sStrTblPtr.data.data();

        section.name = &st[nameIdx];
    }

    return true;
}

// Parse the Section Headers.
//
// Note: The command below can be used to print the section headers:
//
//   $ readelf --sections ./example_4k
//   $ readelf -S ./example_4k
bool Elf64Parser::ParseSectionHeaders(std::ifstream& elfFile, Elf64Binary& elf64Binary) {
    uint64_t shOffset = elf64Binary.ehdr.e_shoff;
    uint16_t shNum = elf64Binary.ehdr.e_shnum;

    // Move the cursor position to the section headers offset.
    elfFile.seekg(shOffset);

    for (int i = 0; i < shNum; i++) {
        Elf64_Shdr shdr;

        elfFile.read((char*)&shdr, sizeof(shdr));
        if (!elfFile.good())
            return false;

        elf64Binary.shdrs.push_back(shdr);
    }

    return true;
}

// Parse the elf file and populate the elfBinary object.
bool Elf64Parser::ParseElfFile(const std::string& fileName, Elf64Binary& elf64Binary) {
    std::ifstream elfFile;
    bool ret = false;

    if (OpenElfFile(fileName, elfFile) &&
        ParseExecutableHeader(elfFile, elf64Binary) &&
        IsElf64(elf64Binary) &&
        ParseProgramHeaders(elfFile, elf64Binary) &&
        ParseSectionHeaders(elfFile, elf64Binary) &&
        ParseSections(elfFile, elf64Binary)) {
        elf64Binary.path = fileName;
        ret = true;
    }

    CloseElfFile(elfFile);

    return ret;
}

}  // namespace elf64
}  // namespace android

