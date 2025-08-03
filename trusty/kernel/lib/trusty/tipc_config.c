/*
 * Copyright (c) 2014-2015, Google, Inc. All rights reserved
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
#include <trace.h>

#include <lib/trusty/tipc_virtio_dev.h>
#include <lk/init.h>

#include "trusty_virtio.h"

/* Default TIPC device (/dev/trusty-ipc-dev0) */
DECLARE_TIPC_DEVICE_DESCR(_descr0, 0, 32, 32, "dev0");

static status_t tipc_init(struct trusty_virtio_bus* vb) {
    status_t res;

    res = create_tipc_device(vb, &_descr0, sizeof(_descr0), &zero_uuid, NULL);
    if (res != NO_ERROR) {
        TRACEF("WARNING: failed (%d) to register tipc device\n", res);
    }
    return res;
}

static void register_tipc_init(uint level) {
    static struct trusty_virtio_bus_notifier vb_notifier = {
            .on_create = tipc_init,
    };
    trusty_virtio_register_bus_notifier(&vb_notifier);
}

LK_INIT_HOOK_FLAGS(register_tipc_init,
                   register_tipc_init,
                   LK_INIT_LEVEL_APPS - 2,
                   LK_INIT_FLAG_PRIMARY_CPU);
