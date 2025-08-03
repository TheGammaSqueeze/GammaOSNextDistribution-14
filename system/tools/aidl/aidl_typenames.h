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
#include <map>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>

using std::map;
using std::optional;
using std::pair;
using std::set;
using std::string;
using std::unique_ptr;
using std::vector;

class AidlDefinedType;
class AidlEnumDeclaration;
class AidlInterface;
class AidlLocation;
class AidlParcelable;
class AidlTypeSpecifier;
class AidlDocument;
struct ArgumentAspect;

namespace android {
namespace aidl {

// AidlTypenames is a collection of AIDL types available to a compilation unit.
//
// Basic types (such as int, String, etc.) are added by default, while defined
// types (such as IFoo, MyParcelable, etc.) and types from preprocessed inputs
// are added as they are recognized by the parser.
//
// When AidlTypeSpecifier is encountered during parsing, parser defers the
// resolution of it until the end of the parsing, where it uses AidlTypenames
// to resolve type names in AidlTypeSpecifier.
//
// Note that nothing here is specific to either Java or C++.
class AidlTypenames final {
 public:
  AidlTypenames() = default;
  bool AddDocument(std::unique_ptr<AidlDocument> doc);
  const std::vector<std::unique_ptr<AidlDocument>>& AllDocuments() const { return documents_; }
  const AidlDocument& MainDocument() const;
  static bool IsBuiltinTypename(const string& type_name);
  static bool IsPrimitiveTypename(const string& type_name);
  bool IsParcelable(const string& type_name) const;
  const AidlDefinedType* TryGetDefinedType(const string& type_name) const;
  std::vector<const AidlDefinedType*> AllDefinedTypes() const;

  struct ResolvedTypename {
    std::string canonical_name;
    bool is_resolved;
    const AidlDefinedType* defined_type;
  };
  ResolvedTypename ResolveTypename(const string& type_name) const;
  std::unique_ptr<AidlTypeSpecifier> MakeResolvedType(const AidlLocation& location,
                                                      const string& name, bool is_array) const;
  ArgumentAspect GetArgumentAspect(const AidlTypeSpecifier& type) const;
  bool CanBeJavaOnlyImmutable(const AidlTypeSpecifier& type) const;
  bool CanBeFixedSize(const AidlTypeSpecifier& type) const;
  static bool IsList(const AidlTypeSpecifier& type);

  bool IsIgnorableImport(const string& import) const;
  // Returns the AidlEnumDeclaration of the given type, or nullptr if the type
  // is not an AidlEnumDeclaration;
  const AidlEnumDeclaration* GetEnumDeclaration(const AidlTypeSpecifier& type) const;
  // Returns the AidlInterface of the given type, or nullptr if the type
  // is not an AidlInterface;
  const AidlInterface* GetInterface(const AidlTypeSpecifier& type) const;
  // Returns the AidlParcelable of the given type, or nullptr if the type
  // is not an AidlParcelable;
  const AidlParcelable* GetParcelable(const AidlTypeSpecifier& type) const;
  // Iterates over all defined types
  void IterateTypes(const std::function<void(const AidlDefinedType&)>& body) const;
  // Fixes AST after type/ref resolution before validation
  bool Autofill() const;

 private:
  map<string, AidlDefinedType*> defined_types_;
  std::vector<std::unique_ptr<AidlDocument>> documents_;
};

}  // namespace aidl
}  // namespace android
