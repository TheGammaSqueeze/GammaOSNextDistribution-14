/*
 * Copyright (C) 2021 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#define START "__dlwrap__start"

#include "crt_arch.h"

#include <elf.h>
#include <link.h>
#include "reloc.h"

#define AUX_CNT 32

#include "ldso_trampoline_phdr.h"

// The location of the embedded linker in the executable.
// Hidden visibility is used to get a pc-relative reference instead
// of a GOT reference, which isn't available when this code runs.
__attribute__((visibility("hidden"))) extern const char __dlwrap_linker;
__attribute__((visibility("hidden"))) extern const char __dlwrap_linker_end;

// The real entry point of the binary to use after linker bootstrapping.
__attribute__((visibility("hidden"))) extern "C" void _start();

// Allocate some R/W memory to store a copy of the program headers.
static ElfW(Phdr) phdr_copy[64];

static size_t get_auxv(size_t* auxv, size_t entry) {
  for (size_t i = 0; auxv[i]; i += 2)
    if (auxv[i] == entry) return auxv[i + 1];
  return 0;
}

static void set_auxv(size_t* auxv, size_t entry, size_t value) {
  for (size_t i = 0; auxv[i]; i += 2) {
    if (auxv[i] == entry) {
      auxv[i + 1] = value;
      return;
    }
  }
  __builtin_trap();
}

/*
 * This is the entry point for the linker wrapper, which finds
 * the real linker, then bootstraps into it.
 */
extern "C" void __dlwrap__start_c(size_t* sp) {
  size_t i, aux[AUX_CNT];

  int argc = *sp;
  char** argv = reinterpret_cast<char**>(sp + 1);

  for (i = argc + 1; argv[i]; i++)
    ;
  size_t* auxv = reinterpret_cast<size_t*>(argv + i + 1);

  for (i = 0; i < AUX_CNT; i++) aux[i] = 0;
  for (i = 0; auxv[i]; i += 2)
    if (auxv[i] < AUX_CNT) aux[auxv[i]] = auxv[i + 1];

  ElfW(Phdr)* phdr = reinterpret_cast<ElfW(Phdr)*>(get_auxv(auxv, AT_PHDR));
  size_t phdr_count = get_auxv(auxv, AT_PHNUM);
  ElfW(Addr) load_bias = get_elf_load_bias_from_phdr(phdr, phdr_count);

  ElfW(Addr) linker_addr = reinterpret_cast<ElfW(Addr)>(&__dlwrap_linker);
  ElfW(Addr) linker_size = static_cast<ElfW(Addr)>(&__dlwrap_linker_end - &__dlwrap_linker);
  ElfW(Addr) linker_vaddr = linker_addr - load_bias;
  ElfW(Addr) linker_entry_offset = reinterpret_cast<ElfW(Ehdr)*>(linker_addr)->e_entry;

  // Make a copy of the ELF program headers that does not contain the load
  // segments for the embedded linker.  The embedded linker contains its
  // own copy of its load segments, which causes problems if musl uses
  // both sets of load segments when donating unused space to the heap.
  if (phdr_count > sizeof(phdr_copy) / sizeof(phdr_copy[0])) __builtin_trap();
  copy_phdr(phdr, phdr_copy, phdr_count, load_bias);
  phdr_trim_embedded_linker(phdr_copy, phdr_count, linker_vaddr, linker_vaddr + linker_size);

  // Set AT_BASE to the embedded linker
  set_auxv(auxv, AT_BASE, linker_addr);
  // Set AT_ENTRY to the proper entry point
  set_auxv(auxv, AT_ENTRY, reinterpret_cast<ElfW(Addr)>(&_start));
  // Set AT_PHDR to the copied program headers
  set_auxv(auxv, AT_PHDR, reinterpret_cast<ElfW(Addr)>(&phdr_copy));

  // Jump to linker entry point
  CRTJMP(linker_addr + linker_entry_offset, sp);
}
