/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <elf.h>

#include <fstream>
#include <iostream>
#include <map>
#include <optional>
#include <queue>
#include <string>

#include <sys/param.h>

#include "dwarf_constants.h"
#include "dwarf_info.h"
#include "elf_reader.h"

#include <berberis/base/algorithm.h>
#include <berberis/base/stringprintf.h>
#include <json/json.h>

namespace {

using berberis::StringPrintf;

constexpr const char* kKindArray = "array";
constexpr const char* kKindAtomic = "atomic";
constexpr const char* kKindConst = "const";
constexpr const char* kKindClass = "class";
constexpr const char* kKindFunction = "function";
constexpr const char* kKindIncomplete = "incomplete";
constexpr const char* kKindRestrict = "restrict";
constexpr const char* kKindStruct = "struct";
constexpr const char* kKindUnion = "union";
constexpr const char* kKindVolatile = "volatile";

class JsonNameValue {
 public:
  JsonNameValue(const std::string name, const Json::Value& value) : name_(name), value_(value) {}
  const std::string& name() const { return name_; }

  const Json::Value& value() const { return value_; }

 private:
  std::string name_;
  Json::Value value_;
};

class TypeInfo {
 public:
  TypeInfo(uint64_t id, const char* kind, const std::string& name, uint64_t size_bits)
      : id_(id), kind_(kind), name_(name), size_bits_(size_bits) {}
  virtual ~TypeInfo() {}

  uint64_t id() const { return id_; }

  const char* kind() const { return kind_; }
  const std::string& name() const { return name_; }
  uint64_t size() const { return size_bits_; }

  virtual JsonNameValue GetJson() const = 0;

  virtual bool EqualsTo(const TypeInfo* other) const {
    // This is default implementation - should work for most TypeInfos
    return kind_ == other->kind_ && size_bits_ == other->size_bits_ && name_ == other->name_;
  }

  // It usually is just a name but for classes and function it represents just
  // the class or function name without 'class'/'func' prefix. Used to correctly
  // resolve names for nested classes/unions/...
  virtual const std::string& base_name() const { return name(); }

 private:
  uint64_t id_;

 protected:
  const char* kind_;
  std::string name_;
  uint64_t size_bits_;

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(TypeInfo);
};

void usage(const char* argv0) {
  printf("usage: %s [--filter=<path_to_filter_file>] <path_to_elf_file>\n", argv0);
}

__attribute__((__noreturn__)) void error(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  vfprintf(stderr, fmt, ap);
  va_end(ap);
  fprintf(stderr, "\n");
  exit(1);
}

void warning(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  vfprintf(stderr, fmt, ap);
  va_end(ap);
  fprintf(stderr, "\n");
}

// TODO: This method does not provide necessary guarantees for being able to
// compare anonymous types by name.
//
// * There are number of situation where a type does not have a name
// * 1. There are anonymous function pointers
// * 2. Unnamed unions and structs inside other unions or structs
// The current approach is to use global counter.
//
// Note that there is no guarantee that these names are going to be same for
// a library compiled on different architectures.
std::string GenerateGlobalAnonName() {
  static size_t counter = 0;
  return StringPrintf("#%zd", ++counter);
}

class TypeInfoFunction : public TypeInfo {
 public:
  TypeInfoFunction(uint64_t id, const std::string& name, const std::string& base_name)
      : TypeInfo(id, kKindFunction, name, 0),
        base_name_(base_name),
        has_variadic_args_(false),
        is_virtual_method_(false) {}

  virtual ~TypeInfoFunction() {}

  void SetReturnType(const std::string& return_type) { return_type_ = return_type; }

  void SetHasVariadicArgs(bool has_variadic_args) { has_variadic_args_ = has_variadic_args; }

  void SetCallingConvention(const std::string& calling_convention) {
    calling_convention_ = calling_convention;
  }

  void AddParam(const std::string& param_name) { params_.push_back(param_name); }

  virtual bool EqualsTo(const TypeInfo*) const override {
    // This method is not applicable for function types.
    return false;
  }

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["has_variadic_args"] = has_variadic_args_;
    obj["is_virtual_method"] = is_virtual_method_;
    obj["kind"] = kind_;
    Json::Value params_array(Json::arrayValue);
    for (const auto& param : params_) {
      params_array.append(param);
    }
    obj["params"] = params_array;
    obj["return_type"] = return_type_;
    obj["size"] = Json::UInt64(size_bits_);

    if (!calling_convention_.empty()) {
      obj["calling_convention"] = calling_convention_;
    }

    return JsonNameValue(name_, obj);
  }

  virtual const std::string& base_name() const override { return base_name_; }

 private:
  std::string base_name_;
  bool has_variadic_args_;
  bool is_virtual_method_;
  std::string return_type_;
  std::string calling_convention_;
  std::vector<std::string> params_;
};

enum class ReferenceType { pointer, reference, rvalue_reference };

class TypeInfoReference : public TypeInfo {
 public:
  TypeInfoReference(uint64_t id,
                    const char* kind,
                    const std::string& name,
                    uint64_t size_bits,
                    const std::string& pointee_type)
      : TypeInfo(id, kind, name, size_bits), pointee_type_(pointee_type) {}

  virtual ~TypeInfoReference() {}

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["kind"] = kind_;
    obj["pointee_type"] = pointee_type_;
    obj["size"] = Json::UInt64(size_bits_);

    return JsonNameValue(name_, obj);
  }

 private:
  std::string pointee_type_;
};

class TypeInfoModifier : public TypeInfo {
 public:
  TypeInfoModifier(uint64_t id,
                   const char* kind,
                   const std::string& name,
                   uint64_t size_bits,
                   const std::string& base_type)
      : TypeInfo(id, kind, name, size_bits), base_type_(base_type) {}

  virtual ~TypeInfoModifier() {}

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["kind"] = kind_;
    obj["base_type"] = base_type_;
    obj["size"] = Json::UInt64(size_bits_);

    return JsonNameValue(name_, obj);
  }

 private:
  std::string base_type_;
};

class TypeInfoIncomplete : public TypeInfo {
 public:
  TypeInfoIncomplete(uint64_t id, const std::string& name, const std::string& base_name)
      : TypeInfo(id, kKindIncomplete, name, 0), base_name_(base_name) {}
  virtual ~TypeInfoIncomplete() {}

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["kind"] = kind_;

    return JsonNameValue(name_, obj);
  }

  virtual const std::string& base_name() const override { return base_name_; }

 private:
  std::string base_name_;
};

class TypeInfoVoid : public TypeInfoIncomplete {
 public:
  TypeInfoVoid() : TypeInfoIncomplete(0, "void", "void") {}
  virtual ~TypeInfoVoid() {}
};

class TypeInfoBase : public TypeInfo {
 public:
  TypeInfoBase(uint64_t id,
               const std::string& name,
               uint64_t size_bits,
               const char* kind,
               bool is_signed)
      : TypeInfo(id, kind, name, size_bits), is_signed_(is_signed) {}
  virtual ~TypeInfoBase() {}

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["kind"] = kind_;
    obj["signed"] = is_signed_;
    obj["size"] = Json::UInt64(size_bits_);

    return JsonNameValue(name_, obj);
  }

 private:
  bool is_signed_;
};

class TypeInfoArray : public TypeInfo {
 public:
  TypeInfoArray(uint64_t id,
                const std::string& name,
                uint64_t size_bits,
                const std::string& element_type)
      : TypeInfo(id, kKindArray, name, size_bits), element_type_(element_type) {}
  virtual ~TypeInfoArray() {}

  virtual JsonNameValue GetJson() const override {
    Json::Value obj(Json::objectValue);

    obj["kind"] = kind_;
    obj["element_type"] = element_type_;
    obj["size"] = Json::UInt64(size_bits_);

    return JsonNameValue(name_, obj);
  }

 private:
  std::string element_type_;
};

class TypeInfoClassField {
 public:
  TypeInfoClassField() : offset_bits_(0) {}
  TypeInfoClassField(const std::string& name, const std::string& type_name, uint32_t offset_bits)
      : name_(name), type_name_(type_name), offset_bits_(offset_bits) {}

  TypeInfoClassField(TypeInfoClassField&& that) = default;
  TypeInfoClassField& operator=(TypeInfoClassField&& that) = default;

  const std::string& name() const { return name_; }
  const std::string& type_name() const { return type_name_; }
  uint64_t offset_bits() const { return offset_bits_; }

 private:
  std::string name_;
  std::string type_name_;
  uint64_t offset_bits_;

  friend bool operator!=(const TypeInfoClassField& one, const TypeInfoClassField& two);

  DISALLOW_COPY_AND_ASSIGN(TypeInfoClassField);
};

bool operator!=(const TypeInfoClassField& one, const TypeInfoClassField& two) {
  return one.offset_bits_ != two.offset_bits_ || one.name_ != two.name_ /* ||
         one.type_name_ != two.type_name_*/
      ;
}

class TypeInfoClass : public TypeInfo {
 public:
  TypeInfoClass(uint64_t id,
                const char* kind,
                const std::string& name,
                uint64_t size_bits,
                const std::string& base_name)
      : TypeInfo(id, kind, name, size_bits), base_name_(base_name) {}
  virtual ~TypeInfoClass() {}

  void AddField(const std::string& name, const std::string& type_name, uint32_t offset_bits) {
    fields_.push_back(TypeInfoClassField(name, type_name, offset_bits));
  }

  void AddInheritance(const std::string& name) { inheritance_types_.push_back(name); }

  virtual bool EqualsTo(const TypeInfo* other) const override {
    if (!TypeInfo::EqualsTo(other)) {
      return false;
    }

    auto other_class = static_cast<const TypeInfoClass*>(other);

    if (fields_.size() != other_class->fields_.size()) {
      return false;
    }

    for (size_t i = 0; i < fields_.size(); ++i) {
      if (fields_[i] != other_class->fields_[i]) {
        return false;
      }
    }

    return true;
  }

  virtual JsonNameValue GetJson() const override {
    Json::Value fields(Json::arrayValue);

    for (auto& field : fields_) {
      Json::Value field_obj(Json::objectValue);
      field_obj["name"] = field.name();
      field_obj["offset"] = Json::UInt64(field.offset_bits());
      field_obj["type"] = field.type_name();
      fields.append(field_obj);
    }

    Json::Value inheritance_types_array(Json::arrayValue);
    for (const auto& inheritance_type : inheritance_types_) {
      inheritance_types_array.append(inheritance_type);
    }

    Json::Value obj(Json::objectValue);

    obj["inheritance"] = inheritance_types_array;
    obj["fields"] = fields;
    obj["kind"] = kind_;
    obj["size"] = Json::UInt64(size_bits_);

    return JsonNameValue(name_, obj);
  }

  virtual const std::string& base_name() const override { return base_name_; }

 private:
  std::string base_name_;
  std::vector<TypeInfoClassField> fields_;
  std::vector<std::string> inheritance_types_;
};

// Returns nullptr for 'void'
const nogrod::DwarfDie* GetAtTypeDie(const nogrod::DwarfDie* die, const nogrod::DwarfInfo* info) {
  auto offset = die->GetUint64Attribute(DW_AT_type);
  if (offset) {
    auto target_die = info->GetDieByOffset(offset.value());
    if (target_die == nullptr) {
      error("Couldn't find die for type of die at offset 0x%" PRIx64 " (DW_AT_type=0x%" PRIx64 ")",
            die->offset(),
            offset.value());
    }

    return target_die;
  }

  // If there is no DW_AT_type check DW_AT_specification
  auto specification_offset = die->GetUint64Attribute(DW_AT_specification);
  if (!specification_offset) {  // this is 'void'
    return nullptr;
  }

  auto specification_die = info->GetDieByOffset(specification_offset.value());
  if (specification_die == nullptr) {
    error("Couldn't find die for specification of die at offset 0x%" PRIx64
          " (DW_AT_type=0x%" PRIx64 ")",
          die->offset(),
          specification_offset.value());
  }

  return GetAtTypeDie(specification_die, info);
}

std::unique_ptr<TypeInfo> ParseBaseType(const nogrod::DwarfDie* die) {
  auto encoding_attr = die->GetUint64Attribute(DW_AT_encoding);
  if (!encoding_attr) {
    error("Couldn't find DW_AT_encoding for DW_TAG_base_type at offset 0x%" PRIx64, die->offset());
  }
  uint64_t encoding = encoding_attr.value();

  auto size_attr = die->GetUint64Attribute(DW_AT_byte_size);
  uint64_t size = 0;

  if ((encoding == DW_ATE_signed_char || encoding == DW_ATE_unsigned_char) && !size_attr) {
    size = 1;
  } else {
    if (!size_attr) {
      error("Couldn't find DW_AT_byte_size for DW_TAG_base_type at offset 0x%" PRIx64,
            die->offset());
    }
    size = size_attr.value();
  }

  if (size > 128 || !powerof2(size)) {
    error("Unsupported size %" PRId64 " for DW_TAG_base_type at offset 0x%" PRIx64
          " - must be no greater than 128 and a power of 2",
          size,
          die->offset());
  }

  bool is_signed = false;
  const char* kind;
  const char* prefix;

  switch (encoding) {
    case DW_ATE_signed:
      kind = "int";
      prefix = "int";
      is_signed = true;
      break;
    case DW_ATE_unsigned:
      kind = "int";
      prefix = "unsigned int";
      is_signed = false;
      break;
    case DW_ATE_boolean:
      kind = "int";
      prefix = "bool";
      is_signed = false;
      break;
    case DW_ATE_float:
      kind = "float";
      prefix = "float";
      is_signed = true;
      break;
    case DW_ATE_signed_char:
      kind = "char";
      prefix = "char";
      is_signed = true;
      break;
    case DW_ATE_unsigned_char:
    case DW_ATE_UTF:
      kind = "char";
      prefix = "unsigned char";
      is_signed = false;
      break;
    default:
      error("Unsupported DW_AT_encoding=0x%" PRIx64 " for DW_TAG_base_type at offset 0x%" PRIx64,
            encoding,
            die->offset());
  }

  std::string name = prefix;
  if (strcmp(prefix, "bool") != 0) {
    name = StringPrintf("%s%" PRId64, prefix, size * CHAR_BIT);
  }

  return std::unique_ptr<TypeInfoBase>(
      new TypeInfoBase(die->offset(), name, size * CHAR_BIT, kind, is_signed));
}

std::unique_ptr<TypeInfo> ParseEnumType(const nogrod::DwarfDie* die) {
  auto size_attr = die->GetUint64Attribute(DW_AT_byte_size);
  if (!size_attr) {
    error("Couldn't find DW_AT_byte_size for DW_TAG_base_type at offset 0x%" PRIx64, die->offset());
  }

  uint64_t size = size_attr.value() * CHAR_BIT;

  std::string name = StringPrintf("%s%" PRId64, "unsigned int", size);

  return std::unique_ptr<TypeInfoBase>(new TypeInfoBase(die->offset(), name, size, "int", false));
}

std::optional<std::string> GetDieName(const nogrod::DwarfDie* die) {
  auto die_name = die->GetStringAttribute(DW_AT_linkage_name);

  if (!die_name) {
    die_name = die->GetStringAttribute(DW_AT_name);
  }

  return die_name;
}

std::string UpdateName(std::string original, bool is_first, std::string base_name) {
  if (!is_first) {
    original += ", ";
  }
  std::vector<std::string> kind_strs{kKindStruct,
                                     kKindClass,
                                     kKindUnion,
                                     kKindArray,
                                     kKindAtomic,
                                     kKindFunction,
                                     kKindIncomplete,
                                     kKindRestrict,
                                     kKindVolatile};
  for (const auto& kind_str : kind_strs) {
    auto index = base_name.find(kind_str);
    if (index != std::string::npos) {
      // remove "kind" prefix and a following space
      base_name.erase(index, kind_str.length() + 1);
    }
  }
  original += base_name;
  return original;
}

std::string GenerateClassName(const auto& children,
                              auto class_name,
                              const nogrod::DwarfDie* die,
                              const nogrod::DwarfInfo* dwarf_info,
                              std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  std::string template_params = "";
  for (size_t i = 0; i < children.size(); ++i) {
    auto child = children[i];

    if (child->tag() == DW_TAG_GNU_template_parameter_pack) {
      const auto& parameter_pack_children = child->children();
      for (auto child_child : parameter_pack_children) {
        if (child_child->tag() == DW_TAG_template_type_parameter ||
            child_child->tag() == DW_TAG_template_value_parameter) {
          auto temp_type_die = GetAtTypeDie(child_child, dwarf_info);
          if (temp_type_die == nullptr) {
            continue;
          }
          auto template_type_info = ParseDie(temp_type_die, child, dwarf_info, types);
          template_params =
              UpdateName(template_params, i == 0, (template_type_info->base_name()).c_str());
          continue;
        }
      }
      continue;
    }

    if (child->tag() == DW_TAG_template_type_parameter ||
        child->tag() == DW_TAG_template_value_parameter) {
      auto child_type_die = GetAtTypeDie(child, dwarf_info);
      if (child_type_die == nullptr) {
        continue;
      }
      auto child_type_info = ParseDie(child_type_die, die, dwarf_info, types);

      if (std::string_view{child_type_info->base_name()}.find("bool") != std::string_view::npos) {
        auto num = child->GetUint64Attribute(DW_AT_const_value);
        if (num) {
          // Using the value of bool to avoid dedup failure
          std::string bool_val = num.value() == 0 ? "false" : "true";
          template_params = UpdateName(template_params, i == 0, bool_val);
        }
      } else {
        template_params =
            UpdateName(template_params, i == 0, (child_type_info->base_name()).c_str());
      }
      continue;
    }
  }

  if (!template_params.empty()) {
    return class_name + "<" + template_params + ">";
  }

  return class_name;
}

const TypeInfo* ParseDie(const nogrod::DwarfDie* start,
                         const nogrod::DwarfDie* referenced_by,
                         const nogrod::DwarfInfo* dwarf_info,
                         std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types);

const TypeInfo* ParseClass(const char* kind,
                           const nogrod::DwarfDie* die,
                           const nogrod::DwarfDie* referenced_by,
                           const nogrod::DwarfInfo* dwarf_info,
                           std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  auto die_name = GetDieName(die);
  auto die_tag = die->tag();
  // Use typedef name in case if this class is part of
  // "typedef struct { .. } blah;" declaration
  if (!die_name && referenced_by != nullptr && referenced_by->tag() == DW_TAG_typedef) {
    die_name = GetDieName(referenced_by);
    die_tag = referenced_by->tag();
  }

  std::string class_name;
  if (die_name) {
    class_name = die_name.value();
  } else {
    class_name = GenerateGlobalAnonName();
  }

  auto parent_die = die->parent();

  if (parent_die->tag() == DW_TAG_structure_type || parent_die->tag() == DW_TAG_class_type ||
      parent_die->tag() == DW_TAG_union_type) {
    const TypeInfo* parent_type_info = ParseDie(parent_die, nullptr, dwarf_info, types);
    CHECK(parent_type_info != nullptr);
    class_name = StringPrintf("%s::%s", parent_type_info->base_name().c_str(), class_name.c_str());
  }

  while (parent_die->tag() == DW_TAG_namespace) {
    // Note: if type placed in anonymous namespace is used with template, e.g.,
    // "icu_65::MaybeStackArray<icu_65::(anonymous namespace)::LocaleAndWeight, 20>"
    // then string "(anonymous namespace)" is used by clang.  But the namespace object
    // itself doesn't have a name.  Assign name "(anonymous namespace)" for consistency.
    static constexpr const char* kAnonymousNamespaceName = "(anonymous namespace)";
    auto parent_die_optional_name = GetDieName(parent_die);
    const char* parent_die_name = parent_die_optional_name
                                      ? parent_die_optional_name.value().c_str()
                                      : kAnonymousNamespaceName;
    class_name = StringPrintf("%s::%s", parent_die_name, class_name.c_str());
    parent_die = parent_die->parent();
  }

  std::string name = StringPrintf("%s %s", kind, class_name.c_str());

  // TODO: align????
  bool incomplete = die->GetBoolAttributeOr(DW_AT_declaration, false);

  if (incomplete) {
    if (!die_name) {
      warning("The incomplete type at offset 0x%" PRIx64 " referenced by \"%s\"@0x%" PRIx64
              " is anonymous (ignoring)",
              die->offset(),
              referenced_by != nullptr ? GetDieName(referenced_by).value_or("<no name>").c_str()
                                       : "<null>",
              referenced_by != nullptr ? referenced_by->offset() : 0);
    }

    std::unique_ptr<TypeInfoIncomplete> incomplete_type_holder(
        new TypeInfoIncomplete(die->offset(), name, class_name));
    TypeInfoIncomplete* result = incomplete_type_holder.get();
    (*types)[die->offset()] = std::move(incomplete_type_holder);
    // An incomplete struct - find other dies by name and parse them too.
    // This should solve the case where actual type is declared in another
    // compilation unit. We could get some false positives - this is ok.
    std::vector<const nogrod::DwarfDie*> dies = dwarf_info->FindDiesByName(class_name);
    if (dies.empty()) {
      warning(
          "Couldn't find dies by name \"%s\" for incomplete type at the offset 0x%x (likely "
          "because it had no name) - ignoring",
          class_name.c_str(),
          result->id());
    }

    for (auto namefellow_die : dies) {
      // Limit to the tag of the original incomplete type
      if (namefellow_die->tag() != die_tag) {
        continue;
      }
      ParseDie(namefellow_die, nullptr, dwarf_info, types);
    }
    return result;
  }

  auto size = die->GetUint64Attribute(DW_AT_byte_size);

  if (!size) {
    error("No DW_AT_byte_size specified for type at offset 0x%" PRIx64, die->offset());
  }

  const auto& children = die->children();
  class_name = GenerateClassName(children, class_name, die, dwarf_info, types);
  name = StringPrintf("%s %s", kind, class_name.c_str());
  std::unique_ptr<TypeInfoClass> type_info_holder(
      new TypeInfoClass(die->offset(), kind, name, size.value() * CHAR_BIT, class_name));
  TypeInfoClass* type_info = type_info_holder.get();
  (*types)[die->offset()] = std::move(type_info_holder);

  for (auto child : children) {
    if (child->tag() == DW_TAG_subprogram) {
      continue;
    }

    // Skip nested types - they are parsed only if referenced by a DW_AT_member (see below).
    if (child->tag() == DW_TAG_structure_type || child->tag() == DW_TAG_union_type ||
        child->tag() == DW_TAG_class_type || child->tag() == DW_TAG_enumeration_type ||
        child->tag() == DW_TAG_typedef) {
      continue;
    }

    if (child->tag() == DW_TAG_inheritance) {
      auto inheritance_die = GetAtTypeDie(child, dwarf_info);
      CHECK(inheritance_die != nullptr);  // voids are not allowed here.
      auto inheritance_type_info = ParseDie(inheritance_die, die, dwarf_info, types);
      type_info->AddInheritance(inheritance_type_info->name());
      continue;
    }

    if (child->tag() == DW_TAG_template_type_parameter ||
        child->tag() == DW_TAG_template_value_parameter ||
        child->tag() == DW_TAG_GNU_template_parameter_pack ||
        child->tag() == DW_TAG_GNU_template_template_param) {
      // These types do not affect struct layout unless they are used
      // for members. This is why we should probably ignore them here.
      // auto type_die = GetAtTypeDie(child, dwarf_info);
      // ParseDie(type_die, dwarf_info, types);
      continue;
    }

    if (child->tag() != DW_TAG_member) {  // see if this is the case...
      error("Unexpected tag 0x%x for the die at offset 0x%" PRIx64 ", expected DW_TAG_member",
            child->tag(),
            child->offset());
    }

    if (child->GetBoolAttributeOr(DW_AT_external, false)) {
      // DW_AT_external is dwarvish for static member
      continue;
    }

    auto member_die = GetAtTypeDie(child, dwarf_info);
    CHECK(member_die != nullptr);
    auto member_type_info = ParseDie(member_die, die, dwarf_info, types);

    auto name = child->GetStringAttribute(DW_AT_name);

    // Nested unions and structs may not have a name.
    if (!name && member_die->tag() != DW_TAG_union_type &&
        member_die->tag() != DW_TAG_structure_type) {
      error("DW_AT_name is not set for the die at offset 0x%" PRIx64, child->offset());
    }

    std::string type_name = member_type_info->name();

    // TODO: handle bit offset
    auto offset = child->GetUint64AttributeOr(DW_AT_data_member_location, 0);
    type_info->AddField(name.value_or(""), type_name, offset * CHAR_BIT);
  }

  // is_polymorphic??

  return type_info;
}

const TypeInfo* ParseFunction(const nogrod::DwarfDie* die,
                              const nogrod::DwarfInfo* dwarf_info,
                              std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  auto die_name = GetDieName(die);
  if (!die_name && die->tag() != DW_TAG_subroutine_type) {
    error("Couldn't resolve name for die at offset=0x%" PRIx64, die->offset());
  }

  std::string function_name = die_name ? die_name.value() : GenerateGlobalAnonName();

  std::string name = StringPrintf("func %s", function_name.c_str());

  std::unique_ptr<TypeInfoFunction> type_info_holder(
      new TypeInfoFunction(die->offset(), name, function_name));
  TypeInfoFunction* type_info = type_info_holder.get();
  (*types)[die->offset()] = std::move(type_info_holder);

  auto return_die = GetAtTypeDie(die, dwarf_info);
  type_info->SetReturnType(ParseDie(return_die, die, dwarf_info, types)->name());

  // This is special case of hard-fp (AAPCS_VFP)
  if (die->GetUint64AttributeOr(DW_AT_calling_convention, 0) == DW_CC_LLVM_AAPCS_VFP) {
    type_info->SetCallingConvention("aapcs-vfp");
  }

  // parse parameters
  const auto& children = die->children();
  for (auto child : children) {
    if (child->tag() == DW_TAG_formal_parameter) {
      auto param_die = GetAtTypeDie(child, dwarf_info);
      // presumably we cannot have void formal parameter... DW_AT_type is
      // required here
      CHECK(param_die != nullptr);  // FAIL_IF?
      type_info->AddParam(ParseDie(param_die, die, dwarf_info, types)->name());
    } else if (child->tag() == DW_TAG_unspecified_parameters) {
      type_info->SetHasVariadicArgs(true);
      break;  // No more formal_parameters after this. TODO: replace with stricter check maybe?
    }
  }

  return type_info;
}

std::unique_ptr<TypeInfo> ParseReference(
    const ReferenceType reference_type,
    const nogrod::DwarfDie* die,
    const nogrod::DwarfInfo* dwarf_info,
    std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  auto referenced_die = GetAtTypeDie(die, dwarf_info);
  std::string referenced_type_name = ParseDie(referenced_die, die, dwarf_info, types)->name();
  std::string name = referenced_type_name;
  const char* kind = nullptr;

  switch (reference_type) {
    case ReferenceType::pointer:
      name += "*";
      kind = "pointer";
      break;
    case ReferenceType::reference:
      name += "&";
      kind = "reference";
      break;
    case ReferenceType::rvalue_reference:
      name += "&&";
      kind = "rvalue_reference";
      break;
  }

  return std::make_unique<TypeInfoReference>(
      die->offset(),
      kind,
      name,
      die->compilation_unit_header()->address_size() * CHAR_BIT,
      referenced_type_name);
}

std::unique_ptr<TypeInfo> ParseModifier(
    const char* kind,
    const nogrod::DwarfDie* die,
    const nogrod::DwarfInfo* dwarf_info,
    std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  // The only field we need is base_type
  auto base_die = GetAtTypeDie(die, dwarf_info);
  auto base_type = ParseDie(base_die, die, dwarf_info, types);
  std::string base_type_name = base_type->name();
  uint64_t base_type_size = base_type->size();

  std::string name = StringPrintf("%s %s", base_type_name.c_str(), kind);

  return std::make_unique<TypeInfoModifier>(
      die->offset(), kind, name, base_type_size, base_type_name);
}

std::unique_ptr<TypeInfo> ParseArray(
    const nogrod::DwarfDie* die,
    const nogrod::DwarfInfo* dwarf_info,
    std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  uint64_t count = 0;

  auto element_die = GetAtTypeDie(die, dwarf_info);
  if (element_die == nullptr) {
    error("'void' cannot be element type of an array (die at offset 0x%" PRIx64 ")", die->offset());
  }

  auto element_type = ParseDie(element_die, die, dwarf_info, types);

  auto children = die->children();

  std::string name = element_type->name();

  for (auto child : die->children()) {
    if (child->tag() != DW_TAG_subrange_type) {
      error("Unexpected tag 0x%x for the die at offset 0x%" PRIx64
            ", expected DW_TAG_subrange_type",
            child->tag(),
            child->offset());
    }

    auto count_attr = child->GetUint64Attribute(DW_AT_count);
    if (count_attr) {
      count = count_attr.value();
    } else {  // use DW_AT_upper_bound/lower_bound
      count = child->GetUint64AttributeOr(DW_AT_upper_bound, 0) -
              child->GetUint64AttributeOr(DW_AT_lower_bound, 0) + 1;
    }

    name += StringPrintf("[%" PRId64 "]", count);
  }

  return std::make_unique<TypeInfoArray>(
      die->offset(), name, count * element_type->size(), element_type->name());
}

std::unique_ptr<TypeInfo> ParseUnspecifiedType(const nogrod::DwarfDie* die) {
  // The only unspecified_type we support is nullptr_t
  auto die_name = GetDieName(die);
  if (!die_name) {
    error("Couldn't resolve name for die at offset=0x%" PRIx64, die->offset());
  }

  if (die_name.value() != "decltype(nullptr)") {
    error("Unspecified type \"%s\" at offset 0x%" PRIx64
          " is not supported "
          "(the only supported unspecified type is nullptr_t)",
          die_name.value().c_str(),
          die->offset());
  }

  return std::make_unique<TypeInfoBase>(die->offset(), die_name.value(), 32, "nullptr_t", false);
}

const TypeInfo* ParseDie(const nogrod::DwarfDie* die,
                         const nogrod::DwarfDie* referenced_by,
                         const nogrod::DwarfInfo* dwarf_info,
                         std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>>* types) {
  if (die == nullptr) {
    auto it = types->find(0);
    if (it != types->end()) {
      return it->second.get();
    } else {
      std::unique_ptr<TypeInfo> void_type(new TypeInfoVoid());
      TypeInfo* result = void_type.get();
      (*types)[0] = std::move(void_type);
      return result;
    }
  }

  auto it = types->find(die->offset());
  if (it != types->end()) {
    return it->second.get();
  }

  std::unique_ptr<TypeInfo> type_info;

  switch (die->tag()) {
    case DW_TAG_subprogram:
    case DW_TAG_subroutine_type:
    case DW_TAG_label:
      return ParseFunction(die, dwarf_info, types);
    case DW_TAG_pointer_type:
    case DW_TAG_ptr_to_member_type:
      type_info = ParseReference(ReferenceType::pointer, die, dwarf_info, types);
      break;
    case DW_TAG_reference_type:
      type_info = ParseReference(ReferenceType::reference, die, dwarf_info, types);
      break;
    case DW_TAG_rvalue_reference_type:
      type_info = ParseReference(ReferenceType::rvalue_reference, die, dwarf_info, types);
      break;
    case DW_TAG_atomic_type:
      type_info = ParseModifier(kKindAtomic, die, dwarf_info, types);
      break;
    case DW_TAG_const_type:
      type_info = ParseModifier(kKindConst, die, dwarf_info, types);
      break;
    case DW_TAG_restrict_type:
      type_info = ParseModifier(kKindRestrict, die, dwarf_info, types);
      break;
    case DW_TAG_volatile_type:
      type_info = ParseModifier(kKindVolatile, die, dwarf_info, types);
      break;
    case DW_TAG_typedef: {
      auto typedef_type = GetAtTypeDie(die, dwarf_info);
      return ParseDie(typedef_type, die, dwarf_info, types);
    }
    case DW_TAG_structure_type:
      return ParseClass(kKindStruct, die, referenced_by, dwarf_info, types);
    case DW_TAG_class_type:
      return ParseClass(kKindClass, die, referenced_by, dwarf_info, types);
    case DW_TAG_union_type:
      return ParseClass(kKindUnion, die, referenced_by, dwarf_info, types);
    case DW_TAG_base_type:
      type_info = ParseBaseType(die);
      break;
    case DW_TAG_enumeration_type:
      type_info = ParseEnumType(die);
      break;
    case DW_TAG_unspecified_type:
      type_info = ParseUnspecifiedType(die);
      break;
    case DW_TAG_array_type:
      type_info = ParseArray(die, dwarf_info, types);
      break;
    default:
      error("Unsupported die tag: 0x%x at the offset 0x%x", die->tag(), die->offset());
  }

  CHECK(type_info);

  const TypeInfo* result = type_info.get();
  (*types)[die->offset()] = std::move(type_info);
  return result;
}

bool IsModifierType(const TypeInfo* type) {
  std::string kind = type->kind();
  return kind == kKindConst || kind == kKindVolatile || kind == kKindRestrict;
}

bool IsArrayType(const TypeInfo* type) {
  return type->kind() == kKindArray;
}

void warning_too_many_dies(const std::string& symbol_name,
                           const std::vector<const nogrod::DwarfDie*>& dies) {
  std::string offsets;
  for (auto die : dies) {
    offsets += StringPrintf("0x%" PRIx64 " ", die->offset());
  }

  warning("Too many DIEs for %s - offsets=[ %s] - will consider only the first one",
          symbol_name.c_str(),
          offsets.c_str());
}

__attribute__((__noreturn__)) void error_unsuccessful_dedup(
    const std::string& type_name,
    const std::vector<const TypeInfo*>& types) {
  std::string type_infos;
  for (auto type : types) {
    type_infos += StringPrintf("(id=0x%" PRIx64 ", kind=\'%s\', name='%s', size=%" PRId64 ") ",
                               type->id(),
                               type->kind(),
                               type->name().c_str(),
                               type->size());
  }

  error("Unsuccessful dedup for %s, number of types left=%d, type_infos=[%s]",
        type_name.c_str(),
        types.size(),
        type_infos.c_str());
}

const nogrod::DwarfDie* FindBestDie(const nogrod::DwarfInfo* dwarf_info, const std::string& name) {
  std::vector<const nogrod::DwarfDie*> dies = dwarf_info->FindDiesByName(name);
  if (dies.empty()) {
    return nullptr;
  }

  const nogrod::DwarfDie* variable_die = nullptr;
  const nogrod::DwarfDie* subprogram_die = nullptr;
  const nogrod::DwarfDie* label_die = nullptr;

  for (const auto die : dies) {
    if (die->tag() == DW_TAG_variable) {
      if (variable_die != nullptr) {
        warning("Multiple variable DIEs for %s - will consider only the first one", name.c_str());
      } else {
        variable_die = die;
      }
    } else if (die->tag() == DW_TAG_subprogram) {
      if (subprogram_die != nullptr) {
        warning("Multiple subprogram DIEs for %s - will consider only the first one", name.c_str());
      } else {
        subprogram_die = die;
      }
    } else if (die->tag() == DW_TAG_label) {
      if (label_die != nullptr) {
        warning("Multiple label DIEs for %s - will consider only the first one", name.c_str());
      } else {
        label_die = die;
      }
    }
  }

  if (variable_die != nullptr) {
    return variable_die;
  }
  if (subprogram_die != nullptr) {
    return subprogram_die;
  }
  if (label_die != nullptr) {
    return label_die;
  }

  if (dies.size() > 1) {
    warning_too_many_dies(name, dies);
  }
  return dies[0];
}

bool ReadFileToStringVector(const char* name, std::vector<std::string>* lines) {
  std::ifstream fs(name);
  if (!fs.is_open()) {
    return false;
  }
  std::string line;
  while (std::getline(fs, line)) {
    lines->push_back(line);
  }
  return true;
}

}  // namespace

int main(int argc, const char** argv) {
  const char* elf_file_name = nullptr;
  const char* filter_file_name = nullptr;

  if (argc == 2) {
    elf_file_name = argv[1];
  } else if (argc == 3 && strncmp(argv[1], "--filter=", 9) == 0) {
    filter_file_name = argv[1] + 9;
    elf_file_name = argv[2];
  } else {
    usage(argv[0]);
    return 0;
  }

  std::string error_msg;

  std::unique_ptr<nogrod::ElfFile> elf_file = nogrod::ElfFile::Load(elf_file_name, &error_msg);

  if (!elf_file) {
    error("Error loading elf-file \"%s\": %s", elf_file_name, error_msg.c_str());
  }

  std::vector<std::string> names;

  if (filter_file_name) {
    if (!ReadFileToStringVector(filter_file_name, &names)) {
      error("Error reading symbols from \"%s\"", filter_file_name);
    }
  } else {
    if (!elf_file->ReadExportedSymbols(&names, &error_msg)) {
      error("Error reading exported symbols from \"%s\": %s", elf_file_name, error_msg.c_str());
    }
  }

  std::unique_ptr<nogrod::DwarfInfo> dwarf_info = elf_file->ReadDwarfInfo(&error_msg);
  if (!dwarf_info) {
    error("Error loading dwarf_info from \"%s\": %s", elf_file_name, error_msg.c_str());
  }

  // map: type id (offset) -> type
  std::unordered_map<uint64_t, std::unique_ptr<TypeInfo>> types;

  // map: symbol name -> type id (offset)
  std::map<std::string, uint64_t> symbols;

  for (const auto& name : names) {
    const nogrod::DwarfDie* die = FindBestDie(dwarf_info.get(), name);
    if (die == nullptr) {
      warning("Couldn't find compatible DIE for %s - skipping...", name.c_str());
      continue;
    }

    if (die->tag() == DW_TAG_subprogram || die->tag() == DW_TAG_label) {
      const TypeInfo* subprogram_type = ParseDie(die, nullptr, dwarf_info.get(), &types);
      symbols[name] = subprogram_type->id();
    } else if (die->tag() == DW_TAG_variable) {
      auto variable_type_die = GetAtTypeDie(die, dwarf_info.get());
      const TypeInfo* variable_type = ParseDie(variable_type_die, die, dwarf_info.get(), &types);
      symbols[name] = variable_type->id();
    } else {  // Something else
      // TODO(random-googler): parse something else meaningfully...
      ParseDie(die, nullptr, dwarf_info.get(), &types);
    }
  }

  Json::Value root(Json::objectValue);
  Json::Value symbols_json(Json::objectValue);
  for (const auto& symbol : symbols) {
    auto& type_name = types[symbol.second]->name();
    symbols_json[symbol.first]["type"] = type_name;
  }

  root["symbols"] = symbols_json;

  // Sort types by name.
  std::map<std::string, std::vector<const TypeInfo*>> types_by_name;
  for (auto& elem : types) {
    const TypeInfo* type_info = elem.second.get();
    const std::string& name = type_info->name();
    std::vector<const TypeInfo*>& types_list = types_by_name[name];
    // Remove duplicate types.
    bool type_info_exists = berberis::ContainsIf(
        types_list, [type_info](const TypeInfo* element) { return element->EqualsTo(type_info); });
    if (!type_info_exists) {
      types_list.push_back(type_info);
    }
  }

  // Second pass
  for (auto& entry : types_by_name) {
    auto& types = entry.second;
    if (types.size() == 1) {
      continue;
    }

    // Remove incomplete types
    // TODO: Improve this by removing all types referencing the incomplete type.
    // Once it is done the next step (removing modifiers and arrays with size=0)
    // can be removed as well.
    types.erase(
        std::remove_if(types.begin(),
                       types.end(),
                       [](const TypeInfo* element) { return element->kind() == kKindIncomplete; }),
        types.end());

    // Remove modifier and array types with size = 0
    // TODO: This is mostly correct, see TODO above for details.
    types.erase(std::remove_if(types.begin(),
                               types.end(),
                               [](const TypeInfo* element) {
                                 return (IsModifierType(element) || IsArrayType(element)) &&
                                        element->size() == 0;
                               }),
                types.end());

    if (types.size() != 1) {
      error_unsuccessful_dedup(entry.first, types);
    }
  }

  Json::Value types_json(Json::objectValue);
  for (const auto& type : types_by_name) {
    auto json_with_name = type.second[0]->GetJson();
    types_json[json_with_name.name()] = json_with_name.value();
  }

  root["types"] = types_json;

  Json::StreamWriterBuilder factory;
  std::unique_ptr<Json::StreamWriter> const json_writer(factory.newStreamWriter());
  json_writer->write(root, &std::cout);

  return 0;
}
