/*
 * Copyright 2019 The Android Open Source Project
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

#define TLOG_TAG "confirmationui"

#ifdef WITH_HANDLE_PROT
#include <lib/handle_prot/handle_prot.h>
#endif
#include <lib/keymaster/keymaster.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <lk/macros.h>
#include <sys/mman.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <memory>

#include "ipc.h"
#include "trusty_operation.h"

struct chan_ctx {
    void* shm_base;
    size_t shm_len;
    std::unique_ptr<TrustyOperation> op;
};

static inline bool is_inited(struct chan_ctx* ctx) {
    return ctx->shm_base;
}

static bool get_auth_token_key(teeui::AuthTokenKey& authKey) {
    long rc = keymaster_open();
    bool result = false;

    if (rc < 0) {
        return false;
    }

    keymaster_session_t session = (keymaster_session_t)rc;
    uint8_t* key = nullptr;
    uint32_t local_length = 0;
    rc = keymaster_get_auth_token_key(session, &key, &local_length);
    keymaster_close(session);

    if (rc == NO_ERROR) {
        if (local_length == teeui::kAuthTokenKeySize) {
            /* Success  - store the key */
            memcpy(authKey.data(), key, teeui::kAuthTokenKeySize);
            result = true;
        }

        free(key);
    }

    return result;
}

struct __attribute__((__packed__)) confirmationui_req {
    struct confirmationui_hdr hdr;
    union {
        struct confirmationui_init_req init_args;
        struct confirmationui_msg_args msg_args;
    };
};

static int confirmationui_recv(handle_t chan,
                               confirmationui_req* req,
                               handle_t* h) {
    int rc;
    ipc_msg_info msg_info;
    uint32_t max_num_handles = h ? 1 : 0;
    struct iovec iov = {
            .iov_base = req,
            .iov_len = sizeof(*req),
    };
    struct ipc_msg ipc_msg = {
            .num_iov = 1,
            .iov = &iov,
            .num_handles = max_num_handles,
            .handles = h,
    };

    rc = get_msg(chan, &msg_info);
    if (rc != NO_ERROR) {
        TLOGE("Failed to get message (%d)\n", rc);
        return rc;
    }

    if (msg_info.len > sizeof(*req)) {
        TLOGE("Message is too long (%zd)\n", msg_info.len);
        rc = ERR_BAD_LEN;
        goto out;
    }

    if (msg_info.num_handles > max_num_handles) {
        TLOGE("Message has too many handles (%u)\n", msg_info.num_handles);
        rc = ERR_TOO_BIG;
        goto out;
    }

    rc = read_msg(chan, msg_info.id, 0, &ipc_msg);

out:
    put_msg(chan, msg_info.id);
    return rc;
}

static int handle_init(handle_t chan,
                       handle_t shm_handle,
                       uint32_t shm_len,
                       struct chan_ctx* ctx) {
    int rc;
    struct confirmationui_hdr hdr;

    if (is_inited(ctx)) {
        TLOGE("TA is already initialized.\n");
        return ERR_BAD_STATE;
    }

    if (shm_len > CONFIRMATIONUI_MAX_MSG_SIZE) {
        TLOGE("Shared memory too long\n");
        return ERR_BAD_LEN;
    }
#ifdef WITH_HANDLE_PROT
    rc = handle_prot_secure(shm_handle, false);
    if (rc < 0) {
        TLOGE("Shared memory should be non-secure, rc=%d\n", rc);
        return rc;
    }

    rc = handle_prot_prot_id(shm_handle, 0);
    if (rc < 0) {
        TLOGE("Shared memory prot Id invalid, rc=%d.\n", rc);
        return rc;
    }
#endif
    void* shm_base = mmap(0, shm_len, PROT_READ | PROT_WRITE, 0, shm_handle, 0);
    if (shm_base == MAP_FAILED) {
        TLOGE("Failed to mmap() handle\n");
        return ERR_BAD_HANDLE;
    }

    hdr.cmd = CONFIRMATIONUI_CMD_INIT | CONFIRMATIONUI_RESP_BIT;
    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc != (int)sizeof(hdr)) {
        TLOGE("Failed to send response (%d)\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        goto err;
    }

    ctx->shm_base = shm_base;
    ctx->shm_len = shm_len;
    return NO_ERROR;

err:
    munmap(shm_base, shm_len);
    return rc;
}

static int handle_msg(handle_t chan, uint32_t req_len, struct chan_ctx* ctx) {
    int rc;
    uint8_t msg[CONFIRMATIONUI_MAX_MSG_SIZE];
    uint32_t resp_len = sizeof(msg);
    struct confirmationui_hdr hdr;
    struct confirmationui_msg_args args;

    if (!is_inited(ctx)) {
        TLOGE("TA is not initialized.\n");
        return ERR_BAD_STATE;
    }

    if (req_len > ctx->shm_len) {
        TLOGE("Message too long (%u)\n", req_len);
        return ERR_BAD_LEN;
    }

    assert(req_len <= sizeof(msg));
    memcpy(msg, ctx->shm_base, req_len);

    ctx->op->handleMsg(msg, req_len, ctx->shm_base, &resp_len);

    hdr.cmd = CONFIRMATIONUI_CMD_MSG | CONFIRMATIONUI_RESP_BIT;
    args.msg_len = resp_len;
    rc = tipc_send2(chan, &hdr, sizeof(hdr), &args, sizeof(args));
    if (rc != (int)(sizeof(hdr) + sizeof(args))) {
        TLOGE("Failed to send response (%d)\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

static int on_connect(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p) {
    auto op = std::make_unique<TrustyOperation>();
    if (!op) {
        TLOGE("Failed to allocate TrustyOperation\n");
        return ERR_NO_MEMORY;
    }

    struct chan_ctx* ctx = (struct chan_ctx*)calloc(1, sizeof(*ctx));
    if (!ctx) {
        TLOGE("Failed to allocate channel context\n");
        return ERR_NO_MEMORY;
    }

#if defined(PLATFORM_GENERIC_ARM64)
    /* Use the test key for emulator. */
    constexpr const auto kTestKey = teeui::AuthTokenKey::fill(
            static_cast<uint8_t>(teeui::TestKeyBits::BYTE));
    op->setHmacKey(kTestKey);
#else
    teeui::AuthTokenKey authKey;
    if (get_auth_token_key(authKey) == true) {
        TLOGD("%s, get auth token key successfully\n", __func__);
    } else {
        TLOGE("%s, get auth token key failed\n", __func__);
        /* Abort operation and free all resources. */
        op->abort();
        free(ctx);
        return ERR_GENERIC;
    }
    op->setHmacKey(authKey);
#endif

    ctx->op = std::move(op);
    *ctx_p = ctx;
    return NO_ERROR;
}

static void on_channel_cleanup(void* _ctx) {
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;
    /* Abort operation and free all resources. */
    munmap(ctx->shm_base, ctx->shm_len);
    ctx->op->abort();
    ctx->op.reset();
    free(ctx);
}

static int on_message(const struct tipc_port* port, handle_t chan, void* _ctx) {
    int rc;
    struct confirmationui_req req;
    handle_t shm_handle = INVALID_IPC_HANDLE;
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;

    assert(ctx);

    rc = confirmationui_recv(chan, &req, &shm_handle);
    if (rc < 0) {
        TLOGE("Failed to receive confirmationui request (%d)\n", rc);
        return rc;
    }

    if (rc != (int)sizeof(req)) {
        TLOGE("Receive request of unexpected size(%d)\n", rc);
        rc = ERR_BAD_LEN;
        goto out;
    }

    switch (req.hdr.cmd) {
    case CONFIRMATIONUI_CMD_INIT:
        rc = handle_init(chan, shm_handle, req.init_args.shm_len, ctx);
        goto out;

    case CONFIRMATIONUI_CMD_MSG:
        rc = handle_msg(chan, req.msg_args.msg_len, ctx);
        goto out;

    default:
        TLOGE("cmd 0x%x: unknown command\n", req.hdr.cmd);
        rc = ERR_CMD_UNKNOWN;
        goto out;
    }

out:
    close(shm_handle);
    return rc;
}

static struct tipc_port_acl confirmationui_port_acl = {
        .flags = IPC_PORT_ALLOW_NS_CONNECT,
};

static struct tipc_port confirmationui_port = {
        .name = CONFIRMATIONUI_PORT,
        .msg_max_size = sizeof(confirmationui_req),
        .msg_queue_len = 1,
        .acl = &confirmationui_port_acl,
};

static struct tipc_srv_ops confirmationui_ops = {
        .on_connect = on_connect,
        .on_message = on_message,
        .on_channel_cleanup = on_channel_cleanup,
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    TLOGD("Initializing ConfirmationUI app\n");

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    rc = tipc_add_service(hset, &confirmationui_port, 1, 1,
                          &confirmationui_ops);
    if (rc != NO_ERROR) {
        return rc;
    }

    return tipc_run_event_loop(hset);
}
