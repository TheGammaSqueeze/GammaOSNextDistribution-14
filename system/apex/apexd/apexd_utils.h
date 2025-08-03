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

#ifndef ANDROID_APEXD_APEXD_UTILS_H_
#define ANDROID_APEXD_APEXD_UTILS_H_

#include <chrono>
#include <cstdint>
#include <filesystem>
#include <string>
#include <thread>
#include <type_traits>
#include <vector>

#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/result.h>
#include <android-base/scopeguard.h>
#include <android-base/strings.h>
#include <cutils/android_reboot.h>
#include <selinux/android.h>

#include "apex_constants.h"

namespace android {
namespace apex {

template <typename Fn>
android::base::Result<void> WalkDir(const std::string& path, Fn fn) {
  namespace fs = std::filesystem;
  std::error_code ec;
  auto it = fs::directory_iterator(path, ec);
  auto end = fs::directory_iterator();
  while (!ec && it != end) {
    fn(*it);
    it.increment(ec);
  }
  if (ec) {
    return android::base::Error()
           << "Can't open " << path << " for reading : " << ec.message();
  }
  return {};
}

template <typename FilterFn>
android::base::Result<std::vector<std::string>> ReadDir(const std::string& path,
                                                        FilterFn fn) {
  namespace fs = std::filesystem;

  std::vector<std::string> ret;
  auto status = WalkDir(path, [&](const fs::directory_entry& entry) {
    if (fn(entry)) {
      ret.push_back(entry.path());
    }
  });
  if (!status.ok()) {
    return status.error();
  }
  return ret;
}

inline bool IsEmptyDirectory(const std::string& path) {
  auto res = ReadDir(path, [](auto _) { return true; });
  return res.ok() && res->empty();
}

inline android::base::Result<void> CreateDirIfNeeded(const std::string& path,
                                                     mode_t mode) {
  struct stat stat_data;

  if (stat(path.c_str(), &stat_data) != 0) {
    if (errno == ENOENT) {
      if (mkdir(path.c_str(), mode) != 0) {
        return android::base::ErrnoError() << "Could not mkdir " << path;
      }
    } else {
      return android::base::ErrnoError() << "Could not stat " << path;
    }
  } else {
    if (!S_ISDIR(stat_data.st_mode)) {
      return android::base::Error()
             << path << " exists and is not a directory.";
    }
  }

  // Need to manually call chmod because mkdir will create a folder with
  // permissions mode & ~umask.
  if (chmod(path.c_str(), mode) != 0) {
    return android::base::ErrnoError() << "Could not chmod " << path;
  }

  return {};
}

inline android::base::Result<void> DeleteDirContent(const std::string& path) {
  auto files = ReadDir(path, [](auto _) { return true; });
  if (!files.ok()) {
    return android::base::Error()
           << "Failed to delete " << path << " : " << files.error();
  }
  for (const std::string& file : *files) {
    std::error_code ec;
    std::filesystem::remove_all(file, ec);
    if (ec) {
      return android::base::Error()
             << "Failed to delete path " << file << " : " << ec.message();
    }
  }
  return {};
}

inline android::base::Result<void> DeleteDir(const std::string& path) {
  namespace fs = std::filesystem;
  std::error_code ec;
  fs::remove_all(path, ec);
  if (ec) {
    return android::base::Error()
           << "Failed to delete path " << path << " : " << ec.message();
  }
  return {};
}

inline android::base::Result<bool> PathExists(const std::string& path) {
  namespace fs = std::filesystem;

  std::error_code ec;
  if (!fs::exists(fs::path(path), ec)) {
    if (ec) {
      return android::base::Error()
             << "Failed to access " << path << " : " << ec.message();
    } else {
      return false;
    }
  }
  return true;
}

inline void Reboot() {
  LOG(INFO) << "Rebooting device";
  if (android_reboot(ANDROID_RB_RESTART2, 0, nullptr) != 0) {
    LOG(ERROR) << "Failed to reboot device";
  }
  // Wait for reboot to complete as we expect this to be a terminal
  // command. Crash apexd if reboot does not complete even after
  // waiting an arbitrary significant amount of time.
  std::this_thread::sleep_for(std::chrono::seconds(120));
  LOG(FATAL) << "Device did not reboot within 120 seconds";
}

inline android::base::Result<void> WaitForFile(
    const std::string& path, std::chrono::nanoseconds timeout) {
  android::base::Timer t;
  bool has_slept = false;
  while (t.duration() < timeout) {
    struct stat sb;
    if (stat(path.c_str(), &sb) != -1) {
      if (has_slept) {
        LOG(INFO) << "wait for '" << path << "' took " << t;
      }
      return {};
    }
    std::this_thread::sleep_for(5ms);
    has_slept = true;
  }
  return android::base::ErrnoError()
         << "wait for '" << path << "' timed out and took " << t;
}

inline android::base::Result<std::vector<std::string>> GetSubdirs(
    const std::string& path) {
  namespace fs = std::filesystem;
  auto filter_fn = [](const std::filesystem::directory_entry& entry) {
    std::error_code ec;
    bool result = entry.is_directory(ec);
    if (ec) {
      LOG(ERROR) << "Failed to check is_directory : " << ec.message();
      return false;
    }
    return result;
  };
  return ReadDir(path, filter_fn);
}

inline android::base::Result<std::vector<std::string>> GetDeUserDirs() {
  return GetSubdirs(kDeNDataDir);
}

inline android::base::Result<std::vector<std::string>> FindFilesBySuffix(
    const std::string& path, const std::vector<std::string>& suffix_list) {
  auto filter_fn =
      [&suffix_list](const std::filesystem::directory_entry& entry) {
        for (const std::string& suffix : suffix_list) {
          std::error_code ec;
          auto name = entry.path().filename().string();
          if (entry.is_regular_file(ec) &&
              android::base::EndsWith(name, suffix)) {
            return true;  // suffix matches, take.
          }
        }
        return false;
      };
  return ReadDir(path, filter_fn);
}

// Returns first path between |first_dir| and |second_dir| that correspond to a
// existing directory. Returns error if neither |first_dir| nor |second_dir|
// correspond to an existing directory.
inline android::base::Result<std::string> FindFirstExistingDirectory(
    const std::string& first_dir, const std::string& second_dir) {
  struct stat stat_buf;
  if (stat(first_dir.c_str(), &stat_buf) != 0) {
    PLOG(WARNING) << "Failed to stat " << first_dir;
    if (stat(second_dir.c_str(), &stat_buf) != 0) {
      return android::base::ErrnoError() << "Failed to stat " << second_dir;
    }
    if (!S_ISDIR(stat_buf.st_mode)) {
      return android::base::Error() << second_dir << " is not a directory";
    }
    return second_dir;
  }

  if (S_ISDIR(stat_buf.st_mode)) {
    return first_dir;
  }
  LOG(WARNING) << first_dir << " is not a directory";

  if (stat(second_dir.c_str(), &stat_buf) != 0) {
    return android::base::ErrnoError() << "Failed to stat " << second_dir;
  }
  if (!S_ISDIR(stat_buf.st_mode)) {
    return android::base::Error() << second_dir << " is not a directory";
  }
  return second_dir;
}

// Copies all entries under |from| directory to |to| directory, and then them.
// Leaving |from| empty.
inline android::base::Result<void> MoveDir(const std::string& from,
                                           const std::string& to) {
  struct stat stat_buf;
  if (stat(to.c_str(), &stat_buf) != 0) {
    return android::base::ErrnoError() << "Failed to stat " << to;
  }
  if (!S_ISDIR(stat_buf.st_mode)) {
    return android::base::Error() << to << " is not a directory";
  }

  namespace fs = std::filesystem;
  std::error_code ec;
  auto it = fs::directory_iterator(from, ec);
  if (ec) {
    return android::base::Error()
           << "Can't read " << from << " : " << ec.message();
  }

  for (const auto& end = fs::directory_iterator(); it != end;) {
    auto from_path = it->path();
    it.increment(ec);
    if (ec) {
      return android::base::Error()
             << "Can't read " << from << " : " << ec.message();
    }
    auto to_path = to / from_path.filename();
    fs::copy(from_path, to_path, fs::copy_options::recursive, ec);
    if (ec) {
      return android::base::Error() << "Failed to copy " << from_path << " to "
                                    << to_path << " : " << ec.message();
    }
    fs::remove_all(from_path, ec);
    if (ec) {
      return android::base::Error()
             << "Failed to delete " << from_path << " : " << ec.message();
    }
  }
  return {};
}

inline android::base::Result<uintmax_t> GetFileSize(
    const std::string& file_path) {
  std::error_code ec;
  auto value = std::filesystem::file_size(file_path, ec);
  if (ec) {
    return android::base::Error() << "Failed to get file size of " << file_path
                                  << " : " << ec.message();
  }

  return value;
}

inline android::base::Result<void> RestoreconPath(const std::string& path) {
  unsigned int seflags = SELINUX_ANDROID_RESTORECON_RECURSE;
  if (selinux_android_restorecon(path.c_str(), seflags) < 0) {
    return android::base::ErrnoError() << "Failed to restorecon " << path;
  }
  return {};
}

inline android::base::Result<std::string> GetfileconPath(
    const std::string& path) {
  char* ctx;
  if (getfilecon(path.c_str(), &ctx) < 0) {
    return android::base::ErrnoError() << "Failed to getfilecon " << path;
  }
  std::string ret(ctx);
  freecon(ctx);
  return ret;
}

}  // namespace apex
}  // namespace android

#endif  // ANDROID_APEXD_APEXD_UTILS_H_
