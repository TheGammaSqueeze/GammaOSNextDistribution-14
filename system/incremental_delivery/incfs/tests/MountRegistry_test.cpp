/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "MountRegistry.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <gtest/gtest.h>
#include <sys/select.h>
#include <unistd.h>

#include <iterator>
#include <optional>
#include <thread>

#include "incfs.h"
#include "path.h"

using namespace android::incfs;
using namespace std::literals;

class MountRegistryTest : public ::testing::Test {
protected:
    virtual void SetUp() {}
    virtual void TearDown() {}

    MountRegistry::Mounts mounts_;

    MountRegistry::Mounts& r() { return mounts_; }
};

TEST_F(MountRegistryTest, RootForRoot) {
    r().addRoot("/root", "/backing");
    ASSERT_STREQ("/root", r().rootFor("/root").data());
    ASSERT_STREQ("/root", r().rootFor("/root/1").data());
    ASSERT_STREQ("/root", r().rootFor("/root/1/2").data());
    ASSERT_STREQ(nullptr, r().rootFor("/root1/1/2").data());
    ASSERT_STREQ(nullptr, r().rootFor("/1/root").data());
    ASSERT_STREQ(nullptr, r().rootFor("root").data());
}

TEST_F(MountRegistryTest, OneBind) {
    r().addRoot("/root", "/backing");
    r().addBind("/root/1", "/bind");
    ASSERT_STREQ("/root", r().rootFor("/root").data());
    ASSERT_STREQ("/root", r().rootFor("/bind").data());
    ASSERT_STREQ("/root", r().rootFor("/bind/1").data());
    ASSERT_STREQ("/root", r().rootFor("/root/1").data());
    ASSERT_STREQ(nullptr, r().rootFor("/1/bind").data());
    ASSERT_STREQ(nullptr, r().rootFor("bind").data());
    ASSERT_STREQ(nullptr, r().rootFor("/bind1").data());
    ASSERT_STREQ(nullptr, r().rootFor("/.bind").data());
}

TEST_F(MountRegistryTest, MultiBind) {
    r().addRoot("/root", "/backing");
    r().addBind("/root/1", "/bind");
    r().addBind("/root/2/3", "/bind2");
    r().addBind("/root/2/3", "/other/bind");
    ASSERT_STREQ("/root", r().rootFor("/root").data());
    ASSERT_STREQ("/root", r().rootFor("/bind").data());
    ASSERT_STREQ("/root", r().rootFor("/bind2").data());
    ASSERT_STREQ("/root", r().rootFor("/other/bind/dir").data());
    ASSERT_EQ("/root"s, r().rootAndSubpathFor("/root").first->path);
    ASSERT_EQ(""s, r().rootAndSubpathFor("/root").second);
    ASSERT_EQ("/root"s, r().rootAndSubpathFor("/bind").first->path);
    ASSERT_EQ("1"s, r().rootAndSubpathFor("/bind").second);
    ASSERT_EQ("/root"s, r().rootAndSubpathFor("/bind2").first->path);
    ASSERT_EQ("2/3"s, r().rootAndSubpathFor("/bind2").second);
    ASSERT_EQ("/root"s, r().rootAndSubpathFor("/bind2/blah").first->path);
    ASSERT_EQ("2/3/blah"s, r().rootAndSubpathFor("/bind2/blah").second);
    ASSERT_EQ("/root"s, r().rootAndSubpathFor("/other/bind/blah").first->path);
    ASSERT_EQ("2/3/blah"s, r().rootAndSubpathFor("/other/bind/blah").second);
}

TEST_F(MountRegistryTest, MultiRoot) {
    r().addRoot("/root", "/backing");
    r().addBind("/root", "/bind");
    ASSERT_STREQ("/root", r().rootFor("/root").data());
    ASSERT_STREQ("/root", r().rootFor("/bind").data());
    ASSERT_STREQ("/root", r().rootFor("/bind/2").data());
}

static MountRegistry::Mounts makeFrom(std::string_view str) {
    TemporaryFile f;
    EXPECT_TRUE(android::base::WriteFully(f.fd, str.data(), str.size()));
    EXPECT_EQ(0, lseek(f.fd, 0, SEEK_SET)); // rewind

    MountRegistry::Mounts m;
    EXPECT_TRUE(m.loadFrom(f.fd, INCFS_NAME));
    return std::move(m);
}

TEST_F(MountRegistryTest, MultiRootLoad) {
    constexpr char mountsFile[] =
            R"(4605 34 0:154 / /mnt/installer/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime shared:45 master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4561 35 0:154 / /mnt/androidwritable/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime shared:44 master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4560 99 0:154 / /storage/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4650 30 0:44 /MyFiles /mnt/pass_through/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,relatime shared:31 - 9p media rw,sync,dirsync,access=client,trans=virtio
3181 79 0:146 / /data/incremental/MT_data_app_vmdl703/mount rw,nosuid,nodev,noatime shared:46 - incremental-fs /data/incremental/MT_data_app_vmdl703/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0
3182 77 0:146 / /var/run/mount/data/mount/data/incremental/MT_data_app_vmdl703/mount rw,nosuid,nodev,noatime shared:46 - incremental-fs /data/incremental/MT_data_app_vmdl703/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0
)";

    auto m = makeFrom(mountsFile);

    EXPECT_EQ(size_t(1), m.size());
    EXPECT_STREQ("/data/incremental/MT_data_app_vmdl703/mount",
                 m.rootFor("/data/incremental/MT_data_app_vmdl703/mount/123/2").data());
    EXPECT_STREQ("/data/incremental/MT_data_app_vmdl703/mount",
                 m.rootFor("/var/run/mount/data/mount/data/incremental/MT_data_app_vmdl703/mount/"
                           "some/thing")
                         .data());
}

TEST_F(MountRegistryTest, MultiRootLoadReversed) {
    constexpr char mountsFile[] =
            R"(4605 34 0:154 / /mnt/installer/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime shared:45 master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4561 35 0:154 / /mnt/androidwritable/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime shared:44 master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4560 99 0:154 / /storage/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,noatime master:43 - fuse /dev/fuse rw,lazytime,user_id=0,group_id=0,allow_other
4650 30 0:44 /MyFiles /mnt/pass_through/0/0000000000000000000000000000CAFEF00D2019 rw,nosuid,nodev,noexec,relatime shared:31 - 9p media rw,sync,dirsync,access=client,trans=virtio
3182 77 0:146 / /var/run/mount/data/mount/data/incremental/MT_data_app_vmdl703/mount rw,nosuid,nodev,noatime shared:46 - incremental-fs /data/incremental/MT_data_app_vmdl703/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0
3181 79 0:146 / /data/incremental/MT_data_app_vmdl703/mount rw,nosuid,nodev,noatime shared:46 - incremental-fs /data/incremental/MT_data_app_vmdl703/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0
)";

    auto m = makeFrom(mountsFile);

    EXPECT_EQ(size_t(1), m.size());
    EXPECT_STREQ("/data/incremental/MT_data_app_vmdl703/mount",
                 m.rootFor("/data/incremental/MT_data_app_vmdl703/mount/123/2").data());
    EXPECT_STREQ("/data/incremental/MT_data_app_vmdl703/mount",
                 m.rootFor("/var/run/mount/data/mount/data/incremental/MT_data_app_vmdl703/mount/"
                           "some/thing")
                         .data());
}

TEST_F(MountRegistryTest, LoadInvalid) {
    constexpr char mountsFile[] =
            R"(9465 93 0:281 // /data/incremental1 shared:56 - incremental-fs /data/incremental2 rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9529 93 0:281 /st_232_0 /data/app/vmdl1998506227.tmp rw,nosuid,nodev,noatime shared:56 - incremental-fs /data/incremental/MT_data_app_vmdl199/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9657 93 0:282 /st_233_0 /data/app/vmdl2034419270.tmp rw,nosuid,nodev,noatime shared:57 - incremental-fs /data/incremental/MT_data_app_vmdl203/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9721 93 0:283 / /data/incremental/MT_data_app_vmdl154/mount rw,nosuid,nodev,noatime shared:58 - incremental-fs /data/incremental/MT_data_app_vmdl154/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9785 93 0:283 /st_234_0 /data/app/vmdl1545425783.tmp rw,nosuid,nodev,noatime shared:58 - incremental-fs /data/incremental/MT_data_app_vmdl154/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9849 93 0:284 / /data/incremental/MT_data_app_vmdl209/mount rw,nosuid,nodev,noatime shared:59 - incremental-fs /data/incremental/MT_data_app_vmdl209/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
9913 93 0:284 /st_235_0 /data/app/vmdl2099748756.tmp rw,nosuid,nodev,noatime shared:59 - incremental-fs /data/incremental/MT_data_app_vmdl209/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
4007 93 0:269 /st_240_1 /data/app/~~I499PLubwcOVbJaEFqpHHQ== rw,nosuid,nodev,noatime shared:44 - incremental-fs /data/incremental/MT_data_app_vmdl158/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
5285 93 0:270 /st_241_1 /data/app/~~CvgMdGm9eNJpvdq-62Jktg== rw,nosuid,nodev,noatime shared:45 - incremental-fs /data/incremental/MT_data_app_vmdl943/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
8786 93 0:271 /st_242_1 /data/app/~~_oNudLuBvqtSE78VoMVY5Q== rw,nosuid,nodev,noatime shared:46 - incremental-fs /data/incremental/MT_data_app_vmdl144/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
10358 93 0:272 /st_243_1 /data/app/~~o5-RadxV4DUe-mgPyv9SkQ== rw,nosuid,nodev,noatime shared:47 - incremental-fs /data/incremental/MT_data_app_vmdl642/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
10422 93 0:131 /st_244_1 /data/app/~~rTnKswx1F427UguGO9nDRA== rw,nosuid,nodev,noatime shared:48 - incremental-fs /data/incremental/MT_data_app_vmdl336/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
10486 93 0:132 /st_245_1 /data/app/~~ZIoVqPDBjLBeajD4thHsYA== rw,nosuid,nodev,noatime shared:49 - incremental-fs /data/incremental/MT_data_app_vmdl993/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
10550 93 0:133 /st_246_1 /data/app/~~2qjtCtx5rqPW2Hwlrciu2w== rw,nosuid,nodev,noatime shared:50 - incremental-fs /data/incremental/MT_data_app_vmdl827/backing_store rw,seclabel,read_timeout_ms=10000,readahead=0,report_uid
)";

    auto m = makeFrom(mountsFile);
    // only two of the mounts in this file are valid
    EXPECT_EQ(size_t(2), m.size());
}
