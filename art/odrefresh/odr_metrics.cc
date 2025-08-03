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

#include "odr_metrics.h"

#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <fstream>
#include <iosfwd>
#include <optional>
#include <ostream>
#include <string>

#include <android-base/logging.h>
#include <base/os.h>
#include <base/string_view_cpp20.h>
#include <odr_fs_utils.h>
#include <odr_metrics_record.h>

namespace art {
namespace odrefresh {

OdrMetrics::OdrMetrics(const std::string& cache_directory, const std::string& metrics_file)
    : cache_directory_(cache_directory), metrics_file_(metrics_file) {
  DCHECK(StartsWith(metrics_file_, "/"));

  // Remove existing metrics file if it exists.
  if (OS::FileExists(metrics_file.c_str())) {
    if (unlink(metrics_file.c_str()) != 0) {
      PLOG(ERROR) << "Failed to remove metrics file '" << metrics_file << "'";
    }
  }

  // Create apexdata dalvik-cache directory if it does not exist. It is required before
  // calling GetFreeSpaceMiB().
  if (!EnsureDirectoryExists(cache_directory)) {
    // This should never fail except for no space on device or configuration issues (e.g. SELinux).
    LOG(WARNING) << "Cache directory '" << cache_directory << "' could not be created.";
  }
  cache_space_free_start_mib_ = GetFreeSpaceMiB(cache_directory);
}

OdrMetrics::~OdrMetrics() {
  CaptureSpaceFreeEnd();

  // Log metrics only if this is explicitly enabled (typically when compilation was done or an error
  // occurred).
  if (enabled_) {
    WriteToFile(metrics_file_, this);
  }
}

void OdrMetrics::CaptureSpaceFreeEnd() {
  cache_space_free_end_mib_ = GetFreeSpaceMiB(cache_directory_);
}

void OdrMetrics::SetDex2OatResult(Stage stage,
                                  int64_t compilation_time_ms,
                                  const std::optional<ExecResult>& dex2oat_result) {
  switch (stage) {
    case Stage::kPrimaryBootClasspath:
      primary_bcp_compilation_millis_ = compilation_time_ms;
      primary_bcp_dex2oat_result_ = dex2oat_result;
      break;
    case Stage::kSecondaryBootClasspath:
      secondary_bcp_compilation_millis_ = compilation_time_ms;
      secondary_bcp_dex2oat_result_ = dex2oat_result;
      break;
    case Stage::kSystemServerClasspath:
      system_server_compilation_millis_ = compilation_time_ms;
      system_server_dex2oat_result_ = dex2oat_result;
      break;
    case Stage::kCheck:
    case Stage::kComplete:
    case Stage::kPreparation:
    case Stage::kUnknown:
      LOG(FATAL) << "Unexpected stage " << stage_ << " when setting dex2oat result";
  }
}

void OdrMetrics::SetBcpCompilationType(Stage stage, BcpCompilationType type) {
  switch (stage) {
    case Stage::kPrimaryBootClasspath:
      primary_bcp_compilation_type_ = type;
      break;
    case Stage::kSecondaryBootClasspath:
      secondary_bcp_compilation_type_ = type;
      break;
    case Stage::kSystemServerClasspath:
    case Stage::kCheck:
    case Stage::kComplete:
    case Stage::kPreparation:
    case Stage::kUnknown:
      LOG(FATAL) << "Unexpected stage " << stage_ << " when setting BCP compilation type";
  }
}

int32_t OdrMetrics::GetFreeSpaceMiB(const std::string& path) {
  static constexpr uint32_t kBytesPerMiB = 1024 * 1024;
  static constexpr uint64_t kNominalMaximumCacheBytes = 1024 * kBytesPerMiB;

  // Assume nominal cache space is 1GiB (much larger than expected, ~100MB).
  uint64_t used_space_bytes;
  if (!GetUsedSpace(path, &used_space_bytes)) {
    used_space_bytes = 0;
  }
  uint64_t nominal_free_space_bytes = kNominalMaximumCacheBytes - used_space_bytes;

  // Get free space on partition containing `path`.
  uint64_t free_space_bytes;
  if (!GetFreeSpace(path, &free_space_bytes)) {
    free_space_bytes = kNominalMaximumCacheBytes;
  }

  // Pick the smallest free space, ie space on partition or nominal space in cache.
  // There are two things of interest for metrics:
  //  (i) identifying failed compilations due to low space.
  // (ii) understanding what the storage requirements are for the spectrum of boot classpaths and
  //      system_server classpaths.
  uint64_t free_space_mib = std::min(free_space_bytes, nominal_free_space_bytes) / kBytesPerMiB;
  return static_cast<int32_t>(free_space_mib);
}

OdrMetricsRecord OdrMetrics::ToRecord() const {
  return {
      .odrefresh_metrics_version = kOdrefreshMetricsVersion,
      .art_apex_version = art_apex_version_,
      .trigger = static_cast<int32_t>(trigger_),
      .stage_reached = static_cast<int32_t>(stage_),
      .status = static_cast<int32_t>(status_),
      .cache_space_free_start_mib = cache_space_free_start_mib_,
      .cache_space_free_end_mib = cache_space_free_end_mib_,
      .primary_bcp_compilation_millis = primary_bcp_compilation_millis_,
      .secondary_bcp_compilation_millis = secondary_bcp_compilation_millis_,
      .system_server_compilation_millis = system_server_compilation_millis_,
      .primary_bcp_dex2oat_result = ConvertExecResult(primary_bcp_dex2oat_result_),
      .secondary_bcp_dex2oat_result = ConvertExecResult(secondary_bcp_dex2oat_result_),
      .system_server_dex2oat_result = ConvertExecResult(system_server_dex2oat_result_),
      .primary_bcp_compilation_type = static_cast<int32_t>(primary_bcp_compilation_type_),
      .secondary_bcp_compilation_type = static_cast<int32_t>(secondary_bcp_compilation_type_),
  };
}

OdrMetricsRecord::Dex2OatExecResult OdrMetrics::ConvertExecResult(
    const std::optional<ExecResult>& result) {
  if (result.has_value()) {
    return OdrMetricsRecord::Dex2OatExecResult(result.value());
  } else {
    return {};
  }
}

void OdrMetrics::WriteToFile(const std::string& path, const OdrMetrics* metrics) {
  OdrMetricsRecord record = metrics->ToRecord();

  const android::base::Result<void>& result = record.WriteToFile(path);
  if (!result.ok()) {
    LOG(ERROR) << "Failed to report metrics to file: " << path
               << ", error: " << result.error().message();
  }
}

}  // namespace odrefresh
}  // namespace art
