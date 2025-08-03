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

#include "file_utils.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <filesystem>
#include <memory>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>

#include "aidl/com/android/server/art/FsPermission.h"
#include "android-base/errors.h"
#include "android-base/logging.h"
#include "android-base/result.h"
#include "android-base/scopeguard.h"
#include "base/macros.h"
#include "base/os.h"
#include "base/unix_file/fd_file.h"

namespace art {
namespace artd {

namespace {

using ::aidl::com::android::server::art::FsPermission;
using ::android::base::make_scope_guard;
using ::android::base::Result;

void UnlinkIfExists(const std::string& path) {
  std::error_code ec;
  std::filesystem::remove(path, ec);
  if (ec) {
    LOG(WARNING) << ART_FORMAT("Failed to remove file '{}': {}", path, ec.message());
  }
}

}  // namespace

Result<std::unique_ptr<NewFile>> NewFile::Create(const std::string& path,
                                                 const FsPermission& fs_permission) {
  std::unique_ptr<NewFile> output_file(new NewFile(path, fs_permission));
  OR_RETURN(output_file->Init());
  return output_file;
}

NewFile::~NewFile() { Cleanup(); }

Result<void> NewFile::Keep() {
  if (close(std::exchange(fd_, -1)) != 0) {
    return ErrnoErrorf("Failed to close file '{}'", temp_path_);
  }
  return {};
}

Result<void> NewFile::CommitOrAbandon() {
  auto cleanup = make_scope_guard([this] { Unlink(); });
  OR_RETURN(Keep());
  std::error_code ec;
  std::filesystem::rename(temp_path_, final_path_, ec);
  if (ec) {
    // If this fails because the temp file doesn't exist, it could be that the file is deleted by
    // `Artd::cleanup` if that method is run simultaneously. At the time of writing, this should
    // never happen because `Artd::cleanup` is only called at the end of the backgrond dexopt job.
    return Errorf(
        "Failed to move new file '{}' to path '{}': {}", temp_path_, final_path_, ec.message());
  }
  cleanup.Disable();
  committed_ = true;
  return {};
}

void NewFile::Cleanup() {
  if (fd_ >= 0) {
    Unlink();
    if (close(std::exchange(fd_, -1)) != 0) {
      // Nothing we can do. If the file is already unlinked, it will go away when the process exits.
      PLOG(WARNING) << "Failed to close file '" << temp_path_ << "'";
    }
  }
}

Result<void> NewFile::Init() {
  mode_t mode = FileFsPermissionToMode(fs_permission_);
  // "<path_>.XXXXXX.tmp".
  temp_path_ = BuildTempPath(final_path_, "XXXXXX");
  fd_ = mkstemps(temp_path_.data(), /*suffixlen=*/4);
  if (fd_ < 0) {
    return ErrnoErrorf("Failed to create temp file for '{}'", final_path_);
  }
  temp_id_ = temp_path_.substr(/*pos=*/final_path_.length() + 1, /*count=*/6);
  if (fchmod(fd_, mode) != 0) {
    return ErrnoErrorf("Failed to chmod file '{}'", temp_path_);
  }
  OR_RETURN(Chown(temp_path_, fs_permission_));
  return {};
}

void NewFile::Unlink() {
  // This should never fail. We were able to create the file, so we should be able to remove it.
  UnlinkIfExists(temp_path_);
}

Result<void> NewFile::CommitAllOrAbandon(const std::vector<NewFile*>& files_to_commit,
                                         const std::vector<std::string_view>& files_to_remove) {
  std::vector<std::pair<std::string_view, std::string>> moved_files;

  auto cleanup = make_scope_guard([&]() {
    // Clean up new files.
    for (NewFile* new_file : files_to_commit) {
      if (new_file->committed_) {
        UnlinkIfExists(new_file->FinalPath());
      } else {
        new_file->Cleanup();
      }
    }

    // Move old files back.
    for (const auto& [original_path, temp_path] : moved_files) {
      std::error_code ec;
      std::filesystem::rename(temp_path, original_path, ec);
      if (ec) {
        // This should never happen. We were able to move the file from `original_path` to
        // `temp_path`. We should be able to move it back.
        LOG(WARNING) << ART_FORMAT("Failed to move old file '{}' back from temporary path '{}': {}",
                                   original_path,
                                   temp_path,
                                   ec.message());
      }
    }
  });

  // Move old files to temporary locations.
  std::vector<std::string_view> all_files_to_remove;
  all_files_to_remove.reserve(files_to_commit.size() + files_to_remove.size());
  for (NewFile* file : files_to_commit) {
    all_files_to_remove.push_back(file->FinalPath());
  }
  all_files_to_remove.insert(
      all_files_to_remove.end(), files_to_remove.begin(), files_to_remove.end());

  for (std::string_view original_path : all_files_to_remove) {
    std::error_code ec;
    std::filesystem::file_status status = std::filesystem::status(original_path, ec);
    if (!std::filesystem::status_known(status)) {
      return Errorf("Failed to get status of old file '{}': {}", original_path, ec.message());
    }
    if (std::filesystem::is_directory(status)) {
      return ErrnoErrorf("Old file '{}' is a directory", original_path);
    }
    if (std::filesystem::exists(status)) {
      std::string temp_path = BuildTempPath(original_path, "XXXXXX");
      int fd = mkstemps(temp_path.data(), /*suffixlen=*/4);
      if (fd < 0) {
        return ErrnoErrorf("Failed to create temporary path for old file '{}'", original_path);
      }
      close(fd);

      std::filesystem::rename(original_path, temp_path, ec);
      if (ec) {
        UnlinkIfExists(temp_path);
        return Errorf("Failed to move old file '{}' to temporary path '{}': {}",
                      original_path,
                      temp_path,
                      ec.message());
      }

      moved_files.push_back({original_path, std::move(temp_path)});
    }
  }

  // Commit new files.
  for (NewFile* file : files_to_commit) {
    OR_RETURN(file->CommitOrAbandon());
  }

  cleanup.Disable();

  // Clean up old files.
  for (const auto& [original_path, temp_path] : moved_files) {
    // This should never fail.  We were able to move the file to `temp_path`. We should be able to
    // remove it.
    UnlinkIfExists(temp_path);
  }

  return {};
}

std::string NewFile::BuildTempPath(std::string_view final_path, const std::string& id) {
  return ART_FORMAT("{}.{}.tmp", final_path, id);
}

Result<std::unique_ptr<File>> OpenFileForReading(const std::string& path) {
  std::unique_ptr<File> file(OS::OpenFileForReading(path.c_str()));
  if (file == nullptr) {
    return ErrnoErrorf("Failed to open file '{}'", path);
  }
  return file;
}

mode_t FileFsPermissionToMode(const FsPermission& fs_permission) {
  return S_IRUSR | S_IWUSR | S_IRGRP | (fs_permission.isOtherReadable ? S_IROTH : 0) |
         (fs_permission.isOtherExecutable ? S_IXOTH : 0);
}

mode_t DirFsPermissionToMode(const FsPermission& fs_permission) {
  return FileFsPermissionToMode(fs_permission) | S_IXUSR | S_IXGRP;
}

Result<void> Chown(const std::string& path, const FsPermission& fs_permission) {
  if (fs_permission.uid < 0 && fs_permission.gid < 0) {
    // Keep the default owner.
  } else if (fs_permission.uid < 0 || fs_permission.gid < 0) {
    return Errorf("uid and gid must be both non-negative or both negative, got {} and {}.",
                  fs_permission.uid,
                  fs_permission.gid);
  }
  if (chown(path.c_str(), fs_permission.uid, fs_permission.gid) != 0) {
    return ErrnoErrorf("Failed to chown '{}'", path);
  }
  return {};
}

}  // namespace artd
}  // namespace art
