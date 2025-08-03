/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef BERBERIS_TINY_LOADER_TINY_SYMBOL_TABLE_H_
#define BERBERIS_TINY_LOADER_TINY_SYMBOL_TABLE_H_

#include "berberis/base/macros.h"
#include "berberis/tiny_loader/elf_types.h"

#include <elf.h>

#include <functional>

class TinySymbolTable {
 public:
  TinySymbolTable();

  TinySymbolTable(ElfAddr load_bias, ElfSym* symtab, const char* strtab, size_t strtab_size,
                  size_t gnu_nbucket, uint32_t* gnu_bucket, uint32_t* gnu_chain,
                  uint32_t gnu_maskwords, uint32_t gnu_shift2, ElfAddr* gnu_bloom_filter);

  TinySymbolTable(ElfAddr load_bias, ElfSym* symtab, const char* strtab, size_t strtab_size,
                  size_t sysv_nbucket, size_t sysv_nchain, uint32_t* sysv_bucket,
                  uint32_t* sysv_chain);

  TinySymbolTable(TinySymbolTable&& that) = default;
  TinySymbolTable& operator=(TinySymbolTable&& that) = default;

  void* FindSymbol(const char* name) const;

  // Iterate over all defined symbols in the elf-file
  void ForEachSymbol(std::function<void(const char*, void*, const ElfSym*)> symbol_handler) const;

 private:
  uint32_t GnuHash(const char* name) const;
  uint32_t SysvHash(const char* name) const;
  void* FindGnuSymbol(const char* name) const;
  void* FindSysvSymbol(const char* name) const;
  void ForEachGnuSymbol(std::function<void(const ElfSym*)> symbol_handler) const;
  void ForEachSysvSymbol(std::function<void(const ElfSym*)> symbol_handler) const;

  const char* GetString(ElfWord index) const;

  ElfAddr load_bias_;

  // Symbol table
  ElfSym* symtab_;

  // String table
  const char* strtab_;
  size_t strtab_size_;

  bool is_gnu_hash_;

  // Gnu hash
  size_t gnu_nbucket_;
  uint32_t* gnu_bucket_;
  uint32_t* gnu_chain_;
  uint32_t gnu_maskwords_;
  uint32_t gnu_shift2_;
  ElfAddr* gnu_bloom_filter_;

  // Sysv hash
  size_t sysv_nbucket_;
  size_t sysv_nchain_;
  uint32_t* sysv_bucket_;
  uint32_t* sysv_chain_;
};

#endif  // BERBERIS_TINY_LOADER_TINY_SYMBOL_TABLE_H_
