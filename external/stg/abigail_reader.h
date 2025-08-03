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

#ifndef STG_ABIGAIL_READER_H_
#define STG_ABIGAIL_READER_H_

#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

#include <libxml/tree.h>
#include "graph.h"
#include "runtime.h"
#include "scope.h"

namespace stg {
namespace abixml {

// Parser for libabigail's ABI XML format, creating a Symbol-Type Graph.
//
// On construction Abigail consumes a libxml node tree and builds a graph.
//
// The parser supports C types only, with C++ types to be added later.
//
// The main producer of ABI XML is abidw. The format has no formal specification
// and has very limited semantic versioning. This parser makes no attempt to
// support or correct for deficiencies in older versions of the format.
//
// The parser detects unexpected elements and will abort on the presence of at
// least: namespace, base class and member function information.
//
// The parser ignores attributes it doesn't care about, including member access
// specifiers and (meaningless) type ids on array dimensions.
//
// The STG IR and libabigail ABI XML models diverge in some ways. The parser has
// to do extra work for each of these, as follows.
//
// 0. XML uses type and symbol ids to link together elements. These become edges
// in the graph between symbols and types and between types and types. Dangling
// type references will cause an abort. libabigail is much more relaxed about
// symbols without type information and these are modelled as such.
//
// 1. XML function declarations have in-line types. The parser creates
// free-standing types on-the-fly. A useful space optimisation might be to
// prevent duplicate creation of such types.
//
// 2. Variadic parameters are currently flagged with an XML attribute. A
// variadic type node is created on demand and will be shared by all such
// paramerters.
//
// 3. XML symbols and aliases have a rather poor repesentation with aliases
// represented as comma-separated attribute values. Aliases are resolved in a
// post-processing phase.
//
// 4. XML anonymous types also have unhelpful names, these are ignored.
class Abigail {
 public:
  explicit Abigail(Graph& graph);
  Id ProcessRoot(xmlNodePtr root);

 private:
  struct SymbolInfo {
    std::string name;
    std::optional<ElfSymbol::VersionInfo> version_info;
    xmlNodePtr node;
  };

  Graph& graph_;

  // The STG IR uses a distinct node type for the variadic parameter type; if
  // allocated, this is its STG node id.
  std::optional<Id> variadic_;
  // Map from libabigail type ids to STG node ids; except for the type of
  // variadic parameters.
  std::unordered_map<std::string, Id> type_ids_;

  // symbol id to symbol information
  std::unordered_map<std::string, SymbolInfo> symbol_info_map_;
  // alias symbol id to main symbol id
  std::unordered_map<std::string, std::string> alias_to_main_;
  // libabigail decorates certain declarations with symbol ids; this is the
  // mapping from symbol id to the corresponding type and full name.
  std::unordered_map<std::string, std::pair<Id, std::string>>
      symbol_id_and_full_name_;

  // Full name of the current scope.
  Scope scope_name_;

  Id GetNode(const std::string& type_id);
  Id GetEdge(xmlNodePtr element);
  Id GetVariadic();
  Function MakeFunctionType(xmlNodePtr function);

  void ProcessCorpusGroup(xmlNodePtr group);
  void ProcessCorpus(xmlNodePtr corpus);
  void ProcessSymbols(xmlNodePtr symbols);
  void ProcessSymbol(xmlNodePtr symbol);

  bool ProcessUserDefinedType(std::string_view name, Id id, xmlNodePtr decl);
  void ProcessScope(xmlNodePtr scope);

  void ProcessInstr(xmlNodePtr instr);
  void ProcessNamespace(xmlNodePtr scope);

  Id ProcessDecl(bool is_variable, xmlNodePtr decl);

  void ProcessFunctionType(Id id, xmlNodePtr function);
  void ProcessTypedef(Id id, xmlNodePtr type_definition);
  void ProcessPointer(Id id, bool is_pointer, xmlNodePtr pointer);
  void ProcessQualified(Id id, xmlNodePtr qualified);
  void ProcessArray(Id id, xmlNodePtr array);
  void ProcessTypeDecl(Id id, xmlNodePtr type_decl);
  void ProcessStructUnion(Id id, bool is_struct, xmlNodePtr struct_union);
  void ProcessEnum(Id id, xmlNodePtr enumeration);

  Id ProcessBaseClass(xmlNodePtr base_class);
  std::optional<Id> ProcessDataMember(bool is_struct, xmlNodePtr data_member);
  void ProcessMemberFunction(std::vector<Id>& methods, xmlNodePtr method);
  void ProcessMemberType(xmlNodePtr member_type);

  Id BuildSymbol(const SymbolInfo& info,
                 std::optional<Id> type_id,
                 const std::optional<std::string>& name);
  Id BuildSymbols();
};

Id Read(Runtime& runtime, Graph& graph, const std::string& path);

// Exposed for testing.
void Clean(xmlNodePtr root);
bool EqualTree(xmlNodePtr left, xmlNodePtr right);
bool SubTree(xmlNodePtr left, xmlNodePtr right);
using Document =
    std::unique_ptr<std::remove_pointer_t<xmlDocPtr>, void(*)(xmlDocPtr)>;
Document Read(Runtime& runtime, const std::string& path);

}  // namespace abixml
}  // namespace stg

#endif  // STG_ABIGAIL_READER_H_
