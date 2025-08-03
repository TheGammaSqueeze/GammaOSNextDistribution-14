// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2023 Google LLC
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

#include <string_view>

#include <catch2/catch.hpp>
#include "elf_loader.h"
#include "elf_reader.h"
#include "graph.h"

namespace Test {

using SymbolTable = stg::elf::internal::SymbolTable;
using SymbolTableEntry = stg::elf::SymbolTableEntry;

SymbolTableEntry MakeSymbol(std::string_view name) {
  return {
    .name = name,
    .value = 0,
    .size = 0,
    .symbol_type = SymbolTableEntry::SymbolType::OBJECT,
    .binding = SymbolTableEntry::Binding::GLOBAL,
    .visibility = SymbolTableEntry::Visibility::DEFAULT,
    .section_index = 0,
    .value_type = SymbolTableEntry::ValueType::RELATIVE_TO_SECTION,
  };
}


TEST_CASE("GetKsymtabSymbols") {
  const SymbolTable all_symbols = {
    MakeSymbol("foo"),
    MakeSymbol("__ksymtab_foo"),
    MakeSymbol("bar"),
  };
  const auto ksymtab = stg::elf::internal::GetKsymtabSymbols(all_symbols);
  REQUIRE(ksymtab.size() == 1);
  CHECK(*ksymtab.begin() == "foo");
}

}  // namespace Test
