/*
 * Copyright (c) 2021, Google Inc. All rights reserved
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

#include <err.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/mmutest/mmutest.h>
#include <lib/trusty/trusty_app.h>
#include <lib/unittest/unittest.h>

#if USER_SCS_ENABLED
#define FEATURE_GATED_TEST_NAME(name) name

/**
 * translate_uspace_ptr() - Translate userspace pointer to shadow stack
 * @uspace:         An address space of a trust app
 * @uspace_ptr:     Pointer into above user space.
 * @kspace_ptr_out: Pointer translated into the kernel's address space. The
 *                  value of this output parameter should not be consumed
 *                  if this function returned a negative error code.
 *
 * Attempt to translate userspace pointer into its kernel equivalent. We
 * cannot simply dereference a userspace pointer because the TLB isn't
 * loaded with the correct page table. Instead, we translate the pointer
 * to physical memory and then into the kernel virtual address space.
 * Translation can fail the userspace address is not backed by physical
 * memory; this is the case for guard pages.
 *
 * Return:
 * * NO_ERROR         - if translation succeeded
 * * ERR_OUT_OF_RANGE - if address translation failed
 * * ERR_NOT_FOUND    - if pointer is not backed by physical memory
 * * ERR_INVALID_ARGS - if kspace_ptr_out is NULL
 */
static int translate_uspace_ptr(struct vmm_aspace* uspace,
                                vaddr_t uspace_ptr,
                                vaddr_t* kspace_ptr_out) {
    paddr_t phys_ptr;
    uint flags;

    if (kspace_ptr_out == NULL) {
        return ERR_INVALID_ARGS;
    }
    *kspace_ptr_out = 0;

    /* translate userspace virtual address to physical address */
    status_t res =
            arch_mmu_query(&uspace->arch_aspace, uspace_ptr, &phys_ptr, &flags);
    /*
     * failures can happen if the pointer is invalid or points to a guard page
     * not backed by physical memory (in which case res is ERR_NOT_FOUND).
     */
    if (res) {
        return res;
    }

    EXPECT_EQ(flags, ARCH_MMU_FLAG_PERM_NO_EXECUTE | ARCH_MMU_FLAG_PERM_USER,
              "Shadow call stack must point to non-executable user memory");

    /* translate physical address to kernel virtual address */
    *kspace_ptr_out = (vaddr_t)paddr_to_kvaddr(phys_ptr);
    ASSERT_NE(0, *kspace_ptr_out,
              "Failed to map phys addr to kernel virtual addr");

    return NO_ERROR;
test_abort:
    return ERR_OUT_OF_RANGE;
}

struct scs_test_status {
    uint32_t running_apps;
    uint32_t invalid_apps;
};

/**
 * trusty_app_callback() - Test that app has a valid user shadow call stack
 *
 * @ta: Application to test
 * @status: Pointer to a structure tracking the current test status.
 */
static void trusty_app_callback(struct trusty_app* ta, void* status) {
    struct scs_test_status* status_ptr = (struct scs_test_status*)status;
    if (strcmp(ta->props.app_name, "userscs-custom") == 0) {
        /* were we able to request a custom shadow stack size? */
        ASSERT_EQ(ta->props.min_shadow_stack_size, 128);
    } else if (strcmp(ta->props.app_name, "userscs-disabled") == 0) {
        /* were we able to opt out of shadow stacks? */
        ASSERT_EQ(ta->props.min_shadow_stack_size, 0);
        /* userscs-* apps loop infinitely so they'll always have a thread */
        ASSERT_NE((void*)ta->thread, NULL, "App has thread");
        ASSERT_EQ((void*)ta->thread->shadow_stack_base, NULL,
                  "Shadow call stack was disabled");
        return;
    } else if (strcmp(ta->props.app_name, "userscs-default") == 0) {
        /* did default scs app get the default shadow stack size? */
        ASSERT_EQ(ta->props.min_shadow_stack_size, DEFAULT_SHADOW_STACK_SIZE,
                  "Expected shadow call stack to have the default size");
    }

    /* size must be a multiple of the pointer size */
    ASSERT_EQ(0, ta->props.min_shadow_stack_size % sizeof(vaddr_t),
              "Shadow call stack size is not a multiple of the pointer size");

    /*
     * Apps that aren't running may not have a thread allocated. Moreover,
     * apps that opt out of shadow call stacks need no further inspection.
     */
    if (ta->state != APP_RUNNING || ta->props.min_shadow_stack_size == 0) {
        return;
    }
    status_ptr->running_apps++;

    struct trusty_thread* tt = ta->thread;
    ASSERT_NE((void*)tt, NULL, "Running trusty app must have a valid thread");
    ASSERT_NE((void*)tt->shadow_stack_base, NULL,
              "Shadow call stack must point to allocation");

    ASSERT_EQ(false, is_kernel_address(tt->shadow_stack_base),
              "Shadow stack on user thread points to kernel memory");

    ASSERT_NE(tt->stack_start, tt->shadow_stack_base,
              "Shadow stack on user thread aliases the regular stack");

    /*
     * Check the shadow stack size by examining the last element and one past
     * the last element. Note that these pointers are valid in the address
     * space of the trusty app, not in the current address space of a kernel
     * app. Therefore, we translate from the app's address space to the kernel
     * addres space before dereferencing lest we generate an access violation.
     */

    vaddr_t past_last = (vaddr_t)tt->shadow_stack_base + tt->shadow_stack_size;
    vaddr_t last_elem = past_last - sizeof(vaddr_t);
    /* a whole number of pages is allocated no matter the shadow stack size */
    vaddr_t pre_first = last_elem - round_up(tt->shadow_stack_size, PAGE_SIZE);
    vaddr_t elem_translated, ignored;

    struct vmm_aspace* uspace = ta->aspace;

    ASSERT_EQ(NO_ERROR,
              translate_uspace_ptr(uspace, last_elem, &elem_translated),
              "Actual size of shadow stack differs from recorded size");
    /*
     * Check that test app uses its shadow stack as expected. The shadow call
     * stack is zero-initialized and once a stack slot is used it can never
     * become zero again.
     */
    if (strcmp(ta->props.app_name, "userscs-default") == 0) {
        EXPECT_EQ(0, *(vaddr_t*)elem_translated,
                  "Expected last element of shadow stack to be zero "
                  "(unused)");

        ASSERT_EQ(NO_ERROR, translate_uspace_ptr(uspace, tt->shadow_stack_base,
                                                 &elem_translated));
        /*
         * The link register is initially zero so when shadow call stacks are
         * enabled for libc, the second element holds the first non-zero entry
         */
        EXPECT_NE(0, *(vaddr_t*)elem_translated + sizeof(vaddr_t),
                  "Expected second element of shadow stack to be non-zero "
                  "(used)");
    }

    ASSERT_EQ(ERR_NOT_FOUND, translate_uspace_ptr(uspace, past_last, &ignored),
              "Expected guard page after shadow stack on user thread");

    ASSERT_EQ(ERR_NOT_FOUND, translate_uspace_ptr(uspace, pre_first, &ignored),
              "Expected guard page before shadow stack on user thread");

    return;
test_abort:
    status_ptr->invalid_apps++;
}

static int inspect_trusty_threads(void) {
    struct scs_test_status status = {0};
    trusty_app_forall(trusty_app_callback, &status);
    ASSERT_NE(0, status.running_apps);
    return status.invalid_apps;
test_abort:
    return -1;
}
#else
#define FEATURE_GATED_TEST_NAME(name) DISABLED_##name

static int inspect_trusty_threads(void) {
    return 0;
}

#endif

TEST(userscstest, FEATURE_GATED_TEST_NAME(user_threads_have_scs)) {
    int res = inspect_trusty_threads();
    EXPECT_NE(-1, res, "There were no running apps with threads to inspect");
    EXPECT_EQ(0, res,
              "One or more apps did not have the expected shadow call stack");
}

PORT_TEST(userscstest, "com.android.kernel.userscstest");
