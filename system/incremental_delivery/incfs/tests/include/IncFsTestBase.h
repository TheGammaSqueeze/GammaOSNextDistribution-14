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

#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <gtest/gtest.h>
#include <selinux/selinux.h>

#include "incfs.h"
#include "path.h"

namespace android::incfs {

static bool exists(std::string_view path) {
    return access(path.data(), F_OK) == 0;
}

class IncFsTestBase : public ::testing::Test {
protected:
    virtual void SetUp() {
        tmp_dir_for_mount_.emplace();
        mount_dir_path_ = tmp_dir_for_mount_->path;
        tmp_dir_for_image_.emplace();
        image_dir_path_ = tmp_dir_for_image_->path;
        ASSERT_TRUE(exists(image_dir_path_));
        ASSERT_TRUE(exists(mount_dir_path_));
        if (!enabled()) {
            GTEST_SKIP() << "test not supported: IncFS is not enabled";
        } else {
            metrics_key_ = path::baseName(image_dir_path_);
            control_ = mount(image_dir_path_, mount_dir_path_,
                             MountOptions{.readLogBufferPages = 4,
                                          .defaultReadTimeoutMs = getReadTimeout(),
                                          .sysfsName = metrics_key_.c_str()});
            ASSERT_TRUE(control_.cmd() >= 0) << "Expected >= 0 got " << control_.cmd();
            ASSERT_TRUE(control_.pendingReads() >= 0);
            ASSERT_TRUE(control_.logs() >= 0);
            checkRestoreconResult(mountPath(INCFS_PENDING_READS_FILENAME));
            checkRestoreconResult(mountPath(INCFS_LOG_FILENAME));
        }
    }

    virtual void TearDown() {
        unmount(mount_dir_path_);
        tmp_dir_for_image_.reset();
        tmp_dir_for_mount_.reset();
        EXPECT_FALSE(exists(image_dir_path_));
        EXPECT_FALSE(exists(mount_dir_path_));
    }

    static void checkRestoreconResult(std::string_view path) {
        char* ctx = nullptr;
        ASSERT_NE(-1, getfilecon(path.data(), &ctx));
        ASSERT_EQ("u:object_r:shell_data_file:s0", std::string(ctx));
        freecon(ctx);
    }

    static IncFsFileId fileId(uint64_t i) {
        IncFsFileId id = {};
        static_assert(sizeof(id) >= sizeof(i));
        memcpy(&id, &i, sizeof(i));
        return id;
    }

    virtual int32_t getReadTimeout() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(kDefaultReadTimeout).count();
    }

    template <class... Paths>
    std::string mountPath(Paths&&... paths) const {
        return path::join(mount_dir_path_, std::forward<Paths>(paths)...);
    }

    virtual int makeFileWithHash(int id) {
        // calculate the required size for two leaf hash blocks
        constexpr auto size =
                (INCFS_DATA_FILE_BLOCK_SIZE / INCFS_MAX_HASH_SIZE + 1) * INCFS_DATA_FILE_BLOCK_SIZE;

        // assemble a signature/hashing data for it
        struct __attribute__((packed)) Signature {
            uint32_t version = INCFS_SIGNATURE_VERSION;
            uint32_t hashingSize = sizeof(hashing);
            struct __attribute__((packed)) Hashing {
                uint32_t algo = INCFS_HASH_TREE_SHA256;
                uint8_t log2Blocksize = 12;
                uint32_t saltSize = 0;
                uint32_t rootHashSize = INCFS_MAX_HASH_SIZE;
                char rootHash[INCFS_MAX_HASH_SIZE] = {};
            } hashing;
            uint32_t signingSize = 0;
        } signature;

        int res = makeFile(control_, mountPath(test_file_name_), 0555, fileId(id),
                           {.size = size,
                            .signature = {.data = (char*)&signature, .size = sizeof(signature)}});
        EXPECT_EQ(0, res);
        return res ? -1 : size;
    }

    std::string mount_dir_path_;
    std::optional<TemporaryDir> tmp_dir_for_mount_;
    std::string image_dir_path_;
    std::optional<TemporaryDir> tmp_dir_for_image_;
    inline static const std::string_view test_file_name_ = "test.txt";
    inline static const std::string_view test_mapped_file_name_ = "mapped.txt";
    inline static const std::string_view test_dir_name_ = "test_dir";
    std::string metrics_key_;
    Control control_;
};

} // namespace android::incfs
