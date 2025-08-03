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
// Author: Siddharth Nayyar

#ifndef STG_STABLE_HASH_H_
#define STG_STABLE_HASH_H_

#include <cstdint>
#include <iostream>
#include <unordered_map>
#include <vector>

#include "graph.h"
#include "hashing.h"

namespace stg {

class StableHash {
 public:
  explicit StableHash(const Graph& graph) : graph_(graph) {}

  HashValue operator()(Id);
  HashValue operator()(const Special&);
  HashValue operator()(const PointerReference&);
  HashValue operator()(const PointerToMember&);
  HashValue operator()(const Typedef&);
  HashValue operator()(const Qualified&);
  HashValue operator()(const Primitive&);
  HashValue operator()(const Array&);
  HashValue operator()(const BaseClass&);
  HashValue operator()(const Method&);
  HashValue operator()(const Member&);
  HashValue operator()(const StructUnion&);
  HashValue operator()(const Enumeration&);
  HashValue operator()(const Function&);
  HashValue operator()(const ElfSymbol&);
  HashValue operator()(const Interface&);

 private:
  const Graph& graph_;
  std::unordered_map<Id, HashValue> cache_;

  // Function object: (Args...) -> HashValue
  Hash hash_;
};

}  // namespace stg

#endif  // STG_STABLE_HASH_H_
