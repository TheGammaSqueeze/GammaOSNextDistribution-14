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

#include "apex_database.h"
#include "apex_constants.h"
#include "apex_file.h"
#include "apexd_utils.h"
#include "string_log.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/strings.h>

#include <filesystem>
#include <fstream>
#include <string>
#include <unordered_map>
#include <utility>

using android::base::ConsumeSuffix;
using android::base::EndsWith;
using android::base::ErrnoError;
using android::base::Error;
using android::base::ParseInt;
using android::base::ReadFileToString;
using android::base::Result;
using android::base::Split;
using android::base::StartsWith;
using android::base::Trim;

namespace fs = std::filesystem;

namespace android {
namespace apex {

namespace {

using MountedApexData = MountedApexDatabase::MountedApexData;

enum BlockDeviceType {
  UnknownDevice,
  LoopDevice,
  DeviceMapperDevice,
};

const fs::path kDevBlock = "/dev/block";
const fs::path kSysBlock = "/sys/block";

class BlockDevice {
  std::string name;  // loopN, dm-N, ...
 public:
  explicit BlockDevice(const fs::path& path) { name = path.filename(); }

  BlockDeviceType GetType() const {
    if (StartsWith(name, "loop")) return LoopDevice;
    if (StartsWith(name, "dm-")) return DeviceMapperDevice;
    return UnknownDevice;
  }

  fs::path SysPath() const { return kSysBlock / name; }

  fs::path DevPath() const { return kDevBlock / name; }

  Result<std::string> GetProperty(const std::string& property) const {
    auto property_file = SysPath() / property;
    std::string property_value;
    if (!ReadFileToString(property_file, &property_value)) {
      return ErrnoError() << "Fail to read";
    }
    return Trim(property_value);
  }

  std::vector<BlockDevice> GetSlaves() const {
    std::vector<BlockDevice> slaves;
    std::error_code ec;
    auto status = WalkDir(SysPath() / "slaves", [&](const auto& entry) {
      BlockDevice dev(entry);
      if (fs::is_block_file(dev.DevPath(), ec)) {
        slaves.push_back(dev);
      }
    });
    if (!status.ok()) {
      LOG(WARNING) << status.error();
    }
    return slaves;
  }
};

std::pair<fs::path, fs::path> ParseMountInfo(const std::string& mount_info) {
  const auto& tokens = Split(mount_info, " ");
  if (tokens.size() < 2) {
    return std::make_pair("", "");
  }
  return std::make_pair(tokens[0], tokens[1]);
}

std::pair<std::string, int> ParseMountPoint(const std::string& mount_point) {
  auto package_id = fs::path(mount_point).filename();
  auto split = Split(package_id, "@");
  if (split.size() == 2) {
    int version;
    if (!ParseInt(split[1], &version)) {
      version = -1;
    }
    return std::make_pair(split[0], version);
  }
  return std::make_pair(package_id, -1);
}

bool IsActiveMountPoint(const std::string& mount_point) {
  return (mount_point.find('@') == std::string::npos);
}

Result<void> PopulateLoopInfo(const BlockDevice& top_device,
                              const std::vector<std::string>& data_dirs,
                              const std::string& apex_hash_tree_dir,
                              MountedApexData* apex_data) {
  std::vector<BlockDevice> slaves = top_device.GetSlaves();
  if (slaves.size() != 1 && slaves.size() != 2) {
    return Error() << "dm device " << top_device.DevPath()
                   << " has unexpected number of slaves : " << slaves.size();
  }
  std::vector<std::string> backing_files;
  backing_files.reserve(slaves.size());
  for (const auto& dev : slaves) {
    if (dev.GetType() != LoopDevice) {
      return Error() << dev.DevPath() << " is not a loop device";
    }
    auto backing_file = dev.GetProperty("loop/backing_file");
    if (!backing_file.ok()) {
      return backing_file.error();
    }
    backing_files.push_back(std::move(*backing_file));
  }
  // Enforce following invariant:
  //  * slaves[0] always represents a data loop device
  //  * if size = 2 then slaves[1] represents an external hashtree loop device
  auto is_data_loop_device = [&](const std::string& backing_file) {
    return std::any_of(
        data_dirs.begin(), data_dirs.end(),
        [&](const std::string& dir) { return StartsWith(backing_file, dir); });
  };
  if (slaves.size() == 2) {
    if (!is_data_loop_device(backing_files[0])) {
      std::swap(slaves[0], slaves[1]);
      std::swap(backing_files[0], backing_files[1]);
    }
  }
  if (!is_data_loop_device(backing_files[0])) {
    return Error() << "Data loop device " << slaves[0].DevPath()
                   << " has unexpected backing file " << backing_files[0];
  }
  if (slaves.size() == 2) {
    if (!StartsWith(backing_files[1], apex_hash_tree_dir)) {
      return Error() << "Hashtree loop device " << slaves[1].DevPath()
                     << " has unexpected backing file " << backing_files[1];
    }
    apex_data->hashtree_loop_name = slaves[1].DevPath();
  }
  apex_data->loop_name = slaves[0].DevPath();
  apex_data->full_path = backing_files[0];
  return {};
}

// This is not the right place to do this normalization, but proper solution
// will require some refactoring first. :(
// TODO(b/158469911): introduce MountedApexDataBuilder and delegate all
//  building/normalization logic to it.
void NormalizeIfDeleted(MountedApexData* apex_data) {
  std::string_view full_path = apex_data->full_path;
  if (ConsumeSuffix(&full_path, "(deleted)")) {
    apex_data->deleted = true;
    auto it = full_path.rbegin();
    while (it != full_path.rend() && isspace(*it)) {
      it++;
    }
    full_path.remove_suffix(it - full_path.rbegin());
  } else {
    apex_data->deleted = false;
  }
  apex_data->full_path = full_path;
}

Result<MountedApexData> ResolveMountInfo(
    const BlockDevice& block, const std::string& mount_point,
    const std::vector<std::string>& data_dirs,
    const std::string& apex_hash_tree_dir) {
  bool temp_mount = EndsWith(mount_point, ".tmp");
  // Now, see if it is dm-verity or loop mounted
  switch (block.GetType()) {
    case LoopDevice: {
      auto backing_file = block.GetProperty("loop/backing_file");
      if (!backing_file.ok()) {
        return backing_file.error();
      }
      MountedApexData result;
      result.loop_name = block.DevPath();
      result.full_path = *backing_file;
      result.mount_point = mount_point;
      result.is_temp_mount = temp_mount;
      NormalizeIfDeleted(&result);
      return result;
    }
    case DeviceMapperDevice: {
      auto name = block.GetProperty("dm/name");
      if (!name.ok()) {
        return name.error();
      }
      MountedApexData result;
      result.mount_point = mount_point;
      result.device_name = *name;
      result.is_temp_mount = temp_mount;
      auto status =
          PopulateLoopInfo(block, data_dirs, apex_hash_tree_dir, &result);
      if (!status.ok()) {
        return status.error();
      }
      NormalizeIfDeleted(&result);
      return result;
    }
    case UnknownDevice: {
      return Errorf("Can't resolve {}", block.DevPath().string());
    }
  }
}

}  // namespace

// On startup, APEX database is populated from /proc/mounts.

// /apex/<package-id> can be mounted from
// - /dev/block/loopX : loop device
// - /dev/block/dm-X : dm-verity

// In case of loop device, the original APEX file can be tracked
// by /sys/block/loopX/loop/backing_file.

// In case of dm-verity, it is mapped to a loop device.
// This mapped loop device can be traced by
// /sys/block/dm-X/slaves/ directory which contains
// a symlink to /sys/block/loopY, which leads to
// the original APEX file.
// Device name can be retrieved from
// /sys/block/dm-Y/dm/name.

// Need to read /proc/mounts on startup since apexd can start
// at any time (It's a lazy service).
void MountedApexDatabase::PopulateFromMounts(
    const std::vector<std::string>& data_dirs,
    const std::string& apex_hash_tree_dir) REQUIRES(!mounted_apexes_mutex_) {
  LOG(INFO) << "Populating APEX database from mounts...";

  std::ifstream mounts("/proc/mounts");
  std::string line;
  std::lock_guard lock(mounted_apexes_mutex_);
  while (std::getline(mounts, line)) {
    auto [block, mount_point] = ParseMountInfo(line);
    // TODO(b/158469914): distinguish between temp and non-temp mounts
    if (fs::path(mount_point).parent_path() != kApexRoot) {
      continue;
    }
    if (IsActiveMountPoint(mount_point)) {
      continue;
    }

    auto mount_data = ResolveMountInfo(BlockDevice(block), mount_point,
                                       data_dirs, apex_hash_tree_dir);
    if (!mount_data.ok()) {
      LOG(WARNING) << "Can't resolve mount info " << mount_data.error();
      continue;
    }

    auto [package, version] = ParseMountPoint(mount_point);
    mount_data->version = version;
    AddMountedApexLocked(package, *mount_data);

    LOG(INFO) << "Found " << mount_point << " backed by"
              << (mount_data->deleted ? " deleted " : " ") << "file "
              << mount_data->full_path;
  }

  LOG(INFO) << mounted_apexes_.size() << " packages restored.";
}

}  // namespace apex
}  // namespace android
