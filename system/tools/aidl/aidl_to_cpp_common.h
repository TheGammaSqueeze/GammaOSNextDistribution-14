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

#pragma once

#include <functional>
#include <string>
#include <type_traits>

#include "aidl_language.h"

// This is used to help generate code targetting C++ (the language) whether using the libbinder or
// libbinder_ndk backend.

namespace android {
namespace aidl {
namespace cpp {

extern char kTransactionLogStruct[];

// These roughly correspond to the various class names in the C++ hierarchy:
enum class ClassNames {
  BASE,             // Foo (not a real class, but useful in some circumstances).
  CLIENT,           // BpFoo
  SERVER,           // BnFoo
  INTERFACE,        // IFoo
  DEFAULT_IMPL,     // IFooDefault
  RAW,              // (as shown in the file)
  DELEGATOR_IMPL,   // IFooDelegator
  MAYBE_INTERFACE,  // == INTERFACE for AidlInterface, == RAW for other types
};

string ClassName(const AidlDefinedType& defined_type, ClassNames type);

// Return the alignment of known types and enum backing types.
// If the alignment is unknown, or it is a FizedSize parcelable with its
// own guaranteed alignment(so it does not need to be specified), 0 will be
// returned.
size_t AlignmentOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames);

// Generate the relative path to a header file.  If |use_os_sep| we'll use the
// operating system specific path separator rather than C++'s expected '/' when
// including headers.
std::string HeaderFile(const AidlDefinedType& defined_type, ClassNames class_type,
                       bool use_os_sep = true);

bool ValidateOutputFilePath(const string& output_file, const Options& options,
                            const AidlDefinedType& defined_type);

void EnterNamespace(CodeWriter& out, const AidlDefinedType& defined_type);
void LeaveNamespace(CodeWriter& out, const AidlDefinedType& defined_type);

string BuildVarName(const AidlArgument& a);
const string GenLogBeforeExecute(const string className, const AidlMethod& method, bool isServer,
                                 bool isNdk);
const string GenLogAfterExecute(const string className, const AidlInterface& interface,
                                const AidlMethod& method, const string& statusVarName,
                                const string& returnVarName, bool isServer, bool isNdk);

template <typename T, typename = std::enable_if_t<std::is_copy_constructible_v<T>>>
std::vector<T> Append(std::vector<T> as, const std::vector<T>& bs) {
  as.insert(as.end(), bs.begin(), bs.end());
  return as;
}

template <typename T>
std::vector<T> Append(std::vector<T>&& as, std::vector<T>&& bs) {
  std::vector<T> appended = std::move(as);
  std::copy(std::move_iterator(bs.begin()), std::move_iterator(bs.end()),
            std::back_inserter(appended));
  return appended;
}

// Returns Parent1::Parent2::Self. Namespaces are not included.
std::string GetQualifiedName(const AidlDefinedType& type, ClassNames name = ClassNames::RAW);

void GenerateEnumClassDecl(CodeWriter& out, const AidlEnumDeclaration& enum_decl,
                           const std::string& backing_type, ::ConstantValueDecorator decorator);
std::string GenerateEnumToString(const AidlEnumDeclaration& enum_decl,
                                 const std::string& backing_type);
std::string GenerateEnumValues(const AidlEnumDeclaration& enum_decl,
                               const std::vector<std::string>& enclosing_namespaces_of_enum_decl);
std::string TemplateDecl(const AidlParcelable& defined_type);

void GenerateParcelableComparisonOperators(CodeWriter& out, const AidlParcelable& parcelable);

void GenerateToString(CodeWriter& out, const AidlStructuredParcelable& parcelable);
void GenerateToString(CodeWriter& out, const AidlUnionDecl& parcelable);

std::string GetDeprecatedAttribute(const AidlCommentable& type);

template <typename Stream>
void GenerateDeprecated(Stream& out, const AidlCommentable& type) {
  if (auto deprecated = GetDeprecatedAttribute(type); !deprecated.empty()) {
    out << " " + deprecated;
  }
}

struct ParcelWriterContext {
  string status_type;
  string status_ok;
  string status_bad;
  std::function<void(CodeWriter& out, const std::string& var, const AidlTypeSpecifier& type)>
      read_func;
  std::function<void(CodeWriter& out, const std::string& value, const AidlTypeSpecifier& type)>
      write_func;
};

struct UnionWriter {
  const AidlUnionDecl& decl;
  const AidlTypenames& typenames;
  const std::function<std::string(const AidlTypeSpecifier&, const AidlTypenames&)> name_of;
  const ::ConstantValueDecorator& decorator;

  static std::set<std::string> GetHeaders(const AidlUnionDecl&);

  void PrivateFields(CodeWriter& out) const;
  void PublicFields(CodeWriter& out) const;
  void ReadFromParcel(CodeWriter& out, const ParcelWriterContext&) const;
  void WriteToParcel(CodeWriter& out, const ParcelWriterContext&) const;
};

std::string CppConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value, bool is_ndk);

void GenerateForwardDecls(CodeWriter& out, const AidlDefinedType& root_type, bool is_ndk);

struct ClangDiagnosticIgnoreDeprecated {
  CodeWriter& out;
  bool deprecated;
  ClangDiagnosticIgnoreDeprecated(CodeWriter& out, bool deprecated)
      : out(out), deprecated(deprecated) {
    // enter
    if (deprecated) {
      out << "#pragma clang diagnostic push\n";
      out << "#pragma clang diagnostic ignored \"-Wdeprecated-declarations\"\n";
    }
  }
  ~ClangDiagnosticIgnoreDeprecated() {
    // exit
    if (deprecated) {
      out << "#pragma clang diagnostic pop\n";
    }
  }
};

bool HasDeprecatedField(const AidlParcelable& parcelable);
}  // namespace cpp
}  // namespace aidl
}  // namespace android
