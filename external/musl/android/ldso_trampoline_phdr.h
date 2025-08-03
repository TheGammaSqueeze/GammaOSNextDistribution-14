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

/* Find the load bias (difference between address and p_vaddr) of an
 * executable or shared object loaded by the kernel. The ELF file's
 * PHDR table must have a PT_PHDR entry.  A VDSO doesn't have a PT_PHDR
 * entry in its PHDR table.
 */
static inline ElfW(Addr)
    get_elf_load_bias_from_phdr(const ElfW(Phdr) * phdr_table, size_t phdr_count) {
  for (size_t i = 0; i < phdr_count; ++i) {
    if (phdr_table[i].p_type == PT_PHDR) {
      return reinterpret_cast<ElfW(Addr)>(phdr_table) - phdr_table[i].p_vaddr;
    }
  }
  return 0;
}

/* Copy the phdr to a new location.  Update the PT_PHDR section to point to the
 * new location.
 */
static inline void copy_phdr(ElfW(Phdr) * phdr_from, ElfW(Phdr) * phdr_to, size_t count,
                             ElfW(Addr) load_bias) {
  ElfW(Phdr)* pt_phdr = nullptr;       // The phdr entry with type PT_PHDR.
  ElfW(Phdr)* phdr_to_phdr = nullptr;  // The phdr entry for the load segment that contains phdr_to.
  ElfW(Phdr)* p = phdr_to;

  // The ELF vaddr of phdr_to.
  ElfW(Addr) phdr_to_vaddr = reinterpret_cast<ElfW(Addr)>(phdr_to) - load_bias;

  for (size_t i = 0; i < count; ++i, ++p) {
    // Assign each member to avoid the struct assignment being turned into a memcpy.
    p->p_type = phdr_from[i].p_type;
    p->p_offset = phdr_from[i].p_offset;
    p->p_vaddr = phdr_from[i].p_vaddr;
    p->p_paddr = phdr_from[i].p_paddr;
    p->p_filesz = phdr_from[i].p_filesz;
    p->p_memsz = phdr_from[i].p_memsz;
    p->p_flags = phdr_from[i].p_flags;
    p->p_align = phdr_from[i].p_align;

    if (p->p_type == PT_PHDR) pt_phdr = p;
    if (p->p_vaddr <= phdr_to_vaddr && p->p_vaddr + p->p_memsz > phdr_to_vaddr) phdr_to_phdr = p;
  }

  if (pt_phdr != nullptr && phdr_to_phdr != nullptr) {
    pt_phdr->p_vaddr = reinterpret_cast<ElfW(Addr)>(phdr_to) - load_bias;
    pt_phdr->p_paddr = pt_phdr->p_vaddr;
    pt_phdr->p_offset = phdr_to_phdr->p_offset + (pt_phdr->p_vaddr - phdr_to_phdr->p_vaddr);
  }
}

/* Trim a section to the given start and end.
 */
static inline void phdr_trim_segment(ElfW(Phdr) * phdr, ElfW(Addr) start, ElfW(Addr) end) {
  const ElfW(Addr) shift = start - phdr->p_vaddr;
  phdr->p_vaddr = start;
  phdr->p_paddr = start;
  phdr->p_memsz = end - start;
  if (shift > 0) {
    phdr->p_offset += shift;
    phdr->p_filesz = (shift > phdr->p_filesz) ? 0 : (phdr->p_filesz - shift);
  }
  if (phdr->p_filesz > end - start) {
    phdr->p_filesz = end - start;
  }
}

/* Trim load sections that overlap with the embedded linker, and replace load sections
 * that are entirely contained within the embedded linker with PT_NULL.
 */
static inline void phdr_trim_embedded_linker(ElfW(Phdr) * phdr, size_t phdr_count,
                                             ElfW(Off) linker_start, ElfW(Off) linker_end) {
  for (size_t i = 0; i < phdr_count; ++i, ++phdr) {
    if (phdr->p_type != PT_LOAD) continue;

    ElfW(Addr) start = phdr->p_vaddr;
    ElfW(Addr) end = phdr->p_vaddr + phdr->p_memsz;

    // A segment that surrounds an embedded linker segment is not supported;
    if (start < linker_start && end > linker_end) __builtin_trap();

    // Handle a segment that overlaps the beginning of the embedded linker;
    if (start < linker_start && end > linker_start) end = linker_start;

    // Handle a segment that overlaps the end of the embedded linker;
    if (start < linker_end && end > linker_end) start = linker_end;

    if (start < end && (start < linker_start || end > linker_end)) {
      // The segment is still needed, trim it.
      phdr_trim_segment(phdr, start, end);
    } else {
      // The segment is not needed, replace it with PT_NULL to avoid having
      // to move the following segments in the phdr.
      phdr->p_type = PT_NULL;
    }
  }
}
