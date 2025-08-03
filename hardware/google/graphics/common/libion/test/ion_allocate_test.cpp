/*
 * Copyright (C) 2018 Samsung Electronics Co., Ltd.
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
#include <iostream>
#include <cerrno>
#include <cstring>

#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>

#include "ion_test_fixture.h"
#include "ion_test_define.h"

#define TEST_ALLOC_CACHED 1
#define TEST_ALLOC_BUDDY  2

using namespace std;

class Allocate : public IonAllocTest {
protected:
    struct test_type_struct {
        int type_flags;
        const char *type_title;
    };
    off_t checkZero(int fd, size_t size, unsigned long *val) {
        unsigned long *p = reinterpret_cast<unsigned long *>(mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0));
        if (p == MAP_FAILED)
            return -1;

        off_t idx;
        for (idx = 0; idx < static_cast<off_t>(size / sizeof(*p)); idx++) {
            if (p[idx] != 0) {
                if (val)
                    *val = p[idx];
                break;
            }
        }

        munmap(p, size);

        return idx * sizeof(*p);
    }

    void flushShrinker() {
        int fd = open("/sys/kernel/debug/ion_system_heap_shrink", O_RDWR);
        if (fd < 0)
            return;

        unsigned long val = mb(256); // This is very big enough to flush shrinker
        if (write(fd, &val, sizeof(val)) < 0)
            FAIL() << "Failed to write " << val << " to 'ion_system_heap_shrink': " << strerror(errno);
        if (read(fd, &val, sizeof(val)) < 0)
            FAIL() << "Failed to read from 'ion_system_heap_shrink': " << strerror(errno);
        if (val > 0)
            FAIL() << "ion_system_heap_shrink still has value " << val;
        close(fd);
    }
};

TEST_F(Allocate, Allocate)
{
    static const size_t allocation_sizes[] = {
        mkb(16, 716), mkb(12, 4), mkb(8, 912), mkb(4, 60), mkb(2, 520), mkb(1, 92),
        mb(16), mb(12), mb(8), mb(4), mb(2), mb(1), kb(64), kb(4),
    };
    static const test_type_struct test_types[] = {
        {0,                                     "uncached"},
        {TEST_ALLOC_CACHED,                     "cached"},
        {TEST_ALLOC_BUDDY,                      "uncached|flush_pool"},
        {TEST_ALLOC_CACHED | TEST_ALLOC_BUDDY,  "cached|flush_pool"},
    };

    for (test_type_struct type: test_types) {
        for (unsigned int i = 0; i < getHeapCount(); i++) {
            if ((type.type_flags & TEST_ALLOC_BUDDY) && !(getHeapFlags(i) & ION_HEAPDATA_FLAGS_DEFER_FREE))
                continue;

            if (getCmaUsed(getHeapName(i)) > 0)
                continue;

            for (size_t size : allocation_sizes) {
                if (size > getHeapSize(i))
                    continue;

                ion_allocation_data_modern data;
                int ret;

                data.len = size;
                data.heap_id_mask = getHeapMask(i);
                data.flags = (type.type_flags & TEST_ALLOC_CACHED) ? ION_FLAG_CACHED : 0;
                data.fd = 0;

                SCOPED_TRACE(::testing::Message() << "heap: " << getHeapName(i) << ", heapmask: " << getHeapMask(i));
                SCOPED_TRACE(::testing::Message() << "size: " << size << ", flags: " << data.flags);
                SCOPED_TRACE(::testing::Message() << "test type: " << type.type_title);

                if (type.type_flags & TEST_ALLOC_BUDDY)
                    flushShrinker();

                EXPECT_EQ(0, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data)) << ": " << strerror(errno);

                EXPECT_LT(2U, data.fd);
                EXPECT_GT(1024U, data.fd);
                if (ret == 0) {
                    if (!(getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)) {
                        off_t erridx;
                        unsigned long val = 0;
                        EXPECT_EQ(static_cast<off_t>(size), erridx = checkZero(data.fd, size, &val))
                                                << "non-zero " << val << " found at " << erridx << " byte";
                    }
                    EXPECT_EQ(0, close(data.fd));
                }
            }
        }
    }
}

TEST_F(Allocate, Large)
{
    static const test_type_struct test_types[] = {
        {0,                                     "uncached"},
        {TEST_ALLOC_CACHED,                     "cached"},
        {TEST_ALLOC_BUDDY,                      "uncached|flush_pool"},
        {TEST_ALLOC_CACHED | TEST_ALLOC_BUDDY,  "cached|flush_pool"},
    };

    for (test_type_struct type: test_types) {
        for (unsigned int i = 0; i < getHeapCount(); i++) {
            if ((type.type_flags & TEST_ALLOC_BUDDY) && !(getHeapFlags(i) & ION_HEAPDATA_FLAGS_DEFER_FREE))
                continue;

            if (getCmaUsed(getHeapName(i)) > 0)
                continue;

            __u64 size = 0;
            switch(getHeapType(i)) {
                case ION_HEAP_TYPE_SYSTEM:
                    size = getMemTotal() / 2 - 4096;
                    break;
                case ION_HEAP_TYPE_CARVEOUT:
                case ION_HEAP_TYPE_DMA:
                    size = getHeapSize(i);
                    break;
                default:
                    continue;
            }

            ion_allocation_data_modern data;
            int ret;

            data.len = size;
            data.heap_id_mask = getHeapMask(i);
            data.flags = (type.type_flags & TEST_ALLOC_CACHED) ? ION_FLAG_CACHED : 0;
            data.fd = 0;

            SCOPED_TRACE(::testing::Message() << "heap: " << getHeapName(i) << ", heapmask: " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size: " << size << ", flags: " << data.flags);
            SCOPED_TRACE(::testing::Message() << "test type: " << type.type_title);

            if (type.type_flags & TEST_ALLOC_BUDDY)
                flushShrinker();

            EXPECT_EQ(0, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data)) << ": " << strerror(errno);

            EXPECT_LT(2U, data.fd);
            EXPECT_GT(1024U, data.fd);
            if (ret == 0)
                EXPECT_EQ(0, close(data.fd));
        }
    }
}

TEST_F(Allocate, Nozeroed)
{
    static const size_t allocation_sizes[] = { mkb(4, 60), mkb(2, 520), mkb(1, 92) };
    static const unsigned long allocation_flags[] = { 0, ION_FLAG_CACHED, ION_FLAG_SYNC_FORCE };

    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (unsigned long flag : allocation_flags) {
            for (size_t size : allocation_sizes) {
                if (size > getHeapSize(i))
                    continue;

                ion_allocation_data_modern data;
                int ret;

                data.len = size;
                data.heap_id_mask = getHeapMask(i);
                data.flags = ION_FLAG_NOZEROED | flag;
                data.fd = 0;

                SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
                SCOPED_TRACE(::testing::Message() << "size " << size << ", flags " << data.flags);

                flushShrinker();

                EXPECT_EQ(0, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data)) << ": " << strerror(errno);

                EXPECT_LT(2U, data.fd);
                EXPECT_GT(1024U, data.fd);
                if (ret == 0) {
                    void *p;

                    EXPECT_EQ(MAP_FAILED, p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, data.fd, 0));
                    EXPECT_EQ(EACCES, errno);
                    if (p != MAP_FAILED)
                        munmap(p, size);
                    EXPECT_EQ(0, close(data.fd));
                }
            }
        }
    }
}

TEST_F(Allocate, Protected)
{
    static const size_t allocation_sizes[] = {kb(4), kb(64), mb(1), mb(2)};
    static const unsigned long allocation_flags[] = { 0, ION_FLAG_CACHED, ION_FLAG_SYNC_FORCE };

    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (unsigned long flags: allocation_flags) {
            for (size_t size : allocation_sizes) {
                if (size > getHeapSize(i))
                    continue;

                ion_allocation_data_modern data;
                int ret;

                data.len = size;
                data.heap_id_mask = getHeapMask(i);
                data.flags = ION_FLAG_PROTECTED | flags;
                data.fd = 0;

                SCOPED_TRACE(::testing::Message() << "heap: " << getHeapName(i) << ", heapmask: " << getHeapMask(i));
                SCOPED_TRACE(::testing::Message() << "size: " << size << ", flags: " << data.flags);

                EXPECT_EQ(0, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data)) << ": " << strerror(errno);

                EXPECT_LT(2U, data.fd);
                EXPECT_GT(1024U, data.fd);
                if (ret == 0) {
                    void *p;

                    EXPECT_EQ(MAP_FAILED, p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, data.fd, 0));
                    EXPECT_EQ(EACCES, errno);

                    if (p != MAP_FAILED)
                        munmap(p, size);

                    EXPECT_EQ(0, close(data.fd));
                }
            }
        }
    }
}

TEST_F(Allocate, InvalidValues)
{
    ion_allocation_data_modern data;
    int ret;

    data.len = kb(4);
    data.heap_id_mask = 1; // any first heap
    data.flags = 0;
    data.fd = 0;

    // incorrect /dev/ion fd
    EXPECT_EQ(-1, ret = ioctl(0, ION_IOC_ALLOC_MODERN, &data));
    EXPECT_TRUE(errno == EINVAL || errno == ENOTTY) << " unexpected error: " << strerror(errno);

    // invalid fd
    EXPECT_EQ(-1, ret = ioctl(-1, ION_IOC_ALLOC_MODERN, &data));
    EXPECT_EQ(EBADF, errno) << " unexpected error: " << strerror(errno);

    // invalid heap id
    data.heap_id_mask = 0;
    EXPECT_EQ(-1, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data));
    EXPECT_EQ(ENODEV, errno) << " unexpected error: " << strerror(errno);

    // unavailable heap id (the largest heap id + 1)
    data.heap_id_mask = 1 << (getMaxHeapId() + 1);
    EXPECT_EQ(-1, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data))
                << "unexpected success with heapmask " << data.heap_id_mask;
    EXPECT_EQ(ENODEV, errno) << " unexpected error: " << strerror(errno);

    // zero size
    data.len = 0;
    EXPECT_EQ(-1, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data));
    EXPECT_EQ(EINVAL, errno) << " unexpected error: " << strerror(errno);

    // too large size
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        data.heap_id_mask = getHeapMask(i);

        switch(getHeapType(i)) {
            case ION_HEAP_TYPE_SYSTEM:
                data.len = kb(getMemTotal() / 2) + kb(4);
		break;
            case ION_HEAP_TYPE_CARVEOUT:
            case ION_HEAP_TYPE_DMA:
                data.len = getHeapSize(i) + 1;
                break;
            case ION_HEAP_TYPE_HPA:
                data.len = ((sysconf(_SC_PAGESIZE) * 2) / 8) * (64 * 1024) + 1; // see hpa heap of ION driver
                break;
            default:
                continue;
        }

        SCOPED_TRACE(::testing::Message() << "heap: " << getHeapName(i) << ", heapmask: " << getHeapMask(i));
        SCOPED_TRACE(::testing::Message() << "size: " << data.len << ", flags: " << data.flags);

        EXPECT_EQ(-1, ret = ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data));
        EXPECT_EQ(ENOMEM, errno) << " unexpected error: " << strerror(errno);
    }
}
