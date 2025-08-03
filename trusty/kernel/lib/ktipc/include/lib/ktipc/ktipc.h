/*
 * Copyright (c) 2020, Google Inc. All rights reserved
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

#include <kernel/event.h>
#include <kernel/thread.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/uio.h>

__BEGIN_CDECLS

struct ktipc_server {
    const char* name;
    struct handle* hset;
    struct thread* thread;
    struct event have_handles_evt;
};

/**
 * KTIPC_SERVER_INITIAL_VALUE() - ktipc server static initializer
 * @ksrv: pointer to @struct ktipc_server to initialize
 * @name: server name
 *
 * @name should not be deallocated while server is running
 */
#define KTIPC_SERVER_INITIAL_VALUE(ksrv, nm)                          \
    {                                                                 \
        .name = (nm), .hset = NULL, .thread = NULL,                   \
        .have_handles_evt =                                           \
                EVENT_INITIAL_VALUE(ksrv.have_handles_evt, false, 0), \
    }

/**
 * ktipc_server_init() - initialize ktipc server structure
 * @ksrv: pointer to @struct ktipc_server to initialize
 * @name: server name
 *
 * @name should not be deallocated while server is running
 *
 * Return: none
 */
static inline void ktipc_server_init(struct ktipc_server* ksrv,
                                     const char* name) {
    ksrv->name = name;
    ksrv->hset = NULL;
    ksrv->thread = NULL;
    event_init(&ksrv->have_handles_evt, false, 0);
}

/**
 *  ktipc_server_start() - start specified ktipc server
 *  @server: server to start
 *  @name: server name
 *
 *  The &struct ktipc_server must be initialized by calling ktipc_server_init()
 *  or static initializer KTIPC_SERVER_INITIAL_VALUE.
 *
 *
 *  Return: 0 on success, negative error code otherwise
 */
int ktipc_server_start(struct ktipc_server* server);

/**
 * struct tipc_port_acl - tipc port ACL descriptor
 * @flags:      a combination of IPC_PORT_ALLOW_XXX_CONNECT flags that will be
 *              directly passed to underlying port_create call.
 * @uuid_num:   number of entries in an array pointed by @uuids field
 * @uuids:      pointer to array of pointers to uuids of apps allowed to connect
 * @extra_data: pointer to extra data associated with this ACL structure, which
 *              can be used in application specific way.
 *
 * Note: @uuid_num parameter can be 0 to indicate that there is no filtering by
 * UUID and connection from any client will be accepted. In this case @uuids
 * parameter is ignored and can be set to NULL.
 */
struct ktipc_port_acl {
    uint32_t flags;
    uint32_t uuid_num;
    const struct uuid** uuids;
    const void* extra_data;
};

/**
 * struct tipc_port - TIPC port descriptor
 * @name:          port name
 * @uuid:          UUID of the service
 * @msg_max_size:  max message size supported
 * @msg_queue_len: max number of messages in queue
 * @acl:           pointer to &struct tipc_port_acl specifying ACL rules
 * @priv:          port specific private data
 *
 * Note: @acl is a required parameter for specifying any port even if
 * service does not require access control.
 *
 * Most services should use ``&kernel_uuid`` as the value of @uuid,
 * unless there is a legitimate reason to use a different UUID.
 */
struct ktipc_port {
    const char* name;
    const struct uuid* uuid;
    uint32_t msg_max_size;
    uint32_t msg_queue_len;
    const struct ktipc_port_acl* acl;
    const void* priv;
};

/**
 * struct ktipc_srv_ops - service specific ops (callbacks)
 * @on_connect:    is invoked when a new connection is established.
 * @on_message:    is invoked when a new message is available
 * @on_disconnect: is invoked when the peer terminates connection
 * @on_channel_cleanup: is invoked to cleanup user allocated state
 * @on_send_unblocked:  is invoked when the channel is unblocked for sending
 *
 * The overall call flow is as follow:
 *
 * Upon receiving a connection request from client framework accepts it and
 * validates against configured ACL rules. If connection is allowed, the
 * framework allocates a channel tracking structure and invokes an optional
 * @on_connect callback if specified. The user who choose to implement this
 * callback can allocate its own tracking structure and return pointer to that
 * through @ctx_p parameter. After that this pointer will be associated with
 * particular channel and it will be passed to all other callbacks.
 *
 * Upon receiving a message directed to particular channel, a corresponding
 * @on_message callback is invoked so this message can be handled according
 * with application specific protocol. The @on_message call back is a
 * mandatory in this implementation.
 *
 * Upon receiving a disconnect request an optional @on_disconnect callback is
 * invoked to indicate that peer closed connection. At this point the channel
 * is still alive but in disconnected state, it will be closed by framework
 * after control returns from executing this callback.
 *
 * The @on_channel_cleanup callback is invoked by framework to give the user
 * a chance to release channel specific resources if allocated by
 * @on_connect callback, after the channel have been closed. This
 * callback is mandatory if the user implements @on_connect callback and
 * allocates per channel state.
 *
 * The @on_send_unblocked callback is invoked when a channel becomes unblocked
 * for sending after an earlier %ERR_NOT_ENOUGH_BUFFER error code returned
 * by tipc_send1() or ipc_send_msg().
 *
 * Note: an application implementing these callbacks MUST not close channel
 * received as @chan parameter directly, instead it should return an error
 * and the channel will be closed by the framework.
 *
 */
struct ktipc_srv_ops {
    int (*on_connect)(const struct ktipc_port* port,
                      struct handle* chan,
                      const struct uuid* peer,
                      void** ctx_p);

    int (*on_message)(const struct ktipc_port* port,
                      struct handle* chan,
                      void* ctx);

    void (*on_disconnect)(const struct ktipc_port* port,
                          struct handle* chan,
                          void* ctx);

    void (*on_channel_cleanup)(void* ctx);

    int (*on_send_unblocked)(const struct ktipc_port* port,
                             struct handle* chan,
                             void* ctx);
};

/**
 * ktipc_server_add_port() - Add new port to kernel tipc server
 * @ksrv: pointer to kernel tipc server to add port to
 * @port: pointer to &struct ktipc_port
 * @ops:  pointer to &struct ktipc_srv_ops with service specific
 *        callbacks
 *
 * Note: the caller retains ownership of structures pointed by @port
 * parameters and should not modify these structures in any way after the
 * service has been instantiated. Also, the caller is responsible for keeping
 * them alive while service is running.
 *
 * Return: 0 on success, negative error code otherwise
 */
int ktipc_server_add_port(struct ktipc_server* ksrv,
                          const struct ktipc_port* port,
                          const struct ktipc_srv_ops* ops);

#define KTIPC_INTERNAL_IOV_INIT(buf, len) \
    { .iov_base = (void*)(buf), .iov_len = (len) }

#define ktipc_internal_send(chan, arg_handles, arg_num_handles, buf0, sz0,    \
                            buf1, sz1, buf2, sz2, buf3, sz3, buf4, sz4, buf5, \
                            sz5, buf6, sz6, buf7, sz7, db0, ds0, db1, ds1,    \
                            db2, ds2, db3, ds3, db4, ds4, db5, ds5, db6, ds6, \
                            db7, ds7, count, rest...)                         \
    ({                                                                        \
        /*                                                                    \
         * Put the rest of the arguments in a fixed array so we get a         \
         * static error if the caller passed in too many buffers              \
         */                                                                   \
        __UNUSED int rest_array[2 * 8] = {rest};                              \
        struct iovec_kern iov[] = {                                           \
                KTIPC_INTERNAL_IOV_INIT(buf0, sz0),                           \
                KTIPC_INTERNAL_IOV_INIT(buf1, sz1),                           \
                KTIPC_INTERNAL_IOV_INIT(buf2, sz2),                           \
                KTIPC_INTERNAL_IOV_INIT(buf3, sz3),                           \
                KTIPC_INTERNAL_IOV_INIT(buf4, sz4),                           \
                KTIPC_INTERNAL_IOV_INIT(buf5, sz5),                           \
                KTIPC_INTERNAL_IOV_INIT(buf6, sz6),                           \
                KTIPC_INTERNAL_IOV_INIT(buf7, sz7),                           \
        };                                                                    \
        struct ipc_msg_kern msg = {                                           \
                .iov = iov,                                                   \
                .num_iov = count,                                             \
                .handles = (struct handle**)(arg_handles),                    \
                .num_handles = (arg_num_handles),                             \
        };                                                                    \
        ipc_send_msg((chan), &msg);                                           \
    })

/**
 * ktipc_send_handles() - send a message from zero or more buffers
 * @chan: handle of the channel to send message over
 * @handles: pointer to an array of handles or %NULL
 * @num_handles: size of @handles array
 * @buffers: zero or more pairs of two arguments: a pointer to a buffer
 *           and the size of that buffer; up to 8 buffer pairs are currently
 *           supported by the implementation
 *
 * If there are no handles to send, @handles should be %NULL
 * and @num_handles should be zero.
 *
 * Return: the total number of bytes sent on success, a negative
 *         error code otherwise.
 */
#define ktipc_send_handles(chan, handles, num_handles, buffers...)             \
    ktipc_internal_send(chan, handles, num_handles, buffers, NULL, 0, NULL, 0, \
                        NULL, 0, NULL, 0, NULL, 0, NULL, 0, NULL, 0, NULL, 0,  \
                        8, 8, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0)

/**
 * ktipc_send() - send a message from zero or more buffers
 * @chan: handle of the channel to send message over
 * @buffers: zero or more pairs of two arguments: a pointer to a buffer
 *           and the size of that buffer; up to 8 buffer pairs are currently
 *           supported by the implementation
 *
 * Return: the total number of bytes sent on success, a negative
 *         error code otherwise.
 */
#define ktipc_send(chan, buffers...) ktipc_send_handles(chan, NULL, 0, buffers)

/**
 * ktipc_recv_iov() - receive message into a set of iovecs
 * @chan: handle of the channel to receive message from
 * @min_sz: minimum size of the message expected
 * @iov: pointer to an array of &struct iovec_kern elements
 * @num_iov: number of iovecs to receive
 * @handles: pointer to an array of handles or %NULL
 * @num_handles: size of @handles array
 *
 * The received message has to contain at least @min_sz bytes and
 * fully fit into provided buffers.
 *
 * If there are no handles to receive, @handles should be %NULL
 * and @num_handles should be zero.
 *
 * Return: the number of bytes stored into the iovecs
 *         parameter on success, a negative error code otherwise
 */
int ktipc_recv_iov(struct handle* chan,
                   size_t min_sz,
                   struct iovec_kern* iov,
                   uint32_t num_iov,
                   struct handle** handles,
                   uint32_t num_handles);

#define ktipc_internal_recv(chan, min_sz, handles, num_handles, buf0, sz0,    \
                            buf1, sz1, buf2, sz2, buf3, sz3, buf4, sz4, buf5, \
                            sz5, buf6, sz6, buf7, sz7, db0, ds0, db1, ds1,    \
                            db2, ds2, db3, ds3, db4, ds4, db5, ds5, db6, ds6, \
                            db7, ds7, count, rest...)                         \
    ({                                                                        \
        /*                                                                    \
         * Put the rest of the arguments in a fixed array so we get a         \
         * static error if the caller passed in too many buffers              \
         */                                                                   \
        __UNUSED int rest_array[2 * 8] = {rest};                              \
        struct iovec_kern iov[] = {                                           \
                KTIPC_INTERNAL_IOV_INIT(buf0, sz0),                           \
                KTIPC_INTERNAL_IOV_INIT(buf1, sz1),                           \
                KTIPC_INTERNAL_IOV_INIT(buf2, sz2),                           \
                KTIPC_INTERNAL_IOV_INIT(buf3, sz3),                           \
                KTIPC_INTERNAL_IOV_INIT(buf4, sz4),                           \
                KTIPC_INTERNAL_IOV_INIT(buf5, sz5),                           \
                KTIPC_INTERNAL_IOV_INIT(buf6, sz6),                           \
                KTIPC_INTERNAL_IOV_INIT(buf7, sz7),                           \
        };                                                                    \
        ktipc_recv_iov((chan), (min_sz), iov, count, (handles),               \
                       (num_handles));                                        \
    })

/**
 * ktipc_recv_handles() - receive a message into zero or more buffers
 * @chan: handle of the channel to send message over
 * @min_sz: minimum size of the message expected
 * @handles: pointer to an array of handles or %NULL
 * @num_handles: size of @handles array
 * @buffers: zero or more pairs of two arguments: a pointer to a buffer
 *           and the size of that buffer; up to 8 buffer pairs are currently
 *           supported by the implementation
 *
 * The received message has to contain at least @min_sz bytes and
 * fully fit into provided buffer.
 *
 * If there are no handles to send, @handles should be %NULL
 * and @num_handles should be zero.
 *
 * Return: the number of bytes stored into the buffers pointed by @buffers
 *         on success, a negative error code otherwise
 */
#define ktipc_recv_handles(chan, min_sz, handles, num_handles, buffers...)    \
    ktipc_internal_recv(chan, min_sz, handles, num_handles, buffers, NULL, 0, \
                        NULL, 0, NULL, 0, NULL, 0, NULL, 0, NULL, 0, NULL, 0, \
                        NULL, 0, 8, 8, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, \
                        1, 0)

/**
 * ktipc_recv() - receive a message into zero or more buffers
 * @chan: handle of the channel to send message over
 * @min_sz: minimum size of the message expected
 * @buffers: zero or more pairs of two arguments: a pointer to a buffer
 *           and the size of that buffer; up to 8 buffer pairs are currently
 *           supported by the implementation
 *
 * The received message has to contain at least @min_sz bytes and
 * fully fit into provided buffer.
 *
 * Return: the number of bytes stored into the buffers pointed by @buffers
 *         on success, a negative error code otherwise
 */
#define ktipc_recv(chan, min_sz, buffers...) \
    ktipc_recv_handles(chan, min_sz, NULL, 0, buffers)

__END_CDECLS
