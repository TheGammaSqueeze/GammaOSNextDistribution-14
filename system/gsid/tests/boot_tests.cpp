//
// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <linux/fs.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/vfs.h>
#include <unistd.h>

#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <android/hardware/weaver/1.0/IWeaver.h>
#include <ext4_utils/ext4_utils.h>
#include <fstab/fstab.h>
#include <gtest/gtest.h>

using namespace android::fs_mgr;

using android::base::unique_fd;
using android::hardware::weaver::V1_0::IWeaver;
using android::hardware::weaver::V1_0::WeaverConfig;
using android::hardware::weaver::V1_0::WeaverStatus;


static bool IsAutomotiveDevice() {
    auto hw_type = android::base::GetProperty("ro.hardware.type", "");
    return hw_type == "automotive";
}

TEST(MetadataPartition, FirstStageMount) {
    Fstab fstab;
    if (ReadFstabFromDt(&fstab)) {
        auto entry = GetEntryForMountPoint(&fstab, "/metadata");
        ASSERT_NE(entry, nullptr);
    } else {
        ASSERT_TRUE(ReadDefaultFstab(&fstab));
        auto entry = GetEntryForMountPoint(&fstab, "/metadata");
        ASSERT_NE(entry, nullptr);
        EXPECT_TRUE(entry->fs_mgr_flags.first_stage_mount);
    }
}

static int GetVsrLevel() {
    return android::base::GetIntProperty("ro.vendor.api_level", -1);
}

TEST(MetadataPartition, MinimumSize) {
    Fstab fstab;
    ASSERT_TRUE(ReadDefaultFstab(&fstab));

    auto entry = GetEntryForMountPoint(&fstab, "/metadata");
    ASSERT_NE(entry, nullptr);

    unique_fd fd(open(entry->blk_device.c_str(), O_RDONLY | O_CLOEXEC));
    ASSERT_GE(fd, 0);

    uint64_t size = get_block_device_size(fd);
    ASSERT_GE(size, 16777216);
}

TEST(Weaver, MinimumSlots) {
    auto weaver = IWeaver::getService();
    if (!weaver) {
        return;
    }

    WeaverStatus hw_status;
    WeaverConfig hw_config;

    auto res = weaver->getConfig([&](WeaverStatus status, const WeaverConfig& config) {
            hw_status = status;
            hw_config = config;
    });
    ASSERT_TRUE(res.isOk());
    ASSERT_EQ(hw_status, WeaverStatus::OK);
    EXPECT_GE(hw_config.slots, 16);
}

TEST(MetadataPartition, FsType) {
    if (GetVsrLevel() < __ANDROID_API_T__ || IsAutomotiveDevice()) {
        GTEST_SKIP();
    }

    Fstab fstab;
    ASSERT_TRUE(ReadDefaultFstab(&fstab));

    std::vector<std::string> mount_points = {"/data"};
    for (const auto& mount_point : mount_points) {
        auto path = mount_point + "/gsi";

        // These paths should not be symlinks.
        struct stat s;
        ASSERT_GE(lstat(path.c_str(), &s), 0) << path;
        ASSERT_FALSE(S_ISLNK(s.st_mode));

        struct statfs64 fs;
        ASSERT_GE(statfs64(path.c_str(), &fs), 0) << path;
        ASSERT_EQ(fs.f_type, F2FS_SUPER_MAGIC);

        auto entry = GetEntryForMountPoint(&fstab, mount_point);
        ASSERT_NE(entry, nullptr);
        ASSERT_EQ(entry->fs_type, "f2fs");
    }
}
