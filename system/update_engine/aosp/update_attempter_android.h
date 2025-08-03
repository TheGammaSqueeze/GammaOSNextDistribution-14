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

#ifndef UPDATE_ENGINE_AOSP_UPDATE_ATTEMPTER_ANDROID_H_
#define UPDATE_ENGINE_AOSP_UPDATE_ATTEMPTER_ANDROID_H_

#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

#include <android-base/unique_fd.h>
#include <base/time/time.h>

#include "update_engine/aosp/apex_handler_interface.h"
#include "update_engine/aosp/service_delegate_android_interface.h"
#include "update_engine/client_library/include/update_engine/update_status.h"
#include "update_engine/common/action_processor.h"
#include "update_engine/common/boot_control_interface.h"
#include "update_engine/common/clock_interface.h"
#include "update_engine/common/daemon_state_interface.h"
#include "update_engine/common/download_action.h"
#include "update_engine/common/error_code.h"
#include "update_engine/common/hardware_interface.h"
#include "update_engine/common/metrics_reporter_interface.h"
#include "update_engine/common/network_selector_interface.h"
#include "update_engine/common/prefs_interface.h"
#include "update_engine/metrics_utils.h"
#include "update_engine/payload_consumer/filesystem_verifier_action.h"
#include "update_engine/payload_consumer/postinstall_runner_action.h"

namespace chromeos_update_engine {

enum class OTAResult {
  NOT_ATTEMPTED,
  ROLLED_BACK,
  UPDATED_NEED_REBOOT,
  OTA_SUCCESSFUL,
};

class UpdateAttempterAndroid
    : public ServiceDelegateAndroidInterface,
      public ActionProcessorDelegate,
      public DownloadActionDelegate,
      public FilesystemVerifyDelegate,
      public PostinstallRunnerAction::DelegateInterface,
      public CleanupPreviousUpdateActionDelegateInterface {
 public:
  using UpdateStatus = update_engine::UpdateStatus;

  UpdateAttempterAndroid(DaemonStateInterface* daemon_state,
                         PrefsInterface* prefs,
                         BootControlInterface* boot_control_,
                         HardwareInterface* hardware_,
                         std::unique_ptr<ApexHandlerInterface> apex_handler);
  ~UpdateAttempterAndroid() override;

  // Further initialization to be done post construction.
  void Init();

  // ServiceDelegateAndroidInterface overrides.
  bool ApplyPayload(const std::string& payload_url,
                    int64_t payload_offset,
                    int64_t payload_size,
                    const std::vector<std::string>& key_value_pair_headers,
                    Error* error) override;
  bool ApplyPayload(int fd,
                    int64_t payload_offset,
                    int64_t payload_size,
                    const std::vector<std::string>& key_value_pair_headers,
                    Error* error) override;
  bool SuspendUpdate(Error* error) override;
  bool ResumeUpdate(Error* error) override;
  bool CancelUpdate(Error* error) override;
  bool ResetStatus(Error* error) override;
  bool VerifyPayloadApplicable(const std::string& metadata_filename,
                               Error* error) override;
  uint64_t AllocateSpaceForPayload(
      const std::string& metadata_filename,
      const std::vector<std::string>& key_value_pair_headers,
      Error* error) override;
  void CleanupSuccessfulUpdate(
      std::unique_ptr<CleanupSuccessfulUpdateCallbackInterface> callback,
      Error* error) override;
  bool setShouldSwitchSlotOnReboot(const std::string& metadata_filename,
                                   Error* error) override;
  bool resetShouldSwitchSlotOnReboot(Error* error) override;

  bool SetPerformanceMode(bool enable, Error* error) override;

  // ActionProcessorDelegate methods:
  void ProcessingDone(const ActionProcessor* processor,
                      ErrorCode code) override;
  void ProcessingStopped(const ActionProcessor* processor) override;
  void ActionCompleted(ActionProcessor* processor,
                       AbstractAction* action,
                       ErrorCode code) override;

  // DownloadActionDelegate overrides.
  void BytesReceived(uint64_t bytes_progressed,
                     uint64_t bytes_received,
                     uint64_t total) override;
  bool ShouldCancel(ErrorCode* cancel_reason) override;
  void DownloadComplete() override;

  // FilesystemVerifyDelegate overrides
  void OnVerifyProgressUpdate(double progress) override;

  // PostinstallRunnerAction::DelegateInterface
  void ProgressUpdate(double progress) override;

  // CleanupPreviousUpdateActionDelegateInterface
  void OnCleanupProgressUpdate(double progress) override;

  // Check the result of an OTA update. Intended to be called after reboot, this
  // will use prefs on disk to determine if OTA was installed, or rolledback.
  [[nodiscard]] OTAResult GetOTAUpdateResult() const;
  // Intended to be called:
  // 1. When system rebooted and slot switch is attempted
  // 2. When a new update is started
  // 3. When user called |ResetStatus()|
  bool ClearUpdateCompletedMarker();

  void set_update_certificates_path(
      const std::string& update_certificates_path) {
    update_certificates_path_ = update_certificates_path;
  }

 private:
  friend class UpdateAttempterAndroidTest;

  // Return |true| only if slot switched successfully after an OTA reboot.
  // This will return |false| if an downgrade OTA is applied. Because after a
  // downgrade OTA, we wipe /data, and there's no way for update_engine to
  // "remember" that a downgrade OTA took place.
  [[nodiscard]] bool OTARebootSucceeded() const;

  // Schedules an event loop callback to start the action processor. This is
  // scheduled asynchronously to unblock the event loop.
  void ScheduleProcessingStart();

  // Notifies an update request completed with the given error |code| to all
  // observers.
  void TerminateUpdateAndNotify(ErrorCode error_code);

  // Sets the status to the given |status| and notifies a status update to
  // all observers.
  void SetStatusAndNotify(UpdateStatus status);

  // Helper method to construct the sequence of actions to be performed for
  // applying an update using a given HttpFetcher. The ownership of |fetcher| is
  // passed to this function.
  void BuildUpdateActions(HttpFetcher* fetcher);

  // Writes to the processing completed marker. Does nothing if
  // |update_completed_marker_| is empty.
  [[nodiscard]] bool WriteUpdateCompletedMarker();

  // Returns whether a slot switch was attempted in the current boot.
  [[nodiscard]] bool UpdateCompletedOnThisBoot() const;

  // Prefs to use for metrics report
  // |kPrefsPayloadAttemptNumber|: number of update attempts for the current
  // payload_id.
  // |KprefsNumReboots|: number of reboots when applying the current update.
  // |kPrefsSystemUpdatedMarker|: end timestamp of the last successful update.
  // |kPrefsUpdateTimestampStart|: start timestamp in monotonic time of the
  // current update.
  // |kPrefsUpdateBootTimestampStart|: start timestamp in boot time of
  // the current update.
  // |kPrefsCurrentBytesDownloaded|: number of bytes downloaded for the current
  // payload_id.
  // |kPrefsTotalBytesDownloaded|: number of bytes downloaded in total since
  // the last successful update.

  // Metrics report function to call:
  //   |ReportUpdateAttemptMetrics|
  //   |ReportSuccessfulUpdateMetrics|
  // Prefs to update:
  //   |kPrefsSystemUpdatedMarker|
  void CollectAndReportUpdateMetricsOnUpdateFinished(ErrorCode error_code);

  // This function is called after update_engine is started after device
  // reboots. If update_engine is restarted w/o device reboot, this function
  // would not be called.

  // Metrics report function to call:
  //   |ReportAbnormallyTerminatedUpdateAttemptMetrics|
  //   |ReportTimeToRebootMetrics|
  // Prefs to update:
  //   |kPrefsBootId|, |kPrefsPreviousVersion|
  void UpdateStateAfterReboot(OTAResult result);

  // Prefs to update:
  //   |kPrefsPayloadAttemptNumber|, |kPrefsUpdateTimestampStart|,
  //   |kPrefsUpdateBootTimestampStart|
  void UpdatePrefsOnUpdateStart(bool is_resume);

  // Prefs to delete:
  //   |kPrefsNumReboots|, |kPrefsCurrentBytesDownloaded|
  //   |kPrefsSystemUpdatedMarker|, |kPrefsUpdateTimestampStart|,
  //   |kPrefsUpdateBootTimestampStart|
  void ClearMetricsPrefs();

  // Return source and target slots for update.
  BootControlInterface::Slot GetCurrentSlot() const;
  BootControlInterface::Slot GetTargetSlot() const;

  // Helper of public VerifyPayloadApplicable. Return the parsed manifest in
  // |manifest|.
  static bool VerifyPayloadParseManifest(const std::string& metadata_filename,
                                         std::string_view metadata_hash,
                                         DeltaArchiveManifest* manifest,
                                         Error* error);
  static bool VerifyPayloadParseManifest(const std::string& metadata_filename,
                                         DeltaArchiveManifest* manifest,
                                         Error* error) {
    return VerifyPayloadParseManifest(metadata_filename, "", manifest, error);
  }

  // Enqueue and run a CleanupPreviousUpdateAction.
  void ScheduleCleanupPreviousUpdate();

  // Notify and clear |cleanup_previous_update_callbacks_|.
  void NotifyCleanupPreviousUpdateCallbacksAndClear();

  // Remove |callback| from |cleanup_previous_update_callbacks_|.
  void RemoveCleanupPreviousUpdateCallback(
      CleanupSuccessfulUpdateCallbackInterface* callback);

  bool IsProductionBuild();

  DaemonStateInterface* daemon_state_;

  // DaemonStateAndroid pointers.
  PrefsInterface* prefs_;
  BootControlInterface* boot_control_;
  HardwareInterface* hardware_;

  std::unique_ptr<ApexHandlerInterface> apex_handler_android_;

  // Last status notification timestamp used for throttling. Use monotonic
  // TimeTicks to ensure that notifications are sent even if the system clock is
  // set back in the middle of an update.
  base::TimeTicks last_notify_time_;

  // The processor for running Actions.
  std::unique_ptr<ActionProcessor> processor_;

  // The InstallPlan used during the ongoing update.
  InstallPlan install_plan_;

  // For status:
  UpdateStatus status_{UpdateStatus::IDLE};
  double download_progress_{0.0};

  // The offset in the payload file where the CrAU part starts.
  int64_t base_offset_{0};

  // Helper class to select the network to use during the update.
  std::unique_ptr<NetworkSelectorInterface> network_selector_;

  std::unique_ptr<ClockInterface> clock_;

  std::unique_ptr<MetricsReporterInterface> metrics_reporter_;

  ::android::base::unique_fd payload_fd_;

  std::vector<std::unique_ptr<CleanupSuccessfulUpdateCallbackInterface>>
      cleanup_previous_update_callbacks_;
  // Result of previous CleanupPreviousUpdateAction. Nullopt If
  // CleanupPreviousUpdateAction has not been executed.
  std::optional<ErrorCode> cleanup_previous_update_code_{std::nullopt};

  // The path to the zip file with X509 certificates.
  std::string update_certificates_path_{constants::kUpdateCertificatesPath};
  ErrorCode last_error_{ErrorCode::kSuccess};

  metrics_utils::PersistedValue<int64_t> metric_bytes_downloaded_;
  metrics_utils::PersistedValue<int64_t> metric_total_bytes_downloaded_;

  bool performance_mode_ = false;

  DISALLOW_COPY_AND_ASSIGN(UpdateAttempterAndroid);
};

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_AOSP_UPDATE_ATTEMPTER_ANDROID_H_
