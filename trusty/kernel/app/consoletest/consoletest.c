/*
 * Copyright (c) 2019, Google Inc. All rights reserved
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
#include <kernel/spinlock.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdio.h>
#include <string.h>
#if LK_LIBC_IMPLEMENTATION_IS_MUSL
#include <trusty/io_handle.h>
#endif

static size_t print_count;
static size_t print_bytes;
static size_t commit_count;

/*
 * NOTE: we're just looking for a general sense these functions are being
 * called. The kernel could be logging from other threads.
 * TODO: modularize logging so it can be tested in isolation.
 */

static void test_print_callback(print_callback_t* cb,
                                const char* str,
                                size_t len) {
    print_count += 1;
    print_bytes += len;
}

static void test_commit_callback(print_callback_t* cb) {
    commit_count += 1;
}

static void clear_stats(void) {
    print_count = 0;
    print_bytes = 0;
    commit_count = 0;
}

/* Did we print something like "Hello, test!" ? */
static void check_standard_stats(void) {
    EXPECT_LE(1, print_count);
    EXPECT_LE(13, print_bytes);
    EXPECT_LE(1, commit_count);
}

/*
 * Most of these tests are smoke tests - making sure there are no trivial
 * deadlocks or crashes.
 */

TEST(consoletest, puts) {
    clear_stats();

    /* puts will have a slightly different code path than printf. */
    puts("Hello, test!\n");

    check_standard_stats();
}

TEST(consoletest, threading) {
    clear_stats();

    printf("Hello, %s!\n", "test");

    check_standard_stats();
}

TEST(consoletest, irq_disabled) {
    spin_lock_saved_state_t state;
    clear_stats();

    arch_interrupt_save(&state, SPIN_LOCK_FLAG_INTERRUPTS);
    printf("Hello, %s!\n", "test");
    arch_interrupt_restore(state, SPIN_LOCK_FLAG_INTERRUPTS);

    check_standard_stats();
}

TEST(consoletest, steal_lock) {
    spin_lock_saved_state_t state;
    clear_stats();

    io_lock(file_io_handle(stdout));
    arch_interrupt_save(&state, SPIN_LOCK_FLAG_INTERRUPTS);
    printf("Hello, %s!\n", "test");
    arch_interrupt_restore(state, SPIN_LOCK_FLAG_INTERRUPTS);
    io_unlock(file_io_handle(stdout));

    check_standard_stats();
}

static bool run_console_test(struct unittest* test) {
    bool tests_passed;
    print_callback_t cb;
    /* Set a stub callback so all paths get exercised. */
    memset(&cb, 0, sizeof(cb));
    cb.print = test_print_callback;
    cb.commit = test_commit_callback;
    register_print_callback(&cb);
    tests_passed = RUN_ALL_TESTS();
    unregister_print_callback(&cb);
    return tests_passed;
}

static void console_test_init(uint level) {
    static struct unittest console_unittest = {
            .port_name = "com.android.kernel.console-unittest",
            .run_test = run_console_test,
    };

    unittest_add(&console_unittest);
}

LK_INIT_HOOK(console_test, console_test_init, LK_INIT_LEVEL_APPS);
