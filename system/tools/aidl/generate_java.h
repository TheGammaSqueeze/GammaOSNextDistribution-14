/*
 * Copyright (C) 2016, The Android Open Source Project
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

#include "aidl_language.h"
#include "ast_java.h"
#include "io_delegate.h"
#include "options.h"

#include <optional>
#include <string>

namespace android {
namespace aidl {
namespace java {

void GenerateJava(const std::string& filename, const Options& options,
                  const AidlTypenames& typenames, const AidlDefinedType& defined_type,
                  const IoDelegate& io_delegate);

void GenerateClass(CodeWriter& out, const AidlDefinedType& defined_type, const AidlTypenames& types,
                   const Options& options);

std::unique_ptr<android::aidl::java::Class> GenerateInterfaceClass(const AidlInterface* iface,
                                                                   const AidlTypenames& typenames,
                                                                   const Options& options);

void GenerateConstantDeclarations(CodeWriter& out, const AidlDefinedType& type);

std::string GenerateComments(const AidlCommentable& node);

std::string GenerateAnnotations(const AidlNode& node);

std::vector<std::string> JavaAnnotationsFor(const AidlNode& a);

std::optional<std::string> JavaPermissionAnnotation(const AidlAnnotatable& a);

}  // namespace java
}  // namespace aidl
}  // namespace android
