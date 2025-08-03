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

#include <kernel/thread.h>
#include <lk/init.h>
#include <stdbool.h>
#include <stdio.h>

#define FPTEST_THREAD_COUNT (2)

void fptest_arch_init(uint64_t base_value);
int fptest_arch_check_state(uint64_t base_value);

static int fptest(void* arg) {
    int i = (uintptr_t)arg;
    int corrupted_regs;

    fptest_arch_init(i);
    while (true) {
        thread_sleep((1 + i) * 10 * 1000);
        corrupted_regs = fptest_arch_check_state(i);
        if (corrupted_regs) {
            printf("%s(%d): bad register state detected, %d registers corrupted\n",
                   __func__, i, corrupted_regs);
        } else {
            printf("%s(%d): register state OK\n", __func__, i);
        }
    }
    return 0;
}

static void fptest_init(uint level) {
    int i;
    int cpu = 0; /* test only on cpu for now */
    char thread_name[32];
    thread_t* thread;

    for (i = 0; i < FPTEST_THREAD_COUNT; i++) {
        snprintf(thread_name, sizeof(thread_name), "fptest-%d-%d", cpu, i);
        thread = thread_create(thread_name, fptest, (void*)(uintptr_t)i,
                               DEFAULT_PRIORITY, DEFAULT_STACK_SIZE);
        thread->pinned_cpu = cpu;
        thread_resume(thread);
    }
}

LK_INIT_HOOK(fptest, fptest_init, LK_INIT_LEVEL_APPS);
