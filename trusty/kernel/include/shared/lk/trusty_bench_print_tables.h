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
#include <trusty_log.h>

/* Max Width ever needed for a cell in the table in horizontal printing*/
static size_t trusty_bench_max_column_width;

/* Max Width ever needed for a metric cell in the table */
static size_t trusty_bench_max_metric_name_width =
        6 /* strlen("Metric") is the minimum needed*/;

/* Max Width ever needed for a Param cell in the table */
static size_t trusty_bench_max_param_name_width =
        5 /* strlen("Param") is the minimum needed*/;

/* Max Width ever needed for a Metric Value cell in the table */
static size_t trusty_bench_max_metric_digit_width;

static inline void reset_vertical_print_widths(void) {
    /* strlen("Metric") is the minimum needed*/
    trusty_bench_max_metric_name_width = 6;
    /* strlen("Param") is the minimum needed*/
    trusty_bench_max_param_name_width = 5;
    /* Max of strlen of Min/Max/Avg/Cold is the minimum needed*/
    trusty_bench_max_metric_digit_width = 4;
}

/**
 * trusty_bench_print_border - Prints a Dash Sequence of prescribed size sz.
 * @sz:     Number of Dashes to be printed.
 */
static inline void trusty_bench_print_border(size_t sz) {
    for (size_t i = 0; i < sz; ++i) {
        trusty_unittest_printf("-");
    }
    trusty_unittest_printf("\n");
}

/**
 * trusty_bench_center_print - Prints sz char in total, centering val inside it.
 * If unbalanced, left is one space character smaller
 * @sz:     Size of the column.
 * @val:    String to print.
 */
static inline void trusty_bench_center_print(size_t sz, const char* val) {
    int spaces = (int)(sz - strlen(val));
    int left = spaces / 2;
    int right = spaces - left;

    for (int i = 0; i < left; ++i) {
        trusty_unittest_printf(" ");
    }
    trusty_unittest_printf("%s", val);
    for (int i = 0; i < right; ++i) {
        trusty_unittest_printf(" ");
    }
}

/**
 * trusty_bench_left_print - Prints sz char in total, val on the left of it.
 * @sz:     Size of the column.
 * @val:    String to print.
 */
static inline void trusty_bench_left_print(size_t sz, const char* val) {
    trusty_unittest_printf("%s%*s", val, (int)(sz - strlen(val)), "");
}

/**
 * trusty_bench_print_title - Prints Benchmark Title in dashed box.
 * @suite:      Suite Name
 * @bench:      Bench Name
 * @param:      Param Name
 */
static inline void trusty_bench_print_title(const char* suite,
                                            const char* bench,
                                            const char* param) {
    char buffer[64];

    int nb_chars =
            snprintf(buffer, sizeof(buffer), "RUNNING %s_%s", suite, bench);
    if (*param != '\0' && nb_chars < (int)sizeof(buffer)) {
        snprintf(buffer + nb_chars, sizeof(buffer) - nb_chars, "_%s", param);
    }
    trusty_bench_print_border(BENCH_TITLE_WIDTH);
    trusty_bench_center_print(BENCH_TITLE_WIDTH - 1, buffer);
    trusty_unittest_printf("|\n");
    trusty_bench_print_border(BENCH_TITLE_WIDTH);
}

/**
 * trusty_bench_print_col_header - Prints column header with fixed size padding.
 * @sz:         Column Width
 * @name:       Title of the column
 * @center:     Center text in cell?
 */
static inline void trusty_bench_print_col_header(size_t sz,
                                                 const char* name,
                                                 bool center) {
    if (center) {
        trusty_bench_center_print(sz, name);
    } else {
        trusty_bench_left_print(sz, name);
    }
    trusty_unittest_printf("|");
}

/**
 * trusty_bench_print_header - Prints all column headers for a metric summary
 * table.
 * @metric_list:    List of metrics aggregated during all BENCH runs
 */
static inline void trusty_bench_print_header(struct list_node* metric_list) {
    struct bench_metric_list_node* entry;

    trusty_unittest_printf("|");
    trusty_bench_print_col_header(BENCH_LEFTMOST_COL_SIZE, " Metrics ", true);
    const char* prev_metric = NULL;
    size_t sz = 0;

    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (prev_metric == NULL) {
            prev_metric = entry->name;
        }

        if (strcmp(prev_metric, entry->name) != 0) {
            trusty_bench_print_col_header(sz - 1, prev_metric, true);

            sz = 0;
            prev_metric = entry->name;
        }

        sz += entry->col_sz + 1;
    }

    trusty_bench_print_col_header(sz - 1, prev_metric, true);
    trusty_unittest_printf("\n");
}

/**
 * trusty_bench_compute_widths -    Compute Columns Width and Total Width before
 *                                  printing anything.
 * @metric_list:    List of metrics aggregated during all BENCH runs.
 * @nb_params:      number of parameters in the parameter array.
 */
static inline void trusty_bench_compute_widths(struct list_node* metric_list,
                                               size_t nb_params) {
    struct bench_metric_list_node* entry;

    trusty_bench_table_total_width = 0;
    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        char buf[BENCH_MAX_COL_SIZE];

        /* Get the size of the header */
        /* First must be bigger than the size of the param header if any */
        size_t column_width = 0;

        if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
            if (entry->param_name_cb) {
                entry->param_name_cb(buf, sizeof(buf), entry->param_idx);
            } else if (trusty_bench_get_param_name_cb) {
                trusty_bench_get_param_name_cb(buf, sizeof(buf),
                                               entry->param_idx);
            } else {
                snprintf(buf, sizeof(buf), "%zu", entry->param_idx);
            }
            size_t param_name_width = strnlen(buf, sizeof(buf));

            trusty_bench_max_param_name_width =
                    MAX(trusty_bench_max_param_name_width, param_name_width);
            trusty_bench_max_column_width =
                    MAX(trusty_bench_max_column_width, param_name_width);
            column_width = MAX(column_width, param_name_width);
        }

        /* Then must be bigger than the size of the metric header */
        snprintf(buf, sizeof(buf), "%s", entry->name);
        size_t metric_name_width = strnlen(buf, sizeof(buf));

        trusty_bench_max_column_width =
                MAX(trusty_bench_max_column_width, metric_name_width);
        trusty_bench_max_metric_name_width =
                MAX(trusty_bench_max_metric_name_width, metric_name_width);
        column_width = MAX(column_width, metric_name_width);

        /* Get the size of the max value */
        trusty_bench_sprint_col_stat(
                buf, sizeof(buf), entry->metric.aggregates[BENCH_AGGREGATE_MAX],
                entry->name);
        trusty_bench_max_column_width =
                MAX(strnlen(buf, sizeof(buf)), trusty_bench_max_column_width);
        trusty_bench_max_metric_digit_width = MAX(
                trusty_bench_max_metric_digit_width, strnlen(buf, sizeof(buf)));

        /* Get the size of the min value, because aggregates are signed */
        trusty_bench_sprint_col_stat(
                buf, sizeof(buf), entry->metric.aggregates[BENCH_AGGREGATE_MIN],
                entry->name);
        trusty_bench_max_column_width =
                MAX(strnlen(buf, sizeof(buf)), trusty_bench_max_column_width);
        trusty_bench_max_metric_digit_width = MAX(
                trusty_bench_max_metric_digit_width, strnlen(buf, sizeof(buf)));

        /* Check Column is not too big */
        if (trusty_bench_max_column_width > BENCH_MAX_COL_SIZE) {
            TLOGE("Column size cannot exceed BENCH_MAX_COL_SIZE: %d",
                  BENCH_MAX_COL_SIZE);
            return;
        }

        /* Set the size of the column */
        entry->col_sz = column_width;
        trusty_bench_table_total_width += column_width + 1;
    }
    trusty_bench_table_total_width += BENCH_LEFTMOST_COL_SIZE + 2;
}

/**
 * trusty_bench_print_params -   Print all parameter column headers
 * @metric_list:    List of metrics aggregated during all BENCH runs
 */
static inline void trusty_bench_print_params(struct list_node* metric_list) {
    struct bench_metric_list_node* entry;

    trusty_unittest_printf("|");
    trusty_bench_print_col_header(BENCH_LEFTMOST_COL_SIZE, " Params ", true);
    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        char buf[BENCH_MAX_COL_SIZE];

        if (entry->param_name_cb) {
            entry->param_name_cb(buf, sizeof(buf), entry->param_idx);
        } else if (trusty_bench_get_param_name_cb) {
            trusty_bench_get_param_name_cb(buf, sizeof(buf), entry->param_idx);
        } else {
            snprintf(buf, sizeof(buf), "%zu", entry->param_idx);
        }
        trusty_bench_print_col_header(entry->col_sz, buf, true);
    }
    trusty_unittest_printf("\n");
}

/**
 * trusty_bench_print_col_stat -     print the value of one statistical
 * aggregate in a formatted column
 * @sz:                 Columns Width
 * @val:                Value to print
 * @metric_name:        Metric for which the aggregate stat is to be printed.
 */
static inline void trusty_bench_print_col_stat(
        size_t sz,
        int64_t val,
        const char* metric_name,
        trusty_bench_get_formatted_value_callback_t value_format_cb) {
    if (value_format_cb == NULL) {
        value_format_cb = trusty_bench_get_formatted_value_cb;
    }
    if (value_format_cb == NULL) {
        trusty_unittest_printf("%*" PRId64 "|", (int)sz, val);
    } else {
        char buffer[32];

        value_format_cb(buffer, sizeof(buffer), val, metric_name);
        if (trusty_bench_validate_numeric(buffer)) {
            trusty_unittest_printf("%*s|", (int)sz, buffer);
        } else {
            trusty_unittest_printf("%*s|", (int)sz, "");
        }
    }
}

/**
 * trusty_bench_print_stat -     print one list with the value of one
 * statistical aggregate across all params/metric
 * @metric_list:    List of metrics aggregated during all BENCH runs
 * @idx:            Position of the aggregate in the aggregate array
 * @aggregate_name: Name of the aggregate for the row header on the left
 */
static inline void trusty_bench_print_stat(struct list_node* metric_list,
                                           enum bench_aggregate_idx idx,
                                           const char* aggregate_name) {
    struct bench_metric_list_node* entry;

    trusty_unittest_printf("|");
    trusty_bench_print_col_header(BENCH_LEFTMOST_COL_SIZE, aggregate_name,
                                  true);
    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (entry->metric.cnt == 0) {
            trusty_bench_center_print(trusty_bench_max_metric_digit_width, "-");
            trusty_unittest_printf("|");
        } else if (idx == BENCH_AGGREGATE_COLD) {
            trusty_bench_print_col_stat(entry->col_sz, entry->metric.cold,
                                        entry->name, entry->formatted_value_cb);
        } else {
            trusty_bench_print_col_stat(entry->col_sz,
                                        entry->metric.aggregates[idx],
                                        entry->name, entry->formatted_value_cb);
        }
    }
    trusty_unittest_printf("\n");
}

/**
 * trusty_bench_print_horizontal_metric_list -  Prints a summary table of all
 * statistical aggregates for all param/metric in the last BENCH
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @nb_params:          Number of Parameters in the param array of BENCH.
 * @suite_name:         Name of the Bench Suite
 * @bench_name:         Name of the Bench
 */
static inline void trusty_bench_print_horizontal_metric_list(
        struct list_node* metric_list,
        size_t nb_params,
        const char* suite_name,
        const char* bench_name) {
    trusty_bench_compute_widths(metric_list, nb_params);
    trusty_bench_print_border(trusty_bench_table_total_width);
    trusty_bench_print_header(metric_list);
    trusty_bench_print_border(trusty_bench_table_total_width);
    if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
        trusty_bench_print_params(metric_list);
        trusty_bench_print_border(trusty_bench_table_total_width);
    }

    trusty_bench_print_stat(metric_list, BENCH_AGGREGATE_MIN, "min");
    trusty_bench_print_stat(metric_list, BENCH_AGGREGATE_AVG, "avg");
    trusty_bench_print_stat(metric_list, BENCH_AGGREGATE_MAX, "max");
    trusty_bench_print_stat(metric_list, BENCH_AGGREGATE_COLD, "cold");

    trusty_bench_print_border(trusty_bench_table_total_width);
}

/**
 * trusty_bench_print_vertical_metric_list -  Prints a summary table of all
 * statistical aggregates for all param/metric in the last BENCH
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @nb_params:          Number of Parameters in the param array of BENCH.
 * @suite_name:         Name of the Bench Suite
 * @bench_name:         Name of the Bench
 */
static inline void trusty_bench_print_vertical_metric_list(
        struct list_node* metric_list,
        size_t nb_params,
        const char* suite_name,
        const char* bench_name) {
    struct bench_metric_list_node* entry;

    trusty_bench_compute_widths(metric_list, nb_params);
    size_t width = trusty_bench_max_metric_name_width +
                   4 * trusty_bench_max_metric_digit_width + 6;

    /* Need one column for params? */
    if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
        width += trusty_bench_max_param_name_width + 1;
    }
    trusty_bench_print_border(width);
    trusty_unittest_printf("|");
    trusty_bench_print_col_header(trusty_bench_max_metric_name_width, "Metric",
                                  false);
    if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
        trusty_bench_print_col_header(trusty_bench_max_param_name_width,
                                      "Param", false);
    }
    trusty_bench_print_col_header(trusty_bench_max_metric_digit_width, "Min",
                                  false);
    trusty_bench_print_col_header(trusty_bench_max_metric_digit_width, "Avg",
                                  false);
    trusty_bench_print_col_header(trusty_bench_max_metric_digit_width, "Max",
                                  false);
    trusty_bench_print_col_header(trusty_bench_max_metric_digit_width, "Cold",
                                  false);
    trusty_unittest_printf("\n");

    const char* prev_metric = "";

    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (strcmp(prev_metric, entry->name) != 0) {
            prev_metric = entry->name;
            trusty_bench_print_border(width);
        }
        trusty_unittest_printf("|");
        trusty_bench_print_col_header(trusty_bench_max_metric_name_width,
                                      entry->name, false);
        if (nb_params > 1 || trusty_bench_nb_cpu > 1) {
            char buf[BENCH_MAX_COL_SIZE];

            if (entry->param_name_cb) {
                entry->param_name_cb(buf, sizeof(buf), entry->param_idx);
            } else if (trusty_bench_get_param_name_cb) {
                trusty_bench_get_param_name_cb(buf, sizeof(buf),
                                               entry->param_idx);
            } else {
                snprintf(buf, sizeof(buf), "%zu", entry->param_idx);
            }
            trusty_bench_print_col_header(trusty_bench_max_param_name_width,
                                          buf, false);
        }

        if (entry->metric.cnt) {
            trusty_bench_print_col_stat(
                    trusty_bench_max_metric_digit_width,
                    entry->metric.aggregates[BENCH_AGGREGATE_MIN], entry->name,
                    entry->formatted_value_cb);
            trusty_bench_print_col_stat(
                    trusty_bench_max_metric_digit_width,
                    entry->metric.aggregates[BENCH_AGGREGATE_AVG], entry->name,
                    entry->formatted_value_cb);
            trusty_bench_print_col_stat(
                    trusty_bench_max_metric_digit_width,
                    entry->metric.aggregates[BENCH_AGGREGATE_MAX], entry->name,
                    entry->formatted_value_cb);
            trusty_bench_print_col_stat(trusty_bench_max_metric_digit_width,
                                        entry->metric.cold, entry->name,
                                        entry->formatted_value_cb);
        } else {
            for (int i = 0; i < 4; i++) {
                trusty_bench_center_print(trusty_bench_max_metric_digit_width,
                                          "-");
                trusty_unittest_printf("|");
            }
        }
        trusty_unittest_printf("\n");
    }
    trusty_bench_print_border(width);
}
