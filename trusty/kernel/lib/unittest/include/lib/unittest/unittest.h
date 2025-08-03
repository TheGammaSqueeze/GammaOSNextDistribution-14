/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <lib/trusty/ipc.h>
#include <lk/compiler.h>
#include <stdbool.h>

__BEGIN_CDECLS

/**
 * struct unittest - struct representing a kernel unit-test.
 * @port_name:  Port name.
 * @run_test:   Function to call when a client connects to @port_name.
 * @_href:      Private data used by library.
 */
struct unittest {
    const char* port_name;
    bool (*run_test)(struct unittest* test);
    struct handle_ref _href;
};

int unittest_printf(const char* fmt, ...);
int unittest_add(struct unittest* test);

#define trusty_unittest_printf(args...) \
    do {                                \
        unittest_printf(args);          \
    } while (0)

#include <lk/trusty_unittest.h>

#include <lk/init.h>
#include <platform.h>

/*
 * This function returns a time in nanoseconds based on hardware counters
 * it is expected to:
 *  - Be non-wrapping or have very long (years) roll-over period
 *  - Have a resolution below 100ns
 */
uint64_t get_current_time_ns(void);

#define PORT_TEST_COMMON(suite_name, port_name_string, suite_name_string) \
    static bool run_##suite_name(struct unittest* test) {                 \
        return RUN_ALL_SUITE_TESTS(suite_name_string);                    \
    }                                                                     \
                                                                          \
    static void suite_name##_init(uint level) {                           \
        static struct unittest test = {                                   \
                .port_name = port_name_string,                            \
                .run_test = run_##suite_name,                             \
        };                                                                \
        unittest_add(&test);                                              \
    }                                                                     \
                                                                          \
    LK_INIT_HOOK(suite_name, suite_name##_init, LK_INIT_LEVEL_APPS);

#define PORT_TEST(suite_name, port_name_string) \
    PORT_TEST_COMMON(suite_name, port_name_string, NULL)

#define PORT_TEST_SUITE(suite_name, port_name_string) \
    PORT_TEST_COMMON(suite_name, port_name_string, #suite_name)

__END_CDECLS
