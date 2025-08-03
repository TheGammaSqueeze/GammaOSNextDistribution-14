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
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>

#include "ion_test_fixture.h"
#include "ion_test_define.h"

static const size_t allocation_sizes[] = {
    mkb(16, 716), mkb(12, 4), mkb(8, 912), mkb(4, 60), mkb(2, 520), mkb(1, 92),
    mb(16), mb(12), mb(8), mb(4), mb(2), mb(1), kb(64), kb(4),
};

void flushShrinker(const char *heapname) {
    std::string pathname = "/sys/kernel/debug/";

    pathname += heapname;
    pathname += "_shrink";

    int fd = open(pathname.c_str(), O_RDWR);
    if (fd < 0)
        return;

    unsigned long val = mb(256); // This is very big enough to flush shrinker
    if (write(fd, &val, sizeof(val)) < 0)
        FAIL() << "Failed to write " << val << " to '" << heapname << "_shrink': " << strerror(errno);
    if (read(fd, &val, sizeof(val)) < 0)
        FAIL() << "Failed to read from '" << heapname << "_shrink': " << strerror(errno);
    if (val > 0)
        FAIL() << heapname << "_shrink still has value " << val;
    close(fd);
}

class PhysTest : public IonSpecialTest {
protected:
    void ionTestPhys(int fd, unsigned int cmd) {
        int ret;

        EXPECT_EQ(0, ret = ioctl(getTestDevFd(), ION_IOC_TEST_SET_FD, fd));

        if (ret == 0) {
            ion_test_phys_data data;
            data.cmd = cmd;
            data.arg = 0;
            data.result = 0;

            EXPECT_EQ(0, ioctl(getTestDevFd(), ION_IOC_TEST_PHYS, &data));

            EXPECT_EQ(0, ioctl(getTestDevFd(), ION_IOC_TEST_SET_FD, -1));
        }
    }
};

TEST_F(PhysTest, Carveout)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapType(i) != ION_HEAP_TYPE_CARVEOUT)
            continue;

        for (size_t size : allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            int fd = ionAlloc(size, getHeapMask(i), 0);

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            EXPECT_LT(2, fd);
            EXPECT_GT(1024, fd);

            ionTestPhys(fd, PHYS_IS_RESERVED);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(PhysTest, Cma)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        if (getHeapType(i) != ION_HEAP_TYPE_DMA)
            continue;

        for (size_t size : allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            int fd = ionAlloc(size, getHeapMask(i), 0);

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            EXPECT_LT(2, fd);
            EXPECT_GT(1024, fd);

            ionTestPhys(fd, PHYS_IS_CMA);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(PhysTest, Hpa)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapType(i) != ION_HEAP_TYPE_CARVEOUT)
            continue;

        for (size_t size : allocation_sizes) {
            if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_DEFER_FREE)
                flushShrinker(getHeapName(i));

            int fd = ionAlloc(size, getHeapMask(i), 0);

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            EXPECT_LT(2, fd);
            EXPECT_GT(1024, fd);

            ionTestPhys(fd, PHYS_CHUNK_IS_IDENTICAL_SIZE);
            ionTestPhys(fd, PHYS_IS_ORDERED_IN_ADDRESS);

            EXPECT_EQ(0, close(fd));
        }
    }
}
