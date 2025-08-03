/*
 * Copyright (c) 2008-2009 Travis Geiselbrecht
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
#ifndef __KERNEL_TIMER_H
#define __KERNEL_TIMER_H

#include <compiler.h>
#include <list.h>
#include <sys/types.h>

__BEGIN_CDECLS;

void timer_init(void);

struct timer;
typedef enum handler_return (*timer_callback)(struct timer *, lk_time_ns_t now,
                                              void *arg);

#define TIMER_MAGIC (0x74696D72)  //'timr'

typedef struct timer {
    int magic;
    uint cpu;
    bool running;
    struct list_node node;

    lk_time_ns_t scheduled_time;
    lk_time_ns_t periodic_time;

    timer_callback callback;
    void *arg;
} timer_t;

#define TIMER_INITIAL_VALUE(t) \
{ \
    .magic = TIMER_MAGIC, \
    .cpu = ~0U, \
    .running = false, \
    .node = LIST_INITIAL_CLEARED_VALUE, \
    .scheduled_time = 0, \
    .periodic_time = 0, \
    .callback = NULL, \
    .arg = NULL, \
}

/* Rules for Timers:
 * - Timer callbacks occur from interrupt context
 * - Timers may be programmed or canceled from interrupt or thread context.
 *   Timers canceled from interrupt context must be canceled from the same CPU
 *   that the callback runs on. Timers canceled from thread context should use
 *   timer_cancel_sync instead of timer_cancel to make sure the timer callback
 *   is not still running when the call returns.
 * - Timers may be canceled or reprogrammed from within their callback
 * - Timers currently are dispatched from a 10ms periodic tick
*/
void timer_initialize(timer_t *);
void timer_set_oneshot_ns(timer_t *, lk_time_ns_t delay, timer_callback,
                          void *arg);
void timer_set_periodic_ns(timer_t *, lk_time_ns_t period, timer_callback,
                           void *arg);

/**
 * timer_cancel_etc - Cancel timer and optionally wait for the callback
 * @timer:  Timer to cancel.
 * @wait:   If %true, wait for callback.
 */
void timer_cancel_etc(timer_t *timer, bool wait);

/**
 * timer_cancel - Cancel timer without waiting for callback to finish
 * @timer:  Timer to cancel.
 *
 * Can be called from interrupt context, including the timer callback itself.
 * It is not safe to free the timer immediately this call returns as the timer
 * could still be running.
 */
static void timer_cancel(timer_t *timer) {
    timer_cancel_etc(timer, false);
}

/**
 * timer_cancel_sync - Cancel timer and wait for callback to finish
 * @timer:  Timer to cancel.
 *
 * Can not be called from interrupt context.
 */
static void timer_cancel_sync(timer_t *timer) {
    timer_cancel_etc(timer, true);
}

__END_CDECLS;

#endif

