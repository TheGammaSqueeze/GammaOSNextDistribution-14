// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022-2023 Google LLC
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
// Author: Giuliano Procida

#include "unification.h"

#include <cstddef>
#include <utility>

#include "graph.h"

namespace stg {

namespace {

// Type Unification
//
// This is very similar to Equals. The differences are the recursion control,
// caching and handling of StructUnion and Enum nodes.
//
// During unification, keep track of which pairs of types need to be equal, but
// do not add them immediately to the unification substitutions. The caller can
// do that if the whole unification succeeds.
//
// A declaration and definition of the same named type can be unified. This is
// forward declaration resolution.
struct Unifier {
  enum Winner { Neither, Right, Left };  // makes p ? Right : Neither a no-op

  Unifier(const Graph& graph, Unification& unification)
      : graph(graph), unification(unification) {}

  bool operator()(Id id1, Id id2) {
    Id fid1 = Find(id1);
    Id fid2 = Find(id2);
    if (fid1 == fid2) {
      return true;
    }

    // Check if the comparison has an already known result.
    //
    // Opportunistic as seen is unaware of new mappings.
    if (!seen.emplace(fid1, fid2).second) {
      return true;
    }

    const auto winner = graph.Apply2<Winner>(*this, fid1, fid2);
    if (winner == Neither) {
      return false;
    }

    // These will occasionally get substituted due to a recursive call.
    fid1 = Find(fid1);
    fid2 = Find(fid2);
    if (fid1 == fid2) {
      return true;
    }

    if (winner == Left) {
      std::swap(fid1, fid2);
    }
    mapping.insert({fid1, fid2});

    return true;
  }

  bool operator()(const std::vector<Id>& ids1, const std::vector<Id>& ids2) {
    bool result = ids1.size() == ids2.size();
    for (size_t ix = 0; result && ix < ids1.size(); ++ix) {
      result = (*this)(ids1[ix], ids2[ix]);
    }
    return result;
  }

  template <typename Key>
  bool operator()(const std::map<Key, Id>& ids1,
                  const std::map<Key, Id>& ids2) {
    bool result = ids1.size() == ids2.size();
    auto it1 = ids1.begin();
    auto it2 = ids2.begin();
    const auto end1 = ids1.end();
    const auto end2 = ids2.end();
    while (result && it1 != end1 && it2 != end2) {
      result = it1->first == it2->first
               && (*this)(it1->second, it2->second);
      ++it1;
      ++it2;
    }
    return result && it1 == end1 && it2 == end2;
  }

  Winner operator()(const Special& x1, const Special& x2) {
    return x1.kind == x2.kind
        ? Right : Neither;
  }

  Winner operator()(const PointerReference& x1,
                    const PointerReference& x2) {
    return x1.kind == x2.kind
        && (*this)(x1.pointee_type_id, x2.pointee_type_id)
        ? Right : Neither;
  }

  Winner operator()(const PointerToMember& x1, const PointerToMember& x2) {
    return (*this)(x1.containing_type_id, x2.containing_type_id)
        && (*this)(x1.pointee_type_id, x2.pointee_type_id)
        ? Right : Neither;
  }

  Winner operator()(const Typedef& x1, const Typedef& x2) {
    return x1.name == x2.name
        && (*this)(x1.referred_type_id, x2.referred_type_id)
        ? Right : Neither;
  }

  Winner operator()(const Qualified& x1, const Qualified& x2) {
    return x1.qualifier == x2.qualifier
        && (*this)(x1.qualified_type_id, x2.qualified_type_id)
        ? Right : Neither;
  }

  Winner operator()(const Primitive& x1, const Primitive& x2) {
    return x1.name == x2.name
        && x1.encoding == x2.encoding
        && x1.bytesize == x2.bytesize
        ? Right : Neither;
  }

  Winner operator()(const Array& x1, const Array& x2) {
    return x1.number_of_elements == x2.number_of_elements
        && (*this)(x1.element_type_id, x2.element_type_id)
        ? Right : Neither;
  }

  Winner operator()(const BaseClass& x1, const BaseClass& x2) {
    return x1.offset == x2.offset
        && x1.inheritance == x2.inheritance
        && (*this)(x1.type_id, x2.type_id)
        ? Right : Neither;
  }

  Winner operator()(const Method& x1, const Method& x2) {
    return x1.mangled_name == x2.mangled_name
        && x1.name == x2.name
        && x1.vtable_offset == x2.vtable_offset
        && (*this)(x1.type_id, x2.type_id)
        ? Right : Neither;
  }

  Winner operator()(const Member& x1, const Member& x2) {
    return x1.name == x2.name
        && x1.offset == x2.offset
        && x1.bitsize == x2.bitsize
        && (*this)(x1.type_id, x2.type_id)
        ? Right : Neither;
  }

  Winner operator()(const StructUnion& x1, const StructUnion& x2) {
    const auto& definition1 = x1.definition;
    const auto& definition2 = x2.definition;
    bool result = x1.kind == x2.kind
                  && x1.name == x2.name;
    // allow mismatches as forward declarations are always unifiable
    if (result && definition1.has_value() && definition2.has_value()) {
      result = definition1->bytesize == definition2->bytesize
               && (*this)(definition1->base_classes, definition2->base_classes)
               && (*this)(definition1->methods, definition2->methods)
               && (*this)(definition1->members, definition2->members);
    }
    return result ? definition2.has_value() ? Right : Left : Neither;
  }

  Winner operator()(const Enumeration& x1, const Enumeration& x2) {
    const auto& definition1 = x1.definition;
    const auto& definition2 = x2.definition;
    bool result = x1.name == x2.name;
    // allow mismatches as forward declarations are always unifiable
    if (result && definition1.has_value() && definition2.has_value()) {
      result = (*this)(definition1->underlying_type_id,
                       definition2->underlying_type_id)
               && definition1->enumerators == definition2->enumerators;
    }
    return result ? definition2.has_value() ? Right : Left : Neither;
  }

  Winner operator()(const Function& x1, const Function& x2) {
    return (*this)(x1.parameters, x2.parameters)
        && (*this)(x1.return_type_id, x2.return_type_id)
        ? Right : Neither;
  }

  Winner operator()(const ElfSymbol& x1, const ElfSymbol& x2) {
    bool result = x1.symbol_name == x2.symbol_name
                  && x1.version_info == x2.version_info
                  && x1.is_defined == x2.is_defined
                  && x1.symbol_type == x2.symbol_type
                  && x1.binding == x2.binding
                  && x1.visibility == x2.visibility
                  && x1.crc == x2.crc
                  && x1.ns == x2.ns
                  && x1.full_name == x2.full_name
                  && x1.type_id.has_value() == x2.type_id.has_value();
    if (result && x1.type_id.has_value()) {
      result = (*this)(x1.type_id.value(), x2.type_id.value());
    }
    return result ? Right : Neither;
  }

  Winner operator()(const Interface& x1, const Interface& x2) {
    return (*this)(x1.symbols, x2.symbols)
        && (*this)(x1.types, x2.types)
        ? Right : Neither;
  }

  Winner Mismatch() {
    return Neither;
  }

  Id Find(Id id) {
    while (true) {
      id = unification.Find(id);
      auto it = mapping.find(id);
      if (it != mapping.end()) {
        id = it->second;
        continue;
      }
      return id;
    }
  }

  const Graph& graph;
  Unification& unification;
  std::unordered_set<Pair> seen;
  std::unordered_map<Id, Id> mapping;
};

}  // namespace

bool Unification::Unify(Id id1, Id id2) {
  // TODO: Unifier only needs access to Unification::Find
  Unifier unifier(graph_, *this);
  if (unifier(id1, id2)) {
    // commit
    for (const auto& s : unifier.mapping) {
      Union(s.first, s.second);
    }
    return true;
  }
  return false;
}

}  // namespace stg
