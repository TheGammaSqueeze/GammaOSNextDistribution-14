/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <memory>

#include <utils/Errors.h>
#include <vintf/Dirmap.h>
#include <vintf/FileSystem.h>

namespace android::vintf::details {

// A FileSystem object that uses Dirmap to redirect reads.
// FileSystem is read via the internal impl.
class HostFileSystem : public FileSystemImpl {
   public:
    // Use FileSystemImpl for any actual reads.
    HostFileSystem(const Dirmap& dirmap, status_t missingError);
    // Use |impl| for any actual reads. Does not own impl.
    HostFileSystem(const Dirmap& dirmap, status_t missingError, FileSystem* impl);
    // Use |impl| for any actual reads. Owns impl.
    HostFileSystem(const Dirmap& dirmap, status_t missingError, std::unique_ptr<FileSystem>&& impl);

    status_t fetch(const std::string& path, std::string* fetched,
                   std::string* error) const override;
    status_t listFiles(const std::string& path, std::vector<std::string>* out,
                       std::string* error) const override;
    status_t modifiedTime(const std::string& path, TimeSpec* mtime,
                          std::string* error) const override;

   private:
    std::string resolve(const std::string& path, std::string* error) const;

    Dirmap mDirMap;
    status_t mMissingError;

    std::unique_ptr<FileSystem> mImplUniq;
    FileSystem* mImpl;
};

}  // namespace android::vintf::details
