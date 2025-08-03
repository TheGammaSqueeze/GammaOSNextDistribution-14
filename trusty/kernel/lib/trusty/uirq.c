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
#include <platform/interrupts.h>
#include <trace.h>

#define LOCAL_TRACE 0

#include <lib/trusty/event.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/uirq.h>

static void uirq_hw_mask(const void* vector) {
    int ret = mask_interrupt((unsigned int)(uintptr_t)vector);
    ASSERT(ret == NO_ERROR);
}

static void uirq_hw_unmask(const void* vector) {
    int ret = unmask_interrupt((unsigned int)(uintptr_t)vector);
    ASSERT(ret == NO_ERROR);
}

struct event_source_ops platform_uirq_ops = {
        .open = uirq_hw_unmask, /* unmask for open */
        .mask = uirq_hw_mask,
        .unmask = uirq_hw_unmask,
        .close = uirq_hw_mask, /* mask for close  */
};

static enum handler_return plat_uirq_handler(void* arg) {
    event_source_signal((struct handle*)arg);
    return INT_RESCHEDULE;
}

int uirq_register_sw_irq(const struct uirq* uirq,
                         const struct event_source_ops* ops,
                         const void* ops_arg,
                         struct handle** ph) {
    int rc;
    struct handle* h;

    ASSERT(uirq);
    ASSERT(ph);

    LTRACEF("registering uirq '%s'\n", uirq->name);

    rc = event_source_create(uirq->name, ops, ops_arg, uirq->uuids,
                             uirq->uuids_num, uirq->cfg_flags, &h);
    if (rc < 0) {
        LTRACEF("failed (%d) to create uirq event '%s'\n", rc, uirq->name);
        return rc;
    }

    rc = event_source_publish(h);
    if (rc < 0) {
        LTRACEF("failed (%d) to publish uirq event '%s'\n", rc, uirq->name);
        handle_decref(h);
        return rc;
    }

    *ph = h;
    return NO_ERROR;
}

int uirq_register_hw_irq(unsigned int vector,
                         const struct uirq* uirq,
                         struct handle** ph) {
    int rc;
    struct handle* h;

    ASSERT(uirq);
    ASSERT(ph);

    LTRACEF("registering uirq '%s'\n", uirq->name);

    rc = mask_interrupt(vector);
    if (rc != NO_ERROR) {
        LTRACEF("failed (%d) to mask irq source '%s'\n", rc, uirq->name);
        return rc;
    }

    rc = event_source_create(uirq->name, &platform_uirq_ops,
                             (const void*)(uintptr_t)vector, uirq->uuids,
                             uirq->uuids_num, uirq->cfg_flags, &h);
    if (rc < 0) {
        LTRACEF("failed (%d) to create uirq event '%s'\n", rc, uirq->name);
        return rc;
    }

    register_int_handler(vector, plat_uirq_handler, h);

    rc = event_source_publish(h);
    if (rc < 0) {
        LTRACEF("failed (%d) to publish uirq event '%s'\n", rc, uirq->name);
        register_int_handler(vector, NULL, NULL);
        handle_decref(h);
        return rc;
    }

    *ph = h;
    return NO_ERROR;
}
