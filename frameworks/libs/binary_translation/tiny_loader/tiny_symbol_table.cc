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

#include "berberis/tiny_loader/tiny_symbol_table.h"

#include <elf.h>
#include <inttypes.h>

#include "berberis/base/checks.h"

TinySymbolTable::TinySymbolTable()
    : load_bias_(0),
      symtab_(nullptr),
      strtab_(nullptr),
      strtab_size_(0),
      is_gnu_hash_(false),
      gnu_nbucket_(0),
      gnu_bucket_(nullptr),
      gnu_chain_(nullptr),
      gnu_maskwords_(0),
      gnu_shift2_(0),
      gnu_bloom_filter_(nullptr),
      sysv_nbucket_(0),
      sysv_nchain_(0),
      sysv_bucket_(nullptr),
      sysv_chain_(nullptr) {}

TinySymbolTable::TinySymbolTable(ElfAddr load_bias, ElfSym* symtab, const char* strtab,
                                 size_t strtab_size, size_t gnu_nbucket, uint32_t* gnu_bucket,
                                 uint32_t* gnu_chain, uint32_t gnu_maskwords, uint32_t gnu_shift2,
                                 ElfAddr* gnu_bloom_filter)
    : load_bias_(load_bias),
      symtab_(symtab),
      strtab_(strtab),
      strtab_size_(strtab_size),
      is_gnu_hash_(true),
      gnu_nbucket_(gnu_nbucket),
      gnu_bucket_(gnu_bucket),
      gnu_chain_(gnu_chain),
      gnu_maskwords_(gnu_maskwords),
      gnu_shift2_(gnu_shift2),
      gnu_bloom_filter_(gnu_bloom_filter),
      sysv_nbucket_(0),
      sysv_nchain_(0),
      sysv_bucket_(nullptr),
      sysv_chain_(nullptr) {}

TinySymbolTable::TinySymbolTable(ElfAddr load_bias, ElfSym* symtab, const char* strtab,
                                 size_t strtab_size, size_t sysv_nbucket, size_t sysv_nchain,
                                 uint32_t* sysv_bucket, uint32_t* sysv_chain)
    : load_bias_(load_bias),
      symtab_(symtab),
      strtab_(strtab),
      strtab_size_(strtab_size),
      is_gnu_hash_(false),
      gnu_nbucket_(0),
      gnu_bucket_(nullptr),
      gnu_chain_(nullptr),
      gnu_maskwords_(0),
      gnu_shift2_(0),
      gnu_bloom_filter_(nullptr),
      sysv_nbucket_(sysv_nbucket),
      sysv_nchain_(sysv_nchain),
      sysv_bucket_(sysv_bucket),
      sysv_chain_(sysv_chain) {}

uint32_t TinySymbolTable::GnuHash(const char* symbol_name) const {
  uint32_t h = 5381;
  const uint8_t* name = reinterpret_cast<const uint8_t*>(symbol_name);
  while (*name != 0) {
    h += (h << 5) + *name++;  // h*33 + c = h + h * 32 + c = h + h << 5 + c
  }

  return h;
}

uint32_t TinySymbolTable::SysvHash(const char* symbol_name) const {
  const uint8_t* name = reinterpret_cast<const uint8_t*>(symbol_name);
  uint32_t h = 0;
  while (*name != 0) {
    h = (h << 4) + *name++;
    uint32_t g = h & 0xf0000000;
    h ^= g;
    h ^= g >> 24;
  }

  return h;
}

const char* TinySymbolTable::GetString(ElfWord index) const {
  CHECK(index < strtab_size_);
  return strtab_ + index;
}

static bool is_symbol_global_and_defined(ElfSym* s) {
  return (ELF32_ST_BIND(s->st_info) == STB_GLOBAL || ELF32_ST_BIND(s->st_info) == STB_WEAK) &&
         s->st_shndx != SHN_UNDEF;
}

void* TinySymbolTable::FindGnuSymbol(const char* name) const {
  CHECK(is_gnu_hash_);
  CHECK(gnu_bloom_filter_ != nullptr);
  CHECK(gnu_bucket_ != nullptr);
  CHECK(gnu_chain_ != nullptr);

  uint32_t hash = GnuHash(name);
  uint32_t h2 = hash >> gnu_shift2_;

  uint32_t bloom_mask_bits = sizeof(ElfAddr) * 8;
  uint32_t word_num = (hash / bloom_mask_bits) & gnu_maskwords_;
  ElfAddr bloom_word = gnu_bloom_filter_[word_num];

  // test against bloom filter
  if ((1 & (bloom_word >> (hash % bloom_mask_bits)) & (bloom_word >> (h2 % bloom_mask_bits))) ==
      0) {
    return nullptr;
  }

  // probably yes. Run precise test..
  uint32_t n = gnu_bucket_[hash % gnu_nbucket_];

  if (n == 0) {
    return nullptr;
  }

  do {
    ElfSym* s = symtab_ + n;
    if (((gnu_chain_[n] ^ hash) >> 1) == 0 && strcmp(GetString(s->st_name), name) == 0 &&
        is_symbol_global_and_defined(s)) {
      return reinterpret_cast<void*>(load_bias_ + s->st_value);
    }
  } while ((gnu_chain_[n++] & 1) == 0);

  return nullptr;
}

void* TinySymbolTable::FindSysvSymbol(const char* name) const {
  CHECK(!is_gnu_hash_);
  CHECK(sysv_bucket_ != nullptr);
  CHECK(sysv_chain_ != nullptr);

  uint32_t hash = SysvHash(name);

  for (uint32_t n = sysv_bucket_[hash % sysv_nbucket_]; n != 0; n = sysv_chain_[n]) {
    ElfSym* s = symtab_ + n;
    if (strcmp(GetString(s->st_name), name) == 0 && is_symbol_global_and_defined(s)) {
      return reinterpret_cast<void*>(load_bias_ + s->st_value);
    }
  }

  return nullptr;
}

void TinySymbolTable::ForEachGnuSymbol(std::function<void(const ElfSym*)> symbol_handler) const {
  CHECK(is_gnu_hash_);
  CHECK(gnu_bucket_ != nullptr);
  CHECK(gnu_chain_ != nullptr);

  for (size_t i = 0; i < gnu_nbucket_; ++i) {
    uint32_t n = gnu_bucket_[i];

    if (n == 0) {
      continue;
    }

    do {
      symbol_handler(symtab_ + n);
    } while ((gnu_chain_[n++] & 1) == 0);
  }
}

void TinySymbolTable::ForEachSysvSymbol(std::function<void(const ElfSym*)> symbol_handler) const {
  CHECK(!is_gnu_hash_);

  for (size_t i = 0; i < sysv_nchain_; ++i) {
    symbol_handler(symtab_ + i);
  }
}

void TinySymbolTable::ForEachSymbol(
    std::function<void(const char*, void*, const ElfSym*)> symbol_handler) const {
  std::function<void(const ElfSym*)> handler = [&](const ElfSym* s) {
    symbol_handler(GetString(s->st_name), reinterpret_cast<void*>(load_bias_ + s->st_value), s);
  };

  if (is_gnu_hash_) {
    ForEachGnuSymbol(handler);
  } else {
    ForEachSysvSymbol(handler);
  }
}

void* TinySymbolTable::FindSymbol(const char* name) const {
  return is_gnu_hash_ ? FindGnuSymbol(name) : FindSysvSymbol(name);
}
