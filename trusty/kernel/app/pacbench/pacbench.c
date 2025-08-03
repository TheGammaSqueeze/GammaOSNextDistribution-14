/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define TLOG_TAG "pacbench"

#include <arch/ops.h>
#include <inttypes.h>
#include <stdint.h>
#include <trusty_benchmark.h>
#include <uapi/err.h>

/* Runs over which to collect statistics */
#define RUNS 100u

/* Benchmark run duration */
#define LOOPS 1000000u
#define INSTRUCTIONS_PER_LOOP 16u

/* Extended loop count for faster functions */
#define EXTRA_LOOPS 10000000u

#define PACKBENCH_STR_REP2(s) s s
#define PACKBENCH_STR_REP4(s) PACKBENCH_STR_REP2(s) PACKBENCH_STR_REP2(s)
#define PACKBENCH_STR_REP8(s) PACKBENCH_STR_REP4(s) PACKBENCH_STR_REP4(s)
#define PACKBENCH_STR_REP16(s) PACKBENCH_STR_REP8(s) PACKBENCH_STR_REP8(s)

BENCH_SETUP(pac) {
    return NO_ERROR;
}

BENCH_TEARDOWN(pac) {}

#ifdef KERNEL_PAC_ENABLED
/*
 * Test PACIA instruction.
 * If PAC is supported and enabled in the kernel, this key should be valid and
 * the instruction functional, though this benchmark does not test the
 * instruction - see pactest instead.
 */
BENCH_ALL_CPU(pac, pacia, RUNS) {
    uint64_t val = 0;

    for (uint64_t i = 0; i < LOOPS; i++) {
        __asm__ volatile(".arch_extension pauth\n\t" PACKBENCH_STR_REP16(
                                 "PACIA %0, %1\n\t")
                         : "+r"(val)
                         : "r"(i));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, pacia, ps_per_pacia) {
    return (bench_get_duration_ns() * 1000u) / (LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, pacia, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, pacia, instructions) {
    return LOOPS * INSTRUCTIONS_PER_LOOP;
}

/*
 * Test PACIA & AUTIA instruction.
 * If PAC is supported and enabled in the kernel, this key should be valid and
 * the instruction functional.
 * Note we cannot test AUTIA alone since it may generate an exception if it
 * fails.
 */
BENCH_ALL_CPU(pac, pacautia, RUNS) {
    uint64_t val = 0;

    for (uint64_t i = 0; i < LOOPS; i++) {
        __asm__ volatile(".arch_extension pauth\n\t" PACKBENCH_STR_REP16(
                                 "PACIA %0, %1\n\tAUTIA %0, %1\n\t")
                         : "+r"(val)
                         : "r"(i));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, pacautia, ps_per_pacautia) {
    return (bench_get_duration_ns() * 1000u) / (LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, pacautia, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, pacautia, instructions) {
    return LOOPS * INSTRUCTIONS_PER_LOOP;
}

/*
 * Test PACIB instruction.
 * If PAC is supported and enabled in the kernel, this key should be valid and
 * the instruction functional, though this benchmark does not test the
 * instruction - see pactest instead.
 */
BENCH_ALL_CPU(pac, pacib, RUNS) {
    uint64_t val = 0;

    for (uint64_t i = 0; i < LOOPS; i++) {
        __asm__ volatile(".arch_extension pauth\n\t" PACKBENCH_STR_REP16(
                                 "PACIB %0, %1\n\t")
                         : "+r"(val)
                         : "r"(i));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, pacib, ps_per_pacib) {
    return (bench_get_duration_ns() * 1000u) / (LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, pacib, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, pacib, instructions) {
    return LOOPS * INSTRUCTIONS_PER_LOOP;
}

/*
 * Test PACIAB & AUTIB instruction.
 * Even if PAC is supported by the hardware, Trusty doesn't use or enable this
 * key.
 */
BENCH_ALL_CPU(pac, pacautib, RUNS) {
    uint64_t val = 0;

    for (uint64_t i = 0; i < LOOPS; i++) {
        __asm__ volatile(".arch_extension pauth\n\t" PACKBENCH_STR_REP16(
                                 "PACIB %0, %1\n\tAUTIB %0, %1\n\t")
                         : "+r"(val)
                         : "r"(i));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, pacautib, ps_per_pacautib) {
    return (bench_get_duration_ns() * 1000u) / (LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, pacautib, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, pacautib, instructions) {
    return LOOPS * INSTRUCTIONS_PER_LOOP;
}
#endif

/*
 * Simple arithmetic instruction test.
 */
BENCH_ALL_CPU(pac, add, RUNS) {
    uint64_t val = 0;

    for (uint64_t i = 0; i < EXTRA_LOOPS; i++) {
        __asm__ volatile(PACKBENCH_STR_REP16(PACBENCH_ADD_INSTR)
                         : "+r"(val)
                         : "r"(i));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, add, ps_per_add) {
    return (bench_get_duration_ns() * 1000u) /
           (EXTRA_LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, add, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, add, instructions) {
    return EXTRA_LOOPS * INSTRUCTIONS_PER_LOOP;
}

/*
 * NOP instruction test.
 */
BENCH_ALL_CPU(pac, nop, RUNS) {
    for (uint64_t i = 0; i < EXTRA_LOOPS; i++) {
        __asm__ volatile(PACKBENCH_STR_REP16(PACBENCH_NOP_INSTR));
    }

    return NO_ERROR;
}

BENCH_RESULT(pac, nop, ps_per_nop) {
    return (bench_get_duration_ns() * 1000u) /
           (EXTRA_LOOPS * INSTRUCTIONS_PER_LOOP);
}

BENCH_RESULT(pac, nop, us_total) {
    return bench_get_duration_ns() / 1000u;
}

BENCH_RESULT(pac, nop, instructions) {
    return EXTRA_LOOPS * INSTRUCTIONS_PER_LOOP;
}

PORT_TEST(pac, "com.android.kernel.pacbench")
