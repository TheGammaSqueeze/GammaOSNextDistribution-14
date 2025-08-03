// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2020-2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Maria Teguiani
// Author: Giuliano Procida
// Author: Aleksei Vetrov

#include "elf_loader.h"

#include <elf.h>
#include <gelf.h>
#include <libelf.h>

#include <cstddef>
#include <cstring>
#include <functional>
#include <limits>
#include <ostream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "error.h"
#include "graph.h"

namespace stg {
namespace elf {

namespace {

SymbolTableEntry::SymbolType ParseSymbolType(unsigned char symbol_type) {
  switch (symbol_type) {
    case STT_NOTYPE:
      return SymbolTableEntry::SymbolType::NOTYPE;
    case STT_OBJECT:
      return SymbolTableEntry::SymbolType::OBJECT;
    case STT_FUNC:
      return SymbolTableEntry::SymbolType::FUNCTION;
    case STT_SECTION:
      return SymbolTableEntry::SymbolType::SECTION;
    case STT_FILE:
      return SymbolTableEntry::SymbolType::FILE;
    case STT_COMMON:
      return SymbolTableEntry::SymbolType::COMMON;
    case STT_TLS:
      return SymbolTableEntry::SymbolType::TLS;
    case STT_GNU_IFUNC:
      return SymbolTableEntry::SymbolType::GNU_IFUNC;
    default:
      Die() << "Unknown ELF symbol type: " << symbol_type;
  }
}

SymbolTableEntry::Binding ParseSymbolBinding(unsigned char binding) {
  switch (binding) {
    case STB_LOCAL:
      return SymbolTableEntry::Binding::LOCAL;
    case STB_GLOBAL:
      return SymbolTableEntry::Binding::GLOBAL;
    case STB_WEAK:
      return SymbolTableEntry::Binding::WEAK;
    case STB_GNU_UNIQUE:
      return SymbolTableEntry::Binding::GNU_UNIQUE;
    default:
      Die() << "Unknown ELF symbol binding: " << binding;
  }
}

SymbolTableEntry::Visibility ParseSymbolVisibility(unsigned char visibility) {
  switch (visibility) {
    case STV_DEFAULT:
      return SymbolTableEntry::Visibility::DEFAULT;
    case STV_INTERNAL:
      return SymbolTableEntry::Visibility::INTERNAL;
    case STV_HIDDEN:
      return SymbolTableEntry::Visibility::HIDDEN;
    case STV_PROTECTED:
      return SymbolTableEntry::Visibility::PROTECTED;
    default:
      Die() << "Unknown ELF symbol visibility: " << visibility;
  }
}

SymbolTableEntry::ValueType ParseSymbolValueType(Elf64_Section section_index) {
  switch (section_index) {
    case SHN_UNDEF:
      return SymbolTableEntry::ValueType::UNDEFINED;
    case SHN_ABS:
      return SymbolTableEntry::ValueType::ABSOLUTE;
    case SHN_COMMON:
      return SymbolTableEntry::ValueType::COMMON;
    default:
      return SymbolTableEntry::ValueType::RELATIVE_TO_SECTION;
  }
}

std::string ElfHeaderTypeToString(unsigned char elf_header_type) {
  switch (elf_header_type) {
    case ET_NONE:
      return "none";
    case ET_REL:
      return "relocatable";
    case ET_EXEC:
      return "executable";
    case ET_DYN:
      return "shared object";
    case ET_CORE:
      return "coredump";
    default:
      return "unknown (type = " + std::to_string(elf_header_type) + ')';
  }
}

std::string ElfSectionTypeToString(Elf64_Word elf_section_type) {
  switch (elf_section_type) {
    case SHT_SYMTAB:
      return "symtab";
    case SHT_DYNSYM:
      return "dynsym";
    case SHT_GNU_verdef:
      return "GNU_verdef";
    case SHT_GNU_verneed:
      return "GNU_verneed";
    case SHT_GNU_versym:
      return "GNU_versym";
    default:
      return "unknown (type = " + std::to_string(elf_section_type) + ')';
  }
}

GElf_Half GetMachine(Elf* elf) {
  GElf_Ehdr header;
  Check(gelf_getehdr(elf, &header) != nullptr) << "could not get ELF header";
  return header.e_machine;
}

void AdjustAddress(GElf_Half machine, SymbolTableEntry& entry) {
  if (machine == EM_ARM) {
    if (entry.symbol_type == SymbolTableEntry::SymbolType::FUNCTION
        || entry.symbol_type == SymbolTableEntry::SymbolType::GNU_IFUNC) {
      // Clear bit zero of ARM32 addresses as per "ELF for the Arm Architecture"
      // section 5.5.3.  https://static.docs.arm.com/ihi0044/g/aaelf32.pdf
      entry.value &= ~1;
    }
  } else if (machine == EM_AARCH64) {
    // Copy bit 55 over bits 56 to 63 which may be tag information.
    entry.value = entry.value & (1ULL << 55)
                  ? entry.value | (0xffULL << 56)
                  : entry.value & ~(0xffULL << 56);
  }
}

std::vector<Elf_Scn*> GetSectionsIf(
    Elf* elf, const std::function<bool(const GElf_Shdr&)>& predicate) {
  std::vector<Elf_Scn*> result;
  Elf_Scn* section = nullptr;
  GElf_Shdr header;
  while ((section = elf_nextscn(elf, section)) != nullptr) {
    Check(gelf_getshdr(section, &header) != nullptr)
        << "could not get ELF section header";
    if (predicate(header)) {
      result.push_back(section);
    }
  }
  return result;
}

std::vector<Elf_Scn*> GetSectionsByName(Elf* elf, const std::string& name) {
  size_t shdr_strtab_index;
  Check(elf_getshdrstrndx(elf, &shdr_strtab_index) == 0)
      << "could not get ELF section header string table index";
  return GetSectionsIf(elf, [&](const GElf_Shdr& header) {
    const auto* section_name =
        elf_strptr(elf, shdr_strtab_index, header.sh_name);
    return section_name != nullptr && section_name == name;
  });
}

Elf_Scn* MaybeGetSectionByName(Elf* elf, const std::string& name) {
  const auto sections = GetSectionsByName(elf, name);
  if (sections.empty()) {
    return nullptr;
  }
  Check(sections.size() == 1)
      << "multiple sections found with name '" << name << "'";
  return sections[0];
}

Elf_Scn* GetSectionByName(Elf* elf, const std::string& name) {
  Elf_Scn* section = MaybeGetSectionByName(elf, name);
  Check(section != nullptr) << "no section found with name '" << name << "'";
  return section;
}

Elf_Scn* MaybeGetSectionByType(Elf* elf, Elf64_Word type) {
  auto sections = GetSectionsIf(
      elf, [&](const GElf_Shdr& header) { return header.sh_type == type; });
  if (sections.empty()) {
    return nullptr;
  }
  Check(sections.size() == 1) << "multiple sections found with type " << type;
  return sections[0];
}

Elf_Scn* GetSectionByIndex(Elf* elf, size_t index) {
  Elf_Scn* section = elf_getscn(elf, index);
  Check(section != nullptr) << "no section found with index " << index;
  return section;
}

struct SectionInfo {
  GElf_Shdr header;
  Elf_Data* data;
};

SectionInfo GetSectionInfo(Elf_Scn* section) {
  const size_t index = elf_ndxscn(section);
  GElf_Shdr section_header;
  Check(gelf_getshdr(section, &section_header) != nullptr)
      << "failed to read section (index = " << index << ") header";
  Elf_Data* data = elf_getdata(section, nullptr);
  Check(data != nullptr) << "section (index = " << index << ") data is invalid";
  return {section_header, data};
}

size_t GetNumberOfEntries(const GElf_Shdr& section_header) {
  Check(section_header.sh_entsize != 0)
      << "zero table entity size is unexpected for section "
      << ElfSectionTypeToString(section_header.sh_type);
  return section_header.sh_size / section_header.sh_entsize;
}

std::string_view GetString(Elf* elf, uint32_t section, size_t offset) {
  const auto name = elf_strptr(elf, section, offset);

  Check(name != nullptr) << "string was not found (section: " << section
                         << ", offset: " << offset << ")";
  return name;
}

Elf_Scn* GetSymbolTableSection(Elf* elf, bool is_linux_kernel_binary) {
  GElf_Ehdr elf_header;
  Check(gelf_getehdr(elf, &elf_header) != nullptr)
      << "could not get ELF header";

  Elf_Scn* symtab = MaybeGetSectionByType(elf, SHT_SYMTAB);
  Elf_Scn* dynsym = MaybeGetSectionByType(elf, SHT_DYNSYM);
  if (symtab != nullptr && dynsym != nullptr) {
    // Relocatable ELF binaries, Linux kernel and modules have their
    // exported symbols in .symtab, all other ELF types have their
    // exported symbols in .dynsym.
    if (elf_header.e_type == ET_REL || is_linux_kernel_binary) {
      return symtab;
    }
    if (elf_header.e_type == ET_DYN || elf_header.e_type == ET_EXEC) {
      return dynsym;
    }
    Die() << "unsupported ELF type: '"
          << ElfHeaderTypeToString(elf_header.e_type) << "'";
  } else if (symtab != nullptr) {
    return symtab;
  } else if (dynsym != nullptr) {
    return dynsym;
  } else {
    Die() << "no ELF symbol table found";
  }
}


constexpr std::string_view kCFISuffix = ".cfi";

bool IsCFISymbolName(std::string_view name) {
  // Check if symbol name ends with ".cfi"
  // TODO: use std::string_view::ends_with
  return (name.size() >= kCFISuffix.size() &&
          name.substr(name.size() - kCFISuffix.size()) == kCFISuffix);
}

}  // namespace

std::string_view UnwrapCFISymbolName(std::string_view cfi_name) {
  Check(IsCFISymbolName(cfi_name))
      << "CFI symbol " << cfi_name << " doesn't end with .cfi";
  return cfi_name.substr(0, cfi_name.size() - kCFISuffix.size());
}

namespace {

std::vector<SymbolTableEntry> GetSymbols(
    Elf* elf, Elf_Scn* symbol_table_section, bool cfi) {
  const auto machine = GetMachine(elf);
  const auto [symbol_table_header, symbol_table_data] =
      GetSectionInfo(symbol_table_section);
  const size_t number_of_symbols = GetNumberOfEntries(symbol_table_header);

  std::vector<SymbolTableEntry> result;
  result.reserve(number_of_symbols);

  // GElf uses int for indexes in symbol table, prevent int overflow.
  Check(number_of_symbols <= std::numeric_limits<int>::max())
      << "number of symbols exceeds INT_MAX";
  for (size_t i = 0; i < number_of_symbols; ++i) {
    GElf_Sym symbol;
    Check(gelf_getsym(symbol_table_data, static_cast<int>(i), &symbol) !=
          nullptr)
        << "symbol (i = " << i << ") was not found";

    const auto name =
        GetString(elf, symbol_table_header.sh_link, symbol.st_name);
    if (cfi != IsCFISymbolName(name)) {
      continue;
    }
    SymbolTableEntry entry{
        .name = name,
        .value = symbol.st_value,
        .size = symbol.st_size,
        .symbol_type = ParseSymbolType(GELF_ST_TYPE(symbol.st_info)),
        .binding = ParseSymbolBinding(GELF_ST_BIND(symbol.st_info)),
        .visibility =
            ParseSymbolVisibility(GELF_ST_VISIBILITY(symbol.st_other)),
        .section_index = symbol.st_shndx,
        .value_type = ParseSymbolValueType(symbol.st_shndx),
    };
    AdjustAddress(machine, entry);
    result.push_back(entry);
  }

  return result;
}

bool IsLinuxKernelBinary(Elf* elf) {
  // The Linux kernel itself has many specific sections that are sufficient to
  // classify a binary as kernel binary if present, `__ksymtab_strings` is one
  // of them. It is present if a kernel binary (vmlinux or a module) exports
  // symbols via the EXPORT_SYMBOL_* macros and it contains symbol names and
  // namespaces which form part of the ABI.
  //
  // Kernel modules might not present a `__ksymtab_strings` section if they do
  // not export symbols themselves via the ksymtab. Yet they can be identified
  // by the presence of the `.modinfo` section. Since that is somewhat a generic
  // name, also check for the presence of `.gnu.linkonce.this_module` to get
  // solid signal as both of those sections are present in kernel modules.
  return MaybeGetSectionByName(elf, "__ksymtab_strings") != nullptr ||
         (MaybeGetSectionByName(elf, ".modinfo") != nullptr &&
          MaybeGetSectionByName(elf, ".gnu.linkonce.this_module") != nullptr);
}

bool IsRelocatable(Elf* elf) {
  GElf_Ehdr elf_header;
  Check(gelf_getehdr(elf, &elf_header) != nullptr)
      << "could not get ELF header";

  return elf_header.e_type == ET_REL;
}

bool IsLittleEndianBinary(Elf* elf) {
  GElf_Ehdr elf_header;
  Check(gelf_getehdr(elf, &elf_header) != nullptr)
      << "could not get ELF header";

  switch (auto endianness = elf_header.e_ident[EI_DATA]) {
    case ELFDATA2LSB:
      return true;
    case ELFDATA2MSB:
      return false;
    default:
      Die() << "Unsupported ELF endianness: " << endianness;
  }
}

}  // namespace

std::ostream& operator<<(std::ostream& os, SymbolTableEntry::SymbolType type) {
  using SymbolType = SymbolTableEntry::SymbolType;
  switch (type) {
    case SymbolType::NOTYPE:
      return os << "notype";
    case SymbolType::OBJECT:
      return os << "object";
    case SymbolType::FUNCTION:
      return os << "function";
    case SymbolType::SECTION:
      return os << "section";
    case SymbolType::FILE:
      return os << "file";
    case SymbolType::COMMON:
      return os << "common";
    case SymbolType::TLS:
      return os << "TLS";
    case SymbolType::GNU_IFUNC:
      return os << "indirect (ifunc) function";
  }
}

std::ostream& operator<<(std::ostream& os,
                         const SymbolTableEntry::ValueType type) {
  using ValueType = SymbolTableEntry::ValueType;
  switch (type) {
    case ValueType::UNDEFINED:
      return os << "undefined";
    case ValueType::ABSOLUTE:
      return os << "absolute";
    case ValueType::COMMON:
      return os << "common";
    case ValueType::RELATIVE_TO_SECTION:
      return os << "relative";
  }
}

ElfLoader::ElfLoader(Elf* elf)
    : elf_(elf) {
  Check(elf_ != nullptr) << "No ELF was provided";
  InitializeElfInformation();
}

void ElfLoader::InitializeElfInformation() {
  is_linux_kernel_binary_ = elf::IsLinuxKernelBinary(elf_);
  is_relocatable_ = elf::IsRelocatable(elf_);
  is_little_endian_binary_ = elf::IsLittleEndianBinary(elf_);
}

std::string_view ElfLoader::GetBtfRawData() const {
  Elf_Scn* btf_section = GetSectionByName(elf_, ".BTF");
  Check(btf_section != nullptr) << ".BTF section is invalid";
  Elf_Data* elf_data = elf_rawdata(btf_section, nullptr);
  Check(elf_data != nullptr) << ".BTF section data is invalid";
  const char* btf_start = static_cast<char*>(elf_data->d_buf);
  const size_t btf_size = elf_data->d_size;
  return std::string_view(btf_start, btf_size);
}

std::vector<SymbolTableEntry> ElfLoader::GetElfSymbols() const {
  Elf_Scn* symbol_table_section =
      GetSymbolTableSection(elf_, is_linux_kernel_binary_);
  Check(symbol_table_section != nullptr)
      << "failed to find symbol table section";

  return GetSymbols(elf_, symbol_table_section, /* cfi = */ false);
}

std::vector<SymbolTableEntry> ElfLoader::GetCFISymbols() const {
  // CFI symbols may be only in .symtab
  Elf_Scn* symbol_table_section = MaybeGetSectionByType(elf_, SHT_SYMTAB);
  if (symbol_table_section == nullptr) {
    // It is possible for ET_DYN and ET_EXEC ELF binaries to not have .symtab,
    // because it was trimmed away. We can't determine whether there were CFI
    // symbols in the first place, so the best we can do is returning an empty
    // list.
    return {};
  }
  return GetSymbols(elf_, symbol_table_section, /* cfi = */ true);
}

ElfSymbol::CRC ElfLoader::GetElfSymbolCRC(
    const SymbolTableEntry& symbol) const {
  Check(is_little_endian_binary_)
      << "CRC is not supported in big-endian binaries";
  const auto address = GetAbsoluteAddress(symbol);
  if (symbol.value_type == SymbolTableEntry::ValueType::ABSOLUTE) {
    return ElfSymbol::CRC{static_cast<uint32_t>(address)};
  }
  Check(symbol.value_type == SymbolTableEntry::ValueType::RELATIVE_TO_SECTION)
      << "CRC symbol is expected to be absolute or relative to a section";

  const auto section = GetSectionByIndex(elf_, symbol.section_index);
  const auto [header, data] = GetSectionInfo(section);
  Check(data->d_buf != nullptr) << "Section has no data buffer";

  Check(address >= header.sh_addr)
      << "CRC symbol address is below CRC section start";

  const size_t offset = address - header.sh_addr;
  const size_t offset_end = offset + sizeof(uint32_t);
  Check(offset_end <= data->d_size && offset_end <= header.sh_size)
      << "CRC symbol address is above CRC section end";

  return ElfSymbol::CRC{*reinterpret_cast<uint32_t*>(
      reinterpret_cast<char*>(data->d_buf) + offset)};
}

std::string_view ElfLoader::GetElfSymbolNamespace(
    const SymbolTableEntry& symbol) const {
  Check(symbol.value_type == SymbolTableEntry::ValueType::RELATIVE_TO_SECTION)
      << "Namespace symbol is expected to be relative to a section";

  const auto section = GetSectionByIndex(elf_, symbol.section_index);
  const auto [header, data] = GetSectionInfo(section);
  Check(data->d_buf != nullptr) << "Section has no data buffer";

  const auto address = GetAbsoluteAddress(symbol);
  Check(address >= header.sh_addr)
      << "Namespace symbol address is below namespace section start";

  const size_t offset = address - header.sh_addr;
  Check(offset < data->d_size && offset < header.sh_size)
      << "Namespace symbol address is above namespace section end";

  const char* begin = reinterpret_cast<const char*>(data->d_buf) + offset;
  const size_t length = strnlen(begin, data->d_size - offset);
  Check(offset + length < data->d_size)
      << "Namespace string should be null-terminated";

  return std::string_view(begin, length);
}

size_t ElfLoader::GetAbsoluteAddress(const SymbolTableEntry& symbol) const {
  if (symbol.value_type == SymbolTableEntry::ValueType::ABSOLUTE) {
    return symbol.value;
  }
  Check(symbol.value_type == SymbolTableEntry::ValueType::RELATIVE_TO_SECTION)
      << "Only absolute and relative to sections symbols are supported";
  // In relocatable files, st_value holds a section offset for a defined symbol.
  if (is_relocatable_) {
    const auto section = GetSectionByIndex(elf_, symbol.section_index);
    GElf_Shdr header;
    Check(gelf_getshdr(section, &header) != nullptr)
        << "failed to get symbol section header";
    Check(symbol.value + symbol.size <= header.sh_size)
        << "Symbol should be inside the section";
    return symbol.value + header.sh_addr;
  }
  // In executable and shared object files, st_value holds a virtual address.
  return symbol.value;
}

bool ElfLoader::IsLinuxKernelBinary() const {
  return is_linux_kernel_binary_;
}

bool ElfLoader::IsLittleEndianBinary() const {
  return is_little_endian_binary_;
}

}  // namespace elf
}  // namespace stg
