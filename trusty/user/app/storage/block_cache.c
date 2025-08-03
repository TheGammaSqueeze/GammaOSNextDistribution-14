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

#include <assert.h>
#include <inttypes.h>
#include <limits.h>
#include <lk/reflist.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/crypto.h>
#include <openssl/rand.h>

#include "block_cache.h"
#include "block_cache_priv.h"
#include "crypt.h"
#include "debug.h"
#include "debug_stats.h"
#include "error_reporting.h"
#include "transaction.h"

static bool print_cache_lookup = false;
static bool print_cache_lookup_verbose = false;
static bool print_block_ops = false;
static bool print_block_load = false;
static bool print_block_store = false;
static bool print_block_move = false;
static bool print_block_decrypt_encrypt = false;
static bool print_clean_transaction = false;
static bool print_mac_update = false;
static bool print_cache_get_ref_block_count = true;

#define BLOCK_CACHE_GUARD_1 (0xdead0001dead0003)
#define BLOCK_CACHE_GUARD_2 (0xdead0005dead0007)

static struct list_node block_cache_lru = LIST_INITIAL_VALUE(block_cache_lru);
static struct block_cache_entry block_cache_entries[BLOCK_CACHE_SIZE];
static bool block_cache_init_called = false;

static bool block_cache_entry_data_is_valid(
        const struct block_cache_entry* entry) {
    return entry->state == BLOCK_ENTRY_DATA_CLEAN_DECRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED;
}

static bool block_cache_entry_data_is_dirty(
        const struct block_cache_entry* entry) {
    return entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED;
}

static bool block_cache_entry_data_is_encrypted(
        const struct block_cache_entry* entry) {
    return entry->state == BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED;
}

static bool block_cache_entry_data_is_decrypted(
        const struct block_cache_entry* entry) {
    return entry->state == BLOCK_ENTRY_DATA_CLEAN_DECRYPTED ||
           entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED;
}

static const char* block_cache_entry_data_state_name(
        enum block_cache_entry_data_state state) {
    switch (state) {
    case BLOCK_ENTRY_DATA_INVALID:
        return "BLOCK_ENTRY_DATA_INVALID";
    case BLOCK_ENTRY_DATA_LOADING:
        return "BLOCK_ENTRY_DATA_LOADING";
    case BLOCK_ENTRY_DATA_LOAD_FAILED:
        return "BLOCK_ENTRY_DATA_LOAD_FAILED";
    case BLOCK_ENTRY_DATA_NOT_FOUND:
        return "BLOCK_ENTRY_DATA_NOT_FOUND";
    case BLOCK_ENTRY_DATA_CLEAN_DECRYPTED:
        return "BLOCK_ENTRY_DATA_CLEAN_DECRYPTED";
    case BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED:
        return "BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED";
    case BLOCK_ENTRY_DATA_DIRTY_DECRYPTED:
        return "BLOCK_ENTRY_DATA_DIRTY_DECRYPTED";
    case BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED:
        return "BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED";
    }
}

/**
 * block_cache_queue_io_op - Helper function to start a read or write operation
 * @entry:      Cache entry.
 * @io_op:      BLOCK_CACHE_IO_OP_READ or BLOCK_CACHE_IO_OP_WRITE.
 *
 * Set io_op for cache entry and add it to the tail of the io_ops for the
 * block device that the cache entry belongs to.
 */
static void block_cache_queue_io_op(struct block_cache_entry* entry,
                                    int io_op) {
    assert(io_op == BLOCK_CACHE_IO_OP_READ || io_op == BLOCK_CACHE_IO_OP_WRITE);
    assert(entry->io_op == BLOCK_CACHE_IO_OP_NONE);
    assert(entry->dev);
    assert(!list_in_list(&entry->io_op_node));

    entry->io_op = io_op;
    list_add_tail(&entry->dev->io_ops, &entry->io_op_node);
}

/**
 * block_cache_queue_read - Start a read operation
 * @entry:      Cache entry.
 */
static void block_cache_queue_read(struct block_cache_entry* entry) {
    assert(!block_cache_entry_data_is_dirty(entry));
    entry->state = BLOCK_ENTRY_DATA_LOADING;
    block_cache_queue_io_op(entry, BLOCK_CACHE_IO_OP_READ);
    stats_timer_start(STATS_CACHE_START_READ);
    entry->dev->start_read(entry->dev, entry->block);
    stats_timer_stop(STATS_CACHE_START_READ);
}

/**
 * block_cache_queue_write - Start a write operation
 * @entry:      Cache entry.
 */
static void block_cache_queue_write(struct block_cache_entry* entry,
                                    const void* encrypted_data) {
    block_cache_queue_io_op(entry, BLOCK_CACHE_IO_OP_WRITE);
    stats_timer_start(STATS_CACHE_START_WRITE);
    entry->dev->start_write(entry->dev, entry->block, encrypted_data,
                            entry->block_size, entry->is_superblock);
    stats_timer_stop(STATS_CACHE_START_WRITE);
}

/**
 * block_cache_complete_io - Wait for io operation on block device to complete
 * @dev:        Block device to wait for
 */
static void block_cache_complete_io(struct block_device* dev) {
    while (!list_is_empty(&dev->io_ops)) {
        assert(dev->wait_for_io);
        dev->wait_for_io(dev);
    }
}

/**
 * block_cache_pop_io_op - Get cache entry for completed read or write operation
 * @dev:        Block device
 * @block:      Block number
 * @io_op:      BLOCK_CACHE_IO_OP_READ or BLOCK_CACHE_IO_OP_WRITE.
 *
 * Finds block cache entry that matches @dev and @block and remove it from
 * the io_ops queue of the block device.
 *
 * This is a helper function for block_cache_complete_read and
 * block_cache_complete_write.
 *
 * Return: Matching block cache entry.
 */
static struct block_cache_entry* block_cache_pop_io_op(struct block_device* dev,
                                                       data_block_t block,
                                                       unsigned int io_op) {
    struct block_cache_entry* entry;

    list_for_every_entry(&dev->io_ops, entry, struct block_cache_entry,
                         io_op_node) {
        if (entry->block == block) {
            assert(entry->dev == dev);
            assert(entry->io_op == io_op);
            entry->io_op = BLOCK_CACHE_IO_OP_NONE;
            list_delete(&entry->io_op_node);
            return entry;
        }
        assert(false); /* Out of order completion not expected */
    }
    assert(false); /* No matching entry found */

    return NULL;
}

/**
 * block_cache_complete_read - Read complete callback from block device
 * @dev:        Block device
 * @block:      Block number
 * @data:       Pointer to encrypted data, only valid if @res is
 *              &block_read_error.BLOCK_READ_SUCCESS
 * @data_size:  Data size, must match block size of device.
 * @res:        &block_read_error.BLOCK_READ_SUCCESS if read operation was
 *              successful, otherwise describes the error.
 *
 * Calculates mac and decrypts data into cache entry. Does not validate mac.
 */
void block_cache_complete_read(struct block_device* dev,
                               data_block_t block,
                               const void* data,
                               size_t data_size,
                               enum block_read_error res) {
    int ret;
    struct block_cache_entry* entry;

    assert(data_size <= sizeof(entry->data));
    assert(data_size == dev->block_size);

    entry = block_cache_pop_io_op(dev, block, BLOCK_CACHE_IO_OP_READ);
    assert(entry->state == BLOCK_ENTRY_DATA_LOADING);
    switch (res) {
    case BLOCK_READ_SUCCESS:
        /* handled below */
        break;
    case BLOCK_READ_IO_ERROR:
        printf("%s: load block %" PRIu64 " failed\n", __func__, entry->block);
        entry->state = BLOCK_ENTRY_DATA_LOAD_FAILED;
        return;
    case BLOCK_READ_NO_DATA:
        printf("%s: load block %" PRIu64 " failed, no data\n", __func__,
               entry->block);
        entry->state = BLOCK_ENTRY_DATA_NOT_FOUND;
        return;
    }
    assert(res == BLOCK_READ_SUCCESS);

    entry->block_size = data_size;
    /* TODO: change decrypt function to take separate in/out buffers */
    memcpy(entry->data, data, data_size);

    stats_timer_start(STATS_FS_READ_BLOCK_CALC_MAC);
    ret = calculate_mac(entry->key, &entry->mac, entry->data,
                        entry->block_size);
    stats_timer_stop(STATS_FS_READ_BLOCK_CALC_MAC);
    assert(!ret);

    /* TODO: check mac here instead of when getting data from the cache? */
    if (print_block_load) {
        printf("%s: load/decrypt block %" PRIu64 " complete\n", __func__,
               entry->block);
    }

    entry->state = BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED;
}

/**
 * block_cache_complete_write - Write complete callback from block device
 * @dev:        Block device
 * @block:      Block number
 * @failed:     true if write operation failed, and data is not on disc. If
 *              block device has tamper detection, e.g. rpmb, passing false here
 *              means that the secure side block device code has verified that
 *              the data was written to disk.
 */
void block_cache_complete_write(struct block_device* dev,
                                data_block_t block,
                                enum block_write_error res) {
    struct block_cache_entry* entry;

    entry = block_cache_pop_io_op(dev, block, BLOCK_CACHE_IO_OP_WRITE);
    if (print_block_store) {
        printf("%s: write block %" PRIu64 " complete\n", __func__,
               entry->block);
    }
    assert(entry->dirty_tr);
    if (res == BLOCK_WRITE_SUCCESS) {
        entry->dirty_tr = NULL;
        entry->pinned = false;
    } else {
        pr_err("write block %" PRIu64 " failed, fail transaction\n",
               entry->block);
        transaction_fail(entry->dirty_tr);

        if (res == BLOCK_WRITE_SYNC_FAILED) {
            /*
             * We have to fail ALL pending transactions here because an fsync
             * failed and we don't know which write caused that failure.
             *
             * TODO: Should we fail only transactions that write to non-secure
             * devices? I.e. not fail TP transactions?
             *
             * TODO: storageproxy could track which file failed to sync and
             * communicate this back so we only have to fail transactions that
             * touched that backing file.
             */
            pr_err("An fsync failed, fail all pending transactions\n");
            fs_fail_all_transactions();
        }

        /*
         * Failing the transaction must not clear the block number, as we rely
         * on the block number + pinned flag to reserve and reuse the block
         * cache entry when reinitializing a special transaction.
         */
        assert(block == entry->block);

        if (res == BLOCK_WRITE_FAILED_UNKNOWN_STATE) {
            /*
             * We don't know what was written, force superblock to be rewritten.
             * This must be done after we have failed the transaction in case we
             * need to reuse block that was part of this transaction.
             */
            fs_unknown_super_block_state_all();
        }
    }
}

/**
 * block_cache_entry_has_refs - Check if cache entry is referenced
 * @entry:      Cache entry
 *
 * Return: true if there are no references to @entry.
 */
static bool block_cache_entry_has_refs(struct block_cache_entry* entry) {
    return !list_is_empty(&entry->obj.ref_list);
}

/**
 * block_cache_entry_has_one_ref - Check if cache entry is referenced once
 * @entry:      Cache entry
 *
 * Return: true if there is a single reference to @entry.
 */
static bool block_cache_entry_has_one_ref(struct block_cache_entry* entry) {
    return list_length(&entry->obj.ref_list) == 1;
}

/**
 * block_cache_entry_decrypt - Decrypt cache entry
 * @entry:          Cache entry
 */
static void block_cache_entry_decrypt(struct block_cache_entry* entry) {
    int ret;
    const struct iv* iv = NULL; /* TODO: support external iv */
    void* decrypt_data;
    size_t decrypt_size;

    assert(block_cache_entry_data_is_encrypted(entry));

    decrypt_data = entry->data;
    decrypt_size = entry->block_size;
    if (!iv) {
        iv = (void*)entry->data;
        assert(decrypt_size > sizeof(*iv));
        decrypt_data += sizeof(*iv);
        decrypt_size -= sizeof(*iv);
    }
    stats_timer_start(STATS_FS_READ_BLOCK_DECRYPT);
    ret = decrypt(entry->key, decrypt_data, decrypt_size, iv);
    stats_timer_stop(STATS_FS_READ_BLOCK_DECRYPT);
    assert(!ret);

    if (print_block_decrypt_encrypt) {
        printf("%s: decrypt block %" PRIu64 " complete\n", __func__,
               entry->block);
    }

    if (entry->state == BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED) {
        entry->state = BLOCK_ENTRY_DATA_CLEAN_DECRYPTED;
    } else if (entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED) {
        /*
         * We leave blocks in DIRTY_ENCRYPTED state after computing a MAC but
         * before flushing the block from the cache. We may decrypt a block
         * again to read it before write back, which is fine as it will be
         * re-encrypted (with the same IV) when flushed for write back.
         */
        entry->state = BLOCK_ENTRY_DATA_DIRTY_DECRYPTED;
    } else {
        /* Covered by assert that the entry was encrypted above. */
        assert(false);
    }
}

/**
 * block_cache_entry_encrypt - Encrypt cache entry and update mac
 * @entry:          Cache entry
 */
static void block_cache_entry_encrypt(struct block_cache_entry* entry) {
    int ret;
    void* encrypt_data;
    size_t encrypt_size;
    struct mac mac;
    struct iv* iv = NULL; /* TODO: support external iv */

    assert(entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED);
    assert(!block_cache_entry_has_refs(entry));

    encrypt_data = entry->data;
    encrypt_size = entry->block_size;
    if (!iv) {
        iv = (void*)entry->data;
        assert(encrypt_size > sizeof(*iv));
        encrypt_data += sizeof(*iv);
        encrypt_size -= sizeof(*iv);
    }

    stats_timer_start(STATS_FS_WRITE_BLOCK_ENCRYPT);
    ret = encrypt(entry->key, encrypt_data, encrypt_size, iv);
    stats_timer_stop(STATS_FS_WRITE_BLOCK_ENCRYPT);
    assert(!ret);
    entry->state = BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED;
    if (print_block_decrypt_encrypt) {
        printf("%s: encrypt block %" PRIu64 " complete\n", __func__,
               entry->block);
    }

    if (!entry->dirty_mac) {
        mac = entry->mac;
    }

    stats_timer_start(STATS_FS_WRITE_BLOCK_CALC_MAC);
    ret = calculate_mac(entry->key, &entry->mac, entry->data,
                        entry->block_size);
    stats_timer_stop(STATS_FS_WRITE_BLOCK_CALC_MAC);
    assert(!ret);

    if (!entry->dirty_mac) {
        assert(!CRYPTO_memcmp(&mac, &entry->mac, sizeof(mac)));
    }
    entry->dirty_mac = false;
    // assert(!entry->parent || entry->parent->ref_count);
    // assert(!entry->parent || entry->parent->dirty_ref);
}

/**
 * block_cache_entry_clean - Write dirty cache entry to disc
 * @entry:          Cache entry
 *
 * Does not wait for write to complete.
 */
static void block_cache_entry_clean(struct block_cache_entry* entry) {
    if (!block_cache_entry_data_is_dirty(entry)) {
        return;
    }

    if (print_block_store) {
        printf("%s: encrypt block %" PRIu64 "\n", __func__, entry->block);
    }

    assert(entry->block_size <= sizeof(entry->data));
    if (entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED) {
        block_cache_entry_encrypt(entry);
    }
    assert(entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED);
    /* TODO: release ref to parent */

    assert(entry->dirty_tr);
    /*
     * We have to save the current transaction for this entry because we need it
     * to check for transaction failure after queueing the write. Transactions
     * are managed by the storage client layer, and thus will outlive this
     * function, which is internal to the block cache.
     */
    struct transaction* tr = entry->dirty_tr;

    assert(entry->dirty_tr->fs);
    struct transaction* itr = entry->dirty_tr->fs->initial_super_block_tr;
    /*
     * Block(s) in fs->initial_super_block_tr must be written before any other
     * blocks to the same filesystem.
     */
    if (itr && itr != entry->dirty_tr) {
        printf("%s: write initial superblock before block %" PRIu64 "\n",
               __func__, entry->block);
        transaction_initial_super_block_complete(itr);

        /*
         * Check that initial_super_block_tr was cleared. If it was not, it must
         * have failed to write the initial super block and the transaction
         * that entry belongs to must also fail.
         */
        if (entry->dirty_tr->fs->initial_super_block_tr) {
            /*
             * transaction_initial_super_block_complete() always reinitialize
             * initial_super_block_tr if the write failed.
             */
            assert(!entry->dirty_tr->fs->initial_super_block_tr->failed);
            transaction_fail(entry->dirty_tr);
            assert(entry->state == BLOCK_ENTRY_DATA_INVALID);
            return;
        }
    }

    block_cache_queue_write(entry, entry->data);

    /*
     * If we fail the transaction in block_cache_complete_write(), which is
     * currently called during block_cache_queue_write(), we will clear the
     * dirty flag on all cache entries associate with the transaction, including
     * the one we're currently trying to clean.
     *
     * We can't redundantly clear the flag again here if the transaction has
     * failed, because the write failure may have forced us to trigger
     * fs_unknown_super_block_state_all(). Triggering this function creates
     * writes for the current superblock state of each filesystem, and this may
     * have reused the (now) clean entry we are trying to clean. If so,
     * entry->dirty must stay set.
     */
    if (!tr->failed) {
        assert(entry->state == BLOCK_ENTRY_DATA_DIRTY_ENCRYPTED);
        entry->state = BLOCK_ENTRY_DATA_CLEAN_ENCRYPTED;
    }
}

/**
 * block_cache_entry_score - Get a keep score
 * @entry:      Block cache entry to check
 * @index:      Number of available entries before @entry in lru.
 *
 * Return: A score value indicating in what order entries that are close in the
 * lru should be replaced.
 */
static unsigned int block_cache_entry_score(struct block_cache_entry* entry,
                                            unsigned int index) {
    if (!entry->dev) {
        return UINT_MAX;
    }
    return index * (block_cache_entry_data_is_dirty(entry)
                            ? (entry->dirty_tmp ? 1 : 2)
                            : 4);
}

/**
 * block_cache_entry_discard_dirty - Discard cache entry (can be dirty).
 * @entry:      Block cache entry to discard
 */
static void block_cache_entry_discard_dirty(struct block_cache_entry* entry) {
    assert(!entry->dirty_ref);
    assert(!list_in_list(&entry->io_op_node));
    entry->state = BLOCK_ENTRY_DATA_INVALID;
    entry->dev = NULL;
    entry->block = DATA_BLOCK_INVALID;
    entry->dirty_tr = NULL;
    /* We have to unpin here because we're clearing the block number */
    entry->pinned = false;
    entry->is_superblock = false;

    entry->dirty_mac = false;
}

/**
 * block_cache_entry_discard - Discard cache entry (must be clean and unused).
 * @entry:      Block cache entry to discard
 */
static void block_cache_entry_discard(struct block_cache_entry* entry) {
    assert(!block_cache_entry_has_refs(entry));
    assert(!entry->dirty_ref);
    assert(!entry->dirty_tr);
    assert(!list_in_list(&entry->io_op_node));
    block_cache_entry_discard_dirty(entry);
}

/**
 * block_cache_lookup - Get cache entry for a specific block
 * @fs:         File system state object, or %NULL is @allocate is %false.
 * @dev:        Block device object.
 * @block:      Block number
 * @allocate:   If true, assign an unused entry to the specified @dev,@block
 *              if no matching entry is found.
 *
 * Return: cache entry matching @dev and @block. If no matching entry is found,
 * and @allocate is true, pick an unused entry and update it to match. If no
 * entry can be used, return NULL.
 */

static struct block_cache_entry* block_cache_lookup(struct fs* fs,
                                                    struct block_device* dev,
                                                    data_block_t block,
                                                    bool allocate) {
    struct block_cache_entry* entry;
    struct block_cache_entry* unused_entry = NULL;
    unsigned int unused_entry_score = 0;
    unsigned int score;
    unsigned int available = 0;
    unsigned int in_use = 0;

    assert(dev);
    assert(fs || !allocate);

    stats_timer_start(STATS_CACHE_LOOKUP);
    /*
     * We may need to attempt to find and flush a cache entry multiple times
     * before finding one that we could successfully use that was not reused
     * during the clean. This relies on the block cache being large enough to
     * hold a super block for each filesystem plus all currently referenced
     * blocks (which is less than the maximum block path length). We cap the
     * number of retries here to avoid an infinite loop, but we should only need
     * one retry attempt since the block cache is LRU and the fresh super block
     * will be the most recently used entry.
     */
    for (int retry = 0; retry < BLOCK_CACHE_SIZE; ++retry) {
        unused_entry = NULL;
        unused_entry_score = 0;
        available = 0;
        in_use = 0;

        list_for_every_entry(&block_cache_lru, entry, struct block_cache_entry,
                             lru_node) {
            assert(entry->guard1 == BLOCK_CACHE_GUARD_1);
            assert(entry->guard2 == BLOCK_CACHE_GUARD_2);
            if (entry->dev == dev && entry->block == block) {
                if (print_cache_lookup) {
                    printf("%s: block %" PRIu64
                           ", found cache entry %zd, state %s\n",
                           __func__, block, entry - block_cache_entries,
                           block_cache_entry_data_state_name(entry->state));
                }
                stats_timer_start(STATS_CACHE_LOOKUP_FOUND);
                stats_timer_stop(STATS_CACHE_LOOKUP_FOUND);
                goto done;
            }
            /*
             * Do not select any cache entries that have active references as
             * they aren't ready to flush, and do not select any pinned entries.
             * Pinned entries can only be flushed by
             * transaction_initial_super_block_complete() and may not be flushed
             * by another transaction. We need to keep special superblock writes
             * pinned in the cache because otherwise we might fill the cache up
             * with other data, flushing the special superblock, which might
             * fail to write. In this case we would leave no room to recreate
             * the write later, since the cache is full of data which can't be
             * flushed until the initial superblock write is completed.
             */
            if (!block_cache_entry_has_refs(entry) && !entry->pinned) {
                score = block_cache_entry_score(entry, available);
                available++;
                if (score >= unused_entry_score) {
                    unused_entry = entry;
                    unused_entry_score = score;
                }
                if (print_cache_lookup_verbose) {
                    printf("%s: block %" PRIu64
                           ", cache entry %zd available last used for %" PRIu64
                           "\n",
                           __func__, block, entry - block_cache_entries,
                           entry->block);
                }
            } else {
                /*
                 * Pinned entries must have a valid block number so they can be
                 * reused.
                 */
                if (entry->pinned) {
                    assert(entry->block != DATA_BLOCK_INVALID);
                }
                if (print_cache_lookup_verbose) {
                    printf("%s: block %" PRIu64
                           ", cache entry %zd in use for %" PRIu64 "\n",
                           __func__, block, entry - block_cache_entries,
                           entry->block);
                }
                in_use++;
            }
        }
        entry = unused_entry;

        if (!entry || !allocate) {
            if (print_cache_lookup) {
                printf("%s: block %" PRIu64
                       ", no available entries, %u in use, allocate %d\n",
                       __func__, block, in_use, allocate);
            }
            entry = NULL;
            goto done;
        }

        if (print_cache_lookup) {
            printf("%s: block %" PRIu64
                   ", use cache entry %zd, state %s, %u available, %u in_use\n",
                   __func__, block, entry - block_cache_entries,
                   block_cache_entry_data_state_name(entry->state), available,
                   in_use);
        }

        assert(!entry->dirty_ref);

        if (block_cache_entry_data_is_dirty(entry)) {
            stats_timer_start(STATS_CACHE_LOOKUP_CLEAN);
            block_cache_entry_clean(entry);
            block_cache_complete_io(entry->dev);
            stats_timer_stop(STATS_CACHE_LOOKUP_CLEAN);
        }

        /*
         * The chosen entry we are flushing can't have been a special superblock
         * write because we do not select pinned entries, however, any RPMB data
         * write may create a new pinned superblock entry if the RPMB write
         * failed but the write counter was incremented. In this case
         * block_cache_entry_clean() will create a new superblock write by
         * calling fs_unknown_super_block_state_all(). This new write may reuse
         * the block cache entry we just chose and cleaned, resulting in our
         * chosen entry now being pinned for a different transaction. In this
         * case we restart the search for a cache entry and try to pick (and if
         * needed clean) a new entry.
         */

        if (!entry->pinned) {
            /* We found a clean entry to use */
            break;
        }

        pr_warn("%s: Retrying attempt to lookup and (if needed) free a block cache entry. "
                "Entry block %" PRIu64 " was reused during cleaning.\n",
                __func__, entry->block);
    }
    assert(!block_cache_entry_data_is_dirty(entry));
    assert(!entry->dirty_mac);
    assert(!entry->dirty_tr);

    entry->dev = dev;
    entry->block = block;
    assert(dev->block_size <= sizeof(entry->data));
    entry->block_size = dev->block_size;
    entry->key = fs->key;
    entry->state = BLOCK_ENTRY_DATA_INVALID;
    entry->is_superblock = false;

done:
    stats_timer_stop(STATS_CACHE_LOOKUP);

    return entry;
}

enum cache_load_result {
    CACHE_LOAD_SUCCESS = 0,
    CACHE_LOAD_IO_FAILED,
    CACHE_LOAD_NO_DATA,
    CACHE_LOAD_MAC_MISMATCH,
};

/**
 * block_cache_load_entry - Get cache entry for a specific block
 * @entry:      Block cache entry to load.
 * @mac:        Optional mac.
 * @mac_size:   Size of @mac.
 *
 * If entry is not already loaded, attempt to load the block and optionally
 * compare with the expected @mac, if provided.
 *
 * Return: &cache_load_result.CACHE_LOAD_SUCCESS if the block (matching @mac, if
 * provided) was already in cache or was loaded successfully. Otherwise return a
 * relevant error.
 */
static enum cache_load_result block_cache_load_entry(
        struct block_cache_entry* entry,
        const void* mac,
        size_t mac_size) {
    if (!block_cache_entry_data_is_valid(entry)) {
        assert(!block_cache_entry_has_refs(entry));
        if (print_block_load) {
            printf("%s: request load block %" PRIu64 "\n", __func__,
                   entry->block);
        }
        block_cache_queue_read(entry);
        block_cache_complete_io(entry->dev);
    }
    if (!block_cache_entry_data_is_valid(entry)) {
        printf("%s: failed to load block %" PRIu64 ", state: %d\n", __func__,
               entry->block, entry->state);
        switch (entry->state) {
        case BLOCK_ENTRY_DATA_LOAD_FAILED:
            return CACHE_LOAD_IO_FAILED;
        case BLOCK_ENTRY_DATA_NOT_FOUND:
            return CACHE_LOAD_NO_DATA;
        default:
            assert(false && "Unexpected entry state");
        }
    }
    if (mac) {
        if (CRYPTO_memcmp(&entry->mac, mac, mac_size)) {
            printf("%s: block %" PRIu64 ", mac mismatch\n", __func__,
                   entry->block);
            return CACHE_LOAD_MAC_MISMATCH;
        }
    }
    /*
     * We eagerly encrypt a block when releasing it so that we can compute the
     * block's mac. If we re-load the same block before flushing it from the
     * cache, we may end up decrypting a dirty block here, so we want to allow
     * decryption of both clean and dirty blocks.
     */
    if (block_cache_entry_data_is_encrypted(entry)) {
        block_cache_entry_decrypt(entry);
    }
    assert(block_cache_entry_data_is_decrypted(entry));

    return CACHE_LOAD_SUCCESS;
}

/**
 * block_cache_get - Get cache entry for a specific block and add a reference
 * @fs:         File system state object.
 * @dev:        Block device object.
 * @block:      Block number.
 * @load:       If true, load data if needed.
 * @mac:        Optional mac. Unused if @load is false.
 * @mac_size:   Size of @mac.
 * @ref:        Pointer to store reference in.
 * @load_result: Optional output pointer to store load result in. May be %NULL.
 *               If not %NULL, @load must be %true.
 *
 * Find cache entry, optionally load then add a reference to it.
 *
 * Return: cache entry matching dev in @tr and @block. Can return NULL if @load
 * is true and entry could not be loaded or does not match provided mac.
 */
static struct block_cache_entry* block_cache_get(
        struct fs* fs,
        struct block_device* dev,
        data_block_t block,
        bool load,
        const void* mac,
        size_t mac_size,
        struct obj_ref* ref,
        enum cache_load_result* load_result) {
    enum cache_load_result res;
    struct block_cache_entry* entry;

    assert(dev);
    assert(!load_result || load);

    if (block >= dev->block_count) {
        printf("%s: bad block num %" PRIu64 " >= %" PRIu64 "\n", __func__,
               block, dev->block_count);
        if (load_result) {
            *load_result = CACHE_LOAD_NO_DATA;
        }
        return NULL;
    }
    assert(block < dev->block_count);

    entry = block_cache_lookup(fs, dev, block, true);
    assert(entry);

    if (load) {
        res = block_cache_load_entry(entry, mac, mac_size);
        if (res == CACHE_LOAD_MAC_MISMATCH) {
            error_report_block_mac_mismatch(fs->name, BLOCK_TYPE_UNKNOWN);
        }
        if (load_result) {
            *load_result = res;
        }
        if (res != CACHE_LOAD_SUCCESS) {
            return NULL;
        }
    }

    assert(!entry->dirty_ref);
    obj_add_ref_allow_unreferenced_obj(&entry->obj, ref);
    if (print_block_ops) {
        printf("%s: block %" PRIu64 ", cache entry %zd, state %s\n", __func__,
               block, entry - block_cache_entries,
               block_cache_entry_data_state_name(entry->state));
    }
    return entry;
}

/**
 * block_cache_get_data - Call block_cache_get and return data pointer
 * @fs:         File system state object.
 * @dev:        Block device object.
 * @block:      Block number.
 * @load:       If true, load data if needed.
 * @mac:        Optional mac. Unused if @load is false.
 * @mac_size:   Size of @mac.
 * @ref:        Pointer to store reference in.
 * @load_result: Optional output pointer to store load result in. May be %NULL.
 *               Only updated if @load is %true.
 *
 * Return: block data pointer, or NULL if block_cache_get returned NULL.
 */
static void* block_cache_get_data(struct fs* fs,
                                  struct block_device* dev,
                                  data_block_t block,
                                  bool load,
                                  const void* mac,
                                  size_t mac_size,
                                  struct obj_ref* ref,
                                  enum cache_load_result* load_result) {
    struct block_cache_entry* entry;
    entry = block_cache_get(fs, dev, block, load, mac, mac_size, ref,
                            load_result);
    if (!entry) {
        return NULL;
    }
    return entry->data;
}

/**
 * data_to_block_cache_entry - Get cache entry from data pointer
 * @data:       Pointer to data member of cache entry.
 *
 * Return: cache entry matching @data.
 */
static struct block_cache_entry* data_to_block_cache_entry(const void* data) {
    struct block_cache_entry* entry;

    assert(data);
    entry = containerof(data, struct block_cache_entry, data);
    assert(entry >= block_cache_entries);
    assert(entry < &block_cache_entries[BLOCK_CACHE_SIZE]);
    assert(((uintptr_t)entry - (uintptr_t)entry) % sizeof(entry[0]) == 0);
    return entry;
}

/**
 * data_to_block_cache_entry_or_null - Get cache entry or NULL from data pointer
 * @data:       Pointer to data member of cache entry or NULL.
 *
 * Return: cache entry matching @data, or NULL is data is NULL.
 */
static struct block_cache_entry* data_to_block_cache_entry_or_null(
        const void* data) {
    return data ? data_to_block_cache_entry(data) : NULL;
}

/**
 * block_cache_entry_destroy - Callback function for obj_del_ref
 * @obj:        Pointer to obj member of cache entry.
 *
 * Callback called by reference tracking code when the last reference to a
 * cache entry has been released. Since this is a cache, and not a normal heap
 * allocated object, the cache entry is not destroyed here. It is instead left
 * in a state where block_cache_lookup can reuse it.
 */
static void block_cache_entry_destroy(struct obj* obj) {
    struct block_cache_entry* entry =
            containerof(obj, struct block_cache_entry, obj);

    list_delete(&entry->lru_node);
    list_add_head(&block_cache_lru, &entry->lru_node);

    if (entry->dirty_mac) {
        block_cache_entry_encrypt(entry);
    }
}

/**
 * block_cache_init - Allocate and initialize block cache
 */
void block_cache_init(void) {
    int i;
    struct obj_ref ref;

    assert(!block_cache_init_called);

    block_cache_init_called = true;

    full_assert(memset(block_cache_entries, 1, sizeof(block_cache_entries)));

    for (i = 0; i < BLOCK_CACHE_SIZE; i++) {
        block_cache_entries[i].guard1 = BLOCK_CACHE_GUARD_1;
        block_cache_entries[i].guard2 = BLOCK_CACHE_GUARD_2;
        block_cache_entries[i].dev = NULL;
        block_cache_entries[i].block = DATA_BLOCK_INVALID;
        block_cache_entries[i].state = BLOCK_ENTRY_DATA_INVALID;
        block_cache_entries[i].dirty_ref = false;
        block_cache_entries[i].dirty_mac = false;
        block_cache_entries[i].pinned = false;
        block_cache_entries[i].is_superblock = false;
        block_cache_entries[i].dirty_tr = NULL;
        block_cache_entries[i].io_op = BLOCK_CACHE_IO_OP_NONE;
        obj_init(&block_cache_entries[i].obj, &ref);
        list_clear_node(&block_cache_entries[i].io_op_node);
        list_add_head(&block_cache_lru, &block_cache_entries[i].lru_node);
        obj_del_ref(&block_cache_entries[i].obj, &ref,
                    block_cache_entry_destroy);
    }
}

/**
 * block_cache_dev_destroy - Discard all blocks associated with device
 * @dev:        Block device to remove
 */
void block_cache_dev_destroy(struct block_device* dev) {
    int i;
    for (i = 0; i < BLOCK_CACHE_SIZE; i++) {
        if (block_cache_entries[i].dev == dev) {
            block_cache_entry_discard(&block_cache_entries[i]);
        }
    }
}

/**
 * block_cache_clean_transaction - Clean blocks modified by transaction
 * @tr:         Transaction
 */
void block_cache_clean_transaction(struct transaction* tr) {
    struct block_cache_entry* entry;
    struct block_device* dev = NULL;

    stats_timer_start(STATS_CACHE_CLEAN_TRANSACTION);

    list_for_every_entry(&block_cache_lru, entry, struct block_cache_entry,
                         lru_node) {
        assert(entry->guard1 == BLOCK_CACHE_GUARD_1);
        assert(entry->guard2 == BLOCK_CACHE_GUARD_2);
        if (entry->dirty_tr != tr) {
            continue;
        }

        assert(block_cache_entry_data_is_dirty(entry));

        assert(!entry->dirty_ref);

        if (entry->dirty_tmp) {
            continue;
        }

        if (!dev) {
            dev = entry->dev;
            assert(dev == tr->fs->dev || dev == tr->fs->super_dev);
        }

        assert(entry->dev == dev);

        if (print_clean_transaction) {
#if TLOG_LVL >= TLOG_LVL_DEBUG
            printf("%s: tr %p, block %" PRIu64 "\n", __func__, tr,
                   entry->block);
#else
            printf("%s: transaction block %" PRIu64 "\n", __func__,
                   entry->block);
#endif
        }

        assert(!block_cache_entry_has_refs(entry));
        stats_timer_start(STATS_CACHE_CLEAN_TRANSACTION_ENT_CLN);
        block_cache_entry_clean(entry);
        stats_timer_stop(STATS_CACHE_CLEAN_TRANSACTION_ENT_CLN);
        assert(entry->dirty_tr != tr);
        if (!tr->failed) {
            /*
             * If the write failed we may have reused this block cache entry for
             * a super block write and it therefore might not be clean.
             */
            assert(!block_cache_entry_data_is_dirty(entry));
            assert(!entry->dirty_tr);
        }
    }

    if (dev) {
        stats_timer_start(STATS_CACHE_CLEAN_TRANSACTION_WAIT_IO);
        block_cache_complete_io(dev);
        stats_timer_stop(STATS_CACHE_CLEAN_TRANSACTION_WAIT_IO);
    }
    stats_timer_stop(STATS_CACHE_CLEAN_TRANSACTION);
}

/**
 * block_cache_discard_transaction - Discard blocks modified by transaction
 * @tr:             Transaction
 * @discard_all:    If true, discard all dirty blocks modified by @tr. If false,
 *                  discard tmp dirty blocks modified by @tr.
 *
 * If @discard_all is %false, only tmp blocks should be dirty. @discard_all
 * therefore only affects errors checks.
 */
void block_cache_discard_transaction(struct transaction* tr, bool discard_all) {
    struct block_cache_entry* entry;
    struct block_device* dev = NULL;

    list_for_every_entry(&block_cache_lru, entry, struct block_cache_entry,
                         lru_node) {
        assert(entry->guard1 == BLOCK_CACHE_GUARD_1);
        assert(entry->guard2 == BLOCK_CACHE_GUARD_2);
        if (entry->dirty_tr != tr) {
            continue;
        }

        if (entry->dirty_tmp) {
            /* tmp blocks should never be on the superblock device */
            assert(entry->dev == tr->fs->dev);
        } else {
            /*
             * An transaction should never have dirty non-tmp blocks both
             * devices at the same time.
             */
            if (!dev) {
                dev = entry->dev;
                assert(dev == tr->fs->dev || dev == tr->fs->super_dev);
            }
            assert(entry->dev == dev);
        }
        assert(block_cache_entry_data_is_dirty(entry));

        if (print_clean_transaction) {
#if TLOG_LVL >= TLOG_LVL_DEBUG
            printf("%s: tr %p, block %" PRIu64 ", tmp %d\n", __func__, tr,
                   entry->block, entry->dirty_tmp);
#else
            printf("%s: transaction block %" PRIu64 ", tmp %d\n", __func__,
                   entry->block, entry->dirty_tmp);
#endif
        }

        if (block_cache_entry_has_refs(entry)) {
#if TLOG_LVL >= TLOG_LVL_DEBUG
            pr_warn("tr %p, block %" PRIu64 " has ref (dirty_ref %d)\n", tr,
                    entry->block, entry->dirty_ref);
#else
            pr_warn("transaction block %" PRIu64 " has ref (dirty_ref %d)\n",
                    entry->block, entry->dirty_ref);
#endif
        } else {
            assert(!entry->dirty_ref);
        }
        if (!discard_all) {
            assert(!block_cache_entry_has_refs(entry));
            assert(entry->dirty_tmp);
        }
        entry->dirty_tr = NULL;
        entry->state = BLOCK_ENTRY_DATA_INVALID;
        assert(!entry->dirty_tr);
    }
}

/**
 * block_get_no_read - Get block data without read
 * @tr:         Transaction to get device from
 * @block:      Block number
 * @ref:        Pointer to store reference in.
 *
 * Return: Const block data pointer.
 *
 * This is only useful if followed by block_dirty.
 */
const void* block_get_no_read(struct transaction* tr,
                              data_block_t block,
                              struct obj_ref* ref) {
    assert(tr);
    assert(tr->fs);

    return block_cache_get_data(tr->fs, tr->fs->dev, block, false, NULL, 0, ref,
                                NULL);
}

/**
 * block_get_super - Get super block data without checking mac
 * @fs:         File system state object.
 * @block:      Block number.
 * @ref:        Pointer to store reference in.
 *
 * Return: Const block data pointer.
 */
const void* block_get_super(struct fs* fs,
                            data_block_t block,
                            struct obj_ref* ref) {
    assert(fs);
    assert(fs->super_dev);
    assert((fs->allow_tampering && !fs->super_dev->tamper_detecting) ||
           (!fs->allow_tampering && fs->super_dev->tamper_detecting));

    return block_cache_get_data(fs, fs->super_dev, block, true, NULL, 0, ref,
                                NULL);
}

/**
 * block_get_no_tr_fail - Get block data
 * @tr:         Transaction to get device from
 * @block_mac:  Block number and mac
 * @iv:         Initial vector used to decrypt block, or NULL. If NULL, the
 *              start of the loaded block data is used as the iv.
 *              Only NULL is currently supported.
 * @ref:        Pointer to store reference in.
 *
 * Return: Const block data pointer, or NULL if mac of loaded data does not mac
 * in @block_mac or a read error was reported by the block device when loading
 * the data.
 */
const void* block_get_no_tr_fail(struct transaction* tr,
                                 const struct block_mac* block_mac,
                                 const struct iv* iv,
                                 struct obj_ref* ref) {
    data_block_t block;
    void* data;
    enum cache_load_result load_result = CACHE_LOAD_NO_DATA;

    assert(tr);
    assert(tr->fs);
    assert(block_mac);

    block = block_mac_to_block(tr, block_mac);
    assert(block);

    data = block_cache_get_data(tr->fs, tr->fs->dev, block, true,
                                block_mac_to_mac(tr, block_mac),
                                tr->fs->mac_size, ref, &load_result);
    if (load_result == CACHE_LOAD_MAC_MISMATCH ||
        load_result == CACHE_LOAD_NO_DATA) {
        tr->invalid_block_found = true;
    }
    return data;
}

/**
 * block_get - Get block data
 * @tr:         Transaction to get device from
 * @block_mac:  Block number and mac
 * @iv:         Initial vector used to decrypt block, or NULL. If NULL, the
 *              start of the loaded block data is used as the iv.
 *              Only NULL is currently supported.
 * @ref:        Pointer to store reference in.
 *
 * Return: Const block data pointer, or NULL if the transaction has failed. A
 * transaction failure is triggered if mac of loaded data does not mac in
 * @block_mac or a read error was reported by the block device when loading the
 * data.
 */
const void* block_get(struct transaction* tr,
                      const struct block_mac* block_mac,
                      const struct iv* iv,
                      struct obj_ref* ref) {
    const void* data;

    assert(tr);

    if (tr->failed) {
        pr_warn("transaction already failed\n");
        return NULL;
    }

    data = block_get_no_tr_fail(tr, block_mac, iv, ref);
    if (!data && !tr->failed) {
        pr_warn("transaction failed\n");
        transaction_fail(tr);
        if (tr->invalid_block_found) {
            fs_mark_scan_required(tr->fs);
        }
    }
    return data;
}

/**
 * block_dirty - Mark cache entry dirty and return non-const block data pointer.
 * @tr:         Transaction
 * @data:       Const block data pointer
 * @is_tmp:     If true, data is only needed until @tr is commited.
 *
 * Return: Non-const block data pointer.
 */
void* block_dirty(struct transaction* tr, const void* data, bool is_tmp) {
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    assert(tr);
    assert(list_in_list(&tr->node)); /* transaction must be active */
    assert(!entry->dirty_tr || entry->dirty_tr == tr);
    assert(!entry->dirty_ref);
    assert(fs_is_writable(tr->fs));

    if (block_cache_entry_data_is_encrypted(entry)) {
        if (print_block_ops) {
            printf("%s: skip decrypt block %" PRIu64 "\n", __func__,
                   entry->block);
        }
    } else if (entry->state != BLOCK_ENTRY_DATA_CLEAN_DECRYPTED) {
        if (print_block_ops) {
            printf("%s: Dirtying block %" PRIu64
                   " that was not loaded. Previous state: %s\n",
                   __func__, entry->block,
                   block_cache_entry_data_state_name(entry->state));
        }
    }
    assert(block_cache_entry_has_one_ref(entry));
    entry->state = BLOCK_ENTRY_DATA_DIRTY_DECRYPTED;
    entry->dirty_ref = true;
    entry->dirty_tmp = is_tmp;
    entry->dirty_tr = tr;
    return (void*)data;
}

/**
 * block_is_clean - Check if block is clean
 * @dev:        Block device
 * @block:      Block number
 *
 * Return: %true if there is no matching dirty cache entry, %false if the cache
 * contains a dirty block matching @dev and @block.
 */
bool block_is_clean(struct block_device* dev, data_block_t block) {
    struct block_cache_entry* entry;

    entry = block_cache_lookup(NULL, dev, block, false);
    return !entry || !block_cache_entry_data_is_dirty(entry);
}

/**
 * block_discard_dirty - Discard dirty cache data.
 * @data:       Block data pointer
 */
void block_discard_dirty(const void* data) {
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    if (block_cache_entry_data_is_dirty(entry)) {
        assert(entry->dev);
        block_cache_entry_discard_dirty(entry);
    }
}

/**
 * block_discard_dirty_by_block - Discard cache entry if dirty.
 * @dev:        Block device
 * @block:      Block number
 */
void block_discard_dirty_by_block(struct block_device* dev,
                                  data_block_t block) {
    struct block_cache_entry* entry;

    entry = block_cache_lookup(NULL, dev, block, false);
    if (!entry) {
        return;
    }
    assert(!entry->dirty_ref);
    assert(!block_cache_entry_has_refs(entry));
    if (!block_cache_entry_data_is_dirty(entry)) {
        return;
    }
    block_discard_dirty(entry->data);
}

/**
 * block_put_dirty - Release reference to dirty block.
 * @tr:             Transaction
 * @data:           Block data pointer
 * @data_ref:       Reference pointer to release
 * @block_mac:      block_mac pointer to update after encryting block
 * @block_mac_ref:  Block data pointer that @block_mac belongs to, or NULL if
 *                  @block_mac points to a memory only location.
 *
 * Helper function to for block_put_dirty, block_put_dirty_no_mac and
 * block_put_dirty_discard.
 */
static void block_put_dirty_etc(struct transaction* tr,
                                void* data,
                                struct obj_ref* data_ref,
                                struct block_mac* block_mac,
                                void* block_mac_ref) {
    int ret;
    struct block_cache_entry* entry = data_to_block_cache_entry(data);
    struct block_cache_entry* parent =
            data_to_block_cache_entry_or_null(block_mac_ref);
    struct iv* iv = (void*)entry->data; /* TODO: support external iv */

    if (tr) {
        assert(block_mac);
        assert(entry->state == BLOCK_ENTRY_DATA_DIRTY_DECRYPTED);
        assert(entry->dirty_ref);
    } else {
        assert(!block_mac);
    }
    assert(entry->guard1 == BLOCK_CACHE_GUARD_1);
    assert(entry->guard2 == BLOCK_CACHE_GUARD_2);

    entry->dirty_ref = false;
    if (block_cache_entry_data_is_dirty(entry)) {
        entry->dirty_mac = true;
        ret = generate_iv(iv);
        assert(!ret);
    } else {
        pr_warn("block %" PRIu64 ", not dirty\n", entry->block);
        assert(entry->dirty_tr == NULL);
        assert(!tr);
    }

    block_put(data, data_ref);
    /* TODO: fix clients to support lazy write */
    assert(block_cache_entry_data_is_encrypted(entry) || !tr);
    assert(!entry->dirty_mac);
    if (block_mac) {
        assert(block_mac_to_block(tr, block_mac) == entry->block);
        block_mac_set_mac(tr, block_mac, &entry->mac);
    }
#if TLOG_LVL >= TLOG_LVL_DEBUG
    if (print_mac_update) {
        printf("%s: block %" PRIu64 ", update parent mac, %p, block %" PRIu64
               "\n",
               __func__, entry->block, block_mac, parent ? parent->block : 0);
    }
#endif
}

/**
 * block_put_dirty - Release reference to dirty block.
 * @tr:             Transaction
 * @data:           Block data pointer
 * @data_ref:       Reference pointer to release
 * @block_mac:      block_mac pointer to update after encryting block
 * @block_mac_ref:  Block data pointer that @block_mac belongs to, or NULL if
 *                  @block_mac points to a memory only location.
 */
void block_put_dirty(struct transaction* tr,
                     void* data,
                     struct obj_ref* data_ref,
                     struct block_mac* block_mac,
                     void* block_mac_ref) {
    assert(tr);
    assert(block_mac);
    block_put_dirty_etc(tr, data, data_ref, block_mac, block_mac_ref);
}

/**
 * block_put_dirty_no_mac - Release reference to dirty super block.
 * @data:           Block data pointer
 * @data_ref:       Reference pointer to release
 * @allow_tampering: %true if this file system does not require tamper-proof
 *                   super block storage, %false if tamper detection must be
 *                   required.
 *
 * Similar to block_put_dirty except no transaction or block_mac is needed.
 */
void block_put_dirty_no_mac(void* data,
                            struct obj_ref* data_ref,
                            bool allow_tampering) {
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    assert(entry->dev);
    assert((allow_tampering && !entry->dev->tamper_detecting) ||
           (!allow_tampering && entry->dev->tamper_detecting));
    block_put_dirty_etc(NULL, data, data_ref, NULL, NULL);
}

/**
 * block_put_dirty_discard - Release reference to dirty block.
 * @data:           Block data pointer
 * @data_ref:       Reference pointer to release
 *
 * Similar to block_put_dirty except data can be discarded.
 */
void block_put_dirty_discard(void* data, struct obj_ref* data_ref) {
    block_put_dirty_etc(NULL, data, data_ref, NULL, NULL);
    block_discard_dirty(data);
}

/**
 * block_get_write_no_read - Get block data without read for write
 * @tr:         Transaction
 * @block:      Block number
 * @is_tmp:     If true, data is only needed until @tr is commited.
 * @ref:        Pointer to store reference in.
 *
 * Same as block_get_no_read followed by block_dirty.
 *
 * Return: Block data pointer.
 */
void* block_get_write_no_read(struct transaction* tr,
                              data_block_t block,
                              bool is_tmp,
                              struct obj_ref* ref) {
    const void* data_ro = block_get_no_read(tr, block, ref);
    return block_dirty(tr, data_ro, is_tmp);
}

/**
 * block_get_write - Get block data for write
 * @tr:         Transaction
 * @block_mac:  Block number and mac
 * @iv:         Initial vector used to decrypt block, or NULL. If NULL, the
 *              start of the loaded block data is used as the iv.
 *              Only NULL is currently supported.
 * @is_tmp:     If true, data is only needed until @tr is commited.
 * @ref:        Pointer to store reference in.
 *
 * Same as block_get followed by block_dirty.
 *
 * Return: Block data pointer.
 */
void* block_get_write(struct transaction* tr,
                      const struct block_mac* block_mac,
                      const struct iv* iv,
                      bool is_tmp,
                      struct obj_ref* ref) {
    const void* data_ro = block_get(tr, block_mac, iv, ref);
    if (!data_ro) {
        return NULL;
    }
    return block_dirty(tr, data_ro, is_tmp);
}

/**
 * block_get_cleared - Get block cleared data for write
 * @tr:         Transaction
 * @block:      Block number
 * @is_tmp:     If true, data is only needed until @tr is commited.
 * @ref:        Pointer to store reference in.
 *
 * Return: Block data pointer.
 */
void* block_get_cleared(struct transaction* tr,
                        data_block_t block,
                        bool is_tmp,
                        struct obj_ref* ref) {
    void* data = block_get_write_no_read(tr, block, is_tmp, ref);
    memset(data, 0, MAX_BLOCK_SIZE);
    return data;
}

/**
 * block_get_cleared_super - Get block with cleared data for write on super_dev
 * @tr:         Transaction
 * @block:      Block number
 * @ref:        Pointer to store reference in.
 * @pinned:     Pin this block in the cache until it is successfully written
 *
 * Return: Block data pointer.
 */
void* block_get_cleared_super(struct transaction* tr,
                              data_block_t block,
                              struct obj_ref* ref,
                              bool pinned) {
    void* data_rw;
    const void* data_ro = block_cache_get_data(tr->fs, tr->fs->super_dev, block,
                                               false, NULL, 0, ref, NULL);

    /*
     * We should never end up in a situation where there is a dirty copy of a
     * super block in the cache while we are trying to rewrite that super block.
     * If a super block entry was created via write_current_super_block(), it
     * must be flushed before the necessary data writes go through to write new
     * root nodes. If we are trying to commit an empty transaction (i.e. no data
     * blocks changed), we skip the super block update in
     * transaction_complete(). The only other way to write a new super block,
     * write_current_super_block(), will be a no-op if there is already a
     * pending super block rewrite.
     */
    assert(data_ro);
    struct block_cache_entry* entry = data_to_block_cache_entry(data_ro);
    assert(!block_cache_entry_data_is_dirty(entry));
    entry->pinned = pinned;
    entry->is_superblock = true;

    data_rw = block_dirty(tr, data_ro, false);
    assert(tr->fs->super_dev->block_size <= MAX_BLOCK_SIZE);
    memset(data_rw, 0, tr->fs->super_dev->block_size);
    return data_rw;
}

/**
 * block_get_copy - Get block for write with data copied from another block.
 * @tr:         Transaction
 * @data:       Block data pointer
 * @block:      New block number
 * @is_tmp:     If true, data is only needed until @tr is commited.
 * @new_ref:    Pointer to store reference to new block in.
 *
 * Return: Block data pointer.
 */
void* block_get_copy(struct transaction* tr,
                     const void* data,
                     data_block_t block,
                     bool is_tmp,
                     struct obj_ref* new_ref) {
    void* dst_data;
    struct block_cache_entry* src_entry = data_to_block_cache_entry(data);

    assert(block);
    assert(block < tr->fs->dev->block_count);

    dst_data = block_get_write_no_read(tr, block, is_tmp, new_ref);
    memcpy(dst_data, data, src_entry->block_size);
    return dst_data;
}

/**
 * block_move - Get block for write and move to new location
 * @tr:         Transaction
 * @data:       Block data pointer
 * @block:      New block number
 * @is_tmp:     If true, data is only needed until @tr is commited.
 *
 * Change block number of cache entry mark new block dirty. Useful for
 * copy-on-write.
 *
 * Return: Non-const block data pointer.
 */
void* block_move(struct transaction* tr,
                 const void* data,
                 data_block_t block,
                 bool is_tmp) {
    struct block_cache_entry* dest_entry;
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    assert(block_cache_entry_has_one_ref(entry));
    assert(!block_cache_entry_data_is_dirty(entry));
    assert(entry->dev == tr->fs->dev);

    if (print_block_move) {
        printf("%s: move cache entry %zd, from block %" PRIu64 " to %" PRIu64
               "\n",
               __func__, entry - block_cache_entries, entry->block, block);
    }

    dest_entry = block_cache_lookup(NULL, tr->fs->dev, block, false);
    if (dest_entry) {
        assert(!block_cache_entry_has_refs(dest_entry));
        assert(!dest_entry->dirty_ref);
        assert(!dest_entry->dirty_tr || dest_entry->dirty_tr == tr);
        assert(!list_in_list(&dest_entry->io_op_node));
        assert(dest_entry->block == block);
        if (print_block_move) {
            printf("%s: clear old cache entry for block %" PRIu64 ", %zd\n",
                   __func__, block, dest_entry - block_cache_entries);
        }
        /* TODO: Use block_cache_entry_discard instead? */
        block_cache_entry_discard_dirty(dest_entry);
    }

    entry->block = block;
    return block_dirty(tr, data, is_tmp);
}

/**
 * block_put - Release reference to block.
 * @data:           Block data pointer
 * @data_ref:       Reference pointer to release
 */
void block_put(const void* data, struct obj_ref* ref) {
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    if (print_block_ops) {
        printf("%s: block %" PRIu64 ", cache entry %zd, state %s\n", __func__,
               entry->block, entry - block_cache_entries,
               block_cache_entry_data_state_name(entry->state));
    }

    assert(!entry->dirty_ref);

    obj_del_ref(&entry->obj, ref, block_cache_entry_destroy);
}

/**
 * block_probe - Verify that the given block is loadable and its mac is correct
 * @fs:          Filesystem containing the block to probe
 * @block_mac:   Block to probe
 * @allow_invalid: If %true, an invalid (i.e. zero) @block_mac will not be
 *                 probed and this function will return true
 *
 * Return: %false if the block is not valid or does not match the expected mac.
 * Returns %true if the block was readable, valid and matched the expected mac.
 * If @allow_invalid is %true, also return %true if @block_mac is invalid. Also
 * returns %true if an I/O error was encountered which does not positively
 * confirm a corrupted block.
 */
bool block_probe(struct fs* fs,
                 const struct block_mac* block_mac,
                 bool allow_invalid) {
    struct transaction probe_tr;
    struct obj_ref probe_ref = OBJ_REF_INITIAL_VALUE(probe_ref);
    const void* probe_block;
    /*
     * Assume the block is valid unless we get positive confirmation of an
     * invalid block.
     */
    bool valid = true;

    transaction_init(&probe_tr, fs, true);
    if (block_mac_valid(&probe_tr, block_mac)) {
        probe_block =
                block_get_no_tr_fail(&probe_tr, block_mac, NULL, &probe_ref);
        if (probe_block) {
            block_put(probe_block, &probe_ref);
        } else if (probe_tr.invalid_block_found) {
            valid = false;
        }
    } else if (allow_invalid) {
        valid = true;
    }
    transaction_fail(&probe_tr);
    transaction_free(&probe_tr);

    return valid;
}

/**
 * data_to_block_num - Get block number from block data pointer
 * @data:       Block data pointer
 *
 * Only used for debug code.
 *
 * Return: block number.
 */
data_block_t data_to_block_num(const void* data) {
    struct block_cache_entry* entry = data_to_block_cache_entry(data);

    return entry->block;
}

/**
 * block_cache_debug_get_ref_block_count - Get number of blocks that have
 * references
 *
 * Only used for debug code.
 *
 * Return: number of blocks in cache that have references.
 */
unsigned int block_cache_debug_get_ref_block_count(void) {
    unsigned int count = 0;
    struct block_cache_entry* entry;

    list_for_every_entry(&block_cache_lru, entry, struct block_cache_entry,
                         lru_node) {
        assert(entry->guard1 == BLOCK_CACHE_GUARD_1);
        assert(entry->guard2 == BLOCK_CACHE_GUARD_2);
        if (block_cache_entry_has_refs(entry)) {
            if (print_cache_get_ref_block_count) {
#if TLOG_LVL >= TLOG_LVL_DEBUG
                printf("%s: cache entry %zd in use for %" PRIu64 ", dev %p\n",
                       __func__, entry - block_cache_entries, entry->block,
                       entry->dev);
#else
                printf("%s: cache entry %zd in use for %" PRIu64 "\n",
                       __func__, entry - block_cache_entries, entry->block);
#endif
            }
            count++;
        }
    }
    return count;
}
