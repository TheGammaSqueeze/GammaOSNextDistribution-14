/*
 * Copyright (c) 2015, Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <assert.h>
#include <err.h>
#include <kernel/usercopy.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdio.h>
#include <string.h>
#include <trusty/string.h>
#ifdef ARCH_ARM64
#include <arch/safecopy.h>
#endif

#define PORT_NAME "com.android.kernel.usercopy-unittest"

#define TEST_BUF_SIZE (16)
#define TEST_BUF1_SIZE (TEST_BUF_SIZE / 2)
#define TEST_BUF2_SIZE (TEST_BUF_SIZE - TEST_BUF1_SIZE)
#define TEST_BUF_COPY_START (1)
#define TEST_BUF_COPY_SIZE (TEST_BUF_SIZE - TEST_BUF_COPY_START - 1)
#define TEST_BUF1_COPY_SIZE (TEST_BUF1_SIZE - TEST_BUF_COPY_START)
#define TEST_BUF2_COPY_SIZE (TEST_BUF_COPY_SIZE - TEST_BUF1_COPY_SIZE)
#define TEST_BUF_COPY_LAST (TEST_BUF_SIZE - 1 - 1)
#define TEST_BUF2_COPY_LAST (TEST_BUF_COPY_LAST - TEST_BUF1_SIZE)

#define SRC_DATA (0x22)
#define DEST_DATA (0x11)

#define FLAGS_NO_PAGE (ARCH_MMU_FLAG_INVALID)
#define FLAGS_NO_USER (0u)
#define FLAGS_RO_USER (ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_RO)
#define FLAGS_RW_USER (ARCH_MMU_FLAG_PERM_USER)

#define STACK_ADDR_IDX (0)
#define HEAP_ADDR_IDX (1)
#define GLOBAL_ADDR_IDX (2)

#define START_PAGE_ADDR ((void*)(PAGE_SIZE * 0x10))
#define TEST_BUF_ADDR \
    ((user_addr_t)((uintptr_t)(START_PAGE_ADDR + PAGE_SIZE - TEST_BUF1_SIZE)))

static inline user_addr_t get_addr_param(void) {
    const void* const* param_arr = GetParam();
    const user_addr_t* addr = param_arr[0];
    return *addr;
}

static inline uint32_t get_start_flags_param(void) {
    const void* const* param_arr = GetParam();
    const uint32_t* start_flags = param_arr[1];
    return *start_flags;
}

static inline uint32_t get_end_flags_param(void) {
    const void* const* param_arr = GetParam();
    const uint32_t* end_flags = param_arr[2];
    return *end_flags;
}

static int checkbuf(const char* buf, char c, size_t size) {
    int error_count = 0;
    for (size_t i = 0; i < size; i++) {
        if (buf[i] != c) {
            error_count++;
        }
    }
    return error_count;
}

static void usercopy_test_init_buf(char* kbuf1,
                                   char* kbuf2,
                                   uint8_t val,
                                   int null_offset) {
    if (kbuf1) {
        memset(kbuf1, val, TEST_BUF1_SIZE);
        if (null_offset >= 0 && null_offset < TEST_BUF1_SIZE) {
            kbuf1[null_offset] = '\0';
        }
    }
    if (kbuf2) {
        memset(kbuf2, val, TEST_BUF2_SIZE);
        if (null_offset >= TEST_BUF1_SIZE && null_offset < TEST_BUF_SIZE) {
            kbuf2[null_offset - TEST_BUF1_SIZE] = '\0';
        }
    }
}

typedef struct {
    struct vmm_aspace* aspace;
} usercopytest_t;

TEST_F_SETUP(usercopytest) {
    int ret;
    void* addr = START_PAGE_ADDR;
    uint32_t start_flags = get_start_flags_param();
    uint32_t end_flags = get_end_flags_param();

    _state->aspace = NULL;

    ret = vmm_create_aspace(&_state->aspace, "usercopy_test", 0);
    ASSERT_EQ(NO_ERROR, ret);

    if (start_flags != FLAGS_NO_PAGE) {
        ret = vmm_alloc(_state->aspace, "start-page", PAGE_SIZE, &addr, 0,
                        VMM_FLAG_VALLOC_SPECIFIC | VMM_FLAG_NO_END_GUARD,
                        start_flags);
        ASSERT_EQ(NO_ERROR, ret);
        ASSERT_EQ(START_PAGE_ADDR, addr);
    }

    addr += PAGE_SIZE;

    if (end_flags != FLAGS_NO_PAGE) {
        ret = vmm_alloc(_state->aspace, "end-page", PAGE_SIZE, &addr, 0,
                        VMM_FLAG_VALLOC_SPECIFIC | VMM_FLAG_NO_START_GUARD,
                        end_flags);
        ASSERT_EQ(NO_ERROR, ret);
        ASSERT_EQ(START_PAGE_ADDR + PAGE_SIZE, addr);
    }

    vmm_set_active_aspace(_state->aspace);

test_abort:
    return;
}

TEST_F_TEARDOWN(usercopytest) {
    vmm_set_active_aspace(NULL);

    if (_state->aspace) {
        vmm_free_aspace(_state->aspace);
    }
}

TEST_P(usercopytest, copy_to_user) {
    user_addr_t addr = get_addr_param();
    uint32_t arch_mmu_flags_start = get_start_flags_param();
    uint32_t arch_mmu_flags_end = get_end_flags_param();
    int ret;
    char src_buf[TEST_BUF_SIZE];
    char* dest_kbuf1;
    char* dest_kbuf2;
    char expect1;
    char expect2;

    dest_kbuf1 = paddr_to_kvaddr(vaddr_to_paddr((void*)(uintptr_t)addr));
    dest_kbuf2 = paddr_to_kvaddr(
            vaddr_to_paddr((void*)(uintptr_t)addr + TEST_BUF1_SIZE));

    /* dest buffs should be NULL iff their flags are FLAGS_NO_PAGE */
    EXPECT_EQ((dest_kbuf1 == NULL), (arch_mmu_flags_start == FLAGS_NO_PAGE));
    EXPECT_EQ((dest_kbuf2 == NULL), (arch_mmu_flags_end == FLAGS_NO_PAGE));

    usercopy_test_init_buf(dest_kbuf1, dest_kbuf2, DEST_DATA, -1);
    memset(src_buf, SRC_DATA, sizeof(src_buf));

    /* Zero-length copy should always succeed */
    ret = copy_to_user(addr + TEST_BUF_COPY_START, NULL, 0);
    EXPECT_EQ(0, ret);

    /* Dest buffer should be untouched after zero-length copy */
    if (dest_kbuf1) {
        EXPECT_EQ(0, checkbuf(dest_kbuf1, DEST_DATA, TEST_BUF1_SIZE));
    }
    if (dest_kbuf2) {
        EXPECT_EQ(0, checkbuf(dest_kbuf2, DEST_DATA, TEST_BUF2_SIZE));
    }

    /* Perform non-zero length copy */
    ret = copy_to_user(addr + TEST_BUF_COPY_START,
                       src_buf + TEST_BUF_COPY_START, TEST_BUF_COPY_SIZE);

    /*
     * If both pages are writeable copy_to_user should succeed otherwise it
     * should return ERR_FAULT.
     */
    if (arch_mmu_flags_start == ARCH_MMU_FLAG_PERM_USER &&
        arch_mmu_flags_end == ARCH_MMU_FLAG_PERM_USER) {
        /*
         * If both pages are writeable from user-space copy_to_user should
         * return success and every byte should be copied to dest_buf.
         */
        EXPECT_EQ(0, ret);
        expect1 = SRC_DATA;
        expect2 = SRC_DATA;
    } else {
        /*
         * If one of the pages is not writeable from user-space copy_to_user
         * should return ERR_FAULT. If only the first page is writeable everying
         * should be copied in the first page or nothing should be copied in the
         * first page. If the first page is not writeable, nothing should be
         * copied to either page. If the second page is not writeable, no data
         * should be copied to it, even if the first page was written to.
         */
        EXPECT_EQ(ERR_FAULT, ret);
        if (arch_mmu_flags_start == ARCH_MMU_FLAG_PERM_USER &&
            dest_kbuf1[TEST_BUF_COPY_START] == SRC_DATA) {
            expect1 = SRC_DATA;
        } else {
            expect1 = DEST_DATA;
        }
        expect2 = DEST_DATA;
    }

    /* copy_to_user should not modify src_buf at all */
    EXPECT_EQ(0, checkbuf(src_buf, SRC_DATA, TEST_BUF_SIZE));

    if (dest_kbuf1) {
        /* Dest byte before copied region should be untouched */
        EXPECT_EQ(DEST_DATA, dest_kbuf1[0]);

        /* Check that copied region match expected value we selected above */
        EXPECT_EQ(0, checkbuf(dest_kbuf1 + TEST_BUF_COPY_START, expect1,
                              TEST_BUF1_COPY_SIZE));
    }

    if (dest_kbuf2) {
        /* Check that copied region match expected value we selected above */
        EXPECT_EQ(0, checkbuf(dest_kbuf2, expect2, TEST_BUF2_COPY_SIZE));

        /* Dest byte after copied region should be untouched */
        EXPECT_EQ(DEST_DATA, dest_kbuf2[TEST_BUF2_SIZE - 1]);
    }
}

TEST_P(usercopytest, copy_from_user) {
    user_addr_t addr = get_addr_param();
    uint32_t arch_mmu_flags_start = get_start_flags_param();
    uint32_t arch_mmu_flags_end = get_end_flags_param();
    int ret;
    char dest_buf[TEST_BUF_SIZE];
    char* src_kbuf1;
    char* src_kbuf2;
    char expect1;
    char expect2;

    memset(dest_buf, DEST_DATA, sizeof(dest_buf));
    src_kbuf1 = paddr_to_kvaddr(vaddr_to_paddr((void*)(uintptr_t)addr));
    src_kbuf2 = paddr_to_kvaddr(
            vaddr_to_paddr((void*)(uintptr_t)addr + TEST_BUF1_SIZE));

    /* src buffs should be NULL iff their flags are FLAGS_NO_PAGE */
    EXPECT_EQ((src_kbuf1 == NULL), (arch_mmu_flags_start == FLAGS_NO_PAGE));
    EXPECT_EQ((src_kbuf2 == NULL), (arch_mmu_flags_end == FLAGS_NO_PAGE));

    usercopy_test_init_buf(src_kbuf1, src_kbuf2, SRC_DATA, -1);

    /* Zero-length copy should always succeed */
    ret = copy_from_user(NULL, addr + TEST_BUF_COPY_START, 0);
    EXPECT_EQ(0, ret);

    /* Dest buffer should be untouched after zero-length copy */
    EXPECT_EQ(0, checkbuf(dest_buf, DEST_DATA, TEST_BUF_SIZE));

    /* Perform non-zero length copy */
    ret = copy_from_user(dest_buf + TEST_BUF_COPY_START,
                         addr + TEST_BUF_COPY_START, TEST_BUF_COPY_SIZE);
    if (arch_mmu_flags_start & arch_mmu_flags_end & ARCH_MMU_FLAG_PERM_USER) {
        /*
         * If both pages are readable from user-space copy_from_user should
         * return success and every byte should be copied to dest_buf.
         */
        EXPECT_EQ(0, ret);
        expect1 = SRC_DATA;
        expect2 = SRC_DATA;
    } else {
        /*
         * If one of the pages is not readable from user-space copy_from_user
         * should return ERR_FAULT, and the parts of dest_buf that could not be
         * copied into should be set to 0.
         * The destination kernel buffer should always be written so
         * potentially uninitialized kernel data does not leak.
         */
        EXPECT_EQ(ERR_FAULT, ret);
        if (!(arch_mmu_flags_start & ARCH_MMU_FLAG_PERM_USER) ||
            !dest_buf[TEST_BUF_COPY_START]) {
            expect1 = 0;
        } else {
            expect1 = SRC_DATA;
        }
        expect2 = 0;
    }

    EXPECT_EQ(0, checkbuf(dest_buf + TEST_BUF_COPY_START, expect1,
                          TEST_BUF1_COPY_SIZE));
    EXPECT_EQ(0, checkbuf(dest_buf + TEST_BUF1_SIZE, expect2,
                          TEST_BUF2_COPY_SIZE));

    /* Dest bytes before and after copied region should be untouched */
    EXPECT_EQ(DEST_DATA, dest_buf[0]);
    EXPECT_EQ(DEST_DATA, dest_buf[TEST_BUF_SIZE - 1]);

    /* Src buffer should not be modified */
    if (src_kbuf1) {
        EXPECT_EQ(0, checkbuf(src_kbuf1, SRC_DATA, TEST_BUF1_SIZE));
    }
    if (src_kbuf2) {
        EXPECT_EQ(0, checkbuf(src_kbuf2, SRC_DATA, TEST_BUF2_SIZE));
    }
}

#if ARCH_ARM64
#define ENABLED_ON_ARM64_NAME(name) name
#else
#define ENABLED_ON_ARM64_NAME(name) DISABLED_##name
#define copy_from_anywhere(dst, src, len) -1
#endif

TEST_P(usercopytest, ENABLED_ON_ARM64_NAME(copy_from_anywhere)) {
    user_addr_t addr = get_addr_param();
    uint32_t arch_mmu_flags_start = get_start_flags_param();
    uint32_t arch_mmu_flags_end = get_end_flags_param();
    int ret;
    char dest_buf[TEST_BUF_SIZE];
    char* src_kbuf1;
    char* src_kbuf2;
    char expect1;
    char expect2;

    memset(dest_buf, DEST_DATA, sizeof(dest_buf));
    src_kbuf1 = paddr_to_kvaddr(vaddr_to_paddr((void*)(uintptr_t)addr));
    src_kbuf2 = paddr_to_kvaddr(
            vaddr_to_paddr((void*)(uintptr_t)addr + TEST_BUF1_SIZE));

    /* src buffs should be NULL iff their flags are FLAGS_NO_PAGE */
    EXPECT_EQ((src_kbuf1 == NULL), (arch_mmu_flags_start == FLAGS_NO_PAGE));
    EXPECT_EQ((src_kbuf2 == NULL), (arch_mmu_flags_end == FLAGS_NO_PAGE));

    usercopy_test_init_buf(src_kbuf1, src_kbuf2, SRC_DATA, -1);

    /* Zero-length copy should always succeed */
    ret = copy_from_anywhere(NULL, addr + TEST_BUF_COPY_START, 0);
    EXPECT_EQ(0, ret);

    /* Dest buffer should be untouched after zero-length copy */
    EXPECT_EQ(0, checkbuf(dest_buf, DEST_DATA, TEST_BUF_SIZE));

    /* Perform non-zero length copy */
    ret = copy_from_anywhere(dest_buf + TEST_BUF_COPY_START,
                             addr + TEST_BUF_COPY_START, TEST_BUF_COPY_SIZE);
    if (arch_mmu_flags_start != FLAGS_NO_PAGE &&
        arch_mmu_flags_end != FLAGS_NO_PAGE) {
        /*
         * If both pages are readable, copy_from_anywhere should return
         * success and every byte should be copied to dest_buf.
         */
        EXPECT_EQ(0, ret);
        expect1 = SRC_DATA;
        expect2 = SRC_DATA;
    } else {
        /*
         * If one of the pages is not readable copy_from_anywhere should
         * return ERR_FAULT, and the parts of dest_buf that could not be
         * copied into should be set to 0.
         * The destination kernel buffer should always be written so
         * potentially uninitialized kernel data does not leak.
         */
        EXPECT_EQ(ERR_FAULT, ret);
        if (arch_mmu_flags_start == FLAGS_NO_PAGE) {
            expect1 = 0;
        } else {
            expect1 = SRC_DATA;
        }
        expect2 = 0;
    }

    EXPECT_EQ(0, checkbuf(dest_buf + TEST_BUF_COPY_START, expect1,
                          TEST_BUF1_COPY_SIZE));
    EXPECT_EQ(0, checkbuf(dest_buf + TEST_BUF1_SIZE, expect2,
                          TEST_BUF2_COPY_SIZE));

    /* Dest bytes before and after copied region should be untouched */
    EXPECT_EQ(DEST_DATA, dest_buf[0]);
    EXPECT_EQ(DEST_DATA, dest_buf[TEST_BUF_SIZE - 1]);

    /* Src buffer should not be modified */
    if (src_kbuf1) {
        EXPECT_EQ(0, checkbuf(src_kbuf1, SRC_DATA, TEST_BUF1_SIZE));
    }
    if (src_kbuf2) {
        EXPECT_EQ(0, checkbuf(src_kbuf2, SRC_DATA, TEST_BUF2_SIZE));
    }
}

static void usercopy_test_strlcpy_from_user_inner(user_addr_t addr,
                                                  uint arch_mmu_flags_start,
                                                  uint arch_mmu_flags_end,
                                                  int copy_size,
                                                  int null_off) {
    int ret;
    char dest_buf[TEST_BUF_SIZE];
    char* src_kbuf1;
    char* src_kbuf2;
    size_t dest_len;
    int copy_len = copy_size ? copy_size - 1 : 0;

    memset(dest_buf, DEST_DATA, sizeof(dest_buf));
    src_kbuf1 = paddr_to_kvaddr(vaddr_to_paddr((void*)(uintptr_t)addr));
    src_kbuf2 = paddr_to_kvaddr(
            vaddr_to_paddr((void*)(uintptr_t)addr + TEST_BUF1_SIZE));

    /* src buffs should be NULL iff their flags are FLAGS_NO_PAGE */
    EXPECT_EQ((src_kbuf1 == NULL), (arch_mmu_flags_start == FLAGS_NO_PAGE));
    EXPECT_EQ((src_kbuf2 == NULL), (arch_mmu_flags_end == FLAGS_NO_PAGE));

    usercopy_test_init_buf(src_kbuf1, src_kbuf2, SRC_DATA, null_off);

    ret = strlcpy_from_user(dest_buf + TEST_BUF_COPY_START,
                            addr + TEST_BUF_COPY_START, copy_size);

    dest_len = strnlen(dest_buf + TEST_BUF_COPY_START, TEST_BUF_COPY_SIZE);
    if (copy_size) {
        /*
         * Kernel buffer should always be null terminated.
         */
        EXPECT_NE(TEST_BUF_COPY_SIZE, dest_len, "  null_off=%d, copy_size=%d\n",
                  null_off, copy_size);
    } else {
        /*
         * If copy_size is 0, then kernel buffer will not be null terminated.
         */
        EXPECT_EQ(TEST_BUF_COPY_SIZE, dest_len, "  null_off=%d, copy_size=%d\n",
                  null_off, copy_size);
        dest_len = 0;
    }

    /*
     * If the string in dest_buf is not empty it should only contain data from
     * the source string.
     */
    EXPECT_EQ(0, checkbuf(dest_buf + TEST_BUF_COPY_START, SRC_DATA, dest_len),
              "  null_off=%d, copy_size=%d\n", null_off, copy_size);

    if ((arch_mmu_flags_start & ARCH_MMU_FLAG_PERM_USER) &&
        ((arch_mmu_flags_end & ARCH_MMU_FLAG_PERM_USER) ||
         null_off < TEST_BUF1_SIZE)) {
        /*
         * If the pages readable from user-space contain a 0 terminated string,
         * strlcpy_from_user should return the length of that string and every
         * byte up to the 0 terminator that fits in dest_buf should be copied
         * there. dest_buf should always be 0 terminated.
         */
        EXPECT_EQ(null_off - TEST_BUF_COPY_START, ret,
                  "  wrong strlen returned, null_off=%d, copy_size=%d\n",
                  null_off, copy_size);
        EXPECT_EQ(MIN(null_off - TEST_BUF_COPY_START, copy_len), dest_len,
                  "  null_off=%d, copy_size=%d\n", null_off, copy_size);
    } else {
        /*
         * If one of the pages is not readable from user-space strlcpy_from_user
         * should return ERR_FAULT, and dest_buf should have a null terminator
         * at the start of the faulting page or at the start of the string.
         */
        EXPECT_EQ(ERR_FAULT, ret, "  null_off=%d, copy_size=%d\n", null_off,
                  copy_size);
        if (!(arch_mmu_flags_start & ARCH_MMU_FLAG_PERM_USER)) {
            EXPECT_EQ(0, dest_len, "  null_off=%d, copy_size=%d\n", null_off,
                      copy_size);
        } else if (dest_len) {
            EXPECT_EQ(MIN(TEST_BUF1_COPY_SIZE, copy_len), dest_len,
                      "  null_off=%d, copy_size=%d\n", null_off, copy_size);
        }
    }

    /* Src buffer should not be modified */
    if (src_kbuf1) {
        if (null_off < TEST_BUF1_SIZE) {
            EXPECT_EQ(0, checkbuf(src_kbuf1, SRC_DATA, null_off));
            EXPECT_EQ('\0', src_kbuf1[null_off]);
            EXPECT_EQ(0, checkbuf(src_kbuf1 + null_off + 1, SRC_DATA,
                                  TEST_BUF1_SIZE - null_off - 1));
        } else {
            EXPECT_EQ(0, checkbuf(src_kbuf1, SRC_DATA, TEST_BUF1_SIZE));
        }
    }
    if (src_kbuf2) {
        if (null_off >= TEST_BUF1_SIZE) {
            size_t null_off2 = null_off - TEST_BUF1_SIZE;
            EXPECT_EQ(0, checkbuf(src_kbuf2, SRC_DATA, null_off2));
            EXPECT_EQ('\0', src_kbuf2[null_off2]);
            EXPECT_EQ(0, checkbuf(src_kbuf2 + null_off2 + 1, SRC_DATA,
                                  TEST_BUF2_SIZE - null_off2 - 1));
        } else {
            EXPECT_EQ(0, checkbuf(src_kbuf2, SRC_DATA, TEST_BUF2_SIZE));
        }
    }

    /* Dest bytes before and after copied region should be untouched */
    EXPECT_EQ(DEST_DATA, dest_buf[0]);
    EXPECT_EQ(DEST_DATA, dest_buf[TEST_BUF_COPY_START + copy_size]);
    EXPECT_EQ(DEST_DATA, dest_buf[TEST_BUF_SIZE - 1]);
}

TEST_P(usercopytest, strlcpy_from_user) {
    user_addr_t addr = get_addr_param();
    uint32_t arch_mmu_flags_start = get_start_flags_param();
    uint32_t arch_mmu_flags_end = get_end_flags_param();
    size_t copy_sizes[] = {0, TEST_BUF1_COPY_SIZE, TEST_BUF_COPY_SIZE};
    size_t copy_sizes_index;
    int null_off;
    int copy_size;

    for (copy_sizes_index = 0; copy_sizes_index < countof(copy_sizes);
         copy_sizes_index++) {
        copy_size = copy_sizes[copy_sizes_index];
        for (null_off = TEST_BUF_COPY_START; null_off < TEST_BUF_SIZE;
             null_off++) {
            usercopy_test_strlcpy_from_user_inner(addr, arch_mmu_flags_start,
                                                  arch_mmu_flags_end, copy_size,
                                                  null_off);
        }
    }
}

static const char* flags_to_str(uint32_t flags) {
    switch (flags) {
    case FLAGS_NO_PAGE:
        return "--";
    case FLAGS_NO_USER:
        return "ko";
    case FLAGS_RO_USER:
        return "ro";
    case FLAGS_RW_USER:
        return "rw";
    default:
        return "??";
    }
}

static void user_param_to_string(const void* param,
                                 char* buf,
                                 size_t buf_size) {
    uint32_t start_flags = get_start_flags_param();
    uint32_t end_flags = get_end_flags_param();
    size_t count = 0;

    count = scnprintf(buf + count, buf_size - count, "%s",
                      flags_to_str(start_flags));
    scnprintf(buf + count, buf_size - count, "%s", flags_to_str(end_flags));
}

INSTANTIATE_TEST_SUITE_P(UserCopyTestParams,
                         usercopytest,
                         testing_Combine(testing_Values(TEST_BUF_ADDR),
                                         testing_Values(FLAGS_NO_PAGE,
                                                        FLAGS_NO_USER,
                                                        FLAGS_RO_USER,
                                                        FLAGS_RW_USER),
                                         testing_Values(FLAGS_NO_PAGE,
                                                        FLAGS_NO_USER,
                                                        FLAGS_RO_USER,
                                                        FLAGS_RW_USER)),
                         user_param_to_string);

#if IS_64BIT && USER_32BIT
/*
 * Tests with Kernel addresses are not applicable to arm64u32 since kernel
 * addresses do not fit in a user_addr_t.
 */
static_assert(KERNEL_BASE > UINT32_MAX);

PORT_TEST(usercopy_tests, PORT_NAME)

#else
/* These are filled in before the tests are run */
static user_addr_t kernel_addrs[3];

static void kernel_param_to_string(const void* param,
                                   char* buf,
                                   size_t buf_size) {
    const void* const* kernel_param = param;
    size_t idx = ((user_addr_t*)kernel_param[0] - kernel_addrs);
    const char* str;

    switch (idx) {
    case STACK_ADDR_IDX:
        str = "kernel-stack";
        break;
    case HEAP_ADDR_IDX:
        str = "kernel-heap";
        break;
    case GLOBAL_ADDR_IDX:
        str = "kernel-global";
        break;
    default:
        str = "unknown-address-type";
    }

    scnprintf(buf, buf_size, "%s", str);
}

INSTANTIATE_TEST_SUITE_P(KernelUserCopyTestParams,
                         usercopytest,
                         testing_Combine(testing_ValuesIn(kernel_addrs),
                                         testing_Values(FLAGS_NO_USER),
                                         testing_Values(FLAGS_NO_USER)),
                         kernel_param_to_string);

static bool run_usercopy_test(struct unittest* test) {
    bool tests_passed;
    static uint8_t global_buf[TEST_BUF_SIZE];
    uint8_t stack_buf[TEST_BUF_SIZE];
    uint8_t* heap_buf = malloc(TEST_BUF_SIZE);

    ASSERT(heap_buf);

    kernel_addrs[STACK_ADDR_IDX] = (user_addr_t)stack_buf;
    kernel_addrs[HEAP_ADDR_IDX] = (user_addr_t)heap_buf;
    kernel_addrs[GLOBAL_ADDR_IDX] = (user_addr_t)global_buf;

    tests_passed = RUN_ALL_TESTS();

    free(heap_buf);

    return tests_passed;
}

static void usercopy_test_init(uint level) {
    static struct unittest usercopy_unittest = {
            .port_name = PORT_NAME,
            .run_test = run_usercopy_test,
    };

    unittest_add(&usercopy_unittest);
}

LK_INIT_HOOK(usercopy_test, usercopy_test_init, LK_INIT_LEVEL_APPS);
#endif  // !(IS_64BIT && USER_32BIT)
