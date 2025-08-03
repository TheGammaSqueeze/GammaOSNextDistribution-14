/*
 * Copyright (c) 2019, Google Inc. All rights reserved
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

#include <err.h>
#include <kernel/timer.h>
#include <lib/dpc.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>

#define TEST_REQUEUE_CNT 10

struct dpc_test_ctx {
    struct dpc work;
    uint32_t count;
    uint32_t in_atomic_requeue_cnt;
    uint32_t in_thread_requeue_cnt;
    struct event evt;
    struct timer tmr;
};

static enum handler_return dpc_test_timer_callback(struct timer* t,
                                                   lk_time_ns_t now,
                                                   void* arg) {
    struct dpc_test_ctx* ctx = arg;

    ctx->in_atomic_requeue_cnt++;
    dpc_enqueue_work(NULL, &ctx->work, false);
    return INT_RESCHEDULE;
}

static void dpc_test_callback(struct dpc* work) {
    struct dpc_test_ctx* ctx = containerof(work, struct dpc_test_ctx, work);

    if (ctx->count > TEST_REQUEUE_CNT / 2) {
        /* requeue work from irq context */
        ctx->count--;
        timer_set_oneshot_ns(&ctx->tmr, 10 * 1000 * 1000,
                             dpc_test_timer_callback, ctx);
    } else if (ctx->count) {
        /* requeue work from thread context */
        ctx->count--;
        ctx->in_thread_requeue_cnt++;
        dpc_enqueue_work(NULL, &ctx->work, false);
    } else {
        /* we are done here */
        event_signal(&ctx->evt, true);
    }
}

TEST(dpctest, test1) {
    status_t rc;
    struct dpc_test_ctx test_ctx;

    /* Init test context */
    test_ctx.count = TEST_REQUEUE_CNT;
    test_ctx.in_atomic_requeue_cnt = 0;
    test_ctx.in_thread_requeue_cnt = 0;
    timer_initialize(&test_ctx.tmr);
    event_init(&test_ctx.evt, false, EVENT_FLAG_AUTOUNSIGNAL);
    dpc_work_init(&test_ctx.work, dpc_test_callback, 0);

    /* init dpc work and queue it on default queue */
    dpc_enqueue_work(NULL, &test_ctx.work, false);

    /* wait for complete */
    rc = event_wait_timeout(&test_ctx.evt, 1000);

    /* check results */
    EXPECT_EQ(NO_ERROR, rc);
    EXPECT_EQ(0, test_ctx.count);
    EXPECT_EQ(TEST_REQUEUE_CNT / 2, test_ctx.in_atomic_requeue_cnt);
    EXPECT_EQ(TEST_REQUEUE_CNT / 2, test_ctx.in_thread_requeue_cnt);
}

PORT_TEST(dpctest, "com.android.kernel.dpc-unittest");
