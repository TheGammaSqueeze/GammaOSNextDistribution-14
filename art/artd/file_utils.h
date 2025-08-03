/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_ARTD_FILE_UTILS_H_
#define ART_ARTD_FILE_UTILS_H_

#include <sys/types.h>

#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include "aidl/com/android/server/art/FsPermission.h"
#include "android-base/result.h"
#include "base/os.h"

namespace art {
namespace artd {

// A class that creates a new file that will eventually be committed to the given path. The new file
// is created at a temporary location. It will not overwrite the file at the given path until
// `CommitOrAbandon` has been called and will be automatically cleaned up on object destruction
// unless `CommitOrAbandon` has been called.
// The new file is opened without O_CLOEXEC so that it can be passed to subprocesses.
class NewFile {
 public:
  // Creates a new file at the given path with the given permission.
  static android::base::Result<std::unique_ptr<NewFile>> Create(
      const std::string& path, const aidl::com::android::server::art::FsPermission& fs_permission);

  NewFile(const NewFile&) = delete;
  NewFile& operator=(const NewFile&) = delete;
  NewFile(NewFile&& other) noexcept
      : fd_(std::exchange(other.fd_, -1)),
        final_path_(std::move(other.final_path_)),
        temp_path_(std::move(other.temp_path_)),
        temp_id_(std::move(other.temp_id_)),
        fs_permission_(other.fs_permission_) {}

  // Deletes the file if it is not committed.
  virtual ~NewFile();

  int Fd() const { return fd_; }

  // The path that the file will eventually be committed to.
  const std::string& FinalPath() const { return final_path_; }

  // The path to the new file.
  const std::string& TempPath() const { return temp_path_; }

  // The unique ID of the new file. Can be used by `BuildTempPath` for reconstructing the path to
  // the file.
  const std::string& TempId() const { return temp_id_; }

  // Closes the new file, keeps it, moves the file to the final path, and overwrites any existing
  // file at that path, or abandons the file on failure. The fd will be invalid after this function
  // is called.
  android::base::Result<void> CommitOrAbandon();

  // Closes the new file and keeps it at the temporary location. The file will not be automatically
  // cleaned up on object destruction. The file can be found at `TempPath()` (i.e.,
  // `BuildTempPath(FinalPath(), TempId())`). The fd will be invalid after this function is called.
  virtual android::base::Result<void> Keep();

  // Unlinks and closes the new file if it is not committed. The fd will be invalid after this
  // function is called.
  void Cleanup();

  // Commits all new files, replacing old files, and removes given files in addition. Or abandons
  // new files and restores old files at best effort if any error occurs. The fds will be invalid
  // after this function is called.
  //
  // Note: This function is NOT thread-safe. It is intended to be used in single-threaded code or in
  // cases where some race condition is acceptable.
  //
  // Usage:
  //
  // Commit `file_1` and `file_2`, and remove the file at "path_3":
  //   CommitAllOrAbandon({file_1, file_2}, {"path_3"});
  static android::base::Result<void> CommitAllOrAbandon(
      const std::vector<NewFile*>& files_to_commit,
      const std::vector<std::string_view>& files_to_remove = {});

  // Returns the path to a temporary file. See `Keep`.
  static std::string BuildTempPath(std::string_view final_path, const std::string& id);

 private:
  NewFile(const std::string& path,
          const aidl::com::android::server::art::FsPermission& fs_permission)
      : final_path_(path), fs_permission_(fs_permission) {}

  android::base::Result<void> Init();

  // Unlinks the new file. The fd will still be valid after this function is called.
  void Unlink();

  int fd_ = -1;
  std::string final_path_;
  std::string temp_path_;
  std::string temp_id_;
  aidl::com::android::server::art::FsPermission fs_permission_;
  bool committed_ = false;
};

// Opens a file for reading.
android::base::Result<std::unique_ptr<File>> OpenFileForReading(const std::string& path);

// Converts FsPermission to Linux access mode for a file.
mode_t FileFsPermissionToMode(const aidl::com::android::server::art::FsPermission& fs_permission);

// Converts FsPermission to Linux access mode for a directory.
mode_t DirFsPermissionToMode(const aidl::com::android::server::art::FsPermission& fs_permission);

// Changes the owner based on FsPermission.
android::base::Result<void> Chown(
    const std::string& path, const aidl::com::android::server::art::FsPermission& fs_permission);

}  // namespace artd
}  // namespace art

#endif  // ART_ARTD_FILE_UTILS_H_
