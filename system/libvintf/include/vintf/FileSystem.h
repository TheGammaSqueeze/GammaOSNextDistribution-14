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

#ifndef ANDROID_VINTF_FILE_SYSTEM_H
#define ANDROID_VINTF_FILE_SYSTEM_H

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include <sys/stat.h>  // for timespec

#include <utils/Errors.h>

namespace android {
namespace vintf {

using TimeSpec = struct timespec;

// Queries the file system in the correct way. Files can come from
// an actual file system, a sub-directory, or from ADB, depending on the
// implementation.
//
// This class can be used to create a mock for overriding.
class FileSystem {
   public:
    virtual ~FileSystem() {}
    // Return NAME_NOT_FOUND if file is not found,
    //        OK if file is retrieved and written to "fetched".
    virtual status_t fetch(const std::string& path, std::string* fetched,
                           std::string* error) const = 0;
    // Return NAME_NOT_FOUND if directory is not found,
    //        OK if file names are retrieved and written to out.
    virtual status_t listFiles(const std::string& path, std::vector<std::string>* out,
                               std::string* error) const = 0;
    // Return NAME_NOT_FOUND if file is not found,
    //        OK if "mtime" is set with modified time of the file.
    virtual status_t modifiedTime(const std::string& path, TimeSpec* mtime,
                                  std::string* error) const = 0;
};

// Interface to a writable filesystem.
class WritableFileSystem : public FileSystem {
   public:
    // Return OK if successful. On error, return -errno, or UNKNOWN_ERROR if unknown.
    virtual status_t write(const std::string& path, const std::string& content,
                           std::string* error) const = 0;
    // Return OK if successful. On error, return -errno, or UNKNOWN_ERROR if unknown.
    virtual status_t deleteFile(const std::string& path, std::string* error) const = 0;
};

namespace details {

// Class that actually queries the file system.
class FileSystemImpl : public FileSystem {
   public:
    status_t fetch(const std::string&, std::string*, std::string*) const override;
    status_t listFiles(const std::string&, std::vector<std::string>*, std::string*) const override;
    status_t modifiedTime(const std::string& path, TimeSpec* mtime, std::string* error) const;
};

// Class that does nothing.
class FileSystemNoOp : public FileSystem {
   public:
    status_t fetch(const std::string&, std::string*, std::string*) const override;
    status_t listFiles(const std::string&, std::vector<std::string>*, std::string*) const override;
    status_t modifiedTime(const std::string& path, TimeSpec* mtime,
                          std::string* error) const override;
};

// The root is mounted to a given path.
class FileSystemUnderPath : public FileSystem {
   public:
    FileSystemUnderPath(const std::string& rootdir);
    status_t fetch(const std::string& path, std::string* fetched,
                   std::string* error) const override;
    status_t listFiles(const std::string& path, std::vector<std::string>* out,
                       std::string* error) const override;
    status_t modifiedTime(const std::string& path, TimeSpec* mtime,
                          std::string* error) const override;

   protected:
    const std::string& getRootDir() const;

   private:
    std::string mRootDir;
    FileSystemImpl mImpl;
};

// A FileSystem object that can redirect access for one path
// FileSystem is read via the internal impl.
class PathReplacingFileSystem : public FileSystem {
   public:
    // Use |impl| for any actual reads. Owns impl.
    PathReplacingFileSystem(std::string path_to_override, std::string path_replacement,
                            std::unique_ptr<FileSystem> impl);

    status_t fetch(const std::string& path, std::string* fetched,
                   std::string* error) const override;
    status_t listFiles(const std::string& path, std::vector<std::string>* out,
                       std::string* error) const override;
    status_t modifiedTime(const std::string& path, TimeSpec* mtime,
                          std::string* error) const override;

   private:
    std::string path_replace(std::string_view path) const;
    std::string path_to_replace_;
    std::string path_replacement_;
    std::unique_ptr<FileSystem> impl_;
};
}  // namespace details
}  // namespace vintf
}  // namespace android

#endif
