//
// Copyright (C) 2020 The Android Open Source Project
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
#include "update_engine/aosp/cleanup_previous_update_action.h"

#include <algorithm>
#include <chrono>
#include <functional>
#include <string>
#include <type_traits>

#include <android-base/chrono_utils.h>
#include <android-base/properties.h>
#include <base/bind.h>
#include <libsnapshot/snapshot.h>

#ifndef __ANDROID_RECOVERY__
#include <statslog_ue.h>
#endif

#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/delta_performer.h"

using android::base::GetBoolProperty;
using android::snapshot::ISnapshotManager;
using android::snapshot::SnapshotMergeStats;
using android::snapshot::UpdateState;
using brillo::MessageLoop;

constexpr char kBootCompletedProp[] = "sys.boot_completed";
constexpr auto&& kMergeDelaySecondsProp = "ro.virtual_ab.merge_delay_seconds";
constexpr size_t kMaxMergeDelaySeconds = 600;
// Interval to check sys.boot_completed.
constexpr auto kCheckBootCompletedInterval = base::TimeDelta::FromSeconds(2);
// Interval to check IBootControl::isSlotMarkedSuccessful
constexpr auto kCheckSlotMarkedSuccessfulInterval =
    base::TimeDelta::FromSeconds(2);
// Interval to call SnapshotManager::ProcessUpdateState
constexpr auto kWaitForMergeInterval = base::TimeDelta::FromSeconds(2);

#ifdef __ANDROID_RECOVERY__
static constexpr bool kIsRecovery = true;
#else
static constexpr bool kIsRecovery = false;
#endif

namespace chromeos_update_engine {

CleanupPreviousUpdateAction::CleanupPreviousUpdateAction(
    PrefsInterface* prefs,
    BootControlInterface* boot_control,
    android::snapshot::ISnapshotManager* snapshot,
    CleanupPreviousUpdateActionDelegateInterface* delegate)
    : prefs_(prefs),
      boot_control_(boot_control),
      snapshot_(snapshot),
      delegate_(delegate),
      running_(false),
      cancel_failed_(false),
      last_percentage_(0),
      merge_stats_(nullptr) {}

CleanupPreviousUpdateAction::~CleanupPreviousUpdateAction() {
  StopActionInternal();
}

void CleanupPreviousUpdateAction::PerformAction() {
  StartActionInternal();
}

void CleanupPreviousUpdateAction::TerminateProcessing() {
  StopActionInternal();
}

void CleanupPreviousUpdateAction::ResumeAction() {
  StartActionInternal();
}

void CleanupPreviousUpdateAction::SuspendAction() {
  StopActionInternal();
}

void CleanupPreviousUpdateAction::ActionCompleted(ErrorCode error_code) {
  StopActionInternal();
  ReportMergeStats();
  metadata_device_ = nullptr;
}

std::string CleanupPreviousUpdateAction::Type() const {
  return StaticType();
}

std::string CleanupPreviousUpdateAction::StaticType() {
  return "CleanupPreviousUpdateAction";
}

// This function is called at the beginning of all delayed functions. By
// resetting |scheduled_task_|, the delayed function acknowledges that the task
// has already been executed, therefore there's no need to cancel it in the
// future. This avoids StopActionInternal() from resetting task IDs in an
// unexpected way because task IDs could be reused.
void CleanupPreviousUpdateAction::AcknowledgeTaskExecuted() {
  if (scheduled_task_.IsScheduled()) {
    LOG(INFO) << "Executing task " << scheduled_task_;
  }
}

// Check that scheduled_task_ is a valid task ID. Otherwise, terminate the
// action.
void CleanupPreviousUpdateAction::CheckTaskScheduled(std::string_view name) {
  if (!scheduled_task_.IsScheduled()) {
    LOG(ERROR) << "Unable to schedule " << name;
    processor_->ActionComplete(this, ErrorCode::kError);
  } else {
    LOG(INFO) << "CleanupPreviousUpdateAction scheduled task ID "
              << scheduled_task_ << " for " << name;
  }
}

void CleanupPreviousUpdateAction::StopActionInternal() {
  LOG(INFO) << "Stopping/suspending/completing CleanupPreviousUpdateAction";
  running_ = false;

  if (scheduled_task_.IsScheduled()) {
    if (scheduled_task_.Cancel()) {
      LOG(INFO) << "CleanupPreviousUpdateAction cancelled pending task ID "
                << scheduled_task_;
    } else {
      LOG(ERROR) << "CleanupPreviousUpdateAction unable to cancel task ID "
                 << scheduled_task_;
    }
  }
}

void CleanupPreviousUpdateAction::StartActionInternal() {
  CHECK(prefs_);
  CHECK(boot_control_);

  LOG(INFO) << "Starting/resuming CleanupPreviousUpdateAction";
  running_ = true;
  // Do nothing on non-VAB device.
  if (!boot_control_->GetDynamicPartitionControl()
           ->GetVirtualAbFeatureFlag()
           .IsEnabled()) {
    processor_->ActionComplete(this, ErrorCode::kSuccess);
    return;
  }
  // SnapshotManager must be available on VAB devices.
  CHECK(snapshot_ != nullptr);
  merge_stats_ = snapshot_->GetSnapshotMergeStatsInstance();
  CHECK(merge_stats_ != nullptr);
  WaitBootCompletedOrSchedule();
}

void CleanupPreviousUpdateAction::ScheduleWaitBootCompleted() {
  TEST_AND_RETURN(running_);
  if (!scheduled_task_.PostTask(
          FROM_HERE,
          base::Bind(&CleanupPreviousUpdateAction::WaitBootCompletedOrSchedule,
                     base::Unretained(this)),
          kCheckBootCompletedInterval)) {
    CheckTaskScheduled("WaitBootCompleted");
  }
}

void CleanupPreviousUpdateAction::WaitBootCompletedOrSchedule() {
  AcknowledgeTaskExecuted();
  TEST_AND_RETURN(running_);
  if (!kIsRecovery &&
      !android::base::GetBoolProperty(kBootCompletedProp, false)) {
    // repeat
    ScheduleWaitBootCompleted();
    return;
  }

  auto boot_time = std::chrono::duration_cast<std::chrono::milliseconds>(
      android::base::boot_clock::now().time_since_epoch());
  merge_stats_->set_boot_complete_time_ms(boot_time.count());

  LOG(INFO) << "Boot completed, waiting on markBootSuccessful()";
  CheckSlotMarkedSuccessfulOrSchedule();
}

void CleanupPreviousUpdateAction::ScheduleWaitMarkBootSuccessful() {
  TEST_AND_RETURN(running_);
  if (!scheduled_task_.PostTask(
          FROM_HERE,
          base::Bind(
              &CleanupPreviousUpdateAction::CheckSlotMarkedSuccessfulOrSchedule,
              base::Unretained(this)),
          kCheckSlotMarkedSuccessfulInterval)) {
    CheckTaskScheduled("WaitMarkBootSuccessful");
  }
}

void CleanupPreviousUpdateAction::CheckForMergeDelay() {
  if (!android::snapshot::SnapshotManager::IsSnapshotManagerNeeded()) {
    StartMerge();
    return;
  }
  const auto merge_delay_seconds =
      std::clamp<int>(android::base::GetIntProperty(kMergeDelaySecondsProp, 0),
                      0,
                      kMaxMergeDelaySeconds);
  if (merge_delay_seconds != 0) {
    LOG(INFO) << "Merge is ready to start, but " << kMergeDelaySecondsProp
              << " is set, delaying merge by " << merge_delay_seconds
              << " seconds";
  }
  if (!scheduled_task_.PostTask(
          FROM_HERE,
          [this]() { StartMerge(); },
          base::TimeDelta::FromSeconds(merge_delay_seconds))) {
    LOG(ERROR) << "Unable to schedule " << __FUNCTION__;
    processor_->ActionComplete(this, ErrorCode::kError);
  }
}

void CleanupPreviousUpdateAction::CheckSlotMarkedSuccessfulOrSchedule() {
  AcknowledgeTaskExecuted();
  TEST_AND_RETURN(running_);
  if (!kIsRecovery &&
      !boot_control_->IsSlotMarkedSuccessful(boot_control_->GetCurrentSlot())) {
    ScheduleWaitMarkBootSuccessful();
    return;
  }
  CheckForMergeDelay();
}

void CleanupPreviousUpdateAction::StartMerge() {
  if (metadata_device_ == nullptr) {
    metadata_device_ = snapshot_->EnsureMetadataMounted();
  }

  if (metadata_device_ == nullptr) {
    LOG(ERROR) << "Failed to mount /metadata.";
    // If metadata is erased but not formatted, it is possible to not mount
    // it in recovery. It is safe to skip CleanupPreviousUpdateAction.
    processor_->ActionComplete(
        this, kIsRecovery ? ErrorCode::kSuccess : ErrorCode::kError);
    return;
  }

  if (kIsRecovery) {
    auto snapshots_created =
        snapshot_->RecoveryCreateSnapshotDevices(metadata_device_);
    switch (snapshots_created) {
      case android::snapshot::CreateResult::CREATED: {
        // If previous update has not finished merging, snapshots exists and are
        // created here so that ProcessUpdateState can proceed.
        LOG(INFO) << "Snapshot devices are created";
        break;
      }
      case android::snapshot::CreateResult::NOT_CREATED: {
        // If there is no previous update, no snapshot devices are created and
        // ProcessUpdateState will return immediately. Hence, NOT_CREATED is not
        // considered an error.
        LOG(INFO) << "Snapshot devices are not created";
        break;
      }
      case android::snapshot::CreateResult::ERROR:
      default: {
        LOG(ERROR)
            << "Failed to create snapshot devices (CreateResult = "
            << static_cast<
                   std::underlying_type_t<android::snapshot::CreateResult>>(
                   snapshots_created);
        processor_->ActionComplete(this, ErrorCode::kError);
        return;
      }
    }
  }

  if (!merge_stats_->Start()) {
    // Not an error because CleanupPreviousUpdateAction may be paused and
    // resumed while kernel continues merging snapshots in the background.
    LOG(WARNING) << "SnapshotMergeStats::Start failed.";
  }
  LOG(INFO) << "Waiting for any previous merge request to complete. "
            << "This can take up to several minutes.";
  WaitForMergeOrSchedule();
}

void CleanupPreviousUpdateAction::ScheduleWaitForMerge() {
  TEST_AND_RETURN(running_);
  if (!scheduled_task_.PostTask(
          FROM_HERE,
          base::Bind(&CleanupPreviousUpdateAction::WaitForMergeOrSchedule,
                     base::Unretained(this)),
          kWaitForMergeInterval)) {
    CheckTaskScheduled("WaitForMerge");
  }
}

void CleanupPreviousUpdateAction::WaitForMergeOrSchedule() {
  AcknowledgeTaskExecuted();
  TEST_AND_RETURN(running_);

  snapshot_->SetMergeStatsFeatures(merge_stats_);

  // Propagate the merge failure code to the merge stats. If we wait until
  // after ProcessUpdateState, then a successful merge could overwrite the
  // state of the previous failure.
  auto failure_code = snapshot_->ReadMergeFailureCode();
  if (failure_code != android::snapshot::MergeFailureCode::Ok) {
    merge_stats_->set_merge_failure_code(failure_code);
  }

  auto state = snapshot_->ProcessUpdateState(
      std::bind(&CleanupPreviousUpdateAction::OnMergePercentageUpdate, this),
      std::bind(&CleanupPreviousUpdateAction::BeforeCancel, this));
  merge_stats_->set_state(state);

  switch (state) {
    case UpdateState::None: {
      LOG(INFO) << "Can't find any snapshot to merge.";
      ErrorCode error_code = ErrorCode::kSuccess;
      if (!snapshot_->CancelUpdate()) {
        error_code = ErrorCode::kError;
        LOG(INFO) << "Failed to call SnapshotManager::CancelUpdate().";
      }
      processor_->ActionComplete(this, error_code);
      return;
    }

    case UpdateState::Initiated: {
      LOG(ERROR) << "Previous update has not been completed, not cleaning up";
      processor_->ActionComplete(this, ErrorCode::kSuccess);
      return;
    }

    case UpdateState::Unverified: {
      InitiateMergeAndWait();
      return;
    }

    case UpdateState::Merging: {
      ScheduleWaitForMerge();
      return;
    }

    case UpdateState::MergeNeedsReboot: {
      LOG(ERROR) << "Need reboot to finish merging.";
      processor_->ActionComplete(this, ErrorCode::kError);
      return;
    }

    case UpdateState::MergeCompleted: {
      LOG(INFO) << "Merge finished with state MergeCompleted.";
      boot_control_->MarkSlotUnbootable(1 - boot_control_->GetCurrentSlot());
      processor_->ActionComplete(this, ErrorCode::kSuccess);
      return;
    }

    case UpdateState::MergeFailed: {
      LOG(ERROR) << "Merge failed. Device may be corrupted.";
      merge_stats_->set_merge_failure_code(snapshot_->ReadMergeFailureCode());
      processor_->ActionComplete(this, ErrorCode::kDeviceCorrupted);
      return;
    }

    case UpdateState::Cancelled: {
      // DeltaPerformer::ResetUpdateProgress failed, hence snapshots are
      // not deleted to avoid inconsistency.
      // Nothing can be done here; just try next time.
      ErrorCode error_code =
          cancel_failed_ ? ErrorCode::kError : ErrorCode::kSuccess;
      processor_->ActionComplete(this, error_code);
      return;
    }

    default: {
      // Protobuf has some reserved enum values, so a default case is needed.
      LOG(FATAL) << "SnapshotManager::ProcessUpdateState returns "
                 << static_cast<int32_t>(state);
    }
  }
}

bool CleanupPreviousUpdateAction::OnMergePercentageUpdate() {
  double percentage = 0.0;
  snapshot_->GetUpdateState(&percentage);
  if (delegate_) {
    // libsnapshot uses [0, 100] percentage but update_engine uses [0, 1].
    delegate_->OnCleanupProgressUpdate(percentage / 100);
  }

  // Log if percentage increments by at least 1.
  if (last_percentage_ < static_cast<unsigned int>(percentage)) {
    last_percentage_ = percentage;
    LOG(INFO) << "Waiting for merge to complete: " << last_percentage_ << "%.";
  }

  // Do not continue to wait for merge. Instead, let ProcessUpdateState
  // return Merging directly so that we can ScheduleWaitForMerge() in
  // MessageLoop.
  return false;
}

bool CleanupPreviousUpdateAction::BeforeCancel() {
  if (DeltaPerformer::ResetUpdateProgress(
          prefs_,
          false /* quick */,
          false /* skip dynamic partitions metadata*/)) {
    return true;
  }

  // ResetUpdateProgress might not work on stub prefs. Do additional checks.
  LOG(WARNING) << "ProcessUpdateState returns Cancelled but cleanup failed.";

  std::string val;
  ignore_result(prefs_->GetString(kPrefsDynamicPartitionMetadataUpdated, &val));
  if (val.empty()) {
    LOG(INFO) << kPrefsDynamicPartitionMetadataUpdated
              << " is empty, assuming successful cleanup";
    return true;
  }
  LOG(WARNING)
      << kPrefsDynamicPartitionMetadataUpdated << " is " << val
      << ", not deleting snapshots even though UpdateState is Cancelled.";
  cancel_failed_ = true;
  return false;
}

void CleanupPreviousUpdateAction::InitiateMergeAndWait() {
  TEST_AND_RETURN(running_);
  LOG(INFO) << "Attempting to initiate merge.";
  // suspend the VAB merge when running a DSU
  if (GetBoolProperty("ro.gsid.image_running", false)) {
    LOG(WARNING) << "Suspend the VAB merge when running a DSU.";
    processor_->ActionComplete(this, ErrorCode::kError);
    return;
  }

  snapshot_->UpdateCowStats(merge_stats_);

  auto merge_start_time = std::chrono::duration_cast<std::chrono::milliseconds>(
      android::base::boot_clock::now().time_since_epoch());
  merge_stats_->set_boot_complete_to_merge_start_time_ms(
      merge_start_time.count() - merge_stats_->boot_complete_time_ms());

  auto source_build_fingerprint = snapshot_->ReadSourceBuildFingerprint();
  merge_stats_->set_source_build_fingerprint(source_build_fingerprint);

  if (!merge_stats_->WriteState()) {
    LOG(ERROR) << "Failed to write merge stats; record may be unreliable if "
                  "merge is interrupted.";
  }

  if (snapshot_->InitiateMerge()) {
    WaitForMergeOrSchedule();
    return;
  }

  LOG(WARNING) << "InitiateMerge failed.";
  auto state = snapshot_->GetUpdateState();
  merge_stats_->set_state(state);
  if (state == UpdateState::Unverified) {
    // We are stuck at unverified state. This can happen if the update has
    // been applied, but it has not even been attempted yet (in libsnapshot,
    // rollback indicator does not exist); for example, if update_engine
    // restarts before the device reboots, then this state may be reached.
    // Nothing should be done here.
    LOG(WARNING) << "InitiateMerge leaves the device at "
                 << "UpdateState::Unverified. (Did update_engine "
                 << "restarted?)";
    processor_->ActionComplete(this, ErrorCode::kSuccess);
    return;
  }

  // State does seems to be advanced.
  // It is possibly racy. For example, on a userdebug build, the user may
  // manually initiate a merge with snapshotctl between last time
  // update_engine checks UpdateState. Hence, just call
  // WaitForMergeOrSchedule one more time.
  LOG(WARNING) << "IniitateMerge failed but GetUpdateState returned "
               << android::snapshot::UpdateState_Name(state)
               << ", try to wait for merge again.";
  WaitForMergeOrSchedule();
  return;
}

void CleanupPreviousUpdateAction::ReportMergeStats() {
  auto result = merge_stats_->Finish();
  if (result == nullptr) {
    LOG(WARNING) << "Not reporting merge stats because "
                    "SnapshotMergeStats::Finish failed.";
    return;
  }

#ifdef __ANDROID_RECOVERY__
  LOG(INFO) << "Skip reporting merge stats in recovery.";
#else
  const auto& report = result->report();

  if (report.state() == UpdateState::None ||
      report.state() == UpdateState::Initiated ||
      report.state() == UpdateState::Unverified) {
    LOG(INFO) << "Not reporting merge stats because state is "
              << android::snapshot::UpdateState_Name(report.state());
    return;
  }

  auto passed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
      result->merge_time());

  bool vab_retrofit = boot_control_->GetDynamicPartitionControl()
                          ->GetVirtualAbFeatureFlag()
                          .IsRetrofit();
  bool vab_compression_enabled = boot_control_->GetDynamicPartitionControl()
                                     ->GetVirtualAbCompressionFeatureFlag()
                                     .IsEnabled();
  // The snapshot has been merged, so we can no longer call
  // DynamicPartitionControlInterface::UpdateUsesSnapshotCompression.
  // However, we have saved the flag in the snapshot report.
  bool vab_compression_used = report.compression_enabled();
  bool userspace_snapshots_enabled =
      boot_control_->GetDynamicPartitionControl()
          ->GetVirtualAbUserspaceSnapshotsFeatureFlag()
          .IsEnabled();
  bool userspace_snapshots_used = report.userspace_snapshots_used();
  bool xor_compression_enabled = boot_control_->GetDynamicPartitionControl()
                                     ->GetVirtualAbCompressionXorFeatureFlag()
                                     .IsEnabled();
  bool xor_compression_used = report.xor_compression_used();
  bool iouring_used = report.iouring_used();

  auto target_build_fingerprint =
      android::base::GetProperty("ro.build.fingerprint", "");

  LOG(INFO) << "Reporting merge stats: "
            << android::snapshot::UpdateState_Name(report.state()) << " in "
            << passed_ms.count() << "ms (resumed " << report.resume_count()
            << " times), using " << report.cow_file_size()
            << " bytes of COW image.";
  statsd::stats_write(statsd::SNAPSHOT_MERGE_REPORTED,
                      static_cast<int32_t>(report.state()),
                      static_cast<int64_t>(passed_ms.count()),
                      static_cast<int32_t>(report.resume_count()),
                      vab_retrofit,
                      static_cast<int64_t>(report.cow_file_size()),
                      vab_compression_enabled,
                      vab_compression_used,
                      report.total_cow_size_bytes(),
                      report.estimated_cow_size_bytes(),
                      report.boot_complete_time_ms(),
                      report.boot_complete_to_merge_start_time_ms(),
                      static_cast<int32_t>(report.merge_failure_code()),
                      report.source_build_fingerprint().c_str(),
                      target_build_fingerprint.c_str(),
                      userspace_snapshots_enabled,
                      userspace_snapshots_used,
                      xor_compression_enabled,
                      xor_compression_used,
                      iouring_used);
#endif
}

}  // namespace chromeos_update_engine
