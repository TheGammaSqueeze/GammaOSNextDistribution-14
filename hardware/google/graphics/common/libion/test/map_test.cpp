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
#include <sys/mman.h>

#include <gtest/gtest.h>

#include <ion/ion.h>
#include <../include/exynos_ion.h>

#include "ion_test_fixture.h"

class Map : public IonAllHeapsTest {
};

TEST_F(Map, MapFd)
{
    static const size_t allocationSizes[] = {4*1024, 64*1024, 1024*1024, 2*1024*1024};
    struct ion_heap_data *heap = (struct ion_heap_data *)m_heap_query.heaps;

    for (unsigned int i = 0; i < m_heap_query.cnt; i++) {
        unsigned int heapMask = 1 << heap[i].heap_id;
        for (size_t size : allocationSizes) {
            SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
            SCOPED_TRACE(::testing::Message() << "size " << size);
            int map_fd = -1;

            ASSERT_EQ(0, ion_alloc_fd(m_ionFd, size, 0, heapMask, 0, &map_fd));
            ASSERT_GE(map_fd, 0);

            void *ptr;
            ptr = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, map_fd, 0);
            ASSERT_TRUE(ptr != NULL);

            ASSERT_EQ(0, close(map_fd));

            memset(ptr, 0xaa, size);

            ASSERT_EQ(0, munmap(ptr, size));
        }
    }
}

TEST_F(Map, MapOffset)
{
    struct ion_heap_data *heap = (struct ion_heap_data *)m_heap_query.heaps;

    for (unsigned int i = 0; i < m_heap_query.cnt; i++) {
        unsigned int heapMask = 1 << heap[i].heap_id;
        SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
        int map_fd = -1;

        ASSERT_EQ(0, ion_alloc_fd(m_ionFd, PAGE_SIZE * 2, 0, heapMask, 0, &map_fd));
        ASSERT_GE(map_fd, 0);

        unsigned char *ptr;
        ptr = (unsigned char *)mmap(NULL, PAGE_SIZE * 2, PROT_READ | PROT_WRITE, MAP_SHARED, map_fd, 0);
        ASSERT_TRUE(ptr != NULL);

        memset(ptr, 0, PAGE_SIZE);
        memset(ptr + PAGE_SIZE, 0xaa, PAGE_SIZE);

        ASSERT_EQ(0, munmap(ptr, PAGE_SIZE * 2));

        ptr = (unsigned char *)mmap(NULL, PAGE_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, map_fd, PAGE_SIZE);
        ASSERT_TRUE(ptr != NULL);

        ASSERT_EQ(ptr[0], 0xaa);
        ASSERT_EQ(ptr[PAGE_SIZE - 1], 0xaa);

        ASSERT_EQ(0, munmap(ptr, PAGE_SIZE));

        ASSERT_EQ(0, close(map_fd));
    }
}

TEST_F(Map, MapCached)
{
    static const size_t allocationSizes[] = {4*1024, 64*1024, 1024*1024, 2*1024*1024};
    struct ion_heap_data *heap = (struct ion_heap_data *)m_heap_query.heaps;

    for (unsigned int i = 0; i < m_heap_query.cnt; i++) {
        unsigned int heapMask = 1 << heap[i].heap_id;
        for (size_t size : allocationSizes) {
            SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
            SCOPED_TRACE(::testing::Message() << "size " << size);
            int map_fd = -1;
            unsigned int flags = ION_FLAG_CACHED | ION_FLAG_CACHED_NEEDS_SYNC;

            ASSERT_EQ(0, ion_alloc_fd(m_ionFd, size, 0, heapMask, flags, &map_fd));
            ASSERT_GE(map_fd, 0);

            void *ptr;
            ptr = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, map_fd, 0);
            ASSERT_TRUE(ptr != NULL);

            ASSERT_EQ(0, close(map_fd));

            memset(ptr, 0xaa, size);

            ASSERT_EQ(0, munmap(ptr, size));
        }
    }
}

TEST_F(Map, NoZeroed)
{
    struct ion_heap_data *heap = (struct ion_heap_data *)m_heap_query.heaps;

    for (unsigned int i = 0; i < m_heap_query.cnt; i++) {
        unsigned int heapMask = 1 << heap[i].heap_id;
        SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
        int map_fd = -1;
        ASSERT_EQ(0, ion_alloc_fd(m_ionFd, 4096, 0, heapMask, ION_FLAG_NOZEROED, &map_fd));
        ASSERT_GE(map_fd, 0);

        void *ptr = NULL;
        ptr = mmap(NULL, 4096, PROT_READ, MAP_SHARED, map_fd, 0);
        ASSERT_TRUE(ptr != NULL);

        ASSERT_EQ(0, close(map_fd));
    }
}

TEST_F(Map, Protected)
{
    /* TODO : add the secure Heaps */
    static const unsigned int secureHeaps[] = {
        EXYNOS_ION_HEAP_CRYPTO_MASK,
        EXYNOS_ION_HEAP_VIDEO_STREAM_MASK,
        EXYNOS_ION_HEAP_CAMERA,
    };

    for (unsigned int heapMask : secureHeaps) {
        SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
        int map_fd = -1;
        ASSERT_EQ(0, ion_alloc_fd(m_ionFd, 4096, 0, heapMask, ION_FLAG_PROTECTED, &map_fd));
        ASSERT_GE(map_fd, 0);

        void *ptr = NULL;
        ptr = mmap(NULL, 4096, PROT_READ, MAP_SHARED, map_fd, 0);
        ASSERT_TRUE(ptr != NULL);

        ASSERT_EQ(0, close(map_fd));
    }
}

TEST_F(Map, InvalidValues)
{
    struct ion_heap_data *heap = (struct ion_heap_data *)m_heap_query.heaps;

    for (unsigned int i = 0; i < m_heap_query.cnt; i++) {
        unsigned int heapMask = 1 << heap[i].heap_id;
        SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
        int map_fd = -1;
        ASSERT_EQ(0, ion_alloc_fd(m_ionFd, 4096, 0, heapMask, 0, &map_fd));
        ASSERT_GE(map_fd, 0);

        void *ptr = NULL;

        /* bad size */
        ptr = mmap(NULL, 0, PROT_READ, MAP_SHARED, map_fd, 0);
        ASSERT_TRUE(ptr != NULL);
        /* bad prot */
        ptr = mmap(NULL, 4096, -1, MAP_SHARED, map_fd, 0);
        ASSERT_TRUE(ptr != NULL);
        /* bad offset */
        ptr = mmap(NULL, 4096, PROT_READ, MAP_SHARED, map_fd, -1);
        ASSERT_TRUE(ptr != NULL);
        /* null map fd */
        ptr = mmap(NULL, 4096, PROT_READ, MAP_SHARED, 0, 0);
        ASSERT_TRUE(ptr != NULL);

        ASSERT_EQ(0, close(map_fd));
    }
}
