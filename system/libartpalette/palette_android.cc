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

#define ATRACE_TAG ATRACE_TAG_DALVIK

#include "palette/palette.h"

#include <errno.h>
#include <sys/resource.h>
#include <sys/time.h>
#include <unistd.h>

#include <filesystem>
#include <mutex>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <cutils/ashmem.h>
#include <cutils/trace.h>
#include <processgroup/processgroup.h>
#include <selinux/selinux.h>
#include <tombstoned/tombstoned.h>
#include <utils/Thread.h>

#include "palette_system.h"

// Conversion map for "nice" values.
//
// We use Android thread priority constants to be consistent with the rest
// of the system.  In some cases adjacent entries may overlap.
//
static const int kNiceValues[art::palette::kNumManagedThreadPriorities] = {
    ANDROID_PRIORITY_LOWEST,  // 1 (MIN_PRIORITY)
    ANDROID_PRIORITY_BACKGROUND + 6,
    ANDROID_PRIORITY_BACKGROUND + 3,
    ANDROID_PRIORITY_BACKGROUND,
    ANDROID_PRIORITY_NORMAL,  // 5 (NORM_PRIORITY)
    ANDROID_PRIORITY_NORMAL - 2,
    ANDROID_PRIORITY_NORMAL - 4,
    ANDROID_PRIORITY_URGENT_DISPLAY + 3,
    ANDROID_PRIORITY_URGENT_DISPLAY + 2,
    ANDROID_PRIORITY_URGENT_DISPLAY  // 10 (MAX_PRIORITY)
};

// Unless explicitly mentioned otherwise, the following methods have been
// introduced in version 1 API, corresponding to SDK level 31.

palette_status_t PaletteSchedSetPriority(int32_t tid, int32_t managed_priority) {
  if (managed_priority < art::palette::kMinManagedThreadPriority ||
      managed_priority > art::palette::kMaxManagedThreadPriority) {
    return PALETTE_STATUS_INVALID_ARGUMENT;
  }
  int new_nice = kNiceValues[managed_priority - art::palette::kMinManagedThreadPriority];
  int curr_nice = getpriority(PRIO_PROCESS, tid);

  if (curr_nice == new_nice) {
    return PALETTE_STATUS_OK;
  }

  if (setpriority(PRIO_PROCESS, tid, new_nice) != 0) {
    return PALETTE_STATUS_CHECK_ERRNO;
  }
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteSchedGetPriority(int32_t tid, /*out*/ int32_t* managed_priority) {
  errno = 0;
  int native_priority = getpriority(PRIO_PROCESS, tid);
  if (native_priority == -1 && errno != 0) {
    *managed_priority = art::palette::kNormalManagedThreadPriority;
    return PALETTE_STATUS_CHECK_ERRNO;
  }

  for (int p = art::palette::kMinManagedThreadPriority;
       p <= art::palette::kMaxManagedThreadPriority; p = p + 1) {
    int index = p - art::palette::kMinManagedThreadPriority;
    if (native_priority >= kNiceValues[index]) {
      *managed_priority = p;
      return PALETTE_STATUS_OK;
    }
  }
  *managed_priority = art::palette::kMaxManagedThreadPriority;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteWriteCrashThreadStacks(/*in*/ const char* stacks, size_t stacks_len) {
  android::base::unique_fd tombstone_fd;
  android::base::unique_fd output_fd;

  if (!tombstoned_connect(getpid(), &tombstone_fd, &output_fd, kDebuggerdJavaBacktrace)) {
    // Failure here could be due to file descriptor resource exhaustion
    // so write the stack trace message to the log in case it helps
    // debug that.
    LOG(INFO) << std::string_view(stacks, stacks_len);
    // tombstoned_connect() logs failure reason.
    return PALETTE_STATUS_FAILED_CHECK_LOG;
  }

  palette_status_t status = PALETTE_STATUS_OK;
  if (!android::base::WriteFully(output_fd, stacks, stacks_len)) {
    PLOG(ERROR) << "Failed to write tombstoned output";
    TEMP_FAILURE_RETRY(ftruncate(output_fd, 0));
    status = PALETTE_STATUS_FAILED_CHECK_LOG;
  }

  if (TEMP_FAILURE_RETRY(fdatasync(output_fd)) == -1 && errno != EINVAL) {
    // Ignore EINVAL so we don't report failure if we just tried to flush a pipe
    // or socket.
    if (status == PALETTE_STATUS_OK) {
      PLOG(ERROR) << "Failed to fsync tombstoned output";
      status = PALETTE_STATUS_FAILED_CHECK_LOG;
    }
    TEMP_FAILURE_RETRY(ftruncate(output_fd, 0));
    TEMP_FAILURE_RETRY(fdatasync(output_fd));
  }

  if (close(output_fd.release()) == -1 && errno != EINTR) {
    if (status == PALETTE_STATUS_OK) {
      PLOG(ERROR) << "Failed to close tombstoned output";
      status = PALETTE_STATUS_FAILED_CHECK_LOG;
    }
  }

  if (!tombstoned_notify_completion(tombstone_fd)) {
    // tombstoned_notify_completion() logs failure.
    status = PALETTE_STATUS_FAILED_CHECK_LOG;
  }

  return status;
}

palette_status_t PaletteTraceEnabled(/*out*/ bool* enabled) {
  *enabled = (ATRACE_ENABLED() != 0) ? true : false;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceBegin(const char* name) {
  ATRACE_BEGIN(name);
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceEnd() {
  ATRACE_END();
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceIntegerValue(const char* name, int32_t value) {
  ATRACE_INT(name, value);
  return PALETTE_STATUS_OK;
}

// Flag whether to use legacy ashmem or current (b/139855428)
static std::atomic_bool g_assume_legacy_ashmemd(false);

palette_status_t PaletteAshmemCreateRegion(const char* name, size_t size, int* fd) {
  if (g_assume_legacy_ashmemd.load(std::memory_order_acquire) == false) {
    // Current platform behaviour which open ashmem fd in process (b/139855428)
    *fd = ashmem_create_region(name, size);
    if (*fd >= 0) {
      return PALETTE_STATUS_OK;
    }
  }

  // Try legacy behavior just required for ART build bots which may be running tests on older
  // platform builds.

  // We implement our own ashmem creation, as the libcutils implementation does
  // a binder call, and our only use of ashmem in ART is for zygote, which
  // cannot communicate to binder.
  *fd = TEMP_FAILURE_RETRY(open("/dev/ashmem", O_RDWR | O_CLOEXEC));
  if (*fd == -1) {
    return PALETTE_STATUS_CHECK_ERRNO;
  }

  if (TEMP_FAILURE_RETRY(ioctl(*fd, ASHMEM_SET_SIZE, size)) < 0) {
    goto error;
  }

  if (name != nullptr) {
    char buf[ASHMEM_NAME_LEN] = {0};
    strlcpy(buf, name, sizeof(buf));
    if (TEMP_FAILURE_RETRY(ioctl(*fd, ASHMEM_SET_NAME, buf)) < 0) {
      goto error;
    }
  }

  g_assume_legacy_ashmemd.store(true, std::memory_order_release);
  return PALETTE_STATUS_OK;

error:
  // Save errno before closing.
  int save_errno = errno;
  close(*fd);
  errno = save_errno;
  return PALETTE_STATUS_CHECK_ERRNO;
}

palette_status_t PaletteAshmemSetProtRegion(int fd, int prot) {
  if (!g_assume_legacy_ashmemd.load(std::memory_order_acquire)) {
    if (ashmem_set_prot_region(fd, prot) < 0) {
      return PALETTE_STATUS_CHECK_ERRNO;
    }
  } else if (TEMP_FAILURE_RETRY(ioctl(fd, ASHMEM_SET_PROT_MASK, prot)) < 0) {
    // Legacy behavior just required for ART build bots which may be running tests on older
    // platform builds.
    return PALETTE_STATUS_CHECK_ERRNO;
  }
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteCreateOdrefreshStagingDirectory(const char** staging_dir) {
  static constexpr const char* kStagingDirectory = "/data/misc/apexdata/com.android.art/staging";

  std::error_code ec;
  if (std::filesystem::exists(kStagingDirectory, ec)) {
    if (!std::filesystem::remove_all(kStagingDirectory, ec)) {
      LOG(ERROR) << ec.message()
                 << "Could not remove existing staging directory: " << kStagingDirectory;
      DCHECK_EQ(ec.value(), errno);
      return PALETTE_STATUS_CHECK_ERRNO;
    }
  }

  if (mkdir(kStagingDirectory, S_IRWXU) != 0) {
    PLOG(ERROR) << "Could not set permissions on staging directory: " << kStagingDirectory;
    return PALETTE_STATUS_CHECK_ERRNO;
  }

  if (setfilecon(kStagingDirectory, "u:object_r:apex_art_staging_data_file:s0") != 0) {
    PLOG(ERROR) << "Could not set label on staging directory: " << kStagingDirectory;
    return PALETTE_STATUS_CHECK_ERRNO;
  }

  *staging_dir = kStagingDirectory;
  return PALETTE_STATUS_OK;
}

// Introduced in version 3 API, corresponding to SDK level 34.
palette_status_t PaletteSetTaskProfiles(int32_t tid, const char* const profiles[],
                                        size_t profiles_len) {
  std::vector<std::string> p;
  p.reserve(profiles_len);
  for (int i = 0; i < profiles_len; ++i) {
    p.push_back(profiles[i]);
  }
  return SetTaskProfiles(tid, p, false) ? PALETTE_STATUS_OK : PALETTE_STATUS_FAILED_CHECK_LOG;
}
