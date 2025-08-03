/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ART_LIBNATIVELOADER_LIBRARY_NAMESPACES_H_
#define ART_LIBNATIVELOADER_LIBRARY_NAMESPACES_H_

#if !defined(ART_TARGET_ANDROID)
#error "Not available for host or linux target"
#endif

#include <list>
#include <optional>
#include <string>
#include <string_view>

#include "android-base/result.h"
#include "jni.h"
#include "native_loader_namespace.h"

namespace android::nativeloader {

using android::base::Result;

// The device may be configured to have the vendor libraries loaded to a separate namespace.
// For historical reasons this namespace was named sphal but effectively it is intended
// to use to load vendor libraries to separate namespace with controlled interface between
// vendor and system namespaces.
constexpr const char* kVendorNamespaceName = "sphal";
// Similar to sphal namespace, product namespace provides some product libraries.
constexpr const char* kProductNamespaceName = "product";

// vndk namespace for unbundled vendor apps
constexpr const char* kVndkNamespaceName = "vndk";
// vndk_product namespace for unbundled product apps
constexpr const char* kVndkProductNamespaceName = "vndk_product";

// API domains, roughly corresponding to partitions. Interdependencies between
// these must follow API restrictions, while intradependencies do not.
using ApiDomain = enum {
  API_DOMAIN_DEFAULT = 0,  // Locations other than those below, in particular for ordinary apps
  API_DOMAIN_VENDOR = 1,   // Vendor partition
  API_DOMAIN_PRODUCT = 2,  // Product partition
};

ApiDomain GetApiDomainFromPath(const std::string_view path);
Result<ApiDomain> GetApiDomainFromPathList(const std::string& path_list);

// LibraryNamespaces is a singleton object that manages NativeLoaderNamespace
// objects for an app process. Its main job is to create (and configure) a new
// NativeLoaderNamespace object for a Java ClassLoader, and to find an existing
// object for a given ClassLoader.
class LibraryNamespaces {
 public:
  LibraryNamespaces() : initialized_(false), app_main_namespace_(nullptr) {}

  LibraryNamespaces(LibraryNamespaces&&) = default;
  LibraryNamespaces(const LibraryNamespaces&) = delete;
  LibraryNamespaces& operator=(const LibraryNamespaces&) = delete;

  void Initialize();
  void Reset() {
    namespaces_.clear();
    initialized_ = false;
    app_main_namespace_ = nullptr;
  }
  Result<NativeLoaderNamespace*> Create(JNIEnv* env,
                                        uint32_t target_sdk_version,
                                        jobject class_loader,
                                        ApiDomain api_domain,
                                        bool is_shared,
                                        const std::string& dex_path,
                                        jstring library_path_j,
                                        jstring permitted_path_j,
                                        jstring uses_library_list_j);
  NativeLoaderNamespace* FindNamespaceByClassLoader(JNIEnv* env, jobject class_loader);

 private:
  Result<void> InitPublicNamespace(const char* library_path);
  NativeLoaderNamespace* FindParentNamespaceByClassLoader(JNIEnv* env, jobject class_loader);

  bool initialized_;
  NativeLoaderNamespace* app_main_namespace_;
  std::list<std::pair<jweak, NativeLoaderNamespace>> namespaces_;
};

std::optional<std::string> FindApexNamespaceName(const std::string& location);

}  // namespace android::nativeloader

#endif  // ART_LIBNATIVELOADER_LIBRARY_NAMESPACES_H_
