/*
 * Copyright (C) 2012-2014 The Android Open Source Project
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

#include <inttypes.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>

#include <lib/unittest/unittest.h>
#include <trusty_unittest.h>

#define TLOG_TAG "timertest"

static void __attribute__((noinline)) nop(void) {
    __UNUSED static int i;
    i++;
}

struct timer_unittest {
    struct unittest unittest;
    bool loop;
};

#define TIMER_TEST_NOP_LOOP_COUNT (100000000)
#define ONE_MS (1000 * 1000ULL)
#define TIMER_TEST_MS_SLEEP_LOOP_COUNT (1000)
#define ONE_S (1000 * ONE_MS)

static void check_timestamps(int64_t t1,
                             int64_t delta_min,
                             int64_t delta_max,
                             const char* name) {
    int64_t delta;
    int64_t t2 = t1 - 1;

    trusty_gettime(0, &t2);
    delta = t2 - t1;

    EXPECT_EQ(false, (delta < delta_min || delta > delta_max),
              "bad timestamp after %s: t1 %" PRId64 ", t2 %" PRId64
              ", delta %" PRId64 ", min %" PRId64 " max %" PRId64 "\n",
              name, t1, t2, t2 - t1, delta_min, delta_max);
}

TEST(TimerTest, BusyLoop) {
    int i;
    int64_t ts = 0;

    trusty_gettime(0, &ts);
    for (i = 0; i < TIMER_TEST_NOP_LOOP_COUNT; i++)
        nop();
    check_timestamps(ts, TIMER_TEST_NOP_LOOP_COUNT / 100,
                     TIMER_TEST_NOP_LOOP_COUNT * 10000ULL, "nop loop");
}

TEST(TimerTest, NanoSleepOneMilliSecond) {
    int i;
    int64_t ts = 0;

    trusty_gettime(0, &ts);
    for (i = 0; i < TIMER_TEST_MS_SLEEP_LOOP_COUNT; i++)
        trusty_nanosleep(0, 0, ONE_MS);
    check_timestamps(ts, TIMER_TEST_MS_SLEEP_LOOP_COUNT * ONE_MS,
                     TIMER_TEST_MS_SLEEP_LOOP_COUNT * ONE_MS * 10, "ms loop");
}

TEST(TimerTest, NanoSleepTenSeconds) {
    int64_t ts = 0;

    trusty_gettime(0, &ts);
    trusty_nanosleep(0, 0, 10ULL * ONE_S);
    check_timestamps(ts, ONE_S * 10, ONE_S * 11, "10s sleep");
}

static bool timer_test(struct unittest* test) {
    struct timer_unittest* timer_test =
            containerof(test, struct timer_unittest, unittest);
    bool passed;

    do {
        passed = RUN_ALL_TESTS();
    } while (timer_test->loop);

    return passed;
}

#define PORT_BASE "com.android.timer-unittest"

int main(void) {
    static struct timer_unittest timer_unittests[2] = {
            {
                    .unittest =
                            {
                                    .port_name = PORT_BASE,
                                    .run_test = timer_test,
                            },
                    .loop = false,
            },
            {
                    .unittest =
                            {
                                    .port_name = PORT_BASE ".loop",
                                    .run_test = timer_test,
                            },
                    .loop = true,
            },
    };
    static struct unittest* unittests[countof(timer_unittests)];

    for (size_t i = 0; i < countof(timer_unittests); i++) {
        unittests[i] = &timer_unittests[i].unittest;
    }

    return unittest_main(unittests, countof(unittests));
}
