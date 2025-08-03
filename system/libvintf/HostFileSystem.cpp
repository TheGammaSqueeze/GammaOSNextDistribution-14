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

#include <vintf/HostFileSystem.h>

#include <android-base/logging.h>
#include <android-base/strings.h>

namespace android::vintf::details {

HostFileSystem::HostFileSystem(const Dirmap& dirmap, status_t missingError)
    : HostFileSystem(dirmap, missingError, std::make_unique<FileSystemImpl>()) {}

HostFileSystem::HostFileSystem(const Dirmap& dirmap, status_t missingError,
                               std::unique_ptr<FileSystem>&& impl)
    : HostFileSystem(dirmap, missingError, impl.get()) {
    mImplUniq = std::move(impl);
}

HostFileSystem::HostFileSystem(const Dirmap& dirmap, status_t missingError, FileSystem* impl)
    : mDirMap(dirmap), mMissingError(missingError), mImpl(impl) {}

status_t HostFileSystem::fetch(const std::string& path, std::string* fetched,
                               std::string* error) const {
    auto resolved = resolve(path, error);
    if (resolved.empty()) {
        return mMissingError;
    }
    status_t status = mImpl->fetch(resolved, fetched, error);
    LOG(INFO) << "Fetch '" << resolved << "': " << statusToString(status);
    return status;
}

status_t HostFileSystem::listFiles(const std::string& path, std::vector<std::string>* out,
                                   std::string* error) const {
    auto resolved = resolve(path, error);
    if (resolved.empty()) {
        return mMissingError;
    }
    status_t status = mImpl->listFiles(resolved, out, error);
    LOG(INFO) << "List '" << resolved << "': " << statusToString(status);
    return status;
}

status_t HostFileSystem::modifiedTime(const std::string& path, TimeSpec* mtime,
                                      std::string* error) const {
    auto resolved = resolve(path, error);
    if (resolved.empty()) {
        return mMissingError;
    }
    status_t status = mImpl->modifiedTime(resolved, mtime, error);
    LOG(INFO) << "Get modified time '" << resolved << "': " << statusToString(status);
    return status;
}

std::string HostFileSystem::resolve(const std::string& path, std::string* error) const {
    for (auto [prefix, mappedPath] : mDirMap) {
        if (path == prefix) {
            return mappedPath;
        }
        if (android::base::StartsWith(path, prefix + "/")) {
            return mappedPath + "/" + path.substr(prefix.size() + 1);
        }
    }
    if (error) {
        *error = "Cannot resolve path " + path;
    } else {
        LOG(mMissingError == NAME_NOT_FOUND ? INFO : ERROR) << "Cannot resolve path " << path;
    }
    return "";
}

}  // namespace android::vintf::details
