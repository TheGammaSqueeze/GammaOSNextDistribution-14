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

#define LOG_TAG "derive_sdk"

#include "derive_sdk.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-modules-utils/sdk_level.h>
#include <dirent.h>
#include <sys/stat.h>

#include <algorithm>
#include <iostream>
#include <vector>

#include "packages/modules/common/proto/sdk.pb.h"

namespace android {
namespace derivesdk {

static const std::unordered_map<std::string, SdkModule> kApexNameToModule = {
    {"com.android.adservices", SdkModule::AD_SERVICES},
    {"com.android.appsearch", SdkModule::APPSEARCH},
    {"com.android.art", SdkModule::ART},
    {"com.android.configinfrastructure", SdkModule::CONFIG_INFRASTRUCTURE},
    {"com.android.conscrypt", SdkModule::CONSCRYPT},
    {"com.android.extservices", SdkModule::EXT_SERVICES},
    {"com.android.healthfitness", SdkModule::HEALTH_FITNESS},
    {"com.android.ipsec", SdkModule::IPSEC},
    {"com.android.media", SdkModule::MEDIA},
    {"com.android.mediaprovider", SdkModule::MEDIA_PROVIDER},
    {"com.android.ondevicepersonalization", SdkModule::ON_DEVICE_PERSONALIZATION},
    {"com.android.permission", SdkModule::PERMISSIONS},
    {"com.android.scheduling", SdkModule::SCHEDULING},
    {"com.android.sdkext", SdkModule::SDK_EXTENSIONS},
    {"com.android.os.statsd", SdkModule::STATSD},
    {"com.android.tethering", SdkModule::TETHERING},
};

static const std::unordered_set<SdkModule> kRModules = {
    SdkModule::CONSCRYPT,      SdkModule::EXT_SERVICES,   SdkModule::IPSEC,
    SdkModule::MEDIA,          SdkModule::MEDIA_PROVIDER, SdkModule::PERMISSIONS,
    SdkModule::SDK_EXTENSIONS, SdkModule::STATSD,         SdkModule::TETHERING,
};

static const std::unordered_set<SdkModule> kSModules = {SdkModule::ART, SdkModule::SCHEDULING};

static const std::unordered_set<SdkModule> kTModules = {
    SdkModule::AD_SERVICES, SdkModule::APPSEARCH, SdkModule::ON_DEVICE_PERSONALIZATION};

static const std::unordered_set<SdkModule> kUModules = {SdkModule::CONFIG_INFRASTRUCTURE,
                                                        SdkModule::HEALTH_FITNESS};

static const std::string kSystemPropertiesPrefix = "build.version.extensions.";

void ReadSystemProperties(std::map<std::string, std::string>& properties) {
  const std::string default_ = "<not set>";

  for (const auto& dessert : {"r", "s", "t", "ad_services", "u"}) {
    properties[kSystemPropertiesPrefix + dessert] =
        android::base::GetProperty(kSystemPropertiesPrefix + dessert, default_);
  }
  properties["ro.build.version.sdk"] = android::base::GetProperty("ro.build.version.sdk", default_);
}

bool ReadDatabase(const std::string& db_path, ExtensionDatabase& db) {
  std::string contents;
  if (!android::base::ReadFileToString(db_path, &contents, true)) {
    PLOG(ERROR) << "failed to read " << db_path << ": ";
    return false;
  }
  if (!db.ParseFromString(contents)) {
    LOG(ERROR) << "failed to parse " << db_path;
    return false;
  }
  return true;
}

bool VersionRequirementsMet(
    const ExtensionVersion& ext_version,
    const std::unordered_set<SdkModule>& relevant_modules,
    const std::unordered_map<SdkModule, int>& module_versions) {
  for (const auto& requirement : ext_version.requirements()) {
    // Only requirements on modules relevant for this extension matter.
    if (relevant_modules.find(requirement.module()) == relevant_modules.end())
      continue;

    auto version = module_versions.find(requirement.module());
    if (version == module_versions.end()) {
      LOG(DEBUG) << "Not version " << ext_version.version() << ": Module "
                 << requirement.module() << " is missing";
      return false;
    }
    if (version->second < requirement.version().version()) {
      LOG(DEBUG) << "Not version " << ext_version.version() << ": Module "
                 << requirement.module() << " version (" << version->second
                 << ") too low. Needed " << requirement.version().version();
      return false;
    }
  }
  return true;
}

int GetSdkLevel(const ExtensionDatabase& db,
                const std::unordered_set<SdkModule>& relevant_modules,
                const std::unordered_map<SdkModule, int>& module_versions) {
  int max = 0;

  for (const auto& ext_version : db.versions()) {
    if (ext_version.version() > max &&
        VersionRequirementsMet(ext_version, relevant_modules,
                               module_versions)) {
      max = ext_version.version();
    }
  }
  return max;
}

bool SetExtension(const std::string& extension_name, int version) {
  LOG(INFO) << "extension " << extension_name << " version is " << version;

  const std::string property_name = kSystemPropertiesPrefix + extension_name;
  if (!android::base::SetProperty(property_name, std::to_string(version))) {
    LOG(ERROR) << "failed to set sdk_info prop " << property_name;
    return false;
  }
  return true;
}

bool GetAndSetExtension(const std::string& extension_name, const ExtensionDatabase& db,
                        const std::unordered_set<SdkModule>& relevant_modules,
                        const std::unordered_map<SdkModule, int>& module_versions) {
  int version = GetSdkLevel(db, relevant_modules, module_versions);
  return SetExtension(extension_name, version);
}

bool ReadSdkInfoFromApexes(const std::string& mountpath,
                           std::unordered_map<SdkModule, int>& versions) {
  for (const auto& module_itr : kApexNameToModule) {
    std::string path = mountpath + "/" + module_itr.first + "/etc/sdkinfo.pb";
    struct stat statbuf;
    if (stat(path.c_str(), &statbuf) != 0) {
      continue;
    }
    std::string contents;
    if (!android::base::ReadFileToString(path, &contents, true)) {
      LOG(ERROR) << "failed to read " << path;
      continue;
    }
    SdkVersion sdk_version;
    if (!sdk_version.ParseFromString(contents)) {
      LOG(ERROR) << "failed to parse " << path;
      continue;
    }
    SdkModule module = module_itr.second;
    LOG(INFO) << "Read version " << sdk_version.version() << " from " << module;
    versions[module] = sdk_version.version();
  }
  return true;
}

bool SetSdkLevels(const std::string& mountpath) {
  ExtensionDatabase db;
  if (!ReadDatabase(mountpath + "/com.android.sdkext/etc/extensions_db.pb", db)) {
    LOG(ERROR) << "Failed to read database";
    return false;
  }

  std::unordered_map<SdkModule, int> versions;
  if (!ReadSdkInfoFromApexes(mountpath, versions)) {
    LOG(ERROR) << "Failed to SDK info from apexes";
    return false;
  }

  std::unordered_set<SdkModule> relevant_modules;
  relevant_modules.insert(kRModules.begin(), kRModules.end());
  if (!GetAndSetExtension("r", db, relevant_modules, versions)) {
    return false;
  }

  relevant_modules.insert(kSModules.begin(), kSModules.end());
  if (android::modules::sdklevel::IsAtLeastS()) {
    if (!GetAndSetExtension("s", db, relevant_modules, versions)) {
      return false;
    }
  }

  relevant_modules.insert(kTModules.begin(), kTModules.end());
  if (android::modules::sdklevel::IsAtLeastT()) {
    if (!GetAndSetExtension("t", db, relevant_modules, versions)) {
      return false;
    }
  }

  relevant_modules.insert(kUModules.begin(), kUModules.end());
  if (android::modules::sdklevel::IsAtLeastU()) {
    if (!GetAndSetExtension("u", db, relevant_modules, versions)) {
      return false;
    }
  }

  // Consistency check: verify all modules with requirements is included in some dessert
  for (const auto& ext_version : db.versions()) {
    for (const auto& requirement : ext_version.requirements()) {
      if (relevant_modules.find(requirement.module()) == relevant_modules.end()) {
        LOG(ERROR) << "version " << ext_version.version() << " requires unmapped module"
                   << requirement.module();
        return false;
      }
    }
  }

  if (android::modules::sdklevel::IsAtLeastT()) {
    if (versions[AD_SERVICES] >= 7) {
      if (!SetExtension("ad_services", versions[AD_SERVICES])) {
        return false;
      }
    } else {
      relevant_modules.clear();
      relevant_modules.insert(SdkModule::AD_SERVICES);
      if (!GetAndSetExtension("ad_services", db, relevant_modules, versions)) {
        return false;
      }
    }
  }
  return true;
}

bool PrintHeader() {
  std::map<std::string, std::string> properties;
  ReadSystemProperties(properties);

  bool print_separator = false;
  std::cout << "[";
  for (const auto& property : properties) {
    if (property.first.find(kSystemPropertiesPrefix) == 0) {
      if (print_separator) {
        std::cout << ", ";
      }
      const auto name = property.first.substr(kSystemPropertiesPrefix.size());
      std::cout << name << "=" << property.second;
      print_separator = true;
    }
  }
  std::cout << "]\n";
  return true;
}

bool PrintDump(const std::string& mountpath, std::ostream& ostream) {
  std::map<std::string, std::string> properties;
  ReadSystemProperties(properties);

  std::unordered_map<SdkModule, int> versions;
  if (!ReadSdkInfoFromApexes(mountpath, versions)) {
    LOG(ERROR) << "Failed to read SDK info from apexes";
    return false;
  }

  ostream << "system properties:\n";
  for (const auto& property : properties) {
    ostream << "  " << property.first << ":" << property.second << "\n";
  }

  ostream << "apex module versions:\n";
  for (const auto& version : versions) {
    ostream << "  " << SdkModule_Name(version.first) << ":" << version.second << "\n";
  }

  ExtensionDatabase db;
  if (!ReadDatabase(mountpath + "/com.android.sdkext/etc/extensions_db.pb", db)) {
    LOG(ERROR) << "Failed to read database";
    return false;
  }
  std::map<int, std::unordered_set<SdkModule>> new_requirements;
  for (const auto& ext_version : db.versions()) {
    std::unordered_set<SdkModule> new_required;
    for (const auto& requirement : ext_version.requirements()) {
      if (requirement.version().version() == ext_version.version())
        new_required.insert(requirement.module());
    }
    new_requirements[ext_version.version()] = new_required;
  }

  ostream << "last 3 version requirements:\n";
  int i = 0;
  for (auto itr = new_requirements.crbegin(); itr != new_requirements.crend() && i < 3;
       ++itr, ++i) {
    ostream << "  " << itr->first << ": ";
    for (auto const& module : itr->second) ostream << SdkModule_Name(module) << " ";
    ostream << std::endl;
  }

  return true;
}

}  // namespace derivesdk
}  // namespace android
