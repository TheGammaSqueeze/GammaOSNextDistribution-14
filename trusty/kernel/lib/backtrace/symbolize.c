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

#include <lib/backtrace/symbolize.h>
#include <trace.h>

#include "elf_sym.h"

#define LOCAL_TRACE 0

#undef ELF_64BIT
#if !IS_64BIT || USER_32BIT
#define ELF_64BIT 0
#else
#define ELF_64BIT 1
#endif

#if ELF_64BIT
#define ELF_SHDR Elf64_Shdr
#define ELF_EHDR Elf64_Ehdr
#define ELF_SYM Elf64_Sym
#else
#define ELF_SHDR Elf32_Shdr
#define ELF_EHDR Elf32_Ehdr
#define ELF_SYM Elf32_Sym
#endif

static inline bool range_within_app_img(uintptr_t start,
                                        size_t size,
                                        struct trusty_app_img* app_img) {
    uintptr_t end;
    if (__builtin_add_overflow(start, size, &end)) {
        return false;
    }
    return app_img->img_start <= start && end <= app_img->img_end;
}

static inline bool range_within_range(uintptr_t start0,
                                      size_t size0,
                                      uintptr_t start1,
                                      size_t size1) {
    uintptr_t end0;
    if (__builtin_add_overflow(start0, size0, &end0)) {
        return false;
    }
    uintptr_t end1;
    if (__builtin_add_overflow(start1, size1, &end1)) {
        return false;
    }
    return start1 <= start0 && end0 <= end1;
}

int trusty_app_symbolize(struct trusty_app* app,
                         uintptr_t pc,
                         struct pc_symbol_info* info) {
    if (!app) {
        goto out_no_symbol;
    }
    /* Adjust pc to be relative to app image */
    if (__builtin_sub_overflow(pc, app->load_bias, &pc)) {
        goto out_no_symbol;
    }
    /* pc must be within the app image */
    struct trusty_app_img* app_img = &app->app_img;
    if (app_img->img_end <= app_img->img_start) {
        goto out_no_symbol;
    }
    if (pc > app_img->img_end - app_img->img_start) {
        goto out_no_symbol;
    }

    ELF_EHDR* ehdr = (ELF_EHDR*)app_img->img_start;
    ELF_SHDR* shdr = (ELF_SHDR*)((uintptr_t)ehdr + ehdr->e_shoff);

    ELF_SHDR* symtab_shdr = NULL;
    ELF_SHDR* strtab_shdr = NULL;

    /* Find section headers for .symtab and .strtab */
    for (size_t i = 0; i < ehdr->e_shnum; i++) {
        if (shdr[i].sh_type == SHT_SYMTAB) {
            symtab_shdr = shdr + i;
        }
        if (shdr[i].sh_type == SHT_STRTAB) {
            strtab_shdr = shdr + i;
        }
    }

    /* Handle the case when app is not built with .symtab or .strtab */
    if (!symtab_shdr || !strtab_shdr) {
        LTRACEF("App built without symbol table\n");
        goto out_no_symbol;
    }

    uintptr_t symtab_start = app_img->img_start + symtab_shdr->sh_offset;
    size_t symtab_size = symtab_shdr->sh_size;
    uintptr_t strtab_start = app_img->img_start + strtab_shdr->sh_offset;
    size_t strtab_size = strtab_shdr->sh_size;

    /* Validate .symtab and .strtab locations */
    if (!range_within_app_img(symtab_start, symtab_size, app_img)) {
        TRACEF(".symtab section is not within the app image\n");
        goto out_no_symbol;
    }
    if (!range_within_app_img(strtab_start, strtab_size, app_img)) {
        TRACEF(".strtab section is not within the app image\n");
        goto out_no_symbol;
    }

    /* Find closest symbol preceding pc */
    info->offset = ULONG_MAX;
    for (uintptr_t curr = symtab_start;
         curr < symtab_start + symtab_shdr->sh_size;
         curr += symtab_shdr->sh_entsize) {
        /* Entry must be within .symtab section */
        if (!range_within_range(curr, symtab_shdr->sh_entsize, symtab_start,
                                symtab_size)) {
            TRACEF(".symtab section is malformed\n");
            goto out_no_symbol;
        }

        ELF_SYM* symtab_entry = (ELF_SYM*)curr;
        /* We are looking for a symbol of a function */
        if (ELF_ST_TYPE(symtab_entry->st_info) != STT_FUNC) {
            continue;
        }

        uintptr_t func_start = symtab_entry->st_value;
        if (func_start <= pc && info->offset > pc - func_start) {
            /* Offset must be within .strtab section */
            if (symtab_entry->st_name >= strtab_size) {
                TRACEF(".strtab section is malformed\n");
                goto out_no_symbol;
            }

            info->symbol = (const char*)(strtab_start + symtab_entry->st_name);
            info->offset = pc - func_start;
            info->size = symtab_entry->st_size;
        }
    }

    if (info->offset == ULONG_MAX) {
        goto out_no_symbol;
    }
    return NO_ERROR;

out_no_symbol:
    info->symbol = NULL;
    info->offset = 0;
    info->size = 0;
    return ERR_NOT_FOUND;
}
