/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <lk/compiler.h>
#include <lk/list.h>
#include <stdbool.h>
#include <string.h>

__BEGIN_CDECLS

/*
 * Test functions can be defined with:
 * TEST(SuiteName, TestName) {
 *   ... test body ...
 * }
 * or with:
 * TEST_F(SuiteName, TestName) {
 *   ... test body ...
 * }
 * or with:
 * TEST_P(SuiteName, TestName) {
 *   ... test body ...
 * }
 *
 * NOTE: SuiteName and TestName should not contain underscores.
 *
 * Use EXPECT_<op> or ASSERT_<op> directly in test functions or from nested
 * functions to check test conditions. Where <op> can be:
 *   EQ for ==
 *   NE for !=
 *   LT for <
 *   LE for <=
 *   GT for >
 *   GE for >=
 *
 * The test functions follows this pattern:
 *   <EXPECT|ASSERT>_<op>(val1, val2 [, format, ...])
 * If val1 <op> val2 is not true, then both values will be printed and a test
 * failure will be recorded. For ASSERT_<op> it will also jump to a test_abort
 * label in the calling function.
 *
 * Call RUN_ALL_TESTS() to run all tests defined by TEST (or
 * RUN_ALL_SUITE_TESTS("SuiteName") to only run tests with the specified
 * SuiteName). RUN_ALL_TESTS and RUN_ALL_SUITE_TESTS return true if all the
 * tests passed.
 *
 * Test functions defined with TEST_F or TEST_P expect the type <SuiteName>_t
 * and <SuiteName>_SetUp and <SuiteName>_TearDown functions to be defined.
 * The <SuiteName>_SetUp function will be called once before each test in
 * SuiteName in run and the <SuiteName>_TearDown function will be called once
 * after each test in SuiteName is run. These functions can be defined with
 * TEST_F_SETUP(<SuiteName>) {
 *  ... setup body ...
 * }
 * and with:
 * TEST_F_TEARDOWN(<SuiteName>) {
 *  ... teardown body ...
 * }
 * A pointer to a <SuiteName>_t variable will be passed as "_state" to the
 * setup, teardown and test functions.
 *
 * TEST_FIXTURE_ALIAS(NewSuiteName, OldSuiteName) can be used to use the test
 * fixture defined for OldSuiteName with NewSuiteName.
 *
 * Tests defined with TEST_P will only run when their suite is run if they have
 * been instantiated with parameters using INSTANTIATE_TEST_SUITE_P. These tests
 * can access their parameter using GetParam()
 */

#ifndef trusty_unittest_printf
#error trusty_unittest_printf must be defined
#endif

/**
 * struct test_context - struct representing the state of a test run.
 * @tests_total:    Number of conditions checked
 * @tests_skipped:  Number of tests skipped
 * @tests_disabled: Number of disabled tests skipped
 * @tests_failed:   Number of conditions failed
 * @inst_name:      Name of the current parameter instantiation
 * @suite_name:     Name of the current test suite
 * @param_name:     Name of the current parameter
 * @test_name:      Name of current test case
 * @test_param:     The current test parameter
 * @all_ok:         State of current test case
 * @skipped:        Current test was skipped.
 * @hard_fail:      Type of test failure (when @all_ok is false)
 */
struct test_context {
    unsigned int tests_total;
    unsigned int tests_skipped;
    unsigned int tests_disabled;
    unsigned int tests_failed;
    const char* inst_name;
    const char* suite_name;
    const char* param_name;
    const char* test_name;
    const void* test_param;
    bool all_ok;
    bool skipped;
    bool hard_fail;
};

/**
 * struct test_list_node - node to hold test function in list of tests
 * @node:           List node
 * @suite:          Name of test suite (optionally used for filtering)
 * @name:           Name of test (optionally used for filtering)
 * @func:           Test function
 * @needs_param:    Indicates if the test function is parameterized
 */

struct test_list_node {
    struct list_node node;
    const char* suite;
    const char* name;
    void (*func)(void);
    bool needs_param;
};

/**
 * struct test_param_gen -  struct representing a parameter generator
 * @gen_param:              Function to generate the parameter for a test
 * @priv:                   Private data passed to gen_param
 */
struct test_param_gen {
    const void* (*gen_param)(void*, int);
    void* priv;
};

/**
 * typedef test_param_to_string_t - Converts a test parameter to its string form
 * @param:      Parameter to convert
 * @buf:        Buffer to fill with a NULL terminated string representation of
 *              @param
 * @buf_size:   Size in bytes of @buf
 *
 * When called, this function is passed a pointer to the parameter for the test
 * that is being executed in @param and must return a null-terminated string
 * representing the passed in parameter in @buf of at most size @buf_size.
 */
typedef void (*test_param_to_string_t)(const void* param,
                                       char* buf,
                                       size_t buf_size);

/**
 * struct test_param_list_node - holds parameter generators
 * @node:               List node
 * @param_gen:          Parameter generator
 * @to_string:          Function to convert a parameter to its string form
 * @inst_name:          Name of the instantiation associated with the generator
 * @suite:              Name of test suite associated with the generator
 */

struct test_param_list_node {
    struct list_node node;
    struct test_param_gen param_gen;
    test_param_to_string_t to_string;
    const char* inst_name;
    const char* suite;
};

static struct test_context _test_context;

/*
 * List of tests. Tests are added by a __attribute__((constructor)) function
 * per test defined by the TEST macro.
 */
static struct list_node _test_list = LIST_INITIAL_VALUE(_test_list);

/*
 * List of parameter generators. Parameter generators  are added by a
 * __attribute__((constructor)) function per instantiation defined with
 * INSTANTIATE_TEST_SUITE_P.
 */
static struct list_node _test_param_list = LIST_INITIAL_VALUE(_test_param_list);

static inline void trusty_unittest_print_status_name(const char* suite_name,
                                                     const char* test_name,
                                                     const char* status) {
    if (_test_context.test_param) {
        trusty_unittest_printf("[ %s ] %s/%s.%s/%s\n", status,
                               _test_context.inst_name, suite_name, test_name,
                               _test_context.param_name);
    } else {
        trusty_unittest_printf("[ %s ] %s.%s\n", status, suite_name, test_name);
    }
}

static inline void trusty_unittest_print_status(const char* status) {
    trusty_unittest_print_status_name(_test_context.suite_name,
                                      _test_context.test_name, status);
}

static inline void TEST_BEGIN_FUNC(const char* suite_name,
                                   const char* test_name) {
    _test_context.suite_name = suite_name;
    _test_context.test_name = test_name;
    _test_context.all_ok = true;
    _test_context.hard_fail = false;
    _test_context.skipped = false;
    _test_context.tests_total++;
    trusty_unittest_print_status("RUN     ");
}

static inline void TEST_END_FUNC(void) {
    if (_test_context.skipped) {
        trusty_unittest_print_status(" SKIPPED");
    } else if (_test_context.all_ok) {
        trusty_unittest_print_status("      OK");
    } else {
        trusty_unittest_print_status(" FAILED ");
    }
    _test_context.test_name = NULL;
}

#define STRINGIFY(x) #x

#define TEST_FIXTURE_ALIAS(new_suite_name, old_suite_name)              \
    typedef old_suite_name##_t new_suite_name##_t;                      \
                                                                        \
    static void new_suite_name##_SetUp(new_suite_name##_t* _state) {    \
        old_suite_name##_SetUp(_state);                                 \
    }                                                                   \
    static void new_suite_name##_TearDown(new_suite_name##_t* _state) { \
        old_suite_name##_TearDown(_state);                              \
    }

#define TEST_INTERNAL(suite_name, test_name, w_param, pre, post, arg, argp)  \
    static void suite_name##_##test_name##_inner argp;                       \
                                                                             \
    static void suite_name##_##test_name(void) {                             \
        TEST_BEGIN_FUNC(STRINGIFY(suite_name), STRINGIFY(test_name));        \
        {                                                                    \
            pre;                                                             \
            if (!_test_context.hard_fail && !_test_context.skipped) {        \
                suite_name##_##test_name##_inner arg;                        \
            }                                                                \
            post;                                                            \
        }                                                                    \
        TEST_END_FUNC();                                                     \
    }                                                                        \
                                                                             \
    static struct test_list_node suite_name##_##test_name##_node = {         \
            .node = LIST_INITIAL_CLEARED_VALUE,                              \
            .suite = #suite_name,                                            \
            .name = #test_name,                                              \
            .func = suite_name##_##test_name,                                \
            .needs_param = w_param,                                          \
    };                                                                       \
                                                                             \
    __attribute__((constructor)) void suite_name##_##test_name##_add(void) { \
        list_add_tail(&_test_list, &suite_name##_##test_name##_node.node);   \
    }                                                                        \
                                                                             \
    static void suite_name##_##test_name##_inner argp

#define TEST_F_SETUP(suite_name) \
    static void suite_name##_SetUp(suite_name##_t* _state)

#define TEST_F_TEARDOWN(suite_name) \
    static void suite_name##_TearDown(suite_name##_t* _state)

#define TEST(suite_name, test_name) \
    TEST_INTERNAL(suite_name, test_name, false, , , (), (void))

#define TEST_F_CUSTOM_ARGS(suite_name, test_name, arg, argp)                  \
    TEST_INTERNAL(suite_name, test_name, false, suite_name##_t state;         \
                  suite_name##_SetUp(&state);, suite_name##_TearDown(&state); \
                  , arg, argp)

#define TEST_F(suite_name, test_name)                   \
    TEST_F_CUSTOM_ARGS(suite_name, test_name, (&state), \
                       (suite_name##_t * _state))

#define TEST_P_CUSTOM_ARGS(suite_name, test_name, arg, argp)                  \
    TEST_INTERNAL(suite_name, test_name, true, suite_name##_t state;          \
                  suite_name##_SetUp(&state);, suite_name##_TearDown(&state); \
                  , arg, argp)

#define TEST_P(suite_name, test_name)                   \
    TEST_P_CUSTOM_ARGS(suite_name, test_name, (&state), \
                       (suite_name##_t * _state))

struct test_array_param {
    const void* arr;
    int elem_size;
    int count;
};

static inline const void* test_gen_array_param(void* priv, int i) {
    struct test_array_param* param = (struct test_array_param*)priv;

    if (i >= param->count) {
        return NULL;
    }

    return (uint8_t*)param->arr + param->elem_size * i;
}

struct test_range_param {
    long begin;
    long end;
    long step;
    long current;
};

static inline const void* test_gen_range_param(void* priv, int i) {
    struct test_range_param* range_param = (struct test_range_param*)priv;

    range_param->current = range_param->begin + range_param->step * i;

    if (range_param->current >= range_param->end) {
        return NULL;
    }

    return &range_param->current;
}

struct combined_params {
    struct test_param_gen* generators;
    int generator_count;
    int* idxs;
    const void** current;
};

static inline void update_combined_params(struct combined_params* params,
                                          int j,
                                          bool reset) {
    if (reset) {
        params->idxs[j] = 0;
    }

    params->current[j] = params->generators[j].gen_param(
            params->generators[j].priv, params->idxs[j]);
    params->idxs[j]++;
}

static inline const void* test_gen_combined_param(void* priv, int i) {
    struct combined_params* params = (struct combined_params*)priv;

    if (i == 0) {
        for (int j = 0; j < params->generator_count; j++) {
            update_combined_params(params, j, true);
        }
        return params->current;
    }

    for (int j = 0; j < params->generator_count; j++) {
        update_combined_params(params, j, false);

        if (params->current[j] != NULL) {
            return params->current;
        }

        update_combined_params(params, j, true);
    }

    return NULL;
}

#define FIRST_ARG(arg0, args...) arg0
#define SECOND_ARG(arg0, arg1, args...) arg1
/* Parentheses are used to prevent commas from being interpreted when they are
 * passed in macro arguments. DELETE_PAREN is used to remove these parentheses
 * inside the macro that uses the commas e.g.:
 *
 * MY_MACRO((1, 2, 3))
 *
 * #define MY_MACRO(arg)
 *      DELETE_PAREN arg
 */
#define DELETE_PAREN(args...) args

#define testing_Range(_begin, end_step...)                  \
    (static struct test_range_param range_param =           \
             {                                              \
                     .begin = _begin,                       \
                     .end = FIRST_ARG(end_step, ),          \
                     .step = SECOND_ARG(end_step, 1, ),     \
             };                                             \
     param_node.param_gen.gen_param = test_gen_range_param; \
     param_node.param_gen.priv = &range_param;)

#define testing_ValuesIn(array)                             \
    (static struct test_array_param array_param =           \
             {                                              \
                     .arr = array,                          \
                     .elem_size = sizeof(array[0]),         \
                     .count = countof(array),               \
             };                                             \
                                                            \
     param_node.param_gen.gen_param = test_gen_array_param; \
     param_node.param_gen.priv = &array_param;)

/*
 * (args, args) is passed to __typeof__ to guarantee that it resolves to const
 * char* instead of const char[] in cases where args contains a single string.
 * When args is a single string, it is inlined and typeof will resolve to const
 * char[].
 */
#define testing_Values(args...)                        \
    (static __typeof__(args, args) new_arr[] = {args}; \
     DELETE_PAREN testing_ValuesIn(new_arr))

#define testing_Bool() testing_Values(false, true)

#define test_set_combine_params(generator, i, count)                  \
    {                                                                 \
        DELETE_PAREN generator;                                       \
        if (i < count) {                                              \
            param_gens[i].gen_param = param_node.param_gen.gen_param; \
            param_gens[i].priv = param_node.param_gen.priv;           \
        }                                                             \
    }

#define testing_Combine_internal(arg0, arg1, arg2, arg3, arg4, arg5, arg6,   \
                                 arg7, arg8, arg9, da0, da1, da2, da3, da4,  \
                                 da5, da6, da7, da8, da9, count, args...)    \
    (static struct test_param_gen param_gens[count]; static int idxs[count]; \
     static const void* current_params[count];                               \
     static struct combined_params combined_params =                         \
             {                                                               \
                     param_gens,                                             \
                     count,                                                  \
                     idxs,                                                   \
                     current_params,                                         \
             };                                                              \
                                                                             \
     test_set_combine_params(arg0, 0, count);                                \
     test_set_combine_params(arg1, 1, count);                                \
     test_set_combine_params(arg2, 2, count);                                \
     test_set_combine_params(arg3, 3, count);                                \
     test_set_combine_params(arg4, 4, count);                                \
     test_set_combine_params(arg5, 5, count);                                \
     test_set_combine_params(arg6, 6, count);                                \
     test_set_combine_params(arg7, 7, count);                                \
     test_set_combine_params(arg8, 8, count);                                \
     test_set_combine_params(arg9, 9, count);                                \
     param_node.param_gen.gen_param = test_gen_combined_param;               \
     param_node.param_gen.priv = &combined_params;)

#define testing_Combine(generators...)                                       \
    testing_Combine_internal(generators, (), (), (), (), (), (), (), (), (), \
                             (), 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

#define INSTANTIATE_TEST_SUITE_P_INTERNAL(_inst_name, suite_name, param_gen, \
                                          param_to_string, args...)          \
                                                                             \
    __attribute__((constructor)) void suite_name##_##_inst_name##param_add(  \
            void) {                                                          \
        static struct test_param_list_node param_node = {                    \
                .node = LIST_INITIAL_CLEARED_VALUE,                          \
                .to_string = param_to_string,                                \
                .inst_name = STRINGIFY(_inst_name),                          \
                .suite = #suite_name,                                        \
        };                                                                   \
                                                                             \
        DELETE_PAREN param_gen;                                              \
                                                                             \
        list_add_tail(&_test_param_list, &param_node.node);                  \
    }

static inline bool has_disabled_prefix(const char* str) {
    const char disabled_prefix[] = "DISABLED_";
    return strncmp(str, disabled_prefix, strlen(disabled_prefix)) == 0;
}

static inline bool test_is_disabled(struct test_list_node* entry) {
    return has_disabled_prefix(entry->suite) ||
           has_disabled_prefix(entry->name);
}

static bool test_suite_instantiated(const char* suite) {
    struct test_param_list_node* param_entry;
    list_for_every_entry(&_test_param_list, param_entry,
                         struct test_param_list_node, node) {
        if (!strcmp(suite, param_entry->suite)) {
            return true;
        }
    }
    return false;
}

static void run_test_suite(const char* suite, bool needs_param) {
    struct test_list_node* entry;
    bool valid_suite = false;

    list_for_every_entry(&_test_list, entry, struct test_list_node, node) {
        if ((!suite || !strcmp(suite, entry->suite)) &&
            (entry->needs_param == needs_param)) {
            valid_suite = true;
            if (test_is_disabled(entry)) {
                trusty_unittest_print_status_name(entry->suite, entry->name,
                                                  "DISABLED");
                _test_context.tests_disabled++;
            } else {
                entry->func();
            }
        }
        if (!needs_param && entry->needs_param &&
            !test_suite_instantiated(entry->suite)) {
            trusty_unittest_print_status_name(entry->suite, entry->name,
                                              "NO PARAM");
            _test_context.tests_failed++;
        }
    }
    if (needs_param && !valid_suite) {
        trusty_unittest_print_status_name(suite, "[NO TESTS]", " FAILED ");
        _test_context.tests_failed++;
    }
}

/*
 * The testing framework uses 3 global variables to keep track of tests and
 * related data:
 *
 * _test_context: contains information about the overall execution of the
 * framework (e.g. total tests run) and information about the currently
 * executing test (e.g. test name, suite name).
 *
 * _test_list: contains a list of tests that can be run. Each test belongs to a
 * test suite and may require parameters to be run.
 *
 * _test_param_list: contains a list of parameter generators for tests that
 * require parameters. Each generator is associated with a specific test suite.
 * Parameter generators are functions that return parameters that apply to all
 * the tests that require parameters (i.e. parameterized tests) in a given test
 * suite.
 *
 * Tests are only run as part of test suites. When a test suite is run all of
 * the non-paremeterized tests belonging to that suite are run first followed by
 * the parameterized tests in the suite. All of the parameterized tests in a
 * suite are run once for each value returned by a parameter generator
 * associated with that suite.
 */
static inline bool RUN_ALL_SUITE_TESTS(const char* suite) {
    struct test_param_list_node* param_entry;
    const void* test_param;
    int i;
    char param_str[64];
    _test_context.tests_total = 0;
    _test_context.tests_disabled = 0;
    _test_context.tests_failed = 0;
    _test_context.test_param = NULL;
    _test_context.param_name = param_str;

    /* Run all the non-parameterized tests in the suite */
    run_test_suite(suite, false);

    /* For each parameter generator associated with the suite */
    list_for_every_entry(&_test_param_list, param_entry,
                         struct test_param_list_node, node) {
        if (!suite || !strcmp(suite, param_entry->suite)) {
            i = 0;
            /* For each parameter from the generator */
            while ((test_param = param_entry->param_gen.gen_param(
                            param_entry->param_gen.priv, i))) {
                /* Set the parameter for the next run */
                _test_context.inst_name = param_entry->inst_name;
                _test_context.test_param = test_param;
                if (param_entry->to_string) {
                    param_entry->to_string(test_param, param_str,
                                           sizeof(param_str));
                } else {
                    snprintf(param_str, sizeof(param_str), "%d", i);
                }
                /* Run all the parameterized tests in the suite */
                run_test_suite(param_entry->suite, true);
                i++;
            }
        }
    }

    trusty_unittest_printf("[==========] %d tests ran.\n",
                           _test_context.tests_total);
    if (_test_context.tests_total != _test_context.tests_failed) {
        trusty_unittest_printf(
                "[  PASSED  ] %d tests.\n",
                _test_context.tests_total - _test_context.tests_failed);
    }
    if (_test_context.tests_skipped) {
        trusty_unittest_printf("[  SKIPPED ] %d tests.\n",
                               _test_context.tests_skipped);
    }
    if (_test_context.tests_disabled) {
        trusty_unittest_printf("[ DISABLED ] %d tests.\n",
                               _test_context.tests_disabled);
    }
    if (_test_context.tests_failed) {
        trusty_unittest_printf("[  FAILED  ] %d tests.\n",
                               _test_context.tests_failed);
    }
    return _test_context.tests_failed == 0;
}

static inline bool RUN_ALL_TESTS(void) {
    return RUN_ALL_SUITE_TESTS(NULL);
}

/**
 * GTEST_SKIP() - Skip current test
 *
 * This will skip the current test without triggering a failure. It will use
 * same test_abort label as the ASSERT_... macros. Calling this after a test has
 * failed or calling ASSERT_.../EXPECT_... macros after GTEST_SKIP has jumped
 * to test_abort is not supported.
 */
#define GTEST_SKIP()                       \
    {                                      \
        if (!_test_context.skipped) {      \
            _test_context.skipped = true;  \
            _test_context.tests_skipped++; \
        }                                  \
        goto test_abort;                   \
    }

#define ASSERT_EXPECT_TEST(op, op_pre, op_sep, op_args, is_hard_fail,        \
                           fail_action, vals_type, vals_format_placeholder,  \
                           print_cast, print_op, val1, val2, extra_msg...)   \
    {                                                                        \
        vals_type _val1 = val1;                                              \
        vals_type _val2 = val2;                                              \
        if (!op_pre(_val1 DELETE_PAREN op_sep _val2 DELETE_PAREN op_args)) { \
            trusty_unittest_printf("%s: @ %s:%d\n", _test_context.test_name, \
                                   __FILE__, __LINE__);                      \
            trusty_unittest_printf(                                          \
                    "  expected: %s (" vals_format_placeholder ") " print_op \
                    " %s (" vals_format_placeholder ")\n",                   \
                    #val1, print_cast _val1, #val2, print_cast _val2);       \
            trusty_unittest_printf("  " extra_msg);                          \
            trusty_unittest_printf("\n");                                    \
            if (_test_context.all_ok) {                                      \
                _test_context.all_ok = false;                                \
                _test_context.tests_failed++;                                \
            }                                                                \
            _test_context.hard_fail |= is_hard_fail;                         \
            fail_action                                                      \
        }                                                                    \
    }

static inline bool HasFailure(void) {
    return !_test_context.all_ok;
}

/**
 * INSTANTIATE_TEST_SUITE_P - Instantiate parameters for a test suite
 * @inst_name:          Name for instantiation of parameters. Should not contain
 *                      underscores.
 * @suite_name:         Name of test suite associated with the parameters
 * @param_gen:          One of the parameter generators (see below)
 * @param_to_string:    Function of type &typedef test_param_to_string_t
 *                      used to convert a parameter to its string form. This
 *                      argument is optional.
 *
 * Parameter Generators:
 *  testing_Range(being, end, step):
 *  Returns the values {begin, being+step, being+step+step, ...} up to but not
 *  including end. step is optional and defaults to 1.
 *
 *  testing_Values(v1, v2, ..., vN):
 *  Returns the values {v1, v2, ..., vN)
 *
 *  testing_ValuesIn(array)
 *  Returns the values in array
 *
 *  testing_Bool()
 *  Returns {false, true}
 *
 *  testing_Combine(g1, [g2, g3, g4, g5]):
 *  Returns the values of the combinations of the provided generators
 *  (min 1, max 5) an as an array.
 */
#define INSTANTIATE_TEST_SUITE_P(inst_name, suite_name, param_gen_args...)   \
    INSTANTIATE_TEST_SUITE_P_INTERNAL(inst_name, suite_name, param_gen_args, \
                                      NULL, )

/**
 * GetParam() - Returns a pointer to the current test parameter
 *
 * Context: This function can be called within a parameterized test to
 *          retrieve the current parameter to the test.
 *
 * Return: a pointer to the current test parameter.
 *
 * This pointer should be cast to the expected parameter type for the executing
 * test.
 */
static inline const void* GetParam(void) {
    return _test_context.test_param;
}

#define ASSERT_EXPECT_LONG_TEST(op, is_hard_fail, fail_action, val1, val2, \
                                args...)                                   \
    ASSERT_EXPECT_TEST(op, , (op), (), is_hard_fail, fail_action,          \
                       __typeof__(val2), "%ld", (long), #op, val1, val2, args)

#define ASSERT_EXPECT_STR_TEST(func, is_hard_fail, fail_action, args...) \
    ASSERT_EXPECT_TEST(func, func, (, ), (), is_hard_fail, fail_action,  \
                       const char*, "\"%s\"", , args)

#define ASSERT_EXPECT_STRN_TEST(func, n, is_hard_fail, fail_action, args...) \
    ASSERT_EXPECT_TEST(func, func, (, ), (, n), is_hard_fail, fail_action,   \
                       const char*, "\"%s\"", , args)

#define EXPECT_TEST(op, args...) ASSERT_EXPECT_LONG_TEST(op, false, , args)
#define EXPECT_EQ(args...) EXPECT_TEST(==, args)
#define EXPECT_NE(args...) EXPECT_TEST(!=, args)
#define EXPECT_LT(args...) EXPECT_TEST(<, args)
#define EXPECT_LE(args...) EXPECT_TEST(<=, args)
#define EXPECT_GT(args...) EXPECT_TEST(>, args)
#define EXPECT_GE(args...) EXPECT_TEST(>=, args)
#define EXPECT_STR_TEST(func, args...) \
    ASSERT_EXPECT_STR_TEST(func, false, , args)
#define EXPECT_STREQ(args...) EXPECT_STR_TEST(!strcmp, "==", args)
#define EXPECT_STRNE(args...) EXPECT_STR_TEST(strcmp, "!=", args)
#define EXPECT_STRCASEEQ(args...) \
    EXPECT_STR_TEST(!strcasecmp, "== (ignoring case)", args)
#define EXPECT_STRCASENE(args...) \
    EXPECT_STR_TEST(strcasecmp, "!= (ignoring case)", args)
#define EXPECT_STRN_TEST(func, n, args...) \
    ASSERT_EXPECT_STRN_TEST(func, n, false, , args)
#define EXPECT_STREQN(val1, val2, n, args...) \
    EXPECT_STR_TEST(!strncmp, n, "==", val1, val2, args)
#define EXPECT_STRNEN(val1, val2, n, args...) \
    EXPECT_STR_TEST(strncmp, n, "!=", val1, val2, args)
#define EXPECT_STRCASEEQN(val1, val2, n, args...) \
    EXPECT_STR_TEST(!strncasecmp, n, "== (ignoring case)", val1, val2, args)
#define EXPECT_STRCASENEN(val1, val2, n, args...) \
    EXPECT_STR_TEST(strncasecmp, n, "!= (ignoring case)", val1, val2, args)

#define ASSERT_TEST(op, args...) \
    ASSERT_EXPECT_LONG_TEST(op, true, goto test_abort;, args)
#define ASSERT_EQ(args...) ASSERT_TEST(==, args)
#define ASSERT_NE(args...) ASSERT_TEST(!=, args)
#define ASSERT_LT(args...) ASSERT_TEST(<, args)
#define ASSERT_LE(args...) ASSERT_TEST(<=, args)
#define ASSERT_GT(args...) ASSERT_TEST(>, args)
#define ASSERT_GE(args...) ASSERT_TEST(>=, args)
#define ASSERT_STR_TEST(func, args...) \
    ASSERT_EXPECT_STR_TEST(func, true, goto test_abort;, args)
#define ASSERT_STREQ(args...) ASSERT_STR_TEST(!strcmp, "==", args)
#define ASSERT_STRNE(args...) ASSERT_STR_TEST(strcmp, "!=", args)
#define ASSERT_STRCASEEQ(args...) \
    ASSERT_STR_TEST(!strcasecmp, "== (ignoring case)", args)
#define ASSERT_STRCASENE(args...) \
    ASSERT_STR_TEST(strcasecmp, "!= (ignoring case)", args)
#define ASSERT_STRN_TEST(func, n, args...) \
    ASSERT_EXPECT_STRN_TEST(func, n, true, goto test_abort;, args)
#define ASSERT_STREQN(val1, val2, n, args...) \
    ASSERT_STRN_TEST(!strncmp, n, "==", val1, val2, args)
#define ASSERT_STRNEN(val1, val2, n, args...) \
    ASSERT_STRN_TEST(strncmp, n, "!=", val1, val2, args)
#define ASSERT_STRCASEEQN(val1, val2, n, args...) \
    ASSERT_STRN_TEST(!strncasecmp, n, "== (ignoring case)", val1, val2, args)
#define ASSERT_STRCASENEN(val1, val2, n, args...) \
    ASSERT_STRN_TEST(strncasecmp, n, "!= (ignoring case)", val1, val2, args)

__END_CDECLS
