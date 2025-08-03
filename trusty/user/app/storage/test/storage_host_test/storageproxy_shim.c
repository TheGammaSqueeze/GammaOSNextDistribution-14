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

#include "storageproxy_shim.h"

#include <errno.h>
#include <fcntl.h>
#include <interface/storage/storage.h>
#include <limits.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <unistd.h>

#include "block_device_tipc.h"
#include "rpmb.h"
#include "rpmb_dev/rpmb_dev.h"

#define TLOG_TAG "ss-test"

#define DATA_DIRECTORY "rpmb_host_test_data"
#define PERSIST_DIRECTORY "persist"
#define RPMB_FILENAME "RPMB_DATA"
#define HOST_TEST_RPMB_SIZE 1024

static char data_directory[PATH_MAX];
static struct rpmb_dev_state rpmb_state = {
        .data_fd = -1,
};

/* If >0 silently ignore writes to NS backing data files. */
static int ignore_next_ns_write_count = 0;
void ignore_next_ns_writes(int count) {
    ignore_next_ns_write_count = count;
}

bool init_rpmb_state(const char* base_directory) {
    int rc;
    bool res = false;
    rc = snprintf(data_directory, PATH_MAX - 1, "%s/%s", base_directory,
                  DATA_DIRECTORY);
    if (rc < 0) {
        goto err_mkdir;
    }
    data_directory[PATH_MAX - 1] = '\0';
    rc = mkdir(data_directory, S_IWUSR | S_IRUSR | S_IXUSR);
    if (rc < 0) {
        if (errno != EEXIST) {
            goto err_mkdir;
        }
    }

#if HAS_FS_TDP
    char* tdp_directory =
            malloc(strlen(data_directory) + sizeof(PERSIST_DIRECTORY) + 2);
    if (!tdp_directory) {
        goto err_alloc_tdp;
    }
    rc = sprintf(tdp_directory, "%s/%s", data_directory, PERSIST_DIRECTORY);
    if (rc < 0) {
        goto err_tdp_dirname;
    }
    rc = mkdir(tdp_directory, S_IWUSR | S_IRUSR | S_IXUSR);
    if (rc < 0) {
        if (errno != EEXIST) {
            goto err_tdp_mkdir;
        }
    }
#endif

    char* rpmb_filename =
            malloc(strlen(data_directory) + sizeof(RPMB_FILENAME) + 2);
    if (!rpmb_filename) {
        goto err_alloc_rpmb;
    }
    rc = sprintf(rpmb_filename, "%s/%s", data_directory, RPMB_FILENAME);
    if (rc < 0) {
        goto err_rpmb_filename;
    }
    rpmb_state.data_fd =
            open(rpmb_filename, O_RDWR | O_CREAT | O_TRUNC, S_IWUSR | S_IRUSR);
    if (rpmb_state.data_fd < 0) {
        fprintf(stderr, "storageproxy_shim: Could not open backing file\n");
        goto err_open_rpmb;
    }

    /* Create new rpmb data file */
    if (rpmb_state.header.max_block == 0) {
        rpmb_state.header.max_block = HOST_TEST_RPMB_SIZE - 1;
    }
    rc = write(rpmb_state.data_fd, &rpmb_state.header,
               sizeof(rpmb_state.header));
    if (rc != sizeof(rpmb_state.header)) {
        fprintf(stderr,
                "storageproxy_shim: Failed to write rpmb data file: %d, %s\n",
                rc, strerror(errno));
        goto err_write_rpmb;
    }

    res = true;

err_write_rpmb:
err_open_rpmb:
err_rpmb_filename:
    free(rpmb_filename);
err_alloc_rpmb:
#if HAS_FS_TDP
err_tdp_mkdir:
err_tdp_dirname:
    free(tdp_directory);
err_alloc_tdp:
#endif
err_mkdir:
    return res;
}

void destroy_rpmb_state() {
    if (rpmb_state.data_fd >= 0) {
        close(rpmb_state.data_fd);
        rpmb_state.data_fd = -1;
    }
}

void fail_next_rpmb_writes(int count, bool commit_writes) {
    rpmb_state.fail_next_writes = count;
    rpmb_state.commit_failed_writes = commit_writes;
}

void fail_next_rpmb_reads(int count) {
    rpmb_state.fail_next_reads = count;
}

void fail_next_rpmb_get_counters(int count) {
    rpmb_state.fail_next_get_counters = count;
}

int rpmb_send(void* mmc_handle,
              void* reliable_write_buf,
              size_t reliable_write_size,
              void* write_buf,
              size_t write_buf_size,
              void* read_buf,
              size_t read_buf_size,
              bool sync,
              bool sync_checkpoint) {
    rpmb_state.res_count = read_buf_size / sizeof(struct rpmb_packet);
    assert(rpmb_state.res_count <= MAX_PACKET_COUNT);
    rpmb_state.cmd_count =
            (reliable_write_size + write_buf_size) / sizeof(struct rpmb_packet);
    assert(rpmb_state.cmd_count <= MAX_PACKET_COUNT);

    size_t cmd_index = reliable_write_size / sizeof(struct rpmb_packet);
    memcpy(&rpmb_state.cmd[0], reliable_write_buf, reliable_write_size);
    memcpy(&rpmb_state.cmd[cmd_index], write_buf, write_buf_size);

    rpmb_dev_process_cmd(&rpmb_state);

    memcpy(read_buf, rpmb_state.res, read_buf_size);
    return NO_ERROR;
}

int ns_open_file(handle_t ipc_handle,
                 const char* fname,
                 ns_handle_t* handlep,
                 bool create) {
    int rc;
    char* path = malloc(strlen(data_directory) + strlen(fname) + 2);
    if (!path) {
        rc = ERR_NO_MEMORY;
        goto err;
    }
    rc = sprintf(path, "%s/%s", data_directory, fname);
    if (rc < 0) {
        TLOGE("%s: asprintf failed\n", __func__);
        rc = ERR_GENERIC;
        goto err;
    }

    int flags = O_RDWR;
    rc = open(path, flags, S_IWUSR | S_IRUSR);
    if (create && rc == -1 && errno == ENOENT) {
        flags |= O_CREAT;
        rc = open(path, flags, S_IWUSR | S_IRUSR);
    }
    if (rc < 0) {
        fprintf(stderr, "shim %s: open of file %s failed: %s\n", __func__, path,
                strerror(errno));
        goto err;
    }
    *handlep = rc;
    rc = 0;

err:
    if (path) {
        free(path);
    }
    return rc;
}

void ns_close_file(handle_t ipc_handle, ns_handle_t handle) {
    int fd = handle;
    close(fd);
}

/* Helpers from storageproxyd */
static ssize_t write_with_retry(int fd,
                                const void* buf_,
                                size_t size,
                                off_t offset) {
    ssize_t rc;
    const uint8_t* buf = buf_;

    while (size > 0) {
        rc = pwrite(fd, buf, size, offset);
        if (rc < 0)
            return rc;
        size -= rc;
        buf += rc;
        offset += rc;
    }
    return 0;
}

static ssize_t read_with_retry(int fd, void* buf_, size_t size, off_t offset) {
    ssize_t rc;
    size_t rcnt = 0;
    uint8_t* buf = buf_;

    while (size > 0) {
        rc = pread(fd, buf, size, offset);
        if (rc < 0)
            return rc;
        if (rc == 0)
            break;
        size -= rc;
        buf += rc;
        offset += rc;
        rcnt += rc;
    }
    return rcnt;
}

static enum storage_err translate_errno(int error) {
    enum storage_err result;
    switch (error) {
    case 0:
        result = NO_ERROR;
        break;
    case EBADF:
    case EINVAL:
    case ENOTDIR:
    case EISDIR:
    case ENAMETOOLONG:
        result = ERR_NOT_VALID;
        break;
    default:
        result = ERR_GENERIC;
        break;
    }

    return result;
}

int ns_get_max_size(handle_t ipc_handle,
                    ns_handle_t handle,
                    data_block_t* size) {
    assert(size != NULL);
    *size = 0x10000000000;
    return 0;
}

int ns_read_pos(handle_t ipc_handle,
                ns_handle_t handle,
                ns_off_t pos,
                void* data,
                int data_size) {
    if (read_with_retry(handle, data, data_size, pos) != data_size) {
        fprintf(stderr, "shim %s: read failed: %s\n", __func__,
                strerror(errno));
        return translate_errno(errno);
    }
    return data_size;
}

int ns_write_pos(handle_t ipc_handle,
                 ns_handle_t handle,
                 ns_off_t pos,
                 const void* data,
                 int data_size,
                 bool is_userdata,
                 bool sync) {
    if (ignore_next_ns_write_count > 0) {
        if (ignore_next_ns_write_count != INT_MAX) {
            ignore_next_ns_write_count--;
        }
        return data_size;
    }
    if (write_with_retry(handle, data, data_size, pos)) {
        fprintf(stderr, "shim %s: write failed: %s\n", __func__,
                strerror(errno));
        return translate_errno(errno);
    }
    return data_size;
}
