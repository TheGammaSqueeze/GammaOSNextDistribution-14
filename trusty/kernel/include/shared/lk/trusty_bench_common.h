/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once
#include <ctype.h>
#include <stdio.h>

#include <lib/unittest/unittest.h>

#include "trusty_bench_option_cb.h"

/**
 * enum bench_aggregate_idx - The position of the calculated aggregate in the
 * aggregate array of the bench_metric_node
 * @BENCH_AGGREGATE_MIN: index of the current minimum value for this metric.
 * @BENCH_AGGREGATE_MAX: index of the current maximum value for this metric.
 * @BENCH_AGGREGATE_AVG: index of the current average value for this metric.
 * @BENCH_AGGREGATE_COLD:index of the cold run value for this metric.
 * @BENCH_NUM_AGGREGATE: Number of available aggregates. Indicates the end of
 * the enum possible values.
 */
enum bench_aggregate_idx {
    BENCH_AGGREGATE_MIN = 0,
    BENCH_AGGREGATE_MAX = 1,
    BENCH_AGGREGATE_AVG = 2,
    BENCH_AGGREGATE_COLD = 3,
    BENCH_NUM_AGGREGATE = 4
};

/**
 * struct bench_metric_node - holds current aggregate for the metrics of the
 * current bench.
 * @cnt:                Number of BENCH runs already aggregated.
 * @tot:                Total of all values returned by BENCH_RESULT.
 * @cold:               Value of the metric for the initial cold run.
 * @aggregates:         Array of computed aggregates.
 *                      BENCH_AGGREGATE_MIN: Smallest value returned by
 * BENCH_RESULT. BENCH_AGGREGATE_MAX: Highest value returned by BENCH_RESULT.
 *                      BENCH_AGGREGATE_AVG: Average value returned by
 * BENCH_RESULT.
 */
struct bench_metric_node {
    size_t cnt;
    int64_t tot;
    int64_t cold;
    int64_t aggregates[BENCH_NUM_AGGREGATE];
};

/**
 * struct bench_metric_list_node - holds a metric declared by BENCH_RESULT in
 * a lk_list node
 * @node:               List node.
 * @metric:             Metric values container.
 * @name:               Name to use in summary table header for this metric.
 * @param_idx:          index of current param in param array this metric node
 *                      is aggregating for.
 * @col_sz:             size in bytes needed to print this column.
 * @bench_result:       Function pointer holding the BENCH_RESULT body
 *                      Used to get the value to be aggregate for this metric
 *                      after each BENCH body run.
 * @formatted_value_cb: A callback of
 *                      trusty_bench_get_formatted_value_callback_t type for
 *                      formatting the result value to a string
 * @param_name_cb:      A callback of trusty_bench_get_param_name_callback_t
 *                      type for formatting the param name
 */
struct bench_metric_list_node {
    struct list_node node;
    struct bench_metric_node metric;
    const char* name;
    size_t param_idx;
    size_t col_sz;
    int64_t (*bench_result)(void);
    trusty_bench_get_formatted_value_callback_t formatted_value_cb;
    trusty_bench_get_param_name_callback_t param_name_cb;
};

/*
 * Some Helper Functions for human readable output.
 */

/* Some hardcoded sizes */
#define BENCH_MAX_COL_SIZE 64
#define BENCH_LEFTMOST_COL_SIZE 16
#define BENCH_TITLE_WIDTH 72

/* Total Width of the resulting horizontal Table */
static size_t trusty_bench_table_total_width;

/**
 * trusty_bench_validate_numeric - Utility function to parse Google F1 SQL valid
 * values except NaN and +/-inf
 *
 * @s: string to parse
 *
 * Return: true if s is a valid double
 */
static inline bool trusty_bench_validate_numeric(const char* s) {
    bool found_dot = false;
    /* ignore initital spaces */
    while (*s != '\0' && (*s == ' ' || *s == '\t')) {
        ++s;
    }

    /* can optionally start with sign */
    if (*s == '+' || *s == '-') {
        ++s;
    }

    /* Then digits and one optional dot */
    while (*s != '\0' && *s != ' ' && *s != '\t') {
        switch (*s) {
        case '.':
            if (found_dot) {
                return false;
            }
            found_dot = true;
            break;
        case 'E':
        case 'e':
            found_dot = true;  // dot are not allowed anymore

            // Start Exponent. Ignore Sign.
            ++s;
            if (*s == '+' || *s == '-') {
                ++s;
            }
            // Make sure there is an exponent after the E+/-.
            // Let the loop do the increment
            if (!isdigit(*s)) {
                return false;
            }
            break;
        default:
            // Note: Leading 0 are accepted by SQL SAFE_CAST parsing functions
            if (!isdigit(*s)) {
                return false;
            }
            break;
        }
        ++s;
    }

    /* ignore trailing spaces */
    while (*s != '\0' && (*s == ' ' || *s == '\t')) {
        ++s;
    }
    if (*s == '\0') {
        return true;
    }

    return false;
}

/**
 * trusty_bench_sprint_col_stat -     print the value of one statistical
 * aggregate in a formatted column
 * @buffer:             Buffer in which to write the results. Preallocated.
 * @buffer_len:         Size of the Buffer in bytes.
 * @val:                Value to print
 * @metric_name:        Name of the metric for which this value is to be
 *                      formatted
 */
static inline void trusty_bench_sprint_col_stat(char* buffer,
                                                size_t buffer_len,
                                                int64_t val,
                                                const char* metric_name) {
    if (trusty_bench_get_formatted_value_cb == NULL) {
        snprintf(buffer, buffer_len, "%" PRId64, val);
    } else {
        trusty_bench_get_formatted_value_cb(buffer, buffer_len, val,
                                            metric_name);
        EXPECT_EQ(trusty_bench_validate_numeric(buffer), true,
                  "%s is not a valid double representation.\n", buffer);
    }
}

/* Number of CPU on which to bench */
static uint8_t trusty_bench_nb_cpu = 1;
static size_t trusty_cur_bench_nb_params = 1;
static size_t trusty_bench_multi_cpu_param_idx(size_t param_idx) {
    return param_idx % trusty_cur_bench_nb_params;
}
static size_t trusty_bench_cpu_idx(size_t param_idx) {
    return param_idx / trusty_cur_bench_nb_params;
}
