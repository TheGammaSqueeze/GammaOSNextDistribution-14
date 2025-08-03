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

#include <android-base/file.h>
#include <sys/select.h>

#include <setjmp.h>
#include <unistd.h>

#include "IncFsTestBase.h"

#include "util/map_ptr.h"

using namespace android::incfs;
using namespace std::literals;

constexpr int FILE_PAGES = 5U;
constexpr int FILE_SIZE = INCFS_DATA_FILE_BLOCK_SIZE * FILE_PAGES;
constexpr int FILE_MISSING_PAGE = 3U;

class MapPtrTest : public IncFsTestBase {
protected:
    virtual void SetUp() override {
        IncFsTestBase::SetUp();

        const auto id = fileId(1);
        ASSERT_TRUE(control_.logs() >= 0);
        ASSERT_EQ(0, makeFile(control_, mountPath(test_file_name_), 0555, id, {.size = FILE_SIZE}));
        auto fd = openForSpecialOps(control_, fileId(1));
        ASSERT_GE(fd.get(), 0);

        // Generate the file data.
        std::vector<uint32_t> data(FILE_SIZE);
        for (int i = 0; i < FILE_SIZE; i++) {
            data[i] = i;
        }

        // Write the file, but leave one page missing.
        for (int p = 0; p < FILE_PAGES; p++) {
            if (p == FILE_MISSING_PAGE) {
                continue;
            }
            auto block = DataBlock{
                    .fileFd = fd.get(),
                    .pageIndex = p,
                    .compression = INCFS_COMPRESSION_KIND_NONE,
                    .dataSize = (uint32_t)INCFS_DATA_FILE_BLOCK_SIZE,
                    .data = reinterpret_cast<const char *>(data.data()) +
                            INCFS_DATA_FILE_BLOCK_SIZE * p,
            };
            ASSERT_EQ(1, writeBlocks({&block, 1}));
        }

        mount_path_ = mountPath(test_file_name_);
    }

    android::base::unique_fd GetFd() {
        return android::base::unique_fd(open(mount_path_.c_str(), O_RDONLY | O_CLOEXEC | O_BINARY));
    }

    int32_t getReadTimeout() override { return 1; }

    std::unique_ptr<IncFsFileMap> GetFileMap(int fd, off64_t offset, size_t length) {
        auto map = std::make_unique<IncFsFileMap>();
        return map->Create(fd, offset, length, nullptr) ? std::move(map) : nullptr;
    }

private:
    std::string mount_path_;
};

struct TwoValues {
    uint32_t first;
    uint32_t second;
};

TEST_F(MapPtrTest, ReadAtStart) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(0U, p1.value());

    auto p2 = map->data<TwoValues>();
    ASSERT_TRUE(p2);
    ASSERT_EQ(0U, p2->first);
    ASSERT_EQ(1U, p2->second);
}

TEST_F(MapPtrTest, ReadNull) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(0U, p1.value());

    p1 = nullptr;
    ASSERT_FALSE(p1);

    p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(0U, p1.value());
}

TEST_F(MapPtrTest, ReadAtStartWithOffset) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), sizeof(uint32_t) * 4U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(4U, p1.value());

    auto p2 = map->data<TwoValues>();
    ASSERT_TRUE(p2);
    ASSERT_EQ(4U, p2->first);
    ASSERT_EQ(5U, p2->second);
}

TEST_F(MapPtrTest, PointerArithmetic) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>() + 11U;
    ASSERT_TRUE(p1);
    ASSERT_EQ(11U, p1.value());

    auto p2 = p1 - 5U;
    ASSERT_TRUE(p2);
    ASSERT_EQ(6U, p2.value());

    auto dis = p1 - p2;
    ASSERT_EQ((ptrdiff_t)5U, dis);
}

TEST_F(MapPtrTest, PointerIncrement) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(0U, p1.value());

    auto p2 = p1++;
    ASSERT_TRUE(p1);
    ASSERT_TRUE(p2);
    ASSERT_EQ(1U, p1.value());
    ASSERT_EQ(0U, p2.value());

    auto p3 = ++p2;
    ASSERT_TRUE(p2);
    ASSERT_TRUE(p3);
    ASSERT_EQ(1U, p2.value());
    ASSERT_EQ(1U, p3.value());
}

TEST_F(MapPtrTest, PointerComparison) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data<uint32_t>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(0U, p1.value());

    auto p2 = p1;
    ASSERT_TRUE(p1 == p2);
    ASSERT_TRUE(p1 < p2 + 1U);
    ASSERT_TRUE(p2 != p2 + 1U);
}

TEST_F(MapPtrTest, PointerConvert) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = (map->data<uint32_t>() + 11U).convert<TwoValues>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(11U, p1->first);
    ASSERT_EQ(12U, p1->second);
}

TEST_F(MapPtrTest, PointerOffset) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto p1 = map->data().offset(11U * sizeof(uint32_t)).convert<TwoValues>();
    ASSERT_TRUE(p1);
    ASSERT_EQ(11U, p1->first);
    ASSERT_EQ(12U, p1->second);
}

TEST_F(MapPtrTest, Iterator) {
    auto fd = GetFd();
    auto map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
    ASSERT_NE(nullptr, map);

    auto it = map->data<uint32_t>().iterator();
    ASSERT_TRUE(*it);
    ASSERT_EQ(0U, (*it).value());

    auto it2 = it;
    ASSERT_EQ(it, it2);

    auto it3 = it++;
    ASSERT_TRUE(*it3);
    ASSERT_EQ(0U, (*it3).value());

    ASSERT_NE(it, it2);
    ASSERT_EQ(1, it - it2);
    ASSERT_EQ(-1, it2 - it);

    auto it4 = ++it;
    ASSERT_TRUE(*it4);
    ASSERT_EQ(2U, (*it4).value());

    it += 10;
    ASSERT_EQ(12U, (*it).value());
}

static jmp_buf buf;

void sigbus_handler(int sig) {
    if (sig == SIGBUS) {
        siglongjmp(buf, 1);
    } else {
        FAIL();
    }
}

#define ASSERT_SIGBUS(test)               \
    do {                                  \
        signal(SIGBUS, &sigbus_handler);  \
        if (sigsetjmp(buf, 1) == 0) {     \
            ASSERT_EQ(0U, (test));        \
            FAIL() << "No signal raised"; \
        }                                 \
    } while (0)

TEST_F(MapPtrTest, VerifyMissingPageFails) {
    for (uint32_t off :
         std::vector<uint32_t>{0U, INCFS_DATA_FILE_BLOCK_SIZE / 2 - 1,
                               INCFS_DATA_FILE_BLOCK_SIZE / 2 + 1, INCFS_DATA_FILE_BLOCK_SIZE,
                               INCFS_DATA_FILE_BLOCK_SIZE * 3 / 2 - 1,
                               INCFS_DATA_FILE_BLOCK_SIZE * 3 / 2 + 1}) {
        auto fd = GetFd();
        auto map = GetFileMap(fd.get(), off /* offset */, FILE_SIZE);
        ASSERT_NE(nullptr, map);

        auto missing_page_start = INCFS_DATA_FILE_BLOCK_SIZE * FILE_MISSING_PAGE;
        auto p1 = map->data().offset(missing_page_start - off).convert<uint32_t>();
        ASSERT_FALSE(p1);
        ASSERT_SIGBUS(p1.value());

        const auto p2 = p1;
        ASSERT_FALSE(p2);
        ASSERT_SIGBUS(p2.value());

        const auto p3 = p2 - 1U;
        ASSERT_TRUE(p3);
        ASSERT_EQ(3071U, p3.value());

        auto p4 = p3;
        ASSERT_TRUE(p4);
        ASSERT_EQ(3071U, p4.value());

        ASSERT_FALSE(p4 + 1U);
        ASSERT_SIGBUS((p4 + 1U).value());

        auto p5 = p4++;
        ASSERT_TRUE(p5);
        ASSERT_EQ(3071U, p5.value());
        ASSERT_FALSE(p4);
        ASSERT_SIGBUS(p4.value());

        auto p6 = p3;
        ASSERT_TRUE(p6);
        ASSERT_EQ(3071U, p6.value());

        auto p7 = ++p6;
        ASSERT_FALSE(p7);
        ASSERT_SIGBUS(p7.value());
        ASSERT_FALSE(p6);
        ASSERT_SIGBUS(p6.value());

        auto missing_page_end = INCFS_DATA_FILE_BLOCK_SIZE * (FILE_MISSING_PAGE + 1);
        auto p8 = map->data().offset(missing_page_end - off).convert<uint32_t>();
        ASSERT_TRUE(p8);
        ASSERT_EQ(4096U, p8.value());

        ASSERT_FALSE(p8 - 1U);
        ASSERT_SIGBUS((p8 - 1U).value());
    }
}

TEST_F(MapPtrTest, GetDataAfterClose) {
    std::unique_ptr<IncFsFileMap> map;
    {
        auto fd = GetFd();
        map = GetFileMap(fd.get(), 0U /* offset */, FILE_SIZE);
        ASSERT_NE(nullptr, map);
    }

    auto missing_page_start = INCFS_DATA_FILE_BLOCK_SIZE * FILE_MISSING_PAGE;
    auto p1 = map->data().offset(missing_page_start).convert<uint32_t>();
    ASSERT_FALSE(p1);
    ASSERT_SIGBUS(p1.value());
}
