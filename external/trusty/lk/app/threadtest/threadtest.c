/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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
#include <list.h>
#include <kernel/thread.h>
#include <lib/unittest/unittest.h>
#include <lib/rand/rand.h>
#include <platform.h>
#include <shared/lk/macros.h>
#include <sys/types.h>

#define US2NS(us) ((us) * 1000LL)
#define MS2NS(ms) (US2NS(ms) * 1000LL)
#define S2NS(s) (MS2NS(s) * 1000LL)

/* run test in thread that will exit on panic rather than halting execution */
static int threadtest_run_in_thread(const char* thread_name,
                                    int (*func)(void* arg),
                                    void* arg) {
    int ret;
    int thread_ret;
    struct thread* thread;
    thread = thread_create(thread_name, func, arg, DEFAULT_PRIORITY,
                           DEFAULT_STACK_SIZE);
    if (!thread) {
        return ERR_NO_MEMORY;
    }

    thread_set_flag_exit_on_panic(thread, true);

    ret = thread_resume(thread);
    if (ret) {
        return ret;
    }

    ret = thread_join(thread, &thread_ret, INFINITE_TIME);
    if (ret) {
        return ret;
    }

    return thread_ret;
}

static void thread_test_corrupt_current_thread_cookie(void) {
    thread_t *curr = get_current_thread();
    curr->cookie = curr->cookie + 1;
}

static int thread_test_corrupt_cookie_before_yield_fn(void *unused) {
    thread_test_corrupt_current_thread_cookie();
    /* put thread at the end run queue before calling thread_resched */
    thread_yield();

    /* should not get here */
    return ERR_GENERIC;
}

/* TODO(b/300168583): fix test flakyness. */
TEST(threadtest, DISABLED_cookie_corruption_before_yield_must_panic) {
    int ret;
    ret = threadtest_run_in_thread("yielding_cookie_corrupter_thread",
                                   thread_test_corrupt_cookie_before_yield_fn,
                                   NULL);
    /*
     * The thread will corrupt its own cookie which will cause a panic.
     * Because the test thread is set to exit on panic, the thread_exit
     * function will set its return value to ERR_FAULT.
     */
    EXPECT_EQ(ret, ERR_FAULT);
}

static int thread_test_corrupt_cookie_before_preempt_fn(void *unused) {
    thread_test_corrupt_current_thread_cookie();
    /*
     * put thread at the head of run queue before calling thread_resched
     *
     * note: this relies on the thread having a positive remaining quantum
     * which *should* be satisfied given that the thread was just started.
     */
    thread_preempt();

    /* should not get here */
    return ERR_GENERIC;
}

/* TODO(b/300168583): fix test flakyness. */
TEST(threadtest, DISABLED_cookie_corruption_before_preempt_must_panic) {
    int ret;
    ret = threadtest_run_in_thread("preempted_cookie_corrupter_thread",
                                   thread_test_corrupt_cookie_before_preempt_fn,
                                   NULL);
    EXPECT_EQ(ret, ERR_FAULT);
}

static int thread_test_corrupt_cookie_before_exit_fn(void *unused) {
    thread_test_corrupt_current_thread_cookie();

    /* exit thread with corrupt cookie */
    return ERR_GENERIC;
}

TEST(threadtest, cookie_corruption_before_exit_must_panic) {
    int ret;
    ret = threadtest_run_in_thread("exiting_cookie_corrupter_thread",
                                   thread_test_corrupt_cookie_before_exit_fn,
                                   NULL);
    EXPECT_EQ(ret, ERR_FAULT);
}

static int thread_blocking_fn(void *arg) {
    wait_queue_t *queue = (wait_queue_t*)arg;

    /* block so parent can corrupt cookie; ignore return value */
    THREAD_LOCK(state);

    wait_queue_block(queue, INFINITE_TIME);

    /* should not get here - cookie corrupted by parent thread */
    THREAD_UNLOCK(state);

    return ERR_GENERIC;
}

/* sleep until a newly created thread is blocked on a wait queue */
static status_t thread_sleep_until_blocked(thread_t *sleeper) {
    int thread_state;
    lk_time_ns_t now_ns = current_time_ns();
    lk_time_ns_t timeout = now_ns + S2NS(10);

    do {
        thread_sleep_ns(MS2NS(100));

        THREAD_LOCK(state);
        thread_state = sleeper->state;
        THREAD_UNLOCK(state);
    } while ((now_ns = current_time_ns()) < timeout &&
             thread_state != THREAD_BLOCKED);

    return thread_state == THREAD_BLOCKED ? NO_ERROR : ERR_TIMED_OUT;
}

struct thread_queue_args {
    wait_queue_t *queue;
    thread_t *thread;
};

static int thread_corrupt_then_wake_fn(void *args) {
    struct thread_queue_args *test_args = (struct thread_queue_args*)args;
    wait_queue_t *queue = test_args->queue;
    thread_t* sleeper = test_args->thread;

    status_t res = thread_sleep_until_blocked(sleeper);

    if (res != NO_ERROR || queue->count != 1)
        return ERR_NOT_BLOCKED;

    sleeper->cookie += 1; /* corrupt its cookie */

    THREAD_LOCK(state);
    wait_queue_wake_one(queue, true, NO_ERROR);

    /* should not get here - above call should panic due to corrupt cookie */
    THREAD_UNLOCK(state);
test_abort:
    return ERR_GENERIC;
}

TEST(threadtest, cookie_corruption_detected_after_wakeup) {
    int ret;
    wait_queue_t queue;
    thread_t *sleeping_thread;
    uint64_t expected_cookie;

    wait_queue_init(&queue);
    sleeping_thread = thread_create("sleeping thread", &thread_blocking_fn,
                                    &queue, DEFAULT_PRIORITY,
                                    DEFAULT_STACK_SIZE);
    ASSERT_NE(sleeping_thread, NULL);
    expected_cookie = sleeping_thread->cookie;
    thread_set_flag_exit_on_panic(sleeping_thread, true);

    thread_resume(sleeping_thread);

    struct thread_queue_args test_args = {
        .queue = &queue,
        .thread = sleeping_thread,
    };

    ret = threadtest_run_in_thread("waking thread",
                                   thread_corrupt_then_wake_fn,
                                   &test_args);

    ASSERT_EQ(ret, ERR_FAULT);

    /* sleeping thread got taken out of wait queue but is still blocked */
    ASSERT_EQ(sleeping_thread->state, THREAD_BLOCKED);
    ASSERT_EQ(list_in_list(&sleeping_thread->queue_node), false);
    ASSERT_EQ(queue.count, 0);

test_abort:;
    THREAD_LOCK(state);
    if (sleeping_thread && sleeping_thread->cookie != expected_cookie) {
        /* wake one detected corrupted cookie, recover state before cleanup */
        sleeping_thread->cookie = expected_cookie;

        /* put the thread back on the wait queue and increment its count */
        list_add_head(&queue.list, &sleeping_thread->queue_node);
        queue.count++;
    }

    /* this will retry wake operation on sleeping thread with valid cookie */
    wait_queue_destroy(&queue, true);
    THREAD_UNLOCK(state);

    if (sleeping_thread) {
        /* release test thread - must happen after we release the thread lock */
        thread_join(sleeping_thread, NULL, INFINITE_TIME);
    }
}

static int thread_fake_then_wake_fn(void *args) {
    struct thread_queue_args *test_args = (struct thread_queue_args*)args;
    wait_queue_t *queue = test_args->queue;
    thread_t fake, *sleeper = test_args->thread;

    status_t res = thread_sleep_until_blocked(sleeper);

    if (res != NO_ERROR || queue->count != 1)
        return ERR_NOT_BLOCKED;

    /*
     * Create a fake thread without updating its cookie. Since thread cookies
     * are address dependent, the cookie checks should detect the fake thread.
     */
    memcpy(&fake, sleeper, sizeof(thread_t));

    /* add the fake thread to the head of the wait queue */
    list_add_head(&queue->list, &fake.queue_node);
    queue->count++;

    THREAD_LOCK(state);
    wait_queue_wake_one(queue, true, NO_ERROR);

    /* should not get here - above call should panic due to corrupt cookie */
    THREAD_UNLOCK(state);
test_abort:
    return ERR_GENERIC;
}

TEST(threadtest, fake_thread_struct_detected_after_wakeup) {
    int ret;
    wait_queue_t queue;
    thread_t *sleeping_thread;

    wait_queue_init(&queue);
    sleeping_thread = thread_create("sleeping thread", &thread_blocking_fn,
                                    &queue, DEFAULT_PRIORITY,
                                    DEFAULT_STACK_SIZE);
    ASSERT_NE(sleeping_thread, NULL);
    thread_set_flag_exit_on_panic(sleeping_thread, true);

    thread_resume(sleeping_thread);

    struct thread_queue_args test_args = {
        .queue = &queue,
        .thread = sleeping_thread,
    };

    ret = threadtest_run_in_thread("faking thread",
                                   thread_fake_then_wake_fn,
                                   &test_args);

    ASSERT_EQ(ret, ERR_FAULT);

    /* sleeping thread should still be blocked on wait queue */
    ASSERT_EQ(sleeping_thread->state, THREAD_BLOCKED);
    ASSERT_EQ(sleeping_thread->blocking_wait_queue, &queue);
    ASSERT_EQ(queue.count, 1);

test_abort:;
    THREAD_LOCK(state);
    /* this will unblock the sleeping thread before destroying the wait queue */
    wait_queue_destroy(&queue, true);
    THREAD_UNLOCK(state);

    if (sleeping_thread) {
        /* release test thread - must happen after we release the thread lock */
        thread_join(sleeping_thread, NULL, INFINITE_TIME);
    }
}

static int cookie_tester(void *_unused) {
    return 0;
}

TEST(threadtest, threads_have_valid_cookies) {
    thread_t *curr = get_current_thread();
    thread_t *new;

    new = thread_create("cookie tester", &cookie_tester, NULL, DEFAULT_PRIORITY,
                        DEFAULT_STACK_SIZE);

    /*
     * Threads must have the same cookie value modulo the effects of
     * xor'ing the cookie with the address of the associated thread.
     */
    EXPECT_EQ(new->cookie ^ (uint64_t)new, curr->cookie ^ (uint64_t)curr);

    /*
     * xor'ing the cookie with the address of the associated thread should
     * make thread cookies unique to each thread because addresses differ.
     */
    EXPECT_NE(new->cookie, curr->cookie);

    /* start and join the thread so it gets reclaimed */
    thread_resume(new);
    thread_join(new, NULL, INFINITE_TIME);
}

#if KERNEL_PAC_ENABLED
#include <arch/arm64/sregs.h>
#include <arch/ops.h>

/*
 * This tests only one key, as used by the current PAC implementation, and
 * assumes the structure contains no padding, so can be trivially copied and
 * compared.  If the structure is changed, this test will likely need to be
 * updated to at least check additional keys.
 */
STATIC_ASSERT(sizeof(struct packeys) == sizeof(uint64_t) * 2);

static int pac_tester(void *param) {
    struct packeys *keys = param;

    keys->apia[0] = ARM64_READ_SYSREG(APIAKeyLo_EL1);
    keys->apia[1] = ARM64_READ_SYSREG(APIAKeyHi_EL1);

    return 0;
}

TEST(threadtest, threads_have_valid_pac_keys) {
    struct packeys actual_keys[4] = { 0 };

    if (!arch_pac_address_supported()) {
        trusty_unittest_printf("[  SKIPPED ] PAuth is not supported\n");
        return;
    }

    /* Test multiple threads */
    for (uint8_t i = 0; i < countof(actual_keys); i++) {
        struct packeys expected_keys = { 0 };

        thread_t *new = thread_create("pac thread", &pac_tester, &actual_keys[i],
                                      DEFAULT_PRIORITY, DEFAULT_STACK_SIZE);

        /* Store the expected keys assigned to the thread */
        expected_keys = new->arch.packeys;

        /* Start and join the thread so it completes */
        thread_resume(new);
        thread_join(new, NULL, INFINITE_TIME);

        /* Check the keys are as expected */
        EXPECT_EQ(memcmp(&actual_keys[i], &expected_keys,
                         sizeof(struct packeys)),
                  0, "incorrect key assignment");

        /* Check threads don't share keys */
        for (uint8_t j = 0; j < i; j++) {
            EXPECT_NE(memcmp(&actual_keys[i], &actual_keys[j],
                             sizeof(struct packeys)),
                      0, "duplicate key assignment");
        }
    }
}
#else
TEST(threadtest, DISABLED_threads_have_valid_pac_keys) {}
#endif

PORT_TEST(threadtest, "com.android.kernel.threadtest");
