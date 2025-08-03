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

#include "odrefresh.h"

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sysexits.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <functional>
#include <initializer_list>
#include <iosfwd>
#include <iostream>
#include <iterator>
#include <memory>
#include <optional>
#include <ostream>
#include <set>
#include <sstream>
#include <string>
#include <string_view>
#include <system_error>
#include <type_traits>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "android-base/chrono_utils.h"
#include "android-base/file.h"
#include "android-base/function_ref.h"
#include "android-base/logging.h"
#include "android-base/macros.h"
#include "android-base/parseint.h"
#include "android-base/properties.h"
#include "android-base/result.h"
#include "android-base/scopeguard.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "android-modules-utils/sdk_level.h"
#include "arch/instruction_set.h"
#include "base/file_utils.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/os.h"
#include "base/stl_util.h"
#include "base/unix_file/fd_file.h"
#include "com_android_apex.h"
#include "com_android_art.h"
#include "dex/art_dex_file_loader.h"
#include "exec_utils.h"
#include "gc/collector/mark_compact.h"
#include "odr_artifacts.h"
#include "odr_common.h"
#include "odr_config.h"
#include "odr_fs_utils.h"
#include "odr_metrics.h"
#include "odrefresh/odrefresh.h"
#include "selinux/selinux.h"
#include "tools/cmdline_builder.h"

namespace art {
namespace odrefresh {

namespace {

namespace apex = com::android::apex;
namespace art_apex = com::android::art;

using ::android::base::Basename;
using ::android::base::Dirname;
using ::android::base::Join;
using ::android::base::ParseInt;
using ::android::base::Result;
using ::android::base::SetProperty;
using ::android::base::Split;
using ::android::base::StartsWith;
using ::android::base::StringPrintf;
using ::android::base::Timer;
using ::android::modules::sdklevel::IsAtLeastU;
using ::android::modules::sdklevel::IsAtLeastV;
using ::art::tools::CmdlineBuilder;

// Name of cache info file in the ART Apex artifact cache.
constexpr const char* kCacheInfoFile = "cache-info.xml";

// Maximum execution time for odrefresh from start to end.
constexpr time_t kMaximumExecutionSeconds = 480;

// Maximum execution time for any child process spawned.
constexpr time_t kMaxChildProcessSeconds = 120;

constexpr mode_t kFileMode = S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH;

constexpr const char* kFirstBootImageBasename = "boot.art";
constexpr const char* kMinimalBootImageBasename = "boot_minimal.art";

// The default compiler filter for primary boot image.
constexpr const char* kPrimaryCompilerFilter = "speed-profile";

// The compiler filter for boot image mainline extension. We don't have profiles for mainline BCP
// jars, so we always use "verify".
constexpr const char* kMainlineCompilerFilter = "verify";

void EraseFiles(const std::vector<std::unique_ptr<File>>& files) {
  for (auto& file : files) {
    file->Erase(/*unlink=*/true);
  }
}

// Moves `files` to the directory `output_directory_path`.
//
// If any of the files cannot be moved, then all copies of the files are removed from both
// the original location and the output location.
//
// Returns true if all files are moved, false otherwise.
bool MoveOrEraseFiles(const std::vector<std::unique_ptr<File>>& files,
                      std::string_view output_directory_path) {
  std::vector<std::unique_ptr<File>> output_files;
  for (auto& file : files) {
    std::string file_basename(Basename(file->GetPath()));
    std::string output_file_path = ART_FORMAT("{}/{}", output_directory_path, file_basename);
    std::string input_file_path = file->GetPath();

    output_files.emplace_back(OS::CreateEmptyFileWriteOnly(output_file_path.c_str()));
    if (output_files.back() == nullptr) {
      PLOG(ERROR) << "Failed to open " << QuotePath(output_file_path);
      output_files.pop_back();
      EraseFiles(output_files);
      EraseFiles(files);
      return false;
    }

    if (fchmod(output_files.back()->Fd(), kFileMode) != 0) {
      PLOG(ERROR) << "Could not set file mode on " << QuotePath(output_file_path);
      EraseFiles(output_files);
      EraseFiles(files);
      return false;
    }

    size_t file_bytes = file->GetLength();
    if (!output_files.back()->Copy(file.get(), /*offset=*/0, file_bytes)) {
      PLOG(ERROR) << "Failed to copy " << QuotePath(file->GetPath()) << " to "
                  << QuotePath(output_file_path);
      EraseFiles(output_files);
      EraseFiles(files);
      return false;
    }

    if (!file->Erase(/*unlink=*/true)) {
      PLOG(ERROR) << "Failed to erase " << QuotePath(file->GetPath());
      EraseFiles(output_files);
      EraseFiles(files);
      return false;
    }

    if (output_files.back()->FlushCloseOrErase() != 0) {
      PLOG(ERROR) << "Failed to flush and close file " << QuotePath(output_file_path);
      EraseFiles(output_files);
      EraseFiles(files);
      return false;
    }
  }
  return true;
}

Result<std::string> CreateStagingDirectory() {
  std::string staging_dir = GetArtApexData() + "/staging";

  std::error_code ec;
  if (std::filesystem::exists(staging_dir, ec)) {
    if (!std::filesystem::remove_all(staging_dir, ec)) {
      return Errorf(
          "Could not remove existing staging directory '{}': {}", staging_dir, ec.message());
    }
  }

  if (mkdir(staging_dir.c_str(), S_IRWXU) != 0) {
    return ErrnoErrorf("Could not create staging directory '{}'", staging_dir);
  }

  if (setfilecon(staging_dir.c_str(), "u:object_r:apex_art_staging_data_file:s0") != 0) {
    return ErrnoErrorf("Could not set label on staging directory '{}'", staging_dir);
  }

  return staging_dir;
}

// Gets the `ApexInfo` associated with the currently active ART APEX.
std::optional<apex::ApexInfo> GetArtApexInfo(const std::vector<apex::ApexInfo>& info_list) {
  auto it = std::find_if(info_list.begin(), info_list.end(), [](const apex::ApexInfo& info) {
    return info.getModuleName() == "com.android.art";
  });
  return it != info_list.end() ? std::make_optional(*it) : std::nullopt;
}

// Returns cache provenance information based on the current APEX version and filesystem
// information.
art_apex::ModuleInfo GenerateModuleInfo(const apex::ApexInfo& apex_info) {
  // The lastUpdateMillis is an addition to ApexInfoList.xsd to support samegrade installs.
  int64_t last_update_millis =
      apex_info.hasLastUpdateMillis() ? apex_info.getLastUpdateMillis() : 0;
  return art_apex::ModuleInfo{apex_info.getModuleName(),
                              apex_info.getVersionCode(),
                              apex_info.getVersionName(),
                              last_update_millis};
}

// Returns cache provenance information for all APEXes.
std::vector<art_apex::ModuleInfo> GenerateModuleInfoList(
    const std::vector<apex::ApexInfo>& apex_info_list) {
  std::vector<art_apex::ModuleInfo> module_info_list;
  std::transform(apex_info_list.begin(),
                 apex_info_list.end(),
                 std::back_inserter(module_info_list),
                 GenerateModuleInfo);
  return module_info_list;
}

// Returns a rewritten path based on environment variables for interesting paths.
std::string RewriteParentDirectoryIfNeeded(const std::string& path) {
  if (StartsWith(path, "/system/")) {
    return GetAndroidRoot() + path.substr(7);
  } else if (StartsWith(path, "/system_ext/")) {
    return GetSystemExtRoot() + path.substr(11);
  } else {
    return path;
  }
}

template <typename T>
Result<void> CheckComponents(
    const std::vector<T>& expected_components,
    const std::vector<T>& actual_components,
    const std::function<Result<void>(const T& expected, const T& actual)>& custom_checker =
        [](const T&, const T&) -> Result<void> { return {}; }) {
  if (expected_components.size() != actual_components.size()) {
    return Errorf(
        "Component count differs ({} != {})", expected_components.size(), actual_components.size());
  }

  for (size_t i = 0; i < expected_components.size(); ++i) {
    const T& expected = expected_components[i];
    const T& actual = actual_components[i];

    if (expected.getFile() != actual.getFile()) {
      return Errorf(
          "Component {} file differs ('{}' != '{}')", i, expected.getFile(), actual.getFile());
    }

    if (expected.getSize() != actual.getSize()) {
      return Errorf(
          "Component {} size differs ({} != {})", i, expected.getSize(), actual.getSize());
    }

    if (expected.getChecksums() != actual.getChecksums()) {
      return Errorf("Component {} checksums differ ('{}' != '{}')",
                    i,
                    expected.getChecksums(),
                    actual.getChecksums());
    }

    Result<void> result = custom_checker(expected, actual);
    if (!result.ok()) {
      return Errorf("Component {} {}", i, result.error().message());
    }
  }

  return {};
}

Result<void> CheckSystemServerComponents(
    const std::vector<art_apex::SystemServerComponent>& expected_components,
    const std::vector<art_apex::SystemServerComponent>& actual_components) {
  return CheckComponents<art_apex::SystemServerComponent>(
      expected_components,
      actual_components,
      [](const art_apex::SystemServerComponent& expected,
         const art_apex::SystemServerComponent& actual) -> Result<void> {
        if (expected.getIsInClasspath() != actual.getIsInClasspath()) {
          return Errorf("isInClasspath differs ({} != {})",
                        expected.getIsInClasspath(),
                        actual.getIsInClasspath());
        }

        return {};
      });
}

template <typename T>
std::vector<T> GenerateComponents(
    const std::vector<std::string>& jars,
    const std::function<T(const std::string& path, uint64_t size, const std::string& checksum)>&
        custom_generator) {
  std::vector<T> components;

  for (const std::string& path : jars) {
    std::string actual_path = RewriteParentDirectoryIfNeeded(path);
    struct stat sb;
    if (stat(actual_path.c_str(), &sb) == -1) {
      PLOG(ERROR) << "Failed to stat component: " << QuotePath(actual_path);
      return {};
    }

    std::optional<uint32_t> checksum;
    std::string error_msg;
    ArtDexFileLoader dex_loader(actual_path);
    if (!dex_loader.GetMultiDexChecksum(&checksum, &error_msg)) {
      LOG(ERROR) << "Failed to get multi-dex checksum: " << error_msg;
      return {};
    }

    const std::string checksum_str =
        checksum.has_value() ? StringPrintf("%08x", checksum.value()) : std::string();

    Result<T> component = custom_generator(path, static_cast<uint64_t>(sb.st_size), checksum_str);
    if (!component.ok()) {
      LOG(ERROR) << "Failed to generate component: " << component.error();
      return {};
    }

    components.push_back(*std::move(component));
  }

  return components;
}

std::vector<art_apex::Component> GenerateComponents(const std::vector<std::string>& jars) {
  return GenerateComponents<art_apex::Component>(
      jars, [](const std::string& path, uint64_t size, const std::string& checksum) {
        return art_apex::Component{path, size, checksum};
      });
}

// Checks whether a group of artifacts exists. Returns true if all are present, false otherwise.
// If `checked_artifacts` is present, adds checked artifacts to `checked_artifacts`.
bool ArtifactsExist(const OdrArtifacts& artifacts,
                    bool check_art_file,
                    /*out*/ std::string* error_msg,
                    /*out*/ std::vector<std::string>* checked_artifacts = nullptr) {
  std::vector<const char*> paths{artifacts.OatPath().c_str(), artifacts.VdexPath().c_str()};
  if (check_art_file) {
    paths.push_back(artifacts.ImagePath().c_str());
  }
  for (const char* path : paths) {
    if (!OS::FileExists(path)) {
      if (errno == EACCES) {
        PLOG(ERROR) << "Failed to stat() " << path;
      }
      *error_msg = "Missing file: " + QuotePath(path);
      return false;
    }
  }
  // This should be done after checking all artifacts because either all of them are valid or none
  // of them is valid.
  if (checked_artifacts != nullptr) {
    for (const char* path : paths) {
      checked_artifacts->emplace_back(path);
    }
  }
  return true;
}

void AddDex2OatCommonOptions(/*inout*/ CmdlineBuilder& args) {
  args.Add("--android-root=out/empty");
  args.Add("--abort-on-hard-verifier-error");
  args.Add("--no-abort-on-soft-verifier-error");
  args.Add("--compilation-reason=boot");
  args.Add("--image-format=lz4");
  args.Add("--force-determinism");
  args.Add("--resolve-startup-const-strings=true");

  // Avoid storing dex2oat cmdline in oat header. We want to be sure that the compiled artifacts
  // are identical regardless of where the compilation happened. But some of the cmdline flags tends
  // to be unstable, e.g. those contains FD numbers. To avoid the problem, the whole cmdline is not
  // added to the oat header.
  args.Add("--avoid-storing-invocation");
}

bool IsCpuSetSpecValid(const std::string& cpu_set) {
  for (const std::string& str : Split(cpu_set, ",")) {
    int id;
    if (!ParseInt(str, &id, 0)) {
      return false;
    }
  }
  return true;
}

Result<void> AddDex2OatConcurrencyArguments(/*inout*/ CmdlineBuilder& args,
                                            bool is_compilation_os,
                                            const OdrSystemProperties& system_properties) {
  std::string threads;
  if (is_compilation_os) {
    threads = system_properties.GetOrEmpty("dalvik.vm.background-dex2oat-threads",
                                           "dalvik.vm.dex2oat-threads");
  } else {
    threads = system_properties.GetOrEmpty("dalvik.vm.boot-dex2oat-threads");
  }
  args.AddIfNonEmpty("-j%s", threads);

  std::string cpu_set;
  if (is_compilation_os) {
    cpu_set = system_properties.GetOrEmpty("dalvik.vm.background-dex2oat-cpu-set",
                                           "dalvik.vm.dex2oat-cpu-set");
  } else {
    cpu_set = system_properties.GetOrEmpty("dalvik.vm.boot-dex2oat-cpu-set");
  }
  if (!cpu_set.empty()) {
    if (!IsCpuSetSpecValid(cpu_set)) {
      return Errorf("Invalid CPU set spec '{}'", cpu_set);
    }
    args.Add("--cpu-set=%s", cpu_set);
  }

  return {};
}

void AddDex2OatDebugInfo(/*inout*/ CmdlineBuilder& args) {
  args.Add("--generate-mini-debug-info");
  args.Add("--strip");
}

void AddDex2OatInstructionSet(/*inout*/ CmdlineBuilder& args,
                              InstructionSet isa,
                              const OdrSystemProperties& system_properties) {
  const char* isa_str = GetInstructionSetString(isa);
  args.Add("--instruction-set=%s", isa_str);
  std::string features_prop = ART_FORMAT("dalvik.vm.isa.{}.features", isa_str);
  args.AddIfNonEmpty("--instruction-set-features=%s", system_properties.GetOrEmpty(features_prop));
  std::string variant_prop = ART_FORMAT("dalvik.vm.isa.{}.variant", isa_str);
  args.AddIfNonEmpty("--instruction-set-variant=%s", system_properties.GetOrEmpty(variant_prop));
}

// Returns true if any profile has been added, or false if no profile exists, or error if any error
// occurred.
Result<bool> AddDex2OatProfile(
    /*inout*/ CmdlineBuilder& args,
    /*inout*/ std::vector<std::unique_ptr<File>>& output_files,
    const std::vector<std::string>& profile_paths) {
  bool has_any_profile = false;
  for (const std::string& path : profile_paths) {
    std::unique_ptr<File> profile_file(OS::OpenFileForReading(path.c_str()));
    if (profile_file != nullptr) {
      args.Add("--profile-file-fd=%d", profile_file->Fd());
      output_files.emplace_back(std::move(profile_file));
      has_any_profile = true;
    } else if (errno != ENOENT) {
      return ErrnoErrorf("Failed to open profile file '{}'", path);
    }
  }
  return has_any_profile;
}

Result<void> AddBootClasspathFds(/*inout*/ CmdlineBuilder& args,
                                 /*inout*/ std::vector<std::unique_ptr<File>>& output_files,
                                 const std::vector<std::string>& bcp_jars) {
  std::vector<std::string> bcp_fds;
  for (const std::string& jar : bcp_jars) {
    // Special treatment for Compilation OS. JARs in staged APEX may not be visible to Android, and
    // may only be visible in the VM where the staged APEX is mounted. On the contrary, JARs in
    // /system is not available by path in the VM, and can only made available via (remote) FDs.
    if (StartsWith(jar, "/apex/")) {
      bcp_fds.emplace_back("-1");
    } else {
      std::string actual_path = RewriteParentDirectoryIfNeeded(jar);
      std::unique_ptr<File> jar_file(OS::OpenFileForReading(actual_path.c_str()));
      if (jar_file == nullptr) {
        return ErrnoErrorf("Failed to open a BCP jar '{}'", actual_path);
      }
      bcp_fds.push_back(std::to_string(jar_file->Fd()));
      output_files.push_back(std::move(jar_file));
    }
  }
  args.AddRuntime("-Xbootclasspathfds:%s", Join(bcp_fds, ':'));
  return {};
}

Result<void> AddCacheInfoFd(/*inout*/ CmdlineBuilder& args,
                            /*inout*/ std::vector<std::unique_ptr<File>>& readonly_files_raii,
                            const std::string& cache_info_filename) {
  std::unique_ptr<File> cache_info_file(OS::OpenFileForReading(cache_info_filename.c_str()));
  if (cache_info_file == nullptr) {
    return ErrnoErrorf("Failed to open a cache info file '{}'", cache_info_filename);
  }

  args.Add("--cache-info-fd=%d", cache_info_file->Fd());
  readonly_files_raii.push_back(std::move(cache_info_file));
  return {};
}

std::string GetBootImageComponentBasename(const std::string& jar_path, bool is_first_jar) {
  if (is_first_jar) {
    return kFirstBootImageBasename;
  }
  std::string jar_name = Basename(jar_path);
  return "boot-" + ReplaceFileExtension(jar_name, "art");
}

Result<void> AddCompiledBootClasspathFdsIfAny(
    /*inout*/ CmdlineBuilder& args,
    /*inout*/ std::vector<std::unique_ptr<File>>& output_files,
    const std::vector<std::string>& bcp_jars,
    InstructionSet isa,
    const std::vector<std::string>& boot_image_locations) {
  std::vector<std::string> bcp_image_fds;
  std::vector<std::string> bcp_oat_fds;
  std::vector<std::string> bcp_vdex_fds;
  std::vector<std::unique_ptr<File>> opened_files;
  bool added_any = false;
  std::string artifact_dir;
  for (size_t i = 0; i < bcp_jars.size(); i++) {
    const std::string& jar = bcp_jars[i];
    std::string basename = GetBootImageComponentBasename(jar, /*is_first_jar=*/i == 0);
    // If there is an entry in `boot_image_locations` for the current jar, update `artifact_dir` for
    // the current jar and the subsequent jars.
    for (const std::string& location : boot_image_locations) {
      if (Basename(location) == basename) {
        artifact_dir = Dirname(location);
        break;
      }
    }
    CHECK(!artifact_dir.empty());
    std::string image_path = ART_FORMAT("{}/{}", artifact_dir, basename);
    image_path = GetSystemImageFilename(image_path.c_str(), isa);
    std::unique_ptr<File> image_file(OS::OpenFileForReading(image_path.c_str()));
    if (image_file != nullptr) {
      bcp_image_fds.push_back(std::to_string(image_file->Fd()));
      opened_files.push_back(std::move(image_file));
      added_any = true;
    } else if (errno == ENOENT) {
      bcp_image_fds.push_back("-1");
    } else {
      return ErrnoErrorf("Failed to open boot image file '{}'", image_path);
    }

    std::string oat_path = ReplaceFileExtension(image_path, "oat");
    std::unique_ptr<File> oat_file(OS::OpenFileForReading(oat_path.c_str()));
    if (oat_file != nullptr) {
      bcp_oat_fds.push_back(std::to_string(oat_file->Fd()));
      opened_files.push_back(std::move(oat_file));
      added_any = true;
    } else if (errno == ENOENT) {
      bcp_oat_fds.push_back("-1");
    } else {
      return ErrnoErrorf("Failed to open boot image file '{}'", oat_path);
    }

    std::string vdex_path = ReplaceFileExtension(image_path, "vdex");
    std::unique_ptr<File> vdex_file(OS::OpenFileForReading(vdex_path.c_str()));
    if (vdex_file != nullptr) {
      bcp_vdex_fds.push_back(std::to_string(vdex_file->Fd()));
      opened_files.push_back(std::move(vdex_file));
      added_any = true;
    } else if (errno == ENOENT) {
      bcp_vdex_fds.push_back("-1");
    } else {
      return ErrnoErrorf("Failed to open boot image file '{}'", vdex_path);
    }
  }
  // Add same amount of FDs as BCP JARs, or none.
  if (added_any) {
    std::move(opened_files.begin(), opened_files.end(), std::back_inserter(output_files));

    args.AddRuntime("-Xbootclasspathimagefds:%s", Join(bcp_image_fds, ':'));
    args.AddRuntime("-Xbootclasspathoatfds:%s", Join(bcp_oat_fds, ':'));
    args.AddRuntime("-Xbootclasspathvdexfds:%s", Join(bcp_vdex_fds, ':'));
  }

  return {};
}

std::string GetStagingLocation(const std::string& staging_dir, const std::string& path) {
  return staging_dir + "/" + Basename(path);
}

WARN_UNUSED bool CheckCompilationSpace() {
  // Check the available storage space against an arbitrary threshold because dex2oat does not
  // report when it runs out of storage space and we do not want to completely fill
  // the users data partition.
  //
  // We do not have a good way of pre-computing the required space for a compilation step, but
  // typically observe no more than 48MiB as the largest total size of AOT artifacts for a single
  // dex2oat invocation, which includes an image file, an executable file, and a verification data
  // file.
  static constexpr uint64_t kMinimumSpaceForCompilation = 48 * 1024 * 1024;

  uint64_t bytes_available;
  const std::string& art_apex_data_path = GetArtApexData();
  if (!GetFreeSpace(art_apex_data_path, &bytes_available)) {
    return false;
  }

  if (bytes_available < kMinimumSpaceForCompilation) {
    LOG(WARNING) << "Low space for " << QuotePath(art_apex_data_path) << " (" << bytes_available
                 << " bytes)";
    return false;
  }

  return true;
}

bool HasVettedDeviceSystemServerProfiles() {
  // While system_server profiles were bundled on the device prior to U+, they were not used by
  // default or rigorously tested, so we cannot vouch for their efficacy.
  static const bool kDeviceIsAtLeastU = IsAtLeastU();
  return kDeviceIsAtLeastU;
}

}  // namespace

CompilationOptions CompilationOptions::CompileAll(const OnDeviceRefresh& odr) {
  CompilationOptions options;
  for (InstructionSet isa : odr.Config().GetBootClasspathIsas()) {
    options.boot_images_to_generate_for_isas.emplace_back(
        isa, BootImages{.primary_boot_image = true, .boot_image_mainline_extension = true});
  }
  options.system_server_jars_to_compile = odr.AllSystemServerJars();
  return options;
}

int BootImages::Count() const {
  int count = 0;
  if (primary_boot_image) {
    count++;
  }
  if (boot_image_mainline_extension) {
    count++;
  }
  return count;
}

OdrMetrics::BcpCompilationType BootImages::GetTypeForMetrics() const {
  if (primary_boot_image && boot_image_mainline_extension) {
    return OdrMetrics::BcpCompilationType::kPrimaryAndMainline;
  }
  if (boot_image_mainline_extension) {
    return OdrMetrics::BcpCompilationType::kMainline;
  }
  LOG(FATAL) << "Unexpected BCP compilation type";
  UNREACHABLE();
}

int CompilationOptions::CompilationUnitCount() const {
  int count = 0;
  for (const auto& [isa, boot_images] : boot_images_to_generate_for_isas) {
    count += boot_images.Count();
  }
  count += system_server_jars_to_compile.size();
  return count;
}

OnDeviceRefresh::OnDeviceRefresh(const OdrConfig& config)
    : OnDeviceRefresh(config,
                      config.GetArtifactDirectory() + "/" + kCacheInfoFile,
                      std::make_unique<ExecUtils>(),
                      CheckCompilationSpace) {}

OnDeviceRefresh::OnDeviceRefresh(const OdrConfig& config,
                                 const std::string& cache_info_filename,
                                 std::unique_ptr<ExecUtils> exec_utils,
                                 android::base::function_ref<bool()> check_compilation_space)
    : config_(config),
      cache_info_filename_(cache_info_filename),
      start_time_(time(nullptr)),
      exec_utils_(std::move(exec_utils)),
      check_compilation_space_(check_compilation_space) {
  // Updatable APEXes should not have DEX files in the DEX2OATBOOTCLASSPATH. At the time of
  // writing i18n is a non-updatable APEX and so does appear in the DEX2OATBOOTCLASSPATH.
  dex2oat_boot_classpath_jars_ = Split(config_.GetDex2oatBootClasspath(), ":");

  all_systemserver_jars_ = Split(config_.GetSystemServerClasspath(), ":");
  systemserver_classpath_jars_ = {all_systemserver_jars_.begin(), all_systemserver_jars_.end()};
  boot_classpath_jars_ = Split(config_.GetBootClasspath(), ":");
  std::string standalone_system_server_jars_str = config_.GetStandaloneSystemServerJars();
  if (!standalone_system_server_jars_str.empty()) {
    std::vector<std::string> standalone_systemserver_jars =
        Split(standalone_system_server_jars_str, ":");
    std::move(standalone_systemserver_jars.begin(),
              standalone_systemserver_jars.end(),
              std::back_inserter(all_systemserver_jars_));
  }
}

time_t OnDeviceRefresh::GetExecutionTimeUsed() const { return time(nullptr) - start_time_; }

time_t OnDeviceRefresh::GetExecutionTimeRemaining() const {
  return std::max(static_cast<time_t>(0),
                  kMaximumExecutionSeconds - GetExecutionTimeUsed());
}

time_t OnDeviceRefresh::GetSubprocessTimeout() const {
  return std::min(GetExecutionTimeRemaining(), kMaxChildProcessSeconds);
}

std::optional<std::vector<apex::ApexInfo>> OnDeviceRefresh::GetApexInfoList() const {
  std::optional<apex::ApexInfoList> info_list =
      apex::readApexInfoList(config_.GetApexInfoListFile().c_str());
  if (!info_list.has_value()) {
    return std::nullopt;
  }

  // We are only interested in active APEXes that contain compilable JARs.
  std::unordered_set<std::string_view> relevant_apexes;
  relevant_apexes.reserve(info_list->getApexInfo().size());
  for (const std::vector<std::string>* jar_list :
       {&all_systemserver_jars_, &boot_classpath_jars_}) {
    for (const std::string& jar : *jar_list) {
      std::string_view apex = ApexNameFromLocation(jar);
      if (!apex.empty()) {
        relevant_apexes.insert(apex);
      }
    }
  }
  // The ART APEX is always relevant no matter it contains any compilable JAR or not, because it
  // contains the runtime.
  relevant_apexes.insert("com.android.art");

  std::vector<apex::ApexInfo> filtered_info_list;
  std::copy_if(info_list->getApexInfo().begin(),
               info_list->getApexInfo().end(),
               std::back_inserter(filtered_info_list),
               [&](const apex::ApexInfo& info) {
                 return info.getIsActive() && relevant_apexes.count(info.getModuleName()) != 0;
               });
  return filtered_info_list;
}

Result<art_apex::CacheInfo> OnDeviceRefresh::ReadCacheInfo() const {
  std::optional<art_apex::CacheInfo> cache_info = art_apex::read(cache_info_filename_.c_str());
  if (!cache_info.has_value()) {
    if (errno != 0) {
      return ErrnoErrorf("Failed to load {}", QuotePath(cache_info_filename_));
    } else {
      return Errorf("Failed to parse {}", QuotePath(cache_info_filename_));
    }
  }
  return cache_info.value();
}

Result<void> OnDeviceRefresh::WriteCacheInfo() const {
  if (OS::FileExists(cache_info_filename_.c_str())) {
    if (unlink(cache_info_filename_.c_str()) != 0) {
      return ErrnoErrorf("Failed to unlink file {}", QuotePath(cache_info_filename_));
    }
  }

  std::string dir_name = Dirname(cache_info_filename_);
  if (!EnsureDirectoryExists(dir_name)) {
    return Errorf("Could not create directory {}", QuotePath(dir_name));
  }

  std::vector<art_apex::KeyValuePair> system_properties;
  for (const auto& [key, value] : config_.GetSystemProperties()) {
    if (!art::ContainsElement(kIgnoredSystemProperties, key)) {
      system_properties.emplace_back(key, value);
    }
  }

  std::optional<std::vector<apex::ApexInfo>> apex_info_list = GetApexInfoList();
  if (!apex_info_list.has_value()) {
    return Errorf("Could not update {}: no APEX info", QuotePath(cache_info_filename_));
  }

  std::optional<apex::ApexInfo> art_apex_info = GetArtApexInfo(apex_info_list.value());
  if (!art_apex_info.has_value()) {
    return Errorf("Could not update {}: no ART APEX info", QuotePath(cache_info_filename_));
  }

  art_apex::ModuleInfo art_module_info = GenerateModuleInfo(art_apex_info.value());
  std::vector<art_apex::ModuleInfo> module_info_list =
      GenerateModuleInfoList(apex_info_list.value());

  std::vector<art_apex::Component> bcp_components = GenerateBootClasspathComponents();
  std::vector<art_apex::Component> dex2oat_bcp_components =
      GenerateDex2oatBootClasspathComponents();
  std::vector<art_apex::SystemServerComponent> system_server_components =
      GenerateSystemServerComponents();

  std::ofstream out(cache_info_filename_.c_str());
  if (out.fail()) {
    return ErrnoErrorf("Could not create cache info file {}", QuotePath(cache_info_filename_));
  }

  std::unique_ptr<art_apex::CacheInfo> info(new art_apex::CacheInfo(
      {art_apex::KeyValuePairList(system_properties)},
      {art_module_info},
      {art_apex::ModuleInfoList(module_info_list)},
      {art_apex::Classpath(bcp_components)},
      {art_apex::Classpath(dex2oat_bcp_components)},
      {art_apex::SystemServerComponents(system_server_components)},
      config_.GetCompilationOsMode() ? std::make_optional(true) : std::nullopt));

  art_apex::write(out, *info);
  out.close();
  if (out.fail()) {
    return ErrnoErrorf("Could not write cache info file {}", QuotePath(cache_info_filename_));
  }

  return {};
}

static void ReportNextBootAnimationProgress(uint32_t current_compilation,
                                            uint32_t number_of_compilations) {
  // We arbitrarily show progress until 90%, expecting that our compilations take a large chunk of
  // boot time.
  uint32_t value = (90 * current_compilation) / number_of_compilations;
  SetProperty("service.bootanim.progress", std::to_string(value));
}

std::vector<art_apex::Component> OnDeviceRefresh::GenerateBootClasspathComponents() const {
  return GenerateComponents(boot_classpath_jars_);
}

std::vector<art_apex::Component> OnDeviceRefresh::GenerateDex2oatBootClasspathComponents() const {
  return GenerateComponents(dex2oat_boot_classpath_jars_);
}

std::vector<art_apex::SystemServerComponent> OnDeviceRefresh::GenerateSystemServerComponents()
    const {
  return GenerateComponents<art_apex::SystemServerComponent>(
      all_systemserver_jars_,
      [&](const std::string& path, uint64_t size, const std::string& checksum) {
        bool isInClasspath = ContainsElement(systemserver_classpath_jars_, path);
        return art_apex::SystemServerComponent{path, size, checksum, isInClasspath};
      });
}

std::vector<std::string> OnDeviceRefresh::GetArtBcpJars() const {
  std::string art_root = GetArtRoot() + "/";
  std::vector<std::string> art_bcp_jars;
  for (const std::string& jar : dex2oat_boot_classpath_jars_) {
    if (StartsWith(jar, art_root)) {
      art_bcp_jars.push_back(jar);
    }
  }
  CHECK(!art_bcp_jars.empty());
  return art_bcp_jars;
}

std::vector<std::string> OnDeviceRefresh::GetFrameworkBcpJars() const {
  std::string art_root = GetArtRoot() + "/";
  std::vector<std::string> framework_bcp_jars;
  for (const std::string& jar : dex2oat_boot_classpath_jars_) {
    if (!StartsWith(jar, art_root)) {
      framework_bcp_jars.push_back(jar);
    }
  }
  CHECK(!framework_bcp_jars.empty());
  return framework_bcp_jars;
}

std::vector<std::string> OnDeviceRefresh::GetMainlineBcpJars() const {
  // Elements in `dex2oat_boot_classpath_jars_` should be at the beginning of
  // `boot_classpath_jars_`, followed by mainline BCP jars.
  CHECK_LT(dex2oat_boot_classpath_jars_.size(), boot_classpath_jars_.size());
  CHECK(std::equal(dex2oat_boot_classpath_jars_.begin(),
                   dex2oat_boot_classpath_jars_.end(),
                   boot_classpath_jars_.begin(),
                   boot_classpath_jars_.begin() + dex2oat_boot_classpath_jars_.size()));
  return {boot_classpath_jars_.begin() + dex2oat_boot_classpath_jars_.size(),
          boot_classpath_jars_.end()};
}

std::string OnDeviceRefresh::GetPrimaryBootImage(bool on_system, bool minimal) const {
  DCHECK(!on_system || !minimal);
  const char* basename = minimal ? kMinimalBootImageBasename : kFirstBootImageBasename;
  if (on_system) {
    // Typically "/system/framework/boot.art".
    return GetPrebuiltPrimaryBootImageDir() + "/" + basename;
  } else {
    // Typically "/data/misc/apexdata/com.android.art/dalvik-cache/boot.art".
    return config_.GetArtifactDirectory() + "/" + basename;
  }
}

std::string OnDeviceRefresh::GetPrimaryBootImagePath(bool on_system,
                                                     bool minimal,
                                                     InstructionSet isa) const {
  // Typically "/data/misc/apexdata/com.android.art/dalvik-cache/<isa>/boot.art".
  return GetSystemImageFilename(GetPrimaryBootImage(on_system, minimal).c_str(), isa);
}

std::string OnDeviceRefresh::GetSystemBootImageFrameworkExtension() const {
  std::vector<std::string> framework_bcp_jars = GetFrameworkBcpJars();
  std::string basename =
      GetBootImageComponentBasename(framework_bcp_jars[0], /*is_first_jar=*/false);
  // Typically "/system/framework/boot-framework.art".
  return ART_FORMAT("{}/framework/{}", GetAndroidRoot(), basename);
}

std::string OnDeviceRefresh::GetSystemBootImageFrameworkExtensionPath(InstructionSet isa) const {
  // Typically "/system/framework/<isa>/boot-framework.art".
  return GetSystemImageFilename(GetSystemBootImageFrameworkExtension().c_str(), isa);
}

std::string OnDeviceRefresh::GetBootImageMainlineExtension(bool on_system) const {
  std::vector<std::string> mainline_bcp_jars = GetMainlineBcpJars();
  std::string basename =
      GetBootImageComponentBasename(mainline_bcp_jars[0], /*is_first_jar=*/false);
  if (on_system) {
    // Typically "/system/framework/boot-framework-adservices.art".
    return ART_FORMAT("{}/framework/{}", GetAndroidRoot(), basename);
  } else {
    // Typically "/data/misc/apexdata/com.android.art/dalvik-cache/boot-framework-adservices.art".
    return ART_FORMAT("{}/{}", config_.GetArtifactDirectory(), basename);
  }
}

std::string OnDeviceRefresh::GetBootImageMainlineExtensionPath(bool on_system,
                                                               InstructionSet isa) const {
  // Typically
  // "/data/misc/apexdata/com.android.art/dalvik-cache/<isa>/boot-framework-adservices.art".
  return GetSystemImageFilename(GetBootImageMainlineExtension(on_system).c_str(), isa);
}

std::vector<std::string> OnDeviceRefresh::GetBestBootImages(InstructionSet isa,
                                                            bool include_mainline_extension) const {
  std::vector<std::string> locations;
  std::string unused_error_msg;
  bool primary_on_data = false;
  if (PrimaryBootImageExist(
          /*on_system=*/false, /*minimal=*/false, isa, &unused_error_msg)) {
    primary_on_data = true;
    locations.push_back(GetPrimaryBootImage(/*on_system=*/false, /*minimal=*/false));
  } else {
    locations.push_back(GetPrimaryBootImage(/*on_system=*/true, /*minimal=*/false));
    if (!IsAtLeastU()) {
      // Prior to U, there was a framework extension.
      locations.push_back(GetSystemBootImageFrameworkExtension());
    }
  }
  if (include_mainline_extension) {
    if (BootImageMainlineExtensionExist(/*on_system=*/false, isa, &unused_error_msg)) {
      locations.push_back(GetBootImageMainlineExtension(/*on_system=*/false));
    } else {
      // If the primary boot image is on /data, it means we have regenerated all boot images, so the
      // mainline extension must be on /data too.
      CHECK(!primary_on_data)
          << "Mainline extension not found while primary boot image is on /data";
      locations.push_back(GetBootImageMainlineExtension(/*on_system=*/true));
    }
  }
  return locations;
}

std::string OnDeviceRefresh::GetSystemServerImagePath(bool on_system,
                                                      const std::string& jar_path) const {
  if (on_system) {
    if (LocationIsOnApex(jar_path)) {
      return GetSystemOdexFilenameForApex(jar_path, config_.GetSystemServerIsa());
    }
    std::string jar_name = Basename(jar_path);
    std::string image_name = ReplaceFileExtension(jar_name, "art");
    const char* isa_str = GetInstructionSetString(config_.GetSystemServerIsa());
    // Typically "/system/framework/oat/<isa>/services.art".
    return ART_FORMAT("{}/oat/{}/{}", Dirname(jar_path), isa_str, image_name);
  } else {
    // Typically
    // "/data/misc/apexdata/.../dalvik-cache/<isa>/system@framework@services.jar@classes.art".
    const std::string image = GetApexDataImage(jar_path);
    return GetSystemImageFilename(image.c_str(), config_.GetSystemServerIsa());
  }
}

WARN_UNUSED bool OnDeviceRefresh::RemoveArtifactsDirectory() const {
  if (config_.GetDryRun()) {
    LOG(INFO) << "Directory " << QuotePath(config_.GetArtifactDirectory())
              << " and contents would be removed (dry-run).";
    return true;
  }
  return RemoveDirectory(config_.GetArtifactDirectory());
}

WARN_UNUSED bool OnDeviceRefresh::PrimaryBootImageExist(
    bool on_system,
    bool minimal,
    InstructionSet isa,
    /*out*/ std::string* error_msg,
    /*out*/ std::vector<std::string>* checked_artifacts) const {
  std::string path = GetPrimaryBootImagePath(on_system, minimal, isa);
  OdrArtifacts artifacts = OdrArtifacts::ForBootImage(path);
  if (!ArtifactsExist(artifacts, /*check_art_file=*/true, error_msg, checked_artifacts)) {
    return false;
  }
  // Prior to U, there was a split between the primary boot image and the extension on /system, so
  // they need to be checked separately. This does not apply to the boot image on /data.
  if (on_system && !IsAtLeastU()) {
    std::string extension_path = GetSystemBootImageFrameworkExtensionPath(isa);
    OdrArtifacts extension_artifacts = OdrArtifacts::ForBootImage(extension_path);
    if (!ArtifactsExist(
            extension_artifacts, /*check_art_file=*/true, error_msg, checked_artifacts)) {
      return false;
    }
  }
  return true;
}

WARN_UNUSED bool OnDeviceRefresh::BootImageMainlineExtensionExist(
    bool on_system,
    InstructionSet isa,
    /*out*/ std::string* error_msg,
    /*out*/ std::vector<std::string>* checked_artifacts) const {
  std::string path = GetBootImageMainlineExtensionPath(on_system, isa);
  OdrArtifacts artifacts = OdrArtifacts::ForBootImage(path);
  return ArtifactsExist(artifacts, /*check_art_file=*/true, error_msg, checked_artifacts);
}

bool OnDeviceRefresh::SystemServerArtifactsExist(
    bool on_system,
    /*out*/ std::string* error_msg,
    /*out*/ std::set<std::string>* jars_missing_artifacts,
    /*out*/ std::vector<std::string>* checked_artifacts) const {
  for (const std::string& jar_path : all_systemserver_jars_) {
    const std::string image_location = GetSystemServerImagePath(on_system, jar_path);
    const OdrArtifacts artifacts = OdrArtifacts::ForSystemServer(image_location);
    // .art files are optional and are not generated for all jars by the build system.
    const bool check_art_file = !on_system;
    std::string error_msg_tmp;
    if (!ArtifactsExist(artifacts, check_art_file, &error_msg_tmp, checked_artifacts)) {
      jars_missing_artifacts->insert(jar_path);
      *error_msg = error_msg->empty() ? error_msg_tmp : *error_msg + "\n" + error_msg_tmp;
    }
  }
  return jars_missing_artifacts->empty();
}

WARN_UNUSED bool OnDeviceRefresh::CheckSystemPropertiesAreDefault() const {
  // We don't have to check properties that match `kCheckedSystemPropertyPrefixes` here because none
  // of them is persistent. This only applies when `cache-info.xml` does not exist. When
  // `cache-info.xml` exists, we call `CheckSystemPropertiesHaveNotChanged` instead.
  DCHECK(std::none_of(std::begin(kCheckedSystemPropertyPrefixes),
                      std::end(kCheckedSystemPropertyPrefixes),
                      [](const char* prefix) { return StartsWith(prefix, "persist."); }));

  const OdrSystemProperties& system_properties = config_.GetSystemProperties();

  for (const SystemPropertyConfig& system_property_config : *kSystemProperties.get()) {
    std::string property = system_properties.GetOrEmpty(system_property_config.name);
    DCHECK_NE(property, "");

    if (property != system_property_config.default_value) {
      LOG(INFO) << "System property " << system_property_config.name << " has a non-default value ("
                << property << ").";
      return false;
    }
  }

  return true;
}

WARN_UNUSED bool OnDeviceRefresh::CheckSystemPropertiesHaveNotChanged(
    const art_apex::CacheInfo& cache_info) const {
  std::unordered_map<std::string, std::string> cached_system_properties;
  std::unordered_set<std::string> checked_properties;

  const art_apex::KeyValuePairList* list = cache_info.getFirstSystemProperties();
  if (list == nullptr) {
    // This should never happen. We have already checked the ART module version, and the cache
    // info is generated by the latest version of the ART module if it exists.
    LOG(ERROR) << "Missing system properties from cache-info.";
    return false;
  }

  for (const art_apex::KeyValuePair& pair : list->getItem()) {
    cached_system_properties[pair.getK()] = pair.getV();
    checked_properties.insert(pair.getK());
  }

  const OdrSystemProperties& system_properties = config_.GetSystemProperties();

  for (const auto& [key, value] : system_properties) {
    if (!art::ContainsElement(kIgnoredSystemProperties, key)) {
      checked_properties.insert(key);
    }
  }

  for (const std::string& name : checked_properties) {
    std::string property = system_properties.GetOrEmpty(name);
    std::string cached_property = cached_system_properties[name];

    if (property != cached_property) {
      LOG(INFO) << "System property " << name << " value changed (before: \"" << cached_property
                << "\", now: \"" << property << "\").";
      return false;
    }
  }

  return true;
}

WARN_UNUSED bool OnDeviceRefresh::CheckBuildUserfaultFdGc() const {
  bool build_enable_uffd_gc =
      config_.GetSystemProperties().GetBool("ro.dalvik.vm.enable_uffd_gc", /*default_value=*/false);
  bool is_at_most_u = !IsAtLeastV();
  bool kernel_supports_uffd = KernelSupportsUffd();
  if (!art::odrefresh::CheckBuildUserfaultFdGc(
          build_enable_uffd_gc, is_at_most_u, kernel_supports_uffd)) {
    // Normally, this should not happen. If this happens, the system image was probably built with a
    // wrong PRODUCT_ENABLE_UFFD_GC flag.
    LOG(WARNING) << ART_FORMAT(
        "Userfaultfd GC check failed (build_enable_uffd_gc: {}, is_at_most_u: {}, "
        "kernel_supports_uffd: {}).",
        build_enable_uffd_gc,
        is_at_most_u,
        kernel_supports_uffd);
    return false;
  }
  return true;
}

WARN_UNUSED PreconditionCheckResult OnDeviceRefresh::CheckPreconditionForSystem(
    const std::vector<apex::ApexInfo>& apex_info_list) const {
  if (!CheckSystemPropertiesAreDefault()) {
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  if (!CheckBuildUserfaultFdGc()) {
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  std::optional<apex::ApexInfo> art_apex_info = GetArtApexInfo(apex_info_list);
  if (!art_apex_info.has_value()) {
    // This should never happen, further up-to-date checks are not possible if it does.
    LOG(ERROR) << "Could not get ART APEX info.";
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kUnknown);
  }

  if (!art_apex_info->getIsFactory()) {
    LOG(INFO) << "Updated ART APEX mounted";
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  if (std::any_of(apex_info_list.begin(),
                  apex_info_list.end(),
                  [](const apex::ApexInfo& apex_info) { return !apex_info.getIsFactory(); })) {
    LOG(INFO) << "Updated APEXes mounted";
    return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
        OdrMetrics::Trigger::kApexVersionMismatch);
  }

  return PreconditionCheckResult::AllOk();
}

WARN_UNUSED static bool CheckModuleInfo(const art_apex::ModuleInfo& cached_info,
                                        const apex::ApexInfo& current_info) {
  if (cached_info.getVersionCode() != current_info.getVersionCode()) {
    LOG(INFO) << ART_FORMAT("APEX ({}) version code mismatch (before: {}, now: {})",
                            current_info.getModuleName(),
                            cached_info.getVersionCode(),
                            current_info.getVersionCode());
    return false;
  }

  if (cached_info.getVersionName() != current_info.getVersionName()) {
    LOG(INFO) << ART_FORMAT("APEX ({}) version name mismatch (before: {}, now: {})",
                            current_info.getModuleName(),
                            cached_info.getVersionName(),
                            current_info.getVersionName());
    return false;
  }

  // Check lastUpdateMillis for samegrade installs. If `cached_info` is missing the lastUpdateMillis
  // field then it is not current with the schema used by this binary so treat it as a samegrade
  // update. Otherwise check whether the lastUpdateMillis changed.
  const int64_t cached_last_update_millis =
      cached_info.hasLastUpdateMillis() ? cached_info.getLastUpdateMillis() : -1;
  if (cached_last_update_millis != current_info.getLastUpdateMillis()) {
    LOG(INFO) << ART_FORMAT("APEX ({}) last update time mismatch (before: {}, now: {})",
                            current_info.getModuleName(),
                            cached_info.getLastUpdateMillis(),
                            current_info.getLastUpdateMillis());
    return false;
  }

  return true;
}

WARN_UNUSED PreconditionCheckResult OnDeviceRefresh::CheckPreconditionForData(
    const std::vector<com::android::apex::ApexInfo>& apex_info_list) const {
  Result<art_apex::CacheInfo> cache_info = ReadCacheInfo();
  if (!cache_info.ok()) {
    if (cache_info.error().code() == ENOENT) {
      // If the cache info file does not exist, it usually means it's the first boot, or the
      // dalvik-cache directory is cleared by odsign due to corrupted files. Set the trigger to be
      // `kApexVersionMismatch` to force generate the cache info file and compile if necessary.
      LOG(INFO) << "No prior cache-info file: " << QuotePath(cache_info_filename_);
    } else {
      // This should not happen unless odrefresh is updated to a new version that is not compatible
      // with an old cache-info file. Further up-to-date checks are not possible if it does.
      LOG(ERROR) << cache_info.error().message();
    }
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  if (!CheckSystemPropertiesHaveNotChanged(cache_info.value())) {
    // We don't have a trigger kind for system property changes. For now, we reuse
    // `kApexVersionMismatch` as it implies the expected behavior: re-compile regardless of the last
    // compilation attempt.
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  // Check whether the current cache ART module info differs from the current ART module info.
  const art_apex::ModuleInfo* cached_art_info = cache_info->getFirstArtModuleInfo();
  if (cached_art_info == nullptr) {
    LOG(ERROR) << "Missing ART APEX info from cache-info.";
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  std::optional<apex::ApexInfo> current_art_info = GetArtApexInfo(apex_info_list);
  if (!current_art_info.has_value()) {
    // This should never happen, further up-to-date checks are not possible if it does.
    LOG(ERROR) << "Could not get ART APEX info.";
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kUnknown);
  }

  if (!CheckModuleInfo(*cached_art_info, *current_art_info)) {
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  // Check boot class components.
  //
  // This checks the size and checksums of odrefresh compilable files on the DEX2OATBOOTCLASSPATH
  // (the Odrefresh constructor determines which files are compilable). If the number of files
  // there changes, or their size or checksums change then compilation will be triggered.
  //
  // The boot class components may change unexpectedly, for example an OTA could update
  // framework.jar.
  const std::vector<art_apex::Component> current_dex2oat_bcp_components =
      GenerateDex2oatBootClasspathComponents();

  const art_apex::Classpath* cached_dex2oat_bcp_components =
      cache_info->getFirstDex2oatBootClasspath();
  if (cached_dex2oat_bcp_components == nullptr) {
    LOG(INFO) << "Missing Dex2oatBootClasspath components.";
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  Result<void> result = CheckComponents(current_dex2oat_bcp_components,
                                        cached_dex2oat_bcp_components->getComponent());
  if (!result.ok()) {
    LOG(INFO) << "Dex2OatClasspath components mismatch: " << result.error();
    return PreconditionCheckResult::NoneOk(OdrMetrics::Trigger::kDexFilesChanged);
  }

  // Check whether the current cached module info differs from the current module info.
  const art_apex::ModuleInfoList* cached_module_info_list = cache_info->getFirstModuleInfoList();
  if (cached_module_info_list == nullptr) {
    LOG(ERROR) << "Missing APEX info list from cache-info.";
    return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
        OdrMetrics::Trigger::kApexVersionMismatch);
  }

  std::unordered_map<std::string, const art_apex::ModuleInfo*> cached_module_info_map;
  for (const art_apex::ModuleInfo& module_info : cached_module_info_list->getModuleInfo()) {
    cached_module_info_map[module_info.getName()] = &module_info;
  }

  // Note that apex_info_list may omit APEXes that are included in cached_module_info - e.g. if an
  // apex used to be compilable, but now isn't. That won't be detected by this loop, but will be
  // detected below in CheckComponents.
  for (const apex::ApexInfo& current_apex_info : apex_info_list) {
    auto& apex_name = current_apex_info.getModuleName();

    auto it = cached_module_info_map.find(apex_name);
    if (it == cached_module_info_map.end()) {
      LOG(INFO) << "Missing APEX info from cache-info (" << apex_name << ").";
      return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
          OdrMetrics::Trigger::kApexVersionMismatch);
    }

    const art_apex::ModuleInfo* cached_module_info = it->second;
    if (!CheckModuleInfo(*cached_module_info, current_apex_info)) {
      return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
          OdrMetrics::Trigger::kApexVersionMismatch);
    }
  }

  const std::vector<art_apex::Component> current_bcp_components = GenerateBootClasspathComponents();

  const art_apex::Classpath* cached_bcp_components = cache_info->getFirstBootClasspath();
  if (cached_bcp_components == nullptr) {
    LOG(INFO) << "Missing BootClasspath components.";
    return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
        OdrMetrics::Trigger::kApexVersionMismatch);
  }

  result = CheckComponents(current_bcp_components, cached_bcp_components->getComponent());
  if (!result.ok()) {
    LOG(INFO) << "BootClasspath components mismatch: " << result.error();
    // Boot classpath components can be dependencies of system_server components, so system_server
    // components need to be recompiled if boot classpath components are changed.
    return PreconditionCheckResult::BootImageMainlineExtensionNotOk(
        OdrMetrics::Trigger::kDexFilesChanged);
  }

  // Check system server components.
  //
  // This checks the size and checksums of odrefresh compilable files on the
  // SYSTEMSERVERCLASSPATH (the Odrefresh constructor determines which files are compilable). If
  // the number of files there changes, or their size or checksums change then compilation will be
  // triggered.
  //
  // The system_server components may change unexpectedly, for example an OTA could update
  // services.jar.
  const std::vector<art_apex::SystemServerComponent> current_system_server_components =
      GenerateSystemServerComponents();

  const art_apex::SystemServerComponents* cached_system_server_components =
      cache_info->getFirstSystemServerComponents();
  if (cached_system_server_components == nullptr) {
    LOG(INFO) << "Missing SystemServerComponents.";
    return PreconditionCheckResult::SystemServerNotOk(OdrMetrics::Trigger::kApexVersionMismatch);
  }

  result = CheckSystemServerComponents(current_system_server_components,
                                       cached_system_server_components->getComponent());
  if (!result.ok()) {
    LOG(INFO) << "SystemServerComponents mismatch: " << result.error();
    return PreconditionCheckResult::SystemServerNotOk(OdrMetrics::Trigger::kDexFilesChanged);
  }

  return PreconditionCheckResult::AllOk();
}

WARN_UNUSED BootImages OnDeviceRefresh::CheckBootClasspathArtifactsAreUpToDate(
    OdrMetrics& metrics,
    InstructionSet isa,
    const PreconditionCheckResult& system_result,
    const PreconditionCheckResult& data_result,
    /*out*/ std::vector<std::string>* checked_artifacts) const {
  const char* isa_str = GetInstructionSetString(isa);

  BootImages boot_images_on_system{.primary_boot_image = false,
                                   .boot_image_mainline_extension = false};
  if (system_result.IsPrimaryBootImageOk()) {
    // We can use the artifacts on /system. Check if they exist.
    std::string error_msg;
    if (PrimaryBootImageExist(/*on_system=*/true, /*minimal=*/false, isa, &error_msg)) {
      boot_images_on_system.primary_boot_image = true;
    } else {
      LOG(INFO) << "Incomplete primary boot image or framework extension on /system: " << error_msg;
    }
  }

  if (boot_images_on_system.primary_boot_image && system_result.IsBootImageMainlineExtensionOk()) {
    std::string error_msg;
    if (BootImageMainlineExtensionExist(/*on_system=*/true, isa, &error_msg)) {
      boot_images_on_system.boot_image_mainline_extension = true;
    } else {
      LOG(INFO) << "Incomplete boot image mainline extension on /system: " << error_msg;
    }
  }

  if (boot_images_on_system.Count() == BootImages::kMaxCount) {
    LOG(INFO) << ART_FORMAT("Boot images on /system OK ({})", isa_str);
    // Nothing to compile.
    return BootImages{.primary_boot_image = false, .boot_image_mainline_extension = false};
  }

  LOG(INFO) << ART_FORMAT("Checking boot images /data ({})", isa_str);
  BootImages boot_images_on_data{.primary_boot_image = false,
                                 .boot_image_mainline_extension = false};

  if (data_result.IsPrimaryBootImageOk()) {
    std::string error_msg;
    if (PrimaryBootImageExist(
            /*on_system=*/false, /*minimal=*/false, isa, &error_msg, checked_artifacts)) {
      boot_images_on_data.primary_boot_image = true;
    } else {
      LOG(INFO) << "Incomplete primary boot image on /data: " << error_msg;
      metrics.SetTrigger(OdrMetrics::Trigger::kMissingArtifacts);
      // Add the minimal boot image to `checked_artifacts` if exists. This is to prevent the minimal
      // boot image from being deleted. It does not affect the return value because we should still
      // attempt to generate a full boot image even if the minimal one exists.
      if (PrimaryBootImageExist(
              /*on_system=*/false, /*minimal=*/true, isa, &error_msg, checked_artifacts)) {
        LOG(INFO) << ART_FORMAT("Found minimal primary boot image ({})", isa_str);
      }
    }
  } else {
    metrics.SetTrigger(data_result.GetTrigger());
  }

  if (boot_images_on_system.primary_boot_image || boot_images_on_data.primary_boot_image) {
    if (data_result.IsBootImageMainlineExtensionOk()) {
      std::string error_msg;
      if (BootImageMainlineExtensionExist(
              /*on_system=*/false, isa, &error_msg, checked_artifacts)) {
        boot_images_on_data.boot_image_mainline_extension = true;
      } else {
        LOG(INFO) << "Incomplete boot image mainline extension on /data: " << error_msg;
        metrics.SetTrigger(OdrMetrics::Trigger::kMissingArtifacts);
      }
    } else {
      metrics.SetTrigger(data_result.GetTrigger());
    }
  }

  BootImages boot_images_to_generate{
      .primary_boot_image =
          !boot_images_on_system.primary_boot_image && !boot_images_on_data.primary_boot_image,
      .boot_image_mainline_extension = !boot_images_on_system.boot_image_mainline_extension &&
                                       !boot_images_on_data.boot_image_mainline_extension,
  };

  if (boot_images_to_generate.Count() == 0) {
    LOG(INFO) << ART_FORMAT("Boot images on /data OK ({})", isa_str);
  }

  return boot_images_to_generate;
}

std::set<std::string> OnDeviceRefresh::CheckSystemServerArtifactsAreUpToDate(
    OdrMetrics& metrics,
    const PreconditionCheckResult& system_result,
    const PreconditionCheckResult& data_result,
    /*out*/ std::vector<std::string>* checked_artifacts) const {
  std::set<std::string> jars_to_compile;
  std::set<std::string> jars_missing_artifacts_on_system;
  if (system_result.IsSystemServerOk()) {
    // We can use the artifacts on /system. Check if they exist.
    std::string error_msg;
    if (SystemServerArtifactsExist(
            /*on_system=*/true, &error_msg, &jars_missing_artifacts_on_system)) {
      LOG(INFO) << "system_server artifacts on /system OK";
      return {};
    }

    LOG(INFO) << "Incomplete system server artifacts on /system: " << error_msg;
    LOG(INFO) << "Checking system server artifacts /data";
  } else {
    jars_missing_artifacts_on_system = AllSystemServerJars();
  }

  std::set<std::string> jars_missing_artifacts_on_data;
  std::string error_msg;
  if (data_result.IsSystemServerOk()) {
    SystemServerArtifactsExist(
        /*on_system=*/false, &error_msg, &jars_missing_artifacts_on_data, checked_artifacts);
  } else {
    jars_missing_artifacts_on_data = AllSystemServerJars();
  }

  std::set_intersection(jars_missing_artifacts_on_system.begin(),
                        jars_missing_artifacts_on_system.end(),
                        jars_missing_artifacts_on_data.begin(),
                        jars_missing_artifacts_on_data.end(),
                        std::inserter(jars_to_compile, jars_to_compile.end()));
  if (!jars_to_compile.empty()) {
    if (data_result.IsSystemServerOk()) {
      LOG(INFO) << "Incomplete system_server artifacts on /data: " << error_msg;
      metrics.SetTrigger(OdrMetrics::Trigger::kMissingArtifacts);
    } else {
      metrics.SetTrigger(data_result.GetTrigger());
    }
    return jars_to_compile;
  }

  LOG(INFO) << "system_server artifacts on /data OK";
  return {};
}

Result<void> OnDeviceRefresh::CleanupArtifactDirectory(
    OdrMetrics& metrics, const std::vector<std::string>& artifacts_to_keep) const {
  const std::string& artifact_dir = config_.GetArtifactDirectory();
  std::unordered_set<std::string> artifact_set{artifacts_to_keep.begin(), artifacts_to_keep.end()};

  // When anything unexpected happens, remove all artifacts.
  auto remove_artifact_dir = android::base::make_scope_guard([&]() {
    if (!RemoveDirectory(artifact_dir)) {
      LOG(ERROR) << "Failed to remove the artifact directory";
    }
  });

  std::vector<std::filesystem::directory_entry> entries;
  std::error_code ec;
  for (const auto& entry : std::filesystem::recursive_directory_iterator(artifact_dir, ec)) {
    // Save the entries and use them later because modifications during the iteration will result in
    // undefined behavior;
    entries.push_back(entry);
  }
  if (ec && ec.value() != ENOENT) {
    metrics.SetStatus(ec.value() == EPERM ? OdrMetrics::Status::kDalvikCachePermissionDenied :
                                            OdrMetrics::Status::kIoError);
    return Errorf("Failed to iterate over entries in the artifact directory: {}", ec.message());
  }

  for (const std::filesystem::directory_entry& entry : entries) {
    std::string path = entry.path().string();
    if (entry.is_regular_file()) {
      if (!ContainsElement(artifact_set, path)) {
        LOG(INFO) << "Removing " << path;
        if (unlink(path.c_str()) != 0) {
          metrics.SetStatus(OdrMetrics::Status::kIoError);
          return ErrnoErrorf("Failed to remove file {}", QuotePath(path));
        }
      }
    } else if (!entry.is_directory()) {
      // Neither a regular file nor a directory. Unexpected file type.
      LOG(INFO) << "Removing " << path;
      if (unlink(path.c_str()) != 0) {
        metrics.SetStatus(OdrMetrics::Status::kIoError);
        return ErrnoErrorf("Failed to remove file {}", QuotePath(path));
      }
    }
  }

  remove_artifact_dir.Disable();
  return {};
}

Result<void> OnDeviceRefresh::RefreshExistingArtifacts() const {
  const std::string& artifact_dir = config_.GetArtifactDirectory();
  if (!OS::DirectoryExists(artifact_dir.c_str())) {
    return {};
  }

  std::vector<std::filesystem::directory_entry> entries;
  std::error_code ec;
  for (const auto& entry : std::filesystem::recursive_directory_iterator(artifact_dir, ec)) {
    // Save the entries and use them later because modifications during the iteration will result in
    // undefined behavior;
    entries.push_back(entry);
  }
  if (ec) {
    return Errorf("Failed to iterate over entries in the artifact directory: {}", ec.message());
  }

  for (const std::filesystem::directory_entry& entry : entries) {
    std::string path = entry.path().string();
    if (entry.is_regular_file()) {
      // Unexpected files are already removed by `CleanupArtifactDirectory`. We can safely assume
      // that all the remaining files are good.
      LOG(INFO) << "Refreshing " << path;
      std::string content;
      if (!android::base::ReadFileToString(path, &content)) {
        return Errorf("Failed to read file {}", QuotePath(path));
      }
      if (unlink(path.c_str()) != 0) {
        return ErrnoErrorf("Failed to remove file {}", QuotePath(path));
      }
      if (!android::base::WriteStringToFile(content, path)) {
        return Errorf("Failed to write file {}", QuotePath(path));
      }
      if (chmod(path.c_str(), kFileMode) != 0) {
        return ErrnoErrorf("Failed to chmod file {}", QuotePath(path));
      }
    }
  }

  return {};
}

WARN_UNUSED ExitCode
OnDeviceRefresh::CheckArtifactsAreUpToDate(OdrMetrics& metrics,
                                           /*out*/ CompilationOptions* compilation_options) const {
  metrics.SetStage(OdrMetrics::Stage::kCheck);

  // Clean-up helper used to simplify clean-ups and handling failures there.
  auto cleanup_and_compile_all = [&, this]() {
    *compilation_options = CompilationOptions::CompileAll(*this);
    if (!RemoveArtifactsDirectory()) {
      metrics.SetStatus(OdrMetrics::Status::kIoError);
      return ExitCode::kCleanupFailed;
    }
    return ExitCode::kCompilationRequired;
  };

  std::optional<std::vector<apex::ApexInfo>> apex_info_list = GetApexInfoList();
  if (!apex_info_list.has_value()) {
    // This should never happen, further up-to-date checks are not possible if it does.
    LOG(ERROR) << "Could not get APEX info.";
    metrics.SetTrigger(OdrMetrics::Trigger::kUnknown);
    return cleanup_and_compile_all();
  }

  std::optional<apex::ApexInfo> art_apex_info = GetArtApexInfo(apex_info_list.value());
  if (!art_apex_info.has_value()) {
    // This should never happen, further up-to-date checks are not possible if it does.
    LOG(ERROR) << "Could not get ART APEX info.";
    metrics.SetTrigger(OdrMetrics::Trigger::kUnknown);
    return cleanup_and_compile_all();
  }

  // Record ART APEX version for metrics reporting.
  metrics.SetArtApexVersion(art_apex_info->getVersionCode());

  // Log the version so there's a starting point for any issues reported (b/197489543).
  LOG(INFO) << "ART APEX version " << art_apex_info->getVersionCode();

  // Record ART APEX last update milliseconds (used in compilation log).
  metrics.SetArtApexLastUpdateMillis(art_apex_info->getLastUpdateMillis());

  InstructionSet system_server_isa = config_.GetSystemServerIsa();
  std::vector<std::string> checked_artifacts;

  PreconditionCheckResult system_result = CheckPreconditionForSystem(apex_info_list.value());
  PreconditionCheckResult data_result = CheckPreconditionForData(apex_info_list.value());

  for (InstructionSet isa : config_.GetBootClasspathIsas()) {
    BootImages boot_images_to_generate = CheckBootClasspathArtifactsAreUpToDate(
        metrics, isa, system_result, data_result, &checked_artifacts);
    if (boot_images_to_generate.Count() > 0) {
      compilation_options->boot_images_to_generate_for_isas.emplace_back(isa,
                                                                         boot_images_to_generate);
      // system_server artifacts are invalid without valid boot classpath artifacts.
      if (isa == system_server_isa) {
        compilation_options->system_server_jars_to_compile = AllSystemServerJars();
      }
    }
  }

  if (compilation_options->system_server_jars_to_compile.empty()) {
    compilation_options->system_server_jars_to_compile = CheckSystemServerArtifactsAreUpToDate(
        metrics, system_result, data_result, &checked_artifacts);
  }

  bool compilation_required = compilation_options->CompilationUnitCount() > 0;

  if (!compilation_required && !data_result.IsAllOk()) {
    // Return kCompilationRequired to generate the cache info even if there's nothing to compile.
    compilation_required = true;
    metrics.SetTrigger(data_result.GetTrigger());
  }

  // Always keep the cache info.
  checked_artifacts.push_back(cache_info_filename_);

  Result<void> result = CleanupArtifactDirectory(metrics, checked_artifacts);
  if (!result.ok()) {
    LOG(ERROR) << result.error();
    return ExitCode::kCleanupFailed;
  }

  return compilation_required ? ExitCode::kCompilationRequired : ExitCode::kOkay;
}

WARN_UNUSED CompilationResult OnDeviceRefresh::RunDex2oat(
    const std::string& staging_dir,
    const std::string& debug_message,
    InstructionSet isa,
    const std::vector<std::string>& dex_files,
    const std::vector<std::string>& boot_classpath,
    const std::vector<std::string>& input_boot_images,
    const OdrArtifacts& artifacts,
    CmdlineBuilder&& extra_args,
    /*inout*/ std::vector<std::unique_ptr<File>>& readonly_files_raii) const {
  CmdlineBuilder args;
  args.Add(config_.GetDex2Oat());

  AddDex2OatCommonOptions(args);
  AddDex2OatDebugInfo(args);
  AddDex2OatInstructionSet(args, isa, config_.GetSystemProperties());
  Result<void> result = AddDex2OatConcurrencyArguments(
      args, config_.GetCompilationOsMode(), config_.GetSystemProperties());
  if (!result.ok()) {
    return CompilationResult::Error(OdrMetrics::Status::kUnknown, result.error().message());
  }

  // dex2oat reads some system properties from cache-info.xml generated by odrefresh.
  result = AddCacheInfoFd(args, readonly_files_raii, cache_info_filename_);
  if (!result.ok()) {
    return CompilationResult::Error(OdrMetrics::Status::kUnknown, result.error().message());
  }

  for (const std::string& dex_file : dex_files) {
    std::string actual_path = RewriteParentDirectoryIfNeeded(dex_file);
    args.Add("--dex-file=%s", dex_file);
    std::unique_ptr<File> file(OS::OpenFileForReading(actual_path.c_str()));
    if (file == nullptr) {
      return CompilationResult::Error(
          OdrMetrics::Status::kIoError,
          ART_FORMAT("Failed to open dex file '{}': {}", actual_path, strerror(errno)));
    }
    args.Add("--dex-fd=%d", file->Fd());
    readonly_files_raii.push_back(std::move(file));
  }

  args.AddRuntime("-Xbootclasspath:%s", Join(boot_classpath, ":"));
  result = AddBootClasspathFds(args, readonly_files_raii, boot_classpath);
  if (!result.ok()) {
    return CompilationResult::Error(OdrMetrics::Status::kIoError, result.error().message());
  }

  if (!input_boot_images.empty()) {
    args.Add("--boot-image=%s", Join(input_boot_images, ':'));
    result = AddCompiledBootClasspathFdsIfAny(
        args, readonly_files_raii, boot_classpath, isa, input_boot_images);
    if (!result.ok()) {
      return CompilationResult::Error(OdrMetrics::Status::kIoError, result.error().message());
    }
  }

  args.Add("--oat-location=%s", artifacts.OatPath());
  std::pair<std::string, const char*> location_kind_pairs[] = {
      std::make_pair(artifacts.ImagePath(), artifacts.ImageKind()),
      std::make_pair(artifacts.OatPath(), "oat"),
      std::make_pair(artifacts.VdexPath(), "output-vdex")};
  std::vector<std::unique_ptr<File>> staging_files;
  for (const auto& [location, kind] : location_kind_pairs) {
    std::string staging_location = GetStagingLocation(staging_dir, location);
    std::unique_ptr<File> staging_file(OS::CreateEmptyFile(staging_location.c_str()));
    if (staging_file == nullptr) {
      return CompilationResult::Error(
          OdrMetrics::Status::kIoError,
          ART_FORMAT("Failed to create {} file '{}': {}", kind, staging_location, strerror(errno)));
    }
    // Don't check the state of the staging file. It doesn't need to be flushed because it's removed
    // after the compilation regardless of success or failure.
    staging_file->MarkUnchecked();
    args.Add(StringPrintf("--%s-fd=%d", kind, staging_file->Fd()));
    staging_files.emplace_back(std::move(staging_file));
  }

  std::string install_location = Dirname(artifacts.OatPath());
  if (!EnsureDirectoryExists(install_location)) {
    return CompilationResult::Error(
        OdrMetrics::Status::kIoError,
        ART_FORMAT("Error encountered when preparing directory '{}'", install_location));
  }

  args.Concat(std::move(extra_args));

  Timer timer;
  time_t timeout = GetSubprocessTimeout();
  std::string cmd_line = Join(args.Get(), ' ');
  LOG(INFO) << ART_FORMAT("{}: {} [timeout {}s]", debug_message, cmd_line, timeout);
  if (config_.GetDryRun()) {
    LOG(INFO) << "Compilation skipped (dry-run).";
    return CompilationResult::Ok();
  }

  std::string error_msg;
  ExecResult dex2oat_result = exec_utils_->ExecAndReturnResult(args.Get(), timeout, &error_msg);

  if (dex2oat_result.exit_code != 0) {
    return CompilationResult::Dex2oatError(
        dex2oat_result.exit_code < 0 ?
            error_msg :
            ART_FORMAT("dex2oat returned an unexpected code: {}", dex2oat_result.exit_code),
        timer.duration().count(),
        dex2oat_result);
  }

  if (!MoveOrEraseFiles(staging_files, install_location)) {
    return CompilationResult::Error(
        OdrMetrics::Status::kIoError,
        ART_FORMAT("Failed to commit artifacts to '{}'", install_location));
  }

  return CompilationResult::Dex2oatOk(timer.duration().count(), dex2oat_result);
}

WARN_UNUSED CompilationResult
OnDeviceRefresh::RunDex2oatForBootClasspath(const std::string& staging_dir,
                                            const std::string& debug_name,
                                            InstructionSet isa,
                                            const std::vector<std::string>& dex_files,
                                            const std::vector<std::string>& boot_classpath,
                                            const std::vector<std::string>& input_boot_images,
                                            const std::string& output_path) const {
  CmdlineBuilder args;
  std::vector<std::unique_ptr<File>> readonly_files_raii;

  // Compile as a single image for fewer files and slightly less memory overhead.
  args.Add("--single-image");

  if (input_boot_images.empty()) {
    // Primary boot image.
    std::string art_boot_profile_file = GetArtRoot() + "/etc/boot-image.prof";
    std::string framework_boot_profile_file = GetAndroidRoot() + "/etc/boot-image.prof";
    Result<bool> has_any_profile = AddDex2OatProfile(
        args, readonly_files_raii, {art_boot_profile_file, framework_boot_profile_file});
    if (!has_any_profile.ok()) {
      return CompilationResult::Error(OdrMetrics::Status::kIoError,
                                      has_any_profile.error().message());
    }
    if (!*has_any_profile) {
      return CompilationResult::Error(OdrMetrics::Status::kIoError, "Missing boot image profile");
    }
    const std::string& compiler_filter = config_.GetBootImageCompilerFilter();
    if (!compiler_filter.empty()) {
      args.Add("--compiler-filter=%s", compiler_filter);
    } else {
      args.Add("--compiler-filter=%s", kPrimaryCompilerFilter);
    }

    args.Add(StringPrintf("--base=0x%08x", ART_BASE_ADDRESS));

    std::string dirty_image_objects_file(GetAndroidRoot() + "/etc/dirty-image-objects");
    std::unique_ptr<File> file(OS::OpenFileForReading(dirty_image_objects_file.c_str()));
    if (file != nullptr) {
      args.Add("--dirty-image-objects-fd=%d", file->Fd());
      readonly_files_raii.push_back(std::move(file));
    } else if (errno == ENOENT) {
      LOG(WARNING) << ART_FORMAT("Missing dirty objects file '{}'", dirty_image_objects_file);
    } else {
      return CompilationResult::Error(OdrMetrics::Status::kIoError,
                                      ART_FORMAT("Failed to open dirty objects file '{}': {}",
                                                 dirty_image_objects_file,
                                                 strerror(errno)));
    }

    std::string preloaded_classes_file(GetAndroidRoot() + "/etc/preloaded-classes");
    file.reset(OS::OpenFileForReading(preloaded_classes_file.c_str()));
    if (file != nullptr) {
      args.Add("--preloaded-classes-fds=%d", file->Fd());
      readonly_files_raii.push_back(std::move(file));
    } else if (errno == ENOENT) {
      LOG(WARNING) << ART_FORMAT("Missing preloaded classes file '{}'", preloaded_classes_file);
    } else {
      return CompilationResult::Error(OdrMetrics::Status::kIoError,
                                      ART_FORMAT("Failed to open preloaded classes file '{}': {}",
                                                 preloaded_classes_file,
                                                 strerror(errno)));
    }
  } else {
    // Mainline extension.
    args.Add("--compiler-filter=%s", kMainlineCompilerFilter);
  }

  const OdrSystemProperties& system_properties = config_.GetSystemProperties();
  args.AddRuntimeIfNonEmpty("-Xms%s", system_properties.GetOrEmpty("dalvik.vm.image-dex2oat-Xms"))
      .AddRuntimeIfNonEmpty("-Xmx%s", system_properties.GetOrEmpty("dalvik.vm.image-dex2oat-Xmx"));

  return RunDex2oat(
      staging_dir,
      ART_FORMAT("Compiling boot classpath ({}, {})", GetInstructionSetString(isa), debug_name),
      isa,
      dex_files,
      boot_classpath,
      input_boot_images,
      OdrArtifacts::ForBootImage(output_path),
      std::move(args),
      readonly_files_raii);
}

WARN_UNUSED CompilationResult
OnDeviceRefresh::CompileBootClasspath(const std::string& staging_dir,
                                      InstructionSet isa,
                                      BootImages boot_images,
                                      const std::function<void()>& on_dex2oat_success) const {
  DCHECK_GT(boot_images.Count(), 0);
  DCHECK_IMPLIES(boot_images.primary_boot_image, boot_images.boot_image_mainline_extension);

  CompilationResult result = CompilationResult::Ok();

  if (config_.GetMinimal()) {
    result.Merge(
        CompilationResult::Error(OdrMetrics::Status::kUnknown, "Minimal boot image requested"));
  }

  if (!check_compilation_space_()) {
    result.Merge(CompilationResult::Error(OdrMetrics::Status::kNoSpace, "Insufficient space"));
  }

  if (result.IsOk() && boot_images.primary_boot_image) {
    CompilationResult primary_result = RunDex2oatForBootClasspath(
        staging_dir,
        "primary",
        isa,
        dex2oat_boot_classpath_jars_,
        dex2oat_boot_classpath_jars_,
        /*input_boot_images=*/{},
        GetPrimaryBootImagePath(/*on_system=*/false, /*minimal=*/false, isa));
    result.Merge(primary_result);

    if (primary_result.IsOk()) {
      on_dex2oat_success();

      // Remove the minimal boot image only if the full boot image is successfully generated.
      std::string path = GetPrimaryBootImagePath(/*on_system=*/false, /*minimal=*/true, isa);
      OdrArtifacts artifacts = OdrArtifacts::ForBootImage(path);
      unlink(artifacts.ImagePath().c_str());
      unlink(artifacts.OatPath().c_str());
      unlink(artifacts.VdexPath().c_str());
    }
  }

  if (!result.IsOk() && boot_images.primary_boot_image) {
    LOG(ERROR) << "Compilation of primary BCP failed: " << result.error_msg;

    // Fall back to generating a minimal boot image.
    // The compilation of the full boot image will be retried on later reboots with a backoff
    // time, and the minimal boot image will be removed once the compilation of the full boot
    // image succeeds.
    std::string ignored_error_msg;
    if (PrimaryBootImageExist(
            /*on_system=*/false, /*minimal=*/true, isa, &ignored_error_msg)) {
      LOG(INFO) << "Minimal boot image already up-to-date";
      return result;
    }
    std::vector<std::string> art_bcp_jars = GetArtBcpJars();
    CompilationResult minimal_result = RunDex2oatForBootClasspath(
        staging_dir,
        "minimal",
        isa,
        art_bcp_jars,
        art_bcp_jars,
        /*input_boot_images=*/{},
        GetPrimaryBootImagePath(/*on_system=*/false, /*minimal=*/true, isa));
    result.Merge(minimal_result);

    if (!minimal_result.IsOk()) {
      LOG(ERROR) << "Compilation of minimal BCP failed: " << result.error_msg;
    }

    return result;
  }

  if (result.IsOk() && boot_images.boot_image_mainline_extension) {
    CompilationResult mainline_result =
        RunDex2oatForBootClasspath(staging_dir,
                                   "mainline",
                                   isa,
                                   GetMainlineBcpJars(),
                                   boot_classpath_jars_,
                                   GetBestBootImages(isa, /*include_mainline_extension=*/false),
                                   GetBootImageMainlineExtensionPath(/*on_system=*/false, isa));
    result.Merge(mainline_result);

    if (mainline_result.IsOk()) {
      on_dex2oat_success();
    }
  }

  if (!result.IsOk() && boot_images.boot_image_mainline_extension) {
    LOG(ERROR) << "Compilation of mainline BCP failed: " << result.error_msg;
  }

  return result;
}

WARN_UNUSED CompilationResult OnDeviceRefresh::RunDex2oatForSystemServer(
    const std::string& staging_dir,
    const std::string& dex_file,
    const std::vector<std::string>& classloader_context) const {
  CmdlineBuilder args;
  std::vector<std::unique_ptr<File>> readonly_files_raii;
  InstructionSet isa = config_.GetSystemServerIsa();
  std::string output_path = GetSystemServerImagePath(/*on_system=*/false, dex_file);

  std::string actual_jar_path = RewriteParentDirectoryIfNeeded(dex_file);
  std::string profile = actual_jar_path + ".prof";
  const std::string& compiler_filter = config_.GetSystemServerCompilerFilter();
  bool maybe_add_profile = !compiler_filter.empty() || HasVettedDeviceSystemServerProfiles();
  bool has_added_profile = false;
  if (maybe_add_profile) {
    Result<bool> has_any_profile = AddDex2OatProfile(args, readonly_files_raii, {profile});
    if (!has_any_profile.ok()) {
      return CompilationResult::Error(OdrMetrics::Status::kIoError,
                                      has_any_profile.error().message());
    }
    has_added_profile = *has_any_profile;
  }
  if (!compiler_filter.empty()) {
    args.Add("--compiler-filter=%s", compiler_filter);
  } else if (has_added_profile) {
    args.Add("--compiler-filter=speed-profile");
  } else {
    args.Add("--compiler-filter=speed");
  }

  std::string context_path = Join(classloader_context, ':');
  if (art::ContainsElement(systemserver_classpath_jars_, dex_file)) {
    args.Add("--class-loader-context=PCL[%s]", context_path);
  } else {
    args.Add("--class-loader-context=PCL[];PCL[%s]", context_path);
  }
  if (!classloader_context.empty()) {
    std::vector<int> fds;
    for (const std::string& path : classloader_context) {
      std::string actual_path = RewriteParentDirectoryIfNeeded(path);
      std::unique_ptr<File> file(OS::OpenFileForReading(actual_path.c_str()));
      if (file == nullptr) {
        return CompilationResult::Error(
            OdrMetrics::Status::kIoError,
            ART_FORMAT(
                "Failed to open classloader context '{}': {}", actual_path, strerror(errno)));
      }
      fds.emplace_back(file->Fd());
      readonly_files_raii.emplace_back(std::move(file));
    }
    args.Add("--class-loader-context-fds=%s", Join(fds, ':'));
  }

  const OdrSystemProperties& system_properties = config_.GetSystemProperties();
  args.AddRuntimeIfNonEmpty("-Xms%s", system_properties.GetOrEmpty("dalvik.vm.dex2oat-Xms"))
      .AddRuntimeIfNonEmpty("-Xmx%s", system_properties.GetOrEmpty("dalvik.vm.dex2oat-Xmx"));

  return RunDex2oat(staging_dir,
                    ART_FORMAT("Compiling {}", Basename(dex_file)),
                    isa,
                    {dex_file},
                    boot_classpath_jars_,
                    GetBestBootImages(isa, /*include_mainline_extension=*/true),
                    OdrArtifacts::ForSystemServer(output_path),
                    std::move(args),
                    readonly_files_raii);
}

WARN_UNUSED CompilationResult
OnDeviceRefresh::CompileSystemServer(const std::string& staging_dir,
                                     const std::set<std::string>& system_server_jars_to_compile,
                                     const std::function<void()>& on_dex2oat_success) const {
  DCHECK(!system_server_jars_to_compile.empty());

  CompilationResult result = CompilationResult::Ok();
  std::vector<std::string> classloader_context;

  if (!check_compilation_space_()) {
    LOG(ERROR) << "Compilation of system_server failed: Insufficient space";
    return CompilationResult::Error(OdrMetrics::Status::kNoSpace, "Insufficient space");
  }

  for (const std::string& jar : all_systemserver_jars_) {
    if (ContainsElement(system_server_jars_to_compile, jar)) {
      CompilationResult current_result =
          RunDex2oatForSystemServer(staging_dir, jar, classloader_context);
      result.Merge(current_result);

      if (current_result.IsOk()) {
        on_dex2oat_success();
      } else {
        LOG(ERROR) << ART_FORMAT("Compilation of {} failed: {}", Basename(jar), result.error_msg);
      }
    }

    if (ContainsElement(systemserver_classpath_jars_, jar)) {
      classloader_context.emplace_back(jar);
    }
  }

  return result;
}

WARN_UNUSED ExitCode OnDeviceRefresh::Compile(OdrMetrics& metrics,
                                              CompilationOptions compilation_options) const {
  std::string staging_dir;
  metrics.SetStage(OdrMetrics::Stage::kPreparation);

  // If partial compilation is disabled, we should compile everything regardless of what's in
  // `compilation_options`.
  if (!config_.GetPartialCompilation()) {
    compilation_options = CompilationOptions::CompileAll(*this);
    if (!RemoveArtifactsDirectory()) {
      metrics.SetStatus(OdrMetrics::Status::kIoError);
      return ExitCode::kCleanupFailed;
    }
  }

  if (!EnsureDirectoryExists(config_.GetArtifactDirectory())) {
    LOG(ERROR) << "Failed to prepare artifact directory";
    metrics.SetStatus(errno == EPERM ? OdrMetrics::Status::kDalvikCachePermissionDenied :
                                       OdrMetrics::Status::kIoError);
    return ExitCode::kCleanupFailed;
  }

  if (config_.GetRefresh()) {
    Result<void> result = RefreshExistingArtifacts();
    if (!result.ok()) {
      LOG(ERROR) << "Failed to refresh existing artifacts: " << result.error();
      metrics.SetStatus(OdrMetrics::Status::kIoError);
      return ExitCode::kCleanupFailed;
    }
  }

  // Emit cache info before compiling. This can be used to throttle compilation attempts later.
  Result<void> result = WriteCacheInfo();
  if (!result.ok()) {
    LOG(ERROR) << result.error();
    metrics.SetStatus(OdrMetrics::Status::kIoError);
    return ExitCode::kCleanupFailed;
  }

  if (!config_.GetStagingDir().empty()) {
    staging_dir = config_.GetStagingDir();
  } else {
    // Create staging area and assign label for generating compilation artifacts.
    Result<std::string> res = CreateStagingDirectory();
    if (!res.ok()) {
      LOG(ERROR) << res.error().message();
      metrics.SetStatus(OdrMetrics::Status::kStagingFailed);
      return ExitCode::kCleanupFailed;
    }
    staging_dir = res.value();
  }

  std::string error_msg;

  uint32_t dex2oat_invocation_count = 0;
  uint32_t total_dex2oat_invocation_count = compilation_options.CompilationUnitCount();
  ReportNextBootAnimationProgress(dex2oat_invocation_count, total_dex2oat_invocation_count);
  auto advance_animation_progress = [&]() {
    ReportNextBootAnimationProgress(++dex2oat_invocation_count, total_dex2oat_invocation_count);
  };

  const std::vector<InstructionSet>& bcp_instruction_sets = config_.GetBootClasspathIsas();
  DCHECK(!bcp_instruction_sets.empty() && bcp_instruction_sets.size() <= 2);
  InstructionSet system_server_isa = config_.GetSystemServerIsa();

  bool system_server_isa_failed = false;
  std::optional<std::pair<OdrMetrics::Stage, OdrMetrics::Status>> first_failure;

  for (const auto& [isa, boot_images_to_generate] :
       compilation_options.boot_images_to_generate_for_isas) {
    OdrMetrics::Stage stage = (isa == bcp_instruction_sets.front()) ?
                                  OdrMetrics::Stage::kPrimaryBootClasspath :
                                  OdrMetrics::Stage::kSecondaryBootClasspath;
    CompilationResult bcp_result =
        CompileBootClasspath(staging_dir, isa, boot_images_to_generate, advance_animation_progress);
    metrics.SetDex2OatResult(stage, bcp_result.elapsed_time_ms, bcp_result.dex2oat_result);
    metrics.SetBcpCompilationType(stage, boot_images_to_generate.GetTypeForMetrics());
    if (!bcp_result.IsOk()) {
      if (isa == system_server_isa) {
        system_server_isa_failed = true;
      }
      first_failure = first_failure.value_or(std::make_pair(stage, bcp_result.status));
    }
  }

  // Don't compile system server if the compilation of BCP failed.
  if (!system_server_isa_failed && !compilation_options.system_server_jars_to_compile.empty()) {
    OdrMetrics::Stage stage = OdrMetrics::Stage::kSystemServerClasspath;
    CompilationResult ss_result = CompileSystemServer(
        staging_dir, compilation_options.system_server_jars_to_compile, advance_animation_progress);
    metrics.SetDex2OatResult(stage, ss_result.elapsed_time_ms, ss_result.dex2oat_result);
    if (!ss_result.IsOk()) {
      first_failure = first_failure.value_or(std::make_pair(stage, ss_result.status));
    }
  }

  if (first_failure.has_value()) {
    LOG(ERROR) << "Compilation failed, stage: " << first_failure->first
               << " status: " << first_failure->second;
    metrics.SetStage(first_failure->first);
    metrics.SetStatus(first_failure->second);

    if (!config_.GetDryRun() && !RemoveDirectory(staging_dir)) {
      return ExitCode::kCleanupFailed;
    }
    return ExitCode::kCompilationFailed;
  }

  metrics.SetStage(OdrMetrics::Stage::kComplete);
  metrics.SetStatus(OdrMetrics::Status::kOK);
  return ExitCode::kCompilationSuccess;
}

}  // namespace odrefresh
}  // namespace art
