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

#include "apexd_loop.h"

#include <ApexProperties.sysprop.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <dirent.h>
#include <fcntl.h>
#include <libdm/dm.h>
#include <linux/fs.h>
#include <linux/loop.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <utils/Trace.h>

#include <array>
#include <filesystem>
#include <mutex>
#include <string_view>

#include "apexd_utils.h"

using android::base::Basename;
using android::base::ErrnoError;
using android::base::Error;
using android::base::GetBoolProperty;
using android::base::ParseUint;
using android::base::ReadFileToString;
using android::base::Result;
using android::base::StartsWith;
using android::base::StringPrintf;
using android::base::unique_fd;
using android::dm::DeviceMapper;

namespace android {
namespace apex {
namespace loop {

static constexpr const char* kApexLoopIdPrefix = "apex:";

// 128 kB read-ahead, which we currently use for /system as well
static constexpr const char* kReadAheadKb = "128";

void LoopbackDeviceUniqueFd::MaybeCloseBad() {
  if (device_fd.get() != -1) {
    // Disassociate any files.
    if (ioctl(device_fd.get(), LOOP_CLR_FD) == -1) {
      PLOG(ERROR) << "Unable to clear fd for loopback device";
    }
  }
}

Result<void> ConfigureScheduler(const std::string& device_path) {
  ATRACE_NAME("ConfigureScheduler");
  if (!StartsWith(device_path, "/dev/")) {
    return Error() << "Invalid argument " << device_path;
  }

  const std::string device_name = Basename(device_path);

  const std::string sysfs_path =
      StringPrintf("/sys/block/%s/queue/scheduler", device_name.c_str());
  unique_fd sysfs_fd(open(sysfs_path.c_str(), O_RDWR | O_CLOEXEC));
  if (sysfs_fd.get() == -1) {
    return ErrnoError() << "Failed to open " << sysfs_path;
  }

  // Kernels before v4.1 only support 'noop'. Kernels [v4.1, v5.0) support
  // 'noop' and 'none'. Kernels v5.0 and later only support 'none'.
  static constexpr const std::array<std::string_view, 2> kNoScheduler = {
      "none", "noop"};

  int ret = 0;
  std::string cur_sched_str;
  if (!ReadFileToString(sysfs_path, &cur_sched_str)) {
    return ErrnoError() << "Failed to read " << sysfs_path;
  }
  cur_sched_str = android::base::Trim(cur_sched_str);
  if (std::count(kNoScheduler.begin(), kNoScheduler.end(), cur_sched_str)) {
    return {};
  }

  for (const std::string_view& scheduler : kNoScheduler) {
    ret = write(sysfs_fd.get(), scheduler.data(), scheduler.size());
    if (ret > 0) {
      break;
    }
  }

  if (ret <= 0) {
    return ErrnoError() << "Failed to write to " << sysfs_path;
  }

  return {};
}

// Return the parent device of a partition. Converts e.g. "sda26" into "sda".
static Result<std::string> PartitionParent(const std::string& blockdev) {
  if (blockdev.find('/') != std::string::npos) {
    return Error() << "Invalid argument " << blockdev;
  }
  std::error_code ec;
  for (const auto& entry :
       std::filesystem::directory_iterator("/sys/class/block", ec)) {
    const std::string path = entry.path().string();
    if (std::filesystem::exists(
            StringPrintf("%s/%s", path.c_str(), blockdev.c_str()))) {
      return Basename(path);
    }
  }
  return blockdev;
}

// Convert a major:minor pair into a block device name.
static std::string BlockdevName(dev_t dev) {
  std::error_code ec;
  for (const auto& entry :
       std::filesystem::directory_iterator("/dev/block", ec)) {
    struct stat statbuf;
    if (stat(entry.path().string().c_str(), &statbuf) < 0) {
      continue;
    }
    if (dev == statbuf.st_rdev) {
      return Basename(entry.path().string());
    }
  }
  return {};
}

// For file `file_path`, retrieve the block device backing the filesystem on
// which the file exists and return the queue depth of the block device. The
// loop in this function may e.g. traverse the following hierarchy:
// /dev/block/dm-9 (system-verity; dm-verity)
// -> /dev/block/dm-1 (system_b; dm-linear)
// -> /dev/sda26
static Result<uint32_t> BlockDeviceQueueDepth(const std::string& file_path) {
  struct stat statbuf;
  int res = stat(file_path.c_str(), &statbuf);
  if (res < 0) {
    return ErrnoErrorf("stat({})", file_path.c_str());
  }
  std::string blockdev = "/dev/block/" + BlockdevName(statbuf.st_dev);
  LOG(VERBOSE) << file_path << " -> " << blockdev;
  if (blockdev.empty()) {
    return Errorf("Failed to convert {}:{} (path {})", major(statbuf.st_dev),
                  minor(statbuf.st_dev), file_path.c_str());
  }
  auto& dm = DeviceMapper::Instance();
  for (;;) {
    std::optional<std::string> child = dm.GetParentBlockDeviceByPath(blockdev);
    if (!child) {
      break;
    }
    LOG(VERBOSE) << blockdev << " -> " << *child;
    blockdev = *child;
  }
  std::optional<std::string> maybe_blockdev =
      android::dm::ExtractBlockDeviceName(blockdev);
  if (!maybe_blockdev) {
    return Error() << "Failed to remove /dev/block/ prefix from " << blockdev;
  }
  Result<std::string> maybe_parent = PartitionParent(*maybe_blockdev);
  if (!maybe_parent.ok()) {
    return Error() << "Failed to determine parent of " << *maybe_blockdev;
  }
  blockdev = *maybe_parent;
  LOG(VERBOSE) << "Partition parent: " << blockdev;
  const std::string nr_tags_path =
      StringPrintf("/sys/class/block/%s/mq/0/nr_tags", blockdev.c_str());
  std::string nr_tags;
  if (!ReadFileToString(nr_tags_path, &nr_tags)) {
    return ErrnoError() << "Failed to read " << nr_tags_path;
  }
  nr_tags = android::base::Trim(nr_tags);
  LOG(VERBOSE) << file_path << " is backed by /dev/" << blockdev
               << " and that block device supports queue depth " << nr_tags;
  return strtol(nr_tags.c_str(), NULL, 0);
}

// Set 'nr_requests' of `loop_device_path` equal to the queue depth of
// the block device backing `file_path`.
Result<void> ConfigureQueueDepth(const std::string& loop_device_path,
                                 const std::string& file_path) {
  ATRACE_NAME("ConfigureQueueDepth");
  if (!StartsWith(loop_device_path, "/dev/")) {
    return Error() << "Invalid argument " << loop_device_path;
  }

  const std::string loop_device_name = Basename(loop_device_path);

  const std::string sysfs_path =
      StringPrintf("/sys/block/%s/queue/nr_requests", loop_device_name.c_str());
  std::string cur_nr_requests_str;
  if (!ReadFileToString(sysfs_path, &cur_nr_requests_str)) {
    return ErrnoError() << "Failed to read " << sysfs_path;
  }
  cur_nr_requests_str = android::base::Trim(cur_nr_requests_str);
  uint32_t cur_nr_requests = 0;
  if (!ParseUint(cur_nr_requests_str.c_str(), &cur_nr_requests)) {
    return Error() << "Failed to parse " << cur_nr_requests_str;
  }

  unique_fd sysfs_fd(open(sysfs_path.c_str(), O_RDWR | O_CLOEXEC));
  if (sysfs_fd.get() == -1) {
    return ErrnoErrorf("Failed to open {}", sysfs_path);
  }

  const auto qd = BlockDeviceQueueDepth(file_path);
  if (!qd.ok()) {
    return qd.error();
  }
  if (*qd == cur_nr_requests) {
    return {};
  }
  // Only report write failures if reducing the queue depth. Attempts to
  // increase the queue depth are rejected by the kernel if no I/O scheduler
  // is associated with the request queue.
  if (!WriteStringToFd(StringPrintf("%u", *qd), sysfs_fd) &&
      *qd < cur_nr_requests) {
    return ErrnoErrorf("Failed to write {} to {}", *qd, sysfs_path);
  }
  return {};
}

Result<void> ConfigureReadAhead(const std::string& device_path) {
  ATRACE_NAME("ConfigureReadAhead");
  CHECK(StartsWith(device_path, "/dev/"));
  std::string device_name = Basename(device_path);

  std::string sysfs_device =
      StringPrintf("/sys/block/%s/queue/read_ahead_kb", device_name.c_str());
  unique_fd sysfs_fd(open(sysfs_device.c_str(), O_RDWR | O_CLOEXEC));
  if (sysfs_fd.get() == -1) {
    return ErrnoError() << "Failed to open " << sysfs_device;
  }

  int ret = TEMP_FAILURE_RETRY(
      write(sysfs_fd.get(), kReadAheadKb, strlen(kReadAheadKb) + 1));
  if (ret < 0) {
    return ErrnoError() << "Failed to write to " << sysfs_device;
  }

  return {};
}

Result<void> PreAllocateLoopDevices(size_t num) {
  Result<void> loop_ready = WaitForFile("/dev/loop-control", 20s);
  if (!loop_ready.ok()) {
    return loop_ready;
  }
  unique_fd ctl_fd(
      TEMP_FAILURE_RETRY(open("/dev/loop-control", O_RDWR | O_CLOEXEC)));
  if (ctl_fd.get() == -1) {
    return ErrnoError() << "Failed to open loop-control";
  }

  int new_allocations = 0;  // for logging purpose

  // Assumption: loop device ID [0..num) is valid.
  // This is because pre-allocation happens during bootstrap.
  // Anyway Kernel pre-allocated loop devices
  // as many as CONFIG_BLK_DEV_LOOP_MIN_COUNT,
  // Within the amount of kernel-pre-allocation,
  // LOOP_CTL_ADD will fail with EEXIST
  for (size_t id = 0ul, cnt = 0; cnt < num; ++id) {
    int ret = ioctl(ctl_fd.get(), LOOP_CTL_ADD, id);
    if (ret > 0) {
      new_allocations++;
      cnt++;
    } else if (errno == EEXIST) {
      // When LOOP_CTL_ADD failed with EEXIST, it can check
      // whether it is already in use.
      // Otherwise, the loop devices pre-allocated by the kernel can be used.
      std::string loop_device = StringPrintf("/sys/block/loop%zu/loop", id);
      if (access(loop_device.c_str(), F_OK) == 0) {
        LOG(WARNING) << "Loop device " << id << " already in use";
      } else {
        cnt++;
      }
    } else {
      return ErrnoError() << "Failed LOOP_CTL_ADD id = " << id;
    }
  }

  // Don't wait until the dev nodes are actually created, which
  // will delay the boot. By simply returing here, the creation of the dev
  // nodes will be done in parallel with other boot processes, and we
  // just optimistally hope that they are all created when we actually
  // access them for activating APEXes. If the dev nodes are not ready
  // even then, we wait 50ms and warning message will be printed (see below
  // CreateLoopDevice()).
  LOG(INFO) << "Found " << (num - new_allocations)
            << " idle loopback devices that were "
            << "pre-allocated by kernel. Allocated " << new_allocations
            << " more.";
  return {};
}

Result<void> ConfigureLoopDevice(const int device_fd, const std::string& target,
                                 const uint32_t image_offset,
                                 const size_t image_size) {
  static bool use_loop_configure;
  static std::once_flag once_flag;
  std::call_once(once_flag, [&]() {
    // LOOP_CONFIGURE is a new ioctl in Linux 5.8 (and backported in Android
    // common) that allows atomically configuring a loop device. It is a lot
    // faster than the traditional LOOP_SET_FD/LOOP_SET_STATUS64 combo, but
    // it may not be available on updating devices, so try once before
    // deciding.
    struct loop_config config;
    memset(&config, 0, sizeof(config));
    config.fd = -1;
    if (ioctl(device_fd, LOOP_CONFIGURE, &config) == -1 && errno == EBADF) {
      // If the IOCTL exists, it will fail with EBADF for the -1 fd
      use_loop_configure = true;
    }
  });

  /*
   * Using O_DIRECT will tell the kernel that we want to use Direct I/O
   * on the underlying file, which we want to do to avoid double caching.
   * Note that Direct I/O won't be enabled immediately, because the block
   * size of the underlying block device may not match the default loop
   * device block size (512); when we call LOOP_SET_BLOCK_SIZE below, the
   * kernel driver will automatically enable Direct I/O when it sees that
   * condition is now met.
   */
  bool use_buffered_io = false;
  bool enable_odirect = false;
  struct utsname uts;
  unsigned int major, minor;
  if (uname(&uts) == 0 && sscanf(uts.release, "%u.%u", &major, &minor) == 2) {
    if(major > 4) enable_odirect = true;
    if(major == 4 && minor > 19) enable_odirect = true;
  }
  unique_fd target_fd;
  if (enable_odirect) {
    target_fd = unique_fd(open(target.c_str(), O_RDONLY | O_CLOEXEC | O_DIRECT));
  } else {
    target_fd = unique_fd(open(target.c_str(), O_RDONLY | O_CLOEXEC));
  }
  //unique_fd target_fd(open(target.c_str(), O_RDONLY | O_CLOEXEC));
  if (target_fd.get() == -1) {
    struct statfs stbuf;
    int saved_errno = errno;
    // let's give another try with buffered I/O for EROFS and squashfs
    if (statfs(target.c_str(), &stbuf) != 0 ||
        (stbuf.f_type != EROFS_SUPER_MAGIC_V1 &&
         stbuf.f_type != SQUASHFS_MAGIC &&
         stbuf.f_type != OVERLAYFS_SUPER_MAGIC)) {
      return Error(saved_errno) << "Failed to open " << target;
    }
    LOG(WARNING) << "Fallback to buffered I/O for " << target;
    use_buffered_io = true;
    target_fd.reset(open(target.c_str(), O_RDONLY | O_CLOEXEC));
    if (target_fd.get() == -1) {
      return ErrnoError() << "Failed to open " << target;
    }
  }

  struct loop_info64 li;
  memset(&li, 0, sizeof(li));
  strlcpy((char*)li.lo_crypt_name, kApexLoopIdPrefix, LO_NAME_SIZE);
  li.lo_offset = image_offset;
  li.lo_sizelimit = image_size;
  // Automatically free loop device on last close.
  li.lo_flags |= LO_FLAGS_AUTOCLEAR;

  if (use_loop_configure) {
    struct loop_config config;
    memset(&config, 0, sizeof(config));
    config.fd = target_fd.get();
    config.info = li;
    config.block_size = 4096;
    if (!use_buffered_io) {
        li.lo_flags |= LO_FLAGS_DIRECT_IO;
    }

    if (ioctl(device_fd, LOOP_CONFIGURE, &config) == -1) {
      return ErrnoError() << "Failed to LOOP_CONFIGURE";
    }

    return {};
  } else {
    if (ioctl(device_fd, LOOP_SET_FD, target_fd.get()) == -1) {
      return ErrnoError() << "Failed to LOOP_SET_FD";
    }

    if (ioctl(device_fd, LOOP_SET_STATUS64, &li) == -1) {
      return ErrnoError() << "Failed to LOOP_SET_STATUS64";
    }

    if (ioctl(device_fd, BLKFLSBUF, 0) == -1) {
      // This works around a kernel bug where the following happens.
      // 1) The device runs with a value of loop.max_part > 0
      // 2) As part of LOOP_SET_FD above, we do a partition scan, which loads
      //    the first 2 pages of the underlying file into the buffer cache
      // 3) When we then change the offset with LOOP_SET_STATUS64, those pages
      //    are not invalidated from the cache.
      // 4) When we try to mount an ext4 filesystem on the loop device, the ext4
      //    code will try to find a superblock by reading 4k at offset 0; but,
      //    because we still have the old pages at offset 0 lying in the cache,
      //    those pages will be returned directly. However, those pages contain
      //    the data at offset 0 in the underlying file, not at the offset that
      //    we configured
      // 5) the ext4 driver fails to find a superblock in the (wrong) data, and
      //    fails to mount the filesystem.
      //
      // To work around this, explicitly flush the block device, which will
      // flush the buffer cache and make sure we actually read the data at the
      // correct offset.
      return ErrnoError() << "Failed to flush buffers on the loop device";
    }

    // Direct-IO requires the loop device to have the same block size as the
    // underlying filesystem.
    if (ioctl(device_fd, LOOP_SET_BLOCK_SIZE, 4096) == -1) {
      PLOG(WARNING) << "Failed to LOOP_SET_BLOCK_SIZE";
    }
  }
  return {};
}

Result<LoopbackDeviceUniqueFd> WaitForDevice(int num) {
  const std::vector<std::string> candidate_devices = {
      StringPrintf("/dev/block/loop%d", num),
      StringPrintf("/dev/loop%d", num),
  };

  // apexd-bootstrap runs in parallel with ueventd to optimize boot time. In
  // rare cases apexd would try attempt to mount an apex before ueventd created
  // a loop device for it. To work around this we keep polling for loop device
  // to be created until ueventd's cold boot sequence is done.
  bool cold_boot_done = GetBoolProperty("ro.cold_boot_done", false);

  // Even though the kernel has created the loop device, we still depend on
  // ueventd to run to actually create the device node in userspace. To solve
  // this properly we should listen on the netlink socket for uevents, or use
  // inotify. For now, this will have to do.
  size_t attempts =
      android::sysprop::ApexProperties::loop_wait_attempts().value_or(3u);
  for (size_t i = 0; i != attempts; ++i) {
    if (!cold_boot_done) {
      cold_boot_done = GetBoolProperty("ro.cold_boot_done", false);
    }
    for (const auto& device : candidate_devices) {
      unique_fd sysfs_fd(open(device.c_str(), O_RDWR | O_CLOEXEC));
      if (sysfs_fd.get() != -1) {
        return LoopbackDeviceUniqueFd(std::move(sysfs_fd), device);
      }
    }
    PLOG(WARNING) << "Loopback device " << num << " not ready. Waiting 50ms...";
    usleep(50000);
    if (!cold_boot_done) {
      // ueventd hasn't finished cold boot yet, keep trying.
      i = 0;
    }
  }

  return Error() << "Failed to open loopback device " << num;
}

Result<LoopbackDeviceUniqueFd> CreateLoopDevice(const std::string& target,
                                                uint32_t image_offset,
                                                size_t image_size) {
  ATRACE_NAME("CreateLoopDevice");

  unique_fd ctl_fd(open("/dev/loop-control", O_RDWR | O_CLOEXEC));
  if (ctl_fd.get() == -1) {
    return ErrnoError() << "Failed to open loop-control";
  }

  static std::mutex mtx;
  std::lock_guard lock(mtx);
  int num = ioctl(ctl_fd.get(), LOOP_CTL_GET_FREE);
  if (num == -1) {
    return ErrnoError() << "Failed LOOP_CTL_GET_FREE";
  }

  Result<LoopbackDeviceUniqueFd> loop_device = WaitForDevice(num);
  if (!loop_device.ok()) {
    return loop_device.error();
  }
  CHECK_NE(loop_device->device_fd.get(), -1);

  Result<void> configure_status = ConfigureLoopDevice(
      loop_device->device_fd.get(), target, image_offset, image_size);
  if (!configure_status.ok() && configure_status.error().code() == EBUSY) {
    // EBUSY means that loop device was bound to a different process. We need to call
    // CloseGood() here to ensure that when destroying LoopbackDeviceUniqueFd we
    // don't call LOOP_CLR_FD ioctl on this loop device, essentially clearing the
    // loop device while other process is using it.
    loop_device->CloseGood();
    return configure_status.error();
  }

  return loop_device;
}

Result<LoopbackDeviceUniqueFd> CreateAndConfigureLoopDevice(
    const std::string& target, uint32_t image_offset, size_t image_size) {
  ATRACE_NAME("CreateAndConfigureLoopDevice");
  // Do minimal amount of work while holding a mutex. We need it because
  // acquiring + configuring a loop device is not atomic. Ideally we should
  // pre-acquire all the loop devices in advance, so that when we run APEX
  // activation in-parallel, we can do it without holding any lock.
  // Unfortunately, this will require some refactoring of how we manage loop
  // devices, and probably some new loop-control ioctls, so for the time being
  // we just limit the scope that requires locking.
  android::base::Timer timer;
  Result<LoopbackDeviceUniqueFd> loop_device;
  while (timer.duration() < 1s) {
    loop_device = CreateLoopDevice(target, image_offset, image_size);
    if (loop_device.ok()) {
      break;
    }
    std::this_thread::sleep_for(5ms);
  }

  if (!loop_device.ok()) {
    return loop_device.error();
  }

  Result<void> sched_status = ConfigureScheduler(loop_device->name);
  if (!sched_status.ok()) {
    LOG(WARNING) << "Configuring I/O scheduler failed: "
                 << sched_status.error();
  }

  Result<void> qd_status = ConfigureQueueDepth(loop_device->name, target);
  if (!qd_status.ok()) {
    LOG(WARNING) << qd_status.error();
  }

  Result<void> read_ahead_status = ConfigureReadAhead(loop_device->name);
  if (!read_ahead_status.ok()) {
    return read_ahead_status.error();
  }

  return loop_device;
}

void DestroyLoopDevice(const std::string& path, const DestroyLoopFn& extra) {
  unique_fd fd(open(path.c_str(), O_RDWR | O_CLOEXEC));
  if (fd.get() == -1) {
    if (errno != ENOENT) {
      PLOG(WARNING) << "Failed to open " << path;
    }
    return;
  }

  struct loop_info64 li;
  if (ioctl(fd.get(), LOOP_GET_STATUS64, &li) < 0) {
    if (errno != ENXIO) {
      PLOG(WARNING) << "Failed to LOOP_GET_STATUS64 " << path;
    }
    return;
  }

  auto id = std::string((char*)li.lo_crypt_name);
  if (StartsWith(id, kApexLoopIdPrefix)) {
    extra(path, id);

    if (ioctl(fd.get(), LOOP_CLR_FD, 0) < 0) {
      PLOG(WARNING) << "Failed to LOOP_CLR_FD " << path;
    }
  }
}

}  // namespace loop
}  // namespace apex
}  // namespace android
