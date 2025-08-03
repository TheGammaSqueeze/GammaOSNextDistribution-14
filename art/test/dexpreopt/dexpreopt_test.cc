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

// A test to verify that the compilation artifacts built in the system image for all system server
// jars are used. It will fail if odrefresh has run (in which case, artifacts in /data will be used
// instead) or the artifacts in the system image are rejected by the runtime. This test should only
// run on a clean system without any APEX (including com.android.art.testing) installed on data,
// which otherwise will trigger odrefresh.

#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>

#include <algorithm>
#include <iterator>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

#include "android-base/properties.h"
#include "android-base/result.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "arch/instruction_set.h"
#include "base/common_art_test.h"
#include "base/file_utils.h"
#include "base/os.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "oat/oat_file_assistant.h"
#include "procinfo/process_map.h"

namespace art {

using ::android::base::Error;
using ::testing::IsSupersetOf;

constexpr const char* kZygote32 = "zygote";
constexpr const char* kZygote64 = "zygote64";

std::vector<std::string> GetListFromEnv(const std::string& name) {
  const char* env_value = getenv(name.c_str());
  if (env_value == nullptr || strlen(env_value) == 0) {
    return {};
  }
  return android::base::Split(env_value, ":");
}

android::base::Result<std::vector<std::pair<std::string, InstructionSet>>> GetZygoteNamesAndIsas() {
  std::vector<std::pair<std::string, InstructionSet>> names_and_isas;

  // Possible values are: "zygote32", "zygote64", "zygote32_64", "zygote64_32".
  std::string zygote_kinds = android::base::GetProperty("ro.zygote", {});
  if (zygote_kinds.empty()) {
    return Errorf("Unable to get Zygote kinds");
  }

  switch (kRuntimeISA) {
    case InstructionSet::kArm:
    case InstructionSet::kArm64:
      if (zygote_kinds.find("32") != std::string::npos) {
        names_and_isas.push_back(std::make_pair(kZygote32, InstructionSet::kArm));
      }
      if (zygote_kinds.find("64") != std::string::npos) {
        names_and_isas.push_back(std::make_pair(kZygote64, InstructionSet::kArm64));
      }
      break;
    case InstructionSet::kX86:
    case InstructionSet::kX86_64:
      if (zygote_kinds.find("32") != std::string::npos) {
        names_and_isas.push_back(std::make_pair(kZygote32, InstructionSet::kX86));
      }
      if (zygote_kinds.find("64") != std::string::npos) {
        names_and_isas.push_back(std::make_pair(kZygote64, InstructionSet::kX86_64));
      }
      break;
    default:
      return Errorf("Unknown runtime ISA: {}", GetInstructionSetString(kRuntimeISA));
  }

  return names_and_isas;
}

android::base::Result<std::vector<std::string>> GetZygoteExpectedArtifacts(InstructionSet isa) {
  std::vector<std::string> jars = GetListFromEnv("DEX2OATBOOTCLASSPATH");
  if (jars.empty()) {
    return Errorf("Environment variable `DEX2OATBOOTCLASSPATH` is not defined or empty");
  }
  std::string error_msg;
  std::string first_mainline_jar = GetFirstMainlineFrameworkLibraryFilename(&error_msg);
  if (first_mainline_jar.empty()) {
    return Error() << error_msg;
  }
  jars.push_back(std::move(first_mainline_jar));
  std::string art_root = GetArtRoot();
  std::string android_root = GetAndroidRoot();
  std::vector<std::string> artifacts;
  for (size_t i = 0; i < jars.size(); i++) {
    const std::string& jar = jars[i];
    std::string basename =
        i == 0 ? "boot.oat" : "boot-" + ReplaceFileExtension(android::base::Basename(jar), "oat");
    std::string dir = android::base::StartsWith(jar, art_root) ? GetPrebuiltPrimaryBootImageDir() :
                                                                 android_root + "/framework";
    std::string oat_file = android::base::StringPrintf(
        "%s/%s/%s", dir.c_str(), GetInstructionSetString(isa), basename.c_str());

    if (!OS::FileExists(oat_file.c_str())) {
      if (errno == EACCES) {
        return ErrnoErrorf("Failed to stat() {}", oat_file);
      }
      // Dexpreopting is probably disabled. No need to report missing artifacts here because
      // artifact generation is already checked at build time.
      continue;
    }

    artifacts.push_back(oat_file);
  }
  return artifacts;
}

android::base::Result<std::vector<std::string>> GetSystemServerExpectedArtifacts() {
  std::vector<std::string> jars = GetListFromEnv("SYSTEMSERVERCLASSPATH");
  if (jars.empty()) {
    return Errorf("Environment variable `SYSTEMSERVERCLASSPATH` is not defined or empty");
  }
  std::vector<std::string> standalone_jars = GetListFromEnv("STANDALONE_SYSTEMSERVER_JARS");
  std::move(standalone_jars.begin(), standalone_jars.end(), std::back_inserter(jars));
  if (kRuntimeISA == InstructionSet::kNone) {
    return Errorf("Unable to get system server ISA");
  }
  std::vector<std::string> artifacts;
  for (const std::string& jar : jars) {
    std::string error_msg;
    std::string odex_file;

    if (!OatFileAssistant::DexLocationToOdexFilename(jar, kRuntimeISA, &odex_file, &error_msg)) {
      return Errorf("Failed to get odex filename: {}", error_msg);
    }

    if (!OS::FileExists(odex_file.c_str())) {
      if (errno == EACCES) {
        return ErrnoErrorf("Failed to stat() {}", odex_file);
      }
      // Dexpreopting is probably disabled. No need to report missing artifacts here because
      // artifact generation is already checked at build time.
      continue;
    }

    artifacts.push_back(odex_file);
  }
  return artifacts;
}

android::base::Result<std::vector<std::string>> GetMappedFiles(pid_t pid,
                                                               const std::string& extension,
                                                               uint16_t flags) {
  std::vector<android::procinfo::MapInfo> maps;
  if (!android::procinfo::ReadProcessMaps(pid, &maps)) {
    return ErrnoErrorf("Failed to get mapped memory regions of pid {}", pid);
  }
  std::vector<std::string> files;
  for (const android::procinfo::MapInfo& map : maps) {
    if ((map.flags & flags) && android::base::EndsWith(map.name, extension)) {
      files.push_back(map.name);
    }
  }
  return files;
}

android::base::Result<std::vector<std::string>> GetZygoteMappedOatFiles(
    const std::string& zygote_name) {
  std::vector<pid_t> pids = art::GetPidByName(zygote_name);
  if (pids.empty()) {
    return Errorf("Unable to find Zygote process: {}", zygote_name);
  }
  // OAT files in boot images may not be mmaped with PROT_EXEC if they don't contain executable
  // code. Checking PROT_READ is sufficient because an OAT file will be unmapped if the runtime
  // rejects it.
  return GetMappedFiles(pids[0], ".oat", PROT_READ);
}

android::base::Result<std::vector<std::string>> GetSystemServerArtifactsMappedOdexes() {
  std::vector<pid_t> pids = art::GetPidByName("system_server");
  if (pids.size() != 1) {
    return Errorf("There should be exactly one `system_server` process, found {}", pids.size());
  }
  return GetMappedFiles(pids[0], ".odex", PROT_READ);
}

TEST(DexpreoptTest, ForZygote) {
  android::base::Result<std::vector<std::pair<std::string, InstructionSet>>> zygote_names_and_isas =
      GetZygoteNamesAndIsas();
  ASSERT_RESULT_OK(zygote_names_and_isas);

  for (const auto& [zygote_name, isa] : *zygote_names_and_isas) {
    android::base::Result<std::vector<std::string>> expected_artifacts =
        GetZygoteExpectedArtifacts(isa);
    ASSERT_RESULT_OK(expected_artifacts);

    if (expected_artifacts->empty()) {
      // Skip the test if dexpreopting is disabled.
      return;
    }

    android::base::Result<std::vector<std::string>> mapped_oat_files =
        GetZygoteMappedOatFiles(zygote_name);
    ASSERT_RESULT_OK(mapped_oat_files);

    EXPECT_THAT(mapped_oat_files.value(), IsSupersetOf(expected_artifacts.value()));
  }
}

TEST(DexpreoptTest, ForSystemServer) {
  android::base::Result<std::vector<std::string>> expected_artifacts =
      GetSystemServerExpectedArtifacts();
  ASSERT_RESULT_OK(expected_artifacts);

  if (expected_artifacts->empty()) {
    // Skip the test if dexpreopting is disabled.
    return;
  }

  android::base::Result<std::vector<std::string>> mapped_odexes =
      GetSystemServerArtifactsMappedOdexes();
  ASSERT_RESULT_OK(mapped_odexes);

  EXPECT_THAT(mapped_odexes.value(), IsSupersetOf(expected_artifacts.value()));
}

}  // namespace art
