/*
 * Copyright (c) 2018, Google Inc. All rights reserved
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

#include <kernel/mp.h>
#include <kernel/thread.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdbool.h>
#include <stdio.h>

#define THREAD_DELAY_MS 1

#define SMPTEST_CYCLES 16

static struct smptest_thread {
    thread_t* thread;

    volatile bool started;
    volatile uint unblock_count;
    volatile uint error_count;
    volatile uint done_count;

} smptest_thread[SMP_MAX_CPUS];

/* Check if a thread is blocked, using volatile to ensure re-read */
static bool thread_is_blocked(volatile thread_t* thread) {
    return thread->state == THREAD_BLOCKED;
}

static int smptest_thread_func(void* arg) {
    const uint i = (uintptr_t)arg;
    const uint expected_cpu = i;
    struct smptest_thread* const smpt = &smptest_thread[i];

    /* Note thread as started so main thread sees which CPUs are available */
    smpt->started = true;

    uint cpu = arch_curr_cpu_num();
    if (cpu != expected_cpu) {
        /* Warn if the thread starts on another CPU than it was pinned to */
        printf("%s: thread %d started on wrong cpu: %d\n", __func__, i, cpu);
        smpt->error_count++;
    }

    while (true) {
        THREAD_LOCK(state1);
        get_current_thread()->state = THREAD_BLOCKED;
        thread_block();

        cpu = arch_curr_cpu_num();
        if (cpu != expected_cpu) {
            /* Don't update any state if the thread runs on the wrong CPU. */
            printf("%s: thread %d ran on wrong cpu: %d\n", __func__, i, cpu);
            smpt->error_count++;
            continue;
        }

        /*
         * Update unblock count for this cpu so the main test thread can see
         * that it ran.
         */
        smpt->unblock_count++;
        THREAD_UNLOCK(state1);

        /* Sleep to allow other threads to block */
        thread_sleep(THREAD_DELAY_MS);

        THREAD_LOCK(state2);

        /* Find and unblock the next started cpu */
        for (uint next_cpu = i + 1; next_cpu < SMP_MAX_CPUS; next_cpu++) {
            if (smptest_thread[next_cpu].started) {
                thread_t* next = smptest_thread[next_cpu].thread;

                /* Next CPU should be blocked; wake it up */
                if (thread_is_blocked(next)) {
                    thread_unblock(next, false);
                } else {
                    printf("%s: thread %d not blocked\n", __func__, i + 1);
                    smpt->error_count++;
                }

                break;
            }
        }

        /*
         * Update unblock count for this cpu so the main test thread can see
         * that it completed.
         */
        smpt->done_count++;
        THREAD_UNLOCK(state2);
    }
    return 0;
}

TEST(smptest, run) {
    bool wait_for_cpus = false;

    for (uint i = 0; i < SMP_MAX_CPUS; i++) {
        if (!thread_is_blocked(smptest_thread[i].thread)) {
            unittest_printf("[   INFO   ] thread %d not ready\n", i);
            wait_for_cpus = true;
        }
    }

    /*
     * test-runner can start the test before all CPUs have finished booting.
     * Wait another second for all the CPUs we need to be ready if needed.
     */
    if (wait_for_cpus) {
        unittest_printf("[   INFO   ] waiting for threads to be ready\n");
        thread_sleep(1000);
    }

    for (uint i = 0; i < SMP_MAX_CPUS; i++) {
        ASSERT_EQ(!mp_is_cpu_active(i) ||
                          thread_is_blocked(smptest_thread[i].thread),
                  true, "thread %d not ready\n", i);
    }

    for (uint i = 0; i < SMP_MAX_CPUS; i++) {
        smptest_thread[i].unblock_count = 0;
        smptest_thread[i].error_count = 0;
        smptest_thread[i].done_count = 0;
    }

    /*
     * Repeat the test, in case the CPUs don't go back to the same state
     * after the first wake-up
     */
    for (uint j = 1; j < SMPTEST_CYCLES; j++) {
        THREAD_LOCK(state);
        /*
         * Wake up thread on CPU 0 to start a test run. Each thread 'n' should
         * wake-up thread 'n+1' until the last thread stops.
         * Check thread is blocked before unblocking to avoid asserts.
         */
        if (thread_is_blocked(smptest_thread[0].thread)) {
            thread_unblock(smptest_thread[0].thread, false);
        }

        THREAD_UNLOCK(state);

        /* Sleep to allow all CPUs to run with some margin */
        thread_sleep((THREAD_DELAY_MS + 5) * SMP_MAX_CPUS);

        /*
         * Check that every CPU-thread ran exactly once each time we woke up the
         * first thread.
         */
        for (uint cpu = 0; cpu < SMP_MAX_CPUS; cpu++) {
            const struct smptest_thread* const smpt = &smptest_thread[cpu];

            /*
             * Some cpus can still execute the thread body (e.g. if they are
             * interrupted by some other jobs), let them time to finish
             * (up to 1 sec, then think they got stuck).
             */
            for (int i = 0; i < 10; i++) {
                if (smpt->unblock_count != j || smpt->done_count != j) {
                    thread_sleep(100);
                }
            }

            const int unblock_count = smpt->unblock_count;
            const int error_count = smpt->error_count;
            const int done_count = smpt->done_count;

            if (smpt->started) {
                EXPECT_EQ(unblock_count, j, "cpu %d FAILED block count\n", cpu);
                EXPECT_EQ(error_count, 0, "cpu %d FAILED error count\n", cpu);
                EXPECT_EQ(done_count, j, "cpu %d FAILED done count\n", cpu);

                if (j == SMPTEST_CYCLES - 1) {
                    unittest_printf(
                            "[   INFO   ] smptest cpu %d ran %d times\n", cpu,
                            SMPTEST_CYCLES);
                }
            } else {
                EXPECT_EQ(mp_is_cpu_active(cpu), false,
                          "cpu %d active but not running", cpu);
                EXPECT_EQ(unblock_count, 0, "cpu %d FAILED block count\n", cpu);
                EXPECT_EQ(error_count, 0, "cpu %d FAILED error count\n", cpu);
                EXPECT_EQ(done_count, 0, "cpu %d FAILED done count\n", cpu);
            }
        }
    }

test_abort:;
}

static void smptest_setup(uint level) {
    /* Create a thread for each possible CPU */
    for (uint cpu = 0; cpu < SMP_MAX_CPUS; cpu++) {
        struct smptest_thread* smpt = &smptest_thread[cpu];
        char thread_name[32];

        snprintf(thread_name, sizeof(thread_name), "smptest-%u", cpu);
        smpt->thread = thread_create(thread_name, smptest_thread_func,
                                     (void*)(uintptr_t)cpu, HIGH_PRIORITY,
                                     DEFAULT_STACK_SIZE);
        thread_set_pinned_cpu(smpt->thread, cpu);
    }

    /* Allow threads to run */
    for (uint cpu = 0; cpu < SMP_MAX_CPUS; cpu++) {
        thread_resume(smptest_thread[cpu].thread);
    }
}

LK_INIT_HOOK(smptest_hook, smptest_setup, LK_INIT_LEVEL_APPS);

PORT_TEST(smptest, "com.android.kernel.smp-unittest");
