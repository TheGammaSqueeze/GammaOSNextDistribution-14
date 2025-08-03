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

#include "apex_classpath.h"

#include <android-base/file.h>
#include <android-base/scopeguard.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <logwrap/logwrap.h>

#include <fstream>
#include <regex>

namespace android {
namespace apex {

using ::android::base::Error;
using ::android::base::StringPrintf;

android::base::Result<ClassPath> ClassPath::DeriveClassPath(
    const std::vector<std::string>& temp_mounted_apex_paths,
    const std::string& sdkext_module_name) {
  if (temp_mounted_apex_paths.empty()) {
    return Error()
           << "Invalid argument: There are no APEX to derive claspath from";
  }
  // Call derive_classpath binary to generate required information

  // Prefer using the binary from staged session if possible
  std::string apex_of_binary =
      StringPrintf("/apex/%s", sdkext_module_name.c_str());
  for (const auto& temp_mounted_apex_path : temp_mounted_apex_paths) {
    if (temp_mounted_apex_path.starts_with(apex_of_binary + "@")) {
      apex_of_binary = temp_mounted_apex_path;
      break;
    }
  }
  std::string binary_path =
      StringPrintf("%s/bin/derive_classpath", apex_of_binary.c_str());
  std::string scan_dirs_flag =
      StringPrintf("--scan-dirs=%s",
                   android::base::Join(temp_mounted_apex_paths, ",").c_str());

  // Create a temp file to write output
  auto temp_output_path = "/apex/derive_classpath_temp";
  auto cleanup = [temp_output_path]() {
    android::base::RemoveFileIfExists(temp_output_path);
  };
  auto scope_guard = android::base::make_scope_guard(cleanup);
  // Cleanup to ensure we are creating an empty file
  cleanup();
  // Create the empty file where derive_classpath will write into
  std::ofstream _(temp_output_path);

  const char* const argv[] = {binary_path.c_str(), scan_dirs_flag.c_str(),
                              temp_output_path};
  auto rc = logwrap_fork_execvp(arraysize(argv), argv, nullptr, false, LOG_ALOG,
                                false, nullptr);
  if (rc != 0) {
    return Error() << "Running derive_classpath failed; binary path: " +
                          binary_path;
  }

  return ClassPath::ParseFromFile(temp_output_path);
}

// Parse the string output into structured information
// The raw output from derive_classpath has the following format:
// ```
// export BOOTCLASSPATH path/to/jar1:/path/to/jar2
// export DEX2OATBOOTCLASSPATH
// export SYSTEMSERVERCLASSPATH path/to/some/jar
android::base::Result<ClassPath> ClassPath::ParseFromFile(
    const std::string& file_path) {
  ClassPath result;

  std::string contents;
  auto read_status = android::base::ReadFileToString(file_path, &contents,
                                                     /*follow_symlinks=*/false);
  if (!read_status) {
    return Error() << "Failed to read classpath info from file";
  }

  // Jars in apex have the following format: /apex/<package-name>/*
  const std::regex capture_apex_package_name("^/apex/([^/]+)/");

  for (const auto& line : android::base::Split(contents, "\n")) {
    // Split the line by space. The second element determines which type of
    // classpath we are dealing with and the third element are the jars
    // separated by :
    auto tokens = android::base::Split(line, " ");
    if (tokens.size() < 3) {
      continue;
    }
    auto jars_list = tokens[2];
    for (const auto& jar_path : android::base::Split(jars_list, ":")) {
      std::smatch match;
      if (std::regex_search(jar_path, match, capture_apex_package_name)) {
        auto package_name = match[1];
        result.AddPackageWithClasspathJars(package_name);
      }
    }
  }
  return result;
}

void ClassPath::AddPackageWithClasspathJars(const std::string& package) {
  packages_with_classpath_jars.insert(package);
}

bool ClassPath::HasClassPathJars(const std::string& package) {
  return packages_with_classpath_jars.find(package) !=
         packages_with_classpath_jars.end();
}

}  // namespace apex
}  // namespace android
