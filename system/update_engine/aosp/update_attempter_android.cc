//
// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "update_engine/aosp/update_attempter_android.h"

#include <algorithm>
#include <map>
#include <memory>
#include <ostream>
#include <utility>
#include <vector>

#include <android-base/parsebool.h>
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <base/bind.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <brillo/data_encoding.h>
#include <brillo/message_loops/message_loop.h>
#include <brillo/strings/string_utils.h>
#include <log/log_safetynet.h>
#include <processgroup/processgroup.h>

#include "update_engine/aosp/cleanup_previous_update_action.h"
#include "update_engine/common/clock.h"
#include "update_engine/common/constants.h"
#include "update_engine/common/daemon_state_interface.h"
#include "update_engine/common/download_action.h"
#include "update_engine/common/error_code.h"
#include "update_engine/common/error_code_utils.h"
#include "update_engine/common/file_fetcher.h"
#include "update_engine/common/metrics_reporter_interface.h"
#include "update_engine/common/network_selector.h"
#include "update_engine/common/utils.h"
#include "update_engine/metrics_utils.h"
#include "update_engine/payload_consumer/delta_performer.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/file_descriptor_utils.h"
#include "update_engine/payload_consumer/filesystem_verifier_action.h"
#include "update_engine/payload_consumer/partition_writer.h"
#include "update_engine/payload_consumer/payload_constants.h"
#include "update_engine/payload_consumer/payload_metadata.h"
#include "update_engine/payload_consumer/payload_verifier.h"
#include "update_engine/payload_consumer/postinstall_runner_action.h"
#include "update_engine/update_boot_flags_action.h"
#include "update_engine/update_status.h"
#include "update_engine/update_status_utils.h"

#ifndef _UE_SIDELOAD
// Do not include support for external HTTP(s) urls when building
// update_engine_sideload.
#include "update_engine/libcurl_http_fetcher.h"
#endif

using android::base::unique_fd;
using base::Bind;
using base::Time;
using base::TimeDelta;
using base::TimeTicks;
using std::string;
using std::vector;
using update_engine::UpdateEngineStatus;

namespace chromeos_update_engine {

namespace {

// Minimum threshold to broadcast an status update in progress and time.
const double kBroadcastThresholdProgress = 0.01;  // 1%
const int kBroadcastThresholdSeconds = 10;

// Log and set the error on the passed ErrorPtr.
bool LogAndSetGenericError(Error* error,
                           int line_number,
                           const char* file_name,
                           const string& reason) {
  LOG(ERROR) << "Replying with failure: " << file_name << " " << line_number
             << ": " << reason;
  error->line_number = line_number;
  error->file_name = file_name;
  error->message = reason;
  error->error_code = ErrorCode::kError;
  return false;
}

// Log and set the error on the passed ErrorPtr.
bool LogAndSetError(Error* error,
                    int line_number,
                    const char* file_name,
                    const string& reason,
                    ErrorCode error_code) {
  LOG(ERROR) << "Replying with failure: " << file_name << " " << line_number
             << ": " << reason;
  error->line_number = line_number;
  error->file_name = file_name;
  error->message = reason;
  error->error_code = error_code;
  return false;
}

bool GetHeaderAsBool(const string& header, bool default_value) {
  int value = 0;
  if (base::StringToInt(header, &value) && (value == 0 || value == 1))
    return value == 1;
  return default_value;
}

bool ParseKeyValuePairHeaders(const vector<string>& key_value_pair_headers,
                              std::map<string, string>* headers,
                              Error* error) {
  for (const string& key_value_pair : key_value_pair_headers) {
    string key;
    string value;
    if (!brillo::string_utils::SplitAtFirst(
            key_value_pair, "=", &key, &value, false)) {
      return LogAndSetGenericError(error,
                                   __LINE__,
                                   __FILE__,
                                   "Passed invalid header: " + key_value_pair);
    }
    if (!headers->emplace(key, value).second)
      return LogAndSetGenericError(
          error, __LINE__, __FILE__, "Passed repeated key: " + key);
  }
  return true;
}

// Unique identifier for the payload. An empty string means that the payload
// can't be resumed.
string GetPayloadId(const std::map<string, string>& headers) {
  return (headers.count(kPayloadPropertyFileHash)
              ? headers.at(kPayloadPropertyFileHash)
              : "") +
         (headers.count(kPayloadPropertyMetadataHash)
              ? headers.at(kPayloadPropertyMetadataHash)
              : "");
}

std::string GetCurrentBuildVersion() {
  // Example: [ro.build.fingerprint]:
  // [generic/aosp_cf_x86_64_phone/vsoc_x86_64:VanillaIceCream/AOSP.MAIN/user08011303:userdebug/test-keys]
  return android::base::GetProperty("ro.build.fingerprint", "");
}

}  // namespace

UpdateAttempterAndroid::UpdateAttempterAndroid(
    DaemonStateInterface* daemon_state,
    PrefsInterface* prefs,
    BootControlInterface* boot_control,
    HardwareInterface* hardware,
    std::unique_ptr<ApexHandlerInterface> apex_handler)
    : daemon_state_(daemon_state),
      prefs_(prefs),
      boot_control_(boot_control),
      hardware_(hardware),
      apex_handler_android_(std::move(apex_handler)),
      processor_(new ActionProcessor()),
      clock_(new Clock()),
      metric_bytes_downloaded_(kPrefsCurrentBytesDownloaded, prefs_),
      metric_total_bytes_downloaded_(kPrefsTotalBytesDownloaded, prefs_) {
  metrics_reporter_ = metrics::CreateMetricsReporter(
      boot_control_->GetDynamicPartitionControl(), &install_plan_);
  network_selector_ = network::CreateNetworkSelector();
  SetTaskProfiles(0, {"OtaProfiles"});
}

UpdateAttempterAndroid::~UpdateAttempterAndroid() {
  // Release ourselves as the ActionProcessor's delegate to prevent
  // re-scheduling the updates due to the processing stopped.
  processor_->set_delegate(nullptr);
}

[[nodiscard]] static bool DidSystemReboot(PrefsInterface* prefs) {
  string boot_id;
  TEST_AND_RETURN_FALSE(utils::GetBootId(&boot_id));
  string old_boot_id;
  // If no previous boot id found, treat as a reboot and write boot ID.
  if (!prefs->GetString(kPrefsBootId, &old_boot_id)) {
    return true;
  }
  return old_boot_id != boot_id;
}

std::ostream& operator<<(std::ostream& out, OTAResult result) {
  switch (result) {
    case OTAResult::NOT_ATTEMPTED:
      out << "OTAResult::NOT_ATTEMPTED";
      break;
    case OTAResult::ROLLED_BACK:
      out << "OTAResult::ROLLED_BACK";
      break;
    case OTAResult::UPDATED_NEED_REBOOT:
      out << "OTAResult::UPDATED_NEED_REBOOT";
      break;
    case OTAResult::OTA_SUCCESSFUL:
      out << "OTAResult::OTA_SUCCESSFUL";
      break;
  }
  return out;
}

void UpdateAttempterAndroid::Init() {
  // In case of update_engine restart without a reboot we need to restore the
  // reboot needed state.
  if (UpdateCompletedOnThisBoot()) {
    LOG(INFO) << "Updated installed but update_engine is restarted without "
                 "device reboot. Resuming old state.";
    SetStatusAndNotify(UpdateStatus::UPDATED_NEED_REBOOT);
  } else {
    const auto result = GetOTAUpdateResult();
    LOG(INFO) << result;
    SetStatusAndNotify(UpdateStatus::IDLE);
    if (DidSystemReboot(prefs_)) {
      UpdateStateAfterReboot(result);
    }

#ifdef _UE_SIDELOAD
    LOG(INFO) << "Skip ScheduleCleanupPreviousUpdate in sideload because "
              << "ApplyPayload will call it later.";
#else
    ScheduleCleanupPreviousUpdate();
#endif
  }
}

bool UpdateAttempterAndroid::ApplyPayload(
    const string& payload_url,
    int64_t payload_offset,
    int64_t payload_size,
    const vector<string>& key_value_pair_headers,
    Error* error) {
  if (status_ == UpdateStatus::UPDATED_NEED_REBOOT) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "An update already applied, waiting for reboot",
                          ErrorCode::kUpdateAlreadyInstalled);
  }
  if (processor_->IsRunning()) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Already processing an update, cancel it first.",
                          ErrorCode::kUpdateProcessing);
  }
  DCHECK_EQ(status_, UpdateStatus::IDLE);

  std::map<string, string> headers;
  if (!ParseKeyValuePairHeaders(key_value_pair_headers, &headers, error)) {
    return false;
  }

  string payload_id = GetPayloadId(headers);

  // Setup the InstallPlan based on the request.
  install_plan_ = InstallPlan();

  install_plan_.download_url = payload_url;
  install_plan_.version = "";
  base_offset_ = payload_offset;
  InstallPlan::Payload payload;
  payload.size = payload_size;
  if (!payload.size) {
    if (!base::StringToUint64(headers[kPayloadPropertyFileSize],
                              &payload.size)) {
      payload.size = 0;
    }
  }
  if (!brillo::data_encoding::Base64Decode(headers[kPayloadPropertyFileHash],
                                           &payload.hash)) {
    LOG(WARNING) << "Unable to decode base64 file hash: "
                 << headers[kPayloadPropertyFileHash];
  }
  if (!base::StringToUint64(headers[kPayloadPropertyMetadataSize],
                            &payload.metadata_size)) {
    payload.metadata_size = 0;
  }
  // The |payload.type| is not used anymore since minor_version 3.
  payload.type = InstallPayloadType::kUnknown;
  install_plan_.payloads.push_back(payload);

  // The |public_key_rsa| key would override the public key stored on disk.
  install_plan_.public_key_rsa = "";

  install_plan_.hash_checks_mandatory = hardware_->IsOfficialBuild();
  install_plan_.is_resume = !payload_id.empty() &&
                            DeltaPerformer::CanResumeUpdate(prefs_, payload_id);
  if (!install_plan_.is_resume) {
    boot_control_->GetDynamicPartitionControl()->Cleanup();
    boot_control_->GetDynamicPartitionControl()->ResetUpdate(prefs_);

    if (!prefs_->SetString(kPrefsUpdateCheckResponseHash, payload_id)) {
      LOG(WARNING) << "Unable to save the update check response hash.";
    }
  }
  install_plan_.source_slot = GetCurrentSlot();
  install_plan_.target_slot = GetTargetSlot();

  install_plan_.powerwash_required =
      GetHeaderAsBool(headers[kPayloadPropertyPowerwash], false);

  install_plan_.spl_downgrade =
      GetHeaderAsBool(headers[kPayloadPropertySplDowngrade], false);

  if (!IsProductionBuild()) {
    install_plan_.disable_vabc =
        GetHeaderAsBool(headers[kPayloadDisableVABC], false);
  }

  install_plan_.switch_slot_on_reboot =
      GetHeaderAsBool(headers[kPayloadPropertySwitchSlotOnReboot], true);

  install_plan_.run_post_install =
      GetHeaderAsBool(headers[kPayloadPropertyRunPostInstall], true);

  // Skip writing verity if we're resuming and verity has already been written.
  install_plan_.write_verity = true;
  if (install_plan_.is_resume && prefs_->Exists(kPrefsVerityWritten)) {
    bool verity_written = false;
    if (prefs_->GetBoolean(kPrefsVerityWritten, &verity_written) &&
        verity_written) {
      install_plan_.write_verity = false;
    }
  }

  NetworkId network_id = kDefaultNetworkId;
  if (!headers[kPayloadPropertyNetworkId].empty()) {
    if (!base::StringToUint64(headers[kPayloadPropertyNetworkId],
                              &network_id)) {
      return LogAndSetGenericError(
          error,
          __LINE__,
          __FILE__,
          "Invalid network_id: " + headers[kPayloadPropertyNetworkId]);
    }
    if (!network_selector_->SetProcessNetwork(network_id)) {
      return LogAndSetGenericError(
          error,
          __LINE__,
          __FILE__,
          "Unable to set network_id: " + headers[kPayloadPropertyNetworkId]);
    }
    LOG(INFO) << "Using network ID: " << network_id;
  }

  LOG(INFO) << "Using this install plan:";
  install_plan_.Dump();

  HttpFetcher* fetcher = nullptr;
  if (FileFetcher::SupportedUrl(payload_url)) {
    DLOG(INFO) << "Using FileFetcher for file URL.";
    fetcher = new FileFetcher();
  } else {
#ifdef _UE_SIDELOAD
    LOG(FATAL) << "Unsupported sideload URI: " << payload_url;
    return false;  // NOLINT, unreached but analyzer might not know.
                   // Suppress warnings about null 'fetcher' after this.
#else
    LibcurlHttpFetcher* libcurl_fetcher = new LibcurlHttpFetcher(hardware_);
    if (!headers[kPayloadDownloadRetry].empty()) {
      libcurl_fetcher->set_max_retry_count(
          atoi(headers[kPayloadDownloadRetry].c_str()));
    }
    libcurl_fetcher->set_server_to_check(ServerToCheck::kDownload);
    fetcher = libcurl_fetcher;
#endif  // _UE_SIDELOAD
  }
  // Setup extra headers.
  if (!headers[kPayloadPropertyAuthorization].empty())
    fetcher->SetHeader("Authorization", headers[kPayloadPropertyAuthorization]);
  if (!headers[kPayloadPropertyUserAgent].empty())
    fetcher->SetHeader("User-Agent", headers[kPayloadPropertyUserAgent]);

  if (!headers[kPayloadPropertyNetworkProxy].empty()) {
    LOG(INFO) << "Using proxy url from payload headers: "
              << headers[kPayloadPropertyNetworkProxy];
    fetcher->SetProxies({headers[kPayloadPropertyNetworkProxy]});
  }
  if (!headers[kPayloadVABCNone].empty()) {
    install_plan_.vabc_none = true;
  }
  if (!headers[kPayloadEnableThreading].empty()) {
    const auto res = android::base::ParseBool(headers[kPayloadEnableThreading]);
    if (res != android::base::ParseBoolResult::kError) {
      install_plan_.enable_threading =
          res == android::base::ParseBoolResult::kTrue;
    }
  }
  if (!headers[kPayloadBatchedWrites].empty()) {
    install_plan_.batched_writes = true;
  }

  BuildUpdateActions(fetcher);

  SetStatusAndNotify(UpdateStatus::UPDATE_AVAILABLE);

  UpdatePrefsOnUpdateStart(install_plan_.is_resume);
  // TODO(xunchang) report the metrics for unresumable updates

  ScheduleProcessingStart();
  return true;
}

bool UpdateAttempterAndroid::ApplyPayload(
    int fd,
    int64_t payload_offset,
    int64_t payload_size,
    const vector<string>& key_value_pair_headers,
    Error* error) {
  // update_engine state must be checked before modifying payload_fd_ otherwise
  // already running update will be terminated (existing file descriptor will be
  // closed)
  if (status_ == UpdateStatus::UPDATED_NEED_REBOOT) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "An update already applied, waiting for reboot");
  }
  if (processor_->IsRunning()) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "Already processing an update, cancel it first.");
  }
  DCHECK_EQ(status_, UpdateStatus::IDLE);

  payload_fd_.reset(dup(fd));
  const string payload_url = "fd://" + std::to_string(payload_fd_.get());

  return ApplyPayload(
      payload_url, payload_offset, payload_size, key_value_pair_headers, error);
}

bool UpdateAttempterAndroid::SuspendUpdate(Error* error) {
  if (!processor_->IsRunning())
    return LogAndSetGenericError(
        error, __LINE__, __FILE__, "No ongoing update to suspend.");
  processor_->SuspendProcessing();
  return true;
}

bool UpdateAttempterAndroid::ResumeUpdate(Error* error) {
  if (!processor_->IsRunning())
    return LogAndSetGenericError(
        error, __LINE__, __FILE__, "No ongoing update to resume.");
  processor_->ResumeProcessing();
  return true;
}

bool UpdateAttempterAndroid::CancelUpdate(Error* error) {
  if (!processor_->IsRunning())
    return LogAndSetGenericError(
        error, __LINE__, __FILE__, "No ongoing update to cancel.");
  processor_->StopProcessing();
  return true;
}

bool UpdateAttempterAndroid::ResetStatus(Error* error) {
  LOG(INFO) << "Attempting to reset state from "
            << UpdateStatusToString(status_) << " to UpdateStatus::IDLE";
  if (processor_->IsRunning()) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "Already processing an update, cancel it first.");
  }
  if (status_ != UpdateStatus::IDLE &&
      status_ != UpdateStatus::UPDATED_NEED_REBOOT) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "Status reset not allowed in this state, please "
        "cancel on going OTA first.");
  }

  if (apex_handler_android_ != nullptr) {
    LOG(INFO) << "Cleaning up reserved space for compressed APEX (if any)";
    std::vector<ApexInfo> apex_infos_blank;
    apex_handler_android_->AllocateSpace(apex_infos_blank);
  }
  // Remove the reboot marker so that if the machine is rebooted
  // after resetting to idle state, it doesn't go back to
  // UpdateStatus::UPDATED_NEED_REBOOT state.
  if (!ClearUpdateCompletedMarker()) {
    return LogAndSetGenericError(error,
                                 __LINE__,
                                 __FILE__,
                                 "Failed to reset the status because "
                                 "ClearUpdateCompletedMarker() failed");
  }
  if (status_ == UpdateStatus::UPDATED_NEED_REBOOT) {
    if (!resetShouldSwitchSlotOnReboot(error)) {
      LOG(INFO) << "Failed to reset slot switch.";
      return false;
    }
    LOG(INFO) << "Slot switch reset successful";
  }
  if (!boot_control_->GetDynamicPartitionControl()->ResetUpdate(prefs_)) {
    LOG(WARNING) << "Failed to reset snapshots. UpdateStatus is IDLE but"
                 << "space might not be freed.";
  }
  return true;
}

bool operator==(const std::vector<unsigned char>& a, std::string_view b) {
  if (a.size() != b.size()) {
    return false;
  }
  return memcmp(a.data(), b.data(), a.size()) == 0;
}
bool operator!=(const std::vector<unsigned char>& a, std::string_view b) {
  return !(a == b);
}

bool UpdateAttempterAndroid::VerifyPayloadParseManifest(
    const std::string& metadata_filename,
    std::string_view expected_metadata_hash,
    DeltaArchiveManifest* manifest,
    Error* error) {
  FileDescriptorPtr fd(new EintrSafeFileDescriptor);
  if (!fd->Open(metadata_filename.c_str(), O_RDONLY)) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Failed to open " + metadata_filename,
                          ErrorCode::kDownloadManifestParseError);
  }
  brillo::Blob metadata(kMaxPayloadHeaderSize);
  if (!fd->Read(metadata.data(), metadata.size())) {
    return LogAndSetError(
        error,
        __LINE__,
        __FILE__,
        "Failed to read payload header from " + metadata_filename,
        ErrorCode::kDownloadManifestParseError);
  }
  ErrorCode errorcode{};
  PayloadMetadata payload_metadata;
  if (payload_metadata.ParsePayloadHeader(metadata, &errorcode) !=
      MetadataParseResult::kSuccess) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Failed to parse payload header: " +
                              utils::ErrorCodeToString(errorcode),
                          errorcode);
  }
  uint64_t metadata_size = payload_metadata.GetMetadataSize() +
                           payload_metadata.GetMetadataSignatureSize();
  if (metadata_size < kMaxPayloadHeaderSize ||
      metadata_size >
          static_cast<uint64_t>(utils::FileSize(metadata_filename))) {
    return LogAndSetError(
        error,
        __LINE__,
        __FILE__,
        "Invalid metadata size: " + std::to_string(metadata_size),
        ErrorCode::kDownloadManifestParseError);
  }
  metadata.resize(metadata_size);
  if (!fd->Read(metadata.data() + kMaxPayloadHeaderSize,
                metadata.size() - kMaxPayloadHeaderSize)) {
    return LogAndSetError(
        error,
        __LINE__,
        __FILE__,
        "Failed to read metadata and signature from " + metadata_filename,
        ErrorCode::kDownloadManifestParseError);
  }
  fd->Close();
  if (!expected_metadata_hash.empty()) {
    brillo::Blob metadata_hash;
    TEST_AND_RETURN_FALSE(HashCalculator::RawHashOfBytes(
        metadata.data(), payload_metadata.GetMetadataSize(), &metadata_hash));
    if (metadata_hash != expected_metadata_hash) {
      return LogAndSetError(error,
                            __LINE__,
                            __FILE__,
                            "Metadata hash mismatch. Expected hash: " +
                                HexEncode(expected_metadata_hash) +
                                " actual hash: " + HexEncode(metadata_hash),
                            ErrorCode::kDownloadManifestParseError);
    } else {
      LOG(INFO) << "Payload metadata hash check passed : "
                << HexEncode(metadata_hash);
    }
  }

  auto payload_verifier = PayloadVerifier::CreateInstanceFromZipPath(
      constants::kUpdateCertificatesPath);
  if (!payload_verifier) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Failed to create the payload verifier from " +
                              std::string(constants::kUpdateCertificatesPath),
                          ErrorCode::kDownloadManifestParseError);
  }
  errorcode = payload_metadata.ValidateMetadataSignature(
      metadata, "", *payload_verifier);
  if (errorcode != ErrorCode::kSuccess) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Failed to validate metadata signature: " +
                              utils::ErrorCodeToString(errorcode),
                          errorcode);
  }
  if (!payload_metadata.GetManifest(metadata, manifest)) {
    return LogAndSetError(error,
                          __LINE__,
                          __FILE__,
                          "Failed to parse manifest.",
                          ErrorCode::kDownloadManifestParseError);
  }

  return true;
}

bool UpdateAttempterAndroid::VerifyPayloadApplicable(
    const std::string& metadata_filename, Error* error) {
  DeltaArchiveManifest manifest;
  TEST_AND_RETURN_FALSE(
      VerifyPayloadParseManifest(metadata_filename, &manifest, error));

  FileDescriptorPtr fd(new EintrSafeFileDescriptor);
  ErrorCode errorcode{};

  BootControlInterface::Slot current_slot = GetCurrentSlot();
  if (current_slot < 0) {
    return LogAndSetError(
        error,
        __LINE__,
        __FILE__,
        "Failed to get current slot " + std::to_string(current_slot),
        ErrorCode::kDownloadStateInitializationError);
  }
  for (const PartitionUpdate& partition : manifest.partitions()) {
    if (!partition.has_old_partition_info())
      continue;
    string partition_path;
    if (!boot_control_->GetPartitionDevice(
            partition.partition_name(), current_slot, &partition_path)) {
      return LogAndSetGenericError(
          error,
          __LINE__,
          __FILE__,
          "Failed to get partition device for " + partition.partition_name());
    }
    if (!fd->Open(partition_path.c_str(), O_RDONLY)) {
      return LogAndSetGenericError(
          error, __LINE__, __FILE__, "Failed to open " + partition_path);
    }
    for (const InstallOperation& operation : partition.operations()) {
      if (!operation.has_src_sha256_hash())
        continue;
      brillo::Blob source_hash;
      if (!fd_utils::ReadAndHashExtents(fd,
                                        operation.src_extents(),
                                        manifest.block_size(),
                                        &source_hash)) {
        return LogAndSetGenericError(
            error, __LINE__, __FILE__, "Failed to hash " + partition_path);
      }
      if (!PartitionWriter::ValidateSourceHash(
              source_hash, operation, fd, &errorcode)) {
        return false;
      }
    }
    fd->Close();
  }
  return true;
}

bool UpdateAttempterAndroid::SetPerformanceMode(bool enable,
                                                Error* error) {
  LOG(INFO) << (enable ? "Enabling" : "Disabling") << " performance mode.";

  if (performance_mode_ == enable)
    return true;
  bool ret;
  if (enable)
    ret = SetTaskProfiles(0, {"ProcessCapacityMax", "HighIoPriority", "MaxPerformance"});
  else
    ret = SetTaskProfiles(0, {"OtaProfiles"});
  if (!ret)
    return LogAndSetGenericError(error, __LINE__, __FILE__, "Could not change profiles");
  performance_mode_ = enable;
  return true;
}

void UpdateAttempterAndroid::ProcessingDone(const ActionProcessor* processor,
                                            ErrorCode code) {
  LOG(INFO) << "Processing Done.";
  metric_bytes_downloaded_.Flush(true);
  metric_total_bytes_downloaded_.Flush(true);
  last_error_ = code;
  if (status_ == UpdateStatus::CLEANUP_PREVIOUS_UPDATE) {
    TerminateUpdateAndNotify(code);
    return;
  }

  switch (code) {
    case ErrorCode::kSuccess:
      // Update succeeded.
      if (!WriteUpdateCompletedMarker()) {
        LOG(ERROR) << "Failed to write update completion marker";
      }
      prefs_->SetInt64(kPrefsDeltaUpdateFailures, 0);

      LOG(INFO) << "Update successfully applied, waiting to reboot.";
      break;

    case ErrorCode::kFilesystemCopierError:
    case ErrorCode::kNewRootfsVerificationError:
    case ErrorCode::kNewKernelVerificationError:
    case ErrorCode::kFilesystemVerifierError:
    case ErrorCode::kDownloadStateInitializationError:
      // Reset the ongoing update for these errors so it starts from the
      // beginning next time.
      DeltaPerformer::ResetUpdateProgress(prefs_, false);
      LOG(INFO) << "Resetting update progress.";
      break;

    case ErrorCode::kPayloadTimestampError:
      // SafetyNet logging, b/36232423
      android_errorWriteLog(0x534e4554, "36232423");
      break;

    default:
      // Ignore all other error codes.
      break;
  }

  TerminateUpdateAndNotify(code);
}

void UpdateAttempterAndroid::ProcessingStopped(
    const ActionProcessor* processor) {
  TerminateUpdateAndNotify(ErrorCode::kUserCanceled);
}

void UpdateAttempterAndroid::ActionCompleted(ActionProcessor* processor,
                                             AbstractAction* action,
                                             ErrorCode code) {
  // Reset download progress regardless of whether or not the download
  // action succeeded.
  const string type = action->Type();
  if (type == CleanupPreviousUpdateAction::StaticType() ||
      (type == NoOpAction::StaticType() &&
       status_ == UpdateStatus::CLEANUP_PREVIOUS_UPDATE)) {
    cleanup_previous_update_code_ = code;
    NotifyCleanupPreviousUpdateCallbacksAndClear();
  }
  // download_progress_ is actually used by other actions, such as
  // filesystem_verify_action. Therefore we always clear it.
  download_progress_ = 0;
  if (type == PostinstallRunnerAction::StaticType()) {
    bool succeeded =
        code == ErrorCode::kSuccess || code == ErrorCode::kUpdatedButNotActive;
    prefs_->SetBoolean(kPrefsPostInstallSucceeded, succeeded);
  }
  if (code != ErrorCode::kSuccess) {
    // If an action failed, the ActionProcessor will cancel the whole thing.
    return;
  }
  if (type == UpdateBootFlagsAction::StaticType()) {
    SetStatusAndNotify(UpdateStatus::CLEANUP_PREVIOUS_UPDATE);
  }
  if (type == DownloadAction::StaticType()) {
    auto download_action = static_cast<DownloadAction*>(action);
    install_plan_ = *download_action->install_plan();
    SetStatusAndNotify(UpdateStatus::VERIFYING);
  } else if (type == FilesystemVerifierAction::StaticType()) {
    SetStatusAndNotify(UpdateStatus::FINALIZING);
    prefs_->SetBoolean(kPrefsVerityWritten, true);
  }
}

void UpdateAttempterAndroid::BytesReceived(uint64_t bytes_progressed,
                                           uint64_t bytes_received,
                                           uint64_t total) {
  double progress = 0;
  if (total)
    progress = static_cast<double>(bytes_received) / static_cast<double>(total);
  if (status_ != UpdateStatus::DOWNLOADING || bytes_received == total) {
    download_progress_ = progress;
    SetStatusAndNotify(UpdateStatus::DOWNLOADING);
  } else {
    ProgressUpdate(progress);
  }

  // Update the bytes downloaded in prefs.
  metric_bytes_downloaded_ += bytes_progressed;
  metric_total_bytes_downloaded_ += bytes_progressed;
}

bool UpdateAttempterAndroid::ShouldCancel(ErrorCode* cancel_reason) {
  // TODO(deymo): Notify the DownloadAction that it should cancel the update
  // download.
  return false;
}

void UpdateAttempterAndroid::DownloadComplete() {
  // Nothing needs to be done when the download completes.
}

void UpdateAttempterAndroid::ProgressUpdate(double progress) {
  // Self throttle based on progress. Also send notifications if progress is
  // too slow.
  if (progress == 1.0 ||
      progress - download_progress_ >= kBroadcastThresholdProgress ||
      TimeTicks::Now() - last_notify_time_ >=
          TimeDelta::FromSeconds(kBroadcastThresholdSeconds)) {
    download_progress_ = progress;
    SetStatusAndNotify(status_);
  }
}

void UpdateAttempterAndroid::OnVerifyProgressUpdate(double progress) {
  assert(status_ == UpdateStatus::VERIFYING);
  ProgressUpdate(progress);
}

void UpdateAttempterAndroid::ScheduleProcessingStart() {
  LOG(INFO) << "Scheduling an action processor start.";
  processor_->set_delegate(this);
  brillo::MessageLoop::current()->PostTask(
      FROM_HERE,
      Bind([](ActionProcessor* processor) { processor->StartProcessing(); },
           base::Unretained(processor_.get())));
}

void UpdateAttempterAndroid::TerminateUpdateAndNotify(ErrorCode error_code) {
  if (status_ == UpdateStatus::IDLE) {
    LOG(ERROR) << "No ongoing update, but TerminatedUpdate() called.";
    return;
  }

  if (status_ == UpdateStatus::CLEANUP_PREVIOUS_UPDATE) {
    ClearUpdateCompletedMarker();
    LOG(INFO) << "Terminating cleanup previous update.";
    SetStatusAndNotify(UpdateStatus::IDLE);
    for (auto observer : daemon_state_->service_observers())
      observer->SendPayloadApplicationComplete(error_code);
    return;
  }

  boot_control_->GetDynamicPartitionControl()->Cleanup();

  download_progress_ = 0;
  UpdateStatus new_status =
      (error_code == ErrorCode::kSuccess ? UpdateStatus::UPDATED_NEED_REBOOT
                                         : UpdateStatus::IDLE);
  SetStatusAndNotify(new_status);
  payload_fd_.reset();

  // The network id is only applicable to one download attempt and once it's
  // done the network id should not be re-used anymore.
  if (!network_selector_->SetProcessNetwork(kDefaultNetworkId)) {
    LOG(WARNING) << "Unable to unbind network.";
  }

  for (auto observer : daemon_state_->service_observers())
    observer->SendPayloadApplicationComplete(error_code);

  CollectAndReportUpdateMetricsOnUpdateFinished(error_code);
  ClearMetricsPrefs();
  if (error_code == ErrorCode::kSuccess) {
    // We should only reset the PayloadAttemptNumber if the update succeeds, or
    // we switch to a different payload.
    prefs_->Delete(kPrefsPayloadAttemptNumber);
    metrics_utils::SetSystemUpdatedMarker(clock_.get(), prefs_);
    // Clear the total bytes downloaded if and only if the update succeeds.
    metric_total_bytes_downloaded_.Delete();
  }
}

void UpdateAttempterAndroid::SetStatusAndNotify(UpdateStatus status) {
  status_ = status;
  size_t payload_size =
      install_plan_.payloads.empty() ? 0 : install_plan_.payloads[0].size;
  UpdateEngineStatus status_to_send = {.status = status_,
                                       .progress = download_progress_,
                                       .new_size_bytes = payload_size};

  for (auto observer : daemon_state_->service_observers()) {
    observer->SendStatusUpdate(status_to_send);
  }
  last_notify_time_ = TimeTicks::Now();
}

void UpdateAttempterAndroid::BuildUpdateActions(HttpFetcher* fetcher) {
  CHECK(!processor_->IsRunning());

  // Actions:
  auto update_boot_flags_action =
      std::make_unique<UpdateBootFlagsAction>(boot_control_);
  auto cleanup_previous_update_action =
      boot_control_->GetDynamicPartitionControl()
          ->GetCleanupPreviousUpdateAction(boot_control_, prefs_, this);
  auto install_plan_action = std::make_unique<InstallPlanAction>(install_plan_);
  auto download_action =
      std::make_unique<DownloadAction>(prefs_,
                                       boot_control_,
                                       hardware_,
                                       fetcher,  // passes ownership
                                       true /* interactive */,
                                       update_certificates_path_);
  download_action->set_delegate(this);
  download_action->set_base_offset(base_offset_);
  auto filesystem_verifier_action = std::make_unique<FilesystemVerifierAction>(
      boot_control_->GetDynamicPartitionControl());
  auto postinstall_runner_action =
      std::make_unique<PostinstallRunnerAction>(boot_control_, hardware_);
  filesystem_verifier_action->set_delegate(this);
  postinstall_runner_action->set_delegate(this);

  // Bond them together. We have to use the leaf-types when calling
  // BondActions().
  BondActions(install_plan_action.get(), download_action.get());
  BondActions(download_action.get(), filesystem_verifier_action.get());
  BondActions(filesystem_verifier_action.get(),
              postinstall_runner_action.get());

  processor_->EnqueueAction(std::move(update_boot_flags_action));
  processor_->EnqueueAction(std::move(cleanup_previous_update_action));
  processor_->EnqueueAction(std::move(install_plan_action));
  processor_->EnqueueAction(std::move(download_action));
  processor_->EnqueueAction(std::move(filesystem_verifier_action));
  processor_->EnqueueAction(std::move(postinstall_runner_action));
}

bool UpdateAttempterAndroid::WriteUpdateCompletedMarker() {
  string boot_id;
  TEST_AND_RETURN_FALSE(utils::GetBootId(&boot_id));
  LOG(INFO) << "Writing update complete marker, slot "
            << boot_control_->GetCurrentSlot() << ", boot id: " << boot_id;
  TEST_AND_RETURN_FALSE(
      prefs_->SetString(kPrefsUpdateCompletedOnBootId, boot_id));
  TEST_AND_RETURN_FALSE(
      prefs_->SetInt64(kPrefsPreviousSlot, boot_control_->GetCurrentSlot()));
  return true;
}

bool UpdateAttempterAndroid::ClearUpdateCompletedMarker() {
  LOG(INFO) << "Clearing update complete marker.";
  TEST_AND_RETURN_FALSE(prefs_->Delete(kPrefsUpdateCompletedOnBootId));
  TEST_AND_RETURN_FALSE(prefs_->Delete(kPrefsPreviousSlot));
  return true;
}

bool UpdateAttempterAndroid::UpdateCompletedOnThisBoot() const {
  // In case of an update_engine restart without a reboot, we stored the boot_id
  // when the update was completed by setting a pref, so we can check whether
  // the last update was on this boot or a previous one.
  string boot_id;
  TEST_AND_RETURN_FALSE(utils::GetBootId(&boot_id));

  string update_completed_on_boot_id;
  return (prefs_->Exists(kPrefsUpdateCompletedOnBootId) &&
          prefs_->GetString(kPrefsUpdateCompletedOnBootId,
                            &update_completed_on_boot_id) &&
          update_completed_on_boot_id == boot_id);
}

// Collect and report the android metrics when we terminate the update.
void UpdateAttempterAndroid::CollectAndReportUpdateMetricsOnUpdateFinished(
    ErrorCode error_code) {
  int64_t attempt_number =
      metrics_utils::GetPersistedValue(kPrefsPayloadAttemptNumber, prefs_);
  PayloadType payload_type = kPayloadTypeFull;
  int64_t payload_size = 0;
  for (const auto& p : install_plan_.payloads) {
    if (p.type == InstallPayloadType::kDelta)
      payload_type = kPayloadTypeDelta;
    payload_size += p.size;
  }
  // In some cases, e.g. after calling |setShouldSwitchSlotOnReboot()|,  this
  // function will be triggered, but payload_size in this case might be 0, if so
  // skip reporting any metrics.
  if (payload_size == 0) {
    return;
  }

  metrics::AttemptResult attempt_result =
      metrics_utils::GetAttemptResult(error_code);
  Time boot_time_start = Time::FromInternalValue(
      metrics_utils::GetPersistedValue(kPrefsUpdateBootTimestampStart, prefs_));
  Time monotonic_time_start = Time::FromInternalValue(
      metrics_utils::GetPersistedValue(kPrefsUpdateTimestampStart, prefs_));
  TimeDelta duration = clock_->GetBootTime() - boot_time_start;
  TimeDelta duration_uptime = clock_->GetMonotonicTime() - monotonic_time_start;

  metrics_reporter_->ReportUpdateAttemptMetrics(
      static_cast<int>(attempt_number),
      payload_type,
      duration,
      duration_uptime,
      payload_size,
      attempt_result,
      error_code);

  int64_t current_bytes_downloaded = metric_bytes_downloaded_.get();
  metrics_reporter_->ReportUpdateAttemptDownloadMetrics(
      current_bytes_downloaded,
      0,
      DownloadSource::kNumDownloadSources,
      metrics::DownloadErrorCode::kUnset,
      metrics::ConnectionType::kUnset);

  if (error_code == ErrorCode::kSuccess) {
    int64_t reboot_count =
        metrics_utils::GetPersistedValue(kPrefsNumReboots, prefs_);
    string build_version;
    prefs_->GetString(kPrefsPreviousVersion, &build_version);

    // For android metrics, we only care about the total bytes downloaded
    // for all sources; for now we assume the only download source is
    // HttpsServer.
    int64_t total_bytes_downloaded = metric_total_bytes_downloaded_.get();
    int64_t num_bytes_downloaded[kNumDownloadSources] = {};
    num_bytes_downloaded[DownloadSource::kDownloadSourceHttpsServer] =
        total_bytes_downloaded;

    int download_overhead_percentage = 0;
    if (total_bytes_downloaded >= payload_size) {
      CHECK_GT(payload_size, 0);
      download_overhead_percentage =
          (total_bytes_downloaded - payload_size) * 100ull / payload_size;
    } else {
      LOG(WARNING) << "Downloaded bytes " << total_bytes_downloaded
                   << " is smaller than the payload size " << payload_size;
    }

    metrics_reporter_->ReportSuccessfulUpdateMetrics(
        static_cast<int>(attempt_number),
        0,  // update abandoned count
        payload_type,
        payload_size,
        num_bytes_downloaded,
        download_overhead_percentage,
        duration,
        duration_uptime,
        static_cast<int>(reboot_count),
        0);  // url_switch_count
  }
}

bool UpdateAttempterAndroid::OTARebootSucceeded() const {
  const auto current_slot = boot_control_->GetCurrentSlot();
  const string current_version = GetCurrentBuildVersion();
  int64_t previous_slot = -1;
  TEST_AND_RETURN_FALSE(prefs_->GetInt64(kPrefsPreviousSlot, &previous_slot));
  string previous_version;
  TEST_AND_RETURN_FALSE(
      prefs_->GetString(kPrefsPreviousVersion, &previous_version));
  if (previous_slot != current_slot) {
    LOG(INFO) << "Detected a slot switch, OTA succeeded, device updated from "
              << previous_version << " to " << current_version
              << ", previous slot: " << previous_slot
              << " current slot: " << current_slot;
    if (previous_version == current_version) {
      LOG(INFO) << "Previous version is the same as current version, this is "
                   "possibly a self-OTA.";
    }
    return true;
  } else {
    LOG(INFO) << "Slot didn't switch, either the OTA is rolled back, or slot "
                 "switch never happened, or system not rebooted at all.";
    if (previous_version != current_version) {
      LOG(INFO) << "Slot didn't change, but version changed from "
                << previous_version << " to " << current_version
                << " device could be flashed.";
    }
    return false;
  }
}

OTAResult UpdateAttempterAndroid::GetOTAUpdateResult() const {
  // We only set |kPrefsSystemUpdatedMarker| if slot is actually switched, so
  // existence of this pref is sufficient indicator. Given that we have to
  // delete this pref after checking it. This is done in
  // |DeltaPerformer::ResetUpdateProgress| and
  // |UpdateAttempterAndroid::UpdateStateAfterReboot|
  auto slot_switch_attempted = prefs_->Exists(kPrefsUpdateCompletedOnBootId);
  auto system_rebooted = DidSystemReboot(prefs_);
  auto ota_successful = OTARebootSucceeded();
  if (ota_successful) {
    return OTAResult::OTA_SUCCESSFUL;
  }
  if (slot_switch_attempted) {
    if (system_rebooted) {
      // If we attempted slot switch, but still end up on the same slot, we
      // probably rolled back.
      return OTAResult::ROLLED_BACK;
    } else {
      return OTAResult::UPDATED_NEED_REBOOT;
    }
  }
  return OTAResult::NOT_ATTEMPTED;
}

void UpdateAttempterAndroid::UpdateStateAfterReboot(const OTAResult result) {
  const string current_version = GetCurrentBuildVersion();
  TEST_AND_RETURN(!current_version.empty());

  // |UpdateStateAfterReboot()| is only called after system reboot, so record
  // boot id unconditionally
  string current_boot_id;
  TEST_AND_RETURN(utils::GetBootId(&current_boot_id));
  prefs_->SetString(kPrefsBootId, current_boot_id);
  std::string slot_switch_indicator;
  prefs_->GetString(kPrefsUpdateCompletedOnBootId, &slot_switch_indicator);
  if (slot_switch_indicator != current_boot_id) {
    ClearUpdateCompletedMarker();
  }

  // If there's no record of previous version (e.g. due to a data wipe), we
  // save the info of current boot and skip the metrics report.
  if (!prefs_->Exists(kPrefsPreviousVersion)) {
    prefs_->SetString(kPrefsPreviousVersion, current_version);
    prefs_->SetInt64(kPrefsPreviousSlot, boot_control_->GetCurrentSlot());
    ClearMetricsPrefs();
    return;
  }
  // update_engine restarted under the same build and same slot.
  if (result != OTAResult::OTA_SUCCESSFUL) {
    // Increment the reboot number if |kPrefsNumReboots| exists. That pref is
    // set when we start a new update.
    if (prefs_->Exists(kPrefsNumReboots)) {
      int64_t reboot_count =
          metrics_utils::GetPersistedValue(kPrefsNumReboots, prefs_);
      metrics_utils::SetNumReboots(reboot_count + 1, prefs_);
    }

    if (result == OTAResult::ROLLED_BACK) {
      // This will release all space previously allocated for apex
      // decompression. If we detect a rollback, we should release space and
      // return the space to user. Any subsequent attempt to install OTA will
      // allocate space again anyway.
      LOG(INFO) << "Detected a rollback, releasing space allocated for apex "
                   "deompression.";
      apex_handler_android_->AllocateSpace({});
      DeltaPerformer::ResetUpdateProgress(prefs_, false);
    }
    return;
  }

  // Now that the build version changes, report the update metrics.
  // TODO(xunchang) check the build version is larger than the previous one.
  prefs_->SetString(kPrefsPreviousVersion, current_version);
  prefs_->SetInt64(kPrefsPreviousSlot, boot_control_->GetCurrentSlot());

  bool previous_attempt_exists = prefs_->Exists(kPrefsPayloadAttemptNumber);
  // |kPrefsPayloadAttemptNumber| should be cleared upon successful update.
  if (previous_attempt_exists) {
    metrics_reporter_->ReportAbnormallyTerminatedUpdateAttemptMetrics();
  }

  metrics_utils::LoadAndReportTimeToReboot(
      metrics_reporter_.get(), prefs_, clock_.get());
  ClearMetricsPrefs();

  // Also reset the update progress if the build version has changed.
  if (!DeltaPerformer::ResetUpdateProgress(prefs_, false)) {
    LOG(WARNING) << "Unable to reset the update progress.";
  }
}

// Save the update start time. Reset the reboot count and attempt number if the
// update isn't a resume; otherwise increment the attempt number.
void UpdateAttempterAndroid::UpdatePrefsOnUpdateStart(bool is_resume) {
  if (!is_resume) {
    metrics_utils::SetNumReboots(0, prefs_);
    metrics_utils::SetPayloadAttemptNumber(1, prefs_);
  } else {
    int64_t attempt_number =
        metrics_utils::GetPersistedValue(kPrefsPayloadAttemptNumber, prefs_);
    metrics_utils::SetPayloadAttemptNumber(attempt_number + 1, prefs_);
  }
  metrics_utils::SetUpdateTimestampStart(clock_->GetMonotonicTime(), prefs_);
  metrics_utils::SetUpdateBootTimestampStart(clock_->GetBootTime(), prefs_);
  ClearUpdateCompletedMarker();
}

void UpdateAttempterAndroid::ClearMetricsPrefs() {
  CHECK(prefs_);
  metric_bytes_downloaded_.Delete();
  prefs_->Delete(kPrefsNumReboots);
  prefs_->Delete(kPrefsSystemUpdatedMarker);
  prefs_->Delete(kPrefsUpdateTimestampStart);
  prefs_->Delete(kPrefsUpdateBootTimestampStart);
}

BootControlInterface::Slot UpdateAttempterAndroid::GetCurrentSlot() const {
  return boot_control_->GetCurrentSlot();
}

BootControlInterface::Slot UpdateAttempterAndroid::GetTargetSlot() const {
  return GetCurrentSlot() == 0 ? 1 : 0;
}

uint64_t UpdateAttempterAndroid::AllocateSpaceForPayload(
    const std::string& metadata_filename,
    const vector<string>& key_value_pair_headers,
    Error* error) {
  std::map<string, string> headers;
  if (!ParseKeyValuePairHeaders(key_value_pair_headers, &headers, error)) {
    return 0;
  }
  DeltaArchiveManifest manifest;
  brillo::Blob metadata_hash;
  if (!brillo::data_encoding::Base64Decode(
          headers[kPayloadPropertyMetadataHash], &metadata_hash)) {
    metadata_hash.clear();
  }
  if (!VerifyPayloadParseManifest(
          metadata_filename, ToStringView(metadata_hash), &manifest, error)) {
    return 0;
  }

  std::vector<ApexInfo> apex_infos(manifest.apex_info().begin(),
                                   manifest.apex_info().end());
  uint64_t apex_size_required = 0;
  if (apex_handler_android_ != nullptr) {
    auto result = apex_handler_android_->CalculateSize(apex_infos);
    if (!result.ok()) {
      LogAndSetGenericError(
          error,
          __LINE__,
          __FILE__,
          "Failed to calculate size required for compressed APEX");
      return 0;
    }
    apex_size_required = *result;
  }

  string payload_id = GetPayloadId(headers);
  uint64_t required_size = 0;
  ErrorCode error_code{};

  if (!DeltaPerformer::PreparePartitionsForUpdate(prefs_,
                                                  boot_control_,
                                                  GetTargetSlot(),
                                                  manifest,
                                                  payload_id,
                                                  &required_size,
                                                  &error_code)) {
    if (error_code == ErrorCode::kOverlayfsenabledError) {
      LogAndSetError(error,
                     __LINE__,
                     __FILE__,
                     "OverlayFS Shouldn't be enabled for OTA.",
                     error_code);
      return 0;
    }
    if (required_size == 0) {
      LogAndSetGenericError(
          error, __LINE__, __FILE__, "Failed to allocate space for payload.");
      return 0;
    } else {
      LOG(ERROR) << "Insufficient space for payload: " << required_size
                 << " bytes, apex decompression: " << apex_size_required
                 << " bytes";
      return required_size + apex_size_required;
    }
  }

  if (apex_size_required > 0 && apex_handler_android_ != nullptr &&
      !apex_handler_android_->AllocateSpace(apex_infos)) {
    LOG(ERROR) << "Insufficient space for apex decompression: "
               << apex_size_required << " bytes";
    return apex_size_required;
  }

  LOG(INFO) << "Successfully allocated space for payload.";
  return 0;
}

void UpdateAttempterAndroid::CleanupSuccessfulUpdate(
    std::unique_ptr<CleanupSuccessfulUpdateCallbackInterface> callback,
    Error* error) {
  if (cleanup_previous_update_code_.has_value()) {
    LOG(INFO) << "CleanupSuccessfulUpdate has previously completed with "
              << utils::ErrorCodeToString(*cleanup_previous_update_code_);
    if (callback) {
      callback->OnCleanupComplete(
          static_cast<int32_t>(*cleanup_previous_update_code_));
    }
    return;
  }
  if (callback) {
    auto callback_ptr = callback.get();
    cleanup_previous_update_callbacks_.emplace_back(std::move(callback));
    callback_ptr->RegisterForDeathNotifications([this, callback_ptr]() {
      RemoveCleanupPreviousUpdateCallback(callback_ptr);
    });
  }
  ScheduleCleanupPreviousUpdate();
}

bool UpdateAttempterAndroid::setShouldSwitchSlotOnReboot(
    const std::string& metadata_filename, Error* error) {
  LOG(INFO) << "setShouldSwitchSlotOnReboot(" << metadata_filename << ")";
  if (processor_->IsRunning()) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "Already processing an update, cancel it first.");
  }
  DeltaArchiveManifest manifest;
  TEST_AND_RETURN_FALSE(
      VerifyPayloadParseManifest(metadata_filename, &manifest, error));

  InstallPlan install_plan_;
  install_plan_.source_slot = GetCurrentSlot();
  install_plan_.target_slot = GetTargetSlot();
  // Don't do verity computation, just hash the partitions
  install_plan_.write_verity = false;
  // Don't run postinstall, we just need PostinstallAction to switch the slots.
  install_plan_.run_post_install = false;
  install_plan_.is_resume = true;

  CHECK_NE(install_plan_.source_slot, UINT32_MAX);
  CHECK_NE(install_plan_.target_slot, UINT32_MAX);

  auto postinstall_runner_action =
      std::make_unique<PostinstallRunnerAction>(boot_control_, hardware_);
  postinstall_runner_action->set_delegate(this);

  // If last error code is kUpdatedButNotActive, we know that we reached this
  // state by calling applyPayload() with switch_slot=false. That applyPayload()
  // call would have already performed filesystem verification, therefore, we
  // can safely skip the verification to save time.
  if (last_error_ == ErrorCode::kUpdatedButNotActive) {
    auto install_plan_action =
        std::make_unique<InstallPlanAction>(install_plan_);
    BondActions(install_plan_action.get(), postinstall_runner_action.get());
    processor_->EnqueueAction(std::move(install_plan_action));
    SetStatusAndNotify(UpdateStatus::FINALIZING);
  } else {
    ErrorCode error_code{};
    if (!boot_control_->GetDynamicPartitionControl()
             ->PreparePartitionsForUpdate(GetCurrentSlot(),
                                          GetTargetSlot(),
                                          manifest,
                                          false /* should update */,
                                          nullptr,
                                          &error_code)) {
      return LogAndSetGenericError(
          error, __LINE__, __FILE__, "Failed to PreparePartitionsForUpdate");
    }
    if (!install_plan_.ParsePartitions(manifest.partitions(),
                                       boot_control_,
                                       manifest.block_size(),
                                       &error_code)) {
      return LogAndSetError(error,
                            __LINE__,
                            __FILE__,
                            "Failed to LoadPartitionsFromSlots " +
                                utils::ErrorCodeToString(error_code),
                            error_code);
    }
    auto install_plan_action =
        std::make_unique<InstallPlanAction>(install_plan_);
    auto filesystem_verifier_action =
        std::make_unique<FilesystemVerifierAction>(
            boot_control_->GetDynamicPartitionControl());
    filesystem_verifier_action->set_delegate(this);
    BondActions(install_plan_action.get(), filesystem_verifier_action.get());
    BondActions(filesystem_verifier_action.get(),
                postinstall_runner_action.get());
    processor_->EnqueueAction(std::move(install_plan_action));
    processor_->EnqueueAction(std::move(filesystem_verifier_action));
    SetStatusAndNotify(UpdateStatus::VERIFYING);
  }

  processor_->EnqueueAction(std::move(postinstall_runner_action));
  ScheduleProcessingStart();
  return true;
}

bool UpdateAttempterAndroid::resetShouldSwitchSlotOnReboot(Error* error) {
  if (processor_->IsRunning()) {
    return LogAndSetGenericError(
        error,
        __LINE__,
        __FILE__,
        "Already processing an update, cancel it first.");
  }
  TEST_AND_RETURN_FALSE(ClearUpdateCompletedMarker());
  // Update the boot flags so the current slot has higher priority.
  if (!boot_control_->SetActiveBootSlot(GetCurrentSlot())) {
    return LogAndSetGenericError(
        error, __LINE__, __FILE__, "Failed to SetActiveBootSlot");
  }

  // Mark the current slot as successful again, since marking it as active
  // may reset the successful bit. We ignore the result of whether marking
  // the current slot as successful worked.
  if (!boot_control_->MarkBootSuccessfulAsync(Bind([](bool successful) {}))) {
    return LogAndSetGenericError(
        error, __LINE__, __FILE__, "Failed to MarkBootSuccessfulAsync");
  }

  // Resets the warm reset property since we won't switch the slot.
  hardware_->SetWarmReset(false);

  // Resets the vbmeta digest.
  hardware_->SetVbmetaDigestForInactiveSlot(true /* reset */);
  LOG(INFO) << "Slot switch cancelled.";
  SetStatusAndNotify(UpdateStatus::IDLE);
  return true;
}

void UpdateAttempterAndroid::ScheduleCleanupPreviousUpdate() {
  // If a previous CleanupSuccessfulUpdate call has not finished, or an update
  // is in progress, skip enqueueing the action.
  if (processor_->IsRunning()) {
    LOG(INFO) << "Already processing an update. CleanupPreviousUpdate should "
              << "be done when the current update finishes.";
    return;
  }
  LOG(INFO) << "Scheduling CleanupPreviousUpdateAction.";
  auto action =
      boot_control_->GetDynamicPartitionControl()
          ->GetCleanupPreviousUpdateAction(boot_control_, prefs_, this);
  processor_->EnqueueAction(std::move(action));
  processor_->set_delegate(this);
  SetStatusAndNotify(UpdateStatus::CLEANUP_PREVIOUS_UPDATE);
  processor_->StartProcessing();
}

void UpdateAttempterAndroid::OnCleanupProgressUpdate(double progress) {
  for (auto&& callback : cleanup_previous_update_callbacks_) {
    callback->OnCleanupProgressUpdate(progress);
  }
}

void UpdateAttempterAndroid::NotifyCleanupPreviousUpdateCallbacksAndClear() {
  CHECK(cleanup_previous_update_code_.has_value());
  for (auto&& callback : cleanup_previous_update_callbacks_) {
    callback->OnCleanupComplete(
        static_cast<int32_t>(*cleanup_previous_update_code_));
  }
  cleanup_previous_update_callbacks_.clear();
}

void UpdateAttempterAndroid::RemoveCleanupPreviousUpdateCallback(
    CleanupSuccessfulUpdateCallbackInterface* callback) {
  auto end_it =
      std::remove_if(cleanup_previous_update_callbacks_.begin(),
                     cleanup_previous_update_callbacks_.end(),
                     [&](const auto& e) { return e.get() == callback; });
  cleanup_previous_update_callbacks_.erase(
      end_it, cleanup_previous_update_callbacks_.end());
}

bool UpdateAttempterAndroid::IsProductionBuild() {
  if (android::base::GetProperty("ro.build.type", "") != "userdebug" ||
      android::base::GetProperty("ro.build.tags", "") == "release-keys" ||
      android::base::GetProperty("ro.boot.verifiedbootstate", "") == "green") {
    return true;
  }
  return false;
}

}  // namespace chromeos_update_engine
