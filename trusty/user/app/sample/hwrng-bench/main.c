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

#define TLOG_TAG "hwcrypto_bench"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <interface/hwkey/hwkey.h>
#include <lib/hwkey/hwkey.h>
#include <lib/rng/trusty_rng.h>
#include <trusty/time.h>
#include <trusty_benchmark.h>
#include <uapi/err.h>

#define BUF_SIZE 4096

typedef struct {
    uint8_t rng_buf[BUF_SIZE];
} crypto_state_t;

static crypto_state_t* crypto_state;

struct query_param {
    size_t sz;
    int (*rng_call)(uint8_t*, size_t);
};

/* Parameters Array for the parametric benchmark */
static const struct query_param fixed_total_size_chunked[] = {
        {2, trusty_rng_hw_rand},        {4, trusty_rng_hw_rand},
        {8, trusty_rng_hw_rand},        {16, trusty_rng_hw_rand},
        {32, trusty_rng_hw_rand},       {64, trusty_rng_hw_rand},
        {128, trusty_rng_hw_rand},      {256, trusty_rng_hw_rand},
        {512, trusty_rng_hw_rand},      {1024, trusty_rng_hw_rand},
        {2048, trusty_rng_hw_rand},     {4096, trusty_rng_hw_rand},
        {2, trusty_rng_secure_rand},    {4, trusty_rng_secure_rand},
        {8, trusty_rng_secure_rand},    {16, trusty_rng_secure_rand},
        {32, trusty_rng_secure_rand},   {64, trusty_rng_secure_rand},
        {128, trusty_rng_secure_rand},  {256, trusty_rng_secure_rand},
        {512, trusty_rng_secure_rand},  {1024, trusty_rng_secure_rand},
        {2048, trusty_rng_secure_rand}, {4096, trusty_rng_secure_rand},
};

static const struct query_param* variable_sizes = fixed_total_size_chunked;

/*
 * Construct the Column Header for each param. Can have any name, but must be
 * assigned to trusty_bench_get_param_name_cb global in BENCH_SETUP
 */
static void get_param_name_cb(char* buf, size_t buf_size, size_t param_idx) {
    snprintf(buf, buf_size, "%zu Bytes - %s",
             fixed_total_size_chunked[param_idx].sz,
             fixed_total_size_chunked[param_idx].rng_call == trusty_rng_hw_rand
                     ? "hw_rand"
                     : "secure_rand");
}

/*
 * Construct the Column Header for each param. Can have any name, but must be
 * assigned to trusty_bench_get_param_name_cb global in BENCH_SETUP
 */
static void get_param_name_cb_fixed(char* buf,
                                    size_t buf_size,
                                    size_t param_idx) {
    snprintf(buf, buf_size, "%d Total - %zu Bytes Chunks - %s", BUF_SIZE,
             fixed_total_size_chunked[param_idx].sz,
             fixed_total_size_chunked[param_idx].rng_call == trusty_rng_hw_rand
                     ? "hw_rand"
                     : "secure_rand");
}
/*
 * Construct the Formatted Aggregate Values. Can have any name, but must be
 * assigned to trusty_bench_get_formatted_value_cb global in BENCH_SETUP
 */
static void get_formatted_value_cb(char* buf,
                                   size_t buf_size,
                                   int64_t value,
                                   const char* metric_name) {
    if (strcmp("time_micro_seconds", metric_name) == 0 ||
        strcmp("micro_sec_per_byte", metric_name) == 0) {
        int64_t mic_sec = value / 1000;
        int64_t n_sec = value % 1000;
        snprintf(buf, buf_size, "%" PRId64 ".%03" PRId64 "", mic_sec, n_sec);
    } else {
        snprintf(buf, buf_size, "%" PRId64, value);
    }
}

/*
 * Executed before each atomic execution of a BENCH(crypto, ...) Macro.
 */
BENCH_SETUP(crypto) {
    /*
     * Let Framework know how to print param column header. Default is the
     * current param index. Will be reset to NULL after BENCH_TEARDOWN(crypto,
     * hwrng, ...)
     */
    trusty_bench_get_param_name_cb = &get_param_name_cb;

    /*
     * Let Framework know how to print formatted aggregate values. Default is
     * printing the int64_t value as such. Will be reset to NULL after
     * BENCH_TEARDOWN(crypto, hwrng, ...)
     */
    trusty_bench_get_formatted_value_cb = &get_formatted_value_cb;
    /*
     * Let Framework know how to print results. Defaults to vertical. This line
     * is here for demonstration purpose only. Feel free to uncomment.
     */

    /* trusty_bench_print_cb = &trusty_bench_print_horizontal_metric_list; */

    crypto_state = calloc(1, sizeof(crypto_state_t));
    if (crypto_state == NULL) {
        TLOGE("Failed to Allocate memory for crypto_state!");
        return ERR_NO_MEMORY;
    }

    return NO_ERROR;
}

/*
 * Executed after each atomic execution of a BENCH(crypto, ...) Macro.
 */
BENCH_TEARDOWN(crypto) {
    free(crypto_state);
    crypto_state = NULL;
}

/*
 * BENCH with 3 parameters (suite_name, test_name, nb_of_runs).
 * The inner content is run 100 times.
 * BENCH_SETUP/BENCH_TEARDOWN are executed before/after each individual run
 * respectively.
 */
BENCH(crypto, hwrng_hw_rand, 20) {
    int rc;
    rc = trusty_rng_hw_rand(crypto_state->rng_buf, BUF_SIZE);
    ASSERT_EQ(NO_ERROR, rc);

test_abort:
    return rc;
}

/* the returned time is in nanoseconds, the formatter will make it micro but the
 * name here is the one used in printing */
BENCH_RESULT(crypto, hwrng_hw_rand, time_micro_seconds) {
    /*
     * bench_get_duration_ns() is the ns time from trusty_get_time with clock ID
     * 0 for the last execution of 'BENCH'.
     */
    return bench_get_duration_ns();
}

BENCH_RESULT(crypto, hwrng_hw_rand, micro_sec_per_byte) {
    return bench_get_duration_ns() / BUF_SIZE;
}

/*
 * BENCH with 4 parameters (suite_name, test_name, nb_of_runs, params).
 * For each parameter in query_params, the inner content is run 5 times.
 * BENCH_SETUP/BENCH_TEARDOWN are executed before/after each individual run
 * respectively.
 */
BENCH(crypto, hwrng_fixed_total, 20, fixed_total_size_chunked) {
    int rc;
    size_t i;

    trusty_bench_get_param_name_cb = &get_param_name_cb_fixed;

    size_t nq_query =
            BUF_SIZE / fixed_total_size_chunked[bench_get_param_idx()].sz;
    for (i = 0; i < nq_query; i++) {
        rc = fixed_total_size_chunked[bench_get_param_idx()].rng_call(
                crypto_state->rng_buf,
                fixed_total_size_chunked[bench_get_param_idx()].sz);
        ASSERT_EQ(NO_ERROR, rc);
    }

test_abort:
    return rc;
}

/* the returned time is in nanoseconds, the formatter will make it micro but the
 * name here is the one used in printing */
BENCH_RESULT(crypto, hwrng_fixed_total, time_micro_seconds) {
    return bench_get_duration_ns();
}

BENCH_RESULT(crypto, hwrng_fixed_total, micro_sec_per_byte) {
    return bench_get_duration_ns() / BUF_SIZE;
}

/*
 * BENCH with 5 parameters (suite_name, test_name, nb_of_runs, params).
 * For each parameter in query_params, the inner content is run 100 times.
 * BENCH_SETUP/BENCH_TEARDOWN are executed before/after each individual run
 * respectively. For convenience, one can reuse (suite_name, test_name) from a 3
 * or 4 param BENCH macro. This allows sharing
 * BENCH_SETUP/BENCH_TEARDOWN/BENCH_SETUP/BENCH_RESULT macros. you need a
 * different parameter name, hence the aliasing here.
 */
BENCH(crypto,
      hwrng_var_size,
      20,
      variable_sizes,
      countof(fixed_total_size_chunked)) {
    int rc;

    rc = variable_sizes[bench_get_param_idx()].rng_call(
            crypto_state->rng_buf, variable_sizes[bench_get_param_idx()].sz);
    ASSERT_EQ(NO_ERROR, rc);

test_abort:
    return rc;
}

/* the returned time is in nanoseconds, the formatter will make it micro but the
 * name here is the one used in printing */
BENCH_RESULT(crypto, hwrng_var_size, time_micro_seconds) {
    return bench_get_duration_ns();
}
BENCH_RESULT(crypto, hwrng_var_size, micro_sec_per_byte) {
    return bench_get_duration_ns() / variable_sizes[bench_get_param_idx()].sz;
}

PORT_TEST(hwcrypto, "com.android.trusty.hwrng.bench")
