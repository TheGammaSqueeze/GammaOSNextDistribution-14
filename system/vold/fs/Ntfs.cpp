/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <sys/mount.h>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include <logwrap/logwrap.h>

#include "Ntfs.h"
#include "Utils.h"

using android::base::StringPrintf;

namespace android {
namespace vold {
namespace ntfs {

static const char* kMkfsPath = "/system/bin/mkfs.ntfs";
static const char* kFsckPath = "/system/bin/fsck.ntfs";
static const char* kMountPath = "/system/bin/mount.ntfs";

bool IsSupported() {
    return access(kMkfsPath, X_OK) == 0
            && access(kFsckPath, X_OK) == 0
            && access(kMountPath, X_OK) == 0
            && IsFilesystemSupported("ntfs");
}

status_t Check(const std::string& source) {
    std::vector<std::string> cmd;
    cmd.push_back(kFsckPath);
    cmd.push_back("-n");
    cmd.push_back(source);

    int rc = ForkExecvp(cmd, nullptr, sFsckUntrustedContext);
    if (rc == 0) {
        LOG(INFO) << "Check OK";
        return 0;
    } else {
        LOG(ERROR) << "Check failed (code " << rc << ")";
        errno = EIO;
        return -1;
    }
}

status_t Mount(const std::string& source, const std::string& target, int ownerUid, int ownerGid,
               int permMask) {
    auto mountData = android::base::StringPrintf("utf8,uid=%d,gid=%d,fmask=%o,dmask=%o,"
                                                 "shortname=mixed,nodev,nosuid,dirsync,noatime,"
                                                 "noexec", ownerUid, ownerGid, permMask, permMask);

    std::vector<std::string> cmd;
    cmd.push_back(kMountPath);
    cmd.push_back("-o");
    cmd.push_back(mountData.c_str());
    cmd.push_back(source.c_str());
    cmd.push_back(target.c_str());

    int rc = ForkExecvp(cmd);
    if (rc == 0) {
        LOG(INFO) << "Mount OK";
        return 0;
    } else {
        LOG(ERROR) << "Mount failed (code " << rc << ")";
        errno = EIO;
        return -1;
    }
}

status_t Format(const std::string& source) {
    std::vector<std::string> cmd;
    cmd.push_back(kMkfsPath);
    cmd.push_back(source);

    int rc = ForkExecvp(cmd);
    if (rc == 0) {
        LOG(INFO) << "Format OK";
        return 0;
    } else {
        LOG(ERROR) << "Format failed (code " << rc << ")";
        errno = EIO;
        return -1;
    }
    return 0;
}

}  // namespace ntfs
}  // namespace vold
}  // namespace android
