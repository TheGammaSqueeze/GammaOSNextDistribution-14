/*
 * Copyright (c) 2021, Google Inc. All rights reserved
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
#include <interface/metrics/consumer.h>
#include <kernel/mutex.h>
#include <lib/dpc.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/trusty_app.h>
#include <lk/init.h>
#include <lk/trace.h>
#include <string.h>
#include <trusty/uuid.h>

#define LOCAL_TRACE (0)

/*
 * Format of the payload is "<UUID>:<app name>", with neither UUID nor app name
 * being null-terminated. However, unlike APP_NAME_MAX_SIZE, UUID_STR_SIZE
 * counts the null character. Hence, the maximum size of an app name is
 * METRICS_MAX_APP_ID_LEN - UUID_STR_SIZE.
 */
static_assert(UUID_STR_SIZE <= METRICS_MAX_APP_ID_LEN);
#define APP_NAME_MAX_SIZE (METRICS_MAX_APP_ID_LEN - UUID_STR_SIZE)

/**
 * enum chan_state - states of the metrics consumer channel event handler
 * CHAN_STATE_WAITING_CHAN_READY:
 *      Inital state of the channel handler. At this point we are waiting for an
 *      IPC_HANDLE_POLL_READY channel event that signifies that metrics consumer
 *      connection is ready for use. After consuming this event, we transition
 *      to %CHAN_STATE_IDLE state.
 * CHAN_STATE_IDLE:
 *      While in this state we (2) can not consume any events from the channel
 *      (1) can only send one message over the channel. Once a message is sent,
 *      we transition to either %CHAN_STATE_WAITING_CRASH_RESP or
 *      %CHAN_STATE_WAITING_EVENT_DROP_RESP depending on what message was sent.
 * CHAN_STATE_WAITING_CRASH_RESP:
 *      In this state we are waiting for a response to a message about an app
 *      crash.  After receiving the response message, we transition to
 *      %CHAN_STATE_IDLE state.
 * CHAN_STATE_WAITING_EVENT_DROP_RESP:
 *      In this state we are waiting for a response to a message about an event
 *      drop. After receiving the response message, we transition to
 *      %CHAN_STATE_IDLE state.
 */
enum chan_state {
    CHAN_STATE_WAITING_CHAN_READY = 0,
    CHAN_STATE_IDLE = 1,
    CHAN_STATE_WAITING_CRASH_RESP = 2,
    CHAN_STATE_WAITING_EVENT_DROP_RESP = 3,
};

struct metrics_ctx {
    struct handle* chan;
    enum chan_state chan_state;
    bool event_dropped;
};

static struct metrics_ctx ctx;
static mutex_t ctx_lock = MUTEX_INITIAL_VALUE(ctx_lock);

static int recv_resp(struct handle* chan, uint32_t cmd) {
    int rc;
    struct ipc_msg_info msg_info;
    struct metrics_resp resp;

    rc = ipc_get_msg(chan, &msg_info);
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) to get message\n", rc);
        return rc;
    }

    struct iovec_kern iov = {
            .iov_base = &resp,
            .iov_len = sizeof(resp),
    };
    struct ipc_msg_kern ipc_msg = {
            .num_iov = 1,
            .iov = &iov,
            .num_handles = 0,
            .handles = NULL,
    };
    rc = ipc_read_msg(chan, msg_info.id, 0, &ipc_msg);
    ipc_put_msg(chan, msg_info.id);

    if (rc < 0) {
        TRACEF("failed (%d) ipc_read_msg().\n", rc);
        return rc;
    }

    if (rc != sizeof(resp)) {
        TRACEF("unexpected number of bytes received: %d.\n", rc);
        return ERR_BAD_LEN;
    }

    if (resp.cmd != (cmd | METRICS_CMD_RESP_BIT)) {
        TRACEF("unknown command received: %u.\n", resp.cmd);
        return ERR_CMD_UNKNOWN;
    }

    if (resp.status != METRICS_NO_ERROR) {
        TRACEF("event report failure: %d.\n", resp.status);
        /* This error is not severe enough to close the connection. */
    }

    return NO_ERROR;
}

static int send_req(struct handle* chan,
                    struct ipc_msg_kern* ipc_msg,
                    size_t total_len) {
    int rc = ipc_send_msg(chan, ipc_msg);
    if (rc < 0) {
        TRACEF("failed (%d) to send message\n", rc);
        return rc;
    }

    if (rc != (int)total_len) {
        TRACEF("unexpected number of bytes sent: %d\n", rc);
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int report_crash(struct handle* chan,
                        struct trusty_app* app,
                        uint32_t crash_reason) {
    int rc;
    struct metrics_req req;
    struct metrics_report_crash_req args;
    char payload[METRICS_MAX_APP_ID_LEN];
    size_t uuid_str_len;
    size_t app_name_len;
    size_t payload_len;
    size_t total_len;

    DEBUG_ASSERT(is_mutex_held(&ctx_lock));

    memset(payload, 0, METRICS_MAX_APP_ID_LEN);

    /* Format of the payload is "<UUID>:<app name>". */
    uuid_to_str(&app->props.uuid, payload);
    uuid_str_len = strnlen(payload, UUID_STR_SIZE);
    assert(uuid_str_len == UUID_STR_SIZE - 1);

    /* Delimiter between UUID value and app name */
    payload[uuid_str_len] = ':';

    app_name_len = strnlen(app->props.app_name, APP_NAME_MAX_SIZE);
    memcpy(&payload[UUID_STR_SIZE], app->props.app_name, app_name_len);
    payload_len = UUID_STR_SIZE + app_name_len;
    assert(payload_len <= METRICS_MAX_APP_ID_LEN);

    req.cmd = METRICS_CMD_REPORT_CRASH;
    args.app_id_len = payload_len;
    args.crash_reason = crash_reason;

    struct iovec_kern iovs[] = {
            {
                    .iov_base = &req,
                    .iov_len = sizeof(req),
            },
            {
                    .iov_base = &args,
                    .iov_len = sizeof(args),
            },
            {
                    .iov_base = payload,
                    .iov_len = payload_len,
            },
    };
    struct ipc_msg_kern ipc_msg = {
            .num_iov = countof(iovs),
            .iov = iovs,
    };

    total_len = sizeof(req) + sizeof(args) + payload_len;
    rc = send_req(chan, &ipc_msg, total_len);
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) report app crash\n", rc);
        return rc;
    }

    return NO_ERROR;
}

static int report_event_drop(struct handle* chan) {
    int rc;
    struct metrics_req req;

    DEBUG_ASSERT(is_mutex_held(&ctx_lock));

    req.cmd = METRICS_CMD_REPORT_EVENT_DROP;
    req.reserved = 0;

    struct iovec_kern iov = {
            .iov_base = &req,
            .iov_len = sizeof(req),
    };
    struct ipc_msg_kern ipc_msg = {
            .num_iov = 1,
            .iov = &iov,
    };

    rc = send_req(chan, &ipc_msg, sizeof(req));
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) report event drop\n", rc);
        return rc;
    }

    return NO_ERROR;
}

static int on_ta_crash(struct trusty_app* app, uint32_t reason) {
    int rc;

    mutex_acquire(&ctx_lock);

    if (ctx.chan_state != CHAN_STATE_IDLE) {
        TRACEF("there is a metrics event still in progress or metrics TA "
               "is unavailable\n");
        ctx.event_dropped = true;
        goto out;
    }

    if (!ctx.chan) {
        TRACEF("failed get metrics consumer channel\n");
        goto out;
    }

    rc = report_crash(ctx.chan, app, reason);
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) report app crash\n", rc);
        goto err;
    }

    ctx.chan_state = CHAN_STATE_WAITING_CRASH_RESP;
    goto out;

err:
    handle_close(ctx.chan);
    ctx.chan = NULL;
out:
    mutex_release(&ctx_lock);
    /*
     * Returning an error here will bring down the kernel. Metrics reporting
     * isn't critical. So, we always return NO_ERROR. If something goes wrong,
     * printing an error should suffice.
     */
    return NO_ERROR;
}

static struct trusty_app_notifier notifier = {
        .crash = on_ta_crash,
};

static void handle_chan(struct dpc* work) {
    int rc;
    uint32_t event;

    mutex_acquire(&ctx_lock);

    event = ctx.chan->ops->poll(ctx.chan, ~0U, true);
    if (event & IPC_HANDLE_POLL_HUP) {
        TRACEF("received IPC_HANDLE_POLL_HUP, closing channel\n");
        goto err;
    }

    switch (ctx.chan_state) {
    case CHAN_STATE_WAITING_CHAN_READY:
        if (!(event & IPC_HANDLE_POLL_READY)) {
            TRACEF("unexpected channel event: 0x%x\n", event);
            goto err;
        }

        ctx.chan_state = CHAN_STATE_IDLE;
        goto out;

    case CHAN_STATE_IDLE:
        TRACEF("unexpected channel event: 0x%x\n", event);
        goto err;

    case CHAN_STATE_WAITING_CRASH_RESP:
        if (!(event & IPC_HANDLE_POLL_MSG)) {
            TRACEF("unexpected channel event: 0x%x\n", event);
            goto err;
        }

        rc = recv_resp(ctx.chan, METRICS_CMD_REPORT_CRASH);
        if (rc != NO_ERROR) {
            TRACEF("failed (%d) receive response\n", rc);
            goto err;
        }

        ctx.chan_state = CHAN_STATE_IDLE;

        if (ctx.event_dropped) {
            rc = report_event_drop(ctx.chan);
            if (rc != NO_ERROR) {
                TRACEF("failed (%d) report event drop\n", rc);
                goto err;
            }
            ctx.chan_state = CHAN_STATE_WAITING_EVENT_DROP_RESP;
            goto out;
        }

        goto out;

    case CHAN_STATE_WAITING_EVENT_DROP_RESP:
        if (!(event & IPC_HANDLE_POLL_MSG)) {
            TRACEF("unexpected channel event: 0x%x\n", event);
            goto err;
        }

        rc = recv_resp(ctx.chan, METRICS_CMD_REPORT_EVENT_DROP);
        if (rc != NO_ERROR) {
            TRACEF("failed (%d) receive response\n", rc);
            goto err;
        }

        ctx.chan_state = CHAN_STATE_IDLE;
        ctx.event_dropped = false;
        goto out;
    }

err:
    handle_close(ctx.chan);
    ctx.chan = NULL;
out:
    mutex_release(&ctx_lock);
}

static struct dpc chan_event_work = {
        .node = LIST_INITIAL_CLEARED_VALUE,
        .cb = handle_chan,
};

static void on_handle_event(struct handle_waiter* waiter) {
    int rc = dpc_enqueue_work(NULL, &chan_event_work, false);
    if (rc != NO_ERROR) {
        TRACEF("failed (%d) to enqueue dpc work\n", rc);
    }
}

static struct handle_waiter waiter = {
        .node = LIST_INITIAL_CLEARED_VALUE,
        .notify_proc = on_handle_event,
};

static void metrics_init(uint level) {
    int rc = ipc_port_connect_async(&kernel_uuid, METRICS_CONSUMER_PORT,
                                    IPC_PORT_PATH_MAX,
                                    IPC_CONNECT_WAIT_FOR_PORT, &ctx.chan);
    if (rc) {
        TRACEF("failed (%d) to connect to port\n", rc);
        goto err_port_connect;
    }

    rc = trusty_register_app_notifier(&notifier);
    if (rc) {
        TRACEF("failed (%d) to register app notifier\n", rc);
        goto err_app_notifier;
    }

    ctx.chan_state = CHAN_STATE_WAITING_CHAN_READY;
    handle_add_waiter(ctx.chan, &waiter);

    return;

err_app_notifier:
    handle_close(ctx.chan);
    ctx.chan = NULL;
err_port_connect:
    return;
}

/* Need to init before (LK_INIT_LEVEL_APPS - 1) to register an app notifier. */
LK_INIT_HOOK(metrics, metrics_init, LK_INIT_LEVEL_APPS - 2);
