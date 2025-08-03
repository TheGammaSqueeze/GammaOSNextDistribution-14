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

// Refer to https://llvm.org/doxygen/Object_2COFF_8h_source.html and Microsoft Portable Executable
// and Common Object File Format Specification for more detail.

// See https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#machine-types
#define IMAGE_FILE_MACHINE_RISCV64 0x5064

// See https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#characteristics
#define IMAGE_FILE_EXECUTABLE_IMAGE 0x0002

// See https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#optional-header-image-only
#define PE32_PLUS_HEADER_MAGIC 0x020b

// See https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#windows-subsystem
#define IMAGE_SUBSYSTEM_EFI_APPLICATION 10

.macro DATA_DIRECTORY name
\name\()_data_directory:
  .long 0  // support::ulittle32_t RelativeVirtualAddress;
  .long 0  // support::ulittle32_t Size;
.endm

.section .efi_header,"a"
.global dos_header

// MS-DOS Stub
dos_header:
  .short 0x5a4d    // Magic 'MZ'
  .short 0         // support::ulittle16_t UsedBytesInTheLastPage;
  .short 0         // support::ulittle16_t FileSizeInPages;
  .short 0         // support::ulittle16_t NumberOfRelocationItems;
  .short 0         // support::ulittle16_t HeaderSizeInParagraphs;
  .short 0         // support::ulittle16_t MinimumExtraParagraphs;
  .short 0         // support::ulittle16_t MaximumExtraParagraphs;
  .short 0         // support::ulittle16_t InitialRelativeSS;
  .short 0         // support::ulittle16_t InitialSP;
  .short 0         // support::ulittle16_t Checksum;
  .short 0         // support::ulittle16_t InitialIP;
  .short 0         // support::ulittle16_t InitialRelativeCS;
  .short 0         // support::ulittle16_t AddressOfRelocationTable;
  .short 0         // support::ulittle16_t OverlayNumber;
  .skip 2*4        // support::ulittle16_t Reserved[4];
  .short 0         // support::ulittle16_t OEMid;
  .short 0         // support::ulittle16_t OEMinfo;
  .skip 2*10       // support::ulittle16_t Reserved2[10];
  // Even though we fixed `dos_header` at 0x00, we still need to subtract it to
  // indicate this is relative offset. Otherwise linker complains about referencing
  // an absolute address when linking with `-fPIE` (position independent executable)
  .long signature - dos_header  // support::ulittle32_t AddressOfNewExeHeader;

// Signature
signature:
  .long 0x00004550  // "PE\0\0"

// COFF File Header
coff_file_header:
  .short IMAGE_FILE_MACHINE_RISCV64            // support::ulittle16_t Machine;
   // ".reloc" and ".text"
  .short 2                                     // support::ulittle16_t NumberOfSections;
  .long 0                                      // support::ulittle32_t TimeDateStamp;
  .long 0                                      // support::ulittle32_t PointerToSymbolTable;
  .long 0                                      // support::ulittle32_t NumberOfSymbols;
  .short section_table_start - pe32plus_header // support::ulittle16_t SizeOfOptionalHeader;
  .short IMAGE_FILE_EXECUTABLE_IMAGE           // support::ulittle16_t Characteristics;

// PE32+ Optional Header
pe32plus_header:
  .short PE32_PLUS_HEADER_MAGIC           // support::ulittle16_t Magic;
  .byte 0                                 // uint8_t MajorLinkerVersion;
  .byte 0                                 // uint8_t MinorLinkerVersion;
  .long _end - dos_header                 // support::ulittle32_t SizeOfCode;
  .long 0                                 // support::ulittle32_t SizeOfInitializedData;
  .long 0                                 // support::ulittle32_t SizeOfUninitializedData;
  .long _start - dos_header               // support::ulittle32_t AddressOfEntryPoint;
  .long dos_header - dos_header           // support::ulittle32_t BaseOfCode;
  .quad 0                                 // support::ulittle64_t ImageBase;
  .long 512                               // support::ulittle32_t SectionAlignment;
  .long 512                               // support::ulittle32_t FileAlignment;
  .short 0                                // support::ulittle16_t MajorOperatingSystemVersion;
  .short 0                                // support::ulittle16_t MinorOperatingSystemVersion;
  .short 0                                // support::ulittle16_t MajorImageVersion;
  .short 0                                // support::ulittle16_t MinorImageVersion;
  .short 0                                // support::ulittle16_t MajorSubsystemVersion;
  .short 0                                // support::ulittle16_t MinorSubsystemVersion;
  .long 0                                 // support::ulittle32_t Win32VersionValue;
  .long _end - dos_header                 // support::ulittle32_t SizeOfImage;
  .long end_of_header - dos_header        // support::ulittle32_t SizeOfHeaders;
  .long 0                                 // support::ulittle32_t CheckSum;
  .short IMAGE_SUBSYSTEM_EFI_APPLICATION  // support::ulittle16_t Subsystem;
  .short 0                                // support::ulittle16_t DLLCharacteristics;
  .quad 0                                 // support::ulittle64_t SizeOfStackReserve;
  .quad 0                                 // support::ulittle64_t SizeOfStackCommit;
  .quad 0                                 // support::ulittle64_t SizeOfHeapReserve;
  .quad 0                                 // support::ulittle64_t SizeOfHeapCommit;
  .long 0                                 // support::ulittle32_t LoaderFlags;
  // 16 data directories in total
  .long 16                                // support::ulittle32_t NumberOfRvaAndSize;

  // The following are data directories mostly for place holder purpose for now. Entries
  // such as certification_table might be needed for secure boot in the future.
  DATA_DIRECTORY export_table
  DATA_DIRECTORY import_table
  DATA_DIRECTORY resource_table
  DATA_DIRECTORY exception_table
  DATA_DIRECTORY certification_table
  DATA_DIRECTORY base_relocation_table
  DATA_DIRECTORY debug
  DATA_DIRECTORY architecture_data
  DATA_DIRECTORY global_ptr
  DATA_DIRECTORY tls_table
  DATA_DIRECTORY load_config_table
  DATA_DIRECTORY bound_inport
  DATA_DIRECTORY import_address_table
  DATA_DIRECTORY delay_import_descriptor
  DATA_DIRECTORY CLR_runtime_header
  .quad 0

// Section Table
section_table_start:
// .reloc. May be expected by some PE32+ loader
reloc_section:
  .ascii  ".reloc"
  .byte 0
  .byte 0           // 8 bytes of `char Name[COFF::NameSize];`
  .long 0           // support::ulittle32_t VirtualSize;
  .long 0           // support::ulittle32_t VirtualAddress;
  .long 0           // support::ulittle32_t SizeOfRawData;
  .long 0           // support::ulittle32_t PointerToRawData;
  .long 0           // support::ulittle32_t PointerToRelocations;
  .long 0           // support::ulittle32_t PointerToLinenumbers;
  .short 0          // support::ulittle16_t NumberOfRelocations;
  .short 0          // support::ulittle16_t NumberOfLinenumbers;
  // https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#section-flags
  // We use the same value as Clang would generate with "-Wl,/subsystem:efi_application"
  // IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_DISCARDABLE |
  // IMAGE_SCN_MEM_READ
  .long 0x42000040  // support::ulittle32_t Characteristics;

// .text
text_section:
  .ascii  ".text"
  .byte  0
  .byte  0
  .byte  0                          // 8 bytes of `char Name[COFF::NameSize];`
  .long _end - end_of_header        // support::ulittle32_t VirtualSize;
  .long end_of_header - dos_header  // support::ulittle32_t VirtualAddress;
  .long _end - end_of_header        // support::ulittle32_t SizeOfRawData;
  .long end_of_header - dos_header  // support::ulittle32_t PointerToRawData;
  .long 0                           // support::ulittle32_t PointerToRelocations;
  .long 0                           // support::ulittle32_t PointerToLinenumbers;
  .short 0                          // support::ulittle16_t NumberOfRelocations;
  .short 0                          // support::ulittle16_t NumberOfLinenumbers;
  // IMAGE_SCN_CNT_CODE | IMAGE_SCN_CNT_INITIALIZED_DATA |
  // IMAGE_SCN_MEM_EXECUTE | IMAGE_SCN_MEM_READ
  // We use the same value as Clang would generate with "-Wl,/subsystem:efi_application"
  .long 0x60000020                  // support::ulittle32_t Characteristics;

end_of_header:

// Traditionally, PE/COFF header occupies the entire first page. We keep this pattern.
.align 9

_start:
  // Behave like a function call.
  addi sp, sp, -8*3
  sd ra, 0(sp)
  sd t0, 8(sp)
  sd t1, 16(sp)

  // Save the EFI image handle from a0
  mv t0, a0
  // Save the EFI system table from a1
  mv t1, a1

  // Get the program load address (same as dos_header), and .dynamic section address.
  lla a0, dos_header
  lla a1, _DYNAMIC
  // Apply relocation fixup
  call ApplyRelocationHangIfFail

  // Now call efi_main.
  mv a0, t0
  mv a1, t1
  call efi_main

  // Prepare to return.
  ld ra, 0(sp)
  ld t0, 8(sp)
  ld t1, 16(sp)
  add sp, sp, 8*3
  ret
