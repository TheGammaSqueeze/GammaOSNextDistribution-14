// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2023 Google LLC
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
// Author: Ignes Simeonova

#include "abigail_reader.h"

#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <ios>
#include <map>
#include <memory>
#include <optional>
#include <set>
#include <sstream>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

#include <libxml/parser.h>
#include <libxml/tree.h>
#include "error.h"
#include "file_descriptor.h"
#include "graph.h"
#include "runtime.h"
#include "scope.h"
#include "type_normalisation.h"

namespace stg {
namespace abixml {

namespace {

// Cast a libxml string to C string and present it as a string_view.
std::string_view FromLibxml(const xmlChar* str) {
  return reinterpret_cast<const char*>(str);
}

// Cast a C string to a libxml string.
const xmlChar* ToLibxml(const char* str) {
  return reinterpret_cast<const xmlChar*>(str);
}

// Get the name of an XML element.
std::string_view GetName(xmlNodePtr element) {
  return FromLibxml(element->name);
}

void CheckName(const char* name, xmlNodePtr element) {
  const auto element_name = GetName(element);
  if (element_name != name) {
    Die() << "expected element '" << name
          << "' but got '" << element_name << "'";
  }
}

xmlNodePtr Child(xmlNodePtr node) {
  return node->children;
}

xmlNodePtr Next(xmlNodePtr node) {
  return node->next;
}

xmlNodePtr GetOnlyChild(xmlNodePtr element) {
  const xmlNodePtr child = Child(element);
  if (child == nullptr || Next(child) != nullptr) {
    Die() << "element '" << GetName(element) << "' without exactly one child";
  }
  return child;
}

// Get an optional attribute.
std::optional<std::string> GetAttribute(xmlNodePtr node, const char* name) {
  std::optional<std::string> result;
  xmlChar* attribute = xmlGetProp(node, ToLibxml(name));
  if (attribute) {
    result.emplace(FromLibxml(attribute));
    xmlFree(attribute);
  }
  return result;
}

// Get an attribute.
std::string GetAttributeOrDie(xmlNodePtr node, const char* name) {
  xmlChar* attribute = xmlGetProp(node, ToLibxml(name));
  if (!attribute) {
    Die() << "element '" << GetName(node)
          << "' missing attribute '" << name << "'";
  }
  const std::string result(FromLibxml(attribute));
  xmlFree(attribute);
  return result;
}

// Set an attribute value.
void SetAttribute(xmlNodePtr node, const char* name, const std::string& value) {
  xmlSetProp(node, ToLibxml(name), ToLibxml(value.c_str()));
}

// Unset an attribute value.
void UnsetAttribute(xmlNodePtr node, const char* name) {
  xmlUnsetProp(node, ToLibxml(name));
}

// Remove a node and free its storage.
void RemoveNode(xmlNodePtr node) {
  xmlUnlinkNode(node);
  xmlFreeNode(node);
}

// Move a node to be the last child of another.
void MoveNode(xmlNodePtr node, xmlNodePtr destination) {
  xmlUnlinkNode(node);
  xmlAddChild(destination, node);
}

template <typename T>
std::optional<T> Parse(const std::string& value) {
  T result;
  std::istringstream is(value);
  is >> std::noskipws >> result;
  if (is && is.eof()) {
    return {result};
  }
  return {};
}

template <>
std::optional<bool> Parse<bool>(const std::string& value) {
  if (value == "yes") {
    return {true};
  } else if (value == "no") {
    return {false};
  }
  return {};
}

template <>
std::optional<ElfSymbol::SymbolType> Parse<ElfSymbol::SymbolType>(
    const std::string& value) {
  if (value == "object-type") {
    return {ElfSymbol::SymbolType::OBJECT};
  } else if (value == "func-type") {
    return {ElfSymbol::SymbolType::FUNCTION};
  } else if (value == "common-type") {
    return {ElfSymbol::SymbolType::COMMON};
  } else if (value == "tls-type") {
    return {ElfSymbol::SymbolType::TLS};
  } else if (value == "gnu-ifunc-type") {
    return {ElfSymbol::SymbolType::GNU_IFUNC};
  }
  return {};
}

template <>
std::optional<ElfSymbol::Binding> Parse<ElfSymbol::Binding>(
    const std::string& value) {
  if (value == "global-binding") {
    return {ElfSymbol::Binding::GLOBAL};
  } else if (value == "local-binding") {
    return {ElfSymbol::Binding::LOCAL};
  } else if (value == "weak-binding") {
    return {ElfSymbol::Binding::WEAK};
  } else if (value == "gnu-unique-binding") {
    return {ElfSymbol::Binding::GNU_UNIQUE};
  }
  return {};
}

template <>
std::optional<ElfSymbol::Visibility> Parse<ElfSymbol::Visibility>(
    const std::string& value) {
  if (value == "default-visibility") {
    return {ElfSymbol::Visibility::DEFAULT};
  } else if (value == "protected-visibility") {
    return {ElfSymbol::Visibility::PROTECTED};
  } else if (value == "hidden-visibility") {
    return {ElfSymbol::Visibility::HIDDEN};
  } else if (value == "internal-visibility") {
    return {ElfSymbol::Visibility::INTERNAL};
  }
  return {};
}

template <>
std::optional<ElfSymbol::CRC> Parse<ElfSymbol::CRC>(const std::string& value) {
  uint32_t number;
  std::istringstream is(value);
  is >> std::noskipws >> std::hex >> number;
  if (is && is.eof()) {
    return std::make_optional<ElfSymbol::CRC>(number);
  }
  return std::nullopt;
}

template <typename T>
T GetParsedValueOrDie(xmlNodePtr element, const char* name,
                      const std::string& value, const std::optional<T>& parse) {
  if (parse) {
    return *parse;
  }
  Die() << "element '" << GetName(element)
        << "' has attribute '" << name
        << "' with bad value '" << value << "'";
}

template <typename T>
T ReadAttributeOrDie(xmlNodePtr element, const char* name) {
  const auto value = GetAttributeOrDie(element, name);
  return GetParsedValueOrDie(element, name, value, Parse<T>(value));
}

template <typename T>
std::optional<T> ReadAttribute(xmlNodePtr element, const char* name) {
  const auto value = GetAttribute(element, name);
  if (value) {
    return {GetParsedValueOrDie(element, name, *value, Parse<T>(*value))};
  }
  return {};
}

template <typename T>
T ReadAttribute(xmlNodePtr element, const char* name, const T& default_value) {
  const auto value = GetAttribute(element, name);
  if (value) {
    return GetParsedValueOrDie(element, name, *value, Parse<T>(*value));
  }
  return default_value;
}

template <typename T>
T ReadAttribute(xmlNodePtr element, const char* name,
                std::function<std::optional<T>(const std::string&)> parse) {
  const auto value = GetAttributeOrDie(element, name);
  return GetParsedValueOrDie(element, name, value, parse(value));
}

// Remove non-element nodes, recursively.
//
// This simplifies subsequent manipulation. This should only remove comment,
// text and possibly CDATA nodes.
void StripNonElements(xmlNodePtr node) {
  switch (node->type) {
    case XML_COMMENT_NODE:
    case XML_TEXT_NODE:
    case XML_CDATA_SECTION_NODE:
      RemoveNode(node);
      break;
    case XML_ELEMENT_NODE: {
      xmlNodePtr child = Child(node);
      while (child) {
        const xmlNodePtr next = Next(child);
        StripNonElements(child);
        child = next;
      }
      break;
    }
    default:
      Die() << "unexpected XML node type: " << node->type;
  }
}

// Determine whether one XML element is a subtree of another, and optionally,
// actually equal to it.
bool SubOrEqualTree(bool also_equal, xmlNodePtr left, xmlNodePtr right) {
  // Node names must match.
  const auto left_name = GetName(left);
  const auto right_name = GetName(right);
  if (left_name != right_name) {
    return false;
  }

  // Attributes may be missing on the left, but must match otherwise.
  size_t left_attributes = 0;
  for (auto* p = left->properties; p; p = p->next) {
    ++left_attributes;
    const auto attribute = FromLibxml(p->name);
    const char* attribute_name = attribute.data();
    const auto left_value = GetAttributeOrDie(left, attribute_name);
    const auto right_value = GetAttribute(right, attribute_name);
    if (!right_value || left_value != right_value.value()) {
      return false;
    }
  }
  // To also be equal, we just need to check the counts are the same.
  if (also_equal) {
    size_t right_attributes = 0;
    for (auto* p = right->properties; p; p = p->next) {
      ++right_attributes;
    }
    if (left_attributes != right_attributes) {
      return false;
    }
  }

  // The left subelements must be a subsequence of the right ones and to also be
  // equal, we must not have skipped any right ones.
  xmlNodePtr left_child = Child(left);
  xmlNodePtr right_child = Child(right);
  while (left_child != nullptr && right_child != nullptr) {
    if (SubOrEqualTree(also_equal, left_child, right_child)) {
      left_child = Next(left_child);
    } else if (also_equal) {
      return false;
    }
    right_child = Next(right_child);
  }
  return left_child == nullptr && (right_child == nullptr || !also_equal);
}

}  // namespace

// Determine whether one XML element is a subtree of another.
bool SubTree(xmlNodePtr left, xmlNodePtr right) {
  return SubOrEqualTree(false, left, right);
}

// Determine whether one XML element is the same as another.
bool EqualTree(xmlNodePtr left, xmlNodePtr right) {
  return SubOrEqualTree(true, left, right);
}

// Find a maximal XML element if one exists.
std::optional<size_t> MaximalTree(const std::vector<xmlNodePtr>& nodes) {
  if (nodes.empty()) {
    return std::nullopt;
  }

  // Find a potentially maximal candidate by scanning through and retaining the
  // new node if it's a supertree of the current candidate.
  const auto count = nodes.size();
  std::vector<bool> ok(count);
  size_t candidate = 0;
  ok[candidate] = true;
  for (size_t ix = 1; ix < count; ++ix) {
    if (SubTree(nodes[candidate], nodes[ix])) {
      candidate = ix;
      ok[candidate] = true;
    }
  }

  // Verify the candidate is indeed maximal by comparing it with the nodes not
  // already known to be subtrees of it.
  const auto& candidate_node = nodes[candidate];
  for (size_t ix = 0; ix < count; ++ix) {
    const auto& node = nodes[ix];
    if (!ok[ix] && !SubTree(node, candidate_node)) {
      return std::nullopt;
    }
  }

  return std::make_optional(candidate);
}

namespace {

// Check if string_view is in an array.
template<size_t N>
bool Contains(const std::array<std::string_view, N>& haystack,
              std::string_view needle) {
  return std::find(haystack.begin(), haystack.end(), needle) != haystack.end();
}

// Remove source location attributes.
//
// This simplifies element comparison later.
void StripLocationInfo(xmlNodePtr node) {
  static const std::array<std::string_view, 7> has_location_info = {
    "class-decl",
    "enum-decl",
    "function-decl",
    "parameter",
    "typedef-decl",
    "union-decl",
    "var-decl"
  };

  if (Contains(has_location_info, GetName(node))) {
    UnsetAttribute(node, "filepath");
    UnsetAttribute(node, "line");
    UnsetAttribute(node, "column");
  }
  for (auto* child = Child(node); child; child = Next(child)) {
    StripLocationInfo(child);
  }
}

// Remove access attribute.
//
// This simplifies element comparison later in a very specific way: libabigail
// (possibly older versions) uses the access specifier for the type it's trying
// to "emit in scope", even for its containing types, making deduplicating types
// trickier. We don't care about access anyway, so just remove it everywhere.
void StripAccess(xmlNodePtr node) {
  static const std::array<std::string_view, 5> has_access = {
    "base-class",
    "data-member",
    "member-function",
    "member-template",
    "member-type",
  };

  if (Contains(has_access, GetName(node))) {
    UnsetAttribute(node, "access");
  }
  for (auto* child = Child(node); child; child = Next(child)) {
    StripAccess(child);
  }
}

// Elements corresponding to named types that can be anonymous or marked as
// unreachable by libabigail, so user-defined types, excepting typedefs.
const std::array<std::string_view, 3> kNamedTypes = {
  "class-decl",
  "enum-decl",
  "union-decl",
};

// Remove attributes emitted by abidw --load-all-types.
//
// With this invocation and if any user-defined types are deemed unreachable,
// libabigail will output a tracking-non-reachable-types attribute on top-level
// elements and an is-non-reachable attribute on each such type element.
//
// We have our own graph-theoretic notion of reachability and these attributes
// have no ABI relevance and can interfere with element comparisons.
void StripReachabilityAttributes(xmlNodePtr node) {
  const auto node_name = GetName(node);

  if (node_name == "abi-corpus-group" || node_name == "abi-corpus") {
    UnsetAttribute(node, "tracking-non-reachable-types");
  } else if (Contains(kNamedTypes, node_name)) {
    UnsetAttribute(node, "is-non-reachable");
  }

  for (auto* child = Child(node); child; child = Next(child)) {
    StripReachabilityAttributes(child);
  }
}

// Fix bad DWARF -> ELF links caused by size zero symbol confusion.
//
// libabigail used to be confused by these sorts of symbols, resulting in
// declarations pointing at the wrong ELF symbols:
//
// 573623: ffffffc0122383c0   256 OBJECT  GLOBAL DEFAULT   33 vm_node_stat
// 573960: ffffffc0122383c0     0 OBJECT  GLOBAL DEFAULT   33 vm_numa_stat
void FixBadDwarfElfLinks(xmlNodePtr root) {
  std::unordered_map<std::string, size_t> elf_links;

  // See which ELF symbol IDs have multiple declarations.
  const std::function<void(xmlNodePtr)> count = [&](xmlNodePtr node) {
    if (GetName(node) == "var-decl") {
      const auto symbol_id = GetAttribute(node, "elf-symbol-id");
      if (symbol_id) {
        ++elf_links[symbol_id.value()];
      }
    }

    for (auto* child = Child(node); child; child = Next(child)) {
      count(child);
    }
  };
  count(root);

  // Fix up likely bad links from DWARF declaration to ELF symbol.
  const std::function<void(xmlNodePtr)> fix = [&](xmlNodePtr node) {
    if (GetName(node) == "var-decl") {
      const auto name = GetAttributeOrDie(node, "name");
      const auto mangled_name = GetAttribute(node, "mangled-name");
      const auto symbol_id = GetAttribute(node, "elf-symbol-id");
      if (mangled_name && symbol_id && name != symbol_id.value()
          && elf_links[symbol_id.value()] > 1) {
        if (mangled_name.value() == name) {
          Warn() << "fixing up ELF symbol for '" << name
                 << "' (was '" << symbol_id.value() << "')";
          SetAttribute(node, "elf-symbol-id", name);
        } else if (mangled_name.value() == symbol_id.value()) {
          Warn() << "fixing up mangled name and ELF symbol for '" << name
                 << "' (was '" << symbol_id.value() << "')";
          SetAttribute(node, "mangled-name", name);
          SetAttribute(node, "elf-symbol-id", name);
        }
      }
    }

    for (auto* child = Child(node); child; child = Next(child)) {
      fix(child);
    }
  };
  fix(root);
}

// Tidy anonymous types in various ways.
//
// 1. Normalise anonymous type names by dropping the name attribute.
//
// Anonymous type names take the form __anonymous_foo__N where foo is one of
// enum, struct or union and N is an optional numerical suffix. We don't care
// about these names but they may cause trouble when comparing elements.
//
// 2. Reanonymise anonymous types that have been given names.
//
// At some point abidw changed its behaviour given an anonymous with a naming
// typedef. In addition to linking the typedef and type in both directions, the
// code now gives (some) anonymous types the same name as the typedef. This
// misrepresents the original types.
//
// Such types should be anonymous. We set is-anonymous and drop the name.
//
// 3. Discard naming typedef backlinks.
//
// The attribute naming-typedef-id is a backwards link from an anonymous type to
// the typedef that refers to it.
//
// We don't care about these attributes and they may cause comparison issues.
void TidyAnonymousTypes(xmlNodePtr node) {
  if (Contains(kNamedTypes, GetName(node))) {
    const bool is_anon = ReadAttribute<bool>(node, "is-anonymous", false);
    const auto naming_attribute = GetAttribute(node, "naming-typedef-id");
    if (is_anon) {
      UnsetAttribute(node, "name");
    } else if (naming_attribute) {
      SetAttribute(node, "is-anonymous", "yes");
      UnsetAttribute(node, "name");
    }
    if (naming_attribute) {
      UnsetAttribute(node, "naming-typedef-id");
    }
  }

  for (auto* child = Child(node); child; child = Next(child)) {
    TidyAnonymousTypes(child);
  }
}

// Remove duplicate members.
void RemoveDuplicateMembers(xmlNodePtr root) {
  std::vector<xmlNodePtr> types;

  // find all structs and unions
  std::function<void(xmlNodePtr)> dfs = [&](xmlNodePtr node) {
    const auto node_name = GetName(node);
    // preorder in case we delete a nested element
    for (auto* child = Child(node); child; child = Next(child)) {
      dfs(child);
    }
    if (node_name == "class-decl" || node_name == "union-decl") {
      types.push_back(node);
    }
  };
  dfs(root);

  for (const auto& node : types) {
    // partition members by node name
    std::map<std::string_view, std::vector<xmlNodePtr>> member_map;
    for (auto* child = Child(node); child; child = Next(child)) {
      member_map[GetName(child)].push_back(child);
    }
    // for each kind of member...
    for (auto& [name, members] : member_map) {
      // ... remove identical duplicate members - O(n^2)
      for (size_t i = 0; i < members.size(); ++i) {
        xmlNodePtr& i_node = members[i];
        bool duplicate = false;
        for (size_t j = 0; j < i; ++j) {
          const xmlNodePtr& j_node = members[j];
          if (j_node != nullptr && EqualTree(i_node, j_node)) {
            duplicate = true;
            break;
          }
        }
        if (duplicate) {
          RemoveNode(i_node);
          i_node = nullptr;
        }
      }
    }
  }
}

// Eliminate non-conflicting / report conflicting duplicate definitions.
//
// XML elements representing types are sometimes emitted multiple times,
// identically. Also, member typedefs are sometimes emitted separately from
// their types, resulting in duplicate XML fragments.
//
// Both these issues can be resolved by first detecting duplicate occurrences of
// a given type id and then checking to see if there's an instance that subsumes
// the others, which can then be eliminated.
//
// This function eliminates exact type duplicates and duplicates where there is
// at least one maximal definition. It can report the remaining duplicate
// definitions.
//
// If a type has duplicate definitions in multiple namespace scopes or
// definitions with different effective names, these are considered to be
// *conflicting* duplicate definitions. TODO: update text
void HandleDuplicateTypes(xmlNodePtr root) {
  // Convenience typedef referring to a namespace scope.
  using namespace_scope = std::vector<std::string>;
  // map of type-id to pair of set of namespace scopes and vector of
  // xmlNodes
  std::unordered_map<
      std::string,
      std::pair<
          std::set<namespace_scope>,
          std::vector<xmlNodePtr>>> types;
  namespace_scope namespaces;

  // find all type occurrences
  std::function<void(xmlNodePtr)> dfs = [&](xmlNodePtr node) {
    const auto node_name = GetName(node);
    std::optional<std::string> namespace_name;
    if (node_name == "namespace-decl") {
      namespace_name = GetAttribute(node, "name");
    }
    if (namespace_name) {
      namespaces.push_back(namespace_name.value());
    }
    if (node_name == "abi-corpus-group"
        || node_name == "abi-corpus"
        || node_name == "abi-instr"
        || namespace_name) {
      for (auto* child = Child(node); child; child = Next(child)) {
        dfs(child);
      }
    } else {
      const auto id = GetAttribute(node, "id");
      if (id) {
        auto& info = types[id.value()];
        info.first.insert(namespaces);
        info.second.push_back(node);
      }
    }
    if (namespace_name) {
      namespaces.pop_back();
    }
  };
  dfs(root);

  for (const auto& [id, scopes_and_definitions] : types) {
    const auto& [scopes, definitions] = scopes_and_definitions;

    if (scopes.size() > 1) {
      Warn() << "conflicting scopes found for type '" << id << '\'';
      continue;
    }

    const auto possible_maximal = MaximalTree(definitions);
    if (possible_maximal) {
      // Remove all but the maximal definition.
      const size_t maximal = possible_maximal.value();
      for (size_t ix = 0; ix < definitions.size(); ++ix) {
        if (ix != maximal) {
          RemoveNode(definitions[ix]);
        }
      }
      continue;
    }

    // As a rare alternative, check for a stray anonymous member that has been
    // separated from the main definition.
    size_t strays = 0;
    std::optional<size_t> stray;
    std::optional<size_t> non_stray;
    for (size_t ix = 0; ix < definitions.size(); ++ix) {
      auto node = definitions[ix];
      auto member = Child(node);
      if (member && !Next(member) && GetName(member) == "data-member") {
        auto decl = Child(member);
        if (decl && !Next(decl) && GetName(decl) == "var-decl") {
          auto name = GetAttribute(decl, "name");
          if (name && name.value().empty()) {
            ++strays;
            stray = ix;
            continue;
          }
        }
      }
      non_stray = ix;
    }
    if (strays + 1 == definitions.size() && stray.has_value()
        && non_stray.has_value()) {
      const auto stray_index = stray.value();
      const auto non_stray_index = non_stray.value();
      bool good = true;
      for (size_t ix = 0; ix < definitions.size(); ++ix) {
        if (ix == stray_index || ix == non_stray_index) {
          continue;
        }
        if (EqualTree(definitions[stray_index], definitions[ix])) {
          // it doesn't hurt if we remove exact duplicates and then fail
          RemoveNode(definitions[ix]);
        } else {
          good = false;
          break;
        }
      }
      if (good) {
        MoveNode(Child(definitions[stray_index]), definitions[non_stray_index]);
        RemoveNode(definitions[stray_index]);
        continue;
      }
    }

    Warn() << "unresolvable duplicate definitions found for type '" << id
           << '\'';
  }
}

}  // namespace

// Remove XML nodes and attributes that are neither used or wanted.
void Clean(xmlNodePtr root) {
  // Strip non-element nodes to simplify other operations.
  StripNonElements(root);

  // Strip location information.
  StripLocationInfo(root);

  // Strip access.
  StripAccess(root);

  // Strip reachability attributes.
  StripReachabilityAttributes(root);
}

namespace {

// Transform XML elements to improve their semantics.
void Tidy(xmlNodePtr root) {
  // Fix bad ELF symbol links
  FixBadDwarfElfLinks(root);

  // Normalise anonymous type names.
  // Reanonymise anonymous types.
  // Discard naming typedef backlinks.
  TidyAnonymousTypes(root);

  // Remove duplicate members.
  RemoveDuplicateMembers(root);

  // Eliminate complete duplicates and extra fragments of types.
  // Report conflicting duplicate defintions.
  // Record whether there are conflicting duplicate definitions.
  HandleDuplicateTypes(root);
}

std::optional<uint64_t> ParseLength(const std::string& value) {
  if (value == "infinite" || value == "unknown") {
    return {0};
  }
  return Parse<uint64_t>(value);
}

std::optional<PointerReference::Kind> ParseReferenceKind(
    const std::string& value) {
  if (value == "lvalue") {
    return {PointerReference::Kind::LVALUE_REFERENCE};
  } else if (value == "rvalue") {
    return {PointerReference::Kind::RVALUE_REFERENCE};
  }
  return {};
}

}  // namespace

Abigail::Abigail(Graph& graph) : graph_(graph) {}

Id Abigail::GetNode(const std::string& type_id) {
  const auto [it, inserted] = type_ids_.insert({type_id, Id(0)});
  if (inserted) {
    it->second = graph_.Allocate();
  }
  return it->second;
}

Id Abigail::GetEdge(xmlNodePtr element) {
  return GetNode(GetAttributeOrDie(element, "type-id"));
}

Id Abigail::GetVariadic() {
  if (!variadic_) {
    variadic_ = {graph_.Add<Special>(Special::Kind::VARIADIC)};
  }
  return *variadic_;
}

Function Abigail::MakeFunctionType(xmlNodePtr function) {
  std::vector<Id> parameters;
  std::optional<Id> return_type;
  for (auto* child = Child(function); child; child = Next(child)) {
    const auto child_name = GetName(child);
    if (return_type) {
      Die() << "unexpected element after return-type";
    }
    if (child_name == "parameter") {
      const auto is_variadic = ReadAttribute<bool>(child, "is-variadic", false);
      parameters.push_back(is_variadic ? GetVariadic() : GetEdge(child));
    } else if (child_name == "return") {
      return_type = {GetEdge(child)};
    } else {
      Die() << "unrecognised " << GetName(function)
            << " child element '" << child_name << "'";
    }
  }
  if (!return_type) {
    Die() << "missing return-type";
  }
  return Function(*return_type, parameters);
}

Id Abigail::ProcessRoot(xmlNodePtr root) {
  Clean(root);
  Tidy(root);
  const auto name = GetName(root);
  if (name == "abi-corpus-group") {
    ProcessCorpusGroup(root);
  } else if (name == "abi-corpus") {
    ProcessCorpus(root);
  } else {
    Die() << "unrecognised root element '" << name << "'";
  }
  for (const auto& [type_id, id] : type_ids_) {
    if (!graph_.Is(id)) {
      Warn() << "no definition found for type '" << type_id << "'";
    }
  }
  const Id id = BuildSymbols();
  RemoveUselessQualifiers(graph_, id);
  return id;
}

void Abigail::ProcessCorpusGroup(xmlNodePtr group) {
  for (auto* corpus = Child(group); corpus; corpus = Next(corpus)) {
    CheckName("abi-corpus", corpus);
    ProcessCorpus(corpus);
  }
}

void Abigail::ProcessCorpus(xmlNodePtr corpus) {
  for (auto* element = Child(corpus); element; element = Next(element)) {
    const auto name = GetName(element);
    if (name == "elf-function-symbols" || name == "elf-variable-symbols") {
      ProcessSymbols(element);
    } else if (name == "elf-needed") {
      // ignore this
    } else if (name == "abi-instr") {
      ProcessInstr(element);
    } else {
      Die() << "unrecognised abi-corpus child element '" << name << "'";
    }
  }
}

void Abigail::ProcessSymbols(xmlNodePtr symbols) {
  for (auto* element = Child(symbols); element; element = Next(element)) {
    CheckName("elf-symbol", element);
    ProcessSymbol(element);
  }
}

void Abigail::ProcessSymbol(xmlNodePtr symbol) {
  // Symbol processing is done in two parts. In this first part, we parse just
  // enough XML attributes to generate a symbol id and determine any aliases.
  // Symbol ids in this format can be found in elf-symbol alias attributes and
  // in {var,function}-decl elf-symbol-id attributes.
  const auto name = GetAttributeOrDie(symbol, "name");
  const auto version =
      ReadAttribute<std::string>(symbol, "version", std::string());
  const bool is_default_version =
      ReadAttribute<bool>(symbol, "is-default-version", false);
  const auto alias = GetAttribute(symbol, "alias");

  std::string elf_symbol_id = name;
  std::optional<ElfSymbol::VersionInfo> version_info;
  if (!version.empty()) {
    version_info = ElfSymbol::VersionInfo{is_default_version, version};
    elf_symbol_id += VersionInfoToString(*version_info);
  }

  Check(symbol_info_map_
            .emplace(elf_symbol_id, SymbolInfo{name, version_info, symbol})
            .second)
      << "multiple symbols with id " << elf_symbol_id;

  if (alias) {
    std::istringstream is(*alias);
    std::string item;
    while (std::getline(is, item, ',')) {
      Check(alias_to_main_.insert({item, elf_symbol_id}).second)
          << "multiple aliases with id " << elf_symbol_id;
    }
  }
}

bool Abigail::ProcessUserDefinedType(std::string_view name, Id id,
                                     xmlNodePtr decl) {
  if (name == "typedef-decl") {
    ProcessTypedef(id, decl);
  } else if (name == "class-decl") {
    ProcessStructUnion(id, true, decl);
  } else if (name == "union-decl") {
    ProcessStructUnion(id, false, decl);
  } else if (name == "enum-decl") {
    ProcessEnum(id, decl);
  } else {
    return false;
  }
  return true;
}

void Abigail::ProcessScope(xmlNodePtr scope) {
  for (auto* element = Child(scope); element; element = Next(element)) {
    const auto name = GetName(element);
    const auto type_id = GetAttribute(element, "id");
    // all type elements have "id", all non-types do not
    if (type_id) {
      const auto id = GetNode(*type_id);
      if (graph_.Is(id)) {
        Warn() << "duplicate definition of type '" << *type_id << '\'';
        continue;
      }
      if (name == "function-type") {
        ProcessFunctionType(id, element);
      } else if (name == "pointer-type-def") {
        ProcessPointer(id, true, element);
      } else if (name == "reference-type-def") {
        ProcessPointer(id, false, element);
      } else if (name == "qualified-type-def") {
        ProcessQualified(id, element);
      } else if (name == "array-type-def") {
        ProcessArray(id, element);
      } else if (name == "type-decl") {
        ProcessTypeDecl(id, element);
      } else if (!ProcessUserDefinedType(name, id, element)) {
        Die() << "bad abi-instr type child element '" << name << "'";
      }
    } else {
      if (name == "var-decl") {
        ProcessDecl(true, element);
      } else if (name == "function-decl") {
        ProcessDecl(false, element);
      } else if (name == "namespace-decl") {
        ProcessNamespace(element);
      } else {
        Die() << "bad abi-instr non-type child element '" << name << "'";
      }
    }
  }
}

void Abigail::ProcessInstr(xmlNodePtr instr) {
  ProcessScope(instr);
}

void Abigail::ProcessNamespace(xmlNodePtr scope) {
  const auto name = GetAttributeOrDie(scope, "name");
  const PushScopeName push_scope_name(scope_name_, "namespace", name);
  ProcessScope(scope);
}

Id Abigail::ProcessDecl(bool is_variable, xmlNodePtr decl) {
  const auto name = scope_name_ + GetAttributeOrDie(decl, "name");
  const auto symbol_id = GetAttribute(decl, "elf-symbol-id");
  const auto type = is_variable ? GetEdge(decl)
                                : graph_.Add<Function>(MakeFunctionType(decl));
  if (symbol_id) {
    // There's a link to an ELF symbol.
    const auto [it, inserted] = symbol_id_and_full_name_.emplace(
        *symbol_id, std::make_pair(type, name));
    if (!inserted) {
      Die() << "duplicate type for '" << *symbol_id << "'";
    }
  }
  return type;
}

void Abigail::ProcessFunctionType(Id id, xmlNodePtr function) {
  graph_.Set<Function>(id, MakeFunctionType(function));
}

void Abigail::ProcessTypedef(Id id, xmlNodePtr type_definition) {
  const auto name = scope_name_ + GetAttributeOrDie(type_definition, "name");
  const auto type = GetEdge(type_definition);
  graph_.Set<Typedef>(id, name, type);
}

void Abigail::ProcessPointer(Id id, bool is_pointer, xmlNodePtr pointer) {
  const auto type = GetEdge(pointer);
  const auto kind = is_pointer ? PointerReference::Kind::POINTER
                               : ReadAttribute<PointerReference::Kind>(
                                     pointer, "kind", &ParseReferenceKind);
  graph_.Set<PointerReference>(id, kind, type);
}

void Abigail::ProcessQualified(Id id, xmlNodePtr qualified) {
  std::vector<Qualifier> qualifiers;
  // Do these in reverse order so we get CVR ordering.
  if (ReadAttribute<bool>(qualified, "restrict", false)) {
    qualifiers.push_back(Qualifier::RESTRICT);
  }
  if (ReadAttribute<bool>(qualified, "volatile", false)) {
    qualifiers.push_back(Qualifier::VOLATILE);
  }
  if (ReadAttribute<bool>(qualified, "const", false)) {
    qualifiers.push_back(Qualifier::CONST);
  }
  Check(!qualifiers.empty()) << "qualified-type-def has no qualifiers";
  // Handle multiple qualifiers by unconditionally adding as new nodes all but
  // the last qualifier which is set into place.
  auto type = GetEdge(qualified);
  auto count = qualifiers.size();
  for (auto qualifier : qualifiers) {
    --count;
    const Qualified node(qualifier, type);
    if (count) {
      type = graph_.Add<Qualified>(node);
    } else {
      graph_.Set<Qualified>(id, node);
    }
  }
}

void Abigail::ProcessArray(Id id, xmlNodePtr array) {
  std::vector<size_t> dimensions;
  for (auto* child = Child(array); child; child = Next(child)) {
    CheckName("subrange", child);
    const auto length = ReadAttribute<uint64_t>(child, "length", &ParseLength);
    dimensions.push_back(length);
  }
  Check(!dimensions.empty()) << "array-type-def element has no children";
  // int[M][N] means array[M] of array[N] of int
  //
  // We need to chain a bunch of types together:
  //
  // id = array[n] of id = ... = array[n] of id
  //
  // where the first id is the new type in slot ix
  // and the last id is the old type in slot type
  //
  // Use the same approach as for qualifiers.
  auto type = GetEdge(array);
  auto count = dimensions.size();
  for (auto it = dimensions.crbegin(); it != dimensions.crend(); ++it) {
    --count;
    const auto size = *it;
    const Array node(size, type);
    if (count) {
      type = graph_.Add<Array>(node);
    } else {
      graph_.Set<Array>(id, node);
    }
  }
}

void Abigail::ProcessTypeDecl(Id id, xmlNodePtr type_decl) {
  const auto name = scope_name_ + GetAttributeOrDie(type_decl, "name");
  const auto bits = ReadAttribute<size_t>(type_decl, "size-in-bits", 0);
  if (bits % 8) {
    Die() << "size-in-bits is not a multiple of 8";
  }
  const auto bytes = bits / 8;

  if (name == "void") {
    graph_.Set<Special>(id, Special::Kind::VOID);
  } else {
    // libabigail doesn't model encoding at all and we don't want to parse names
    // (which will not always work) in an attempt to reconstruct it.
    graph_.Set<Primitive>(id, name, /* encoding= */ std::nullopt, bytes);
  }
}

void Abigail::ProcessStructUnion(Id id, bool is_struct,
                                 xmlNodePtr struct_union) {
  // Libabigail sometimes reports is-declaration-only but still provides some
  // child elements. So we check both things.
  const bool forward =
      ReadAttribute<bool>(struct_union, "is-declaration-only", false)
      && Child(struct_union) == nullptr;
  const auto kind = is_struct
                    ? StructUnion::Kind::STRUCT
                    : StructUnion::Kind::UNION;
  const bool is_anonymous =
      ReadAttribute<bool>(struct_union, "is-anonymous", false);
  const auto name =
      is_anonymous ? std::string() : GetAttributeOrDie(struct_union, "name");
  const auto full_name =
      is_anonymous ? std::string() : scope_name_ + name;
  const PushScopeName push_scope_name(scope_name_, kind, name);
  if (forward) {
    graph_.Set<StructUnion>(id, kind, full_name);
    return;
  }
  const auto bits = ReadAttribute<size_t>(struct_union, "size-in-bits", 0);
  const auto bytes = (bits + 7) / 8;

  std::vector<Id> base_classes;
  std::vector<Id> methods;
  std::vector<Id> members;
  for (auto* child = Child(struct_union); child; child = Next(child)) {
    const auto child_name = GetName(child);
    if (child_name == "data-member") {
      if (const auto member = ProcessDataMember(is_struct, child)) {
        members.push_back(*member);
      }
    } else if (child_name == "member-type") {
      ProcessMemberType(child);
    } else if (child_name == "base-class") {
      base_classes.push_back(ProcessBaseClass(child));
    } else if (child_name == "member-function") {
      ProcessMemberFunction(methods, child);
    } else {
      Die() << "unrecognised " << kind << "-decl child element '" << child_name
            << "'";
    }
  }

  graph_.Set<StructUnion>(id, kind, full_name, bytes, base_classes, methods,
                          members);
}

void Abigail::ProcessEnum(Id id, xmlNodePtr enumeration) {
  const bool forward =
      ReadAttribute<bool>(enumeration, "is-declaration-only", false);
  const auto name = ReadAttribute<bool>(enumeration, "is-anonymous", false)
                    ? std::string()
                    : scope_name_ + GetAttributeOrDie(enumeration, "name");
  if (forward) {
    graph_.Set<Enumeration>(id, name);
    return;
  }

  const xmlNodePtr underlying = Child(enumeration);
  Check(underlying != nullptr) << "enum-decl has no child elements";
  CheckName("underlying-type", underlying);
  const auto type = GetEdge(underlying);

  std::vector<std::pair<std::string, int64_t>> enumerators;
  for (auto* enumerator = Next(underlying); enumerator;
       enumerator = Next(enumerator)) {
    CheckName("enumerator", enumerator);
    const auto enumerator_name = GetAttributeOrDie(enumerator, "name");
    // libabigail currently supports anything that fits in an int64_t
    const auto enumerator_value =
        ReadAttributeOrDie<int64_t>(enumerator, "value");
    enumerators.emplace_back(enumerator_name, enumerator_value);
  }

  graph_.Set<Enumeration>(id, name, type, enumerators);
}

Id Abigail::ProcessBaseClass(xmlNodePtr base_class) {
  const auto& type = GetEdge(base_class);
  const auto offset =
      ReadAttributeOrDie<size_t>(base_class, "layout-offset-in-bits");
  const auto inheritance = ReadAttribute<bool>(base_class, "is-virtual", false)
                           ? BaseClass::Inheritance::VIRTUAL
                           : BaseClass::Inheritance::NON_VIRTUAL;
  return graph_.Add<BaseClass>(type, offset, inheritance);
}

std::optional<Id> Abigail::ProcessDataMember(bool is_struct,
                                             xmlNodePtr data_member) {
  const xmlNodePtr decl = GetOnlyChild(data_member);
  CheckName("var-decl", decl);
  if (ReadAttribute<bool>(data_member, "static", false)) {
    ProcessDecl(true, decl);
    return {};
  }

  const auto offset = is_struct
                      ? ReadAttributeOrDie<size_t>(data_member,
                                                   "layout-offset-in-bits")
                      : 0;
  const auto name = GetAttributeOrDie(decl, "name");
  const auto type = GetEdge(decl);

  // Note: libabigail does not model member size, yet
  return {graph_.Add<Member>(name, type, offset, 0)};
}

void Abigail::ProcessMemberFunction(std::vector<Id>& methods,
                                    xmlNodePtr method) {
  const xmlNodePtr decl = GetOnlyChild(method);
  CheckName("function-decl", decl);
  // ProcessDecl creates symbol references so must be called unconditionally.
  const auto type = ProcessDecl(false, decl);
  const auto vtable_offset = ReadAttribute<uint64_t>(method, "vtable-offset");
  if (vtable_offset) {
    static const std::string missing = "{missing}";
    const auto mangled_name = ReadAttribute(decl, "mangled-name", missing);
    const auto name = GetAttributeOrDie(decl, "name");
    methods.push_back(
        graph_.Add<Method>(mangled_name, name, vtable_offset.value(), type));
  }
}

void Abigail::ProcessMemberType(xmlNodePtr member_type) {
  const xmlNodePtr decl = GetOnlyChild(member_type);
  const auto type_id = GetAttributeOrDie(decl, "id");
  const auto id = GetNode(type_id);
  if (graph_.Is(id)) {
    Warn() << "duplicate definition of member type '" << type_id << '\'';
    return;
  }
  const auto name = GetName(decl);
  if (!ProcessUserDefinedType(name, id, decl)) {
    Die() << "unrecognised member-type child element '" << name << "'";
  }
}

Id Abigail::BuildSymbol(const SymbolInfo& info,
                        std::optional<Id> type_id,
                        const std::optional<std::string>& name) {
  const xmlNodePtr symbol = info.node;
  const bool is_defined = ReadAttributeOrDie<bool>(symbol, "is-defined");
  const auto crc = ReadAttribute<ElfSymbol::CRC>(symbol, "crc");
  const auto ns = ReadAttribute<std::string>(symbol, "namespace");
  const auto type = ReadAttributeOrDie<ElfSymbol::SymbolType>(symbol, "type");
  const auto binding =
      ReadAttributeOrDie<ElfSymbol::Binding>(symbol, "binding");
  const auto visibility =
      ReadAttributeOrDie<ElfSymbol::Visibility>(symbol, "visibility");

  return graph_.Add<ElfSymbol>(
      info.name, info.version_info,
      is_defined, type, binding, visibility, crc, ns, type_id, name);
}

Id Abigail::BuildSymbols() {
  // Libabigail's model is (approximately):
  //
  //   (alias)* -> main symbol <- some decl -> type
  //
  // which we turn into:
  //
  //   symbol / alias -> type
  //
  for (const auto& [alias, main] : alias_to_main_) {
    Check(!alias_to_main_.count(main))
        << "found main symbol and alias with id " << main;
  }
  // Build final symbol table, tying symbols to their types.
  std::map<std::string, Id> symbols;
  for (const auto& [id, symbol_info] : symbol_info_map_) {
    const auto main = alias_to_main_.find(id);
    const auto lookup = main != alias_to_main_.end() ? main->second : id;
    const auto type_id_and_name_it = symbol_id_and_full_name_.find(lookup);
    std::optional<Id> type_id;
    std::optional<std::string> name;
    if (type_id_and_name_it != symbol_id_and_full_name_.end()) {
      const auto& type_id_and_name = type_id_and_name_it->second;
      type_id = {type_id_and_name.first};
      name = {type_id_and_name.second};
    }
    symbols.insert({id, BuildSymbol(symbol_info, type_id, name)});
  }
  return graph_.Add<Interface>(symbols);
}

Document Read(Runtime& runtime, const std::string& path) {
  // Open input for reading.
  const FileDescriptor fd(path.c_str(), O_RDONLY);

  // Read the XML.
  Document document(nullptr, xmlFreeDoc);
  {
    const Time t(runtime, "abigail.libxml_parse");
    const std::unique_ptr<
        std::remove_pointer_t<xmlParserCtxtPtr>, void(*)(xmlParserCtxtPtr)>
        context(xmlNewParserCtxt(), xmlFreeParserCtxt);
    document.reset(
        xmlCtxtReadFd(context.get(), fd.Value(), nullptr, nullptr,
                      XML_PARSE_NONET));
  }
  Check(document != nullptr) << "failed to parse input as XML";

  return document;
}

Id Read(Runtime& runtime, Graph& graph, const std::string& path) {
  const Document document = Read(runtime, path);
  const xmlNodePtr root = xmlDocGetRootElement(document.get());
  Check(root != nullptr) << "XML document has no root element";
  return Abigail(graph).ProcessRoot(root);
}

}  // namespace abixml
}  // namespace stg
