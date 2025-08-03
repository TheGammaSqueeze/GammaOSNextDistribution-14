/*
 * Copyright (c) 2022 Google Inc. All rights reserved
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

#define LOCAL_TRACE 0

#include <arch/arch_ops.h>
#include <assert.h>
#include <kernel/thread.h>
#include <lib/smc/smc.h>
#include <lk/init.h>
#include <lk/macros.h>
#include <lk/trace.h>
#include <platform.h>
#include <platform/random.h>
#include <string.h>

#include "arm_trng.h"

#define US2NS(us) ((us) * (1000ULL))
#define MS2NS(ms) (US2NS(ms) * 1000ULL)

#if ARCH_ARM64
#define ARM_TRNG_RND_SMC SMC_FC64_TRNG_RND
#else
#define ARM_TRNG_RND_SMC SMC_FC_TRNG_RND
#endif

static atomic_bool checked_for_support;

static void arm_trng_check_for_support(void) {
    if (atomic_load_explicit(&checked_for_support, memory_order_acquire)) {
        return;
    }

    struct smc_ret8 smc_ret;
    smc_ret = smc8(SMC_FC_TRNG_VERSION, 0, 0, 0, 0, 0, 0, 0);
    if ((long)smc_ret.r0 < 0) {
        panic("ARM TRNG version SMC error: %lx\n", smc_ret.r0);
    }

    long trng_major_version = (long)smc_ret.r0 >> 16;
    if (trng_major_version != SMC_TRNG_CURRENT_MAJOR_VERSION) {
        panic("ARM TRNG unexpected version, got %ld expected %ld\n",
              trng_major_version, (long)SMC_TRNG_CURRENT_MAJOR_VERSION);
    }

    smc_ret = smc8(SMC_FC_TRNG_FEATURES, ARM_TRNG_RND_SMC, 0, 0, 0, 0, 0, 0);
    if ((long)smc_ret.r0 < 0) {
        panic("ARM TRNG features SMC error: %lx\n", smc_ret.r0);
    }

    atomic_store_explicit(&checked_for_support, true, memory_order_release);
}

static void arm_trng_copy_register(uint8_t** buf_ptr,
                                   ulong* reg,
                                   size_t* len_ptr) {
    size_t count = MIN(*len_ptr, sizeof(*reg));
    if (!count) {
        return;
    }

    memcpy(*buf_ptr, reg, count);
    *buf_ptr += count;
    *len_ptr -= count;
}

void platform_random_get_bytes(uint8_t* buf, size_t len) {
    arm_trng_check_for_support();

    struct smc_ret8 smc_ret;
    size_t initial_len = len;
    lk_time_ns_t initial_start_time = current_time_ns();
    while (len > 0) {
        size_t count = MIN(len, 3 * sizeof(ulong));
        bool printed_long_wait = false;
        lk_time_ns_t wait_start_time = current_time_ns();
        for (;;) {
            LTRACEF("asking for %zu bytes\n", count);
            smc_ret = smc8(ARM_TRNG_RND_SMC, count * 8, 0, 0, 0, 0, 0, 0);
            if ((long)smc_ret.r0 != TRNG_ERROR_NO_ENTROPY) {
                break;
            }

            if (!arch_ints_disabled()) {
                /*
                 * Sleep when called from thread context. The kernel enables
                 * interrupts about when it starts the scheduler, so checking
                 * if interrupts are enabled is a good approximation for whether
                 * it's safe to sleep.
                 */
                thread_sleep_ns(ARM_TRNG_ENTROPY_SLEEP_NS);
            }

            if (!printed_long_wait) {
                lk_time_ns_t time_waited = current_time_ns() - wait_start_time;
                if (time_waited >= MS2NS(ARM_TRNG_LONG_WAIT_MS)) {
                    dprintf(ALWAYS,
                            "ARM TRNG waited for a long time: %llu ns\n",
                            time_waited);
                    printed_long_wait = true;
                }
            }
        }
        if ((long)smc_ret.r0 < 0) {
            panic("ARM TRNG RND SMC error: %lx\n", smc_ret.r0);
        }

        arm_trng_copy_register(&buf, &smc_ret.r3, &len);
        arm_trng_copy_register(&buf, &smc_ret.r2, &len);
        arm_trng_copy_register(&buf, &smc_ret.r1, &len);
    }

    lk_time_ns_t total_time = current_time_ns() - initial_start_time;
    if (total_time >= MS2NS(ARM_TRNG_PRINT_MS)) {
        dprintf(INFO, "ARM TRNG total time for %zu bytes: %llu ns\n",
                initial_len, total_time);
    }
}
