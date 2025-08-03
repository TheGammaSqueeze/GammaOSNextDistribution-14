/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lk/macros.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <interface/hwaes/hwaes.h>
#include <lib/hwaes/hwaes.h>

#include <inttypes.h>

#define TLOG_TAG "libhwaes"

/**
 * struct hwaes_iov - an wrapper of an array of iovec.
 * @iov:       array of iovec.
 * @num_iov:   number of iovec.
 * @total_len: total length of the tipc message.
 */
struct hwaes_iov {
    struct iovec iov[TIPC_MAX_MSG_PARTS];
    size_t num_iov;
    size_t total_len;
};

/**
 * struct hwaes_shm - an wrapper of an array of shared memory handles.
 * @handles:     array of shared memory handles.
 * @num_handles: number of shared memory handles.
 */
struct hwaes_shm {
    handle_t handles[HWAES_MAX_NUM_HANDLES];
    size_t num_handles;
};

/**
 * hwaes_err_to_tipc_err() - translates hwaes err value to tipc/lk err
 * value
 * @hwaes_err: hwaes err value
 *
 * Returns: enum hwaes_err value
 */
static int hwaes_err_to_tipc_err(enum hwaes_err hwaes_err) {
    switch (hwaes_err) {
    case HWAES_NO_ERROR:
        return NO_ERROR;
    case HWAES_ERR_INVALID_ARGS:
        return ERR_INVALID_ARGS;
    case HWAES_ERR_IO:
        return ERR_IO;
    case HWAES_ERR_BAD_HANDLE:
        return ERR_BAD_HANDLE;
    case HWAES_ERR_NOT_IMPLEMENTED:
        return ERR_NOT_IMPLEMENTED;
    default:
        return ERR_GENERIC;
    }
}

/**
 * hwaes_set_shm_arg_helper() - helper to set shared memory for argument.
 * @data_ptr:        pointer to the argument data.
 * @len:             length of the argument data.
 * @shm_hd_ptr:      pointer to the shared memory descriptor handler.
 * @write:           the write flag of the shared memory.
 * @data_desc_ptr:   pointer to data descriptor.
 * @shm_descs:       array of shared memory descriptors.
 * @shm_wrapper_ptr: pointer to the wrapper of shared memmory array.
 *
 */
static void hwaes_set_shm_arg_helper(const uint8_t* data_ptr,
                                     size_t len,
                                     struct hwcrypt_shm_hd* shm_hd_ptr,
                                     bool write,
                                     struct hwaes_data_desc* data_desc_ptr,
                                     struct hwaes_shm_desc* shm_descs,
                                     struct hwaes_shm* shm_wrapper_ptr) {
    size_t shm_num = shm_wrapper_ptr->num_handles;
    size_t i;

    if (shm_hd_ptr) {
        for (i = 0; i < shm_num; i++) {
            if (shm_wrapper_ptr->handles[i] == shm_hd_ptr->handle) {
                break;
            }
        }

        if (i == shm_num) {
            shm_descs[i].size = shm_hd_ptr->size;
            shm_wrapper_ptr->handles[i] = shm_hd_ptr->handle;
            shm_wrapper_ptr->num_handles = shm_num + 1;
        }

        if (write) {
            shm_descs[i].write = 1U;
        }

        const uint8_t* shm_base = shm_hd_ptr->base;
        data_desc_ptr->offset = data_ptr - shm_base;
        data_desc_ptr->len = len;
        data_desc_ptr->shm_idx = i;
    } else {
        data_desc_ptr->shm_idx = HWAES_INVALID_INDEX;
    }
}

/**
 * hwaes_set_shm_arg_out() - set shared memory for output argument.
 * @arg_ptr:         pointer to the output arg.
 * @data_desc_ptr:   pointer to data descriptor.
 * @shm_descs:       array of shared memory descriptors.
 * @shm_wrapper_ptr: pointer to the wrapper of shared memmory array.
 *
 */
static void hwaes_set_shm_arg_out(const struct hwcrypt_arg_out* arg_ptr,
                                  struct hwaes_data_desc* data_desc_ptr,
                                  struct hwaes_shm_desc* shm_descs,
                                  struct hwaes_shm* shm_wrapper_ptr) {
    hwaes_set_shm_arg_helper(arg_ptr->data_ptr, arg_ptr->len,
                             arg_ptr->shm_hd_ptr, true, data_desc_ptr,
                             shm_descs, shm_wrapper_ptr);
}

/**
 * hwaes_set_shm_arg_in() - set shared memory for input argument.
 * @arg_ptr:         pointer to the input arg.
 * @data_desc_ptr:   pointer to data descriptor.
 * @shm_descs:       array of shared memory descriptors.
 * @shm_wrapper_ptr: pointer to the wrapper of shared memory array.
 *
 */
static void hwaes_set_shm_arg_in(const struct hwcrypt_arg_in* arg_ptr,
                                 struct hwaes_data_desc* data_desc_ptr,
                                 struct hwaes_shm_desc* shm_descs,
                                 struct hwaes_shm* shm_wrapper_ptr) {
    hwaes_set_shm_arg_helper(arg_ptr->data_ptr, arg_ptr->len,
                             arg_ptr->shm_hd_ptr, false, data_desc_ptr,
                             shm_descs, shm_wrapper_ptr);
}

/**
 * hwaes_set_iov_helper() - helper to set iov for argument
 * @data_ptr:        pointer to the argument data.
 * @len:             length of the argument data.
 * @iov_wrapper_ptr: pointer to a wrapper for an iovec array.
 *
 */
static void hwaes_set_iov_helper(const void* data_ptr,
                                 size_t len,
                                 struct hwaes_iov* iov_wrapper_ptr) {
    size_t iov_num = iov_wrapper_ptr->num_iov;

    assert(iov_num < TIPC_MAX_MSG_PARTS);

    /* iovec's iov_base is not const, so a cast is required*/
    iov_wrapper_ptr->iov[iov_num].iov_base = (void*)data_ptr;
    iov_wrapper_ptr->iov[iov_num].iov_len = len;
    iov_wrapper_ptr->total_len += len;
    iov_num++;
    iov_wrapper_ptr->num_iov = iov_num;
}

/**
 * hwaes_pad_iov_helper() - helper to set iov for argument and add pad
 * before the buffer to satisfy the alignment requirements
 * @pad_ptr:         pointer to the pad buffer.
 * @alignment:       alignment required for the next field
 * @iov_wrapper_ptr: pointer to a wrapper for an iovec array.
 *
 * This may add an iov entry to insert padding bytes from `pad_ptr` to ensure
 * the next bytes written will be aligned to the specified offset from the start
 * of the data stream.  If the receiving server reads the message into a
 * suitably aligned buffer, the alignment of the next bytes can be controlled to
 * meet DMA hardware or cache-line alignment requirements.
 */
static void hwaes_pad_iov_helper(const void* pad_ptr,
                                 size_t alignment,
                                 struct hwaes_iov* iov_wrapper_ptr) {
    size_t pad_size = ROUND_UP(iov_wrapper_ptr->total_len, alignment) -
                      iov_wrapper_ptr->total_len;
    if (pad_size > 0) {
        hwaes_set_iov_helper(pad_ptr, pad_size, iov_wrapper_ptr);
    }
}

/**
 * hwaes_set_iov_arg_helper() - helper to set iov for argument
 * @data_ptr:        pointer to the argument data.
 * @len:             length of the argument data.
 * @data_desc_ptr:   pointer to data descriptor.
 * @iov_wrapper_ptr: pointer to a wrapper for an iovec array.
 *
 */
static void hwaes_set_iov_arg_helper(const void* data_ptr,
                                     size_t len,
                                     struct hwaes_data_desc* data_desc_ptr,
                                     struct hwaes_iov* iov_wrapper_ptr) {
    data_desc_ptr->offset = iov_wrapper_ptr->total_len;
    data_desc_ptr->len = len;
    hwaes_set_iov_helper(data_ptr, len, iov_wrapper_ptr);
}

/**
 * hwaes_set_iov_arg_in() - set iovec for input argument.
 * @arg_ptr:         pointer to the input arg.
 * @data_desc_ptr:   pointer to data descriptor.
 * @iov_wrapper_ptr: pointer to a wrapper for an iovec array.
 *
 */
static void hwaes_set_iov_arg_in(const struct hwcrypt_arg_in* arg_ptr,
                                 struct hwaes_data_desc* data_desc_ptr,
                                 struct hwaes_iov* iov_wrapper_ptr) {
    if (data_desc_ptr->shm_idx == HWAES_INVALID_INDEX && arg_ptr->len != 0) {
        hwaes_set_iov_arg_helper(arg_ptr->data_ptr, arg_ptr->len, data_desc_ptr,
                                 iov_wrapper_ptr);
    }
}

/**
 * hwaes_set_iov_arg_out() - set iovec for output argument.
 * @arg_ptr:         pointer to the output arg.
 * @data_desc_ptr:   pointer to data descriptor.
 * @iov_wrapper_ptr: pointer to a wrapper for an iovec array.
 *
 */
static void hwaes_set_iov_arg_out(const struct hwcrypt_arg_out* arg_ptr,
                                  struct hwaes_data_desc* data_desc_ptr,
                                  struct hwaes_iov* iov_wrapper_ptr) {
    if (data_desc_ptr->shm_idx == HWAES_INVALID_INDEX && arg_ptr->len != 0) {
        hwaes_set_iov_arg_helper(arg_ptr->data_ptr, arg_ptr->len, data_desc_ptr,
                                 iov_wrapper_ptr);
    }
}

/**
 * hwaes_send_req() - sends request to hwaes server
 * @session:     the hwaes session handle.
 * @req_iov_ptr: pointer to the request iovec wrapper.
 * @shm_ptr:     pointer to an wrapper of an shared memory handles array.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_send_req(hwaes_session_t session,
                          struct hwaes_iov* req_iov_ptr,
                          struct hwaes_shm* shm_ptr) {
    int rc;

    struct ipc_msg ipc_msg = {
            .iov = req_iov_ptr->iov,
            .num_iov = req_iov_ptr->num_iov,
            .handles = shm_ptr->handles,
            .num_handles = shm_ptr->num_handles,
    };

    rc = send_msg(session, &ipc_msg);
    if (rc < 0 || (size_t)rc != req_iov_ptr->total_len) {
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        TLOGE("failed to send_msg (%d)\n", rc);
        return rc;
    }
    return NO_ERROR;
}

/**
 * hwaes_recv_resp() - receives response hwaes server
 * @session:       the hwaes session handle.
 * @resp_iov_ptr:  pointer to the response iovec wrapper.
 * @resp_msg_size: pointer to the response message size.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_recv_resp(hwaes_session_t session,
                           struct hwaes_iov* resp_iov_ptr,
                           size_t* resp_msg_size) {
    int rc;
    uevent_t uevt;

    rc = wait(session, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("failed to wait (%d)\n", rc);
        return rc;
    }

    ipc_msg_info_t msg_inf;

    rc = get_msg(session, &msg_inf);
    if (rc != NO_ERROR) {
        TLOGE("failed to get_msg (%d)\n", rc);
        return rc;
    }

    if (msg_inf.len < sizeof(struct hwaes_resp)) {
        TLOGE("msg size (%zu) is less than size of resp header (%zu)\n",
              msg_inf.len, sizeof(struct hwaes_resp));
        rc = ERR_BAD_LEN;
        goto out;
    }

    struct ipc_msg ipc_msg = {
            .iov = resp_iov_ptr->iov,
            .num_iov = resp_iov_ptr->num_iov,
            .handles = NULL,
            .num_handles = 0,
    };
    rc = read_msg(session, msg_inf.id, 0, &ipc_msg);

    if (rc != (int)msg_inf.len) {
        TLOGE("failed (%d) to read_msg()\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        goto out;
    }

    *resp_msg_size = rc;
    rc = NO_ERROR;
out:
    put_msg(session, msg_inf.id);
    return rc;
}

/**
 * hwaes_crypt() - Perform AES operation. It is a helper function
 * @session: session handle retrieved from hwaes_open.
 * @args:    arguments for the AES operation.
 * @encrypt: flag to indicate encrypt (true) or decrypt (false).
 *
 * Return: NO_ERROR on success, error code less than 0 on error.
 *
 */
static int hwaes_crypt(hwaes_session_t session,
                       const struct hwcrypt_args* args,
                       bool encrypt) {
    int rc;

    if (session == INVALID_IPC_HANDLE) {
        TLOGE("invalid session handle\n");
        return ERR_BAD_HANDLE;
    }

    struct hwaes_req req = {
            .cmd = HWAES_AES,
    };
    struct hwaes_resp resp = {0};

    struct hwaes_aes_req cmd_header = {
            .key_type = args->key_type,
            .padding = args->padding,
            .mode = args->mode,
            .encrypt = encrypt ? 1 : 0,
    };

    struct hwaes_iov req_iov = {0};
    struct hwaes_iov resp_iov = {0};
    struct hwaes_shm shm = {0};
    struct hwaes_shm_desc shm_descs[HWAES_MAX_NUM_HANDLES] = {0};

    hwaes_set_shm_arg_in(&args->key, &cmd_header.key, shm_descs, &shm);
    hwaes_set_shm_arg_in(&args->iv, &cmd_header.iv, shm_descs, &shm);
    hwaes_set_shm_arg_in(&args->aad, &cmd_header.aad, shm_descs, &shm);
    hwaes_set_shm_arg_in(&args->text_in, &cmd_header.text_in, shm_descs, &shm);
    hwaes_set_shm_arg_in(&args->tag_in, &cmd_header.tag_in, shm_descs, &shm);

    hwaes_set_shm_arg_out(&args->text_out, &cmd_header.text_out, shm_descs,
                          &shm);
    hwaes_set_shm_arg_out(&args->tag_out, &cmd_header.tag_out, shm_descs, &shm);

    cmd_header.num_handles = shm.num_handles;

    hwaes_set_iov_helper(&req, sizeof(req), &req_iov);
    hwaes_set_iov_helper(&cmd_header, sizeof(cmd_header), &req_iov);
    hwaes_set_iov_helper(shm_descs,
                         shm.num_handles * sizeof(struct hwaes_shm_desc),
                         &req_iov);

    hwaes_set_iov_arg_in(&args->key, &cmd_header.key, &req_iov);
    hwaes_set_iov_arg_in(&args->iv, &cmd_header.iv, &req_iov);

    uint8_t padd_buf[MAX(HWAES_TEXT_IN_BUF_ALIGNMENT,
                         HWAES_TEXT_OUT_BUF_ALIGNMENT)] = {0};
    if (cmd_header.aad.shm_idx == HWAES_INVALID_INDEX && args->aad.len != 0) {
        hwaes_pad_iov_helper(padd_buf, HWAES_TEXT_IN_BUF_ALIGNMENT, &req_iov);
    }
    hwaes_set_iov_arg_in(&args->aad, &cmd_header.aad, &req_iov);

    if (cmd_header.text_in.shm_idx == HWAES_INVALID_INDEX &&
        args->text_in.len != 0) {
        hwaes_pad_iov_helper(padd_buf, HWAES_TEXT_IN_BUF_ALIGNMENT, &req_iov);
    }
    hwaes_set_iov_arg_in(&args->text_in, &cmd_header.text_in, &req_iov);
    hwaes_set_iov_arg_in(&args->tag_in, &cmd_header.tag_in, &req_iov);

    hwaes_set_iov_helper(&resp, sizeof(resp), &resp_iov);

    if (cmd_header.text_out.shm_idx == HWAES_INVALID_INDEX &&
        args->text_out.len != 0) {
        hwaes_pad_iov_helper(padd_buf, HWAES_TEXT_OUT_BUF_ALIGNMENT, &resp_iov);
    }
    hwaes_set_iov_arg_out(&args->text_out, &cmd_header.text_out, &resp_iov);
    hwaes_set_iov_arg_out(&args->tag_out, &cmd_header.tag_out, &resp_iov);

    rc = hwaes_send_req(session, &req_iov, &shm);
    if (rc != NO_ERROR) {
        TLOGE("failed to hwaes_send_req (%d)\n", rc);
        return rc;
    }

    size_t resp_msg_size;
    rc = hwaes_recv_resp(session, &resp_iov, &resp_msg_size);
    if (rc != NO_ERROR) {
        TLOGE("failed to hwaes_recv_resp (%d)\n", rc);
        return rc;
    }

    if (resp.cmd != (req.cmd | HWAES_RESP_BIT)) {
        TLOGE("invalid response cmd (0x%x) for request cmd (0x%x)\n", resp.cmd,
              req.cmd);
        return ERR_NOT_VALID;
    }

    if (resp.result == HWAES_NO_ERROR && resp_msg_size != resp_iov.total_len) {
        TLOGE("wrong response message length (%zu)\n", resp_msg_size);
        return ERR_BAD_LEN;
    }

    return hwaes_err_to_tipc_err(resp.result);
}

int hwaes_open(hwaes_session_t* session) {
    int rc = tipc_connect(session, HWAES_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s\n", HWAES_PORT);
    }
    return rc;
}

int hwaes_encrypt(hwaes_session_t session, const struct hwcrypt_args* args) {
    return hwaes_crypt(session, args, true);
}

int hwaes_decrypt(hwaes_session_t session, const struct hwcrypt_args* args) {
    return hwaes_crypt(session, args, false);
}

void hwaes_close(hwaes_session_t session) {
    close(session);
}
