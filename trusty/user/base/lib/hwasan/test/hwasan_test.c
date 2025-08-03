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

#include <stdint.h>
#define TLOG_TAG "hwasan-test"

#include <memref.h>
#include <stdlib.h>
#include <trusty_unittest.h>

#include <lib/hwasan/hwasan_shadow.h>
#include <lk/compiler.h>
#include <sys/auxv.h>
#include <sys/mman.h>

#ifndef ARCH_ARM64
#error Trusty only supports HWAsan on arm64
#endif

#define PAGE_SIZE getauxval(AT_PAGESZ)

/* Magic number, only true for Aarch64 */
#define TAGGING_GRANULARITY 16

#define OK 0
#define ERR 1

static int hwasan_error = OK;
void __hwasan_report_error(void) {
    WRITE_ONCE(hwasan_error, ERR);
}

/*
 * This symbol is branched into when HWASan error is detected. Trusty HWASan
 * runtime defines it as a weak symbol. Override it for testing.
 */
static int hwasan_get_error(void) {
    int ret = READ_ONCE(hwasan_error);
    WRITE_ONCE(hwasan_error, OK);
    return ret;
}

/* To make sure variable isn't optimized away */
static void touch(volatile void* a) {
    *((volatile uint8_t*)a) = *((volatile uint8_t*)a);
}

TEST(hwasan, hello_world) {
    TLOGI("Hello World!\n");
    ASSERT_EQ(hwasan_get_error(), OK);
test_abort:;
}

TEST(hwasan, stack_ok) {
    int a = 0;

    WRITE_ONCE(a, 5);
    ASSERT_EQ(hwasan_get_error(), OK);

    ASSERT_EQ(READ_ONCE(a), 5);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:;
}

TEST(hwasan, stack_err) {
    int a = 0;
    int* b = (int*)hwasan_remove_ptr_tag((void*)(&a));

    WRITE_ONCE(*b, 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

    ASSERT_EQ(READ_ONCE(*b), 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:;
}

TEST(hwasan, heap_ok) {
    int* a = malloc(sizeof(int));

    WRITE_ONCE(*a, 5);
    ASSERT_EQ(hwasan_get_error(), OK);

    ASSERT_EQ(READ_ONCE(*a), 5);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:
    free(a);
}

TEST(hwasan, heap_err) {
    int* a = malloc(sizeof(int));
    int* b = (int*)hwasan_remove_ptr_tag((void*)a);

    WRITE_ONCE(*b, 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

    ASSERT_EQ(READ_ONCE(*b), 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:
    free(a);
}

TEST(hwasan, heap_use_after_free) {
    int* a = malloc(sizeof(int));

    WRITE_ONCE(*a, 5);
    ASSERT_EQ(*a, 5);

    free(a);
    ASSERT_EQ(READ_ONCE(*a), 5); /* heap use after free */
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:;
}

TEST(hwasan, memintrinsics_ok) {
    static const size_t size = TAGGING_GRANULARITY;
    int8_t* a = malloc(size);

    touch(a);
    memset(a, 'a', size);
    touch(a);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:
    free(a);
}

TEST(hwasan, memintrinsics_err) {
    static const size_t size = TAGGING_GRANULARITY;
    int8_t* a = malloc(size);

    touch(a);
    memset(a, 'a', size + 1); /* heap buffer overflow */
    touch(a);
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:
    free(a);
}

TEST(hwasan, realloc_tag_new) {
    static const size_t size = TAGGING_GRANULARITY;
    int8_t* a = malloc(size);
    int8_t* b = realloc(a, size + 1);
    ASSERT_NE(b, NULL);

    touch(b);
    memset(b, 'b', size + 1);
    touch(b);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:
    free(b);
}

/* Test that realloc()'ing to new memory makes old memory inaccessible */
TEST(hwasan, realloc_untag_old) {
    static const size_t old_size = TAGGING_GRANULARITY;
    static const size_t new_size = 128 * TAGGING_GRANULARITY;
    static const size_t num_tries = 100;

    int8_t* a;
    int8_t* unused;
    int8_t* b;
    void* a_notag;
    void* b_notag;

    /* Try getting a new memory location from realloc() */
    for (size_t i = 0; i < num_tries; i++) {
        a = malloc(old_size);
        unused = malloc(old_size);
        b = realloc(a, new_size); /* hopefully we get a new address */

        a_notag = hwasan_remove_ptr_tag(a);
        b_notag = hwasan_remove_ptr_tag(b);

        if (a_notag != b_notag) {
            break;
        }

        touch(unused);
        touch(b);
        free(unused);
        free(b);
    }

    /* Might not be true, but better to not ignore this. */
    ASSERT_NE(a_notag, b_notag);

    /*
     * "a" should have been freed and untagged by this point. Writing to &a
     * may corrupt the heap, so avoid doing that.
     */
    WRITE_ONCE(*b, READ_ONCE(*a));
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:
    free(unused);
    free(b);
}

/* Test that realloc()'ing to same memory location retags that memory */
TEST(hwasan, realloc_retag) {
    static const size_t num_tries = 100;

    int8_t* a;
    int8_t* b;
    void* a_notag;
    void* b_notag;

    /* Try getting the same memory location from realloc() */
    for (size_t i = 0; i < num_tries; i++) {
        a = malloc(sizeof(*a));
        b = realloc(a, sizeof(*a)); /* hopefully we get a new address */

        a_notag = hwasan_remove_ptr_tag(a);
        b_notag = hwasan_remove_ptr_tag(b);

        if (a_notag == b_notag) {
            break;
        }

        free(b);
    }

    /* Might not be true, but better to not ignore this. */
    ASSERT_EQ(a_notag, b_notag);

    /*
     * Since there are no allocations between a and b, their tags can't collide
     * (even if they point to same memory).
     */
    ASSERT_NE(a, b);

test_abort:
    free(b);
}

TEST(hwasan, memalign) {
    int* a = memalign(PAGE_SIZE, PAGE_SIZE);
    WRITE_ONCE(*a, 5);
    ASSERT_EQ(READ_ONCE(*a), 5);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:
    free(a);
}

TEST(hwasan, memref_create) {
    void* a = memalign(PAGE_SIZE, PAGE_SIZE);
    int handle = memref_create(a, PAGE_SIZE,
                               MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE);

    ASSERT_GE(handle, 0);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:
    close(handle);
    free(a);
}

#define MEM_MAP_ID 1
#define MEM_MAP_ADDR 0x70000000U
#define MEM_MAP_SIZE 0x1000U

TEST(hwasan, mmap) {
    int ret;
    struct dma_pmem pmem;
    int* a;
    uint32_t dma_flags = DMA_FLAG_FROM_DEVICE | DMA_FLAG_ALLOW_PARTIAL;

    for (size_t i = 0; i < 10; i++) {
        a = mmap(NULL, MEM_MAP_SIZE, PROT_READ | PROT_WRITE,
                 MMAP_FLAG_IO_HANDLE, MEM_MAP_ID, 0);
        ASSERT_NE(a, MAP_FAILED);

        ret = prepare_dma(a, MEM_MAP_SIZE, dma_flags, &pmem);
        ASSERT_EQ(ret, 1);
        ASSERT_EQ(pmem.paddr, MEM_MAP_ADDR);
        ASSERT_EQ(pmem.size, MEM_MAP_SIZE);

        touch(a);
        finish_dma(a, MEM_MAP_SIZE, dma_flags);
        munmap(a, MEM_MAP_SIZE);
    }

    return;

test_abort:
    finish_dma(a, MEM_MAP_SIZE, dma_flags);
    munmap(a, MEM_MAP_SIZE);
}

TEST(hwasan, mmap_ok) {
    int* a = mmap(NULL, MEM_MAP_SIZE, PROT_READ | PROT_WRITE,
                  MMAP_FLAG_IO_HANDLE, MEM_MAP_ID, 0);
    ASSERT_NE(a, MAP_FAILED);

    WRITE_ONCE(*a, 5);
    ASSERT_EQ(hwasan_get_error(), OK);

    ASSERT_EQ(READ_ONCE(*a), 5);
    ASSERT_EQ(hwasan_get_error(), OK);

test_abort:;
    munmap(a, MEM_MAP_SIZE);
}

TEST(hwasan, mmap_err) {
    int* a = mmap(NULL, MEM_MAP_SIZE, PROT_READ | PROT_WRITE,
                  MMAP_FLAG_IO_HANDLE, MEM_MAP_ID, 0);
    ASSERT_NE(a, MAP_FAILED);
    int* b = (int*)hwasan_remove_ptr_tag((void*)a);

    WRITE_ONCE(*b, 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

    ASSERT_EQ(READ_ONCE(*b), 5);
    ASSERT_EQ(hwasan_get_error(), ERR);

test_abort:;
    munmap(a, MEM_MAP_SIZE);
}

static int bss_int;

TEST(hwasan, bss_ok) {
    WRITE_ONCE(bss_int, 5);
    ASSERT_EQ(bss_int, 5);
    ASSERT_EQ(hwasan_get_error(), OK);
test_abort:;
}

TEST(hwasan, bss_err) {
    int* a = (int*)hwasan_remove_ptr_tag((void*)(&bss_int));
    WRITE_ONCE(*a, 6);
    ASSERT_EQ(READ_ONCE(*a), 6);

    /* TODO(b/148877030): Sanitize globals */
    ASSERT_EQ(hwasan_get_error(), OK);
test_abort:;
}

PORT_TEST(hwcrypto, "com.android.trusty.hwasan.user.test")
