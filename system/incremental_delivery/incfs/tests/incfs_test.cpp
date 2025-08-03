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

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <sys/select.h>

#include <unistd.h>

#include <optional>
#include <thread>

#include "IncFsTestBase.h"

using namespace android::incfs;
using namespace std::literals;
namespace ab = android::base;

struct ScopedUnmount {
    std::string path_;
    explicit ScopedUnmount(std::string&& path) : path_(std::move(path)) {}
    ~ScopedUnmount() { unmount(path_); }
};

class IncFsTest : public IncFsTestBase {
protected:
    virtual int32_t getReadTimeout() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(kDefaultReadTimeout).count();
    }

    static IncFsSpan metadata(std::string_view sv) {
        return {.data = sv.data(), .size = IncFsSize(sv.size())};
    }

    static int sizeToPages(int size) {
        return (size + INCFS_DATA_FILE_BLOCK_SIZE - 1) / INCFS_DATA_FILE_BLOCK_SIZE;
    }

    void writeTestRanges(int id, int size) {
        auto wfd = openForSpecialOps(control_, fileId(id));
        ASSERT_GE(wfd.get(), 0);

        auto lastPage = sizeToPages(size) - 1;

        std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
        DataBlock blocks[] = {{
                                      .fileFd = wfd.get(),
                                      .pageIndex = 1,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .data = data.data(),
                              },
                              {
                                      .fileFd = wfd.get(),
                                      .pageIndex = 2,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .data = data.data(),
                              },
                              {
                                      .fileFd = wfd.get(),
                                      .pageIndex = 10,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .data = data.data(),
                              },
                              {
                                      .fileFd = wfd.get(),
                                      // last data page
                                      .pageIndex = lastPage,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .data = data.data(),
                              },
                              {
                                      .fileFd = wfd.get(),
                                      // first hash page
                                      .pageIndex = 0,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .kind = INCFS_BLOCK_KIND_HASH,
                                      .data = data.data(),
                              },
                              {
                                      .fileFd = wfd.get(),
                                      .pageIndex = 2,
                                      .compression = INCFS_COMPRESSION_KIND_NONE,
                                      .dataSize = (uint32_t)data.size(),
                                      .kind = INCFS_BLOCK_KIND_HASH,
                                      .data = data.data(),
                              }};
        ASSERT_EQ((int)std::size(blocks), writeBlocks({blocks, std::size(blocks)}));
    }

    void writeBlock(int pageIndex) {
        auto fd = openForSpecialOps(control_, fileId(1));
        ASSERT_GE(fd.get(), 0);

        std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
        auto block = DataBlock{
                .fileFd = fd.get(),
                .pageIndex = pageIndex,
                .compression = INCFS_COMPRESSION_KIND_NONE,
                .dataSize = (uint32_t)data.size(),
                .data = data.data(),
        };
        ASSERT_EQ(1, writeBlocks({&block, 1}));
    }

    template <class ReadStruct>
    void testWriteBlockAndPageRead() {
        const auto id = fileId(1);
        ASSERT_TRUE(control_.logs() >= 0);
        ASSERT_EQ(0,
                  makeFile(control_, mountPath(test_file_name_), 0555, id,
                           {.size = test_file_size_}));
        writeBlock(/*pageIndex=*/0);

        std::thread wait_page_read_thread([&]() {
            std::vector<ReadStruct> reads;
            auto res = waitForPageReads(control_, std::chrono::seconds(5), &reads);
            ASSERT_EQ(WaitResult::HaveData, res) << (int)res;
            ASSERT_FALSE(reads.empty());
            EXPECT_EQ(0, memcmp(&id, &reads[0].id, sizeof(id)));
            EXPECT_EQ(0, int(reads[0].block));
            if constexpr (std::is_same_v<ReadStruct, ReadInfoWithUid>) {
                if (features() & Features::v2) {
                    EXPECT_NE(kIncFsNoUid, int(reads[0].uid));
                } else {
                    EXPECT_EQ(kIncFsNoUid, int(reads[0].uid));
                }
            }
        });

        const auto file_path = mountPath(test_file_name_);
        const android::base::unique_fd readFd(
                open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
        ASSERT_TRUE(readFd >= 0);
        char buf[INCFS_DATA_FILE_BLOCK_SIZE];
        ASSERT_TRUE(android::base::ReadFully(readFd, buf, sizeof(buf)));
        wait_page_read_thread.join();
    }

    template <class PendingRead>
    void testWaitForPendingReads() {
        const auto id = fileId(1);
        ASSERT_EQ(0,
                  makeFile(control_, mountPath(test_file_name_), 0555, id,
                           {.size = test_file_size_}));

        std::thread wait_pending_read_thread([&]() {
            std::vector<PendingRead> pending_reads;
            ASSERT_EQ(WaitResult::HaveData,
                      waitForPendingReads(control_, std::chrono::seconds(10), &pending_reads));
            ASSERT_GT(pending_reads.size(), 0u);
            EXPECT_EQ(0, memcmp(&id, &pending_reads[0].id, sizeof(id)));
            EXPECT_EQ(0, (int)pending_reads[0].block);
            if constexpr (std::is_same_v<PendingRead, ReadInfoWithUid>) {
                if (features() & Features::v2) {
                    EXPECT_NE(kIncFsNoUid, int(pending_reads[0].uid));
                } else {
                    EXPECT_EQ(kIncFsNoUid, int(pending_reads[0].uid));
                }
            }

            auto fd = openForSpecialOps(control_, fileId(1));
            ASSERT_GE(fd.get(), 0);

            std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
            auto block = DataBlock{
                    .fileFd = fd.get(),
                    .pageIndex = 0,
                    .compression = INCFS_COMPRESSION_KIND_NONE,
                    .dataSize = (uint32_t)data.size(),
                    .data = data.data(),
            };
            ASSERT_EQ(1, writeBlocks({&block, 1}));
        });

        const auto file_path = mountPath(test_file_name_);
        const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
        ASSERT_GE(fd.get(), 0);
        char buf[INCFS_DATA_FILE_BLOCK_SIZE];
        ASSERT_TRUE(android::base::ReadFully(fd, buf, sizeof(buf)));
        wait_pending_read_thread.join();
    }

    inline static const int test_file_size_ = INCFS_DATA_FILE_BLOCK_SIZE;
};

TEST_F(IncFsTest, GetIncfsFeatures) {
    ASSERT_NE(features(), none);
}

TEST_F(IncFsTest, FalseIncfsPath) {
    TemporaryDir test_dir;
    ASSERT_FALSE(isIncFsPath(test_dir.path));
}

TEST_F(IncFsTest, TrueIncfsPath) {
    ASSERT_TRUE(isIncFsPath(mount_dir_path_));
}

TEST_F(IncFsTest, TrueIncfsPathForBindMount) {
    TemporaryDir tmp_dir_to_bind;
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));
    ASSERT_EQ(0, bindMount(mountPath(test_dir_name_), tmp_dir_to_bind.path));
    ScopedUnmount su(tmp_dir_to_bind.path);
    ASSERT_TRUE(isIncFsPath(tmp_dir_to_bind.path));
}

TEST_F(IncFsTest, FalseIncfsPathFile) {
    TemporaryFile test_file;
    ASSERT_FALSE(isIncFsFd(test_file.fd));
    ASSERT_FALSE(isIncFsPath(test_file.path));
}

TEST_F(IncFsTest, TrueIncfsPathForBindMountFile) {
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, fileId(1),
                       {.size = test_file_size_}));
    const auto file_path = mountPath(test_file_name_);
    const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    ASSERT_GE(fd.get(), 0);
    ASSERT_TRUE(isIncFsFd(fd.get()));
    ASSERT_TRUE(isIncFsPath(file_path));
}

TEST_F(IncFsTest, Control) {
    ASSERT_TRUE(control_);
    EXPECT_GE(IncFs_GetControlFd(control_, CMD), 0);
    EXPECT_GE(IncFs_GetControlFd(control_, PENDING_READS), 0);
    EXPECT_GE(IncFs_GetControlFd(control_, LOGS), 0);
    EXPECT_EQ((features() & Features::v2) != 0, IncFs_GetControlFd(control_, BLOCKS_WRITTEN) >= 0);

    auto fds = control_.releaseFds();
    EXPECT_GE(fds.size(), size_t(4));
    EXPECT_GE(fds[0].get(), 0);
    EXPECT_GE(fds[1].get(), 0);
    EXPECT_GE(fds[2].get(), 0);
    ASSERT_TRUE(control_);
    EXPECT_LT(IncFs_GetControlFd(control_, CMD), 0);
    EXPECT_LT(IncFs_GetControlFd(control_, PENDING_READS), 0);
    EXPECT_LT(IncFs_GetControlFd(control_, LOGS), 0);
    EXPECT_LT(IncFs_GetControlFd(control_, BLOCKS_WRITTEN), 0);

    control_.close();
    EXPECT_FALSE(control_);

    auto control = IncFs_CreateControl(fds[0].release(), fds[1].release(), fds[2].release(), -1);
    ASSERT_TRUE(control);
    EXPECT_GE(IncFs_GetControlFd(control, CMD), 0);
    EXPECT_GE(IncFs_GetControlFd(control, PENDING_READS), 0);
    EXPECT_GE(IncFs_GetControlFd(control, LOGS), 0);
    IncFsFd rawFds[4];
    EXPECT_EQ(-EINVAL, IncFs_ReleaseControlFds(nullptr, rawFds, 3));
    EXPECT_EQ(-EINVAL, IncFs_ReleaseControlFds(control, nullptr, 3));
    EXPECT_EQ(-ERANGE, IncFs_ReleaseControlFds(control, rawFds, 2));
    EXPECT_EQ(4, IncFs_ReleaseControlFds(control, rawFds, 4));
    EXPECT_GE(rawFds[0], 0);
    EXPECT_GE(rawFds[1], 0);
    EXPECT_GE(rawFds[2], 0);
    ::close(rawFds[0]);
    ::close(rawFds[1]);
    ::close(rawFds[2]);
    if (rawFds[3] >= 0) ::close(rawFds[3]);
    IncFs_DeleteControl(control);
}

TEST_F(IncFsTest, MakeDir) {
    const auto dir_path = mountPath(test_dir_name_);
    ASSERT_FALSE(exists(dir_path));
    ASSERT_EQ(makeDir(control_, dir_path), 0);
    ASSERT_TRUE(exists(dir_path));
}

TEST_F(IncFsTest, MakeDirs) {
    const auto dir_path = mountPath(test_dir_name_);
    ASSERT_FALSE(exists(dir_path));
    ASSERT_EQ(makeDirs(control_, dir_path), 0);
    ASSERT_TRUE(exists(dir_path));
    ASSERT_EQ(makeDirs(control_, dir_path), 0);
    auto nested = dir_path + "/couple/more/nested/levels";
    ASSERT_EQ(makeDirs(control_, nested), 0);
    ASSERT_TRUE(exists(nested));
    ASSERT_NE(makeDirs(control_, "/"), 0);
}

TEST_F(IncFsTest, BindMount) {
    {
        TemporaryDir tmp_dir_to_bind;
        ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));
        ASSERT_EQ(0, bindMount(mountPath(test_dir_name_), tmp_dir_to_bind.path));
        ScopedUnmount su(tmp_dir_to_bind.path);
        const auto test_file = mountPath(test_dir_name_, test_file_name_);
        ASSERT_FALSE(exists(test_file.c_str())) << "Present: " << test_file;
        ASSERT_EQ(0,
                  makeFile(control_, test_file, 0555, fileId(1),
                           {.size = test_file_size_, .metadata = metadata("md")}));
        ASSERT_TRUE(exists(test_file.c_str())) << "Missing: " << test_file;
        const auto file_binded_path = path::join(tmp_dir_to_bind.path, test_file_name_);
        ASSERT_TRUE(exists(file_binded_path.c_str())) << "Missing: " << file_binded_path;
    }

    {
        // Don't allow binding the root
        TemporaryDir tmp_dir_to_bind;
        ASSERT_EQ(-EINVAL, bindMount(mount_dir_path_, tmp_dir_to_bind.path));
    }
}

TEST_F(IncFsTest, Root) {
    ASSERT_EQ(mount_dir_path_, root(control_)) << "Error: " << errno;
}

TEST_F(IncFsTest, RootInvalidControl) {
    const TemporaryFile tmp_file;
    auto control{createControl(tmp_file.fd, -1, -1, -1)};
    ASSERT_EQ("", root(control)) << "Error: " << errno;
}

TEST_F(IncFsTest, Open) {
    Control control = open(mount_dir_path_);
    ASSERT_TRUE(control.cmd() >= 0);
    ASSERT_TRUE(control.pendingReads() >= 0);
    ASSERT_TRUE(control.logs() >= 0);
}

TEST_F(IncFsTest, OpenFail) {
    TemporaryDir tmp_dir_to_bind;
    Control control = open(tmp_dir_to_bind.path);
    ASSERT_TRUE(control.cmd() < 0);
    ASSERT_TRUE(control.pendingReads() < 0);
    ASSERT_TRUE(control.logs() < 0);
}

TEST_F(IncFsTest, MakeFile) {
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));
    const auto file_path = mountPath(test_dir_name_, test_file_name_);
    ASSERT_FALSE(exists(file_path));
    ASSERT_EQ(0,
              makeFile(control_, file_path, 0111, fileId(1),
                       {.size = test_file_size_, .metadata = metadata("md")}));
    struct stat s;
    ASSERT_EQ(0, stat(file_path.c_str(), &s));
    ASSERT_EQ(test_file_size_, (int)s.st_size);
}

TEST_F(IncFsTest, MakeFile0) {
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));
    const auto file_path = mountPath(test_dir_name_, ".info");
    ASSERT_FALSE(exists(file_path));
    ASSERT_EQ(0,
              makeFile(control_, file_path, 0555, fileId(1),
                       {.size = 0, .metadata = metadata("mdsdfhjasdkfas l;jflaskdjf")}));
    struct stat s;
    ASSERT_EQ(0, stat(file_path.c_str(), &s));
    ASSERT_EQ(0, (int)s.st_size);
}

TEST_F(IncFsTest, MakeMappedFile) {
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));

    constexpr auto file_size = INCFS_DATA_FILE_BLOCK_SIZE * 2;
    constexpr auto mapped_file_offset = file_size / 2;
    constexpr auto mapped_file_size = file_size / 3;

    const auto file_path = mountPath(test_dir_name_, test_file_name_);
    ASSERT_FALSE(exists(file_path));
    ASSERT_EQ(0,
              makeFile(control_, file_path, 0111, fileId(1),
                       {.size = file_size, .metadata = metadata("md")}));
    struct stat s = {};
    ASSERT_EQ(0, stat(file_path.c_str(), &s));
    ASSERT_EQ(file_size, (int)s.st_size);

    const auto mapped_file_path = mountPath(test_dir_name_, test_mapped_file_name_);
    ASSERT_FALSE(exists(mapped_file_path));
    ASSERT_EQ(0,
              makeMappedFile(control_, mapped_file_path, 0111,
                             {.sourceId = fileId(1),
                              .sourceOffset = mapped_file_offset,
                              .size = mapped_file_size}));
    s = {};
    ASSERT_EQ(0, stat(mapped_file_path.c_str(), &s));
    ASSERT_EQ(mapped_file_size, (int)s.st_size);

    // Check fileId for the source file.
    ASSERT_EQ(fileId(1), getFileId(control_, file_path));
    // Check that there is no fileId for the mapped file.
    ASSERT_EQ(kIncFsInvalidFileId, getFileId(control_, mapped_file_path));
}

TEST_F(IncFsTest, GetFileId) {
    auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id,
                       {.size = test_file_size_, .metadata = metadata("md")}));
    EXPECT_EQ(id, getFileId(control_, mountPath(test_file_name_))) << "errno = " << errno;
    EXPECT_EQ(kIncFsInvalidFileId, getFileId(control_, test_file_name_));
    EXPECT_EQ(kIncFsInvalidFileId, getFileId(control_, "asdf"));
    EXPECT_EQ(kIncFsInvalidFileId, getFileId({}, mountPath(test_file_name_)));
}

TEST_F(IncFsTest, GetMetaData) {
    const std::string_view md = "abc"sv;
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, fileId(1),
                       {.size = test_file_size_, .metadata = metadata(md)}));
    {
        const auto raw_metadata = getMetadata(control_, mountPath(test_file_name_));
        ASSERT_NE(0u, raw_metadata.size()) << errno;
        const std::string result(raw_metadata.begin(), raw_metadata.end());
        ASSERT_EQ(md, result);
    }
    {
        const auto raw_metadata = getMetadata(control_, fileId(1));
        ASSERT_NE(0u, raw_metadata.size()) << errno;
        const std::string result(raw_metadata.begin(), raw_metadata.end());
        ASSERT_EQ(md, result);
    }
}

TEST_F(IncFsTest, LinkAndUnlink) {
    ASSERT_EQ(0, makeFile(control_, mountPath(test_file_name_), 0555, fileId(1), {.size = 0}));
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));
    const std::string_view test_file = "test1.txt"sv;
    const auto linked_file_path = mountPath(test_dir_name_, test_file);
    ASSERT_FALSE(exists(linked_file_path));
    ASSERT_EQ(0, link(control_, mountPath(test_file_name_), linked_file_path));
    ASSERT_TRUE(exists(linked_file_path));
    ASSERT_EQ(0, unlink(control_, linked_file_path));
    ASSERT_FALSE(exists(linked_file_path));
}

TEST_F(IncFsTest, WriteBlocksAndPageRead) {
    ASSERT_NO_FATAL_FAILURE(testWriteBlockAndPageRead<ReadInfo>());
}

TEST_F(IncFsTest, WriteBlocksAndPageReadWithUid) {
    ASSERT_NO_FATAL_FAILURE(testWriteBlockAndPageRead<ReadInfoWithUid>());
}

TEST_F(IncFsTest, WaitForPendingReads) {
    ASSERT_NO_FATAL_FAILURE(testWaitForPendingReads<ReadInfo>());
}

TEST_F(IncFsTest, WaitForPendingReadsWithUid) {
    ASSERT_NO_FATAL_FAILURE(testWaitForPendingReads<ReadInfoWithUid>());
}

TEST_F(IncFsTest, GetFilledRangesBad) {
    EXPECT_EQ(-EBADF, IncFs_GetFilledRanges(-1, {}, nullptr));
    EXPECT_EQ(-EINVAL, IncFs_GetFilledRanges(0, {}, nullptr));
    EXPECT_EQ(-EINVAL, IncFs_GetFilledRangesStartingFrom(0, -1, {}, nullptr));

    makeFileWithHash(1);
    const android::base::unique_fd readFd(
            open(mountPath(test_file_name_).c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    ASSERT_GE(readFd.get(), 0);

    char buffer[1024];
    IncFsFilledRanges res;
    EXPECT_EQ(-EPERM, IncFs_GetFilledRanges(readFd.get(), {buffer, std::size(buffer)}, &res));
}

TEST_F(IncFsTest, GetFilledRanges) {
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, fileId(1),
                       {.size = 4 * INCFS_DATA_FILE_BLOCK_SIZE}));
    char buffer[1024];
    const auto bufferSpan = IncFsSpan{.data = buffer, .size = std::size(buffer)};

    auto fd = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fd.get(), 0);

    IncFsFilledRanges filledRanges;
    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), IncFsSpan{}, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 0, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 1, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 30, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(-ENODATA, IncFs_IsFullyLoaded(fd.get()));
    EXPECT_EQ(-ENODATA, IncFs_IsEverythingFullyLoaded(control_));

    // write one block
    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fd.get(),
            .pageIndex = 0,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(1, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 0, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(1, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 1, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 30, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(-ENODATA, IncFs_IsFullyLoaded(fd.get()));
    EXPECT_EQ(-ENODATA, IncFs_IsEverythingFullyLoaded(control_));

    // append one more block next to the first one
    block.pageIndex = 1;
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 0, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 1, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(1, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 30, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(-ENODATA, IncFs_IsFullyLoaded(fd.get()));
    EXPECT_EQ(-ENODATA, IncFs_IsEverythingFullyLoaded(control_));

    // now create a gap between filled blocks
    block.pageIndex = 3;
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(2, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(3, filledRanges.dataRanges[1].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[1].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 0, bufferSpan, &filledRanges));
    ASSERT_EQ(2, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(3, filledRanges.dataRanges[1].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[1].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 1, bufferSpan, &filledRanges));
    ASSERT_EQ(2, filledRanges.dataRangesCount);
    EXPECT_EQ(1, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(2, filledRanges.dataRanges[0].end);
    EXPECT_EQ(3, filledRanges.dataRanges[1].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[1].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 2, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(3, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 30, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(-ENODATA, IncFs_IsFullyLoaded(fd.get()));
    EXPECT_EQ(-ENODATA, IncFs_IsEverythingFullyLoaded(control_));

    // at last fill the whole file and make sure we report it as having a single range
    block.pageIndex = 2;
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 0, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 1, bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(1, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(4, filledRanges.dataRanges[0].end);

    EXPECT_EQ(0, IncFs_GetFilledRangesStartingFrom(fd.get(), 30, bufferSpan, &filledRanges));
    EXPECT_EQ(0, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.hashRangesCount);

    EXPECT_EQ(0, IncFs_IsFullyLoaded(fd.get()));
    EXPECT_EQ(0, IncFs_IsEverythingFullyLoaded(control_));
}

TEST_F(IncFsTest, GetFilledRangesSmallBuffer) {
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, fileId(1),
                       {.size = 5 * INCFS_DATA_FILE_BLOCK_SIZE}));
    char buffer[1024];

    auto fd = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fd.get(), 0);

    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    DataBlock blocks[] = {DataBlock{
                                  .fileFd = fd.get(),
                                  .pageIndex = 0,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = (uint32_t)data.size(),
                                  .data = data.data(),
                          },
                          DataBlock{
                                  .fileFd = fd.get(),
                                  .pageIndex = 2,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = (uint32_t)data.size(),
                                  .data = data.data(),
                          },
                          DataBlock{
                                  .fileFd = fd.get(),
                                  .pageIndex = 4,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = (uint32_t)data.size(),
                                  .data = data.data(),
                          }};
    ASSERT_EQ(3, writeBlocks({blocks, 3}));

    IncFsSpan bufferSpan = {.data = buffer, .size = sizeof(IncFsBlockRange)};
    IncFsFilledRanges filledRanges;
    EXPECT_EQ(-ERANGE, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(0, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(1, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);
    EXPECT_EQ(2, filledRanges.endIndex);

    EXPECT_EQ(-ERANGE,
              IncFs_GetFilledRangesStartingFrom(fd.get(), filledRanges.endIndex, bufferSpan,
                                                &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(2, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(3, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);
    EXPECT_EQ(4, filledRanges.endIndex);

    EXPECT_EQ(0,
              IncFs_GetFilledRangesStartingFrom(fd.get(), filledRanges.endIndex, bufferSpan,
                                                &filledRanges));
    ASSERT_EQ(1, filledRanges.dataRangesCount);
    EXPECT_EQ(4, filledRanges.dataRanges[0].begin);
    EXPECT_EQ(5, filledRanges.dataRanges[0].end);
    EXPECT_EQ(0, filledRanges.hashRangesCount);
    EXPECT_EQ(5, filledRanges.endIndex);
}

TEST_F(IncFsTest, GetFilledRangesWithHashes) {
    auto size = makeFileWithHash(1);
    ASSERT_GT(size, 0);
    ASSERT_NO_FATAL_FAILURE(writeTestRanges(1, size));

    auto fd = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fd.get(), 0);

    char buffer[1024];
    IncFsSpan bufferSpan = {.data = buffer, .size = sizeof(buffer)};
    IncFsFilledRanges filledRanges;
    EXPECT_EQ(0, IncFs_GetFilledRanges(fd.get(), bufferSpan, &filledRanges));
    ASSERT_EQ(3, filledRanges.dataRangesCount);
    auto lastPage = sizeToPages(size) - 1;
    EXPECT_EQ(lastPage, filledRanges.dataRanges[2].begin);
    EXPECT_EQ(lastPage + 1, filledRanges.dataRanges[2].end);
    EXPECT_EQ(2, filledRanges.hashRangesCount);
    EXPECT_EQ(0, filledRanges.hashRanges[0].begin);
    EXPECT_EQ(1, filledRanges.hashRanges[0].end);
    EXPECT_EQ(2, filledRanges.hashRanges[1].begin);
    EXPECT_EQ(3, filledRanges.hashRanges[1].end);
    EXPECT_EQ(sizeToPages(size) + 3, filledRanges.endIndex);
}

TEST_F(IncFsTest, GetFilledRangesCpp) {
    auto size = makeFileWithHash(1);
    ASSERT_GT(size, 0);
    ASSERT_NO_FATAL_FAILURE(writeTestRanges(1, size));

    auto fd = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fd.get(), 0);

    // simply get all ranges
    auto [res, ranges] = getFilledRanges(fd.get());
    EXPECT_EQ(res, 0);
    EXPECT_EQ(size_t(5), ranges.totalSize());
    ASSERT_EQ(size_t(3), ranges.dataRanges().size());
    auto lastPage = sizeToPages(size) - 1;
    EXPECT_EQ(lastPage, ranges.dataRanges()[2].begin);
    EXPECT_EQ(size_t(1), ranges.dataRanges()[2].size());
    ASSERT_EQ(size_t(2), ranges.hashRanges().size());
    EXPECT_EQ(0, ranges.hashRanges()[0].begin);
    EXPECT_EQ(size_t(1), ranges.hashRanges()[0].size());
    EXPECT_EQ(2, ranges.hashRanges()[1].begin);
    EXPECT_EQ(size_t(1), ranges.hashRanges()[1].size());

    // now check how buffer size limiting works.
    FilledRanges::RangeBuffer buf(ranges.totalSize() - 1);
    auto [res2, ranges2] = getFilledRanges(fd.get(), std::move(buf));
    ASSERT_EQ(-ERANGE, res2);
    EXPECT_EQ(ranges.totalSize() - 1, ranges2.totalSize());
    ASSERT_EQ(size_t(3), ranges2.dataRanges().size());
    ASSERT_EQ(size_t(1), ranges2.hashRanges().size());
    EXPECT_EQ(0, ranges2.hashRanges()[0].begin);
    EXPECT_EQ(size_t(1), ranges2.hashRanges()[0].size());

    // and now check the resumption from the previous result
    auto [res3, ranges3] = getFilledRanges(fd.get(), std::move(ranges2));
    ASSERT_EQ(0, res3);
    EXPECT_EQ(ranges.totalSize(), ranges3.totalSize());
    ASSERT_EQ(size_t(3), ranges3.dataRanges().size());
    ASSERT_EQ(size_t(2), ranges3.hashRanges().size());
    EXPECT_EQ(0, ranges3.hashRanges()[0].begin);
    EXPECT_EQ(size_t(1), ranges3.hashRanges()[0].size());
    EXPECT_EQ(2, ranges3.hashRanges()[1].begin);
    EXPECT_EQ(size_t(1), ranges3.hashRanges()[1].size());

    EXPECT_EQ(LoadingState::MissingBlocks, isFullyLoaded(fd.get()));
    EXPECT_EQ(LoadingState::MissingBlocks, isEverythingFullyLoaded(control_));

    {
        std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
        DataBlock block = {.fileFd = fd.get(),
                           .pageIndex = 1,
                           .compression = INCFS_COMPRESSION_KIND_NONE,
                           .dataSize = (uint32_t)data.size(),
                           .data = data.data()};
        for (auto i = 0; i != sizeToPages(size); ++i) {
            block.pageIndex = i;
            ASSERT_EQ(1, writeBlocks({&block, 1}));
        }
        block.kind = INCFS_BLOCK_KIND_HASH;
        for (auto i = 0; i != 3; ++i) {
            block.pageIndex = i;
            ASSERT_EQ(1, writeBlocks({&block, 1}));
        }
    }
    EXPECT_EQ(LoadingState::Full, isFullyLoaded(fd.get()));
    EXPECT_EQ(LoadingState::Full, isEverythingFullyLoaded(control_));
}

TEST_F(IncFsTest, BlocksWritten) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    const auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = test_file_size_}));

    IncFsSize blocksWritten = 0;
    ASSERT_EQ(0, IncFs_WaitForFsWrittenBlocksChange(control_, 0, &blocksWritten));
    EXPECT_EQ(0, blocksWritten);

    auto fd = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fd.get(), 0);

    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fd.get(),
            .pageIndex = 0,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    ASSERT_EQ(0, IncFs_WaitForFsWrittenBlocksChange(control_, 0, &blocksWritten));
    EXPECT_EQ(1, blocksWritten);
}

TEST_F(IncFsTest, Timeouts) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    IncFsUidReadTimeouts timeouts[2] = {{1, 1000, 2000, 3000}, {2, 1000, 3000, 4000}};

    EXPECT_EQ(0, IncFs_SetUidReadTimeouts(control_, timeouts, std::size(timeouts)));

    IncFsUidReadTimeouts outTimeouts[3];

    size_t outSize = 1;
    EXPECT_EQ(-E2BIG, IncFs_GetUidReadTimeouts(control_, outTimeouts, &outSize));
    EXPECT_EQ(size_t(2), outSize);

    outSize = 3;
    EXPECT_EQ(0, IncFs_GetUidReadTimeouts(control_, outTimeouts, &outSize));
    EXPECT_EQ(size_t(2), outSize);

    EXPECT_EQ(0, memcmp(timeouts, outTimeouts, 2 * sizeof(timeouts[0])));
}

TEST_F(IncFsTest, CompletionNoFiles) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    size_t count = 0;
    EXPECT_EQ(0, IncFs_ListIncompleteFiles(control_, nullptr, &count));
    EXPECT_EQ(size_t(0), count);
    EXPECT_EQ(0, IncFs_WaitForLoadingComplete(control_, 0));
}

TEST_F(IncFsTest, CompletionOneFile) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    const auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = test_file_size_}));

    size_t count = 0;
    EXPECT_EQ(-E2BIG, IncFs_ListIncompleteFiles(control_, nullptr, &count));
    EXPECT_EQ(size_t(1), count);
    EXPECT_EQ(-ETIMEDOUT, IncFs_WaitForLoadingComplete(control_, 0));

    IncFsFileId ids[2];
    count = 2;
    EXPECT_EQ(0, IncFs_ListIncompleteFiles(control_, ids, &count));
    EXPECT_EQ(size_t(1), count);
    EXPECT_EQ(id, ids[0]);

    auto fd = openForSpecialOps(control_, id);
    ASSERT_GE(fd.get(), 0);
    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fd.get(),
            .pageIndex = 0,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    count = 2;
    EXPECT_EQ(0, IncFs_ListIncompleteFiles(control_, ids, &count));
    EXPECT_EQ(size_t(0), count);
    EXPECT_EQ(0, IncFs_WaitForLoadingComplete(control_, 0));
}

TEST_F(IncFsTest, CompletionMultiple) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    const auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = test_file_size_}));

    size_t count = 0;
    EXPECT_EQ(-E2BIG, IncFs_ListIncompleteFiles(control_, nullptr, &count));
    EXPECT_EQ(size_t(1), count);
    EXPECT_EQ(-ETIMEDOUT, IncFs_WaitForLoadingComplete(control_, 0));

    // fill the existing file but add another one
    const auto id2 = fileId(2);
    ASSERT_EQ(0, makeFile(control_, mountPath("test2"), 0555, id2, {.size = test_file_size_}));

    IncFsFileId ids[2];
    count = 2;
    EXPECT_EQ(0, IncFs_ListIncompleteFiles(control_, ids, &count));
    EXPECT_EQ(size_t(2), count);
    EXPECT_EQ(id, ids[0]);
    EXPECT_EQ(id2, ids[1]);

    auto fd = openForSpecialOps(control_, id);
    ASSERT_GE(fd.get(), 0);
    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fd.get(),
            .pageIndex = 0,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    count = 2;
    EXPECT_EQ(0, IncFs_ListIncompleteFiles(control_, ids, &count));
    EXPECT_EQ(size_t(1), count);
    EXPECT_EQ(id2, ids[0]);
    EXPECT_EQ(-ETIMEDOUT, IncFs_WaitForLoadingComplete(control_, 0));
}

TEST_F(IncFsTest, CompletionWait) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    ASSERT_EQ(0,
              makeFile(control_, mountPath("test1"), 0555, fileId(1),
                       {.size = INCFS_DATA_FILE_BLOCK_SIZE}));
    ASSERT_EQ(0,
              makeFile(control_, mountPath("test2"), 0555, fileId(2),
                       {.size = INCFS_DATA_FILE_BLOCK_SIZE}));
    ASSERT_EQ(0,
              makeFile(control_, mountPath("test3"), 0555, fileId(3),
                       {.size = INCFS_DATA_FILE_BLOCK_SIZE}));

    std::atomic<int> res = -1;
    auto waiter = std::thread([&] { res = IncFs_WaitForLoadingComplete(control_, 5 * 1000); });

    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);

    {
        auto fd = openForSpecialOps(control_, fileId(1));
        ASSERT_GE(fd.get(), 0);
        auto block = DataBlock{
                .fileFd = fd.get(),
                .pageIndex = 0,
                .compression = INCFS_COMPRESSION_KIND_NONE,
                .dataSize = (uint32_t)data.size(),
                .data = data.data(),
        };
        ASSERT_EQ(1, writeBlocks({&block, 1}));
    }
    ASSERT_TRUE(res == -1);

    {
        auto fd = openForSpecialOps(control_, fileId(3));
        ASSERT_GE(fd.get(), 0);
        auto block = DataBlock{
                .fileFd = fd.get(),
                .pageIndex = 0,
                .compression = INCFS_COMPRESSION_KIND_NONE,
                .dataSize = (uint32_t)data.size(),
                .data = data.data(),
        };
        ASSERT_EQ(1, writeBlocks({&block, 1}));
    }
    ASSERT_TRUE(res == -1);

    {
        auto fd = openForSpecialOps(control_, fileId(2));
        ASSERT_GE(fd.get(), 0);
        auto block = DataBlock{
                .fileFd = fd.get(),
                .pageIndex = 0,
                .compression = INCFS_COMPRESSION_KIND_NONE,
                .dataSize = (uint32_t)data.size(),
                .data = data.data(),
        };
        ASSERT_EQ(1, writeBlocks({&block, 1}));
    }

    waiter.join();

    auto listIncomplete = [&] {
        IncFsFileId ids[3];
        size_t count = 3;
        if (IncFs_ListIncompleteFiles(control_, ids, &count) != 0) {
            return "error listing incomplete files"s;
        }
        auto res = ab::StringPrintf("[%d]", int(count));
        for (size_t i = 0; i < count; ++i) {
            ab::StringAppendF(&res, " %s", toString(ids[i]).c_str());
        }
        return res;
    };
    EXPECT_EQ(0, res) << "Incomplete files: " << listIncomplete();
}

TEST_F(IncFsTest, GetBlockCounts) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    const auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id,
                       {.size = 20 * INCFS_DATA_FILE_BLOCK_SIZE + 3}));

    IncFsBlockCounts counts = {};
    EXPECT_EQ(0,
              IncFs_GetFileBlockCountByPath(control_, mountPath(test_file_name_).c_str(), &counts));
    EXPECT_EQ(21, counts.totalDataBlocks);
    EXPECT_EQ(0, counts.filledDataBlocks);
    EXPECT_EQ(0, counts.totalHashBlocks);
    EXPECT_EQ(0, counts.filledHashBlocks);

    EXPECT_EQ(0, IncFs_GetFileBlockCountById(control_, id, &counts));
    EXPECT_EQ(21, counts.totalDataBlocks);
    EXPECT_EQ(0, counts.filledDataBlocks);
    EXPECT_EQ(0, counts.totalHashBlocks);
    EXPECT_EQ(0, counts.filledHashBlocks);

    auto fd = openForSpecialOps(control_, id);
    ASSERT_GE(fd.get(), 0);
    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fd.get(),
            .pageIndex = 3,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    EXPECT_EQ(0,
              IncFs_GetFileBlockCountByPath(control_, mountPath(test_file_name_).c_str(), &counts));
    EXPECT_EQ(21, counts.totalDataBlocks);
    EXPECT_EQ(1, counts.filledDataBlocks);
    EXPECT_EQ(0, counts.totalHashBlocks);
    EXPECT_EQ(0, counts.filledHashBlocks);

    EXPECT_EQ(0, IncFs_GetFileBlockCountById(control_, id, &counts));
    EXPECT_EQ(21, counts.totalDataBlocks);
    EXPECT_EQ(1, counts.filledDataBlocks);
    EXPECT_EQ(0, counts.totalHashBlocks);
    EXPECT_EQ(0, counts.filledHashBlocks);
}

TEST_F(IncFsTest, GetBlockCountsHash) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }

    auto size = makeFileWithHash(1);
    ASSERT_GT(size, 0);

    IncFsBlockCounts counts = {};
    EXPECT_EQ(0,
              IncFs_GetFileBlockCountByPath(control_, mountPath(test_file_name_).c_str(), &counts));
    EXPECT_EQ(sizeToPages(size), counts.totalDataBlocks);
    EXPECT_EQ(0, counts.filledDataBlocks);
    EXPECT_EQ(3, counts.totalHashBlocks);
    EXPECT_EQ(0, counts.filledHashBlocks);

    ASSERT_NO_FATAL_FAILURE(writeTestRanges(1, size));

    EXPECT_EQ(0,
              IncFs_GetFileBlockCountByPath(control_, mountPath(test_file_name_).c_str(), &counts));
    EXPECT_EQ(sizeToPages(size), counts.totalDataBlocks);
    EXPECT_EQ(4, counts.filledDataBlocks);
    EXPECT_EQ(3, counts.totalHashBlocks);
    EXPECT_EQ(2, counts.filledHashBlocks);
}

TEST_F(IncFsTest, ReserveSpace) {
    auto size = makeFileWithHash(1);
    ASSERT_GT(size, 0);

    EXPECT_EQ(-ENOENT,
              IncFs_ReserveSpaceByPath(control_, mountPath("1"s += test_file_name_).c_str(), size));
    EXPECT_EQ(0, IncFs_ReserveSpaceByPath(control_, mountPath(test_file_name_).c_str(), size));
    EXPECT_EQ(0, IncFs_ReserveSpaceByPath(control_, mountPath(test_file_name_).c_str(), 2 * size));
    EXPECT_EQ(0, IncFs_ReserveSpaceByPath(control_, mountPath(test_file_name_).c_str(), 2 * size));
    EXPECT_EQ(0,
              IncFs_ReserveSpaceByPath(control_, mountPath(test_file_name_).c_str(),
                                       kTrimReservedSpace));
    EXPECT_EQ(0,
              IncFs_ReserveSpaceByPath(control_, mountPath(test_file_name_).c_str(),
                                       kTrimReservedSpace));

    EXPECT_EQ(-ENOENT, IncFs_ReserveSpaceById(control_, fileId(2), size));
    EXPECT_EQ(0, IncFs_ReserveSpaceById(control_, fileId(1), size));
    EXPECT_EQ(0, IncFs_ReserveSpaceById(control_, fileId(1), 2 * size));
    EXPECT_EQ(0, IncFs_ReserveSpaceById(control_, fileId(1), 2 * size));
    EXPECT_EQ(0, IncFs_ReserveSpaceById(control_, fileId(1), kTrimReservedSpace));
    EXPECT_EQ(0, IncFs_ReserveSpaceById(control_, fileId(1), kTrimReservedSpace));
}

TEST_F(IncFsTest, ForEachFile) {
    const auto incompleteSupported = (features() & Features::v2) != 0;
    EXPECT_EQ(-EINVAL, IncFs_ForEachFile(nullptr, nullptr, nullptr));
    EXPECT_EQ(-EINVAL, IncFs_ForEachIncompleteFile(nullptr, nullptr, nullptr));
    EXPECT_EQ(-EINVAL, IncFs_ForEachFile(control_, nullptr, nullptr));
    EXPECT_EQ(-EINVAL, IncFs_ForEachIncompleteFile(control_, nullptr, nullptr));
    EXPECT_EQ(0, IncFs_ForEachFile(control_, nullptr, [](auto, auto, auto) { return true; }));
    EXPECT_EQ(incompleteSupported ? 0 : -ENOTSUP,
              IncFs_ForEachIncompleteFile(control_, nullptr,
                                          [](auto, auto, auto) { return true; }));
    EXPECT_EQ(0, IncFs_ForEachFile(control_, this, [](auto, auto, auto) { return true; }));
    EXPECT_EQ(incompleteSupported ? 0 : -ENOTSUP,
              IncFs_ForEachIncompleteFile(control_, this, [](auto, auto, auto) { return true; }));

    int res = makeFile(control_, mountPath("incomplete.txt"), 0555, fileId(1),
                       {.metadata = metadata("md")});
    ASSERT_EQ(res, 0);

    EXPECT_EQ(1, IncFs_ForEachFile(control_, this, [](auto, auto context, auto id) {
                  auto self = (IncFsTest*)context;
                  EXPECT_EQ(self->fileId(1), id);
                  return true;
              }));
    EXPECT_EQ(incompleteSupported ? 0 : -ENOTSUP,
              IncFs_ForEachIncompleteFile(control_, this, [](auto, auto, auto) { return true; }));

    auto size = makeFileWithHash(2);
    ASSERT_GT(size, 0);

    EXPECT_EQ(1, IncFs_ForEachFile(control_, this, [](auto, auto context, auto id) {
                  auto self = (IncFsTest*)context;
                  EXPECT_TRUE(id == self->fileId(1) || id == self->fileId(2));
                  return false;
              }));
    EXPECT_EQ(2, IncFs_ForEachFile(control_, this, [](auto, auto context, auto id) {
                  auto self = (IncFsTest*)context;
                  EXPECT_TRUE(id == self->fileId(1) || id == self->fileId(2));
                  return true;
              }));
    EXPECT_EQ(incompleteSupported ? 1 : -ENOTSUP,
              IncFs_ForEachIncompleteFile(control_, this, [](auto, auto context, auto id) {
                  auto self = (IncFsTest*)context;
                  EXPECT_EQ(self->fileId(2), id);
                  return true;
              }));
}

TEST(CStrWrapperTest, EmptyStringView) {
    ASSERT_STREQ("", details::c_str({}).get());
    ASSERT_STREQ("", details::c_str({nullptr, 0}).get());
}

class IncFsGetMetricsTest : public IncFsTestBase {
protected:
    int32_t getReadTimeout() override { return 100 /* 0.1 second */; }
};

TEST_F(IncFsGetMetricsTest, MetricsWithNoEvents) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    IncFsLastReadError lastReadError = {.id = fileId(-1),
                                        .timestampUs = static_cast<uint64_t>(-1),
                                        .block = static_cast<IncFsBlockIndex>(-1),
                                        .errorNo = static_cast<uint32_t>(-1),
                                        .uid = static_cast<IncFsUid>(-1)};
    EXPECT_EQ(0, IncFs_GetLastReadError(control_, &lastReadError));
    // All fields should be zero
    EXPECT_EQ(FileId{}, lastReadError.id);
    EXPECT_EQ(0, (int)lastReadError.timestampUs);
    EXPECT_EQ(0, (int)lastReadError.block);
    EXPECT_EQ(0, (int)lastReadError.errorNo);
    EXPECT_EQ(0, (int)lastReadError.uid);

    IncFsMetrics incfsMetrics = {10, 10, 10, 10, 10, 10, 10, 10, 10};
    EXPECT_EQ(0, IncFs_GetMetrics(metrics_key_.c_str(), &incfsMetrics));
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMin);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMinUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPending);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPendingUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedHashVerification);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedOther);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedTimedOut);
}

TEST_F(IncFsGetMetricsTest, MetricsWithReadsTimeOut) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    const auto id = fileId(1);
    ASSERT_EQ(0,
              makeFile(control_, mountPath(test_file_name_), 0555, id,
                       {.size = INCFS_DATA_FILE_BLOCK_SIZE}));

    const auto file_path = mountPath(test_file_name_);
    const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    ASSERT_GE(fd.get(), 0);
    // Read should timeout immediately
    char buf[INCFS_DATA_FILE_BLOCK_SIZE];
    EXPECT_FALSE(android::base::ReadFully(fd, buf, sizeof(buf)));
    IncFsLastReadError lastReadError = {.id = fileId(-1),
                                        .timestampUs = static_cast<uint64_t>(-1),
                                        .block = static_cast<IncFsBlockIndex>(-1),
                                        .errorNo = static_cast<uint32_t>(-1),
                                        .uid = static_cast<IncFsUid>(-1)};
    EXPECT_EQ(0, IncFs_GetLastReadError(control_, &lastReadError));
    EXPECT_EQ(id, lastReadError.id);
    EXPECT_TRUE(lastReadError.timestampUs > 0);
    EXPECT_EQ(0, (int)lastReadError.block);
    EXPECT_EQ(-ETIME, (int)lastReadError.errorNo);
    EXPECT_EQ((int)getuid(), (int)lastReadError.uid);

    IncFsMetrics incfsMetrics = {10, 10, 10, 10, 10, 10, 10, 10, 10};
    EXPECT_EQ(0, IncFs_GetMetrics(metrics_key_.c_str(), &incfsMetrics));
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMin);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMinUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPending);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPendingUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedHashVerification);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedOther);
    EXPECT_EQ(1, (int)incfsMetrics.readsFailedTimedOut);
}

TEST_F(IncFsGetMetricsTest, MetricsWithHashFailure) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    auto size = makeFileWithHash(1);
    ASSERT_GT(size, 0);
    // Make data and hash mismatch
    const auto id = fileId(1);
    char data[INCFS_DATA_FILE_BLOCK_SIZE]{static_cast<char>(-1)};
    char hashData[INCFS_DATA_FILE_BLOCK_SIZE]{};
    auto wfd = openForSpecialOps(control_, id);
    ASSERT_GE(wfd.get(), 0);
    DataBlock blocks[] = {{
                                  .fileFd = wfd.get(),
                                  .pageIndex = 0,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = INCFS_DATA_FILE_BLOCK_SIZE,
                                  .data = data,
                          },
                          {
                                  .fileFd = wfd.get(),
                                  // first hash page
                                  .pageIndex = 0,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = INCFS_DATA_FILE_BLOCK_SIZE,
                                  .kind = INCFS_BLOCK_KIND_HASH,
                                  .data = hashData,
                          },
                          {
                                  .fileFd = wfd.get(),
                                  .pageIndex = 2,
                                  .compression = INCFS_COMPRESSION_KIND_NONE,
                                  .dataSize = INCFS_DATA_FILE_BLOCK_SIZE,
                                  .kind = INCFS_BLOCK_KIND_HASH,
                                  .data = hashData,
                          }};
    ASSERT_EQ((int)std::size(blocks), writeBlocks({blocks, std::size(blocks)}));
    const auto file_path = mountPath(test_file_name_);
    const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    ASSERT_GE(fd.get(), 0);
    // Read should fail at reading the first block due to hash failure
    char buf[INCFS_DATA_FILE_BLOCK_SIZE];
    EXPECT_FALSE(android::base::ReadFully(fd, buf, sizeof(buf)));
    IncFsLastReadError lastReadError = {.id = fileId(-1),
                                        .timestampUs = static_cast<uint64_t>(-1),
                                        .block = static_cast<IncFsBlockIndex>(-1),
                                        .errorNo = static_cast<uint32_t>(-1),
                                        .uid = static_cast<IncFsUid>(-1)};
    EXPECT_EQ(0, IncFs_GetLastReadError(control_, &lastReadError));
    EXPECT_EQ(0, std::strcmp(lastReadError.id.data, id.data));
    EXPECT_TRUE(lastReadError.timestampUs > 0);
    EXPECT_EQ(0, (int)lastReadError.block);
    EXPECT_EQ(-EBADMSG, (int)lastReadError.errorNo);
    EXPECT_EQ((int)getuid(), (int)lastReadError.uid);

    IncFsMetrics incfsMetrics = {10, 10, 10, 10, 10, 10, 10, 10, 10};
    EXPECT_EQ(0, IncFs_GetMetrics(metrics_key_.c_str(), &incfsMetrics));
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMin);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMinUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPending);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPendingUs);
    EXPECT_EQ(1, (int)incfsMetrics.readsFailedHashVerification);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedOther);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedTimedOut);
}

TEST_F(IncFsGetMetricsTest, MetricsWithReadsDelayed) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    const auto id = fileId(1);
    int testFileSize = INCFS_DATA_FILE_BLOCK_SIZE;
    int waitBeforeWriteUs = 10000;
    ASSERT_EQ(0, makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = testFileSize}));
    std::thread wait_before_write_thread([&]() {
        std::vector<ReadInfoWithUid> pending_reads;
        ASSERT_EQ(WaitResult::HaveData,
                  waitForPendingReads(control_, std::chrono::seconds(1), &pending_reads));
        // Additional wait is needed for the kernel jiffies counter to increment
        usleep(waitBeforeWriteUs);
        auto fd = openForSpecialOps(control_, fileId(1));
        ASSERT_GE(fd.get(), 0);
        std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
        auto block = DataBlock{
                .fileFd = fd.get(),
                .pageIndex = 0,
                .compression = INCFS_COMPRESSION_KIND_NONE,
                .dataSize = (uint32_t)data.size(),
                .data = data.data(),
        };
        ASSERT_EQ(1, writeBlocks({&block, 1}));
    });

    const auto file_path = mountPath(test_file_name_);
    const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    ASSERT_GE(fd.get(), 0);
    char buf[testFileSize];
    EXPECT_TRUE(android::base::ReadFully(fd, buf, sizeof(buf)));
    wait_before_write_thread.join();

    IncFsLastReadError lastReadError = {.id = fileId(-1), 1, 1, 1, 1};
    EXPECT_EQ(0, IncFs_GetLastReadError(control_, &lastReadError));
    EXPECT_EQ(FileId{}, lastReadError.id);
    EXPECT_EQ(0, (int)lastReadError.timestampUs);
    EXPECT_EQ(0, (int)lastReadError.block);
    EXPECT_EQ(0, (int)lastReadError.errorNo);
    EXPECT_EQ(0, (int)lastReadError.uid);

    IncFsMetrics incfsMetrics = {10, 10, 10, 10, 10, 10, 10, 10, 10};
    EXPECT_EQ(0, IncFs_GetMetrics(metrics_key_.c_str(), &incfsMetrics));
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMin);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedMinUs);
    EXPECT_EQ(1, (int)incfsMetrics.readsDelayedPending);
    EXPECT_TRUE((int)incfsMetrics.readsDelayedPendingUs > 0);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedHashVerification);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedOther);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedTimedOut);
}

TEST_F(IncFsGetMetricsTest, MetricsWithReadsDelayedPerUidTimeout) {
    if (!(features() & Features::v2)) {
        GTEST_SKIP() << "test not supported: IncFS is too old";
        return;
    }
    const auto id = fileId(1);
    int testFileSize = INCFS_DATA_FILE_BLOCK_SIZE;
    ASSERT_EQ(0, makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = testFileSize}));

    auto fdToFill = openForSpecialOps(control_, fileId(1));
    ASSERT_GE(fdToFill.get(), 0);
    std::vector<char> data(INCFS_DATA_FILE_BLOCK_SIZE);
    auto block = DataBlock{
            .fileFd = fdToFill.get(),
            .pageIndex = 0,
            .compression = INCFS_COMPRESSION_KIND_NONE,
            .dataSize = (uint32_t)data.size(),
            .data = data.data(),
    };
    ASSERT_EQ(1, writeBlocks({&block, 1}));

    // Set per-uid read timeout then read
    uint32_t readTimeoutUs = 1000000;
    IncFsUidReadTimeouts timeouts[1] = {
            {static_cast<IncFsUid>(getuid()), readTimeoutUs, readTimeoutUs, readTimeoutUs}};
    ASSERT_EQ(0, IncFs_SetUidReadTimeouts(control_, timeouts, std::size(timeouts)));
    const auto file_path = mountPath(test_file_name_);
    const android::base::unique_fd fd(open(file_path.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    char buf[testFileSize];
    ASSERT_GE(fd.get(), 0);
    ASSERT_TRUE(android::base::ReadFully(fd, buf, sizeof(buf)));

    IncFsLastReadError lastReadError = {.id = fileId(-1), 1, 1, 1, 1};
    EXPECT_EQ(0, IncFs_GetLastReadError(control_, &lastReadError));
    EXPECT_EQ(FileId{}, lastReadError.id);
    EXPECT_EQ(0, (int)lastReadError.timestampUs);
    EXPECT_EQ(0, (int)lastReadError.block);
    EXPECT_EQ(0, (int)lastReadError.errorNo);
    EXPECT_EQ(0, (int)lastReadError.uid);

    IncFsMetrics incfsMetrics = {10, 10, 10, 10, 10, 10, 10, 10, 10};
    EXPECT_EQ(0, IncFs_GetMetrics(metrics_key_.c_str(), &incfsMetrics));
    EXPECT_EQ(1, (int)incfsMetrics.readsDelayedMin);
    EXPECT_EQ(readTimeoutUs, (uint32_t)incfsMetrics.readsDelayedMinUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPending);
    EXPECT_EQ(0, (int)incfsMetrics.readsDelayedPendingUs);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedHashVerification);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedOther);
    EXPECT_EQ(0, (int)incfsMetrics.readsFailedTimedOut);
}

inline bool operator==(const BlockCounts& lhs, const BlockCounts& rhs) {
    return lhs.totalDataBlocks == rhs.totalDataBlocks &&
            lhs.filledDataBlocks == rhs.filledDataBlocks &&
            lhs.totalHashBlocks == rhs.totalHashBlocks &&
            lhs.filledHashBlocks == rhs.filledHashBlocks;
}

TEST_F(IncFsTest, LoadingProgress) {
    ASSERT_EQ(0, makeDir(control_, mountPath(test_dir_name_)));

    constexpr auto file_size = INCFS_DATA_FILE_BLOCK_SIZE * 2;
    constexpr auto mapped_file_offset = file_size / 2;
    constexpr auto mapped_file_size = file_size / 3;

    const auto file_id = fileId(1);

    const auto file_path = mountPath(test_dir_name_, test_file_name_);
    ASSERT_FALSE(exists(file_path));
    ASSERT_EQ(0,
              makeFile(control_, file_path, 0111, file_id,
                       {.size = file_size, .metadata = metadata("md")}));
    struct stat s = {};
    ASSERT_EQ(0, stat(file_path.c_str(), &s));
    ASSERT_EQ(file_size, (int)s.st_size);

    const auto mapped_file_path = mountPath(test_dir_name_, test_mapped_file_name_);
    ASSERT_FALSE(exists(mapped_file_path));
    ASSERT_EQ(0,
              makeMappedFile(control_, mapped_file_path, 0111,
                             {.sourceId = file_id,
                              .sourceOffset = mapped_file_offset,
                              .size = mapped_file_size}));
    s = {};
    ASSERT_EQ(0, stat(mapped_file_path.c_str(), &s));
    ASSERT_EQ(mapped_file_size, (int)s.st_size);

    // Check fully loaded first.
    ASSERT_EQ(LoadingState::MissingBlocks, isFullyLoaded(control_, file_path));
    ASSERT_EQ(LoadingState::MissingBlocks, isFullyLoaded(control_, file_id));
    ASSERT_EQ((LoadingState)-ENOTSUP, isFullyLoaded(control_, mapped_file_path));

    // Next is loading progress.
    ASSERT_EQ(BlockCounts{.totalDataBlocks = 2}, *getBlockCount(control_, file_path));
    ASSERT_EQ(BlockCounts{.totalDataBlocks = 2}, *getBlockCount(control_, file_id));
    ASSERT_FALSE(getBlockCount(control_, mapped_file_path));

    // Now write a page #0.
    ASSERT_NO_FATAL_FAILURE(writeBlock(0));

    // Recheck everything.
    ASSERT_EQ(LoadingState::MissingBlocks, isFullyLoaded(control_, file_path));
    ASSERT_EQ(LoadingState::MissingBlocks, isFullyLoaded(control_, file_id));
    ASSERT_EQ((LoadingState)-ENOTSUP, isFullyLoaded(control_, mapped_file_path));

    BlockCounts onePage{.totalDataBlocks = 2, .filledDataBlocks = 1};
    ASSERT_EQ(onePage, *getBlockCount(control_, file_path));
    ASSERT_EQ(onePage, *getBlockCount(control_, file_id));
    ASSERT_FALSE(getBlockCount(control_, mapped_file_path));

    // Now write a page #1.
    ASSERT_NO_FATAL_FAILURE(writeBlock(1));

    // Check for fully loaded.
    ASSERT_EQ(LoadingState::Full, isFullyLoaded(control_, file_path));
    ASSERT_EQ(LoadingState::Full, isFullyLoaded(control_, file_id));
    ASSERT_EQ((LoadingState)-ENOTSUP, isFullyLoaded(control_, mapped_file_path));

    BlockCounts twoPages{.totalDataBlocks = 2, .filledDataBlocks = 2};
    ASSERT_EQ(twoPages, *getBlockCount(control_, file_path));
    ASSERT_EQ(twoPages, *getBlockCount(control_, file_id));
    ASSERT_FALSE(getBlockCount(control_, mapped_file_path));
}
