/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_LIBELFFILE_ELF_ELF_UTILS_H_
#define ART_LIBELFFILE_ELF_ELF_UTILS_H_

#include <elf.h>

#include <sys/cdefs.h>

#include <android-base/logging.h>

namespace art {

struct ElfTypes32 {
  using Addr = Elf32_Addr;
  using Off = Elf32_Off;
  using Half = Elf32_Half;
  using Word = Elf32_Word;
  using Sword = Elf32_Sword;
  using Ehdr = Elf32_Ehdr;
  using Shdr = Elf32_Shdr;
  using Sym = Elf32_Sym;
  using Rel = Elf32_Rel;
  using Rela = Elf32_Rela;
  using Phdr = Elf32_Phdr;
  using Dyn = Elf32_Dyn;
};

struct ElfTypes64 {
  using Addr = Elf64_Addr;
  using Off = Elf64_Off;
  using Half = Elf64_Half;
  using Word = Elf64_Word;
  using Sword = Elf64_Sword;
  using Xword = Elf64_Xword;
  using Sxword = Elf64_Sxword;
  using Ehdr = Elf64_Ehdr;
  using Shdr = Elf64_Shdr;
  using Sym = Elf64_Sym;
  using Rel = Elf64_Rel;
  using Rela = Elf64_Rela;
  using Phdr = Elf64_Phdr;
  using Dyn = Elf64_Dyn;
};

#define ELF_ST_BIND(x) ((x) >> 4)
#define ELF_ST_TYPE(x) ((x) & 0xf)

// Architecture dependent flags for the ELF header.
#define EF_ARM_EABI_VER5 0x05000000

#define EI_ABIVERSION 8
#define EM_ARM 40
#if !defined(STV_DEFAULT)
#define STV_DEFAULT 0
#endif

#define EM_AARCH64 183

#ifndef EM_RISCV
#define EM_RISCV 243
#endif

#ifndef EF_RISCV_RVC
#define EF_RISCV_RVC 0x1
#define EF_RISCV_FLOAT_ABI_DOUBLE 0x4
#endif

#define DT_BIND_NOW 24
#define DT_INIT_ARRAY 25
#define DT_FINI_ARRAY 26
#define DT_INIT_ARRAYSZ 27
#define DT_FINI_ARRAYSZ 28
#define DT_RUNPATH 29
#define DT_FLAGS 30

// Patching section type
#define SHT_OAT_PATCH        SHT_LOUSER

static inline void SetBindingAndType(Elf32_Sym* sym, unsigned char b, unsigned char t) {
  sym->st_info = (b << 4) + (t & 0x0f);
}

static inline bool IsDynamicSectionPointer(Elf32_Word d_tag,
                                           [[maybe_unused]] Elf32_Word e_machine) {
  // TODO: Remove the `e_machine` parameter from API (not needed after Mips target was removed).
  switch (d_tag) {
    // case 1: well known d_tag values that imply Elf32_Dyn.d_un contains an address in d_ptr
    case DT_PLTGOT:
    case DT_HASH:
    case DT_STRTAB:
    case DT_SYMTAB:
    case DT_RELA:
    case DT_INIT:
    case DT_FINI:
    case DT_REL:
    case DT_DEBUG:
    case DT_JMPREL: {
      return true;
    }
    // d_val or ignored values
    case DT_NULL:
    case DT_NEEDED:
    case DT_PLTRELSZ:
    case DT_RELASZ:
    case DT_RELAENT:
    case DT_STRSZ:
    case DT_SYMENT:
    case DT_SONAME:
    case DT_RPATH:
    case DT_SYMBOLIC:
    case DT_RELSZ:
    case DT_RELENT:
    case DT_PLTREL:
    case DT_TEXTREL:
    case DT_BIND_NOW:
    case DT_INIT_ARRAYSZ:
    case DT_FINI_ARRAYSZ:
    case DT_RUNPATH:
    case DT_FLAGS: {
      return false;
    }
    // boundary values that should not be used
    case DT_ENCODING:
    case DT_LOOS:
    case DT_HIOS:
    case DT_LOPROC:
    case DT_HIPROC: {
      LOG(FATAL) << "Illegal d_tag value 0x" << std::hex << d_tag;
      return false;
    }
    default: {
      // case 2: "regular" DT_* ranges where even d_tag values imply an address in d_ptr
      if ((DT_ENCODING  < d_tag && d_tag < DT_LOOS)
          || (DT_LOOS   < d_tag && d_tag < DT_HIOS)
          || (DT_LOPROC < d_tag && d_tag < DT_HIPROC)) {
        if ((d_tag % 2) == 0) {
          return true;
        } else {
          return false;
        }
      } else {
        LOG(FATAL) << "Unknown d_tag value 0x" << std::hex << d_tag;
        return false;
      }
    }
  }
}

}  // namespace art

#endif  // ART_LIBELFFILE_ELF_ELF_UTILS_H_
