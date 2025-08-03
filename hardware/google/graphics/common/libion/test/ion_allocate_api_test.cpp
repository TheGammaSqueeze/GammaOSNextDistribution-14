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

class AllocateAPI : public IonAllocTest {
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

TEST_F(AllocateAPI, Allocate)
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
        for (unsigned int i = 0; i < MAX_LEGACY_HEAP_IDS; i++) {
            unsigned int heap_id = getModernHeapId(i);
            unsigned int heapmask = 1 << getLegacyHeapId(i);

            if (heap_id == ION_NUM_HEAP_IDS)
                continue;
            if ((type.type_flags & TEST_ALLOC_BUDDY) && !(getHeapFlags(heap_id) & ION_HEAPDATA_FLAGS_DEFER_FREE))
                continue;

            for (size_t size : allocation_sizes) {
                if (size > getHeapSize(heap_id))
                    continue;

                unsigned int flags = (type.type_flags & TEST_ALLOC_CACHED) ? ION_FLAG_CACHED : 0;
                int fd;

                SCOPED_TRACE(::testing::Message() << "heap: " << getHeapName(heap_id) << ", heap id: " << heap_id << ", heapmask: " << heapmask);
                SCOPED_TRACE(::testing::Message() << "size: " << size << ", flags: " << flags);
                SCOPED_TRACE(::testing::Message() << "test type: " << type.type_title);

                if (type.type_flags & TEST_ALLOC_BUDDY)
                    flushShrinker();

                EXPECT_LE(0, fd = exynos_ion_alloc(getIonFd(), size, heapmask, flags)) << ": " << strerror(errno);

                EXPECT_LT(2, fd);
                EXPECT_GT(1024, fd);
                if (fd >= 0) {
                    if (!(getHeapFlags(heap_id) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)) {
                        off_t erridx;
                        unsigned long val = 0;
                        EXPECT_EQ(static_cast<off_t>(size), erridx = checkZero(fd, size, &val))
                                                << "non-zero " << val << " found at " << erridx << " byte";
                    }
                    EXPECT_EQ(0, close(fd));
                }
            }
        }
    }
}
