/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "berberis/tiny_loader/tiny_loader.h"

#include <elf.h>
#include <fcntl.h>
#include <inttypes.h>
#include <sys/param.h>
#include <sys/stat.h>
#include <sys/user.h>
#include <unistd.h>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/base/mapped_file_fragment.h"
#include "berberis/base/page_size.h"
#include "berberis/base/prctl_helpers.h"
#include "berberis/base/stringprintf.h"

#define MAYBE_MAP_FLAG(x, from, to) (((x) & (from)) ? (to) : 0)
#define PFLAGS_TO_PROT(x)                                                        \
  (MAYBE_MAP_FLAG((x), PF_X, PROT_EXEC) | MAYBE_MAP_FLAG((x), PF_R, PROT_READ) | \
   MAYBE_MAP_FLAG((x), PF_W, PROT_WRITE))

namespace {

void set_error_msg(std::string* error_msg, const char* format, ...) {
  if (error_msg == nullptr) {
    return;
  }

  va_list ap;
  va_start(ap, format);
  berberis::StringAppendV(error_msg, format, ap);
  va_end(ap);
}

template <typename T>
constexpr T page_align_down(T addr) {
  return berberis::AlignDown(addr, berberis::kPageSize);
}

template <typename T>
constexpr T page_align_up(T addr) {
  return berberis::AlignUp(addr, berberis::kPageSize);
}

template <typename T>
constexpr T page_offset(T addr) {
  return addr - page_align_down(addr);
}

const char* EiClassString(int elf_class) {
  switch (elf_class) {
    case ELFCLASSNONE:
      return "ELFCLASSNONE";
    case ELFCLASS32:
      return "ELFCLASS32";
    case ELFCLASS64:
      return "ELFCLASS64";
    default:
      return "(unknown)";
  }
}

// Returns the size of the extent of all the possibly non-contiguous
// loadable segments in an ELF program header table. This corresponds
// to the page-aligned size in bytes that needs to be reserved in the
// process' address space. If there are no loadable segments, 0 is
// returned.
//
// If out_min_vaddr or out_max_vaddr are not null, they will be
// set to the minimum and maximum addresses of pages to be reserved,
// or 0 if there is nothing to load.
size_t phdr_table_get_load_size(const ElfPhdr* phdr_table, size_t phdr_count,
                                ElfAddr* out_min_vaddr) {
  ElfAddr min_vaddr = UINTPTR_MAX;
  ElfAddr max_vaddr = 0;

  bool found_pt_load = false;
  for (size_t i = 0; i < phdr_count; ++i) {
    const ElfPhdr* phdr = &phdr_table[i];

    if (phdr->p_type != PT_LOAD) {
      continue;
    }
    found_pt_load = true;

    if (phdr->p_vaddr < min_vaddr) {
      min_vaddr = phdr->p_vaddr;
    }

    if (phdr->p_vaddr + phdr->p_memsz > max_vaddr) {
      max_vaddr = phdr->p_vaddr + phdr->p_memsz;
    }
  }
  if (!found_pt_load) {
    min_vaddr = 0;
  }

  min_vaddr = page_align_down(min_vaddr);
  max_vaddr = page_align_up(max_vaddr);

  if (out_min_vaddr != nullptr) {
    *out_min_vaddr = min_vaddr;
  }
  return max_vaddr - min_vaddr;
}

class TinyElfLoader {
 public:
  explicit TinyElfLoader(const char* name);

  bool LoadFromFile(int fd, off64_t file_size, size_t align, TinyLoader::mmap64_fn_t mmap64_fn,
                    TinyLoader::munmap_fn_t munmap_fn, LoadedElfFile* loaded_elf_file);

  bool LoadFromMemory(void* load_addr, size_t load_size, LoadedElfFile* loaded_elf_file);

  const std::string& error_msg() const { return error_msg_; }

 private:
  bool CheckElfHeader(const ElfEhdr* header);
  bool ReadElfHeader(int fd, ElfEhdr* header);
  bool ReadProgramHeadersFromFile(const ElfEhdr* header, int fd, off64_t file_size,
                                  const ElfPhdr** phdr_table, size_t* phdr_num);

  bool ReadProgramHeadersFromMemory(const ElfEhdr* header, uintptr_t load_addr, size_t load_size,
                                    const ElfPhdr** phdr_table, size_t* phdr_num);

  bool ReserveAddressSpace(ElfHalf e_type, const ElfPhdr* phdr_table, size_t phdr_num, size_t align,
                           TinyLoader::mmap64_fn_t mmap64_fn, TinyLoader::munmap_fn_t munmap_fn,
                           void** load_start, size_t* load_size, uintptr_t* load_bias);

  bool LoadSegments(int fd, size_t file_size, ElfHalf e_type, const ElfPhdr* phdr_table,
                    size_t phdr_num, size_t align, TinyLoader::mmap64_fn_t mmap64_fn,
                    TinyLoader::munmap_fn_t munmap_fn, void** load_start, size_t* load_size);

  bool FindDynamicSegment(const ElfEhdr* header);
  bool InitializeFields(const ElfEhdr* header);

  bool Parse(void* load_ptr, size_t load_size, LoadedElfFile* loaded_elf_file);

  static bool CheckFileRange(off64_t file_size, ElfAddr offset, size_t size, size_t alignment);
  static bool CheckMemoryRange(uintptr_t load_addr, size_t load_size, ElfAddr offset, size_t size,
                               size_t alignment);
  uint8_t* Reserve(void* hint, size_t size, TinyLoader::mmap64_fn_t mmap64_fn);

  bool did_load_;

  const char* name_;

  MappedFileFragment phdr_fragment_;

  // Loaded phdr
  const ElfPhdr* loaded_phdr_;
  size_t loaded_phdr_num_;

  ElfAddr load_bias_;

  void* entry_point_;

  // Loaded dynamic section
  const ElfDyn* dynamic_;

  // Fields needed for symbol lookup
  bool has_gnu_hash_;
  size_t gnu_nbucket_;
  uint32_t* gnu_bucket_;
  uint32_t* gnu_chain_;
  uint32_t gnu_maskwords_;
  uint32_t gnu_shift2_;
  ElfAddr* gnu_bloom_filter_;

  uint32_t sysv_nbucket_;
  uint32_t sysv_nchain_;
  uint32_t* sysv_bucket_;
  uint32_t* sysv_chain_;

  ElfSym* symtab_;

  const char* strtab_;
  size_t strtab_size_;

  std::string error_msg_;
};

TinyElfLoader::TinyElfLoader(const char* name)
    : did_load_(false),
      name_(name),
      loaded_phdr_(nullptr),
      loaded_phdr_num_(0),
      load_bias_(0),
      entry_point_(nullptr),
      dynamic_(nullptr),
      has_gnu_hash_(false),
      gnu_nbucket_(0),
      gnu_bucket_(nullptr),
      gnu_chain_(nullptr),
      gnu_maskwords_(0),
      gnu_shift2_(0),
      gnu_bloom_filter_(nullptr),
      sysv_nbucket_(0),
      sysv_nchain_(0),
      sysv_bucket_(nullptr),
      sysv_chain_(nullptr),
      symtab_(nullptr),
      strtab_(nullptr),
      strtab_size_(0) {}

bool TinyElfLoader::CheckElfHeader(const ElfEhdr* header) {
  if (memcmp(header->e_ident, ELFMAG, SELFMAG) != 0) {
    set_error_msg(&error_msg_, "\"%s\" has bad ELF magic", name_);
    return false;
  }

  int elf_class = header->e_ident[EI_CLASS];
  if (elf_class != kSupportedElfClass) {
    set_error_msg(&error_msg_, "\"%s\" %s is not supported, expected %s.", name_,
                  EiClassString(elf_class), EiClassString(kSupportedElfClass));
    return false;
  }

  if (header->e_ident[EI_DATA] != ELFDATA2LSB) {
    set_error_msg(&error_msg_, "\"%s\" not little-endian: %d", name_, header->e_ident[EI_DATA]);
    return false;
  }

  if (header->e_version != EV_CURRENT) {
    set_error_msg(&error_msg_, "\"%s\" has unexpected e_version: %d", name_, header->e_version);
    return false;
  }

  if (header->e_shentsize != sizeof(ElfShdr)) {
    set_error_msg(&error_msg_, "\"%s\" has unsupported e_shentsize: 0x%x (expected 0x%zx)", name_,
                  header->e_shentsize, sizeof(ElfShdr));
    return false;
  }

  if (header->e_shstrndx == 0) {
    set_error_msg(&error_msg_, "\"%s\" has invalid e_shstrndx", name_);
    return false;
  }

  // Like the kernel, we only accept program header tables that
  // are smaller than 64KiB.
  if (header->e_phnum < 1 || header->e_phnum > 65536 / sizeof(ElfPhdr)) {
    set_error_msg(&error_msg_, "\"%s\" has invalid e_phnum: %zd", name_, header->e_phnum);
    return false;
  }

  return true;
}

bool TinyElfLoader::ReadElfHeader(int fd, ElfEhdr* header) {
  ssize_t rc = TEMP_FAILURE_RETRY(pread64(fd, header, sizeof(*header), 0));
  if (rc < 0) {
    set_error_msg(&error_msg_, "can't read file \"%s\": %s", name_, strerror(errno));
    return false;
  }

  if (rc != sizeof(*header)) {
    set_error_msg(&error_msg_, "\"%s\" is too small to be an ELF executable: only found %zd bytes",
                  name_, static_cast<size_t>(rc));
    return false;
  }

  return CheckElfHeader(header);
}

bool TinyElfLoader::CheckFileRange(off64_t file_size, ElfAddr offset, size_t size,
                                   size_t alignment) {
  off64_t range_start = offset;
  off64_t range_end;

  return offset > 0 && !__builtin_add_overflow(range_start, size, &range_end) &&
         (range_start < file_size) && (range_end <= file_size) && ((offset % alignment) == 0);
}

bool TinyElfLoader::CheckMemoryRange(uintptr_t load_addr, size_t load_size, ElfAddr offset,
                                     size_t size, size_t alignment) {
  uintptr_t dummy;
  uintptr_t offset_end;

  return offset < load_size && !__builtin_add_overflow(load_addr, load_size, &dummy) &&
         !__builtin_add_overflow(offset, size, &offset_end) && offset_end <= load_size &&
         ((offset % alignment) == 0);
}

bool TinyElfLoader::ReadProgramHeadersFromFile(const ElfEhdr* header, int fd, off64_t file_size,
                                               const ElfPhdr** phdr_table, size_t* phdr_num) {
  size_t phnum = header->e_phnum;
  size_t size = phnum * sizeof(ElfPhdr);

  if (!CheckFileRange(file_size, header->e_phoff, size, alignof(ElfPhdr))) {
    set_error_msg(&error_msg_, "\"%s\" has invalid phdr offset/size: %zu/%zu", name_,
                  static_cast<size_t>(header->e_phoff), size);
    return false;
  }

  if (!phdr_fragment_.Map(fd, 0, header->e_phoff, size)) {
    set_error_msg(&error_msg_, "\"%s\" phdr mmap failed: %s", name_, strerror(errno));
    return false;
  }

  *phdr_table = static_cast<ElfPhdr*>(phdr_fragment_.data());
  *phdr_num = phnum;
  return true;
}

bool TinyElfLoader::ReadProgramHeadersFromMemory(const ElfEhdr* header, uintptr_t load_addr,
                                                 size_t load_size, const ElfPhdr** phdr_table,
                                                 size_t* phdr_num) {
  size_t phnum = header->e_phnum;
  size_t size = phnum * sizeof(ElfPhdr);

  if (!CheckMemoryRange(load_addr, load_size, header->e_phoff, size, alignof(ElfPhdr))) {
    set_error_msg(&error_msg_, "\"%s\" has invalid phdr offset/size: %zu/%zu", name_,
                  static_cast<size_t>(header->e_phoff), size);
    return false;
  }

  *phdr_table = reinterpret_cast<const ElfPhdr*>(load_addr + header->e_phoff);
  *phdr_num = phnum;
  return true;
}

uint8_t* TinyElfLoader::Reserve(void* hint, size_t size, TinyLoader::mmap64_fn_t mmap64_fn) {
  int mmap_flags = MAP_PRIVATE | MAP_ANONYMOUS;

  void* mmap_ptr = mmap64_fn(hint, size, PROT_NONE, mmap_flags, -1, 0);
  if (mmap_ptr == MAP_FAILED) {
    return nullptr;
  }

  return reinterpret_cast<uint8_t*>(mmap_ptr);
}

bool TinyElfLoader::ReserveAddressSpace(ElfHalf e_type, const ElfPhdr* phdr_table, size_t phdr_num,
                                        size_t align, TinyLoader::mmap64_fn_t mmap64_fn,
                                        TinyLoader::munmap_fn_t munmap_fn, void** load_start,
                                        size_t* load_size, uintptr_t* load_bias) {
  ElfAddr min_vaddr;
  size_t size = phdr_table_get_load_size(phdr_table, phdr_num, &min_vaddr);
  if (size == 0) {
    set_error_msg(&error_msg_, "\"%s\" has no loadable segments", name_);
    return false;
  }

  uint8_t* addr = reinterpret_cast<uint8_t*>(min_vaddr);
  uint8_t* start;

  if (e_type == ET_EXEC) {
    // Reserve with hint.
    start = Reserve(addr, size, mmap64_fn);
    if (start != addr) {
      if (start != nullptr) {
        munmap_fn(start, size);
      }
      set_error_msg(&error_msg_, "couldn't reserve %zd bytes of address space at %p for \"%s\"",
                    size, addr, name_);

      return false;
    }
  } else if (align <= berberis::kPageSize) {
    // Reserve.
    start = Reserve(nullptr, size, mmap64_fn);
    if (start == nullptr) {
      set_error_msg(&error_msg_, "couldn't reserve %zd bytes of address space for \"%s\"", size,
                    name_);
      return false;
    }
  } else {
    // Reserve overaligned.
    CHECK(berberis::IsPowerOf2(align));
    uint8_t* unaligned_start = Reserve(nullptr, align + size, mmap64_fn);
    if (unaligned_start == nullptr) {
      set_error_msg(&error_msg_,
                    "couldn't reserve %zd bytes of address space aligned on %zd for \"%s\"", size,
                    align, name_);
      return false;
    }
    start = berberis::AlignUp(unaligned_start, align);
    munmap_fn(unaligned_start, start - unaligned_start);
    munmap_fn(start + size, unaligned_start + align - start);
  }

  *load_start = start;
  *load_size = size;
  *load_bias = start - addr;
  return true;
}

bool TinyElfLoader::LoadSegments(int fd, size_t file_size, ElfHalf e_type,
                                 const ElfPhdr* phdr_table, size_t phdr_num, size_t align,
                                 TinyLoader::mmap64_fn_t mmap64_fn,
                                 TinyLoader::munmap_fn_t munmap_fn, void** load_start,
                                 size_t* load_size) {
  uintptr_t load_bias = 0;
  if (!ReserveAddressSpace(e_type, phdr_table, phdr_num, align, mmap64_fn, munmap_fn, load_start,
                           load_size, &load_bias)) {
    return false;
  }

  for (size_t i = 0; i < phdr_num; ++i) {
    const ElfPhdr* phdr = &phdr_table[i];

    if (phdr->p_type != PT_LOAD) {
      continue;
    }

    // Segment addresses in memory.
    ElfAddr seg_start = phdr->p_vaddr + load_bias;
    ElfAddr seg_end = seg_start + phdr->p_memsz;

    ElfAddr seg_page_start = page_align_down(seg_start);
    ElfAddr seg_page_end = page_align_up(seg_end);

    ElfAddr seg_file_end = seg_start + phdr->p_filesz;

    // File offsets.
    ElfAddr file_start = phdr->p_offset;
    ElfAddr file_end = file_start + phdr->p_filesz;

    ElfAddr file_page_start = page_align_down(file_start);
    ElfAddr file_length = file_end - file_page_start;

    if (file_size <= 0) {
      set_error_msg(&error_msg_, "\"%s\" invalid file size: %" PRId64, name_, file_size);
      return false;
    }

    if (file_end > static_cast<size_t>(file_size)) {
      set_error_msg(&error_msg_,
                    "invalid ELF file \"%s\" load segment[%zd]:"
                    " p_offset (%p) + p_filesz (%p) ( = %p) past end of file (0x%" PRIx64 ")",
                    name_, i, reinterpret_cast<void*>(phdr->p_offset),
                    reinterpret_cast<void*>(phdr->p_filesz), reinterpret_cast<void*>(file_end),
                    file_size);
      return false;
    }

    if (file_length != 0) {
      int prot = PFLAGS_TO_PROT(phdr->p_flags);
      if ((prot & (PROT_EXEC | PROT_WRITE)) == (PROT_EXEC | PROT_WRITE)) {
        set_error_msg(&error_msg_, "\"%s\": W + E load segments are not allowed", name_);
        return false;
      }

      void* seg_addr = mmap64_fn(reinterpret_cast<void*>(seg_page_start), file_length, prot,
                                 MAP_FIXED | MAP_PRIVATE, fd, file_page_start);
      if (seg_addr == MAP_FAILED) {
        set_error_msg(&error_msg_, "couldn't map \"%s\" segment %zd: %s", name_, i,
                      strerror(errno));
        return false;
      }
    }

    // if the segment is writable, and does not end on a page boundary,
    // zero-fill it until the page limit.
    if ((phdr->p_flags & PF_W) != 0 && page_offset(seg_file_end) > 0) {
      memset(reinterpret_cast<void*>(seg_file_end),
             0,
             berberis::kPageSize - page_offset(seg_file_end));
    }

    seg_file_end = page_align_up(seg_file_end);

    // seg_file_end is now the first page address after the file
    // content. If seg_end is larger, we need to zero anything
    // between them. This is done by using a private anonymous
    // map for all extra pages.
    if (seg_page_end > seg_file_end) {
      size_t zeromap_size = seg_page_end - seg_file_end;
      void* zeromap =
          mmap64_fn(reinterpret_cast<void*>(seg_file_end), zeromap_size,
                    PFLAGS_TO_PROT(phdr->p_flags), MAP_FIXED | MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
      if (zeromap == MAP_FAILED) {
        set_error_msg(&error_msg_, "couldn't zero fill \"%s\" gap: %s", name_, strerror(errno));
        return false;
      }

      berberis::SetVmaAnonName(zeromap, zeromap_size, ".bss");
    }
  }

  return true;
}

bool TinyElfLoader::FindDynamicSegment(const ElfEhdr* header) {
  // Static executables do not have PT_DYNAMIC
  if (header->e_type == ET_EXEC) {
    return true;
  }

  for (size_t i = 0; i < loaded_phdr_num_; ++i) {
    const ElfPhdr& phdr = loaded_phdr_[i];
    if (phdr.p_type == PT_DYNAMIC) {
      // TODO(dimitry): Check all addresses and sizes referencing loaded segments.
      dynamic_ = reinterpret_cast<ElfDyn*>(load_bias_ + phdr.p_vaddr);
      return true;
    }
  }

  set_error_msg(&error_msg_, "dynamic segment was not found in \"%s\"", name_);
  return false;
}

bool TinyElfLoader::InitializeFields(const ElfEhdr* header) {
  if (header->e_entry != 0) {
    entry_point_ = reinterpret_cast<void*>(load_bias_ + header->e_entry);
  }

  // There is nothing else to do for a static executable.
  if (header->e_type == ET_EXEC) {
    return true;
  }

  for (const ElfDyn* d = dynamic_; d->d_tag != DT_NULL; ++d) {
    if (d->d_tag == DT_GNU_HASH) {
      has_gnu_hash_ = true;
      gnu_nbucket_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[0];
      gnu_maskwords_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[2];
      gnu_shift2_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[3];
      gnu_bloom_filter_ = reinterpret_cast<ElfAddr*>(load_bias_ + d->d_un.d_ptr + 16);
      gnu_bucket_ = reinterpret_cast<uint32_t*>(gnu_bloom_filter_ + gnu_maskwords_);
      gnu_chain_ =
          gnu_bucket_ + gnu_nbucket_ - reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[1];

      if (!powerof2(gnu_maskwords_)) {
        set_error_msg(&error_msg_,
                      "invalid maskwords for gnu_hash = 0x%x, in \"%s\" expecting power of two",
                      gnu_maskwords_, name_);

        return false;
      }

      --gnu_maskwords_;
    } else if (d->d_tag == DT_HASH) {
      sysv_nbucket_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[0];
      sysv_nchain_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr)[1];
      sysv_bucket_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr + 8);
      sysv_chain_ = reinterpret_cast<uint32_t*>(load_bias_ + d->d_un.d_ptr + 8 + sysv_nbucket_ * 4);
    } else if (d->d_tag == DT_SYMTAB) {
      symtab_ = reinterpret_cast<ElfSym*>(load_bias_ + d->d_un.d_ptr);
    } else if (d->d_tag == DT_STRTAB) {
      strtab_ = reinterpret_cast<const char*>(load_bias_ + d->d_un.d_ptr);
    } else if (d->d_tag == DT_STRSZ) {
      strtab_size_ = d->d_un.d_val;
    }
  }

  if (symtab_ == nullptr) {
    set_error_msg(&error_msg_, "missing DT_SYMTAB in \"%s\"", name_);
    return false;
  }

  if (strtab_ == nullptr) {
    set_error_msg(&error_msg_, "missing DT_STRTAB in \"%s\"", name_);
    return false;
  }

  if (strtab_size_ == 0) {
    set_error_msg(&error_msg_, "missing or invalid (0) DT_STRSZ in \"%s\"", name_);
    return false;
  }

  return true;
}

bool TinyElfLoader::Parse(void* load_ptr, size_t load_size, LoadedElfFile* loaded_elf_file) {
  uintptr_t load_addr = reinterpret_cast<uintptr_t>(load_ptr);
  const ElfEhdr* header = reinterpret_cast<const ElfEhdr*>(load_addr);
  if (!CheckElfHeader(header)) {
    return false;
  }

  if (!ReadProgramHeadersFromMemory(header, load_addr, load_size, &loaded_phdr_,
                                    &loaded_phdr_num_)) {
    return false;
  }

  ElfAddr min_vaddr;
  phdr_table_get_load_size(loaded_phdr_, loaded_phdr_num_, &min_vaddr);
  load_bias_ = load_addr - min_vaddr;

  if (!FindDynamicSegment(header) || !InitializeFields(header)) {
    return false;
  }

  if (has_gnu_hash_) {
    *loaded_elf_file = LoadedElfFile(header->e_type, load_ptr, load_bias_, entry_point_,
                                     loaded_phdr_, loaded_phdr_num_, dynamic_, gnu_nbucket_,
                                     gnu_bucket_, gnu_chain_, gnu_maskwords_, gnu_shift2_,
                                     gnu_bloom_filter_, symtab_, strtab_, strtab_size_);
  } else {
    *loaded_elf_file =
        LoadedElfFile(header->e_type, load_ptr, load_bias_, entry_point_, loaded_phdr_,
                      loaded_phdr_num_, dynamic_, sysv_nbucket_, sysv_nchain_, sysv_bucket_,
                      sysv_chain_, symtab_, strtab_, strtab_size_);
  }
  return true;
}

bool TinyElfLoader::LoadFromFile(int fd, off64_t file_size, size_t align,
                                 TinyLoader::mmap64_fn_t mmap64_fn,
                                 TinyLoader::munmap_fn_t munmap_fn,
                                 LoadedElfFile* loaded_elf_file) {
  CHECK(!did_load_);
  void* load_addr = nullptr;
  size_t load_size = 0;
  ElfEhdr header;
  const ElfPhdr* phdr_table = nullptr;
  size_t phdr_num = 0;

  did_load_ = ReadElfHeader(fd, &header) &&
              ReadProgramHeadersFromFile(&header, fd, file_size, &phdr_table, &phdr_num) &&
              LoadSegments(fd, file_size, header.e_type, phdr_table, phdr_num, align, mmap64_fn,
                           munmap_fn, &load_addr, &load_size) &&
              Parse(load_addr, load_size, loaded_elf_file);

  return did_load_;
}

bool TinyElfLoader::LoadFromMemory(void* load_addr, size_t load_size,
                                   LoadedElfFile* loaded_elf_file) {
  CHECK(!did_load_);
  did_load_ = Parse(load_addr, load_size, loaded_elf_file);
  return did_load_;
}

}  // namespace

bool TinyLoader::LoadFromFile(const char* path, size_t align, TinyLoader::mmap64_fn_t mmap64_fn,
                              TinyLoader::munmap_fn_t munmap_fn, LoadedElfFile* loaded_elf_file,
                              std::string* error_msg) {
  int fd = TEMP_FAILURE_RETRY(open(path, O_RDONLY | O_CLOEXEC));
  if (fd == -1) {
    set_error_msg(error_msg, "unable to open the file \"%s\": %s", path, strerror(errno));
    return false;
  }

  struct stat file_stat;
  if (TEMP_FAILURE_RETRY(fstat(fd, &file_stat)) != 0) {
    set_error_msg(error_msg, "unable to stat file for the library \"%s\": %s", path,
                  strerror(errno));
    close(fd);
    return false;
  }

  TinyElfLoader loader(path);

  if (!loader.LoadFromFile(fd, file_stat.st_size, align, mmap64_fn, munmap_fn, loaded_elf_file)) {
    if (error_msg != nullptr) {
      *error_msg = loader.error_msg();
    }

    close(fd);
    return false;
  }

  close(fd);
  return true;
}

bool TinyLoader::LoadFromMemory(const char* path, void* address, size_t size,
                                LoadedElfFile* loaded_elf_file, std::string* error_msg) {
  TinyElfLoader loader(path);
  if (!loader.LoadFromMemory(address, size, loaded_elf_file)) {
    if (error_msg != nullptr) {
      *error_msg = loader.error_msg();
    }

    return false;
  }

  return true;
}
