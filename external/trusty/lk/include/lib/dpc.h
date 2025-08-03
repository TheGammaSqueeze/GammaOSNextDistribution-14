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
#pragma once

#include <lk/compiler.h>
#include <lk/list.h>
#include <kernel/thread.h>
#include <sys/types.h>

__BEGIN_CDECLS

struct dpc;

/**
 * typedef dpc_callback - DPC callback routine
 * @work: pointer to &struct dpc item for which this callback routine
 * is invoked.
 *
 * It is expected that &struct dpc item specified by @work parameter
 * is embedded into a caller specific structure and the caller will be
 * using the containerof macro to recover the outer structure.
 *
 * Return: none
 */
typedef void (*dpc_callback)(struct dpc* work);

/**
 * struct dpc_queue - opaque DPC queue tracking structure
 */
struct dpc_queue;

/**
 * struct dpc - DPC work item tracking structure
 * @node: tracking list node
 * @cb:  pointer to callback routine to invoke
 * @q: pointer to DPC queue if DPC item is queued
 */
struct dpc {
    /* private: internal use only */
    struct list_node node;
    dpc_callback cb;
    struct dpc_queue* q;
};

/**
 * dpc_work_init() - initialize specified DPC work item
 * @work: pointer to &struct dpc to initialize
 * @cb: callback to invoke
 * @flags: reserved must be 0
 *
 * Return: none
 */
void dpc_work_init(struct dpc* work, dpc_callback cb, uint32_t flags);

/**
 * dpc_enqueue_work(): enqueue DPC work to run on specified DPC queue
 * @q: DPC queue to run DPC work specified by @work parameter. If @q is NULL
 * the work will be enqueued in the default DPC queue.
 * @work: DPC work to enqueue on DPC queue specified by @q parameter
 * @resched: directly passed to underlying event_signal() call. (See the
 * description of event_signal() call for more details).
 *
 * Note 1: It is guaranteed that after each invocation of dpc_enqueue_work()
 * routine the corresponding DPC work item will be processed at least once.
 *
 * Note 2: A DPC work item may be re-enqueued the same DPC work queue it is
 * already in. This is a no op.
 *
 * Note 3: The work item is removed from the DPC queue before the callback is
 * invoked. It is safe to re-enqueue DPC work item inside its own callback.
 *
 * Note 4: If DPC work item is enqueued again after it has been removed from
 * the queue but before callback is invoked or while callback is running it is
 * guaranteed that DPC work item will be processed again in the future.
 *
 * Note 5: An initialized DPC work item can only be enqueued in one queue.
 * An item needs to be initialized again in order to be enqueued in another
 * queue.
 *
 * Note 6: The only time when it is safe to free or reinitialize a DPC work
 * item is when it is not queued, for example, from DPC callback itself.
 *
 * Note 7: The @resched parameter must be false if invoked from interrupt
 * context.
 *
 * return value: 0 on success
 */
int dpc_enqueue_work(struct dpc_queue* q, struct dpc* work, bool resched);

/**
 * dpc_queue_start(): initialize and start DPC queue
 * @name: DPC queue name
 * @thread_priority: a priority of DPC queue handling thread
 * @thread_stack_size: stack size of DPC queue handling thread
 * DEFAULT_STACK_SIZE is a reasonable default value here.
 *
 * Return: NO_ERROR on success, a negative error code otherwise
 */
status_t dpc_queue_start(struct dpc_queue* q,
                         const char* name,
                         int thread_priority,
                         size_t thread_stack_size);

__END_CDECLS
