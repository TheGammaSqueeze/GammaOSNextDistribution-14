/*
 * Copyright (c) 2013 Google Inc. All rights reserved
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
#include <stddef.h>
#include <stdint.h>
#include <sys/uio.h>
#include <uapi/trusty_ipc.h>
#include <uapi/trusty_uuid.h>

__BEGIN_CDECLS

/*
 *  handle_t is an opaque 32 bit value that is used to reference an
 *  object (like ipc port or channel) in kernel space
 */
typedef int32_t handle_t;

/*
 *  Invalid IPC handle
 */
#define INVALID_IPC_HANDLE ((handle_t)-1)

/*
 *  Specify this timeout value to wait forever.
 */
#define INFINITE_TIME UINT32_MAX

/*
 * Combination of these flags sets additional options
 * for port_create syscall.
 */
enum {
    /* allow Trusted Apps to connect to this port */
    IPC_PORT_ALLOW_TA_CONNECT = 0x1,
    /* allow non-secure clients to connect to this port */
    IPC_PORT_ALLOW_NS_CONNECT = 0x2,
};

/*
 * Options for connect syscall
 */
enum {
    IPC_CONNECT_WAIT_FOR_PORT = 0x1,
    IPC_CONNECT_ASYNC = 0x2,
};

/*
 *  IPC message
 */
typedef struct ipc_msg {
    uint32_t num_iov;
    struct iovec* iov;

    uint32_t num_handles;
    handle_t* handles;
} ipc_msg_t;

typedef struct ipc_msg_info {
    size_t len;
    uint32_t id;
    uint32_t num_handles;
} ipc_msg_info_t;

/*
 *  Combination of these values is used for event field
 *  ot uevent_t structure.
 */
enum {
    IPC_HANDLE_POLL_NONE = 0x0,
    IPC_HANDLE_POLL_READY = 0x1,
    IPC_HANDLE_POLL_ERROR = 0x2,
    IPC_HANDLE_POLL_HUP = 0x4,
    IPC_HANDLE_POLL_MSG = 0x8,
    IPC_HANDLE_POLL_SEND_UNBLOCKED = 0x10,
};

/*
 *  Values for cmd parameter of handle_set_ctrl call
 */
enum {
    HSET_ADD = 0x0, /* adds new handle to handle set */
    HSET_DEL = 0x1, /* deletes handle from handle set */
    HSET_MOD = 0x2, /* modifies handle attributes in handle set */
};

/*
 *  Is used by wait and wait_any calls to return information
 *  about event.
 */
typedef struct uevent {
    handle_t handle; /* handle this event is related too */
    uint32_t event;  /* combination of IPC_HANDLE_POLL_XXX flags */
    void* cookie;    /* cookie aasociated with handle */
} uevent_t;

#define UEVENT_INITIAL_VALUE(event) \
    { 0, 0, 0 }

handle_t port_create(const char* path,
                     uint32_t num_recv_bufs,
                     uint32_t recv_buf_size,
                     uint32_t flags);
handle_t connect(const char* path, uint32_t flags);
handle_t accept(handle_t handle, uuid_t* peer_uuid);
int close(handle_t handle);
int set_cookie(handle_t handle, void* cookie);
handle_t handle_set_create(void);
int handle_set_ctrl(handle_t handle, uint32_t cmd, struct uevent* evt);
int wait(handle_t handle, uevent_t* event, uint32_t timeout_msecs);
int wait_any(uevent_t* event, uint32_t timeout_msecs);
int get_msg(handle_t handle, ipc_msg_info_t* msg_info);
ssize_t read_msg(handle_t handle,
                 uint32_t msg_id,
                 uint32_t offset,
                 ipc_msg_t* msg);
int put_msg(handle_t handle, uint32_t msg_id);
ssize_t send_msg(handle_t handle, ipc_msg_t* msg);
handle_t dup(handle_t handle);

__END_CDECLS
