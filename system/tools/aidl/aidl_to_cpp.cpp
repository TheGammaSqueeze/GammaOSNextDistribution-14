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

#include "aidl_to_cpp.h"
#include "aidl_to_cpp_common.h"
#include "aidl_language.h"
#include "logging.h"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include <functional>
#include <unordered_map>

using android::base::Join;
using android::base::Split;
using android::base::StringPrintf;
using std::ostringstream;

namespace android {
namespace aidl {
namespace cpp {

namespace {

std::string RawParcelMethod(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                            bool readMethod) {
  static map<string, string> kBuiltin = {
      {"byte", "Byte"},
      {"boolean", "Bool"},
      {"char", "Char"},
      {"double", "Double"},
      {"FileDescriptor", "UniqueFileDescriptor"},
      {"float", "Float"},
      {"IBinder", "StrongBinder"},
      {"int", "Int32"},
      {"long", "Int64"},
      {"ParcelFileDescriptor", "Parcelable"},
      {"String", "String16"},
      {"ParcelableHolder", "Parcelable"},
  };

  static map<string, string> kBuiltinVector = {
      {"FileDescriptor", "UniqueFileDescriptorVector"},
      {"double", "DoubleVector"},
      {"char", "CharVector"},
      {"boolean", "BoolVector"},
      {"byte", "ByteVector"},
      {"float", "FloatVector"},
      {"IBinder", "StrongBinderVector"},
      {"String", "String16Vector"},
      {"int", "Int32Vector"},
      {"long", "Int64Vector"},
      {"ParcelFileDescriptor", "ParcelableVector"},
  };

  const bool nullable = type.IsNullable();
  const bool isVector = type.IsArray() || typenames.IsList(type);
  const bool utf8 = type.IsUtf8InCpp();

  if (type.IsFixedSizeArray()) {
    return "FixedArray";
  }

  if (auto enum_decl = typenames.GetEnumDeclaration(type); enum_decl != nullptr) {
    if (isVector) {
      return "EnumVector";
    } else {
      return RawParcelMethod(enum_decl->GetBackingType(), typenames, readMethod);
    }
  }

  if (isVector) {
    string element_name;
    if (typenames.IsList(type)) {
      AIDL_FATAL_IF(type.GetTypeParameters().size() != 1, type);
      element_name = type.GetTypeParameters().at(0)->GetName();
    } else {
      element_name = type.GetName();
    }
    if (kBuiltinVector.find(element_name) != kBuiltinVector.end()) {
      AIDL_FATAL_IF(!AidlTypenames::IsBuiltinTypename(element_name), type);
      if (utf8) {
        AIDL_FATAL_IF(element_name != "String", type);
        return readMethod ? "Utf8VectorFromUtf16Vector" : "Utf8VectorAsUtf16Vector";
      }
      return kBuiltinVector[element_name];
    }
    auto definedType = typenames.TryGetDefinedType(element_name);
    if (definedType != nullptr && definedType->AsInterface() != nullptr) {
      return "StrongBinderVector";
    }
    return "ParcelableVector";
  }

  const string& type_name = type.GetName();
  if (kBuiltin.find(type_name) != kBuiltin.end()) {
    AIDL_FATAL_IF(!AidlTypenames::IsBuiltinTypename(type_name), type);
    if (type_name == "IBinder" && nullable && readMethod) {
      return "NullableStrongBinder";
    }
    if (type_name == "ParcelFileDescriptor" && nullable && !readMethod) {
      return "NullableParcelable";
    }
    if (utf8) {
      AIDL_FATAL_IF(type_name != "String", type);
      return readMethod ? "Utf8FromUtf16" : "Utf8AsUtf16";
    }
    return kBuiltin[type_name];
  }

  AIDL_FATAL_IF(AidlTypenames::IsBuiltinTypename(type.GetName()), type);
  auto definedType = typenames.TryGetDefinedType(type.GetName());
  // The type must be either primitive or interface or parcelable,
  // so it cannot be nullptr.
  AIDL_FATAL_IF(definedType == nullptr, type) << type.GetName() << " is not found.";

  if (definedType->AsInterface() != nullptr) {
    if (nullable && readMethod) {
      return "NullableStrongBinder";
    }
    return "StrongBinder";
  }

  // Parcelable
  if (nullable && !readMethod) {
    return "NullableParcelable";
  }
  return "Parcelable";
}

std::string GetRawCppName(const AidlTypeSpecifier& type) {
  return "::" + Join(type.GetSplitName(), "::");
}

std::string WrapIfNullable(const std::string type_str, const AidlTypeSpecifier& raw_type,
                           const AidlTypenames& typenames) {
  const auto& type = typenames.IsList(raw_type) ? (*raw_type.GetTypeParameters().at(0)) : raw_type;

  if (raw_type.IsNullable() && !AidlTypenames::IsPrimitiveTypename(type.GetName()) &&
      type.GetName() != "IBinder" && typenames.GetEnumDeclaration(type) == nullptr) {
    if (raw_type.IsHeapNullable()) {
      return "::std::unique_ptr<" + type_str + ">";
    }
    return "::std::optional<" + type_str + ">";
  }
  return type_str;
}

std::string GetCppName(const AidlTypeSpecifier& raw_type, const AidlTypenames& typenames) {
  // map from AIDL built-in type name to the corresponding Cpp type name
  static map<string, string> m = {
      {"boolean", "bool"},
      {"byte", "int8_t"},
      {"char", "char16_t"},
      {"double", "double"},
      {"FileDescriptor", "::android::base::unique_fd"},
      {"float", "float"},
      {"IBinder", "::android::sp<::android::IBinder>"},
      {"int", "int32_t"},
      {"long", "int64_t"},
      {"ParcelFileDescriptor", "::android::os::ParcelFileDescriptor"},
      {"String", "::android::String16"},
      {"void", "void"},
      {"ParcelableHolder", "::android::os::ParcelableHolder"},
  };
  AIDL_FATAL_IF(typenames.IsList(raw_type) && raw_type.GetTypeParameters().size() != 1, raw_type);
  const auto& type = typenames.IsList(raw_type) ? (*raw_type.GetTypeParameters().at(0)) : raw_type;
  const string& aidl_name = type.GetName();
  if (m.find(aidl_name) != m.end()) {
    AIDL_FATAL_IF(!AidlTypenames::IsBuiltinTypename(aidl_name), raw_type);
    if (aidl_name == "byte" && type.IsArray()) {
      return "uint8_t";
    } else if (raw_type.IsUtf8InCpp()) {
      AIDL_FATAL_IF(aidl_name != "String", type);
      return WrapIfNullable("::std::string", raw_type, typenames);
    }
    return WrapIfNullable(m[aidl_name], raw_type, typenames);
  }
  auto definedType = typenames.TryGetDefinedType(type.GetName());
  if (definedType != nullptr && definedType->AsInterface() != nullptr) {
    return "::android::sp<" + GetRawCppName(type) + ">";
  }
  auto cpp_name = GetRawCppName(type);
  if (type.IsGeneric()) {
    std::vector<std::string> type_params;
    for (const auto& parameter : type.GetTypeParameters()) {
      type_params.push_back(CppNameOf(*parameter, typenames));
    }
    cpp_name += "<" + base::Join(type_params, ", ") + ">";
  }
  return WrapIfNullable(cpp_name, raw_type, typenames);
}
}  // namespace
std::string ConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value) {
  return CppConstantValueDecorator(type, raw_value, /*is_ndk=*/false);
};

std::string GetTransactionIdFor(const std::string& clazz, const AidlMethod& method) {
  return clazz + "::TRANSACTION_" + method.GetName();
}

std::string CppNameOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  // get base type's cpp_name with nullable processed.
  std::string cpp_name = GetCppName(type, typenames);

  if (type.IsArray() || typenames.IsList(type)) {
    if (type.IsFixedSizeArray()) {
      auto dimensions = type.GetFixedSizeArrayDimensions();
      for (auto it = rbegin(dimensions), end = rend(dimensions); it != end; it++) {
        cpp_name = "std::array<" + cpp_name + ", " + std::to_string(*it) + ">";
      }
    } else {
      cpp_name = "::std::vector<" + cpp_name + ">";
    }

    // wrap nullable again because @nullable applies to BOTH array type(outermost type) AND base
    // type(innermost type)
    if (type.IsHeapNullable()) {
      cpp_name = "::std::unique_ptr<" + cpp_name + ">";
    } else if (type.IsNullable()) {
      cpp_name = "::std::optional<" + cpp_name + ">";
    }
  }
  return cpp_name;
}

bool IsNonCopyableType(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  if (type.IsArray() || typenames.IsList(type)) {
    return false;
  }

  const std::string cpp_name = GetCppName(type, typenames);
  if (cpp_name == "::android::base::unique_fd") {
    return true;
  }
  return false;
}

std::string ParcelReadMethodOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  return "read" + RawParcelMethod(type, typenames, true /* readMethod */);
}

std::string ParcelReadCastOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                             const std::string& variable_name) {
  if (auto enum_decl = typenames.GetEnumDeclaration(type);
      enum_decl != nullptr && !type.IsArray()) {
    return StringPrintf("reinterpret_cast<%s *>(%s)",
                        CppNameOf(enum_decl->GetBackingType(), typenames).c_str(),
                        variable_name.c_str());
  }

  return variable_name;
}

std::string ParcelWriteMethodOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  return "write" + RawParcelMethod(type, typenames, false /* readMethod */);
}

std::string ParcelWriteCastOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                              const std::string& variable_name) {
  if (auto enum_decl = typenames.GetEnumDeclaration(type);
      enum_decl != nullptr && !type.IsArray()) {
    return StringPrintf("static_cast<%s>(%s)",
                        CppNameOf(enum_decl->GetBackingType(), typenames).c_str(),
                        variable_name.c_str());
  }
  return variable_name;
}

// Add includes for a type ref. Note that this is non-recursive.
void AddHeaders(const AidlTypeSpecifier& type, const AidlTypenames& typenames,
                std::set<std::string>* headers) {
  if (type.IsArray()) {
    if (type.IsFixedSizeArray()) {
      headers->insert("array");
    } else {
      headers->insert("vector");
    }
  }
  if (type.IsNullable()) {
    if (type.GetName() != "IBinder") {
      headers->insert("optional");
    }
  }
  if (typenames.IsList(type)) {
    headers->insert("vector");
    return;
  }
  if (type.GetName() == "String") {
    if (type.IsUtf8InCpp()) {
      headers->insert("string");
    } else {
      headers->insert("utils/String16.h");
    }
    return;
  }
  if (type.GetName() == "IBinder") {
    headers->insert("binder/IBinder.h");
    return;
  }
  if (type.GetName() == "FileDescriptor") {
    headers->insert("android-base/unique_fd.h");
    return;
  }
  if (type.GetName() == "ParcelFileDescriptor") {
    headers->insert("binder/ParcelFileDescriptor.h");
    return;
  }
  if (type.GetName() == "ParcelableHolder") {
    headers->insert("binder/ParcelableHolder.h");
    return;
  }

  static const std::set<string> need_cstdint{"byte", "int", "long"};
  if (need_cstdint.find(type.GetName()) != need_cstdint.end()) {
    headers->insert("cstdint");
    return;
  }

  if (AidlTypenames::IsPrimitiveTypename(type.GetName())) {
    return;
  }

  auto defined_type = typenames.TryGetDefinedType(type.GetName());
  AIDL_FATAL_IF(defined_type == nullptr, type) << "Unexpected type: " << type.GetName();

  headers->insert(CppHeaderForType(*defined_type));
}

std::string CppHeaderForType(const AidlDefinedType& defined_type) {
  // Unstructured parcelable should set its cpp_header. use it.
  if (auto unstructured = AidlCast<AidlParcelable>(defined_type); unstructured) {
    const std::string cpp_header = unstructured->GetCppHeader();
    AIDL_FATAL_IF(cpp_header.empty(), unstructured)
        << "Parcelable " << unstructured->GetCanonicalName() << " has no cpp_header defined.";
    return cpp_header;
  }
  return HeaderFile(defined_type, ClassNames::RAW, /*use_os_sep=*/false);
}

}  // namespace cpp
}  // namespace aidl
}  // namespace android
