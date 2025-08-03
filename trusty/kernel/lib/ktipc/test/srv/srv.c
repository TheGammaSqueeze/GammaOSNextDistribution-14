/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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

#include <err.h>
#include <inttypes.h>
#include <ktipc_test.h>
#include <lib/ktipc/ktipc.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/uuid.h>
#include <lk/init.h>
#include <lk/list.h>
#include <lk/trace.h>
#include <string.h>

struct chan_ctx {
    struct list_node queued_msgs;
};

struct queued_msg {
    struct list_node node;
    size_t len;
    char buf[KTIPC_TEST_MAX_MSG_SIZE];
};

static uint8_t echo_buf[KTIPC_TEST_MAX_MSG_SIZE];
static uint32_t close_counter = 0;

static int queue_buffer(struct chan_ctx* ctx, const uint8_t* buf, size_t len) {
    struct queued_msg* qmsg;

    qmsg = calloc(1, sizeof(struct queued_msg));
    if (!qmsg) {
        TRACEF("%s: failed to allocate queued message\n", __func__);
        return ERR_NO_MEMORY;
    }

    qmsg->len = len;
    memcpy(qmsg->buf, buf, len);
    list_add_tail(&ctx->queued_msgs, &qmsg->node);

    return NO_ERROR;
}

static int test_handle_msg(const struct ktipc_port* port,
                           struct handle* chan,
                           void* ctx_v) {
    int rc;
    struct chan_ctx* ctx = ctx_v;
    struct ktipc_test_req* req = (struct ktipc_test_req*)echo_buf;

    rc = ktipc_recv(chan, sizeof(*req), req, KTIPC_TEST_MAX_MSG_SIZE);
    if (rc < 0) {
        TRACEF("%s: failed (%d) to read message\n", __func__, rc);
        return rc;
    }

    switch (req->cmd) {
    case KTIPC_TEST_CMD_NOP:
        break;

    case KTIPC_TEST_CMD_ECHO: {
        size_t len = rc - sizeof(*req);
        if (list_is_empty(&ctx->queued_msgs)) {
            /* Queue is empty, we can send this message directly */
            rc = ktipc_send(chan, req->payload, len);
        } else {
            /* There are other messages in the queue, add this one to the end */
            rc = ERR_NOT_ENOUGH_BUFFER;
        }

        if (rc < 0) {
            if (rc == ERR_NOT_ENOUGH_BUFFER) {
                TRACEF("%s: queuing message %zd\n", __func__, len);
                return queue_buffer(ctx, req->payload, len);
            } else {
                TRACEF("%s: failed (%d) to send echo\n", __func__, rc);
                return rc;
            }
        }
        break;
    }

    case KTIPC_TEST_CMD_CLOSE:
        /* We want to close our end of the connection, and this does it */
        return ERR_INVALID_ARGS;

    case KTIPC_TEST_CMD_READ_CLOSE_COUNTER:
        rc = ktipc_send(chan, &close_counter, sizeof(close_counter));
        if (rc < 0) {
            TRACEF("%s: failed (%d) to send close counter\n", __func__, rc);
            return rc;
        }
        break;

    default:
        TRACEF("%s: unknown test command: %" PRIu32 "\n", __func__, req->cmd);
        return ERR_CMD_UNKNOWN;
    }

    return NO_ERROR;
}

static int test_handle_connect(const struct ktipc_port* port,
                               struct handle* chan,
                               const struct uuid* peer,
                               void** ctx_p) {
    struct chan_ctx* ctx = calloc(1, sizeof(*ctx));
    if (!ctx) {
        TRACEF("%s: failed to allocate context\n", __func__);
        return ERR_NO_MEMORY;
    }

    list_initialize(&ctx->queued_msgs);
    *ctx_p = ctx;

    return NO_ERROR;
}

static void test_handle_channel_cleanup(void* ctx_v) {
    struct chan_ctx* ctx = ctx_v;
    struct queued_msg* qmsg;
    struct queued_msg* temp;

    close_counter++;

    list_for_every_entry_safe(&ctx->queued_msgs, qmsg, temp, struct queued_msg,
                              node) {
        free(qmsg);
    }

    free(ctx);
}

static int test_handle_send_unblocked(const struct ktipc_port* port,
                                      struct handle* chan,
                                      void* ctx_v) {
    int rc;
    struct chan_ctx* ctx = ctx_v;
    struct queued_msg* qmsg;
    struct queued_msg* temp;

    list_for_every_entry_safe(&ctx->queued_msgs, qmsg, temp, struct queued_msg,
                              node) {
        TRACEF("%s: sending queued message %zd\n", __func__, qmsg->len);
        rc = ktipc_send(chan, qmsg->buf, qmsg->len);
        if (rc < 0) {
            if (rc == ERR_NOT_ENOUGH_BUFFER) {
                break;
            } else {
                TRACEF("%s: failed (%d) to send echo\n", __func__, rc);
                return rc;
            }
        }

        list_delete(&qmsg->node);
        free(qmsg);
    }

    return NO_ERROR;
}

static int connecterr_handle_connect(const struct ktipc_port* port,
                                     struct handle* chan,
                                     const struct uuid* peer,
                                     void** ctx_p) {
    TRACEF("%s: closing channel\n", __func__);
    return ERR_INVALID_ARGS;
}

static int nop_handle_msg(const struct ktipc_port* port,
                          struct handle* chan,
                          void* ctx_v) {
    return NO_ERROR;
}

static void nop_handle_channel_cleanup(void* ctx_v) {}

/* Operation structures for this server */
const static struct ktipc_srv_ops test_srv_ops = {
        .on_connect = test_handle_connect,
        .on_message = test_handle_msg,
        .on_channel_cleanup = test_handle_channel_cleanup,
        .on_send_unblocked = test_handle_send_unblocked,
};

const static struct ktipc_srv_ops connecterr_srv_ops = {
        .on_connect = connecterr_handle_connect,
        .on_message = nop_handle_msg,
        .on_channel_cleanup = nop_handle_channel_cleanup,
};

/* UUID structures for this server */
const static struct uuid* valid_uuids[] = {
        &kernel_uuid,
};

const static struct uuid* bad_uuids[] = {
        &zero_uuid,
};

/* Ports for this server */
const static struct ktipc_port_acl test_srv_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = valid_uuids,
        .uuid_num = countof(valid_uuids),
        .extra_data = NULL,
};

const static struct ktipc_port test_srv_port = {
        .name = KTIPC_TEST_SRV_PORT,
        .uuid = &kernel_uuid,
        .msg_max_size = KTIPC_TEST_MAX_MSG_SIZE,
        .msg_queue_len = 1,
        .acl = &test_srv_port_acl,
        .priv = NULL,
};

const static struct ktipc_port_acl blocked_srv_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = bad_uuids,
        .uuid_num = countof(bad_uuids),
        .extra_data = NULL,
};

const static struct ktipc_port blocked_srv_port = {
        .name = KTIPC_TEST_SRV_PORT ".blocked",
        .uuid = &kernel_uuid,
        .msg_max_size = KTIPC_TEST_MAX_MSG_SIZE,
        .msg_queue_len = 1,
        .acl = &blocked_srv_port_acl,
        .priv = NULL,
};

const static struct ktipc_port_acl connecterr_srv_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = valid_uuids,
        .uuid_num = countof(valid_uuids),
        .extra_data = NULL,
};

const static struct ktipc_port connecterr_srv_port = {
        .name = KTIPC_TEST_SRV_PORT ".connecterr",
        .uuid = &kernel_uuid,
        .msg_max_size = KTIPC_TEST_MAX_MSG_SIZE,
        .msg_queue_len = 1,
        .acl = &connecterr_srv_port_acl,
        .priv = NULL,
};

static struct ktipc_server ktipc_test_server =
        KTIPC_SERVER_INITIAL_VALUE(ktipc_test_server, "ktipc_test_server");

static void ktipc_test_server_init(uint lvl) {
    int rc;

    rc = ktipc_server_start(&ktipc_test_server);
    if (rc < 0) {
        TRACEF("Failed (%d) to start ktipc_test_srv server\n", rc);
        return;
    }

    rc = ktipc_server_add_port(&ktipc_test_server, &test_srv_port,
                               &test_srv_ops);
    if (rc < 0) {
        TRACEF("Failed (%d) to create echo service port\n", rc);
    }

    rc = ktipc_server_add_port(&ktipc_test_server, &blocked_srv_port,
                               &test_srv_ops);
    if (rc < 0) {
        TRACEF("Failed (%d) to create blocked service port\n", rc);
    }

    rc = ktipc_server_add_port(&ktipc_test_server, &connecterr_srv_port,
                               &connecterr_srv_ops);
    if (rc < 0) {
        TRACEF("Failed (%d) to create connecterr service port\n", rc);
    }
}

LK_INIT_HOOK(ktipc_test_server_init,
             ktipc_test_server_init,
             LK_INIT_LEVEL_APPS - 1)
