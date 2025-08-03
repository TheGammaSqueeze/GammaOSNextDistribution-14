/*
 * Copyright (C) 2024 The Android Open Source Project
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
#define LOG_TAG "ApexSupport"

#include "android/apexsupport.h"

#include <dlfcn.h>

#include <string>

#include <android/dlext.h>
#include <log/log.h>

extern "C" android_namespace_t *android_get_exported_namespace(const char *);

void *AApexSupport_loadLibrary(const char *_Nonnull name,
                               const char *_Nonnull apexName, int flag) {
  // convert the apex name into the linker namespace name
  std::string namespaceName = apexName;
  std::replace(namespaceName.begin(), namespaceName.end(), '.', '_');

  android_namespace_t *ns =
      android_get_exported_namespace(namespaceName.c_str());
  if (ns == nullptr) {
    ALOGE("Could not find namespace for %s APEX. Is it visible?", apexName);
    return nullptr;
  }

  const android_dlextinfo dlextinfo = {
      .flags = ANDROID_DLEXT_USE_NAMESPACE,
      .library_namespace = ns,
  };
  void *handle = android_dlopen_ext(name, flag, &dlextinfo);
  if (!handle) {
    ALOGE("Could not load %s: %s", name, dlerror());
    return nullptr;
  }
  return handle;
}
