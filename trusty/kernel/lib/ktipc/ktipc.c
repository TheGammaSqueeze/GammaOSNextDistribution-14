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
#include <lib/ktipc/ktipc.h>

#include <assert.h>
#include <err.h>
#include <inttypes.h>
#include <kernel/thread.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <lk/init.h>
#include <lk/list.h>
#include <string.h>
#include <trace.h>

#define LOCAL_TRACE 0

struct ksrv_event_handler {
    void (*handler)(struct ktipc_server* ksrv,
                    struct ksrv_event_handler* evth,
                    uint32_t event);
};

struct ksrv_port {
    struct handle_ref href;
    struct ksrv_event_handler evth;
    const struct ktipc_srv_ops* ops;
    const struct ktipc_port* port;
};

struct ksrv_chan {
    struct handle_ref href;
    struct ksrv_event_handler evth;
    const struct ktipc_srv_ops* ops;
    const struct ktipc_port* port;
    void* user_ctx;
};

/*
 * Helper to close channel
 */
static void ktipc_chan_close(struct ksrv_chan* kchan) {
    void* user_ctx = kchan->user_ctx;
    const struct ktipc_srv_ops* ops = kchan->ops;

    /* detach handle_ref */
    handle_set_detach_ref(&kchan->href);

    /* close channel */
    handle_decref(kchan->href.handle);

    /* free memory */
    free(kchan);

    /*  cleanup user allocated state if any */
    if (user_ctx) {
        ops->on_channel_cleanup(user_ctx);
    }
}

static void chan_event_handler(struct ktipc_server* ksrv,
                               struct ksrv_event_handler* evth,
                               uint32_t event) {
    int rc;

    struct ksrv_chan* kchan = containerof(evth, struct ksrv_chan, evth);

    if ((event & IPC_HANDLE_POLL_ERROR) || (event & IPC_HANDLE_POLL_READY)) {
        /* should never happen for channel handles */
        TRACEF("error event (0x%" PRIx32 ")\n", event);
        ktipc_chan_close(kchan);
        return;
    }

    if (event & IPC_HANDLE_POLL_MSG) {
        LTRACEF("got message\n");

        rc = kchan->ops->on_message(kchan->port, kchan->href.handle,
                                    kchan->user_ctx);
        if (rc < 0) {
            /* report an error and close channel */
            TRACEF("failed (%d) to handle event on channel %p\n", rc,
                   kchan->href.handle);
            ktipc_chan_close(kchan);
            return;
        }
    }

    if (event & IPC_HANDLE_POLL_HUP) {
        LTRACEF("connection closed by peer\n");

        /* closed by peer. */
        if (kchan->ops->on_disconnect) {
            kchan->ops->on_disconnect(kchan->port, kchan->href.handle,
                                      kchan->user_ctx);
        }
        ktipc_chan_close(kchan);
        return;
    }

    if (event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
        LTRACEF("unblocked for sending\n");

        if (kchan->ops->on_send_unblocked) {
            rc = kchan->ops->on_send_unblocked(kchan->port, kchan->href.handle,
                                               kchan->user_ctx);
            if (rc < 0) {
                /* report an error and close channel */
                TRACEF("failed (%d) to handle event on channel %p\n", rc,
                       kchan->href.handle);
                ktipc_chan_close(kchan);
                return;
            }
        } else {
            LTRACEF("send-unblocking not handled for channel %p\n",
                    kchan->href.handle);
            ktipc_chan_close(kchan);
            return;
        }
    }
}

/*
 *  Check if client is allowed to connect on specified port
 */
static bool client_is_allowed(const struct ktipc_port_acl* acl,
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
static void handle_connect(struct ktipc_server* ksrv, struct ksrv_port* kport) {
    int rc;
    struct handle* hchan;
    const struct uuid* peer;
    void* user_ctx = NULL;
    struct ksrv_chan* kchan;

    /* incoming connection: accept it */
    rc = ipc_port_accept(kport->href.handle, &hchan, &peer);
    if (rc < 0) {
        TRACEF("failed (%d) to accept on port %s\n", rc, kport->port->name);
        return;
    }

    /* do access control */
    if (!client_is_allowed(kport->port->acl, peer)) {
        TRACEF("access denied on port %s\n", kport->port->name);
        goto err_access;
    }

    kchan = calloc(1, sizeof(*kchan));
    if (!kchan) {
        TRACEF("oom handling connect on port %s\n", kport->port->name);
        goto err_oom;
    }

    /* setup channel structure */
    kchan->evth.handler = chan_event_handler;
    kchan->port = kport->port;
    kchan->ops = kport->ops;

    /* add new channel to handle set */
    kchan->href.emask = ~0U;
    kchan->href.cookie = &kchan->evth;
    kchan->href.handle = hchan;

    rc = handle_set_attach(ksrv->hset, &kchan->href);
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) to add chan to hset\n", rc);
        goto err_hset_add;
    }

    /* invoke on_connect handler if any */
    if (kport->ops->on_connect) {
        rc = kport->ops->on_connect(kport->port, hchan, peer, &user_ctx);
        if (rc < 0) {
            TRACEF("on_connect failed (%d) on port %s\n", rc,
                   kport->port->name);
            goto err_on_connect;
        }
    }

    /* attach context provided by caller */
    kchan->user_ctx = user_ctx;

    return;

err_on_connect:
    handle_set_detach_ref(&kchan->href);
err_hset_add:
    free(kchan);
err_oom:
err_access:
    handle_decref(hchan);
}

static void port_event_handler(struct ktipc_server* ksrv,
                               struct ksrv_event_handler* evth,
                               uint32_t event) {
    struct ksrv_port* kport = containerof(evth, struct ksrv_port, evth);

    if ((event & IPC_HANDLE_POLL_ERROR) || (event & IPC_HANDLE_POLL_HUP) ||
        (event & IPC_HANDLE_POLL_MSG) ||
        (event & IPC_HANDLE_POLL_SEND_UNBLOCKED)) {
        /* should never happen with port handles */
        LTRACEF("error event (0x%" PRIx32 ") for port\n", event);
        return;
    }

    if (event & IPC_HANDLE_POLL_READY) {
        handle_connect(ksrv, kport);
    }
}

int ktipc_server_add_port(struct ktipc_server* ksrv,
                          const struct ktipc_port* port,
                          const struct ktipc_srv_ops* ops) {
    int rc;
    struct ksrv_port* kport;

    if (!ksrv) {
        rc = ERR_INVALID_ARGS;
        goto err_null_ksrv;
    }

    /* validate ops structure */
    if (!ops) {
        TRACEF("ktipc_srv_ops structure is NULL\n");
        rc = ERR_INVALID_ARGS;
        goto err_null_ops;
    }
    if (!ops->on_message) {
        TRACEF("on_message handler is NULL\n");
        rc = ERR_INVALID_ARGS;
        goto err_null_on_message;
    }
    /* on_channel_cleanup is required if on_connect is present */
    if (ops->on_connect && !ops->on_channel_cleanup) {
        TRACEF("on_connect handler without on_channel_cleanup\n");
        rc = ERR_INVALID_ARGS;
        goto err_null_on_channel_cleanup;
    }

    /* allocate port tracking structure */
    kport = calloc(1, sizeof(*kport));
    if (!kport) {
        TRACEF("failed to allocate port\n");
        rc = ERR_NO_MEMORY;
        goto err_oom;
    }

    /* create port */
    rc = ipc_port_create(port->uuid, port->name, port->msg_queue_len,
                         port->msg_max_size, port->acl->flags,
                         &kport->href.handle);
    if (rc) {
        TRACEF("failed (%d) to create port %s\n", rc, port->name);
        goto err_port_create;
    }

    /* and publish it */
    rc = ipc_port_publish(kport->href.handle);
    if (rc) {
        TRACEF("failed (%d) to publish port %s\n", rc, port->name);
        goto err_port_publish;
    }

    /* configure port event handler */
    kport->evth.handler = port_event_handler;
    kport->port = port;
    kport->ops = ops;

    /* attach it to handle set */
    kport->href.emask = ~0U;
    kport->href.cookie = &kport->evth;

    rc = handle_set_attach(ksrv->hset, &kport->href);
    if (rc < 0) {
        TRACEF("failed (%d) to attach handle for port %s\n", rc, port->name);
        goto err_hset_add_port;
    }

    /* kick have handles event */
    event_signal(&ksrv->have_handles_evt, false);

    return 0;

err_hset_add_port:
err_port_publish:
    handle_decref(kport->href.handle);
err_port_create:
    free(kport);
err_oom:
err_null_on_channel_cleanup:
err_null_on_message:
err_null_ops:
err_null_ksrv:
    return rc;
}

static void ksrv_event_loop(struct ktipc_server* ksrv) {
    int rc;
    struct handle_ref evt_ref;

    while (true) {
        /* wait here until we have handles */
        event_wait(&ksrv->have_handles_evt);
        while (true) {
            rc = handle_set_wait(ksrv->hset, &evt_ref, INFINITE_TIME);
            if (rc != NO_ERROR) {
                panic("handle_set_wait failed: %d\n", rc);
            }

            /* get handler and invoke it */
            struct ksrv_event_handler* evth = evt_ref.cookie;
            DEBUG_ASSERT(evth && evth->handler);
            evth->handler(ksrv, evth, evt_ref.emask);

            /* decrement ref obtained by handle_set_wait */
            handle_decref(evt_ref.handle);
        }
    }
}

static int ksrv_thread(void* args) {
    struct ktipc_server* ksrv = args;

    LTRACEF("starting ktipc service: %s\n", ksrv->name);

    ksrv->hset = handle_set_create();
    if (!ksrv->hset) {
        TRACEF("failed to create handle set\n");
        return ERR_NO_MEMORY;
    }

    /* enter event loop */
    ksrv_event_loop(ksrv);
    LTRACEF("event loop returned\n");

    /* kill handle set */
    handle_decref(ksrv->hset);
    return 0;
}

int ktipc_server_start(struct ktipc_server* ksrv) {
    DEBUG_ASSERT(ksrv);
    DEBUG_ASSERT(!ksrv->thread);

    ksrv->thread = thread_create(ksrv->name, ksrv_thread, ksrv,
                                 DEFAULT_PRIORITY, DEFAULT_STACK_SIZE);
    if (!ksrv->thread) {
        TRACEF("failed to create %s thread\n", ksrv->name);
        return ERR_NO_MEMORY;
    }
    thread_resume(ksrv->thread);
    return 0;
}

int ktipc_recv_iov(struct handle* chan,
                   size_t min_sz,
                   struct iovec_kern* iov,
                   uint32_t num_iov,
                   struct handle** handles,
                   uint32_t num_handles) {
    int rc;
    struct ipc_msg_info msg_inf;
    size_t max_sz = 0;

    rc = ipc_get_msg(chan, &msg_inf);
    if (rc) {
        return rc;
    }

    for (uint32_t i = 0; i < num_iov; i++) {
        max_sz += iov[i].iov_len;
    }

    if (msg_inf.len < min_sz || msg_inf.len > max_sz) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct ipc_msg_kern msg = {
                .iov = iov,
                .num_iov = num_iov,
                .handles = handles,
                .num_handles = num_handles,
        };
        rc = ipc_read_msg(chan, msg_inf.id, 0, &msg);
    }

    ipc_put_msg(chan, msg_inf.id);
    return rc;
}
