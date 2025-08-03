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
 */

OUTPUT_FORMAT("elf64-littleriscv", "elf64-littleriscv", "elf64-littleriscv")
OUTPUT_ARCH(riscv)
SECTIONS
{
  .efi_header 0x0 : {
    /* This is our PE/COFF header. Always keep it. */
    KEEP(*(.efi_header))
  }

  .text : {
    *(.text)
    *(.text.*)
    . = ALIGN(8);
  }

  .interp : { *(.interp) }

  /* Dynamic sections which may involve in relocation */
  .data.rel.ro : { *(.data.rel.ro*) }
  .dynamic  : { *(.dynamic*) }
  .got : { *(.got*) }
  .dynsym  : { *(.dynsym) }
  .dynstr  : { *(.dynstr) }
  .gnu.hash  : { *(.gnu.hash) }

  .eh_frame_hdr : { *(.eh_frame_hdr) }
  .eh_frame : { *(.eh_frame) }

  . = ALIGN(4096);
  .data : {
    *(.rodata*)
    *(.data*)
    *(.sdata)

    /*
    Tranditionally bss section are stuffed into .data section becase
    some PE32+ loader doesn't support a separate bss section.
    */
    . = ALIGN(16);
    *(.sbss)
    *(.scommon)
    *(.dynbss)
    *(.bss)
    *(.bss.*)
    *(COMMON)
  }

  . = ALIGN(4096);
  .note.gnu.build-id : { *(.note.gnu.build-id) }

  /DISCARD/ : {
    *(.debug*)
    *(.comment)
  }

  _end = .;
}
