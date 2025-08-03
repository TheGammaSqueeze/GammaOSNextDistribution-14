
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

#include <vintf/FileSystem.h>

#include <dirent.h>

#include <android-base/file.h>
#include <android-base/strings.h>

namespace android {
namespace vintf {
namespace details {

status_t FileSystemImpl::fetch(const std::string& path, std::string* fetched,
                               std::string* error) const {
    if (!android::base::ReadFileToString(path, fetched, true /* follow_symlinks */)) {
        int saved_errno = errno;
        if (error) {
            *error = "Cannot read " + path + ": " + strerror(saved_errno);
        }
        return saved_errno == 0 ? UNKNOWN_ERROR : -saved_errno;
    }
    return OK;
}

status_t FileSystemImpl::listFiles(const std::string& path, std::vector<std::string>* out,
                                   std::string* error) const {
    std::unique_ptr<DIR, decltype(&closedir)> dir(opendir(path.c_str()), closedir);
    if (!dir) {
        int saved_errno = errno;
        if (error) {
            *error = "Cannot open " + path + ": " + strerror(saved_errno);
        }
        return saved_errno == 0 ? UNKNOWN_ERROR : -saved_errno;
    }

    dirent* dp;
    while (errno = 0, dp = readdir(dir.get()), dp != nullptr) {
        if (dp->d_type != DT_DIR) {
            out->push_back(dp->d_name);
        }
    }
    int saved_errno = errno;
    if (saved_errno != 0) {
        if (error) {
            *error = "Failed while reading directory " + path + ": " + strerror(saved_errno);
        }
    }
    return -saved_errno;
}

status_t FileSystemImpl::modifiedTime(const std::string& path, TimeSpec* mtime,
                                      std::string* error) const {
    struct stat stat_buf;
    if (stat(path.c_str(), &stat_buf) != 0) {
        int saved_errno = errno;
        if (error) {
            *error = "Cannot open " + path + ": " + strerror(saved_errno);
        }
        return saved_errno == 0 ? UNKNOWN_ERROR : -saved_errno;
    }
    *mtime = stat_buf.st_mtim;
    return OK;
}

status_t FileSystemNoOp::fetch(const std::string&, std::string*, std::string*) const {
    return NAME_NOT_FOUND;
}

status_t FileSystemNoOp::listFiles(const std::string&, std::vector<std::string>*,
                                   std::string*) const {
    return NAME_NOT_FOUND;
}

status_t FileSystemNoOp::modifiedTime(const std::string&, TimeSpec*, std::string*) const {
    return NAME_NOT_FOUND;
}

FileSystemUnderPath::FileSystemUnderPath(const std::string& rootdir) {
    mRootDir = rootdir;
    if (!mRootDir.empty() && mRootDir.back() != '/') {
        mRootDir.push_back('/');
    }
}

status_t FileSystemUnderPath::fetch(const std::string& path, std::string* fetched,
                                    std::string* error) const {
    return mImpl.fetch(mRootDir + path, fetched, error);
}

status_t FileSystemUnderPath::listFiles(const std::string& path, std::vector<std::string>* out,
                                        std::string* error) const {
    return mImpl.listFiles(mRootDir + path, out, error);
}

status_t FileSystemUnderPath::modifiedTime(const std::string& path, TimeSpec* mtime,
                                           std::string* error) const {
    return mImpl.modifiedTime(mRootDir + path, mtime, error);
}

const std::string& FileSystemUnderPath::getRootDir() const {
    return mRootDir;
}

PathReplacingFileSystem::PathReplacingFileSystem(std::string path_to_replace,
                                                 std::string path_replacement,
                                                 std::unique_ptr<FileSystem> impl)
    : path_to_replace_{std::move(path_to_replace)},
      path_replacement_{std::move(path_replacement)},
      impl_{std::move(impl)} {
    // Enforce a trailing slash on the path-to-be-replaced, prevents
    // the problem (for example) of /foo matching and changing /fooxyz
    if (!android::base::EndsWith(path_to_replace_, '/')) {
        path_to_replace_ += "/";
    }
    // Enforce a trailing slash on the replacement path.  This ensures
    // we are replacing a directory with a directory.
    if (!android::base::EndsWith(path_replacement_, '/')) {
        path_replacement_ += "/";
    }
}

status_t PathReplacingFileSystem::fetch(const std::string& path, std::string* fetched,
                                        std::string* error) const {
    return impl_->fetch(path_replace(path), fetched, error);
}

status_t PathReplacingFileSystem::listFiles(const std::string& path, std::vector<std::string>* out,
                                            std::string* error) const {
    return impl_->listFiles(path_replace(path), out, error);
}

status_t PathReplacingFileSystem::modifiedTime(const std::string& path, TimeSpec* mtime,
                                               std::string* error) const {
    return impl_->modifiedTime(path_replace(path), mtime, error);
}

std::string PathReplacingFileSystem::path_replace(std::string_view path) const {
    std::string retstr;
    if (android::base::ConsumePrefix(&path, path_to_replace_)) {
        retstr.reserve(path_replacement_.size() + path.size());
        retstr.append(path_replacement_);
        retstr.append(path);
        return retstr;
    }
    return std::string{path};
}

}  // namespace details
}  // namespace vintf
}  // namespace android
