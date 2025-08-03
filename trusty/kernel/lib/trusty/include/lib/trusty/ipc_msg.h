/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#include <kernel/usercopy.h>
#include <stdbool.h>
#include <stdint.h>
#include <sys/types.h>
#include <uapi/trusty_ipc.h>

#include <lib/trusty/uctx.h>
#include <lib/trusty/uio.h>

__BEGIN_CDECLS

struct ipc_msg_queue;
int ipc_msg_queue_create(uint num_items,
                         size_t item_sz,
                         struct ipc_msg_queue** mq);
void ipc_msg_queue_destroy(struct ipc_msg_queue* mq);

bool ipc_msg_queue_is_empty(struct ipc_msg_queue* mq);
bool ipc_msg_queue_is_full(struct ipc_msg_queue* mq);

/********** these structure definitions shared with userspace **********/

/* The layout for iovec_user and ipc_msg_user MUST match
 * the layout of iovec_kern and ipc_msg_kern
 */
#define MAX_MSG_HANDLES IPC_MAX_MSG_HANDLES

struct ipc_msg_kern {
    uint32_t num_iov;
    struct iovec_kern* iov;

    uint32_t num_handles;
    struct handle** handles;
};

struct ipc_msg_user {
    uint32_t num_iov;
    user_addr_t iov;

    uint32_t num_handles;
    user_addr_t handles; /* points to array of handle ids */
};

struct ipc_msg_info {
    size_t len;
    uint32_t id;
    uint32_t num_handles;
};

struct ipc_msg_info_user {
    user_size_t len;
    uint32_t id;
    uint32_t num_handles;
};

int ipc_get_msg(struct handle* chandle, struct ipc_msg_info* msg_info);
int ipc_read_msg(struct handle* chandle,
                 uint32_t msg_id,
                 uint32_t offset,
                 struct ipc_msg_kern* msg);
int ipc_put_msg(struct handle* chandle, uint32_t msg_id);
int ipc_send_msg(struct handle* chandle, struct ipc_msg_kern* msg);

__END_CDECLS
