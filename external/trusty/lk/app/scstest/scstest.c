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
#include <lib/unittest/unittest.h>
#include <lib/mmutest/mmutest.h>

#if KERNEL_SCS_ENABLED
#define FEATURE_GATED_TEST_NAME(name) name

/**
 * inspect_thread() - Inspect the shadow stack of a thread
 *
 * @t: thread to test
 * @ss_sz: expected shadow stack size
 */
static void inspect_thread(thread_t *t, size_t ss_sz) {
    /* all threads have a shadow stack when the feature is enabled */
    ASSERT_NE(NULL, t->shadow_stack, "Shadow call stack missing");
    ASSERT_EQ(true, is_kernel_address((vaddr_t)t->shadow_stack),
              "Shadow call stack does not point to kernel memory");
    EXPECT_EQ(t->shadow_stack_size,
              round_up(t->shadow_stack_size, sizeof(vaddr_t)),
              "Shadow call stack size was not rounded to the pointer size");
    EXPECT_NE(t->stack, t->shadow_stack,
              "Shadow call stack aliases the regular stack");
    EXPECT_EQ(ss_sz, t->shadow_stack_size,
              "Shadow call stack did not have the expected size");

    /* check the shadow stack size by inverting the last element */
    void* last_elem = t->shadow_stack + t->shadow_stack_size - sizeof(vaddr_t);
    EXPECT_EQ(NO_ERROR,
              mmutest_arch_store_uint32((uint32_t*)last_elem, false),
              "Actual size of shadow call stack differs from recorded size");
    /* restore last_elem by calling mmutest_arch_store_uint32 a second time */
    EXPECT_EQ(NO_ERROR,
              mmutest_arch_store_uint32((uint32_t*)last_elem, false),
              "Restoring last element of shadow stack failed");

    const size_t extra_space = round_up(t->shadow_stack_size, PAGE_SIZE) -
                               t->shadow_stack_size;
    void *const guard_region = t->shadow_stack - extra_space;
    EXPECT_EQ(0, (vaddr_t)guard_region & (PAGE_SIZE - 1),
              "Shadow call stack guard region is not page aligned");

    /* check for guard page before the shadow stack */
    void* before_guard_end = guard_region - sizeof(uint32_t);
    ASSERT_EQ(ERR_GENERIC,
              mmutest_arch_store_uint32((uint32_t*)before_guard_end, false),
              "Expected guard page after shadow call stack");

    /* check for guard page after the shadow stack */
    void *after_guard_begin = t->shadow_stack + t->shadow_stack_size;
    ASSERT_EQ(ERR_GENERIC,
              mmutest_arch_store_uint32((uint32_t*)after_guard_begin, false),
              "Expected guard page after shadow call stack");

    /*
     * this test will not run on idle threads which are the only
     * threads that do not have the free shadow stack flag set.
     */
    ASSERT_EQ(0, t->flags & THREAD_FLAG_IDLE, "Thread is an idle thread");
    EXPECT_NE(0, t->flags & THREAD_FLAG_FREE_SHADOW_STACK,
              "Shadow call stack did not have the free flag set");

    /*
     * Shadow stacks grow up. Test that the shadow stack is set up such that
     * we'll hit the guard page once we use the number of bytes corresponding
     * to the shadow stack size even if more bytes were allocated. We do so
     * by checking that all bytes are zero (i.e., unused) in the interval
     * [guard_region...t->shadow_stack).
     */
    vaddr_t *slot = (vaddr_t *)guard_region;
    while (slot <  (vaddr_t*)t->shadow_stack) {
        ASSERT_EQ(0, *slot++, "Expected unused shadow call stack slot");
    }

    /* shadow stack slots are either unused or point to kernel memory */
    while (slot < (vaddr_t*)after_guard_begin) {
        vaddr_t ret_addr = *slot++;
        if (!ret_addr)
            continue; /* slot is unused */
        ASSERT_EQ(true, is_kernel_address(ret_addr),
                  "Expected pointer to kernel memory");
    }

test_abort:;
}
#else
#define FEATURE_GATED_TEST_NAME(name) DISABLED_##name

static void inspect_thread(thread_t *t, size_t ss_sz) { }
#endif

TEST(scstest, FEATURE_GATED_TEST_NAME(current_kernel_thread_has_scs)) {
    thread_t *curr_thread = get_current_thread();
    inspect_thread(curr_thread, DEFAULT_SHADOW_STACK_SIZE);
}

static int new_thread_func(void* arg) {
    size_t expected_shadow_stack_size = *(size_t*)arg;
    thread_t *curr_thread = get_current_thread();
    inspect_thread(curr_thread, expected_shadow_stack_size);
    return 0;
}

TEST(scstest, FEATURE_GATED_TEST_NAME(new_kernel_thread_has_scs)) {
    int test_thread_ret;
    size_t shadow_stack_size = DEFAULT_SHADOW_STACK_SIZE;
    thread_t* test_thread =
            thread_create("scstest_thread", new_thread_func,
                          &shadow_stack_size, DEFAULT_PRIORITY,
                          DEFAULT_STACK_SIZE);
    ASSERT_NE(NULL, test_thread, "Failed to create test thread");

    ASSERT_EQ(NO_ERROR, thread_resume(test_thread), "Failed to start thread");

    ASSERT_EQ(NO_ERROR,
              thread_join(test_thread, &test_thread_ret, INFINITE_TIME),
              "Failed to wait on test thread");
    /* test_thread is deallocated here, do inspection in new_thread_func */

test_abort:;
}

TEST(scstest, FEATURE_GATED_TEST_NAME(new_kernel_thread_has_custom_size)) {
    int test_thread_ret;
    size_t expected_shadow_stack_size = 128;
    thread_t* test_thread =
            thread_create_etc(NULL, "scstest_thread", new_thread_func,
                              &expected_shadow_stack_size, DEFAULT_PRIORITY,
                              NULL, DEFAULT_STACK_SIZE,
                              expected_shadow_stack_size);
    ASSERT_NE(NULL, test_thread, "Failed to create test thread");

    ASSERT_EQ(NO_ERROR, thread_resume(test_thread), "Failed to start thread");

    ASSERT_EQ(NO_ERROR,
              thread_join(test_thread, &test_thread_ret, INFINITE_TIME),
              "Failed to wait on test thread");

test_abort:;
}

PORT_TEST(scstest, "com.android.kernel.scstest");
