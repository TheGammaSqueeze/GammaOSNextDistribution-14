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
#ifndef ION_TEST_FIXTURE_H_
#define ION_TEST_FIXTURE_H_

#include <string>

#include <hardware/exynos/ion.h>

#include <gtest/gtest.h>

#include "ion_test.h"
#include "../ion_uapi.h"

#define MAX_LEGACY_HEAP_IDS 7

class IonTest : public ::testing::Test {
    struct ion_id_table {
        unsigned int legacy_id;
        unsigned int heap_id;
    };

    struct ion_heap_data *m_ionHeapData;
    int m_ionFd;
    unsigned int m_heapCount;
    unsigned int m_allHeapMask;
    struct ion_id_table m_idTable[MAX_LEGACY_HEAP_IDS]; /* see IonTest::SetUp() */
public:
    IonTest();
    virtual ~IonTest() {};
    virtual void SetUp();
    virtual void TearDown();

    int getIonFd() { return m_ionFd; }
    unsigned int getHeapCount() { return m_heapCount; }
    ion_heap_type getHeapType(unsigned int idx) {
        return static_cast<ion_heap_type>(m_ionHeapData[idx].type);
    }
    unsigned int getHeapMask(unsigned int idx) {
        return (m_heapCount > 0) ? 1 << m_ionHeapData[idx].heap_id : 0;
    }
    const char *getHeapName(unsigned int idx) {
        return (m_heapCount > 0) ? m_ionHeapData[idx].name : "[NO HEAP]";
    }
    unsigned int getHeapFlags(unsigned int idx) {
        return m_ionHeapData[idx].heap_flags;
    }
    unsigned int getHeapSize(unsigned int idx) {
        if (m_ionHeapData[idx].size == 0)
            return -1;
        return m_ionHeapData[idx].size;
    }
    unsigned int getMaxHeapId() {
        unsigned int heapid = 0;

        for (unsigned int i = 0; i < getHeapCount(); i++)
            if (heapid < m_ionHeapData[i].heap_id)
                heapid = m_ionHeapData[i].heap_id;

        return heapid;
    }
    unsigned int getAllHeapMask() {
        return m_allHeapMask;
    }
    unsigned int getModernHeapId(unsigned int idx) {
        return m_idTable[idx].heap_id;
    }

    unsigned int getLegacyHeapId(unsigned int idx) {
        return m_idTable[idx].legacy_id;
    }
    size_t getCmaUsed(std::string heapname);
};

class IonAllocTest : public IonTest {
    size_t m_memTotal;
public:
    IonAllocTest();
    virtual ~IonAllocTest() {};
    virtual void SetUp();
    virtual void TearDown();

    size_t getMemTotal() { return m_memTotal; }
};

class IonSpecialTest : public IonTest {
    int m_ionTestDevFd;
protected:
    int getTestDevFd() { return m_ionTestDevFd; }
    int ionAlloc(size_t size, unsigned int heapmask, unsigned int flags);
public:
    IonSpecialTest();
    virtual ~IonSpecialTest() {};
    virtual void SetUp();
    virtual void TearDown();
};

class IonClientDeviceTest : public IonSpecialTest {

    void ionTestMapping(int fd, bool write, unsigned long cmd,
                        void *ptr, size_t size, off_t offset);
protected:
    char *ionMmap(int fd, size_t size);
    void ionMunmap(void *ptr, size_t size);

    void ionTestWriteDma(int fd, void *ptr, size_t size, off_t offset) {
        ionTestMapping(fd, true, ION_IOC_TEST_DMA_MAPPING, ptr, size, offset);
    }
    void ionTestReadDma(int fd, void *ptr, size_t size, off_t offset) {
        ionTestMapping(fd, false, ION_IOC_TEST_DMA_MAPPING, ptr, size, offset);
    }
    void ionTestWriteKernel(int fd, void *ptr, size_t size, off_t offset) {
        ionTestMapping(fd, true, ION_IOC_TEST_KERNEL_MAPPING, ptr, size, offset);
    }
    void ionTestReadKernel(int fd, void *ptr, size_t size, off_t offset) {
        ionTestMapping(fd, false, ION_IOC_TEST_KERNEL_MAPPING, ptr, size, offset);
    }

    void fill(void *ptr, size_t size, off_t offset);
    bool check(void *ptr, size_t size, off_t offset);
    void blowCache();
    void dirtyCache(void *ptr, size_t size);
public:
    IonClientDeviceTest() { }
    virtual ~IonClientDeviceTest() {}
};

#endif /* ION_TEST_FIXTURE_H_ */
