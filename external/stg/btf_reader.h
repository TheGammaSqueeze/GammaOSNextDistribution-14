// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2020-2023 Google LLC
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
// Author: Maria Teguiani
// Author: Giuliano Procida

#ifndef STG_BTF_READER_H_
#define STG_BTF_READER_H_

#include <cstddef>
#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include <linux/btf.h>
#include "graph.h"
#include "reader_options.h"

namespace stg {
namespace btf {

// BTF Specification: https://www.kernel.org/doc/html/latest/bpf/btf.html
class Structs {
 public:
  explicit Structs(Graph& graph);
  Id Process(std::string_view data);

 private:
  struct MemoryRange {
    const char* start;
    const char* limit;
    bool Empty() const;
    template <typename T> const T* Pull(size_t count = 1);
  };

  Graph& graph_;

  MemoryRange string_section_;

  std::optional<Id> void_;
  std::optional<Id> variadic_;
  std::unordered_map<uint32_t, Id> btf_type_ids_;
  std::map<std::string, Id> btf_symbols_;

  Id GetVoid();
  Id GetVariadic();
  Id GetIdRaw(uint32_t btf_index);
  Id GetId(uint32_t btf_index);
  Id GetParameterId(uint32_t btf_index);

  Id BuildTypes(MemoryRange memory);
  void BuildOneType(const btf_type* t, uint32_t btf_index,
                    MemoryRange& memory);
  Id BuildSymbols();
  std::vector<Id> BuildMembers(
      bool kflag, const btf_member* members, size_t vlen);
  Enumeration::Enumerators BuildEnums(
      bool is_signed, const struct btf_enum* enums, size_t vlen);
  Enumeration::Enumerators BuildEnums64(
      bool is_signed, const struct btf_enum64* enums, size_t vlen);
  std::vector<Id> BuildParams(const struct btf_param* params, size_t vlen);
  Id BuildEnumUnderlyingType(size_t size, bool is_signed);
  std::string GetName(uint32_t name_off);
};

Id ReadFile(Graph& graph, const std::string& path, ReadOptions options);

}  // namespace btf
}  // namespace stg

#endif  // STG_BTF_READER_H_
