/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <stdbool.h>
#include <stdint.h>

#include "block_device.h"
#include "crypt.h"

#ifdef APP_STORAGE_BLOCK_CACHE_SIZE
#define BLOCK_CACHE_SIZE (APP_STORAGE_BLOCK_CACHE_SIZE)
#else
#define BLOCK_CACHE_SIZE (64)
#endif
#ifdef APP_STORAGE_MAIN_BLOCK_SIZE
#define MAX_BLOCK_SIZE (APP_STORAGE_MAIN_BLOCK_SIZE)
#else
#define MAX_BLOCK_SIZE (2048)
#endif

/**
 * enum block_cache_entry_data_state - State of a block cache entry's data
 * @BLOCK_ENTRY_DATA_INVALID:     Block entry does not contain valid data.
 * @BLOCK_ENTRY_DATA_LOADING:     Block entry data load is pending. State will
 *                                be updated when the load operation completes.
 * @BLOCK_ENTRY_DATA_LOAD_FAILED: Block data could not be loaded from the disk.
 *                                This may be caused by a transient I/O error.
 * @BLOCK_ENTRY_DATA_NOT_FOUND:   Block does not exist on disk.
 * @BLOCK_ENTRY_DATA_CLEAN_DECRYPTED: Block entry contains valid plaintext data
 *                                    that is either on disk or queued to be
 *                                    written to disk
 * @BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED: Block entry contains valid ciphertext data
 *                                    that is either on disk or queued to be
 *                                    written to disk.
 * @BLOCK_ENTRY_DATA_DIRTY_DECRYPTED: Block entry contains valid plaintext data
 *                                    that has not yet been queued for write
 *                                    back to disk. Data must be encrypted and
 *                                    written back or discarded before the cache
 *                                    entry can be reused.
 * @BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED: Block entry contains valid ciphertext data
 *                                    that has not yet been queued for write
 *                                    back to disk. Data must be written back or
 *                                    discarded before the cache entry can be
 *                                    reused.
 */
enum block_cache_entry_data_state {
    BLOCK_ENTRY_DATA_INVALID = 0,
    BLOCK_ENTRY_DATA_LOADING,
    BLOCK_ENTRY_DATA_LOAD_FAILED,
    BLOCK_ENTRY_DATA_NOT_FOUND,
    BLOCK_ENTRY_DATA_CLEAN_DECRYPTED,
    BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED,
    BLOCK_ENTRY_DATA_DIRTY_DECRYPTED,
    BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED,
};

/**
 * struct block_cache_entry - block cache entry
 * @guard1:                 Set to BLOCK_CACHE_GUARD_1 to detect out of bound
 *                          writes to data.
 * @data:                   Decrypted block data.
 * @guard2:                 Set to BLOCK_CACHE_GUARD_2 to detect out of bound
 *                          writes to data.
 * @key:                    Key to use for encrypt, decrypt and calculate_mac.
 * @dev:                    Device that block was read from and will be written
 *                          to.
 * @block:                  Block number in dev.
 * @block_size:             Size of block, but match dev->block_size.
 * @mac:                    Last calculated mac of encrypted block data.
 * @state:                  Current state of @data, indicating if data has been
 *                          loaded from disk or written into this cache entry.
 *                          This state is reset to %BLOCK_ENTRY_INVALID when a
 *                          cache entry previously containing a different block
 *                          is selected for reuse. See &enum
 *                          block_cache_entry_state for details.
 * @encrypted:              %true if @data is currently encrypted.
 * @dirty_ref:              Data is currently being modified. Only a single
 *                          reference should be allowed.
 * @dirty_mac:              Data has been modified. Mac needs to be updated
 *                          after encrypting block.
 * @dirty_tmp:              Data can be discarded by
 *                          block_cache_discard_transaction.
 * @pinned:                 Block cannot be reused if it fails to write.
 * @is_superblock:          Block is used as a superblock and files should be
 *                          synced before it is written.
 * @dirty_tr:               Transaction that modified block.
 * @obj:                    Reference tracking struct.
 * @lru_node:               List node for tracking least recently used cache
 *                          entries.
 * @io_op_node:             List node for tracking active read and write
 *                          operations.
 * @io_op:                  Currently active io operation.
 *
 * @dirty_ref, @dirty_mac, @dirty_tmp, and @dirty_tr are only relevant if @state
 * is %BLOCK_ENTRY_DATA_DIRTY, i.e. @data has been modified and not yet queued
 * for write or discarded.
 */
struct block_cache_entry {
    uint64_t guard1;
    uint8_t data[MAX_BLOCK_SIZE];
    uint64_t guard2;

    const struct key* key;
    struct block_device* dev;
    data_block_t block;
    size_t block_size;
    struct mac mac;
    enum block_cache_entry_data_state state;
    bool dirty_ref;
    bool dirty_mac;
    bool dirty_tmp;
    bool pinned;
    bool is_superblock;
    struct transaction* dirty_tr;

    struct obj obj;
    struct list_node lru_node;
    struct list_node io_op_node;
    enum {
        BLOCK_CACHE_IO_OP_NONE,
        BLOCK_CACHE_IO_OP_READ,
        BLOCK_CACHE_IO_OP_WRITE,
    } io_op;
};

#define BLOCK_CACHE_SIZE_BYTES \
    (sizeof(struct block_cache_entry[BLOCK_CACHE_SIZE]))
