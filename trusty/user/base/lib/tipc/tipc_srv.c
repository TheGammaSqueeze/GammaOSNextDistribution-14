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

#include <lib/coverage/common/cov_shm.h>
#include <interface/line-coverage/aggregator.h>
#include <lib/line-coverage/shm.h>
#include <lib/coverage/common/ipc.h>
#include <assert.h>
#include <lk/err_ptr.h>
#include <lk/list.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#define TLOG_TAG "tipc-srv"
#include <lib/tipc/tipc_srv.h>
#include <trusty_log.h>

#include "tipc_priv.h"

static void port_event_handler_proc(const struct uevent* ev, void* port_ctx);
static void chan_event_handler_proc(const struct uevent* ev, void* chan_ctx);

struct port_ctx {
    struct tipc_event_handler event_handler;
    const struct tipc_port* cfg;
    struct tipc_srv* srv;
    handle_t handle;
};

struct chan_ctx {
    struct list_node chan_list_node;
    struct tipc_event_handler event_handler;
    struct port_ctx* port;
    handle_t handle;
    void* user_ctx;
};

struct tipc_srv {
    struct list_node chan_list;
    const struct tipc_srv_ops* ops;
    struct tipc_hset* hset;
    uint32_t chan_cnt;
    uint32_t max_chan_cnt;
    uint32_t port_cnt;
    struct port_ctx ports[0];
};

/*
 * Helper to mask/unmask events for all ports
 */
static void set_ports_event_mask(struct tipc_srv* srv, uint32_t mask) {
    uint32_t i;
    struct port_ctx* p;

    /* unmask ports here */
    for (i = 0, p = srv->ports; i < srv->port_cnt; i++, p++) {
        (void)tipc_hset_mod_entry(srv->hset, p->handle, mask,
                                  &p->event_handler);
    }
}

static bool server_at_max_chan_cnt(struct tipc_srv* srv) {
    return (srv->max_chan_cnt && (srv->chan_cnt == srv->max_chan_cnt));
}

/*
 * Helper to close channel
 */
static void tipc_chan_close(struct chan_ctx* chan) {
    int rc;
    struct tipc_srv* srv = chan->port->srv;
    void* user_ctx = chan->user_ctx;

    /* remove it from handle set */
    rc = tipc_hset_remove_entry(srv->hset, chan->handle);
    if (rc != NO_ERROR) {
        /* the only reason for this to fail if any handle is somehow
         * becomes invalid. There is no reasonable way to recover
         * from this.
         */
        TLOGE("hset_remove_entry failed (%d)\n", rc);
        abort();
    }

    /* remove it from list */
    list_delete(&chan->chan_list_node);

    /*
     * if we had  a maximum number of channels we will now be below maximum.
     * Unmask ports for this service so we can create channels.
     */
    if (server_at_max_chan_cnt(srv)) {
        set_ports_event_mask(srv, ~0u);
    }

    /* decrement channel count */
    srv->chan_cnt--;

    /* close channel */
    close(chan->handle);

    /* free memory */
    free(chan);

    /*  cleanup user allocated state if any */
    if (user_ctx) {
        srv->ops->on_channel_cleanup(user_ctx);
    }
}

/*
 *  channel event handler
 */
static void chan_event_handler_proc(const struct uevent* ev, void* chan_ctx) {
    int rc;
    struct chan_ctx* chan = chan_ctx;
    struct tipc_srv* srv = chan->port->srv;

    assert(ev->handle == chan->handle);

    tipc_handle_chan_errors(ev);

    setup_shm();

    if (ev->event & IPC_HANDLE_POLL_MSG) {
        rc = srv->ops->on_message(chan->port->cfg, chan->handle,
                                  chan->user_ctx);
        if (rc < 0) {
            /* report an error and close channel */
            TLOGE("failed (%d) to handle event on channel %d\n", rc,
                  ev->handle);
            tipc_chan_close(chan);
            dump_shm();
            return;
        }
    }

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        /* closed by peer. */
        TLOGD("close connection\n");
        dump_shm();
        if (srv->ops->on_disconnect) {
            srv->ops->on_disconnect(chan->port->cfg, chan->handle,
                                    chan->user_ctx);
        }

        tipc_chan_close(chan);
        return;
    }
}

/*
 *  Check if client is allowed to connect on specified port
 */
static bool client_is_allowed(const struct tipc_port_acl* acl,
                              const struct uuid* peer) {
    uint32_t i;

    if (!acl->uuid_num)
        return true;

    for (i = 0; i < acl->uuid_num; i++) {
        if (memcmp(peer, acl->uuids[i], sizeof(*peer)) == 0) {
            /* match */
            return true;
        }
    }

    return false;
}

/*
 *  Handle incoming connection
 */
static void handle_connect(struct port_ctx* port) {
    int rc;
    handle_t hchan;
    struct uuid peer;
    void* user_ctx = NULL;
    struct chan_ctx* chan;
    struct tipc_srv* srv = port->srv;

    TLOGD("Incoming connection on %s\n", port->cfg->name);

    /* incoming connection: accept it */
    rc = accept(port->handle, &peer);
    if (rc < 0) {
        TLOGE("failed (%d) to accept on port %s\n", rc, port->cfg->name);
        return;
    }
    hchan = (handle_t)rc;

    if (server_at_max_chan_cnt(srv)) {
        /* we should not ever get here after we implement port mask */
        TLOGE("too many channels for port %s\n", port->cfg->name);
        goto err_too_many_chan;
    }

    /* do access control */
    if (!client_is_allowed(port->cfg->acl, &peer)) {
        TLOGE("access denied on port %s\n", port->cfg->name);
        goto err_access;
    }

    chan = calloc(1, sizeof(*chan));
    if (!chan) {
        TLOGE("oom while handling port %s\n", port->cfg->name);
        goto err_oom;
    }

    /* fill channel structure */
    chan->event_handler.proc = chan_event_handler_proc;
    chan->event_handler.priv = chan;
    chan->port = port;
    chan->handle = hchan;

    /* add new channel to handle set */
    rc = tipc_hset_add_entry(srv->hset, hchan, ~0u, &chan->event_handler);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to add chan to hset\n", rc);
        goto err_hset_add;
    }

    /* invoke on_connect handler if any */
    if (srv->ops->on_connect) {
        rc = srv->ops->on_connect(port->cfg, chan->handle, &peer, &user_ctx);
        if (rc < 0) {
            TLOGE("on_connect failed (%d) on port %s\n", rc, port->cfg->name);
            goto err_on_connect;
        }
    }

    /* attach context provided by caller */
    chan->user_ctx = user_ctx;

    /* add it to the list */
    list_add_tail(&srv->chan_list, &chan->chan_list_node);
    srv->chan_cnt++;

    /* mask all ports if max number of connections has been reached */
    if (server_at_max_chan_cnt(srv)) {
        set_ports_event_mask(srv, 0u);
    }

    TLOGD("got connection on %s\n", port->cfg->name);
    return;

err_on_connect:
err_hset_add:
    free(chan);
err_oom:
err_too_many_chan:
err_access:
    close(hchan);
}

/*
 *  Port event handler
 */
static void port_event_handler_proc(const struct uevent* ev, void* ctx) {
    tipc_handle_port_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_READY) {
        struct port_ctx* port = ctx;
        assert(port->handle == ev->handle);
        handle_connect(port);
    }
}

/*
 *  Add new TIPC service to handle set
 */
int tipc_add_service(struct tipc_hset* hset,
                     const struct tipc_port* ports,
                     uint32_t num_ports,
                     uint32_t max_chan_cnt,
                     const struct tipc_srv_ops* ops) {
    int rc;
    uint32_t i;
    struct tipc_srv* srv;
    struct port_ctx* port;

    if (IS_ERR(hset)) {
        TLOGE("invalid handle set (%d)\n", PTR_ERR(hset));
        return ERR_INVALID_ARGS;
    }

    if (!hset || !ports || !num_ports || !ops) {
        TLOGE("required parameter is missing\n");
        return ERR_INVALID_ARGS;
    }

    /* allocate new service */
    srv = calloc(1,
                 sizeof(struct tipc_srv) + sizeof(struct port_ctx) * num_ports);
    if (!srv) {
        return ERR_NO_MEMORY;
    }

    /* and initialize it */
    srv->hset = hset;
    srv->port_cnt = num_ports;
    srv->max_chan_cnt = max_chan_cnt;

    list_initialize(&srv->chan_list);

    srv->ops = ops;
    for (i = 0; i < num_ports; i++) {
        srv->ports[i].handle = INVALID_IPC_HANDLE;
    }

    /* for each port */
    for (i = 0; i < num_ports; i++) {
        TLOGD("Initialize port: %s\n", ports[i].name);

        port = &srv->ports[i];

        if (!ports[i].acl) {
            TLOGE("ACL is required to create port\n");
            rc = ERR_INVALID_ARGS;
            goto err_no_acl;
        }

        /* create port */
        rc = port_create(ports[i].name, ports[i].msg_queue_len,
                         ports[i].msg_max_size, ports[i].acl->flags);
        if (rc < 0) {
            TLOGE("failed (%d) to create port\n", rc);
            goto err_port_create;
        }
        port->handle = (handle_t)rc;

        /* init event handler and other pointers */
        port->cfg = &ports[i];
        port->event_handler.proc = port_event_handler_proc;
        port->event_handler.priv = port;
        port->srv = srv;

        /* and add it to the handle set */
        rc = tipc_hset_add_entry(hset, port->handle, ~0u, &port->event_handler);
        if (rc < 0) {
            TLOGE("failed (%d) to register port\n", rc);
            goto err_hset_add;
        }
    }
    setup_mailbox(ports, num_ports);
    return 0;

err_hset_add:
err_port_create:
err_no_acl:
    /* kill all ports we have created so far */
    for (i = 0; i < num_ports; i++) {
        if (srv->ports[i].handle != INVALID_IPC_HANDLE) {
            /* Note: closing handle also removes it from all handle sets */
            rc = close(srv->ports[i].handle);
            assert(rc == 0);
        }
    }
    /* then free service */
    free(srv);

    return rc;
}
