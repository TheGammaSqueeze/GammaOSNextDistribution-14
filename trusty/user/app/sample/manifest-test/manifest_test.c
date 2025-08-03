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

#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define TEST1_ID 1
#define TEST1_PHY_BASE_ADDR 0x70000000U
#define TEST1_REG_SIZE 0x1000U

#define TEST2_ID 2
#define TEST2_PHY_BASE_ADDR 0x70010000U
#define TEST2_REG_SIZE 0x100U

#define TEST3_ID 3
#define TEST3_PHY_BASE_ADDR 0x70020000U
#define TEST3_REG_SIZE 0x4U

typedef struct manifest_test {
} manifest_test_t;

TEST_F_SETUP(manifest_test) {}

TEST_F_TEARDOWN(manifest_test) {
test_abort:;
}

bool compare_memory_map(uint8_t* va_base, uint64_t phy_addr, uint32_t size) {
    const uint32_t flags = DMA_FLAG_FROM_DEVICE | DMA_FLAG_ALLOW_PARTIAL;
    int ret;
    struct dma_pmem pmem;

    ASSERT_NE(va_base, MAP_FAILED);

    ret = prepare_dma((void*)va_base, size, flags, &pmem);
    ASSERT_EQ(ret, 1);
    ret = finish_dma((void*)va_base, size, flags);
    ASSERT_EQ(ret, NO_ERROR);
    ASSERT_NE(0, phy_addr);
    ASSERT_EQ(phy_addr, pmem.paddr);
    ASSERT_EQ(size, pmem.size);

    return true;

test_abort:
    return false;
}

TEST_F(manifest_test, mem_map_test_1) {
    uint8_t* va_base = MAP_FAILED;
    bool ret;

    va_base = mmap(NULL, TEST1_REG_SIZE, PROT_READ | PROT_WRITE,
                   MMAP_FLAG_IO_HANDLE, TEST1_ID, 0);

    ret = compare_memory_map(va_base, TEST1_PHY_BASE_ADDR, TEST1_REG_SIZE);

    ASSERT_EQ(ret, true)

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, TEST1_REG_SIZE);
    }
}

TEST_F(manifest_test, mem_map_test_2) {
    uint8_t* va_base = MAP_FAILED;
    bool ret;

    va_base = mmap(NULL, TEST2_REG_SIZE, PROT_READ | PROT_WRITE,
                   MMAP_FLAG_IO_HANDLE, TEST2_ID, 0);

    ret = compare_memory_map(va_base, TEST2_PHY_BASE_ADDR, TEST2_REG_SIZE);

    ASSERT_EQ(ret, true)

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, TEST2_REG_SIZE);
    }
}

TEST_F(manifest_test, mem_map_test_3) {
    uint8_t* va_base = MAP_FAILED;
    bool ret;

    va_base = mmap(NULL, TEST3_REG_SIZE, PROT_READ | PROT_WRITE,
                   MMAP_FLAG_IO_HANDLE, TEST3_ID, 0);

    ret = compare_memory_map(va_base, TEST3_PHY_BASE_ADDR, TEST3_REG_SIZE);

    ASSERT_EQ(ret, true)

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, TEST3_REG_SIZE);
    }
}

TEST_F(manifest_test, mem_map_test_small_size) {
    uint8_t* va_base = MAP_FAILED;
    bool ret;
    uint32_t size = 0x400;

    va_base = mmap(NULL, size, PROT_READ | PROT_WRITE, MMAP_FLAG_IO_HANDLE,
                   TEST1_ID, 0);

    ret = compare_memory_map(va_base, TEST1_PHY_BASE_ADDR, size);

    ASSERT_EQ(ret, true)

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, size);
    }
}

TEST_F(manifest_test, mem_map_test_large_size) {
    uint8_t* va_base = MAP_FAILED;
    uint32_t size = 0x2000;

    va_base = mmap(NULL, size, PROT_READ | PROT_WRITE, MMAP_FLAG_IO_HANDLE,
                   TEST2_ID, 0);
    ASSERT_EQ(va_base, MAP_FAILED);

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, size);
    }
}

TEST_F(manifest_test, mem_map_test_unknown_id) {
    uint8_t* va_base = MAP_FAILED;
    uint32_t id = 100;

    va_base = mmap(NULL, TEST1_REG_SIZE, PROT_READ | PROT_WRITE,
                   MMAP_FLAG_IO_HANDLE, id, 0);
    ASSERT_EQ(va_base, MAP_FAILED);

test_abort:
    if (va_base != MAP_FAILED) {
        munmap(va_base, TEST1_REG_SIZE);
    }
}

PORT_TEST(manifest_test, "com.android.manifesttest");
