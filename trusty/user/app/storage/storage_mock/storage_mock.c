/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <lib/storage/storage.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#define FILE_BUFFER_MAX 1000000
#define TEST_FILE_MAX 100
#define TEST_HANDLER_MAX 1000

#define MAX_CHUNK_SIZE 4040
#define MAX_PARAMETER_SIZE 10

/* File info structure representing a file on secure storage. */
struct FileInfo {
    char name[STORAGE_MAX_NAME_LENGTH_BYTES];
    size_t size;
    /* For test we just use a static buffer for file content. */
    uint8_t buf[FILE_BUFFER_MAX];
};

struct FileHandler {
    char name[STORAGE_MAX_NAME_LENGTH_BYTES];
    uint64_t file_handler;
    struct FileInfo* file_info;
};

static struct FileInfo* storage_mock_files[TEST_FILE_MAX];
static struct FileHandler* storage_mock_handlers[TEST_HANDLER_MAX];
static uint64_t storage_mock_handlers_count;
static uint64_t storage_mock_files_count;
static uint64_t storage_handler_num = 1;
static uint64_t storage_session_num = 1;
static bool storage_session_opened;

static struct FileHandler* get_file_handler(file_handle_t fh) {
    for (int i = 0; i < TEST_HANDLER_MAX; i++) {
        if (storage_mock_handlers[i] == NULL) {
            continue;
        }
        if (storage_mock_handlers[i]->file_handler == fh) {
            return storage_mock_handlers[i];
        }
    }
    return NULL;
}

static int not_implemented_handler(const char* operation) {
    fprintf(stderr,
            "%s is not supported in fake secure storage implementation.\n",
            operation);
    return ERR_NOT_IMPLEMENTED;
}

/*
 * Copied from client_tipc.c.
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

int storage_open_session(storage_session_t* session_p, const char* type) {
    if (storage_session_opened) {
        return not_implemented_handler("Using more than one session");
    }
    storage_session_opened = true;
    *session_p = storage_session_num;
    storage_session_num++;
    return NO_ERROR;
}

void storage_close_session(storage_session_t session) {
    storage_session_opened = false;
    return;
}

int storage_open_file(storage_session_t session,
                      file_handle_t* handle_p,
                      const char* name,
                      uint32_t flags,
                      uint32_t opflags) {
    if (strlen(name) >= STORAGE_MAX_NAME_LENGTH_BYTES) {
        return ERR_NOT_VALID;
    }

    if (!is_valid_name(name, strlen(name))) {
        return ERR_NOT_VALID;
    }

    if (storage_mock_handlers_count >= TEST_HANDLER_MAX) {
        return ERR_GENERIC;
    }
    struct FileInfo* file_info_p = NULL;
    for (int i = 0; i < TEST_FILE_MAX; i++) {
        if (storage_mock_files[i] == NULL) {
            continue;
        }
        if (!strcmp(storage_mock_files[i]->name, name)) {
            file_info_p = storage_mock_files[i];
        }
    }
    if (file_info_p == NULL) {
        /* File not found. */
        if (!(flags & STORAGE_FILE_OPEN_CREATE)) {
            return ERR_NOT_FOUND;
        } else {
            /* Create new file. */
            storage_mock_files[storage_mock_files_count] =
                    malloc(sizeof(struct FileInfo));
            memset(storage_mock_files[storage_mock_files_count], 0,
                   sizeof(struct FileInfo));
            strncpy(storage_mock_files[storage_mock_files_count]->name, name,
                    STORAGE_MAX_NAME_LENGTH_BYTES);
            file_info_p = storage_mock_files[storage_mock_files_count];
            storage_mock_files_count++;
        }
    } else {
        if ((flags & STORAGE_FILE_OPEN_CREATE) &&
            (flags & STORAGE_FILE_OPEN_CREATE_EXCLUSIVE)) {
            /* When CREATE_EXCLUSIVE is specified, opening existing file would
             * fail. */
            return ERR_ALREADY_EXISTS;
        }
        for (int i = 0; i < TEST_HANDLER_MAX; i++) {
            if (storage_mock_handlers[i] == NULL) {
                continue;
            }
            if (!strcmp(storage_mock_handlers[i]->name, name)) {
                /* open a same file second time is not allowed. */
                return ERR_NOT_ALLOWED;
            }
        }
        if (flags & STORAGE_FILE_OPEN_TRUNCATE) {
            /* Discard existing contents. */
            file_info_p->size = 0;
            memset(file_info_p->buf, 0, FILE_BUFFER_MAX);
        }
    }
    storage_mock_handlers[storage_mock_handlers_count] =
            malloc(sizeof(struct FileHandler));
    memset(storage_mock_handlers[storage_mock_handlers_count], 0,
           sizeof(struct FileHandler));
    strncpy(storage_mock_handlers[storage_mock_handlers_count]->name, name,
            STORAGE_MAX_NAME_LENGTH_BYTES);
    storage_mock_handlers[storage_mock_handlers_count]->file_handler =
            storage_handler_num;
    storage_mock_handlers[storage_mock_handlers_count]->file_info = file_info_p;
    *handle_p = storage_handler_num;
    storage_handler_num++;
    storage_mock_handlers_count++;
    return NO_ERROR;
}

void storage_close_file(file_handle_t fh) {
    for (int i = 0; i < TEST_HANDLER_MAX; i++) {
        if (storage_mock_handlers[i] == NULL) {
            continue;
        }
        if (storage_mock_handlers[i]->file_handler == fh) {
            free(storage_mock_handlers[i]);
            for (int j = i; j < TEST_HANDLER_MAX - 1; j++) {
                storage_mock_handlers[j] = storage_mock_handlers[j + 1];
            }
            return;
        }
    }
}

int storage_move_file(storage_session_t session,
                      file_handle_t handle,
                      const char* old_name,
                      const char* new_name,
                      uint32_t flags,
                      uint32_t opflags) {
    return not_implemented_handler("Moving file");
}

int storage_delete_file(storage_session_t session,
                        const char* name,
                        uint32_t opflags) {
    for (int i = 0; i < TEST_FILE_MAX; i++) {
        if (storage_mock_files[i] == NULL) {
            continue;
        }
        if (!strcmp(storage_mock_files[i]->name, name)) {
            free(storage_mock_files[i]);
            for (int j = i; j < TEST_FILE_MAX - 1; j++) {
                storage_mock_files[j] = storage_mock_files[j + 1];
            }
            /* Invalid all the handlers pointing to the non existing file. */
            for (int j = 0; j < TEST_HANDLER_MAX; j++) {
                if (storage_mock_handlers[j] == NULL) {
                    continue;
                }
                if (strcmp(storage_mock_handlers[j]->name, name)) {
                    storage_mock_handlers[j]->file_info = NULL;
                }
            }
            return NO_ERROR;
        }
    }
    return ERR_NOT_FOUND;
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
    return not_implemented_handler("Opening directory");
}

void storage_close_dir(storage_session_t session,
                       struct storage_open_dir_state* state) {
    not_implemented_handler("Closing directory");
    return;
}

int storage_read_dir(storage_session_t session,
                     struct storage_open_dir_state* state,
                     uint8_t* flags,
                     char* name,
                     size_t name_out_size) {
    return not_implemented_handler("Reading directory");
}

ssize_t storage_read(file_handle_t fh,
                     storage_off_t off,
                     void* buf,
                     size_t size) {
    struct FileHandler* file_handler_p = get_file_handler(fh);
    if (file_handler_p == NULL) {
        return ERR_NOT_VALID;
    }
    struct FileInfo* file_info = file_handler_p->file_info;
    if (file_info == NULL) {
        return ERR_NOT_VALID;
    }
    if (off > file_info->size) {
        return ERR_NOT_VALID;
    }
    size_t copy_size = size;
    if (off + copy_size > file_info->size) {
        copy_size = file_info->size - off;
    }
    memcpy(buf, file_info->buf + off, copy_size);
    return copy_size;
}

ssize_t storage_write(file_handle_t fh,
                      storage_off_t off,
                      const void* buf,
                      size_t size,
                      uint32_t opflags) {
    struct FileHandler* file_handler_p = get_file_handler(fh);
    if (file_handler_p == NULL) {
        return ERR_NOT_VALID;
    }
    struct FileInfo* file_info = file_handler_p->file_info;
    if (file_info == NULL) {
        return ERR_NOT_VALID;
    }
    if (off + size > FILE_BUFFER_MAX) {
        return ERR_GENERIC;
    }
    if (off > file_info->size) {
        // Write beyond EOF, pad with 0 first.
        size_t padding_size = off - file_info->size;
        memset(file_info->buf + file_info->size, 0, padding_size);
    }
    if (off + size > file_info->size) {
        file_info->size = off + size;
    }

    memcpy(file_info->buf + off, buf, size);

    return size;
}

int storage_set_file_size(file_handle_t fh,
                          storage_off_t file_size,
                          uint32_t opflags) {
    if (file_size > FILE_BUFFER_MAX) {
        return ERR_GENERIC;
    }
    struct FileHandler* file_handler_p = get_file_handler(fh);
    if (file_handler_p == NULL) {
        return ERR_NOT_VALID;
    }
    struct FileInfo* file_info = file_handler_p->file_info;
    if (file_info == NULL) {
        return ERR_NOT_VALID;
    }
    if (file_size > file_info->size) {
        // Write beyond EOF, pad with 0.
        size_t padding_size = file_size - file_info->size;
        memset(file_info->buf + file_info->size, 0, padding_size);
    }
    file_info->size = file_size;

    return NO_ERROR;
}

int storage_get_file_size(file_handle_t fh, storage_off_t* size_p) {
    struct FileHandler* file_handler_p = get_file_handler(fh);
    if (file_handler_p == NULL) {
        return ERR_NOT_VALID;
    }
    struct FileInfo* file_info = file_handler_p->file_info;
    if (file_info == NULL) {
        /* File has been deleted, return NO_ERROR. */
        size_p = 0;
        return NO_ERROR;
    }
    *size_p = file_info->size;
    return NO_ERROR;
}

int storage_end_transaction(storage_session_t session, bool complete) {
    if (!complete) {
        return not_implemented_handler("Discard transaction");
    }
    return NO_ERROR;
}
