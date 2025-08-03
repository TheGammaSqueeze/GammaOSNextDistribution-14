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

#include <lib/unittest/unittest.h>
#include <lk/list.h>

/* Build Time Option Switching */
#if (BENCHMARK_MACHINE_READABLE == 1)
#define BENCHMARK_PRINT_CB trusty_bench_print_json_metric_list
#else
#define BENCHMARK_PRINT_CB trusty_bench_print_vertical_metric_list
#endif

/**
 * typedef trusty_bench_get_param_name_callback - Type of the callback to
 * customize header names of parameter columns
 * @buf:            To be filled with the desired name.
 * @buf_size:       size of the buffer.
 * @param_idx:      Index of the parameter whose name is to be written into the
 *                  buffer.
 */
typedef void (*trusty_bench_get_param_name_callback_t)(char* buf,
                                                       size_t buf_size,
                                                       size_t param_idx);

/*
 * trusty_bench_get_param_name_cb - To be set during BENCH_SETUP with a
 * callback of trusty_bench_get_param_name_callback type. Will be used to print
 * param columns headers and then reset to NULL after BENCH_TEARDOWN
 */
static trusty_bench_get_param_name_callback_t trusty_bench_get_param_name_cb;

/**
 * typedef trusty_bench_get_formatted_value_callback - Type of the callback to
 * customize value printing
 * @buf:            To be filled with the desired name.
 * @buf_size:       size of the buffer.
 * @value:          value to be formatted.
 * @metric_name:    Name of the metric this value is associated to.
 */
typedef void (*trusty_bench_get_formatted_value_callback_t)(
        char* buf,
        size_t buf_size,
        int64_t value,
        const char* metric_name);

/*
 * trusty_bench_get_formatted_value_cb - To be set during BENCH_SETUP with a
 * callback of trusty_bench_get_formatted_value_callback type. Will be used to
 * print formatted aggregates values and then reset to NULL after BENCH_TEARDOWN
 */
static trusty_bench_get_formatted_value_callback_t
        trusty_bench_get_formatted_value_cb;

/**
 * typedef trusty_bench_print_callback -    Function pointer to Print a summary
 * table of all statistical aggregates for all param/metric in the last BENCH.
 * Use to switch between different printing formats.
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @nb_params:          Number of Parameters in the param array of BENCH.
 * @suite_name:         Name of the Bench Suite
 * @bench_name:         Name of Current Bench
 */
typedef void (*trusty_bench_print_callback_t)(struct list_node* metric_list,
                                              size_t nb_params,
                                              const char* suite_name,
                                              const char* bench_name);

/*
 * trusty_bench_print_cb - To be set with a callback
 * of trusty_bench_print_callback type. Defaults to vertical printing until
 * command line switch is added.
 */
static trusty_bench_print_callback_t trusty_bench_print_cb;
