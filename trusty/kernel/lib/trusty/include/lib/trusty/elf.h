/*
 * Copyright (c) 2015, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#pragma once

#include <sys/types.h>

/* 32-bit types */
typedef uint32_t Elf32_Addr;
typedef uint32_t Elf32_Off;
typedef uint16_t Elf32_Half;
typedef uint32_t Elf32_Word;
typedef int32_t Elf32_Sword;

/* 64-bit types */
typedef uint64_t Elf64_Addr;
typedef uint64_t Elf64_Off;
typedef uint16_t Elf64_Half;
typedef uint32_t Elf64_Word;
typedef int32_t Elf64_Sword;
typedef uint64_t Elf64_Xword;
typedef int64_t Elf64_Sxword;

/*
 * ELF Headers
 */
#define EI_NIDENT 16

typedef struct {
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

typedef struct {
    unsigned char e_ident[EI_NIDENT];
    Elf64_Half e_type;
    Elf64_Half e_machine;
    Elf64_Word e_version;
    Elf64_Addr e_entry;
    Elf64_Off e_phoff;
    Elf64_Off e_shoff;
    Elf64_Word e_flags;
    Elf64_Half e_ehsize;
    Elf64_Half e_phentsize;
    Elf64_Half e_phnum;
    Elf64_Half e_shentsize;
    Elf64_Half e_shnum;
    Elf64_Half e_shstrndx;
} Elf64_Ehdr;

/* e_ident indexes */
#define EI_MAG0 0       /* File identification (0x7f) */
#define EI_MAG1 1       /* File identification ('E')  */
#define EI_MAG2 2       /* File identification ('L')  */
#define EI_MAG3 3       /* File identification ('F')  */
#define EI_CLASS 4      /* File class */
#define EI_DATA 5       /* Data encoding */
#define EI_VERSION 6    /* File version */
#define EI_OSABI 7      /* Operating system/ABI identification */
#define EI_ABIVERSION 8 /* ABI version */
#define EI_PAD 9        /* Start of padding bytes up to EI_NIDENT*/
#define EI_NIDENT 16    /* First non-ident header byte */

/* e_ident[EI_MAG{0-3}] values */
#define ELFMAG0 0x7f
#define ELFMAG1 'E'
#define ELFMAG2 'L'
#define ELFMAG3 'F'
#define ELFMAG "\177ELF"
#define SELFMAG 4

/* e_ident[EI_CLASS] */
#define ELFCLASSNONE 0 /* Invalid class */
#define ELFCLASS32 1   /* 32-bit */
#define ELFCLASS64 2   /* 64-bit */

/* e_ident[EI_DATA] */
#define ELFDATANONE 0 /* Invalid data encoding */
#define ELFDATA2LSB 1 /* LSB */
#define ELFDATA2MSB 2 /* MSB */

/* e_ident[EI_VERSION] */
#define EV_NONE 0    /* Invalid version */
#define EV_CURRENT 1 /* Current version */

/* e_type */
#define ET_NONE 0        /* No file type */
#define ET_REL 1         /* Relocatable file */
#define ET_EXEC 2        /* Executable file */
#define ET_DYN 3         /* Shared object file */
#define ET_CORE 4        /* Core file */
#define ET_LOOS 0xfe00   /* OS specific range LO */
#define ET_HIOS 0xfeff   /* OS specific range HI */
#define ET_LOPROC 0xff00 /* Processor-specific range LO */
#define ET_HIPROC 0xffff /* Processor-specific range HI */

/* e_machine */
#define EM_ARM 40 /* ARM */

/*
 * Program Header
 */
typedef struct {
    Elf32_Word p_type;
    Elf32_Off p_offset;
    Elf32_Addr p_vaddr;
    Elf32_Addr p_paddr;
    Elf32_Word p_filesz;
    Elf32_Word p_memsz;
    Elf32_Word p_flags;
    Elf32_Word p_align;
} Elf32_Phdr;

typedef struct {
    Elf64_Word p_type;
    Elf64_Word p_flags;
    Elf64_Off p_offset;
    Elf64_Addr p_vaddr;
    Elf64_Addr p_paddr;
    Elf64_Xword p_filesz;
    Elf64_Xword p_memsz;
    Elf64_Xword p_align;
} Elf64_Phdr;

/* p_type */
#define PT_NULL 0            /* entry unused */
#define PT_LOAD 1            /* Loadable segment */
#define PT_DYNAMIC 2         /* Dynamic linking information */
#define PT_INTERP 3          /* Program interpreter */
#define PT_NOTE 4            /* Auxiliary information */
#define PT_SHLIB 5           /* Reserved, unspecified semantics */
#define PT_PHDR 6            /* Entry for header table itself */
#define PT_TLS 7             /* TLS initialisation */
#define PT_LOOS 0x60000000   /* OS-specific range LO */
#define PT_HIOS 0x6fffffff   /* OS-specific range HI */
#define PT_LOPROC 0x70000000 /* Processor-specific range LO */
#define PT_HIPROC 0x7fffffff /* Processor-specific range HI */
#define PT_GNU_PROPERTY (PT_LOOS + 0x474e553)

/* p_flags */
#define PF_R 0x4               /* readable */
#define PF_W 0x2               /* writable */
#define PF_X 0x1               /* executable */
#define PF_MASKOS 0x0ff00000   /* OS specific values mask */
#define PF_MASKPROC 0xf0000000 /* Processor-specific values mask */

/*
 * Section Headers
 */
typedef struct {
    Elf32_Word sh_name;      /* section name (.shstrtab index) */
    Elf32_Word sh_type;      /* section type */
    Elf32_Word sh_flags;     /* section flags */
    Elf32_Addr sh_addr;      /* virtual address */
    Elf32_Off sh_offset;     /* file offset */
    Elf32_Word sh_size;      /* section size */
    Elf32_Word sh_link;      /* link to another */
    Elf32_Word sh_info;      /* misc info */
    Elf32_Word sh_addralign; /* memory alignment */
    Elf32_Word sh_entsize;   /* table entry size */
} Elf32_Shdr;

typedef struct {
    Elf64_Word sh_name;       /* section name (.shstrtab index) */
    Elf64_Word sh_type;       /* section type */
    Elf64_Xword sh_flags;     /* section flags */
    Elf64_Addr sh_addr;       /* virtual address */
    Elf64_Off sh_offset;      /* file offset */
    Elf64_Xword sh_size;      /* section size */
    Elf64_Word sh_link;       /* link to another */
    Elf64_Word sh_info;       /* misc info */
    Elf64_Xword sh_addralign; /* memory alignment */
    Elf64_Xword sh_entsize;   /* table entry size */
} Elf64_Shdr;

/* sh_type */
#define SHT_NULL 0           /* entry unused */
#define SHT_PROGBITS 1       /* Program information */
#define SHT_SYMTAB 2         /* Symbol table */
#define SHT_STRTAB 3         /* String table */
#define SHT_RELA 4           /* Relocation information w/ addend */
#define SHT_HASH 5           /* Symbol hash table */
#define SHT_DYNAMIC 6        /* Dynamic linking information */
#define SHT_NOTE 7           /* Auxiliary information */
#define SHT_NOBITS 8         /* No space allocated in file image */
#define SHT_REL 9            /* Relocation information w/o addend */
#define SHT_SHLIB 10         /* Reserved, unspecified semantics */
#define SHT_DYNSYM 11        /* Symbol table for dynamic linker */
#define SHT_INIT_ARRAY 14    /* Initialization function pointers */
#define SHT_FINI_ARRAY 15    /* Termination function pointers */
#define SHT_PREINIT_ARRAY 16 /* Pre-initialization function ptrs */
#define SHT_GROUP 17         /* Section group */
#define SHT_SYMTAB_SHNDX 18  /* Section indexes (see SHN_XINDEX) */

#define SHT_LOOS 0x60000000 /* OS specific range LO */
#define SHT_HIOS 0x6fffffff /* OS specific range HI */

#define SHT_LOPROC 0x70000000 /* Processor-specific range LO */
#define SHT_HIPROC 0x7fffffff /* Processor-specific range HI */

#define SHT_LOUSER 0x80000000 /* App-specific range LO */
#define SHT_HIUSER 0xffffffff /* App-specific range HI */

/* sh_flags */
#define SHF_WRITE 0x00000001            /* Writable data */
#define SHF_ALLOC 0x00000002            /* Occupies memory */
#define SHF_EXECINSTR 0x00000004        /* Executable */
#define SHF_MERGE 0x00000010            /* Might be merged */
#define SHF_STRINGS 0x00000020          /* Contains nul terminated strings */
#define SHF_INFO_LINK 0x00000040        /* "sh_info" contains SHT index */
#define SHF_LINK_ORDER 0x00000080       /* Preserve order after combining */
#define SHF_OS_NONCONFORMING 0x00000100 /* OS specific handling required */
#define SHF_GROUP 0x00000200            /* Is member of a group */
#define SHF_TLS 0x00000400              /* Holds thread-local data */
#define SHF_MASKOS 0x0ff00000           /* OS specific values */
#define SHF_MASKPROC 0xf0000000         /* Processor-specific values */

/*
 * Note header
 */
typedef struct {
    Elf32_Word n_namesz;
    Elf32_Word n_descsz;
    Elf32_Word n_type;
} Elf32_Nhdr;

typedef struct {
    Elf64_Word n_namesz;
    Elf64_Word n_descsz;
    Elf64_Word n_type;
} Elf64_Nhdr;

/* n_type values */
#define NT_GNU_PROPERTY_TYPE_0 5 /* .note.gnu.property */

/*
 * GNU Property (.note.gnu.property)
 */
typedef struct {
    Elf32_Word pr_type;
    Elf32_Word pr_datasz;
    Elf32_Word pr_data[];
} ELF_GnuProp;

/* .note.gnu.property types */
#define GNU_PROPERTY_AARCH64_FEATURE_1_AND 0xc0000000

/* Bits for GNU_PROPERTY_AARCH64_FEATURE_1_AND */
#define GNU_PROPERTY_AARCH64_FEATURE_1_BTI (1u << 0)
#define GNU_PROPERTY_AARCH64_FEATURE_1_PAC (1u << 1)
