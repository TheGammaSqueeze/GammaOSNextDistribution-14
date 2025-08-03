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
 *
 */

// The code in this file is responsible for applying dynamic relocation
// for position independent ELF. It itself has to work without any relocation.
// That means all dependencies of this file need to be statically linked. There
// shall not be any reference to any symbol from dynamically linked library
// before relocation is done.
//
// It would be worth to investigate if this can be implemented in rust.

#include <stddef.h>
#include <stdint.h>

#include "libelf/elf.h"

#include "elf/relocation.h"

#ifdef HOST_TOOLING
#include <stdio.h>
#include <stdlib.h>
#define RELOCATION_PRINTF(...) fprintf(stderr, __VA_ARGS__)
#else
#define RELOCATION_PRINTF(...)
#endif

namespace {

struct DynamicSectionInfo {
  const Elf64_Dyn* rel;
  const Elf64_Dyn* rel_size;
  const Elf64_Dyn* rela;
  const Elf64_Dyn* rela_size;
  const Elf64_Sym* symtab;
};

Elf64_Sxword GetRelxAddend(const Elf64_Rela& entry, uint64_t*) {
  return entry.r_addend;
}
Elf64_Sxword GetRelxAddend(const Elf64_Rel&, uint64_t* addr) {
  return *addr;
}

// Perform relocation fixup for the given RELA/REL relocation table.
//
// For more information about REL/RELA relocation, see the Relocation and Dynamic linking
// related sections in System V ABI (https://www.sco.com/developers/gabi/latest/contents.html)
template <typename T>
bool FixUpRelxTable(uintptr_t program_base, [[maybe_unused]] const Elf64_Sym* symtab,
                    uintptr_t table_addr, size_t size) {
  size_t num_entries = size / sizeof(T);
  const T* table = reinterpret_cast<const T*>(table_addr);
  for (size_t i = 0; i < num_entries; i++) {
    const T& entry = table[i];
    // Type of the relocation. It determines the calculation.
    uint64_t reloc_type = entry.r_info & 0xffffffff;
    // Address for storing the new calculated address.
    // TODO(294059825): Add support for overflow checking.
    uint64_t* addr = reinterpret_cast<uint64_t*>(program_base + entry.r_offset);
    // Addend value. For RELA, it comes from the entry. For REL, it's the current
    // value at address `addr`.
    Elf64_Sxword addend = GetRelxAddend(entry, addr);
    // Index of the corresponding symbol in the symbol table.
    // Certain type of relocation requires to modify symbol table. Keep this here
    // for reference.
    [[maybe_unused]] size_t symbol_index = entry.r_info >> 32;

    // Now process the various type of relocation.
    //
    // For documents about AARCH64 relocation, see
    // https://github.com/ARM-software/abi-aa/blob/main/aaelf64/aaelf64.rst#5712dynamic-relocations
    //
    // For document about RISC-V relocation, see
    // https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-elf.adoc#relocations
    //
    // GBL's usage of ELF is restricted. We only implement relocations that we expect to see.
    if (reloc_type == R_RISCV_RELATIVE) {
      // This is the most common type of relocation. For position independent executable,
      // symbol address is typically the relative offset to program start. Relocated absolute
      // address is obtained by adding the address the program is loaded to.
      *addr = program_base + addend;
    } else {
      RELOCATION_PRINTF("Unhandled relocation type: %lx\n", reloc_type);
      return false;
    }
  }

  return true;
}

// Search for RELA type relocation table from the .dynamic section and apply relocation fix up if
// there is one.
bool ApplyRelaRelocation(uintptr_t program_base, const DynamicSectionInfo& info) {
  if ((info.rela == NULL) != (info.rela_size == NULL)) {
    return false;
  } else if (!info.rela) {
    return true;
  }
  return FixUpRelxTable<Elf64_Rela>(program_base, info.symtab, info.rela->d_un.d_ptr + program_base,
                                    info.rela_size->d_un.d_val);
}

// Search for REL type relocation table from the .dynamic section and apply relocation fix up if
// there is one.
bool ApplyRelRelocation(uintptr_t program_base, const DynamicSectionInfo& info) {
  if ((info.rel == NULL) != (info.rel_size == NULL)) {
    return false;
  } else if (!info.rel) {
    return true;
  }
  return FixUpRelxTable<Elf64_Rel>(program_base, info.symtab, info.rel->d_un.d_ptr + program_base,
                                   info.rel_size->d_un.d_val);
}

}  // namespace

extern "C" bool ApplyRelocation(uintptr_t program_base, uintptr_t dynamic_section) {
  DynamicSectionInfo dynamic_section_info = {
      .rel = NULL,
      .rel_size = NULL,
      .rela = NULL,
      .rela_size = NULL,
      .symtab = NULL,
  };

  const Elf64_Dyn* dynamic_section_table = reinterpret_cast<const Elf64_Dyn*>(dynamic_section);
  for (size_t i = 0; dynamic_section_table[i].d_tag != DT_NULL; i++) {
    switch (dynamic_section_table[i].d_tag) {
      // RELA type relocation entries
      case DT_RELA:
        dynamic_section_info.rela = &dynamic_section_table[i];
        break;
      // RELA type relocation entries total size
      case DT_RELASZ:
        dynamic_section_info.rela_size = &dynamic_section_table[i];
        break;
      // REL type relocation entries
      case DT_REL:
        dynamic_section_info.rel = &dynamic_section_table[i];
        break;
      // RELA type relocation entries total size
      case DT_RELSZ:
        dynamic_section_info.rel_size = &dynamic_section_table[i];
        break;
      // Symbol table
      case DT_SYMTAB:
        dynamic_section_info.symtab =
            (const Elf64_Sym*)(dynamic_section_table[i].d_un.d_ptr + program_base);
        break;
      // We shouldn't see RELR relocation unless `-Wl,--pack-dyn-relocs=relr` is used.
      case DT_RELR:
      // We shouldn't see PLT relocation unless we are loading a shared library.
      case DT_JMPREL:
        return false;
      default:
        break;
    }
  }

  if (!dynamic_section_info.symtab) {
    return false;
  }

  return ApplyRelRelocation(program_base, dynamic_section_info) &&
         ApplyRelaRelocation(program_base, dynamic_section_info);
}
