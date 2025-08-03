/*
 * Copyright 2016 The Android Open Source Project
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

#include <UniquePtr.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include "avb_ipc.h"
#include "avb_manager.h"
#include "secure_storage.h"

const char kAvbServiceName[] = "com.android.trusty.avb";
const int kAvbServiceNumBufs = 1;
const int kAvbServiceBufSize = 2048;
const uint32_t kAvbServiceFlags = IPC_PORT_ALLOW_NS_CONNECT;
static bool boot_state_locked = false;

avb::AvbManager* g_avb_manager;

namespace avb {

// Deserializes request in |in_buf|, calls |operation| to execute request,
// and serializes response to |out_buf|. Returns a Trusty error code if there
// is a problem during serializing the response or deserializing the request.
template <typename Request, typename Response>
static int ExecuteCommand(void (AvbManager::*operation)(const Request&,
                                                        Response*),
                          const uint8_t* in_buf,
                          uint32_t in_size,
                          UniquePtr<uint8_t[]>* out_buf,
                          uint32_t* out_size,
                          AvbError* error) {
    Request req;
    int rc = req.Deserialize(in_buf, in_buf + in_size);
    if (rc < 0) {
        TLOGE("Error deserializing request: %d\n", rc);
        return rc;
    }
    Response rsp;
    (g_avb_manager->*operation)(req, &rsp);

    *error = rsp.get_error();
    *out_size = rsp.GetSerializedSize();
    if (*out_size > kAvbServiceBufSize) {
        TLOGE("Response size too large: %d\n", *out_size);
        *out_size = 0;
        return ERR_TOO_BIG;
    }

    if (*out_size == 0) {
        return NO_ERROR;
    }

    out_buf->reset(new uint8_t[*out_size]);
    if (out_buf->get() == nullptr) {
        *out_size = 0;
        return ERR_NO_MEMORY;
    }

    if (rsp.Serialize(out_buf->get(), out_buf->get() + *out_size) !=
        *out_size) {
        TLOGE("Error serializing response message\n");
        return ERR_NOT_VALID;
    }

    return NO_ERROR;
}

// Dispatches command |cmd|. Fulfills request in |in_buf|, and writes response
// to |out_buf|. Returns a Trusty error code if there is a problem serializing
// the response or deserializing the request. Avb specific errors are put in
// |error|.
static int ProcessRequest(uint32_t cmd,
                          const uint8_t* in_buf,
                          uint32_t in_size,
                          UniquePtr<uint8_t[]>* out_buf,
                          uint32_t* out_size,
                          AvbError* error) {
    if (boot_state_locked) {
        switch (cmd) {
        case WRITE_ROLLBACK_INDEX:
        case WRITE_PERMANENT_ATTRIBUTES:
        case WRITE_LOCK_STATE:
            *out_size = 0;
            *error = AvbError::kInvalid;
            return NO_ERROR;
        }
    }
    switch (cmd) {
    case READ_ROLLBACK_INDEX:
        return ExecuteCommand(&AvbManager::ReadRollbackIndex, in_buf, in_size,
                              out_buf, out_size, error);
    case WRITE_ROLLBACK_INDEX:
        return ExecuteCommand(&AvbManager::WriteRollbackIndex, in_buf, in_size,
                              out_buf, out_size, error);
    case AVB_GET_VERSION:
        return ExecuteCommand(&AvbManager::GetVersion, in_buf, in_size, out_buf,
                              out_size, error);
    case READ_PERMANENT_ATTRIBUTES:
        return ExecuteCommand(&AvbManager::ReadPermanentAttributes, in_buf,
                              in_size, out_buf, out_size, error);
    case WRITE_PERMANENT_ATTRIBUTES:
        return ExecuteCommand(&AvbManager::WritePermanentAttributes, in_buf,
                              in_size, out_buf, out_size, error);
    case READ_LOCK_STATE:
        return ExecuteCommand(&AvbManager::ReadLockState, in_buf, in_size,
                              out_buf, out_size, error);
    case WRITE_LOCK_STATE:
        return ExecuteCommand(&AvbManager::WriteLockState, in_buf, in_size,
                              out_buf, out_size, error);
    case LOCK_BOOT_STATE:
        *out_size = 0;
        *error = AvbError::kNone;
        boot_state_locked = true;
        return NO_ERROR;

    default:
        return ERR_NOT_VALID;
    }
}

// Reads the message described by |msg_info| from channel and processes it.
// Writes the response message back to |channel|. Returns a Trusty error code if
// there is any I/O problem.
static int ProcessOneMessage(handle_t channel, const ipc_msg_info_t& msg_info) {
    if (msg_info.len > kAvbServiceBufSize) {
        TLOGE("Message too large on channel %x: %zu", channel, msg_info.len);
        return ERR_TOO_BIG;
    }
    UniquePtr<uint8_t[]> msg_buf(new uint8_t[msg_info.len]);

    struct iovec request_iov = {msg_buf.get(), msg_info.len};
    ipc_msg_t request_msg = {
            1,             // number of iovecs
            &request_iov,  // iovecs pointer
            0,             // number of handles
            nullptr        // handles pointer
    };

    int rc = read_msg(channel, msg_info.id, 0, &request_msg);

    if (rc < 0) {
        TLOGE("Failed to read msg for channel %x: %d\n", channel, rc);
        return rc;
    }

    if (((size_t)rc) < sizeof(avb_message)) {
        TLOGE("Invalid message of size %zu for channel %x\n", (size_t)rc,
              channel);
        return ERR_NOT_VALID;
    }

    // Parse and handle request
    avb_message* avb_request_header =
            reinterpret_cast<struct avb_message*>(msg_buf.get());
    UniquePtr<uint8_t[]> out_buf;
    uint32_t out_size = 0;
    AvbError error;
    rc = ProcessRequest(avb_request_header->cmd, avb_request_header->payload,
                        msg_info.len - sizeof(avb_message), &out_buf, &out_size,
                        &error);
    if (rc < 0) {
        TLOGE("Unable to handle request: %d", rc);
        return rc;
    }

    // Send response message back to caller
    avb_message avb_response_header = {
            .cmd = avb_request_header->cmd | AVB_RESP_BIT,
            .result = error,
            .payload = {},
    };
    struct iovec response_iov[2] = {
            {&avb_response_header, sizeof(avb_response_header)},
            {out_buf.get(), out_size},
    };
    ipc_msg_t response_msg = {
            2,             // number of iovecs
            response_iov,  // iovecs pointer
            0,             // number of handles
            nullptr        // handles pointer
    };
    rc = send_msg(channel, &response_msg);
    if (rc < 0) {
        TLOGE("Failed to send_msg on channel %x: %d\n", channel, rc);
        return rc;
    }
    return NO_ERROR;
}

// Receives all pending messages from |channel| and processes them, sending
// responses as appropriate. Returns a Trusty error code if there is a
// problem with message processing or I/O.
static int ProcessMessages(handle_t channel) {
    while (true) {
        ipc_msg_info_t msg_info;
        int rc = get_msg(channel, &msg_info);
        if (rc == ERR_NO_MSG) {
            break;
        }

        if (rc != NO_ERROR) {
            TLOGE("Failed to get_msg on channel %x: %d\n", channel, rc);
            return rc;
        }

        rc = ProcessOneMessage(channel, msg_info);
        if (rc != NO_ERROR) {
            put_msg(channel, msg_info.id);
            return rc;
        }

        rc = put_msg(channel, msg_info.id);
        if (rc != NO_ERROR) {
            TLOGE("Failed to put_msg on channel %x: %d\n", rc, channel);
            return rc;
        }
    }

    return NO_ERROR;
}

// Handles an |event| on a channel. If it is an incoming message, it is
// processed. Otherwise, the event indicates a channel hangup or error, so
// the channel is closed.
static void HandleChannelEvent(const uevent_t& event) {
    if ((event.event & IPC_HANDLE_POLL_ERROR) ||
        (event.event & IPC_HANDLE_POLL_READY)) {
        // close it as it is in an error state
        TLOGE("Error event on channel %x: 0x%x\n", event.event, event.handle);
        close(event.handle);
    }

    if (event.event & IPC_HANDLE_POLL_HUP) {
        // closed by peer
        close(event.handle);
        return;
    }

    if (event.event & IPC_HANDLE_POLL_MSG) {
        if (ProcessMessages(event.handle) != NO_ERROR) {
            close(event.handle);
        }
    }
}

// Handles an |event| on a port. If it is an incoming connection event,
// the connection is accepted. Any other event is unexpected.
static void HandlePortEvent(const uevent_t& event) {
    if (event.event & IPC_HANDLE_POLL_READY) {
        // incoming connection: accept it
        uuid_t peer_uuid;
        int rc = accept(event.handle, &peer_uuid);
        if (rc < 0) {
            TLOGE("Failed to accept on port %d: %d\n", event.handle, rc);
        }
    } else {
        TLOGE("Unexpected event on port %d: 0x%x\n", event.handle, event.event);
    }
}

}  // namespace avb

int main(void) {
    avb::AvbManager avb_manager(new avb::SecureStorage);
    g_avb_manager = &avb_manager;

    TLOGD("Initializing AVB App\n");

    int rc = port_create(kAvbServiceName, kAvbServiceNumBufs,
                         kAvbServiceBufSize, kAvbServiceFlags);
    if (rc < 0) {
        TLOGE("Failed to initialize AVB app: %d", rc);
        return rc;
    }

    handle_t port = static_cast<handle_t>(rc);

    // enter main event loop
    while (true) {
        uevent_t event;
        event.handle = INVALID_IPC_HANDLE;
        event.event = 0;
        event.cookie = nullptr;
        rc = wait_any(&event, INFINITE_TIME);

        if (rc == NO_ERROR) {  // got an event
            if (event.handle == port) {
                avb::HandlePortEvent(event);
            } else {
                avb::HandleChannelEvent(event);
            }
        } else {
            TLOGE("wait_any failed: %d\n", rc);
        }
    }

    g_avb_manager = nullptr;
    return 0;
}
