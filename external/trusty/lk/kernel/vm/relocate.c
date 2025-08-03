/*
 * Copyright (c) 2022 Google Inc. All rights reserved
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

#include <kernel/vm.h>
#include <sys/types.h>

void update_relocation_entries(uintptr_t* relr_start, uintptr_t* relr_end,
                               uintptr_t reloc_delta) {
    ASSERT(!(reloc_delta & 1));
    uintptr_t* relr_addr;
    for (relr_addr = relr_start; relr_addr < relr_end; relr_addr++) {
        uintptr_t entry = *relr_addr;
        if (!(entry & 1)) {
            *relr_addr -= reloc_delta;
        }
    }
}

__WEAK void arch_relocate_relative(uintptr_t* ptr, uintptr_t old_base,
                                   uintptr_t new_base) {
    uintptr_t offset = *ptr - old_base;
    *ptr = new_base + offset;
}

void relocate_kernel(uintptr_t* relr_start, uintptr_t* relr_end,
                     uintptr_t old_base, uintptr_t new_base) {
    if (new_base == old_base) {
        return;
    }

    /*
     * The RELR format is a compact encoding for all the R_AARCH64_RELATIVE
     * dynamic relocations that apply to an ELF binary. It consists of an array
     * of 64-bit entry words (sorted by relocation address) with the following
     * semantics:
     * * Even entries (LSB is clear) encode an absolute 64-bit pointer to the
     *   next relocation in the file.
     * * Odd entries (LSB is set) encode a bitmap that specifies which of the
     *   next 63 file words following the last relocation also have relative
     *   relocations. The bits of the bitmap are mapped to file words in little
     *   endian order. Each odd entry covers a consecutive interval of
     *   63 * 8 = 504 bytes in the ELF file.
     *
     * For more details, see the original proposal at
     * https://groups.google.com/g/generic-abi/c/bX460iggiKg
     */
    uintptr_t* relr_addr;
    uintptr_t base = 0;
    for (relr_addr = relr_start; relr_addr < relr_end; relr_addr++) {
        uintptr_t entry = *relr_addr;
        if (!(entry & 1)) {
            arch_relocate_relative((uintptr_t*)entry, old_base, new_base);
            base = entry + sizeof(uintptr_t);
        } else {
            uintptr_t* offset = (uintptr_t*)base;
            while (entry) {
                entry >>= 1;
                if (entry & 1) {
                    arch_relocate_relative(offset, old_base, new_base);
                }
                offset++;
            }

            const size_t length =
                    (8 * sizeof(uintptr_t) - 1) * sizeof(uintptr_t);
            base += length;
        }
    }
}
