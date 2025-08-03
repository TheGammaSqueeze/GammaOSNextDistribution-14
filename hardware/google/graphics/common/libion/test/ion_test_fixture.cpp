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
#include <fstream>
#include <string>
#include <cerrno>

#include <unistd.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <fcntl.h>

#include "ion_test_fixture.h"
#include "ion_test_define.h"

using namespace std;

IonTest::IonTest() : m_ionFd(-1), m_heapCount(0), m_allHeapMask(0)
{
    m_idTable[0].legacy_id = ION_EXYNOS_HEAP_ID_SYSTEM;
    m_idTable[1].legacy_id = ION_EXYNOS_HEAP_ID_CRYPTO;
    m_idTable[2].legacy_id = ION_EXYNOS_HEAP_ID_VIDEO_STREAM;
    m_idTable[3].legacy_id = ION_EXYNOS_HEAP_ID_VIDEO_FRAME;
    m_idTable[4].legacy_id = ION_EXYNOS_HEAP_ID_VIDEO_SCALER;
    m_idTable[5].legacy_id = ION_EXYNOS_HEAP_ID_CAMERA;
    m_idTable[6].legacy_id = ION_EXYNOS_HEAP_ID_SECURE_CAMERA;

    for (unsigned int i = 0; i < MAX_LEGACY_HEAP_IDS; i++)
        m_idTable[i].heap_id = ION_NUM_HEAP_IDS;
}

void IonTest::SetUp()
{
    m_ionFd = open("/dev/ion", O_RDWR);

    m_ionHeapData = new ion_heap_data[ION_NUM_HEAP_IDS];
    if (m_ionHeapData != NULL) {
        ion_heap_query query;
        int ret;

        memset(&query, 0, sizeof(query));
        memset(m_ionHeapData, 0, sizeof(ion_heap_data) * ION_NUM_HEAP_IDS);

        query.cnt = ION_NUM_HEAP_IDS;
        query.heaps = reinterpret_cast<__u64>(m_ionHeapData);

        ret = ioctl(m_ionFd, ION_IOC_HEAP_QUERY, &query);
        if (ret < 0) {
            FAIL() << "ION_IOC_HEAP_QUERY failed: " << strerror(errno);
        } else {
            m_heapCount = query.cnt;
        }
    }

    for (unsigned int i = 0; i < MAX_LEGACY_HEAP_IDS; i++) {
        const char *name = exynos_ion_get_heap_name(m_idTable[i].legacy_id);
        for (unsigned int k = 0; k < m_heapCount; k++) {
            if (!strcmp(name, m_ionHeapData[k].name) &&
                (strlen(name) == strlen(m_ionHeapData[k].name))) {
                    m_allHeapMask |= 1 << m_idTable[i].legacy_id;
                    m_idTable[i].heap_id = m_ionHeapData[k].heap_id;
            }
        }
    }

    RecordProperty("Heaps", m_heapCount);

    SUCCEED() << "Found " << m_heapCount << " heaps found";
}

void IonTest::TearDown()
{
    delete [] m_ionHeapData;
    close(m_ionFd);
}

size_t IonTest::getCmaUsed(std::string heapname)
{
    std::string pathname = "/sys/kernel/debug/cma/cma-";
    pathname = pathname + heapname;
    pathname = pathname + "/used";

    ifstream ifs;
    ifs.open(pathname);
    // CONFIG_CMA_DEBUGFS is not enabled or @heapname is not defined
    if (!ifs)
        return 0;

    size_t len;
    ifs >> len;

    return len * sysconf(_SC_PAGESIZE);
}

IonAllocTest::IonAllocTest() : m_memTotal(0)
{
}

void IonAllocTest::SetUp()
{
    IonTest::SetUp();

    ifstream ifs;
    ifs.open("/proc/meminfo");

    string title, kb;
    size_t size;

    ifs >> title >> size >> kb;

    m_memTotal = size;

    RecordProperty("MemTotalInKb", m_memTotal);

    SUCCEED() << "MemTotal: " << m_memTotal << " kb";
}

void IonAllocTest::TearDown()
{
    IonTest::TearDown();
}

IonSpecialTest::IonSpecialTest() : m_ionTestDevFd(-1)
{
}

void IonSpecialTest::SetUp()
{
    IonTest::SetUp();

    m_ionTestDevFd = open("/dev/ion-test", O_RDWR);
    if (m_ionTestDevFd < 0) {
        FAIL() << "Failed to open /dev/ion-test: " << strerror(errno);
    }
}

void IonSpecialTest::TearDown()
{
    IonTest::TearDown();

    close(m_ionTestDevFd);
}

int IonSpecialTest::ionAlloc(size_t size, unsigned int heapmask,
                            unsigned int flags)
{
    int fd = -1;

    ion_allocation_data_modern data;

    data.len = size;
    data.heap_id_mask = heapmask;
    data.flags = flags;
    data.fd = 0;

    if (ioctl(getIonFd(), ION_IOC_ALLOC_MODERN, &data) == 0)
        fd = static_cast<int>(data.fd);

    return fd;
}

char *IonClientDeviceTest::ionMmap(int fd, size_t size)
{
    void *p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (p == MAP_FAILED)
        return NULL;
    return reinterpret_cast<char *>(p);
}

void IonClientDeviceTest::ionMunmap(void *ptr, size_t size)
{
    munmap(ptr, size);
}

void IonClientDeviceTest::ionTestMapping(int fd, bool write, unsigned long cmd,
                                         void *ptr, size_t size, off_t offset)
{
    EXPECT_EQ(0, ioctl(getTestDevFd(), ION_IOC_TEST_SET_FD, fd));

    ion_test_rw_data data;

    data.ptr = reinterpret_cast<__u64>(ptr);
    data.offset = offset;
    data.size = size;
    data.write = write ? 1 : 0;

    EXPECT_EQ(0, ioctl(getTestDevFd(), cmd, &data)) << strerror(errno);

    EXPECT_EQ(0, ioctl(getTestDevFd(), ION_IOC_TEST_SET_FD, -1));
}

void IonClientDeviceTest::blowCache()
{
    const size_t bigger_than_cache = mb(8);
    const size_t nelem = bigger_than_cache / sizeof(unsigned int);
    unsigned int *buf1 = new unsigned int[nelem];
    unsigned int *buf2 = new unsigned int[nelem];

    // write order is swizzled to prevent
    // detection of streaming write by a high-performance CPU.
    // Such CPUs skip storing streaming writes to caches.
    for (size_t i = 0; i < (nelem / 2); i++) {
        buf1[i] = i;
        buf1[nelem - i - 1] = i;
    }

    memcpy(buf2, buf1, bigger_than_cache);

    delete [] buf2;
    delete [] buf1;
}

void IonClientDeviceTest::dirtyCache(void *ptr, size_t size)
{
    unsigned int *uip = reinterpret_cast<unsigned int *>(ptr);
    const size_t nelem = size / sizeof(unsigned int);

    for (size_t i = 0; i < (nelem / 2); i++) {
        uip[i] = 0x5a6b5a6b;
        uip[nelem - i - 1] = 0x6b5a6b5a;
    }
}

void IonClientDeviceTest::fill(void *ptr, size_t size, off_t offset)
{
    unsigned int *uip = reinterpret_cast<unsigned int *>(ptr);

    size /= sizeof(unsigned int);
    offset /= sizeof(unsigned int);

    uip += offset;

    for (size_t i = 0; i < (size / 2); i++) {
        uip[i] = i;
        uip[size - i - 1] = size - i - 1;
    }
}

bool IonClientDeviceTest::check(void *ptr, size_t size, off_t offset)
{
    unsigned int *uip = reinterpret_cast<unsigned int *>(ptr);

    size /= sizeof(unsigned int);
    offset /= sizeof(unsigned int);

    uip += offset;

    for (size_t i = 0; i < size; i++) {
        if (uip[i] != i) {
            cerr <<  "Expected " << i << " at offset " << i * sizeof(i) << " byte but found " << uip[i] << endl;
            return false;
        }
    }

    return true;
}
