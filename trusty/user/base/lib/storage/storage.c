/*
 * Copyright (C) 2015-2016 The Android Open Source Project
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

#define TLOG_TAG "storage_client"
#include <trusty_log.h>

#include <uapi/err.h>

#include <lib/storage/storage.h>

#define TLOGE_APP_NAME(fmt, ...) TLOGE("%s: " fmt, __progname, ##__VA_ARGS__)

#define MAX_CHUNK_SIZE 4040

/* At what delay threshold should wait_infinite_logged() start logging? */
#define WAIT_INFINITE_LOG_THRESHOLD_MSEC 1000

/* Maximum timeout value to use for wait_infinite_logged */
#define WAIT_INFINITE_LOG_MAX_TIMEOUT_MSEC 60000

/* Initialized by __init_libc in ./trusty/musl/src/env/__libc_start_main.c */
extern char* __progname;

static inline file_handle_t make_file_handle(storage_session_t s,
                                             uint32_t fid) {
    return ((uint64_t)s << 32) | fid;
}

static inline storage_session_t _to_session(file_handle_t fh) {
    return (storage_session_t)(fh >> 32);
}

static inline uint32_t _to_handle(file_handle_t fh) {
    return (uint32_t)fh;
}

static inline uint32_t _to_msg_flags(uint32_t opflags) {
    uint32_t msg_flags = 0;

    if (opflags & STORAGE_OP_COMPLETE)
        msg_flags |= STORAGE_MSG_FLAG_TRANSACT_COMPLETE;

    if (opflags & STORAGE_OP_CHECKPOINT) {
        if ((msg_flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) == 0) {
            TLOGE("STORAGE_OP_CHECKPOINT only valid when committing a checkpoint\n");
        }
        msg_flags |= STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT;
    }

    if (opflags & STORAGE_OP_FS_REPAIRED_ACK) {
        msg_flags |= STORAGE_MSG_FLAG_FS_REPAIRED_ACK;
    }

    return msg_flags;
}

static ssize_t check_response(struct storage_msg* msg, ssize_t res) {
    if (res < 0)
        return res;

    if ((size_t)res < sizeof(*msg)) {
        TLOGE("invalid msg length (%zd < %zd)\n", (size_t)res, sizeof(*msg));
        return ERR_IO;
    }

    /* TLOGI("cmd 0x%x: server returned %u\n", msg->cmd, msg->result); */

    switch (msg->result) {
    case STORAGE_NO_ERROR:
        return res - sizeof(*msg);

    case STORAGE_ERR_NOT_FOUND:
        return ERR_NOT_FOUND;

    case STORAGE_ERR_EXIST:
        return ERR_ALREADY_EXISTS;

    case STORAGE_ERR_NOT_VALID:
        return ERR_NOT_VALID;

    case STORAGE_ERR_NOT_ALLOWED:
        return ERR_NOT_ALLOWED;

    case STORAGE_ERR_TRANSACT:
        return ERR_BUSY;

    case STORAGE_ERR_ACCESS:
        return ERR_ACCESS_DENIED;

    case STORAGE_ERR_FS_REPAIRED:
        return ERR_BAD_STATE;

    case STORAGE_ERR_UNIMPLEMENTED:
        TLOGE("cmd 0x%x: is unhandles command\n", msg->cmd);
        return ERR_NOT_IMPLEMENTED;

    case STORAGE_ERR_GENERIC:
        TLOGE("cmd 0x%x: internal server error\n", msg->cmd);
        return ERR_GENERIC;

    default:
        TLOGE("cmd 0x%x: unhandled server response %u\n", msg->cmd,
              msg->result);
    }

    return ERR_IO;
}

int wait_infinite_logged(storage_session_t session,
                         uevent_t* ev,
                         const char* caller) {
    unsigned long wait_time = WAIT_INFINITE_LOG_THRESHOLD_MSEC;

    /* wait for reply */
    int rc;
    do {
        rc = wait(session, ev, wait_time);
        if (rc == ERR_TIMED_OUT) {
            TLOGE_APP_NAME(
                    "Timed out after %ldx milliseconds, retrying; "
                    "called by %s\n",
                    wait_time, caller);
            wait_time = wait_time * 2;
            if (wait_time > WAIT_INFINITE_LOG_MAX_TIMEOUT_MSEC) {
                wait_time = WAIT_INFINITE_LOG_MAX_TIMEOUT_MSEC;
            }
        }
    } while (rc == ERR_TIMED_OUT);

    if (wait_time != WAIT_INFINITE_LOG_THRESHOLD_MSEC) {
        TLOGE_APP_NAME(
                "Finally succeeded (last wait: < %ldx milliseconds); "
                "called by %s\n",
                wait_time, caller);
    }

    return rc;
}

static ssize_t get_response(storage_session_t session,
                            struct iovec* rx_iovs,
                            uint32_t rx_iovcnt)

{
    uevent_t ev;
    struct ipc_msg_info mi;
    struct ipc_msg rx_msg = {
            .iov = rx_iovs,
            .num_iov = rx_iovcnt,
    };

    if (!rx_iovcnt)
        return 0;

    /* wait for reply */
    int rc = wait_infinite_logged(session, &ev, __func__);
    if (rc != NO_ERROR) {
        TLOGE("%s: interrupted waiting for response", __func__);
        return rc;
    }

    rc = get_msg(session, &mi);
    if (rc != NO_ERROR) {
        TLOGE("%s: failed to get_msg (%d)\n", __func__, rc);
        return rc;
    }

    rc = read_msg(session, mi.id, 0, &rx_msg);
    put_msg(session, mi.id);
    if (rc < 0) {
        TLOGE("%s: failed to read msg (%d)\n", __func__, rc);
        return rc;
    }

    if ((size_t)rc != mi.len) {
        TLOGE("%s: partial message read (%zd vs. %zd)\n", __func__, (size_t)rc,
              mi.len);
        return ERR_IO;
    }

    return rc;
}

static int wait_to_send(handle_t session, struct ipc_msg* msg) {
    int rc;
    struct uevent ev;

    rc = wait_infinite_logged(session, &ev, __func__);
    if (rc < 0) {
        TLOGE("failed to wait for outgoing queue to free up\n");
        return rc;
    }

    if (ev.event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
        return send_msg(session, msg);
    }

    if (ev.event & IPC_HANDLE_POLL_MSG) {
        return ERR_BUSY;
    }

    if (ev.event & IPC_HANDLE_POLL_HUP) {
        return ERR_CHANNEL_CLOSED;
    }

    return rc;
}

ssize_t send_reqv(storage_session_t session,
                  struct iovec* tx_iovs,
                  uint32_t tx_iovcnt,
                  struct iovec* rx_iovs,
                  uint32_t rx_iovcnt) {
    ssize_t rc;

    struct ipc_msg tx_msg = {
            .iov = tx_iovs,
            .num_iov = tx_iovcnt,
    };

    rc = send_msg(session, &tx_msg);
    if (rc == ERR_NOT_ENOUGH_BUFFER) {
        rc = wait_to_send(session, &tx_msg);
    }

    if (rc < 0) {
        TLOGE("%s: failed (%d) to send_msg\n", __func__, (int)rc);
        return rc;
    }

    rc = get_response(session, rx_iovs, rx_iovcnt);
    if (rc < 0) {
        TLOGI("%s: failed (%d) to get response\n", __func__, (int)rc);
        return rc;
    }

    return rc;
}

int storage_open_session(storage_session_t* session_p, const char* type) {
    long rc = connect(type, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc < 0) {
        return rc;
    }

    *session_p = (storage_session_t)rc;
    return NO_ERROR;
}

void storage_close_session(storage_session_t session) {
    close(session);
}

int storage_open_file(storage_session_t session,
                      file_handle_t* handle_p,
                      const char* name,
                      uint32_t flags,
                      uint32_t opflags) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_OPEN,
                              .flags = _to_msg_flags(opflags)};
    struct storage_file_open_req req = {.flags = flags};
    struct iovec tx[3] = {{&msg, sizeof(msg)},
                          {&req, sizeof(req)},
                          {(void*)name, strlen(name)}};
    struct storage_file_open_resp rsp = {0};
    struct iovec rx[2] = {{&msg, sizeof(msg)}, {&rsp, sizeof(rsp)}};

    ssize_t rc = send_reqv(session, tx, 3, rx, 2);
    rc = check_response(&msg, rc);
    if (rc < 0)
        return rc;

    if ((size_t)rc != sizeof(rsp)) {
        TLOGE("%s: invalid response length (%zd != %zd)\n", __func__,
              (size_t)rc, sizeof(rsp));
        return ERR_IO;
    }
    *handle_p = make_file_handle(session, rsp.handle);
    return NO_ERROR;
}

void storage_close_file(file_handle_t fh) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_CLOSE};
    struct storage_file_close_req req = {.handle = _to_handle(fh)};
    struct iovec tx[2] = {{&msg, sizeof(msg)}, {&req, sizeof(req)}};
    struct iovec rx[1] = {{&msg, sizeof(msg)}};

    ssize_t rc = send_reqv(_to_session(fh), tx, 2, rx, 1);
    rc = check_response(&msg, rc);
    if (rc < 0) {
        TLOGE("close file failed (%d)\n", (int)rc);
    }
}

int storage_move_file(storage_session_t session,
                      file_handle_t handle,
                      const char* old_name,
                      const char* new_name,
                      uint32_t flags,
                      uint32_t opflags) {
    size_t old_name_len = strlen(old_name);
    size_t new_name_len = strlen(new_name);
    struct storage_msg msg = {
            .cmd = STORAGE_FILE_MOVE,
            .flags = _to_msg_flags(opflags),
    };
    struct storage_file_move_req req = {
            .flags = flags,
            .handle = _to_handle(handle),
            .old_name_len = old_name_len,
    };
    struct iovec tx[4] = {
            {&msg, sizeof(msg)},
            {&req, sizeof(req)},
            {(void*)old_name, old_name_len},
            {(void*)new_name, new_name_len},
    };
    struct iovec rx[1] = {{&msg, sizeof(msg)}};

    ssize_t rc = send_reqv(session, tx, 4, rx, 1);
    return (int)check_response(&msg, rc);
}

int storage_delete_file(storage_session_t session,
                        const char* name,
                        uint32_t opflags) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_DELETE,
                              .flags = _to_msg_flags(opflags)};
    struct storage_file_delete_req req = {
            .flags = 0,
    };
    struct iovec tx[3] = {{&msg, sizeof(msg)},
                          {&req, sizeof(req)},
                          {(void*)name, strlen(name)}};
    struct iovec rx[1] = {{&msg, sizeof(msg)}};

    ssize_t rc = send_reqv(session, tx, 3, rx, 1);
    return (int)check_response(&msg, rc);
}

struct storage_open_dir_state {
    uint8_t buf[MAX_CHUNK_SIZE];
    size_t buf_size;
    size_t buf_last_read;
    size_t buf_read;
};

int storage_open_dir(storage_session_t session,
                     const char* path,
                     struct storage_open_dir_state** state) {
    struct storage_file_list_resp* resp;

    if (path && strlen(path)) {
        return ERR_NOT_FOUND; /* current server does not support directories */
    }
    *state = malloc(sizeof(**state));
    if (*state == NULL) {
        return ERR_NO_MEMORY;
    }
    resp = (void*)(*state)->buf;
    resp->flags = STORAGE_FILE_LIST_START;
    (*state)->buf_size = sizeof(*resp);
    (*state)->buf_last_read = 0;
    (*state)->buf_read = (*state)->buf_size;

    return 0;
}

void storage_close_dir(storage_session_t session,
                       struct storage_open_dir_state* state) {
    free(state);
}

static int storage_read_dir_send_message(storage_session_t session,
                                         struct storage_open_dir_state* state) {
    struct storage_file_list_resp* last_item =
            (void*)(state->buf + state->buf_last_read);
    struct storage_msg msg = {.cmd = STORAGE_FILE_LIST};
    struct storage_file_list_req req = {.flags = last_item->flags};
    struct iovec tx[3] = {
            {&msg, sizeof(msg)},
            {&req, sizeof(req)},
    };
    uint32_t tx_count = 2;
    struct iovec rx[2] = {{&msg, sizeof(msg)},
                          {state->buf, sizeof(state->buf)}};
    ssize_t rc;

    if (last_item->flags != STORAGE_FILE_LIST_START) {
        tx[2].iov_base = last_item->name;
        tx[2].iov_len = strlen(last_item->name);
        tx_count = 3;
    }

    rc = send_reqv(session, tx, tx_count, rx, 2);
    rc = check_response(&msg, rc);

    state->buf_size = (rc > 0) ? rc : 0;
    state->buf_last_read = 0;
    state->buf_read = 0;

    if (rc < 0)
        return rc;

    return 0;
}

int storage_read_dir(storage_session_t session,
                     struct storage_open_dir_state* state,
                     uint8_t* flags,
                     char* name,
                     size_t name_out_size) {
    int ret;
    size_t rem;
    size_t name_size;
    struct storage_file_list_resp* item;

    if (state->buf_size == 0) {
        return ERR_IO;
    }

    if (state->buf_read >= state->buf_size) {
        ret = storage_read_dir_send_message(session, state);
        if (ret) {
            return ret;
        }
    }
    rem = state->buf_size - state->buf_read;
    if (rem < sizeof(*item)) {
        TLOGE("got short response\n");
        return ERR_IO;
    }
    item = (void*)(state->buf + state->buf_read);
    rem -= sizeof(*item);

    *flags = item->flags;
    if ((item->flags & STORAGE_FILE_LIST_STATE_MASK) == STORAGE_FILE_LIST_END) {
        state->buf_size = 0;
        name_size = 0;
    } else {
        name_size = strnlen(item->name, rem) + 1;
        if (name_size > rem) {
            TLOGE("got invalid filename size %zd >= %zd\n", name_size, rem);
            return ERR_IO;
        }
        if (name_size >= name_out_size) {
            return ERR_NOT_ENOUGH_BUFFER;
        }
        strcpy(name, item->name);
    }

    state->buf_last_read = state->buf_read;
    state->buf_read += sizeof(*item) + name_size;

    return name_size;
}

static ssize_t _read_chunk(file_handle_t fh,
                           storage_off_t off,
                           void* buf,
                           size_t size) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_READ};
    struct storage_file_read_req req = {
            .handle = _to_handle(fh),
            .size = size,
            .offset = off,
    };
    struct iovec tx[2] = {
            {&msg, sizeof(msg)},
            {&req, sizeof(req)},
    };
    struct iovec rx[2] = {
            {&msg, sizeof(msg)},
            {buf, size},
    };

    ssize_t rc = send_reqv(_to_session(fh), tx, 2, rx, 2);
    return check_response(&msg, rc);
}

ssize_t storage_read(file_handle_t fh,
                     storage_off_t off,
                     void* buf,
                     size_t size) {
    ssize_t rc;
    size_t bytes_read = 0;
    size_t chunk = MAX_CHUNK_SIZE;
    uint8_t* ptr = buf;

    while (size) {
        if (chunk > size)
            chunk = size;
        rc = _read_chunk(fh, off, ptr, chunk);
        if (rc < 0)
            return rc;
        if (rc == 0)
            break;
        off += rc;
        ptr += rc;
        bytes_read += rc;
        size -= rc;
    }
    return bytes_read;
}

static ssize_t _write_req(file_handle_t fh,
                          storage_off_t off,
                          const void* buf,
                          size_t size,
                          uint32_t msg_flags) {
    struct storage_msg msg = {
            .cmd = STORAGE_FILE_WRITE,
            .flags = msg_flags,
    };
    struct storage_file_write_req req = {
            .handle = _to_handle(fh),
            .offset = off,
    };
    struct iovec tx[3] = {
            {&msg, sizeof(msg)}, {&req, sizeof(req)}, {(void*)buf, size}};
    struct iovec rx[1] = {{&msg, sizeof(msg)}};

    ssize_t rc = send_reqv(_to_session(fh), tx, 3, rx, 1);
    rc = check_response(&msg, rc);
    return rc < 0 ? rc : (ssize_t)size;
}

ssize_t storage_write(file_handle_t fh,
                      storage_off_t off,
                      const void* buf,
                      size_t size,
                      uint32_t opflags) {
    ssize_t rc;
    size_t bytes_written = 0;
    size_t chunk = MAX_CHUNK_SIZE;
    const uint8_t* ptr = buf;
    uint32_t msg_flags = _to_msg_flags(opflags & ~STORAGE_OP_COMPLETE);

    while (size) {
        if (chunk >= size) {
            /* last chunk in sequence */
            chunk = size;
            msg_flags = _to_msg_flags(opflags);
        }
        rc = _write_req(fh, off, ptr, chunk, msg_flags);
        if (rc < 0)
            return rc;
        if ((size_t)rc != chunk) {
            TLOGE("got partial write (%d)\n", (int)rc);
            return ERR_IO;
        }
        off += chunk;
        ptr += chunk;
        bytes_written += chunk;
        size -= chunk;
    }
    return bytes_written;
}

int storage_set_file_size(file_handle_t fh,
                          storage_off_t file_size,
                          uint32_t opflags) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_SET_SIZE,
                              .flags = _to_msg_flags(opflags)};
    struct storage_file_set_size_req req = {
            .handle = _to_handle(fh),
            .size = file_size,
    };
    struct iovec tx[2] = {{&msg, sizeof(msg)}, {&req, sizeof(req)}};
    struct iovec rx[1] = {{&msg, sizeof(msg)}};

    ssize_t rc = send_reqv(_to_session(fh), tx, 2, rx, 1);
    return (int)check_response(&msg, rc);
}

int storage_get_file_size(file_handle_t fh, storage_off_t* size_p) {
    struct storage_msg msg = {.cmd = STORAGE_FILE_GET_SIZE};
    struct storage_file_get_size_req req = {
            .handle = _to_handle(fh),
    };
    struct iovec tx[2] = {{&msg, sizeof(msg)}, {&req, sizeof(req)}};
    struct storage_file_get_size_resp rsp;
    struct iovec rx[2] = {{&msg, sizeof(msg)}, {&rsp, sizeof(rsp)}};

    ssize_t rc = send_reqv(_to_session(fh), tx, 2, rx, 2);
    rc = check_response(&msg, rc);
    if (rc < 0)
        return rc;

    if ((size_t)rc != sizeof(rsp)) {
        TLOGE("%s: invalid response length (%zd != %zd)\n", __func__,
              (size_t)rc, sizeof(rsp));
        return ERR_IO;
    }

    *size_p = rsp.size;
    return NO_ERROR;
}

int storage_end_transaction(storage_session_t session, bool complete) {
    struct storage_msg msg = {
            .cmd = STORAGE_END_TRANSACTION,
            .flags = complete ? STORAGE_MSG_FLAG_TRANSACT_COMPLETE : 0,
    };
    struct iovec iov = {&msg, sizeof(msg)};

    ssize_t rc = send_reqv(session, &iov, 1, &iov, 1);
    return (int)check_response(&msg, rc);
}

int storage_commit_checkpoint(storage_session_t session) {
    struct storage_msg msg = {
            .cmd = STORAGE_END_TRANSACTION,
            .flags = STORAGE_MSG_FLAG_TRANSACT_COMPLETE |
                     STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT,
    };
    struct iovec iov = {&msg, sizeof(msg)};

    ssize_t rc = send_reqv(session, &iov, 1, &iov, 1);
    return (int)check_response(&msg, rc);
}
