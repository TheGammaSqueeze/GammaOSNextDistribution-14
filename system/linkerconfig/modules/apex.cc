/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include "linkerconfig/apex.h"

#include <algorithm>
#include <cstring>
#include <regex>
#include <set>
#include <string>
#include <string_view>
#include <vector>

#include <android-base/file.h>
#include <android-base/result.h>
#include <android-base/strings.h>
#include <apexutil.h>
#include <unistd.h>

#include "linkerconfig/configparser.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"
#include "linkerconfig/stringutil.h"

// include after log.h to avoid macro redefinition error
#include "com_android_apex.h"

using android::base::ErrnoError;
using android::base::Error;
using android::base::ReadFileToString;
using android::base::Result;
using android::base::StartsWith;

namespace {
bool PathExists(const std::string& path) {
  return access(path.c_str(), F_OK) == 0;
}

Result<std::set<std::string>> ReadPublicLibraries(const std::string& filepath) {
  std::string file_content;
  if (!android::base::ReadFileToString(filepath, &file_content, true)) {
    return ErrnoError();
  }
  std::vector<std::string> lines = android::base::Split(file_content, "\n");
  std::set<std::string> sonames;
  for (auto& line : lines) {
    auto trimmed_line = android::base::Trim(line);
    if (trimmed_line.empty() || trimmed_line[0] == '#') {
      continue;
    }
    std::vector<std::string> tokens = android::base::Split(trimmed_line, " ");
    if (tokens.size() < 1 || tokens.size() > 3) {
      return Errorf("Malformed line \"{}\"", line);
    }
    sonames.insert(tokens[0]);
  }
  return sonames;
}

std::vector<std::string> Intersect(const std::vector<std::string>& as,
                                   const std::set<std::string>& bs) {
  std::vector<std::string> intersect;
  std::copy_if(as.begin(),
               as.end(),
               std::back_inserter(intersect),
               [&bs](const auto& a) { return bs.find(a) != bs.end(); });
  return intersect;
}

bool IsValidForPath(const uint_fast8_t c) {
  if (c >= 'a' && c <= 'z') return true;
  if (c >= 'A' && c <= 'Z') return true;
  if (c >= '0' && c <= '9') return true;
  if (c == '-' || c == '_' || c == '.') return true;
  return false;
}

Result<void> VerifyPath(const std::string& path) {
  const size_t length = path.length();
  constexpr char lib_dir[] = "${LIB}";
  constexpr size_t lib_dir_len = (sizeof lib_dir) - 1;
  const std::string_view path_view(path);

  if (length == 0) {
    return Error() << "Empty path is not allowed";
  }

  for (size_t i = 0; i < length; i++) {
    uint_fast8_t current_char = path[i];
    if (current_char == '/') {
      i++;
      if (i >= length) {
        return {};
      } else if (path[i] == '/') {
        return Error() << "'/' should not appear twice in " << path;
      } else if (i + lib_dir_len <= length &&
                 path_view.substr(i, lib_dir_len) == lib_dir) {
        i += lib_dir_len - 1;
      } else {
        for (; i < length; i++) {
          current_char = path[i];
          if (current_char == '/') {
            i--;
            break;
          }

          if (!IsValidForPath(current_char)) {
            return Error() << "Invalid char '" << current_char << "' in "
                           << path;
          }
        }
      }
    } else {
      return Error() << "Invalid char '" << current_char << "' in " << path
                     << " at " << i;
    }
  }

  return {};
}

Result<std::set<std::string>> ReadPublicLibrariesOpt(const std::string& filepath) {
  // Do not fail when public.libraries.txt is missing for minimal Android
  // environment with no ART.
  if (!PathExists(filepath)) {
    return {};
  }
  // But fails with error while reading the file
  auto public_libraries = ReadPublicLibraries(filepath);
  if (!public_libraries.ok()) {
    return Error() << "Can't read " << filepath << ": "
                   << public_libraries.error();
  }
  return public_libraries;
}
}  // namespace

namespace android {
namespace linkerconfig {
namespace modules {

Result<std::map<std::string, ApexInfo>> ScanActiveApexes(const std::string& root) {
  std::map<std::string, ApexInfo> apexes;
  const auto apex_root = root + apex::kApexRoot;
  for (const auto& [path, manifest] : apex::GetActivePackages(apex_root)) {
    bool has_bin = PathExists(path + "/bin");
    bool has_lib = PathExists(path + "/lib") || PathExists(path + "/lib64");
    bool has_shared_lib = manifest.requiresharedapexlibs().size() != 0;

    std::vector<std::string> permitted_paths;
    bool visible = false;

    std::string linker_config_path = path + "/etc/linker.config.pb";
    if (PathExists(linker_config_path)) {
      auto linker_config = ParseLinkerConfig(linker_config_path);

      if (linker_config.ok()) {
        permitted_paths = {linker_config->permittedpaths().begin(),
                           linker_config->permittedpaths().end()};
        for (const std::string& path : permitted_paths) {
          Result<void> verify_permitted_path = VerifyPath(path);
          if (!verify_permitted_path.ok()) {
            return Error() << "Failed to validate path from APEX linker config"
                           << linker_config_path << " : "
                           << verify_permitted_path.error();
          }
        }
        visible = linker_config->visible();
      } else {
        return Error() << "Failed to read APEX linker config : "
                       << linker_config.error();
      }
    }

    ApexInfo info(manifest.name(),
                  TrimPrefix(path, root),
                  {manifest.providenativelibs().begin(),
                   manifest.providenativelibs().end()},
                  {manifest.requirenativelibs().begin(),
                   manifest.requirenativelibs().end()},
                  {manifest.jnilibs().begin(), manifest.jnilibs().end()},
                  std::move(permitted_paths),
                  has_bin,
                  has_lib,
                  visible,
                  has_shared_lib);
    apexes.emplace(manifest.name(), std::move(info));
  }

  // After scanning apexes, we still need to augment ApexInfo based on other
  // input files
  // - original_path: based on /apex/apex-info-list.xml
  // - public_libs: based on /{system, vendor}/etc/public.libraries.txt

  if (!apexes.empty()) {
    // 1. set original_path
    const std::string info_list_file = apex_root + "/apex-info-list.xml";
    auto info_list =
        com::android::apex::readApexInfoList(info_list_file.c_str());
    if (info_list.has_value()) {
      for (const auto& info : info_list->getApexInfo()) {
        // skip inactive apexes
        if (!info.getIsActive()) {
          continue;
        }
        // skip "sharedlibs" apexes
        if (info.getProvideSharedApexLibs()) {
          continue;
        }
        // Get the pre-installed path of the apex. Normally (i.e. in Android),
        // failing to find the pre-installed path is an assertion failure
        // because apexd demands that every apex to have a pre-installed one.
        // However, when this runs in a VM where apexes are seen as virtio block
        // devices, the situation is different. If the APEX in the host side is
        // an updated (or staged) one, the block device representing the APEX on
        // the VM side doesn't have the pre-installed path because the factory
        // version of the APEX wasn't exported to the VM. Therefore, we use the
        // module path as original_path when we are running in a VM which can be
        // guessed by checking if the path is /dev/block/vdN.
        std::string path;
        if (info.hasPreinstalledModulePath()) {
          path = info.getPreinstalledModulePath();
        } else if (StartsWith(info.getModulePath(), "/dev/block/vd")) {
          path = info.getModulePath();
        } else {
          return Error() << "Failed to determine original path for apex "
                         << info.getModuleName() << " at " << info_list_file;
        }
        apexes[info.getModuleName()].original_path = std::move(path);
      }
    } else {
      return ErrnoError() << "Can't read " << info_list_file;
    }

    // 2. set public_libs
    auto system_public_libs =
        ReadPublicLibrariesOpt(root + "/system/etc/public.libraries.txt");
    if (!system_public_libs.ok()) return system_public_libs.error();

    auto vendor_public_libs =
        ReadPublicLibrariesOpt(root + "/vendor/etc/public.libraries.txt");
    if (!vendor_public_libs.ok()) return vendor_public_libs.error();

    for (auto& [name, apex] : apexes) {
      if (apex.InSystem()) {
        apex.public_libs = Intersect(apex.provide_libs, *system_public_libs);
      } else if (apex.InVendor()) {
        apex.public_libs = Intersect(apex.provide_libs, *vendor_public_libs);
      }
    }
  }

  return apexes;
}

bool ApexInfo::InSystem() const {
  // /system partition
  if (StartsWith(original_path, "/system/apex/")) {
    return true;
  }
  // /system_ext partition
  if (StartsWith(original_path, "/system_ext/apex/") ||
      StartsWith(original_path, "/system/system_ext/apex/")) {
    return true;
  }
  // /product partition if it's not separated from "system"
  if (!IsTreblelizedDevice()) {
    if (StartsWith(original_path, "/product/apex/") ||
        StartsWith(original_path, "/system/product/apex/")) {
      return true;
    }
  }
  // Guest mode Android may have system APEXes from host via block APEXes
  if (StartsWith(original_path, "/dev/block/vd")) {
    return true;
  }
  return false;
}

bool ApexInfo::InProduct() const {
  // /product partition if it's separated from "system"
  if (IsTreblelizedDevice()) {
    if (StartsWith(original_path, "/product/apex/") ||
        StartsWith(original_path, "/system/product/apex/")) {
      return true;
    }
  }
  return false;
}

bool ApexInfo::InVendor() const {
  // /vendor partition
  if (StartsWith(original_path, "/vendor/apex/") ||
      StartsWith(original_path, "/system/vendor/apex/")) {
    return true;
  }
  // /odm/apex is not supported yet.
  return false;
}

}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
