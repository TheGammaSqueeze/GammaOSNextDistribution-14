/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "service.h"

#include <jni.h>

#include <filesystem>

#include "android-base/errors.h"
#include "android-base/file.h"
#include "android-base/result.h"
#include "class_loader_context.h"
#include "gc/heap.h"
#include "nativehelper/utils.h"
#include "runtime.h"

namespace art {
namespace service {

using ::android::base::Dirname;
using ::android::base::Result;

Result<void> ValidateAbsoluteNormalPath(const std::string& path_str) {
  if (path_str.empty()) {
    return Errorf("Path is empty");
  }
  if (path_str.find('\0') != std::string::npos) {
    return Errorf("Path '{}' has invalid character '\\0'", path_str);
  }
  std::filesystem::path path(path_str);
  if (!path.is_absolute()) {
    return Errorf("Path '{}' is not an absolute path", path_str);
  }
  if (path.lexically_normal() != path_str) {
    return Errorf("Path '{}' is not in normal form", path_str);
  }
  return {};
}

Result<void> ValidatePathElementSubstring(const std::string& path_element_substring,
                                          const std::string& name) {
  if (path_element_substring.empty()) {
    return Errorf("{} is empty", name);
  }
  if (path_element_substring.find('/') != std::string::npos) {
    return Errorf("{} '{}' has invalid character '/'", name, path_element_substring);
  }
  if (path_element_substring.find('\0') != std::string::npos) {
    return Errorf("{} '{}' has invalid character '\\0'", name, path_element_substring);
  }
  return {};
}

Result<void> ValidatePathElement(const std::string& path_element, const std::string& name) {
  OR_RETURN(ValidatePathElementSubstring(path_element, name));
  if (path_element == "." || path_element == "..") {
    return Errorf("Invalid {} '{}'", name, path_element);
  }
  return {};
}

Result<void> ValidateDexPath(const std::string& dex_path) {
  OR_RETURN(ValidateAbsoluteNormalPath(dex_path));
  return {};
}

std::string GetGarbageCollector() {
  return Runtime::Current()->GetHeap()->GetForegroundCollectorName();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_server_art_ArtJni_validateDexPathNative(JNIEnv* env, jobject, jstring j_dex_path) {
  std::string dex_path(GET_UTF_OR_RETURN(env, j_dex_path));

  if (Result<void> result = ValidateDexPath(dex_path); !result.ok()) {
    return CREATE_UTF_OR_RETURN(env, result.error().message()).release();
  } else {
    return nullptr;
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_server_art_ArtJni_validateClassLoaderContextNative(
    JNIEnv* env, jobject, jstring j_dex_path, jstring j_class_loader_context) {
  ScopedUtfChars dex_path = GET_UTF_OR_RETURN(env, j_dex_path);
  std::string class_loader_context(GET_UTF_OR_RETURN(env, j_class_loader_context));

  if (class_loader_context == ClassLoaderContext::kUnsupportedClassLoaderContextEncoding) {
    return nullptr;
  }

  std::unique_ptr<ClassLoaderContext> context = ClassLoaderContext::Create(class_loader_context);
  if (context == nullptr) {
    return CREATE_UTF_OR_RETURN(
               env, ART_FORMAT("Class loader context '{}' is invalid", class_loader_context))
        .release();
  }

  std::vector<std::string> flattened_context = context->FlattenDexPaths();
  std::string dex_dir = Dirname(dex_path);
  for (const std::string& context_element : flattened_context) {
    std::string context_path = std::filesystem::path(dex_dir).append(context_element);
    if (Result<void> result = ValidateDexPath(context_path); !result.ok()) {
      return CREATE_UTF_OR_RETURN(env, result.error().message()).release();
    }
  }

  return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_server_art_ArtJni_getGarbageCollectorNative(JNIEnv* env, jobject) {
  return CREATE_UTF_OR_RETURN(env, GetGarbageCollector()).release();
}

}  // namespace service
}  // namespace art
