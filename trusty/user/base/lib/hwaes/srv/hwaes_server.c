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

#define TLOG_TAG "lib_hwaes_server"

#include <assert.h>
#include <lib/hwaes_server/hwaes_server.h>
#include <lib/tipc/tipc_srv.h>
#include <stdlib.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>

#include <trusty_log.h>
#include <uapi/err.h>

#include <inttypes.h>

#define AUX_PAGE_SIZE() getauxval(AT_PAGESZ)

struct shm {
    void* base;
    size_t size;
    int mmap_prot;
};

/**
 * tipc_err_to_hwaes_err() - translates tipc/lk err to hwaes err value
 * @tipc_err: tipc err value
 *
 * Returns: enum hwaes_err value
 */
static enum hwaes_err tipc_err_to_hwaes_err(int tipc_err) {
    switch (tipc_err) {
    case NO_ERROR:
        return HWAES_NO_ERROR;
    case ERR_INVALID_ARGS:
        return HWAES_ERR_INVALID_ARGS;
    case ERR_IO:
        return HWAES_ERR_IO;
    case ERR_BAD_HANDLE:
        return HWAES_ERR_BAD_HANDLE;
    case ERR_NOT_IMPLEMENTED:
        return HWAES_ERR_NOT_IMPLEMENTED;
    default:
        return HWAES_ERR_GENERIC;
    }
}

/**
 * int hwaes_send_resp() - send response to the client
 * @chan:   the channel handle.
 * @buf:    the pointer to the buffer to store the response.
 * @buf_sz: the size of the buffer pointed by @buf parameter.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_send_resp(handle_t chan, void* buf, size_t buf_sz) {
    struct hwaes_resp* resp_header = (struct hwaes_resp*)buf;
    resp_header->cmd |= HWAES_RESP_BIT;

    int rc = tipc_send1(chan, buf, buf_sz);
    if ((size_t)rc != buf_sz) {
        TLOGE("failed (%d) to send message. Expected to send %zu bytes.\n", rc,
              buf_sz);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }
    return NO_ERROR;
}

/**
 * int hwaes_map_shm() - map the shared memories.
 * @num:       the number of shared memories.
 * @handles:   the array of shared memory handles.
 * @shm_descs: the array of shared memory descriptors.
 * @shms:      the array of shared memories.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_map_shm(size_t num,
                         handle_t* handles,
                         struct hwaes_shm_desc* shm_descs,
                         struct shm* shms) {
    for (size_t i = 0; i < num; i++) {
        if (shm_descs[i].reserved) {
            TLOGE("bad shared memory descriptor, reserved not 0, (%d)\n",
                  shm_descs[i].reserved);
            return ERR_IO;
        }

        if (~1U & shm_descs[i].write) {
            TLOGE("the write flag (%u) of shared memory is invalid.\n",
                  shm_descs[i].write);
            return ERR_IO;
        }
        int mmap_prot = PROT_READ;
        if (shm_descs[i].write) {
            mmap_prot |= PROT_WRITE;
        }

        uint64_t size64 = shm_descs[i].size;
        if (size64 > SIZE_MAX) {
            TLOGE("share memory size is larger than SIZE_MAX\n");
            return ERR_INVALID_ARGS;
        }

        size_t size = size64;
        if (size == 0 || size % AUX_PAGE_SIZE()) {
            TLOGE("size (%zu) of shared memory is invalid.\n", size);
            return ERR_INVALID_ARGS;
        }

        shms[i].base = mmap(0, shm_descs[i].size, mmap_prot, 0, handles[i], 0);
        if (shms[i].base == MAP_FAILED) {
            TLOGE("failed to mmap() shared memory for handle (%zu).\n", i);
            return ERR_BAD_HANDLE;
        }
        shms[i].size = shm_descs[i].size;
        shms[i].mmap_prot = mmap_prot;
    }
    return NO_ERROR;
}

/**
 * void hwaes_unmap_shm() - unmap the shared memories.
 * @num:  the number of shared memories.
 * @shms: the array of shared memories.
 *
 */
static void hwaes_unmap_shm(size_t num, struct shm* shms) {
    for (size_t i = 0; i < num; i++) {
        if (shms[i].size) {
            munmap(shms[i].base, shms[i].size);
        }
    }
}

/**
 * int hwaes_set_arg_out() - set the output argument
 * @data_desc_ptr: pointer to a data_desc.
 * @mmap_prot:     shared memory mmap flag required for the argument.
 * @shms:          the array of shared memories.
 * @num_shms:      the number of shared memories.
 * @buf_start:     the start of the buffer.
 * @max_buf_sz:    the maximum size of the buffer.
 * @offset_ptr:    pointer to the offset to the start of the buffer.
 * @arg_ptr:       pointer to the output argument.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_set_arg_out(struct hwaes_data_desc* data_desc_ptr,
                             int mmap_prot,
                             struct shm* shms,
                             size_t num_shms,
                             uint8_t* buf_start,
                             size_t max_buf_sz,
                             size_t* offset_ptr,
                             struct hwaes_arg_out* arg_ptr) {
    if (data_desc_ptr->reserved) {
        TLOGE("bad data descriptor, reserved not 0, (%d)\n",
              data_desc_ptr->reserved);
        return ERR_IO;
    }

    size_t offset = *offset_ptr;
    if (data_desc_ptr->len) {
        if (data_desc_ptr->len > SIZE_MAX) {
            TLOGE("data length is larger than SIZE_MAX\n");
            return ERR_INVALID_ARGS;
        }
        arg_ptr->len = data_desc_ptr->len;
        if (HWAES_INVALID_INDEX != data_desc_ptr->shm_idx) {
            if (data_desc_ptr->shm_idx >= num_shms) {
                TLOGE("invalid shared memory index\n");
                return ERR_IO;
            }
            int shm_mmap_prot = shms[data_desc_ptr->shm_idx].mmap_prot;
            if (~shm_mmap_prot & mmap_prot) {
                TLOGE("invalid shared memory protect flag\n");
                return ERR_IO;
            }
            size_t end_offset;
            if (__builtin_add_overflow(data_desc_ptr->offset,
                                       data_desc_ptr->len, &end_offset)) {
                TLOGE("the calculation of end_offset overflows \n");
                return ERR_INVALID_ARGS;
            }
            if (shms[data_desc_ptr->shm_idx].size < end_offset) {
                TLOGE("data exceeds shared memory boundaries\n");
                return ERR_INVALID_ARGS;
            }
            arg_ptr->data_ptr =
                    shms[data_desc_ptr->shm_idx].base + data_desc_ptr->offset;
        } else {
            if (offset != data_desc_ptr->offset) {
                /* Some buffers may have buffer alignment requirements.
                 * For inlined buffers, the offsets should be adjusted
                 * on the client side to achieve the required alignment.
                 * If this has been done, the offset needs updating here
                 * also, read it from the descriptor (it should be
                 * not less than end of the previous entry, though).
                 */
                if (data_desc_ptr->offset < offset) {
                    TLOGE("invalid descriptor offset (%" PRIu64 " < %zu)\n",
                          data_desc_ptr->offset, offset);
                    return ERR_INVALID_ARGS;
                }
                offset = data_desc_ptr->offset;
            }

            if (__builtin_add_overflow(offset, data_desc_ptr->len, &offset)) {
                TLOGE("the calculation of offset overflows\n");
                return ERR_INVALID_ARGS;
            }
            if (offset > max_buf_sz) {
                TLOGE("offset (%zu) exceeds the maximum message size\n",
                      offset);
                return ERR_INVALID_ARGS;
            }
            arg_ptr->data_ptr = buf_start + data_desc_ptr->offset;
            *offset_ptr = offset;
        }
    }
    return NO_ERROR;
}

static int hwaes_set_arg_in(struct hwaes_data_desc* data_desc_ptr,
                            int mmap_prot,
                            struct shm* shms,
                            size_t num_shms,
                            uint8_t* buf_start,
                            size_t max_buf_sz,
                            size_t* offset_ptr,
                            struct hwaes_arg_in* arg) {
    int rc;
    struct hwaes_arg_out out_arg = {0};
    rc = hwaes_set_arg_out(data_desc_ptr, mmap_prot, shms, num_shms, buf_start,
                           max_buf_sz, offset_ptr, &out_arg);
    if (rc != NO_ERROR) {
        return rc;
    }
    arg->data_ptr = out_arg.data_ptr;
    arg->len = out_arg.len;
    return NO_ERROR;
}

/**
 * int hwaes_handle_aes_cmd() - handle request with HWAES_AES command
 * @chan:         the channel handle.
 * @req_msg_buf:  the request message buffer.
 * @req_msg_size: the size of request message.
 * @resp_msg_buf: the response message buffer.
 * @shm_handles:  the array of shared memory handles.
 * @num_handles:  the number of shared memory handles.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_handle_aes_cmd(handle_t chan,
                                uint8_t* req_msg_buf,
                                size_t req_msg_size,
                                uint8_t* resp_msg_buf,
                                handle_t* shm_handles,
                                size_t num_handles) {
    int rc;
    struct shm shms[HWAES_MAX_NUM_HANDLES] = {0};

    struct hwaes_req* req_header = (struct hwaes_req*)req_msg_buf;

    struct hwaes_resp* resp_header = (struct hwaes_resp*)resp_msg_buf;
    size_t resp_msg_size = sizeof(*resp_header);

    struct hwaes_aes_req* cmd_header =
            (struct hwaes_aes_req*)(req_msg_buf + sizeof(*req_header));

    if (cmd_header->reserved) {
        TLOGE("bad cmd header, reserved not 0, (%d)\n", cmd_header->reserved);
        return ERR_NOT_VALID;
    }

    size_t header_num_handles = cmd_header->num_handles;

    if (header_num_handles != num_handles) {
        TLOGE("header specified num_handles(%zu) is not equal to the num_handles(%zu)\n",
              header_num_handles, num_handles);
        return ERR_NOT_VALID;
    }

    size_t req_header_size = sizeof(*req_header) + sizeof(*cmd_header) +
                             num_handles * sizeof(struct hwaes_shm_desc);

    if (req_header_size > HWAES_MAX_MSG_SIZE) {
        TLOGE("request header size (%zu) exceeds the maximum message size\n",
              req_header_size);
        return ERR_NOT_VALID;
    }

    struct hwaes_shm_desc* shm_descs =
            (struct hwaes_shm_desc*)(req_msg_buf + sizeof(*req_header) +
                                     sizeof(*cmd_header));

    rc = hwaes_map_shm(num_handles, shm_handles, shm_descs, shms);
    if (rc != NO_ERROR) {
        TLOGE("failed to hwaes_map_shm()\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    struct hwaes_aes_op_args args = {
            .key_type = cmd_header->key_type,
            .padding = cmd_header->padding,
            .mode = cmd_header->mode,
            .encrypt = !!cmd_header->encrypt,
    };

    size_t req_offset = req_header_size;
    size_t resp_offset = resp_msg_size;

    rc = hwaes_set_arg_in(&cmd_header->key, PROT_READ, shms, num_handles,
                          req_msg_buf, req_msg_size, &req_offset, &args.key);
    if (rc != NO_ERROR) {
        TLOGE("failed to set key\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_in(&cmd_header->iv, PROT_READ, shms, num_handles,
                          req_msg_buf, req_msg_size, &req_offset, &args.iv);
    if (rc != NO_ERROR) {
        TLOGE("failed to set iv\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_in(&cmd_header->aad, PROT_READ, shms, num_handles,
                          req_msg_buf, req_msg_size, &req_offset, &args.aad);
    if (rc != NO_ERROR) {
        TLOGE("failed to set aad\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_in(&cmd_header->text_in, PROT_READ, shms, num_handles,
                          req_msg_buf, req_msg_size, &req_offset,
                          &args.text_in);
    if (rc != NO_ERROR) {
        TLOGE("failed to set text_in\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_in(&cmd_header->tag_in, PROT_READ, shms, num_handles,
                          req_msg_buf, req_msg_size, &req_offset, &args.tag_in);
    if (rc != NO_ERROR) {
        TLOGE("failed to set tag_in\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_out(&cmd_header->text_out, PROT_READ | PROT_WRITE, shms,
                           num_handles, resp_msg_buf, HWAES_MAX_MSG_SIZE,
                           &resp_offset, &args.text_out);
    if (rc != NO_ERROR) {
        TLOGE("failed to set text_out\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    rc = hwaes_set_arg_out(&cmd_header->tag_out, PROT_READ | PROT_WRITE, shms,
                           num_handles, resp_msg_buf, HWAES_MAX_MSG_SIZE,
                           &resp_offset, &args.tag_out);
    if (rc != NO_ERROR) {
        TLOGE("failed to set tag_out\n");
        resp_header->result = tipc_err_to_hwaes_err(rc);
        goto out;
    }

    resp_header->result = hwaes_aes_op(&args);
    if (resp_header->result == HWAES_NO_ERROR) {
        resp_msg_size = resp_offset;
    }

out:
    rc = hwaes_send_resp(chan, resp_msg_buf, resp_msg_size);
    hwaes_unmap_shm(cmd_header->num_handles, shms);
    return rc;
}

/**
 * int hwaes_read_req() - read the request from the client
 * @chan:             the channel handle.
 * @buf:              the pointer to the buffer to store the request.
 * @shm_handles:      the array of shared memory handles.
 * @num_handles_ptr:  pointer to the number of shared memory handles.
 * @req_msg_size_ptr: pointer to the size of request message.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static int hwaes_read_req(handle_t chan,
                          void* buf,
                          handle_t* shm_handles,
                          size_t* num_handles_ptr,
                          size_t* req_msg_size_ptr) {
    int rc;
    struct ipc_msg_info msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to get_msg()\n", rc);
        return rc;
    }

    if (msg_inf.len < sizeof(struct hwaes_req) ||
        msg_inf.len > HWAES_MAX_MSG_SIZE) {
        TLOGE("unexpected msg size: buffer too small or too big\n");
        rc = ERR_BAD_LEN;
        goto free_msg;
    }

    if (msg_inf.num_handles > HWAES_MAX_NUM_HANDLES) {
        TLOGE("too many shared memory handles\n");
        rc = ERR_BAD_LEN;
        goto free_msg;
    }

    struct iovec iov = {
            .iov_base = buf,
            .iov_len = HWAES_MAX_MSG_SIZE,
    };
    struct ipc_msg ipc_msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = shm_handles,
            .num_handles = msg_inf.num_handles,
    };

    rc = read_msg(chan, msg_inf.id, 0, &ipc_msg);
    if (rc < 0) {
        TLOGE("failed (%d) to read_msg()\n", rc);
        goto free_msg;
    }
    assert(rc == (int)msg_inf.len);

    *req_msg_size_ptr = rc;
    *num_handles_ptr = msg_inf.num_handles;
    rc = NO_ERROR;

free_msg:
    put_msg(chan, msg_inf.id);
    return rc;
}

static int hwaes_on_message(const struct tipc_port* port,
                            handle_t chan,
                            void* ctx) {
    int rc;

    handle_t shm_handles[HWAES_MAX_NUM_HANDLES];
    size_t num_handles = 0;
    size_t req_msg_size;

    uint8_t* req_msg_buf = memalign(AUX_PAGE_SIZE(), HWAES_MAX_MSG_SIZE);
    uint8_t* resp_msg_buf = memalign(AUX_PAGE_SIZE(), HWAES_MAX_MSG_SIZE);

    if (req_msg_buf == 0 || resp_msg_buf == 0) {
        TLOGE("failed to allocate memory for req/resp msg.\n");
        rc = ERR_NO_MEMORY;
        goto free_handles;
    }

    rc = hwaes_read_req(chan, req_msg_buf, shm_handles, &num_handles,
                        &req_msg_size);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to hwaes_read_req()\n", rc);
        num_handles = 0;
        goto free_handles;
    };

    struct hwaes_req* req_header = (struct hwaes_req*)req_msg_buf;
    if (req_header->reserved) {
        TLOGE("bad general header, reserved not 0, (%d)\n",
              req_header->reserved);
        rc = ERR_NOT_VALID;
        goto free_handles;
    }

    struct hwaes_resp* resp_header = (struct hwaes_resp*)resp_msg_buf;

    resp_header->cmd = req_header->cmd;

    /* handle it */
    switch (req_header->cmd) {
    case HWAES_AES:
        rc = hwaes_handle_aes_cmd(chan, req_msg_buf, req_msg_size, resp_msg_buf,
                                  shm_handles, num_handles);
        break;

    default:
        TLOGE("unsupported request: %d\n", (int)req_header->cmd);
        resp_header->result = HWAES_ERR_NOT_IMPLEMENTED;
        rc = hwaes_send_resp(chan, resp_header, sizeof(*resp_header));
    }

free_handles:
    for (size_t i = 0; i < num_handles; i++) {
        close(shm_handles[i]);
    }

    free(req_msg_buf);
    free(resp_msg_buf);

    return rc;
}

int add_hwaes_service(struct tipc_hset* hset,
                      const uuid_t** allowed_clients,
                      size_t allowed_clients_len) {
    static struct tipc_port_acl acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };
    acl.uuid_num = allowed_clients_len;
    acl.uuids = allowed_clients;

    static struct tipc_port port = {
            .name = HWAES_PORT,
            .msg_max_size = HWAES_MAX_MSG_SIZE,
            .msg_queue_len = 1,
            .acl = &acl,
    };
    static struct tipc_srv_ops ops = {
            .on_message = hwaes_on_message,
    };
    return tipc_add_service(hset, &port, 1, 2, &ops);
}
