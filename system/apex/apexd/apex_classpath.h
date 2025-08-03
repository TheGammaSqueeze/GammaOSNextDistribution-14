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

#ifndef ANDROID_APEXD_APEX_CLASSPATH_H_
#define ANDROID_APEXD_APEX_CLASSPATH_H_

#include <android-base/result.h>

#include <set>
#include <string>

namespace android {
namespace apex {

/**
 * An utility class that contains logic to extract classpath fragments
 * information from mounted APEX.
 *
 * The bulk of the work is done by derive_classpath binary, which is found
 * inside sdkext module. This class is a wrapper for calling that binary and
 * parsing its string output into a structured object.
 */
class ClassPath {
  static constexpr const char* kSdkExtModuleName = "com.android.sdkext";

 public:
  static android::base::Result<ClassPath> DeriveClassPath(
      const std::vector<std::string>& temp_mounted_apex_paths,
      const std::string& sdkext_module_name = kSdkExtModuleName);

  bool HasClassPathJars(const std::string& package);

  // Exposed for testing only
  static android::base::Result<ClassPath> ParseFromFile(
      const std::string& file_path);

 private:
  void AddPackageWithClasspathJars(const std::string& package);

  std::set<std::string> packages_with_classpath_jars;
};

}  // namespace apex
}  // namespace android

#endif  // ANDROID_APEXD_APEXD_CHECKPOINT_H_
