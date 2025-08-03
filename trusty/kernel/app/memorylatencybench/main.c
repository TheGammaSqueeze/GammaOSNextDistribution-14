/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define TLOG_TAG "memlatency"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <arch/defines.h>
#include <trusty_benchmark.h>
#include <uapi/err.h>

#define BLOCK_SIZE_BYTES (CACHE_LINE * 4)
#define STRUCT_NPAD (BLOCK_SIZE_BYTES) / sizeof(uintptr_t)
#define MAX_WORKING_SET_SZ 16777216

static const uint64_t working_set_sizes[] = {
        BLOCK_SIZE_BYTES,
        512,
        1024,
        2048,
        4096,
        8192,
        16384,
        32768,
        65536,
        131072,
        262144,
        524288,
        1048576,
        2097152,
        4194304,
        8388608,
        MAX_WORKING_SET_SZ,
};

typedef union memlatency_state_t {
    union memlatency_state_t* next;
    uintptr_t pad[STRUCT_NPAD];
} memlatency_state_t;

static memlatency_state_t* memlatency_state_start;

static size_t nb_blocks = MAX_WORKING_SET_SZ / sizeof(memlatency_state_t);

static void get_param_name_cb_fixed(char* buf,
                                    size_t buf_size,
                                    size_t param_idx) {
    snprintf(buf, buf_size,
             "%" PRIu64 " Bytes working size in blocks of %zu Bytes",
             working_set_sizes[param_idx], sizeof(memlatency_state_t));
}

static void get_formatted_value_cb(char* buf,
                                   size_t buf_size,
                                   int64_t value,
                                   const char* metric_name) {
    if (strcmp("time_micro_seconds", metric_name) == 0) {
        int64_t mic_sec = value / 1000;
        int64_t n_sec = value % 1000;

        snprintf(buf, buf_size, "%" PRId64 ".%03" PRId64 "", mic_sec, n_sec);
    } else {
        snprintf(buf, buf_size, "%" PRId64, value);
    }
}

BENCH_SETUP(memlatency) {
    trusty_bench_get_param_name_cb = &get_param_name_cb_fixed;
    trusty_bench_get_formatted_value_cb = &get_formatted_value_cb;
    memlatency_state_start =
            memalign(CACHE_LINE, nb_blocks * sizeof(memlatency_state_t));

    if (memlatency_state_start == NULL) {
        TLOGE("Failed to Allocate memory for memlatency_state!");
        return ERR_NO_MEMORY;
    }

    memset((uint8_t*)memlatency_state_start, 0,
           nb_blocks * sizeof(memlatency_state_t));

    for (size_t idx = 0; idx < nb_blocks - 1; ++idx) {
        memlatency_state_start[idx].next = &memlatency_state_start[idx + 1];
    }

    static_assert(sizeof(memlatency_state_t) == BLOCK_SIZE_BYTES);

    return NO_ERROR;
}

BENCH_TEARDOWN(memlatency) {
    free(memlatency_state_start);
    memlatency_state_start = NULL;
}

BENCH(memlatency, latency_read, 20, working_set_sizes) {
    uint64_t sz = working_set_sizes[bench_get_param_idx()];
    uint64_t nb_blocks = sz / BLOCK_SIZE_BYTES;
    uint64_t loops = 10 * (MAX_WORKING_SET_SZ / sz);

    ASSERT_GT(nb_blocks, 0);

    while (loops > 0) {
        --loops;
        volatile union memlatency_state_t* block = memlatency_state_start;

        for (size_t idx = 0; idx < nb_blocks; idx++) {
            /* To make sure we are not overwriting next block Address */
            static_assert(sizeof(uintptr_t) == __SIZEOF_POINTER__);
            block = block->next;
        }
    }

    return NO_ERROR;
test_abort:
    return ERR_GENERIC;
}

BENCH(memlatency, latency_write, 20, working_set_sizes) {
    uint64_t sz = working_set_sizes[bench_get_param_idx()];
    uint64_t nb_blocks = sz / BLOCK_SIZE_BYTES;
    uint64_t loops = 10 * (MAX_WORKING_SET_SZ / sz);

    ASSERT_GT(nb_blocks, 0);

    while (loops > 0) {
        --loops;
        union memlatency_state_t* block = memlatency_state_start;

        for (size_t idx = 0; idx < nb_blocks; idx++) {
            /* To make sure we are not overwriting next block Address */
            static_assert(sizeof(uintptr_t) == __SIZEOF_POINTER__);
            (block + idx)->pad[1] = idx + sz;
        }
    }

    return NO_ERROR;
test_abort:
    return ERR_GENERIC;
}

BENCH_RESULT(memlatency, latency_read, time_micro_seconds) {
    return bench_get_duration_ns();
}

BENCH_RESULT(memlatency, latency_write, time_micro_seconds) {
    return bench_get_duration_ns();
}

PORT_TEST(memlatency, "com.android.kernel.memorylatency.bench");
