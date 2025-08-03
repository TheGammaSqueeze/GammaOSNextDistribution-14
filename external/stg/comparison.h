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
// Author: Ignes Simeonova

#ifndef STG_COMPARISON_H_
#define STG_COMPARISON_H_

#include <cstddef>
#include <functional>
#include <map>
#include <memory>
#include <optional>
#include <ostream>
#include <set>
#include <sstream>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

#include "graph.h"
#include "runtime.h"
#include "scc.h"

namespace stg {

struct Ignore {
  enum Value {
    // noise reduction
    SYMBOL_TYPE_PRESENCE = 1<<0,
    TYPE_DECLARATION_STATUS = 1<<1,
    PRIMITIVE_TYPE_ENCODING = 1<<2,
    MEMBER_SIZE = 1<<3,
    ENUM_UNDERLYING_TYPE = 1<<4,
    QUALIFIER = 1<<5,
    SYMBOL_CRC = 1<<6,
    // ABI compatibility testing
    INTERFACE_ADDITION = 1<<7,
    TYPE_DEFINITION_ADDITION = 1<<8,
  };

  using Bitset = std::underlying_type_t<Value>;

  Ignore() = default;
  template <typename... Values>
  explicit Ignore(Values... values) {
    for (auto value : {values...}) {
      Set(value);
    }
  }

  void Set(Value other) {
    bitset |= static_cast<Bitset>(other);
  }
  bool Test(Value other) const {
    return bitset & static_cast<Bitset>(other);
  }

  Bitset bitset = 0;
};

std::optional<Ignore::Value> ParseIgnore(std::string_view ignore);

struct IgnoreUsage {};
std::ostream& operator<<(std::ostream& os, IgnoreUsage);

using Comparison = std::pair<std::optional<Id>, std::optional<Id>>;

struct DiffDetail {
  DiffDetail(const std::string& text, const std::optional<Comparison>& edge)
      : text_(text), edge_(edge) {}
  std::string text_;
  std::optional<Comparison> edge_;
};

struct Diff {
  // This diff node corresponds to an entity that is reportable, if it or any of
  // its children (excluding reportable ones) has changed.
  bool holds_changes = false;
  // This diff node contains a local (non-recursive) change.
  bool has_changes = false;
  std::vector<DiffDetail> details;

  void Add(const std::string& text,
           const std::optional<Comparison>& comparison) {
    details.emplace_back(text, comparison);
  }
};

struct Result {
  // Used when two nodes cannot be meaningfully compared.
  Result& MarkIncomparable() {
    equals_ = false;
    diff_.has_changes = true;
    return *this;
  }

  // Used when a node attribute has changed.
  void AddNodeDiff(const std::string& text) {
    equals_ = false;
    diff_.has_changes = true;
    diff_.Add(text, {});
  }

  // Used when a node attribute may have changed.
  template <typename T>
  void MaybeAddNodeDiff(
      const std::string& text, const T& before, const T& after) {
    if (before != after) {
      std::ostringstream os;
      os << text << " changed from " << before << " to " << after;
      AddNodeDiff(os.str());
    }
  }

  // Used when a node attribute may have changed, lazy version.
  template <typename T>
  void MaybeAddNodeDiff(const std::function<void(std::ostream&)>& text,
                        const T& before, const T& after) {
    if (before != after) {
      std::ostringstream os;
      text(os);
      os << " changed from " << before << " to " << after;
      AddNodeDiff(os.str());
    }
  }

  // Used when node attributes are optional values.
  template <typename T>
  void MaybeAddNodeDiff(const std::string& text, const std::optional<T>& before,
                        const std::optional<T>& after) {
    if (before && after) {
      MaybeAddNodeDiff(text, *before, *after);
    } else if (before) {
      std::ostringstream os;
      os << text << ' ' << *before << " was removed";
      AddNodeDiff(os.str());
    } else if (after) {
      std::ostringstream os;
      os << text << ' ' << *after << " was added";
      AddNodeDiff(os.str());
    }
  }

  // Used when an edge has been removed or added.
  void AddEdgeDiff(const std::string& text, const Comparison& comparison) {
    equals_ = false;
    diff_.Add(text, {comparison});
  }

  // Used when an edge to a possible comparison is present.
  void MaybeAddEdgeDiff(const std::string& text,
                        const std::pair<bool, std::optional<Comparison>>& p) {
    equals_ &= p.first;
    const auto& comparison = p.second;
    if (comparison) {
      diff_.Add(text, comparison);
    }
  }

  // Used when an edge to a possible comparison is present, lazy version.
  void MaybeAddEdgeDiff(const std::function<void(std::ostream&)>& text,
                        const std::pair<bool, std::optional<Comparison>>& p) {
    equals_ &= p.first;
    const auto& comparison = p.second;
    if (comparison) {
      std::ostringstream os;
      text(os);
      diff_.Add(os.str(), comparison);
    }
  }

  bool equals_ = true;
  Diff diff_;
};

struct HashComparison {
  size_t operator()(const Comparison& comparison) const {
    size_t seed = 0;
    const std::hash<std::optional<Id>> h;
    combine_hash(seed, h(comparison.first));
    combine_hash(seed, h(comparison.second));
    return seed;
  }
  static void combine_hash(size_t& seed, size_t hash) {
    seed ^= hash + 0x9e3779b97f4a7c15 + (seed << 12) + (seed >> 4);
  }
};

using Outcomes = std::unordered_map<Comparison, Diff, HashComparison>;

struct MatchingKey {
  explicit MatchingKey(const Graph& graph) : graph(graph) {}
  std::string operator()(Id id);
  std::string operator()(const BaseClass&);
  std::string operator()(const Method&);
  std::string operator()(const Member&);
  std::string operator()(const StructUnion&);
  template <typename Node>
  std::string operator()(const Node&);
  const Graph& graph;
};

std::pair<Id, std::vector<std::string>> ResolveTypedefs(
    const Graph& graph, Id id);

struct ResolveTypedef {
  ResolveTypedef(const Graph& graph, Id& id, std::vector<std::string>& names)
      : graph(graph), id(id), names(names) {}
  bool operator()(const Typedef&);
  template <typename Node>
  bool operator()(const Node&);

  const Graph& graph;
  Id& id;
  std::vector<std::string>& names;
};

using Qualifiers = std::set<Qualifier>;

// Separate qualifiers from underlying type.
//
// The caller must always be prepared to receive a different type as qualifiers
// are sometimes discarded.
std::pair<Id, Qualifiers> ResolveQualifiers(const Graph& graph, Id id);

struct ResolveQualifier {
  ResolveQualifier(const Graph& graph, Id& id, Qualifiers& qualifiers)
      : graph(graph), id(id), qualifiers(qualifiers) {}
  bool operator()(const Qualified&);
  bool operator()(const Array&);
  bool operator()(const Function&);
  template <typename Node>
  bool operator()(const Node&);

  const Graph& graph;
  Id& id;
  Qualifiers& qualifiers;
};

struct Compare {
  Compare(Runtime& runtime, const Graph& graph, const Ignore& ignore)
      : graph(graph), ignore(ignore),
        queried(runtime, "compare.queried"),
        already_compared(runtime, "compare.already_compared"),
        being_compared(runtime, "compare.being_compared"),
        really_compared(runtime, "compare.really_compared"),
        equivalent(runtime, "compare.equivalent"),
        inequivalent(runtime, "compare.inequivalent"),
        scc_size(runtime, "compare.scc_size") {}
  std::pair<bool, std::optional<Comparison>>  operator()(Id id1, Id id2);

  Comparison Removed(Id id);
  Comparison Added(Id id);
  void CompareDefined(bool defined1, bool defined2, Result& result);

  Result Mismatch();
  Result operator()(const Special&, const Special&);
  Result operator()(const PointerReference&, const PointerReference&);
  Result operator()(const PointerToMember&, const PointerToMember&);
  Result operator()(const Typedef&, const Typedef&);
  Result operator()(const Qualified&, const Qualified&);
  Result operator()(const Primitive&, const Primitive&);
  Result operator()(const Array&, const Array&);
  Result operator()(const BaseClass&, const BaseClass&);
  Result operator()(const Method&, const Method&);
  Result operator()(const Member&, const Member&);
  Result operator()(const StructUnion&, const StructUnion&);
  Result operator()(const Enumeration&, const Enumeration&);
  Result operator()(const Function&, const Function&);
  Result operator()(const ElfSymbol&, const ElfSymbol&);
  Result operator()(const Interface&, const Interface&);

  const Graph& graph;
  const Ignore ignore;
  std::unordered_map<Comparison, bool, HashComparison> known;
  Outcomes outcomes;
  Outcomes provisional;
  SCC<Comparison, HashComparison> scc;
  Counter queried;
  Counter already_compared;
  Counter being_compared;
  Counter really_compared;
  Counter equivalent;
  Counter inequivalent;
  Histogram scc_size;
};

}  // namespace stg

#endif  // STG_COMPARISON_H_
