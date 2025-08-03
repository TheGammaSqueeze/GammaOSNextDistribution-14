/*
 * Copyright (c) 2023, Google Inc. All rights reserved
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

#include <lib/trusty/ipc.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdio.h>
#include <string.h>

TEST(list_ports, lists) {
    struct ipc_port* port_list;
    size_t len = ipc_get_port_list(&port_list);

    trusty_unittest_printf("|%.64s|%.12s|%.12s|%.12s|%.12s|%.12s|\n", "NAME",
                           "TA_CONNECT", "NS_CONNECT", "STATUS", "NB_BUFFER",
                           "BUFFER_SZ");
    for (size_t port_idx = 0; port_idx < len; ++port_idx) {
        struct ipc_port* port = port_list + port_idx;

        trusty_unittest_printf("|%.64s|", port->path);

        if (port->flags & IPC_PORT_ALLOW_TA_CONNECT) {
            trusty_unittest_printf("%.12s|", "TRUE");
        } else {
            trusty_unittest_printf("%.12s|", "FALSE");
        }

        if (port->flags & IPC_PORT_ALLOW_NS_CONNECT) {
            trusty_unittest_printf("%.12s|", "TRUE");
        } else {
            trusty_unittest_printf("%.12s|", "FALSE");
        }

        /* Port State */
        if (port->state == IPC_PORT_STATE_INVALID) {
            trusty_unittest_printf("%.12s|", "INVALID");
        } else if (port->state == IPC_PORT_STATE_LISTENING) {
            trusty_unittest_printf("%.12s|", "LISTENING");
        } else {
            trusty_unittest_printf("%.12s|", "UNKNOWN");
        }

        trusty_unittest_printf("%.12u|", port->num_recv_bufs);
        trusty_unittest_printf("%.12zu|", port->recv_buf_size);

        trusty_unittest_printf("\n");
    }
    ipc_free_port_list(port_list);
}

PORT_TEST(list_ports, "com.android.kernel.list-ports");
