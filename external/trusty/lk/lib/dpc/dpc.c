/*
 * Copyright (c) 2008 Travis Geiselbrecht
 * Copyright (c) 2019 Google, Inc.
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
#include <debug.h>
#include <kernel/event.h>
#include <kernel/spinlock.h>
#include <kernel/thread.h>
#include <lib/dpc.h>
#include <lk/init.h>
#include <lk/list.h>
#include <malloc.h>
#include <stddef.h>
#include <trace.h>
#include <uapi/err.h>

#define LOCAL_TRACE 0

struct dpc_queue {
    struct list_node list;
    struct event event;
    spin_lock_t lock;
    struct thread* thread;
};

static struct dpc_queue default_queue;

static int dpc_thread_routine(void* arg);

void dpc_work_init(struct dpc* work, dpc_callback cb, uint32_t flags) {
    ASSERT(work);

    list_clear_node(&work->node);
    work->cb = cb;
    work->q = NULL;
}

int dpc_enqueue_work(struct dpc_queue* q, struct dpc* work, bool resched) {
    spin_lock_saved_state_t state;

    ASSERT(work);
    ASSERT(work->cb);

    if (!q) {
        q = &default_queue;
    }

    spin_lock_irqsave(&q->lock, state);
    ASSERT(!work->q || (work->q == q));
    if (!list_in_list(&work->node)) {
        list_add_tail(&q->list, &work->node);
        work->q = q;
    }
    spin_unlock_irqrestore(&q->lock, state);
    event_signal(&q->event, resched);
    return 0;
}

static int dpc_thread_routine(void* arg) {
    struct dpc* work;
    struct dpc_queue* q = arg;
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(q);

    for (;;) {
        event_wait(&q->event);
        do {
            spin_lock_irqsave(&q->lock, state);
            work = list_remove_head_type(&q->list, struct dpc, node);
            spin_unlock_irqrestore(&q->lock, state);

            if (work) {
                LTRACEF("dpc calling %p\n", work->cb);
                work->cb(work);
            }
        } while (work);
    }

    return 0;
}

status_t dpc_queue_start(struct dpc_queue* q,
                         const char* name,
                         int thread_priority,
                         size_t thread_stack_size) {
    DEBUG_ASSERT(q);
    DEBUG_ASSERT(!q->thread);

    /* Initiliaze queue */
    spin_lock_init(&q->lock);
    list_initialize(&q->list);
    event_init(&q->event, false, EVENT_FLAG_AUTOUNSIGNAL);

    /* create thread */
    q->thread = thread_create(name, dpc_thread_routine, q, thread_priority,
                              thread_stack_size);
    if (!q->thread)
        return ERR_NO_MEMORY;

    /* start thread */
    thread_detach_and_resume(q->thread);
    return 0;
}

static void dpc_init(uint level) {
    status_t rc;

    /* init and start default DPC queue */
    rc = dpc_queue_start(&default_queue, "dpc", DPC_PRIORITY,
                         DEFAULT_STACK_SIZE);
    if (rc != NO_ERROR) {
        panic("failed to start default dpc queue\n");
    }
}

LK_INIT_HOOK(libdpc, &dpc_init, LK_INIT_LEVEL_THREADING);
