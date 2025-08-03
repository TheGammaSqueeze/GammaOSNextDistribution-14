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

#include <charconv>
#include <filesystem>
#include <map>
#include <span>
#include <string>
#include <vector>

#include <fcntl.h>
#include <linux/fs.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "android-base/errors.h"
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <asm/byteorder.h>
#include <libfsverity.h>
#include <linux/fsverity.h>

#define FS_VERITY_MAX_DIGEST_SIZE 64

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using android::base::unique_fd;

static const char* kFsVerityProcPath = "/proc/sys/fs/verity";

bool SupportsFsVerity() {
    return access(kFsVerityProcPath, F_OK) == 0;
}

static std::string toHex(std::span<const uint8_t> data) {
    std::stringstream ss;
    for (auto it = data.begin(); it != data.end(); ++it) {
        ss << std::setfill('0') << std::setw(2) << std::hex << static_cast<unsigned>(*it);
    }
    return ss.str();
}

static int read_callback(void* file, void* buf, size_t count) {
    int* fd = (int*)file;
    if (TEMP_FAILURE_RETRY(read(*fd, buf, count)) < 0) return errno ? -errno : -EIO;
    return 0;
}

static Result<std::vector<uint8_t>> createDigest(int fd) {
    struct stat filestat;
    int ret = fstat(fd, &filestat);
    if (ret < 0) {
        return ErrnoError() << "Failed to fstat";
    }
    struct libfsverity_merkle_tree_params params = {
        .version = 1,
        .hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
        .file_size = static_cast<uint64_t>(filestat.st_size),
        .block_size = 4096,
    };

    struct libfsverity_digest* digest;
    ret = libfsverity_compute_digest(&fd, &read_callback, &params, &digest);
    if (ret < 0) {
        return ErrnoError() << "Failed to compute fs-verity digest";
    }
    int expected_digest_size = libfsverity_get_digest_size(FS_VERITY_HASH_ALG_SHA256);
    if (digest->digest_size != expected_digest_size) {
        return Error() << "Digest does not have expected size: " << expected_digest_size
                       << " actual: " << digest->digest_size;
    }
    std::vector<uint8_t> digestVector(&digest->digest[0], &digest->digest[expected_digest_size]);
    free(digest);
    return digestVector;
}

Result<std::vector<uint8_t>> createDigest(const std::string& path) {
    unique_fd fd(TEMP_FAILURE_RETRY(open(path.c_str(), O_RDONLY | O_CLOEXEC)));
    if (!fd.ok()) {
        return ErrnoError() << "Unable to open";
    }
    return createDigest(fd.get());
}

namespace {
template <typename T> struct DeleteAsPODArray {
    void operator()(T* x) {
        if (x) {
            x->~T();
            delete[](uint8_t*) x;
        }
    }
};

template <typename T> using trailing_unique_ptr = std::unique_ptr<T, DeleteAsPODArray<T>>;

template <typename T>
static trailing_unique_ptr<T> makeUniqueWithTrailingData(size_t trailing_data_size) {
    uint8_t* memory = new uint8_t[sizeof(T) + trailing_data_size];
    T* ptr = new (memory) T;
    return trailing_unique_ptr<T>{ptr};
}

static Result<std::string> measureFsVerity(int fd) {
    auto d = makeUniqueWithTrailingData<fsverity_digest>(FS_VERITY_MAX_DIGEST_SIZE);
    d->digest_size = FS_VERITY_MAX_DIGEST_SIZE;

    if (ioctl(fd, FS_IOC_MEASURE_VERITY, d.get()) != 0) {
        if (errno == ENODATA) {
            return Error() << "File is not in fs-verity";
        } else {
            return ErrnoError() << "Failed to FS_IOC_MEASURE_VERITY";
        }
    }

    return toHex({&d->digest[0], &d->digest[d->digest_size]});
}

static Result<std::string> measureFsVerity(const std::string& path) {
    unique_fd fd(TEMP_FAILURE_RETRY(open(path.c_str(), O_RDONLY | O_CLOEXEC)));
    if (!fd.ok()) {
        return ErrnoError() << "Failed to open " << path;
    }

    return measureFsVerity(fd.get());
}

}  // namespace

static Result<void> enableFsVerity(int fd) {
    struct fsverity_enable_arg arg = {.version = 1};

    arg.hash_algorithm = FS_VERITY_HASH_ALG_SHA256;
    arg.block_size = 4096;

    int ret = ioctl(fd, FS_IOC_ENABLE_VERITY, &arg);

    if (ret != 0) {
        return ErrnoError() << "Failed to call FS_IOC_ENABLE_VERITY";
    }

    return {};
}

Result<void> enableFsVerity(const std::string& path) {
    unique_fd fd(TEMP_FAILURE_RETRY(open(path.c_str(), O_RDONLY | O_CLOEXEC)));
    if (!fd.ok()) {
        return Error() << "Can't open " << path;
    }

    return enableFsVerity(fd.get());
}

static Result<bool> isFileInVerity(int fd) {
    unsigned int flags;
    if (ioctl(fd, FS_IOC_GETFLAGS, &flags) < 0) {
        return ErrnoError() << "ioctl(FS_IOC_GETFLAGS) failed";
    }
    return (flags & FS_VERITY_FL) != 0;
}

Result<std::map<std::string, std::string>> addFilesToVerityRecursive(const std::string& path) {
    std::map<std::string, std::string> digests;

    std::error_code ec;
    auto it = std::filesystem::recursive_directory_iterator(path, ec);
    for (auto end = std::filesystem::recursive_directory_iterator(); it != end; it.increment(ec)) {
        if (it->is_regular_file()) {
            unique_fd fd(TEMP_FAILURE_RETRY(open(it->path().c_str(), O_RDONLY | O_CLOEXEC)));
            if (!fd.ok()) {
                return ErrnoError() << "Failed to open " << path;
            }
            auto enabled = OR_RETURN(isFileInVerity(fd));
            if (!enabled) {
                LOG(INFO) << "Adding " << it->path() << " to fs-verity...";
                OR_RETURN(enableFsVerity(fd));
            } else {
                LOG(INFO) << it->path() << " was already in fs-verity.";
            }
            auto digest = OR_RETURN(measureFsVerity(fd));
            digests[it->path()] = digest;
        }
    }
    if (ec) {
        return Error() << "Failed to iterate " << path << ": " << ec.message();
    }

    return digests;
}

Result<std::map<std::string, std::string>> verifyAllFilesInVerity(const std::string& path) {
    std::map<std::string, std::string> digests;
    std::error_code ec;

    auto it = std::filesystem::recursive_directory_iterator(path, ec);
    auto end = std::filesystem::recursive_directory_iterator();

    while (!ec && it != end) {
        if (it->is_regular_file()) {
            // Verify the file is in fs-verity
            auto result = OR_RETURN(measureFsVerity(it->path()));
            digests[it->path()] = result;
        } else if (it->is_directory()) {
            // These are fine to ignore
        } else if (it->is_symlink()) {
            return Error() << "Rejecting artifacts, symlink at " << it->path();
        } else {
            return Error() << "Rejecting artifacts, unexpected file type for " << it->path();
        }
        ++it;
    }
    if (ec) {
        return Error() << "Failed to iterate " << path << ": " << ec;
    }

    return digests;
}

Result<void> verifyAllFilesUsingCompOs(const std::string& directory_path,
                                       const std::map<std::string, std::string>& digests) {
    std::error_code ec;
    size_t verified_count = 0;
    auto it = std::filesystem::recursive_directory_iterator(directory_path, ec);
    for (auto end = std::filesystem::recursive_directory_iterator(); it != end; it.increment(ec)) {
        auto& path = it->path();
        if (it->is_regular_file()) {
            auto entry = digests.find(path);
            if (entry == digests.end()) {
                return Error() << "Unexpected file found: " << path;
            }
            auto& compos_digest = entry->second;

            unique_fd fd(TEMP_FAILURE_RETRY(open(path.c_str(), O_RDONLY | O_CLOEXEC)));
            if (!fd.ok()) {
                return ErrnoError() << "Can't open " << path;
            }

            bool enabled = OR_RETURN(isFileInVerity(fd));
            if (!enabled) {
                LOG(INFO) << "Enabling fs-verity for " << path;
                OR_RETURN(enableFsVerity(fd));
            }

            auto actual_digest = OR_RETURN(measureFsVerity(fd));
            // Make sure the file's fs-verity digest matches the known value.
            if (actual_digest == compos_digest) {
                ++verified_count;
            } else {
                return Error() << "fs-verity digest does not match CompOS digest: " << path;
            }
        } else if (it->is_directory()) {
            // These are fine to ignore
        } else if (it->is_symlink()) {
            return Error() << "Rejecting artifacts, symlink at " << path;
        } else {
            return Error() << "Rejecting artifacts, unexpected file type for " << path;
        }
    }
    if (ec) {
        return Error() << "Failed to iterate " << directory_path << ": " << ec.message();
    }

    // Make sure all the files we expected have been seen
    if (verified_count != digests.size()) {
        return Error() << "Verified " << verified_count << " files, but expected "
                       << digests.size();
    }

    return {};
}
