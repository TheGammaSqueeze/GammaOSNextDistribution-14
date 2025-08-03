/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <trusty_ipc.h>
#include <uapi/err.h>

#include "gatekeeper_ipc.h"
#include "trusty_gatekeeper.h"

using namespace gatekeeper;
TrustyGateKeeper* device;

class SessionManager {
public:
    SessionManager(long* err) { *err = device->OpenSession(); }
    ~SessionManager() { device->CloseSession(); }
};

static gatekeeper_error_t tipc_err_to_gatekeeper_err(long tipc_err) {
    switch (tipc_err) {
    case NO_ERROR:
        return ERROR_NONE;
    case ERR_BAD_LEN:
    case ERR_NOT_VALID:
    case ERR_NOT_IMPLEMENTED:
    case ERR_NOT_SUPPORTED:
        return ERROR_INVALID;
    default:
        return ERROR_UNKNOWN;
    }
}

template <typename Request, typename Response>
static gatekeeper_error_t exec_cmd(void (GateKeeper::*operation)(const Request&,
                                                                 Response*),
                                   uint8_t* in_buf,
                                   uint32_t in_size,
                                   UniquePtr<uint8_t[]>* out_buf,
                                   uint32_t* out_size) {
    long rc;
    SessionManager sm(&rc);
    if (rc != NO_ERROR)
        return tipc_err_to_gatekeeper_err(rc);

    Request req;
    gatekeeper_error_t err = req.Deserialize(in_buf, in_buf + in_size);
    if (err != ERROR_NONE) {
        TLOGE("error (%d) deserializing request\n", err);
        return ERROR_INVALID;
    }

    Response rsp;
    (device->*operation)(req, &rsp);

    if (rsp.error == ERROR_NOT_IMPLEMENTED) {
        return ERROR_NOT_IMPLEMENTED;
    }

    *out_size = rsp.GetSerializedSize();
    if (*out_size > GATEKEEPER_MAX_BUFFER_LENGTH) {
        *out_size = 0;
        TLOGE("response size too large (%d)\n", *out_size);
        return ERROR_UNKNOWN;
    }

    out_buf->reset(new uint8_t[*out_size]);
    if (out_buf->get() == NULL) {
        *out_size = 0;
        return ERROR_UNKNOWN;
    }

    if (rsp.Serialize(out_buf->get(), out_buf->get() + *out_size) !=
        *out_size) {
        TLOGE("error serializing response message\n");
        return ERROR_UNKNOWN;
    }

    return ERROR_NONE;
}

static gatekeeper_error_t handle_request(uint32_t cmd,
                                         uint8_t* in_buf,
                                         uint32_t in_buf_size,
                                         UniquePtr<uint8_t[]>* out_buf,
                                         uint32_t* out_buf_size) {
    switch (cmd) {
    case GK_ENROLL:
        return exec_cmd(&GateKeeper::Enroll, in_buf, in_buf_size, out_buf,
                        out_buf_size);
    case GK_VERIFY:
        return exec_cmd(&GateKeeper::Verify, in_buf, in_buf_size, out_buf,
                        out_buf_size);
    case GK_DELETE_USER:
        return exec_cmd(&GateKeeper::DeleteUser, in_buf, in_buf_size, out_buf,
                        out_buf_size);
    case GK_DELETE_ALL_USERS:
        return exec_cmd(&GateKeeper::DeleteAllUsers, in_buf, in_buf_size,
                        out_buf, out_buf_size);
    default:
        return ERROR_INVALID;
    }
}

static gatekeeper_error_t send_response(handle_t chan,
                                        uint32_t cmd,
                                        uint8_t* out_buf,
                                        uint32_t out_buf_size) {
    struct gatekeeper_message gk_msg = {cmd | GK_RESP_BIT, {}};
    struct iovec iov[2] = {
            {&gk_msg, sizeof(gk_msg)},
            {out_buf, out_buf_size},
    };
    ipc_msg_t msg = {2, iov, 0, NULL};

    /* send message back to the caller */
    long rc = send_msg(chan, &msg);

    // fatal error
    if (rc < 0) {
        TLOGE("failed (%ld) to send_msg for chan (%d)\n", rc, chan);
        return tipc_err_to_gatekeeper_err(rc);
    }

    return ERROR_NONE;
}

static gatekeeper_error_t send_error_response(handle_t chan,
                                              uint32_t cmd,
                                              gatekeeper_error_t err) {
    GateKeeperMessage msg(err);
    size_t serialized_size = msg.GetSerializedSize();
    uint8_t* out_buf = new uint8_t[serialized_size];
    if (out_buf == NULL) {
        return ERROR_UNKNOWN;
    }

    msg.Serialize(out_buf, out_buf + serialized_size);
    gatekeeper_error_t rc = send_response(chan, cmd, out_buf, serialized_size);

    delete[] out_buf;
    return rc;
}

static gatekeeper_error_t handle_msg(handle_t chan) {
    /* get message info */
    ipc_msg_info_t msg_inf;

    long rc = get_msg(chan, &msg_inf);
    if (rc == ERR_NO_MSG)
        return ERROR_NONE; /* no new messages */

    // fatal error
    if (rc != NO_ERROR) {
        TLOGE("failed (%ld) to get_msg for chan (%d), closing connection\n", rc,
              chan);
        return tipc_err_to_gatekeeper_err(rc);
    }

    /* TODO: handle heap allocation failure
     * and retire the message on failure too
     */
    UniquePtr<uint8_t[]> msg_buf(new uint8_t[msg_inf.len]);

    /* read msg content */
    struct iovec iov = {msg_buf.get(), msg_inf.len};
    ipc_msg_t msg = {1, &iov, 0, NULL};

    rc = read_msg(chan, msg_inf.id, 0, &msg);

    // retire the message (note msg_inf.id becomes invalid after put_msg)
    put_msg(chan, msg_inf.id);

    if (rc < 0) {
        TLOGE("failed to read msg (%ld) for chan (%d)\n", rc, chan);
        return tipc_err_to_gatekeeper_err(rc);
    }

    if (((size_t)rc) < sizeof(gatekeeper_message)) {
        TLOGE("invalid message of size (%zu) for chan (%d)\n", (size_t)rc,
              chan);
        return ERROR_INVALID;
    }

    /* get request command */
    gatekeeper_message* gk_msg =
            reinterpret_cast<struct gatekeeper_message*>(msg_buf.get());

    UniquePtr<uint8_t[]> out_buf;
    uint32_t out_buf_size = 0;
    gatekeeper_error_t err = handle_request(
            gk_msg->cmd, gk_msg->payload,
            msg_inf.len - sizeof(gatekeeper_message), &out_buf, &out_buf_size);

    if (err != ERROR_NONE) {
        TLOGE("unable (%ld) to handle request\n", rc);
        return send_error_response(chan, gk_msg->cmd, err);
    }

    err = send_response(chan, gk_msg->cmd, out_buf.get(), out_buf_size);

    if (err != ERROR_NONE) {
        TLOGE("unable (%ld) to send response\n", rc);
    }

    return err;
}

static void gatekeeper_handle_port(uevent_t* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_HUP) ||
        (ev->event & IPC_HANDLE_POLL_MSG) ||
        (ev->event & IPC_HANDLE_POLL_SEND_UNBLOCKED)) {
        /* should never happen with port handles */
        TLOGE("error event (0x%x) for port (%d)\n", ev->event, ev->handle);
        abort();
    }

    uuid_t peer_uuid;
    if (ev->event & IPC_HANDLE_POLL_READY) {
        /* incoming connection: accept it */
        int rc = accept(ev->handle, &peer_uuid);
        if (rc < 0) {
            TLOGE("failed (%d) to accept on port %d\n", rc, ev->handle);
            return;
        }
    }
}

static void gatekeeper_handle_channel(uevent_t* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_READY)) {
        /* close it as it is in an error state */
        TLOGE("error event (0x%x) for chan (%d)\n", ev->event, ev->handle);
        abort();
    }

    handle_t chan = ev->handle;

    if (ev->event & IPC_HANDLE_POLL_MSG) {
        gatekeeper_error_t rc = handle_msg(chan);
        if (rc != ERROR_NONE) {
            /* report an error and close channel */
            TLOGE("failed (%u) to handle event on channel %d\n", rc,
                  ev->handle);
            close(chan);
        }
    }

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        /* closed by peer. */
        close(chan);
        return;
    }
}

static long gatekeeper_ipc_init(void) {
    int rc;

    /* Initialize service */
    rc = port_create(GATEKEEPER_PORT, 1, GATEKEEPER_MAX_BUFFER_LENGTH,
                     IPC_PORT_ALLOW_NS_CONNECT);
    if (rc < 0) {
        TLOGE("Failed (%d) to create port %s\n", rc, GATEKEEPER_PORT);
    }

    return rc;
}

int main(void) {
    long rc;
    uevent_t event;

    TLOGD("Initializing\n");

    device = new TrustyGateKeeper();

    rc = gatekeeper_ipc_init();
    if (rc < 0) {
        TLOGE("failed (%ld) to initialize gatekeeper", rc);
        return rc;
    }

    handle_t port = (handle_t)rc;

    /* enter main event loop */
    while (true) {
        event.handle = INVALID_IPC_HANDLE;
        event.event = 0;
        event.cookie = NULL;

        rc = wait_any(&event, INFINITE_TIME);
        if (rc < 0) {
            TLOGE("wait_any failed (%ld)\n", rc);
            break;
        }

        if (rc == NO_ERROR) { /* got an event */
            if (event.handle == port) {
                gatekeeper_handle_port(&event);
            } else {
                gatekeeper_handle_channel(&event);
            }
        }
    }

    return 0;
}
