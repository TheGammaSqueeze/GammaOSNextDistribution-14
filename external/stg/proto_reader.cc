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

#include "proto_reader.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstdint>
#include <fstream>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/repeated_field.h>
#include <google/protobuf/repeated_ptr_field.h>
#include <google/protobuf/text_format.h>
#include "error.h"
#include "graph.h"
#include "stg.pb.h"

namespace stg {
namespace proto {

namespace {

struct Transformer {
  explicit Transformer(Graph& graph) : graph(graph) {}

  Id Transform(const proto::STG&);

  Id GetId(uint32_t);

  template <typename ProtoType>
  void AddNodes(const google::protobuf::RepeatedPtrField<ProtoType>&);
  void AddNode(const Void&);
  void AddNode(const Variadic&);
  void AddNode(const Special&);
  void AddNode(const PointerReference&);
  void AddNode(const PointerToMember&);
  void AddNode(const Typedef&);
  void AddNode(const Qualified&);
  void AddNode(const Primitive&);
  void AddNode(const Array&);
  void AddNode(const BaseClass&);
  void AddNode(const Method&);
  void AddNode(const Member&);
  void AddNode(const StructUnion&);
  void AddNode(const Enumeration&);
  void AddNode(const Function&);
  void AddNode(const ElfSymbol&);
  void AddNode(const Symbols&);
  void AddNode(const Interface&);
  template <typename STGType, typename... Args>
  void AddNode(Args&&...);

  std::vector<Id> Transform(const google::protobuf::RepeatedField<uint32_t>&);
  template <typename GetKey>
  std::map<std::string, Id> Transform(GetKey,
                                      const google::protobuf::RepeatedField<uint32_t>&);
  stg::Special::Kind Transform(Special::Kind);
  stg::PointerReference::Kind Transform(PointerReference::Kind);
  stg::Qualifier Transform(Qualified::Qualifier);
  stg::Primitive::Encoding Transform(Primitive::Encoding);
  stg::BaseClass::Inheritance Transform(BaseClass::Inheritance);
  stg::StructUnion::Kind Transform(StructUnion::Kind);
  stg::ElfSymbol::SymbolType Transform(ElfSymbol::SymbolType);
  stg::ElfSymbol::Binding Transform(ElfSymbol::Binding);
  stg::ElfSymbol::Visibility Transform(ElfSymbol::Visibility);
  stg::Enumeration::Enumerators Transform(
      const google::protobuf::RepeatedPtrField<Enumeration::Enumerator>&);
  template <typename STGType, typename ProtoType>
  std::optional<STGType> Transform(bool, const ProtoType&);
  template <typename Type>
  Type Transform(const Type&);

  Graph& graph;
  std::unordered_map<uint32_t, Id> id_map;
};

Id Transformer::Transform(const proto::STG& x) {
  AddNodes(x.void_());  // deprecated
  AddNodes(x.variadic());  // deprecated
  AddNodes(x.special());
  AddNodes(x.pointer_reference());
  AddNodes(x.pointer_to_member());
  AddNodes(x.typedef_());
  AddNodes(x.qualified());
  AddNodes(x.primitive());
  AddNodes(x.array());
  AddNodes(x.base_class());
  AddNodes(x.method());
  AddNodes(x.member());
  AddNodes(x.struct_union());
  AddNodes(x.enumeration());
  AddNodes(x.function());
  AddNodes(x.elf_symbol());
  AddNodes(x.symbols());
  AddNodes(x.interface());
  return GetId(x.root_id());
}

Id Transformer::GetId(uint32_t id) {
  auto [it, inserted] = id_map.emplace(id, 0);
  if (inserted) {
    it->second = graph.Allocate();
  }
  return it->second;
}

template <typename ProtoType>
void Transformer::AddNodes(const google::protobuf::RepeatedPtrField<ProtoType>& x) {
  for (const ProtoType& proto : x) {
    AddNode(proto);
  }
}

void Transformer::AddNode(const Void& x) {
  AddNode<stg::Special>(GetId(x.id()), stg::Special::Kind::VOID);
}

void Transformer::AddNode(const Variadic& x) {
  AddNode<stg::Special>(GetId(x.id()), stg::Special::Kind::VARIADIC);
}

void Transformer::AddNode(const Special& x) {
  AddNode<stg::Special>(GetId(x.id()), x.kind());
}

void Transformer::AddNode(const PointerReference& x) {
  AddNode<stg::PointerReference>(GetId(x.id()), x.kind(),
                                 GetId(x.pointee_type_id()));
}

void Transformer::AddNode(const PointerToMember& x) {
  AddNode<stg::PointerToMember>(GetId(x.id()), GetId(x.containing_type_id()),
                                GetId(x.pointee_type_id()));
}

void Transformer::AddNode(const Typedef& x) {
  AddNode<stg::Typedef>(GetId(x.id()), x.name(), GetId(x.referred_type_id()));
}

void Transformer::AddNode(const Qualified& x) {
  AddNode<stg::Qualified>(GetId(x.id()), x.qualifier(),
                          GetId(x.qualified_type_id()));
}

void Transformer::AddNode(const Primitive& x) {
  const auto& encoding =
      Transform<stg::Primitive::Encoding>(x.has_encoding(), x.encoding());
  AddNode<stg::Primitive>(GetId(x.id()), x.name(), encoding, x.bytesize());
}

void Transformer::AddNode(const Array& x) {
  AddNode<stg::Array>(GetId(x.id()), x.number_of_elements(),
                      GetId(x.element_type_id()));
}

void Transformer::AddNode(const BaseClass& x) {
  AddNode<stg::BaseClass>(GetId(x.id()), GetId(x.type_id()), x.offset(),
                          x.inheritance());
}

void Transformer::AddNode(const Method& x) {
  AddNode<stg::Method>(GetId(x.id()), x.mangled_name(), x.name(),
                       x.vtable_offset(), GetId(x.type_id()));
}

void Transformer::AddNode(const Member& x) {
  AddNode<stg::Member>(GetId(x.id()), x.name(), GetId(x.type_id()), x.offset(),
                       x.bitsize());
}

void Transformer::AddNode(const StructUnion& x) {
  if (x.has_definition()) {
    AddNode<stg::StructUnion>(
        GetId(x.id()), x.kind(), x.name(), x.definition().bytesize(),
        x.definition().base_class_id(), x.definition().method_id(),
        x.definition().member_id());
  } else {
    AddNode<stg::StructUnion>(GetId(x.id()), x.kind(), x.name());
  }
}

void Transformer::AddNode(const Enumeration& x) {
  if (x.has_definition()) {
    AddNode<stg::Enumeration>(GetId(x.id()), x.name(),
                              GetId(x.definition().underlying_type_id()),
                              x.definition().enumerator());
    return;
  } else {
    AddNode<stg::Enumeration>(GetId(x.id()), x.name());
  }
}

void Transformer::AddNode(const Function& x) {
  AddNode<stg::Function>(GetId(x.id()), GetId(x.return_type_id()),
                         x.parameter_id());
}

void Transformer::AddNode(const ElfSymbol& x) {
  auto make_version_info = [](const ElfSymbol::VersionInfo& x) {
    return std::make_optional(
        stg::ElfSymbol::VersionInfo{x.is_default(), x.name()});
  };
  std::optional<stg::ElfSymbol::VersionInfo> version_info =
      x.has_version_info() ? make_version_info(x.version_info()) : std::nullopt;
  const auto& crc = x.has_crc()
                        ? std::make_optional<stg::ElfSymbol::CRC>(x.crc())
                        : std::nullopt;
  const auto& ns = Transform<std::string>(x.has_namespace_(), x.namespace_());
  const auto& type_id =
      x.has_type_id() ? std::make_optional(GetId(x.type_id())) : std::nullopt;
  const auto& full_name =
      Transform<std::string>(x.has_full_name(), x.full_name());

  AddNode<stg::ElfSymbol>(GetId(x.id()), x.name(), version_info, x.is_defined(),
                          x.symbol_type(), x.binding(), x.visibility(), crc, ns,
                          type_id, full_name);
}

void Transformer::AddNode(const Symbols& x) {
  std::map<std::string, Id> symbols;
  for (const auto& [symbol, id] : x.symbol()) {
    symbols.emplace(symbol, GetId(id));
  }
  AddNode<stg::Interface>(GetId(x.id()), symbols);
}

void Transformer::AddNode(const Interface& x) {
  const InterfaceKey get_key(graph);
  AddNode<stg::Interface>(GetId(x.id()), Transform(get_key, x.symbol_id()),
                          Transform(get_key, x.type_id()));
}

template <typename STGType, typename... Args>
void Transformer::AddNode(Args&&... args) {
  graph.Set<STGType>(Transform(args)...);
}

std::vector<Id> Transformer::Transform(
    const google::protobuf::RepeatedField<uint32_t>& ids) {
  std::vector<Id> result;
  result.reserve(ids.size());
  for (uint32_t id : ids) {
    result.push_back(GetId(id));
  }
  return result;
}

template <typename GetKey>
std::map<std::string, Id> Transformer::Transform(
    GetKey get_key, const google::protobuf::RepeatedField<uint32_t>& ids) {
  std::map<std::string, Id> result;
  for (auto id : ids) {
    const Id stg_id = GetId(id);
    const auto [it, inserted] = result.emplace(get_key(stg_id), stg_id);
    if (!inserted) {
      Die() << "conflicting interface nodes: " << it->first;
    }
  }
  return result;
}

stg::Special::Kind Transformer::Transform(Special::Kind x) {
  switch (x) {
    case Special::VOID:
      return stg::Special::Kind::VOID;
    case Special::VARIADIC:
      return stg::Special::Kind::VARIADIC;
    case Special::NULLPTR:
      return stg::Special::Kind::NULLPTR;
    default:
      Die() << "unknown Special::Kind " << x;
  }
}

stg::PointerReference::Kind Transformer::Transform(PointerReference::Kind x) {
  switch (x) {
    case PointerReference::POINTER:
      return stg::PointerReference::Kind::POINTER;
    case PointerReference::LVALUE_REFERENCE:
      return stg::PointerReference::Kind::LVALUE_REFERENCE;
    case PointerReference::RVALUE_REFERENCE:
      return stg::PointerReference::Kind::RVALUE_REFERENCE;
    default:
      Die() << "unknown PointerReference::Kind " << x;
  }
}

stg::Qualifier Transformer::Transform(Qualified::Qualifier x) {
  switch (x) {
    case Qualified::CONST:
      return stg::Qualifier::CONST;
    case Qualified::VOLATILE:
      return stg::Qualifier::VOLATILE;
    case Qualified::RESTRICT:
      return stg::Qualifier::RESTRICT;
    case Qualified::ATOMIC:
      return stg::Qualifier::ATOMIC;
    default:
      Die() << "unknown Qualified::Qualifier " << x;
  }
}

stg::Primitive::Encoding Transformer::Transform(Primitive::Encoding x) {
  switch (x) {
    case Primitive::BOOLEAN:
      return stg::Primitive::Encoding::BOOLEAN;
    case Primitive::SIGNED_INTEGER:
      return stg::Primitive::Encoding::SIGNED_INTEGER;
    case Primitive::UNSIGNED_INTEGER:
      return stg::Primitive::Encoding::UNSIGNED_INTEGER;
    case Primitive::SIGNED_CHARACTER:
      return stg::Primitive::Encoding::SIGNED_CHARACTER;
    case Primitive::UNSIGNED_CHARACTER:
      return stg::Primitive::Encoding::UNSIGNED_CHARACTER;
    case Primitive::REAL_NUMBER:
      return stg::Primitive::Encoding::REAL_NUMBER;
    case Primitive::COMPLEX_NUMBER:
      return stg::Primitive::Encoding::COMPLEX_NUMBER;
    case Primitive::UTF:
      return stg::Primitive::Encoding::UTF;
    default:
      Die() << "unknown Primitive::Encoding " << x;
  }
}

stg::BaseClass::Inheritance Transformer::Transform(BaseClass::Inheritance x) {
  switch (x) {
    case BaseClass::NON_VIRTUAL:
      return stg::BaseClass::Inheritance::NON_VIRTUAL;
    case BaseClass::VIRTUAL:
      return stg::BaseClass::Inheritance::VIRTUAL;
    default:
      Die() << "unknown BaseClass::Inheritance " << x;
  }
}

stg::StructUnion::Kind Transformer::Transform(StructUnion::Kind x) {
  switch (x) {
    case StructUnion::STRUCT:
      return stg::StructUnion::Kind::STRUCT;
    case StructUnion::UNION:
      return stg::StructUnion::Kind::UNION;
    default:
      Die() << "unknown StructUnion::Kind " << x;
  }
}

stg::ElfSymbol::SymbolType Transformer::Transform(ElfSymbol::SymbolType x) {
  switch (x) {
    case ElfSymbol::OBJECT:
      return stg::ElfSymbol::SymbolType::OBJECT;
    case ElfSymbol::FUNCTION:
      return stg::ElfSymbol::SymbolType::FUNCTION;
    case ElfSymbol::COMMON:
      return stg::ElfSymbol::SymbolType::COMMON;
    case ElfSymbol::TLS:
      return stg::ElfSymbol::SymbolType::TLS;
    case ElfSymbol::GNU_IFUNC:
      return stg::ElfSymbol::SymbolType::GNU_IFUNC;
    default:
      Die() << "unknown ElfSymbol::SymbolType " << x;
  }
}

stg::ElfSymbol::Binding Transformer::Transform(ElfSymbol::Binding x) {
  switch (x) {
    case ElfSymbol::GLOBAL:
      return stg::ElfSymbol::Binding::GLOBAL;
    case ElfSymbol::LOCAL:
      return stg::ElfSymbol::Binding::LOCAL;
    case ElfSymbol::WEAK:
      return stg::ElfSymbol::Binding::WEAK;
    case ElfSymbol::GNU_UNIQUE:
      return stg::ElfSymbol::Binding::GNU_UNIQUE;
    default:
      Die() << "unknown ElfSymbol::Binding " << x;
  }
}

stg::ElfSymbol::Visibility Transformer::Transform(ElfSymbol::Visibility x) {
  switch (x) {
    case ElfSymbol::DEFAULT:
      return stg::ElfSymbol::Visibility::DEFAULT;
    case ElfSymbol::PROTECTED:
      return stg::ElfSymbol::Visibility::PROTECTED;
    case ElfSymbol::HIDDEN:
      return stg::ElfSymbol::Visibility::HIDDEN;
    case ElfSymbol::INTERNAL:
      return stg::ElfSymbol::Visibility::INTERNAL;
    default:
      Die() << "unknown ElfSymbol::Visibility " << x;
  }
}

stg::Enumeration::Enumerators Transformer::Transform(
    const google::protobuf::RepeatedPtrField<Enumeration::Enumerator>& x) {
  stg::Enumeration::Enumerators enumerators;
  enumerators.reserve(x.size());
  for (const auto& enumerator : x) {
    enumerators.emplace_back(enumerator.name(), enumerator.value());
  }
  return enumerators;
}

template <typename STGType, typename ProtoType>
std::optional<STGType> Transformer::Transform(bool has_field,
                                              const ProtoType& field) {
  return has_field ? std::make_optional<STGType>(Transform(field))
                   : std::nullopt;
}

template <typename Type>
Type Transformer::Transform(const Type& x) {
  return x;
}

const std::array<uint32_t, 3> kSupportedFormatVersions = {0, 1, 2};

void CheckFormatVersion(uint32_t version, std::optional<std::string> path) {
  Check(std::count(kSupportedFormatVersions.begin(),
                   kSupportedFormatVersions.end(), version) > 0)
      << "STG format version " << version
      << " is not supported, minimum supported version: "
      << kSupportedFormatVersions.front();
  if (version != kSupportedFormatVersions.back()) {
    auto warn = Warn();
    warn << "STG format version " << version
         << " is deprecated, consider upgrading stg format to latest version ("
         << kSupportedFormatVersions.back() << ")";
    if (path) {
      warn << " with: stg --stg " << *path << " --output " << *path;
    }
  }
}

}  // namespace

Id Read(Graph& graph, const std::string& path) {
  std::ifstream ifs(path);
  Check(ifs.good()) << "error opening file '" << path
                    << "' for reading: " << Error(errno);
  google::protobuf::io::IstreamInputStream is(&ifs);
  proto::STG stg;
  google::protobuf::TextFormat::Parse(&is, &stg);
  CheckFormatVersion(stg.version(), path);
  return Transformer(graph).Transform(stg);
}

Id ReadFromString(Graph& graph, const std::string_view input) {
  proto::STG stg;
  google::protobuf::TextFormat::ParseFromString(std::string(input), &stg);
  CheckFormatVersion(stg.version(), std::nullopt);
  return Transformer(graph).Transform(stg);
}

}  // namespace proto
}  // namespace stg
