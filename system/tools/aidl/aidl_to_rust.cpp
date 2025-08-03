/*
 * Copyright (C) 2020, The Android Open Source Project
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

#include "aidl_to_rust.h"
#include "aidl_language.h"
#include "aidl_typenames.h"
#include "logging.h"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include <functional>
#include <iostream>
#include <map>
#include <string>
#include <vector>

using android::base::Join;
using android::base::Split;
using android::base::StringPrintf;

namespace android {
namespace aidl {
namespace rust {

namespace {
std::string GetRawRustName(const AidlTypeSpecifier& type);

std::string ConstantValueDecoratorInternal(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value, bool by_ref) {
  if (type.IsArray()) {
    const auto& values = std::get<std::vector<std::string>>(raw_value);
    std::string value = "[" + Join(values, ", ") + "]";
    if (type.IsDynamicArray()) {
      value = "vec!" + value;
    }
    if (!type.IsMutated() && type.IsNullable()) {
      value = "Some(" + value + ")";
    }
    return value;
  }

  std::string value = std::get<std::string>(raw_value);

  const auto& aidl_name = type.GetName();
  if (aidl_name == "char") {
    return value + " as u16";
  }

  if (aidl_name == "float") {
    // value already ends in `f`, so just add `32`
    return value + "32";
  }

  if (aidl_name == "double") {
    return value + "f64";
  }

  if (auto defined_type = type.GetDefinedType(); defined_type) {
    auto enum_type = defined_type->AsEnumDeclaration();
    AIDL_FATAL_IF(!enum_type, type) << "Invalid type for \"" << value << "\"";
    return GetRawRustName(type) + "::" + value.substr(value.find_last_of('.') + 1);
  }

  if (aidl_name == "String" && !by_ref) {
    // The actual type might be String or &str,
    // and .into() transparently converts into either one
    value = value + ".into()";
  }

  if (type.IsNullable()) {
    value = "Some(" + value + ")";
  }

  return value;
}

std::string GetRawRustName(const AidlTypeSpecifier& type) {
  const auto defined_type = type.GetDefinedType();
  if (defined_type != nullptr) {
    const auto unstructured = AidlCast<AidlParcelable>(*defined_type);
    if (unstructured != nullptr) {
      // Unstructured parcelable should set its rust_type. Use it.
      const std::string rust_type = unstructured->GetRustType();
      AIDL_FATAL_IF(rust_type.empty(), unstructured)
          << "Parcelable " << unstructured->GetCanonicalName() << " has no rust_type defined.";
      return rust_type;
    }
  }

  // Each Rust type is defined in a file with the same name,
  // e.g., IFoo is in IFoo.rs
  auto split_name = type.GetSplitName();
  std::string rust_name{"crate::mangled::"};
  for (const auto& component : split_name) {
    rust_name += StringPrintf("_%zd_%s", component.size(), component.c_str());
  }
  return rust_name;
}

// Usually, this means that the type implements `Default`, however `ParcelableHolder` is also
// included in this list because the code generator knows how to call `::new(stability)`.
bool AutoConstructor(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  return !(type.GetName() == "ParcelFileDescriptor" || type.GetName() == "IBinder" ||
           TypeIsInterface(type, typenames));
}

std::string GetRustName(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                        StorageMode mode) {
  // map from AIDL built-in type name to the corresponding Rust type name
  static map<string, string> m = {
      {"void", "()"},
      {"boolean", "bool"},
      {"byte", "i8"},
      {"char", "u16"},
      {"int", "i32"},
      {"long", "i64"},
      {"float", "f32"},
      {"double", "f64"},
      {"String", "String"},
      {"IBinder", "binder::SpIBinder"},
      {"ParcelFileDescriptor", "binder::ParcelFileDescriptor"},
      {"ParcelableHolder", "binder::ParcelableHolder"},
  };
  const string& type_name = type.GetName();
  if (m.find(type_name) != m.end()) {
    AIDL_FATAL_IF(!AidlTypenames::IsBuiltinTypename(type_name), type);
    if (type_name == "String" && mode == StorageMode::UNSIZED_ARGUMENT) {
      return "str";
    } else {
      return m[type_name];
    }
  }
  auto name = GetRawRustName(type);
  if (TypeIsInterface(type, typenames)) {
    name = "binder::Strong<dyn " + name + ">";
  }
  if (type.IsGeneric()) {
    name += "<";
    for (const auto& param : type.GetTypeParameters()) {
      name += GetRustName(*param, typenames, mode);
      name += ",";
    }
    name += ">";
  }
  return name;
}
}  // namespace

std::string ConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value) {
  return ConstantValueDecoratorInternal(type, raw_value, false);
}

std::string ConstantValueDecoratorRef(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value) {
  return ConstantValueDecoratorInternal(type, raw_value, true);
}

// Returns default value for array.
std::string ArrayDefaultValue(const AidlTypeSpecifier& type) {
  AIDL_FATAL_IF(!type.IsFixedSizeArray(), type) << "not a fixed-size array";
  auto dimensions = type.GetFixedSizeArrayDimensions();
  std::string value = "Default::default()";
  for (auto it = rbegin(dimensions), end = rend(dimensions); it != end; it++) {
    value = "[" + Join(std::vector<std::string>(*it, value), ", ") + "]";
  }
  return value;
}

// Returns true if @nullable T[] should be mapped Option<Vec<Option<T>>
bool UsesOptionInNullableVector(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  AIDL_FATAL_IF(!type.IsArray() && !typenames.IsList(type), type) << "not a vector";
  AIDL_FATAL_IF(typenames.IsList(type) && type.GetTypeParameters().size() != 1, type)
      << "List should have a single type arg.";

  const auto& element_type = type.IsArray() ? type : *type.GetTypeParameters().at(0);
  if (typenames.IsPrimitiveTypename(element_type.GetName())) {
    return false;
  }
  if (typenames.GetEnumDeclaration(element_type)) {
    return false;
  }
  return true;
}

std::string RustLifetimeName(Lifetime lifetime) {
  switch (lifetime) {
    case Lifetime::NONE:
      return "";
    case Lifetime::A:
      return "'a ";
  }
}

std::string RustLifetimeGeneric(Lifetime lifetime) {
  switch (lifetime) {
    case Lifetime::NONE:
      return "";
    case Lifetime::A:
      return "<'a>";
  }
}

std::string RustNameOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                       StorageMode mode, Lifetime lifetime) {
  std::string rust_name;
  if (type.IsArray() || typenames.IsList(type)) {
    const auto& element_type = type.IsGeneric() ? (*type.GetTypeParameters().at(0)) : type;
    StorageMode element_mode;
    if (type.IsFixedSizeArray() && mode == StorageMode::PARCELABLE_FIELD) {
      // Elements of fixed-size array field need to have Default.
      element_mode = StorageMode::DEFAULT_VALUE;
    } else if (mode == StorageMode::OUT_ARGUMENT || mode == StorageMode::DEFAULT_VALUE) {
      // Elements need to have Default for resize_out_vec()
      element_mode = StorageMode::DEFAULT_VALUE;
    } else {
      element_mode = StorageMode::VALUE;
    }
    if (type.IsArray() && element_type.GetName() == "byte") {
      rust_name = "u8";
    } else {
      rust_name = GetRustName(element_type, typenames, element_mode);
    }

    // Needs `Option` wrapping because type is not default constructible
    const bool default_option =
        element_mode == StorageMode::DEFAULT_VALUE && !AutoConstructor(element_type, typenames);
    // Needs `Option` wrapping due to being a nullable, non-primitive, non-enum type in a vector.
    const bool nullable_option = type.IsNullable() && UsesOptionInNullableVector(type, typenames);
    if (default_option || nullable_option) {
      rust_name = "Option<" + rust_name + ">";
    }

    if (mode == StorageMode::UNSIZED_ARGUMENT) {
      rust_name = "[" + rust_name + "]";
    } else if (type.IsFixedSizeArray()) {
      auto dimensions = type.GetFixedSizeArrayDimensions();
      // T[N][M] => [[T; M]; N]
      for (auto it = rbegin(dimensions), end = rend(dimensions); it != end; it++) {
        rust_name = "[" + rust_name + "; " + std::to_string(*it) + "]";
      }
    } else {
      rust_name = "Vec<" + rust_name + ">";
    }
  } else {
    rust_name = GetRustName(type, typenames, mode);
  }

  if (mode == StorageMode::IN_ARGUMENT || mode == StorageMode::UNSIZED_ARGUMENT) {
    // If this is a nullable input argument, put the reference inside the option,
    // e.g., `Option<&str>` instead of `&Option<str>`
    rust_name = "&" + RustLifetimeName(lifetime) + rust_name;
  }

  if (type.IsNullable() ||
      // Some types don't implement Default, so we wrap them
      // in Option, which defaults to None
      (TypeNeedsOption(type, typenames) &&
       (mode == StorageMode::DEFAULT_VALUE || mode == StorageMode::OUT_ARGUMENT ||
        mode == StorageMode::PARCELABLE_FIELD))) {
    if (type.IsHeapNullable()) {
      rust_name = "Option<Box<" + rust_name + ">>";
    } else {
      rust_name = "Option<" + rust_name + ">";
    }
  }

  if (mode == StorageMode::OUT_ARGUMENT || mode == StorageMode::INOUT_ARGUMENT) {
    rust_name = "&" + RustLifetimeName(lifetime) + "mut " + rust_name;
  }

  return rust_name;
}

StorageMode ArgumentStorageMode(const AidlArgument& arg, const AidlTypenames& typenames) {
  if (arg.IsOut()) {
    return arg.IsIn() ? StorageMode::INOUT_ARGUMENT : StorageMode::OUT_ARGUMENT;
  }

  const auto typeName = arg.GetType().GetName();
  const auto definedType = typenames.TryGetDefinedType(typeName);

  const bool isEnum = definedType && definedType->AsEnumDeclaration() != nullptr;
  const bool isPrimitive = AidlTypenames::IsPrimitiveTypename(typeName);
  if (typeName == "String" || arg.GetType().IsDynamicArray() || typenames.IsList(arg.GetType())) {
    return StorageMode::UNSIZED_ARGUMENT;
  } else if (!(isPrimitive || isEnum) || arg.GetType().IsFixedSizeArray()) {
    return StorageMode::IN_ARGUMENT;
  } else {
    return StorageMode::VALUE;
  }
}

ReferenceMode ArgumentReferenceMode(const AidlArgument& arg, const AidlTypenames& typenames) {
  auto arg_mode = ArgumentStorageMode(arg, typenames);
  switch (arg_mode) {
    case StorageMode::IN_ARGUMENT:
      if (arg.GetType().IsNullable()) {
        // &Option<T> => Option<&T>
        return ReferenceMode::AS_REF;
      } else {
        return ReferenceMode::REF;
      }

    case StorageMode::OUT_ARGUMENT:
    case StorageMode::INOUT_ARGUMENT:
      return ReferenceMode::MUT_REF;

    case StorageMode::UNSIZED_ARGUMENT:
      if (arg.GetType().IsNullable()) {
        // &Option<String> => Option<&str>
        // &Option<Vec<T>> => Option<&[T]>
        return ReferenceMode::AS_DEREF;
      } else {
        return ReferenceMode::REF;
      }

    default:
      return ReferenceMode::VALUE;
  }
}

std::string TakeReference(ReferenceMode ref_mode, const std::string& name) {
  switch (ref_mode) {
    case ReferenceMode::REF:
      return "&" + name;

    case ReferenceMode::MUT_REF:
      return "&mut " + name;

    case ReferenceMode::AS_REF:
      return name + ".as_ref()";

    case ReferenceMode::AS_DEREF:
      return name + ".as_deref()";

    default:
      return name;
  }
}

bool TypeIsInterface(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  const auto definedType = typenames.TryGetDefinedType(type.GetName());
  return definedType != nullptr && definedType->AsInterface() != nullptr;
}

bool TypeNeedsOption(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  if (type.IsArray() || typenames.IsList(type)) {
    return false;
  }

  // Already an Option<T>
  if (type.IsNullable()) {
    return false;
  }

  const string& aidl_name = type.GetName();
  if (aidl_name == "IBinder") {
    return true;
  }
  if (aidl_name == "ParcelFileDescriptor") {
    return true;
  }
  if (aidl_name == "ParcelableHolder") {
    // ParcelableHolder never needs an Option because we always
    // call its new() constructor directly instead of default()
    return false;
  }

  // Strong<dyn IFoo> values don't implement Default
  if (TypeIsInterface(type, typenames)) {
    return true;
  }

  return false;
}

}  // namespace rust
}  // namespace aidl
}  // namespace android
