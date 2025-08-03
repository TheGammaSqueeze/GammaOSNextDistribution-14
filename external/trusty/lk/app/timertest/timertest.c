/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

#include <kernel/timer.h>
#include <lib/unittest/unittest.h>
#include <platform.h>
#include <stdatomic.h>

#define US2NS(us) ((us) * 1000LL)
#define MS2NS(ms) (US2NS(ms) * 1000LL)
#define S2NS(s) (MS2NS(s) * 1000LL)

/* Expect better than 1us timer resolution. */
#if GEM5
// In gem5 simulation CNTPCT register is updated with frequency less than
// 1MHz, so adjust target period for this test
#define TIMER_TEST_MAX_CLOCK_PERIOD (1200)
#elif defined(APP_TIMERTEST_MAX_CLOCK_PERIOD)
#define TIMER_TEST_MAX_CLOCK_PERIOD (APP_TIMERTEST_MAX_CLOCK_PERIOD)
#else
#define TIMER_TEST_MAX_CLOCK_PERIOD (900)
#endif

/*
 * Expect much better than 1ms timer interrupt latency, but qemu currently has
 * more than 100us latency in the best case, and Linux adds several milliseconds
 * to this in the worst case.
 */
#define TIMER_TEST_MAX_TIMER_LATENCY (MS2NS(10))

/* Periodic timers don't currently avoid drifting when the interrupt is late */
#define TIMER_TEST_MAX_TIMER_DRIFT TIMER_TEST_MAX_TIMER_LATENCY

#define TIMER_TEST_RETRY_COUNT (10)

static int64_t TimerTestGetTimeNs(void) {
    return current_time_ns();
}

static void TimerTestBusyWait(int64_t delay) {
    int64_t end = TimerTestGetTimeNs() + delay;
    while (TimerTestGetTimeNs() < end) {
    }
}

struct TimerTestTimer {
    struct timer timer;
    int64_t delta;
    int64_t before_start_time;
    int64_t after_start_time;
    size_t target_trigger_count;
    atomic_size_t trigger_count;
    int64_t trigger_time_passed;
    int64_t handler_run_time;
    void *arg_passed;
};

#define TIMER_TEST_TIMER_INITIAL_VALUE(t) { \
        TIMER_INITIAL_VALUE(&t->timer), 0, 0, 0, 0, 0, 0, 0, NULL \
    }

static struct TimerTestTimer *ToTimerTestTimer(struct timer *timer) {
    return containerof(timer, struct TimerTestTimer, timer);
}

/**
 * TimerTestTimerCallback - Timer callback function.
 * @timer:  Timer object.
 * @now:    Current time passed from lk timer code.
 * @arg:    Argument passed to timer_set*.
 *
 * Common timer callback function used by tests to record how many times it was
 * called, when it was last called and what arguments were passed. Stops
 * periodic timer after a specified trigger count.
 *
 * Return: INT_NO_RESCHEDULE to tell the caller that the scheduler does not need
 *         to run.
 */
static enum handler_return TimerTestTimerCallback(struct timer *timer,
                                                  lk_time_ns_t now,
                                                  void *arg) {
    struct TimerTestTimer *t = ToTimerTestTimer(timer);
    size_t trigger_count;

    t->trigger_time_passed = now;
    t->handler_run_time = TimerTestGetTimeNs();
    t->arg_passed = arg;
    trigger_count = atomic_fetch_add_explicit(&t->trigger_count, 1,
                                              memory_order_release) + 1;
    if (t->timer.periodic_time &&
        trigger_count == t->target_trigger_count) {
        timer_cancel(&t->timer);
    }
    return INT_NO_RESCHEDULE;
}

/**
 * TimerTestTimerStart - Start test timer.
 * @t:      Test timer object.
 * @delta:  Delay in nanoseconds until timer callback should be called and
 *          interval between timer callback calls if @count is greater than one.
 * @count:  Number of times to call the timer callback.
 */
static void TimerTestTimerStart(struct TimerTestTimer *t, int64_t delta,
                                size_t count) {
    lk_time_ns_t lk_time = delta;
    t->delta = delta;
    t->target_trigger_count = count;
    atomic_store(&t->trigger_count, 0);
    t->before_start_time = TimerTestGetTimeNs();
    if (count > 1) {
        timer_set_periodic_ns(&t->timer, lk_time, TimerTestTimerCallback, t);
    } else {
        timer_set_oneshot_ns(&t->timer, lk_time, TimerTestTimerCallback, t);
    }
    t->after_start_time = TimerTestGetTimeNs();
}

/**
 * TimerTestTimerCheck - Check that timer callback was called
 * @t:      Test timer object.
 * @retry:  If non-zero, return early if timer callback ran late.
 *
 * Return: @retry if timer callback ran late and test should run again, 0
 *         otherwise.
 */
static size_t TimerTestTimerCheck(struct TimerTestTimer *t, size_t retry) {
    size_t trigger_count;
    int64_t delta = t->delta * t->target_trigger_count;
    int64_t min_delta = delta;
    int64_t max_delta = delta + TIMER_TEST_MAX_TIMER_LATENCY +
                        TIMER_TEST_MAX_TIMER_DRIFT *
                        (t->target_trigger_count - 1);

    trigger_count = atomic_load_explicit(&t->trigger_count,
                                         memory_order_acquire);

    EXPECT_EQ(trigger_count, t->target_trigger_count);
    timer_cancel_sync(&t->timer);
    if (trigger_count != t->target_trigger_count) {
        return 0;
    }

    EXPECT_EQ(t->arg_passed, t);

    if (retry && MAX(t->trigger_time_passed, t->handler_run_time) -
                 t->after_start_time >= max_delta) {
        unittest_printf("    %lld/%lld > %lld, retry\n",
                        t->trigger_time_passed - t->after_start_time,
                        t->handler_run_time - t->after_start_time, max_delta);
        return retry;
    }

    EXPECT_GE(t->trigger_time_passed - t->before_start_time, min_delta);
    EXPECT_LT(t->trigger_time_passed - t->after_start_time, max_delta);
    EXPECT_GE(t->handler_run_time - t->before_start_time, min_delta);
    EXPECT_LT(t->handler_run_time - t->after_start_time, max_delta);

    return 0;
}

/**
 * TimerTestTimerPoll - Busy wait for timer callback to run.
 * @t:          Test timer object.
 * @timeout:    Timeout in nanoseconds to abort busy-wait.
 *
 * Return: %true if timer callback ran the expected number of times, %false
 *         otherwise.
 */
static bool TimerTestTimerPoll(struct TimerTestTimer *t, int64_t timeout) {
    size_t trigger_count;
    int64_t end_time = TimerTestGetTimeNs() + timeout;

    do {
        trigger_count = atomic_load_explicit(&t->trigger_count,
                                             memory_order_acquire);
    } while (trigger_count < t->target_trigger_count &&
             TimerTestGetTimeNs() < end_time);

    return trigger_count == t->target_trigger_count;
}

/* Test high resolution api to read timer */
TEST(TimerTest, GetTime) {
    int64_t t1;
    int64_t t2;
    size_t i;
    const int timeout = 1000;
    const int64_t max_delta = TIMER_TEST_MAX_CLOCK_PERIOD;

    t2 = TimerTestGetTimeNs();
    for (i = 0; i < timeout; i++) {
        t1 = t2;
        t2 =  TimerTestGetTimeNs();
        if (t2 != t1 && t2 - t1 <= max_delta) {
            break;
        }
    }
    EXPECT_GT(t2 - t1, 0);
    EXPECT_LE(t2 - t1, max_delta);
}

/* Test one shot timer api */
TEST(TimerTest, TimerSetOneShot) {
    struct TimerTestTimer t = TIMER_TEST_TIMER_INITIAL_VALUE(&t);
    int retry = TIMER_TEST_RETRY_COUNT;
    do {
        TimerTestTimerStart(&t, MS2NS(1), 1);
        TimerTestTimerPoll(&t, S2NS(10));
        retry = TimerTestTimerCheck(&t, retry - 1);
    } while(retry);
}

/* Test periodic timer api */
TEST(TimerTest, TimerSetPeriodic) {
    struct TimerTestTimer t = TIMER_TEST_TIMER_INITIAL_VALUE(&timer);
    int retry = TIMER_TEST_RETRY_COUNT;
    do {
        TimerTestTimerStart(&t, MS2NS(1), 100);
        TimerTestTimerPoll(&t, S2NS(10));
        retry = TimerTestTimerCheck(&t, retry - 1);
    } while(retry);
}

/* Test multiple timers (which could cause early wake up in old timer code) */
TEST(TimerTest, TimerSetOneShotMultipleEvenlySpaced) {
    size_t i;
    static struct TimerTestTimer tb[] = {
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[0]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[1]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[2]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[3]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[4]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[5]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[6]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[7]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[8]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&tb[9]),
    };
    static struct TimerTestTimer ts[] = {
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[0]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[1]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[2]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[3]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[4]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[5]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[6]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[7]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[8]),
        TIMER_TEST_TIMER_INITIAL_VALUE(&ts[9]),
    };
    int retry = TIMER_TEST_RETRY_COUNT;
    int next_retry;
    do {
        for (i = 0; i < countof(tb); i++) {
            TimerTestTimerStart(&tb[i], MS2NS(1), 1);
        }
        for (i = 0; i < countof(ts); i++) {
            TimerTestTimerStart(&ts[i], MS2NS(1), 1);
            TimerTestBusyWait(MS2NS(1) / countof(ts));
            timer_cancel_sync(&tb[i].timer);
        }
        TimerTestTimerPoll(&ts[countof(ts) - 1], S2NS(10));
        for (i = 0; i < countof(ts); i++) {
            /* thread may migrate to another cpu */
            TimerTestTimerPoll(&ts[i], S2NS(1));
        }
        next_retry = retry - 1;
        retry = 0;
        for (i = 0; i < countof(ts); i++) {
            int tmp_retry = TimerTestTimerCheck(&ts[i], next_retry);
            if (tmp_retry) {
                retry = tmp_retry;
            }
        }
    } while(retry);
}

/*
 * Test timer_cancel api. Call after a delay from alternating CPUs to try to
 * cancel it while the timer callback is running.
 */
TEST(TimerTest, TimerCancel) {
    struct TimerTestTimer t = TIMER_TEST_TIMER_INITIAL_VALUE(&t);
    struct wait_queue wq = WAIT_QUEUE_INITIAL_VALUE(wq);
    bool triggered;
    int64_t wait_time = MS2NS(2);
    int saved_pinned_cpu = thread_pinned_cpu(get_current_thread());
    EXPECT_EQ(saved_pinned_cpu, -1);
    for (int i = 0; i < 1000; i++) {
        TimerTestTimerStart(&t, MS2NS(2), 1);
        thread_set_pinned_cpu(get_current_thread(),
                              (arch_curr_cpu_num() + 1) % 2);
        THREAD_LOCK(state);
        wait_queue_block(&wq, 1);
        THREAD_UNLOCK(state);
        triggered = TimerTestTimerPoll(&t, wait_time);
        if (!triggered) {
            wait_time += US2NS(10);
        } else {
            wait_time -= US2NS(10);
        }
        timer_cancel_sync(&t.timer);
    }
    thread_set_pinned_cpu(get_current_thread(), saved_pinned_cpu);
}

PORT_TEST(TimerTest, "com.android.kernel.timertest");
