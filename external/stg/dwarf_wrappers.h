// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Aleksei Vetrov

#ifndef STG_DWARF_WRAPPERS_H_
#define STG_DWARF_WRAPPERS_H_

#include <elf.h>
#include <elfutils/libdw.h>
#include <elfutils/libdwfl.h>

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <ostream>
#include <string>
#include <tuple>
#include <vector>

namespace stg {
namespace dwarf {

struct Address {
  // TODO: use auto operator<=>
  bool operator<(const Address& other) const {
    return std::tie(value, is_tls) < std::tie(other.value, other.is_tls);
  }

  bool operator==(const Address& other) const {
    return value == other.value && is_tls == other.is_tls;
  }

  uint64_t value;
  bool is_tls;
};

std::ostream& operator<<(std::ostream& os, const Address& address);

// C++ wrapper over Dwarf_Die, providing interface for its various properties.
struct Entry {
  // All methods in libdw take Dwarf_Die by non-const pointer as libdw caches
  // in it a link to the associated abbreviation table. Updating this link is
  // not thread-safe and so we cannot, for example, hold a std::shared_ptr to a
  // heap-allocated Dwarf_Die.
  //
  // The only options left are holding a std::unique_ptr or storing a value.
  // Unique pointers will add one more level of indirection to a hot path.
  // So we choose to store Dwarf_Die values.
  //
  // Each Entry only contains references to DWARF file memory and is fairly
  // small (32 bytes), so copies can be easily made if necessary. However,
  // within one thread it is preferable to pass it by reference.
  Dwarf_Die die{};

  // Get list of direct descendants of an entry in the DWARF tree.
  std::vector<Entry> GetChildren();

  // All getters are non-const as libdw may need to modify Dwarf_Die.
  int GetTag();
  Dwarf_Off GetOffset();
  std::optional<std::string> MaybeGetString(uint32_t attribute);
  std::optional<std::string> MaybeGetDirectString(uint32_t attribute);
  std::optional<uint64_t> MaybeGetUnsignedConstant(uint32_t attribute);
  bool GetFlag(uint32_t attribute);
  std::optional<Entry> MaybeGetReference(uint32_t attribute);
  std::optional<Address> MaybeGetAddress(uint32_t attribute);
  std::optional<uint64_t> MaybeGetMemberByteOffset();
  std::optional<uint64_t> MaybeGetVtableOffset();
  // Returns value of subrange element count if it is constant or nullopt if it
  // is not defined or cannot be represented as constant.
  std::optional<uint64_t> MaybeGetCount();
};

// Metadata and top-level entry of a compilation unit.
struct CompilationUnit {
  int version;
  Entry entry;
};

// C++ wrapper over libdw (DWARF library).
//
// Creates a "Dwarf" object from an ELF file or a memory and controls the life
// cycle of the created objects.
class Handler {
 public:
  explicit Handler(const std::string& path);
  Handler(char* data, size_t size);

  Elf* GetElf();
  std::vector<CompilationUnit> GetCompilationUnits();

 private:
  struct DwflDeleter {
    void operator()(Dwfl* dwfl) {
      dwfl_end(dwfl);
    }
  };

  void InitialiseDwarf();

  std::unique_ptr<Dwfl, DwflDeleter> dwfl_;
  // Lifetime of Dwfl_Module and Dwarf is controlled by Dwfl.
  Dwfl_Module* dwfl_module_ = nullptr;
  Dwarf* dwarf_ = nullptr;
};

class Files {
 public:
  Files() = default;
  explicit Files(Entry& compilation_unit);
  std::optional<std::string> MaybeGetFile(Entry& entry,
                                          uint32_t attribute) const;

 private:
  Dwarf_Files* files_ = nullptr;
  size_t files_count_ = 0;
};

}  // namespace dwarf
}  // namespace stg

#endif  // STG_DWARF_WRAPPERS_H_
