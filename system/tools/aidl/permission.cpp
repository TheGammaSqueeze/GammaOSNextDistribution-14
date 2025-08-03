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

#include "permission.h"
#include <memory>
#include <string>
#include <variant>
#include <vector>

#include <android-base/strings.h>

namespace android {
namespace aidl {
namespace perm {

std::string AsJavaAnnotation(const Expression& expr) {
  if (const auto& s = std::get_if<std::string>(&expr); s) {
    return JavaFullName(*s);
  }
  if (const auto& all = std::get_if<AllOf>(&expr); all) {
    return all->JavaAnnotation();
  }
  if (const auto& any = std::get_if<AnyOf>(&expr); any) {
    return any->JavaAnnotation();
  }
  return "";
}

std::string JavaFullName(const std::string& permission) {
  if (permission.find('.') == std::string::npos) {
    return "android.Manifest.permission." + permission;
  }
  return permission;
}

}  // namespace perm
}  // namespace aidl
}  // namespace android
