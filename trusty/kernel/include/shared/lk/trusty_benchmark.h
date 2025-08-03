/*
 * Copyright (C) 2022 The Android Open Source Project
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

/*
 * bench functions can be defined with the macro
 * BENCH(suite_name,bench_name,n [, params])
 * {
 *     ... bench function body ...
 * }
 *
 *  - This body will be executed n times for each params, if 4 arguments are
 *    given.
 *  - This body will be executed n times, if 3 arguments are given.
 *
 * For a suite, one is expected to also define BENCH_SETUP, BENCH_TEARDOWN.
 * For a 2-tuple (suite_name, bench_name) one is expected to also define at
 * least one BENCH_RESULT.
 *
 * BENCH_SETUP(suite_name)
 * {
 *     ... bench setup body ...
 *     return int_error_code;
 * }
 *
 * BENCH_SETUP(suite_name):
 *  - Will return 0 or NO_ERROR when it succeed.
 *  - Will be run before every execution of the BENCH body
 *  - Will cancel execution of the next BENCH body if returns non-zero.
 *    Test will be considered failed.
 *  - Will cancel execution of the next BENCH body if any ASSERT_<op> fails.
 *    Test will be considered failed.
 *  - All ASSERT_<op> macros from trusty_unittest can be used
 *  - GTEST_SKIP() maybe be called to skip the benchmark run.
 *
 * BENCH_TEARDOWN(suite_name)
 * {
 *     ... bench teardown body ...
 * }
 *
 * BENCH_TEARDOWN(suite_name):
 *  - Is executed even if BENCH_SETUP failed
 *  - Does not return any value
 *  - All ASSERT_<op> macros from trusty_unittest can be used
 *
 * BENCH_RESULT(suite_name,bench_name,res_name)
 * {
 *     ... bench result body ...
 *     return int64_t_value_of_res_name_for_last_bench_body_run;
 * }
 *
 *
 * BENCH_RESULT(suite_name,bench_name,res_name):
 *  - At least one must be defined. Can define multiple times.
 *  - Must return an int64_t
 *  - Results will be aggregated for n runs of the BENCH( ) body.
 *    Aggregation is grouped by params to min/max/avg of the n runs
 *  - res_name will be used as column title for the metric summary
 *
 * Example:
 *      BENCH_RESULT(hwcrypto, hwrng, time_ns) {
 *          return bench_get_duration_ns();
 *      }
 *
 * - The execution sequence is roughly:
 *
 *       for each param if any:
 *          BENCH_SETUP(suite_name,bench_name)
 *           repeat n times:
 *               BENCH_CONTENT
 *               for each BENCH_RESULT(suite_name,bench_name,res_name)
 *                   update the accumulators for res_name [min,max,avg]
 *           BENCH_TEARDOWN(suite_name,bench_name)
 *       Print Result Table
 *
 * NOTE:
 * When using a parameter array:
 *  - params must be an array of any type T any_name_is_fine[NB_PARAMS] = {...};
 *    The number of params is deduced from the sizeof(params)/sizeof(params[0]).
 *    So please do not dynamically allocate T* params.
 *  - params array name is up to the test writer
 *
 * The default column name for a parameter in the summary table is its index in
 * the param array. To customize it, one can define a function with the
 * following signature:
 * static void trusty_bench_get_param_name_cb(char* buf, size_t buf_size,
 * size_t param_idx);
 *
 * then assign it during BENCH_SETUP to the trusty_bench_get_param_name_cb
 * global:
 *
 * BENCH_SETUP(suite_name) {
 *   trusty_bench_get_param_name_cb = &get_param_name_cb;
 *   …
 * }
 *
 * trusty_bench_get_param_name_cb will be reset to NULL after teardown.
 *
 * See "trusty/user/app/sample/hwrng-bench/main.c" for a working and thoroughly
 * commented example
 */

#pragma once
#include <errno.h>
#include <inttypes.h>
#include <stdarg.h>
#include <stdlib.h>

#include <lib/unittest/unittest.h>
#include <trusty_log.h>
#include "trusty_bench_common.h"
#include "trusty_bench_json_print.h"
#include "trusty_bench_option_cb.h"
#include "trusty_bench_print_tables.h"
#include "trusty_unittest.h"
#ifdef TRUSTY_USERSPACE
#ifdef WITH_PTHREAD
#include <lib/thread/pthread.h>
#endif
#elif WITH_SMP
#include <kernel/mp.h>
#endif
#include <uapi/err.h>

/*
 * A few helper macros for static dispatch
 */
#define NB_ARGS_HELPER(_1, _2, _3, _4, _5, _6, _7, _8, N, ...) N
#define NB_ARGS(...) NB_ARGS_HELPER(__VA_ARGS__, 8, 7, 6, 5, 4, 3, 2, 1, 0)

#define CAT(a, ...) PRIMITIVE_CAT(a, __VA_ARGS__)
#define PRIMITIVE_CAT(a, ...) a##__VA_ARGS__

#define EVAL(...) __VA_ARGS__

__BEGIN_CDECLS

/**
 * struct benchmark_internal_state - Store internals for current bench.
 * @last_bench_body_duration:   nanoseconds duration of the last execution of
 *                              the bench body.
 * @cur_param_idx:              index of current parameter in param_array.
 */
static struct benchmark_internal_state {
    int64_t last_bench_body_duration;
    size_t cur_param_idx;
} bench_state;

/**
 * bench_get_duration_ns - convenience function to use in BENCH_RESULT to get
 * the duration of last bench body execution.
 *
 * Return: The duration of the last completed BENCH body in nanoseconds.
 */
static inline int64_t bench_get_duration_ns(void) {
    return bench_state.last_bench_body_duration;
}

/**
 * bench_get_param_idx - convenience function to use to get the
 * index of the current parameter BENCH_XXX is running for.
 * Return: The index of the parameter BENCH_XXX is running for.
 */
static inline size_t bench_get_param_idx(void) {
    return bench_state.cur_param_idx % trusty_cur_bench_nb_params;
}

/**
 * bench_get_cpu_idx - convenience function to use to get the
 * index of the current cpu BENCH_XXX is running for.
 * Return: The index of the cpu BENCH_XXX is running for.
 */
static inline size_t bench_get_cpu_idx(void) {
    return bench_state.cur_param_idx / trusty_cur_bench_nb_params;
}

/*
 * Helper macros to run on tests on all CPUs
 */
#if defined(TRUSTY_USERSPACE) && defined(WITH_PTHREAD)
static int trusty_bench_multi_cpus_setup(void) {
    if (trusty_bench_nb_cpu > 1) {
        cpu_set_t cpu_set;

        CPU_ZERO(&cpu_set);
        CPU_SET(bench_state.cur_param_idx / trusty_cur_bench_nb_params,
                &cpu_set);

        return pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t),
                                      &cpu_set);
    }
    return NO_ERROR;
}

static int trusty_bench_multi_cpus_teardown(void) {
    if (trusty_bench_nb_cpu > 1) {
        cpu_set_t cpu_set;

        CPU_ZERO(&cpu_set);
        for (int i = 0; i < SMP_MAX_CPUS; i++) {
            CPU_SET(i, &cpu_set);
        }

        return pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t),
                                      &cpu_set);
    }
    return NO_ERROR;
}
#elif !defined(TRUSTY_USERSPACE) && WITH_SMP
static int trusty_bench_multi_cpus_setup(void) {
    if (trusty_bench_nb_cpu > 1) {
        const int cpu = bench_state.cur_param_idx / trusty_cur_bench_nb_params;

        if (cpu < SMP_MAX_CPUS && mp_is_cpu_active(cpu)) {
            thread_set_pinned_cpu(get_current_thread(), cpu);
        } else {
            return EINVAL;
        }
    }

    return NO_ERROR;
}

static int trusty_bench_multi_cpus_teardown(void) {
    if (trusty_bench_nb_cpu > 1) {
        thread_set_pinned_cpu(get_current_thread(), -1);
    }
    return NO_ERROR;
}
#else
static int trusty_bench_multi_cpus_setup(void) {
    return NO_ERROR;
}

static int trusty_bench_multi_cpus_teardown(void) {
    return NO_ERROR;
}
#endif

/**
 * trusty_bench_update_metric -  Update the appropriate metric with the value
 * returned by BENCH_RESULT
 * @m:              The metric whose aggregate needs to be updated.
 * @val:            The value returned by BENCH_RESULT.
 */
static inline void trusty_bench_update_metric(struct bench_metric_node* m,
                                              int64_t val) {
    m->cnt += 1;
    m->tot += val;
    m->aggregates[BENCH_AGGREGATE_AVG] = m->tot / m->cnt;
    m->aggregates[BENCH_AGGREGATE_MIN] =
            MIN(m->aggregates[BENCH_AGGREGATE_MIN], val);
    m->aggregates[BENCH_AGGREGATE_MAX] =
            MAX(m->aggregates[BENCH_AGGREGATE_MAX], val);
}

/**
 * trusty_bench_run_metrics -        Run All Metric Updaters after one iteration
 * of bench function for all param/metric in the last BENCH.
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @param_idx:          Index of the current parameter in the param_array of
 *                      BENCH.
 * @cold_run:           Are we updating metrics for the initial cold run?
 */
static inline void trusty_bench_run_metrics(struct list_node* metric_list,
                                            size_t param_idx,
                                            bool cold_run) {
    struct bench_metric_list_node* entry;

    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (param_idx == entry->param_idx) {
            if (cold_run) {
                entry->metric.cold = entry->bench_result();
            } else {
                trusty_bench_update_metric(&entry->metric,
                                           entry->bench_result());
            }
        }
    }
}

/**
 * trusty_bench_reset_metrics -        Run All Metric Updaters after one
 * iteration of bench function for all param/metric in the last BENCH.
 * @metric_list:        List of metrics aggregated during all BENCH runs.
 * @param_idx:          Index of the current parameter in the param_array of
 *                      BENCH.
 */
static inline void trusty_bench_reset_metrics(struct list_node* metric_list,
                                              size_t param_idx) {
    struct bench_metric_list_node* entry;

    list_for_every_entry(metric_list, entry, struct bench_metric_list_node,
                         node) {
        if (param_idx == entry->param_idx) {
            trusty_bench_update_metric(&entry->metric, entry->bench_result());
        }
    }
}

/**
 * BENCH_SETUP -        Runs before every execution of the body of the BENCH
 *                      macro. Can be used to allocate memory, setup 'states',
 *                      initialize 'sessions'...
 * @suite_name:         Identifier of the current suite.
 */
#define BENCH_SETUP(suite_name)          \
    static int suite_name##_setup(void); \
    static int suite_name##_setup(void)

/**
 * BENCH_TEARDOWN -     Runs after every execution of the body of the BENCH
 *                      macro. Can be used to free memory, clear 'states',
 *                      close 'sessions'...
 * @suite_name:         Identifier of the current suite.
 */
#define BENCH_TEARDOWN(suite_name)           \
    static void suite_name##_teardown(void); \
    static void suite_name##_teardown(void)

/**
 * BENCH_RESULT_INNER -       Declare a metric name for the corresponding BENCH
 * and declare the functions to update it after every iteration
 * @suite_name:         Identifier of the current suite.
 * @bench_name:         Unique identifier of the Bench in the suite.
 * @metric_name:        Name of the metric to print in the result table.
 * @formatted_value_cb:        [optional] A callback of
 * trusty_bench_get_formatted_value_callback_t type for formatting the result
 * value to a string
 * @param_name_cb:              [optional] A callback of
 * trusty_bench_get_param_name_callback_t type for formatting the param name
 */
#define BENCH_RESULT_INNER(suite_name, bench_name, metric_name,                 \
                           formatted_value_cb_, param_name_cb_)                 \
    static int64_t update_##suite_name##_##bench_name##_##metric_name(void);    \
    static struct bench_metric_list_node                                        \
            suite_name##_##bench_name##_##metric_name##_node = {                \
                    .node = LIST_INITIAL_CLEARED_VALUE,                         \
                    .metric = {0, 0, 0, {INT32_MAX, 0, 0}},                     \
                    .name = STRINGIFY(metric_name),                             \
                    .param_idx = 0,                                             \
                    .bench_result =                                             \
                            update_##suite_name##_##bench_name##_##metric_name, \
                    .formatted_value_cb = formatted_value_cb_,                  \
                    .param_name_cb = param_name_cb_};                           \
    __attribute__((constructor)) void                                           \
            suite_name##_##bench_name##_##metric_name##_add(void) {             \
        list_add_tail(&suite_name##_##bench_name##_metric_list,                 \
                      &suite_name##_##bench_name##_##metric_name##_node.node);  \
    }                                                                           \
                                                                                \
    static int64_t update_##suite_name##_##bench_name##_##metric_name(void)

/* Dispatch Mechanics for BENCH_RESULT */
#define BENCH_RESULT_3(suite_name, bench_name, metric_name) \
    BENCH_RESULT_INNER(suite_name, bench_name, metric_name, 0, 0)
#define BENCH_RESULT_4(suite_name, bench_name, metric_name, \
                       formatted_value_cb)                  \
    BENCH_RESULT_INNER(suite_name, bench_name, metric_name, \
                       formatted_value_cb, 0)
#define BENCH_RESULT_5(suite_name, bench_name, metric_name, \
                       formatted_value_cb, param_name_cb)   \
    BENCH_RESULT_INNER(suite_name, bench_name, metric_name, \
                       formatted_value_cb, param_name_cb)

/**
 * BENCH_RESULT             Dispatch BENCH_RESULT Called 3, 4 or 5 parameters.
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @metric_name:        Name of the metric to print in the result table.
 * @formatted_value_cb:        [optional] A callback of
 * trusty_bench_get_formatted_value_callback_t type for formatting the result
 * value to a string
 * @param_name_cb:              [optional] A callback of
 * trusty_bench_get_param_name_callback_t type for formatting the param name
 */
#define BENCH_RESULT(...) \
    CAT(BENCH_RESULT_, EVAL(NB_ARGS(__VA_ARGS__)))(__VA_ARGS__)

/**
 * PARAM_TEST_NODES_SIMPLE -    Create the unparameterized test node lists for
 *                              BENCH
 * @suite_name:                 Identifier of the current suite.
 * @bench_name:                 Unique identifier of the Bench in the suite.
 */
#define PARAM_TEST_NODES_SIMPLE(suite_name, bench_name)                        \
    static struct test_list_node suite_name##_##bench_name##_bench_##_node = { \
            .node = LIST_INITIAL_CLEARED_VALUE,                                \
            .suite = STRINGIFY(suite_name_),                                   \
            .name = STRINGIFY(bench_name_),                                    \
            .func = suite_name##_##bench_name##_bench_,                        \
            .needs_param = 0,                                                  \
    };                                                                         \
                                                                               \
    __attribute__((constructor)) void                                          \
            suite_name##_##bench_name##_bench_##_add(void) {                   \
        list_add_tail(&_test_list,                                             \
                      &suite_name##_##bench_name##_bench_##_node.node);        \
    }

/**
 * PARAM_TEST_NODES_PARAMETRIC -    Create the unparameterized test node lists
 *                                  for BENCH
 * @suite_name:                     Identifier of the current suite.
 * @bench_name:                     Unique identifier of the Bench in the suite.
 * @params:                         identifier of the param Array for parametric
 * benches
 */
#define PARAM_TEST_NODES_PARAMETRIC(suite_name, bench_name, params)       \
    static struct test_list_node                                          \
            suite_name##_##bench_name##_bench_##params##_node = {         \
                    .node = LIST_INITIAL_CLEARED_VALUE,                   \
                    .suite = STRINGIFY(suite_name_##params),              \
                    .name = STRINGIFY(bench_name_##params),               \
                    .func = suite_name##_##bench_name##_bench_##params,   \
                    .needs_param = 0,                                     \
    };                                                                    \
                                                                          \
    __attribute__((constructor)) void                                     \
            suite_name##_##bench_name##_bench_##params##_add(void) {      \
        list_add_tail(                                                    \
                &_test_list,                                              \
                &suite_name##_##bench_name##_bench_##params##_node.node); \
    }

/**
 * set_param_metric -       Create a list of parameterized metrics out of the
 *                          existing list of non-parameterized metric.
 * @unparameterized_list:   List of metrics aggregated during all BENCH
 *                          runs.
 * @parameterized_list:     Will be filled with nb_params *
 *                          length_of(unparameterized_list) metrics with
 *                          appropriate param_idx value.
 * @nb_params:              Number of parameters of the BENCH macro.
 * Return:                  The list of parameterized metrics.
 */
static inline struct bench_metric_list_node* set_param_metric(
        struct list_node* unparameterized_list,
        struct list_node* parameterized_list,
        size_t nb_params) {
    size_t idx = 0;
    struct bench_metric_list_node* entry;
    struct bench_metric_list_node* list_pool =
            calloc(nb_params * list_length(unparameterized_list),
                   sizeof(struct bench_metric_list_node));
    if (list_pool == NULL) {
        TLOGE("Failed to Allocate memory for bench_metric_list_node!");
        return NULL;
    }
    list_for_every_entry(unparameterized_list, entry,
                         struct bench_metric_list_node, node) {
        for (size_t idx_param = 0; idx_param < nb_params; ++idx_param) {
            struct bench_metric_node tmp_metric = {0, 0, 0, {INT32_MAX, 0, 0}};

            list_pool[idx].metric = tmp_metric;
            list_pool[idx].name = entry->name;
            list_pool[idx].param_idx = idx_param;
            list_pool[idx].bench_result = entry->bench_result;
            list_pool[idx].formatted_value_cb = entry->formatted_value_cb;
            list_pool[idx].param_name_cb = entry->param_name_cb;
            list_add_tail(parameterized_list, &(list_pool[idx].node));
            ++idx;
        }
    }
    return list_pool;
}

/**
 * trusty_bench_get_overhead - Get Minimal overhead of the benchmark around
 * benched function
 *
 * Return:        The Value of the overhead in nanoseconds.
 */
static int64_t trusty_bench_get_overhead(void) {
    const size_t nb_runs = 100;
    int64_t start_time;
    int64_t end_time;
    int64_t res = INT64_MAX;

    for (size_t i = 0; i < nb_runs; ++i) {
        start_time = get_current_time_ns();
        end_time = get_current_time_ns();
        res = MIN(end_time - start_time, res);
    }
    return res;
}

/**
 * get_extended_bench_name - Print Status of Currently Running Bench.
 *
 * @test_name_in:   Name of the Current Unparameterized Test.
 * @test_name_out:  Name of the Current Unparameterized Test.
 *                  + "_[param_idx]"
 *
 * Return:          When successful, returns 0
 *                  If test_name_out allocation/print failed returns asprintf
 *                  return code
 */
static inline int get_extended_bench_name(const char* test_name_in,
                                          char** test_name_out) {
    int res = snprintf(NULL, 0, "%s_%zu", test_name_in,
                       bench_state.cur_param_idx);
    *test_name_out = NULL;
    if (res >= 0) {
        *test_name_out = malloc(res + 1);
        res = snprintf(*test_name_out, res + 1, "%s_%zu", test_name_in,
                       bench_state.cur_param_idx);
    }
    if (res < 0) {
        return res;
    }
    if (!test_name_out) {
        TLOGE("Cannot Allocate memory for test name\n");
        return -1;
    }
    return 0;
}

/**
 * BENCH_CORE -             Called by both parametrized and unparameterized
 * BENCH for their common part
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @nb_runs:                The number of execution of its body for each param
 * @nb_params:              Number of params in params array
 * @params:                 An array T array_name[nb_params] of parameter
 * @metric_list:            List of metric nodes to update
 */
#define BENCH_CORE(suite_name, bench_name, nb_runs, nb_params, params,          \
                   metric_list)                                                 \
    reset_vertical_print_widths();                                              \
    trusty_bench_print_title(STRINGIFY(suite_name), STRINGIFY(bench_name),      \
                             STRINGIFY(params));                                \
    static trusty_bench_print_callback_t trusty_bench_print_cb =                \
            &BENCHMARK_PRINT_CB;                                                \
    trusty_cur_bench_nb_params = nb_params;                                     \
    for (size_t idx_param = 0; idx_param < (nb_params * trusty_bench_nb_cpu);   \
         ++idx_param) {                                                         \
        bench_state.cur_param_idx = idx_param;                                  \
        char* extended_test_name = NULL;                                        \
        int res_alloc = get_extended_bench_name(                                \
                STRINGIFY(bench_name##_##params), &extended_test_name);         \
        if (res_alloc < 0) {                                                    \
            TLOGE("ERROR %d expanding test name\n", res_alloc);                 \
            _test_context.all_ok = false;                                       \
            _test_context.tests_failed++;                                       \
            continue;                                                           \
        }                                                                       \
        TEST_BEGIN_FUNC(STRINGIFY(suite_name), extended_test_name);             \
                                                                                \
        int rc = trusty_bench_multi_cpus_setup();                               \
        if (rc != NO_ERROR) {                                                   \
            _test_context.skipped = true;                                       \
            _test_context.tests_skipped++;                                      \
        } else {                                                                \
            rc = suite_name##_setup();                                          \
        }                                                                       \
                                                                                \
        if (_test_context.skipped) {                                            \
            trusty_unittest_print_status(" SKIPPED");                           \
            continue;                                                           \
        } else if (rc != NO_ERROR) {                                            \
            TLOGE("ERROR %d during benchmark setup\n", rc);                     \
            _test_context.all_ok = false;                                       \
            _test_context.tests_failed++;                                       \
            continue;                                                           \
        }                                                                       \
        int64_t overhead = trusty_bench_get_overhead();                         \
                                                                                \
        /* Cold Run */                                                          \
        int64_t start_time;                                                     \
        int64_t end_time;                                                       \
        start_time = get_current_time_ns();                                     \
        int64_t res = suite_name##_##bench_name##_inner_##params();             \
        end_time = get_current_time_ns();                                       \
                                                                                \
        if (res != NO_ERROR) {                                                  \
            TLOGE("ERROR During Cold Run%" PRId64 "\n", res);                   \
            _test_context.all_ok = false;                                       \
            _test_context.tests_failed++;                                       \
            continue;                                                           \
        }                                                                       \
                                                                                \
        bench_state.last_bench_body_duration = end_time - start_time;           \
        if (5 * overhead >= bench_state.last_bench_body_duration) {             \
            trusty_unittest_printf(                                             \
                    "WARNING: Benchmark internal function is too fast %" PRId64 \
                    "ns, while the benchmark overhead is %" PRId64 "ns.",       \
                    overhead, bench_state.last_bench_body_duration);            \
        }                                                                       \
                                                                                \
        bench_state.last_bench_body_duration -= overhead;                       \
                                                                                \
        if (!_test_context.hard_fail && _test_context.all_ok) {                 \
            trusty_bench_run_metrics(&metric_list, idx_param, true);            \
        }                                                                       \
                                                                                \
        for (size_t idx_run = 0; idx_run < nb_runs; ++idx_run) {                \
            if (!_test_context.hard_fail && _test_context.all_ok) {             \
                start_time = get_current_time_ns();                             \
                res = suite_name##_##bench_name##_inner_##params();             \
                end_time = get_current_time_ns();                               \
                bench_state.last_bench_body_duration = end_time - start_time;   \
                if (overhead >= bench_state.last_bench_body_duration) {         \
                    TLOGE("Benchmark internal function is too fast %" PRId64    \
                          "ns, while the benchmark overhead is %" PRId64        \
                          "ns.",                                                \
                          overhead, bench_state.last_bench_body_duration);      \
                }                                                               \
                                                                                \
                bench_state.last_bench_body_duration -= overhead;               \
                if (res != NO_ERROR) {                                          \
                    TLOGE("ERROR %" PRId64 "\n", res);                          \
                }                                                               \
            }                                                                   \
            if (!_test_context.hard_fail && _test_context.all_ok) {             \
                trusty_bench_run_metrics(&metric_list, idx_param, false);       \
            }                                                                   \
        }                                                                       \
        suite_name##_teardown();                                                \
        rc = trusty_bench_multi_cpus_teardown();                                \
        if (rc != NO_ERROR) {                                                   \
            TLOGW("failed to reset CPU affinity: %d\n", rc);                    \
        }                                                                       \
        TEST_END_FUNC();                                                        \
        free(extended_test_name);                                               \
        extended_test_name = NULL;                                              \
    }                                                                           \
    trusty_bench_print_cb(&metric_list, (nb_params * trusty_bench_nb_cpu),      \
                          STRINGIFY(suite_name),                                \
                          STRINGIFY(bench_name##_##params));                    \
    trusty_bench_get_param_name_cb = NULL;                                      \
    trusty_bench_get_formatted_value_cb = NULL

/**
 * BENCH_PARAMETERIZED_PTR -Called when BENCH has 5 parameters. This allows
 *                          to reuse Other macros for different bench by
 * aliasing an array to a pointer
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @nb_runs:                The number of execution of its body for each param
 * @params:                 An array T array_name[nb_params] of parameter
 * @nb_params:              Number of parameters in the parameter Array
 */
#define BENCH_PARAMETERIZED_PTR(nb_cpu, suite_name, bench_name, nb_runs,         \
                                params, nb_params)                               \
    static int suite_name##_##bench_name##_inner_##params(void);                 \
    static void suite_name##_##bench_name##_bench_##params(void);                \
    static struct list_node suite_name##_##bench_name##_metric_list =            \
            LIST_INITIAL_VALUE(suite_name##_##bench_name##_metric_list);         \
    static struct list_node suite_name##_##bench_name##_metric_##params##_list = \
            LIST_INITIAL_VALUE(                                                  \
                    suite_name##_##bench_name##_metric_##params##_list);         \
                                                                                 \
    static void suite_name##_##bench_name##_bench_##params(void) {               \
        trusty_bench_nb_cpu = nb_cpu;                                            \
        struct bench_metric_list_node* metric_pool = set_param_metric(           \
                &suite_name##_##bench_name##_metric_list,                        \
                &suite_name##_##bench_name##_metric_##params##_list,             \
                (nb_params * trusty_bench_nb_cpu));                              \
        if (metric_pool == NULL) {                                               \
            _test_context.hard_fail = true;                                      \
            return;                                                              \
        }                                                                        \
        BENCH_CORE(suite_name, bench_name, nb_runs, nb_params, params,           \
                   suite_name##_##bench_name##_metric_##params##_list);          \
        free(metric_pool);                                                       \
    }                                                                            \
    PARAM_TEST_NODES(suite_name, bench_name, params)                             \
                                                                                 \
    static int suite_name##_##bench_name##_inner_##params(void)

/**
 * BENCH_PARAMETERIZED -    Called when BENCH has 4 parameters
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @nb_runs:                The number of execution of its body for each param
 * @params:                 An array T array_name[nb_params] of parameter
 */
#define BENCH_PARAMETERIZED(nb_cpu, suite_name, bench_name, nb_runs, params) \
    BENCH_PARAMETERIZED_PTR(nb_cpu, suite_name, bench_name, nb_runs, params, \
                            countof(params))

/**
 * BENCH_SIMPLE -       Called when BENCH has only 3 parameters.
 * @suite_name:         Identifier of the current suite.
 * @bench_name:         Unique identifier of the Bench in the suite.
 * @nb_runs:            The number of execution of its body.
 */
#define BENCH_SIMPLE(nb_cpu, suite_name, bench_name, nb_runs)                  \
    static int suite_name##_##bench_name##_inner_(void);                       \
    static void suite_name##_##bench_name##_bench_(void);                      \
    static struct list_node suite_name##_##bench_name##_metric_list =          \
            LIST_INITIAL_VALUE(suite_name##_##bench_name##_metric_list);       \
    static struct list_node suite_name##_##bench_name##_metric_cpu##_list =    \
            LIST_INITIAL_VALUE(suite_name##_##bench_name##_metric_cpu##_list); \
    static void suite_name##_##bench_name##_bench_(void) {                     \
        bench_state.cur_param_idx = 0;                                         \
        trusty_bench_nb_cpu = nb_cpu;                                          \
        struct bench_metric_list_node* metric_pool = set_param_metric(         \
                &suite_name##_##bench_name##_metric_list,                      \
                &suite_name##_##bench_name##_metric_cpu##_list,                \
                trusty_bench_nb_cpu);                                          \
        if (metric_pool == NULL) {                                             \
            _test_context.hard_fail = true;                                    \
            return;                                                            \
        }                                                                      \
        BENCH_CORE(suite_name, bench_name, nb_runs, 1, ,                       \
                   suite_name##_##bench_name##_metric_cpu##_list);             \
    }                                                                          \
                                                                               \
    PARAM_TEST_NODES(suite_name, bench_name)                                   \
    static int suite_name##_##bench_name##_inner_(void)

/*
 * BENCH - Routing the BENCH macros depending on its number of parameters.
 */
#define BENCH_3 BENCH_SIMPLE
#define BENCH_4 BENCH_PARAMETERIZED
#define BENCH_5 BENCH_PARAMETERIZED_PTR

/**
 * BENCH - Called 3, 4 or 5 parameters. This allows
 *                          to reuse Other macros for different bench by
 * aliasing an array to a pointer
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @nb_runs:                The number of execution of its body for each param
 * @params:                 [optional] An array T array_name[nb_params] of
 *                          parameter, or a pointer T*, in the latter case a 5th
 *                          parameter is needed
 * @nb_params:              [optional] if 4th parameter is a pointer, Number of
 *                          parameters in the parameter Array
 */
#define BENCH(...) CAT(BENCH_, EVAL(NB_ARGS(__VA_ARGS__)))(1, __VA_ARGS__)

/**
 * BENCH_ALL_CPU - Called 3, 4 or 5 parameters. This allows
 *                          to reuse Other macros for different bench by
 * aliasing an array to a pointer
 * @suite_name:             Identifier of the current suite.
 * @bench_name:             Unique identifier of the Bench in the suite.
 * @nb_runs:                The number of execution of its body for each param
 * @params:                 [optional] An array T array_name[nb_params] of
 *                          parameter, or a pointer T*, in the latter case a 5th
 *                          parameter is needed
 * @nb_params:              [optional] if 4th parameter is a pointer, Number of
 *                          parameters in the parameter Array
 */
#define BENCH_ALL_CPU(...) \
    CAT(BENCH_, EVAL(NB_ARGS(__VA_ARGS__)))(SMP_MAX_CPUS, __VA_ARGS__)

/*
 * PARAM_TEST_NODES - Routing the PARAM_TEST_NODES macros depending on its
 * number of parameters.
 */
#define PARAM_TEST_NODES_2 PARAM_TEST_NODES_SIMPLE
#define PARAM_TEST_NODES_3 PARAM_TEST_NODES_PARAMETRIC
#define PARAM_TEST_NODES(...) \
    CAT(PARAM_TEST_NODES_, EVAL(NB_ARGS(__VA_ARGS__)))(__VA_ARGS__)

__END_CDECLS
