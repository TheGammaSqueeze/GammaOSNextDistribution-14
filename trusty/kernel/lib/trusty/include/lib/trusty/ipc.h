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

#include <bits.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <reflist.h>
#include <stdbool.h>
#include <stdint.h>
#include <sys/types.h>

#include <lib/trusty/handle.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/trusty_app.h>
#include <lib/trusty/uuid.h>

__BEGIN_CDECLS

enum {
    IPC_PORT_STATE_INVALID = 0,
    IPC_PORT_STATE_LISTENING = 1,
};

enum {
    IPC_PORT_ALLOW_TA_CONNECT = 0x1,
    IPC_PORT_ALLOW_NS_CONNECT = 0x2,
};

#define IPC_PORT_PATH_MAX 64

struct ipc_port {
    /* e.g. /service/sys/crypto, /service/usr/drm/widevine */
    char path[IPC_PORT_PATH_MAX];
    const struct uuid* uuid;

    uint32_t state;
    uint32_t flags;

    uint num_recv_bufs;
    size_t recv_buf_size;

    struct handle handle;

    struct list_node pending_list;

    struct list_node node;
};

enum {
    IPC_CHAN_STATE_ACCEPTING = 1,
    IPC_CHAN_STATE_CONNECTING = 2,
    IPC_CHAN_STATE_CONNECTED = 3,
    IPC_CHAN_STATE_DISCONNECTING = 4,
};

enum {
    IPC_CHAN_FLAG_SERVER = 0x1,
};

/* aux state bitmasks */
#define IPC_CHAN_AUX_STATE_PEER_SEND_BLOCKED (1U << 1)
#define IPC_CHAN_AUX_STATE_SEND_UNBLOCKED (1U << 2)
#define IPC_CHAN_AUX_STATE_CONNECTED (1U << 3)

#define IPC_CHAN_MAX_BUFS 32
#define IPC_CHAN_MAX_BUF_SIZE 4096

struct ipc_chan {
    struct obj refobj;
    spin_lock_t ref_slock;
    struct obj_ref peer_ref;
    struct ipc_chan* peer;
    const struct uuid* uuid;

    uint32_t state;
    uint32_t flags;
    uint32_t aux_state;

    /* handle_ref is a self reference when there are
     * outstanding handles out there. It is removed
     * when last handle ref goes away.
     */
    struct obj_ref handle_ref;
    struct handle handle;

    /* used for port's pending list. node_ref field is a
     * self reference when node field is inserted in the list.
     *
     * TODO: consider creating generic solution by grouping
     * together list_node and struct obj_ref into single struct.
     */
    struct obj_ref node_ref;
    struct list_node node;

    struct ipc_msg_queue* msg_queue;

    /*
     * TODO: consider changing async connect to preallocate
     *       not-yet-existing port object then we can get rid
     *      of this field.
     */
    const char* path;

    struct mutex mlock;
};

/* called by server to create port */
int ipc_port_create(const uuid_t* sid,
                    const char* path,
                    uint num_recv_bufs,
                    size_t recv_buf_size,
                    uint32_t flags,
                    struct handle** phandle_ptr);

/* called by server to publish the port */
int ipc_port_publish(struct handle* phandle);

/* server calls to accept a pending connection */
int ipc_port_accept(struct handle* phandle,
                    struct handle** chandle_ptr,
                    const uuid_t** uuid_ptr);

/**
 * ipc_connection_waiting_for_port () - Query if the given port path has any
 * valid connection waiting for it.
 * @path: port for the query
 * @flags: flags to validate connections against
 *
 * Return: true if there is a valid connection waiting for @port_path, false
 * otherwise.
 */
bool ipc_connection_waiting_for_port(const char* path, uint32_t flags);

/**
 * ipc_remove_connection_waiting_for_port () - Remove all valid connections
 * waiting for a given port path.
 * @path: port for the query
 * @flags: flags to validate connections against
 */
void ipc_remove_connection_waiting_for_port(const char* path, uint32_t flags);

/* client requests a connection to a port */
#define IPC_CONNECT_WAIT_FOR_PORT 0x1U
#define IPC_CONNECT_ASYNC 0x2U
#define IPC_CONNECT_MASK (IPC_CONNECT_WAIT_FOR_PORT | IPC_CONNECT_ASYNC)

int ipc_port_connect_async(const uuid_t* cid,
                           const char* path,
                           size_t max_path,
                           uint flags,
                           struct handle** chandle_ptr);

bool ipc_is_channel(struct handle* handle);
bool ipc_is_port(struct handle* handle);

/**
 *  is_ns_client() - checks if specified uuid represents a non-secure client
 *  @uuid: pointer to struct uuid representin IPC client
 *
 *  Each IPC client is identified by uuid that originated connection which
 *  could be an app, external TIPC device, or kernel entity. This call
 *  is typically implemented by a module that configures external TIPC
 *  devices and check if specified UUID represents non-secure TIPC device.
 *
 *  Return: true if uuds represents non-secure client, false otherwise
 */
bool is_ns_client(const uuid_t* uuid);

/**
 * ipc_port_check_access() - Check if an application can access a port with the
 * given flags.
 * @port_flags: flags of the port to check against
 * @uuid: uuid of the application to check
 *
 * Return: NO_ERROR if the access is allowed, ERR_ACCESS_DENIED otherwise.
 */
int ipc_port_check_access(uint32_t port_flags, const uuid_t* uuid);

#if TEST_BUILD
/**
 * ipc_get_port_list() - List all ports used by application
 * @out_port_list: pointer to list of ports. must be freed by caller
 *
 * Return: The size of the list if positive. An Error if negative.
 */
int ipc_get_port_list(struct ipc_port** out_port_list);

/**
 * ipc_free_port_list() - Free the given pointer to port list
 * @out_port_list: pointer to list of ports.
 *
 */
void ipc_free_port_list(struct ipc_port* out_port_list);
#endif

__END_CDECLS
