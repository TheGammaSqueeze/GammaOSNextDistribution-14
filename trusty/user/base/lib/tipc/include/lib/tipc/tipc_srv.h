/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lib/tipc/tipc.h>
#include <lk/compiler.h>
#include <lk/list.h>
#include <stddef.h>

__BEGIN_CDECLS

/**
 * DOC: Theory of Operation
 *
 * This module implements TIPC service framework that supports
 * hosting multiple TIPC services within single applications in a
 * unified manner. The service set is semi-static, it can be instantiated
 * at runtime but individual services are not expected to terminate once
 * instantiated.
 *
 * An individual TIPC service consists of one or more TIPC ports that external
 * clients can connect to. All ports for the same service share the same set
 * of user provided ops (callbacks) that framework invokes to handle requests
 * in a uniform way.

 * Each port can be configured individually to accept connections only from
 * predefined set of clients identified by UUIDs.
 *
 * Each service can be configured to accept only limited number of connection
 * across all ports within service.
 *
 * It is expected that application would make the following sequence of calls:
 *
 *  - call tipc_hset_create function to create handle set
 *
 *  - allocate statically or dynamically and initialize one or more tipc_port
 *    structures describing set of ports related to particular services
 *
 *  - allocate statically or dynamically and initialize tipc_srv_ops struct
 *    containing pointers for service specific callbacks.
 *
 *  - call tipc_add_service to add service to service set. Multiple services
 *  can be added to the same service set.
 *
 *  - call tipc_run_event_loop to run them. This routine is not expected
 *  to return unless unrecoverable condition is encountered.
 */

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
 * UUID and  connection from any client will be accepted. In this case @uuids
 * parameter is ignored and can be set NULL.
 */
struct tipc_port_acl {
    uint32_t flags;
    uint32_t uuid_num;
    const struct uuid** uuids;
    const void* extra_data;
};

/**
 * struct tipc_port - TIPC port descriptor
 * @name:          port name
 * @msg_max_size:  max message size supported
 * @msg_queue_len: max number of messages in queue
 * @acl:           pointer to &struct tipc_port_acl specifying ACL rules
 * @priv:          port specific private data
 *
 * Note: @acl is a required parameter for specifying any port even if
 * service does not require access control.
 */
struct tipc_port {
    const char* name;
    uint32_t msg_max_size;
    uint32_t msg_queue_len;
    const struct tipc_port_acl* acl;
    const void* priv;
};

/**
 * struct tipc_srv_ops - service specific ops (callbacks)
 * @on_connect:    is invoked when a new connection is established.
 * @on_message:    is invoked when a new message is available
 * @on_disconnect: is invoked when the peer terminates connection
 * @on_channel_cleanup: is invoked to cleanup user allocated state
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
 * Note: an application implementing these callbacks MUST not close channel
 * received as @chan parameter directly, instead it should return an error
 * and the channel will be closed by the framework.
 *
 */
struct tipc_srv_ops {
    int (*on_connect)(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p);

    int (*on_message)(const struct tipc_port* port, handle_t chan, void* ctx);

    void (*on_disconnect)(const struct tipc_port* port,
                          handle_t chan,
                          void* ctx);

    void (*on_channel_cleanup)(void* ctx);
};

/**
 * tipc_add_service() - Add new service to service set
 * @hset:         pointer to handle set to add service to
 * @ports:        an array of &struct tipc_port describing ports for this
 *                service
 * @num_ports:    number of ports in array pointed by @ports
 * @max_chan_cnt: max number of active connections allowed for this service, 0
 *                for no limit.
 * @ops:          pointer to &struct tipc_srv_ops with service specific
 *                callbacks
 *
 * Note: the caller retain an ownership of structures pointed by @ports
 * parameters and should not modify these structures in any way after the
 * service has beed instantiated. Also, the caller is responsible for keeping
 * them alive while service is running. The same is true for handle set. In
 * addition, the caller should not invoke any direct operations on handle set
 * outside of API's provided by this framework.
 *
 * Return: 0 on success, negative error code otherwise
 */
int tipc_add_service(struct tipc_hset* hset,
                     const struct tipc_port* ports,
                     uint32_t num_ports,
                     uint32_t max_chan_cnt,
                     const struct tipc_srv_ops* ops);

__END_CDECLS
