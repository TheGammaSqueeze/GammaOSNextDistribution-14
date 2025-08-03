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

#ifndef BERBERIS_TINY_LOADER_LOADED_ELF_FILE_H_
#define BERBERIS_TINY_LOADER_LOADED_ELF_FILE_H_

#include "berberis/base/macros.h"
#include "berberis/tiny_loader/elf_types.h"
#include "berberis/tiny_loader/tiny_symbol_table.h"

#include <elf.h>

#include <string>

class LoadedElfFile {
 public:
  LoadedElfFile()
      : e_type_(ET_NONE),
        base_addr_(nullptr),
        load_bias_(0),
        entry_point_(nullptr),
        phdr_table_(nullptr),
        phdr_count_(0),
        dynamic_(nullptr) {}

  LoadedElfFile(ElfHalf e_type, void* base_addr, ElfAddr load_bias, void* entry_point,
                const ElfPhdr* phdr_table, size_t phdr_count, const ElfDyn* dynamic,
                size_t gnu_nbucket, uint32_t* gnu_bucket, uint32_t* gnu_chain,
                uint32_t gnu_maskwords, uint32_t gnu_shift2, ElfAddr* gnu_bloom_filter,
                ElfSym* symtab, const char* strtab, size_t strtab_size)
      : e_type_(e_type),
        base_addr_(base_addr),
        load_bias_(load_bias),
        entry_point_(entry_point),
        phdr_table_(phdr_table),
        phdr_count_(phdr_count),
        dynamic_(dynamic),
        symbol_table_(load_bias, symtab, strtab, strtab_size, gnu_nbucket, gnu_bucket, gnu_chain,
                      gnu_maskwords, gnu_shift2, gnu_bloom_filter) {}

  LoadedElfFile(ElfHalf e_type, void* base_addr, ElfAddr load_bias, void* entry_point,
                const ElfPhdr* phdr_table, size_t phdr_count, const ElfDyn* dynamic,
                size_t sysv_nbucket, size_t sysv_nchain, uint32_t* sysv_bucket,
                uint32_t* sysv_chain, ElfSym* symtab, const char* strtab, size_t strtab_size)
      : e_type_(e_type),
        base_addr_(base_addr),
        load_bias_(load_bias),
        entry_point_(entry_point),
        phdr_table_(phdr_table),
        phdr_count_(phdr_count),
        dynamic_(dynamic),
        symbol_table_(load_bias, symtab, strtab, strtab_size, sysv_nbucket, sysv_nchain,
                      sysv_bucket, sysv_chain) {}

  LoadedElfFile(LoadedElfFile&& that) = default;

  LoadedElfFile& operator=(LoadedElfFile&& that) = default;

  bool is_loaded() const { return e_type_ != ET_NONE; }

  ElfHalf e_type() const { return e_type_; }

  void* base_addr() const { return base_addr_; }

  ElfAddr load_bias() const { return load_bias_; }

  void* entry_point() const { return entry_point_; }

  const ElfPhdr* phdr_table() const { return phdr_table_; }

  size_t phdr_count() const { return phdr_count_; }

  const ElfDyn* dynamic() const { return dynamic_; }

  void* FindSymbol(const char* name) const { return symbol_table_.FindSymbol(name); }

  void ForEachSymbol(std::function<void(const char*, void*, const ElfSym*)> symbol_handler) const {
    symbol_table_.ForEachSymbol(symbol_handler);
  }

 private:
  ElfHalf e_type_;
  void* base_addr_;
  ElfAddr load_bias_;
  void* entry_point_;
  const ElfPhdr* phdr_table_;
  size_t phdr_count_;
  const ElfDyn* dynamic_;

  TinySymbolTable symbol_table_;
};

#endif  // BERBERIS_TINY_LOADER_LOADED_ELF_FILE_H_
