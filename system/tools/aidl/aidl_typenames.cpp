/*
 * Copyright (C) 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "aidl_typenames.h"
#include "aidl_language.h"
#include "logging.h"

#include <android-base/file.h>
#include <android-base/strings.h>

#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

using android::base::Split;

using std::make_pair;
using std::map;
using std::pair;
using std::set;
using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace aidl {

// The built-in AIDL types..
static const set<string> kBuiltinTypes = {"void",
                                          "boolean",
                                          "byte",
                                          "char",
                                          "int",
                                          "long",
                                          "float",
                                          "double",
                                          "String",
                                          "List",
                                          "Map",
                                          "IBinder",
                                          "FileDescriptor",
                                          "CharSequence",
                                          "ParcelFileDescriptor",
                                          "ParcelableHolder"};

static const set<string> kPrimitiveTypes = {"void", "boolean", "byte",  "char",
                                            "int",  "long",    "float", "double"};

// Note: these types may look wrong because they look like Java
// types, but they have long been supported from the time when Java
// was the only target language of this compiler. They are added here for
// backwards compatibility, but we internally treat them as List and Map,
// respectively.
static const map<string, string> kJavaLikeTypeToAidlType = {
    {"java.util.List", "List"},
    {"java.util.Map", "Map"},
    {"android.os.ParcelFileDescriptor", "ParcelFileDescriptor"},
};

// Package name and type name can't be one of these as they are keywords
// in Java and C++. Using these names will eventually cause compilation error,
// so checking this here is not a must have, but early detection of errors
// is always better.
static const set<string> kCppOrJavaReservedWord = {
    "break",  "case",   "catch", "char",     "class",  "continue", "default",
    "do",     "double", "else",  "enum",     "false",  "float",    "for",
    "goto",   "if",     "int",   "long",     "new",    "private",  "protected",
    "public", "return", "short", "static",   "switch", "this",     "throw",
    "true",   "try",    "void",  "volatile", "while"};

static bool HasValidNameComponents(const AidlDefinedType& defined) {
  bool success = true;
  vector<string> pieces = Split(defined.GetCanonicalName(), ".");
  for (const string& piece : pieces) {
    if (kCppOrJavaReservedWord.find(piece) != kCppOrJavaReservedWord.end()) {
      AIDL_ERROR(defined) << defined.GetCanonicalName() << " is an invalid name because '" << piece
                          << "' is a Java or C++ identifier.";
      success = false;
    }
    // not checking kJavaLikeTypeToAidl, since that wouldn't make sense here
    if (kBuiltinTypes.find(piece) != kBuiltinTypes.end()) {
      AIDL_ERROR(defined) << defined.GetCanonicalName() << " is an invalid name because '" << piece
                          << "' is a built-in AIDL type.";
      success = false;
    }
  }
  return success;
}

bool AidlTypenames::IsIgnorableImport(const string& import) const {
  if (IsBuiltinTypename(import)) return true;

  static set<string> ignore_import = {
      "android.os.IInterface",   "android.os.IBinder", "android.os.Parcelable", "android.os.Parcel",
      "android.content.Context", "java.lang.String",   "java.lang.CharSequence"};
  // these known built-in types don't need to be imported
  if (ignore_import.find(import) != ignore_import.end()) return true;

  if (TryGetDefinedType(import)) return true;

  return false;
}

// Add a parsed document and populate type names in it.
// Name conflict is an error unless one of them is from preprocessed.
// For legacy, we populate unqualified names from preprocessed unstructured parcelable types
// so that they can be referenced via a simple name.
bool AidlTypenames::AddDocument(std::unique_ptr<AidlDocument> doc) {
  bool is_preprocessed = doc->IsPreprocessed();
  std::vector<AidlDefinedType*> types_to_add;
  // Add types in two steps to avoid adding a type while the doc is rejected.
  // 1. filter types to add
  // 2. add types

  std::function<bool(const std::vector<std::unique_ptr<AidlDefinedType>>&)> collect_types_to_add;
  collect_types_to_add = [&](auto& types) {
    for (const auto& type : types) {
      if (IsBuiltinTypename(type->GetName())) {
        // ParcelFileDescriptor is treated as a built-in type, but it's also in the framework.aidl.
        // So aidl should ignore built-in types in framework.aidl to prevent duplication.
        // (b/130899491)
        if (is_preprocessed) {
          continue;
        }
        // HasValidNameComponents handles name conflicts with built-in types
      }

      if (auto prev_definition = defined_types_.find(type->GetCanonicalName());
          prev_definition != defined_types_.end()) {
        // Skip duplicate type in preprocessed document
        if (is_preprocessed) {
          continue;
        }
        // Overwrite duplicate type which is already added via preprocessed with a new one
        if (!prev_definition->second->GetDocument().IsPreprocessed()) {
          AIDL_ERROR(type) << "redefinition: " << type->GetCanonicalName() << " is defined "
                           << prev_definition->second->GetLocation();
          return false;
        }
      }

      if (!HasValidNameComponents(*type)) {
        return false;
      }

      types_to_add.push_back(type.get());

      // recursively check nested types
      if (!collect_types_to_add(type->GetNestedTypes())) {
        return false;
      }
    }
    return true;
  };

  if (!collect_types_to_add(doc->DefinedTypes())) {
    return false;
  }

  for (const auto& type : types_to_add) {
    // populate global 'type' namespace with fully-qualified names
    defined_types_.emplace(type->GetCanonicalName(), type);
    // preprocessed unstructured parcelable types can be referenced without qualification
    if (is_preprocessed && type->AsUnstructuredParcelable()) {
      defined_types_.emplace(type->GetName(), type);
    }
  }

  // transfer ownership of document
  documents_.push_back(std::move(doc));
  return true;
}

const AidlDocument& AidlTypenames::MainDocument() const {
  AIDL_FATAL_IF(documents_.size() == 0, AIDL_LOCATION_HERE) << "Main document doesn't exist";
  return *(documents_[0]);
}

bool AidlTypenames::IsBuiltinTypename(const string& type_name) {
  return kBuiltinTypes.find(type_name) != kBuiltinTypes.end() ||
      kJavaLikeTypeToAidlType.find(type_name) != kJavaLikeTypeToAidlType.end();
}

bool AidlTypenames::IsPrimitiveTypename(const string& type_name) {
  return kPrimitiveTypes.find(type_name) != kPrimitiveTypes.end();
}

bool AidlTypenames::IsParcelable(const string& type_name) const {
  if (IsBuiltinTypename(type_name)) {
    return type_name == "ParcelableHolder" || type_name == "ParcelFileDescriptor";
  }
  if (auto defined_type = TryGetDefinedType(type_name); defined_type) {
    return defined_type->AsParcelable() != nullptr;
  }
  return false;
}

const AidlDefinedType* AidlTypenames::TryGetDefinedType(const string& type_name) const {
  auto found_def = defined_types_.find(type_name);
  if (found_def != defined_types_.end()) {
    return found_def->second;
  }
  return nullptr;
}

std::vector<const AidlDefinedType*> AidlTypenames::AllDefinedTypes() const {
  std::vector<const AidlDefinedType*> res;
  for (const auto& doc : AllDocuments()) {
    VisitTopDown(
        [&](const AidlNode& node) {
          if (auto defined_type = AidlCast<AidlDefinedType>(node); defined_type) {
            res.push_back(defined_type);
          }
        },
        *doc);
  }
  return res;
}

AidlTypenames::ResolvedTypename AidlTypenames::ResolveTypename(const string& type_name) const {
  if (IsBuiltinTypename(type_name)) {
    auto found = kJavaLikeTypeToAidlType.find(type_name);
    if (found != kJavaLikeTypeToAidlType.end()) {
      return {found->second, true, nullptr};
    }
    return {type_name, true, nullptr};
  }
  const AidlDefinedType* defined_type = TryGetDefinedType(type_name);
  if (defined_type != nullptr) {
    return {defined_type->GetCanonicalName(), true, defined_type};
  } else {
    return {type_name, false, nullptr};
  }
}

std::unique_ptr<AidlTypeSpecifier> AidlTypenames::MakeResolvedType(const AidlLocation& location,
                                                                   const string& name,
                                                                   bool is_array) const {
  std::optional<ArrayType> array;
  if (is_array) {
    array = DynamicArray{};
  }
  std::unique_ptr<AidlTypeSpecifier> type(
      new AidlTypeSpecifier(location, name, std::move(array), nullptr, {}));
  AIDL_FATAL_IF(!type->Resolve(*this, nullptr), type) << "Can't make unknown type: " << name;
  type->MarkVisited();
  return type;
}

// Only immutable Parcelable, primitive type, and String, and List, Map, array of the types can be
// immutable.
bool AidlTypenames::CanBeJavaOnlyImmutable(const AidlTypeSpecifier& type) const {
  const string& name = type.GetName();
  if (type.IsGeneric()) {
    if (type.GetName() == "List" || type.GetName() == "Map") {
      const auto& types = type.GetTypeParameters();
      return std::all_of(types.begin(), types.end(),
                         [this](const auto& t) { return CanBeJavaOnlyImmutable(*t); });
    }
    AIDL_ERROR(type) << "For a generic type, an immutable parcelable can contain only List or Map.";
    return false;
  }
  if (IsPrimitiveTypename(name) || name == "String") {
    return true;
  }
  const AidlDefinedType* t = TryGetDefinedType(type.GetName());
  if (t == nullptr) {
    AIDL_ERROR(type) << "An immutable parcelable can contain only immutable Parcelable, primitive "
                        "type, and String.";
    return false;
  }
  if (t->AsEnumDeclaration()) {
    return true;
  }
  return t->IsJavaOnlyImmutable();
}

// Followings can be FixedSize:
// - @FixedSize parcelables
// - primitive types and enum types
// - fixed-size arrays of FixedSize types
bool AidlTypenames::CanBeFixedSize(const AidlTypeSpecifier& type) const {
  const string& name = type.GetName();
  if (type.IsGeneric() || type.IsNullable()) {
    return false;
  }
  if (type.IsArray() && !type.IsFixedSizeArray()) {
    return false;
  }
  if (IsPrimitiveTypename(name)) {
    return true;
  }
  if (IsBuiltinTypename(name)) {
    return false;
  }
  const AidlDefinedType* t = TryGetDefinedType(type.GetName());
  AIDL_FATAL_IF(t == nullptr, type)
      << "Failed to look up type. Cannot determine if it can be fixed size: " << type.GetName();

  if (t->AsEnumDeclaration()) {
    return true;
  }
  return t->IsFixedSize();
}

bool AidlTypenames::IsList(const AidlTypeSpecifier& type) {
  return type.GetName() == "List";
}

ArgumentAspect AidlTypenames::GetArgumentAspect(const AidlTypeSpecifier& type) const {
  if (type.IsArray()) {
    return {"array",
            {AidlArgument::Direction::IN_DIR, AidlArgument::Direction::OUT_DIR,
             AidlArgument::Direction::INOUT_DIR}};
  }
  const string& name = type.GetName();
  if (IsBuiltinTypename(name)) {
    if (name == "List" || name == "Map") {
      return {name,
              {AidlArgument::Direction::IN_DIR, AidlArgument::Direction::OUT_DIR,
               AidlArgument::Direction::INOUT_DIR}};
    } else if (name == "ParcelFileDescriptor") {
      // "out ParcelFileDescriptor" is not allowed because ParcelFileDescriptor is not
      // default-constructible.
      return {name, {AidlArgument::Direction::IN_DIR, AidlArgument::Direction::INOUT_DIR}};
    } else if (name == "ParcelableHolder") {
      // TODO(b/156872582): Support it when ParcelableHolder supports every backend.
      return {name, {}};
    } else {
      return {name, {AidlArgument::Direction::IN_DIR}};
    }
  }

  const AidlDefinedType* t = TryGetDefinedType(name);
  AIDL_FATAL_IF(t == nullptr, type) << "Unrecognized type: '" << name << "'";

  // An 'out' field is passed as an argument, so it doesn't make sense if it is immutable.
  if (t->AsParcelable() != nullptr) {
    if (t->IsJavaOnlyImmutable()) {
      return {"@JavaOnlyImmutable", {AidlArgument::Direction::IN_DIR}};
    }
    return {"parcelable/union",
            {AidlArgument::Direction::IN_DIR, AidlArgument::Direction::OUT_DIR,
             AidlArgument::Direction::INOUT_DIR}};
  }

  return {t->GetPreprocessDeclarationName(), {AidlArgument::Direction::IN_DIR}};
}

const AidlEnumDeclaration* AidlTypenames::GetEnumDeclaration(const AidlTypeSpecifier& type) const {
  if (auto defined_type = TryGetDefinedType(type.GetName()); defined_type != nullptr) {
    if (auto enum_decl = defined_type->AsEnumDeclaration(); enum_decl != nullptr) {
      return enum_decl;
    }
  }
  return nullptr;
}

const AidlInterface* AidlTypenames::GetInterface(const AidlTypeSpecifier& type) const {
  if (auto defined_type = TryGetDefinedType(type.GetName()); defined_type != nullptr) {
    if (auto intf = defined_type->AsInterface(); intf != nullptr) {
      return intf;
    }
  }
  return nullptr;
}

const AidlParcelable* AidlTypenames::GetParcelable(const AidlTypeSpecifier& type) const {
  if (auto defined_type = TryGetDefinedType(type.GetName()); defined_type != nullptr) {
    if (auto parcelable = defined_type->AsParcelable(); parcelable != nullptr) {
      return parcelable;
    }
  }
  return nullptr;
}

void AidlTypenames::IterateTypes(const std::function<void(const AidlDefinedType&)>& body) const {
  for (const auto& kv : defined_types_) {
    body(*kv.second);
  }
}

bool AidlTypenames::Autofill() const {
  bool success = true;
  IterateTypes([&](const AidlDefinedType& type) {
    // BackingType is filled in for all known enums, including imported enums,
    // because other types that may use enums, such as Interface or
    // StructuredParcelable, need to know the enum BackingType when
    // generating code.
    if (auto enum_decl = const_cast<AidlDefinedType&>(type).AsEnumDeclaration(); enum_decl) {
      if (!enum_decl->Autofill(*this)) {
        success = false;
      }
    }
  });
  return success;
}

}  // namespace aidl
}  // namespace android
