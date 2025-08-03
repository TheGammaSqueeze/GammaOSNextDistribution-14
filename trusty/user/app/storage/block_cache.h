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

#pragma once

#include <lk/reflist.h>
#include <sys/types.h>

#include "block_device.h"

struct block_mac;
struct fs;
struct iv;
struct transaction;

enum block_read_error {
    BLOCK_READ_SUCCESS = 0,
    BLOCK_READ_IO_ERROR,
    BLOCK_READ_NO_DATA,
};

void block_cache_complete_read(struct block_device* dev,
                               data_block_t block,
                               const void* data,
                               size_t data_size,
                               enum block_read_error res);

enum block_write_error {
    BLOCK_WRITE_SUCCESS = 0,
    BLOCK_WRITE_FAILED,
    BLOCK_WRITE_FAILED_UNKNOWN_STATE,
    BLOCK_WRITE_SYNC_FAILED,
};

void block_cache_complete_write(struct block_device* dev,
                                data_block_t block,
                                enum block_write_error res);

void block_cache_init(void);

void block_cache_dev_destroy(struct block_device* dev);

void block_cache_clean_transaction(struct transaction* tr);

void block_cache_discard_transaction(struct transaction* tr, bool discard_all);

const void* block_get_no_read(struct transaction* tr,
                              data_block_t block,
                              struct obj_ref* ref);

const void* block_get_super(struct fs* fs,
                            data_block_t block,
                            struct obj_ref* ref);

const void* block_get_no_tr_fail(struct transaction* tr,
                                 const struct block_mac* block_mac,
                                 const struct iv* iv,
                                 struct obj_ref* ref);

const void* block_get(struct transaction* tr,
                      const struct block_mac* block_mac,
                      const struct iv* iv,
                      struct obj_ref* ref);

void* block_dirty(struct transaction* tr, const void* data, bool is_tmp);

bool block_is_clean(struct block_device* dev, data_block_t block);

void block_discard_dirty(const void* data);

void block_discard_dirty_by_block(struct block_device* dev, data_block_t block);

void block_put_dirty(struct transaction* tr,
                     void* data,
                     struct obj_ref* data_ref,
                     struct block_mac* block_mac,
                     void* block_mac_ref);

void block_put_dirty_no_mac(void* data,
                            struct obj_ref* data_ref,
                            bool allow_tampering);

void block_put_dirty_discard(void* data, struct obj_ref* data_ref);

void* block_get_write_no_read(struct transaction* tr,
                              data_block_t block,
                              bool is_tmp,
                              struct obj_ref* ref);

void* block_get_write(struct transaction* tr,
                      const struct block_mac* block_mac,
                      const struct iv* iv,
                      bool is_tmp,
                      struct obj_ref* ref);

void* block_get_cleared(struct transaction* tr,
                        data_block_t block,
                        bool is_tmp,
                        struct obj_ref* ref);

void* block_get_cleared_super(struct transaction* tr,
                              data_block_t block,
                              struct obj_ref* ref,
                              bool pinned);

void* block_move(struct transaction* tr,
                 const void* data,
                 data_block_t block,
                 bool is_tmp);

void* block_get_copy(struct transaction* tr,
                     const void* data,
                     data_block_t block,
                     bool is_tmp,
                     struct obj_ref* new_ref);

void block_put(const void* data, struct obj_ref* ref);

bool block_probe(struct fs* fs,
                 const struct block_mac* block_mac,
                 bool allow_invalid);

data_block_t data_to_block_num(const void* data); /* test api, remove ? */

unsigned int block_cache_debug_get_ref_block_count(void);
