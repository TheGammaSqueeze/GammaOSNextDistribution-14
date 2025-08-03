/*
 * Copyright (C) 2021, The Android Open Source Project
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

#include "check_valid.h"
#include "aidl.h"

#include <vector>

namespace android {
namespace aidl {

using TypePredicate = std::function<bool(const AidlTypeSpecifier&)>;
using DefinedTypePredicate = std::function<bool(const AidlDefinedType&)>;

namespace {
bool IsListOf(const AidlTypeSpecifier& type, TypePredicate pred) {
  return type.GetName() == "List" && type.IsGeneric() && type.GetTypeParameters().size() == 1 &&
         pred(*type.GetTypeParameters().at(0));
}
bool IsArrayOf(const AidlTypeSpecifier& type, TypePredicate pred) {
  return type.IsArray() && pred(type);
}
bool IsInterface(const AidlTypeSpecifier& type) {
  return type.GetDefinedType() && type.GetDefinedType()->AsInterface();
}
}  // namespace

struct CheckTypeVisitor : AidlVisitor {
  bool success = true;
  std::vector<TypePredicate> checkers;
  std::vector<DefinedTypePredicate> defined_checkers;

  void Visit(const AidlTypeSpecifier& type) override {
    for (auto& checker : checkers) {
      if (!checker(type)) {
        success = false;
      }
    }
  }
  void Visit(const AidlInterface& t) override { CheckDefinedType(t); }
  void Visit(const AidlEnumDeclaration& t) override { CheckDefinedType(t); }
  void Visit(const AidlStructuredParcelable& t) override { CheckDefinedType(t); }
  void Visit(const AidlUnionDecl& t) override { CheckDefinedType(t); }
  void Visit(const AidlParcelable& t) override { CheckDefinedType(t); }

  void Check(TypePredicate checker) { checkers.push_back(std::move(checker)); }
  void Check(DefinedTypePredicate checker) { defined_checkers.push_back(std::move(checker)); }

 private:
  void CheckDefinedType(const AidlDefinedType& type) {
    for (auto& checker : defined_checkers) {
      if (!checker(type)) {
        success = false;
      }
    }
  }
};

bool CheckValid(const AidlDocument& doc, const Options& options) {
  const auto lang = options.TargetLanguage();
  const auto min_sdk_version = options.GetMinSdkVersion();

  CheckTypeVisitor v;

  v.Check([&](const AidlTypeSpecifier& type) {
    const auto valid_version = MinSdkVersionFromString("Tiramisu").value();
    if ((IsListOf(type, IsInterface) || IsArrayOf(type, IsInterface)) &&
        lang == Options::Language::JAVA && min_sdk_version < valid_version) {
      const auto kind = IsListOf(type, IsInterface) ? "List" : "Array";
      AIDL_ERROR(type) << kind << " of interfaces is available since SDK = " << valid_version
                       << " in Java. Current min_sdk_version is " << min_sdk_version << ".";
      return false;
    }
    return true;
  });

  v.Check([&](const AidlTypeSpecifier& type) {
    const auto valid_version = MinSdkVersionFromString("S").value();
    if (type.GetName() == "ParcelableHolder" && min_sdk_version < valid_version) {
      AIDL_ERROR(type) << " ParcelableHolder is available since SDK = " << valid_version
                       << ". Current min_sdk_version is " << min_sdk_version << ".";
      return false;
    }
    return true;
  });

  // Check all nested types for potential #include cycles that would contain
  // them. The algorithm performs a depth-first search on a graph with the
  // following properties:
  //
  // * Graph nodes are top-level (non-nested) types, under the assumption that
  //   there is a 1:1 mapping between top-level types and included headers. This
  //   implies that a cycle between these types will be equivalent to a cycle
  //   between headers.
  //
  // * Each edge U -> V represents a "declare V before U" relationship between
  //   types. This means that V.h needs to be included by U.h, or the V type
  //   needs to be forward-declared before U. For any type U, its neighbors
  //   are all nodes V such that U or its nested types have a reference to V
  //   or any type nested in it.
  //
  // * The algorithm tries to find a cycle containing start_type. Such a
  //   cycle exists if the following hold true:
  //   * There exists a path from start_type to another top-level type T
  //     (different from start_type)
  //   * There is a back edge from T to start_type which closes the cycle
  v.Check([&](const AidlDefinedType& start_type) {
    if (start_type.GetParentType() == nullptr) {
      return true;
    }

    std::set<const AidlDefinedType*> visited;
    std::function<bool(const AidlDefinedType*)> dfs = [&](const AidlDefinedType* type) {
      if (!visited.insert(type).second) {
        // Already visited
        return false;
      }

      for (const auto& t : Collect<AidlTypeSpecifier>(*type)) {
        auto defined_type = t->GetDefinedType();
        if (!defined_type) {
          // Skip primitive/builtin types
          continue;
        }

        auto top_type = defined_type->GetRootType();
        if (top_type == type) {
          // Skip type references within the same top-level type
          continue;
        }

        if (defined_type == &start_type) {
          // Found a cycle back to the starting nested type
          return true;
        }

        if (dfs(top_type)) {
          // Found a cycle while visiting the top type for the next node
          return true;
        }
      }

      return false;
    };

    bool has_cycle = dfs(start_type.GetRootType());
    if (has_cycle) {
      AIDL_ERROR(start_type) << "has cyclic references to nested types.";
      return false;
    }

    return true;
  });

  VisitTopDown(v, doc);
  return v.success;
}

}  // namespace aidl
}  // namespace android
