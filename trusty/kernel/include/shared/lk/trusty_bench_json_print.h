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

#include <inttypes.h>

#include <lib/unittest/unittest.h>
#include "trusty_bench_option_cb.h"

/*
 * Up this by one if any change to the output is performed in a way that prevent
 * schema validation. Schema is available in the same folder as this file
 * "trusty_bench_json_schema.vXXX.json"
 */
#define BENCH_SCHEMA_VERSION "3"

/**
 * trusty_bench_print_json_metric_list -  Prints a machine readable json of all
 * statistical aggregates for all param/metric in the last BENCH
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @nb_params:          Number of Parameters in the param array of BENCH.
 * @suite_name:         Name of the Bench Suite
 * @bench_name:         Name of the Bench
 */
static inline void trusty_bench_print_json_metric_list(
        struct list_node* metric_list,
        size_t nb_params,
        const char* suite_name,
        const char* bench_name) {
    trusty_unittest_printf("{");
    trusty_unittest_printf("\"schema_version\": " BENCH_SCHEMA_VERSION ",\n");
    trusty_unittest_printf("\"suite_name\": \"%s\",\n", suite_name);
    trusty_unittest_printf("\"bench_name\": \"%s\",\n", bench_name);

    trusty_unittest_printf("\"results\": [");
    struct bench_metric_list_node* entry;
    char buf[BENCH_MAX_COL_SIZE];
    bool first_iter = true;

    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (!first_iter) {
            trusty_unittest_printf(",");
        }
        first_iter = false;
        trusty_unittest_printf("{");
        trusty_unittest_printf("\"metric_name\": \"%s\", ", entry->name);
        if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
            trusty_unittest_printf("\"param_id\": %zu, ", entry->param_idx);
            if (entry->param_name_cb) {
                entry->param_name_cb(buf, sizeof(buf), entry->param_idx);
                trusty_unittest_printf("\"param_name\": \"%s\", ", buf);
            } else if (trusty_bench_get_param_name_cb) {
                trusty_bench_get_param_name_cb(buf, sizeof(buf),
                                               entry->param_idx);
                trusty_unittest_printf("\"param_name\": \"%s\", ", buf);
            }
        }
        /* print formatted values */
        trusty_bench_sprint_col_stat(
                buf, sizeof(buf), entry->metric.aggregates[BENCH_AGGREGATE_MIN],
                entry->name);
        trusty_unittest_printf("\"min\": \"%s\",", buf);
        trusty_bench_sprint_col_stat(
                buf, sizeof(buf), entry->metric.aggregates[BENCH_AGGREGATE_MAX],
                entry->name);
        trusty_unittest_printf("\"max\": \"%s\",", buf);
        trusty_bench_sprint_col_stat(
                buf, sizeof(buf), entry->metric.aggregates[BENCH_AGGREGATE_AVG],
                entry->name);
        trusty_unittest_printf("\"avg\": \"%s\",", buf);
        trusty_bench_sprint_col_stat(buf, sizeof(buf), entry->metric.cold,
                                     entry->name);
        trusty_unittest_printf("\"cold\": \"%s\",", buf);

        /* Formatting is conditional to Metric Name, so we always print raw
         * values even when a formatter callback is present
         */
        trusty_unittest_printf("\"raw_min\": %" PRId64 ", ",
                               entry->metric.aggregates[BENCH_AGGREGATE_MIN]);
        trusty_unittest_printf("\"raw_max\": %" PRId64 ", ",
                               entry->metric.aggregates[BENCH_AGGREGATE_MAX]);
        trusty_unittest_printf("\"raw_avg\": %" PRId64 ", ",
                               entry->metric.aggregates[BENCH_AGGREGATE_AVG]);
        trusty_unittest_printf("\"raw_cold\": %" PRId64, entry->metric.cold);
        trusty_unittest_printf("}\n");
    }
    trusty_unittest_printf("]");
    trusty_unittest_printf("}\n");
}
