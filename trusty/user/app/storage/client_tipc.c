/*
 * Copyright (C) 2013-2016 The Android Open Source Project
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

#include "client_tipc.h"

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <lk/list.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <openssl/mem.h>

#include <interface/storage/storage.h>
#include <lib/system_state/system_state.h>

#include "client_session_tipc.h"
#include "file.h"
#include "ipc.h"
#include "session.h"
#include "tipc_limits.h"

/* macros to help manage debug output */
#define SS_ERR(args...) fprintf(stderr, "ss: " args)
#define SS_DBG_IO(args...) \
    do {                   \
    } while (0)

#if 0
/* this can generate alot of spew on debug builds */
#define SS_INFO(args...) fprintf(stderr, "ss: " args)
#else
#define SS_INFO(args...) \
    do {                 \
    } while (0)
#endif

static int client_handle_msg(struct ipc_channel_context* ctx,
                             void* msg,
                             size_t msg_size);
static void client_disconnect(struct ipc_channel_context* context);
static int send_response(struct storage_client_session* session,
                         enum storage_err result,
                         struct storage_msg* msg,
                         void* out,
                         size_t out_size);

/**
 * checkpoint_update_allowed - Is checkpoint modification currently allowed?
 * @transaction:    Transaction object.
 *
 * Check if the given transaction is allowed to create a new or update the
 * existing checkpoint for its file system. Checkpoint updates may only be
 * requested by client while the device is in provisioning mode.
 *
 * Returns %true if a transaction may update the current checkpoint state,
 * %false otherwise.
 */
static bool checkpoint_update_allowed(struct transaction* tr) {
    return system_state_provisioning_allowed();
}

/*
 * Legal secure storage directory and file names contain only
 * characters from the following set: [a-z][A-Z][0-9][.-_]
 *
 * It is not null terminated.
 */
static int is_valid_name(const char* name, size_t name_len) {
    size_t i;

    if (!name_len)
        return 0;

    for (i = 0; i < name_len; i++) {
        if ((name[i] >= 'a') && (name[i] <= 'z'))
            continue;
        if ((name[i] >= 'A') && (name[i] <= 'Z'))
            continue;
        if ((name[i] >= '0') && (name[i] <= '9'))
            continue;
        if ((name[i] == '.') || (name[i] == '-') || (name[i] == '_'))
            continue;

        /* not a legal character so reject this name */
        return 0;
    }

    return 1;
}

static int get_path(char* path_out,
                    size_t path_out_size,
                    const uuid_t* uuid,
                    const char* file_name,
                    size_t file_name_len) {
    unsigned int rc;

    rc = snprintf(path_out, path_out_size,
                  "%08x%04x%04x%02x%02x%02x%02x%02x%02x%02x%02x/",
                  uuid->time_low, uuid->time_mid, uuid->time_hi_and_version,
                  uuid->clock_seq_and_node[0], uuid->clock_seq_and_node[1],
                  uuid->clock_seq_and_node[2], uuid->clock_seq_and_node[3],
                  uuid->clock_seq_and_node[4], uuid->clock_seq_and_node[5],
                  uuid->clock_seq_and_node[6], uuid->clock_seq_and_node[7]);

    if (rc + file_name_len >= path_out_size) {
        return STORAGE_ERR_NOT_VALID;
    }

    memcpy(path_out + rc, file_name, file_name_len);
    path_out[rc + file_name_len] = '\0';

    return STORAGE_NO_ERROR;
}

static enum storage_err file_op_result_to_storage_err(
        enum file_op_result result) {
    switch (result) {
    case FILE_OP_SUCCESS:
        return STORAGE_NO_ERROR;
    case FILE_OP_ERR_FAILED:
        /* TODO: Consider returning STORAGE_ERR_TRANSACT consistently */
        return STORAGE_ERR_GENERIC;
    case FILE_OP_ERR_EXIST:
        return STORAGE_ERR_EXIST;
    case FILE_OP_ERR_ALREADY_OPEN:
        return STORAGE_ERR_NOT_ALLOWED;
    case FILE_OP_ERR_NOT_FOUND:
        return STORAGE_ERR_NOT_FOUND;
    case FILE_OP_ERR_FS_REPAIRED:
        return STORAGE_ERR_FS_REPAIRED;
    }

    SS_ERR("%s: Unknown file_op_result: %d\n", __func__, result);
    return STORAGE_ERR_GENERIC;
}

static enum storage_err session_set_files_count(
        struct storage_client_session* session,
        size_t files_count) {
    struct file_handle** files;

    if (files_count > STORAGE_MAX_OPEN_FILES) {
        SS_ERR("%s: too many open files\n", __func__);
        return STORAGE_ERR_NOT_VALID;
    }

    if (!files_count) {
        free(session->files);
        session->files = NULL;
    } else {
        files = realloc(session->files, sizeof(files[0]) * files_count);
        if (!files) {
            SS_ERR("%s: out of memory\n", __func__);
            return STORAGE_ERR_GENERIC;
        }
        if (files_count > session->files_count)
            memset(files + session->files_count, 0,
                   sizeof(files[0]) * (files_count - session->files_count));

        session->files = files;
    }
    session->files_count = files_count;

    SS_INFO("%s: new file table size, 0x%x\n", __func__, files_count);

    return STORAGE_NO_ERROR;
}

static void session_shrink_files(struct storage_client_session* session) {
    uint32_t handle;

    handle = session->files_count;
    while (handle > 0 && !session->files[handle - 1])
        handle--;

    if (handle < session->files_count)
        session_set_files_count(session, handle);
}

static void session_close_all_files(struct storage_client_session* session) {
    uint32_t f_handle;
    struct file_handle* file;

    for (f_handle = 0; f_handle < session->files_count; f_handle++) {
        file = session->files[f_handle];
        if (file) {
            file_close(file);
            free(file);
        }
    }
    if (session->files) {
        free(session->files);
    }
    session->files_count = 0;
}

static enum storage_err create_file_handle(
        struct storage_client_session* session,
        uint32_t* handlep,
        struct file_handle** file_p) {
    enum storage_err result;
    uint32_t handle;
    struct file_handle* file;

    for (handle = 0; handle < session->files_count; handle++)
        if (!session->files[handle])
            break;

    if (handle >= session->files_count) {
        result = session_set_files_count(session, handle + 1);
        if (result != STORAGE_NO_ERROR)
            return result;
    }

    file = calloc(1, sizeof(*file));
    if (!file) {
        SS_ERR("%s: out of memory\n", __func__);
        return STORAGE_ERR_GENERIC;
    }

    session->files[handle] = file;

    SS_INFO("%s: created file handle 0x%x\n", __func__, handle);
    *handlep = handle;
    *file_p = file;
    return STORAGE_NO_ERROR;
}

static void free_file_handle(struct storage_client_session* session,
                             uint32_t handle) {
    if (handle >= session->files_count) {
        SS_ERR("%s: invalid handle, 0x%x\n", __func__, handle);
        return;
    }
    if (session->files[handle] == NULL) {
        SS_ERR("%s: closed handle, 0x%x\n", __func__, handle);
        return;
    }
    free(session->files[handle]);
    session->files[handle] = NULL;

    SS_INFO("%s: deleted file handle 0x%x\n", __func__, handle);

    session_shrink_files(session);
}

static struct file_handle* get_file_handle(
        struct storage_client_session* session,
        uint32_t handle) {
    struct file_handle* file;
    if (handle >= session->files_count) {
        SS_ERR("%s: invalid handle, 0x%x\n", __func__, handle);
        return NULL;
    }
    file = session->files[handle];
    if (!file) {
        SS_ERR("%s: closed handle, 0x%x\n", __func__, handle);
        return NULL;
    }
    return file;
}

static enum storage_err storage_file_delete(
        struct storage_msg* msg,
        struct storage_file_delete_req* req,
        size_t req_size,
        struct storage_client_session* session) {
    enum file_op_result delete_res;
    enum storage_err result;
    const char* fname;
    size_t fname_len;
    uint32_t flags;
    char path_buf[FS_PATH_MAX];

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        return STORAGE_ERR_NOT_VALID;
    }

    flags = req->flags;
    if ((flags & ~STORAGE_FILE_DELETE_MASK) != 0) {
        SS_ERR("invalid delete flags 0x%x\n", flags);
        return STORAGE_ERR_NOT_VALID;
    }

    /* make sure filename is legal */
    fname = req->name;
    fname_len = req_size - sizeof(*req);
    if (!is_valid_name(fname, fname_len)) {
        SS_ERR("%s: invalid filename\n", __func__);
        return STORAGE_ERR_NOT_VALID;
    }

    result = get_path(path_buf, sizeof(path_buf), &session->uuid, fname,
                      fname_len);
    if (result != STORAGE_NO_ERROR) {
        return result;
    }

    SS_INFO("%s: path %s\n", __func__, path_buf);

    delete_res = file_delete(&session->tr, path_buf,
                             msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);

    if (delete_res != FILE_OP_SUCCESS) {
        return file_op_result_to_storage_err(delete_res);
    }

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
        if (session->tr.failed) {
            SS_ERR("%s: transaction commit failed\n", __func__);
            return STORAGE_ERR_GENERIC;
        }
        return STORAGE_NO_ERROR;
    }

    return STORAGE_NO_ERROR;
}

/**
 * storage_file_check_name - Check if file handle matches path
 * @tr:         Transaction object.
 * @file:       File handle object.
 * @path:       Path to check.
 *
 * Return: %true if @file matches @path, %false otherwise.
 */
static bool storage_file_check_name(struct transaction* tr,
                                    const struct file_handle* file,
                                    const char* path) {
    bool ret;
    const struct file_info* file_info;
    struct obj_ref ref = OBJ_REF_INITIAL_VALUE(ref);

    file_info = file_get_info(tr, &file->block_mac, &ref);
    if (!file_info) {
        printf("can't read file entry at %" PRIu64 "\n",
               block_mac_to_block(tr, &file->block_mac));
        return false;
    }
    assert(file_info);
    ret = strcmp(file_info->path, path) == 0;
    file_info_put(file_info, &ref);

    return ret;
}

static enum storage_err storage_file_move(
        struct storage_msg* msg,
        struct storage_file_move_req* req,
        size_t req_size,
        struct storage_client_session* session) {
    enum file_op_result open_result;
    enum file_op_result move_result;
    enum storage_err result;
    const char* old_name;
    const char* new_name;
    size_t fname_len;
    size_t old_len;
    size_t new_len;
    uint32_t flags;
    struct file_handle* file = NULL;
    char path_buf[FS_PATH_MAX];
    enum file_create_mode file_create_mode;
    struct file_handle tmp_file;

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        return STORAGE_ERR_NOT_VALID;
    }

    flags = req->flags;
    if ((flags & ~STORAGE_FILE_MOVE_MASK) != 0) {
        SS_ERR("invalid move flags 0x%x\n", flags);
        return STORAGE_ERR_NOT_VALID;
    }

    if (flags & STORAGE_FILE_MOVE_CREATE) {
        if (flags & STORAGE_FILE_MOVE_CREATE_EXCLUSIVE) {
            file_create_mode = FILE_OPEN_CREATE_EXCLUSIVE;
        } else {
            file_create_mode = FILE_OPEN_CREATE;
        }
    } else {
        file_create_mode = FILE_OPEN_NO_CREATE;
    }

    /* make sure filename is legal */
    old_name = req->old_new_name;
    fname_len = req_size - sizeof(*req);
    if (!is_valid_name(old_name, fname_len)) {
        SS_ERR("%s: invalid filename\n", __func__);
        return STORAGE_ERR_NOT_VALID;
    }

    old_len = req->old_name_len;
    if (old_len >= fname_len) {
        SS_ERR("%s: invalid old filename length %zu >= %zu\n", __func__,
               old_len, fname_len);
        return STORAGE_ERR_NOT_VALID;
    }
    new_len = fname_len - old_len;
    new_name = old_name + old_len;

    if (flags & STORAGE_FILE_MOVE_OPEN_FILE) {
        file = get_file_handle(session, req->handle);
        if (!file)
            return STORAGE_ERR_NOT_VALID;
    }

    result = get_path(path_buf, sizeof(path_buf), &session->uuid, old_name,
                      old_len);
    if (result != STORAGE_NO_ERROR) {
        return result;
    }

    SS_INFO("%s: old path %s\n", __func__, path_buf);

    if (file) {
        if (!storage_file_check_name(&session->tr, file, path_buf)) {
            return STORAGE_ERR_NOT_VALID;
        }
    } else {
        open_result = file_open(&session->tr, path_buf, &tmp_file,
                                FILE_OPEN_NO_CREATE,
                                msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);
        if (open_result != FILE_OP_SUCCESS) {
            return file_op_result_to_storage_err(open_result);
        }
        file = &tmp_file;
    }

    result = get_path(path_buf, sizeof(path_buf), &session->uuid, new_name,
                      new_len);
    if (result != STORAGE_NO_ERROR) {
        if (file == &tmp_file) {
            file_close(&tmp_file);
        }
        return result;
    }
    SS_INFO("%s: new path %s\n", __func__, path_buf);

    move_result = file_move(&session->tr, file, path_buf, file_create_mode,
                            msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);
    if (file == &tmp_file) {
        file_close(&tmp_file);
    }

    if (move_result != FILE_OP_SUCCESS) {
        return file_op_result_to_storage_err(move_result);
    }

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
        if (session->tr.failed) {
            SS_ERR("%s: transaction commit failed\n", __func__);
            return STORAGE_ERR_GENERIC;
        }
        return STORAGE_NO_ERROR;
    }

    return STORAGE_NO_ERROR;
}

static int storage_file_open(struct storage_msg* msg,
                             struct storage_file_open_req* req,
                             size_t req_size,
                             struct storage_client_session* session)

{
    enum file_op_result open_result;
    enum storage_err result;
    struct file_handle* file = NULL;
    const char* fname;
    size_t fname_len;
    uint32_t flags, f_handle;
    char path_buf[FS_PATH_MAX];
    void* out = NULL;
    size_t out_size = 0;
    enum file_create_mode file_create_mode;

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_size;
    }

    flags = req->flags;
    if ((flags & ~STORAGE_FILE_OPEN_MASK) != 0) {
        SS_ERR("%s: invalid flags 0x%x\n", __func__, flags);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_mask;
    }

    /* make sure filename is legal */
    fname = req->name;
    fname_len = req_size - sizeof(*req);
    if (!is_valid_name(fname, fname_len)) {
        SS_ERR("%s: invalid filename\n", __func__);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_name;
    }

    result = get_path(path_buf, sizeof(path_buf), &session->uuid, fname,
                      fname_len);
    if (result != STORAGE_NO_ERROR) {
        goto err_get_path;
    }

    SS_INFO("%s: path %s flags 0x%x\n", __func__, path_buf, flags);

    SS_INFO("%s: call create_file_handle\n", __func__);
    /* alloc file info struct */
    result = create_file_handle(session, &f_handle, &file);
    if (result != STORAGE_NO_ERROR)
        goto err_create_file_handle;

    if (flags & STORAGE_FILE_OPEN_CREATE) {
        if (flags & STORAGE_FILE_OPEN_CREATE_EXCLUSIVE) {
            file_create_mode = FILE_OPEN_CREATE_EXCLUSIVE;
        } else {
            file_create_mode = FILE_OPEN_CREATE;
        }
    } else {
        file_create_mode = FILE_OPEN_NO_CREATE;
    }

    open_result = file_open(&session->tr, path_buf, file, file_create_mode,
                            msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);
    if (open_result != FILE_OP_SUCCESS) {
        result = file_op_result_to_storage_err(open_result);
        goto err_open_file;
    }

    if ((flags & STORAGE_FILE_OPEN_TRUNCATE) && file->size) {
        file_set_size(&session->tr, file, 0);
    }

    if (session->tr.failed) {
        SS_ERR("%s: transaction failed\n", __func__);
        result = STORAGE_ERR_GENERIC;
        goto err_transaction_failed;
    }

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
        if (session->tr.failed) {
            SS_ERR("%s: transaction commit failed\n", __func__);
            result = STORAGE_ERR_GENERIC;
            goto err_transaction_failed;
        }
    }

    struct storage_file_open_resp resp = {.handle = f_handle};

    out = &resp;
    out_size = sizeof(resp);

    result = STORAGE_NO_ERROR;
    goto done;

err_transaction_failed:
    file_close(file);
err_open_file:
    free_file_handle(session, f_handle);
err_create_file_handle:
err_get_path:
err_invalid_name:
err_invalid_mask:
err_invalid_size:
done:
    return send_response(session, result, msg, out, out_size);
}

static enum storage_err storage_file_close(
        struct storage_msg* msg,
        struct storage_file_close_req* req,
        size_t req_size,
        struct storage_client_session* session) {
    struct file_handle* file;

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        return STORAGE_ERR_NOT_VALID;
    }

    file = get_file_handle(session, req->handle);
    if (!file)
        return STORAGE_ERR_NOT_VALID;

    file_close(file);

    free_file_handle(session, req->handle);

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
        if (session->tr.failed) {
            SS_ERR("%s: transaction commit failed\n", __func__);
            return STORAGE_ERR_GENERIC;
        }
        return STORAGE_NO_ERROR;
    }

    return STORAGE_NO_ERROR;
}

static int storage_file_read(struct storage_msg* msg,
                             struct storage_file_read_req* req,
                             size_t req_size,
                             struct storage_client_session* session) {
    enum storage_err result = STORAGE_NO_ERROR;
    void* bufp = NULL;
    size_t buflen;
    size_t bytes_left, len;
    uint64_t offset;
    struct file_handle* file;
    void* out = NULL;
    size_t out_size = 0;
    size_t block_size = get_file_block_size(session->tr.fs);
    data_block_t block_num;
    const uint8_t* block_data;
    struct obj_ref block_data_ref = OBJ_REF_INITIAL_VALUE(block_data_ref);
    size_t block_offset;

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    file = get_file_handle(session, req->handle);
    if (!file) {
        SS_ERR("%s: invalid file handle (%d)\n", __func__, req->handle);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    buflen = req->size;
    if (buflen > STORAGE_MAX_BUFFER_SIZE - sizeof(*msg)) {
        SS_ERR("can't read more than %d bytes, requested %zu\n",
               STORAGE_MAX_BUFFER_SIZE, buflen);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    offset = req->offset;
    if (offset > file->size) {
        SS_ERR("can't read past end of file (%" PRIu64 " > %" PRIu64 ")\n",
               offset, file->size);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    // reuse the input buffer
    bufp = (uint8_t*)(msg + 1);

    /* calc number of bytes to read */
    if ((offset + buflen) > file->size) {
        bytes_left = (size_t)(file->size - offset);
    } else {
        bytes_left = buflen;
    }
    buflen = bytes_left; /* save to return it to caller */

    SS_INFO("%s: start 0x%x cnt %d\n", __func__, offset, bytes_left);

    result = STORAGE_NO_ERROR;
    while (bytes_left) {
        block_num = offset / block_size;
        block_data =
                file_get_block(&session->tr, file, block_num, &block_data_ref);
        block_offset = offset % block_size;
        len = (block_offset + bytes_left > block_size)
                      ? block_size - block_offset
                      : bytes_left;
        if (!block_data) {
            if (session->tr.failed) {
                SS_ERR("error reading block %" PRIu64 "\n", block_num);
                result = STORAGE_ERR_GENERIC;
                goto err_get_block;
            }
            memset(bufp, 0, len);
        } else {
            memcpy(bufp, block_data + block_offset, len);
            file_block_put(block_data, &block_data_ref);
        }

        bytes_left -= len;
        offset += len;
        bufp += len;
    }

    out = (uint8_t*)(msg + 1);
    out_size = buflen;

err_get_block:
err_invalid_input:
    return send_response(session, result, msg, out, out_size);
}

static enum storage_err storage_create_gap(
        struct storage_client_session* session,
        struct file_handle* file) {
    size_t block_size = get_file_block_size(session->tr.fs);
    data_block_t block_num;
    size_t block_offset;
    uint8_t* block_data;
    struct obj_ref block_data_ref = OBJ_REF_INITIAL_VALUE(block_data_ref);

    block_num = file->size / block_size;
    block_offset = file->size % block_size;

    if (block_offset) {
        /*
         * The file does not currently end on a block boundary.
         * We don't clear data in partial blocks when truncating
         * a file, so the last block could contain data that
         * should not be readable. We unconditionally clear the
         * exposed data when creating gaps in the file, as we
         * don't know if that data is already clear.
         */
        block_data = file_get_block_write(&session->tr, file, block_num, true,
                                          &block_data_ref);
        if (!block_data) {
            SS_ERR("error getting block %" PRIu64 "\n", block_num);
            return STORAGE_ERR_GENERIC;
        }

        memset(block_data + block_offset, 0, block_size - block_offset);
        file_block_put_dirty(&session->tr, file, block_num, block_data,
                             &block_data_ref);

        SS_INFO("%s: clear block at old size 0x%" PRIx64
                ", block_offset 0x%zx\n",
                __func__, file->size, block_offset);
    }
    return STORAGE_NO_ERROR;
}

static enum storage_err storage_file_write(
        struct storage_msg* msg,
        struct storage_file_write_req* req,
        size_t req_size,
        struct storage_client_session* session) {
    enum storage_err result = STORAGE_NO_ERROR;
    const void* bufp = NULL;
    size_t buflen;
    uint64_t offset, end_offset, bytes_left;
    size_t len;
    struct file_handle* file;
    size_t block_size = get_file_block_size(session->tr.fs);
    data_block_t block_num;
    uint8_t* block_data;
    struct obj_ref block_data_ref = OBJ_REF_INITIAL_VALUE(block_data_ref);
    size_t block_offset;

    if (req_size <= sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        return STORAGE_ERR_NOT_VALID;
    }

    file = get_file_handle(session, req->handle);
    if (!file) {
        SS_ERR("%s: invalid file handle (%d)\n", __func__, req->handle);
        return STORAGE_ERR_NOT_VALID;
    }

    offset = req->offset;
    if (offset > file->size) {
        result = storage_create_gap(session, file);
        if (result != STORAGE_NO_ERROR) {
            goto err_write;
        }
    }

    bufp = req->data;
    buflen = req_size - sizeof(*req);

    end_offset = offset + buflen - 1;
    bytes_left = end_offset - offset + 1;

    /* transfer data one ss block at a time */
    while (bytes_left) {
        block_num = offset / block_size;
        block_offset = offset % block_size;
        len = (block_offset + bytes_left > block_size)
                      ? block_size - block_offset
                      : bytes_left;

        block_data = file_get_block_write(&session->tr, file, block_num,
                                          len != block_size, &block_data_ref);
        if (!block_data) {
            SS_ERR("error getting block %" PRIu64 "\n", block_num);
            result = STORAGE_ERR_GENERIC;
            goto err_write;
        }

        memcpy(block_data + block_offset, bufp, len);
        file_block_put_dirty(&session->tr, file, block_num, block_data,
                             &block_data_ref);

#if TLOG_LVL >= TLOG_LVL_DEBUG
        SS_INFO("%s: bufp %p offset 0x%" PRIx64 " len 0x%x\n", __func__, bufp,
                offset, len);
#endif
        bytes_left -= len;
        offset += len;
        bufp += len;
    }

    if (offset > file->size) {
        file_set_size(&session->tr, file, offset);
    }

    if (session->tr.failed) {
        SS_ERR("%s: transaction failed\n", __func__);
        return STORAGE_ERR_GENERIC;
    }

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
        if (session->tr.failed) {
            SS_ERR("%s: transaction commit failed\n", __func__);
            return STORAGE_ERR_GENERIC;
        }
    }

    return STORAGE_NO_ERROR;

err_write:
    if (!session->tr.failed) {
        transaction_fail(&session->tr);
    }
err_transaction_complete:
    return result;
}

struct storage_file_list_state {
    struct file_iterate_state iter;
    char prefix[34];
    size_t prefix_len;
    uint8_t buf[1024];
    size_t buf_used;
    uint8_t max_count;
    uint8_t count;
};

static bool storage_file_list_buf_full(struct storage_file_list_state* miter) {
    size_t max_item_size = FS_PATH_MAX - miter->prefix_len + 2;

    if (miter->max_count && miter->count >= miter->max_count) {
        return true;
    }

    return miter->buf_used + max_item_size > sizeof(miter->buf);
}

static void storage_file_list_add(struct storage_file_list_state* miter,
                                  uint8_t flags,
                                  const char* path) {
    struct storage_file_list_resp* resp;

    assert(!storage_file_list_buf_full(miter));

    resp = (void*)(miter->buf + miter->buf_used);
    resp->flags = flags;
    miter->buf_used++;
    miter->count++;
    if (path) {
        strcpy(resp->name, path);
        miter->buf_used += strlen(path) + 1;
    }
}

static bool storage_file_list_iter(struct file_iterate_state* iter,
                                   struct transaction* tr,
                                   const struct block_mac* block_mac,
                                   bool added,
                                   bool removed) {
    struct storage_file_list_state* miter =
            containerof(iter, struct storage_file_list_state, iter);
    const struct file_info* file_info;
    struct obj_ref ref = OBJ_REF_INITIAL_VALUE(ref);

    file_info = file_get_info(tr, block_mac, &ref);

    if (strncmp(file_info->path, miter->prefix, miter->prefix_len) == 0) {
        storage_file_list_add(miter,
                              added     ? STORAGE_FILE_LIST_ADDED
                              : removed ? STORAGE_FILE_LIST_REMOVED
                                        : STORAGE_FILE_LIST_COMMITTED,
                              file_info->path + miter->prefix_len);
    }

    file_info_put(file_info, &ref);

    return storage_file_list_buf_full(miter);
}

static int storage_file_list(struct storage_msg* msg,
                             struct storage_file_list_req* req,
                             size_t req_size,
                             struct storage_client_session* session) {
    enum storage_err result = STORAGE_NO_ERROR;
    void* out = NULL;
    size_t out_size = 0;
    enum file_op_result iterate_res;
    const char* last_name;
    char path_buf[FS_PATH_MAX];
    uint8_t last_state;
    const char* fname;
    size_t fname_len;
    struct storage_file_list_state state = {
            .iter.file = storage_file_list_iter,
            .buf_used = 0,
    };

    if (req_size < sizeof(*req)) {
        SS_ERR("%s: invalid request size (%zu)\n", __func__, req_size);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    result =
            get_path(state.prefix, sizeof(state.prefix), &session->uuid, "", 0);
    if (result != STORAGE_NO_ERROR) {
        SS_ERR("%s: internal error, get_path failed\n", __func__);
        result = STORAGE_ERR_GENERIC;
        goto err_get_path_prefix;
    }
    state.prefix_len = strlen(state.prefix);

    last_state = req->flags & STORAGE_FILE_LIST_STATE_MASK;

    if (last_state == STORAGE_FILE_LIST_END) {
        SS_ERR("%s: invalid request state (%d)\n", __func__, last_state);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_state;
    }

    if (last_state == STORAGE_FILE_LIST_START) {
        last_name = NULL;
    } else {
        /* make sure filename is legal */
        fname = req->name;
        fname_len = req_size - sizeof(*req);
        if (!is_valid_name(fname, fname_len)) {
            SS_ERR("%s: invalid filename\n", __func__);
            result = STORAGE_ERR_NOT_VALID;
            goto err_invalid_filename;
        }

        result = get_path(path_buf, sizeof(path_buf), &session->uuid, fname,
                          fname_len);
        if (result != STORAGE_NO_ERROR) {
            goto err_invalid_filename;
        }

        last_name = path_buf;
    }

    if (last_state != STORAGE_FILE_LIST_ADDED) {
        iterate_res =
                file_iterate(&session->tr, last_name, false, &state.iter,
                             msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);
        last_name = NULL;
    } else {
        iterate_res = FILE_OP_SUCCESS;
    }
    if (iterate_res == FILE_OP_SUCCESS && !storage_file_list_buf_full(&state)) {
        iterate_res =
                file_iterate(&session->tr, last_name, true, &state.iter,
                             msg->flags & STORAGE_MSG_FLAG_FS_REPAIRED_ACK);
    }
    if (iterate_res != FILE_OP_SUCCESS) {
        SS_ERR("%s: file_iterate failed\n", __func__);
        result = file_op_result_to_storage_err(iterate_res);
        goto err_file_iterate;
    }

    if (!storage_file_list_buf_full(&state)) {
        storage_file_list_add(&state, STORAGE_FILE_LIST_END, NULL);
    }

    out = state.buf;
    out_size = state.buf_used;

err_file_iterate:
err_invalid_filename:
err_invalid_state:
err_get_path_prefix:
err_invalid_input:
    return send_response(session, result, msg, out, out_size);
}

static int storage_file_get_size(struct storage_msg* msg,
                                 struct storage_file_get_size_req* req,
                                 size_t req_size,
                                 struct storage_client_session* session) {
    bool valid;
    struct file_handle* file;
    enum storage_err result = STORAGE_NO_ERROR;
    void* out = NULL;
    size_t out_size = 0;

    if (req_size != sizeof(*req)) {
        SS_ERR("%s: inavlid request size (%zu)\n", __func__, req_size);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    file = get_file_handle(session, req->handle);
    if (!file) {
        SS_ERR("%s: invalid file handle (%d)\n", __func__, req->handle);
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    struct storage_file_get_size_resp resp;

    valid = file_get_size(&session->tr, file, &resp.size);
    if (!valid) {
        result = STORAGE_ERR_NOT_VALID;
        goto err_invalid_input;
    }

    out = &resp;
    out_size = sizeof(resp);

err_invalid_input:
    return send_response(session, result, msg, out, out_size);
}

static enum storage_err storage_file_set_size(
        struct storage_msg* msg,
        struct storage_file_set_size_req* req,
        size_t req_size,
        struct storage_client_session* session) {
    struct file_handle* file;
    uint64_t new_size;

    if (req_size != sizeof(*req)) {
        SS_ERR("%s: inavlid request size (%zu)\n", __func__, req_size);
        return STORAGE_ERR_NOT_VALID;
    }

    new_size = req->size;

    file = get_file_handle(session, req->handle);
    if (!file) {
        SS_ERR("%s: invalid file handle (%d)\n", __func__, req->handle);
        return STORAGE_ERR_NOT_VALID;
    }

    SS_INFO("%s: new size 0x%" PRIx64 ", old size 0x%" PRIx64 "\n", __func__,
            new_size, file->size);

    /* for now we only support shrinking the file */
    if (new_size > file->size) {
        enum storage_err result;
        result = storage_create_gap(session, file);
        if (result != STORAGE_NO_ERROR) {
            return result;
        }
        storage_create_gap(session, file);
    }

    /* check for nop */
    if (new_size != file->size) {
        /* update size */
        file_set_size(&session->tr, file, new_size);
    }

    /* try to commit */
    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
        transaction_complete_etc(
                &session->tr,
                msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
    }

    if (session->tr.failed) {
        SS_ERR("%s: transaction failed\n", __func__);
        return STORAGE_ERR_GENERIC;
    }

    return STORAGE_NO_ERROR;
}

static struct storage_client_session* chan_context_to_client_session(
        struct ipc_channel_context* ctx) {
    assert(ctx != NULL);
    struct storage_client_session* session;

    session = containerof(ctx, struct storage_client_session, context);
    assert(session->magic == STORAGE_CLIENT_SESSION_MAGIC);
    return session;
}

static struct client_port_context* port_context_to_client_port_context(
        struct ipc_port_context* context) {
    assert(context != NULL);

    return containerof(context, struct client_port_context, client_ctx);
}

static void client_channel_ops_init(struct ipc_channel_ops* ops) {
    ops->on_handle_msg = client_handle_msg;
    ops->on_disconnect = client_disconnect;
}

static struct ipc_channel_context* client_connect(
        struct ipc_port_context* parent_ctx,
        const uuid_t* peer_uuid,
        handle_t chan_handle) {
    struct client_port_context* client_port_context;
    struct storage_client_session* client_session;

    client_port_context = port_context_to_client_port_context(parent_ctx);

    client_session = calloc(1, sizeof(*client_session));
    if (client_session == NULL) {
        SS_ERR("out of memory allocating client session\n");
        return NULL;
    }

    client_session->magic = STORAGE_CLIENT_SESSION_MAGIC;

    client_session->files = NULL;
    client_session->files_count = 0;

    transaction_init(&client_session->tr, client_port_context->tr_state, false);

    /* cache identity information */
    memcpy(&client_session->uuid, peer_uuid, sizeof(*peer_uuid));

    client_channel_ops_init(&client_session->context.ops);
    return &client_session->context;
}

static void client_disconnect(struct ipc_channel_context* context) {
    struct storage_client_session* session;

    session = chan_context_to_client_session(context);

    if (list_in_list(&session->tr.allocated.node) && !session->tr.failed) {
        transaction_fail(&session->tr); /* discard partial transaction */
    }
    session_close_all_files(session);
    transaction_free(&session->tr);

    OPENSSL_cleanse(session, sizeof(struct storage_client_session));
    free(session);
}

static int send_response(struct storage_client_session* session,
                         enum storage_err result,
                         struct storage_msg* msg,
                         void* out,
                         size_t out_size) {
    size_t resp_buf_count = 1;
    if (result == STORAGE_NO_ERROR && out != NULL && out_size != 0) {
        ++resp_buf_count;
    }

    struct iovec resp_bufs[2];

    msg->cmd |= STORAGE_RESP_BIT;
    msg->flags = 0;
    msg->size = sizeof(struct storage_msg) + out_size;
    msg->result = result;

    resp_bufs[0].iov_base = msg;
    resp_bufs[0].iov_len = sizeof(struct storage_msg);

    if (resp_buf_count == 2) {
        resp_bufs[1].iov_base = out;
        resp_bufs[1].iov_len = out_size;
    }

    struct ipc_msg resp_ipc_msg = {
            .iov = resp_bufs,
            .num_iov = resp_buf_count,
    };

    return send_msg(session->context.common.handle, &resp_ipc_msg);
}

static int send_result(struct storage_client_session* session,
                       struct storage_msg* msg,
                       enum storage_err result) {
    return send_response(session, result, msg, NULL, 0);
}

static int client_handle_msg(struct ipc_channel_context* ctx,
                             void* msg_buf,
                             size_t msg_size) {
    struct storage_client_session* session;
    struct storage_msg* msg = msg_buf;
    size_t payload_len;
    enum storage_err result;
    void* payload;

    session = chan_context_to_client_session(ctx);

    if (msg_size < sizeof(struct storage_msg)) {
        SS_ERR("%s: invalid message of size (%zu)\n", __func__, msg_size);
        struct storage_msg err_msg = {.cmd = STORAGE_RESP_MSG_ERR};
        send_result(session, &err_msg, STORAGE_ERR_NOT_VALID);
        return ERR_NOT_VALID; /* would force to close connection */
    }

    payload_len = msg_size - sizeof(struct storage_msg);
    payload = msg->payload;

    if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT) {
        if (!(msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE)) {
            SS_ERR("%s: STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT cannot "
                   "be used without STORAGE_MSG_FLAG_TRANSACT_COMPLETE\n",
                   __func__);
            return send_result(session, msg, STORAGE_ERR_NOT_VALID);
        }

        if (!checkpoint_update_allowed(&session->tr)) {
            SS_ERR("%s: Checkpoint requested but not currently allowed.\n",
                   __func__);
            return send_result(session, msg, STORAGE_ERR_NOT_ALLOWED);
        }
    }

    /* abort transaction and clear sticky transaction error */
    if (msg->cmd == STORAGE_END_TRANSACTION) {
        if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
            /* Allow checkpoint creation without an active transaction */
            if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT &&
                !transaction_is_active(&session->tr)) {
                transaction_activate(&session->tr);
            }
            /* try to complete current transaction */
            if (transaction_is_active(&session->tr)) {
                transaction_complete_etc(
                        &session->tr,
                        msg->flags & STORAGE_MSG_FLAG_TRANSACT_CHECKPOINT);
            }
            if (session->tr.failed) {
                SS_ERR("%s: failed to complete transaction\n", __func__);
                /* clear transaction failed state */
                session->tr.failed = false;
                return send_result(session, msg, STORAGE_ERR_TRANSACT);
            }
            return send_result(session, msg, STORAGE_NO_ERROR);
        } else {
            /* discard current transaction */
            if (transaction_is_active(&session->tr)) {
                transaction_fail(&session->tr);
            }
            /* clear transaction failed state */
            session->tr.failed = false;
            return send_result(session, msg, STORAGE_NO_ERROR);
        }
    }

    if (session->tr.failed) {
        if (msg->flags & STORAGE_MSG_FLAG_TRANSACT_COMPLETE) {
            /* last command in current trunsaction: reset failed state and
             * return error */
            session->tr.failed = false;
        }
        return send_result(session, msg, STORAGE_ERR_TRANSACT);
    }

    if (!transaction_is_active(&session->tr)) {
        /* previous transaction complete */
        transaction_activate(&session->tr);
    }

    switch (msg->cmd) {
    case STORAGE_FILE_DELETE:
        result = storage_file_delete(msg, payload, payload_len, session);
        break;
    case STORAGE_FILE_MOVE:
        result = storage_file_move(msg, payload, payload_len, session);
        break;
    case STORAGE_FILE_OPEN:
        return storage_file_open(msg, payload, payload_len, session);
    case STORAGE_FILE_CLOSE:
        result = storage_file_close(msg, payload, payload_len, session);
        break;
    case STORAGE_FILE_WRITE:
        result = storage_file_write(msg, payload, payload_len, session);
        break;
    case STORAGE_FILE_READ:
        return storage_file_read(msg, payload, payload_len, session);
    case STORAGE_FILE_LIST:
        return storage_file_list(msg, payload, payload_len, session);
    case STORAGE_FILE_GET_SIZE:
        return storage_file_get_size(msg, payload, payload_len, session);
    case STORAGE_FILE_SET_SIZE:
        result = storage_file_set_size(msg, payload, payload_len, session);
        break;
    default:
        SS_ERR("%s: unsupported command 0x%x\n", __func__, msg->cmd);
        result = STORAGE_ERR_UNIMPLEMENTED;
        break;
    }

    return send_result(session, msg, result);
}

int client_create_port(struct ipc_port_context* client_ctx,
                       const char* port_name) {
    int ret;
    uint32_t flags = IPC_PORT_ALLOW_TA_CONNECT;
#if TEST_BUILD
    flags |= IPC_PORT_ALLOW_NS_CONNECT;
#endif

    /* start accepting client connections */
    client_ctx->ops.on_connect = client_connect;
    ret = ipc_port_create(client_ctx, port_name, 1, STORAGE_MAX_BUFFER_SIZE,
                          flags);
    if (ret < 0) {
        SS_ERR("%s: failure initializing client port (%d)\n", __func__, ret);
        return ret;
    }
    return 0;
}
