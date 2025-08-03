/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define ATRACE_TAG ATRACE_TAG_PACKAGE_MANAGER

#include "apexd.h"

#include <ApexProperties.sysprop.h>
#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/scopeguard.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <dirent.h>
#include <fcntl.h>
#include <google/protobuf/util/message_differencer.h>
#include <libavb/libavb.h>
#include <libdm/dm.h>
#include <libdm/dm_table.h>
#include <libdm/dm_target.h>
#include <linux/f2fs.h>
#include <linux/loop.h>
#include <selinux/android.h>
#include <stdlib.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Trace.h>
#include <vintf/VintfObject.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <future>
#include <iomanip>
#include <iterator>
#include <memory>
#include <mutex>
#include <optional>
#include <queue>
#include <sstream>
#include <string>
#include <string_view>
#include <thread>
#include <unordered_map>
#include <unordered_set>

#include "VerityUtils.h"
#include "apex_constants.h"
#include "apex_database.h"
#include "apex_file.h"
#include "apex_file_repository.h"
#include "apex_manifest.h"
#include "apex_shim.h"
#include "apexd_checkpoint.h"
#include "apexd_lifecycle.h"
#include "apexd_loop.h"
#include "apexd_private.h"
#include "apexd_rollback_utils.h"
#include "apexd_session.h"
#include "apexd_utils.h"
#include "apexd_vendor_apex.h"
#include "apexd_verity.h"
#include "com_android_apex.h"

using android::base::boot_clock;
using android::base::ConsumePrefix;
using android::base::ErrnoError;
using android::base::Error;
using android::base::GetProperty;
using android::base::Join;
using android::base::ParseUint;
using android::base::RemoveFileIfExists;
using android::base::Result;
using android::base::SetProperty;
using android::base::StartsWith;
using android::base::StringPrintf;
using android::base::unique_fd;
using android::dm::DeviceMapper;
using android::dm::DmDeviceState;
using android::dm::DmTable;
using android::dm::DmTargetVerity;
using ::apex::proto::ApexManifest;
using apex::proto::SessionState;
using google::protobuf::util::MessageDifferencer;

namespace android {
namespace apex {

using MountedApexData = MountedApexDatabase::MountedApexData;

namespace {

static constexpr const char* kBuildFingerprintSysprop = "ro.build.fingerprint";

// This should be in UAPI, but it's not :-(
static constexpr const char* kDmVerityRestartOnCorruption =
    "restart_on_corruption";

MountedApexDatabase gMountedApexes;

// Can be set by SetConfig()
std::optional<ApexdConfig> gConfig;

// Set by InitializeSessionManager
ApexSessionManager* gSessionManager;

CheckpointInterface* gVoldService;
bool gSupportsFsCheckpoints = false;
bool gInFsCheckpointMode = false;

// APEXEs for which a different version was activated than in the previous boot.
// This can happen in the following scenarios:
//  1. This APEX is part of the staged session that was applied during this
//    boot.
//  2. This is a compressed APEX that was decompressed during this boot.
//  3. We failed to activate APEX from /data/apex/active and fallback to the
//  pre-installed APEX.
std::set<std::string> gChangedActiveApexes;

static constexpr size_t kLoopDeviceSetupAttempts = 3u;

// Please DO NOT add new modules to this list without contacting mainline-modularization@ first.
static const std::vector<std::string> kBootstrapApexes = ([]() {
  std::vector<std::string> ret = {
      "com.android.i18n",
      "com.android.runtime",
      "com.android.tzdata",
  };

  auto vendor_vndk_ver = GetProperty("ro.vndk.version", "");
  if (vendor_vndk_ver != "") {
    ret.push_back("com.android.vndk.v" + vendor_vndk_ver);
  }
  auto product_vndk_ver = GetProperty("ro.product.vndk.version", "");
  if (product_vndk_ver != "" && product_vndk_ver != vendor_vndk_ver) {
    ret.push_back("com.android.vndk.v" + product_vndk_ver);
  }
  return ret;
})();

static constexpr const int kNumRetriesWhenCheckpointingEnabled = 1;

bool IsBootstrapApex(const ApexFile& apex) {
  if (IsVendorApex(apex) && apex.GetManifest().vendorbootstrap()) {
    return true;
  }
  return std::find(kBootstrapApexes.begin(), kBootstrapApexes.end(),
                   apex.GetManifest().name()) != kBootstrapApexes.end();
}

void ReleaseF2fsCompressedBlocks(const std::string& file_path) {
  unique_fd fd(
      TEMP_FAILURE_RETRY(open(file_path.c_str(), O_RDONLY | O_CLOEXEC, 0)));
  if (fd.get() == -1) {
    PLOG(ERROR) << "Failed to open " << file_path;
    return;
  }
  unsigned int flags;
  if (ioctl(fd, FS_IOC_GETFLAGS, &flags) == -1) {
    PLOG(ERROR) << "Failed to call FS_IOC_GETFLAGS on " << file_path;
    return;
  }
  if ((flags & FS_COMPR_FL) == 0) {
    // Doesn't support f2fs-compression.
    return;
  }
  uint64_t blk_cnt;
  if (ioctl(fd, F2FS_IOC_RELEASE_COMPRESS_BLOCKS, &blk_cnt) == -1) {
    PLOG(ERROR) << "Failed to call F2FS_IOC_RELEASE_COMPRESS_BLOCKS on "
                << file_path;
  }
  LOG(INFO) << "Released " << blk_cnt << " compressed blocks from "
            << file_path;
}

std::unique_ptr<DmTable> CreateVerityTable(const ApexVerityData& verity_data,
                                           const std::string& block_device,
                                           const std::string& hash_device,
                                           bool restart_on_corruption) {
  AvbHashtreeDescriptor* desc = verity_data.desc.get();
  auto table = std::make_unique<DmTable>();

  uint32_t hash_start_block = 0;
  if (hash_device == block_device) {
    hash_start_block = desc->tree_offset / desc->hash_block_size;
  }

  auto target = std::make_unique<DmTargetVerity>(
      0, desc->image_size / 512, desc->dm_verity_version, block_device,
      hash_device, desc->data_block_size, desc->hash_block_size,
      desc->image_size / desc->data_block_size, hash_start_block,
      verity_data.hash_algorithm, verity_data.root_digest, verity_data.salt);

  target->IgnoreZeroBlocks();
  if (restart_on_corruption) {
    target->SetVerityMode(kDmVerityRestartOnCorruption);
  }
  table->AddTarget(std::move(target));

  table->set_readonly(true);

  return table;
}

// Deletes a dm-verity device with a given name and path
// Synchronizes on the device actually being deleted from userspace.
Result<void> DeleteVerityDevice(const std::string& name, bool deferred) {
  DeviceMapper& dm = DeviceMapper::Instance();
  if (deferred) {
    if (!dm.DeleteDeviceDeferred(name)) {
      return ErrnoError() << "Failed to issue deferred delete of verity device "
                          << name;
    }
    return {};
  }
  auto timeout = std::chrono::milliseconds(
      android::sysprop::ApexProperties::dm_delete_timeout().value_or(750));
  if (!dm.DeleteDevice(name, timeout)) {
    return Error() << "Failed to delete dm-device " << name;
  }
  return {};
}

class DmVerityDevice {
 public:
  DmVerityDevice() : cleared_(true) {}
  explicit DmVerityDevice(std::string name)
      : name_(std::move(name)), cleared_(false) {}
  DmVerityDevice(std::string name, std::string dev_path)
      : name_(std::move(name)),
        dev_path_(std::move(dev_path)),
        cleared_(false) {}

  DmVerityDevice(DmVerityDevice&& other) noexcept
      : name_(std::move(other.name_)),
        dev_path_(std::move(other.dev_path_)),
        cleared_(other.cleared_) {
    other.cleared_ = true;
  }

  DmVerityDevice& operator=(DmVerityDevice&& other) noexcept {
    name_ = other.name_;
    dev_path_ = other.dev_path_;
    cleared_ = other.cleared_;
    other.cleared_ = true;
    return *this;
  }

  ~DmVerityDevice() {
    if (!cleared_) {
      Result<void> ret = DeleteVerityDevice(name_, /* deferred= */ false);
      if (!ret.ok()) {
        LOG(ERROR) << ret.error();
      }
    }
  }

  const std::string& GetName() const { return name_; }
  const std::string& GetDevPath() const { return dev_path_; }

  void Release() { cleared_ = true; }

 private:
  std::string name_;
  std::string dev_path_;
  bool cleared_;
};

Result<DmVerityDevice> CreateVerityDevice(
    DeviceMapper& dm, const std::string& name, const DmTable& table,
    const std::chrono::milliseconds& timeout) {
  std::string dev_path;
  if (!dm.CreateDevice(name, table, &dev_path, timeout)) {
    return Errorf("Couldn't create verity device.");
  }
  return DmVerityDevice(name, dev_path);
}

Result<DmVerityDevice> CreateVerityDevice(const std::string& name,
                                          const DmTable& table,
                                          bool reuse_device) {
  ATRACE_NAME("CreateVerityDevice");
  LOG(VERBOSE) << "Creating verity device " << name;
  auto timeout = std::chrono::milliseconds(
      android::sysprop::ApexProperties::dm_create_timeout().value_or(1000));

  DeviceMapper& dm = DeviceMapper::Instance();

  auto state = dm.GetState(name);
  if (state == DmDeviceState::INVALID) {
    return CreateVerityDevice(dm, name, table, timeout);
  }

  if (reuse_device) {
    if (state == DmDeviceState::ACTIVE) {
      LOG(WARNING) << "Deleting existing active dm device " << name;
      if (auto r = DeleteVerityDevice(name, /* deferred= */ false); !r.ok()) {
        return r.error();
      }
      return CreateVerityDevice(dm, name, table, timeout);
    }
    if (!dm.LoadTableAndActivate(name, table)) {
      dm.DeleteDevice(name);
      return Error() << "Failed to activate dm device " << name;
    }
    std::string path;
    if (!dm.WaitForDevice(name, timeout, &path)) {
      dm.DeleteDevice(name);
      return Error() << "Failed waiting for dm device " << name;
    }
    return DmVerityDevice(name, path);
  } else {
    if (state != DmDeviceState::INVALID) {
      // Delete dangling dm-device. This can happen if apexd fails to delete it
      // while unmounting an apex.
      LOG(WARNING) << "Deleting existing dm device " << name;
      if (auto r = DeleteVerityDevice(name, /* deferred= */ false); !r.ok()) {
        return r.error();
      }
    }
    return CreateVerityDevice(dm, name, table, timeout);
  }
}

/**
 * When we create hardlink for a new apex package in kActiveApexPackagesDataDir,
 * there might be an older version of the same package already present in there.
 * Since a new version of the same package is being installed on this boot, the
 * old one needs to deleted so that we don't end up activating same package
 * twice.
 *
 * @param affected_packages package names of the news apex that are being
 * installed in this boot
 * @param files_to_keep path to the new apex packages in
 * kActiveApexPackagesDataDir
 */
Result<void> RemovePreviouslyActiveApexFiles(
    const std::unordered_set<std::string>& affected_packages,
    const std::unordered_set<std::string>& files_to_keep) {
  auto all_active_apex_files =
      FindFilesBySuffix(gConfig->active_apex_data_dir, {kApexPackageSuffix});

  if (!all_active_apex_files.ok()) {
    return all_active_apex_files.error();
  }

  for (const std::string& path : *all_active_apex_files) {
    Result<ApexFile> apex_file = ApexFile::Open(path);
    if (!apex_file.ok()) {
      return apex_file.error();
    }

    const std::string& package_name = apex_file->GetManifest().name();
    if (affected_packages.find(package_name) == affected_packages.end()) {
      // This apex belongs to a package that wasn't part of this stage sessions,
      // hence it should be kept.
      continue;
    }

    if (files_to_keep.find(apex_file->GetPath()) != files_to_keep.end()) {
      // This is a path that was staged and should be kept.
      continue;
    }

    LOG(DEBUG) << "Deleting previously active apex " << apex_file->GetPath();
    if (unlink(apex_file->GetPath().c_str()) != 0) {
      return ErrnoError() << "Failed to unlink " << apex_file->GetPath();
    }
  }

  return {};
}

// Reads the entire device to verify the image is authenticatic
Result<void> ReadVerityDevice(const std::string& verity_device,
                              uint64_t device_size) {
  static constexpr int kBlockSize = 4096;
  static constexpr size_t kBufSize = 1024 * kBlockSize;
  std::vector<uint8_t> buffer(kBufSize);

  unique_fd fd(
      TEMP_FAILURE_RETRY(open(verity_device.c_str(), O_RDONLY | O_CLOEXEC)));
  if (fd.get() == -1) {
    return ErrnoError() << "Can't open " << verity_device;
  }

  size_t bytes_left = device_size;
  while (bytes_left > 0) {
    size_t to_read = std::min(bytes_left, kBufSize);
    if (!android::base::ReadFully(fd.get(), buffer.data(), to_read)) {
      return ErrnoError() << "Can't verify " << verity_device << "; corrupted?";
    }
    bytes_left -= to_read;
  }

  return {};
}

Result<void> VerifyMountedImage(const ApexFile& apex,
                                const std::string& mount_point) {
  // Verify that apex_manifest.pb inside mounted image matches the one in the
  // outer .apex container.
  Result<ApexManifest> verified_manifest =
      ReadManifest(mount_point + "/" + kManifestFilenamePb);
  if (!verified_manifest.ok()) {
    return verified_manifest.error();
  }
  if (!MessageDifferencer::Equals(*verified_manifest, apex.GetManifest())) {
    return Errorf(
        "Manifest inside filesystem does not match manifest outside it");
  }
  if (shim::IsShimApex(apex)) {
    return shim::ValidateShimApex(mount_point, apex);
  }
  return {};
}

Result<MountedApexData> MountPackageImpl(const ApexFile& apex,
                                         const std::string& mount_point,
                                         const std::string& device_name,
                                         const std::string& hashtree_file,
                                         bool verify_image, bool reuse_device,
                                         bool temp_mount = false) {
  auto tag = "MountPackageImpl: " + apex.GetManifest().name();
  ATRACE_NAME(tag.c_str());
  if (apex.IsCompressed()) {
    return Error() << "Cannot directly mount compressed APEX "
                   << apex.GetPath();
  }

  LOG(VERBOSE) << "Creating mount point: " << mount_point;
  auto time_started = boot_clock::now();
  // Note: the mount point could exist in case when the APEX was activated
  // during the bootstrap phase (e.g., the runtime or tzdata APEX).
  // Although we have separate mount namespaces to separate the early activated
  // APEXes from the normally activate APEXes, the mount points themselves
  // are shared across the two mount namespaces because /apex (a tmpfs) itself
  // mounted at / which is (and has to be) a shared mount. Therefore, if apexd
  // finds an empty directory under /apex, it's not a problem and apexd can use
  // it.
  auto exists = PathExists(mount_point);
  if (!exists.ok()) {
    return exists.error();
  }
  if (!*exists && mkdir(mount_point.c_str(), kMkdirMode) != 0) {
    return ErrnoError() << "Could not create mount point " << mount_point;
  }
  auto deleter = [&mount_point]() {
    if (rmdir(mount_point.c_str()) != 0) {
      PLOG(WARNING) << "Could not rmdir " << mount_point;
    }
  };
  auto scope_guard = android::base::make_scope_guard(deleter);
  if (!IsEmptyDirectory(mount_point)) {
    return ErrnoError() << mount_point << " is not empty";
  }

  const std::string& full_path = apex.GetPath();

  if (!apex.GetImageOffset() || !apex.GetImageSize()) {
    return Error() << "Cannot create mount point without image offset and size";
  }
  loop::LoopbackDeviceUniqueFd loopback_device;
  for (size_t attempts = 1;; ++attempts) {
    Result<loop::LoopbackDeviceUniqueFd> ret =
        loop::CreateAndConfigureLoopDevice(full_path,
                                           apex.GetImageOffset().value(),
                                           apex.GetImageSize().value());
    if (ret.ok()) {
      loopback_device = std::move(*ret);
      break;
    }
    if (attempts >= kLoopDeviceSetupAttempts) {
      return Error() << "Could not create loop device for " << full_path << ": "
                     << ret.error();
    }
  }
  LOG(VERBOSE) << "Loopback device created: " << loopback_device.name;

  auto& instance = ApexFileRepository::GetInstance();

  auto public_key = instance.GetPublicKey(apex.GetManifest().name());
  if (!public_key.ok()) {
    return public_key.error();
  }

  auto verity_data = apex.VerifyApexVerity(*public_key);
  if (!verity_data.ok()) {
    return Error() << "Failed to verify Apex Verity data for " << full_path
                   << ": " << verity_data.error();
  }
  if (instance.IsBlockApex(apex)) {
    auto root_digest = instance.GetBlockApexRootDigest(apex.GetPath());
    if (root_digest.has_value() &&
        root_digest.value() != verity_data->root_digest) {
      return Error() << "Failed to verify Apex Verity data for " << full_path
                     << ": root digest (" << verity_data->root_digest
                     << ") mismatches with the one (" << root_digest.value()
                     << ") specified in config";
    }
  }

  std::string block_device = loopback_device.name;
  MountedApexData apex_data(apex.GetManifest().version(), loopback_device.name,
                            apex.GetPath(), mount_point,
                            /* device_name = */ "",
                            /* hashtree_loop_name = */ "",
                            /* is_temp_mount */ temp_mount);

  // for APEXes in immutable partitions, we don't need to mount them on
  // dm-verity because they are already in the dm-verity protected partition;
  // system. However, note that we don't skip verification to ensure that APEXes
  // are correctly signed.
  const bool mount_on_verity = !instance.IsPreInstalledApex(apex) ||
                               // decompressed apexes are on /data
                               instance.IsDecompressedApex(apex) ||
                               // block apexes are from host
                               instance.IsBlockApex(apex);

  DmVerityDevice verity_dev;
  loop::LoopbackDeviceUniqueFd loop_for_hash;
  if (mount_on_verity) {
    std::string hash_device = loopback_device.name;
    if (verity_data->desc->tree_size == 0) {
      if (auto st = PrepareHashTree(apex, *verity_data, hashtree_file);
          !st.ok()) {
        return st.error();
      }
      auto create_loop_status =
          loop::CreateAndConfigureLoopDevice(hashtree_file,
                                             /* image_offset= */ 0,
                                             /* image_size= */ 0);
      if (!create_loop_status.ok()) {
        return create_loop_status.error();
      }
      loop_for_hash = std::move(*create_loop_status);
      hash_device = loop_for_hash.name;
      apex_data.hashtree_loop_name = hash_device;
    }
    auto verity_table =
        CreateVerityTable(*verity_data, loopback_device.name, hash_device,
                          /* restart_on_corruption = */ !verify_image);
    Result<DmVerityDevice> verity_dev_res =
        CreateVerityDevice(device_name, *verity_table, reuse_device);
    if (!verity_dev_res.ok()) {
      return Error() << "Failed to create Apex Verity device " << full_path
                     << ": " << verity_dev_res.error();
    }
    verity_dev = std::move(*verity_dev_res);
    apex_data.device_name = device_name;
    block_device = verity_dev.GetDevPath();

    Result<void> read_ahead_status =
        loop::ConfigureReadAhead(verity_dev.GetDevPath());
    if (!read_ahead_status.ok()) {
      return read_ahead_status.error();
    }
  }
  // TODO(b/158467418): consider moving this inside RunVerifyFnInsideTempMount.
  if (mount_on_verity && verify_image) {
    Result<void> verity_status =
        ReadVerityDevice(block_device, (*verity_data).desc->image_size);
    if (!verity_status.ok()) {
      return verity_status.error();
    }
  }

  uint32_t mount_flags = MS_NOATIME | MS_NODEV | MS_DIRSYNC | MS_RDONLY;
  if (apex.GetManifest().nocode()) {
    mount_flags |= MS_NOEXEC;
  }

  if (!apex.GetFsType()) {
    return Error() << "Cannot mount package without FsType";
  }
  if (mount(block_device.c_str(), mount_point.c_str(),
            apex.GetFsType().value().c_str(), mount_flags, nullptr) == 0) {
    auto time_elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        boot_clock::now() - time_started).count();
    LOG(INFO) << "Successfully mounted package " << full_path << " on "
              << mount_point << " duration=" << time_elapsed;
    auto status = VerifyMountedImage(apex, mount_point);
    if (!status.ok()) {
      if (umount2(mount_point.c_str(), UMOUNT_NOFOLLOW) != 0) {
        PLOG(ERROR) << "Failed to umount " << mount_point;
      }
      return Error() << "Failed to verify " << full_path << ": "
                     << status.error();
    }
    // Time to accept the temporaries as good.
    verity_dev.Release();
    loopback_device.CloseGood();
    loop_for_hash.CloseGood();

    scope_guard.Disable();  // Accept the mount.
    return apex_data;
  } else {
    return ErrnoError() << "Mounting failed for package " << full_path;
  }
}

std::string GetHashTreeFileName(const ApexFile& apex, bool is_new) {
  const std::string& id = GetPackageId(apex.GetManifest());
  std::string ret =
      StringPrintf("%s/%s", gConfig->apex_hash_tree_dir, id.c_str());
  return is_new ? ret + ".new" : ret;
}

Result<MountedApexData> VerifyAndTempMountPackage(
    const ApexFile& apex, const std::string& mount_point) {
  const std::string& package_id = GetPackageId(apex.GetManifest());
  LOG(DEBUG) << "Temp mounting " << package_id << " to " << mount_point;
  const std::string& temp_device_name = package_id + ".tmp";
  std::string hashtree_file = GetHashTreeFileName(apex, /* is_new = */ true);
  if (access(hashtree_file.c_str(), F_OK) == 0) {
    LOG(DEBUG) << hashtree_file << " already exists. Deleting it";
    if (TEMP_FAILURE_RETRY(unlink(hashtree_file.c_str())) != 0) {
      return ErrnoError() << "Failed to unlink " << hashtree_file;
    }
  }
  auto ret =
      MountPackageImpl(apex, mount_point, temp_device_name, hashtree_file,
                       /* verify_image = */ true, /* reuse_device= */ false,
                       /* temp_mount = */ true);
  if (!ret.ok()) {
    LOG(DEBUG) << "Cleaning up " << hashtree_file;
    if (TEMP_FAILURE_RETRY(unlink(hashtree_file.c_str())) != 0) {
      PLOG(ERROR) << "Failed to unlink " << hashtree_file;
    }
  } else {
    gMountedApexes.AddMountedApex(apex.GetManifest().name(), *ret);
  }
  return ret;
}

}  // namespace

Result<void> Unmount(const MountedApexData& data, bool deferred) {
  LOG(DEBUG) << "Unmounting " << data.full_path << " from mount point "
             << data.mount_point << " deferred = " << deferred;
  // Unmount whatever is mounted.
  if (umount2(data.mount_point.c_str(), UMOUNT_NOFOLLOW) != 0 &&
      errno != EINVAL && errno != ENOENT) {
    return ErrnoError() << "Failed to unmount directory " << data.mount_point;
  }

  if (!deferred) {
    if (rmdir(data.mount_point.c_str()) != 0) {
      PLOG(ERROR) << "Failed to rmdir " << data.mount_point;
    }
  }

  // Try to free up the device-mapper device.
  if (!data.device_name.empty()) {
    const auto& result = DeleteVerityDevice(data.device_name, deferred);
    if (!result.ok()) {
      return result;
    }
  }

  // Try to free up the loop device.
  auto log_fn = [](const std::string& path, const std::string& /*id*/) {
    LOG(VERBOSE) << "Freeing loop device " << path << " for unmount.";
  };

  // Since we now use LO_FLAGS_AUTOCLEAR when configuring loop devices, in
  // theory we don't need to manually call DestroyLoopDevice here even if
  // |deferred| is false. However we prefer to call it to ensure the invariant
  // of SubmitStagedSession (after it's done, loop devices created for temp
  // mount are freed).
  if (!data.loop_name.empty() && !deferred) {
    loop::DestroyLoopDevice(data.loop_name, log_fn);
  }
  if (!data.hashtree_loop_name.empty() && !deferred) {
    loop::DestroyLoopDevice(data.hashtree_loop_name, log_fn);
  }

  return {};
}

namespace {

template <typename VerifyFn>
Result<void> RunVerifyFnInsideTempMount(const ApexFile& apex,
                                        const VerifyFn& verify_fn,
                                        bool unmount_during_cleanup) {
  // Temp mount image of this apex to validate it was properly signed;
  // this will also read the entire block device through dm-verity, so
  // we can be sure there is no corruption.
  const std::string& temp_mount_point =
      apexd_private::GetPackageTempMountPoint(apex.GetManifest());

  Result<MountedApexData> mount_status =
      VerifyAndTempMountPackage(apex, temp_mount_point);
  if (!mount_status.ok()) {
    LOG(ERROR) << "Failed to temp mount to " << temp_mount_point << " : "
               << mount_status.error();
    return mount_status.error();
  }
  auto cleaner = [&]() {
    LOG(DEBUG) << "Unmounting " << temp_mount_point;
    Result<void> result = Unmount(*mount_status, /* deferred= */ false);
    if (!result.ok()) {
      LOG(WARNING) << "Failed to unmount " << temp_mount_point << " : "
                   << result.error();
    }
    gMountedApexes.RemoveMountedApex(apex.GetManifest().name(), apex.GetPath(),
                                     true);
  };
  auto scope_guard = android::base::make_scope_guard(cleaner);
  auto result = verify_fn(temp_mount_point);
  if (!result.ok()) {
    return result.error();
  }
  if (!unmount_during_cleanup) {
    scope_guard.Disable();
  }
  return {};
}

// Converts a list of apex file paths into a list of ApexFile objects
//
// Returns error when trying to open empty set of inputs.
Result<std::vector<ApexFile>> OpenApexFiles(
    const std::vector<std::string>& paths) {
  if (paths.empty()) {
    return Errorf("Empty set of inputs");
  }
  std::vector<ApexFile> ret;
  for (const std::string& path : paths) {
    Result<ApexFile> apex_file = ApexFile::Open(path);
    if (!apex_file.ok()) {
      return apex_file.error();
    }
    ret.emplace_back(std::move(*apex_file));
  }
  return ret;
}

Result<void> ValidateStagingShimApex(const ApexFile& to) {
  using android::base::StringPrintf;
  auto system_shim = ApexFile::Open(
      StringPrintf("%s/%s", kApexPackageSystemDir, shim::kSystemShimApexName));
  if (!system_shim.ok()) {
    return system_shim.error();
  }
  auto verify_fn = [&](const std::string& system_apex_path) {
    return shim::ValidateUpdate(system_apex_path, to.GetPath());
  };
  return RunVerifyFnInsideTempMount(*system_shim, verify_fn, true);
}

Result<void> VerifyVndkVersion(const ApexFile& apex_file) {
  const std::string& vndk_version = apex_file.GetManifest().vndkversion();
  if (vndk_version.empty()) {
    return {};
  }

  static std::string vendor_vndk_version = GetProperty("ro.vndk.version", "");
  static std::string product_vndk_version =
      GetProperty("ro.product.vndk.version", "");

  const auto& instance = ApexFileRepository::GetInstance();
  const auto& preinstalled =
      instance.GetPreInstalledApex(apex_file.GetManifest().name());
  const auto& preinstalled_path = preinstalled.get().GetPath();
  if (StartsWith(preinstalled_path, "/vendor/apex/") ||
      StartsWith(preinstalled_path, "/system/vendor/apex/")) {
    if (vndk_version != vendor_vndk_version) {
      return Error() << "vndkVersion(" << vndk_version
                     << ") doesn't match with device VNDK version("
                     << vendor_vndk_version << ")";
    }
    return {};
  }
  if (StartsWith(preinstalled_path, "/product/apex/") ||
      StartsWith(preinstalled_path, "/system/product/apex/")) {
    if (vndk_version != product_vndk_version) {
      return Error() << "vndkVersion(" << vndk_version
                     << ") doesn't match with device VNDK version("
                     << product_vndk_version << ")";
    }
    return {};
  }
  return Error() << "vndkVersion(" << vndk_version << ") is set";
}

// A version of apex verification that happens during boot.
// This function should only verification checks that are necessary to run on
// each boot. Try to avoid putting expensive checks inside this function.
Result<void> VerifyPackageBoot(const ApexFile& apex_file) {
  // TODO(ioffe): why do we need this here?
  auto& instance = ApexFileRepository::GetInstance();
  auto public_key = instance.GetPublicKey(apex_file.GetManifest().name());
  if (!public_key.ok()) {
    return public_key.error();
  }
  Result<ApexVerityData> verity_or = apex_file.VerifyApexVerity(*public_key);
  if (!verity_or.ok()) {
    return verity_or.error();
  }

  if (shim::IsShimApex(apex_file)) {
    // Validating shim is not a very cheap operation, but it's fine to perform
    // it here since it only runs during CTS tests and will never be triggered
    // during normal flow.
    const auto& result = ValidateStagingShimApex(apex_file);
    if (!result.ok()) {
      return result;
    }
  }

  if (auto result = VerifyVndkVersion(apex_file); !result.ok()) {
    return result;
  }

  return {};
}

// A version of apex verification that happens on SubmitStagedSession.
// This function contains checks that might be expensive to perform, e.g. temp
// mounting a package and reading entire dm-verity device, and shouldn't be run
// during boot.
Result<void> VerifyPackageStagedInstall(const ApexFile& apex_file) {
  const auto& verify_package_boot_status = VerifyPackageBoot(apex_file);
  if (!verify_package_boot_status.ok()) {
    return verify_package_boot_status;
  }

  const auto validate_fn = [&apex_file](const std::string& mount_point) {
    if (IsVendorApex(apex_file)) {
      return CheckVendorApexUpdate(apex_file, mount_point);
    }
    return Result<void>{};
  };
  return RunVerifyFnInsideTempMount(apex_file, validate_fn, false);
}

template <typename VerifyApexFn>
Result<std::vector<ApexFile>> VerifyPackages(
    const std::vector<std::string>& paths, const VerifyApexFn& verify_apex_fn) {
  Result<std::vector<ApexFile>> apex_files = OpenApexFiles(paths);
  if (!apex_files.ok()) {
    return apex_files.error();
  }

  LOG(DEBUG) << "VerifyPackages() for " << Join(paths, ',');

  for (const ApexFile& apex_file : *apex_files) {
    Result<void> result = verify_apex_fn(apex_file);
    if (!result.ok()) {
      return result.error();
    }
  }
  return std::move(*apex_files);
}

Result<ApexFile> VerifySessionDir(int session_id) {
  std::string session_dir_path =
      StringPrintf("%s/session_%d", gConfig->staged_session_dir, session_id);
  LOG(INFO) << "Scanning " << session_dir_path
            << " looking for packages to be validated";
  Result<std::vector<std::string>> scan =
      FindFilesBySuffix(session_dir_path, {kApexPackageSuffix});
  if (!scan.ok()) {
    LOG(WARNING) << scan.error();
    return scan.error();
  }

  if (scan->size() > 1) {
    return Errorf(
        "More than one APEX package found in the same session directory.");
  }

  auto verified = VerifyPackages(*scan, VerifyPackageStagedInstall);
  if (!verified.ok()) {
    return verified.error();
  }
  return std::move((*verified)[0]);
}

Result<void> DeleteBackup() {
  auto exists = PathExists(std::string(kApexBackupDir));
  if (!exists.ok()) {
    return Error() << "Can't clean " << kApexBackupDir << " : "
                   << exists.error();
  }
  if (!*exists) {
    LOG(DEBUG) << kApexBackupDir << " does not exist. Nothing to clean";
    return {};
  }
  return DeleteDirContent(std::string(kApexBackupDir));
}

Result<void> BackupActivePackages() {
  LOG(DEBUG) << "Initializing  backup of " << gConfig->active_apex_data_dir;

  // Previous restore might've delete backups folder.
  auto create_status = CreateDirIfNeeded(kApexBackupDir, 0700);
  if (!create_status.ok()) {
    return Error() << "Backup failed : " << create_status.error();
  }

  auto apex_active_exists =
      PathExists(std::string(gConfig->active_apex_data_dir));
  if (!apex_active_exists.ok()) {
    return Error() << "Backup failed : " << apex_active_exists.error();
  }
  if (!*apex_active_exists) {
    LOG(DEBUG) << gConfig->active_apex_data_dir
               << " does not exist. Nothing to backup";
    return {};
  }

  auto active_packages =
      FindFilesBySuffix(gConfig->active_apex_data_dir, {kApexPackageSuffix});
  if (!active_packages.ok()) {
    return Error() << "Backup failed : " << active_packages.error();
  }

  auto cleanup_status = DeleteBackup();
  if (!cleanup_status.ok()) {
    return Error() << "Backup failed : " << cleanup_status.error();
  }

  auto backup_path_fn = [](const ApexFile& apex_file) {
    return StringPrintf("%s/%s%s", kApexBackupDir,
                        GetPackageId(apex_file.GetManifest()).c_str(),
                        kApexPackageSuffix);
  };

  auto deleter = []() {
    auto result = DeleteDirContent(std::string(kApexBackupDir));
    if (!result.ok()) {
      LOG(ERROR) << "Failed to cleanup " << kApexBackupDir << " : "
                 << result.error();
    }
  };
  auto scope_guard = android::base::make_scope_guard(deleter);

  for (const std::string& path : *active_packages) {
    Result<ApexFile> apex_file = ApexFile::Open(path);
    if (!apex_file.ok()) {
      return Error() << "Backup failed : " << apex_file.error();
    }
    const auto& dest_path = backup_path_fn(*apex_file);
    if (link(apex_file->GetPath().c_str(), dest_path.c_str()) != 0) {
      return ErrnoError() << "Failed to backup " << apex_file->GetPath();
    }
  }

  scope_guard.Disable();  // Accept the backup.
  return {};
}

Result<void> RestoreActivePackages() {
  LOG(DEBUG) << "Initializing  restore of " << gConfig->active_apex_data_dir;

  auto backup_exists = PathExists(std::string(kApexBackupDir));
  if (!backup_exists.ok()) {
    return backup_exists.error();
  }
  if (!*backup_exists) {
    return Error() << kApexBackupDir << " does not exist";
  }

  struct stat stat_data;
  if (stat(gConfig->active_apex_data_dir, &stat_data) != 0) {
    return ErrnoError() << "Failed to access " << gConfig->active_apex_data_dir;
  }

  LOG(DEBUG) << "Deleting existing packages in "
             << gConfig->active_apex_data_dir;
  auto delete_status =
      DeleteDirContent(std::string(gConfig->active_apex_data_dir));
  if (!delete_status.ok()) {
    return delete_status;
  }

  LOG(DEBUG) << "Renaming " << kApexBackupDir << " to "
             << gConfig->active_apex_data_dir;
  if (rename(kApexBackupDir, gConfig->active_apex_data_dir) != 0) {
    return ErrnoError() << "Failed to rename " << kApexBackupDir << " to "
                        << gConfig->active_apex_data_dir;
  }

  LOG(DEBUG) << "Restoring original permissions for "
             << gConfig->active_apex_data_dir;
  if (chmod(gConfig->active_apex_data_dir, stat_data.st_mode & ALLPERMS) != 0) {
    return ErrnoError() << "Failed to restore original permissions for "
                        << gConfig->active_apex_data_dir;
  }

  return {};
}

Result<void> UnmountPackage(const ApexFile& apex, bool allow_latest,
                            bool deferred, bool detach_mount_point) {
  LOG(INFO) << "Unmounting " << GetPackageId(apex.GetManifest())
            << " allow_latest : " << allow_latest << " deferred : " << deferred
            << " detach_mount_point : " << detach_mount_point;

  const ApexManifest& manifest = apex.GetManifest();

  std::optional<MountedApexData> data;
  bool latest = false;

  auto fn = [&](const MountedApexData& d, bool l) {
    if (d.full_path == apex.GetPath()) {
      data.emplace(d);
      latest = l;
    }
  };
  gMountedApexes.ForallMountedApexes(manifest.name(), fn);

  if (!data) {
    return Error() << "Did not find " << apex.GetPath();
  }

  // Concept of latest sharedlibs apex is somewhat blurred. Since this is only
  // used in testing, it is ok to always allow unmounting sharedlibs apex.
  if (latest && !manifest.providesharedapexlibs()) {
    if (!allow_latest) {
      return Error() << "Package " << apex.GetPath() << " is active";
    }
    std::string mount_point = apexd_private::GetActiveMountPoint(manifest);
    LOG(INFO) << "Unmounting " << mount_point;
    int flags = UMOUNT_NOFOLLOW;
    if (detach_mount_point) {
      flags |= MNT_DETACH;
    }
    if (umount2(mount_point.c_str(), flags) != 0) {
      return ErrnoError() << "Failed to unmount " << mount_point;
    }

    if (!deferred) {
      if (rmdir(mount_point.c_str()) != 0) {
        PLOG(ERROR) << "Failed to rmdir " << mount_point;
      }
    }
  }

  // Clean up gMountedApexes now, even though we're not fully done.
  gMountedApexes.RemoveMountedApex(manifest.name(), apex.GetPath());
  return Unmount(*data, deferred);
}

}  // namespace

void SetConfig(const ApexdConfig& config) { gConfig = config; }

Result<void> MountPackage(const ApexFile& apex, const std::string& mount_point,
                          const std::string& device_name, bool reuse_device,
                          bool temp_mount) {
  auto ret =
      MountPackageImpl(apex, mount_point, device_name,
                       GetHashTreeFileName(apex, /* is_new= */ false),
                       /* verify_image = */ false, reuse_device, temp_mount);
  if (!ret.ok()) {
    return ret.error();
  }

  gMountedApexes.AddMountedApex(apex.GetManifest().name(), *ret);
  return {};
}

namespace apexd_private {

Result<void> UnmountTempMount(const ApexFile& apex) {
  const ApexManifest& manifest = apex.GetManifest();
  LOG(VERBOSE) << "Unmounting all temp mounts for package " << manifest.name();

  bool finished_unmounting = false;
  // If multiple temp mounts exist, ensure that all are unmounted.
  while (!finished_unmounting) {
    Result<MountedApexData> data =
        apexd_private::GetTempMountedApexData(manifest.name());
    if (!data.ok()) {
      finished_unmounting = true;
    } else {
      gMountedApexes.RemoveMountedApex(manifest.name(), data->full_path, true);
      Unmount(*data, /* deferred= */ false);
    }
  }
  return {};
}

Result<MountedApexData> GetTempMountedApexData(const std::string& package) {
  bool found = false;
  Result<MountedApexData> mount_data;
  gMountedApexes.ForallMountedApexes(
      package,
      [&](const MountedApexData& data, [[maybe_unused]] bool latest) {
        if (!found) {
          mount_data = data;
          found = true;
        }
      },
      true);
  if (found) {
    return mount_data;
  }
  return Error() << "No temp mount data found for " << package;
}

bool IsMounted(const std::string& full_path) {
  bool found_mounted = false;
  gMountedApexes.ForallMountedApexes([&](const std::string&,
                                         const MountedApexData& data,
                                         [[maybe_unused]] bool latest) {
    if (full_path == data.full_path) {
      found_mounted = true;
    }
  });
  return found_mounted;
}

std::string GetPackageMountPoint(const ApexManifest& manifest) {
  return StringPrintf("%s/%s", kApexRoot, GetPackageId(manifest).c_str());
}

std::string GetPackageTempMountPoint(const ApexManifest& manifest) {
  return StringPrintf("%s.tmp", GetPackageMountPoint(manifest).c_str());
}

std::string GetActiveMountPoint(const ApexManifest& manifest) {
  return StringPrintf("%s/%s", kApexRoot, manifest.name().c_str());
}

}  // namespace apexd_private

Result<void> ResumeRevertIfNeeded() {
  auto sessions =
      gSessionManager->GetSessionsInState(SessionState::REVERT_IN_PROGRESS);
  if (sessions.empty()) {
    return {};
  }
  return RevertActiveSessions("", "");
}

Result<void> ContributeToSharedLibs(const std::string& mount_point) {
  for (const auto& lib_path : {"lib", "lib64"}) {
    std::string apex_lib_path = mount_point + "/" + lib_path;
    auto lib_dir = PathExists(apex_lib_path);
    if (!lib_dir.ok() || !*lib_dir) {
      continue;
    }

    auto iter = std::filesystem::directory_iterator(apex_lib_path);
    std::error_code ec;

    while (iter != std::filesystem::end(iter)) {
      const auto& lib_entry = *iter;
      if (!lib_entry.is_directory()) {
        iter = iter.increment(ec);
        if (ec) {
          return Error() << "Failed to scan " << apex_lib_path << " : "
                         << ec.message();
        }
        continue;
      }

      const auto library_name = lib_entry.path().filename();
      const std::string library_symlink_dir =
          StringPrintf("%s/%s/%s/%s", kApexRoot, kApexSharedLibsSubDir,
                       lib_path, library_name.c_str());

      auto symlink_dir = PathExists(library_symlink_dir);
      if (!symlink_dir.ok() || !*symlink_dir) {
        std::filesystem::create_directory(library_symlink_dir, ec);
        if (ec) {
          return Error() << "Failed to create directory " << library_symlink_dir
                         << ": " << ec.message();
        }
      }

      auto inner_iter =
          std::filesystem::directory_iterator(lib_entry.path().string());

      while (inner_iter != std::filesystem::end(inner_iter)) {
        const auto& lib_items = *inner_iter;
        const auto hash_value = lib_items.path().filename();
        const std::string library_symlink_hash = StringPrintf(
            "%s/%s", library_symlink_dir.c_str(), hash_value.c_str());

        auto hash_dir = PathExists(library_symlink_hash);
        if (hash_dir.ok() && *hash_dir) {
          // Compare file size for two library files with same name and hash
          // value
          auto existing_file_path =
              library_symlink_hash + "/" + library_name.string();
          auto existing_file_size = GetFileSize(existing_file_path);
          if (!existing_file_size.ok()) {
            return existing_file_size.error();
          }

          auto new_file_path =
              lib_items.path().string() + "/" + library_name.string();
          auto new_file_size = GetFileSize(new_file_path);
          if (!new_file_size.ok()) {
            return new_file_size.error();
          }

          if (*existing_file_size != *new_file_size) {
            return Error() << "There are two libraries with same hash and "
                              "different file size : "
                           << existing_file_path << " and " << new_file_path;
          }

          inner_iter = inner_iter.increment(ec);
          if (ec) {
            return Error() << "Failed to scan " << lib_entry.path().string()
                           << " : " << ec.message();
          }
          continue;
        }
        std::filesystem::create_directory_symlink(lib_items.path(),
                                                  library_symlink_hash, ec);
        if (ec) {
          return Error() << "Failed to create symlink from " << lib_items.path()
                         << " to " << library_symlink_hash << ec.message();
        }

        inner_iter = inner_iter.increment(ec);
        if (ec) {
          return Error() << "Failed to scan " << lib_entry.path().string()
                         << " : " << ec.message();
        }
      }

      iter = iter.increment(ec);
      if (ec) {
        return Error() << "Failed to scan " << apex_lib_path << " : "
                       << ec.message();
      }
    }
  }

  return {};
}

bool IsValidPackageName(const std::string& package_name) {
  return kBannedApexName.count(package_name) == 0;
}

// Activates given APEX file.
//
// In a nutshel activation of an APEX consist of the following steps:
//   1. Create loop devices that is backed by the given apex_file
//   2. If apex_file resides on /data partition then create a dm-verity device
//    backed by the loop device created in step (1).
//   3. Create a mount point under /apex for this APEX.
//   4. Mount the dm-verity device on that mount point.
//     4.1 In case APEX file comes from a partition that is already
//       dm-verity protected (e.g. /system) then we mount the loop device.
//
//
// Note: this function only does the job to activate this single APEX.
// In case this APEX file contributes to the /apex/sharedlibs mount point, then
// you must also call ContributeToSharedLibs after finishing activating all
// APEXes. See ActivateApexPackages for more context.
Result<void> ActivatePackageImpl(const ApexFile& apex_file,
                                 const std::string& device_name,
                                 bool reuse_device) {
  ATRACE_NAME("ActivatePackageImpl");
  const ApexManifest& manifest = apex_file.GetManifest();

  if (!IsValidPackageName(manifest.name())) {
    return Errorf("Package name {} is not allowed.", manifest.name());
  }

  // Validate upgraded shim apex
  if (shim::IsShimApex(apex_file) &&
      !ApexFileRepository::GetInstance().IsPreInstalledApex(apex_file)) {
    // This is not cheap for shim apex, but it is fine here since we have
    // upgraded shim apex only during CTS tests.
    Result<void> result = VerifyPackageBoot(apex_file);
    if (!result.ok()) {
      LOG(ERROR) << "Failed to validate shim apex: " << apex_file.GetPath();
      return result;
    }
  }

  // See whether we think it's active, and do not allow to activate the same
  // version. Also detect whether this is the highest version.
  // We roll this into a single check.
  bool version_found_mounted = false;
  {
    uint64_t new_version = manifest.version();
    bool version_found_active = false;
    gMountedApexes.ForallMountedApexes(
        manifest.name(), [&](const MountedApexData& data, bool latest) {
          Result<ApexFile> other_apex = ApexFile::Open(data.full_path);
          if (!other_apex.ok()) {
            return;
          }
          if (static_cast<uint64_t>(other_apex->GetManifest().version()) ==
              new_version) {
            version_found_mounted = true;
            version_found_active = latest;
          }
        });
    // If the package provides shared libraries to other APEXs, we need to
    // activate all versions available (i.e. preloaded on /system/apex and
    // available on /data/apex/active). The reason is that there might be some
    // APEXs loaded from /system/apex that reference the libraries contained on
    // the preloaded version of the apex providing shared libraries.
    if (version_found_active && !manifest.providesharedapexlibs()) {
      LOG(DEBUG) << "Package " << manifest.name() << " with version "
                 << manifest.version() << " already active";
      return {};
    }
  }

  const std::string& mount_point =
      apexd_private::GetPackageMountPoint(manifest);

  if (!version_found_mounted) {
    auto mount_status = MountPackage(apex_file, mount_point, device_name,
                                     reuse_device, /*temp_mount=*/false);
    if (!mount_status.ok()) {
      return mount_status;
    }
  }

  // Bind mount the latest version to /apex/<package_name>, unless the
  // package provides shared libraries to other APEXs.
  if (!manifest.providesharedapexlibs()) {
    auto st = gMountedApexes.DoIfLatest(
        manifest.name(), apex_file.GetPath(), [&]() -> Result<void> {
          return apexd_private::BindMount(
              apexd_private::GetActiveMountPoint(manifest), mount_point);
        });
    if (!st.ok()) {
      return Error() << "Failed to update package " << manifest.name()
                     << " to version " << manifest.version() << " : "
                     << st.error();
    }
  }

  LOG(DEBUG) << "Successfully activated " << apex_file.GetPath()
             << " package_name: " << manifest.name()
             << " version: " << manifest.version();
  return {};
}

// Wrapper around ActivatePackageImpl.
// Do not use, this wrapper is going away.
Result<void> ActivatePackage(const std::string& full_path) {
  LOG(INFO) << "Trying to activate " << full_path;

  Result<ApexFile> apex_file = ApexFile::Open(full_path);
  if (!apex_file.ok()) {
    return apex_file.error();
  }
  return ActivatePackageImpl(*apex_file, GetPackageId(apex_file->GetManifest()),
                             /* reuse_device= */ false);
}

Result<void> DeactivatePackage(const std::string& full_path) {
  LOG(INFO) << "Trying to deactivate " << full_path;

  Result<ApexFile> apex_file = ApexFile::Open(full_path);
  if (!apex_file.ok()) {
    return apex_file.error();
  }

  return UnmountPackage(*apex_file, /* allow_latest= */ true,
                        /* deferred= */ false, /* detach_mount_point= */ false);
}

Result<std::vector<ApexFile>> GetStagedApexFiles(
    int session_id, const std::vector<int>& child_session_ids) {
  auto session = gSessionManager->GetSession(session_id);
  if (!session.ok()) {
    return session.error();
  }
  // We should only accept sessions in SessionState::STAGED state
  auto session_state = (*session).GetState();
  if (session_state != SessionState::STAGED) {
    return Error() << "Session " << session_id << " is not in state STAGED";
  }

  std::vector<int> ids_to_scan;
  if (!child_session_ids.empty()) {
    ids_to_scan = child_session_ids;
  } else {
    ids_to_scan = {session_id};
  }

  // Find apex files in the staging directory
  std::vector<std::string> apex_file_paths;
  for (int id_to_scan : ids_to_scan) {
    std::string session_dir_path = std::string(gConfig->staged_session_dir) +
                                   "/session_" + std::to_string(id_to_scan);
    Result<std::vector<std::string>> scan =
        FindFilesBySuffix(session_dir_path, {kApexPackageSuffix});
    if (!scan.ok()) {
      return scan.error();
    }
    if (scan->size() != 1) {
      return Error() << "Expected exactly one APEX file in directory "
                     << session_dir_path << ". Found: " << scan->size();
    }
    std::string& apex_file_path = (*scan)[0];
    apex_file_paths.push_back(std::move(apex_file_path));
  }

  return OpenApexFiles(apex_file_paths);
}

Result<ClassPath> MountAndDeriveClassPath(
    const std::vector<ApexFile>& apex_files) {
  auto guard = android::base::make_scope_guard([&]() {
    for (const auto& apex : apex_files) {
      apexd_private::UnmountTempMount(apex);
    }
  });

  // Mount the staged apex files
  std::vector<std::string> temp_mounted_apex_paths;
  for (const auto& apex : apex_files) {
    const std::string& temp_mount_point =
        apexd_private::GetPackageTempMountPoint(apex.GetManifest());
    const std::string& package_id = GetPackageId(apex.GetManifest());
    const std::string& temp_device_name = package_id + ".tmp";
    auto mount_status =
        MountPackage(apex, temp_mount_point, temp_device_name,
                     /*reuse_device=*/false, /*temp_mount=*/true);
    if (!mount_status.ok()) {
      return mount_status.error();
    }
    temp_mounted_apex_paths.push_back(temp_mount_point);
  }

  // Calculate classpaths of temp mounted staged apexs
  return ClassPath::DeriveClassPath(temp_mounted_apex_paths);
}

std::vector<ApexFile> GetActivePackages() {
  std::vector<ApexFile> ret;
  gMountedApexes.ForallMountedApexes(
      [&](const std::string&, const MountedApexData& data, bool latest) {
        if (!latest) {
          return;
        }

        Result<ApexFile> apex_file = ApexFile::Open(data.full_path);
        if (!apex_file.ok()) {
          return;
        }
        ret.emplace_back(std::move(*apex_file));
      });

  return ret;
}

std::vector<ApexFile> CalculateInactivePackages(
    const std::vector<ApexFile>& active) {
  std::vector<ApexFile> inactive = GetFactoryPackages();
  auto new_end = std::remove_if(
      inactive.begin(), inactive.end(), [&active](const ApexFile& apex) {
        return std::any_of(active.begin(), active.end(),
                           [&apex](const ApexFile& active_apex) {
                             return apex.GetPath() == active_apex.GetPath();
                           });
      });
  inactive.erase(new_end, inactive.end());
  return std::move(inactive);
}

Result<void> EmitApexInfoList(bool is_bootstrap) {
  // Apexd runs both in "bootstrap" and "default" mount namespace.
  // To expose /apex/apex-info-list.xml separately in each mount namespaces,
  // we write /apex/.<namespace>-apex-info-list .xml file first and then
  // bind mount it to the canonical file (/apex/apex-info-list.xml).
  const std::string file_name =
      fmt::format("{}/.{}-{}", kApexRoot,
                  is_bootstrap ? "bootstrap" : "default", kApexInfoList);

  unique_fd fd(TEMP_FAILURE_RETRY(
      open(file_name.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644)));
  if (fd.get() == -1) {
    return ErrnoErrorf("Can't open {}", file_name);
  }

  const std::vector<ApexFile> active(GetActivePackages());

  std::vector<ApexFile> inactive;
  // we skip for non-activated built-in apexes in bootstrap mode
  // in order to avoid boottime increase
  if (!is_bootstrap) {
    inactive = CalculateInactivePackages(active);
  }

  std::stringstream xml;
  CollectApexInfoList(xml, active, inactive);

  if (!android::base::WriteStringToFd(xml.str(), fd)) {
    return ErrnoErrorf("Can't write to {}", file_name);
  }

  fd.reset();

  const std::string mount_point =
      fmt::format("{}/{}", kApexRoot, kApexInfoList);
  if (access(mount_point.c_str(), F_OK) != 0) {
    close(open(mount_point.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC,
               0644));
  }
  if (mount(file_name.c_str(), mount_point.c_str(), nullptr, MS_BIND,
            nullptr) == -1) {
    return ErrnoErrorf("Can't bind mount {} to {}", file_name, mount_point);
  }
  return RestoreconPath(file_name);
}

namespace {
std::unordered_map<std::string, uint64_t> GetActivePackagesMap() {
  std::vector<ApexFile> active_packages = GetActivePackages();
  std::unordered_map<std::string, uint64_t> ret;
  for (const auto& package : active_packages) {
    const ApexManifest& manifest = package.GetManifest();
    ret.insert({manifest.name(), manifest.version()});
  }
  return ret;
}

}  // namespace

std::vector<ApexFile> GetFactoryPackages() {
  std::vector<ApexFile> ret;

  // Decompressed APEX is considered factory package
  std::vector<std::string> decompressed_pkg_names;
  auto active_pkgs = GetActivePackages();
  for (ApexFile& apex : active_pkgs) {
    if (ApexFileRepository::GetInstance().IsDecompressedApex(apex)) {
      decompressed_pkg_names.push_back(apex.GetManifest().name());
      ret.emplace_back(std::move(apex));
    }
  }

  const auto& file_repository = ApexFileRepository::GetInstance();
  for (const auto& ref : file_repository.GetPreInstalledApexFiles()) {
    Result<ApexFile> apex_file = ApexFile::Open(ref.get().GetPath());
    if (!apex_file.ok()) {
      LOG(ERROR) << apex_file.error();
      continue;
    }
    // Ignore compressed APEX if it has been decompressed already
    if (apex_file->IsCompressed() &&
        std::find(decompressed_pkg_names.begin(), decompressed_pkg_names.end(),
                  apex_file->GetManifest().name()) !=
            decompressed_pkg_names.end()) {
      continue;
    }

    ret.emplace_back(std::move(*apex_file));
  }
  return ret;
}

Result<ApexFile> GetActivePackage(const std::string& packageName) {
  std::vector<ApexFile> packages = GetActivePackages();
  for (ApexFile& apex : packages) {
    if (apex.GetManifest().name() == packageName) {
      return std::move(apex);
    }
  }

  return ErrnoError() << "Cannot find matching package for: " << packageName;
}

/**
 * Abort individual staged session.
 *
 * Returns without error only if session was successfully aborted.
 **/
Result<void> AbortStagedSession(int session_id) {
  auto session = gSessionManager->GetSession(session_id);
  if (!session.ok()) {
    return Error() << "No session found with id " << session_id;
  }

  switch (session->GetState()) {
    case SessionState::VERIFIED:
      [[clang::fallthrough]];
    case SessionState::STAGED:
      return session->DeleteSession();
    default:
      return Error() << "Session " << *session << " can't be aborted";
  }
}

namespace {

enum ActivationMode { kBootstrapMode = 0, kBootMode, kOtaChrootMode, kVmMode };

std::vector<Result<const ApexFile*>> ActivateApexWorker(
    ActivationMode mode, std::queue<const ApexFile*>& apex_queue,
    std::mutex& mutex) {
  ATRACE_NAME("ActivateApexWorker");
  std::vector<Result<const ApexFile*>> ret;

  while (true) {
    const ApexFile* apex;
    {
      std::lock_guard lock(mutex);
      if (apex_queue.empty()) break;
      apex = apex_queue.front();
      apex_queue.pop();
    }

    std::string device_name;
    if (mode == ActivationMode::kBootMode) {
      device_name = apex->GetManifest().name();
    } else {
      device_name = GetPackageId(apex->GetManifest());
    }
    if (mode == ActivationMode::kOtaChrootMode) {
      device_name += ".chroot";
    }
    bool reuse_device = mode == ActivationMode::kBootMode;
    auto res = ActivatePackageImpl(*apex, device_name, reuse_device);
    if (!res.ok()) {
      ret.push_back(Error() << "Failed to activate " << apex->GetPath() << "("
                            << device_name << "): " << res.error());
    } else {
      ret.push_back({apex});
    }
  }

  return ret;
}

Result<void> ActivateApexPackages(const std::vector<ApexFileRef>& apexes,
                                  ActivationMode mode) {
  ATRACE_NAME("ActivateApexPackages");
  std::queue<const ApexFile*> apex_queue;
  std::mutex apex_queue_mutex;

  for (const ApexFile& apex : apexes) {
    apex_queue.emplace(&apex);
  }

  size_t worker_num =
      android::sysprop::ApexProperties::boot_activation_threads().value_or(0);

  // Setting number of workers to the number of packages to load
  // This seems to provide the best performance
  if (worker_num == 0) {
    worker_num = apex_queue.size();
  }
  worker_num = std::min(apex_queue.size(), worker_num);

  // On -eng builds there might be two different pre-installed art apexes.
  // Attempting to activate them in parallel will result in UB (e.g.
  // apexd-bootstrap might crash). In order to avoid this, for the time being on
  // -eng builds activate apexes sequentially.
  // TODO(b/176497601): remove this.
  if (GetProperty("ro.build.type", "") == "eng") {
    worker_num = 1;
  }

  std::vector<std::future<std::vector<Result<const ApexFile*>>>> futures;
  futures.reserve(worker_num);
  for (size_t i = 0; i < worker_num; i++) {
    futures.push_back(std::async(std::launch::async, ActivateApexWorker,
                                 std::ref(mode), std::ref(apex_queue),
                                 std::ref(apex_queue_mutex)));
  }

  size_t activated_cnt = 0;
  size_t failed_cnt = 0;
  std::string error_message;
  std::vector<const ApexFile*> activated_sharedlibs_apexes;
  for (size_t i = 0; i < futures.size(); i++) {
    for (const auto& res : futures[i].get()) {
      if (res.ok()) {
        ++activated_cnt;
        if (res.value()->GetManifest().providesharedapexlibs()) {
          activated_sharedlibs_apexes.push_back(res.value());
        }
      } else {
        ++failed_cnt;
        LOG(ERROR) << res.error();
        if (failed_cnt == 1) {
          error_message = res.error().message();
        }
      }
    }
  }

  // We finished activation of APEX packages and now are ready to populate the
  // /apex/sharedlibs mount point. Since there can be multiple different APEXes
  // contributing to shared libs (at the point of writing this comment there can
  // be up 2 APEXes: pre-installed sharedlibs APEX and its updated counterpart)
  // we need to call ContributeToSharedLibs sequentially to avoid potential race
  // conditions. See b/240291921
  const auto& apex_repo = ApexFileRepository::GetInstance();
  // To make things simpler we also provide an order in which APEXes contribute
  // to sharedlibs.
  auto cmp = [&apex_repo](const auto& apex_a, const auto& apex_b) {
    // An APEX with higher version should contribute first
    if (apex_a->GetManifest().version() != apex_b->GetManifest().version()) {
      return apex_a->GetManifest().version() > apex_b->GetManifest().version();
    }
    // If they have the same version, then we pick the updated APEX first.
    return !apex_repo.IsPreInstalledApex(*apex_a);
  };
  std::sort(activated_sharedlibs_apexes.begin(),
            activated_sharedlibs_apexes.end(), cmp);
  for (const auto& sharedlibs_apex : activated_sharedlibs_apexes) {
    LOG(DEBUG) << "Populating sharedlibs with APEX "
               << sharedlibs_apex->GetPath() << " ( "
               << sharedlibs_apex->GetManifest().name()
               << " ) version : " << sharedlibs_apex->GetManifest().version();
    auto mount_point =
        apexd_private::GetPackageMountPoint(sharedlibs_apex->GetManifest());
    if (auto ret = ContributeToSharedLibs(mount_point); !ret.ok()) {
      LOG(ERROR) << "Failed to populate sharedlibs with APEX package "
                 << sharedlibs_apex->GetPath() << " : " << ret.error();
      ++failed_cnt;
      if (failed_cnt == 1) {
        error_message = ret.error().message();
      }
    }
  }

  if (failed_cnt > 0) {
    return Error() << "Failed to activate " << failed_cnt
                   << " APEX packages. One of the errors: " << error_message;
  }
  LOG(INFO) << "Activated " << activated_cnt << " packages.";
  return {};
}

// A fallback function in case some of the apexes failed to activate. For all
// such apexes that were coming from /data partition we will attempt to activate
// their corresponding pre-installed copies.
Result<void> ActivateMissingApexes(const std::vector<ApexFileRef>& apexes,
                                   ActivationMode mode) {
  LOG(INFO) << "Trying to activate pre-installed versions of missing apexes";
  const auto& file_repository = ApexFileRepository::GetInstance();
  const auto& activated_apexes = GetActivePackagesMap();
  std::vector<ApexFileRef> fallback_apexes;
  for (const auto& apex_ref : apexes) {
    const auto& apex = apex_ref.get();
    if (apex.GetManifest().providesharedapexlibs()) {
      // We must mount both versions of sharedlibs apex anyway. Not much we can
      // do here.
      continue;
    }
    if (file_repository.IsPreInstalledApex(apex)) {
      // We tried to activate pre-installed apex in the first place. No need to
      // try again.
      continue;
    }
    const std::string& name = apex.GetManifest().name();
    if (activated_apexes.find(name) == activated_apexes.end()) {
      fallback_apexes.push_back(file_repository.GetPreInstalledApex(name));
    }
  }

  // Process compressed APEX, if any
  std::vector<ApexFileRef> compressed_apex;
  for (auto it = fallback_apexes.begin(); it != fallback_apexes.end();) {
    if (it->get().IsCompressed()) {
      compressed_apex.emplace_back(*it);
      it = fallback_apexes.erase(it);
    } else {
      it++;
    }
  }
  std::vector<ApexFile> decompressed_apex;
  if (!compressed_apex.empty()) {
    decompressed_apex = ProcessCompressedApex(
        compressed_apex,
        /* is_ota_chroot= */ mode == ActivationMode::kOtaChrootMode);
    for (const ApexFile& apex_file : decompressed_apex) {
      fallback_apexes.emplace_back(std::cref(apex_file));
    }
  }
  if (mode == kBootMode) {
    // Treat fallback to pre-installed APEXes as a change of the acitve APEX,
    // since we are already in a pretty dire situation, so it's better if we
    // drop all the caches.
    for (const auto& apex : fallback_apexes) {
      gChangedActiveApexes.insert(apex.get().GetManifest().name());
    }
  }
  return ActivateApexPackages(fallback_apexes, mode);
}

}  // namespace

/**
 * Snapshots data from base_dir/apexdata/<apex name> to
 * base_dir/apexrollback/<rollback id>/<apex name>.
 */
Result<void> SnapshotDataDirectory(const std::string& base_dir,
                                   const int rollback_id,
                                   const std::string& apex_name,
                                   bool pre_restore = false) {
  auto rollback_path =
      StringPrintf("%s/%s/%d%s", base_dir.c_str(), kApexSnapshotSubDir,
                   rollback_id, pre_restore ? kPreRestoreSuffix : "");
  const Result<void> result = CreateDirIfNeeded(rollback_path, 0700);
  if (!result.ok()) {
    return Error() << "Failed to create snapshot directory for rollback "
                   << rollback_id << " : " << result.error();
  }
  auto from_path = StringPrintf("%s/%s/%s", base_dir.c_str(), kApexDataSubDir,
                                apex_name.c_str());
  auto to_path =
      StringPrintf("%s/%s", rollback_path.c_str(), apex_name.c_str());

  return ReplaceFiles(from_path, to_path);
}

/**
 * Restores snapshot from base_dir/apexrollback/<rollback id>/<apex name>
 * to base_dir/apexdata/<apex name>.
 * Note the snapshot will be deleted after restoration succeeded.
 */
Result<void> RestoreDataDirectory(const std::string& base_dir,
                                  const int rollback_id,
                                  const std::string& apex_name,
                                  bool pre_restore = false) {
  auto from_path = StringPrintf(
      "%s/%s/%d%s/%s", base_dir.c_str(), kApexSnapshotSubDir, rollback_id,
      pre_restore ? kPreRestoreSuffix : "", apex_name.c_str());
  auto to_path = StringPrintf("%s/%s/%s", base_dir.c_str(), kApexDataSubDir,
                              apex_name.c_str());
  Result<void> result = ReplaceFiles(from_path, to_path);
  if (!result.ok()) {
    return result;
  }
  result = RestoreconPath(to_path);
  if (!result.ok()) {
    return result;
  }
  result = DeleteDir(from_path);
  if (!result.ok()) {
    LOG(ERROR) << "Failed to delete the snapshot: " << result.error();
  }
  return {};
}

void SnapshotOrRestoreDeIfNeeded(const std::string& base_dir,
                                 const ApexSession& session) {
  if (session.HasRollbackEnabled()) {
    for (const auto& apex_name : session.GetApexNames()) {
      Result<void> result =
          SnapshotDataDirectory(base_dir, session.GetRollbackId(), apex_name);
      if (!result.ok()) {
        LOG(ERROR) << "Snapshot failed for " << apex_name << ": "
                   << result.error();
      }
    }
  } else if (session.IsRollback()) {
    for (const auto& apex_name : session.GetApexNames()) {
      if (!gSupportsFsCheckpoints) {
        // Snapshot before restore so this rollback can be reverted.
        SnapshotDataDirectory(base_dir, session.GetRollbackId(), apex_name,
                              true /* pre_restore */);
      }
      Result<void> result =
          RestoreDataDirectory(base_dir, session.GetRollbackId(), apex_name);
      if (!result.ok()) {
        LOG(ERROR) << "Restore of data failed for " << apex_name << ": "
                   << result.error();
      }
    }
  }
}

void SnapshotOrRestoreDeSysData() {
  auto sessions = gSessionManager->GetSessionsInState(SessionState::ACTIVATED);

  for (const ApexSession& session : sessions) {
    SnapshotOrRestoreDeIfNeeded(kDeSysDataDir, session);
  }
}

int SnapshotOrRestoreDeUserData() {
  auto user_dirs = GetDeUserDirs();

  if (!user_dirs.ok()) {
    LOG(ERROR) << "Error reading dirs " << user_dirs.error();
    return 1;
  }

  auto sessions = gSessionManager->GetSessionsInState(SessionState::ACTIVATED);

  for (const ApexSession& session : sessions) {
    for (const auto& user_dir : *user_dirs) {
      SnapshotOrRestoreDeIfNeeded(user_dir, session);
    }
  }

  return 0;
}

Result<void> SnapshotCeData(const int user_id, const int rollback_id,
                            const std::string& apex_name) {
  auto base_dir = StringPrintf("%s/%d", kCeDataDir, user_id);
  return SnapshotDataDirectory(base_dir, rollback_id, apex_name);
}

Result<void> RestoreCeData(const int user_id, const int rollback_id,
                           const std::string& apex_name) {
  auto base_dir = StringPrintf("%s/%d", kCeDataDir, user_id);
  return RestoreDataDirectory(base_dir, rollback_id, apex_name);
}

Result<void> DestroySnapshots(const std::string& base_dir,
                              const int rollback_id) {
  auto path = StringPrintf("%s/%s/%d", base_dir.c_str(), kApexSnapshotSubDir,
                           rollback_id);
  return DeleteDir(path);
}

Result<void> DestroyDeSnapshots(const int rollback_id) {
  DestroySnapshots(kDeSysDataDir, rollback_id);

  auto user_dirs = GetDeUserDirs();
  if (!user_dirs.ok()) {
    return Error() << "Error reading user dirs " << user_dirs.error();
  }

  for (const auto& user_dir : *user_dirs) {
    DestroySnapshots(user_dir, rollback_id);
  }

  return {};
}

Result<void> DestroyCeSnapshots(const int user_id, const int rollback_id) {
  auto path = StringPrintf("%s/%d/%s/%d", kCeDataDir, user_id,
                           kApexSnapshotSubDir, rollback_id);
  return DeleteDir(path);
}

/**
 * Deletes all credential-encrypted snapshots for the given user, except for
 * those listed in retain_rollback_ids.
 */
Result<void> DestroyCeSnapshotsNotSpecified(
    int user_id, const std::vector<int>& retain_rollback_ids) {
  auto snapshot_root =
      StringPrintf("%s/%d/%s", kCeDataDir, user_id, kApexSnapshotSubDir);
  auto snapshot_dirs = GetSubdirs(snapshot_root);
  if (!snapshot_dirs.ok()) {
    return Error() << "Error reading snapshot dirs " << snapshot_dirs.error();
  }

  for (const auto& snapshot_dir : *snapshot_dirs) {
    uint snapshot_id;
    bool parse_ok = ParseUint(
        std::filesystem::path(snapshot_dir).filename().c_str(), &snapshot_id);
    if (parse_ok &&
        std::find(retain_rollback_ids.begin(), retain_rollback_ids.end(),
                  snapshot_id) == retain_rollback_ids.end()) {
      Result<void> result = DeleteDir(snapshot_dir);
      if (!result.ok()) {
        return Error() << "Destroy CE snapshot failed for " << snapshot_dir
                       << " : " << result.error();
      }
    }
  }
  return {};
}

void RestorePreRestoreSnapshotsIfPresent(const std::string& base_dir,
                                         const ApexSession& session) {
  auto pre_restore_snapshot_path =
      StringPrintf("%s/%s/%d%s", base_dir.c_str(), kApexSnapshotSubDir,
                   session.GetRollbackId(), kPreRestoreSuffix);
  if (PathExists(pre_restore_snapshot_path).ok()) {
    for (const auto& apex_name : session.GetApexNames()) {
      Result<void> result = RestoreDataDirectory(
          base_dir, session.GetRollbackId(), apex_name, true /* pre_restore */);
      if (!result.ok()) {
        LOG(ERROR) << "Restore of pre-restore snapshot failed for " << apex_name
                   << ": " << result.error();
      }
    }
  }
}

void RestoreDePreRestoreSnapshotsIfPresent(const ApexSession& session) {
  RestorePreRestoreSnapshotsIfPresent(kDeSysDataDir, session);

  auto user_dirs = GetDeUserDirs();
  if (!user_dirs.ok()) {
    LOG(ERROR) << "Error reading user dirs to restore pre-restore snapshots"
               << user_dirs.error();
  }

  for (const auto& user_dir : *user_dirs) {
    RestorePreRestoreSnapshotsIfPresent(user_dir, session);
  }
}

void DeleteDePreRestoreSnapshots(const std::string& base_dir,
                                 const ApexSession& session) {
  auto pre_restore_snapshot_path =
      StringPrintf("%s/%s/%d%s", base_dir.c_str(), kApexSnapshotSubDir,
                   session.GetRollbackId(), kPreRestoreSuffix);
  Result<void> result = DeleteDir(pre_restore_snapshot_path);
  if (!result.ok()) {
    LOG(ERROR) << "Deletion of pre-restore snapshot failed: " << result.error();
  }
}

void DeleteDePreRestoreSnapshots(const ApexSession& session) {
  DeleteDePreRestoreSnapshots(kDeSysDataDir, session);

  auto user_dirs = GetDeUserDirs();
  if (!user_dirs.ok()) {
    LOG(ERROR) << "Error reading user dirs to delete pre-restore snapshots"
               << user_dirs.error();
  }

  for (const auto& user_dir : *user_dirs) {
    DeleteDePreRestoreSnapshots(user_dir, session);
  }
}

void OnBootCompleted() {
  ApexdLifecycle::GetInstance().MarkBootCompleted();
}

// Returns true if any session gets staged
void ScanStagedSessionsDirAndStage() {
  LOG(INFO) << "Scanning " << GetSessionsDir()
            << " looking for sessions to be activated.";

  auto sessions_to_activate =
      gSessionManager->GetSessionsInState(SessionState::STAGED);
  if (gSupportsFsCheckpoints) {
    // A session that is in the ACTIVATED state should still be re-activated if
    // fs checkpointing is supported. In this case, a session may be in the
    // ACTIVATED state yet the data/apex/active directory may have been
    // reverted. The session should be reverted in this scenario.
    auto activated_sessions =
        gSessionManager->GetSessionsInState(SessionState::ACTIVATED);
    sessions_to_activate.insert(sessions_to_activate.end(),
                                activated_sessions.begin(),
                                activated_sessions.end());
  }

  for (auto& session : sessions_to_activate) {
    auto session_id = session.GetId();

    auto session_failed_fn = [&]() {
      LOG(WARNING) << "Marking session " << session_id << " as failed.";
      auto st = session.UpdateStateAndCommit(SessionState::ACTIVATION_FAILED);
      if (!st.ok()) {
        LOG(WARNING) << "Failed to mark session " << session_id
                     << " as failed : " << st.error();
      }
    };
    auto scope_guard = android::base::make_scope_guard(session_failed_fn);

    std::string build_fingerprint = GetProperty(kBuildFingerprintSysprop, "");
    if (session.GetBuildFingerprint().compare(build_fingerprint) != 0) {
      auto error_message = "APEX build fingerprint has changed";
      LOG(ERROR) << error_message;
      session.SetErrorMessage(error_message);
      continue;
    }

    // If device supports fs-checkpoint, then apex session should only be
    // installed when in checkpoint-mode. Otherwise, we will not be able to
    // revert /data on error.
    if (gSupportsFsCheckpoints && !gInFsCheckpointMode) {
      auto error_message =
          "Cannot install apex session if not in fs-checkpoint mode";
      LOG(ERROR) << error_message;
      session.SetErrorMessage(error_message);
      continue;
    }

    std::vector<std::string> dirs_to_scan =
        session.GetStagedApexDirs(gConfig->staged_session_dir);

    std::vector<std::string> apexes;
    bool scan_successful = true;
    for (const auto& dir_to_scan : dirs_to_scan) {
      Result<std::vector<std::string>> scan =
          FindFilesBySuffix(dir_to_scan, {kApexPackageSuffix});
      if (!scan.ok()) {
        LOG(WARNING) << scan.error();
        session.SetErrorMessage(scan.error().message());
        scan_successful = false;
        break;
      }

      if (scan->size() > 1) {
        std::string error_message = StringPrintf(
            "More than one APEX package found in the same session directory %s "
            ", skipping activation",
            dir_to_scan.c_str());
        LOG(WARNING) << error_message;
        session.SetErrorMessage(error_message);
        scan_successful = false;
        break;
      }

      if (scan->empty()) {
        std::string error_message = StringPrintf(
            "No APEX packages found while scanning %s session id: %d.",
            dir_to_scan.c_str(), session_id);
        LOG(WARNING) << error_message;
        session.SetErrorMessage(error_message);
        scan_successful = false;
        break;
      }
      apexes.push_back(std::move((*scan)[0]));
    }

    if (!scan_successful) {
      continue;
    }

    std::vector<std::string> staged_apex_names;
    for (const auto& apex : apexes) {
      // TODO(b/158470836): Avoid opening ApexFile repeatedly.
      Result<ApexFile> apex_file = ApexFile::Open(apex);
      if (!apex_file.ok()) {
        LOG(ERROR) << "Cannot open apex file during staging: " << apex;
        continue;
      }
      staged_apex_names.push_back(apex_file->GetManifest().name());
    }

    const Result<void> result = StagePackages(apexes);
    if (!result.ok()) {
      std::string error_message = StringPrintf(
          "Activation failed for packages %s : %s", Join(apexes, ',').c_str(),
          result.error().message().c_str());
      LOG(ERROR) << error_message;
      session.SetErrorMessage(error_message);
      continue;
    }

    // Session was OK, release scopeguard.
    scope_guard.Disable();

    for (const std::string& apex : staged_apex_names) {
      gChangedActiveApexes.insert(apex);
    }

    auto st = session.UpdateStateAndCommit(SessionState::ACTIVATED);
    if (!st.ok()) {
      LOG(ERROR) << "Failed to mark " << session
                 << " as activated : " << st.error();
    }
  }
}

namespace {
std::string StageDestPath(const ApexFile& apex_file) {
  return StringPrintf("%s/%s%s", gConfig->active_apex_data_dir,
                      GetPackageId(apex_file.GetManifest()).c_str(),
                      kApexPackageSuffix);
}

}  // namespace

Result<void> StagePackages(const std::vector<std::string>& tmp_paths) {
  if (tmp_paths.empty()) {
    return Errorf("Empty set of inputs");
  }
  LOG(DEBUG) << "StagePackages() for " << Join(tmp_paths, ',');

  // Note: this function is temporary. As such the code is not optimized, e.g.,
  //       it will open ApexFiles multiple times.

  // 1) Verify all packages.
  Result<std::vector<ApexFile>> apex_files = OpenApexFiles(tmp_paths);
  if (!apex_files.ok()) {
    return apex_files.error();
  }
  for (const ApexFile& apex_file : *apex_files) {
    if (shim::IsShimApex(apex_file)) {
      // Shim apex will be validated on every boot. No need to do it here.
      continue;
    }
    Result<void> result = VerifyPackageBoot(apex_file);
    if (!result.ok()) {
      return result.error();
    }
  }

  // Make sure that kActiveApexPackagesDataDir exists.
  auto create_dir_status =
      CreateDirIfNeeded(std::string(gConfig->active_apex_data_dir), 0755);
  if (!create_dir_status.ok()) {
    return create_dir_status.error();
  }

  // 2) Now stage all of them.

  // Ensure the APEX gets removed on failure.
  std::unordered_set<std::string> staged_files;
  std::vector<std::string> changed_hashtree_files;
  auto deleter = [&staged_files, &changed_hashtree_files]() {
    for (const std::string& staged_path : staged_files) {
      if (TEMP_FAILURE_RETRY(unlink(staged_path.c_str())) != 0) {
        PLOG(ERROR) << "Unable to unlink " << staged_path;
      }
    }
    for (const std::string& hashtree_file : changed_hashtree_files) {
      if (TEMP_FAILURE_RETRY(unlink(hashtree_file.c_str())) != 0) {
        PLOG(ERROR) << "Unable to unlink " << hashtree_file;
      }
    }
  };
  auto scope_guard = android::base::make_scope_guard(deleter);

  std::unordered_set<std::string> staged_packages;
  for (const ApexFile& apex_file : *apex_files) {
    // First promote new hashtree file to the one that will be used when
    // mounting apex.
    std::string new_hashtree_file = GetHashTreeFileName(apex_file,
                                                        /* is_new = */ true);
    std::string old_hashtree_file = GetHashTreeFileName(apex_file,
                                                        /* is_new = */ false);
    if (access(new_hashtree_file.c_str(), F_OK) == 0) {
      if (TEMP_FAILURE_RETRY(rename(new_hashtree_file.c_str(),
                                    old_hashtree_file.c_str())) != 0) {
        return ErrnoError() << "Failed to move " << new_hashtree_file << " to "
                            << old_hashtree_file;
      }
      changed_hashtree_files.emplace_back(std::move(old_hashtree_file));
    }
    // And only then move apex to /data/apex/active.
    std::string dest_path = StageDestPath(apex_file);
    if (access(dest_path.c_str(), F_OK) == 0) {
      LOG(DEBUG) << dest_path << " already exists. Deleting";
      if (TEMP_FAILURE_RETRY(unlink(dest_path.c_str())) != 0) {
        return ErrnoError() << "Failed to unlink " << dest_path;
      }
    }

    if (link(apex_file.GetPath().c_str(), dest_path.c_str()) != 0) {
      return ErrnoError() << "Unable to link " << apex_file.GetPath() << " to "
                          << dest_path;
    }
    staged_files.insert(dest_path);
    staged_packages.insert(apex_file.GetManifest().name());

    LOG(DEBUG) << "Success linking " << apex_file.GetPath() << " to "
               << dest_path;
  }

  scope_guard.Disable();  // Accept the state.

  return RemovePreviouslyActiveApexFiles(staged_packages, staged_files);
}

Result<void> UnstagePackages(const std::vector<std::string>& paths) {
  if (paths.empty()) {
    return Errorf("Empty set of inputs");
  }
  LOG(DEBUG) << "UnstagePackages() for " << Join(paths, ',');

  for (const std::string& path : paths) {
    auto apex = ApexFile::Open(path);
    if (!apex.ok()) {
      return apex.error();
    }
    if (ApexFileRepository::GetInstance().IsPreInstalledApex(*apex)) {
      return Error() << "Can't uninstall pre-installed apex " << path;
    }
  }

  for (const std::string& path : paths) {
    if (unlink(path.c_str()) != 0) {
      return ErrnoError() << "Can't unlink " << path;
    }
  }

  return {};
}

/**
 * During apex installation, staged sessions located in /data/apex/sessions
 * mutate the active sessions in /data/apex/active. If some error occurs during
 * installation of apex, we need to revert /data/apex/active to its original
 * state and reboot.
 *
 * Also, we need to put staged sessions in /data/apex/sessions in REVERTED state
 * so that they do not get activated on next reboot.
 */
Result<void> RevertActiveSessions(const std::string& crashing_native_process,
                                  const std::string& error_message) {
  // First check whenever there is anything to revert. If there is none, then
  // fail. This prevents apexd from boot looping a device in case a native
  // process is crashing and there are no apex updates.
  auto active_sessions = gSessionManager->GetSessions();
  active_sessions.erase(
      std::remove_if(active_sessions.begin(), active_sessions.end(),
                     [](const auto& s) {
                       return s.IsFinalized() ||
                              s.GetState() == SessionState::UNKNOWN;
                     }),
      active_sessions.end());
  if (active_sessions.empty()) {
    return Error() << "Revert requested, when there are no active sessions.";
  }

  for (auto& session : active_sessions) {
    if (!crashing_native_process.empty()) {
      session.SetCrashingNativeProcess(crashing_native_process);
    }
    if (!error_message.empty()) {
      session.SetErrorMessage(error_message);
    }
    auto status =
        session.UpdateStateAndCommit(SessionState::REVERT_IN_PROGRESS);
    if (!status.ok()) {
      return Error() << "Revert of session " << session
                     << " failed : " << status.error();
    }
  }

  if (!gSupportsFsCheckpoints) {
    auto restore_status = RestoreActivePackages();
    if (!restore_status.ok()) {
      for (auto& session : active_sessions) {
        auto st = session.UpdateStateAndCommit(SessionState::REVERT_FAILED);
        LOG(DEBUG) << "Marking " << session << " as failed to revert";
        if (!st.ok()) {
          LOG(WARNING) << "Failed to mark session " << session
                       << " as failed to revert : " << st.error();
        }
      }
      return restore_status;
    }
  } else {
    LOG(INFO) << "Not restoring active packages in checkpoint mode.";
  }

  for (auto& session : active_sessions) {
    if (!gSupportsFsCheckpoints && session.IsRollback()) {
      // If snapshots have already been restored, undo that by restoring the
      // pre-restore snapshot.
      RestoreDePreRestoreSnapshotsIfPresent(session);
    }

    auto status = session.UpdateStateAndCommit(SessionState::REVERTED);
    if (!status.ok()) {
      LOG(WARNING) << "Failed to mark session " << session
                   << " as reverted : " << status.error();
    }
  }

  return {};
}

Result<void> RevertActiveSessionsAndReboot(
    const std::string& crashing_native_process,
    const std::string& error_message) {
  auto status = RevertActiveSessions(crashing_native_process, error_message);
  if (!status.ok()) {
    return status;
  }
  LOG(ERROR) << "Successfully reverted. Time to reboot device.";
  if (gInFsCheckpointMode) {
    Result<void> res = gVoldService->AbortChanges(
        "apexd_initiated" /* message */, false /* retry */);
    if (!res.ok()) {
      LOG(ERROR) << res.error();
    }
  }
  Reboot();
  return {};
}

Result<void> CreateSharedLibsApexDir() {
  // Creates /apex/sharedlibs/lib{,64} for SharedLibs APEXes.
  std::string shared_libs_sub_dir =
      StringPrintf("%s/%s", kApexRoot, kApexSharedLibsSubDir);
  auto dir_exists = PathExists(shared_libs_sub_dir);
  if (!dir_exists.ok() || !*dir_exists) {
    std::error_code error_code;
    std::filesystem::create_directory(shared_libs_sub_dir, error_code);
    if (error_code) {
      return Error() << "Failed to create directory " << shared_libs_sub_dir
                     << ": " << error_code.message();
    }
  }
  for (const auto& lib_path : {"lib", "lib64"}) {
    std::string apex_lib_path =
        StringPrintf("%s/%s", shared_libs_sub_dir.c_str(), lib_path);
    auto lib_dir_exists = PathExists(apex_lib_path);
    if (!lib_dir_exists.ok() || !*lib_dir_exists) {
      std::error_code error_code;
      std::filesystem::create_directory(apex_lib_path, error_code);
      if (error_code) {
        return Error() << "Failed to create directory " << apex_lib_path << ": "
                       << error_code.message();
      }
    }
  }

  return {};
}

int OnBootstrap() {
  ATRACE_NAME("OnBootstrap");
  auto time_started = boot_clock::now();

  ApexFileRepository& instance = ApexFileRepository::GetInstance();
  Result<void> status =
      instance.AddPreInstalledApex(gConfig->apex_built_in_dirs);
  if (!status.ok()) {
    LOG(ERROR) << "Failed to collect APEX keys : " << status.error();
    return 1;
  }

  const auto& pre_installed_apexes = instance.GetPreInstalledApexFiles();
  int loop_device_cnt = pre_installed_apexes.size();
  // Find all bootstrap apexes
  std::vector<ApexFileRef> bootstrap_apexes;
  for (const auto& apex : pre_installed_apexes) {
    if (IsBootstrapApex(apex.get())) {
      LOG(INFO) << "Found bootstrap APEX " << apex.get().GetPath();
      bootstrap_apexes.push_back(apex);
      loop_device_cnt++;
    }
    if (apex.get().GetManifest().providesharedapexlibs()) {
      LOG(INFO) << "Found sharedlibs APEX " << apex.get().GetPath();
      // Sharedlis APEX might be mounted 2 times:
      //   * Pre-installed sharedlibs APEX will be mounted in OnStart
      //   * Updated sharedlibs APEX (if it exists) will be mounted in OnStart
      //
      // We already counted a loop device for one of these 2 mounts, need to add
      // 1 more.
      loop_device_cnt++;
    }
  }
  LOG(INFO) << "Need to pre-allocate " << loop_device_cnt
            << " loop devices for " << pre_installed_apexes.size()
            << " APEX packages";
  // TODO(b/209491448) Remove this.
  auto block_count = AddBlockApex(instance);
  if (!block_count.ok()) {
    LOG(ERROR) << status.error();
    return 1;
  }
  if (*block_count > 0) {
    LOG(INFO) << "Also need to pre-allocate " << *block_count
              << " loop devices for block APEXes";
    loop_device_cnt += *block_count;
  }
  if (auto res = loop::PreAllocateLoopDevices(loop_device_cnt); !res.ok()) {
    LOG(ERROR) << "Failed to pre-allocate loop devices : " << res.error();
  }

  DeviceMapper& dm = DeviceMapper::Instance();
  // Create empty dm device for each found APEX.
  // This is a boot time optimization that makes use of the fact that user space
  // paths will be created by ueventd before apexd is started, and hence
  // reducing the time to activate APEXEs on /data.
  // Note: since at this point we don't know which APEXes are updated, we are
  // optimistically creating a verity device for all of them. Once boot
  // finishes, apexd will clean up unused devices.
  // TODO(b/192241176): move to apexd_verity.{h,cpp}
  for (const auto& apex : pre_installed_apexes) {
    const std::string& name = apex.get().GetManifest().name();
    if (!dm.CreatePlaceholderDevice(name)) {
      LOG(ERROR) << "Failed to create empty device " << name;
    }
  }

  // Create directories for APEX shared libraries.
  auto sharedlibs_apex_dir = CreateSharedLibsApexDir();
  if (!sharedlibs_apex_dir.ok()) {
    LOG(ERROR) << sharedlibs_apex_dir.error();
    return 1;
  }

  // Now activate bootstrap apexes.
  auto ret =
      ActivateApexPackages(bootstrap_apexes, ActivationMode::kBootstrapMode);
  if (!ret.ok()) {
    LOG(ERROR) << "Failed to activate bootstrap apex files : " << ret.error();
    return 1;
  }

  OnAllPackagesActivated(/*is_bootstrap=*/true);
  auto time_elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
    boot_clock::now() - time_started).count();
  LOG(INFO) << "OnBootstrap done, duration=" << time_elapsed;
  return 0;
}

Result<void> RemountApexFile(const std::string& path) {
  if (auto ret = DeactivatePackage(path); !ret.ok()) {
    return ret;
  }
  return ActivatePackage(path);
}

void InitializeVold(CheckpointInterface* checkpoint_service) {
  if (checkpoint_service != nullptr) {
    gVoldService = checkpoint_service;
    Result<bool> supports_fs_checkpoints =
        gVoldService->SupportsFsCheckpoints();
    if (supports_fs_checkpoints.ok()) {
      gSupportsFsCheckpoints = *supports_fs_checkpoints;
    } else {
      LOG(ERROR) << "Failed to check if filesystem checkpoints are supported: "
                 << supports_fs_checkpoints.error();
    }
    if (gSupportsFsCheckpoints) {
      Result<bool> needs_checkpoint = gVoldService->NeedsCheckpoint();
      if (needs_checkpoint.ok()) {
        gInFsCheckpointMode = *needs_checkpoint;
      } else {
        LOG(ERROR) << "Failed to check if we're in filesystem checkpoint mode: "
                   << needs_checkpoint.error();
      }
    }
  }
}

void InitializeSessionManager(ApexSessionManager* session_manager) {
  gSessionManager = session_manager;
}

void Initialize(CheckpointInterface* checkpoint_service) {
  InitializeVold(checkpoint_service);
  ApexFileRepository& instance = ApexFileRepository::GetInstance();
  Result<void> status = instance.AddPreInstalledApex(kApexPackageBuiltinDirs);
  if (!status.ok()) {
    LOG(ERROR) << "Failed to collect pre-installed APEX files : "
               << status.error();
    return;
  }

  // TODO(b/209491448) Remove this.
  if (auto block_status = AddBlockApex(instance); !block_status.ok()) {
    LOG(ERROR) << status.error();
    return;
  }

  gMountedApexes.PopulateFromMounts(
      {gConfig->active_apex_data_dir, gConfig->decompression_dir},
      gConfig->apex_hash_tree_dir);
}

// Note: Pre-installed apex are initialized in Initialize(CheckpointInterface*)
// TODO(b/172911822): Consolidate this with Initialize() when
//  ApexFileRepository can act as cache and re-scanning is not expensive
void InitializeDataApex() {
  ApexFileRepository& instance = ApexFileRepository::GetInstance();
  Result<void> status = instance.AddDataApex(kActiveApexPackagesDataDir);
  if (!status.ok()) {
    LOG(ERROR) << "Failed to collect data APEX files : " << status.error();
    return;
  }
}

/**
 * For every package X, there can be at most two APEX, pre-installed vs
 * installed on data. We usually select only one of these APEX for each package
 * based on the following conditions:
 *   - Package X must be pre-installed on one of the built-in directories.
 *   - If there are multiple APEX, we select the one with highest version.
 *   - If there are multiple with same version, we give priority to APEX on
 * /data partition.
 *
 * Typically, only one APEX is activated for each package, but APEX that provide
 * shared libs are exceptions. We have to activate both APEX for them.
 *
 * @param all_apex all the APEX grouped by their package name
 * @return list of ApexFile that needs to be activated
 */
std::vector<ApexFileRef> SelectApexForActivation(
    const std::unordered_map<std::string, std::vector<ApexFileRef>>& all_apex,
    const ApexFileRepository& instance) {
  LOG(INFO) << "Selecting APEX for activation";
  std::vector<ApexFileRef> activation_list;
  // For every package X, select which APEX to activate
  for (auto& apex_it : all_apex) {
    const std::string& package_name = apex_it.first;
    const std::vector<ApexFileRef>& apex_files = apex_it.second;

    if (apex_files.size() > 2 || apex_files.size() == 0) {
      LOG(FATAL) << "Unexpectedly found more than two versions or none for "
                    "APEX package "
                 << package_name;
      continue;
    }

    // The package must have a pre-installed version before we consider it for
    // activation
    if (!instance.HasPreInstalledVersion(package_name)) {
      LOG(INFO) << "Package " << package_name << " is not pre-installed";
      continue;
    }

    if (apex_files.size() == 1) {
      LOG(DEBUG) << "Selecting the only APEX: " << package_name << " "
                 << apex_files[0].get().GetPath();
      activation_list.emplace_back(apex_files[0]);
      continue;
    }

    // TODO(b/179497746): Now that we are dealing with list of reference, this
    //  selection process can be simplified by sorting the vector.

    // Given an APEX A and the version of the other APEX B, should we activate
    // it?
    auto select_apex = [&instance, &activation_list](
                           const ApexFileRef& a_ref,
                           const int version_b) mutable {
      const ApexFile& a = a_ref.get();
      // If A has higher version than B, then it should be activated
      const bool higher_version = a.GetManifest().version() > version_b;
      // If A has same version as B, then data version should get activated
      const bool same_version_priority_to_data =
          a.GetManifest().version() == version_b &&
          !instance.IsPreInstalledApex(a);

      // APEX that provides shared library are special:
      //  - if preinstalled version is lower than data version, both versions
      //    are activated.
      //  - if preinstalled version is equal to data version, data version only
      //    is activated.
      //  - if preinstalled version is higher than data version, preinstalled
      //    version only is activated.
      const bool provides_shared_apex_libs =
          a.GetManifest().providesharedapexlibs();
      bool activate = false;
      if (provides_shared_apex_libs) {
        // preinstalled version gets activated in all cases except when same
        // version as data.
        if (instance.IsPreInstalledApex(a) &&
            (a.GetManifest().version() != version_b)) {
          LOG(DEBUG) << "Activating preinstalled shared libs APEX: "
                     << a.GetManifest().name() << " " << a.GetPath();
          activate = true;
        }
        // data version gets activated in all cases except when its version
        // is lower than preinstalled version.
        if (!instance.IsPreInstalledApex(a) &&
            (a.GetManifest().version() >= version_b)) {
          LOG(DEBUG) << "Activating shared libs APEX: "
                     << a.GetManifest().name() << " " << a.GetPath();
          activate = true;
        }
      } else if (higher_version || same_version_priority_to_data) {
        LOG(DEBUG) << "Selecting between two APEX: " << a.GetManifest().name()
                   << " " << a.GetPath();
        activate = true;
      }
      if (activate) {
        activation_list.emplace_back(a_ref);
      }
    };
    const int version_0 = apex_files[0].get().GetManifest().version();
    const int version_1 = apex_files[1].get().GetManifest().version();
    select_apex(apex_files[0].get(), version_1);
    select_apex(apex_files[1].get(), version_0);
  }
  return activation_list;
}

namespace {

Result<ApexFile> OpenAndValidateDecompressedApex(const ApexFile& capex,
                                                 const std::string& apex_path) {
  auto apex = ApexFile::Open(apex_path);
  if (!apex.ok()) {
    return Error() << "Failed to open decompressed APEX: " << apex.error();
  }
  auto result = ValidateDecompressedApex(capex, *apex);
  if (!result.ok()) {
    return result.error();
  }
  auto ctx = GetfileconPath(apex_path);
  if (!ctx.ok()) {
    return ctx.error();
  }
  if (!StartsWith(*ctx, gConfig->active_apex_selinux_ctx)) {
    return Error() << apex_path << " has wrong SELinux context " << *ctx;
  }
  return std::move(*apex);
}

// Process a single compressed APEX. Returns the decompressed APEX if
// successful.
Result<ApexFile> ProcessCompressedApex(const ApexFile& capex,
                                       bool is_ota_chroot) {
  LOG(INFO) << "Processing compressed APEX " << capex.GetPath();
  const auto decompressed_apex_path =
      StringPrintf("%s/%s%s", gConfig->decompression_dir,
                   GetPackageId(capex.GetManifest()).c_str(),
                   kDecompressedApexPackageSuffix);
  // Check if decompressed APEX already exist
  auto decompressed_path_exists = PathExists(decompressed_apex_path);
  if (decompressed_path_exists.ok() && *decompressed_path_exists) {
    // Check if existing decompressed APEX is valid
    auto result =
        OpenAndValidateDecompressedApex(capex, decompressed_apex_path);
    if (result.ok()) {
      LOG(INFO) << "Skipping decompression for " << capex.GetPath();
      return result;
    }
    // Do not delete existing decompressed APEX when is_ota_chroot is true
    if (!is_ota_chroot) {
      // Existing decompressed APEX is not valid. We will have to redecompress
      LOG(WARNING) << "Existing decompressed APEX is invalid: "
                   << result.error();
      RemoveFileIfExists(decompressed_apex_path);
    }
  }

  // We can also reuse existing OTA APEX, depending on situation
  auto ota_apex_path = StringPrintf("%s/%s%s", gConfig->decompression_dir,
                                    GetPackageId(capex.GetManifest()).c_str(),
                                    kOtaApexPackageSuffix);
  auto ota_path_exists = PathExists(ota_apex_path);
  if (ota_path_exists.ok() && *ota_path_exists) {
    if (is_ota_chroot) {
      // During ota_chroot, we try to reuse ota APEX as is
      auto result = OpenAndValidateDecompressedApex(capex, ota_apex_path);
      if (result.ok()) {
        LOG(INFO) << "Skipping decompression for " << ota_apex_path;
        return result;
      }
      // Existing ota_apex is not valid. We will have to decompress
      LOG(WARNING) << "Existing decompressed OTA APEX is invalid: "
                   << result.error();
      RemoveFileIfExists(ota_apex_path);
    } else {
      // During boot, we can avoid decompression by renaming OTA apex
      // to expected decompressed_apex path

      // Check if ota_apex APEX is valid
      auto result = OpenAndValidateDecompressedApex(capex, ota_apex_path);
      if (result.ok()) {
        // ota_apex matches with capex. Slot has been switched.

        // Rename ota_apex to expected decompressed_apex path
        if (rename(ota_apex_path.c_str(), decompressed_apex_path.c_str()) ==
            0) {
          // Check if renamed decompressed APEX is valid
          result =
              OpenAndValidateDecompressedApex(capex, decompressed_apex_path);
          if (result.ok()) {
            LOG(INFO) << "Renamed " << ota_apex_path << " to "
                      << decompressed_apex_path;
            return result;
          }
          // Renamed ota_apex is not valid. We will have to decompress
          LOG(WARNING) << "Renamed decompressed APEX from " << ota_apex_path
                       << " to " << decompressed_apex_path
                       << " is invalid: " << result.error();
          RemoveFileIfExists(decompressed_apex_path);
        } else {
          PLOG(ERROR) << "Failed to rename file " << ota_apex_path;
        }
      }
    }
  }

  // There was no way to avoid decompression

  // Clean up reserved space before decompressing capex
  if (auto ret = DeleteDirContent(gConfig->ota_reserved_dir); !ret.ok()) {
    LOG(ERROR) << "Failed to clean up reserved space: " << ret.error();
  }

  auto decompression_dest =
      is_ota_chroot ? ota_apex_path : decompressed_apex_path;
  auto scope_guard = android::base::make_scope_guard(
      [&]() { RemoveFileIfExists(decompression_dest); });

  auto decompression_result = capex.Decompress(decompression_dest);
  if (!decompression_result.ok()) {
    return Error() << "Failed to decompress : " << capex.GetPath().c_str()
                   << " " << decompression_result.error();
  }

  // Fix label of decompressed file
  auto restore = RestoreconPath(decompression_dest);
  if (!restore.ok()) {
    return restore.error();
  }

  // Validate the newly decompressed APEX
  auto return_apex = OpenAndValidateDecompressedApex(capex, decompression_dest);
  if (!return_apex.ok()) {
    return Error() << "Failed to decompress CAPEX: " << return_apex.error();
  }

  gChangedActiveApexes.insert(return_apex->GetManifest().name());
  /// Release compressed blocks in case decompression_dest is on f2fs-compressed
  // filesystem.
  ReleaseF2fsCompressedBlocks(decompression_dest);

  scope_guard.Disable();
  return return_apex;
}
}  // namespace

/**
 * For each compressed APEX, decompress it to kApexDecompressedDir
 * and return the decompressed APEX.
 *
 * Returns list of decompressed APEX.
 */
std::vector<ApexFile> ProcessCompressedApex(
    const std::vector<ApexFileRef>& compressed_apex, bool is_ota_chroot) {
  LOG(INFO) << "Processing compressed APEX";

  std::vector<ApexFile> decompressed_apex_list;
  for (const ApexFile& capex : compressed_apex) {
    if (!capex.IsCompressed()) {
      continue;
    }

    auto decompressed_apex = ProcessCompressedApex(capex, is_ota_chroot);
    if (decompressed_apex.ok()) {
      decompressed_apex_list.emplace_back(std::move(*decompressed_apex));
      continue;
    }
    LOG(ERROR) << "Failed to process compressed APEX: "
               << decompressed_apex.error();
  }
  return std::move(decompressed_apex_list);
}

Result<void> ValidateDecompressedApex(const ApexFile& capex,
                                      const ApexFile& apex) {
  // Decompressed APEX must have same public key as CAPEX
  if (capex.GetBundledPublicKey() != apex.GetBundledPublicKey()) {
    return Error()
           << "Public key of compressed APEX is different than original "
           << "APEX for " << apex.GetPath();
  }
  // Decompressed APEX must have same version as CAPEX
  if (capex.GetManifest().version() != apex.GetManifest().version()) {
    return Error()
           << "Compressed APEX has different version than decompressed APEX "
           << apex.GetPath();
  }
  // Decompressed APEX must have same root digest as what is stored in CAPEX
  auto apex_verity = apex.VerifyApexVerity(apex.GetBundledPublicKey());
  if (!apex_verity.ok() ||
      capex.GetManifest().capexmetadata().originalapexdigest() !=
          apex_verity->root_digest) {
    return Error() << "Root digest of " << apex.GetPath()
                   << " does not match with"
                   << " expected root digest in " << capex.GetPath();
  }
  return {};
}

void OnStart() {
  ATRACE_NAME("OnStart");
  LOG(INFO) << "Marking APEXd as starting";
  auto time_started = boot_clock::now();
  if (!SetProperty(gConfig->apex_status_sysprop, kApexStatusStarting)) {
    PLOG(ERROR) << "Failed to set " << gConfig->apex_status_sysprop << " to "
                << kApexStatusStarting;
  }

  // Ask whether we should revert any active sessions; this can happen if
  // we've exceeded the retry count on a device that supports filesystem
  // checkpointing.
  if (gSupportsFsCheckpoints) {
    Result<bool> needs_revert = gVoldService->NeedsRollback();
    if (!needs_revert.ok()) {
      LOG(ERROR) << "Failed to check if we need a revert: "
                 << needs_revert.error();
    } else if (*needs_revert) {
      LOG(INFO) << "Exceeded number of session retries ("
                << kNumRetriesWhenCheckpointingEnabled
                << "). Starting a revert";
      RevertActiveSessions("", "");
    }
  }

  // Create directories for APEX shared libraries.
  auto sharedlibs_apex_dir = CreateSharedLibsApexDir();
  if (!sharedlibs_apex_dir.ok()) {
    LOG(ERROR) << sharedlibs_apex_dir.error();
  }

  // If there is any new apex to be installed on /data/app-staging, hardlink
  // them to /data/apex/active first.
  ScanStagedSessionsDirAndStage();
  if (auto status = ApexFileRepository::GetInstance().AddDataApex(
          gConfig->active_apex_data_dir);
      !status.ok()) {
    LOG(ERROR) << "Failed to collect data APEX files : " << status.error();
  }

  auto status = ResumeRevertIfNeeded();
  if (!status.ok()) {
    LOG(ERROR) << "Failed to resume revert : " << status.error();
  }

  // Group every ApexFile on device by name
  const auto& instance = ApexFileRepository::GetInstance();
  const auto& all_apex = instance.AllApexFilesByName();
  // There can be multiple APEX packages with package name X. Determine which
  // one to activate.
  auto activation_list = SelectApexForActivation(all_apex, instance);

  // Process compressed APEX, if any
  std::vector<ApexFileRef> compressed_apex;
  for (auto it = activation_list.begin(); it != activation_list.end();) {
    if (it->get().IsCompressed()) {
      compressed_apex.emplace_back(*it);
      it = activation_list.erase(it);
    } else {
      it++;
    }
  }
  std::vector<ApexFile> decompressed_apex;
  if (!compressed_apex.empty()) {
    decompressed_apex =
        ProcessCompressedApex(compressed_apex, /* is_ota_chroot= */ false);
    for (const ApexFile& apex_file : decompressed_apex) {
      activation_list.emplace_back(std::cref(apex_file));
    }
  }

  // TODO(b/179248390): activate parallelly if possible
  auto activate_status =
      ActivateApexPackages(activation_list, ActivationMode::kBootMode);
  if (!activate_status.ok()) {
    std::string error_message =
        StringPrintf("Failed to activate packages: %s",
                     activate_status.error().message().c_str());
    LOG(ERROR) << error_message;
    Result<void> revert_status =
        RevertActiveSessionsAndReboot("", error_message);
    if (!revert_status.ok()) {
      LOG(ERROR) << "Failed to revert : " << revert_status.error();
    }
    auto retry_status =
        ActivateMissingApexes(activation_list, ActivationMode::kBootMode);
    if (!retry_status.ok()) {
      LOG(ERROR) << retry_status.error();
    }
  }

  // Now that APEXes are mounted, snapshot or restore DE_sys data.
  SnapshotOrRestoreDeSysData();

  auto time_elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
    boot_clock::now() - time_started).count();
  LOG(INFO) << "OnStart done, duration=" << time_elapsed;
}

void OnAllPackagesActivated(bool is_bootstrap) {
  auto result = EmitApexInfoList(is_bootstrap);
  if (!result.ok()) {
    LOG(ERROR) << "cannot emit apex info list: " << result.error();
  }

  // Because apexd in bootstrap mode runs in blocking mode
  // we don't have to set as activated.
  if (is_bootstrap) {
    return;
  }

  // Set a system property to let other components know that APEXs are
  // activated, but are not yet ready to be used. init is expected to wait
  // for this status before performing configuration based on activated
  // apexes. Other components that need to use APEXs should wait for the
  // ready state instead.
  LOG(INFO) << "Marking APEXd as activated";
  if (!SetProperty(gConfig->apex_status_sysprop, kApexStatusActivated)) {
    PLOG(ERROR) << "Failed to set " << gConfig->apex_status_sysprop << " to "
                << kApexStatusActivated;
  }
}

void OnAllPackagesReady() {
  // Set a system property to let other components know that APEXs are
  // correctly mounted and ready to be used. Before using any file from APEXs,
  // they can query this system property to ensure that they are okay to
  // access. Or they may have a on-property trigger to delay a task until
  // APEXs become ready.
  LOG(INFO) << "Marking APEXd as ready";
  if (!SetProperty(gConfig->apex_status_sysprop, kApexStatusReady)) {
    PLOG(ERROR) << "Failed to set " << gConfig->apex_status_sysprop << " to "
                << kApexStatusReady;
  }
  // Since apexd.status property is a system property, we expose yet another
  // property as system_restricted_prop so that, for example, vendor can rely on
  // the "ready" event.
  if (!SetProperty(kApexAllReadyProp, "true")) {
    PLOG(ERROR) << "Failed to set " << kApexAllReadyProp << " to true";
  }
}

Result<std::vector<ApexFile>> SubmitStagedSession(
    const int session_id, const std::vector<int>& child_session_ids,
    const bool has_rollback_enabled, const bool is_rollback,
    const int rollback_id) {
  if (session_id == 0) {
    return Error() << "Session id was not provided.";
  }

  if (!gSupportsFsCheckpoints) {
    Result<void> backup_status = BackupActivePackages();
    if (!backup_status.ok()) {
      // Do not proceed with staged install without backup
      return backup_status.error();
    }
  }

  std::vector<int> ids_to_scan;
  if (!child_session_ids.empty()) {
    ids_to_scan = child_session_ids;
  } else {
    ids_to_scan = {session_id};
  }

  std::vector<ApexFile> ret;
  auto guard = android::base::make_scope_guard([&]() {
    for (const auto& apex : ret) {
      apexd_private::UnmountTempMount(apex);
    }
  });
  for (int id_to_scan : ids_to_scan) {
    auto verified = VerifySessionDir(id_to_scan);
    if (!verified.ok()) {
      return verified.error();
    }
    LOG(DEBUG) << verified->GetPath() << " is verified";
    ret.push_back(std::move(*verified));
  }

  if (has_rollback_enabled && is_rollback) {
    return Error() << "Cannot set session " << session_id << " as both a"
                   << " rollback and enabled for rollback.";
  }

  auto session = gSessionManager->CreateSession(session_id);
  if (!session.ok()) {
    return session.error();
  }
  (*session).SetChildSessionIds(child_session_ids);
  std::string build_fingerprint = GetProperty(kBuildFingerprintSysprop, "");
  (*session).SetBuildFingerprint(build_fingerprint);
  session->SetHasRollbackEnabled(has_rollback_enabled);
  session->SetIsRollback(is_rollback);
  session->SetRollbackId(rollback_id);
  for (const auto& apex_file : ret) {
    session->AddApexName(apex_file.GetManifest().name());
  }
  Result<void> commit_status =
      (*session).UpdateStateAndCommit(SessionState::VERIFIED);
  if (!commit_status.ok()) {
    return commit_status.error();
  }

  for (const auto& apex : ret) {
    // Release compressed blocks in case /data is f2fs-compressed filesystem.
    ReleaseF2fsCompressedBlocks(apex.GetPath());
  }

  // The scope guard above uses lambda that captures ret by reference.
  // Unfortunately, for the capture by-reference, lifetime of the captured
  // reference ends together with the lifetime of the closure object. This means
  // that we need to manually call UnmountTempMount here.
  for (const auto& apex : ret) {
    apexd_private::UnmountTempMount(apex);
  }

  return ret;
}

Result<void> MarkStagedSessionReady(const int session_id) {
  auto session = gSessionManager->GetSession(session_id);
  if (!session.ok()) {
    return session.error();
  }
  // We should only accept sessions in SessionState::VERIFIED or
  // SessionState::STAGED state. In the SessionState::STAGED case, this
  // function is effectively a no-op.
  auto session_state = (*session).GetState();
  if (session_state == SessionState::STAGED) {
    return {};
  }
  if (session_state == SessionState::VERIFIED) {
    return (*session).UpdateStateAndCommit(SessionState::STAGED);
  }
  return Error() << "Invalid state for session " << session_id
                 << ". Cannot mark it as ready.";
}

Result<void> MarkStagedSessionSuccessful(const int session_id) {
  auto session = gSessionManager->GetSession(session_id);
  if (!session.ok()) {
    return session.error();
  }
  // Only SessionState::ACTIVATED or SessionState::SUCCESS states are accepted.
  // In the SessionState::SUCCESS state, this function is a no-op.
  if (session->GetState() == SessionState::SUCCESS) {
    return {};
  } else if (session->GetState() == SessionState::ACTIVATED) {
    auto cleanup_status = DeleteBackup();
    if (!cleanup_status.ok()) {
      return Error() << "Failed to mark session " << *session
                     << " as successful : " << cleanup_status.error();
    }
    if (session->IsRollback() && !gSupportsFsCheckpoints) {
      DeleteDePreRestoreSnapshots(*session);
    }
    return session->UpdateStateAndCommit(SessionState::SUCCESS);
  } else {
    return Error() << "Session " << *session << " can not be marked successful";
  }
}

// Removes APEXes on /data that have not been activated
void RemoveInactiveDataApex() {
  std::vector<std::string> all_apex_files;
  Result<std::vector<std::string>> active_apex =
      FindFilesBySuffix(gConfig->active_apex_data_dir, {kApexPackageSuffix});
  if (!active_apex.ok()) {
    LOG(ERROR) << "Failed to scan " << gConfig->active_apex_data_dir << " : "
               << active_apex.error();
  } else {
    all_apex_files.insert(all_apex_files.end(),
                          std::make_move_iterator(active_apex->begin()),
                          std::make_move_iterator(active_apex->end()));
  }
  Result<std::vector<std::string>> decompressed_apex = FindFilesBySuffix(
      gConfig->decompression_dir, {kDecompressedApexPackageSuffix});
  if (!decompressed_apex.ok()) {
    LOG(ERROR) << "Failed to scan " << gConfig->decompression_dir << " : "
               << decompressed_apex.error();
  } else {
    all_apex_files.insert(all_apex_files.end(),
                          std::make_move_iterator(decompressed_apex->begin()),
                          std::make_move_iterator(decompressed_apex->end()));
  }

  for (const auto& path : all_apex_files) {
    if (!apexd_private::IsMounted(path)) {
      LOG(INFO) << "Removing inactive data APEX " << path;
      if (unlink(path.c_str()) != 0) {
        PLOG(ERROR) << "Failed to unlink inactive data APEX " << path;
      }
    }
  }
}

bool IsApexDevice(const std::string& dev_name) {
  auto& repo = ApexFileRepository::GetInstance();
  for (const auto& apex : repo.GetPreInstalledApexFiles()) {
    if (StartsWith(dev_name, apex.get().GetManifest().name())) {
      return true;
    }
  }
  return false;
}

// TODO(b/192241176): move to apexd_verity.{h,cpp}.
void DeleteUnusedVerityDevices() {
  DeviceMapper& dm = DeviceMapper::Instance();
  std::vector<DeviceMapper::DmBlockDevice> all_devices;
  if (!dm.GetAvailableDevices(&all_devices)) {
    LOG(WARNING) << "Failed to fetch dm devices";
    return;
  }
  for (const auto& dev : all_devices) {
    auto state = dm.GetState(dev.name());
    if (state == DmDeviceState::SUSPENDED && IsApexDevice(dev.name())) {
      LOG(INFO) << "Deleting unused dm device " << dev.name();
      auto res = DeleteVerityDevice(dev.name(), /* deferred= */ false);
      if (!res.ok()) {
        LOG(WARNING) << res.error();
      }
    }
  }
}

void BootCompletedCleanup() {
  RemoveInactiveDataApex();

  auto sessions = gSessionManager->GetSessions();
  for (const ApexSession& session : sessions) {
    if (!session.IsFinalized()) {
      continue;
    }
    auto result = session.DeleteSession();
    if (!result.ok()) {
      LOG(WARNING) << "Failed to delete finalized session: " << session.GetId();
    }
  }

  DeleteUnusedVerityDevices();
}

int UnmountAll(bool also_include_staged_apexes) {
  std::vector<std::string> data_dirs = {gConfig->active_apex_data_dir,
                                        gConfig->decompression_dir};

  if (also_include_staged_apexes) {
    for (const ApexSession& session :
         gSessionManager->GetSessionsInState(SessionState::STAGED)) {
      std::vector<std::string> dirs_to_scan =
          session.GetStagedApexDirs(gConfig->staged_session_dir);
      std::move(dirs_to_scan.begin(), dirs_to_scan.end(),
                std::back_inserter(data_dirs));
    }
  }

  gMountedApexes.PopulateFromMounts(data_dirs, gConfig->apex_hash_tree_dir);
  int ret = 0;
  gMountedApexes.ForallMountedApexes([&](const std::string& /*package*/,
                                         const MountedApexData& data,
                                         bool latest) {
    LOG(INFO) << "Unmounting " << data.full_path << " mounted on "
              << data.mount_point;
    auto apex = ApexFile::Open(data.full_path);
    if (!apex.ok()) {
      LOG(ERROR) << "Failed to open " << data.full_path << " : "
                 << apex.error();
      ret = 1;
      return;
    }
    if (latest && !apex->GetManifest().providesharedapexlibs()) {
      auto pos = data.mount_point.find('@');
      CHECK(pos != std::string::npos);
      std::string bind_mount = data.mount_point.substr(0, pos);
      if (umount2(bind_mount.c_str(), UMOUNT_NOFOLLOW) != 0) {
        PLOG(ERROR) << "Failed to unmount bind-mount " << bind_mount;
        ret = 1;
        return;
      }
    }
    if (auto status = Unmount(data, /* deferred= */ false); !status.ok()) {
      LOG(ERROR) << "Failed to unmount " << data.mount_point << " : "
                 << status.error();
      ret = 1;
    }
  });
  return ret;
}

Result<void> RemountPackages() {
  std::vector<std::string> apexes;
  gMountedApexes.ForallMountedApexes([&apexes](const std::string& /*package*/,
                                               const MountedApexData& data,
                                               bool latest) {
    if (latest) {
      LOG(DEBUG) << "Found active APEX " << data.full_path;
      apexes.push_back(data.full_path);
    }
  });
  std::vector<std::string> failed;
  for (const std::string& apex : apexes) {
    // Since this is only used during development workflow, we are trying to
    // remount as many apexes as possible instead of failing fast.
    if (auto ret = RemountApexFile(apex); !ret.ok()) {
      LOG(WARNING) << "Failed to remount " << apex << " : " << ret.error();
      failed.emplace_back(apex);
    }
  }
  static constexpr const char* kErrorMessage =
      "Failed to remount following APEX packages, hence previous versions of "
      "them are still active. If APEX you are developing is in this list, it "
      "means that there still are alive processes holding a reference to the "
      "previous version of your APEX.\n";
  if (!failed.empty()) {
    return Error() << kErrorMessage << "Failed (" << failed.size() << ") "
                   << "APEX packages: [" << Join(failed, ',') << "]";
  }
  return {};
}

// Given a single new APEX incoming via OTA, should we allocate space for it?
bool ShouldAllocateSpaceForDecompression(const std::string& new_apex_name,
                                         const int64_t new_apex_version,
                                         const ApexFileRepository& instance) {
  // An apex at most will have two versions on device: pre-installed and data.

  // Check if there is a pre-installed version for the new apex.
  if (!instance.HasPreInstalledVersion(new_apex_name)) {
    // We are introducing a new APEX that doesn't exist at all
    return true;
  }

  // Check if there is a data apex
  if (!instance.HasDataVersion(new_apex_name)) {
    // Data apex doesn't exist. Compare against pre-installed APEX
    auto pre_installed_apex = instance.GetPreInstalledApex(new_apex_name);
    if (!pre_installed_apex.get().IsCompressed()) {
      // Compressing an existing uncompressed system APEX.
      return true;
    }
    // Since there is no data apex, it means device is using the compressed
    // pre-installed version. If new apex has higher version, we are upgrading
    // the pre-install version and if new apex has lower version, we are
    // downgrading it. So the current decompressed apex should be replaced
    // with the new decompressed apex to reflect that.
    const int64_t pre_installed_version =
        instance.GetPreInstalledApex(new_apex_name)
            .get()
            .GetManifest()
            .version();
    return new_apex_version != pre_installed_version;
  }

  // From here on, data apex exists. So we should compare directly against data
  // apex.
  auto data_apex = instance.GetDataApex(new_apex_name);
  // Compare the data apex version with new apex
  const int64_t data_version = data_apex.get().GetManifest().version();
  // We only decompress the new_apex if it has higher version than data apex.
  return new_apex_version > data_version;
}

int64_t CalculateSizeForCompressedApex(
    const std::vector<std::tuple<std::string, int64_t, int64_t>>&
        compressed_apexes,
    const ApexFileRepository& instance) {
  int64_t result = 0;
  for (const auto& compressed_apex : compressed_apexes) {
    std::string module_name;
    int64_t version_code;
    int64_t decompressed_size;
    std::tie(module_name, version_code, decompressed_size) = compressed_apex;
    if (ShouldAllocateSpaceForDecompression(module_name, version_code,
                                            instance)) {
      result += decompressed_size;
    }
  }
  return result;
}

void CollectApexInfoList(std::ostream& os,
                         const std::vector<ApexFile>& active_apexs,
                         const std::vector<ApexFile>& inactive_apexs) {
  std::vector<com::android::apex::ApexInfo> apex_infos;

  auto convert_to_autogen = [&apex_infos](const ApexFile& apex,
                                          bool is_active) {
    auto& instance = ApexFileRepository::GetInstance();

    auto preinstalled_path =
        instance.GetPreinstalledPath(apex.GetManifest().name());
    std::optional<std::string> preinstalled_module_path;
    if (preinstalled_path.ok()) {
      preinstalled_module_path = *preinstalled_path;
    }

    std::optional<int64_t> mtime =
        instance.GetBlockApexLastUpdateSeconds(apex.GetPath());
    if (!mtime.has_value()) {
      struct stat stat_buf;
      if (stat(apex.GetPath().c_str(), &stat_buf) == 0) {
        mtime.emplace(stat_buf.st_mtime);
      } else {
        PLOG(WARNING) << "Failed to stat " << apex.GetPath();
      }
    }
    com::android::apex::ApexInfo apex_info(
        apex.GetManifest().name(), apex.GetPath(), preinstalled_module_path,
        apex.GetManifest().version(), apex.GetManifest().versionname(),
        instance.IsPreInstalledApex(apex), is_active, mtime,
        apex.GetManifest().providesharedapexlibs());
    apex_infos.emplace_back(std::move(apex_info));
  };
  for (const auto& apex : active_apexs) {
    convert_to_autogen(apex, /* is_active= */ true);
  }
  for (const auto& apex : inactive_apexs) {
    convert_to_autogen(apex, /* is_active= */ false);
  }
  com::android::apex::ApexInfoList apex_info_list(apex_infos);
  com::android::apex::write(os, apex_info_list);
}

// Reserve |size| bytes in |dest_dir| by creating a zero-filled file.
// Also, we always clean up ota_apex that has been processed as
// part of pre-reboot decompression whenever we reserve space.
Result<void> ReserveSpaceForCompressedApex(int64_t size,
                                           const std::string& dest_dir) {
  if (size < 0) {
    return Error() << "Cannot reserve negative byte of space";
  }

  // Since we are reserving space, then we must be preparing for a new OTA.
  // Clean up any processed ota_apex from previous OTA.
  auto ota_apex_files =
      FindFilesBySuffix(gConfig->decompression_dir, {kOtaApexPackageSuffix});
  if (!ota_apex_files.ok()) {
    return Error() << "Failed to clean up ota_apex: " << ota_apex_files.error();
  }
  for (const std::string& ota_apex : *ota_apex_files) {
    RemoveFileIfExists(ota_apex);
  }

  auto file_path = StringPrintf("%s/full.tmp", dest_dir.c_str());
  if (size == 0) {
    LOG(INFO) << "Cleaning up reserved space for compressed APEX";
    // Ota is being cancelled. Clean up reserved space
    RemoveFileIfExists(file_path);
    return {};
  }

  LOG(INFO) << "Reserving " << size << " bytes for compressed APEX";
  unique_fd dest_fd(
      open(file_path.c_str(), O_WRONLY | O_CLOEXEC | O_CREAT, 0644));
  if (dest_fd.get() == -1) {
    return ErrnoError() << "Failed to open file for reservation "
                        << file_path.c_str();
  }

  // Resize to required size
  std::error_code ec;
  std::filesystem::resize_file(file_path, size, ec);
  if (ec) {
    RemoveFileIfExists(file_path);
    return ErrnoError() << "Failed to resize file " << file_path.c_str()
                        << " : " << ec.message();
  }

  return {};
}

// Adds block apexes if system property is set.
Result<int> AddBlockApex(ApexFileRepository& instance) {
  auto prop = GetProperty(gConfig->vm_payload_metadata_partition_prop, "");
  if (prop != "") {
    auto block_count = instance.AddBlockApex(prop);
    if (!block_count.ok()) {
      return Error() << "Failed to scan block APEX files: "
                     << block_count.error();
    }
    return block_count;
  } else {
    LOG(INFO) << "No block apex metadata partition found, not adding block "
              << "apexes";
  }
  return 0;
}

// When running in the VM mode, we follow the minimal start-up operations.
// - CreateSharedLibsApexDir
// - AddPreInstalledApex: note that CAPEXes are not supported in the VM mode
// - AddBlockApex
// - ActivateApexPackages
// - setprop apexd.status: activated/ready
int OnStartInVmMode() {
  Result<void> loop_ready = WaitForFile("/dev/loop-control", 20s);
  if (!loop_ready.ok()) {
    LOG(ERROR) << loop_ready.error();
  }

  // Create directories for APEX shared libraries.
  if (auto status = CreateSharedLibsApexDir(); !status.ok()) {
    LOG(ERROR) << "Failed to create /apex/sharedlibs : " << status.ok();
    return 1;
  }

  auto& instance = ApexFileRepository::GetInstance();

  // Scan pre-installed apexes
  if (auto status = instance.AddPreInstalledApex(gConfig->apex_built_in_dirs);
      !status.ok()) {
    LOG(ERROR) << "Failed to scan pre-installed APEX files: " << status.error();
    return 1;
  }

  if (auto status = AddBlockApex(instance); !status.ok()) {
    LOG(ERROR) << status.error();
    return 1;
  }

  if (auto status = ActivateApexPackages(instance.GetPreInstalledApexFiles(),
                                         ActivationMode::kVmMode);
      !status.ok()) {
    LOG(ERROR) << "Failed to activate apex packages : " << status.error();
    return 1;
  }
  if (auto status = ActivateApexPackages(instance.GetDataApexFiles(),
                                         ActivationMode::kVmMode);
      !status.ok()) {
    LOG(ERROR) << "Failed to activate apex packages : " << status.error();
    return 1;
  }

  OnAllPackagesActivated(false);
  // In VM mode, we don't run a separate --snapshotde mode.
  // Instead, we mark apexd.status "ready" right now.
  OnAllPackagesReady();
  return 0;
}

int OnOtaChrootBootstrap(bool also_include_staged_apexes) {
  auto& instance = ApexFileRepository::GetInstance();
  if (auto status = instance.AddPreInstalledApex(gConfig->apex_built_in_dirs);
      !status.ok()) {
    LOG(ERROR) << "Failed to scan pre-installed apexes from "
               << Join(gConfig->apex_built_in_dirs, ',');
    return 1;
  }
  if (also_include_staged_apexes) {
    // Scan staged dirs, and then scan the active dir. If a module is in both a
    // staged dir and the active dir, the APEX with a higher version will be
    // picked. If the versions are equal, the APEX in staged dir will be picked.
    //
    // The result is an approximation of what the active dir will actually have
    // after the reboot. In case of a downgrade install, it differs from the
    // actual, but this is not a supported case.
    for (const ApexSession& session :
         gSessionManager->GetSessionsInState(SessionState::STAGED)) {
      std::vector<std::string> dirs_to_scan =
          session.GetStagedApexDirs(gConfig->staged_session_dir);
      for (const std::string& dir_to_scan : dirs_to_scan) {
        if (auto status = instance.AddDataApex(dir_to_scan); !status.ok()) {
          LOG(ERROR) << "Failed to scan staged apexes from " << dir_to_scan;
          return 1;
        }
      }
    }
  }
  if (auto status = instance.AddDataApex(gConfig->active_apex_data_dir);
      !status.ok()) {
    LOG(ERROR) << "Failed to scan upgraded apexes from "
               << gConfig->active_apex_data_dir;
    // Fail early because we know we will be wasting cycles generating garbage
    // if we continue.
    return 1;
  }

  // Create directories for APEX shared libraries.
  if (auto status = CreateSharedLibsApexDir(); !status.ok()) {
    LOG(ERROR) << "Failed to create /apex/sharedlibs : " << status.ok();
    return 1;
  }

  auto activation_list =
      SelectApexForActivation(instance.AllApexFilesByName(), instance);

  // TODO(b/179497746): This is the third time we are duplicating this code
  // block. This will be easier to dedup once we start opening ApexFiles via
  // ApexFileRepository. That way, ProcessCompressedApex can return list of
  // ApexFileRef, instead of ApexFile.

  // Process compressed APEX, if any
  std::vector<ApexFileRef> compressed_apex;
  for (auto it = activation_list.begin(); it != activation_list.end();) {
    if (it->get().IsCompressed()) {
      compressed_apex.emplace_back(*it);
      it = activation_list.erase(it);
    } else {
      it++;
    }
  }
  std::vector<ApexFile> decompressed_apex;
  if (!compressed_apex.empty()) {
    decompressed_apex =
        ProcessCompressedApex(compressed_apex, /* is_ota_chroot= */ true);

    for (const ApexFile& apex_file : decompressed_apex) {
      activation_list.emplace_back(std::cref(apex_file));
    }
  }

  auto activate_status =
      ActivateApexPackages(activation_list, ActivationMode::kOtaChrootMode);
  if (!activate_status.ok()) {
    LOG(ERROR) << "Failed to activate apex packages : "
               << activate_status.error();
    auto retry_status =
        ActivateMissingApexes(activation_list, ActivationMode::kOtaChrootMode);
    if (!retry_status.ok()) {
      LOG(ERROR) << retry_status.error();
    }
  }

  // There are a bunch of places that are producing apex-info.xml file.
  // We should consolidate the logic in one function and make all other places
  // use it.
  auto active_apexes = GetActivePackages();
  std::vector<ApexFile> inactive_apexes = GetFactoryPackages();
  auto new_end = std::remove_if(
      inactive_apexes.begin(), inactive_apexes.end(),
      [&active_apexes](const ApexFile& apex) {
        return std::any_of(active_apexes.begin(), active_apexes.end(),
                           [&apex](const ApexFile& active_apex) {
                             return apex.GetPath() == active_apex.GetPath();
                           });
      });
  inactive_apexes.erase(new_end, inactive_apexes.end());
  std::stringstream xml;
  CollectApexInfoList(xml, active_apexes, inactive_apexes);
  std::string file_name = StringPrintf("%s/%s", kApexRoot, kApexInfoList);
  unique_fd fd(TEMP_FAILURE_RETRY(
      open(file_name.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644)));
  if (fd.get() == -1) {
    PLOG(ERROR) << "Can't open " << file_name;
    return 1;
  }

  if (!android::base::WriteStringToFd(xml.str(), fd)) {
    PLOG(ERROR) << "Can't write to " << file_name;
    return 1;
  }

  fd.reset();

  if (auto status = RestoreconPath(file_name); !status.ok()) {
    LOG(ERROR) << "Failed to restorecon " << file_name << " : "
               << status.error();
    return 1;
  }

  return 0;
}

android::apex::MountedApexDatabase& GetApexDatabaseForTesting() {
  return gMountedApexes;
}

// A version of apex verification that happens during non-staged APEX
// installation.
Result<void> VerifyPackageNonStagedInstall(const ApexFile& apex_file,
                                           bool force) {
  const auto& verify_package_boot_status = VerifyPackageBoot(apex_file);
  if (!verify_package_boot_status.ok()) {
    return verify_package_boot_status;
  }

  auto check_fn = [&apex_file,
                   &force](const std::string& mount_point) -> Result<void> {
    if (force) {
      return Result<void>{};
    }
    auto dirs = GetSubdirs(mount_point);
    if (!dirs.ok()) {
      return dirs.error();
    }
    if (std::find(dirs->begin(), dirs->end(), mount_point + "/app") !=
        dirs->end()) {
      return Error() << apex_file.GetPath() << " contains app inside";
    }
    if (std::find(dirs->begin(), dirs->end(), mount_point + "/priv-app") !=
        dirs->end()) {
      return Error() << apex_file.GetPath() << " contains priv-app inside";
    }
    if (IsVendorApex(apex_file)) {
      return CheckVendorApexUpdate(apex_file, mount_point);
    }
    return Result<void>{};
  };
  return RunVerifyFnInsideTempMount(apex_file, check_fn, true);
}

Result<void> CheckSupportsNonStagedInstall(const ApexFile& new_apex,
                                           bool force) {
  const auto& new_manifest = new_apex.GetManifest();

  if (!force) {
    if (!new_manifest.supportsrebootlessupdate()) {
      return Error() << new_apex.GetPath()
                     << " does not support non-staged update";
    }

    // Check if update will impact linkerconfig.

    // Updates to shared libs APEXes must be done via staged install flow.
    if (new_manifest.providesharedapexlibs()) {
      return Error() << new_apex.GetPath() << " is a shared libs APEX";
    }

    // This APEX provides native libs to other parts of the platform. It can
    // only be updated via staged install flow.
    if (new_manifest.providenativelibs_size() > 0) {
      return Error() << new_apex.GetPath() << " provides native libs";
    }

    // This APEX requires libs provided by dynamic common library APEX, hence it
    // can only be installed using staged install flow.
    if (new_manifest.requiresharedapexlibs_size() > 0) {
      return Error() << new_apex.GetPath() << " requires shared apex libs";
    }

    // We don't allow non-staged updates of APEXES that have java libs inside.
    if (new_manifest.jnilibs_size() > 0) {
      return Error() << new_apex.GetPath() << " requires JNI libs";
    }
  }

  auto expected_public_key =
      ApexFileRepository::GetInstance().GetPublicKey(new_manifest.name());
  if (!expected_public_key.ok()) {
    return expected_public_key.error();
  }
  auto verity_data = new_apex.VerifyApexVerity(*expected_public_key);
  if (!verity_data.ok()) {
    return verity_data.error();
  }
  // Supporting non-staged install of APEXes without a hashtree is additional
  // hassle, it's easier not to support it.
  if (verity_data->desc->tree_size == 0) {
    return Error() << new_apex.GetPath()
                   << " does not have an embedded hash tree";
  }
  return {};
}

Result<size_t> ComputePackageIdMinor(const ApexFile& apex) {
  static constexpr size_t kMaxVerityDevicesPerApexName = 3u;
  DeviceMapper& dm = DeviceMapper::Instance();
  std::vector<DeviceMapper::DmBlockDevice> dm_devices;
  if (!dm.GetAvailableDevices(&dm_devices)) {
    return Error() << "Failed to list dm devices";
  }
  size_t devices = 0;
  size_t next_minor = 1;
  for (const auto& dm_device : dm_devices) {
    std::string_view dm_name(dm_device.name());
    // Format is <module_name>@<version_code>[_<minor>]
    if (!ConsumePrefix(&dm_name, apex.GetManifest().name())) {
      continue;
    }
    devices++;
    auto pos = dm_name.find_last_of('_');
    if (pos == std::string_view::npos) {
      continue;
    }
    size_t minor;
    if (!ParseUint(std::string(dm_name.substr(pos + 1)), &minor)) {
      return Error() << "Unexpected dm device name " << dm_device.name();
    }
    if (next_minor < minor + 1) {
      next_minor = minor + 1;
    }
  }
  if (devices > kMaxVerityDevicesPerApexName) {
    return Error() << "There are too many (" << devices
                   << ") dm block devices associated with package "
                   << apex.GetManifest().name();
  }
  while (true) {
    std::string target_file =
        StringPrintf("%s/%s_%zu.apex", gConfig->active_apex_data_dir,
                     GetPackageId(apex.GetManifest()).c_str(), next_minor);
    if (access(target_file.c_str(), F_OK) == 0) {
      next_minor++;
    } else {
      break;
    }
  }

  return next_minor;
}

Result<void> UpdateApexInfoList() {
  std::vector<ApexFile> active(GetActivePackages());
  std::vector<ApexFile> inactive = CalculateInactivePackages(active);

  std::stringstream xml;
  CollectApexInfoList(xml, active, inactive);

  std::string name = StringPrintf("%s/.default-%s", kApexRoot, kApexInfoList);
  unique_fd fd(TEMP_FAILURE_RETRY(
      open(name.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644)));
  if (fd.get() == -1) {
    return ErrnoError() << "Can't open " << name;
  }
  if (!WriteStringToFd(xml.str(), fd)) {
    return ErrnoError() << "Failed to write to " << name;
  }

  return {};
}

// TODO(b/238820991) Handle failures
Result<void> UnloadApexFromInit(const std::string& apex_name) {
  if (!SetProperty(kCtlApexUnloadSysprop, apex_name)) {
    // When failed to SetProperty(), there's nothing we can do here.
    // Log error and return early to avoid indefinite waiting for ack.
    return Error() << "Failed to set " << kCtlApexUnloadSysprop << " to "
                << apex_name;
  }
  SetProperty("apex." + apex_name + ".ready", "false");
  return {};
}

// TODO(b/238820991) Handle failures
Result<void> LoadApexFromInit(const std::string& apex_name) {
  if (!SetProperty(kCtlApexLoadSysprop, apex_name)) {
    // When failed to SetProperty(), there's nothing we can do here.
    // Log error and return early to avoid indefinite waiting for ack.
    return Error() << "Failed to set " << kCtlApexLoadSysprop << " to "
                << apex_name;
  }
  SetProperty("apex." + apex_name + ".ready", "true");
  return {};
}

Result<ApexFile> InstallPackage(const std::string& package_path, bool force) {
  LOG(INFO) << "Installing " << package_path;
  auto temp_apex = ApexFile::Open(package_path);
  if (!temp_apex.ok()) {
    return temp_apex.error();
  }

  const std::string& module_name = temp_apex->GetManifest().name();
  // Don't allow non-staged update if there are no active versions of this APEX.
  auto cur_mounted_data = gMountedApexes.GetLatestMountedApex(module_name);
  if (!cur_mounted_data.has_value()) {
    return Error() << "No active version found for package " << module_name;
  }

  auto cur_apex = ApexFile::Open(cur_mounted_data->full_path);
  if (!cur_apex.ok()) {
    return cur_apex.error();
  }

  // Do a quick check if this APEX can be installed without a reboot.
  // Note that passing this check doesn't guarantee that APEX will be
  // successfully installed.
  if (auto r = CheckSupportsNonStagedInstall(*temp_apex, force); !r.ok()) {
    return r.error();
  }

  // 1. Verify that APEX is correct. This is a heavy check that involves
  // mounting an APEX on a temporary mount point and reading the entire
  // dm-verity block device.
  if (auto res = VerifyPackageNonStagedInstall(*temp_apex, force); !res.ok()) {
    return res.error();
  }

  // 2. Compute params for mounting new apex.
  auto new_id_minor = ComputePackageIdMinor(*temp_apex);
  if (!new_id_minor.ok()) {
    return new_id_minor.error();
  }

  std::string new_id = GetPackageId(temp_apex->GetManifest()) + "_" +
                       std::to_string(*new_id_minor);

  // Before unmounting the current apex, unload it from the init process:
  // terminates services started from the apex and init scripts read from the
  // apex.
  OR_RETURN(UnloadApexFromInit(module_name));

  // And then reload it from the init process whether it succeeds or not.
  auto reload_apex = android::base::make_scope_guard([&]() {
    if (auto status = LoadApexFromInit(module_name); !status.ok()) {
      LOG(ERROR) << "Failed to load apex " << module_name
                  << " : " << status.error().message();
    }
  });

  // 2. Unmount currently active APEX.
  if (auto res =
          UnmountPackage(*cur_apex, /* allow_latest= */ true,
                         /* deferred= */ true, /* detach_mount_point= */ force);
      !res.ok()) {
    return res.error();
  }

  // 3. Hard link to final destination.
  std::string target_file =
      StringPrintf("%s/%s.apex", gConfig->active_apex_data_dir, new_id.c_str());

  auto guard = android::base::make_scope_guard([&]() {
    if (unlink(target_file.c_str()) != 0 && errno != ENOENT) {
      PLOG(ERROR) << "Failed to unlink " << target_file;
    }
    // We can't really rely on the fact that dm-verity device backing up
    // previously active APEX is still around. We need to create a new one.
    std::string old_new_id = GetPackageId(temp_apex->GetManifest()) + "_" +
                             std::to_string(*new_id_minor + 1);
    auto res = ActivatePackageImpl(*cur_apex, old_new_id,
                                   /* reuse_device= */ false);
    if (!res.ok()) {
      // At this point not much we can do... :(
      LOG(ERROR) << res.error();
    }
  });

  // At this point it should be safe to hard link |temp_apex| to
  // |params->target_file|. In case reboot happens during one of the stages
  // below, then on next boot apexd will pick up the new verified APEX.
  if (link(package_path.c_str(), target_file.c_str()) != 0) {
    return ErrnoError() << "Failed to link " << package_path << " to "
                        << target_file;
  }

  auto new_apex = ApexFile::Open(target_file);
  if (!new_apex.ok()) {
    return new_apex.error();
  }

  // 4. And activate new one.
  auto activate_status = ActivatePackageImpl(*new_apex, new_id,
                                             /* reuse_device= */ false);
  if (!activate_status.ok()) {
    return activate_status.error();
  }

  // Accept the install.
  guard.Disable();

  // 4. Now we can unlink old APEX if it's not pre-installed.
  if (!ApexFileRepository::GetInstance().IsPreInstalledApex(*cur_apex)) {
    if (unlink(cur_mounted_data->full_path.c_str()) != 0) {
      PLOG(ERROR) << "Failed to unlink " << cur_mounted_data->full_path;
    }
  }

  if (auto res = UpdateApexInfoList(); !res.ok()) {
    LOG(ERROR) << res.error();
  }

  // Release compressed blocks in case target_file is on f2fs-compressed
  // filesystem.
  ReleaseF2fsCompressedBlocks(target_file);

  return new_apex;
}

bool IsActiveApexChanged(const ApexFile& apex) {
  return gChangedActiveApexes.find(apex.GetManifest().name()) !=
         gChangedActiveApexes.end();
}

std::set<std::string>& GetChangedActiveApexesForTesting() {
  return gChangedActiveApexes;
}

}  // namespace apex
}  // namespace android
