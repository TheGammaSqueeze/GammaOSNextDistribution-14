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

#include <assert.h>
#include <inttypes.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "array.h"
#include "block_allocator.h"
#include "block_set.h"
#include "checkpoint.h"
#include "debug.h"
#include "file.h"
#include "transaction.h"

bool print_merge_free;

/**
 * transaction_check_free
 * @tr:         Transaction object.
 * @set:        Free set to check.
 * @min_free    Number of block that must be in @set
 *
 * Return: %true is @set contains @min_free or more blocks, %false otherwise.
 */
static bool transaction_check_free(struct transaction* tr,
                                   struct block_set* set,
                                   data_block_t min_free) {
    data_block_t next_block;
    struct block_range free_range;
    data_block_t count;

    next_block = 0;
    while (true) {
        free_range = block_set_find_next_range(tr, set, next_block);
        if (block_range_empty(free_range)) {
            return false;
        }
        count = free_range.end - free_range.start;
        if (count >= min_free) {
            return true;
        }
        min_free -= count;
        next_block = free_range.end;
    }
}

/**
 * transaction_merge_free_sets
 * @tr:         Transaction object.
 * @new_set:    Output set.
 * @set_i:      Initial set.
 * @set_d:      Set of blocks to delete.
 * @set_a:      Set of blocks to add.
 *
 * Helper function to update the free_set when committing a transaction.
 * @new_set = @set_i - @set_d - @new_set-blocks + @set_a + set_[ida]-blocks
 * @new_set must start empty and will be initialized and filled in this
 * function.
 */
static void transaction_merge_free_sets(struct transaction* tr,
                                        struct block_set* new_set,
                                        struct block_set* set_i,
                                        struct block_set* set_d,
                                        struct block_set* set_a) {
    data_block_t next_block;
    struct block_range delete_range = BLOCK_RANGE_INITIAL_VALUE(delete_range);
    struct block_range add_range = BLOCK_RANGE_INITIAL_VALUE(add_range);

    /* new_set should start empty. */
    assert(block_set_find_next_block(tr, new_set, 1, true) == 0);
    block_set_copy(tr, new_set, set_i);

    full_assert(block_set_check(tr, set_i));
    full_assert(block_set_check(tr, set_d));
    full_assert(block_set_check(tr, set_a));

    assert(!block_mac_valid(tr, &set_i->block_tree.root) ||
           transaction_block_need_copy(
                   tr, block_mac_to_block(tr, &set_i->block_tree.root)));
    if (print_merge_free) {
        printf("%s\n", __func__);
    }

    /* TODO: don't walk the whole tree each time */
    next_block = 1;
    while (next_block != 0) {
        tr->min_free_block = next_block;
        delete_range = block_set_find_next_range(tr, set_d, next_block);
        add_range = block_set_find_next_range(tr, set_a, next_block);
        if (print_merge_free) {
            printf("%s: add %" PRIu64 "-%" PRIu64 " or delete %" PRIu64
                   "-%" PRIu64 "\n",
                   __func__, add_range.start, add_range.end - 1,
                   delete_range.start, delete_range.end - 1);
        }
        assert(!block_range_overlap(delete_range, add_range));
        if (block_range_before(delete_range, add_range)) {
            assert(delete_range.start >= next_block);
            tr->min_free_block = delete_range.end;
            block_allocator_suspend_set_updates(tr);
            block_set_remove_range(tr, new_set, delete_range);
            block_allocator_process_queue(tr);
            next_block = delete_range.end;
        } else if (!block_range_empty(add_range)) {
            assert(add_range.start >= next_block);
            tr->min_free_block = add_range.end;
            block_allocator_suspend_set_updates(tr);
            block_set_add_range(tr, new_set, add_range);
            block_allocator_process_queue(tr);
            next_block = add_range.end;
        } else {
            assert(block_range_empty(delete_range));
            assert(block_range_empty(add_range));
            next_block = 0;
        }
        if (tr->failed) {
            pr_warn("transaction failed, abort\n");
            return;
        }
    }
    full_assert(block_set_check(tr, new_set));
}

/** transaction_rebuild_free_set - Rebuild free set from referenced file blocks
 * @tr:             Transaction object.
 * @new_free_set:   Output free set.
 * @files:          Root block and mac of the files tree.
 * @checkpoint:     Checkpoint metadata block and mac
 *
 * Rebuilds the file system free set by walking the current files tree and
 * ensuring that all referenced blocks are marked as not free. @new_free_set
 * will be initialized to contain all blocks not referenced from the files root.
 * The @checkpoint metadata block will also be removed from the free set, but
 * its children (checkpoint files tree and free set) will not be checked. The
 * blocks in the checkpoint (beside the metadata block) are tracked as
 * free/allocated by the checkpoint free set rather than the active file system
 * free set.
 *
 * We ignore tr->freed and tr->fs->free here because we are reconstructing the
 * entire free set. All blocks that were freed in this transaction will not be
 * referenced by @new_files.
 */
static void transaction_rebuild_free_set(struct transaction* tr,
                                         struct block_set* new_free_set,
                                         struct block_mac* new_files,
                                         struct block_mac* new_checkpoint) {
    struct block_range init_range = {
            .start = tr->fs->min_block_num,
            .end = tr->fs->dev->block_count,
    };
    struct block_range range;
    struct block_set previously_allocated =
            BLOCK_SET_INITIAL_VALUE(previously_allocated);

    /*
     * Copy and save tr->allocated so that we can keep track of the blocks
     * already allocated for the current transaction when performing allocations
     * for the new free set tree nodes. We then reset tr->allocated so that it
     * will only hold new blocks allocated for new_free_set. All blocks
     * allocated for files will already be referenced in new_files, so we'll
     * already be removing them from new_free_set.
     */
    assert(list_in_list(&tr->allocated.node));
    list_delete(&tr->allocated.node);
    block_set_copy_ro(tr, &previously_allocated, &tr->allocated);
    list_add_tail(&tr->fs->allocated, &previously_allocated.node);

    block_set_init(tr->fs, &tr->allocated);
    list_add_tail(&tr->fs->allocated, &tr->allocated.node);

    block_set_init(tr->fs, new_free_set);
    new_free_set->block_tree.copy_on_write = true;
    new_free_set->block_tree.allow_copy_on_write = true;

    block_set_add_range(tr, new_free_set, init_range);

    if (block_mac_valid(tr, new_checkpoint)) {
        block_set_remove_block(tr, new_free_set,
                               block_mac_to_block(tr, new_checkpoint));
    }
    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        return;
    }

    if (block_mac_valid(tr, new_files)) {
        files_rebuild_free_set(tr, new_free_set, new_files);
        if (tr->failed) {
            pr_warn("transaction failed, abort\n");
            return;
        }
    }

    for (range = block_set_find_next_range(tr, &tr->allocated, 1);
         !block_range_empty(range);
         range = block_set_find_next_range(tr, &tr->allocated, range.end)) {
        tr->min_free_block = range.end;
        block_allocator_suspend_set_updates(tr);
        block_set_remove_range(tr, new_free_set, range);
        block_allocator_process_queue(tr);
    }

    /*
     * Copy the rest of the allocated blocks back to tr->allocated to maintain a
     * consistent state. We don't actually need to do this with the current code
     * calling this function, but this restores the transaction state to what
     * would be expected if it were to be used in the future.
     */
    for (range = block_set_find_next_range(tr, &previously_allocated, 1);
         !block_range_empty(range);
         range = block_set_find_next_range(tr, &previously_allocated,
                                           range.end)) {
        block_set_add_range(tr, &tr->allocated, range);
    }
    list_delete(&previously_allocated.node);

    full_assert(block_set_check(tr, new_free_set));
}

/**
 * transaction_block_need_copy - Check if block needs copy
 * @tr:         Transaction object.
 * @block:      Block number to check.
 *
 * Return: %true if block has not been allocated as a non-tmp block for @tr,
 * %false otherwise.
 */
bool transaction_block_need_copy(struct transaction* tr, data_block_t block) {
    assert(block);
    assert(!block_set_block_in_set(tr, &tr->tmp_allocated, block));
    assert(!block_allocator_allocation_queued(tr, block, true));

    return !block_set_block_in_set(tr, &tr->allocated, block) &&
           !block_allocator_allocation_queued(tr, block, false);
}

/**
 * transaction_delete_active - Remove transaction from active lists (internal)
 * @tr:         Transaction object.
 */
static void transaction_delete_active(struct transaction* tr) {
    assert(list_in_list(&tr->allocated.node));
    assert(list_in_list(&tr->tmp_allocated.node));
    list_delete(&tr->allocated.node);
    list_delete(&tr->tmp_allocated.node);
}

/**
 * transaction_fail - Fail transaction
 * @tr:         Transaction object.
 *
 * Marks transaction as failed, removes it from active list, discards dirty
 * cache entries and restore open files to last committed state.
 */
void transaction_fail(struct transaction* tr) {
    assert(!tr->failed);

    tr->failed = true;

    if (tr->complete) {
        return;
    }

    block_cache_discard_transaction(tr, true);
    transaction_delete_active(tr);
    file_transaction_failed(tr);
}

/**
 * transaction_free - Free transaction
 * @tr:         Transaction object.
 *
 * Prepare @tr for free. @tr must not be active and all open files must already
 * be closed.
 */
void transaction_free(struct transaction* tr) {
    assert(!transaction_is_active(tr));
    assert(list_is_empty(&tr->open_files));
    assert(list_in_list(&tr->node));
    list_delete(&tr->node);
}

/**
 * check_free_tree - Check tree of free set (internal)
 * @tr:         Transaction object.
 * @free:       Set object.
 *
 * Check that the blocks used by the tree for a free set are not in the same
 * set.
 */
static void check_free_tree(struct transaction* tr, struct block_set* free) {
    unsigned int i;
    struct block_tree_path path;

    block_tree_walk(tr, &free->block_tree, 0, true, &path);
    while (block_tree_path_get_key(&path)) {
        for (i = 0; i < path.count; i++) {
            assert(!block_set_block_in_set(
                    tr, free,
                    block_mac_to_block(tr, &path.entry[i].block_mac)));
        }
        block_tree_path_next(&path);
    }
}

/**
 * transaction_complete - Complete transaction, optionally updating checkpoint
 * @tr:                Transaction object.
 * @update_checkpoint: If true, update checkpoint with the new file-system
 *                     state.
 */
void transaction_complete_etc(struct transaction* tr, bool update_checkpoint) {
    struct block_mac new_files;
    struct transaction* tmp_tr;
    struct transaction* other_tr;
    struct block_set new_free_set = BLOCK_SET_INITIAL_VALUE(new_free_set);
    struct checkpoint* new_checkpoint = NULL;
    struct block_mac new_checkpoint_mac;
    struct obj_ref new_checkpoint_ref =
            OBJ_REF_INITIAL_VALUE(new_checkpoint_ref);
    bool super_block_updated;

    assert(tr->fs);
    assert(!tr->complete);

    if (tr->fs->checkpoint_required) {
        tr->fs->checkpoint_required = false;
        if (!checkpoint_commit(tr->fs)) {
            /*
             * checkpoint creation failed, so we need to try again before we
             * commit the next transaction
             */
            tr->fs->checkpoint_required = true;
            transaction_fail(tr);
            pr_warn("auto-checkpoint failed, abort\n");
            goto err_transaction_failed;
        }
    }

    // printf("%s: %" PRIu64 "\n", __func__, tr->version);

    block_mac_copy(tr, &new_checkpoint_mac, &tr->fs->checkpoint);

    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        goto err_transaction_failed;
    }

    assert(transaction_is_active(tr));

    file_transaction_complete(tr, &new_files);
    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        goto err_transaction_failed;
    }

    if (update_checkpoint) {
        new_checkpoint = checkpoint_get_new_block(tr, &new_checkpoint_ref,
                                                  &new_checkpoint_mac);
        if (tr->failed) {
            pr_warn("transaction failed, abort\n");
            goto err_transaction_failed;
        }
        assert(new_checkpoint);
    }

    tr->new_free_set = &new_free_set;
    if (tr->rebuild_free_set) {
        transaction_rebuild_free_set(tr, &new_free_set, &new_files,
                                     &new_checkpoint_mac);
    } else {
        transaction_merge_free_sets(tr, &new_free_set, &tr->fs->free,
                                    &tr->allocated, &tr->freed);
    }
    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        goto err_transaction_failed;
    }

    if (!transaction_check_free(tr, &new_free_set, tr->fs->reserved_count)) {
        if (!tr->failed) {
            transaction_fail(tr);
        }
        pr_warn("transaction would leave fs too full, abort\n");
        goto err_transaction_failed;
    }

    if (tr->fs->alternate_data && tr->repaired) {
        if (!tr->failed) {
            transaction_fail(tr);
        }
        pr_warn("transaction cannot repair alternate fs, abort\n");
        goto err_transaction_failed;
    }

    if (0) {
        printf("%s: old free:\n", __func__);
        block_set_print(tr, &tr->fs->free);
        printf("%s: tmp_allocated:\n", __func__);
        block_set_print(tr, &tr->tmp_allocated);
        printf("%s: allocated:\n", __func__);
        block_set_print(tr, &tr->allocated);
        printf("%s: freed:\n", __func__);
        block_set_print(tr, &tr->freed);
        printf("%s: new free:\n", __func__);
        block_set_print(tr, &new_free_set);
    }

    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        goto err_transaction_failed;
    }

    if (update_checkpoint) {
        checkpoint_update_roots(tr, new_checkpoint, &new_files,
                                &new_free_set.block_tree.root);
        block_put_dirty(tr, new_checkpoint, &new_checkpoint_ref,
                        &new_checkpoint_mac, NULL);
        /*
         * We have now released the block reference new_checkpoint_ref, so make
         * sure we don't release it again in err_transaction_failed
         */
        new_checkpoint = NULL;
    }

    block_cache_clean_transaction(tr);

    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        goto err_transaction_failed;
    }

    assert(block_range_empty(new_free_set.initial_range));
    check_free_tree(tr, &new_free_set);

    if (block_mac_same_block(tr, &tr->fs->free.block_tree.root,
                             &new_free_set.block_tree.root)) {
        /*
         * If the root block of the free tree did not move, there can be no
         * other changes to the filesystem.
         */
        assert(block_mac_eq(tr, &tr->fs->free.block_tree.root,
                            &new_free_set.block_tree.root));
        assert(block_mac_eq(tr, &tr->fs->files.root, &new_files));

        /*
         * Skip super block write if there are no changes to the filesystem.
         * This is needed in case a previous write error has triggered a request
         * to write another copy of the old super block. There can only be one
         * copy of each block in the cache. If we try to write a new super block
         * here before cleaning the pending one, we get a conflict. If there
         * were changes to the filesystem, the pending super block has already
         * been cleaned at this point.
         */
        goto complete_nop_transaction;
    }

    super_block_updated = update_super_block(tr, &new_free_set.block_tree.root,
                                             &new_files, &new_checkpoint_mac);
    if (!super_block_updated) {
        assert(tr->failed);
        pr_warn("failed to update super block, abort\n");
        goto err_transaction_failed;
    }
    block_cache_clean_transaction(tr);

    /*
     * If an error was detected writing the super block, it is not safe to
     * continue as we do not know if the write completed. We need to rewrite a
     * known state over the unknown super block to avoid an inconsistent view of
     * the filesystem.
     *
     * At this point block_cache_complete_write has been called by the block
     * device, so the current superblock slot in the block cache is free and not
     * associated with the pending transaction.
     */
    if (tr->failed) {
        pr_warn("failed to write super block, notify fs and abort the transaction\n");
        /*
         * Superblock could have been written or not. Make sure no other blocks
         * are written to the filesystem before writing another copy of the
         * superblock with the existing file and free trees.
         *
         * TODO: Don't trigger a superblock write on unaffected filesystems.
         * We update all for now to simplify testing.
         */
        fs_unknown_super_block_state_all();
        goto err_transaction_failed;
    }

    tr->fs->free.block_tree.root = new_free_set.block_tree.root;
    block_range_clear(
            &tr->fs->free
                     .initial_range); /* clear for initial file-system state */
    tr->fs->files.root = new_files;
    tr->fs->super_block_version = tr->fs->written_super_block_version;
    tr->fs->checkpoint = new_checkpoint_mac;
    if (tr->repaired) {
        assert(!tr->fs->alternate_data);
        tr->fs->main_repaired = true;
    }
    if (update_checkpoint) {
        tr->fs->checkpoint_free.block_tree.root = new_free_set.block_tree.root;
        block_range_clear(&tr->fs->checkpoint_free.initial_range);
    }

complete_nop_transaction:
    transaction_delete_active(tr);
    tr->complete = true;

    file_transaction_success(tr);
    assert(!tr->failed);

    check_free_tree(tr, &tr->fs->free);

    list_for_every_entry_safe(&tr->fs->transactions, other_tr, tmp_tr,
                              struct transaction, node) {
        if (tr->failed) {
            break;
        }
        if (!transaction_is_active(other_tr)) {
            continue;
        }
        if (tr->rebuild_free_set) {
            /*
             * TODO: only fail actually conflicting transactions when rebuilding
             * the free set. When rebuilding, tr->freed does not contain all
             * freed blocks if tree nodes were dropped. We could rebuild a free
             * set delta by subtracting the new free set from the old one and
             * then compare this delta against other transactions.
             */
            pr_warn("Rebuilding free set requires failing all pending transactions\n");
            transaction_fail(other_tr);
        } else if (block_set_overlap(tr, &tr->freed, &other_tr->freed)) {
            pr_warn("fail conflicting transaction\n");
            transaction_fail(other_tr);
        }
    }
    if (tr->failed) {
        pr_warn("transaction failed while failing conflicting transactions\n");
        tr->failed = false;
        list_for_every_entry_safe(&tr->fs->transactions, other_tr, tmp_tr,
                                  struct transaction, node) {
            if (!transaction_is_active(other_tr)) {
                continue;
            }
            pr_warn("fail possibly conflicting transaction\n");
            transaction_fail(other_tr);
        }
    }
    assert(!tr->failed);
    block_cache_discard_transaction(tr, false);

err_transaction_failed:
    if (new_checkpoint) {
        block_put_dirty_discard(new_checkpoint, &new_checkpoint_ref);
    }
    if (tr->failed) {
        file_transaction_complete_failed(tr);
    }
    assert(!block_cache_debug_get_ref_block_count());
}

/**
 * transaction_initial_super_block_complete - Complete special transaction
 * @tr:         Transaction object. Must match initial_super_block_tr in fs.
 *
 * Flush the initial superblock in @tr to disk. If the block could not be
 * written re-initialize @tr and leave it in place for another attempt.
 * Otherwise clear @tr->fs->initial_super_block_tr and free @tr.
 *
 * The initial superblock can only be flushed from the block cache by the
 * block_cache_clean_transaction() call here, as we do not allow initial
 * superblocks to be flushed to make room for other data. This ensures that we
 * don't run out of room to recreate the superblock write in case it fails.
 */
void transaction_initial_super_block_complete(struct transaction* tr) {
    assert(tr == tr->fs->initial_super_block_tr);
    block_cache_clean_transaction(tr);
    if (tr->failed) {
        /*
         * If we failed to write the superblock we re-initialize a new attempt
         * to write that superblock before the next time we write to this
         * filesystem.
         */
        pr_err("%s: failed to write initial superblock, version %d.\n",
               __func__, tr->fs->written_super_block_version);
        write_current_super_block(tr->fs, true /* reinitialize */);
        return;
    }
    printf("%s: write initial superblock, version %d -> %d\n", __func__,
           tr->fs->super_block_version, tr->fs->written_super_block_version);

    assert(tr == tr->fs->initial_super_block_tr);
    tr->fs->super_block_version = tr->fs->written_super_block_version;
    tr->fs->initial_super_block_tr = NULL;

    /* not a real transaction, discard the state so it can be freed */
    transaction_fail(tr);
    transaction_free(tr);
    free(tr);
}

/**
 * transaction_activate - Activate transaction
 * @tr:         Transaction object.
 */
void transaction_activate(struct transaction* tr) {
    assert(tr->fs);
    assert(!transaction_is_active(tr));

    tr->failed = false;
    tr->invalid_block_found = false;
    tr->complete = false;
    tr->rebuild_free_set = false;
    tr->repaired = false;
    tr->min_free_block = 0;
    tr->last_free_block = 0;
    tr->last_tmp_free_block = 0;
    tr->new_free_set = NULL;

    block_set_init(tr->fs, &tr->tmp_allocated);
    block_set_init(tr->fs, &tr->allocated);
    block_set_init(tr->fs, &tr->freed);

    fs_file_tree_init(tr->fs, &tr->files_added);
    fs_file_tree_init(tr->fs, &tr->files_updated);
    fs_file_tree_init(tr->fs, &tr->files_removed);

    list_add_tail(&tr->fs->allocated, &tr->allocated.node);
    list_add_tail(&tr->fs->allocated, &tr->tmp_allocated.node);
}

/**
 * transaction_init - Initialize new transaction object
 * @tr:         Transaction object.
 * @fs:         File system state object.
 * @activate:   Activate transaction
 */
void transaction_init(struct transaction* tr, struct fs* fs, bool activate) {
    assert(fs);
    assert(fs->dev);

    memset(tr, 0, sizeof(*tr));
    tr->fs = fs;

    list_initialize(&tr->open_files);
    list_add_tail(&fs->transactions, &tr->node);

    if (activate) {
        transaction_activate(tr);
    }
}
