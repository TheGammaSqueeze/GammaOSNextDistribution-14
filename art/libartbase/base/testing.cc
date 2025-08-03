/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <string>
#include <vector>

#include "android-base/stringprintf.h"
#include "base/file_utils.h"
#include "base/globals.h"

namespace art {
namespace testing {

namespace {

std::string GetDexFileName(const std::string& jar_prefix, const std::string& prefix) {
  const char* apexPath =
      (jar_prefix == "conscrypt") ?
          kAndroidConscryptApexDefaultPath :
          (jar_prefix == "core-icu4j" ? kAndroidI18nApexDefaultPath : kAndroidArtApexDefaultPath);
  return android::base::StringPrintf(
      "%s%s/javalib/%s.jar", prefix.c_str(), apexPath, jar_prefix.c_str());
}

}  // namespace

std::vector<std::string> GetLibCoreModuleNames(bool core_only) {
  // Note: This must start with the CORE_IMG_JARS in Android.common_path.mk because that's what we
  // use for compiling the boot.art image. It may contain additional modules from TEST_CORE_JARS.

  // CORE_IMG_JARS modules.
  std::vector<std::string> modules{
      "core-oj",
      "core-libart",
      "okhttp",
      "bouncycastle",
      "apache-xml",
  };

  // Additional modules.
  if (!core_only) {
    modules.push_back("core-icu4j");
    modules.push_back("conscrypt");
  }

  return modules;
}

std::vector<std::string> GetLibCoreDexFileNames(const std::string& prefix,
                                                const std::vector<std::string>& modules) {
  std::vector<std::string> result;
  result.reserve(modules.size());
  for (const std::string& module : modules) {
    result.push_back(GetDexFileName(module, prefix));
  }
  return result;
}

std::vector<std::string> GetLibCoreDexFileNames(const std::string& prefix, bool core_only) {
  std::vector<std::string> modules = GetLibCoreModuleNames(core_only);
  return GetLibCoreDexFileNames(prefix, modules);
}

std::vector<std::string> GetLibCoreDexLocations(const std::vector<std::string>& modules) {
  return GetLibCoreDexFileNames(/*prefix=*/"", modules);
}

std::vector<std::string> GetLibCoreDexLocations(bool core_only) {
  std::vector<std::string> modules = GetLibCoreModuleNames(core_only);
  return GetLibCoreDexLocations(modules);
}

}  // namespace testing
}  // namespace art
