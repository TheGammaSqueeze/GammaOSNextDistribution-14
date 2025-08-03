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

#define TLOG_TAG "secure_fb_ipc"

#include <lib/secure_fb/secure_fb.h>

#include <assert.h>
#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <lk/compiler.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

struct secure_fb_session {
    handle_t chan;
    size_t next_fb;
    size_t num_fbs;
    struct secure_fb_desc fbs[SECURE_FB_MAX_FBS];
};

static struct secure_fb_session* new_secure_fb_session(void) {
    struct secure_fb_session* s = calloc(1, sizeof(*s));
    if (s == NULL) {
        return NULL;
    }

    s->chan = INVALID_IPC_HANDLE;
    return s;
}

static void free_secure_fb_session(struct secure_fb_session* s) {
    for (size_t i = 0; i < SECURE_FB_MAX_FBS; ++i) {
        uint8_t** buffer = &s->fbs[i].fb_info.buffer;
        if (*buffer) {
            munmap(*buffer, s->fbs[i].fb_info.size);
            *buffer = NULL;
        }
    }
    free(s);
}

static struct secure_fb_session* new_connected_session(uint32_t idx) {
    char port_name[SECURE_FB_MAX_PORT_NAME_SIZE] = {0};
    struct secure_fb_session* s;
    int rc;

    if (idx >= SECURE_FB_MAX_INST) {
        TLOGE("Invalid index %" PRIu32 "\n", idx);
        return NULL;
    }

    int n = snprintf(port_name, sizeof(port_name), "%s.%d", SECURE_FB_PORT_NAME,
                     idx);
    if (n != SECURE_FB_MAX_PORT_NAME_SIZE - 1) {
        TLOGE("Failed to create port name\n");
        return NULL;
    }

    s = new_secure_fb_session();
    if (s == NULL) {
        TLOGE("Failed to create session\n");
        return NULL;
    }

    rc = tipc_connect(&s->chan, port_name);
    if (rc != NO_ERROR) {
        TLOGE("Failed to connect to \"%s_%d\" (%d)\n", SECURE_FB_PORT_NAME, idx,
              rc);
        free_secure_fb_session(s);
        return NULL;
    }

    return s;
}

static void free_connected_session(struct secure_fb_session* s) {
    if (s->chan != INVALID_IPC_HANDLE) {
        close(s->chan);
    }
    free_secure_fb_session(s);
}

static int await_resp(handle_t chan, struct ipc_msg* msg) {
    int rc;
    uevent_t event;

    rc = wait(chan, &event, INFINITE_TIME);
    if (rc < 0) {
        TLOGE("Failed to wait for response (%d)\n", rc);
        return rc;
    }

    ipc_msg_info_t msg_info;
    rc = get_msg(chan, &msg_info);
    if (rc != 0) {
        TLOGE("Failed to get_msg (%d)\n", rc);
        return rc;
    }

    rc = read_msg(chan, msg_info.id, 0, msg);
    put_msg(chan, msg_info.id);
    return rc;
}

static int mmap_fbs(struct secure_fb_desc* fbs,
                    size_t num_fbs,
                    handle_t* handles) {
    struct secure_fb_desc* fb;

    for (size_t i = 0; i < num_fbs; i++) {
        fb = &fbs[i];
        fb->fb_info.buffer =
                mmap(NULL, fb->fb_info.size, PROT_READ | PROT_WRITE, 0,
                     handles[fb->handle_index], fb->offset);
        if (fb->fb_info.buffer == MAP_FAILED) {
            goto err;
        }
    }
    return NO_ERROR;

err:
    for (size_t i = 0; i < num_fbs; i++) {
        fb = &fbs[i];
        if (fb->fb_info.buffer) {
            munmap(fb->fb_info.buffer, fb->fb_info.size);
        }
        memset(fb, 0, sizeof(*fb));
    }
    return ERR_BAD_HANDLE;
}

static int handle_get_fbs_resp(struct secure_fb_session* s) {
    int rc;
    struct secure_fb_resp hdr;
    struct secure_fb_get_fbs_resp args;
    struct secure_fb_desc fbs[SECURE_FB_MAX_FBS];
    size_t fbs_len;
    handle_t handles[SECURE_FB_MAX_FBS] = {[0 ... SECURE_FB_MAX_FBS - 1] =
                                                   INVALID_IPC_HANDLE};
    struct iovec iovs[] = {
            {
                    .iov_base = &hdr,
                    .iov_len = sizeof(hdr),
            },
            {
                    .iov_base = &args,
                    .iov_len = sizeof(args),
            },
            {
                    .iov_base = fbs,
                    .iov_len = sizeof(fbs),
            },
    };
    struct ipc_msg msg = {
            .num_iov = countof(iovs),
            .iov = iovs,
            .num_handles = SECURE_FB_MAX_FBS,
            .handles = handles,
    };

    rc = await_resp(s->chan, &msg);
    if (rc < 0) {
        return rc;
    }

    if (rc < (int)(sizeof(hdr) + sizeof(args))) {
        return ERR_BAD_LEN;
    }

    fbs_len = sizeof(fbs[0]) * args.num_fbs;

    if (rc != (int)(sizeof(hdr) + sizeof(args) + fbs_len)) {
        if (rc >= 0) {
            return ERR_BAD_LEN;
        }
        return rc;
    }

    if (hdr.cmd != (SECURE_FB_CMD_GET_FBS | SECURE_FB_CMD_RESP_BIT)) {
        return ERR_CMD_UNKNOWN;
    }

    if (hdr.status != SECURE_FB_ERROR_OK) {
        TLOGE("Failed SECURE_FB_CMD_DISPLAY_FB request (%d)\n", hdr.status);
        return ERR_GENERIC;
    }

    rc = mmap_fbs(fbs, args.num_fbs, handles);
    /* Close all received handles. We don't need to keep them around. */
    for (size_t i = 0; i < SECURE_FB_MAX_FBS; ++i) {
        if (handles[i] != INVALID_IPC_HANDLE) {
            close(handles[i]);
        }
    }

    if (rc != NO_ERROR) {
        TLOGE("Failed to mmap() framebuffers (%d)\n", hdr.status);
        return rc;
    }

    for (size_t i = 0; i < (size_t)args.num_fbs; ++i) {
        s->fbs[i] = fbs[i];
    }
    s->num_fbs = args.num_fbs;
    s->next_fb = 0;
    return NO_ERROR;
}

static int get_fbs(struct secure_fb_session* s) {
    int rc;
    struct secure_fb_req req;

    assert(s->chan != INVALID_IPC_HANDLE);

    req.cmd = SECURE_FB_CMD_GET_FBS;
    rc = tipc_send1(s->chan, &req, sizeof(req));
    if (rc != (int)sizeof(req)) {
        TLOGE("Failed to send SECURE_FB_CMD_GET_FBS request (%d)\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return handle_get_fbs_resp(s);
}

static int handle_display_fb_resp(handle_t chan) {
    int rc;
    struct uevent evt;
    struct secure_fb_resp hdr;

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Error waiting for response (%d)\n", rc);
        return rc;
    }

    rc = tipc_recv1(chan, sizeof(hdr), &hdr, sizeof(hdr));
    if (rc < 0) {
        TLOGE("Failed to receive SECURE_FB_CMD_DISPLAY_FB response (%d)\n", rc);
        return rc;
    }

    if (hdr.cmd != (SECURE_FB_CMD_DISPLAY_FB | SECURE_FB_CMD_RESP_BIT)) {
        return ERR_CMD_UNKNOWN;
    }

    if (hdr.status != SECURE_FB_ERROR_OK) {
        TLOGE("Failed SECURE_FB_CMD_DISPLAY_FB (%d)\n", hdr.status);
        return ERR_GENERIC;
    }

    return NO_ERROR;
}

static int display_fb(handle_t chan, uint32_t buffer_id) {
    int rc;
    struct secure_fb_req hdr;
    struct secure_fb_display_fb_req args;

    hdr.cmd = SECURE_FB_CMD_DISPLAY_FB;
    args.buffer_id = buffer_id;

    rc = tipc_send2(chan, &hdr, sizeof(hdr), &args, sizeof(args));
    if (rc != (int)(sizeof(hdr) + sizeof(args))) {
        TLOGE("Failed to send SECURE_FB_CMD_DISPLAY_FB request (%d)\n", rc);
        return rc;
    }

    return handle_display_fb_resp(chan);
}

secure_fb_error secure_fb_open(secure_fb_handle_t* session,
                               struct secure_fb_info* fb_info,
                               uint32_t idx) {
    int rc;
    struct secure_fb_session* s;

    if (!session) {
        return TTUI_ERROR_UNEXPECTED_NULL_PTR;
    }

    s = new_connected_session(idx);
    if (s == NULL) {
        return TTUI_ERROR_NO_SERVICE;
    }

    rc = get_fbs(s);
    if (rc != NO_ERROR) {
        free_connected_session(s);
        return TTUI_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    *fb_info = s->fbs[s->next_fb].fb_info;
    *session = (secure_fb_handle_t)s;
    return TTUI_ERROR_OK;
}

secure_fb_error secure_fb_display_next(secure_fb_handle_t session,
                                       struct secure_fb_info* fb_info) {
    int rc;
    uint32_t buffer_id;
    struct secure_fb_session* s = (struct secure_fb_session*)session;

    if (!fb_info || !s) {
        return TTUI_ERROR_UNEXPECTED_NULL_PTR;
    }

    buffer_id = s->fbs[s->next_fb].buffer_id;
    rc = display_fb(s->chan, buffer_id);
    if (rc != NO_ERROR) {
        return TTUI_ERROR_NO_FRAMEBUFFER;
    }

    s->next_fb = (s->next_fb + 1) % s->num_fbs;
    *fb_info = s->fbs[s->next_fb].fb_info;
    return TTUI_ERROR_OK;
}

void secure_fb_close(secure_fb_handle_t session) {
    int rc;
    struct secure_fb_req req;
    struct secure_fb_session* s = (struct secure_fb_session*)session;

    if (!s || s->chan == INVALID_IPC_HANDLE) {
        return;
    }

    req.cmd = SECURE_FB_CMD_RELEASE;
    rc = tipc_send1(s->chan, &req, sizeof(req));
    if (rc != (int)sizeof(req)) {
        TLOGE("Failed to send SECURE_FB_CMD_RELEASE request (%d)\n", rc);
    }

    free_connected_session(s);
}
