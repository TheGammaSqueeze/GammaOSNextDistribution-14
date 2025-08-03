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

#pragma once

#include <memory>
#include <string>
#include <variant>
#include <vector>

#include <android-base/strings.h>

namespace android {
namespace aidl {
namespace perm {

struct AnyOf;
struct AllOf;

typedef std::variant<std::string, AnyOf, AllOf> Expression;
std::string AsJavaAnnotation(const Expression& expr);
std::string JavaFullName(const std::string& permission);

struct AnyOf {
  std::vector<std::string> operands;

  std::string JavaAnnotation() const {
    std::string ret("anyOf = {");
    for (size_t i = 0; i < operands.size(); i++) {
      ret += android::aidl::perm::JavaFullName(operands[i]);
      if (i != operands.size() - 1) {
        ret += ", ";
      }
    }
    return ret + "}";
  }
};

struct AllOf {
  std::vector<std::string> operands;

  std::string JavaAnnotation() const {
    std::string ret("allOf = {");
    for (size_t i = 0; i < operands.size(); i++) {
      ret += android::aidl::perm::JavaFullName(operands[i]);
      if (i != operands.size() - 1) {
        ret += ", ";
      }
    }
    return ret + "}";
  }
};



}  // namespace perm
}  // namespace aidl
}  // namespace android
