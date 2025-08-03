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

#pragma once

#include <stdbool.h>
#include "block_cache.h"
#include "block_mac.h"

struct block_set;
struct fs;
struct transaction;

#define FS_PATH_MAX (64 + 128)

struct file_handle {
    struct list_node node;
    struct block_mac to_commit_block_mac;
    struct block_mac committed_block_mac;
    struct block_mac block_mac;
    data_block_t to_commit_size;
    data_block_t size;
    bool used_by_tr;
};

/**
 * struct file_info - On-disk file entry
 * @size:       File size in bytes.
 * @reserved:   Reserved for future use. Write 0, read ignore.
 * @path:       File path and name.
 */
struct file_info {
    data_block_t size;
    uint64_t reserved;
    char path[FS_PATH_MAX];
};

/**
 * struct file_iterate_state - File iterator state
 */
struct file_iterate_state {
    /**
     * file - Found file callback
     * @iter:       Iterator object.
     * @tr:         Transaction object.
     * @block_mac:  File entry block_mac.
     * @added:      %true if file was added in current transaction and has not
     *              yet been committed
     */
    bool (*file)(struct file_iterate_state* iter,
                 struct transaction* tr,
                 const struct block_mac* block_mac,
                 bool added,
                 bool removed);
};

size_t get_file_block_size(struct fs* fs);
const void* file_get_block(struct transaction* tr,
                           struct file_handle* file,
                           data_block_t file_block,
                           struct obj_ref* ref);
void* file_get_block_write(struct transaction* tr,
                           struct file_handle* file,
                           data_block_t file_block,
                           bool read,
                           struct obj_ref* ref);
void file_block_put(const void* data, struct obj_ref* data_ref);
void file_block_put_dirty(struct transaction* tr,
                          struct file_handle* file,
                          data_block_t file_block,
                          void* data,
                          struct obj_ref* data_ref);

const struct file_info* file_get_info(struct transaction* tr,
                                      const struct block_mac* block_mac,
                                      struct obj_ref* ref);
void file_info_put(const struct file_info* data, struct obj_ref* data_ref);

bool file_get_size(struct transaction* tr,
                   struct file_handle* file,
                   data_block_t* size);
void file_set_size(struct transaction* tr,
                   struct file_handle* file,
                   data_block_t size);

void file_print(struct transaction* tr, const struct file_handle* file);
void files_print(struct transaction* tr);

bool file_check(struct transaction* tr, const struct file_handle* file);

void file_transaction_complete(struct transaction* tr,
                               struct block_mac* new_files_block_mac);
void file_transaction_complete_failed(struct transaction* tr);

void file_transaction_success(struct transaction* tr);
void file_transaction_failed(struct transaction* tr);
void files_rebuild_free_set(struct transaction* tr,
                            struct block_set* new_free_set,
                            struct block_mac* files_root);

/* TODO: move to dir? */
enum file_create_mode {
    FILE_OPEN_NO_CREATE,
    FILE_OPEN_CREATE,
    FILE_OPEN_CREATE_EXCLUSIVE,
};

/**
 * enum file_op_result - Result of attempting to operate on a file
 * @FILE_OP_SUCCESS: File was opened successfully.
 * @FILE_OP_ERR_FAILED: Transaction failed while attempting to open the file.
 * @FILE_OP_ERR_EXIST: File was found but exclusive access was requested.
 * @FILE_OP_ERR_ALREADY_OPEN: File is already open in the provided
 *                            transaction.
 * @FILE_OP_ERR_NOT_FOUND: File was not found.
 * @FILE_OP_ERR_FS_REPAIRED: File system has been repaired which may have
 *                           impacted the requested file. Pass @allow_repaired
 *                           = true to accept the repaired state.
 */
enum file_op_result {
    FILE_OP_SUCCESS,
    FILE_OP_ERR_FAILED,
    FILE_OP_ERR_EXIST,
    FILE_OP_ERR_ALREADY_OPEN,
    FILE_OP_ERR_NOT_FOUND,
    FILE_OP_ERR_FS_REPAIRED,
};
enum file_op_result file_open(struct transaction* tr,
                              const char* path,
                              struct file_handle* file,
                              enum file_create_mode create,
                              bool allow_repaired);
void file_close(struct file_handle* file);

enum file_op_result file_delete(struct transaction* tr,
                                const char* path,
                                bool allow_repaired);
enum file_op_result file_move(struct transaction* tr,
                              struct file_handle* file,
                              const char* dest_path,
                              enum file_create_mode dest_create,
                              bool allow_repaired);
enum file_op_result file_iterate(struct transaction* tr,
                                 const char* start_path,
                                 bool added,
                                 struct file_iterate_state* state,
                                 bool allow_repaired);
