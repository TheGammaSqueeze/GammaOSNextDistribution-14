/*
 * Copyright (c) 2019, Google, Inc. All rights reserved
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
#include <assert.h>
#include <err.h>
#include <kernel/timer.h>
#include <lk/init.h>
#include <platform.h>
#include <trace.h>

#include <lib/trusty/event.h>
#include <lib/trusty/uirq.h>
#include <lib/trusty/uuid.h>

#define LOCAL_TRACE 0

#define MS_TO_NS(ms) ((ms)*1000ULL * 1000ULL)

struct timer_uirq {
    struct uirq uirq;
    struct timer tm;
    struct handle* handle;
    lk_time_ns_t delay;
};

static const struct uuid _test_app_uuid[] = {
        /* UIRQ unittest app UUID : {e20af937-a4d0-4b95-b852-95ef21333cd1} */
        {0xe20af937,
         0xa4d0,
         0x4b95,
         {0xb8, 0x52, 0x95, 0xef, 0x21, 0x33, 0x3c, 0xd1}},
};

static struct timer_uirq _tm_uirqs[] = {
        {
                .uirq = UIRQ_INITIALIZER("test-uirq-10ms",
                                         &_test_app_uuid[0],
                                         1,
                                         0),
                .tm = TIMER_INITIAL_VALUE(.tm),
                .delay = MS_TO_NS(10),
        },
        {
                .uirq = UIRQ_INITIALIZER("test-uirq-no-access",
                                         &zero_uuid,
                                         1,
                                         0),
                .tm = TIMER_INITIAL_VALUE(.tm),
                .delay = MS_TO_NS(50),
        },
};

static lk_time_ns_t ts_start;
static lk_time_ns_t ts_notified;
static lk_time_ns_t ts_handled;
static lk_time_ns_t ttl_elapsed1;
static lk_time_ns_t ttl_elapsed2;
static unsigned int ttl_count;

static enum handler_return test_tm_uirq_callback(struct timer* t,
                                                 lk_time_ns_t now,
                                                 void* arg) {
    struct timer_uirq* u = arg;
    ts_start = current_time_ns();
    event_source_signal(u->handle);
    ts_notified = current_time_ns();
    return INT_RESCHEDULE;
};

static void tm_uirq_mask(const void* arg) {}

static void tm_uirq_unmask(const void* arg) {
    ts_handled = current_time_ns();
    if (ts_start) {
        ttl_elapsed1 += ts_notified - ts_start;
        ttl_elapsed2 += ts_handled - ts_start;
        ttl_count++;
        ts_start = 0;
    }
    struct timer_uirq* u = (struct timer_uirq*)arg;
    timer_set_oneshot_ns(&u->tm, u->delay, test_tm_uirq_callback, u);
}

static void tm_uirq_open(const void* arg) {
    ttl_count = 0;
    ts_start = 0;
    ts_notified = 0;
    ts_handled = 0;
    ttl_elapsed1 = 0;
    ttl_elapsed2 = 0;

    struct timer_uirq* u = (struct timer_uirq*)arg;
    timer_set_oneshot_ns(&u->tm, u->delay, test_tm_uirq_callback, u);
}

static void tm_uirq_close(const void* arg) {
    struct timer_uirq* u = (struct timer_uirq*)arg;
    timer_cancel_sync(&u->tm);

    if (ttl_count) {
        LTRACEF("cnt=%u: %lld %lld\n", ttl_count, ttl_elapsed1, ttl_elapsed2);
    }
}

static const struct event_source_ops _tm_evt_ops = {
        .open = tm_uirq_open,
        .mask = tm_uirq_mask,
        .unmask = tm_uirq_unmask,
        .close = tm_uirq_close,
};

static void uirq_test_init(uint level) {
    int rc;

    /* register uirq interrupts */
    for (uint i = 0; i < countof(_tm_uirqs); i++) {
        struct timer_uirq* u = &_tm_uirqs[i];
        rc = uirq_register_sw_irq(&u->uirq, &_tm_evt_ops, u, &u->handle);
        if (rc < 0) {
            TRACEF("Failed (%d) to initialize test uirq %s\n", rc,
                   u->uirq.name);
        }
    }
}

LK_INIT_HOOK(uirq_test, uirq_test_init, LK_INIT_LEVEL_APPS - 2);
