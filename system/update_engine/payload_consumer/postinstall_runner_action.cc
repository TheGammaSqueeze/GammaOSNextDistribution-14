//
// Copyright (C) 2011 The Android Open Source Project
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

#include "update_engine/payload_consumer/postinstall_runner_action.h"

#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <selinux/selinux.h>
#include <sys/mount.h>
#include <sys/types.h>
#include <unistd.h>

#include <cmath>
#include <fstream>
#include <string>

#include <base/files/file_path.h>
#include <base/files/file_util.h>
#include <base/logging.h>
#include <base/stl_util.h>
#include <base/strings/string_split.h>
#include <base/strings/string_util.h>

#include "update_engine/common/action_processor.h"
#include "update_engine/common/boot_control_interface.h"
#include "update_engine/common/error_code_utils.h"
#include "update_engine/common/platform_constants.h"
#include "update_engine/common/subprocess.h"
#include "update_engine/common/utils.h"

namespace {

// The file descriptor number from the postinstall program's perspective where
// it can report status updates. This can be any number greater than 2 (stderr),
// but must be kept in sync with the "bin/postinst_progress" defined in the
// sample_images.sh file.
const int kPostinstallStatusFd = 3;

static constexpr bool Contains(std::string_view haystack,
                               std::string_view needle) {
  return haystack.find(needle) != std::string::npos;
}

static void LogBuildInfoForPartition(std::string_view mount_point) {
  static constexpr std::array<std::string_view, 3> kBuildPropFiles{
      "build.prop", "etc/build.prop", "system/build.prop"};
  for (const auto& file : kBuildPropFiles) {
    auto path = std::string(mount_point);
    if (path.back() != '/') {
      path.push_back('/');
    }
    path += file;
    LOG(INFO) << "Trying to read " << path;
    std::ifstream infile(path);
    std::string line;
    while (std::getline(infile, line)) {
      if (Contains(line, "ro.build")) {
        LOG(INFO) << line;
      }
    }
  }
}

}  // namespace

namespace chromeos_update_engine {

using std::string;
using std::vector;

PostinstallRunnerAction::PostinstallRunnerAction(
    BootControlInterface* boot_control, HardwareInterface* hardware)
    : boot_control_(boot_control), hardware_(hardware) {
#ifdef __ANDROID__
  fs_mount_dir_ = "/postinstall";
#else   // __ANDROID__
  base::FilePath temp_dir;
  TEST_AND_RETURN(base::CreateNewTempDirectory("au_postint_mount", &temp_dir));
  fs_mount_dir_ = temp_dir.value();
#endif  // __ANDROID__
  CHECK(!fs_mount_dir_.empty());
  EnsureUnmounted();
  LOG(INFO) << "postinstall mount point: " << fs_mount_dir_;
}

void PostinstallRunnerAction::EnsureUnmounted() {
  if (utils::IsMountpoint(fs_mount_dir_)) {
    LOG(INFO) << "Found previously mounted filesystem at " << fs_mount_dir_;
    utils::UnmountFilesystem(fs_mount_dir_);
  }
}

void PostinstallRunnerAction::PerformAction() {
  CHECK(HasInputObject());
  CHECK(boot_control_);
  install_plan_ = GetInputObject();

  auto dynamic_control = boot_control_->GetDynamicPartitionControl();
  CHECK(dynamic_control);

  // Mount snapshot partitions for Virtual AB Compression Compression.
  if (dynamic_control->UpdateUsesSnapshotCompression()) {
    // Before calling MapAllPartitions to map snapshot devices, all CowWriters
    // must be closed, and MapAllPartitions() should be called.
    if (!install_plan_.partitions.empty()) {
      if (!dynamic_control->MapAllPartitions()) {
        return CompletePostinstall(ErrorCode::kPostInstallMountError);
      }
    }
  }

  // We always powerwash when rolling back, however policy can determine
  // if this is a full/normal powerwash, or a special rollback powerwash
  // that retains a small amount of system state such as enrollment and
  // network configuration. In both cases all user accounts are deleted.
  if (install_plan_.powerwash_required || install_plan_.is_rollback) {
    if (hardware_->SchedulePowerwash(
            install_plan_.rollback_data_save_requested)) {
      powerwash_scheduled_ = true;
    } else {
      return CompletePostinstall(ErrorCode::kPostinstallPowerwashError);
    }
  }

  // Initialize all the partition weights.
  partition_weight_.resize(install_plan_.partitions.size());
  total_weight_ = 0;
  for (size_t i = 0; i < install_plan_.partitions.size(); ++i) {
    auto& partition = install_plan_.partitions[i];
    if (!install_plan_.run_post_install && partition.postinstall_optional) {
      partition.run_postinstall = false;
      LOG(INFO) << "Skipping optional post-install for partition "
                << partition.name << " according to install plan.";
    }

    // TODO(deymo): This code sets the weight to all the postinstall commands,
    // but we could remember how long they took in the past and use those
    // values.
    partition_weight_[i] = partition.run_postinstall;
    total_weight_ += partition_weight_[i];
  }
  accumulated_weight_ = 0;
  ReportProgress(0);

  PerformPartitionPostinstall();
}

bool PostinstallRunnerAction::MountPartition(
    const InstallPlan::Partition& partition) noexcept {
  // Perform post-install for the current_partition_ partition. At this point we
  // need to call CompletePartitionPostinstall to complete the operation and
  // cleanup.
  const auto mountable_device = partition.readonly_target_path;
  if (!utils::FileExists(mountable_device.c_str())) {
    LOG(ERROR) << "Mountable device " << mountable_device << " for partition "
               << partition.name << " does not exist";
    return false;
  }

  if (!utils::FileExists(fs_mount_dir_.c_str())) {
    LOG(ERROR) << "Mount point " << fs_mount_dir_
               << " does not exist, mount call will fail";
    return false;
  }
  // Double check that the fs_mount_dir is not busy with a previous mounted
  // filesystem from a previous crashed postinstall step.
  EnsureUnmounted();

#ifdef __ANDROID__
#if !defined(__ANDROID_RECOVERY__) && defined(RUN_BACKUPTOOL)
  // Check the currently installed /system partition to see if it's ever
  // been mounted R/W. If it has, we'll run backuptool scripts for it
  // since we can safely assume something on the partition has been
  // changed and we won't be breaking verity (since it's already been
  // broken). If it hasn't ever been mounted R/W, we can assume that
  // the rom that the user is upgrading to will have everything they
  // need and no addon.d scripts will need to be run to retain stuff
  // after the upgrade.
  //
  // Use the following disk layout info to make the determination
  // https://ext4.wiki.kernel.org/index.php/Ext4_Disk_Layout
  // Super block starts from block 0, offset 0x400
  //   0x2C: len32 Mount time
  //   0x30: len32 Write time
  //   0x34: len16 Number of mounts since the last fsck
  //   0x38: len16 Magic signature 0xEF53

  string source_path;

  if (install_plan_.source_slot != BootControlInterface::kInvalidSlot) {
    boot_control_->GetPartitionDevice(partition.name, install_plan_.source_slot, &source_path);
  }

  uint16_t mount_count = 0;

  if (!source_path.empty()) {
    brillo::Blob chunk;

    utils::ReadFileChunk(source_path, 0x400 + 0x34, sizeof(uint16_t), &chunk);
    mount_count = *reinterpret_cast<uint16_t*>(chunk.data());
  }

  LOG(INFO) << source_path << " has been mounted R/W " << mount_count << " times.";

  if (mount_count > 0) {
    if (!utils::SetBlockDeviceReadOnly(mountable_device, false)) {
      LOG(ERROR) << "Error marking the device " << mountable_device << " writeable.";
      return false;
    }
    // Mount the target partition R/W
    LOG(INFO) << "Running backuptool scripts";
    utils::MountFilesystem(mountable_device, fs_mount_dir_, MS_NOATIME | MS_NODEV | MS_NODIRATIME,
                           partition.filesystem_type, "seclabel");

    // Switch to a permissive domain
    if (setexeccon("u:r:backuptool:s0")) {
      LOG(ERROR) << "Failed to set backuptool context";
      return false;
    }

    // Run backuptool script
    int ret = system("/postinstall/system/bin/backuptool_postinstall.sh");
    if (ret == -1 || WEXITSTATUS(ret) != 0) {
      LOG(ERROR) << "Backuptool postinstall step failed. ret=" << ret;
    }

    // Switch back to update_engine domain
    if (setexeccon(nullptr)) {
      LOG(ERROR) << "Failed to set update_engine context";
      return false;
    }
  } else {
    LOG(INFO) << "Skipping backuptool scripts";
  }

  utils::UnmountFilesystem(fs_mount_dir_);
#endif  // !__ANDROID_RECOVERY__ && RUN_BACKUPTOOL

  // In Chromium OS, the postinstall step is allowed to write to the block
  // device on the target image, so we don't mark it as read-only and should
  // be read-write since we just wrote to it during the update.

  // Mark the block device as read-only before mounting for post-install.
  if (!utils::SetBlockDeviceReadOnly(mountable_device, true)) {
    return false;
  }
#endif  // __ANDROID__

  if (!utils::MountFilesystem(
          mountable_device,
          fs_mount_dir_,
          MS_RDONLY,
          partition.filesystem_type,
          hardware_->GetPartitionMountOptions(partition.name))) {
    return false;
  }
  return true;
}

void PostinstallRunnerAction::PerformPartitionPostinstall() {
  if (install_plan_.download_url.empty()) {
    LOG(INFO) << "Skipping post-install";
    return CompletePostinstall(ErrorCode::kSuccess);
  }

  // Skip all the partitions that don't have a post-install step.
  while (current_partition_ < install_plan_.partitions.size() &&
         !install_plan_.partitions[current_partition_].run_postinstall) {
    VLOG(1) << "Skipping post-install on partition "
            << install_plan_.partitions[current_partition_].name;
    // Attempt to mount a device if it has postinstall script configured, even
    // if we want to skip running postinstall script.
    // This is because we've seen bugs like b/198787355 which is only triggered
    // when you attempt to mount a device. If device fails to mount, it will
    // likely fail to mount during boot anyway, so it's better to catch any
    // issues earlier.
    // It's possible that some of the partitions aren't mountable, but these
    // partitions shouldn't have postinstall configured. Therefore we guard this
    // logic with |postinstall_path.empty()|.
    const auto& partition = install_plan_.partitions[current_partition_];
    if (!partition.postinstall_path.empty()) {
      const auto mountable_device = partition.readonly_target_path;
      if (!MountPartition(partition)) {
        return CompletePostinstall(ErrorCode::kPostInstallMountError);
      }
      LogBuildInfoForPartition(fs_mount_dir_);
      if (!utils::UnmountFilesystem(fs_mount_dir_)) {
        return CompletePartitionPostinstall(
            1, "Error unmounting the device " + mountable_device);
      }
    }
    current_partition_++;
  }
  if (current_partition_ == install_plan_.partitions.size())
    return CompletePostinstall(ErrorCode::kSuccess);

  const InstallPlan::Partition& partition =
      install_plan_.partitions[current_partition_];

  const string mountable_device = partition.readonly_target_path;
  // Perform post-install for the current_partition_ partition. At this point we
  // need to call CompletePartitionPostinstall to complete the operation and
  // cleanup.

  if (!MountPartition(partition)) {
    CompletePostinstall(ErrorCode::kPostInstallMountError);
    return;
  }
  LogBuildInfoForPartition(fs_mount_dir_);
  base::FilePath postinstall_path(partition.postinstall_path);
  if (postinstall_path.IsAbsolute()) {
    LOG(ERROR) << "Invalid absolute path passed to postinstall, use a relative"
                  "path instead: "
               << partition.postinstall_path;
    return CompletePostinstall(ErrorCode::kPostinstallRunnerError);
  }

  string abs_path =
      base::FilePath(fs_mount_dir_).Append(postinstall_path).value();
  if (!base::StartsWith(
          abs_path, fs_mount_dir_, base::CompareCase::SENSITIVE)) {
    LOG(ERROR) << "Invalid relative postinstall path: "
               << partition.postinstall_path;
    return CompletePostinstall(ErrorCode::kPostinstallRunnerError);
  }

  LOG(INFO) << "Performing postinst (" << partition.postinstall_path << " at "
            << abs_path << ") installed on mountable device "
            << mountable_device;

  // Logs the file format of the postinstall script we are about to run. This
  // will help debug when the postinstall script doesn't match the architecture
  // of our build.
  LOG(INFO) << "Format file for new " << partition.postinstall_path
            << " is: " << utils::GetFileFormat(abs_path);

  // Runs the postinstall script asynchronously to free up the main loop while
  // it's running.
  vector<string> command = {abs_path};
#ifdef __ANDROID__
  // In Brillo and Android, we pass the slot number and status fd.
  command.push_back(std::to_string(install_plan_.target_slot));
  command.push_back(std::to_string(kPostinstallStatusFd));
#else
  // Chrome OS postinstall expects the target rootfs as the first parameter.
  command.push_back(partition.target_path);
#endif  // __ANDROID__

  current_command_ = Subprocess::Get().ExecFlags(
      command,
      Subprocess::kRedirectStderrToStdout,
      {kPostinstallStatusFd},
      base::Bind(&PostinstallRunnerAction::CompletePartitionPostinstall,
                 base::Unretained(this)));
  // Subprocess::Exec should never return a negative process id.
  CHECK_GE(current_command_, 0);

  if (!current_command_) {
    CompletePartitionPostinstall(1, "Postinstall didn't launch");
    return;
  }

  // Monitor the status file descriptor.
  progress_fd_ =
      Subprocess::Get().GetPipeFd(current_command_, kPostinstallStatusFd);
  int fd_flags = fcntl(progress_fd_, F_GETFL, 0) | O_NONBLOCK;
  if (HANDLE_EINTR(fcntl(progress_fd_, F_SETFL, fd_flags)) < 0) {
    PLOG(ERROR) << "Unable to set non-blocking I/O mode on fd " << progress_fd_;
  }

  progress_controller_ = base::FileDescriptorWatcher::WatchReadable(
      progress_fd_,
      base::BindRepeating(&PostinstallRunnerAction::OnProgressFdReady,
                          base::Unretained(this)));
}

void PostinstallRunnerAction::OnProgressFdReady() {
  char buf[1024];
  size_t bytes_read;
  do {
    bytes_read = 0;
    bool eof;
    bool ok =
        utils::ReadAll(progress_fd_, buf, base::size(buf), &bytes_read, &eof);
    progress_buffer_.append(buf, bytes_read);
    // Process every line.
    vector<string> lines = base::SplitString(
        progress_buffer_, "\n", base::KEEP_WHITESPACE, base::SPLIT_WANT_ALL);
    if (!lines.empty()) {
      progress_buffer_ = lines.back();
      lines.pop_back();
      for (const auto& line : lines) {
        ProcessProgressLine(line);
      }
    }
    if (!ok || eof) {
      // There was either an error or an EOF condition, so we are done watching
      // the file descriptor.
      progress_controller_.reset();
      return;
    }
  } while (bytes_read);
}

bool PostinstallRunnerAction::ProcessProgressLine(const string& line) {
  double frac = 0;
  if (sscanf(line.c_str(), "global_progress %lf", &frac) == 1 &&
      !std::isnan(frac)) {
    ReportProgress(frac);
    return true;
  }

  return false;
}

void PostinstallRunnerAction::ReportProgress(double frac) {
  if (!delegate_)
    return;
  if (current_partition_ >= partition_weight_.size() || total_weight_ == 0) {
    delegate_->ProgressUpdate(1.);
    return;
  }
  if (!std::isfinite(frac) || frac < 0)
    frac = 0;
  if (frac > 1)
    frac = 1;
  double postinst_action_progress =
      (accumulated_weight_ + partition_weight_[current_partition_] * frac) /
      total_weight_;
  delegate_->ProgressUpdate(postinst_action_progress);
}

void PostinstallRunnerAction::Cleanup() {
  utils::UnmountFilesystem(fs_mount_dir_);
#ifndef __ANDROID__
#if BASE_VER < 800000
  if (!base::DeleteFile(base::FilePath(fs_mount_dir_), true)) {
#else
  if (!base::DeleteFile(base::FilePath(fs_mount_dir_))) {
#endif
    PLOG(WARNING) << "Not removing temporary mountpoint " << fs_mount_dir_;
  }
#endif

  progress_fd_ = -1;
  progress_controller_.reset();

  progress_buffer_.clear();
}

void PostinstallRunnerAction::CompletePartitionPostinstall(
    int return_code, const string& output) {
  current_command_ = 0;
  Cleanup();

  if (return_code != 0) {
    LOG(ERROR) << "Postinst command failed with code: " << return_code;
    ErrorCode error_code = ErrorCode::kPostinstallRunnerError;

    if (return_code == 3) {
      // This special return code means that we tried to update firmware,
      // but couldn't because we booted from FW B, and we need to reboot
      // to get back to FW A.
      error_code = ErrorCode::kPostinstallBootedFromFirmwareB;
    }

    if (return_code == 4) {
      // This special return code means that we tried to update firmware,
      // but couldn't because we booted from FW B, and we need to reboot
      // to get back to FW A.
      error_code = ErrorCode::kPostinstallFirmwareRONotUpdatable;
    }

    // If postinstall script for this partition is optional we can ignore the
    // result.
    if (install_plan_.partitions[current_partition_].postinstall_optional) {
      LOG(INFO) << "Ignoring postinstall failure since it is optional";
    } else {
      return CompletePostinstall(error_code);
    }
  }
  accumulated_weight_ += partition_weight_[current_partition_];
  current_partition_++;
  ReportProgress(0);

  PerformPartitionPostinstall();
}

PostinstallRunnerAction::~PostinstallRunnerAction() {
  if (!install_plan_.partitions.empty()) {
    auto dynamic_control = boot_control_->GetDynamicPartitionControl();
    CHECK(dynamic_control);
    dynamic_control->UnmapAllPartitions();
    LOG(INFO) << "Unmapped all partitions.";
  }
}

void PostinstallRunnerAction::CompletePostinstall(ErrorCode error_code) {
  // We only attempt to mark the new slot as active if all the postinstall
  // steps succeeded.
  DEFER {
    if (error_code != ErrorCode::kSuccess &&
        error_code != ErrorCode::kUpdatedButNotActive) {
      LOG(ERROR) << "Postinstall action failed. "
                 << utils::ErrorCodeToString(error_code);

      // Undo any changes done to trigger Powerwash.
      if (powerwash_scheduled_)
        hardware_->CancelPowerwash();
    }
    processor_->ActionComplete(this, error_code);
  };
  if (error_code == ErrorCode::kSuccess) {
    if (install_plan_.switch_slot_on_reboot) {
      if constexpr (!constants::kIsRecovery) {
        if (!boot_control_->GetDynamicPartitionControl()->MapAllPartitions()) {
          LOG(WARNING)
              << "Failed to map all partitions before marking snapshot as "
                 "ready for slot switch. Subsequent FinishUpdate() call may or "
                 "may not work";
        }
      }
      if (!boot_control_->GetDynamicPartitionControl()->FinishUpdate(
              install_plan_.powerwash_required) ||
          !boot_control_->SetActiveBootSlot(install_plan_.target_slot)) {
        error_code = ErrorCode::kPostinstallRunnerError;
      } else {
        // Schedules warm reset on next reboot, ignores the error.
        hardware_->SetWarmReset(true);
        // Sets the vbmeta digest for the other slot to boot into.
        hardware_->SetVbmetaDigestForInactiveSlot(false);
      }
    } else {
      error_code = ErrorCode::kUpdatedButNotActive;
    }
  }

  LOG(INFO) << "All post-install commands succeeded";
  if (HasOutputPipe()) {
    SetOutputObject(install_plan_);
  }
}

void PostinstallRunnerAction::SuspendAction() {
  if (!current_command_)
    return;
  if (kill(current_command_, SIGSTOP) != 0) {
    PLOG(ERROR) << "Couldn't pause child process " << current_command_;
  } else {
    is_current_command_suspended_ = true;
  }
}

void PostinstallRunnerAction::ResumeAction() {
  if (!current_command_)
    return;
  if (kill(current_command_, SIGCONT) != 0) {
    PLOG(ERROR) << "Couldn't resume child process " << current_command_;
  } else {
    is_current_command_suspended_ = false;
  }
}

void PostinstallRunnerAction::TerminateProcessing() {
  if (!current_command_)
    return;
  // Calling KillExec() will discard the callback we registered and therefore
  // the unretained reference to this object.
  Subprocess::Get().KillExec(current_command_);

  // If the command has been suspended, resume it after KillExec() so that the
  // process can process the SIGTERM sent by KillExec().
  if (is_current_command_suspended_) {
    ResumeAction();
  }

  current_command_ = 0;
  Cleanup();
}

}  // namespace chromeos_update_engine
