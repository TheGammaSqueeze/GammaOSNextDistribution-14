/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_SHARED_LOADER_UTIL_H_
#define CHRE_PLATFORM_SHARED_LOADER_UTIL_H_

// Macros used to define a symbol that can be exported by the nanoapp loader
#define ADD_EXPORTED_SYMBOL(function_name, function_string) \
  { reinterpret_cast<void *>(function_name), function_string }
#define ADD_EXPORTED_C_SYMBOL(function_name) \
  ADD_EXPORTED_SYMBOL(function_name, STRINGIFY(function_name))

// The below macros allow switching the ELF symbol type between 32/64-bit
// depending on what the chipset supports.
#ifndef ELFW
#ifndef __WORDSIZE
// Until we can get a hold of wordsize.h, we need to define it here.
// Only 32-bit architectures currently supported.
#ifdef CHRE_32_BIT_WORD_SIZE
#define __WORDSIZE 32
#else
#error "Architecture not supported by CHRE dynamic loading"
#endif
#endif
// https://refspecs.linuxbase.org/elf/gabi4+/ch4.reloc.html
#define ELF32_R_SYM(info) ((info) >> 8)
#define ELF32_R_TYPE(info) ((unsigned char)(info))
#define ELF32_R_INFO(sym, type) (((sym) << 8) + (unsigned char)(type))
#define __ELF_NATIVE_CLASS __WORDSIZE
#define ELFW(type) _ELFW(__ELF_NATIVE_CLASS, type)
#define _ELFW(bits, type) __ELFW(bits, type)
#define __ELFW(bits, type) ELF##bits##_##type
#endif
#define ELFW_R_TYPE(x) ELFW(R_TYPE)(x)
#define ELFW_R_SYM(x) ELFW(R_SYM)(x)

struct ExportedData {
  void *data;
  const char *dataName;
};

// The below is copied from bionic/libc/kernel/uapi/linux/elf.h
// to avoid pulling those deps into the build.
#if defined(__LP64__)
#define ElfW(type) Elf64_##type
#else
#define ElfW(type) Elf32_##type
#endif

#define EM_ARM 40
#define EM_RISCV 243
#define EI_MAG0 0
#define EI_MAG1 1
#define EI_MAG2 2
#define EI_MAG3 3
#define EI_CLASS 4
#define EI_DATA 5
#define EI_VERSION 6
#define EI_OSABI 7
#define EI_PAD 8
#define ELFMAG0 0x7f
#define ELFMAG1 'E'
#define ELFMAG2 'L'
#define ELFMAG3 'F'
#define ELFMAG "\177ELF"
#define SELFMAG 4
#define ELFCLASSNONE 0
#define ELFCLASS32 1
#define ELFCLASS64 2
#define ELFCLASSNUM 3
#define ELFDATANONE 0
#define ELFDATA2LSB 1
#define ELFDATA2MSB 2
#define EV_NONE 0
#define EV_CURRENT 1
#define EV_NUM 2
#define ELFOSABI_NONE 0
#define ELFOSABI_LINUX 3
#define PT_NULL 0
#define PT_LOAD 1
#define PT_DYNAMIC 2
#define PT_INTERP 3
#define PT_NOTE 4
#define PT_SHLIB 5
#define PT_PHDR 6
#define PT_TLS 7
#define PT_LOOS 0x60000000
#define PT_HIOS 0x6fffffff
#define PT_LOPROC 0x70000000
#define PT_HIPROC 0x7fffffff
#define PT_GNU_EH_FRAME 0x6474e550
#define PT_GNU_STACK (PT_LOOS + 0x474e551)
#define PN_XNUM 0xffff
#define ET_NONE 0
#define ET_REL 1
#define ET_EXEC 2
#define ET_DYN 3
#define ET_CORE 4
#define ET_LOPROC 0xff00
#define ET_HIPROC 0xffff
#define DT_NULL 0
#define DT_NEEDED 1
#define DT_PLTRELSZ 2
#define DT_PLTGOT 3
#define DT_HASH 4
#define DT_STRTAB 5
#define DT_SYMTAB 6
#define DT_RELA 7
#define DT_RELASZ 8
#define DT_RELAENT 9
#define DT_STRSZ 10
#define DT_SYMENT 11
#define DT_INIT 12
#define DT_FINI 13
#define DT_SONAME 14
#define DT_RPATH 15
#define DT_SYMBOLIC 16
#define DT_REL 17
#define DT_RELSZ 18
#define DT_RELENT 19
#define DT_PLTREL 20
#define DT_DEBUG 21
#define DT_TEXTREL 22
#define DT_JMPREL 23
#define DT_ENCODING 32

typedef __signed__ char __s8;
typedef unsigned char __u8;
typedef __signed__ short __s16;
typedef unsigned short __u16;
typedef __signed__ int __s32;
typedef unsigned int __u32;
typedef __signed__ long __s64;
typedef unsigned long __u64;

typedef __u32 Elf32_Addr;
typedef __u16 Elf32_Half;
typedef __u32 Elf32_Off;
typedef __s32 Elf32_Sword;
typedef __u32 Elf32_Word;

#define EI_NIDENT 16
typedef struct elf32_hdr {
  unsigned char e_ident[EI_NIDENT];
  Elf32_Half e_type;
  Elf32_Half e_machine;
  Elf32_Word e_version;
  Elf32_Addr e_entry;
  Elf32_Off e_phoff;
  Elf32_Off e_shoff;
  Elf32_Word e_flags;
  Elf32_Half e_ehsize;
  Elf32_Half e_phentsize;
  Elf32_Half e_phnum;
  Elf32_Half e_shentsize;
  Elf32_Half e_shnum;
  Elf32_Half e_shstrndx;
} Elf32_Ehdr;

typedef struct dynamic {
  Elf32_Sword d_tag;
  union {
    Elf32_Sword d_val;
    Elf32_Addr d_ptr;
  } d_un;
} Elf32_Dyn;

typedef struct elf32_phdr {
  Elf32_Word p_type;
  Elf32_Off p_offset;
  Elf32_Addr p_vaddr;
  Elf32_Addr p_paddr;
  Elf32_Word p_filesz;
  Elf32_Word p_memsz;
  Elf32_Word p_flags;
  Elf32_Word p_align;
} Elf32_Phdr;

typedef struct elf32_shdr {
  Elf32_Word sh_name;
  Elf32_Word sh_type;
  Elf32_Word sh_flags;
  Elf32_Addr sh_addr;
  Elf32_Off sh_offset;
  Elf32_Word sh_size;
  Elf32_Word sh_link;
  Elf32_Word sh_info;
  Elf32_Word sh_addralign;
  Elf32_Word sh_entsize;
} Elf32_Shdr;

typedef struct elf32_rela {
  Elf32_Addr r_offset;
  Elf32_Word r_info;
  Elf32_Sword r_addend;
} Elf32_Rela;

typedef struct elf32_rel {
  Elf32_Addr r_offset;
  Elf32_Word r_info;
} Elf32_Rel;

typedef struct elf32_sym {
  Elf32_Word st_name;
  Elf32_Addr st_value;
  Elf32_Word st_size;
  unsigned char st_info;
  unsigned char st_other;
  Elf32_Half st_shndx;
} Elf32_Sym;

// The following defines are copied from bionic's elf_arm.h header
// at bionic/libc/kernel/uapi/linux/elf.h
// Only the relocation types currently supported are copied.
#define R_ARM_NONE 0
#define R_ARM_ABS32 2
#define R_ARM_COPY 20
#define R_ARM_GLOB_DAT 21
#define R_ARM_JUMP_SLOT 22
#define R_ARM_RELATIVE 23
#define R_RISCV_NONE 0
#define R_RISCV_32 1
#define R_RISCV_RELATIVE 3
#define R_RISCV_JUMP_SLOT 5
// Undefined symbol.
#define SHN_UNDEF 0

// The following (legal values for segment flags) are copied from
// bionic's elf.h
/* http://www.sco.com/developers/gabi/latest/ch5.pheader.html */
#define PF_X 0x1
#define PF_W 0x2
#define PF_R 0x4
#define PF_MASKOS 0x0ff00000
#define PF_MASKPROC 0xf0000000

#endif  // CHRE_PLATFORM_SHARED_LOADER_UTIL_H_
