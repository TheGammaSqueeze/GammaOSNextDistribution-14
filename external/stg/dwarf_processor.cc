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
// Author: Aleksei Vetrov

#include "dwarf_processor.h"

#include <dwarf.h>
#include <elfutils/libdw.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "dwarf_wrappers.h"
#include "error.h"
#include "filter.h"
#include "graph.h"
#include "scope.h"

namespace stg {
namespace dwarf {

namespace {

std::string EntryToString(Entry& entry) {
  std::ostringstream os;
  os << "DWARF entry <" << Hex(entry.GetOffset()) << ">";
  return os.str();
}

std::optional<std::string> MaybeGetName(Entry& entry) {
  return entry.MaybeGetString(DW_AT_name);
}

std::string GetName(Entry& entry) {
  auto result = MaybeGetName(entry);
  if (!result.has_value()) {
    Die() << "Name was not found for " << EntryToString(entry);
  }
  return std::move(*result);
}

std::string GetNameOrEmpty(Entry& entry) {
  auto result = MaybeGetName(entry);
  if (!result.has_value()) {
    return std::string();
  }
  return std::move(*result);
}

std::optional<std::string> MaybeGetLinkageName(int version, Entry& entry) {
  return entry.MaybeGetString(
      version < 4 ? DW_AT_MIPS_linkage_name : DW_AT_linkage_name);
}

size_t GetBitSize(Entry& entry) {
  if (auto byte_size = entry.MaybeGetUnsignedConstant(DW_AT_byte_size)) {
    return *byte_size * 8;
  } else if (auto bit_size = entry.MaybeGetUnsignedConstant(DW_AT_bit_size)) {
    return *bit_size;
  }
  Die() << "Bit size was not found for " << EntryToString(entry);
}

size_t GetByteSize(Entry& entry) {
  if (auto byte_size = entry.MaybeGetUnsignedConstant(DW_AT_byte_size)) {
    return *byte_size;
  } else if (auto bit_size = entry.MaybeGetUnsignedConstant(DW_AT_bit_size)) {
    // Round up bit_size / 8 to get minimal needed storage size in bytes.
    return (*bit_size + 7) / 8;
  }
  Die() << "Byte size was not found for " << EntryToString(entry);
}

Primitive::Encoding GetEncoding(Entry& entry) {
  const auto dwarf_encoding = entry.MaybeGetUnsignedConstant(DW_AT_encoding);
  if (!dwarf_encoding) {
    Die() << "Encoding was not found for " << EntryToString(entry);
  }
  switch (*dwarf_encoding) {
    case DW_ATE_boolean:
      return Primitive::Encoding::BOOLEAN;
    case DW_ATE_complex_float:
      return Primitive::Encoding::COMPLEX_NUMBER;
    case DW_ATE_float:
      return Primitive::Encoding::REAL_NUMBER;
    case DW_ATE_signed:
      return Primitive::Encoding::SIGNED_INTEGER;
    case DW_ATE_signed_char:
      return Primitive::Encoding::SIGNED_CHARACTER;
    case DW_ATE_unsigned:
      return Primitive::Encoding::UNSIGNED_INTEGER;
    case DW_ATE_unsigned_char:
      return Primitive::Encoding::UNSIGNED_CHARACTER;
    case DW_ATE_UTF:
      return Primitive::Encoding::UTF;
    default:
      Die() << "Unknown encoding " << Hex(*dwarf_encoding) << " for "
            << EntryToString(entry);
  }
}

std::optional<Entry> MaybeGetReferredType(Entry& entry) {
  return entry.MaybeGetReference(DW_AT_type);
}

Entry GetReferredType(Entry& entry) {
  auto result = MaybeGetReferredType(entry);
  if (!result.has_value()) {
    Die() << "Type reference was not found in " << EntryToString(entry);
  }
  return *result;
}

size_t GetNumberOfElements(Entry& entry) {
  // DWARF standard says, that array dimensions could be an entry with
  // either DW_TAG_subrange_type or DW_TAG_enumeration_type. However, this
  // code supports only the DW_TAG_subrange_type.
  Check(entry.GetTag() == DW_TAG_subrange_type)
      << "Array's dimensions should be an entry of DW_TAG_subrange_type";
  std::optional<size_t> number_of_elements = entry.MaybeGetCount();
  if (number_of_elements) {
    return *number_of_elements;
  }
  // If a subrange has no DW_AT_count and no DW_AT_upper_bound attribute, its
  // size is unknown.
  return 0;
}

// Calculate number of bits from the "beginning" of the containing entity to
// the "beginning" of the data member using DW_AT_bit_offset.
//
// "Number of bits from the beginning", depends on the definition of the
// "beginning", which is different for big- and little-endian architectures.
// However, DW_AT_bit_offset is defined from the high order bit of the storage
// unit to the high order bit of a field and is the same for both architectures.

// So this function converts DW_AT_bit_offset to the "number of bits from the
// beginning".
size_t CalculateBitfieldAdjustment(Entry& entry, size_t bit_size,
                             bool is_little_endian_binary) {
  if (bit_size == 0) {
    // bit_size == 0 marks that it is not a bit field. No adjustment needed.
    return 0;
  }
  auto container_byte_size = entry.MaybeGetUnsignedConstant(DW_AT_byte_size);
  auto bit_offset = entry.MaybeGetUnsignedConstant(DW_AT_bit_offset);
  Check(container_byte_size.has_value() && bit_offset.has_value())
      << "If member offset is defined as DW_AT_data_member_location, bit field "
         "should have DW_AT_byte_size and DW_AT_bit_offset";
  // The following structure will be used as an example in the explanations:
  // struct foo {
  //   uint16_t rest_of_the_struct;
  //   uint16_t x : 5;
  //   uint16_t y : 6;
  //   uint16_t z : 5;
  // };
  if (is_little_endian_binary) {
    // Compiler usualy packs bit fields starting with the least significant
    // bits, but DW_AT_bit_offset is counted from high to low bits:
    //
    // rest of the struct|<    container   >
    //    Container bits: 01234|56789A|BCDEF
    //  Bit-fields' bits: 01234|012345|01234
    //        bit_offset: <<<<B<<<<<<5<<<<<0
    //   bits from start: 0>>>>>5>>>>>>B>>>>
    //                    <x:5>|< y:6>|<z:5>
    //
    //   x.bit_offset: 11 (0xB) bits
    //   y.bit_offset: 5 bits
    //   z.bit_offset: 0 bits
    //
    // So we need to subtract bit_offset from the container bit size
    // (container_byte_size * 8) to inverse direction. Also we need to convert
    // from high- to low-order bit, because the field "begins" with low-order
    // bit. To do so we need to subtract field's bit size. Resulting formula is:
    //
    //   container_byte_size * 8 - bit_offset - bit_size
    //
    // If we try it on example, we get correct values:
    //   x: 2 * 8 - 11 - 5 = 0
    //   y: 2 * 8 - 5 - 6 = 5
    //   z: 2 * 8 - 0 - 5 = 11 (0xB)
    return *container_byte_size * 8 - *bit_offset - bit_size;
  }
  // Big-endian orders begins with high-order bit and the bit_offset is from the
  // high order bit:
  //
  // rest of the struct|<    container   >
  //    Container bits: FEDCB|A98765|43210
  //  Bit-fields' bits: 43210|543210|43210
  //        bit_offset: 0>>>>>5>>>>>>B>>>>
  //   bits from start: 0>>>>>5>>>>>>B>>>>
  //                    <x:5>|< y:6>|<z:5>
  //
  // So we just return bit_offset.
  return *bit_offset;
}

// Calculate the number of bits from the beginning of the structure to the
// beginning of the data member.
size_t GetDataBitOffset(Entry& entry, size_t bit_size,
                        bool is_little_endian_binary) {
  // Offset may be represented either by DW_AT_data_bit_offset (in bits) or by
  // DW_AT_data_member_location (in bytes).
  if (auto data_bit_offset =
          entry.MaybeGetUnsignedConstant(DW_AT_data_bit_offset)) {
    // DW_AT_data_bit_offset contains what this function needs for any type
    // of member (bitfield or not) on architecture of any endianness.
    return *data_bit_offset;
  } else if (auto byte_offset = entry.MaybeGetMemberByteOffset()) {
    // DW_AT_data_member_location contains offset in bytes.
    const size_t bit_offset = *byte_offset * 8;
    // But there can be offset part, coming from DW_AT_bit_offset. DWARF 5
    // standard requires to use DW_AT_data_bit_offset in this case, but a lot
    // of binaries still use combination of DW_AT_data_member_location and
    // DW_AT_bit_offset.
    const size_t bitfield_adjusment =
        CalculateBitfieldAdjustment(entry, bit_size, is_little_endian_binary);
    return bit_offset + bitfield_adjusment;
  } else {
    // If the beginning of the data member is the same as the beginning of the
    // containing entity then neither attribute is required.
    return 0;
  }
}

}  // namespace

// Transforms DWARF entries to STG.
class Processor {
 public:
  Processor(Graph& graph, Id void_id, Id variadic_id,
            bool is_little_endian_binary,
            const std::unique_ptr<Filter>& file_filter, Types& result)
      : graph_(graph),
        void_id_(void_id),
        variadic_id_(variadic_id),
        is_little_endian_binary_(is_little_endian_binary),
        file_filter_(file_filter),
        result_(result) {}

  void ProcessCompilationUnit(CompilationUnit& compilation_unit) {
    version_ = compilation_unit.version;
    if (file_filter_ != nullptr) {
      files_ = dwarf::Files(compilation_unit.entry);
    }
    Process(compilation_unit.entry);
  }

  void CheckUnresolvedIds() const {
    for (const auto& [offset, id] : id_map_) {
      if (!graph_.Is(id)) {
        Die() << "unresolved id " << id << ", DWARF offset " << Hex(offset);
      }
    }
  }

  void ResolveSymbolSpecifications() {
    std::sort(unresolved_symbol_specifications_.begin(),
              unresolved_symbol_specifications_.end());
    std::sort(scoped_names_.begin(), scoped_names_.end());
    auto symbols_it = unresolved_symbol_specifications_.begin();
    auto names_it = scoped_names_.begin();
    while (symbols_it != unresolved_symbol_specifications_.end()) {
      while (names_it != scoped_names_.end() &&
             names_it->first < symbols_it->first) {
        ++names_it;
      }
      if (names_it == scoped_names_.end() ||
          names_it->first != symbols_it->first) {
        Die() << "Scoped name not found for entry " << Hex(symbols_it->first);
      }
      result_.symbols[symbols_it->second].name = names_it->second;
      ++symbols_it;
    }
  }

 private:
  void Process(Entry& entry) {
    try {
      return ProcessInternal(entry);
    } catch (Exception& e) {
      std::ostringstream os;
      os << "processing DIE " << Hex(entry.GetOffset());
      e.Add(os.str());
      throw;
    }
  }

  void ProcessInternal(Entry& entry) {
    ++result_.processed_entries;
    const auto tag = entry.GetTag();
    switch (tag) {
      case DW_TAG_array_type:
        ProcessArray(entry);
        break;
      case DW_TAG_enumeration_type:
        ProcessEnum(entry);
        break;
      case DW_TAG_class_type:
        ProcessStructUnion(entry, StructUnion::Kind::STRUCT);
        break;
      case DW_TAG_structure_type:
        ProcessStructUnion(entry, StructUnion::Kind::STRUCT);
        break;
      case DW_TAG_union_type:
        ProcessStructUnion(entry, StructUnion::Kind::UNION);
        break;
      case DW_TAG_member:
        Die() << "DW_TAG_member outside of struct/class/union";
        break;
      case DW_TAG_pointer_type:
        ProcessReference<PointerReference>(
            entry, PointerReference::Kind::POINTER);
        break;
      case DW_TAG_reference_type:
        ProcessReference<PointerReference>(
            entry, PointerReference::Kind::LVALUE_REFERENCE);
        break;
      case DW_TAG_rvalue_reference_type:
        ProcessReference<PointerReference>(
            entry, PointerReference::Kind::RVALUE_REFERENCE);
        break;
      case DW_TAG_ptr_to_member_type:
        ProcessPointerToMember(entry);
        break;
      case DW_TAG_unspecified_type:
        ProcessUnspecifiedType(entry);
        break;
      case DW_TAG_compile_unit:
        ProcessAllChildren(entry);
        break;
      case DW_TAG_typedef:
        ProcessTypedef(entry);
        break;
      case DW_TAG_base_type:
        ProcessBaseType(entry);
        break;
      case DW_TAG_const_type:
        ProcessReference<Qualified>(entry, Qualifier::CONST);
        break;
      case DW_TAG_volatile_type:
        ProcessReference<Qualified>(entry, Qualifier::VOLATILE);
        break;
      case DW_TAG_restrict_type:
        ProcessReference<Qualified>(entry, Qualifier::RESTRICT);
        break;
      case DW_TAG_atomic_type:
        // TODO: test pending BTF / test suite support
        ProcessReference<Qualified>(entry, Qualifier::ATOMIC);
        break;
      case DW_TAG_variable:
        // Process only variables visible externally
        if (entry.GetFlag(DW_AT_external)) {
          ProcessVariable(entry);
        }
        break;
      case DW_TAG_subroutine_type:
        // Standalone function type, for example, used in function pointers.
        ProcessFunction(entry);
        break;
      case DW_TAG_subprogram:
        // DWARF equivalent of ELF function symbol.
        ProcessFunction(entry);
        break;
      case DW_TAG_namespace:
        ProcessNamespace(entry);
        break;
      case DW_TAG_lexical_block:
        ProcessAllChildren(entry);
        break;

      default:
        // TODO: die on unexpected tag, when this switch contains
        // all expected tags
        break;
    }
  }

  void ProcessAllChildren(Entry& entry) {
    for (auto& child : entry.GetChildren()) {
      Process(child);
    }
  }

  void CheckNoChildren(Entry& entry) {
    if (!entry.GetChildren().empty()) {
      Die() << "Entry expected to have no children";
    }
  }

  void ProcessNamespace(Entry& entry) {
    const auto name = GetNameOrEmpty(entry);
    const PushScopeName push_scope_name(scope_, "namespace", name);
    ProcessAllChildren(entry);
  }

  void ProcessBaseType(Entry& entry) {
    CheckNoChildren(entry);
    const auto type_name = GetName(entry);
    const size_t bit_size = GetBitSize(entry);
    if (bit_size % 8) {
      Die() << "type '" << type_name << "' size is not a multiple of 8";
    }
    const size_t byte_size = bit_size / 8;
    AddProcessedNode<Primitive>(entry, type_name, GetEncoding(entry),
                                byte_size);
  }

  void ProcessTypedef(Entry& entry) {
    const auto type_name = GetName(entry);
    const auto full_name = scope_ + type_name;
    const Id referred_type_id = GetReferredTypeId(MaybeGetReferredType(entry));
    const Id id = AddProcessedNode<Typedef>(entry, full_name, referred_type_id);
    if (!ShouldKeepDefinition(entry, type_name)) {
      // We always model (and keep) typedef definitions. But we should exclude
      // filtered out types from being type roots.
      return;
    }
    AddNamedTypeNode(id);
  }

  template<typename Node, typename KindType>
  void ProcessReference(Entry& entry, KindType kind) {
    const Id referred_type_id = GetReferredTypeId(MaybeGetReferredType(entry));
    AddProcessedNode<Node>(entry, kind, referred_type_id);
  }

  void ProcessPointerToMember(Entry& entry) {
    const Id containing_type_id =
        GetReferredTypeId(entry.MaybeGetReference(DW_AT_containing_type));
    const Id pointee_type_id = GetReferredTypeId(MaybeGetReferredType(entry));
    AddProcessedNode<PointerToMember>(entry, containing_type_id,
                                      pointee_type_id);
  }

  void ProcessUnspecifiedType(Entry& entry) {
    const std::string type_name =  GetName(entry);
    Check(type_name == "decltype(nullptr)")
        << "Unsupported DW_TAG_unspecified_type: " << type_name;
    AddProcessedNode<Special>(entry, Special::Kind::NULLPTR);
  }

  bool ShouldKeepDefinition(Entry& entry, const std::string& name) const {
    if (file_filter_ == nullptr) {
      return true;
    }
    const auto file = files_.MaybeGetFile(entry, DW_AT_decl_file);
    if (!file) {
      // Built in types that do not have DW_AT_decl_file should be preserved.
      static constexpr std::string_view kBuiltinPrefix = "__";
      // TODO: use std::string_view::starts_with
      if (name.substr(0, kBuiltinPrefix.size()) == kBuiltinPrefix) {
        return true;
      }
      Die() << "File filter is provided, but " << name << " ("
            << EntryToString(entry) << ") doesn't have DW_AT_decl_file";
    }
    return (*file_filter_)(*file);
  }

  void ProcessStructUnion(Entry& entry, StructUnion::Kind kind) {
    const auto type_name = GetNameOrEmpty(entry);
    const auto full_name = type_name.empty() ? type_name : scope_ + type_name;
    const PushScopeName push_scope_name(scope_, kind, type_name);

    std::vector<Id> base_classes;
    std::vector<Id> members;
    std::vector<Id> methods;

    for (auto& child : entry.GetChildren()) {
      auto child_tag = child.GetTag();
      // All possible children of struct/class/union
      switch (child_tag) {
        case DW_TAG_member:
          if (child.GetFlag(DW_AT_external)) {
            // static members are interpreted as variables and not included in
            // members.
            ProcessVariable(child);
          } else {
            members.push_back(GetIdForEntry(child));
            ProcessMember(child);
          }
          break;
        case DW_TAG_subprogram:
          ProcessMethod(methods, child);
          break;
        case DW_TAG_inheritance:
          base_classes.push_back(GetIdForEntry(child));
          ProcessBaseClass(child);
          break;
        case DW_TAG_structure_type:
        case DW_TAG_class_type:
        case DW_TAG_union_type:
        case DW_TAG_enumeration_type:
        case DW_TAG_typedef:
        case DW_TAG_const_type:
        case DW_TAG_volatile_type:
        case DW_TAG_restrict_type:
        case DW_TAG_atomic_type:
        case DW_TAG_array_type:
        case DW_TAG_pointer_type:
        case DW_TAG_reference_type:
        case DW_TAG_rvalue_reference_type:
        case DW_TAG_ptr_to_member_type:
        case DW_TAG_unspecified_type:
        case DW_TAG_variable:
          Process(child);
          break;
        case DW_TAG_imported_declaration:
        case DW_TAG_imported_module:
          // For now information there is useless for ABI monitoring, but we
          // need to check that there is no missing information in descendants.
          CheckNoChildren(child);
          break;
        case DW_TAG_template_type_parameter:
        case DW_TAG_template_value_parameter:
        case DW_TAG_GNU_template_template_param:
        case DW_TAG_GNU_template_parameter_pack:
          // We just skip these as neither GCC nor Clang seem to use them
          // properly (resulting in no references to such DIEs).
          break;
        case DW_TAG_variant_part:
          // TODO: Add a DWARF processor to process variants.
          break;
        default:
          Die() << "Unexpected tag for child of struct/class/union: "
                << Hex(child_tag) << ", " << EntryToString(child);
      }
    }

    if (entry.GetFlag(DW_AT_declaration) ||
        !ShouldKeepDefinition(entry, type_name)) {
      // Declaration may have partial information about members or method.
      // We only need to parse children for information that will be needed in
      // complete definition, but don't need to store them in incomplete node.
      AddProcessedNode<StructUnion>(entry, kind, full_name);
      return;
    }

    const auto byte_size = GetByteSize(entry);

    const Id id = AddProcessedNode<StructUnion>(
        entry, kind, full_name, byte_size, std::move(base_classes),
        std::move(methods), std::move(members));
    if (!full_name.empty()) {
      AddNamedTypeNode(id);
    }
  }

  void ProcessMember(Entry& entry) {
    const auto name = GetNameOrEmpty(entry);
    auto referred_type = GetReferredType(entry);
    const Id referred_type_id = GetIdForEntry(referred_type);
    auto optional_bit_size = entry.MaybeGetUnsignedConstant(DW_AT_bit_size);
    // Member has DW_AT_bit_size if and only if it is bit field.
    // STG uses bit_size == 0 to mark that the member is not a bit field.
    Check(!optional_bit_size || *optional_bit_size > 0)
        << "DW_AT_bit_size should be a positive number";
    auto bit_size = optional_bit_size ? *optional_bit_size : 0;
    AddProcessedNode<Member>(
        entry, std::move(name), referred_type_id,
        GetDataBitOffset(entry, bit_size, is_little_endian_binary_), bit_size);
  }

  void ProcessMethod(std::vector<Id>& methods, Entry& entry) {
    Subprogram subprogram = GetSubprogram(entry);
    auto id = graph_.Add<Function>(std::move(subprogram.node));
    if (subprogram.external && subprogram.address) {
      // Only external functions with address are useful for ABI monitoring
      // TODO: cover virtual methods
      const auto new_symbol_idx = result_.symbols.size();
      result_.symbols.push_back(Types::Symbol{
          .name = GetScopedNameForSymbol(
              new_symbol_idx, subprogram.name_with_context),
          .linkage_name = subprogram.linkage_name,
          .address = *subprogram.address,
          .id = id});
    }
    const auto virtuality = entry.MaybeGetUnsignedConstant(DW_AT_virtuality)
                                 .value_or(DW_VIRTUALITY_none);
    if (virtuality == DW_VIRTUALITY_virtual ||
        virtuality == DW_VIRTUALITY_pure_virtual) {
      if (!subprogram.name_with_context.unscoped_name) {
        Die() << "Method " << EntryToString(entry) << " should have name";
      }
      if (subprogram.name_with_context.specification) {
        Die() << "Method " << EntryToString(entry)
              << " shouldn't have specification";
      }
      const auto vtable_offset = entry.MaybeGetVtableOffset().value_or(0);
      // TODO: proper handling of missing linkage name
      methods.push_back(AddProcessedNode<Method>(
          entry, subprogram.linkage_name.value_or("{missing}"),
          *subprogram.name_with_context.unscoped_name, vtable_offset, id));
    }
  }

  void ProcessBaseClass(Entry& entry) {
    const Id type_id = GetReferredTypeId(GetReferredType(entry));
    const auto byte_offset = entry.MaybeGetMemberByteOffset();
    if (!byte_offset) {
      Die() << "No offset found for base class " << EntryToString(entry);
    }
    const auto bit_offset = *byte_offset * 8;
    const auto virtuality = entry.MaybeGetUnsignedConstant(DW_AT_virtuality)
                                 .value_or(DW_VIRTUALITY_none);
    BaseClass::Inheritance inheritance;
    if (virtuality == DW_VIRTUALITY_none) {
      inheritance = BaseClass::Inheritance::NON_VIRTUAL;
    } else if (virtuality == DW_VIRTUALITY_virtual) {
      inheritance = BaseClass::Inheritance::VIRTUAL;
    } else {
      Die() << "Unexpected base class virtuality: " << virtuality;
    }
    AddProcessedNode<BaseClass>(entry, type_id, bit_offset, inheritance);
  }

  void ProcessArray(Entry& entry) {
    auto referred_type = GetReferredType(entry);
    Id referred_type_id = GetIdForEntry(referred_type);
    auto children = entry.GetChildren();
    // Multiple children in array describe multiple dimensions of this array.
    // For example, int[M][N] contains two children, M located in the first
    // child, N located in the second child. But in STG multidimensional arrays
    // are represented as chain of arrays: int[M][N] is array[M] of array[N] of
    // int.
    //
    // We need to chain children as types together in reversed order.
    // "referred_type_id" is updated every time to contain the top element in
    // the chain. Rightmost chldren refers to the original "referred_type_id".
    for (auto it = children.rbegin(); it != children.rend(); ++it) {
      auto& child = *it;
      // All subarrays except the first (last in the reversed order) are
      // attached to the corresponding child. First subarray (last in the
      // reversed order) is attached to the original entry itself.
      auto& entry_to_attach = (it + 1 == children.rend()) ? entry : child;
      // Update referred_type_id so next array in chain points there.
      referred_type_id = AddProcessedNode<Array>(
          entry_to_attach, GetNumberOfElements(child), referred_type_id);
    }
  }

  void ProcessEnum(Entry& entry) {
    const auto type_name = GetNameOrEmpty(entry);
    const auto full_name = type_name.empty() ? type_name : scope_ + type_name;

    if (entry.GetFlag(DW_AT_declaration)) {
      // It is expected to have only name and no children in declaration.
      // However, it is not guaranteed and we should do something if we find an
      // example.
      CheckNoChildren(entry);
      AddProcessedNode<Enumeration>(entry, full_name);
      return;
    }
    const Id underlying_type_id =
        GetReferredTypeId(MaybeGetReferredType(entry));
    auto children = entry.GetChildren();
    Enumeration::Enumerators enumerators;
    enumerators.reserve(children.size());
    for (auto& child : children) {
      auto child_tag = child.GetTag();
      switch (child_tag) {
        case DW_TAG_enumerator: {
          const std::string enumerator_name = GetName(child);
          // TODO: detect signedness of underlying type and call
          // an appropriate method.
          std::optional<size_t> value_optional =
              child.MaybeGetUnsignedConstant(DW_AT_const_value);
          Check(value_optional.has_value()) << "Enumerator should have value";
          // TODO: support both uint64_t and int64_t, depending on
          // signedness of underlying type.
          enumerators.emplace_back(enumerator_name,
                                   static_cast<int64_t>(*value_optional));
          break;
        }
        case DW_TAG_subprogram:
          // STG does not support virtual methods for enums.
          Check(child.MaybeGetUnsignedConstant(DW_AT_virtuality)
                    .value_or(DW_VIRTUALITY_none) == DW_VIRTUALITY_none)
              << "Enums can not have virtual methods: " << EntryToString(child);
          ProcessFunction(child);
          break;
        default:
          Die() << "Unexpected tag for child of enum: " << Hex(child_tag)
                << ", " << EntryToString(child);
      }
    }
    if (!ShouldKeepDefinition(entry, type_name)) {
      AddProcessedNode<Enumeration>(entry, full_name);
      return;
    }
    const Id id = AddProcessedNode<Enumeration>(
        entry, full_name, underlying_type_id, std::move(enumerators));
    if (!full_name.empty()) {
      AddNamedTypeNode(id);
    }
  }

  struct NameWithContext {
    std::optional<Dwarf_Off> specification;
    std::optional<std::string> unscoped_name;
    std::optional<std::string> scoped_name;
  };

  NameWithContext GetNameWithContext(Entry& entry) {
    NameWithContext result;
    // Leaf of specification tree is usually a declaration (of a function or a
    // method). Then goes definition, which references declaration by
    // DW_AT_specification. And on top we have instantiation, which references
    // definition by DW_AT_abstract_origin. In the worst case we have:
    // * instantiation
    //     >-DW_AT_abstract_origin-> definition
    //         >-DW_AT_specification-> declaration
    //
    // By using attribute integration we fold all information from definition to
    // instantiation, flattening hierarchy:
    // * instantiation + definition
    //     >-DW_AT_specification-> declaration
    // NB: DW_AT_abstract_origin attribute is also visible, but it should be
    // ignored, since we already used it during integration.
    //
    // We also need to support this case, when we don't have separate
    // declaration:
    // * instantiation +
    //     >-DW_AT_abstract_origin -> definition
    //
    // So the final algorithm is to get final DW_AT_specification through the
    // whole chain, or use DW_AT_abstract_origin if there is no
    // DW_AT_specification.
    if (auto specification = entry.MaybeGetReference(DW_AT_specification)) {
      result.specification = specification->GetOffset();
    } else if (auto abstract_origin =
                   entry.MaybeGetReference(DW_AT_abstract_origin)) {
      result.specification = abstract_origin->GetOffset();
    }
    result.unscoped_name = entry.MaybeGetDirectString(DW_AT_name);
    if (!result.unscoped_name && !result.specification) {
      // If there is no name and specification, then this entry is anonymous.
      // Anonymous entries are modelled as the empty string and not nullopt.
      // This allows us to fill and register scoped_name (also empty string) to
      // be used in references.
      result.unscoped_name = std::string();
    }
    if (result.unscoped_name) {
      result.scoped_name = scope_ + *result.unscoped_name;
      scoped_names_.emplace_back(
          entry.GetOffset(), *result.scoped_name);
    }
    return result;
  }

  std::string GetScopedNameForSymbol(size_t symbol_idx,
                                     const NameWithContext& name) {
    // This method is designed to resolve this topology:
    //   A: specification=B
    //   B: name="foo"
    // Any other topologies are rejected:
    //   * Name and specification in one DIE: checked right below.
    //   * Chain of specifications will result in symbol referencing another
    //     specification, which will not be in scoped_names_, because "name and
    //     specification in one DIE" is rejected.
    if (name.scoped_name) {
      if (name.specification) {
        Die() << "Entry has name " << *name.scoped_name
              << " and specification " << Hex(*name.specification);
      }
      return *name.scoped_name;
    }
    if (name.specification) {
      unresolved_symbol_specifications_.emplace_back(*name.specification,
                                                     symbol_idx);
      // Name will be filled in ResolveSymbolSpecifications
      return {};
    }
    Die() << "Entry should have either name or specification";
  }

  void ProcessVariable(Entry& entry) {
    auto name_with_context = GetNameWithContext(entry);

    auto referred_type = GetReferredType(entry);
    const Id referred_type_id = GetIdForEntry(referred_type);

    if (auto address = entry.MaybeGetAddress(DW_AT_location)) {
      // Only external variables with address are useful for ABI monitoring
      const auto new_symbol_idx = result_.symbols.size();
      result_.symbols.push_back(Types::Symbol{
          .name = GetScopedNameForSymbol(new_symbol_idx, name_with_context),
          .linkage_name = MaybeGetLinkageName(version_, entry),
          .address = *address,
          .id = referred_type_id});
    }
  }

  void ProcessFunction(Entry& entry) {
    Subprogram subprogram = GetSubprogram(entry);
    const Id id = AddProcessedNode<Function>(entry, std::move(subprogram.node));
    if (subprogram.external && subprogram.address) {
      // Only external functions with address are useful for ABI monitoring
      const auto new_symbol_idx = result_.symbols.size();
      result_.symbols.push_back(Types::Symbol{
          .name = GetScopedNameForSymbol(
              new_symbol_idx, subprogram.name_with_context),
          .linkage_name = std::move(subprogram.linkage_name),
          .address = *subprogram.address,
          .id = id});
    }
  }

  struct Subprogram {
    Function node;
    NameWithContext name_with_context;
    std::optional<std::string> linkage_name;
    std::optional<Address> address;
    bool external;
  };

  Subprogram GetSubprogram(Entry& entry) {
    const Id return_type_id = GetReferredTypeId(MaybeGetReferredType(entry));

    std::vector<Id> parameters;
    for (auto& child : entry.GetChildren()) {
      auto child_tag = child.GetTag();
      switch (child_tag) {
        case DW_TAG_formal_parameter:
          parameters.push_back(GetReferredTypeId(GetReferredType(child)));
          break;
        case DW_TAG_unspecified_parameters:
          // Note: C++ allows a single ... argument specification but C does
          // not. However, "extern int foo();" (note lack of "void" in
          // parameters) in C will produce the same DWARF as "extern int
          // foo(...);" in C++.
          CheckNoChildren(child);
          parameters.push_back(variadic_id_);
          break;
        case DW_TAG_enumeration_type:
        case DW_TAG_label:
        case DW_TAG_lexical_block:
        case DW_TAG_structure_type:
        case DW_TAG_class_type:
        case DW_TAG_union_type:
        case DW_TAG_typedef:
        case DW_TAG_const_type:
        case DW_TAG_volatile_type:
        case DW_TAG_restrict_type:
        case DW_TAG_atomic_type:
        case DW_TAG_array_type:
        case DW_TAG_pointer_type:
        case DW_TAG_reference_type:
        case DW_TAG_rvalue_reference_type:
        case DW_TAG_ptr_to_member_type:
        case DW_TAG_unspecified_type:
        case DW_TAG_inlined_subroutine:
        case DW_TAG_subprogram:
        case DW_TAG_variable:
        case DW_TAG_call_site:
        case DW_TAG_GNU_call_site:
          // TODO: Do not leak local types outside this scope.
          // TODO: It would be better to not process any
          // information that is function local but there is a dangling
          // reference Clang bug.
          Process(child);
          break;
        case DW_TAG_imported_declaration:
        case DW_TAG_imported_module:
          // For now information there is useless for ABI monitoring, but we
          // need to check that there is no missing information in descendants.
          CheckNoChildren(child);
          break;
        case DW_TAG_template_type_parameter:
        case DW_TAG_template_value_parameter:
        case DW_TAG_GNU_template_template_param:
        case DW_TAG_GNU_template_parameter_pack:
        case DW_TAG_GNU_formal_parameter_pack:
          // We just skip these as neither GCC nor Clang seem to use them
          // properly (resulting in no references to such DIEs).
          break;
        default:
          Die() << "Unexpected tag for child of function: " << Hex(child_tag)
                << ", " << EntryToString(child);
      }
    }

    return Subprogram{.node = Function(return_type_id, parameters),
                      .name_with_context = GetNameWithContext(entry),
                      .linkage_name = MaybeGetLinkageName(version_, entry),
                      .address = entry.MaybeGetAddress(DW_AT_low_pc),
                      .external = entry.GetFlag(DW_AT_external)};
  }

  // Allocate or get already allocated STG Id for Entry.
  Id GetIdForEntry(Entry& entry) {
    const auto offset = entry.GetOffset();
    const auto [it, emplaced] = id_map_.emplace(offset, Id(-1));
    if (emplaced) {
      it->second = graph_.Allocate();
    }
    return it->second;
  }

  // Same as GetIdForEntry, but returns "void_id_" for "unspecified" references,
  // because it is normal for DWARF (5.2 Unspecified Type Entries).
  Id GetReferredTypeId(std::optional<Entry> referred_type) {
    return referred_type ? GetIdForEntry(*referred_type) : void_id_;
  }

  // Wrapper for GetIdForEntry to allow lvalues.
  Id GetReferredTypeId(Entry referred_type) {
    return GetIdForEntry(referred_type);
  }

  // Populate Id from method above with processed Node.
  template <typename Node, typename... Args>
  Id AddProcessedNode(Entry& entry, Args&&... args) {
    const Id id = GetIdForEntry(entry);
    graph_.Set<Node>(id, std::forward<Args>(args)...);
    return id;
  }

  void AddNamedTypeNode(Id id) {
    result_.named_type_ids.push_back(id);
  }

  Graph& graph_;
  Id void_id_;
  Id variadic_id_;
  bool is_little_endian_binary_;
  const std::unique_ptr<Filter>& file_filter_;
  Types& result_;
  std::unordered_map<Dwarf_Off, Id> id_map_;
  std::vector<std::pair<Dwarf_Off, std::string>> scoped_names_;
  std::vector<std::pair<Dwarf_Off, size_t>> unresolved_symbol_specifications_;

  // Current scope.
  Scope scope_;
  int version_;
  dwarf::Files files_;
};

Types Process(Handler& dwarf, bool is_little_endian_binary,
              const std::unique_ptr<Filter>& file_filter, Graph& graph) {
  Types result;
  const Id void_id = graph.Add<Special>(Special::Kind::VOID);
  const Id variadic_id = graph.Add<Special>(Special::Kind::VARIADIC);
  // TODO: Scope Processor to compilation units?
  Processor processor(graph, void_id, variadic_id, is_little_endian_binary,
                      file_filter, result);
  for (auto& compilation_unit : dwarf.GetCompilationUnits()) {
    // Could fetch top-level attributes like compiler here.
    processor.ProcessCompilationUnit(compilation_unit);
  }
  processor.CheckUnresolvedIds();
  processor.ResolveSymbolSpecifications();

  return result;
}

}  // namespace dwarf
}  // namespace stg
