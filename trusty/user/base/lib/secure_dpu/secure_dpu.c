/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TLOG_TAG "secure_dpu"

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <interface/secure_dpu/secure_dpu.h>
#include <lib/secure_dpu/secure_dpu.h>

struct secure_dpu_ctx {
    /*
     * This pointer is passed from user.
     * Update this pointer when connecting / disconnecting.
     */
    handle_t* chan;
};

static struct secure_dpu_ctx ctx;

static struct tipc_port_acl acl = {
    .flags = IPC_PORT_ALLOW_NS_CONNECT,
};
static struct tipc_port port = {
    .name = SECURE_DPU_PORT_NAME,
    .msg_max_size = SECURE_DPU_MAX_MSG_SIZE,
    .msg_queue_len = 1,
    .acl = &acl,
    .priv = &ctx,
};

static int handle_allocate_buffer_resp(handle_t chan,
                                       size_t buffer_len,
                                       struct secure_dpu_buf_info* buf_info) {
    int rc = NO_ERROR;
    struct uevent evt;
    struct secure_dpu_resp hdr;
    struct secure_dpu_allocate_buffer_resp resp;
    handle_t buf_handle;

    struct iovec iov[] = {
        {
            .iov_base = &hdr,
            .iov_len = sizeof(hdr),
        },
        {
            .iov_base = &resp,
            .iov_len = sizeof(resp),
        },
    };

    struct ipc_msg msg = {
            .iov = iov,
            .num_iov = countof(iov),
            .handles = &buf_handle,
            .num_handles = 1,
    };

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Error waiting for response (%d)\n", rc);
        return rc;
    }

    struct ipc_msg_info msg_inf;
    rc = get_msg(chan, &msg_inf);
    if (rc) {
        return rc;
    }

    if (msg_inf.num_handles != 1) {
        TLOGE("Message had no handles\n");
        return ERR_INVALID_ARGS;
    }

    rc = read_msg(chan, msg_inf.id, 0, &msg);
    put_msg(chan, msg_inf.id);
    if (rc != (int)(sizeof(hdr) + sizeof(resp))) {
        TLOGE("Read length does not match\n");
        close(buf_handle);
        return ERR_BAD_LEN;
    }

    if (hdr.cmd != (SECURE_DPU_CMD_ALLOCATE_BUFFER | SECURE_DPU_CMD_RESP_BIT)) {
        close(buf_handle);
        return ERR_CMD_UNKNOWN;
    }

    if (hdr.status != SECURE_DPU_ERROR_OK) {
        TLOGE("Failed SECURE_DPU_CMD_ALLOCATE_BUFFER (%d)\n", hdr.status);
        close(buf_handle);
        return ERR_GENERIC;
    }

    if ((size_t)resp.buffer_len < buffer_len) {
        TLOGE("Not allocated enough buffer length, "
              "requested (%zu), allocated (%zu)\n", (size_t)buffer_len,
                                                    (size_t)resp.buffer_len);
        close(buf_handle);
        return ERR_NOT_ENOUGH_BUFFER;
    }

    buf_info->handle = buf_handle;
    buf_info->len = (size_t)resp.buffer_len;

    return NO_ERROR;
}

int secure_dpu_allocate_buffer(handle_t chan,
                               size_t buffer_len,
                               struct secure_dpu_buf_info* buf_info) {

    int rc;
    struct secure_dpu_req hdr;
    struct secure_dpu_allocate_buffer_req args;
    if (!buf_info) {
        TLOGE("Invalid arguments to allocate DPU buffer\n");
        return ERR_INVALID_ARGS;
    }
    if (chan == INVALID_IPC_HANDLE) {
        TLOGE("Channel is not ready\n");
        return ERR_NOT_READY;
    }

    hdr.cmd = SECURE_DPU_CMD_ALLOCATE_BUFFER;
    args.buffer_len = (uint64_t)buffer_len;

    rc = tipc_send2(chan, &hdr, sizeof(hdr), &args, sizeof(args));
    if (rc != (int)(sizeof(hdr) + sizeof(args))) {
        TLOGE("Failed to send SECURE_DPU_CMD_ALLOCATE_BUFFER request (%d)\n", rc);
        return rc;
    }

    rc = handle_allocate_buffer_resp(chan, buffer_len, buf_info);
    if (rc < 0) {
        TLOGE("Failed to handle allocate buffer\n");
        return rc;
    }
    return rc;
}

int secure_dpu_release_buffer(struct secure_dpu_buf_info* buf_info) {
    if (!buf_info) {
        TLOGE("Invalid arguments to release DPU buffer\n");
        return ERR_INVALID_ARGS;
    }

    close(buf_info->handle);

    return NO_ERROR;
}

static int handle_start_secure_display_resp(handle_t chan) {
    int rc;
    struct uevent evt;
    struct secure_dpu_resp hdr;

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Error waiting for response (%d)\n", rc);
        return rc;
    }

    rc = tipc_recv1(chan, sizeof(hdr), &hdr, sizeof(hdr));
    if (rc != sizeof(hdr)) {
        TLOGE("Failed to receive SECURE_DPU_CMD_START_SECURE_DISPLAY response (%d)\n", rc);
        return rc;
    }

    if (hdr.cmd != (SECURE_DPU_CMD_START_SECURE_DISPLAY | SECURE_DPU_CMD_RESP_BIT)) {
        return ERR_CMD_UNKNOWN;
    }

    if (hdr.status != SECURE_DPU_ERROR_OK) {
        TLOGE("Failed SECURE_DPU_CMD_START_SECURE_DISPLAY (%d)\n", hdr.status);
        return ERR_GENERIC;
    }

    return NO_ERROR;
}

int secure_dpu_start_secure_display(handle_t chan) {
    int rc;
    struct secure_dpu_req hdr;

    if (chan == INVALID_IPC_HANDLE) {
        TLOGE("Invalid arguments to start display\n");
        return ERR_INVALID_ARGS;
    }

    hdr.cmd = SECURE_DPU_CMD_START_SECURE_DISPLAY;

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc != (int)(sizeof(hdr))) {
        TLOGE("Failed to send SECURE_DPU_CMD_START_SECURE_DISPLAY request (%d)\n", rc);
        return rc;
    }

    return handle_start_secure_display_resp(chan);
}

static int handle_stop_secure_display_resp(handle_t chan) {
    int rc;
    struct uevent evt;
    struct secure_dpu_resp hdr;

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Error waiting for response (%d)\n", rc);
        return rc;
    }

    rc = tipc_recv1(chan, sizeof(hdr), &hdr, sizeof(hdr));
    if (rc != sizeof(hdr)) {
        TLOGE("Failed to receive SECURE_DPU_CMD_STOP_SECURE_DISPLAY response (%d)\n", rc);
        return rc;
    }

    if (hdr.cmd != (SECURE_DPU_CMD_STOP_SECURE_DISPLAY | SECURE_DPU_CMD_RESP_BIT)) {
        return ERR_CMD_UNKNOWN;
    }

    if (hdr.status != SECURE_DPU_ERROR_OK) {
        TLOGE("Failed SECURE_DPU_CMD_STOP_SECURE_DISPLAY (%d)\n", hdr.status);
        return ERR_GENERIC;
    }

    return NO_ERROR;
}

int secure_dpu_stop_secure_display(handle_t chan) {
    int rc;
    struct secure_dpu_req hdr;

    if (chan == INVALID_IPC_HANDLE) {
        TLOGE("Invalid arguments to stop display\n");
        return ERR_INVALID_ARGS;
    }

    hdr.cmd = SECURE_DPU_CMD_STOP_SECURE_DISPLAY;

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc != (int)(sizeof(hdr))) {
        TLOGE("Failed to send SECURE_DPU_CMD_STOP_SECURE_DISPLAY request (%d)\n", rc);
        return rc;
    }

    return handle_stop_secure_display_resp(chan);
}

/* Default message handler, not being used for normal case */
static int secure_dpu_on_message(const struct tipc_port* port,
                                 handle_t chan,
                                 void* _ctx) {
    /* Not expect any incoming message to this default handler */
    return ERR_CMD_UNKNOWN;
}

static int secure_dpu_on_connect(const struct tipc_port* port,
                                 handle_t chan,
                                 const struct uuid* peer,
                                 void** ctx_p) {
    struct secure_dpu_ctx* priv = (struct secure_dpu_ctx*)port->priv;

    /* WORKAROUND(b/267744475):
     * The secure display is a limited resource. This means only one client
     * can have an open session at a time.
     * Ideally the port's max_chan_cnt = 1 so we don't have to handle this
     * case, but a stuck port or remote process could 'jam' the port / TA.
     * So we accept the newest connection and drop anything previous.
     */
    if (*(priv->chan) != INVALID_IPC_HANDLE) {
        TLOGE("New connection while connected, closing old\n");
        close(*(priv->chan));
    }

    /* Update the handle to user provided pointer */
    *(priv->chan) = chan;

    return NO_ERROR;
}

void secure_dpu_on_disconnect(const struct tipc_port* port,
                              handle_t chan,
                              void* ctx) {
    struct secure_dpu_ctx* priv = (struct secure_dpu_ctx*)port->priv;

    assert(priv->chan);
    *(priv->chan) = INVALID_IPC_HANDLE;
}

int add_secure_dpu_service(struct tipc_hset* hset, handle_t* chan) {
    if (!hset || !chan) {
        return ERR_INVALID_ARGS;
    }

    ctx.chan = chan;

    static struct tipc_srv_ops ops = {
            .on_connect = secure_dpu_on_connect,
            .on_disconnect = secure_dpu_on_disconnect,
            .on_message = secure_dpu_on_message,
    };

    return tipc_add_service(hset, &port, 1 /* num_ports */,
                            2 /* max_chan_cnt */, &ops);
}
