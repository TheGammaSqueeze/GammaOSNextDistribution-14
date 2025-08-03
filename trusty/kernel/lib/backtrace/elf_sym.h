/*
 * Copyright (c) 2020 Google Inc. All rights reserved
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

#include <lib/trusty/elf.h>

/*
 * Symbol table entry
 */
typedef struct {
    Elf32_Word st_name;     /* symbol name (.strtab index) */
    Elf32_Addr st_value;    /* symbol value */
    Elf32_Word st_size;     /* symbol size */
    unsigned char st_info;  /* symbol type and binding */
    unsigned char st_other; /* symbol visibility */
    Elf32_Half st_shndx;    /* section index */
} Elf32_Sym;

typedef struct {
    Elf64_Word st_name;     /* symbol name (.strtab index) */
    unsigned char st_info;  /* symbol type and binding */
    unsigned char st_other; /* symbol visibility */
    Elf64_Half st_shndx;    /* section index */
    Elf64_Addr st_value;    /* symbol value */
    Elf64_Xword st_size;    /* symbol size */
} Elf64_Sym;

/* Symbol bindings */
#define STB_LOCAL  0
#define STB_GLOBAL 1
#define STB_WEAK   2

/* Symbol types */
#define STT_NOTYPE  0
#define STT_OBJECT  1
#define STT_FUNC    2
#define STT_SECTION 3
#define STT_FILE    4
#define STT_COMMON  5
#define STT_TLS     6

#define ELF_ST_BIND(x) ((x) >> 4)
#define ELF_ST_TYPE(x) (((unsigned int)x) & 0xf)
