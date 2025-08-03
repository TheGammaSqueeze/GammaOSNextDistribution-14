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

#ifndef ART_ODREFRESH_ODREFRESH_H_
#define ART_ODREFRESH_ODREFRESH_H_

#include <ctime>
#include <functional>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <unordered_set>
#include <vector>

#include "android-base/function_ref.h"
#include "android-base/result.h"
#include "base/os.h"
#include "com_android_apex.h"
#include "com_android_art.h"
#include "exec_utils.h"
#include "odr_artifacts.h"
#include "odr_config.h"
#include "odr_metrics.h"
#include "odrefresh/odrefresh.h"
#include "tools/cmdline_builder.h"

namespace art {
namespace odrefresh {

class OnDeviceRefresh;

struct BootImages {
  static constexpr int kMaxCount = 2;

  bool primary_boot_image : 1;
  bool boot_image_mainline_extension : 1;

  int Count() const;

  OdrMetrics::BcpCompilationType GetTypeForMetrics() const;
};

struct CompilationOptions {
  // If not empty, generate the boot images for ISAs in the list.
  std::vector<std::pair<InstructionSet, BootImages>> boot_images_to_generate_for_isas;

  // If not empty, compile the system server jars in the list.
  std::set<std::string> system_server_jars_to_compile;

  static CompilationOptions CompileAll(const OnDeviceRefresh& odr);

  int CompilationUnitCount() const;
};

struct CompilationResult {
  OdrMetrics::Status status = OdrMetrics::Status::kOK;
  std::string error_msg;
  int64_t elapsed_time_ms = 0;
  std::optional<ExecResult> dex2oat_result;

  static CompilationResult Ok() { return {}; }

  static CompilationResult Dex2oatOk(int64_t elapsed_time_ms, const ExecResult& dex2oat_result) {
    return {.elapsed_time_ms = elapsed_time_ms, .dex2oat_result = dex2oat_result};
  }

  static CompilationResult Error(OdrMetrics::Status status, const std::string& error_msg) {
    return {.status = status, .error_msg = error_msg};
  }

  static CompilationResult Dex2oatError(const std::string& error_msg,
                                        int64_t elapsed_time_ms,
                                        const ExecResult& dex2oat_result) {
    return {.status = OdrMetrics::Status::kDex2OatError,
            .error_msg = error_msg,
            .elapsed_time_ms = elapsed_time_ms,
            .dex2oat_result = dex2oat_result};
  }

  bool IsOk() { return status == OdrMetrics::Status::kOK; }

  void Merge(const CompilationResult& other) {
    // Accumulate the compilation time.
    elapsed_time_ms += other.elapsed_time_ms;

    // Only keep the first failure.
    if (status == OdrMetrics::Status::kOK) {
      status = other.status;
      error_msg = other.error_msg;
      dex2oat_result = other.dex2oat_result;
    }
  }

 private:
  CompilationResult() = default;
};

class PreconditionCheckResult {
 public:
  static PreconditionCheckResult NoneOk(OdrMetrics::Trigger trigger) {
    return PreconditionCheckResult(trigger,
                                   /*primary_boot_image_ok=*/false,
                                   /*boot_image_mainline_extension_ok=*/false,
                                   /*system_server_ok=*/false);
  }
  static PreconditionCheckResult BootImageMainlineExtensionNotOk(OdrMetrics::Trigger trigger) {
    return PreconditionCheckResult(trigger,
                                   /*primary_boot_image_ok=*/true,
                                   /*boot_image_mainline_extension_ok=*/false,
                                   /*system_server_ok=*/false);
  }
  static PreconditionCheckResult SystemServerNotOk(OdrMetrics::Trigger trigger) {
    return PreconditionCheckResult(trigger,
                                   /*primary_boot_image_ok=*/true,
                                   /*boot_image_mainline_extension_ok=*/true,
                                   /*system_server_ok=*/false);
  }
  static PreconditionCheckResult AllOk() {
    return PreconditionCheckResult(/*trigger=*/std::nullopt,
                                   /*primary_boot_image_ok=*/true,
                                   /*boot_image_mainline_extension_ok=*/true,
                                   /*system_server_ok=*/true);
  }
  bool IsAllOk() const { return !trigger_.has_value(); }
  OdrMetrics::Trigger GetTrigger() const { return trigger_.value(); }
  bool IsPrimaryBootImageOk() const { return primary_boot_image_ok_; }
  bool IsBootImageMainlineExtensionOk() const { return boot_image_mainline_extension_ok_; }
  bool IsSystemServerOk() const { return system_server_ok_; }

 private:
  // Use static factory methods instead.
  PreconditionCheckResult(std::optional<OdrMetrics::Trigger> trigger,
                          bool primary_boot_image_ok,
                          bool boot_image_mainline_extension_ok,
                          bool system_server_ok)
      : trigger_(trigger),
        primary_boot_image_ok_(primary_boot_image_ok),
        boot_image_mainline_extension_ok_(boot_image_mainline_extension_ok),
        system_server_ok_(system_server_ok) {}

  // Indicates why the precondition is not okay, or `std::nullopt` if it's okay.
  std::optional<OdrMetrics::Trigger> trigger_;
  bool primary_boot_image_ok_;
  bool boot_image_mainline_extension_ok_;
  bool system_server_ok_;
};

class OnDeviceRefresh final {
 public:
  explicit OnDeviceRefresh(const OdrConfig& config);

  // Constructor with injections. For testing and internal use only.
  OnDeviceRefresh(const OdrConfig& config,
                  const std::string& cache_info_filename,
                  std::unique_ptr<ExecUtils> exec_utils,
                  android::base::function_ref<bool()> check_compilation_space);

  // Returns the exit code and specifies what should be compiled in `compilation_options`.
  WARN_UNUSED ExitCode
  CheckArtifactsAreUpToDate(OdrMetrics& metrics,
                            /*out*/ CompilationOptions* compilation_options) const;

  WARN_UNUSED ExitCode Compile(OdrMetrics& metrics, CompilationOptions compilation_options) const;

  WARN_UNUSED bool RemoveArtifactsDirectory() const;

  // Returns a set of all system server jars.
  std::set<std::string> AllSystemServerJars() const {
    return {all_systemserver_jars_.begin(), all_systemserver_jars_.end()};
  }

  const OdrConfig& Config() const { return config_; }

 private:
  time_t GetExecutionTimeUsed() const;

  time_t GetExecutionTimeRemaining() const;

  time_t GetSubprocessTimeout() const;

  // Gets the `ApexInfo` for active APEXes.
  std::optional<std::vector<com::android::apex::ApexInfo>> GetApexInfoList() const;

  // Reads the ART APEX cache information (if any) found in the output artifact directory.
  android::base::Result<com::android::art::CacheInfo> ReadCacheInfo() const;

  // Writes ART APEX cache information to `kOnDeviceRefreshOdrefreshArtifactDirectory`.
  android::base::Result<void> WriteCacheInfo() const;

  std::vector<com::android::art::Component> GenerateBootClasspathComponents() const;

  std::vector<com::android::art::Component> GenerateDex2oatBootClasspathComponents() const;

  std::vector<com::android::art::SystemServerComponent> GenerateSystemServerComponents() const;

  // Returns the list of BCP jars in the ART module.
  std::vector<std::string> GetArtBcpJars() const;

  // Returns the list of BCP jars for the boot image framework extension.
  std::vector<std::string> GetFrameworkBcpJars() const;

  // Returns the list of BCP jars for the boot image mainline extension.
  std::vector<std::string> GetMainlineBcpJars() const;

  // Returns the symbolic primary boot image location (without ISA). If `minimal` is true, returns
  // the symbolic location of the minimal boot image.
  std::string GetPrimaryBootImage(bool on_system, bool minimal) const;

  // Returns the real primary boot image location (with ISA).  If `minimal` is true, returns the
  // real location of the minimal boot image.
  std::string GetPrimaryBootImagePath(bool on_system, bool minimal, InstructionSet isa) const;

  // Returns the symbolic boot image framework extension location (without ISA). Note that this only
  // applies to boot images on /system.
  std::string GetSystemBootImageFrameworkExtension() const;

  // Returns the real boot image framework extension location (with ISA). Note that this only
  // applies to boot images on /system.
  std::string GetSystemBootImageFrameworkExtensionPath(InstructionSet isa) const;

  // Returns the symbolic boot image mainline extension location (without ISA).
  std::string GetBootImageMainlineExtension(bool on_system) const;

  // Returns the real boot image mainline extension location (with ISA).
  std::string GetBootImageMainlineExtensionPath(bool on_system, InstructionSet isa) const;

  // Returns the best combination of symbolic boot image locations (without ISA) based on file
  // existence.
  std::vector<std::string> GetBestBootImages(InstructionSet isa,
                                             bool include_mainline_extension) const;

  std::string GetSystemServerImagePath(bool on_system, const std::string& jar_path) const;

  // Removes files that are not in the list.
  android::base::Result<void> CleanupArtifactDirectory(
      OdrMetrics& metrics, const std::vector<std::string>& artifacts_to_keep) const;

  // Loads artifacts to memory and writes them back. This is a workaround for old versions of
  // odsign, which encounters "file exists" error when it adds existing artifacts to fs-verity. This
  // function essentially removes existing artifacts from fs-verity to avoid the error.
  android::base::Result<void> RefreshExistingArtifacts() const;

  // Returns whether the primary boot image is present.
  // If `on_system` is true, checks both the primary boot image and the framework extension on
  // /system.
  // If `minimal` is true, checks the minimal boot image.
  // If `checked_artifacts` is present, adds checked artifacts to `checked_artifacts`.
  WARN_UNUSED bool PrimaryBootImageExist(
      bool on_system,
      bool minimal,
      InstructionSet isa,
      /*out*/ std::string* error_msg,
      /*out*/ std::vector<std::string>* checked_artifacts = nullptr) const;

  // Returns whether the boot image mainline extension exists.
  WARN_UNUSED bool BootImageMainlineExtensionExist(
      bool on_system,
      InstructionSet isa,
      /*out*/ std::string* error_msg,
      /*out*/ std::vector<std::string>* checked_artifacts = nullptr) const;

  // Checks whether all system_server artifacts are present. The artifacts are checked in their
  // order of compilation. Returns true if all are present, false otherwise.
  // Adds the paths to the jars that are missing artifacts in `jars_with_missing_artifacts`.
  // If `checked_artifacts` is present, adds checked artifacts to `checked_artifacts`.
  bool SystemServerArtifactsExist(
      bool on_system,
      /*out*/ std::string* error_msg,
      /*out*/ std::set<std::string>* jars_missing_artifacts,
      /*out*/ std::vector<std::string>* checked_artifacts = nullptr) const;

  // Returns true if all of the system properties listed in `kSystemProperties` are set to the
  // default values. This function is usually called when cache-info.xml does not exist (i.e.,
  // compilation has not been done before).
  WARN_UNUSED bool CheckSystemPropertiesAreDefault() const;

  // Returns true if none of the system properties listed in `kSystemProperties` has changed since
  // the last compilation. This function is usually called when cache-info.xml exists.
  WARN_UNUSED bool CheckSystemPropertiesHaveNotChanged(
      const com::android::art::CacheInfo& cache_info) const;

  // Returns true if the system image is built with the right userfaultfd GC flag.
  WARN_UNUSED bool CheckBuildUserfaultFdGc() const;

  // Returns whether the precondition for using artifacts on /system is met. Note that this function
  // does not check the artifacts.
  WARN_UNUSED PreconditionCheckResult
  CheckPreconditionForSystem(const std::vector<com::android::apex::ApexInfo>& apex_info_list) const;

  // Returns whether the precondition for using artifacts on /data is met. Note that this function
  // does not check the artifacts.
  WARN_UNUSED PreconditionCheckResult
  CheckPreconditionForData(const std::vector<com::android::apex::ApexInfo>& apex_info_list) const;

  // Checks whether all boot classpath artifacts are up to date. Returns the boot images that need
  // to be (re-)generated. If `checked_artifacts` is present, adds checked artifacts to
  // `checked_artifacts`.
  WARN_UNUSED BootImages
  CheckBootClasspathArtifactsAreUpToDate(OdrMetrics& metrics,
                                         InstructionSet isa,
                                         const PreconditionCheckResult& system_result,
                                         const PreconditionCheckResult& data_result,
                                         /*out*/ std::vector<std::string>* checked_artifacts) const;

  // Checks whether all system_server artifacts are up to date. The artifacts are checked in their
  // order of compilation. Returns the paths to the jars that need to be compiled.
  // If `checked_artifacts` is present, adds checked artifacts to `checked_artifacts`.
  WARN_UNUSED std::set<std::string> CheckSystemServerArtifactsAreUpToDate(
      OdrMetrics& metrics,
      const PreconditionCheckResult& system_result,
      const PreconditionCheckResult& data_result,
      /*out*/ std::vector<std::string>* checked_artifacts) const;

  WARN_UNUSED CompilationResult
  RunDex2oat(const std::string& staging_dir,
             const std::string& debug_message,
             InstructionSet isa,
             const std::vector<std::string>& dex_files,
             const std::vector<std::string>& boot_classpath,
             const std::vector<std::string>& input_boot_images,
             const OdrArtifacts& artifacts,
             tools::CmdlineBuilder&& extra_args,
             /*inout*/ std::vector<std::unique_ptr<File>>& readonly_files_raii) const;

  WARN_UNUSED CompilationResult
  RunDex2oatForBootClasspath(const std::string& staging_dir,
                             const std::string& debug_name,
                             InstructionSet isa,
                             const std::vector<std::string>& dex_files,
                             const std::vector<std::string>& boot_classpath,
                             const std::vector<std::string>& input_boot_images,
                             const std::string& output_path) const;

  WARN_UNUSED CompilationResult
  CompileBootClasspath(const std::string& staging_dir,
                       InstructionSet isa,
                       BootImages boot_images,
                       const std::function<void()>& on_dex2oat_success) const;

  WARN_UNUSED CompilationResult
  RunDex2oatForSystemServer(const std::string& staging_dir,
                            const std::string& dex_file,
                            const std::vector<std::string>& classloader_context) const;

  WARN_UNUSED CompilationResult
  CompileSystemServer(const std::string& staging_dir,
                      const std::set<std::string>& system_server_jars_to_compile,
                      const std::function<void()>& on_dex2oat_success) const;

  // Configuration to use.
  const OdrConfig& config_;

  // Path to cache information file that is used to speed up artifact checking.
  const std::string cache_info_filename_;

  // The raw list from DEX2OATBOOTCLASSPATH. This is the list of jars that should be compiled into
  // the primary boot image.
  std::vector<std::string> dex2oat_boot_classpath_jars_;

  // The raw list from BOOTCLASSPATH. This is the list of all BCP jars.
  std::vector<std::string> boot_classpath_jars_;

  // Set of system_server components in SYSTEMSERVERCLASSPATH that should be compiled.
  std::unordered_set<std::string> systemserver_classpath_jars_;

  // List of all system_server components, including those in SYSTEMSERVERCLASSPATH and those in
  // STANDALONE_SYSTEMSERVER_JARS (jars that system_server loads dynamically using separate
  // classloaders).
  std::vector<std::string> all_systemserver_jars_;

  const time_t start_time_;

  std::unique_ptr<ExecUtils> exec_utils_;

  android::base::function_ref<bool()> check_compilation_space_;

  DISALLOW_COPY_AND_ASSIGN(OnDeviceRefresh);
};

}  // namespace odrefresh
}  // namespace art

#endif  // ART_ODREFRESH_ODREFRESH_H_
