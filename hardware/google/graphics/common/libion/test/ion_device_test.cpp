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

#include "ion_test.h"
#include "../ion_uapi.h"

#include <gtest/gtest.h>

#include "ion_test_fixture.h"
#include "ion_test_define.h"

#define ALIGN(x,y) (((x) + ((y) - 1)) & ~((y) - 1))

static const size_t g_allocation_sizes[] = {
    mkb(16, 716), mkb(12, 4), mkb(8, 912), mkb(4, 60), mkb(2, 520), mkb(1, 92),
    mb(16), mb(12), mb(8), mb(4), mb(2), mb(1), kb(64), kb(4),
};

class Device : public IonClientDeviceTest {
};

TEST_F(Device, KernelRead)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), 0);
            ASSERT_LT(2, fd);

            char *dst = new char[size];
            void *src = ionMmap(fd, size);

            fill(src, size, 0);

            ionTestReadKernel(fd, dst, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] dst;

            ionMunmap(src, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, KernelWrite)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), 0);
            ASSERT_LT(2, fd);

            char *src = new char[size];
            void *dst = ionMmap(fd, size);

            fill(src, size, 0);
            dirtyCache(dst, size);

            ionTestWriteKernel(fd, src, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] src;

            ionMunmap(dst, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, KernelReadCached)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), ION_FLAG_CACHED);
            ASSERT_LT(2, fd);

            char *dst = new char[size];
            void *src = ionMmap(fd, size);

            fill(src, size, 0);

            ionTestReadKernel(fd, dst, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] dst;

            ionMunmap(src, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, KernelWriteCached)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), ION_FLAG_CACHED);
            ASSERT_LT(2, fd);

            char *src = new char[size];
            void *dst = ionMmap(fd, size);

            fill(src, size, 0);
            dirtyCache(dst, size);

            ionTestWriteKernel(fd, src, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] src;

            ionMunmap(dst, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, DeviceRead)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), 0);
            ASSERT_LT(2, fd);

            char *dst = new char[size];
            void *src = ionMmap(fd, size);

            fill(src, size, 0);

            ionTestReadDma(fd, dst, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] dst;

            ionMunmap(src, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, DeviceWrite)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), 0);
            ASSERT_LT(2, fd);

            char *src = new char[size];
            void *dst = ionMmap(fd, size);

            fill(src, size, 0);
            dirtyCache(dst, size);

            ionTestWriteDma(fd, src, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] src;

            ionMunmap(dst, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, DeviceReadCached)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), ION_FLAG_CACHED);
            ASSERT_LT(2, fd);

            char *dst = new char[size];
            void *src = ionMmap(fd, size);

            fill(src, size, 0);

            ionTestReadDma(fd, dst, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] dst;

            ionMunmap(src, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}

TEST_F(Device, DeviceWriteCached)
{
    for (unsigned int i = 0; i < getHeapCount(); i++) {
        if (getHeapFlags(i) & ION_HEAPDATA_FLAGS_UNTOUCHABLE)
            continue;

        if (getCmaUsed(getHeapName(i)) > 0)
            continue;

        for (size_t size : g_allocation_sizes) {
            if (size > getHeapSize(i))
                continue;

            SCOPED_TRACE(::testing::Message() << "heap " << getHeapName(i) << " mask " << getHeapMask(i));
            SCOPED_TRACE(::testing::Message() << "size " << size);

            int fd = ionAlloc(size, getHeapMask(i), ION_FLAG_CACHED);
            ASSERT_LT(2, fd);

            char *src = new char[size];
            void *dst = ionMmap(fd, size);

            fill(src, size, 0);
            dirtyCache(dst, size);

            ionTestWriteDma(fd, src, size, 0);

            EXPECT_TRUE(check(dst, size, 0));

            delete [] src;

            ionMunmap(dst, size);

            EXPECT_EQ(0, close(fd));
        }
    }
}
